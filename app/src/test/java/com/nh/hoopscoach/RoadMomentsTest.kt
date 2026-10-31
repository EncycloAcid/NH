package com.nh.hoopscoach

import com.nh.hoopscoach.game.Arch
import com.nh.hoopscoach.game.GameEngine
import com.nh.hoopscoach.game.LeagueGen
import com.nh.hoopscoach.game.NilDeal
import com.nh.hoopscoach.game.Phase
import com.nh.hoopscoach.game.Pos
import com.nh.hoopscoach.game.Practice
import com.nh.hoopscoach.game.Road
import com.nh.hoopscoach.game.Rng
import com.nh.hoopscoach.game.Season
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Big moments, practice plans and award races over one player-mode season. */
class RoadMomentsTest {

    @Test
    fun seasonOfChoices() {
        Rng.r = Random(12)
        var lg = LeagueGen.newLeague()
        val hero = Road.createHero(lg, Road.Spec("Dre", "Collins", Pos.SG, 77, 200, "Memphis", "TN", Arch.SHARPSHOOTER, 5, 0))
        val offer = Road.offers(lg, hero).minBy { it.ahead }
        lg = Road.start(lg, hero, offer.teamId)
        Road.chooseDeal(lg, NilDeal.STEADY)
        val r = lg.road!!
        val h = lg.player(lg.heroId)
        val startOut = h.attrs[com.nh.hoopscoach.game.Attr.OUT.ordinal]
        Road.setPractice(lg, Practice.SHOOTING)

        var offered = 0
        var games = 0
        var maxFatigue = 0
        val picks = GameEngine.MomentChoice.entries
        var guard = 0
        while (lg.phase == Phase.REGULAR && guard++ < 60) {
            val g = Season.userGameToday(lg)
            if (g == null) { Season.playDay(lg); continue }
            // Halfway through, rest to bring fatigue down.
            if (games == 15) Road.setPractice(lg, Practice.REST)
            val quiet = GameEngine(lg, g, false)
            assertEquals("simmed games never stop for moments", null, quiet.pendingMoment())
            val e = GameEngine(lg, g, true).also { it.interactive = true }
            var steps = 0
            while (!e.finished && steps++ < 2000) {
                val m = e.pendingMoment()
                if (m != null) {
                    assertTrue(m.options.size in 2..3)
                    assertTrue(m.options.all { it.third in 5..95 })
                    assertTrue(m.clock <= 120 && kotlin.math.abs(m.margin) <= 5)
                    e.playMoment(picks[offered % picks.size])
                    offered++
                    assertTrue("every moment has a result", e.lastMoment != null)
                } else e.step()
            }
            e.commit()
            val side = if (g.home == lg.userTeamId) 0 else 1
            val box = g.box!!
            val pts = (if (side == 0) box.home else box.away).sumOf { it.s.pts }
            assertEquals("box score adds up", g.scoreFor(lg.userTeamId), pts)
            Season.playDay(lg)
            games++
            maxFatigue = maxOf(maxFatigue, r.fatigue)
        }
        println("games $games, big moments offered $offered, recorded ${r.moments}, came through ${r.momentsWon}")
        println("fatigue peak $maxFatigue, now ${r.fatigue} (${Road.fatigueLabel(r.fatigue)}); 3PT ${startOut} -> ${h.attrs[com.nh.hoopscoach.game.Attr.OUT.ordinal]}, ovr ${h.ovr}")
        println("POY race: No. ${r.raceRank} (last week ${r.lastRaceRank}), live ${Road.raceRank(lg)} · ${Road.allAmericaPace(Road.raceRank(lg))}")
        assertTrue("close games bring big moments", offered >= 2)
        assertEquals(offered, r.moments)
        assertTrue(r.momentsWon in 0..r.moments)
        assertTrue("shooting reps build the three", h.attrs[com.nh.hoopscoach.game.Attr.OUT.ordinal] > startOut)
        assertTrue("fatigue stays in range", maxFatigue in 1..100)
        assertTrue("the race is tracked", r.raceRank > 0)
    }
}
