package com.nh.hoopscoach

import com.nh.hoopscoach.game.Finance
import com.nh.hoopscoach.game.LeagueGen
import com.nh.hoopscoach.game.Offseason
import com.nh.hoopscoach.game.OffseasonState
import com.nh.hoopscoach.game.Phase
import com.nh.hoopscoach.game.Rng
import com.nh.hoopscoach.game.ShoeCo
import com.nh.hoopscoach.game.Shoes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ShoesTest {

    @Test
    fun everySchoolGetsRealApparelPartner() {
        Rng.r = Random(21)
        val lg = LeagueGen.newLeague()
        assertTrue(lg.teams.all { it.apparel.isNotEmpty() && Shoes.of(it) != null })
        assertTrue(lg.teams.any { Shoes.of(it) == ShoeCo.NIKE || Shoes.of(it) == ShoeCo.JORDAN })
        assertTrue(lg.teams.any { Shoes.of(it) in listOf(ShoeCo.PUMA, ShoeCo.NEW_BALANCE, ShoeCo.CONVERSE, ShoeCo.REEBOK, ShoeCo.UNDER_ARMOUR) })
    }

    @Test
    fun apparelMoneyAddsToBudget() {
        Rng.r = Random(22)
        val lg = LeagueGen.newLeague()
        val t = lg.teams.maxBy { it.prestige }
        val kit = Shoes.apparelMoney(t)
        assertTrue(kit >= Shoes.of(t)!!.apparelFloor)
        val without = Finance.donor(t.prestige) + Finance.fanMoney(t) + Finance.revShare(lg, t) + Finance.appliedBonus(lg, t)
        assertEquals(without + kit, Finance.budget(lg, t))
    }

    @Test
    fun starsCanLandPersonalDeals() {
        Rng.r = Random(23)
        val lg = LeagueGen.newLeague()
        val withDeals = lg.players.count { it.shoePay > 0 && Shoes.ofPlayer(it) != null }
        assertTrue("expected seeded shoe deals, got $withDeals", withDeals > 50)
        val nikeish = lg.players.count { it.shoe in listOf(ShoeCo.NIKE.name, ShoeCo.JORDAN.name, ShoeCo.ADIDAS.name) }
        assertTrue(nikeish > 0)
    }

    @Test
    fun userGetsOffersInDevelopmentCycle() {
        Rng.r = Random(24)
        val base = LeagueGen.newLeague()
        val lg = Offseason.claimTeam(base, base.teams.maxBy { it.prestige }.id, "Test", "Coach")
        // Force a star without a deal so an offer is likely.
        val star = lg.rosterOf(lg.user).maxBy { it.ovr }
        Shoes.clearDeal(star)
        star.ovr // touch
        repeat(10) { star.attrs[it] = (star.attrs[it] + 5).coerceAtMost(99) }
        val os = OffseasonState()
        lg.offseason = os
        lg.phase = Phase.OFFSEASON
        Shoes.runOffseason(lg, os)
        assertTrue("user star should get an offer or keep a deal", star.shoeOffer.isNotEmpty() || star.shoePay > 0 || os.shoeOffers.isNotEmpty() || lg.rosterOf(lg.user).any { it.shoeOffer.isNotEmpty() })
        val offered = lg.rosterOf(lg.user).firstOrNull { it.shoeOffer.isNotEmpty() }
        if (offered != null) {
            val pay = offered.shoeOfferPay
            assertTrue(Shoes.acceptOffer(offered))
            assertEquals(pay, offered.shoePay)
            assertNotNull(Shoes.ofPlayer(offered))
            assertTrue(offered.shoeOffer.isEmpty())
        }
    }

    @Test
    fun pullBonusFromJordanAndNike() {
        Rng.r = Random(25)
        val lg = LeagueGen.newLeague()
        val t = lg.user
        t.apparel = ShoeCo.JORDAN.name
        t.apparelLeft = 3
        assertEquals(4, Shoes.pullBonus(t))
        t.apparel = ShoeCo.NIKE.name
        assertEquals(3, Shoes.pullBonus(t))
    }
}
