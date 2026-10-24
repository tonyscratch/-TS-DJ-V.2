package com.tonyscratch.tsdj.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tonyscratch.tsdj.ui.theme.TS
import com.tonyscratch.tsdj.ui.theme.Type
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// ------------------------------------------------------------------------------------------
// Frame clock: lets controls redraw every frame to follow the audio engine.
// ------------------------------------------------------------------------------------------

val LocalFrameTick = compositionLocalOf<MutableLongState> { mutableLongStateOf(0L) }

/**
 * Reads a value from the audio engine once per frame, but only recomposes when it changes.
 * Use it for things like the play icon, the time left, or the REC timer.
 */
@Composable
fun <T> engineValue(read: () -> T): State<T> {
    val tick = LocalFrameTick.current
    val latest = rememberUpdatedState(read)
    return remember(tick) { derivedStateOf { tick.longValue; latest.value() } }
}

// ------------------------------------------------------------------------------------------
// Buttons
// ------------------------------------------------------------------------------------------

/** A rounded TS Dj button. [filled] uses [color] as the background; otherwise it's outlined. */
@Composable
fun TsButton(
    modifier: Modifier = Modifier,
    color: Color = TS.Orange,
    filled: Boolean = false,
    neutral: Boolean = false,
    dashed: Boolean = false,
    corner: Dp = 10.dp,
    description: String? = null,
    onLongPress: (() -> Unit)? = null,
    onClick: () -> Unit,
    content: @Composable RowScope.(contentColor: Color) -> Unit,
) {
    val shape = RoundedCornerShape(corner)
    val bg = when {
        filled -> color
        neutral -> TS.Charcoal
        else -> Color.Transparent
    }
    val border = when {
        filled -> Color.Transparent
        neutral -> TS.Line2
        else -> color
    }
    val fg = when {
        filled -> TS.Black
        neutral -> TS.Text
        else -> color
    }
    val click by rememberUpdatedState(onClick)
    val longPress by rememberUpdatedState(onLongPress)
    Row(
        modifier = modifier
            .clip(shape)
            .background(bg, shape)
            .then(
                if (dashed) Modifier.dashedBorder(border, corner)
                else Modifier.border(if (filled) 0.dp else 1.5.dp, border, shape)
            )
            .semantics {
                role = Role.Button
                if (description != null) contentDescription = description
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { click() },
                    onLongPress = { longPress?.invoke() ?: click() },
                )
            }
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content(fg)
    }
}

private fun Modifier.dashedBorder(color: Color, corner: Dp): Modifier = this.drawBehind {
    val stroke = Stroke(
        width = 1.5.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
    )
    drawRoundRect(color, cornerRadius = CornerRadius(corner.toPx()), style = stroke)
}

@Composable
fun TsIcon(icon: ImageVector, color: Color, size: Dp = 18.dp, description: String? = null) {
    Icon(icon, contentDescription = description, tint = color, modifier = Modifier.size(size))
}

@Composable
fun Label(text: String, color: Color = TS.Muted, size: Int = 9) {
    Text(text, style = Type.label(size.sp, color), maxLines = 1, overflow = TextOverflow.Clip)
}

/** A small rounded tag, e.g. "LOCAL" or "ON OUTPUT". */
@Composable
fun Tag(text: String, color: Color, filled: Boolean = false) {
    val shape = RoundedCornerShape(5.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (filled) color else Color.Transparent)
            .border(1.dp, color, shape)
            .padding(horizontal = 5.dp, vertical = 1.dp),
    ) {
        Text(text, style = Type.label(8.sp, if (filled) TS.Black else color), maxLines = 1)
    }
}

// ------------------------------------------------------------------------------------------
// Knob
// ------------------------------------------------------------------------------------------

/**
 * A rotary knob. Drag up/down to turn; double-tap to reset to the middle.
 * [read] and [write] connect it to a value in the audio engine (0..1).
 */
