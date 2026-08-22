package com.openconvert.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveFormatTest {
    @Test
    fun recognizesArchiveExtensions() {
        assertEquals(FileFormat.ZIP, FileFormat.fromFileName("photos.zip"))
        assertEquals(FileFormat.TAR, FileFormat.fromFileName("backup.tar"))
        assertEquals(FileFormat.GZIP, FileFormat.fromFileName("file.gz"))
        assertEquals(FileFormat.BZIP2, FileFormat.fromFileName("file.bz2"))
        assertEquals(FileFormat.TAR_GZ, FileFormat.fromFileName("backup.tar.gz"))
        assertEquals(FileFormat.TAR_GZ, FileFormat.fromFileName("backup.tgz"))
        assertEquals(FileFormat.TAR_GZ, FileFormat.fromFileName("BACKUP.TAR.GZ"))
        assertEquals(FileFormat.SEVEN_Z, FileFormat.fromFileName("backup.7z"))
        assertEquals(FileFormat.SEVEN_Z, FileFormat.fromFileName("BACKUP.7Z"))
        assertEquals(FileFormat.XZ, FileFormat.fromFileName("file.xz"))
        assertEquals(FileFormat.TAR_XZ, FileFormat.fromFileName("backup.tar.xz"))
        assertEquals(FileFormat.TAR_XZ, FileFormat.fromFileName("backup.txz"))
        assertEquals(FileFormat.TAR_XZ, FileFormat.fromFileName("BACKUP.TAR.XZ"))
    }

    @Test
    fun recognizesXzMagicAndMime() {
        val header = byteArrayOf(0xFD.toByte(), 0x37, 0x7A, 0x58, 0x5A, 0x00)
        assertEquals(FileFormat.XZ, FileTypeDetector.fromMagicBytes(header, header.size))
        assertEquals(FileFormat.XZ, FileTypeDetector.fromMimeType("application/x-xz"))
        assertTrue(ConversionGraph.toolsFor(FileFormat.XZ).contains(ConversionKind.ARCHIVE_EXTRACT))
        assertTrue(ConversionGraph.toolsFor(FileFormat.TAR).contains(ConversionKind.ARCHIVE_EXTRACT))
        assertTrue(ConversionGraph.toolsFor(FileFormat.TAR_XZ).contains(ConversionKind.ARCHIVE_EXTRACT))
        assertTrue(ConversionGraph.toolsFor(FileFormat.GZIP).contains(ConversionKind.ARCHIVE_EXTRACT))
    }

    @Test
    fun recognizesSevenZMagicAndMime() {
        val header = byteArrayOf(0x37, 0x7A, 0xBC.toByte(), 0xAF.toByte(), 0x27, 0x1C)
        assertEquals(FileFormat.SEVEN_Z, FileTypeDetector.fromMagicBytes(header, header.size))
        assertEquals(FileFormat.SEVEN_Z, FileTypeDetector.fromMimeType("application/x-7z-compressed"))
        assertTrue(ConversionGraph.toolsFor(FileFormat.SEVEN_Z).contains(ConversionKind.ARCHIVE_EXTRACT))
    }

    @Test
    fun archiveTargetsStayWithinArchiveCategory() {
        FileFormat.ZIP.availableTargets().forEach { assertTrue(it.category == FileCategory.ARCHIVE) }
        assertTrue(FileFormat.TAR.availableTargets().contains(FileFormat.ZIP))
        assertTrue(FileFormat.GZIP.availableTargets().contains(FileFormat.ZIP))
    }

    @Test
    fun archiveCanConvertLocallyWithinCategory() {
        assertTrue(FileFormat.ZIP.canConvertLocallyTo(FileFormat.TAR))
        assertFalse(FileFormat.ZIP.canConvertLocallyTo(FileFormat.PDF))
        assertFalse(FileFormat.UNKNOWN.canConvertLocallyTo(FileFormat.ZIP))
    }

    @Test
    fun suggestedOutputNameUsesPreferredExtension() {
        assertEquals("photo.zip", suggestedOutputName("photo.jpg", FileFormat.ZIP))
        assertEquals("data.tar.gz", suggestedOutputName("data", FileFormat.TAR_GZ))
        assertEquals("file.bz2", suggestedOutputName("file.jpg", FileFormat.BZIP2))
        assertEquals("notes.xz", suggestedOutputName("notes.txt", FileFormat.XZ))
        assertEquals("backup.tar.xz", suggestedOutputName("backup", FileFormat.TAR_XZ))
    }
}
