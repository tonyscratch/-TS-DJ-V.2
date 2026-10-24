package com.tonyscratch.tsdj.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tonyscratch.tsdj.audio.Deck
import com.tonyscratch.tsdj.audio.TrackData
import com.tonyscratch.tsdj.data.PenSettings
import com.tonyscratch.tsdj.data.PressureCurve
import com.tonyscratch.tsdj.ui.theme.TS
import com.tonyscratch.tsdj.ui.theme.Type
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** How tightly the platter follows the pen, from pen pressure and the S Pen settings. */
fun gripFrom(pressure: Float, isPen: Boolean, pen: PenSettings): Double {
    if (!isPen) return pen.gripBase.toDouble()
    val p = pressure.coerceIn(0f, 1f).toDouble()
    val curved = when (pen.curve) {
        PressureCurve.SOFT -> p.pow(0.6)
        PressureCurve.LINEAR -> p
        PressureCurve.FIRM -> p.pow(1.6)
    }
    return (pen.gripBase * (0.5 + curved)).coerceIn(0.1, 1.0)
}

private class JogTouch {
    var mode = 0 // 0 = none, 1 = scratch, 2 = nudge (rim or pen button)
    var lastAngle = 0.0
}

/**
 * The jog wheel. Touch the platter to scratch; touch the outer rim (or hold the S Pen
 * button while touching) to nudge the tempo. With the S Pen, pressing harder grips the
 * record more tightly. Hovering the pen over the wheel lights up the ring.
 */
@Composable
fun JogWheel(
    deck: Deck,
    color: Color,
    sampleRate: Int,
    pen: PenSettings,
    liveGrip: MutableFloatState,
    onTouch: () -> Unit,
    onPenSeen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tick = LocalFrameTick.current
    var hover by remember { mutableStateOf(false) }
    val touch = remember { JogTouch() }
    val penNow by rememberUpdatedState(pen)
    val framesPerRev = 1.8 * sampleRate // a 33⅓ rpm record turns once every 1.8 s

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .semantics { contentDescription = "Jog wheel, deck ${deck.index + 1}" }
                .pointerInput(deck) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: continue
                            val isPen = change.type == PointerType.Stylus || change.type == PointerType.Eraser
                            if (isPen) onPenSeen()
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            val dx = change.position.x - cx
                            val dy = change.position.y - cy
                            val angle = atan2(dy, dx).toDouble()
                            val radius = hypot(dx, dy) / (min(size.width, size.height) / 2f)
                            when (event.type) {
                                PointerEventType.Press -> {
                                    if (radius <= 1.05f) {
                                        onTouch()
                                        touch.lastAngle = angle
                                        val penButton = isPen && event.buttons.isSecondaryPressed && penNow.penButtonBend
                                        if (radius > 0.82f || penButton) {
                                            touch.mode = 2
                                        } else {
                                            touch.mode = 1
                                            deck.grip = gripFrom(change.pressure, isPen, penNow)
                                            if (isPen) liveGrip.floatValue = deck.grip.toFloat()
                                            deck.startScratch()
                                        }
                                        change.consume()
                                    }
                                }
                                PointerEventType.Move -> {
                                    if (change.pressed && touch.mode != 0) {
                                        var d = angle - touch.lastAngle
                                        while (d > PI) d -= 2 * PI
                                        while (d < -PI) d += 2 * PI
                                        touch.lastAngle = angle
                                        if (touch.mode == 1) {
                                            deck.grip = gripFrom(change.pressure, isPen, penNow)
                                            if (isPen) liveGrip.floatValue = deck.grip.toFloat()
                                            deck.scratchBy(d / (2 * PI) * framesPerRev)
                                        } else {
                                            val dt = max(1L, change.uptimeMillis - change.previousUptimeMillis) / 1000.0
                                            val revPerSec = d / (2 * PI) / dt
                                            deck.nudge = (revPerSec * 0.12).coerceIn(-0.3, 0.3)
                                        }
                                        change.consume()
                                    } else if (!change.pressed) {
                                        hover = isPen && radius <= 1.1f
                                    }
                                }
                                PointerEventType.Release -> {
                                    if (touch.mode == 1) deck.endScratch()
                                    if (touch.mode == 2) deck.nudge = 0.0
                                    touch.mode = 0
                                    liveGrip.floatValue = -1f
                                }
                                PointerEventType.Enter -> if (isPen && !change.pressed) hover = true
                                PointerEventType.Exit -> {
                                    hover = false
                                    if (touch.mode == 2) deck.nudge = 0.0
                                }
                            }
                        }
                    }
                },
        ) {
            tick.longValue
            val r = min(size.width, size.height) / 2f
            val c = Offset(size.width / 2f, size.height / 2f)
            val ring = r * 0.085f
            val ringR = r - ring / 2f
            val t = deck.track
            // Outer ring with progress through the track.
            drawCircle(TS.Line, radius = ringR, center = c, style = Stroke(ring))
            if (t != null && t.frames > 0) {
                val sweep = (deck.position / t.frames * 360.0).toFloat().coerceIn(0f, 360f)
                drawArc(
                    color, -90f, sweep, false,
                    Offset(c.x - ringR, c.y - ringR), Size(ringR * 2, ringR * 2),
                    style = Stroke(ring, cap = StrokeCap.Round),
                )
            }
            if (hover) {
                drawCircle(
                    TS.Amber, radius = r - 1.dp.toPx(), center = c,
                    style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))),
                )
            }
            // Platter and grooves.
            val platter = r * 0.86f
            drawCircle(TS.Charcoal, radius = platter, center = c)
            for (k in 1..3) drawCircle(TS.Graphite, radius = platter * (1f - k * 0.13f), center = c, style = Stroke(1.5.dp.toPx()))
            // Position marker turns with the record.
            val rot = if (t != null) ((deck.position / framesPerRev) * 2 * PI - PI / 2) else -PI / 2
            val m1 = Offset(c.x + (cos(rot) * platter * 0.95).toFloat(), c.y + (sin(rot) * platter * 0.95).toFloat())
            val m2 = Offset(c.x + (cos(rot) * platter * 0.7).toFloat(), c.y + (sin(rot) * platter * 0.7).toFloat())
            drawLine(TS.Text, m1, m2, strokeWidth = r * 0.06f, cap = StrokeCap.Round)
            drawCircle(color, radius = r * 0.32f, center = c)
            drawCircle(TS.Black, radius = r * 0.045f, center = c)
            if (touch.mode == 1) drawCircle(TS.Amber, radius = platter, center = c, style = Stroke(2.dp.toPx()))
        }
        val grip = liveGrip.floatValue
        if (grip >= 0f) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .clip(RoundedCornerShape(50))
                    .background(TS.Black)
                    .border(1.dp, TS.Amber, RoundedCornerShape(50))
                    .padding(horizontal = 7.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TsIcon(TsIcons.Pen, TS.Amber, size = 10.dp)
                Spacer(Modifier.width(4.dp))
                Text("GRIP ${(grip * 100).roundToInt()}%", style = Type.label(9.sp, TS.Amber))
            }
        }
    }
}

