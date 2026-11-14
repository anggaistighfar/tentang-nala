package com.lembar.journal.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [EntryEntity::class, AttachmentEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class LembarDatabase : RoomDatabase() {
    abstract fun journalDao(): JournalDao

    companion object {
        const val FILE_NAME = "lembar.db"

        /** v1.1 → v1.2: handwriting on the page and paragraph alignment. Existing entries keep everything. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE entries ADD COLUMN ink TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE entries ADD COLUMN align TEXT NOT NULL DEFAULT 'start'")
            }
        }

        fun create(context: Context): LembarDatabase =
            Room.databaseBuilder(context, LembarDatabase::class.java, FILE_NAME)
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
