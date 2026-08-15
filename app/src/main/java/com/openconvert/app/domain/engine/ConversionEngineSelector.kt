package com.openconvert.app.domain.engine

import com.openconvert.app.domain.converter.StreamCodecs
import com.openconvert.app.domain.device.DeviceCapabilities
import com.openconvert.app.domain.model.FileCategory
import com.openconvert.app.domain.model.FileFormat
import com.openconvert.app.domain.model.QualityPreset
import com.openconvert.app.domain.model.ResolutionPreset

enum class EngineType {
    LIBVIPS,
    BITMAP_FACTORY,
    MEDIA3_MEDIACODEC,
    LITR,
    FFMPEG_KIT,
    LIBREOFFICE_KIT,
    PDFBOX,
    COMMONS_COMPRESS,
}

data class EngineDecision(
    val primaryEngine: EngineType,
    val fallbackEngine: EngineType? = null,
    val isStreamCopy: Boolean = false,
    val reason: String = "",
)

/**
 * 转换引擎选择器与转码规划决策层（计划书 §二十、§二十一）。
 */
object ConversionEngineSelector {

    fun selectEngine(
        inputFormat: FileFormat,
        outputFormat: FileFormat,
        quality: QualityPreset = QualityPreset.BALANCED,
        resolution: ResolutionPreset = ResolutionPreset.ORIGINAL,
        codecs: StreamCodecs? = null,
    ): EngineDecision {
        // 1. 图片类别
        if (inputFormat.category == FileCategory.IMAGE && outputFormat.category == FileCategory.IMAGE) {
            return EngineDecision(
                primaryEngine = EngineType.LIBVIPS,
                fallbackEngine = EngineType.BITMAP_FACTORY,
                reason = "SIMD 原生加速 + 低内存占用",
            )
        }

        // 2. Office 文档
        if (inputFormat in setOf(FileFormat.DOCX, FileFormat.DOC, FileFormat.PPTX, FileFormat.PPT, FileFormat.XLSX, FileFormat.XLS)) {
            return EngineDecision(
                primaryEngine = EngineType.LIBREOFFICE_KIT,
                reason = "LibreOfficeKit 原生离线渲染",
            )
        }

        // 3. 压缩包
        if (inputFormat.category == FileCategory.ARCHIVE || outputFormat.category == FileCategory.ARCHIVE) {
            return EngineDecision(
                primaryEngine = EngineType.COMMONS_COMPRESS,
                reason = "Apache Commons Compress 流式压缩",
            )
        }

        // 4. PDF 工具
        if (inputFormat == FileFormat.PDF || outputFormat == FileFormat.PDF) {
            return EngineDecision(
                primaryEngine = EngineType.PDFBOX,
                reason = "PdfBox / PdfRenderer 页面流水线",
            )
        }

        // 5. 视频转换
        if (inputFormat.category == FileCategory.VIDEO) {
            val hwProfile = DeviceCapabilities.getHardwareProfile()

            // 视频提取音频
            if (outputFormat.category == FileCategory.AUDIO) {
                return EngineDecision(
                    primaryEngine = EngineType.FFMPEG_KIT,
                    reason = "FFmpeg 音频流解复用与重编码",
                )
            }

            // WebM 目标优先 LiTr
            if (outputFormat == FileFormat.WEBM) {
                return EngineDecision(
                    primaryEngine = EngineType.LITR,
                    fallbackEngine = EngineType.FFMPEG_KIT,
                    reason = "LiTr VP8 硬件编码",
                )
            }

            // MP4 目标：有 H.264 硬件编码器走 Media3
            if (outputFormat == FileFormat.MP4 && hwProfile.hasH264HardwareEncoder) {
                return EngineDecision(
                    primaryEngine = EngineType.MEDIA3_MEDIACODEC,
                    fallbackEngine = EngineType.FFMPEG_KIT,
                    reason = "Android Media3 / MediaCodec 芯片级硬件加速",
                )
            }

            return EngineDecision(
                primaryEngine = EngineType.FFMPEG_KIT,
                reason = "FFmpeg 兼容模式",
            )
        }

        // 6. 音频转换
        if (inputFormat.category == FileCategory.AUDIO) {
            return EngineDecision(
                primaryEngine = EngineType.FFMPEG_KIT,
                reason = "FFmpegKit 全能音频编码",
            )
        }

        return EngineDecision(
            primaryEngine = EngineType.FFMPEG_KIT,
            reason = "通用转换引擎",
        )
    }
}
