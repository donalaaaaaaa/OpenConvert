package com.openconvert.app.domain.converter

import android.content.Context
import android.net.Uri
import com.openconvert.app.domain.model.ConversionResult
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.FileNotFoundException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * PDF 删除页面：移除用户勾选的页面后生成新 PDF。
 * 原 PDF 保持不变，输出写入新 URI。
 */
class PdfDeletePagesConverter(
    private val context: Context,
    private val onProgress: suspend (Int) -> Unit = {},
) {
    private val resolver = context.contentResolver

    suspend fun convert(
        inputUri: Uri,
        outputUri: Uri,
        pagesToDelete: List<Int>,
        sourceName: String,
    ): ConversionResult = withContext(Dispatchers.IO) {
        if (pagesToDelete.isEmpty()) {
            return@withContext ConversionResult.Failure("没有选择需要删除的页面")
        }
        try {
            resolver.openInputStream(inputUri)?.use { input ->
                PDDocument.load(input).use { source ->
                    val pageCount = source.numberOfPages
                    require(pageCount > 0) { "PDF 没有可处理的页面" }
                    val toDelete = pagesToDelete
                        .map { it - 1 } // 转 0-based
                        .filter { it in 0 until pageCount }
                        .distinct()
                        .sortedDescending() // 从后往前删，避免索引偏移
                    if (toDelete.isEmpty()) {
                        return@withContext ConversionResult.Failure("要删除的页码超出范围 1-$pageCount")
                    }
                    if (toDelete.size >= pageCount) {
                        return@withContext ConversionResult.Failure("不能删除全部页面，至少保留一页")
                    }
                    toDelete.forEachIndexed { index, pageIndex ->
                        currentCoroutineContext().ensureActive()
                        source.removePage(pageIndex)
                        onProgress(((index + 1) * 90 / toDelete.size).coerceAtLeast(1))
                    }
                    resolver.openOutputStream(outputUri, "w")?.use { output ->
                        source.save(output)
                    } ?: throw FileNotFoundException("无法写入输出 PDF")
                }
            } ?: throw FileNotFoundException("无法读取 PDF")
            onProgress(100)
            val outputSize = resolver.openFileDescriptor(outputUri, "r")?.use { it.statSize } ?: 0L
            ConversionResult.Success(outputUri.toString(), outputSize.coerceAtLeast(0))
        } catch (cancelled: CancellationException) {
            runCatching { resolver.delete(outputUri, null, null) }
            ConversionResult.Cancelled
        } catch (error: Throwable) {
            runCatching { resolver.delete(outputUri, null, null) }
            val message = if (error.javaClass.simpleName.contains("InvalidPassword")) {
                "暂不支持带密码的 PDF"
            } else {
                error.toUserMessage("PDF 删除页面失败，请检查文件是否损坏")
            }
            ConversionResult.Failure(message, error)
        }
    }

    /** 供 ViewModel 校验：删除后剩余页数。 */
    fun remainingPages(totalPages: Int, pagesToDelete: List<Int>): Int {
        val toDelete = pagesToDelete.map { it - 1 }.filter { it in 0 until totalPages }.distinct().size
        return totalPages - toDelete
    }
}
