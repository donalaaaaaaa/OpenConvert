package com.openconvert.app.domain.task

import com.openconvert.app.domain.engine.EngineType
import com.openconvert.app.domain.model.ConversionPayload
import com.openconvert.app.domain.model.ConversionStatus
import com.openconvert.app.domain.model.ConversionTask
import com.openconvert.app.domain.model.FileFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThroughputTrackerTest {

    private var now = 0L
    private fun tracker() = ThroughputTracker { now }

    private fun task(
        id: String = "t1",
        status: ConversionStatus = ConversionStatus.RUNNING,
        progress: Int = 0,
        fileSize: Long = 100L * 1024 * 1024,
    ) = ConversionTask(
        id = id,
        sourceUri = "content://x",
        sourceName = "movie.mkv",
        sourceFormat = FileFormat.MKV,
        targetFormat = FileFormat.MP4,
        status = status,
        progress = progress,
        fileSize = fileSize,
    )

    @Test
    fun `first sample cannot produce an estimate`() {
        val t = tracker()
        now = 1_000
        val estimate = t.update(task(progress = 10))
        assertNull(estimate.bytesPerSecond)
        assertNull(estimate.remainingMillis)
    }

    @Test
    fun `second sample produces speed and remaining time`() {
        val t = tracker()
        now = 1_000
        t.update(task(progress = 10))
        now = 3_000
        val estimate = t.update(task(progress = 30))
        assertNotNull(estimate.bytesPerSecond)
        assertNotNull(estimate.remainingMillis)
    }

    @Test
    fun `stale sample keeps the previous estimate instead of blanking it`() {
        val t = tracker()
        now = 1_000
        t.update(task(progress = 10))
        now = 3_000
        val good = t.update(task(progress = 30))
        // 进度没推进：不能把已显示的速度抹成空白（否则卡片数字闪烁）。
        now = 3_100
        val stale = t.update(task(progress = 30))
        assertEquals(good, stale)
    }

    @Test
    fun `terminal status clears tracking`() {
        val t = tracker()
        now = 1_000
        t.update(task(progress = 50))
        assertEquals(1, t.trackedCount())

        now = 2_000
        val done = t.update(task(status = ConversionStatus.COMPLETED, progress = 100))
        assertNull(done.bytesPerSecond)
        assertEquals("终态任务必须从追踪表移除", 0, t.trackedCount())
    }

    @Test
    fun `tasks removed from the list are cleaned up`() {
        val t = tracker()
        now = 1_000
        t.updateAll(listOf(task(id = "a", progress = 10), task(id = "b", progress = 10)))
        assertEquals(2, t.trackedCount())

        now = 3_000
        t.updateAll(listOf(task(id = "a", progress = 30)))
        assertEquals("已消失的任务不得泄漏", 1, t.trackedCount())
    }

    @Test
    fun `updateAll returns an entry per task`() {
        val t = tracker()
        now = 1_000
        val result = t.updateAll(
            listOf(task(id = "a", progress = 5), task(id = "b", progress = 5)),
        )
        assertEquals(setOf("a", "b"), result.keys)
    }
}

class TaskCardFactoryTest {

    private fun base(
        status: ConversionStatus,
        progress: Int = 0,
        fileSize: Long = 0,
        outputSize: Long? = null,
        createdAt: Long = 1_000,
        completedAt: Long? = null,
        errorMessage: String? = null,
        actualEngine: EngineType? = null,
    ) = ConversionTask(
        id = "t",
        sourceUri = "content://x",
        sourceName = "report.docx",
        sourceFormat = FileFormat.DOCX,
        targetFormat = FileFormat.PDF,
        status = status,
        progress = progress,
        fileSize = fileSize,
        outputSize = outputSize,
        createdAt = createdAt,
        completedAt = completedAt,
        errorMessage = errorMessage,
        actualEngine = actualEngine,
        payload = ConversionPayload(),
    )

    @Test
    fun `running card shows route speed and remaining`() {
        val card = TaskCardFactory.create(
            base(ConversionStatus.RUNNING, progress = 82),
            ThroughputEstimate(41L * 1024 * 1024, 83_000L),
        )
        assertEquals("DOCX → PDF", card.route)
        assertEquals(82, card.progressPercent)
        assertEquals("41.0 MB/s", card.speedText)
        assertEquals("1m 23s", card.remainingText)
    }

    @Test
    fun `finished card hides speed and shows size summary plus elapsed`() {
        val card = TaskCardFactory.create(
            base(
                ConversionStatus.COMPLETED,
                progress = 100,
                fileSize = (18.4 * 1024 * 1024).toLong(),
                outputSize = (7.1 * 1024 * 1024).toLong(),
                createdAt = 1_000,
                completedAt = 14_000,
            ),
            ThroughputEstimate(999L, 999L),
        )
        assertNull("完成后不显示速度", card.speedText)
        assertNull(card.remainingText)
        assertTrue(card.sizeSummary!!.contains("18.4 MB"))
        assertTrue(card.sizeSummary!!.contains("7.1 MB"))
        assertEquals("耗时 13 秒", card.elapsedText)
    }

    @Test
    fun `finished card shows the actual fallback engine`() {
        val card = TaskCardFactory.create(
            base(
                ConversionStatus.COMPLETED,
                completedAt = 2_000,
                actualEngine = EngineType.FFMPEG_KIT,
            ),
            ThroughputEstimate.UNKNOWN,
        )
        assertEquals("引擎 · FFmpegKit", card.engineText)
    }

    @Test
    fun `failed card carries a structured error`() {
        val card = TaskCardFactory.create(
            base(ConversionStatus.FAILED, errorMessage = "存储空间不足，请清理后再试"),
            ThroughputEstimate.UNKNOWN,
        )
        val error = card.error
        assertNotNull(error)
        assertEquals("存储空间不足，请清理后再试", error!!.title)
        assertNotNull("失败必须带建议", error.suggestion)
    }

    @Test
    fun `failed card with no message still renders something actionable`() {
        val card = TaskCardFactory.create(
            base(ConversionStatus.FAILED, errorMessage = null),
            ThroughputEstimate.UNKNOWN,
        )
        assertTrue(card.error!!.title.isNotBlank())
    }

    @Test
    fun `successful card without output size falls back to input only`() {
        val card = TaskCardFactory.create(
            base(
                ConversionStatus.COMPLETED,
                fileSize = 5L * 1024 * 1024,
                outputSize = null,
                completedAt = 2_000,
            ),
            ThroughputEstimate.UNKNOWN,
        )
        assertEquals("输入 5.0 MB", card.sizeSummary)
    }

    @Test
    fun `missing timestamps do not fabricate a zero duration`() {
        val card = TaskCardFactory.create(
            base(ConversionStatus.COMPLETED, completedAt = null),
            ThroughputEstimate.UNKNOWN,
        )
        assertNull(card.elapsedText)
    }

    @Test
    fun `pending card has no error and no speed`() {
        val card = TaskCardFactory.create(
            base(ConversionStatus.PENDING),
            ThroughputEstimate.UNKNOWN,
        )
        assertNull(card.error)
        assertNull(card.speedText)
    }
}
