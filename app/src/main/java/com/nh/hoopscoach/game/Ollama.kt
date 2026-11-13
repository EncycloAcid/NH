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
 *
 * The settings UI only offers [CHEAP_MODELS] — small/free local tags that run on phones
 * and modest PCs. Heavier models can still be typed into a save file, but the picker
 * will not surface them.
 */
object Ollama {
    private val json = Json { ignoreUnknownKeys = true }

    /** Curated free/cheap local models shown in Settings. id is the Ollama tag to pull. */
    data class CheapModel(val id: String, val label: String, val blurb: String)

    val CHEAP_MODELS = listOf(
        CheapModel("llama3.2", "Llama 3.2", "Default · free · solid writing"),
        CheapModel("llama3.2:3b", "Llama 3.2 3B", "Small · good phone balance"),
        CheapModel("llama3.2:1b", "Llama 3.2 1B", "Tiny · lowest memory"),
        CheapModel("phi3:mini", "Phi-3 Mini", "Compact · strong prose"),
        CheapModel("phi3", "Phi-3", "Compact · free local"),
        CheapModel("gemma2:2b", "Gemma 2 2B", "Google small · free"),
        CheapModel("qwen2.5:3b", "Qwen 2.5 3B", "Small · vivid voice"),
        CheapModel("qwen2.5:1.5b", "Qwen 2.5 1.5B", "Tiny · quick drafts"),
        CheapModel("mistral", "Mistral 7B", "Popular · still free local"),
        CheapModel("tinyllama", "TinyLlama", "Minimal · experimental"),
    )

    private val BEAT_SYSTEM = """
        You are a sharp basketball beat writer for Hoops Wire.
        Write clean, specific sports journalism: concrete details, one short coach or player quote when it fits, no fluff.
        Never use markdown, bullet lists, title prefixes, hashtags, or meta commentary like "Here is the article".
        Prefer 2–4 short paragraphs separated by blank lines.
    """.trimIndent()

    /** Normalizes a host string to a valid HTTP URL. */
    fun cleanHost(raw: String): String {
        val trimmed = raw.trim().removeSuffix("/")
        return when {
            trimmed.isEmpty() -> "http://10.0.2.2:11434"
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            else -> "http://$trimmed"
        }
    }

    /** True when [installed] is the same tag as [wanted], or a tagged variant of it. */
    fun modelMatches(installed: String, wanted: String): Boolean {
        val a = installed.trim().lowercase()
        val b = wanted.trim().lowercase()
        if (a == b) return true
        // "llama3.2:latest" matches catalog "llama3.2"
        if (a.substringBefore(":") == b.substringBefore(":")) {
            val aTag = a.substringAfter(":", "latest")
            val bTag = b.substringAfter(":", "latest")
            if (b == b.substringBefore(":") && aTag == "latest") return true
            if (aTag == bTag) return true
        }
        return a.startsWith("$b:") || b.startsWith("$a:")
    }

    /** Keep only free/cheap catalog entries; prefer ones actually installed on the server. */
    fun filterCheap(installed: List<String>): List<CheapModel> {
        val hits = CHEAP_MODELS.filter { cheap -> installed.any { modelMatches(it, cheap.id) } }
        return hits.ifEmpty { CHEAP_MODELS }
    }

    /** Picker rows: label + blurb, optionally marked installed from [installed]. */
    fun pickerOptions(installed: List<String> = emptyList()): List<Pair<String, String>> =
        CHEAP_MODELS.map { m ->
            val have = installed.any { modelMatches(it, m.id) }
            m.id to if (installed.isEmpty()) m.blurb
            else if (have) "${m.blurb} · installed"
            else "${m.blurb} · run: ollama pull ${m.id}"
        }

    /** Snap [current] to a cheap catalog id; keep it if already allowed. */
    fun resolveModel(current: String, installed: List<String> = emptyList()): String {
        val cur = current.trim().ifBlank { "llama3.2" }
        if (CHEAP_MODELS.any { modelMatches(cur, it.id) }) {
            // Prefer the exact installed tag when we have one.
            installed.firstOrNull { modelMatches(it, cur) }?.let { return it }
            return CHEAP_MODELS.first { modelMatches(cur, it.id) }.id
        }
        val cheapInstalled = filterCheap(installed)
        installed.firstOrNull { name -> cheapInstalled.any { modelMatches(name, it.id) } }?.let { return it }
        return CHEAP_MODELS.first().id
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
        system: String = BEAT_SYSTEM,
        temperature: Double = 0.65,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val model = resolveModel(config.model)
            val url = URL("${cleanHost(config.host)}/api/generate")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8000
                readTimeout = 90000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
            }

