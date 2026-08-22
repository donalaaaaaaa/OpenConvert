package com.openconvert.app.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.openconvert.app.R
import org.junit.Assert.assertEquals
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
}
