package com.nh.hoopscoach

import com.nh.hoopscoach.game.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class HistoricalRivalriesTest {
    @Test
    fun briefAndFallbackCoverSeries() {
        Rng.r = Random(13)
        val lg = LeagueGen.newLeague()
        Rivalries.ensure(lg)
        assertTrue(lg.rivals.isNotEmpty())
        val brief = HistoricalRivalries.historyBrief(lg)
        assertTrue(brief.contains("PROGRAM RIVALRIES") || brief.contains("RIVAL"))
        val pack = HistoricalRivalries.fallbackPackage(lg)
        assertTrue(pack.stories.isNotEmpty())
        assertFalse(pack.isAi)
        assertTrue(pack.stories.any { it.source.contains("Rivalries") })
    }

    @Test
    fun emergentRivalryFromRepeatedMeetings() {
        Rng.r = Random(8)
        val lg = LeagueGen.newLeague()
        Rivalries.ensure(lg)
        val a = lg.user
        val b = lg.teams.first { t -> t.id != a.id && lg.rivalry(a.id, t.id) == null }
        // Clear any accidental rivalry
        lg.rivals.removeAll { it.involves(a.id) && it.involves(b.id) }
        repeat(4) { i ->
            val home = if (i % 2 == 0) a.id else b.id
            val away = if (home == a.id) b.id else a.id
            lg.games += Game(
                id = 9000 + i, day = i + 1, home = home, away = away, neutral = false,
                type = if (i == 3) GameType.CONF_TOURNEY else GameType.CONF,
                hs = 70 + i, aws = 68,
            )
        }
        val g = lg.games.last()
        Rivalries.maybeEmergent(lg, g)
        val r = lg.rivalry(a.id, b.id)
        assertNotNull(r)
        assertEquals("Emerging rivalry", r!!.name)
    }

    @Test
    fun flagSurvivesSave() {
        val lg = LeagueGen.newLeague()
        lg.ollama.historicalRivalries = false
        assertFalse(SaveIO.decode(SaveIO.encode(lg)).ollama.historicalRivalries)
    }
}
