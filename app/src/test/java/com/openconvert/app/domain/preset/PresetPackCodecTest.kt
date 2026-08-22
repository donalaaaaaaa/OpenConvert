package com.openconvert.app.domain.preset

import com.openconvert.app.domain.model.FileCategory
import com.openconvert.app.domain.model.FileFormat
import com.openconvert.app.domain.model.QualityPreset
import com.openconvert.app.domain.model.ResolutionPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetPackCodecTest {

    private fun custom() = Preset(
        id = "custom_abc",
        category = FileCategory.IMAGE,
        name = "朋友圈",
        description = "JPEG · 最长边 1280",
        targetFormat = FileFormat.JPG,
        quality = QualityPreset.BALANCED,
        longestEdgePx = 1280,
        stripMetadata = true,
        isBuiltIn = false,
        createdAt = 1_700_000_000_000L,
    )

    @Test
    fun roundTripKeepsCustomFields() {
        val json = PresetPackCodec.encode(listOf(custom()))
        val pack = PresetPackCodec.decode(json).getOrThrow()
        assertEquals(1, pack.presets.size)
        val decoded = pack.presets.single()
        assertEquals("朋友圈", decoded.name)
        assertEquals(1280, decoded.longestEdgePx)
        assertEquals(true, decoded.stripMetadata)
        assertEquals(FileFormat.JPG, decoded.targetFormat)
        assertEquals(false, decoded.isBuiltIn)
    }

    @Test
    fun encodeMarksFormatAndVersion() {
        val json = PresetPackCodec.encode(emptyList())
        assertTrue(json.contains("\"openconvert.presets\""))
        assertTrue(json.contains("\"version\""))
    }

    @Test
    fun rejectsUnknownFormat() {
        val result = PresetPackCodec.decode("""{"format":"other","version":1,"presets":[]}""")
        assertTrue(result.isFailure)
    }

    @Test
    fun skipsBrokenPresetEntries() {
        val json = """
            {
              "format":"openconvert.presets",
              "version":1,
              "presets":[
                {"name":"ok","category":"IMAGE","targetFormat":"PNG","quality":"HIGH","resolution":"ORIGINAL"},
                {"name":"bad","category":"NOPE","targetFormat":"JPG"}
              ]
            }
        """.trimIndent()
        val pack = PresetPackCodec.decode(json).getOrThrow()
        assertEquals(1, pack.presets.size)
        assertEquals("ok", pack.presets.single().name)
        assertEquals(FileFormat.PNG, pack.presets.single().targetFormat)
        assertEquals(QualityPreset.HIGH, pack.presets.single().quality)
        assertEquals(ResolutionPreset.ORIGINAL, pack.presets.single().resolution)
    }
}
