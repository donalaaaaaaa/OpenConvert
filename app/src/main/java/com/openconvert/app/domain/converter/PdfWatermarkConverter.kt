package com.openconvert.app.domain.converter

import android.content.Context
import android.net.Uri
import com.openconvert.app.domain.work.BoundedIo
import com.openconvert.app.domain.work.TempWorkspaceManager
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.util.Matrix
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 文本水印。每页叠一层半透明字，默认斜向居中。
 * 有中文时尽量用系统 CJK 字体，找不到再回退 Helvetica。
 */
class PdfWatermarkConverter(
    private val context: Context,
    private val onProgress: suspend (Int) -> Unit = {},
) {
    private val resolver = context.contentResolver

    suspend fun apply(
        inputUri: Uri,
        outputUri: Uri,
        text: String,
        opacity: Float = 0.18f,
        position: PdfWatermarkPosition = PdfWatermarkPosition.DIAGONAL,
    ): Long = withContext(Dispatchers.IO) {
        val watermark = text.trim()
        require(watermark.isNotEmpty()) { "请输入水印文字" }
        val alpha = opacity.coerceIn(0.05f, 0.6f)
        val tempFile = TempWorkspaceManager(context).file(
            TempWorkspaceManager.NS_PDF,
            "pdf_wm_${System.currentTimeMillis()}.pdf",
        )
        try {
            onProgress(10)
            resolver.openInputStream(inputUri)?.use { input ->
                PDDocument.load(input).use { document ->
                    val font = loadFont(document, watermark)
                    val pageCount = document.numberOfPages.coerceAtLeast(1)
                    for (index in 0 until document.numberOfPages) {
                        val page = document.getPage(index)
                        val box = page.cropBox ?: page.mediaBox
                        val placement = PdfWatermarkLayout.place(
                            pageWidth = box.width,
                            pageHeight = box.height,
                            position = position,
                            textLength = watermark.length,
                        )
                        PDPageContentStream(
                            document,
                            page,
                            PDPageContentStream.AppendMode.APPEND,
                            true,
                            true,
                        ).use { stream ->
                            val gs = PDExtendedGraphicsState()
                            gs.nonStrokingAlphaConstant = alpha
                            stream.setGraphicsStateParameters(gs)
                            stream.beginText()
                            stream.setFont(font, placement.fontSize)
                            stream.setNonStrokingColor(0.45f, 0.45f, 0.45f)
                            stream.setTextMatrix(
                                Matrix.getRotateInstance(
                                    Math.toRadians(placement.rotationDeg.toDouble()),
                                    box.lowerLeftX + placement.x,
                                    box.lowerLeftY + placement.y,
                                ),
                            )
                            stream.showText(watermark)
                            stream.endText()
                        }
                        onProgress((10 + ((index + 1) * 75 / pageCount)).coerceIn(10, 85))
                    }
                    onProgress(90)
                    tempFile.outputStream().use { out -> document.save(out) }
                }
            } ?: throw FileNotFoundException("无法读取源 PDF 文件")

            resolver.openOutputStream(outputUri, "wt")?.use { out ->
                tempFile.inputStream().use { input -> BoundedIo.copy(input, out) }
                out.flush()
            } ?: throw FileNotFoundException("无法写入目标 PDF 文件")
            onProgress(100)
            tempFile.length()
        } finally {
            tempFile.delete()
        }
    }

    private fun loadFont(document: PDDocument, text: String): PDFont {
        val needsCjk = text.any { it.code > 0x7F }
        if (needsCjk) {
            CJK_FONT_CANDIDATES.forEach { path ->
                val file = File(path)
                if (file.isFile) {
                    runCatching {
                        return FileInputStream(file).use { PDType0Font.load(document, it, true) }
                    }
                }
            }
        }
        return PDType1Font.HELVETICA
    }

    companion object {
        private val CJK_FONT_CANDIDATES = listOf(
            "/system/fonts/NotoSansCJK-Regular.ttc",
            "/system/fonts/NotoSansSC-Regular.otf",
            "/system/fonts/NotoSansCJKsc-Regular.otf",
            "/system/fonts/DroidSansFallback.ttf",
        )
    }
}
