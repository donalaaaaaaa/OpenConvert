package com.openconvert.app.domain.converter

import com.openconvert.app.domain.model.QualityPreset
import com.openconvert.app.domain.model.ResolutionPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LitrWebmFormatsTest {
    @Test
    fun `never targets vp9`() {
        assertEquals("video/x-vnd.on2.vp8", LitrWebmFormats.VIDEO_MIME)
        assertTrue(LitrWebmFormats.VIDEO_MIME.contains("vp8"))
        assertTrue(!LitrWebmFormats.VIDEO_MIME.contains("vp9"))
    }

    @Test
    fun `scales to even dimensions`() {
        val (width, height) = LitrWebmFormats.scaledSize(1920, 1080, ResolutionPreset.SMALL)
        assertEquals(960, width)
        assertEquals(540, height)
        assertEquals(0, width % 2)
        assertEquals(0, height % 2)
    }

    @Test
    fun `balanced bitrate stays below source`() {
        val bps = LitrWebmFormats.videoBitrateBps(QualityPreset.BALANCED, 8_000_000)
        assertEquals(3_600_000, bps)
    }
}
