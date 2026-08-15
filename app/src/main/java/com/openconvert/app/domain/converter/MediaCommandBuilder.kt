package com.openconvert.app.domain.converter

import com.openconvert.app.domain.model.FileFormat
import com.openconvert.app.domain.model.QualityPreset
import com.openconvert.app.domain.model.ResolutionPreset

internal object MediaCommandBuilder {
    fun build(
        inputPath: String,
        outputPath: String,
        target: FileFormat,
        quality: QualityPreset,
        resolution: ResolutionPreset,
        plan: EncodePlan = MediaEncodePlanner.plan(target, quality, resolution),
    ): Array<String> {
        val arguments = mutableListOf(
            "-y",
            "-nostdin",
            "-hide_banner",
            "-i",
            inputPath,
        )

        when (plan.mode) {
            EncodeMode.AUDIO_ONLY -> addAudio(arguments, target, quality)
            EncodeMode.AUDIO_COPY -> addAudioCopy(arguments, target)
            EncodeMode.REMUX -> arguments += listOf(
                "-map", "0:v:0",
                "-map", "0:a:0?",
                "-c", "copy",
                "-movflags", "+faststart",
            )
            EncodeMode.COPY_VIDEO -> {
                arguments += listOf("-map", "0:v:0", "-map", "0:a:0?", "-c:v", "copy")
                addAac(arguments, quality)
                arguments += listOf("-movflags", "+faststart")
            }
            EncodeMode.HARDWARE_H264 -> {
                addScale(arguments, resolution)
                arguments += listOf(
                    "-c:v", "h264_mediacodec",
                    "-b:v", plan.videoBitrate ?: "4000k",
                    "-maxrate", plan.videoBitrate ?: "4000k",
                )
                addAac(arguments, quality)
                arguments += listOf("-movflags", "+faststart")
            }
            EncodeMode.SOFTWARE_MPEG4 -> {
                addScale(arguments, resolution)
                arguments += listOf(
                    "-c:v", "mpeg4",
                    "-q:v", mpeg4Quality(quality),
                    "-threads", "0",
                )
                addAac(arguments, quality)
                arguments += listOf("-movflags", "+faststart")
            }
            EncodeMode.FAST_VP8 -> {
                addScale(arguments, resolution)
                arguments += listOf(
                    "-c:v", "libvpx",
                    "-deadline", "realtime",
                    "-cpu-used", "8",
                    "-lag-in-frames", "0",
                    "-auto-alt-ref", "0",
                    "-b:v", plan.videoBitrate ?: "2500k",
                    "-c:a", "libopus",
                    "-b:a", audioBitrate(quality),
                )
            }
            EncodeMode.LITR_VP8 -> Unit
        }

        arguments += outputPath
        return arguments.toTypedArray()
    }

    private fun addAudio(arguments: MutableList<String>, target: FileFormat, quality: QualityPreset) {
        when (target) {
            FileFormat.MP3 -> arguments += listOf("-vn", "-c:a", "libmp3lame", "-b:a", audioBitrate(quality))
            FileFormat.AAC -> arguments += listOf("-vn", "-c:a", "aac", "-b:a", audioBitrate(quality), "-f", "adts")
            FileFormat.WAV -> arguments += listOf("-vn", "-c:a", "pcm_s16le")
            FileFormat.FLAC -> arguments += listOf("-vn", "-c:a", "flac")
            FileFormat.M4A -> arguments += listOf("-vn", "-c:a", "aac", "-b:a", audioBitrate(quality))
            FileFormat.OGG -> arguments += listOf("-vn", "-c:a", "libvorbis", "-b:a", audioBitrate(quality))
            FileFormat.OPUS -> arguments += listOf("-vn", "-c:a", "libopus", "-b:a", audioBitrate(quality))
            else -> error("目标格式不是受支持的音频格式")
        }
    }

    /** 同编码直拷：demux → copy stream，只换容器（清单 §3）。 */
    private fun addAudioCopy(arguments: MutableList<String>, target: FileFormat) {
        arguments += listOf("-vn", "-c:a", "copy")
        if (target == FileFormat.AAC) {
            arguments += listOf("-f", "adts")
        }
    }

    private fun addAac(arguments: MutableList<String>, quality: QualityPreset) {
        arguments += listOf("-c:a", "aac", "-b:a", audioBitrate(quality))
    }

    private fun addScale(arguments: MutableList<String>, resolution: ResolutionPreset) {
        if (resolution == ResolutionPreset.ORIGINAL) return
        val ratio = resolution.scalePercent / 100.0
        arguments += listOf(
            "-vf",
            "scale=trunc(iw*$ratio/2)*2:trunc(ih*$ratio/2)*2",
        )
    }

    private fun audioBitrate(quality: QualityPreset): String = when (quality) {
        QualityPreset.HIGH -> "256k"
        QualityPreset.BALANCED -> "192k"
        QualityPreset.SMALL -> "128k"
    }

    private fun mpeg4Quality(quality: QualityPreset): String = when (quality) {
        QualityPreset.HIGH -> "2"
        QualityPreset.BALANCED -> "5"
        QualityPreset.SMALL -> "8"
    }

    private fun vp9Crf(quality: QualityPreset): String = when (quality) {
        QualityPreset.HIGH -> "28"
        QualityPreset.BALANCED -> "36"
        QualityPreset.SMALL -> "42"
    }
}
