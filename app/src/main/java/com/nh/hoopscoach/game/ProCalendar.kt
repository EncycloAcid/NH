package com.nh.hoopscoach.game

import kotlin.math.roundToInt

/**
 * The NBA Cup: three groups of five in each conference, four group games on Cup nights early in
 * the season (they count in the standings), then an eight-team knockout. Group winners and one
 * wild card per conference go through. Knockout games are extra: they don't count in the
 * standings or the season's stats, so every team still plays 83.
 */
object NbaCup {
    val ROUNDS = listOf("Quarterfinal", "Semifinal", "Final")

    fun groupName(g: Int) = "${if (g < 3) "East" else "West"} Group ${'A' + g % 3}"
    fun groupLabel(g: Int) = "NBA Cup · ${groupName(g)}"

    /** Groups drawn from five tiers of three by team strength, so each group gets one team from each tier. */
    fun draw(lg: League): List<List<Int>> {
        val out = mutableListOf<List<Int>>()
        for (c in 0..1) {
            val ranked = lg.teams.filter { it.confId == c }.sortedByDescending { it.elo }.map { it.id }
            val groups = List(3) { mutableListOf<Int>() }
            ranked.chunked(3).forEach { tier -> tier.shuffled(Rng.r).forEachIndexed { g, id -> groups[g] += id } }
            out += groups
        }
        return out
    }

    /** Five Cup nights: in each group of five, two games a night and one team rests. */
    fun groupNights(groups: List<List<Int>>): List<List<Pair<Int, Int>>> {
        val nights = List(5) { mutableListOf<Pair<Int, Int>>() }
        for (g in groups) {
            val ring = (g + List(6 - g.size.coerceAtMost(6)) { -1 }).toMutableList()
            for (rd in 0 until 5) {
                for (i in 0 until 3) {
                    val a = ring[i]
                    val b = ring[5 - i]
                    if (a >= 0 && b >= 0) nights[rd] += a to b
                }
                ring.add(1, ring.removeAt(5))
            }
        }
        return nights
    }

    class Row(val teamId: Int, var w: Int = 0, var l: Int = 0, var diff: Int = 0)

    private fun order(lg: League) = compareByDescending<Row> { it.w }.thenByDescending { it.diff }.thenByDescending { lg.team(it.teamId).elo }

    /** A group's table: wins, then point differential. */
    fun table(lg: League, g: Int): List<Row> {
        val ids = lg.proState?.cupGroups?.getOrNull(g) ?: return emptyList()
        val rows = ids.associateWith { Row(it) }
        val label = groupLabel(g)
        for (game in lg.games) {
            if (!game.played || game.label != label) continue
            val h = rows[game.home] ?: continue
            val a = rows[game.away] ?: continue
            val margin = game.hs - game.aws
            if (margin > 0) { h.w++; a.l++ } else { a.w++; h.l++ }
            h.diff += margin
            a.diff -= margin
        }
        return rows.values.sortedWith(order(lg))
    }

    fun groupOf(lg: League, teamId: Int) = lg.proState?.cupGroups?.indexOfFirst { teamId in it } ?: -1

    fun active(lg: League) = (lg.proState?.cupQfDay ?: 0) > 0

    /** Group stage over: the night before the quarterfinals, the eight are set. */
    fun groupStageOver(lg: League) = active(lg) && lg.day >= lg.proState!!.cupQfDay - 1 && lg.proState!!.cup.isNotEmpty()

    /** Called at the end of every regular-season day. */
    fun endOfDay(lg: League) {
        val ps = lg.proState ?: return
        if (ps.cupQfDay <= 0) return
        when (lg.day) {
            ps.cupQfDay - 1 -> quarterfinals(lg)
            ps.cupQfDay -> { score(lg, 1); advance(lg, 1) }
            ps.cupSfDay -> { score(lg, 2); advance(lg, 2) }
            ps.cupFinalDay -> { score(lg, 3); crown(lg) }
        }
    }

