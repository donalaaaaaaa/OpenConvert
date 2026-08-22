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
import com.openconvert.app.domain.engine.EngineType
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

            // Primary engine: libvips (SIMD, low-memory, high quality).
            // Any failure falls back to the BitmapFactory path below.
            // stripMetadata 时强制走 Bitmap 路径：Bitmap.compress 不写 EXIF，天然实现"删除全部元数据"。
            if (vipsCanConvert(task.sourceFormat, task.targetFormat) && !task.payload.stripMetadata) {
                val vipsResult = runCatching {
                    convertWithVips(task, sourceUri, outputUri)
                }
                vipsResult.fold(
                    onSuccess = { return@withContext it },
                    onFailure = {
                        android.util.Log.w("OpenConvert", "libvips path failed, falling back", it)
                    },
                )
            }

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
            bitmap = applyPresetSize(bitmap, task)
            bitmap = applyAdvancedEdits(bitmap, task)
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
            ConversionResult.Success(outputUri.toString(), outputSize, EngineType.BITMAP_FACTORY)
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

    private fun vipsCanConvert(source: FileFormat, target: FileFormat): Boolean =
        VipsNative.isAvailable &&
            source in setOf(FileFormat.JPG, FileFormat.PNG, FileFormat.WEBP) &&
            target in setOf(FileFormat.JPG, FileFormat.PNG, FileFormat.WEBP)

    /**
     * 预设尺寸约束 → 目标像素（计划书 §8.1）。
     * 固定尺寸 > 最长边；只缩小不放大。返回 null 表示交给百分比缩放。
     */
    private fun presetTargetSize(
        task: ConversionTask,
        baseWidth: Int,
        baseHeight: Int,
    ): Pair<Int, Int>? {
        val fw = task.payload.fixedWidthPx
        val fh = task.payload.fixedHeightPx
        if (fw != null && fh != null && fw > 0 && fh > 0) return fw to fh

        val limit = task.payload.longestEdgePx ?: return null
        if (limit <= 0) return null
        val longest = maxOf(baseWidth, baseHeight)
        if (longest <= limit) return null // 已经够小，不放大
        val scale = limit.toDouble() / longest
        return (baseWidth * scale).toInt().coerceAtLeast(1) to
            (baseHeight * scale).toInt().coerceAtLeast(1)
    }

    private fun vipsExtension(target: FileFormat): String = when (target) {
        FileFormat.JPG -> "jpg"
        FileFormat.PNG -> "png"
        FileFormat.WEBP -> "webp"
        else -> throw IOException("目标格式不是图片")
    }

    /** Bounds after EXIF orientation, mirroring the bitmap path. */
    private fun readOrientedBounds(uri: Uri): Pair<Int, Int> {
        val (w, h) = readBounds(uri)
        val orientation = runCatching {
            contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val swapped = orientation in setOf(
            ExifInterface.ORIENTATION_ROTATE_90,
            ExifInterface.ORIENTATION_ROTATE_270,
            ExifInterface.ORIENTATION_TRANSPOSE,
            ExifInterface.ORIENTATION_TRANSVERSE,
        )
        return if (swapped) h to w else w to h
    }

    private suspend fun convertWithVips(task: ConversionTask, sourceUri: Uri, outputUri: Uri): ConversionResult {
        val bytes = contentResolver.openInputStream(sourceUri)?.use { it.readBytes() }
            ?: throw FileNotFoundException("无法读取源文件")
        if (bytes.isEmpty()) throw IOException("源文件为空")

        val (ow, oh) = readOrientedBounds(sourceUri)
        // 旋转 90/270 后宽高互换，先算目标比例再决定输出尺寸。
        val rotated = task.payload.rotateDegrees % 180 != 0
        val baseWidth = if (rotated) oh else ow
        val baseHeight = if (rotated) ow else oh
        // 预设的尺寸约束（§8.1「最长边 1920」「1024×1024」）优先于百分比缩放。
        val presetSize = presetTargetSize(task, baseWidth, baseHeight)
        var targetWidth = presetSize?.first
            ?: (baseWidth * task.resolution.scalePercent / 100f).toInt().coerceAtLeast(1)
        var targetHeight = presetSize?.second
            ?: (baseHeight * task.resolution.scalePercent / 100f).toInt().coerceAtLeast(1)

        // 裁剪比例：cover-crop 模式（mode=1）按目标比例居中裁剪。
        val aspect = ImageEditMath.parseAspectRatio(task.payload.cropAspect)
        var mode = 0
        if (aspect != null) {
            mode = 1
            val (aw, ah) = ImageEditMath.coverCropSize(targetWidth, targetHeight, aspect)
            targetWidth = aw
            targetHeight = ah
        }

        reportProgress(30)
        // autorotate applies EXIF orientation inside libvips (same result as the bitmap path).
        val out = VipsNative.convertBuffer(
            bytes,
            targetWidth,
            targetHeight,
            mode = mode,
            fmt = vipsExtension(task.targetFormat),
            quality = task.quality.compressionQuality,
            rotate = ImageEditMath.rotateCode(task.payload.rotateDegrees),
            flip = task.payload.flip,
        )
        reportProgress(85)

        contentResolver.openOutputStream(outputUri, "wt")?.use { stream ->
            stream.write(out)
            stream.flush()
        } ?: throw FileNotFoundException("无法写入目标文件")

        reportProgress(100)
        return ConversionResult.Success(outputUri.toString(), out.size.toLong(), EngineType.LIBVIPS)
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

    /**
     * 预设尺寸约束在 Bitmap 兜底路径上同样生效（§8.1）。
     * libvips 不可用或 stripMetadata 强制走位图时会经过这里。
     */
    private fun applyPresetSize(source: Bitmap, task: ConversionTask): Bitmap {
        val target = presetTargetSize(task, source.width, source.height) ?: return source
        val (tw, th) = target
        if (source.width == tw && source.height == th) return source
        val scaled = Bitmap.createScaledBitmap(source, tw, th, true)
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

    /**
     * 图片高级编辑（Bitmap 路径）：旋转 / 翻转 / 裁剪比例。
     * libvips 主引擎失败回退时同样生效。
     */
    private fun applyAdvancedEdits(source: Bitmap, task: ConversionTask): Bitmap {
        var current = source
        val rotateDegrees = task.payload.rotateDegrees % 360
        val flip = task.payload.flip
        val aspect = ImageEditMath.parseAspectRatio(task.payload.cropAspect)

        val needsRotate = rotateDegrees != 0
        val needsFlip = flip != 0
        val needsCrop = aspect != null
        if (!needsRotate && !needsFlip && !needsCrop) return current

        val matrix = Matrix()
        if (needsRotate) matrix.postRotate(rotateDegrees.toFloat())
        if (flip == 1) matrix.postScale(-1f, 1f)
        if (flip == 2) matrix.postScale(1f, -1f)

        if (needsRotate || needsFlip) {
            val transformed = Bitmap.createBitmap(
                current, 0, 0, current.width, current.height, matrix, true,
            )
            if (transformed !== current) {
                current.recycle()
                current = transformed
            }
        }

        if (needsCrop) {
            val (cw, ch) = ImageEditMath.coverCropSize(current.width, current.height, aspect)
            if (cw != current.width || ch != current.height) {
                val left = (current.width - cw) / 2
                val top = (current.height - ch) / 2
                val cropped = Bitmap.createBitmap(current, left, top, cw, ch)
                if (cropped !== current) {
                    current.recycle()
                    current = cropped
                }
            }
        }
        return current
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
