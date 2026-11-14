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
        "google" -> if (config.googleApiKey.isBlank()) "Gemini Advanced chat cannot power this game. Add a free AI Studio developer key from aistudio.google.com." else "Gemini is using your AI Studio developer key (separate from Gemini Advanced chat)."
        "openai" -> if (config.openAiApiKey.isBlank()) "ChatGPT Plus cannot power this game. Add an OpenAI developer key from platform.openai.com (billed separately)." else "OpenAI is using your developer key (separate from ChatGPT Plus)."
        "anthropic" -> if (config.anthropicApiKey.isBlank()) "Claude Pro / Max cannot power this game — Anthropic blocks third-party apps from using those subscriptions. Add a Claude Console developer key, or pick a free provider (Groq / OpenRouter)." else "Claude is using your Console developer key (separate from Claude Pro chat)."
        "groq" -> if (config.groqApiKey.isBlank()) "Add a free Groq developer key from console.groq.com." else "Groq is connected — free tier covers Llama and Gemma models."
        "deepseek" -> if (config.deepseekApiKey.isBlank()) "Add a DeepSeek developer key from platform.deepseek.com." else "DeepSeek is connected with your developer key."
        "openrouter" -> if (config.openRouterApiKey.isBlank()) "Add an OpenRouter key from openrouter.ai. Prefer models ending in :free." else "OpenRouter is connected — free (:free) and paid models use your key."
        "cerebras" -> if (config.cerebrasApiKey.isBlank()) "Add a free Cerebras key from cloud.cerebras.ai." else "Cerebras is connected — free-tier Llama is very fast for dynasty copy."
        "mistral" -> if (config.mistralApiKey.isBlank()) "Add a free Mistral key from console.mistral.ai." else "Mistral is connected — free-tier Small / Nemo work well for short recaps."
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
