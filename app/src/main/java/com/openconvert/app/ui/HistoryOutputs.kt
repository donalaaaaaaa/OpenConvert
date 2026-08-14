package com.openconvert.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.openconvert.app.domain.model.ConversionTask

object HistoryOutputs {
    /** Pure string selection so it is testable without an Android runtime. */
    fun uriStrings(task: ConversionTask): List<String> =
        task.payload.outputUris.ifEmpty { listOfNotNull(task.outputUri) }

    fun uris(task: ConversionTask): List<Uri> = uriStrings(task).map(Uri::parse)

    fun openIntent(task: ConversionTask): Intent? {
        val uri = uris(task).firstOrNull() ?: return null
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, task.targetFormat.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun shareIntent(task: ConversionTask): Intent? {
        val outputs = uris(task)
        if (outputs.isEmpty()) return null
        return Intent(
            if (outputs.size > 1) Intent.ACTION_SEND_MULTIPLE else Intent.ACTION_SEND,
        ).apply {
            type = task.targetFormat.mimeType
            if (outputs.size > 1) {
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(outputs))
            } else {
                putExtra(Intent.EXTRA_STREAM, outputs.first())
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun startOpen(context: Context, task: ConversionTask): Boolean {
        val intent = openIntent(task) ?: return false
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    fun startShare(context: Context, task: ConversionTask): Boolean {
        val intent = shareIntent(task) ?: return false
        return runCatching {
            context.startActivity(Intent.createChooser(intent, "分享转换后的文件"))
        }.isSuccess
    }
}
