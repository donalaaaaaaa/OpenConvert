package com.openconvert.app.ui

import android.content.Intent
import android.net.Uri
import com.openconvert.app.OpenConvertApplication
import com.openconvert.app.domain.model.ConversionStatus
import com.openconvert.app.domain.model.ConversionTask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Convert / PDF / Batch 共用的转换会话：一次只能跑一条单任务，状态给完成页用。
 */
class ConversionHost(
    val app: OpenConvertApplication,
    val scope: CoroutineScope,
) {
    private val resolver = app.contentResolver
    private var trackedTaskId: String? = null
    private var observeJob: Job? = null

    private val _conversionState = MutableStateFlow<ConversionUiState>(ConversionUiState.Configuring)
    val conversionState: StateFlow<ConversionUiState> = _conversionState.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    var postedMessage: String?
        get() = _message.value
        set(value) {
            _message.value = value
        }

    var uiState: ConversionUiState
        get() = _conversionState.value
        set(value) {
            _conversionState.value = value
        }

    fun postMessage(text: String) {
        _message.value = text
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun setConfiguring() {
        _conversionState.value = ConversionUiState.Configuring
    }

    fun submit(task: ConversionTask) {
        if (!ensureIdle()) return
        trackTask(task.copy(status = ConversionStatus.RUNNING, progress = 1))
        scope.launch {
            app.historyRepository.save(task)
            app.conversionScheduler.enqueue(task.id)
        }
    }

    fun trackTask(task: ConversionTask) {
        trackedTaskId = task.id
        applyTask(task)
        observeJob?.cancel()
        observeJob = scope.launch {
            app.historyRepository.observe(task.id).collect { latest ->
                if (latest != null) applyTask(latest)
            }
        }
    }

    fun cancelConversion() {
        val taskId = trackedTaskId ?: return
        cancelTask(taskId)
    }

    fun cancelTask(taskId: String) {
        app.conversionScheduler.cancel(taskId)
    }

    fun retryConversion() {
        _conversionState.value = ConversionUiState.Configuring
    }

    fun resetConversion() {
        if (_conversionState.value is ConversionUiState.Running) cancelConversion()
        _conversionState.value = ConversionUiState.Configuring
    }

    fun persistDocumentPermission(uri: Uri) {
        runCatching {
            resolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
    }

    fun persistTreePermission(uri: Uri) = persistDocumentPermission(uri)

    fun persistReadPermission(uri: Uri) {
        runCatching {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun takeReadPermission(uri: Uri) = persistReadPermission(uri)

    private fun applyTask(task: ConversionTask) {
        _conversionState.value = when (task.status) {
            ConversionStatus.PENDING, ConversionStatus.RUNNING -> ConversionUiState.Running(task)
            ConversionStatus.COMPLETED -> ConversionUiState.Completed(
                task = task,
                outputName = task.outputName ?: task.sourceName,
                outputUris = task.payload.outputUris.ifEmpty { listOfNotNull(task.outputUri) },
            )
            ConversionStatus.FAILED -> ConversionUiState.Failed(
                task,
                task.errorMessage ?: "转换失败",
            )
            ConversionStatus.CANCELLED -> {
                if (_conversionState.value is ConversionUiState.Running) {
                    _message.value = "转换已取消"
                }
                ConversionUiState.Configuring
            }
        }
    }

    private fun ensureIdle(): Boolean {
        if (_conversionState.value is ConversionUiState.Running) {
            _message.value = "请等待当前转换完成"
            return false
        }
        return true
    }
}
