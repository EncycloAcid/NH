package com.nh.hoopscoach.game

object ArticleWriting {
    fun checked(news: News) = news.isAiGenerated && errors(news.body, OllamaConfig()).isEmpty()
    fun small(config: OllamaConfig) = config.provider.lowercase() == "ollama" && (config.speedMode == "turbo" || Regex("(?i)(1b|3b|small)").containsMatchIn(config.model))
    fun budget(config: OllamaConfig) = if (small(config)) 850 else 1800
    fun instruction(config: OllamaConfig) = "Write 4-7 substantial paragraphs, ${if (small(config)) "160-240" else "250-450"} words. Separate paragraphs with blank lines. Finish every sentence. Cover the lede, exact game lines for both teams, recorded period scoring or a recorded decisive event, records and standings context, and the next scheduled game when supplied. For other article types develop the relevant event, evidence, implications and what comes next. Omit unavailable details. Never invent a run or a quote; no quotations unless explicitly supplied as recorded dialogue."

    fun bodies(raw: String): List<String> {
        if (Regex("(?m)^SCOOP \\d+:").containsMatchIn(raw)) return briefs(raw, "SCOOP").map { it.second }
        if (Regex("(?m)^RUMOR \\d+:").containsMatchIn(raw)) return briefs(raw, "RUMOR").map { it.second }
        val blocks = Regex("(?im)^\\s*(?:BODY|STORY):\\s*").findAll(raw).toList()
        if (blocks.isEmpty()) return listOf(raw.trim())
        return blocks.map { marker ->
            raw.substring(marker.range.last + 1).split(Regex("(?m)^\\s*(?:[A-Z][A-Z_ ]*(?: \\d+)?:|---)"), limit = 2).first().trim()
        }
    }

    fun briefs(raw: String, prefix: String): List<Pair<String, String>> {
        val markers = Regex("(?m)^${Regex.escape(prefix)} \\d+:").findAll(raw).toList()
        return markers.mapIndexed { index, marker ->
            val chunk = raw.substring(marker.range.last + 1, markers.getOrNull(index + 1)?.range?.first ?: raw.length).trim()
            chunk.substringBefore('|').trim() to chunk.substringAfter('|', "").trim()
        }.filter { it.first.isNotBlank() && it.second.isNotBlank() }
    }

    fun errors(raw: String, config: OllamaConfig): List<String> = bodies(raw).flatMap { body ->
        val paragraphs = body.split(Regex("\\n\\s*\\n")).filter { it.isNotBlank() }
        buildList {
            if (paragraphs.size !in 4..7) add("article requires 4-7 paragraphs")
            if (Regex("\\S+").findAll(body).count() < if (small(config)) 140 else 220) add("article is too short")
            if (paragraphs.any { !Regex("[.!?][\\\"'’”)]*$").containsMatchIn(it.trim()) }) add("unfinished article sentence")
            if (Regex("(?i)\\b(to|with|and|the|a|an|of|for|in|at|by|from|or|but|because)[.!?][\\\"'’”)]*$").containsMatchIn(body.trim())) add("unfinished article sentence")
        }
    }.distinct()

    private fun f1(value: Double) = String.format(java.util.Locale.US, "%.1f", value)

    private fun noRepeat(paragraphs: List<String>): String {
        val seen = mutableSetOf<String>()
        return paragraphs.map { paragraph ->
            paragraph.trim().split(Regex("(?<=[.!?])\\s+(?=[A-Z])")).filter { it.isNotBlank() && seen.add(it) }.joinToString(" ")
        }.filter { it.isNotBlank() }.joinToString("\n\n")
    }

    private fun short(lg: League, team: Team) = if (lg.pro) team.mascot else team.school
    private fun possessive(name: String) = name + if (name.endsWith("s", true)) "'" else "'s"

