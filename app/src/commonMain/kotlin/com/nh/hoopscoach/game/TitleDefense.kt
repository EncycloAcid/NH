package com.nh.hoopscoach.game

import kotlinx.serialization.Serializable

@Serializable
class TitleRun(val season: Int, val champ: Int, val streak: Int, var flags: Int = 0)

/**
 * The defending champion's year, in the news: the title defense opening on banner night, how the
 * champs look at midseason (rolling, or a title hangover), a Finals or title-game rematch, the night
 * the reign ends (and where), a champion missing the playoffs altogether, and repeats: back-to-back,
 * a three-peat, four straight, with how long it's been since anyone did it.
 */
object TitleDefense {
    private const val OPENER = 1
    private const val MIDSEASON = 2
    private const val MISSED = 4
    private const val OUT = 8
    private const val REMATCH = 16
    private const val REPEAT = 32

    fun champion(lg: League): Team? = if (lg.pro) NbaHistory.finalists(lg, lg.season - 1).first else CollegeHistory.lastChampion(lg)

    private fun runnerUp(lg: League): Team? = if (lg.pro) NbaHistory.finalists(lg, lg.season - 1).second
    else lg.history.firstOrNull { it.season == lg.season - 1 }?.runnerUp?.let { lg.teams.getOrNull(it) }

    fun titles(lg: League, t: Team): List<Int> = if (lg.pro) NbaHistory.titles(lg, t) else CollegeHistory.titles(lg, t)

    /** Titles in a row for [t], ending with [through]. */
    fun streak(lg: League, t: Team, through: Int): Int {
        val set = titles(lg, t).toSet()
        var n = 0
        var s = through
        while (s in set) { n++; s-- }
        return n
    }

    /** "back-to-back", "a three-peat", "four straight titles". */
    fun runWords(n: Int): String = when (n) {
        2 -> "back-to-back titles"
        3 -> "a three-peat"
        4 -> "four straight titles"
        5 -> "five straight titles"
        else -> "$n straight titles"
    }

    /** What the champions are chasing this season, after [n] in a row. */
    fun chasing(n: Int): String = when (n) {
        1 -> "a repeat"
        2 -> "a three-peat"
        3 -> "a fourth straight title"
        else -> "title No. ${n + 1} in a row"
    }

    /** Every known champion by season (the real past, then this league's). */
    private fun bySeason(lg: League): Map<Int, Int> {
        val out = HashMap<Int, Int>()
        for (t in lg.teams) for (s in titles(lg, t)) out[s] = t.id
        return out
    }

    /** The last repeat champion before [season], or null. */
    fun lastRepeat(lg: League, season: Int): Pair<Int, Team>? {
        val m = bySeason(lg)
        val s = m.keys.filter { it < season && m[it - 1] == m[it] }.maxOrNull() ?: return null
        return s to lg.team(m.getValue(s))
    }

    /** The last defending NBA champion to miss the playoffs before [season] (records go back to 1980). */
    private fun lastMiss(lg: League, season: Int): Pair<Int, Team>? {
        if (!lg.pro) return null
        val m = bySeason(lg)
        for (s in season - 1 downTo 1981) {
            val c = m[s - 1]?.let { lg.teams.getOrNull(it) } ?: continue
            val y = NbaHistory.season(lg, c, s) ?: continue
            if (!y.playoffs) return s to c
        }
        return null
    }

    private fun the(lg: League, t: Team) = if (lg.pro) "the ${t.name}" else t.school
    private fun The(lg: League, t: Team) = the(lg, t).replaceFirstChar { it.uppercase() }
    private fun are(lg: League) = if (lg.pro) "are" else "is"
    private fun poss(s: String) = if (s.endsWith("s")) "$s'" else "$s's"

