package com.tonyscratch.tsdj.audio

import java.util.concurrent.atomic.AtomicReferenceArray
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan
import kotlin.math.tanh

// =============================================================================================
// The effect list
// =============================================================================================

enum class FxGroup(val label: String) { ECHO("ECHO"), SPACE("SPACE"), BEAT("BEAT"), LOFI("LO-FI") }

/** TOGGLE: tap on/off. HOLD: on while your finger is down. TRIGGER: tap fires once. */
enum class FxMode { TOGGLE, HOLD, TRIGGER }

/**
 * Every effect in TS Dj. [amountLabel] names the intensity control, [paramLabel] the second control.
 * Effects marked [deck] move the record itself (brake, backspin, reverse, censor).
 */
enum class FxType(
    val title: String,
    val short: String,
    val group: FxGroup,
    val mode: FxMode,
    val amountLabel: String,
    val paramLabel: String,
    val defAmount: Float,
    val defParam: Float,
    val deck: Boolean = false,
) {
    ECHO("Echo", "ECHO", FxGroup.ECHO, FxMode.TOGGLE, "MIX", "BEATS", 0.5f, 0.5f),
    ECHO_OUT("Echo Out", "E.OUT", FxGroup.ECHO, FxMode.TOGGLE, "TAIL", "BEATS", 0.6f, 0.5f),
    DELAY("Delay", "DELAY", FxGroup.ECHO, FxMode.TOGGLE, "MIX", "BEATS", 0.5f, 0.67f),
    PING_PONG("Ping Pong", "PING", FxGroup.ECHO, FxMode.TOGGLE, "MIX", "BEATS", 0.5f, 0.5f),
    DUB_ECHO("Dub Echo", "DUB", FxGroup.ECHO, FxMode.TOGGLE, "FEEDBK", "BEATS", 0.5f, 0.67f),
    SPIRAL("Spiral", "SPIRAL", FxGroup.ECHO, FxMode.TOGGLE, "MIX", "BEATS", 0.5f, 0.33f),

    REVERB("Reverb", "VERB", FxGroup.SPACE, FxMode.TOGGLE, "MIX", "SIZE", 0.4f, 0.6f),
    SPACE("Space", "SPACE", FxGroup.SPACE, FxMode.TOGGLE, "MIX", "FILTER", 0.6f, 0.5f),
    FLANGER("Flanger", "FLANG", FxGroup.SPACE, FxMode.TOGGLE, "DEPTH", "BEATS", 0.7f, 0.6f),
    PHASER("Phaser", "PHASE", FxGroup.SPACE, FxMode.TOGGLE, "DEPTH", "BEATS", 0.7f, 0.4f),
    CHORUS("Chorus", "CHORUS", FxGroup.SPACE, FxMode.TOGGLE, "MIX", "RATE", 0.5f, 0.3f),
    SWEEP("Filter Sweep", "SWEEP", FxGroup.SPACE, FxMode.TOGGLE, "DEPTH", "BEATS", 0.8f, 0.4f),
    AUTO_PAN("Auto Pan", "PAN", FxGroup.SPACE, FxMode.TOGGLE, "DEPTH", "BEATS", 0.8f, 0.2f),
    ROBOT("Robot", "ROBOT", FxGroup.SPACE, FxMode.TOGGLE, "MIX", "TONE", 0.6f, 0.4f),

    TRANS("Trans (Gate)", "TRANS", FxGroup.BEAT, FxMode.TOGGLE, "DEPTH", "BEATS", 1f, 0.5f),
    ROLL("Roll", "ROLL", FxGroup.BEAT, FxMode.HOLD, "MIX", "BEATS", 1f, 0.5f),
    REV_ROLL("Reverse Roll", "R.ROLL", FxGroup.BEAT, FxMode.HOLD, "MIX", "BEATS", 1f, 0.67f),
    BRAKE("Brake", "BRAKE", FxGroup.BEAT, FxMode.TRIGGER, "LENGTH", "RESUME", 0.4f, 0f, deck = true),
    BACKSPIN("Backspin", "SPIN", FxGroup.BEAT, FxMode.TRIGGER, "LENGTH", "RESUME", 0.4f, 0f, deck = true),
    REVERSE("Reverse", "REV", FxGroup.BEAT, FxMode.TOGGLE, "SPEED", "SLIP", 1f, 1f, deck = true),
    CENSOR("Censor", "CENSOR", FxGroup.BEAT, FxMode.HOLD, "SPEED", "—", 1f, 1f, deck = true),
    PITCH("Pitch", "PITCH", FxGroup.BEAT, FxMode.TOGGLE, "MIX", "SEMIS", 1f, 0.75f),

    CRUSH("Bitcrusher", "CRUSH", FxGroup.LOFI, FxMode.TOGGLE, "BITS", "RATE", 0.6f, 0.3f),
    DISTORT("Distortion", "DIST", FxGroup.LOFI, FxMode.TOGGLE, "DRIVE", "TONE", 0.5f, 0.7f),
    NOISE("Noise", "NOISE", FxGroup.LOFI, FxMode.TOGGLE, "LEVEL", "FILTER", 0.5f, 0.3f),
    VINYL("Vinyl", "VINYL", FxGroup.LOFI, FxMode.TOGGLE, "LEVEL", "AGE", 0.6f, 0.4f);

    /** Text shown next to the second control. */
    fun paramText(p: Float): String = when (this) {
        ECHO, ECHO_OUT, DELAY, PING_PONG, DUB_ECHO, SPIRAL -> FxBeats.label(FxBeats.pick(FxBeats.ECHO, p))
        FLANGER, PHASER, SWEEP, AUTO_PAN -> FxBeats.label(FxBeats.pick(FxBeats.LFO, p))
        TRANS -> FxBeats.label(FxBeats.pick(FxBeats.GATE, p))
        ROLL, REV_ROLL -> FxBeats.label(FxBeats.pick(FxBeats.ROLL, p))
        BRAKE, BACKSPIN -> if (p >= 0.5f) "ON" else "OFF"
        REVERSE -> if (p >= 0.5f) "ON" else "OFF"
        CENSOR -> "—"
        PITCH -> pitchSemis(p).let { if (it > 0) "+$it" else "$it" }
        else -> "${(p * 100).roundToInt()}%"
    }

    fun amountText(a: Float): String = when (this) {
        BRAKE, BACKSPIN -> String.format(java.util.Locale.US, "%.1fs", deckLengthSec(a))
        else -> "${(a * 100).roundToInt()}%"
    }

    companion object {
        fun inGroup(g: FxGroup): List<FxType> = entries.filter { it.group == g }
        fun pitchSemis(p: Float): Int = (p * 24f - 12f).roundToInt()
        /** Brake / backspin length in seconds from the LENGTH control. */
        fun deckLengthSec(a: Float): Double = 0.25 + a * 2.75
    }
}

