package com.nh.hoopscoach

import com.nh.hoopscoach.game.Arch
import com.nh.hoopscoach.game.GameEngine
import com.nh.hoopscoach.game.LeagueGen
import com.nh.hoopscoach.game.NilDeal
import com.nh.hoopscoach.game.OffStep
import com.nh.hoopscoach.game.Offseason
import com.nh.hoopscoach.game.Phase
import com.nh.hoopscoach.game.Pos
import com.nh.hoopscoach.game.Ratings
import com.nh.hoopscoach.game.Rival
import com.nh.hoopscoach.game.Road
import com.nh.hoopscoach.game.Rng
import com.nh.hoopscoach.game.SaveIO
import com.nh.hoopscoach.game.Season
import com.nh.hoopscoach.game.Summer
import com.nh.hoopscoach.game.SummerEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Archetypes, Spotlight live games, the class rival and summer events. */
class RoadExtrasTest {

    @Test
    fun archetypes() {
        val styles = Arch.entries.filter { it != Arch.MANUAL }
        assertEquals("thirty archetypes", 30, styles.size)
        assertEquals("short codes are unique", styles.size, styles.map { it.short }.toSet().size)
        assertTrue("each boosts three ratings", styles.all { it.boosts.size == 3 && it.boosts.toSet().size == 3 })
        for (pos in Pos.entries) assertTrue("$pos has at least 11 styles", Ratings.styles(pos).size >= 11)
        assertTrue("every archetype is used somewhere", styles.all { a -> Pos.entries.any { a in Ratings.styles(it) } })
        Rng.r = Random(1)
        for (pos in Pos.entries) for (a in Ratings.styles(pos)) {
            val attrs = Ratings.build(pos, a, 75)
            val ovr = Ratings.ovr(pos, attrs)
            assertTrue("$pos ${a.label} builds near its target ($ovr)", ovr in 72..78)
            val boosted = a.boosts.map { attrs[it.ordinal] }.average()
            val rest = attrs.indices.filter { i -> a.boosts.none { it.ordinal == i } }.map { attrs[it] }.average()
            assertTrue("$pos ${a.label} is strongest where it says", boosted > rest)
        }
    }

