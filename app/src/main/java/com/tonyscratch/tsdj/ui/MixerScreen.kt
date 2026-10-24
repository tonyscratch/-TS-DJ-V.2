package com.tonyscratch.tsdj.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tonyscratch.tsdj.DjViewModel
import com.tonyscratch.tsdj.Screen
import com.tonyscratch.tsdj.audio.Camelot
import com.tonyscratch.tsdj.audio.Keys
import com.tonyscratch.tsdj.audio.Mixer
import com.tonyscratch.tsdj.data.Actions
import com.tonyscratch.tsdj.data.Shortcut
import com.tonyscratch.tsdj.ui.components.Crossfader
import com.tonyscratch.tsdj.ui.components.JogWheel
import com.tonyscratch.tsdj.ui.components.Knob
import com.tonyscratch.tsdj.ui.components.Label
import com.tonyscratch.tsdj.ui.components.LevelMeter
import com.tonyscratch.tsdj.ui.components.MiniToggle
import com.tonyscratch.tsdj.ui.components.ProgressBar
import com.tonyscratch.tsdj.ui.components.Tag
import com.tonyscratch.tsdj.ui.components.TrackOverview
import com.tonyscratch.tsdj.ui.components.TsButton
import com.tonyscratch.tsdj.ui.components.TsIcon
import com.tonyscratch.tsdj.ui.components.TsIcons
import com.tonyscratch.tsdj.ui.components.VerticalFader
import com.tonyscratch.tsdj.ui.components.WaveformLane
import com.tonyscratch.tsdj.ui.components.engineValue
import com.tonyscratch.tsdj.ui.theme.TS
import com.tonyscratch.tsdj.ui.theme.Type
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

