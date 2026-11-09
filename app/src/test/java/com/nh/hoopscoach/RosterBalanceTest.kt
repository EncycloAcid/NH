package com.nh.hoopscoach

import com.nh.hoopscoach.game.LeagueGen
import com.nh.hoopscoach.game.Rng
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Generated rosters should be top-heavy: a star and a sidekick, not a packed five of near-stars. */
class RosterBalanceTest {

    @Test
    fun eliteTeamsAreNotFiveDeep() {
        Rng.r = Random(19)
        val elite85 = mutableListOf<Int>()
        val elite80 = mutableListOf<Int>()
        val mid80 = mutableListOf<Int>()
        repeat(40) {
            val lg = LeagueGen.newLeague()
            for (t in lg.teams) {
                val ovrs = lg.rosterOf(t).map { it.ovr }
                val n85 = ovrs.count { it >= 85 }
                val n80 = ovrs.count { it >= 80 }
                when {
                    t.prestige >= 90 -> { elite85 += n85; elite80 += n80 }
                    t.prestige in 50..65 -> mid80 += n80
                }
            }
        }
        val avg85 = elite85.average()
        val avg80 = elite80.average()
        val midAvg80 = mid80.average()
        println("elite (≥90 prestige): avg #85+=%.2f #80+=%.2f over ${elite85.size} rosters; mid-major #80+=%.2f".format(avg85, avg80, midAvg80))
        // Blue bloods get a star and maybe a sidekick near 85, not four or five.
        assertTrue("elite teams average too many 85+ players ($avg85)", avg85 <= 2.4)
        assertTrue("elite teams average too many 80+ players ($avg80)", avg80 <= 4.0)
        // Mid-majors stay thinner than the old flat curve (which gave them ~1+ at 80).
        assertTrue("mid-majors should rarely stack 80+ talent ($midAvg80)", midAvg80 <= 1.2)
    }
}
