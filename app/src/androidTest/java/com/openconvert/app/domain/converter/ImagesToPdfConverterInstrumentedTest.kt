package com.openconvert.app.domain.converter

import android.content.ContentResolver
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.openconvert.app.domain.model.ConversionResult
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImagesToPdfConverterInstrumentedTest {
    @Test
    fun createsTwoPagePdfFromPortraitAndLandscapeImages() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver
        val testId = UUID.randomUUID().toString()
        val portrait = createImage(resolver, "portrait-$testId.png", 360, 640, Color.rgb(30, 90, 160))
        val landscape = createImage(resolver, "landscape-$testId.png", 640, 360, Color.rgb(180, 80, 40))
        val output = requireNotNull(
            resolver.insert(
                MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, "openconvert-$testId.pdf")
                    put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                    put(MediaStore.Downloads.RELATIVE_PATH, "Download/OpenConvertTest")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                },
            ),
        )

        try {
            val result = ImagesToPdfConverter(resolver).convert(listOf(portrait, landscape), output)
            assertTrue("Expected success, got $result", result is ConversionResult.Success)
            val signature = resolver.openInputStream(output)!!.use { stream ->
                ByteArray(4).also { stream.read(it) }.toString(Charsets.US_ASCII)
            }
            assertEquals("%PDF", signature)
            resolver.openFileDescriptor(output, "r")!!.use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    assertEquals(2, renderer.pageCount)
                    assertTrue(renderer.openPage(0).use { it.width < it.height })
                    assertTrue(renderer.openPage(1).use { it.width > it.height })
                }
            }
        } finally {
            runCatching { resolver.delete(portrait, null, null) }
            runCatching { resolver.delete(landscape, null, null) }
            runCatching { resolver.delete(output, null, null) }
        }
    }

    @Test
    fun embedsJpegStreamDirectlyWithoutRecompressing() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver
        val testId = UUID.randomUUID().toString()
        val jpeg = createJpeg(resolver, "embed-$testId.jpg", 800, 600, Color.rgb(40, 120, 80))
        val output = requireNotNull(
            resolver.insert(
                MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, "openconvert-embed-$testId.pdf")
                    put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                    put(MediaStore.Downloads.RELATIVE_PATH, "Download/OpenConvertTest")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                },
            ),
        )
        try {
            val result = ImagesToPdfConverter(resolver).convert(listOf(jpeg), output)
            assertTrue("Expected success, got $result", result is ConversionResult.Success)
            resolver.openFileDescriptor(output, "r")!!.use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    assertEquals(1, renderer.pageCount)
                    // Render the page and sample the centre pixel: the embedded JPEG must
                    // still carry the original DCT data (no decode/re-encode round trip).
                    renderer.openPage(0).use { page ->
                        val bitmap = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888)
                        try {
                            bitmap.eraseColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val center = bitmap.getPixel(32, 24)
                            val r = Color.red(center)
                            val g = Color.green(center)
                            val b = Color.blue(center)
                            assertTrue(
                                "Embedded JPEG rendered wrong colour: ($r,$g,$b), expected ~(40,120,80)",
                                kotlin.math.abs(r - 40) < 40 && kotlin.math.abs(g - 120) < 40 && kotlin.math.abs(b - 80) < 40,
                            )
                        } finally {
                            bitmap.recycle()
                        }
                    }
                }
            }
        } finally {
            runCatching { resolver.delete(jpeg, null, null) }
            runCatching { resolver.delete(output, null, null) }
        }
    }

    private fun createJpeg(
        resolver: ContentResolver,
        name: String,
        width: Int,
        height: Int,
        color: Int,
    ): Uri {
        val uri = requireNotNull(
            resolver.insert(
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/OpenConvertTest")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                },
            ),
        )
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        resolver.openOutputStream(uri, "wt")!!.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        resolver.update(
            uri,
            ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
            null,
            null,
        )
        return uri
    }

    private fun createImage(
        resolver: ContentResolver,
        name: String,
        width: Int,
        height: Int,
        color: Int,
    ): Uri {
        val uri = requireNotNull(
            resolver.insert(
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/OpenConvertTest")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                },
            ),
        )
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        resolver.openOutputStream(uri, "wt")!!.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        resolver.update(
            uri,
            ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
            null,
            null,
        )
        return uri
    }
}
