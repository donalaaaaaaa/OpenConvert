package com.openconvert.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ConversionPayloadCodecTest {
    @Test
    fun `round-trips source lists and page data`() {
        val payload = ConversionPayload(
            sourceUris = listOf("content://a", "content://b"),
            pageRanges = "1-3, 5",
            pages = listOf(1, 2, 3, 5),
            outputTreeUri = "content://tree",
            outputUris = listOf("content://out1", "content://out2"),
        )
        assertEquals(payload, ConversionPayloadCodec.decode(ConversionPayloadCodec.encode(payload)))
    }

    @Test
    fun `blank json becomes an empty payload`() {
        assertEquals(ConversionPayload(), ConversionPayloadCodec.decode(null))
        assertEquals(ConversionPayload(), ConversionPayloadCodec.decode(" "))
    }
}
