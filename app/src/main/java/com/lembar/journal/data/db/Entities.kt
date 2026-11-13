package com.lembar.journal.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * One journal entry. When [locked] is true, [title] and [body] hold
 * AES-GCM ciphertext (see EntryCipher) instead of plain text.
 * [mood], [emotions] and [wordCount] stay readable so stats and XP work
 * without decrypting anything.
 */
@Entity(tableName = "entries", indices = [Index("date")])
data class EntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** LocalDate.toEpochDay() of the day this entry is about. */
    val date: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val title: String,
    val body: String,
    /** 1..5, 0 = not chosen. */
    val mood: Int,
    /** Emotion labels joined with '|'. */
    val emotions: String,
    val fontKey: String,
    val locked: Boolean,
    val wordCount: Int,
)

@Entity(
    tableName = "attachments",
    foreignKeys = [
        ForeignKey(
            entity = EntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("entryId")],
)
data class AttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryId: Long,
    /** "photo" or "sketch". */
    val kind: String,
    /** File name inside filesDir/media. */
    val fileName: String,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val position: Int,
    val createdAt: Long,
)

data class EntryWithAttachments(
    @Embedded val entry: EntryEntity,
    @Relation(parentColumn = "id", entityColumn = "entryId")
    val attachments: List<AttachmentEntity>,
)
