package com.nh.hoopscoach

import com.nh.hoopscoach.game.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

/** Today's players carry their NBA 2K27 ratings, shown on the 2K scale. */
class RatingsTest {
    @Test
    fun today() {
        Rng.r = Random(2)
        val lg = ProLeague.create(ProTest().toDraft(21))
        val real = lg.players.filter { it.photo.isNotEmpty() && it.teamId >= 0 }
        val shown = real.map { Ratings.shownPro(it.ovr) }.sortedDescending()
        println("after ${lg.season - ProLeague.DATA_SEASON} years: ${real.size} real players; 90+ ${shown.count { it >= 90 }}, 85+ ${shown.count { it >= 85 }}, 80+ ${shown.count { it >= 80 }}, median ${shown[shown.size / 2]}; top " +
            real.sortedByDescending { it.ovr }.take(8).joinToString { "${it.name} ${Ratings.shownPro(it.ovr)}" })
        val perTeam = lg.teams.map { t -> lg.rosterOf(t).count { Ratings.shownPro(it.ovr) >= 90 } }
        println("90+ per team: ${perTeam.sorted()}")
        assertTrue("about 2K's number of 90s", shown.count { it >= 90 } in 8..35)
        assertTrue("no team stacked with 90s", perTeam.all { it <= 3 })
        // Two seasons on, the league hasn't filled up with 90s.
        repeat(2) {
            var g = 0
            while (lg.phase == Phase.OFFSEASON && g++ < 10) { val r = lg.road!!; if (lg.proState!!.step == ProStep.FREE_AGENCY && (r.contract?.years ?: 0) <= 0 && r.proOffers.isNotEmpty()) ProCareer.signWith(lg, 0, 2, false, false); ProCareer.next(lg) }
            g = 0
            while (lg.phase != Phase.OFFSEASON && g++ < 300) Sim.playDay(lg)
        }
        val later = lg.players.filter { it.teamId >= 0 }.map { Ratings.shownPro(it.ovr) }
        println("${lg.season}: 90+ ${later.count { it >= 90 }}, 85+ ${later.count { it >= 85 }}, median ${later.sorted()[later.size / 2]}")
        assertTrue("still about 2K's number of 90s", later.count { it >= 90 } in 8..35)
        // The scale goes both ways.
        for (k in 60..99) assertTrue(abs(Ratings.shownPro(Ratings.proInternal(k)) - k) <= 1)
        val jokic = EraData.today("Nikola Jokic")!!
        assertEquals(97, jokic.ovr)
        assertTrue("a passing center", jokic.attrs!![Attr.PAS.ordinal] > jokic.attrs[Attr.OUT.ordinal])
    }
}
