package com.openconvert.app.domain.capability

import com.openconvert.app.BuildConfig
import com.openconvert.app.R
import com.openconvert.app.domain.model.ConversionGraph
import com.openconvert.app.domain.model.ConversionKind
import com.openconvert.app.domain.model.FileFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 首页 UI 2.0（计划书 §六）文件驱动能力面板的数据正确性。
 */
class FileCapabilityResolverTest {

    @Test
    fun `heic offers convert targets and image tools`() {
        // 计划书 §6.3 的示例：选中 IMG_2856.HEIC 应看到 JPG/PNG/WEBP + 图片工具。
        val caps = FileCapabilityResolver.resolve(FileFormat.HEIC)
        assertEquals(
            listOf(FileFormat.JPG, FileFormat.PNG, FileFormat.WEBP),
            caps.convertTargets,
        )
        assertTrue(caps.tools.any { it.kind == ConversionKind.IMAGES_TO_PDF })
        assertEquals(R.string.cap_convert_image, caps.convertSectionTitleRes)
        assertEquals(R.string.cap_tools_image, caps.toolSectionTitleRes)
        assertTrue(caps.hasAnything)
    }

    @Test
    fun `heic never offers itself or another read-only format as a target`() {
        val caps = FileCapabilityResolver.resolve(FileFormat.HEIC)
        // 没有 HEIC/AVIF 编码器 —— 不能作为输出。
        assertFalse(caps.convertTargets.contains(FileFormat.HEIC))
        assertFalse(caps.convertTargets.contains(FileFormat.AVIF))
        assertFalse(caps.convertTargets.contains(FileFormat.TIFF))
    }

    @Test
    fun `pdf shows the full tool suite and no convert targets`() {
        val caps = FileCapabilityResolver.resolve(FileFormat.PDF)
        assertTrue("PDF 没有一进一出的转换边", caps.convertTargets.isEmpty())
        assertEquals(R.string.cap_tools_pdf, caps.toolSectionTitleRes)

        val kinds = caps.tools.map { it.kind }
        listOf(
            ConversionKind.PDF_TO_IMAGES,
            ConversionKind.PDF_MERGE,
            ConversionKind.PDF_SPLIT,
            ConversionKind.PDF_COMPRESS,
            ConversionKind.PDF_PAGE_MANAGER,
            ConversionKind.PDF_SECURITY,
            ConversionKind.PDF_CROP,
            ConversionKind.PDF_METADATA,
            ConversionKind.PDF_WATERMARK,
        ).forEach { assertTrue("PDF 应提供 $it", kinds.contains(it)) }
        assertTrue(caps.hasAnything)
    }

    @Test
    fun `office documents export to pdf only`() {
        val caps = FileCapabilityResolver.resolve(FileFormat.DOCX)
        assertEquals(
            if (BuildConfig.OFFICE_BUNDLED) listOf(FileFormat.PDF) else emptyList(),
            caps.convertTargets,
        )
        assertEquals(R.string.cap_convert_office, caps.convertSectionTitleRes)
    }

    @Test
    fun `office conversion follows the current edition`() {
        assertEquals(
            BuildConfig.OFFICE_BUNDLED,
            FileCapabilityResolver.canConvertInEdition(FileFormat.DOCX, FileFormat.PDF),
        )
    }

    @Test
    fun `video offers container conversion and audio extraction`() {
        val caps = FileCapabilityResolver.resolve(FileFormat.MKV)
        assertTrue(caps.convertTargets.contains(FileFormat.MP4))
        assertTrue("视频应能提取音轨", caps.convertTargets.contains(FileFormat.MP3))
        assertEquals(R.string.cap_convert_video, caps.convertSectionTitleRes)
    }

    @Test
    fun `archive can be extracted and re-compressed`() {
        val caps = FileCapabilityResolver.resolve(FileFormat.ZIP)
        assertTrue(caps.convertTargets.contains(FileFormat.TAR))
        val kinds = caps.tools.map { it.kind }
        assertTrue(kinds.contains(ConversionKind.ARCHIVE_EXTRACT))
        assertTrue(
            ConversionGraph.toolsFor(FileFormat.SEVEN_Z).contains(ConversionKind.ARCHIVE_EXTRACT),
        )
        assertTrue(kinds.contains(ConversionKind.ARCHIVE_COMPRESS))
    }

    @Test
    fun `any known format can at least be packed into an archive`() {
        FileFormat.entries.filter { it != FileFormat.UNKNOWN }.forEach { format ->
            val caps = FileCapabilityResolver.resolve(format)
            assertTrue(
                "$format 至少应能压缩打包",
                caps.tools.any { it.kind == ConversionKind.ARCHIVE_COMPRESS },
            )
            assertTrue("$format 应有可执行能力", caps.hasAnything)
        }
    }

    @Test
    fun `unknown format offers nothing`() {
        val caps = FileCapabilityResolver.resolve(FileFormat.UNKNOWN)
        assertTrue(caps.convertTargets.isEmpty())
        assertTrue(caps.tools.isEmpty())
        assertFalse(caps.hasAnything)
    }

    @Test
    fun `every tool action carries a label and description`() {
        FileFormat.entries.forEach { format ->
            FileCapabilityResolver.resolve(format).tools.forEach { action ->
                assertTrue("${action.kind} 缺少标签", action.label.isNotBlank())
                assertTrue("${action.kind} 缺少说明", action.description.isNotBlank())
            }
        }
    }
}
