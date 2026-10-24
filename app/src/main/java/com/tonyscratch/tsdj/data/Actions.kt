package com.tonyscratch.tsdj.data

import com.tonyscratch.tsdj.audio.FxBeats
import com.tonyscratch.tsdj.audio.FxType
import com.tonyscratch.tsdj.audio.SampleBank

enum class ActionCat(val label: String) {
    DECK("Deck"),
    LOOP("Loops & jumps"),
    CUES("Hot cues"),
    FX("Effects"),
    SAMPLER("Sounds"),
    MIXER("Mixer"),
}

/**
 * Something a shortcut button can do. [code] is what gets saved; [perDeck] actions need a deck
 * (A, B or both). Codes look like "play", "loop:4", "fx:ECHO", "sample:0:3".
 */
data class ActionDef(val code: String, val label: String, val short: String, val cat: ActionCat, val perDeck: Boolean)

/** A button you made: an action, and which deck it works on (0 = A, 1 = B, 2 = both, -1 = none). */
data class Shortcut(val code: String, val deck: Int)

object Actions {
    private val fixed: List<ActionDef> = buildList {
        fun deck(code: String, label: String, short: String) = add(ActionDef(code, label, short, ActionCat.DECK, true))
        deck("play", "Play / pause", "PLAY")
        deck("cue", "Back to cue", "CUE")
        deck("sync", "Sync", "SYNC")
        deck("keylock", "Keylock on/off", "KEYLOCK")
        deck("slip", "Slip on/off", "SLIP")
        deck("keymatch", "Key match", "KEY ✓")
        deck("key:1", "Key +1", "KEY +1")
        deck("key:-1", "Key −1", "KEY −1")
        deck("keyreset", "Key reset", "KEY 0")
        deck("pitchreset", "Reset tempo", "TEMPO 0")
        deck("brake", "Brake", "BRAKE")
        deck("backspin", "Backspin", "SPIN")
        deck("reverse", "Reverse on/off", "REV")
        deck("killlow", "Kill bass on/off", "BASS ✕")
        deck("killhigh", "Kill treble on/off", "HIGH ✕")
        deck("filterreset", "Filter to middle", "FILTER 0")
        deck("pfl", "Headphones on/off", "PFL")
        deck("eject", "Eject track", "EJECT")

        for (b in doubleArrayOf(0.25, 0.5, 1.0, 2.0, 4.0, 8.0, 16.0, 32.0)) {
            add(ActionDef("loop:$b", "Loop ${FxBeats.label(b)} beat${if (b > 1) "s" else ""}", "LOOP ${FxBeats.label(b)}", ActionCat.LOOP, true))
        }
        add(ActionDef("loopexit", "Exit loop", "LOOP ✕", ActionCat.LOOP, true))
        add(ActionDef("reloop", "Reloop", "RELOOP", ActionCat.LOOP, true))
        for (j in intArrayOf(-16, -4, -1, 1, 4, 16)) {
            add(ActionDef("jump:$j", "Jump ${if (j > 0) "+" else "−"}${kotlin.math.abs(j)} beats", "${if (j > 0) "+" else "−"}${kotlin.math.abs(j)}", ActionCat.LOOP, true))
        }

        for (i in 1..8) add(ActionDef("hotcue:$i", "Hot cue $i", "CUE $i", ActionCat.CUES, true))

        for (t in FxType.entries) add(ActionDef("fx:${t.name}", "${t.title}${if (t.mode.name == "TOGGLE") " on/off" else ""}", t.short, ActionCat.FX, true))
        add(ActionDef("fxoff", "All effects off", "FX OFF", ActionCat.FX, true))

        add(ActionDef("xf:a", "Crossfader to A", "◀ A", ActionCat.MIXER, false))
        add(ActionDef("xf:mid", "Crossfader to middle", "◀▶", ActionCat.MIXER, false))
        add(ActionDef("xf:b", "Crossfader to B", "B ▶", ActionCat.MIXER, false))
        add(ActionDef("fade", "Fade to the other deck (8 s)", "FADE", ActionCat.MIXER, false))
        add(ActionDef("automix", "Automix on/off", "AUTO", ActionCat.MIXER, false))
        add(ActionDef("rec", "Record on/off", "REC", ActionCat.MIXER, false))
        add(ActionDef("samplestop", "Stop all sounds", "■ SOUNDS", ActionCat.MIXER, false))
    }

    /** Every action, including one per sound in each sampler bank. */
    fun all(banks: List<SampleBank>): List<ActionDef> = fixed + banks.flatMapIndexed { bi, bank ->
        bank.sounds.mapIndexedNotNull { pi, s ->
            val name = s?.name ?: if (bank.custom) "${bank.name} pad ${pi + 1}" else return@mapIndexedNotNull null
            ActionDef("sample:$bi:$pi", name, name.uppercase().take(10), ActionCat.SAMPLER, false)
        }
    }

    fun find(code: String, banks: List<SampleBank>): ActionDef? = all(banks).firstOrNull { it.code == code }

    fun label(s: Shortcut, banks: List<SampleBank>): String {
        val def = find(s.code, banks) ?: return "?"
        val prefix = when (s.deck) { 0 -> "A "; 1 -> "B "; 2 -> "A+B "; else -> "" }
        return if (def.perDeck) prefix + def.short else def.short
    }
}
