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

    /** Normalizes a host string to a valid HTTP URL. */
    fun cleanHost(raw: String): String {
        val trimmed = raw.trim().removeSuffix("/")
        return when {
            trimmed.isEmpty() -> "http://10.0.2.2:11434"
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            else -> "http://$trimmed"
        }
    }

    /** Test connection to Ollama server at [host] and return available model names. */
    suspend fun testConnection(host: String): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL("${cleanHost(host)}/api/tags")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4000
                readTimeout = 4000
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

    /** Low-level generate call using Ollama's /api/generate endpoint. */
    suspend fun generate(
        config: OllamaConfig,
        prompt: String,
        system: String = "You are a professional basketball beat writer and columnist. Write engaging, realistic sports newspaper journalism in 2-4 clean paragraphs. Never include markdown headers, title prefixes, or conversational meta-commentary.",
        temperature: Double = 0.7,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL("${cleanHost(config.host)}/api/generate")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 6000
                readTimeout = 30000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
            }

            val payload = buildString {
                append("{\"model\":")
                append(jsonString(config.model.ifBlank { "llama3.2" }))
                append(",\"prompt\":")
                append(jsonString(prompt))
                append(",\"system\":")
                append(jsonString(system))
                append(",\"stream\":false")
                append(",\"options\":{\"temperature\":$temperature}}")
            }

            conn.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(payload) }

            val code = conn.responseCode
            if (code in 200..299) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val root = json.parseToJsonElement(body).jsonObject
                val responseText = root["response"]?.jsonPrimitive?.content.orEmpty().trim()
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
        val coach = lg.userCoach
        val top = lg.teams.maxByOrNull { it.elo } ?: u

        val prompt = buildString {
            append("You are a basketball beat reporter. Write a breaking story or feature article about the following topic:\n")
            append("Topic: $topic\n")
            append("League: ${if (lg.pro) "NBA" else "NCAA College Basketball"}\n")
            append("User team: ${u.school} (${u.w}-${u.l}), Coach: ${coach.name}\n")
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

        return generate(config, prompt).map { raw ->
            val stories = mutableListOf<Story>()
            val blocks = raw.split("---")
            for (b in blocks) {
                var kicker = ""
                var head = ""
                val body = mutableListOf<String>()
                for (line in b.lines()) {
                    val trimmed = line.trim()
                    when {
                        trimmed.startsWith("KICKER:", ignoreCase = true) -> kicker = trimmed.substringAfter(":").trim()
                        trimmed.startsWith("HEADLINE:", ignoreCase = true) -> head = trimmed.substringAfter(":").trim()
                        trimmed.startsWith("BODY:", ignoreCase = true) -> body.add(trimmed.substringAfter(":").trim())
                        kicker.isNotEmpty() && head.isNotEmpty() && trimmed.isNotBlank() -> body.add(trimmed)
                    }
                }
                if (kicker.isNotEmpty() && head.isNotEmpty()) {
                    stories += Story(kicker, head, body.joinToString(" ").trim(), userTeamId)
                }
            }
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
}