            val payload = buildString {
                append("{\"model\":")
                append(jsonString(model))
                append(",\"prompt\":")
                append(jsonString(prompt))
                append(",\"system\":")
                append(jsonString(system))
                append(",\"stream\":false")
                append(",\"keep_alive\":\"10m\"")
                append(",\"options\":{\"temperature\":$temperature,\"top_p\":0.9,\"repeat_penalty\":1.12,\"num_predict\":700}}")
            }

            conn.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(payload) }

            val code = conn.responseCode
            if (code in 200..299) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val root = json.parseToJsonElement(body).jsonObject
                val responseText = cleanOutput(root["response"]?.jsonPrimitive?.content.orEmpty())
                if (responseText.isEmpty()) throw Exception("Empty response from Ollama")
                responseText
            } else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
                throw Exception("HTTP $code: $err")
            }
        }
    }

    /** Strip markdown / chatty wrappers small models often add. */
    fun cleanOutput(raw: String): String {
        var t = raw.trim()
        if (t.isEmpty()) return t
        t = t.replace(Regex("(?m)^```\\w*\\s*"), "").replace(Regex("(?m)^```\\s*$"), "")
        t = t.replace(Regex("(?i)^(here(?:'s| is)|sure[,!]?)[^\\n]*\\n+"), "")
        t = t.replace(Regex("(?m)^#+\\s+"), "")
        t = t.replace(Regex("(?m)^[-*]\\s+"), "")
        t = t.replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
        t = t.replace(Regex("\\s+\n"), "\n").replace(Regex("\n{3,}"), "\n\n")
        return t.trim()
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
        val coach = team?.let { runCatching { lg.coachOf(it) }.getOrNull() }

        val prompt = buildString {
            append("Rewrite this Hoops Wire beat story. Body only — no headline.\n")
            append("Headline: ${news.text}\n")
            append("Kind: ${news.kind.ifEmpty { "news" }}\n")
            append("Season ${news.season}, day ${news.day}. ${if (lg.pro) "NBA" else "NCAA"}.\n")
            if (team != null) append("Team: ${team.school} ${team.w}-${team.l}${coach?.let { ", coach ${it.name}" } ?: ""}.\n")
            if (opp != null) append("Opponent: ${opp.school} ${opp.record}.\n")
            if (game != null && game.played) append("Final: ${game.hs}-${game.aws}${if (game.ot > 0) " (OT)" else ""}.\n")
            if (player != null) {
                append("Featured: ${player.name}, ${player.posLabel}, ")
                append("${"%.1f".format(player.stats.ppg)} PPG / ${"%.1f".format(player.stats.rpg)} RPG / ${"%.1f".format(player.stats.apg)} APG.\n")
            }
            if (news.body.isNotEmpty()) append("Notes to keep honest: ${news.body.take(400)}\n")
            append("Write 2–3 tight paragraphs with one short quote. Stay specific to these facts.")
        }

        return generate(config, prompt)
    }

    /** Generate a fresh custom story on demand. */
    suspend fun generateCustomStory(config: OllamaConfig, lg: League, topic: String): Result<News> {
        val u = lg.user
        val coach = lg.userCoach
        val top = lg.teams.maxByOrNull { it.elo } ?: u
        val stars = lg.rosterOf(u).sortedByDescending { it.ovr }.take(3)

        val prompt = buildString {
            append("Write a Hoops Wire breaking story.\n")
            append("Topic: $topic\n")
            append("League: ${if (lg.pro) "NBA" else "NCAA College Basketball"}\n")
            append("Focus team: ${u.school} (${u.w}-${u.l}), coach ${coach.name}\n")
            if (stars.isNotEmpty()) {
                append("Roster names you may quote: ")
                append(stars.joinToString { "${it.name} (${it.posLabel})" })
                append("\n")
            }
            append("League favorite: ${top.school} (${top.w}-${top.l})\n")
            append("Season ${lg.season}, day ${lg.day}\n\n")
            append("Format EXACTLY:\n")
            append("HEADLINE: <punchy beat headline>\n")
            append("OUTLET: Hoops Wire\n")
            append("BODY:\n<2-3 paragraphs>")
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
