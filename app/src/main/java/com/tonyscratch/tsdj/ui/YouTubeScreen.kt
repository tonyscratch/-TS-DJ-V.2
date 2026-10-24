package com.tonyscratch.tsdj.ui

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import com.tonyscratch.tsdj.DjViewModel
import com.tonyscratch.tsdj.Screen
import com.tonyscratch.tsdj.ui.components.Crossfader
import com.tonyscratch.tsdj.ui.components.Label
import com.tonyscratch.tsdj.ui.components.ProgressBar
import com.tonyscratch.tsdj.ui.components.Segmented
import com.tonyscratch.tsdj.ui.components.Tag
import com.tonyscratch.tsdj.ui.components.TsButton
import com.tonyscratch.tsdj.ui.components.TsIcon
import com.tonyscratch.tsdj.ui.components.TsIcons
import com.tonyscratch.tsdj.youtube.BrowseTab
import com.tonyscratch.tsdj.youtube.YouTubeApi
import com.tonyscratch.tsdj.youtube.YouTubeApiException
import com.tonyscratch.tsdj.youtube.YouTubeMixState
import com.tonyscratch.tsdj.youtube.YtItem
import com.tonyscratch.tsdj.youtube.YtView
import com.tonyscratch.tsdj.ui.theme.TS
import com.tonyscratch.tsdj.ui.theme.Type
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

private val YT_SCOPE = Scope("https://www.googleapis.com/auth/youtube.readonly")

/**
 * YouTube Mix: two YouTube players with volume and crossfade.
 * Follows YouTube's player rules: each player stays on screen while it plays, at least
 * 200 × 200, with nothing drawn on top of it, and videos never start on their own.
 */
