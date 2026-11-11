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
    )

    val RECOMMENDED_MODELS = listOf(
        ModelPreset(
            id = "llama3.2:1b",
            name = "Llama 3.2 1B",
            size = "1.3 GB",
            tag = "Recommended · Phone",
            description = "Meta's lightweight mobile model. Fast, witty game recaps and realistic player quotes with low RAM usage.",
        ),
        ModelPreset(
            id = "qwen2.5:0.5b",
            name = "Qwen 2.5 0.5B",
            size = "398 MB",
            tag = "Ultra-Lightweight",
            description = "Extremely lightweight (~400MB). Runs smoothly on all Android phones with minimal battery and memory impact.",
        ),
        ModelPreset(
            id = "smollm2:135m",
            name = "SmolLM2 135M",
            size = "270 MB",
            tag = "Nano · Instant",
            description = "Smallest footprint (~270MB). Instant headline and recap generation with near-zero memory footprint.",
        ),
        ModelPreset(
            id = "llama3.2:3b",
            name = "Llama 3.2 3B",
            size = "2.0 GB",
            tag = "Pro Quality",
            description = "Full-length sports journalism, colorful columns, and rich storylines for mid-to-high end phones.",
        ),
    )

    data class AutoConfigResult(
        val host: String,
        val models: List<String>,
        val selectedModel: String,
        val message: String,
    )

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
                    models.contains("llama3.2:1b") -> "llama3.2:1b"
                    models.contains("llama3.2") -> "llama3.2"
                    models.contains("qwen2.5:0.5b") -> "qwen2.5:0.5b"
                    models.contains("smollm2:135m") -> "smollm2:135m"
                    models.any { it.startsWith("llama") } -> models.first { it.startsWith("llama") }
                    models.isNotEmpty() -> models.first()
                    else -> "llama3.2:1b"
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

    /** Low-level generate call using Ollama's /api/generate endpoint with streaming to prevent socket timeouts. */
    suspend fun generate(
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

            val payload = buildString {
                append("{\"model\":")
                append(jsonString(config.model.ifBlank { "llama3.2:1b" }))
                append(",\"prompt\":")
                append(jsonString(prompt))
                append(",\"system\":")
                append(jsonString(system))
                append(",\"stream\":true")
                append(",\"options\":{\"temperature\":$temperature,\"num_predict\":$numPredict}}")
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
                source = "$outlet · Ollama ${config.model}",
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
                listOf(Story("Ollama Spotlight", "A New Era Begins", raw.trim().take(300), userTeamId))
            } else stories
        }
    }

    /** Fire-and-forget generation if auto-generate is enabled. */
    fun maybeAutoGenerate(config: OllamaConfig, lg: League, news: News) {
        if (!config.enabled || !config.autoGenerate) return
        CoroutineScope(Dispatchers.IO).launch {
            generateArticle(config, lg, news).onSuccess { body ->
                news.body = body
                if (!news.source.contains("Ollama")) {
                    news.source = "${news.source} · Ollama ${config.model}".trim(' ', '·')
                }
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

            PressQuestion(
                season = lg.season,
                gameId = g.id,
                topic = topic,
                prompt = question,
                answers = listOf(
                    PressAnswer(humble, followers = 0.015, fans = 2, teammates = 3, persona = -1),
                    PressAnswer(brash, followers = 0.04, fans = 0, teammates = -2, persona = 2),
                    PressAnswer(analytical, followers = 0.008, fans = 1, teammates = 1, persona = 0),
                ),
                reporter = reporter,
            )
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
                        ),
                    )
                }
            }
            if (posts.isEmpty()) {
                listOf(
                    Post(season, day, "${t.school} Faithful", "${t.abbr.lowercase()}nation", "fan",
                        if (won) "What a performance by ${player.last}! $statLineStr in the win over ${opp.school}."
                        else "Tough loss tonight against ${opp.school}, but ${player.last} gave everything with $statLineStr.",
                        likes = 250, gameId = g.id),
                )
            } else posts
        }
    }

    /** Generates a complete newspaper beat writer recap with coach and player quotes for a completed game. */
    suspend fun generatePostgameReport(
        config: OllamaConfig,
        lg: League,
        g: Game,
    ): Result<PostgameReport> {
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
            append("STORY:\n<2 paragraphs recapping the game's decisive run and key matchup>\n")
            append("COACH_QUOTE: <Realistic postgame quote from Coach $coach>\n")
            append("STAR_QUOTE: <Postgame locker room quote from $topPlayerName>\n")
        }

        return generate(
            config,
            prompt,
            system = "You are a professional basketball beat writer. Write engaging, realistic newspaper recaps using the exact provided statistics and final score.",
            numPredict = 320,
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

            PostgameReport(headline, outlet, story, coachQuote, starQuote)
        }
    }

    /** Generates 3 tactical pregame scouting keys based on team ratings and matchups. */
    suspend fun generateScoutingKeys(
        config: OllamaConfig,
        lg: League,
        userTeam: Team,
        oppTeam: Team,
    ): Result<List<ScoutingKey>> {
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
            if (keys.isEmpty()) {
                listOf(
                    ScoutingKey("Pace & Transition", "Control the tempo and limit ${oppTeam.school}'s fast-break scoring opportunities."),
                    ScoutingKey("Paint Protection", "Protect the rim and close out defensive possessions with solid rebounding."),
                    ScoutingKey("Ball Movement", "Execute offensive sets patiently and make the extra pass to open shooters."),
                )
            } else keys
        }
    }

    /** Generates an insider trade grade & analysis for a completed deal. */
    suspend fun generateTradeAnalysis(
        config: OllamaConfig,
        lg: League,
        trade: Trade,
    ): Result<TradeAnalysis> {
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

            TradeAnalysis(gradeA, gradeB, analysis)
        }
    }

    /** Generates 3 realistic NBA trade deadline rumor mill whispers per league sources. */
    suspend fun generateTradeRumors(
        config: OllamaConfig,
        lg: League,
    ): Result<List<Pair<String, String>>> {
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
            if (rumors.isEmpty()) {
                listOf("Deadline Buzz" to "Multiple contenders have engaged sellers on perimeter scoring and interior defense ahead of the trade deadline.")
            } else rumors
        }
    }
}
