package com.openconvert.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.openconvert.app.domain.model.FileCategory
import com.openconvert.app.domain.model.FileFormat
import com.openconvert.app.domain.model.QualityPreset
import com.openconvert.app.domain.model.ResolutionPreset
import com.openconvert.app.domain.preset.Preset

@Entity(tableName = "conversion_presets")
data class PresetEntity(
    @PrimaryKey val id: String,
    val category: String,
    val name: String,
    val description: String,
    val targetFormat: String,
    val quality: String,
    val resolution: String,
    val stripMetadata: Boolean,
    val isDefault: Boolean,
    val isBuiltIn: Boolean,
    val createdAt: Long,
    // Room v6：§8.1 的尺寸约束（最长边 / 固定尺寸 / 裁剪比例）。
    val longestEdgePx: Int? = null,
    val fixedWidthPx: Int? = null,
    val fixedHeightPx: Int? = null,
    val cropAspect: String = "free",
)

fun PresetEntity.toDomain(): Preset = Preset(
    id = id,
    category = runCatching { FileCategory.valueOf(category) }.getOrDefault(FileCategory.UNKNOWN),
    name = name,
    description = description,
    targetFormat = runCatching { FileFormat.valueOf(targetFormat) }
        .getOrDefault(FileFormat.UNKNOWN),
    quality = runCatching { QualityPreset.valueOf(quality) }.getOrDefault(QualityPreset.BALANCED),
    resolution = runCatching { ResolutionPreset.valueOf(resolution) }
        .getOrDefault(ResolutionPreset.ORIGINAL),
    stripMetadata = stripMetadata,
    longestEdgePx = longestEdgePx,
    fixedWidthPx = fixedWidthPx,
    fixedHeightPx = fixedHeightPx,
    cropAspect = cropAspect,
    isDefault = isDefault,
    isBuiltIn = isBuiltIn,
    createdAt = createdAt,
)

fun Preset.toEntity(): PresetEntity = PresetEntity(
    id = id,
    category = category.name,
    name = name,
    description = description,
    targetFormat = targetFormat.name,
    quality = quality.name,
    resolution = resolution.name,
    stripMetadata = stripMetadata,
    isDefault = isDefault,
    isBuiltIn = isBuiltIn,
    createdAt = createdAt,
    longestEdgePx = longestEdgePx,
    fixedWidthPx = fixedWidthPx,
    fixedHeightPx = fixedHeightPx,
    cropAspect = cropAspect,
)
