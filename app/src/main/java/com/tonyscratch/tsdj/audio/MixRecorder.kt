package com.tonyscratch.tsdj.audio

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.os.StatFs
import android.provider.MediaStore
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Records the master output (exactly what the crowd hears) to a WAV file in
 * Music/TS Dj, where any other app can find it.
 */
class MixRecorder(private val context: Context, private val sampleRate: Int) {
    private val ring = RecordRing(sampleRate * 2 * 8) // 8 seconds of slack
    @Volatile var recording = false
        private set
    @Volatile var framesWritten = 0L
        private set
    private var thread: Thread? = null
    var lastUri: Uri? = null
        private set

    /** Plug this into the mixer so it receives the master output. */
    val tap = MasterTap { buf, frames -> if (recording) ring.write(buf, frames * 2) }

    val elapsedSec: Long get() = framesWritten / sampleRate

    /** Minutes of WAV recording that fit in the free space on the phone. */
    fun freeMinutes(): Long {
        val stat = StatFs(Environment.getExternalStorageDirectory().path)
        return stat.availableBytes / (sampleRate.toLong() * 4 * 60)
    }

    /** Returns false if the file couldn't be created. */
    fun start(): Boolean {
        if (recording) return true
        val name = "TS Dj mix " + SimpleDateFormat("yyyy-MM-dd HH-mm", Locale.US).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "$name.wav")
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/x-wav")
            put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/TS Dj")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: return false
        val pfd: ParcelFileDescriptor = try {
            resolver.openFileDescriptor(uri, "rw") ?: return false
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            return false
        }

        // Throw away anything left over from a previous recording.
        val scratch = FloatArray(8192)
        while (ring.read(scratch) > 0) { /* drain */ }
        framesWritten = 0
        lastUri = uri
        recording = true

        thread = Thread({
            val stream = FileOutputStream(pfd.fileDescriptor)
            val channel = stream.channel
            try {
                channel.write(ByteBuffer.wrap(wavHeader(0)))
                val floats = FloatArray(8192)
                val bytes = ByteBuffer.allocate(8192 * 2).order(ByteOrder.LITTLE_ENDIAN)
                while (true) {
                    val n = ring.read(floats)
                    if (n == 0) {
                        if (!recording) break
                        Thread.sleep(15)
                        continue
                    }
                    bytes.clear()
                    for (i in 0 until n) {
                        bytes.putShort((floats[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort())
                    }
                    bytes.flip()
                    while (bytes.hasRemaining()) channel.write(bytes)
                    framesWritten += n / 2
                }
                channel.position(0)
                channel.write(ByteBuffer.wrap(wavHeader(framesWritten)))
                stream.fd.sync()
            } finally {
                try { channel.close() } catch (_: Exception) {}
                try { pfd.close() } catch (_: Exception) {}
                val done = ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }
                resolver.update(uri, done, null, null)
            }
        }, "TSDj-recorder").also { it.start() }
        return true
    }

    /** Stops and finishes the file. Call off the main thread; it waits for the writer to finish. */
    fun stop(): Uri? {
        if (!recording) return lastUri
        recording = false
        try { thread?.join(5000) } catch (_: InterruptedException) {}
        thread = null
        return lastUri
    }

    private fun wavHeader(frames: Long): ByteArray {
        val dataBytes = (frames * 4).coerceAtMost(0xFFFFFFFFL - 36)
        val b = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray(Charsets.US_ASCII))
        b.putInt((36 + dataBytes).toInt())
        b.put("WAVE".toByteArray(Charsets.US_ASCII))
        b.put("fmt ".toByteArray(Charsets.US_ASCII))
        b.putInt(16)
        b.putShort(1) // PCM
        b.putShort(2) // stereo
        b.putInt(sampleRate)
        b.putInt(sampleRate * 4)
        b.putShort(4)
        b.putShort(16)
        b.put("data".toByteArray(Charsets.US_ASCII))
        b.putInt(dataBytes.toInt())
        return b.array()
    }
}
