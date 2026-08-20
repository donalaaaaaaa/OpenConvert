package com.openconvert.app.domain.planner

import com.openconvert.app.domain.converter.StreamCodecs
import com.openconvert.app.domain.model.FileFormat
import com.openconvert.app.domain.work.StorageGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Planner 的拒绝路径与并发决策。计划书 §5 验收标准要求 Planner 能自动决定
 * 「并发还是串行」「是否需要预留临时空间」，并在不可行时给出具体原因。
 */
class ConversionPlannerGuardTest {

    private val roomy = RuntimeFacts(usableScratchBytes = 64L * 1024 * 1024 * 1024)

    private fun req(
        input: FileFormat = FileFormat.PNG,
        target: FileFormat = FileFormat.JPG,
        inputBytes: Long = 10L * 1024 * 1024,
        runtime: RuntimeFacts = roomy,
        codecs: StreamCodecs? = null,
    ) = PlanRequest(
        input = input,
        target = target,
        inputBytes = inputBytes,
        codecs = codecs,
        runtime = runtime,
    )

    @Test
    fun `route missing from the capability graph is rejected by name`() {
        val result = ConversionPlanner.plan(req(FileFormat.JPG, FileFormat.HEIC))
        val rejection = (result as PlanResult.Rejected).rejection
        assertTrue(rejection is PlanRejection.UnsupportedRoute)
        val message = PlanRejectionMessages.describe(rejection)
        assertTrue(message.contains("JPG"))
        assertTrue(message.contains("HEIC"))
    }

    @Test
    fun `empty input file is rejected before touching any engine`() {
        val result = ConversionPlanner.plan(
            req(inputBytes = 0L).copy(isSizeVerified = true),
        )
        val rejection = (result as PlanResult.Rejected).rejection
        assertTrue(rejection is PlanRejection.InvalidInput)
        assertTrue(PlanRejectionMessages.describe(rejection).contains("0 字节"))
    }

    @Test
    fun `unverified zero size is not treated as an empty file`() {
        // SAF OpenableColumns.SIZE 对未知大小合法返回 0——不能据此拒绝，
        // 否则真实转换会在 Planner 就被误杀（真机 instrumented 曾因此失败）。
        val result = ConversionPlanner.plan(req(inputBytes = 0L))
        assertTrue("unverified 0 must not be rejected, got $result", result is PlanResult.Ready)
    }

    @Test
    fun `insufficient space rejection carries both numbers`() {
        val inputBytes = 500L * 1024 * 1024
        val required = StorageGuard.requiredScratchBytes(inputBytes, copiesInput = true)
        val result = ConversionPlanner.plan(
            req(
                inputBytes = inputBytes,
                runtime = RuntimeFacts(usableScratchBytes = required - 1),
            ),
        )
        val rejection = (result as PlanResult.Rejected).rejection as PlanRejection.InsufficientSpace
        assertEquals(required, rejection.requiredBytes)
        assertEquals(required - 1, rejection.availableBytes)

        // 文案必须同时出现「需要」与「剩余」的具体数字（计划书 §7.3）。
        val message = PlanRejectionMessages.describe(rejection)
        assertTrue(message.contains("需要"))
        assertTrue(message.contains("当前剩余"))
        assertTrue(message.contains("GB") || message.contains("MB"))
    }

    @Test
    fun `plan reports the scratch budget it reserved`() {
        val inputBytes = 100L * 1024 * 1024
        val result = ConversionPlanner.plan(req(inputBytes = inputBytes)) as PlanResult.Ready
        assertEquals(
            StorageGuard.requiredScratchBytes(inputBytes, copiesInput = true),
            result.plan.requiredScratchBytes,
        )
    }

    @Test
    fun `small image runs in parallel`() {
        val result = ConversionPlanner.plan(req(inputBytes = 2L * 1024 * 1024)) as PlanResult.Ready
        assertEquals(ConcurrencySlot.PARALLEL, result.plan.concurrency)
    }

    @Test
    fun `large file is forced to run serially`() {
        val result = ConversionPlanner.plan(
            req(inputBytes = 500L * 1024 * 1024),
        ) as PlanResult.Ready
        assertEquals(ConcurrencySlot.SERIAL, result.plan.concurrency)
    }

    @Test
    fun `video re-encode is serial even for a small file`() {
        // 硬件编码器是独占资源，不能并行。
        val result = ConversionPlanner.plan(
            PlanRequest(
                input = FileFormat.MP4,
                target = FileFormat.WEBM,
                inputBytes = 5L * 1024 * 1024,
                codecs = StreamCodecs(videoCodec = "h264", audioCodec = "aac"),
                hardware = object : HardwareFacts {
                    override val hasH264HardwareEncoder = true
                    override val hasVp8HardwareEncoder = true
                },
                runtime = roomy,
            ),
        ) as PlanResult.Ready
        assertEquals(ConcurrencySlot.SERIAL, result.plan.concurrency)
    }

    @Test
    fun `stream copy stays parallel even for a large video`() {
        // 拷流不吃 CPU，大文件也可并行。
        val result = ConversionPlanner.plan(
            PlanRequest(
                input = FileFormat.MKV,
                target = FileFormat.MP4,
                inputBytes = 2L * 1024 * 1024 * 1024,
                codecs = StreamCodecs(videoCodec = "h264", audioCodec = "aac"),
                runtime = roomy,
            ),
        ) as PlanResult.Ready
        assertTrue(result.plan.isStreamCopy)
        assertEquals(ConcurrencySlot.PARALLEL, result.plan.concurrency)
    }

    @Test
    fun `byte formatting is human readable at every scale`() {
        assertEquals("512 B", PlanRejectionMessages.formatBytes(512))
        assertEquals("1.0 KB", PlanRejectionMessages.formatBytes(1024))
        assertEquals("1.5 MB", PlanRejectionMessages.formatBytes((1.5 * 1024 * 1024).toLong()))
        assertEquals("2.1 GB", PlanRejectionMessages.formatBytes((2.1 * 1024 * 1024 * 1024).toLong()))
    }

    @Test
    fun `every plan carries a non-empty human readable reason`() {
        listOf(
            req(FileFormat.PNG, FileFormat.JPG),
            req(FileFormat.DOCX, FileFormat.PDF),
            req(FileFormat.ZIP, FileFormat.TAR),
            req(FileFormat.FLAC, FileFormat.MP3, codecs = StreamCodecs(audioCodec = "flac")),
        ).forEach { request ->
            val plan = (ConversionPlanner.plan(request) as PlanResult.Ready).plan
            assertTrue("${request.input}→${request.target} 缺少决策理由", plan.reason.isNotBlank())
        }
    }
}
