package com.tonyscratch.tsdj.ui

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tonyscratch.tsdj.DjViewModel
import com.tonyscratch.tsdj.PadPage
import com.tonyscratch.tsdj.audio.FxBeats
import com.tonyscratch.tsdj.audio.FxGroup
import com.tonyscratch.tsdj.audio.FxType
import com.tonyscratch.tsdj.audio.Keys
import com.tonyscratch.tsdj.data.Actions
import com.tonyscratch.tsdj.data.Shortcut
import com.tonyscratch.tsdj.ui.components.LabeledKnob
import com.tonyscratch.tsdj.ui.components.Pad
import com.tonyscratch.tsdj.ui.components.TsButton
import com.tonyscratch.tsdj.ui.components.TsIcon
import com.tonyscratch.tsdj.ui.components.TsIcons
import com.tonyscratch.tsdj.ui.components.engineValue
import com.tonyscratch.tsdj.ui.theme.TS
import com.tonyscratch.tsdj.ui.theme.Type
import kotlin.math.abs

private val LOOP_SIZES = doubleArrayOf(0.25, 0.5, 1.0, 2.0, 4.0, 8.0, 16.0, 32.0)
private val ROLL_SIZES = doubleArrayOf(1 / 32.0, 1 / 16.0, 1 / 8.0, 1 / 4.0, 1 / 2.0, 1.0, 2.0, 4.0)
private val JUMPS = doubleArrayOf(-32.0, -16.0, -4.0, -1.0, 1.0, 4.0, 16.0, 32.0)
private val KEY_PADS = listOf(-3, -2, -1, 0, 1, 2, 3, 99) // 0 = reset, 99 = match

/**
 * The deck's switchable pad section: 8 pads whose job changes with the page —
 * hot cues, loops, rolls, beat jumps, effects, sampler, key, or your own MY pads.
 */
