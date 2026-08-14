package com.openconvert.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficeFormatTest {
    @Test
    fun recognizesOfficeExtensions() {
        assertEquals(FileFormat.DOCX, FileFormat.fromFileName("report.docx"))
        assertEquals(FileFormat.PPTX, FileFormat.fromFileName("slides.pptx"))
        assertEquals(FileFormat.XLSX, FileFormat.fromFileName("data.xlsx"))
        assertEquals(FileFormat.DOCX, FileFormat.fromFileName("REPORT.DOCX"))
    }

    @Test
    fun officeCategoryIsOffice() {
        assertEquals(FileCategory.OFFICE, FileFormat.DOCX.category)
        assertEquals(FileCategory.OFFICE, FileFormat.PPTX.category)
        assertEquals(FileCategory.OFFICE, FileFormat.XLSX.category)
    }

    @Test
    fun officeTargetsOnlyPdf() {
        assertEquals(listOf(FileFormat.PDF), FileFormat.DOCX.availableTargets())
        assertEquals(listOf(FileFormat.PDF), FileFormat.PPTX.availableTargets())
        assertEquals(listOf(FileFormat.PDF), FileFormat.XLSX.availableTargets())
    }

    @Test
    fun officeCanConvertLocallyToPdfOnly() {
        assertTrue(FileFormat.DOCX.canConvertLocallyTo(FileFormat.PDF))
        assertTrue(FileFormat.PPTX.canConvertLocallyTo(FileFormat.PDF))
        assertTrue(FileFormat.XLSX.canConvertLocallyTo(FileFormat.PDF))
        assertFalse(FileFormat.DOCX.canConvertLocallyTo(FileFormat.PPTX))
        assertFalse(FileFormat.DOCX.canConvertLocallyTo(FileFormat.JPG))
    }

    @Test
    fun conversionGraphOfficeEdges() {
        assertTrue(ConversionGraph.canConvert(FileFormat.DOCX, FileFormat.PDF))
        assertTrue(ConversionGraph.canConvert(FileFormat.PPTX, FileFormat.PDF))
        assertTrue(ConversionGraph.canConvert(FileFormat.XLSX, FileFormat.PDF))
        assertFalse(ConversionGraph.canConvert(FileFormat.DOCX, FileFormat.DOCX))
    }

    @Test
    fun suggestedOutputNameUsesPdfExtension() {
        assertEquals("report.pdf", suggestedOutputName("report.docx", FileFormat.PDF))
        assertEquals("slides.pdf", suggestedOutputName("slides.pptx", FileFormat.PDF))
    }

    @Test
    fun mimeDetectionRecognizesOffice() {
        assertEquals(
            FileFormat.DOCX,
            FileTypeDetector.fromMimeType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
        )
        assertEquals(
            FileFormat.PPTX,
            FileTypeDetector.fromMimeType("application/vnd.openxmlformats-officedocument.presentationml.presentation"),
        )
        assertEquals(
            FileFormat.XLSX,
            FileTypeDetector.fromMimeType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
        )
    }

    @Test
    fun detectPrefersOfficeExtensionOverZipMagic() {
        // docx 文件头是 ZIP（PK），但扩展名 .docx 应识别为 DOCX
        val stream = java.io.ByteArrayInputStream("PK\u0003\u0004rest".toByteArray())
        val format = FileTypeDetector.detect("report.docx", null, stream)
        assertEquals(FileFormat.DOCX, format)
    }

    @Test
    fun detectFallsBackToMimeForOfficeWithoutExtension() {
        val stream = java.io.ByteArrayInputStream(byteArrayOf(1, 2, 3))
        val format = FileTypeDetector.detect(
            "document",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            stream,
        )
        assertEquals(FileFormat.DOCX, format)
    }
}
