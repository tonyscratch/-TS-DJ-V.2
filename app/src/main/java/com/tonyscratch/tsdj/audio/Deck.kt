package com.tonyscratch.tsdj.audio

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

/**
 * One turntable: the loaded track, transport (play, cue, 8 hot cues, loops, beat jump),
 * tempo (pitch, keylock, key shift, sync, nudge), scratching, slip mode, deck effects
 * (brake, backspin, reverse, censor), EQ, filter and the effect rack.
 *
 * Threading: the UI thread calls the public functions; the audio thread calls [render].
 * Shared fields are @Volatile. Position changes from the UI go through a "seek" request
 * that the audio thread picks up at the start of its next block.
 */
class Deck(val index: Int, private val sampleRate: Int) {

    @Volatile var track: TrackData? = null
        private set
    @Volatile var info: TrackInfo? = null
        private set
    @Volatile var analysis: TrackAnalysis? = null

    /** Hot cue positions in frames; -1 means empty. */
    val hotCues = DoubleArray(HOT_CUES) { -1.0 }
    @Volatile var hotCueVersion = 0
        private set

    // ---- Transport -------------------------------------------------------------------------
    @Volatile var playing = false
        private set
    @Volatile var position = 0.0
        private set
    @Volatile var cuePoint = 0.0
        private set
    @Volatile private var cueHeld = false
    @Volatile private var seekTarget = 0.0
    @Volatile private var seekSerial = 0
    private var handledSeekSerial = 0

    // ---- Tempo and key ---------------------------------------------------------------------
    /** Speed change as a fraction: 0.012 = +1.2%. */
    @Volatile var pitch = 0.0
    @Volatile var pitchRange = 0.08
    @Volatile var nudge = 0.0
    @Volatile var synced = false
    /** Keylock (master tempo): tempo changes keep the song in its original key. */
    @Volatile var keylock = true
    /** Key change in semitones, independent of tempo. */
    @Volatile var keyShift = 0
    /** Snap cues, loops and jumps to the beat grid. Set from Settings. */
    @Volatile var quantize = true
    /** Slip mode: after a scratch, loop or reverse, carry on where the track would have been. */
    @Volatile var slip = false

    // ---- Scratch ---------------------------------------------------------------------------
    @Volatile var scratching = false
        private set
    @Volatile var scratchTarget = 0.0
        private set
    @Volatile var grip = 0.7

    // ---- Loop ------------------------------------------------------------------------------
    @Volatile var loopActive = false
        private set
    @Volatile var loopIn = 0.0
        private set
    @Volatile var loopOut = 0.0
        private set
    /** Length of the current or next auto loop in beats (1/32 … 32). */
    @Volatile var loopBeats = 4.0
    @Volatile var loopInMark = -1.0
        private set

    // ---- Deck effects ----------------------------------------------------------------------
    @Volatile var reverse = false
    @Volatile var reverseSlip = true
    @Volatile var censor = false
    @Volatile var reverseSpeed = 1.0
    @Volatile private var brakeSerial = 0
    @Volatile private var brakeSec = 1.0
    @Volatile private var brakeResume = false
    @Volatile private var spinSerial = 0
    @Volatile private var spinSec = 1.0
    @Volatile private var spinResume = false
    @Volatile var braking = false
        private set

    // ---- Channel ---------------------------------------------------------------------------
    @Volatile var eqHigh = 0.5f
    @Volatile var eqLow = 0.5f
    @Volatile var filter = 0.5f
    @Volatile var fader = 0.8f
    /** Headphone pre-listen (only heard when split output is on). */
    @Volatile var pfl = false
    /** Auto-gain: brings quiet and loud tracks to a similar level. */
    @Volatile var gain = 1f
    @Volatile var level = 0f

    val fx = DeckFx(sampleRate)
    private val fxCtx = FxContext()

    // ---- Audio-thread-only state -----------------------------------------------------------
    private var rate = 0.0
    private val lowShelf = Biquad()
    private val highShelf = Biquad()
    private val sweep = Biquad()
    private var lastHigh = -1f
    private var lastLow = -1f
    private var lastFilter = -1f
    private var filterMode = 0
    private val stretcher = Stretcher(sampleRate)
    private var stretchOn = false
    private var xfade = 0
    private var slipPos = 0.0
    private var wasSlipping = false
    private var handledBrake = 0
    private var handledSpin = 0
    private var motion = 0 // 0 none, 1 brake, 2 backspin
    private var motionLeft = 0
    private var motionTotal = 1
    private var motionStartRate = 0.0
    private var motionResume = false

