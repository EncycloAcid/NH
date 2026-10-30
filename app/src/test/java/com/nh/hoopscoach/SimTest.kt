package com.nh.hoopscoach

import com.nh.hoopscoach.game.GameType
import com.nh.hoopscoach.game.Offseason
import com.nh.hoopscoach.game.Phase
import com.nh.hoopscoach.game.Recruiting
import com.nh.hoopscoach.game.Rng
import com.nh.hoopscoach.game.SaveIO
import com.nh.hoopscoach.game.Season
import com.nh.hoopscoach.game.StatLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SimTest {

    @Test
    fun simulatesSeveralSeasons() {
        Rng.r = Random(7)
        val lg = Offseason.newGame(5, "Test", "Coach")
        repeat(3) { s ->
            val t0 = System.currentTimeMillis()
            var guard = 0
            while (lg.phase != Phase.WRAP_UP && guard++ < 100) Season.playDay(lg)
            val ms = System.currentTimeMillis() - t0
            assertEquals(Phase.WRAP_UP, lg.phase)

            val all = StatLine()
            var games = 0
            for (t in lg.teams) { all.add(t.totals); games += t.gp }
            val reg = lg.games.filter { it.played && it.type != GameType.NCAA }
            val margins = reg.map { kotlin.math.abs(it.hs - it.aws) }.average()
            val homeWin = lg.games.filter { it.played && !it.neutral }.let { g -> g.count { it.hs > it.aws }.toDouble() / g.size }
            val confMargin = lg.games.filter { it.played && it.type == GameType.CONF }.map { kotlin.math.abs(it.hs - it.aws) }.average()
            val ncMargin = lg.games.filter { it.played && it.type == GameType.NONCONF }.map { kotlin.math.abs(it.hs - it.aws) }.average()
            val act = lg.players.filter { it.teamId >= 0 }
            val avgAttr = (0 until 10).map { i -> act.map { it.attrs[i] }.average().toInt() }
            println("  conf margin=%.1f nc margin=%.1f attrs=%s".format(confMargin, ncMargin, avgAttr))
            println(
                "Season ${lg.season}: ${ms}ms, games=${lg.games.count { it.played }}, " +
                    "ppg=%.1f fg=%.3f 3p=%.3f 3pa/fga=%.2f ft=%.3f fta/fga=%.2f to=%.1f orb=%.1f drb=%.1f ast=%.1f stl=%.1f blk=%.1f margin=%.1f homeW=%.2f".format(
                        all.pts.toDouble() / games, all.fgPct, all.tpPct, all.tpa.toDouble() / all.fga, all.ftPct,
                        all.fta.toDouble() / all.fga, all.tov.toDouble() / games, all.orb.toDouble() / games,
                        all.drb.toDouble() / games, all.ast.toDouble() / games, all.stl.toDouble() / games, all.blk.toDouble() / games, margins, homeWin,
                    ),
            )
            val champ = lg.team(lg.history.last().champion)
            println("  Champion: ${champ.name} (${champ.record}, prestige ${champ.prestige}); user ${lg.user.name} ${lg.user.record} → ${lg.wrapUp?.result}")
            val top = lg.players.filter { it.teamId >= 0 }.sortedByDescending { it.stats.ppg }.take(3)
            top.forEach { println("  ${it.name} ${it.pos} ovr ${it.ovr}: %.1f ppg %.1f rpg %.1f apg, mpg %.1f".format(it.stats.ppg, it.stats.rpg, it.stats.apg, it.stats.mpg)) }
            val best = lg.teams.sortedByDescending { it.w }.take(3)
            best.forEach { println("  ${it.school}: ${it.record} prestige ${it.prestige}") }
            val ovrs = lg.players.filter { it.teamId >= 0 }.map { it.ovr }
            println("  ovr avg=%.1f max=%d min=%d".format(ovrs.average(), ovrs.max(), ovrs.min()))

            val bytes = SaveIO.encode(lg)
            val back = SaveIO.decode(bytes)
            assertEquals(lg.players.size, back.players.size)
            println("  save size: ${bytes.size / 1024} KB gz")

            Offseason.begin(lg)
            assertEquals(Phase.OFFSEASON, lg.phase)
            val os = lg.offseason!!
            Offseason.advance(lg) // roster -> draft
            println("  offseason: grads=${os.graduates.size} entrants=${os.entrants.size} transfersOut=${os.transferredOut.size} portal=${lg.recruits.count { it.transfer }} offers=${os.offers.size} drafted=${os.draftedCount}")
            Offseason.autoComplete(lg)
            println("  signed=${os.signed.size} growth=${os.growth.take(3).map { "${it.before}->${it.after}" }}")
            assertEquals(Phase.REGULAR, lg.phase)
            for (t in lg.teams) assertEquals("roster size for ${t.school}", Recruiting.MAX_ROSTER, t.roster.size)
            assertTrue(lg.recruits.isNotEmpty())
            println("  new season ${lg.season}, games scheduled=${lg.games.size}, user exp wins=%.1f".format(lg.user.expWins))
            assertTrue(s >= 0)
        }
    }
}
