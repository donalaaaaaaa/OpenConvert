package com.openconvert.app.domain.converter

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import com.openconvert.app.domain.model.ConversionResult
import com.openconvert.app.domain.model.ConversionTask
import com.openconvert.app.domain.model.FileCategory
import com.openconvert.app.domain.model.FileFormat
import java.io.FileNotFoundException
import java.io.IOException
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class ImageConverter(
    private val contentResolver: ContentResolver,
    private val onProgress: suspend (Int) -> Unit = {},
) : Converter {

    override fun supports(inputFormat: FileFormat, outputFormat: FileFormat): Boolean =
        inputFormat.category == FileCategory.IMAGE &&
            outputFormat.category == FileCategory.IMAGE &&
            inputFormat != outputFormat

    override suspend fun convert(task: ConversionTask): ConversionResult = withContext(Dispatchers.IO) {
        val outputUri = task.outputUri?.let(Uri::parse)
            ?: return@withContext ConversionResult.Failure("没有选择输出文件")

        if (!supports(task.sourceFormat, task.targetFormat)) {
            return@withContext ConversionResult.Failure(
                "暂不支持 ${task.sourceFormat.displayName} → ${task.targetFormat.displayName}",
            )
        }

        try {
            reportProgress(8)
            val sourceUri = Uri.parse(task.sourceUri)
            val bounds = readBounds(sourceUri)
            val sampleSize = calculateSampleSize(
                width = bounds.first,
                height = bounds.second,
                scalePercent = task.resolution.scalePercent,
            )

            reportProgress(24)
            var bitmap = decodeBitmap(sourceUri, sampleSize)
            bitmap = applyExifOrientation(sourceUri, bitmap)

            reportProgress(48)
            bitmap = scaleBitmap(bitmap, bounds.first, bounds.second, task.resolution.scalePercent)
            bitmap = flattenForJpegIfNeeded(bitmap, task.targetFormat)

            reportProgress(68)
            writeBitmap(
                bitmap = bitmap,
                outputUri = outputUri,
                targetFormat = task.targetFormat,
                quality = task.quality.compressionQuality,
            )
            bitmap.recycle()

            reportProgress(96)
            val outputSize = contentResolver.openFileDescriptor(outputUri, "r")?.use { descriptor ->
                descriptor.statSize.coerceAtLeast(0L)
            } ?: 0L
            reportProgress(100)
            ConversionResult.Success(outputUri.toString(), outputSize)
        } catch (cancelled: CancellationException) {
            deleteIncompleteOutput(outputUri)
            throw cancelled
        } catch (outOfMemory: OutOfMemoryError) {
            deleteIncompleteOutput(outputUri)
            ConversionResult.Failure("图片分辨率过高，请选择 75% 或 50% 尺寸后重试", outOfMemory)
        } catch (error: Throwable) {
            deleteIncompleteOutput(outputUri)
            ConversionResult.Failure(error.toUserMessage(), error)
        }
    }

    private suspend fun reportProgress(progress: Int) {
        coroutineContext.ensureActive()
        onProgress(progress)
    }

    private fun readBounds(uri: Uri): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val input = contentResolver.openInputStream(uri)
            ?: throw FileNotFoundException("无法读取源文件")
        input.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }

        if (options.outWidth <= 0 || options.outHeight <= 0) {
            throw IOException("文件不是有效图片或已经损坏")
        }
        return options.outWidth to options.outHeight
    }

    private fun calculateSampleSize(width: Int, height: Int, scalePercent: Int): Int {
        var sampleSize = 1
        if (scalePercent <= 50) sampleSize = 2

        val decodedPixels = (width.toLong() / sampleSize) * (height.toLong() / sampleSize)
        if (decodedPixels > MAX_DECODE_PIXELS) {
            throw IOException("图片分辨率过高，请选择更小的输出尺寸")
        }
        return sampleSize
    }

    private fun decodeBitmap(uri: Uri, sampleSize: Int): Bitmap {
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        } ?: throw IOException("图片解码失败")
    }

    private fun applyExifOrientation(uri: Uri, source: Bitmap): Bitmap {
        val orientation = runCatching {
            contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    setRotate(90f)
                    postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    setRotate(-90f)
                    postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
            }
        }

        if (matrix.isIdentity) return source
        val oriented = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        if (oriented !== source) source.recycle()
        return oriented
    }

    private fun scaleBitmap(source: Bitmap, sourceWidth: Int, sourceHeight: Int, scalePercent: Int): Bitmap {
        if (scalePercent == 100) return source

        val orientationSwapped = (source.width > source.height) != (sourceWidth > sourceHeight)
        val orientedWidth = if (orientationSwapped) sourceHeight else sourceWidth
        val orientedHeight = if (orientationSwapped) sourceWidth else sourceHeight
        val targetWidth = (orientedWidth * scalePercent / 100f).toInt().coerceAtLeast(1)
        val targetHeight = (orientedHeight * scalePercent / 100f).toInt().coerceAtLeast(1)

        if (source.width == targetWidth && source.height == targetHeight) return source
        val scaled = Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
        if (scaled !== source) source.recycle()
        return scaled
    }

    private fun flattenForJpegIfNeeded(source: Bitmap, targetFormat: FileFormat): Bitmap {
        if (targetFormat != FileFormat.JPG || !source.hasAlpha()) return source

        val flattened = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        Canvas(flattened).apply {
            drawColor(Color.WHITE)
            drawBitmap(source, 0f, 0f, null)
        }
        source.recycle()
        return flattened
    }

    private fun writeBitmap(
        bitmap: Bitmap,
        outputUri: Uri,
        targetFormat: FileFormat,
        quality: Int,
    ) {
        val compressFormat = when (targetFormat) {
            FileFormat.JPG -> Bitmap.CompressFormat.JPEG
            FileFormat.PNG -> Bitmap.CompressFormat.PNG
            FileFormat.WEBP -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
            else -> throw IOException("目标格式不是图片")
        }

        contentResolver.openOutputStream(outputUri, "wt")?.use { output ->
            if (!bitmap.compress(compressFormat, quality, output)) {
                throw IOException("图片编码失败")
            }
            output.flush()
        } ?: throw FileNotFoundException("无法写入目标文件")
    }

    private fun Throwable.toUserMessage(): String = when (this) {
        is SecurityException -> "没有读取或保存此文件的权限"
        is FileNotFoundException -> message ?: "找不到源文件或保存位置"
        is IOException -> message ?: "图片转换失败"
        else -> "图片转换失败，请更换文件后重试"
    }

    private fun deleteIncompleteOutput(uri: Uri) {
        runCatching {
            if (uri.scheme == ContentResolver.SCHEME_FILE) {
                uri.path?.let { java.io.File(it).delete() }
            } else {
                contentResolver.delete(uri, null, null)
            }
        }
    }

    private companion object {
        const val MAX_DECODE_PIXELS = 48_000_000L
    }
}
