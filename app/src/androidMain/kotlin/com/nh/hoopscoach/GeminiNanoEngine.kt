package com.nh.hoopscoach

import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.common.StreamingCallback
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.ModelPreference
import com.google.mlkit.genai.prompt.ModelReleaseStage
import com.google.mlkit.genai.prompt.PromptPrefix
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import com.google.mlkit.genai.prompt.generationConfig
import com.google.mlkit.genai.prompt.modelConfig
import com.nh.hoopscoach.game.OnDeviceAi
import com.nh.hoopscoach.game.OnDeviceAi.State
import com.nh.hoopscoach.game.OnDeviceAi.Status
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Gemini Nano on the phone, through AICore (ML Kit's GenAI Prompt API). Asks for the full-quality model, or
 * the fast one when the setting says so; a phone that offers only its default model gets that.
 */
class GeminiNanoEngine : OnDeviceAi.Engine {
    private val lock = Mutex()
    private var full: GenerativeModel? = null
    private var fast: GenerativeModel? = null
    private var plain: GenerativeModel? = null
    /** The phone didn't take a model preference: use its default model for everything. */
    private var usePlain = false
    private var tokenLimit = 0
    /** AICore takes the instructions as a cached prefix; off if this phone's AICore refuses prefixes. */
    private var prefixOk = true

    private fun preferred(fastMode: Boolean): GenerativeModel {
        if (usePlain) return plain ?: Generation.getClient().also { plain = it }
        val cached = if (fastMode) fast else full
        if (cached != null) return cached
        val m = Generation.getClient(generationConfig {
            modelConfig = modelConfig {
                preference = if (fastMode) ModelPreference.FAST else ModelPreference.FULL
                releaseStage = ModelReleaseStage.STABLE
            }
        })
        if (fastMode) fast = m else full = m
        return m
    }

    /** The model to use: the preferred one if the phone has it, else its default. */
    private suspend fun client(fastMode: Boolean): GenerativeModel {
        if (!usePlain) {
            val m = runCatching { preferred(fastMode) }.getOrNull()
            if (m != null && runCatching { m.checkStatus() }.getOrDefault(FeatureStatus.UNAVAILABLE) != FeatureStatus.UNAVAILABLE) return m
            usePlain = true
        }
        return plain ?: Generation.getClient().also { plain = it }
    }

    override suspend fun status(): Status = lock.withLock {
        try {
            val m = client(false)
            when (m.checkStatus()) {
                FeatureStatus.AVAILABLE -> Status(State.READY, "Runs on this phone's chip. Nothing leaves the phone.", runCatching { m.getBaseModelName() }.getOrDefault(""))
                FeatureStatus.DOWNLOADABLE -> Status(State.DOWNLOADABLE, "Gemini Nano needs a one-time download from Google (on Wi-Fi is best).")
                FeatureStatus.DOWNLOADING -> Status(State.DOWNLOADING, "Gemini Nano is downloading. It's ready when this says so.")
                else -> Status(State.UNAVAILABLE, "This phone doesn't offer Gemini Nano to apps right now. It needs a Pixel 9 or newer (or another AICore phone), up-to-date AICore and Google Play services, and a locked bootloader.")
            }
        } catch (e: GenAiException) {
            Status(State.UNAVAILABLE, describe(e))
        } catch (e: Throwable) {
            Status(State.UNAVAILABLE, "AICore didn't answer: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    override suspend fun download(onProgress: (String) -> Unit): Result<Unit> = runCatching {
        var total = 0L
        var failed: Throwable? = null
        client(false).download().collect { s ->
            when (s) {
                is DownloadStatus.DownloadStarted -> {
                    total = s.bytesToDownload
                    onProgress("Starting download" + if (total > 0) " · ${mb(total)}" else "")
                }
                is DownloadStatus.DownloadProgress -> onProgress(
                    if (total > 0) "Downloading · ${(s.totalBytesDownloaded * 100 / total).coerceIn(0, 100)}%" else "Downloading · ${mb(s.totalBytesDownloaded)}",
                )
                is DownloadStatus.DownloadFailed -> failed = s.e
                else -> onProgress("Download complete")
            }
        }
        failed?.let { throw Exception(if (it is GenAiException) describe(it) else it.message ?: "Download failed", it) }
    }

    override suspend fun generate(prompt: String, system: String, temperature: Double, maxTokens: Int, fast: Boolean, onToken: ((String) -> Unit)?): String = lock.withLock {
        val m = client(fast)
        if (tokenLimit <= 0) tokenLimit = runCatching { m.getTokenLimit() }.getOrDefault(4000).takeIf { it > 0 } ?: 4000
        val out = maxTokens.coerceIn(32, 1024)
        // About 3 characters a token, leaving room for the instructions and the reply.
        val budget = ((tokenLimit - out - 96).coerceAtLeast(500) * 3.0).toInt() - system.length
        var input = OnDeviceAi.fit(prompt, budget.coerceAtLeast(1200))
        var attempt = 0
        while (true) {
            try {
                // The instructions are word for word the same every time for each desk (the weekly Dispatch, the
                // press conference...), so AICore's prefix cache reuses them and only the new facts are processed.
                val cached = prefixOk
                val request = generateContentRequest(TextPart(if (cached) input else "$system\n\n$input")) {
                    if (cached) promptPrefix = PromptPrefix(system)
                    this.temperature = temperature.toFloat().coerceIn(0f, 1f)
                    topK = 40
                    maxOutputTokens = out
                }
                val streamed = StringBuilder()
                val response = m.generateContent(request, object : StreamingCallback {
                    override fun onNewText(text: String) {
                        streamed.append(text)
                        onToken?.invoke(text)
                    }
                })
                return@withLock response.candidates.firstOrNull()?.text.orEmpty().ifBlank { streamed.toString() }
            } catch (e: GenAiException) {
                attempt++
                // A phone that won't take a cached prefix: send the instructions in the prompt from now on.
                if (prefixOk && e.errorCode in listOf(GenAiException.ErrorCode.CACHE_PROCESSING_ERROR, GenAiException.ErrorCode.REQUEST_PROCESSING_ERROR, GenAiException.ErrorCode.UNKNOWN)) {
                    prefixOk = false
                    continue
                }
                when (e.errorCode) {
                    // AICore is serving another request: back off and try again.
                    GenAiException.ErrorCode.BUSY -> if (attempt <= 3) { delay(400L shl attempt); continue }
                    // Too long for the window after all: cut the middle further.
                    GenAiException.ErrorCode.REQUEST_TOO_LARGE -> if (attempt <= 2) { input = OnDeviceAi.fit(input, (input.length * 0.6).toInt()); continue }
                }
                throw Exception(describe(e), e)
            }
        }
        @Suppress("UNREACHABLE_CODE") ""
    }

    private fun mb(bytes: Long) = if (bytes >= 1_000_000_000) "%.1f GB".format(bytes / 1e9) else "${bytes / 1_000_000} MB"

    private fun describe(e: GenAiException): String = when (e.errorCode) {
        GenAiException.ErrorCode.BACKGROUND_USE_BLOCKED -> "Gemini Nano only answers while the game is on screen. The game's own writing filled in."
        GenAiException.ErrorCode.BUSY -> "Gemini Nano is busy with another request. Try again in a moment."
        GenAiException.ErrorCode.PER_APP_BATTERY_USE_QUOTA_EXCEEDED -> "This app has used today's on-device AI allowance. It resets on its own; the game's own writing fills in until then."
        GenAiException.ErrorCode.NOT_AVAILABLE -> "Gemini Nano isn't available on this phone yet. Update AICore and Google Play services, then check again."
        GenAiException.ErrorCode.NEEDS_SYSTEM_UPDATE, GenAiException.ErrorCode.AICORE_INCOMPATIBLE -> "AICore needs an update. Update it (and Android System Intelligence) from the Play Store, then check again."
        GenAiException.ErrorCode.NOT_ENOUGH_DISK_SPACE -> "Not enough free storage for Gemini Nano."
        GenAiException.ErrorCode.REQUEST_TOO_LARGE -> "That request was too long for Gemini Nano."
        GenAiException.ErrorCode.CANCELLED -> "The request was cancelled."
        else -> "Gemini Nano error ${e.errorCode}: ${e.message ?: "no details"}"
    }
}
