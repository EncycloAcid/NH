package com.nh.hoopscoach

import com.nh.hoopscoach.game.Media
import com.nh.hoopscoach.game.Offseason
import com.nh.hoopscoach.game.Phase
import com.nh.hoopscoach.game.ProCareer
import com.nh.hoopscoach.game.ProLeague
import com.nh.hoopscoach.game.Rng
import com.nh.hoopscoach.game.SocialAccounts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Real X handles from the social accounts pack land on team / media / analyst posts. */
class SocialHandleTest {

    @Test
    fun collegeTeamUsesPackHandle() {
        Rng.r = Random(4)
        val lg = Offseason.newGame(5, "Test", "Coach")
        val duke = lg.teams.first { it.school == "Duke" }
        val acct = SocialAccounts.forTeam(lg, duke)
        assertNotNull(acct)
        assertEquals("DukeMBB", acct!!.handle)
        assertTrue(acct.verified)
    }

    @Test
    fun nbaOddNamesStillMatchPack() {
        Rng.r = Random(5)
        val lg = ProLeague.create(ProTest().toDraft(12))
        while (lg.phase == Phase.OFFSEASON) ProCareer.next(lg)
        fun handle(school: String, mascot: String): String {
            val t = lg.teams.first { it.school == school && it.mascot == mascot }
            return SocialAccounts.forTeam(lg, t)!!.handle
        }
        assertEquals("okcthunder", handle("Oklahoma City", "Thunder"))
        assertEquals("MiamiHEAT", handle("Miami", "Heat"))
        assertEquals("nyknicks", handle("New York", "Knicks"))
        assertEquals("LAClippers", handle("LA", "Clippers"))
    }

    @Test
    fun mediaAndPunditPostsCarryRealHandles() {
        Rng.r = Random(8)
        val lg = ProLeague.create(ProTest().toDraft(18))
        while (lg.phase == Phase.OFFSEASON) ProCareer.next(lg)
        val h = lg.hero!!
        Media.post(lg, "Hoops Highlights", "HoopsHighlights", "media", "${h.name} with the poster dunk.", 100, verified = true)
        val clip = lg.road!!.feed.last { it.kind == "media" }
        assertFalse("placeholder handle", clip.handle.equals("HoopsHighlights", true))
        assertTrue(clip.handle.isNotBlank())
        assertTrue(clip.avatar.isNotBlank())

        val v = Media.pundit(lg)
        assertFalse("fake pundit handle", v.handle in setOf("HoopsHarlan", "TheFilmRoomMW", "StatSheetSam", "PaintTouches", "BoxScoreBen", "ThirtyFootJay", "NiaOnTheBeat"))
        assertTrue(v.handle.isNotBlank())
        assertTrue(v.avatar.isNotBlank())
    }
}
