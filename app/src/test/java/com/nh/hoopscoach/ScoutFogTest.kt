package com.nh.hoopscoach

import com.nh.hoopscoach.game.Offseason
import com.nh.hoopscoach.game.Recruit
import com.nh.hoopscoach.game.Recruiting
import com.nh.hoopscoach.game.Rng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ScoutFogTest {

    @Test
    fun newClassStartsFoggedWithWideBands() {
        Rng.r = Random(41)
        val lg = Offseason.newGame(5, "Test", "Coach")
        val sample = lg.recruits.filter { !it.transfer }.take(80)
        assertTrue(sample.isNotEmpty())
        assertTrue(sample.all { !it.scouted && it.scoutLevel == 0 })
        val spans = sample.map { Recruiting.fog(it).ovrSpan }
        assertTrue("expected wide unscouted bands, got avg=${spans.average()} sample=$spans", spans.average() >= 8.0)
        assertTrue(sample.any { kotlin.math.abs(it.noiseO) >= 3 || kotlin.math.abs(it.noiseP) >= 3 })
    }

    @Test
    fun threeLooksClearTheFog() {
        Rng.r = Random(42)
        val lg = Offseason.newGame(5, "Test", "Coach")
        lg.recruitPoints = 60
        val r = lg.recruits.first { it.p.stars >= 4 && !it.transfer }
        Recruiting.seedFog(r)
        val start = Recruiting.fog(r)
        assertFalse(start.known)
        assertEquals("Watch film", start.nextAction)

        assertTrue(Recruiting.scout(lg, r))
        val film = Recruiting.fog(r)
        assertEquals(1, r.scoutLevel)
        assertTrue("film should narrow the band (${start.ovrSpan} -> ${film.ovrSpan})", film.ovrSpan < start.ovrSpan)

        assertTrue(Recruiting.scout(lg, r))
        assertEquals(2, r.scoutLevel)
        assertEquals("Full report", Recruiting.fog(r).nextAction)

        assertTrue(Recruiting.scout(lg, r))
        val done = Recruiting.fog(r)
        assertTrue(done.known)
        assertTrue(r.scouted)
        assertEquals(r.p.ovr, done.midOvr)
        assertEquals(r.p.pot, done.midPot)
        assertEquals(0, done.ovrSpan)
        assertFalse(Recruiting.scout(lg, r))
    }

    @Test
    fun ownPortalCutsStayKnownForeignPortalStartsFogged() {
        Rng.r = Random(43)
        val lg = Offseason.newGame(5, "Test", "Coach")
        val mine = lg.rosterOf(lg.user).filter { !it.walkOn }.minBy { it.ovr }
        Offseason.release(lg, mine.id)
        val known = lg.recruits.first { it.p.id == mine.id }
        assertTrue(known.transfer)
        assertTrue(Recruiting.fog(known).known)

        val stranger = lg.rosterOf(lg.teams.first { it.id != lg.userTeamId }).first { !it.walkOn }
        val r = Recruit(stranger, transfer = true)
        Recruiting.seedFog(r, known = false)
        assertFalse(Recruiting.fog(r).known)
        assertTrue(Recruiting.fog(r).ovrSpan >= 6)
    }
}
