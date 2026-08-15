package com.openconvert.app.domain.advisor

import com.openconvert.app.domain.device.DeviceCapabilities
import com.openconvert.app.domain.model.FileCategory
import com.openconvert.app.domain.model.FileFormat
import com.openconvert.app.domain.model.QualityPreset
import com.openconvert.app.domain.model.ResolutionPreset

data class FileAnalysis(
    val format: FileFormat,
    val fileSizeBytes: Long,
    val width: Int = 0,
    val height: Int = 0,
    val durationMs: Long = 0L,
    val hasAlpha: Boolean = false,
    val videoCodec: String? = null,
    val audioCodec: String? = null,
    val pageCount: Int = 0,
)

data class ConversionRecommendation(
    val suggestedFormat: FileFormat,
    val suggestedQuality: QualityPreset = QualityPreset.BALANCED,
    val suggestedResolution: ResolutionPreset = ResolutionPreset.ORIGINAL,
    val estimatedOutputSizeBytes: Long = 0L,
    val estimatedReductionPercent: Int = 0,
    val reason: String = "",
    val confidence: Float = 0.85f,
)

/**
 * 全本地智能转换建议顾问（计划书 §二十二～§二十六）。
 * 基于文件物理特征与本机硬件能力，运行离线规则引擎给出最佳转换建议。
 */
object ConversionAdvisor {

    fun advise(analysis: FileAnalysis): ConversionRecommendation? {
        return when (analysis.format.category) {
            FileCategory.IMAGE -> adviseImage(analysis)
            FileCategory.VIDEO -> adviseVideo(analysis)
            FileCategory.AUDIO -> adviseAudio(analysis)
            FileCategory.OFFICE -> adviseOffice(analysis)
            FileCategory.PDF -> advisePdf(analysis)
            else -> null
        }
    }

    private fun adviseImage(analysis: FileAnalysis): ConversionRecommendation {
        val size = analysis.fileSizeBytes
        val isLarge = size > 2 * 1024 * 1024L // > 2MB

        if (analysis.hasAlpha) {
            return ConversionRecommendation(
                suggestedFormat = FileFormat.WEBP,
                suggestedQuality = QualityPreset.BALANCED,
                suggestedResolution = ResolutionPreset.ORIGINAL,
                estimatedOutputSizeBytes = (size * 0.35).toLong(),
                estimatedReductionPercent = 65,
                reason = "包含透明通道，推荐转换为 WebP 格式，体积缩减约 65% 且保留透明度",
            )
        }

        if (analysis.format in setOf(FileFormat.BMP, FileFormat.TIFF, FileFormat.PNG) && isLarge) {
            return ConversionRecommendation(
                suggestedFormat = FileFormat.WEBP,
                suggestedQuality = QualityPreset.BALANCED,
                suggestedResolution = ResolutionPreset.ORIGINAL,
                estimatedOutputSizeBytes = (size * 0.22).toLong(),
                estimatedReductionPercent = 78,
                reason = "大尺寸未压缩图像，转换为 WebP 平衡模式可节省约 78% 存储空间",
            )
        }

        if (analysis.format == FileFormat.JPG && isLarge) {
            return ConversionRecommendation(
                suggestedFormat = FileFormat.WEBP,
                suggestedQuality = QualityPreset.BALANCED,
                suggestedResolution = ResolutionPreset.ORIGINAL,
                estimatedOutputSizeBytes = (size * 0.60).toLong(),
                estimatedReductionPercent = 40,
                reason = "高分辨率照片，转换为现代 WebP 格式可在保持视觉无损的前提下缩小体积",
            )
        }

        return ConversionRecommendation(
            suggestedFormat = FileFormat.WEBP,
            suggestedQuality = QualityPreset.BALANCED,
            suggestedResolution = ResolutionPreset.ORIGINAL,
            estimatedOutputSizeBytes = (size * 0.70).toLong(),
            estimatedReductionPercent = 30,
            reason = "推荐转为 WebP 获得更高效的存储和分享体验",
        )
    }

    private fun adviseVideo(analysis: FileAnalysis): ConversionRecommendation {
        val size = analysis.fileSizeBytes
        val hwProfile = DeviceCapabilities.getHardwareProfile()

        val isHighRes = analysis.width >= 3840 || analysis.height >= 2160 || (analysis.width > 1920 && analysis.height > 1080)
        val isLarge = size > 100 * 1024 * 1024L // > 100MB

        if (isHighRes || isLarge) {
            return ConversionRecommendation(
                suggestedFormat = FileFormat.MP4,
                suggestedQuality = QualityPreset.BALANCED,
                suggestedResolution = ResolutionPreset.MEDIUM,
                estimatedOutputSizeBytes = (size * 0.38).toLong(),
                estimatedReductionPercent = 62,
                reason = "超清大文件视频，建议转码为 1080P/75% MP4，利用芯片硬件加速大幅压缩体积",
            )
        }

        if (analysis.format in setOf(FileFormat.MKV, FileFormat.AVI, FileFormat.MOV)) {
            return ConversionRecommendation(
                suggestedFormat = FileFormat.MP4,
                suggestedQuality = QualityPreset.HIGH,
                suggestedResolution = ResolutionPreset.ORIGINAL,
                estimatedOutputSizeBytes = (size * 0.85).toLong(),
                estimatedReductionPercent = 15,
                reason = "转换为通用 MP4 格式，提升全平台播放兼容性",
            )
        }

        return ConversionRecommendation(
            suggestedFormat = FileFormat.MP4,
            suggestedQuality = QualityPreset.BALANCED,
            suggestedResolution = ResolutionPreset.ORIGINAL,
            estimatedOutputSizeBytes = (size * 0.75).toLong(),
            estimatedReductionPercent = 25,
            reason = "保持当前分辨率，优化编码码率以节省空间",
        )
    }

    private fun adviseAudio(analysis: FileAnalysis): ConversionRecommendation {
        val size = analysis.fileSizeBytes

        if (analysis.format in setOf(FileFormat.WAV, FileFormat.FLAC)) {
            return ConversionRecommendation(
                suggestedFormat = FileFormat.MP3,
                suggestedQuality = QualityPreset.HIGH,
                estimatedOutputSizeBytes = (size * 0.20).toLong(),
                estimatedReductionPercent = 80,
                reason = "无损音频转换为 320Kbps 高保真 MP3，大幅缩减体积且听感几乎一致",
            )
        }

        return ConversionRecommendation(
            suggestedFormat = FileFormat.AAC,
            suggestedQuality = QualityPreset.BALANCED,
            estimatedOutputSizeBytes = (size * 0.70).toLong(),
            estimatedReductionPercent = 30,
            reason = "转换为 AAC 格式，兼容性好且压缩率高",
        )
    }

    private fun advisePdf(analysis: FileAnalysis): ConversionRecommendation {
        val size = analysis.fileSizeBytes
        return ConversionRecommendation(
            suggestedFormat = FileFormat.PDF,
            estimatedOutputSizeBytes = (size * 0.45).toLong(),
            estimatedReductionPercent = 55,
            reason = "使用 PDF 智能压缩对内置图像降采样，显著降低文件体积",
        )
    }

    private fun adviseOffice(analysis: FileAnalysis): ConversionRecommendation {
        return ConversionRecommendation(
            suggestedFormat = FileFormat.PDF,
            reason = "使用内置 LibreOfficeKit 离线将 Office 文档导出为高保真 PDF",
        )
    }
}
