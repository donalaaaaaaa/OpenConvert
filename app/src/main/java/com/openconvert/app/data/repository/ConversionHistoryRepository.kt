package com.openconvert.app.data.repository

import com.openconvert.app.data.local.ConversionDao
import com.openconvert.app.data.local.toDomain
import com.openconvert.app.data.local.toEntity
import com.openconvert.app.domain.model.ConversionTask
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ConversionHistoryRepository(private val dao: ConversionDao) {
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
}