    // =======================================================================================
    // UI-thread API
    // =======================================================================================

    fun load(data: TrackData, trackInfo: TrackInfo, trackAnalysis: TrackAnalysis?, savedCues: DoubleArray?, resetPitch: Boolean) {
        playing = false
        cueHeld = false
        scratching = false
        loopActive = false
        loopInMark = -1.0
        reverse = false
        censor = false
        nudge = 0.0
        synced = false
        keyShift = 0
        if (resetPitch) pitch = 0.0
        analysis = trackAnalysis
        info = trackInfo
        gain = data.autoGain
        track = data
        for (i in 0 until HOT_CUES) hotCues[i] = savedCues?.getOrNull(i) ?: -1.0
        hotCueVersion++
        val start = (trackAnalysis?.firstBeatSec ?: 0.0) * sampleRate
        cuePoint = start.coerceIn(0.0, max(0.0, data.frames - 1.0))
        seek(cuePoint)
    }

    fun eject() {
        playing = false
        track = null
        info = null
        analysis = null
        loopActive = false
        for (i in 0 until HOT_CUES) hotCues[i] = -1.0
        hotCueVersion++
        seek(0.0)
    }

    fun seek(frame: Double) {
        val t = track
        val limit = if (t != null) t.frames - 1.0 else 0.0
        seekTarget = frame.coerceIn(0.0, max(0.0, limit))
        seekSerial++
        position = seekTarget
    }

    fun togglePlay() {
        if (track == null) return
        if (cueHeld) {
            cueHeld = false
            return
        }
        playing = !playing
    }

    fun setPlaying(on: Boolean) {
        if (track == null) return
        cueHeld = false
        playing = on
    }

    fun cuePress() {
        if (track == null) return
        if (playing && !cueHeld) {
            playing = false
            seek(cuePoint)
        } else {
            if (abs(position - cuePoint) > sampleRate * 0.01) cuePoint = snap(position)
            seek(cuePoint)
            cueHeld = true
            playing = true
        }
    }

    fun cueRelease() {
        if (cueHeld) {
            cueHeld = false
            playing = false
            seek(cuePoint)
        }
    }

    /** Tap a hot cue: empty pads store the current spot; set pads jump there and play. */
    fun hotCue(slot: Int) {
        if (track == null) return
        if (hotCues[slot] < 0) {
            hotCues[slot] = snap(position)
            hotCueVersion++
        } else {
            jumpTo(hotCues[slot])
            if (!playing) playing = true
            cueHeld = false
        }
    }

    fun clearHotCue(slot: Int) {
        hotCues[slot] = -1.0
        hotCueVersion++
    }

    /** Jump to [frame]; with quantize on while playing, keep the beat in phase. */
    fun jumpTo(frame: Double) {
        val beat = beatLengthFrames()
        if (quantize && playing && beat != null) {
            val first = (analysis?.firstBeatSec ?: 0.0) * sampleRate
            val phase = (position - first) / beat
            val offset = (phase - floor(phase)) * beat
            val target = frame + offset
            seek(target)
        } else {
            seek(frame)
        }
    }

    /** Move by a number of beats (negative = backwards). Moves an active loop with it. */
    fun beatJump(beats: Double) {
        val beat = beatLengthFrames() ?: (sampleRate * 0.5)
        val delta = beats * beat
        if (loopActive) {
            loopIn += delta
            loopOut += delta
        }
        seek(position + delta)
    }

    /** Start an auto loop of [beats] from the current beat, or stop it if it's already that size. */
    fun autoLoop(beats: Double) {
        val t = track ?: return
        if (loopActive && abs(beats - loopBeats) < 1e-9) {
            exitLoop()
            return
        }
        loopBeats = beats
        val beat = beatLengthFrames() ?: (sampleRate * 0.5)
        val start = if (loopActive) loopIn else snapDown(position)
        loopIn = start
        loopOut = (start + beat * beats).coerceAtMost(t.frames - 1.0)
        loopActive = loopOut - loopIn > 32
    }

    fun loopHalf() = resizeLoop(0.5)
    fun loopDouble() = resizeLoop(2.0)

    private fun resizeLoop(factor: Double) {
        loopBeats = (loopBeats * factor).coerceIn(1 / 32.0, 32.0)
        if (loopActive) {
            val t = track ?: return
            val beat = beatLengthFrames() ?: (sampleRate * 0.5)
            loopOut = (loopIn + beat * loopBeats).coerceAtMost(t.frames - 1.0)
        }
    }