/**
 * Scrolling waveform with the playhead in the middle, beat grid, hot cues and loop.
 * Drag it to move the song: while playing it scratches like the jog wheel, while stopped it
 * moves the playhead. Hover the S Pen over it to zoom in.
 */
@Composable
fun WaveformLane(
    deck: Deck,
    color: Color,
    dim: Color,
    sampleRate: Int,
    hoverZoom: Boolean,
    onPenSeen: () -> Unit,
    onTouch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tick = LocalFrameTick.current
    var zoomed by remember { mutableStateOf(false) }
    val zoomAllowed by rememberUpdatedState(hoverZoom)

    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(TS.Graphite)
            .semantics { contentDescription = "Waveform, deck ${deck.index + 1}" }
            .pointerInput(deck) {
                awaitPointerEventScope {
                    var lastX = 0f
                    var mode = 0 // 0 none, 1 seek (stopped), 2 scratch (playing)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: continue
                        val isPen = change.type == PointerType.Stylus
                        if (isPen) onPenSeen()
                        when (event.type) {
                            PointerEventType.Press -> {
                                lastX = change.position.x
                                mode = when {
                                    deck.track == null -> 0
                                    deck.playing -> { deck.startScratch(); 2 }
                                    else -> 1
                                }
                                if (mode != 0) {
                                    onTouch()
                                    change.consume()
                                }
                            }
                            PointerEventType.Move -> {
                                if (change.pressed && mode != 0) {
                                    val windowSec = if (zoomed) 3.0 else 8.0
                                    val framesPerPx = windowSec * sampleRate / size.width
                                    val delta = -(change.position.x - lastX) * framesPerPx
                                    if (mode == 2) deck.scratchBy(delta) else deck.seek(deck.position + delta)
                                    lastX = change.position.x
                                    change.consume()
                                } else if (!change.pressed) {
                                    zoomed = isPen && zoomAllowed
                                }
                            }
                            PointerEventType.Release -> {
                                if (mode == 2) deck.endScratch()
                                mode = 0
                            }
                            PointerEventType.Enter -> if (isPen && zoomAllowed) zoomed = true
                            PointerEventType.Exit -> {
                                zoomed = false
                                if (mode == 2) deck.endScratch()
                                mode = 0
                            }
                        }
                    }
                }
            },
    ) {
        tick.longValue
        val w = size.width
        val h = size.height
        val t = deck.track
        if (t != null) {
            val windowSec = if (zoomed) 3.0 else 8.0
            val framesPerPx = windowSec * sampleRate / w
            val pos = deck.position
            val barW = 2.dp.toPx()
            val step = barW + 1.dp.toPx()
            val wave = t.waveform
            val waveStep = TrackData.WAVE_STEP

            // Loop region.
            if (deck.loopActive) {
                val x0 = (w / 2 + (deck.loopIn - pos) / framesPerPx).toFloat()
                val x1 = (w / 2 + (deck.loopOut - pos) / framesPerPx).toFloat()
                if (x1 > 0 && x0 < w) drawRect(TS.Lime.copy(alpha = 0.14f), Offset(max(0f, x0), 0f), Size(min(w, x1) - max(0f, x0), h))
            }

            // Bars.
            var x = 0f
            val spanBins = max(1, ceil(step * framesPerPx / waveStep).toInt())
            while (x < w) {
                val frame = pos + (x + step / 2 - w / 2) * framesPerPx
                if (frame >= 0 && frame < t.frames) {
                    val bin = (frame / waveStep).toInt()
                    var peak = 0f
                    for (k in 0 until spanBins) {
                        val b = bin + k
                        if (b < wave.size && wave[b] > peak) peak = wave[b]
                    }
                    val bh = max(2f, peak * (h - 6.dp.toPx()))
                    drawRect(if (x + step < w / 2) dim else color, Offset(x, (h - bh) / 2f), Size(barW, bh))
                }
                x += step
            }

            // Beat grid: a small tick every beat, a taller one every bar.
            val a = deck.analysis
            if (a != null && a.bpm > 0) {
                val beat = 60.0 / a.bpm * sampleRate
                val first = a.firstBeatSec * sampleRate
                val startFrame = pos - w / 2 * framesPerPx
                val endFrame = pos + w / 2 * framesPerPx
                var n = floor((startFrame - first) / beat).toLong()
                while (true) {
                    val f = first + n * beat
                    if (f > endFrame) break
                    if (f >= 0) {
                        val bx = (w / 2 + (f - pos) / framesPerPx).toFloat()
                        val bar = Math.floorMod(n, 4L) == 0L
                        val len = if (bar) h * 0.22f else h * 0.1f
                        val c = if (bar) TS.Text.copy(alpha = 0.7f) else TS.Text.copy(alpha = 0.3f)
                        drawLine(c, Offset(bx, 0f), Offset(bx, len), 1.dp.toPx())
                        drawLine(c, Offset(bx, h - len), Offset(bx, h), 1.dp.toPx())
                    }
                    n++
                }
            }

            // Hot cue markers.
            for (i in 0 until Deck.HOT_CUES) {
                val cue = deck.hotCues[i]
                if (cue < 0) continue
                val cx = (w / 2 + (cue - pos) / framesPerPx).toFloat()
                if (cx < -10 || cx > w + 10) continue
                val p = Path().apply {
                    moveTo(cx - 5.dp.toPx(), 0f)
                    lineTo(cx + 5.dp.toPx(), 0f)
                    lineTo(cx, 7.dp.toPx())
                    close()
                }
                drawPath(p, TS.cueColors[i % TS.cueColors.size])
            }
            if (zoomed) drawRect(TS.Amber, Offset(0f, 0f), Size(w, h), style = Stroke(1.dp.toPx()))
        }
        drawRect(TS.Text, Offset(w / 2 - 1.dp.toPx(), 0f), Size(2.dp.toPx(), h))
    }
}


