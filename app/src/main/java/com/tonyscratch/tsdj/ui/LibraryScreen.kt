package com.tonyscratch.tsdj.ui

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tonyscratch.tsdj.DjViewModel
import com.tonyscratch.tsdj.Screen
import com.tonyscratch.tsdj.audio.Camelot
import com.tonyscratch.tsdj.audio.Keys
import com.tonyscratch.tsdj.audio.TrackAnalysis
import com.tonyscratch.tsdj.data.LocalTrack
import com.tonyscratch.tsdj.ui.components.Label
import com.tonyscratch.tsdj.ui.components.TsButton
import com.tonyscratch.tsdj.ui.components.TsIcon
import com.tonyscratch.tsdj.ui.components.TsIcons
import com.tonyscratch.tsdj.ui.components.engineValue
import com.tonyscratch.tsdj.ui.theme.TS
import com.tonyscratch.tsdj.ui.theme.Type
import java.util.Locale
import kotlin.math.abs

private sealed class Source {
    data object All : Source()
    data class Folder(val path: String) : Source()
    data object Recent : Source()
    data object History : Source()
    data object Queue : Source()
    data class CrateAt(val index: Int) : Source()
}

private enum class SortBy(val label: String) { TITLE("TRACK"), BPM("BPM"), KEY("KEY"), TIME("TIME"), ADDED("ADDED") }

/** One folder on the phone, with how many tracks are in it (and below it). */
private class FolderNode(val path: String, val name: String, val depth: Int) {
    val children = sortedMapOf<String, FolderNode>(String.CASE_INSENSITIVE_ORDER)
    var direct = 0
    var total = 0
}

private fun buildTree(tracks: List<LocalTrack>): FolderNode {
    val root = FolderNode("", "Phone", -1)
    for (t in tracks) {
        val parts = t.folder.split('/').filter { it.isNotBlank() }
        var node = root
        node.total++
        var path = ""
        for ((i, p) in parts.withIndex()) {
            path += "$p/"
            node = node.children.getOrPut(p) { FolderNode(path, p, i) }
            node.total++
        }
        node.direct++
    }
    return root
}

private fun findNode(root: FolderNode, path: String): FolderNode? {
    if (path.isEmpty()) return root
    var node = root
    for (p in path.split('/').filter { it.isNotBlank() }) node = node.children[p] ?: return null
    return node
}

/** How far the track's tempo is from the reference, in %, choosing half/double time if closer. */
private fun bpmDiff(trackBpm: Double?, ref: Double?): Double? {
    if (trackBpm == null || ref == null || trackBpm <= 0) return null
    return listOf(1.0, 2.0, 0.5).map { (ref / (trackBpm * it) - 1.0) * 100.0 }.minByOrNull { abs(it) }
}