    fun markLoopIn() {
        if (track == null) return
        loopInMark = snap(position)
        loopActive = false
    }

    fun markLoopOut() {
        val inMark = loopInMark
        if (track == null || inMark < 0) return
        val out = snap(position)
        if (out - inMark > 64) {
            loopIn = inMark
            loopOut = out
            beatLengthFrames()?.let { loopBeats = (out - inMark) / it }
            loopActive = true
        }
    }

    fun exitLoop() {
        loopActive = false
    }

    fun reloop() {
        if (loopOut > loopIn) {
            loopActive = true
            seek(loopIn)
        }
    }

    /** True while a loop-roll pad is held. */
    @Volatile var rolling = false
        private set
    private var savedLoop: Triple<Boolean, Double, Double>? = null

    /**
     * Loop roll (ROLL pads): loops [beats] from the current beat while held, with the track
     * carrying on underneath — on release it continues exactly where it would have been.
     */
    fun startRoll(beats: Double) {
        if (track == null || !playing) return
        val beat = beatLengthFrames() ?: (sampleRate * 0.5)
        val len = max(64.0, beat * beats)
        val first = (analysis?.firstBeatSec ?: 0.0) * sampleRate
        val start = if (quantize && analysis != null) first + floor((position - first) / len) * len else position
        if (!rolling) savedLoop = Triple(loopActive, loopIn, loopOut)
        loopIn = start.coerceAtLeast(0.0)
        loopOut = loopIn + len
        loopActive = true
        rolling = true
    }

    fun stopRoll() {
        if (!rolling) return
        val saved = savedLoop
        if (saved != null) {
            loopActive = saved.first
            loopIn = saved.second
            loopOut = saved.third
        } else {
            loopActive = false
        }
        rolling = false
    }

    fun startScratch() {
        if (track == null) return
        scratchTarget = position
        scratching = true
    }

    fun scratchBy(deltaFrames: Double) {
        if (scratching) scratchTarget += deltaFrames
    }

    fun endScratch() {
        scratching = false
    }

    /** Vinyl brake: slows to a stop over [seconds]. With [resume], carries on afterwards (slip). */
    fun brake(seconds: Double, resume: Boolean) {
        if (track == null || !playing) return
        brakeSec = seconds
        brakeResume = resume
        brakeSerial++
    }

    /** Backspin: spins the record backwards and lets it slow down. */
    fun backspin(seconds: Double, resume: Boolean) {
        if (track == null || !playing) return
        spinSec = seconds
        spinResume = resume
        spinSerial++
    }

    /** Set the beat grid so a beat lands exactly here. */
    fun setGridHere() {
        val a = analysis ?: return
        val beatSec = 60.0 / a.bpm
        val here = position / sampleRate
        val first = here - floor(here / beatSec) * beatSec
        analysis = a.copy(firstBeatSec = first)
    }

    fun effectiveBpm(): Double? = analysis?.bpm?.let { it * (1.0 + pitch) }

    /** The key you hear right now: original key + key shift (+ the pitch change when keylock is off). */
    fun currentKey(): String? {
        val k = analysis?.key ?: return null
        val pitchSemis = if (keylock) 0 else Keys.semitonesFromRate(1.0 + pitch)
        return Keys.shift(k, keyShift + pitchSemis)
    }

    fun beatLengthFrames(): Double? = analysis?.bpm?.takeIf { it > 0 }?.let { 60.0 / it * sampleRate }

    fun remainingSec(): Double {
        val t = track ?: return 0.0
        return (t.frames - position) / sampleRate / (1.0 + pitch)
    }

    /** Match tempo to [other]; if both are playing, line up the beats too. */
    fun syncTo(other: Deck): Boolean {
        val myBpm = analysis?.bpm ?: return false
        val otherBpm = other.analysis?.bpm ?: return false
        val otherEff = otherBpm * (1.0 + other.pitch)
        var best = otherEff / myBpm - 1.0
        for (r in doubleArrayOf(1.0, 2.0, 0.5)) {
            val p = otherEff * r / myBpm - 1.0
            if (abs(p) < abs(best)) best = p
        }
        val needed = abs(best)
        pitchRange = when {
            needed <= 0.08 -> max(pitchRange, 0.08)
            needed <= 0.16 -> max(pitchRange, 0.16)
            else -> 0.5
        }
        pitch = best.coerceIn(-pitchRange, pitchRange)
        synced = true
        if (other.playing && track != null) {
            val myBeatSec = 60.0 / myBpm
            val otherBeatSec = 60.0 / otherBpm
            val myPhase = frac((position / sampleRate - (analysis?.firstBeatSec ?: 0.0)) / myBeatSec)
            val otherPhase = frac((other.position / sampleRate - (other.analysis?.firstBeatSec ?: 0.0)) / otherBeatSec)
            var diff = otherPhase - myPhase
            if (diff > 0.5) diff -= 1.0
            if (diff < -0.5) diff += 1.0
            seek(position + diff * myBeatSec * sampleRate)
        }
        return true
    }