    private fun quarterfinals(lg: League) {
        val ps = lg.proState ?: return
        ps.cup.clear()
        ps.cupSeeds.clear()
        for (c in 0..1) {
            val tables = (0 until 3).map { table(lg, c * 3 + it) }
            if (tables.any { it.isEmpty() }) return
            val winners = tables.map { it.first() }.sortedWith(order(lg))
            val wild = tables.flatMap { it.drop(1) }.sortedWith(order(lg)).first()
            val seeded = winners + wild
            seeded.forEachIndexed { i, row -> ps.cupSeeds[row.teamId] = i + 1 }
            listOf(0 to 3, 1 to 2).forEachIndexed { slot, (a, b) -> ps.cup += Series(1, c, slot, seeded[a].teamId, seeded[b].teamId, bestOf = 1) }
        }
        book(lg, 1, ps.cupQfDay)
        val u = lg.userTeamId
        lg.news += News(lg.season, lg.day,
            if (u in ps.cupSeeds) "The ${lg.user.name} are in the NBA Cup quarterfinals as the No. ${ps.cupSeeds[u]} seed in the ${lg.conf(lg.user.confId).abbr.lowercase().replaceFirstChar { it.uppercase() }}"
            else "NBA Cup: the knockout round is set. The ${lg.user.name} didn't make it", u in ps.cupSeeds)
    }

    private fun book(lg: League, round: Int, day: Int) {
        val ps = lg.proState ?: return
        for (s in ps.cup.filter { it.round == round }) {
            val g = Game(lg.nextGameId++, day, s.hi, s.lo, false, GameType.CUP, "NBA Cup · ${ROUNDS[round - 1]}")
            lg.addGame(g)
            s.gameIds += g.id
        }
    }

    private fun score(lg: League, round: Int) {
        val ps = lg.proState ?: return
        for (s in ps.cup.filter { it.round == round && !it.done }) {
            val g = s.gameIds.lastOrNull()?.let { lg.game(it) } ?: continue
            if (!g.played) continue
            if (g.winner() == s.hi) s.hiWins++ else s.loWins++
            if (s.involves(lg.userTeamId)) {
                val won = s.winner == lg.userTeamId
                val opp = lg.team(if (s.hi == lg.userTeamId) s.lo else s.hi)
                lg.news += News(lg.season, lg.day, "NBA Cup ${ROUNDS[round - 1].lowercase()}: the ${lg.user.name} ${if (won) "beat" else "fall to"} the ${opp.name}", true, g.id)
                if (!won) prize(lg, round)
            }
        }
    }

    private fun advance(lg: League, round: Int) {
        val ps = lg.proState ?: return
        val cur = ps.cup.filter { it.round == round }
        if (cur.isEmpty() || !cur.all { it.done }) return
        if (round == 1) {
            for (c in 0..1) {
                val (a, b) = cur.filter { it.conf == c }.sortedBy { it.slot }.map { it.winner }
                val (hi, lo) = if ((ps.cupSeeds[a] ?: 9) <= (ps.cupSeeds[b] ?: 9)) a to b else b to a
                ps.cup += Series(2, c, 0, hi, lo, bestOf = 1)
            }
            book(lg, 2, ps.cupSfDay)
        } else {
            val e = cur.first { it.conf == 0 }.winner
            val w = cur.first { it.conf == 1 }.winner
            val (hi, lo) = if (lg.team(e).winPct >= lg.team(w).winPct) e to w else w to e
            ps.cup += Series(3, -1, 0, hi, lo, bestOf = 1)
            book(lg, 3, ps.cupFinalDay)
        }
    }

    /** Prize money for the user's player, in thousands: more the further his team goes. */
    private fun prize(lg: League, round: Int, won: Boolean = false) {
        val r = lg.road ?: return
        val h = lg.playerOrNull(lg.heroId) ?: return
        if (h.teamId != lg.userTeamId) return
        r.earned += if (won) 515 else when (round) { 1 -> 51; 2 -> 103; else -> 206 }
    }

