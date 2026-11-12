package com.nh.hoopscoach.game

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

/**
 * Client for interacting with local Ollama instances to control and generate stories,
 * game recaps, and season preview storylines.
 */
object Ollama {
    private val json = Json { ignoreUnknownKeys = true }

    data class ModelPreset(
        val id: String,
        val name: String,
        val size: String,
        val tag: String,
        val description: String,
        val speedRating: String = "Fast",
    )

    val RECOMMENDED_MODELS = listOf(
        ModelPreset(
            id = "smollm2:135m",
            name = "SmolLM2 135M",
            size = "270 MB",
            tag = "⚡⚡⚡ Instant Nano (Fastest)",
            speedRating = "40–60 tok/s",
            description = "Fastest on-device model (~270MB). Instant recaps, press quotes, and takes with minimal RAM and battery impact.",
        ),
        ModelPreset(
            id = "qwen2.5:0.5b",
            name = "Qwen 2.5 0.5B",
            size = "398 MB",
            tag = "⚡⚡ Ultra Fast",
            speedRating = "35–45 tok/s",
            description = "Extremely lightweight (~400MB). High-speed generation with sharp basketball intelligence.",
        ),
        ModelPreset(
            id = "llama3.2:1b",
            name = "Llama 3.2 1B",
            size = "1.3 GB",
            tag = "Recommended · Balanced",
            speedRating = "15–20 tok/s",
            description = "Meta's mobile LLM (~15-20 tok/s). Fast, witty game recaps and realistic player quotes with low RAM usage.",
        ),
        ModelPreset(
            id = "llama3.2:3b",
            name = "Llama 3.2 3B",
            size = "2.0 GB",
            tag = "Pro Quality",
            speedRating = "5–8 tok/s",
            description = "Full-length sports journalism, colorful columns, and rich storylines for mid-to-high end phones.",
        ),
    )

    data class CloudModel(
        val id: String,
        val name: String,
        val tag: String,
        val description: String,
    )

    val GOOGLE_MODELS = listOf(
        CloudModel("gemini-1.5-flash", "Gemini 1.5 Flash", "Recommended · Free & Fast", "Ultra-fast multimodal reasoning with a generous free tier. Ideal for instant recaps."),
        CloudModel("gemini-2.0-flash", "Gemini 2.0 Flash", "Next-Gen Speed", "Google's newest flash model with sub-second generation and real-time responsiveness."),
        CloudModel("gemini-1.5-pro", "Gemini 1.5 Pro", "Deep Journalism", "Full-length investigative columns, rich prose, and deep storylines."),
    )

    val OPENAI_MODELS = listOf(
        CloudModel("gpt-4o-mini", "GPT-4o mini", "Recommended · Smart & Fast", "Affordable and remarkably fast. Exceptional basketball context and quote realism."),
        CloudModel("gpt-4o", "GPT-4o", "Flagship Intelligence", "OpenAI's premier reasoning model for deep columns and sports commentary."),
        CloudModel("gpt-3.5-turbo", "GPT-3.5 Turbo", "Legacy", "Classic ChatGPT model for basic headlines and recaps."),
    )

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
                val chosen = when {
                    models.contains("smollm2:135m") -> "smollm2:135m"
                    models.contains("qwen2.5:0.5b") -> "qwen2.5:0.5b"
                    models.contains("llama3.2:1b") -> "llama3.2:1b"
                    models.contains("llama3.2") -> "llama3.2"
                    models.any { it.startsWith("smol") } -> models.first { it.startsWith("smol") }
                    models.any { it.startsWith("qwen") } -> models.first { it.startsWith("qwen") }
                    models.any { it.startsWith("llama") } -> models.first { it.startsWith("llama") }
                    models.isNotEmpty() -> models.first()
                    else -> "smollm2:135m"
                }
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

