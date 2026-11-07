package com.nh.hoopscoach.game

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow

object Rankings {
    const val HOME_ELO = 70.0

    /** Home-court edge in Elo for a game: none on a neutral floor, louder for a rivalry. */
    fun homeElo(lg: League, g: Game): Double = when {
        g.neutral -> 0.0
        lg.rivalry(g) != null -> HOME_ELO + Rivalries.HOME_BOOST
        else -> HOME_ELO
    }

    /** Pregame win chance for [teamId] in [g], crowd included. */
    fun winProbFor(lg: League, g: Game, teamId: Int): Double {
        val adv = homeElo(lg, g)
        val diff = lg.team(teamId).elo - lg.team(g.opp(teamId)).elo + if (g.home == teamId) adv else -adv
        return 1.0 / (1.0 + 10.0.pow(-diff / 400.0))
    }

    fun winProb(eloA: Double, eloB: Double, aHome: Int /* 1 home, 0 neutral, -1 away */): Double {
        val diff = eloA - eloB + aHome * HOME_ELO
        return 1.0 / (1.0 + 10.0.pow(-diff / 400.0))
    }

    fun strength(lg: League, t: Team): Double {
        Gameplan.validate(lg, t)
        val rating = Gameplan.teamRating(lg, t)
        val c = lg.coachOf(t)
        return rating + (c.off + c.def - 140) * 0.03
    }

    fun seedElo(lg: League, carryOver: Boolean) {
        for (t in lg.teams) {
            // Calibrated against simulated seasons (28 left the projections a little bunched).
            val fresh = 1500 + (strength(lg, t) - 70) * 32
            t.elo = if (carryOver) fresh * 0.75 + t.elo * 0.25 else fresh
        }
    }

    fun updateElo(lg: League, g: Game) {
        val h = lg.team(g.home)
        val a = lg.team(g.away)
        val adv = homeElo(lg, g)
        val pH = winProbFor(lg, g, h.id)
        val sH = if (g.hs > g.aws) 1.0 else 0.0
        val margin = abs(g.hs - g.aws).toDouble()
        val eloDiff = if (sH == 1.0) h.elo - a.elo + adv else a.elo - h.elo - adv
        val mult = ln(margin + 1) * (2.2 / (eloDiff * 0.001 + 2.2))
        val k = 22.0
        val delta = k * mult * (sH - pH)
        h.elo += delta
        a.elo -= delta
    }

    fun refresh(lg: League) {
        val byElo = lg.teams.sortedByDescending { it.elo }
        byElo.forEachIndexed { i, t -> t.net = i + 1 }
        val ap = lg.teams.sortedByDescending { apScore(it) }
        ap.forEachIndexed { i, t -> t.ap = if (i < 25) i + 1 else 0 }
    }

    private fun apScore(t: Team): Double = t.elo + 5.0 * t.w - 16.0 * t.l + t.prestige * 0.4

    fun computeExpectedWins(lg: League) {
        val reg = lg.games.filter { it.type == GameType.NONCONF || it.type == GameType.CONF || it.type == GameType.EVENT && it.label.endsWith("R1") }
        for (t in lg.teams) t.expWins = 0.0
        for (g in reg) {
            val h = lg.team(g.home); val a = lg.team(g.away)
            val p = winProbFor(lg, g, h.id)
            h.expWins += p
            a.expWins += 1 - p
        }
        // Two more event games for event teams, roughly a coin flip each.
        for (ev in lg.events) for (id in ev.teams) lg.team(id).expWins += 1.0
    }

    fun power(t: Team): Int = (50 + (t.elo - 1500) / 9).toInt().coerceIn(1, 99)

    /** Quadrant thresholds scaled to this league's size. */
    fun quadrant(lg: League, oppNet: Int, site: String): Int {
        val s = lg.teams.size / 364.0
        val q1 = when (site) { "vs" -> 30; "N" -> 50; else -> 75 } * s
        val q2 = when (site) { "vs" -> 75; "N" -> 100; else -> 135 } * s
        val q3 = when (site) { "vs" -> 160; "N" -> 200; else -> 240 } * s
        return when { oppNet <= q1 -> 1; oppNet <= q2 -> 2; oppNet <= q3 -> 3; else -> 4 }
    }

    fun quadRecord(lg: League, t: Team, q: Int): String {
        var w = 0; var l = 0
        for (g in lg.games) {
            if (!g.played || !g.involves(t.id)) continue
            val opp = lg.team(g.opp(t.id))
            if (quadrant(lg, opp.net, g.site(t.id)) != q) continue
            if (g.winner() == t.id) w++ else l++
        }
        return "$w-$l"
    }

    /** Wins above what a bubble team would expect against the same schedule. */
    fun wab(lg: League, t: Team): Double {
        val bubble = lg.teams.sortedBy { it.net }.getOrNull(44)?.elo ?: 1500.0
        var v = 0.0
        for (g in lg.games) {
            if (!g.played || !g.involves(t.id)) continue
            val opp = lg.team(g.opp(t.id))
            val site = when { g.neutral -> 0; g.home == t.id -> 1; else -> -1 }
            val p = winProb(bubble, opp.elo, site)
            v += (if (g.winner() == t.id) 1.0 else 0.0) - p
        }
        return v
    }

    fun sosRank(lg: League, t: Team): Int {
        fun sos(team: Team): Double {
            val opps = lg.games.filter { it.played && it.involves(team.id) }.map { lg.team(it.opp(team.id)).elo }
            return if (opps.isEmpty()) 0.0 else opps.average()
        }
        val mine = sos(t)
        return lg.teams.count { sos(it) > mine } + 1
    }
}

object Records {
    private val DEFS = listOf("Points in a game", "Rebounds in a game", "Assists in a game", "Threes in a game", "Steals in a game", "Blocks in a game")

    fun ensure(lg: League) {
        if (lg.records.isEmpty()) DEFS.forEach { lg.records += RecordEntry(it, 0, "", "") }
    }

    fun checkGame(lg: League, p: Player, t: Team, s: StatLine) {
        ensure(lg)
        val vals = intArrayOf(s.pts, s.reb, s.ast, s.tpm, s.stl, s.blk)
        for (i in vals.indices) {
            val r = lg.records[i]
            if (vals[i] > r.value) {
                r.value = vals[i]; r.holder = p.name; r.detail = "${t.school} · ${lg.season}"
            }
        }
    }
}
