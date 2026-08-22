package com.openconvert.app.domain.benchmark

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class BenchmarkMemorySamplerTest {

    @Test
    fun `captures a transient peak between start and finish`() = runTest {
        var current = 10L

        val measured = measurePeakMemory(
            initialBytes = 5L,
            intervalMillis = 1L,
            sample = { current },
        ) {
            current = 80L
            delay(2L)
            current = 20L
            "done"
        }

        assertEquals("done", measured.value)
        assertEquals(80L, measured.peakBytes)
    }

    @Test
    fun `initial sample remains the peak when later usage is lower`() = runTest {
        val measured = measurePeakMemory(
            initialBytes = 100L,
            intervalMillis = 1L,
            sample = { 20L },
        ) {
            delay(2L)
            Unit
        }

        assertEquals(100L, measured.peakBytes)
    }
}
