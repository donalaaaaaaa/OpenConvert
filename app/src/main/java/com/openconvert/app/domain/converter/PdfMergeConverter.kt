package com.openconvert.app.domain.converter

import android.content.Context
import android.net.Uri
import com.openconvert.app.domain.model.ConversionResult
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import java.io.FileNotFoundException
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class PdfMergeConverter(
    private val context: Context,
    private val onProgress: suspend (Int) -> Unit = {},
) {
    private val resolver = context.contentResolver

    suspend fun convert(inputUris: List<Uri>, outputUri: Uri): ConversionResult = withContext(Dispatchers.IO) {
        if (inputUris.size < 2) return@withContext ConversionResult.Failure("至少选择 2 个 PDF")
        if (inputUris.size > MAX_INPUT_FILES) {
            return@withContext ConversionResult.Failure("一次最多合并 $MAX_INPUT_FILES 个 PDF")
        }
        try {
            val openedStreams = mutableListOf<InputStream>()
            try {
                val merger = PDFMergerUtility()
                inputUris.forEachIndexed { documentIndex, uri ->
                    currentCoroutineContext().ensureActive()
                    val stream = resolver.openInputStream(uri)
                        ?: throw FileNotFoundException("无法读取第 ${documentIndex + 1} 个 PDF")
                    openedStreams += stream
                    merger.addSource(stream)
                    onProgress(((documentIndex + 1) * 30 / inputUris.size).coerceAtLeast(1))
                }
                resolver.openOutputStream(outputUri, "w")?.use { output ->
                    merger.destinationStream = output
                    merger.mergeDocuments(MemoryUsageSetting.setupMixed(16L * 1024L * 1024L))
                } ?: throw FileNotFoundException("无法写入合并后的 PDF")
            } finally {
                openedStreams.forEach { runCatching { it.close() } }
            }
            onProgress(100)
            val size = resolver.openFileDescriptor(outputUri, "r")?.use { it.statSize } ?: 0L
            ConversionResult.Success(outputUri.toString(), size.coerceAtLeast(0))
        } catch (cancelled: CancellationException) {
            runCatching { resolver.delete(outputUri, null, null) }
            ConversionResult.Cancelled
        } catch (error: Throwable) {
            runCatching { resolver.delete(outputUri, null, null) }
            val message = if (error.javaClass.simpleName.contains("InvalidPassword")) {
                "暂不支持带密码的 PDF"
            } else {
                error.toUserMessage("PDF 合并失败，请检查文件是否损坏")
            }
            ConversionResult.Failure(message, error)
        }
    }

    private companion object {
        const val MAX_INPUT_FILES = 20
    }
}
