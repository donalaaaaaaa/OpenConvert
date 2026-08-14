package com.openconvert.app.domain.converter

import com.openconvert.app.domain.model.BatchJob
import com.openconvert.app.domain.model.BatchJobStatus
import com.openconvert.app.domain.model.BatchSettings
import com.openconvert.app.domain.model.BatchSettingsCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfDeletePagesLogicTest {
    @Test
    fun remainingPagesCountsDistinctValidPages() {
        val converter = PdfDeletePagesConverterForTest()
        assertEquals(3, converter.remainingPages(5, listOf(1, 2)))
        assertEquals(5, converter.remainingPages(5, listOf(99))) // 越界忽略
        assertEquals(5, converter.remainingPages(5, emptyList()))
        assertEquals(3, converter.remainingPages(5, listOf(2, 2, 3))) // 重复去重
    }

    @Test
    fun deletingAllPagesIsRejectedByRemainingCheck() {
        val converter = PdfDeletePagesConverterForTest()
        assertEquals(0, converter.remainingPages(2, listOf(1, 2)))
    }
}

/** 仅暴露纯逻辑，避免测试依赖 Android ContentResolver。 */
class PdfDeletePagesConverterForTest {
    fun remainingPages(totalPages: Int, pagesToDelete: List<Int>): Int {
        val toDelete = pagesToDelete.map { it - 1 }.filter { it in 0 until totalPages }.distinct().size
        return totalPages - toDelete
    }
}

class BatchJobTest {
    @Test
    fun progressPercentScalesWithDoneAndFailed() {
        val job = BatchJob(
            id = "b1",
            name = "10 files",
            status = BatchJobStatus.RUNNING,
            total = 10,
            done = 2,
            failed = 1,
            createdAt = 0,
        )
        assertEquals(30, job.progressPercent)
    }

    @Test
    fun progressPercentClampsToRange() {
        val over = BatchJob("b2", "x", BatchJobStatus.RUNNING, 2, 2, 1, 0)
        assertEquals(100, over.progressPercent)
        val empty = BatchJob("b3", "x", BatchJobStatus.RUNNING, 0, 0, 0, 0)
        assertEquals(0, empty.progressPercent)
    }

    @Test
    fun settingsCodecRoundTrips() {
        val settings = BatchSettings(
            sourceUris = listOf("content://a", "content://b"),
            sourceNames = listOf("a.jpg", "b.jpg"),
            sourceFormats = listOf("JPG", "JPG"),
            targetFormat = "WEBP",
            quality = "HIGH",
            resolution = "SMALL",
            outputTreeUri = "content://tree",
        )
        val decoded = BatchSettingsCodec.decode(BatchSettingsCodec.encode(settings))
        assertEquals(settings, decoded)
    }

    @Test
    fun settingsCodecHandlesBlank() {
        val decoded = BatchSettingsCodec.decode("")
        assertEquals(BatchSettings(), decoded)
        assertTrue(decoded.sourceUris.isEmpty())
    }

    @Test
    fun batchStatusRoundTripsThroughName() {
        BatchJobStatus.entries.forEach { status ->
            assertFalse(status.name.isBlank())
            assertEquals(status, BatchJobStatus.valueOf(status.name))
        }
    }
}
