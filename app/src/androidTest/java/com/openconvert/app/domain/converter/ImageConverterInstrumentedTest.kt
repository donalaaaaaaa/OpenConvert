package com.openconvert.app.domain.converter

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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

@RunWith(AndroidJUnit4::class)
class ImageConverterInstrumentedTest {
    @Test
    fun convertsTransparentPngToHalfSizeJpeg() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver
        val testId = UUID.randomUUID().toString()
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val sourceUri = requireNotNull(
            resolver.insert(
                collection,
                ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "openconvert-source-$testId.png")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/OpenConvertTest")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                },
            ),
        )
        val outputUri = requireNotNull(
            resolver.insert(
                collection,
                ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "openconvert-output-$testId.jpg")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/OpenConvertTest")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                },
            ),
        )

        try {
            val bitmap = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.argb(100, 40, 120, 200))
            }
            resolver.openOutputStream(sourceUri, "wt")!!.use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
            resolver.update(
                sourceUri,
                ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                null,
                null,
            )

            val result = ImageConverter(resolver).convert(
                ConversionTask(
                    id = UUID.randomUUID().toString(),
                    sourceUri = sourceUri.toString(),
                    sourceName = "openconvert-source.png",
                    sourceFormat = FileFormat.PNG,
                    targetFormat = FileFormat.JPG,
                    outputUri = outputUri.toString(),
                    fileSize = 1L,
                    quality = QualityPreset.BALANCED,
                    resolution = ResolutionPreset.SMALL,
                ),
            )

            assertTrue("Expected success, got $result", result is ConversionResult.Success)
            val outputSize = resolver.openFileDescriptor(outputUri, "r")!!.use { it.statSize }
            assertTrue(outputSize > 0)
            val converted = resolver.openInputStream(outputUri)!!.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
            assertEquals(400, converted.width)
            assertEquals(300, converted.height)
            converted.recycle()
        } finally {
            runCatching { resolver.delete(sourceUri, null, null) }
            runCatching { resolver.delete(outputUri, null, null) }
        }
    }
}
