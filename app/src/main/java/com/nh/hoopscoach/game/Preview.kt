package com.nh.hoopscoach.game

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/** A team's preseason projection: national rank, predicted conference finish and projected records. */
@Serializable
class Proj(val nat: Int, val confRank: Int, val w: Int, val l: Int, val cw: Int, val cl: Int)

/** The preseason magazine: projections for every team, the players to watch and the experts' picks. */
@Serializable
class Preview(
    val season: Int,
    val proj: Map<Int, Proj>,
    val allAmerica: List<Int>,
    val freshmen: List<Int>,
    val transfers: List<Int>,
    val breakouts: List<Int>,
    /** Preseason Player of the Year for each conference (conference id to player id). */
    val confPoy: Map<Int, Int>,
    val finalFour: List<Int>,
    val sleeper: Int,
)

object Previews {

    /** Built once per season, when the schedule is set. */
    fun build(lg: League): Preview {
        val n = lg.teams.size
        val games = IntArray(n)
        val confGames = IntArray(n)
        val confWins = DoubleArray(n)
        for (g in lg.games) {
            if (g.type != GameType.NONCONF && g.type != GameType.CONF && g.type != GameType.EVENT) continue
            games[g.home]++
            games[g.away]++
            if (g.type == GameType.CONF) {
                val p = Rankings.winProbFor(lg, g, g.home)
                confWins[g.home] += p
                confWins[g.away] += 1 - p
                confGames[g.home]++
                confGames[g.away]++
            }
        }
        for (ev in lg.events) for (id in ev.teams) games[id] += 2
        val byElo = lg.teams.sortedByDescending { it.elo }
        val nat = HashMap<Int, Int>()
        byElo.forEachIndexed { i, t -> nat[t.id] = i + 1 }
        val confRank = HashMap<Int, Int>()
        for (c in lg.confs) {
            c.teamIds.map { lg.team(it) }.sortedWith(compareByDescending<Team> { confWins[it.id] }.thenByDescending { it.elo })
                .forEachIndexed { i, t -> confRank[t.id] = i + 1 }
        }
        val proj = lg.teams.associate { t ->
            val w = t.expWins.roundToInt().coerceIn(0, games[t.id])
            val cw = confWins[t.id].roundToInt().coerceIn(0, confGames[t.id])
            t.id to Proj(nat.getValue(t.id), confRank[t.id] ?: 0, w, games[t.id] - w, cw, confGames[t.id] - cw)
        }

        val active = lg.players.filter { it.teamId >= 0 }
        fun value(p: Player) = p.ovr + (p.career.lastOrNull()?.s?.ppg ?: 0.0) * 0.25
        val allAmerica = active.sortedByDescending { value(it) }.take(10).map { it.id }
        val freshmen = active.filter { it.year == 1 && it.recruitRank > 0 }.sortedByDescending { it.ovr }.take(5).map { it.id }
        val transfers = active.filter { p -> p.career.isNotEmpty() && p.career.last().teamId >= 0 && p.career.last().teamId != p.teamId }
            .sortedByDescending { it.ovr }.take(5).map { it.id }
        val breakouts = active.filter { it.year in 2..3 && it.pot - it.ovr >= 8 && it.ovr in 68..82 && it.id !in allAmerica }
            .sortedByDescending { it.pot * 2 + it.ovr }.take(5).map { it.id }
        val confPoy = HashMap<Int, Int>()
        for (c in lg.confs) {
            c.teamIds.flatMap { lg.team(it).roster }.map { lg.player(it) }.maxByOrNull { value(it) }?.let { confPoy[c.id] = it.id }
        }
        val finalFour = byElo.take(4).map { it.id }
        val sleeper = byElo.drop(25).firstOrNull { lg.conf(it.confId).tier >= 1 }?.id ?: byElo[25].id
        return Preview(lg.season, proj, allAmerica, freshmen, transfers, breakouts, confPoy, finalFour, sleeper)
    }

    /** This season's issue, built now if the save predates the magazine. */
    fun current(lg: League): Preview {
        val p = lg.preview
        if (p != null && p.season == lg.season) return p
        return build(lg).also { lg.preview = it }
    }

    fun star(lg: League, t: Team): Player? = lg.rosterOf(t).maxByOrNull { it.ovr }

    /** Best freshman or transfer on the roster, other than the headliner. */
    fun newcomer(lg: League, t: Team): Player? {
        val star = star(lg, t)
        return lg.rosterOf(t).filter { isNewcomer(it) && it.id != star?.id }.maxByOrNull { it.ovr }
    }

    /** A freshman, or a transfer whose last season was somewhere else. */
    fun isNewcomer(p: Player) = p.year == 1 || (p.career.isNotEmpty() && p.career.last().teamId >= 0 && p.career.last().teamId != p.teamId)

    private fun lastSeason(result: String) = when (result) {
        "National Champion" -> "won the national title"
        "Runner-Up" -> "reached the title game"
        "Final Four" -> "made the Final Four"
        "Elite Eight" -> "reached the Elite Eight"
        "Sweet 16" -> "made the Sweet 16"
        "Round of 32" -> "won a game in the NCAA Tournament"
        "Round of 64", "First Four" -> "lost in its first NCAA Tournament game"
        "NIT Champion" -> "won the NIT"
        else -> if (result.startsWith("NIT")) "went to the NIT" else "missed the NCAA Tournament"
    }

    /** A short write-up: how last season went and who to watch. */
    fun blurb(lg: League, t: Team): String {
        val last = t.history.lastOrNull()
        val parts = mutableListOf<String>()
        if (last != null) parts += "Went ${last.w}-${last.l} and ${lastSeason(last.result)} last season."
        star(lg, t)?.let { s -> parts += "${s.name} (${s.pos}, ${s.cls}, ${s.ovr}) is the headliner." }
        newcomer(lg, t)?.let { nc -> parts += "${nc.name} is the newcomer to watch." }
        return parts.joinToString(" ")
    }

    /** What the program should expect from its preseason ranking. */
    fun expectation(nat: Int) = when {
        nat <= 4 -> "National title contender"
        nat <= 12 -> "Final Four darkhorse"
        nat <= 25 -> "Second-weekend team"
        nat <= 45 -> "NCAA Tournament team"
        nat <= 70 -> "On the bubble"
        else -> "Building for the future"
    }
}
