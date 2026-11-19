package com.nh.hoopscoach.game

import kotlin.math.roundToInt

/**
 * The user's player earns his summer growth. How far he closes the gap to his ceiling depends on
 * his season: minutes (45%), production against what his rating predicts (35%), and practice (20%).
 * A starter who plays above his rating and puts in extra work closes about half the gap; a
 * benchwarmer who rests about a tenth. Everyone else in the league grows as before.
 */
object Earned {
    class Report(val minutes: Double, val production: Double, val work: Double, val mpg: Double, val over: Double, val effort: Double) {
        val earned get() = 0.45 * minutes + 0.35 * production + 0.20 * work
    }

    /** Game score per 36 minutes: what he produces when he plays. */
    private fun perThirtySix(s: StatLine): Double {
        if (s.sec <= 0) return 0.0
        val gs = s.pts + 0.7 * s.reb + 0.9 * s.ast + 1.2 * (s.stl + s.blk) - s.tov - 0.4 * (s.fga - s.fgm)
        return gs * 36.0 / (s.sec / 60.0)
    }

    /** What a player of [ovr] usually produces per 36 minutes at his level. */
    private fun expected(lg: League, ovr: Int) = if (lg.pro) 15.0 + (ovr - 85) * 0.55 else 15.0 + (ovr - 75) * 0.45

    fun report(lg: League, p: Player): Report {
        val r = lg.road
        val s = p.stats
        val full = if (lg.pro) 32.0 else 30.0
        val mpg = s.mpg
        val over = if (s.gp == 0) 0.0 else perThirtySix(s) - expected(lg, p.ovr)
        val effort = if (r == null || r.workDays == 0) 0.5 else r.work / r.workDays
        return Report(
            minutes = (mpg / full).coerceIn(0.0, 1.0),
            production = if (s.gp == 0) 0.0 else (0.5 + over / 12.0).coerceIn(0.0, 1.0),
            work = effort.coerceIn(0.0, 1.0), mpg = mpg, over = over, effort = effort,
        )
    }

    /** Share of the gap to his ceiling he closes this summer. */
    fun share(rep: Report) = 0.08 + 0.42 * rep.earned

    /** The summer: growth from what he did, and a line saying why. */
    fun hero(lg: League, p: Player, coachDev: Int) {
        val r = lg.road ?: return
        val before = p.ovr
        val rep = report(lg, p)
        Ratings.offseasonGrowth(p, coachDev, share(rep))
        val gain = p.ovr - before
        r.growthNote = note(rep, gain)
        r.work = 0.0
        r.workDays = 0
    }

    /** "+5 this summer: a starter's minutes (34 a game), played above his rating, extra work most days." */
    fun note(rep: Report, gain: Int): String {
        val mins = when { rep.minutes >= 0.85 -> "a starter's minutes (${rep.mpg.roundToInt()} a game)"; rep.minutes >= 0.5 -> "real minutes (${rep.mpg.roundToInt()} a game)"; rep.mpg >= 1 -> "few minutes (${rep.mpg.roundToInt()} a game)"; else -> "no minutes" }
        val prod = when { rep.production >= 0.7 -> "played well above his rating"; rep.production >= 0.45 -> "played to his rating"; rep.mpg >= 1 -> "played below his rating"; else -> "no games to show for it" }
        val work = when { rep.work >= 0.8 -> "extra work most days"; rep.work >= 0.5 -> "steady practice"; else -> "light practice" }
        val head = when { gain > 0 -> "+$gain this summer"; gain == 0 -> "No change this summer"; else -> "$gain this summer" }
        return "$head: $mins, $prod, $work."
    }

    /** The same, mid-season: what this summer looks like if the season ended today. */
    fun outlook(lg: League, p: Player): String {
        val rep = report(lg, p)
        val gap = (p.pot - p.ovr).coerceAtLeast(0)
        val est = (gap * share(rep)).roundToInt()
        return (if (gap == 0) "At your ceiling." else "On track for about +$est this summer") + " · minutes ${pct(rep.minutes)}, production ${pct(rep.production)}, practice ${pct(rep.work)}"
    }

    private fun pct(x: Double) = when { x >= 0.75 -> "strong"; x >= 0.45 -> "fair"; else -> "weak" }
}
