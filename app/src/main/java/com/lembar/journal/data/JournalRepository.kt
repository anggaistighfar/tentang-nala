package com.lembar.journal.data

import androidx.room.withTransaction
import com.lembar.journal.data.db.AttachmentEntity
import com.lembar.journal.data.db.EntryEntity
import com.lembar.journal.data.db.EntryWithAttachments
import com.lembar.journal.data.db.LembarDatabase
import com.lembar.journal.domain.EntryFacts
import com.lembar.journal.domain.Emotions
import com.lembar.journal.domain.Markup
import com.lembar.journal.domain.Mood
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

enum class MediaKind(val key: String) {
    Photo("photo"), Sketch("sketch");

    companion object {
        fun fromKey(key: String) = if (key == Sketch.key) Sketch else Photo
    }
}

data class Attachment(
    val id: Long,
    val kind: MediaKind,
    val file: File,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
)

/**
 * UI-facing entry. For locked entries [title] and [body] are empty; call
 * [JournalRepository.readText] after the user authenticates.
 */
data class JournalEntry(
    val id: Long,
    val date: LocalDate,
    val createdAt: Long,
    val updatedAt: Long,
    val title: String,
    val body: String,
    val mood: Mood?,
    val emotions: List<String>,
    val fontKey: String,
    val locked: Boolean,
    val wordCount: Int,
    val attachments: List<Attachment>,
    /** Paragraph alignment key, see BodyAlign. */
    val align: String = "start",
    /** True when the page has handwriting. The strokes themselves come from [JournalRepository.readContent]. */
    val hasInk: Boolean = false,
) {
    fun facts() = EntryFacts(
        epochDay = date.toEpochDay(),
        createdAt = createdAt,
        mood = mood?.value ?: 0,
        emotionCount = emotions.size,
        wordCount = wordCount,
        mediaCount = attachments.size,
    )
}

/** A media item in the editor: either already saved, or freshly written to disk. */
sealed interface DraftMedia {
    data class Existing(val attachment: Attachment) : DraftMedia
    data class Fresh(val kind: MediaKind, val image: StoredImage) : DraftMedia
}

data class EntryDraft(
    val id: Long?,
    val date: LocalDate,
    val title: String,
    val body: String,
    val mood: Mood?,
    val emotions: List<String>,
    val fontKey: String,
    val locked: Boolean,
    val media: List<DraftMedia>,
    val ink: String = "",
    val align: String = "start",
)

/** Readable content of an entry (decrypted for locked entries). */
data class EntryContent(val title: String, val body: String, val ink: String)

