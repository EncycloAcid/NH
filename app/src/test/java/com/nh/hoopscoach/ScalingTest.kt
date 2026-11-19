package com.nh.hoopscoach

import com.nh.hoopscoach.game.Tactics
import com.nh.hoopscoach.game.LeagueGen
import com.nh.hoopscoach.game.Offseason
import com.nh.hoopscoach.game.Phase
import com.nh.hoopscoach.game.Rng
import com.nh.hoopscoach.game.Season
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Player ceilings and game talent scale should keep the league stable and stars decisive. */
class ScalingTest {

    @Test
    fun leagueOverallDoesNotInflate() {
        Rng.r = Random(23)
        val lg = Offseason.newGame(5, "Test", "Coach")
        fun avgOvr() = lg.players.filter { it.teamId >= 0 }.map { it.ovr }.average()
        fun count90() = lg.players.count { it.teamId >= 0 && it.ovr >= 90 }
        val start = avgOvr()
        val start90 = count90()
        val avgs = mutableListOf(start)
        println("start avg=%.1f 90+=%d".format(start, start90))
        repeat(3) {
            var guard = 0
            while (lg.phase != Phase.WRAP_UP && guard++ < 120) Season.playDay(lg)
            Offseason.autoComplete(lg)
            avgs += avgOvr()
            println("after S${lg.season - 1}: avg=%.1f 90+=%d".format(avgs.last(), count90()))
        }
        val end = avgs.last()
        val end90 = count90()
        // Initial top-heavy rosters sit a little below steady state; the climb should slow, not runaway.
        val lateClimb = avgs[3] - avgs[2]
        assertTrue("late-season overall still climbing hard ($lateClimb)", lateClimb <= 1.6)
        assertTrue("league overall ran away: $start → $end", end - start <= 5.5)
        assertTrue("90+ players should not pile up ($end90, started $start90)", end90 <= start90 + 8)
        assertTrue("stars should still exist", end90 >= 1)
    }

    @Test
    fun starsSeparateOnTheFloor() {
        Rng.r = Random(29)
        val lg = LeagueGen.newLeague()
        val elite = lg.teams.filter { it.prestige >= 90 }
        assertTrue(elite.isNotEmpty())
        var starLed = 0
        for (t in elite) {
            Tactics.validate(lg, t)
            val roster = lg.rosterOf(t).sortedByDescending { it.ovr }
            val star = roster.first()
            val role = roster.drop(5).firstOrNull() ?: roster.last()
            if (star.ovr - role.ovr >= 12) starLed++
        }
        println("elite teams with a clear star/role gap: $starLed / ${elite.size}")
        assertTrue("stars should stand well above the deep bench", starLed >= elite.size * 0.7)
    }
}
