package com.nh.hoopscoach.game

import kotlinx.coroutines.CancellationException
import java.math.BigDecimal
import java.security.MessageDigest
import java.util.Locale

data class AiFactSheet(
    val text: String,
    val names: Set<String>,
    val allNames: Set<String>,
    val records: Map<String, Set<String>>,
    val phase: Phase,
    val preseason: Boolean,
    val preview: Boolean = false,
    val upcoming: Boolean = false,
    val entityAliases: Map<String, String> = emptyMap(),
)

object AiFacts {
    private fun aliases(t: Team) = listOf(t.name, t.school, t.abbr).filter { it.isNotBlank() }
    private fun mentioned(text: String, name: String) = text.contains(name, ignoreCase = true) &&
        Regex("(?i)(?<![\\p{L}\\d])${Regex.escape(name)}(?![\\p{L}\\d])").containsMatchIn(text)
    private fun stats(s: StatLine) = String.format(Locale.US, "%d GP; %.1f PPG; %.1f RPG; %.1f APG; %.1f MPG; %.1f%% FG; %.1f%% 3PT; %.1f%% FT", s.gp, s.ppg, s.rpg, s.apg, s.mpg, s.fgPct * 100, s.tpPct * 100, s.ftPct * 100)

    fun build(lg: League, request: String, extra: String = "", players: List<Player> = emptyList(), preview: Boolean = false, upcoming: Boolean = false): AiFactSheet {
        val population = (lg.players + lg.alumni + lg.recruits.map { it.p } + players).distinctBy { it.id }
        val namedPlayers = population.filter { mentioned(request, it.name) }
        val teams = (lg.teams.filter { t -> aliases(t).any { mentioned(request, it) } } +
            namedPlayers.mapNotNull { p -> lg.teams.firstOrNull { it.id == p.teamId } } + lg.user).distinctBy { it.id }.take(8)
        val featured = (namedPlayers + players + teams.flatMap { t -> lg.rosterOf(t).sortedByDescending { it.ovr }.take(2) } +
            teams.flatMap { t -> lg.rosterOf(t).filter { it.out } } + teams.mapNotNull { AiTemplates.newcomer(lg, it) } + teams.mapNotNull { AiTemplates.departure(lg, it) }).distinctBy { it.id }
        val names = (teams.flatMap { aliases(it) } + featured.map { it.name } + teams.map { lg.coachOf(it).name } +
            teams.map { lg.conf(it.confId).name } + lg.rivals.map { it.name } + featured.flatMap { listOf(it.hometown, it.highSchool, it.injury) }.filter { it.isNotBlank() }).toMutableSet()
        val allNames = (lg.teams.flatMap { aliases(it) } + population.map { it.name } + lg.coaches.map { it.name }).toSet()
        val records = mutableMapOf<String, Set<String>>()
        val preseason = lg.phase == Phase.REGULAR && lg.teams.all { it.gp == 0 }
        val text = buildString {
            appendLine("SEASON ${lg.season}; DATE day ${lg.day}; PHASE ${lg.phase.name}${if (preseason) " (preseason; no games played)" else ""}.")
            appendLine("League: ${if (lg.pro) "NBA" else "college basketball"}. Forecasts are simulation probabilities, not results. Missing facts are unknown.")
            if (preview) appendLine("PURPOSE: preseason magazine. Discuss forecasts and last season; do not claim this season has been played.")
            if (upcoming) appendLine("PURPOSE: upcoming matchup. Do not announce a result or postgame reaction.")
            teams.forEach { t ->
                val order = lg.teams.filter { it.confId == t.confId }.sortedWith(compareByDescending<Team> {
                    if (lg.pro) it.winPct else if (it.cw + it.cl > 0) it.cw.toDouble() / (it.cw + it.cl) else 0.0
                }.thenByDescending { it.elo }.thenBy { it.id })
                val rank = if (t.gp == 0) "standings not established" else "conference standing ${order.indexOf(t) + 1} of ${order.size} (ties ordered by Elo)"
                appendLine("TEAM ${t.name} (${t.abbr}); current record ${t.record}; conference ${lg.conf(t.confId).name}, ${t.confRecord}; $rank; AP ${t.ap.takeIf { it > 0 } ?: "unranked"}; NET ${t.net.takeIf { it > 0 } ?: "unranked"}; prestige ${t.prestige}/100; titles ${t.titles}.")
                val c = lg.coachOf(t)
                appendLine("COACH ${c.name}; job security ${c.security}/100; titles ${c.titles}; recorded seasons at this team ${c.history.count { it.teamId == t.id }}.")
                t.history.lastOrNull { it.season == lg.season - 1 }?.let { h ->
                    appendLine("TEAM last season ${h.season}: ${h.w}-${h.l}; ${h.result}.")
                }
                if (lg.pro) lg.proState?.preview?.takeIf { it.season == lg.season }?.proj?.get(t.id)?.let { p ->
                    appendLine("FORECAST ${t.name}: ${p.w}-${p.l}; win percentiles ${p.lo}/${p.median ?: "unknown"}/${p.hi}; playoffs ${PreviewWriting.percent(p.playoffs)}; Finals ${PreviewWriting.percent(p.finals)}; title ${PreviewWriting.percent(p.title)}.")
                } else lg.preview?.takeIf { it.season == lg.season }?.let { pv ->
                    pv.proj[t.id]?.let { p -> appendLine("FORECAST ${t.name}: rank ${p.nat}; ${p.w}-${p.l}.") }
                    pv.odds[t.id]?.let { o -> appendLine("ODDS ${t.name}: NCAA ${PreviewWriting.percent(o.ncaa)}; conference ${PreviewWriting.percent(o.conf)}; Final Four ${PreviewWriting.percent(o.f4)}; title ${PreviewWriting.percent(o.title)}; win percentiles ${o.lo}/${o.median ?: "unknown"}/${o.hi}.") }
                }
                aliases(t).forEach { records[it] = setOf(t.record) }
                appendLine("EDITORIAL ${if (lg.pro) t.name else t.school}: ${AiTemplates.previewCopy(lg, t)}")
            }
            val results = lg.games.filter { it.played && it.day <= lg.day && teams.any { t -> it.involves(t.id) } }
                .sortedWith(compareByDescending<Game> { it.day }.thenByDescending { it.id }).take(6)
            results.forEach { g ->
                val h = lg.team(g.home); val a = lg.team(g.away)
                names += aliases(h); names += aliases(a)
                appendLine("RESULT day ${g.day}: ${h.name} ${g.hs}-${g.aws} ${a.name}; winner ${lg.team(g.winner()).name}; ${g.type.name} ${g.label}.")
                g.box?.let { b -> (b.home + b.away).filter { line -> featured.any { it.id == line.pid } }.forEach { line ->
                    appendLine("BOX ${line.name}: ${line.s.pts} PTS, ${line.s.reb} REB, ${line.s.ast} AST, ${line.s.stl} STL, ${line.s.blk} BLK, ${line.s.tov} TOV.")
                } }
                featured.forEach { p -> p.log.lastOrNull { it.gameId == g.id }?.let { l ->
                    appendLine("GAME LINE ${p.name}: ${l.pts} PTS, ${l.reb} REB, ${l.ast} AST, ${l.stl} STL, ${l.blk} BLK, ${l.tov} TOV, ${l.min} MIN.")
                } }
            }
            featured.forEach { p ->
                if (p in namedPlayers) lg.teams.firstOrNull { it.id == p.teamId }?.let { t -> appendLine("PLAYER EDITORIAL ${p.name}: ${AiTemplates.previewCopy(lg, t, p)}") }
                appendLine("PLAYER ${p.name}; ${p.posLabel}; class ${p.cls}; team ${lg.teams.firstOrNull { it.id == p.teamId }?.name ?: "unattached"}; age ${p.age}; height ${p.height} in; weight ${p.weight} lb; recruit rank ${p.recruitRank}; rating ${p.ovr}; potential ${p.pot}.")
                val best = Attr.entries.maxBy { p[it] }; val worst = Attr.entries.minBy { p[it] }
                appendLine("SCOUT ${p.name}: strongest ${best.label} ${p[best]}; weakest ${worst.label} ${p[worst]}.")
                if (p.stats.gp > 0) appendLine("CURRENT ${p.name}: ${stats(p.stats)}.") else appendLine("CURRENT ${p.name}: no appearances; no current per-game production established.")
                p.career.lastOrNull { it.season == lg.season - 1 }?.let { s -> appendLine("LAST SEASON ${p.name}, ${s.season}, ${s.team}: ${stats(s.s)}.") }
                appendLine("AVAILABILITY ${p.name}: ${if (p.out) "out: ${p.injury.ifBlank { "injury" }}; ${if (p.injGames >= Injuries.SEASON_OUT) "season-ending" else "${p.injGames} team games remaining"}" else "no current injury absence"}.")
                if (lg.pro) appendLine("CONTRACT ${p.name}: salary ${p.salary} thousand dollars (${Road.dollars(p.salary)}); years remaining ${p.contractYears}.")
            }
            (lg.games.filter { !it.played && teams.any { t -> it.involves(t.id) } }.sortedBy { it.day }.take(3) + teams.mapNotNull { AiTemplates.notableGame(lg, it) }).distinctBy { it.id }.forEach { g ->
                val h = lg.team(g.home); val a = lg.team(g.away)
                names += aliases(h); names += aliases(a)
                appendLine("SCHEDULE day ${g.day}: ${a.name} at ${h.name}${if (g.neutral) " (neutral)" else ""}${lg.rivalry(g)?.let { "; rivalry ${it.name}" } ?: ""}; not played.")
            }
            if (extra.isNotBlank()) appendLine("EVENT FACTS: $extra")
        }
        val shortNames = mutableMapOf<String, String>()
        teams.filter { t -> t.mascot.length > 2 && lg.teams.count { it.mascot.equals(t.mascot, true) } == 1 }.forEach { shortNames[it.mascot] = it.name }
        featured.forEach { p ->
            val last = p.name.substringAfterLast(' ')
            if (last.length > 2 && population.count { it.name.substringAfterLast(' ').equals(last, true) } == 1 && lg.coaches.none { it.name.substringAfterLast(' ').equals(last, true) }) shortNames[last] = p.name
        }
        return AiFactSheet(text, names, allNames, records, lg.phase, preseason, preview, upcoming, shortNames)
    }

