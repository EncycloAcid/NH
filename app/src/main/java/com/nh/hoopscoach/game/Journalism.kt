package com.nh.hoopscoach.game

/**
 * Facts for the AI sports desk: only real league state (results, rankings, recruiting).
 * No invented scores — the model must stay inside this brief.
 */
object Journalism {
    data class Package(
        val stories: List<News>,
        val isAi: Boolean,
        val detail: String,
    )

    /** Compact event brief used by [Ollama.generateSportsJournalism]. */
    fun eventBrief(lg: League): String = buildString {
        val u = lg.user
        append(SituationAwareness.leagueBrief(lg))
        appendLine("YOUR TEAM: ${u.school} (${u.w}-${u.l}) · Elo ${"%.0f".format(u.elo)} · prestige ${u.prestige}")
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

    /** Rule-based package when AI is off or fails — still grounded in [eventBrief] facts. */
    fun fallbackPackage(lg: League): Package {
        val stories = mutableListOf<News>()
        val outlet = "Hoops Wire Desk"
        val u = lg.user
        val today = lg.games.filter { it.played && it.day == lg.day && it.involves(lg.userTeamId) }
        today.maxByOrNull { kotlin.math.abs(it.hs - it.aws) }?.let { g ->
            val won = g.winner() == u.id
            val opp = lg.team(g.opp(u.id))
            stories += News(
                lg.season, lg.day,
                if (won) "${u.school} holds off ${opp.school}, ${g.scoreFor(u.id)}-${g.scoreAgainst(u.id)}"
                else "${opp.school} stuns ${u.school}, ${g.scoreAgainst(u.id)}-${g.scoreFor(u.id)}",
                mine = true, gameId = g.id, kind = "game",
                body = "From the wire: ${u.school} ${if (won) "won" else "fell"} ${g.scoreFor(u.id)}-${g.scoreAgainst(u.id)} against ${opp.school} on day ${lg.day}. All numbers from the box score.",
                source = "Staff · $outlet", teamId = u.id,
            )
        }
        val board = lg.teams.sortedByDescending { it.elo }
        val top = board.take(5)
        val defending = TitleDefense.champion(lg)
        val titleLeaders = lg.teams.map { it to TitleDefense.titles(lg, it).size }.filter { it.second > 0 }.sortedByDescending { it.second }.take(5)
        if (defending != null || titleLeaders.isNotEmpty()) {
            fun titleLine(t: Team): String = if (lg.pro) {
                val ts = NbaHistory.titles(lg, t)
                when {
                    ts.isEmpty() -> "${t.name} has ${NbaHistory.titleWords(0)}."
                    else -> "${t.name} owns ${NbaHistory.titleWords(ts.size)}, the last in ${ts.last()}."
                }
            } else CollegeHistory.line(lg, t)
            val body = buildString {
                if (defending != null) {
                    val label = if (lg.pro) defending.name else defending.school
                    append("Defending champion $label sits ${defending.w}-${defending.l}")
                    if (defending.ap > 0) append(" (AP #${defending.ap})")
                    append(". ${titleLine(defending)}\n\n")
                }
                if (titleLeaders.isNotEmpty()) {
                    append("Banner count across the league:\n")
                    for ((t, n) in titleLeaders) append("- ${if (lg.pro) t.name else t.school}: $n · now ${t.w}-${t.l}\n")
                }
                append("\n${titleLine(u)}")
            }
            stories += News(
                lg.season, lg.day,
                if (defending != null) "Title watch: Can ${if (lg.pro) defending.name else defending.school} stay on the mountain?"
                else "Title ledger: who owns the banners in this league?",
                mine = defending?.id == u.id || titleLeaders.any { it.first.id == u.id },
                kind = "column", body = body, source = "Staff · $outlet",
                teamId = defending?.id ?: titleLeaders.firstOrNull()?.first?.id ?: u.id,
            )
        }
        val debate = buildString {
            append("National debate — who sits atop the board after day ${lg.day}?\n\n")
            top.forEachIndexed { i, t -> append("${i + 1}. ${t.school} (${t.w}-${t.l})\n") }
            append("\n${u.school} checks in at No.${board.indexOfFirst { it.id == u.id } + 1}. Rankings use live Elo, not vibes.")
        }
        stories += News(
            lg.season, lg.day, "Rankings debate: ${top.firstOrNull()?.school ?: "the field"} holds the top spot",
            mine = board.take(25).any { it.id == u.id }, kind = "column", body = debate, source = "Staff · $outlet",
            teamId = top.firstOrNull()?.id ?: u.id,
        )
        if (!lg.pro) {
            val r = lg.recruits.filter { it.committed < 0 }.minByOrNull { it.p.recruitRank.takeIf { n -> n > 0 } ?: 9999 }
            if (r != null) {
                val no = if (r.p.recruitRank > 0) "No.${r.p.recruitRank}" else "an unranked"
                stories += News(
                    lg.season, lg.day, "Recruiting buzz: $no ${r.p.name} still on the board",
                    mine = false, kind = "column",
                    body = "Insider notebook: ${r.p.name} (${r.p.stars}★ ${r.p.posLabel}) lists ${r.pref.label} as a priority. No commitment is confirmed — only the published board ranking.",
                    source = "Staff · $outlet", pid = r.p.id,
                )
            }
        }
        val hotTake = News(
            lg.season, lg.day,
            if (u.streak >= 3) "Hot take: Is ${u.school}'s ${u.streak}-game surge sustainable?"
            else if (u.streak <= -3) "Hot take: ${u.school}'s skid (${-u.streak} straight) raises hard questions"
            else "Notebook: What ${u.school}'s ${u.w}-${u.l} start actually means",
            mine = true, kind = "column",
            body = "Controversial but grounded: ${u.school} sits ${u.w}-${u.l} with a ${if (u.streak >= 0) "W${u.streak}" else "L${-u.streak}"} streak. The Elo board has them No.${board.indexOfFirst { it.id == u.id } + 1}. Argue from those facts — not invented injuries or quotes.",
            source = "Staff · $outlet", teamId = u.id,
        )
        stories += hotTake
        return Package(stories, false, "Game-written journalism desk (${stories.size} pieces).")
    }
}
