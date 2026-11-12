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

    fun game(lg: League, g: Game): String {
        val home = lg.team(g.home); val away = lg.team(g.away)
        val win = lg.team(g.winner()); val lose = lg.team(g.loser())
        fun line(p: PlayerLine) = "${p.name} finished with ${p.s.pts} points, ${p.s.reb} rebounds and ${p.s.ast} assists" +
            if (p.s.fga > 0) ", shooting ${p.s.fgm}-of-${p.s.fga} from the field." else "."
        fun contributors(t: Team, players: List<PlayerLine>) = buildString {
            append("${t.name} received production from several players. ")
            append(players.sortedByDescending { it.s.pts }.take(3).joinToString(" ") { line(it) })
            append(" The team combined for ${players.sumOf { it.s.ast }} assists and ${players.sumOf { it.s.reb }} individual rebounds. Its players made ${players.sumOf { it.s.fgm }} of ${players.sumOf { it.s.fga }} field-goal attempts and ${players.sumOf { it.s.ftm }} of ${players.sumOf { it.s.fta }} free throws.")
        }
        val top = g.box?.let { if (g.winner() == g.home) it.home else it.away }?.maxByOrNull { it.s.pts }
        val paragraphs = mutableListOf("${win.name} beat ${lose.name} ${g.scoreFor(win.id)}-${g.scoreFor(lose.id)} on day ${g.day} of the ${lg.season} season. ${top?.let { "${it.name} led the winning team with ${it.s.pts} points. " }.orEmpty()}The final margin was ${kotlin.math.abs(g.hs - g.aws)} points${if (g.ot > 0) " after overtime" else ""}. ${if (g.neutral) "The teams met at a neutral site." else "${home.name} played at home against ${away.name}."}")
        g.box?.let { b ->
            paragraphs += contributors(home, b.home)
            paragraphs += contributors(away, b.away)
            if (b.halves.isNotEmpty()) paragraphs += "The period scoring offers another view of the contest. " + b.halves.mapIndexedNotNull { i, scores -> if (scores.size < 2) null else "In period ${i + 1}, ${home.name} scored ${scores[0]} and ${away.name} scored ${scores[1]}." }.joinToString(" ")
        }
        lg.moments.firstOrNull { it.gameId == g.id }?.let { paragraphs += "${it.headline.trimEnd('.')}. ${it.detail.trimEnd('.')}." }
        if (g.box == null) paragraphs += "${home.name} finished with ${g.hs} points and ${away.name} with ${g.aws}. A detailed individual box score was not retained for this game. The team result, rather than a reconstructed individual performance, is the basis for assessing this meeting."
        paragraphs += "${home.name} currently stands at ${home.w}-${home.l}, while ${away.name} is ${away.w}-${away.l}. ${listOf(home, away).joinToString(" ") { t -> if (t.ap in 1..25) "${t.name} is ranked No. ${t.ap}." else "${t.name} is outside the current Top 25." }} The result belongs alongside those current records rather than replacing the broader picture of each team's season. Both sides still have to turn their individual production into results over the rest of the campaign."
        val next = listOf(home, away).mapNotNull { t -> lg.games.filter { !it.played && it.day >= g.day && it.involves(t.id) }.minByOrNull { it.day }?.let { n -> "${t.name} next faces ${lg.team(n.opp(t.id)).name} on day ${n.day}${if (n.neutral) " at a neutral site" else if (n.home == t.id) " at home" else " on the road"}." } }
        if (next.isNotEmpty()) paragraphs += next.joinToString(" ") + " Those matchups are the next opportunity to build on the positive parts of this performance and address the areas that did not produce the desired result. The focus moves from the final score here to preparation for the next opponent."
        else paragraphs += "Neither team has another unplayed fixture on the current schedule. The result leaves ${win.name} with the victory in this meeting and ${lose.name} with the defeat, while the next stage of the season will determine when either side returns to action."
        return paragraphs.joinToString("\n\n")
    }

    fun news(lg: League, n: News, includeForecast: Boolean = true): String {
        lg.games.firstOrNull { it.id == n.gameId && it.played }?.let { return game(lg, it) }
        val t = lg.teams.getOrNull(n.teamId) ?: lg.teams.firstOrNull { n.text.contains(it.name) || n.text.contains(it.school) } ?: lg.user
        val p = lg.playerOrNull(n.pid) ?: lg.rosterOf(t).maxByOrNull { it.ovr }
        val paragraphs = mutableListOf("${n.text.trimEnd('.', '!', '?')}. ${t.name} currently stands at ${t.record} in the ${lg.season} season. ${lg.coachOf(t).name} leads the team, which plays in ${lg.conf(t.confId).name}. ${if (t.ap in 1..25) "The team is ranked No. ${t.ap}." else "The team is outside the current Top 25."}")
        p?.let { player ->
            val prior = player.career.lastOrNull { it.season == lg.season - 1 && it.s.gp > 0 }
            val s = prior?.s ?: player.stats
            paragraphs += "${player.name} plays ${player.posLabel} for ${t.name}. " + if (s.gp > 0) java.lang.String.format(java.util.Locale.US, "%s, the player averaged %.1f points, %.1f rebounds and %.1f assists across %d appearances. That production gives %s a specific individual contribution to build around alongside the rest of the team.", if (prior != null) "Last season" else "This season", s.ppg, s.rpg, s.apg, s.gp, t.name) else "The player has not yet made an appearance this season. The next opportunity will be to establish a contribution alongside teammates, rather than relying on an individual scoring average before games have been played."
        }
        val preview = if (includeForecast) AiTemplates.previewCopy(lg, t, p).split(Regex("(?<=[.!?])\\s+(?=[A-Z])")) else emptyList()
        val outlook = preview.filter { it.contains("projected") || it.contains("win range") || it.contains("joins the group") || it.contains("has moved on") || it.contains(" is out with ") }
        paragraphs += outlook.joinToString(" ").ifBlank { "${t.name} has ${lg.rosterOf(t).size} players on the team. ${p?.name ?: t.name} is part of that group as the staff weighs its options for the next game. The team's current record is the starting point for assessing what comes next, rather than a guarantee of how the rest of the season will unfold." }
        val upcoming = lg.games.filter { !it.played && it.involves(t.id) }.minByOrNull { it.day }
        paragraphs += upcoming?.let { "${t.name} next meets ${lg.team(it.opp(t.id)).name} on day ${it.day}, ${if (it.neutral) "at a neutral site" else if (it.home == t.id) "at home" else "on the road"}. That matchup is the next scheduled test for the team and its leading contributors. Preparation now turns toward that opponent, with another result still to be earned on the court." } ?: "The next fixture is not yet scheduled. ${t.name} therefore finishes this update at ${t.record}, with its current results and individual production setting the context for the next stage. The team's next confirmed matchup will supply a more specific test when the schedule is set."
        return paragraphs.joinToString("\n\n")
    }

    fun boxFacts(g: Game) = g.box?.let { b -> (b.home.sortedByDescending { it.s.pts }.take(3) + b.away.sortedByDescending { it.s.pts }.take(3)).joinToString("\n") { "BOX ${it.name}: ${it.s.pts} PTS, ${it.s.reb} REB, ${it.s.ast} AST, ${it.s.stl} STL, ${it.s.blk} BLK, ${it.s.tov} TOV." } }.orEmpty()
}
