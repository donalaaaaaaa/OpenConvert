package com.openconvert.app.domain.preset

import com.openconvert.app.domain.model.FileCategory
import com.openconvert.app.domain.model.FileFormat
import com.openconvert.app.domain.model.QualityPreset
import com.openconvert.app.domain.model.ResolutionPreset

data class Preset(
    val id: String,
    val category: FileCategory,
    val name: String,
    val description: String,
    val targetFormat: FileFormat,
    val quality: QualityPreset = QualityPreset.BALANCED,
    val resolution: ResolutionPreset = ResolutionPreset.ORIGINAL,
    val stripMetadata: Boolean = false,
    val isDefault: Boolean = false,
    val isBuiltIn: Boolean = true,
)

/**
 * 转换预设体系（计划书 §十二～§十六）。
 */
object PresetRepository {

    val BUILT_IN_PRESETS: List<Preset> = listOf(
        // 图片预设
        Preset(
            id = "img_original",
            category = FileCategory.IMAGE,
            name = "原始质量",
            description = "保持原有分辨率与高保真度",
            targetFormat = FileFormat.PNG,
            quality = QualityPreset.HIGH,
            resolution = ResolutionPreset.ORIGINAL,
        ),
        Preset(
            id = "img_balanced",
            category = FileCategory.IMAGE,
            name = "平衡推荐",
            description = "画质与体积的最佳均衡",
            targetFormat = FileFormat.JPG,
            quality = QualityPreset.BALANCED,
            resolution = ResolutionPreset.ORIGINAL,
            isDefault = true,
        ),
        Preset(
            id = "img_small",
            category = FileCategory.IMAGE,
            name = "小体积",
            description = "适合社交分享与网络传输",
            targetFormat = FileFormat.WEBP,
            quality = QualityPreset.SMALL,
            resolution = ResolutionPreset.MEDIUM,
        ),
        Preset(
            id = "img_privacy",
            category = FileCategory.IMAGE,
            name = "隐私分享",
            description = "自动擦除 EXIF、GPS 定位与拍摄参数",
            targetFormat = FileFormat.JPG,
            quality = QualityPreset.BALANCED,
            resolution = ResolutionPreset.ORIGINAL,
            stripMetadata = true,
        ),

        // 视频预设
        Preset(
            id = "video_original",
            category = FileCategory.VIDEO,
            name = "原画质",
            description = "保持原分辨率与帧率",
            targetFormat = FileFormat.MP4,
            quality = QualityPreset.HIGH,
            resolution = ResolutionPreset.ORIGINAL,
        ),
        Preset(
            id = "video_1080p",
            category = FileCategory.VIDEO,
            name = "高清模式",
            description = "兼顾清晰度与主流设备播放兼容性",
            targetFormat = FileFormat.MP4,
            quality = QualityPreset.BALANCED,
            resolution = ResolutionPreset.ORIGINAL,
            isDefault = true,
        ),
        Preset(
            id = "video_720p",
            category = FileCategory.VIDEO,
            name = "平衡压缩",
            description = "体积轻巧，适合快速传输",
            targetFormat = FileFormat.MP4,
            quality = QualityPreset.BALANCED,
            resolution = ResolutionPreset.MEDIUM,
        ),
        Preset(
            id = "video_small",
            category = FileCategory.VIDEO,
            name = "极限压缩",
            description = "大幅降低码率，显著减小体积",
            targetFormat = FileFormat.MP4,
            quality = QualityPreset.SMALL,
            resolution = ResolutionPreset.SMALL,
        ),

        // 音频预设
        Preset(
            id = "audio_flac",
            category = FileCategory.AUDIO,
            name = "无损母带",
            description = "FLAC 格式，保留全部音频细节",
            targetFormat = FileFormat.FLAC,
            quality = QualityPreset.HIGH,
        ),
        Preset(
            id = "audio_high",
            category = FileCategory.AUDIO,
            name = "高保真 MP3",
            description = "320Kbps 码率，高音质享受",
            targetFormat = FileFormat.MP3,
            quality = QualityPreset.HIGH,
            isDefault = true,
        ),
        Preset(
            id = "audio_balanced",
            category = FileCategory.AUDIO,
            name = "标准音质",
            description = "192Kbps 码率，通用兼容",
            targetFormat = FileFormat.MP3,
            quality = QualityPreset.BALANCED,
        ),
        Preset(
            id = "audio_speech",
            category = FileCategory.AUDIO,
            name = "语音便携",
            description = "AAC 格式，适合人声与录音",
            targetFormat = FileFormat.AAC,
            quality = QualityPreset.SMALL,
        ),
    )

    fun presetsFor(category: FileCategory): List<Preset> =
        BUILT_IN_PRESETS.filter { it.category == category }
}
