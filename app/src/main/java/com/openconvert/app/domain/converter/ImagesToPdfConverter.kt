package com.openconvert.app.domain.converter

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.openconvert.app.domain.engine.EngineType
import com.openconvert.app.domain.model.ConversionResult
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.io.FileNotFoundException
import java.io.IOException
import kotlin.coroutines.coroutineContext
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * 图片 → PDF（清单 §8）。
 *
 * JPEG 且无 EXIF 旋转时直接把压缩流嵌入 PDF（JPEGFactory.createFromStream），
 * 不解码、不重新压缩：更快、无损、体积更小、内存更低。
 * 其他格式（PNG/WEBP/带旋转的 JPEG）走位图路径。
 */
class ImagesToPdfConverter(
    private val contentResolver: ContentResolver,
    private val onProgress: suspend (Int) -> Unit = {},
) {
    suspend fun convert(sourceUris: List<Uri>, outputUri: Uri): ConversionResult = withContext(Dispatchers.IO) {
        if (sourceUris.isEmpty()) {
            return@withContext ConversionResult.Failure("请至少选择一张图片")
        }

        val document = PDDocument()
        try {
            reportProgress(4)
            sourceUris.forEachIndexed { index, uri ->
                coroutineContext.ensureActive()
                appendPage(document, uri)
                reportProgress(8 + ((index + 1) * 76 / sourceUris.size))
            }

            contentResolver.openOutputStream(outputUri, "wt")?.use { output ->
                document.save(output)
                output.flush()
            } ?: throw FileNotFoundException("无法写入目标 PDF")

            reportProgress(96)
            val outputSize = contentResolver.openFileDescriptor(outputUri, "r")?.use {
                it.statSize.coerceAtLeast(0L)
            } ?: 0L
            reportProgress(100)
            ConversionResult.Success(outputUri.toString(), outputSize, EngineType.PDFBOX)
        } catch (cancelled: CancellationException) {
            deleteIncompleteOutput(outputUri)
            throw cancelled
        } catch (outOfMemory: OutOfMemoryError) {
            deleteIncompleteOutput(outputUri)
            ConversionResult.Failure("图片分辨率过高，无法生成 PDF", outOfMemory)
        } catch (error: Throwable) {
            deleteIncompleteOutput(outputUri)
            ConversionResult.Failure(error.toUserMessage(), error)
        } finally {
            runCatching { document.close() }
        }
    }

    private fun appendPage(document: PDDocument, uri: Uri) {
        val isJpeg = contentResolver.getType(uri)?.lowercase()?.let {
            it == "image/jpeg" || it == "image/jpg"
        } ?: false
        val orientation = readOrientation(uri)

        val image: PDImageXObject
        val rotation: Int
        if (isJpeg && orientation == ExifInterface.ORIENTATION_NORMAL) {
            image = contentResolver.openInputStream(uri)?.use {
                JPEGFactory.createFromStream(document, it)
            } ?: throw FileNotFoundException("无法读取图片")
            rotation = 0
        } else {
            val bitmap = decodeForPdf(uri)
            try {
                val oriented = applyExifOrientation(bitmap, orientation)
                image = LosslessFactory.createFromImage(document, oriented)
                if (oriented !== bitmap) oriented.recycle()
            } finally {
                bitmap.recycle()
            }
            rotation = 0
        }

        val (pageWidth, pageHeight) = pageSizeFor(image.width, image.height)
        val page = PDPage(PDRectangle(pageWidth, pageHeight))
        document.addPage(page)

        PDPageContentStream(document, page).use { content ->
            content.setNonStrokingColor(255, 255, 255)
            content.fillRect(0f, 0f, pageWidth, pageHeight)

            val availableWidth = pageWidth - PAGE_MARGIN * 2
            val availableHeight = pageHeight - PAGE_MARGIN * 2
            val scale = min(availableWidth / image.width, availableHeight / image.height)
            val drawWidth = image.width * scale
            val drawHeight = image.height * scale
            val left = (pageWidth - drawWidth) / 2
            val bottom = (pageHeight - drawHeight) / 2
            content.drawImage(image, left, bottom, drawWidth, drawHeight)
        }
    }

    private fun pageSizeFor(imageWidth: Int, imageHeight: Int): Pair<Float, Float> {
        val landscape = imageWidth > imageHeight
        val short = if (landscape) A4_LONG_EDGE else A4_SHORT_EDGE
        val long = if (landscape) A4_SHORT_EDGE else A4_LONG_EDGE
        return short to long
    }

    private fun readOrientation(uri: Uri): Int = runCatching {
        contentResolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        } ?: ExifInterface.ORIENTATION_NORMAL
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    private fun decodeForPdf(uri: Uri): Bitmap {
        val bounds = BitmapFactory.Options()
        bounds.inJustDecodeBounds = true
        val boundsInput = contentResolver.openInputStream(uri)
            ?: throw FileNotFoundException("无法读取图片")
        boundsInput.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IOException("所选文件不是有效图片或已经损坏")
        }

        var sampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > MAX_IMAGE_EDGE) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decodeInput = contentResolver.openInputStream(uri)
            ?: throw FileNotFoundException("无法读取图片")
        return decodeInput.use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IOException("图片解码失败")
    }

    private fun applyExifOrientation(source: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(-90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
            }
        }
        if (matrix.isIdentity) return source
        val result = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        return result
    }

    private suspend fun reportProgress(progress: Int) {
        coroutineContext.ensureActive()
        onProgress(progress.coerceIn(0, 100))
    }

    private fun deleteIncompleteOutput(uri: Uri) {
        runCatching { contentResolver.delete(uri, null, null) }
    }

    private fun Throwable.toUserMessage(): String = when (this) {
        is SecurityException -> "没有读取图片或保存 PDF 的权限"
        is FileNotFoundException -> message ?: "找不到图片或保存位置"
        is IOException -> message ?: "PDF 生成失败"
        else -> "PDF 生成失败，请更换图片后重试"
    }

    private companion object {
        const val A4_SHORT_EDGE = 595f
        const val A4_LONG_EDGE = 842f
        const val PAGE_MARGIN = 28f
        const val MAX_IMAGE_EDGE = 3000
    }
}
