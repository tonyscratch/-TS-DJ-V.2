package com.tonyscratch.tsdj.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tonyscratch.tsdj.DjViewModel
import com.tonyscratch.tsdj.Screen
import com.tonyscratch.tsdj.data.PressureCurve
import com.tonyscratch.tsdj.ui.components.Label
import com.tonyscratch.tsdj.ui.components.Segmented
import com.tonyscratch.tsdj.ui.theme.TS
import com.tonyscratch.tsdj.ui.theme.Type

/** Settings for how the S Pen controls the decks. */
@Composable
fun SPenScreen(vm: DjViewModel) {
    val pen = vm.pen
    Column(Modifier.fillMaxSize().background(TS.Black)) {
        Row(
            Modifier.fillMaxWidth().height(50.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BackButton { vm.screen = Screen.Mixer }
            Text("S Pen ", style = Type.display(16.sp))
            Text("controls", style = Type.display(16.sp, TS.Amber))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(TS.Charcoal))

        Row(Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(
                Modifier.width(300.dp).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(if (vm.penSeen) TS.Lime else TS.Muted))
                        Spacer(Modifier.width(6.dp))
                        Label(if (vm.penSeen) "PEN DETECTED" else "TOUCH A JOG WHEEL WITH THE PEN", if (vm.penSeen) TS.Lime else TS.Muted, 10)
                    }
                    PenDrawing(Modifier.fillMaxWidth().height(44.dp))
                }
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Scratch grip (pressure)", style = Type.ui(12.sp, 800), modifier = Modifier.weight(1f))
                        Text("${(pen.gripBase * 100).toInt()}%", style = Type.mono(13.sp, color = TS.Amber))
                    }
                    Slider(
                        value = pen.gripBase,
                        onValueChange = { vm.updatePen(pen.copy(gripBase = it)) },
                        valueRange = 0.2f..1f,
                        colors = SliderDefaults.colors(thumbColor = TS.Text, activeTrackColor = TS.Amber, inactiveTrackColor = TS.Line),
                        modifier = Modifier.semantics { contentDescription = "Scratch grip" },
                    )
                    Text("How hard to press for a firm grip", style = Type.ui(10.sp, 600, TS.Text2))
                    Segmented(
                        options = listOf("Soft", "Linear", "Firm"),
                        selected = pen.curve.ordinal,
                        colors = listOf(TS.Amber),
                        onSelect = { vm.updatePen(pen.copy(curve = PressureCurve.entries[it])) },
                    )
                }
                Card {
                    Text("Pen button controls", style = Type.ui(12.sp, 800))
                    Segmented(
                        options = listOf("Deck A", "Last touched", "Deck B"),
                        selected = when (pen.focusDeck) { 0 -> 0; 1 -> 2; else -> 1 },
                        colors = listOf(TS.Orange, TS.Amber, TS.Cyan),
                        onSelect = { vm.updatePen(pen.copy(focusDeck = when (it) { 0 -> 0; 2 -> 1; else -> -1 })) },
                    )
                }
                Card {
                    ToggleRow("Zoom waveform when the pen hovers", pen.hoverZoom) { vm.updatePen(pen.copy(hoverZoom = it)) }
                    ToggleRow("Hold pen button on a jog wheel to bend tempo", pen.penButtonBend) { vm.updatePen(pen.copy(penButtonBend = it)) }
                }
            }

            Column(
                Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Card {
                    Label("ON THE SCREEN")
                    GestureRow("DRG", TS.Orange, "Drag on a jog wheel", "Scratch")
                    GestureRow("PRS", TS.Amber, "Press harder while dragging", "Tighter grip")
                    GestureRow("RIM", TS.Cyan, "Drag the outer rim", "Nudge tempo")
                    GestureRow("BTN", TS.Cyan, "Hold pen button + drag a jog", "Nudge tempo")
                    GestureRow("HOV", TS.Lime, "Hover over a waveform", "Zoom in")
                    GestureRow("DRG", TS.Lime, "Drag a stopped waveform", "Move through the track")
                }
                Card {
                    Label("PEN BUTTON FROM A DISTANCE (AIR ACTIONS)")
                    GestureRow("1×", TS.Pink, "Play / pause key", "Play or pause the deck above")
                    GestureRow("→", TS.Pink, "Next-track key", "Crossfade towards B")
                    GestureRow("←", TS.Pink, "Previous-track key", "Crossfade towards A")
                    Text(
                        "TS Dj listens for media keys. On your phone, open Settings › Advanced features › " +
                            "S Pen › Air actions, and if TS Dj (or \"Media\") is listed, set the pen button to " +
                            "play/pause and the swipes to next/previous. Full Air actions support needs Samsung's " +
                            "S Pen Remote SDK, which is planned for a later version.",
                        style = Type.ui(11.sp, 600, TS.Text2),
                    )
                }
            }
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(TS.Graphite)
            .border(1.dp, TS.Line, RoundedCornerShape(14.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun ToggleRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = Type.ui(12.sp, 700), modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = TS.Amber, checkedThumbColor = TS.Black,
                uncheckedTrackColor = TS.Charcoal, uncheckedThumbColor = TS.Muted, uncheckedBorderColor = TS.Line2,
            ),
        )
    }
}

@Composable
private fun GestureRow(glyph: String, tint: Color, gesture: String, action: String) {
    Row(
        Modifier.fillMaxWidth().height(38.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(tint), contentAlignment = Alignment.Center) {
            Text(glyph, style = Type.mono(10.sp, color = TS.Black))
        }
        Text(gesture, style = Type.ui(12.sp, 700), modifier = Modifier.weight(1f))
        Box(
            Modifier.clip(RoundedCornerShape(50)).background(TS.Charcoal).border(1.dp, TS.Line2, RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) { Text(action, style = Type.ui(11.sp, 800)) }
    }
}

@Composable
private fun PenDrawing(modifier: Modifier) {
    Canvas(modifier.semantics { contentDescription = "S Pen with its side button highlighted" }) {
        val h = size.height
        val w = size.width
        val body = Path().apply {
            moveTo(w * 0.05f, h / 2)
            lineTo(w * 0.12f, h * 0.32f)
            lineTo(w * 0.9f, h * 0.32f)
            @Suppress("DEPRECATION")
            quadraticBezierTo(w * 0.94f, h / 2, w * 0.9f, h * 0.68f)
            lineTo(w * 0.12f, h * 0.68f)
            close()
        }
        drawPath(body, TS.Charcoal)
        drawPath(body, TS.Line2, style = Stroke(1.5.dp.toPx()))
        val tip = Path().apply {
            moveTo(0f, h / 2)
            lineTo(w * 0.05f, h * 0.4f)
            lineTo(w * 0.05f, h * 0.6f)
            close()
        }
        drawPath(tip, TS.Orange)
        drawRoundRect(TS.Amber, Offset(w * 0.28f, h * 0.4f), androidx.compose.ui.geometry.Size(w * 0.09f, h * 0.2f), androidx.compose.ui.geometry.CornerRadius(h * 0.1f))
        drawCircle(
            TS.Amber, radius = h * 0.32f, center = Offset(w * 0.325f, h / 2),
            style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))),
        )
    }
}
