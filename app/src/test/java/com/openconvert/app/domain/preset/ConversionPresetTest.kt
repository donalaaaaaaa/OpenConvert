package com.openconvert.app.domain.preset

import com.openconvert.app.domain.model.FileCategory
import com.openconvert.app.domain.model.FileFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversionPresetTest {

    @Test
    fun testBuiltInPresetsRepository() {
        val imagePresets = PresetRepository.presetsFor(FileCategory.IMAGE)
        assertTrue(imagePresets.isNotEmpty())
        assertTrue(imagePresets.any { it.targetFormat == FileFormat.WEBP })

        val videoPresets = PresetRepository.presetsFor(FileCategory.VIDEO)
        assertTrue(videoPresets.isNotEmpty())
        assertTrue(videoPresets.any { it.isDefault })

        val audioPresets = PresetRepository.presetsFor(FileCategory.AUDIO)
        assertTrue(audioPresets.isNotEmpty())
        assertTrue(audioPresets.any { it.targetFormat == FileFormat.MP3 })
    }

    @Test
    fun testFindPresetById() {
        val smallImg = PresetRepository.BUILT_IN_PRESETS.firstOrNull { it.id == "img_small" }
        assertNotNull(smallImg)
        assertEquals(FileFormat.WEBP, smallImg?.targetFormat)
    }
}
