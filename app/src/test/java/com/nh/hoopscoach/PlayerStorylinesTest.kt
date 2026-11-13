package com.nh.hoopscoach

import com.nh.hoopscoach.game.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class PlayerStorylinesTest {
    @Test
    fun detectsBenchToStarAndWalkOnHero() {
        Rng.r = Random(21)
        val lg = LeagueGen.newLeague()
        val p = lg.rosterOf(lg.user).first()
        p.career += SeasonLine(lg.season - 1, lg.user.school, "X", 1, 68, StatLine().also {
            it.gp = 30; it.sec = 30 * 12 * 60; it.pts = 150
        })
        p.stats.gp = 20
        p.stats.sec = 20 * 30 * 60
        p.stats.pts = 320
        for (i in p.attrs.indices) p.attrs[i] = 88
        p.ovrHistory.clear()
        p.ovrHistory += listOf(70, 76, p.ovr)

        val walk = lg.rosterOf(lg.user).getOrNull(1) ?: p
        walk.walkOn = true
        walk.tryout = lg.season - 1
        walk.story = 1
        walk.stats.gp = 12
        walk.stats.pts = 180
        walk.tourney.gp = 2
        walk.tourney.pts = 40

        val arcs = PlayerStorylines.detectArcs(lg)
        assertTrue(arcs.any { it.kind == "BENCH_TO_STAR" })
        assertTrue(arcs.any { it.kind == "WALKON_HERO" })
        val pack = PlayerStorylines.fallbackPackage(lg)
        assertTrue(pack.stories.isNotEmpty())
        assertFalse(pack.isAi)
    }

    @Test
    fun detectsBlueChipHeatCheck() {
        Rng.r = Random(5)
        val lg = LeagueGen.newLeague()
        val p = lg.rosterOf(lg.user).first()
        p.stars = 5
        p.recruitRank = 3
        p.year = 2
        for (i in p.attrs.indices) p.attrs[i] = 62
        p.stats.gp = 15
        p.stats.pts = 90
        p.stats.sec = 15 * 18 * 60
        assertTrue(p.ovr <= 76)
        assertTrue(PlayerStorylines.detectArcs(lg).any { it.kind == "RECRUIT_BUST" && it.pid == p.id })
    }

    @Test
    fun flagSurvivesSave() {
        val lg = LeagueGen.newLeague()
        lg.ollama.playerStorylines = false
        assertFalse(SaveIO.decode(SaveIO.encode(lg)).ollama.playerStorylines)
    }
}