@Composable
fun Knob(
    read: () -> Float,
    write: (Float) -> Unit,
    color: Color,
    description: String,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    bipolar: Boolean = true,
) {
    val tick = LocalFrameTick.current
    val acc = remember { floatArrayOf(0f) }
    val readNow by rememberUpdatedState(read)
    val writeNow by rememberUpdatedState(write)
    Canvas(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = description }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { writeNow(if (bipolar) 0.5f else readNow()) })
            }
            .pointerInput(Unit) {
                detectDragGestures(onDragStart = { acc[0] = readNow() }) { change, drag ->
                    change.consume()
                    acc[0] = (acc[0] - drag.y / 160f + drag.x / 400f).coerceIn(0f, 1f)
                    // A gentle detent at the centre makes "flat" easy to find.
                    val snapped = if (bipolar && kotlin.math.abs(acc[0] - 0.5f) < 0.025f) 0.5f else acc[0]
                    writeNow(snapped)
                }
            },
    ) {
        tick.longValue
        val r = min(this.size.width, this.size.height) / 2f
        val c = Offset(this.size.width / 2f, this.size.height / 2f)
        val stroke = r * 0.18f
        val arcR = r - stroke / 2f
        drawCircle(TS.KnobBody, radius = r * 0.62f, center = c)
        val topLeft = Offset(c.x - arcR, c.y - arcR)
        val arcSize = Size(arcR * 2, arcR * 2)
        // Track: 270° sweep starting at 135° (bottom-left).
        drawArc(TS.Line, 135f, 270f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        val v = readNow().coerceIn(0f, 1f)
        if (bipolar) {
            val sweep = (v - 0.5f) * 270f
            if (kotlin.math.abs(sweep) > 0.5f) {
                drawArc(color, 270f, sweep, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        } else {
            drawArc(color, 135f, v * 270f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        val ang = (135f + v * 270f) * PI.toFloat() / 180f
        val tip = Offset(c.x + cos(ang) * r * 0.55f, c.y + sin(ang) * r * 0.55f)
        drawLine(TS.Text, c, tip, strokeWidth = r * 0.12f, cap = StrokeCap.Round)
    }
}

// ------------------------------------------------------------------------------------------
// Faders and meters
// ------------------------------------------------------------------------------------------

/** A vertical fader. Top = 1. [read]/[write] connect it to the engine. */
@Composable
fun VerticalFader(
    read: () -> Float,
    write: (Float) -> Unit,
    color: Color,
    description: String,
    modifier: Modifier = Modifier,
    capColor: Color = color,
    centreLine: Boolean = false,
    resetTo: Float? = null,
) {
    val tick = LocalFrameTick.current
    Canvas(
        modifier = modifier
            .semantics { contentDescription = description }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { if (resetTo != null) write(resetTo) })
            }
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    val travel = size.height * 0.85f
                    write((read() - drag.y / travel).coerceIn(0f, 1f))
                }
            },
    ) {
        tick.longValue
        val w = this.size.width
        val h = this.size.height
        val capH = min(14.dp.toPx(), h * 0.15f)
        drawRoundRect(TS.Line, Offset(w / 2f - 2.dp.toPx(), 0f), Size(4.dp.toPx(), h), CornerRadius(2.dp.toPx()))
        if (centreLine) drawLine(TS.Muted, Offset(w * 0.15f, h / 2f), Offset(w * 0.85f, h / 2f), 1.dp.toPx())
        val v = read().coerceIn(0f, 1f)
        val y = (1f - v) * (h - capH)
        drawRoundRect(capColor, Offset(0f, y), Size(w, capH), CornerRadius(4.dp.toPx()))
        if (capColor != color) {
            drawRoundRect(color, Offset(0f, y), Size(w, capH), CornerRadius(4.dp.toPx()), style = Stroke(2.dp.toPx()))
        }
    }
}

/** The crossfader: drag left for Deck A, right for Deck B; double-tap to centre. */
@Composable
fun Crossfader(read: () -> Float, write: (Float) -> Unit, modifier: Modifier = Modifier, fillA: Boolean = false) {
    val tick = LocalFrameTick.current
    Canvas(
        modifier = modifier
            .semantics { contentDescription = "Crossfader" }
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { write(0.5f) }) }
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    write((read() + drag.x / (size.width * 0.9f)).coerceIn(0f, 1f))
                }
            },
    ) {
        tick.longValue
        val w = this.size.width
        val h = this.size.height
        val capW = 20.dp.toPx()
        val track = 6.dp.toPx()
        drawRoundRect(TS.Line, Offset(0f, h / 2 - track / 2), Size(w, track), CornerRadius(track / 2))
        val v = read().coerceIn(0f, 1f)
        val x = v * (w - capW)
        if (fillA) {
            drawRoundRect(TS.Orange, Offset(0f, h / 2 - track / 2), Size(x + capW / 2, track), CornerRadius(track / 2))
        }
        drawLine(TS.Line2, Offset(w / 2, h * 0.12f), Offset(w / 2, h * 0.88f), 1.dp.toPx())
        drawRoundRect(TS.Black, Offset(x - 2.dp.toPx(), h * 0.08f - 2.dp.toPx()), Size(capW + 4.dp.toPx(), h * 0.84f + 4.dp.toPx()), CornerRadius(6.dp.toPx()))
        drawRoundRect(TS.Text, Offset(x, h * 0.08f), Size(capW, h * 0.84f), CornerRadius(5.dp.toPx()))
    }
}