object FxBeats {
    val ECHO = doubleArrayOf(0.125, 0.25, 0.5, 0.75, 1.0, 2.0, 4.0)
    val LFO = doubleArrayOf(1.0, 2.0, 4.0, 8.0, 16.0, 32.0)
    val GATE = doubleArrayOf(1 / 16.0, 1 / 8.0, 1 / 4.0, 1 / 2.0, 1.0)
    val ROLL = doubleArrayOf(1 / 32.0, 1 / 16.0, 1 / 8.0, 1 / 4.0, 1 / 2.0, 1.0, 2.0)

    fun pick(values: DoubleArray, p: Float): Double = values[(p * (values.size - 1)).roundToInt().coerceIn(0, values.size - 1)]

    fun label(b: Double): String = when {
        b >= 1.0 -> if (b == floor(b)) b.toInt().toString() else b.toString()
        abs(b - 0.75) < 1e-9 -> "3/4"
        else -> "1/${(1.0 / b).roundToInt()}"
    }
}

/** Live settings for one effect on one deck, written by the screen and read by the audio thread. */
class FxState(val type: FxType) {
    @Volatile var on = false
    @Volatile var amount = type.defAmount
    @Volatile var param = type.defParam
}

/** Timing information the effects need for each block of audio. */
class FxContext {
    /** Length of one beat in output frames. */
    var beatFrames = 24000.0
    /** Beats since the first beat of the track at the start of this block (for beat-synced effects). */
    var beatPhase = 0.0
}

// =============================================================================================
// One deck's effect rack
// =============================================================================================

class DeckFx(private val sr: Int) {
    val states: List<FxState> = FxType.entries.map { FxState(it) }
    private val procs = AtomicReferenceArray<FxProc?>(FxType.entries.size)

