package com.openconvert.app.domain.work

import com.openconvert.app.domain.model.ConversionStatus
import com.openconvert.app.domain.model.ConversionTask

object ConversionRecovery {
    const val ORPHAN_MESSAGE = "上次转换被系统中断，请重试"

    fun reconcile(
        activeTasks: List<ConversionTask>,
        activeWorkIds: Set<String>,
        now: Long = System.currentTimeMillis(),
    ): List<ConversionTask> = activeTasks
        .filter { it.status == ConversionStatus.PENDING || it.status == ConversionStatus.RUNNING }
        .filter { it.id !in activeWorkIds }
        .map { task ->
            task.copy(
                status = ConversionStatus.FAILED,
                errorMessage = ORPHAN_MESSAGE,
                completedAt = now,
            )
        }
}
