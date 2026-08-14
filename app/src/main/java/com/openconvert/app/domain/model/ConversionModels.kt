package com.openconvert.app.domain.model

enum class ConversionStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED,
}

enum class QualityPreset(val label: String) {
    HIGH("高质量"),
    BALANCED("平衡"),
    SMALL("节省空间");

    val compressionQuality: Int
        get() = when (this) {
            HIGH -> 95
            BALANCED -> 82
            SMALL -> 65
        }
}

enum class ResolutionPreset(val label: String, val scalePercent: Int) {
    ORIGINAL("原始尺寸", 100),
    MEDIUM("缩小至 75%", 75),
    SMALL("缩小至 50%", 50),
}

data class ConversionTask(
    val id: String,
    val sourceUri: String,
    val sourceName: String,
    val sourceFormat: FileFormat,
    val targetFormat: FileFormat,
    val outputUri: String? = null,
    val fileSize: Long = 0,
    val outputSize: Long? = null,
    val quality: QualityPreset = QualityPreset.BALANCED,
    val resolution: ResolutionPreset = ResolutionPreset.ORIGINAL,
    val progress: Int = 0,
    val status: ConversionStatus = ConversionStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val kind: ConversionKind = ConversionKind.SINGLE,
    val payload: ConversionPayload = ConversionPayload(),
    val errorMessage: String? = null,
    val outputName: String? = null,
)

sealed interface ConversionResult {
    data class Success(val outputUri: String, val outputSize: Long) : ConversionResult
    data class Failure(val message: String, val cause: Throwable? = null) : ConversionResult
    data object Cancelled : ConversionResult
}