@Composable
fun MixerScreen(vm: DjViewModel) {
    BoxWithConstraints(Modifier.fillMaxSize().background(TS.Black)) {
        val laneH = (maxHeight * 0.075f).coerceIn(24.dp, 36.dp)
        Column(Modifier.fillMaxSize()) {
            TopBar(vm)
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for (i in 0..1) {
                    Row(Modifier.height(laneH), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            Modifier.width(20.dp).fillMaxHeight().clip(RoundedCornerShape(6.dp)).background(TS.deck(i)),
                            contentAlignment = Alignment.Center,
                        ) { Text(vm.deckName(i), style = Type.display(10.sp, TS.Black)) }
                        WaveformLane(
                            deck = vm.deck(i),
                            color = TS.deck(i),
                            dim = TS.deckDim(i),
                            sampleRate = vm.engine.sampleRate,
                            hoverZoom = vm.pen.hoverZoom,
                            onPenSeen = { vm.penSeen = true },
                            onTouch = { vm.touch(i) },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                        TrackOverview(
                            deck = vm.deck(i),
                            color = TS.deck(i),
                            dim = TS.deckDim(i),
                            onTouch = { vm.touch(i) },
                            modifier = Modifier.width(150.dp).fillMaxHeight(),
                        )
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().weight(1f).padding(start = 12.dp, end = 12.dp, top = 3.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DeckPanel(vm, 0, Modifier.weight(1f).fillMaxHeight())
                MixerPanel(vm, Modifier.width(150.dp).fillMaxHeight())
                DeckPanel(vm, 1, Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

// ------------------------------------------------------------------------------------------
// Top bar: name, master tempo, REC, Automix, your shortcuts, navigation
// ------------------------------------------------------------------------------------------

@Composable
private fun TopBar(vm: DjViewModel) {
    val masterBpm by engineValue {
        val m = vm.mixer
        val a = m.decks[0]
        val b = m.decks[1]
        val gA = Mixer.crossGainA(m.crossfader) * a.fader * (if (a.playing) 1f else 0.01f)
        val gB = Mixer.crossGainB(m.crossfader) * b.fader * (if (b.playing) 1f else 0.01f)
        val lead = if (gA >= gB) a else b
        (lead.effectiveBpm() ?: a.effectiveBpm() ?: b.effectiveBpm())?.let { String.format(Locale.US, "%.1f", it) } ?: "---.-"
    }
    val elapsed by engineValue { vm.recorder.elapsedSec }

    Row(
        Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Logo()
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(TS.Graphite).border(1.dp, TS.Line, RoundedCornerShape(50))
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(masterBpm, style = Type.mono(13.sp))
            Spacer(Modifier.width(3.dp))
            Label("BPM")
        }
        TsButton(
            modifier = Modifier.height(30.dp),
            color = TS.Pink,
            filled = vm.recording,
            corner = 15.dp,
            description = if (vm.recording) "Stop recording" else "Start recording",
            onClick = { vm.toggleRecording() },
        ) { fg ->
            Box(Modifier.size(8.dp).clip(CircleShape).background(fg))
            Spacer(Modifier.width(5.dp))
            Text(if (vm.recording) hms(elapsed) else "REC", style = Type.mono(11.sp, color = fg))
        }
        TsButton(
            modifier = Modifier.height(30.dp),
            color = TS.Lime,
            filled = vm.automix,
            corner = 15.dp,
            description = "Automix",
            onClick = { if (vm.queue.isEmpty() && !vm.automix) vm.screen = Screen.Library; vm.toggleAutomix() },
        ) { fg -> Text("AUTO", style = Type.label(10.sp, fg)) }
        ShortcutBar(vm, Modifier.weight(1f).fillMaxHeight())
        NavIcon(TsIcons.Library, "Library") { vm.screen = Screen.Library }
        NavIcon(TsIcons.Video, "YouTube Mix") { vm.screen = Screen.YouTube }
        NavIcon(TsIcons.Disc, "Recordings") { vm.refreshRecordings(); vm.screen = Screen.Recordings }
        NavIcon(TsIcons.Pen, "S Pen controls", TS.Amber) { vm.screen = Screen.SPen }
        NavIcon(TsIcons.Sliders, "Settings") { vm.screen = Screen.Settings }
    }
}

@Composable
fun Logo(size: Dp = 22.dp) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics { contentDescription = "Tony Scratch Dj" }) {
        Canvas(Modifier.size(size)) {
            val r = this.size.minDimension / 2
            drawCircle(TS.Orange, r)
            drawCircle(TS.Black, r * 0.64f, style = Stroke(1.2.dp.toPx()))
            drawCircle(TS.Black, r * 0.36f, style = Stroke(1.2.dp.toPx()))
            drawCircle(TS.Black, r * 0.15f)
        }
        Spacer(Modifier.width(7.dp))
        Text("Tony Scratch ", style = Type.display(13.sp), maxLines = 1)
        Text("Dj", style = Type.display(13.sp, TS.Orange), maxLines = 1)
    }
}

@Composable
fun NavIcon(icon: ImageVector, description: String, tint: Color = TS.Text, onClick: () -> Unit) {
    Box(
        Modifier.size(38.dp, 32.dp).clip(RoundedCornerShape(10.dp)).background(TS.Graphite)
            .semantics { contentDescription = description; role = Role.Button }
            .pointerInput(Unit) { detectTapGestures { onClick() } },
        contentAlignment = Alignment.Center,
    ) { TsIcon(icon, tint, 18.dp) }
}

/**
 * Your shortcut buttons. Tap + to add one (pick a category, a deck and an action).
 * Tap the pencil to move or remove them.
 */
@Composable
private fun ShortcutBar(vm: DjViewModel, modifier: Modifier) {
    var editing by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    @Suppress("UNUSED_VARIABLE") val v = vm.samplerVersion
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            itemsIndexed(vm.shortcuts) { i, s ->
                ShortcutChip(vm, i, s, editing)
            }
            item {
                Box(
                    Modifier.size(32.dp, 28.dp).clip(RoundedCornerShape(8.dp)).border(1.5.dp, TS.Orange, RoundedCornerShape(8.dp))
                        .semantics { role = Role.Button; contentDescription = "Add a shortcut" }
                        .pointerInput(Unit) { detectTapGestures { adding = true } },
                    contentAlignment = Alignment.Center,
                ) { TsIcon(TsIcons.Plus, TS.Orange, 15.dp) }
            }
        }
        if (vm.shortcuts.isNotEmpty()) {
            Box(
                Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(if (editing) TS.Amber else TS.Graphite)
                    .semantics { role = Role.Button; contentDescription = if (editing) "Done editing shortcuts" else "Edit shortcuts" }
                    .pointerInput(Unit) { detectTapGestures { editing = !editing } },
                contentAlignment = Alignment.Center,
            ) { TsIcon(if (editing) TsIcons.Check else TsIcons.Pen, if (editing) TS.Black else TS.Text2, 13.dp) }
        }
    }
    if (adding) {
        ActionPicker(
            title = "New shortcut",
            banks = vm.banks,
            fixedDeck = null,
            onPick = { code, deck -> vm.addShortcut(Shortcut(code, deck)); adding = false },
            onDismiss = { adding = false },
        )
    }
}

@Composable
private fun ShortcutChip(vm: DjViewModel, index: Int, s: Shortcut, editing: Boolean) {
    val label = Actions.label(s, vm.banks)
    val color = when (s.deck) { 0 -> TS.Orange; 1 -> TS.Cyan; 2 -> TS.Amber; else -> TS.Violet }
    val shortcut by rememberUpdatedState(s)
    val edit by rememberUpdatedState(editing)
    var pressed by remember { mutableStateOf(false) }
    Row(
        Modifier.height(28.dp).clip(RoundedCornerShape(8.dp))
            .background(if (pressed) color else TS.Graphite)
            .border(1.5.dp, color, RoundedCornerShape(8.dp))
            .semantics { role = Role.Button; contentDescription = "Shortcut $label" }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    if (!edit) {
                        pressed = true
                        vm.actionDown(shortcut.code, shortcut.deck)
                        tryAwaitRelease()
                        vm.actionUp(shortcut.code, shortcut.deck)
                        pressed = false
                    }
                })
            }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (editing) {
            Text("◀", style = Type.ui(10.sp, 800, TS.Text2), modifier = Modifier.padding(end = 6.dp).pointerInput(index) { detectTapGestures { vm.moveShortcut(index, -1) } })
        }
        Text(label, style = Type.label(9.sp, if (pressed) TS.Black else color), maxLines = 1)
        if (editing) {
            Text("▶", style = Type.ui(10.sp, 800, TS.Text2), modifier = Modifier.padding(start = 6.dp).pointerInput(index) { detectTapGestures { vm.moveShortcut(index, 1) } })
            Text("✕", style = Type.ui(11.sp, 800, TS.Pink), modifier = Modifier.padding(start = 8.dp).pointerInput(index) { detectTapGestures { vm.removeShortcut(index) } })
        }
    }
}

