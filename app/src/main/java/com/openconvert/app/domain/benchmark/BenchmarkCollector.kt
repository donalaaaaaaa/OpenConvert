package com.openconvert.app.domain.benchmark

import android.content.Context
import android.os.Debug
import java.io.File
import org.json.JSONObject

/**
 * 采集并落盘性能指标（计划书 §11.1）。
 *
 * 写 JSONL 到 `filesDir/benchmark/records.jsonl`，一行一条，可用
 * `adb shell run-as com.openconvert.app cat files/benchmark/records.jsonl` 取出。
 * 选 JSONL 而非 Room：这是诊断数据，不参与业务查询，不值得再加一张表和一次迁移。
 *
 * 文件超过 [MAX_BYTES] 时整体轮转为 .1（只保留一代），避免长期使用把用户
 * 存储吃掉——诊断数据的价值随时间衰减，留最近一批就够。
 */
class BenchmarkCollector(context: Context) {

    private val directory = File(context.filesDir, "benchmark")
    private val file = File(directory, FILE_NAME)

    /**
     * 采样峰值内存。native heap 对 libvips/FFmpeg 有意义，Java 堆对 Bitmap
     * 路径有意义，取两者之和的观测最大值。
     *
     * 注意：这是**进程级**数字，不是单任务归因——UI、Room、WorkManager 都算在内。
     */
    fun sampleMemory(): Long {
        val runtime = Runtime.getRuntime()
        val javaUsed = runtime.totalMemory() - runtime.freeMemory()
        return Debug.getNativeHeapAllocatedSize() + javaUsed
    }

    fun record(record: BenchmarkRecord) {
        runCatching {
            synchronized(IO_LOCK) {
                directory.mkdirs()
                rotateIfNeeded()
                file.appendText(encode(record) + "\n")
            }
        }
    }

    /** 读回全部记录，供测试与诊断使用。 */
    fun readAll(): List<JSONObject> {
        return synchronized(IO_LOCK) {
            readJsonLines(file)
        }
    }

    /**
     * 报告导出读取上一代轮转文件 + 当前文件，并按记录时间排序。
     * [readAll] 保留“当前代”语义供诊断；报告不能悄悄漏掉仍被保留的上一代。
     */
    fun readReportRecords(): List<BenchmarkRecord> = synchronized(IO_LOCK) {
        val previous = File(directory, "$FILE_NAME.1")
        (readJsonLines(previous) + readJsonLines(file))
            .mapNotNull(BenchmarkRecord::fromJson)
            .sortedBy { it.recordedAt }
    }

    fun reportRecordCount(): Int = readReportRecords().size

    fun clear() {
        runCatching {
            synchronized(IO_LOCK) {
                file.delete()
                File(directory, "$FILE_NAME.1").delete()
            }
        }
    }

    private fun rotateIfNeeded() {
        if (!file.exists() || file.length() < MAX_BYTES) return
        val previous = File(directory, "$FILE_NAME.1")
        previous.delete()
        file.renameTo(previous)
    }

    private fun readJsonLines(source: File): List<JSONObject> {
        if (!source.exists()) return emptyList()
        return source.readLines()
            .filter { it.isNotBlank() }
            .mapNotNull { line -> runCatching { JSONObject(line) }.getOrNull() }
    }

    private fun encode(record: BenchmarkRecord): String = JSONObject().apply {
        put("taskId", record.taskId)
        put("route", record.route)
        put("inputFormat", record.inputFormat.name)
        put("outputFormat", record.outputFormat.name)
        put("inputBytes", record.inputBytes)
        put("outputBytes", record.outputBytes)
        put("elapsedMillis", record.elapsedMillis)
        put("engine", record.engine?.name ?: JSONObject.NULL)
        put("streamCopy", record.streamCopy)
        put("hardwareEncode", record.hardwareEncode)
        put("peakMemoryBytes", record.peakMemoryBytes ?: JSONObject.NULL)
        put("bytesPerSecond", record.bytesPerSecond ?: JSONObject.NULL)
        put("reductionPercent", record.reductionPercent ?: JSONObject.NULL)
        put("succeeded", record.succeeded)
        put("recordedAt", record.recordedAt)
    }.toString()

    companion object {
        private const val FILE_NAME = "records.jsonl"
        private const val MAX_BYTES = 512L * 1024

        /**
         * ConversionExecutor 按任务创建 collector；批量转换时会有多个实例并发写入。
         * 锁必须是进程级共享对象，实例锁无法阻止 JSON 行交错或轮转竞态。
         */
        private val IO_LOCK = Any()
    }
}
