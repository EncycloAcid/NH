package com.nh.hoopscoach

import com.nh.hoopscoach.game.Game
import com.nh.hoopscoach.game.GameType
import com.nh.hoopscoach.game.LeagueGen
import com.nh.hoopscoach.game.News
import com.nh.hoopscoach.game.Ollama
import com.nh.hoopscoach.game.OllamaConfig
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
}
