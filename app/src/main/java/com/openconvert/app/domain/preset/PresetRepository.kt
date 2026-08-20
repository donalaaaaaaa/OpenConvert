package com.openconvert.app.domain.preset

import com.openconvert.app.domain.model.FileCategory
import com.openconvert.app.domain.model.FileFormat
import com.openconvert.app.domain.model.QualityPreset
import com.openconvert.app.domain.model.ResolutionPreset

/**
 * 内置预设清单（计划书 §8.1 / §8.2）。
 *
 * 内置项在首次启动时写入 Room（见 PresetStore.seedIfEmpty），
 * 之后与用户自定义预设一视同仁地从库里读——UI 不区分来源，
 * 只用 [Preset.isBuiltIn] 决定能否删除。
 */
object PresetRepository {

    val BUILT_IN_PRESETS: List<Preset> = listOf(
        // ---- 图片（§8.1）----
        Preset(
            id = "img_wechat",
            category = FileCategory.IMAGE,
            name = "微信发送",
            description = "JPEG · 质量 85% · 最长边 1920 · 去 EXIF",
            targetFormat = FileFormat.JPG,
            quality = QualityPreset.HIGH,
            longestEdgePx = 1920,
            stripMetadata = true,
            isDefault = true,
        ),
        Preset(
            id = "img_web",
            category = FileCategory.IMAGE,
            name = "网页图片",
            description = "WEBP · 质量 80% · 适合网络传输",
            targetFormat = FileFormat.WEBP,
            quality = QualityPreset.BALANCED,
        ),
        Preset(
            id = "img_avatar",
            category = FileCategory.IMAGE,
            name = "头像",
            description = "JPEG · 1:1 方形 · 1024×1024",
            targetFormat = FileFormat.JPG,
            quality = QualityPreset.HIGH,
            fixedWidthPx = 1024,
            fixedHeightPx = 1024,
            cropAspect = "1:1",
        ),
        Preset(
            id = "img_original",
            category = FileCategory.IMAGE,
            name = "原始质量",
            description = "PNG 无损 · 保持原尺寸",
            targetFormat = FileFormat.PNG,
            quality = QualityPreset.HIGH,
        ),
        Preset(
            id = "img_privacy",
            category = FileCategory.IMAGE,
            name = "隐私分享",
            description = "擦除 EXIF / GPS / 拍摄参数",
            targetFormat = FileFormat.JPG,
            quality = QualityPreset.BALANCED,
            stripMetadata = true,
        ),

        // ---- 视频（§8.2）----
        Preset(
            id = "video_small",
            category = FileCategory.VIDEO,
            name = "小体积",
            description = "720P · H.264 · AAC",
            targetFormat = FileFormat.MP4,
            quality = QualityPreset.SMALL,
            resolution = ResolutionPreset.SMALL,
        ),
        Preset(
            id = "video_hd",
            category = FileCategory.VIDEO,
            name = "高清",
            description = "1080P · H.264 · AAC",
            targetFormat = FileFormat.MP4,
            quality = QualityPreset.BALANCED,
            isDefault = true,
        ),
        Preset(
            id = "video_high_quality",
            category = FileCategory.VIDEO,
            name = "高质量",
            description = "原分辨率 · 高码率 · AAC",
            targetFormat = FileFormat.MP4,
            quality = QualityPreset.HIGH,
        ),

        // ---- 音频 ----
        Preset(
            id = "audio_lossless",
            category = FileCategory.AUDIO,
            name = "无损母带",
            description = "FLAC · 保留全部细节",
            targetFormat = FileFormat.FLAC,
            quality = QualityPreset.HIGH,
        ),
        Preset(
            id = "audio_high",
            category = FileCategory.AUDIO,
            name = "高保真 MP3",
            description = "MP3 · 320Kbps",
            targetFormat = FileFormat.MP3,
            quality = QualityPreset.HIGH,
            isDefault = true,
        ),
        Preset(
            id = "audio_standard",
            category = FileCategory.AUDIO,
            name = "标准音质",
            description = "MP3 · 192Kbps",
            targetFormat = FileFormat.MP3,
            quality = QualityPreset.BALANCED,
        ),
        Preset(
            id = "audio_speech",
            category = FileCategory.AUDIO,
            name = "语音便携",
            description = "AAC · 适合人声录音",
            targetFormat = FileFormat.AAC,
            quality = QualityPreset.SMALL,
        ),
    )

    fun presetsFor(category: FileCategory): List<Preset> =
        BUILT_IN_PRESETS.filter { it.category == category }

    fun byId(id: String): Preset? = BUILT_IN_PRESETS.firstOrNull { it.id == id }
}
