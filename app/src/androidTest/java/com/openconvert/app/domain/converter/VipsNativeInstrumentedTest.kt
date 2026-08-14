package com.openconvert.app.domain.converter

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VipsNativeInstrumentedTest {
    @Test
    fun nativeLibraryLoadsAndConvertsPng() {
        assumeTrue("libvips_android.so not present on this device", VipsNative.isAvailable)

        // Build a 640x480 RGBA png in memory.
        val bitmap = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(30, 140, 220))
        val pngBytes = ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
        bitmap.recycle()

        val info = VipsNative.probeBuffer(pngBytes)
        assertEquals(640, info[0])
        assertEquals(480, info[1])

        // Shrink to half via libvips.
        val outBytes = VipsNative.convertBuffer(pngBytes, 320, 240, 0, "jpg", 85)
        assertTrue("expected non-empty jpeg output", outBytes.isNotEmpty())

        // Decode what libvips produced and confirm the dimensions.
        val decoded = android.graphics.BitmapFactory.decodeByteArray(outBytes, 0, outBytes.size)
        assertEquals(320, decoded.width)
        assertEquals(240, decoded.height)
        decoded.recycle()
    }
}
