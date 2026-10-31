package com.nh.hoopscoach

import com.nh.hoopscoach.game.*
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * A logic sweep: plays careers (college, today's pros, a past era) and checks that nothing in the
 * league is impossible: rosters, players, records, box scores, the playoffs, awards, the draft,
 * contracts and the text the game writes.
 */
class SweepTest {
    private val problems = mutableListOf<String>()
    private fun bad(what: String) { if (problems.size < 80 && what !in problems) problems += what }

    private fun stats(tag: String, p: Player, s: StatLine) {
        if (s.fgm > s.fga || s.tpm > s.tpa || s.ftm > s.fta || s.tpm > s.fgm) bad("$tag ${p.name}: made more than taken")
        if (s.pts != 2 * s.fgm + s.tpm + s.ftm) bad("$tag ${p.name}: points ${s.pts} don't add up (${s.fgm} FG, ${s.tpm} 3PT, ${s.ftm} FT)")
        if (s.gs > s.gp) bad("$tag ${p.name}: more starts than games")
        if (s.gp > 0 && s.mpg > 53) bad("$tag ${p.name}: ${s.mpg} minutes a game")
        if (listOf(s.pts, s.orb, s.drb, s.ast, s.stl, s.blk, s.tov, s.pf, s.sec).any { it < 0 }) bad("$tag ${p.name}: a negative stat")
    }

    private fun text(tag: String, t: String) {
        val l = t.lowercase()
        if ("null" in l.split(" ", ",", ".", ":") || "nan" in l.split(" ", ",", ".", ":") || "  " in t || " ," in t || " ." in t || t.contains("{") || Regex("\\$[a-zA-Z]").containsMatchIn(t))
            bad("$tag text: \"$t\"")
        if (Regex("\\b1 (games|wins|losses|points|seasons|years|teams|rebounds|assists)\\b").containsMatchIn(t)) bad("$tag plural: \"$t\"")
        if (Regex("\\bthe the\\b", RegexOption.IGNORE_CASE).containsMatchIn(t)) bad("$tag doubled word: \"$t\"")
    }

    private fun league(tag: String, lg: League) {
        val seen = mutableMapOf<Int, Int>()
        for (t in lg.teams) {
            if (lg.pro && t.roster.size !in 10..15) bad("$tag ${t.name}: ${t.roster.size} players")
            // College rosters thin out in the summer (graduation, the draft) until recruits arrive.
            if (!lg.pro && lg.phase != Phase.OFFSEASON && t.roster.size !in 8..17) bad("$tag ${t.name}: ${t.roster.size} players")
            for (pid in t.roster) {
                val p = lg.playerOrNull(pid)
                if (p == null) { bad("$tag ${t.name}: missing player $pid"); continue }
                seen[pid]?.let { bad("$tag ${p.name} on two teams") }
                seen[pid] = t.id
                if (p.teamId != t.id) bad("$tag ${p.name}: teamId ${p.teamId} but on ${t.name}")
            }
            if (t.w < 0 || t.l < 0) bad("$tag ${t.name}: negative record")
            if (t.name.isBlank()) bad("$tag a team with no name")
        }
        val names = mutableMapOf<String, Int>()
        for (p in lg.players) {
            if (p.first.isBlank() && p.last.isBlank()) bad("$tag a player with no name")
            if (p.age !in 16..46) bad("$tag ${p.name}: age ${p.age}")
            if (p.ovr !in 25..99 || p.pot !in 25..99) bad("$tag ${p.name}: ovr ${p.ovr} pot ${p.pot}")
            if (p.pot < p.ovr) bad("$tag ${p.name}: ceiling ${p.pot} below overall ${p.ovr}")
            if (p.attrs.any { it !in 1..99 }) bad("$tag ${p.name}: attribute out of range")
            if (p.pos2 != null && p.pos2 !in Ratings.secondOptions(p.pos)) bad("$tag ${p.name}: second position ${p.pos2}")
            stats(tag, p, p.stats)
            stats("$tag playoffs", p, p.tourney)
            if (p.teamId >= 0) names[p.name] = (names[p.name] ?: 0) + 1
        }
        val h = lg.playerOrNull(lg.heroId)
        if (h != null && h.teamId >= 0 && lg.teams.count { h.id in it.roster } != 1) bad("$tag the user's player isn't on exactly one team")
        if (lg.pro) {
            // Real people only once.
            for ((n, c) in names) if (c > 1 && (lg.proState?.era ?: "TODAY") != "TODAY" && EraData.draft(lg.season).any { it.name == n }) bad("$tag $n appears $c times")
        }
        for (g in lg.games.filter { it.played }) {
            if (g.hs == g.aws) bad("$tag game ${g.id} ended in a tie")
            if (g.hs < 30 || g.aws < 30 || g.hs > 200 || g.aws > 200) bad("$tag game ${g.id}: ${g.hs}-${g.aws}")
            g.box?.let { b ->
                if (b.home.sumOf { it.s.pts } != g.hs || b.away.sumOf { it.s.pts } != g.aws) bad("$tag game ${g.id}: box points don't match the score")
            }
        }
        for (n in lg.news.takeLast(150)) text("$tag news", n.text)
        lg.road?.feed?.takeLast(60)?.forEach { text("$tag post", it.text) }
        lg.road?.press?.let { text("$tag press", it.prompt) }
        lg.road?.growthNote?.let { if (it.isNotEmpty()) text("$tag growth", it) }
    }

    private fun proChecks(tag: String, lg: League) {
        val ps = lg.proState ?: return
        league(tag, lg)
        val games = ProLeague.seasonGames(lg)
        if (lg.phase == Phase.OFFSEASON || lg.phase == Phase.PLAYOFFS) for (t in lg.teams) {
            if (t.w + t.l != games) bad("$tag ${t.name}: ${t.w}-${t.l} in a $games-game season")
        }
        if (ps.champion >= 0 && ps.series.firstOrNull { it.round == 4 }?.winner != ps.champion) bad("$tag the champion didn't win the Finals")
        for (s in ps.series) if (s.done && s.hiWins != s.bestOf / 2 + 1 && s.loWins != s.bestOf / 2 + 1) bad("$tag a finished series without a winner")
        val honors = NbaAwards.of(lg)
        for (a in listOf("MVP", "Rookie of the Year", "Finals MVP", "Defensive Player of the Year")) if (honors.count { it.award == a } > 1) bad("$tag two ${a}s")
        if (honors.filter { it.award.startsWith("All-NBA") }.map { it.pid }.let { it.size != it.toSet().size }) bad("$tag a player on two All-NBA teams")
        val r = lg.road
        r?.contract?.let { c -> if (c.salary <= 0 || c.years < 0) bad("$tag contract ${c.salary} for ${c.years} years") }
        if (r != null && r.leadership !in 0..100) bad("$tag leadership ${r.leadership}")
    }

    private fun proCareer(tag: String, lg: League, seasons: Int) {
        var guard = 0
        while (lg.phase == Phase.OFFSEASON && guard++ < 10) ProCareer.next(lg)
        repeat(seasons) { season ->
            guard = 0
            while (lg.phase == Phase.REGULAR && guard++ < 200) {
                Sim.playDay(lg); lg.road!!.press?.let { Road.answer(lg, Rng.int(0, it.answers.size - 1)) }
                if (lg.day % 30 == 0) proChecks("$tag ${lg.season} day ${lg.day}", lg)
            }
            guard = 0
            while (lg.phase == Phase.PLAYOFFS && guard++ < 120) Sim.playDay(lg)
            proChecks("$tag ${lg.season} end", lg)
            if (lg.road!!.ended) return
            guard = 0
            while (lg.phase == Phase.OFFSEASON && guard++ < 10) {
                val r = lg.road!!
                if (lg.proState!!.step == ProStep.FREE_AGENCY && (r.contract?.years ?: 0) <= 0 && r.proOffers.isNotEmpty()) ProCareer.signWith(lg, 0, 2, false, false)
                ProCareer.next(lg)
            }
            if (lg.phase == Phase.OFFSEASON) { bad("$tag stuck in the offseason (step ${lg.proState!!.step})"); return }
            proChecks("$tag ${lg.season} start", lg)
            val back = SaveIO.decode(SaveIO.encode(lg))
            if (back.players.size != lg.players.size || back.teams.size != lg.teams.size) bad("$tag the save lost something")
        }
    }

    @Test
    fun sweep() {
        Rng.r = Random(31)
        val college = ProTest().toDraft(21)
        league("college ${college.season}", college)
        proCareer("today", ProLeague.create(college), 3)
        Rng.r = Random(32)
        proCareer("1986", ProLeague.create(ProTest().toDraft(22), Era.Y1986), 4)
        println("SWEEP: ${problems.size} problems\n" + problems.joinToString("\n"))
        assertTrue("no problems:\n" + problems.joinToString("\n"), problems.isEmpty())
    }
}
