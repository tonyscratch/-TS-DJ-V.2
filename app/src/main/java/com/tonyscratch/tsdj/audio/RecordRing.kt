package com.tonyscratch.tsdj.audio

import java.util.concurrent.atomic.AtomicLong

/**
 * A lock-free single-producer / single-consumer ring of floats.
 * The audio thread writes the master output into it; the recorder thread drains it to disk.
 * If the disk ever falls behind, new audio is dropped rather than blocking the audio thread.
 */
class RecordRing(capacityFloats: Int) {
    private val buf = FloatArray(capacityFloats)
    private val cap = capacityFloats.toLong()
    private val writePos = AtomicLong(0)
    private val readPos = AtomicLong(0)
    val dropped = AtomicLong(0)

    /** Audio thread. */
    fun write(src: FloatArray, count: Int) {
        val w = writePos.get()
        val r = readPos.get()
        val free = cap - (w - r)
        if (count > free) {
            dropped.addAndGet(count.toLong())
            return
        }
        for (i in 0 until count) buf[((w + i) % cap).toInt()] = src[i]
        writePos.set(w + count)
    }

    /** Recorder thread. Returns how many floats were copied into [dst]. */
    fun read(dst: FloatArray): Int {
        val w = writePos.get()
        val r = readPos.get()
        val available = (w - r).toInt()
        val n = minOf(available, dst.size)
        for (i in 0 until n) dst[i] = buf[((r + i) % cap).toInt()]
        readPos.set(r + n)
        return n
    }
}
