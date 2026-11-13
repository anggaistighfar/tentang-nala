package com.lembar.journal.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface JournalDao {

    @Transaction
    @Query("SELECT * FROM entries ORDER BY date DESC, createdAt DESC")
    fun observeAll(): Flow<List<EntryWithAttachments>>

    @Transaction
    @Query("SELECT * FROM entries WHERE id = :id")
    fun observeById(id: Long): Flow<EntryWithAttachments?>

    @Transaction
    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun getById(id: Long): EntryWithAttachments?

    @Query("SELECT COUNT(*) FROM entries WHERE date = :epochDay")
    suspend fun countForDay(epochDay: Long): Int

    @Query("SELECT fileName FROM attachments")
    suspend fun allAttachmentFiles(): List<String>

    @Insert
    suspend fun insertEntry(entry: EntryEntity): Long

    @Update
    suspend fun updateEntry(entry: EntryEntity)

    @Query("DELETE FROM entries WHERE id = :id")
    suspend fun deleteEntry(id: Long)

    @Insert
    suspend fun insertAttachments(items: List<AttachmentEntity>)

    @Query("DELETE FROM attachments WHERE id IN (:ids)")
    suspend fun deleteAttachments(ids: List<Long>)

    @Query("UPDATE attachments SET position = :position WHERE id = :id")
    suspend fun setAttachmentPosition(id: Long, position: Int)
}
