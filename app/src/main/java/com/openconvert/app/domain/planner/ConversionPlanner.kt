package com.openconvert.app.domain.planner

import com.openconvert.app.domain.converter.EncodeMode
import com.openconvert.app.domain.converter.MediaEncodePlanner
import com.openconvert.app.domain.converter.StreamCodecs
import com.openconvert.app.domain.engine.EngineType
import com.openconvert.app.domain.model.ConversionGraph
import com.openconvert.app.domain.model.FileCategory
import com.openconvert.app.domain.model.FileFormat
import com.openconvert.app.domain.model.QualityPreset
import com.openconvert.app.domain.model.ResolutionPreset
import com.openconvert.app.domain.work.StorageGuard

/**
 * 转换策略规划层（计划书 §五 ConversionPlanner）。
 *
 * 在 `ConverterRegistry` 真正执行之前，把散落的四块决策逻辑串成一次判断：
 *
 * ```
 * ConversionGraph      能力校验（这条边存在吗）
 *        ↓
 * MediaEncodePlanner   编码模式（Remux / 流拷贝 / 硬编 / 软编）
 *        ↓
 * HardwareFacts        硬件事实（有没有 H.264 / VP8 硬件编码器）
 *        ↓
 * StorageGuard         临时空间预检（带精确数字）
 *        ↓
 * ConversionPlan       引擎 + 兜底 + 并发槽位 + 空间预算 + 决策理由
 * ```
 *
 * 关键区别于旧 `ConversionEngineSelector`：Selector 只回答"用哪个引擎"，
 * 且没有任何调用方（死代码）。Planner 输出的是可直接驱动执行的完整方案，
 * 并且在方案不可行时给出带数字的 [PlanRejection]，满足计划书 §7.3 的
 * "错误必须能告诉用户具体原因"。
 */
object ConversionPlanner {

    fun plan(request: PlanRequest): PlanResult {
        // 1. 能力校验：唯一依据是能力图，与 ConverterRegistry 的引擎索引同源。
        if (!ConversionGraph.canConvert(request.input, request.target)) {
            return PlanResult.Rejected(
                PlanRejection.UnsupportedRoute(
                    input = request.input.displayName,
                    target = request.target.displayName,
                ),
            )
        }

        // 2. 输入有效性：只有在体积经过真实校验时，0 才代表空文件。
        //    task.fileSize 来自 SAF/MediaStore 元数据，对未知大小合法返回 0
        //    （MediaStore 行刚插入、流未 flush 时就是这种情况），不能当作空文件证据。
        //    真正的空文件由引擎读流时拦截。
        if (request.isSizeVerified && request.inputBytes == 0L) {
            return PlanResult.Rejected(PlanRejection.InvalidInput("文件是空的（0 字节）"))
        }

        // 3. 按类别规划引擎与编码模式。
        val draft = when {
            request.input.category == FileCategory.IMAGE -> planImage()
            request.input.category == FileCategory.OFFICE -> planOffice()
            request.input.category == FileCategory.ARCHIVE -> planArchive()
            request.input.category == FileCategory.AUDIO ||
                request.input.category == FileCategory.VIDEO -> planMedia(request)
            else -> return PlanResult.Rejected(
                PlanRejection.UnsupportedRoute(
                    request.input.displayName,
                    request.target.displayName,
                ),
            )
        }
        if (draft is DraftResult.Rejected) return PlanResult.Rejected(draft.rejection)
        val engines = (draft as DraftResult.Draft)

        // 4. 空间预检：Remux/流拷贝不产生解码中间文件，预算更小。
        val required = StorageGuard.requiredScratchBytes(
            inputBytes = request.inputBytes,
            copiesInput = request.copiesInputToCache,
        )
        if (!StorageGuard.hasEnoughSpace(request.runtime.usableScratchBytes, required)) {
            return PlanResult.Rejected(
                PlanRejection.InsufficientSpace(
                    requiredBytes = required,
                    availableBytes = request.runtime.usableScratchBytes,
                ),
            )
        }

        // 5. 并发槽位：大文件或视频硬件编码器独占时串行。
        val slot = decideConcurrency(request, engines.isStreamCopy)

        return PlanResult.Ready(
            ConversionPlan(
                primaryEngine = engines.primary,
                fallbackEngine = engines.fallback,
                encodeMode = engines.encodeMode,
                isStreamCopy = engines.isStreamCopy,
                concurrency = slot,
                requiredScratchBytes = required,
                reason = engines.reason,
            ),
        )
    }

    /**
     * 并发决策（计划书 §5.4）：
     * - 流拷贝几乎不吃 CPU/内存 → 允许并行
     * - 视频重编码占用硬件编码器 → 串行
     * - 超过阈值的大文件 → 串行（避免多份 1MB 缓冲 + 解码位图同时驻留）
     */
    private fun decideConcurrency(request: PlanRequest, isStreamCopy: Boolean): ConcurrencySlot = when {
        isStreamCopy -> ConcurrencySlot.PARALLEL
        request.input.category == FileCategory.VIDEO -> ConcurrencySlot.SERIAL
        request.inputBytes >= request.runtime.serialThresholdBytes -> ConcurrencySlot.SERIAL
        else -> ConcurrencySlot.PARALLEL
    }

