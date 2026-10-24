package com.tonyscratch.tsdj.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Builds the overview used to draw waveforms: one peak (0..1) per [TrackData.WAVE_STEP] frames. */
object WaveformBuilder {
    fun build(pcm: ShortArray): FloatArray {
        val frames = pcm.size / 2
        val step = TrackData.WAVE_STEP
        val out = FloatArray((frames + step - 1) / step)
        var maxPeak = 1e-6f
        for (b in out.indices) {
            val start = b * step
            val end = min(frames, start + step)
            var peak = 0
            for (f in start until end) {
                val m = abs((pcm[2 * f] + pcm[2 * f + 1]) / 2)
                if (m > peak) peak = m
            }
            val v = peak / 32768f
            out[b] = v
            if (v > maxPeak) maxPeak = v
        }
        // Normalise so quiet masters still draw a readable waveform.
        val scale = 1f / maxPeak
        for (i in out.indices) out[i] = min(1f, out[i] * scale)
        return out
    }
}

/**
 * Tempo and first-beat detection.
 * 1. Turn the audio into an "onset" curve: how sharply loudness rises, ~187 times a second.
 * 2. Autocorrelate it and score every tempo from 70 to 180 BPM, counting 1–4 beat multiples.
 * 3. Find the beat phase that lines up best with the onsets.
 */
object BpmDetector {
    private const val HOP = 256

    fun detect(pcm: ShortArray, sampleRate: Int): Pair<Double, Double>? {
        val frames = pcm.size / 2
        val maxFrames = min(frames, sampleRate * 360) // first 6 minutes is plenty
        val hops = maxFrames / HOP
        if (hops < 400) return null
        val envRate = sampleRate.toDouble() / HOP

        // Energy per hop, with extra weight on the bass (kick drums), on a log scale.
        val energy = DoubleArray(hops)
        var lp = 0.0
        val lpCoef = 1.0 - kotlin.math.exp(-2.0 * PI * 150.0 / sampleRate)
        for (h in 0 until hops) {
            var full = 0.0
            var low = 0.0
            val base = h * HOP
            for (f in base until base + HOP) {
                val m = (pcm[2 * f] + pcm[2 * f + 1]) / 65536.0
                lp += (m - lp) * lpCoef
                full += m * m
                low += lp * lp
            }
            energy[h] = ln(1.0 + 1000.0 * (full + 4.0 * low) / HOP)
        }
        val onset = DoubleArray(hops)
        for (h in 1 until hops) onset[h] = max(0.0, energy[h] - energy[h - 1])
        // Remove the slow-moving average so loud sections don't dominate.
        val mean = onset.average()
        for (h in onset.indices) onset[h] = max(0.0, onset[h] - mean)

        val minBpm = 70.0
        val maxBpm = 180.0
        val maxLag = (4 * 60.0 * envRate / minBpm).toInt() + 2
        if (maxLag >= hops) return null
        val ac = DoubleArray(maxLag + 1)
        for (lag in 1..maxLag) {
            var s = 0.0
            for (i in lag until hops) s += onset[i] * onset[i - lag]
            ac[lag] = s / (hops - lag)
        }

        fun acAt(lag: Double): Double {
            val i = floor(lag).toInt()
            if (i < 1 || i + 1 > maxLag) return 0.0
            val f = lag - i
            return ac[i] * (1 - f) + ac[i + 1] * f
        }

        var bestBpm = 0.0
        var bestScore = -1.0
        var bpm = minBpm
        while (bpm <= maxBpm) {
            val lag = 60.0 * envRate / bpm
            val score = acAt(lag) + 0.6 * acAt(2 * lag) + 0.4 * acAt(3 * lag) + 0.5 * acAt(4 * lag) + 0.3 * acAt(lag / 2)
            if (score > bestScore) {
                bestScore = score
                bestBpm = bpm
            }
            bpm += 0.02
        }
        if (bestScore <= 0) return null
        while (bestBpm < 85) bestBpm *= 2
        while (bestBpm > 175) bestBpm /= 2

        // Fine-tune: a beat grid laid over the whole track only lines up with the kicks
        // at the exact tempo, so test small tempo changes and keep the best-aligned grid.
        var bestPhase = 0.0
        var bestGrid = -1.0
        var refined = bestBpm
        var cand = bestBpm - 0.4
        while (cand <= bestBpm + 0.4) {
            val (ph, sc) = bestPhaseFor(onset, 60.0 * envRate / cand, 1.0)
            if (sc > bestGrid) {
                bestGrid = sc
                refined = cand
                bestPhase = ph
            }
            cand += 0.01
        }
        bestBpm = refined
        val rounded = bestBpm.roundToInt().toDouble()
        if (abs(bestBpm - rounded) < 0.15) bestBpm = rounded
        bestBpm = (bestBpm * 100).roundToInt() / 100.0

        // Final phase at quarter-hop resolution for the chosen tempo.
        bestPhase = bestPhaseFor(onset, 60.0 * envRate / bestBpm, 0.25).first
        val firstBeatSec = (bestPhase * HOP) / sampleRate
        return bestBpm to firstBeatSec
    }

