package com.nh.hoopscoach.game

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections

/**
 * Client for interacting with local Ollama instances to control and generate stories,
 * game recaps, and season preview storylines.
 */
object Ollama {
    private val json = Json { ignoreUnknownKeys = true }
    /** Free-tier keys get hammered when every desk launches at once — queue cloud/local generates. */
    private val generateGate = Mutex()

    data class ModelPreset(
        val id: String,
        val name: String,
        val size: String,
        val tag: String,
        val description: String,
        val speedRating: String = "Fast",
    )

    /** Free / cheap on-device presets only — no large "pro" phone models. */
    val RECOMMENDED_MODELS = listOf(
        ModelPreset(
            id = "smollm2:135m",
            name = "SmolLM2 135M",
            size = "270 MB",
            tag = "Fastest · free",
            speedRating = "40–60 tok/s",
            description = "Fastest on-device model (~270MB). Instant recaps and quotes with minimal RAM.",
        ),
        ModelPreset(
            id = "qwen2.5:0.5b",
            name = "Qwen 2.5 0.5B",
            size = "398 MB",
            tag = "Ultra fast · free",
            speedRating = "35–45 tok/s",
            description = "Lightweight (~400MB). High-speed generation with sharp basketball context.",
        ),
        ModelPreset(
            id = "llama3.2:1b",
            name = "Llama 3.2 1B",
            size = "1.3 GB",
            tag = "Recommended · free",
            speedRating = "15–20 tok/s",
            description = "Meta's mobile LLM. Fast, witty recaps and realistic quotes with low RAM.",
        ),
    )

    data class CloudModel(
        val id: String,
        val name: String,
        val tag: String,
        val description: String,
    )

    /**
     * Free Gemini text models for new API keys (Nov 2026).
     * Order is probe preference — generate falls through this list when one 404s / is blocked.
     * Shut-down 1.5 / 2.0 ids are intentionally absent — see [snapGoogleModel].
     */
    val GOOGLE_MODELS = listOf(
        CloudModel("gemini-3.8-flash", "Gemini 3.8 Flash", "Recommended · free", "Current free-tier flash. Reliable for recaps and quotes."),
        CloudModel("gemini-3.6-flash", "Gemini 3.6 Flash", "Free · stable", "Prior free flash — strong fallback when 3.8 is unavailable."),
        CloudModel("gemini-3.5-flash-lite", "Gemini 3.5 Flash-Lite", "Free · cheapest", "Cheapest free flash-lite when full flash is rate-limited."),
        CloudModel("gemini-3.1-flash-lite", "Gemini 3.1 Flash-Lite", "Free · cheap", "Older free flash-lite fallback."),
    )

    /** Session denylist of model ids that returned not-found / not-allowed for this process. */
    private val failedModelIds: MutableSet<String> = Collections.synchronizedSet(linkedSetOf())

    fun rememberFailedModel(id: String) {
        val key = id.trim().removePrefix("models/").lowercase()
        if (key.isNotBlank()) failedModelIds += key
    }

    fun forgetFailedModel(id: String) {
        failedModelIds -= id.trim().removePrefix("models/").lowercase()
    }

    fun clearFailedModels() {
        failedModelIds.clear()
    }

    fun isFailedModel(id: String): Boolean =
        failedModelIds.contains(id.trim().removePrefix("models/").lowercase())

    /** Cheap OpenAI tags only — flagship gpt-4o is hidden from Settings. */
    val OPENAI_MODELS = listOf(
        CloudModel("gpt-4o-mini", "GPT-4o mini", "Recommended · cheap", "Affordable and fast. Strong basketball context."),
        CloudModel("gpt-3.5-turbo", "GPT-3.5 Turbo", "Cheap · legacy", "Classic ChatGPT for basic headlines and recaps."),
    )

    /** Remaps retired / expensive / specialty ids onto the free curated default. Live flash picks stick. */
    fun snapGoogleModel(id: String): String {
        val t = id.trim().removePrefix("models/").ifEmpty { return GOOGLE_MODELS.first().id }
        val low = t.lowercase()
        if (!eligibleGoogleTextModel(low) || isRetiredGoogleModel(low) || isExpensiveGoogleModel(low)) {
            return GOOGLE_MODELS.first().id
        }
        return t
    }

    fun snapOpenAiModel(id: String): String {
        val t = id.trim().ifEmpty { return OPENAI_MODELS.first().id }
        val low = t.lowercase()
        val blocked = low == "gpt-4o" || low == "gpt-4" || low == "gpt-4-turbo" || low == "gpt-4-turbo-preview" ||
            low.startsWith("o1") || low.startsWith("o3") || low.startsWith("o4")
        return if (blocked) OPENAI_MODELS.first().id else t
    }

    fun snapLocalModel(id: String): String {
        val t = id.trim().ifEmpty {
            return RECOMMENDED_MODELS.first { it.tag.contains("Recommended", true) }.id
        }
        val low = t.lowercase()
        val heavy = Regex(""":(3|7|8|13|70)b\b""").containsMatchIn(low) || low.endsWith("3b") || low.contains("3b:")
        return if (heavy) RECOMMENDED_MODELS.first { it.tag.contains("Recommended", true) }.id else t
    }

    /** Shut-down Gemini families that still appear in old saves / menus. */
    internal fun isRetiredGoogleModel(id: String): Boolean {
        val key = id.lowercase().removePrefix("models/")
        return key.startsWith("gemini-1.") || key.startsWith("gemini-2.0") ||
            key == "gemini-pro" || key == "gemini-ultra" || key == "gemini-1.0-pro"
    }

    internal fun isExpensiveGoogleModel(id: String): Boolean {
        val key = id.lowercase().removePrefix("models/")
        return (key.contains("pro") && !key.contains("flash") && !key.contains("nano")) ||
            "argon" in key || "carbon" in key || "hydrogen" in key || "ultra" in key
    }

    /** Image / audio / omni endpoints are listed by Google but fail for text desks. */
    internal fun isSpecialtyGoogleModel(id: String): Boolean {
        val key = id.lowercase().removePrefix("models/")
        return "tts" in key || "transcribe" in key || "speech" in key || "image" in key ||
            "imagen" in key || "banana" in key || "omni" in key || "embed" in key
    }

    internal fun isUnstableGoogleModel(id: String): Boolean {
        val key = id.lowercase().removePrefix("models/")
        return "preview" in key || "experimental" in key || "exp" in key.split('-') ||
            key.endsWith("-latest") || key.contains("latest")
    }

    internal fun eligibleGoogleTextModel(id: String): Boolean {
        val key = id.lowercase().removePrefix("models/")
        return key.contains("gemini") && !isSpecialtyGoogleModel(key)
    }

    /**
     * Ordered free/cheap candidates for [provider]. Prefer curated ids in [available];
     * skip specialty / preview / retired / previously-failed ids.
     */
    fun rankedAutoPickCandidates(provider: String, available: List<String> = emptyList()): List<String> {
        val curated = when (provider.lowercase()) {
            "google" -> GOOGLE_MODELS.map { it.id }
            "openai" -> OPENAI_MODELS.map { it.id }
            else -> RECOMMENDED_MODELS.map { it.id }
        }
        val raw = available.map { it.trim().removePrefix("models/") }.filter { it.isNotBlank() }.distinct()
        val pool = when (provider.lowercase()) {
            "google" -> {
                val eligible = raw.filter { eligibleGoogleTextModel(it) && !isRetiredGoogleModel(it) && !isExpensiveGoogleModel(it) }
                val stable = eligible.filterNot { isUnstableGoogleModel(it) }.ifEmpty { eligible }
                val curatedHits = curated.filter { c -> stable.any { it.equals(c, true) } }
                when {
                    curatedHits.isNotEmpty() -> curatedHits
                    stable.isNotEmpty() -> stable
                    else -> curated
                }
            }
            "openai" -> {
                val cheap = raw.filter {
                    val k = it.lowercase()
                    ("mini" in k || "3.5" in k || "nano" in k) && !k.startsWith("o1") && !k.startsWith("o3") && !k.startsWith("o4")
                }
                val curatedHits = curated.filter { c -> raw.any { it.equals(c, true) } }
                when {
                    curatedHits.isNotEmpty() -> curatedHits
                    cheap.isNotEmpty() -> cheap
                    raw.isNotEmpty() -> raw
                    else -> curated
                }
            }
            else -> raw.ifEmpty { curated }
        }
        val ranked = catalogFetchedModels(provider, pool)
            .sortedWith(
                compareBy<CatalogEntry> { curatedIndex(provider, it.id) }
                    .thenBy { it.categoryOrder }
                    .thenBy { modelPreferenceScore(provider, it.id) }
                    .thenBy { if (provider.equals("google", true) && isUnstableGoogleModel(it.id)) 1 else 0 }
                    .thenByDescending { googleVersionScore(it.id) }
                    .thenBy { it.id.lowercase() },
            )
            .map {
                when (provider.lowercase()) {
                    "google" -> snapGoogleModel(it.id)
                    "openai" -> snapOpenAiModel(it.id)
                    else -> snapLocalModel(it.id)
                }
            }
            .distinct()
        val live = ranked.filterNot { isFailedModel(it) }
        return live.ifEmpty { ranked.ifEmpty { curated } }
    }

    /**
     * Best free/cheap text model for [provider].
     * Prefer curated ids that appear in [available]; skip specialty / preview / retired / failed junk;
     * otherwise fall back to the curated Settings shortlist (never invent unavailable GA aliases).
     */
    fun pickBestModel(provider: String, available: List<String> = emptyList()): String {
        val curated = when (provider.lowercase()) {
            "google" -> GOOGLE_MODELS.map { it.id }
            "openai" -> OPENAI_MODELS.map { it.id }
            else -> RECOMMENDED_MODELS.map { it.id }
        }
        return rankedAutoPickCandidates(provider, available).firstOrNull() ?: curated.first()
    }

    /** Curated shortlist order — lower is better; unknown ids sort after. */
    private fun curatedIndex(provider: String, id: String): Int {
        val curated = when (provider.lowercase()) {
            "google" -> GOOGLE_MODELS.map { it.id }
            "openai" -> OPENAI_MODELS.map { it.id }
            else -> RECOMMENDED_MODELS.map { it.id }
        }
        val idx = curated.indexOfFirst { it.equals(id, true) }
        return if (idx >= 0) idx else curated.size + 1
    }

    /** Prefer newer Gemini generations when ranking non-curated flash ids. */
    internal fun googleVersionScore(id: String): Int {
        val key = id.lowercase().removePrefix("models/")
        val m = Regex("""gemini-(\d+(?:\.\d+)?)""").find(key) ?: return 0
        val parts = m.groupValues[1].split('.')
        val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return major * 100 + minor
    }

    /** Lower is better — flash-lite / mini / smol before heavier free options. */
    internal fun modelPreferenceScore(provider: String, id: String): Int {
        val key = id.lowercase().removePrefix("models/")
        return when (provider.lowercase()) {
            "google" -> when {
                "flash-lite" in key || (key.contains("flash") && "lite" in key) -> 0
                "flash" in key -> 1
                "nano" in key -> 2
                else -> 8
            }
            "openai" -> when {
                "mini" in key -> 0
                "3.5" in key -> 1
                "nano" in key -> 2
                else -> 8
            }
            else -> when {
                key == "smollm2:135m" || key.startsWith("smollm2:135m") -> 0
                key == "qwen2.5:0.5b" || key.startsWith("qwen2.5:0.5") -> 1
                key == "llama3.2:1b" || key.startsWith("llama3.2:1b") -> 2
                key.startsWith("smol") -> 3
                key.startsWith("qwen") -> 4
                key.startsWith("llama") -> 5
                else -> 8
            }
        }
    }

    /** Writes [pickBestModel] into the active provider's model field on [config]. */
    fun applyAutoPick(config: OllamaConfig, available: List<String> = emptyList()): String {
        val pick = pickBestModel(config.provider, available)
        when (config.provider.lowercase()) {
            "google" -> config.googleModel = pick
            "openai" -> config.openAiModel = pick
            else -> config.model = pick
        }
        return pick
    }

    /**
     * Rank → set → short generate probe. Walks provider fallbacks, denylists models that
     * 404 / are blocked for this key, and sticks the first id that actually returns text.
     */
    suspend fun applyAutoPickVerified(
        config: OllamaConfig,
        available: List<String> = emptyList(),
    ): Result<String> = withContext(Dispatchers.IO) {
        applyAutoPick(config, available)
        val before = config.activeModelName
        val probe = generate(
            config,
            prompt = "Reply with exactly OK.",
            system = "Connection test. Reply OK only.",
            temperature = 0.0,
            numPredict = 32,
        )
        if (probe.isSuccess) {
            val used = config.activeModelName
            forgetFailedModel(used)
            Result.success(used)
        } else {
            val err = probe.exceptionOrNull()
            if (isModelAccessError(err?.message)) rememberFailedModel(before)
            Result.failure(err ?: Exception("No working free/cheap model found for this API key."))
        }
    }

    /** Ordered generate fallbacks for Google when the selected id 404s / is retired. */
    fun googleGenerateCandidates(config: OllamaConfig): List<String> {
        val primary = snapGoogleModel(config.googleModel.ifBlank { GOOGLE_MODELS.first().id })
        val all = (listOf(primary) + GOOGLE_MODELS.map { it.id }).distinct()
        val live = all.filterNot { isFailedModel(it) }
        return live.ifEmpty { all }
    }

    fun openAiGenerateCandidates(config: OllamaConfig): List<String> {
        val primary = snapOpenAiModel(config.openAiModel.ifBlank { OPENAI_MODELS.first().id })
        val all = (listOf(primary) + OPENAI_MODELS.map { it.id }).distinct()
        val live = all.filterNot { isFailedModel(it) }
        return live.ifEmpty { all }
    }

    /** True when the provider error looks like a bad / missing / blocked model id (safe to try the next candidate). */
    internal fun isModelAccessError(message: String?): Boolean {
        val m = message?.lowercase().orEmpty()
        if (isTransientProviderError(m)) return false
        return "404" in m || "403" in m || "not found" in m || "not supported" in m || "is not found" in m ||
            "does not exist" in m || "invalid model" in m || "model_not_found" in m ||
            "no longer available" in m || "has been shut down" in m || "deprecated" in m ||
            "not available" in m || "not enabled" in m || "permission" in m || "not allowed" in m ||
            "does not have access" in m || "not eligible" in m || "unsupported" in m
    }

    /** Rate limits, overloads, and socket blips — retry same model before giving up. */
    internal fun isTransientProviderError(message: String?): Boolean {
        val m = message?.lowercase().orEmpty()
        return "429" in m || "502" in m || "503" in m || "504" in m || "408" in m ||
            "rate limit" in m || "resource_exhausted" in m || "unavailable" in m ||
            "timeout" in m || "timed out" in m || "connection reset" in m ||
            "connection refused" in m || "temporarily" in m || "try again" in m ||
            "socket" in m || "broken pipe" in m
    }

    private suspend fun <T> retryTransient(attempts: Int = 3, block: suspend (attempt: Int) -> T): T {
        var last: Exception? = null
        repeat(attempts) { i ->
            try {
                return block(i)
            } catch (e: Exception) {
                last = e
                if (!isTransientProviderError(e.message) || i == attempts - 1) throw e
                delay(400L * (1L shl i)) // 400ms, 800ms
            }
        }
        throw (last ?: Exception("Provider request failed"))
    }

    /**
     * One row in the fetched-model menu: provider id, display title, category header, and a short
     * price tag so long raw lists (especially Gemini) are readable.
     */
    data class CatalogEntry(
        val id: String,
        val title: String,
        val category: String,
        val price: String,
        val categoryOrder: Int,
    )

    /** Group + price labels for models returned by a provider's list endpoint. */
    fun catalogFetchedModels(provider: String, ids: List<String>): List<CatalogEntry> =
        ids.distinct().map { id -> catalogEntry(provider, id) }
            .sortedWith(compareBy({ it.categoryOrder }, { it.title.lowercase() }, { it.id.lowercase() }))

    private fun catalogEntry(provider: String, id: String): CatalogEntry {
        val key = id.lowercase().removePrefix("models/")
        val title = humanModelTitle(id)
        return when (provider.lowercase()) {
            "google" -> googleCatalog(key, id, title)
            "openai" -> openAiCatalog(key, id, title)
            else -> CatalogEntry(id, title, "On-device · free", "Free · local", 0)
        }
    }

