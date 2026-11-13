package com.nh.hoopscoach

import com.nh.hoopscoach.game.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class UniversityDevelopmentTest {
    @Test
    fun briefAndFallbackCoverCampus() {
        Rng.r = Random(19)
        val lg = LeagueGen.newLeague()
        Business.ensure(lg, lg.user)
        val brief = UniversityDevelopment.campusBrief(lg)
        assertTrue(brief.contains("ARENA"))
        assertTrue(brief.contains("PROGRAM PULL") || brief.contains("PULL"))
        val pack = UniversityDevelopment.fallbackPackage(lg)
        assertTrue(pack.stories.isNotEmpty())
        assertFalse(pack.isAi)
        assertTrue(pack.stories.any { it.source.contains("Campus") })
    }

    @Test
    fun newUpgradesBuildAndAffectProgram() {
        Rng.r = Random(2)
        val lg = LeagueGen.newLeague()
        val t = lg.user
        Business.ensure(lg, t)
        t.cash = 100_000
        val beforeArena = t.arena
        val beforeBuzz = t.buzz.coerceAtLeast(0.0)
        assertEquals("Arena renovation built.", Business.buy(lg, t, "arena"))
        assertTrue(t.arena >= beforeArena)
        assertTrue("arena" in t.upgrades)
        assertEquals("National brand campaign built.", Business.buy(lg, t, "brand"))
        assertTrue(t.buzz >= beforeBuzz + 7)
        assertTrue(Business.UPGRADES.any { it.id == "training" })
        assertTrue(Business.UPGRADES.any { it.id == "recruiting" })
    }

    @Test
    fun flagSurvivesSave() {
        val lg = LeagueGen.newLeague()
        lg.ollama.universityDevelopment = false
        assertFalse(SaveIO.decode(SaveIO.encode(lg)).ollama.universityDevelopment)
    }
}
