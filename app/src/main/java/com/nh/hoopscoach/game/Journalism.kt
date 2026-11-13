package com.nh.hoopscoach.game

/**
 * Facts for the AI sports desk: only real league state (results, rankings, titles, recruiting).
 * No invented scores — the model must stay inside this brief.
 */
object Journalism {
    data class Package(
        val stories: List<News>,
        val isAi: Boolean,
        val detail: String,
    )

    private fun label(lg: League, t: Team) = if (lg.pro) t.name else t.school

    private fun titleLine(lg: League, t: Team): String = if (lg.pro) {
        val ts = NbaHistory.titles(lg, t)
        when {
            ts.isEmpty() -> "${t.name} has ${NbaHistory.titleWords(0)}."
            else -> "${t.name} owns ${NbaHistory.titleWords(ts.size)}, the last in ${ts.last()}."
        }
    } else CollegeHistory.line(lg, t)

    /** Compact event brief used by [Ollama.generateSportsJournalism]. */
    fun eventBrief(lg: League): String = buildString {
        val u = lg.user
        append(SituationAwareness.leagueBrief(lg))
        appendLine("YOUR TEAM: ${label(lg, u)} (${u.w}-${u.l}) · Elo ${"%.0f".format(u.elo)} · prestige ${u.prestige}")
        appendLine("DESK PRIORITY: Lead with titles, defending champions, droughts, and league-wide races. Results support the title narrative — they are not the only story.")
        val today = lg.games.filter { it.played && it.day == lg.day }.sortedByDescending {
            kotlin.math.abs(it.hs - it.aws) + if (it.involves(lg.userTeamId)) 40 else 0
        }
        if (today.isNotEmpty()) {
            appendLine("TODAY'S RESULTS:")
            for (g in today.take(8)) {
                val h = lg.team(g.home); val a = lg.team(g.away)
                appendLine("- ${a.school} ${g.aws}, ${h.school} ${g.hs}${if (g.ot > 0) " (OT)" else ""}${if (lg.rivalry(g) != null) " [RIVALRY]" else ""} id=${g.id}")
            }
        }
        val top = lg.teams.sortedByDescending { it.elo }.take(10)
        val userRank = top.indices.firstOrNull { top[it].id == u.id }?.plus(1)
            ?: (lg.teams.sortedByDescending { it.elo }.indexOfFirst { it.id == u.id } + 1)
        appendLine("Your board rank: #$userRank")
        if (!lg.pro) {
            val board = lg.recruits.filter { it.committed < 0 }.sortedBy { it.p.recruitRank.takeIf { n -> n > 0 } ?: 9999 }.take(8)
            if (board.isNotEmpty()) {
                appendLine("RECRUITING BOARD:")
                for (r in board) {
                    val no = if (r.p.recruitRank > 0) "No.${r.p.recruitRank}" else "UR"
                    appendLine("- $no ${r.p.name} (${r.p.posLabel}, ${r.p.stars}★) · pref ${r.pref.label}")
                }
            }
        } else {
            val hot = lg.players.filter { it.teamId >= 0 && it.contractYears <= 1 }.sortedByDescending { it.ovr }.take(6)
            if (hot.isNotEmpty()) {
                appendLine("EXPIRING / SHORT CONTRACTS:")
                for (p in hot) appendLine("- ${p.name} (${lg.team(p.teamId).name}) ovr ${p.ovr}, ${p.contractYears}yr left")
            }
        }
        val recentNews = lg.news.filter { it.season == lg.season && it.day >= lg.day - 2 }.takeLast(6)
        if (recentNews.isNotEmpty()) {
            appendLine("RECENT HEADLINES (do not contradict):")
            for (n in recentNews) appendLine("- ${n.text}")
        }
    }

