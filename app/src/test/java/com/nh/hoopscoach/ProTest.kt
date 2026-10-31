package com.nh.hoopscoach

import com.nh.hoopscoach.game.Arch
import com.nh.hoopscoach.game.GameType
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.LeagueGen
import com.nh.hoopscoach.game.NilDeal
import com.nh.hoopscoach.game.Offseason
import com.nh.hoopscoach.game.Phase
import com.nh.hoopscoach.game.Pos
import com.nh.hoopscoach.game.ProCareer
import com.nh.hoopscoach.game.ProLeague
import com.nh.hoopscoach.game.ProSeason
import com.nh.hoopscoach.game.ProStep
import com.nh.hoopscoach.game.Rival
import com.nh.hoopscoach.game.Road
import com.nh.hoopscoach.game.Rng
import com.nh.hoopscoach.game.SaveIO
import com.nh.hoopscoach.game.Season
import com.nh.hoopscoach.game.Sim
import com.nh.hoopscoach.game.Summer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** From Draft Night to retirement: the pro league, its seasons and the user's whole pro career. */
class ProTest {

    /** A college career played until the user's player is drafted. */
    fun toDraft(seed: Int): League {
        Rng.r = Random(seed)
        var lg = LeagueGen.newLeague()
        val hero = Road.createHero(lg, Road.Spec("Malik", "Grant", Pos.SF, 79, 215, "Chicago", "IL", Arch.SLASHER, 5, 1))
        lg = Road.start(lg, hero, Road.offers(lg, hero).minBy { it.ahead }.teamId)
        var seasons = 0
        while (!lg.road!!.goingPro && !lg.road!!.ended && seasons++ < 5) {
            if (Road.needsDeal(lg)) Road.chooseDeal(lg, NilDeal.STEADY)
            var guard = 0
            while (lg.phase != Phase.WRAP_UP && guard++ < 120) { Season.playDay(lg); lg.road!!.press?.let { Road.answer(lg, 0) } }
            Offseason.begin(lg)
            val me = lg.playerOrNull(lg.heroId)!!
            val rank = Offseason.draftBoard(lg)[me.id]
            lg.road!!.declared = me.year < 4 && rank != null && rank <= 15
            Road.next(lg)
            if (lg.road!!.goingPro || lg.road!!.ended) break
            Road.next(lg)
            Summer.invites(lg).firstOrNull()?.let { Summer.attend(lg, it) }
            Road.next(lg)
        }
        return lg
    }

    @Test
    fun trades() {
        val college = toDraft(21)
        var lg = ProLeague.create(college)
        val r = lg.road!!
        while (lg.phase == Phase.OFFSEASON) ProCareer.next(lg)
        repeat(20) { Sim.playDay(lg); r.press?.let { Road.answer(lg, 0) } }
        val mood = com.nh.hoopscoach.game.Trades.mood(lg)
        println("mood ${mood.score} ${mood.label}: ${mood.reasons}")
        assertTrue("can ask before the deadline", com.nh.hoopscoach.game.Trades.canRequest(lg))
        val from = lg.userTeamId
        val n = com.nh.hoopscoach.game.Trades.request(lg)
        println("suitors: " + r.suitors.joinToString { s -> "${lg.team(s.teamId).name} (${s.role}) sends ${s.sends.map { lg.player(it).let { p -> "${p.name} ${p.ovr}" } }}" })
        assertTrue("teams call", n > 0)
        assertTrue("one request per window", !com.nh.hoopscoach.game.Trades.canRequest(lg))
        val pick = r.suitors.first()
        val coming = pick.sends.toList()
        assertTrue(com.nh.hoopscoach.game.Trades.accept(lg, 0))
        val h = lg.player(lg.heroId)
        assertEquals("on his new team", pick.teamId, h.teamId)
        assertEquals("the user follows him", pick.teamId, lg.userTeamId)
        assertEquals("contract moves with him", pick.teamId, r.contract!!.teamId)
        assertTrue("old team got the players", coming.all { lg.playerOrNull(it)?.teamId == from || lg.playerOrNull(it) == null })
        assertTrue("career lists both teams", r.proTeams.containsAll(listOf(from, pick.teamId)))
        assertTrue("trade recorded", lg.proState!!.trades.last().hero)
        println(r.tradeNote)
        lg = SaveIO.decode(SaveIO.encode(lg))
        while (lg.day < com.nh.hoopscoach.game.Trades.deadlineDay(lg) && lg.phase == Phase.REGULAR) Sim.playDay(lg)
        val ai = lg.proState!!.trades.filter { !it.hero && !it.offseason }
        ai.forEach { println("  " + com.nh.hoopscoach.game.Trades.describe(lg, it)) }
        assertTrue("deadline deals", ai.size >= 2)
        assertTrue("no asking after the deadline", !com.nh.hoopscoach.game.Trades.canRequest(lg))
        assertTrue("rosters legal", lg.teams.all { t -> t.roster.size in 13..15 && t.roster.all { lg.player(it).teamId == t.id } })
        var guard = 0
        while (lg.phase != Phase.OFFSEASON && guard++ < 120) Sim.playDay(lg)
        ProCareer.next(lg)
        assertTrue("summer deals", lg.proState!!.trades.any { it.offseason && it.season == lg.season })
        assertTrue("can ask again in the offseason", com.nh.hoopscoach.game.Trades.canRequest(lg))
        com.nh.hoopscoach.game.Trades.request(lg)
        com.nh.hoopscoach.game.Trades.withdraw(lg)
        assertTrue("withdrawn", lg.road!!.suitors.isEmpty() && lg.player(lg.heroId).teamId == pick.teamId)
    }