    fun state(t: FxType): FxState = states[t.ordinal]

    /** Create the effect's buffers. Call from the UI thread before switching it on. */
    fun prepare(t: FxType) {
        if (t.deck || procs.get(t.ordinal) != null) return
        procs.set(t.ordinal, create(t))
    }

    fun setOn(t: FxType, on: Boolean) {
        if (on) prepare(t)
        state(t).on = on
    }

    fun allOff() {
        for (s in states) s.on = false
    }

    fun anyOn(): Boolean = states.any { it.on && !it.type.deck }

    private fun create(t: FxType): FxProc = when (t) {
        FxType.ECHO, FxType.ECHO_OUT, FxType.DELAY, FxType.PING_PONG, FxType.DUB_ECHO, FxType.SPIRAL -> EchoProc(sr, t)
        FxType.REVERB -> ReverbProc(sr, space = false)
        FxType.SPACE -> ReverbProc(sr, space = true)
        FxType.FLANGER -> FlangerProc(sr)
        FxType.PHASER -> PhaserProc(sr)
        FxType.CHORUS -> ChorusProc(sr)
        FxType.SWEEP -> SweepProc(sr)
        FxType.AUTO_PAN -> PanProc(sr)
        FxType.ROBOT -> RobotProc(sr)
        FxType.TRANS -> TransProc(sr)
        FxType.ROLL -> RollProc(sr, reverse = false)
        FxType.REV_ROLL -> RollProc(sr, reverse = true)
        FxType.PITCH -> PitchProc(sr)
        FxType.CRUSH -> CrushProc(sr)
        FxType.DISTORT -> DistortProc(sr)
        FxType.NOISE -> NoiseProc(sr)
        FxType.VINYL -> VinylProc(sr)
        FxType.BRAKE, FxType.BACKSPIN, FxType.REVERSE, FxType.CENSOR -> error("deck effect")
    }

    /** Audio thread: run every effect that is on (or still ringing out) over the block. */
    fun process(l: FloatArray, r: FloatArray, n: Int, ctx: FxContext) {
        for (t in ORDER) {
            val p = procs.get(t.ordinal) ?: continue
            val s = states[t.ordinal]
            if (s.on || p.active) p.process(s, l, r, n, ctx)
        }
    }

    companion object {
        private val ORDER = listOf(
            FxType.PITCH, FxType.CRUSH, FxType.DISTORT, FxType.NOISE, FxType.VINYL, FxType.ROBOT,
            FxType.TRANS, FxType.SWEEP, FxType.FLANGER, FxType.PHASER, FxType.CHORUS, FxType.AUTO_PAN,
            FxType.ROLL, FxType.REV_ROLL,
            FxType.ECHO, FxType.ECHO_OUT, FxType.DELAY, FxType.PING_PONG, FxType.DUB_ECHO, FxType.SPIRAL,
            FxType.REVERB, FxType.SPACE,
        )
    }
}

// =============================================================================================
// Building blocks
// =============================================================================================

abstract class FxProc(val sr: Int) {
    /** True while the effect is producing sound (on, or an echo/reverb tail still ringing). */
    var active = false
    abstract fun process(s: FxState, l: FloatArray, r: FloatArray, n: Int, ctx: FxContext)
}

/** A delay line with fractional (interpolated) reads. */
class DelayLine(val size: Int) {
    private val buf = FloatArray(size)
    private var w = 0

    /** The sample written [d] samples ago. */
    fun read(d: Double): Float {
        var rp = w - d
        while (rp < 0) rp += size
        val i = rp.toInt()
        val f = (rp - i).toFloat()
        val a = buf[i % size]
        val b = buf[(i + 1) % size]
        return a + (b - a) * f
    }

    fun write(x: Float) {
        buf[w] = x
        w++
        if (w >= size) w = 0
    }

    fun clear() = buf.fill(0f)
}

/** Simple delay-line pitch shifter (two crossfaded read heads). Used by Spiral and Pitch. */
class PitchShifter(sr: Int) {
    private val size = (sr * 0.07).toInt()
    private val line = DelayLine(size + 4)
    private var phase = 0.0