@Composable
fun YouTubeScreen(vm: DjViewModel) {
    val yt = vm.youtube
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val players = remember { arrayOfNulls<YouTubePlayer>(2) }
    val views = remember { Array(2) { i -> createPlayerView(context, i, yt, players) } }
    val fadeJob = remember { arrayOfNulls<Job>(1) }

    DisposableEffect(lifecycleOwner) {
        // When TS Dj goes to the background the players are hidden, so they must pause (YouTube rule).
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) players.forEach { it?.pause() }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            views.forEach { it.release() }
            players[0] = null
            players[1] = null
            yt.decks.forEach { d -> d.playing = false; d.ready = false }
        }
    }

    // Keep each player's volume in step with its fader and the crossfader.
    LaunchedEffect(Unit) {
        snapshotFlow { Pair(yt.gain(0), yt.gain(1)) }.collect { (a, b) ->
            players[0]?.setVolume((a * 100).toInt().coerceIn(0, 100))
            players[1]?.setVolume((b * 100).toInt().coerceIn(0, 100))
        }
    }

    // ---- Google sign-in for the YouTube library ---------------------------------------------
    fun loadBrowse() {
        val token = yt.accessToken ?: return
        scope.launch {
            yt.loading = true
            yt.browseError = null
            try {
                when (yt.tab) {
                    BrowseTab.Playlists -> {
                        val open = yt.openPlaylist
                        if (open == null) {
                            yt.playlists = YouTubeApi.myPlaylists(token)
                        } else {
                            yt.items = YouTubeApi.playlistItems(token, open.id)
                        }
                    }
                    BrowseTab.Liked -> {
                        yt.items = YouTubeApi.likedVideos(token)
                    }
                    BrowseTab.Search -> {
                        if (yt.searchText.isNotBlank()) yt.items = YouTubeApi.search(token, yt.searchText)
                    }
                    BrowseTab.Link -> {}
                }
                Unit
            } catch (e: YouTubeApiException) {
                if (e.code == 401) yt.accessToken = null
                yt.browseError = if (e.code == 401) "Your sign-in expired. Sign in again." else e.message
            } catch (e: Exception) {
                yt.browseError = "Couldn't reach YouTube. Check your internet connection."
            } finally {
                yt.loading = false
            }
        }
    }

    fun signedIn(token: String?) {
        if (token.isNullOrEmpty()) {
            yt.signInError = "Google didn't send a sign-in token. Try again."
            return
        }
        yt.accessToken = token
        yt.signInError = null
        yt.browsing = true
        loadBrowse()
    }

    val authLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            try {
                val auth = Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(result.data)
                signedIn(auth.accessToken)
            } catch (e: Exception) {
                yt.signInError = signInProblem(e)
            }
        } else {
            yt.signInError = "Sign-in didn't finish. If Google showed “Access blocked” or “has not completed verification”, " +
                "add your Gmail as a Test user (README › YouTube setup, step 4). If it showed “Google hasn't verified this app”, " +
                "tap Advanced › Go to TS Dj."
        }
    }

    fun signIn() {
        yt.signInError = null
        val request = AuthorizationRequest.builder().setRequestedScopes(listOf(YT_SCOPE)).build()
        Identity.getAuthorizationClient(context).authorize(request)
            .addOnSuccessListener { result ->
                val pending = result.pendingIntent
                if (result.hasResolution() && pending != null) {
                    authLauncher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
                } else {
                    signedIn(result.accessToken)
                }
            }
            .addOnFailureListener { e ->
                yt.signInError = signInProblem(e)
                yt.browsing = true
            }
    }

    // ---- Deck actions -------------------------------------------------------------------------
    fun load(i: Int, item: YtItem) {
        val d = yt.decks[i]
        d.item = item
        d.error = null
        d.currentSec = 0f
        d.durationSec = 0f
        // No title yet (pasted link)? Look it up — works without signing in.
        if (item.channel == "From a link") {
            scope.launch {
                val info = YouTubeApi.titleFor(item.videoId) ?: return@launch
                if (d.item?.videoId == item.videoId) d.item = info
            }
        }
    }

    // Cue a deck's player whenever it gets a new video (from Browse, a link, or a share).
    val cued = remember { arrayOfNulls<String>(2) }
    LaunchedEffect(Unit) {
        snapshotFlow { listOf(yt.decks[0].item?.videoId, yt.decks[1].item?.videoId, yt.decks[0].ready, yt.decks[1].ready) }
            .collect {
                for (i in 0..1) {
                    val id = yt.decks[i].item?.videoId ?: continue
                    val p = players[i] ?: continue
                    if (cued[i] != id) {
                        p.cueVideo(id, yt.decks[i].currentSec)
                        cued[i] = id
                    }
                }
            }
    }

    fun playPause(i: Int) {
        val p = players[i] ?: return
        if (yt.decks[i].item == null) return
        if (yt.decks[i].playing) p.pause() else p.play()
    }

    fun fadeTo(target: Float, seconds: Float) {
        fadeJob[0]?.cancel()
        val start = yt.crossfader
        fadeJob[0] = scope.launch {
            val steps = max(1, (seconds * 60).toInt())
            for (s in 1..steps) {
                yt.crossfader = start + (target - start) * s / steps
                delay(16)
            }
        }
    }

    // The two players are created once and moved between layouts, so switching
    // between Video, Audio and Browse never reloads or interrupts them.
    val playerA = remember { movableContentOf { m: Modifier -> PlayerBox(views[0], m) } }
    val playerB = remember { movableContentOf { m: Modifier -> PlayerBox(views[1], m) } }
    val player: @Composable (Int, Modifier) -> Unit = { i, m -> if (i == 0) playerA(m) else playerB(m) }

    Column(Modifier.fillMaxSize().background(TS.Black)) {
        // Header
        Row(
            Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BackButton { vm.screen = Screen.Mixer }
            Row {
                Text("YouTube ", style = Type.display(15.sp))
                Text("Mix", style = Type.display(15.sp, TS.Orange))
            }
            Segmented(
                options = listOf("Video", "Audio"),
                selected = if (yt.view == YtView.Video && !yt.browsing) 0 else 1,
                colors = listOf(TS.Orange),
                onSelect = { yt.view = if (it == 0) YtView.Video else YtView.Audio; if (it == 0) yt.browsing = false },
                modifier = Modifier.width(150.dp),
            )
            TsButton(
                modifier = Modifier.height(36.dp),
                color = TS.Amber,
                filled = yt.browsing,
                description = "Browse YouTube",
                onClick = { yt.browsing = !yt.browsing; if (yt.browsing) loadBrowse() },
            ) { fg ->
                TsIcon(TsIcons.Search, fg, 15.dp)
                Spacer(Modifier.width(5.dp))
                Text("Browse", style = Type.ui(12.sp, 800, fg))
            }
            Spacer(Modifier.weight(1f))
            if (yt.accessToken != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(TS.Lime))
                    Spacer(Modifier.width(6.dp))
                    Text("Signed in", style = Type.ui(11.sp, 700, TS.Text2))
                }
            } else {
                TsButton(Modifier.height(36.dp), color = TS.Orange, filled = true, description = "Sign in with Google", onClick = { signIn() }) { fg ->
                    Text("Sign in with Google", style = Type.ui(12.sp, 800, fg))
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(TS.Charcoal))

        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth().weight(1f)) {
                when {
                    yt.browsing -> BrowseLayout(yt, player, ::playPause, ::load, ::signIn, ::loadBrowse)
                    yt.view == YtView.Video -> VideoLayout(yt, player, ::playPause, ::fadeTo)
                    else -> AudioLayout(yt, player, ::playPause, ::fadeTo)
                }
            }
            // Crossfader
            Row(
                Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(12.dp)).background(TS.Graphite)
                    .border(1.dp, TS.Line, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("A", style = Type.display(11.sp, TS.Orange))
                Spacer(Modifier.width(10.dp))
                Crossfader(read = { yt.crossfader }, write = { fadeJob[0]?.cancel(); yt.crossfader = it }, modifier = Modifier.weight(1f).fillMaxHeight(), fillA = true)
                Spacer(Modifier.width(10.dp))
                Text("B", style = Type.display(11.sp, TS.Cyan))
                Spacer(Modifier.width(10.dp))
                Label("CROSSFADE")
            }
        }
    }
}