    fun version(lg: League): String {
        val snapshot = buildString {
            append("grounded2/${lg.season}/${lg.day}/${lg.phase}/${lg.userTeamId}/${lg.pro}")
            lg.teams.forEach { append("|${it.id}:${it.name}:${it.record}:${it.confRecord}:${it.ap}:${it.net}:${it.elo}") }
            (lg.players + lg.alumni).forEach { append("|${it.id}:${it.name}:${it.teamId}:${it.ovr}:${it.injGames}:${it.injury}:${it.salary}:${it.contractYears}:${it.stats.pts}:${it.stats.gp}:${it.stats.reb}:${it.stats.ast}:${it.attrs.contentHashCode()}") }
            lg.coaches.forEach { append("|${it.name}:${it.teamId}:${it.security}:${it.titles}") }
            lg.games.filter { it.played }.forEach { append("|${it.id}:${it.hs}:${it.aws}") }
        }
        return MessageDigest.getInstance("SHA-256").digest(snapshot.toByteArray()).take(12).joinToString("") { "%02x".format(it) }
    }
}

object AiGrounding {
    val instructions = """Use ONLY the fact sheet for names, statistics, records, scores, injuries, history and events. Do not invent play sequences, quotes, reactions, medical advice, contract demands or transactions. Missing information is unknown: omit it. Distinguish previous-season statistics, current records and simulated forecasts. Never present forecasts as results or certainties. Use the stated phase, not a real-world calendar assumption. Use sentence case, full supplied player/team names, digits for numbers and exact supplied numeric formatting. Do not introduce other named people or teams (including player comparisons). Use Staff for a reporter and Courtside Report for an outlet. Keep the requested output fields. Fictional player/coach dialogue may express opinions or intentions, but must not invent factual events. Never mention simulations, assumptions or rosters in the copy; describe players, projections and games directly."""
    private val number = Regex("(?<![\\p{L}\\d])\\d+(?:\\.\\d+)?%?")
    private val pair = Regex("\\b(\\d{1,3})\\s*[-–—]\\s*(\\d{1,3})\\b")
    private fun normalized(n: String) = BigDecimal(n.removeSuffix("%")).stripTrailingZeros().toPlainString() + if (n.endsWith("%")) "%" else ""
    private fun canonicalPairs(text: String) = pair.findAll(text).map { "${it.groupValues[1].toInt()}-${it.groupValues[2].toInt()}" }.toSet()
    private val wordValues = (listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen").mapIndexed { i, s -> s to i } +
        listOf("twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety").mapIndexed { i, s -> s to (i + 2) * 10 }).toMap()
    private fun numericWords(text: String): String = Regex("\\b(${wordValues.keys.joinToString("|")})(?:[ -](one|two|three|four|five|six|seven|eight|nine))?(?=\\s+(?:wins?|losses|points?|rebounds?|assists?)\\b)", RegexOption.IGNORE_CASE).replace(text) { m ->
        m.value.lowercase(Locale.US).split(' ', '-').sumOf { wordValues.getValue(it) }.toString()
    }

