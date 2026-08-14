package com.openconvert.app.domain.converter

import com.openconvert.app.domain.model.FileFormat
import com.openconvert.app.domain.model.QualityPreset
import com.openconvert.app.domain.model.ResolutionPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaEncodePlannerTest {
    private val phoneVideo = StreamCodecs(
        videoCodec = "h264",
        audioCodec = "aac",
        videoBitrate = 8_000_000,
        durationMs = 180_000,
        fileSize = 1_000L * 1024 * 1024,
    )

    @Test
    fun `mov to mp4 remuxes compatible phone video`() {
        val plan = MediaEncodePlanner.plan(
            FileFormat.MP4,
            QualityPreset.BALANCED,
            ResolutionPreset.ORIGINAL,
            phoneVideo,
        )
        assertEquals(EncodeMode.REMUX, plan.mode)
    }

    @Test
    fun `compression preset does not remux`() {
        val plan = MediaEncodePlanner.plan(
            FileFormat.MP4,
            QualityPreset.SMALL,
            ResolutionPreset.ORIGINAL,
            phoneVideo,
        )
        assertEquals(EncodeMode.HARDWARE_H264, plan.mode)
        assertTrue(plan.videoBitrate!!.endsWith("k"))
    }

    @Test
    fun `pcm audio keeps video copy and reencodes audio`() {
        val plan = MediaEncodePlanner.plan(
            FileFormat.MP4,
            QualityPreset.HIGH,
            ResolutionPreset.ORIGINAL,
            phoneVideo.copy(audioCodec = "pcm_s16le"),
        )
        assertEquals(EncodeMode.COPY_VIDEO, plan.mode)
    }

    @Test
    fun `unknown video uses hardware h264`() {
        val plan = MediaEncodePlanner.plan(
            FileFormat.MP4,
            QualityPreset.BALANCED,
            ResolutionPreset.ORIGINAL,
            StreamCodecs(videoCodec = "prores", audioCodec = "aac"),
        )
        assertEquals(EncodeMode.HARDWARE_H264, plan.mode)
    }

    @Test
    fun `mp4 to webm uses litr vp8 not vp9`() {
        val plan = MediaEncodePlanner.plan(
            FileFormat.WEBM,
            QualityPreset.BALANCED,
            ResolutionPreset.ORIGINAL,
            phoneVideo,
        )
        assertEquals(EncodeMode.LITR_VP8, plan.mode)
        assertEquals(EncodeMode.FAST_VP8, MediaEncodePlanner.fallback(plan)?.mode)
    }

    @Test
    fun `audio targets stay audio-only`() {
        assertEquals(
            EncodeMode.AUDIO_ONLY,
            MediaEncodePlanner.plan(FileFormat.MP3, QualityPreset.BALANCED, ResolutionPreset.ORIGINAL).mode,
        )
    }

    @Test
    fun `hardware failure falls back to software`() {
        val fallback = MediaEncodePlanner.fallback(EncodePlan(EncodeMode.HARDWARE_H264, "4000k"))
        assertEquals(EncodeMode.SOFTWARE_MPEG4, fallback?.mode)
    }
}

class MediaCommandBuilderTest {
    @Test
    fun `mp3 extraction disables video and uses selected bitrate`() {
        val command = MediaCommandBuilder.build(
            "input.mp4",
            "output.mp3",
            FileFormat.MP3,
            QualityPreset.HIGH,
            ResolutionPreset.ORIGINAL,
        ).toList()

        assertTrue(command.containsAll(listOf("-vn", "libmp3lame", "256k")))
    }

    @Test
    fun `webm conversion uses realtime vp8 because mp4 cannot be remuxed`() {
        val command = MediaCommandBuilder.build(
            "input.mp4",
            "output.webm",
            FileFormat.WEBM,
            QualityPreset.BALANCED,
            ResolutionPreset.MEDIUM,
            EncodePlan(EncodeMode.FAST_VP8, "2500k"),
        ).toList()

        assertTrue(command.containsAll(listOf("libvpx", "realtime", "8", "libopus")))
        assertTrue(command.any { it.contains("trunc(iw*0.75/2)*2") })
        assertFalse(command.contains("libvpx-vp9"))
        assertFalse(command.contains("mpeg4"))
    }

    @Test
    fun `compatible mp4 remux copies bitstreams`() {
        val command = MediaCommandBuilder.build(
            "input.mov",
            "output.mp4",
            FileFormat.MP4,
            QualityPreset.BALANCED,
            ResolutionPreset.ORIGINAL,
            EncodePlan(EncodeMode.REMUX),
        ).toList()

        assertTrue(command.containsAll(listOf("-c", "copy", "+faststart")))
        assertFalse(command.contains("libvpx-vp9"))
        assertFalse(command.contains("mpeg4"))
        assertFalse(command.contains("h264_mediacodec"))
    }

    @Test
    fun `wav output uses uncompressed pcm`() {
        val command = MediaCommandBuilder.build(
            "input.flac",
            "output.wav",
            FileFormat.WAV,
            QualityPreset.SMALL,
            ResolutionPreset.ORIGINAL,
        ).toList()

        assertTrue(command.contains("pcm_s16le"))
    }
}