private fun createPlayerView(context: Context, index: Int, yt: YouTubeMixState, players: Array<YouTubePlayer?>): YouTubePlayerView {
    val view = YouTubePlayerView(context)
    view.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    view.addYouTubePlayerListener(object : AbstractYouTubePlayerListener() {
        override fun onReady(youTubePlayer: YouTubePlayer) {
            players[index] = youTubePlayer
            val d = yt.decks[index]
            d.ready = true
            youTubePlayer.setVolume((yt.gain(index) * 100).toInt().coerceIn(0, 100))
        }

        override fun onStateChange(youTubePlayer: YouTubePlayer, state: PlayerConstants.PlayerState) {
            yt.decks[index].playing = state == PlayerConstants.PlayerState.PLAYING || state == PlayerConstants.PlayerState.BUFFERING
        }

        override fun onCurrentSecond(youTubePlayer: YouTubePlayer, second: Float) {
            yt.decks[index].currentSec = second
        }

        override fun onVideoDuration(youTubePlayer: YouTubePlayer, duration: Float) {
            yt.decks[index].durationSec = duration
        }

        override fun onError(youTubePlayer: YouTubePlayer, error: PlayerConstants.PlayerError) {
            yt.decks[index].error = when (error) {
                PlayerConstants.PlayerError.VIDEO_NOT_PLAYABLE_IN_EMBEDDED_PLAYER -> "The owner doesn't allow this video in other apps."
                PlayerConstants.PlayerError.VIDEO_NOT_FOUND -> "Video not found or private."
                PlayerConstants.PlayerError.REQUEST_MISSING_HTTP_REFERER -> "YouTube refused the player. Update the app."
                else -> "Couldn't play this video."
            }
        }
    })
    return view
}