    private fun nextGames(lg: League, teams: List<Team>, day: Int): String {
        val games = teams.mapNotNull { t -> lg.games.filter { !it.played && it.day >= maxOf(day, lg.day) && it.involves(t.id) }.minByOrNull { it.day } }.distinctBy { it.id }
        if (games.isEmpty()) return "Neither team has another game on the published schedule."
        return games.joinToString(" ") { n ->
            val home = lg.team(n.home); val away = lg.team(n.away)
            if (teams.all { n.involves(it.id) }) "${short(lg, away)} and ${short(lg, home)} meet again on day ${n.day}${if (n.neutral) " at a neutral site" else ", this time on the ${possessive(home.mascot)} home floor"}."
            else "${short(lg, away)} ${if (n.neutral) "meets" else "visits"} ${short(lg, home)} on day ${n.day}${if (n.neutral) " at a neutral site" else ""}."
        }
    }

    fun game(lg: League, g: Game): String {
        val win = lg.team(g.winner()); val lose = lg.team(g.loser())
        val random = kotlin.random.Random(lg.season * 31 + g.id * 17 + g.day)
        fun pick(vararg lines: String) = lines[random.nextInt(lines.size)]
        fun surname(p: PlayerLine) = lg.playerOrNull(p.pid)?.last ?: p.name.substringAfterLast(' ')
        val w = short(lg, win); val l = short(lg, lose)
        val wi = if (win.id == g.home) 0 else 1
        val winnerLines = g.box?.let { if (wi == 0) it.home else it.away }.orEmpty()
        val loserLines = g.box?.let { if (wi == 0) it.away else it.home }.orEmpty()
        val winners = winnerLines.filter { it.s.pts > 0 }.sortedByDescending { it.s.pts }
        val losers = loserLines.filter { it.s.pts > 0 }.sortedByDescending { it.s.pts }
        val top = winners.firstOrNull()
        val margin = g.scoreFor(win.id) - g.scoreFor(lose.id)
        val periods = g.box?.halves.orEmpty().filter { it.size >= 2 }
        val half = if (g.box?.quarters == true) periods.take(2).takeIf { it.size == 2 }?.let { p -> intArrayOf(p.sumOf { it[0] }, p.sumOf { it[1] }) } else periods.firstOrNull()
        val deficit = half?.let { it[1 - wi] - it[wi] } ?: 0
        val score = "${g.scoreFor(win.id)}-${g.scoreFor(lose.id)}"
        val shape = when {
            margin >= 20 -> pick("${win.name} rolled past ${lose.name}", "${win.name} overwhelmed ${lose.name}", "${win.name} pulled away from ${lose.name}")
            deficit > 0 -> pick("${win.name} overturned a halftime deficit to beat ${lose.name}", "${win.name} came from behind at the break to defeat ${lose.name}", "${win.name} rallied after halftime to beat ${lose.name}")
            lose.ap in 1..25 && (win.ap !in 1..25 || win.ap > lose.ap) -> "${win.name} knocked off ${lose.name}"
            margin <= 5 -> pick("${win.name} edged ${lose.name}", "${win.name} narrowly beat ${lose.name}", "${win.name} squeezed past ${lose.name}")
            else -> pick("${win.name} defeated ${lose.name}", "${win.name} got the better of ${lose.name}", "${win.name} beat ${lose.name}")
        }
        val paragraphs = mutableListOf("$shape $score${if (g.ot > 0) " in overtime" else ""} on day ${g.day} of the ${lg.season} season. ${top?.let { "${it.name} led the ${win.mascot} with ${it.s.pts} points${if (it.s.reb > 0) ", ${it.s.reb} rebounds" else ""}${if (it.s.ast > 0) " and ${it.s.ast} assists" else ""}." }.orEmpty()}")
        top?.let { p ->
            val s = p.s
            val shooting = if (s.fga > 0 && s.fgm in 1..s.fga) pick("${surname(p)} went ${s.fgm}-of-${s.fga} from the field on his way to ${s.pts} points.", "${surname(p)} built his scoring night on ${s.fgm}-of-${s.fga} shooting from the field.", "${surname(p)} made ${s.fgm} of his ${s.fga} field-goal attempts to anchor $w's offense.") else ""
            val extras = listOfNotNull(s.stl.takeIf { it > 0 }?.let { "$it steals" }, s.blk.takeIf { it > 0 }?.let { "$it blocks" })
            val free = if (s.fta > 0 && s.ftm in 1..s.fta) " He made ${s.ftm} of ${s.fta} free throws." else ""
            paragraphs[0] += " $shooting$free ${if (extras.isNotEmpty()) "He also recorded ${extras.joinToString(" and ")}." else ""}"
        }
        if (half != null) {
            val after = intArrayOf(g.hs - half[0], g.aws - half[1])
            paragraphs += when {
                deficit > 0 -> "Down $deficit at the break, $w outscored $l ${after[wi]}-${after[1 - wi]} after halftime${if (g.ot > 0) ", including overtime" else ""}. The ${lose.mascot} had led ${half[1 - wi]}-${half[wi]} at intermission. That ${after[wi] - after[1 - wi]}-point advantage over the rest of the game turned the deficit into the $score win."
                deficit < 0 -> "$w led ${half[wi]}-${half[1 - wi]} at halftime, a ${-deficit}-point cushion. After the break, the ${win.mascot} scored ${after[wi]}, while the ${lose.mascot} scored ${after[1 - wi]}${if (g.ot > 0) ", including overtime" else ""}. ${if (after[wi] > after[1 - wi]) "$w extended its lead over the remainder of the game." else "$w's halftime lead was enough to withstand the scoring swing after the break."}"
                else -> "The teams were level at ${half[0]} at halftime. $w scored ${after[wi]} after the break to $l's ${after[1 - wi]}${if (g.ot > 0) ", including overtime" else ""}."
            }
        }
        lg.moments.firstOrNull { it.gameId == g.id && it.detail.isNotBlank() }?.let { moment ->
            val detail = (winners + losers).fold(moment.detail) { text, p -> text.replace(p.name, surname(p)) }.replace(win.name, w).replace(lose.name, l)
            paragraphs[paragraphs.lastIndex] += " ${detail.trimEnd('.', '!', '?')}."
        }
        val support = winners.drop(1).take(2)
        if (support.isNotEmpty() || losers.isNotEmpty()) paragraphs += buildString {
            if (support.size == 2) append(pick("${support[0].name} and ${support[1].name} backed up ${top?.let { surname(it) }} with ${support[0].s.pts} and ${support[1].s.pts} points, respectively. ", "$w also got ${support[0].s.pts} points from ${support[0].name} and ${support[1].s.pts} from ${support[1].name}. "))
            else support.firstOrNull()?.let { append("${it.name} added ${it.s.pts} points for $w. ") }
            if (support.size == 2) {
                val a = support[0]; val b = support[1]
                if (a.s.reb > 0 && a.s.ast > 0 && b.s.reb > 0 && b.s.ast > 0) append("${surname(a)} also had ${a.s.reb} rebounds and ${a.s.ast} assists; ${surname(b)} contributed ${b.s.reb} rebounds and ${b.s.ast} assists. ")
                if (a.s.fga > 0 && a.s.fgm in 1..a.s.fga && b.s.fga > 0 && b.s.fgm in 1..b.s.fga) append("${surname(a)} shot ${a.s.fgm}-of-${a.s.fga} from the floor, with ${surname(b)} going ${b.s.fgm}-of-${b.s.fga}. ")
            }
            val combined = winners.take(3).sumOf { it.s.pts }
            if (winners.size >= 3) append("The leading trio supplied $combined of $w's ${g.scoreFor(win.id)} points. ")
            losers.firstOrNull()?.let { p -> append("${p.name} paced $l with ${p.s.pts} points${if (p.s.reb > 0) " and ${p.s.reb} rebounds" else ""}${losers.getOrNull(1)?.let { ", while ${it.name} added ${it.s.pts}" }.orEmpty()}.") }
        }
        val complete = winnerLines.sumOf { it.s.pts } == g.scoreFor(win.id) && loserLines.sumOf { it.s.pts } == g.scoreFor(lose.id)
        if (complete && winners.isNotEmpty() && losers.isNotEmpty()) {
            val wm = winnerLines.sumOf { it.s.fgm }; val wa = winnerLines.sumOf { it.s.fga }
            val lm = loserLines.sumOf { it.s.fgm }; val la = loserLines.sumOf { it.s.fga }
            val meaningful = wa >= 10 && la >= 10 && wm in 1..wa && lm in 1..la &&
                (kotlin.math.abs(wm - lm) >= 5 || kotlin.math.abs(wm.toDouble() / wa - lm.toDouble() / la) >= 0.1)
            val shooting = if (meaningful) " $w made $wm of $wa shots from the field, compared with $lm of $la for $l." else ""
            val benchW = winners.filter { !it.starter }.sumOf { it.s.pts }; val benchL = losers.filter { !it.starter }.sumOf { it.s.pts }
            val depth = winners.count { it.s.pts >= 10 }
            val depthLine = if (depth > 1) " $w had $depth double-figure scorers." else ""
            val benchLine = if (benchW > 0 && benchL > 0 && kotlin.math.abs(benchW - benchL) >= 6) " Bench scoring favored ${if (benchW > benchL) w else l} ${maxOf(benchW, benchL)}-${minOf(benchW, benchL)}." else ""
            paragraphs[paragraphs.lastIndex] += shooting + depthLine + benchLine
        }
        if (g.box == null) paragraphs += "The individual box score was not retained for this game."
        val context = "$w's current record is ${win.record}; $l is ${lose.record}." + listOf(win, lose).filter { it.ap in 1..25 }.joinToString("") { " ${short(lg, it)} is ranked No. ${it.ap}." } + if (win.streak >= 3) " The ${win.mascot} have won ${win.streak} straight." else ""
        paragraphs += "$context ${nextGames(lg, listOf(win, lose), g.day)}"
        if (paragraphs.size < 4) paragraphs.add(paragraphs.lastIndex, "The ${lg.season} meeting ended with $w on ${g.scoreFor(win.id)} and $l on ${g.scoreFor(lose.id)}.")
        return noRepeat(paragraphs)
    }

