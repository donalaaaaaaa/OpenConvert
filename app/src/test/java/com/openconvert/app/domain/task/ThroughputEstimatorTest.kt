package com.openconvert.app.domain.task

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 单任务卡片的速度 / 剩余时间估算（计划书 §7.1）。 */
class ThroughputEstimatorTest {

    private val oneHundredMb = 100L * 1024 * 1024

    @Test
    fun `estimates speed and remaining time from two samples`() {
        // 2 秒推进 20 个百分点 → 0.01 %/ms，处理了 20MB → 约 10MB/s。
        // 当前 30%，剩余 70 个百分点 → 70 / 0.01 = 7000ms。
        val estimate = ThroughputEstimator.estimate(
            previous = ProgressSample(10, 1_000L),
            current = ProgressSample(30, 3_000L),
            inputBytes = oneHundredMb,
        )
        val bps = estimate.bytesPerSecond
        org.junit.Assert.assertNotNull(bps)
        assertTrue("速度应约 10MB/s，实际 $bps", bps!! in (9L * 1024 * 1024)..(11L * 1024 * 1024))
        assertEquals(7_000L, estimate.remainingMillis)
    }

    @Test
    fun `too small a progress delta yields no estimate`() {
        val estimate = ThroughputEstimator.estimate(
            previous = ProgressSample(50, 0L),
            current = ProgressSample(50, 5_000L),
            inputBytes = oneHundredMb,
        )
        assertNull(estimate.bytesPerSecond)
        assertNull(estimate.remainingMillis)
    }

    @Test
    fun `too short an interval yields no estimate`() {
        // 100ms 内进度跳变会算出荒谬速率，必须拒绝。
        val estimate = ThroughputEstimator.estimate(
            previous = ProgressSample(10, 0L),
            current = ProgressSample(60, 100L),
            inputBytes = oneHundredMb,
        )
        assertNull(estimate.bytesPerSecond)
    }

    @Test
    fun `unknown input size still gives remaining time`() {
        // SAF 元数据可能没有体积（返回 0），此时速度未知但剩余时间仍可推。
        val estimate = ThroughputEstimator.estimate(
            previous = ProgressSample(20, 0L),
            current = ProgressSample(40, 2_000L),
            inputBytes = 0L,
        )
        assertNull("没有体积就不该编造速度", estimate.bytesPerSecond)
        assertEquals(6_000L, estimate.remainingMillis)
    }

    @Test
    fun `completed task reports no remaining time`() {
        val estimate = ThroughputEstimator.estimate(
            previous = ProgressSample(80, 0L),
            current = ProgressSample(100, 1_000L),
            inputBytes = oneHundredMb,
        )
        assertNull(estimate.remainingMillis)
        assertNotNull(estimate.bytesPerSecond)
    }

    @Test
    fun `speed formatting matches the spec sample`() {
        assertEquals("41.0 MB/s", ThroughputEstimator.formatSpeed(41L * 1024 * 1024))
        assertEquals("820 KB/s", ThroughputEstimator.formatSpeed(820L * 1024))
        assertEquals("512 B/s", ThroughputEstimator.formatSpeed(512))
        assertNull(ThroughputEstimator.formatSpeed(null))
        assertNull("0 不应显示为速度", ThroughputEstimator.formatSpeed(0))
    }

    @Test
    fun `remaining time formatting matches the spec sample`() {
        // 计划书 §7.1 的示例：剩余 1m 23s
        assertEquals("1m 23s", ThroughputEstimator.formatRemaining(83_000L))
        assertEquals("12s", ThroughputEstimator.formatRemaining(12_000L))
        assertEquals("1h 04m", ThroughputEstimator.formatRemaining(3_840_000L))
        assertNull(ThroughputEstimator.formatRemaining(null))
        assertNull(ThroughputEstimator.formatRemaining(0L))
    }

    @Test
    fun `backwards progress is rejected rather than producing negative speed`() {
        val estimate = ThroughputEstimator.estimate(
            previous = ProgressSample(60, 0L),
            current = ProgressSample(30, 1_000L),
            inputBytes = oneHundredMb,
        )
        assertNull(estimate.bytesPerSecond)
        assertNull(estimate.remainingMillis)
    }
}
