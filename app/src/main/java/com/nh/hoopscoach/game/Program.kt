package com.nh.hoopscoach.game

import kotlin.math.roundToInt

/**
 * Pro pipeline: draft picks from the last four drafts help a program recruit.
 * Lottery picks count 3, other first-rounders 2, second-rounders 1.
 */
object Pipeline {
    const val DRAFTS = 4
    private const val CAP = 12

    /** The drafts that count: the four most recent. */
    fun recentSeasons(lg: League): List<Int> = lg.draftHistory.map { it.season }.distinct().sortedDescending().take(DRAFTS)

    fun picks(lg: League, teamId: Int): List<DraftPick> {
        val seasons = recentSeasons(lg).toSet()
        return lg.draftHistory.filter { it.teamId == teamId && it.season in seasons }
    }

    fun value(pick: Int) = when { pick <= 14 -> 3; pick <= 30 -> 2; else -> 1 }

    /** Pipeline points for every team, indexed by team id. */
    fun table(lg: League): IntArray {
        val seasons = recentSeasons(lg).toSet()
        val out = IntArray(lg.teams.size)
        for (d in lg.draftHistory) if (d.season in seasons && d.teamId in out.indices) out[d.teamId] += value(d.pick)
        return out
    }

    fun points(lg: League, teamId: Int) = picks(lg, teamId).sumOf { value(it.pick) }

    /** Extra recruiting pull: top recruits care most about getting to the league. */
    fun bonus(points: Int, topRecruit: Boolean) = points.coerceAtMost(CAP) * if (topRecruit) 0.018 else 0.01

    fun isTop(r: Recruit) = r.p.stars >= 4 || (r.transfer && r.p.ovr >= 75)
}

/**
 * Fan mood. Wins (especially at home, over ranked teams, in rivalries and in March) lift it,
 * losses sink it, and it drifts back toward what the program's stature supports. It sets
 * attendance and adds to, or takes from, the donor NIL pool.
 */
object Fans {
    fun baseline(t: Team) = 30 + t.prestige * 0.5

    fun ensure(t: Team) { if (t.fans <= 0.0) t.fans = baseline(t) }

    fun mood(fans: Double) = when {
        fans >= 88 -> "Electric"
        fans >= 72 -> "Buzzing"
        fans >= 55 -> "Steady"
        fans >= 40 -> "Restless"
        else -> "Apathetic"
    }

    /** Share of the arena a regular home game fills, before game-to-game noise. */
    fun expectedFill(lg: League, g: Game): Double {
        val home = lg.team(g.home)
        val away = lg.team(g.away)
        return (0.28 + home.fans / 100.0 * 0.72 + (if (away.ap > 0) 0.06 else 0.0) + (if (g.type == GameType.CONF) 0.02 else 0.0)).coerceIn(0.25, 1.0)
    }

    fun afterGame(lg: League, g: Game) {
        if (!g.neutral && g.att > 0) {
            val h = lg.team(g.home)
            h.homeGames++
            h.homeAtt += g.att
            if (g.att >= h.arena) h.sellouts++
        }
        val rivalry = lg.rivalry(g) != null
        for (id in intArrayOf(g.home, g.away)) {
            val t = lg.team(id)
            val opp = lg.team(g.opp(id))
            val won = g.winner() == id
            var d = if (won) 1.6 else -1.6
            if (!g.neutral && g.home == id) d *= 1.25
            if (won && opp.ap > 0) d += 1.5
            if (!won && opp.prestige + 15 < t.prestige) d -= 1.5
            if (won && t.streak >= 5) d += 0.5
            when (g.type) {
                GameType.NCAA -> d = if (won) 5.0 else -1.0
                GameType.NIT -> d = if (won) 1.5 else -0.5
                GameType.CONF_TOURNEY -> d = if (won) 2.0 else -1.0
                else -> {}
            }
            if (rivalry) d *= 2
            t.fans = (t.fans + d).coerceIn(5.0, 100.0)
        }
    }

    /** Daily pull back toward the program's usual level. */
    fun drift(lg: League) {
        for (t in lg.teams) t.fans += (baseline(t) - t.fans) * 0.025
    }

    /** A new season: part of last year's mood carries over. */
    fun newSeason(t: Team) {
        ensure(t)
        t.fans = baseline(t) + (t.fans - baseline(t)) * 0.6
        t.homeGames = 0
        t.homeAtt = 0
        t.sellouts = 0
    }

    /** Fans' share of the NIL pool: up to 20% more donor money when the arena is rocking, less when it isn't. */
    fun nilBoost(t: Team, donor: Int) = (donor * (t.fans - 50) * 0.004).roundToInt() / 5 * 5

    fun avgCrowd(t: Team) = if (t.homeGames == 0) 0 else (t.homeAtt / t.homeGames).toInt()
    fun avgFill(t: Team) = if (t.homeGames == 0 || t.arena <= 0) 0.0 else avgCrowd(t).toDouble() / t.arena
}

