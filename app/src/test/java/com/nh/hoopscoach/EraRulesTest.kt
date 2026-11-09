package com.nh.hoopscoach

import com.nh.hoopscoach.game.CollegeEra
import com.nh.hoopscoach.game.EraRules
import com.nh.hoopscoach.game.GameEngine
import com.nh.hoopscoach.game.LeagueGen
import com.nh.hoopscoach.game.Offseason
import com.nh.hoopscoach.game.Rng
import com.nh.hoopscoach.game.Schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** College eras should map to real NCAA rule changes and change how games play. */
class EraRulesTest {

    @Test
    fun seasonsMapToHistoricalEras() {
        assertEquals(CollegeEra.PRE_CLOCK, EraRules.ofSeason(1980))
        assertEquals(CollegeEra.CLOCK_45, EraRules.ofSeason(1986))
        assertEquals(CollegeEra.SHORT_THREE, EraRules.ofSeason(1990))
        assertEquals(CollegeEra.CLASSIC, EraRules.ofSeason(2000))
        assertEquals(CollegeEra.MID, EraRules.ofSeason(2010))
        assertEquals(CollegeEra.THIRTY, EraRules.ofSeason(2016))
        assertEquals(CollegeEra.MODERN, EraRules.ofSeason(2026))
        assertFalse(CollegeEra.PRE_CLOCK.hasThrees)
        assertFalse(CollegeEra.CLOCK_45.hasThrees)
        assertTrue(CollegeEra.MODERN.hasThrees)
        assertEquals(0, CollegeEra.PRE_CLOCK.shotClock)
        assertEquals(45, CollegeEra.SHORT_THREE.shotClock)
        assertEquals(30, CollegeEra.MODERN.shotClock)
    }

    @Test
    fun modernGamesUseOneAndOneAndDoubleBonus() {
        Rng.r = Random(21)
        val lg = Offseason.claimTeam(LeagueGen.newLeague(2026), 0, "Test", "Coach")
        Schedule.build(lg)
        val tallies = mutableListOf<String>()
        var fouledOut = 0
        var maxPf = 0
        for (g in lg.games.filter { !it.played }.take(250)) {
            val e = GameEngine(lg, g, true)
            e.simToEnd()
            assertEquals(CollegeEra.MODERN, e.era)
            tallies += e.events.map { it.text }
            val pfs = e.lines.flatMap { it.values }.map { it.pf }
            maxPf = maxOf(maxPf, pfs.maxOrNull() ?: 0)
            fouledOut += pfs.count { it >= 5 }
        }
        assertEquals("five fouls and you're out", 5, maxPf)
        assertTrue("one-and-ones from the seventh foul", tallies.any { it.contains("one-and-one for") })
        assertTrue("missed front ends", tallies.any { it.contains("misses the front end") })
        assertTrue("double bonus from the tenth", tallies.any { it.contains(", two shots for") })
        assertTrue(fouledOut >= 0)
    }

    @Test
    fun preThreeEraDoesNotCountThrees() {
        Rng.r = Random(9)
        val lg = Offseason.claimTeam(LeagueGen.newLeague(1980), 0, "Test", "Coach")
        Schedule.build(lg)
        var tpa = 0
        var longJumpers = 0
        for (g in lg.games.filter { !it.played }.take(80)) {
            val e = GameEngine(lg, g, true)
            e.simToEnd()
            assertEquals(CollegeEra.PRE_CLOCK, e.era)
            tpa += e.lines.flatMap { it.values }.sumOf { it.tpa }
            longJumpers += e.events.count { it.text.contains("long jumper") }
        }
        assertEquals("no three-point line means zero threes attempted", 0, tpa)
        assertTrue("long jumpers still happen without a three-point line", longJumpers > 0)
    }

    @Test
    fun modernTakesFarMoreThreesThanEarlyThreeEra() {
        fun threeRate(year: Int, seed: Int): Double {
            Rng.r = Random(seed)
            val lg = Offseason.claimTeam(LeagueGen.newLeague(year), 0, "Test", "Coach")
            Schedule.build(lg)
            var fga = 0
            var tpa = 0
            for (g in lg.games.filter { !it.played }.take(60)) {
                val e = GameEngine(lg, g, false)
                e.simToEnd()
                for (side in e.lines) for (st in side.values) {
                    fga += st.fga
                    tpa += st.tpa
                }
            }
            return tpa.toDouble() / fga.coerceAtLeast(1)
        }
        val modern = threeRate(2026, 3)
        val early = threeRate(1990, 3)
        println("three rates: modern %.3f, 1990 %.3f".format(modern, early))
        assertTrue("modern three rate too low: $modern", modern > 0.28)
        assertTrue("1990 three rate too high: $early", early < 0.20)
        assertTrue("modern should shoot threes much more than 1990 ($modern vs $early)", modern > early * 1.5)
    }
}
