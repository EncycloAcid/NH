package com.nh.hoopscoach.game

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The league's newsroom: real stories, not just one-liners. Every night it looks for the best
 * performance around the league (40-point games, triple-doubles, 20-20s), writes up streaks, and
 * recaps the user's games when he coaches. In the NBA it also runs weekly power rankings, an MVP
 * ladder when there's no player career to follow, a story for every finished playoff series, and a
 * full write-up of every trade: who got what, what they've done this season, and who won the deal.
 */
object NewsDesk {
    private val OUTLETS = listOf("Hoops Wire", "The Daily Dribble", "Courtside Report", "Baseline Sports", "The Rim", "Fast Break Network", "Above the Break")
    private val WRITERS = listOf("Dana Marsh", "Theo Banks", "Rae Whitfield", "Marcus Lyle", "Jules Ortega", "Priya Shah", "Cal Benton", "Nora Quinn")
    /** A byline: each writer has one outlet (two cover for Hoops Wire), so the names stay with their papers. */
    private fun outlet(): String {
        val i = Rng.int(0, WRITERS.size - 1)
        return "${WRITERS[i]} · ${OUTLETS[i % OUTLETS.size]}"
    }
    private val DAYS = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

    /** A player's night, noted when the game is committed. */
    private class Night(val pid: Int, val teamId: Int, val oppId: Int, val gameId: Int, val won: Boolean, val us: Int, val them: Int, val s: StatLine)

    private val tonight = mutableListOf<Night>()
    private var tonightKey = -1

    /** Called by the engine for every finished game: remembers each team's best line. */
    fun note(lg: League, g: Game, teams: Array<Team>, lines: Array<out Map<Int, StatLine>>, score: IntArray) {
        val key = lg.season * 1000 + lg.day
        synchronized(tonight) {
            if (key != tonightKey) { tonight.clear(); tonightKey = key }
            for (side in 0..1) {
                val best = lines[side].entries.maxByOrNull { (_, s) -> gameScore(s) } ?: continue
                tonight += Night(best.key, teams[side].id, teams[1 - side].id, g.id, score[side] > score[1 - side], score[side], score[1 - side], best.value.copy())
            }
        }
    }

    private fun gameScore(s: StatLine) = s.pts + s.reb * 1.1 + s.ast * 1.4 + (s.stl + s.blk) * 2.0 - s.tov * 0.8

    private fun tripleDouble(s: StatLine) = listOf(s.pts, s.reb, s.ast, s.stl, s.blk).count { it >= 10 } >= 3

    /** What makes a night news. */
    private fun notable(lg: League, s: StatLine): Boolean = if (lg.pro)
        s.pts >= 40 || tripleDouble(s) || (s.pts >= 20 && s.reb >= 20) || s.reb >= 22 || s.ast >= 17 || s.blk >= 7 || s.stl >= 7
    else s.pts >= 35 || tripleDouble(s) || (s.pts >= 20 && s.reb >= 18) || s.blk >= 8

    private fun line(s: StatLine) = buildString {
        append("${s.pts} points")
        if (s.reb >= 5) append(", ${s.reb} rebounds")
        if (s.ast >= 5) append(", ${s.ast} assists")
        if (s.stl >= 4) append(", ${s.stl} steals")
        if (s.blk >= 4) append(", ${s.blk} blocks")
    }

    private fun name(lg: League, t: Team) = if (lg.pro) t.name else t.school
    /** A team in a sentence: "the Boston Celtics", but "Kentucky". */
    private fun the(lg: League, t: Team) = if (lg.pro) "the ${t.name}" else t.school
    private fun The(lg: League, t: Team) = the(lg, t).replaceFirstChar { it.uppercase() }
    /** A dateline is the home city (or school): "BOSTON —", "KENTUCKY —". */
    private fun dateline(t: Team) = t.school.uppercase()
    /** "(9 rebounds, 5 assists)", leaving out the zeros. */
    private fun extras(reb: Int, ast: Int): String {
        val parts = listOfNotNull(if (reb > 0) "$reb ${if (reb == 1) "rebound" else "rebounds"}" else null, if (ast > 0) "$ast ${if (ast == 1) "assist" else "assists"}" else null)
        return if (parts.isEmpty()) "" else " (${parts.joinToString(", ")})"
    }
    private fun nick(lg: League, t: Team) = if (lg.pro) t.name.substringAfterLast(' ') else t.mascot

    // ---------- every night ----------

