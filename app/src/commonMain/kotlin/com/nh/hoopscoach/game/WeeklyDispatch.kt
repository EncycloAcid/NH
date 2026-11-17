package com.nh.hoopscoach.game

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.math.roundToInt

/** One week's Hoops Dispatch: five headlines and three rumors. [ai] when a model wrote it. */
@Serializable
class DispatchEdition(
    val season: Int,
    val week: Int,
    val headlines: List<String>,
    val rumors: List<String>,
    val ai: Boolean = false,
    val writer: String = "",
)

/** A recruiting round's crystal ball: where the top uncommitted players are leaning, and portal whispers. */
@Serializable
class CrystalBall(
    val season: Int,
    val round: Int,
    val picks: List<String>,
    val rumors: List<String>,
    val ai: Boolean = false,
    val writer: String = "",
)

/**
 * Writes the batched AI copy. The game asks for it once a simulated week (and once a recruiting round) with a
 * compact fact sheet, instead of a call per story. [AiNewsGenerator] asks the AI provider; [TemplateNewsGenerator]
 * writes the same thing from the facts, instantly: the fallback, and what tests and simulations plug in.
 */
interface NewsGenerator {
    suspend fun weekly(brief: WeeklyDispatch.Brief, config: OllamaConfig): DispatchEdition?
    suspend fun crystalBall(brief: WeeklyDispatch.Brief, config: OllamaConfig): CrystalBall?
}

/** The game's own lines, built from the fact sheet. Never fails, never waits. */
object TemplateNewsGenerator : NewsGenerator {
    override suspend fun weekly(brief: WeeklyDispatch.Brief, config: OllamaConfig) =
        DispatchEdition(brief.season, brief.index, brief.lines.take(5), brief.rumorLines.take(3))

    override suspend fun crystalBall(brief: WeeklyDispatch.Brief, config: OllamaConfig) =
        CrystalBall(brief.season, brief.index, brief.lines.take(5), brief.rumorLines.take(3))
}

/**
 * The AI provider writes it in one request: the fact sheet as compact JSON in, JSON out
 * ({"headlines": [...], "rumors": [...]}), parsed and checked against the facts. The instructions stay word for
 * word the same every week, so Gemini Nano's prefix cache reuses them. Returns null if the model can't
 * answer or gets the facts wrong, and the game's own lines stay up.
 */
object AiNewsGenerator : NewsGenerator {
    override suspend fun weekly(brief: WeeklyDispatch.Brief, config: OllamaConfig): DispatchEdition? {
        val system = if (brief.pro) WeeklyDispatch.SYSTEM_NBA else WeeklyDispatch.SYSTEM_COLLEGE
        val prompt = "WEEK ${brief.index} FACTS (JSON):\n${brief.json}\n\n" +
            "Write exactly 5 headlines and 3 ${if (brief.pro) "trade rumors" else "rumors (transfer portal, coaching seats, recruiting)"}.\n" +
            "Reply with JSON only: {\"headlines\": [5 strings], \"rumors\": [3 strings]}"
        val raw = Ollama.generate(config, prompt, system, temperature = 0.8, numPredict = 420, desk = OnDeviceAi.Desk.NOTIFICATIONS).getOrNull() ?: return null
        val (h, r) = WeeklyDispatch.parse(raw, "headlines", "rumors", brief) ?: return null
        if (h.size < 3) return null
        return DispatchEdition(brief.season, brief.index, h.take(5), r.take(3), ai = true, writer = Ollama.writerName(config, true))
    }

    override suspend fun crystalBall(brief: WeeklyDispatch.Brief, config: OllamaConfig): CrystalBall? {
        val prompt = "RECRUITING ROUND ${brief.index} FACTS (JSON):\n${brief.json}\n\n" +
            "Write 5 crystal-ball predictions (each names one recruit and the school he's leaning to, from the facts) and 3 transfer portal or recruiting rumors.\n" +
            "Reply with JSON only: {\"predictions\": [5 strings], \"rumors\": [3 strings]}"
        val raw = Ollama.generate(config, prompt, WeeklyDispatch.SYSTEM_CRYSTAL, temperature = 0.8, numPredict = 420, desk = OnDeviceAi.Desk.NOTIFICATIONS).getOrNull() ?: return null
        val (p, r) = WeeklyDispatch.parse(raw, "predictions", "rumors", brief) ?: return null
        if (p.size < 3) return null
        return CrystalBall(brief.season, brief.index, p.take(5), r.take(3), ai = true, writer = Ollama.writerName(config, true))
    }
}

