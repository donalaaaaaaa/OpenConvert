package com.openconvert.app.domain.task

import com.openconvert.app.domain.model.ConversionStatus
import com.openconvert.app.domain.model.ConversionTask

/**
 * 任务中心 2.0 的分组（计划书 §七）：正在运行 / 等待中 / 暂停 / 失败 / 已完成。
 *
 * 「暂停」不是 [ConversionStatus] 的取值——它由所属批量任务的 PAUSED 状态推导：
 * 批量被暂停时其子任务保持 PENDING 以便恢复（见 ConversionWorker.finalizeCancelled），
 * 因此判断暂停必须结合 batch 状态，不能只看任务本身。
 */
enum class TaskBucket(val label: String) {
    RUNNING("正在运行"),
    WAITING("等待中"),
    PAUSED("暂停"),
    FAILED("失败"),
    COMPLETED("已完成");

    val labelRes: Int
        get() = when (this) {
            RUNNING -> com.openconvert.app.R.string.bucket_running
            WAITING -> com.openconvert.app.R.string.bucket_waiting
            PAUSED -> com.openconvert.app.R.string.bucket_paused
            FAILED -> com.openconvert.app.R.string.bucket_failed
            COMPLETED -> com.openconvert.app.R.string.bucket_completed
        }
}

data class TaskGroup(
    val bucket: TaskBucket,
    val tasks: List<ConversionTask>,
) {
    val count: Int get() = tasks.size
}

object TaskCenterGrouping {

    /**
     * 按 [TaskBucket] 分组并排序：活跃任务在前，已完成在后；
     * 组内按时间倒序（最近的在上）。空组不返回。
     *
     * @param pausedBatchIds 处于 PAUSED 的批量任务 id 集合。
     */
    fun group(
        tasks: List<ConversionTask>,
        pausedBatchIds: Set<String> = emptySet(),
    ): List<TaskGroup> {
        val buckets = tasks.groupBy { bucketOf(it, pausedBatchIds) }
        return TaskBucket.entries.mapNotNull { bucket ->
            val items = buckets[bucket] ?: return@mapNotNull null
            if (items.isEmpty()) return@mapNotNull null
            TaskGroup(
                bucket = bucket,
                tasks = items.sortedByDescending { it.completedAt ?: it.createdAt },
            )
        }
    }

    fun bucketOf(task: ConversionTask, pausedBatchIds: Set<String> = emptySet()): TaskBucket {
        val batchId = task.payload.batchId
        val inPausedBatch = !batchId.isNullOrBlank() && batchId in pausedBatchIds
        return when (task.status) {
            ConversionStatus.RUNNING -> if (inPausedBatch) TaskBucket.PAUSED else TaskBucket.RUNNING
            ConversionStatus.PENDING -> if (inPausedBatch) TaskBucket.PAUSED else TaskBucket.WAITING
            ConversionStatus.FAILED -> TaskBucket.FAILED
            ConversionStatus.CANCELLED -> TaskBucket.FAILED
            ConversionStatus.COMPLETED -> TaskBucket.COMPLETED
        }
    }

    /** 是否有任务正在跑或排队——决定任务中心是否显示活跃角标。 */
    fun hasActiveWork(tasks: List<ConversionTask>): Boolean = tasks.any {
        it.status == ConversionStatus.RUNNING || it.status == ConversionStatus.PENDING
    }
}
