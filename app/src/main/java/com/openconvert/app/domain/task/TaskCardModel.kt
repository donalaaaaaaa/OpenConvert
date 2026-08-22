package com.openconvert.app.domain.task

import com.openconvert.app.domain.error.ErrorPresentation
import com.openconvert.app.domain.error.ErrorPresenter
import com.openconvert.app.domain.model.ConversionStatus
import com.openconvert.app.domain.model.ConversionTask

/**
 * 单任务卡片的完整展示数据（计划书 §7.1 / §7.2 / §7.3）。
 *
 * 运行中：
 * ```
 * movie.mkv
 * MKV → MP4
 * ████████░░ 82%
 * 速度：41 MB/s   剩余：1m 23s
 * ```
 * 完成：输入 18.4 MB → 输出 7.1 MB，耗时 13 秒
 * 失败：结构化错误（标题 / 明细 / 建议）
 */
data class TaskCardModel(
    val task: ConversionTask,
    val route: String,
    val progressPercent: Int,
    val speedText: String?,
    val remainingText: String?,
    val sizeSummary: String?,
    val elapsedText: String?,
    val engineText: String?,
    val error: ErrorPresentation?,
) {
    val id: String get() = task.id
    val title: String get() = task.outputName ?: task.sourceName
}

object TaskCardFactory {

    fun create(task: ConversionTask, estimate: ThroughputEstimate): TaskCardModel {
        val isRunning = task.status == ConversionStatus.RUNNING
        return TaskCardModel(
            task = task,
            route = "${task.sourceFormat.displayName} → ${task.targetFormat.displayName}",
            progressPercent = task.progress.coerceIn(0, 100),
            speedText = if (isRunning) ThroughputEstimator.formatSpeed(estimate.bytesPerSecond) else null,
            remainingText = if (isRunning) {
                ThroughputEstimator.formatRemaining(estimate.remainingMillis)
            } else {
                null
            },
            sizeSummary = sizeSummary(task),
            elapsedText = elapsedText(task),
            engineText = task.actualEngine?.let { "引擎 · ${it.displayName}" },
            error = if (task.status == ConversionStatus.FAILED) {
                ErrorPresenter.fromStoredMessage(
                    message = task.errorMessage,
                    sourceFormat = task.sourceFormat,
                    targetFormat = task.targetFormat,
                )
            } else {
                null
            },
        )
    }

    /** 「输入：18.4 MB  输出：7.1 MB」，输出未知时只给输入。 */
    private fun sizeSummary(task: ConversionTask): String? {
        if (task.status != ConversionStatus.COMPLETED) return null
        val input = task.fileSize.takeIf { it > 0 } ?: return null
        val output = task.outputSize?.takeIf { it > 0 }
            ?: return "输入 ${formatSize(input)}"
        return "输入 ${formatSize(input)} → 输出 ${formatSize(output)}"
    }

    /** 「耗时 13 秒」。缺时间戳时返回 null 而不是显示 0 秒。 */
    private fun elapsedText(task: ConversionTask): String? {
        val finished = task.completedAt ?: return null
        val elapsed = finished - task.createdAt
        if (elapsed <= 0L) return null
        val seconds = elapsed / 1000
        return when {
            seconds >= 3600 -> "耗时 ${seconds / 3600} 小时 ${(seconds % 3600) / 60} 分"
            seconds >= 60 -> "耗时 ${seconds / 60} 分 ${seconds % 60} 秒"
            seconds > 0 -> "耗时 $seconds 秒"
            else -> "耗时 ${elapsed}ms"
        }
    }

    fun formatSize(bytes: Long): String {
        val kb = 1024.0
        val mb = kb * 1024
        val gb = mb * 1024
        return when {
            bytes >= gb -> String.format("%.1f GB", bytes / gb)
            bytes >= mb -> String.format("%.1f MB", bytes / mb)
            bytes >= kb -> String.format("%.1f KB", bytes / kb)
            else -> "$bytes B"
        }
    }
}
