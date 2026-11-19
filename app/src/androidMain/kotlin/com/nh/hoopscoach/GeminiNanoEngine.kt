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
        // AICore writes 1 to 256 output tokens a request; a longer reply (the reply-length setting) is chained.
        val want = maxTokens.coerceIn(16, OnDeviceAi.MAX_OUTPUT * 3)
        val out = want.coerceAtMost(OnDeviceAi.MAX_OUTPUT)
        // About 3 characters a token, leaving room for the instructions and the reply.
        val budget = (((tokenLimit - out - 96).coerceAtLeast(500) * 3.0).toInt() - system.length).coerceAtLeast(1200)
        val input = OnDeviceAi.fit(prompt, budget)
        var (text, capped) = ask(m, input, system, temperature, out, onToken)
        var written = out
        // Stopped at the cap with room left in the reply: ask it to go on from exactly where it stopped. The
        // instructions are the same cached prefix; the new part is the reply so far.
        while (capped && written + 32 < want) {
            val more = (want - written).coerceAtMost(OnDeviceAi.MAX_OUTPUT)
            val sofar = "\n\nYOUR REPLY SO FAR (continue it from exactly where it stops: don't repeat any of it, don't start over, keep the same format):\n$text"
            val next = runCatching {
                delay(300)
                ask(m, OnDeviceAi.fit(input, (budget - sofar.length).coerceAtLeast(1200)) + sofar, system, temperature, more, onToken)
            }.getOrNull() ?: break
            if (next.first.isBlank()) break
            text = join(text, next.first)
            capped = next.second
            written += more
        }
        // Still stopped at the cap: don't end on half a sentence.
        if (capped) OnDeviceAi.tidy(text) else text
    }

    /** One request: the reply, and whether it stopped at the token cap (FinishReason.MAX_TOKENS). */
    private suspend fun ask(m: GenerativeModel, prompt: String, system: String, temperature: Double, out: Int, onToken: ((String) -> Unit)?): Pair<String, Boolean> {
        var input = prompt
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
                val candidate = response.candidates.firstOrNull()
                return candidate?.text.orEmpty().ifBlank { streamed.toString() } to (candidate?.finishReason == 1)
            } catch (e: GenAiException) {
                attempt++
                // A phone that won't take a cached prefix: send the instructions in the prompt from now on.
                if (prefixOk && e.errorCode in listOf(GenAiException.ErrorCode.CACHE_PROCESSING_ERROR, GenAiException.ErrorCode.REQUEST_PROCESSING_ERROR, GenAiException.ErrorCode.UNKNOWN)) {
                    prefixOk = false
                    continue
                }
                when (e.errorCode) {
                    // AICore is serving another request: one short pause, then the game's traffic control takes over.
                    GenAiException.ErrorCode.BUSY -> if (attempt <= 1) { delay(1_500); continue }
                    // Too long for the window after all: cut the middle further.
                    GenAiException.ErrorCode.REQUEST_TOO_LARGE -> if (attempt <= 2) { input = OnDeviceAi.fit(input, (input.length * 0.6).toInt()); continue }
                }
                throw refusal(e)
            }
        }
    }

    /** The reply so far and its continuation, joined at the seam. */
    private fun join(a: String, b: String): String {
        val seamless = a.isEmpty() || a.last().isWhitespace() || b.first().isWhitespace() || b.first() in ".,;:!?)'’\"-"
        return if (seamless) a + b else "$a $b"
    }

    /** AICore's error as a refusal the game's traffic control understands (rest a moment, rest for the day...). */
    private fun refusal(e: GenAiException): OnDeviceAi.Refused = OnDeviceAi.Refused(
        when (e.errorCode) {
            GenAiException.ErrorCode.BUSY -> OnDeviceAi.Refused.Kind.BUSY
            GenAiException.ErrorCode.PER_APP_BATTERY_USE_QUOTA_EXCEEDED -> OnDeviceAi.Refused.Kind.QUOTA
            GenAiException.ErrorCode.BACKGROUND_USE_BLOCKED -> OnDeviceAi.Refused.Kind.OFF_SCREEN
            else -> OnDeviceAi.Refused.Kind.OTHER
        },
        describe(e), e,
    )

    private fun mb(bytes: Long) = if (bytes >= 1_000_000_000) "%.1f GB".format(bytes / 1e9) else "${bytes / 1_000_000} MB"

    private fun describe(e: GenAiException): String = when (e.errorCode) {
        GenAiException.ErrorCode.BACKGROUND_USE_BLOCKED -> "Gemini Nano only writes while the game is on screen, so the game wrote this one"
        GenAiException.ErrorCode.BUSY -> "Gemini Nano was busy, so the game wrote this one"
        GenAiException.ErrorCode.PER_APP_BATTERY_USE_QUOTA_EXCEEDED -> "Gemini Nano has used this app's on-device allowance for now, so the game wrote this one. It resets on its own"
        GenAiException.ErrorCode.NOT_AVAILABLE -> "Gemini Nano isn't available on this phone yet. Update AICore and Google Play services, then check again."
        GenAiException.ErrorCode.NEEDS_SYSTEM_UPDATE, GenAiException.ErrorCode.AICORE_INCOMPATIBLE -> "AICore needs an update. Update it (and Android System Intelligence) from the Play Store, then check again."
        GenAiException.ErrorCode.NOT_ENOUGH_DISK_SPACE -> "Not enough free storage for Gemini Nano."
        GenAiException.ErrorCode.REQUEST_TOO_LARGE -> "That request was too long for Gemini Nano."
        GenAiException.ErrorCode.CANCELLED -> "The request was cancelled."
        else -> "Gemini Nano error ${e.errorCode}: ${e.message ?: "no details"}"
    }
}
