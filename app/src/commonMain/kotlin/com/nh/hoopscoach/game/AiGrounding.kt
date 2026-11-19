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
    val acronyms: Set<String> = emptySet(),
    /** Real capitalized phrases that aren't people: mascots, conferences, honors (see [AiGrounding.validate]). */
    val phrases: Set<String> = emptySet(),
    /** Every first name and surname the game knows (its name pools and this league's people). */
    val nameWords: Set<String> = emptySet(),
)

object AiFacts {
    private fun aliases(t: Team) = listOf(t.name, t.school, t.abbr).filter { it.isNotBlank() }
    private fun mentioned(text: String, name: String) = text.contains(name, ignoreCase = true) &&
        Regex("(?i)(?<![\\p{L}\\d])${Regex.escape(name)}(?![\\p{L}\\d])").containsMatchIn(text)
    private fun stats(s: StatLine) = String.format(Locale.US, "%d GP; %.1f PPG; %.1f RPG; %.1f APG; %.1f MPG; %.1f%% FG; %.1f%% 3PT; %.1f%% FT", s.gp, s.ppg, s.rpg, s.apg, s.mpg, s.fgPct * 100, s.tpPct * 100, s.ftPct * 100)

    /**
     * The fact sheet for one request. [lean]: for Gemini Nano's small window (about 4,000 tokens, and every token
     * spends its on-device allowance): the request's own teams (up to three), its players and each team's best,
     * recent results, no long editorials. Every team and player the request names stays allowed either way.
     */
    fun build(lg: League, request: String, extra: String = "", players: List<Player> = emptyList(), preview: Boolean = false, upcoming: Boolean = false, eventGame: Game? = null, lean: Boolean = false): AiFactSheet {
        // Snapshot before iterating — auto article desks run on IO while the sim mutates these lists.
        val allPlayers = LeagueLists.players(lg)
        val alumni = LeagueLists.alumni(lg)
        val recruits = LeagueLists.recruits(lg)
        val leagueTeams = LeagueLists.teams(lg)
        val coaches = LeagueLists.coaches(lg)
        val games = LeagueLists.games(lg)
        val population = (allPlayers + alumni + recruits.map { it.p } + players).distinctBy { it.id }
        val context = request + "\n" + extra
        val namedPlayers = population.filter { mentioned(context, it.name) }
        val gameReport = eventGame != null && !preview && !upcoming
        // Game/series sheets stay under the local 8192 context budget: only the two clubs,
        // box principals, and a micro title brief — no abbr/surname false-positives or desk fluff.
        val eventTeams = eventGame?.let { listOf(lg.team(it.home), lg.team(it.away)) }.orEmpty()
        val situationTeams = if (gameReport || lean) emptyList() else SituationAwareness.namedTeams(lg).take(3)
        // In the order the request names them, so the subject comes first.
        val mentionedTeams = if (gameReport) emptyList() else leagueTeams.filter { t -> aliases(t).any { mentioned(context, it) } }
            .sortedBy { t -> aliases(t).map { context.indexOf(it) }.filter { it >= 0 }.minOrNull() ?: Int.MAX_VALUE }
        val teams = (eventTeams + mentionedTeams + (if (gameReport) emptyList() else namedPlayers.mapNotNull { p -> leagueTeams.firstOrNull { it.id == p.teamId } }) +
            (if (gameReport) emptyList() else listOf(lg.user)) + situationTeams)
            .distinctBy { it.id }.take(if (gameReport) 2 else if (lean) 3 else 8)
        val eventPlayerIds = eventGame?.box?.let { b -> (b.home + b.away).map { it.pid }.toSet() }.orEmpty()
        val featured = if (gameReport) {
            // Only box principals / named players who actually played this game — not same-name
            // collisions elsewhere in the league population.
            (players + namedPlayers.filter { it.id in eventPlayerIds || teams.any { t -> it.teamId == t.id } } +
                teams.flatMap { t -> lg.rosterOf(t).filter { it.out } }).distinctBy { it.id }
        } else {
            if (lean) (namedPlayers + players + teams.flatMap { t -> lg.rosterOf(t).sortedByDescending { it.ovr }.take(1) } +
                teams.flatMap { t -> lg.rosterOf(t).filter { it.out } }).distinctBy { it.id }.take(6)
            else (namedPlayers + players + teams.flatMap { t -> lg.rosterOf(t).sortedByDescending { it.ovr }.take(2) } +
                teams.flatMap { t -> lg.rosterOf(t).filter { it.out } } + teams.mapNotNull { AiTemplates.newcomer(lg, it) } +
                teams.mapNotNull { AiTemplates.departure(lg, it, allPlayers, alumni) }).distinctBy { it.id }
        }
        val names = (teams.flatMap { aliases(it) } + featured.map { it.name } + teams.map { lg.coachOf(it).name } +
            teams.map { lg.conf(it.confId).name } + (if (gameReport) emptyList() else lg.rivals.map { it.name }) +
            featured.flatMap { listOf(it.hometown, it.highSchool, it.injury) }.filter { it.isNotBlank() }).toMutableSet()
        // The request named them, so the writer may too, even when the sheet has no room for their details.
        if (!gameReport) {
            names += mentionedTeams.flatMap { aliases(it) }
            names += namedPlayers.map { it.name }
        }
        val allNames = (leagueTeams.flatMap { aliases(it) } + population.map { it.name } + coaches.map { it.name }).toSet()
        val records = mutableMapOf<String, Set<String>>()
        val preseason = lg.phase == Phase.REGULAR && leagueTeams.all { it.gp == 0 }
        val text = buildString {
            appendLine("SEASON ${lg.season}; DATE day ${lg.day}; PHASE ${lg.phase.name}${if (preseason) " (preseason; no games played)" else ""}.")
            appendLine("League: ${if (lg.pro) "NBA" else "college basketball"}. Forecasts are simulation probabilities, not results. Missing facts are unknown.")
            if (preview) appendLine("PURPOSE: preseason magazine. Discuss forecasts and last season; do not claim this season has been played.")
            if (upcoming) appendLine("PURPOSE: upcoming matchup. Do not announce a result or postgame reaction.")
            appendLine(SituationAwareness.microBrief(lg).trimEnd())
            teams.forEach { t ->
                val order = leagueTeams.filter { it.confId == t.confId }.sortedWith(compareByDescending<Team> {
                    if (lg.pro) it.winPct else if (it.cw + it.cl > 0) it.cw.toDouble() / (it.cw + it.cl) else 0.0
                }.thenByDescending { it.elo }.thenBy { it.id })
                val rank = if (t.gp == 0) "standings not established" else "conference standing ${order.indexOf(t) + 1} of ${order.size} (ties ordered by Elo)"
                val bannerYears = TitleDefense.titles(lg, t)
                appendLine("TEAM ${t.name} (${t.abbr}); current record ${t.record}; conference ${lg.conf(t.confId).name}, ${t.confRecord}; $rank; AP ${t.ap.takeIf { it > 0 } ?: "unranked"}; NET ${t.net.takeIf { it > 0 } ?: "unranked"}; prestige ${t.prestige}/100; program titles ${t.titles}; national titles ${bannerYears.size}${bannerYears.lastOrNull()?.let { " (last $it)" } ?: ""}.")
                val c = lg.coachOf(t)
                appendLine("COACH ${c.name}; job security ${c.security}/100; titles ${c.titles}; recorded seasons at this team ${c.history.count { it.teamId == t.id }}.")
                if (!lg.pro && t.id == lg.userTeamId) appendLine("TITLE HISTORY ${t.school}: ${CollegeHistory.line(lg, t)}")
                if (!gameReport) t.history.lastOrNull { it.season == lg.season - 1 }?.let { h ->
                    appendLine("TEAM last season ${h.season}: ${h.w}-${h.l}; ${h.result}.")
                }
                if (!gameReport) {
                    if (lg.pro) lg.proState?.preview?.takeIf { it.season == lg.season }?.proj?.get(t.id)?.let { p ->
                        appendLine("FORECAST ${t.name}: ${p.w}-${p.l}; win percentiles ${p.lo}/${p.median ?: "unknown"}/${p.hi}; playoffs ${PreviewWriting.percent(p.playoffs)}; Finals ${PreviewWriting.percent(p.finals)}; title ${PreviewWriting.percent(p.title)}.")
                    } else lg.preview?.takeIf { it.season == lg.season }?.let { pv ->
                        pv.proj[t.id]?.let { p -> appendLine("FORECAST ${t.name}: rank ${p.nat}; ${p.w}-${p.l}.") }
                        pv.odds[t.id]?.let { o -> appendLine("ODDS ${t.name}: NCAA ${PreviewWriting.percent(o.ncaa)}; conference ${PreviewWriting.percent(o.conf)}; Final Four ${PreviewWriting.percent(o.f4)}; title ${PreviewWriting.percent(o.title)}; win percentiles ${o.lo}/${o.median ?: "unknown"}/${o.hi}.") }
                    }
                    if (!lean || t == teams.first()) appendLine("EDITORIAL ${if (lg.pro) t.name else t.school}: ${AiTemplates.previewCopy(lg, t)}")
                }
                // "Los Angeles" is two NBA teams: only a name that means one team ties a record to it.
                aliases(t).filter { a -> teams.count { o -> aliases(o).any { it.equals(a, true) } } == 1 }.forEach { records[it] = setOf(t.record) }
            }
            val results = (eventGame?.let { listOf(it) } ?: games).filter { it.played && it.day <= lg.day && teams.any { t -> it.involves(t.id) } }
                .sortedWith(compareByDescending<Game> { it.day }.thenByDescending { it.id }).take(if (gameReport) 1 else if (lean) 3 else 6)
            results.forEach { g ->
                val h = lg.team(g.home); val a = lg.team(g.away)
                names += aliases(h); names += aliases(a)
                appendLine("RESULT day ${g.day}: ${h.name} ${g.hs}-${g.aws} ${a.name}; winner ${lg.team(g.winner()).name}; ${g.type.name} ${g.label}.")
                g.box?.let { b -> (b.home + b.away).filter { line -> featured.any { it.id == line.pid } }.forEach { line ->
                    appendLine("BOX ${line.name}: ${line.s.pts} PTS, ${line.s.reb} REB, ${line.s.ast} AST, ${line.s.stl} STL, ${line.s.blk} BLK, ${line.s.tov} TOV, ${line.s.fgm} FGM, ${line.s.fga} FGA, ${line.s.ftm} FTM, ${line.s.fta} FTA.")
                } }
                g.box?.let { b ->
                    listOf(h to b.home, a to b.away).forEach { (team, lines) ->
                        val top3 = lines.sortedByDescending { it.s.pts }.take(3).sumOf { it.s.pts }
                        val teamPts = if (team.id == g.home) g.hs else g.aws
                        appendLine("TEAM BOX ${team.name}: ${lines.sumOf { it.s.fgm }} FGM, ${lines.sumOf { it.s.fga }} FGA, ${lines.sumOf { it.s.ftm }} FTM, ${lines.sumOf { it.s.fta }} FTA, ${lines.sumOf { it.s.ast }} AST; leading trio $top3 of $teamPts.")
                    }
                    b.halves.forEachIndexed { index, scores -> if (scores.size >= 2) appendLine("PERIOD ${index + 1}: ${h.name} ${scores[0]}, ${a.name} ${scores[1]}; score pair ${scores[0]}-${scores[1]}.") }
                    val half = if (b.quarters) b.halves.take(2).takeIf { it.size == 2 }?.let { p -> intArrayOf(p.sumOf { it[0] }, p.sumOf { it[1] }) }
                        else b.halves.firstOrNull()
                    if (half != null) {
                        val cushion = kotlin.math.abs(half[0] - half[1])
                        appendLine("HALFTIME: ${h.name} ${half[0]}, ${a.name} ${half[1]}; pair ${half[0]}-${half[1]}; cushion $cushion; after break ${g.hs - half[0]}-${g.aws - half[1]}.")
                    }
                    appendLine("MARGIN: ${kotlin.math.abs(g.hs - g.aws)}.")
                }
                if (g.type == GameType.PLAYOFF || g.type == GameType.NCAA || g.type == GameType.CONF_TOURNEY) {
                    val sf = ArticleWriting.seriesFacts(lg, g)
                    if (sf.isNotBlank()) appendLine(sf)
                }
                featured.forEach { p -> p.log.lastOrNull { it.gameId == g.id }?.let { l ->
                    appendLine("GAME LINE ${p.name}: ${l.pts} PTS, ${l.reb} REB, ${l.ast} AST, ${l.stl} STL, ${l.blk} BLK, ${l.tov} TOV, ${l.min} MIN.")
                } }
            }
            featured.forEach { p ->
                if (!gameReport && !lean && p in namedPlayers) leagueTeams.firstOrNull { it.id == p.teamId }?.let { t -> appendLine("PLAYER EDITORIAL ${p.name}: ${AiTemplates.previewCopy(lg, t, p)}") }
                appendLine("PLAYER ${p.name}; ${p.posLabel}; class ${p.cls}; team ${leagueTeams.firstOrNull { it.id == p.teamId }?.name ?: "unattached"}; age ${p.age}; height ${p.height} in; weight ${p.weight} lb; recruit rank ${p.recruitRank}; rating ${p.ovr}; potential ${p.pot}.")
                if (!gameReport) {
                    val best = Attr.entries.maxBy { p[it] }; val worst = Attr.entries.minBy { p[it] }
                    appendLine("SCOUT ${p.name}: strongest ${best.label} ${p[best]}; weakest ${worst.label} ${p[worst]}.")
                }
                if (p.stats.gp > 0) appendLine("CURRENT ${p.name}: ${stats(p.stats)}.") else appendLine("CURRENT ${p.name}: no appearances; no current per-game production established.")
                if (!gameReport) p.career.lastOrNull { it.season == lg.season - 1 }?.let { s -> appendLine("LAST SEASON ${p.name}, ${s.season}, ${s.team}: ${stats(s.s)}.") }
                appendLine("AVAILABILITY ${p.name}: ${if (p.out) "out: ${p.injury.ifBlank { "injury" }}; ${if (p.injGames >= Injuries.SEASON_OUT) "season-ending" else "${p.injGames} team games remaining"}" else "no current injury absence"}.")
                if (lg.pro && !gameReport) appendLine("CONTRACT ${p.name}: salary ${p.salary} thousand dollars (${Road.dollars(p.salary)}); years remaining ${p.contractYears}.")
            }
            val scheduleGames = if (gameReport) {
                games.filter { !it.played && teams.any { t -> it.involves(t.id) } }.sortedBy { it.day }.take(2)
            } else {
                if (lean) games.filter { !it.played && teams.any { t -> it.involves(t.id) } }.sortedBy { it.day }.take(2)
                else (games.filter { !it.played && teams.any { t -> it.involves(t.id) } }.sortedBy { it.day }.take(3) + teams.mapNotNull { AiTemplates.notableGame(lg, it, games) }).distinctBy { it.id }
            }
            scheduleGames.forEach { g ->
                val h = lg.team(g.home); val a = lg.team(g.away)
                names += aliases(h); names += aliases(a)
                appendLine("SCHEDULE day ${g.day}: ${a.name} at ${h.name}${if (g.neutral) " (neutral)" else ""}${lg.rivalry(g)?.let { "; rivalry ${it.name}" } ?: ""}; not played.")
            }
            if (extra.isNotBlank()) appendLine("EVENT FACTS: $extra")
        }
        val shortNames = mutableMapOf<String, String>()
        teams.filter { t -> t.mascot.length > 2 && teams.count { it.mascot.equals(t.mascot, true) } == 1 }.forEach { shortNames[it.mascot] = it.name }
        featured.forEach { p ->
            val last = p.name.substringAfterLast(' ')
            if (last.length > 2 && featured.count { it.name.substringAfterLast(' ').equals(last, true) } == 1 && teams.none { lg.coachOf(it).name.substringAfterLast(' ').equals(last, true) } && names.none { it.equals(last, true) }) {
                shortNames[last] = p.name
                val initial = p.name.firstOrNull()?.uppercaseChar()
                if (initial != null) shortNames["$initial. $last"] = p.name
            }
        }
        val properNames = leagueTeams.flatMap { listOf(it.name, it.school) } + population.map { it.name } + coaches.map { it.name }
        val acronyms = leagueTeams.map { it.abbr }.filter { abbreviation -> properNames.none { it.equals(abbreviation, true) } }.toSet()
        // Mascots ("Tar Heels"), conferences ("Big Ten") and honors are real, capitalized and not people.
        // ...and so are the capitalized phrases the game itself wrote into the request (a reporter's byline, an outlet).
        val phrases = (leagueTeams.map { it.mascot } + lg.confs.flatMap { listOf(it.name, it.abbr) } +
            Regex("\\b[A-Z][a-z]+(?:[ '-][A-Z][a-z]+)+\\b").findAll(request).map { it.value }).filter { it.length > 2 }.toSet()
        val nameWords = (population.map { it.name } + coaches.map { it.name }).flatMap { it.split(' ') }.filter { it.length > 1 }.toSet() + Names.first + Names.last
        return AiFactSheet(text, names, allNames, records, lg.phase, preseason, preview, upcoming, shortNames, acronyms, phrases, nameWords)
    }

