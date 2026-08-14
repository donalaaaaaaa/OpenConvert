package com.openconvert.app.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.openconvert.app.OpenConvertApplication
import com.openconvert.app.domain.model.ConversionStatus
import kotlinx.coroutines.launch

class CancelConversionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val app = context.applicationContext as? OpenConvertApplication ?: return
        app.conversionScheduler.cancel(taskId)
        val pending = goAsync()
        app.applicationScope.launch {
            try {
                val task = app.historyRepository.get(taskId) ?: return@launch
                if (task.status == ConversionStatus.RUNNING || task.status == ConversionStatus.PENDING) {
                    app.historyRepository.save(
                        task.copy(
                            status = ConversionStatus.CANCELLED,
                            completedAt = System.currentTimeMillis(),
                        ),
                    )
                }
            } finally {
                ConversionNotifier.dismissProgress(app)
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION = "com.openconvert.app.action.CANCEL_CONVERSION"
        const val EXTRA_TASK_ID = "task_id"
    }
}
