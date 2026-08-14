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
class PdfMergeConverterInstrumentedTest {
    @Test
    fun mergesPdfPagesInSelectedOrder() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver
        val testId = UUID.randomUUID().toString()
        val first = createPdf(resolver, "merge-first-$testId.pdf", listOf(595 to 842, 842 to 595))
        val second = createPdf(resolver, "merge-second-$testId.pdf", listOf(400 to 600))
        val output = createPendingDownload(resolver, "merge-output-$testId.pdf")

        try {
            val result = PdfMergeConverter(context).convert(listOf(second, first), output)
            assertTrue("Expected success, got $result", result is ConversionResult.Success)
            resolver.openFileDescriptor(output, "r")!!.use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    assertEquals(3, renderer.pageCount)
                    renderer.openPage(0).use { page ->
                        assertEquals(400, page.width)
                        assertEquals(600, page.height)
                    }
                    assertTrue(renderer.openPage(1).use { it.width < it.height })
                    assertTrue(renderer.openPage(2).use { it.width > it.height })
                }
            }
        } finally {
            runCatching { resolver.delete(first, null, null) }
            runCatching { resolver.delete(second, null, null) }
            runCatching { resolver.delete(output, null, null) }
        }
    }

    private fun createPdf(
        resolver: ContentResolver,
        name: String,
        sizes: List<Pair<Int, Int>>,
    ): Uri {
        val uri = createPendingDownload(resolver, name)
        val document = PdfDocument()
        try {
            sizes.forEachIndexed { index, (width, height) ->
                val page = document.startPage(PdfDocument.PageInfo.Builder(width, height, index + 1).create())
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

    private fun createPendingDownload(resolver: ContentResolver, name: String): Uri = requireNotNull(
        resolver.insert(
            MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.RELATIVE_PATH, "Download/OpenConvertTest")
            },
        ),
    )
}