@Composable
private fun PlayerBox(view: YouTubePlayerView, modifier: Modifier) {
    // Nothing is drawn on top of the player or clipped from it (YouTube rule).
    AndroidView(factory = { (view.parent as? ViewGroup)?.removeView(view); view }, modifier = modifier)
}

// ---------------------------------------------------------------------------------------------
// Layouts
// ---------------------------------------------------------------------------------------------

@Composable
private fun VideoLayout(
    yt: YouTubeMixState,
    player: @Composable (Int, Modifier) -> Unit,
    playPause: (Int) -> Unit,
    fadeTo: (Float, Float) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val centerW = 96.dp
        val colW = (maxWidth - centerW - 24.dp) / 2
        val controlsH = 44.dp
        val playerH = min(max(200f, colW.value * 9f / 16f), max(200f, (maxHeight - controlsH - 10.dp).value)).dp
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for (i in 0..1) {
                if (i == 1) TransitionColumn(yt, fadeTo, Modifier.width(centerW).fillMaxHeight())
                Column(Modifier.width(colW), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.fillMaxWidth().height(playerH).border(2.dp, TS.deck(i), RoundedCornerShape(10.dp)).padding(2.dp)) {
                        player(i, Modifier.fillMaxSize())
                    }
                    DeckControls(yt, i, playPause, Modifier.fillMaxWidth().height(controlsH))
                }
            }
        }
    }
}

@Composable
private fun AudioLayout(
    yt: YouTubeMixState,
    player: @Composable (Int, Modifier) -> Unit,
    playPause: (Int) -> Unit,
    fadeTo: (Float, Float) -> Unit,
) {
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        for (i in 0..1) {
            if (i == 1) TransitionColumn(yt, fadeTo, Modifier.width(96.dp).fillMaxHeight())
            val shape = RoundedCornerShape(14.dp)
            Row(
                Modifier.weight(1f).fillMaxHeight().clip(shape).background(TS.Graphite).border(1.dp, TS.Line, shape).padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val playerBox: @Composable () -> Unit = {
                    // 200 × 200 is YouTube's minimum player size, so the square never gets smaller.
                    Box(Modifier.size(204.dp).border(2.dp, TS.deck(i), RoundedCornerShape(10.dp)).padding(2.dp)) {
                        player(i, Modifier.fillMaxSize())
                    }
                }
                if (i == 0) playerBox()
                AudioInfo(yt, i, playPause, Modifier.weight(1f).fillMaxHeight())
                if (i == 1) playerBox()
            }
        }
    }
}

@Composable
private fun BrowseLayout(
    yt: YouTubeMixState,
    player: @Composable (Int, Modifier) -> Unit,
    playPause: (Int) -> Unit,
    load: (Int, YtItem) -> Unit,
    signIn: () -> Unit,
    reload: () -> Unit,
) {
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (i in 0..1) {
                    Box(Modifier.size(204.dp).border(2.dp, TS.deck(i), RoundedCornerShape(10.dp)).padding(2.dp)) {
                        player(i, Modifier.fillMaxSize())
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (i in 0..1) DeckControls(yt, i, playPause, Modifier.width(204.dp).height(44.dp), compact = true)
            }
        }
        BrowsePanel(yt, load, signIn, reload, Modifier.weight(1f).fillMaxHeight())
    }
}

// ---------------------------------------------------------------------------------------------
// Pieces
// ---------------------------------------------------------------------------------------------

