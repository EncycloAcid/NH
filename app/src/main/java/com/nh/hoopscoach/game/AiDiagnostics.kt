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
        else -> "Ollama must run separately. Localhost means this phone, not your computer. Use the computer's LAN IP and port 11434 on the same network; bind Ollama to that LAN address and allow the port through its firewall. 10.0.2.2 is emulator-only."
    }
    fun networkReason(error: Throwable?, config: OllamaConfig): String {
        if (listOf("token limit", "Incomplete model stream", "incomplete or filtered").any { error?.message?.contains(it, true) == true }) return "Model response was cut off or blocked before completion. Used complete game-written coverage."
        if (error?.message?.contains("no compatible text models", true) == true) return "Provider lists no compatible text models. Check API access or enter a supported model ID manually."
        if (error?.message?.contains("fact sheet exceeds", true) == true) return "The report exceeds the local model context budget. Use a cloud model or a smaller report."
        if ((config.provider == "google" && config.googleApiKey.isBlank()) || (config.provider == "openai" && config.openAiApiKey.isBlank())) return setupHint(config)
        val code = Regex("\\b(400|401|403|404|408|429|500|502|503|504)\\b").find(error?.message.orEmpty())?.value
        return when (code) {
            "401", "403" -> "Provider rejected API access (HTTP $code). Check the API key and permissions."
            "404" -> "Model or endpoint not found (HTTP 404). Select a model available to your account."
            "429" -> "Provider quota or rate limit reached (HTTP 429). Check API billing and quota."
            null -> if (config.provider == "ollama") "Ollama could not respond. Check the server address, installed model and connection. Localhost is this phone." else "Provider connection failed (${error?.javaClass?.simpleName ?: "unknown error"}). Check connectivity and API settings."
            else -> "Provider request failed (HTTP $code). Try again or check AI settings."
        }
    }
}
