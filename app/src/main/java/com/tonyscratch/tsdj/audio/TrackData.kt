package com.tonyscratch.tsdj.audio

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** What we know about a track before it's decoded (title, artist, where it lives). */
data class TrackInfo(
    val id: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val uri: String,
    val source: String = "LOCAL",
)

/** Results of analysing a track once: tempo, where the first beat is, and musical key. */
data class TrackAnalysis(
    val bpm: Double,
    val firstBeatSec: Double,
    val key: String?, // Camelot notation, e.g. "8A"
)

/**
 * A decoded track held in memory, ready to play.
 * [pcm] is interleaved stereo 16-bit audio (L, R, L, R…) at [sampleRate].
 * [waveform] holds one peak value (0..1) per [WAVE_STEP] frames, for drawing.
 * [autoGain] is the volume correction that brings the track to a standard loudness.
 */
class TrackData(
    val pcm: ShortArray,
    val sampleRate: Int,
    val waveform: FloatArray,
    val autoGain: Float = 1f,
) {
    val frames: Int = pcm.size / 2
    val durationSec: Double get() = frames.toDouble() / sampleRate

    /** A short version of the waveform (one value per bin) for the whole-track overview. */
    val overview: FloatArray by lazy {
        val bins = 400
        val out = FloatArray(bins)
        if (waveform.isEmpty()) return@lazy out
        for (b in 0 until bins) {
            val s = (b.toLong() * waveform.size / bins).toInt()
            val e = max(s + 1, ((b + 1).toLong() * waveform.size / bins).toInt())
            var p = 0f
            for (i in s until min(e, waveform.size)) if (waveform[i] > p) p = waveform[i]
            out[b] = p
        }
        out
    }

    companion object {
        const val WAVE_STEP = 256

        /**
         * Auto-gain: measures the track's average loudness (RMS) and returns the gain that
         * brings it to about -14 dB, limited to ±12 dB.
         */
        fun measureGain(pcm: ShortArray): Float {
            val frames = pcm.size / 2
            if (frames == 0) return 1f
            val step = max(1, frames / 400_000)
            var sum = 0.0
            var count = 0
            var f = 0
            while (f < frames) {
                val m = (pcm[2 * f] + pcm[2 * f + 1]) / 65536.0
                sum += m * m
                count++
                f += step
            }
            val rms = sqrt(sum / max(1, count))
            if (rms < 1e-5) return 1f
            return (0.2 / rms).toFloat().coerceIn(0.25f, 4f)
        }
    }
}
