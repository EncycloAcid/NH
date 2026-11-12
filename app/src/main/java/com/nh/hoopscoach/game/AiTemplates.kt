package com.nh.hoopscoach.game

import java.util.Locale

data class AiText(val text: String, val isAi: Boolean = false, val provider: String = "") {
    fun source(outlet: String) = if (isAi) "$outlet · $provider" else "Game-written · $outlet"

    fun applyTo(news: News) {
        val source = if (news.isAiGenerated) news.source.substringBeforeLast(" · ", news.source) else news.source
        val outlet = source.substringAfterLast(" · ").ifBlank { "Courtside Report" }
        news.body = text
        news.aiLabeled = isAi
        news.source = source(outlet)
    }
}

object AiTemplates {
    private fun f1(n: Double) = String.format(Locale.US, "%.1f", n)

    fun notableGame(lg: League, t: Team): Game? {
        val games = lg.games.filter { !it.played && it.involves(t.id) && PreviewAvailability.regular(it) }.sortedBy { it.day }
        return games.firstOrNull { lg.rivalry(it) != null || lg.team(it.opp(t.id)).ap in 1..25 } ?: games.firstOrNull()
    }

    fun newcomer(lg: League, t: Team): Player? = lg.rosterOf(t).filter { p ->
        (!lg.pro && p.year == 1) || p.career.lastOrNull { it.season == lg.season - 1 }?.let { it.teamId >= 0 && it.teamId != t.id } == true
    }.maxByOrNull { it.ovr }

    fun departure(lg: League, t: Team): Player? = (lg.players + lg.alumni).filter { p ->
        p.teamId != t.id && p.career.any { it.season == lg.season - 1 && it.teamId == t.id }
    }.maxByOrNull { it.career.last { it.season == lg.season - 1 && it.teamId == t.id }.s.ppg }

