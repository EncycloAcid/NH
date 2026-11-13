package com.nh.hoopscoach.game

import java.util.Locale

data class PreviewTopic(val kind: String, val teamId: Int, val pid: Int = -1, val subject: String, val facts: String, val priority: Double)

object PreviewTopics {
    fun select(lg: League, college: Preview? = lg.preview, pro: NbaPreview? = lg.proState?.preview): List<PreviewTopic> {
        val out = mutableListOf<PreviewTopic>()
        val title = if (lg.pro) pro?.proj?.mapValues { it.value.title }.orEmpty() else college?.odds?.mapValues { it.value.title }.orEmpty()
        title.entries.sortedWith(compareByDescending<Map.Entry<Int, Double>> { it.value }.thenBy { it.key }).take(2).forEach { (id, p) ->
            val t = lg.teams.firstOrNull { it.id == id } ?: return@forEach
            val record = if (lg.pro) pro?.proj?.get(id)?.let { "${it.w}-${it.l}" } else college?.proj?.get(id)?.let { "${it.w}-${it.l}" }
            out += PreviewTopic("title", id, subject = "${t.name}: the title case", facts = "${PreviewWriting.percent(p)} title chance; projected $record.", priority = 4.0 + p)
        }
        val production = (lg.players + lg.alumni).distinctBy { it.id }.flatMap { p -> p.career.filter {
            it.season == lg.season - 1 && (!lg.pro || it.cls == PRO_YEAR)
        }.map { Triple(it.teamId, p.id, it.s) } }.groupBy { it.first }
        lg.teams.forEach { t ->
            val roster = lg.rosterOf(t)
            val prior = production[t.id].orEmpty()
            val minutes = prior.sumOf { it.third.sec }
            if (minutes > 0) {
                val retained = prior.filter { it.second in t.roster }.sumOf { it.third.sec }.toDouble() / minutes
                if (retained < 0.65) out += PreviewTopic("turnover", t.id, subject = "${t.name}: rebuilding the rotation",
                    facts = "Returns ${PreviewWriting.percent(retained)} of last season's minutes. Newcomers: " + roster.filter { p ->
                        p.career.lastOrNull { it.season == lg.season - 1 }?.teamId != t.id
                    }.sortedByDescending { it.ovr }.take(2).joinToString { p -> p.name + if (p.recruitRank > 0 && p.year == 1) " (recruit No. ${p.recruitRank})" else " (${p.posLabel})" }, priority = 2.0 + 2 * (1 - retained))
            }
            roster.filter { it.out }.maxByOrNull { it.ovr }?.let { p ->
                out += PreviewTopic("injury", t.id, p.id, "${t.name}: covering ${p.name}'s absence",
                    "${p.name} is out ${if (p.injGames >= Injuries.SEASON_OUT) "for the season" else "for ${p.injGames} team games"} with ${p.injury.ifBlank { "an injury" }}.", 4.5 + p.ovr / 100.0)
            }
            val c = lg.coachOf(t)
            if (c.history.lastOrNull()?.let { it.teamId != t.id } == true) out += PreviewTopic("coach", t.id,
                subject = "${t.name}: a new voice on the bench", facts = "${c.name} coached ${c.history.last().team} last season; current job security ${c.security}/100.", priority = 3.4)
        }
        lg.players.filter { it.teamId >= 0 }.mapNotNull { p ->
            p.career.lastOrNull { it.season == lg.season - 1 && it.s.gp > 0 && (!lg.pro || it.cls == PRO_YEAR) }?.let { p to it.s }
        }.sortedByDescending { it.second.ppg }.take(2).forEach { (p, s) ->
            out += PreviewTopic("production", p.teamId, p.id, "${p.name}: proven production",
                String.format(Locale.US, "Last season: %.1f PPG, %.1f RPG, %.1f APG in %d appearances.", s.ppg, s.rpg, s.apg, s.gp), 3.0 + s.ppg / 30)
        }
        lg.games.filter { !it.played && lg.rivalry(it) != null }.sortedWith(compareByDescending<Game> { it.involves(lg.userTeamId) }.thenBy { it.day }).firstOrNull()?.let { g ->
            out += PreviewTopic("rivalry", g.home, subject = "${lg.team(g.home).school} and ${lg.team(g.away).school}: a date to circle",
                facts = "On day ${g.day}, ${lg.team(g.away).name} and ${lg.team(g.home).name} meet ${if (g.neutral) "at a neutral site" else "on ${lg.team(g.home).school}'s floor"} in the ${lg.rivalry(g)!!.name}.", priority = 3.6)
        }
        return out.sortedWith(compareByDescending<PreviewTopic> { it.priority }.thenBy { it.teamId }.thenBy { it.kind })
            .distinctBy { it.kind to it.teamId }.let { all ->
                val kinds = mutableSetOf<String>()
                (all.filter { kinds.add(it.kind) } + all).distinct().take(6)
            }
    }

    fun stories(lg: League, topics: List<PreviewTopic>) = topics.map {
        Story(it.kind.replaceFirstChar { c -> c.uppercase() }, it.subject, it.facts + "\n\n" + ArticleWriting.news(lg, News(lg.season, lg.day, it.subject, teamId = it.teamId, pid = it.pid), includeForecast = false), it.teamId, it.pid)
    }

    fun match(lg: League, topics: List<PreviewTopic>, text: String): PreviewTopic? =
        topics.firstOrNull { t -> t.pid >= 0 && lg.playerOrNull(t.pid)?.let { text.contains(it.name, true) } == true } ?:
            topics.firstOrNull { t -> t.pid < 0 && lg.teams.firstOrNull { it.id == t.teamId }?.let { text.contains(it.school, true) } == true }
}