@Composable
fun PadSection(vm: DjViewModel, index: Int, modifier: Modifier) {
    val page = vm.padPage[index]
    var edit by remember(page) { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        PageSelector(vm, index, Modifier.fillMaxWidth().height(24.dp))
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when (page) {
                PadPage.CUES -> CuePads(vm, index, edit)
                PadPage.LOOP -> LoopPads(vm, index)
                PadPage.ROLL -> RollPads(vm, index)
                PadPage.JUMP -> JumpPads(vm, index)
                PadPage.FX -> FxPads(vm, index)
                PadPage.SAMPLER -> SamplerPads(vm, edit)
                PadPage.KEY -> KeyPads(vm, index)
                PadPage.MY -> MyPads(vm, index, edit)
            }
        }
        Row(
            Modifier.fillMaxWidth().height(30.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            when (page) {
                PadPage.CUES -> {
                    Hint("Tap an empty pad to save a cue", Modifier.weight(1f))
                    EditToggle("DEL", edit, TS.Pink) { edit = !edit }
                }
                PadPage.LOOP -> LoopFooter(vm, index)
                PadPage.ROLL -> Hint("Hold a pad · the track carries on underneath", Modifier.weight(1f))
                PadPage.JUMP -> Hint("Jumps in beats (keeps the beat)", Modifier.weight(1f))
                PadPage.FX -> FxFooter(vm, index)
                PadPage.SAMPLER -> SamplerFooter(vm, edit) { edit = !edit }
                PadPage.KEY -> KeyFooter(vm, index)
                PadPage.MY -> {
                    Hint("Tap + to add your own pad", Modifier.weight(1f))
                    EditToggle("EDIT", edit, TS.Amber) { edit = !edit }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------
// Page selector: ◀ [ HOT CUES ▾ ] ▶
// ------------------------------------------------------------------------------------------

@Composable
private fun PageSelector(vm: DjViewModel, index: Int, modifier: Modifier) {
    var menu by remember { mutableStateOf(false) }
    val color = TS.deck(index)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        SmallSquare("◀", "Previous pad page") { vm.cyclePadPage(index, -1) }
        Box(Modifier.weight(1f).fillMaxHeight()) {
            Row(
                Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp)).background(TS.Charcoal)
                    .border(1.dp, color, RoundedCornerShape(6.dp))
                    .semantics { role = Role.Button; contentDescription = "Choose pad page" }
                    .pointerInput(Unit) { detectTapGestures { menu = true } },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(vm.padPage[index].label, style = Type.label(9.sp, color), maxLines = 1)
                Spacer(Modifier.width(3.dp))
                TsIcon(TsIcons.ChevronDown, color, 11.dp)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                for (p in PadPage.entries) {
                    DropdownMenuItem(
                        text = { Text(p.label, style = Type.ui(13.sp, if (p == vm.padPage[index]) 800 else 600, if (p == vm.padPage[index]) color else TS.Text)) },
                        onClick = { vm.padPage[index] = p; menu = false },
                    )
                }
            }
        }
        SmallSquare("▶", "Next pad page") { vm.cyclePadPage(index, 1) }
    }
}

@Composable
private fun SmallSquare(text: String, description: String, onClick: () -> Unit) {
    Box(
        Modifier.width(26.dp).fillMaxHeight().clip(RoundedCornerShape(6.dp)).background(TS.Charcoal)
            .semantics { role = Role.Button; contentDescription = description }
            .pointerInput(Unit) { detectTapGestures { onClick() } },
        contentAlignment = Alignment.Center,
    ) { Text(text, style = Type.ui(10.sp, 800, TS.Text2)) }
}

@Composable
private fun Hint(text: String, modifier: Modifier) {
    Text(text, style = Type.ui(9.sp, 600, TS.Muted), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = modifier)
}

@Composable
private fun EditToggle(text: String, on: Boolean, color: Color, onClick: () -> Unit) {
    TsButton(Modifier.width(44.dp).fillMaxHeight(), color = color, filled = on, neutral = !on, corner = 7.dp, description = "$text mode", onClick = onClick) { fg ->
        Text(text, style = Type.label(9.sp, fg))
    }
}

@Composable
private fun FooterButton(text: String, description: String, modifier: Modifier, color: Color = TS.Text, onClick: () -> Unit) {
    TsButton(modifier.fillMaxHeight(), neutral = true, corner = 7.dp, description = description, onClick = onClick) {
        Text(text, style = Type.label(9.sp, color), maxLines = 1)
    }
}

/** 2 rows × 4 pads. */
@Composable
private fun PadGrid(cell: @Composable (slot: Int, modifier: Modifier) -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (row in 0..1) {
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (col in 0..3) cell(row * 4 + col, Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun PadLabel(top: String, bottom: String?, fg: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(top, style = Type.mono(12.sp, color = fg), maxLines = 1)
        if (bottom != null) Text(bottom, style = Type.label(7.sp, fg), maxLines = 1, overflow = TextOverflow.Clip)
    }
}

// ------------------------------------------------------------------------------------------
// Hot cues
// ------------------------------------------------------------------------------------------

@Composable
private fun CuePads(vm: DjViewModel, index: Int, deleteMode: Boolean) {
    val d = vm.deck(index)
    @Suppress("UNUSED_VARIABLE") val v = vm.deckVersion[index]
    PadGrid { slot, m ->
        val set = d.hotCues[slot] >= 0
        val c = TS.cueColors[slot]
        Pad(
            modifier = m,
            color = if (deleteMode && set) TS.Pink else c,
            lit = set && !deleteMode,
            dashed = !set,
            description = if (set) "Hot cue ${slot + 1}" else "Save hot cue ${slot + 1}",
            onPress = { if (deleteMode) { if (set) vm.clearHotCue(index, slot) } else vm.hotCue(index, slot) },
        ) { fg -> PadLabel("${slot + 1}", if (deleteMode && set) "DELETE" else if (set) "CUE" else "+ SET", fg) }
    }
}

// ------------------------------------------------------------------------------------------
// Loops
// ------------------------------------------------------------------------------------------

@Composable
private fun LoopPads(vm: DjViewModel, index: Int) {
    val d = vm.deck(index)
    val active by engineValue { if (d.loopActive && !d.rolling) d.loopBeats else -1.0 }
    PadGrid { slot, m ->
        val size = LOOP_SIZES[slot]
        Pad(
            modifier = m,
            color = TS.Lime,
            lit = abs(active - size) < 1e-6,
            description = "Loop ${FxBeats.label(size)} beats",
            onPress = { vm.autoLoop(index, size) },
        ) { fg -> PadLabel(FxBeats.label(size), "BEAT${if (size > 1) "S" else ""}", fg) }
    }
}

@Composable
private fun RowScope.LoopFooter(vm: DjViewModel, index: Int) {
    val d = vm.deck(index)
    val marking by engineValue { d.loopInMark >= 0 && !d.loopActive }
    FooterButton("½", "Halve loop", Modifier.weight(1f)) { vm.loopHalf(index) }
    FooterButton("×2", "Double loop", Modifier.weight(1f)) { vm.loopDouble(index) }
    FooterButton("IN", "Loop in", Modifier.weight(1f), if (marking) TS.Lime else TS.Text) { vm.loopIn(index) }
    FooterButton("OUT", "Loop out", Modifier.weight(1f)) { vm.loopOut(index) }
    FooterButton("✕", "Exit loop", Modifier.weight(1f), TS.Pink) { vm.loopExit(index) }
}

@Composable
private fun RollPads(vm: DjViewModel, index: Int) {
    var held by remember { mutableIntStateOf(-1) }
    PadGrid { slot, m ->
        val size = ROLL_SIZES[slot]
        Pad(
            modifier = m,
            color = TS.Cyan,
            lit = held == slot,
            description = "Roll ${FxBeats.label(size)} beat",
            onPress = { held = slot; vm.rollStart(index, size) },
            onRelease = { if (held == slot) { held = -1; vm.rollStop(index) } },
        ) { fg -> PadLabel(FxBeats.label(size), "ROLL", fg) }
    }
}

@Composable
private fun JumpPads(vm: DjViewModel, index: Int) {
    PadGrid { slot, m ->
        val j = JUMPS[slot]
        Pad(
            modifier = m,
            color = if (j < 0) TS.Violet else TS.Mint,
            lit = false,
            description = "Jump ${j.toInt()} beats",
            onPress = { vm.beatJump(index, j) },
        ) { fg -> PadLabel(if (j > 0) "+${j.toInt()}" else "${j.toInt()}", "BEATS", fg) }
    }
}

// ------------------------------------------------------------------------------------------
// Effects
// ------------------------------------------------------------------------------------------

@Composable
private fun FxPads(vm: DjViewModel, index: Int) {
    val d = vm.deck(index)
    val group = vm.fxGroup[index]
    val list = FxType.inGroup(group)
    val color = TS.fxGroupColors[group.ordinal]
    val onSet by engineValue { list.map { d.fx.state(it).on } }
    PadGrid { slot, m ->
        val t = list.getOrNull(slot)
        if (t == null) {
            Box(m)
        } else {
            Pad(
                modifier = m,
                color = color,
                lit = onSet.getOrElse(slot) { false },
                selected = vm.fxSelected[index] == t,
                description = "${t.title} on deck ${vm.deckName(index)}",
                onPress = { vm.fxDown(index, t) },
                onRelease = { vm.fxUp(index, t) },
            ) { fg ->
                PadLabel(t.short, when (t.mode) {
                    com.tonyscratch.tsdj.audio.FxMode.HOLD -> "HOLD"
                    com.tonyscratch.tsdj.audio.FxMode.TRIGGER -> "TAP"
                    else -> null
                }, fg)
            }
        }
    }
}

@Composable
private fun RowScope.FxFooter(vm: DjViewModel, index: Int) {
    val d = vm.deck(index)
    val group = vm.fxGroup[index]
    val t = vm.fxSelected[index].let { if (it.group == group) it else FxType.inGroup(group).first() }
    val s = d.fx.state(t)
    val color = TS.fxGroupColors[group.ordinal]
    val amountText by engineValue { t.amountText(s.amount) }
    val paramText by engineValue { t.paramText(s.param) }
    TsButton(Modifier.width(52.dp).fillMaxHeight(), color = color, corner = 7.dp, description = "Effect group ${group.label}, tap for next", onClick = {
        val next = FxGroup.entries[(group.ordinal + 1) % FxGroup.entries.size]
        vm.fxGroup[index] = next
        vm.fxSelected[index] = FxType.inGroup(next).first()
    }) { fg -> Text(group.label, style = Type.label(8.sp, fg), maxLines = 1) }
    LabeledKnob(
        label = t.amountLabel, valueText = amountText,
        read = { s.amount }, write = { s.amount = it },
        color = color, modifier = Modifier.weight(1f),
    )
    LabeledKnob(
        label = t.paramLabel, valueText = paramText,
        read = { s.param }, write = { s.param = it },
        color = TS.Text2, modifier = Modifier.weight(1f),
    )
}

// ------------------------------------------------------------------------------------------
// Sampler
// ------------------------------------------------------------------------------------------

@Composable
private fun SamplerPads(vm: DjViewModel, edit: Boolean) {
    val context = LocalContext.current
    @Suppress("UNUSED_VARIABLE") val v = vm.samplerVersion
    val bankIdx = vm.samplerBank.coerceIn(0, (vm.banks.size - 1).coerceAtLeast(0))
    val bank = vm.banks.getOrNull(bankIdx)
    var pickFor by remember { mutableIntStateOf(-1) }
    var editPad by remember { mutableIntStateOf(-1) }
    val playing by engineValue { vm.mixer.sampler.playing }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val slot = pickFor
        pickFor = -1
        if (uri != null && slot >= 0) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {
            }
            var name = "Sound ${slot + 1}"
            try {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0)?.let { name = it }
                }
            } catch (_: Exception) {
            }
            vm.assignCustomPad(bankIdx, slot, uri, name)
        }
    }

    if (bank == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Making sounds…", style = Type.ui(11.sp, 600, TS.Muted))
        }
        return
    }
    PadGrid { slot, m ->
        val sound = bank.sounds.getOrNull(slot)
        val color = TS.cueColors[(slot + bankIdx * 2) % TS.cueColors.size]
        Pad(
            modifier = m,
            color = color,
            lit = sound != null && sound in playing,
            dashed = sound == null,
            description = sound?.name ?: "Empty pad ${slot + 1}",
            onPress = {
                when {
                    bank.custom && (sound == null) -> { pickFor = slot; picker.launch(arrayOf("audio/*")) }
                    bank.custom && edit -> editPad = slot
                    sound != null -> vm.samplerTrigger(bankIdx, slot)
                }
            },
        ) { fg ->
            Text(
                sound?.name?.uppercase() ?: "+ ADD",
                style = Type.label(8.sp, fg),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
        }
    }

    if (editPad >= 0) {
        val slot = editPad
        AlertDialog(
            onDismissRequest = { editPad = -1 },
            containerColor = TS.Charcoal,
            title = { Text(bank.sounds.getOrNull(slot)?.name ?: "Pad ${slot + 1}", style = Type.ui(15.sp, 800)) },
            text = { Text("Replace this sound with another file, or remove it.", style = Type.ui(13.sp, 600, TS.Text2)) },
            confirmButton = {
                TextButton(onClick = { editPad = -1; pickFor = slot; picker.launch(arrayOf("audio/*")) }) {
                    Text("Replace", style = Type.ui(13.sp, 800, TS.Orange))
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.clearCustomPad(bankIdx, slot); editPad = -1 }) {
                    Text("Remove", style = Type.ui(13.sp, 800, TS.Pink))
                }
            },
        )
    }
}