    /** Playoff series that contains [g], if any. */
    fun seriesOf(lg: League, g: Game): Series? =
        lg.proState?.series?.firstOrNull { g.id in it.gameIds }

    /** Compact series ledger for fact sheets (games played, score, round, clinch). */
    fun seriesFacts(lg: League, g: Game): String {
        val s = seriesOf(lg, g) ?: return ""
        val w = if (s.done) lg.team(s.winner) else null
        val l = if (s.done) lg.team(s.loser) else null
        val games = s.hiWins + s.loWins
        val round = ProSeason.roundName(s)
        return buildString {
            appendLine("SERIES: ${round}; best of ${s.bestOf}; series score ${maxOf(s.hiWins, s.loWins)}-${minOf(s.hiWins, s.loWins)}; games played $games.")
            if (w != null && l != null) {
                appendLine("SERIES RESULT: ${w.name} beat ${l.name} in $games to win the $round.")
                appendLine("SERIES WINNER: ${w.name} (${w.record}); SERIES LOSER: ${l.name} (${l.record}).")
            }
            val totals = HashMap<Int, Int>()
            for (id in s.gameIds) {
                val sg = lg.game(id)
                val side = if (w != null && sg.home == w.id) sg.box?.home else if (w != null) sg.box?.away else null
                side?.forEach { totals[it.pid] = (totals[it.pid] ?: 0) + it.s.pts }
            }
            totals.entries.sortedByDescending { it.value }.take(3).forEach { (pid, pts) ->
                val p = lg.playerOrNull(pid) ?: return@forEach
                val avg = String.format(java.util.Locale.US, "%.1f", pts.toDouble() / games.coerceAtLeast(1))
                appendLine("SERIES LINE ${p.name}: $pts PTS in $games games; $avg points a game in the series.")
            }
        }.trim()
    }

