package com.openconvert.app.domain.batch

import org.junit.Assert.assertEquals
import org.junit.Test

class BatchModelsTest {

    @Test
    fun testBatchSummaryReport() {
        val report = BatchSummaryReport(
            totalCount = 10,
            successCount = 8,
            failedCount = 2,
            totalOriginalSizeBytes = 100_000_000L,
            totalOutputSizeBytes = 40_000_000L,
            reductionPercent = 60.0,
            durationMs = 12_500L,
        )

        assertEquals(10, report.totalCount)
        assertEquals(8, report.successCount)
        assertEquals(2, report.failedCount)
        assertEquals(60.0, report.reductionPercent, 0.01)
    }

    @Test
    fun testDuplicateFileStrategy() {
        assertEquals("自动重命名 (推荐)", DuplicateFileStrategy.RENAME.displayName)
        assertEquals("覆盖原文件", DuplicateFileStrategy.OVERWRITE.displayName)
        assertEquals("跳过已存在文件", DuplicateFileStrategy.SKIP.displayName)
    }
}
