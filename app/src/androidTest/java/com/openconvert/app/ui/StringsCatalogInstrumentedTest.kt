package com.openconvert.app.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.openconvert.app.R
import com.openconvert.app.domain.capability.FileCapabilityResolver
import com.openconvert.app.domain.error.ConversionError
import com.openconvert.app.domain.error.ErrorPresenter
import com.openconvert.app.domain.model.FileFormat
import com.openconvert.app.domain.preset.PresetRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StringsCatalogInstrumentedTest {

    private val ctx get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun chromeStringsResolve() {
        assertEquals("首页", ctx.getString(R.string.nav_home))
        assertEquals("设置", ctx.getString(R.string.nav_settings))
        assertEquals("选择文件", ctx.getString(R.string.home_pick_file))
        assertEquals("PDF 工具箱", ctx.getString(R.string.pdf_tools_title))
        assertEquals("正在转换", ctx.getString(R.string.conversion_running))
        assertEquals("文件不会离开您的设备", ctx.getString(R.string.privacy_hint))
    }

    @Test
    fun formattedStringsKeepPlaceholders() {
        assertEquals("3 条 · 导出报告", ctx.getString(R.string.settings_benchmark_count, 3))
        assertEquals("速度 41 MB/s", ctx.getString(R.string.tasks_speed, "41 MB/s"))
        mainDestinations.forEach { dest ->
            assertTrue(ctx.getString(dest.labelRes).isNotBlank())
        }
    }

    @Test
    fun errorCodesMatchPresenter() {
        ConversionError.Code.entries.forEach { code ->
            val presented = ErrorPresenter.fromCode(code)
            assertEquals(
                "title $code",
                presented.title,
                ctx.getString(ErrorCopy.titleRes(code)),
            )
            val suggestionRes = ErrorCopy.suggestionRes(code)
            if (suggestionRes != null) {
                assertEquals(
                    "suggestion $code",
                    presented.suggestion,
                    ctx.getString(suggestionRes),
                )
            }
        }
    }

    @Test
    fun builtInPresetNamesMatchRepository() {
        PresetRepository.BUILT_IN_PRESETS.forEach { preset ->
            val nameRes = PresetCopy.nameRes(preset.id)
            val descRes = PresetCopy.descriptionRes(preset.id)
            assertNotNull("${preset.id} missing name res", nameRes)
            assertNotNull("${preset.id} missing desc res", descRes)
            assertEquals(preset.name, ctx.getString(nameRes!!))
            assertEquals(preset.description, ctx.getString(descRes!!))
        }
    }

    @Test
    fun capabilityPanelTitlesResolve() {
        val pdf = FileCapabilityResolver.resolve(FileFormat.PDF)
        assertEquals("PDF 工具", ctx.getString(pdf.toolSectionTitleRes))
        val heic = FileCapabilityResolver.resolve(FileFormat.HEIC)
        assertEquals("转换为", ctx.getString(heic.convertSectionTitleRes))
        pdf.tools.forEach { action ->
            assertTrue(ctx.getString(ToolCopy.labelRes(action.kind)).isNotBlank())
            assertTrue(ctx.getString(ToolCopy.descriptionRes(action.kind)).isNotBlank())
        }
    }
}
