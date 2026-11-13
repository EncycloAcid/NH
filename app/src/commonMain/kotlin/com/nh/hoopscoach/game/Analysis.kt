package com.nh.hoopscoach.game

import kotlin.math.abs
import kotlin.math.pow

/**
 * The numbers behind the previews and the columns: how much of a roster is back (by last season's
 * minutes), who arrived and who left, how old the rotation is, where a team's offense and defense rank
 * on paper, and a projected stat line for a player (his last season's rates, scaled by how much better
 * or worse he is now and by the minutes his new role brings).
 */
object Analysis {
    private fun lastLines(lg: League, p: Player) =
        p.career.filter { it.season == lg.season - 1 && (if (lg.pro) it.cls == PRO_YEAR else it.cls != PRO_YEAR) && it.s.gp > 0 }

    /** His line with [t] last season, if he played there. */
    fun lastWith(lg: League, p: Player, t: Team): SeasonLine? = lastLines(lg, p).lastOrNull { it.teamId == t.id }

    /** His last season wherever he played (the one with the most games), if any. */
    fun last(lg: League, p: Player): SeasonLine? = lastLines(lg, p).maxByOrNull { it.s.gp }

    class Turnover(
        /** Share of last season's team minutes that are back (0 to 1). */
        val returning: Double,
        val back: List<Player>,
        /** New faces: draft picks, freshmen, signings, trades, transfers. */
        val arrivals: List<Player>,
        /** Players from last season's team now elsewhere (retired and graduated players are gone from the data). */
        val departures: List<Pair<Player, SeasonLine>>,
        /** Points a game that left with the departures, and that came back. */
        val lostPpg: Double, val backPpg: Double,
    )

    /** How much of [t] is back from last season, or null in a league's first season. */
    fun turnover(lg: League, t: Team): Turnover? {
        val roster = lg.rosterOf(t)
        val backLines = roster.mapNotNull { p -> lastWith(lg, p, t)?.let { p to it } }
        val gone = lg.players.filter { it.teamId != t.id }.mapNotNull { p -> lastWith(lg, p, t)?.let { p to it } }
        val anyLine = (backLines + gone).firstOrNull()?.second ?: return null
        val games = (anyLine.w + anyLine.l).takeIf { it > 0 } ?: t.history.lastOrNull()?.let { it.w + it.l } ?: return null
        val teamSeconds = games * (if (lg.pro) 240 else 200) * 60.0
        val backSec = backLines.sumOf { it.second.s.sec }
        // Players who left the data (retired, graduated, overseas) aren't counted, so take the larger of the two views.
        val seen = backSec + gone.sumOf { it.second.s.sec }
        val share = (backSec / maxOf(teamSeconds, seen.toDouble())).coerceIn(0.0, 1.0)
        return Turnover(
            share, backLines.map { it.first },
            roster.filter { p -> backLines.none { it.first.id == p.id } },
            gone.sortedByDescending { it.second.s.ppg },
            gone.sumOf { it.second.s.ppg }, backLines.sumOf { it.second.s.ppg },
        )
    }

    /** The average age of the top eight. */
    fun rotationAge(lg: League, t: Team): Double = lg.rosterOf(t).sortedByDescending { it.ovr }.take(8).map { it.age }.average().takeIf { !it.isNaN() } ?: 0.0

    fun offense(lg: League, t: Team) = Gameplan.teamRating(lg, t) { Ratings.offense(it) }
    fun defense(lg: League, t: Team) = Gameplan.teamRating(lg, t) { Ratings.defense(it) }

    /** Where [t] ranks (1 = best) among [teams] by [f]. */
    fun rank(teams: List<Team>, t: Team, f: (Team) -> Int): Int = teams.sortedByDescending(f).indexOfFirst { it.id == t.id } + 1

    class Line(val ppg: Double, val rpg: Double, val apg: Double, val mpg: Double) {
        override fun toString() = "%.1f points, %.1f rebounds and %.1f assists".format(ppg, rpg, apg)
        val short get() = "%.1f / %.1f / %.1f".format(ppg, rpg, apg)
    }

    /** Minutes his place in the rotation brings: the best player plays the most. */
    private fun roleMinutes(lg: League, p: Player): Double {
        if (p.teamId < 0) return 0.0
        val i = lg.rosterOf(lg.team(p.teamId)).sortedByDescending { it.ovr }.indexOfFirst { it.id == p.id }
        val pro = listOf(35.0, 33.5, 32.0, 30.0, 28.5, 25.0, 22.0, 18.5, 14.0, 10.0)
        val col = listOf(33.0, 31.5, 30.0, 28.5, 27.0, 22.0, 18.0, 14.0, 10.0, 7.0)
        return (if (lg.pro) pro else col).getOrElse(i) { 6.0 }
    }

    /** A projected per-game line this season. */
    fun project(lg: League, p: Player): Line {
        val min = roleMinutes(lg, p)
        val l = last(lg, p)?.takeIf { it.s.gp >= 8 && it.s.sec >= 8 * 60 * 8 }
        if (l != null) {
            val mins = l.s.sec / 60.0
            // Ratings move production: about 1.5% per point of overall, compounding.
            val grow = (1.015).pow((p.ovr - l.ovr).toDouble()).coerceIn(0.7, 1.45)
            val m = min.coerceAtMost(mins / l.s.gp + 8)
            return Line(l.s.pts / mins * m * grow, l.s.reb / mins * m * (0.6 + grow * 0.4), l.s.ast / mins * m * (0.6 + grow * 0.4), m)
        }
        // No line to go on (a rookie, a freshman): his rating and position.
        if (!lg.pro) {
            val colK = ((p.ovr - 58) / 28.0).coerceIn(0.1, 1.45)
            val ppg = (6.0 + 8.5 * colK) * (min / 30.0).coerceIn(0.35, 1.15)
            val rpg = when (p.pos) { Pos.C -> 8.0; Pos.PF -> 6.6; Pos.SF -> 4.8; Pos.SG -> 3.4; Pos.PG -> 2.6 } * (0.75 + colK * 0.3) * (min / 30.0)
            val apg = when (p.pos) { Pos.PG -> 5.0; Pos.SG -> 2.8; Pos.SF -> 2.2; Pos.PF -> 1.5; Pos.C -> 1.1 } * (0.75 + colK * 0.3) * (min / 30.0)
            return Line(ppg, rpg, apg, min)
        }
        val k = ((p.ovr - 72) / 20.0).coerceIn(0.1, 1.6)
        val per36 = Line(10 + 12 * k, when (p.pos) { Pos.C -> 9.5; Pos.PF -> 8.0; Pos.SF -> 5.5; Pos.SG -> 4.0; Pos.PG -> 3.5 } * (0.8 + k * 0.3),
            when (p.pos) { Pos.PG -> 6.5; Pos.SG -> 3.5; Pos.SF -> 2.8; Pos.PF -> 2.0; Pos.C -> 1.6 } * (0.8 + k * 0.3), 36.0)
        return Line(per36.ppg * min / 36, per36.rpg * min / 36, per36.apg * min / 36, min)
    }

    /** The biggest gap between two numbers, for "best call" and "biggest miss". */
    fun gap(a: Int, b: Int) = abs(a - b)
}
