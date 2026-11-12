package com.nh.hoopscoach

import com.nh.hoopscoach.game.AiFacts
import com.nh.hoopscoach.game.AiGrounding
import com.nh.hoopscoach.game.Game
import com.nh.hoopscoach.game.GameType
import com.nh.hoopscoach.game.LeagueGen
import com.nh.hoopscoach.game.Offseason
import com.nh.hoopscoach.game.Phase
import com.nh.hoopscoach.game.PreviewTopics
import com.nh.hoopscoach.game.Rng
import com.nh.hoopscoach.game.SaveIO
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import kotlin.random.Random

class AiGroundingTest {
    private fun league() = LeagueGen.newLeague().also { lg ->
        lg.day = 10
        lg.user.w = 7; lg.user.l = 3
        val p = lg.rosterOf(lg.user).first()
        p.stats.gp = 10; p.stats.pts = 210; p.stats.ast = 40
        p.injury = "sprained ankle"; p.injGames = 3
        lg.games += Game(98765, 9, lg.userTeamId, lg.teams.first { it.id != lg.userTeamId }.id, false, GameType.NONCONF, hs = 84, aws = 79)
    }

    @Test fun factSheetUsesActualDatePhaseRecordsStatsInjuriesAndResults() {
        val lg = league()
        val p = lg.rosterOf(lg.user).first()
        val facts = AiFacts.build(lg, "${lg.user.name} and ${p.name}")
        listOf("SEASON ${lg.season}", "DATE day 10", "PHASE REGULAR", "current record 7-3", "conference standing", "84-79", "${p.name}", "21.0 PPG", "3 team games remaining", "sprained ankle", "AP", "NET").forEach {
            assertTrue("Missing $it", facts.text.contains(it))
        }
        assertFalse(facts.preseason)
        assertTrue(facts.text.length < 16000)
    }

    @Test fun rejectsWrongNamesRecordsScoresNumbersAndPhase() {
        val lg = league()
        val facts = AiFacts.build(lg, lg.user.name)
        listOf("Michael Nobody is the star.", "Atlantis is 7-3.", "${lg.user.school} is 97-2.", "${lg.user.school} won 999-998.", "The player averages 123.4 PPG.", "The season has yet to begin.", "The team won the championship.").forEach {
            assertTrue("Accepted $it", AiGrounding.validate(it, facts).isNotEmpty())
        }
        assertTrue(AiGrounding.validate("${lg.user.school} is 7-3.", facts).isEmpty())
        assertTrue(AiGrounding.validate("", facts).contains("empty response"))
    }

    @Test fun doesNotBorrowARecordOrStatisticFromAnotherEntity() {
        val lg = league()
        val other = lg.teams.first { it.id != lg.userTeamId }
        other.w = 8; other.l = 2
        val a = lg.rosterOf(lg.user).first()
        val b = lg.rosterOf(other).first()
        b.stats.gp = 10; b.stats.pts = 300
        val facts = AiFacts.build(lg, "${lg.user.name}, ${other.name}, ${a.name}, ${b.name}")
        assertTrue(AiGrounding.validate("${lg.user.school} is 8-2.", facts).contains("record belongs to another team"))
        assertTrue(AiGrounding.validate("${a.name} averages 30.0 PPG.", facts).contains("statistic belongs to another player"))
        assertTrue(AiGrounding.validate("${a.name} averages 21.0 PPG.", facts).isEmpty())
    }

    @Test fun retriesOnceThenKeepsTemplateAndDoesNotStreamAnInvalidDraft() = runBlocking {
        val lg = league()
        val facts = AiFacts.build(lg, lg.user.name)
        val prompts = mutableListOf<String>()
        val good = "${lg.user.school} is 7-3."
        val result = AiGrounding.generate(facts, "Write a report") {
            prompts += it
            Result.success(if (prompts.size == 1) "Fake Player scored 999 points." else good)
        }
        assertEquals(good, result.getOrThrow())
        assertEquals(2, prompts.size)
        assertTrue(prompts.all { it.contains(facts.text) && it.contains("Use ONLY the fact sheet") })
        assertTrue(prompts.last().contains("previous draft was rejected"))
        var attempts = 0
        val rejected = AiGrounding.generate(facts, "Write a report") { attempts++; Result.success("Unknown Person won 999-998.") }
        assertEquals(2, attempts)
        assertEquals("Existing template", rejected.getOrElse { "Existing template" })
        assertTrue(rejected.exceptionOrNull()!!.message!!.contains("existing game-written content was kept"))
    }

