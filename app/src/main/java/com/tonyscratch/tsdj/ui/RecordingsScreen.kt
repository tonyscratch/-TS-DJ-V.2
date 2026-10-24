package com.tonyscratch.tsdj.ui

import android.content.Intent
import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tonyscratch.tsdj.DjViewModel
import com.tonyscratch.tsdj.Screen
import com.tonyscratch.tsdj.data.Recording
import com.tonyscratch.tsdj.ui.components.ProgressBar
import com.tonyscratch.tsdj.ui.components.TsButton
import com.tonyscratch.tsdj.ui.components.TsIcon
import com.tonyscratch.tsdj.ui.components.TsIcons
import com.tonyscratch.tsdj.ui.components.engineValue
import com.tonyscratch.tsdj.ui.theme.TS
import com.tonyscratch.tsdj.ui.theme.Type
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Your recorded mixes: play them back, rename, share or delete. */
@Composable
fun RecordingsScreen(vm: DjViewModel) {
    val context = LocalContext.current
    var playingUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    val player = remember { arrayOfNulls<MediaPlayer>(1) }
    var renaming by remember { mutableStateOf<Recording?>(null) }
    var deleting by remember { mutableStateOf<Recording?>(null) }
    val elapsed by engineValue { vm.recorder.elapsedSec }

    fun stopPlayback() {
        player[0]?.let { try { it.stop() } catch (_: Exception) {}; it.release() }
        player[0] = null
        playingUri = null
        progress = 0f
    }

    fun play(r: Recording) {
        stopPlayback()
        try {
            val mp = MediaPlayer()
            mp.setDataSource(context, r.uri)
            mp.setOnCompletionListener { stopPlayback() }
            mp.prepare()
            mp.start()
            player[0] = mp
            playingUri = r.uri
        } catch (e: Exception) {
            vm.toast("Couldn't play this mix")
        }
    }

    DisposableEffect(Unit) { onDispose { stopPlayback() } }
    LaunchedEffect(Unit) { vm.refreshRecordings() }
    LaunchedEffect(playingUri) {
        while (playingUri != null) {
            player[0]?.let { if (it.duration > 0) progress = it.currentPosition.toFloat() / it.duration }
            delay(250)
        }
    }

    Column(Modifier.fillMaxSize().background(TS.Black)) {
        Row(
            Modifier.fillMaxWidth().height(50.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BackButton { vm.screen = Screen.Mixer }
            Text("Recordings", style = Type.display(16.sp))
            Text("Saved in Music/TS Dj", style = Type.ui(11.sp, 600, TS.Text2))
            Spacer(Modifier.weight(1f))
            TsButton(
                modifier = Modifier.height(34.dp),
                color = TS.Pink,
                filled = vm.recording,
                corner = 17.dp,
                description = if (vm.recording) "Stop recording" else "Start recording",
                onClick = { vm.toggleRecording() },
            ) { fg ->
                Box(Modifier.size(8.dp).clip(CircleShape).background(fg))
                Spacer(Modifier.width(6.dp))
                Text(if (vm.recording) "STOP  ${hms(elapsed)}" else "RECORD A MIX", style = Type.mono(12.sp, color = fg))
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(TS.Charcoal))

        if (vm.recordings.isEmpty()) {
            EmptyState("No mixes yet. Press REC on the mixer to record what you play.", null) {}
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(vm.recordings, key = { it.uri.toString() }) { r ->
                    val isPlaying = playingUri == r.uri
                    Row(
                        Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(12.dp)).background(TS.Graphite)
                            .border(1.dp, if (isPlaying) TS.Pink else TS.Line, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        TsButton(
                            modifier = Modifier.size(42.dp),
                            color = TS.Pink,
                            filled = isPlaying,
                            corner = 12.dp,
                            description = if (isPlaying) "Stop" else "Play ${r.name}",
                            onClick = { if (isPlaying) stopPlayback() else play(r) },
                        ) { fg -> TsIcon(if (isPlaying) TsIcons.Pause else TsIcons.Play, fg, 18.dp) }
                        Column(Modifier.weight(1f)) {
                            Text(r.name, style = Type.ui(13.sp, 800), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(r.dateAddedSec * 1000))} · " +
                                    "${ms(r.durationMs / 1000.0)} · ${"%.0f".format(Locale.US, r.sizeBytes / 1_048_576.0)} MB",
                                style = Type.ui(11.sp, 600, TS.Text2),
                            )
                            if (isPlaying) {
                                Spacer(Modifier.height(4.dp))
                                ProgressBar(progress, TS.Pink)
                            }
                        }
                        SmallAction(TsIcons.Share, "Share ${r.name}") {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "audio/wav"
                                putExtra(Intent.EXTRA_STREAM, r.uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(send, "Share mix"))
                        }
                        SmallAction(TsIcons.Pen, "Rename ${r.name}") { renaming = r }
                        SmallAction(TsIcons.Delete, "Delete ${r.name}", TS.Pink) { deleting = r }
                    }
                }
            }
        }
    }

    renaming?.let { r ->
        var name by remember(r) { mutableStateOf(r.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            containerColor = TS.Charcoal,
            title = { Text("Rename mix", style = Type.ui(15.sp, 800)) },
            text = { TextInput(name, { name = it }, "Mix name") },
            confirmButton = { TextButton(onClick = { vm.renameRecording(r, name); renaming = null }) { Text("Save", style = Type.ui(13.sp, 800, TS.Orange)) } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel", style = Type.ui(13.sp, 700, TS.Text2)) } },
        )
    }

    deleting?.let { r ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = TS.Charcoal,
            title = { Text("Delete “${r.name}”?", style = Type.ui(15.sp, 800)) },
            text = { Text("This removes the file from your phone.", style = Type.ui(13.sp, 600, TS.Text2)) },
            confirmButton = {
                TextButton(onClick = {
                    if (playingUri == r.uri) stopPlayback()
                    vm.deleteRecording(r)
                    deleting = null
                }) { Text("Delete", style = Type.ui(13.sp, 800, TS.Pink)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel", style = Type.ui(13.sp, 700, TS.Text2)) } },
        )
    }
}

@Composable
private fun SmallAction(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, color: androidx.compose.ui.graphics.Color = TS.Text, onClick: () -> Unit) {
    TsButton(modifier = Modifier.size(40.dp), neutral = true, corner = 10.dp, description = description, onClick = onClick) {
        TsIcon(icon, color, 17.dp)
    }
}
