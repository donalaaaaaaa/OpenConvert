package com.openconvert.app.domain.benchmark

import com.openconvert.app.domain.engine.EngineType
import com.openconvert.app.domain.model.FileFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** §11.1 指标派生值。 */
class BenchmarkRecordTest {

    private fun record(
        inputBytes: Long = 0,
        outputBytes: Long = 0,
        elapsedMillis: Long = 0,
        succeeded: Boolean = true,
    ) = BenchmarkRecord(
        taskId = "t",
        inputFormat = FileFormat.MKV,
        outputFormat = FileFormat.MP4,
        inputBytes = inputBytes,
        outputBytes = outputBytes,
        elapsedMillis = elapsedMillis,
        engine = EngineType.MEDIA3_MEDIACODEC,
        streamCopy = false,
        hardwareEncode = true,
        peakMemoryBytes = 128L * 1024 * 1024,
        succeeded = succeeded,
    )

    @Test
    fun `throughput is input volume over elapsed time`() {
        // 100MB / 10s = 10MB/s
        val bps = record(inputBytes = 100L * 1024 * 1024, elapsedMillis = 10_000).bytesPerSecond!!
        assertEquals(10L * 1024 * 1024, bps)
    }

    @Test
    fun `zero elapsed yields no throughput rather than infinity`() {
        assertNull(record(inputBytes = 1024, elapsedMillis = 0).bytesPerSecond)
    }

    @Test
    fun `unknown input size yields no throughput`() {
        assertNull(record(inputBytes = 0, elapsedMillis = 5_000).bytesPerSecond)
    }

    @Test
    fun `reduction percent is positive when the file shrinks`() {
        // 10MB → 2.5MB = 缩小 75%
        val pct = record(
            inputBytes = 10L * 1024 * 1024,
            outputBytes = (2.5 * 1024 * 1024).toLong(),
        ).reductionPercent!!
        assertEquals(75, pct)
    }

    @Test
    fun `reduction percent is negative when the file grows`() {
        // JPG→PNG 无损化会变大，压缩率必须能表达"变大"。
        val pct = record(
            inputBytes = 1L * 1024 * 1024,
            outputBytes = 3L * 1024 * 1024,
        ).reductionPercent!!
        assertTrue("变大时应为负值，实际 $pct", pct < 0)
        assertEquals(-200, pct)
    }

    @Test
    fun `reduction is null when either side is unknown`() {
        assertNull(record(inputBytes = 0, outputBytes = 100).reductionPercent)
        assertNull(record(inputBytes = 100, outputBytes = 0).reductionPercent)
    }

    @Test
    fun `route renders both display names`() {
        assertEquals("MKV → MP4", record().route)
    }

    @Test
    fun `failed records keep their identity`() {
        // 失败任务也要记录：排查回归时"哪些组合会失败"与"多快"同等重要。
        val failed = record(inputBytes = 1024, succeeded = false)
        assertEquals(false, failed.succeeded)
        assertEquals(FileFormat.MKV, failed.inputFormat)
    }
}
