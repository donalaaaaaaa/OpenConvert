package com.openconvert.app.domain.work

import com.openconvert.app.domain.model.ConversionStatus
import com.openconvert.app.domain.model.ConversionTask
import com.openconvert.app.domain.model.FileFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversionRecoveryTest {
    @Test
    fun `orphans without work are marked failed`() {
        val running = sample("run", ConversionStatus.RUNNING)
        val pending = sample("pend", ConversionStatus.PENDING)
        val recovered = ConversionRecovery.reconcile(
            activeTasks = listOf(running, pending),
            activeWorkIds = emptySet(),
            now = 42L,
        )
        assertEquals(2, recovered.size)
        assertTrue(recovered.all { it.status == ConversionStatus.FAILED })
        assertTrue(recovered.all { it.errorMessage == ConversionRecovery.ORPHAN_MESSAGE })
        assertTrue(recovered.all { it.completedAt == 42L })
    }

    @Test
    fun `active work is left untouched`() {
        val running = sample("keep", ConversionStatus.RUNNING)
        val recovered = ConversionRecovery.reconcile(
            activeTasks = listOf(running),
            activeWorkIds = setOf("keep"),
        )
        assertTrue(recovered.isEmpty())
    }

    private fun sample(id: String, status: ConversionStatus) = ConversionTask(
        id = id,
        sourceUri = "content://src",
        sourceName = "clip.mov",
        sourceFormat = FileFormat.MOV,
        targetFormat = FileFormat.MP4,
        status = status,
    )
}
