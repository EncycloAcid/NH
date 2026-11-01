package com.nh.hoopscoach

import com.nh.hoopscoach.game.*
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

/** Leading your team and earning your growth. */
class ImpactTest {
    private fun gain(strong: Boolean): Pair<Int, String> {
        val lg = ProTest().toDraft(21)
        val h = lg.hero!!
        // A sophomore with room to grow (no ratings near the cap).
        for (i in h.attrs.indices) h.attrs[i] = (h.attrs[i] - 14).coerceAtLeast(30)
        h.pot = h.ovr + 14
        h.stats = if (strong) StatLine(gp = 30, gs = 30, sec = 34 * 60 * 30, pts = 620, fgm = 240, fga = 470, drb = 190, ast = 120, stl = 40, blk = 20, tov = 60)
            else StatLine(gp = 20, gs = 0, sec = 6 * 60 * 20, pts = 30, fgm = 12, fga = 40, drb = 15, ast = 6, tov = 10)
        lg.road!!.work = if (strong) 27.0 else 3.0
        lg.road!!.workDays = 30
        val before = h.ovr
        Rng.r = Random(1)
        Earned.hero(lg, h, 70)
        return (h.ovr - before) to lg.road!!.growthNote
    }

    @Test
    fun growthIsEarned() {
        val (a, na) = gain(true)
        val (b, nb) = gain(false)
        println("starter who works: +$a ($na)\nbenchwarmer who rests: +$b ($nb)")
        assertTrue("playing and working pays", a >= b + 3)
        assertTrue(na.contains("starter's minutes") && nb.contains("few minutes"))
    }

    @Test
    fun leadingTheTeam() {
        Rng.r = Random(4)
        val lg = ProLeague.create(ProTest().toDraft(21))
        while (lg.phase == Phase.OFFSEASON) ProCareer.next(lg)
        val h = lg.hero!!
        val t = lg.team(h.teamId)
        println("lift at ${h.ovr}: ${Leadership.lift(lg, lg.rosterOf(t))}")
        h.proYears = 2
        var guard = 0
        while (h.ovr < 97 && guard++ < 600) { val i = Rng.int(0, 9); if (h.attrs[i] < 99) h.attrs[i]++ }
        lg.road!!.leadership = 60
        Gameplan.autoAllocate(lg, t)
        val lift = Leadership.lift(lg, lg.rosterOf(t))
        println("lift at ${h.ovr} as a captain: $lift")
        assertTrue("a star lifts his teammates", lift > 0.006)
        repeat(20) { Sim.playDay(lg); lg.road!!.press?.let { Road.answer(lg, 0) } }
        // Plus-minus adds up: five players on the floor share every point.
        for (g in lg.games.filter { it.played && it.box != null }.take(5)) {
            val home = g.box!!.home.sumOf { it.s.pm }
            println("game ${g.id}: margin ${g.hs - g.aws}, home plus-minus total $home")
            assertTrue("plus-minus sums to five times the margin", abs(home - 5 * (g.hs - g.aws)) <= 10)
        }
        println("his plus-minus: ${h.stats.pm} in ${h.stats.gp} games; projection ${Leadership.projection(lg, t)}")
        val before = lg.road!!.leadership
        Leadership.seasonEnd(lg)
        println("leadership $before -> ${lg.road!!.leadership}")
        assertTrue("a starter's season builds leadership", lg.road!!.leadership > before)
    }
}
