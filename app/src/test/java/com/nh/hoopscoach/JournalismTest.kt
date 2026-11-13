package com.nh.hoopscoach

import com.nh.hoopscoach.game.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class JournalismTest {
    @Test
    fun eventBriefAndFallbackStayGrounded() {
        Rng.r = Random(42)
        val lg = LeagueGen.newLeague()
        lg.day = 12
        val brief = Journalism.eventBrief(lg)
        assertTrue(brief.contains("SEASON") || brief.contains("LEAGUE SITUATION"))
        assertTrue(brief.contains("POWER BOARD") || brief.contains("AP POLL") || brief.contains("NATIONAL TITLES"))
        assertTrue(brief.contains(lg.user.school))
        assertTrue(brief.contains("TITLE") || brief.contains("CHAMPION") || brief.contains("title"))
        val pack = Journalism.fallbackPackage(lg)
        assertTrue(pack.stories.size >= 2)
        assertFalse(pack.isAi)
        assertTrue(pack.stories.any { it.kind == "column" })
        assertTrue(pack.stories.any { it.body.isNotBlank() })
        assertTrue(pack.stories.all { it.source.contains("Hoops Wire Desk") })
        assertTrue(pack.stories.any { it.text.contains("Title", ignoreCase = true) || it.body.contains("title", ignoreCase = true) || it.body.contains("champion", ignoreCase = true) || it.body.contains("banner", ignoreCase = true) })
    }

    @Test
    fun sportsJournalismFlagSurvivesSave() {
        val lg = LeagueGen.newLeague()
        lg.ollama.sportsJournalism = false
        val restored = SaveIO.decode(SaveIO.encode(lg))
        assertFalse(restored.ollama.sportsJournalism)
        restored.ollama.sportsJournalism = true
        assertTrue(SaveIO.decode(SaveIO.encode(restored)).ollama.sportsJournalism)
    }
}
