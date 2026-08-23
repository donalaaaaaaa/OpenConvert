package com.openconvert.app.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.openconvert.app.AppCopy
import com.openconvert.app.R
import com.openconvert.app.domain.model.ConversionStatus
import com.openconvert.app.domain.model.ConversionTask
import com.openconvert.app.domain.task.TaskBucket
import com.openconvert.app.domain.task.TaskCardFactory
import com.openconvert.app.domain.task.TaskCardModel

fun Modifier.actionSemantics(
    label: String,
    state: String? = null,
    selected: Boolean? = null,
    enabled: Boolean = true,
    role: Role = Role.Button,
): Modifier = semantics(mergeDescendants = true) {
    contentDescription = label
    this.role = role
    if (state != null) stateDescription = state
    if (selected != null) this.selected = selected
    if (!enabled) disabled()
}

fun Modifier.liveProgressSemantics(label: String): Modifier = semantics(mergeDescendants = true) {
    contentDescription = label
    liveRegion = LiveRegionMode.Polite
}

/**
 * TalkBack 文案。UI 和测试共用，避免按钮只有图标、进度条只报「进度条」。
 */
object AccessibilityCopy {
    fun tool(title: String, subtitle: String): String =
        if (subtitle.isBlank()) title else "$title，$subtitle"

    fun setting(title: String, value: String): String =
        if (value.isBlank()) title else "$title，$value"

    fun convertTo(formatDisplay: String): String =
        AppCopy.getOr(R.string.a11y_convert_to, "转换为 $formatDisplay", formatDisplay)

    fun pickFile(formatsHint: String): String =
        if (formatsHint.isBlank()) {
            AppCopy.getOr(R.string.home_pick_file, "选择文件")
        } else {
            AppCopy.getOr(R.string.a11y_pick_file, "选择文件，$formatsHint", formatsHint)
        }

    fun preset(name: String, detail: String, selected: Boolean): String = buildString {
        append(AppCopy.getOr(R.string.a11y_preset, "预设 $name", name))
        if (detail.isNotBlank()) append("，$detail")
        if (selected) append("，").append(AppCopy.getOr(R.string.a11y_preset_selected, "已选用"))
    }

    fun history(task: ConversionTask): String {
        val status = when (task.status) {
            ConversionStatus.PENDING -> AppCopy.getOr(R.string.a11y_status_pending, "排队中")
            ConversionStatus.RUNNING -> AppCopy.getOr(R.string.a11y_status_running, "进行中 ${task.progress}%", task.progress)
            ConversionStatus.FAILED -> AppCopy.getOr(R.string.a11y_status_failed, "失败")
            ConversionStatus.CANCELLED -> AppCopy.getOr(R.string.a11y_status_cancelled, "已取消")
            ConversionStatus.COMPLETED -> AppCopy.getOr(R.string.a11y_status_completed, "已完成")
        }
        return AppCopy.getOr(
            R.string.a11y_history,
            "${task.sourceName}，${task.sourceFormat.displayName} 转为 ${task.targetFormat.displayName}，$status",
            task.sourceName,
            task.sourceFormat.displayName,
            task.targetFormat.displayName,
            status,
        )
    }

    fun progress(percent: Int, bytesProcessed: Long = 0L, bytesTotal: Long = 0L): String {
        val clamped = percent.coerceIn(0, 100)
        return if (bytesTotal > 0L && bytesProcessed > 0L) {
            AppCopy.getOr(
                R.string.a11y_progress_bytes,
                "正在转换 $clamped%，已处理 ${TaskCardFactory.formatSize(bytesProcessed)} / ${TaskCardFactory.formatSize(bytesTotal)}",
                clamped,
                TaskCardFactory.formatSize(bytesProcessed),
                TaskCardFactory.formatSize(bytesTotal),
            )
        } else {
            AppCopy.getOr(R.string.a11y_progress, "正在转换 $clamped%", clamped)
        }
    }

    fun taskCard(card: TaskCardModel, bucket: TaskBucket): String {
        val extra = when (bucket) {
            TaskBucket.RUNNING, TaskBucket.PAUSED -> "，${card.progressPercent}%"
            TaskBucket.FAILED -> card.error?.title?.let { "，$it" }.orEmpty()
            TaskBucket.COMPLETED -> "，${AppCopy.getOr(R.string.a11y_completed, "已完成")}"
            TaskBucket.WAITING -> "，${AppCopy.getOr(R.string.a11y_waiting, "等待中")}"
        }
        return "${card.title}，${card.route}$extra"
    }
}
