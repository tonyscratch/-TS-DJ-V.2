package com.tonyscratch.tsdj.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.tanh

/** Receives the finished master output (interleaved stereo) — used for recording. */
fun interface MasterTap {
    fun onAudio(interleaved: FloatArray, frames: Int)
}

/**
 * Two decks (each with its own EQ, filter and effects) → channel faders → crossfader
 * → + sampler → master volume → soft limiter.
 *
 * With [splitCue] on, the output is split for headphone cueing with a Y-splitter cable:
 * left = master (mono), right = the decks with their headphone (PFL) button on.
 */
class Mixer(val sampleRate: Int) {
    val decks = arrayOf(Deck(0, sampleRate), Deck(1, sampleRate))
    val sampler = Sampler()

    /** 0 = full Deck A, 1 = full Deck B. */
    @Volatile var crossfader = 0.5f
    @Volatile var masterVolume = 0.9f
    @Volatile var masterLevel = 0f
    @Volatile var splitCue = false
    @Volatile var tap: MasterTap? = null

    private var bufA = FloatArray(0)
    private var bufAR = FloatArray(0)
    private var bufB = FloatArray(0)
    private var bufBR = FloatArray(0)
    private var mixL = FloatArray(0)
    private var mixR = FloatArray(0)
    private var master = FloatArray(0)

    fun render(out: FloatArray, n: Int) {
        if (bufA.size < n) {
            bufA = FloatArray(n); bufAR = FloatArray(n)
            bufB = FloatArray(n); bufBR = FloatArray(n)
            mixL = FloatArray(n); mixR = FloatArray(n)
            master = FloatArray(n * 2)
        }
        val a = decks[0]
        val b = decks[1]
        a.render(bufA, bufAR, n)
        b.render(bufB, bufBR, n)

        val cf = crossfader
        val gA = crossGainA(cf) * faderCurve(a.fader)
        val gB = crossGainB(cf) * faderCurve(b.fader)

        var peakA = 0f
        var peakB = 0f
        for (i in 0 until n) {
            val al = bufA[i] * gA
            val ar = bufAR[i] * gA
            val bl = bufB[i] * gB
            val br = bufBR[i] * gB
            peakA = max(peakA, max(abs(al), abs(ar)))
            peakB = max(peakB, max(abs(bl), abs(br)))
            mixL[i] = al + bl
            mixR[i] = ar + br
        }
        a.level = decay(a.level, peakA)
        b.level = decay(b.level, peakB)

        sampler.render(mixL, mixR, n)

        val mv = masterVolume
        var peakM = 0f
        for (i in 0 until n) {
            val l = softClip(mixL[i] * mv)
            val r = softClip(mixR[i] * mv)
            master[2 * i] = l
            master[2 * i + 1] = r
            peakM = max(peakM, max(abs(l), abs(r)))
        }
        masterLevel = decay(masterLevel, peakM)
        tap?.onAudio(master, n)

        if (!splitCue) {
            System.arraycopy(master, 0, out, 0, n * 2)
        } else {
            val cueA = a.pfl
            val cueB = b.pfl
            for (i in 0 until n) {
                val m = (master[2 * i] + master[2 * i + 1]) * 0.5f
                val cue = if (!cueA && !cueB) m else {
                    var c = 0f
                    if (cueA) c += (bufA[i] + bufAR[i]) * 0.5f
                    if (cueB) c += (bufB[i] + bufBR[i]) * 0.5f
                    softClip(c * 0.8f)
                }
                out[2 * i] = m
                out[2 * i + 1] = cue
            }
        }
    }

    fun deck(index: Int): Deck = decks[index.coerceIn(0, 1)]

    companion object {
        /** Deck A stays at full volume until the middle, then fades out (no dip in the middle). */
        fun crossGainA(cf: Float): Float = if (cf <= 0.5f) 1f else cos((cf - 0.5f) * PI).toFloat()
        fun crossGainB(cf: Float): Float = if (cf >= 0.5f) 1f else cos((0.5f - cf) * PI).toFloat()

        fun faderCurve(f: Float): Float = f * f

        private fun decay(old: Float, peak: Float): Float = if (peak > old) peak else old * 0.92f

        /** Leaves normal levels untouched and rounds off peaks above 0.8 so the output never hard-clips. */
        fun softClip(x: Float): Float {
            val ax = abs(x)
            if (ax <= 0.8f) return x
            val over = (ax - 0.8f) / 0.2f
            val y = 0.8f + 0.2f * tanh(over.toDouble()).toFloat()
            return if (x < 0) -y else y
        }
    }
}