    fun version(lg: League): String {
        val teams = LeagueLists.teams(lg)
        val players = LeagueLists.players(lg)
        val alumni = LeagueLists.alumni(lg)
        val coaches = LeagueLists.coaches(lg)
        val games = LeagueLists.games(lg)
        val history = LeagueLists.history(lg)
        val snapshot = buildString {
            append("grounded3/${lg.season}/${lg.day}/${lg.phase}/${lg.userTeamId}/${lg.pro}")
            teams.forEach { append("|${it.id}:${it.name}:${it.record}:${it.confRecord}:${it.ap}:${it.net}:${it.elo}:${it.titles}:${it.streak}") }
            TitleDefense.champion(lg)?.let { append("|champ:${it.id}") }
            history.takeLast(3).forEach { append("|hist:${it.season}:${it.champion}") }
            (players + alumni).forEach { append("|${it.id}:${it.name}:${it.teamId}:${it.ovr}:${it.injGames}:${it.injury}:${it.salary}:${it.contractYears}:${it.stats.pts}:${it.stats.gp}:${it.stats.reb}:${it.stats.ast}:${it.attrs.contentHashCode()}") }
            coaches.forEach { append("|${it.name}:${it.teamId}:${it.security}:${it.titles}") }
            games.filter { it.played }.forEach { append("|${it.id}:${it.hs}:${it.aws}") }
        }
        return MessageDigest.getInstance("SHA-256").digest(snapshot.toByteArray()).take(12).joinToString("") { "%02x".format(it) }
    }
}

