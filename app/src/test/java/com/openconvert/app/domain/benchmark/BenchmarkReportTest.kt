package com.openconvert.app.domain.benchmark

import com.openconvert.app.domain.engine.EngineType
import com.openconvert.app.domain.model.FileFormat
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BenchmarkReportTest {

    private fun record(
        taskId: String,
        elapsedMillis: Long = 1_000,
        succeeded: Boolean = true,
        recordedAt: Long = 0,
    ) = BenchmarkRecord(
        taskId = taskId,
        inputFormat = FileFormat.PNG,
        outputFormat = FileFormat.JPG,
        inputBytes = 1_000,
        outputBytes = if (succeeded) 500 else 0,
        elapsedMillis = elapsedMillis,
        engine = EngineType.LIBVIPS,
        streamCopy = false,
        hardwareEncode = false,
        peakMemoryBytes = 10L * 1024 * 1024,
        succeeded = succeeded,
        recordedAt = recordedAt,
    )

    @Test
    fun `markdown contains aggregate and individual results`() {
        val report = BenchmarkReport.markdown(
            records = listOf(
                record("a", elapsedMillis = 1_000, recordedAt = 1_000),
                record("b", elapsedMillis = 2_000, recordedAt = 2_000),
                record("failed", elapsedMillis = 100, succeeded = false, recordedAt = 3_000),
            ),
            generatedAt = 0,
        )

        assertTrue(report.contains("总记录：3"))
        assertTrue(report.contains("成功：2"))
        assertTrue(report.contains("PNG → JPG"))
        assertTrue(report.contains("LIBVIPS"))
        assertTrue("平均耗时只统计成功记录", report.contains("1.50 s"))
        assertTrue(report.contains("失败"))
        assertTrue(report.contains("CPU：未采集"))
    }

    @Test
    fun `csv has utf8 bom raw fields and escaped cells`() {
        val csv = BenchmarkReport.csv(listOf(record("task,\"quoted\"")))

        assertEquals('\uFEFF', csv.first())
        assertTrue(csv.contains("bytesPerSecond"))
        assertTrue(csv.contains("\"task,\"\"quoted\"\"\""))
        assertTrue(csv.contains("\"PNG → JPG\""))
    }

    @Test
    fun `json decode accepts nullable metrics`() {
        val json = JSONObject().apply {
            put("taskId", "decoded")
            put("inputFormat", "PNG")
            put("outputFormat", "JPG")
            put("inputBytes", 0)
            put("outputBytes", 0)
            put("elapsedMillis", 5)
            put("engine", JSONObject.NULL)
            put("streamCopy", false)
            put("hardwareEncode", false)
            put("peakMemoryBytes", JSONObject.NULL)
            put("succeeded", false)
            put("recordedAt", 7)
        }

        val decoded = BenchmarkRecord.fromJson(json)!!
        assertEquals("decoded", decoded.taskId)
        assertNull(decoded.engine)
        assertNull(decoded.peakMemoryBytes)
        assertEquals(7L, decoded.recordedAt)
    }

    @Test
    fun `json decode skips unknown future enum safely`() {
        val json = JSONObject().apply {
            put("taskId", "bad")
            put("inputFormat", "FUTURE_FORMAT")
            put("outputFormat", "JPG")
            put("inputBytes", 1)
            put("outputBytes", 1)
            put("elapsedMillis", 1)
            put("engine", JSONObject.NULL)
            put("succeeded", true)
        }

        assertNull(BenchmarkRecord.fromJson(json))
    }
}
