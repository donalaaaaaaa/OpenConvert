package com.openconvert.app.data.saf

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.openconvert.app.domain.model.FileFormat
import com.openconvert.app.domain.model.FileTypeDetector

data class SelectedDocument(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val mimeType: String?,
    val format: FileFormat,
    /** true = 由 Magic Number 确认，而非仅靠扩展名。 */
    val magicVerified: Boolean = false,
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

    val mimeType = getType(uri)

    // 第三层 Magic Number：读文件头，避免单纯依赖扩展名。
    var magicVerified = false
    val magicFormat = runCatching {
        openInputStream(uri)?.use { stream ->
            FileTypeDetector.fromMagicNumber(stream).takeIf { it != FileFormat.UNKNOWN }
        }
    }.getOrNull()

    val format = when {
        magicFormat != null -> {
            magicVerified = true
            magicFormat
        }
        else -> FileTypeDetector.fromMimeType(mimeType) ?: FileFormat.fromFileName(name)
    }

    return SelectedDocument(
        uri = uri,
        name = name,
        sizeBytes = size,
        mimeType = mimeType,
        format = format,
        magicVerified = magicVerified,
    )
}
