package com.nh.hoopscoach

import com.nh.hoopscoach.game.Awards
import com.nh.hoopscoach.game.Brand
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.Offseason
import com.nh.hoopscoach.game.Phase
import com.nh.hoopscoach.game.Rng
import com.nh.hoopscoach.game.Season
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Prestige, Legacy, Buzz and Pull over several seasons: the league shouldn't drift, and the rules should bite. */
class BrandTest {

    private fun tierMeans(lg: League): List<Double> = (0..2).map { tier ->
        lg.teams.filter { lg.conf(it.confId).tier == tier }.map { it.prestige }.average()
    }

    @Test
    fun brandOverSeasons() {
        Rng.r = Random(11)
        val lg = Offseason.newGame(5, "Test", "Coach")
        val start = tierMeans(lg)
        val startTop = lg.teams.sortedByDescending { it.prestige }.take(25).map { it.prestige }.average()
        println("start tiers=%s top25=%.1f".format(start.map { "%.1f".format(it) }, startTop))
        val seasons = (System.getProperty("brand.seasons") ?: "5").toInt()
        repeat(seasons) {
            var guard = 0
            while (lg.phase != Phase.WRAP_UP && guard++ < 120) Season.playDay(lg)
            val tiers = tierMeans(lg)
            val top25 = lg.teams.sortedByDescending { it.prestige }.take(25)
            val champ = lg.team(lg.history.last().champion)
            val buzz = lg.teams.sortedByDescending { it.buzz }.take(3)
            val pulls = Brand.pullTable(lg)
            println(
                "S${lg.season}: tiers=%s top25=%.1f max=%d ≥90:%d ≥80:%d | champ %s p%d L%.0f ceil %.0f buzz %.0f | hottest %s".format(
                    tiers.map { "%.1f".format(it) }, top25.map { it.prestige }.average(), top25.first().prestige,
                    lg.teams.count { it.prestige >= 90 }, lg.teams.count { it.prestige >= 80 },
                    champ.school, champ.prestige, champ.legacy, Brand.ceiling(champ), champ.buzz,
                    buzz.joinToString { "${it.school} %.0f (r%d)".format(it.buzz, Awards.tourneyRound(lg, it.id)) },
                ),
            )
            val byPull = lg.teams.sortedByDescending { pulls[it.id] }.take(5)
            println("   pull top: " + byPull.joinToString { "${it.abbr} ${pulls[it.id]} (p${it.prestige})" } +
                " | low tier pull avg %.1f".format(lg.teams.filter { lg.conf(it.confId).tier == 2 }.map { pulls[it.id] }.average()))
            val movers = lg.teams.sortedByDescending { it.prestige - it.prevPrestige }
            println("   up: " + movers.take(3).joinToString { "${it.abbr} ${it.prevPrestige}->${it.prestige} (${Awards.resultFor(lg, it.id)})" } +
                " | down: " + movers.takeLast(3).joinToString { "${it.abbr} ${it.prevPrestige}->${it.prestige} (${Awards.resultFor(lg, it.id)})" })
            Offseason.autoComplete(lg)
        }
        val end = tierMeans(lg)
        for (i in 0..2) assertTrue("tier $i drifted from %.1f to %.1f".format(start[i], end[i]), kotlin.math.abs(end[i] - start[i]) < 6)
        assertTrue("some programs stay elite", lg.teams.count { it.prestige >= 85 } >= 5)
        assertTrue("buzz is used", lg.teams.any { it.buzz > 20 })
    }
}