    fun endOfDay(lg: League) {
        val nights = synchronized(tonight) { if (tonightKey == lg.season * 1000 + lg.day) tonight.toList() else emptyList() }
        bigNight(lg, nights)
        streaks(lg)
        if (!lg.playerMode) recap(lg)
        if (lg.pro && lg.phase == Phase.REGULAR) {
            if (lg.day >= 14 && lg.day % 7 == 0) powerRankings(lg)
            if (lg.road == null && lg.day >= 21 && lg.day % 14 == 0) mvpLadder(lg)
        }
        if (lg.pro && lg.phase == Phase.PLAYOFFS) seriesStories(lg)
    }

    /** The best line around the league tonight, if it's a big one (the user's own player has his own coverage). */
    private fun bigNight(lg: League, nights: List<Night>) {
        val n = nights.filter { it.pid != lg.heroId && notable(lg, it.s) }.maxByOrNull { gameScore(it.s) } ?: return
        val p = lg.playerOrNull(n.pid) ?: return
        val t = lg.team(n.teamId)
        val opp = lg.team(n.oppId)
        val s = n.s
        val g = lg.games.firstOrNull { it.id == n.gameId }
        val home = g?.home == t.id
        val what = when {
            s.pts >= 50 -> "drops ${s.pts}"
            s.pts >= 40 -> "pours in ${s.pts}"
            tripleDouble(s) -> "posts a ${s.pts}-${s.reb}-${s.ast} triple-double"
            s.reb >= 20 -> "grabs ${s.reb} rebounds"
            s.blk >= 7 -> "blocks ${s.blk} shots"
            s.ast >= 15 -> "hands out ${s.ast} assists"
            else -> "goes for ${s.pts} and ${s.reb}"
        }
        val headline = "${p.last} $what as ${nick(lg, t)} ${if (n.won) "beat" else "fall to"} ${nick(lg, opp)} ${max(n.us, n.them)}-${minOf(n.us, n.them)}"
        val place = dateline(if (home) t else opp)
        val body = buildList {
            add("$place — ${p.name} had ${line(s)} on ${s.fgm}-of-${s.fga} shooting, and ${the(lg, t)} ${if (n.won) "beat" else "lost to"} ${the(lg, opp)} ${n.us}-${n.them} on ${DAYS[(lg.day + lg.season) % 7]} night.")
            add(when {
                s.pts >= 50 -> "It's the kind of night people remember: only a handful of players ever get to 50."
                s.pts >= 40 && !n.won -> "It wasn't enough. ${p.last} did everything he could, and the ${nick(lg, t)} still came up short."
                tripleDouble(s) -> "He filled every column, and the ${nick(lg, t)} were at their best with him running things."
                n.won -> Rng.pick(listOf("The ${nick(lg, t)} needed every bit of it.", "He took over when it mattered.", "It was a clinic from start to finish."))
                else -> "His team couldn't hold on."
            })
            add("He's averaging ${"%.1f".format(p.stats.ppg)} points, ${"%.1f".format(p.stats.rpg)} rebounds and ${"%.1f".format(p.stats.apg)} assists. The ${nick(lg, t)} are ${t.w}-${t.l}" +
                when { t.streak >= 3 -> ", winners of ${t.streak} straight."; t.streak <= -3 -> " and have lost ${-t.streak} in a row."; else -> "." })
            val coach = lg.coachOf(t)
            add(Rng.pick(listOf(
                "“That's what he does,” coach ${coach.last} said. “You keep giving him the ball.”",
                "“Special,” coach ${coach.last} said. “He made the right play all night.”",
                "“We needed every bit of it,” coach ${coach.last} said.",
            )))
        }.joinToString("\n\n")
        lg.news += News(lg.season, lg.day, headline, t.id == lg.userTeamId, n.gameId, "game", body, outlet(), t.id, p.id)
    }

