package com.nh.hoopscoach.game

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

@Serializable
class SummerLine(val pid: Int, val teamId: Int, val name: String, val pos: String, val ppg: Double, val rpg: Double, val apg: Double, val note: String = "")

@Serializable
class SummerRecap(val season: Int, val champion: Int, val mvp: Int, val mvpLine: String, val lines: List<SummerLine>)

/**
 * NBA Summer League, in July after the draft: every team's rookies and second-year players play a few
 * games. The standouts come out of it a little better (and with headlines), top picks who struggle get
 * questions, and one team and one MVP take home the title.
 */
object SummerLeague {
    private class Run(val p: Player, val t: Team, val ppg: Double, val rpg: Double, val apg: Double) { val value get() = ppg + rpg * 0.5 + apg * 0.7 }

    private fun f1(d: Double) = "%.1f".format(d)

    fun run(lg: League) {
        val ps = lg.proState ?: return
        if (ps.summerLeague?.season == lg.season) return
        val runs = mutableListOf<Run>()
        val strength = HashMap<Int, Double>()
        for (t in lg.teams) {
            val young = lg.rosterOf(t).filter { it.proYears <= 1 }.sortedByDescending { it.ovr }.take(8)
            val mine = young.mapIndexed { i, p ->
                val usage = listOf(6.0, 4.0, 2.5, 1.5, 1.0, 0.5, 0.0, 0.0)[i]
                val q = 0.7 + (p.ovr - 70) / 60.0
                val ppg = (4 + (p.ovr - 72) * 0.65 + (p.pot - p.ovr) * 0.2 + usage + Rng.gauss() * 2.6).coerceIn(1.5, 32.0)
                val rpg = ((when (p.pos) { Pos.C -> 7.0; Pos.PF -> 6.0; Pos.SF -> 4.5; Pos.SG -> 3.5; Pos.PG -> 3.0 }) * q + Rng.gauss()).coerceIn(0.8, 14.0)
                val apg = ((when (p.pos) { Pos.PG -> 5.0; Pos.SG -> 3.0; Pos.SF -> 2.2; Pos.PF -> 1.5; Pos.C -> 1.2 }) * q + Rng.gauss() * 0.8).coerceIn(0.3, 10.0)
                Run(p, t, ppg, rpg, apg)
            }
            runs += mine
            strength[t.id] = mine.sortedByDescending { it.value }.take(5).sumOf { it.value } + Rng.gauss() * 6
        }
        if (runs.isEmpty()) return
        val champ = lg.team(strength.entries.sortedByDescending { it.value }.take(8).maxBy { it.value + Rng.gauss() * 8 }.key)
        val best = runs.maxBy { it.value }
        val mvp = runs.filter { it.t.id == champ.id }.maxByOrNull { it.value }?.takeIf { it.value >= best.value * 0.8 } ?: best
        val ranked = runs.sortedByDescending { it.value }
        val standouts = ranked.take(5)
        // Confidence: the standouts come out a point or two better.
        for (s in standouts) {
            repeat(Rng.range(1, 2)) { Ratings.raise(s.p, Rng.int(0, s.p.attrs.size)) }
            ProLeague.rookieCap(lg, s.p)
        }
        val cut = ranked.size * 6 / 10
        fun pick(p: Player) = p.awards.firstOrNull { it.startsWith("${lg.season} Draft #") }?.substringAfter("#")?.toIntOrNull()
        val busts = ranked.drop(cut).filter { (pick(it.p) ?: 99) <= 10 }
        fun line(r: Run) = "${f1(r.ppg)} pts, ${f1(r.rpg)} reb, ${f1(r.apg)} ast"
        val keep = (runs.filter { it.t.id == lg.userTeamId } + standouts + busts + mvp).distinctBy { it.p.id }.map { r ->
            SummerLine(r.p.id, r.t.id, r.p.name, r.p.posLabel, r.ppg, r.rpg, r.apg, when {
                r.p.id == mvp.p.id -> "MVP"
                r in standouts -> "Standout"
                r in busts -> "Struggled"
                else -> ""
            })
        }
        ps.summerLeague = SummerRecap(lg.season, champ.id, mvp.p.id, line(mvp), keep)
        lg.news += News(lg.season, lg.day, "Summer League: the ${champ.name} win the title, and ${mvp.p.name} is MVP", champ.id == lg.userTeamId,
            -1, "", "${mvp.p.name} (${mvp.t.abbr}) averaged ${line(mvp)}.", "Hoops Wire", champ.id, mvp.p.id)
        lg.news += News(lg.season, lg.day, "Summer League standouts: " + standouts.joinToString(", ") { "${it.p.name} (${f1(it.ppg)} ppg)" }, standouts.any { it.t.id == lg.userTeamId },
            -1, "", standouts.joinToString(" ") { "${it.p.name}, ${it.t.abbr}: ${line(it)}." } + " Teams leave Las Vegas convinced they've found something.", "Hoops Wire")
        for (b in busts.take(2)) lg.news += News(lg.season, lg.day, "Questions for No. ${pick(b.p)} pick ${b.p.name} after a rough Summer League", b.t.id == lg.userTeamId,
            -1, "", "${b.p.name} averaged ${line(b)} in Las Vegas. It's July, his coaches say. The internet doesn't care.", "Hoops Wire", b.t.id, b.p.id)
        // The user's own player, if he's young enough to be there.
        val hero = lg.hero?.takeIf { lg.road != null }
        runs.firstOrNull { it.p.id == hero?.id }?.let { r ->
            Alerts.add(lg, "Summer League", "You averaged ${line(r)} in Las Vegas" + when (r) { mvp -> ". MVP."; in standouts -> ". One of the standouts: you come out of it a little better."; else -> "." }, "")
        }
    }
}
