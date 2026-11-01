package com.nh.hoopscoach.game

import kotlin.math.roundToInt

/**
 * College in an era career: each season, the real players who were in school then (everyone who
 * went in the first 60 picks of a draft from 1986 on) are on their real schools, in their real
 * class. Their ratings follow where they were drafted and when: a future No. 1 pick is a top
 * player by his last season. Made-up players fill the rest, as in today's game.
 */
object EraCollege {
    /** Places this season's real players. Called when the league is made and at each new season. */
    fun populate(lg: League) {
        if (lg.era == "TODAY" || lg.pro) return
        val bySchool = lg.teams.associateBy { it.school }
        val touched = mutableSetOf<Team>()
        for (c in EraData.collegeSeason(lg.season)) {
            if (c.id in lg.eraIds) continue
            val t = bySchool[c.ours] ?: continue
            val pick = EraData.pickOf(c.id) ?: continue
            val pos = pick.pos ?: when { c.rpg >= 8 -> Pos.C; c.rpg >= 6 -> Pos.PF; c.apg >= 4 -> Pos.PG; c.ppg >= 12 -> Pos.SG; else -> Pos.SF }
            // On the college game's scale: about 95 for a No. 1 pick in his last college season, 88 for No. 60,
            // and three and a half less for each year before (in-game growth makes up the rest).
            val atDraft = (95 - (pick.pick - 1) * 0.12).roundToInt()
            val target = (atDraft - (pick.year - c.season) * 3.5).roundToInt().coerceIn(62, 95)
            val p = LeagueGen.makePlayer(lg, pos, c.cls.coerceIn(1, 4), target, t.id)
            val parts = pick.name.split(" ", limit = 2)
            p.first = parts[0]
            p.last = parts.getOrElse(1) { "" }
            p.country = null
            p.pot = maxOf(p.ovr + 2, atDraft + 3 + if (pick.peak >= 95) 3 else 0).coerceAtMost(99)
            // Make room: the weakest made-up scholarship player goes.
            if (t.roster.size >= 13) {
                lg.rosterOf(t).filter { it.id != lg.heroId && it.id !in lg.eraIds.values && !it.walkOn }.minByOrNull { it.ovr }
                    ?.let { cut -> t.roster.remove(cut.id); lg.removePlayer(cut) }
            }
            if (t.roster.any { lg.player(it).number == p.number }) p.number = Recruiting.freeNumber(lg, t)
            lg.addPlayer(p)
            t.roster += p.id
            lg.eraIds[c.id] = p.id
            touched += t
        }
        for (t in touched) Gameplan.autoAllocate(lg, t)
        lg.reindex()
    }

    /** A new college league that starts in [era]'s season, with its real players. */
    fun newLeague(era: Era): League {
        val lg = LeagueGen.newLeague(era.year)
        lg.era = era.name
        populate(lg)
        return lg
    }
}
