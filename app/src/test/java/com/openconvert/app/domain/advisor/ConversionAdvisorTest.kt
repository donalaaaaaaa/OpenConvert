package com.openconvert.app.domain.advisor

import com.openconvert.app.domain.model.FileFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversionAdvisorTest {

    @Test
    fun testBmpToWebpAdvice() {
        val analysis = FileAnalysis(
            format = FileFormat.BMP,
            fileSizeBytes = 10 * 1024 * 1024L,
            width = 3000,
            height = 2000,
        )
        val rec = ConversionAdvisor.advise(analysis)
        assertNotNull(rec)
        assertEquals(FileFormat.WEBP, rec?.suggestedFormat)
        assertTrue((rec?.estimatedReductionPercent ?: 0) >= 70)
    }

    @Test
    fun testLargePngToWebpAdvice() {
        val analysis = FileAnalysis(
            format = FileFormat.PNG,
            fileSizeBytes = 8 * 1024 * 1024L,
            width = 4000,
            height = 3000,
        )
        val rec = ConversionAdvisor.advise(analysis)
        assertNotNull(rec)
        assertEquals(FileFormat.WEBP, rec?.suggestedFormat)
        assertTrue((rec?.estimatedReductionPercent ?: 0) >= 60)
    }

    @Test
    fun testFlacToMp3Advice() {
        val analysis = FileAnalysis(
            format = FileFormat.FLAC,
            fileSizeBytes = 45 * 1024 * 1024L,
        )
        val rec = ConversionAdvisor.advise(analysis)
        assertNotNull(rec)
        assertEquals(FileFormat.MP3, rec?.suggestedFormat)
        assertTrue((rec?.estimatedReductionPercent ?: 0) >= 70)
    }
}
