package com.nh.hoopscoach.game

/**
 * Accumulated rivalry history for AI coverage: programs (series), coaches (heat),
 * players (happiness / starring roles), and fans (mood / sellouts).
 */
object HistoricalRivalries {
    data class Package(val stories: List<News>, val isAi: Boolean, val detail: String)

    fun historyBrief(lg: League): String = buildString {
        // Snapshot: rivalry desk runs on IO while the sim mutates coaches/games/news.
        val coachRoster = LeagueLists.coaches(lg)
        val teams = LeagueLists.teams(lg)
        val news = LeagueLists.news(lg)
        val games = LeagueLists.games(lg)
        appendLine("LEVEL: ${if (lg.pro) "NBA" else "NCAA"} · SEASON ${lg.season} · DAY ${lg.day} · PHASE ${lg.phase}")
        val ranked = lg.rivals.sortedByDescending { it.aWins + it.bWins }.take(10)
        if (ranked.isEmpty()) appendLine("PROGRAM RIVALRIES: none recorded yet")
        else {
            appendLine("PROGRAM RIVALRIES (series since dynasty start):")
            for (r in ranked) {
                val a = lg.team(r.a); val b = lg.team(r.b)
                val emergent = r.name.equals("Emerging rivalry", ignoreCase = true) || r.name.isEmpty()
                appendLine("- ${r.title}: ${a.school} ${r.aWins}-${r.bWins} ${b.school}${if (emergent) " [ORGANIC]" else ""} · prestige ${a.prestige}/${b.prestige}")
            }
        }
        val mine = lg.rivals.filter { it.involves(lg.userTeamId) }.sortedByDescending { it.aWins + it.bWins }
        if (mine.isNotEmpty()) {
            appendLine("YOUR RIVALRIES:")
            for (r in mine.take(5)) {
                val opp = lg.team(r.other(lg.userTeamId))
                appendLine("- vs ${opp.school}: you ${r.winsFor(lg.userTeamId)}-${r.winsFor(opp.id)} · ${r.title}")
            }
        }
        val coachHeats = CoachRivals.rivals(lg).take(6)
        if (coachHeats.isNotEmpty()) {
            appendLine("COACHING RIVALRIES:")
            for (cr in coachHeats) {
                val oc = coachRoster.firstOrNull { it.id == cr.coachId } ?: continue
                val school = teams.getOrNull(oc.teamId)?.school ?: "unemployed"
                appendLine("- vs ${oc.name} ($school): ${cr.w}-${cr.l}, heat ${CoachRivals.heatLabel(cr.heat)}, big games ${cr.big}")
            }
        }
        val recent = news.filter { it.season == lg.season && (it.kind == "rivalry" || it.kind == "barb" || it.text.contains("Emerging rivalry", true)) }.takeLast(8)
        if (recent.isNotEmpty()) {
            appendLine("RECENT RIVALRY / BARB HEADLINES:")
            for (n in recent) appendLine("- ${n.text}")
        }
        val u = lg.user
        appendLine("FAN / PROGRAM PULSE: ${u.school} fans ${Fans.mood(u.fans).lowercase()} · sellouts ${u.sellouts} · buzz ${"%.0f".format(u.buzz)}")
        val stars = lg.rosterOf(u).sortedByDescending { it.stats.ppg }.take(3)
        if (stars.isNotEmpty()) {
            appendLine("YOUR PLAYERS IN THE RIVALRY ECOSYSTEM:")
            for (p in stars) appendLine("- ${p.name}: ${"%.1f".format(p.stats.ppg)} ppg · mood happiness ${p.happiness}/100")
        }
        val upcoming = games.filter { !it.played && it.day >= lg.day && it.day <= lg.day + 14 && lg.rivalry(it) != null }
            .sortedBy { it.day }.take(4)
        if (upcoming.isNotEmpty()) {
            appendLine("UPCOMING RIVALRY DATES:")
            for (g in upcoming) {
                val r = lg.rivalry(g)!!
                appendLine("- Day ${g.day}: ${lg.team(g.away).school} @ ${lg.team(g.home).school} (${r.title})")
            }
        }
    }

    fun fallbackPackage(lg: League): Package {
        val outlet = "Hoops Wire Rivalries"
        val stories = mutableListOf<News>()
        val r = lg.rivals.filter { it.involves(lg.userTeamId) }.maxByOrNull { it.aWins + it.bWins }
            ?: lg.rivals.maxByOrNull { it.aWins + it.bWins }
        if (r != null) {
            val a = lg.team(r.a); val b = lg.team(r.b)
            stories += News(
                lg.season, lg.day,
                "History lesson: ${r.title} — ${a.school} ${r.aWins}-${r.bWins} ${b.school}",
                mine = r.involves(lg.userTeamId), kind = "column",
                body = "Program ledger since the dynasty began. Prestige sits at ${a.prestige} and ${b.prestige}. Fans, coaches, and players keep stacking chapters every time these schools meet.",
                source = "Staff · $outlet", teamId = if (r.involves(lg.userTeamId)) lg.userTeamId else r.a,
            )
        }
        val cr = CoachRivals.rivals(lg).firstOrNull()
        if (cr != null) {
            val oc = lg.coaches.firstOrNull { it.id == cr.coachId }
            val me = runCatching { lg.userCoach }.getOrNull()
            if (oc != null && me != null) {
                stories += News(
                    lg.season, lg.day,
                    "Sideline grudge: ${me.last} vs ${oc.last} (${CoachRivals.heatLabel(cr.heat).lowercase()})",
                    mine = true, kind = "column",
                    body = "Head-to-head ${cr.w}-${cr.l} for ${me.name}. Heat ${cr.heat}/12 with ${cr.big} big-stage meetings. Barbs land when the series boils.",
                    source = "Staff · $outlet", teamId = oc.teamId.takeIf { it >= 0 } ?: lg.userTeamId,
                )
            }
        }
        val organic = lg.rivals.firstOrNull { it.name.equals("Emerging rivalry", ignoreCase = true) }
        if (organic != null) {
            val a = lg.team(organic.a); val b = lg.team(organic.b)
            stories += News(
                lg.season, lg.day,
                "Born from the schedule: ${a.school}–${b.school} is now a rivalry",
                mine = organic.involves(lg.userTeamId), kind = "column",
                body = "Repeated close games and postseason heat forged this one. Series ${organic.aWins}-${organic.bWins}. Coaches, fans, and players will keep the ledger.",
                source = "Staff · $outlet", teamId = if (organic.involves(lg.userTeamId)) lg.userTeamId else organic.a,
            )
        }
        if (stories.isEmpty()) {
            stories += News(
                lg.season, lg.day, "Rivalry watch: history still writing itself",
                mine = false, kind = "column",
                body = "Traditional and organic rivalries accumulate series records, coaching heat, fan mood, and player happiness as games land.",
                source = "Staff · $outlet",
            )
        }
        return Package(stories, false, "Game-written historical rivalries (${stories.size}).")
    }
}
