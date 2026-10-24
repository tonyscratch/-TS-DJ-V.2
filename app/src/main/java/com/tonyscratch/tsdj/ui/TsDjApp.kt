package com.tonyscratch.tsdj.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tonyscratch.tsdj.DjViewModel
import com.tonyscratch.tsdj.Screen
import com.tonyscratch.tsdj.ui.components.LocalFrameTick
import com.tonyscratch.tsdj.ui.theme.TS
import com.tonyscratch.tsdj.ui.theme.Type

/** The whole app: picks the current screen and shows short messages at the bottom. */
@Composable
fun TsDjApp(vm: DjViewModel, hasPermission: Boolean, requestPermission: () -> Unit) {
    // A clock that ticks every display frame so waveforms, jog wheels and meters stay live.
    val tick = remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) withFrameNanos { tick.longValue = it }
    }

    BackHandler(enabled = vm.screen != Screen.Mixer) { vm.screen = Screen.Mixer }

    CompositionLocalProvider(LocalFrameTick provides tick) {
        Box(
            Modifier
                .fillMaxSize()
                .background(TS.Black)
                .windowInsetsPadding(WindowInsets.displayCutout),
        ) {
            when (vm.screen) {
                Screen.Mixer -> MixerScreen(vm)
                Screen.Library -> LibraryScreen(vm, hasPermission, requestPermission)
                Screen.YouTube -> YouTubeScreen(vm)
                Screen.SPen -> SPenScreen(vm)
                Screen.Recordings -> RecordingsScreen(vm)
                Screen.Settings -> SettingsScreen(vm)
            }
            vm.message?.let { text ->
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 14.dp)
                        .clip(RoundedCornerShape(50))
                        .background(TS.Charcoal)
                        .border(1.dp, TS.Orange, RoundedCornerShape(50))
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                ) {
                    Text(text, style = Type.ui(12.sp, 700))
                }
            }
        }
    }
}