object AiGrounding {
    /** Honors and basketball phrases a writer capitalizes that aren't names. */
    private val HONORS = setOf(
        "All-American", "All-America", "All-Conference", "All-Defensive", "All-Rookie", "All-Star", "All-Tournament", "All-Freshman",
        "Player of the Year", "Coach of the Year", "Rookie of the Year", "Freshman of the Year", "Sixth Man", "Defensive Player",
        "Selection Sunday", "Senior Night", "Senior Day", "Midnight Madness", "Big Dance", "Draft Night", "Media Day", "Game Day",
        "Hall of Fame", "Mr. Basketball", "Tobacco Road", "Big Man", "Point God",
    )

    /** Ordinary words that start a sentence of dialogue ("Tonight is...", "Everybody has..."). */
    private val STARTERS = setOf(
        "Everybody", "Everything", "Everyone", "Somebody", "Someone", "Anybody", "Anyone", "Nothing", "Today", "Tonight", "Tomorrow", "Yesterday",
        "Winning", "Losing", "Effort", "Energy", "Confidence", "Toughness", "Execution", "Rebounding", "Shooting", "Passing", "Practice",
        "Chemistry", "Teamwork", "Leadership", "Pressure", "Momentum", "Experience", "Focus", "Respect", "Family", "Life", "Credit",
        "Trust", "Belief", "Talent", "Health", "Recruiting", "Coaching", "Defending", "Scoring", "Each", "Every", "One",
        "Here", "Where", "When", "Why", "How", "Which", "Mine", "Ours", "Yours", "His", "Hers", "Theirs", "Ball", "Game", "Season",
        "Team", "Character", "Culture", "Discipline", "Attitude", "Preparation", "Hard", "Nobody's", "Consistency", "Patience",
    )