    /** Rule-based package when AI is off or fails — titles and league pulse first. */
    fun fallbackPackage(lg: League): Package {
        val stories = mutableListOf<News>()
        val outlet = "Hoops Wire Desk"
        val u = lg.user
        val defending = TitleDefense.champion(lg)
        val titleLeaders = lg.teams.map { it to TitleDefense.titles(lg, it).size }.filter { it.second > 0 }.sortedByDescending { it.second }.take(6)
        val board = lg.teams.sortedByDescending { it.elo }
        val top = board.take(5)

        // 1) Title watch — always lead when we have banner facts
        if (defending != null || titleLeaders.isNotEmpty()) {
            val body = buildString {
                if (defending != null) {
                    append("Defending champion ${label(lg, defending)} sits ${defending.w}-${defending.l}")
                    if (defending.ap > 0) append(" (AP #${defending.ap})")
                    append(". ${titleLine(lg, defending)}\n\n")
                    val streak = TitleDefense.streak(lg, defending, lg.season - 1)
                    if (streak >= 2) append("They are chasing ${TitleDefense.chasing(streak)}.\n\n")
                }
                if (titleLeaders.isNotEmpty()) {
                    append("Banner count across the league:\n")
                    for ((t, n) in titleLeaders) append("- ${label(lg, t)}: $n · now ${t.w}-${t.l}\n")
                }
                append("\n${titleLine(lg, u)}")
            }
            stories += News(
                lg.season, lg.day,
                if (defending != null) "Title watch: Can ${label(lg, defending)} stay on the mountain?"
                else "Title ledger: who owns the banners in this league?",
                mine = defending?.id == u.id || titleLeaders.any { it.first.id == u.id },
                kind = "column", body = body, source = "Staff · $outlet",
                teamId = defending?.id ?: titleLeaders.firstOrNull()?.first?.id ?: u.id,
            )
        }

        // 2) League pulse — polls, board, hot seats
        val hotSeats = lg.coaches.filter { it.teamId >= 0 && it.security <= 35 }.sortedBy { it.security }.take(3)
        val poll = lg.teams.filter { it.ap in 1..10 }.sortedBy { it.ap }
        val pulse = buildString {
            append("League pulse after day ${lg.day}:\n\n")
            if (poll.isNotEmpty()) {
                append("AP board: ")
                append(poll.joinToString { "#${it.ap} ${it.school} (${it.w}-${it.l})" })
                append(".\n\n")
            } else {
                append("Elo board: ")
                top.forEachIndexed { i, t -> append("${i + 1}. ${t.school} (${t.w}-${t.l}) ") }
                append("\n\n")
            }
            if (hotSeats.isNotEmpty()) {
                append("Hot seats: ")
                append(hotSeats.joinToString {
                    val t = lg.team(it.teamId)
                    "Coach ${it.name} (${t.school}, security ${it.security})"
                })
                append(".\n\n")
            }
            append("${label(lg, u)} checks in at board No.${board.indexOfFirst { it.id == u.id } + 1} (${u.w}-${u.l}).")
        }
        stories += News(
            lg.season, lg.day,
            if (poll.isNotEmpty()) "League pulse: AP board and the title chase"
            else "League pulse: power board after day ${lg.day}",
            mine = true, kind = "column", body = pulse, source = "Staff · $outlet",
            teamId = poll.firstOrNull()?.id ?: top.firstOrNull()?.id ?: u.id,
        )

        // 3) Today's result — framed with title context when possible
        val today = lg.games.filter { it.played && it.day == lg.day && it.involves(lg.userTeamId) }
        today.maxByOrNull { kotlin.math.abs(it.hs - it.aws) }?.let { g ->
            val won = g.winner() == u.id
            val opp = lg.team(g.opp(u.id))
            val champNote = defending?.let {
                if (it.id == u.id) " The defending champions are ${if (won) "still rolling" else "under pressure"}."
                else if (it.id == opp.id) " That was a result against the defending champion."
                else ""
            } ?: ""
            stories += News(
                lg.season, lg.day,
                if (won) "${u.school} holds off ${opp.school}, ${g.scoreFor(u.id)}-${g.scoreAgainst(u.id)}"
                else "${opp.school} stuns ${u.school}, ${g.scoreAgainst(u.id)}-${g.scoreFor(u.id)}",
                mine = true, gameId = g.id, kind = "game",
                body = "From the wire: ${u.school} ${if (won) "won" else "fell"} ${g.scoreFor(u.id)}-${g.scoreAgainst(u.id)} against ${opp.school} on day ${lg.day}.$champNote ${titleLine(lg, u)}",
                source = "Staff · $outlet", teamId = u.id,
            )
        }

        if (!lg.pro) {
            val r = lg.recruits.filter { it.committed < 0 }.minByOrNull { it.p.recruitRank.takeIf { n -> n > 0 } ?: 9999 }
            if (r != null) {
                val no = if (r.p.recruitRank > 0) "No.${r.p.recruitRank}" else "an unranked"
                stories += News(
                    lg.season, lg.day, "Recruiting buzz: $no ${r.p.name} still on the board",
                    mine = false, kind = "column",
                    body = "Insider notebook: ${r.p.name} (${r.p.stars}★ ${r.p.posLabel}) lists ${r.pref.label} as a priority. Programs with banners still sell history — ${titleLine(lg, u)} No commitment is confirmed.",
                    source = "Staff · $outlet", pid = r.p.id,
                )
            }
        }

        val drought = titleLeaders.map { it.first }.firstOrNull { t ->
            val ts = TitleDefense.titles(lg, t)
            ts.isNotEmpty() && lg.season - ts.last() >= 10
        }
        stories += News(
            lg.season, lg.day,
            when {
                drought != null -> "Hot take: How long can ${label(lg, drought)} wait for the next banner?"
                u.streak >= 3 -> "Hot take: Is ${u.school}'s ${u.streak}-game surge a title statement?"
                u.streak <= -3 -> "Hot take: ${u.school}'s skid (${-u.streak} straight) and the banner standard"
                else -> "Notebook: Where ${u.school}'s ${u.w}-${u.l} sits in the title conversation"
            },
            mine = true, kind = "column",
            body = buildString {
                append("Controversial but grounded: ${u.school} sits ${u.w}-${u.l}")
                append(" with a ${if (u.streak >= 0) "W${u.streak}" else "L${-u.streak}"} streak")
                append(" and board No.${board.indexOfFirst { it.id == u.id } + 1}. ")
                append(titleLine(lg, u))
                if (drought != null) append(" Meanwhile ${titleLine(lg, drought)}")
                append(" Argue from those facts — not invented injuries or quotes.")
            },
            source = "Staff · $outlet", teamId = drought?.id ?: u.id,
        )
        return Package(stories, false, "Game-written journalism desk (${stories.size} pieces).")
    }
}