    @Test fun emptyResponsesAndProviderErrorsHaveBoundedFallbackButCancellationPropagates() = runBlocking {
        val facts = AiFacts.build(league(), "Report")
        for (response in listOf(Result.success(""), Result.failure<String>(IllegalStateException("offline")))) {
            var attempts = 0
            assertTrue(AiGrounding.generate(facts, "Report") { attempts++; response }.isFailure)
            assertEquals(2, attempts)
        }
        try {
            AiGrounding.generate(facts, "Report") { throw CancellationException("cancel") }
            fail("Cancellation swallowed")
        } catch (_: CancellationException) { }
    }

    @Test fun topicsReflectSimulationOddsAndRealInjuryRatherThanRankBands() {
        Rng.r = Random(41)
        val lg = Offseason.newGame(5, "Test", "Coach")
        val p = lg.rosterOf(lg.user).maxBy { it.ovr }
        p.injury = "sprained ankle"; p.injGames = 5
        val topics = PreviewTopics.select(lg)
        val favorite = lg.preview!!.odds.maxBy { it.value.title }.key
        assertEquals(favorite, topics.first { it.kind == "title" }.teamId)
        val injury = topics.first { it.kind == "injury" }
        assertEquals(p.id, injury.pid)
        assertTrue(injury.facts.contains("5 team games"))
        p.injGames = 0
        assertFalse(PreviewTopics.select(lg).any { it.kind == "injury" && it.pid == p.id })
        val raw = PreviewTopics.stories(lg, topics)
        assertTrue(raw.all { it.teamId >= 0 && it.body.isNotBlank() && !it.isAiGenerated })
    }

    @Test fun cachedDraftsAreIsolatedByDateRosterAndResults() {
        val lg = league()
        val original = AiFacts.version(lg)
        lg.day++
        assertNotEquals(original, AiFacts.version(lg))
        val next = AiFacts.version(lg)
        lg.rosterOf(lg.user).first().stats.pts++
        assertNotEquals(next, AiFacts.version(lg))
    }

    @Test fun commentaryAboutMethodsIsRejected() {
        val lg = league()
        val facts = AiFacts.build(lg, lg.user.name)
        listOf("The simulations favor ${lg.user.school}.", "No results have been assumed.", "The roster includes the team's players.").forEach {
            assertTrue(AiGrounding.validate(it, facts).contains("meta-commentary instead of sports copy"))
        }
    }

    @Test fun blankRequiredFieldsCannotBorrowTheNextField() = runBlocking {
        val lg = league()
        val facts = AiFacts.build(lg, lg.user.name)
        val request = "Format your response EXACTLY as:\nHEADLINE: <Headline>\nOUTLET: <Outlet>\nBODY:\n<Text>"
        var attempts = 0
        val result = AiGrounding.generate(facts, request) {
            attempts++
            Result.success("HEADLINE:\nOUTLET: Courtside Report\nBODY: ${lg.user.school} is ${lg.user.record}.")
        }
        assertTrue(result.isFailure)
        assertEquals(2, attempts)
    }

    @Test fun malformedKeyCannotBePublishedAsAnAiCard() = runBlocking {
        val lg = league()
        val facts = AiFacts.build(lg, lg.user.name)
        var attempts = 0
        val result = AiGrounding.generate(facts, "Format your response EXACTLY as:\nKEY 1: <Title> | <Detail>") {
            attempts++
            Result.success("KEY 1: ${lg.user.school} is ${lg.user.record}.")
        }
        assertTrue(result.isFailure)
        assertEquals(2, attempts)
    }

    @Test fun oldSaveWithoutNewPreviewFieldsLoadsAndKeepsAvatarChoices() {
        Rng.r = Random(41)
        val lg = Offseason.newGame(5, "Test", "Coach")
        val looks = lg.players.map { it.look }
        val json = Json { ignoreUnknownKeys = true }
        fun strip(e: JsonElement): JsonElement = when (e) {
            is JsonObject -> JsonObject(e.filterKeys { it !in setOf("reports", "regularActual", "letter", "median", "aiProvider") }.mapValues { strip(it.value) })
            is JsonArray -> JsonArray(e.map { strip(it) })
            else -> e
        }
        val text = strip(json.parseToJsonElement(json.encodeToString(com.nh.hoopscoach.game.League.serializer(), lg))).toString()
        val bytes = ByteArrayOutputStream().also { b -> GZIPOutputStream(b).use { it.write(text.toByteArray()) } }.toByteArray()
        val loaded = SaveIO.decode(bytes)
        assertEquals(looks, loaded.players.map { it.look })
        assertTrue(loaded.preview!!.reports.isEmpty())
        assertTrue(loaded.preview!!.odds.values.all { it.median == null })
        assertTrue(loaded.preview!!.stories.none { it.isAiGenerated })
        val facts = AiFacts.build(lg, lg.user.name, preview = true)
        assertTrue(AiGrounding.validate("${lg.user.school} clinched the title tonight.", facts).isNotEmpty())
    }
}
