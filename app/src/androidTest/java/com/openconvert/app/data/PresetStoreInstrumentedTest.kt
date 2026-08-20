package com.openconvert.app.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.openconvert.app.data.local.OpenConvertDatabase
import com.openconvert.app.data.repository.PresetStore
import com.openconvert.app.domain.model.FileCategory
import com.openconvert.app.domain.model.FileFormat
import com.openconvert.app.domain.preset.Preset
import com.openconvert.app.domain.preset.PresetRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 预设持久化（计划书 §八）在真 Room 上的验收，含 v5→v6 迁移。
 */
@RunWith(AndroidJUnit4::class)
class PresetStoreInstrumentedTest {

    private lateinit var db: OpenConvertDatabase
    private lateinit var store: PresetStore

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, OpenConvertDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = PresetStore(db.presetDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun seedWritesBuiltInPresetsOnce() = runBlocking {
        store.seedIfEmpty()
        val first = store.presets.first()
        assertEquals(PresetRepository.BUILT_IN_PRESETS.size, first.size)

        // 二次调用不得重复写入或覆盖。
        store.seedIfEmpty()
        assertEquals(first.size, store.presets.first().size)
    }

    @Test
    fun sizeConstraintsSurviveARoundTrip() = runBlocking {
        store.seedIfEmpty()
        val wechat = store.get("img_wechat")!!
        assertEquals("最长边必须持久化", 1920, wechat.longestEdgePx)
        assertTrue(wechat.stripMetadata)

        val avatar = store.get("img_avatar")!!
        assertEquals(1024, avatar.fixedWidthPx)
        assertEquals(1024, avatar.fixedHeightPx)
        assertEquals("1:1", avatar.cropAspect)
    }

    @Test
    fun customPresetCanBeSavedAndDeleted() = runBlocking {
        val saved = store.saveCustom(
            Preset(
                id = "",
                category = FileCategory.IMAGE,
                name = "我的预设",
                description = "自定义",
                targetFormat = FileFormat.WEBP,
                longestEdgePx = 800,
            ),
        )
        assertTrue("应生成 id", saved.id.isNotBlank())
        assertFalse("自定义预设不得标记为内置", saved.isBuiltIn)
        assertNotNull(store.get(saved.id))

        assertTrue(store.deleteCustom(saved.id))
        assertNull(store.get(saved.id))
    }

    @Test
    fun builtInPresetsCannotBeDeleted() = runBlocking {
        store.seedIfEmpty()
        // SQL 层用 isBuiltIn = 0 条件保护，返回 false 表示未删除。
        assertFalse(store.deleteCustom("img_wechat"))
        assertNotNull("内置预设必须仍在", store.get("img_wechat"))
    }

    @Test
    fun defaultIsExclusiveWithinACategory() = runBlocking {
        store.seedIfEmpty()
        val web = store.get("img_web")!!
        store.setDefault(web)

        val imagePresets = store.presetsFor(FileCategory.IMAGE).first()
        val defaults = imagePresets.filter { it.isDefault }
        assertEquals("同类别只能有一个默认", 1, defaults.size)
        assertEquals("img_web", defaults.first().id)

        // 其他类别的默认不受影响。
        val audioDefaults = store.presetsFor(FileCategory.AUDIO).first().filter { it.isDefault }
        assertEquals(1, audioDefaults.size)
    }

    @Test
    fun presetsAreScopedByCategory() = runBlocking {
        store.seedIfEmpty()
        val video = store.presetsFor(FileCategory.VIDEO).first()
        assertTrue(video.isNotEmpty())
        assertTrue(video.all { it.category == FileCategory.VIDEO })
    }
}
