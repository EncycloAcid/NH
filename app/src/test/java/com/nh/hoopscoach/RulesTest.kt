package com.nh.hoopscoach

import com.nh.hoopscoach.game.GameEngine
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.LeagueGen
import com.nh.hoopscoach.game.Offseason
import com.nh.hoopscoach.game.Phase
import com.nh.hoopscoach.game.ProCareer
import com.nh.hoopscoach.game.ProLeague
import com.nh.hoopscoach.game.Rng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** The game follows the real rules of each level: periods, the clock, foul limits and the penalty. */
class RulesTest {
    private class Tally(var games: Int = 0, var maxPf: Int = 0, var fouledOut: Int = 0, var ots: Int = 0, val text: MutableList<String> = mutableListOf())

    private fun play(lg: League, n: Int, check: (GameEngine) -> Unit): Tally {
        val t = Tally()
        for (g in lg.games.filter { !it.played }.take(n)) {
            val e = GameEngine(lg, g, true)
            e.simToEnd()
            check(e)
            t.games++
            val pfs = e.lines.flatMap { it.values }.map { it.pf }
            t.maxPf = maxOf(t.maxPf, pfs.max())
            t.fouledOut += pfs.count { it >= (if (lg.pro) 6 else 5) }
            if (e.period > e.regPeriods) t.ots++
            t.text += e.events.map { it.text }
        }
        return t
    }

    @Test
    fun nbaRules() {
        val lg = ProLeague.create(ProTest().toDraft(21))
        while (lg.phase == Phase.OFFSEASON) ProCareer.next(lg)
        val t = play(lg, 300) { e ->
            assertEquals("four quarters", 4, e.regPeriods)
            assertEquals(720.0, e.periodLen, 0.0)
            assertTrue("a period score for every quarter and overtime", e.periodScores.size == e.period && e.period >= 4)
            assertTrue("a winner", e.score[0] != e.score[1])
            val sec = e.lines[0].values.sumOf { it.sec }
            val expected = (48 + (e.period - 4) * 5) * 60 * 5
            assertTrue("48 minutes (plus overtime) for five players: $sec vs $expected", kotlin.math.abs(sec - expected) <= 40)
        }
        println("NBA: ${t.games} games, max fouls ${t.maxPf}, foul-outs ${t.fouledOut}, overtimes ${t.ots}")
        assertEquals("six fouls and you're out", 6, t.maxPf)
        assertTrue("quarters in the play-by-play", t.text.any { it.startsWith("End of the 1st quarter") } && t.text.any { it.startsWith("Halftime") } && t.text.any { it.startsWith("End of the 3rd quarter") })
        assertTrue("no one-and-one in the NBA", t.text.none { it.contains("one-and-one") })
        assertTrue("penalty shots", t.text.any { it.contains(", two shots for") })
    }

    @Test
    fun collegeRules() {
        Rng.r = Random(11)
        val lg = Offseason.claimTeam(LeagueGen.newLeague(), 0, "Test", "Coach")
        val t = play(lg, 400) { e ->
            assertEquals("two halves", 2, e.regPeriods)
            assertEquals(1200.0, e.periodLen, 0.0)
            assertTrue(e.periodScores.size == e.period && e.period >= 2)
            val sec = e.lines[0].values.sumOf { it.sec }
            val expected = (40 + (e.period - 2) * 5) * 60 * 5
            assertTrue("40 minutes (plus overtime) for five players: $sec vs $expected", kotlin.math.abs(sec - expected) <= 40)
        }
        println("College: ${t.games} games, max fouls ${t.maxPf}, foul-outs ${t.fouledOut}, overtimes ${t.ots}")
        assertEquals("five fouls and you're out", 5, t.maxPf)
        assertTrue("one-and-ones from the seventh foul", t.text.any { it.contains("one-and-one for") })
        assertTrue("missed front ends", t.text.any { it.contains("misses the front end") })
        assertTrue("double bonus from the tenth", t.text.any { it.contains(", two shots for") })
        assertTrue("no quarters in college", t.text.none { it.contains("quarter") })
    }
}