@Composable
private fun TransitionColumn(yt: YouTubeMixState, fadeTo: (Float, Float) -> Unit, modifier: Modifier) {
    val out = yt.onOutput
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(TS.Graphite).border(1.dp, TS.Line, RoundedCornerShape(10.dp)).padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Label("ON OUTPUT")
            Text(if (out == 0) "A" else "B", style = Type.display(26.sp, TS.deck(out)))
            Text("Next: ${if (out == 0) "B" else "A"}", style = Type.ui(9.sp, 700, TS.Text2))
        }
        val target = if (out == 0) 1f else 0f
        TsButton(Modifier.fillMaxWidth().height(36.dp), neutral = true, description = "Cut to the other deck", onClick = { fadeTo(target, 0f) }) { fg ->
            Text("CUT", style = Type.label(11.sp, fg))
        }
        TsButton(Modifier.fillMaxWidth().height(36.dp), color = TS.Amber, filled = true, description = "Fade over 4 seconds", onClick = { fadeTo(target, 4f) }) { fg ->
            Text("FADE 4s", style = Type.label(11.sp, fg))
        }
        TsButton(Modifier.fillMaxWidth().height(36.dp), neutral = true, description = "Fade over 8 seconds", onClick = { fadeTo(target, 8f) }) { fg ->
            Text("FADE 8s", style = Type.label(11.sp, fg))
        }
    }
}

@Composable
private fun DeckControls(yt: YouTubeMixState, i: Int, playPause: (Int) -> Unit, modifier: Modifier, compact: Boolean = false) {
    val d = yt.decks[i]
    val color = TS.deck(i)
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TsButton(
            modifier = Modifier.size(40.dp),
            color = color,
            filled = d.playing,
            corner = 12.dp,
            description = if (d.playing) "Pause deck ${if (i == 0) "A" else "B"}" else "Play deck ${if (i == 0) "A" else "B"}",
            onClick = { playPause(i) },
        ) { fg -> TsIcon(if (d.playing) TsIcons.Pause else TsIcons.Play, fg, 18.dp) }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (i == 0) "A" else "B", style = Type.display(10.sp, color))
                Spacer(Modifier.width(6.dp))
                if (yt.onOutput == i) Tag("ON OUTPUT", TS.Pink, filled = true) else if (d.item != null) Tag("CUED", color)
                Spacer(Modifier.weight(1f))
                if (d.durationSec > 0) Text("-${ms((d.durationSec - d.currentSec).toDouble())}", style = Type.mono(10.sp, 500, TS.Text2))
            }
            Text(
                d.error ?: d.item?.title ?: "Browse to load a video",
                style = Type.ui(12.sp, 800, if (d.error != null) TS.Pink else TS.Text),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (!compact && d.durationSec > 0) ProgressBar(d.currentSec / d.durationSec, color)
        }
        if (!compact) {
            Row(Modifier.width(110.dp), verticalAlignment = Alignment.CenterVertically) {
                TsIcon(TsIcons.Volume, TS.Text2, 14.dp)
                Slider(
                    value = d.volume,
                    onValueChange = { d.volume = it },
                    colors = SliderDefaults.colors(thumbColor = TS.Text, activeTrackColor = color, inactiveTrackColor = TS.Line),
                    modifier = Modifier.weight(1f).semantics { contentDescription = "Volume, deck ${if (i == 0) "A" else "B"}" },
                )
            }
        }
    }
}

