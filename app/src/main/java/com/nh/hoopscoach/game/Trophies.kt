package com.nh.hoopscoach.game

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/** An achievement he's unlocked: which one, the season, and a word on how. */
@Serializable
class Achievement(val id: String, val season: Int, val detail: String = "")

/**
 * The trophy room: milestones across the whole career (player, college coach, NBA coach). Each one is
 * checked as the season goes; a new one gets a small pop-up. They travel with him from save to save.
 */
object Trophies {
    class Def(val id: String, val title: String, val how: String, val group: String, val check: (League) -> String?)

    private val NCAA_RESULTS = setOf("National Champion", "Runner-Up", "Final Four", "Elite Eight", "Sweet 16", "Round of 32", "Round of 64", "First Four")

    private fun hero(lg: League): Player? = if (lg.road != null) lg.hero else null
    private fun careerPts(lg: League, h: Player): Int = h.career.filter { it.cls == PRO_YEAR }.sumOf { it.s.pts } + (if (lg.pro) h.stats.pts else 0)
    private fun has(h: Player, suffix: String) = h.awards.firstOrNull { it.substringAfter(" ") == suffix }?.substringBefore(" ")
    private fun hasPrefix(h: Player, prefix: String) = h.awards.firstOrNull { it.substringAfter(" ").startsWith(prefix) }?.substringBefore(" ")

    /** The coach's seasons, including this one in progress. */
    private fun coachSeasons(lg: League): List<CoachSeason> {
        if (lg.road != null) return emptyList()
        val c = runCatching { lg.userCoach }.getOrNull() ?: return emptyList()
        return c.history
    }
    private fun coachWins(lg: League): Int {
        if (lg.road != null) return 0
        val c = runCatching { lg.userCoach }.getOrNull() ?: return 0
        val done = c.history.filter { it.season != lg.season }.sumOf { it.w }
        val now = c.history.firstOrNull { it.season == lg.season }?.w ?: runCatching { lg.user.w }.getOrDefault(0)
        return done + now
    }
    private fun titles(lg: League) = coachSeasons(lg).count { it.result == "National Champion" || it.result == "Champion" }
    private fun isCollege(lg: League, s: CoachSeason) = !lg.pro || s.teamId < 0