    fun process(x: Float, ratio: Double): Float {
        line.write(x)
        phase += (1.0 - ratio) / size
        if (phase < 0) phase += 1.0
        if (phase >= 1) phase -= 1.0
        val p2 = (phase + 0.5) % 1.0
        val a = line.read(1.0 + phase * size)
        val b = line.read(1.0 + p2 * size)
        val ga = (1.0 - abs(2.0 * phase - 1.0)).toFloat()
        val gb = (1.0 - abs(2.0 * p2 - 1.0)).toFloat()
        return a * ga + b * gb
    }
}

/** Keeps feedback loops from running away: untouched below 1, gently limited above. */
private fun guard(x: Float): Float = if (x > 1f || x < -1f) tanh(x.toDouble()).toFloat() else x

private fun onePole(fc: Double, sr: Int): Float = (1.0 - exp(-2.0 * PI * fc / sr)).toFloat()

private fun frac(x: Double): Double = x - floor(x)

/** Tracks whether a tail has died away so the effect can switch itself off. */
private class TailWatch(private val sr: Int) {
    private var quiet = 0
    fun done(on: Boolean, peak: Float, n: Int): Boolean {
        if (on || peak > 1e-4f) {
            quiet = 0
            return false
        }
        quiet += n
        return quiet > sr / 4
    }
}

// =============================================================================================
// Echo family: Echo, Echo Out, Delay, Ping Pong, Dub Echo, Spiral
// =============================================================================================

private class EchoProc(sr: Int, private val kind: FxType) : FxProc(sr) {
    private val size = (sr * 2.6).toInt()
    private val dl = DelayLine(size)
    private val dr = DelayLine(size)
    private var d = -1.0
    private var wasOn = false
    private var dry = 1f
    private var feedLeft = 0
    private var lpL = 0f
    private var lpR = 0f
    private var lowL = 0f
    private var lowR = 0f
    private val lpA = onePole(2200.0, sr)
    private val lowA = onePole(160.0, sr)
    private val shiftL = if (kind == FxType.SPIRAL) PitchShifter(sr) else null
    private val shiftR = if (kind == FxType.SPIRAL) PitchShifter(sr) else null
    private val tail = TailWatch(sr)

    override fun process(s: FxState, l: FloatArray, r: FloatArray, n: Int, ctx: FxContext) {
        val on = s.on
        val beats = FxBeats.pick(FxBeats.ECHO, s.param)
        val target = (beats * ctx.beatFrames).coerceIn(32.0, size - 4.0)
        if (d < 0) d = target
        if (on && !wasOn) {
            active = true
            if (kind == FxType.ECHO_OUT) feedLeft = target.toInt()
        }
        wasOn = on
        val a = s.amount
        val fb = when (kind) {
            FxType.ECHO -> 0.25f + 0.5f * a
            FxType.ECHO_OUT -> 0.5f + 0.42f * a
            FxType.DELAY -> 0.15f
            FxType.PING_PONG -> 0.35f + 0.45f * a
            FxType.DUB_ECHO -> 0.55f + 0.42f * a
            else -> 0.4f + 0.45f * a // Spiral
        }
        val wet = when (kind) {
            FxType.ECHO_OUT -> 1f
            FxType.DUB_ECHO -> 0.85f
            else -> a
        }
        val dryTarget = if (kind == FxType.ECHO_OUT && on) 0f else 1f
        var peak = 0f
        for (i in 0 until n) {
            d += (target - d) * 0.0008
            val xl = l[i]
            val xr = r[i]
            val el = dl.read(d)
            val er = dr.read(d)
            val feed = on && (kind != FxType.ECHO_OUT || feedLeft > 0)
            if (kind == FxType.ECHO_OUT && feedLeft > 0) feedLeft--
            val inL = if (feed) xl else 0f
            val inR = if (feed) xr else 0f
            var fl = el * fb
            var fr = er * fb
            if (kind == FxType.DUB_ECHO) {
                lpL += lpA * (fl - lpL); lowL += lowA * (lpL - lowL)
                lpR += lpA * (fr - lpR); lowR += lowA * (lpR - lowR)
                fl = tanh(1.4 * (lpL - lowL)).toFloat() / 1.2f
                fr = tanh(1.4 * (lpR - lowR)).toFloat() / 1.2f
            } else if (kind == FxType.SPIRAL) {
                fl = shiftL!!.process(fl, 1.19)
                fr = shiftR!!.process(fr, 1.19)
            }
            if (kind == FxType.PING_PONG) {
                dl.write(guard((inL + inR) * 0.5f + er * fb))
                dr.write(guard(el * fb))
            } else {
                dl.write(guard(inL + fl))
                dr.write(guard(inR + fr))
            }
            dry += (dryTarget - dry) * 0.002f
            l[i] = xl * dry + el * wet
            r[i] = xr * dry + er * wet
            peak = max(peak, abs(el) + abs(er))
        }
        if (tail.done(on, peak, n)) {
            active = false
            dl.clear(); dr.clear()
            d = -1.0
            dry = 1f
        }
    }
}

