package com.nh.hoopscoach

import com.nh.hoopscoach.game.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class AlternateUniverseTest {
    @Test fun twoCollegeSavesDivergeOnPreLeagueChampions() {
        Rng.r = Random(11)
        val a = LeagueGen.newLeague(2026)
        Rng.r = Random(99)
        val b = LeagueGen.newLeague(2026)
        assertTrue(a.altCollegeTitles.isNotEmpty())
        assertTrue(b.altCollegeTitles.isNotEmpty())
        assertNotEquals(a.altCollegeTitles, b.altCollegeTitles)
        assertNotEquals(a.universeSeed, b.universeSeed)
        val aDuke = CollegeHistory.titles(a, a.teams.first { it.school == "Duke" })
        val bDuke = CollegeHistory.titles(b, b.teams.first { it.school == "Duke" })
        assertTrue(a.altCollegeTitles.values.toSet().size >= 8)
        assertTrue(a.teams.all { it.philosophy.isNotBlank() })
        assertEquals(a.altCollegeTitles.count { it.value == "Duke" }, aDuke.size)
        assertEquals(b.altCollegeTitles.count { it.value == "Duke" }, bDuke.size)
    }

    @Test fun nbaSeedRedrawsFranchiseBanners() {
        Rng.r = Random(42)
        val lg = ProLeague.coachLeague(2000, "SEA", "Test", "Coach")
        assertTrue(lg.altNbaTitles.isNotEmpty())
        assertTrue(lg.teams.all { it.philosophy.isNotBlank() })
        val leaders = lg.teams.map { it to NbaHistory.titles(lg, it).size }.sortedByDescending { it.second }
        assertTrue(leaders.first().second >= 3)
        Rng.r = Random(7)
        val other = ProLeague.coachLeague(2000, "BOS", "Other", "Coach")
        assertNotEquals(lg.altNbaTitles, other.altNbaTitles)
    }

    @Test fun playerCoachStampsPhilosophyAndPlayedAt() {
        Rng.r = Random(3)
        val college = LeagueGen.newLeague(2026)
        val school = college.teams.first().school
        Offseason.claimTeam(college, college.teams.first().id, "Hero", "Player")
        val past = PlayedAs(
            name = "Hero Player", pos = "PG", college = school, collegeYears = "2022–2025",
            collegeLine = "4 seasons · 18.0 pts", collegeHonors = listOf("All-American"),
            draft = "No. 5 pick, 2025", proTeams = listOf("Boston Celtics"), proSlots = listOf("BOS"),
            proYears = "2026–2032", proLine = "7 seasons · 22.0 pts", proSeasons = 7,
            honors = listOf("2× All-Star"), rings = 1, allStars = 2, mvps = 0,
            verdict = "Ring winner", hofClass = 0, allTime = 50, lastSeason = 2032,
        )
        college.playedAs = past
        GenerationalLegacy.stampFromPlayer(college, school, past)
        val t = college.teams.first { it.school == school }
        assertTrue(t.philosophy.isNotBlank())
        assertEquals("Hero Player", t.legacyCoach)
        assertTrue(college.userCoach.playedAt.contains(school))
        assertTrue(college.userCoach.playedAt.contains("Boston Celtics"))
    }

    @Test fun shadowProAdvancesAndFeedsRecruitHeat() {
        Rng.r = Random(5)
        val lg = LeagueGen.newLeague(2026)
        val t = lg.teams.first()
        Offseason.claimTeam(lg, t.id, "A", "B")
        val picks = (1..8).map { i ->
            DraftPick(lg.season, i, "Prospect $i", Pos.SG, lg.userTeamId, lg.user.school, 4, 88 - i, nba = "Boston Celtics")
        }
        ShadowProWorld.afterDraft(lg, picks)
        assertEquals(8, lg.shadowPros.size)
        repeat(6) { ShadowProWorld.advance(lg) }
        assertTrue(lg.shadowPros.any { it.seasons >= 6 || it.retired })
        assertTrue(ShadowProWorld.heat(lg, lg.userTeamId) > 0)
        val prospect = lg.rosterOf(lg.teams.first { it.id != lg.userTeamId }).first()
        val r = Recruit(prospect, pref = RecruitPref.PROS)
        val factor = GenerationalLegacy.recruitFactor(lg, lg.user, r)
        assertNotNull(factor)
        assertTrue(factor!!.second >= 1.0)
    }
}
