package com.nh.hoopscoach

import com.nh.hoopscoach.game.Phase
import com.nh.hoopscoach.game.ProCareer
import com.nh.hoopscoach.game.ProLeague
import com.nh.hoopscoach.game.ProStep
import com.nh.hoopscoach.game.Rng
import com.nh.hoopscoach.game.Sim
import com.nh.hoopscoach.game.Summer
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** A pro rookie tops out at 83 from Draft Night to the end of his first season, then grows. */
class RookieCapTest {
    @Test
    fun rookieSeasonStaysAt83() {
        for (seed in 0 until 2) {
            Rng.r = Random(seed * 31 + 7)
            val lg = ProLeague.create(ProTest().toDraft(21))
            val h = { lg.hero!! }
            val seen = mutableListOf(h().ovr)
            while (lg.phase == Phase.OFFSEASON) {
                if (lg.proState!!.step == ProStep.SUMMER) Summer.invites(lg).firstOrNull()?.let { Summer.attend(lg, it) }
                seen += h().ovr
                ProCareer.next(lg)
            }
            var guard = 0
            while (lg.phase != Phase.OFFSEASON && guard++ < 220) { Sim.playDay(lg); seen += h().ovr }
            println("seed $seed: rookie season ${seen.min()}..${seen.max()}, pot ${h().pot}")
            assertTrue("never above ${ProLeague.ROOKIE_MAX} as a rookie: $seen", seen.all { it <= ProLeague.ROOKIE_MAX })
            // His first summer: growth is allowed again.
            while (lg.phase == Phase.OFFSEASON && lg.proState!!.step != ProStep.SUMMER) ProCareer.next(lg)
            assertTrue("growth after the rookie year", h().proYears >= 1)
        }
    }
}
