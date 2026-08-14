package com.openconvert.app.domain.converter

import android.content.ContentResolver
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.openconvert.app.domain.model.ConversionPayload
import com.openconvert.app.domain.model.ConversionResult
import com.openconvert.app.domain.model.ConversionTask
import com.openconvert.app.domain.model.FileFormat
import com.openconvert.app.domain.model.QualityPreset
import com.openconvert.app.domain.model.ResolutionPreset
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 图片高级功能真机验证：旋转 / 翻转 / 裁剪 / 去元数据。
 * 使用 2x4 非对称纯色图（左半红、右半蓝），验证旋转与翻转改变像素布局。
 */
@RunWith(AndroidJUnit4::class)
class ImageAdvancedInstrumentedTest {

    @Test
    fun vipsRotate90SwapsDimensions() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver
        val testId = UUID.randomUUID().toString()
        val input = createBitmapFile(resolver, "rot-in-$testId.png", 200, 400)
        val output = createPendingDownload(resolver, "rot-out-$testId.jpg", "image/jpeg")

        try {
            val result = ImageConverter(resolver).convert(
                task(
                    input = input,
                    output = output,
                    payload = ConversionPayload(rotateDegrees = 90),
                ),
            )
            assertTrue("Expected success, got $result", result is ConversionResult.Success)
            decode(resolver, output) { bitmap ->
                assertEquals(400, bitmap.width)
                assertEquals(200, bitmap.height)
            }
        } finally {
            runCatching { resolver.delete(input, null, null) }
            runCatching { resolver.delete(output, null, null) }
        }
    }

    @Test
    fun vipsRotate180FlipsPixels() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver
        val testId = UUID.randomUUID().toString()
        val input = createHalfColorBitmapFile(resolver, "rot180-in-$testId.png")
        val output = createPendingDownload(resolver, "rot180-out-$testId.jpg", "image/jpeg")

        try {
            val result = ImageConverter(resolver).convert(
                task(
                    input = input,
                    output = output,
                    payload = ConversionPayload(rotateDegrees = 180),
                ),
            )
            assertTrue("Expected success, got $result", result is ConversionResult.Success)
            decode(resolver, output) { bitmap ->
                // 左半红右半蓝，旋转 180° 后：左上角应变成蓝色
                val leftTop = bitmap.getPixel(2, 2)
                val red = Color.red(leftTop)
                val blue = Color.blue(leftTop)
                assertTrue("expected blue at top-left after 180, got r=$red b=$blue", blue > red)
            }
        } finally {
            runCatching { resolver.delete(input, null, null) }
            runCatching { resolver.delete(output, null, null) }
        }
    }

    @Test
    fun vipsFlipHorizontalMirrorsPixels() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver
        val testId = UUID.randomUUID().toString()
        val input = createHalfColorBitmapFile(resolver, "flip-in-$testId.png")
        val output = createPendingDownload(resolver, "flip-out-$testId.jpg", "image/jpeg")

        try {
            val result = ImageConverter(resolver).convert(
                task(
                    input = input,
                    output = output,
                    payload = ConversionPayload(flip = 1),
                ),
            )
            assertTrue("Expected success, got $result", result is ConversionResult.Success)
            decode(resolver, output) { bitmap ->
                val leftTop = bitmap.getPixel(2, 2)
                val red = Color.red(leftTop)
                val blue = Color.blue(leftTop)
                assertTrue("expected blue at top-left after flip, got r=$red b=$blue", blue > red)
            }
        } finally {
            runCatching { resolver.delete(input, null, null) }
            runCatching { resolver.delete(output, null, null) }
        }
    }

    @Test
    fun cropAspectProducesSquare() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver
        val testId = UUID.randomUUID().toString()
        val input = createBitmapFile(resolver, "crop-in-$testId.png", 400, 200)
        val output = createPendingDownload(resolver, "crop-out-$testId.jpg", "image/jpeg")

        try {
            val result = ImageConverter(resolver).convert(
                task(
                    input = input,
                    output = output,
                    payload = ConversionPayload(cropAspect = "1:1"),
                ),
            )
            assertTrue("Expected success, got $result", result is ConversionResult.Success)
            decode(resolver, output) { bitmap ->
                assertEquals(200, bitmap.width)
                assertEquals(200, bitmap.height)
            }
        } finally {
            runCatching { resolver.delete(input, null, null) }
            runCatching { resolver.delete(output, null, null) }
        }
    }

    @Test
    fun stripMetadataFallsBackToBitmapPath() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver
        val testId = UUID.randomUUID().toString()
        val input = createBitmapFile(resolver, "strip-in-$testId.png", 120, 80)
        val output = createPendingDownload(resolver, "strip-out-$testId.jpg", "image/jpeg")

        try {
            val result = ImageConverter(resolver).convert(
                task(
                    input = input,
                    output = output,
                    payload = ConversionPayload(stripMetadata = true),
                ),
            )
            assertTrue("Expected success, got $result", result is ConversionResult.Success)
            decode(resolver, output) { bitmap ->
                assertEquals(120, bitmap.width)
                assertEquals(80, bitmap.height)
            }
        } finally {
            runCatching { resolver.delete(input, null, null) }
            runCatching { resolver.delete(output, null, null) }
        }
    }

    private fun task(input: Uri, output: Uri, payload: ConversionPayload) = ConversionTask(
        id = UUID.randomUUID().toString(),
        sourceUri = input.toString(),
        sourceName = "test.png",
        sourceFormat = FileFormat.PNG,
        targetFormat = FileFormat.JPG,
        outputUri = output.toString(),
        quality = QualityPreset.HIGH,
        resolution = ResolutionPreset.ORIGINAL,
        payload = payload,
    )

    private fun createBitmapFile(resolver: ContentResolver, name: String, w: Int, h: Int): Uri {
        val uri = createPendingDownload(resolver, name, "image/png")
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(200, 30, 30))
        resolver.openOutputStream(uri, "w")!!.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return uri
    }

    /** 左半红、右半蓝的 40x40 图，用于验证旋转/翻转方向。 */
    private fun createHalfColorBitmapFile(resolver: ContentResolver, name: String): Uri {
        val uri = createPendingDownload(resolver, name, "image/png")
        val bitmap = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888)
        for (x in 0 until 40) {
            for (y in 0 until 40) {
                bitmap.setPixel(x, y, if (x < 20) Color.rgb(200, 30, 30) else Color.rgb(30, 30, 200))
            }
        }
        resolver.openOutputStream(uri, "w")!!.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return uri
    }

    private fun decode(resolver: ContentResolver, uri: Uri, block: (Bitmap) -> Unit) {
        val bitmap = resolver.openInputStream(uri)!!.use {
            android.graphics.BitmapFactory.decodeStream(it)
        } ?: throw AssertionError("decode failed")
        try {
            block(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    private fun createPendingDownload(resolver: ContentResolver, name: String, mime: String): Uri = requireNotNull(
        resolver.insert(
            MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, "Download/OpenConvertTest")
            },
        ),
    )
}
