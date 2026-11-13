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
        "google" -> if (config.googleApiKey.isBlank()) "Gemini requires an API key. Email/password or browser sign-in cannot authorize this app." else "Gemini uses your API key and selected model; API access and quota are separate from a Google account."
        "openai" -> if (config.openAiApiKey.isBlank()) "OpenAI requires an API key. A ChatGPT login or subscription does not authorize API requests." else "OpenAI uses your API key; API billing and quota are separate from ChatGPT."
        "anthropic" -> if (config.anthropicApiKey.isBlank()) "Claude requires an Anthropic API key from console.anthropic.com. Account login does not authorize this app." else "Claude uses your Anthropic API key; billing and quota are separate from claude.ai chat."
        "groq" -> if (config.groqApiKey.isBlank()) "Groq requires an API key from console.groq.com." else "Groq uses your API key with a generous free tier for Llama and Gemma models."
        "deepseek" -> if (config.deepseekApiKey.isBlank()) "DeepSeek requires an API key from platform.deepseek.com." else "DeepSeek uses your API key; chat is cheap for dynasty copy."
        "openrouter" -> if (config.openRouterApiKey.isBlank()) "OpenRouter requires an API key from openrouter.ai. Prefer models ending in :free." else "OpenRouter routes free (:free) and paid models with your key."
        "cerebras" -> if (config.cerebrasApiKey.isBlank()) "Cerebras requires an API key from cloud.cerebras.ai." else "Cerebras free-tier Llama is very fast for dynasty copy."
        "mistral" -> if (config.mistralApiKey.isBlank()) "Mistral requires an API key from console.mistral.ai." else "Mistral free-tier Small / Nemo work well for short recaps."
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
            "401", "403" -> "Provider rejected API access (HTTP $code). Check the API key and permissions."
            "404" -> "Model or endpoint not found (HTTP 404). Select a model available to your account."
            "429" -> "Provider quota or rate limit reached (HTTP 429). The app retries and queues AI desks; wait a moment or lower auto-generate desks."
            "502", "503", "504" -> "Provider temporarily overloaded (HTTP $code). The app retries automatically — try Test AI again if it still fails."
            null -> if (config.provider == "ollama") "Ollama could not respond. Check the server address, installed model and connection. Localhost is this phone." else "Provider connection failed (${error?.javaClass?.simpleName ?: "unknown error"}). Check connectivity and API settings."
            else -> "Provider request failed (HTTP $code). Try again or check AI settings."
        }
    }
}
