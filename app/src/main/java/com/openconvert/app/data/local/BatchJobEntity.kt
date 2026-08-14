package com.openconvert.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 批量转换任务（BatchJob）。
 * 一个 BatchJob 对应一次批量操作（如 100 张 JPG → WEBP），
 * 每个文件是一个独立 ConversionTask（通过 batchId 关联）。
 */
@Entity(tableName = "batch_jobs")
data class BatchJobEntity(
    @PrimaryKey val id: String,
    val name: String,
    val status: String, // PENDING / RUNNING / PAUSED / COMPLETED / FAILED / CANCELLED
    val total: Int,
    val done: Int,
    val failed: Int,
    val createdAt: Long,
    val finishedAt: Long?,
    val settingsJson: String,
)
