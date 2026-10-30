package com.nh.hoopscoach.game

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Traditional rivalries. Conference rivals meet twice (the rematch closes conference play),
 * non-conference rivals get a fixed date every year. Rivalry games sell out, the home crowd is
 * worth [HOME_BOOST] extra Elo, and the result swings both programs' prestige.
 */
object Rivalries {
    /** Extra home-court edge, in Elo points, when the home crowd sees its rival. */
    const val HOME_BOOST = 25.0

    private class Def(val a: String, val b: String, val name: String = "", val neutral: Boolean = false)

    /** Most famous first: when a school has two conference rivals, the first one gets the home-and-home. */
    private val LIST = listOf(
        Def("Duke", "North Carolina", "Tobacco Road"),
        Def("Kentucky", "Louisville", "Battle of the Bluegrass"),
        Def("Kansas", "Missouri", "Border War"),
        Def("Kansas", "Kansas State", "Sunflower Showdown"),
        Def("Michigan", "Michigan State"),
        Def("Indiana", "Purdue"),
        Def("Syracuse", "Georgetown"),
        Def("Cincinnati", "Xavier", "Crosstown Shootout"),
        Def("Villanova", "Saint Joseph's", "Holy War"),
        Def("Missouri", "Illinois", "Braggin' Rights", neutral = true),
        Def("Kentucky", "Tennessee"),
        Def("Arizona", "Arizona State", "Duel in the Desert"),
        Def("UCLA", "USC", "Crosstown Rivalry"),
        Def("Gonzaga", "Saint Mary's"),
        Def("Alabama", "Auburn"),
        Def("Texas", "Texas A&M", "Lone Star Showdown"),
        Def("Oklahoma", "Oklahoma State", "Bedlam"),
        Def("BYU", "Utah", "Holy War"),
        Def("Pittsburgh", "West Virginia", "Backyard Brawl"),
        Def("Iowa", "Iowa State", "Cy-Hawk Series"),
        Def("Virginia", "Virginia Tech", "Commonwealth Clash"),
        Def("Georgia", "Georgia Tech", "Clean, Old-Fashioned Hate"),
        Def("Florida", "Florida State", "Sunshine Showdown"),
        Def("North Carolina", "NC State"),
        Def("Michigan", "Ohio State"),
        Def("Wisconsin", "Minnesota", "Border Battle"),
        Def("Marquette", "Wisconsin"),
        Def("Rutgers", "Seton Hall", "Garden State Hardwood Classic"),
        Def("Providence", "Rhode Island"),
        Def("Ole Miss", "Mississippi State"),
        Def("Clemson", "South Carolina"),
        Def("Stanford", "California"),
        Def("Oregon", "Oregon State"),
        Def("Washington", "Washington State"),
        Def("Colorado", "Colorado State"),
        Def("Nebraska", "Creighton"),
        Def("Richmond", "VCU", "Black and Blue Classic"),
        Def("Pittsburgh", "Duquesne", "City Game"),
        Def("Montana", "Montana State"),
        Def("Harvard", "Yale"),
        Def("Penn", "Princeton"),
        Def("Army", "Navy"),
        Def("North Dakota", "North Dakota State"),
        Def("South Dakota", "South Dakota State"),
        Def("Cal Poly", "UC Santa Barbara", "Blue-Green Rivalry"),
    )

    /** Schools named in the rivalry list, for tests. */
    val schools: Set<String> get() = LIST.flatMap { listOf(it.a, it.b) }.toSet()

    /** Resolves the list to this league's teams and sizes every arena. Safe to call any time. */
    fun ensure(lg: League) {
        for (t in lg.teams) if (t.arena <= 0) t.arena = arenaFor(t)
        if (lg.rivalsReady) return
        val byName = lg.teams.associateBy { it.school }
        for (d in LIST) {
            val x = byName[d.a] ?: continue
            val y = byName[d.b] ?: continue
            if (lg.rivalry(x.id, y.id) == null) lg.rivals += Rivalry(x.id, y.id, d.name, d.neutral)
        }
        lg.rivalsReady = true
    }

    /** Bigger programs play in bigger buildings, give or take 20%. */
    private fun arenaFor(t: Team): Int {
        val v = ((t.id * 7919L + 104729) % 41 - 20) / 100.0
        return ((3500 + t.prestige * 165) * (1 + v) / 50).roundToInt() * 50
    }

