package com.tonyscratch.tsdj.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Real-time time-stretch and pitch-shift (WSOLA: waveform-similarity overlap-add).
 *
 * This is what makes **keylock** work: the deck can play faster or slower while the
 * song keeps its musical key, and the key can be moved up or down without changing tempo.
 *
 * Each output "hop" adds one short windowed grain read from the track. Before adding it,
 * we search a few milliseconds around the playhead for the spot that best continues the
 * previous grain, so the joins stay smooth. Audio thread only.
 */
class Stretcher(sampleRate: Int) {
    private val n = if (sampleRate > 60000) 4096 else 2048   // grain length in output frames (~43 ms)
    private val hop = n / 2
    private val window = FloatArray(n) { (0.5 - 0.5 * cos(2.0 * PI * it / n)).toFloat() }
    private val olaL = FloatArray(n)
    private val olaR = FloatArray(n)
    private val outL = FloatArray(hop)
    private val outR = FloatArray(hop)
    private var outIdx = hop
    private var prevStart = 0.0
    private var prevP = 1.0
    private var primed = false
    private val search = (sampleRate * 0.012).toInt() // ±12 ms

    /** Output of the last [next] call. */
    var l = 0f
        private set
    var r = 0f
        private set

    fun reset() {
        primed = false
        outIdx = hop
        olaL.fill(0f)
        olaR.fill(0f)
    }

    /**
     * Produce one output frame. [pos] is the playhead in track frames,
     * [p] the pitch factor (1 = original key, 2 = an octave up).
     */
    fun next(pcm: ShortArray, frames: Int, pos: Double, p: Double) {
        if (!primed) prime(pcm, frames, pos, p)
        if (outIdx >= hop) synth(pcm, frames, pos, p)
        l = outL[outIdx]
        r = outR[outIdx]
        outIdx++
    }

    /** Start cleanly: pretend a grain already ended right here, so there's no fade-in dip. */
    private fun prime(pcm: ShortArray, frames: Int, pos: Double, p: Double) {
        olaL.fill(0f)
        olaR.fill(0f)
        val start = pos - p * hop
        addGrain(pcm, frames, start, p)
        shift()
        prevStart = start
        prevP = p
        outIdx = hop
        primed = true
    }

    private fun synth(pcm: ShortArray, frames: Int, pos: Double, p: Double) {
        val natural = prevStart + prevP * hop
        val start = if (abs(natural - pos) > 4.0 * search) pos else bestMatch(pcm, frames, natural, pos, p)
        addGrain(pcm, frames, start, p)
        for (i in 0 until hop) {
            outL[i] = olaL[i]
            outR[i] = olaR[i]
        }
        shift()
        prevStart = start
        prevP = p
        outIdx = 0
    }

    private fun shift() {
        System.arraycopy(olaL, hop, olaL, 0, hop)
        System.arraycopy(olaR, hop, olaR, 0, hop)
        for (i in hop until n) {
            olaL[i] = 0f
            olaR[i] = 0f
        }
    }

    private fun addGrain(pcm: ShortArray, frames: Int, start: Double, p: Double) {
        for (j in 0 until n) {
            val sp = start + p * j
            val w = window[j]
            olaL[j] += (interp(pcm, frames, sp, 0) * w).toFloat()
            olaR[j] += (interp(pcm, frames, sp, 1) * w).toFloat()
        }
    }

    /** Find the grain start near [nominal] whose opening best matches the natural continuation. */
    private fun bestMatch(pcm: ShortArray, frames: Int, natural: Double, nominal: Double, p: Double): Double {
        val base = floor(nominal).toInt()
        val nat = floor(natural).toInt()
        // Reference: the natural continuation, read once (every 4th frame of the overlap).
        val count = hop / 4
        for (k in 0 until count) {
            refIdx[k] = (p * k * 4).toInt()
            ref[k] = mono(pcm, frames, nat + refIdx[k])
        }
        var bestOff = 0
        var bestScore = Double.NEGATIVE_INFINITY
        var off = -search
        while (off <= search) {
            val s = score(pcm, frames, base + off, count, 2)
            if (s > bestScore) { bestScore = s; bestOff = off }
            off += 3
        }
        val coarse = bestOff
        for (o in coarse - 2..coarse + 2) {
            if (o == coarse) continue
            val s = score(pcm, frames, base + o, count, 1)
            if (s > bestScore) { bestScore = s; bestOff = o }
        }
        return nominal + bestOff
    }

    private val ref = DoubleArray(hop / 4 + 1)
    private val refIdx = IntArray(hop / 4 + 1)

    private fun score(pcm: ShortArray, frames: Int, b: Int, count: Int, step: Int): Double {
        var dot = 0.0
        var eb = 1e-9
        val safe = b >= 0 && b + refIdx[count - 1] + 1 < frames
        var k = 0
        while (k < count) {
            val i = b + refIdx[k]
            val y = if (safe) (pcm[2 * i] + pcm[2 * i + 1]).toDouble() else mono(pcm, frames, i)
            dot += ref[k] * y
            eb += y * y
            k += step
        }
        return dot / sqrt(eb)
    }

    companion object {
        private fun mono(pcm: ShortArray, frames: Int, i: Int): Double =
            if (i < 0 || i >= frames) 0.0 else (pcm[2 * i] + pcm[2 * i + 1]).toDouble()

        private fun s(pcm: ShortArray, frames: Int, i: Int, ch: Int): Double =
            if (i < 0 || i >= frames) 0.0 else pcm[2 * i + ch] / 32768.0

        fun interp(pcm: ShortArray, frames: Int, pos: Double, ch: Int): Double {
            val i = floor(pos).toInt()
            val f = pos - i
            val xm1 = s(pcm, frames, i - 1, ch)
            val x0 = s(pcm, frames, i, ch)
            val x1 = s(pcm, frames, i + 1, ch)
            val x2 = s(pcm, frames, i + 2, ch)
            val c1 = 0.5 * (x1 - xm1)
            val c2 = xm1 - 2.5 * x0 + 2.0 * x1 - 0.5 * x2
            val c3 = 0.5 * (x2 - xm1) + 1.5 * (x0 - x1)
            return ((c3 * f + c2) * f + c1) * f + x0
        }
    }
}