    private fun equivalent(a: String, b: String): Boolean {
        if (a.endsWith('%') != b.endsWith('%')) return false
        val x = a.removeSuffix("%"); val y = b.removeSuffix("%")
        if (!x.contains('.') && !y.contains('.')) return x == y
        val digits = minOf(x.substringAfter('.', "").length, y.substringAfter('.', "").length)
        return kotlin.math.abs(x.toDouble() - y.toDouble()) <= 0.5 * Math.pow(10.0, -digits.toDouble()) + 1e-9
    }

    private fun canonicalText(text: String, facts: AiFactSheet): String {
        var result = text
        val names = facts.entityAliases.values.distinct().sortedByDescending { it.length }
        fun word(s: String) = Regex("(?i)(?<![\\p{L}\\d])${Regex.escape(s)}(?![\\p{L}\\d])")
        names.forEachIndexed { i, name -> result = result.replace(word(name), "@@ENTITY$i@@") }
        facts.entityAliases.entries.sortedByDescending { it.key.length }.forEach { (alias, name) -> result = result.replace(word(alias), "@@ENTITY${names.indexOf(name)}@@") }
        names.forEachIndexed { i, name -> result = result.replace("@@ENTITY$i@@", name) }
        return result
    }

    fun validate(text: String, facts: AiFactSheet): List<String> {
        if (text.isBlank()) return listOf("empty response")
        val errors = mutableListOf<String>()
        val content = numericWords(canonicalText(text, facts)).lines().filterNot { it.startsWith("OUTLET:", true) || it.startsWith("REPORTER:", true) }
            .joinToString("\n") { it.replace(Regex("^(SCOOP|RUMOR) \\d+:"), "ITEM:") }
        val numbers = number.findAll(facts.text).map { it.value }.toSet()
        if (number.findAll(content).any { m ->
                val rate = m.value.endsWith('%') || Regex("^\\s*(PPG|RPG|APG|MPG|points per game|rebounds per game|assists per game)\\b", RegexOption.IGNORE_CASE).containsMatchIn(content.substring(m.range.last + 1))
                numbers.none { normalized(m.value) == normalized(it) || (rate && equivalent(m.value, it)) }
            }) errors += "unsupported number"
        Regex("(\\d+(?:\\.\\d+)?)\\s*(PPG|RPG|APG|PTS|REB|AST|STL|BLK|TOV|MIN|points|rebounds|assists)\\b", RegexOption.IGNORE_CASE).findAll(content).forEach { m ->
            val prefix = content.substring((m.range.first - 100).coerceAtLeast(0), m.range.first)
            val player = facts.names.mapNotNull { name ->
                Regex(Regex.escape(name), RegexOption.IGNORE_CASE).findAll(prefix).lastOrNull()?.let { name to it.range.first }
            }.filter { (name, _) -> facts.text.lines().any { it.startsWith("PLAYER $name;") } }.maxByOrNull { it.second }?.first
            if (player != null) {
                val perGame = Regex("averag", RegexOption.IGNORE_CASE).containsMatchIn(prefix) || content.substring(m.range.last + 1).trimStart().startsWith("per game", true)
                val metric = when (m.groupValues[2].uppercase(Locale.US)) {
                    "POINTS" -> if (perGame) "PPG" else "PTS"
                    "REBOUNDS" -> if (perGame) "RPG" else "REB"
                    "ASSISTS" -> if (perGame) "APG" else "AST"
                    else -> m.groupValues[2].uppercase(Locale.US)
                }
                val expected = facts.text.lines().filter { if (metric in setOf("PPG", "RPG", "APG"))
                    it.startsWith("CURRENT $player:") || it.startsWith("LAST SEASON $player,")
                    else it.startsWith("BOX $player:") || it.startsWith("GAME LINE $player:") }
                    .flatMap { Regex("(\\d+(?:\\.\\d+)?) $metric\\b", RegexOption.IGNORE_CASE).findAll(it).map { match -> match.groupValues[1] }.toList() }.toSet()
                if (expected.none { if (metric in setOf("PPG", "RPG", "APG")) equivalent(m.groupValues[1], it) else normalized(m.groupValues[1]) == normalized(it) }) errors += "statistic belongs to another player"
            }
        }
        val scores = facts.text.lines().filter { it.startsWith("RESULT") || it.startsWith("EVENT FACTS") }.flatMap { canonicalPairs(it) }
        val allowedPairs = canonicalPairs(facts.text) + scores.map { p -> p.split('-').let { v -> "${v[1]}-${v[0]}" } }
        pair.findAll(content).forEach { m ->
            val value = "${m.groupValues[1].toInt()}-${m.groupValues[2].toInt()}"
            if (value !in allowedPairs) errors += "unsupported record or score"
            val prefix = content.substring((m.range.first - 90).coerceAtLeast(0), m.range.first)
            if (Regex("(?:record|sits? at|stands? at|is|are|\\()\\s*$", RegexOption.IGNORE_CASE).containsMatchIn(prefix) &&
                !Regex("last season|last year|previous|project|forecast|conference", RegexOption.IGNORE_CASE).containsMatchIn(prefix)) {
                val named = facts.records.keys.mapNotNull { name -> Regex(Regex.escape(name), RegexOption.IGNORE_CASE).findAll(prefix).lastOrNull()?.let { name to it.range.first } }.maxByOrNull { it.second }?.first
                if (named != null && value !in facts.records.getValue(named)) errors += "record belongs to another team"
            }
        }
        val known = facts.names.sortedByDescending { it.length }
        var residue = content
        known.forEach { name -> residue = residue.replace(Regex("(?i)(?<![\\p{L}\\d])${Regex.escape(name)}(?![\\p{L}\\d])"), "entity") }
        facts.allNames.filter { it !in facts.names && it.length > 3 && residue.contains(it, ignoreCase = true) }.forEach { name ->
            if (Regex("(?i)(?<![\\p{L}\\d])${Regex.escape(name)}(?![\\p{L}\\d])").containsMatchIn(residue)) errors += "entity outside fact sheet"
        }
        val common = setOf("Final Four", "Sweet Sixteen", "Sweet Sixteen", "Elite Eight", "Head Coach", "Coach", "Game Winner", "The Favorite", "New Era", "Courtside Report", "National Championship", "March Madness", "All America", "Most Valuable Player", "First Team", "Second Team", "Third Team", "Staff", "Hot Seat")
        common.sortedByDescending { it.length }.forEach { residue = residue.replace(it, "topic", ignoreCase = true) }
        val prose = residue.lines().filterNot { Regex("(?i)^(HEADLINE|KICKER|TITLE|HEAD):").containsMatchIn(it) }.joinToString("\n")
        val headers = residue.lines().filter { Regex("(?i)^(HEADLINE|KICKER|TITLE|HEAD):").containsMatchIn(it) }.joinToString("\n")
        val firstNames = facts.allNames.map { it.substringBefore(' ') }.toSet()
        val properName = Regex("\\b[A-Z][a-z]+(?:[ '-][A-Z][a-z]+)+\\b")
        if (properName.containsMatchIn(prose) || properName.findAll(headers).any { it.value.substringBefore(' ') in firstNames }) errors += "unknown proper name"
        val subjects = setOf("He", "She", "It", "This", "That", "There", "They", "We", "What", "Who", "Basketball", "March", "April", "Defense", "Offense", "Depth", "Availability", "Continuity", "Nothing", "Everyone", "Nobody", "Something")
        if (Regex("\\b([A-Z][a-z]+)\\s+(?:is|are|has|went|won|lost)\\b").findAll(prose).any { it.groupValues[1] !in subjects }) errors += "unknown named subject"
        val lower = content.lowercase(Locale.US)
        if ((facts.preseason || facts.preview || facts.upcoming) && Regex("\\b(tonight's win|tonight's loss|won tonight|lost tonight|just defeated|clinched|eliminated|advanced to|celebrated the win)\\b").containsMatchIn(lower)) errors += "result claimed before event"
        if (facts.phase == Phase.OFFSEASON && Regex("\\b(tonight's game|tonight's win|tonight's loss|tomorrow's game)\\b").containsMatchIn(lower)) errors += "wrong season phase"
        if (!facts.preseason && !facts.preview && Regex("\\b(before the season begins|season has yet to begin|no games have been played)\\b").containsMatchIn(lower) && facts.phase != Phase.OFFSEASON) errors += "wrong season phase"
        if (facts.phase == Phase.REGULAR && !facts.preview && Regex("\\b(won the championship|won the title|clinched the title|playoff victory|postseason victory)\\b").containsMatchIn(lower)) errors += "wrong season phase"
        if (Regex("\\b(buzzer.beater|game.winning shot|late run|closing minutes|fourth.quarter run|second.chance points|locker.room champagne|parade|contract demand|demanded a trade|physicians confirmed|doctors confirmed|non.contact conditioning|medically cleared)\\b").containsMatchIn(lower)) errors += "unsupported event detail"
        return errors.distinct()
    }