    /** Nearest beat when quantize is on. */
    private fun snap(frame: Double): Double {
        val beat = beatLengthFrames()
        if (!quantize || beat == null) return frame
        val first = (analysis?.firstBeatSec ?: 0.0) * sampleRate
        return (first + Math.round((frame - first) / beat) * beat).coerceAtLeast(0.0)
    }

    /** The beat at or just before [frame] when quantize is on. */
    private fun snapDown(frame: Double): Double {
        val beat = beatLengthFrames()
        if (!quantize || beat == null) return frame
        val first = (analysis?.firstBeatSec ?: 0.0) * sampleRate
        // A small tolerance so pressing just after a beat still catches that beat.
        return (first + floor((frame - first) / beat + 0.08) * beat).coerceAtLeast(0.0)
    }

    // =======================================================================================
    // Audio thread
    // =======================================================================================

    fun render(outL: FloatArray, outR: FloatArray, n: Int) {
        val t = track
        var pos: Double
        val serial = seekSerial
        if (serial != handledSeekSerial) {
            handledSeekSerial = serial
            pos = seekTarget
            if (scratching) scratchTarget = pos
            slipPos = pos
            wasSlipping = false
        } else {
            pos = position
        }

        if (t == null) {
            for (i in 0 until n) { outL[i] = 0f; outR[i] = 0f }
            rate = 0.0
            position = pos
            motion = 0
            braking = false
            return
        }

        // ---- Brake / backspin requests --------------------------------------------------
        if (brakeSerial != handledBrake) {
            handledBrake = brakeSerial
            if (playing) startMotion(1, brakeSec, brakeResume, if (rate > 0.05) rate else 1.0 + pitch)
        }
        if (spinSerial != handledSpin) {
            handledSpin = spinSerial
            if (playing) startMotion(2, spinSec, spinResume, -3.5)
        }

        // ---- Slip ----------------------------------------------------------------------
        val slipping = (slip && (scratching || loopActive)) || rolling || (reverse && (slip || reverseSlip)) || censor ||
            (motion != 0 && motionResume)
        if (wasSlipping && !slipping) {
            pos = slipPos
            rate = if (playing) 1.0 + pitch + nudge else 0.0
            stretcher.reset()
        }
        wasSlipping = slipping

        updateFilters()

        // ---- Speed for this block ------------------------------------------------------
        val startRate = rate
        val backwards = reverse || censor
        if (scratching) {
            val desired = ((scratchTarget - pos) / n).coerceIn(-10.0, 10.0)
            val follow = 0.15 + 0.8 * grip.coerceIn(0.0, 1.0)
            rate += (desired - rate) * follow
            motion = 0
        } else if (motion != 0) {
            val f = (motionLeft.toDouble() / motionTotal).coerceIn(0.0, 1.0)
            rate = if (motion == 1) motionStartRate * f else motionStartRate * f * f
            motionLeft -= n
            if (motionLeft <= 0) {
                motion = 0
                if (motionResume) {
                    rate = 1.0 + pitch
                } else {
                    playing = false
                    rate = 0.0
                }
            }
        } else {
            val base = 1.0 + pitch + nudge
            val target = if (playing) (if (backwards) -base * reverseSpeed else base) else 0.0
            val approach = if (abs(target) > abs(rate)) 0.25 else 0.12
            rate += (target - rate) * approach
            if (abs(rate - target) < 1e-4) rate = target
        }
        braking = motion != 0
        val endRate = rate

        // ---- Keylock / key shift: use the time-stretcher when the pitch must differ from the speed
        val keyFactor = 2.0.pow(keyShift / 12.0)
        val wantedPitch = if (keylock) keyFactor else endRate * keyFactor
        val canStretch = !scratching && motion == 0 && !backwards && endRate > 0.3 && endRate < 2.5
        val useStretch = canStretch && abs(wantedPitch - endRate) > 1e-4
        if (useStretch != stretchOn) {
            if (useStretch) stretcher.reset()
            stretchOn = useStretch
            xfade = XFADE
        }

        val pcm = t.pcm
        val frames = t.frames
        val blockStart = pos
        val loopOn = loopActive
        val lIn = loopIn
        val lOut = loopOut
        val mode = filterMode
        val g = gain

        for (i in 0 until n) {
            val r = startRate + (endRate - startRate) * (i + 1) / n
            var l: Double
            var rr: Double
            if (stretchOn) {
                stretcher.next(pcm, frames, pos, wantedPitch)
                l = stretcher.l.toDouble()
                rr = stretcher.r.toDouble()
                if (xfade > 0) {
                    val k = xfade.toDouble() / XFADE
                    l = l * (1 - k) + Stretcher.interp(pcm, frames, pos, 0) * k
                    rr = rr * (1 - k) + Stretcher.interp(pcm, frames, pos, 1) * k
                    xfade--
                }
            } else {
                l = if (pos >= 0.0 && pos < frames) Stretcher.interp(pcm, frames, pos, 0) else 0.0
                rr = if (pos >= 0.0 && pos < frames) Stretcher.interp(pcm, frames, pos, 1) else 0.0
                if (xfade > 0) {
                    // Fade in from silence after leaving the stretcher, avoiding a click.
                    val k = 1.0 - xfade.toDouble() / XFADE
                    l *= k
                    rr *= k
                    xfade--
                }
            }
            pos += r
            if (loopOn && r > 0 && pos >= lOut && lOut > lIn) pos -= (lOut - lIn)
            if (pos >= frames) {
                pos = frames.toDouble()
                if (!scratching) playing = false
            } else if (pos < 0.0) {
                pos = 0.0
                if (backwards && !scratching) playing = slipping && playing
            }

            l *= g
            rr *= g
            l = highShelf.processL(lowShelf.processL(l))
            rr = highShelf.processR(lowShelf.processR(rr))
            if (mode != 0) {
                l = sweep.processL(l)
                rr = sweep.processR(rr)
            }
            outL[i] = l.toFloat()
            outR[i] = rr.toFloat()
        }

        // ---- Slip tracking ---------------------------------------------------------------
        if (slipping) {
            if (playing || motion != 0) slipPos += (1.0 + pitch) * n
        } else {
            slipPos = pos
        }
        position = pos

        // ---- Effects --------------------------------------------------------------------
        val bpm = analysis?.bpm ?: 120.0
        val effRate = max(0.25, abs(1.0 + pitch))
        fxCtx.beatFrames = 60.0 / (bpm * effRate) * sampleRate
        val beatSec = 60.0 / bpm
        fxCtx.beatPhase = (blockStart / sampleRate - (analysis?.firstBeatSec ?: 0.0)) / beatSec
        fx.process(outL, outR, n, fxCtx)
    }

