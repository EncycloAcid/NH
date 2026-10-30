package com.nh.hoopscoach

import com.nh.hoopscoach.game.Awards
import com.nh.hoopscoach.game.Bracketology
import com.nh.hoopscoach.game.GameType
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.Offseason
import com.nh.hoopscoach.game.Schedule
import com.nh.hoopscoach.game.Phase
import com.nh.hoopscoach.game.Recruiting
import com.nh.hoopscoach.game.Rivalries
import com.nh.hoopscoach.game.Tryouts
import com.nh.hoopscoach.game.Rng
import com.nh.hoopscoach.game.SaveIO
import com.nh.hoopscoach.game.Season
import com.nh.hoopscoach.game.StatLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SimTest {

    /** A hidden gem who tries out as a freshman should usually be a star by his junior or senior year. */
    @Test
    fun gemsBecomeStars() {
        Rng.r = Random(3)
        val lg = com.nh.hoopscoach.game.LeagueGen.newLeague()
        val finals = mutableListOf<Int>()
        val plain = mutableListOf<Int>()
        repeat(300) { k ->
            val gem = k % 2 == 0
            val p = Tryouts.unrated(lg, com.nh.hoopscoach.game.Pos.SG, 1, 46, gem)
            repeat(3) {
                // A season of practice and games, then a summer.
                com.nh.hoopscoach.game.Ratings.addXp(p, (1300 * com.nh.hoopscoach.game.Ratings.devRate(p, 70)).toInt())
                Tryouts.summer(p)
                com.nh.hoopscoach.game.Ratings.offseasonGrowth(p, 70)
                p.year++
            }
            com.nh.hoopscoach.game.Ratings.addXp(p, (1300 * com.nh.hoopscoach.game.Ratings.devRate(p, 70)).toInt())
            (if (gem) finals else plain) += p.ovr
        }
        finals.sort(); plain.sort()
        val stars = finals.count { it >= Tryouts.STAR }
        println("Gems as seniors: median ${finals[finals.size / 2]}, stars ${stars}/${finals.size}, range ${finals.first()}-${finals.last()}; ordinary walk-ons: median ${plain[plain.size / 2]}, max ${plain.last()}")
        assertTrue("most gems become stars", stars >= finals.size * 0.6)
        assertTrue("ordinary walk-ons stay role players", plain.last() < Tryouts.STAR)
    }

    @Test
    fun simulatesSeveralSeasons() {
        Rng.r = Random(7)
        val lg = Offseason.newGame(5, "Test", "Coach")
        assertEquals("abbreviations must be unique", lg.teams.size, lg.teams.map { it.abbr }.toSet().size)
        println("Teams: ${lg.teams.size} in ${lg.confs.size} conferences")
        repeat(3) { s ->
            val t0 = System.currentTimeMillis()
            var guard = 0
            val pv = checkPreview(lg)
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
            checkRivalries(lg)
            checkFans(lg)
            checkMoments(lg)
            previewAccuracy(lg, pv)
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
            assertEquals("former players survive a save", lg.alumni.size, back.alumni.size)
            println("  save size: ${bytes.size / 1024} KB gz")

            Offseason.begin(lg)
            assertEquals(Phase.OFFSEASON, lg.phase)
            val os = lg.offseason!!
            Offseason.advance(lg) // roster -> draft
            println("  offseason: grads=${os.graduates.size} entrants=${os.entrants.size} transfersOut=${os.transferredOut.size} portal=${lg.recruits.count { it.transfer }} offers=${os.offers.size} drafted=${os.draftedCount}")
            checkTryouts(lg, os)
            println("  signed=${os.signed.size} growth=${os.growth.take(3).map { "${it.before}->${it.after}" }}")
            assertEquals(Phase.REGULAR, lg.phase)
            for (t in lg.teams) {
                assertEquals("scholarships for ${t.school}", Recruiting.MAX_ROSTER, Tryouts.scholarships(lg, t))
                assertTrue("walk-ons for ${t.school}", Tryouts.walkOns(lg, t) <= Tryouts.WALK_ON_SPOTS)
                if (t.id != lg.userTeamId) assertEquals("AI roster for ${t.school}", Recruiting.MAX_ROSTER, t.roster.size)
            }
            checkLegends(lg)
            checkCareers(lg)
            val gems = lg.players.filter { it.tryout > 0 && it.teamId >= 0 }
            println("  former tryout walk-ons in the league: ${gems.size}, broke out ${gems.count { it.story >= 1 }}, stars ${gems.count { it.story >= 2 }}")
            assertTrue(lg.recruits.isNotEmpty())
            println("  new season ${lg.season}, games scheduled=${lg.games.size}, user exp wins=%.1f".format(lg.user.expWins))
            assertTrue(s >= 0)
        }
    }

    /** The preseason magazine is there every season and its rankings hold together. */
    private fun checkPreview(lg: League): com.nh.hoopscoach.game.Preview {
        val pv = lg.preview!!
        assertEquals(lg.season, pv.season)
        assertEquals(lg.teams.size, pv.proj.size)
        assertEquals((1..lg.teams.size).toSet(), pv.proj.values.map { it.nat }.toSet())
        for (c in lg.confs) assertEquals("conference ranks for ${c.abbr}", (1..c.teamIds.size).toSet(), c.teamIds.map { pv.proj.getValue(it).confRank }.toSet())
        assertEquals(10, pv.allAmerica.size)
        assertEquals(5, pv.freshmen.size)
        assertEquals(4, pv.finalFour.size)
        assertEquals(lg.confs.size, pv.confPoy.size)
        assertTrue(pv.proj.values.all { it.w + it.l in 25..34 })
        return pv
    }

    private fun previewAccuracy(lg: League, pv: com.nh.hoopscoach.game.Preview) {
        val f4 = lg.ncaa!!.rounds[4].toSet()
        var err = 0.0
        var n = 0
        for (c in lg.confs) {
            val st = Season.standings(lg, c.id)
            st.forEachIndexed { i, t -> err += kotlin.math.abs(i + 1 - pv.proj.getValue(t.id).confRank); n++ }
        }
        val pick = lg.team(pv.finalFour[0])
        val top25 = pv.proj.filterValues { it.nat <= 25 }.keys
        val made = top25.count { lg.ncaa!!.hasTeam(it) }
        println("  preview: champion pick ${pick.school} -> ${com.nh.hoopscoach.game.Awards.resultFor(lg, pick.id)}; Final Four picks in F4 ${pv.finalFour.count { it in f4 }}/4; top-25 teams in NCAA field $made/25; avg conference finish error %.1f places".format(err / n))
    }

    /** March moments: upsets, buzzer-beaters, Cinderellas and the champion make the highlight reel. */
    private fun checkMoments(lg: League) {
        val ms = com.nh.hoopscoach.game.Moments.season(lg, lg.season)
        val byKind = ms.groupingBy { it.kind }.eachCount()
        val st = com.nh.hoopscoach.game.Moments.stats
        assertEquals("one title moment", 1, byKind[com.nh.hoopscoach.game.MomentKind.TITLE])
        val ncaa = lg.ncaa!!
        val upsetGames = lg.games.filter { it.played && it.type == GameType.NCAA }.count { g ->
            val ws = ncaa.seeds[g.winner()] ?: 0; val ls = ncaa.seeds[g.loser()] ?: 0; ws - ls >= 5 && !g.label.endsWith("First Four")
        }
        println("  moments: $byKind; NCAA upsets(5+ lines)=$upsetGames; all-time buzzer-beaters ${st[1]}/${st[0]} games (%.2f%%), forced OT ${st[2]}".format(st[1] * 100.0 / st[0]))
        ms.sortedByDescending { it.weight }.take(3).forEach { println("    [${it.kind}] ${it.headline} — ${it.detail}") }
        assertTrue(ms.all { it.season == lg.season })
    }

    /** Winning fills the arena and the NIL pool; losing empties both. */
    private fun checkFans(lg: League) {
        val byWins = lg.teams.sortedByDescending { it.winPct }
        val top = byWins.take(40)
        val bottom = byWins.takeLast(40)
        val fTop = top.map { it.fans }.average()
        val fBot = bottom.map { it.fans }.average()
        val fillTop = top.map { com.nh.hoopscoach.game.Fans.avgFill(it) }.average()
        val fillBot = bottom.map { com.nh.hoopscoach.game.Fans.avgFill(it) }.average()
        assertTrue("winners have happier fans", fTop > fBot + 15)
        assertTrue("winners draw bigger crowds", fillTop > fillBot + 0.1)
        val boosts = lg.teams.map { com.nh.hoopscoach.game.Finance.fanMoney(it) }
        println("  fans: top-40 by win%% mood %.0f fill %.0f%%, bottom-40 mood %.0f fill %.0f%%; sellouts %d; fan NIL %d..%dK".format(
            fTop, fillTop * 100, fBot, fillBot * 100, lg.teams.sumOf { it.sellouts }, boosts.min(), boosts.max()))
        val u = lg.user
        println("  user fans: ${"%.0f".format(u.fans)} ${com.nh.hoopscoach.game.Fans.mood(u.fans)}, avg crowd ${com.nh.hoopscoach.game.Fans.avgCrowd(u)}/${u.arena}, NIL ${com.nh.hoopscoach.game.Finance.fanMoney(u)}K")
    }

    /** Career pages: full season lines, highs, honors, and former players kept on record. */
    private fun checkCareers(lg: League) {
        val lastDraft = lg.draftHistory.filter { it.season == lg.season - 1 }
        assertEquals(60, lastDraft.size)
        assertTrue("every draft pick stays on record", lastDraft.all { d -> lg.alumni.any { it.id == d.pid } })
        val vets = lg.players.filter { it.teamId >= 0 && it.career.size >= 1 }
        assertTrue(vets.isNotEmpty())
        val line = vets.first().career.last()
        assertTrue("season lines carry the team record and result", line.w + line.l > 0 && line.result.isNotEmpty() && line.teamId >= 0)
        assertTrue("career highs tracked", vets.count { it.highs[0] > 0 } > vets.size * 0.8)
        val mine = com.nh.hoopscoach.game.Careers.formerPlayers(lg, lg.userTeamId)
        val star = lg.alumni.maxByOrNull { com.nh.hoopscoach.game.Careers.total(it).pts }!!
        val honors = com.nh.hoopscoach.game.Careers.honorsBySeason(star, lg.season).flatMap { it.honors.map { h -> h.first } }
        println("  alumni ${lg.alumni.size} (yours ${mine.size}); top: ${star.name} ${com.nh.hoopscoach.game.Careers.total(star).pts} pts, ${star.left}; honors ${honors.take(3)}; highs ${star.highs.toList()}")
    }

    /** Legends are honored when great players leave; retired numbers are never handed out again. */
    private fun checkLegends(lg: League) {
        val last = lg.season - 1
        val fresh = lg.teams.flatMap { t -> t.legends.filter { it.inducted == last }.map { t to it } }
        val retired = fresh.filter { it.second.retired }
        for (t in lg.teams) {
            val nums = com.nh.hoopscoach.game.Legacy.retiredNumbers(t)
            val newcomers = lg.rosterOf(t).filter { it.year == 1 || it.ovrHistory.size <= 1 }
            assertTrue("retired numbers stay retired at ${t.school}", newcomers.none { it.number in nums && t.legends.any { l -> l.retired && l.number == it.number && l.inducted < lg.season - 1 } })
        }
        val pipe = com.nh.hoopscoach.game.Pipeline.table(lg)
        val best = lg.teams.sortedByDescending { pipe[it.id] }.take(3)
        println("  legends this offseason: ${fresh.size} (retired ${retired.size}) e.g. ${fresh.maxByOrNull { it.second.score }?.let { (t, l) -> "${l.name} ${t.abbr} score ${l.score}${if (l.retired) " #${l.number} retired" else ""}: ${l.honors.take(2)}" }}")
        println("  pro pipeline: ${best.map { "${it.abbr} ${pipe[it.id]}pts +${(com.nh.hoopscoach.game.Pipeline.bonus(pipe[it.id], true) * 100).toInt()}%" }}")
    }

    /** Walk-on tryouts: unrated candidates, closer looks, keeping two, then the new season. */
    private fun checkTryouts(lg: League, os: com.nh.hoopscoach.game.OffseasonState) {
        var guard = 0
        while (os.step != com.nh.hoopscoach.game.OffStep.TRYOUTS && guard++ < 20) {
            if (os.step == com.nh.hoopscoach.game.OffStep.CAREER && os.fired) com.nh.hoopscoach.game.Coaching.acceptOffer(lg, os.offers.first().teamId)
            Offseason.advance(lg)
        }
        assertEquals(com.nh.hoopscoach.game.OffStep.TRYOUTS, os.step)
        assertEquals(Tryouts.CANDIDATES, os.tryouts.size)
        assertEquals(Tryouts.LOOKS, os.looks)
        val u = lg.user
        assertEquals("user roster not filled before tryouts", Tryouts.openScholarships(lg, u), Recruiting.MAX_ROSTER - Tryouts.scholarships(lg, u))
        assertTrue(Tryouts.look(lg, 0))
        assertTrue(os.tryouts[0].looked)
        assertTrue(Tryouts.look(lg, 1))
        assertTrue("only two looks", !Tryouts.look(lg, 2))
        if (Tryouts.capacity(lg) == 0) {
            val w = lg.rosterOf(u).first { it.walkOn }
            assertTrue(Tryouts.release(lg, w.id))
            assertTrue("release frees a spot", lg.playerOrNull(w.id) == null && Tryouts.capacity(lg) == 1)
        }
        val cap = Tryouts.capacity(lg)
        assertTrue(cap >= Tryouts.WALK_ON_SPOTS - Tryouts.walkOns(lg, u))
        val names = mutableListOf<String>()
        for (i in os.tryouts.indices) if (Tryouts.kept(os) < 2 && Tryouts.toggle(lg, i)) names += os.tryouts[i].p.name
        val walkOnsBefore = Tryouts.walkOns(lg, u)
        val season = lg.season
        Offseason.advance(lg)
        assertEquals(Phase.REGULAR, lg.phase)
        val made = lg.rosterOf(lg.user).filter { it.name in names && it.tryout == season + 1 }
        assertEquals("kept walk-ons make the team", names.size, made.size)
        assertTrue(made.all { it.nil == 0 })
        assertTrue(Tryouts.walkOns(lg, lg.user) >= minOf(Tryouts.WALK_ON_SPOTS, walkOnsBefore + names.size))
        println("  tryouts: kept ${made.map { "${it.name} ${it.pos} ${it.ovr}${if (it.walkOn) " walk-on" else " schol"}" }}, roster ${lg.user.roster.size}")
    }

    /** Rivalry games are scheduled, sell out, swing prestige and make headlines. */
    private fun checkRivalries(lg: League) {
        val names = lg.teams.map { it.school }.toSet()
        assertTrue("rivalry schools exist: ${Rivalries.schools - names}", names.containsAll(Rivalries.schools))
        assertEquals(45, lg.rivals.size)
        val regular = lg.games.filter { it.played && it.day <= Schedule.REG_DAYS }
        var twice = 0
        for (r in lg.rivals) {
            val meetings = regular.filter { it.involves(r.a) && it.involves(r.b) }
            assertTrue("${lg.team(r.a).school}-${lg.team(r.b).school} meet", meetings.isNotEmpty())
            val conf = meetings.filter { it.type == GameType.CONF }
            if (conf.size >= 2) {
                twice++
                assertTrue("home-and-home", conf.map { it.home }.toSet().size == 2)
            }
            for (g in meetings) {
                assertTrue("rivalry sells out", g.att == (if (g.neutral) g.att else lg.team(g.home).arena))
                assertTrue("prestige swing", g.swing in 1..2)
            }
        }
        val duke = lg.teams.first { it.school == "Duke" }.id
        val unc = lg.teams.first { it.school == "North Carolina" }.id
        val dukeUnc = regular.filter { it.involves(duke) && it.involves(unc) && it.type == GameType.CONF }
        assertEquals("Duke-UNC home-and-home", 2, dukeUnc.size)
        assertEquals("rematch closes conference play", Schedule.REG_DAYS, dukeUnc.maxOf { it.day })
        val braggin = lg.rivals.first { it.name == "Braggin' Rights" }
        assertTrue(regular.first { it.involves(braggin.a) && it.involves(braggin.b) }.neutral)
        val homeGames = lg.games.filter { it.played && !it.neutral }
        val rivalHome = homeGames.filter { lg.rivalry(it) != null }
        val normalFill = homeGames.filter { lg.rivalry(it) == null }.map { it.att.toDouble() / lg.team(it.home).arena }.average()
        if (lg.rivalsOf(lg.userTeamId).isNotEmpty()) {
            val sig = lg.wrapUp!!.signals.first { it.label == "Rivalry games" }
            println("  user rivalry signal: ${sig.detail} ${sig.value}")
        }
        val heads = lg.news.filter { it.rivalry && it.season == lg.season }
        assertTrue(heads.isNotEmpty())
        println("  rivalries: ${lg.rivals.size}, home-and-home $twice, rivalry home W%%=%.2f (n=%d), normal fill=%.2f, headlines=%d, e.g. \"%s\"".format(
            rivalHome.count { it.hs > it.aws }.toDouble() / rivalHome.size, rivalHome.size, normalFill, heads.size, heads.last().text))
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
