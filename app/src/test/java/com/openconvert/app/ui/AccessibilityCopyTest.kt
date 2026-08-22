package com.openconvert.app.ui

import com.openconvert.app.domain.capability.FileCapabilityResolver
import com.openconvert.app.domain.error.ErrorPresentation
import com.openconvert.app.domain.model.ConversionStatus
import com.openconvert.app.domain.model.ConversionTask
import com.openconvert.app.domain.model.FileFormat
import com.openconvert.app.domain.task.TaskBucket
import com.openconvert.app.domain.task.TaskCardModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityCopyTest {

    @Test
    fun toolJoinsTitleAndSubtitle() {
        assertEquals("PDF 工具，转换 · 合并 · 拆分", AccessibilityCopy.tool("PDF 工具", "转换 · 合并 · 拆分"))
        assertEquals("批量", AccessibilityCopy.tool("批量", ""))
    }

    @Test
    fun progressMentionsBytesWhenKnown() {
        val spoken = AccessibilityCopy.progress(40, bytesProcessed = 40L * 1024 * 1024, bytesTotal = 100L * 1024 * 1024)
        assertTrue(spoken.contains("40%"))
        assertTrue(spoken.contains("40.0 MB") || spoken.contains("40 MB"))
        assertTrue(spoken.contains("100.0 MB") || spoken.contains("100 MB"))
    }

    @Test
    fun historyNamesStatusForTalkBack() {
        val spoken = AccessibilityCopy.history(
            ConversionTask(
                id = "1",
                sourceUri = "content://x",
                sourceName = "vacation.heic",
                sourceFormat = FileFormat.HEIC,
                targetFormat = FileFormat.JPG,
                status = ConversionStatus.FAILED,
            ),
        )
        assertTrue(spoken.contains("vacation.heic"))
        assertTrue(spoken.contains("失败"))
        assertFalse(spoken.contains("null"))
    }

    @Test
    fun everyPdfToolHasASpokenLabel() {
        val tools = FileCapabilityResolver.resolve(FileFormat.PDF).tools
        assertTrue(tools.isNotEmpty())
        tools.forEach { action ->
            assertTrue("${action.kind} 缺面板文案", action.label.isNotBlank())
            assertTrue("${action.kind} 缺说明", action.description.isNotBlank())
            assertEquals(action.label, AccessibilityCopy.tool(action.label, action.description).substringBefore("，"))
        }
    }

    @Test
    fun mainDestinationsHaveResourceIds() {
        mainDestinations.forEach { dest ->
            assertTrue(dest.labelRes != 0)
            assertTrue(dest.route.isNotBlank())
        }
    }

    @Test
    fun failedTaskCardIncludesErrorTitle() {
        val card = TaskCardModel(
            task = ConversionTask(
                id = "t",
                sourceUri = "content://x",
                sourceName = "clip.mp4",
                sourceFormat = FileFormat.MP4,
                targetFormat = FileFormat.GIF,
                status = ConversionStatus.FAILED,
            ),
            route = "MP4 → GIF",
            progressPercent = 0,
            speedText = null,
            remainingText = null,
            sizeSummary = null,
            elapsedText = null,
            engineText = null,
            error = ErrorPresentation(title = "空间不足", suggestion = "清理存储"),
        )
        val spoken = AccessibilityCopy.taskCard(card, TaskBucket.FAILED)
        assertTrue(spoken.contains("空间不足"))
        assertTrue(spoken.contains("MP4 → GIF"))
    }
}