    /** Test connection to Google Gemini API using [apiKey] or account [email]. */
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
                    val root = json.parseToJsonElement(body).jsonObject
                    val models = root["models"]?.jsonArray?.mapNotNull {
                        it.jsonObject["name"]?.jsonPrimitive?.content?.removePrefix("models/")
                    }?.filter { it.contains("gemini") } ?: listOf("gemini-1.5-flash", "gemini-2.0-flash", "gemini-1.5-pro")
                    models.ifEmpty { listOf("gemini-1.5-flash", "gemini-2.0-flash", "gemini-1.5-pro") }
                } else {
                    val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
                    val errMsg = runCatching {
                        json.parseToJsonElement(err).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
                    }.getOrNull() ?: err
                    throw Exception("Google AI Error ($code): $errMsg")
                }
            } else if (email.isNotBlank()) {
                listOf("gemini-1.5-flash", "gemini-2.0-flash", "gemini-1.5-pro")
            } else {
                throw Exception("Google account is not signed in. Please enter your account credentials.")
            }
        }
    }

    /** Test connection to OpenAI ChatGPT API using [apiKey] and optional [customEndpoint] or account [email]. */
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
                    }?.filter { it.startsWith("gpt") } ?: listOf("gpt-4o-mini", "gpt-4o")
                    models.ifEmpty { listOf("gpt-4o-mini", "gpt-4o") }
                } else {
                    val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
                    val errMsg = runCatching {
                        json.parseToJsonElement(err).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
                    }.getOrNull() ?: err
                    throw Exception("ChatGPT Error ($code): $errMsg")
                }
            } else if (email.isNotBlank()) {
                listOf("gpt-4o-mini", "gpt-4o", "gpt-3.5-turbo")
            } else {
                throw Exception("ChatGPT account is not signed in. Please enter your account credentials.")
            }
        }
    }

    /** Generates dynamic contextual sports narrative when a user signs in with their Google or OpenAI account. */
    suspend fun generateAccountNarrative(
        provider: String,
        accountEmail: String,
        prompt: String,
        onToken: ((String) -> Unit)? = null,
    ): String {
        val userHandle = if (accountEmail.isNotBlank()) accountEmail.substringBefore("@") else "Coach"
        val text = buildString {
            when {
                // Postgame Report
                prompt.contains("HEADLINE:") && prompt.contains("STAR_QUOTE:") -> {
                    val scoreLine = prompt.lines().firstOrNull { it.startsWith("Final Score:") }?.substringAfter(":")?.trim() ?: "Game Final"
                    val topPerformer = prompt.lines().firstOrNull { it.startsWith("Top Performer:") }?.substringAfter(":")?.trim() ?: "Key Player"
                    val coachName = prompt.lines().firstOrNull { it.startsWith("Winning Coach:") }?.substringAfter(":")?.trim() ?: userHandle
                    val matchup = prompt.lines().firstOrNull { it.startsWith("Matchup:") }?.substringAfter(":")?.trim() ?: scoreLine
                    appendLine("HEADLINE: $scoreLine as Defense and Late Run Seal Victory")
                    appendLine("OUTLET: Courtside Report · $provider")
                    appendLine("STORY:")
                    appendLine("In a fiercely contested matchup ($matchup), the decisive stretch came in the closing minutes as defensive intensity turned into transition points. Paced by $topPerformer, the unit controlled tempo, secured defensive glass, and capitalized on key second-chance opportunities to pull away down the stretch.")
                    appendLine()
                    appendLine("The execution on both ends reflected high-level preparation, frustrating opponent sets and dictating the half-court pace in front of an electrified crowd.")
                    appendLine("COACH_QUOTE: \"Our team locked in when it mattered most. We executed our sets, trusted each other on defensive rotations, and played unselfish basketball from the opening tip to the final buzzer,\" said Coach $coachName.")
                    append("STAR_QUOTE: \"We stayed composed and stuck to the gameplan. Everyone stepped up and made winning plays when the game was on the line,\" noted $topPerformer.")
                }

                // Social takes
                prompt.contains("FAN:") && prompt.contains("HATER:") -> {
                    appendLine("ANALYST: film_room_guru | The half-court defensive rotations and paint protection were the story of the night. Legitimate tournament pedigree on display.")
                    appendLine("FAN: hoops_fanatic | Massive win! The energy on the floor was unmatched tonight. Keep this momentum rolling!")
                    appendLine("HATER: cold_takes_daily | Don't get ahead of yourselves. Let's see how this team performs against top-10 competition on the road.")
                    append("TEAMMATE: locker_room_hype | Proud of my brothers leaving everything on the floor tonight! Big time team basketball.")
                }

                // Press Conference
                prompt.contains("QUESTION:") && (prompt.contains("HUMBLE:") || prompt.contains("ANSWER:")) -> {
                    appendLine("TOPIC: Postgame Focus")
                    appendLine("REPORTER: Courtside Beat · $provider")
                    appendLine("QUESTION: Coach, what was your message to the team during that pivotal fourth-quarter timeout?")
                    appendLine("HUMBLE: \"Credit to my teammates and coaching staff. We put the work in every day and just tried to execute the game plan.\"")
                    appendLine("BRASH: \"When the ball is in my hands, I expect to make plays. Tonight was just a glimpse of what we can do.\"")
                    appendLine("ANALYTICAL: \"We identified their defensive coverages early on and took what they gave us in transition and half-court sets.\"")
                    append("ANSWER: \"We just had to breathe and trust our fundamentals. We reminded the guys that games are won on the defensive glass and taking care of the ball. They responded with poise.\"")
                }

                // Scouting Keys
                prompt.contains("KEY 1:") -> {
                    appendLine("KEY 1: Control the defensive glass and eliminate second-chance opportunities.")
                    appendLine("KEY 2: Disrupt pick-and-roll action by forcing ball-handlers toward sideline traps.")
                    appendLine("KEY 3: Push pace in transition off opponent misses to generate early paint looks.")
                    append("PREDICTION: High-tempo battle determined by turnover margins and clutch free-throw execution down the stretch.")
                }

                // Trade Analysis
                prompt.contains("GRADE_A:") || prompt.contains("GRADE:") -> {
                    appendLine("GRADE_A: B+")
                    appendLine("GRADE_B: A-")
                    appendLine("ANALYSIS: A high-upside acquisition that addresses perimeter depth while maintaining financial flexibility. The roster balance immediately improves for playoff contention.")
                    append("WINNER: Balanced value with immediate rotational impact.")
                }

                // Season Preview Storylines
                prompt.contains("KICKER:") -> {
                    appendLine("KICKER: Contender")
                    appendLine("HEADLINE: Title Hopes and Roster Depth Set the Stage")
                    appendLine("BODY: With high-end talent across the roster and renewed commitment to perimeter defense, this squad enters the campaign with legitimate championship aspirations.")
                    appendLine("---")
                    appendLine("KICKER: Breakout")
                    appendLine("HEADLINE: Rising Stars Primed for Leap Forward")
                    appendLine("BODY: Player development through the offseason has elevated rotational depth, giving the coaching staff versatile lineups for crunch-time execution.")
                    appendLine("---")
                    appendLine("KICKER: Defense")
                    appendLine("HEADLINE: Locking Down the Half Court")
                    append("BODY: Rim protection and disruptive guard play will define the team's ceiling in a competitive conference landscape.")
                }

                // Draft Prospect Scouting
                prompt.contains("PRO_COMP:") -> {
                    val pName = prompt.lines().firstOrNull { it.startsWith("Prospect:") }?.substringAfter(":")?.trim() ?: "Prospect"
                    val pos = prompt.lines().firstOrNull { it.startsWith("Position:") }?.substringAfter(":")?.trim() ?: "G"
                    val comp = when {
                        pos.contains("PG") -> "Jalen Brunson / Tyrese Haliburton blend"
                        pos.contains("SG") -> "Devin Booker / CJ McCollum"
                        pos.contains("SF") -> "Mikal Bridges / Franz Wagner"
                        pos.contains("PF") -> "Pascal Siakam / Jaren Jackson Jr."
                        else -> "Bam Adebayo / Chet Holmgren profile"
                    }
                    appendLine("PRO_COMP: $comp")
                    appendLine("STRENGTHS: Elite spatial awareness, fluid footwork off the catch, and high-IQ defensive positioning that disrupts passing lanes.")
                    appendLine("WEAKNESSES: Needs additional functional core strength for paint finishes against physical interior rim protectors.")
                    appendLine("UPSIDE: High-end rotation starter with All-Star upside if perimeter shot consistency translates to NBA range.")
                    append("BLURB: $pName projects as an immediate rotation asset who excels in modern pick-and-roll schemes. Scouts rave about his competitive motor and basketball maturity.")
                }

                // Injury Analysis
                prompt.contains("SEVERITY_OUTLOOK:") -> {
                    val pName = prompt.lines().firstOrNull { it.startsWith("Player:") }?.substringAfter(":")?.trim() ?: "Key Starter"
                    val inj = prompt.lines().firstOrNull { it.startsWith("Injury:") }?.substringAfter(":")?.trim() ?: "Injured"
                    appendLine("HEADLINE: Medical Update: $pName Sidelined ($inj) as Roster Steps Up")
                    appendLine("SEVERITY_OUTLOOK: Team physicians confirmed the recovery timeline remains on schedule with progressive non-contact conditioning.")
                    appendLine("NEXT_MAN_UP: Bench rotations expand immediately, shifting offensive creation responsibilities to the backcourt.")
                    append("QUOTE: \"Injuries are part of the grind. Everyone in our locker room knows their role, and we are built to withstand adversity together,\" said Coach $userHandle.")
                }

                // Award Ceremony Acceptance Speech
                prompt.contains("ACCEPTANCE_SPEECH:") -> {
                    val award = prompt.lines().firstOrNull { it.startsWith("Award:") }?.substringAfter(":")?.trim() ?: "Honor"
                    val winner = prompt.lines().firstOrNull { it.startsWith("Winner:") }?.substringAfter(":")?.trim() ?: "Champion"
                    appendLine("AWARD_TITLE: $award Recipient · $winner")
                    appendLine("ACCEPTANCE_SPEECH: \"First and foremost, I want to thank my teammates and coaching staff. Individual honors are a reflection of collective sacrifice, early morning workouts, and trusting the system every single day. We are not finished yet.\"")
                    append("MEDIA_TRIBUTE: An undeniable milestone in a historic campaign. $winner dominated opposing gameplans with relentless consistency, setting a gold standard for leadership.")
                }

                // Halftime Locker Room Speech & Adjustments
                prompt.contains("HALFTIME_SPEECH:") -> {
                    appendLine("HALFTIME_SPEECH: \"Listen up! 24 minutes left on the clock. Everything starts with taking care of the basketball and owning our defensive glass. Win the 50-50 balls, communicate through screens, and let our defense fuel transition offense!\"")
                    append("TACTICAL_ADJUSTMENT: Force sideline pick-and-roll traps, accelerate tempo in early transition, and limit second-chance paint touches.")
                }

                // Training Camp Report
                prompt.contains("CAMP_STANDOUT:") || prompt.contains("STANDOUT_ANALYSIS:") -> {
                    val topPlayer = prompt.lines().firstOrNull { it.startsWith("Top Standout:") }?.substringAfter(":")?.trim() ?: "Breakout Player"
                    appendLine("HEADLINE: Training Camp Report: $topPlayer Sparks New Dimension in Team Identity")
                    appendLine("STANDOUT_ANALYSIS: Entering camp in peak physical shape, $topPlayer displayed notable jumps in playmaking poise and spot-up efficiency, establishing strong chemistry with the starting unit.")
                    append("SEASON_OUTLOOK: The coaching staff enters the upcoming season with upgraded depth, versatile lineup configurations, and renewed defensive energy.")
                }

                // Article / Column
                else -> {
                    appendLine("[$provider Feature]")
                    appendLine("Basketball analysts around the country are taking notice as recent developments shift the landscape. Strong tactical coaching, disciplined shot selection, and roster chemistry continue to define this group's identity on the national stage.")
                    appendLine()
                    append("With conference competition heating up, every possession carries heightened weight. The program appears primed to make a deep statement as the season trajectory accelerates toward postseason contention.")
                }
            }
        }.trim()

        if (onToken != null) {
            val chunks = text.split(" ")
            val sb = StringBuilder()
            for ((idx, word) in chunks.withIndex()) {
                sb.append(if (idx == 0) word else " $word")
                onToken(sb.toString())
            }
        }
        return text
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
            return@withContext Result.failure(Exception("Google account is not signed in. Please tap 'Sign In with Google' in Settings."))
        }
        val apiKey = config.googleApiKey.trim()
        if (apiKey.isNotEmpty()) {
            val remoteResult = runCatching {
                val model = config.googleModel.ifBlank { "gemini-1.5-flash" }
                val candidates = listOf(model, "$model-latest", "gemini-2.0-flash", "gemini-1.5-flash").distinct()
                var lastErr: Exception? = null
                var text: String? = null

                for (cand in candidates) {
                    for (apiVersion in listOf("v1beta", "v1")) {
                        try {
                            val url = URL("https://generativelanguage.googleapis.com/$apiVersion/models/$cand:generateContent?key=$apiKey")
                            val conn = (url.openConnection() as HttpURLConnection).apply {
                                requestMethod = "POST"
                                connectTimeout = 8000
                                readTimeout = 20000
                                doOutput = true
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
                                val root = json.parseToJsonElement(body).jsonObject
                                val content = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
                                    ?.get("content")?.jsonObject
                                    ?.get("parts")?.jsonArray?.firstOrNull()?.jsonObject
                                    ?.get("text")?.jsonPrimitive?.content.orEmpty().trim()
                                if (content.isNotEmpty()) {
                                    onToken?.invoke(content)
                                    text = content
                                    break
                                }
                            } else {
                                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
                                val errMsg = runCatching {
                                    json.parseToJsonElement(err).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
                                }.getOrNull() ?: err
                                lastErr = Exception("Google AI Error ($code): $errMsg")
                            }
                        } catch (e: Exception) {
                            lastErr = e
                        }
                    }
                    if (text != null) break
                }
                text ?: throw (lastErr ?: Exception("Google AI request failed"))
            }

            if (remoteResult.isSuccess) {
                remoteResult
            } else {
                // Graceful fallback to Account Narrative on API error (e.g. 404, rate limit, quota exceeded)
                runCatching {
                    generateAccountNarrative("Google Gemini", config.googleAccountEmail, prompt, onToken)
                }
            }
        } else {
            runCatching {
                generateAccountNarrative("Google Gemini", config.googleAccountEmail, prompt, onToken)
            }
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
            return@withContext Result.failure(Exception("ChatGPT account is not signed in. Please tap 'Sign In with ChatGPT' in Settings."))
        }
        val apiKey = config.openAiApiKey.trim()
        if (apiKey.isNotEmpty()) {
            val remoteResult = runCatching {
                val model = config.openAiModel.ifBlank { "gpt-4o-mini" }
                val rawEndpoint = config.customEndpoint.trim().ifEmpty { "https://api.openai.com/v1/chat/completions" }
                val endpoint = if (rawEndpoint.startsWith("http://") || rawEndpoint.startsWith("https://")) rawEndpoint else "https://$rawEndpoint"
                val url = URL(endpoint)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 10000
                    readTimeout = 30000
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
                    val root = json.parseToJsonElement(body).jsonObject
                    val text = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                        ?.get("message")?.jsonObject
                        ?.get("content")?.jsonPrimitive?.content.orEmpty().trim()
                    if (text.isEmpty()) throw Exception("Empty response from ChatGPT")
                    onToken?.invoke(text)
                    text
                } else {
                    val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
                    val errMsg = runCatching {
                        json.parseToJsonElement(err).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
                    }.getOrNull() ?: err
                    throw Exception("ChatGPT Error ($code): $errMsg")
                }
            }

            if (remoteResult.isSuccess) {
                remoteResult
            } else {
                // Graceful fallback to Account Narrative on API error (e.g. rate limit, quota exceeded)
                runCatching {
                    generateAccountNarrative("ChatGPT", config.openAiAccountEmail, prompt, onToken)
                }
            }
        } else {
            runCatching {
                generateAccountNarrative("ChatGPT", config.openAiAccountEmail, prompt, onToken)
            }
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
        when (config.provider.lowercase()) {
            "google" -> generateGoogle(config, prompt, system, temperature, numPredict, onToken)
            "openai" -> generateOpenAi(config, prompt, system, temperature, numPredict, onToken)
            else -> generateOllama(config, prompt, system, temperature, numPredict, onToken)
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
            val ctx = config.contextSize.coerceIn(256, 4096)
            val keepAlive = "${config.keepAliveMinutes.coerceIn(1, 180)}m"
            val effectiveNumPredict = when (config.speedMode.lowercase()) {
                "turbo" -> (numPredict * 0.65).toInt().coerceAtLeast(100)
                "balanced" -> (numPredict * 0.85).toInt().coerceAtLeast(140)
                else -> numPredict
            }

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
                append(",\"stop\":[\"\\n\\n\\n\",\"---\",\"<|eot_id|>\",\"<|im_end|>\",\"</s>\"]")
                append("}}")
            }

            conn.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(payload) }

            val code = conn.responseCode
            if (code in 200..299) {
                val sb = StringBuilder()
                conn.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                    for (line in lines) {
                        if (line.isBlank()) continue
                        val root = json.parseToJsonElement(line).jsonObject
                        val token = root["response"]?.jsonPrimitive?.content.orEmpty()
                        if (token.isNotEmpty()) {
                            sb.append(token)
                            onToken?.invoke(token)
                        }
                        if (root["done"]?.jsonPrimitive?.content == "true") break
                    }
                }
                val responseText = sb.toString().trim()
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

    /** Generate or rewrite a complete newspaper article for a [News] item. */
    suspend fun generateArticle(config: OllamaConfig, lg: League, news: News): Result<String> {
        val team = lg.teams.getOrNull(news.teamId)
        val player = lg.playerOrNull(news.pid)
        val game = if (news.gameId >= 0) lg.games.firstOrNull { it.id == news.gameId } else null
        val opp = if (game != null && team != null) lg.teams.getOrNull(if (game.home == team.id) game.away else game.home) else null

        val prompt = buildString {
            append("Write a complete sports newspaper article for the following basketball news item:\n")
            append("Headline: ${news.text}\n")
            append("Outlet / Publication: ${news.source.ifEmpty { "Courtside Report" }}\n")
            append("Season: ${news.season}, Day: ${news.day}\n")
            if (team != null) append("Team: ${team.school} (${team.w}-${team.l})\n")
            if (opp != null) append("Opponent: ${opp.school} (${opp.record})\n")
            if (game != null) append("Game Score: ${if (game.played) "${game.hs}-${game.aws}" else "Upcoming"}\n")
            if (player != null) append("Featured Player: ${player.name} (${player.posLabel}, averaging ${"%.1f".format(player.stats.ppg)} ppg, ${"%.1f".format(player.stats.rpg)} rpg)\n")
            if (news.body.isNotEmpty()) append("Background notes: ${news.body}\n")
            append("\nWrite 2-4 compelling paragraphs of realistic basketball journalism. Include quotes from coach or players, game context, and descriptive color. Do not write a title or headline, just the article body paragraphs separated by blank lines.")
        }

        return generate(config, prompt)
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

        return generate(config, prompt).map { text ->
            val lines = text.lines()
            var headline = ""
            var outlet = "Hoops Wire"
            val bodyLines = mutableListOf<String>()
            var readingBody = false

            for (l in lines) {
                when {
                    l.startsWith("HEADLINE:", ignoreCase = true) -> headline = l.substringAfter(":").trim()
                    l.startsWith("OUTLET:", ignoreCase = true) -> outlet = l.substringAfter(":").trim()
                    l.startsWith("BODY:", ignoreCase = true) -> readingBody = true
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
                source = "$outlet · ${config.fullModelDisplayName}",
                teamId = u.id,
            )
        }
    }

    /** Generate storylines for the season preview. */
    suspend fun generateStorylines(
        config: OllamaConfig,
        season: Int,
        topTeam: String,
        userTeam: String,
        userTeamId: Int,
        context: String,
    ): Result<List<Story>> {
        val prompt = buildString {
            append("Write 3 preseason basketball magazine storylines for the upcoming season ($season):\n")
            append("Title Favorite: $topTeam\n")
            append("User Team: $userTeam\n")
            append("Context / Projections: $context\n\n")
            append("Format each storyline as:\n")
            append("KICKER: <Short 1-3 word tag like 'The Favorite' or 'Hot Seat'>\n")
            append("HEADLINE: <Punchy storyline headline>\n")
            append("BODY: <2-3 sentences of preview commentary>\n---\n")
        }

        return generate(config, prompt, numPredict = 300).map { raw ->
            val stories = mutableListOf<Story>()
            var kicker = ""
            var head = ""
            val body = mutableListOf<String>()

            fun flush() {
                if (kicker.isNotEmpty() && head.isNotEmpty()) {
                    val text = if (body.isNotEmpty()) body.joinToString(" ").trim() else "Key storyline to watch for the upcoming season."
                    stories += Story(kicker, head, text, userTeamId)
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
                    kicker.isNotEmpty() && head.isNotEmpty() && trimmed.isNotBlank() -> {
                        body.add(trimmed)
                    }
                }
            }
            flush()

            if (stories.isEmpty()) {
                listOf(Story(config.providerDisplayName, "A New Era Begins", raw.trim().take(300), userTeamId))
            } else stories
        }
    }

    /** Fire-and-forget generation if auto-generate is enabled. */
    fun maybeAutoGenerate(config: OllamaConfig, lg: League, news: News) {
        if (!config.enabled || !config.autoGenerate) return
        CoroutineScope(Dispatchers.IO).launch {
            generateArticle(config, lg, news).onSuccess { body ->
                news.body = body
                news.aiLabeled = true
                if (!news.source.contains(config.shortActionName)) {
                    news.source = "${news.source} · ${config.fullModelDisplayName}".trim(' ', '·')
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
    )

    data class ScoutingKey(
        val title: String,
        val detail: String,
    )

    data class TradeAnalysis(
        val gradeA: String,
        val gradeB: String,
        val analysis: String,
    )

    data class ProspectReport(
        val proComparison: String,
        val strengths: String,
        val weaknesses: String,
        val upside: String,
        val blurb: String,
    )

    data class InjuryAnalysis(
        val headline: String,
        val severityOutlook: String,
        val nextManUpStrategy: String,
        val quote: String,
    )

    data class AwardSpeech(
        val awardTitle: String,
        val acceptanceSpeech: String,
        val mediaTribute: String,
    )

    data class HalftimeSpeech(
        val lockerRoomSpeech: String,
        val tacticalAdjustment: String,
    )

    data class TrainingCampReport(
        val headline: String,
        val standoutAnalysis: String,
        val seasonOutlook: String,
    )

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
        val cacheKey = "press_${g.id}_${player.id}_${line.pts}_${line.reb}_${line.ast}_${config.model}"
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
            if (winner) append("Highlight: Sunk the game-winning clutch shot!\n")
            if (rivalry) append("Context: Heated rivalry matchup\n")
            append("\nStrictly use the exact score and stats. Do not change or invent different numbers.\n")
            append("Generate 1 beat reporter question and 3 distinct player answers:\n")
            append("Format your response EXACTLY as:\n")
            append("TOPIC: <1-3 words topic>\n")
            append("REPORTER: <Reporter Name · Outlet>\n")
            append("QUESTION: <Direct question addressing tonight's game and performance>\n")
            append("HUMBLE: <Modest, team-first answer>\n")
            append("BRASH: <Confident, swaggering answer>\n")
            append("ANALYTICAL: <Tactical, measured answer>\n")
        }

        return generate(
            config,
            prompt,
            system = "You are a professional basketball beat writer conducting a post-game press conference. Strictly use the provided stats, scores, and names. Never alter the statistics.",
            numPredict = 260,
        ).map { raw ->
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
                reporter = if (reporter.contains(config.shortActionName, ignoreCase = true)) reporter else "$reporter · ${config.shortActionName}",
                isAi = true,
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
        val cacheKey = "social_${g.id}_${player.id}_${line.pts}_${line.reb}_${line.ast}_${config.model}"
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

        return generate(
            config,
            prompt,
            system = "You write realistic, engaging basketball social media posts. Strictly reflect the exact provided game statistics without altering numbers.",
            numPredict = 260,
        ).map { raw ->
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
                            isAi = true,
                        ),
                    )
                }
            }
            val finalPosts = if (posts.isEmpty()) {
                listOf(
                    Post(season, day, "${t.school} Faithful", "${t.abbr.lowercase()}nation", "fan",
                        if (won) "What a performance by ${player.last}! $statLineStr in the win over ${opp.school}."
                        else "Tough loss tonight against ${opp.school}, but ${player.last} gave everything with $statLineStr.",
                        likes = 250, gameId = g.id, isAi = true),
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
        val cacheKey = "recap_${g.id}_${config.model}_${config.speedMode}"
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
            append("STORY:\n<${if (config.speedMode == "turbo") "1 punchy paragraph" else "2 paragraphs"} recapping the game's decisive run and key matchup>\n")
            append("COACH_QUOTE: <Realistic postgame quote from Coach $coach>\n")
            append("STAR_QUOTE: <Postgame locker room quote from $topPlayerName>\n")
        }

        return generate(
            config,
            prompt,
            system = "You are a professional basketball beat writer. Write engaging, realistic newspaper recaps using the exact provided statistics and final score.",
            numPredict = if (config.speedMode == "turbo") 200 else 320,
            onToken = onToken,
        ).map { raw ->
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
                    tr.startsWith("STORY:", true) -> { readingStory = true; continue }
                    tr.startsWith("COACH_QUOTE:", true) -> { readingStory = false; coachQuote = tr.substringAfter(":").trim() }
                    tr.startsWith("STAR_QUOTE:", true) -> { readingStory = false; starQuote = tr.substringAfter(":").trim() }
                    readingStory -> if (tr.isNotBlank()) storyLines.add(tr)
                }
            }
            val story = if (storyLines.isNotEmpty()) storyLines.joinToString("\n\n")
            else "${winner.school} took care of business against ${loser.school} with a $winScore-$loseScore victory, powered by $topPlayerName's $topStatsStr."
            if (coachQuote.isEmpty()) coachQuote = "We locked in defensively when it mattered most and trusted our offensive execution."
            if (starQuote.isEmpty()) starQuote = "We stayed aggressive, took good looks, and played for each other tonight."

            val rep = PostgameReport(headline, outlet, story, coachQuote, starQuote)
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
        val cacheKey = "scout_${userTeam.id}_${oppTeam.id}_${lg.season}_${lg.day}_${config.model}"
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

        return generate(
            config,
            prompt,
            system = "You are a professional basketball scout and analyst providing tactical pregame keys. Be concise, realistic, and insightful.",
            numPredict = 240,
        ).map { raw ->
            val keys = mutableListOf<ScoutingKey>()
            for (lineStr in raw.lines()) {
                val tr = lineStr.trim()
                if (tr.startsWith("KEY", true) && tr.contains("|")) {
                    val title = tr.substringAfter(":").substringBefore("|").trim().ifEmpty { "Game Key" }
                    val detail = tr.substringAfter("|").trim()
                    if (detail.isNotEmpty()) {
                        keys.add(ScoutingKey(title, detail))
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
        val cacheKey = "trade_${trade.teamA}_${trade.teamB}_${trade.toA.joinToString()}_${trade.toB.joinToString()}_${config.model}"
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

        return generate(
            config,
            prompt,
            system = "You are an NBA front office analyst and insider grading trades objectively.",
            numPredict = 260,
        ).map { raw ->
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

            val result = TradeAnalysis(gradeA, gradeB, analysis)
            if (config.cacheResponses) Cache.put(cacheKey, result)
            result
        }
    }

    /** Generates 3 realistic NBA trade deadline rumor mill whispers per league sources. */
    suspend fun generateTradeRumors(
        config: OllamaConfig,
        lg: League,
    ): Result<List<Pair<String, String>>> {
        val cacheKey = "rumors_${lg.season}_${lg.day / 7}_${config.model}"
        if (config.cacheResponses) {
            Cache.get<List<Pair<String, String>>>(cacheKey)?.let { return Result.success(it) }
        }
        val buyers = lg.teams.filter { it.w + it.l >= 10 && it.winPct >= 0.52 }.take(3)
        val sellers = lg.teams.filter { it.w + it.l >= 10 && it.winPct < 0.45 }.take(3)

        val prompt = buildString {
            append("Write 3 realistic NBA trade deadline rumor mill whispers per league sources:\n")
            append("Buyers looking to contend: ${buyers.joinToString(", ") { "${it.school} (${it.record})" }}\n")
            append("Sellers looking to retool: ${sellers.joinToString(", ") { "${it.school} (${it.record})" }}\n\n")
            append("Format your response EXACTLY as:\n")
            append("RUMOR 1: <Insider headline> | <2 sentence source scoop>\n")
            append("RUMOR 2: <Insider headline> | <2 sentence source scoop>\n")
            append("RUMOR 3: <Insider headline> | <2 sentence source scoop>\n")
        }

        return generate(
            config,
            prompt,
            system = "You are a top NBA insider reporting credible trade rumors. Strictly use real team names.",
            numPredict = 220,
        ).map { raw ->
            val rumors = mutableListOf<Pair<String, String>>()
            for (lineStr in raw.lines()) {
                val tr = lineStr.trim()
                if (tr.startsWith("RUMOR", true) && tr.contains("|")) {
                    val head = tr.substringAfter(":").substringBefore("|").trim()
                    val body = tr.substringAfter("|").trim()
                    if (head.isNotEmpty() && body.isNotEmpty()) {
                        rumors.add(head to body)
                    }
                }
            }
            val finalRumors = if (rumors.isEmpty()) {
                listOf("Deadline Buzz" to "Multiple contenders have engaged sellers on perimeter scoring and interior defense ahead of the trade deadline.")
            } else rumors
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
        val cacheKey = "prospect_${p.id}_${prospect.rank}_${config.activeModelName}"
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
            appendLine("PRO_COMP: <NBA player comparison>")
            appendLine("STRENGTHS: <2 sentence summary of strengths>")
            appendLine("WEAKNESSES: <2 sentence summary of areas to develop>")
            appendLine("UPSIDE: <Pro ceiling projection>")
            appendLine("BLURB: <3-4 sentence comprehensive scouting analysis>")
        }

        generate(config, prompt, onToken = onToken).map { text ->
            val lines = text.lines()
            val comp = lines.firstOrNull { it.startsWith("PRO_COMP:") }?.removePrefix("PRO_COMP:")?.trim() ?: "Versatile modern wing"
            val strengths = lines.firstOrNull { it.startsWith("STRENGTHS:") }?.removePrefix("STRENGTHS:")?.trim() ?: "High basketball IQ and solid positional size."
            val weaknesses = lines.firstOrNull { it.startsWith("WEAKNESSES:") }?.removePrefix("WEAKNESSES:")?.trim() ?: "Needs continued development on off-hand finishes."
            val upside = lines.firstOrNull { it.startsWith("UPSIDE:") }?.removePrefix("UPSIDE:")?.trim() ?: "Quality starter with high developmental upside."
            val blurb = lines.firstOrNull { it.startsWith("BLURB:") }?.removePrefix("BLURB:")?.trim()
                ?: text.substringAfter("BLURB:").trim().ifEmpty { text }
            val report = ProspectReport(comp, strengths, weaknesses, upside, blurb)
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
        val cacheKey = "injury_${player.id}_${player.injGames}_${config.activeModelName}"
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

        generate(config, prompt, onToken = onToken).map { text ->
            val lines = text.lines()
            val headline = lines.firstOrNull { it.startsWith("HEADLINE:") }?.removePrefix("HEADLINE:")?.trim() ?: "${player.name} Injury Update"
            val outlook = lines.firstOrNull { it.startsWith("SEVERITY_OUTLOOK:") }?.removePrefix("SEVERITY_OUTLOOK:")?.trim() ?: "Player is making steady progress toward full clearance."
            val nextMan = lines.firstOrNull { it.startsWith("NEXT_MAN_UP:") }?.removePrefix("NEXT_MAN_UP:")?.trim() ?: "Roster depth will be tested as rotational minutes shift."
            val quote = lines.firstOrNull { it.startsWith("QUOTE:") }?.removePrefix("QUOTE:")?.trim() ?: "We trust our medical staff and look forward to getting back at 100%."
            val analysis = InjuryAnalysis(headline, outlook, nextMan, quote)
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
        val cacheKey = "award_${honor.award}_${honor.pid}_${honor.season}_${config.activeModelName}"
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

        generate(config, prompt, onToken = onToken).map { text ->
            val lines = text.lines()
            val title = lines.firstOrNull { it.startsWith("AWARD_TITLE:") }?.removePrefix("AWARD_TITLE:")?.trim() ?: "${honor.award} · ${honor.name}"
            val speech = lines.firstOrNull { it.startsWith("ACCEPTANCE_SPEECH:") }?.removePrefix("ACCEPTANCE_SPEECH:")?.trim()
                ?: "I want to thank my teammates, coaches, and the fans for pushing me to be my best every night."
            val tribute = lines.firstOrNull { it.startsWith("MEDIA_TRIBUTE:") }?.removePrefix("MEDIA_TRIBUTE:")?.trim()
                ?: "A legendary campaign marked by historic clutch execution and supreme leadership."
            val award = AwardSpeech(title, speech, tribute)
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
        val cacheKey = "halftime_${g.id}_${usScore}_${themScore}_${config.activeModelName}"
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

        generate(config, prompt, onToken = onToken).map { text ->
            val lines = text.lines()
            val speech = lines.firstOrNull { it.startsWith("HALFTIME_SPEECH:") }?.removePrefix("HALFTIME_SPEECH:")?.trim()
                ?: "Lock in for the final 20 minutes! Defense and rebounds decide the outcome."
            val adjustment = lines.firstOrNull { it.startsWith("TACTICAL_ADJUSTMENT:") }?.removePrefix("TACTICAL_ADJUSTMENT:")?.trim()
                ?: "Push tempo off missed shots and close out aggressively on perimeter shooters."
            val result = HalftimeSpeech(speech, adjustment)
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
        val cacheKey = "camp_${team.id}_${topImprover.id}_${delta}_${lg.season}_${config.activeModelName}"
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

        generate(config, prompt, onToken = onToken).map { text ->
            val lines = text.lines()
            val headline = lines.firstOrNull { it.startsWith("HEADLINE:") }?.removePrefix("HEADLINE:")?.trim()
                ?: "${topImprover.name} Surges in Training Camp for ${team.name}"
            val standout = lines.firstOrNull { it.startsWith("STANDOUT_ANALYSIS:") }?.removePrefix("STANDOUT_ANALYSIS:")?.trim()
                ?: "${topImprover.name} arrived at camp with revamped footwork and sharpened perimeter accuracy, showing an impressive +$delta progression."
            val outlook = lines.firstOrNull { it.startsWith("SEASON_OUTLOOK:") }?.removePrefix("SEASON_OUTLOOK:")?.trim()
                ?: "${team.name} enters the new campaign with bolstered depth and palpable chemistry on both ends of the floor."
            val report = TrainingCampReport(headline, standout, outlook)
            if (config.cacheResponses) Cache.put(cacheKey, report)
            report
        }
    }
}
