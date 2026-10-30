package com.nh.hoopscoach

import com.nh.hoopscoach.game.Awards
import com.nh.hoopscoach.game.Bracketology
import com.nh.hoopscoach.game.GameType
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.Offseason
import com.nh.hoopscoach.game.Schedule
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
        assertEquals("abbreviations must be unique", lg.teams.size, lg.teams.map { it.abbr }.toSet().size)
        println("Teams: ${lg.teams.size} in ${lg.confs.size} conferences")
        repeat(3) { s ->
            val t0 = System.currentTimeMillis()
            var guard = 0
            var projected = false
            while (lg.phase != Phase.WRAP_UP && guard++ < 100) {
                Season.playDay(lg)
                if (!projected && lg.phase == Phase.CONF_TOURNEY) { projected = true; checkProjection(lg) }
            }
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
            checkPostseason(lg)
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
            assertEquals("First Four survives a save", 4, back.ncaa!!.playIns.size)
            assertEquals("NIT survives a save", lg.nit!!.champion, back.nit!!.champion)
            assertEquals("Selection Sunday survives a save", lg.bubble!!.groups.map { it.ids }, back.bubble!!.groups.map { it.ids })
            assertEquals(lg.wrapUp!!.pathRounds, back.wrapUp!!.pathRounds)
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

    /** Bubble Watch during conference tournament week. */
    private fun checkProjection(lg: League) {
        val t0 = System.nanoTime()
        val b = Bracketology.board(lg)!!
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertTrue(!b.final)
        val size = b.groups.associate { it.key to it.ids.size }
        assertEquals(8, size["bubble"])
        assertEquals(4, size["ffo"])
        assertEquals(4, size["nfo"])
        assertEquals("projected field", 68, b.seeds.size)
        val u = lg.userTeamId
        println("  Bubble Watch (${ms}ms): $size; user ${Bracketology.status(b, u)}, proj seed ${b.seeds[u]}")
    }

    /** 68-team field with a First Four, a 32-team NIT, and a Bubble Watch that matches the selection. */
    private fun checkPostseason(lg: League) {
        val ncaa = lg.ncaa!!
        val nit = lg.nit!!
        val bubble = lg.bubble!!
        val autos = lg.confBrackets.map { it.champion }.toSet()
        assertEquals("NCAA field", 68, ncaa.seeds.size)
        assertEquals("Round of 64 slots", 64, ncaa.rounds[0].size)
        assertTrue("every Round of 64 slot filled", ncaa.rounds[0].all { it >= 0 })
        assertEquals("First Four games", 4, ncaa.playIns.size)
        val elevens = ncaa.playIns.filter { it.seed == 11 }
        val sixteens = ncaa.playIns.filter { it.seed == 16 }
        assertEquals(2, elevens.size)
        assertEquals(2, sixteens.size)
        assertTrue("11-seed play-ins are at-large teams", elevens.all { it.a !in autos && it.b !in autos })
        assertTrue("16-seed play-ins are automatic qualifiers", sixteens.all { it.a in autos && it.b in autos })
        for (pi in ncaa.playIns) {
            val g = lg.game(pi.gameId)
            assertTrue(g.played && g.involves(pi.a) && g.involves(pi.b))
            assertEquals("First Four winner takes the slot", g.winner(), ncaa.rounds[0][pi.slot])
        }
        assertTrue("all conference champions are in", autos.all { it in ncaa.seeds })
        assertEquals("seed lines", (1..16).toSet(), ncaa.seeds.values.toSet())
        for (s in 1..16) {
            val n = ncaa.seeds.values.count { it == s }
            assertEquals("teams on the $s line", if (s == 11 || s == 16) 6 else 4, n)
        }
        assertEquals("NIT field", 32, nit.seeds.size)
        assertTrue("NIT teams missed the NCAA", nit.seeds.keys.none { it in ncaa.seeds })
        assertTrue(nit.champion >= 0)
        val lastIn = bubble.groups.first { it.key == "bubble" }.ids.takeLast(4).toSet()
        assertEquals("Bubble Watch last four in = 11-seed First Four", elevens.flatMap { listOf(it.a, it.b) }.toSet(), lastIn)
        val ffo = bubble.groups.first { it.key == "ffo" }.ids
        assertTrue("first four out are NIT top seeds", ffo.all { (nit.seeds[it] ?: 9) <= 2 })
        val ffDay = lg.games.filter { it.day == Schedule.NCAA_START }
        assertEquals("First Four + NIT first round", 4 + 16, ffDay.size)
        assertEquals("NIT Champion", Awards.resultFor(lg, nit.champion))
        assertTrue(lg.team(nit.champion).history.last().result == "NIT Champion")
        val ffLosers = ncaa.playIns.map { lg.game(it.gameId).loser() }
        assertTrue(ffLosers.all { Awards.resultFor(lg, it) == "First Four" })
        println("  First Four: " + ncaa.playIns.joinToString(" | ") { "${it.seed}: ${lg.team(it.a).abbr} v ${lg.team(it.b).abbr}" })
        println("  First four out: ${ffo.map { lg.team(it).abbr }}  NIT champ: ${lg.team(nit.champion).school} → prestige ${lg.team(nit.champion).prevPrestige}→${lg.team(nit.champion).prestige}")
        val atLargeByConf = ncaa.seeds.keys.filter { it !in autos }.groupingBy { lg.conf(lg.team(it).confId).abbr }.eachCount()
        println("  at-large bids: $atLargeByConf")
    }
}
