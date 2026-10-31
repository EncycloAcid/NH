package com.nh.hoopscoach

import com.nh.hoopscoach.game.Era
import com.nh.hoopscoach.game.Eras
import com.nh.hoopscoach.game.GameType
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.Phase
import com.nh.hoopscoach.game.ProCareer
import com.nh.hoopscoach.game.ProLeague
import com.nh.hoopscoach.game.Sim
import com.nh.hoopscoach.game.StatLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Era mode: each past era's stars, club names, rules, money and style of play. */
class EraTest {
    private val misses = mutableListOf<String>()
    private fun check(what: String, v: Double, range: ClosedFloatingPointRange<Double>) {
        if (v !in range) misses += "$what ${"%.3f".format(v)} not in $range"
    }

    private fun build(era: Era): League {
        val lg = ProLeague.create(ProTest().toDraft(21), era)
        while (lg.phase == Phase.OFFSEASON) ProCareer.next(lg)
        return lg
    }

    private fun averages(lg: League): Map<String, Double> {
        val tot = StatLine()
        lg.teams.forEach { tot.add(it.totals) }
        val g = lg.teams.sumOf { it.totals.gp }.toDouble()
        return mapOf("pts" to tot.pts / g, "fg" to tot.fgPct, "3pa" to tot.tpa / g, "fta" to tot.fta / g, "ast" to tot.ast / g, "reb" to tot.reb / g)
    }

    @Test
    fun pastEras() {
        // Points, FG%, threes attempted and free throws attempted a team, roughly as in each real season.
        val targets = mapOf(
            Era.Y1986 to listOf(102.0..118.0, 0.47..0.51, 1.0..6.5, 25.0..35.0),
            Era.Y1996 to listOf(93.0..106.0, 0.445..0.48, 12.0..20.0, 21.0..29.0),
            Era.Y2003 to listOf(89.0..101.0, 0.425..0.46, 11.0..18.5, 21.0..29.0),
            Era.Y2013 to listOf(93.0..105.0, 0.44..0.47, 16.0..24.0, 19.0..26.0),
        )
        for ((era, t) in targets) {
            val lg = build(era)
            val ps = lg.proState!!
            assertEquals("starts in its own year", era.year, lg.season)
            assertEquals(era.name, ps.era)
            val stars = Eras.stars(era)
            val named = stars.values.flatten().count { s -> lg.players.any { it.name == s.name } }
            println("$era: ${lg.season}, ${named} of ${stars.values.flatten().size} stars in the league; clubs ${lg.teams.filter { it.logo == ProLeague.ERA_BADGE }.map { "${it.school} ${it.mascot}" }}")
            assertTrue("the era's stars are here", named >= stars.values.flatten().size * 0.85)
            assertTrue("no NBA Cup before 2023", ps.cupGroups.isEmpty() && lg.games.none { it.label.startsWith("NBA Cup") })
            assertTrue("83 games each", lg.teams.all { tm -> lg.games.count { it.involves(tm.id) } == ProLeague.GAMES })
            val h = lg.hero!!
            assertTrue("money scaled to the era (${h.name}: ${lg.road!!.contract!!.salary})", lg.road!!.contract!!.salary <= ProCareer.rookieSalary(lg, 1))
            var guard = 0
            while (lg.phase != Phase.OFFSEASON && guard++ < 260) Sim.playDay(lg)
            assertEquals(Phase.OFFSEASON, lg.phase)
            val a = averages(lg)
            println("   team per game: pts %.1f fg %.3f 3pa %.1f fta %.1f ast %.1f reb %.1f".format(a["pts"], a["fg"], a["3pa"], a["fta"], a["ast"], a["reb"]))
            println("   champion ${lg.team(ps.champion).name}, MVP ${lg.seasonAwards.firstOrNull { it.award == "MVP" }?.name}, top scorer ${lg.players.filter { it.stats.gp >= 40 }.maxBy { it.stats.ppg }.let { "${it.name} %.1f".format(it.stats.ppg) }}")
            check("$era pts", a["pts"]!!, t[0]); check("$era fg%", a["fg"]!!, t[1]); check("$era 3pa", a["3pa"]!!, t[2]); check("$era fta", a["fta"]!!, t[3])
            assertTrue("no play-in", ps.series.none { it.round == 0 })
            assertEquals("16 playoff teams", 16, ps.seeds.size)
            assertTrue("first round best of ${era.firstRoundBestOf}", ps.series.filter { it.round == 1 }.all { it.bestOf == era.firstRoundBestOf && it.done })
            assertTrue("later rounds best of seven", ps.series.filter { it.round >= 2 }.all { it.bestOf == 7 })
            assertTrue("a champion", ps.champion >= 0)
            assertTrue("no Cup knockouts", lg.games.none { it.type == GameType.CUP })
            if (!era.risingStars) assertTrue("no Rising Stars before 1994", ps.weekend.none { it.startsWith("Rising Stars") })
        }
        assertTrue(misses.joinToString("; "), misses.isEmpty())
    }

    @Test
    fun theStarsAreWhereTheyWere() {
        val lg = ProLeague.create(ProTest().toDraft(21), Era.Y1996)
        fun teamOf(name: String) = lg.players.firstOrNull { it.name == name }?.let { lg.team(it.teamId) }
        assertEquals("Bulls", teamOf("Michael Jordan")?.mascot)
        assertEquals("SuperSonics", teamOf("Gary Payton")?.mascot)
        assertEquals("Magic", teamOf("Shaquille O'Neal")?.mascot)
        assertTrue("old clubs get a monogram, not today's logo", lg.teams.first { it.abbr == "SEA" }.logo == ProLeague.ERA_BADGE)
        assertTrue("Vancouver", lg.teams.any { it.school == "Vancouver" && it.mascot == "Grizzlies" })
        assertTrue("today's players aren't here", lg.players.none { it.name == "Nikola Jokic" })
        val today = ProLeague.create(ProTest().toDraft(21))
        assertTrue("today is unchanged", today.players.any { it.name == "Nikola Jokic" } && Eras.of(today) == Era.TODAY)
    }
}
