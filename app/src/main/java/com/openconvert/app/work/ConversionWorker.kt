package com.openconvert.app.work

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.openconvert.app.OpenConvertApplication
import com.openconvert.app.domain.converter.ConversionExecutor
import com.openconvert.app.domain.converter.ExecutionResult
import com.openconvert.app.domain.model.BatchJobStatus
import com.openconvert.app.domain.model.ConversionStatus
import com.openconvert.app.domain.model.ConversionTask
import com.openconvert.app.domain.work.BatchConcurrency
import kotlinx.coroutines.CancellationException

class ConversionWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val taskId = inputData.getString(KEY_TASK_ID)
        val task = taskId?.let { repository()?.get(it) }
        return ConversionNotifier.foregroundInfo(applicationContext, task)
    }

    override suspend fun doWork(): Result {
        val taskId = inputData.getString(KEY_TASK_ID) ?: return Result.failure()
        val repo = repository() ?: return Result.failure()
        var task = repo.get(taskId) ?: return Result.failure()

        when (task.status) {
            ConversionStatus.CANCELLED, ConversionStatus.COMPLETED -> return Result.success()
            ConversionStatus.FAILED -> return Result.failure()
            ConversionStatus.PENDING, ConversionStatus.RUNNING -> Unit
        }

        // 批量任务：batch 已被暂停/取消时，直接让出（保留任务状态以便恢复）。
        val batchId = task.payload.batchId
        if (!batchId.isNullOrBlank()) {
            val batch = repo.getBatch(batchId)
            if (batch == null) return Result.failure()
            if (batch.status == BatchJobStatus.PAUSED) return Result.success()
            if (batch.status == BatchJobStatus.CANCELLED) return Result.success()
        }

        setForeground(ConversionNotifier.foregroundInfo(applicationContext, task))
        task = task.copy(status = ConversionStatus.RUNNING, progress = maxOf(task.progress, 1))
        repo.save(task)
        ConversionNotifier.notifyProgress(applicationContext, task)

        return try {
            val result = BatchConcurrency.withPermit(task) {
                ConversionExecutor(applicationContext).execute(task) { progress ->
                    if (isStopped) throw CancellationException()
                    val latest = repo.get(taskId)
                    if (latest?.status == ConversionStatus.CANCELLED) throw CancellationException()
                    val total = task.fileSize
                    val processed = if (total > 0L) total * progress.toLong() / 100L else 0L
                    val updated = (latest ?: task).copy(
                        progress = progress.coerceIn(0, 100),
                        bytesProcessed = processed,
                        bytesTotal = total,
                        status = ConversionStatus.RUNNING,
                    )
                    repo.save(updated)
                    ConversionNotifier.notifyProgress(applicationContext, updated)
                }
            }

            val latest = repo.get(taskId)
            if (isStopped || latest?.status == ConversionStatus.CANCELLED) {
                finalizeCancelled(latest ?: task)
                return Result.success()
            }

            when (result) {
                is ExecutionResult.Success -> {
                    val done = (latest ?: task).copy(
                        outputUri = result.outputUri,
                        outputSize = result.outputSize,
                        progress = 100,
                        status = ConversionStatus.COMPLETED,
                        completedAt = System.currentTimeMillis(),
                        outputName = result.outputName ?: task.outputName,
                        actualEngine = result.actualEngine,
                        payload = task.payload.copy(outputUris = result.outputUris),
                    )
                    repo.save(done)
                    ConversionNotifier.notifyFinished(applicationContext, done)
                    updateBatchProgress(repo, taskId)
                    Result.success()
                }

                is ExecutionResult.Failure -> {
                    val failed = (latest ?: task).copy(
                        status = ConversionStatus.FAILED,
                        errorMessage = result.message,
                        errorCode = result.errorCode,
                        completedAt = System.currentTimeMillis(),
                    )
                    repo.save(failed)
                    ConversionNotifier.notifyFinished(applicationContext, failed)
                    updateBatchProgress(repo, taskId)
                    Result.failure()
                }

                ExecutionResult.Cancelled -> {
                    finalizeCancelled(latest ?: task)
                    Result.success()
                }
            }
        } catch (_: CancellationException) {
            finalizeCancelled(repo.get(taskId) ?: task)
            Result.success()
        } catch (_: Throwable) {
            val failed = task.copy(
                status = ConversionStatus.FAILED,
                errorMessage = "转换失败，请重试",
                errorCode = com.openconvert.app.domain.error.ConversionError.Code.UNKNOWN.name,
                completedAt = System.currentTimeMillis(),
            )
            repo.save(failed)
            ConversionNotifier.notifyFinished(applicationContext, failed)
            updateBatchProgress(repo, taskId)
            Result.failure()
        } finally {
            cleanupTemps(taskId)
        }
    }

    /**
     * 批量任务结束时刷新 BatchJob 的 done/failed 计数。
     * 全部完成（含失败）时置 COMPLETED（部分失败也视为批量结束）。
     */
    private suspend fun updateBatchProgress(
        repo: com.openconvert.app.data.repository.ConversionHistoryRepository,
        taskId: String,
    ) {
        val task = repo.get(taskId) ?: return
        val batchId = task.payload.batchId ?: return
        val batch = repo.getBatch(batchId) ?: return
        val tasks = repo.batchTasks(batchId)
        if (tasks.any { it.status == ConversionStatus.PENDING || it.status == ConversionStatus.RUNNING }) return
        val done = tasks.count { it.status == ConversionStatus.COMPLETED }
        val failed = tasks.count { it.status == ConversionStatus.FAILED }
        repo.saveBatch(
            batch.copy(
                status = BatchJobStatus.COMPLETED,
                done = done,
                failed = failed,
                finishedAt = System.currentTimeMillis(),
            ),
        )
    }

    private suspend fun finalizeCancelled(task: ConversionTask) {
        if (task.status != ConversionStatus.COMPLETED) {
            // 批量暂停：保留任务 PENDING，等待恢复。
            val batchId = task.payload.batchId
            if (!batchId.isNullOrBlank()) {
                val batch = repository()?.getBatch(batchId)
                if (batch?.status == BatchJobStatus.PAUSED) {
                    repository()?.save(
                        task.copy(status = ConversionStatus.PENDING, progress = 1),
                    )
                    ConversionNotifier.dismissProgress(applicationContext)
                    return
                }
            }
            task.outputUri?.let { uri ->
                runCatching { applicationContext.contentResolver.delete(Uri.parse(uri), null, null) }
            }
            repository()?.save(
                task.copy(
                    status = ConversionStatus.CANCELLED,
                    completedAt = System.currentTimeMillis(),
                ),
            )
        }
        ConversionNotifier.dismissProgress(applicationContext)
    }

    private fun cleanupTemps(taskId: String) {
        val temps = (applicationContext as? OpenConvertApplication)?.tempWorkspace
            ?: com.openconvert.app.domain.work.TempWorkspaceManager(applicationContext)
        temps.cleanup(com.openconvert.app.domain.work.TempWorkspaceManager.NS_MEDIA, taskId)
        temps.cleanup(com.openconvert.app.domain.work.TempWorkspaceManager.NS_OFFICE, taskId)
        temps.cleanup(com.openconvert.app.domain.work.TempWorkspaceManager.NS_ARCHIVE, taskId)
        temps.cleanup(com.openconvert.app.domain.work.TempWorkspaceManager.NS_PDF, taskId)
    }

    private fun repository() = (applicationContext as? OpenConvertApplication)?.historyRepository

    companion object {
        const val KEY_TASK_ID = "task_id"
    }
}