    /** Full series clincher article — title/round aware, not a single-game box dump. */
    fun series(lg: League, n: News): String {
        val last = lg.games.firstOrNull { it.id == n.gameId && it.played }
        val s = last?.let { seriesOf(lg, it) }
        if (s == null || !s.done) {
            return n.body.ifBlank { last?.let { game(lg, it) } ?: n.text }
        }
        val w = lg.team(s.winner); val l = lg.team(s.loser)
        val games = s.hiWins + s.loWins
        val round = ProSeason.roundName(s)
        val seriesScore = "${maxOf(s.hiWins, s.loWins)}-${minOf(s.hiWins, s.loWins)}"
        val paragraphs = mutableListOf<String>()
        paragraphs += if (s.round == 4) {
            "The ${w.name} are champions of ${lg.season}. They beat the ${l.name} $seriesScore in the $round, closing the series in $games games."
        } else {
            "The ${w.name} beat the ${l.name} in $games to win the $round. The series finished $seriesScore."
        }
        val totals = HashMap<Int, Int>()
        for (id in s.gameIds) {
            val sg = lg.game(id)
            val side = if (sg.home == w.id) sg.box?.home else sg.box?.away
            side?.forEach { totals[it.pid] = (totals[it.pid] ?: 0) + it.s.pts }
        }
        totals.maxByOrNull { it.value }?.let { (pid, pts) ->
            val p = lg.playerOrNull(pid) ?: return@let
            val avg = String.format(java.util.Locale.US, "%.1f", pts.toDouble() / games.coerceAtLeast(1))
            paragraphs += "${p.name} led the way with $avg points a game in the series ($pts PTS across $games games)."
        }
        last?.let { g ->
            paragraphs += "The clincher on day ${g.day} ended ${g.scoreFor(w.id)}-${g.scoreFor(l.id)}. ${w.name}'s record is ${w.record}; ${l.name} finished the series at ${l.record}."
        }
        if (s.round == 4) {
            val ts = NbaHistory.titles(lg, w)
            paragraphs += when {
                ts.isEmpty() -> "${w.name} won four playoff series on the way to the ${lg.season} title."
                else -> "${w.name} won four playoff series on the way to the title and now owns ${NbaHistory.titleWords(ts.size)}, the latest in ${ts.last()}."
            }
        } else {
            val next = if (s.round == 3) "the Finals" else ProLeague.ROUND_NAMES.getOrElse(s.round) { "the next round" }
            paragraphs += "Next for the ${w.name}: $next. ${w.name} is ${w.record} after winning the $round."
        }
        if (paragraphs.size < 4) {
            paragraphs += "The ${lg.season} ${round} series between ${w.name} and ${l.name} required $games games."
        }
        return noRepeat(paragraphs)
    }

