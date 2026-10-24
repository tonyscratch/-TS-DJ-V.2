package com.tonyscratch.tsdj

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tonyscratch.tsdj.audio.AudioDecoder
import com.tonyscratch.tsdj.audio.AudioEngine
import com.tonyscratch.tsdj.audio.BpmDetector
import com.tonyscratch.tsdj.audio.Deck
import com.tonyscratch.tsdj.audio.FxGroup
import com.tonyscratch.tsdj.audio.FxMode
import com.tonyscratch.tsdj.audio.FxType
import com.tonyscratch.tsdj.audio.KeyDetector
import com.tonyscratch.tsdj.audio.Keys
import com.tonyscratch.tsdj.audio.MixRecorder
import com.tonyscratch.tsdj.audio.SampleBank
import com.tonyscratch.tsdj.audio.SampleSound
import com.tonyscratch.tsdj.audio.SoundSynth
import com.tonyscratch.tsdj.audio.TrackAnalysis
import com.tonyscratch.tsdj.audio.TrackData
import com.tonyscratch.tsdj.audio.TrackInfo
import com.tonyscratch.tsdj.audio.WaveformBuilder
import com.tonyscratch.tsdj.data.AppSettings
import com.tonyscratch.tsdj.data.ButtonsStore
import com.tonyscratch.tsdj.data.Crate
import com.tonyscratch.tsdj.data.CrateStore
import com.tonyscratch.tsdj.data.CustomPad
import com.tonyscratch.tsdj.data.HistoryEntry
import com.tonyscratch.tsdj.data.HistoryStore
import com.tonyscratch.tsdj.data.LibraryRepository
import com.tonyscratch.tsdj.data.LocalTrack
import com.tonyscratch.tsdj.data.PenSettings
import com.tonyscratch.tsdj.data.Recording
import com.tonyscratch.tsdj.data.RecordingsRepository
import com.tonyscratch.tsdj.data.SettingsStore
import com.tonyscratch.tsdj.data.Shortcut
import com.tonyscratch.tsdj.data.TrackMemoryStore
import com.tonyscratch.tsdj.youtube.YouTubeApi
import com.tonyscratch.tsdj.youtube.YouTubeMixState
import com.tonyscratch.tsdj.youtube.YtItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

enum class Screen { Mixer, Library, YouTube, SPen, Recordings, Settings }

/** The pages of the pad section on each deck. */
enum class PadPage(val label: String) {
    CUES("HOT CUES"),
    LOOP("LOOP"),
    ROLL("ROLL"),
    JUMP("BEAT JUMP"),
    FX("EFFECTS"),
    SAMPLER("SAMPLER"),
    KEY("KEY"),
    MY("MY PADS"),
}

/** Shown on a deck while a track is being read and analysed. */
data class DeckLoad(val title: String, val progress: Float, val error: String?)

/**
 * The app's brain: owns the audio engine, library, sampler, recordings, settings, shortcuts and
 * automix, and turns button presses into engine actions. Survives screen changes.
 */
class DjViewModel(app: Application) : AndroidViewModel(app) {

    val engine = AudioEngine(app)
    val mixer get() = engine.mixer
    val recorder = MixRecorder(app, engine.sampleRate)

    private val library = LibraryRepository(app)
    private val memory = TrackMemoryStore(app)
    private val crateStore = CrateStore(app)
    private val settingsStore = SettingsStore(app)
    private val historyStore = HistoryStore(app)
    private val buttonsStore = ButtonsStore(app)
    private val recordingsRepo = RecordingsRepository(app)

    val youtube = YouTubeMixState()

    var screen by mutableStateOf(Screen.Mixer)

    var settings by mutableStateOf(settingsStore.loadApp())
        private set

    // ---- Library ---------------------------------------------------------------------------
    var tracks by mutableStateOf<List<LocalTrack>>(emptyList())
        private set
    var libraryLoaded by mutableStateOf(false)
        private set
    val analyses = mutableStateMapOf<String, TrackAnalysis>()
    var crates by mutableStateOf<List<Crate>>(emptyList())
        private set
    var analyzeAllProgress by mutableStateOf<Float?>(null)
        private set
    private var analyzeJob: Job? = null
    var history by mutableStateOf<List<HistoryEntry>>(emptyList())
        private set

