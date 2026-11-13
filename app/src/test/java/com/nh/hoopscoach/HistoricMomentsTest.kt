package com.nh.hoopscoach

import com.nh.hoopscoach.game.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class HistoricMomentsTest {
    @Test
    fun courtStormCandidateNeedsHomeUnderdog() {
        Rng.r = Random(9)
        val lg = LeagueGen.newLeague()
        val home = lg.user
        val away = lg.teams.first { it.id != home.id }
        home.elo = 1400.0
        away.elo = 1600.0
        val g = Game(id = 99, day = lg.day, home = home.id, away = away.id, neutral = false, type = GameType.NONCONF, hs = 78, aws = 74)
        assertTrue(HistoricMoments.isCourtStormCandidate(lg, g))
        val roadWin = Game(id = 100, day = lg.day, home = home.id, away = away.id, neutral = false, type = GameType.NONCONF, hs = 70, aws = 80)
        assertFalse(HistoricMoments.isCourtStormCandidate(lg, roadWin))
    }

    @Test
    fun fallbackPackageUsesMomentFacts() {
        Rng.r = Random(3)
        val lg = LeagueGen.newLeague()
        val m = Moment(
            lg.season, lg.day, MomentKind.COURT_STORM, "Game ${lg.day}",
            "Court storm! ${lg.user.school} knocks off rival",
            "Fans rushed the floor after a recorded final.",
            lg.userTeamId, lg.teams.first { it.id != lg.userTeamId }.id,
            wScore = 71, lScore = 68, mine = true, weight = 60, gameId = -1,
        )
        val pack = HistoricMoments.fallbackPackage(lg, m)
        assertEquals(1, pack.stories.size)
        assertTrue(pack.stories.first().body.contains("Fans") || pack.stories.first().body.contains("floor"))
        assertFalse(pack.isAi)
    }

    @Test
    fun historicFlagSurvivesSave() {
        val lg = LeagueGen.newLeague()
        lg.ollama.historicMoments = false
        assertFalse(SaveIO.decode(SaveIO.encode(lg)).ollama.historicMoments)
    }
}