    private fun playSeason(lg: League) {
        var guard = 0
        while (lg.phase != Phase.OFFSEASON && guard++ < 200) { Sim.playDay(lg); lg.road!!.press?.let { Road.answer(lg, 0) } }
    }

    @Test
    fun extras() {
        var lg = ProLeague.create(toDraft(21))
        val r = lg.road!!
        val rb = com.nh.hoopscoach.game.RecordBook
        assertEquals("records book seeded", 18, lg.proState!!.records.size)
        while (lg.phase == Phase.OFFSEASON) ProCareer.next(lg)
        // Recruit a star: push for a trade before the deadline.
        repeat(15) { Sim.playDay(lg) }
        val sr = com.nh.hoopscoach.game.StarRecruit
        val odds = sr.odds(lg)
        assertEquals("title odds add up", 1.0, odds.values.sum(), 0.001)
        val targets = sr.tradeTargets(lg)
        println("title odds ${"%.1f".format(odds[lg.userTeamId]!! * 100)}%; trade targets ${targets.map { "${it.name} ${it.ovr} (${lg.team(it.teamId).abbr}) -> ${"%.1f".format(sr.oddsWith(lg, it) * 100)}%" }}")
        if (targets.isNotEmpty()) { println("push: " + sr.push(lg, targets.first().id)); assertTrue("one push a season", !sr.canPush(lg)) }
        // A record night.
        val lines = arrayOf(LinkedHashMap<Int, com.nh.hoopscoach.game.StatLine>(), LinkedHashMap())
        lines[0][lg.heroId] = com.nh.hoopscoach.game.StatLine(gp = 1, sec = 2800, pts = 101)
        val before = com.nh.hoopscoach.game.AllTime.heroScore(lg)
        rb.afterGame(lg, lines)
        val pts = lg.proState!!.records.first { it.key == "game_pts" }
        assertTrue("he holds the points record", pts.mine && pts.value == 101.0)
        assertTrue("it makes the news", lg.news.any { it.text.startsWith("Record: ") })
        assertTrue("it adds legacy", com.nh.hoopscoach.game.AllTime.heroScore(lg) >= before + 3)
        playSeason(lg)
        // Big playoff nights: every series decider has stakes for both teams.
        val ps = lg.proState!!
        for (sx in ps.series.filter { it.done }) {
            val last = lg.game(sx.gameIds.last())
            assertTrue("deciders are big nights", com.nh.hoopscoach.game.BigNights.stakes(lg, last, sx.hi).isNotEmpty() && com.nh.hoopscoach.game.BigNights.stakes(lg, last, sx.lo).isNotEmpty())
        }
        val g7 = ps.series.firstOrNull { it.gameIds.size == 7 }
        if (g7 != null) assertTrue(com.nh.hoopscoach.game.BigNights.stakes(lg, lg.game(g7.gameIds.last()), g7.hi).startsWith("Game 7"))
        println("reel: ${r.reel.map { "${it.title}: ${it.line}" }}")
        val lbj = ps.records.first { it.key == "career_pts" }
        println("career points record: ${lbj.holder} ${lbj.value}")
        assertTrue("an active holder's record keeps growing", lbj.mine || lbj.value >= lbj.base)
        // The summer market.
        ProCareer.next(lg)
        assertTrue("stars available", ps.freeStars.isNotEmpty())
        val star = lg.player(ps.freeStars.first())
        println("pitch ${star.name} (${star.ovr}, ${"%.0f".format(sr.chance(lg, star) * 100)}%): " + sr.pitch(lg, star.id))
        assertTrue("one pitch a summer", !sr.canPitch(lg))
        assertTrue("rosters legal", lg.teams.all { t -> t.roster.size in 13..15 && t.roster.all { lg.player(it).teamId == t.id } })
        // Retire, and take a college job.
        lg.hero!!.age = 34
        ProCareer.retire(lg)
        println("jerseys ${lg.road!!.jerseys}, hof ${lg.road!!.hofClass}, highlights ${com.nh.hoopscoach.game.Afterward.highlights(lg)}")
        val jobs = com.nh.hoopscoach.game.Afterward.coachingJobs(lg)
        println("jobs: ${jobs.map { "${it.school} (${it.why})" }}")
        assertTrue("his alma mater calls", jobs.first().why.contains("alma mater"))
        val college = com.nh.hoopscoach.game.Afterward.becomeCoach(lg, LeagueGen.newLeague(lg.season + 1), jobs.first().school)
        val c = college.userCoach
        assertEquals(lg.hero!!.last, c.last)
        assertEquals(jobs.first().school, college.user.school)
        assertTrue("coach mode", !college.playerMode && !college.pro)
        println("coach ${c.name}, age ${c.age}, rec ${c.rec}, off ${c.off}, def ${c.def} at ${college.user.school}, season ${college.season}")
        repeat(5) { Season.playDay(college) }
        assertTrue("the coach game runs", college.games.any { it.played })
    }

