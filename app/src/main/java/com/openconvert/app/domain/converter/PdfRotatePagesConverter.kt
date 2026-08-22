package com.openconvert.app.domain.converter

import android.content.Context
import android.net.Uri
import com.openconvert.app.domain.engine.EngineType
import com.openconvert.app.domain.model.ConversionResult
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.FileNotFoundException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * PDF 页面旋转：将全部页面（或指定页面）旋转 90°/180°/270°。
 * 输出写入新 URI，原 PDF 不变。
 */
class PdfRotatePagesConverter(
    private val context: Context,
    private val onProgress: suspend (Int) -> Unit = {},
) {
    private val resolver = context.contentResolver

    suspend fun convert(
        inputUri: Uri,
        outputUri: Uri,
        degrees: Int,
        pages: List<Int>? = null, // null = 全部页面；否则 1-based 页面列表
    ): ConversionResult = withContext(Dispatchers.IO) {
        if (degrees % 90 != 0 || degrees !in setOf(90, 180, 270)) {
            return@withContext ConversionResult.Failure("仅支持旋转 90°、180° 或 270°")
        }
        try {
            resolver.openInputStream(inputUri)?.use { input ->
                PDDocument.load(input).use { source ->
                    val pageCount = source.numberOfPages
                    require(pageCount > 0) { "PDF 没有可处理的页面" }
                    val targetPages = if (pages.isNullOrEmpty()) {
                        (0 until pageCount).toList()
                    } else {
                        pages.map { it - 1 }.filter { it in 0 until pageCount }
                    }
                    if (targetPages.isEmpty()) {
                        return@withContext ConversionResult.Failure("要旋转的页码超出范围 1-$pageCount")
                    }
                    targetPages.forEachIndexed { index, pageIndex ->
                        currentCoroutineContext().ensureActive()
                        val page = source.getPage(pageIndex)
                        val current = page.rotation % 360
                        page.rotation = (current + degrees) % 360
                        onProgress(((index + 1) * 90 / targetPages.size).coerceAtLeast(1))
                    }
                    resolver.openOutputStream(outputUri, "w")?.use { output ->
                        source.save(output)
                    } ?: throw FileNotFoundException("无法写入输出 PDF")
                }
            } ?: throw FileNotFoundException("无法读取 PDF")
            onProgress(100)
            val outputSize = resolver.openFileDescriptor(outputUri, "r")?.use { it.statSize } ?: 0L
            ConversionResult.Success(
                outputUri.toString(),
                outputSize.coerceAtLeast(0),
                EngineType.PDFBOX,
            )
        } catch (cancelled: CancellationException) {
            runCatching { resolver.delete(outputUri, null, null) }
            ConversionResult.Cancelled
        } catch (error: Throwable) {
            runCatching { resolver.delete(outputUri, null, null) }
            val message = if (error.javaClass.simpleName.contains("InvalidPassword")) {
                "暂不支持带密码的 PDF"
            } else {
                error.toUserMessage("PDF 旋转失败，请检查文件是否损坏")
            }
            ConversionResult.Failure(message, error)
        }
    }
}
