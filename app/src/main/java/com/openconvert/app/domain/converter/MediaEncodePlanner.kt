package com.openconvert.app.domain.converter

import com.openconvert.app.domain.model.FileFormat
import com.openconvert.app.domain.model.QualityPreset
import com.openconvert.app.domain.model.ResolutionPreset

enum class EncodeMode {
    REMUX,
    COPY_VIDEO,
    AUDIO_COPY,
    HARDWARE_H264,
    SOFTWARE_MPEG4,
    LITR_VP8,
    FAST_VP8,
    AUDIO_ONLY,
}

data class StreamCodecs(
    val videoCodec: String? = null,
    val audioCodec: String? = null,
    val videoBitrate: Long? = null,
    val durationMs: Long? = null,
    val fileSize: Long = 0L,
)

data class EncodePlan(
    val mode: EncodeMode,
    val videoBitrate: String? = null,
)

object MediaEncodePlanner {
    private val mp4Video = setOf("h264", "avc1", "hevc", "h265", "hev1", "hvc1", "mpeg4")
    private val mp4Audio = setOf("aac", "mp3", "alac")
    private val webmVideo = setOf("vp8", "vp9", "av1")
    private val webmAudio = setOf("opus", "vorbis")

    fun plan(
        target: FileFormat,
        quality: QualityPreset,
        resolution: ResolutionPreset,
        codecs: StreamCodecs? = null,
    ): EncodePlan {
        if (target.categoryIsAudio()) {
            return if (canAudioCopy(target, codecs)) {
                EncodePlan(EncodeMode.AUDIO_COPY)
            } else {
                EncodePlan(EncodeMode.AUDIO_ONLY)
            }
        }
        if (target == FileFormat.WEBM) {
            if (canRemux(codecs, webmVideo, webmAudio, quality, resolution)) {
                return EncodePlan(EncodeMode.REMUX)
            }
            return EncodePlan(EncodeMode.LITR_VP8, videoBitrate(quality, codecs))
        }
        if (target == FileFormat.MP4) {
            if (canRemux(codecs, mp4Video, mp4Audio, quality, resolution)) {
                return EncodePlan(EncodeMode.REMUX)
            }
            if (canCopyVideo(codecs, mp4Video, quality, resolution)) {
                return EncodePlan(EncodeMode.COPY_VIDEO)
            }
            return EncodePlan(EncodeMode.HARDWARE_H264, videoBitrate(quality, codecs))
        }
        return EncodePlan(EncodeMode.SOFTWARE_MPEG4)
    }

    fun fallback(plan: EncodePlan): EncodePlan? = when (plan.mode) {
        EncodeMode.HARDWARE_H264 -> EncodePlan(EncodeMode.SOFTWARE_MPEG4)
        EncodeMode.LITR_VP8 -> EncodePlan(EncodeMode.FAST_VP8, plan.videoBitrate)
        EncodeMode.AUDIO_COPY -> EncodePlan(EncodeMode.AUDIO_ONLY)
        else -> null
    }

    /**
     * 同编码转换优先 demux → copy stream，绝不重新编码（清单 §3）。
     * M4A(AAC) → AAC / M4A，FLAC → FLAC，MP3 → MP3 都是纯换容器。
     */
    private fun canAudioCopy(target: FileFormat, codecs: StreamCodecs?): Boolean {
        val source = codecs?.audioCodec?.lowercase() ?: return false
        return when (target) {
            FileFormat.M4A, FileFormat.AAC -> source == "aac"
            FileFormat.MP3 -> source == "mp3"
            FileFormat.FLAC -> source == "flac"
            FileFormat.WAV -> source.startsWith("pcm")
            FileFormat.OGG -> source == "vorbis" || source == "opus"
            FileFormat.OPUS -> source == "opus"
            else -> false
        }
    }

    private fun canRemux(
        codecs: StreamCodecs?,
        videos: Set<String>,
        audios: Set<String>,
        quality: QualityPreset,
        resolution: ResolutionPreset,
    ): Boolean {
        if (codecs == null || quality == QualityPreset.SMALL || resolution != ResolutionPreset.ORIGINAL) {
            return false
        }
        val video = codecs.videoCodec?.lowercase() ?: return false
        val audio = codecs.audioCodec?.lowercase()
        return video in videos && (audio == null || audio in audios)
    }

    private fun canCopyVideo(
        codecs: StreamCodecs?,
        videos: Set<String>,
        quality: QualityPreset,
        resolution: ResolutionPreset,
    ): Boolean {
        if (codecs == null || quality == QualityPreset.SMALL || resolution != ResolutionPreset.ORIGINAL) {
            return false
        }
        val video = codecs.videoCodec?.lowercase() ?: return false
        return video in videos
    }

    private fun videoBitrate(quality: QualityPreset, codecs: StreamCodecs?): String {
        val fromStream = codecs?.videoBitrate?.takeIf { it > 0 }
        val fromSize = if (codecs != null && (codecs.durationMs ?: 0L) > 0L && codecs.fileSize > 0L) {
            codecs.fileSize * 8_000L / codecs.durationMs!!
        } else {
            null
        }
        val source = fromStream ?: fromSize ?: 4_000_000L
        val factor = when (quality) {
            QualityPreset.HIGH -> 1.0
            QualityPreset.BALANCED -> 0.55
            QualityPreset.SMALL -> 0.30
        }
        val kbps = ((source * factor) / 1000.0).toLong().coerceIn(600L, 20_000L)
        return "${kbps}k"
    }

    private fun FileFormat.categoryIsAudio(): Boolean = this.category == com.openconvert.app.domain.model.FileCategory.AUDIO
}
