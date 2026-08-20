package com.openconvert.app.domain.testassets

import com.openconvert.app.domain.model.FileFormat
import com.openconvert.app.domain.model.FileTypeDetector
import java.io.ByteArrayInputStream
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * 阶段一稳定性测试体系（计划书 §4.2）的纯逻辑部分：
 * 测试文件库生成器本身的正确性 + 三层识别在异常输入下的行为。
 *
 * 真机 I/O 路径（引擎实际读取损坏文件）在 androidTest 里覆盖。
 */
class TestAssetFactoryTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun dir(): File = temp.newFolder()

    @Test
    fun `deterministic bytes are reproducible across calls`() {
        val a = TestAssetFactory.deterministicBytes(256, seed = 99L)
        val b = TestAssetFactory.deterministicBytes(256, seed = 99L)
        assertTrue("同 seed 必须产出同序列（失败可复现的前提）", a.contentEquals(b))

        val c = TestAssetFactory.deterministicBytes(256, seed = 100L)
        assertTrue("不同 seed 应产出不同序列", !a.contentEquals(c))
    }

    @Test
    fun `empty file has zero length`() {
        val file = TestAssetFactory.empty(dir(), "blank.png")
        assertTrue(file.isFile)
        assertEquals(0L, file.length())
    }

    @Test
    fun `truncated file carries only the magic bytes`() {
        val file = TestAssetFactory.truncated(dir(), "cut.png", TestAssetFactory.PNG_MAGIC)
        assertEquals(TestAssetFactory.PNG_MAGIC.size.toLong(), file.length())
        // 魔数仍可识别 —— 识别成功但解码必然失败，正是要测的场景。
        assertEquals(FileFormat.PNG, FileTypeDetector.fromMagicBytes(file.readBytes(), file.readBytes().size))
    }

    @Test
    fun `corrupted file keeps a valid header over garbage body`() {
        val file = TestAssetFactory.corrupted(dir(), "broken.jpg", TestAssetFactory.JPEG_MAGIC)
        assertTrue("损坏文件必须比魔数长（有垃圾主体）", file.length() > TestAssetFactory.JPEG_MAGIC.size)

        val bytes = file.readBytes()
        assertEquals(FileFormat.JPG, FileTypeDetector.fromMagicBytes(bytes, bytes.size))
    }

    @Test
    fun `magic number wins over a lying extension`() {
        // 内容是 PDF，扩展名谎称 .png —— 计划书 §4.2 的「MIME 与扩展名不一致」场景。
        val file = TestAssetFactory.wrongExtension(dir(), "actually-pdf.png", TestAssetFactory.PDF_MAGIC)

        val byName = FileFormat.fromFileName(file.name)
        assertEquals(FileFormat.PNG, byName)

        val detected = FileTypeDetector.detect(
            fileName = file.name,
            mimeType = "image/png",
            input = ByteArrayInputStream(file.readBytes()),
        )
        assertEquals("三层识别必须以内容为准", FileFormat.PDF, detected)
        assertNotEquals(byName, detected)
    }

    @Test
    fun `sparse file reports the requested length`() {
        val oneGb = 1024L * 1024 * 1024
        val file = TestAssetFactory.sparse(dir(), "big.bin", oneGb)
        assertEquals(oneGb, file.length())
    }

    @Test
    fun `every provided magic signature is actually recognised`() {
        // 生成器的魔数常量必须与 FileTypeDetector 对齐，否则测试素材本身就是错的。
        val cases = listOf(
            TestAssetFactory.PNG_MAGIC to FileFormat.PNG,
            TestAssetFactory.JPEG_MAGIC to FileFormat.JPG,
            TestAssetFactory.PDF_MAGIC to FileFormat.PDF,
            TestAssetFactory.ZIP_MAGIC to FileFormat.ZIP,
            TestAssetFactory.GZIP_MAGIC to FileFormat.GZIP,
            TestAssetFactory.FLAC_MAGIC to FileFormat.FLAC,
            TestAssetFactory.MP3_MAGIC to FileFormat.MP3,
            TestAssetFactory.MKV_MAGIC to FileFormat.MKV,
            TestAssetFactory.wavMagic() to FileFormat.WAV,
            TestAssetFactory.webpMagic() to FileFormat.WEBP,
            TestAssetFactory.mp4Magic() to FileFormat.MP4,
        )
        cases.forEach { (magic, expected) ->
            assertEquals(
                "魔数常量与 FileTypeDetector 不一致：$expected",
                expected,
                FileTypeDetector.fromMagicBytes(magic, magic.size),
            )
        }
    }

    @Test
    fun `empty content is detected as unknown rather than misclassified`() {
        val empty = ByteArray(0)
        assertEquals(FileFormat.UNKNOWN, FileTypeDetector.fromMagicBytes(empty, 0))
    }
}