    private fun crown(lg: League) {
        val ps = lg.proState ?: return
        val f = ps.cup.firstOrNull { it.round == 3 && it.done } ?: return
        val champ = lg.team(f.winner)
        val other = lg.team(f.loser)
        ps.cupChampion = champ.id
        val mvp = lg.rosterOf(champ).maxByOrNull { Awards.score(it, champ) + Rng.gauss() * 3 }
        for (p in lg.rosterOf(champ)) p.awards += "${lg.season} NBA Cup winner"
        mvp?.let { it.awards += "${lg.season} NBA Cup MVP"; ps.cupMvp = it.name; NbaAwards.note(lg, "NBA Cup MVP", it, champ.id) }
        NbaAwards.cupTeam(lg, champ, mvp)
        val h = lg.playerOrNull(lg.heroId)
        val r = lg.road
        if (h != null && r != null && h.teamId == champ.id) {
            prize(lg, 3, won = true)
            val gain = (40_000 + r.followers * 0.03 * Road.damp(r.followers)).roundToInt()
            r.followers += gain
            ps.cupNote = "The ${champ.name} win the NBA Cup in Las Vegas" + (if (mvp?.id == h.id) ", and you're the Cup MVP" else "") +
                ". $515K in prize money, +${Road.count(gain)} followers."
        } else {
            ps.cupNote = "The ${champ.name} beat the ${other.name} to win the NBA Cup." + (mvp?.let { " Cup MVP: ${it.name}." } ?: "")
        }
        lg.news += News(lg.season, lg.day, "NBA Cup: the ${champ.name} beat the ${other.name} in Las Vegas" + (mvp?.let { ". Cup MVP: ${it.name}" } ?: ""), champ.id == lg.userTeamId)
    }

    /** Where the user's team stands in the Cup, in a line. */
    fun status(lg: League): String {
        val ps = lg.proState ?: return ""
        if (!active(lg)) return ""
        val u = lg.userTeamId
        if (ps.cupChampion >= 0) return ps.cupNote
        ps.cup.filter { it.involves(u) }.maxByOrNull { it.round }?.let { s ->
            val opp = lg.team(if (s.hi == u) s.lo else s.hi)
            return when {
                !s.done -> "${ROUNDS[s.round - 1]} vs the ${opp.name}" + if (s.round >= 2) " in Las Vegas" else ""
                s.winner == u -> "Through to the ${ROUNDS.getOrElse(s.round) { "title" }.lowercase()}"
                else -> "Out in the ${ROUNDS[s.round - 1].lowercase()} to the ${opp.name}"
            }
        }
        val g = groupOf(lg, u)
        if (g < 0) return ""
        val t = table(lg, g)
        val i = t.indexOfFirst { it.teamId == u }
        val row = t[i]
        if (ps.cup.isNotEmpty()) return "Out in the group stage at ${row.w}-${row.l}"
        return "${groupName(g)}: ${row.w}-${row.l}, ${ordinal(i + 1)} of ${t.size}"
    }

    private fun ordinal(n: Int) = "$n" + when { n % 100 in 11..13 -> "th"; n % 10 == 1 -> "st"; n % 10 == 2 -> "nd"; n % 10 == 3 -> "rd"; else -> "th" }
}