/** A 12-segment level meter (lime → amber → pink). */
@Composable
fun LevelMeter(read: () -> Float, modifier: Modifier = Modifier) {
    val tick = LocalFrameTick.current
    Canvas(modifier = modifier.width(5.dp)) {
        tick.longValue
        val level = read()
        val segs = 12
        val gap = 2.dp.toPx()
        val segH = (size.height - gap * (segs - 1)) / segs
        // Map -36 dB … 0 dB onto the 12 segments.
        val db = if (level <= 1e-5f) -100f else 20f * kotlin.math.log10(level)
        val lit = (((db + 36f) / 36f) * segs).toInt().coerceIn(0, segs)
        for (i in 0 until segs) {
            val y = size.height - (i + 1) * segH - i * gap
            val c = when {
                i >= lit -> TS.Line
                i >= 10 -> TS.Pink
                i >= 7 -> TS.Amber
                else -> TS.Lime
            }
            drawRoundRect(c, Offset(0f, y), Size(size.width, segH), CornerRadius(1.dp.toPx()))
        }
    }
}

/** Segmented control, e.g. Soft / Linear / Firm. */
@Composable
fun Segmented(options: List<String>, selected: Int, colors: List<Color>, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(TS.Black)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (on) colors[i % colors.size] else Color.Transparent)
                    .semantics { role = Role.Tab }
                    .pointerInput(i) { detectTapGestures { onSelect(i) } },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = Type.ui(11.sp, 800, if (on) TS.Black else TS.Text2), maxLines = 1)
            }
        }
    }
}

/** A thin progress bar. */
@Composable
fun ProgressBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(TS.Line)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).background(color))
    }
}

// ------------------------------------------------------------------------------------------
// Pads and small knobs
// ------------------------------------------------------------------------------------------

/**
 * A performance pad. Fires on finger-down (instant, like a controller), with an optional
 * release action for hold-type pads (loop roll, censor, hold effects).
 */
@Composable
fun Pad(
    modifier: Modifier,
    color: Color,
    lit: Boolean,
    description: String,
    dashed: Boolean = false,
    selected: Boolean = false,
    dim: Boolean = false,
    onPress: () -> Unit,
    onRelease: (() -> Unit)? = null,
    content: @Composable (fg: Color) -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    val press by rememberUpdatedState(onPress)
    val release by rememberUpdatedState(onRelease)
    val c = if (dim) TS.Line2 else color
    val fg = if (lit) TS.Black else c
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (lit) c else TS.Charcoal.copy(alpha = 0.6f))
            .then(if (dashed) Modifier.dashedBorder(c, 8.dp) else Modifier.border(if (lit) 0.dp else 1.5.dp, c, shape))
            .semantics {
                role = Role.Button
                contentDescription = description
            }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    press()
                    tryAwaitRelease()
                    release?.invoke()
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        content(fg)
        if (selected) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 3.dp)
                    .size(width = 18.dp, height = 3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (lit) TS.Black else TS.Amber),
            )
        }
    }
}

/** A small knob with its label and value beside it, for the pad section footer. */
@Composable
fun LabeledKnob(
    label: String,
    valueText: String,
    read: () -> Float,
    write: (Float) -> Unit,
    color: Color,
    modifier: Modifier = Modifier,
    bipolar: Boolean = false,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Knob(read = read, write = write, color = color, description = label, size = 26.dp, bipolar = bipolar)
        Spacer(Modifier.width(3.dp))
        Column {
            Text(label, style = Type.label(7.sp), maxLines = 1)
            Text(valueText, style = Type.mono(9.sp), maxLines = 1)
        }
    }
}

/** A compact on/off button for the deck's transport row (KEYLOCK, SLIP, Q…). */
@Composable
fun MiniToggle(text: String, on: Boolean, color: Color, description: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    TsButton(modifier = modifier, color = color, filled = on, neutral = !on, corner = 8.dp, description = description, onClick = onClick) { fg ->
        Text(text, style = Type.label(9.sp, fg), maxLines = 1)
    }
}
