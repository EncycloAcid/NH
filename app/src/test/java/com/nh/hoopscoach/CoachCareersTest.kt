package com.nh.hoopscoach

import com.nh.hoopscoach.game.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class CoachCareersTest {
    @Test
    fun briefAndFallbackGrounded() {
        Rng.r = Random(11)
        val lg = LeagueGen.newLeague()
        val brief = CoachCareers.careerBrief(lg)
        assertTrue(brief.contains("SEASON"))
        assertTrue(brief.contains("LEVEL"))
        runCatching { lg.userCoach }.onSuccess { assertTrue(brief.contains(it.name) || brief.contains("HOT SEATS") || brief.contains("LEGACY")) }
        val pack = CoachCareers.fallbackPackage(lg)
        assertTrue(pack.stories.isNotEmpty())
        assertFalse(pack.isAi)
        assertTrue(pack.stories.any { it.source.contains("Sideline") })
    }

    @Test
    fun flagSurvivesSave() {
        val lg = LeagueGen.newLeague()
        lg.ollama.livingCoachingCareers = false
        assertFalse(SaveIO.decode(SaveIO.encode(lg)).ollama.livingCoachingCareers)
    }
}
