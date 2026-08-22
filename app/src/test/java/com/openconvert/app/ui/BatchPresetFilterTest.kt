package com.openconvert.app.ui

import com.openconvert.app.domain.model.FileCategory
import com.openconvert.app.domain.model.FileFormat
import com.openconvert.app.domain.preset.Preset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchPresetFilterTest {

    private val videoMp3 = Preset(
        id = "video_mp3",
        category = FileCategory.VIDEO,
        name = "提取音频",
        description = "视频转 MP3",
        targetFormat = FileFormat.MP3,
    )
    private val audioMp3 = Preset(
        id = "audio_mp3",
        category = FileCategory.AUDIO,
        name = "MP3 音频",
        description = "音频转 MP3",
        targetFormat = FileFormat.MP3,
    )

    @Test
    fun `video batch does not show an audio preset with the same target`() {
        val available = availableBatchPresets(
            sourceFormats = listOf(FileFormat.MKV, FileFormat.MOV),
            commonFormats = listOf(FileFormat.MP4, FileFormat.MP3),
            presets = listOf(audioMp3, videoMp3),
        )

        assertEquals(listOf(videoMp3), available)
    }

    @Test
    fun `mixed categories never expose a batch preset`() {
        val available = availableBatchPresets(
            sourceFormats = listOf(FileFormat.MKV, FileFormat.WAV),
            commonFormats = listOf(FileFormat.MP3),
            presets = listOf(audioMp3, videoMp3),
        )

        assertTrue(available.isEmpty())
    }

    @Test
    fun `preset target must be common to every selected file`() {
        val available = availableBatchPresets(
            sourceFormats = listOf(FileFormat.MKV, FileFormat.MOV),
            commonFormats = listOf(FileFormat.MP4),
            presets = listOf(videoMp3),
        )

        assertTrue(available.isEmpty())
    }
}
