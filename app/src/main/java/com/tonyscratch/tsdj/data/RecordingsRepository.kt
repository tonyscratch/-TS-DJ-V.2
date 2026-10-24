package com.tonyscratch.tsdj.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

data class Recording(
    val uri: Uri,
    val name: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val dateAddedSec: Long,
)

/** Lists, renames and deletes the mixes saved in Music/TS Dj. */
class RecordingsRepository(private val context: Context) {

    fun list(): List<Recording> {
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_ADDED,
        )
        val out = ArrayList<Recording>()
        context.contentResolver.query(
            collection, projection,
            "${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?", arrayOf("Music/TS Dj%"),
            "${MediaStore.Audio.Media.DATE_ADDED} DESC",
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val addedCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                out += Recording(
                    uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                    name = (c.getString(nameCol) ?: "Mix").removeSuffix(".wav"),
                    durationMs = c.getLong(durCol),
                    sizeBytes = c.getLong(sizeCol),
                    dateAddedSec = c.getLong(addedCol),
                )
            }
        }
        return out
    }

    fun rename(uri: Uri, newName: String): Boolean {
        val clean = newName.trim().replace(Regex("[/\\\\:*?\"<>|]"), "-").ifEmpty { return false }
        val values = ContentValues().apply { put(MediaStore.Audio.Media.DISPLAY_NAME, "$clean.wav") }
        return try {
            context.contentResolver.update(uri, values, null, null) > 0
        } catch (_: Exception) {
            false
        }
    }

    fun delete(uri: Uri): Boolean = try {
        context.contentResolver.delete(uri, null, null) > 0
    } catch (_: Exception) {
        false
    }
}
