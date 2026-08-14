package com.openconvert.app.domain.converter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.openconvert.app.domain.model.FileFormat
import java.io.FileNotFoundException
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlin.math.min
import kotlin.math.roundToInt

class PdfToImagesConverter(
    private val context: Context,
    private val onProgress: suspend (Int) -> Unit = {},
) {
    private val resolver = context.contentResolver

    suspend fun convert(
        inputUri: Uri,
        outputTreeUri: Uri,
        pages: List<Int>,
        targetFormat: FileFormat,
        sourceName: String,
        jpegQuality: Int = 92,
    ): PdfBatchResult = withContext(Dispatchers.IO) {
        if (targetFormat !in setOf(FileFormat.JPG, FileFormat.PNG)) {
            return@withContext PdfBatchResult.Failure("PDF 只能导出为 JPG 或 PNG")
        }
        if (pages.isEmpty()) return@withContext PdfBatchResult.Failure("没有选择需要导出的页面")
        if (pages.size > MAX_OUTPUT_PAGES) {
            return@withContext PdfBatchResult.Failure("一次最多导出 $MAX_OUTPUT_PAGES 页")
        }

        val outputDirectory = DocumentFile.fromTreeUri(context, outputTreeUri)
            ?: return@withContext PdfBatchResult.Failure("无法访问所选文件夹")
        val created = mutableListOf<DocumentFile>()
        var totalBytes = 0L
        try {
            resolver.openFileDescriptor(inputUri, "r")?.use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    require(renderer.pageCount > 0) { "PDF 没有可导出的页面" }
                    require(pages.all { it in 1..renderer.pageCount }) { "选择的页码超出 PDF 范围" }
                    val digits = renderer.pageCount.toString().length.coerceAtLeast(2)
                    val baseName = sourceName.substringBeforeLast('.').sanitizeFileName()
                    pages.forEachIndexed { index, pageNumber ->
                        currentCoroutineContext().ensureActive()
                        renderer.openPage(pageNumber - 1).use { page ->
                            val scale = min(RENDER_SCALE, MAX_BITMAP_EDGE.toFloat() / maxOf(page.width, page.height))
                            val width = (page.width * scale).roundToInt().coerceAtLeast(1)
                            val height = (page.height * scale).roundToInt().coerceAtLeast(1)
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            try {
                                bitmap.eraseColor(Color.WHITE)
                                val transform = Matrix().apply { postScale(scale, scale) }
                                page.render(bitmap, null, transform, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                val extension = if (targetFormat == FileFormat.JPG) "jpg" else "png"
                                val name = "${baseName}_page_${pageNumber.toString().padStart(digits, '0')}.$extension"
                                val output = outputDirectory.createFile(targetFormat.mimeType, name)
                                    ?: throw IOException("无法创建输出图片")
                                created += output
                                resolver.openOutputStream(output.uri, "w")?.use { stream ->
                                    val format = if (targetFormat == FileFormat.JPG) {
                                        Bitmap.CompressFormat.JPEG
                                    } else {
                                        Bitmap.CompressFormat.PNG
                                    }
                                    check(bitmap.compress(format, jpegQuality, stream)) { "图片编码失败" }
                                } ?: throw FileNotFoundException("无法写入输出图片")
                                totalBytes += output.length().coerceAtLeast(0)
                            } finally {
                                bitmap.recycle()
                            }
                        }
                        onProgress(((index + 1) * 100 / pages.size).coerceIn(1, 100))
                    }
                }
            } ?: throw FileNotFoundException("无法读取 PDF")
            PdfBatchResult.Success(created.map { it.uri.toString() }, totalBytes)
        } catch (cancelled: CancellationException) {
            created.forEach { runCatching { it.delete() } }
            PdfBatchResult.Cancelled
        } catch (error: Throwable) {
            created.forEach { runCatching { it.delete() } }
            PdfBatchResult.Failure(error.toUserMessage("PDF 转图片失败"), error)
        }
    }

    private fun String.sanitizeFileName(): String =
        replace(Regex("[\\\\/:*?\"<>|]"), "_").take(80).ifBlank { "OpenConvert" }

    private companion object {
        const val RENDER_SCALE = 2f
        const val MAX_BITMAP_EDGE = 3000
        const val MAX_OUTPUT_PAGES = 200
    }
}

internal fun Throwable.toUserMessage(fallback: String): String = when (this) {
    is SecurityException -> "没有读取 PDF 或写入目标位置的权限"
    is OutOfMemoryError -> "PDF 页面过大，设备内存不足"
    is IllegalArgumentException -> message ?: fallback
    is IOException -> message ?: fallback
    else -> fallback
}