// =============================================================================================
// Reverb and Space (Freeverb design)
// =============================================================================================

private class Comb(size: Int) {
    private val buf = FloatArray(size)
    private var idx = 0
    private var store = 0f
    fun process(x: Float, fb: Float, damp: Float): Float {
        val o = buf[idx]
        store = o * (1f - damp) + store * damp
        buf[idx] = x + store * fb
        if (++idx >= buf.size) idx = 0
        return o
    }
    fun clear() { buf.fill(0f); store = 0f }
}

private class AllPass(size: Int) {
    private val buf = FloatArray(size)
    private var idx = 0
    fun process(x: Float): Float {
        val b = buf[idx]
        buf[idx] = x + b * 0.5f
        if (++idx >= buf.size) idx = 0
        return b - x
    }
    fun clear() = buf.fill(0f)
}

class Freeverb(sr: Int) {
    private val scale = sr / 44100.0
    private val combL = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617).map { Comb((it * scale).toInt()) }
    private val combR = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617).map { Comb(((it + 23) * scale).toInt()) }
    private val apL = intArrayOf(556, 441, 341, 225).map { AllPass((it * scale).toInt()) }
    private val apR = intArrayOf(556, 441, 341, 225).map { AllPass(((it + 23) * scale).toInt()) }
    var outL = 0f
        private set
    var outR = 0f
        private set

    fun process(input: Float, room: Float, damp: Float) {
        var a = 0f
        var b = 0f
        for (c in combL) a += c.process(input, room, damp)
        for (c in combR) b += c.process(input, room, damp)
        for (p in apL) a = p.process(a)
        for (p in apR) b = p.process(b)
        outL = a
        outR = b
    }

    fun clear() {
        combL.forEach { it.clear() }; combR.forEach { it.clear() }
        apL.forEach { it.clear() }; apR.forEach { it.clear() }
    }
}

private class ReverbProc(sr: Int, private val space: Boolean) : FxProc(sr) {
    private val verb = Freeverb(sr)
    private val hp = Biquad()
    private var lastParam = -1f
    private val tail = TailWatch(sr)

    override fun process(s: FxState, l: FloatArray, r: FloatArray, n: Int, ctx: FxContext) {
        val on = s.on
        if (on) active = true
        val room = if (space) 0.93f else 0.7f + 0.28f * s.param
        val damp = if (space) 0.15f else 0.45f - 0.3f * s.param
        val wet = s.amount * 3f
        if (space && s.param != lastParam) {
            hp.setHighPass(sr, 20.0 * (5000.0 / 20.0).pow(s.param.toDouble()), 1.4)
            lastParam = s.param
        }
        var peak = 0f
        for (i in 0 until n) {
            val input = if (on) (l[i] + r[i]) * 0.015f else 0f
            verb.process(input, room, damp)
            var wl = verb.outL
            var wr = verb.outR
            if (space) {
                wl = hp.processL(wl.toDouble()).toFloat()
                wr = hp.processR(wr.toDouble()).toFloat()
            }
            l[i] += wl * wet
            r[i] += wr * wet
            peak = max(peak, abs(wl) + abs(wr))
        }
        if (tail.done(on, peak, n)) {
            active = false
            verb.clear()
            hp.reset()
        }
    }
}

// =============================================================================================
// Modulation: Flanger, Phaser, Chorus, Filter Sweep, Auto Pan, Robot
// =============================================================================================

/** Triangle wave 0..1..0 over one period. */
private fun tri(phase: Double): Double = 1.0 - abs(2.0 * frac(phase) - 1.0)

private class FlangerProc(sr: Int) : FxProc(sr) {
    private val lineL = DelayLine((sr * 0.012).toInt())
    private val lineR = DelayLine((sr * 0.012).toInt())
    private val minD = sr * 0.0006
    private val maxD = sr * 0.0055
    private var fbL = 0f
    private var fbR = 0f

