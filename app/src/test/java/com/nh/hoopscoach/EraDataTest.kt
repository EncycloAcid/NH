package com.nh.hoopscoach

import com.nh.hoopscoach.game.Attr
import com.nh.hoopscoach.game.Era
import com.nh.hoopscoach.game.EraData
import com.nh.hoopscoach.game.Eras
import com.nh.hoopscoach.game.Phase
import com.nh.hoopscoach.game.ProCareer
import com.nh.hoopscoach.game.ProLeague
import com.nh.hoopscoach.game.ProStep
import com.nh.hoopscoach.game.Rng
import com.nh.hoopscoach.game.Sim
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/** Era mode's real data: the era's stars with their 2K shapes and photos, and every year's real draft class. */
class EraDataTest {
    @Test
    fun theData() {
        for (era in Era.entries.filter { it != Era.TODAY }) {
            val stars = EraData.stars(era)
            println("$era: ${stars.size} players, ${stars.count { it.attrs != null }} with 2K attributes, ${stars.count { it.photo.isNotEmpty() }} with photos; top: " +
                stars.sortedByDescending { it.ovr }.take(5).joinToString { "${it.name} ${it.ovr}" })
            assertTrue("about 120 or more per era", stars.size >= 120)
            assertTrue("photos are bundled", stars.filter { it.photo.isNotEmpty() }.all { File("src/main/assets/nba/heads/${it.photo}.webp").exists() })
            assertTrue("on the game's scale", stars.all { it.ovr in 70..99 })
        }
        // Real drafts, every year from 1986.
        assertTrue((1986..2026).all { EraData.draft(it).size >= 54 })
        assertEquals("David Robinson", EraData.draft(1987).first { it.pick == 1 }.name)
        assertEquals("Scottie Pippen", EraData.draft(1987).first { it.pick == 5 }.name)
        assertEquals("Shaquille O'Neal", EraData.draft(1992).first { it.pick == 1 }.name)
        assertEquals("Kobe Bryant", EraData.draft(1996).first { it.pick == 13 }.name)
        assertTrue("future stars carry their ceiling", EraData.draft(1987).first { it.pick == 5 }.peak >= 95)
        assertTrue("college seasons", EraData.college(EraData.draft(1986).first { it.pick == 1 }.id).size >= 3)
    }

    @Test
    fun aCareerIn1986() {
        Rng.r = Random(8)
        val lg = ProLeague.create(ProTest().toDraft(21), Era.Y1986)
        while (lg.phase == Phase.OFFSEASON) ProCareer.next(lg)
        val bird = lg.players.first { it.name == "Larry Bird" }
        val jordan = lg.players.first { it.name == "Michael Jordan" }
        println("Bird ${bird.ovr} ${lg.team(bird.teamId).abbr} ${bird.photo} ${bird.posLabel}: ${Attr.entries.joinToString { "${it.short} ${bird[it]}" }}")
        println("Jordan ${jordan.ovr} ${lg.team(jordan.teamId).abbr} ${jordan.photo}")
        assertEquals("BOS", lg.team(bird.teamId).abbr)
        assertTrue("Bird's photo", bird.photo == "eras/1985-86/birdla01")
        assertTrue("Bird shoots threes better than he blocks shots or finishes inside", bird[Attr.OUT] > bird[Attr.BLK] && bird[Attr.OUT] > bird[Attr.INS])
        val mchale = lg.players.first { it.name == "Kevin McHale" }
        println("McHale ${mchale.ovr}: ${Attr.entries.joinToString { "${it.short} ${mchale[it]}" }}")
        assertTrue("McHale is a post player", mchale[Attr.IDF] > mchale[Attr.OUT] + 10)
        assertEquals("CHI", lg.team(jordan.teamId).abbr)
        val withPhotos = lg.players.count { it.photo.startsWith("eras/1985-86/") || it.photo.startsWith("eras/1995-96/") }
        println("players with photos: $withPhotos")
        assertTrue("most real players have their photo ($withPhotos)", withPhotos >= 100)

        // Play the season through the draft: the real 1986 class arrives.
        var guard = 0
        while (lg.phase != Phase.OFFSEASON && guard++ < 260) Sim.playDay(lg)
        guard = 0
        while (lg.proState!!.step != ProStep.FREE_AGENCY && guard++ < 6) ProCareer.next(lg)
        val drafted = lg.players.filter { p -> p.awards.any { it.startsWith("1986 Draft #") } }
        println("1986 draft: " + drafted.sortedBy { p -> p.awards.first { it.startsWith("1986 Draft #") }.substringAfter("#").toInt() }.take(8).joinToString { "${it.name} (${it.highSchool}, ${it.ovr}/${it.pot})" })
        val daugherty = drafted.first { it.name == "Brad Daugherty" }
        assertTrue("No. 1", daugherty.awards.contains("1986 Draft #1"))
        assertEquals("UNC", daugherty.highSchool)
        assertTrue("his college seasons", daugherty.career.count { it.team == "UNC" } >= 3)
        val rodman = drafted.firstOrNull { it.name == "Dennis Rodman" }
        if (rodman != null) {
            val peak = EraData.draft(1986).first { it.name == "Dennis Rodman" }.peak
            println("Rodman ${rodman.ovr}/${rodman.pot}, peak in the data $peak")
            assertTrue("the ceiling he reached", rodman.pot >= peak - 1)
        }
        assertTrue("most of the class is real", drafted.count { p -> EraData.draft(1986).any { it.name == p.name } } >= 50)
    }
}
