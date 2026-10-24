package com.tonyscratch.tsdj.audio

import java.util.concurrent.ConcurrentLinkedQueue

/** A ready-to-play sound (stereo, at the engine's sample rate). */
class SampleSound(val name: String, val l: FloatArray, val r: FloatArray) {
    val frames: Int get() = l.size
}

/** A bank of up to 8 sounds. Custom banks hold sounds you pick from your phone. */
class SampleBank(val name: String, val custom: Boolean, val sounds: Array<SampleSound?>)

/**
 * The sampler: plays up to 12 sounds at once on top of the mix (they go straight to the
 * master output, so they're heard and recorded but aren't affected by the decks' EQ or FX).
 */
class Sampler {
    @Volatile var volume = 0.8f

    private class Voice {
        var sound: SampleSound? = null
        var pos = 0
        var stopping = 0
    }

    private sealed class Cmd {
        class Play(val sound: SampleSound) : Cmd()
        class Stop(val sound: SampleSound?) : Cmd()
    }

    private val voices = Array(12) { Voice() }
    private val commands = ConcurrentLinkedQueue<Cmd>()

    /** Sounds playing right now (for lighting up pads). Written by the audio thread. */
    @Volatile var playing: Set<SampleSound> = emptySet()
        private set

    fun play(sound: SampleSound) {
        commands.offer(Cmd.Play(sound))
    }

    fun stop(sound: SampleSound) {
        commands.offer(Cmd.Stop(sound))
    }

    fun stopAll() {
        commands.offer(Cmd.Stop(null))
    }

    /** Audio thread: add the sampler's sound into the mix buffers. */
    fun render(mixL: FloatArray, mixR: FloatArray, n: Int) {
        var changed = false
        while (true) {
            val c = commands.poll() ?: break
            changed = true
            when (c) {
                is Cmd.Play -> {
                    // Re-trigger if this sound is already playing, else use a free (or the oldest) voice.
                    val v = voices.firstOrNull { it.sound === c.sound }
                        ?: voices.firstOrNull { it.sound == null }
                        ?: voices.maxByOrNull { it.pos }!!
                    v.sound = c.sound
                    v.pos = 0
                    v.stopping = 0
                }
                is Cmd.Stop -> for (v in voices) {
                    if (v.sound != null && (c.sound == null || v.sound === c.sound)) v.stopping = 256
                }
            }
        }
        val vol = volume
        for (v in voices) {
            val s = v.sound ?: continue
            var p = v.pos
            val frames = s.frames
            for (i in 0 until n) {
                if (p >= frames) break
                var g = vol
                if (v.stopping > 0) {
                    g *= v.stopping / 256f
                    v.stopping--
                    if (v.stopping == 0) { p = frames; break }
                }
                mixL[i] += s.l[p] * g
                mixR[i] += s.r[p] * g
                p++
            }
            v.pos = p
            if (p >= frames) {
                v.sound = null
                changed = true
            }
        }
        if (changed) {
            val set = HashSet<SampleSound>()
            for (v in voices) v.sound?.let { set.add(it) }
            playing = set
        }
    }
}