@Composable
private fun RowScope.SamplerFooter(vm: DjViewModel, edit: Boolean, onToggleEdit: () -> Unit) {
    val count = vm.banks.size
    val bank = vm.banks.getOrNull(vm.samplerBank)
    val vol by engineValue { (vm.mixer.sampler.volume * 100).toInt() }
    TsButton(
        Modifier.weight(1f).fillMaxHeight(), color = TS.Violet, corner = 7.dp,
        description = "Sound bank ${bank?.name ?: ""}, tap for next, hold for previous",
        onLongPress = { if (count > 0) vm.samplerBank = Math.floorMod(vm.samplerBank - 1, count) },
        onClick = { if (count > 0) vm.samplerBank = (vm.samplerBank + 1) % count },
    ) { fg -> Text(bank?.name ?: "…", style = Type.label(8.sp, fg), maxLines = 1, overflow = TextOverflow.Ellipsis) }
    LabeledKnob(
        label = "VOL", valueText = "$vol%",
        read = { vm.mixer.sampler.volume }, write = { vm.mixer.sampler.volume = it },
        color = TS.Violet,
    )
    if (bank?.custom == true) {
        EditToggle("EDIT", edit, TS.Amber, onToggleEdit)
    } else {
        FooterButton("■", "Stop all sounds", Modifier.width(30.dp), TS.Pink) { vm.samplerStopAll() }
    }
}

