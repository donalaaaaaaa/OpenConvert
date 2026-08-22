package com.openconvert.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.openconvert.app.OpenConvertApplication
import com.openconvert.app.domain.model.BatchJobStatus
import com.openconvert.app.domain.model.ConversionTask
import com.openconvert.app.domain.task.TaskCardFactory
import com.openconvert.app.domain.task.TaskCardModel
import com.openconvert.app.domain.task.TaskCenterGrouping
import com.openconvert.app.domain.task.TaskGroup
import com.openconvert.app.domain.task.ThroughputEstimate
import com.openconvert.app.domain.task.ThroughputTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 任务中心 2.0：按状态分组、速度估算、取消。
 * 速度不进 Room——重启后看几秒前的速率没有价值。
 */
class TaskCenterViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as OpenConvertApplication
    private val throughputTracker = ThroughputTracker()

    private val _taskCards = MutableStateFlow<Map<String, TaskCardModel>>(emptyMap())
    val taskCards: StateFlow<Map<String, TaskCardModel>> = _taskCards.asStateFlow()

    private val _taskGroups = MutableStateFlow<List<TaskGroup>>(emptyList())
    val taskGroups: StateFlow<List<TaskGroup>> = _taskGroups.asStateFlow()

    init {
        viewModelScope.launch {
            app.historyRepository.history.collect { tasks -> refresh(tasks) }
        }
    }

    fun cancelTask(taskId: String) {
        app.conversionScheduler.cancel(taskId)
    }

    private suspend fun refresh(tasks: List<ConversionTask>) {
        val pausedBatchIds = runCatching {
            app.database.batchJobDao().observeAll().first()
                .filter { it.status == BatchJobStatus.PAUSED.name }
                .map { it.id }
                .toSet()
        }.getOrDefault(emptySet())

        val estimates = throughputTracker.updateAll(tasks)
        _taskCards.value = tasks.associate { task ->
            task.id to TaskCardFactory.create(
                task = task,
                estimate = estimates[task.id] ?: ThroughputEstimate.UNKNOWN,
            )
        }
        _taskGroups.value = TaskCenterGrouping.group(
            tasks = tasks,
            pausedBatchIds = pausedBatchIds,
        )
    }
}
