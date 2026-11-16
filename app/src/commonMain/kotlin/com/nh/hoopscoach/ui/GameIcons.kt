package com.nh.hoopscoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The app's icons: one family of rounded line icons on a 24-point grid, the same weight as the tab bar, with a
 * soft two-tone fill where a shape needs body. They take any tint, so they follow the team's colors and both
 * themes. Used instead of emoji, which look different on every phone and don't match the rest of the app.
 */
object GameIcons {
    /** One piece of an icon: an outline, and how much of its own fill shows (0 none, 1 solid). */
    private class Part(val d: String, val fill: Float = 0f, val stroke: Boolean = true)

    private fun s(d: String) = Part(d)
    private fun f(d: String, a: Float = 0.24f) = Part(d, a)
    private fun solid(d: String) = Part(d, 1f, stroke = false)

    private fun icon(name: String, vararg parts: Part, weight: Float = 1.8f): ImageVector = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        for (p in parts) addPath(
            addPathNodes(p.d),
            fill = if (p.fill > 0f) SolidColor(Color.Black) else null, fillAlpha = p.fill,
            stroke = if (p.stroke) SolidColor(Color.Black) else null,
            strokeLineWidth = weight, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        )
    }.build()

    // ---------- shapes, shared by the line and filled versions ----------

    private const val CAL = "M4 6.5A1.5 1.5 0 0 1 5.5 5h13A1.5 1.5 0 0 1 20 6.5v12a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 18.5z"
    private const val CAL_LINES = "M4 10h16 M8 3v4 M16 3v4"
    private const val JERSEY = "M8.5 3.5c1.2 1.8 5.8 1.8 7 0l3.2 1.4c-.4 2.6.3 4.1 1.6 5L18.6 20.5H5.4L3.7 9.9C5 9 5.7 7.5 5.3 4.9z"
    private const val STAR = "M12 3.5l2.6 5.3 5.9.9-4.3 4.1 1 5.8L12 16.9l-5.2 2.7 1-5.8L3.5 9.7l5.9-.9z"
    private const val CUP = "M7 4h10v5.5a5 5 0 0 1-10 0z"
    private const val CUP_REST = "M7 6H4.5v1a3.5 3.5 0 0 0 3 3.4 M17 6h2.5v1a3.5 3.5 0 0 1-3 3.4 M12 14.5v3"
    private const val CUP_BASE = "M9.5 17.5h5l.6 3H8.9z"
    private const val HEAD = "M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8z"
    private const val BODY = "M4.5 20c.8-3.6 3.8-5.5 7.5-5.5s6.7 1.9 7.5 5.5z"
    private const val BALL = "M12 3.5a8.5 8.5 0 1 0 0 17a8.5 8.5 0 1 0 0-17z"
    private const val BALL_SEAMS = "M3.5 12h17 M12 3.5v17 M6 6c2.5 2 3.6 3.9 3.6 6S8.5 16 6 18 M18 6c-2.5 2-3.6 3.9-3.6 6s1.1 4 3.6 6"
    private const val SWAP = "M5 8h13l-3.5-3.5 M19 16H6l3.5 3.5"

    // ---------- the tab bar: a line icon, and a filled one for the tab you're on ----------

    private val todayLine = icon("today", s(CAL), s(CAL_LINES), weight = 1.9f)
    private val todayOn = icon("today-on", f(CAL, 0.28f), s(CAL_LINES), solid("M8 13h3v3H8z"), weight = 1.9f)
    private val teamLine = icon("team", s(JERSEY), weight = 1.9f)
    private val teamOn = icon("team-on", f(JERSEY, 0.3f), s("M10 10.5h4 M12 10.5v5"), weight = 1.9f)
    private val starLine = icon("recruit", s(STAR), weight = 1.9f)
    private val starOn = icon("recruit-on", f(STAR, 0.35f), weight = 1.9f)
    private val tradesLine = icon("trades", s(SWAP), weight = 1.9f)
    private val tradesOn = icon("trades-on", s(SWAP), weight = 2.3f)
    private val meLine = icon("me", s(BALL), s(BALL_SEAMS), weight = 1.9f)
    private val meOn = icon("me-on", f(BALL, 0.3f), s(BALL_SEAMS), weight = 1.9f)
    private val leagueLine = icon("league", s(CUP), s(CUP_REST), s(CUP_BASE), weight = 1.9f)
    private val leagueOn = icon("league-on", f(CUP, 0.32f), s(CUP_REST), f(CUP_BASE, 0.32f), weight = 1.9f)
    private val careerLine = icon("career", s(HEAD), s("M4.5 20c.8-3.6 3.8-5.5 7.5-5.5s6.7 1.9 7.5 5.5"), weight = 1.9f)
    private val careerOn = icon("career-on", f(HEAD, 0.32f), f(BODY, 0.32f), weight = 1.9f)

    /** A tab's icon. [kind] is "today", "team", "recruit", "trades", "me", "league" or "career". */
    fun tab(kind: String, selected: Boolean): ImageVector = when (kind) {
        "today" -> if (selected) todayOn else todayLine
        "team" -> if (selected) teamOn else teamLine
        "trades" -> if (selected) tradesOn else tradesLine
        "me" -> if (selected) meOn else meLine
        "league" -> if (selected) leagueOn else leagueLine
        "career" -> if (selected) careerOn else careerLine
        else -> if (selected) starOn else starLine
    }

    // ---------- the game ----------

    val Ball = icon("ball", f(BALL, 0.22f), s(BALL_SEAMS))
    val Trophy = icon("trophy", f(CUP, 0.26f), s(CUP_REST), f(CUP_BASE, 0.26f))
    val Star = icon("star", f(STAR, 0.3f))
    val Medal = icon("medal",
        f("M7 2.8h3.2L12 7l1.8-4.2H17l-3.2 7.4h-3.6z", 0.3f),
        f("M12 10a5.3 5.3 0 1 0 0 10.6A5.3 5.3 0 0 0 12 10z", 0.3f),
        s("M12 12.9l.9 1.8 2 .3-1.45 1.4.35 2-1.8-.95-1.8.95.35-2L9.1 15l2-.3z"),
    )
    val Swap = icon("swap", s(SWAP))

    // ---------- program building ----------

    val Arena = icon("arena",
        f("M3 10v7.5c0 1.2 4 2.8 9 2.8s9-1.6 9-2.8V10", 0.2f),
        s("M3 10c0-2.5 4-4.5 9-4.5s9 2 9 4.5 M3 10c0 2.5 4 4.5 9 4.5s9-2 9-4.5 M7 14.1v5 M12 14.5v5.8 M17 14.1v5"),
        s("M12 5.5V2.6l2.6 1.1-2.6 1.1"),
    )
    val Hoop = icon("hoop",
        f("M5 3.5h14v8H5z", 0.18f),
        s("M9.5 11.5V8h5v3.5 M7.5 11.5h9 M8.3 11.5l1.6 8.5 M15.7 11.5l-1.6 8.5 M9.9 20h4.2 M8.9 15.5h6.2"),
    )
    val Clipboard = icon("clipboard",
        f("M7 5H5.5A1.5 1.5 0 0 0 4 6.5v13A1.5 1.5 0 0 0 5.5 21h13a1.5 1.5 0 0 0 1.5-1.5v-13A1.5 1.5 0 0 0 18.5 5H17", 0.16f),
        f("M8.5 3h7v3.5h-7z", 0.35f),
        s("M8 11h8 M8 14.5h8 M8 18h4.5"),
    )
    val Medical = icon("medical",
        f("M12 20.5s-8-4.7-8-10.6A4.5 4.5 0 0 1 12 7.1a4.5 4.5 0 0 1 8 2.8c0 5.9-8 10.6-8 10.6z", 0.2f),
        s("M6.5 12.5h3l1.5-2.6 2 5.2 1.5-2.6h3"),
    )
    val Locker = icon("locker",
        f("M4.5 3.5h15v17h-15z", 0.16f),
        s("M12 3.5v17 M7 6.8h2.5 M7 8.8h2.5 M14.5 6.8H17 M14.5 8.8H17 M9.8 13v2.4 M14.2 13v2.4"),
    )
    val Megaphone = icon("megaphone",
        f("M3.5 10v4a1 1 0 0 0 1 1h2l8.5 4.5v-15L6.5 9h-2a1 1 0 0 0-1 1z", 0.22f),
        s("M6.5 15l1.4 4.8h2.4L9.4 16 M18 9.6a3.4 3.4 0 0 1 0 4.8 M20.3 7.4a6.6 6.6 0 0 1 0 9.2"),
    )
    val Money = icon("money",
        f("M3 6.5h18v11H3z", 0.18f),
        s("M12 9.5a2.5 2.5 0 1 0 0 5 2.5 2.5 0 0 0 0-5z M6 9.5v5 M18 9.5v5"),
    )

    // ---------- the senior year ----------

    val Sun = icon("sun",
        f("M12 7.8a4.2 4.2 0 1 0 0 8.4 4.2 4.2 0 0 0 0-8.4z", 0.3f),
        s("M12 2.5v2 M12 19.5v2 M2.5 12h2 M19.5 12h2 M5.3 5.3l1.4 1.4 M17.3 17.3l1.4 1.4 M5.3 18.7l1.4-1.4 M17.3 6.7l1.4-1.4"),
    )
    val Plane = icon("plane", f("M20.5 3.5L3.5 10.5l7 3 3 7z", 0.22f), s("M10.5 13.5l10-10"))
    val Cap = icon("cap",
        f("M4.5 15.5C4.5 10 7.8 6.5 12 6.5s7.5 3.5 7.5 9z", 0.24f),
        s("M12 15.5h9a1.25 1.25 0 0 1 0 2.5H11 M12 6.5V5 M12 6.5c-1.7 2.4-2.1 5.6-1.6 9"),
    )

    // ---------- highlights and the trophy room ----------

    val Bolt = icon("bolt", f("M13.5 2.5L5 13.5h6.2l-1 8 8.3-11.2h-6.2z", 0.26f))
    val Shield = icon("shield", f("M12 3l7.5 3v5.5c0 4.5-3.2 8-7.5 9.5-4.3-1.5-7.5-5-7.5-9.5V6z", 0.22f), s("M9 12l2.2 2.2L15.2 10"))
    val Stopwatch = icon("stopwatch",
        f("M12 7.5a6.5 6.5 0 1 0 0 13 6.5 6.5 0 0 0 0-13z", 0.2f),
        s("M12 11v3.5l2.2 1.6 M10 3h4 M12 3v4.5 M18 6.6l1.4-1.4"),
    )
    val Target = icon("target",
        s("M12 3.5a8.5 8.5 0 1 0 0 17a8.5 8.5 0 1 0 0-17z"),
        f("M12 7.5a4.5 4.5 0 1 0 0 9 4.5 4.5 0 0 0 0-9z", 0.22f),
        solid("M12 10.6a1.4 1.4 0 1 0 0 2.8 1.4 1.4 0 0 0 0-2.8z"),
    )
    val Film = icon("film",
        f("M4 9.5h16v9.5a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 19z", 0.18f),
        s("M4 9.5l-.6-3.3 15.7-2.8.6 3.3z M8.3 5.4l2.2 3.2 M13.3 4.5l2.2 3.3"),
        solid("M10.5 12.5v5.4l4.3-2.7z"),
    )
    val Lock = icon("lock", f("M5.5 11h13v9.5h-13z", 0.22f), s("M8 11V8a4 4 0 0 1 8 0v3 M12 14.5v2.5"))
    val Newspaper = icon("newspaper",
        f("M4 5h12.5v14.5a1 1 0 0 0 1 1H5.5A1.5 1.5 0 0 1 4 19z", 0.16f),
        s("M16.5 9H20v10a1.5 1.5 0 0 1-3 0 M7 8.5h6.5 M7 12h6.5 M7 15.5h4"),
    )
    /** Written by the AI. */
    val Sparkle = icon("sparkle",
        f("M11 3.5l1.8 5.2L18 10.5l-5.2 1.8L11 17.5l-1.8-5.2L4 10.5l5.2-1.8z", 0.3f),
        f("M18.5 15l.7 1.8 1.8.7-1.8.7-.7 1.8-.7-1.8-1.8-.7 1.8-.7z", 1f),
    )

    val Reply = icon("reply", s("M4.5 5.5h15v10h-8.5l-4.5 3.8V15.5H4.5z"))
    val Repost = icon("repost", s("M7 7.5h10.5l-2.6-2.6 M17.5 7.5v3.5 M17 16.5H6.5l2.6 2.6 M6.5 16.5V13"))

    /** A highlight's kind. */
    fun highlight(kind: String) = when (kind) { "dunk", "poster" -> Bolt; "block" -> Shield; "winner", "tie" -> Stopwatch; else -> Target }

    /** A facility. */
    fun facility(id: String) = when (id) {
        "arena" -> Arena; "practice" -> Hoop; "recruiting" -> Clipboard; "training" -> Medical; "locker" -> Locker; else -> Megaphone
    }
}

/** An icon on a soft rounded tile of its own color: a list row's or a card's leading mark. */
@Composable
fun IconTile(icon: ImageVector, color: Color = P.accent, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(color.copy(alpha = if (P.dark) 0.18f else 0.12f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = color, modifier = Modifier.size(size * 0.56f))
    }
}

/** A small icon in front of a line of text, sized to it: a label's trophy, a badge's arrows. */
@Composable
fun IconLabel(icon: ImageVector, text: String, style: androidx.compose.ui.text.TextStyle, color: Color, modifier: Modifier = Modifier, iconColor: Color = color) {
    androidx.compose.foundation.layout.Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        val px = with(androidx.compose.ui.platform.LocalDensity.current) { (style.fontSize.toPx() * 1.25f).toDp() }
        Icon(icon, null, tint = iconColor, modifier = Modifier.size(px))
        androidx.compose.foundation.layout.Spacer(Modifier.size(px * 0.35f))
        androidx.compose.material3.Text(text, style = style, color = color, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}
