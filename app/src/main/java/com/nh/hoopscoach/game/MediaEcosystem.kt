package com.nh.hoopscoach.game

/**
 * Living college/pro basketball media ecosystem grounded in real events:
 * breaking news, analyst debates, fan arguments, recruiting rumors, coach barbs,
 * player social posts, and national-poll reactions — not random filler.
 */
object MediaEcosystem {
    data class Package(val stories: List<News>, val isAi: Boolean, val detail: String)

    /** Situation-aware brief: league titles, polls, today, your program, recruiting, rivalries. */
    fun ecosystemBrief(lg: League): String = buildString {
        Brand.ensure(lg)
        val u = lg.user
        // Snapshot: media desk runs on IO while the sim mutates games/teams/news.
        val games = LeagueLists.games(lg)
        val teams = LeagueLists.teams(lg)
        val recruits = LeagueLists.recruits(lg)
        val coaches = LeagueLists.coaches(lg)
        val news = LeagueLists.news(lg)
        append(SituationAwareness.leagueBrief(lg))
        val coach = runCatching { lg.userCoach }.getOrNull()
        appendLine("YOUR PROGRAM: ${if (lg.pro) u.name else u.school} (${u.w}-${u.l}) · AP ${if (u.ap > 0) "#${u.ap}" else "NR"} · Elo ${"%.0f".format(u.elo)} · prestige ${u.prestige}" +
            (coach?.let { " · titles ${it.titles} (coach ${it.name}, security ${it.security})" } ?: ""))
        val today = games.filter { it.played && it.day == lg.day }.sortedByDescending {
            kotlin.math.abs(it.hs - it.aws) + if (it.involves(lg.userTeamId)) 50 else 0 + if (lg.rivalry(it) != null) 20 else 0
        }
        if (today.isNotEmpty()) {
            appendLine("TODAY'S RESULTS (breaking-news fuel):")
            for (g in today.take(10)) {
                val h = lg.team(g.home); val a = lg.team(g.away)
                val upset = h.ap in 1..15 && g.winner() == a.id && (a.ap == 0 || a.ap > h.ap + 5) ||
                    a.ap in 1..15 && g.winner() == h.id && (h.ap == 0 || h.ap > a.ap + 5)
                appendLine("- ${a.school}${if (a.ap > 0) " #${a.ap}" else ""} ${g.aws}, ${h.school}${if (h.ap > 0) " #${h.ap}" else ""} ${g.hs}${if (g.ot > 0) " OT" else ""}${if (lg.rivalry(g) != null) " RIVALRY" else ""}${if (upset) " UPSET" else ""}")
            }
        }
        val poll = teams.filter { it.ap in 1..25 }.sortedBy { it.ap }
        if (poll.isNotEmpty()) {
            appendLine("NATIONAL POLL (AP top ${poll.size}):")
            for (t in poll.take(15)) appendLine("- #${t.ap} ${t.school} (${t.w}-${t.l})")
            if (u.ap > 0) appendLine("Your poll slot: #${u.ap}")
        }
        val tourney = if (!lg.pro) {
            val round = Awards.tourneyRound(lg, u.id)
            "Tournament pulse: NCAA round marker $round · buzz ${Brand.buzzLabel(u.buzz)} · Pull ${Brand.pull(lg, u)}"
        } else "Playoff pulse: ${u.w}-${u.l} · buzz ${Brand.buzzLabel(u.buzz)}"
        appendLine(tourney)
        lg.tv?.let { appendLine("TV DEBATE ON AIR: ${it.show} — ${it.topic}${if (it.poll >= 0) " (fans ${it.poll}% yes)" else ""}") }
        if (!lg.pro) {
            val board = recruits.filter { it.committed < 0 }.sortedBy { it.p.recruitRank.takeIf { n -> n > 0 } ?: 9999 }.take(6)
            if (board.isNotEmpty()) {
                appendLine("RECRUITING BOARD (insider fuel):")
                for (r in board) appendLine("- ${if (r.p.recruitRank > 0) "No.${r.p.recruitRank}" else "UR"} ${r.p.name} (${r.p.stars}★) pref ${r.pref.label}")
            }
        }
        val rivals = lg.rivals.filter { it.involves(u.id) }.take(3)
        if (rivals.isNotEmpty()) {
            appendLine("RIVAL PROGRAMS:")
            for (r in rivals) {
                val o = lg.team(r.other(u.id))
                appendLine("- ${r.title} vs ${o.school}: series you ${r.winsFor(u.id)}-${r.winsFor(o.id)}")
            }
        }
        CoachRivals.rivals(lg).take(3).forEach { cr ->
            val oc = coaches.firstOrNull { it.id == cr.coachId } ?: return@forEach
            appendLine("COACH HEAT: vs ${oc.name} ${cr.w}-${cr.l} (${CoachRivals.heatLabel(cr.heat)})")
        }
        val stars = lg.rosterOf(u).sortedByDescending { it.stats.ppg }.take(3)
        if (stars.isNotEmpty()) {
            appendLine("YOUR PLAYERS (social fuel):")
            for (p in stars) appendLine("- ${p.name}: ${"%.1f".format(p.stats.ppg)} ppg · happiness ${p.happiness}")
        }
        val recent = news.filter { it.season == lg.season && it.day >= lg.day - 1 }.takeLast(8)
        if (recent.isNotEmpty()) {
            appendLine("RECENT WIRE (do not contradict):")
            for (n in recent) appendLine("- ${n.text}")
        }
    }

    /** Rule-based social posts into the road feed when available. */
    fun publishSocialBeats(lg: League) {
        val u = lg.user
        val games = LeagueLists.games(lg)
        val today = games.filter { it.played && it.day == lg.day && it.involves(u.id) }.maxByOrNull { kotlin.math.abs(it.hs - it.aws) }
        if (today != null) {
            val won = today.winner() == u.id
            val opp = lg.team(today.opp(u.id))
            Media.post(
                lg, "${u.school} ${u.mascot}", u.abbr.lowercase() + "bball", "team",
                if (won) "Final: ${u.school} ${today.scoreFor(u.id)}-${today.scoreAgainst(u.id)} over ${opp.school}. On to the next one."
                else "Final: ${opp.school} ${today.scoreAgainst(u.id)}-${today.scoreFor(u.id)}. We respond next.",
                Media.likes(lg, 0.04), verified = true, gameId = today.id,
            )
            val star = lg.rosterOf(u).filter { it.stats.gp > 0 }.maxByOrNull { it.stats.ppg }
            if (star != null) {
                Media.post(
                    lg, star.name, star.last.lowercase() + star.id, "player",
                    if (won) "Great team win. Back to work." else "We'll be better. Trust the process.",
                    Media.likes(lg, 0.03), gameId = today.id,
                )
            }
            val coachLast = runCatching { lg.userCoach.last }.getOrNull() ?: "the staff"
            Media.post(
                lg, Media.fanName(), Media.fanHandle(u), "fan",
                if (won) "That's how you do it, ${u.mascot}! Coach $coachLast cooking."
                else "Coach $coachLast has to answer for that one. Fans deserve better.",
                Media.likes(lg, 0.02), gameId = today.id,
            )
            val pundit = Media.pundit(lg)
            Media.say(
                lg, pundit, "analyst",
                if (u.ap > 0) "${u.school} is No.${u.ap} for a reason — tonight was ${if (won) "a statement" else "a red flag"} against ${opp.school}."
                else "${u.school} (${u.w}-${u.l}) just ${if (won) "climbed" else "slipped"} in the national conversation vs ${opp.school}.",
                Media.likes(lg, 0.025),
            )
        }
        if (!lg.pro) {
            val r = LeagueLists.recruits(lg).filter { it.committed < 0 }.minByOrNull { it.p.recruitRank.takeIf { n -> n > 0 } ?: 9999 }
            if (r != null) {
                val v = Media.insider(lg)
                Media.say(
                    lg, v, "insider",
                    "Hearing ${r.p.name} (${if (r.p.recruitRank > 0) "No.${r.p.recruitRank}" else "unranked"}, ${r.p.stars}★) still prioritizing ${r.pref.label}. ${u.school} in the mix only if the board says so — no commit confirmed.",
                    Media.likes(lg, 0.02),
                )
            }
        }
        val upset = games.filter { it.played && it.day == lg.day }.firstOrNull { g ->
            val w = lg.team(g.winner()); val l = lg.team(g.loser())
            l.ap in 1..10 && (w.ap == 0 || w.ap > l.ap + 4)
        }
        if (upset != null) {
            val w = lg.team(upset.winner()); val l = lg.team(upset.loser())
            Media.post(
                lg, if (lg.pro) "NBA" else "NCAA March Madness", if (lg.pro) "nba" else "marchmadness", "league",
                "POLL ALERT: No.${l.ap} ${l.school} falls to ${w.school} ${upset.scoreFor(w.id)}-${upset.scoreFor(l.id)}. Ballots will move.",
                Media.likes(lg, 0.05), verified = true, gameId = upset.id, league = true,
            )
        }
    }

    fun fallbackPackage(lg: League): Package {
        publishSocialBeats(lg)
        val outlet = "Hoops Wire Network"
        val stories = mutableListOf<News>()
        val u = lg.user
        val games = LeagueLists.games(lg)
        val teams = LeagueLists.teams(lg)
        val recruits = LeagueLists.recruits(lg)
        val coaches = LeagueLists.coaches(lg)
        val defending = TitleDefense.champion(lg)
        val titleLeaders = teams.map { it to TitleDefense.titles(lg, it).size }.filter { it.second > 0 }.sortedByDescending { it.second }.take(5)
        fun titleLine(t: Team): String = if (lg.pro) {
            val ts = NbaHistory.titles(lg, t)
            when {
                ts.isEmpty() -> "${t.name} has ${NbaHistory.titleWords(0)}."
                else -> "${t.name} owns ${NbaHistory.titleWords(ts.size)}, the last in ${ts.last()}."
            }
        } else CollegeHistory.line(lg, t)
        if (defending != null || titleLeaders.isNotEmpty()) {
            val body = buildString {
                if (defending != null) {
                    val name = if (lg.pro) defending.name else defending.school
                    append("Defending champion $name is ${defending.w}-${defending.l}")
                    if (defending.ap > 0) append(" and AP #${defending.ap}")
                    append(". ${titleLine(defending)}\n\n")
                }
                if (titleLeaders.isNotEmpty()) {
                    append("League banner ledger:\n")
                    for ((t, n) in titleLeaders) append("- ${if (lg.pro) t.name else t.school}: $n · now ${t.w}-${t.l}\n")
                }
                append("\n${titleLine(u)}")
            }
            stories += News(
                lg.season, lg.day,
                if (defending != null) "Network title desk: ${if (lg.pro) defending.name else defending.school} and the banner chase"
                else "Network title desk: who owns the banners?",
                mine = defending?.id == u.id, kind = "column", body = body, source = "Staff · $outlet",
                teamId = defending?.id ?: titleLeaders.firstOrNull()?.first?.id ?: u.id,
            )
        }
        val today = games.filter { it.played && it.day == lg.day }.sortedByDescending {
            (if (it.involves(u.id)) 40 else 0) + kotlin.math.abs(it.hs - it.aws)
        }
        today.firstOrNull()?.let { g ->
            val w = lg.team(g.winner()); val l = lg.team(g.loser())
            val champTag = defending?.let { if (it.id == w.id || it.id == l.id) " · defending champ involved" else "" } ?: ""
            stories += News(
                lg.season, lg.day,
                "BREAKING: ${w.school}${if (w.ap > 0) " #${w.ap}" else ""} ${g.scoreFor(w.id)}-${g.scoreFor(l.id)} ${l.school}${if (l.ap > 0) " #${l.ap}" else ""}",
                mine = g.involves(u.id), gameId = g.id, kind = "article",
                body = "Wire copy from the final: ${w.school} ${g.scoreFor(w.id)}, ${l.school} ${g.scoreFor(l.id)}${if (g.ot > 0) " in OT" else ""}$champTag. Title programs and poll teams still set the national conversation.",
                source = "Staff · $outlet", teamId = w.id,
            )
        }
        stories += News(
            lg.season, lg.day,
            "Analysts split: Can ${u.school} (${u.w}-${u.l}${if (u.ap > 0) ", #${u.ap}" else ""}) join the title conversation?",
            mine = true, kind = "column",
            body = run {
                val c = runCatching { lg.userCoach }.getOrNull()
                "One side cites the ${u.w}-${u.l} mark, Program Pull ${Brand.pull(lg, u)}" +
                    (c?.let { ", and coach ${it.name}'s ${it.titles} titles. The other side points at job security ${it.security}/100" } ?: "") +
                    " and the AP board (${if (u.ap > 0) "#${u.ap}" else "unranked"}). ${titleLine(u)}"
            },
            source = "Staff · $outlet", teamId = u.id,
        )
        runCatching { lg.userCoach }.getOrNull()?.let { c ->
            stories += News(
                lg.season, lg.day,
                "Fanbase divided on Coach ${c.last}",
                mine = true, kind = "column",
                body = "Message-board energy: security sits at ${c.security}/100 after a ${u.w}-${u.l} start. Fans argue rotations and recruiting — not invented quotes.",
                source = "Staff · $outlet", teamId = u.id,
            )
        }
        if (!lg.pro) {
            val r = recruits.filter { it.committed < 0 }.minByOrNull { it.p.recruitRank.takeIf { n -> n > 0 } ?: 9999 }
            if (r != null) {
                stories += News(
                    lg.season, lg.day,
                    "Insider notebook: ${r.p.name} still on the board",
                    mine = false, kind = "column",
                    body = "Commitment rumor desk: ${r.p.name} (${r.p.stars}★) lists ${r.pref.label}. No school is confirmed — only the published ranking and preference.",
                    source = "Staff · $outlet", pid = r.p.id,
                )
            }
        }
        val cr = CoachRivals.rivals(lg).firstOrNull()
        if (cr != null) {
            val oc = coaches.firstOrNull { it.id == cr.coachId }
            if (oc != null) {
                stories += News(
                    lg.season, lg.day,
                    "Sideline shots: heat rising with ${oc.name}",
                    mine = true, kind = "column",
                    body = "Coaching rivalry ledger ${cr.w}-${cr.l}, heat ${CoachRivals.heatLabel(cr.heat)}. Rival programs feel it when these staffs meet.",
                    source = "Staff · $outlet", teamId = oc.teamId.takeIf { it >= 0 } ?: u.id,
                )
            }
        }
        val pollHit = games.filter { it.played && it.day == lg.day }.firstOrNull { g ->
            val l = lg.team(g.loser()); l.ap in 1..15
        }
        if (pollHit != null) {
            val w = lg.team(pollHit.winner()); val l = lg.team(pollHit.loser())
            stories += News(
                lg.season, lg.day,
                "Poll watch: No.${l.ap} ${l.school} takes an L — ballots incoming",
                mine = pollHit.involves(u.id), gameId = pollHit.id, kind = "column",
                body = "${w.school} ${pollHit.scoreFor(w.id)}-${pollHit.scoreFor(l.id)} ${l.school}. National poll voters react to shocking (or routine) results from the AP board — no invented voters.",
                source = "Staff · $outlet", teamId = l.id,
            )
        }
        return Package(stories, false, "Game-written media ecosystem (${stories.size} beats + social).")
    }
}
