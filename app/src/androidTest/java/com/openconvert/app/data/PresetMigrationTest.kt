package com.openconvert.app.data

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.openconvert.app.data.local.OpenConvertDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * v5 → 最新迁移：预设尺寸约束与任务实际引擎列均可无损升级。
 *
 * 不用 Room 的 MigrationTestHelper：它在本工程会因 room-testing 与
 * kotlinx-serialization 版本不匹配抛 AbstractMethodError
 * （GeneratedSerializer.typeParametersSerializers）。改为手工建 v5 库
 * （原始 SQLite + user_version=5），再让 Room 走真实迁移链打开——
 * 验证的是同一件事，且不引入额外依赖。
 */
@RunWith(AndroidJUnit4::class)
class PresetMigrationTest {

    private val dbName = "migration-v5-to-latest.db"

    private fun context() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun migrate5ToLatestAddsColumnsAndKeepsRows() = runBlocking {
        val dbFile = context().getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()
        dbFile.delete()

        // ---- 建一个 v5 库并写入一行 ----
        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { legacy ->
            legacy.execSQL(
                "CREATE TABLE IF NOT EXISTS `conversion_tasks` (`id` TEXT NOT NULL, " +
                    "`sourceUri` TEXT NOT NULL, `sourceName` TEXT NOT NULL, " +
                    "`sourceFormat` TEXT NOT NULL, `targetFormat` TEXT NOT NULL, " +
                    "`outputUri` TEXT, `fileSize` INTEGER NOT NULL, `outputSize` INTEGER, " +
                    "`quality` TEXT NOT NULL, `resolution` TEXT NOT NULL, " +
                    "`progress` INTEGER NOT NULL, `status` TEXT NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, `completedAt` INTEGER, `kind` TEXT NOT NULL, " +
                    "`payloadJson` TEXT, `errorMessage` TEXT, `outputName` TEXT, " +
                    "`batchId` TEXT, PRIMARY KEY(`id`))",
            )
            legacy.execSQL(
                "CREATE TABLE IF NOT EXISTS `batch_jobs` (`id` TEXT NOT NULL, " +
                    "`name` TEXT NOT NULL, `status` TEXT NOT NULL, `total` INTEGER NOT NULL, " +
                    "`done` INTEGER NOT NULL, `failed` INTEGER NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, `finishedAt` INTEGER, " +
                    "`settingsJson` TEXT NOT NULL, PRIMARY KEY(`id`))",
            )
            legacy.execSQL(
                "CREATE TABLE IF NOT EXISTS `conversion_presets` (`id` TEXT NOT NULL, " +
                    "`category` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                    "`description` TEXT NOT NULL, `targetFormat` TEXT NOT NULL, " +
                    "`quality` TEXT NOT NULL, `resolution` TEXT NOT NULL, " +
                    "`stripMetadata` INTEGER NOT NULL, `isDefault` INTEGER NOT NULL, " +
                    "`isBuiltIn` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`id`))",
            )
            // Room 用 room_master_table 记 identityHash，缺它会被判为"非 Room 库"。
            legacy.execSQL(
                "CREATE TABLE IF NOT EXISTS room_master_table " +
                    "(id INTEGER PRIMARY KEY, identity_hash TEXT)",
            )
            legacy.execSQL(
                "INSERT OR REPLACE INTO room_master_table (id, identity_hash) " +
                    "VALUES (42, '0d84fac1db2a2dc520b68eeea2def13f')",
            )
            legacy.execSQL(
                """
                INSERT INTO conversion_presets
                (id, category, name, description, targetFormat, quality, resolution,
                 stripMetadata, isDefault, isBuiltIn, createdAt)
                VALUES ('legacy', 'IMAGE', '老预设', '升级前写入', 'JPG', 'BALANCED',
                        'ORIGINAL', 0, 1, 1, 111)
                """.trimIndent(),
            )
            legacy.execSQL(
                """
                INSERT INTO conversion_tasks
                (id, sourceUri, sourceName, sourceFormat, targetFormat, outputUri,
                 fileSize, outputSize, quality, resolution, progress, status,
                 createdAt, completedAt, kind, payloadJson, errorMessage, outputName, batchId)
                VALUES ('legacy-task', 'content://legacy', 'legacy.png', 'PNG', 'JPG', NULL,
                        1024, NULL, 'BALANCED', 'ORIGINAL', 0, 'PENDING',
                        111, NULL, 'SINGLE', NULL, NULL, NULL, NULL)
                """.trimIndent(),
            )
            legacy.version = 5
        }

        // ---- 让 Room 走真实迁移链打开 ----
        val db = OpenConvertDatabase.create(context(), dbName)
        try {
            val migrated = db.presetDao().getById("legacy")
            assertNotNull("升级后原有行必须还在", migrated)
            assertEquals("legacy", migrated!!.id)
            assertEquals("新列应有默认值", "free", migrated.cropAspect)
            assertTrue("可空列升级后应为 NULL", migrated.longestEdgePx == null)
            assertTrue(migrated.fixedWidthPx == null)
            assertTrue(migrated.fixedHeightPx == null)

            // 新列可写可读。
            db.presetDao().insertOrUpdate(
                migrated.copy(longestEdgePx = 1920, cropAspect = "1:1"),
            )
            val updated = db.presetDao().getById("legacy")!!
            assertEquals(1920, updated.longestEdgePx)
            assertEquals("1:1", updated.cropAspect)

            val migratedTask = db.conversionDao().getById("legacy-task")
            assertNotNull("升级后原任务必须还在", migratedTask)
            assertTrue("旧任务没有实际引擎，升级后应为 NULL", migratedTask!!.actualEngine == null)
        } finally {
            db.close()
            context().getDatabasePath(dbName).delete()
        }
    }
}
