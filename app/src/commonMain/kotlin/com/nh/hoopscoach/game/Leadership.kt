package com.nh.hoopscoach.game

import kotlin.math.roundToInt

/**
 * Leading your team. A star changes games beyond his own shots: with the user's player on the
 * floor, his teammates' shots get easier, by how far he's above the team's best eight (up to 12
 * points) and how much he leads. Leadership grows with seasons as a starter, winning beyond the
 * team's preseason projection, and humble answers; it falls with brash ones and a new team.
 */
object Leadership {
    /** Teammates' added make chance with him on the floor, for the side whose roster is [roster]. */
    fun lift(lg: League, roster: List<Player>): Double {
        val r = lg.road ?: return 0.0
        val h = roster.firstOrNull { it.id == lg.heroId } ?: return 0.0
        val others = roster.filter { it.id != h.id }.map { it.ovr }.sortedDescending().take(7)
        if (others.isEmpty()) return 0.0
        val above = (h.ovr - others.average()).coerceIn(0.0, 12.0)
        return above * 0.0013 * (0.6 + r.leadership / 125.0)
    }

    fun label(l: Int) = when { l >= 80 -> "Franchise leader"; l >= 60 -> "Captain"; l >= 40 -> "Respected voice"; l >= 20 -> "Finding his voice"; else -> "New in the room" }

    /** The team's projected wins this season (from its roster before the season), and where it's headed. */
    fun projection(lg: League, t: Team): Pair<Int, Int> {
        val games = if (lg.pro) ProLeague.seasonGames(lg) else (t.w + t.l).coerceAtLeast(1)
        val proj = t.expWins.roundToInt()
        val pace = if (t.w + t.l == 0) proj else (t.w.toDouble() / (t.w + t.l) * (if (lg.pro) games else t.w + t.l)).roundToInt()
        return proj to pace
    }

    /** End of a season: leadership moves, and a line for his story when he carried a team past its projection. */
    fun seasonEnd(lg: League) {
        val r = lg.road ?: return
        val h = lg.playerOrNull(lg.heroId) ?: return
        if (h.teamId < 0) return
        val t = lg.team(h.teamId)
        val s = h.stats
        val starter = s.gp > 0 && s.gs * 10 >= s.gp * 6
        val beat = if (t.expWins > 0) t.w - t.expWins else 0.0
        var delta = (if (starter) 8 else 2) + (beat.coerceIn(-10.0, 12.0) * 0.6).roundToInt()
        if (r.persona <= -2) delta += 3
        if (r.persona >= 4) delta -= 3
        r.leadership = (r.leadership + delta).coerceIn(0, 100)
        if (t.expWins > 0 && beat >= 6 && starter) {
            lg.news += News(lg.season, lg.day, "${h.name} led the ${t.name} to ${t.w} wins, ${beat.roundToInt()} more than projected", true)
        }
    }

    /** A new team: he has to earn the room again. */
    fun newTeam(lg: League) { lg.road?.let { it.leadership = (it.leadership * 2 / 3) } }
}