    @Test
    fun negotiation() {
        var lg = ProLeague.create(toDraft(21))
        val r = lg.road!!
        while (lg.phase == Phase.OFFSEASON) ProCareer.next(lg)
        playSeason(lg)
        // His deal is up: free agency.
        r.contract!!.years = 0
        ProCareer.next(lg); ProCareer.next(lg)
        assertEquals(ProStep.FREE_AGENCY, lg.proState!!.step)
        assertTrue("offers", r.proOffers.size >= 2)
        assertTrue("every offer has room to stretch", r.proOffers.all { it.max >= it.salary })
        val before = r.proOffers.map { it.teamId to it.salary }
        val answer = ProCareer.counter(lg, 0, 0.20)
        println("counter: $answer; offers ${before.map { (t, s) -> "${lg.team(t).abbr} $s" }} -> ${r.proOffers.map { "${lg.team(it.teamId).abbr} ${it.salary}" }}")
        assertTrue(answer.isNotEmpty())
        val o0 = r.proOffers.firstOrNull { it.teamId == before[0].first }
        if (o0 != null) {
            assertTrue("a counter never lowers the offer", o0.salary >= before[0].second)
            assertEquals("one counter per offer", o0.salary, ProCareer.counter(lg, r.proOffers.indexOf(o0), 0.1).let { o0.salary })
        }
        val i = r.proOffers.indices.last
        val base = r.proOffers[i].salary
        val msg = ProCareer.signWith(lg, i, 2, option = true, discount = false)
        println(msg)
        val c = r.contract!!
        assertEquals(2, c.years)
        assertTrue("player option", c.option)
        assertEquals("5% less for the option", (base * 0.95).toInt() / 10 * 10, c.salary)
        assertTrue("signed", r.proOffers.isEmpty())
        lg = SaveIO.decode(SaveIO.encode(lg))
        while (lg.phase == Phase.OFFSEASON) ProCareer.next(lg)
        playSeason(lg)
        ProCareer.next(lg); ProCareer.next(lg)
        assertTrue("the option comes due", lg.road!!.optionPending)
        ProCareer.optOut(lg)
        val rr = lg.road!!
        assertTrue("opting out opens free agency", rr.contract!!.years == 0 && rr.proOffers.isNotEmpty() && !rr.optionPending)
        // A ring discount with a contender, if one calls.
        val ci = rr.proOffers.indexOfFirst { ProCareer.contender(lg, it.teamId) }
        if (ci >= 0) {
            val o = rr.proOffers[ci]
            val team = lg.team(o.teamId)
            val rosterBefore = team.roster.toSet()
            val note = ProCareer.signWith(lg, ci, 3, option = false, discount = true)
            println(note)
            assertTrue("15% less", kotlin.math.abs((o.salary * 0.85).toInt() / 10 * 10 - rr.contract!!.salary) <= 10)
            assertTrue("the team adds a veteran", team.roster.any { it !in rosterBefore && it != lg.heroId })
        } else {
            ProCareer.signWith(lg, 0, 3, option = false, discount = true)
            println("no contender called; the discount does nothing without one")
        }
        assertTrue("rosters legal", lg.teams.all { t -> t.roster.size in 13..15 })
    }

