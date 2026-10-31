package com.nh.hoopscoach

import com.nh.hoopscoach.game.Arch
import com.nh.hoopscoach.game.GameMode
import com.nh.hoopscoach.game.LeagueGen
import com.nh.hoopscoach.game.NilDeal
import com.nh.hoopscoach.game.OffStep
import com.nh.hoopscoach.game.Offseason
import com.nh.hoopscoach.game.Phase
import com.nh.hoopscoach.game.Pos
import com.nh.hoopscoach.game.Recruiting
import com.nh.hoopscoach.game.Road
import com.nh.hoopscoach.game.Rng
import com.nh.hoopscoach.game.SaveIO
import com.nh.hoopscoach.game.Season
import com.nh.hoopscoach.game.Tryouts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** A whole player-mode career: offers, NIL deals, seasons, the portal, the draft decision and the ending. */
class RoadTest {

    @Test
    fun fullCareer() {
        Rng.r = Random(3)
        var lg = LeagueGen.newLeague()
        val spec = Road.Spec("Jalen", "Brooks", Pos.SG, 77, 195, "Atlanta", "GA", Arch.SHOT_CREATOR, 4, 3)
        val hero = Road.createHero(lg, spec)
        val offers = Road.offers(lg, hero)
        println("offers for a 4-star SG from GA (ovr ${hero.ovr}, pot ${hero.pot}): " + offers.joinToString { "${lg.team(it.teamId).school} (${it.role}, ${it.nil}K)" })
        assertEquals(8, offers.size)
        val pick = offers.minBy { it.ahead }
        lg = Road.start(lg, hero, pick.teamId)
        assertEquals(GameMode.PLAYER, lg.mode)
        assertEquals(pick.teamId, lg.userTeamId)
        assertTrue("hero on the roster", hero.id in lg.user.roster)
        assertTrue("roster within scholarships", Tryouts.scholarships(lg, lg.user) <= Recruiting.MAX_ROSTER)
        assertTrue(Road.needsDeal(lg))

        var seasons = 0
        var transferred = false
        var presses = 0
        val topics = mutableSetOf<String>()
        val startFollowers = lg.road!!.followers
        assertTrue("a four-star starts with a following", startFollowers in 40_000..80_000)
        while (!lg.road!!.ended && seasons < 6) {
            seasons++
            val deal = when (seasons % 3) { 1 -> NilDeal.STEADY; 2 -> NilDeal.PERFORMANCE; else -> NilDeal.BRAND }
            Road.chooseDeal(lg, deal)
            assertTrue(!Road.needsDeal(lg))
            val h = lg.playerOrNull(lg.heroId)!!
            assertEquals(Road.guaranteed(deal, lg.road!!.dealBase), h.nil)
            var guard = 0
            while (lg.phase != Phase.WRAP_UP && guard++ < 120) {
                Season.playDay(lg)
                lg.road!!.press?.let { q ->
                    presses++
                    topics += q.topic
                    val before = lg.road!!.followers
                    Road.answer(lg, presses % 3)
                    assertTrue("answering clears the question", lg.road!!.press == null)
                    if (q.answers[presses % 3].followers > 0) assertTrue("followers grow", lg.road!!.followers >= before)
                }
            }
            println("   media: ${Road.count(lg.road!!.followers)} followers, persona ${lg.road!!.persona} (${Road.personaLabel(lg.road!!.persona)}), milestones ${lg.road!!.milestone}, market ${Road.market(lg)}K")
            println("season ${lg.season} at ${lg.user.school}: ${h.cls} ovr ${h.ovr}, ${Road.role(lg)}, %.1f mpg %.1f ppg, team ${lg.user.record}".format(h.stats.mpg, h.stats.ppg))
            lg = SaveIO.decode(SaveIO.encode(lg))
            Offseason.begin(lg)
            val os = lg.offseason!!
            assertEquals(OffStep.ROSTER, os.step)
            val me = lg.playerOrNull(lg.heroId)!!
            val rank = Offseason.draftBoard(lg)[me.id]
            lg.road!!.declared = me.year < 4 && rank != null && rank <= 14
            val pool = lg.players.filter { it.teamId >= 0 && (it.year >= 4 || it.id in os.entrants) }
            println("   draft board rank $rank, year ${me.year}, ovr ${me.ovr}/${me.pot}; pool ${pool.size}, pool ovr>=${me.ovr}: ${pool.count { it.ovr >= me.ovr }}")
            if (!transferred && seasons == 2 && !lg.road!!.declared) lg.road!!.portal = true
            Road.next(lg)
            if (lg.road!!.ended) break
            assertEquals(OffStep.DRAFT, os.step)
            Road.next(lg)
            assertEquals(OffStep.DEVELOPMENT, os.step)
            if (lg.road!!.portal) {
                assertTrue("transfer offers", lg.road!!.offers.isNotEmpty())
                val to = lg.road!!.offers.first()
                val from = lg.userTeamId
                Road.transfer(lg, to.teamId)
                assertEquals(to.teamId, lg.userTeamId)
                assertTrue(lg.heroId !in lg.team(from).roster)
                transferred = true
            }
            lg = SaveIO.decode(SaveIO.encode(lg))
            Road.next(lg)
            assertEquals(Phase.REGULAR, lg.phase)
            assertTrue("hero still on a roster", lg.heroId in lg.user.roster)
            for (t in lg.teams) assertTrue("${t.school} roster valid", t.roster.all { lg.playerOrNull(it) != null })
        }
        val r = lg.road!!
        val d = lg.draftHistory.filter { it.season == lg.draftHistory.last().season }
        println("draft ${d.first().season}: " + listOf(0, 14, 29, 44, 59).mapNotNull { d.getOrNull(it) }.joinToString { "#${it.pick} ovr ${it.ovr} yr ${it.cls}" } +
            " · seniors drafted ${d.count { it.cls >= 4 }}, min senior ovr ${d.filter { it.cls >= 4 }.minOfOrNull { it.ovr }}")
        println("ended after $seasons seasons: ${r.ending} · NIL earned ${r.earned}K · schools ${r.schools.map { lg.team(it).abbr }}")
        r.story.forEach { println("  $it") }
        assertTrue(r.ended)
        assertTrue(r.ending.isNotEmpty())
        assertTrue("career kept", lg.hero != null && lg.alumni.any { it.id == lg.heroId })
        assertTrue(r.earned > 0)
        println("press questions answered: $presses, topics $topics; followers ${Road.count(startFollowers)} -> ${Road.count(r.followers)}; log: ${r.pressLog.takeLast(2)}")
        assertTrue("press questions come up", presses >= 10)
        assertTrue("several kinds of questions", topics.size >= 3)
        assertTrue("followers grow over a career", r.followers > startFollowers * 2)
        assertTrue("sponsors pay at milestones", r.milestone >= 1)
        assertEquals(seasons, r.story.size)
    }
}
