package com.openconvert.app.work

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import com.openconvert.app.MainActivity
import com.openconvert.app.domain.model.ConversionStatus
import com.openconvert.app.domain.model.ConversionTask

object ConversionNotifier {
    const val CHANNEL_ID = "openconvert.conversions"
    const val PROGRESS_NOTIFICATION_ID = 1001
    private const val RESULT_NOTIFICATION_ID = 1002

    fun ensureChannel(context: Context) {
        val manager = manager(context)
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(com.openconvert.app.R.string.notif_channel),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(com.openconvert.app.R.string.notif_channel_desc)
                setShowBadge(false)
            },
        )
    }

    fun foregroundInfo(context: Context, task: ConversionTask?): ForegroundInfo {
        val notification = progressNotification(context, task)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                PROGRESS_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(PROGRESS_NOTIFICATION_ID, notification)
        }
    }

    fun notifyProgress(context: Context, task: ConversionTask) {
        ensureChannel(context)
        manager(context).notify(PROGRESS_NOTIFICATION_ID, progressNotification(context, task))
    }

    fun notifyFinished(context: Context, task: ConversionTask) {
        ensureChannel(context)
        manager(context).cancel(PROGRESS_NOTIFICATION_ID)
        val title = if (task.status == ConversionStatus.COMPLETED) {
            context.getString(com.openconvert.app.R.string.conversion_complete)
        } else {
            context.getString(com.openconvert.app.R.string.conversion_failed)
        }
        val text = when (task.status) {
            ConversionStatus.COMPLETED -> task.outputName ?: task.sourceName
            ConversionStatus.FAILED -> task.errorMessage ?: context.getString(com.openconvert.app.R.string.conversion_failed)
            else -> task.sourceName
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        manager(context).notify(RESULT_NOTIFICATION_ID, notification)
    }

    fun dismissProgress(context: Context) {
        manager(context).cancel(PROGRESS_NOTIFICATION_ID)
    }

    private fun progressNotification(context: Context, task: ConversionTask?): Notification {
        ensureChannel(context)
        val progress = task?.progress?.coerceIn(0, 100) ?: 0
        val name = task?.sourceName ?: context.getString(com.openconvert.app.R.string.notif_file)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("OpenConvert")
            .setContentText(context.getString(com.openconvert.app.R.string.notif_running, name))
            .setSubText("$progress%")
            .setProgress(100, progress, progress <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent(context))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (task != null) {
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                context.getString(com.openconvert.app.R.string.action_cancel),
                cancelIntent(context, task.id),
            )
        }
        return builder.build()
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun cancelIntent(context: Context, taskId: String): PendingIntent {
        val intent = Intent(context, CancelConversionReceiver::class.java).apply {
            action = CancelConversionReceiver.ACTION
            putExtra(CancelConversionReceiver.EXTRA_TASK_ID, taskId)
        }
        return PendingIntent.getBroadcast(
            context,
            taskId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun manager(context: Context): NotificationManager =
        context.getSystemService(NotificationManager::class.java)
}