    @Test
    fun spotlightRivalAndSummers() {
        Rng.r = Random(11)
        var lg = LeagueGen.newLeague()
        val hero = Road.createHero(lg, Road.Spec("Dre", "Collins", Pos.PG, 74, 185, "Memphis", "TN", Arch.TWO_WAY_GUARD, 5, 1))
        val pick = Road.offers(lg, hero).minBy { it.ahead }
        lg = Road.start(lg, hero, pick.teamId)
        val r = lg.road!!

        // The rival: a five-star at the same position at another school in the same conference.
        val rv = Rival.active(lg)!!
        val home = lg.team(hero.teamId)
        println("rival: ${rv.name}, ${rv.pos}, ${lg.team(rv.teamId).school} (prestige ${lg.team(rv.teamId).prestige}), ovr ${rv.ovr}/${rv.pot}, No. ${rv.recruitRank}; you ovr ${hero.ovr}/${hero.pot} at ${home.school}")
        assertEquals(hero.pos, rv.pos)
        assertEquals(1, rv.year)
        assertEquals(5, rv.stars)
        assertTrue("rival at another school", rv.teamId != hero.teamId)
        assertEquals("same conference", home.confId, lg.team(rv.teamId).confId)
        assertTrue("rival on the roster", rv.id in lg.team(rv.teamId).roster)
        assertTrue("they meet this season", lg.games.any { Rival.isDuel(lg, it) })
        assertTrue(r.rivalName == rv.name)

        // Spotlight: stepping to the hero's plays takes a game in a few dozen stops.
        Road.chooseDeal(lg, NilDeal.STEADY)
        val stopsPer = mutableListOf<Int>()
        val playsPer = mutableListOf<Int>()
        var tries = 0
        while (stopsPer.size < 6 && tries++ < 40) {
            val g = Season.userGameToday(lg)
            if (g == null) { Season.playDay(lg); continue }
            val e = GameEngine(lg, g, true)
            var stops = 0
            while (!e.finished && stops < 400) { e.stepToHero(); stops++ }
            assertTrue("the game ends", e.finished)
            val heroPlays = e.events.count { it.hero }
            assertTrue("every stop but the last lands on one of his plays", stops <= heroPlays + 1)
            stopsPer += stops
            playsPer += heroPlays
            e.commit()
            Season.playDay(lg)
        }
        println("spotlight: stops per game $stopsPer, his plays $playsPer; about %.1f minutes a game at 2x".format(stopsPer.average() * 3.0 / 60))
        assertTrue("he's in plenty of plays", playsPer.average() >= 10)
        assertTrue("a spotlight game is short", stopsPer.average() in 8.0..45.0)

        var seasons = 0
        var rivalPresses = 0
        var summers = 0
        val notes = mutableListOf<String>()
        while (!r.ended && seasons < 5) {
            seasons++
            if (Road.needsDeal(lg)) Road.chooseDeal(lg, NilDeal.STEADY)
            var guard = 0
            while (lg.phase != Phase.WRAP_UP && guard++ < 120) {
                Season.playDay(lg)
                lg.road!!.press?.let { q -> if (q.topic == "Rival") rivalPresses++; Road.answer(lg, 1) }
            }
            val me = lg.playerOrNull(lg.heroId)!!
            val them = Rival.player(lg)!!
            val (w, l) = Rival.record(lg)
            println("season ${lg.season}: you ${me.ovr} ovr %.1f ppg, race No. ${Rival.raceRank(lg, me)} · ${them.last} ${them.ovr} ovr %.1f ppg, race No. ${Rival.raceRank(lg, them)} · head to head $w-$l".format(me.stats.ppg, them.stats.ppg))
            lg = SaveIO.decode(SaveIO.encode(lg))
            Offseason.begin(lg)
            val rank = Offseason.draftBoard(lg)[me.id]
            lg.road!!.declared = me.year < 4 && rank != null && rank <= 10
            Road.next(lg)
            if (lg.road!!.ended) break
            assertEquals(OffStep.DRAFT, lg.offseason!!.step)
            assertTrue("no summer events before the draft is over", !Summer.open(lg))
            Road.next(lg)
            // Summer: one invitation per summer.
            assertTrue("summer is open", Summer.open(lg))
            val inv = Summer.invites(lg)
            val h = lg.playerOrNull(lg.heroId)!!
            val ev = inv.first()
            val attrs = h.attrs.copyOf()
            val followers = lg.road!!.followers
            val note = Summer.attend(lg, ev)
            notes += note
            summers++
            println("   summer ${lg.season}: invites ${inv.map { it.name }} -> $note")
            assertTrue(note.isNotEmpty())
            assertTrue("followers grow", lg.road!!.followers > followers)
            if (ev == SummerEvent.U19) assertTrue("U19 raises every rating", h.attrs.indices.all { h.attrs[it] >= attrs[it] } && h.attrs.sum() > attrs.sum())
            else for (a in ev.boosts) assertTrue("${a.label} goes up", h.attrs[a.ordinal] > attrs[a.ordinal] || attrs[a.ordinal] == 99)
            assertTrue("one per summer", !Summer.open(lg))
            assertEquals("", Summer.attend(lg, ev))
            lg = SaveIO.decode(SaveIO.encode(lg))
            assertTrue("save keeps the summer", lg.road!!.summer == lg.season && lg.road!!.summerNote == note)
            Road.next(lg)
            assertEquals(Phase.REGULAR, lg.phase)
        }
        val rr = lg.road!!
        val (w, l) = Rival.record(lg)
        println("career: ${rr.ending}")
        println("duels: ${rr.duels.joinToString { "${it.season} ${if (it.won) "W" else "L"} ${it.me}-${it.him}" }}; rival press questions $rivalPresses; rival ${rr.rivalLeft.ifEmpty { "still in college" }}")
        rr.story.forEach { println("  $it") }
        assertTrue("career ended", rr.ended)
        assertTrue("they played each other", w + l >= 2)
        assertTrue("the press asks about the rival", rivalPresses >= 1)
        assertTrue("the ending mentions the rival", rr.ending.contains("rival"))
        assertTrue("summers happened", summers >= 1 || seasons == 1)
        assertTrue("summer shows in the story", summers == 0 || rr.story.any { it.contains("Summer:") })
        assertTrue("U19 shows up for a five-star", notes.any { it.startsWith("U19") } || summers == 0)
    }
}
