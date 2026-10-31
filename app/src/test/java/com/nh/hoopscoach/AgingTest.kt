package com.nh.hoopscoach

import com.nh.hoopscoach.game.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Everyone ages: college players every offseason, and the real pros while the user is in college. */
class AgingTest {
    @Test
    fun collegePlayersAge() {
        Rng.r = Random(8)
        val lg = Offseason.claimTeam(LeagueGen.newLeague(), 0, "Test", "Coach")
        assertTrue("college players have ages", lg.players.all { it.age in 17..23 })
        val fr = lg.players.filter { it.year == 1 }.map { it.age }.average()
        val sr = lg.players.filter { it.year == 4 }.map { it.age }.average()
        assertTrue("seniors are older than freshmen ($fr vs $sr)", sr > fr + 2.5)
        val before = lg.players.associate { it.id to it.age }
        var g = 0
        while (!Season.seasonOver(lg) && g++ < 200) Season.playDay(lg)
        Offseason.autoComplete(lg)
        val stayed = lg.players.filter { it.id in before }
        assertTrue("returning players", stayed.size > 1000)
        assertTrue("a year older after the offseason", stayed.all { it.age == before.getValue(it.id) + 1 })
    }

    @Test
    fun theProsAgeWhileYouAreInCollege() {
        val college = ProTest().toDraft(21)
        val gap = college.season + 1 - ProLeague.DATA_SEASON
        val lg = ProLeague.create(college)
        println("went pro after the ${college.season} college season: $gap year(s) since the roster data")
        assertTrue(gap >= 1)
        val real = NbaData.PLAYERS.associateBy { it.name }
        val here = lg.players.filter { it.name in real && it.photo.isNotEmpty() }
        assertTrue("real players are still around", here.size > 200)
        for (p in here) assertEquals("${p.name} is $gap year(s) older", real.getValue(p.name).age + gap, p.age)
        val retired = real.values.filter { it.age >= 39 }.map { it.name }.filter { n -> lg.players.none { it.name == n } }
        println("retired while you were in college: ${retired.take(8)}")
        assertTrue("the oldest real players have moved on", retired.isNotEmpty())
        assertTrue("draftees took their places", lg.teams.all { it.roster.size >= 13 })
        assertTrue("your player has his age from college", lg.hero!!.age in 19..23)
    }
}
