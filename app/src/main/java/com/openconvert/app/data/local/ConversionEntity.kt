package com.openconvert.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.openconvert.app.domain.engine.EngineType
import com.openconvert.app.domain.model.ConversionKind
import com.openconvert.app.domain.model.ConversionPayloadCodec
import com.openconvert.app.domain.model.ConversionStatus
import com.openconvert.app.domain.model.ConversionTask
import com.openconvert.app.domain.model.FileFormat
import com.openconvert.app.domain.model.QualityPreset
import com.openconvert.app.domain.model.ResolutionPreset

@Entity(tableName = "conversion_tasks")
data class ConversionEntity(
    @PrimaryKey val id: String,
    val sourceUri: String,
    val sourceName: String,
    val sourceFormat: String,
    val targetFormat: String,
    val outputUri: String?,
    val fileSize: Long,
    val outputSize: Long?,
    val quality: String,
    val resolution: String,
    val progress: Int,
    val status: String,
    val createdAt: Long,
    val completedAt: Long?,
    val kind: String,
    val payloadJson: String?,
    val errorMessage: String?,
    val outputName: String?,
    val batchId: String? = null,
    val actualEngine: String? = null,
)

fun ConversionEntity.toDomain() = ConversionTask(
    id = id,
    sourceUri = sourceUri,
    sourceName = sourceName,
    sourceFormat = FileFormat.valueOf(sourceFormat),
    targetFormat = FileFormat.valueOf(targetFormat),
    outputUri = outputUri,
    fileSize = fileSize,
    outputSize = outputSize,
    quality = QualityPreset.valueOf(quality),
    resolution = ResolutionPreset.valueOf(resolution),
    progress = progress,
    status = ConversionStatus.valueOf(status),
    createdAt = createdAt,
    completedAt = completedAt,
    kind = runCatching { ConversionKind.valueOf(kind) }.getOrDefault(ConversionKind.SINGLE),
    payload = ConversionPayloadCodec.decode(payloadJson),
    errorMessage = errorMessage,
    outputName = outputName,
    actualEngine = actualEngine?.let { stored ->
        runCatching { EngineType.valueOf(stored) }.getOrNull()
    },
)

fun ConversionTask.toEntity() = ConversionEntity(
    id = id,
    sourceUri = sourceUri,
    sourceName = sourceName,
    sourceFormat = sourceFormat.name,
    targetFormat = targetFormat.name,
    outputUri = outputUri,
    fileSize = fileSize,
    outputSize = outputSize,
    quality = quality.name,
    resolution = resolution.name,
    progress = progress,
    status = status.name,
    createdAt = createdAt,
    completedAt = completedAt,
    kind = kind.name,
    payloadJson = ConversionPayloadCodec.encode(payload),
    errorMessage = errorMessage,
    outputName = outputName,
    batchId = payload.batchId,
    actualEngine = actualEngine?.name,
)