// ------------------------------------------------------------------------------------------
// Decks
// ------------------------------------------------------------------------------------------

@Composable
private fun DeckPanel(vm: DjViewModel, index: Int, modifier: Modifier) {
    val deck = vm.deck(index)
    val color = TS.deck(index)
    @Suppress("UNUSED_VARIABLE") val version = vm.deckVersion[index]
    val load = vm.deckLoad[index]
    val shape = RoundedCornerShape(14.dp)

    Box(modifier.clip(shape).background(TS.Graphite).border(1.dp, TS.Line, shape)) {
        Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            DeckHeader(vm, index)
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                val bodyH = maxHeight
                val pitchW = 26.dp
                val padW = (maxWidth * 0.52f).coerceIn(160.dp, 230.dp)
                val jog = min((maxWidth - padW - pitchW - 12.dp).value, bodyH.value).coerceAtLeast(70f).dp
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    val jogWheel: @Composable () -> Unit = {
                        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                            JogWheel(
                                deck = deck, color = color, sampleRate = vm.engine.sampleRate, pen = vm.pen,
                                liveGrip = vm.liveGrip[index], onTouch = { vm.touch(index) }, onPenSeen = { vm.penSeen = true },
                                modifier = Modifier.size(jog),
                            )
                        }
                    }
                    if (index == 0) {
                        PitchColumn(vm, index, Modifier.width(pitchW).fillMaxHeight())
                        jogWheel()
                        PadSection(vm, index, Modifier.width(padW).fillMaxHeight())
                    } else {
                        PadSection(vm, index, Modifier.width(padW).fillMaxHeight())
                        jogWheel()
                        PitchColumn(vm, index, Modifier.width(pitchW).fillMaxHeight())
                    }
                }
            }
            TransportRow(vm, index)
        }

        if (load != null) {
            Column(
                Modifier.fillMaxSize().background(TS.Black.copy(alpha = 0.86f)).padding(20.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(load.title, style = Type.ui(13.sp, 800), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(10.dp))
                if (load.error == null) {
                    Text(if (load.progress >= 0.93f) "Analysing BPM and key…" else "Loading…", style = Type.ui(11.sp, 600, TS.Text2))
                    Spacer(Modifier.height(8.dp))
                    ProgressBar(load.progress, color)
                } else {
                    Text(load.error, style = Type.ui(11.sp, 600, TS.Pink), textAlign = TextAlign.Center)
                    Spacer(Modifier.height(10.dp))
                    TsButton(Modifier.height(36.dp), color = color, onClick = { vm.deckLoad[index] = null }) { fg ->
                        Text("OK", style = Type.ui(12.sp, 800, fg))
                    }
                }
            }
        }
    }
}

