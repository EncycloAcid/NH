package com.nh.hoopscoach.game

import kotlin.math.roundToInt

object Awards {

    fun score(p: Player, t: Team): Double {
        val s = p.stats
        if (s.gp == 0) return 0.0
        val eff = s.ppg + 1.15 * s.rpg + 1.5 * s.apg + 2.0 * s.spg + 2.0 * s.bpg - 1.2 * s.per(s.tov) +
            (s.fgPct - 0.45) * 20 * (s.fga.toDouble() / s.gp / 10)
        return eff + t.winPct * 6 + p.ovr * 0.08
    }

    fun defScore(p: Player): Double {
        val s = p.stats
        if (s.gp == 0) return 0.0
        return 3 * s.spg + 3 * s.bpg + 0.6 * s.per(s.drb) + (p[Attr.PER] + p[Attr.IDF] + p[Attr.STL] + p[Attr.BLK]) / 40.0
    }

    fun line(p: Player): String = "%.1f ppg · %.1f rpg · %.1f apg".format(p.stats.ppg, p.stats.rpg, p.stats.apg)

    /** Everyone eligible for national awards, best first by the award score. */
    fun ladder(lg: League): List<Pair<Player, Team>> = eligible(lg).sortedByDescending { score(it.first, it.second) }

    private fun eligible(lg: League, ids: Collection<Int>? = null): List<Pair<Player, Team>> {
        val out = mutableListOf<Pair<Player, Team>>()
        for (t in lg.teams) for (pid in t.roster) {
            if (ids != null && pid !in ids) continue
            val p = lg.player(pid)
            if (t.gp > 0 && p.stats.gp >= t.gp * 0.6) out += p to t
        }
        return out
    }

    private fun give(lg: League, list: MutableList<AwardWinner>, award: String, p: Player, t: Team) {
        list += AwardWinner(award, p.id, p.name, t.id, Reports.awardLine(award, p, t))
        p.awards += "${lg.season} $award"
        if (p.id == lg.heroId && lg.road != null) Alerts.add(lg, "Honored", "You're on the list: ${lg.season} $award.", "award")
    }

    /** Prefer one player per position, then fill with the next-best available. */
    private fun balancedTeam(pool: List<Pair<Player, Team>>, n: Int): List<Pair<Player, Team>> {
        val ranked = pool.sortedByDescending { score(it.first, it.second) }.toMutableList()
        val out = mutableListOf<Pair<Player, Team>>()
        for (pos in Pos.entries) {
            val i = ranked.indexOfFirst { it.first.pos == pos }
            if (i >= 0) out += ranked.removeAt(i)
        }
        while (out.size < n && ranked.isNotEmpty()) out += ranked.removeAt(0)
        return out.take(n)
    }

    /** End of the regular season: conference honors. */
    fun conference(lg: League) {
        val out = mutableListOf<AwardWinner>()
        for (c in lg.confs) {
            val pool = eligible(lg).filter { it.second.confId == c.id }
            if (pool.isEmpty()) continue
            val ranked = pool.sortedByDescending { score(it.first, it.second) }
            give(lg, out, "Conf POY", ranked[0].first, ranked[0].second)
            balancedTeam(pool, 5).forEach { give(lg, out, "All-Conf 1st", it.first, it.second) }
            // DPOY: prefer a defender who also played winning basketball, not a pure block specialist on a bad team.
            pool.maxByOrNull { defScore(it.first) + it.second.winPct * 4 }?.let { give(lg, out, "Conf DPOY", it.first, it.second) }
            pool.filter { it.first.year == 1 }.maxByOrNull { score(it.first, it.second) }?.let { give(lg, out, "Conf FOY", it.first, it.second) }
            val coty = c.teamIds.map { lg.team(it) }.maxByOrNull { it.w - it.expWins + it.cw * 0.2 }
            if (coty != null) {
                val coach = lg.coachOf(coty)
                coach.confCoty++
                out += AwardWinner("Conf COTY", -coach.id, coach.name, coty.id, Reports.coachAwardLine(coty, 0))
            }
        }
        lg.confAwards = out
    }

