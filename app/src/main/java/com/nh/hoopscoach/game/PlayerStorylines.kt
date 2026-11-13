package com.nh.hoopscoach.game

/**
 * Player arcs grounded in roster facts: bench-to-star leaps, five-star disappointments,
 * walk-on / late-bloomer tournament heroes. No invented stats.
 */
object PlayerStorylines {
    data class Arc(val kind: String, val pid: Int, val headlineHint: String, val facts: String)
    data class Package(val stories: List<News>, val isAi: Boolean, val detail: String)

    fun detectArcs(lg: League): List<Arc> {
        val out = mutableListOf<Arc>()
        val players = LeagueLists.players(lg)
        val teams = LeagueLists.teams(lg)
        val active = players.filter { it.teamId >= 0 }
        // Bench → star: quiet prior season (or low early ovr), loud current role.
        for (p in active) {
            val prior = p.career.lastOrNull()?.s
            val wasBench = prior != null && (prior.mpg < 16.0 || prior.ppg < 7.5) && prior.gp >= 8
            val quietHistory = p.ovrHistory.size >= 2 && p.ovrHistory.first() <= p.ovr - 8
            val nowStar = p.stats.gp >= 5 && (p.stats.mpg >= 26.0 || p.stats.ppg >= 14.0) && p.ovr >= 78
            if ((wasBench || quietHistory) && nowStar) {
                val school = teams.getOrNull(p.teamId)?.school ?: "?"
                out += Arc(
                    "BENCH_TO_STAR", p.id,
                    "From the bench to the spotlight: ${p.name}",
                    "$school · ${p.posLabel} · ${p.cls} · was ${prior?.let { "%.1f mpg / %.1f ppg".format(it.mpg, it.ppg) } ?: "a low-${p.ovrHistory.firstOrNull() ?: p.ovr} overall"} · now ${"%.1f".format(p.stats.ppg)} ppg / ${"%.1f".format(p.stats.mpg)} mpg · ovr ${p.ovr}",
                )
            }
        }
        // Five-star disappoints: blue-chip pedigree, muted production.
        for (p in active.filter { it.stars >= 5 || (it.stars >= 4 && it.recruitRank in 1..40) }) {
            if (p.year < 2 && p.stats.gp < 8) continue
            val muted = p.ovr <= 76 || (p.stats.gp >= 8 && p.stats.ppg < 9.0 && p.stats.mpg < 24.0)
            if (!muted) continue
            val school = teams.getOrNull(p.teamId)?.school ?: "?"
            val pedigree = if (p.recruitRank > 0) "No.${p.recruitRank} ${p.stars}★" else "${p.stars}★"
            out += Arc(
                "RECRUIT_BUST", p.id,
                "Blue-chip heat check: ${p.name} hasn't matched the hype",
                "$school · $pedigree ${p.posLabel} · ${p.cls} · ovr ${p.ovr} · ${if (p.stats.gp > 0) "%.1f ppg / %.1f mpg in %d gp".format(p.stats.ppg, p.stats.mpg, p.stats.gp) else "limited minutes"}",
            )
        }
        // Walk-on / late bloomer tournament (or season) hero.
        for (p in active.filter { it.walkOn || it.tryout > 0 || it.has(Trait.LATE_BLOOMER) || it.story >= 1 }) {
            val tourneyHero = p.tourney.gp >= 1 && (p.tourney.ppg >= 12.0 || p.tourney.pts >= 20)
            val seasonHero = p.stats.gp >= 6 && p.stats.ppg >= 12.0 && (p.walkOn || p.story >= 1)
            if (!tourneyHero && !seasonHero && p.story < 2) continue
            val school = teams.getOrNull(p.teamId)?.school ?: "?"
            val path = when {
                p.walkOn -> "current walk-on"
                p.tryout > 0 -> "former walk-on (tryout ${p.tryout})"
                p.has(Trait.LATE_BLOOMER) -> "Late Bloomer"
                else -> "walk-on story arc"
            }
            out += Arc(
                "WALKON_HERO", p.id,
                "Walk-on hero: ${p.name} writing March (and winter) chapters",
                "$school · $path · ${p.posLabel} · ovr ${p.ovr} · season ${"%.1f".format(p.stats.ppg)} ppg · tourney ${if (p.tourney.gp > 0) "%.1f ppg in %d gp".format(p.tourney.ppg, p.tourney.gp) else "awaiting dance"} · story stage ${p.story}",
            )
        }
        return out.distinctBy { it.pid to it.kind }.sortedByDescending { arcWeight(it) }.take(9)
    }