    /** Which offset puts the most onsets on a grid with this [period]? Returns (phase, score). */
    private fun bestPhaseFor(onset: DoubleArray, period: Double, step: Double): Pair<Double, Double> {
        var bestPhase = 0.0
        var bestScore = -1.0
        var phase = 0.0
        val last = onset.size - 1
        while (phase < period) {
            var s = 0.0
            var t = phase
            while (t < last) {
                val i = t.toInt()
                val f = t - i
                s += onset[i] * (1 - f) + onset[i + 1] * f
                t += period
            }
            if (s > bestScore) {
                bestScore = s
                bestPhase = phase
            }
            phase += step
        }
        return bestPhase to bestScore
    }
}

/**
 * Musical key detection, reported in Camelot notation (e.g. 8A) so harmonic mixing is easy.
 * Builds a 12-note "chroma" profile with an FFT and compares it with the standard
 * Krumhansl–Kessler major and minor key profiles.
 */
object KeyDetector {
    private val MAJOR = doubleArrayOf(6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88)
    private val MINOR = doubleArrayOf(6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17)

    // Camelot numbers by pitch class (C = 0).
    private val CAMELOT_MAJOR = intArrayOf(8, 3, 10, 5, 12, 7, 2, 9, 4, 11, 6, 1)
    private val CAMELOT_MINOR = intArrayOf(5, 12, 7, 2, 9, 4, 11, 6, 1, 8, 3, 10)

    fun detect(pcm: ShortArray, sampleRate: Int): String? {
        val frames = pcm.size / 2
        val factor = max(1, sampleRate / 11025)
        val sr = sampleRate.toDouble() / factor
        val n = 4096
        val totalDown = frames / factor
        if (totalDown < n * 4) return null

        // Up to 160 analysis windows spread evenly through the track.
        val windows = min(160, totalDown / n)
        val stride = (totalDown - n) / max(1, windows - 1)
        val re = DoubleArray(n)
        val im = DoubleArray(n)
        val chroma = DoubleArray(12)
        val hann = DoubleArray(n) { 0.5 - 0.5 * cos(2 * PI * it / (n - 1)) }

        for (w in 0 until windows) {
            val startDown = w * stride
            for (k in 0 until n) {
                // Downsample by averaging [factor] frames (a simple anti-alias filter).
                var s = 0.0
                val f0 = (startDown + k) * factor
                for (j in 0 until factor) {
                    val f = f0 + j
                    if (f < frames) s += (pcm[2 * f] + pcm[2 * f + 1]) / 65536.0
                }
                re[k] = s / factor * hann[k]
                im[k] = 0.0
            }
            fft(re, im)
            for (bin in 1 until n / 2) {
                val freq = bin * sr / n
                if (freq < 55.0 || freq > 2000.0) continue
                val mag = sqrt(re[bin] * re[bin] + im[bin] * im[bin])
                val midi = 69.0 + 12.0 * log2(freq / 440.0)
                val pc = ((midi.roundToInt() % 12) + 12) % 12
                chroma[pc] += mag
            }
        }
        if (chroma.sum() <= 0) return null

        var bestScore = Double.NEGATIVE_INFINITY
        var bestKey: String? = null
        for (tonic in 0 until 12) {
            val maj = correlation(chroma, MAJOR, tonic)
            if (maj > bestScore) { bestScore = maj; bestKey = "${CAMELOT_MAJOR[tonic]}B" }
            val min = correlation(chroma, MINOR, tonic)
            if (min > bestScore) { bestScore = min; bestKey = "${CAMELOT_MINOR[tonic]}A" }
        }
        return bestKey
    }

    private fun correlation(chroma: DoubleArray, profile: DoubleArray, tonic: Int): Double {
        val mc = chroma.average()
        val mp = profile.average()
        var num = 0.0
        var dc = 0.0
        var dp = 0.0
        for (i in 0 until 12) {
            val c = chroma[(i + tonic) % 12] - mc
            val p = profile[i] - mp
            num += c * p
            dc += c * c
            dp += p * p
        }
        return if (dc == 0.0 || dp == 0.0) 0.0 else num / sqrt(dc * dp)
    }

    /** In-place radix-2 FFT. [re].size must be a power of two. */
    fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2 * PI / len
            val wr = cos(ang)
            val wi = sin(ang)
            var i = 0
            while (i < n) {
                var cr = 1.0
                var ci = 0.0
                for (k in 0 until len / 2) {
                    val ur = re[i + k]
                    val ui = im[i + k]
                    val vr = re[i + k + len / 2] * cr - im[i + k + len / 2] * ci
                    val vi = re[i + k + len / 2] * ci + im[i + k + len / 2] * cr
                    re[i + k] = ur + vr
                    im[i + k] = ui + vi
                    re[i + k + len / 2] = ur - vr
                    im[i + k + len / 2] = ui - vi
                    val ncr = cr * wr - ci * wi
                    ci = cr * wi + ci * wr
                    cr = ncr
                }
                i += len
            }
            len = len shl 1
        }
    }
}

/** Camelot wheel helpers for the "key match" filter. */
object Camelot {
    /** True when two keys mix well: same key, one step around the wheel, or relative major/minor. */
    fun compatible(a: String?, b: String?): Boolean {
        if (a == null || b == null) return false
        val na = a.dropLast(1).toIntOrNull() ?: return false
        val nb = b.dropLast(1).toIntOrNull() ?: return false
        val la = a.last()
        val lb = b.last()
        if (na == nb) return true
        if (la != lb) return false
        val d = ((na - nb) % 12 + 12) % 12
        return d == 1 || d == 11
    }
}
