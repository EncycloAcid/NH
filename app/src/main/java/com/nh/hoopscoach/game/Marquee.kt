package com.nh.hoopscoach.game

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/**
 * The big nights on the calendar. NBA: opening night, Christmas Day (five games, the best matchups),
 * and a national TV game most nights. College: top-10 showdowns, ranked matchups, rivalry games and
 * Rivalry Week at the end of the regular season. Marquee games sell out, get previewed the day before
 * and written up after.
 */
object Marquee {
    private fun stars(lg: League, t: Team) = lg.rosterOf(t).maxOfOrNull { it.ovr } ?: 0

    /** How big a matchup is: both teams' strength and their best players. */
    private fun heat(lg: League, g: Game): Double {
        val h = lg.team(g.home); val a = lg.team(g.away)
        return h.elo + a.elo + (stars(lg, h) + stars(lg, a)) * 6.0 + (if (lg.rivalry(g) != null) 120 else 0)
    }

    fun christmasDay(lg: League): Int = (ProLeague.lastDay(lg) * 0.36).roundToInt().coerceAtLeast(2)

    private val cache = HashMap<Triple<Int, Int, Int>, Set<Int>>()

    /** The games picked out of a day: Christmas's five, opening night's three, or tonight's TV game. */
    private fun picks(lg: League, day: Int, n: Int): Set<Int> {
        val key = Triple(System.identityHashCode(lg), lg.season, day * 10 + n)
        cache[key]?.let { return it }
        val games = lg.games.filter { it.day == day && (it.type == GameType.CONF || it.type == GameType.NONCONF) }
        val out = games.sortedByDescending { heat(lg, it) }.take(n).map { it.id }.toSet()
        if (games.isNotEmpty() && games.all { it.played || it.day >= lg.day }) cache[key] = out
        return out
    }

    /** The marquee label for [g], or "" for an ordinary night. */
    fun label(lg: League, g: Game): String {
        if (g.neutral && g.type != GameType.NONCONF) return ""
        if (lg.pro) {
            if (g.type != GameType.CONF && g.type != GameType.NONCONF) return ""
            if (g.label == "Rivalry Night") return ""
            if (g.label.isNotEmpty()) return ""
            if (g.day == christmasDay(lg) && g.id in picks(lg, g.day, 5)) return "Christmas Day"
            if (g.day == 1 && g.id in picks(lg, 1, 3)) return "Opening Night"
            if (g.day % 3 == 0 && g.id in picks(lg, g.day, 1)) return "National TV"
            return ""
        }
        val h = lg.team(g.home); val a = lg.team(g.away)
        if (g.type != GameType.CONF && g.type != GameType.NONCONF && g.type != GameType.EVENT) return ""
        if (lg.rivalry(g) != null) return if (g.day >= Schedule.REG_DAYS - 4) "Rivalry Week" else "Rivalry game"
        if (h.ap in 1..10 && a.ap in 1..10) return "Top-10 showdown"
        if (h.ap in 1..25 && a.ap in 1..25 && (h.ap <= 12 || a.ap <= 12)) return "Ranked showdown"
        return ""
    }

    fun isMarquee(lg: League, g: Game) = label(lg, g).isNotEmpty()

    private fun the(lg: League, t: Team) = if (lg.pro) "the ${t.name}" else t.school
    private fun rank(t: Team) = if (t.ap in 1..25) "No. ${t.ap} " else ""

    /** The night before: the preview stories. */
    fun preview(lg: League) {
        if (lg.phase != Phase.REGULAR) return
        val tomorrow = lg.games.filter { it.day == lg.day + 1 && !it.played }.filter { isMarquee(lg, it) }
        if (tomorrow.isEmpty()) return
        if (lg.pro) {
            val xmas = tomorrow.filter { label(lg, it) == "Christmas Day" }
            if (xmas.isNotEmpty()) {
                val slate = xmas.joinToString(", ") { "${lg.team(it.away).name} at ${lg.team(it.home).name}" }
                lg.news += News(lg.season, lg.day, "Christmas Day: ${xmas.size} games headlined by ${lg.team(xmas.first().away).name.substringAfterLast(' ')} at ${lg.team(xmas.first().home).name.substringAfterLast(' ')}",
                    xmas.any { it.involves(lg.userTeamId) }, -1, "marquee", "The league's showcase: $slate. The best players in the world, the whole country watching.", "Hoops Wire")
            }
            tomorrow.filter { label(lg, it) == "Opening Night" || label(lg, it) == "National TV" }.forEach { g ->
                val h = lg.team(g.home); val a = lg.team(g.away)
                lg.news += News(lg.season, lg.day, "${label(lg, g)}: the ${a.name} visit the ${h.name}", g.involves(lg.userTeamId), -1, "marquee",
                    "${a.name} (${a.record}) at ${h.name} (${h.record}). " + listOfNotNull(lg.rosterOf(a).maxByOrNull { it.ovr }, lg.rosterOf(h).maxByOrNull { it.ovr }).joinToString(" against ") { it.name } + ".", "Hoops Wire", h.id)
            }
        } else tomorrow.filter { label(lg, it) != "Rivalry game" }.forEach { g ->
            val h = lg.team(g.home); val a = lg.team(g.away)
            lg.news += News(lg.season, lg.day, "${label(lg, g)}: ${rank(a)}${a.school} at ${rank(h)}${h.school}", g.involves(lg.userTeamId), -1, "marquee",
                "${a.school} (${a.record}) visits ${h.school} (${h.record}). The building's been sold out for weeks.", "Hoops Wire", h.id)
        }
    }

