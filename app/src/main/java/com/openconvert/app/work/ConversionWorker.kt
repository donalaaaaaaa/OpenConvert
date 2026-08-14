package com.openconvert.app.work

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.openconvert.app.OpenConvertApplication
import com.openconvert.app.domain.converter.ConversionExecutor
import com.openconvert.app.domain.converter.ExecutionResult
import com.openconvert.app.domain.model.ConversionStatus
import com.openconvert.app.domain.model.ConversionTask
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

        setForeground(ConversionNotifier.foregroundInfo(applicationContext, task))
        task = task.copy(status = ConversionStatus.RUNNING, progress = maxOf(task.progress, 1))
        repo.save(task)
        ConversionNotifier.notifyProgress(applicationContext, task)

        return try {
            val result = ConversionExecutor(applicationContext).execute(task) { progress ->
                if (isStopped) throw CancellationException()
                val latest = repo.get(taskId)
                if (latest?.status == ConversionStatus.CANCELLED) throw CancellationException()
                val updated = (latest ?: task).copy(
                    progress = progress,
                    status = ConversionStatus.RUNNING,
                )
                repo.save(updated)
                ConversionNotifier.notifyProgress(applicationContext, updated)
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
                        payload = task.payload.copy(outputUris = result.outputUris),
                    )
                    repo.save(done)
                    ConversionNotifier.notifyFinished(applicationContext, done)
                    Result.success()
                }

                is ExecutionResult.Failure -> {
                    val failed = (latest ?: task).copy(
                        status = ConversionStatus.FAILED,
                        errorMessage = result.message,
                        completedAt = System.currentTimeMillis(),
                    )
                    repo.save(failed)
                    ConversionNotifier.notifyFinished(applicationContext, failed)
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
                completedAt = System.currentTimeMillis(),
            )
            repo.save(failed)
            ConversionNotifier.notifyFinished(applicationContext, failed)
            Result.failure()
        }
    }

    private suspend fun finalizeCancelled(task: ConversionTask) {
        if (task.status != ConversionStatus.COMPLETED) {
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

    private fun repository() = (applicationContext as? OpenConvertApplication)?.historyRepository

    companion object {
        const val KEY_TASK_ID = "task_id"
    }
}
