package com.openconvert.app.domain.benchmark

import android.content.Context
import android.net.Uri
import java.io.IOException

data class BenchmarkExportResult(
    val recordCount: Int,
    val format: BenchmarkReportFormat,
)

/** SAF 报告导出；不申请存储权限，用户明确选择目标文件。 */
class BenchmarkReportExporter(private val context: Context) {
    private val collector = BenchmarkCollector(context)

    fun recordCount(): Int = collector.reportRecordCount()

    fun export(outputUri: Uri, format: BenchmarkReportFormat): Result<BenchmarkExportResult> = runCatching {
        val records = collector.readReportRecords()
        require(records.isNotEmpty()) { "暂无可导出的 Benchmark 记录" }
        val report = when (format) {
            BenchmarkReportFormat.MARKDOWN -> BenchmarkReport.markdown(records)
            BenchmarkReportFormat.CSV -> BenchmarkReport.csv(records)
        }
        val output = context.contentResolver.openOutputStream(outputUri, "wt")
            ?: throw IOException("无法写入所选文件")
        output.bufferedWriter(Charsets.UTF_8).use { it.write(report) }
        BenchmarkExportResult(records.size, format)
    }
}
