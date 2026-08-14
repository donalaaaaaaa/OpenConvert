package com.openconvert.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileFormatTest {
    @Test
    fun `recognizes supported extensions without case sensitivity`() {
        assertEquals(FileFormat.JPG, FileFormat.fromFileName("Photo.JPEG"))
        assertEquals(FileFormat.MOV, FileFormat.fromFileName("clip.MOV"))
        assertEquals(FileFormat.PDF, FileFormat.fromFileName("report.pdf"))
    }

    @Test
    fun `returns unknown when extension is unsupported`() {
        assertEquals(FileFormat.UNKNOWN, FileFormat.fromFileName("notes.txt"))
        assertEquals(FileFormat.DOCX, FileFormat.fromFileName("notes.docx"))
    }

    @Test
    fun `jpg exposes only MVP targets`() {
        assertTrue(FileFormat.JPG.availableTargets().containsAll(listOf(FileFormat.PNG, FileFormat.WEBP, FileFormat.PDF)))
    }

    @Test
    fun `suggested output name replaces only final extension`() {
        assertEquals("holiday.photo.webp", suggestedOutputName("holiday.photo.jpeg", FileFormat.WEBP))
        assertEquals("OpenConvert.png", suggestedOutputName("", FileFormat.PNG))
    }

    @Test
    fun `local engine supports image audio and video routes`() {
        assertTrue(FileFormat.PNG.canConvertLocallyTo(FileFormat.JPG))
        assertEquals(false, FileFormat.PNG.canConvertLocallyTo(FileFormat.PDF))
        assertTrue(FileFormat.MOV.canConvertLocallyTo(FileFormat.MP4))
        assertTrue(FileFormat.MP4.canConvertLocallyTo(FileFormat.MP3))
        assertTrue(FileFormat.FLAC.canConvertLocallyTo(FileFormat.M4A))
    }
}
