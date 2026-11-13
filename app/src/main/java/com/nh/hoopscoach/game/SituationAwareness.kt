package com.nh.hoopscoach.game

/**
 * Shared league situation for AI desks: titles, defending champs, polls, races,
 * hot seats, streaks, moments, and bubble — so copy stays aware of the whole league.
 */
object SituationAwareness {
    /**
     * League-wide brief. Safe for fact sheets and Hoops Wire desks.
     * @param compact shorter block for [AiFacts] (still includes titles / champ / poll).
     */
    fun leagueBrief(lg: League, compact: Boolean = false): String = buildString {
        appendLine("LEAGUE SITUATION: ${if (lg.pro) "NBA" else "NCAA"} · SEASON ${lg.season} · DAY ${lg.day} · PHASE ${lg.phase}")
        val defending = TitleDefense.champion(lg)
        if (defending != null) {
            val n = TitleDefense.streak(lg, defending, lg.season - 1)
            appendLine(
                "DEFENDING CHAMPION: ${defending.school} (${defending.w}-${defending.l}" +
                    (if (defending.ap > 0) ", AP #${defending.ap}" else "") + ")" +
                    if (n >= 2) " · chasing ${TitleDefense.chasing(n)}" else " · defending the banner",
            )
            appendLine(
                "TITLE LINE: " + if (lg.pro) {
                    val ts = NbaHistory.titles(lg, defending)
                    when {
                        ts.isEmpty() -> "${defending.name} has ${NbaHistory.titleWords(0)}."
                        else -> "${defending.name} owns ${NbaHistory.titleWords(ts.size)}, the last in ${ts.last()}."
                    }
                } else CollegeHistory.line(lg, defending),
            )
        }
        val titleCap = if (compact) 5 else 8
        val titleLeaders = lg.teams
            .map { it to TitleDefense.titles(lg, it).size }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(titleCap)
        if (titleLeaders.isNotEmpty()) {
            appendLine("NATIONAL TITLES (all-time in this universe):")
            for ((t, n) in titleLeaders) {
                val last = TitleDefense.titles(lg, t).lastOrNull()
                appendLine("- ${t.school}: $n${last?.let { " (last $it)" } ?: ""} · now ${t.w}-${t.l}")
            }
        }
        val recent = lg.history.takeLast(if (compact) 3 else 6)
        if (recent.isNotEmpty()) {
            appendLine("RECENT LEAGUE CHAMPIONS:")
            for (h in recent) {
                val c = lg.teams.getOrNull(h.champion)
                val r = lg.teams.getOrNull(h.runnerUp)
                appendLine("- ${h.season}: ${c?.school ?: "?"} over ${r?.school ?: "?"}")
            }
        }
        val poll = lg.teams.filter { it.ap in 1..25 }.sortedBy { it.ap }
        val pollN = if (compact) 5 else 10
        if (poll.isNotEmpty()) {
            appendLine("AP POLL (top ${minOf(pollN, poll.size)}):")
            for (t in poll.take(pollN)) appendLine("- #${t.ap} ${t.school} (${t.w}-${t.l})")
        } else {
            val board = lg.teams.sortedByDescending { it.elo }.take(if (compact) 5 else 8)
            appendLine("POWER BOARD (Elo):")
            board.forEachIndexed { i, t -> appendLine("- ${i + 1}. ${t.school} (${t.w}-${t.l})") }
        }
        if (!compact && !lg.pro && lg.teams.any { it.cw + it.cl > 0 }) {
            appendLine("CONFERENCE RACES (leaders):")
            for (c in lg.confs.sortedBy { it.tier }.take(8)) {
                val stand = lg.teams.filter { it.confId == c.id }.sortedWith(
                    compareByDescending<Team> { if (it.cw + it.cl > 0) it.cw.toDouble() / (it.cw + it.cl) else 0.0 }
                        .thenByDescending { it.cw }
                        .thenByDescending { it.elo },
                )
                val lead = stand.firstOrNull() ?: continue
                val chase = stand.getOrNull(1)
                appendLine(
                    "- ${c.name}: ${lead.school} ${lead.cw}-${lead.cl}" +
                        (chase?.let { " · chase ${it.school} ${it.cw}-${it.cl}" } ?: ""),
                )
            }
        }
        val hotSeats = lg.coaches.filter { it.teamId >= 0 && it.security <= 35 }.sortedBy { it.security }.take(if (compact) 3 else 5)
        if (hotSeats.isNotEmpty()) {
            appendLine("HOT SEATS:")
            for (c in hotSeats) {
                val t = lg.teams.getOrNull(c.teamId) ?: continue
                appendLine("- Coach ${c.name} (${t.school}): security ${c.security}/100 · ${t.w}-${t.l} · coach titles ${c.titles}")
            }
        }
        val streaks = lg.teams.filter { kotlin.math.abs(it.streak) >= 4 }.sortedByDescending { kotlin.math.abs(it.streak) }.take(if (compact) 4 else 6)
        if (streaks.isNotEmpty()) {
            appendLine("STREAKS:")
            for (t in streaks) {
                val label = if (t.streak > 0) "W${t.streak}" else "L${-t.streak}"
                appendLine("- ${t.school}: $label (${t.w}-${t.l})")
            }
        }
        val moments = lg.moments.filter { it.season == lg.season && it.day == lg.day }.take(if (compact) 3 else 5)
        if (moments.isNotEmpty()) {
            appendLine("TODAY'S HISTORIC MOMENTS:")
            for (m in moments) appendLine("- ${m.kind}: ${m.headline}")
        }
        if (!lg.pro) {
            if (!compact) {
                lg.bubble?.takeIf { it.season == lg.season }?.let { b ->
                    appendLine("BUBBLE WATCH (${if (b.final) "final" else "projection"}):")
                    for (g in b.groups.take(4)) {
                        val schools = g.ids.take(4).mapNotNull { id -> lg.teams.getOrNull(id)?.school }
                        if (schools.isNotEmpty()) appendLine("- ${g.title}: ${schools.joinToString()}")
                    }
                }
            }
            val u = lg.user
            appendLine("YOUR TOURNAMENT PULSE: NCAA round marker ${Awards.tourneyRound(lg, u.id)} · prestige ${u.prestige}")
            appendLine("YOUR TITLE HISTORY: ${CollegeHistory.line(lg, u)}")
        } else {
            val u = lg.user
            val ts = NbaHistory.titles(lg, u)
            appendLine("YOUR FRANCHISE: ${u.name} (${u.w}-${u.l})")
            appendLine(
                "YOUR TITLE HISTORY: " + when {
                    ts.isEmpty() -> "${u.name} has ${NbaHistory.titleWords(0)}."
                    else -> "${u.name} owns ${NbaHistory.titleWords(ts.size)}, the last in ${ts.last()}."
                },
            )
        }
        if (!compact) lg.tv?.let { appendLine("TV DEBATE: ${it.show} — ${it.topic}") }
    }