    /**
     * Long winning and losing streaks make the news when they hit a round number: the user's team
     * always, and otherwise the best-known team on each kind of streak that day (one each), so a
     * night of low-major losing streaks doesn't fill the page.
     */
    private fun streaks(lg: League) {
        if (lg.phase != Phase.REGULAR) return
        val played = lg.games.filter { it.played && it.day == lg.day }.flatMap { listOf(it.home, it.away) }.toSet()
        val hits = lg.teams.filter { t ->
            if (t.id !in played) return@filter false
            val s = t.streak
            (s >= 7 && (s == 7 || s % 3 == 1)) || (s <= -8 && (s == -8 || s % 4 == 0) && (lg.pro || s <= -10 || t.id == lg.userTeamId))
        }
        val known = { t: Team -> if (lg.pro) t.elo else t.prestige * 20.0 + t.elo / 10 }
        val pick = hits.filter { it.id == lg.userTeamId } +
            listOfNotNull(hits.filter { it.id != lg.userTeamId && it.streak > 0 }.maxByOrNull(known)) +
            listOfNotNull(hits.filter { it.id != lg.userTeamId && it.streak < 0 }.maxByOrNull(known))
        for (t in pick) {
            val s = t.streak
            val win = s > 0
            // "The Knicks win", but "Gonzaga wins": school names are singular.
            val (w, l, has) = if (lg.pro) Triple("win", "lose", "have") else Triple("wins", "loses", "has")
            val the = if (lg.pro) "The " else ""
            val star = lg.rosterOf(t).filter { it.stats.gp > 0 }.maxByOrNull { it.stats.ppg }
            val headline = if (win) "${name(lg, t)} $w $s straight" else "${name(lg, t)} $l ${-s} in a row"
            val body = (if (win) "$the${name(lg, t)} $has won $s games in a row and ${if (lg.pro) "sit" else "sits"} at ${t.w}-${t.l}."
                else "$the${name(lg, t)} $has dropped ${-s} straight and ${if (lg.pro) "fall" else "falls"} to ${t.w}-${t.l}.") +
                (star?.let { "\n\n${it.name} leads ${if (lg.pro) "them" else "the ${t.mascot}"} at ${"%.1f".format(it.stats.ppg)} points a game." } ?: "") +
                (if (win) "\n\n“We're not thinking about the streak,” coach ${lg.coachOf(t).last} said. “Just the next one.”"
                else "\n\n“Nobody's feeling sorry for us,” coach ${lg.coachOf(t).last} said. “We have to find a way.”")
            lg.news += News(lg.season, lg.day, headline, t.id == lg.userTeamId, -1, "team", body, outlet(), t.id)
        }
    }

    /** When the user coaches: a story on every game his team plays. */
    private fun recap(lg: League) {
        val u = lg.user
        val g = lg.games.firstOrNull { it.played && it.day == lg.day && it.involves(u.id) } ?: return
        val opp = lg.team(g.opp(u.id))
        val us = g.scoreFor(u.id)
        val them = g.scoreAgainst(u.id)
        val won = us > them
        val box = g.box
        // Box score lines carry short names; stories use the full ones.
        fun full(x: PlayerLine) = PlayerLine(x.pid, lg.playerOrNull(x.pid)?.name ?: x.name, x.pos, x.starter, x.s)
        val mine = box?.let { if (g.home == u.id) it.home else it.away }.orEmpty().sortedByDescending { it.s.pts }.map(::full)
        val theirs = box?.let { if (g.home == u.id) it.away else it.home }.orEmpty().maxByOrNull { it.s.pts }?.let(::full)
        val top = mine.firstOrNull()
        val coach = lg.userCoach
        fun last(x: PlayerLine) = x.name.substringAfter(' ')
        val headline = when {
            top == null -> "${name(lg, u)} ${if (won) "beat" else "fall to"} ${name(lg, opp)} $us-$them"
            won && abs(us - them) >= 20 -> "${nick(lg, u)} rout ${nick(lg, opp)} $us-$them behind ${last(top)}'s ${top.s.pts}"
            won && abs(us - them) <= 3 -> "${nick(lg, u)} edge ${nick(lg, opp)} $us-$them"
            won -> "${last(top)} scores ${top.s.pts}, ${nick(lg, u)} top ${nick(lg, opp)} $us-$them"
            else -> "${nick(lg, opp)} beat ${nick(lg, u)} $them-$us" + (theirs?.let { " as ${last(it)} scores ${it.s.pts}" } ?: "")
        }
        val body = buildList {
            add("${dateline(if (g.home == u.id) u else opp)} — ${The(lg, u)} ${if (won) "beat" else "lost to"} ${the(lg, opp)} $us-$them" +
                (top?.let { ", with ${it.name} scoring ${it.s.pts}${extras(it.s.reb, it.s.ast)}" } ?: "") + ".")
            mine.drop(1).take(2).filter { it.s.pts >= 10 }.takeIf { it.isNotEmpty() }?.let { o -> add(o.joinToString(" and ") { "${it.name} added ${it.s.pts}" } + ".") }
            theirs?.let { add("${it.name} led ${the(lg, opp)} with ${it.s.pts}.") }
            add("The ${nick(lg, u)} are ${u.w}-${u.l}" + when { u.streak >= 3 -> ", winners of ${u.streak} straight."; u.streak <= -3 -> " and have lost ${-u.streak} in a row."; else -> "." })
            add(if (won) Rng.pick(listOf("“I liked our energy,” coach ${coach.last} said. “We defended and shared it.”", "“Good win. Now we get ready for the next one,” coach ${coach.last} said."))
                else Rng.pick(listOf("“We'll learn from it,” coach ${coach.last} said. “That one's on me.”", "“Not good enough,” coach ${coach.last} said. “We have to be sharper.”")))
        }.joinToString("\n\n")
        lg.news += News(lg.season, lg.day, headline, true, g.id, "recap", body, outlet(), u.id, top?.pid ?: -1)
    }