/**
 * The whole track at a glance: tap or drag anywhere to jump there.
 * Shows what has played, hot cues and the loop.
 */
@Composable
fun TrackOverview(deck: Deck, color: Color, dim: Color, onTouch: () -> Unit, modifier: Modifier = Modifier) {
    val tick = LocalFrameTick.current
    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(TS.Graphite)
            .semantics { contentDescription = "Track overview, deck ${deck.index + 1}. Tap to jump." }
            .pointerInput(deck) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: continue
                        val t = deck.track ?: continue
                        if (change.pressed && (event.type == PointerEventType.Press || event.type == PointerEventType.Move)) {
                            if (event.type == PointerEventType.Press) onTouch()
                            val f = (change.position.x / size.width).coerceIn(0f, 1f)
                            deck.seek(f.toDouble() * t.frames)
                            change.consume()
                        }
                    }
                }
            },
    ) {
        tick.longValue
        val w = size.width
        val h = size.height
        val t = deck.track ?: return@Canvas
        val ov = t.overview
        val played = (deck.position / t.frames).toFloat()
        if (deck.loopActive) {
            val x0 = (deck.loopIn / t.frames).toFloat() * w
            val x1 = (deck.loopOut / t.frames).toFloat() * w
            drawRect(TS.Lime.copy(alpha = 0.25f), Offset(x0, 0f), Size(max(2f, x1 - x0), h))
        }
        val bw = w / ov.size
        for (i in ov.indices) {
            val x = i * bw
            val bh = max(1f, ov[i] * (h - 2.dp.toPx()))
            drawRect(if (i.toFloat() / ov.size < played) dim else color, Offset(x, (h - bh) / 2f), Size(max(1f, bw - 0.5f), bh))
        }
        for (i in 0 until Deck.HOT_CUES) {
            val c = deck.hotCues[i]
            if (c < 0) continue
            val x = (c / t.frames).toFloat() * w
            drawRect(TS.cueColors[i % TS.cueColors.size], Offset(x - 1f, 0f), Size(2.dp.toPx(), h * 0.35f))
        }
        val px = played * w
        drawRect(TS.Text, Offset(px - 1f, 0f), Size(2.dp.toPx(), h))
    }
}
