package com.openconvert.app.domain.preset

import com.openconvert.app.domain.model.ConversionPayload
import com.openconvert.app.domain.model.FileCategory
import com.openconvert.app.domain.model.FileFormat
import com.openconvert.app.domain.model.QualityPreset
import com.openconvert.app.domain.model.ResolutionPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetRepositoryTest {

    @Test
    fun `spec examples from section 8_1 exist with the right parameters`() {
        // 微信发送：JPEG / 85% / 最长边 1920 / 去 EXIF
        val wechat = PresetRepository.byId("img_wechat")!!
        assertEquals(FileFormat.JPG, wechat.targetFormat)
        assertEquals(QualityPreset.HIGH, wechat.quality)
        assertEquals(1920, wechat.longestEdgePx)
        assertTrue(wechat.stripMetadata)

        // 网页图片：WEBP / 80%
        val web = PresetRepository.byId("img_web")!!
        assertEquals(FileFormat.WEBP, web.targetFormat)
        assertEquals(QualityPreset.BALANCED, web.quality)

        // 头像：1:1 / 1024×1024 / JPEG
        val avatar = PresetRepository.byId("img_avatar")!!
        assertEquals(FileFormat.JPG, avatar.targetFormat)
        assertEquals(1024, avatar.fixedWidthPx)
        assertEquals(1024, avatar.fixedHeightPx)
        assertEquals("1:1", avatar.cropAspect)
    }

    @Test
    fun `spec examples from section 8_2 exist`() {
        val small = PresetRepository.byId("video_small")!!
        assertEquals(ResolutionPreset.SMALL, small.resolution)
        assertEquals(QualityPreset.SMALL, small.quality)

        val hd = PresetRepository.byId("video_hd")!!
        assertEquals(FileFormat.MP4, hd.targetFormat)
        assertTrue(hd.isDefault)

        assertNotNull(PresetRepository.byId("video_high_quality"))
    }

    @Test
    fun `every category has exactly one default`() {
        listOf(FileCategory.IMAGE, FileCategory.VIDEO, FileCategory.AUDIO).forEach { category ->
            val defaults = PresetRepository.presetsFor(category).filter { it.isDefault }
            assertEquals("$category 应恰好有一个默认预设", 1, defaults.size)
        }
    }

    @Test
    fun `every preset targets a format the engine can actually produce`() {
        PresetRepository.BUILT_IN_PRESETS.forEach { preset ->
            val reachable = FileFormat.entries.any { input ->
                com.openconvert.app.domain.model.ConversionGraph.canConvert(
                    input,
                    preset.targetFormat,
                )
            }
            assertTrue(
                "${preset.id} 的目标 ${preset.targetFormat} 没有任何引擎能产出",
                reachable,
            )
        }
    }

    @Test
    fun `all built-in presets are marked built-in and have unique ids`() {
        val ids = PresetRepository.BUILT_IN_PRESETS.map { it.id }
        assertEquals("预设 id 不得重复", ids.size, ids.distinct().size)
        assertTrue(PresetRepository.BUILT_IN_PRESETS.all { it.isBuiltIn })
    }

    @Test
    fun `size summary describes the constraint for the ui`() {
        assertEquals("最长边 1920", PresetRepository.byId("img_wechat")!!.sizeSummary)
        assertEquals("1024×1024", PresetRepository.byId("img_avatar")!!.sizeSummary)
        assertNull("原尺寸预设无需摘要", PresetRepository.byId("img_original")!!.sizeSummary)
    }
}

class PresetSizingTest {

    private fun preset(
        longestEdgePx: Int? = null,
        fixedWidthPx: Int? = null,
        fixedHeightPx: Int? = null,
        resolution: ResolutionPreset = ResolutionPreset.ORIGINAL,
        cropAspect: String = "free",
        stripMetadata: Boolean = false,
    ) = Preset(
        id = "p",
        category = FileCategory.IMAGE,
        name = "n",
        description = "d",
        targetFormat = FileFormat.JPG,
        resolution = resolution,
        longestEdgePx = longestEdgePx,
        fixedWidthPx = fixedWidthPx,
        fixedHeightPx = fixedHeightPx,
        cropAspect = cropAspect,
        stripMetadata = stripMetadata,
    )

    @Test
    fun `longest edge scales a landscape image proportionally`() {
        // 4000×3000 限制最长边 1920 → 1920×1440
        val size = PresetSizing.resolve(preset(longestEdgePx = 1920), 4000, 3000)!!
        assertEquals(1920, size.width)
        assertEquals(1440, size.height)
    }

    @Test
    fun `longest edge scales a portrait image on its height`() {
        val size = PresetSizing.resolve(preset(longestEdgePx = 1920), 3000, 4000)!!
        assertEquals(1440, size.width)
        assertEquals(1920, size.height)
    }

    @Test
    fun `images already smaller than the limit are not upscaled`() {
        // 预设意图是省体积，放大只会增大文件且损失观感。
        assertNull(PresetSizing.resolve(preset(longestEdgePx = 1920), 800, 600))
    }

    @Test
    fun `fixed size wins over longest edge`() {
        val size = PresetSizing.resolve(
            preset(longestEdgePx = 1920, fixedWidthPx = 1024, fixedHeightPx = 1024),
            4000,
            3000,
        )!!
        assertEquals(1024, size.width)
        assertEquals(1024, size.height)
    }

    @Test
    fun `longest edge wins over percentage resolution`() {
        val size = PresetSizing.resolve(
            preset(longestEdgePx = 1000, resolution = ResolutionPreset.SMALL),
            4000,
            2000,
        )!!
        assertEquals(1000, size.width)
        assertEquals(500, size.height)
    }

    @Test
    fun `percentage resolution applies when no pixel constraint is set`() {
        val size = PresetSizing.resolve(
            preset(resolution = ResolutionPreset.SMALL),
            1000,
            800,
        )!!
        assertEquals(500, size.width)
        assertEquals(400, size.height)
    }

    @Test
    fun `original resolution means no resize`() {
        assertNull(PresetSizing.resolve(preset(), 1000, 800))
    }

    @Test
    fun `unknown source dimensions yield no target size`() {
        assertNull(PresetSizing.resolve(preset(longestEdgePx = 1920), 0, 0))
    }

    @Test
    fun `half of a fixed size pair is ignored`() {
        // 只给宽不给高无法确定尺寸，应回落到其他规则。
        assertNull(PresetSizing.resolve(preset(fixedWidthPx = 1024), 4000, 3000))
    }

    @Test
    fun `payload picks up crop aspect and metadata stripping`() {
        val payload = PresetSizing.applyTo(
            ConversionPayload(),
            preset(cropAspect = "1:1", stripMetadata = true),
        )
        assertEquals("1:1", payload.cropAspect)
        assertTrue(payload.stripMetadata)
    }

    @Test
    fun `scaling never produces a zero dimension`() {
        // 极端比例的窄图：短边算出来会是 0，必须夹到 1。
        val size = PresetSizing.resolve(preset(longestEdgePx = 10), 10000, 5)!!
        assertTrue(size.width >= 1)
        assertTrue(size.height >= 1)
    }
}
