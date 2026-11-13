package com.nh.hoopscoach.game

/**
 * NBA / pro draft universe facts: college stock, combine-style workout profiles,
 * draft-night surprises, and former players' pro destinations — all from league state.
 */
object DraftUniverse {
    data class Package(val stories: List<News>, val isAi: Boolean, val detail: String)

    /** Athletic/size profile derived from listed height, weight, and attributes (not an invented combine). */
    fun workoutProfile(p: Player): String {
        val ath = (p.attrs.getOrNull(Attr.PER.ordinal) ?: 70) + (p.attrs.getOrNull(Attr.STL.ordinal) ?: 70) + (p.attrs.getOrNull(Attr.HND.ordinal) ?: 70)
        val burst = when {
            ath >= 255 -> "elite burst / lateral quickness on the listed tools"
            ath >= 225 -> "above-average athleticism on the listed tools"
            ath >= 195 -> "solid functional athlete"
            else -> "skill-first profile; athleticism is a question on the listed tools"
        }
        return "${p.height}\" / ${p.weight} lb · $burst · style ${p.style.label}"
    }

    fun eventBrief(lg: League): String = buildString {
        val players = LeagueLists.players(lg)
        val teams = LeagueLists.teams(lg)
        appendLine("LEVEL: ${if (lg.pro) "NBA" else "NCAA"} · SEASON ${lg.season} · DAY ${lg.day} · PHASE ${lg.phase}")
        val board = Offseason.draftBoard(lg)
        val top = players.filter { it.teamId >= 0 }.sortedBy { board[it.id] ?: 9999 }.take(12)
        if (top.isNotEmpty() && !lg.pro) {
            appendLine("COLLEGE DRAFT STOCK (1 = best):")
            for (p in top) {
                val r = board[p.id] ?: continue
                val t = lg.team(p.teamId)
                appendLine("- No.$r ${p.name} (${t.school}, ${p.posLabel}, ${p.cls}) · ovr ${p.ovr}/pot ${p.pot} · ${"%.1f".format(p.stats.ppg)} ppg · ${Offseason.projection(r, p.year)}")
            }
        }
        val dn = lg.proState?.draftNight
        if (dn != null) {
            appendLine("DRAFT NIGHT: season ${dn.season} · pick ${dn.at + 1} of ${dn.order.size} · ${if (dn.done) "COMPLETE" else "IN PROGRESS"}")
            appendLine("CONSENSUS BOARD (remaining top 10):")
            for (pr in dn.prospects.sortedBy { it.rank }.take(10)) {
                appendLine("- Board No.${pr.rank} ${pr.p.name} (${pr.school}, ${pr.p.posLabel}, age ${pr.p.age}) · ${pr.line} · scout visits ${pr.scout}/3 · ${workoutProfile(pr.p)}")
            }
            if (dn.log.isNotEmpty()) {
                appendLine("PICKS LOGGED:")
                for (m in dn.log.takeLast(12)) {
                    val club = teams.getOrNull(m.teamId)?.name ?: "?"
                    appendLine("- No.${m.pick} $club: ${m.name} (${m.pos}) board No.${m.rank} · grade ${m.grade}")
                }
                dn.log.maxByOrNull { it.pick - it.rank }?.takeIf { it.pick - it.rank >= 5 }?.let {
                    appendLine("NOTABLE SLIDE: ${it.name} fell to No.${it.pick} (board No.${it.rank})")
                }
                dn.log.maxByOrNull { it.rank - it.pick }?.takeIf { it.rank - it.pick >= 5 }?.let {
                    appendLine("NOTABLE REACH: ${it.name} taken No.${it.pick} (board No.${it.rank})")
                }
            }
            if (!dn.done) {
                val mock = DraftNight.mock(lg, 5)
                if (mock.isNotEmpty()) {
                    appendLine("MOCK NEXT 5 (board view, does not change the draft):")
                    for (m in mock) appendLine("- No.${m.slot} ${lg.team(m.teamId).name}: ${m.pr.p.name} — ${m.why}")
                }
            }
        }
        val recent = lg.draftHistory.filter { it.season >= lg.season - 1 }.sortedBy { it.pick }.take(15)
        if (recent.isNotEmpty()) {
            appendLine("RECENT DRAFT CLASS (${recent.first().season}):")
            for (d in recent.take(10)) {
                val club = d.nba.ifBlank { d.proTeam }.ifBlank { teams.getOrNull(d.teamId)?.name ?: "?" }
                appendLine("- No.${d.pick} ${d.playerName} (${d.pos}, ${d.school}) → $club · ovr ${d.ovr}")
            }
        }
        val alumni = lg.draftHistory.filter { it.season <= lg.season - 1 && (it.nba.isNotBlank() || it.proTeam.isNotBlank() || it.pid >= 0) }
            .sortedByDescending { it.season }.distinctBy { it.pid.takeIf { id -> id >= 0 } ?: it.playerName }.take(8)
        if (alumni.isNotEmpty()) {
            appendLine("FORMER PLAYERS / PRO CAREERS:")
            for (d in alumni) {
                val live = d.pid.takeIf { it >= 0 }?.let { lg.anyPlayer(it) }
                val dest = d.proTeam.ifBlank { d.nba }.ifBlank { "pro ball" }
                val line = if (live != null && live.teamId >= 0 && lg.pro) {
                    val tm = teams.getOrNull(live.teamId)
                    "${live.name}: drafted No.${d.pick} (${d.season}) · now ${tm?.name ?: dest} · ${live.proYears} pro yrs · ${"%.1f".format(live.stats.ppg)} ppg"
                } else {
                    "${d.playerName}: drafted No.${d.pick} (${d.season}) out of ${d.school} · destination $dest · drafted ovr ${d.ovr}"
                }
                appendLine("- $line")
            }
        }
        val userPicks = lg.draftHistory.filter { it.teamId == lg.userTeamId && it.season >= lg.season - 2 }
        if (userPicks.isNotEmpty()) {
            appendLine("YOUR RECENT PIPELINE:")
            for (d in userPicks.take(6)) appendLine("- No.${d.pick} ${d.playerName} (${d.season})")
        }
    }