@Composable
fun LibraryScreen(vm: DjViewModel, hasPermission: Boolean, onRequestPermission: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var source by remember { mutableStateOf<Source>(Source.All) }
    var sortBy by remember { mutableStateOf(SortBy.TITLE) }
    var ascending by remember { mutableStateOf(true) }
    var vsDeck by remember { mutableIntStateOf(if (vm.deck(1).playing && !vm.deck(0).playing) 1 else 0) }
    var keyFilter by remember { mutableStateOf(false) }
    var bpmFilter by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(setOf<String>()) }
    var crateDialogFor by remember { mutableStateOf<LocalTrack?>(null) }
    var newCrate by remember { mutableStateOf(false) }

    LaunchedEffect(hasPermission) { if (hasPermission && !vm.libraryLoaded) vm.refreshLibrary() }

    val tree = remember(vm.tracks) { buildTree(vm.tracks) }
    val musical = vm.settings.musicalKeys

    Column(Modifier.fillMaxSize().background(TS.Black)) {
        Row(
            Modifier.fillMaxWidth().height(50.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BackButton { vm.screen = Screen.Mixer }
            Text("Library", style = Type.display(16.sp))
            SearchField(query, { query = it }, Modifier.weight(1f))
            for (i in 0..1) DeckChip(vm, i)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(TS.Charcoal))

        Row(Modifier.fillMaxSize()) {
            // ---------------- Sidebar ----------------
            LazyColumn(Modifier.width(206.dp).fillMaxHeight().padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                item { SectionLabel("ON THIS PHONE") }
                item { SideItem("All tracks", TsIcons.Note, source == Source.All, count = vm.tracks.size) { source = Source.All } }
                item { SideItem("Recently added", TsIcons.Download, source == Source.Recent) { source = Source.Recent } }
                item { SideItem("History", TsIcons.Disc, source == Source.History, count = vm.history.size) { source = Source.History } }
                item { Spacer(Modifier.height(6.dp)); SectionLabel("FOLDERS") }
                val flat = ArrayList<FolderNode>()
                fun walk(n: FolderNode) {
                    for (c in n.children.values) {
                        flat += c
                        if (c.path in expanded) walk(c)
                    }
                }
                walk(tree)
                items(flat, key = { "f:" + it.path }) { node ->
                    FolderItem(
                        node = node,
                        selected = (source as? Source.Folder)?.path == node.path,
                        open = node.path in expanded,
                        onToggle = { expanded = if (node.path in expanded) expanded - node.path else expanded + node.path },
                        onClick = { source = Source.Folder(node.path); if (node.children.isNotEmpty()) expanded = expanded + node.path },
                    )
                }
                item { Spacer(Modifier.height(6.dp)); SectionLabel("AUTOMIX", trailing = if (vm.automix) "ON" else null) }
                item { SideItem("Automix list", TsIcons.Playlist, source == Source.Queue, count = vm.queue.size) { source = Source.Queue } }
                item { Spacer(Modifier.height(6.dp)); SectionLabel("CRATES") }
                vm.crates.forEachIndexed { i, crate ->
                    item(key = "c$i") {
                        SideItem(
                            crate.name, null, source == Source.CrateAt(i),
                            dot = TS.crateColors[crate.colorIndex % TS.crateColors.size],
                            count = crate.trackIds.size,
                        ) { source = Source.CrateAt(i) }
                    }
                }
                item { SideItem("New crate", TsIcons.Plus, false) { newCrate = true } }
                item { Spacer(Modifier.height(6.dp)); SectionLabel("YOUTUBE MUSIC", trailing = "PREMIUM") }
                item { SideItem("Open YouTube Mix", TsIcons.Video, false) { vm.screen = Screen.YouTube } }
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(TS.Charcoal))

            // ---------------- Main list ----------------
            Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (!hasPermission) {
                    EmptyState("TS Dj needs permission to read the music on your phone.", "Allow access") { onRequestPermission() }
                    return@Column
                }
                if (source == Source.Queue) {
                    QueueView(vm)
                    return@Column
                }

                // Filters
                LazyRow(Modifier.fillMaxWidth().height(34.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    item { Chip(text = "Compare to deck ${vm.deckName(vsDeck)}", on = true, color = TS.deck(vsDeck)) { vsDeck = 1 - vsDeck } }
                    item { Chip(text = "Key match", on = keyFilter, color = TS.Lime) { keyFilter = !keyFilter } }
                    item { Chip(text = "BPM ±6%", on = bpmFilter, color = TS.Lime) { bpmFilter = !bpmFilter } }
                    item {
                        val p = vm.analyzeAllProgress
                        Chip(
                            text = if (p != null) "Analysing ${(p * 100).toInt()}% · stop" else "Analyse all",
                            on = p != null, color = TS.Amber, icon = TsIcons.Analyze,
                        ) { vm.analyzeAll() }
                    }
                }

                val ref = vm.deck(vsDeck)
                @Suppress("UNUSED_VARIABLE") val v0 = vm.deckVersion[0]
                @Suppress("UNUSED_VARIABLE") val v1 = vm.deckVersion[1]
                val refKey by engineValue { ref.currentKey() }
                val refBpm by engineValue { ref.effectiveBpm()?.let { Math.round(it * 10) / 10.0 } }

                // Folder breadcrumb and sub-folders
                val folderNode = (source as? Source.Folder)?.let { findNode(tree, it.path) }
                if (folderNode != null) {
                    Breadcrumb(folderNode.path) { source = if (it.isEmpty()) Source.All else Source.Folder(it) }
                }

                val list = remember(vm.tracks, source, query, keyFilter, bpmFilter, sortBy, ascending, vm.crates, vm.history, refKey, refBpm, vm.analyses.size) {
                    var l: List<LocalTrack> = when (val s = source) {
                        Source.All -> vm.tracks
                        is Source.Folder -> vm.tracks.filter { it.folder == s.path }
                        Source.Recent -> vm.tracks.sortedByDescending { it.dateAddedSec }.take(150)
                        Source.History -> {
                            val byId = vm.tracks.associateBy { it.info.id }
                            vm.history.asReversed().mapNotNull { byId[it.trackId] }.distinctBy { it.info.id }
                        }
                        is Source.CrateAt -> {
                            val ids = vm.crates.getOrNull(s.index)?.trackIds ?: emptyList()
                            vm.tracks.filter { it.info.id in ids }
                        }
                        Source.Queue -> emptyList()
                    }
                    if (query.isNotBlank()) {
                        val q = query.trim().lowercase(Locale.getDefault())
                        l = l.filter { it.info.title.lowercase(Locale.getDefault()).contains(q) || it.info.artist.lowercase(Locale.getDefault()).contains(q) }
                    }
                    if (keyFilter && refKey != null) l = l.filter { Camelot.compatible(vm.analyses[it.info.id]?.key, refKey) }
                    if (bpmFilter && refBpm != null) l = l.filter { t -> bpmDiff(vm.analyses[t.info.id]?.bpm, refBpm)?.let { abs(it) <= 6.0 } ?: false }
                    if (source != Source.History && source != Source.Recent || sortBy != SortBy.TITLE) {
                        val cmp: Comparator<LocalTrack> = when (sortBy) {
                            SortBy.TITLE -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.info.title }
                            SortBy.BPM -> compareBy { vm.analyses[it.info.id]?.bpm ?: Double.MAX_VALUE }
                            SortBy.KEY -> compareBy { keySortValue(vm.analyses[it.info.id]?.key) }
                            SortBy.TIME -> compareBy { it.info.durationMs }
                            SortBy.ADDED -> compareBy { it.dateAddedSec }
                        }
                        l = l.sortedWith(if (ascending) cmp else cmp.reversed())
                    }
                    l
                }

                // Column headers (tap to sort)
                Row(
                    Modifier.fillMaxWidth().height(26.dp).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Spacer(Modifier.width(36.dp))
                    Header("TRACK", sortBy == SortBy.TITLE, ascending, Modifier.weight(1f)) { if (sortBy == SortBy.TITLE) ascending = !ascending else { sortBy = SortBy.TITLE; ascending = true } }
                    Header("BPM", sortBy == SortBy.BPM, ascending, Modifier.width(46.dp)) { if (sortBy == SortBy.BPM) ascending = !ascending else { sortBy = SortBy.BPM; ascending = true } }
                    Box(Modifier.width(50.dp)) { Label("±BPM") }
                    Header("KEY", sortBy == SortBy.KEY, ascending, Modifier.width(40.dp)) { if (sortBy == SortBy.KEY) ascending = !ascending else { sortBy = SortBy.KEY; ascending = true } }
                    Box(Modifier.width(36.dp)) { Label("±KEY") }
                    Header("TIME", sortBy == SortBy.TIME, ascending, Modifier.width(40.dp)) { if (sortBy == SortBy.TIME) ascending = !ascending else { sortBy = SortBy.TIME; ascending = true } }
                    Box(Modifier.width(92.dp), contentAlignment = Alignment.CenterEnd) { Label("LOAD") }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(TS.Charcoal))

                LazyColumn(Modifier.fillMaxSize()) {
                    if (folderNode != null) {
                        items(folderNode.children.values.toList(), key = { "sub:" + it.path }) { child ->
                            SubFolderRow(child) {
                                source = Source.Folder(child.path)
                                expanded = expanded + folderNode.path
                            }
                        }
                    }
                    items(list, key = { it.info.id }) { t ->
                        TrackRow(vm, t, refKey, refBpm, musical, onCrate = { crateDialogFor = t })
                    }
                    if (vm.libraryLoaded && list.isEmpty() && (folderNode == null || folderNode.children.isEmpty())) {
                        item {
                            Text(
                                if (vm.tracks.isEmpty()) "No music found on this phone. Copy songs into the Music or Download folder."
                                else if (source == Source.History) "Tracks you play for 45 seconds or more show up here."
                                else "No tracks match.",
                                style = Type.ui(13.sp, 600, TS.Text2),
                                modifier = Modifier.padding(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    crateDialogFor?.let { t ->
        AlertDialog(
            onDismissRequest = { crateDialogFor = null },
            containerColor = TS.Charcoal,
            title = { Text("Add “${t.info.title}” to a crate", style = Type.ui(15.sp, 800)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (vm.crates.isEmpty()) Text("No crates yet. Make one with “New crate”.", style = Type.ui(13.sp, 600, TS.Text2))
                    vm.crates.forEachIndexed { i, c ->
                        val inIt = t.info.id in c.trackIds
                        Row(
                            Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(8.dp))
                                .background(if (inIt) TS.Line else Color.Transparent)
                                .pointerInput(i, inIt) { detectTapGestures { vm.toggleInCrate(i, t.info.id) } }
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(TS.crateColors[c.colorIndex % TS.crateColors.size]))
                            Spacer(Modifier.width(10.dp))
                            Text(c.name, style = Type.ui(13.sp), modifier = Modifier.weight(1f))
                            if (inIt) TsIcon(TsIcons.Check, TS.Lime, 16.dp)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { crateDialogFor = null }) { Text("Done", style = Type.ui(13.sp, 800, TS.Orange)) } },
        )
    }

    if (newCrate) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { newCrate = false },
            containerColor = TS.Charcoal,
            title = { Text("New crate", style = Type.ui(15.sp, 800)) },
            text = { TextInput(name, { name = it }, "Crate name, e.g. Friday set") },
            confirmButton = { TextButton(onClick = { vm.createCrate(name); newCrate = false }) { Text("Create", style = Type.ui(13.sp, 800, TS.Orange)) } },
            dismissButton = { TextButton(onClick = { newCrate = false }) { Text("Cancel", style = Type.ui(13.sp, 700, TS.Text2)) } },
        )
    }
}

/** Sort order for keys: around the Camelot wheel, minor before major. */
private fun keySortValue(key: String?): Int {
    if (key == null) return 999
    val n = key.dropLast(1).toIntOrNull() ?: return 999
    return n * 2 + if (key.endsWith("B")) 1 else 0
}

@Composable
private fun TrackRow(
    vm: DjViewModel,
    t: LocalTrack,
    refKey: String?,
    refBpm: Double?,
    musical: Boolean,
    onCrate: () -> Unit,
) {
    val a: TrackAnalysis? = vm.analyses[t.info.id]
    val onA by engineValue { vm.deck(0).info?.id == t.info.id }
    val onB by engineValue { vm.deck(1).info?.id == t.info.id }
    var menu by remember { mutableStateOf(false) }
    val played = remember(vm.history) { vm.history.any { it.trackId == t.info.id } }

    val diff = bpmDiff(a?.bpm, refBpm)
    val diffColor = when {
        diff == null -> TS.Muted
        abs(diff) <= 4.0 -> TS.Lime
        abs(diff) <= 8.0 -> TS.Amber
        else -> TS.Muted
    }
    val shift = Keys.bestShift(a?.key, refKey)
    val keyColor = when {
        shift == 0 -> TS.Lime
        shift != null && abs(shift) == 1 -> TS.Amber
        else -> TS.Text2
    }
    val rowBg = when {
        onA -> TS.Orange.copy(alpha = 0.10f)
        onB -> TS.Cyan.copy(alpha = 0.10f)
        else -> Color.Transparent
    }
    val artColors = listOf(TS.Orange, TS.Amber, TS.Pink, TS.Lime, TS.Cyan, TS.Ember)
    Box {
        Row(
            Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(8.dp)).background(rowBg)
                .pointerInput(t.info.id) { detectTapGestures(onLongPress = { menu = true }) }
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(artColors[Math.floorMod(t.info.id.hashCode(), artColors.size)]),
                contentAlignment = Alignment.Center,
            ) { TsIcon(if (played) TsIcons.Check else TsIcons.Disc, TS.Black, 20.dp) }
            Column(Modifier.weight(1f)) {
                Text(t.info.title, style = Type.ui(13.sp, 800, if (played) TS.Text2 else TS.Text), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(t.info.artist, style = Type.ui(11.sp, 600, TS.Text2), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(a?.bpm?.let { String.format(Locale.US, "%.1f", it) } ?: "—", style = Type.mono(12.sp), modifier = Modifier.width(46.dp))
            Text(
                diff?.let { String.format(Locale.US, "%+.1f%%", it) } ?: "—",
                style = Type.mono(10.sp, 500, diffColor), modifier = Modifier.width(50.dp), maxLines = 1,
            )
            Box(Modifier.width(40.dp)) {
                Text(
                    Keys.display(a?.key, musical),
                    style = Type.mono(10.sp, color = keyColor),
                    modifier = Modifier.border(1.dp, keyColor, RoundedCornerShape(5.dp)).padding(horizontal = 4.dp, vertical = 1.dp),
                    maxLines = 1,
                )
            }
            Text(
                when {
                    shift == null -> "—"
                    shift == 0 -> "✓"
                    else -> if (shift > 0) "+$shift" else "$shift"
                },
                style = Type.mono(11.sp, 700, keyColor), modifier = Modifier.width(36.dp),
            )
            Text(ms(t.info.durationMs / 1000.0), style = Type.mono(11.sp, 500, TS.Text2), modifier = Modifier.width(40.dp))
            Row(Modifier.width(92.dp), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End)) {
                for (i in 0..1) {
                    val on = if (i == 0) onA else onB
                    TsButton(
                        modifier = Modifier.size(42.dp, 36.dp),
                        color = TS.deck(i),
                        filled = on,
                        corner = 8.dp,
                        description = "Load ${t.info.title} to deck ${vm.deckName(i)}",
                        onClick = { vm.loadToDeck(i, t.info) },
                    ) { fg -> Text(if (on) "ON" else vm.deckName(i), style = Type.display(if (on) 9.sp else 11.sp, fg)) }
                }
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Load to deck A", style = Type.ui(13.sp)) }, onClick = { vm.loadToDeck(0, t.info); menu = false })
            DropdownMenuItem(text = { Text("Load to deck B", style = Type.ui(13.sp)) }, onClick = { vm.loadToDeck(1, t.info); menu = false })
            DropdownMenuItem(text = { Text("Automix: play next", style = Type.ui(13.sp)) }, onClick = { vm.queueAdd(t.info, next = true); menu = false })
            DropdownMenuItem(text = { Text("Automix: add to list", style = Type.ui(13.sp)) }, onClick = { vm.queueAdd(t.info); menu = false })
            DropdownMenuItem(text = { Text("Add to crate…", style = Type.ui(13.sp)) }, onClick = { menu = false; onCrate() })
        }
    }
}

@Composable
private fun QueueView(vm: DjViewModel) {
    Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Automix list", style = Type.ui(14.sp, 800), modifier = Modifier.weight(1f))
        Text("Crossfade ${vm.settings.automixSeconds.toInt()} s · change in Settings", style = Type.ui(11.sp, 600, TS.Text2))
        TsButton(Modifier.height(34.dp), neutral = true, description = "Clear the Automix list", onClick = { vm.queueClear() }) { fg ->
            Text("Clear", style = Type.ui(12.sp, 800, fg))
        }
        TsButton(Modifier.height(34.dp), color = TS.Lime, filled = vm.automix, description = "Automix on or off", onClick = { vm.toggleAutomix() }) { fg ->
            Text(if (vm.automix) "AUTOMIX ON" else "START AUTOMIX", style = Type.label(11.sp, fg))
        }
    }
    if (vm.queue.isEmpty()) {
        EmptyState("Press and hold a track, then choose “Automix: add to list”. Automix plays the list for you, syncing and crossfading each track.", null) {}
        return
    }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        itemsIndexed(vm.queue, key = { i, t -> "$i:${t.id}" }) { i, t ->
            Row(
                Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(8.dp)).background(TS.Graphite).padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("${i + 1}", style = Type.mono(12.sp, color = TS.Lime), modifier = Modifier.width(24.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.title, style = Type.ui(13.sp, 800), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(t.artist, style = Type.ui(11.sp, 600, TS.Text2), maxLines = 1)
                }
                QueueButton("▲", "Move up") { vm.queueMove(i, -1) }
                QueueButton("▼", "Move down") { vm.queueMove(i, 1) }
                QueueButton("✕", "Remove", TS.Pink) { vm.queueRemove(i) }
            }
        }
    }
}

@Composable
private fun QueueButton(text: String, description: String, color: Color = TS.Text, onClick: () -> Unit) {
    TsButton(Modifier.size(38.dp, 34.dp), neutral = true, corner = 8.dp, description = description, onClick = onClick) {
        Text(text, style = Type.ui(12.sp, 800, color))
    }
}

@Composable
private fun Breadcrumb(path: String, onGo: (String) -> Unit) {
    val parts = path.split('/').filter { it.isNotBlank() }
    LazyRow(Modifier.fillMaxWidth().height(30.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        item { Crumb("Phone", false) { onGo("") } }
        itemsIndexed(parts) { i, p ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("›", style = Type.ui(12.sp, 700, TS.Muted))
                Crumb(p, i == parts.lastIndex) { onGo(parts.take(i + 1).joinToString("/") + "/") }
            }
        }
    }
}

@Composable
private fun Crumb(text: String, current: Boolean, onClick: () -> Unit) {
    Text(
        text,
        style = Type.ui(12.sp, if (current) 800 else 600, if (current) TS.Orange else TS.Text2),
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            .semantics { role = Role.Button }
            .pointerInput(text) { detectTapGestures { onClick() } },
        maxLines = 1,
    )
}

@Composable
private fun SubFolderRow(node: FolderNode, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(8.dp))
            .semantics { role = Role.Button; contentDescription = "Open folder ${node.name}" }
            .pointerInput(node.path) { detectTapGestures { onOpen() } }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(TS.Charcoal), contentAlignment = Alignment.Center) {
            TsIcon(TsIcons.Folder, TS.Amber, 19.dp)
        }
        Text(node.name, style = Type.ui(13.sp, 800), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${node.total} tracks", style = Type.ui(11.sp, 600, TS.Text2))
        Text("›", style = Type.ui(16.sp, 800, TS.Muted))
    }
}

@Composable
private fun FolderItem(node: FolderNode, selected: Boolean, open: Boolean, onToggle: () -> Unit, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(32.dp).clip(RoundedCornerShape(8.dp))
            .background(if (selected) TS.Orange else Color.Transparent)
            .padding(start = (node.depth * 12).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = if (selected) TS.Black else TS.Text
        Box(
            Modifier.size(24.dp, 32.dp)
                .semantics { role = Role.Button; contentDescription = if (open) "Collapse ${node.name}" else "Expand ${node.name}" }
                .pointerInput(node.path, open) { detectTapGestures { onToggle() } },
            contentAlignment = Alignment.Center,
        ) {
            if (node.children.isNotEmpty()) Text(if (open) "▾" else "▸", style = Type.ui(11.sp, 800, fg))
        }
        Row(
            Modifier.weight(1f).fillMaxHeight()
                .semantics { role = Role.Tab }
                .pointerInput(node.path) { detectTapGestures { onClick() } }
                .padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TsIcon(TsIcons.Folder, if (selected) TS.Black else TS.Amber, 14.dp)
            Spacer(Modifier.width(6.dp))
            Text(node.name, style = Type.ui(12.sp, if (selected) 800 else 600, fg), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text("${node.total}", style = Type.mono(10.sp, 500, if (selected) TS.Black else TS.Muted))
        }
    }
}

@Composable
private fun RowScope.Header(text: String, active: Boolean, ascending: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .fillMaxHeight()
            .semantics { role = Role.Button; contentDescription = "Sort by $text" }
            .pointerInput(text) { detectTapGestures { onClick() } },
        contentAlignment = Alignment.CenterStart,
    ) {
        Label(if (active) "$text ${if (ascending) "▲" else "▼"}" else text, if (active) TS.Orange else TS.Muted)
    }
}

@Composable
private fun DeckChip(vm: DjViewModel, i: Int) {
    val title by engineValue { vm.deck(i).info?.title ?: "Empty" }
    Row(
        Modifier.height(36.dp).clip(RoundedCornerShape(10.dp)).background(TS.Graphite)
            .border(1.dp, TS.deck(i), RoundedCornerShape(10.dp)).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(vm.deckName(i), style = Type.display(10.sp, TS.deck(i)))
        Spacer(Modifier.width(6.dp))
        Text(title, style = Type.ui(11.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(84.dp))
    }
}

@Composable
private fun SectionLabel(text: String, trailing: String? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 2.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { Label(text) }
        if (trailing != null) Label(trailing, TS.Lime)
    }
}

@Composable
private fun SideItem(
    text: String,
    icon: ImageVector?,
    selected: Boolean,
    dot: Color? = null,
    count: Int? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(8.dp))
            .background(if (selected) TS.Orange else Color.Transparent)
            .semantics { role = Role.Tab }
            .pointerInput(text) { detectTapGestures { onClick() } }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = if (selected) TS.Black else TS.Text
        if (icon != null) TsIcon(icon, fg, 15.dp)
        if (dot != null) Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(dot))
        Spacer(Modifier.width(8.dp))
        Text(text, style = Type.ui(12.sp, if (selected) 800 else 600, fg), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (count != null) Text("$count", style = Type.mono(10.sp, 500, if (selected) TS.Black else TS.Muted))
    }
}
