package com.openconvert.app.domain.converter

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.openconvert.app.domain.model.ConversionResult
import com.openconvert.app.domain.model.ConversionTask
import com.openconvert.app.domain.model.FileFormat
import com.openconvert.app.domain.model.QualityPreset
import com.openconvert.app.domain.model.ResolutionPreset
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Office Pack 可选下载端到端（真机）。
 * 前置：`adb push output/office-pack.zip /sdcard/Download/`
 * 覆盖：未安装→不可用；安装 pack → DOCX/PPTX/XLSX → PDF。
 */
@RunWith(AndroidJUnit4::class)
class OfficeConverterInstrumentedTest {

    @Test
    fun bundledEngineConvertsAllOfficeFormats() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue("内置 LibreOfficeKit 引擎应可用", OfficeEngine.isAvailable(context))
        convertAndVerify("office-test-doc.docx", FileFormat.DOCX)
        convertAndVerify("office-test-slide.pptx", FileFormat.PPTX)
        convertAndVerify("office-test-sheet.xlsx", FileFormat.XLSX)
    }

    private suspend fun convertAndVerify(assetName: String, format: FileFormat) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val testContext = InstrumentationRegistry.getInstrumentation().context
        val resolver = context.contentResolver
        val testId = UUID.randomUUID().toString()

        val input = createPendingDownload(resolver, "$testId-$assetName", format.mimeType)
        testContext.assets.open(assetName).use { inputStream ->
            resolver.openOutputStream(input, "w")!!.use { output -> inputStream.copyTo(output) }
        }
        val output = createPendingDownload(resolver, "$testId-out.pdf", "application/pdf")

        try {
            val converter = OfficeConverter(context)
            assertTrue("supports $format→PDF", converter.supports(format, FileFormat.PDF))

            val task = ConversionTask(
                id = UUID.randomUUID().toString(),
                sourceUri = input.toString(),
                sourceName = assetName,
                sourceFormat = format,
                targetFormat = FileFormat.PDF,
                outputUri = output.toString(),
                quality = QualityPreset.HIGH,
                resolution = ResolutionPreset.ORIGINAL,
            )
            val result = converter.convert(task)
            assertTrue("Expected success, got $result", result is ConversionResult.Success)

            resolver.openInputStream(output)!!.use { stream ->
                val header = stream.readBytes().take(5)
                assertEquals("%PDF-", String(header.toByteArray()))
            }
            val size = resolver.openFileDescriptor(output, "r")!!.use { it.statSize }
            assertTrue("PDF 应非空, size=$size", size > 0)
        } finally {
            runCatching { resolver.delete(input, null, null) }
            runCatching { resolver.delete(output, null, null) }
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