    fun fallbackPackage(lg: League): Package {
        val outlet = "Hoops Wire Draft"
        val stories = mutableListOf<News>()
        val board = Offseason.draftBoard(lg)
        if (!lg.pro) {
            val top = LeagueLists.players(lg).filter { it.teamId >= 0 }.minByOrNull { board[it.id] ?: 9999 }
            if (top != null) {
                val r = board[top.id]!!
                val t = lg.team(top.teamId)
                stories += News(
                    lg.season, lg.day,
                    "Draft stock: ${top.name} sits No.$r nationally (${Offseason.projection(r, top.year)})",
                    mine = t.id == lg.userTeamId, kind = "column",
                    body = "${top.name} (${t.school}, ${top.posLabel}) grades as ${Offseason.projection(r, top.year)} on the live board. Production: ${"%.1f".format(top.stats.ppg)} ppg · ovr ${top.ovr}/pot ${top.pot}. Workout profile: ${workoutProfile(top)}.",
                    source = "Staff · $outlet", teamId = t.id, pid = top.id,
                )
            }
        }
        val dn = lg.proState?.draftNight
        if (dn != null && dn.log.isNotEmpty()) {
            val slide = dn.log.maxByOrNull { it.pick - it.rank }
            val reach = dn.log.maxByOrNull { it.rank - it.pick }
            val surprise = when {
                slide != null && slide.pick - slide.rank >= 5 -> slide
                reach != null && reach.rank - reach.pick >= 5 -> reach
                else -> dn.log.last()
            }
            stories += News(
                lg.season, lg.day,
                "Draft night notebook: ${surprise.name} at No.${surprise.pick} (board No.${surprise.rank})",
                mine = surprise.teamId == lg.userTeamId, kind = "column",
                body = "Logged pick: ${lg.teams.getOrNull(surprise.teamId)?.name} selected ${surprise.name} (${surprise.pos}). Consensus board had him No.${surprise.rank}; grade ${surprise.grade}. No invented medicals or workouts beyond the recorded board.",
                source = "Staff · $outlet", teamId = surprise.teamId, pid = surprise.pid,
            )
        }
        val alum = lg.draftHistory.filter { it.season < lg.season }.maxByOrNull { it.season * 100 - it.pick }
        if (alum != null) {
            val dest = alum.proTeam.ifBlank { alum.nba }.ifBlank { "the pros" }
            stories += News(
                lg.season, lg.day,
                "Where are they now: ${alum.playerName} (No.${alum.pick}, ${alum.season})",
                mine = alum.teamId == lg.userTeamId, kind = "column",
                body = "${alum.playerName} left ${alum.school} as a ${alum.pos} pick and is tracked to $dest. Drafted overall ${alum.ovr}. Pipeline facts only.",
                source = "Staff · $outlet", teamId = alum.teamId, pid = alum.pid,
            )
        }
        if (stories.isEmpty()) {
            stories += News(
                lg.season, lg.day, "Draft universe: board quiet — check back as stock moves",
                mine = false, kind = "column",
                body = "No loud stock movers or draft-night logs yet. The Features/Draft desk will file when the board or lottery night produces recorded facts.",
                source = "Staff · $outlet",
            )
        }
        return Package(stories, false, "Game-written draft universe (${stories.size}).")
    }
}
