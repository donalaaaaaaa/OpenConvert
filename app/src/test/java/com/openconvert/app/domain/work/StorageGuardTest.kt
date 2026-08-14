package com.openconvert.app.domain.work

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageGuardTest {
    @Test
    fun `scratch budget includes input only when the engine copies it`() {
        val input = 2L * 1024 * 1024 * 1024
        val withCopy = StorageGuard.requiredScratchBytes(input, copiesInput = true)
        val withoutCopy = StorageGuard.requiredScratchBytes(input, copiesInput = false)
        assertTrue(withCopy > withoutCopy)
        assertEquals(input, withCopy - withoutCopy)
        assertTrue(withoutCopy >= input + StorageGuard.SAFETY_MARGIN_BYTES)
    }

    @Test
    fun `rejects usable space below the required budget`() {
        val required = StorageGuard.requiredScratchBytes(100L * 1024 * 1024, copiesInput = true)
        assertFalse(StorageGuard.hasEnoughSpace(required - 1, required))
        assertTrue(StorageGuard.hasEnoughSpace(required, required))
    }
}
