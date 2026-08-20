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
    fun `jpg exposes only formats the registry can actually encode`() {
        // 转换边只含 libvips/BitmapFactory 能编码的三种格式。
        assertEquals(
            listOf(FileFormat.PNG, FileFormat.WEBP),
            FileFormat.JPG.availableTargets(),
        )
        // PDF 不是转换边，而是工具能力（IMAGES_TO_PDF）。
        assertTrue(ConversionGraph.toolsFor(FileFormat.JPG).contains(ConversionKind.IMAGES_TO_PDF))
    }

    @Test
    fun `read-only image inputs can be converted out but not targeted`() {
        assertTrue(FileFormat.HEIC.canConvertLocallyTo(FileFormat.JPG))
        assertTrue(FileFormat.AVIF.canConvertLocallyTo(FileFormat.WEBP))
        // 没有 HEIC/AVIF 编码器，不能作为输出目标。
        assertEquals(false, FileFormat.JPG.canConvertLocallyTo(FileFormat.HEIC))
        assertEquals(false, FileFormat.PNG.canConvertLocallyTo(FileFormat.AVIF))
        assertEquals(false, FileFormat.JPG.canConvertLocallyTo(FileFormat.GIF))
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

    @Test
    fun `capability check agrees with the graph for every format pair`() {
        // 回归护栏：canConvertLocallyTo 必须与 ConversionGraph 完全一致，
        // 否则 UI 会放出 registry 里没有引擎的组合。
        FileFormat.entries.forEach { input ->
            FileFormat.entries.forEach { target ->
                assertEquals(
                    "$input → $target",
                    ConversionGraph.canConvert(input, target),
                    input.canConvertLocallyTo(target),
                )
            }
        }
    }
}
