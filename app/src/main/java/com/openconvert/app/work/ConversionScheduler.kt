package com.openconvert.app.work

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf

class ConversionScheduler(context: Context) {
    private val appContext = context.applicationContext
    private val workManager get() = WorkManager.getInstance(appContext)

    fun enqueue(taskId: String) {
        // A single APPEND queue stays blocked after cancel/reinstall.
        // Each conversion is its own unique work so a new start always runs.
        workManager.cancelUniqueWork(LEGACY_QUEUE_NAME)
        val request = OneTimeWorkRequestBuilder<ConversionWorker>()
            .setInputData(workDataOf(ConversionWorker.KEY_TASK_ID to taskId))
            .addTag(TAG)
            .addTag(tagFor(taskId))
            .build()
        workManager.enqueueUniqueWork(workName(taskId), ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(taskId: String) {
        workManager.cancelUniqueWork(workName(taskId))
        workManager.cancelAllWorkByTag(tagFor(taskId))
        cancelLegacyQueue()
    }

    fun cancelLegacyQueue() {
        workManager.cancelUniqueWork(LEGACY_QUEUE_NAME)
    }

    suspend fun activeTaskIds(): Set<String> {
        val infos = workManager.getWorkInfosByTag(TAG).get()
        return infos
            .filter { !it.state.isFinished }
            .mapNotNull { info ->
                info.tags.firstOrNull { it.startsWith(TASK_PREFIX) }?.removePrefix(TASK_PREFIX)
            }
            .toSet()
    }

    companion object {
        const val LEGACY_QUEUE_NAME = "openconvert-conversion-queue"
        const val TAG = "openconvert-conversion"
        const val TASK_PREFIX = "task:"

        fun tagFor(taskId: String): String = "$TASK_PREFIX$taskId"
        fun workName(taskId: String): String = "openconvert-conversion-$taskId"
    }
}
