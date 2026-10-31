package com.nh.hoopscoach

import com.nh.hoopscoach.game.Era
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.NbaAwards
import com.nh.hoopscoach.game.Phase
import com.nh.hoopscoach.game.Pos
import com.nh.hoopscoach.game.ProCareer
import com.nh.hoopscoach.game.ProLeague
import com.nh.hoopscoach.game.Rng
import com.nh.hoopscoach.game.SaveIO
import com.nh.hoopscoach.game.Sim
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** The NBA's honors: weekly and monthly awards, awards night, the playoffs, the Cup and All-Star, by era. */
class AwardsTest {
    private fun season(era: Era, seed: Int): League {
        Rng.r = Random(seed)
        val lg = ProLeague.create(ProTest().toDraft(21), era)
        while (lg.phase == Phase.OFFSEASON) ProCareer.next(lg)
        var guard = 0
        while (lg.phase != Phase.OFFSEASON && guard++ < 260) Sim.playDay(lg)
        assertEquals(Phase.OFFSEASON, lg.phase)
        return lg
    }

    private fun group(p: Pos) = when (p) { Pos.PG, Pos.SG -> 0; Pos.SF, Pos.PF -> 1; Pos.C -> 2 }

    @Test
    fun today() {
        val lg = season(Era.TODAY, 5)
        val all = NbaAwards.of(lg)
        fun n(a: String) = all.count { it.award == a }
        val byAward = all.groupingBy { it.award }.eachCount()
        println("TODAY honors: " + byAward.entries.joinToString { "${it.key} ${it.value}" })

        // Weekly: one per conference every four game days.
        val weeks = all.filter { it.award == "Player of the Week" }
        val nWeeks = weeks.map { it.period }.distinct().size
        assertTrue("about 24 weeks ($nWeeks)", nWeeks in 20..28)
        assertTrue("East and West each week", weeks.groupBy { it.period }.values.all { w -> w.map { it.conf }.sorted() == listOf(0, 1) })
        assertTrue("a winner plays in his conference", weeks.all { lg.team(it.teamId).confId == it.conf })
        val most = weeks.groupingBy { it.name }.eachCount().entries.sortedByDescending { it.value }
        println("Players of the Week: ${most.size} different; most: " + most.take(6).joinToString { "${it.key} ${it.value}" })
        assertTrue("spread around (${most.first().value} for one player)", most.first().value <= 8 && most.size >= 14)
        // Monthly: five months, each conference's player, rookie and coach.
        for (a in listOf("Player of the Month", "Rookie of the Month", "Coach of the Month")) assertEquals(a, 10, n(a))
        assertTrue("rookies of the month are rookies", all.filter { it.award == "Rookie of the Month" }.all { lg.player(it.pid).proYears <= 1 })
        all.firstOrNull { it.award == "Rising Stars MVP" }?.let { rs ->
            val p = lg.player(rs.pid)
            println("Rising Stars MVP: ${p.name}, age ${p.age}, ${p.proYears} years")
            assertTrue("Rising Stars are first- and second-year players", p.proYears <= 2)
        }
        // Today's real players count the years they've played, including the ones while the user was in college.
        val vets = lg.players.filter { it.photo.isNotEmpty() && it.age >= 25 }
        assertTrue("real veterans have years in the league", vets.isNotEmpty() && vets.all { it.proYears >= 3 })
        assertTrue("coaches have no player id", all.filter { it.award.startsWith("Coach") || it.award == "Executive of the Year" }.all { it.pid == -1 })

        // Awards night.
        for (a in listOf("MVP", "Rookie of the Year", "Defensive Player of the Year", "Sixth Man of the Year", "Clutch Player of the Year",
            "Coach of the Year", "Executive of the Year", "Hustle Award", "Sportsmanship Award", "Teammate of the Year", "Citizenship Award",
            "Social Justice Champion", "Scoring title", "Rebounding title", "Assists title", "Steals title", "Blocks title")) assertEquals(a, 1, n(a))
        val allNba = all.filter { it.award.startsWith("All-NBA") }
        assertEquals("three All-NBA teams", 15, allNba.size)
        assertEquals("fifteen different players", 15, allNba.map { it.pid }.distinct().size)
        assertEquals("the MVP is first team", "All-NBA 1st", allNba.first { it.pid == all.first { h -> h.award == "MVP" }.pid }.award)
        assertEquals("two All-Defensive teams", 10, all.count { it.award.startsWith("All-Defensive") })
        assertEquals("two All-Rookie teams", 10, all.count { it.award.startsWith("All-Rookie") })
        val clutch = all.first { it.award == "Clutch Player of the Year" }
        val top = lg.players.maxOf { it.stats.clutch }
        println("clutch: ${clutch.name} ${clutch.line}; league high $top")
        assertTrue("clutch points add up over a season ($top)", top in 60..320)
        val need = (ProLeague.seasonGames(lg) * 0.7).toInt()
        val scorer = lg.player(all.first { it.award == "Scoring title" }.pid)
        assertTrue("the scoring champion qualified", scorer.stats.gp >= need)
        assertTrue("and led the qualified", lg.players.filter { it.stats.gp >= need + 1 }.all { it.stats.ppg <= scorer.stats.ppg + 1e-9 })

        // Playoffs: each conference finals MVP is from its winner, the Finals MVP from the champion.
        val ps = lg.proState!!
        for (c in 0..1) {
            val s = ps.series.first { it.round == 3 && it.conf == c }
            val mvp = all.single { it.award == "${if (c == 0) "Eastern" else "Western"} Conference Finals MVP" }
            assertEquals("conference finals MVP from the winner", s.winner, mvp.teamId)
            assertTrue("with his series line", mvp.line.endsWith("in the series"))
        }
        assertEquals("Finals MVP on the champion", ps.champion, all.single { it.award == "Finals MVP" }.teamId)
        // The Cup.
        assertEquals(1, n("NBA Cup MVP"))
        assertEquals("Cup All-Tournament Team", 5, n("NBA Cup All-Tournament"))
        val cupTeams = ps.cup.flatMap { listOf(it.hi, it.lo) }.toSet()
        assertTrue("from the knockout teams", all.filter { it.award == "NBA Cup All-Tournament" }.all { it.teamId in cupTeams })
        assertTrue("more than one team", all.filter { it.award == "NBA Cup All-Tournament" }.map { it.teamId }.distinct().size >= 2)
        // All-Star: five starters (two guards) and seven reserves a side.
        for (c in 0..1) {
            val starters = all.filter { it.award == "All-Star starter" && it.conf == c }
            assertEquals(5, starters.size)
            assertEquals("two guards start", 2, starters.count { group(lg.player(it.pid).pos) == 0 })
            assertEquals(7, all.count { it.award == "All-Star reserve" && it.conf == c })
        }
        assertEquals(1, n("All-Star MVP"))
        assertEquals("24 All-Stars", 24, ps.allStars.size)
        // Trophy names today.
        assertEquals("Michael Jordan Trophy", NbaAwards.trophy("MVP", Era.TODAY))
        assertEquals("Jerry West Trophy", NbaAwards.trophy("Clutch Player of the Year", Era.TODAY))

        // On the players' records, through a save, and old saves' "All-League" reads as All-NBA.
        val p = lg.player(allNba.first().pid)
        assertTrue(p.awards.any { it == "${lg.season} All-NBA 1st" })
        p.awards += "${lg.season - 1} All-League 2nd"
        val back = SaveIO.decode(SaveIO.encode(lg))
        assertEquals("honors survive a save", ps.honors.size, back.proState!!.honors.size)
        assertTrue("migrated", back.player(p.id).awards.contains("${lg.season - 1} All-NBA 2nd") && back.player(p.id).awards.none { it.contains("All-League") })
        assertEquals("All-NBA Second Team", NbaAwards.label("All-NBA 2nd"))
        val case = NbaAwards.trophyCase(back.player(p.id))
        println("trophy case: " + case.joinToString { "${it.first} x${it.second.size}" })
        assertTrue(case.any { it.first == "All-NBA 1st" })

        // The next season starts fresh weeks but keeps last season's awards.
        while (lg.phase == Phase.OFFSEASON) ProCareer.next(lg)
        assertEquals(0, lg.proState!!.week)
        assertTrue("past awards kept", NbaAwards.of(lg, lg.season - 1).any { it.award == "MVP" })
    }

