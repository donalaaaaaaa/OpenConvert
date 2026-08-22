package com.openconvert.app.domain.converter

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.openconvert.app.BuildConfig
import com.openconvert.app.domain.engine.EngineType
import com.openconvert.app.domain.model.ConversionResult
import com.openconvert.app.domain.model.ConversionTask
import com.openconvert.app.domain.model.FileFormat
import com.openconvert.app.domain.model.QualityPreset
import com.openconvert.app.domain.model.ResolutionPreset
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Office Flavor 内置 LibreOfficeKit 端到端（真机）。
 * 覆盖：DOCX/PPTX/XLSX → PDF，以及保真度素材矩阵。
 */
@RunWith(AndroidJUnit4::class)
class OfficeConverterInstrumentedTest {

    @Test
    fun bundledEngineConvertsAllOfficeFormats() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue("Office 变体必须声明内置引擎", BuildConfig.OFFICE_BUNDLED)
        assertTrue("内置 LibreOfficeKit 引擎应可用", OfficeEngine.isAvailable(context))
        convertAndVerify("office-test-doc.docx", FileFormat.DOCX)
        convertAndVerify("office-test-slide.pptx", FileFormat.PPTX)
        convertAndVerify("office-test-sheet.xlsx", FileFormat.XLSX)
    }

    @Test
    fun bundledEnginePreservesOfficeFidelityMatrix() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue("Office 变体必须声明内置引擎", BuildConfig.OFFICE_BUNDLED)
        assertTrue("内置 LibreOfficeKit 引擎应可用", OfficeEngine.isAvailable(context))

        convertAndVerify(
            assetName = "office-fidelity-doc.docx",
            format = FileFormat.DOCX,
            minimumPages = 2,
            expectedMarkers = listOf(
                "DOCX-中文-甲",
                "DOCX-表格-合计",
                "DOCX-HEADER",
                "DOCX-FOOTER",
                "DOCX-CJK-PASS",
            ),
        )
        convertAndVerify(
            assetName = "office-fidelity-slide.pptx",
            format = FileFormat.PPTX,
            minimumPages = 2,
            expectedMarkers = listOf(
                "PPTX-CJK-PASS",
                "PPTX-第二页-乙",
                "PPTX-TABLE-PASS",
            ),
        )
        convertAndVerify(
            assetName = "office-fidelity-sheet.xlsx",
            format = FileFormat.XLSX,
            minimumPages = 3,
            expectedMarkers = listOf(
                "XLSX-总览-甲",
                "XLSX-华东-乙",
                "XLSX-华南-丙",
                "XLSX-FORMULA-PASS",
                "XLSX-SHEETS-PASS",
                "2,740,000",
            ),
        )
    }

    private suspend fun convertAndVerify(
        assetName: String,
        format: FileFormat,
        minimumPages: Int = 1,
        expectedMarkers: List<String> = emptyList(),
    ) {
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
            assertEquals(
                "Office 成功结果必须报告实际引擎",
                EngineType.LIBREOFFICE_KIT,
                (result as ConversionResult.Success).actualEngine,
            )

            resolver.openInputStream(output)!!.use { stream ->
                val header = stream.readBytes().take(5)
                assertEquals("%PDF-", String(header.toByteArray()))
            }
            val size = resolver.openFileDescriptor(output, "r")!!.use { it.statSize }
            assertTrue("PDF 应非空, size=$size", size > 0)

            resolver.openInputStream(output)!!.use { stream ->
                PDDocument.load(stream).use { document ->
                    assertTrue(
                        "$assetName 导出页数不足：${document.numberOfPages} < $minimumPages",
                        document.numberOfPages >= minimumPages,
                    )
                    if (expectedMarkers.isNotEmpty()) {
                        val text = PDFTextStripper().getText(document).withoutWhitespace()
                        expectedMarkers.forEach { marker ->
                            assertTrue(
                                "$assetName 导出 PDF 缺少关键文本：$marker\n实际文本：${text.take(800)}",
                                text.contains(marker.withoutWhitespace()),
                            )
                        }
                    }
                }
            }
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

    private fun String.withoutWhitespace(): String = replace(Regex("\\s+"), "")
}
