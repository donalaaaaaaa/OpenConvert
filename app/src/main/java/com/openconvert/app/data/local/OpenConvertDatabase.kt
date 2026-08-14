package com.openconvert.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ConversionEntity::class, BatchJobEntity::class],
    version = 4,
    exportSchema = true,
)
abstract class OpenConvertDatabase : RoomDatabase() {
    abstract fun conversionDao(): ConversionDao
    abstract fun batchJobDao(): BatchJobDao

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

        fun create(context: Context): OpenConvertDatabase = Room.databaseBuilder(
            context.applicationContext,
            OpenConvertDatabase::class.java,
            "openconvert.db",
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build()
    }
}
