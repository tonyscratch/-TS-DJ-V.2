package com.tonyscratch.tsdj.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Line icons drawn to match the TS Dj design (24×24 grid, round strokes). */
object TsIcons {
    private fun circle(cx: Float, cy: Float, r: Float) =
        "M${cx - r},${cy}a$r,$r 0 1,0 ${2 * r},0a$r,$r 0 1,0 ${-2 * r},0"

    private fun rrect(x: Float, y: Float, w: Float, h: Float, r: Float) =
        "M${x + r},${y}h${w - 2 * r}a$r,$r 0 0 1 $r,$r" + "v${h - 2 * r}a$r,$r 0 0 1 ${-r},$r" +
            "h${-(w - 2 * r)}a$r,$r 0 0 1 ${-r},${-r}" + "v${-(h - 2 * r)}a$r,$r 0 0 1 $r,${-r}z"

    private fun icon(name: String, stroke: List<String>, fill: List<String> = emptyList(), width: Float = 2f): ImageVector {
        val b = ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
        for (p in stroke) {
            b.addPath(
                pathData = addPathNodes(p),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        for (p in fill) {
            b.addPath(pathData = addPathNodes(p), fill = SolidColor(Color.Black))
        }
        return b.build()
    }

    val Library = icon("library", listOf("M4 6h10M4 12h10M4 18h6", circle(17f, 17f, 3f), "M20 17V5l-3 1"))
    val Video = icon("video", listOf(rrect(3f, 5f, 18f, 14f, 3f), "M10 9.5v5l4.5-2.5z"))
    val Pen = icon("pen", listOf("M4 20l4-1 11-11-3-3L5 16z", "M14 6l3 3"))
    val Sliders = icon("sliders", listOf("M4 7h10M18 7h2M4 17h4M12 17h8", circle(16f, 7f, 2f), circle(10f, 17f, 2f)))
    val Back = icon("back", listOf("M15 5l-7 7 7 7"), width = 2.4f)
    val Search = icon("search", listOf(circle(11f, 11f, 7f), "M20 20l-4-4"))
    val Headphones = icon("headphones", listOf("M4 15v-3a8 8 0 0 1 16 0v3", rrect(3f, 14f, 4f, 6f, 1.5f), rrect(17f, 14f, 4f, 6f, 1.5f)))
    val Play = icon("play", emptyList(), listOf("M7 4.5v15a1 1 0 0 0 1.5.86l12-7.5a1 1 0 0 0 0-1.72l-12-7.5A1 1 0 0 0 7 4.5z"))
    val Pause = icon("pause", emptyList(), listOf(rrect(6f, 5f, 4f, 14f, 1f), rrect(14f, 5f, 4f, 14f, 1f)))
    val Loop = icon("loop", listOf("M17 2l3 3-3 3", "M4 11V9a4 4 0 0 1 4-4h12", "M7 22l-3-3 3-3", "M20 13v2a4 4 0 0 1-4 4H4"))
    val Sync = icon("sync", listOf("M20 12a8 8 0 0 1-14 5.3", "M4 12a8 8 0 0 1 14-5.3", "M18 3v4h-4", "M6 21v-4h4"), width = 2.5f)
    val Folder = icon("folder", listOf("M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"))
    val Download = icon("download", listOf("M12 4v11", "M7 10l5 5 5-5", "M5 20h14"))
    val Heart = icon("heart", listOf("M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10z"))
    val Playlist = icon("playlist", listOf("M4 6h12M4 12h12M4 18h8", "M19 15v6M16 18h6"))
    val Note = icon("note", listOf("M9 18V5l12-2v13", circle(6f, 18f, 3f), circle(18f, 16f, 3f)))
    val Check = icon("check", listOf("M5 12l5 5 9-10"), width = 3f)
    val ChevronDown = icon("chevron", listOf("M6 9l6 6 6-6"), width = 2.4f)
    val Share = icon("share", listOf(circle(18f, 5f, 3f), circle(6f, 12f, 3f), circle(18f, 19f, 3f), "M8.6 13.5l6.8 4M15.4 6.5l-6.8 4"))
    val Delete = icon("delete", listOf("M4 7h16", "M10 11v6M14 11v6", "M6 7l1 13h10l1-13", "M9 7V4h6v3"))
    val Plus = icon("plus", listOf("M12 5v14M5 12h14"), width = 2.4f)
    val Close = icon("close", listOf("M6 6l12 12M18 6L6 18"), width = 2.4f)
    val Info = icon("info", listOf(circle(12f, 12f, 9f), "M12 11v5M12 8h.01"), width = 2.2f)
    val Link = icon("link", listOf("M10 14a4 4 0 0 0 6 0l3-3a4 4 0 0 0-6-6l-1 1", "M14 10a4 4 0 0 0-6 0l-3 3a4 4 0 0 0 6 6l1-1"))
    val Disc = icon("disc", listOf(circle(12f, 12f, 9f), circle(12f, 12f, 3f)))
    val Volume = icon("volume", listOf("M4 9v6h4l5 4V5L8 9z", "M16 9a4 4 0 0 1 0 6"), width = 2.2f)
    val Record = icon("record", emptyList(), listOf(circle(12f, 12f, 6f)))
    val Analyze = icon("analyze", listOf("M3 12h3l3-7 4 14 3-7h5"))
}
