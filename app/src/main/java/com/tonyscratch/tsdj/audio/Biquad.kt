package com.tonyscratch.tsdj.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A stereo two-pole filter (RBJ "Audio EQ Cookbook" formulas).
 * Used for the HI/LOW EQ shelves and the sweep filter on each deck.
 * Only touched by the audio thread.
 */
class Biquad {
    private var b0 = 1.0
    private var b1 = 0.0
    private var b2 = 0.0
    private var a1 = 0.0
    private var a2 = 0.0

    private var z1L = 0.0
    private var z2L = 0.0
    private var z1R = 0.0
    private var z2R = 0.0

    fun reset() {
        z1L = 0.0; z2L = 0.0; z1R = 0.0; z2R = 0.0
    }

    fun setLowShelf(sampleRate: Int, freq: Double, gainDb: Double) {
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * freq / sampleRate
        val cosW = cos(w0)
        val alpha = sin(w0) / 2.0 * sqrt(2.0) // shelf slope S = 1
        val sqA = 2.0 * sqrt(a) * alpha
        val n0 = a * ((a + 1) - (a - 1) * cosW + sqA)
        val n1 = 2 * a * ((a - 1) - (a + 1) * cosW)
        val n2 = a * ((a + 1) - (a - 1) * cosW - sqA)
        val d0 = (a + 1) + (a - 1) * cosW + sqA
        val d1 = -2 * ((a - 1) + (a + 1) * cosW)
        val d2 = (a + 1) + (a - 1) * cosW - sqA
        set(n0, n1, n2, d0, d1, d2)
    }

    fun setHighShelf(sampleRate: Int, freq: Double, gainDb: Double) {
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * freq / sampleRate
        val cosW = cos(w0)
        val alpha = sin(w0) / 2.0 * sqrt(2.0)
        val sqA = 2.0 * sqrt(a) * alpha
        val n0 = a * ((a + 1) + (a - 1) * cosW + sqA)
        val n1 = -2 * a * ((a - 1) + (a + 1) * cosW)
        val n2 = a * ((a + 1) + (a - 1) * cosW - sqA)
        val d0 = (a + 1) - (a - 1) * cosW + sqA
        val d1 = 2 * ((a - 1) - (a + 1) * cosW)
        val d2 = (a + 1) - (a - 1) * cosW - sqA
        set(n0, n1, n2, d0, d1, d2)
    }

    fun setLowPass(sampleRate: Int, freq: Double, q: Double) {
        val w0 = 2.0 * PI * freq / sampleRate
        val cosW = cos(w0)
        val alpha = sin(w0) / (2.0 * q)
        set((1 - cosW) / 2, 1 - cosW, (1 - cosW) / 2, 1 + alpha, -2 * cosW, 1 - alpha)
    }

    fun setHighPass(sampleRate: Int, freq: Double, q: Double) {
        val w0 = 2.0 * PI * freq / sampleRate
        val cosW = cos(w0)
        val alpha = sin(w0) / (2.0 * q)
        set((1 + cosW) / 2, -(1 + cosW), (1 + cosW) / 2, 1 + alpha, -2 * cosW, 1 - alpha)
    }

    /** Band-pass with 0 dB peak at [freq]. */
    fun setBandPass(sampleRate: Int, freq: Double, q: Double) {
        val w0 = 2.0 * PI * freq / sampleRate
        val cosW = cos(w0)
        val alpha = sin(w0) / (2.0 * q)
        set(alpha, 0.0, -alpha, 1 + alpha, -2 * cosW, 1 - alpha)
    }

    private fun set(n0: Double, n1: Double, n2: Double, d0: Double, d1: Double, d2: Double) {
        b0 = n0 / d0
        b1 = n1 / d0
        b2 = n2 / d0
        a1 = d1 / d0
        a2 = d2 / d0
    }

    fun processL(x: Double): Double {
        val y = b0 * x + z1L
        z1L = b1 * x - a1 * y + z2L
        z2L = b2 * x - a2 * y
        return y
    }

    fun processR(x: Double): Double {
        val y = b0 * x + z1R
        z1R = b1 * x - a1 * y + z2R
        z2R = b2 * x - a2 * y
        return y
    }
}
