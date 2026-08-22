package com.openconvert.app.domain.converter

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.openconvert.app.domain.model.ConversionResult
import com.openconvert.app.domain.model.FileFormat
import com.openconvert.app.domain.work.StorageGuard
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 生产转换路径上的大文件，不是纯 BoundedIo 拷贝。
 * 4GB 图片会 OOM，因此图片走可解码的生成 JPEG；4GB 走单文件 GZIP 流式压缩。
 */
@RunWith(AndroidJUnit4::class)
class LargeFileConversionInstrumentedTest {

    @Test
    fun compresses100MbFileToZip() = compressZeros(100L * 1024 * 1024, FileFormat.ZIP, "application/zip")

    @Test
    fun compresses4GbFileToGzip() = compressZeros(4L * 1024 * 1024 * 1024, FileFormat.GZIP, "application/gzip")

    @Test
    fun convertsGeneratedJpegToPng() = runBlocking(Dispatchers.IO) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver
        val testId = UUID.randomUUID().toString()
        val source = requireNotNull(
            resolver.insert(
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "oc-large-$testId.jpg")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/OpenConvertTest")
                },
            ),
        )
        val output = requireNotNull(
            resolver.insert(
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "oc-large-$testId.png")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/OpenConvertTest")
                },
            ),
        )
        try {
            val bitmap = Bitmap.createBitmap(2048, 2048, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.rgb(40, 120, 200))
            }
            resolver.openOutputStream(source, "w")!!.use {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it))
            }
            bitmap.recycle()
            val task = com.openconvert.app.domain.model.ConversionTask(
                id = UUID.randomUUID().toString(),
                sourceUri = source.toString(),
                sourceName = "oc-large-$testId.jpg",
                sourceFormat = FileFormat.JPG,
                targetFormat = FileFormat.PNG,
                outputUri = output.toString(),
            )
            val result = ImageConverter(resolver).convert(task)
            assertTrue("image convert failed: $result", result is ConversionResult.Success)
        } finally {
            runCatching { resolver.delete(source, null, null) }
            runCatching { resolver.delete(output, null, null) }
        }
    }

    private fun compressZeros(size: Long, format: FileFormat, mime: String) = runBlocking(Dispatchers.IO) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver
        val usable = context.cacheDir.usableSpace
        val required = size + StorageGuard.SAFETY_MARGIN_BYTES + 32L * 1024 * 1024
        assumeTrue("usable=$usable required=$required", StorageGuard.hasEnoughSpace(usable, required))

        val testId = UUID.randomUUID().toString()
        val ext = format.preferredExtension
        val input = requireNotNull(
            resolver.insert(
                MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, "oc-zeros-$testId.bin")
                    put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                    put(MediaStore.Downloads.RELATIVE_PATH, "Download/OpenConvertTest")
                    put(MediaStore.Downloads.IS_PENDING, 0)
                },
            ),
        )
        val output = requireNotNull(
            resolver.insert(
                MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, "oc-zeros-$testId.$ext")
                    put(MediaStore.Downloads.MIME_TYPE, mime)
                    put(MediaStore.Downloads.RELATIVE_PATH, "Download/OpenConvertTest")
                    put(MediaStore.Downloads.IS_PENDING, 0)
                },
            ),
        )
        try {
            resolver.openOutputStream(input, "w")!!.use { stream ->
                val buf = ByteArray(1024 * 1024)
                var left = size
                while (left > 0) {
                    val n = minOf(buf.size.toLong(), left).toInt()
                    stream.write(buf, 0, n)
                    left -= n
                }
            }
            val result = ArchiveConverter(context).compress(
                inputUris = listOf(input),
                inputNames = listOf("zeros.bin"),
                outputUri = output,
                targetFormat = format,
            )
            assertTrue("$format compress failed: $result", result is ConversionResult.Success)
            val outSize = resolver.openFileDescriptor(output, "r")?.use { it.statSize } ?: 0L
            assertTrue("output too small: $outSize", outSize > 0L)
        } finally {
            runCatching { resolver.delete(input, null, null) }
            runCatching { resolver.delete(output, null, null) }
        }
    }
}
