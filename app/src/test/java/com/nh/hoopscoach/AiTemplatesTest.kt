package com.nh.hoopscoach

import com.nh.hoopscoach.game.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class AiTemplatesTest {
    @Test fun preseasonCopyUsesProductionClassForecastNewcomerAndSchedule() {
        Rng.r = Random(41)
        val lg = Offseason.newGame(5, "Test", "Coach")
        val star = lg.rosterOf(lg.user).maxBy { it.ovr }
        star.year = 4
        star.career.removeAll { it.season == lg.season - 1 }
        star.career += SeasonLine(lg.season - 1, lg.user.school, lg.conf(lg.user.confId).name, 3, star.ovr,
            StatLine(gp = 30, pts = 600, orb = 60, drb = 90, ast = 120), teamId = lg.userTeamId)
        lg.rosterOf(lg.user).first { it.id != star.id }.year = 1
        val request = "Team: ${lg.user.school}\nPlayer: ${star.name}"
        val draft = AiTemplates.create(AiFacts.build(lg, request, preview = true), request)
        assertFalse(draft.isAi)
        assertTrue(draft.text.contains(star.name))
        assertTrue(draft.text.contains("senior"))
        assertTrue(draft.text.contains("20.0 points, 5.0 rebounds and 4.0 assists"))
        assertTrue(draft.text.contains("win range"))
        assertTrue(draft.text.contains("NCAA bid"))
        assertTrue(draft.text.contains(AiTemplates.newcomer(lg, lg.user)!!.name))
        assertTrue(draft.text.contains("On day"))
        assertFalse(Regex("simulat|assum|roster", RegexOption.IGNORE_CASE).containsMatchIn(draft.text))
    }

    @Test fun emptyModelResponseFallsBackToFactsWithoutInventingStatistics() {
        val lg = LeagueGen.newLeague()
        val request = "Team: ${lg.user.school}"
        val facts = AiFacts.build(lg, request)
        assertTrue(AiGrounding.validate("", facts).isNotEmpty())
        val draft = AiTemplates.create(facts, request)
        assertTrue(draft.text.isNotBlank())
        assertFalse(draft.isAi)
        assertFalse(draft.text.contains("averaged"))
    }

    @Test fun applyingGameWrittenCopyRemovesLegacyProviderAndKeepsPublication() {
        val news = News(2026, 1, "Outlook", body = "Old draft", source = "Staff · Courtside Report · Google Gemini", aiLabeled = true)
        AiText("Factual article").applyTo(news)
        assertEquals("Factual article", news.body)
        assertEquals("Game-written · Courtside Report", news.source)
        assertFalse(news.isAiGenerated)
        AiText("Factual model article", true, "Google Gemini").applyTo(news)
        assertTrue(news.isAiGenerated)
        assertEquals("Courtside Report · Google Gemini", news.source)
    }

    @Test(expected = IllegalArgumentException::class)
    fun missingFactsCannotProduceAPretendArticle() {
        AiTemplates.create(AiFactSheet("", emptySet(), emptySet(), emptyMap(), Phase.REGULAR, true), "Write an article")
    }
}
