package com.openconvert.app.domain.work

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class BoundedIoTest {
    @Test
    fun `copies payloads larger than the fixed buffer`() {
        val source = ByteArray(BoundedIo.BUFFER_BYTES * 2 + 17) { index -> (index % 251).toByte() }
        val output = ByteArrayOutputStream()
        val seen = mutableListOf<Long>()
        val copied = BoundedIo.copy(ByteArrayInputStream(source), output, seen::add)
        assertEquals(source.size.toLong(), copied)
        assertArrayEquals(source, output.toByteArray())
        assertEquals(source.size.toLong(), seen.last())
        assertTrue(seen.size >= 3)
    }

    @Test
    fun `copies an empty stream as zero bytes`() {
        val output = ByteArrayOutputStream()
        assertEquals(0L, BoundedIo.copy(ByteArrayInputStream(ByteArray(0)), output))
        assertEquals(0, output.size())
    }

    private fun assertTrue(condition: Boolean) {
        org.junit.Assert.assertTrue(condition)
    }
}