@Composable
private fun DeckHeader(vm: DjViewModel, index: Int) {
    val deck = vm.deck(index)
    val color = TS.deck(index)
    @Suppress("UNUSED_VARIABLE") val version = vm.deckVersion[index]
    val info = deck.info
    val musical = vm.settings.musicalKeys
    val remaining by engineValue { deck.remainingSec().toLong() }
    val bpm by engineValue { deck.effectiveBpm()?.let { String.format(Locale.US, "%.1f", it) } ?: "--.-" }
    val key by engineValue { deck.currentKey() }
    val otherKey by engineValue { vm.other(index).currentKey() }
    val shift by engineValue { deck.keyShift }
    var menu by remember { mutableStateOf(false) }

    Row(Modifier.fillMaxWidth().height(34.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            if (info == null) {
                TsButton(Modifier.height(30.dp), color = color, onClick = { vm.screen = Screen.Library }) { fg ->
                    TsIcon(TsIcons.Library, fg, 15.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("Load a track", style = Type.ui(12.sp, 800, fg))
                }
            } else {
                Text(info.title, style = Type.ui(13.sp, 800), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Tag(info.source, color)
                    Spacer(Modifier.width(6.dp))
                    Text(info.artist, style = Type.ui(11.sp, 600, TS.Text2), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        // Key, coloured by how well it mixes with the other deck.
        val compat = when {
            key == null || otherKey == null -> TS.Text2
            Camelot.compatible(key, otherKey) -> TS.Lime
            Keys.close(key, otherKey) -> TS.Amber
            else -> TS.Text2
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                Keys.display(key, musical),
                style = Type.mono(12.sp, color = compat),
                modifier = Modifier.border(1.dp, compat, RoundedCornerShape(5.dp)).padding(horizontal = 5.dp, vertical = 1.dp),
            )
            if (shift != 0) Text(if (shift > 0) "KEY +$shift" else "KEY $shift", style = Type.label(7.sp, TS.Violet))
        }
        Box {
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier
                    .semantics { contentDescription = "Tempo options"; role = Role.Button }
                    .pointerInput(Unit) { detectTapGestures { menu = true } },
            ) {
                Text(bpm, style = Type.mono(18.sp, color = color))
                Text("-${ms(remaining.toDouble())}", style = Type.mono(10.sp, 500, TS.Text2))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Double BPM (×2)", style = Type.ui(13.sp)) }, onClick = { vm.fixBpm(index, 2.0); menu = false })
                DropdownMenuItem(text = { Text("Halve BPM (÷2)", style = Type.ui(13.sp)) }, onClick = { vm.fixBpm(index, 0.5); menu = false })
                DropdownMenuItem(text = { Text("Set a beat here (fix grid)", style = Type.ui(13.sp)) }, onClick = { vm.setGridHere(index); menu = false })
                DropdownMenuItem(text = { Text("Reset tempo to 0%", style = Type.ui(13.sp)) }, onClick = { vm.resetPitch(index); menu = false })
                DropdownMenuItem(
                    text = { Text("Pitch range ±${(deck.pitchRange * 100).toInt()}% (tap to change)", style = Type.ui(13.sp)) },
                    onClick = { vm.cyclePitchRange(index); menu = false },
                )
                DropdownMenuItem(text = { Text("Eject track", style = Type.ui(13.sp, color = TS.Pink)) }, onClick = { vm.eject(index); menu = false })
            }
        }
    }
}

@Composable
private fun PitchColumn(vm: DjViewModel, index: Int, modifier: Modifier) {
    val deck = vm.deck(index)
    val label by engineValue { String.format(Locale.US, "%+.1f", deck.pitch * 100) }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = Type.mono(9.sp))
        VerticalFader(
            read = { (0.5 - deck.pitch / (2 * deck.pitchRange)).toFloat() },
            write = { v ->
                val snapped = if (abs(v - 0.5f) < 0.02f) 0.5f else v
                vm.setPitch(index, (0.5 - snapped) * 2 * deck.pitchRange)
            },
            color = TS.deck(index),
            capColor = TS.Text,
            centreLine = true,
            resetTo = 0.5f,
            description = "Tempo, deck ${vm.deckName(index)}",
            modifier = Modifier.width(24.dp).weight(1f),
        )
        Label("TEMPO", size = 7)
    }
}

@Composable
private fun TransportRow(vm: DjViewModel, index: Int) {
    val deck = vm.deck(index)
    val color = TS.deck(index)
    val playing by engineValue { deck.playing }
    val synced by engineValue { deck.synced }
    val keylock by engineValue { deck.keylock }
    val slip by engineValue { deck.slip }
    Row(Modifier.fillMaxWidth().height(40.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        // CUE: hold to preview from the cue point, let go to jump back.
        Box(
            Modifier.width(50.dp).fillMaxHeight().clip(RoundedCornerShape(10.dp))
                .border(1.5.dp, color, RoundedCornerShape(10.dp))
                .semantics { contentDescription = "Cue"; role = Role.Button }
                .pointerInput(index) {
                    detectTapGestures(onPress = {
                        vm.cuePress(index)
                        tryAwaitRelease()
                        vm.cueRelease(index)
                    })
                },
            contentAlignment = Alignment.Center,
        ) { Text("CUE", style = Type.label(11.sp, color)) }

        TsButton(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            color = color,
            filled = playing,
            description = if (playing) "Pause" else "Play",
            onClick = { vm.togglePlay(index) },
        ) { fg -> TsIcon(if (playing) TsIcons.Pause else TsIcons.Play, fg, 20.dp) }

        TsButton(
            modifier = Modifier.width(54.dp).fillMaxHeight(),
            color = TS.Lime,
            filled = synced,
            neutral = !synced,
            corner = 8.dp,
            description = "Sync",
            onClick = { vm.sync(index) },
        ) { fg ->
            TsIcon(TsIcons.Sync, fg, 11.dp)
            Spacer(Modifier.width(3.dp))
            Text("SYNC", style = Type.label(9.sp, fg))
        }
        MiniToggle("LOCK", keylock, TS.Amber, "Keylock", Modifier.width(44.dp).fillMaxHeight()) { vm.toggleKeylock(index) }
        MiniToggle("SLIP", slip, TS.Violet, "Slip mode", Modifier.width(40.dp).fillMaxHeight()) { vm.toggleSlip(index) }
        MiniToggle("Q", vm.settings.quantize, TS.Mint, "Quantize (snap to beat)", Modifier.width(30.dp).fillMaxHeight()) { vm.toggleQuantize() }
    }
}

// ------------------------------------------------------------------------------------------
// Mixer
// ------------------------------------------------------------------------------------------

@Composable
private fun MixerPanel(vm: DjViewModel, modifier: Modifier) {
    val shape = RoundedCornerShape(14.dp)
    val m = vm.mixer
    val pflA by engineValue { vm.deck(0).pfl }
    val pflB by engineValue { vm.deck(1).pfl }
    Column(
        modifier.clip(shape).background(TS.Graphite).border(1.dp, TS.Line, shape).padding(horizontal = 9.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("A", style = Type.display(10.sp, TS.Orange))
            Label("MIXER")
            Text("B", style = Type.display(10.sp, TS.Cyan))
        }
        for (label in listOf("HI", "LOW", "FILTER")) {
            Row(Modifier.fillMaxWidth().height(30.dp), verticalAlignment = Alignment.CenterVertically) {
                for (i in 0..1) {
                    val d = vm.deck(i)
                    if (i == 1) {
                        Text(label, style = Type.label(8.sp, TS.Text2), modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    }
                    Knob(
                        read = { when (label) { "HI" -> d.eqHigh; "LOW" -> d.eqLow; else -> d.filter } },
                        write = { v ->
                            when (label) {
                                "HI" -> { d.eqHigh = v }
                                "LOW" -> { d.eqLow = v }
                                else -> { d.filter = v }
                            }
                        },
                        color = TS.deck(i),
                        description = "$label, deck ${vm.deckName(i)}",
                        size = 30.dp,
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().weight(1f).padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                LevelMeter(read = { vm.deck(0).level }, modifier = Modifier.fillMaxHeight())
                VerticalFader(
                    read = { vm.deck(0).fader }, write = { vm.deck(0).fader = it },
                    color = TS.Orange, description = "Volume, deck A", modifier = Modifier.width(22.dp).fillMaxHeight(),
                )
            }
            Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
                PflButton("A", pflA, TS.Orange) { vm.togglePfl(0) }
                PflButton("B", pflB, TS.Cyan) { vm.togglePfl(1) }
                LevelMeter(read = { m.masterLevel }, modifier = Modifier.height(22.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                VerticalFader(
                    read = { vm.deck(1).fader }, write = { vm.deck(1).fader = it },
                    color = TS.Cyan, description = "Volume, deck B", modifier = Modifier.width(22.dp).fillMaxHeight(),
                )
                LevelMeter(read = { vm.deck(1).level }, modifier = Modifier.fillMaxHeight())
            }
        }
        Row(Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("A", style = Type.display(9.sp, TS.Orange))
            Spacer(Modifier.width(5.dp))
            Crossfader(read = { m.crossfader }, write = { m.crossfader = it }, modifier = Modifier.weight(1f).fillMaxHeight())
            Spacer(Modifier.width(5.dp))
            Text("B", style = Type.display(9.sp, TS.Cyan))
        }
    }
}

@Composable
private fun PflButton(deck: String, on: Boolean, color: Color, onClick: () -> Unit) {
    TsButton(Modifier.size(46.dp, 26.dp), color = color, filled = on, neutral = !on, corner = 7.dp, description = "Headphone cue, deck $deck", onClick = onClick) { fg ->
        TsIcon(TsIcons.Headphones, fg, 13.dp)
        Spacer(Modifier.width(2.dp))
        Text(deck, style = Type.label(9.sp, fg))
    }
}
