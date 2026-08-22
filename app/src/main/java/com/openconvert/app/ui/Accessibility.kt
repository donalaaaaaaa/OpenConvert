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

    fun convertTo(formatDisplay: String): String = "转换为 $formatDisplay"

    fun pickFile(formatsHint: String): String =
        if (formatsHint.isBlank()) "选择文件" else "选择文件，$formatsHint"

    fun preset(name: String, detail: String, selected: Boolean): String = buildString {
        append("预设 $name")
        if (detail.isNotBlank()) append("，$detail")
        if (selected) append("，已选用")
    }

    fun history(task: ConversionTask): String {
        val status = when (task.status) {
            ConversionStatus.PENDING -> "排队中"
            ConversionStatus.RUNNING -> "进行中 ${task.progress}%"
            ConversionStatus.FAILED -> "失败"
            ConversionStatus.CANCELLED -> "已取消"
            ConversionStatus.COMPLETED -> "已完成"
        }
        return "${task.sourceName}，${task.sourceFormat.displayName} 转为 ${task.targetFormat.displayName}，$status"
    }

    fun progress(percent: Int, bytesProcessed: Long = 0L, bytesTotal: Long = 0L): String {
        val clamped = percent.coerceIn(0, 100)
        return if (bytesTotal > 0L && bytesProcessed > 0L) {
            "正在转换 $clamped%，已处理 ${TaskCardFactory.formatSize(bytesProcessed)} / ${TaskCardFactory.formatSize(bytesTotal)}"
        } else {
            "正在转换 $clamped%"
        }
    }

    fun taskCard(card: TaskCardModel, bucket: TaskBucket): String {
        val extra = when (bucket) {
            TaskBucket.RUNNING, TaskBucket.PAUSED -> "，${card.progressPercent}%"
            TaskBucket.FAILED -> card.error?.title?.let { "，$it" }.orEmpty()
            TaskBucket.COMPLETED -> "，已完成"
            TaskBucket.WAITING -> "，等待中"
        }
        return "${card.title}，${card.route}$extra"
    }
}
