package com.openconvert.app.domain.engine

import com.openconvert.app.domain.model.FileFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversionEngineSelectorTest {

    @Test
    fun testImageConversionsUseLibvips() {
        val decision = ConversionEngineSelector.selectEngine(
            inputFormat = FileFormat.PNG,
            outputFormat = FileFormat.WEBP,
        )
        assertEquals(EngineType.LIBVIPS, decision.primaryEngine)
    }

    @Test
    fun testOfficeConversionsUseLibreOfficeKit() {
        val decision = ConversionEngineSelector.selectEngine(
            inputFormat = FileFormat.DOCX,
            outputFormat = FileFormat.PDF,
        )
        assertEquals(EngineType.LIBREOFFICE_KIT, decision.primaryEngine)
    }

    @Test
    fun testPdfToolsUsePdfBox() {
        val decision = ConversionEngineSelector.selectEngine(
            inputFormat = FileFormat.PDF,
            outputFormat = FileFormat.PDF,
        )
        assertEquals(EngineType.PDFBOX, decision.primaryEngine)
    }
}