    /** After the title game: national honors and tournament awards. */
    fun national(lg: League) {
        val out = mutableListOf<AwardWinner>()
        val pool = eligible(lg)
        val ranked = pool.sortedByDescending { score(it.first, it.second) }
        if (ranked.isNotEmpty()) give(lg, out, "Nat MVP", ranked[0].first, ranked[0].second)
        val aa = balancedTeam(pool, 15)
        aa.take(5).forEach { give(lg, out, "AA 1st", it.first, it.second) }
        aa.drop(5).take(5).forEach { give(lg, out, "AA 2nd", it.first, it.second) }
        aa.drop(10).take(5).forEach { give(lg, out, "AA 3rd", it.first, it.second) }
        pool.maxByOrNull { defScore(it.first) + it.second.winPct * 4 }?.let { give(lg, out, "DPOY", it.first, it.second) }
        pool.filter { it.first.year == 1 }.maxByOrNull { score(it.first, it.second) }?.let { give(lg, out, "FOY", it.first, it.second) }
        for (pos in Pos.entries) pool.filter { it.first.pos == pos }.maxByOrNull { score(it.first, it.second) }?.let { give(lg, out, "Pos OY", it.first, it.second) }

        // Tournament awards from NCAA stats. MOP is almost always from the champion.
        val ncaa = lg.ncaa
        if (ncaa != null && ncaa.champion >= 0) {
            val f4 = ncaa.rounds.getOrNull(4)?.filter { it >= 0 } ?: emptyList()
            val tp = f4.flatMap { tid -> lg.team(tid).roster.map { lg.player(it) to lg.team(tid) } }
                .filter { it.first.tourney.gp > 0 }
                .sortedByDescending { (p, t) -> tScore(p) + if (t.id == ncaa.champion) 8 else 0 }
            tp.firstOrNull()?.let { (p, t) ->
                out += AwardWinner("MOP", p.id, p.name, t.id, Reports.awardLine("MOP", p, t)); p.awards += "${lg.season} MOP"
            }
            // All-Tournament: prefer the champion's stars, then the rest of the Final Four.
            val allT = tp.sortedByDescending { (p, t) -> tScore(p) + if (t.id == ncaa.champion) 6 else 2 }.take(5)
            allT.forEach { (p, t) -> out += AwardWinner("All-Tourn", p.id, p.name, t.id, Reports.awardLine("All-Tourn", p, t)); p.awards += "${lg.season} All-Tourn" }
        }

        // Coach of the year: biggest over-performance, with a bump for March.
        val best = lg.teams.maxByOrNull { (it.w - it.expWins) + tourneyRound(lg, it.id) * 1.2 }
        if (best != null) {
            val c = lg.coachOf(best)
            c.natCoty++
            lg.cotyCoachId = c.id
            out += AwardWinner("COTY", -c.id, c.name, best.id, Reports.coachAwardLine(best, tourneyRound(lg, best.id)))
        }
        lg.seasonAwards = out
    }

    private fun tScore(p: Player): Double {
        val s = p.tourney
        return s.ppg + 1.1 * s.rpg + 1.4 * s.apg + 1.5 * (s.spg + s.bpg) + s.gp * 1.5
    }

    /** 0 = missed, 1 = made the field (First Four or Round of 64) ... 6 = runner-up, 7 = champion. */
    fun tourneyRound(lg: League, teamId: Int): Int {
        val b = lg.ncaa ?: return 0
        if (b.champion == teamId) return 7
        var reached = if (b.playInOf(teamId) != null) 1 else 0
        for ((i, r) in b.rounds.withIndex()) if (teamId in r) reached = i + 1
        return reached
    }

    /** How a team's season ended, NIT included. */
    fun resultFor(lg: League, teamId: Int): String {
        val round = tourneyRound(lg, teamId)
        val b = lg.ncaa
        if (round == 1 && b != null && teamId !in b.rounds[0]) return "First Four"
        if (round > 0) return resultLabel(round)
        val nit = lg.nit
        if (nit != null && nit.hasTeam(teamId)) {
            if (nit.champion == teamId) return "NIT Champion"
            return when (nit.rounds.indexOfLast { teamId in it }) {
                4 -> "NIT Runner-Up"; 3 -> "NIT Semifinals"; 2 -> "NIT Quarterfinals"; 1 -> "NIT Second Round"; else -> "NIT First Round"
            }
        }
        return resultLabel(0)
    }

    fun resultLabel(round: Int): String = when (round) {
        7 -> "National Champion"; 6 -> "Runner-Up"; 5 -> "Final Four"; 4 -> "Elite Eight"; 3 -> "Sweet 16"
        2 -> "Round of 32"; 1 -> "Round of 64"; else -> "Missed Tournament"
    }

    fun badge(award: String): String = award.substringAfter(' ').let { if (it.firstOrNull()?.isDigit() == true) award else it }

    @Suppress("unused")
    fun pct(v: Double) = (v * 100).roundToInt()
}
