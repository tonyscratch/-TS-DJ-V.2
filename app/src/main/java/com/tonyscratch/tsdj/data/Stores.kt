package com.tonyscratch.tsdj.data

import android.content.Context
import com.tonyscratch.tsdj.audio.TrackAnalysis
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** What TS Dj remembers about each track: BPM, first beat, key and hot cues (in seconds). */
data class TrackMemory(
    val analysis: TrackAnalysis? = null,
    val hotCuesSec: List<Double> = List(8) { -1.0 },
)

/** Saves track analysis and hot cues so each track is only analysed once. */
class TrackMemoryStore(context: Context) {
    private val file = File(context.filesDir, "tracks.json")
    private val map = HashMap<String, TrackMemory>()

    init { load() }

    @Synchronized fun get(id: String): TrackMemory? = map[id]

    @Synchronized fun putAnalysis(id: String, analysis: TrackAnalysis) {
        val old = map[id] ?: TrackMemory()
        map[id] = old.copy(analysis = analysis)
        save()
    }

    @Synchronized fun putHotCues(id: String, cuesSec: List<Double>) {
        val old = map[id] ?: TrackMemory()
        map[id] = old.copy(hotCuesSec = cuesSec)
        save()
    }

    @Synchronized private fun load() {
        if (!file.exists()) return
        try {
            val root = JSONObject(file.readText())
            for (id in root.keys()) {
                val o = root.getJSONObject(id)
                val analysis = if (o.has("bpm")) TrackAnalysis(
                    bpm = o.getDouble("bpm"),
                    firstBeatSec = o.optDouble("firstBeat", 0.0),
                    key = o.optString("key").takeIf { it.isNotEmpty() },
                ) else null
                val cues = o.optJSONArray("cues")?.let { arr -> List(8) { i -> arr.optDouble(i, -1.0) } }
                    ?: List(8) { -1.0 }
                map[id] = TrackMemory(analysis, cues)
            }
        } catch (_: Exception) {
            // A damaged file just means tracks get analysed again.
        }
    }

    @Synchronized private fun save() {
        val root = JSONObject()
        for ((id, m) in map) {
            val o = JSONObject()
            m.analysis?.let {
                o.put("bpm", it.bpm)
                o.put("firstBeat", it.firstBeatSec)
                o.put("key", it.key ?: "")
            }
            o.put("cues", JSONArray(m.hotCuesSec))
            root.put(id, o)
        }
        writeAtomic(file, root.toString())
    }
}

private fun writeAtomic(file: File, text: String) {
    val tmp = File(file.parentFile, file.name + ".tmp")
    tmp.writeText(text)
    if (!tmp.renameTo(file)) file.writeText(text)
}

private fun readOrNull(file: File): String? = try { if (file.exists()) file.readText() else null } catch (_: Exception) { null }

/** A named collection of tracks, like a record crate. */
data class Crate(val name: String, val colorIndex: Int, val trackIds: List<String>)

class CrateStore(context: Context) {
    private val file = File(context.filesDir, "crates.json")

