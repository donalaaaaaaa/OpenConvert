package com.openconvert.app.domain.task

import com.openconvert.app.domain.model.ConversionPayload
import com.openconvert.app.domain.model.ConversionStatus
import com.openconvert.app.domain.model.ConversionTask
import com.openconvert.app.domain.model.FileFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 任务中心 2.0 分组（计划书 §七）。 */
class TaskCenterGroupingTest {

    private var seq = 0

    private fun task(
        status: ConversionStatus,
        batchId: String? = null,
        createdAt: Long = (++seq).toLong(),
    ) = ConversionTask(
        id = "task-$seq",
        sourceUri = "content://x/$seq",
        sourceName = "f$seq.png",
        sourceFormat = FileFormat.PNG,
        targetFormat = FileFormat.JPG,
        status = status,
        createdAt = createdAt,
        payload = ConversionPayload(batchId = batchId),
    )

    @Test
    fun `groups map to the five buckets in the spec`() {
        val groups = TaskCenterGrouping.group(
            listOf(
                task(ConversionStatus.RUNNING),
                task(ConversionStatus.PENDING),
                task(ConversionStatus.FAILED),
                task(ConversionStatus.COMPLETED),
            ),
        )
        assertEquals(
            listOf(
                TaskBucket.RUNNING,
                TaskBucket.WAITING,
                TaskBucket.FAILED,
                TaskBucket.COMPLETED,
            ),
            groups.map { it.bucket },
        )
    }

    @Test
    fun `empty buckets are omitted`() {
        val groups = TaskCenterGrouping.group(listOf(task(ConversionStatus.COMPLETED)))
        assertEquals(1, groups.size)
        assertEquals(TaskBucket.COMPLETED, groups.first().bucket)
    }

    @Test
    fun `pending task inside a paused batch shows as paused`() {
        // 批量暂停时子任务保持 PENDING 以便恢复 —— 只看任务状态会误报「等待中」。
        val paused = task(ConversionStatus.PENDING, batchId = "batch-1")
        assertEquals(
            TaskBucket.PAUSED,
            TaskCenterGrouping.bucketOf(paused, pausedBatchIds = setOf("batch-1")),
        )
        // 同一任务，批量未暂停时应是等待中。
        assertEquals(TaskBucket.WAITING, TaskCenterGrouping.bucketOf(paused, emptySet()))
    }

    @Test
    fun `running task inside a paused batch also shows as paused`() {
        val t = task(ConversionStatus.RUNNING, batchId = "b")
        assertEquals(TaskBucket.PAUSED, TaskCenterGrouping.bucketOf(t, setOf("b")))
    }

    @Test
    fun `cancelled tasks land in the failed bucket`() {
        // 计划书只列了五组，取消归入「失败」而不是凭空造第六组。
        assertEquals(
            TaskBucket.FAILED,
            TaskCenterGrouping.bucketOf(task(ConversionStatus.CANCELLED)),
        )
    }

    @Test
    fun `tasks inside a group are newest first`() {
        val old = task(ConversionStatus.COMPLETED, createdAt = 100)
        val recent = task(ConversionStatus.COMPLETED, createdAt = 900)
        val groups = TaskCenterGrouping.group(listOf(old, recent))
        assertEquals(listOf(recent.id, old.id), groups.first().tasks.map { it.id })
    }

    @Test
    fun `active work detection covers running and pending only`() {
        assertTrue(TaskCenterGrouping.hasActiveWork(listOf(task(ConversionStatus.RUNNING))))
        assertTrue(TaskCenterGrouping.hasActiveWork(listOf(task(ConversionStatus.PENDING))))
        assertFalse(
            TaskCenterGrouping.hasActiveWork(
                listOf(task(ConversionStatus.COMPLETED), task(ConversionStatus.FAILED)),
            ),
        )
    }

    @Test
    fun `every status maps to some bucket`() {
        ConversionStatus.entries.forEach { status ->
            // 不抛异常即通过 —— when 是穷尽的，新增状态会编译失败而非运行时漏掉。
            TaskCenterGrouping.bucketOf(task(status))
        }
    }
}
