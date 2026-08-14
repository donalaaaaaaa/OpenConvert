package com.openconvert.app.domain.model

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileTypeDetectorTest {
    @Test
    fun detectsPngByMagicBytes() {
        val header = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
        )
        assertEquals(FileFormat.PNG, FileTypeDetector.fromMagicBytes(header, header.size))
    }

    @Test
    fun detectsJpegByMagicBytes() {
        val header = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
        assertEquals(FileFormat.JPG, FileTypeDetector.fromMagicBytes(header, header.size))
    }

    @Test
    fun detectsPdfByMagicBytes() {
        val header = "%PDF-1.7\n".toByteArray()
        assertEquals(FileFormat.PDF, FileTypeDetector.fromMagicBytes(header, header.size))
    }

    @Test
    fun detectsWebpRiffHeader() {
        // RIFF....WEBP
        val header = "RIFF".toByteArray() + byteArrayOf(0, 0, 0, 0) + "WEBP".toByteArray()
        assertEquals(FileFormat.WEBP, FileTypeDetector.fromMagicBytes(header, header.size))
    }

    @Test
    fun detectsZipByPkHeader() {
        val header = "PK\u0003\u0004".toByteArray()
        assertEquals(FileFormat.ZIP, FileTypeDetector.fromMagicBytes(header, header.size))
    }

    @Test
    fun detectsGzipByHeader() {
        val header = byteArrayOf(0x1F.toByte(), 0x8B.toByte(), 0x08.toByte())
        assertEquals(FileFormat.GZIP, FileTypeDetector.fromMagicBytes(header, header.size))
    }

    @Test
    fun detectsBzip2ByHeader() {
        val header = "BZh9".toByteArray()
        assertEquals(FileFormat.BZIP2, FileTypeDetector.fromMagicBytes(header, header.size))
    }

    @Test
    fun detectsMp4FtypFamily() {
        val mp4 = byteArrayOf(0, 0, 0, 0x18) + "ftypisom".toByteArray()
        assertEquals(FileFormat.MP4, FileTypeDetector.fromMagicBytes(mp4, mp4.size))
        val mov = byteArrayOf(0, 0, 0, 0x18) + "ftypqt  ".toByteArray()
        assertEquals(FileFormat.MOV, FileTypeDetector.fromMagicBytes(mov, mov.size))
    }

    @Test
    fun unknownHeaderReturnsUnknown() {
        val header = "random text".toByteArray()
        assertEquals(FileFormat.UNKNOWN, FileTypeDetector.fromMagicBytes(header, header.size))
    }

    @Test
    fun detectPrefersMagicOverExtensionAndMime() {
        // 扩展名说是 PNG，MIME 说是 image/jpeg，但内容是 PDF → 应识别为 PDF
        val stream = ByteArrayInputStream("%PDF-1.4\n".toByteArray())
        val format = FileTypeDetector.detect("photo.png", "image/jpeg", stream)
        assertEquals(FileFormat.PDF, format)
    }

    @Test
    fun detectFallsBackToMimeWhenMagicUnknown() {
        val stream = ByteArrayInputStream(byteArrayOf(1, 2, 3, 4, 5))
        val format = FileTypeDetector.detect("song.mp3", "audio/mpeg", stream)
        assertEquals(FileFormat.MP3, format)
    }

    @Test
    fun detectFallsBackToExtensionWhenAllUnknown() {
        val stream = ByteArrayInputStream(byteArrayOf(1, 2, 3))
        val format = FileTypeDetector.detect("doc.pdf", "application/octet-stream", stream)
        assertEquals(FileFormat.PDF, format)
    }
}

class ConversionGraphTest {
    @Test
    fun imageTargetsMatchGraph() {
        assertTrue(ConversionGraph.canConvert(FileFormat.JPG, FileFormat.PNG))
        assertTrue(ConversionGraph.canConvert(FileFormat.PNG, FileFormat.WEBP))
        assertFalse(ConversionGraph.canConvert(FileFormat.JPG, FileFormat.JPG))
        assertFalse(ConversionGraph.canConvert(FileFormat.JPG, FileFormat.MP3))
    }

    @Test
    fun videoTargetsIncludeAudioExtraction() {
        assertTrue(ConversionGraph.canConvert(FileFormat.MP4, FileFormat.MP3))
        assertTrue(ConversionGraph.canConvert(FileFormat.MKV, FileFormat.MP4))
        assertTrue(ConversionGraph.canConvert(FileFormat.WEBM, FileFormat.MP4))
    }

    @Test
    fun archiveTargetsWithinCategory() {
        assertTrue(ConversionGraph.canConvert(FileFormat.ZIP, FileFormat.TAR))
        assertTrue(ConversionGraph.canConvert(FileFormat.TAR, FileFormat.TAR_GZ))
        assertFalse(ConversionGraph.canConvert(FileFormat.ZIP, FileFormat.PDF))
    }

    @Test
    fun commonTargetsIntersectsAcrossInputs() {
        val common = ConversionGraph.commonTargets(
            listOf(FileFormat.JPG, FileFormat.PNG),
        )
        assertTrue(common.contains(FileFormat.WEBP))
        assertTrue(common.contains(FileFormat.PDF)) // JPG→PDF 与 PNG→PDF 都支持
        assertFalse(common.contains(FileFormat.JPG)) // 自身不算
    }

    @Test
    fun commonTargetsEmptyForMixedCategories() {
        val common = ConversionGraph.commonTargets(listOf(FileFormat.JPG, FileFormat.MP4))
        assertTrue(common.isEmpty())
    }

    @Test
    fun availableTargetsDelegatesToGraph() {
        assertEquals(ConversionGraph.targetsFor(FileFormat.JPG), FileFormat.JPG.availableTargets())
        assertEquals(ConversionGraph.targetsFor(FileFormat.WEBM), FileFormat.WEBM.availableTargets())
    }
}
