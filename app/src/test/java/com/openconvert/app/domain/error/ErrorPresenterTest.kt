package com.openconvert.app.domain.error

import com.openconvert.app.domain.engine.EngineType
import com.openconvert.app.domain.model.FileFormat
import com.openconvert.app.domain.planner.PlanRejection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 计划书 §7.3：错误信息禁止仅显示 "Conversion failed"，
 * 必须映射为用户能理解的原因。
 */
class ErrorPresenterTest {

    @Test
    fun `insufficient space carries both numbers and a next step`() {
        val presentation = ErrorPresenter.fromRejection(
            PlanRejection.InsufficientSpace(
                requiredBytes = (2.1 * 1024 * 1024 * 1024).toLong(),
                availableBytes = (1.3 * 1024 * 1024 * 1024).toLong(),
            ),
        )
        assertEquals("存储空间不足", presentation.title)
        // 计划书示例：需要 2.1 GB / 当前剩余 1.3 GB
        assertTrue(presentation.detail!!.contains("2.1 GB"))
        assertTrue(presentation.detail!!.contains("1.3 GB"))
        assertNotNull("必须告诉用户能做什么", presentation.suggestion)
    }

    @Test
    fun `unsupported codec lists what was attempted`() {
        val presentation = ErrorPresenter.fromRejection(
            PlanRejection.NoUsableEncoder(
                codec = "h264",
                attempted = listOf(EngineType.MEDIA3_MEDIACODEC),
            ),
        )
        // 计划书示例：已尝试 MediaCodec / 可切换 FFmpeg
        assertTrue(presentation.detail!!.contains("MediaCodec"))
        assertNotNull(presentation.suggestion)
    }

    @Test
    fun `structured storage exception keeps the byte numbers`() {
        val presentation = ErrorPresenter.fromException(
            ConversionException.InsufficientStorage(
                requiredBytes = 500L * 1024 * 1024,
                availableBytes = 100L * 1024 * 1024,
            ),
        )
        assertTrue(presentation.detail!!.contains("500.0 MB"))
        assertTrue(presentation.detail!!.contains("100.0 MB"))
    }

    @Test
    fun `every structured exception yields a non-empty title`() {
        val samples = listOf(
            ConversionException.UnsupportedFormat("xyz"),
            ConversionException.InvalidFile("header broken"),
            ConversionException.PermissionDenied(),
            ConversionException.InsufficientStorage(1, 0),
            ConversionException.CodecUnavailable("av1"),
            ConversionException.OutOfMemoryRisk(),
            ConversionException.PasswordRequired(),
            ConversionException.WrongPassword(),
            ConversionException.EngineFailure("boom"),
            ConversionException.TaskCancelled(),
            ConversionException.Unknown(""),
            ConversionException.ArchiveExpansionLimit(
                com.openconvert.app.domain.converter.ArchiveRejectReason.RATIO_TOO_HIGH,
            ),
        )
        samples.forEach { exception ->
            val presentation = ErrorPresenter.fromException(exception)
            assertTrue(
                "${exception::class.simpleName} 标题为空",
                presentation.title.isNotBlank(),
            )
        }
    }

    @Test
    fun `blank stored message never renders as an empty error`() {
        listOf(null, "", "   ").forEach { raw ->
            val presentation = ErrorPresenter.fromStoredMessage(raw)
            assertTrue(presentation.title.isNotBlank())
            assertNotNull(presentation.suggestion)
        }
    }

    @Test
    fun `the forbidden placeholder gets replaced with something actionable`() {
        val presentation = ErrorPresenter.fromStoredMessage(
            "Conversion failed",
            sourceFormat = FileFormat.MKV,
            targetFormat = FileFormat.MP4,
        )
        assertFalse(
            "不得把英文占位串直接呈现给用户",
            presentation.title.equals("Conversion failed", ignoreCase = true),
        )
        assertTrue(presentation.detail!!.contains("MKV"))
        assertTrue(presentation.detail!!.contains("MP4"))
        assertNotNull(presentation.suggestion)
    }

    @Test
    fun `known chinese patterns gain a suggestion`() {
        val cases = mapOf(
            "存储空间不足，请清理后再试" to "清理",
            "没有读取或保存此文件的权限" to "授权",
            "文件不是有效图片或已经损坏" to "换一个",
            "图片分辨率过高，请选择更小的输出尺寸" to "尺寸",
            "上次转换被系统中断，请重试" to "重新",
        )
        cases.forEach { (message, expectedHint) ->
            val presentation = ErrorPresenter.fromStoredMessage(message)
            assertEquals("标题应保留原始信息", message, presentation.title)
            assertTrue(
                "「$message」应给出含『$expectedHint』的建议，实际 ${presentation.suggestion}",
                presentation.suggestion?.contains(expectedHint) == true,
            )
        }
    }

    @Test
    fun `unrecognised message is preserved verbatim as the title`() {
        val odd = "FFmpeg exited with code 234"
        val presentation = ErrorPresenter.fromStoredMessage(odd)
        assertEquals("不得丢失既有信息", odd, presentation.title)
    }

    @Test
    fun `one line representation joins title and detail`() {
        val presentation = ErrorPresentation("存储空间不足", "需要 2.1 GB，当前剩余 1.3 GB")
        assertEquals("存储空间不足 — 需要 2.1 GB，当前剩余 1.3 GB", presentation.oneLine)
    }
}
