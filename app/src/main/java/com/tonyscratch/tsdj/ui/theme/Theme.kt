@file:OptIn(ExperimentalTextApi::class)

package com.tonyscratch.tsdj.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.tonyscratch.tsdj.R

/** The TS Dj palette: bright colours on pure black, orange first. */
object TS {
    val Orange = Color(0xFFFF6B1A)
    val Ember = Color(0xFFE8500A)
    val Amber = Color(0xFFFFB020)
    val Pink = Color(0xFFFF4D8D)
    val Cyan = Color(0xFF2ED3F0)
    val Lime = Color(0xFFB5F23D)
    val Violet = Color(0xFFA98BFF)
    val Mint = Color(0xFF3DF2B5)

    val Black = Color(0xFF000000)
    val Graphite = Color(0xFF141414)
    val Charcoal = Color(0xFF1F1F1F)
    val Line = Color(0xFF262626)
    val Line2 = Color(0xFF333333)
    val KnobBody = Color(0xFF2A2A2A)

    val Text = Color(0xFFFFFFFF)
    val Text2 = Color(0xFFCCCCCC)
    val Muted = Color(0xFFA3A3A3)

    val OrangeDim = Color(0xFF7A3A14)
    val CyanDim = Color(0xFF1D5F6B)

    val cueColors = listOf(Orange, Amber, Pink, Lime, Cyan, Violet, Mint, Ember)
    val fxGroupColors = listOf(Amber, Cyan, Pink, Lime)
    val crateColors = listOf(Pink, Amber, Lime, Cyan, Orange)

    fun deck(i: Int): Color = if (i == 0) Orange else Cyan
    fun deckDim(i: Int): Color = if (i == 0) OrangeDim else CyanDim
}

private fun variable(res: Int, weight: Int) =
    Font(res, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

val Unbounded = FontFamily(variable(R.font.unbounded, 700), variable(R.font.unbounded, 800))
val Manrope = FontFamily(
    variable(R.font.manrope, 500),
    variable(R.font.manrope, 600),
    variable(R.font.manrope, 700),
    variable(R.font.manrope, 800),
)
val Mono = FontFamily(variable(R.font.jetbrains_mono, 500), variable(R.font.jetbrains_mono, 700))

/** Text style helpers so every screen uses the same three typefaces. */
object Type {
    fun display(size: TextUnit, color: Color = TS.Text) =
        TextStyle(fontFamily = Unbounded, fontWeight = FontWeight(800), fontSize = size, color = color)

    fun ui(size: TextUnit, weight: Int = 700, color: Color = TS.Text) =
        TextStyle(fontFamily = Manrope, fontWeight = FontWeight(weight), fontSize = size, color = color)

    fun label(size: TextUnit = 9.sp, color: Color = TS.Muted) =
        TextStyle(fontFamily = Manrope, fontWeight = FontWeight(800), fontSize = size, color = color, letterSpacing = 1.2.sp)

    fun mono(size: TextUnit, weight: Int = 700, color: Color = TS.Text) =
        TextStyle(fontFamily = Mono, fontWeight = FontWeight(weight), fontSize = size, color = color)
}

@Composable
fun TsDjTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = TS.Orange,
            onPrimary = TS.Black,
            secondary = TS.Cyan,
            tertiary = TS.Amber,
            background = TS.Black,
            onBackground = TS.Text,
            surface = TS.Graphite,
            onSurface = TS.Text,
            surfaceVariant = TS.Charcoal,
            surfaceContainer = TS.Charcoal,
            onSurfaceVariant = TS.Text2,
            outline = TS.Line2,
            error = TS.Pink,
        ),
        content = content,
    )
}