    // ---- Decks -----------------------------------------------------------------------------
    val deckLoad = mutableStateListOf<DeckLoad?>(null, null)
    val deckVersion = mutableStateListOf(0, 0)
    private val loadJobs = arrayOfNulls<Job>(2)
    private val playAfterLoad = BooleanArray(2)
    var lastTouchedDeck by mutableIntStateOf(0)

    // ---- Pads, effects, sampler --------------------------------------------------------------
    val padPage = mutableStateListOf(PadPage.CUES, PadPage.CUES)
    val fxGroup = mutableStateListOf(FxGroup.ECHO, FxGroup.ECHO)
    val fxSelected = mutableStateListOf(FxType.ECHO, FxType.ECHO)
    var banks by mutableStateOf<List<SampleBank>>(emptyList())
        private set
    var samplerBank by mutableIntStateOf(0)
    var samplerVersion by mutableIntStateOf(0)
        private set
    private var customPads: List<List<CustomPad?>> = emptyList()

    // ---- Shortcuts ---------------------------------------------------------------------------
    var shortcuts by mutableStateOf<List<Shortcut>>(emptyList())
        private set
    var myPads by mutableStateOf<List<List<String?>>>(List(2) { List(8) { null } })
        private set

    // ---- Automix -----------------------------------------------------------------------------
    var automix by mutableStateOf(false)
        private set
    var queue by mutableStateOf<List<TrackInfo>>(emptyList())
        private set
    private val automixPrepared = arrayOfNulls<String>(2)
    private var automixFade: Job? = null

    // ---- Recording ---------------------------------------------------------------------------
    var recording by mutableStateOf(false)
        private set
    var recordings by mutableStateOf<List<Recording>>(emptyList())
        private set

    // ---- S Pen -------------------------------------------------------------------------------
    var pen by mutableStateOf(settingsStore.loadPen())
        private set
    var penSeen by mutableStateOf(false)
    val liveGrip = arrayOf(mutableFloatStateOf(-1f), mutableFloatStateOf(-1f))

    var message by mutableStateOf<String?>(null)

    private var fadeJob: Job? = null
    private val playSeconds = DoubleArray(2)
    private val logged = BooleanArray(2)

    init {
        mixer.tap = recorder.tap
        applySettings(settings)
        for (d in mixer.decks) d.keylock = settings.keylockDefault
        engine.start()
        crates = crateStore.load()
        val saved = buttonsStore.load()
        shortcuts = saved.shortcuts
        myPads = saved.myPads
        customPads = saved.customPads
        viewModelScope.launch(Dispatchers.IO) {
            val h = historyStore.load()
            withContext(Dispatchers.Main) { history = h }
        }
        buildBanks()
        viewModelScope.launch {
            while (isActive) {
                delay(250)
                monitorTick()
            }
        }
    }

    override fun onCleared() {
        if (recording) recorder.stop()
        engine.stop()
    }

    fun deck(i: Int): Deck = mixer.decks[i]
    fun other(i: Int): Deck = mixer.decks[1 - i]

    fun toast(text: String) {
        message = text
        viewModelScope.launch {
            delay(3500)
            if (message == text) message = null
        }
    }

    private fun bump(i: Int) { deckVersion[i] = deckVersion[i] + 1 }

    // =========================================================================================
    // Settings
    // =========================================================================================

    fun updateSettings(s: AppSettings) {
        settings = s
        applySettings(s)
        settingsStore.saveApp(s)
    }

    private fun applySettings(s: AppSettings) {
        for (d in mixer.decks) d.quantize = s.quantize
        mixer.splitCue = s.splitCue
    }

    fun updatePen(s: PenSettings) {
        pen = s
        settingsStore.savePen(s)
    }

    // =========================================================================================
    // Library
    // =========================================================================================

