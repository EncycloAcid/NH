package com.nh.hoopscoach

import com.nh.hoopscoach.game.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class SituationAwarenessTest {
    @Test
    fun leagueBriefCoversTitlesAndLeague() {
        Rng.r = Random(11)
        val lg = LeagueGen.newLeague()
        val brief = SituationAwareness.leagueBrief(lg)
        assertTrue(brief.contains("LEAGUE SITUATION"))
        assertTrue(brief.contains("TITLE") || brief.contains("CHAMPION") || brief.contains("titles"))
        assertTrue(brief.contains(lg.user.school) || brief.contains("YOUR"))
        val named = SituationAwareness.namedTeams(lg)
        assertTrue(named.any { it.id == lg.userTeamId })
        val facts = AiFacts.build(lg, lg.user.school)
        assertTrue(facts.text.contains("LEAGUE SITUATION") || facts.text.contains("TITLE"))
        assertTrue(facts.text.contains("YOUR TITLE HISTORY") || facts.text.contains("DEFENDING") || facts.text.contains("NATIONAL TITLES"))
        assertTrue("fact sheet must stay inside local context", facts.text.length < 8192)
    }
}
