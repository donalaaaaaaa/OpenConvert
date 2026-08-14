package com.openconvert.app.domain.converter

import android.content.ContentResolver
import android.content.ContentValues
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
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
class PdfPageEditInstrumentedTest {
    @Test
    fun deletesSelectedPages() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver
        val testId = UUID.randomUUID().toString()
        val input = createPdf(resolver, "delete-in-$testId.pdf", 4)
        val output = createPendingDownload(resolver, "delete-out-$testId.pdf", "application/pdf")

        try {
            val result = PdfDeletePagesConverter(context)
                .convert(input, output, listOf(1, 3), "delete-in-$testId.pdf")
            assertTrue("Expected success, got $result", result is ConversionResult.Success)
            resolver.openFileDescriptor(output, "r")!!.use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    assertEquals(2, renderer.pageCount)
                }
            }
        } finally {
            runCatching { resolver.delete(input, null, null) }
            runCatching { resolver.delete(output, null, null) }
        }
    }

    @Test
    fun rejectsDeletingAllPages() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver
        val testId = UUID.randomUUID().toString()
        val input = createPdf(resolver, "delete-all-in-$testId.pdf", 2)
        val output = createPendingDownload(resolver, "delete-all-out-$testId.pdf", "application/pdf")

        try {
            val result = PdfDeletePagesConverter(context)
                .convert(input, output, listOf(1, 2), "delete-all-in-$testId.pdf")
            assertTrue("Expected failure, got $result", result is ConversionResult.Failure)
        } finally {
            runCatching { resolver.delete(input, null, null) }
            runCatching { resolver.delete(output, null, null) }
        }
    }

    @Test
    fun rotatesAllPagesBy90() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver
        val testId = UUID.randomUUID().toString()
        val input = createPdf(resolver, "rotate-in-$testId.pdf", 1)
        val output = createPendingDownload(resolver, "rotate-out-$testId.pdf", "application/pdf")

        try {
            val result = PdfRotatePagesConverter(context).convert(input, output, 90, null)
            assertTrue("Expected success, got $result", result is ConversionResult.Success)
            resolver.openFileDescriptor(output, "r")!!.use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    assertEquals(1, renderer.pageCount)
                    // 595x842 portrait rotated 90° -> renderer reports 842x595 landscape
                    assertTrue(renderer.openPage(0).use { it.width > it.height })
                }
            }
        } finally {
            runCatching { resolver.delete(input, null, null) }
            runCatching { resolver.delete(output, null, null) }
        }
    }

    @Test
    fun rejectsInvalidRotation() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver
        val testId = UUID.randomUUID().toString()
        val input = createPdf(resolver, "rotate-bad-in-$testId.pdf", 1)
        val output = createPendingDownload(resolver, "rotate-bad-out-$testId.pdf", "application/pdf")

        try {
            val result = PdfRotatePagesConverter(context).convert(input, output, 45, null)
            assertTrue("Expected failure, got $result", result is ConversionResult.Failure)
        } finally {
            runCatching { resolver.delete(input, null, null) }
            runCatching { resolver.delete(output, null, null) }
        }
    }

    private fun createPdf(resolver: ContentResolver, name: String, pageCount: Int): Uri {
        val uri = createPendingDownload(resolver, name, "application/pdf")
        val document = PdfDocument()
        try {
            repeat(pageCount) { index ->
                val page = document.startPage(
                    PdfDocument.PageInfo.Builder(595, 842, index + 1).create(),
                )
                page.canvas.drawColor(Color.WHITE)
                page.canvas.drawText("page-${index + 1}", 40f, 80f, Paint().apply {
                    color = Color.BLACK
                    textSize = 32f
                })
                document.finishPage(page)
            }
            resolver.openOutputStream(uri, "w")!!.use(document::writeTo)
        } finally {
            document.close()
        }
        return uri
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