/**
 * The Hoops Dispatch: once a simulated week, the week's biggest stories (upsets, buzzer-beaters, rivalry
 * results, big nights, injuries, coaches on the hot seat) as five headlines and three rumors.
 *
 * The game's own edition goes up at once, so the Today screen never waits. Then, if AI is on, one request writes
 * the AI edition from a compact fact sheet (well under Gemini Nano's ~4,000-token window). A rapid sim
 * (straight to Selection Sunday) asks only for the week it stops on.
 */
object WeeklyDispatch {
    const val SYSTEM_COLLEGE = "You are the Hoops Dispatch, a college basketball insider. You write punchy one-sentence news headlines and rumors. " +
        "Use only the names, teams and numbers in the facts. Never invent a name, a score or a number. Present tense, no hashtags, no emoji."
    const val SYSTEM_NBA = "You are the Hoops Dispatch, an NBA insider. You write punchy one-sentence news headlines and trade rumors. " +
        "Use only the names, teams and numbers in the facts. Never invent a name, a score or a number. Present tense, no hashtags, no emoji."
    const val SYSTEM_CRYSTAL = "You are a college basketball recruiting insider making crystal-ball calls. One sentence each. " +
        "Use only the recruits, schools and numbers in the facts. Never invent a name or a number. No hashtags, no emoji."

    /** Swappable: tests and simulations plug in [TemplateNewsGenerator]. */
    @Volatile var generator: NewsGenerator = AiNewsGenerator