    /** Crowd for a game. Rivalry games always sell out. */
    fun attendance(lg: League, g: Game): Int {
        val home = lg.team(g.home)
        val away = lg.team(g.away)
        val rival = lg.rivalry(g) != null
        if (g.neutral) {
            val venue = when (g.type) {
                GameType.NCAA -> if (g.label.endsWith("Final Four") || g.label.endsWith("Championship")) 72_000 else 18_000
                GameType.NIT -> 9_100
                GameType.CONF_TOURNEY -> when (lg.conf(home.confId).tier) { 0 -> 19_000; 1 -> 11_000; else -> 5_500 }
                GameType.EVENT -> 8_500
                else -> maxOf(home.arena, away.arena) + 2_000
            }
            if (rival) return venue
            val draw = 0.55 + (home.prestige + away.prestige) * 0.002 + Rng.gauss() * 0.05
            return (venue * draw.coerceIn(0.35, 1.0)).roundToInt()
        }
        if (rival) return home.arena
        val fill = 0.4 + home.prestige * 0.0045 + (home.winPct - 0.5) * 0.3 +
            (if (away.ap > 0) 0.08 else 0.0) + (if (g.type == GameType.CONF) 0.04 else 0.0) + Rng.gauss() * 0.04
        return (home.arena * fill.coerceIn(0.3, 0.97)).roundToInt()
    }

    fun sellout(lg: League, g: Game) = g.att > 0 && lg.rivalry(g) != null

    /** Upsets swing prestige twice as hard. */
    private fun swingFor(w: Team, l: Team) = if (w.prestige + 5 < l.prestige) 2 else 1

    /** After a rivalry game: series record, prestige swing, locker-room mood, job security and the headline. */
    fun afterGame(lg: League, g: Game) {
        val r = lg.rivalry(g) ?: return
        val w = lg.team(g.winner())
        val l = lg.team(g.loser())
        if (w.id == r.a) r.aWins++ else r.bWins++
        val swing = swingFor(w, l)
        g.swing = swing
        val wb = w.prestige
        w.prestige = (w.prestige + swing).coerceAtMost(99)
        w.rivalryDelta += w.prestige - wb
        val lb = l.prestige
        l.prestige = (l.prestige - swing).coerceAtLeast(5)
        l.rivalryDelta += l.prestige - lb
        for (pid in w.roster) lg.player(pid).let { it.happiness = (it.happiness + 3).coerceAtMost(100) }
        for (pid in l.roster) lg.player(pid).let { it.happiness = (it.happiness - 2).coerceAtLeast(0) }
        val user = lg.userTeamId
        val mine = g.involves(user)
        if (mine) lg.userCoach.security = (lg.userCoach.security + if (w.id == user) 2 else -2).coerceIn(0, 100)
        if (mine || w.ap > 0 || l.ap > 0) lg.news += News(lg.season, lg.day, "${r.title}: ${headline(lg, g)}", mine, g.id)
    }

    /** Newspaper headline for a finished rivalry game. */
    fun headline(lg: League, g: Game): String {
        val w = lg.team(g.winner())
        val l = lg.team(g.loser())
        val margin = abs(g.hs - g.aws)
        val sweep = lg.games.any { it.id != g.id && it.played && it.day < g.day && it.involves(w.id) && it.involves(l.id) && it.winner() == w.id }
        val road = !g.neutral && g.away == w.id
        return when {
            g.ot > 0 -> "${w.school} outlasts ${l.school} in overtime"
            g.swing >= 2 -> "${w.school} stuns ${if (l.ap > 0) "No. ${l.ap} " else ""}${l.school}"
            margin >= 20 -> "${w.school} routs ${l.school}"
            sweep -> "${w.school} sweeps ${l.school}"
            margin <= 3 -> "${w.school} edges ${l.school} in a thriller"
            road -> "${w.school} silences the crowd at ${l.school}"
            else -> listOf("${w.school} beats ${l.school}", "${w.school} takes down ${l.school}", "${w.school} tops ${l.school}")[g.id % 3]
        }
    }

    /** The user's most recent game, if it was a rivalry game and nothing has been played since. */
    fun latestUserRivalry(lg: League): Game? {
        val last = lg.games.filter { it.played && it.involves(lg.userTeamId) }.maxByOrNull { it.day } ?: return null
        return last.takeIf { lg.rivalry(it) != null }
    }
}
