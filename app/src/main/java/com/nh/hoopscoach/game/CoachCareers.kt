package com.nh.hoopscoach.game

/**
 * Facts for AI living coaching careers: reputation (job security), firings, job offers,
 * coaching rivalries, and multi-school history — grounded only in league state.
 */
object CoachCareers {
    data class Package(
        val stories: List<News>,
        val isAi: Boolean,
        val detail: String,
    )

    fun careerBrief(lg: League): String = buildString {
        appendLine("LEVEL: ${if (lg.pro) "NBA" else "NCAA"} · SEASON ${lg.season} · DAY ${lg.day} · PHASE ${lg.phase}")
        val me = runCatching { lg.userCoach }.getOrNull()
        if (me != null) {
            val tenure = me.history.count { it.teamId == lg.userTeamId } + 1
            val schools = me.history.map { it.team }.distinct()
            appendLine("YOUR COACH: ${me.name} · ${me.w}-${me.l} career · security ${me.security}/100 (${NbaCoach.securityLabel(me.security)}) · year $tenure here · titles ${me.titles}")
            if (schools.isNotEmpty()) appendLine("PRIOR STOPS: ${schools.takeLast(6).joinToString(" → ")}")
            if (me.offerSalary > 0) appendLine("EXTENSION ON TABLE: ${me.offerYears}yr / ${CoachContracts.money(me.offerSalary)}")
        }
        val hot = lg.coaches.filter { it.teamId >= 0 && it.security < 40 }.sortedBy { it.security }.take(6)
        if (hot.isNotEmpty()) {
            appendLine("HOT SEATS:")
            for (c in hot) {
                val t = lg.teams.getOrNull(c.teamId) ?: continue
                appendLine("- ${c.name} @ ${t.school} (${t.w}-${t.l}) security ${c.security}/100")
            }
        }
        val rivals = CoachRivals.rivals(lg).take(5)
        if (rivals.isNotEmpty() && me != null) {
            appendLine("COACHING RIVALRIES (you):")
            for (r in rivals) {
                val oc = lg.coaches.firstOrNull { it.id == r.coachId } ?: continue
                val school = lg.teams.getOrNull(oc.teamId)?.school ?: "unemployed"
                appendLine("- vs ${oc.name} ($school): ${r.w}-${r.l}, heat ${CoachRivals.heatLabel(r.heat)}")
            }
        }
        val moves = lg.news.filter {
            it.season == lg.season && (
                it.text.contains("fire", ignoreCase = true) ||
                    it.text.contains("hire", ignoreCase = true) ||
                    it.text.contains("Coaching change", ignoreCase = true) ||
                    it.text.contains("head coach", ignoreCase = true) ||
                    it.kind == "barb"
                )
        }.takeLast(8)
        if (moves.isNotEmpty()) {
            appendLine("RECENT COACHING NEWS:")
            for (n in moves) appendLine("- ${n.text}")
        }
        val legends = lg.coaches.filter { it.titles >= 1 || it.hofSeason > 0 }.sortedByDescending { it.titles * 10 + it.finalFours }.take(5)
        if (legends.isNotEmpty()) {
            appendLine("LEGACY BOARD:")
            for (c in legends) {
                val stop = lg.teams.getOrNull(c.teamId)?.school ?: c.history.lastOrNull()?.team ?: "—"
                appendLine("- ${c.name} ($stop): ${c.titles} titles, ${c.finalFours} Final Fours, ${c.w}-${c.l}")
            }
        }
    }

    fun fallbackPackage(lg: League): Package {
        val stories = mutableListOf<News>()
        val outlet = "Hoops Wire Sideline"
        val me = runCatching { lg.userCoach }.getOrNull()
        val u = lg.user
        if (me != null) {
            val label = NbaCoach.securityLabel(me.security)
            val tenure = me.history.count { it.teamId == u.id } + 1
            stories += News(
                lg.season, lg.day,
                "Reputation watch: ${me.name} is ${label.lowercase()} at ${u.school}",
                mine = true, kind = "column",
                body = "Job security sits at ${me.security}/100 ($label) in year $tenure with the ${u.mascot}. Career mark ${me.w}-${me.l}, ${me.titles} titles. Numbers from the coaching ledger — no invented quotes.",
                source = "Staff · $outlet", teamId = u.id,
            )
        }
        val hot = lg.coaches.filter { it.teamId >= 0 && it.security < 35 }.minByOrNull { it.security }
        if (hot != null) {
            val t = lg.team(hot.teamId)
            stories += News(
                lg.season, lg.day,
                "Hot seat: ${hot.name} under fire at ${t.school}",
                mine = t.id == lg.userTeamId, kind = "column",
                body = "${hot.name} (${t.w}-${t.l}) checks in at ${hot.security}/100 security. Programs in this range often face carousel pressure when the offseason hits.",
                source = "Staff · $outlet", teamId = t.id,
            )
        }
        val rival = CoachRivals.rivals(lg).firstOrNull()
        if (rival != null && me != null) {
            val oc = lg.coaches.firstOrNull { it.id == rival.coachId }
            if (oc != null) {
                stories += News(
                    lg.season, lg.day,
                    "Coaching rivalry: ${me.last} vs ${oc.last} (${rival.w}-${rival.l})",
                    mine = true, kind = "column",
                    body = "Heat is ${CoachRivals.heatLabel(rival.heat).lowercase()}. Head-to-head ${rival.w}-${rival.l} for ${me.name}. Big meetings: ${rival.big}.",
                    source = "Staff · $outlet", teamId = oc.teamId.takeIf { it >= 0 } ?: u.id,
                )
            }
        }
        val multi = me?.history?.map { it.team }?.distinct().orEmpty()
        if (multi.size >= 2) {
            stories += News(
                lg.season, lg.day,
                "Legacy trail: ${me!!.name}'s stops — ${multi.takeLast(4).joinToString(" → ")}",
                mine = true, kind = "column",
                body = "Recorded coaching history across ${multi.size} programs. Latest: ${multi.last()}. Career ${me.w}-${me.l}.",
                source = "Staff · $outlet", teamId = u.id,
            )
        }
        if (stories.isEmpty()) {
            stories += News(
                lg.season, lg.day, "Sideline notebook: coaching reputations around the league",
                mine = false, kind = "column",
                body = "No hot-seat alarms today. Security scores and carousel moves will update as results land.",
                source = "Staff · $outlet",
            )
        }
        return Package(stories, false, "Game-written coaching careers desk (${stories.size} pieces).")
    }
}
