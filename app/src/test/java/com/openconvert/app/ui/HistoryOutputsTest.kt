package com.openconvert.app.ui

import com.openconvert.app.domain.model.ConversionPayload
import com.openconvert.app.domain.model.ConversionTask
import com.openconvert.app.domain.model.FileFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryOutputsTest {
    @Test
    fun `prefers payload output list then single output uri`() {
        val fromPayload = sample(payloadUris = listOf("content://a", "content://b"), outputUri = "content://old")
        assertEquals(listOf("content://a", "content://b"), HistoryOutputs.uriStrings(fromPayload))
        val fromSingle = sample(payloadUris = emptyList(), outputUri = "content://one")
        assertEquals(listOf("content://one"), HistoryOutputs.uriStrings(fromSingle))
    }

    @Test
    fun `missing output yields an empty selection`() {
        val task = sample(payloadUris = emptyList(), outputUri = null)
        assertTrue(HistoryOutputs.uriStrings(task).isEmpty())
    }

    private fun sample(payloadUris: List<String>, outputUri: String?) = ConversionTask(
        id = "1",
        sourceUri = "content://src",
        sourceName = "clip.mp4",
        sourceFormat = FileFormat.MP4,
        targetFormat = FileFormat.WEBM,
        outputUri = outputUri,
        payload = ConversionPayload(outputUris = payloadUris),
    )
}
