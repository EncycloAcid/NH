package com.nh.hoopscoach

import com.nh.hoopscoach.game.Gameplan
import com.nh.hoopscoach.game.OffStep
import com.nh.hoopscoach.game.Offseason
import com.nh.hoopscoach.game.Phase
import com.nh.hoopscoach.game.RecruitPref
import com.nh.hoopscoach.game.Redshirt
import com.nh.hoopscoach.game.Redshirts
import com.nh.hoopscoach.game.Rng
import com.nh.hoopscoach.game.Season
import com.nh.hoopscoach.game.StatLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RedshirtTest {

    @Test
    fun advanceYearPreservesClassForRedshirts() {
        Rng.r = Random(3)
        val lg = Offseason.newGame(5, "Test", "Coach")
        val p = lg.rosterOf(lg.user).first { it.year == 1 }
        p.redshirt = Redshirt.ACADEMIC
        val y = p.year
        assertTrue(Redshirts.advanceYear(p))
        assertEquals(y, p.year)
        assertEquals(Redshirt.NONE, p.redshirt)
        assertTrue(p.usedAcademicRs)
        assertTrue(p.rsClass)

        val q = lg.rosterOf(lg.user).first { it.year == 2 }
        val before = q.year
        assertTrue(!Redshirts.advanceYear(q))
        assertEquals(before + 1, q.year)
    }

    @Test
    fun academicLeavesDepthChart() {
        Rng.r = Random(11)
        val lg = Offseason.newGame(5, "Test", "Coach")
        val t = lg.user
        val p = lg.rosterOf(t).filter { it.year in 1..2 }.minBy { it.ovr }
        assertTrue(Redshirts.setAcademic(lg, p.id, true))
        assertEquals(Redshirt.ACADEMIC, p.redshirt)
        assertTrue(Redshirts.sitting(p))
        assertTrue(t.plan.slots.none { it.starter == p.id || it.reserve == p.id })
        assertTrue(p.cls.startsWith("RS "))
        assertTrue(Gameplan.role(lg, t, p.id) == "Redshirt")
    }

    @Test
    fun medicalKeepsSeniorOnRosterThroughDraft() {
        Rng.r = Random(22)
        val lg = Offseason.newGame(5, "Test", "Coach")
        val t = lg.user
        val p = lg.rosterOf(t).first { it.year == 4 }
        var guard = 0
        while (lg.phase != Phase.WRAP_UP && guard++ < 120) Season.playDay(lg)
        p.stats = StatLine().also { it.gp = 2 }
        assertTrue(Redshirts.canMedical(lg, p))
        Offseason.begin(lg)
        assertTrue(Redshirts.setMedical(lg, p.id))
        assertTrue(lg.offseason!!.graduates.none { it.pid == p.id })
        Offseason.advance(lg) // ROSTER → transfers + draft → DRAFT
        assertEquals(OffStep.DRAFT, lg.offseason!!.step)
        assertTrue("medical senior still on roster", p.id in t.roster)
        assertEquals(4, p.year)
    }

    @Test
    fun developPitchHelpsUpsideKidsBehindDepth() {
        Rng.r = Random(33)
        val lg = Offseason.newGame(5, "Test", "Coach")
        val t = lg.user
        val r = lg.recruits.first { it.p.pot - it.p.ovr >= 12 && it.pref != RecruitPref.MINUTES }
        val factor = Redshirts.developFactor(lg, t, r, ahead = 3)
        assertNotNull(factor)
        assertTrue(factor!!.value > 1.0)
        assertTrue(factor.note.contains("Redshirt", ignoreCase = true))

        val minutesKid = lg.recruits.first { it.pref == RecruitPref.MINUTES && it.p.pot - it.p.ovr >= 8 }
        val hates = Redshirts.developFactor(lg, t, minutesKid, ahead = 2)
        assertNotNull(hates)
        assertTrue(hates!!.value < 1.0)
        assertNull(Redshirts.developFactor(lg, t, r, ahead = 0))
    }
}