    @Test
    fun proCareer() {
        val college = toDraft(21)
        val r0 = college.road!!
        assertTrue("headed to the pros", r0.goingPro)
        assertTrue("not over", !r0.ended)
        assertTrue("green room still shows", Road.inGreenRoom(college))
        val picks = college.draftHistory.filter { it.season == college.season }
        assertTrue("every pick has a pro team", picks.all { it.proTeam.isNotEmpty() })
        println("drafted No. ${r0.draftPick} by the ${picks.firstOrNull { it.pid == college.heroId }?.proTeam}")

        var lg = ProLeague.create(college)
        val r = lg.road!!
        val h0 = lg.player(lg.heroId)
        assertTrue(lg.pro)
        assertEquals(30, lg.teams.size)
        assertTrue("rosters are full", lg.teams.all { it.roster.size in 13..15 })
        assertTrue("hero on his team", h0.id in lg.user.roster)
        assertTrue("rookie contract", r.contract != null && r.contract!!.salary >= 750)
        assertEquals(Phase.OFFSEASON, lg.phase)
        assertEquals(ProStep.FREE_AGENCY, lg.proState!!.step)
        assertTrue("college record kept", r.collegeTotals != null && r.collegeSeasons >= 1 && r.collegeSchools.isNotEmpty())
        assertTrue("college lines no longer point at teams", h0.career.all { it.teamId == -1 })
        val draftees = picks.count { lg.playerOrNull(it.pid) != null }
        println("pro league built: ${draftees} of ${picks.size} draftees imported; you: ${h0.name}, age ${h0.age}, ovr ${h0.ovr}, ${lg.user.name}, ${r.contract!!.kind} ${r.contract!!.years}y ${Road.dollars(r.contract!!.salary)}; " +
            "team ovr top ${lg.rosterOf(lg.user).map { it.ovr }.sortedDescending().take(8)}; rival ${Rival.player(lg)?.let { "${it.name} ${if (lg.playerOrNull(it.id) != null) "in the league" else "pending"}" }}")
        lg = SaveIO.decode(SaveIO.encode(lg))

        var seasons = 0
        while (!lg.road!!.ended && seasons < 18) {
            // Contract, summer, then the new season.
            while (lg.phase == Phase.OFFSEASON) {
                val ps = lg.proState!!
                if (ps.step == ProStep.FREE_AGENCY && lg.road!!.proOffers.isNotEmpty()) {
                    val o = lg.road!!.proOffers.maxBy { it.salary + (if (it.ahead == 0) 4000 else 0) }
                    ProCareer.sign(lg, o)
                }
                if (ps.step == ProStep.SUMMER) Summer.invites(lg).firstOrNull()?.let { Summer.attend(lg, it) }
                ProCareer.next(lg)
                if (lg.road!!.ended) break
            }
            if (lg.road!!.ended) break
            seasons++
            assertEquals(Phase.REGULAR, lg.phase)
            assertTrue("salary paid", lg.road!!.salarySeason == lg.season)
            assertEquals(83, ProLeague.GAMES)
            assertTrue("83 games each", lg.teams.all { t -> lg.games.count { it.involves(t.id) && it.type != GameType.CUP } == ProLeague.GAMES })
            val ps0 = lg.proState!!
            assertTrue("six Cup groups of five", ps0.cupGroups.size == 6 && ps0.cupGroups.all { it.size == 5 })
            assertTrue("four Cup group games each", lg.teams.all { t -> lg.games.count { it.involves(t.id) && it.label.startsWith("NBA Cup") } == 4 })
            assertTrue("Cup group games stay in the group", lg.games.filter { it.label.startsWith("NBA Cup") }.all { g -> ps0.cupGroups.any { g.home in it && g.away in it } })
            assertTrue("Rivalry Night: one game each", lg.teams.all { t -> lg.games.count { it.involves(t.id) && it.label == "Rivalry Night" } == 1 })
            assertTrue("Cup knockout days are free", lg.games.none { it.day in listOf(ps0.cupQfDay, ps0.cupSfDay, ps0.cupFinalDay) })
            assertTrue("one game a day at most", lg.teams.all { t -> lg.games.filter { it.involves(t.id) }.groupBy { it.day }.values.all { it.size == 1 } })
            assertTrue("no games on All-Star day", lg.games.none { it.day == ProLeague.allStarDay(lg) })
            var guard = 0
            while (lg.phase != Phase.OFFSEASON && guard++ < 200) {
                Sim.playDay(lg)
                lg.road!!.press?.let { Road.answer(lg, seasons % 3) }
            }
            assertEquals(Phase.OFFSEASON, lg.phase)
            val ps = lg.proState!!
            val h = lg.playerOrNull(lg.heroId)!!
            val t = lg.team(h.teamId)
            assertTrue("standings add up", lg.teams.all { it.w + it.l == ProLeague.GAMES })
            assertTrue("rosters stay legal through trades", lg.teams.all { t -> t.roster.size in 13..15 && t.roster.all { lg.player(it).teamId == t.id } })
            assertTrue("AI teams trade at the deadline", lg.proState!!.trades.any { it.season == lg.season && !it.offseason })
            assertTrue("a champion", ps.champion >= 0)
            assertTrue("16 playoff teams", ps.seeds.size == 16)
            assertTrue("a play-in: three games a conference", ps.series.count { it.round == 0 } == 6 && ps.series.filter { it.round == 0 }.all { it.done && it.gameIds.size == 1 })
            assertTrue("play-in teams fill seeds 7 and 8", (0..1).all { c -> ps.series.filter { it.round == 1 && it.conf == c }.flatMap { listOf(it.hi, it.lo) }.map { ps.seeds[it] }.toSet() == (1..8).toSet() })
            assertTrue("an NBA Cup winner", ps.cupChampion >= 0 && ps.cup.count { it.round == 1 } == 4 && ps.cup.single { it.round == 3 }.winner == ps.cupChampion)
            assertTrue("Cup knockouts don't count", lg.games.filter { it.type == GameType.CUP }.size == 7 && lg.teams.all { it.w + it.l == ProLeague.GAMES })
            assertTrue("All-Star Saturday", ps.weekend.size >= 2)
            assertTrue("an MVP vote", ps.mvpVotes.size >= 5 && ps.mvpVotes.first().name == lg.seasonAwards.first { it.award == "MVP" }.name && ps.mvpVotes.sumOf { it.firsts } == 100)
            assertTrue("awards night extras", listOf("All-Defensive 1st", "All-Rookie 1st").all { a -> lg.seasonAwards.count { it.award == a } == 5 })
            assertTrue("playoff games kept apart", lg.games.filter { it.type == GameType.PLAYOFF }.all { it.played })
            assertTrue("all-stars named", ps.allStars.size == 24)
            assertTrue("awards given", listOf("MVP", "Rookie of the Year", "Finals MVP").all { a -> lg.seasonAwards.any { it.award == a } })
            assertTrue("season line written (gp ${h.stats.gp}, team ${h.teamId}, lines ${h.career.takeLast(2).map { "${it.season} ${it.team} ${it.teamId}" }}, on roster ${h.id in t.roster})",
                h.stats.gp == 0 || h.career.any { it.season == lg.season && it.teamId == t.id })
            val mine = lg.seasonAwards.filter { it.pid == h.id }.map { it.award }
            println("${lg.season} · age ${h.age} ovr ${h.ovr} · ${t.name} ${t.w}-${t.l} · %.1f ppg %.1f rpg %.1f apg in %.1f mpg · ${ProSeason.resultFor(lg, t.id)} · champion ${lg.team(ps.champion).name} · MVP ${lg.seasonAwards.first { it.award == "MVP" }.name}"
                .format(h.stats.ppg, h.stats.rpg, h.stats.apg, h.stats.mpg) + (if (mine.isNotEmpty()) " · you: $mine" else "") +
                (if (h.id in ps.allStars) " · All-Star" else "") + " · contract ${lg.road!!.contract?.years}y left · bank ${Road.dollars(Road.bank(lg))} · ${Road.count(lg.road!!.followers)}")
            Rival.player(lg)?.let { rv -> println("   rival ${rv.name}: ${if (lg.playerOrNull(rv.id) != null) "${lg.team(rv.teamId).name}, ovr ${rv.ovr}, %.1f ppg".format(rv.stats.ppg) else lg.road!!.rivalLeft.ifEmpty { "pending" }}; duels ${Rival.record(lg)}") }
            val ovrs = lg.players.filter { it.teamId >= 0 }.map { it.ovr }.sortedDescending()
            println("   league: ${ovrs.count { it >= 97 }} at 97+, ${ovrs.count { it >= 94 }} at 94+, top-150 avg %.1f, median %d; your team's top 5 ${lg.rosterOf(t).map { it.ovr }.sortedDescending().take(5)}".format(ovrs.take(150).average(), ovrs[ovrs.size / 2]))
            val at = com.nh.hoopscoach.game.AllTime
            println("   all-time: No. ${at.rank(lg)} with ${at.points(at.heroScore(lg))}; next ${at.next(lg)?.let { "${it.name} ${at.points(it.score)}" }}; top 3 ${at.list(lg).take(3).map { "${it.name} ${at.points(it.score)}" }}")
            val top = lg.seasonAwards.first { it.award == "MVP" }
            println("   MVP line: ${top.line}; scoring leader %.1f".format(lg.players.filter { it.stats.gp >= 30 }.maxOf { it.stats.ppg }))
            lg = SaveIO.decode(SaveIO.encode(lg))
            // Retire at 39, unless the league retires him first.
            if (ProCareer.age(lg) >= 39) { ProCareer.retire(lg); break }
        }
        val rr = lg.road!!
        println("ending: ${rr.ending}")
        rr.proStory.forEach { println("  $it") }
        println("earned ${Road.dollars(rr.earned)}, rings ${rr.rings}, all-stars ${rr.allStars}, mvps ${rr.mvps}, hof ${ProCareer.hofScore(lg)}")
        assertTrue(rr.ended && rr.retired)
        assertTrue("several pro seasons", seasons >= 3)
        assertTrue("story per season", rr.proStory.size >= seasons)
        assertTrue("the hero is kept", lg.hero != null)
        assertTrue("rich", rr.earned > 10_000)
        val at = com.nh.hoopscoach.game.AllTime
        println("all-time: No. ${at.rank(lg)}; news: ${lg.news.filter { it.text.contains("all-time list") }.takeLast(4).map { it.text }}")
        assertTrue("a long career makes the all-time list", at.rank(lg) > 0)
        assertTrue("passing legends makes the news", lg.news.any { it.text.contains("on the all-time list") || it.text.contains("cracks the all-time list") })
        assertTrue("the ending gives the rank", rr.ending.contains("all-time list"))
        assertTrue("the list keeps the greats in order", at.LEGENDS.first().first == "Michael Jordan")
    }
}
