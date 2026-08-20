package com.openconvert.app.data.repository

import com.openconvert.app.data.local.PresetDao
import com.openconvert.app.data.local.toDomain
import com.openconvert.app.data.local.toEntity
import com.openconvert.app.domain.model.FileCategory
import com.openconvert.app.domain.preset.Preset
import com.openconvert.app.domain.preset.PresetRepository
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 预设持久化（计划书 §八）。
 *
 * 内置预设首次启动写库，之后与自定义预设一视同仁地从库里读——
 * UI 不需要区分来源，只用 [Preset.isBuiltIn] 决定能否删除。
 * 这样用户改了内置预设的默认标记也能保留。
 */
class PresetStore(private val dao: PresetDao) {

    val presets: Flow<List<Preset>> =
        dao.getAllPresetsFlow().map { rows -> rows.map { it.toDomain() } }

    fun presetsFor(category: FileCategory): Flow<List<Preset>> =
        dao.getPresetsByCategoryFlow(category.name).map { rows -> rows.map { it.toDomain() } }

    /**
     * 首次启动播种内置预设。空库才写，避免每次启动覆盖用户对内置项的调整。
     */
    suspend fun seedIfEmpty() {
        if (dao.count() > 0) return
        val now = System.currentTimeMillis()
        dao.insertAll(
            PresetRepository.BUILT_IN_PRESETS.mapIndexed { index, preset ->
                // createdAt 递增以保证内置项显示顺序稳定。
                preset.copy(createdAt = now + index).toEntity()
            },
        )
    }

    suspend fun get(id: String): Preset? = dao.getById(id)?.toDomain()

    /** 保存用户自定义预设；id 为空时生成新 id。 */
    suspend fun saveCustom(preset: Preset): Preset {
        val prepared = preset.copy(
            id = preset.id.ifBlank { "custom_${UUID.randomUUID()}" },
            isBuiltIn = false,
            createdAt = if (preset.createdAt > 0) preset.createdAt else System.currentTimeMillis(),
        )
        dao.insertOrUpdate(prepared.toEntity())
        return prepared
    }

    /**
     * 删除预设。内置预设受 SQL 层保护（`isBuiltIn = 0` 条件），
     * 返回 false 表示未删除——UI 据此提示"内置预设不可删除"。
     */
    suspend fun deleteCustom(id: String): Boolean = dao.deleteCustomById(id) > 0

    /** 设为同类别默认（同类别内互斥）。 */
    suspend fun setDefault(preset: Preset) {
        dao.clearDefaultFor(preset.category.name)
        dao.markDefault(preset.id)
    }
}