/**
 * Program legends. When a player leaves, his career at the school is scored: awards, titles,
 * Final Fours, scoring and where he was drafted. The best enter the program's Hall of Fame,
 * and the all-time greats have their jersey retired: no one wears that number again.
 */
object Legacy {
    const val HALL = 9
    const val RETIRE = 17

    private val AWARD_POINTS = mapOf(
        "Nat MVP" to 10, "AA 1st" to 5, "AA 2nd" to 3, "AA 3rd" to 2, "MOP" to 5, "DPOY" to 2,
        "Conf POY" to 3, "All-Conf 1st" to 1, "FOY" to 1,
    )
    private val AWARD_NAMES = mapOf(
        "Nat MVP" to "National Player of the Year", "AA 1st" to "First Team All-American", "AA 2nd" to "Second Team All-American",
        "AA 3rd" to "Third Team All-American", "MOP" to "Final Four Most Outstanding Player", "DPOY" to "Defensive Player of the Year",
        "Conf POY" to "Conference Player of the Year",
    )

    /** Scores a departing player's career at [t]; inducts him if it's good enough. */
    fun honorPlayer(lg: League, t: Team, p: Player) {
        val lines = p.career.filter { it.team == t.abbr }
        if (lines.isEmpty()) return
        val seasons = lines.map { it.season }.toSet()
        var score = 0
        val honors = mutableListOf<String>()
        val counts = HashMap<String, Int>()
        for (a in p.awards) {
            val season = a.substringBefore(' ').toIntOrNull() ?: continue
            val code = a.substringAfter(' ')
            if (season !in seasons) continue
            AWARD_POINTS[code]?.let { score += it; counts.merge(code, 1, Int::plus) }
            if (code.startsWith("Draft #")) {
                val pick = code.removePrefix("Draft #").toIntOrNull() ?: continue
                score += when { pick <= 5 -> 4; pick <= 14 -> 3; pick <= 30 -> 2; else -> 1 }
                honors += "No. $pick pick in the $season draft"
            }
        }
        honors.addAll(0, AWARD_NAMES.mapNotNull { (code, name) -> counts[code]?.let { n -> if (n > 1) "$n× $name" else name } })
        val titles = lg.history.filter { it.season in seasons && it.champion == t.id }.map { it.season }
        val f4 = lg.history.count { it.season in seasons && t.id in it.finalFour }
        score += titles.size * 4 + f4 * 2
        if (titles.isNotEmpty()) honors.add(0, "National champion ${titles.joinToString(", ")}")
        else if (f4 > 0) honors += "$f4 Final Four${if (f4 > 1) "s" else ""}"
        val tot = StatLine()
        lines.forEach { tot.add(it.s) }
        score += when { tot.pts >= 2000 -> 5; tot.pts >= 1500 -> 3; tot.pts >= 1000 -> 1; else -> 0 }
        if (lines.size >= 4) score += 1
        if (score < HALL) return
        val retired = score >= RETIRE
        val legend = Legend(
            p.name, p.pos, p.number, seasons.min(), seasons.max(),
            "%.1f ppg · %s points · %.1f rpg · %.1f apg".format(tot.ppg, "%,d".format(tot.pts), tot.rpg, tot.apg),
            honors.take(4), lg.season, retired, score = score,
        )
        induct(lg, t, legend)
    }

    /** A coach leaving a program after a great run there. */
    fun honorCoach(lg: League, t: Team, c: Coach) {
        val here = c.history.filter { it.teamId == t.id }
        if (here.isEmpty()) return
        val titles = here.count { it.result == "National Champion" }
        val f4 = here.count { it.result == "National Champion" || it.result == "Runner-Up" || it.result == "Final Four" }
        val w = here.sumOf { it.w }
        val l = here.sumOf { it.l }
        if (titles == 0 && f4 < 2 && w < 250) return
        if (t.legends.any { it.coach && it.name == c.name }) return
        val honors = listOfNotNull(
            if (titles > 0) "$titles national title${if (titles > 1) "s" else ""}" else null,
            if (f4 > 0) "$f4 Final Four${if (f4 > 1) "s" else ""}" else null,
        )
        induct(lg, t, Legend(c.name, null, -1, here.minOf { it.season }, here.maxOf { it.season }, "$w-$l in ${here.size} season${if (here.size > 1) "s" else ""}", honors, lg.season, coach = true, score = titles * 6 + f4 * 2))
    }

    private fun induct(lg: League, t: Team, legend: Legend) {
        t.legends += legend
        val mine = t.id == lg.userTeamId
        if (mine && !legend.coach) lg.offseason?.honors?.add(legend)
        val text = when {
            legend.retired -> "${t.school} retires No. ${legend.number} for ${legend.name}"
            legend.coach -> "${t.school} inducts coach ${legend.name} into its Hall of Fame"
            else -> "${t.school} inducts ${legend.name} into its Hall of Fame"
        }
        if (mine || legend.retired) lg.news += News(lg.season, lg.day, text, mine)
    }

    fun retiredNumbers(t: Team): Set<Int> = t.legends.filter { it.retired }.map { it.number }.toSet()
}
