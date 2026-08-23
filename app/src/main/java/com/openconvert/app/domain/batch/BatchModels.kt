package com.openconvert.app.domain.batch

enum class DuplicateFileStrategy(val displayName: String) {
    RENAME("自动重命名 (推荐)"),
    SKIP("跳过已存在文件"),
    OVERWRITE("覆盖原文件");

    val labelRes: Int
        get() = when (this) {
            RENAME -> com.openconvert.app.R.string.dup_rename
            SKIP -> com.openconvert.app.R.string.dup_skip
            OVERWRITE -> com.openconvert.app.R.string.dup_overwrite
        }
}

data class BatchSummaryReport(
    val totalCount: Int,
    val successCount: Int,
    val failedCount: Int,
    val totalOriginalSizeBytes: Long,
    val totalOutputSizeBytes: Long,
    val reductionPercent: Double,
    val durationMs: Long,
)
