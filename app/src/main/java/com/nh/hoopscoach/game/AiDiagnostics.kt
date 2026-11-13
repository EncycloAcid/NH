package com.nh.hoopscoach.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.atomic.AtomicLong

@Serializable
data class AiRequestStatus(val id: Long, val provider: String, val model: String, val request: String, val time: Long, val result: String, val detail: String = "")

object AiDiagnostics {
    private val sequence = AtomicLong()
    private var file: File? = null
    var last by mutableStateOf<AiRequestStatus?>(null)
        private set
    fun install(root: File) {
        file = File(root, "ai-last-request.json")
        last = runCatching { file?.let { Json.decodeFromString(AiRequestStatus.serializer(), it.readText()) } }.getOrNull()
        sequence.set(last?.id ?: 0)
    }
    @Synchronized fun begin(config: OllamaConfig, request: String): Long {
        val id = sequence.incrementAndGet()
        last = AiRequestStatus(id, config.provider, config.activeModelName, request, System.currentTimeMillis(), "requesting")
        persist()
        return id
    }
    @Synchronized fun finish(id: Long, result: String, detail: String = "") {
        if (last?.id != id) return
        last = last?.copy(result = result, detail = detail)
        persist()
    }
    private fun persist() { val status = last ?: return; runCatching { file?.writeText(Json.encodeToString(AiRequestStatus.serializer(), status)) } }
    fun setupHint(config: OllamaConfig): String = when (config.provider.lowercase()) {
        "google" -> if (config.googleApiKey.isBlank()) "Connect your Google account: Sign in at AI Studio in the browser, then paste the free access key here. Never enter your Google password in this app." else "Google Gemini is connected with your account access key. Quota and billing follow that Google AI account."
        "openai" -> if (config.openAiApiKey.isBlank()) "Connect your OpenAI / ChatGPT account: Sign in at platform.openai.com in the browser, then paste an access key. ChatGPT Plus alone does not authorize this app." else "OpenAI is connected with your account access key. API billing is separate from ChatGPT Plus."
        "anthropic" -> if (config.anthropicApiKey.isBlank()) "Connect your Claude account: Sign in at console.anthropic.com in the browser, then paste an access key. claude.ai chat login alone does not authorize this app." else "Claude is connected with your Anthropic account access key. Billing is separate from claude.ai chat."
        "groq" -> if (config.groqApiKey.isBlank()) "Connect your Groq account: Sign in at console.groq.com (free tier), then paste an access key." else "Groq is connected — free tier covers Llama and Gemma models."
        "deepseek" -> if (config.deepseekApiKey.isBlank()) "Connect your DeepSeek account: Sign in at platform.deepseek.com, then paste an access key." else "DeepSeek is connected with your account access key."
        "openrouter" -> if (config.openRouterApiKey.isBlank()) "Connect your OpenRouter account: Sign in at openrouter.ai, then paste an access key. Prefer models ending in :free." else "OpenRouter is connected — free (:free) and paid models use your account key."
        "cerebras" -> if (config.cerebrasApiKey.isBlank()) "Connect your Cerebras account: Sign in at cloud.cerebras.ai (free tier), then paste an access key." else "Cerebras is connected — free-tier Llama is very fast for dynasty copy."
        "mistral" -> if (config.mistralApiKey.isBlank()) "Connect your Mistral account: Sign in at console.mistral.ai (free tier), then paste an access key." else "Mistral is connected — free-tier Small / Nemo work well for short recaps."
        else -> "Ollama must run separately. Localhost means this phone, not your computer. Use the computer's LAN IP and port 11434 on the same network; bind Ollama to that LAN address and allow the port through its firewall. 10.0.2.2 is emulator-only."
    }
    fun networkReason(error: Throwable?, config: OllamaConfig): String {
        if (listOf("token limit", "Incomplete model stream", "incomplete or filtered").any { error?.message?.contains(it, true) == true }) return "Model response was cut off or blocked before completion. Used complete game-written coverage."
        if (error?.message?.contains("no compatible text models", true) == true) return "Provider lists no compatible text models. Check API access or enter a supported model ID manually."
        if (error?.message?.contains("fact sheet exceeds", true) == true) return "The report exceeds the local model context budget. Use a cloud model or a smaller report."
        val missingKey = when (config.provider.lowercase()) {
            "google" -> config.googleApiKey.isBlank()
            "openai" -> config.openAiApiKey.isBlank()
            "anthropic" -> config.anthropicApiKey.isBlank()
            "groq" -> config.groqApiKey.isBlank()
            "deepseek" -> config.deepseekApiKey.isBlank()
            "openrouter" -> config.openRouterApiKey.isBlank()
            "cerebras" -> config.cerebrasApiKey.isBlank()
            "mistral" -> config.mistralApiKey.isBlank()
            else -> false
        }
        if (missingKey) return setupHint(config)
        val code = Regex("\\b(400|401|403|404|408|429|500|502|503|504)\\b").find(error?.message.orEmpty())?.value
        return when (code) {
            "401", "403" -> "Provider rejected account access (HTTP $code). Reconnect and check the access key from your account."
            "404" -> "Model or endpoint not found (HTTP 404). Select a model available to your account."
            "429" -> "Provider quota or rate limit reached (HTTP 429). The app retries and queues AI desks; wait a moment or lower auto-generate desks."
            "502", "503", "504" -> "Provider temporarily overloaded (HTTP $code). The app retries automatically — try Test AI again if it still fails."
            null -> if (config.provider == "ollama") "Ollama could not respond. Check the server address, installed model and connection. Localhost is this phone." else "Provider connection failed (${error?.javaClass?.simpleName ?: "unknown error"}). Check connectivity and reconnect your account."
            else -> "Provider request failed (HTTP $code). Try again or check AI settings."
        }
    }
}
