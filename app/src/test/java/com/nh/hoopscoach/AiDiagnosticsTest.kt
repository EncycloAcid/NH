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
