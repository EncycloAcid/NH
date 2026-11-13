package com.nh.hoopscoach

import com.nh.hoopscoach.game.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class MediaEcosystemTest {
    @Test
    fun briefAndFallbackCoverNetworkBeats() {
        Rng.r = Random(42)
        val lg = LeagueGen.newLeague()
        Brand.ensure(lg)
        val brief = MediaEcosystem.ecosystemBrief(lg)
        assertTrue(brief.contains("YOUR PROGRAM") || brief.contains("LEVEL"))
        assertTrue(brief.contains("NATIONAL POLL") || brief.contains("TODAY") || brief.contains("Tournament") || brief.contains("Playoff"))
        val pack = MediaEcosystem.fallbackPackage(lg)
        assertTrue(pack.stories.isNotEmpty())
        assertFalse(pack.isAi)
        assertTrue(pack.stories.any { it.source.contains("Network") })
        assertTrue(pack.stories.any { it.text.contains("Analysts", ignoreCase = true) || it.kind == "column" })
    }

    @Test
    fun flagSurvivesSave() {
        val lg = LeagueGen.newLeague()
        lg.ollama.mediaEcosystem = false
        assertFalse(SaveIO.decode(SaveIO.encode(lg)).ollama.mediaEcosystem)
    }
}