    suspend fun generate(facts: AiFactSheet, request: String, validateDraft: (String) -> List<String> = { emptyList() }, producer: suspend (String) -> Result<String>): Result<String> {
        var reasons = ""
        val format = request.substringAfter("Format your response EXACTLY as:", request.substringAfter("Format each storyline as:", ""))
        val labels = Regex("(?m)^([A-Z][A-Z_ ]*(?: \\d+)?):(?:\\s*<|\\s*$)").findAll(format).map { it.groupValues[1].trim() }.distinct().toList()
        repeat(2) { attempt ->
            val prompt = "$instructions\n\nFACT SHEET\n${facts.text}\nEND FACT SHEET\n\nWRITING REQUEST\n$request" +
                if (attempt == 0) "" else "\nYour previous draft was rejected ($reasons). Rewrite conservatively from the fact sheet only."
            val result = try { producer(prompt) } catch (e: CancellationException) { throw e } catch (e: Exception) { Result.failure(e) }
            (result.exceptionOrNull() as? CancellationException)?.let { throw it }
            if (result.isSuccess) {
                val raw = result.getOrThrow()
                val output = raw.lines()
                val missing = labels.filter { label ->
                    val index = output.indexOfFirst { it.startsWith("$label:", true) }
                    index < 0 || (output[index].substringAfter(':').isBlank() &&
                        (label !in setOf("BODY", "STORY", "ANALYSIS") || output.drop(index + 1)
                            .takeWhile { line -> labels.none { line.startsWith("$it:", true) } }.none { it.isNotBlank() }))
                }
                val pipeFields = labels.filter { it.startsWith("KEY ") || it.startsWith("SCOOP ") || it.startsWith("RUMOR ") }
                val malformed = pipeFields.filter { label -> raw.lines().firstOrNull { it.startsWith("$label:", true) }?.substringAfter(':')?.split('|')?.let { it.size != 2 || it.any(String::isBlank) } != false }
                val errors = validate(raw, facts) + missing.map { "missing $it field" } + malformed.map { "malformed $it field" } + validateDraft(raw)
                if (errors.isEmpty()) return Result.success(raw.lines().joinToString("\n") { line ->
                    val key = line.substringBefore(':').uppercase(Locale.US)
                    if (key in labels) "$key:${line.substringAfter(':')}" else line
                })
                reasons = errors.joinToString(", ")
            } else reasons = "generation unavailable"
        }
        return Result.failure(IllegalStateException("Could not verify the draft after two attempts ($reasons). The existing game-written content was kept."))
    }
}
