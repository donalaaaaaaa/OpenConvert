package com.openconvert.app.work

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.openconvert.app.OpenConvertApplication
import com.openconvert.app.domain.model.BatchJobStatus
import com.openconvert.app.domain.model.ConversionStatus
import com.openconvert.app.domain.model.ConversionTask
import kotlinx.coroutines.launch

/**
 * 批量转换调度器：一个 BatchJob = 多个 ConversionTask（每个文件一个）。
 * 每个任务独立入队（复用 ConversionWorker），WorkManager 天然提供并行；
 * BatchConcurrency 在 Worker 内按文件类型限制真实并发。
 */
class BatchScheduler(context: Context) {
    private val appContext = context.applicationContext
    private val workManager get() = WorkManager.getInstance(appContext)

    fun enqueueTasks(batchId: String, tasks: List<ConversionTask>) {
        tasks.forEach { task ->
            val request = OneTimeWorkRequestBuilder<ConversionWorker>()
                .setInputData(workDataOf(ConversionWorker.KEY_TASK_ID to task.id))
                .addTag(TAG)
                .addTag(tagFor(batchId))
                .addTag(ConversionScheduler.tagFor(task.id))
                .build()
            workManager.enqueueUniqueWork(
                ConversionScheduler.workName(task.id),
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }

    /**
     * 暂停：取消 batch 下所有未完成 work，Room 任务保持 PENDING/RUNNING 状态
     * （Worker 停止时检测到 batch 为 PAUSED，不把任务标为 CANCELLED）。
     */
    fun pause(batchId: String) {
        workManager.cancelAllWorkByTag(tagFor(batchId))
        val app = appContext as? OpenConvertApplication ?: return
        app.applicationScope.launch {
            val job = app.historyRepository.getBatch(batchId) ?: return@launch
            if (job.status == BatchJobStatus.RUNNING || job.status == BatchJobStatus.PENDING) {
                app.historyRepository.saveBatch(job.copy(status = BatchJobStatus.PAUSED))
            }
        }
    }

    /** 继续：重新入队所有未完成任务。 */
    fun resume(batchId: String) {
        val app = appContext as? OpenConvertApplication ?: return
        app.applicationScope.launch {
            val job = app.historyRepository.getBatch(batchId) ?: return@launch
            val tasks = app.historyRepository.batchTasks(batchId)
                .filter { it.status == ConversionStatus.PENDING || it.status == ConversionStatus.RUNNING }
            if (tasks.isEmpty()) {
                app.historyRepository.saveBatch(job.copy(status = BatchJobStatus.COMPLETED))
                return@launch
            }
            enqueueTasks(batchId, tasks)
            app.historyRepository.saveBatch(job.copy(status = BatchJobStatus.RUNNING))
        }
    }

    /** 取消整个批量：取消 work + 所有未完成任务标 CANCELLED + batch 标 CANCELLED。 */
    fun cancel(batchId: String) {
        workManager.cancelAllWorkByTag(tagFor(batchId))
        val app = appContext as? OpenConvertApplication ?: return
        app.applicationScope.launch {
            val job = app.historyRepository.getBatch(batchId) ?: return@launch
            app.historyRepository.batchTasks(batchId)
                .filter { it.status == ConversionStatus.PENDING || it.status == ConversionStatus.RUNNING }
                .forEach { task ->
                    app.historyRepository.save(
                        task.copy(
                            status = ConversionStatus.CANCELLED,
                            completedAt = System.currentTimeMillis(),
                        ),
                    )
                }
            app.historyRepository.saveBatch(
                job.copy(
                    status = BatchJobStatus.CANCELLED,
                    finishedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    companion object {
        const val TAG = "openconvert-batch"
        const val BATCH_PREFIX = "batch:"

        fun tagFor(batchId: String): String = "$BATCH_PREFIX$batchId"
    }
}
