package com.openconvert.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ConversionEntity::class, BatchJobEntity::class, PresetEntity::class],
    version = 6,
    exportSchema = true,
)
abstract class OpenConvertDatabase : RoomDatabase() {
    abstract fun conversionDao(): ConversionDao
    abstract fun batchJobDao(): BatchJobDao
    abstract fun presetDao(): PresetDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversion_tasks ADD COLUMN outputSize INTEGER")
                db.execSQL("ALTER TABLE conversion_tasks ADD COLUMN quality TEXT NOT NULL DEFAULT 'BALANCED'")
                db.execSQL("ALTER TABLE conversion_tasks ADD COLUMN resolution TEXT NOT NULL DEFAULT 'ORIGINAL'")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversion_tasks ADD COLUMN kind TEXT NOT NULL DEFAULT 'SINGLE'")
                db.execSQL("ALTER TABLE conversion_tasks ADD COLUMN payloadJson TEXT")
                db.execSQL("ALTER TABLE conversion_tasks ADD COLUMN errorMessage TEXT")
                db.execSQL("ALTER TABLE conversion_tasks ADD COLUMN outputName TEXT")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversion_tasks ADD COLUMN batchId TEXT")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS batch_jobs (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        status TEXT NOT NULL,
                        total INTEGER NOT NULL,
                        done INTEGER NOT NULL,
                        failed INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        finishedAt INTEGER,
                        settingsJson TEXT NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS conversion_presets (
                        id TEXT NOT NULL PRIMARY KEY,
                        category TEXT NOT NULL,
                        name TEXT NOT NULL,
                        description TEXT NOT NULL,
                        targetFormat TEXT NOT NULL,
                        quality TEXT NOT NULL,
                        resolution TEXT NOT NULL,
                        stripMetadata INTEGER NOT NULL DEFAULT 0,
                        isDefault INTEGER NOT NULL DEFAULT 0,
                        isBuiltIn INTEGER NOT NULL DEFAULT 1,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // §8.1 的尺寸约束：最长边 / 固定尺寸 / 裁剪比例。
                // 预设是用户数据，必须持久化（与任务速度不同，后者刻意只存内存）。
                db.execSQL("ALTER TABLE conversion_presets ADD COLUMN longestEdgePx INTEGER")
                db.execSQL("ALTER TABLE conversion_presets ADD COLUMN fixedWidthPx INTEGER")
                db.execSQL("ALTER TABLE conversion_presets ADD COLUMN fixedHeightPx INTEGER")
                db.execSQL(
                    "ALTER TABLE conversion_presets ADD COLUMN cropAspect TEXT NOT NULL DEFAULT 'free'",
                )
            }
        }

        /**
         * @param dbName 数据库文件名。生产用默认值；迁移测试传独立名字，
         *   避免污染真机上的用户库。
         */
        fun create(
            context: Context,
            dbName: String = "openconvert.db",
        ): OpenConvertDatabase = Room.databaseBuilder(
            context.applicationContext,
            OpenConvertDatabase::class.java,
            dbName,
        ).addMigrations(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
        ).build()
    }
}