    @Synchronized fun load(): List<Crate> {
        val text = readOrNull(file) ?: return emptyList()
        return try {
            val arr = JSONArray(text)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                val ids = o.getJSONArray("tracks")
                Crate(o.getString("name"), o.optInt("color", i), List(ids.length()) { ids.getString(it) })
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized fun save(crates: List<Crate>) {
        val arr = JSONArray()
        for (c in crates) {
            arr.put(JSONObject().apply {
                put("name", c.name)
                put("color", c.colorIndex)
                put("tracks", JSONArray(c.trackIds))
            })
        }
        writeAtomic(file, arr.toString())
    }
}

/** A track you played (for the History list). */
data class HistoryEntry(val trackId: String, val title: String, val artist: String, val playedAtMs: Long)

class HistoryStore(context: Context) {
    private val file = File(context.filesDir, "history.json")

    @Synchronized fun load(): List<HistoryEntry> {
        val text = readOrNull(file) ?: return emptyList()
        return try {
            val arr = JSONArray(text)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                HistoryEntry(o.getString("id"), o.optString("title"), o.optString("artist"), o.optLong("at"))
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized fun save(list: List<HistoryEntry>) {
        val arr = JSONArray()
        for (h in list.takeLast(500)) {
            arr.put(JSONObject().apply {
                put("id", h.trackId); put("title", h.title); put("artist", h.artist); put("at", h.playedAtMs)
            })
        }
        writeAtomic(file, arr.toString())
    }
}

/** A sound you picked from your phone for a custom sampler pad. */
data class CustomPad(val uri: String, val name: String)

/**
 * Shortcut buttons (top bar), "MY" pads (8 per deck) and custom sampler pads.
 */
class ButtonsStore(context: Context) {
    private val file = File(context.filesDir, "buttons.json")

    data class Saved(
        val shortcuts: List<Shortcut>,
        val myPads: List<List<String?>>,
        val customPads: List<List<CustomPad?>>,
    )

    @Synchronized fun load(): Saved {
        val empty = Saved(DEFAULT_SHORTCUTS, List(2) { List(8) { null } }, List(CUSTOM_BANKS) { List(8) { null } })
        val text = readOrNull(file) ?: return empty
        return try {
            val o = JSONObject(text)
            val sc = o.optJSONArray("shortcuts")?.let { arr ->
                List(arr.length()) { i -> arr.getJSONObject(i).let { Shortcut(it.getString("code"), it.optInt("deck", -1)) } }
            } ?: DEFAULT_SHORTCUTS
            val my = o.optJSONArray("my")?.let { decks ->
                List(2) { d ->
                    val arr = decks.optJSONArray(d)
                    List(8) { i -> arr?.optString(i)?.takeIf { it.isNotEmpty() && it != "null" } }
                }
            } ?: empty.myPads
            val custom = o.optJSONArray("custom")?.let { banks ->
                List(CUSTOM_BANKS) { b ->
                    val arr = banks.optJSONArray(b)
                    List(8) { i ->
                        arr?.optJSONObject(i)?.let { p -> CustomPad(p.getString("uri"), p.optString("name", "Sound")) }
                    }
                }
            } ?: empty.customPads
            Saved(sc, my, custom)
        } catch (_: Exception) {
            empty
        }
    }

    @Synchronized fun save(s: Saved) {
        val o = JSONObject()
        o.put("shortcuts", JSONArray().apply {
            for (x in s.shortcuts) put(JSONObject().apply { put("code", x.code); put("deck", x.deck) })
        })
        o.put("my", JSONArray().apply {
            for (deck in s.myPads) put(JSONArray().apply { for (c in deck) put(c ?: "") })
        })
        o.put("custom", JSONArray().apply {
            for (bank in s.customPads) put(JSONArray().apply {
                for (p in bank) put(if (p == null) JSONObject.NULL else JSONObject().apply { put("uri", p.uri); put("name", p.name) })
            })
        })
        writeAtomic(file, o.toString())
    }

    companion object {
        const val CUSTOM_BANKS = 2
        /** A few handy shortcuts to start with; you can remove them or add your own. */
        val DEFAULT_SHORTCUTS = listOf(
            Shortcut("sample:0:0", -1),
            Shortcut("fx:ECHO_OUT", 0),
            Shortcut("fx:ECHO_OUT", 1),
            Shortcut("fade", -1),
        )
    }
}

enum class PressureCurve { SOFT, LINEAR, FIRM }

/** 0 = Deck A, 1 = Deck B, -1 = whichever deck you touched last. */
data class PenSettings(
    val gripBase: Float = 0.72f,
    val curve: PressureCurve = PressureCurve.LINEAR,
    val focusDeck: Int = -1,
    val hoverZoom: Boolean = true,
    val penButtonBend: Boolean = true,
)

data class AppSettings(
    /** New tracks start with keylock on. */
    val keylockDefault: Boolean = true,
    /** Snap cues, loops and jumps to the beat. */
    val quantize: Boolean = true,
    /** Bring every track to a similar loudness. */
    val autoGain: Boolean = true,
    /** Keep the tempo setting when you load a new track (VirtualDJ calls the opposite "pitch reset"). */
    val keepTempoOnLoad: Boolean = true,
    /** Headphone cueing with a splitter cable: left = master, right = headphones. */
    val splitCue: Boolean = false,
    /** Show keys as "Am / C" instead of "8A / 8B". */
    val musicalKeys: Boolean = false,
    /** Automix crossfade length in seconds. */
    val automixSeconds: Float = 10f,
    /** When a track loads, shift its key by up to one semitone to match the other deck. */
    val autoKeyMatch: Boolean = false,
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun loadPen(): PenSettings = PenSettings(
        gripBase = prefs.getFloat("gripBase", 0.72f),
        curve = runCatching { PressureCurve.valueOf(prefs.getString("curve", "LINEAR")!!) }.getOrDefault(PressureCurve.LINEAR),
        focusDeck = prefs.getInt("focusDeck", -1),
        hoverZoom = prefs.getBoolean("hoverZoom", true),
        penButtonBend = prefs.getBoolean("penButtonBend", true),
    )

    fun savePen(s: PenSettings) {
        prefs.edit()
            .putFloat("gripBase", s.gripBase)
            .putString("curve", s.curve.name)
            .putInt("focusDeck", s.focusDeck)
            .putBoolean("hoverZoom", s.hoverZoom)
            .putBoolean("penButtonBend", s.penButtonBend)
            .apply()
    }

    fun loadApp(): AppSettings = AppSettings(
        keylockDefault = prefs.getBoolean("keylockDefault", true),
        quantize = prefs.getBoolean("quantize", true),
        autoGain = prefs.getBoolean("autoGain", true),
        keepTempoOnLoad = prefs.getBoolean("keepTempoOnLoad", true),
        splitCue = prefs.getBoolean("splitCue", false),
        musicalKeys = prefs.getBoolean("musicalKeys", false),
        automixSeconds = prefs.getFloat("automixSeconds", 10f),
        autoKeyMatch = prefs.getBoolean("autoKeyMatch", false),
    )

    fun saveApp(s: AppSettings) {
        prefs.edit()
            .putBoolean("keylockDefault", s.keylockDefault)
            .putBoolean("quantize", s.quantize)
            .putBoolean("autoGain", s.autoGain)
            .putBoolean("keepTempoOnLoad", s.keepTempoOnLoad)
            .putBoolean("splitCue", s.splitCue)
            .putBoolean("musicalKeys", s.musicalKeys)
            .putFloat("automixSeconds", s.automixSeconds)
            .putBoolean("autoKeyMatch", s.autoKeyMatch)
            .apply()
    }
}