    fun previewCopy(lg: League, t: Team, subject: Player? = null): String = buildString {
        val name = if (lg.pro) t.name else t.school
        val star = subject ?: lg.rosterOf(t).maxByOrNull { it.ovr }
        star?.let { p ->
            val position = when (p.pos) { Pos.PG -> "point guard"; Pos.SG -> "shooting guard"; Pos.SF -> "small forward"; Pos.PF -> "power forward"; Pos.C -> "center" }
            val year = when (p.year) { 1 -> "freshman"; 2 -> "sophomore"; 3 -> "junior"; else -> "senior" }
            append(if (lg.pro) "${p.name}, ${p.age}, plays $position for $name. " else "${p.name}, a ${if (p.cls.startsWith("RS")) "redshirt " else ""}$year $position, heads into ${lg.season} with $name. ")
            p.career.lastOrNull { it.season == lg.season - 1 && it.s.gp > 0 }?.let { y ->
                append("Last season, ${p.name} averaged ${f1(y.s.ppg)} points, ${f1(y.s.rpg)} rebounds and ${f1(y.s.apg)} assists in ${y.s.gp} games. ")
            }
        }
        if (lg.pro) lg.proState?.preview?.takeIf { it.season == lg.season }?.proj?.get(t.id)?.let { p ->
            append("$name is projected for ${p.w}-${p.l}, with a ${p.lo}-${p.hi} win range, a ${PreviewWriting.percent(p.playoffs)} playoff chance and a ${PreviewWriting.percent(p.title)} title chance. ")
        } else lg.preview?.takeIf { it.season == lg.season }?.let { pv ->
            pv.proj[t.id]?.let { p -> append("$name is projected for ${p.w}-${p.l}. ") }
            pv.odds[t.id]?.let { o -> append("The win range is ${o.lo}-${o.hi}, with a ${PreviewWriting.percent(o.ncaa)} chance of an NCAA bid and a ${PreviewWriting.percent(o.title)} title chance. ") }
        }
        newcomer(lg, t)?.takeIf { it.id != star?.id }?.let { p ->
            append("${p.name} joins the group at ${p.posLabel}${if (p.recruitRank > 0) " as the No. ${p.recruitRank} recruit" else ""}. ")
        }
        if (newcomer(lg, t) == null) departure(lg, t)?.let { p ->
            val s = p.career.last { it.season == lg.season - 1 && it.teamId == t.id }.s
            append("${p.name} has moved on after averaging ${f1(s.ppg)} points for $name last season. ")
        }
        lg.rosterOf(t).filter { it.out }.take(1).forEach { p ->
            append("${p.name} is out with ${p.injury.ifBlank { "an injury" }}${if (p.injGames < Injuries.SEASON_OUT) " for ${p.injGames} more team games" else " for the season"}. ")
        }
        notableGame(lg, t)?.let { g ->
            append("On day ${g.day}, ${lg.team(g.away).name} and ${lg.team(g.home).name} meet ${if (g.neutral) "at a neutral site" else "on ${lg.team(g.home).school}'s floor"}${lg.rivalry(g)?.let { " in the ${it.name}" }.orEmpty()}.")
        }
    }.trim()

    fun create(facts: AiFactSheet, request: String): AiText {
        val lines = facts.text.lines()
        require(lines.any { it.startsWith("TEAM ") }) { "No factual team context is available." }
        fun field(key: String) = request.lineSequence().firstOrNull { it.startsWith("$key:", true) }?.substringAfter(':')?.trim().orEmpty()
        val team = field("Team").ifBlank { field("User team") }.substringBefore(" (")
        val editorials = lines.filter { it.startsWith("EDITORIAL ") }
        val player = field("Player").ifBlank { field("Prospect") }.ifBlank { field("Winner") }.substringBefore(" (").substringBefore(',')
        val editorial = (lines.firstOrNull { it.startsWith("PLAYER EDITORIAL $player:") } ?: editorials.firstOrNull { it.startsWith("EDITORIAL $team:") } ?: editorials.firstOrNull())?.substringAfter(": ").orEmpty()
        val current = lines.firstOrNull { it.startsWith("CURRENT $player:") && !it.contains("no appearances") }?.substringAfter(": ")
        val last = lines.firstOrNull { it.startsWith("LAST SEASON $player,") }?.substringAfter(": ")
        val production = (last ?: current)?.let { "$player: $it" }.orEmpty()
        val event = lines.firstOrNull { it.startsWith("EVENT FACTS:") }?.removePrefix("EVENT FACTS: ").orEmpty()
        val availability = lines.firstOrNull { it.startsWith("AVAILABILITY $player:") }?.substringAfter(": ").orEmpty()
        val scouting = lines.firstOrNull { it.startsWith("SCOUT $player:") }?.substringAfter(": ").orEmpty()
        val result = lines.filter { it.startsWith("RESULT ") }.firstOrNull { line ->
            val score = field("Final Score").ifBlank { field("Game Score") }
            score.isBlank() || Regex("\\d+").findAll(score).all { line.contains(it.value) }
        }
        val gameCopy = result?.let { line ->
            val match = Regex("RESULT day (\\d+): (.*?) (\\d+)-(\\d+) (.*?); winner (.*?);").find(line)
            match?.destructured?.let { (day, home, hs, aws, away, winner) ->
                val loser = if (winner == home) away else home
                val score = if (winner == home) "$hs-$aws" else "$aws-$hs"
                "$winner beat $loser $score on day $day."
            }
        }.orEmpty()
        val body = if (facts.preseason || facts.preview || facts.upcoming) editorial else listOf(gameCopy, production, editorial.takeIf { gameCopy.isEmpty() }.orEmpty()).filter { it.isNotBlank() }.joinToString("\n\n")
        val copy = when {
            request.contains("CHAMPIONSHIP_CELEBRATION:") -> event.removePrefix("Champion ").replace("; runner-up ", " finished ahead of ").replace("; finals outcome ", ", ").replace("; Finals MVP ", ". Finals MVP: ") + "."
            else -> body.ifBlank { lines.firstOrNull { it.startsWith("TEAM ") }?.substringBefore(';')?.removePrefix("TEAM ").orEmpty() }
        }
        if (request.contains("Format each storyline as:")) {
            val topics = request.substringBefore("Format each storyline as:").lines().drop(1).filter { it.contains(": ") }.take(3)
            return AiText(topics.joinToString("\n---\n") { line -> "KICKER: Season outlook\nHEADLINE: ${line.substringBefore(':')}\nBODY: ${line.substringAfter(": ")}" })
        }
        if (request.contains("RECRUIT_PITCH:")) {
            return AiText("${field("Recruit").substringBefore(" (")},\n\n${copy}\n\nYour priority is ${field("Recruit Priority").substringBefore('—').trim().lowercase()}. Let's discuss how that fits your next step.\n\nCoach ${field("Head Coach")}")
        }
        val labels = Regex("(?m)^([A-Z][A-Z_ ]*(?: \\d+)?):(?:\\s*<|\\s*$)")
            .findAll(request.substringAfter("Format your response EXACTLY as:", "")).map { it.groupValues[1].trim() }.distinct().toList()
        if (labels.isEmpty()) return AiText(copy)
        val headline = when {
            request.contains("Championship") || request.contains("championship coronation") -> event.substringBefore(';').removePrefix("Champion ") + " takes the title"
            player.isNotBlank() -> "$player and the season ahead"
            team.isNotBlank() -> "$team and the season ahead"
            else -> "The season ahead"
        }
        val text = labels.joinToString("\n") { label ->
            val value = when (label) {
                "HEADLINE" -> headline
                "OUTLET" -> "Courtside Report"
                "BODY", "STORY" -> "\n$copy"
                "BLURB", "SEASON_OUTLOOK" -> copy.replace("\n", " ")
                "ANALYSIS" -> "\n${event.removePrefix("Trade: ").ifBlank { copy }}"
                "REPORTER" -> "Staff"
                "TOPIC" -> "Postgame"
                "QUESTION" -> "$player, how do you assess the game?"
                "HUMBLE" -> "I want to keep improving and help my teammates."
                "BRASH" -> "I want the responsibility of leading this team."
                "ANALYTICAL" -> "My focus is on preparation for the next opponent."
                "ANALYST", "FAN", "HATER", "TEAMMATE" -> "gamewire | ${copy.replace("\n", " ")}"
                "COACH_QUOTE", "STAR_QUOTE", "QUOTE" -> "—"
                "PRO_COMP" -> "${field("Position").ifBlank { "Basketball" }} prospect"
                "STRENGTHS", "WEAKNESSES" -> scouting.ifBlank { production.ifBlank { copy } }
                "UPSIDE" -> "${field("Position").ifBlank { "Basketball" }} development prospect. $production"
                "SEVERITY_OUTLOOK" -> "$player: $availability"
                "NEXT_MAN_UP" -> editorial
                "AWARD_TITLE" -> "${field("Award")} · ${field("Winner")}"
                "ACCEPTANCE_SPEECH" -> "Thank you for this honor. I appreciate my teammates and coaches."
                "MEDIA_TRIBUTE" -> "$player received ${field("Award")} for ${field("Season")}. ${field("Stats/Line")}"
                "HALFTIME_SPEECH" -> "${field("Situation")}. Stay ready for the second half."
                "TACTICAL_ADJUSTMENT" -> "Keep communicating on defense and finish each possession with a rebound."
                "STANDOUT_ANALYSIS" -> event.removePrefix("Camp: ")
                "GRADE_A", "GRADE_B" -> "—"
                else -> when {
                    label.startsWith("SCOOP") || label.startsWith("RUMOR") -> {
                        val contracts = lines.filter { it.startsWith("CONTRACT ") }
                        val index = label.substringAfterLast(' ').toIntOrNull()?.minus(1) ?: 0
                        val contract = contracts.getOrNull(index)?.removePrefix("CONTRACT ")
                        "Contract watch | ${contract ?: event.ifBlank { copy }.replace("\n", " ")}"
                    }
                    label.startsWith("KEY") -> "The matchup ahead | ${copy.replace("\n", " ")}"
                    else -> copy.replace("\n", " ")
                }
            }
            "$label: $value"
        }
        return AiText(text)
    }
}