    @Test
    fun era1986() {
        val lg = season(Era.Y1986, 6)
        val all = NbaAwards.of(lg)
        println("1986 honors: " + all.groupingBy { it.award }.eachCount().entries.joinToString { "${it.key} ${it.value}" })
        val weeks = all.filter { it.award == "Player of the Week" }
        assertTrue("one Player of the Week for the league", weeks.isNotEmpty() && weeks.all { it.conf == -1 } && weeks.groupBy { it.period }.values.all { it.size == 1 })
        for (a in listOf("Clutch Player of the Year", "Hustle Award", "Teammate of the Year", "Sportsmanship Award", "Social Justice Champion",
            "Eastern Conference Finals MVP", "Western Conference Finals MVP", "NBA Cup MVP")) assertEquals("no $a in 1986", 0, all.count { it.award == a })
        assertEquals(1, all.count { it.award == "Citizenship Award" })
        assertEquals(1, all.count { it.award == "Finals MVP" })
        // Two All-NBA teams then, each two guards, two forwards and a center (either of a player's positions).
        val allNba = all.filter { it.award.startsWith("All-NBA") }
        assertEquals(10, allNba.size)
        for (tier in listOf("All-NBA 1st", "All-NBA 2nd")) {
            val five = allNba.filter { it.award == tier }.map { lg.player(it.pid) }
            val fits = five.map { p -> listOfNotNull(p.pos, p.pos2).map(::group).toSet() }
            println("$tier: " + five.joinToString { "${it.name} ${it.posLabel}" })
            assertTrue("a center on $tier", fits.any { 2 in it })
            assertTrue("two guards on $tier", fits.count { 0 in it } >= 2)
        }
        assertEquals("one All-Rookie team", 5, all.count { it.award.startsWith("All-Rookie") })
        assertEquals("Maurice Podoloff Trophy", NbaAwards.trophy("MVP", Era.Y1986))
    }
}
