package com.openconvert.app.data.saf

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.openconvert.app.domain.model.FileFormat

data class SelectedDocument(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val mimeType: String?,
    val format: FileFormat,
)

fun ContentResolver.readSelectedDocument(uri: Uri): SelectedDocument {
    var name = uri.lastPathSegment ?: "未命名文件"
    var size = 0L

    query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex)
            if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
        }
    }

    return SelectedDocument(
        uri = uri,
        name = name,
        sizeBytes = size,
        mimeType = getType(uri),
        format = FileFormat.fromFileName(name),
    )
}

