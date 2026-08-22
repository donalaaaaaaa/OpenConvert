package com.openconvert.app.domain.benchmark

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class BenchmarkReportFormat(
    val extension: String,
    val mimeType: String,
) {
    MARKDOWN("md", "text/markdown"),
    CSV("csv", "text/csv"),
}

/** 纯文本报告生成器：纯 JVM 可测，不依赖 Android 或文件系统。 */
object BenchmarkReport {

    fun markdown(
        records: List<BenchmarkRecord>,
        generatedAt: Long = System.currentTimeMillis(),
    ): String = buildString {
        val successful = records.filter { it.succeeded }
        appendLine("# OpenConvert Benchmark Report")
        appendLine()
        appendLine("生成时间：${timestamp(generatedAt)}")
        appendLine()
        appendLine("- 总记录：${records.size}")
        appendLine("- 成功：${successful.size}")
        appendLine("- 失败：${records.size - successful.size}")
        appendLine("- CPU：未采集（Android 只能可靠取得整进程数据，无法按并行任务归因）")
        appendLine("- 峰值内存：进程级 Java 已用堆 + native heap 的周期采样值")
        appendLine()
        appendLine("## 路由汇总")
        appendLine()
        appendLine("| 路由 | 引擎 | 次数 | 成功 | 平均耗时 | 平均吞吐 | 平均峰值内存 | 平均压缩率 |")
        appendLine("|---|---|---:|---:|---:|---:|---:|---:|")
        records.groupBy { it.inputFormat to (it.outputFormat to it.engine) }
            .toSortedMap(compareBy { "${it.first.name}:${it.second.first.name}:${it.second.second?.name}" })
            .forEach { (_, group) ->
                val successes = group.filter { it.succeeded }
                append("| ${escapeMarkdown(group.first().route)} ")
                append("| ${group.first().engine?.name ?: "—"} ")
                append("| ${group.size} ")
                append("| ${successes.size} ")
                append("| ${average(successes.map { it.elapsedMillis })?.let(::formatDuration) ?: "—"} ")
                append("| ${average(successes.mapNotNull { it.bytesPerSecond })?.let(::formatRate) ?: "—"} ")
                append("| ${average(successes.mapNotNull { it.peakMemoryBytes })?.let(::formatBytes) ?: "—"} ")
                appendLine("| ${average(successes.mapNotNull { it.reductionPercent?.toLong() })?.let { "$it%" } ?: "—"} |")
            }
        if (records.isEmpty()) appendLine("| — | — | 0 | 0 | — | — | — | — |")

        appendLine()
        appendLine("## 单次记录")
        appendLine()
        appendLine("| 时间 | 路由 | 引擎 | 耗时 | 吞吐 | 输入 | 输出 | 压缩率 | 流拷贝 | 硬编 | 峰值内存 | 结果 |")
        appendLine("|---|---|---|---:|---:|---:|---:|---:|---|---|---:|---|")
        records.forEach { record ->
            append("| ${timestamp(record.recordedAt)} ")
            append("| ${escapeMarkdown(record.route)} ")
            append("| ${record.engine?.name ?: "—"} ")
            append("| ${formatDuration(record.elapsedMillis)} ")
            append("| ${record.bytesPerSecond?.let(::formatRate) ?: "—"} ")
            append("| ${formatBytes(record.inputBytes)} ")
            append("| ${record.outputBytes.takeIf { it > 0 }?.let(::formatBytes) ?: "—"} ")
            append("| ${record.reductionPercent?.let { "$it%" } ?: "—"} ")
            append("| ${yesNo(record.streamCopy)} ")
            append("| ${yesNo(record.hardwareEncode)} ")
            append("| ${record.peakMemoryBytes?.let(::formatBytes) ?: "—"} ")
            appendLine("| ${if (record.succeeded) "成功" else "失败"} |")
        }
    }

    /** CSV 使用原始数值，便于 Excel / Python / R 二次分析；BOM 确保 Excel 正确识别 UTF-8。 */
    fun csv(records: List<BenchmarkRecord>): String = buildString {
        append('\uFEFF')
        appendLine(
            listOf(
                "recordedAt", "taskId", "route", "inputFormat", "outputFormat", "engine",
                "inputBytes", "outputBytes", "elapsedMillis", "bytesPerSecond",
                "reductionPercent", "streamCopy", "hardwareEncode", "peakMemoryBytes", "succeeded",
            ).joinToString(","),
        )
        records.forEach { record ->
            appendLine(
                listOf(
                    timestamp(record.recordedAt),
                    record.taskId,
                    record.route,
                    record.inputFormat.name,
                    record.outputFormat.name,
                    record.engine?.name.orEmpty(),
                    record.inputBytes,
                    record.outputBytes,
                    record.elapsedMillis,
                    record.bytesPerSecond.orEmpty(),
                    record.reductionPercent.orEmpty(),
                    record.streamCopy,
                    record.hardwareEncode,
                    record.peakMemoryBytes.orEmpty(),
                    record.succeeded,
                ).joinToString(",") { csvCell(it.toString()) },
            )
        }
    }

    private fun average(values: List<Long>): Long? =
        values.takeIf { it.isNotEmpty() }?.let { it.sum() / it.size }

    private fun timestamp(millis: Long): String = SimpleDateFormat(
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        Locale.US,
    ).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(millis.coerceAtLeast(0L)))

    private fun formatDuration(millis: Long): String = when {
        millis >= 60_000 -> String.format(Locale.US, "%.1f min", millis / 60_000.0)
        millis >= 1_000 -> String.format(Locale.US, "%.2f s", millis / 1_000.0)
        else -> "$millis ms"
    }

    private fun formatRate(bytesPerSecond: Long): String = "${formatBytes(bytesPerSecond)}/s"

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1024L * 1024 * 1024 -> String.format(Locale.US, "%.2f GiB", bytes / (1024.0 * 1024 * 1024))
        bytes >= 1024L * 1024 -> String.format(Locale.US, "%.2f MiB", bytes / (1024.0 * 1024))
        bytes >= 1024L -> String.format(Locale.US, "%.2f KiB", bytes / 1024.0)
        else -> "$bytes B"
    }

    private fun escapeMarkdown(value: String): String = value.replace("|", "\\|")
    private fun yesNo(value: Boolean): String = if (value) "是" else "否"
    private fun csvCell(value: String): String = "\"${value.replace("\"", "\"\"")}\""
    private fun Long?.orEmpty(): String = this?.toString().orEmpty()
    private fun Int?.orEmpty(): String = this?.toString().orEmpty()
}
