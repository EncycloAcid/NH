package com.nh.hoopscoach

import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.OffStep
import com.nh.hoopscoach.game.Offseason
import com.nh.hoopscoach.game.Phase
import com.nh.hoopscoach.game.Recruiting
import com.nh.hoopscoach.game.Rng
import com.nh.hoopscoach.game.SaveIO
import com.nh.hoopscoach.game.Season
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Saving and reloading in the middle of recruiting must not lose transfers or leave rosters pointing at nobody. */
class SaveRepairTest {

    private fun assertRostersValid(lg: League, where: String) {
        for (t in lg.teams) {
            for (pid in t.roster) assertTrue("$where: ${t.school} roster has missing player $pid", lg.playerOrNull(pid) != null)
            assertEquals("$where: ${t.school} roster has duplicates", t.roster.size, t.roster.toSet().size)
            for (s in t.plan.slots) {
                assertTrue("$where: ${t.school} lineup starter", s.starter == -1 || s.starter in t.roster)
                assertTrue("$where: ${t.school} lineup reserve", s.reserve == -1 || s.reserve in t.roster)
            }
        }
    }

    @Test
    fun reloadMidRecruiting() {
        Rng.r = Random(17)
        var lg = Offseason.newGame(5, "Test", "Coach")
        var guard = 0
        while (lg.phase != Phase.WRAP_UP && guard++ < 120) Season.playDay(lg)
        Offseason.begin(lg)
        while (lg.offseason!!.step != OffStep.RECRUITING) {
            val os = lg.offseason!!
            if (os.step == OffStep.CAREER && os.fired) com.nh.hoopscoach.game.Coaching.acceptOffer(lg, os.offers.first().teamId)
            Offseason.advance(lg)
        }
        assertTrue("there are transfers in the portal", lg.recruits.any { it.transfer })
        // Close and reopen the app between every round, the way a phone does.
        while (lg.phase == Phase.OFFSEASON && lg.offseason!!.step == OffStep.RECRUITING) {
            lg = SaveIO.decode(SaveIO.encode(lg))
            for (r in lg.recruits.filter { it.transfer }) assertSame("transfer ${r.p.name} relinked", lg.player(r.p.id), r.p)
            Recruiting.autoPlan(lg)
            Offseason.advance(lg)
        }
        lg = SaveIO.decode(SaveIO.encode(lg))
        assertRostersValid(lg, "after recruiting")
        Offseason.advance(lg)
        assertEquals(Phase.REGULAR, lg.phase)
        assertRostersValid(lg, "new season")
        repeat(3) { Season.playDay(lg) }

        // A save already broken by the old bug heals when it loads.
        val u = lg.user
        u.roster += 987654321
        u.plan.slots.first().reserve = 987654321
        val healed = SaveIO.decode(SaveIO.encode(lg))
        assertTrue(987654321 !in healed.user.roster)
        assertRostersValid(healed, "healed save")
        repeat(2) { Season.playDay(healed) }
    }
}
