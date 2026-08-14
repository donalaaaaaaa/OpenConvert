package com.openconvert.app.domain.converter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Media3TranscoderBitrateTest {
    @Test
    fun `parses ffmpeg-style bitrate specs`() {
        assertEquals(1_234_000, Media3Transcoder.parseBitrateBps("1234k"))
        assertEquals(2_000_000, Media3Transcoder.parseBitrateBps("2m"))
        assertEquals(500_000, Media3Transcoder.parseBitrateBps("500K"))
        assertEquals(8_000_000, Media3Transcoder.parseBitrateBps("8000000"))
    }

    @Test
    fun `rejects invalid specs`() {
        assertNull(Media3Transcoder.parseBitrateBps(null))
        assertNull(Media3Transcoder.parseBitrateBps(""))
        assertNull(Media3Transcoder.parseBitrateBps("abc"))
        assertNull(Media3Transcoder.parseBitrateBps("k"))
        assertNull(Media3Transcoder.parseBitrateBps("-100k"))
    }
}