    /**
     * A week's (or a recruiting round's) fact sheet: [json] for the model, [names] it may use, [numbers] it may
     * use, and the game's own [lines] and [rumorLines] written from the same facts.
     */
    class Brief(
        val season: Int, val index: Int, val pro: Boolean, val json: String,
        val names: Set<String>, val numbers: Set<String>, val lines: List<String>, val rumorLines: List<String>,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var pendingWeek: Job? = null
    @Volatile private var pendingRound: Job? = null

    fun week(lg: League) = lg.day / 7

    /** Called at the end of every simulated day; does its work at the end of each week. */
    fun maybeRun(lg: League) {
        if (lg.phase != Phase.REGULAR || lg.day < 7 || lg.day % 7 != 0) return
        val week = week(lg)
        // Keyed by season and week, so a new season (college or NBA) starts over by itself.
        val key = lg.season * 100 + week
        if (key <= lg.dispatchWeek) return
        lg.dispatchWeek = key
        val brief = runCatching { weeklyBrief(lg, week) }.getOrNull() ?: return
        publish(lg, DispatchEdition(lg.season, week, brief.lines.take(5), brief.rumorLines.take(3)))
        val cfg = lg.ollama
        if (!cfg.enabled || !cfg.autoGenerate) return
        pendingWeek?.cancel()
        pendingWeek = scope.launch {
            // A rapid sim keeps going: only the week the sim stops on gets written.
            delay(1500)
            if (lg.dispatchWeek != key) return@launch
            val ed = runCatching { generator.weekly(brief, cfg) }.getOrNull() ?: return@launch
            if (lg.dispatchWeek == key) publish(lg, ed)
        }
    }

    /** Called after each recruiting round locks. */
    fun maybeCrystalBall(lg: League) {
        if (lg.pro) return
        val round = Recruiting.round(lg)
        if (round <= 0) return
        val brief = runCatching { crystalBrief(lg, round) }.getOrNull() ?: return
        publishCrystal(lg, CrystalBall(lg.season, round, brief.lines.take(5), brief.rumorLines.take(3)))
        val cfg = lg.ollama
        if (!cfg.enabled || !cfg.autoGenerate || !cfg.recruitingPitches) return
        pendingRound?.cancel()
        pendingRound = scope.launch {
            delay(800)
            if (Recruiting.round(lg) != round) return@launch
            val cb = runCatching { generator.crystalBall(brief, cfg) }.getOrNull() ?: return@launch
            if (Recruiting.round(lg) == round) publishCrystal(lg, cb)
        }
    }

    fun latest(lg: League): DispatchEdition? = synchronized(lg.dispatch) { lg.dispatch.lastOrNull { it.season == lg.season } }
    fun latestCrystal(lg: League): CrystalBall? = synchronized(lg.crystal) { lg.crystal.lastOrNull { it.season == lg.season } }

    private fun publish(lg: League, ed: DispatchEdition) {
        synchronized(lg.dispatch) {
            lg.dispatch.removeAll { it.season == ed.season && it.week == ed.week }
            lg.dispatch += ed
            while (lg.dispatch.size > 8) lg.dispatch.removeAt(0)
        }
        if (ed.ai) LeagueLists.appendNews(lg, listOf(News(lg.season, lg.day, "The Hoops Dispatch, week ${ed.week}: ${ed.headlines.first().trimEnd('.')}", false,
            kind = "dispatch", source = "Hoops Dispatch · ${ed.writer}", aiLabeled = true,
            body = (ed.headlines + ed.rumors.map { "Rumor: $it" }).joinToString("\n"))))
    }

    private fun publishCrystal(lg: League, cb: CrystalBall) {
        synchronized(lg.crystal) {
            lg.crystal.removeAll { it.season == cb.season && it.round == cb.round }
            lg.crystal += cb
            while (lg.crystal.size > 6) lg.crystal.removeAt(0)
        }
    }

    // ---------- the fact sheets ----------

    private fun name(lg: League, t: Team) = if (lg.pro) t.name else t.school
    private fun rivalryName(lg: League, g: Game) = lg.rivalry(g)?.name?.takeIf { it.isNotBlank() } ?: "the rivalry"
    private fun rec(t: Team) = "${t.w}-${t.l}"

    /** The week's facts: compact JSON (a few hundred tokens), and the game's own lines from the same facts. */
    fun weeklyBrief(lg: League, week: Int): Brief {
        val from = (week - 1) * 7 + 1
        val to = week * 7
        val games = lg.games.filter { it.played && it.day in from..to }
        val names = mutableSetOf<String>()
        val lines = mutableListOf<String>()
        val rumors = mutableListOf<String>()
        fun team(t: Team): JsonObject {
            names += name(lg, t); names += t.abbr; if (!lg.pro) names += t.mascot
            return buildJsonObject { put("team", name(lg, t)); put("record", rec(t)); if (!lg.pro && t.ap in 1..25) put("rank", t.ap) }
        }
        fun pct(t: Team) = if (t.w + t.l == 0) 0.5 else t.w.toDouble() / (t.w + t.l)

        // Upsets: a ranked (or much better) team beaten.
        val upsets = games.mapNotNull { g ->
            val w = lg.team(g.winner()); val l = lg.team(g.loser())
            val gap = if (lg.pro) pct(l) - pct(w) else if (l.ap in 1..25) ((if (w.ap in 1..25) w.ap else 40) - l.ap).toDouble() else 0.0
            if (gap >= (if (lg.pro) 0.2 else 8.0)) Triple(g, gap, w to l) else null
        }.sortedByDescending { it.second }.take(3)
        // Rivalry results.
        val rivals = games.filter { lg.rivalry(it) != null }.take(3)
        // Big nights: the best scoring lines.
        val nights = games.flatMap { g ->
            val box = g.box ?: return@flatMap emptyList()
            box.home.map { it to g.home } + box.away.map { it to g.away }
        }.sortedByDescending { it.first.s.pts * 2 + it.first.s.drb + it.first.s.orb + it.first.s.ast }.take(3)
        // Buzzer-beaters and the like.
        val moments = lg.moments.filter { it.season == lg.season && it.day in from..to }.take(2)
        // Coaches feeling it.
        val hot = lg.teams.filter { it.w + it.l >= 6 && pct(it) < 0.4 }.mapNotNull { t -> runCatching { lg.coachOf(t) }.getOrNull()?.let { t to it } }
            .sortedBy { (t, c) -> c.security - (if (!lg.pro) t.prestige / 4 else 0) }.take(2)
        // The best players who are hurt.
        val hurt = lg.players.filter { it.teamId >= 0 && it.injGames >= 4 }.sortedByDescending { it.ovr }.take(2)
        // The top of the table.
        val top = lg.teams.sortedWith(compareByDescending<Team> { pct(it) }.thenByDescending { it.w }).take(3)

        val json = buildJsonObject {
            put("league", if (lg.pro) "NBA" else "college basketball")
            put("week", week)
            put("upsets", buildJsonArray { upsets.forEach { (g, _, wl) -> add(buildJsonObject {
                put("winner", team(wl.first)); put("loser", team(wl.second)); put("score", "${g.scoreFor(wl.first.id)}-${g.scoreFor(wl.second.id)}") }) } })
            put("rivalry_games", buildJsonArray { rivals.forEach { g -> val w = lg.team(g.winner()); val l = lg.team(g.loser()); add(buildJsonObject {
                put("rivalry", rivalryName(lg, g)); put("winner", name(lg, w)); put("loser", name(lg, l)); put("score", "${g.scoreFor(w.id)}-${g.scoreFor(l.id)}") }); lg.rivalry(g)?.name?.takeIf { it.isNotBlank() }?.let { names += it }; team(w); team(l) } })
            put("big_nights", buildJsonArray { nights.forEach { (pl, tid) -> val t = lg.team(tid); names += pl.name; names += pl.name.substringAfterLast(' '); add(buildJsonObject {
                put("player", pl.name); put("team", name(lg, t)); put("pts", pl.s.pts); put("reb", pl.s.orb + pl.s.drb); put("ast", pl.s.ast) }); team(t) } })
            put("moments", buildJsonArray { moments.forEach { add(JsonPrimitive(it.headline)) } })
            put("hot_seat", buildJsonArray { hot.forEach { (t, c) -> names += c.name; names += c.last; add(buildJsonObject { put("coach", c.name); put("team", team(t)) }) } })
            put("injuries", buildJsonArray { hurt.forEach { p -> val t = lg.team(p.teamId); names += p.name; names += p.last; add(buildJsonObject {
                put("player", p.name); put("team", name(lg, t)); put("injury", p.injury.ifBlank { "injury" }); put("games_out", p.injGames) }); team(t) } })
            put("leaders", buildJsonArray { top.forEach { add(team(it)) } })
        }.toString()

        // The game's own edition, from the same facts.
        upsets.forEach { (g, _, wl) -> val (w, l) = wl; lines += "${name(lg, w)} stuns ${if (!lg.pro && l.ap in 1..25) "No. ${l.ap} " else ""}${name(lg, l)}, ${g.scoreFor(w.id)}-${g.scoreFor(l.id)}" }
        moments.forEach { lines += it.headline }
        rivals.forEach { g -> val w = lg.team(g.winner()); val l = lg.team(g.loser()); lines += "${name(lg, w)} takes ${rivalryName(lg, g)} over ${name(lg, l)}, ${g.scoreFor(w.id)}-${g.scoreFor(l.id)}" }
        nights.forEach { (pl, tid) -> lines += "${pl.name} drops ${pl.s.pts} for ${name(lg, lg.team(tid))}" }
        top.firstOrNull()?.let { lines += "${name(lg, it)} sits on top at ${rec(it)}" }
        hot.forEach { (t, c) -> rumors += "${c.name}'s seat at ${name(lg, t)} is getting warmer at ${rec(t)}" }
        hurt.forEach { p -> rumors += "${name(lg, lg.team(p.teamId))} braces for life without ${p.name} for ${p.injGames} more games" }
        if (lg.pro) top.drop(1).firstOrNull()?.let { rumors += "Contenders like ${name(lg, it)} are said to be shopping for one more piece" }
        else upsets.firstOrNull()?.let { (_, _, wl) -> rumors += "Recruits are calling ${name(lg, wl.first)} after the win over ${name(lg, wl.second)}" }
        if (lines.isEmpty()) lines += "A quiet week around the league"
        return Brief(lg.season, week, lg.pro, json, names, Regex("\\d+").findAll(json).map { it.value }.toSet(), lines.distinct(), rumors.distinct())
    }

    /** A recruiting round's facts: the top uncommitted players, where they're leaning, and the NIL on the table. */
    fun crystalBrief(lg: League, round: Int): Brief {
        val names = mutableSetOf<String>()
        val lines = mutableListOf<String>()
        val rumors = mutableListOf<String>()
        val open = lg.recruits.filter { it.committed < 0 && it.interest.isNotEmpty() }
            .sortedWith(compareByDescending<Recruit> { it.onBoard }.thenBy { if (it.p.recruitRank > 0) it.p.recruitRank else 999 }).take(6)
        val json = buildJsonObject {
            put("round", round)
            put("your_school", lg.user.school)
            put("recruits", buildJsonArray { open.forEach { r ->
                names += r.p.name; names += r.p.last
                val leaders = r.interest.entries.sortedByDescending { it.value }.take(3)
                add(buildJsonObject {
                    put("name", r.p.name); put("stars", r.p.stars); put("pos", r.p.pos.name)
                    if (r.p.recruitRank > 0) put("rank", r.p.recruitRank)
                    if (r.transfer) put("transfer", true)
                    put("leaning", buildJsonArray { leaders.forEach { (tid, v) -> val t = lg.team(tid); names += t.school; names += t.abbr; add(buildJsonObject { put("school", t.school); put("interest", v) }) } })
                    if (r.nilAsk > 0) put("nil_ask_k", r.nilAsk)
                })
            } })
        }.toString()
        open.forEach { r ->
            val lead = r.interest.entries.sortedByDescending { it.value }
            val first = lead.getOrNull(0) ?: return@forEach
            val second = lead.getOrNull(1)
            val t = lg.team(first.key)
            lines += "${r.p.name} (${r.p.stars}★) leans ${t.school}" + (second?.let { " (${first.value}) over ${lg.team(it.key).school} (${it.value})" } ?: "")
        }
        open.filter { it.transfer }.take(2).forEach { r -> rumors += "${r.p.name} is drawing calls from the portal's big spenders" }
        open.firstOrNull()?.let { r -> r.interest.entries.sortedByDescending { it.value }.getOrNull(1)?.let { rumors += "${lg.team(it.key).school} is making a late push for ${r.p.name}" } }
        return Brief(lg.season, round, false, json, names, Regex("\\d+").findAll(json).map { it.value }.toSet(), lines, rumors)
    }

    // ---------- reading the model's answer ----------

    private val json = Json { isLenient = true; ignoreUnknownKeys = true }

    /**
     * Reads {"<a>": [...], "<b>": [...]} out of a reply (even wrapped in a code fence or chatter), and keeps the
     * lines that name someone from the facts and use only the facts' numbers.
     */
    fun parse(raw: String, a: String, b: String, brief: Brief): Pair<List<String>, List<String>>? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val obj = runCatching { json.parseToJsonElement(raw.substring(start, end + 1)).jsonObject }.getOrNull() ?: return null
        fun list(key: String): List<String> {
            val arr = (obj[key] ?: obj.entries.firstOrNull { it.key.contains(key.take(5), true) }?.value) as? JsonArray ?: return emptyList()
            return arr.mapNotNull { e -> runCatching { e.jsonPrimitive.content }.getOrNull() }.map { clean(it) }.filter { ok(it, brief) }.distinct()
        }
        return list(a) to list(b)
    }

    private fun clean(s: String) = s.trim().removePrefix("-").removePrefix("•").trim().replace(Regex("^\\d+[.)]\\s*"), "").trim('"', '“', '”', ' ')
        .replace("**", "").let { if (it.isNotEmpty() && it.last() !in ".!?") it else it }

    private fun ok(line: String, brief: Brief): Boolean {
        if (line.length !in 12..220) return false
        if (brief.names.none { it.length >= 3 && line.contains(it, ignoreCase = true) }) return false
        // Every number it uses must come from the facts (small counts like "3 straight" are fine).
        return Regex("\\d+").findAll(line).all { m -> m.value in brief.numbers || (m.value.toIntOrNull() ?: 99) <= 5 }
    }
}
