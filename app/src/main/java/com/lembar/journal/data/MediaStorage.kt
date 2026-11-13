package com.lembar.journal.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

/** A compressed image written to filesDir/media. */
data class StoredImage(
    val fileName: String,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val file: File,
)

/** How hard to squeeze photos. "Hemat" keeps a typical photo around 120-200 KB. */
enum class PhotoQuality(val key: String, val label: String, val maxEdge: Int, val quality: Int) {
    Hemat("hemat", "Hemat", 1280, 70),
    Seimbang("seimbang", "Seimbang", 1600, 80),
    Tajam("tajam", "Tajam", 2048, 86);

    companion object {
        fun fromKey(key: String?) = entries.firstOrNull { it.key == key } ?: Seimbang
    }
}

/**
 * Keeps every image inside the app's private storage (not the gallery), as
 * WebP. A 12 MP camera photo of ~4 MB usually ends up at 150-300 KB.
 */
class MediaStorage(private val context: Context) {

    val mediaDir: File = File(context.filesDir, "media").apply { mkdirs() }
    private val captureDir: File get() = File(context.cacheDir, "captures").apply { mkdirs() }

    fun file(fileName: String) = File(mediaDir, fileName)

    /** Temp file the camera app writes into. Deleted after import. */
    fun newCaptureFile(): File = File(captureDir, "cap_${System.currentTimeMillis()}.jpg")

    suspend fun importPhoto(uri: Uri, quality: PhotoQuality): StoredImage? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

            val longEdge = max(bounds.outWidth, bounds.outHeight)
            var sample = 1
            while (longEdge / (sample * 2) >= quality.maxEdge) sample *= 2

            val decoded = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@runCatching null

            val orientation = resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL

            val scaled = decoded.scaleToMaxEdge(quality.maxEdge)
            val upright = scaled.rotateFor(orientation)
            writeWebp(upright, quality.quality, prefix = "p").also {
                if (upright !== decoded) decoded.recycle()
                upright.recycle()
            }
        }.getOrNull()
    }

    suspend fun saveSketch(bitmap: Bitmap): StoredImage = withContext(Dispatchers.IO) {
        writeWebp(bitmap, 90, prefix = "s")
    }

    fun delete(fileName: String) {
        file(fileName).delete()
    }

    fun clearCaptures() {
        captureDir.listFiles()?.forEach { it.delete() }
    }

    /** Bytes used by the database plus every stored image. */
    fun usedBytes(): Long {
        val media = mediaDir.listFiles()?.sumOf { it.length() } ?: 0L
        val dbDir = context.getDatabasePath("lembar.db").parentFile
        val db = dbDir?.listFiles()?.filter { it.name.startsWith("lembar.db") }?.sumOf { it.length() } ?: 0L
        return media + db
    }

    /** Removes files no attachment row points to (e.g. after a crash mid-edit). */
    fun pruneOrphans(knownFiles: Set<String>) {
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        mediaDir.listFiles()?.forEach { f ->
            if (f.name !in knownFiles && f.lastModified() < cutoff) f.delete()
        }
    }

    private fun writeWebp(bitmap: Bitmap, quality: Int, prefix: String): StoredImage {
        val name = "${prefix}_${UUID.randomUUID()}.webp"
        val out = file(name)
        @Suppress("DEPRECATION")
        val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            Bitmap.CompressFormat.WEBP
        }
        out.outputStream().use { bitmap.compress(format, quality, it) }
        return StoredImage(name, bitmap.width, bitmap.height, out.length(), out)
    }

    private fun Bitmap.scaleToMaxEdge(maxEdge: Int): Bitmap {
        val longEdge = max(width, height)
        if (longEdge <= maxEdge) return this
        val ratio = maxEdge.toFloat() / longEdge
        return Bitmap.createScaledBitmap(this, (width * ratio).roundToInt(), (height * ratio).roundToInt(), true)
    }

    private fun Bitmap.rotateFor(orientation: Int): Bitmap {
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return this
        }
        val m = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(this, 0, 0, width, height, m, true)
    }
}