    override fun process(s: FxState, l: FloatArray, r: FloatArray, n: Int, ctx: FxContext) {
        active = s.on
        val period = FxBeats.pick(FxBeats.LFO, s.param)
        val a = s.amount
        for (i in 0 until n) {
            val ph = (ctx.beatPhase + i / ctx.beatFrames) / period
            val dL = minD + (maxD - minD) * tri(ph)
            val dR = minD + (maxD - minD) * tri(ph + 0.25)
            val yl = lineL.read(dL)
            val yr = lineR.read(dR)
            lineL.write(guard(l[i] + yl * 0.6f))
            lineR.write(guard(r[i] + yr * 0.6f))
            fbL = yl; fbR = yr
            l[i] = (l[i] + a * yl) / (1f + 0.5f * a)
            r[i] = (r[i] + a * yr) / (1f + 0.5f * a)
        }
    }
}

private class PhaserProc(sr: Int) : FxProc(sr) {
    private val stages = 6
    private val x1L = FloatArray(stages); private val y1L = FloatArray(stages)
    private val x1R = FloatArray(stages); private val y1R = FloatArray(stages)
    private var fbL = 0f
    private var fbR = 0f
    private var cL = 0f
    private var cR = 0f

    private fun coef(f: Double): Float {
        val t = tan(PI * f / sr)
        return ((t - 1) / (t + 1)).toFloat()
    }

    override fun process(s: FxState, l: FloatArray, r: FloatArray, n: Int, ctx: FxContext) {
        active = s.on
        val period = FxBeats.pick(FxBeats.LFO, s.param)
        val a = s.amount
        for (i in 0 until n) {
            if (i % 16 == 0) {
                val ph = (ctx.beatPhase + i / ctx.beatFrames) / period
                cL = coef(250.0 * (3500.0 / 250.0).pow(tri(ph)))
                cR = coef(250.0 * (3500.0 / 250.0).pow(tri(ph + 0.25)))
            }
            var xl = l[i] + fbL * 0.55f
            var xr = r[i] + fbR * 0.55f
            for (k in 0 until stages) {
                val yl = cL * xl + x1L[k] - cL * y1L[k]
                x1L[k] = xl; y1L[k] = yl; xl = yl
                val yr = cR * xr + x1R[k] - cR * y1R[k]
                x1R[k] = xr; y1R[k] = yr; xr = yr
            }
            fbL = guard(xl); fbR = guard(xr)
            l[i] = (l[i] + a * xl) / (1f + 0.5f * a)
            r[i] = (r[i] + a * xr) / (1f + 0.5f * a)
        }
    }
}

private class ChorusProc(sr: Int) : FxProc(sr) {
    private val lineL = DelayLine((sr * 0.05).toInt())
    private val lineR = DelayLine((sr * 0.05).toInt())
    private var phase = 0.0

    override fun process(s: FxState, l: FloatArray, r: FloatArray, n: Int, ctx: FxContext) {
        active = s.on
        val rateHz = 0.15 + s.param * 3.0
        val a = s.amount
        val base = sr * 0.016
        val depth = sr * 0.006
        for (i in 0 until n) {
            phase += rateHz / sr
            if (phase >= 1) phase -= 1.0
            lineL.write(l[i])
            lineR.write(r[i])
            val cl = lineL.read(base + depth * sin(2 * PI * phase))
            val cr = lineR.read(base + depth * sin(2 * PI * (phase + 0.5)))
            l[i] = l[i] * (1f - 0.3f * a) + cl * 0.7f * a
            r[i] = r[i] * (1f - 0.3f * a) + cr * 0.7f * a
        }
    }
}

private class SweepProc(sr: Int) : FxProc(sr) {
    private val f = Biquad()
    override fun process(s: FxState, l: FloatArray, r: FloatArray, n: Int, ctx: FxContext) {
        if (!active && s.on) f.reset()
        active = s.on
        val period = FxBeats.pick(FxBeats.LFO, s.param)
        val a = s.amount
        for (i in 0 until n) {
            if (i % 32 == 0) {
                val ph = (ctx.beatPhase + i / ctx.beatFrames) / period
                val fc = (20000.0 * exp(ln(180.0 / 20000.0) * a * (1.0 - tri(ph)))).coerceAtMost(sr * 0.45)
                f.setLowPass(sr, fc, 2.5)
            }
            l[i] = f.processL(l[i].toDouble()).toFloat()
            r[i] = f.processR(r[i].toDouble()).toFloat()
        }
    }
}

