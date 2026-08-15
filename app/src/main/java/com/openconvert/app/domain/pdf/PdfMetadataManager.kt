package com.openconvert.app.domain.pdf

import android.content.Context
import android.net.Uri
import com.openconvert.app.domain.work.BoundedIo
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDDocumentInformation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.text.SimpleDateFormat
import java.util.Locale

data class PdfMetadataInfo(
    val title: String = "",
    val author: String = "",
    val subject: String = "",
    val keywords: String = "",
    val creator: String = "",
    val producer: String = "",
    val creationDate: String = "",
    val modificationDate: String = "",
    val pageCount: Int = 0,
    val isEncrypted: Boolean = false,
    val fileSizeBytes: Long = 0L,
)

/**
 * PDF 元数据读取与编辑管理器（计划书 §十）。
 */
class PdfMetadataManager(
    private val context: Context,
) {
    private val resolver = context.contentResolver
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    suspend fun readMetadata(uri: Uri): PdfMetadataInfo = withContext(Dispatchers.IO) {
        var fileSize = 0L
        resolver.openFileDescriptor(uri, "r")?.use { pfd ->
            fileSize = pfd.statSize
        }

        resolver.openInputStream(uri)?.use { stream ->
            PDDocument.load(stream).use { doc ->
                val info = doc.documentInformation ?: PDDocumentInformation()
                PdfMetadataInfo(
                    title = info.title.orEmpty(),
                    author = info.author.orEmpty(),
                    subject = info.subject.orEmpty(),
                    keywords = info.keywords.orEmpty(),
                    creator = info.creator.orEmpty(),
                    producer = info.producer.orEmpty(),
                    creationDate = info.creationDate?.time?.let { dateFormat.format(it) }.orEmpty(),
                    modificationDate = info.modificationDate?.time?.let { dateFormat.format(it) }.orEmpty(),
                    pageCount = doc.numberOfPages,
                    isEncrypted = doc.isEncrypted,
                    fileSizeBytes = fileSize,
                )
            }
        } ?: throw FileNotFoundException("无法读取源 PDF 文件")
    }

    suspend fun updateMetadata(
        inputUri: Uri,
        outputUri: Uri,
        title: String,
        author: String,
        subject: String,
        keywords: String,
    ): Long = withContext(Dispatchers.IO) {
        val tempFile = File(context.cacheDir, "pdf_meta_${System.currentTimeMillis()}.pdf")
        try {
            resolver.openInputStream(inputUri)?.use { input ->
                PDDocument.load(input).use { doc ->
                    val info = doc.documentInformation ?: PDDocumentInformation()
                    info.title = title
                    info.author = author
                    info.subject = subject
                    info.keywords = keywords
                    doc.documentInformation = info

                    tempFile.outputStream().use { out ->
                        doc.save(out)
                    }
                }
            } ?: throw FileNotFoundException("无法读取源 PDF 文件")

            resolver.openOutputStream(outputUri, "wt")?.use { out ->
                tempFile.inputStream().use { input ->
                    BoundedIo.copy(input, out)
                }
                out.flush()
            } ?: throw FileNotFoundException("无法写入目标 PDF 文件")

            tempFile.length()
        } finally {
            tempFile.delete()
        }
    }
}