    private fun humanModelTitle(id: String): String {
        val bare = id.removePrefix("models/").substringAfterLast('/')
        return bare.split('-', '_', ':').filter { it.isNotBlank() }.joinToString(" ") { part ->
            part.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
    }

    private fun googleCatalog(key: String, id: String, title: String): CatalogEntry = when {
        "tts" in key || "transcribe" in key || "speech" in key ->
            CatalogEntry(id, title, "Speech · TTS / audio", "Paid · audio", 4)
        "image" in key || "imagen" in key || "banana" in key ->
            CatalogEntry(id, title, "Image", "Paid · image", 3)
        "omni" in key ->
            CatalogEntry(id, title, "Omni · multimodal", "Paid · multimodal", 2)
        "nano" in key ->
            CatalogEntry(id, title, "Nano · tiny", "Free · on-device", 0)
        "flash-lite" in key || key.contains("flash") && "lite" in key ->
            CatalogEntry(id, title, "Flash · free & cheap", "Free tier · cheapest", 0)
        "flash" in key ->
            CatalogEntry(id, title, "Flash · free & cheap", "Free tier · cheap", 0)
        "pro" in key || "ultra" in key || "argon" in key || "carbon" in key || "hydrogen" in key ->
            CatalogEntry(id, title, "Pro / frontier · paid", "Paid · premium", 1)
        else ->
            CatalogEntry(id, title, "Other Gemini", "See Google pricing", 5)
    }

    private fun openAiCatalog(key: String, id: String, title: String): CatalogEntry = when {
        "mini" in key || "3.5" in key || "nano" in key ->
            CatalogEntry(id, title, "Cheap ChatGPT", "Cheap · text", 0)
        "o1" in key || "o3" in key || "o4" in key ->
            CatalogEntry(id, title, "Reasoning · premium", "Paid · premium", 2)
        "4o" in key || "gpt-4" in key ->
            CatalogEntry(id, title, "Flagship · paid", "Paid · standard", 1)
        else ->
            CatalogEntry(id, title, "Other OpenAI", "See OpenAI pricing", 3)
    }

    data class AutoConfigResult(
        val host: String,
        val models: List<String>,
        val selectedModel: String,
        val message: String,
    )

    /** In-memory LRU response cache for 0ms instant reload on revisiting game recaps, scouting, and storylines. */
    object Cache {
        private const val MAX_ENTRIES = 64
        private val entries = LinkedHashMap<String, Any>(MAX_ENTRIES, 0.75f, true)

        @Synchronized
        fun <T> get(key: String): T? {
            @Suppress("UNCHECKED_CAST")
            return entries[key] as? T
        }

        @Synchronized
        fun put(key: String, value: Any) {
            if (!cacheable(value)) return
            if (entries.size >= MAX_ENTRIES) {
                val oldest = entries.keys.firstOrNull()
                if (oldest != null) entries.remove(oldest)
            }
            entries[key] = value
        }

        @Synchronized
        fun clear() {
            entries.clear()
        }

        @Synchronized
        fun size(): Int = entries.size

        private fun cacheable(value: Any?): Boolean = when (value) {
            is List<*> -> value.isNotEmpty() && value.all { cacheable(it) }
            is AiText -> value.isAi
            is PressQuestion -> value.isAi
            is Post -> value.isAi
            is News -> value.isAiGenerated
            is Story -> value.isAiGenerated
            is Brief -> value.isAi
            is PostgameReport -> value.isAi
            is ScoutingKey -> value.isAi
            is TradeAnalysis -> value.isAi
            is ProspectReport -> value.isAi
            is InjuryAnalysis -> value.isAi
            is AwardSpeech -> value.isAi
            is HalftimeSpeech -> value.isAi
            is TrainingCampReport -> value.isAi
            is PlayerInterview -> value.isAi
            is Journalism.Package -> value.isAi
            is CoachCareers.Package -> value.isAi
            is HistoricMoments.Package -> value.isAi
            is PlayerStorylines.Package -> value.isAi
            is DraftUniverse.Package -> value.isAi
            is HistoricalRivalries.Package -> value.isAi
            is UniversityDevelopment.Package -> value.isAi
            else -> true
        }
    }

    /** Optimal thread count for ARM mobile big.LITTLE processors (e.g. 4 performance cores). */
    fun defaultOptimalThreads(): Int {
        val cores = Runtime.getRuntime().availableProcessors()
        return when {
            cores >= 8 -> 4 // Target performance core cluster on Snapdragon / Dimensity / Tensor
            cores in 5..7 -> 4
            cores in 3..4 -> cores
            else -> maxOf(2, cores)
        }
    }

    /** Normalizes a host string to a valid HTTP URL, defaulting to local phone host localhost:11434. */
    fun cleanHost(raw: String): String {
        val trimmed = raw.trim().removeSuffix("/")
        return when {
            trimmed.isEmpty() -> "http://localhost:11434"
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            else -> "http://$trimmed"
        }
    }

    /** Test connection to Ollama server at [host] and return available model names. */
    suspend fun testConnection(host: String, timeoutMs: Int = 4000): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL("${cleanHost(host)}/api/tags")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                setRequestProperty("Accept", "application/json")
            }
            val code = conn.responseCode
            if (code in 200..299) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val root = json.parseToJsonElement(body).jsonObject
                val models = root["models"]?.jsonArray?.mapNotNull {
                    it.jsonObject["name"]?.jsonPrimitive?.content
                } ?: emptyList()
                models
            } else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
                throw Exception("HTTP $code: $err")
            }
        }
    }

    /**
     * Automatically probes localhost:11434 (on phone), 127.0.0.1:11434, 10.0.2.2:11434 (emulator),
     * and custom host, detects the active server, and selects the best installed model.
     */
    suspend fun autoConfigure(currentHost: String = ""): Result<AutoConfigResult> = withContext(Dispatchers.IO) {
        val candidates = listOf(
            currentHost.trim(),
            "http://localhost:11434",
            "http://127.0.0.1:11434",
            "http://10.0.2.2:11434",
        ).filter { it.isNotBlank() }.distinct()

        for (candidate in candidates) {
            val testRes = testConnection(candidate, timeoutMs = 2500)
            if (testRes.isSuccess) {
                val models = testRes.getOrDefault(emptyList())
                val chosen = pickBestModel("ollama", models)
                val clean = cleanHost(candidate)
                val msg = if (models.isNotEmpty()) {
                    "Connected to $clean! Using '$chosen' (${models.size} models installed)."
                } else {
                    "Connected to $clean! No models downloaded yet on your device. Choose a model below to download."
                }
                return@withContext Result.success(AutoConfigResult(clean, models, chosen, msg))
            }
        }
        Result.failure(Exception("Could not find an active Ollama instance on localhost:11434, 127.0.0.1:11434, or 10.0.2.2:11434. Start Ollama in Termux or on your computer."))
    }

    /**
     * Pulls / downloads a model via Ollama's /api/pull endpoint with streaming progress.
     */
    suspend fun pullModel(
        host: String,
        modelName: String,
        onProgress: (status: String, progress: Float, details: String) -> Unit,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL("${cleanHost(host)}/api/pull")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10000
                readTimeout = 0 // Stream download over time
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
            }

            val payload = "{\"name\":${jsonString(modelName)},\"stream\":true}"
            conn.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(payload) }

            val code = conn.responseCode
            if (code in 200..299) {
                conn.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                    for (line in lines) {
                        if (line.isBlank()) continue
                        val root = json.parseToJsonElement(line).jsonObject
                        val status = root["status"]?.jsonPrimitive?.content.orEmpty()
                        val total = root["total"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
                        val completed = root["completed"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
                        val progress = if (total > 0L) (completed.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
                        val details = if (total > 0L) {
                            val completedMb = completed / (1024 * 1024)
                            val totalMb = total / (1024 * 1024)
                            "$completedMb MB / $totalMb MB (${(progress * 100).toInt()}%)"
                        } else status
                        onProgress(status, progress, details)
                        if (status.equals("success", ignoreCase = true)) break
                    }
                }
            } else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
                throw Exception("HTTP $code: $err")
            }
        }
    }

    internal fun googleTextModels(body: String): List<String> = json.parseToJsonElement(body).jsonObject["models"]?.jsonArray?.mapNotNull {
        val model = it.jsonObject
        val name = model["name"]?.jsonPrimitive?.content?.removePrefix("models/") ?: return@mapNotNull null
        name.takeIf { it.contains("gemini") && model["supportedGenerationMethods"]?.jsonArray?.any { it.jsonPrimitive.content == "generateContent" } == true }
    }.orEmpty()

    /** Test connection to Google Gemini API using [apiKey]. */
    suspend fun testGoogleConnection(apiKey: String = "", email: String = ""): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val key = apiKey.trim()
            if (key.isNotEmpty()) {
                val url = URL("https://generativelanguage.googleapis.com/v1beta/models?key=$key")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8000
                    readTimeout = 8000
                    setRequestProperty("Accept", "application/json")
                }
                val code = conn.responseCode
                if (code in 200..299) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    googleTextModels(body).ifEmpty { throw IllegalStateException("Provider lists no compatible text models.") }
                } else {
                    val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
                    val errMsg = runCatching {
                        json.parseToJsonElement(err).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
                    }.getOrNull() ?: err
                    throw Exception("Google AI Error ($code): $errMsg")
                }
            } else {
                throw Exception("Gemini requires an API key. Email/password sign-in is not supported.")
            }
        }
    }

    /** Test connection to OpenAI ChatGPT API using [apiKey] and optional [customEndpoint]. */
    suspend fun testOpenAiConnection(apiKey: String = "", customEndpoint: String = "", email: String = ""): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val key = apiKey.trim()
            if (key.isNotEmpty()) {
                val raw = customEndpoint.trim().ifEmpty { "https://api.openai.com/v1" }.removeSuffix("/chat/completions")
                val base = if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "https://$raw"
                val url = URL("$base/models")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8000
                    readTimeout = 8000
                    setRequestProperty("Authorization", "Bearer $key")
                    setRequestProperty("Accept", "application/json")
                }
                val code = conn.responseCode
                if (code in 200..299) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val root = json.parseToJsonElement(body).jsonObject
                    val models = root["data"]?.jsonArray?.mapNotNull {
                        it.jsonObject["id"]?.jsonPrimitive?.content
                    }?.filter { it.startsWith("gpt") }.orEmpty()
                    models.ifEmpty { throw IllegalStateException("Provider lists no compatible text models.") }
                } else {
                    val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
                    val errMsg = runCatching {
                        json.parseToJsonElement(err).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
                    }.getOrNull() ?: err
                    throw Exception("ChatGPT Error ($code): $errMsg")
                }
            } else {
                throw Exception("OpenAI requires an API key. Email/password sign-in is not supported.")
            }
        }
    }


    /** Generate content using Google Gemini REST API or Account Cloud Session. */
    suspend fun generateGoogle(
        config: OllamaConfig,
        prompt: String,
        system: String,
        temperature: Double,
        numPredict: Int,
        onToken: ((String) -> Unit)? = null,
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!config.isGoogleSignedIn) {
            return@withContext Result.failure(Exception("Gemini requires an API key. Email/password sign-in is not supported."))
        }
        val apiKey = config.googleApiKey.trim()
        if (apiKey.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("No Google model credential; use game-written coverage."))
        }
        runCatching {
            val candidates = googleGenerateCandidates(config)
            var lastErr: Exception? = null
            var text: String? = null
            var used: String? = null

            for (cand in candidates) {
                try {
                    val content = retryTransient {
                        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$cand:generateContent?key=$apiKey")
                        val conn = (url.openConnection() as HttpURLConnection).apply {
                            requestMethod = "POST"
                            connectTimeout = 12000
                            readTimeout = 90000
                            doOutput = true
                            setRequestProperty("x-goog-api-key", apiKey)
                            if (apiKey.startsWith("ya29.") || apiKey.startsWith("Bearer ")) {
                                setRequestProperty("Authorization", if (apiKey.startsWith("Bearer ")) apiKey else "Bearer $apiKey")
                            }
                            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                            setRequestProperty("Accept", "application/json")
                        }

                        val payload = buildString {
                            append("{\"contents\":[{\"parts\":[{\"text\":").append(jsonString(prompt)).append("}]}]")
                            if (system.isNotBlank()) {
                                append(",\"systemInstruction\":{\"parts\":[{\"text\":").append(jsonString(system)).append("}]}")
                            }
                            append(",\"generationConfig\":{\"temperature\":$temperature,\"maxOutputTokens\":$numPredict}}")
                        }

                        conn.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(payload) }

                        val code = conn.responseCode
                        if (code in 200..299) {
                            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                            googleResponse(body)
                        } else {
                            val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
                            val errMsg = runCatching {
                                json.parseToJsonElement(err).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
                            }.getOrNull() ?: err
                            throw Exception("Google AI Error ($code): $errMsg")
                        }
                    }
                    if (content.isNotEmpty()) {
                        onToken?.invoke(content)
                        text = content
                        used = cand
                        break
                    }
                } catch (e: Exception) {
                    lastErr = e
                    when {
                        isModelAccessError(e.message) -> rememberFailedModel(cand)
                        isTransientProviderError(e.message) -> { /* already retried; try next model */ }
                        else -> throw e // auth / hard failures
                    }
                }
            }
            if (text != null && used != null) {
                forgetFailedModel(used)
                if ((config.autoPickModel || isRetiredGoogleModel(config.googleModel) || config.googleModel.isBlank() || isFailedModel(config.googleModel)) &&
                    !used.equals(config.googleModel, true)
                ) {
                    config.googleModel = used
                }
            }
            text ?: throw (lastErr ?: Exception("Google AI request failed"))
        }
    }

    /** Generate content using OpenAI / ChatGPT REST API or Account Cloud Session. */
    suspend fun generateOpenAi(
        config: OllamaConfig,
        prompt: String,
        system: String,
        temperature: Double,
        numPredict: Int,
        onToken: ((String) -> Unit)? = null,
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!config.isOpenAiSignedIn) {
            return@withContext Result.failure(Exception("OpenAI requires an API key. Email/password sign-in is not supported."))
        }
        val apiKey = config.openAiApiKey.trim()
        if (apiKey.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("No ChatGPT model credential; use game-written coverage."))
        }
        runCatching {
            val candidates = openAiGenerateCandidates(config)
            val rawEndpoint = config.customEndpoint.trim().ifEmpty { "https://api.openai.com/v1/chat/completions" }
            val endpoint = if (rawEndpoint.startsWith("http://") || rawEndpoint.startsWith("https://")) rawEndpoint else "https://$rawEndpoint"
            var lastErr: Exception? = null
            var text: String? = null
            var used: String? = null
            for (model in candidates) {
                try {
                    val content = retryTransient {
                        val url = URL(endpoint)
                        val conn = (url.openConnection() as HttpURLConnection).apply {
                            requestMethod = "POST"
                            connectTimeout = 12000
                            readTimeout = 90000
                            doOutput = true
                            setRequestProperty("Authorization", "Bearer $apiKey")
                            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                            setRequestProperty("Accept", "application/json")
                        }

                        val payload = buildString {
                            append("{\"model\":").append(jsonString(model))
                            append(",\"messages\":[")
                            append("{\"role\":\"system\",\"content\":").append(jsonString(system)).append("},")
                            append("{\"role\":\"user\",\"content\":").append(jsonString(prompt)).append("}")
                            append("],\"temperature\":$temperature,\"max_tokens\":$numPredict}")
                        }

                        conn.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(payload) }

                        val code = conn.responseCode
                        if (code in 200..299) {
                            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                            openAiResponse(body).also { if (it.isEmpty()) throw Exception("Empty response from ChatGPT") }
                        } else {
                            val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
                            val errMsg = runCatching {
                                json.parseToJsonElement(err).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
                            }.getOrNull() ?: err
                            throw Exception("ChatGPT Error ($code): $errMsg")
                        }
                    }
                    onToken?.invoke(content)
                    text = content
                    used = model
                    break
                } catch (e: Exception) {
                    lastErr = e
                    when {
                        isModelAccessError(e.message) -> rememberFailedModel(model)
                        isTransientProviderError(e.message) -> { /* already retried; try next model */ }
                        else -> throw e
                    }
                }
            }
            if (text != null && used != null) {
                forgetFailedModel(used)
                if ((config.autoPickModel || config.openAiModel.isBlank() || isFailedModel(config.openAiModel)) &&
                    !used.equals(config.openAiModel, true)
                ) {
                    config.openAiModel = used
                }
            }
            text ?: throw (lastErr ?: Exception("ChatGPT request failed"))
        }
    }


    /** Unified generate dispatcher across all configured AI providers (Google Gemini, OpenAI ChatGPT, Local Ollama). */
    suspend fun generate(
        config: OllamaConfig,
        prompt: String,
        system: String = "You are a professional basketball beat writer and columnist. Write engaging, realistic sports newspaper journalism in 2-4 clean paragraphs. Never include markdown headers, title prefixes, or conversational meta-commentary.",
        temperature: Double = 0.7,
        numPredict: Int = 350,
        onToken: ((String) -> Unit)? = null,
    ): Result<String> = withContext(Dispatchers.IO) {
        generateGate.withLock {
            when (config.provider.lowercase()) {
                "google" -> generateGoogle(config, prompt, system, temperature, numPredict, onToken)
                "openai" -> generateOpenAi(config, prompt, system, temperature, numPredict, onToken)
                else -> generateOllama(config, prompt, system, temperature, numPredict, onToken)
            }
        }
    }

    /** Low-level generate call using Ollama's /api/generate endpoint with streaming to prevent socket timeouts. */
    suspend fun generateOllama(
        config: OllamaConfig,
        prompt: String,
        system: String = "You are a professional basketball beat writer and columnist. Write engaging, realistic sports newspaper journalism in 2-4 clean paragraphs. Never include markdown headers, title prefixes, or conversational meta-commentary.",
        temperature: Double = 0.7,
        numPredict: Int = 350,
        onToken: ((String) -> Unit)? = null,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL("${cleanHost(config.host)}/api/generate")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10000
                readTimeout = 120000 // 2 minutes: allows on-device mobile models to load into RAM and output tokens
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
            }

            val threads = if (config.numThreads > 0) config.numThreads else defaultOptimalThreads()
            val needed = (prompt.length + system.length) / 3 + numPredict + 256
            require(needed <= 8192) { "The fact sheet exceeds the model context budget. Use game-written coverage for this request." }
            val ctx = maxOf(config.contextSize, needed).coerceIn(2048, 8192)
            val keepAlive = "${config.keepAliveMinutes.coerceIn(1, 180)}m"
            val effectiveNumPredict = numPredict.coerceIn(128, 4096)

            val payload = buildString {
                append("{\"model\":").append(jsonString(config.model.ifBlank { "llama3.2:1b" }))
                append(",\"prompt\":").append(jsonString(prompt))
                append(",\"system\":").append(jsonString(system))
                append(",\"stream\":true")
                append(",\"keep_alive\":").append(jsonString(keepAlive))
                append(",\"options\":{")
                append("\"temperature\":$temperature")
                append(",\"num_predict\":$effectiveNumPredict")
                append(",\"num_ctx\":$ctx")
                append(",\"num_thread\":$threads")
                append(",\"top_k\":40")
                append(",\"top_p\":0.9")
                append("}}")
            }

            conn.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(payload) }

            val code = conn.responseCode
            if (code in 200..299) {
                val sb = StringBuilder()
                var completed = false
                conn.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                    for (line in lines) {
                        if (line.isBlank()) continue
                        val root = json.parseToJsonElement(line).jsonObject
                        val token = root["response"]?.jsonPrimitive?.content.orEmpty()
                        if (token.isNotEmpty()) {
                            sb.append(token)
                            onToken?.invoke(token)
                        }
                        root["error"]?.jsonPrimitive?.content?.let { throw IllegalStateException("Ollama generation failed") }
                        if (root["done"]?.jsonPrimitive?.content == "true") {
                            check(root["done_reason"]?.jsonPrimitive?.content != "length") { "Model output reached its token limit" }
                            completed = true
                            break
                        }
                    }
                }
                val responseText = sb.toString().trim()
                check(completed) { "Incomplete model stream: missing completion marker" }
                if (responseText.isEmpty()) throw Exception("Empty response from Ollama")
                responseText
            } else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
                throw Exception("HTTP $code: $err")
            }
        }
    }

    private fun jsonString(s: String): String =
        "\"" + s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t") + "\""

    internal fun googleResponse(body: String): String {
        val candidate = json.parseToJsonElement(body).jsonObject["candidates"]?.jsonArray?.firstOrNull()?.jsonObject ?: error("Empty response from Gemini")
        val reason = candidate["finishReason"]?.jsonPrimitive?.content
        // MAX_TOKENS is truncated but often still usable; only reject true safety blocks / empty.
        check(reason == null || reason == "STOP" || reason == "MAX_TOKENS") {
            "Model response was incomplete or filtered ($reason)"
        }
        return candidate["content"]?.jsonObject?.get("parts")?.jsonArray?.mapNotNull { part ->
            part.jsonObject.takeUnless { it["thought"]?.jsonPrimitive?.content == "true" }?.get("text")?.jsonPrimitive?.content
        }?.joinToString("").orEmpty().trim().also { check(it.isNotEmpty()) { "Empty response from Gemini" } }
    }

    internal fun openAiResponse(body: String): String {
        val choice = json.parseToJsonElement(body).jsonObject["choices"]?.jsonArray?.firstOrNull()?.jsonObject ?: error("Empty response from ChatGPT")
        val reason = choice["finish_reason"]?.jsonPrimitive?.content
        check(reason == null || reason == "stop" || reason == "length") {
            "Model response was incomplete or filtered ($reason)"
        }
        return choice["message"]?.jsonObject?.get("content")?.jsonPrimitive?.content.orEmpty().trim().also { check(it.isNotEmpty()) { "Empty response from ChatGPT" } }
    }

    /** Generate or rewrite a complete newspaper article for a [News] item. */
    private suspend fun groundedGenerate(
        config: OllamaConfig,
        lg: League,
        prompt: String,
        system: String = "You are a careful basketball beat writer.",
        numPredict: Int = 350,
        onToken: ((String) -> Unit)? = null,
        extraFacts: String = "",
        extraPlayers: List<Player> = emptyList(),
        preview: Boolean = false,
        upcoming: Boolean = false,
        validateDraft: (String) -> List<String> = { emptyList() },
        articleCopy: String = "",
        eventGame: Game? = null,
    ): Result<AiText> {
        val wantsArticle = articleCopy.isNotBlank() || prompt.contains("BODY:") || prompt.contains("STORY:") || prompt.contains("SCOOP 1:") || prompt.contains("RUMOR 1:")
        val subject = LeagueLists.teams(lg).filter { prompt.contains(it.name) || prompt.contains(it.school) }.minByOrNull { listOf(prompt.indexOf(it.name), prompt.indexOf(it.school)).filter { it >= 0 }.minOrNull() ?: Int.MAX_VALUE } ?: lg.user
        val fallbackCopy = articleCopy.ifBlank { if (wantsArticle) ArticleWriting.news(lg, News(lg.season, lg.day, "${subject.name}: the latest outlook", teamId = subject.id)) else "" }
        // Game/series reports already carry RESULT/BOX/SERIES in the sheet; skip duplicating the
        // full game-written article into EVENT FACTS so local models stay under the 8192 budget.
        val articleExtra = when {
            !wantsArticle -> ""
            eventGame != null -> ""
            else -> "\nARTICLE CONTEXT:\n$fallbackCopy"
        }
        val facts = AiFacts.build(lg, prompt, extraFacts + articleExtra, extraPlayers, preview, upcoming, eventGame)
        val requestId = AiDiagnostics.begin(config, if (prompt.contains("KEY 1:")) "Scouting report" else if (prompt.contains("KICKER:")) "Preview stories" else "Game report")
        var providerError: Throwable? = null
        var gotResponse = false
        val longForm = wantsArticle
        val writingPrompt = prompt + if (longForm) "\n${ArticleWriting.instruction(config)}" else ""
        val result = AiGrounding.generate(facts, writingPrompt, { raw -> validateDraft(raw) + if (longForm) ArticleWriting.errors(raw, config) else emptyList() }) { grounded ->
            generate(config, grounded, system = "$system ${AiGrounding.instructions}", numPredict = if (longForm) ArticleWriting.budget(config) * (if (prompt.contains("Format each storyline as:") || prompt.contains("SCOOP 1:") || prompt.contains("RUMOR 1:")) 3 else 1) else numPredict, onToken = onToken).also {
                gotResponse = it.isSuccess
                if (it.isFailure) providerError = it.exceptionOrNull()
            }
        }
        val reason = if (gotResponse) "Fact check failed: " + result.exceptionOrNull()?.message.orEmpty().substringAfter('(').substringBefore(").") else AiDiagnostics.networkReason(providerError, config)
        AiDiagnostics.finish(requestId, if (result.isSuccess) "success" else if (gotResponse) "validation failure" else "network error", if (result.isFailure) reason else "Verified against game facts")
        val draft = result.fold({ AiText(it, true, config.fullModelDisplayName) }, { return runCatching { AiTemplates.create(facts, prompt, fallbackCopy).copy(fallbackReason = reason) }.onSuccess { onToken?.invoke(it.text) } })
        return Result.success(draft)
    }

    suspend fun generateArticle(
        config: OllamaConfig,
        lg: League,
        news: News,
        angle: String = news.angle.ifEmpty { config.defaultStoryAngle },
    ): Result<AiText> {
        return try {
            val teams = LeagueLists.teams(lg)
            val team = teams.getOrNull(news.teamId)
            val player = lg.playerOrNull(news.pid)
            val game = if (news.gameId >= 0) LeagueLists.games(lg).firstOrNull { it.id == news.gameId } else null
            val opp = if (game != null && team != null) teams.getOrNull(if (game.home == team.id) game.away else game.home) else null

            val seriesBits = game?.let { ArticleWriting.seriesFacts(lg, it) }.orEmpty()
            val prompt = buildString {
                append("Write a complete sports newspaper article for the following basketball news item:\n")
                append("Headline: ${news.text}\n")
                append("Outlet / Publication: ${news.source.ifEmpty { "Courtside Report" }}\n")
                append("Season: ${news.season}, Day: ${news.day}\n")
                append("Angle: ${angle.ifEmpty { "balanced" }}\n")
                if (news.kind == "series") append("Story type: playoff series clincher — lead with the series result, round, and title implications; use the clincher box only as support.\n")
                if (team != null) append("Team: ${if (lg.pro) team.name else team.school} (${team.w}-${team.l})\n")
                if (opp != null) append("Opponent: ${if (lg.pro) opp.name else opp.school} (${opp.record})\n")
                if (game != null) {
                    append("Game Score: ${if (game.played) "${game.hs}-${game.aws}" else "Upcoming"}\n")
                    if (game.played) {
                        val margin = kotlin.math.abs(game.hs - game.aws)
                        append("Clutch context: ${if (margin <= 3) "Down-to-the-wire one possession finish" else if (margin >= 20) "Decisive blowout" else "Competitive double-digit battle"}\n")
                    }
                }
                // Series ledger lives in the fact sheet (and EVENT FACTS); keep the writing prompt short.
                if (player != null) append("Featured Player: ${player.name} (${player.posLabel}, averaging ${"%.1f".format(player.stats.ppg)} ppg, ${"%.1f".format(player.stats.rpg)} rpg)\n")
                val angleInstruction = when {
                    news.kind == "series" -> "Stay series-aware: cite the series length, series score, and round from the fact sheet. Do not invent prior game scores."
                    angle == "tactical" -> "Explain the supplied statistical strengths and weaknesses; do not invent film, possessions or lineup efficiency."
                    angle == "beat" -> "Write with a hometown voice about the actual stakes and roster, without inventing crowd or locker room reactions."
                    angle == "skeptic" -> "Question sustainability using supplied results and probabilities; do not invent turnover or shooting problems."
                    angle == "dual" -> "Discuss both teams using their actual records, available players and result. No invented quotations."
                    else -> "Use the supplied event and statistical context; no invented quotations or play-by-play."
                }
                append("\n${ArticleWriting.instruction(config)} $angleInstruction Do not write a title or headline, just the article body paragraphs separated by blank lines.")
            }

            val copy = ArticleWriting.news(lg, news)
            groundedGenerate(
                config, lg, prompt, articleCopy = copy, eventGame = game,
                extraPlayers = game?.box?.let { (it.home.sortedByDescending { it.s.pts }.take(3) + it.away.sortedByDescending { it.s.pts }.take(3)).mapNotNull { lg.playerOrNull(it.pid) } }.orEmpty(),
                extraFacts = "Event: ${news.text}; season ${news.season}, day ${news.day}; kind ${news.kind}\n${game?.let { ArticleWriting.boxFacts(it) }.orEmpty()}\n$seriesBits",
            )
        } catch (_: java.util.ConcurrentModificationException) {
            val body = try {
                ArticleWriting.news(lg, news)
            } catch (_: java.util.ConcurrentModificationException) {
                news.body.ifBlank { news.text }
            }
            Result.success(
                AiText(
                    body,
                    isAi = false,
                    fallbackReason = "Desk raced the sim — used game-written article.",
                ),
            )
        }
    }

    /** Generates an epic coronation newspaper feature for the National Champion or NBA Finals winner. */
    suspend fun generateChampionshipStory(
        config: OllamaConfig,
        lg: League,
        champion: Team,
        runnerUp: Team,
        finalsMvp: Player?,
        seriesOrScore: String,
    ): Result<News> {
        val coach = lg.coachOf(champion)
        val mvpName = finalsMvp?.name ?: "The Finals MVP"
        val mvpStats = finalsMvp?.let { "${it.posLabel}, averaging ${"%.1f".format(it.stats.ppg)} ppg, ${"%.1f".format(it.stats.rpg)} rpg" } ?: ""

        val prompt = buildString {
            append("CHAMPIONSHIP_CELEBRATION: Complete championship coronation newspaper story\n")
            append("League: ${if (lg.pro) "NBA" else "NCAA College Basketball"}\n")
            append("Champion: ${champion.name} (${champion.w}-${champion.l})\n")
            append("Runner-Up: ${runnerUp.name} (${runnerUp.w}-${runnerUp.l})\n")
            append("Finals Outcome: $seriesOrScore\n")
            append("Head Coach: ${coach.name} (${coach.titles} titles)\n")
            if (finalsMvp != null) append("Finals MVP / MOP: $mvpName ($mvpStats)\n")
            append("\nFormat your response EXACTLY as:\n")
            append("HEADLINE: <Epic championship headline>\n")
            append("OUTLET: <Publication, e.g. Championship Chronicle or The Daily Dribble>\n")
            append("BODY:\n<3 paragraphs about the recorded championship outcome, supplied MVP production and team history. No invented celebrations, quotes or parade plans.>")
        }

        return groundedGenerate(config, lg, prompt, numPredict = 360, extraFacts = "Champion ${champion.name}; runner-up ${runnerUp.name}; finals outcome $seriesOrScore${finalsMvp?.let { "; Finals MVP ${it.name}" }.orEmpty()}", extraPlayers = listOfNotNull(finalsMvp)).map { draft ->
            val text = draft.text
            val lines = text.lines()
            var headline = ""
            var outlet = "Championship Chronicle"
            val bodyLines = mutableListOf<String>()
            var readingBody = false

            for (l in lines) {
                when {
                    l.startsWith("HEADLINE:", ignoreCase = true) -> headline = l.substringAfter(":").trim()
                    l.startsWith("OUTLET:", ignoreCase = true) -> outlet = l.substringAfter(":").trim()
                    l.startsWith("BODY:", ignoreCase = true) -> { readingBody = true; l.substringAfter(':').trim().takeIf { it.isNotEmpty() }?.let { bodyLines += it } }
                    readingBody -> bodyLines += l
                    headline.isEmpty() && l.isNotBlank() && !l.startsWith("OUTLET", true) -> headline = l.trim()
                }
            }

            val finalBody = if (bodyLines.isNotEmpty()) bodyLines.joinToString("\n").trim() else text.trim()
            val finalHead = if (headline.isNotEmpty()) headline else "Champions! ${champion.name} reach the mountaintop"

            News(
                season = lg.season,
                day = lg.day,
                text = finalHead,
                mine = champion.id == lg.userTeamId,
                kind = "moment",
                body = finalBody,
                source = draft.source(outlet),
                teamId = champion.id,
                pid = finalsMvp?.id ?: -1,
                aiLabeled = draft.isAi, aiFallbackReason = draft.fallbackReason,
                approved = false,
            )
        }
    }

    /** Generates a personalized recruiting pitch letter from Coach to a top recruit targeting their key priority. */
    suspend fun generateRecruitPitchLetter(
        config: OllamaConfig,
        lg: League,
        r: Recruit,
    ): Result<AiText> {
        val u = lg.user
        val coachName = if (lg.userCoachId >= 0) lg.coaches.firstOrNull { it.id == lg.userCoachId }?.name ?: "Head Coach" else "Head Coach"
        val p = r.p

        val prompt = buildString {
            append("RECRUIT_PITCH: Official College Basketball Recruiting Pitch Letter\n")
            append("Recruit: ${p.name} (${p.stars}-star ${p.posLabel})\n")
            append("Hometown: ${p.hometown} (${p.highSchool})\n")
            append("Recruit Priority: ${r.pref.label} — ${r.pref.blurb}\n")
            append("School: ${u.school} (${u.w}-${u.l}), Conference: ${lg.conf(u.confId).name}, Prestige: ${u.prestige}/100\n")
            append("Head Coach: $coachName\n")
            append("\nWrite a 3-paragraph persuasive personal recruiting letter from Coach $coachName to ${p.name}. Directly address his priority of '${r.pref.label}'. Explain why our system, culture, and track record will prepare him for the NBA while competing for national championships. End with a coach sign-off.")
        }

        return groundedGenerate(config, lg, prompt, numPredict = 320, extraPlayers = listOf(p), extraFacts = "Recruit ${p.name}: ${p.stars} stars; hometown ${p.hometown}; school ${p.highSchool}; priority ${r.pref.label}: ${r.pref.blurb}")
    }

    /** Generates a pregame rivalry preview building maximum hype and bad blood before a showdown. */
    suspend fun generatePregameRivalryPreview(
        config: OllamaConfig,
        lg: League,
        g: Game,
    ): Result<News> {
        val u = lg.user
        val opp = lg.team(g.opp(u.id))
        val coachU = if (lg.userCoachId >= 0) lg.coaches.firstOrNull { it.id == lg.userCoachId }?.name ?: "Head Coach" else "Head Coach"
        val coachOpp = lg.coachOf(opp)
        val rivalry = lg.rivalry(g)

        val prompt = buildString {
            append("PREGAME_RIVALRY: High-Stakes Rivalry Showdown Preview\n")
            append("Home Team: ${if (g.neutral) "${u.school} (Neutral)" else if (g.home == u.id) "${u.school} (Home, ${u.record})" else "${opp.school} (Home, ${opp.record})"}\n")
            append("Away Team: ${if (g.neutral) "${opp.school} (Neutral)" else if (g.home == u.id) "${opp.school} (Away, ${opp.record})" else "${u.school} (Away, ${u.record})"}\n")
            append("Rivalry: ${rivalry?.name ?: "Conference Showdown"}\n")
            append("Coaches: Coach $coachU vs Coach ${coachOpp.name}\n")
            append("\nFormat your response EXACTLY as:\n")
            append("HEADLINE: <Fiery pregame headline>\n")
            append("OUTLET: <Publication, e.g. Courtside Dispatch or The Rivalry Beat>\n")
            append("BODY:\n<3 paragraphs about the actual records, available players and scheduled rivalry. No invented historical incidents, quotes, injuries or result.>")
        }

        return groundedGenerate(config, lg, prompt, numPredict = 320, upcoming = true, extraFacts = "Upcoming day ${g.day}: ${lg.team(g.away).name} at ${lg.team(g.home).name}; rivalry ${rivalry?.name ?: "none recorded"}").map { draft ->
            val text = draft.text
            val lines = text.lines()
            var headline = ""
            var outlet = "Rivalry Beat"
            val bodyLines = mutableListOf<String>()
            var readingBody = false

            for (l in lines) {
                when {
                    l.startsWith("HEADLINE:", ignoreCase = true) -> headline = l.substringAfter(":").trim()
                    l.startsWith("OUTLET:", ignoreCase = true) -> outlet = l.substringAfter(":").trim()
                    l.startsWith("BODY:", ignoreCase = true) -> { readingBody = true; l.substringAfter(':').trim().takeIf { it.isNotEmpty() }?.let { bodyLines += it } }
                    readingBody -> bodyLines += l
                    headline.isEmpty() && l.isNotBlank() && !l.startsWith("OUTLET", true) -> headline = l.trim()
                }
            }

            val finalBody = if (bodyLines.isNotEmpty()) bodyLines.joinToString("\n").trim() else text.trim()
            val finalHead = if (headline.isNotEmpty()) headline else "Rivalry Week: ${u.school} and ${opp.school} collide"

            News(
                season = lg.season,
                day = lg.day,
                text = finalHead,
                mine = true,
                gameId = g.id,
                kind = "rivalry",
                body = finalBody,
                source = draft.source(outlet),
                teamId = u.id,
                aiLabeled = draft.isAi, aiFallbackReason = draft.fallbackReason,
                approved = false,
            )
        }
    }

    /** Generates 3 insider reports regarding impending free agency decisions and star contract demands. */
    suspend fun generateFreeAgencyBuzz(
        config: OllamaConfig,
        lg: League,
    ): Result<List<Brief>> {
        val cacheKey = "fa_buzz_${lg.season}_${lg.day / 7}_${config.model}" + "_facts_${AiFacts.version(lg)}_${config.provider}_${config.activeModelName}"
        if (config.cacheResponses) {
            Cache.get<List<Brief>>(cacheKey)?.let { return Result.success(it) }
        }
        val contenders = lg.teams.sortedByDescending { it.elo }.take(3)
        val rebuilding = lg.teams.sortedByDescending { Payroll.room(lg, it) }.take(3)

        val prompt = buildString {
            append("FREE_AGENCY_BUZZ: Star Free Agency & Trade Demand Intel\n")
            append("Strong current teams (not confirmed buyers): ${contenders.joinToString(", ") { it.name }}\n")
            append("Available cap room in thousands: ${rebuilding.joinToString(", ") { "${it.name}: ${Payroll.room(lg, it)}" }}\n")
            append("Expiring contracts, not confirmed departures: ${lg.players.filter { it.teamId >= 0 && it.contractYears == 1 }.sortedByDescending { it.ovr }.take(5).joinToString { "${it.name} (${lg.team(it.teamId).name})" }}\n")
            append("\nFormat your response EXACTLY as:\n")
            append("SCOOP 1: <Sentence-case headline> | <Conditional analysis of actual roster and expiring contracts>\n")
            append("SCOOP 2: <Sentence-case headline> | <Conditional analysis of actual roster and cap constraints>\n")
            append("SCOOP 3: <Sentence-case headline> | <Conditional analysis; no invented sources, discontent or demands>\n")
        }

        return groundedGenerate(config, lg, prompt, system = "You analyze actual free-agent eligibility; possibilities are not reports or confirmed demands.", numPredict = 240, extraFacts = rebuilding.joinToString { "${it.name} cap room ${Payroll.room(lg, it)} thousand dollars" }).map { draft ->
            val raw = draft.text
            val scoops = ArticleWriting.briefs(raw, "SCOOP").map { Brief(it.first, it.second, draft.isAi, draft.fallbackReason) }
            val finalScoops = scoops
            if (config.cacheResponses) Cache.put(cacheKey, finalScoops)
            finalScoops
        }
    }

    /** Generate a fresh custom story on demand. */
    suspend fun generateCustomStory(config: OllamaConfig, lg: League, topic: String): Result<News> {
        val u = lg.user
        val coachName = if (lg.userCoachId >= 0) lg.coaches.firstOrNull { it.id == lg.userCoachId }?.name ?: "Head Coach" else "Head Coach"
        val top = lg.teams.maxByOrNull { it.elo } ?: u

        val prompt = buildString {
            append("You are a basketball beat reporter. Write a breaking story or feature article about the following topic:\n")
            append("Topic: $topic\n")
            append("League: ${if (lg.pro) "NBA" else "NCAA College Basketball"}\n")
            append("User team: ${u.school} (${u.w}-${u.l}), Coach: $coachName\n")
            append("Top team: ${top.school} (${top.w}-${top.l})\n")
            append("Current season: ${lg.season}, Day: ${lg.day}\n\n")
            append("Format your response EXACTLY as:\n")
            append("HEADLINE: <Catchy newspaper headline>\n")
            append("OUTLET: <Publication name, e.g. Hoops Wire or The Daily Dribble>\n")
            append("BODY:\n<2-3 paragraphs of article text>")
        }

        return groundedGenerate(config, lg, prompt).map { draft ->
            val text = draft.text
            val lines = text.lines()
            var headline = ""
            var outlet = "Hoops Wire"
            val bodyLines = mutableListOf<String>()
            var readingBody = false

            for (l in lines) {
                when {
                    l.startsWith("HEADLINE:", ignoreCase = true) -> headline = l.substringAfter(":").trim()
                    l.startsWith("OUTLET:", ignoreCase = true) -> outlet = l.substringAfter(":").trim()
                    l.startsWith("BODY:", ignoreCase = true) -> { readingBody = true; l.substringAfter(':').trim().takeIf { it.isNotEmpty() }?.let { bodyLines += it } }
                    readingBody -> bodyLines += l
                    headline.isEmpty() && l.isNotBlank() && !l.startsWith("OUTLET", true) -> headline = l.trim()
                }
            }

            val finalBody = if (bodyLines.isNotEmpty()) bodyLines.joinToString("\n").trim() else text.trim()
            val finalHead = if (headline.isNotEmpty()) headline else topic.replaceFirstChar { it.uppercase() }

            News(
                season = lg.season,
                day = lg.day,
                text = finalHead,
                mine = true,
                kind = "article",
                body = finalBody,
                source = draft.source(outlet),
                teamId = u.id,
                aiLabeled = draft.isAi, aiFallbackReason = draft.fallbackReason,
            )
        }
    }

    /** Generate storylines for the season preview. */
    suspend fun generateStorylines(
        config: OllamaConfig,
        lg: League,
        season: Int,
        topTeam: String,
        userTeam: String,
        userTeamId: Int,
        context: String,
    ): Result<List<Story>> {
        val topics = PreviewTopics.select(lg)
        if (topics.isEmpty()) return Result.success(listOf(Story("Outlook", "${lg.user.school} and the season ahead", AiTemplates.previewCopy(lg, lg.user), lg.userTeamId)))
        val prompt = buildString {
            append("Write 3 preseason basketball magazine storylines for the $season issue. Choose different subjects ONLY from these verified topics:\n")
            topics.forEach { appendLine("${it.subject}: ${it.facts}") }
            append("Format each storyline as:\n")
            append("KICKER: <Short 1-3 word tag like 'The Favorite' or 'Hot Seat'>\n")
            append("HEADLINE: <Punchy storyline headline>\n")
            append("BODY: <4-7 paragraphs of evidence-backed preview reporting>\n---\n")
        }

        return groundedGenerate(config, lg, prompt, numPredict = 300, preview = true, extraFacts = topics.joinToString("\n") { "${it.subject}: ${it.facts}" }, validateDraft = { raw ->
            val subjects = raw.split(Regex("(?im)(?=^KICKER:)")).filter { it.contains("HEADLINE:", true) }.map { PreviewTopics.match(lg, topics, it)?.subject }
            if (subjects.size != 3 || subjects.any { it == null } || subjects.distinct().size != 3) listOf("three distinct verified preview subjects required") else emptyList()
        }).mapCatching { draft ->
            if (!draft.isAi) return@mapCatching PreviewTopics.stories(lg, topics).take(3).onEach { it.fallbackReason = draft.fallbackReason }
            val raw = draft.text
            val stories = mutableListOf<Story>()
            var kicker = ""
            var head = ""
            val body = mutableListOf<String>()

            fun flush() {
                if (kicker.isNotEmpty() && head.isNotEmpty()) {
                    val text = if (body.isNotEmpty()) body.joinToString("\n").trim() else "Key storyline to watch for the upcoming season."
                    val subject = PreviewTopics.match(lg, topics, "$head $text")
                    if (subject != null) stories += Story(kicker, head, text, subject.teamId, subject.pid, draft.provider)
                }
                kicker = ""
                head = ""
                body.clear()
            }

            for (line in raw.lines()) {
                val trimmed = line.trim()
                if (trimmed == "---") {
                    flush()
                    continue
                }
                when {
                    trimmed.startsWith("KICKER:", ignoreCase = true) -> {
                        if (kicker.isNotEmpty() && head.isNotEmpty()) flush()
                        kicker = trimmed.substringAfter(":").trim()
                    }
                    trimmed.startsWith("HEADLINE:", ignoreCase = true) -> {
                        head = trimmed.substringAfter(":").trim()
                    }
                    trimmed.startsWith("BODY:", ignoreCase = true) -> {
                        val b = trimmed.substringAfter(":").trim()
                        if (b.isNotEmpty()) body.add(b)
                    }
                    kicker.isNotEmpty() && head.isNotEmpty() -> {
                        body.add(trimmed)
                    }
                }
            }
            flush()

            if (stories.isEmpty()) {
                throw IllegalStateException("No verified subject in the draft; the existing game-written stories were kept.")
            } else stories
        }
    }

    /** Fire-and-forget generation if auto-generate is enabled. */
    fun maybeAutoGenerate(config: OllamaConfig, lg: League, news: News) {
        if (!config.enabled || !config.autoGenerate) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                generateArticle(config, lg, news).onSuccess { it.applyTo(news) }
            } catch (_: java.util.ConcurrentModificationException) {
                // Desk raced the sim — leave the game-written body.
            }
        }
    }

    /** Fire-and-forget living media ecosystem package (breaking + debates + social + polls). */
    fun maybeAutoGenerateMediaEcosystem(config: OllamaConfig, lg: League, force: Boolean = false) {
        if (!config.enabled || !config.mediaEcosystem || !config.autoGenerate) return
        if (!force && (lg.day < 4 || lg.day % 4 != 0)) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                generateMediaEcosystem(config, lg).onSuccess { pack ->
                    if (pack.stories.isNotEmpty()) LeagueLists.appendNews(lg, pack.stories)
                }
            } catch (_: java.util.ConcurrentModificationException) {
                // Media desk raced the sim.
            }
        }
    }

    /** Living media ecosystem desk grounded in [MediaEcosystem.ecosystemBrief]. */
    suspend fun generateMediaEcosystem(
        config: OllamaConfig,
        lg: League,
    ): Result<MediaEcosystem.Package> = withContext(Dispatchers.IO) {
        if (!config.enabled || !config.mediaEcosystem) {
            return@withContext Result.success(MediaEcosystem.fallbackPackage(lg))
        }
        MediaEcosystem.publishSocialBeats(lg)
        val brief = try {
            MediaEcosystem.ecosystemBrief(lg)
        } catch (_: java.util.ConcurrentModificationException) {
            return@withContext Result.success(MediaEcosystem.fallbackPackage(lg).copy(detail = "Desk raced the sim — used game-written media ecosystem."))
        }
        val cacheKey = "media_eco_${lg.season}_${lg.day}_${config.activeModelName}_facts_${AiFacts.version(lg)}_${config.provider}"
        if (config.cacheResponses) {
            Cache.get<MediaEcosystem.Package>(cacheKey)?.let { return@withContext Result.success(it) }
        }
        val prompt = buildString {
            appendLine("You are Hoops Wire Network — a living basketball media desk obsessed with titles and the whole league.")
            appendLine("Using ONLY the ECOSYSTEM BRIEF, write up to six short beats. Prefer banner/title and league-wide angles.")
            appendLine("Do not invent scores, commits, firings, poll voters, or quotes not supported by the brief.")
            appendLine("ECOSYSTEM BRIEF:")
            appendLine(brief)
            appendLine("Format EXACTLY (omit a block if that beat has no facts):")
            appendLine("TITLE_HEADLINE: <defending champ / title drought / banner ledger>")
            appendLine("TITLE_BODY: <2 short paragraphs from title facts>")
            appendLine("BREAKING_HEADLINE: <breaking news from today's real results, framed vs the title picture>")
            appendLine("BREAKING_BODY: <2 short paragraphs>")
            appendLine("ANALYST_HEADLINE: <analysts debating title/tournament chances>")
            appendLine("ANALYST_BODY: <2 short paragraphs citing records/AP/Elo/titles from the brief>")
            appendLine("FAN_HEADLINE: <fans arguing about coaching decisions vs the program's banner standard>")
            appendLine("FAN_BODY: <2 short paragraphs grounded in security/record/titles>")
            appendLine("RECRUIT_HEADLINE: <recruiting insider / commitment rumor (college) or trade buzz (pro)>")
            appendLine("RECRUIT_BODY: <conditional only — no fake commits>")
            appendLine("POLL_HEADLINE: <national poll / league-pulse reaction>")
            appendLine("POLL_BODY: <2 short paragraphs from AP board / league situation>")
        }
        val result = groundedGenerate(
            config, lg, prompt,
            system = "Hoops Wire Network. Titles and league situation first. Facts only from the brief. Speculation must be labeled as rumor.",
            numPredict = 560,
        )
        result.map { draft ->
            fun field(key: String): String {
                val lines = draft.text.lines()
                val start = lines.indexOfFirst { it.trim().startsWith("$key:", ignoreCase = true) }
                if (start < 0) return ""
                val first = lines[start].substringAfter(':').trim()
                val rest = mutableListOf<String>()
                if (first.isNotEmpty()) rest += first
                var i = start + 1
                while (i < lines.size) {
                    val t = lines[i].trim()
                    if (t.contains(':') && t.substringBefore(':').all { it.isUpperCase() || it == '_' } && t.substringBefore(':').length in 4..28) break
                    if (t.isNotEmpty()) rest += t
                    i++
                }
                return rest.joinToString("\n").trim()
            }
            val outlet = draft.source("Hoops Wire Network")
            val stories = mutableListOf<News>()
            fun add(kind: String, headKey: String, bodyKey: String, mine: Boolean = true, teamId: Int = lg.userTeamId) {
                val head = field(headKey)
                val body = field(bodyKey)
                if (head.isBlank() || body.isBlank()) return
                stories += News(
                    lg.season, lg.day, head, mine = mine, kind = kind, body = body, source = outlet,
                    teamId = teamId, aiLabeled = draft.isAi, aiFallbackReason = draft.fallbackReason, approved = false,
                )
            }
            val champId = TitleDefense.champion(lg)?.id ?: lg.userTeamId
            add("column", "TITLE_HEADLINE", "TITLE_BODY", mine = champId == lg.userTeamId, teamId = champId)
            add("article", "BREAKING_HEADLINE", "BREAKING_BODY", mine = false)
            add("column", "ANALYST_HEADLINE", "ANALYST_BODY")
            add("column", "FAN_HEADLINE", "FAN_BODY")
            add("column", "RECRUIT_HEADLINE", "RECRUIT_BODY", mine = false)
            add("column", "POLL_HEADLINE", "POLL_BODY", mine = false)
            val pack = if (stories.size >= 2) MediaEcosystem.Package(stories, draft.isAi, "Network filed ${stories.size} beats + social.")
            else MediaEcosystem.fallbackPackage(lg).copy(detail = "AI thin — used game-written media ecosystem. ${draft.fallbackReason}")
            if (config.cacheResponses && pack.isAi) Cache.put(cacheKey, pack)
            pack
        }.recover { MediaEcosystem.fallbackPackage(lg) }
    }

    /** Fire-and-forget campus / franchise development package. */
    fun maybeAutoGenerateUniversityDevelopment(config: OllamaConfig, lg: League, force: Boolean = false) {
        if (!config.enabled || !config.universityDevelopment || !config.autoGenerate) return
        if (!force && (lg.day < 9 || lg.day % 9 != 0)) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                generateUniversityDevelopment(config, lg).onSuccess { pack ->
                    if (pack.stories.isNotEmpty()) LeagueLists.appendNews(lg, pack.stories)
                }
            } catch (_: java.util.ConcurrentModificationException) {
                // Campus desk raced the sim.
            }
        }
    }

    /** Campus desk: arenas, facilities, recruiting infrastructure, national brand. */
    suspend fun generateUniversityDevelopment(
        config: OllamaConfig,
        lg: League,
    ): Result<UniversityDevelopment.Package> = withContext(Dispatchers.IO) {
        if (!config.enabled || !config.universityDevelopment) {
            return@withContext Result.success(UniversityDevelopment.fallbackPackage(lg))
        }
        val brief = try {
            UniversityDevelopment.campusBrief(lg)
        } catch (_: java.util.ConcurrentModificationException) {
            return@withContext Result.success(UniversityDevelopment.fallbackPackage(lg).copy(detail = "Desk raced the sim — used game-written campus package."))
        }
        val cacheKey = "campus_dev_${lg.season}_${lg.day}_${lg.user.upgrades.size}_${lg.user.cash}_${config.activeModelName}_facts_${AiFacts.version(lg)}"
        if (config.cacheResponses) {
            Cache.get<UniversityDevelopment.Package>(cacheKey)?.let { return@withContext Result.success(it) }
        }
        val prompt = buildString {
            appendLine("You are a national college athletics business / facilities beat writer. Using ONLY the CAMPUS BRIEF, write up to three pieces.")
            appendLine("Do not invent budgets, capacities, or projects not listed.")
            appendLine("CAMPUS BRIEF:")
            appendLine(brief)
            appendLine("Format EXACTLY (omit a block if that topic has no facts):")
            appendLine("ARENA_HEADLINE: <arena / crowd / renovation headline>")
            appendLine("ARENA_BODY: <2 short paragraphs>")
            appendLine("FACILITY_HEADLINE: <practice / training / recruiting infrastructure headline>")
            appendLine("FACILITY_BODY: <2 short paragraphs>")
            appendLine("BRAND_HEADLINE: <national brand / Program Pull headline>")
            appendLine("BRAND_BODY: <2 short paragraphs>")
        }
        val result = groundedGenerate(
            config, lg, prompt,
            system = "University development desk. Facts only from the brief.",
            numPredict = 420,
        )
        result.map { draft ->
            fun field(key: String): String {
                val lines = draft.text.lines()
                val start = lines.indexOfFirst { it.trim().startsWith("$key:", ignoreCase = true) }
                if (start < 0) return ""
                val first = lines[start].substringAfter(':').trim()
                val rest = mutableListOf<String>()
                if (first.isNotEmpty()) rest += first
                var i = start + 1
                while (i < lines.size) {
                    val t = lines[i].trim()
                    if (t.contains(':') && t.substringBefore(':').all { it.isUpperCase() || it == '_' } && t.substringBefore(':').length in 4..28) break
                    if (t.isNotEmpty()) rest += t
                    i++
                }
                return rest.joinToString("\n").trim()
            }
            val outlet = draft.source("Hoops Wire Campus")
            val stories = mutableListOf<News>()
            fun add(headKey: String, bodyKey: String) {
                val head = field(headKey)
                val body = field(bodyKey)
                if (head.isBlank() || body.isBlank()) return
                stories += News(
                    lg.season, lg.day, head, mine = true, kind = "column", body = body, source = outlet,
                    teamId = lg.userTeamId, aiLabeled = draft.isAi, aiFallbackReason = draft.fallbackReason, approved = false,
                )
            }
            add("ARENA_HEADLINE", "ARENA_BODY")
            add("FACILITY_HEADLINE", "FACILITY_BODY")
            add("BRAND_HEADLINE", "BRAND_BODY")
            val pack = if (stories.isNotEmpty()) UniversityDevelopment.Package(stories, draft.isAi, "Campus desk filed ${stories.size} pieces.")
            else UniversityDevelopment.fallbackPackage(lg).copy(detail = "AI thin — used game-written campus desk. ${draft.fallbackReason}")
            if (config.cacheResponses && pack.isAi) Cache.put(cacheKey, pack)
            pack
        }.recover { UniversityDevelopment.fallbackPackage(lg) }
    }

    /** Fire-and-forget historical rivalries package (programs, coaches, fans, players). */
    fun maybeAutoGenerateHistoricalRivalries(config: OllamaConfig, lg: League, force: Boolean = false) {
        if (!config.enabled || !config.historicalRivalries || !config.autoGenerate) return
        if (!force && (lg.day < 6 || lg.day % 6 != 0)) return
        if (lg.rivals.isEmpty() && CoachRivals.rivals(lg).isEmpty()) return
        CoroutineScope(Dispatchers.IO).launch {
            generateHistoricalRivalries(config, lg).onSuccess { pack ->
                if (pack.stories.isNotEmpty()) LeagueLists.appendNews(lg, pack.stories)
            }
        }
    }

    /** Rivalry history desk: series ledgers, coaching grudges, fan pulse, organic rivalries. */
    suspend fun generateHistoricalRivalries(
        config: OllamaConfig,
        lg: League,
    ): Result<HistoricalRivalries.Package> = withContext(Dispatchers.IO) {
        if (!config.enabled || !config.historicalRivalries) {
            return@withContext Result.success(HistoricalRivalries.fallbackPackage(lg))
        }
        val brief = try {
            HistoricalRivalries.historyBrief(lg)
        } catch (_: java.util.ConcurrentModificationException) {
            return@withContext Result.success(HistoricalRivalries.fallbackPackage(lg).copy(detail = "Desk raced the sim — used game-written rivalry package."))
        }
        val cacheKey = "hist_rivals_${lg.season}_${lg.day}_${lg.rivals.size}_${config.activeModelName}_facts_${AiFacts.version(lg)}"
        if (config.cacheResponses) {
            Cache.get<HistoricalRivalries.Package>(cacheKey)?.let { return@withContext Result.success(it) }
        }
        val prompt = buildString {
            appendLine("You are a national basketball historian covering rivalries. Using ONLY the HISTORY BRIEF, write up to three pieces.")
            appendLine("Do not invent series records, quotes, or fights.")
            appendLine("HISTORY BRIEF:")
            appendLine(brief)
            appendLine("Format EXACTLY (omit a block if that topic has no facts):")
            appendLine("PROGRAM_HEADLINE: <program series / accumulated history headline>")
            appendLine("PROGRAM_BODY: <2 short paragraphs>")
            appendLine("COACH_OR_PLAYER_HEADLINE: <coaching grudge or player/fan pulse headline>")
            appendLine("COACH_OR_PLAYER_BODY: <2 short paragraphs>")
            appendLine("ORGANIC_HEADLINE: <emerging rivalry or next chapter headline>")
            appendLine("ORGANIC_BODY: <2 short paragraphs>")
        }
        val result = groundedGenerate(
            config, lg, prompt,
            system = "Historical rivalries desk. Facts only from the brief.",
            numPredict = 420,
        )
        result.map { draft ->
            fun field(key: String): String {
                val lines = draft.text.lines()
                val start = lines.indexOfFirst { it.trim().startsWith("$key:", ignoreCase = true) }
                if (start < 0) return ""
                val first = lines[start].substringAfter(':').trim()
                val rest = mutableListOf<String>()
                if (first.isNotEmpty()) rest += first
                var i = start + 1
                while (i < lines.size) {
                    val t = lines[i].trim()
                    if (t.contains(':') && t.substringBefore(':').all { it.isUpperCase() || it == '_' } && t.substringBefore(':').length in 4..32) break
                    if (t.isNotEmpty()) rest += t
                    i++
                }
                return rest.joinToString("\n").trim()
            }
            val outlet = draft.source("Hoops Wire Rivalries")
            val stories = mutableListOf<News>()
            fun add(headKey: String, bodyKey: String, mine: Boolean = true) {
                val head = field(headKey)
                val body = field(bodyKey)
                if (head.isBlank() || body.isBlank()) return
                stories += News(
                    lg.season, lg.day, head, mine = mine, kind = "column", body = body, source = outlet,
                    teamId = lg.userTeamId, aiLabeled = draft.isAi, aiFallbackReason = draft.fallbackReason, approved = false,
                )
            }
            add("PROGRAM_HEADLINE", "PROGRAM_BODY")
            add("COACH_OR_PLAYER_HEADLINE", "COACH_OR_PLAYER_BODY")
            add("ORGANIC_HEADLINE", "ORGANIC_BODY", mine = false)
            val pack = if (stories.isNotEmpty()) HistoricalRivalries.Package(stories, draft.isAi, "Rivalries desk filed ${stories.size} pieces.")
            else HistoricalRivalries.fallbackPackage(lg).copy(detail = "AI thin — used game-written rivalries desk. ${draft.fallbackReason}")
            if (config.cacheResponses && pack.isAi) Cache.put(cacheKey, pack)
            pack
        }.recover { HistoricalRivalries.fallbackPackage(lg) }
    }

    /** Fire-and-forget draft universe package (stock, combine profiles, surprises, alumni). */
    fun maybeAutoGenerateDraftUniverse(config: OllamaConfig, lg: League) {
        if (!config.enabled || !config.draftUniverse || !config.autoGenerate) return
        val dn = lg.proState?.draftNight
        val draftNightPulse = dn != null && (dn.log.isNotEmpty() || !dn.done)
        if (!draftNightPulse && (lg.day < 12 || lg.day % 8 != 0)) return
        CoroutineScope(Dispatchers.IO).launch {
            generateDraftUniverse(config, lg).onSuccess { pack ->
                if (pack.stories.isNotEmpty()) LeagueLists.appendNews(lg, pack.stories)
            }
        }
    }

    /** Draft desk: prospects, stock, workout profiles, draft-night surprises, pro careers. */
    suspend fun generateDraftUniverse(
        config: OllamaConfig,
        lg: League,
    ): Result<DraftUniverse.Package> = withContext(Dispatchers.IO) {
        if (!config.enabled || !config.draftUniverse) {
            return@withContext Result.success(DraftUniverse.fallbackPackage(lg))
        }
        val brief = try {
            DraftUniverse.eventBrief(lg)
        } catch (_: java.util.ConcurrentModificationException) {
            return@withContext Result.success(DraftUniverse.fallbackPackage(lg).copy(detail = "Desk raced the sim — used game-written draft package."))
        }
        val cacheKey = "draft_universe_${lg.season}_${lg.day}_${lg.proState?.draftNight?.at ?: -1}_${config.activeModelName}_facts_${AiFacts.version(lg)}"
        if (config.cacheResponses) {
            Cache.get<DraftUniverse.Package>(cacheKey)?.let { return@withContext Result.success(it) }
        }
        val prompt = buildString {
            appendLine("You are a national NBA draft insider. Using ONLY the DRAFT BRIEF, write up to three pieces.")
            appendLine("Do not invent combine numbers, medicals, commits, or picks not listed.")
            appendLine("DRAFT BRIEF:")
            appendLine(brief)
            appendLine("Format EXACTLY (omit a block if that topic has no facts):")
            appendLine("STOCK_HEADLINE: <draft stock / board mover headline>")
            appendLine("STOCK_BODY: <2 short paragraphs>")
            appendLine("COMBINE_OR_NIGHT_HEADLINE: <workout profile or draft-night surprise headline>")
            appendLine("COMBINE_OR_NIGHT_BODY: <2 short paragraphs from listed size/tools or logged picks>")
            appendLine("PRO_CAREER_HEADLINE: <former player / where are they now headline>")
            appendLine("PRO_CAREER_BODY: <2 short paragraphs from alumni destinations>")
        }
        val result = groundedGenerate(
            config, lg, prompt,
            system = "NBA draft universe desk. Facts only from the brief.",
            numPredict = 420,
        )
        result.map { draft ->
            fun field(key: String): String {
                val lines = draft.text.lines()
                val start = lines.indexOfFirst { it.trim().startsWith("$key:", ignoreCase = true) }
                if (start < 0) return ""
                val first = lines[start].substringAfter(':').trim()
                val rest = mutableListOf<String>()
                if (first.isNotEmpty()) rest += first
                var i = start + 1
                while (i < lines.size) {
                    val t = lines[i].trim()
                    if (t.contains(':') && t.substringBefore(':').all { it.isUpperCase() || it == '_' } && t.substringBefore(':').length in 4..32) break
                    if (t.isNotEmpty()) rest += t
                    i++
                }
                return rest.joinToString("\n").trim()
            }
            val outlet = draft.source("Hoops Wire Draft")
            val stories = mutableListOf<News>()
            fun add(headKey: String, bodyKey: String, mine: Boolean = false) {
                val head = field(headKey)
                val body = field(bodyKey)
                if (head.isBlank() || body.isBlank()) return
                stories += News(
                    lg.season, lg.day, head, mine = mine, kind = "column", body = body, source = outlet,
                    teamId = lg.userTeamId, aiLabeled = draft.isAi, aiFallbackReason = draft.fallbackReason, approved = false,
                )
            }
            add("STOCK_HEADLINE", "STOCK_BODY", mine = true)
            add("COMBINE_OR_NIGHT_HEADLINE", "COMBINE_OR_NIGHT_BODY")
            add("PRO_CAREER_HEADLINE", "PRO_CAREER_BODY")
            val pack = if (stories.isNotEmpty()) DraftUniverse.Package(stories, draft.isAi, "Draft desk filed ${stories.size} pieces.")
            else DraftUniverse.fallbackPackage(lg).copy(detail = "AI thin — used game-written draft desk. ${draft.fallbackReason}")
            if (config.cacheResponses && pack.isAi) Cache.put(cacheKey, pack)
            pack
        }.recover { DraftUniverse.fallbackPackage(lg) }
    }

    /** Fire-and-forget player storyline features from detected arcs. */
    fun maybeAutoGeneratePlayerStorylines(config: OllamaConfig, lg: League) {
        if (!config.enabled || !config.playerStorylines || !config.autoGenerate) return
        if (lg.day < 8 || lg.day % 7 != 0) return
        val arcs = try {
            PlayerStorylines.detectArcs(lg)
        } catch (_: java.util.ConcurrentModificationException) {
            return
        }
        if (arcs.isEmpty()) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                generatePlayerStorylines(config, lg).onSuccess { pack ->
                    if (pack.stories.isNotEmpty()) LeagueLists.appendNews(lg, pack.stories)
                }
            } catch (_: java.util.ConcurrentModificationException) {
                // Storyline desk raced the sim.
            }
        }
    }

    /** Feature desk: bench-to-star, five-star heat checks, walk-on heroes — facts from [PlayerStorylines.eventBrief]. */
    suspend fun generatePlayerStorylines(
        config: OllamaConfig,
        lg: League,
    ): Result<PlayerStorylines.Package> = withContext(Dispatchers.IO) {
        if (!config.enabled || !config.playerStorylines) {
            return@withContext Result.success(PlayerStorylines.fallbackPackage(lg))
        }
        val brief = try {
            PlayerStorylines.eventBrief(lg)
        } catch (_: java.util.ConcurrentModificationException) {
            return@withContext Result.success(PlayerStorylines.fallbackPackage(lg).copy(detail = "Desk raced the sim — used game-written storyline package."))
        }
        val cacheKey = "player_arcs_${lg.season}_${lg.day}_${config.activeModelName}_facts_${AiFacts.version(lg)}_${config.provider}"
        if (config.cacheResponses) {
            Cache.get<PlayerStorylines.Package>(cacheKey)?.let { return@withContext Result.success(it) }
        }
        val prompt = buildString {
            appendLine("You are a national basketball features writer. Using ONLY the ARC BRIEF, write up to three features.")
            appendLine("Only cover arcs listed. Do not invent stats, awards, or quotes.")
            appendLine("ARC BRIEF:")
            appendLine(brief)
            appendLine("Format EXACTLY (omit a block if that arc kind is absent):")
            appendLine("BENCH_TO_STAR_HEADLINE: <headline>")
            appendLine("BENCH_TO_STAR_BODY: <2 short paragraphs>")
            appendLine("RECRUIT_BUST_HEADLINE: <headline>")
            appendLine("RECRUIT_BUST_BODY: <2 short paragraphs>")
            appendLine("WALKON_HERO_HEADLINE: <headline>")
            appendLine("WALKON_HERO_BODY: <2 short paragraphs>")
        }
        val result = groundedGenerate(
            config, lg, prompt,
            system = "Player storylines desk. Facts only from the brief.",
            numPredict = 420,
        )
        result.map { draft ->
            fun field(key: String): String {
                val lines = draft.text.lines()
                val start = lines.indexOfFirst { it.trim().startsWith("$key:", ignoreCase = true) }
                if (start < 0) return ""
                val first = lines[start].substringAfter(':').trim()
                val rest = mutableListOf<String>()
                if (first.isNotEmpty()) rest += first
                var i = start + 1
                while (i < lines.size) {
                    val t = lines[i].trim()
                    if (t.contains(':') && t.substringBefore(':').all { it.isUpperCase() || it == '_' } && t.substringBefore(':').length in 4..32) break
                    if (t.isNotEmpty()) rest += t
                    i++
                }
                return rest.joinToString("\n").trim()
            }
            val outlet = draft.source("Hoops Wire Features")
            val arcs = PlayerStorylines.detectArcs(lg)
            val stories = mutableListOf<News>()
            fun add(kind: String, headKey: String, bodyKey: String) {
                val head = field(headKey)
                val body = field(bodyKey)
                if (head.isBlank() || body.isBlank()) return
                val arc = arcs.firstOrNull { it.kind == kind }
                val p = arc?.let { lg.playerOrNull(it.pid) }
                stories += News(
                    lg.season, lg.day, head,
                    mine = p?.teamId == lg.userTeamId,
                    kind = "column", body = body, source = outlet,
                    teamId = p?.teamId ?: lg.userTeamId, pid = p?.id ?: -1,
                    aiLabeled = draft.isAi, aiFallbackReason = draft.fallbackReason, approved = false,
                )
            }
            add("BENCH_TO_STAR", "BENCH_TO_STAR_HEADLINE", "BENCH_TO_STAR_BODY")
            add("RECRUIT_BUST", "RECRUIT_BUST_HEADLINE", "RECRUIT_BUST_BODY")
            add("WALKON_HERO", "WALKON_HERO_HEADLINE", "WALKON_HERO_BODY")
            val pack = if (stories.isNotEmpty()) PlayerStorylines.Package(stories, draft.isAi, "Features filed ${stories.size} arcs.")
            else PlayerStorylines.fallbackPackage(lg).copy(detail = "AI thin — used game-written arcs. ${draft.fallbackReason}")
            if (config.cacheResponses && pack.isAi) Cache.put(cacheKey, pack)
            pack
        }.recover { PlayerStorylines.fallbackPackage(lg) }
    }

    /** Fire-and-forget living coaching careers package (hot seats, rivals, legacy). */
    fun maybeAutoGenerateCoachingCareers(config: OllamaConfig, lg: League) {
        if (!config.enabled || !config.livingCoachingCareers || !config.autoGenerate) return
        CoroutineScope(Dispatchers.IO).launch {
            generateLivingCoachingCareers(config, lg).onSuccess { pack ->
                if (pack.stories.isNotEmpty()) LeagueLists.appendNews(lg, pack.stories)
            }
        }
    }

    /**
     * Coaching careers desk: reputation, firings/offers context, rivalries, multi-school legacy.
     * Grounded in [CoachCareers.careerBrief].
     */
    suspend fun generateLivingCoachingCareers(
        config: OllamaConfig,
        lg: League,
    ): Result<CoachCareers.Package> = withContext(Dispatchers.IO) {
        if (!config.enabled || !config.livingCoachingCareers) {
            return@withContext Result.success(CoachCareers.fallbackPackage(lg))
        }
        val brief = try {
            CoachCareers.careerBrief(lg)
        } catch (_: java.util.ConcurrentModificationException) {
            return@withContext Result.success(CoachCareers.fallbackPackage(lg).copy(detail = "Desk raced the sim — used game-written coaching package."))
        }
        val cacheKey = "coach_careers_${lg.season}_${lg.day}_${config.activeModelName}_facts_${AiFacts.version(lg)}_${config.provider}"
        if (config.cacheResponses) {
            Cache.get<CoachCareers.Package>(cacheKey)?.let { return@withContext Result.success(it) }
        }
        val prompt = buildString {
            appendLine("You are a national college/NBA coaching beat writer. Using ONLY the CAREER BRIEF, write three pieces.")
            appendLine("Do not invent firings, hires, quotes, or records.")
            appendLine("CAREER BRIEF:")
            appendLine(brief)
            appendLine("Format EXACTLY:")
            appendLine("REPUTATION_HEADLINE: <job-security / hot-seat headline from real security scores>")
            appendLine("REPUTATION_BODY: <2 short paragraphs>")
            appendLine("CAROUSEL_HEADLINE: <firing, hiring, or job-offer speculation labeled as speculation>")
            appendLine("CAROUSEL_BODY: <conditional analysis from brief only>")
            appendLine("RIVALRY_OR_LEGACY_HEADLINE: <coaching rivalry or multi-school legacy headline>")
            appendLine("RIVALRY_OR_LEGACY_BODY: <facts from head-to-head / prior stops>")
        }
        val result = groundedGenerate(
            config, lg, prompt,
            system = "Coaching careers desk. Facts only from the brief. Label speculation clearly.",
            numPredict = 420,
        )
        result.map { draft ->
            fun field(key: String): String {
                val lines = draft.text.lines()
                val start = lines.indexOfFirst { it.trim().startsWith("$key:", ignoreCase = true) }
                if (start < 0) return ""
                val first = lines[start].substringAfter(':').trim()
                val rest = mutableListOf<String>()
                if (first.isNotEmpty()) rest += first
                var i = start + 1
                while (i < lines.size) {
                    val t = lines[i].trim()
                    if (t.contains(':') && t.substringBefore(':').all { it.isUpperCase() || it == '_' } && t.substringBefore(':').length in 4..28) break
                    if (t.isNotEmpty()) rest += t
                    i++
                }
                return rest.joinToString("\n").trim()
            }
            val outlet = draft.source("Hoops Wire Sideline")
            val stories = mutableListOf<News>()
            fun add(headKey: String, bodyKey: String, mine: Boolean = true) {
                val head = field(headKey)
                val body = field(bodyKey)
                if (head.isBlank() || body.isBlank()) return
                stories += News(
                    lg.season, lg.day, head, mine = mine, kind = "column", body = body, source = outlet,
                    teamId = lg.userTeamId, aiLabeled = draft.isAi, aiFallbackReason = draft.fallbackReason, approved = false,
                )
            }
            add("REPUTATION_HEADLINE", "REPUTATION_BODY")
            add("CAROUSEL_HEADLINE", "CAROUSEL_BODY", mine = false)
            add("RIVALRY_OR_LEGACY_HEADLINE", "RIVALRY_OR_LEGACY_BODY")
            val pack = if (stories.size >= 2) CoachCareers.Package(stories, draft.isAi, "Sideline filed ${stories.size} pieces.")
            else CoachCareers.fallbackPackage(lg).copy(detail = "AI thin — used game-written careers desk. ${draft.fallbackReason}")
            if (config.cacheResponses && pack.isAi) Cache.put(cacheKey, pack)
            pack
        }.recover { CoachCareers.fallbackPackage(lg) }
    }

    /** Fire-and-forget historic moment feature for a recorded highlight. */
    fun maybeAutoGenerateHistoricMoment(config: OllamaConfig, lg: League, m: Moment) {
        if (!config.enabled || !config.historicMoments || !config.autoGenerate) return
        CoroutineScope(Dispatchers.IO).launch {
            generateHistoricMoment(config, lg, m).onSuccess { pack ->
                // Prefer fleshing the matching news row; otherwise append.
                val match = lg.news.indexOfLast { it.gameId == m.gameId && it.season == m.season && it.text == m.headline }
                if (match >= 0 && pack.stories.isNotEmpty()) {
                    val s = pack.stories.first()
                    lg.news[match].body = s.body
                    lg.news[match].source = s.source
                    lg.news[match].aiLabeled = s.aiLabeled
                    lg.news[match].aiFallbackReason = s.aiFallbackReason
                    lg.news[match].approved = false
                } else if (pack.stories.isNotEmpty()) {
                    LeagueLists.appendNews(lg, pack.stories)
                }
            }
        }
    }

    /** Long-form coverage of a court storm / upset / buzzer / heartbreak / title moment. */
    suspend fun generateHistoricMoment(
        config: OllamaConfig,
        lg: League,
        m: Moment,
    ): Result<HistoricMoments.Package> = withContext(Dispatchers.IO) {
        if (!config.enabled || !config.historicMoments) {
            return@withContext Result.success(HistoricMoments.fallbackPackage(lg, m))
        }
        val brief = try {
            HistoricMoments.momentBrief(lg, m)
        } catch (_: java.util.ConcurrentModificationException) {
            return@withContext Result.success(HistoricMoments.fallbackPackage(lg, m).copy(detail = "Desk raced the sim — used game-written moment package."))
        }
        val cacheKey = "historic_${m.season}_${m.day}_${m.gameId}_${m.kind}_${config.activeModelName}_facts_${AiFacts.version(lg)}"
        if (config.cacheResponses) {
            Cache.get<HistoricMoments.Package>(cacheKey)?.let { return@withContext Result.success(it) }
        }
        val prompt = buildString {
            appendLine("You are a national basketball features desk. Using ONLY the MOMENT BRIEF, write one historic feature.")
            appendLine("Do not invent scores, shooters, quotes, or crowd size numbers.")
            appendLine("MOMENT BRIEF:")
            appendLine(brief)
            appendLine("Format EXACTLY:")
            appendLine("HEADLINE: <punchy feature headline matching the kind>")
            appendLine("BODY: <2-3 short paragraphs; court storms / buzzers / heartbreaks stay inside the brief>")
        }
        val result = groundedGenerate(
            config, lg, prompt,
            system = "Historic moments desk. Facts only from the brief.",
            numPredict = 360,
            eventGame = LeagueLists.games(lg).firstOrNull { it.id == m.gameId },
        )
        result.map { draft ->
            val lines = draft.text.lines()
            var headline = ""
            val body = mutableListOf<String>()
            var inBody = false
            for (l in lines) {
                when {
                    l.startsWith("HEADLINE:", ignoreCase = true) -> headline = l.substringAfter(':').trim()
                    l.startsWith("BODY:", ignoreCase = true) -> {
                        inBody = true
                        l.substringAfter(':').trim().takeIf { it.isNotEmpty() }?.let { body += it }
                    }
                    inBody -> if (l.isNotBlank()) body += l
                    headline.isEmpty() && l.isNotBlank() -> headline = l.trim()
                }
            }
            val head = headline.ifBlank { m.headline }
            val text = body.joinToString("\n").trim().ifBlank { draft.text.trim() }
            if (text.length < 40) return@map HistoricMoments.fallbackPackage(lg, m).copy(detail = "AI thin — ${draft.fallbackReason}")
            val story = News(
                m.season, m.day, head, mine = m.mine, gameId = m.gameId,
                kind = when (m.kind) {
                    MomentKind.UPSET, MomentKind.COURT_STORM -> "upset"
                    MomentKind.BUZZER -> "buzzer"
                    else -> "moment"
                },
                body = text, source = draft.source("Hoops Wire Moments"),
                teamId = m.winner, aiLabeled = draft.isAi, aiFallbackReason = draft.fallbackReason, approved = false,
            )
            val pack = HistoricMoments.Package(listOf(story), draft.isAi, "Historic feature filed.")
            if (config.cacheResponses && pack.isAi) Cache.put(cacheKey, pack)
            pack
        }.recover { HistoricMoments.fallbackPackage(lg, m) }
    }

    /** Fire-and-forget national sports desk package after a day of games. */
    fun maybeAutoGenerateJournalism(config: OllamaConfig, lg: League) {
        if (!config.enabled || !config.sportsJournalism || !config.autoGenerate) return
        if (lg.day < 3 || lg.day % 3 != 0) return
        CoroutineScope(Dispatchers.IO).launch {
            generateSportsJournalism(config, lg).onSuccess { pack ->
                if (pack.stories.isNotEmpty()) LeagueLists.appendNews(lg, pack.stories)
            }
        }
    }

    /**
     * National sports journalism package grounded in [Journalism.eventBrief]:
     * headlines, a controversial column, rankings debate, and recruiting/trade rumor.
     */
    suspend fun generateSportsJournalism(
        config: OllamaConfig,
        lg: League,
    ): Result<Journalism.Package> = withContext(Dispatchers.IO) {
        if (!config.enabled || !config.sportsJournalism) {
            return@withContext Result.success(Journalism.fallbackPackage(lg))
        }
        val brief = try {
            Journalism.eventBrief(lg)
        } catch (_: java.util.ConcurrentModificationException) {
            return@withContext Result.success(Journalism.fallbackPackage(lg).copy(detail = "Desk raced the sim — used game-written package."))
        }
        val cacheKey = "journalism_${lg.season}_${lg.day}_${config.activeModelName}" + "_facts_${AiFacts.version(lg)}_${config.provider}"
        if (config.cacheResponses) {
            Cache.get<Journalism.Package>(cacheKey)?.let { return@withContext Result.success(it) }
        }
        val rumorLabel = if (lg.pro) "TRADE_RUMOR" else "RECRUITING_RUMOR"
        val champId = TitleDefense.champion(lg)?.id ?: lg.teams.maxByOrNull { TitleDefense.titles(lg, it).size }?.id ?: lg.userTeamId
        val prompt = buildString {
            appendLine("You are Hoops Wire, a national basketball desk obsessed with titles and the whole league.")
            appendLine("Using ONLY the EVENT BRIEF, write four pieces. Lead with banners, defending champions, droughts, and league-wide races.")
            appendLine("Do not invent scores, commits, trades, injuries, banners, or quotes.")
            appendLine("EVENT BRIEF:")
            appendLine(brief)
            appendLine("Format EXACTLY:")
            appendLine("TITLE_WATCH_HEADLINE: <defending champ / banner ledger / title drought headline>")
            appendLine("TITLE_WATCH_BODY: <2 short paragraphs citing title facts from the brief>")
            appendLine("LEAGUE_PULSE_HEADLINE: <AP/Elo board, conference races, or hot seats>")
            appendLine("LEAGUE_PULSE_BODY: <2 paragraphs of league-wide situation>")
            appendLine("NATIONAL_HEADLINE: <today's results framed against the title/league picture>")
            appendLine("NATIONAL_BODY: <2 short paragraphs>")
            appendLine("${rumorLabel}_HEADLINE: <${if (lg.pro) "trade/FA" else "recruiting"} speculation tied to programs with history when possible>")
            appendLine("${rumorLabel}_BODY: <conditional analysis only — no confirmed deals>")
        }
        val result = groundedGenerate(
            config, lg, prompt,
            system = "Hoops Wire national desk. Titles and league situation first. Facts only from the brief. Speculation must be labeled as such.",
            numPredict = 560,
        )
        result.map { draft ->
            fun field(key: String): String {
                val lines = draft.text.lines()
                val start = lines.indexOfFirst { it.trim().startsWith("$key:", ignoreCase = true) }
                if (start < 0) return ""
                val first = lines[start].substringAfter(':').trim()
                val rest = mutableListOf<String>()
                if (first.isNotEmpty()) rest += first
                var i = start + 1
                while (i < lines.size) {
                    val t = lines[i].trim()
                    if (t.contains(':') && t.substringBefore(':').all { it.isUpperCase() || it == '_' } && t.substringBefore(':').length in 4..28) break
                    if (t.isNotEmpty()) rest += t
                    i++
                }
                return rest.joinToString("\n").trim()
            }
            val outlet = draft.source("Hoops Wire Desk")
            val stories = mutableListOf<News>()
            fun add(kind: String, headKey: String, bodyKey: String, teamId: Int = lg.userTeamId, mine: Boolean = true) {
                val head = field(headKey)
                val body = field(bodyKey)
                if (head.isBlank() || body.isBlank()) return
                stories += News(
                    lg.season, lg.day, head, mine = mine, kind = kind, body = body, source = outlet,
                    teamId = teamId, aiLabeled = draft.isAi, aiFallbackReason = draft.fallbackReason, approved = false,
                )
            }
            add("column", "TITLE_WATCH_HEADLINE", "TITLE_WATCH_BODY", teamId = champId, mine = champId == lg.userTeamId)
            add("column", "LEAGUE_PULSE_HEADLINE", "LEAGUE_PULSE_BODY", teamId = lg.teams.filter { it.ap in 1..25 }.minByOrNull { it.ap }?.id ?: lg.teams.maxByOrNull { it.elo }?.id ?: lg.userTeamId, mine = false)
            add("article", "NATIONAL_HEADLINE", "NATIONAL_BODY", teamId = lg.teams.maxByOrNull { it.elo }?.id ?: lg.userTeamId, mine = false)
            add("column", "${rumorLabel}_HEADLINE", "${rumorLabel}_BODY", mine = false)
            val pack = if (stories.size >= 2) Journalism.Package(stories, draft.isAi, "Desk filed ${stories.size} pieces.")
            else Journalism.fallbackPackage(lg).copy(detail = "AI thin — used game-written desk. ${draft.fallbackReason}")
            if (config.cacheResponses && pack.isAi) Cache.put(cacheKey, pack)
            pack
        }.recover { Journalism.fallbackPackage(lg) }
    }

    /** Fire-and-forget postgame player interviews for the user's game. */
    fun maybeAutoGenerateInterviews(config: OllamaConfig, lg: League, g: Game) {
        if (!config.enabled || !config.playerInterviews || !config.autoGenerate) return
        if (!g.involves(lg.userTeamId) || g.box == null) return
        CoroutineScope(Dispatchers.IO).launch {
            generatePlayerInterviews(config, lg, g).onSuccess { list ->
                if (list.isNotEmpty()) {
                    g.interviews.clear()
                    g.interviews.addAll(list)
                }
            }
        }
    }

    /** Fire-and-forget postgame press question generation if auto-generate is enabled. */
    fun maybeAutoGeneratePress(
        config: OllamaConfig,
        lg: League,
        g: Game,
        player: Player,
        line: GameLogEntry,
        won: Boolean,
        rivalry: Boolean = false,
        winner: Boolean = false,
    ) {
        if (!config.enabled || !config.pressConference || !config.autoGenerate) return
        CoroutineScope(Dispatchers.IO).launch {
            generatePressConference(config, lg, g, player, line, won, rivalry, winner).onSuccess { aiQ ->
                if (lg.road?.press?.gameId == g.id) {
                    lg.road?.press = aiQ
                }
            }
        }
    }

    /** Fire-and-forget social feed reactions generation if auto-generate is enabled. */
    fun maybeAutoGenerateSocial(
        config: OllamaConfig,
        lg: League,
        g: Game,
        player: Player,
        line: GameLogEntry,
        won: Boolean,
    ) {
        if (!config.enabled || !config.socialFeed || !config.autoGenerate) return
        CoroutineScope(Dispatchers.IO).launch {
            generateSocialTakes(config, lg, g, player, line, won).onSuccess { posts ->
                lg.road?.feed?.addAll(posts)
            }
        }
    }

    data class PostgameReport(
        val headline: String,
        val outlet: String,
        val story: String,
        val coachQuote: String,
        val starQuote: String,
        val isAi: Boolean = false,
        val fallbackReason: String = "",
    )

    data class Brief(val first: String, val second: String, val isAi: Boolean = false, val fallbackReason: String = "")

    data class ScoutingKey(
        val title: String,
        val detail: String,
        val isAi: Boolean = false,
        val fallbackReason: String = "",
    )

    data class TradeAnalysis(
        val gradeA: String,
        val gradeB: String,
        val analysis: String,
        val isAi: Boolean = false,
        val fallbackReason: String = "",
    )

    data class ProspectReport(
        val proComparison: String,
        val strengths: String,
        val weaknesses: String,
        val upside: String,
        val blurb: String,
        val isAi: Boolean = false,
        val fallbackReason: String = "",
    )

    data class InjuryAnalysis(
        val headline: String,
        val severityOutlook: String,
        val nextManUpStrategy: String,
        val quote: String,
        val isAi: Boolean = false,
        val fallbackReason: String = "",
    )

    data class AwardSpeech(
        val awardTitle: String,
        val acceptanceSpeech: String,
        val mediaTribute: String,
        val isAi: Boolean = false,
        val fallbackReason: String = "",
    )

    data class HalftimeSpeech(
        val lockerRoomSpeech: String,
        val tacticalAdjustment: String,
        val isAi: Boolean = false,
        val fallbackReason: String = "",
    )

    data class TrainingCampReport(
        val headline: String,
        val standoutAnalysis: String,
        val seasonOutlook: String,
        val isAi: Boolean = false,
        val fallbackReason: String = "",
    )

    /** Parse AI interview lines: `PID|TONE|QUESTION|ANSWER`. */
    internal fun parseInterviewLines(raw: String, byId: Map<Int, Player>, reporter: String, isAi: Boolean, fallbackReason: String = ""): List<PlayerInterview> {
        val out = mutableListOf<PlayerInterview>()
        for (rawLine in raw.lines()) {
            val line = rawLine.trim().trimStart('-', '*', ' ')
            if (line.isEmpty() || !line.contains('|')) continue
            val parts = line.split('|').map { it.trim() }
            if (parts.size < 4) continue
            val id = parts[0].filter { it.isDigit() }.toIntOrNull() ?: continue
            val p = byId[id] ?: continue
            val tone = parts[1].lowercase().ifBlank { "measured" }
            val question = parts[2].trim('"')
            val answer = parts[3].trim('"')
            if (question.isBlank() || answer.isBlank()) continue
            out += PlayerInterview(p.id, p.name, question, answer, reporter, tone, isAi, fallbackReason)
        }
        return out
    }

    /**
     * Postgame interviews for standout players on the user's team.
     * Answers reflect personality, tonight's performance, confidence, and coach relationship.
     */
    suspend fun generatePlayerInterviews(
        config: OllamaConfig,
        lg: League,
        g: Game,
    ): Result<List<PlayerInterview>> = withContext(Dispatchers.IO) {
        val box = g.box ?: return@withContext Result.success(emptyList())
        val userSide = if (g.home == lg.userTeamId) box.home else if (g.away == lg.userTeamId) box.away else return@withContext Result.success(emptyList())
        val t = lg.user
        val opp = lg.team(g.opp(t.id))
        val won = g.winner() == t.id
        val reporter = Press.beat(lg, t)
        val candidates = userSide
            .mapNotNull { pl -> lg.playerOrNull(pl.pid)?.let { it to pl.s } }
            .filter { (_, s) -> s.sec >= 8 * 60 || s.pts + s.reb + s.ast >= 8 }
            .sortedByDescending { (_, s) -> s.pts * 2 + s.reb + s.ast * 2 + s.stl + s.blk }
            .take(3)
        if (candidates.isEmpty()) return@withContext Result.success(emptyList())

        fun toLog(p: Player, s: StatLine) = GameLogEntry(
            g.id, opp.id, g.site(t.id), won, "${g.scoreFor(t.id)}-${g.scoreAgainst(t.id)}",
            s.pts, s.reb, s.ast, s.stl, s.blk, s.tov, (s.sec / 60.0).toInt(),
        )

        if (!config.enabled || !config.playerInterviews) {
            return@withContext Result.success(candidates.map { (p, s) -> Personality.fallbackInterview(lg, g, p, toLog(p, s), reporter) })
        }

        val cacheKey = "interview_${g.id}_${candidates.joinToString("-") { it.first.id.toString() }}_${config.activeModelName}" +
            "_facts_${AiFacts.version(lg)}_${config.provider}"
        if (config.cacheResponses) {
            Cache.get<List<PlayerInterview>>(cacheKey)?.let { return@withContext Result.success(it) }
        }

        val rosterBlock = candidates.joinToString("\n") { (p, s) ->
            "- ${Personality.interviewContext(lg, p, toLog(p, s))} id=${p.id} name=${p.name}"
        }
        val prompt = buildString {
            appendLine("Write short postgame media interviews for these basketball players.")
            appendLine("Game: ${t.school} ${g.scoreFor(t.id)}, ${opp.school} ${g.scoreAgainst(t.id)} (${if (won) "WIN" else "LOSS"}).")
            appendLine("Each answer MUST match that player's personality, tonight's stats, confidence, and relationship with the coach.")
            appendLine("Do not invent stats. One question and one answer per player.")
            appendLine("Format EXACTLY one line per player:")
            appendLine("PID|TONE|QUESTION|ANSWER")
            appendLine("TONE is one of: confident, humble, frustrated, measured")
            appendLine("Players:")
            appendLine(rosterBlock)
        }
        val result = groundedGenerate(
            config, lg, prompt,
            system = "You are a beat writer. Reply only with PID|TONE|QUESTION|ANSWER lines. Stay in character for each player.",
            numPredict = 320,
            eventGame = g,
            extraPlayers = candidates.map { it.first },
        )
        val byId = candidates.associate { it.first.id to it.first }
        result.map { draft ->
            val parsed = parseInterviewLines(draft.text, byId, if (draft.isAi) "$reporter · ${draft.provider}" else reporter, draft.isAi, draft.fallbackReason)
            val filled = candidates.map { (p, s) ->
                parsed.firstOrNull { it.pid == p.id } ?: Personality.fallbackInterview(lg, g, p, toLog(p, s), reporter)
            }
            if (config.cacheResponses && filled.any { it.isAi }) Cache.put(cacheKey, filled)
            filled
        }.recover {
            candidates.map { (p, s) -> Personality.fallbackInterview(lg, g, p, toLog(p, s), reporter) }
        }
    }

    /** Generates a dynamic postgame press conference question & answers tailored to the exact game stats. */
    suspend fun generatePressConference(
        config: OllamaConfig,
        lg: League,
        g: Game,
        player: Player,
        line: GameLogEntry,
        won: Boolean,
        rivalry: Boolean = false,
        winner: Boolean = false,
    ): Result<PressQuestion> {
        val cacheKey = "press_${g.id}_${player.id}_${line.pts}_${line.reb}_${line.ast}_${config.model}" + "_facts_${AiFacts.version(lg)}_${config.provider}_${config.activeModelName}"
        if (config.cacheResponses) {
            Cache.get<PressQuestion>(cacheKey)?.let { return Result.success(it) }
        }
        val t = if (player.teamId >= 0) lg.team(player.teamId) else lg.user
        val opp = lg.team(g.opp(t.id))
        val us = g.scoreFor(t.id)
        val them = g.scoreAgainst(t.id)
        val statLineStr = Feed.line(line.pts, line.reb, line.ast) +
            (if (line.blk >= 3) ", ${line.blk} BLK" else "") +
            (if (line.stl >= 3) ", ${line.stl} STL" else "") +
            (if (line.tov >= 5) ", ${line.tov} TOV" else "")

        val prompt = buildString {
            append("You are generating a postgame basketball press conference question and answers for a simulation game.\n")
            append("Player: ${player.name} (${player.posLabel})\n")
            append("Team: ${t.school} (${t.w}-${t.l})\n")
            append("Opponent: ${opp.school} (${opp.record})\n")
            append("Final Score: ${t.school} $us, ${opp.school} $them (${if (won) "WIN" else "LOSS"})\n")
            append("Tonight's Stats: $statLineStr in ${line.min} minutes\n")
            append("${Personality.interviewContext(lg, player, line)}\n")
            if (winner) append("Highlight: Sunk the game-winning clutch shot!\n")
            if (rivalry) append("Context: Heated rivalry matchup\n")
            append("\nStrictly use the exact score and stats. Do not change or invent different numbers.\n")
            append("Generate 1 beat reporter question and 3 distinct player answers that could come from THIS player's personality:\n")
            append("Format your response EXACTLY as:\n")
            append("TOPIC: <1-3 words topic>\n")
            append("REPORTER: <Reporter Name · Outlet>\n")
            append("QUESTION: <Direct question addressing tonight's game and performance>\n")
            append("HUMBLE: <Modest, team-first answer in his voice>\n")
            append("BRASH: <Confident, swaggering answer in his voice>\n")
            append("ANALYTICAL: <Tactical, measured answer in his voice>\n")
        }

        return groundedGenerate(
            config,
            lg,
            prompt,
            system = "You are a professional basketball beat writer conducting a post-game press conference. Strictly use the provided stats, scores, and names. Never alter the statistics.",
            numPredict = 260,
        ).map { draft ->
            val raw = draft.text
            var topic = if (winner) "Game-Winner" else if (rivalry) "Rivalry" else if (won) "Postgame Win" else "Tough Loss"
            var reporter = Press.beat(lg, t)
            var question = ""
            var humble = ""
            var brash = ""
            var analytical = ""

            for (lineStr in raw.lines()) {
                val tr = lineStr.trim()
                when {
                    tr.startsWith("TOPIC:", ignoreCase = true) -> topic = tr.substringAfter(":").trim()
                    tr.startsWith("REPORTER:", ignoreCase = true) -> {
                        val rep = tr.substringAfter(":").trim()
                        if (rep.isNotEmpty()) reporter = rep
                    }
                    tr.startsWith("QUESTION:", ignoreCase = true) -> question = tr.substringAfter(":").trim()
                    tr.startsWith("HUMBLE:", ignoreCase = true) -> humble = tr.substringAfter(":").trim()
                    tr.startsWith("BRASH:", ignoreCase = true) -> brash = tr.substringAfter(":").trim()
                    tr.startsWith("ANALYTICAL:", ignoreCase = true) -> analytical = tr.substringAfter(":").trim()
                }
            }

            if (question.isEmpty()) {
                question = if (won) "Great win tonight with $statLineStr. Walk us through how you stayed in rhythm."
                else "Tough outcome tonight against ${opp.school}. What was the difference down the stretch?"
            }
            if (humble.isEmpty()) humble = "Credit to my teammates and coaching staff. We put the work in every day and just tried to execute the game plan."
            if (brash.isEmpty()) brash = "When the ball is in my hands, I expect to make plays. Tonight was just a glimpse of what we can do."
            if (analytical.isEmpty()) analytical = "We identified their defensive coverages early on and took what they gave us in transition and half-court sets."

            val questionObj = PressQuestion(
                season = lg.season,
                gameId = g.id,
                topic = topic,
                prompt = question,
                answers = listOf(
                    PressAnswer(humble, followers = 0.015, fans = 2, teammates = 3, persona = -1),
                    PressAnswer(brash, followers = 0.04, fans = 0, teammates = -2, persona = 2),
                    PressAnswer(analytical, followers = 0.008, fans = 1, teammates = 1, persona = 0),
                ),
                reporter = if (draft.isAi) "$reporter · ${draft.provider}" else "Staff",
                isAi = draft.isAi, fallbackReason = draft.fallbackReason,
            )
            if (config.cacheResponses) Cache.put(cacheKey, questionObj)
            questionObj
        }
    }

    /** Generates fresh social media hot takes reacting to tonight's game based strictly on real stats. */
    suspend fun generateSocialTakes(
        config: OllamaConfig,
        lg: League,
        g: Game,
        player: Player,
        line: GameLogEntry,
        won: Boolean,
    ): Result<List<Post>> {
        val cacheKey = "social_${g.id}_${player.id}_${line.pts}_${line.reb}_${line.ast}_${config.model}" + "_facts_${AiFacts.version(lg)}_${config.provider}_${config.activeModelName}"
        if (config.cacheResponses) {
            Cache.get<List<Post>>(cacheKey)?.let { return Result.success(it) }
        }
        val t = if (player.teamId >= 0) lg.team(player.teamId) else lg.user
        val opp = lg.team(g.opp(t.id))
        val us = g.scoreFor(t.id)
        val them = g.scoreAgainst(t.id)
        val statLineStr = Feed.line(line.pts, line.reb, line.ast)

        val prompt = buildString {
            append("Write 4 realistic social media posts (X / Twitter style) reacting to tonight's basketball game:\n")
            append("Player: ${player.name} (${t.school})\n")
            append("Opponent: ${opp.school}\n")
            append("Final Score: ${t.school} $us, ${opp.school} $them (${if (won) "WIN" else "LOSS"})\n")
            append("Player Stats: $statLineStr\n\n")
            append("Strictly use the exact stats and score provided. Never alter or exaggerate the numbers.\n")
            append("Format your response EXACTLY as:\n")
            append("ANALYST: <Handle without @> | <Analytical take on film, spacing, or stat efficiency>\n")
            append("FAN: <Handle without @> | <Passionate fan reaction celebrating or venting>\n")
            append("HATER: <Handle without @> | <Skeptical or critical hot take>\n")
            append("TEAMMATE: <Handle without @> | <Supportive locker room hype post>\n")
        }

        return groundedGenerate(
            config,
            lg,
            prompt,
            system = "You write realistic, engaging basketball social media posts. Strictly reflect the exact provided game statistics without altering numbers.",
            numPredict = 260,
        ).map { draft ->
            val raw = draft.text
            val posts = mutableListOf<Post>()
            val day = lg.day
            val season = lg.season

            for (lineStr in raw.lines()) {
                val tr = lineStr.trim()
                val (role, content) = when {
                    tr.startsWith("ANALYST:", true) -> "analyst" to tr.substringAfter(":").trim()
                    tr.startsWith("FAN:", true) -> "fan" to tr.substringAfter(":").trim()
                    tr.startsWith("HATER:", true) -> "hater" to tr.substringAfter(":").trim()
                    tr.startsWith("TEAMMATE:", true) -> "teammate" to tr.substringAfter(":").trim()
                    else -> continue
                }
                val handle = content.substringBefore("|").trim().removePrefix("@").ifEmpty { "hoopstalk" }
                val text = content.substringAfter("|").trim()
                if (text.isNotEmpty()) {
                    val name = when (role) {
                        "analyst" -> "Hoops Film Room"
                        "fan" -> "${t.school} Faithful"
                        "hater" -> "Hot Takes Only"
                        "teammate" -> "Locker Room Wire"
                        else -> "Hoops Talk"
                    }
                    val likes = when (role) {
                        "analyst" -> 450 + (line.pts * 12)
                        "teammate" -> 850 + (line.pts * 15)
                        "hater" -> 90 + (line.pts * 3)
                        else -> 320 + (line.pts * 10)
                    }
                    posts.add(
                        Post(
                            season = season,
                            day = day,
                            name = name,
                            handle = handle,
                            kind = role,
                            text = text,
                            likes = likes,
                            verified = role == "analyst" || role == "teammate",
                            gameId = g.id,
                            isAi = draft.isAi, fallbackReason = draft.fallbackReason,
                        ),
                    )
                }
            }
            val finalPosts = if (posts.isEmpty()) {
                listOf(
                    Post(season, day, "${t.school} Faithful", "${t.abbr.lowercase()}nation", "fan",
                        if (won) "What a performance by ${player.last}! $statLineStr in the win over ${opp.school}."
                        else "Tough loss tonight against ${opp.school}, but ${player.last} gave everything with $statLineStr.",
                        likes = 250, gameId = g.id, isAi = draft.isAi, fallbackReason = draft.fallbackReason),
                )
            } else posts
            if (config.cacheResponses) Cache.put(cacheKey, finalPosts)
            finalPosts
        }
    }

    /** Generates a complete newspaper beat writer recap with coach and player quotes for a completed game. */
    suspend fun generatePostgameReport(
        config: OllamaConfig,
        lg: League,
        g: Game,
        onToken: ((String) -> Unit)? = null,
    ): Result<PostgameReport> {
        val cacheKey = "recap_${g.id}_${config.model}_${config.speedMode}" + "_facts_${AiFacts.version(lg)}_${config.provider}_${config.activeModelName}"
        if (config.cacheResponses) {
            Cache.get<PostgameReport>(cacheKey)?.let { return Result.success(it) }
        }
        val home = lg.team(g.home)
        val away = lg.team(g.away)
        val winner = if (g.hs > g.aws) home else away
        val loser = if (g.hs > g.aws) away else home
        val winScore = maxOf(g.hs, g.aws)
        val loseScore = minOf(g.hs, g.aws)
        val box = g.box
        val topPlayerLine = box?.let { b ->
            val all = (b.home + b.away).mapNotNull { pl -> lg.playerOrNull(pl.pid)?.let { p -> p to pl.s } }
            all.maxByOrNull { (_, s) -> s.pts + s.reb + s.ast }
        }
        val topPlayerName = topPlayerLine?.first?.name ?: "${winner.school} star"
        val topStatsStr = topPlayerLine?.second?.let { Feed.line(it.pts, it.reb, it.ast) } ?: "$winScore points"
        val coach = (if (winner.id == lg.userTeamId && lg.userCoachId >= 0) lg.coaches.firstOrNull { it.id == lg.userCoachId }?.name else null)
            ?: "${winner.school} head coach"

        val prompt = buildString {
            append("Write a realistic newspaper beat writer game recap for this basketball game:\n")
            append("Matchup: ${away.school} (${away.record}) at ${home.school} (${home.record})\n")
            append("Final Score: ${winner.school} $winScore, ${loser.school} $loseScore\n")
            append("Top Performer: $topPlayerName with $topStatsStr\n")
            append("Winning Coach: $coach\n")
            append("\nStrictly use the exact score and stats. Do not invent or change numbers.\n")
            append("Format your response EXACTLY as:\n")
            append("HEADLINE: <Catchy newspaper headline>\n")
            append("OUTLET: <Publication name e.g. Courtside Report or Daily Dribble>\n")
            append("STORY:\n<4-7 paragraphs using supplied game facts>\n")
            append("COACH_QUOTE: <Use —; no recorded quote supplied>\n")
            append("STAR_QUOTE: <Use —; no recorded quote supplied>\n")
        }

        return groundedGenerate(
            config,
            lg,
            prompt,
            system = "You are a professional basketball beat writer. Write engaging, realistic newspaper recaps using the exact provided statistics and final score.",
            numPredict = if (config.speedMode == "turbo") 200 else 320,
            onToken = onToken,
            articleCopy = ArticleWriting.game(lg, g),
            eventGame = g,
            extraFacts = ArticleWriting.boxFacts(g) + "\n" + ArticleWriting.seriesFacts(lg, g),
        ).map { draft ->
            val raw = draft.text
            var headline = "${winner.school} Defeats ${loser.school} $winScore-$loseScore"
            var outlet = "Courtside Report"
            val storyLines = mutableListOf<String>()
            var coachQuote = ""
            var starQuote = ""
            var readingStory = false

            for (lineStr in raw.lines()) {
                val tr = lineStr.trim()
                when {
                    tr.startsWith("HEADLINE:", true) -> headline = tr.substringAfter(":").trim()
                    tr.startsWith("OUTLET:", true) -> outlet = tr.substringAfter(":").trim()
                    tr.startsWith("STORY:", true) -> { readingStory = true; tr.substringAfter(':').trim().takeIf { it.isNotEmpty() }?.let { storyLines += it }; continue }
                    tr.startsWith("COACH_QUOTE:", true) -> { readingStory = false; coachQuote = tr.substringAfter(":").trim() }
                    tr.startsWith("STAR_QUOTE:", true) -> { readingStory = false; starQuote = tr.substringAfter(":").trim() }
                    readingStory -> if (tr.isNotBlank()) storyLines.add(tr)
                }
            }
            val story = if (storyLines.isNotEmpty()) storyLines.joinToString("\n\n")
            else "${winner.school} took care of business against ${loser.school} with a $winScore-$loseScore victory, powered by $topPlayerName's $topStatsStr."
            if (coachQuote.isEmpty()) coachQuote = "We locked in defensively when it mattered most and trusted our offensive execution."
            if (starQuote.isEmpty()) starQuote = "We stayed aggressive, took good looks, and played for each other tonight."

            val rep = PostgameReport(headline, outlet, story, if (draft.isAi) coachQuote else "", if (draft.isAi) starQuote else "", draft.isAi, draft.fallbackReason)
            if (config.cacheResponses) Cache.put(cacheKey, rep)
            rep
        }
    }

    /** Generates 3 tactical pregame scouting keys based on team ratings and matchups. */
    suspend fun generateScoutingKeys(
        config: OllamaConfig,
        lg: League,
        userTeam: Team,
        oppTeam: Team,
    ): Result<List<ScoutingKey>> {
        val cacheKey = "scout_${userTeam.id}_${oppTeam.id}_${lg.season}_${lg.day}_${config.model}" + "_facts_${AiFacts.version(lg)}_${config.provider}_${config.activeModelName}"
        if (config.cacheResponses) {
            Cache.get<List<ScoutingKey>>(cacheKey)?.let { return Result.success(it) }
        }
        val oppRoster = lg.rosterOf(oppTeam)
        val oppStar = oppRoster.filter { it.stats.gp > 0 }.maxByOrNull { it.stats.ppg } ?: oppRoster.maxByOrNull { it.ovr }
        val userStar = lg.rosterOf(userTeam).filter { it.stats.gp > 0 }.maxByOrNull { it.stats.ppg }

        val prompt = buildString {
            append("Write 3 tactical pregame scouting keys for this basketball matchup:\n")
            append("User Team: ${userTeam.school} (${userTeam.record})\n")
            append("Opponent: ${oppTeam.school} (${oppTeam.record})\n")
            if (oppStar != null) append("Opponent Player to Watch: ${oppStar.name} (${oppStar.posLabel}, averaging ${"%.1f".format(oppStar.stats.ppg)} ppg)\n")
            if (userStar != null) append("User Team Top Scorer: ${userStar.name} (${userStar.posLabel}, averaging ${"%.1f".format(userStar.stats.ppg)} ppg)\n")
            append("\nStrictly use the team names and player names provided.\n")
            append("Format your response EXACTLY as:\n")
            append("KEY 1: <Short Title> | <1-2 sentences of tactical keys to victory>\n")
            append("KEY 2: <Short Title> | <1-2 sentences of tactical keys to victory>\n")
            append("KEY 3: <Short Title> | <1-2 sentences of tactical keys to victory>\n")
        }

        return groundedGenerate(
            config,
            lg,
            prompt,
            system = "You are a professional basketball scout and analyst providing tactical pregame keys. Be concise, realistic, and insightful.",
            numPredict = 240,
        ).map { draft ->
            val raw = draft.text
            val keys = mutableListOf<ScoutingKey>()
            for (lineStr in raw.lines()) {
                val tr = lineStr.trim()
                if (tr.startsWith("KEY", true) && tr.contains("|")) {
                    val title = tr.substringAfter(":").substringBefore("|").trim().ifEmpty { "Game Key" }
                    val detail = tr.substringAfter("|").trim()
                    if (detail.isNotEmpty()) {
                        keys.add(ScoutingKey(title, detail, draft.isAi, draft.fallbackReason))
                    }
                }
            }
            val finalKeys = if (keys.isEmpty()) {
                listOf(
                    ScoutingKey("Pace & Transition", "Control the tempo and limit ${oppTeam.school}'s fast-break scoring opportunities."),
                    ScoutingKey("Paint Protection", "Protect the rim and close out defensive possessions with solid rebounding."),
                    ScoutingKey("Ball Movement", "Execute offensive sets patiently and make the extra pass to open shooters."),
                )
            } else keys
            if (config.cacheResponses) Cache.put(cacheKey, finalKeys)
            finalKeys
        }
    }

    /** Generates an insider trade grade & analysis for a completed deal. */
    suspend fun generateTradeAnalysis(
        config: OllamaConfig,
        lg: League,
        trade: Trade,
    ): Result<TradeAnalysis> {
        val cacheKey = "trade_${trade.teamA}_${trade.teamB}_${trade.toA.joinToString()}_${trade.toB.joinToString()}_${config.model}" + "_facts_${AiFacts.version(lg)}_${config.provider}_${config.activeModelName}"
        if (config.cacheResponses) {
            Cache.get<TradeAnalysis>(cacheKey)?.let { return Result.success(it) }
        }
        val a = lg.team(trade.teamA)
        val b = lg.team(trade.teamB)
        val prompt = buildString {
            append("Analyze this completed basketball trade:\n")
            append("Team A (${a.school}): Receives ${trade.toA.joinToString(", ")}\n")
            append("Team B (${b.school}): Receives ${trade.toB.joinToString(", ")}\n")
            append("\nStrictly analyze this trade based on the actual players listed.\n")
            append("Format your response EXACTLY as:\n")
            append("GRADE_A: <Letter Grade, e.g. A-, B+, B, C>\n")
            append("GRADE_B: <Letter Grade, e.g. A-, B+, B, C>\n")
            append("ANALYSIS:\n<1-2 paragraphs of sharp trade breakdown analyzing roster fit, value, and future outlook>\n")
        }

        return groundedGenerate(
            config,
            lg,
            prompt,
            system = "You are an NBA front office analyst and insider grading trades objectively.",
            numPredict = 260,
            extraFacts = "Trade: ${a.name} receives ${trade.toA.joinToString(", ")}; ${b.name} receives ${trade.toB.joinToString(", ")}.",
        ).map { draft ->
            val raw = draft.text
            var gradeA = "B"
            var gradeB = "B"
            val analysisLines = mutableListOf<String>()
            var readingAnalysis = false

            for (lineStr in raw.lines()) {
                val tr = lineStr.trim()
                when {
                    tr.startsWith("GRADE_A:", true) -> gradeA = tr.substringAfter(":").trim()
                    tr.startsWith("GRADE_B:", true) -> gradeB = tr.substringAfter(":").trim()
                    tr.startsWith("ANALYSIS:", true) -> { readingAnalysis = true; continue }
                    readingAnalysis -> if (tr.isNotBlank()) analysisLines.add(tr)
                }
            }
            val analysis = if (analysisLines.isNotEmpty()) analysisLines.joinToString("\n\n")
            else "A balanced exchange that addresses positional needs for both ${a.school} and ${b.school}."

            val result = TradeAnalysis(gradeA, gradeB, analysis, draft.isAi, draft.fallbackReason)
            if (config.cacheResponses) Cache.put(cacheKey, result)
            result
        }
    }

    /** Generates 3 realistic NBA trade deadline rumor mill whispers per league sources. */
    suspend fun generateTradeRumors(
        config: OllamaConfig,
        lg: League,
    ): Result<List<Brief>> {
        val cacheKey = "rumors_${lg.season}_${lg.day / 7}_${config.model}" + "_facts_${AiFacts.version(lg)}_${config.provider}_${config.activeModelName}"
        if (config.cacheResponses) {
            Cache.get<List<Brief>>(cacheKey)?.let { return Result.success(it) }
        }
        val buyers = lg.teams.filter { it.w + it.l >= 10 && it.winPct >= 0.52 }.take(3)
        val sellers = lg.teams.filter { it.w + it.l >= 10 && it.winPct < 0.45 }.take(3)

        val prompt = buildString {
            append("Write 3 realistic NBA trade deadline rumor mill whispers per league sources:\n")
            append("Buyers looking to contend: ${buyers.joinToString(", ") { "${it.school} (${it.record})" }}\n")
            append("Sellers looking to retool: ${sellers.joinToString(", ") { "${it.school} (${it.record})" }}\n\n")
            append("Format your response EXACTLY as:\n")
            append("RUMOR 1: <Sentence-case headline> | <Conditional roster-fit analysis, not a confirmed report>\n")
            append("RUMOR 2: <Sentence-case headline> | <Conditional analysis based on the supplied records>\n")
            append("RUMOR 3: <Sentence-case headline> | <Conditional analysis; no invented transactions or sources>\n")
        }

        return groundedGenerate(
            config,
            lg,
            prompt,
            system = "Discuss possible roster fits as speculation, never as sourced or confirmed transactions.",
            numPredict = 220,
        ).map { draft ->
            val raw = draft.text
            val rumors = ArticleWriting.briefs(raw, "RUMOR").map { Brief(it.first, it.second, draft.isAi, draft.fallbackReason) }
            val finalRumors = rumors
            if (config.cacheResponses) Cache.put(cacheKey, finalRumors)
            finalRumors
        }
    }

    /** Generates a comprehensive pro scouting report and NBA player comparison for a draft prospect. */
    suspend fun generateProspectScouting(
        config: OllamaConfig,
        lg: League,
        prospect: Prospect,
        onToken: ((String) -> Unit)? = null,
    ): Result<ProspectReport> = withContext(Dispatchers.IO) {
        val p = prospect.p
        val cacheKey = "prospect_${p.id}_${prospect.rank}_${config.activeModelName}" + "_facts_${AiFacts.version(lg)}_${config.provider}_${config.activeModelName}"
        if (config.cacheResponses) {
            Cache.get<ProspectReport>(cacheKey)?.let { return@withContext Result.success(it) }
        }

        val prompt = buildString {
            appendLine("Evaluate NBA/pro draft prospect:")
            appendLine("Prospect: ${p.name}")
            appendLine("Position: ${p.posLabel}")
            appendLine("School/Team: ${prospect.school}")
            appendLine("Age: ${p.age}, Height: ${p.height} in, Weight: ${p.weight} lb")
            appendLine("Estimated Overall: ${prospect.rank}, Style: ${p.style.label}")
            appendLine("Format your response EXACTLY as:")
            appendLine("PRO_COMP: <Playing-style archetype, not an outside player name>")
            appendLine("STRENGTHS: <2 sentence summary of strengths>")
            appendLine("WEAKNESSES: <2 sentence summary of areas to develop>")
            appendLine("UPSIDE: <Pro ceiling projection>")
            appendLine("BLURB: <3-4 sentence comprehensive scouting analysis>")
        }

        groundedGenerate(config, lg, prompt, onToken = onToken, extraPlayers = listOf(p), extraFacts = "Draft prospect ${p.name}; consensus board rank ${prospect.rank}; school ${prospect.school}").map { draft ->
            val text = draft.text
            val lines = text.lines()
            val comp = lines.firstOrNull { it.startsWith("PRO_COMP:") }?.removePrefix("PRO_COMP:")?.trim() ?: "Versatile modern wing"
            val strengths = lines.firstOrNull { it.startsWith("STRENGTHS:") }?.removePrefix("STRENGTHS:")?.trim() ?: "High basketball IQ and solid positional size."
            val weaknesses = lines.firstOrNull { it.startsWith("WEAKNESSES:") }?.removePrefix("WEAKNESSES:")?.trim() ?: "Needs continued development on off-hand finishes."
            val upside = lines.firstOrNull { it.startsWith("UPSIDE:") }?.removePrefix("UPSIDE:")?.trim() ?: "Quality starter with high developmental upside."
            val blurb = lines.firstOrNull { it.startsWith("BLURB:") }?.removePrefix("BLURB:")?.trim()
                ?: text.substringAfter("BLURB:").trim().ifEmpty { text }
            val report = ProspectReport(comp, strengths, weaknesses, upside, blurb, draft.isAi, draft.fallbackReason)
            if (config.cacheResponses) Cache.put(cacheKey, report)
            report
        }
    }

    /** Generates a medical outlook, return timeline, and tactical next-man-up breakdown for an injured player. */
    suspend fun generateInjuryAnalysis(
        config: OllamaConfig,
        lg: League,
        team: Team,
        player: Player,
        onToken: ((String) -> Unit)? = null,
    ): Result<InjuryAnalysis> = withContext(Dispatchers.IO) {
        val cacheKey = "injury_${player.id}_${player.injGames}_${config.activeModelName}" + "_facts_${AiFacts.version(lg)}_${config.provider}_${config.activeModelName}"
        if (config.cacheResponses) {
            Cache.get<InjuryAnalysis>(cacheKey)?.let { return@withContext Result.success(it) }
        }

        val tag = Injuries.tag(player)
        val prompt = buildString {
            appendLine("Analyze basketball player injury:")
            appendLine("Player: ${player.name}")
            appendLine("Team: ${team.name}")
            appendLine("Position: ${player.posLabel}")
            appendLine("Injury: $tag")
            appendLine("Games Missed: ${player.injGames} of ${player.injTotal}")
            appendLine("Format your response EXACTLY as:")
            appendLine("HEADLINE: <Catchy medical news headline>")
            appendLine("SEVERITY_OUTLOOK: <Medical recovery timeline and severity evaluation>")
            appendLine("NEXT_MAN_UP: <Tactical adjustment for the roster>")
            appendLine("QUOTE: <Coach or teammate quote>")
        }

        groundedGenerate(config, lg, prompt, onToken = onToken, extraPlayers = listOf(player)).map { draft ->
            val text = draft.text
            val lines = text.lines()
            val headline = lines.firstOrNull { it.startsWith("HEADLINE:") }?.removePrefix("HEADLINE:")?.trim() ?: "${player.name} Injury Update"
            val outlook = lines.firstOrNull { it.startsWith("SEVERITY_OUTLOOK:") }?.removePrefix("SEVERITY_OUTLOOK:")?.trim() ?: "Player is making steady progress toward full clearance."
            val nextMan = lines.firstOrNull { it.startsWith("NEXT_MAN_UP:") }?.removePrefix("NEXT_MAN_UP:")?.trim() ?: "Roster depth will be tested as rotational minutes shift."
            val quote = lines.firstOrNull { it.startsWith("QUOTE:") }?.removePrefix("QUOTE:")?.trim() ?: "We trust our medical staff and look forward to getting back at 100%."
            val analysis = InjuryAnalysis(headline, outlook, nextMan, if (draft.isAi) quote else "", draft.isAi, draft.fallbackReason)
            if (config.cacheResponses) Cache.put(cacheKey, analysis)
            analysis
        }
    }

    /** Generates an award ceremony acceptance speech and columnist media tribute for a major award winner. */
    suspend fun generateAwardSpeech(
        config: OllamaConfig,
        lg: League,
        honor: Honor,
        onToken: ((String) -> Unit)? = null,
    ): Result<AwardSpeech> = withContext(Dispatchers.IO) {
        val cacheKey = "award_${honor.award}_${honor.pid}_${honor.season}_${config.activeModelName}" + "_facts_${AiFacts.version(lg)}_${config.provider}_${config.activeModelName}"
        if (config.cacheResponses) {
            Cache.get<AwardSpeech>(cacheKey)?.let { return@withContext Result.success(it) }
        }

        val prompt = buildString {
            appendLine("Write award ceremony speech and media tribute:")
            appendLine("Award: ${honor.award}")
            appendLine("Winner: ${honor.name}")
            appendLine("Season: ${honor.season}")
            appendLine("Stats/Line: ${honor.line}")
            appendLine("Format your response EXACTLY as:")
            appendLine("AWARD_TITLE: <Award title and honoree>")
            appendLine("ACCEPTANCE_SPEECH: <Heartfelt, memorable acceptance speech from the winner>")
            appendLine("MEDIA_TRIBUTE: <Columnist editorial celebrating their historic season>")
        }

        groundedGenerate(config, lg, prompt, onToken = onToken, extraFacts = "Award ${honor.award}; winner ${honor.name}; season ${honor.season}; ${honor.line}").map { draft ->
            val text = draft.text
            val lines = text.lines()
            val title = lines.firstOrNull { it.startsWith("AWARD_TITLE:") }?.removePrefix("AWARD_TITLE:")?.trim() ?: "${honor.award} · ${honor.name}"
            val speech = lines.firstOrNull { it.startsWith("ACCEPTANCE_SPEECH:") }?.removePrefix("ACCEPTANCE_SPEECH:")?.trim()
                ?: "I want to thank my teammates, coaches, and the fans for pushing me to be my best every night."
            val tribute = lines.firstOrNull { it.startsWith("MEDIA_TRIBUTE:") }?.removePrefix("MEDIA_TRIBUTE:")?.trim()
                ?: "A legendary campaign marked by historic clutch execution and supreme leadership."
            val award = AwardSpeech(title, speech, tribute, draft.isAi, draft.fallbackReason)
            if (config.cacheResponses) Cache.put(cacheKey, award)
            award
        }
    }

    /** Generates an inspiring halftime locker room speech and second-half tactical adjustments. */
    suspend fun generateHalftimeSpeech(
        config: OllamaConfig,
        lg: League,
        g: Game,
        usScore: Int,
        themScore: Int,
        onToken: ((String) -> Unit)? = null,
    ): Result<HalftimeSpeech> = withContext(Dispatchers.IO) {
        val cacheKey = "halftime_${g.id}_${usScore}_${themScore}_${config.activeModelName}" + "_facts_${AiFacts.version(lg)}_${config.provider}_${config.activeModelName}"
        if (config.cacheResponses) {
            Cache.get<HalftimeSpeech>(cacheKey)?.let { return@withContext Result.success(it) }
        }

        val prompt = buildString {
            appendLine("Write dynamic halftime locker room speech and tactical adjustments:")
            appendLine("Score: Us $usScore, Opponent $themScore")
            appendLine("Situation: ${if (usScore > themScore) "Leading by ${usScore - themScore}" else if (usScore < themScore) "Trailing by ${themScore - usScore}" else "Tied at $usScore"}")
            appendLine("Format your response EXACTLY as:")
            appendLine("HALFTIME_SPEECH: <Inspiring coach locker room address to the team>")
            appendLine("TACTICAL_ADJUSTMENT: <Specific on-court tactical adjustments for second half>")
        }

        groundedGenerate(config, lg, prompt, onToken = onToken, extraFacts = "HALFTIME, not a final result: ${lg.user.name} $usScore-${themScore} ${lg.team(g.opp(lg.userTeamId)).name}; margin ${kotlin.math.abs(usScore - themScore)}; second half not played.").map { draft ->
            val text = draft.text
            val lines = text.lines()
            val speech = lines.firstOrNull { it.startsWith("HALFTIME_SPEECH:") }?.removePrefix("HALFTIME_SPEECH:")?.trim()
                ?: "Lock in for the final 20 minutes! Defense and rebounds decide the outcome."
            val adjustment = lines.firstOrNull { it.startsWith("TACTICAL_ADJUSTMENT:") }?.removePrefix("TACTICAL_ADJUSTMENT:")?.trim()
                ?: "Push tempo off missed shots and close out aggressively on perimeter shooters."
            val result = HalftimeSpeech(speech, adjustment, draft.isAi, draft.fallbackReason)
            if (config.cacheResponses) Cache.put(cacheKey, result)
            result
        }
    }

    /** Generates a training camp report and player development breakdown for top improvers. */
    suspend fun generateTrainingCampReport(
        config: OllamaConfig,
        lg: League,
        team: Team,
        topImprover: Player,
        delta: Int,
        onToken: ((String) -> Unit)? = null,
    ): Result<TrainingCampReport> = withContext(Dispatchers.IO) {
        val cacheKey = "camp_${team.id}_${topImprover.id}_${delta}_${lg.season}_${config.activeModelName}" + "_facts_${AiFacts.version(lg)}_${config.provider}_${config.activeModelName}"
        if (config.cacheResponses) {
            Cache.get<TrainingCampReport>(cacheKey)?.let { return@withContext Result.success(it) }
        }

        val prompt = buildString {
            appendLine("Write training camp report and player development breakdown:")
            appendLine("Team: ${team.name}")
            appendLine("Top Standout: ${topImprover.name} (${topImprover.posLabel}, age ${topImprover.age})")
            appendLine("Progression: +$delta Overall improvement")
            appendLine("Format your response EXACTLY as:")
            appendLine("HEADLINE: <Camp headline highlighting development>")
            appendLine("STANDOUT_ANALYSIS: <2-3 sentence analysis of the standout's breakthrough in camp>")
            appendLine("SEASON_OUTLOOK: <2 sentence team identity and season projection>")
        }

        groundedGenerate(config, lg, prompt, onToken = onToken, extraPlayers = listOf(topImprover), extraFacts = "Camp: ${topImprover.name} improved overall rating by $delta to ${topImprover.ovr} for ${team.name}.").map { draft ->
            val text = draft.text
            val lines = text.lines()
            val headline = lines.firstOrNull { it.startsWith("HEADLINE:") }?.removePrefix("HEADLINE:")?.trim()
                ?: "${topImprover.name} Surges in Training Camp for ${team.name}"
            val standout = lines.firstOrNull { it.startsWith("STANDOUT_ANALYSIS:") }?.removePrefix("STANDOUT_ANALYSIS:")?.trim()
                ?: "${topImprover.name} arrived at camp with revamped footwork and sharpened perimeter accuracy, showing an impressive +$delta progression."
            val outlook = lines.firstOrNull { it.startsWith("SEASON_OUTLOOK:") }?.removePrefix("SEASON_OUTLOOK:")?.trim()
                ?: "${team.name} enters the new campaign with bolstered depth and palpable chemistry on both ends of the floor."
            val report = TrainingCampReport(headline, standout, outlook, draft.isAi, draft.fallbackReason)
            if (config.cacheResponses) Cache.put(cacheKey, report)
            report
        }
    }

    data class PersonalityPass(val assigned: Int, val isAi: Boolean, val detail: String)

    /**
     * Assign on-court personalities (Confident Scorer / Unselfish) for [players].
     * Uses AI when enabled; otherwise seeds from ratings. Safe for empty lists.
     */
    suspend fun assignPlayerPersonalities(
        config: OllamaConfig,
        lg: League,
        players: List<Player>,
    ): Result<PersonalityPass> = withContext(Dispatchers.IO) {
        if (!config.enabled || !config.playerPersonalities) {
            Personality.seedRoster(players)
            return@withContext Result.success(PersonalityPass(players.count { it.has(Trait.GUNNER) || it.has(Trait.PASS_FIRST) }, false, "Rule-based seeding (AI personalities off)."))
        }
        val targets = players.filter { it.teamId >= 0 }.sortedByDescending { it.ovr }.take(12)
        if (targets.isEmpty()) return@withContext Result.success(PersonalityPass(0, false, "No roster players."))

        val rosterLines = targets.joinToString("\n") { p ->
            "- id=${p.id} ${p.name} ${p.posLabel} ovr=${p.ovr} INS=${p[Attr.INS]} MID=${p[Attr.MID]} OUT=${p[Attr.OUT]} PAS=${p[Attr.PAS]} style=${p.style.label}"
        }
        val prompt = buildString {
            appendLine("You are a basketball scout. Assign ONE on-court personality to each player.")
            appendLine("Roles: GUNNER (confident scorer who takes tough shots), PASS_FIRST (unselfish, looks for teammates), or BALANCED.")
            appendLine("Format EXACTLY one line per player:")
            appendLine("ID|ROLE|Short scout note (max 18 words)")
            appendLine("Roster:")
            appendLine(rosterLines)
        }
        val system = "Reply with only the ID|ROLE|note lines. No markdown."
        val result = generate(config, prompt, system, temperature = 0.4, numPredict = 420)
        var assigned = 0
        result.onSuccess { text ->
            for (raw in text.lines()) {
                val line = raw.trim().trimStart('-', '*', ' ')
                if (line.isEmpty() || !line.contains('|')) continue
                val parts = line.split('|').map { it.trim() }
                if (parts.size < 2) continue
                val id = parts[0].filter { it.isDigit() }.toIntOrNull() ?: continue
                val p = targets.firstOrNull { it.id == id } ?: continue
                val role = parts[1]
                val note = parts.getOrNull(2).orEmpty()
                Personality.applyAssignment(p, role, note.ifBlank {
                    when {
                        role.contains("GUN", true) || role.contains("CONF", true) -> "Looks for his own shot, even contested."
                        role.contains("PASS", true) || role.contains("UNSELF", true) -> "Willing passer who kicks tough looks."
                        else -> "Takes what the defense gives."
                    }
                })
                assigned++
            }
        }
        if (assigned == 0) {
            Personality.seedRoster(targets)
            for (p in targets) {
                if (p.personalityNote.isBlank()) {
                    p.personalityNote = when {
                        p.has(Trait.GUNNER) -> "Confident scorer who hunts his own look."
                        p.has(Trait.PASS_FIRST) -> "Unselfish — finds the open man on tough possessions."
                        else -> ""
                    }
                }
            }
            assigned = targets.count { it.has(Trait.GUNNER) || it.has(Trait.PASS_FIRST) }
            return@withContext Result.success(PersonalityPass(assigned, false, result.exceptionOrNull()?.message ?: "AI parse empty — used rule-based personalities."))
        }
        // Fill anyone the model skipped.
        for (p in targets) if (!p.has(Trait.GUNNER) && !p.has(Trait.PASS_FIRST)) Personality.seedOnCourtTrait(p)
        Result.success(PersonalityPass(assigned, result.isSuccess, "Assigned $assigned on-court personalities."))
    }
}
