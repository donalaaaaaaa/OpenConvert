package com.openconvert.app.domain.benchmark

import com.openconvert.app.domain.engine.EngineType
import com.openconvert.app.domain.model.FileFormat
import org.json.JSONObject

/**
 * 统一性能指标（计划书 §十一）。
 *
 * §11.1 要求记录：总耗时 / 转换速度 / 峰值内存 / CPU / 输入体积 / 输出体积 /
 * 压缩率 / 使用引擎 / 硬件编码状态。
 *
 * 诚实说明：
 * - **CPU 使用率不采集**。Android 8+ 起 `/proc/self/stat` 对应用自身仍可读，
 *   但读到的是整进程（含 UI、WorkManager、Room）而非转换本身，归因不可信。
 *   宁可不报也不报一个误导性的数字。
 * - **峰值内存**取 `Debug.getNativeHeapAllocatedSize` + Java 堆已用量的最大值，
 *   同样是进程级；对 libvips/FFmpeg 这类 native 引擎是有意义的量级参考，
 *   但不是精确的单任务归因。
 */
data class BenchmarkRecord(
    val taskId: String,
    val inputFormat: FileFormat,
    val outputFormat: FileFormat,
    val inputBytes: Long,
    val outputBytes: Long,
    val elapsedMillis: Long,
    val engine: EngineType?,
    /** true = 未重新编码（Remux / 流拷贝）。 */
    val streamCopy: Boolean,
    /** true = 使用了硬件编码器。 */
    val hardwareEncode: Boolean,
    val peakMemoryBytes: Long?,
    val succeeded: Boolean,
    val recordedAt: Long = System.currentTimeMillis(),
) {
    /** 吞吐：输入体积 / 耗时。0 耗时（缓存命中）返回 null。 */
    val bytesPerSecond: Long?
        get() = if (elapsedMillis > 0 && inputBytes > 0) {
            (inputBytes * 1000.0 / elapsedMillis).toLong()
        } else {
            null
        }

    /**
     * 压缩率：输出相对输入缩小的百分比。
     * 正值 = 变小；负值 = 变大（如 JPG→PNG 无损化）。
     */
    val reductionPercent: Int?
        get() = if (inputBytes > 0 && outputBytes > 0) {
            (((inputBytes - outputBytes).toDouble() / inputBytes) * 100).toInt()
        } else {
            null
        }

    val route: String get() = "${inputFormat.displayName} → ${outputFormat.displayName}"

    companion object {
        /** 向后兼容地读回 JSONL；未知枚举或缺必填字段时跳过该行。 */
        fun fromJson(json: JSONObject): BenchmarkRecord? = runCatching {
            BenchmarkRecord(
                taskId = json.getString("taskId"),
                inputFormat = FileFormat.valueOf(json.getString("inputFormat")),
                outputFormat = FileFormat.valueOf(json.getString("outputFormat")),
                inputBytes = json.getLong("inputBytes"),
                outputBytes = json.getLong("outputBytes"),
                elapsedMillis = json.getLong("elapsedMillis"),
                engine = json.takeUnless { it.isNull("engine") }
                    ?.getString("engine")
                    ?.let(EngineType::valueOf),
                streamCopy = json.optBoolean("streamCopy"),
                hardwareEncode = json.optBoolean("hardwareEncode"),
                peakMemoryBytes = json.takeUnless { it.isNull("peakMemoryBytes") }
                    ?.getLong("peakMemoryBytes"),
                succeeded = json.getBoolean("succeeded"),
                recordedAt = json.optLong("recordedAt", 0L),
            )
        }.getOrNull()
    }
}
