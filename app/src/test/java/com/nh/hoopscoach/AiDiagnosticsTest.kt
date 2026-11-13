package com.nh.hoopscoach

import com.nh.hoopscoach.game.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AiDiagnosticsTest {
    @Test fun modelDiscoveryUsesAdvertisedGenerationModelsInsteadOfObsoleteDefaults() {
        val models = Ollama.googleTextModels("""{"models":[{"name":"models/gemini-current-flash","supportedGenerationMethods":["generateContent","countTokens"]},{"name":"models/gemini-embedding","supportedGenerationMethods":["embedContent"]}]}""")
        assertEquals(listOf("gemini-current-flash"), models)
    }
    @Test fun snapKeepsProviderPicksAndOnlyBlocksExpensiveDefaults() {
        assertEquals("gemini-3.5-flash", Ollama.snapGoogleModel("gemini-3.5-flash"))
        assertEquals("gemini-3.5-flash-lite", Ollama.snapGoogleModel("gemini-3.5-flash-lite"))
        assertEquals("gemini-3.8-flash", Ollama.snapGoogleModel("gemini-1.5-pro"))
        assertEquals("gemini-3.8-flash", Ollama.snapGoogleModel("gemini-1.5-flash"))
        assertEquals("gemini-3.8-flash", Ollama.snapGoogleModel("gemini-2.0-flash"))
        assertEquals("gemini-3.8-flash", Ollama.snapGoogleModel("gemini-3.1-flash-lite-image"))
        assertEquals("gpt-4o-mini", Ollama.snapOpenAiModel("gpt-4o"))
        assertEquals("gpt-4o-mini", Ollama.snapOpenAiModel("gpt-4o-mini"))
        assertEquals("smollm2:135m", Ollama.snapLocalModel("smollm2:135m"))
        assertEquals("llama3.2:1b", Ollama.snapLocalModel("llama3.2:3b"))
    }
    @Test fun autoPickPrefersFreeCheapModelsFromProviderList() {
        Ollama.clearFailedModels()
        assertEquals(
            "gemini-3.8-flash",
            Ollama.pickBestModel(
                "google",
                listOf("gemini-1.5-pro", "gemini-3.5-flash", "gemini-3.5-flash-lite", "gemini-3.8-flash", "gemini-4-argon", "gemini-3.8-flash-tts"),
            ),
        )
        assertEquals(
            "gemini-3.8-flash",
            Ollama.pickBestModel("google", listOf("gemini-3.8-flash", "gemini-3.5-flash-preview-09-2026", "gemini-2.0-flash")),
        )
        assertEquals("gpt-4o-mini", Ollama.pickBestModel("openai", listOf("gpt-4o", "o1-mini", "gpt-4o-mini")))
        assertEquals("smollm2:135m", Ollama.pickBestModel("ollama", listOf("llama3.2:1b", "smollm2:135m", "qwen2.5:0.5b")))
        assertEquals("gemini-3.8-flash", Ollama.pickBestModel("google"))
        assertEquals("gpt-4o-mini", Ollama.pickBestModel("openai"))
        assertEquals("smollm2:135m", Ollama.pickBestModel("ollama"))
        val cfg = OllamaConfig(provider = "google", googleModel = "gemini-1.5-pro", autoPickModel = true)
        assertEquals("gemini-3.1-flash-lite", Ollama.applyAutoPick(cfg, listOf("gemini-3.1-flash-lite", "gemini-1.5-pro", "gemini-2.0-flash")))
        assertEquals("gemini-3.1-flash-lite", cfg.googleModel)
        assertTrue(Ollama.isModelAccessError("Google AI Error (404): models/gemini-1.5-flash is not found"))
        assertFalse(Ollama.isModelAccessError("Google AI Error (429): Resource exhausted"))
        assertTrue(Ollama.isTransientProviderError("Google AI Error (429): Resource exhausted"))
        assertTrue(Ollama.isTransientProviderError("ChatGPT Error (503): Service unavailable"))
        assertFalse(Ollama.isTransientProviderError("Google AI Error (401): API key invalid"))
        assertEquals(
            listOf("gemini-3.8-flash", "gemini-3.6-flash", "gemini-3.5-flash-lite", "gemini-3.1-flash-lite"),
            Ollama.googleGenerateCandidates(OllamaConfig(googleModel = "gemini-1.5-flash")),
        )
    }
    @Test fun autoPickSkipsModelsThatAlreadyFailedForThisKey() {
        Ollama.clearFailedModels()
        Ollama.rememberFailedModel("gemini-3.8-flash")
        Ollama.rememberFailedModel("gemini-3.6-flash")
        assertEquals("gemini-3.5-flash-lite", Ollama.pickBestModel("google"))
        assertEquals(
            listOf("gemini-3.5-flash-lite", "gemini-3.1-flash-lite"),
            Ollama.googleGenerateCandidates(OllamaConfig(googleModel = "gemini-3.8-flash")),
        )
        Ollama.clearFailedModels()
        assertEquals("gemini-3.8-flash", Ollama.pickBestModel("google"))
    }
    @Test fun fetchedModelMenuIsCategorizedWithPrices() {
        val ids = listOf(
            "gemini-3.5-flash",
            "gemini-3.5-flash-lite",
            "gemini-omni-1.1-flash",
            "gemini-3.1-flash-lite-image",
            "gemini-3.8-flash-tts",
            "gemini-3.5-transcribe",
            "gemini-4-argon",
            "gemini-nano-banana-2.1",
        )
        val catalog = Ollama.catalogFetchedModels("google", ids)
        assertEquals(ids.size, catalog.size)
        assertTrue(catalog.any { it.category.startsWith("Flash") && it.price.contains("Free", ignoreCase = true) })
        assertTrue(catalog.any { it.category.startsWith("Omni") && it.price.contains("Paid") })
        assertTrue(catalog.any { it.category.startsWith("Image") })
        assertTrue(catalog.any { it.category.startsWith("Speech") })
        assertTrue(catalog.any { it.category.startsWith("Pro") })
        // Flash / nano cheap buckets come before paid specialty / frontier.
        assertTrue(catalog.first().categoryOrder <= catalog.last().categoryOrder)
        val openai = Ollama.catalogFetchedModels("openai", listOf("gpt-4o-mini", "gpt-4o", "o1-mini"))
        assertEquals("Cheap · text", openai.first { it.id == "gpt-4o-mini" }.price)
        assertTrue(openai.first { it.id == "gpt-4o" }.price.contains("Paid"))
    }
    @Test fun emptyModelListNeverInventsAvailableModels() {
        assertTrue(Ollama.googleTextModels("""{"models":[]}""").isEmpty())
        assertTrue(Ollama.googleTextModels("{}").isEmpty())
    }
    @Test fun malformedModelListIsAnError() { assertTrue(runCatching { Ollama.googleTextModels("invalid JSON") }.isFailure) }
    @Test fun accountFlagsNeverAuthorizeRealApiAccess() {
        val c = OllamaConfig(googleSignedIn = true, googleAccountEmail = "coach@example.test", openAiSignedIn = true, openAiAccountEmail = "coach@example.test")
        assertFalse(c.isGoogleSignedIn); assertFalse(c.isOpenAiSignedIn)
        c.googleApiKey = "test-key"; c.openAiApiKey = "test-key"
        assertTrue(c.isGoogleSignedIn); assertTrue(c.isOpenAiSignedIn)
    }
    @Test fun failedProviderProducesReasonAndCannotPoisonCache() = runBlocking {
        val lg = LeagueGen.newLeague()
        val c = OllamaConfig(enabled = true, provider = "google", googleSignedIn = true)
        val n = News(lg.season, lg.day, "${lg.user.school} season preview", teamId = lg.userTeamId)
        val draft = Ollama.generateArticle(c, lg, n).getOrThrow()
        assertFalse(draft.isAi); assertTrue(draft.fallbackReason.contains("API key"))
        assertEquals("network error", AiDiagnostics.last?.result)
        draft.applyTo(n); assertEquals(draft.fallbackReason, n.aiFallbackReason)
        Ollama.Cache.clear(); Ollama.Cache.put("fallback", listOf(Ollama.Brief("headline", "body", false, draft.fallbackReason)))
        assertEquals(0, Ollama.Cache.size())
        Ollama.Cache.put("verified", Ollama.Brief("headline", "body", true)); assertEquals(1, Ollama.Cache.size()); Ollama.Cache.clear()
    }
    @Test fun diagnosticsDoNotExposeCredentialsAndOldRequestsCannotOverwriteNewerOnes() {
        val c = OllamaConfig(provider = "openai", openAiApiKey = "private-secret")
        val first = AiDiagnostics.begin(c, "Game report")
        val last = AiDiagnostics.begin(c, "Test AI")
        AiDiagnostics.finish(first, "success")
        assertEquals("requesting", AiDiagnostics.last?.result)
        val detail = AiDiagnostics.networkReason(Exception("HTTP 401 Bearer private-secret"), c)
        AiDiagnostics.finish(last, "network error", detail)
        assertFalse(AiDiagnostics.last.toString().contains("private-secret"))
        assertTrue(detail.contains("401"))
    }
}
