package com.tonyscratch.tsdj.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tonyscratch.tsdj.DjViewModel
import com.tonyscratch.tsdj.Screen
import com.tonyscratch.tsdj.ui.components.Label
import com.tonyscratch.tsdj.ui.theme.TS
import com.tonyscratch.tsdj.ui.theme.Type

@Composable
fun SettingsScreen(vm: DjViewModel) {
    val s = vm.settings
    Column(Modifier.fillMaxSize().background(TS.Black)) {
        Row(
            Modifier.fillMaxWidth().height(50.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BackButton { vm.screen = Screen.Mixer }
            Text("Settings", style = Type.display(16.sp))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(TS.Charcoal))
        Row(Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Card("PLAYING") {
                    Toggle("Keylock on for new decks", "Changing tempo keeps the song in its key", s.keylockDefault) {
                        vm.updateSettings(s.copy(keylockDefault = it))
                        for (i in 0..1) vm.deck(i).keylock = it
                    }
                    Toggle("Keep tempo when loading a track", "The tempo fader stays where it is when you change songs", s.keepTempoOnLoad) {
                        vm.updateSettings(s.copy(keepTempoOnLoad = it))
                    }
                    Toggle("Quantize", "Cues, loops and jumps snap to the beat", s.quantize) { vm.updateSettings(s.copy(quantize = it)) }
                    Toggle("Auto-gain", "Brings quiet and loud tracks to a similar volume", s.autoGain) { vm.updateSettings(s.copy(autoGain = it)) }
                    Toggle("Auto key match", "When a track loads, nudge its key by up to 1 semitone to mix with the playing deck", s.autoKeyMatch) {
                        vm.updateSettings(s.copy(autoKeyMatch = it))
                    }
                }
                Card("DISPLAY") {
                    Toggle("Musical key names", "Show keys as Am / C instead of 8A / 8B", s.musicalKeys) { vm.updateSettings(s.copy(musicalKeys = it)) }
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Card("HEADPHONES") {
                    Toggle(
                        "Split output for headphone cue",
                        "Use a stereo splitter cable: left = speakers (master), right = headphones (decks with the headphone button on)",
                        s.splitCue,
                    ) { vm.updateSettings(s.copy(splitCue = it)) }
                }
                Card("AUTOMIX") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Crossfade length", style = Type.ui(13.sp, 700), modifier = Modifier.weight(1f))
                        Text("${s.automixSeconds.toInt()} s", style = Type.mono(13.sp, color = TS.Lime))
                    }
                    Slider(
                        value = s.automixSeconds,
                        onValueChange = { vm.updateSettings(s.copy(automixSeconds = it)) },
                        valueRange = 2f..30f,
                        colors = SliderDefaults.colors(thumbColor = TS.Text, activeTrackColor = TS.Lime, inactiveTrackColor = TS.Line),
                        modifier = Modifier.semantics { contentDescription = "Automix crossfade length" },
                    )
                }
                Card("ABOUT") {
                    Text("Tony Scratch Dj 0.2", style = Type.ui(13.sp, 800))
                    Text("Recordings are saved in Music/TS Dj. Sounds in the built-in sampler banks are generated by the app.", style = Type.ui(11.sp, 600, TS.Text2))
                }
            }
        }
    }
}

@Composable
private fun Card(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(TS.Graphite)
            .border(1.dp, TS.Line, RoundedCornerShape(14.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Label(title)
        content()
    }
}

@Composable
private fun Toggle(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 10.dp)) {
            Text(title, style = Type.ui(13.sp, 700))
            Text(detail, style = Type.ui(11.sp, 600, TS.Text2))
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = TS.Orange, checkedThumbColor = TS.Black,
                uncheckedTrackColor = TS.Charcoal, uncheckedThumbColor = TS.Muted, uncheckedBorderColor = TS.Line2,
            ),
        )
    }
}