    val ALL: List<Def> = listOf(
        // As a player.
        Def("pts30", "Thirty", "Score 30 in a game", "Player") { lg -> hero(lg)?.let { if (it.highs[0] >= 30) "${it.highs[0]} points" else null } },
        Def("pts40", "Forty", "Score 40 in a game", "Player") { lg -> hero(lg)?.let { if (it.highs[0] >= 40) "${it.highs[0]} points" else null } },
        Def("pts50", "Fifty", "Score 50 in a game", "Player") { lg -> hero(lg)?.let { if (it.highs[0] >= 50) "${it.highs[0]} points" else null } },
        Def("triple", "Triple-double", "10 points, 10 rebounds and 10 assists in a game", "Player") { lg ->
            hero(lg)?.log?.firstOrNull { it.pts >= 10 && it.reb >= 10 && it.ast >= 10 }?.let { "${it.pts}-${it.reb}-${it.ast}" }
        },
        Def("aa", "All-American", "Make an All-America team", "Player") { lg -> hero(lg)?.let { h -> hasPrefix(h, "AA ")?.let { "$it" } } },
        Def("foy", "Freshman of the Year", "Win National Freshman of the Year", "Player") { lg -> hero(lg)?.let { has(it, "FOY") } },
        Def("npoy", "Player of the Year", "Win National Player of the Year", "Player") { lg -> hero(lg)?.let { has(it, "Nat MVP") } },
        Def("ctitle", "Cutting down the nets", "Win a national title as a player", "Player") { lg ->
            hero(lg)?.career?.firstOrNull { it.cls != PRO_YEAR && it.result == "National Champion" }?.let { "${it.season} with ${it.team}" }
        },
        Def("lottery", "Lottery pick", "Go in the top 14 of the draft", "Player") { lg -> lg.road?.draftPick?.takeIf { it in 1..14 }?.let { "No. $it pick" } },
        Def("top5", "Top five", "Go in the top five of the draft", "Player") { lg -> lg.road?.draftPick?.takeIf { it in 1..5 }?.let { "No. $it pick" } },
        Def("allstar", "All-Star", "Make an All-Star team", "Player") { lg -> lg.road?.takeIf { it.allStars > 0 }?.let { "${it.allStars}×" } },
        Def("mvp", "Most Valuable", "Win MVP", "Player") { lg -> hero(lg)?.let { has(it, "MVP") } },
        Def("ring", "Ring", "Win an NBA title as a player", "Player") { lg -> lg.road?.takeIf { it.rings > 0 }?.let { "${it.rings}×" } },
        Def("fmvp", "Finals MVP", "Be Finals MVP", "Player") { lg -> hero(lg)?.let { has(it, "Finals MVP") } },
        Def("k10", "10,000 points", "Score 10,000 career NBA points", "Player") { lg -> hero(lg)?.let { careerPts(lg, it).takeIf { p -> p >= 10_000 }?.let { p -> "%,d".format(p) } } },
        Def("k20", "20,000 points", "Score 20,000 career NBA points", "Player") { lg -> hero(lg)?.let { careerPts(lg, it).takeIf { p -> p >= 20_000 }?.let { p -> "%,d".format(p) } } },
        Def("k30", "30,000 points", "Score 30,000 career NBA points", "Player") { lg -> hero(lg)?.let { careerPts(lg, it).takeIf { p -> p >= 30_000 }?.let { p -> "%,d".format(p) } } },
        Def("at50", "All-time top 50", "Reach the top 50 of the all-time list", "Player") { lg -> if (hero(lg) != null && lg.pro && lg.day % 5 == 0) AllTime.rank(lg).takeIf { it in 1..50 }?.let { "No. $it" } else null },
        Def("at10", "All-time top 10", "Reach the top 10 of the all-time list", "Player") { lg -> if (hero(lg) != null && lg.pro && lg.day % 5 == 0) AllTime.rank(lg).takeIf { it in 1..10 }?.let { "No. $it" } else null },
        Def("hof", "Hall of Famer", "Make the Hall of Fame as a player", "Player") { lg ->
            (lg.road?.hofClass?.takeIf { it > 0 } ?: lg.playedAs?.hofClass?.takeIf { it > 0 })?.let { "Class of $it" }
        },
        // As a coach.
        Def("w1", "First win", "Win your first game as a head coach", "Coach") { lg -> if (coachWins(lg) >= 1) "" else null },
        Def("w100", "100 wins", "Win 100 games as a head coach", "Coach") { lg -> if (coachWins(lg) >= 100) "" else null },
        Def("w250", "250 wins", "Win 250 games as a head coach", "Coach") { lg -> if (coachWins(lg) >= 250) "" else null },
        Def("w500", "500 wins", "Win 500 games as a head coach", "Coach") { lg -> if (coachWins(lg) >= 500) "" else null },
        Def("w1000", "1,000 wins", "Win 1,000 games as a head coach", "Coach") { lg -> if (coachWins(lg) >= 1000) "" else null },
        Def("dance", "The Big Dance", "Make the NCAA Tournament as a coach", "Coach") { lg ->
            coachSeasons(lg).firstOrNull { isCollege(lg, it) && it.result in NCAA_RESULTS }?.let { "${it.season}, ${it.team}" }
        },
        Def("ff", "Final Four", "Reach a Final Four as a coach", "Coach") { lg ->
            coachSeasons(lg).firstOrNull { it.result in setOf("Final Four", "Runner-Up", "National Champion") }?.let { "${it.season}, ${it.team}" }
        },
        Def("ntitle", "National champion", "Win a national title as a coach", "Coach") { lg -> coachSeasons(lg).firstOrNull { it.result == "National Champion" }?.let { "${it.season}, ${it.team}" } },
        Def("w30", "Thirty-win season", "Win 30 games in a college season", "Coach") { lg -> coachSeasons(lg).firstOrNull { isCollege(lg, it) && it.w >= 30 }?.let { "${it.w}-${it.l}, ${it.season}" } },
        Def("playoffs", "Playoff coach", "Make the NBA playoffs as a coach", "Coach") { lg ->
            coachSeasons(lg).firstOrNull { lg.pro && it.teamId >= 0 && it.result.isNotEmpty() && it.result != "Missed playoffs" }?.let { "${it.season}, ${it.team}" }
        },
        Def("w60", "Sixty wins", "Win 60 games in an NBA season", "Coach") { lg -> coachSeasons(lg).firstOrNull { lg.pro && it.teamId >= 0 && it.w >= 60 }?.let { "${it.w}-${it.l}, ${it.season}" } },
        Def("nbatitle", "World champion", "Win an NBA title as a coach", "Coach") { lg -> coachSeasons(lg).firstOrNull { it.result == "Champion" }?.let { "${it.season}, ${it.team}" } },
        Def("coty", "Coach of the Year", "Win Coach of the Year", "Coach") { lg ->
            if (lg.road != null) null else {
                val c = runCatching { lg.userCoach }.getOrNull()
                when {
                    c == null -> null
                    c.natCoty > 0 -> "${c.natCoty}× in college"
                    lg.proState?.honors?.any { it.award == "Coach of the Year" && it.name == c.name } == true -> "NBA"
                    else -> null
                }
            }
        },
        Def("dynasty", "Dynasty", "Win three titles as a coach", "Coach") { lg -> titles(lg).takeIf { it >= 3 }?.let { "$it titles" } },
        Def("gold", "Gold medal coach", "Coach Team USA to a gold medal", "Coach") { lg -> lg.usaGolds.takeIf { it > 0 }?.let { "$it×" } },
        Def("hofcoach", "Hall of Fame coach", "Make the Hall of Fame as a coach", "Coach") { lg ->
            if (lg.road != null) null else runCatching { lg.userCoach }.getOrNull()?.hofSeason?.takeIf { it > 0 }?.let { "Class of $it" }
        },
    )

