package com.openconvert.app.domain.converter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfPageRangesTest {
    @Test
    fun blankValueSelectsEveryPage() {
        assertEquals(listOf(PdfPageRange(1, 6)), parsePdfPageRanges("", 6).getOrThrow())
    }

    @Test
    fun parsesSinglePagesAndRanges() {
        val ranges = parsePdfPageRanges("1-3, 5，8-10", 10).getOrThrow()
        assertEquals(
            listOf(PdfPageRange(1, 3), PdfPageRange(5, 5), PdfPageRange(8, 10)),
            ranges,
        )
        assertEquals(listOf(1, 2, 3, 5, 8, 9, 10), flattenPdfPages(ranges))
    }

    @Test
    fun removesDuplicatePagesWhenExportingImages() {
        val ranges = parsePdfPageRanges("1-3,2-4", 4).getOrThrow()
        assertEquals(listOf(1, 2, 3, 4), flattenPdfPages(ranges))
    }

    @Test
    fun rejectsOutOfBoundsAndReverseRanges() {
        assertTrue(parsePdfPageRanges("0", 5).isFailure)
        assertTrue(parsePdfPageRanges("6", 5).isFailure)
        assertTrue(parsePdfPageRanges("4-2", 5).isFailure)
        assertTrue(parsePdfPageRanges("one", 5).isFailure)
    }
}
