package com.lembar.journal.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [EntryEntity::class, AttachmentEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class LembarDatabase : RoomDatabase() {
    abstract fun journalDao(): JournalDao

    companion object {
        const val FILE_NAME = "lembar.db"

        fun create(context: Context): LembarDatabase =
            Room.databaseBuilder(context, LembarDatabase::class.java, FILE_NAME).build()
    }
}
