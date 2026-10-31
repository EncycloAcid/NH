package com.nh.hoopscoach

import com.nh.hoopscoach.game.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** The postseason the way the app plays it: the user's game live, then the rest of the day. */
class PlayoffFlowTest {
    /** One app day: play the user's game live if there is one, then sim the rest (what completeLive does). */
    private fun appDay(lg: League, live: Boolean): Game? {
        val g = if (live) Season.userGameToday(lg) else null
        if (g != null) {
            val e = GameEngine(lg, g, true).also { it.interactive = lg.playerMode }
            e.simToEnd(); e.commit()
        }
        Sim.playDay(lg)
        return g
    }

    private fun checkDay(lg: League, day: Int) {
        val today = lg.games.filter { it.day == day }
        val teams = today.flatMap { listOf(it.home, it.away) }
        assertEquals("nobody plays twice on day $day: ${today.groupBy { it.home }.filter { it.value.size > 1 }.keys}", teams.size, teams.toSet().size)
        assertTrue("every game on day $day is played", today.all { it.played })
    }

    @Test
    fun proPlayoffsWithLiveGames() {
        Rng.r = Random(3)
        val lg = ProLeague.create(ProTest().toDraft(21))
        while (lg.phase == Phase.OFFSEASON) ProCareer.next(lg)
        var g = 0
        while (lg.phase == Phase.REGULAR && g++ < 200) Sim.playDay(lg)
        assertEquals(Phase.PLAYOFFS, lg.phase)
        val ps = lg.proState!!
        // During the play-in, seeds 1 to 6 are in the playoffs, waiting: never "missed the playoffs".
        assertTrue("the play-in is on", ps.series.all { it.round == 0 })
        for ((tid, seed) in ps.seeds) if (seed <= 6) assertEquals("No. $seed seed is in", "Playoffs", ProSeason.resultFor(lg, tid))
        val liveGames = mutableListOf<Game>()
        g = 0
        while (lg.phase == Phase.PLAYOFFS && g++ < 80) {
            val day = lg.day
            appDay(lg, live = true)?.let { liveGames += it }
            checkDay(lg, day)
        }
        assertEquals(Phase.OFFSEASON, lg.phase)
        for (s in ps.series) {
            val played = s.gameIds.map { lg.game(it) }.filter { it.played }
            val hi = played.count { it.winner() == s.hi }
            assertEquals("series wins match the games (${lg.team(s.hi).abbr}-${lg.team(s.lo).abbr})", hi, s.hiWins)
            assertEquals(played.size - hi, s.loWins)
            assertTrue("no games after a series ends", played.size == s.hiWins + s.loWins)
        }
        println("pro: ${liveGames.size} live games, champion ${lg.team(ps.champion).name}")
    }

    private fun collegeSeason(playerMode: Boolean): League {
        Rng.r = Random(4)
        val base = LeagueGen.newLeague()
        if (!playerMode) return Offseason.claimTeam(base, base.teams.first { it.school == "Duke" }.id, "Test", "Coach")
        val hero = Road.createHero(base, Road.Spec("Malik", "Grant", Pos.SF, 79, 215, "Chicago", "IL", Arch.SLASHER, 5, 1))
        val lg = Road.start(base, hero, Road.offers(base, hero).minBy { it.ahead }.teamId)
        if (Road.needsDeal(lg)) Road.chooseDeal(lg, NilDeal.STEADY)
        return lg
    }

    @Test
    fun collegeTournamentsWithLiveGames() {
        for (playerMode in listOf(false, true)) {
            val lg = collegeSeason(playerMode)
            var g = 0
            while (lg.phase == Phase.REGULAR && g++ < 200) appDay(lg, live = true)
            var live = 0
            g = 0
            while (!Season.seasonOver(lg) && g++ < 120) {
                val day = lg.day
                if (appDay(lg, live = true) != null) live++
                checkDay(lg, day)
            }
            val ncaa = lg.ncaa!!
            assertTrue("NCAA champion (${if (playerMode) "player" else "coach"} mode)", ncaa.done && ncaa.champion >= 0)
            for (b in lg.confBrackets) assertTrue("${b.name} champion", b.done && b.champion >= 0)
            // Every NCAA game's winner moved on (or won it all).
            println("${if (playerMode) "player" else "coach"} mode: ${live} live postseason games, champion ${lg.team(ncaa.champion).school}, phase ${lg.phase}")
        }
    }
}
