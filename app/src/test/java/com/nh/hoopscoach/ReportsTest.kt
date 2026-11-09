package com.nh.hoopscoach

import com.nh.hoopscoach.game.Awards
import com.nh.hoopscoach.game.MomentKind
import com.nh.hoopscoach.game.Offseason
import com.nh.hoopscoach.game.Phase
import com.nh.hoopscoach.game.Pos
import com.nh.hoopscoach.game.Reports
import com.nh.hoopscoach.game.Rng
import com.nh.hoopscoach.game.Season
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Award, champion and wrap-up reports should read like a magazine, not a spreadsheet. */
class ReportsTest {

    @Test
    fun awardsAndChampReportsAreDynamic() {
        Rng.r = Random(17)
        val lg = Offseason.newGame(5, "Test", "Coach")
        var guard = 0
        while (lg.phase != Phase.WRAP_UP && guard++ < 120) Season.playDay(lg)
        assertEquals(Phase.WRAP_UP, lg.phase)

        val aa1 = lg.seasonAwards.filter { it.award == "AA 1st" }
        assertEquals(5, aa1.size)
        assertEquals("All-America first team covers every position", Pos.entries.toSet(), aa1.map { lg.player(it.pid).pos }.toSet())

        val mvp = lg.seasonAwards.first { it.award == "Nat MVP" }
        assertFalse("MVP line should not be the bare stat dump", mvp.line == Awards.line(lg.player(mvp.pid)))
        assertTrue(mvp.line.isNotBlank())

        val wrap = lg.wrapUp!!
        val lead = Reports.wrapLead(lg, wrap)
        assertTrue(lead.contains(lg.user.school))
        assertTrue(lead.contains("${wrap.w}-${wrap.l}"))

        val hist = lg.history.last()
        val card = Reports.seasonCard(lg, hist)
        assertTrue(card.contains(lg.team(hist.champion).school))
        assertTrue(card.contains(lg.team(hist.runnerUp).school))

        val title = lg.moments.lastOrNull { it.kind == MomentKind.TITLE && it.season == lg.season }
        assertTrue("title moment should exist", title != null)
        assertFalse(title!!.headline == "${lg.team(hist.champion).school} are national champions!")
        assertTrue(title.detail.contains("championship") || title.detail.contains("nets") || title.detail.contains("title"))
    }
}
