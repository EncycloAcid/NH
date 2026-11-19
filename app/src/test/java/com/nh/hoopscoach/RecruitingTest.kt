package com.nh.hoopscoach

import com.nh.hoopscoach.game.Brand
import com.nh.hoopscoach.game.Geo
import com.nh.hoopscoach.game.OffStep
import com.nh.hoopscoach.game.Offseason
import com.nh.hoopscoach.game.Phase
import com.nh.hoopscoach.game.Recruiting
import com.nh.hoopscoach.game.Rng
import com.nh.hoopscoach.game.SaveIO
import com.nh.hoopscoach.game.Season
import com.nh.hoopscoach.game.Tryouts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** The five-round recruiting cycle: budgets, rival moves, previews that come true, commitments and the end of the cycle. */
class RecruitingTest {

    @Test
    fun fiveRoundCycle() {
        Rng.r = Random(21)
        val lg = Offseason.newGame(5, "Test", "Coach")
        for (t in lg.teams) assertTrue("state for ${t.school}", Geo.known(t.abbr))
        assertEquals(Recruiting.CLASS_SIZE, lg.recruits.size)
        assertTrue("in season, nobody commits", lg.recruits.none { it.committed >= 0 })
        var guard = 0
        while (lg.phase != Phase.WRAP_UP && guard++ < 120) Season.playDay(lg)
        Offseason.begin(lg)
        val os = lg.offseason!!
        while (os.step != OffStep.RECRUITING) {
            if (os.step == OffStep.CAREER && os.fired) com.nh.hoopscoach.game.Coaching.acceptOffer(lg, os.offers.first().teamId)
            Offseason.advance(lg)
        }
        val u = lg.user
        val pull = Brand.pull(lg, u)
        val open = Recruiting.openSpots(lg, u)
        println("user ${u.school} pull=$pull budget=${lg.recruitBudget} open=$open prospects=${lg.recruits.size} portal=${lg.recruits.count { it.transfer }}")
        assertTrue(lg.recruitBudget > 0)
        assertTrue("rivals moved before round 1", lg.recruits.any { r -> r.alloc.keys.any { it != u.id } })

        // NIL adds interest on its own, more for a bigger offer, and offers can't exceed the NIL room.
        run {
            val c = Recruiting.Ctx(lg)
            val r = lg.recruits.first { it.committed < 0 && u.id !in it.interest && u.id !in it.alloc && it.nilAsk > 0 }
            assertEquals("no offer, no interest", null, Recruiting.project(lg, r, c)[u.id])
            assertTrue(Recruiting.setNil(lg, r, r.nilAsk) || r.nilOffer > 0)
            val atAsk = Recruiting.project(lg, r, c)[u.id] ?: 0
            Recruiting.setNil(lg, r, r.nilAsk * 2)
            val atDouble = Recruiting.project(lg, r, c)[u.id] ?: 0
            println("NIL alone on ${r.p.name} (ask ${r.nilAsk}K, room ${Recruiting.nilRoom(lg, r)}K): ask -> $atAsk, double -> $atDouble")
            assertTrue("NIL alone puts you in the race", atAsk > 4)
            assertTrue("a bigger offer adds more", atDouble >= atAsk)
            assertTrue("can't offer past the NIL room", !Recruiting.setNil(lg, r, Recruiting.nilRoom(lg, r) + 1000))
            assertTrue(r.nilOffer <= Recruiting.nilRoom(lg, r))
            Recruiting.setNil(lg, r, 0)
            assertEquals(null, Recruiting.project(lg, r, c)[u.id])
        }
        run {
            val c = Recruiting.Ctx(lg)
            val r = lg.recruits.first { it.committed < 0 && it.nilAsk > 0 && it.p.stars >= 3 }
            Recruiting.setPoints(lg, r, 50)
            val pointsOnly = Recruiting.project(lg, r, c)[u.id] ?: 0
            Recruiting.setNil(lg, r, r.nilAsk)
            val withNil = Recruiting.project(lg, r, c)[u.id] ?: 0
            println("50 points on ${r.p.name}: $pointsOnly, with NIL at ask: $withNil")
            assertTrue("NIL adds to points", withNil > pointsOnly)
            Recruiting.setPoints(lg, r, 0); Recruiting.setNil(lg, r, 0)
        }

        // A simple user plan: about two targets per open spot that fit the program.
        val ctx = Recruiting.Ctx(lg)
        val targets = lg.recruits.filter { it.committed < 0 && Recruiting.wants(it) <= pull + 8 }
            .sortedByDescending { (it.p.ovr * 0.6 + it.p.pot * 0.4) * Recruiting.fit(lg, u, it, ctx) }
            .take(open * 2)
        val each = (lg.recruitBudget / targets.size.coerceAtLeast(1)) / 5 * 5
        for (r in targets) assertTrue(Recruiting.setPoints(lg, r, each))
        assertTrue("budget respected", Recruiting.available(lg) >= 0)
        assertTrue("can't overspend", !Recruiting.setPoints(lg, targets.first(), each + lg.recruitBudget))

        for (round in 1..Recruiting.ROUNDS) {
            // The preview is exact: interest after locking in matches it.
            val c = Recruiting.Ctx(lg)
            val previews = targets.filter { it.committed < 0 }.associate { it.p.id to Recruiting.project(lg, it, c)[u.id] }
            val t0 = System.currentTimeMillis()
            Offseason.advance(lg)
            val ms = System.currentTimeMillis() - t0
            for ((pid, v) in previews) {
                val r = lg.recruits.firstOrNull { it.p.id == pid } ?: continue
                if (r.committed < 0 && u.id in r.interest) assertEquals("preview for ${r.p.name}", v, r.interest[u.id])
            }
            val rep = os.report
            if (round < Recruiting.ROUNDS) {
                assertEquals(round, rep!!.round)
                println(
                    "round $round (${ms}ms): commits=${rep.commits} mine=${rep.mine.size} lostTargets=${rep.lost.size} dropped=${rep.dropped} entered=${rep.entered} " +
                        "committed so far=${lg.recruits.count { it.committed >= 0 }} available=${Recruiting.available(lg)}",
                )
                assertTrue("points freed when prospects commit", Recruiting.tied(lg) <= lg.recruitBudget)
                // Move freed points onto the best open targets.
                val left = targets.filter { it.committed < 0 }
                if (left.isNotEmpty()) {
                    val extra = Recruiting.available(lg) / left.size / 5 * 5
                    for (r in left) Recruiting.setPoints(lg, r, Recruiting.mine(lg, r) + extra)
                }
            }
        }
        assertEquals(OffStep.TRYOUTS, os.step)
        println("cycle over: user signed ${os.signed.size} of $open open; unsigned prospects=${os.unsigned}")
        val signed = os.signed.map { lg.player(it) }
        println("  user class: " + signed.joinToString { "${it.stars}★ ${it.pos} ${it.ovr}" })
        assertTrue("user lands somebody", os.signed.isNotEmpty())
        assertTrue("some prospects go unsigned", os.unsigned > 0)
        // Where the five-stars went.
        val fives = lg.players.filter { it.year == 1 && it.stars == 5 && it.teamId >= 0 }
        val pulls = Brand.pullTable(lg)
        println("  5★ landed at pull avg %.0f (league avg %.0f)".format(fives.map { pulls[it.teamId] }.average(), pulls.average()))
        assertTrue("five-stars go to strong programs", fives.map { pulls[it.teamId] }.average() > pulls.average() + 15)
        val save = SaveIO.decode(SaveIO.encode(lg))
        assertEquals(lg.players.size, save.players.size)
        Offseason.advance(lg)
        assertEquals(Phase.REGULAR, lg.phase)
        for (t in lg.teams) assertTrue("scholarships for ${t.school}", Tryouts.scholarships(lg, t) in com.nh.hoopscoach.game.WorldEvents.cap(lg, t)..Recruiting.MAX_ROSTER)
    }
}
