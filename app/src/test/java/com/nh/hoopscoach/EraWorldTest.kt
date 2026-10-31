package com.nh.hoopscoach

import com.nh.hoopscoach.game.Arch
import com.nh.hoopscoach.game.Era
import com.nh.hoopscoach.game.EraCollege
import com.nh.hoopscoach.game.Franchises
import com.nh.hoopscoach.game.NilDeal
import com.nh.hoopscoach.game.Offseason
import com.nh.hoopscoach.game.Phase
import com.nh.hoopscoach.game.Pos
import com.nh.hoopscoach.game.ProCareer
import com.nh.hoopscoach.game.ProLeague
import com.nh.hoopscoach.game.Rng
import com.nh.hoopscoach.game.Road
import com.nh.hoopscoach.game.SaveIO
import com.nh.hoopscoach.game.Season
import com.nh.hoopscoach.game.Summer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Era careers: the real teams season by season, and college years in the era with its real players. */
class EraWorldTest {
    @Test
    fun teamsByYear() {
        Rng.r = Random(3)
        val lg = ProLeague.create(ProTest().toDraft(21), Era.Y1986)
        assertEquals("23 teams in 1985-86", 23, lg.teams.size)
        assertTrue("no Heat yet", lg.teams.none { it.slot == "MIA" })
        assertTrue(lg.teams.any { it.name == "Seattle SuperSonics" } && lg.teams.any { it.name == "Washington Bullets" })
        while (lg.phase == Phase.OFFSEASON) ProCareer.next(lg)
        val games = lg.teams.map { t -> lg.games.count { it.involves(t.id) } }
        println("1986 schedule: ${games.toSet()} games, ${lg.proState!!.lastDay} days")
        assertTrue("82 each with 23 teams", games.all { it == 82 })
        assertEquals(82, ProLeague.seasonGames(lg))

        // 1988-89 and 1989-90: four expansion teams draft their rosters.
        for (y in 1987..1990) Franchises.apply(lg, y)
        println("1990: ${lg.teams.size} teams; " + lg.teams.filter { it.slot in setOf("MIA", "CHA", "MIN", "ORL") }.joinToString { "${it.name} ${lg.rosterOf(it).size}" })
        assertEquals(27, lg.teams.size)
        assertTrue("expansion rosters", lg.teams.filter { it.slot in setOf("MIA", "CHA", "MIN", "ORL") }.all { lg.rosterOf(it).size >= ProLeague.ROSTER })
        assertTrue("from the expansion draft", lg.news.count { it.text.startsWith("Expansion draft") } >= 30)
        assertTrue("each team loses at most one per draft", lg.teams.all { lg.rosterOf(it).size >= ProLeague.ROSTER })
        // 2002-03: the Hornets move to New Orleans and keep their players; 2004-05: the Bobcats.
        val hornets = lg.teams.first { it.slot == "CHA" }
        val roster = hornets.roster.toList()
        for (y in 1991..2003) Franchises.apply(lg, y)
        assertEquals("New Orleans Hornets", hornets.name)
        assertEquals("NOP", hornets.slot)
        assertTrue("the same team, most of the same players", hornets.roster.count { it in roster } >= 8)
        for (y in 2004..2005) Franchises.apply(lg, y)
        assertTrue(lg.teams.any { it.name == "Charlotte Bobcats" })
        for (y in 2006..2009) Franchises.apply(lg, y)
        assertTrue("the Sonics become the Thunder", lg.teams.any { it.name == "Oklahoma City Thunder" } && lg.teams.none { it.name == "Seattle SuperSonics" })
        assertEquals(30, lg.teams.size)
        assertTrue("conferences add up", lg.confs.sumOf { it.teamIds.size } == 30 && lg.confs.all { c -> c.teamIds.all { lg.team(it).confId == c.id } })
        assertEquals("it saves", 30, SaveIO.decode(SaveIO.encode(lg)).teams.size)
    }