// ------------------------------------------------------------------------------------------
// Key
// ------------------------------------------------------------------------------------------

@Composable
private fun KeyPads(vm: DjViewModel, index: Int) {
    val d = vm.deck(index)
    val shift by engineValue { d.keyShift }
    PadGrid { slot, m ->
        val k = KEY_PADS[slot]
        val (top, bottom) = when (k) {
            0 -> "0" to "RESET"
            99 -> "✓" to "MATCH"
            else -> (if (k > 0) "+$k" else "$k") to "SEMI"
        }
        Pad(
            modifier = m,
            color = when (k) { 99 -> TS.Lime; 0 -> TS.Text2; else -> TS.Violet },
            lit = (k != 99 && k != 0 && shift == k) || (k == 0 && shift == 0),
            description = when (k) { 0 -> "Reset key"; 99 -> "Match key to the other deck"; else -> "Key ${top}" },
            onPress = {
                when (k) {
                    0 -> vm.keyReset(index)
                    99 -> vm.keyMatch(index)
                    else -> { vm.keyReset(index); vm.keyShift(index, k) }
                }
            },
        ) { fg -> PadLabel(top, bottom, fg) }
    }
}

@Composable
private fun RowScope.KeyFooter(vm: DjViewModel, index: Int) {
    val d = vm.deck(index)
    val musical = vm.settings.musicalKeys
    val text by engineValue {
        val base = d.analysis?.key
        val now = d.currentKey()
        val s = d.keyShift
        "${Keys.display(base, musical)} → ${Keys.display(now, musical)}" + if (s != 0) " (${if (s > 0) "+" else ""}$s)" else ""
    }
    val keylock by engineValue { d.keylock }
    Text(text, style = Type.mono(10.sp), maxLines = 1, modifier = Modifier.weight(1f))
    TsButton(
        Modifier.width(62.dp).fillMaxHeight(), color = TS.Amber, filled = keylock, neutral = !keylock, corner = 7.dp,
        description = "Keylock", onClick = { vm.toggleKeylock(index) },
    ) { fg -> Text("KEYLOCK", style = Type.label(8.sp, fg)) }
}