    private sealed interface DraftResult {
        data class Draft(
            val primary: EngineType,
            val fallback: EngineType?,
            val encodeMode: EncodeMode?,
            val isStreamCopy: Boolean,
            val reason: String,
        ) : DraftResult

        data class Rejected(val rejection: PlanRejection) : DraftResult
    }

    private fun planImage(): DraftResult = DraftResult.Draft(
        primary = EngineType.LIBVIPS,
        fallback = EngineType.BITMAP_FACTORY,
        encodeMode = null,
        isStreamCopy = false,
        reason = "libvips SIMD 原生加速，解码失败回退 BitmapFactory",
    )

    private fun planOffice(): DraftResult = DraftResult.Draft(
        primary = EngineType.LIBREOFFICE_KIT,
        fallback = null,
        encodeMode = null,
        isStreamCopy = false,
        reason = "LibreOfficeKit 原生离线渲染（无兜底引擎）",
    )

    private fun planArchive(): DraftResult = DraftResult.Draft(
        primary = EngineType.COMMONS_COMPRESS,
        fallback = null,
        encodeMode = null,
        isStreamCopy = true,
        reason = "Commons Compress 流式处理，不解码不重压",
    )

    /**
     * 音视频规划：复用已在生产中验证的 [MediaEncodePlanner] 决定编码模式，
     * 再按硬件事实映射到具体引擎。
     */
    private fun planMedia(request: PlanRequest): DraftResult {
        val mode = MediaEncodePlanner.plan(
            target = request.target,
            quality = request.quality,
            resolution = request.resolution,
            codecs = request.codecs,
        ).mode

        return when (mode) {
            // 换容器 / 拷流：不重新编码，最快路径。
            EncodeMode.REMUX, EncodeMode.COPY_VIDEO, EncodeMode.AUDIO_COPY -> DraftResult.Draft(
                primary = EngineType.FFMPEG_KIT,
                fallback = null,
                encodeMode = mode,
                isStreamCopy = true,
                reason = "源编码已兼容目标容器，直接拷流不重编码",
            )

            EncodeMode.HARDWARE_H264 -> if (request.hardware.hasH264HardwareEncoder) {
                DraftResult.Draft(
                    primary = EngineType.MEDIA3_MEDIACODEC,
                    fallback = EngineType.FFMPEG_KIT,
                    encodeMode = mode,
                    isStreamCopy = false,
                    reason = "MediaCodec H.264 芯片硬件编码，失败回退 FFmpeg",
                )
            } else {
                // 无硬件 H.264：FFmpegKit 8.1.7 构建里没有 libx264，
                // 只能退到 mpeg4 软件编码（见 conversion-engine-roadmap.md 已知约束）。
                DraftResult.Draft(
                    primary = EngineType.FFMPEG_KIT,
                    fallback = null,
                    encodeMode = EncodeMode.SOFTWARE_MPEG4,
                    isStreamCopy = false,
                    reason = "本机无 H.264 硬件编码器，改用 FFmpeg mpeg4 软件编码",
                )
            }

            EncodeMode.LITR_VP8 -> if (request.hardware.hasVp8HardwareEncoder) {
                DraftResult.Draft(
                    primary = EngineType.LITR,
                    fallback = null,
                    encodeMode = mode,
                    isStreamCopy = false,
                    reason = "LiTr + MediaCodec VP8 硬件编码（FFmpeg audio 包无 libvpx）",
                )
            } else {
                DraftResult.Rejected(
                    PlanRejection.NoUsableEncoder(
                        codec = "vp8",
                        attempted = listOf(EngineType.LITR),
                    ),
                )
            }

            EncodeMode.AUDIO_ONLY -> DraftResult.Draft(
                primary = EngineType.FFMPEG_KIT,
                fallback = null,
                encodeMode = mode,
                isStreamCopy = false,
                reason = "音频重编码（源编码与目标不一致）",
            )

            EncodeMode.SOFTWARE_MPEG4, EncodeMode.FAST_VP8 -> DraftResult.Draft(
                primary = EngineType.FFMPEG_KIT,
                fallback = null,
                encodeMode = mode,
                isStreamCopy = false,
                reason = "FFmpeg 软件编码兼容模式",
            )
        }
    }
}

/** Planner 的输入：一次转换需要的全部事实。 */
data class PlanRequest(
    val input: FileFormat,
    val target: FileFormat,
    val inputBytes: Long,
    val quality: QualityPreset = QualityPreset.BALANCED,
    val resolution: ResolutionPreset = ResolutionPreset.ORIGINAL,
    /** 探测到的真实音视频编码；图片/文档为 null。 */
    val codecs: StreamCodecs? = null,
    val hardware: HardwareFacts = HardwareFacts.NONE,
    val runtime: RuntimeFacts,
    /** 引擎是否需要先把输入拷进缓存（FFmpeg/LOKit 需要真实路径）。 */
    val copiesInputToCache: Boolean = true,
    /**
     * [inputBytes] 是否经过真实校验（stat/statSize），而非 SAF 元数据。
     * SAF `OpenableColumns.SIZE` 对未知大小合法返回 0，因此只有确认过的 0
     * 才能判定为空文件。默认 false = 不据此拒绝。
     */
    val isSizeVerified: Boolean = false,
)
