package com.tonyscratch.tsdj.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.tonyscratch.tsdj.audio.TrackInfo

/** A song stored on the phone. */
data class LocalTrack(
    val info: TrackInfo,
    val folder: String,     // e.g. "Download/" or "Music/Afro house/"
    val dateAddedSec: Long,
)

/** Reads the phone's music library through Android's media database. */
class LibraryRepository(private val context: Context) {

    fun loadTracks(): List<LocalTrack> {
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.RELATIVE_PATH,
            MediaStore.Audio.Media.DATE_ADDED,
        )
        // Skip ringtones and voice notes (under 30 seconds) and our own recordings.
        val selection = "${MediaStore.Audio.Media.DURATION} >= ? AND " +
            "(${MediaStore.Audio.Media.RELATIVE_PATH} IS NULL OR ${MediaStore.Audio.Media.RELATIVE_PATH} NOT LIKE ?)"
        val args = arrayOf("30000", "Music/TS Dj%")
        val out = ArrayList<LocalTrack>()
        context.contentResolver.query(collection, projection, selection, args, "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC")?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val pathCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)
            val addedCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                val artist = c.getString(artistCol)?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "Unknown artist"
                out += LocalTrack(
                    info = TrackInfo(
                        id = "local:$id",
                        title = c.getString(titleCol) ?: "Untitled",
                        artist = artist,
                        durationMs = c.getLong(durCol),
                        uri = uri.toString(),
                    ),
                    folder = c.getString(pathCol) ?: "",
                    dateAddedSec = c.getLong(addedCol),
                )
            }
        }
        return out
    }
}