/** All-Star Saturday and the Rising Stars game, the weekend before the All-Star Game. */
object AllStarWeekend {
    fun run(lg: League) {
        val ps = lg.proState ?: return
        ps.weekend.clear()
        val pool = lg.teams.flatMap { t -> lg.rosterOf(t).filter { it.stats.gp > 0 && it.stats.gp * 2 >= t.gp }.map { it to t } }
        if (pool.size < 8) return
        val hid = lg.heroId

        // Rising Stars: first- and second-year players (from 1994).
        val young = if (Eras.of(lg).risingStars) pool.filter { it.first.proYears <= 1 }.sortedByDescending { Awards.score(it.first, it.second) }.take(20) else emptyList()
        if (young.size >= 4) {
            val mvp = young.maxBy { Awards.score(it.first, it.second) + Rng.gauss() * 5 }.first
            mvp.awards += "${lg.season} Rising Stars MVP"
            NbaAwards.note(lg, "Rising Stars MVP", mvp, mvp.teamId)
            val me = young.any { it.first.id == hid }
            ps.weekend += "Rising Stars: ${mvp.name} is MVP." + when {
                mvp.id == hid -> " That's you."
                me -> " You played in it."
                else -> ""
            }
            if (me) cheer(lg, if (mvp.id == hid) 0.025 else 0.008)
        }

        // 3-Point Contest: the eight best shooters, then a final three.
        val shooters = pool.map { it.first }.filter { it.stats.tpa >= it.stats.gp * 3 }
            .sortedByDescending { it[Attr.OUT] * 2.0 + it.stats.per(it.stats.tpm) * 4 }.take(8)
        if (shooters.size >= 3) {
            val first = shooters.associateWith { threes(it) }
            val finalists = first.entries.map { it.key to it.value + Rng.d() }.sortedByDescending { it.second }.take(3).map { it.first }
            val last = finalists.associateWith { threes(it) }
            val (champ, score) = last.entries.maxBy { it.value + Rng.d() }.let { it.key to it.value }
            champ.awards += "${lg.season} 3-Point Contest winner"
            NbaAwards.note(lg, "3-Point Contest winner", champ, champ.teamId, "$score of 34 in the final")
            val me = shooters.firstOrNull { it.id == hid }
            ps.weekend += "3-Point Contest: ${champ.name} wins the final with $score of 34." + when {
                champ.id == hid -> " You're the champion."
                me != null && me in finalists -> " You made the final with ${last[me]}."
                me != null -> " You shot ${first[me]} in the first round."
                else -> ""
            }
            if (me != null) cheer(lg, if (champ.id == hid) 0.04 else 0.01)
        }

        // Slam Dunk Contest: four young finishers, two dunks each, then a final two.
        val dunkers = pool.map { it.first }.filter { it.age <= 26 && it.pos != Pos.C }
            .map { it to it[Attr.INS] + Rng.gauss() * 4 }.sortedByDescending { it.second }.take(4).map { it.first }
        if (dunkers.size >= 2) {
            val first = dunkers.associateWith { dunk(it) + dunk(it) }
            val finalists = first.entries.map { it.key to it.value + Rng.d() }.sortedByDescending { it.second }.take(2).map { it.first }
            val last = finalists.associateWith { dunk(it) to dunk(it) }
            val champ = last.entries.maxBy { it.value.first + it.value.second + Rng.d() }
            champ.key.awards += "${lg.season} Slam Dunk Contest winner"
            NbaAwards.note(lg, "Slam Dunk Contest winner", champ.key, champ.key.teamId, "${champ.value.second} on his last dunk")
            val me = dunkers.firstOrNull { it.id == hid }
            ps.weekend += "Slam Dunk Contest: ${champ.key.name} wins it with a ${champ.value.second} on his last dunk." + when {
                champ.key.id == hid -> " That's you. The video is everywhere."
                me != null && me in finalists -> " You made the final."
                me != null -> " You went out in the first round with ${first[me]}."
                else -> ""
            }
            if (me != null) cheer(lg, if (champ.key.id == hid) 0.06 else 0.015)
        }
    }

    /** One round: four racks with a two-point ball, then a rack of money balls. Out of 34. */
    private fun threes(p: Player): Int {
        val make = (0.48 + (p[Attr.OUT] - 80) * 0.012).coerceIn(0.25, 0.82)
        var pts = 0
        repeat(4) { repeat(5) { k -> if (Rng.chance(make)) pts += if (k == 4) 2 else 1 } }
        repeat(5) { if (Rng.chance(make)) pts += 2 }
        return pts
    }

    /** Five judges, 6 to 10 each. */
    private fun dunk(p: Player) = (39 + (p[Attr.INS] - 80) * 0.25 + Rng.gauss() * 5).roundToInt().coerceIn(30, 50)

    private fun cheer(lg: League, share: Double) {
        val r = lg.road ?: return
        r.followers += (15_000 + r.followers * share * Road.damp(r.followers)).roundToInt()
    }
}

/** Lottery night: the 14 teams that missed the playoffs draw for the top four picks. */
object Lottery {
    /** Chances at No. 1, in tenths of a percent, worst record first. */
    val ODDS = listOf(140, 140, 140, 125, 105, 90, 75, 60, 45, 30, 20, 15, 10, 5)

