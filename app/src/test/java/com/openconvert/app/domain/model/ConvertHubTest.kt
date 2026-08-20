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
        // PDF 不在转换边里（图片→PDF 走 IMAGES_TO_PDF 工具流），所以不出现在共同目标里。
        assertFalse(common.contains(FileFormat.PDF))
        assertFalse(common.contains(FileFormat.JPG)) // JPG 是输入之一，自身不算
    }

    @Test
    fun everyConvertEdgeHasARegisteredEngine() {
        // 能力图与引擎能力必须一致：Graph 声明的每条转换边都要有引擎能接。
        // 这里用各 Converter 的 supports() 判定规则复刻一遍（不构造 Android 依赖）。
        FileFormat.entries.forEach { input ->
            ConversionGraph.targetsFor(input).forEach { target ->
                val covered = when {
                    // ImageConverter: IMAGE → IMAGE
                    input.category == FileCategory.IMAGE && target.category == FileCategory.IMAGE -> true
                    // MediaConverter: AUDIO/VIDEO 输入
                    input.category == FileCategory.AUDIO || input.category == FileCategory.VIDEO -> true
                    // OfficeConverter: OFFICE → PDF
                    input.category == FileCategory.OFFICE && target == FileFormat.PDF -> true
                    // ArchiveConverter: 归档互转
                    input.category == FileCategory.ARCHIVE -> true
                    else -> false
                }
                assertTrue("$input → $target 没有引擎可处理", covered)
            }
        }
    }

    @Test
    fun pdfHasToolCapabilitiesButNoConvertEdges() {
        // PDF 的所有能力都需要页面参数或目录输出，因此只有工具边。
        assertTrue(ConversionGraph.targetsFor(FileFormat.PDF).isEmpty())
        val tools = ConversionGraph.toolsFor(FileFormat.PDF)
        assertTrue(tools.contains(ConversionKind.PDF_TO_IMAGES))
        assertTrue(tools.contains(ConversionKind.PDF_COMPRESS))
        assertTrue(tools.contains(ConversionKind.PDF_PAGE_MANAGER))
        assertTrue(ConversionGraph.hasAnyCapability(FileFormat.PDF))
    }

    @Test
    fun unknownFormatHasNoCapabilityAtAll() {
        assertTrue(ConversionGraph.targetsFor(FileFormat.UNKNOWN).isEmpty())
        assertTrue(ConversionGraph.toolsFor(FileFormat.UNKNOWN).isEmpty())
        assertFalse(ConversionGraph.hasAnyCapability(FileFormat.UNKNOWN))
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