private class PanProc(sr: Int) : FxProc(sr) {
    override fun process(s: FxState, l: FloatArray, r: FloatArray, n: Int, ctx: FxContext) {
        active = s.on
        val period = FxBeats.pick(FxBeats.LFO, s.param) / 4.0
        val a = s.amount
        for (i in 0 until n) {
            val ph = (ctx.beatPhase + i / ctx.beatFrames) / period
            val p = sin(2 * PI * ph).toFloat()
            l[i] *= 1f - a * max(0f, p)
            r[i] *= 1f - a * max(0f, -p)
        }
    }
}

private class RobotProc(sr: Int) : FxProc(sr) {
    private var phase = 0.0
    override fun process(s: FxState, l: FloatArray, r: FloatArray, n: Int, ctx: FxContext) {
        active = s.on
        val f = 30.0 * (1200.0 / 30.0).pow(s.param.toDouble())
        val a = s.amount
        for (i in 0 until n) {
            phase += f / sr
            if (phase >= 1) phase -= 1.0
            val c = sin(2 * PI * phase).toFloat() * 1.3f
            l[i] = l[i] * (1f - a) + l[i] * c * a
            r[i] = r[i] * (1f - a) + r[i] * c * a
        }
    }
}

// =============================================================================================
// Beat effects: Trans, Roll, Reverse Roll, Pitch
// =============================================================================================

private class TransProc(sr: Int) : FxProc(sr) {
    private var g = 1f
    override fun process(s: FxState, l: FloatArray, r: FloatArray, n: Int, ctx: FxContext) {
        active = s.on || g < 0.999f
        val div = FxBeats.pick(FxBeats.GATE, s.param)
        val a = s.amount
        for (i in 0 until n) {
            val ph = (ctx.beatPhase + i / ctx.beatFrames) / div
            val target = if (!s.on || frac(ph) < 0.5) 1f else 1f - a
            g += (target - g) * 0.04f
            l[i] *= g
            r[i] *= g
        }
    }
}

/**
 * Loop roll: repeats a short slice of the music while held. The track keeps playing underneath,
 * so when you let go it carries on exactly where it would have been.
 */
class RollProc(sr: Int, private val reverse: Boolean) : FxProc(sr) {
    private val size = sr * 4
    private val bl = FloatArray(size)
    private val br = FloatArray(size)
    private var len = 0
    private var idx = 0
    private var wasOn = false

    override fun process(s: FxState, l: FloatArray, r: FloatArray, n: Int, ctx: FxContext) {
        val on = s.on
        if (!on) {
            active = false
            wasOn = false
            return
        }
        if (!wasOn) {
            val beats = FxBeats.pick(FxBeats.ROLL, s.param)
            len = (beats * ctx.beatFrames).toInt().coerceIn(256, size)
            idx = 0
            wasOn = true
        }
        active = true
        val rev = reverse
        val mix = s.amount
        val fade = 96
        for (i in 0 until n) {
            if (idx < len) {
                bl[idx] = l[i]
                br[idx] = r[i]
            } else {
                val k = (idx - len) % len
                val j = if (rev) len - 1 - k else k
                val e = minOf(1f, k / fade.toFloat(), (len - 1 - k) / fade.toFloat())
                l[i] = l[i] * (1f - mix) + bl[j] * e * mix
                r[i] = r[i] * (1f - mix) + br[j] * e * mix
            }
            idx++
            if (idx >= len * 1000) idx = len // keep the counter small on very long holds
        }
    }
}

private class PitchProc(sr: Int) : FxProc(sr) {
    private val sl = PitchShifter(sr)
    private val sr2 = PitchShifter(sr)
    override fun process(s: FxState, l: FloatArray, r: FloatArray, n: Int, ctx: FxContext) {
        active = s.on
        val ratio = 2.0.pow(FxType.pitchSemis(s.param) / 12.0)
        val a = s.amount
        for (i in 0 until n) {
            val pl = sl.process(l[i], ratio)
            val pr = sr2.process(r[i], ratio)
            l[i] = l[i] * (1f - a) + pl * a
            r[i] = r[i] * (1f - a) + pr * a
        }
    }
}