@Composable
private fun AudioInfo(yt: YouTubeMixState, i: Int, playPause: (Int) -> Unit, modifier: Modifier) {
    val d = yt.decks[i]
    val color = TS.deck(i)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clip(RoundedCornerShape(6.dp)).background(color).padding(horizontal = 6.dp, vertical = 2.dp)) {
                Text(if (i == 0) "A" else "B", style = Type.display(10.sp, TS.Black))
            }
            Spacer(Modifier.width(6.dp))
            if (yt.onOutput == i) Tag("ON OUTPUT", TS.Pink, filled = true) else if (d.item != null) Tag("CUED", color)
        }
        Text(d.error ?: d.item?.title ?: "Nothing loaded", style = Type.ui(14.sp, 800, if (d.error != null) TS.Pink else TS.Text), maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(d.item?.channel ?: "Tap Browse to pick a song", style = Type.ui(11.sp, 600, TS.Text2), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.weight(1f))
        Text(
            if (d.durationSec > 0) "-${ms((d.durationSec - d.currentSec).toDouble())}" else "--:--",
            style = Type.mono(22.sp, color = color),
        )
        ProgressBar(if (d.durationSec > 0) d.currentSec / d.durationSec else 0f, color)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TsButton(
                modifier = Modifier.size(40.dp), color = color, filled = d.playing, corner = 12.dp,
                description = if (d.playing) "Pause" else "Play", onClick = { playPause(i) },
            ) { fg -> TsIcon(if (d.playing) TsIcons.Pause else TsIcons.Play, fg, 18.dp) }
            Slider(
                value = d.volume,
                onValueChange = { d.volume = it },
                colors = SliderDefaults.colors(thumbColor = TS.Text, activeTrackColor = color, inactiveTrackColor = TS.Line),
                modifier = Modifier.weight(1f).semantics { contentDescription = "Volume" },
            )
        }
    }
}

