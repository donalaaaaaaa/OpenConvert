package com.openconvert.app.domain.model

enum class BatchJobStatus {
    PENDING,
    RUNNING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED,
}

/**
 * 批量转换任务聚合。
 * settingsJson 保存 BatchSettings 的 JSON 序列化（输入 URI 列表、目标格式、质量、分辨率、输出目录）。
 */
data class BatchJob(
    val id: String,
    val name: String,
    val status: BatchJobStatus,
    val total: Int,
    val done: Int,
    val failed: Int,
    val createdAt: Long,
    val finishedAt: Long? = null,
    val settingsJson: String = "",
) {
    val progressPercent: Int
        get() = if (total == 0) 0 else ((done + failed) * 100 / total).coerceIn(0, 100)
}

data class BatchSettings(
    val sourceUris: List<String> = emptyList(),
    val sourceNames: List<String> = emptyList(),
    val sourceFormats: List<String> = emptyList(),
    val targetFormat: String = "",
    val quality: String = "BALANCED",
    val resolution: String = "ORIGINAL",
    val outputTreeUri: String = "",
)
