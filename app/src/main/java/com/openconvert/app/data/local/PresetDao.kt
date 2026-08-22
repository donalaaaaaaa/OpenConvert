package com.openconvert.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PresetDao {

    @Query("SELECT * FROM conversion_presets ORDER BY createdAt ASC")
    fun getAllPresetsFlow(): Flow<List<PresetEntity>>

    @Query("SELECT * FROM conversion_presets WHERE category = :category ORDER BY createdAt ASC")
    fun getPresetsByCategoryFlow(category: String): Flow<List<PresetEntity>>

    @Query("SELECT * FROM conversion_presets ORDER BY createdAt ASC")
    suspend fun getAll(): List<PresetEntity>

    @Query("SELECT COUNT(*) FROM conversion_presets")
    suspend fun count(): Int

    @Query("SELECT * FROM conversion_presets WHERE id = :id")
    suspend fun getById(id: String): PresetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(preset: PresetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(presets: List<PresetEntity>)

    /** 只允许删除用户自定义预设——内置预设不可删。 */
    @Query("DELETE FROM conversion_presets WHERE id = :id AND isBuiltIn = 0")
    suspend fun deleteCustomById(id: String): Int

    @Query("DELETE FROM conversion_presets WHERE id = :id")
    suspend fun deleteById(id: String)

    /** 同类别里清除默认标记，配合 setDefault 使用。 */
    @Query("UPDATE conversion_presets SET isDefault = 0 WHERE category = :category")
    suspend fun clearDefaultFor(category: String)

    @Query("UPDATE conversion_presets SET isDefault = 1 WHERE id = :id")
    suspend fun markDefault(id: String)
}
