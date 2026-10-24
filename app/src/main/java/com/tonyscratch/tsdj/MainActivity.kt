package com.tonyscratch.tsdj

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.tonyscratch.tsdj.ui.TsDjApp
import com.tonyscratch.tsdj.youtube.YouTubeApi
import com.tonyscratch.tsdj.ui.theme.TsDjTheme

class MainActivity : ComponentActivity() {

    private val vm: DjViewModel by viewModels()
    private var hasPermission by mutableStateOf(false)
    private var session: MediaSession? = null

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        if (granted) vm.refreshLibrary() else vm.toast("TS Dj can't see your music without this permission")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Full screen, screen stays on while you DJ, and use the space around the camera hole.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        hideSystemBars()

        hasPermission = checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) vm.refreshLibrary() else permissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO)

        setUpMediaKeys()
        handleShare(intent)

        setContent {
            TsDjTheme {
                TsDjApp(vm, hasPermission) { permissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    /** A song shared from the YouTube Music app (Share › Tony Scratch Dj) goes onto a YouTube deck. */
    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        val id = YouTubeApi.videoIdFrom(text)
        if (id == null) {
            vm.toast("That isn't a YouTube or YouTube Music link")
            return
        }
        vm.loadSharedYouTube(id, intent.getStringExtra(Intent.EXTRA_SUBJECT))
        intent.action = null // don't load it again if the screen rotates
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    /**
     * Media keys (from S Pen Air actions, Bluetooth headphones or a keyboard) control the decks:
     * play/pause → the focused deck, next/previous → crossfade towards B/A.
     */
    private fun setUpMediaKeys() {
        session = MediaSession(this, "TS Dj").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = vm.mediaPlayPause()
                override fun onPause() = vm.mediaPlayPause()
                override fun onSkipToNext() = vm.mediaNext()
                override fun onSkipToPrevious() = vm.mediaPrevious()
            })
            setPlaybackState(
                PlaybackState.Builder()
                    .setActions(
                        PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                            PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS
                    )
                    .setState(PlaybackState.STATE_PLAYING, 0, 1f)
                    .build()
            )
            isActive = true
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if ((event?.repeatCount ?: 0) > 0) return super.onKeyDown(keyCode, event)
        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_HEADSETHOOK -> vm.mediaPlayPause()
            KeyEvent.KEYCODE_MEDIA_NEXT -> vm.mediaNext()
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> vm.mediaPrevious()
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }

    override fun onDestroy() {
        session?.release()
        session = null
        super.onDestroy()
    }
}