    @Test
    fun collegeInTheEra() {
        Rng.r = Random(11)
        var lg = EraCollege.newLeague(Era.Y1986)
        assertEquals(1986, lg.season)
        val real = lg.eraIds.values.map { lg.player(it) }
        println("1985-86 college: ${real.size} real players; " + real.sortedByDescending { it.ovr }.take(6).joinToString { "${it.name} (${lg.team(it.teamId).school}, ${it.cls} ${it.ovr})" })
        assertTrue(real.size >= 120)
        val daugherty = lg.players.first { it.name == "Brad Daugherty" }
        assertEquals("North Carolina", lg.team(daugherty.teamId).school)
        val robinson = lg.players.first { it.name == "David Robinson" }
        assertEquals("Navy", lg.team(robinson.teamId).school)

        // Four seasons in school, then the pros: the 1989-90 league.
        val hero = Road.createHero(lg, Road.Spec("Malik", "Grant", Pos.SF, 79, 215, "Chicago", "IL", Arch.SLASHER, 5, 1, era = Era.Y1986))
        lg = Road.start(lg, hero, Road.offers(lg, hero).minBy { it.ahead }.teamId)
        assertEquals("Y1986", lg.era)
        var seasons = 0
        while (!lg.road!!.goingPro && !lg.road!!.ended && seasons++ < 5) {
            if (Road.needsDeal(lg)) Road.chooseDeal(lg, NilDeal.STEADY)
            var guard = 0
            while (lg.phase != Phase.WRAP_UP && guard++ < 120) { Season.playDay(lg); lg.road!!.press?.let { Road.answer(lg, 0) } }
            Offseason.begin(lg)
            val me = lg.playerOrNull(lg.heroId)!!
            lg.road!!.declared = me.year < 4 && (Offseason.draftBoard(lg)[me.id] ?: 99) <= 15
            Road.next(lg)
            if (lg.road!!.goingPro || lg.road!!.ended) break
            Road.next(lg)
            Summer.invites(lg).firstOrNull()?.let { Summer.attend(lg, it) }
            Road.next(lg)
            if (lg.season == 1987) assertTrue("new real freshmen arrive", lg.eraIds.size > real.size)
        }
        val classmates = lg.draftHistory.filter { it.season == lg.season }.map { it.playerName }
        println("drafted ${lg.season}: " + classmates.take(10).joinToString())
        val realClass = (1986..lg.season + 3).flatMap { y -> com.nh.hoopscoach.game.EraData.draft(y).map { it.name } }.toSet()
        assertTrue("real players in his draft class", classmates.count { it in realClass } >= 10)
        assertTrue("Draft Night names that year's teams", lg.draftHistory.filter { it.season == lg.season }.any { it.proTeam.contains("SuperSonics") || it.proTeam.contains("Bullets") || it.proTeam.contains("Nets") })

        val pro = ProLeague.create(lg, Era.Y1986)
        while (pro.phase == Phase.OFFSEASON) ProCareer.next(pro)
        println("pro league ${pro.season}: ${pro.teams.size} teams; Pippen ${pro.players.firstOrNull { it.name == "Scottie Pippen" }?.let { "${pro.team(it.teamId).name} ${it.ovr}/${it.pot} age ${it.age}" }}; " +
            "Bird ${pro.players.firstOrNull { it.name == "Larry Bird" }?.let { "${it.ovr} age ${it.age}" }}")
        assertEquals(lg.season + 1, pro.season)
        assertEquals("the league that season", com.nh.hoopscoach.game.EraData.teams(pro.season)!!.size, pro.teams.size)
        assertTrue("real draft classes from his college years", pro.players.any { it.name == "David Robinson" } && pro.players.any { it.name == "Scottie Pippen" })
        val realNames = (1986..lg.season).flatMap { y -> com.nh.hoopscoach.game.EraData.draft(y).map { it.name } }.toSet()
        assertTrue("no real player twice", pro.players.filter { it.name in realNames }.groupBy { it.name }.values.all { it.size == 1 })
        assertTrue("he's in", pro.playerOrNull(pro.heroId)?.teamId?.let { it >= 0 } == true)
        val games = pro.teams.map { t -> pro.games.count { it.involves(t.id) } }.toSet()
        println("games: $games")
        assertTrue(games.all { it == ProLeague.seasonGames(pro) })
    }
}
