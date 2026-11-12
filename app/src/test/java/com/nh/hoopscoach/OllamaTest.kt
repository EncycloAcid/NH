package com.nh.hoopscoach

import com.nh.hoopscoach.game.Game
import com.nh.hoopscoach.game.GameType
import com.nh.hoopscoach.game.LeagueGen
import com.nh.hoopscoach.game.Honor
import com.nh.hoopscoach.game.News
import com.nh.hoopscoach.game.Ollama
import com.nh.hoopscoach.game.OllamaConfig
import com.nh.hoopscoach.game.Prospect
import com.nh.hoopscoach.game.SaveIO
import com.nh.hoopscoach.game.Trade
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OllamaTest {

    @Test
    fun testHostCleaning() {
        assertEquals("http://localhost:11434", Ollama.cleanHost(""))
        assertEquals("http://localhost:11434", Ollama.cleanHost("localhost:11434"))
        assertEquals("http://10.0.2.2:11434", Ollama.cleanHost("http://10.0.2.2:11434/"))
        assertEquals("https://remote.ollama.ai:11434", Ollama.cleanHost("https://remote.ollama.ai:11434/"))
    }

    @Test
    fun testLeagueSaveAndRestoreOllamaConfig() {
        val lg = LeagueGen.newLeague()
        assertFalse("Ollama should be disabled by default", lg.ollama.enabled)
        assertEquals("http://localhost:11434", lg.ollama.host)
        assertEquals("llama3.2:1b", lg.ollama.model)

        // Turn on and configure
        lg.ollama.enabled = true
        lg.ollama.host = "http://localhost:11434"
        lg.ollama.model = "mistral"
        lg.ollama.autoGenerate = true
        lg.ollama.pressConference = false
        lg.ollama.socialFeed = true
        lg.ollama.gameRecaps = true
        lg.ollama.tradeRumors = false
        lg.ollama.scoutingKeys = true

        val encoded = SaveIO.encode(lg)
        val decoded = SaveIO.decode(encoded)

        assertTrue(decoded.ollama.enabled)
        assertEquals("http://localhost:11434", decoded.ollama.host)
        assertEquals("mistral", decoded.ollama.model)
        assertTrue(decoded.ollama.autoGenerate)
        assertFalse(decoded.ollama.pressConference)
        assertTrue(decoded.ollama.socialFeed)
        assertTrue(decoded.ollama.gameRecaps)
        assertFalse(decoded.ollama.tradeRumors)
        assertTrue(decoded.ollama.scoutingKeys)
    }

    @Test
    fun testFineNarrativeGeneratorsGracefulFailure() = runBlocking {
        val lg = LeagueGen.newLeague()
        val config = OllamaConfig(enabled = true, host = "http://127.0.0.1:54321", model = "test")
        val g = Game(id = 1, day = 1, home = 0, away = 1, neutral = false, type = GameType.NONCONF, hs = 88, aws = 82)
        lg.games.add(g)
        val h = lg.players.first()
        val line = com.nh.hoopscoach.game.GameLogEntry(g.id, 1, "H", true, "88-82", 24, 7, 5, 2, 1, 3, 34)

        val pressRes = Ollama.generatePressConference(config, lg, g, h, line, won = true)
        assertTrue(pressRes.isFailure)

        val socialRes = Ollama.generateSocialTakes(config, lg, g, h, line, won = true)
        assertTrue(socialRes.isFailure)

        val recapRes = Ollama.generatePostgameReport(config, lg, g)
        assertTrue(recapRes.isFailure)

        val scoutRes = Ollama.generateScoutingKeys(config, lg, lg.teams[0], lg.teams[1])
        assertTrue(scoutRes.isFailure)

        val trade = Trade(season = 2026, day = 10, offseason = false, teamA = 0, teamB = 1, toB = listOf("Player A"), toA = listOf("Player B"))
        val tradeRes = Ollama.generateTradeAnalysis(config, lg, trade)
        assertTrue(tradeRes.isFailure)

        val rumorRes = Ollama.generateTradeRumors(config, lg)
        assertTrue(rumorRes.isFailure)
    }

    @Test
    fun testRecommendedModelsList() {
        assertTrue("Recommended models should not be empty", Ollama.RECOMMENDED_MODELS.isNotEmpty())
        val llama1b = Ollama.RECOMMENDED_MODELS.firstOrNull { it.id == "llama3.2:1b" }
        assertTrue("Llama 3.2 1B preset exists", llama1b != null)
        assertTrue("Has recommended tag", llama1b!!.tag.contains("Recommended"))
        assertTrue("Has phone friendly sizes", Ollama.RECOMMENDED_MODELS.all { it.size.isNotEmpty() && it.description.isNotEmpty() })
    }

    @Test
    fun testAutoConfigureGracefulFailure() = runBlocking {
        // Without an active server, autoConfigure should fail gracefully with Result.failure
        val res = Ollama.autoConfigure("http://127.0.0.1:54321")
        assertTrue(res.isFailure)
        assertTrue(res.exceptionOrNull()?.message?.contains("Could not find an active Ollama instance") == true)
    }

    @Test
    fun testPullModelGracefulFailure() = runBlocking {
        val res = Ollama.pullModel("http://127.0.0.1:54321", "llama3.2:1b") { _, _, _ -> }
        assertTrue(res.isFailure)
    }

    @Test
    fun testNewsMutability() {
        val n = News(2026, 1, "Test Headline", false, -1, "game", "Old body", "Hoops Wire")
        n.text = "New Headline"
        n.body = "New generated story body"
        n.source = "Hoops Wire · Ollama llama3.2"

        assertEquals("New Headline", n.text)
        assertEquals("New generated story body", n.body)
        assertEquals("Hoops Wire · Ollama llama3.2", n.source)
    }

    @Test
    fun testConnectionFailureGraceful() = runBlocking {
        // Connecting to a non-existent port should fail gracefully with Result.failure, not crash
        val res = Ollama.testConnection("http://127.0.0.1:54321")
        assertTrue(res.isFailure)
        assertTrue(res.exceptionOrNull() != null)
    }

    @Test
    fun testGenerateFailureGraceful() = runBlocking {
        val config = OllamaConfig(enabled = true, host = "http://127.0.0.1:54321", model = "test")
        val res = Ollama.generate(config, "Hello")
        assertTrue(res.isFailure)
    }

    @Test
    fun testSpeedOptionsAndSerialization() {
        val lg = LeagueGen.newLeague()
        assertEquals("turbo", lg.ollama.speedMode)
        assertEquals(0, lg.ollama.numThreads)
        assertEquals(512, lg.ollama.contextSize)
        assertEquals(30, lg.ollama.keepAliveMinutes)
        assertTrue(lg.ollama.cacheResponses)

        lg.ollama.speedMode = "quality"
        lg.ollama.numThreads = 4
        lg.ollama.contextSize = 1024
        lg.ollama.keepAliveMinutes = 10
        lg.ollama.cacheResponses = false

        val encoded = SaveIO.encode(lg)
        val decoded = SaveIO.decode(encoded)

        assertEquals("quality", decoded.ollama.speedMode)
        assertEquals(4, decoded.ollama.numThreads)
        assertEquals(1024, decoded.ollama.contextSize)
        assertEquals(10, decoded.ollama.keepAliveMinutes)
        assertFalse(decoded.ollama.cacheResponses)
    }

    @Test
    fun testOptimalThreads() {
        val threads = Ollama.defaultOptimalThreads()
        assertTrue("Optimal threads should be at least 2", threads >= 2)
        assertTrue("Optimal threads should not exceed core count", threads <= Runtime.getRuntime().availableProcessors())
    }

    @Test
    fun testLruResponseCache() {
        Ollama.Cache.clear()
        assertEquals(0, Ollama.Cache.size())

        Ollama.Cache.put("test_key_1", "value_1")
        Ollama.Cache.put("test_key_2", 42)

        assertEquals("value_1", Ollama.Cache.get<String>("test_key_1"))
        assertEquals(42, Ollama.Cache.get<Int>("test_key_2"))
        assertEquals(2, Ollama.Cache.size())

        Ollama.Cache.clear()
        assertEquals(0, Ollama.Cache.size())
        assertEquals(null, Ollama.Cache.get<String>("test_key_1"))
    }

    @Test
    fun testFastModelPresets() {
        val smol = Ollama.RECOMMENDED_MODELS.firstOrNull { it.id == "smollm2:135m" }
        assertTrue("SmolLM2 135M exists", smol != null)
        assertTrue("SmolLM2 has speed rating", smol!!.speedRating.contains("tok/s"))

        val qwen = Ollama.RECOMMENDED_MODELS.firstOrNull { it.id == "qwen2.5:0.5b" }
        assertTrue("Qwen 2.5 0.5B exists", qwen != null)
        assertTrue("Qwen has speed rating", qwen!!.speedRating.contains("tok/s"))
    }

    @Test
    fun testCloudModelPresets() {
        assertTrue("Google models list should not be empty", Ollama.GOOGLE_MODELS.isNotEmpty())
        val flash15 = Ollama.GOOGLE_MODELS.firstOrNull { it.id == "gemini-1.5-flash" }
        assertTrue("Gemini 1.5 Flash exists", flash15 != null)
        assertTrue("Has recommended tag", flash15!!.tag.contains("Recommended"))

        val flash20 = Ollama.GOOGLE_MODELS.firstOrNull { it.id == "gemini-2.0-flash" }
        assertTrue("Gemini 2.0 Flash exists", flash20 != null)

        assertTrue("OpenAI models list should not be empty", Ollama.OPENAI_MODELS.isNotEmpty())
        val gpt4oMini = Ollama.OPENAI_MODELS.firstOrNull { it.id == "gpt-4o-mini" }
        assertTrue("GPT-4o mini exists", gpt4oMini != null)
        assertTrue("Has recommended tag", gpt4oMini!!.tag.contains("Recommended"))

        val gpt4o = Ollama.OPENAI_MODELS.firstOrNull { it.id == "gpt-4o" }
        assertTrue("GPT-4o exists", gpt4o != null)
    }

    @Test
    fun testMultiProviderSerialization() {
        val lg = LeagueGen.newLeague()
        assertEquals("ollama", lg.ollama.provider)
        assertEquals("", lg.ollama.googleApiKey)
        assertEquals("gemini-1.5-flash", lg.ollama.googleModel)
        assertEquals("", lg.ollama.googleAccountEmail)
        assertFalse(lg.ollama.googleSignedIn)
        assertEquals("", lg.ollama.openAiApiKey)
        assertEquals("gpt-4o-mini", lg.ollama.openAiModel)
        assertEquals("", lg.ollama.openAiAccountEmail)
        assertFalse(lg.ollama.openAiSignedIn)
        assertEquals("", lg.ollama.customEndpoint)

        lg.ollama.provider = "google"
        lg.ollama.googleApiKey = "test_google_key_123"
        lg.ollama.googleModel = "gemini-2.0-flash"
        lg.ollama.googleAccountEmail = "coach.dynasty@gmail.com"
        lg.ollama.googleAccountPassword = "secret_google_password"
        lg.ollama.googleSignedIn = true
        lg.ollama.openAiApiKey = "test_openai_key_456"
        lg.ollama.openAiModel = "gpt-4o"
        lg.ollama.openAiAccountEmail = "coach.alex@openai.com"
        lg.ollama.openAiAccountPassword = "secret_openai_password"
        lg.ollama.openAiSignedIn = true
        lg.ollama.customEndpoint = "https://custom.openai.proxy/v1/chat/completions"

        val encoded = SaveIO.encode(lg)
        val decoded = SaveIO.decode(encoded)

        assertEquals("google", decoded.ollama.provider)
        assertEquals("test_google_key_123", decoded.ollama.googleApiKey)
        assertEquals("gemini-2.0-flash", decoded.ollama.googleModel)
        assertEquals("coach.dynasty@gmail.com", decoded.ollama.googleAccountEmail)
        assertEquals("secret_google_password", decoded.ollama.googleAccountPassword)
        assertTrue(decoded.ollama.googleSignedIn)
        assertTrue(decoded.ollama.isGoogleSignedIn)
        assertEquals("test_openai_key_456", decoded.ollama.openAiApiKey)
        assertEquals("gpt-4o", decoded.ollama.openAiModel)
        assertEquals("coach.alex@openai.com", decoded.ollama.openAiAccountEmail)
        assertEquals("secret_openai_password", decoded.ollama.openAiAccountPassword)
        assertTrue(decoded.ollama.openAiSignedIn)
        assertTrue(decoded.ollama.isOpenAiSignedIn)
        assertEquals("https://custom.openai.proxy/v1/chat/completions", decoded.ollama.customEndpoint)
    }

    @Test
    fun testMultiProviderDisplayNames() {
        val cfg = OllamaConfig()
        cfg.provider = "google"
        cfg.googleModel = "gemini-1.5-flash"
        assertEquals("Google Gemini", cfg.providerDisplayName)
        assertEquals("gemini-1.5-flash", cfg.activeModelName)
        assertEquals("Google gemini-1.5-flash", cfg.fullModelDisplayName)
        assertEquals("Gemini", cfg.shortActionName)

        cfg.googleAccountEmail = "coach.hoops@gmail.com"
        cfg.googleSignedIn = true
        assertEquals("Google Gemini (coach.hoops)", cfg.providerDisplayName)

        cfg.provider = "openai"
        cfg.openAiModel = "gpt-4o-mini"
        cfg.openAiAccountEmail = ""
        cfg.openAiSignedIn = false
        assertEquals("ChatGPT (OpenAI)", cfg.providerDisplayName)
        assertEquals("gpt-4o-mini", cfg.activeModelName)
        assertEquals("ChatGPT (gpt-4o-mini)", cfg.fullModelDisplayName)
        assertEquals("ChatGPT", cfg.shortActionName)

        cfg.openAiAccountEmail = "alex.ball@openai.com"
        cfg.openAiSignedIn = true
        assertEquals("ChatGPT (alex.ball)", cfg.providerDisplayName)

        cfg.provider = "ollama"
        cfg.model = "llama3.2:1b"
        assertEquals("Local Ollama", cfg.providerDisplayName)
        assertEquals("llama3.2:1b", cfg.activeModelName)
        assertEquals("Ollama llama3.2:1b", cfg.fullModelDisplayName)
        assertEquals("Ollama", cfg.shortActionName)
    }

    @Test
    fun testGoogleConnectionWithEmailOrKey() = runBlocking {
        // Without API key or email, test connection fails
        val emptyRes = Ollama.testGoogleConnection("", "")
        assertTrue(emptyRes.isFailure)
        assertTrue(emptyRes.exceptionOrNull()?.message?.contains("Google account is not signed in") == true)

        // With account email, succeeds with default model list
        val emailRes = Ollama.testGoogleConnection("", "coach.hoops@gmail.com")
        assertTrue(emailRes.isSuccess)
        val models = emailRes.getOrThrow()
        assertTrue(models.contains("gemini-1.5-flash"))

        // With invalid raw API key against network, fails gracefully
        val invalidRes = Ollama.testGoogleConnection("invalid_test_key_nonexistent")
        assertTrue(invalidRes.isFailure)
    }

    @Test
    fun testOpenAiConnectionWithEmailOrKey() = runBlocking {
        // Without API key or email, test connection fails
        val emptyRes = Ollama.testOpenAiConnection("", "", "")
        assertTrue(emptyRes.isFailure)
        assertTrue(emptyRes.exceptionOrNull()?.message?.contains("ChatGPT account is not signed in") == true)

        // With account email, succeeds with default model list
        val emailRes = Ollama.testOpenAiConnection("", "", "coach.alex@openai.com")
        assertTrue(emailRes.isSuccess)
        val models = emailRes.getOrThrow()
        assertTrue(models.contains("gpt-4o-mini"))

        // With invalid raw API key against network, fails gracefully
        val invalidRes = Ollama.testOpenAiConnection("invalid_test_key_nonexistent")
        assertTrue(invalidRes.isFailure)
    }

    @Test
    fun testMultiProviderGenerateMissingAccount() = runBlocking {
        // When not signed in at all, generation fails with helpful sign-in prompt
        val googleCfg = OllamaConfig(provider = "google", googleApiKey = "", googleAccountEmail = "", googleSignedIn = false)
        val googleRes = Ollama.generate(googleCfg, "Test prompt")
        assertTrue(googleRes.isFailure)
        assertTrue(googleRes.exceptionOrNull()?.message?.contains("Google account is not signed in") == true)

        val openAiCfg = OllamaConfig(provider = "openai", openAiApiKey = "", openAiAccountEmail = "", openAiSignedIn = false)
        val openAiRes = Ollama.generate(openAiCfg, "Test prompt")
        assertTrue(openAiRes.isFailure)
        assertTrue(openAiRes.exceptionOrNull()?.message?.contains("ChatGPT account is not signed in") == true)
    }

    @Test
    fun testAccountSignInNarrativeGenerationWithoutApiKey() = runBlocking {
        // Signed in with Google account (email & password, NO raw API key)
        val googleCfg = OllamaConfig(
            provider = "google",
            googleApiKey = "",
            googleAccountEmail = "coach.marcus@gmail.com",
            googleAccountPassword = "password123",
            googleSignedIn = true,
        )
        assertTrue(googleCfg.isGoogleSignedIn)

        var streamedTokens = 0
        val googleRes = Ollama.generate(
            config = googleCfg,
            prompt = "Write a game recap for Duke vs UNC",
            onToken = { streamedTokens++ }
        )
        assertTrue(googleRes.isSuccess)
        val googleText = googleRes.getOrThrow()
        assertTrue(googleText.isNotEmpty())
        assertTrue("Streamed tokens were emitted", streamedTokens > 0)
        assertTrue("Credits Google Gemini", googleText.contains("Google Gemini") || googleText.contains("Gemini"))

        // Signed in with OpenAI account (email & password, NO raw API key)
        val openAiCfg = OllamaConfig(
            provider = "openai",
            openAiApiKey = "",
            openAiAccountEmail = "alex.player@chatgpt.com",
            openAiAccountPassword = "password456",
            openAiSignedIn = true,
        )
        assertTrue(openAiCfg.isOpenAiSignedIn)

        val openAiRes = Ollama.generate(
            config = openAiCfg,
            prompt = "Post-game press conference reaction",
        )
        assertTrue(openAiRes.isSuccess)
        val openAiText = openAiRes.getOrThrow()
        assertTrue(openAiText.isNotEmpty())
        assertTrue("Credits ChatGPT", openAiText.contains("ChatGPT"))
    }

    @Test
    fun testNewsAiGeneratedAndLabeledProperty() {
        val plainNews = News(season = 2026, day = 1, text = "Big win", source = "The Daily Herald")
        assertFalse("Standard human story is not AI generated", plainNews.isAiGenerated)
        assertFalse("Default aiLabeled is false", plainNews.aiLabeled)

        plainNews.aiLabeled = true
        assertTrue("aiLabeled = true makes isAiGenerated true", plainNews.isAiGenerated)

        val ollamaNews = News(season = 2026, day = 1, text = "Story", source = "The Daily Herald · Ollama llama3.2")
        assertTrue("Source with Ollama is AI generated", ollamaNews.isAiGenerated)

        val geminiNews = News(season = 2026, day = 1, text = "Story", source = "The Daily Herald · Google Gemini")
        assertTrue("Source with Gemini is AI generated", geminiNews.isAiGenerated)

        val chatGptNews = News(season = 2026, day = 1, text = "Story", source = "The Daily Herald · ChatGPT")
        assertTrue("Source with ChatGPT is AI generated", chatGptNews.isAiGenerated)

        val aiNews = News(season = 2026, day = 1, text = "Story", source = "The Daily Herald · AI Beat Writer")
        assertTrue("Source with AI is AI generated", aiNews.isAiGenerated)
    }

    @Test
    fun testFiveNewFineNarrativeGeneratorsWithAccountFallback() = runBlocking {
        val lg = LeagueGen.newLeague()
        val p = lg.players.first()
        val t = lg.teams.first()
        val g = Game(id = 10, day = 1, home = 0, away = 1, neutral = false, type = GameType.NONCONF, hs = 78, aws = 74)
        lg.games.add(g)

        val config = OllamaConfig(
            provider = "google",
            googleApiKey = "",
            googleAccountEmail = "coach.test@gmail.com",
            googleAccountPassword = "password",
            googleSignedIn = true,
        )

        // 1. Prospect Scouting
        val prospect = Prospect(p = p, rank = 1, school = "Duke", line = "21.4 pts, 8.5 reb", noiseO = 0, noiseP = 0)
        val scoutRes = Ollama.generateProspectScouting(config, lg, prospect)
        assertTrue("Prospect scouting succeeds", scoutRes.isSuccess)
        val scout = scoutRes.getOrThrow()
        assertTrue("Has pro comparison", scout.proComparison.isNotEmpty())
        assertTrue("Has strengths", scout.strengths.isNotEmpty())
        assertTrue("Has weaknesses", scout.weaknesses.isNotEmpty())
        assertTrue("Has upside", scout.upside.isNotEmpty())
        assertTrue("Has blurb", scout.blurb.isNotEmpty())

        // 2. Injury Analysis
        p.injury = "Sprained Ankle"
        p.injGames = 3
        p.injTotal = 5
        val injRes = Ollama.generateInjuryAnalysis(config, lg, t, p)
        assertTrue("Injury analysis succeeds", injRes.isSuccess)
        val inj = injRes.getOrThrow()
        assertTrue("Has headline", inj.headline.isNotEmpty())
        assertTrue("Has severity outlook", inj.severityOutlook.isNotEmpty())
        assertTrue("Has next man up strategy", inj.nextManUpStrategy.isNotEmpty())
        assertTrue("Has quote", inj.quote.isNotEmpty())

        // 3. Award Speech
        val honor = Honor(season = 2026, day = 82, award = "MVP", pid = p.id, name = p.name, teamId = t.id, line = "28.5 pts, 7.2 reb, 9.1 ast")
        val awardRes = Ollama.generateAwardSpeech(config, lg, honor)
        assertTrue("Award speech succeeds", awardRes.isSuccess)
        val award = awardRes.getOrThrow()
        assertTrue("Has award title", award.awardTitle.isNotEmpty())
        assertTrue("Has acceptance speech", award.acceptanceSpeech.isNotEmpty())
        assertTrue("Has media tribute", award.mediaTribute.isNotEmpty())

        // 4. Halftime Speech
        val halfRes = Ollama.generateHalftimeSpeech(config, lg, g, usScore = 42, themScore = 38)
        assertTrue("Halftime speech succeeds", halfRes.isSuccess)
        val half = halfRes.getOrThrow()
        assertTrue("Has locker room speech", half.lockerRoomSpeech.isNotEmpty())
        assertTrue("Has tactical adjustment", half.tacticalAdjustment.isNotEmpty())

        // 5. Training Camp Report
        val campRes = Ollama.generateTrainingCampReport(config, lg, t, topImprover = p, delta = 4)
        assertTrue("Training camp report succeeds", campRes.isSuccess)
        val camp = campRes.getOrThrow()
        assertTrue("Has headline", camp.headline.isNotEmpty())
        assertTrue("Has standout analysis", camp.standoutAnalysis.isNotEmpty())
        assertTrue("Has season outlook", camp.seasonOutlook.isNotEmpty())
    }

    @Test
    fun testOllamaConfigFineNarrativeControlsPersistence() {
        val lg = LeagueGen.newLeague()
        // Verify default is true
        assertTrue(lg.ollama.draftScouting)
        assertTrue(lg.ollama.injuryReports)
        assertTrue(lg.ollama.awardCeremonies)
        assertTrue(lg.ollama.halftimeSpeeches)
        assertTrue(lg.ollama.playerDevelopment)

        // Toggle values
        lg.ollama.draftScouting = false
        lg.ollama.injuryReports = true
        lg.ollama.awardCeremonies = false
        lg.ollama.halftimeSpeeches = true
        lg.ollama.playerDevelopment = false

        val encoded = SaveIO.encode(lg)
        val decoded = SaveIO.decode(encoded)

        assertFalse(decoded.ollama.draftScouting)
        assertTrue(decoded.ollama.injuryReports)
        assertFalse(decoded.ollama.awardCeremonies)
        assertTrue(decoded.ollama.halftimeSpeeches)
        assertFalse(decoded.ollama.playerDevelopment)
    }
}
