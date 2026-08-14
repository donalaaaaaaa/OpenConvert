package com.openconvert.app.domain.converter

import com.openconvert.app.domain.model.QualityPreset
import com.openconvert.app.domain.model.ResolutionPreset

object LitrWebmFormats {
    const val VIDEO_MIME = "video/x-vnd.on2.vp8"
    const val AUDIO_MIME_OPUS = "audio/opus"
    const val AUDIO_MIME_VORBIS = "audio/vorbis"

    fun scaledSize(width: Int, height: Int, resolution: ResolutionPreset): Pair<Int, Int> {
        val scale = resolution.scalePercent / 100.0
        val outWidth = (width * scale).toInt().coerceAtLeast(2) and 1.inv()
        val outHeight = (height * scale).toInt().coerceAtLeast(2) and 1.inv()
        return outWidth to outHeight
    }

    fun videoBitrateBps(quality: QualityPreset, sourceBitrate: Long?): Int {
        val source = sourceBitrate?.takeIf { it > 0 } ?: 4_000_000L
        val factor = when (quality) {
            QualityPreset.HIGH -> 0.80
            QualityPreset.BALANCED -> 0.45
            QualityPreset.SMALL -> 0.25
        }
        return (source * factor).toLong().coerceIn(600_000L, 12_000_000L).toInt()
    }

    fun audioBitrateBps(quality: QualityPreset): Int = when (quality) {
        QualityPreset.HIGH -> 128_000
        QualityPreset.BALANCED -> 96_000
        QualityPreset.SMALL -> 64_000
    }
}
