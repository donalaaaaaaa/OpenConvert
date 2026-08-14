package com.openconvert.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BatchJobDao {
    @Query("SELECT * FROM batch_jobs WHERE id = :id")
    fun observeById(id: String): Flow<BatchJobEntity?>

    @Query("SELECT * FROM batch_jobs WHERE id = :id")
    suspend fun getById(id: String): BatchJobEntity?

    @Query("SELECT * FROM batch_jobs ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<BatchJobEntity>>

    @Query("SELECT * FROM conversion_tasks WHERE batchId = :batchId")
    fun observeTasksByBatch(batchId: String): Flow<List<ConversionEntity>>

    @Query("SELECT * FROM conversion_tasks WHERE batchId = :batchId")
    suspend fun getTasksByBatch(batchId: String): List<ConversionEntity>

    @Upsert
    suspend fun upsert(job: BatchJobEntity)
}