    private fun arcWeight(a: Arc) = when (a.kind) {
        "WALKON_HERO" -> 3
        "BENCH_TO_STAR" -> 2
        else -> 1
    }

    fun eventBrief(lg: League): String = buildString {
        appendLine("LEVEL: ${if (lg.pro) "NBA" else "NCAA"} · SEASON ${lg.season} · DAY ${lg.day} · PHASE ${lg.phase}")
        val arcs = detectArcs(lg)
        if (arcs.isEmpty()) {
            appendLine("ARCS: none strong enough today — use user roster role changes only if listed below.")
        } else {
            appendLine("PLAYER ARCS (use only these):")
            for (a in arcs) appendLine("- [${a.kind}] ${a.headlineHint} :: ${a.facts}")
        }
        val uRoster = lg.rosterOf(lg.user).sortedByDescending { it.stats.ppg }.take(5)
        if (uRoster.isNotEmpty()) {
            appendLine("YOUR ROTATION SNAPSHOT:")
            for (p in uRoster) {
                appendLine("- ${p.name} (${p.posLabel}${if (p.walkOn) ", walk-on" else ""}${if (p.stars >= 4) ", ${p.stars}★" else ""}): ${"%.1f".format(p.stats.ppg)} ppg, ${"%.1f".format(p.stats.mpg)} mpg, ovr ${p.ovr}")
            }
        }
    }

    fun fallbackPackage(lg: League): Package {
        val arcs = detectArcs(lg)
        val outlet = "Hoops Wire Features"
        val stories = mutableListOf<News>()
        fun add(arc: Arc) {
            val p = lg.playerOrNull(arc.pid) ?: return
            stories += News(
                lg.season, lg.day, arc.headlineHint,
                mine = p.teamId == lg.userTeamId, kind = "column",
                body = "Feature notebook: ${arc.facts}. All numbers from the box / career sheet — no invented quotes.",
                source = "Staff · $outlet", teamId = p.teamId, pid = p.id,
            )
        }
        arcs.firstOrNull { it.kind == "BENCH_TO_STAR" }?.let { add(it) }
        arcs.firstOrNull { it.kind == "RECRUIT_BUST" }?.let { add(it) }
        arcs.firstOrNull { it.kind == "WALKON_HERO" }?.let { add(it) }
        if (stories.isEmpty()) {
            val p = lg.rosterOf(lg.user).maxByOrNull { it.stats.ppg } ?: LeagueLists.players(lg).firstOrNull { it.teamId == lg.userTeamId }
            if (p != null) {
                stories += News(
                    lg.season, lg.day, "Storyline watch: ${p.name} at ${lg.user.school}",
                    mine = true, kind = "column",
                    body = "${p.name} (${p.posLabel}) is at ${"%.1f".format(p.stats.ppg)} ppg / ${"%.1f".format(p.stats.mpg)} mpg with ovr ${p.ovr}. Stronger arcs (bench leaps, blue-chip heat checks, walk-on heroes) will file when the numbers qualify.",
                    source = "Staff · $outlet", teamId = p.teamId, pid = p.id,
                )
            }
        }
        return Package(stories, false, "Game-written player storylines (${stories.size}).")
    }
}
