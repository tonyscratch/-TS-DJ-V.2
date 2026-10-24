package com.tonyscratch.tsdj.youtube

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tonyscratch.tsdj.audio.Mixer

/** One YouTube deck: what's loaded and what the player reports back. */
class YtDeck {
    var item by mutableStateOf<YtItem?>(null)
    var ready by mutableStateOf(false)
    var playing by mutableStateOf(false)
    var currentSec by mutableFloatStateOf(0f)
    var durationSec by mutableFloatStateOf(0f)
    var volume by mutableFloatStateOf(0.9f)
    var error by mutableStateOf<String?>(null)
}

/**
 * Everything YouTube Mix needs to remember while you move around the app:
 * the two decks, the crossfader, the view (video or audio), your sign-in and what you're browsing.
 */
class YouTubeMixState {
    val decks = listOf(YtDeck(), YtDeck())
    var view by mutableStateOf(YtView.Video)
    var browsing by mutableStateOf(false)
    /** 0 = deck A only, 1 = deck B only. */
    var crossfader by mutableFloatStateOf(0f)

    var accessToken by mutableStateOf<String?>(null)
    /** Why the last Google sign-in failed, in plain words (shown in Browse). */
    var signInError by mutableStateOf<String?>(null)
    var tab by mutableStateOf(BrowseTab.Playlists)
    var playlists by mutableStateOf<List<YtPlaylist>>(emptyList())
    var openPlaylist by mutableStateOf<YtPlaylist?>(null)
    var items by mutableStateOf<List<YtItem>>(emptyList())
    var loading by mutableStateOf(false)
    var browseError by mutableStateOf<String?>(null)
    var searchText by mutableStateOf("")
    var linkText by mutableStateOf("")

    /** Final player volume for a deck: its own volume × the crossfader. */
    fun gain(i: Int): Float {
        val x = if (i == 0) Mixer.crossGainA(crossfader) else Mixer.crossGainB(crossfader)
        return x * decks[i].volume
    }

    val onOutput: Int get() = if (crossfader < 0.5f) 0 else 1
}
