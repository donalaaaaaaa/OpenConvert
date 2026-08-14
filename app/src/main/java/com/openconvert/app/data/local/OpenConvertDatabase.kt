package com.openconvert.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ConversionEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class OpenConvertDatabase : RoomDatabase() {
    abstract fun conversionDao(): ConversionDao

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

        fun create(context: Context): OpenConvertDatabase = Room.databaseBuilder(
            context.applicationContext,
            OpenConvertDatabase::class.java,
            "openconvert.db",
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
    }
}