    // ---------- the NBA's columns ----------

    /** Every week: the league's top 10, with how far each team moved. */
    private fun powerRankings(lg: League) {
        val ps = lg.proState ?: return
        val order = lg.teams.sortedWith(compareByDescending<Team> { it.winPct * 100 + (it.totals.pts - it.opp.pts).toDouble() / max(1, it.w + it.l) * 0.8 }.thenByDescending { it.elo })
        val last = ps.powerLast.toList()
        val top = order.take(10)
        val lines = top.mapIndexed { i, t ->
            val before = last.indexOf(t.id).let { if (it < 0) -1 else it + 1 }
            val move = when { before < 0 -> "new"; before > i + 1 -> "up ${before - i - 1}"; before < i + 1 -> "down ${i + 1 - before}"; else -> "—" }
            val star = lg.rosterOf(t).filter { it.stats.gp > 0 }.maxByOrNull { it.stats.ppg }
            "${i + 1}. ${t.name} (${t.w}-${t.l}, $move)" + (star?.let { " · ${it.name} ${"%.1f".format(it.stats.ppg)} ppg" } ?: "")
        }
        val u = lg.user
        val mine = order.indexOf(u) + 1
        ps.powerLast.clear()
        ps.powerLast += order.map { it.id }
        val headline = "Power rankings: the ${top.first().name} are No. 1" + if (mine in 1..10) "; ${u.name} at No. $mine" else ""
        val body = "Week ${lg.day / 7} of the season.\n\n" + lines.joinToString("\n") + "\n\n" +
            (if (mine > 10) "The ${u.name} are No. $mine." else "") +
            (order.lastOrNull()?.let { " At the bottom: the ${it.name} (${it.w}-${it.l})." } ?: "")
        lg.news += News(lg.season, lg.day, headline, mine in 1..10, -1, "column", body.trim(), outlet(), top.first().id)
    }

    /** Every two weeks when no player career is being followed: the MVP race. */
    private fun mvpLadder(lg: League) {
        val ladder = Awards.ladder(lg).take(5)
        if (ladder.size < 5) return
        val lines = ladder.mapIndexed { i, (p, t) -> "${i + 1}. ${p.name}, ${t.name} (${Awards.line(p)})" }
        val leader = ladder.first().first
        val body = "Where the MVP race stands after ${lg.day} days.\n\n" + lines.joinToString("\n") +
            "\n\n${leader.last} has the lead, but there's a long way to go."
        lg.news += News(lg.season, lg.day, "MVP ladder: ${leader.name} holds No. 1", ladder.any { it.second.id == lg.userTeamId }, -1, "column", body, outlet(), ladder.first().second.id, leader.id)
    }

    /** A playoff series that ended today. */
    private fun seriesStories(lg: League) {
        val ps = lg.proState ?: return
        for (s in ps.series) {
            if (!s.done || s.bestOf == 1) continue
            val last = s.gameIds.lastOrNull()?.let { lg.game(it) } ?: continue
            if (!last.played || last.day != lg.day) continue
            val w = lg.team(s.winner)
            val l = lg.team(s.loser)
            val games = s.hiWins + s.loWins
            val round = ProSeason.roundName(s)
            // The series' best player, from its box scores.
            val totals = HashMap<Int, Int>()
            for (id in s.gameIds) lg.game(id).box?.let { b -> for (x in if (lg.game(id).home == w.id) b.home else b.away) totals[x.pid] = (totals[x.pid] ?: 0) + x.s.pts }
            val star = totals.maxByOrNull { it.value }?.let { (pid, pts) -> lg.playerOrNull(pid)?.let { it to pts } }
            val headline = if (s.round == 4) "The ${w.name} are champions" else "${w.name} ${if (games == s.bestOf / 2 + 1) "sweep" else "beat"} ${l.name} in ${games} to win the ${round}"
            val body = buildList {
                add("The ${w.name} beat the ${l.name} ${max(s.hiWins, s.loWins)}-${minOf(s.hiWins, s.loWins)} in the ${round}" + if (s.round == 4) " and won the title." else ".")
                star?.let { (p, pts) -> add("${p.name} led the way with ${"%.1f".format(pts.toDouble() / games)} points a game in the series.") }
                add(if (s.round == 4) "It's the end of a long road: ${w.w}-${w.l} in the regular season, and four series won." else "Next for the ${w.name}: the ${if (s.round == 3) "Finals" else ProLeague.ROUND_NAMES.getOrElse(s.round) { "next round" }}.")
            }.joinToString("\n\n")
            lg.news += News(lg.season, lg.day, headline, s.involves(lg.userTeamId), last.id, "series", body, outlet(), w.id, star?.first?.id ?: -1)
        }
    }