    fun news(lg: League, n: News, includeForecast: Boolean = true): String {
        if (n.kind == "series") return series(lg, n)
        lg.games.firstOrNull { it.id == n.gameId && it.played }?.let { return game(lg, it) }
        val t = lg.teams.getOrNull(n.teamId) ?: lg.teams.firstOrNull { n.text.contains(it.name) || n.text.contains(it.school) } ?: lg.user
        val name = short(lg, t)
        val roster = lg.rosterOf(t)
        val p = lg.playerOrNull(n.pid) ?: roster.maxByOrNull { it.ovr }
        val paragraphs = mutableListOf("${n.text.trimEnd('.', '!', '?')}. ${if (n.text.contains(t.name)) name else t.name} has a ${t.record} record in ${lg.season}. Coach ${lg.coachOf(t).name} leads the ${t.mascot} in ${lg.conf(t.confId).name}.${if (t.ap in 1..25) " $name is ranked No. ${t.ap}." else ""}")
        p?.let { player ->
            val prior = player.career.lastOrNull { it.season == lg.season - 1 && it.s.gp > 0 }
            val s = prior?.s ?: player.stats
            val production = if (s.gp > 0) "${if (prior != null) "Last season" else "This season"}, ${player.last} averaged ${f1(s.ppg)} points${if (s.rpg > 0) ", ${f1(s.rpg)} rebounds" else ""}${if (s.apg > 0) " and ${f1(s.apg)} assists" else ""} in ${s.gp} games." else "${player.last} has yet to appear this season."
            paragraphs += "${player.name}, ${if (lg.pro) "age ${player.age}" else player.cls}, plays ${player.posLabel} for $name. $production${if (player.out) " ${player.last} is out with ${player.injury.ifBlank { "an injury" }}." else ""}"
        }
        val preview = if (includeForecast) AiTemplates.previewCopy(lg, t, p).split(Regex("(?<=[.!?])\\s+(?=[A-Z])")) else emptyList()
        val outlook = preview.filter { it.contains("projected") || it.contains("win range") || it.contains("joins") || it.contains("has moved on") || it.contains(" is out with ") }
        val colleagues = roster.filter { it.id != p?.id }.sortedByDescending { it.ovr }.take(2).joinToString(" ") { player ->
            val s = player.career.lastOrNull { it.season == lg.season - 1 && it.s.gp > 0 }?.s
            "${player.name} plays ${player.posLabel}${if (!lg.pro) " as a ${player.cls}" else ""}${if (s != null) ", coming off ${f1(s.ppg)} points per game last season" else ""}."
        }
        paragraphs += outlook.joinToString(" ").ifBlank { colleagues }
        val next = lg.games.filter { !it.played && it.day >= lg.day && it.involves(t.id) }.minByOrNull { it.day }
        val recent = lg.games.filter { it.played && it.day <= lg.day && it.involves(t.id) }.sortedByDescending { it.day }.take(2).joinToString(" ") {
            "$name ${if (it.winner() == t.id) "beat" else "lost to"} ${short(lg, lg.team(it.opp(t.id)))} ${it.scoreFor(t.id)}-${it.scoreAgainst(t.id)} on day ${it.day}."
        }
        paragraphs += listOf(recent, next?.let { "$name ${if (it.neutral) "meets" else if (it.home == t.id) "hosts" else "visits"} ${short(lg, lg.team(it.opp(t.id)))} on day ${it.day}${if (it.neutral) " at a neutral site" else ""}." } ?: "$name has no further game on the published schedule.").filter { it.isNotBlank() }.joinToString(" ")
        return noRepeat(paragraphs)
    }

