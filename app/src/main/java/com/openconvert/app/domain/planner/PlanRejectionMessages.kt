package com.openconvert.app.domain.planner

import com.openconvert.app.AppCopy
import com.openconvert.app.R
import com.openconvert.app.domain.engine.EngineType

/**
 * 把结构化拒绝原因翻译成用户能看懂的中文（计划书 §7.3）。
 *
 * 硬性要求：不允许输出 "Conversion failed" 这类无信息量文案。
 * 空间不足必须带「需要 X / 剩余 Y」；编码器缺失必须说明已尝试哪些引擎。
 */
object PlanRejectionMessages {

    fun describe(rejection: PlanRejection): String = when (rejection) {
        is PlanRejection.UnsupportedRoute ->
            AppCopy.getOr(
                R.string.error_unsupported_route,
                "暂不支持 ${rejection.input} → ${rejection.target}",
                rejection.input,
                rejection.target,
            )

        is PlanRejection.InsufficientSpace -> buildString {
            append(AppCopy.getOr(R.string.error_storage, "存储空间不足"))
            append("\n\n")
            append(
                AppCopy.getOr(
                    R.string.error_storage_need,
                    "需要：${formatBytes(rejection.requiredBytes)}\n当前剩余：${formatBytes(rejection.availableBytes)}",
                    formatBytes(rejection.requiredBytes),
                    formatBytes(rejection.availableBytes),
                ),
            )
        }

        is PlanRejection.NoUsableEncoder -> buildString {
            append(AppCopy.getOr(R.string.error_codec_device, "视频编码不受当前设备硬件支持"))
            append("\n\n")
            append(
                AppCopy.getOr(
                    R.string.error_codec_tried,
                    "已尝试：" + rejection.attempted.joinToString("、") { engineLabel(it) },
                    rejection.attempted.joinToString("、") { engineLabel(it) },
                ),
            )
        }

        is PlanRejection.InvalidInput ->
            AppCopy.getOr(R.string.error_invalid_input, "文件无法处理") + "：${rejection.detail}"
    }

    fun engineLabel(engine: EngineType): String = when (engine) {
        EngineType.LIBVIPS -> "libvips"
        EngineType.BITMAP_FACTORY -> AppCopy.getOr(R.string.engine_bitmap, "系统解码器")
        EngineType.MEDIA3_MEDIACODEC -> AppCopy.getOr(R.string.engine_mediacodec, "MediaCodec 硬件编码")
        EngineType.LITR -> AppCopy.getOr(R.string.engine_litr, "LiTr 硬件编码")
        EngineType.FFMPEG_KIT -> AppCopy.getOr(R.string.engine_ffmpeg, "FFmpeg 软件编码")
        EngineType.LIBREOFFICE_KIT -> "LibreOfficeKit"
        EngineType.PDFBOX -> "PdfBox"
        EngineType.COMMONS_COMPRESS -> "Commons Compress"
    }

    /** 1 位小数的人类可读体积，用于错误文案里的精确数字。 */
    fun formatBytes(bytes: Long): String {
        val kb = 1024.0
        val mb = kb * 1024
        val gb = mb * 1024
        return when {
            bytes >= gb -> String.format("%.1f GB", bytes / gb)
            bytes >= mb -> String.format("%.1f MB", bytes / mb)
            bytes >= kb -> String.format("%.1f KB", bytes / kb)
            else -> "$bytes B"
        }
    }
}
