package com.openconvert.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversionDao {
    @Query("SELECT * FROM conversion_tasks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ConversionEntity>>

    @Query("SELECT * FROM conversion_tasks WHERE id = :id")
    fun observeById(id: String): Flow<ConversionEntity?>

    @Query("SELECT * FROM conversion_tasks WHERE id = :id")
    suspend fun getById(id: String): ConversionEntity?

    @Query("SELECT * FROM conversion_tasks WHERE status IN ('PENDING', 'RUNNING') ORDER BY createdAt DESC")
    suspend fun findActive(): List<ConversionEntity>

    @Upsert
    suspend fun upsert(task: ConversionEntity)

    @Delete
    suspend fun delete(task: ConversionEntity)

    @Query("DELETE FROM conversion_tasks")
    suspend fun clear()

    @Query("DELETE FROM conversion_tasks WHERE status NOT IN ('PENDING', 'RUNNING')")
    suspend fun clearFinished()

    @Query("DELETE FROM conversion_tasks WHERE id IN (:ids) AND status NOT IN ('PENDING', 'RUNNING')")
    suspend fun deleteFinishedByIds(ids: List<String>)
}
