package com.openconvert.app.data.repository

import com.openconvert.app.data.local.BatchJobDao
import com.openconvert.app.data.local.BatchJobEntity
import com.openconvert.app.data.local.ConversionDao
import com.openconvert.app.data.local.toDomain
import com.openconvert.app.data.local.toEntity
import com.openconvert.app.domain.model.BatchJob
import com.openconvert.app.domain.model.BatchJobStatus
import com.openconvert.app.domain.model.ConversionTask
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ConversionHistoryRepository(
    private val dao: ConversionDao,
    private val batchJobDao: BatchJobDao,
) {
    val history: Flow<List<ConversionTask>> = dao.observeAll().map { rows ->
        rows.map { it.toDomain() }
    }

    fun observe(id: String): Flow<ConversionTask?> = dao.observeById(id).map { it?.toDomain() }

    suspend fun get(id: String): ConversionTask? = dao.getById(id)?.toDomain()

    suspend fun findActive(): List<ConversionTask> = dao.findActive().map { it.toDomain() }

    suspend fun save(task: ConversionTask) = dao.upsert(task.toEntity())
    suspend fun delete(task: ConversionTask) = dao.delete(task.toEntity())
    suspend fun clear() = dao.clear()
    suspend fun clearFinished() = dao.clearFinished()
    suspend fun deleteFinished(ids: Collection<String>) {
        if (ids.isNotEmpty()) dao.deleteFinishedByIds(ids.toList())
    }

    // ---- Batch ----

    fun observeBatch(id: String): Flow<BatchJob?> = batchJobDao.observeById(id).map { it?.toDomain() }

    fun observeBatchTasks(batchId: String): Flow<List<ConversionTask>> =
        batchJobDao.observeTasksByBatch(batchId).map { rows -> rows.map { it.toDomain() } }

    suspend fun batchTasks(batchId: String): List<ConversionTask> =
        batchJobDao.getTasksByBatch(batchId).map { it.toDomain() }

    suspend fun getBatch(id: String): BatchJob? = batchJobDao.getById(id)?.toDomain()

    suspend fun saveBatch(job: BatchJob) = batchJobDao.upsert(job.toEntity())
}

fun BatchJob.toEntity() = BatchJobEntity(
    id = id,
    name = name,
    status = status.name,
    total = total,
    done = done,
    failed = failed,
    createdAt = createdAt,
    finishedAt = finishedAt,
    settingsJson = settingsJson,
)

fun BatchJobEntity.toDomain() = BatchJob(
    id = id,
    name = name,
    status = runCatching { BatchJobStatus.valueOf(status) }.getOrDefault(BatchJobStatus.RUNNING),
    total = total,
    done = done,
    failed = failed,
    createdAt = createdAt,
    finishedAt = finishedAt,
    settingsJson = settingsJson,
)
