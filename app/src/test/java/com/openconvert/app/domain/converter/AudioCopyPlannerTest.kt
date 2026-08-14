package com.openconvert.app.domain.converter

import com.openconvert.app.domain.model.FileFormat
import com.openconvert.app.domain.model.QualityPreset
import com.openconvert.app.domain.model.ResolutionPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioCopyPlannerTest {
    @Test
    fun `m4a aac to aac copies the stream instead of reencoding`() {
        val plan = MediaEncodePlanner.plan(
            FileFormat.AAC,
            QualityPreset.BALANCED,
            ResolutionPreset.ORIGINAL,
            StreamCodecs(audioCodec = "aac", fileSize = 10_000_000),
        )
        assertEquals(EncodeMode.AUDIO_COPY, plan.mode)
    }

    @Test
    fun `m4a aac to m4a copies the stream`() {
        val plan = MediaEncodePlanner.plan(
            FileFormat.M4A,
            QualityPreset.HIGH,
            ResolutionPreset.ORIGINAL,
            StreamCodecs(audioCodec = "aac"),
        )
        assertEquals(EncodeMode.AUDIO_COPY, plan.mode)
    }

    @Test
    fun `flac to flac and mp3 to mp3 copy`() {
        assertEquals(
            EncodeMode.AUDIO_COPY,
            MediaEncodePlanner.plan(FileFormat.FLAC, QualityPreset.BALANCED, ResolutionPreset.ORIGINAL, StreamCodecs(audioCodec = "flac")).mode,
        )
        assertEquals(
            EncodeMode.AUDIO_COPY,
            MediaEncodePlanner.plan(FileFormat.MP3, QualityPreset.BALANCED, ResolutionPreset.ORIGINAL, StreamCodecs(audioCodec = "mp3")).mode,
        )
    }

    @Test
    fun `wav pcm to wav copies without reencoding`() {
        val plan = MediaEncodePlanner.plan(
            FileFormat.WAV,
            QualityPreset.BALANCED,
            ResolutionPreset.ORIGINAL,
            StreamCodecs(audioCodec = "pcm_s16le"),
        )
        assertEquals(EncodeMode.AUDIO_COPY, plan.mode)
    }

    @Test
    fun `different codecs reencode`() {
        val plan = MediaEncodePlanner.plan(
            FileFormat.MP3,
            QualityPreset.BALANCED,
            ResolutionPreset.ORIGINAL,
            StreamCodecs(audioCodec = "aac"),
        )
        assertEquals(EncodeMode.AUDIO_ONLY, plan.mode)
    }

    @Test
    fun `unknown audio codec falls back to reencode`() {
        val plan = MediaEncodePlanner.plan(
            FileFormat.MP3,
            QualityPreset.BALANCED,
            ResolutionPreset.ORIGINAL,
            StreamCodecs(audioCodec = null),
        )
        assertEquals(EncodeMode.AUDIO_ONLY, plan.mode)
    }

    @Test
    fun `audio copy failure falls back to reencode`() {
        assertEquals(
            EncodeMode.AUDIO_ONLY,
            MediaEncodePlanner.fallback(EncodePlan(EncodeMode.AUDIO_COPY))?.mode,
        )
        assertNull(MediaEncodePlanner.fallback(EncodePlan(EncodeMode.AUDIO_ONLY)))
    }
}

class AudioCopyCommandTest {
    @Test
    fun `m4a to aac emits stream copy with adts container`() {
        val command = MediaCommandBuilder.build(
            "input.m4a",
            "output.aac",
            FileFormat.AAC,
            QualityPreset.BALANCED,
            ResolutionPreset.ORIGINAL,
            EncodePlan(EncodeMode.AUDIO_COPY),
        ).toList()

        assertEquals(listOf("-vn", "-c:a", "copy", "-f", "adts"), command.drop(5).dropLast(1))
    }

    @Test
    fun `m4a to m4a emits stream copy without forcing a container`() {
        val command = MediaCommandBuilder.build(
            "input.m4a",
            "output.m4a",
            FileFormat.M4A,
            QualityPreset.HIGH,
            ResolutionPreset.ORIGINAL,
            EncodePlan(EncodeMode.AUDIO_COPY),
        ).toList()

        assertEquals(listOf("-vn", "-c:a", "copy"), command.drop(5).dropLast(1))
    }
}
