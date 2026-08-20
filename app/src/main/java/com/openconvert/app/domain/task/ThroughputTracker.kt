package com.openconvert.app.domain.task

import com.openconvert.app.domain.model.ConversionStatus
import com.openconvert.app.domain.model.ConversionTask

/**
 * 任务速度追踪（计划书 §7.1）。
 *
 * **有意不持久化**：速度与剩余时间是瞬时信息，存进 Room 需要一次迁移，
 * 而用户重启 App 后看到几秒前的速率并无价值。进程内保留最近一次采样即可；
 * 采样丢失时卡片只是暂时不显示速度，不影响任何功能。
 *
 * 非线程安全，只在主线程（ViewModel）里用。
 */
class ThroughputTracker(private val clock: () -> Long = System::currentTimeMillis) {

    private val samples = mutableMapOf<String, ProgressSample>()
    private val latest = mutableMapOf<String, ThroughputEstimate>()

    /**
     * 用任务当前进度更新估算。对每个任务：
     * - 首次见到只记采样，返回 UNKNOWN（两点才能算速率）
     * - 终态任务清理采样，避免 Map 无限增长
     * - 进度未变时保留上一次估算，避免卡片数字闪烁
     */
    fun update(task: ConversionTask): ThroughputEstimate {
        if (task.status != ConversionStatus.RUNNING) {
            samples.remove(task.id)
            latest.remove(task.id)
            return ThroughputEstimate.UNKNOWN
        }

        val now = clock()
        val current = ProgressSample(task.progress, now)
        val previous = samples[task.id]

        if (previous == null) {
            samples[task.id] = current
            return ThroughputEstimate.UNKNOWN
        }

        val estimate = ThroughputEstimator.estimate(previous, current, task.fileSize)
        return if (estimate == ThroughputEstimate.UNKNOWN) {
            // 采样太密或进度未推进：沿用上次结果，不要把已显示的速度抹成空白。
            latest[task.id] ?: ThroughputEstimate.UNKNOWN
        } else {
            samples[task.id] = current
            latest[task.id] = estimate
            estimate
        }
    }

    /** 批量刷新，返回 taskId → 估算。 */
    fun updateAll(tasks: List<ConversionTask>): Map<String, ThroughputEstimate> {
        val active = tasks.map { it.id }.toSet()
        // 已从列表消失的任务（被删除历史）一并清理。
        (samples.keys - active).forEach { samples.remove(it) }
        (latest.keys - active).forEach { latest.remove(it) }
        return tasks.associate { it.id to update(it) }
    }

    fun estimateFor(taskId: String): ThroughputEstimate =
        latest[taskId] ?: ThroughputEstimate.UNKNOWN

    /** 供测试断言内部没有泄漏。 */
    fun trackedCount(): Int = samples.size
}