    fun check(lg: League) {
        val champ = champion(lg) ?: return
        var d = lg.defense
        if (d == null || d.season != lg.season) { d = TitleRun(lg.season, champ.id, streak(lg, champ, lg.season - 1).coerceAtLeast(1)); lg.defense = d }
        val t = lg.teams.getOrNull(d.champ) ?: return
        val mine = t.id == lg.userTeamId
        fun once(flag: Int, block: () -> Unit) { if (d.flags and flag == 0) { d.flags = d.flags or flag; runCatching(block) } }
        val ps = lg.proState
        val last = t.history.lastOrNull { it.season == lg.season - 1 }

        // Opening night: the banner, and what they're chasing.
        if (lg.phase == Phase.REGULAR && t.w + t.l >= 1) once(OPENER) {
            val back = lg.rosterOf(t).filter { p -> if (lg.pro) p.awards.contains("${lg.season - 1} Champion") else p.career.any { it.season == lg.season - 1 && it.teamId == t.id } }
            val head = if (d.streak >= 2) "${The(lg, t)} open their bid for ${chasing(d.streak)}" else "${The(lg, t)} raise the banner and begin their title defense"
            val body = mutableListOf<String>()
            body += (if (d.streak >= 2) "Winners of ${runWords(d.streak)}, " else "Last season's champions, ") + the(lg, t) + (last?.let { " (${it.w}-${it.l})" } ?: "") +
                " start the year with ${back.size} players back from the title team" + (back.maxByOrNull { it.ovr }?.let { ", ${it.name} among them." } ?: ".")
            body += if (d.streak >= 2) "Nobody wants to be the team that ends it. Everybody is trying."
            else lastRepeat(lg, lg.season)?.let { (y, r) -> "Repeating is the hard part: the last team to do it was ${the(lg, r)}, in $y." } ?: "Repeating is the hard part."
            lg.news += News(lg.season, lg.day, head, mine, -1, "defense", body.joinToString(" "), "Hoops Wire", t.id)
        }
        // Midseason: rolling, or a hangover.
        val mid = if (lg.pro) lg.day >= ProLeague.allStarDay(lg) else lg.day >= 16
        if (lg.phase == Phase.REGULAR && mid && t.w + t.l >= 10) once(MIDSEASON) {
            val pct = t.w.toDouble() / (t.w + t.l)
            val head = when {
                pct < 0.5 -> "Title hangover? The defending champions are ${t.record}"
                pct >= 0.68 -> "The champs are rolling: ${the(lg, t)} ${are(lg)} ${t.record}"
                else -> "Halfway through the title defense, ${the(lg, t)} ${are(lg)} ${t.record}"
            }
            val body = when {
                pct < 0.5 -> "A year after winning it all, ${the(lg, t)} ${are(lg)} below .500. " + (last?.let { "They were ${it.w}-${it.l} last season. " } ?: "") + "There's time. There isn't a lot of it."
                pct >= 0.68 -> "No hangover here: ${the(lg, t)} look like the team to beat again" + (if (d.streak >= 2) ", chasing ${chasing(d.streak)}." else ".")
                else -> "Good, not great. Champions get everyone's best night, and ${the(lg, t)} have felt it."
            }
            lg.news += News(lg.season, lg.day, head, mine, -1, "defense", body, "Hoops Wire", t.id)
        }
        // No postseason at all.
        val missed = if (lg.pro) ps?.series?.any { it.round == 1 } == true && ps.series.none { it.round >= 1 && it.involves(t.id) }
        else lg.ncaa?.let { b -> b.seeds.isNotEmpty() && t.id !in b.seeds } == true
        if (missed) once(MISSED) {
            val head = if (lg.pro) "From champions to the lottery: ${the(lg, t)} miss the playoffs" else "${The(lg, t)} won't defend: the champions miss the NCAA Tournament"
            val since = lastMiss(lg, lg.season)
            val body = "${The(lg, t)} finish ${t.record}, a year after winning the title. " + if (lg.pro) {
                since?.let { (y, c) -> "The last defending champion to miss the playoffs: ${the(lg, c)}, in $y." } ?: "No defending champion had missed the playoffs since at least 1980."
            } else "The banner still hangs. There won't be another one this year."
            lg.news += News(lg.season, lg.day, head, mine, -1, "defense", body, "Hoops Wire", t.id)
            if (mine) Alerts.add(lg, "The title defense is over", if (lg.pro) "A year after the title, you miss the playoffs." else "A year after the title, you're not in the field.", "")
        }
        // A rematch for the title.
        val lastUp = runnerUp(lg)
        if (lastUp != null) {
            val rematch = if (lg.pro) ps?.series?.firstOrNull { it.round == 4 }?.let { setOf(it.hi, it.lo) == setOf(t.id, lastUp.id) } == true
            else lg.ncaa?.rounds?.getOrNull(5)?.filter { it >= 0 }?.toSet()?.let { it.size == 2 && it == setOf(t.id, lastUp.id) } == true
            if (rematch) once(REMATCH) {
                lg.news += News(lg.season, lg.day, if (lg.pro) "A Finals rematch: ${the(lg, t)} and ${the(lg, lastUp)}, again" else "A title-game rematch: ${t.school} and ${lastUp.school}, again",
                    mine || lastUp.id == lg.userTeamId, -1, "defense",
                    "Same two teams, one year later. ${The(lg, t)} won it last time; ${the(lg, lastUp)} have waited a year for this.", "Hoops Wire", t.id)
            }
        }
        // The reign ends.
        val out: Pair<String, Team?>? = if (lg.pro) ps?.series?.firstOrNull { it.round >= 1 && it.done && it.loser == t.id }?.let { s -> ProSeason.roundName(s) to lg.teams.getOrNull(s.winner) }
        else lg.games.firstOrNull { it.type == GameType.NCAA && it.played && it.involves(t.id) && it.winner() != t.id }?.let { g -> Awards.resultFor(lg, t.id) to lg.team(g.winner()) }
        if (out != null) once(OUT) {
            val (round, by) = out
            val finals = round == "Finals" || round == "Runner-Up"
            val head = when {
                finals -> "${The(lg, t)} fall short of ${chasing(d.streak)}" + (by?.let { ", beaten by ${the(lg, it)} for the title" } ?: "")
                d.streak >= 2 -> "The reign is over: ${poss(the(lg, t))} run of ${runWords(d.streak)} ends" + (by?.let { " against ${the(lg, it)}" } ?: "")
                else -> "The reign is over: ${the(lg, t)} ${if (lg.pro) "go" else "goes"} out in the $round" + (by?.let { " to ${the(lg, it)}" } ?: "")
            }
            val body = if (lg.pro) "${The(lg, t)} lose in the ${round.lowercase().replace("finals", "Finals")}" + (by?.let { " to ${the(lg, it)}" } ?: "") + ", a year after winning it all."
            else "${poss(The(lg, t))} title defense ends in the $round" + (by?.let { ", beaten by ${it.school}" } ?: "") + "."
            lg.news += News(lg.season, lg.day, head.replace("the the", "the"), mine, -1, "defense", body, "Hoops Wire", t.id)
            if (mine) Alerts.add(lg, "The reign is over", head, "")
        }
        // They did it again.
        val won = if (lg.pro) ps?.champion == t.id else lg.ncaa?.champion == t.id
        if (won && lg.phase != Phase.REGULAR) once(REPEAT) {
            val run = streak(lg, t, lg.season).coerceAtLeast(d.streak + 1)
            val head = when (run) {
                2 -> "Back-to-back: ${the(lg, t)} repeat as champions"
                3 -> "Three-peat! ${The(lg, t)} win a third straight title"
                else -> "${The(lg, t)} win ${runWords(run)}"
            }
            val prior = lastRepeat(lg, lg.season)?.takeIf { it.first < lg.season }
            val body = "${The(lg, t)} ${if (lg.pro) "do" else "does"} what champions almost never do: win it again. " +
                (if (run == 2) prior?.let { (y, r) -> "They're the first repeat champions since ${the(lg, r)} in $y." } ?: "" else "That's ${runWords(run)}. The word is dynasty.")
            lg.news += News(lg.season, lg.day, head, mine, -1, "defense", body, "Hoops Wire", t.id)
        }
    }

    /** For the Today card: the user's team defending a title, and what it's chasing. */
    fun userDefending(lg: League): TitleRun? = lg.defense?.takeIf { it.season == lg.season && it.champ == lg.userTeamId }
}
