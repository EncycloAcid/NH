package com.nh.hoopscoach

import com.nh.hoopscoach.game.Arch
import com.nh.hoopscoach.game.LeagueGen
import com.nh.hoopscoach.game.NilDeal
import com.nh.hoopscoach.game.Offseason
import com.nh.hoopscoach.game.Phase
import com.nh.hoopscoach.game.Pos
import com.nh.hoopscoach.game.Practice
import com.nh.hoopscoach.game.Road
import com.nh.hoopscoach.game.Rng
import com.nh.hoopscoach.game.Season
import com.nh.hoopscoach.game.Staff
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Spending NIL money: the bank, hiring staff, what each one does, and a season of it. */
class RoadStaffTest {

    @Test
    fun spendYourNil() {
        Rng.r = Random(4)
        var lg = LeagueGen.newLeague()
        val hero = Road.createHero(lg, Road.Spec("Tay", "Morgan", Pos.SF, 79, 210, "Raleigh", "NC", Arch.SLASHER, 5, 23))
        lg = Road.start(lg, hero, Road.offers(lg, hero).first().teamId)
        val r = lg.road!!
        assertEquals("nothing in the bank before a deal", 0, Road.bank(lg))
        assertFalse(Road.canHire(lg, Staff.TRAINER))

        val before = Road.market(lg)
        Road.chooseDeal(lg, NilDeal.STEADY)
        val guaranteed = Road.guaranteed(NilDeal.STEADY, r.dealBase)
        assertEquals("guaranteed money arrives at signing", guaranteed, Road.bank(lg))
        println("market $before, guaranteed $guaranteed, bank ${Road.bank(lg)}")

        // Fatigue and development before anyone is hired.
        val tiredBefore = Road.fatigueGain(lg, Practice.WEIGHTS, 32)
        val xpBefore = Road.xpFactor(lg)
        assertEquals(0.0, Road.jumpShotBonus(lg), 0.0)

        for (s in listOf(Staff.TRAINER, Staff.NUTRITIONIST, Staff.SHOOTING_COACH, Staff.AGENT)) {
            val bank = Road.bank(lg)
            val ok = Road.hire(lg, s)
            if (bank >= s.cost) {
                assertTrue("hire ${s.label}", ok)
                assertEquals(bank - s.cost, Road.bank(lg))
                assertTrue(Road.has(lg, s))
                assertFalse("no hiring twice", Road.hire(lg, s))
            } else assertFalse("can't overspend", ok)
        }
        println("hired ${r.hired.map { it.label }}, bank left ${Road.bank(lg)}, spent ${r.spent}")
        assertTrue(Road.has(lg, Staff.TRAINER) && Road.has(lg, Staff.NUTRITIONIST))
        assertEquals("trainer speeds development", xpBefore * 1.25, Road.xpFactor(lg), 1e-9)
        assertTrue("nutritionist cuts fatigue", Road.fatigueGain(lg, Practice.WEIGHTS, 32) < tiredBefore)
        if (Road.has(lg, Staff.SHOOTING_COACH)) assertEquals(0.03, Road.jumpShotBonus(lg), 1e-9)
        if (Road.has(lg, Staff.AGENT)) assertTrue("agent gets more", Road.market(lg) > before)

        // A season with weights and a nutritionist: fatigue stays manageable.
        Road.setPractice(lg, Practice.WEIGHTS)
        var peak = 0
        var guard = 0
        while (lg.phase != Phase.WRAP_UP && guard++ < 120) {
            Season.playDay(lg)
            peak = maxOf(peak, r.fatigue)
            if (lg.phase == Phase.REGULAR && lg.day == 2) {
                // Contracts run the whole season; nothing to re-hire mid-year.
                assertTrue(Road.has(lg, Staff.TRAINER))
            }
        }
        val sponsors = Road.MILESTONES.take(r.milestone).sumOf { (_, pay) -> if (Road.has(lg, Staff.AGENT) || r.hired.contains(Staff.AGENT)) (pay * 1.15).toInt() else pay }
        println("season over: fatigue peak $peak, earned ${r.earned}, spent ${r.spent}, bank ${Road.bank(lg)}, sponsors ${r.milestone}")
        assertTrue("weights with a nutritionist still tire you, but slower", peak in 20..90)
        assertTrue("contracts end with the season", r.hired.isEmpty())
        assertTrue("guaranteed money isn't paid twice", r.earned <= guaranteed + sponsors + 5)
        Offseason.begin(lg)
        assertFalse("no hiring in the offseason", Road.canHire(lg, Staff.TRAINER))
    }
}
