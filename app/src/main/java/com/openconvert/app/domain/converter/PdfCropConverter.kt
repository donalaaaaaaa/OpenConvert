package com.openconvert.app.domain.converter

import android.content.Context
import android.net.Uri
import com.openconvert.app.domain.work.BoundedIo
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException

data class PdfCropMargins(
    val leftPt: Float = 0f,
    val topPt: Float = 0f,
    val rightPt: Float = 0f,
    val bottomPt: Float = 0f,
)

/**
 * PDF 页面边距裁剪转换器（计划书 §八）。
 * 支持指定页或全量页面进行边距裁切调整。
 */
class PdfCropConverter(
    private val context: Context,
    private val onProgress: suspend (Int) -> Unit = {},
) {
    private val resolver = context.contentResolver

    suspend fun crop(
        inputUri: Uri,
        outputUri: Uri,
        margins: PdfCropMargins,
        targetPages: Set<Int>? = null, // null 表示全部页面 (1-based)
    ): Long = withContext(Dispatchers.IO) {
        val tempFile = com.openconvert.app.domain.work.TempWorkspaceManager(context).file(
            com.openconvert.app.domain.work.TempWorkspaceManager.NS_PDF,
            "pdf_crop_${System.currentTimeMillis()}.pdf",
        )
        try {
            onProgress(10)
            resolver.openInputStream(inputUri)?.use { input ->
                PDDocument.load(input).use { document ->
                    val pageCount = document.numberOfPages
                    for (i in 0 until pageCount) {
                        val pageNum = i + 1
                        if (targetPages == null || pageNum in targetPages) {
                            val page: PDPage = document.getPage(i)
                            val mediaBox = page.mediaBox
                            val newX = mediaBox.lowerLeftX + margins.leftPt
                            val newY = mediaBox.lowerLeftY + margins.bottomPt
                            val newWidth = (mediaBox.width - margins.leftPt - margins.rightPt).coerceAtLeast(50f)
                            val newHeight = (mediaBox.height - margins.topPt - margins.bottomPt).coerceAtLeast(50f)

                            val cropBox = PDRectangle(newX, newY, newWidth, newHeight)
                            page.cropBox = cropBox
                        }
                        val progress = 10 + ((i + 1) * 75 / pageCount)
                        onProgress(progress.coerceIn(10, 85))
                    }

                    onProgress(88)
                    tempFile.outputStream().use { out ->
                        document.save(out)
                    }
                }
            } ?: throw FileNotFoundException("无法读取源 PDF 文件")

            onProgress(95)
            resolver.openOutputStream(outputUri, "wt")?.use { out ->
                tempFile.inputStream().use { input ->
                    BoundedIo.copy(input, out)
                }
                out.flush()
            } ?: throw FileNotFoundException("无法写入目标 PDF 文件")

            onProgress(100)
            tempFile.length()
        } finally {
            tempFile.delete()
        }
    }
}
