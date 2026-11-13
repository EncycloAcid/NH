package com.nh.hoopscoach.game

/**
 * AI coverage for court storms, upsets, buzzer-beaters, rivalry wins, titles, and heartbreaks.
 * Briefs are built only from recorded [Moment] / [Game] facts.
 */
object HistoricMoments {
    data class Package(
        val stories: List<News>,
        val isAi: Boolean,
        val detail: String,
    )

    fun momentBrief(lg: League, m: Moment): String = buildString {
        val w = lg.teams.getOrNull(m.winner)
        val l = lg.teams.getOrNull(m.loser)
        appendLine("LEVEL: ${if (lg.pro) "NBA" else "NCAA"} · SEASON ${m.season} · DAY ${m.day}")
        appendLine("KIND: ${m.kind.tag} · STAGE: ${m.stage}")
        appendLine("HEADLINE FACT: ${m.headline}")
        appendLine("DETAIL FACT: ${m.detail}")
        if (w != null && l != null) {
            appendLine("FINAL: ${w.school} ${m.wScore}, ${l.school} ${m.lScore}${if (m.ot > 0) " (${m.ot}OT)" else ""}")
            if (m.wSeed > 0 || m.lSeed > 0) appendLine("SEEDS: No.${m.wSeed} ${w.school} over No.${m.lSeed} ${l.school}")
            appendLine("RECORDS: ${w.school} ${w.w}-${w.l} · ${l.school} ${l.w}-${l.l}")
        }
        lg.games.firstOrNull { it.id == m.gameId }?.let { g ->
            if (lg.rivalry(g) != null) appendLine("RIVALRY: ${lg.rivalry(g)!!.name.ifBlank { "traditional rivalry" }}")
            appendLine("SITE: ${if (g.neutral) "neutral" else "home ${lg.team(g.home).school}"}")
        }
        appendLine("USER INVOLVED: ${m.mine}")
    }

    /** Whether fans would realistically storm the court (college home underdog win). */
    fun isCourtStormCandidate(lg: League, g: Game): Boolean {
        if (lg.pro || g.neutral || !g.played) return false
        if (g.winner() != g.home) return false
        val home = lg.team(g.home)
        val away = lg.team(g.away)
        val eloGap = away.elo - home.elo
        val seeds = when (g.type) {
            GameType.NCAA -> lg.ncaa?.seeds
            GameType.CONF_TOURNEY -> lg.confBrackets.firstOrNull { it.confId == home.confId }?.seeds
            else -> null
        }
        val hs = seeds?.get(home.id) ?: 0
        val as_ = seeds?.get(away.id) ?: 0
        val seedUpset = hs > 0 && as_ > 0 && hs - as_ >= 5
        return eloGap >= 70 || seedUpset || (eloGap >= 40 && lg.rivalry(g) != null)
    }

    fun fallbackPackage(lg: League, m: Moment): Package {
        val outlet = "Hoops Wire Moments"
        val w = lg.teams.getOrNull(m.winner)
        val l = lg.teams.getOrNull(m.loser)
        val body = buildString {
            append(m.detail.trim())
            append("\n\n")
            when (m.kind) {
                MomentKind.COURT_STORM -> append("The home crowd spilled onto the floor after ${w?.school ?: "the winners"} knocked off ${l?.school ?: "a favorite"}. Recorded final only — no invented player quotes.")
                MomentKind.HEARTBREAK -> append("Tournament exit: ${l?.school ?: "the loser"}'s run ends. Seeds and score from the bracket sheet.")
                MomentKind.BUZZER -> append("Instant classic energy from a documented buzzer-beater. Stick to the listed shooter and clock.")
                MomentKind.UPSET -> append("Bracket-buster math: seed gap and final are on the record.")
                MomentKind.TITLE -> append("Championship night — parade talk stays inside the known champion and runner-up.")
                else -> append("Historic moment filed from the highlight reel facts above.")
            }
        }
        val story = News(
            m.season, m.day, m.headline, mine = m.mine, gameId = m.gameId,
            kind = when (m.kind) {
                MomentKind.UPSET, MomentKind.COURT_STORM -> "upset"
                MomentKind.BUZZER -> "buzzer"
                else -> "moment"
            },
            body = body, source = "Staff · $outlet",
            teamId = m.winner, approved = true,
        )
        return Package(listOf(story), false, "Game-written historic moment.")
    }
}