    fun boxFacts(g: Game): String {
        val b = g.box ?: return "FINAL SCORE: ${g.hs}-${g.aws}."
        val lines = (b.home.sortedByDescending { it.s.pts }.take(4) + b.away.sortedByDescending { it.s.pts }.take(4)).joinToString("\n") {
            "BOX ${it.name}: ${it.s.pts} PTS, ${it.s.reb} REB, ${it.s.ast} AST, ${it.s.stl} STL, ${it.s.blk} BLK, ${it.s.tov} TOV, ${it.s.fgm} FGM, ${it.s.fga} FGA, ${it.s.ftm} FTM, ${it.s.fta} FTA."
        }
        val periods = b.halves.mapIndexedNotNull { i, scores ->
            if (scores.size < 2) null else "PERIOD ${i + 1}: ${scores[0]}-${scores[1]}"
        }
        val half = if (b.quarters) b.halves.take(2).takeIf { it.size == 2 }?.let { p -> intArrayOf(p.sumOf { it[0] }, p.sumOf { it[1] }) }
            else b.halves.firstOrNull()
        val margin = kotlin.math.abs(g.hs - g.aws)
        val halfLine = half?.let {
            val cushion = kotlin.math.abs(it[0] - it[1])
            "HALFTIME: ${it[0]}-${it[1]}; cushion $cushion; after break ${g.hs - it[0]}-${g.aws - it[1]}."
        }.orEmpty()
        val homeTop = b.home.sortedByDescending { it.s.pts }.take(3).sumOf { it.s.pts }
        val awayTop = b.away.sortedByDescending { it.s.pts }.take(3).sumOf { it.s.pts }
        return buildString {
            appendLine("FINAL SCORE: ${g.hs}-${g.aws}; margin $margin.")
            if (halfLine.isNotBlank()) appendLine(halfLine)
            periods.forEach { appendLine(it) }
            appendLine("LEADING TRIO: home $homeTop of ${g.hs}; away $awayTop of ${g.aws}.")
            append(lines)
        }.trim()
    }
}
