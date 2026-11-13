package com.nh.hoopscoach

import com.nh.hoopscoach.game.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class DraftUniverseTest {
    @Test
    fun briefIncludesStockAndWorkout() {
        Rng.r = Random(17)
        val lg = LeagueGen.newLeague()
        val brief = DraftUniverse.eventBrief(lg)
        assertTrue(brief.contains("DRAFT STOCK") || brief.contains("DRAFT"))
        val p = lg.rosterOf(lg.user).first()
        val profile = DraftUniverse.workoutProfile(p)
        assertTrue(profile.contains("lb"))
        assertTrue(profile.contains("\""))
        val pack = DraftUniverse.fallbackPackage(lg)
        assertTrue(pack.stories.isNotEmpty())
        assertFalse(pack.isAi)
        assertTrue(pack.stories.any { it.source.contains("Draft") })
    }

    @Test
    fun alumniLineUsesDraftHistory() {
        Rng.r = Random(4)
        val lg = LeagueGen.newLeague()
        val p = lg.rosterOf(lg.user).first()
        lg.draftHistory += DraftPick(
            season = lg.season - 1, pick = 8, playerName = p.name, pos = p.pos,
            teamId = lg.userTeamId, school = lg.user.school, cls = p.year, ovr = p.ovr,
            pid = p.id, nba = "Boston Celtics", proTeam = "Boston Celtics",
        )
        val brief = DraftUniverse.eventBrief(lg)
        assertTrue(brief.contains("FORMER PLAYERS") || brief.contains(p.name))
        val pack = DraftUniverse.fallbackPackage(lg)
        assertTrue(pack.stories.any { it.text.contains(p.name) || it.body.contains("Celtics") || it.body.contains(p.name) })
    }

    @Test
    fun flagSurvivesSave() {
        val lg = LeagueGen.newLeague()
        lg.ollama.draftUniverse = false
        assertFalse(SaveIO.decode(SaveIO.encode(lg)).ollama.draftUniverse)
    }
}
