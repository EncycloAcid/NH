package com.nh.hoopscoach

import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.LeagueGen
import com.nh.hoopscoach.game.Offseason
import com.nh.hoopscoach.game.Phase
import com.nh.hoopscoach.game.Pos
import com.nh.hoopscoach.game.ProCareer
import com.nh.hoopscoach.game.ProLeague
import com.nh.hoopscoach.game.Rng
import com.nh.hoopscoach.game.Season
import com.nh.hoopscoach.game.Sim
import com.nh.hoopscoach.game.StatLine
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Keeps box scores close to real basketball: team averages and league leaders, pro and college. */
class StatsTest {
    private fun check(what: String, v: Double, range: ClosedFloatingPointRange<Double>) =
        assertTrue("$what ${"%.2f".format(v)} not in $range", v in range)

    /** Prints a season's numbers and returns the ones the test checks. */
    private fun report(label: String, lg: League): Map<String, Double> {
        val out = mutableMapOf<String, Double>()
        val tot = StatLine()
        lg.teams.forEach { tot.add(it.totals) }
        val g = lg.teams.sumOf { it.totals.gp }.toDouble()
        println("$label team per game: pts %.1f fga %.1f fg%% %.3f 3pm %.1f 3pa %.1f 3p%% %.3f ftm %.1f fta %.1f orb %.1f drb %.1f reb %.1f ast %.1f stl %.1f blk %.1f tov %.1f pf %.1f".format(
            tot.pts / g, tot.fga / g, tot.fgPct, tot.tpm / g, tot.tpa / g, tot.tpPct, tot.ftm / g, tot.fta / g, tot.orb / g, tot.drb / g, tot.reb / g, tot.ast / g, tot.stl / g, tot.blk / g, tot.tov / g, tot.pf / g))
        out["pts"] = tot.pts / g; out["fg"] = tot.fgPct; out["3pa"] = tot.tpa / g; out["ast"] = tot.ast / g
        out["blk"] = tot.blk / g; out["orb"] = tot.orb / g; out["tov"] = tot.tov / g
        out["fta"] = tot.fta / g; out["ft"] = tot.ftm.toDouble() / tot.fta.coerceAtLeast(1); out["pf"] = tot.pf / g

        val qual = lg.players.filter { it.stats.gp >= (if (lg.pro) 30 else 15) && it.teamId >= 0 }
        for ((n, f) in listOf<Pair<String, (StatLine) -> Double>>("ppg" to { it.ppg }, "rpg" to { it.rpg }, "apg" to { it.apg }, "spg" to { it.spg }, "bpg" to { it.bpg }, "3pm" to { it.per(it.tpm) })) {
            val top = qual.sortedByDescending { f(it.stats) }.take(5)
            out[n] = f(top.first().stats)
            println("  $n leaders: " + top.joinToString { "${it.last} (${it.pos}, ${it.ovr}) %.1f".format(f(it.stats)) })
        }
        for (pos in Pos.entries) {
            val starters = qual.filter { it.pos == pos && it.stats.mpg >= (if (lg.pro) 28.0 else 24.0) }
            if (starters.isEmpty()) continue
            val s = StatLine(); starters.forEach { s.add(it.stats) }
            if (pos == Pos.PG) out["pgAst"] = s.apg
            println("  $pos starters (${starters.size}): %.1f pts %.1f reb %.1f ast %.1f stl %.1f blk %.1f 3pm in %.1f min".format(s.ppg, s.rpg, s.apg, s.spg, s.bpg, s.per(s.tpm), s.mpg))
        }
        return out
    }

    @Test
    fun boxScoresLookReal() {
        val college = ProTest().toDraft(21)
        val pro = ProLeague.create(college)
        while (pro.phase == Phase.OFFSEASON) ProCareer.next(pro)
        var guard = 0
        while (pro.phase == Phase.REGULAR && guard++ < 200) Sim.playDay(pro)
        val p = report("PRO", pro)
        check("pro pts", p["pts"]!!, 108.0..122.0); check("pro fg%", p["fg"]!!, 0.44..0.50); check("pro 3pa", p["3pa"]!!, 30.0..44.0)
        check("pro ast", p["ast"]!!, 22.0..30.0); check("pro blk", p["blk"]!!, 3.8..6.5); check("pro orb", p["orb"]!!, 9.0..14.0)
        check("pro tov", p["tov"]!!, 12.0..17.0); check("pro top ppg", p["ppg"]!!, 26.0..38.0); check("pro top apg", p["apg"]!!, 8.0..13.5)
        check("pro top bpg", p["bpg"]!!, 1.8..4.0); check("pro top 3pm", p["3pm"]!!, 3.5..6.5); check("pro PG apg", p["pgAst"]!!, 5.5..10.0)
        check("pro fta", p["fta"]!!, 19.0..25.0); check("pro ft%", p["ft"]!!, 0.75..0.81); check("pro pf", p["pf"]!!, 16.5..21.0)

        Rng.r = Random(5)
        val c = Offseason.claimTeam(LeagueGen.newLeague(), 0, "Test", "Coach")
        guard = 0
        while (c.phase == Phase.REGULAR && guard++ < 200) Season.playDay(c)
        val k = report("COLLEGE", c)
        check("college pts", k["pts"]!!, 66.0..80.0); check("college ast", k["ast"]!!, 11.0..17.0); check("college blk", k["blk"]!!, 2.5..4.5)
        check("college top ppg", k["ppg"]!!, 18.0..28.0); check("college top apg", k["apg"]!!, 6.0..10.5)
        check("college fta", k["fta"]!!, 15.5..22.0); check("college ft%", k["ft"]!!, 0.68..0.75); check("college pf", k["pf"]!!, 15.0..19.5)
    }
}