    fun refreshLibrary() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = try { library.loadTracks() } catch (e: SecurityException) { emptyList() }
            val found = HashMap<String, TrackAnalysis>()
            for (t in list) memory.get(t.info.id)?.analysis?.let { found[t.info.id] = it }
            withContext(Dispatchers.Main) {
                tracks = list
                analyses.putAll(found)
                libraryLoaded = true
            }
        }
    }

    fun loadToDeck(index: Int, track: TrackInfo, autoPlay: Boolean = false) {
        if (deck(index).playing && !autoPlay && !automix) {
            toast("Deck ${deckName(index)} is playing — pause it first")
            return
        }
        loadJobs[index]?.cancel()
        deckLoad[index] = DeckLoad(track.title, 0f, null)
        playAfterLoad[index] = autoPlay
        lastTouchedDeck = index
        val sr = engine.sampleRate
        val useAutoGain = settings.autoGain
        loadJobs[index] = viewModelScope.launch(Dispatchers.Default) {
            try {
                val pcm = AudioDecoder.decode(getApplication<Application>(), Uri.parse(track.uri), sr) { p ->
                    viewModelScope.launch(Dispatchers.Main) { if (deckLoad[index]?.error == null) deckLoad[index] = DeckLoad(track.title, p, null) }
                }
                val wave = WaveformBuilder.build(pcm)
                val remembered = memory.get(track.id)
                var analysis = remembered?.analysis
                if (analysis == null) {
                    withContext(Dispatchers.Main) { deckLoad[index] = DeckLoad(track.title, 0.94f, null) }
                    analysis = analyse(pcm, sr)
                    if (analysis != null) {
                        memory.putAnalysis(track.id, analysis)
                        val a = analysis
                        withContext(Dispatchers.Main) { analyses[track.id] = a }
                    }
                }
                val gain = if (useAutoGain) TrackData.measureGain(pcm) else 1f
                val data = TrackData(pcm, sr, wave, gain)
                val cues = remembered?.hotCuesSec?.map { if (it < 0) -1.0 else it * sr }?.toDoubleArray()
                val finalAnalysis = analysis
                withContext(Dispatchers.Main) {
                    val d = deck(index)
                    d.load(data, track, finalAnalysis, cues, resetPitch = !settings.keepTempoOnLoad)
                    deckLoad[index] = null
                    playSeconds[index] = 0.0
                    logged[index] = false
                    if (settings.autoKeyMatch && other(index).playing) {
                        val s = Keys.bestShift(d.currentKey(), other(index).currentKey())
                        if (s != null && abs(s) <= 1) d.keyShift = s
                    }
                    if (playAfterLoad[index]) d.setPlaying(true)
                    bump(index)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: OutOfMemoryError) {
                withContext(Dispatchers.Main) { deckLoad[index] = DeckLoad(track.title, 0f, "Track too long to load") }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    deckLoad[index] = DeckLoad(track.title, 0f, e.message ?: "Couldn't load this track")
                }
            }
        }
    }

    private fun analyse(pcm: ShortArray, sr: Int): TrackAnalysis? {
        val bpm = BpmDetector.detect(pcm, sr) ?: return null
        val key = KeyDetector.detect(pcm, sr)
        return TrackAnalysis(bpm.first, bpm.second, key)
    }

    fun analyzeAll() {
        if (analyzeJob?.isActive == true) {
            analyzeJob?.cancel()
            analyzeAllProgress = null
            return
        }
        val todo = tracks.filter { analyses[it.info.id] == null }
        if (todo.isEmpty()) {
            toast("Every track is already analysed")
            return
        }
        analyzeJob = viewModelScope.launch(Dispatchers.Default) {
            todo.forEachIndexed { i, t ->
                withContext(Dispatchers.Main) { analyzeAllProgress = i.toFloat() / todo.size }
                try {
                    val pcm = AudioDecoder.decode(getApplication<Application>(), Uri.parse(t.info.uri), 22050)
                    val a = analyse(pcm, 22050)
                    if (a != null) {
                        memory.putAnalysis(t.info.id, a)
                        withContext(Dispatchers.Main) { analyses[t.info.id] = a }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                }
            }
            withContext(Dispatchers.Main) {
                analyzeAllProgress = null
                toast("Analysis finished")
            }
        }
    }

    fun fixBpm(index: Int, factor: Double) {
        val d = deck(index)
        val a = d.analysis ?: return
        saveAnalysis(index, a.copy(bpm = a.bpm * factor))
    }

    fun setGridHere(index: Int) {
        val d = deck(index)
        d.setGridHere()
        d.analysis?.let { saveAnalysis(index, it) }
    }

    private fun saveAnalysis(index: Int, a: TrackAnalysis) {
        val d = deck(index)
        d.analysis = a
        d.info?.id?.let { id ->
            analyses[id] = a
            viewModelScope.launch(Dispatchers.IO) { memory.putAnalysis(id, a) }
        }
        bump(index)
    }

    fun createCrate(name: String) {
        val n = name.trim().ifEmpty { return }
        crates = crates + Crate(n, crates.size % 5, emptyList())
        saveCrates()
    }

    fun deleteCrate(index: Int) {
        crates = crates.filterIndexed { i, _ -> i != index }
        saveCrates()
    }

    fun toggleInCrate(crateIndex: Int, trackId: String) {
        crates = crates.mapIndexed { i, c ->
            if (i != crateIndex) c
            else if (trackId in c.trackIds) c.copy(trackIds = c.trackIds - trackId)
            else c.copy(trackIds = c.trackIds + trackId)
        }
        saveCrates()
    }

    private fun saveCrates() {
        val snapshot = crates
        viewModelScope.launch(Dispatchers.IO) { crateStore.save(snapshot) }
    }

    // =========================================================================================
    // Deck actions
    // =========================================================================================

    fun deckName(i: Int) = if (i == 0) "A" else "B"

    fun touch(index: Int) { lastTouchedDeck = index }

    fun togglePlay(index: Int) { touch(index); deck(index).togglePlay() }
    fun cuePress(index: Int) { touch(index); deck(index).cuePress() }
    fun cueRelease(index: Int) { deck(index).cueRelease() }

    fun hotCue(index: Int, slot: Int) {
        touch(index)
        val d = deck(index)
        val wasEmpty = d.hotCues[slot] < 0
        d.hotCue(slot)
        if (wasEmpty) saveCues(index)
        bump(index)
    }

    fun clearHotCue(index: Int, slot: Int) {
        deck(index).clearHotCue(slot)
        saveCues(index)
        bump(index)
    }

    private fun saveCues(index: Int) {
        val d = deck(index)
        val id = d.info?.id ?: return
        val sr = engine.sampleRate.toDouble()
        val cues = d.hotCues.map { if (it < 0) -1.0 else it / sr }
        viewModelScope.launch(Dispatchers.IO) { memory.putHotCues(id, cues) }
    }

    fun sync(index: Int) {
        touch(index)
        val d = deck(index)
        if (d.synced) d.synced = false
        else if (!d.syncTo(other(index))) toast("Sync needs a BPM on both decks")
        bump(index)
    }

    fun autoLoop(index: Int, beats: Double) { touch(index); deck(index).autoLoop(beats); bump(index) }
    fun loopHalf(index: Int) { deck(index).loopHalf(); bump(index) }
    fun loopDouble(index: Int) { deck(index).loopDouble(); bump(index) }
    fun loopIn(index: Int) { deck(index).markLoopIn(); bump(index) }
    fun loopOut(index: Int) { deck(index).markLoopOut(); bump(index) }
    fun loopExit(index: Int) { deck(index).exitLoop(); bump(index) }
    fun reloop(index: Int) { deck(index).reloop(); bump(index) }
    fun beatJump(index: Int, beats: Double) { touch(index); deck(index).beatJump(beats) }
    fun rollStart(index: Int, beats: Double) { touch(index); deck(index).startRoll(beats) }
    fun rollStop(index: Int) { deck(index).stopRoll() }

    fun setPitch(index: Int, value: Double) {
        val d = deck(index)
        d.pitch = value.coerceIn(-d.pitchRange, d.pitchRange)
        if (d.synced) d.synced = false
    }

    fun resetPitch(index: Int) { deck(index).pitch = 0.0; deck(index).synced = false }

    fun cyclePitchRange(index: Int) {
        val d = deck(index)
        d.pitchRange = when (d.pitchRange) { 0.08 -> 0.16; 0.16 -> 0.5; else -> 0.08 }
        d.pitch = d.pitch.coerceIn(-d.pitchRange, d.pitchRange)
        bump(index)
    }

    fun toggleKeylock(index: Int) { deck(index).keylock = !deck(index).keylock; bump(index) }
    fun toggleSlip(index: Int) { deck(index).slip = !deck(index).slip; bump(index) }
    fun toggleQuantize() = updateSettings(settings.copy(quantize = !settings.quantize))

    fun keyShift(index: Int, delta: Int) {
        val d = deck(index)
        d.keyShift = (d.keyShift + delta).coerceIn(-12, 12)
        bump(index)
    }

    fun keyReset(index: Int) { deck(index).keyShift = 0; bump(index) }

    /** Move this deck's key the smallest amount so it mixes with the other deck. */
    fun keyMatch(index: Int) {
        val d = deck(index)
        val base = d.analysis?.key
        val target = other(index).currentKey()
        if (base == null || target == null) {
            toast("Key match needs a key on both decks")
            return
        }
        val pitchSemis = if (d.keylock) 0 else Keys.semitonesFromRate(1.0 + d.pitch)
        val s = Keys.bestShift(Keys.shift(base, pitchSemis), target)
        if (s == null) toast("Couldn't match these keys") else d.keyShift = s
        bump(index)
    }

    fun togglePfl(index: Int) {
        deck(index).pfl = !deck(index).pfl
        if (!settings.splitCue && deck(index).pfl) toast("Turn on split output in Settings to hear headphone cue")
        bump(index)
    }

    fun eject(index: Int) {
        val d = deck(index)
        if (d.playing) {
            toast("Pause deck ${deckName(index)} before ejecting")
            return
        }
        d.eject()
        bump(index)
    }

    fun toggleKill(index: Int, low: Boolean) {
        val d = deck(index)
        if (low) d.eqLow = if (d.eqLow < 0.05f) 0.5f else 0f
        else d.eqHigh = if (d.eqHigh < 0.05f) 0.5f else 0f
    }

    fun cyclePadPage(index: Int, step: Int) {
        val pages = PadPage.entries
        padPage[index] = pages[Math.floorMod(padPage[index].ordinal + step, pages.size)]
    }

    // =========================================================================================
    // Effects
    // =========================================================================================

    fun selectFx(index: Int, t: FxType) {
        fxSelected[index] = t
        fxGroup[index] = t.group
    }

    /** A finger (or the pen) went down on an effect pad. */
    fun fxDown(index: Int, t: FxType) {
        touch(index)
        selectFx(index, t)
        val d = deck(index)
        val s = d.fx.state(t)
        when (t) {
            FxType.BRAKE -> d.brake(FxType.deckLengthSec(s.amount), s.param >= 0.5f)
            FxType.BACKSPIN -> d.backspin(FxType.deckLengthSec(s.amount), s.param >= 0.5f)
            FxType.REVERSE -> {
                d.reverseSlip = s.param >= 0.5f
                d.reverseSpeed = 0.5 + 0.5 * s.amount
                d.reverse = !d.reverse
                s.on = d.reverse
            }
            FxType.CENSOR -> {
                d.reverseSpeed = 0.5 + 0.5 * s.amount
                d.censor = true
                s.on = true
            }
            else -> when (t.mode) {
                FxMode.TOGGLE -> d.fx.setOn(t, !s.on)
                FxMode.HOLD -> d.fx.setOn(t, true)
                FxMode.TRIGGER -> {}
            }
        }
    }

    /** The finger came up from an effect pad (only matters for hold effects). */
    fun fxUp(index: Int, t: FxType) {
        val d = deck(index)
        when {
            t == FxType.CENSOR -> { d.censor = false; d.fx.state(t).on = false }
            t.mode == FxMode.HOLD -> d.fx.setOn(t, false)
        }
    }

    fun allFxOff(index: Int) {
        val d = deck(index)
        d.fx.allOff()
        d.reverse = false
        d.censor = false
    }

    // =========================================================================================
    // Sampler
    // =========================================================================================

    private fun buildBanks() {
        val sr = engine.sampleRate
        viewModelScope.launch(Dispatchers.Default) {
            val builtIn = SoundSynth.builtInBanks(sr)
            val custom = List(ButtonsStore.CUSTOM_BANKS) { b -> SampleBank("MY SOUNDS ${b + 1}", true, arrayOfNulls(8)) }
            withContext(Dispatchers.Main) {
                banks = builtIn + custom
                samplerVersion++
            }
            customPads.forEachIndexed { b, pads ->
                pads.forEachIndexed { p, pad -> if (pad != null) loadCustomSound(b, p, pad) }
            }
        }
    }

    private fun customBankIndex(b: Int): Int = banks.size - ButtonsStore.CUSTOM_BANKS + b

    private fun loadCustomSound(customBank: Int, pad: Int, cp: CustomPad) {
        val sr = engine.sampleRate
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val pcm = AudioDecoder.decode(getApplication<Application>(), Uri.parse(cp.uri), sr)
                val frames = minOf(pcm.size / 2, sr * 30) // up to 30 seconds
                val l = FloatArray(frames) { pcm[2 * it] / 32768f }
                val r = FloatArray(frames) { pcm[2 * it + 1] / 32768f }
                val sound = SampleSound(cp.name, l, r)
                withContext(Dispatchers.Main) {
                    banks.getOrNull(customBankIndex(customBank))?.sounds?.set(pad, sound)
                    samplerVersion++
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                withContext(Dispatchers.Main) { toast("Couldn't load sound “${cp.name}”") }
            }
        }
    }

    fun samplerTrigger(bank: Int, pad: Int) {
        val s = banks.getOrNull(bank)?.sounds?.getOrNull(pad) ?: return
        mixer.sampler.play(s)
    }

    fun samplerStopAll() = mixer.sampler.stopAll()

    fun assignCustomPad(bankIndex: Int, pad: Int, uri: Uri, name: String) {
        val b = bankIndex - (banks.size - ButtonsStore.CUSTOM_BANKS)
        if (b !in 0 until ButtonsStore.CUSTOM_BANKS) return
        val cp = CustomPad(uri.toString(), name.substringBeforeLast('.').take(24))
        customPads = customPads.mapIndexed { i, list -> if (i == b) list.mapIndexed { j, x -> if (j == pad) cp else x } else list }
        saveButtons()
        loadCustomSound(b, pad, cp)
    }

    fun clearCustomPad(bankIndex: Int, pad: Int) {
        val b = bankIndex - (banks.size - ButtonsStore.CUSTOM_BANKS)
        if (b !in 0 until ButtonsStore.CUSTOM_BANKS) return
        customPads = customPads.mapIndexed { i, list -> if (i == b) list.mapIndexed { j, x -> if (j == pad) null else x } else list }
        banks.getOrNull(bankIndex)?.sounds?.set(pad, null)
        samplerVersion++
        saveButtons()
    }

    // =========================================================================================
    // Shortcuts and MY pads
    // =========================================================================================

    fun addShortcut(s: Shortcut) {
        shortcuts = shortcuts + s
        saveButtons()
    }

    fun removeShortcut(index: Int) {
        shortcuts = shortcuts.filterIndexed { i, _ -> i != index }
        saveButtons()
    }

    fun moveShortcut(index: Int, step: Int) {
        val j = index + step
        if (index !in shortcuts.indices || j !in shortcuts.indices) return
        val list = shortcuts.toMutableList()
        val x = list[index]; list[index] = list[j]; list[j] = x
        shortcuts = list
        saveButtons()
    }

    fun setMyPad(deckIndex: Int, slot: Int, code: String?) {
        myPads = myPads.mapIndexed { d, list -> if (d == deckIndex) list.mapIndexed { i, c -> if (i == slot) code else c } else list }
        saveButtons()
    }

    private fun saveButtons() {
        val snap = ButtonsStore.Saved(shortcuts, myPads, customPads)
        viewModelScope.launch(Dispatchers.IO) { buttonsStore.save(snap) }
    }

    private fun decksFor(sel: Int): List<Int> = when (sel) {
        0 -> listOf(0)
        1 -> listOf(1)
        2 -> listOf(0, 1)
        else -> emptyList()
    }

    /** A shortcut or MY pad was pressed. */
    fun actionDown(code: String, deckSel: Int) {
        val arg = code.substringAfter(':', "")
        val decks = decksFor(deckSel)
        when {
            code == "play" -> decks.forEach { togglePlay(it) }
            code == "cue" -> decks.forEach { d -> deck(d).setPlaying(false); deck(d).seek(deck(d).cuePoint) }
            code == "sync" -> decks.forEach { sync(it) }
            code == "keylock" -> decks.forEach { toggleKeylock(it) }
            code == "slip" -> decks.forEach { toggleSlip(it) }
            code == "keymatch" -> decks.forEach { keyMatch(it) }
            code.startsWith("key:") -> decks.forEach { keyShift(it, arg.toIntOrNull() ?: 0) }
            code == "keyreset" -> decks.forEach { keyReset(it) }
            code == "pitchreset" -> decks.forEach { resetPitch(it) }
            code == "brake" -> decks.forEach { fxDown(it, FxType.BRAKE) }
            code == "backspin" -> decks.forEach { fxDown(it, FxType.BACKSPIN) }
            code == "reverse" -> decks.forEach { fxDown(it, FxType.REVERSE) }
            code == "killlow" -> decks.forEach { toggleKill(it, true) }
            code == "killhigh" -> decks.forEach { toggleKill(it, false) }
            code == "filterreset" -> decks.forEach { deck(it).filter = 0.5f }
            code == "pfl" -> decks.forEach { togglePfl(it) }
            code == "eject" -> decks.forEach { eject(it) }
            code.startsWith("loop:") -> decks.forEach { autoLoop(it, arg.toDoubleOrNull() ?: 4.0) }
            code == "loopexit" -> decks.forEach { loopExit(it) }
            code == "reloop" -> decks.forEach { reloop(it) }
            code.startsWith("jump:") -> decks.forEach { beatJump(it, arg.toDoubleOrNull() ?: 0.0) }
            code.startsWith("hotcue:") -> decks.forEach { hotCue(it, ((arg.toIntOrNull() ?: 1) - 1).coerceIn(0, 7)) }
            code.startsWith("fx:") -> runCatching { FxType.valueOf(arg) }.getOrNull()?.let { t -> decks.forEach { fxDown(it, t) } }
            code == "fxoff" -> decks.forEach { allFxOff(it) }
            code == "xf:a" -> fadeCrossfader(0f, 0.3f)
            code == "xf:mid" -> fadeCrossfader(0.5f, 0.3f)
            code == "xf:b" -> fadeCrossfader(1f, 0.3f)
            code == "fade" -> fadeCrossfader(if (mixer.crossfader < 0.5f) 1f else 0f, 8f)
            code == "automix" -> toggleAutomix()
            code == "rec" -> toggleRecording()
            code == "samplestop" -> samplerStopAll()
            code.startsWith("sample:") -> {
                val parts = code.split(':')
                samplerTrigger(parts.getOrNull(1)?.toIntOrNull() ?: 0, parts.getOrNull(2)?.toIntOrNull() ?: 0)
            }
        }
    }

    /** The finger came up from a shortcut (matters for hold effects like Roll and Censor). */
    fun actionUp(code: String, deckSel: Int) {
        if (code.startsWith("fx:")) {
            val t = runCatching { FxType.valueOf(code.substringAfter(':')) }.getOrNull() ?: return
            if (t.mode == FxMode.HOLD) decksFor(deckSel).forEach { fxUp(it, t) }
        }
    }

    // =========================================================================================
    // Mixer, automix, history
    // =========================================================================================

    fun fadeCrossfader(target: Float, seconds: Float) {
        fadeJob?.cancel()
        val start = mixer.crossfader
        fadeJob = viewModelScope.launch {
            val steps = (seconds * 60).toInt().coerceAtLeast(1)
            for (s in 1..steps) {
                mixer.crossfader = start + (target - start) * s / steps
                delay(16)
            }
        }
    }

    fun focusDeck(): Int = if (pen.focusDeck >= 0) pen.focusDeck else lastTouchedDeck
    fun mediaPlayPause() = togglePlay(focusDeck())
    fun mediaNext() = fadeCrossfader((mixer.crossfader + 0.5f).coerceAtMost(1f), 1f)
    fun mediaPrevious() = fadeCrossfader((mixer.crossfader - 0.5f).coerceAtLeast(0f), 1f)

    fun toggleAutomix() {
        automix = !automix
        if (automix) {
            if (queue.isEmpty() && !deck(0).playing && !deck(1).playing) {
                toast("Add tracks to the Automix list in the Library first")
                automix = false
                return
            }
            toast("Automix on: ${queue.size} track${if (queue.size == 1) "" else "s"} queued")
        } else {
            automixFade?.cancel()
        }
    }

    fun queueAdd(t: TrackInfo, next: Boolean = false) {
        queue = if (next) listOf(t) + queue else queue + t
        toast(if (next) "Plays next: ${t.title}" else "Added to Automix: ${t.title}")
    }

    fun queueRemove(index: Int) { queue = queue.filterIndexed { i, _ -> i != index } }

    fun queueMove(index: Int, step: Int) {
        val j = index + step
        if (index !in queue.indices || j !in queue.indices) return
        val list = queue.toMutableList()
        val x = list[index]; list[index] = list[j]; list[j] = x
        queue = list
    }

    fun queueClear() { queue = emptyList() }

    private fun monitorTick() {
        // History: a track counts as played after 45 seconds (like VirtualDJ).
        for (i in 0..1) {
            val d = deck(i)
            if (d.playing && d.info != null) {
                playSeconds[i] += 0.25
                if (playSeconds[i] >= 45 && !logged[i]) {
                    logged[i] = true
                    val info = d.info!!
                    history = history + HistoryEntry(info.id, info.title, info.artist, System.currentTimeMillis())
                    val snap = history
                    viewModelScope.launch(Dispatchers.IO) { historyStore.save(snap) }
                }
            }
        }
        if (automix) automixTick()
    }

    private fun automixTick() {
        if (automixFade?.isActive == true) return
        val a = deck(0)
        val b = deck(1)
        val cur = when {
            a.playing && b.playing -> if (mixer.crossfader <= 0.5f) 0 else 1
            a.playing -> 0
            b.playing -> 1
            else -> -1
        }
        if (cur < 0) {
            if (deckLoad[0] != null || deckLoad[1] != null) return
            val next = queue.firstOrNull()
            if (next == null) {
                automix = false
                toast("Automix finished")
                return
            }
            queue = queue.drop(1)
            mixer.crossfader = 0f
            automixPrepared[0] = next.id
            loadToDeck(0, next, autoPlay = true)
            return
        }
        val nxt = 1 - cur
        val cd = deck(cur)
        val nd = deck(nxt)
        if (deckLoad[nxt] != null) return
        val prepared = nd.track != null && nd.info?.id == automixPrepared[nxt] && !nd.playing
        if (!prepared) {
            val t = queue.firstOrNull() ?: return
            if (nd.playing) return
            queue = queue.drop(1)
            automixPrepared[nxt] = t.id
            loadToDeck(nxt, t)
            return
        }
        val secs = settings.automixSeconds
        if (cd.remainingSec() <= secs + 0.3) {
            val ab = cd.effectiveBpm()
            val bb = nd.analysis?.bpm
            if (ab != null && bb != null) {
                val ratio = listOf(1.0, 2.0, 0.5).minOf { abs(ab * it / bb - 1.0) }
                if (ratio <= 0.08) nd.syncTo(cd)
            }
            nd.setPlaying(true)
            automixPrepared[nxt] = null
            val target = if (nxt == 0) 0f else 1f
            val start = mixer.crossfader
            automixFade = viewModelScope.launch {
                val steps = (secs * 30).toInt().coerceAtLeast(1)
                for (s in 1..steps) {
                    mixer.crossfader = start + (target - start) * s / steps
                    delay(33)
                }
                cd.setPlaying(false)
            }
        }
    }

    // =========================================================================================
    // YouTube links shared from the YouTube Music app
    // =========================================================================================

    /** Load a video someone shared to the app onto a free YouTube deck and open YouTube Mix. */
    fun loadSharedYouTube(videoId: String, subject: String?) {
        val yt = youtube
        val d = when {
            yt.decks[0].item == null -> 0
            yt.decks[1].item == null -> 1
            else -> 1 - yt.onOutput
        }
        val deckState = yt.decks[d]
        deckState.item = YtItem(videoId, subject?.takeIf { it.isNotBlank() } ?: "Loading title…", "YouTube")
        deckState.error = null
        deckState.currentSec = 0f
        deckState.durationSec = 0f
        yt.browsing = false
        screen = Screen.YouTube
        toast("Loaded on YouTube deck ${deckName(d)}")
        viewModelScope.launch {
            val info = YouTubeApi.titleFor(videoId) ?: return@launch
            if (deckState.item?.videoId == videoId) deckState.item = info
        }
    }

    // =========================================================================================
    // Recording
    // =========================================================================================

    fun toggleRecording() {
        if (!recording) {
            val free = try { recorder.freeMinutes() } catch (_: Exception) { Long.MAX_VALUE }
            if (free < 5) {
                toast("Not enough free space to record")
                return
            }
            if (recorder.start()) {
                recording = true
                if (free < 60) toast("Space left for about $free minutes of recording")
            } else {
                toast("Couldn't start recording")
            }
        } else {
            recording = false
            viewModelScope.launch(Dispatchers.IO) {
                recorder.stop()
                withContext(Dispatchers.Main) {
                    toast("Mix saved to Music/TS Dj")
                    refreshRecordings()
                }
            }
        }
    }

    fun refreshRecordings() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = try { recordingsRepo.list() } catch (_: Exception) { emptyList() }
            withContext(Dispatchers.Main) { recordings = list }
        }
    }

    fun renameRecording(r: Recording, name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = recordingsRepo.rename(r.uri, name)
            withContext(Dispatchers.Main) {
                if (!ok) toast("Couldn't rename this mix")
                refreshRecordings()
            }
        }
    }

    fun deleteRecording(r: Recording) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = recordingsRepo.delete(r.uri)
            withContext(Dispatchers.Main) {
                if (!ok) toast("Couldn't delete this mix")
                refreshRecordings()
            }
        }
    }
}
