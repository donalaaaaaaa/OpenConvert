package com.openconvert.app.domain.converter

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.FileNotFoundException
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class PdfSplitConverter(
    private val context: Context,
    private val onProgress: suspend (Int) -> Unit = {},
) {
    private val resolver = context.contentResolver

    suspend fun convert(
        inputUri: Uri,
        outputTreeUri: Uri,
        ranges: List<PdfPageRange>,
        sourceName: String,
    ): PdfBatchResult = withContext(Dispatchers.IO) {
        if (ranges.isEmpty()) return@withContext PdfBatchResult.Failure("没有输入拆分页码")
        if (ranges.size > MAX_OUTPUT_FILES) {
            return@withContext PdfBatchResult.Failure("一次最多生成 $MAX_OUTPUT_FILES 个 PDF")
        }
        val directory = DocumentFile.fromTreeUri(context, outputTreeUri)
            ?: return@withContext PdfBatchResult.Failure("无法访问所选文件夹")
        val created = mutableListOf<DocumentFile>()
        var totalBytes = 0L
        try {
            resolver.openInputStream(inputUri)?.use { stream ->
                PDDocument.load(stream).use { source ->
                    require(source.numberOfPages > 0) { "PDF 没有可拆分的页面" }
                    require(ranges.all { it.firstPage >= 1 && it.lastPage <= source.numberOfPages }) {
                        "拆分页码超出 PDF 范围"
                    }
                    val totalPages = ranges.sumOf { it.lastPage - it.firstPage + 1 }
                    var processed = 0
                    val baseName = sourceName.substringBeforeLast('.').sanitizePdfFileName()
                    ranges.forEachIndexed { rangeIndex, range ->
                        currentCoroutineContext().ensureActive()
                        val name = "${baseName}_part_${(rangeIndex + 1).toString().padStart(2, '0')}_pages_${range.label}.pdf"
                        val outputFile = directory.createFile("application/pdf", name)
                            ?: throw IOException("无法创建拆分后的 PDF")
                        created += outputFile
                        PDDocument().use { output ->
                            range.pages.forEach { pageNumber ->
                                currentCoroutineContext().ensureActive()
                                output.importPage(source.getPage(pageNumber - 1))
                                processed++
                                onProgress((processed * 90 / totalPages).coerceAtLeast(1))
                            }
                            resolver.openOutputStream(outputFile.uri, "w")?.use(output::save)
                                ?: throw FileNotFoundException("无法写入拆分后的 PDF")
                        }
                        totalBytes += outputFile.length().coerceAtLeast(0)
                    }
                }
            } ?: throw FileNotFoundException("无法读取 PDF")
            onProgress(100)
            PdfBatchResult.Success(created.map { it.uri.toString() }, totalBytes)
        } catch (cancelled: CancellationException) {
            created.forEach { runCatching { it.delete() } }
            PdfBatchResult.Cancelled
        } catch (error: Throwable) {
            created.forEach { runCatching { it.delete() } }
            val message = if (error.javaClass.simpleName.contains("InvalidPassword")) {
                "暂不支持带密码的 PDF"
            } else {
                error.toUserMessage("PDF 拆分失败，请检查文件是否损坏")
            }
            PdfBatchResult.Failure(message, error)
        }
    }

    private fun String.sanitizePdfFileName(): String =
        replace(Regex("[\\\\/:*?\"<>|]"), "_").take(70).ifBlank { "OpenConvert" }

    private companion object {
        const val MAX_OUTPUT_FILES = 100
    }
}