    private fun startMotion(kind: Int, seconds: Double, resume: Boolean, startRate: Double) {
        motion = kind
        motionTotal = max(1, (seconds * sampleRate).toInt())
        motionLeft = motionTotal
        motionResume = resume
        motionStartRate = startRate
        slipPos = position
    }

    private fun updateFilters() {
        val hi = eqHigh
        if (hi != lastHigh) {
            highShelf.setHighShelf(sampleRate, 2500.0, eqGainDb(hi))
            lastHigh = hi
        }
        val lo = eqLow
        if (lo != lastLow) {
            lowShelf.setLowShelf(sampleRate, 300.0, eqGainDb(lo))
            lastLow = lo
        }
        val f = filter
        if (f != lastFilter) {
            val newMode = when {
                f < 0.47f -> 1
                f > 0.53f -> 2
                else -> 0
            }
            if (newMode != filterMode) {
                sweep.reset()
                filterMode = newMode
            }
            val nyquistSafe = sampleRate * 0.45
            when (newMode) {
                1 -> sweep.setLowPass(sampleRate, (20000.0 * (200.0 / 20000.0).pow((0.47 - f) / 0.47)).coerceAtMost(nyquistSafe), 0.9)
                2 -> sweep.setHighPass(sampleRate, 30.0 * (6000.0 / 30.0).pow((f - 0.53) / 0.47), 0.9)
            }
            lastFilter = f
        }
    }

    companion object {
        const val HOT_CUES = 8
        private const val XFADE = 256

        fun eqGainDb(k: Float): Double {
            return if (k < 0.5f) {
                val x = (k * 2.0).coerceAtLeast(0.01)
                20.0 * log10(x * x)
            } else {
                (k - 0.5) * 2.0 * 6.0
            }
        }

        private fun frac(x: Double): Double = x - floor(x)
    }
}
