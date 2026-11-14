package com.lembar.journal.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.lembar.journal.data.EntryCipher
import com.lembar.journal.data.MediaStorage
import com.lembar.journal.data.db.AttachmentEntity
import com.lembar.journal.data.db.EntryEntity
import com.lembar.journal.data.db.LembarDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BackupResult(val entries: Int, val media: Int, val bytes: Long)
data class RestoreResult(val added: Int, val skipped: Int, val media: Int)

/**
 * Exports every entry (locked ones decrypted, then protected by the backup password)
 * plus its photos and sketches into one .nala file chosen by the user, and restores it.
 *
 * Restore never deletes or overwrites anything: entries already on the phone
 * (same day and creation time) are skipped, the rest are added.
 */
class BackupManager(
    private val context: Context,
    private val db: LembarDatabase,
    private val cipher: EntryCipher,
    private val media: MediaStorage,
) {
    private val dao = db.journalDao()

    suspend fun export(target: Uri, password: CharArray): BackupResult = withContext(Dispatchers.IO) {
        val rows = dao.allWithAttachments()
        val arr = JSONArray()
        var mediaCount = 0
        for (r in rows) {
            val e = r.entry
            val plain = { s: String -> if (e.locked) cipher.decrypt(s) else s }
            arr.put(
                JSONObject()
                    .put("date", e.date)
                    .put("createdAt", e.createdAt)
                    .put("updatedAt", e.updatedAt)
                    .put("title", plain(e.title))
                    .put("body", plain(e.body))
                    .put("ink", plain(e.ink))
                    .put("mood", e.mood)
                    .put("emotions", e.emotions)
                    .put("fontKey", e.fontKey)
                    .put("align", e.align)
                    .put("locked", e.locked)
                    .put("wordCount", e.wordCount)
                    .put(
                        "attachments",
                        JSONArray().apply {
                            r.attachments.sortedBy { it.position }.forEach { a ->
                                if (media.file(a.fileName).exists()) {
                                    mediaCount++
                                    put(
                                        JSONObject()
                                            .put("kind", a.kind)
                                            .put("file", a.fileName)
                                            .put("width", a.width)
                                            .put("height", a.height)
                                            .put("position", a.position)
                                            .put("createdAt", a.createdAt),
                                    )
                                }
                            }
                        },
                    ),
            )
        }
        val manifest = JSONObject()
            .put("format", "tentang-nala-backup")
            .put("version", 1)
            .put("createdAt", System.currentTimeMillis())
            .put("entries", arr)

        val out = context.contentResolver.openOutputStream(target, "wt") ?: throw IOException("Tidak bisa menulis file")
        out.use { raw ->
            val enc = BackupCrypto.encrypting(raw.buffered(), password)
            ZipOutputStream(enc).use { zip ->
                zip.setLevel(Deflater.BEST_SPEED)
                zip.putNextEntry(ZipEntry(MANIFEST))
                zip.write(manifest.toString().toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                for (r in rows) for (a in r.attachments) {
                    val f = media.file(a.fileName)
                    if (!f.exists()) continue
                    zip.putNextEntry(ZipEntry("media/${a.fileName}"))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
        val size = context.contentResolver.openFileDescriptor(target, "r")?.use { it.statSize } ?: -1L
        BackupResult(rows.size, mediaCount, size)
    }

    suspend fun restore(source: Uri, password: CharArray): RestoreResult = withContext(Dispatchers.IO) {
        // 1) Decrypt the whole file into a private temp folder. Any wrong password,
        //    truncation or tampering fails here, before the journal is touched.
        val staging = File(context.cacheDir, "restore_${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            var manifestText: String? = null
            val input = context.contentResolver.openInputStream(source) ?: throw IOException("File tidak bisa dibuka")
            input.use { raw ->
                ZipInputStream(BackupCrypto.decrypting(raw.buffered(), password)).use { zip ->
                    while (true) {
                        val ze = zip.nextEntry ?: break
                        when {
                            ze.name == MANIFEST -> manifestText = zip.readBytes().toString(Charsets.UTF_8)
                            ze.name.startsWith("media/") && !ze.isDirectory -> {
                                val name = ze.name.removePrefix("media/")
                                // Only plain file names; refuse paths like "../x".
                                if (!name.matches(Regex("[A-Za-z0-9_.-]{1,80}"))) continue
                                File(staging, name).outputStream().use { zip.copyTo(it) }
                            }
                        }
                        zip.closeEntry()
                    }
                    // Drain to the end so the final chunk's tag is verified.
                    zip.readBytes()
                }
            }
            val manifest = JSONObject(manifestText ?: throw NotABackupException())
            if (manifest.optString("format") != "tentang-nala-backup") throw NotABackupException()
            val entries = manifest.getJSONArray("entries")

            // 2) Add what isn't on the phone yet, in one transaction.
            val existing = dao.allEntryKeys().toHashSet()
            var added = 0
            var skipped = 0
            var mediaAdded = 0
            val moved = mutableListOf<File>()
            try {
                db.withTransaction {
                    for (i in 0 until entries.length()) {
                        val o = entries.getJSONObject(i)
                        val key = "${o.getLong("date")}:${o.getLong("createdAt")}"
                        if (key in existing) { skipped++; continue }
                        existing += key
                        val locked = o.optBoolean("locked")
                        val seal = { s: String -> if (locked) cipher.encrypt(s) else s }
                        val id = dao.insertEntry(
                            EntryEntity(
                                date = o.getLong("date"),
                                createdAt = o.getLong("createdAt"),
                                updatedAt = o.optLong("updatedAt", o.getLong("createdAt")),
                                title = seal(o.optString("title")),
                                body = seal(o.optString("body")),
                                mood = o.optInt("mood"),
                                emotions = o.optString("emotions"),
                                fontKey = o.optString("fontKey", "lora"),
                                locked = locked,
                                wordCount = o.optInt("wordCount"),
                                ink = seal(o.optString("ink")),
                                align = o.optString("align", "start"),
                            ),
                        )
                        val atts = o.optJSONArray("attachments") ?: JSONArray()
                        val rows = mutableListOf<AttachmentEntity>()
                        for (j in 0 until atts.length()) {
                            val a = atts.getJSONObject(j)
                            val src = File(staging, a.getString("file"))
                            if (!src.exists()) continue
                            val ext = src.extension.ifEmpty { "webp" }
                            val name = "${if (a.optString("kind") == "sketch") "s" else "p"}_${UUID.randomUUID()}.$ext"
                            val dst = media.file(name)
                            // Copy, not rename: a file moved out of cacheDir keeps the cache group
                            // and could be counted as clearable cache by the system.
                            src.copyTo(dst, overwrite = false)
                            src.delete()
                            moved += dst
                            rows += AttachmentEntity(
                                entryId = id,
                                kind = a.optString("kind", "photo"),
                                fileName = name,
                                width = a.optInt("width"),
                                height = a.optInt("height"),
                                sizeBytes = dst.length(),
                                position = a.optInt("position", j),
                                createdAt = a.optLong("createdAt", o.getLong("createdAt")),
                            )
                        }
                        if (rows.isNotEmpty()) dao.insertAttachments(rows)
                        mediaAdded += rows.size
                        added++
                    }
                }
            } catch (t: Throwable) {
                moved.forEach { it.delete() }
                throw t
            }
            RestoreResult(added, skipped, mediaAdded)
        } finally {
            staging.deleteRecursively()
        }
    }

    companion object {
        private const val MANIFEST = "entries.json"

        fun suggestedName(nowMillis: Long = System.currentTimeMillis()): String {
            val d = java.time.Instant.ofEpochMilli(nowMillis).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            return "TentangNala-cadangan-%04d-%02d-%02d.nala".format(d.year, d.monthValue, d.dayOfMonth)
        }
    }
}
