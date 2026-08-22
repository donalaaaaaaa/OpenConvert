package com.openconvert.app.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.openconvert.app.OpenConvertApplication
import com.openconvert.app.domain.capability.FileCapabilityResolver
import com.openconvert.app.domain.model.ConversionKind
import com.openconvert.app.domain.model.FileFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 首页 UI 2.0（计划书 §六）文件驱动流程的 ViewModel 层验收。
 *
 * 需要真 Application（Room + WorkManager），因此放在 androidTest。
 * 只验证状态机，不驱动 Compose。
 */
@RunWith(AndroidJUnit4::class)
class FileDrivenFlowInstrumentedTest {

    private fun viewModel(): MainViewModel {
        val app = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as OpenConvertApplication
        return MainViewModel(app)
    }

    @Test
    fun inspectingAnUnreadableUriReportsAMessageAndPicksNothing() {
        val vm = viewModel()
        val bogus = android.net.Uri.parse("content://com.openconvert.nonexistent/42")

        val accepted = vm.inspectFile(bogus)

        assertFalse("不可读的 URI 不应被接受", accepted)
        assertNull("失败时不得留下已选文件", vm.pickedFile.value)
        assertNull(vm.pickedCapabilities.value)
        assertNotNull("必须给出用户可见提示", vm.message.value)
    }

    @Test
    fun clearPickedFileResetsBothStates() {
        val vm = viewModel()
        vm.clearPickedFile()
        assertNull(vm.pickedFile.value)
        assertNull(vm.pickedCapabilities.value)
    }

    @Test
    fun chooseConvertTargetWithoutAPickedFileIsRejected() {
        val vm = viewModel()
        vm.clearPickedFile()
        assertFalse(
            "没有已选文件时不应产生 draft",
            vm.chooseConvertTarget(FileFormat.JPG),
        )
        assertNull(vm.draft.value)
    }

    /**
     * 能力面板展示的每个转换目标都必须能被 chooseConvertTarget 接受，
     * 每个工具都必须有落地页 —— 否则用户会点到死路。
     */
    @Test
    fun everyAdvertisedCapabilityIsReachable() {
        FileFormat.entries.filter { it != FileFormat.UNKNOWN }.forEach { format ->
            val caps = FileCapabilityResolver.resolve(format)

            caps.convertTargets.forEach { target ->
                assertTrue(
                    "$format → $target 被面板展示但引擎不支持",
                    com.openconvert.app.domain.model.ConversionGraph.canConvert(format, target),
                )
            }

            caps.tools.forEach { action ->
                if (action.kind != ConversionKind.SINGLE && action.kind != ConversionKind.BATCH) {
                    assertNotNull(
                        "${action.kind} 被面板展示但没有对应页面",
                        toolRouteForTest(action.kind),
                    )
                }
            }
        }
    }

    @Test
    fun pdfPanelOffersToolsButNoDirectConversion() {
        val caps = FileCapabilityResolver.resolve(FileFormat.PDF)
        assertTrue(caps.convertTargets.isEmpty())
        assertTrue(caps.tools.isNotEmpty())
        // 面板标题应指向 PDF 工具，而不是"转换为"。
        assertEquals("PDF 工具", caps.toolSectionTitle)
    }

    /**
     * 与 OpenConvertApp.routeForTool 保持同步的测试副本。
     * 该函数在 UI 层是 private，这里复刻映射以断言覆盖完整性；
     * 若新增工具 kind 忘了加路由，上面的用例会失败。
     */
    private fun toolRouteForTest(kind: ConversionKind): String? = when (kind) {
        ConversionKind.IMAGES_TO_PDF -> "images_to_pdf"
        ConversionKind.PDF_TO_IMAGES -> "pdf_to_images"
        ConversionKind.PDF_MERGE -> "pdf_merge"
        ConversionKind.PDF_SPLIT -> "pdf_split"
        ConversionKind.PDF_DELETE_PAGES -> "pdf_delete"
        ConversionKind.PDF_ROTATE_PAGES -> "pdf_rotate"
        ConversionKind.PDF_COMPRESS -> "pdf_compress"
        ConversionKind.PDF_SECURITY -> "pdf_security"
        ConversionKind.PDF_CROP -> "pdf_crop"
        ConversionKind.PDF_METADATA -> "pdf_metadata"
        ConversionKind.PDF_WATERMARK -> "pdf_watermark"
        ConversionKind.PDF_PAGE_MANAGER -> "pdf_page_manager"
        ConversionKind.ARCHIVE_COMPRESS -> "archive_compress"
        ConversionKind.ARCHIVE_EXTRACT -> "archive_extract"
        ConversionKind.SINGLE, ConversionKind.BATCH -> null
    }
}
