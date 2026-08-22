package com.openconvert.app.domain.benchmark

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.openconvert.app.domain.engine.EngineType
import com.openconvert.app.domain.model.FileFormat
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * §11.1 指标落盘：真文件系统上验证 JSONL 写入、读回与轮转。
 */
@RunWith(AndroidJUnit4::class)
class BenchmarkCollectorInstrumentedTest {

    private lateinit var collector: BenchmarkCollector

    @Before
    fun setUp() {
        collector = BenchmarkCollector(
            InstrumentationRegistry.getInstrumentation().targetContext,
        )
        collector.clear()
    }

    @After
    fun tearDown() {
        collector.clear()
    }

    private fun record(id: String, inputBytes: Long = 10L * 1024 * 1024) = BenchmarkRecord(
        taskId = id,
        inputFormat = FileFormat.JPG,
        outputFormat = FileFormat.WEBP,
        inputBytes = inputBytes,
        outputBytes = inputBytes / 4,
        elapsedMillis = 1_200,
        engine = EngineType.LIBVIPS,
        streamCopy = false,
        hardwareEncode = false,
        peakMemoryBytes = 64L * 1024 * 1024,
        succeeded = true,
    )

    @Test
    fun recordsAreAppendedAsJsonLines() {
        collector.record(record("a"))
        collector.record(record("b"))

        val all = collector.readAll()
        assertEquals(2, all.size)
        assertEquals("a", all[0].getString("taskId"))
        assertEquals("b", all[1].getString("taskId"))
    }

    @Test
    fun everySpecFieldIsPersisted() {
        collector.record(record("full"))
        val json = collector.readAll().single()

        // §11.1 清单：耗时 / 速度 / 峰值内存 / 体积 / 压缩率 / 引擎 / 硬件编码
        listOf(
            "taskId", "route", "inputFormat", "outputFormat", "inputBytes", "outputBytes",
            "elapsedMillis", "engine", "streamCopy", "hardwareEncode", "peakMemoryBytes",
            "bytesPerSecond", "reductionPercent", "succeeded", "recordedAt",
        ).forEach { key ->
            assertTrue("缺字段 $key", json.has(key))
        }
        assertEquals("JPG → WEBP", json.getString("route"))
        assertEquals(75, json.getInt("reductionPercent"))
        assertEquals("LIBVIPS", json.getString("engine"))
    }

    @Test
    fun memorySampleIsPositiveOnRealDevice() {
        // native heap + Java 堆已用，进程级；至少应为正数。
        assertTrue(collector.sampleMemory() > 0)
    }

    @Test
    fun fileRotatesInsteadOfGrowingForever() {
        // 写到超过 512KB 触发轮转；轮转后当前文件重新从少量记录开始。
        repeat(3_000) { index -> collector.record(record("bulk-$index")) }
        val after = collector.readAll()
        assertTrue("轮转后当前文件不应仍持有全部 3000 条", after.size < 3_000)
        assertTrue("轮转后仍应能继续写入", after.isNotEmpty())
    }

    @Test
    fun reportIncludesBothRotatedAndCurrentGenerations() {
        // 约 600–700KB：跨过一次 512KB 阈值，但不会触发第二次轮转。
        repeat(2_000) { index -> collector.record(record("report-$index")) }

        val reportRecords = collector.readReportRecords()
        assertEquals("报告应包含仍被保留的上一代与当前代", 2_000, reportRecords.size)
        assertEquals(2_000, reportRecords.map { it.taskId }.toSet().size)
    }

    @Test
    fun clearRemovesEverything() {
        collector.record(record("x"))
        collector.clear()
        assertEquals(0, collector.readAll().size)
    }

    @Test
    fun malformedLinesAreSkippedNotFatal() {
        collector.record(record("good"))
        // 直接往文件里塞坏行，模拟被中断的写入。
        val dir = java.io.File(
            InstrumentationRegistry.getInstrumentation().targetContext.filesDir,
            "benchmark",
        )
        java.io.File(dir, "records.jsonl").appendText("{not json\n")
        collector.record(record("after"))

        val all = collector.readAll()
        assertEquals("坏行应被跳过而非抛异常", 2, all.size)
    }

    @Test
    fun concurrentCollectorInstancesDoNotCorruptJsonLines() {
        // 批量转换会为每个任务创建 ConversionExecutor/Collector，实例锁不够；
        // 用多个实例并发写入，验证进程级共享锁保护了 append + rotate。
        val pool = java.util.concurrent.Executors.newFixedThreadPool(4)
        try {
            val writes = (0 until 200).map { index ->
                pool.submit {
                    BenchmarkCollector(
                        InstrumentationRegistry.getInstrumentation().targetContext,
                    ).record(record("parallel-$index"))
                }
            }
            writes.forEach { it.get(10, java.util.concurrent.TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }

        val all = collector.readAll()
        assertEquals(200, all.size)
        assertEquals(200, all.map { it.getString("taskId") }.toSet().size)
    }
}