    /**
     * Ordinary words a writer capitalizes (positions, roles, days, game and media terms). A Title Case phrase made only
     * of these ("Point Guard", "Saturday Night", "Athletic Director", "Second Half Adjustments") isn't a person.
     */
    private val COMMON = ("point guard guards shooting small power forward forwards center centers wing wings big bigs coach coaches head assistant " +
        "associate athletic director staff trainer scout scouts captain captains rookie rookies freshman freshmen sophomore junior senior seniors " +
        "veteran veterans star stars starter starters bench reserve reserves walk on transfer transfers recruit recruits prospect prospects " +
        "monday tuesday wednesday thursday friday saturday sunday night nights day days morning afternoon evening weekend week month season seasons " +
        "january february march april may june july august september october november december tonight today tomorrow " +
        "game games first second third fourth half halves quarter quarters overtime tip off tipoff buzzer beater clutch time final finals four eight " +
        "sixteen round rounds seed seeds bracket tournament tourney conference league division regular post postseason playoff playoffs play in " +
        "series run runs lead comeback win wins loss losses streak record standings race title titles championship champion champions crown banner " +
        "defense defensive offense offensive transition rebounding rebounds three pointers paint glass pace tempo spacing rotation rotations lineup " +
        "lineups depth minutes adjustments adjustment keys key plan matchup matchups showdown rivalry rivals classic invitational showcase opener " +
        "home road away court floor arena gym crowd fans fan student section network report news daily weekly wire sports basketball hoops insider " +
        "desk beat column editor press media radio show notes rankings ranking poll polls top preseason preview outlook watch buzz rumor rumors scoop " +
        "takeaways grades grade program programs culture identity system era dynasty legacy future state university national nation the of and a an " +
        "to for at with way new next last best hot seat team teams squad unit group family big man men test statement fast break edge battle " +
        "upset rally road trip senior night selection sunday madness dance most valuable improved player players year mid major").split(' ').toSet()

