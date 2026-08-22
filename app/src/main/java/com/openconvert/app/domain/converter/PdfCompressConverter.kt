package com.openconvert.app.domain.converter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.openconvert.app.domain.work.BoundedIo
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import kotlin.math.min
import kotlin.math.roundToInt

enum class PdfCompressPreset(val displayName: String, val maxDpi: Int, val quality: Float) {
    HIGH("高质量", 300, 0.90f),
    BALANCED("平衡推荐", 200, 0.80f),
    SMALL("小体积", 150, 0.68f),
}

data class PdfCompressResult(
    val success: Boolean,
    val originalSizeBytes: Long,
    val outputSizeBytes: Long,
    val reductionPercent: Double,
    val message: String = "",
)

/**
 * PDF 智能压缩转换器（计划书 §七）。
 * 遍历 PDF 页面 XObject 图像资源流，针对高分辨率图片进行降采样与 JPEG 重新编码压缩。
 */
class PdfCompressConverter(
    private val context: Context,
    private val onProgress: suspend (Int) -> Unit = {},
) {
    private val resolver = context.contentResolver

    suspend fun compress(
        inputUri: Uri,
        outputUri: Uri,
        preset: PdfCompressPreset = PdfCompressPreset.BALANCED,
        customDpi: Int? = null,
        customQuality: Float? = null,
    ): PdfCompressResult = withContext(Dispatchers.IO) {
        val targetDpi = customDpi ?: preset.maxDpi
        val quality = (customQuality ?: preset.quality).coerceIn(0.1f, 1.0f)

        val tempFile = com.openconvert.app.domain.work.TempWorkspaceManager(context).file(
            com.openconvert.app.domain.work.TempWorkspaceManager.NS_PDF,
            "pdf_compress_${System.currentTimeMillis()}.pdf",
        )
        var originalSize = 0L

        try {
            onProgress(5)
            resolver.openFileDescriptor(inputUri, "r")?.use { pfd ->
                originalSize = pfd.statSize
            }

            resolver.openInputStream(inputUri)?.use { inputStream ->
                PDDocument.load(inputStream).use { document ->
                    val pageCount = document.numberOfPages
                    var processedImages = 0

                    for (pageIdx in 0 until pageCount) {
                        val page: PDPage = document.getPage(pageIdx)
                        val resources: PDResources? = page.resources
                        if (resources != null) {
                            val xObjectNames = resources.xObjectNames
                            for (name in xObjectNames) {
                                if (resources.isImageXObject(name)) {
                                    val imageXObject = resources.getXObject(name) as? PDImageXObject ?: continue
                                    val bitmap = imageXObject.image ?: continue

                                    // 计算降采样尺寸
                                    val maxAllowedDim = (targetDpi * 11.7).toInt().coerceIn(800, 3600) // A4 对应尺寸
                                    val scale = min(1.0f, maxAllowedDim.toFloat() / maxOf(bitmap.width, bitmap.height))

                                    val targetW = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
                                    val targetH = (bitmap.height * scale).roundToInt().coerceAtLeast(1)

                                    val scaledBitmap = if (targetW != bitmap.width || targetH != bitmap.height) {
                                        Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
                                    } else {
                                        bitmap
                                    }

                                    val compressedXObject = JPEGFactory.createFromImage(
                                        document,
                                        scaledBitmap,
                                        quality,
                                    )

                                    resources.put(name, compressedXObject)
                                    processedImages++
                                }
                            }
                        }
                        val progress = 5 + ((pageIdx + 1) * 80 / pageCount)
                        onProgress(progress.coerceIn(5, 85))
                    }

                    onProgress(88)
                    tempFile.outputStream().use { out ->
                        document.save(out)
                    }
                }
            } ?: throw FileNotFoundException("无法读取源 PDF 文件")

            onProgress(94)
            resolver.openOutputStream(outputUri, "wt")?.use { out ->
                tempFile.inputStream().use { input ->
                    BoundedIo.copy(input, out)
                }
                out.flush()
            } ?: throw FileNotFoundException("无法写入目标 PDF 文件")

            val outputSize = tempFile.length()
            val reduction = if (originalSize > 0) {
                ((originalSize - outputSize).toDouble() / originalSize.toDouble()) * 100.0
            } else {
                0.0
            }

            onProgress(100)
            val msg = if (reduction < 3.0) {
                "当前 PDF 已经具有较高压缩率或主要包含矢量文本"
            } else {
                "压缩完成，文件体积减少 ${String.format("%.1f", reduction)}%"
            }

            PdfCompressResult(
                success = true,
                originalSizeBytes = originalSize,
                outputSizeBytes = outputSize,
                reductionPercent = reduction.coerceAtLeast(0.0),
                message = msg,
            )
        } finally {
            tempFile.delete()
        }
    }
}