// ------------------------------------------------------------------------------------------
// MY pads (your own shortcuts on the deck)
// ------------------------------------------------------------------------------------------

@Composable
private fun MyPads(vm: DjViewModel, index: Int, edit: Boolean) {
    var assign by remember { mutableIntStateOf(-1) }
    var editSlot by remember { mutableIntStateOf(-1) }
    val codes = vm.myPads.getOrNull(index) ?: List(8) { null }
    PadGrid { slot, m ->
        val code = codes.getOrNull(slot)
        val label = code?.let { Actions.label(Shortcut(it, index), vm.banks).removePrefix("${vm.deckName(index)} ") }
        Pad(
            modifier = m,
            color = TS.deck(index),
            lit = false,
            dashed = code == null,
            description = label ?: "Add your own pad",
            onPress = {
                when {
                    code == null -> assign = slot
                    edit -> editSlot = slot
                    else -> vm.actionDown(code, index)
                }
            },
            onRelease = { if (code != null && !edit) vm.actionUp(code, index) },
        ) { fg ->
            if (code == null) TsIcon(TsIcons.Plus, fg, 16.dp)
            else Text(label ?: "?", style = Type.label(8.sp, fg), textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.padding(horizontal = 2.dp))
        }
    }
    if (assign >= 0) {
        val slot = assign
        ActionPicker(
            title = "Pad ${slot + 1} on deck ${vm.deckName(index)} does…",
            banks = vm.banks,
            fixedDeck = index,
            onPick = { code, _ -> vm.setMyPad(index, slot, code); assign = -1 },
            onDismiss = { assign = -1 },
        )
    }
    if (editSlot >= 0) {
        val slot = editSlot
        AlertDialog(
            onDismissRequest = { editSlot = -1 },
            containerColor = TS.Charcoal,
            title = { Text("Pad ${slot + 1}", style = Type.ui(15.sp, 800)) },
            text = { Text("Change what this pad does, or clear it.", style = Type.ui(13.sp, 600, TS.Text2)) },
            confirmButton = { TextButton(onClick = { editSlot = -1; assign = slot }) { Text("Change", style = Type.ui(13.sp, 800, TS.Orange)) } },
            dismissButton = { TextButton(onClick = { vm.setMyPad(index, slot, null); editSlot = -1 }) { Text("Clear", style = Type.ui(13.sp, 800, TS.Pink)) } },
        )
    }
}