    // ---------- trades ----------

    private fun grade(r: Double) = when { r >= 1.3 -> "A"; r >= 1.12 -> "A-"; r >= 1.0 -> "B+"; r >= 0.9 -> "B"; r >= 0.8 -> "B-"; r >= 0.65 -> "C"; else -> "D" }

    /** A full write-up of a trade: what moved, what those players have done, why, and grades. */
    fun trade(lg: League, t: Trade, a: Team, b: Team, fromA: List<Player>, fromB: List<Player>, picksA: List<String>, picksB: List<String>) {
        val all = fromA + fromB
        val star = all.maxByOrNull { Trades.value(it) }
        val big = star != null && Trades.tier(star) in listOf("Franchise", "All-Star")
        val headline = when {
            star != null && big -> "Blockbuster: ${star.name} traded to the ${if (star in fromA) b.name else a.name}"
            star != null -> "${a.name} send ${fromA.firstOrNull()?.name ?: picksA.firstOrNull() ?: "a deal"} to the ${b.name}"
            else -> "${a.name} and ${b.name} make a trade"
        }
        fun about(p: Player): String {
            val s = p.stats
            val numbers = if (s.gp > 0) "${"%.1f".format(s.ppg)} points, ${"%.1f".format(s.rpg)} rebounds and ${"%.1f".format(s.apg)} assists this season" else "a ${Ratings.shownPro(p.ovr)} on our board"
            return "${p.name} (${p.posLabel}, ${p.age}): $numbers. ${Road.dollars(Payroll.salaryOf(lg, p))} a season" + (if (p.contractYears > 1) " for ${p.contractYears} more years." else ", expiring.")
        }
        fun side(team: Team, gets: List<Player>, picks: List<String>) = "The ${team.name} get " +
            (gets.map { it.name } + picks).joinToString(", ").ifEmpty { "nothing" } + "."
        val va = fromB.sumOf { Trades.valueFor(lg, a, it) } + 0.0
        val vb = fromA.sumOf { Trades.valueFor(lg, b, it) } + 0.0
        val gradeA = grade(va / (fromA.sumOf { Trades.valueFor(lg, a, it) }).coerceAtLeast(0.5))
        val gradeB = grade(vb / (fromB.sumOf { Trades.valueFor(lg, b, it) }).coerceAtLeast(0.5))
        // Why each side did it: what it got against what it gave, and where it stands.
        fun why(team: Team, gets: List<Player>, gives: List<Player>): String {
            val rec = if (team.w + team.l > 0) " (${team.w}-${team.l})" else ""
            val getsStar = star != null && star in gets
            val younger = gets.isNotEmpty() && gives.isNotEmpty() && gets.map { it.age }.average() < gives.map { it.age }.average() - 2
            return when {
                getsStar -> "The ${team.name}$rec land the biggest name in the deal."
                star != null && star in gives && younger -> "The ${team.name}$rec cash in ${star.last} and get younger."
                star != null && star in gives -> "The ${team.name}$rec move on from ${star.last}."
                younger -> "The ${team.name}$rec get younger."
                else -> "The ${team.name}$rec add depth."
            }
        }
        val body = buildList {
            add(side(a, fromB, picksB) + " " + side(b, fromA, picksA))
            all.sortedByDescending { Trades.value(it) }.take(3).forEach { add(about(it)) }
            add(why(a, fromB, fromA) + " " + why(b, fromA, fromB))
            add("Our grades: ${a.name} $gradeA, ${b.name} $gradeB.")
        }.joinToString("\n\n")
        lg.news += News(lg.season, lg.day, headline, a.id == lg.userTeamId || b.id == lg.userTeamId, -1, "trade", body, outlet(),
            if (star != null && star in fromA) b.id else a.id, star?.id ?: -1)
        @Suppress("UNUSED_VARIABLE") val unused = t
    }

    @Suppress("unused") private fun unused(x: Double) = x.roundToInt()
}