    /** A fact-check failure in plain words, for the "game-written" note on a story. */
    fun plain(reason: String): String = when {
        reason.contains("unknown proper name") || reason.contains("unknown named subject") ->
            "the AI's draft named someone who isn't in this league" + (Regex(": ([^,)]+)").find(reason)?.groupValues?.get(1)?.let { " ($it)" } ?: "")
        reason.contains("entity outside fact sheet") -> "the AI's draft brought in a team or player who isn't part of this story"
        reason.contains("unsupported number") || reason.contains("statistic belongs") -> "the AI's draft got a number wrong"
        reason.contains("record or score") || reason.contains("record belongs") -> "the AI's draft got a score or record wrong"
        reason.contains("wrong season phase") || reason.contains("result claimed") -> "the AI's draft got the timing wrong (a result that hasn't happened)"
        reason.contains("unsupported event detail") -> "the AI's draft made up a play or detail"
        reason.contains("missing") || reason.contains("malformed") -> "the AI's draft didn't follow the format"
        reason.contains("empty response") -> "the AI sent back nothing"
        else -> reason
    }

    private fun entityLabel(name: String) = com.nh.hoopscoach.CrashReporter.redact(name)
        .replace(Regex("[\\p{Cntrl}\\p{Cf}]"), " ").replace(Regex("\\s+"), " ").trim().take(80)
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
            .joinToString("\n") { it.replace(Regex("^(?:SCOOP|RUMOR|KEY)\\s*\\d+:"), "ITEM:").replace(Regex("^\\s*\\d+\\s*\\|"), "PID|") }
        val numbers = number.findAll(facts.text).map { it.value }.toSet()
        if (number.findAll(content).any { m ->
                val rate = m.value.endsWith('%') || Regex("^\\s*(PPG|RPG|APG|MPG|points per game|rebounds per game|assists per game)\\b", RegexOption.IGNORE_CASE).containsMatchIn(content.substring(m.range.last + 1))
                numbers.none { normalized(m.value) == normalized(it) || (rate && equivalent(m.value, it)) }
            }) errors += "unsupported number"
        Regex("(\\d+(?:\\.\\d+)?)\\s*(PPG|RPG|APG|PTS|REB|AST|STL|BLK|TOV|MIN|points|rebounds|assists)\\b", RegexOption.IGNORE_CASE).findAll(content).forEach { m ->
            val prefix = content.substring((m.range.first - 300).coerceAtLeast(0), m.range.first).split(Regex("(?<=[.!?;])\\s+(?=[A-Z])")).last()
            val player = facts.names.mapNotNull { name ->
                Regex(Regex.escape(name), RegexOption.IGNORE_CASE).findAll(prefix).lastOrNull()?.let { name to it.range.first }
            }.filter { (name, _) -> facts.text.lines().any { it.startsWith("PLAYER $name;") } }.maxByOrNull { it.second }?.first
            if (player != null) {
                val after = content.substring(m.range.last + 1).trimStart()
                val perGame = Regex("averag", RegexOption.IGNORE_CASE).containsMatchIn(prefix) ||
                    after.startsWith("per game", true) || after.startsWith("a game", true)
                val seriesStat = Regex("(?i)series").containsMatchIn(prefix) || Regex("(?i)in the series").containsMatchIn(after)
                val metric = when (m.groupValues[2].uppercase(Locale.US)) {
                    "POINTS" -> if (perGame) "PPG" else "PTS"
                    "REBOUNDS" -> if (perGame) "RPG" else "REB"
                    "ASSISTS" -> if (perGame) "APG" else "AST"
                    else -> m.groupValues[2].uppercase(Locale.US)
                }
                val expected = facts.text.lines().filter {
                    when {
                        seriesStat -> it.startsWith("SERIES LINE $player:") || it.startsWith("BOX $player:") || it.startsWith("GAME LINE $player:")
                        metric in setOf("PPG", "RPG", "APG") -> it.startsWith("CURRENT $player:") || it.startsWith("LAST SEASON $player,") || it.startsWith("SERIES LINE $player:")
                        else -> it.startsWith("BOX $player:") || it.startsWith("GAME LINE $player:") || it.startsWith("SERIES LINE $player:")
                    }
                }.flatMap { line ->
                    val fromMetric = Regex("(\\d+(?:\\.\\d+)?) $metric\\b", RegexOption.IGNORE_CASE).findAll(line).map { it.groupValues[1] }
                    val fromSeries = if (seriesStat || perGame) Regex("(\\d+(?:\\.\\d+)?) points a game", RegexOption.IGNORE_CASE).findAll(line).map { it.groupValues[1] } else emptySequence()
                    (fromMetric + fromSeries).toList()
                }.toSet()
                if (expected.none { if (metric in setOf("PPG", "RPG", "APG") || perGame) equivalent(m.groupValues[1], it) else normalized(m.groupValues[1]) == normalized(it) }) errors += "statistic belongs to another player"
            }
        }
        val scores = facts.text.lines().filter {
            it.startsWith("RESULT") || it.startsWith("EVENT FACTS") || it.startsWith("PERIOD") ||
                it.startsWith("HALFTIME") || it.startsWith("FINAL SCORE") || it.startsWith("SERIES")
        }.flatMap { canonicalPairs(it) }
        val allowedPairs = canonicalPairs(facts.text) + scores.map { p -> p.split('-').let { v -> "${v[1]}-${v[0]}" } }
        pair.findAll(content).forEach { m ->
            val value = "${m.groupValues[1].toInt()}-${m.groupValues[2].toInt()}"
            if (value !in allowedPairs) errors += "unsupported record or score"
            val prefix = content.substring((m.range.first - 90).coerceAtLeast(0), m.range.first)
            if (Regex("(?:record|sits? at|stands? at|is|are|\\()\\s*$", RegexOption.IGNORE_CASE).containsMatchIn(prefix) &&
                !Regex("last season|last year|previous|project|forecast|conference", RegexOption.IGNORE_CASE).containsMatchIn(prefix)) {
                val named = facts.records.keys.mapNotNull { name -> Regex(Regex.escape(name), RegexOption.IGNORE_CASE).findAll(prefix).lastOrNull()?.let { name to it.range.last } }.maxByOrNull { it.second }?.first
                if (named != null && value !in facts.records.getValue(named)) errors += "record belongs to another team"
            }
        }
        val known = facts.names.sortedByDescending { it.length }
        var residue = content
        known.forEach { name -> residue = residue.replace(Regex("(?i)(?<![\\p{L}\\d])${Regex.escape(name)}(?![\\p{L}\\d])"), "entity") }
        // Honors first: "All-American" isn't American University.
        HONORS.sortedByDescending { it.length }.forEach { residue = residue.replace(it, "topic", ignoreCase = true) }
        facts.allNames.filter { it !in facts.names && it.length > 3 && residue.contains(it, ignoreCase = it !in facts.acronyms) }.forEach { name ->
            val caseFlag = if (name in facts.acronyms) "" else "(?i)"
            if (Regex("$caseFlag(?<![\\p{L}\\d])${Regex.escape(name)}(?![\\p{L}\\d])").containsMatchIn(residue)) errors += "entity outside fact sheet: ${entityLabel(name)}"
        }
        val common = setOf(
            "Final Four", "Sweet Sixteen", "Elite Eight", "Head Coach", "Coach", "Game Winner", "The Favorite", "New Era",
            "Courtside Report", "National Championship", "March Madness", "All America", "Most Valuable Player",
            "First Team", "Second Team", "Third Team", "Staff", "Hot Seat", "Baseline Sports", "Hoops Wire",
            "First Round", "Conference Semifinals", "Conference Finals", "Finals", "Play-In", "NBA Cup",
        )
        (common + facts.phrases + HONORS).sortedByDescending { it.length }.forEach { residue = residue.replace(it, "topic", ignoreCase = true) }
        // A heading is in Title Case: HEADLINE:, a package's TITLE_HEADLINE: / AWARD_TITLE:, and the part before "|" on a
        // KEY / SCOOP / RUMOR line (a title) or a social post (the handle).
        val heading = Regex("(?i)^[A-Z_]*(?:HEADLINE|KICKER|TITLE|HEAD):")
        val titled = Regex("(?i)^((?:(?:KEY|SCOOP|RUMOR)\\s*\\d+|ITEM|ANALYST|FAN|HATER|TEAMMATE):)([^|]*)\\|(.*)$")
        val prose = residue.lines().filterNot { heading.containsMatchIn(it) }.joinToString("\n") { line -> titled.find(line.trim())?.let { "${it.groupValues[1]} ${it.groupValues[3]}" } ?: line }
        val headers = (residue.lines().filter { heading.containsMatchIn(it) } + residue.lines().mapNotNull { titled.find(it.trim())?.groupValues?.get(2) }).joinToString("\n")
        val firstNames = facts.allNames.map { it.substringBefore(' ') }.toSet()
        // Names with an inner capital count too (LeBron, DeAndre, McDonald).
        val properName = Regex("\\b[A-Z][a-z]*[A-Z]?[a-z]+(?:[ '-][A-Z][a-z]*[A-Z]?[a-z]+)+\\b")
        // A person's name is two or three words, not all of them ordinary; a longer Title Case run is a headline-style
        // sentence, a name only if it holds one the game knows.
        fun nameLike(phrase: String): Boolean {
            val words = phrase.split(' ', '-', '\'').filter { it.isNotBlank() }
            if (words.all { it.lowercase(Locale.US) in COMMON }) return false
            return words.size <= 3 || words.any { it in facts.nameWords }
        }
        (properName.findAll(prose).filter { nameLike(it.value) } + properName.findAll(headers).filter { it.value.substringBefore(' ') in firstNames }).take(3).forEach { errors += "unknown proper name: ${entityLabel(it.value)}" }
        val subjects = setOf("He", "She", "It", "This", "That", "There", "They", "We", "What", "Who", "Basketball", "March", "April", "Defense", "Offense", "Depth", "Availability", "Continuity", "Nothing", "Everyone", "Nobody", "Something") + STARTERS
        // A capitalized word before "is/has/won" mid-sentence is a name. At the start of a sentence it's usually
        // just a word ("Tonight is...", "Effort has..."), so there it counts only when the clause states a fact
        // with a number ("Atlantis is 7-3").
        Regex("\\b([A-Z][a-z]+)\\s+(?:is|are|has|went|won|lost)\\b").findAll(prose).filter { m ->
            val word = m.groupValues[1]
            if (word in subjects || word.lowercase(Locale.US) in COMMON) return@filter false
            val before = prose.substring(0, m.range.first).trimEnd()
            val sentenceStart = before.isEmpty() || before.last() in ".!?:\"“”\n-—(" || before.endsWith("topic")
            if (!sentenceStart) return@filter true
            val clause = prose.substring(m.range.first).split(Regex("[.!?\\n]")).first()
            clause.any { it.isDigit() }
        }.take(3).forEach { errors += "unknown named subject: ${entityLabel(it.groupValues[1])}" }
        val lower = content.lowercase(Locale.US)
        if ((facts.preseason || facts.preview || facts.upcoming) && Regex("\\b(tonight's win|tonight's loss|won tonight|lost tonight|just defeated|clinched|eliminated|advanced to|celebrated the win)\\b").containsMatchIn(lower)) errors += "result claimed before event"
        if (facts.phase == Phase.OFFSEASON && Regex("\\b(tonight's game|tonight's win|tonight's loss|tomorrow's game)\\b").containsMatchIn(lower)) errors += "wrong season phase"
        if (!facts.preseason && !facts.preview && Regex("\\b(before the season begins|season has yet to begin|no games have been played)\\b").containsMatchIn(lower) && facts.phase != Phase.OFFSEASON) errors += "wrong season phase"
        if (facts.phase == Phase.REGULAR && !facts.preview && Regex("\\b(won the championship|won the title|clinched the title|playoff victory|postseason victory)\\b").containsMatchIn(lower)) errors += "wrong season phase"
        if (Regex("\\b(buzzer.beater|game.winning shot|late run|closing minutes|fourth.quarter run|second.chance points|locker.room champagne|parade|contract demand|demanded a trade|physicians confirmed|doctors confirmed|non.contact conditioning|medically cleared)\\b").findAll(lower).any { !facts.text.contains(it.value, true) }) errors += "unsupported event detail"
        return errors.distinct()
    }

    /** [withInstructions] false: the caller sends [instructions] as the system text already (once is enough). */
    suspend fun generate(facts: AiFactSheet, request: String, validateDraft: (String) -> List<String> = { emptyList() }, withInstructions: Boolean = true, producer: suspend (String) -> Result<String>): Result<String> {
        var reasons = ""
        val format = request.substringAfter("Format your response EXACTLY as:", request.substringAfter("Format each storyline as:", ""))
        val labels = Regex("(?m)^([A-Z][A-Z_ ]*(?: \\d+)?):(?:\\s*<|\\s*$)").findAll(format).map { it.groupValues[1].trim() }.distinct().toList()
        repeat(2) { attempt ->
            val prompt = (if (withInstructions) "$instructions\n\n" else "") + "FACT SHEET\n${facts.text}\nEND FACT SHEET\n\nWRITING REQUEST\n$request" +
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
            } else return Result.failure(result.exceptionOrNull() ?: IllegalStateException("generation unavailable"))
        }
        return Result.failure(IllegalStateException("Could not verify the draft after two attempts ($reasons). The existing game-written content was kept."))
    }
}