    fun def(id: String) = ALL.firstOrNull { it.id == id }

    /** Unlocks whatever he's earned. The first pass on an older save unlocks quietly. */
    fun check(lg: League) {
        val have = lg.achievements.map { it.id }.toHashSet()
        val quiet = !lg.trophiesSeeded
        for (d in ALL) {
            if (d.id in have) continue
            val detail = runCatching { d.check(lg) }.getOrNull() ?: continue
            lg.achievements += Achievement(d.id, lg.season, detail)
            if (!quiet) Alerts.add(lg, "Achievement unlocked", "${d.title}: ${d.how.replaceFirstChar { it.lowercase() }}" + (if (detail.isNotEmpty()) " ($detail)." else "."), "trophy")
        }
        lg.trophiesSeeded = true
    }

    /** Carries the trophy room into the next save of the same career. */
    fun carry(from: League, to: League) {
        val have = to.achievements.map { it.id }.toHashSet()
        to.achievements += from.achievements.filter { it.id !in have }
        to.trophiesSeeded = true
        // The same faces on social media all career long.
        if (from.pfpSeed != 0L) to.pfpSeed = from.pfpSeed
    }
}

/**
 * The greatest teams ever: every champion rated on one scale. A team's score is its win rate, its
 * scoring margin and its playoff run. Real NBA champions since 1980 are rated from their records (their
 * margins estimated from them), so this league's teams can be measured against the 1996 Bulls.
 */
object GreatTeams {
    class Rated(val season: Int, val name: String, val teamId: Int, val w: Int, val l: Int, val margin: Double, val estimated: Boolean,
                val pw: Int, val pl: Int, val score: Double, val real: Boolean, val coach: String)

    /** A team's average scoring margin this season. */
    fun margin(t: Team): Double = if (t.totals.gp <= 0) 0.0 else (t.totals.pts - t.opp.pts).toDouble() / t.totals.gp

    private fun score(pro: Boolean, w: Int, l: Int, margin: Double, pw: Int, pl: Int): Double {
        val pct = w.toDouble() / (w + l).coerceAtLeast(1)
        return if (pro) 50 * pct + 2 * margin + (if (pw + pl > 0) (pw - pl) * 0.4 else 4.0)
        else 50 * pct + 1.2 * margin
    }

    fun list(lg: League): List<Rated> {
        val out = mutableListOf<Rated>()
        val champ = if (lg.pro) "Champion" else "National Champion"
        for (t in lg.teams) for (h in t.history) if (h.result == champ) {
            val est = h.margin == 0.0
            val m = if (est) (h.w.toDouble() / (h.w + h.l).coerceAtLeast(1) - 0.5) * (if (lg.pro) 30.0 else 40.0) else h.margin
            out += Rated(h.season, if (lg.pro) t.name else t.school, t.id, h.w, h.l, m, est, h.pw, h.pl, score(lg.pro, h.w, h.l, m, h.pw, h.pl), false, h.coach)
        }
        if (lg.pro) {
            val first = NbaHistory.firstSeason(lg)
            for (season in 1980 until first) {
                val slot = NbaHistory.realChampion(season) ?: continue
                val y = NbaHistory.real(season, slot) ?: continue
                if (out.any { it.season == season }) continue
                val t = lg.teams.firstOrNull { it.slot == slot }
                val m = (y.w.toDouble() / (y.w + y.l).coerceAtLeast(1) - 0.5) * 30.0
                out += Rated(season, t?.name ?: slot, t?.id ?: -1, y.w, y.l, m, true, 0, 0, score(true, y.w, y.l, m, 0, 0), true, "")
            }
        }
        return out.sortedByDescending { it.score }
    }

    /** Where [t]'s season (this one, finished or not yet in its history) ranks among every champion. */
    fun rankOf(lg: League, t: Team): Int {
        val all = list(lg).toMutableList()
        if (all.none { it.teamId == t.id && it.season == lg.season }) {
            var pw = 0; var pl = 0
            lg.proState?.series?.filter { it.round >= 1 && it.involves(t.id) }?.forEach { s -> val (a, b) = ProSeason.seriesScore(lg, s, t.id); pw += a; pl += b }
            all += Rated(lg.season, t.name, t.id, t.w, t.l, margin(t), false, pw, pl, score(lg.pro, t.w, t.l, margin(t), pw, pl), false, "")
        }
        return all.sortedByDescending { it.score }.indexOfFirst { it.teamId == t.id && it.season == lg.season } + 1
    }

    fun scoreText(r: Rated) = "${r.score.roundToInt()}"
}
