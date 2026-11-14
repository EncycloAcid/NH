package com.nh.hoopscoach.game

import kotlinx.serialization.Serializable

/**
 * The user's playing career, kept on the coach saves that follow it (college, then the NBA), so one
 * life runs player → college coach → NBA coach.
 */
@Serializable
class PlayedAs(
    val name: String,
    val pos: String,
    /** "Duke" or "Duke → Kansas". */
    val college: String,
    val collegeYears: String,
    /** "4 seasons · 18.2 pts, 5.1 reb, 3.3 ast". */
    val collegeLine: String,
    val collegeHonors: List<String>,
    /** "No. 7 pick, 2030" or "Undrafted". */
    val draft: String,
    val proTeams: List<String>,
    /** The franchises he played for, by slot, most recent last. */
    val proSlots: List<String>,
    val proYears: String,
    val proLine: String,
    val proSeasons: Int,
    val honors: List<String>,
    val rings: Int,
    val allStars: Int,
    val mvps: Int,
    /** "Hall of Famer", "Played overseas"... */
    val verdict: String,
    val hofClass: Int,
    val allTime: Int,
    val lastSeason: Int,
    var inducted: Boolean = false,
)

/** One stop on the path: "2027–2030 · Player · Duke". */
class Stop(val years: String, val role: String, val where: String, val line: String)

object Lifetime {
    private fun f1(v: Double) = "%.1f".format(v)
    private fun span(a: Int, b: Int) = if (a == b) "$a" else "$a–$b"
    private fun times(n: Int, what: String) = if (n == 1) what else "$n× $what"

    private fun line(seasons: Int, s: StatLine) =
        "$seasons ${if (seasons == 1) "season" else "seasons"} · ${f1(s.ppg)} pts, ${f1(s.rpg)} reb, ${f1(s.apg)} ast"

    /** His playing career, at the moment it ends (retired from the pros, or done after college). */
    fun fromPlayer(lg: League): PlayedAs? {
        val r = lg.road ?: return null
        val h = lg.hero ?: return null
        val col = h.career.filter { it.cls != PRO_YEAR }
        val pro = h.career.filter { it.cls == PRO_YEAR }
        val colTot = r.collegeTotals ?: StatLine().also { t -> col.forEach { t.add(it.s) }; if (!lg.pro && h.stats.gp > 0) t.add(h.stats) }
        val colSeasons = col.size + (if (!lg.pro && h.stats.gp > 0 && col.none { it.season == lg.season }) 1 else 0)
        val proTot = StatLine().also { t -> pro.forEach { t.add(it.s) } }
        val proSeasons = pro.size
        val firstCol = col.minOfOrNull { it.season } ?: lg.season
        val lastCol = col.maxOfOrNull { it.season } ?: lg.season
        val draftYear = r.draftSeason.takeIf { it > 0 } ?: lastCol
        val award = { k: String -> h.awards.count { it.substringAfter(" ").startsWith(k) && (it.substringBefore(" ").toIntOrNull() ?: 0) <= draftYear } }
        val colHonors = buildList {
            col.count { it.result == "National Champion" }.takeIf { it > 0 }?.let { add(times(it, "National champion")) }
            award("Nat MVP").takeIf { it > 0 }?.let { add(times(it, "National Player of the Year")) }
            (award("AA 1st") + award("AA 2nd") + award("AA 3rd")).takeIf { it > 0 }?.let { add(times(it, "All-American")) }
            award("Conf POY").takeIf { it > 0 }?.let { add(times(it, "Conference Player of the Year")) }
        }
        val honors = buildList {
            if (r.rings > 0) add("${r.rings} ${if (r.rings == 1) "ring" else "rings"}")
            if (r.mvps > 0) add("${r.mvps} MVP${if (r.mvps == 1) "" else "s"}")
            h.awards.count { it.substringAfter(" ") == "Finals MVP" }.takeIf { it > 0 }?.let { add(times(it, "Finals MVP")) }
            if (r.allStars > 0) add(times(r.allStars, "All-Star"))
            h.awards.count { it.substringAfter(" ").startsWith("All-NBA") }.takeIf { it > 0 }?.let { add(times(it, "All-NBA")) }
        }
        val proLeague = lg.pro && proSeasons > 0
        return PlayedAs(
            name = h.name, pos = h.pos.name,
            // In the pro league the team list is the NBA's, so the schools come from the saved line.
            college = if (lg.pro) r.collegeSchools else r.schools.map { lg.team(it).school }.distinct().joinToString(" → "),
            collegeYears = span(firstCol, lastCol),
            collegeLine = line(colSeasons.coerceAtLeast(1), colTot),
            collegeHonors = colHonors,
            draft = if (r.draftPick > 0) "No. ${r.draftPick} pick, $draftYear" else "Undrafted",
            proTeams = if (proLeague) r.proTeams.map { lg.team(it).name }.distinct() else emptyList(),
            proSlots = if (proLeague) r.proTeams.map { lg.team(it).slot }.filter { it.isNotEmpty() } else emptyList(),
            proYears = if (proLeague) span(pro.minOf { it.season }, pro.maxOf { it.season }) else "",
            proLine = if (proLeague) line(proSeasons, proTot) else "",
            proSeasons = if (proLeague) proSeasons else 0,
            honors = honors,
            rings = r.rings, allStars = r.allStars, mvps = r.mvps,
            verdict = if (proLeague) ProCareer.hofVerdict(lg) else "Played overseas",
            hofClass = r.hofClass,
            allTime = if (proLeague) AllTime.rank(lg) else 0,
            lastSeason = lg.season,
        )
    }