// =============================================================================================
// Lo-fi: Bitcrusher, Distortion, Noise, Vinyl
// =============================================================================================

private class CrushProc(sr: Int) : FxProc(sr) {
    private var holdL = 0f
    private var holdR = 0f
    private var count = 0
    override fun process(s: FxState, l: FloatArray, r: FloatArray, n: Int, ctx: FxContext) {
        active = s.on
        val bits = 16f - s.amount * 13f
        val levels = 2f.pow(bits - 1f)
        val hold = 1 + (s.param * 31).toInt()
        for (i in 0 until n) {
            if (count <= 0) {
                holdL = (l[i] * levels).roundToInt() / levels
                holdR = (r[i] * levels).roundToInt() / levels
                count = hold
            }
            count--
            l[i] = holdL
            r[i] = holdR
        }
    }
}

private class DistortProc(sr: Int) : FxProc(sr) {
    private var lpL = 0f
    private var lpR = 0f
    override fun process(s: FxState, l: FloatArray, r: FloatArray, n: Int, ctx: FxContext) {
        active = s.on
        val drive = 1.0 + s.amount * 24.0
        val norm = 1.0 / tanh(drive) / kotlin.math.sqrt(drive)
        val a = onePole(800.0 * (18000.0 / 800.0).pow(s.param.toDouble()), sr)
        for (i in 0 until n) {
            val dl = (tanh(drive * l[i]) * norm).toFloat() * 0.8f
            val dr = (tanh(drive * r[i]) * norm).toFloat() * 0.8f
            lpL += a * (dl - lpL)
            lpR += a * (dr - lpR)
            l[i] = lpL
            r[i] = lpR
        }
    }
}

/** Cheap fast random numbers for noise (no allocation on the audio thread). */
private class Rng(seed: Int) {
    private var x = seed
    fun next(): Float {
        x = x xor (x shl 13); x = x xor (x ushr 17); x = x xor (x shl 5)
        return (x and 0x7fffffff) / 1073741823.5f - 1f
    }
}

private class NoiseProc(sr: Int) : FxProc(sr) {
    private val rng = Rng(12345)
    private val f = Biquad()
    private var last = -1f
    override fun process(s: FxState, l: FloatArray, r: FloatArray, n: Int, ctx: FxContext) {
        active = s.on
        val p = s.param
        if (p != last) {
            if (p < 0.5f) f.setLowPass(sr, (200.0 * (18000.0 / 200.0).pow(p * 2.0)).coerceAtMost(sr * 0.45), 1.2)
            else f.setHighPass(sr, 20.0 * (9000.0 / 20.0).pow((p - 0.5) * 2.0), 1.2)
            last = p
        }
        val level = s.amount * 0.3f
        for (i in 0 until n) {
            l[i] += f.processL(rng.next().toDouble()).toFloat() * level
            r[i] += f.processR(rng.next().toDouble()).toFloat() * level
        }
    }
}

private class VinylProc(sr: Int) : FxProc(sr) {
    private val rng = Rng(777)
    private var pop = 0f
    private var lpL = 0f
    private var lpR = 0f
    private var hiss = 0f
    private val hissA = onePole(5000.0, sr)
    override fun process(s: FxState, l: FloatArray, r: FloatArray, n: Int, ctx: FxContext) {
        active = s.on
        val a = s.amount
        val age = s.param
        val lpA = onePole(18000.0 * (2500.0 / 18000.0).pow(age.toDouble()), sr)
        val mono = age * 0.5f
        val popChance = a * 0.00035f
        for (i in 0 until n) {
            if ((rng.next() + 1f) * 0.5f < popChance) pop = (0.05f + 0.3f * (rng.next() + 1f) * 0.5f) * a
            val crackle = pop * if (rng.next() > 0f) 1f else -1f
            pop *= 0.86f
            hiss += hissA * (rng.next() - hiss)
            val m = (l[i] + r[i]) * 0.5f
            val xl = l[i] * (1f - mono) + m * mono
            val xr = r[i] * (1f - mono) + m * mono
            lpL += lpA * (xl - lpL)
            lpR += lpA * (xr - lpR)
            l[i] = lpL + crackle + hiss * 0.02f * a
            r[i] = lpR + crackle + hiss * 0.02f * a
        }
    }
}
