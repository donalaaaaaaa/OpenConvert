package com.openconvert.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

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
)