@Composable
private fun BrowsePanel(
    yt: YouTubeMixState,
    load: (Int, YtItem) -> Unit,
    signIn: () -> Unit,
    reload: () -> Unit,
    modifier: Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(modifier.clip(shape).background(TS.Graphite).border(1.dp, TS.Line, shape).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Segmented(
            options = listOf("Playlists", "Liked", "Search", "Link"),
            selected = yt.tab.ordinal,
            colors = listOf(TS.Orange),
            onSelect = {
                yt.tab = BrowseTab.entries[it]
                yt.items = emptyList()
                yt.openPlaylist = null
                reload()
            },
        )
        when {
            yt.tab == BrowseTab.Link -> {
                Text("Paste a YouTube or YouTube Music link — or in the YouTube Music app tap Share › Tony Scratch Dj", style = Type.ui(12.sp, 700, TS.Text2))
                val clipboard = LocalContext.current.getSystemService(ClipboardManager::class.java)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) { TextInput(yt.linkText, { yt.linkText = it }, "https://music.youtube.com/watch?v=…") }
                    TsButton(Modifier.height(44.dp), neutral = true, description = "Paste link", onClick = {
                        val clip = clipboard?.primaryClip
                        if (clip != null && clip.itemCount > 0) yt.linkText = clip.getItemAt(0).text?.toString().orEmpty()
                    }) { fg -> Text("Paste", style = Type.ui(12.sp, 800, fg)) }
                }
                val id = YouTubeApi.videoIdFrom(yt.linkText)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (i in 0..1) {
                        TsButton(
                            Modifier.height(38.dp), color = TS.deck(i), filled = id != null,
                            description = "Load link to deck ${if (i == 0) "A" else "B"}",
                            onClick = { if (id != null) load(i, YtItem(id, "YouTube video $id", "From a link")) },
                        ) { fg -> Text("Load to ${if (i == 0) "A" else "B"}", style = Type.ui(12.sp, 800, fg)) }
                    }
                }
                if (yt.linkText.isNotBlank() && id == null) Text("That doesn't look like a YouTube link.", style = Type.ui(11.sp, 600, TS.Pink))
            }
            yt.accessToken == null -> {
                Spacer(Modifier.weight(1f))
                yt.signInError?.let {
                    Text(it, style = Type.ui(12.sp, 700, TS.Pink))
                    Text("Sign-in needs a one-time setup in Google Cloud — README › YouTube setup.", style = Type.ui(11.sp, 600, TS.Text2))
                }
                Text("Sign in with Google to see your playlists, liked songs and search.", style = Type.ui(12.sp, 600, TS.Text2))
                TsButton(Modifier.height(38.dp), color = TS.Orange, filled = true, onClick = signIn) { fg ->
                    Text("Sign in with Google", style = Type.ui(12.sp, 800, fg))
                }
                Text("No sign-in? Use the Link tab, or share a song from the YouTube Music app to Tony Scratch Dj.", style = Type.ui(11.sp, 600, TS.Muted))
                Spacer(Modifier.weight(1f))
            }
            else -> {
                if (yt.tab == BrowseTab.Search) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SearchField(yt.searchText, { yt.searchText = it }, Modifier.weight(1f), placeholder = "Search YouTube")
                        TsButton(Modifier.height(36.dp), color = TS.Orange, filled = true, onClick = reload) { fg ->
                            Text("Go", style = Type.ui(12.sp, 800, fg))
                        }
                    }
                }
                val open = yt.openPlaylist
                if (yt.tab == BrowseTab.Playlists && open != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TsButton(Modifier.height(30.dp), neutral = true, onClick = { yt.openPlaylist = null; yt.items = emptyList(); reload() }) { fg ->
                            TsIcon(TsIcons.Back, fg, 14.dp)
                            Text("Playlists", style = Type.ui(11.sp, 800, fg))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(open.title, style = Type.ui(13.sp, 800), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                yt.browseError?.let { Text(it, style = Type.ui(11.sp, 600, TS.Pink)) }
                if (yt.loading) Text("Loading…", style = Type.ui(12.sp, 600, TS.Text2))
                LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (yt.tab == BrowseTab.Playlists && open == null) {
                        items(yt.playlists, key = { it.id }) { p ->
                            BrowseRow(p.title, "${p.count} items", TsIcons.Playlist, null) {
                                yt.openPlaylist = p
                                reload()
                            }
                        }
                    } else {
                        items(yt.items, key = { it.videoId }) { item ->
                            BrowseRow(item.title, item.channel, TsIcons.Note, load = { i -> load(i, item) }, onClick = null)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowseRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    load: ((Int) -> Unit)?,
    onClick: (() -> Unit)?,
) {
    Row(
        Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(8.dp))
            .then(if (onClick != null) Modifier.semantics { role = Role.Button }.pointerInput(title) { detectTapGestures { onClick() } } else Modifier)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(TS.Charcoal), contentAlignment = Alignment.Center) {
            TsIcon(icon, TS.Text2, 17.dp)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.ui(12.sp, 800), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = Type.ui(10.sp, 600, TS.Text2), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (load != null) {
            for (i in 0..1) {
                TsButton(
                    Modifier.size(42.dp, 34.dp), color = TS.deck(i), corner = 8.dp,
                    description = "Load $title to deck ${if (i == 0) "A" else "B"}",
                    onClick = { load(i) },
                ) { fg -> Text(if (i == 0) "A" else "B", style = Type.display(11.sp, fg)) }
            }
        }
    }
}

/** Turns Google's sign-in error codes into plain words. */
private fun signInProblem(e: Exception): String {
    val code = (e as? ApiException)?.statusCode
    return when (code) {
        CommonStatusCodes.DEVELOPER_ERROR ->
            "Google doesn't recognise this app yet (error 10). In Google Cloud › Credentials, create an OAuth client of type Android " +
                "with package com.tonyscratch.tsdj and the SHA-1 from the README, copied exactly. Changes can take a few minutes."
        CommonStatusCodes.NETWORK_ERROR -> "No internet connection (error 7). Check Wi-Fi or mobile data and try again."
        CommonStatusCodes.CANCELED -> "Sign-in was cancelled."
        CommonStatusCodes.API_NOT_CONNECTED, CommonStatusCodes.SERVICE_DISABLED, CommonStatusCodes.SERVICE_VERSION_UPDATE_REQUIRED ->
            "Google Play services needs updating (error $code). Update it from the Play Store and try again."
        null -> "Google sign-in failed: ${e.message ?: "unknown reason"}"
        else -> "Google sign-in failed (error $code). ${e.message ?: ""}".trim()
    }
}
