package com.nh.hoopscoach

import com.nh.hoopscoach.game.LeagueGen
import com.nh.hoopscoach.game.News
import com.nh.hoopscoach.game.Ollama
import com.nh.hoopscoach.game.OllamaConfig
import com.nh.hoopscoach.game.SaveIO
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

        val encoded = SaveIO.encode(lg)
        val decoded = SaveIO.decode(encoded)

        assertTrue(decoded.ollama.enabled)
        assertEquals("http://localhost:11434", decoded.ollama.host)
        assertEquals("mistral", decoded.ollama.model)
        assertTrue(decoded.ollama.autoGenerate)
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
}