    /** How big a name he is to NBA front offices: 2 a legend, 1 a known pro, 0 neither. */
    fun name(p: PlayedAs?): Int = when {
        p == null -> 0
        p.mvps > 0 || p.hofClass > 0 || p.allStars >= 5 || p.allTime > 0 -> 2
        p.allStars > 0 || p.rings > 0 || p.proSeasons >= 8 -> 1
        else -> 0
    }

    /** The extra chance NBA teams call a college coach because of who he was as a player. */
    fun nbaBoost(lg: League, round: Int): Double {
        val n = name(lg.playedAs)
        if (n == 0 || round < 1) return 0.0
        return when (n) {
            2 -> when { round >= 3 -> 0.35; round >= 2 -> 0.2; else -> 0.1 }
            else -> when { round >= 3 -> 0.2; round >= 2 -> 0.1; else -> 0.0 }
        }
    }

    /** His Hall of Fame night comes while he's coaching: once, in the season of his class. */
    fun hallOfFame(lg: League) {
        val p = lg.playedAs ?: return
        if (p.hofClass <= 0 || p.inducted || lg.season < p.hofClass) return
        p.inducted = true
        // Enshrinement night, on a screen of its own.
        runCatching { Celebrations.hallOfFame(lg, p) }.onFailure {
            lg.news += News(lg.season, lg.day, "${p.name} enters the Hall of Fame, class of ${p.hofClass}", true)
            Alerts.add(lg, "The Hall of Fame", "Class of ${p.hofClass}: ${p.name}.", "award")
        }
    }

    /** The whole path so far, oldest first: playing, then every coaching stop. */
    fun path(lg: League): List<Stop> {
        val p = lg.playedAs ?: return emptyList()
        val out = mutableListOf<Stop>()
        out += Stop(p.collegeYears, "Player", p.college, p.collegeLine)
        if (p.proYears.isNotEmpty()) out += Stop(p.proYears, "NBA player", p.proTeams.joinToString(" → "), p.proLine)
        else {
            val back = (lg.userCoach.history.minOfOrNull { it.season } ?: lg.season) - 1
            out += Stop(if (back > p.lastSeason) span(p.lastSeason + 1, back) else "", "Overseas", "Pro ball abroad", "")
        }
        // Coaching stops: runs of seasons at the same team.
        val c = lg.userCoach
        val seasons = c.history.map { Triple(it.season, it.team, it.teamId < 0 || !lg.pro) } +
            (if (lg.phase != Phase.OFFSEASON && lg.phase != Phase.WRAP_UP && c.history.none { it.season == lg.season }) listOf(Triple(lg.season, if (lg.pro) lg.user.name else lg.user.school, !lg.pro)) else emptyList())
        var i = 0
        while (i < seasons.size) {
            var j = i
            while (j + 1 < seasons.size && seasons[j + 1].second == seasons[i].second) j++
            val run = c.history.filter { it.season in seasons[i].first..seasons[j].first && it.team == seasons[i].second }
            val w = run.sumOf { it.w }; val l = run.sumOf { it.l }
            val best = run.map { it.result }.firstOrNull { it.contains("Champion") } ?: run.map { it.result }.lastOrNull { it.isNotEmpty() && it != "Missed" } ?: ""
            out += Stop(span(seasons[i].first, seasons[j].first) + if (j == seasons.size - 1 && c.history.none { it.season == seasons[j].first }) " (now)" else "",
                if (seasons[i].third) "College coach" else "NBA coach", seasons[i].second,
                if (run.isEmpty()) "First season under way" else "$w-$l" + if (best.isNotEmpty()) " · $best" else "")
            i = j + 1
        }
        return out
    }
}
