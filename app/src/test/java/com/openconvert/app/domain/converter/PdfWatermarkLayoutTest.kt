package com.openconvert.app.domain.converter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfWatermarkLayoutTest {

    @Test
    fun diagonalSitsInsidePageAndRotates() {
        val placement = PdfWatermarkLayout.place(600f, 800f, PdfWatermarkPosition.DIAGONAL, 8)
        assertTrue(placement.x in 0f..600f)
        assertTrue(placement.y in 0f..800f)
        assertEquals(35f, placement.rotationDeg, 0.01f)
        assertTrue(placement.fontSize >= 18f)
    }

    @Test
    fun footerIsNearTheBottom() {
        val placement = PdfWatermarkLayout.place(600f, 800f, PdfWatermarkPosition.FOOTER, 20)
        assertTrue(placement.y < 800f * 0.15f)
        assertEquals(0f, placement.rotationDeg, 0.01f)
        assertEquals(12f, placement.fontSize, 0.01f)
    }

    @Test
    fun unknownTokenFallsBackToDiagonal() {
        assertEquals(PdfWatermarkPosition.DIAGONAL, PdfWatermarkLayout.parsePosition("NOPE"))
        assertEquals(PdfWatermarkPosition.CENTER, PdfWatermarkLayout.parsePosition("CENTER"))
    }
}
