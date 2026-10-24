package com.tonyscratch.tsdj.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.nio.ByteOrder

/**
 * Decodes any audio file Android can play (MP3, AAC/M4A, FLAC, WAV, OGG…) into
 * 16-bit stereo PCM at the engine's sample rate, held in memory for instant scratching.
 */
object AudioDecoder {

    class DecodeException(message: String) : Exception(message)

    suspend fun decode(
        context: Context,
        uri: Uri,
        targetRate: Int,
        onProgress: (Float) -> Unit = {},
    ): ShortArray {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            var trackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    trackIndex = i
                    format = f
                    break
                }
            }
            if (trackIndex < 0 || format == null) throw DecodeException("This file has no audio track.")
            extractor.selectTrack(trackIndex)

            val mime = format.getString(MediaFormat.KEY_MIME)!!
            var srcRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
            val estimate = ((durationUs / 1_000_000.0) * srcRate * 2 * 1.02).toLong()
                .coerceIn(1L shl 16, (Int.MAX_VALUE / 2).toLong()).toInt()
            val out = ShortBuilder(estimate)

            val c = MediaCodec.createDecoderByType(mime)
            codec = c
            c.configure(format, null, null, 0)
            c.start()

            var pcmFloat = false
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var lastProgress = -1f

            while (!outputDone) {
                currentCoroutineContext().ensureActive()
                if (!inputDone) {
                    val inIdx = c.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val buf = c.getInputBuffer(inIdx)!!
                        val size = extractor.readSampleData(buf, 0)
                        if (size < 0) {
                            c.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            c.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIdx = c.dequeueOutputBuffer(info, 10_000)
                if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val of = c.outputFormat
                    srcRate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    pcmFloat = of.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                        of.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                } else if (outIdx >= 0) {
                    if (info.size > 0) {
                        val buf = c.getOutputBuffer(outIdx)!!
                        buf.position(info.offset)
                        buf.limit(info.offset + info.size)
                        buf.order(ByteOrder.LITTLE_ENDIAN)
                        val ch = channels.coerceAtLeast(1)
                        if (pcmFloat) {
                            val fb = buf.asFloatBuffer()
                            val n = fb.remaining() / ch
                            for (k in 0 until n) {
                                val l = fb.get(k * ch)
                                val r = if (ch > 1) fb.get(k * ch + 1) else l
                                out.add(floatToShort(l))
                                out.add(floatToShort(r))
                            }
                        } else {
                            val sb = buf.asShortBuffer()
                            val n = sb.remaining() / ch
                            for (k in 0 until n) {
                                val l = sb.get(k * ch)
                                val r = if (ch > 1) sb.get(k * ch + 1) else l
                                out.add(l)
                                out.add(r)
                            }
                        }
                    }
                    c.releaseOutputBuffer(outIdx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    if (durationUs > 0) {
                        val p = (info.presentationTimeUs.toFloat() / durationUs).coerceIn(0f, 1f)
                        if (p - lastProgress >= 0.02f) {
                            lastProgress = p
                            onProgress(p * 0.9f)
                        }
                    }
                }
            }

            val pcm = out.toArray()
            if (pcm.isEmpty()) throw DecodeException("Couldn't read any audio from this file.")
            return if (srcRate == targetRate) pcm else resample(pcm, srcRate, targetRate)
        } finally {
            try { codec?.stop() } catch (_: Exception) {}
            try { codec?.release() } catch (_: Exception) {}
            extractor.release()
        }
    }

    private fun floatToShort(v: Float): Short = (v.coerceIn(-1f, 1f) * 32767f).toInt().toShort()

    /** Linear-interpolation sample-rate conversion of interleaved stereo. */
    fun resample(pcm: ShortArray, from: Int, to: Int): ShortArray {
        val inFrames = pcm.size / 2
        val outFrames = (inFrames.toLong() * to / from).toInt()
        val out = ShortArray(outFrames * 2)
        val step = from.toDouble() / to
        for (i in 0 until outFrames) {
            val pos = i * step
            val i0 = pos.toInt()
            val f = pos - i0
            val i1 = if (i0 + 1 < inFrames) i0 + 1 else i0
            out[2 * i] = (pcm[2 * i0] * (1 - f) + pcm[2 * i1] * f).toInt().toShort()
            out[2 * i + 1] = (pcm[2 * i0 + 1] * (1 - f) + pcm[2 * i1 + 1] * f).toInt().toShort()
        }
        return out
    }

    private class ShortBuilder(initial: Int) {
        private var data = ShortArray(initial)
        private var size = 0

        fun add(v: Short) {
            if (size == data.size) data = data.copyOf((data.size * 3L / 2).toInt().coerceAtLeast(size + 4096))
            data[size++] = v
        }

        fun toArray(): ShortArray = if (size == data.size) data else data.copyOf(size)
    }
}
