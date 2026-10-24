package com.tonyscratch.tsdj.audio

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * Musical key helpers. Keys are stored in Camelot notation ("8A" = A minor, "8B" = C major),
 * which makes harmonic mixing easy: same number, ±1, or the same number A↔B all mix well.
 */
object Keys {
    // Camelot number for each pitch class (C = 0, C# = 1 … B = 11).
    private val MAJOR = intArrayOf(8, 3, 10, 5, 12, 7, 2, 9, 4, 11, 6, 1)
    private val MINOR = intArrayOf(5, 12, 7, 2, 9, 4, 11, 6, 1, 8, 3, 10)
    private val NAMES = arrayOf("C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B")

    /** Returns (pitch class, isMinor) for a Camelot key such as "8A". */
    fun parse(key: String?): Pair<Int, Boolean>? {
        if (key == null || key.length < 2) return null
        val num = key.dropLast(1).toIntOrNull() ?: return null
        val minor = when (key.last()) {
            'A' -> true
            'B' -> false
            else -> return null
        }
        val table = if (minor) MINOR else MAJOR
        val pc = table.indexOf(num)
        return if (pc < 0) null else pc to minor
    }

    fun camelot(pc: Int, minor: Boolean): String {
        val p = Math.floorMod(pc, 12)
        return "${if (minor) MINOR[p] else MAJOR[p]}${if (minor) "A" else "B"}"
    }

    /** The key after moving it [semis] semitones. */
    fun shift(key: String?, semis: Int): String? {
        val (pc, minor) = parse(key) ?: return key
        return camelot(pc + semis, minor)
    }

    /** Musical name, e.g. "Am" or "F#". */
    fun musical(key: String?): String? {
        val (pc, minor) = parse(key) ?: return null
        return NAMES[pc] + if (minor) "m" else ""
    }

    fun display(key: String?, musical: Boolean): String = if (musical) (musical(key) ?: "--") else (key ?: "--")

    /** Semitones the pitch moves when the speed changes by [rate] (e.g. 1.06 → +1). */
    fun semitonesFromRate(rate: Double): Int = if (rate <= 0) 0 else (12.0 * ln(rate) / ln(2.0)).roundToInt()

    /**
     * The smallest number of semitones to move [key] so it mixes with [target]
     * (0 if they already mix). Null if either key is unknown.
     */
    fun bestShift(key: String?, target: String?): Int? {
        if (parse(key) == null || parse(target) == null) return null
        for (d in 0..6) {
            if (Camelot.compatible(shift(key, d), target)) return d
            if (d > 0 && Camelot.compatible(shift(key, -d), target)) return -d
        }
        return null
    }

    /** True when the keys are one semitone from mixing (VirtualDJ's "close" match). */
    fun close(key: String?, target: String?): Boolean = bestShift(key, target)?.let { abs(it) == 1 } ?: false
}