class JournalRepository(
    private val db: LembarDatabase,
    private val cipher: EntryCipher,
    private val media: MediaStorage,
) {
    private val dao = db.journalDao()

    val entries: Flow<List<JournalEntry>> = dao.observeAll().map { list -> list.map { it.toModel() } }

    fun observe(id: Long): Flow<JournalEntry?> = dao.observeById(id).map { it?.toModel() }

    suspend fun get(id: Long): JournalEntry? = dao.getById(id)?.toModel()

    /** Returns (title, body) in plain text. Only call after authentication for locked entries. */
    suspend fun readText(id: Long): Pair<String, String> = readContent(id).let { it.title to it.body }

    /** Title, body and handwriting in plain form. Only call after authentication for locked entries. */
    suspend fun readContent(id: Long): EntryContent = withContext(Dispatchers.Default) {
        val row = dao.getById(id)?.entry ?: return@withContext EntryContent("", "", "")
        if (row.locked) {
            EntryContent(cipher.decrypt(row.title), cipher.decrypt(row.body), cipher.decrypt(row.ink))
        } else {
            EntryContent(row.title, row.body, row.ink)
        }
    }

    suspend fun save(draft: EntryDraft): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val words = countWords(draft.title) + countWords(Markup.plain(draft.body))
        val title = if (draft.locked) cipher.encrypt(draft.title) else draft.title
        val body = if (draft.locked) cipher.encrypt(draft.body) else draft.body
        val ink = if (draft.locked) cipher.encrypt(draft.ink) else draft.ink
        val removedFiles = mutableListOf<String>()

        val id = db.withTransaction {
            val existing = draft.id?.let { dao.getById(it) }
            val entryId = if (existing == null) {
                dao.insertEntry(
                    EntryEntity(
                        date = draft.date.toEpochDay(),
                        createdAt = now,
                        updatedAt = now,
                        title = title,
                        body = body,
                        mood = draft.mood?.value ?: 0,
                        emotions = Emotions.encode(draft.emotions),
                        fontKey = draft.fontKey,
                        locked = draft.locked,
                        wordCount = words,
                        ink = ink,
                        align = draft.align,
                    ),
                )
            } else {
                dao.updateEntry(
                    existing.entry.copy(
                        updatedAt = now,
                        title = title,
                        body = body,
                        mood = draft.mood?.value ?: 0,
                        emotions = Emotions.encode(draft.emotions),
                        fontKey = draft.fontKey,
                        locked = draft.locked,
                        wordCount = words,
                        ink = ink,
                        align = draft.align,
                    ),
                )
                existing.entry.id
            }

            val keptIds = draft.media.filterIsInstance<DraftMedia.Existing>().map { it.attachment.id }.toSet()
            val removed = existing?.attachments.orEmpty().filter { it.id !in keptIds }
            if (removed.isNotEmpty()) dao.deleteAttachments(removed.map { it.id })
            removedFiles += removed.map { it.fileName }

            val fresh = mutableListOf<AttachmentEntity>()
            draft.media.forEachIndexed { index, item ->
                when (item) {
                    is DraftMedia.Existing -> dao.setAttachmentPosition(item.attachment.id, index)
                    is DraftMedia.Fresh -> fresh += AttachmentEntity(
                        entryId = entryId,
                        kind = item.kind.key,
                        fileName = item.image.fileName,
                        width = item.image.width,
                        height = item.image.height,
                        sizeBytes = item.image.sizeBytes,
                        position = index,
                        createdAt = now,
                    )
                }
            }
            if (fresh.isNotEmpty()) dao.insertAttachments(fresh)
            entryId
        }
        removedFiles.forEach(media::delete)
        id
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        val row = dao.getById(id) ?: return@withContext
        dao.deleteEntry(id)
        row.attachments.forEach { media.delete(it.fileName) }
    }

    suspend fun setLocked(id: Long, locked: Boolean) = withContext(Dispatchers.IO) {
        val row = dao.getById(id)?.entry ?: return@withContext
        if (row.locked == locked) return@withContext
        val updated = if (locked) {
            row.copy(locked = true, title = cipher.encrypt(row.title), body = cipher.encrypt(row.body), ink = cipher.encrypt(row.ink))
        } else {
            row.copy(locked = false, title = cipher.decrypt(row.title), body = cipher.decrypt(row.body), ink = cipher.decrypt(row.ink))
        }
        dao.updateEntry(updated)
    }

    suspend fun pruneOrphanMedia() = withContext(Dispatchers.IO) {
        media.pruneOrphans(dao.allAttachmentFiles().toSet())
        media.clearCaptures()
    }

    suspend fun hasEntryFor(date: LocalDate): Boolean = dao.countForDay(date.toEpochDay()) > 0

    private fun EntryWithAttachments.toModel(): JournalEntry = JournalEntry(
        id = entry.id,
        date = LocalDate.ofEpochDay(entry.date),
        createdAt = entry.createdAt,
        updatedAt = entry.updatedAt,
        title = if (entry.locked) "" else entry.title,
        body = if (entry.locked) "" else entry.body,
        mood = Mood.of(entry.mood),
        emotions = Emotions.decode(entry.emotions),
        fontKey = entry.fontKey,
        locked = entry.locked,
        wordCount = entry.wordCount,
        attachments = attachments.sortedBy { it.position }.map {
            Attachment(it.id, MediaKind.fromKey(it.kind), media.file(it.fileName), it.width, it.height, it.sizeBytes)
        },
        align = entry.align,
        hasInk = entry.ink.isNotEmpty(),
    )

    companion object {
        fun countWords(text: String): Int =
            text.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
    }
}