    /** Team / school names mentioned in [leagueBrief] so grounding allows them. */
    fun namedTeams(lg: League): List<Team> {
        val ids = mutableSetOf(lg.userTeamId)
        TitleDefense.champion(lg)?.let { ids += it.id }
        lg.history.takeLast(6).forEach { h ->
            if (h.champion >= 0) ids += h.champion
            if (h.runnerUp >= 0) ids += h.runnerUp
        }
        lg.teams.filter { it.ap in 1..10 }.forEach { ids += it.id }
        lg.teams.sortedByDescending { TitleDefense.titles(lg, it).size }.take(8).forEach { ids += it.id }
        lg.coaches.filter { it.teamId >= 0 && it.security <= 35 }.take(5).forEach { ids += it.teamId }
        lg.teams.filter { kotlin.math.abs(it.streak) >= 4 }.take(6).forEach { ids += it.id }
        lg.bubble?.takeIf { it.season == lg.season }?.groups?.take(4)?.forEach { g ->
            g.ids.take(4).forEach { ids += it }
        }
        if (!lg.pro) {
            for (c in lg.confs.sortedBy { it.tier }.take(8)) {
                lg.teams.filter { it.confId == c.id }.sortedWith(
                    compareByDescending<Team> { if (it.cw + it.cl > 0) it.cw.toDouble() / (it.cw + it.cl) else 0.0 }
                        .thenByDescending { it.cw },
                ).take(2).forEach { ids += it.id }
            }
        }
        return ids.mapNotNull { lg.teams.getOrNull(it) }.distinctBy { it.id }
    }
}
