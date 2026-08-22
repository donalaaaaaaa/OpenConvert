package com.openconvert.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 预设尺寸约束必须能过 Room（计划书 §8.1）。
 *
 * Worker 在另一个进程周期里执行，payload 是它唯一的参数来源——
 * 这几个字段丢了「最长边 1920」会静默失效，输出尺寸悄悄变成原图。
 */
class PresetPayloadCodecTest {

    @Test
    fun `longest edge survives a round trip`() {
        val payload = ConversionPayload(presetId = "img_wechat", longestEdgePx = 1920)
        val decoded = ConversionPayloadCodec.decode(ConversionPayloadCodec.encode(payload))
        assertEquals("img_wechat", decoded.presetId)
        assertEquals(1920, decoded.longestEdgePx)
    }

    @Test
    fun `fixed size and crop aspect survive a round trip`() {
        val payload = ConversionPayload(
            presetId = "img_avatar",
            fixedWidthPx = 1024,
            fixedHeightPx = 1024,
            cropAspect = "1:1",
            stripMetadata = true,
        )
        val decoded = ConversionPayloadCodec.decode(ConversionPayloadCodec.encode(payload))
        assertEquals(1024, decoded.fixedWidthPx)
        assertEquals(1024, decoded.fixedHeightPx)
        assertEquals("1:1", decoded.cropAspect)
        assertEquals(true, decoded.stripMetadata)
    }

    @Test
    fun `absent size constraints decode as null not zero`() {
        val decoded = ConversionPayloadCodec.decode(ConversionPayloadCodec.encode(ConversionPayload()))
        assertNull(decoded.presetId)
        assertNull(decoded.longestEdgePx)
        assertNull(decoded.fixedWidthPx)
        assertNull(decoded.fixedHeightPx)
    }

    @Test
    fun `payloads written before presets existed still decode`() {
        val legacy = """{"pageRanges":"","rotateDegrees":0,"cropAspect":"free","flip":0}"""
        val decoded = ConversionPayloadCodec.decode(legacy)
        assertNull(decoded.presetId)
        assertNull(decoded.longestEdgePx)
        assertEquals("free", decoded.cropAspect)
    }

    @Test
    fun `watermark fields survive a round trip`() {
        val payload = ConversionPayload(
            watermarkText = "机密",
            watermarkOpacity = 0.28f,
            watermarkPosition = "FOOTER",
            cropMarginsLeft = 12f,
            metadataTitle = "报告",
        )
        val decoded = ConversionPayloadCodec.decode(ConversionPayloadCodec.encode(payload))
        assertEquals("机密", decoded.watermarkText)
        assertEquals(0.28f, decoded.watermarkOpacity, 0.001f)
        assertEquals("FOOTER", decoded.watermarkPosition)
        assertEquals(12f, decoded.cropMarginsLeft, 0.001f)
        assertEquals("报告", decoded.metadataTitle)
    }

    @Test
    fun `existing payload fields are unaffected`() {
        val payload = ConversionPayload(
            sourceUris = listOf("content://a", "content://b"),
            pages = listOf(1, 3, 5),
            batchId = "batch-9",
            rotateDegrees = 90,
            longestEdgePx = 800,
        )
        val decoded = ConversionPayloadCodec.decode(ConversionPayloadCodec.encode(payload))
        assertEquals(listOf("content://a", "content://b"), decoded.sourceUris)
        assertEquals(listOf(1, 3, 5), decoded.pages)
        assertEquals("batch-9", decoded.batchId)
        assertEquals(90, decoded.rotateDegrees)
        assertEquals(800, decoded.longestEdgePx)
    }
}