    /** 1994 to 2018: steeper odds (25% for the worst record), and only the top three picks were drawn. */
    val ODDS_1994 = listOf(250, 199, 156, 119, 88, 63, 43, 28, 17, 11, 8, 7, 6, 5)

    /** How the lottery works in [era], in a sentence. */
    fun rules(era: Era) = when {
        era == Era.TODAY -> "The 14 teams that missed the playoffs drew for the top four picks. The three worst records had a 14% shot at No. 1."
        era.year >= 1994 -> "The 14 teams that missed the playoffs drew for the top three picks. The worst record had a 25% shot at No. 1."
        else -> "The teams that missed the playoffs drew for the top three picks, every team with the same chance."
    }

    /** The pick order for [teams] (worst first): the top picks drawn by the era's odds, the rest by record. */
    fun draw(teams: List<Int>, era: Era = Era.TODAY): List<Int> {
        val left = teams.toMutableList()
        val top = mutableListOf<Int>()
        val odds = when { era == Era.TODAY -> ODDS; era.year >= 1994 -> ODDS_1994; else -> List(14) { 10 } }
        repeat(minOf(if (era == Era.TODAY) 4 else 3, teams.size)) {
            val t = Rng.weighted(left) { odds.getOrElse(teams.indexOf(it)) { 1 }.toDouble() }
            top += t
            left.remove(t)
        }
        return top + left
    }

    /** "+5" when the balls moved a team up, "-2" when it slid. */
    fun move(lg: League, teamId: Int): Int {
        val ps = lg.proState ?: return 0
        return ps.lotteryPre.indexOf(teamId) - ps.lotteryPicks.indexOf(teamId)
    }
}

/** Awards night: the MVP vote and the rest of the season's honors. */
object AwardsNight {
    /** 100 voters rank five players, 10-7-5-3-1. Each sees the race a little differently. */
    fun mvpVote(lg: League, ladder: List<Pair<Player, Team>>): List<Vote> {
        val cands = ladder.take(12)
        if (cands.isEmpty()) return emptyList()
        val base = cands.map { Awards.score(it.first, it.second) }
        val spread = (base.first() - base.getOrElse(5) { base.last() }).coerceAtLeast(1.5)
        val pts = IntArray(cands.size)
        val firsts = IntArray(cands.size)
        repeat(100) {
            val seen = cands.indices.map { base[it] + Rng.gauss() * spread * 0.3 }
            val order = cands.indices.sortedByDescending { seen[it] }
            listOf(10, 7, 5, 3, 1).forEachIndexed { k, v -> order.getOrNull(k)?.let { i -> pts[i] += v; if (k == 0) firsts[i]++ } }
        }
        // Everyone who got a vote is listed, like the real results.
        return cands.indices.sortedByDescending { pts[it] * 1000 + firsts[it] }.filter { pts[it] > 0 }
            .map { i -> Vote(cands[i].first.id, cands[i].first.name, cands[i].second.id, pts[i], firsts[i]) }
    }

    /** Biggest jump from last season, for players who played real minutes both years. */
    fun mostImproved(lg: League, ladder: List<Pair<Player, Team>>): Pair<Player, Team>? =
        ladder.mapNotNull { (p, t) ->
            val prev = p.career.lastOrNull { it.cls == PRO_YEAR && it.season == lg.season - 1 }?.s ?: return@mapNotNull null
            if (prev.gp < 20 || p.stats.ppg < 12) return@mapNotNull null
            Triple(p, t, (p.stats.ppg - prev.ppg) + (p.stats.rpg - prev.rpg) * 0.5 + (p.stats.apg - prev.apg) * 0.7)
        }.maxByOrNull { it.third }?.takeIf { it.third >= 2.5 }?.let { it.first to it.second }

    /** The user's player's place in the MVP vote, or 0. */
    fun heroFinish(lg: League) = (lg.proState?.mvpVotes?.indexOfFirst { it.pid == lg.heroId } ?: -1) + 1
}