    /** After a marquee game: the write-up, and a bump in followers for a player-mode star who shows up. */
    fun afterGame(lg: League, g: Game, teams: Array<Team>, lines: Array<LinkedHashMap<Int, StatLine>>, score: IntArray) {
        val label = label(lg, g)
        if (label.isEmpty()) return
        val w = if (score[0] > score[1]) 0 else 1
        val win = teams[w]; val lose = teams[1 - w]
        val star = lines[w].maxByOrNull { it.value.pts }?.let { lg.playerOrNull(it.key)?.to(it.value) }
        val head = "$label: ${the(lg, win).replaceFirstChar { it.uppercase() }} ${if (lg.pro) "beat" else "beats"} ${the(lg, lose)}, ${score[w]}-${score[1 - w]}"
        lg.news += News(lg.season, lg.day, head, g.involves(lg.userTeamId), g.id, "marquee",
            star?.let { (p, s) -> "${p.name} led the way with ${s.pts} points, ${s.reb} rebounds and ${s.ast} assists on the big stage." } ?: "", "Hoops Wire", win.id, star?.first?.id ?: -1)
        val hero = lg.hero?.takeIf { lg.road != null } ?: return
        val mine = (lines[0][hero.id] ?: lines[1][hero.id]) ?: return
        lg.road?.let { r -> r.followers += (mine.pts * (if (lg.pro) 900 else 300)) + if (mine.pts >= 30) 20_000 else 0 }
    }

    /** The marquee games in the next few days, league-wide (the user's first). */
    fun upcoming(lg: League, days: Int = 4): List<Game> =
        lg.games.filter { !it.played && it.day >= lg.day && it.day < lg.day + days && isMarquee(lg, it) }
            .sortedWith(compareByDescending<Game> { it.involves(lg.userTeamId) }.thenBy { it.day }).take(6)
}

/** The user's record against another coach, and how heated it's gotten. */
@Serializable
class CoachRival(val coachId: Int, var w: Int = 0, var l: Int = 0, var heat: Int = 0, var big: Int = 0, var lastBarb: Int = -1)

/**
 * Coaching rivalries: every coach the user faces has a head-to-head with him. Close games, rivalry
 * games and postseason meetings heat it up; a year apart cools it. Past a point it's a rivalry: the
 * other coach takes shots in the press, and the head-to-head shows before every meeting.
 */
object CoachRivals {
    fun heatLabel(h: Int) = when { h >= 9 -> "Bitter"; h >= 6 -> "Heated"; h >= 3 -> "Simmering"; else -> "Cool" }

    fun of(lg: League, coachId: Int): CoachRival? = lg.coachRivals.firstOrNull { it.coachId == coachId }

    /** Real rivals: heated enough to count. */
    fun rivals(lg: League): List<CoachRival> = lg.coachRivals.filter { it.heat >= 3 }.sortedByDescending { it.heat }

    fun afterGame(lg: League, g: Game, score: IntArray) {
        if (lg.road != null || !g.involves(lg.userTeamId)) return
        val opp = lg.team(g.opp(lg.userTeamId))
        val oc = runCatching { lg.coachOf(opp) }.getOrNull() ?: return
        val r = of(lg, oc.id) ?: CoachRival(oc.id).also { lg.coachRivals += it }
        val mine = if (g.home == lg.userTeamId) 0 else 1
        val won = score[mine] > score[1 - mine]
        if (won) r.w++ else r.l++
        val margin = kotlin.math.abs(score[0] - score[1])
        val post = g.type == GameType.PLAYOFF || g.type == GameType.NCAA || g.type == GameType.CONF_TOURNEY
        r.heat = (r.heat + (if (margin <= 5) 1 else 0) + (if (lg.rivalry(g) != null) 1 else 0) + (if (post) 2 else 0) + (if (!won && margin >= 20) 1 else 0)).coerceAtMost(12)
        if (post) r.big++
        // The other coach has something to say.
        val day = lg.season * 1000 + lg.day
        if (r.heat >= 4 && (margin <= 6 || post || lg.rivalry(g) != null) && day - r.lastBarb >= 10 && Rng.chance(0.55)) {
            r.lastBarb = day
            val me = lg.userCoach.last
            val quote = if (won) Rng.pick(listOf(
                "\"They made a couple more plays than us. That's all I'll say about Coach $me.\"",
                "\"We'll see them again. I've got a long memory.\"",
                "\"Some guys win and talk. Some guys just win. Ask him which one he is.\"",
            )) else Rng.pick(listOf(
                "\"Tell Coach $me we said hello.\"",
                "\"I enjoyed that one. More than usual.\"",
                "\"We knew what they were going to run before they did.\"",
            ))
            val head = "${oc.name} on ${lg.userCoach.name}: ${quote.trim('"').substringBefore('.')}"
            lg.news += News(lg.season, lg.day, head, true, g.id, "barb",
                "After ${if (won) "losing" else "beating"} ${if (lg.pro) "the ${lg.user.name}" else lg.user.school} ${maxOf(score[0], score[1])}-${minOf(score[0], score[1])}, ${oc.name} had this: $quote " +
                    "The coaching rivalry stands at ${r.w}-${r.l} for ${lg.userCoach.last}.", "Hoops Wire", opp.id)
        }
    }

    fun seasonEnd(lg: League) { for (r in lg.coachRivals) r.heat = (r.heat - 2).coerceAtLeast(0) }

    /** The pregame line: "Coach X: you're 4-6 against him (heated)". */
    fun pregame(lg: League, g: Game): String? {
        if (lg.road != null) return null
        val opp = lg.team(g.opp(lg.userTeamId))
        val oc = runCatching { lg.coachOf(opp) }.getOrNull() ?: return null
        val r = of(lg, oc.id)?.takeIf { it.heat >= 3 } ?: return null
        return "Coaching rivalry · ${oc.name}: you're ${r.w}-${r.l} against him (${heatLabel(r.heat).lowercase()})"
    }
}
