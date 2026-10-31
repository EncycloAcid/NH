package com.nh.hoopscoach.game

import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The NBA's honors beyond the MVP vote.
 *
 * During the season: Player of the Week, and Player, Rookie and Coach of the Month (by conference;
 * league-wide before 2001-02). On awards night: Clutch Player, Coach and Executive of the Year, the
 * Hustle Award, the Sportsmanship, Teammate of the Year, Citizenship and Social Justice awards, the
 * statistical titles, and the All-NBA, All-Defensive and All-Rookie teams. In the playoffs: the
 * conference finals MVPs and the Finals MVP (from the series itself). At the Cup: the
 * All-Tournament Team. Each era hands out what the league did then.
 */
object NbaAwards {
    /** A week of game days: about three and a half games a team, like a real NBA week. */
    const val WEEK = 4
    val MONTHS = listOf("October-November", "December", "January", "February", "March-April")
    /** Where each month ends, as a share of the regular season (from a real calendar). */
    private val MONTH_ENDS = listOf(0.23, 0.41, 0.60, 0.74, 1.0)

    // ---------- which honors each era has ----------

    /** Weekly and monthly honors by conference from 2001-02; one for the whole league before. */
    fun byConference(era: Era) = era.year == 0 || era.year >= 2003
    /** Clutch Player of the Year (2022-23), the Hustle Award (2016-17), Social Justice Champion (2020-21), conference finals MVPs (2022). */
    fun clutch(era: Era) = era == Era.TODAY
    fun hustle(era: Era) = era == Era.TODAY
    fun socialJustice(era: Era) = era == Era.TODAY
    fun confFinalsMvp(era: Era) = era == Era.TODAY
    /** Teammate of the Year (2012-13) and the Sportsmanship Award (1995-96). */
    fun teammate(era: Era) = era.year == 0 || era.year >= 2013
    fun sportsmanship(era: Era) = era.year == 0 || era.year >= 1996
    /** All-NBA and All-Defensive teams went positionless in 2023-24; before, each had two guards, two forwards and a center. */
    fun positionless(era: Era) = era == Era.TODAY
    /** A third All-NBA team and a second All-Rookie team came in 1988-89. */
    fun allNbaTeams(era: Era) = if (era.year in 1..1988) 2 else 3
    fun allRookieTeams(era: Era) = if (era.year in 1..1988) 1 else 2

    private val TROPHIES_TODAY = mapOf(
        "MVP" to "Michael Jordan Trophy", "Rookie of the Year" to "Wilt Chamberlain Trophy",
        "Defensive Player of the Year" to "Hakeem Olajuwon Trophy", "Sixth Man of the Year" to "John Havlicek Trophy",
        "Most Improved Player" to "George Mikan Trophy", "Clutch Player of the Year" to "Jerry West Trophy",
        "Coach of the Year" to "Red Auerbach Trophy", "Finals MVP" to "Bill Russell Trophy",
        "Eastern Conference Finals MVP" to "Larry Bird Trophy", "Western Conference Finals MVP" to "Magic Johnson Trophy",
        "All-Star MVP" to "Kobe Bryant Trophy", "Sportsmanship Award" to "Joe Dumars Trophy",
        "Teammate of the Year" to "Twyman-Stokes Trophy", "Social Justice Champion" to "Kareem Abdul-Jabbar Trophy",
        "Citizenship Award" to "J. Walter Kennedy Citizenship Award",
    )

    /** The trophy's name in this era, or "" (most were renamed for legends in 2022-23). */
    fun trophy(award: String, era: Era): String = when {
        era == Era.TODAY -> TROPHIES_TODAY[award]
        award == "MVP" -> "Maurice Podoloff Trophy"
        award == "Rookie of the Year" -> "Eddie Gottlieb Trophy"
        award == "Citizenship Award" -> "J. Walter Kennedy Citizenship Award"
        award == "Coach of the Year" && era.year >= 1996 -> "Red Auerbach Trophy"
        award == "Finals MVP" && era.year >= 2013 -> "Bill Russell Trophy"
        award == "Teammate of the Year" -> "Twyman-Stokes Trophy"
        else -> null
    } ?: ""

    // ---------- recording ----------

    /** A season award: on his record, in the season's awards and in the honors list. */
    fun give(lg: League, award: String, p: Player, t: Team, line: String = Awards.line(p)) {
        lg.seasonAwards += AwardWinner(award, p.id, p.name, t.id, line)
        p.awards += "${lg.season} $award"
        note(lg, award, p, t.id, line)
    }

    /** Adds an honor to the list (his record is kept by whoever gave it). */
    fun note(lg: League, award: String, p: Player, teamId: Int, line: String = Awards.line(p), period: String = "", conf: Int = -1) {
        lg.proState?.honors?.add(Honor(lg.season, lg.day, award, p.id, p.name, teamId, line, period, conf))
    }

    /** An honor for a coach or a front office. */
    private fun staff(lg: League, award: String, name: String, t: Team, line: String, period: String = "", conf: Int = -1) {
        lg.proState?.honors?.add(Honor(lg.season, lg.day, award, -1, name, t.id, line, period, conf))
        if (t.id == lg.userTeamId) lg.news += News(lg.season, lg.day, "$name of the ${t.name}: $award" + if (period.isNotEmpty()) " ($period)" else "", true)
    }

    /** This season's honors (or [season]'s). */
    fun of(lg: League, season: Int = lg.season): List<Honor> = lg.proState?.honors?.filter { it.season == season } ?: emptyList()

    /** The general manager credited with a team's moves; the same name for as long as the save runs. */
    fun executive(lg: League, t: Team): String {
        val r = Random(t.id * 7919L + Eras.of(lg).ordinal * 31L)
        return "${Names.first[r.nextInt(Names.first.size)]} ${Names.last[r.nextInt(Names.last.size)]}"
    }

    // ---------- the season ----------

    /** Before a season: expected wins from each roster, and a fresh week and month. */
    fun preseason(lg: League) {
        val ps = lg.proState ?: return
        ps.weekStart.clear(); ps.monthStart.clear(); ps.weekTeams.clear(); ps.monthTeams.clear()
        ps.weekDay = 0; ps.week = 0; ps.month = 0
        ps.allStarStarters.clear()
        val games = ProLeague.seasonGames(lg).toDouble()
        val elos = lg.teams.map { it.elo }
        val mean = elos.average()
        val sd = sqrt(elos.sumOf { (it - mean) * (it - mean) } / elos.size).coerceAtLeast(1.0)
        // Real win totals spread about 12 wins either side of .500.
        for (t in lg.teams) t.expWins = (games / 2 + (t.elo - mean) / sd * 12 * games / 82).coerceIn(10.0, games - 10)
        // Keep weekly and monthly honors for three seasons (his own for good); season awards stay.
        ps.honors.removeAll { it.period.isNotEmpty() && it.season < lg.season - 3 && it.pid != lg.heroId }
    }

    /** Each night of the regular season: a Player of the Week every four game days, and the monthly honors. */
    fun endOfDay(lg: League) {
        val ps = lg.proState ?: return
        if (lg.phase != Phase.REGULAR) return
        val last = ProLeague.lastDay(lg)
        val days = lg.day - ps.weekDay
        // The last stretch joins the final week rather than making a one-night week.
        if ((days >= WEEK && last - lg.day >= 2) || (lg.day >= last && days >= 2)) week(lg)
        val ends = MONTH_ENDS.map { (last * it).roundToInt() }
        while (ps.month < MONTHS.size && lg.day >= ends[ps.month]) month(lg)
    }

    private fun confs(lg: League) = if (byConference(Eras.of(lg))) listOf(0, 1) else listOf(-1)

    private fun confName(conf: Int) = when (conf) { 0 -> "Eastern Conference"; 1 -> "Western Conference"; else -> "" }

    /** A week's or month's work: production, shooting, and winning. */
    private fun periodScore(s: StatLine, rec: IntArray): Double {
        if (s.gp == 0) return -99.0
        val games = (rec[0] + rec[1]).coerceAtLeast(1)
        return s.ppg + 1.1 * s.rpg + 1.4 * s.apg + 1.8 * (s.spg + s.bpg) - s.per(s.tov) +
            (s.fgPct - 0.47) * s.per(s.fga) + rec[0].toDouble() / games * 10
    }

    /** Voters spread the honor around: each win this season counts against a player, and they see the week a little differently. */
    private fun voters(lg: League, award: String, pid: Int, each: Double): Double =
        -each * (lg.proState?.honors?.count { it.season == lg.season && it.award == award && it.pid == pid } ?: 0) + Rng.gauss() * 1.5

    /** A team's wins, losses, points for and against since [start]. */
    private fun record(t: Team, start: Map<Int, IntArray>): IntArray {
        val now = intArrayOf(t.w, t.l, t.totals.pts, t.opp.pts)
        val then = start[t.id] ?: return now
        return IntArray(4) { now[it] - then[it] }
    }

    private fun snapshot(lg: League, stats: MutableMap<Int, StatLine>, teams: MutableMap<Int, IntArray>) {
        stats.clear(); teams.clear()
        for (t in lg.teams) {
            teams[t.id] = intArrayOf(t.w, t.l, t.totals.pts, t.opp.pts)
            for (p in lg.rosterOf(t)) stats[p.id] = p.stats.copy()
        }
    }

    private fun periodLine(s: StatLine, rec: IntArray) =
        "%.1f ppg · %.1f rpg · %.1f apg · ${rec[0]}-${rec[1]}".format(s.ppg, s.rpg, s.apg)

    private fun week(lg: League) {
        val ps = lg.proState!!
        ps.week++
        val label = "Week ${ps.week}"
        for (conf in confs(lg)) {
            val best = lg.teams.filter { conf < 0 || it.confId == conf }.flatMap { t ->
                val rec = record(t, ps.weekTeams)
                lg.rosterOf(t).map { p -> Triple(p, t, p.stats.since(ps.weekStart[p.id])) }.filter { it.third.gp >= 2 }.map { it to rec }
            }.map { it to periodScore(it.first.third, it.second) + voters(lg, "Player of the Week", it.first.first.id, 3.0) }.maxByOrNull { it.second }?.first ?: continue
            val (p, t, s) = best.first
            honor(lg, "Player of the Week", p, t, periodLine(s, best.second), label, conf, big = false)
        }
        snapshot(lg, ps.weekStart, ps.weekTeams)
        ps.weekDay = lg.day
    }

    private fun month(lg: League) {
        val ps = lg.proState!!
        val label = MONTHS[ps.month]
        for (conf in confs(lg)) {
            val teams = lg.teams.filter { conf < 0 || it.confId == conf }
            val rows = teams.flatMap { t ->
                val rec = record(t, ps.monthTeams)
                lg.rosterOf(t).map { p -> Triple(p, t, p.stats.since(ps.monthStart[p.id])) }
                    .filter { it.third.gp >= 4 && it.third.gp * 2 >= rec[0] + rec[1] }.map { it to rec }
            }
            rows.map { it to periodScore(it.first.third, it.second) + voters(lg, "Player of the Month", it.first.first.id, 2.5) }.maxByOrNull { it.second }?.first?.let { (x, rec) ->
                honor(lg, "Player of the Month", x.first, x.second, periodLine(x.third, rec), label, conf, big = true)
            }
            rows.filter { it.first.first.proYears == 0 }.maxByOrNull { (x, rec) -> periodScore(x.third, rec) }?.let { (x, rec) ->
                honor(lg, "Rookie of the Month", x.first, x.second, periodLine(x.third, rec), label, conf, big = false)
            }
            teams.map { it to record(it, ps.monthTeams) }.filter { it.second[0] + it.second[1] >= 4 }
                .maxByOrNull { (_, r) -> r[0].toDouble() / (r[0] + r[1]) * 100 + (r[2] - r[3]) / (r[0] + r[1]).toDouble() * 0.5 }
                ?.let { (t, r) -> staff(lg, "Coach of the Month", lg.coachOf(t).name, t, "${r[0]}-${r[1]} · ${if (r[2] >= r[3]) "+" else ""}${((r[2] - r[3]) / (r[0] + r[1]).toDouble()).roundToInt()} a game", label, conf) }
        }
        snapshot(lg, ps.monthStart, ps.monthTeams)
        ps.month++
    }

    /** A weekly or monthly honor: on his record, in the list, and for the user's player a headline, a post and new followers. */
    private fun honor(lg: League, award: String, p: Player, t: Team, line: String, period: String, conf: Int, big: Boolean) {
        p.awards += "${lg.season} $award"
        note(lg, award, p, t.id, line, period, conf)
        if (p.id != lg.heroId) return
        val r = lg.road ?: return
        val gain = ((if (big) 45_000 else 15_000) + r.followers * (if (big) 0.02 else 0.008) * Road.damp(r.followers)).roundToInt()
        r.followers += gain
        val where = confName(conf).let { if (it.isEmpty()) "NBA" else it }
        lg.news += News(lg.season, lg.day, "${p.name} named $where $award ($period)", true)
        Media.post(lg, "NBA", "NBA", "media", "${p.name} is the $where $award for $period: $line.", Media.likes(lg, if (big) 0.05 else 0.025), verified = true)
    }

    // ---------- awards night ----------

    private fun group(p: Pos) = when (p) { Pos.PG, Pos.SG -> 0; Pos.SF, Pos.PF -> 1; Pos.C -> 2 }

    /** [count] teams of five, best first: any five today, or two guards, two forwards and a center (a player fits either of his positions). */
    fun teams(ranked: List<Pair<Player, Team>>, count: Int, positions: Boolean): List<List<Pair<Player, Team>>> {
        val left = ranked.toMutableList()
        val out = mutableListOf<List<Pair<Player, Team>>>()
        repeat(count) {
            val five = mutableListOf<Pair<Player, Team>>()
            if (positions) {
                val need = intArrayOf(2, 2, 1)
                for (pt in left) {
                    if (five.size == 5) break
                    val g = listOfNotNull(pt.first.pos, pt.first.pos2).map(::group).firstOrNull { need[it] > 0 } ?: continue
                    need[g]--
                    five += pt
                }
            }
            // Today's teams, or a position nobody could fill: the best left.
            for (pt in left) { if (five.size == 5) break; if (pt !in five) five += pt }
            left.removeAll(five)
            if (five.isNotEmpty()) out += five
        }
        return out
    }

    private val ORD = listOf("1st", "2nd", "3rd")

    /** A bonus for the user's player on the character awards: humble answers to the press count. */
    private fun character(lg: League, p: Player): Double =
        if (p.id == lg.heroId) (-(lg.road?.persona ?: 0)).coerceIn(-4, 8) * 0.5 else 0.0

    private fun per36(v: Int, s: StatLine) = if (s.sec <= 0) 0.0 else v * 36.0 / (s.sec / 60.0)

    /**
     * The rest of awards night, after the MVP, Rookie, Defensive Player, Sixth Man and Most Improved:
     * the All-NBA, All-Defensive and All-Rookie teams, Clutch Player, Coach and Executive of the Year,
     * the Hustle Award, the character awards and the statistical titles.
     */
    fun awardsNight(lg: League, ladder: List<Pair<Player, Team>>, mvp: Pair<Player, Team>, defense: List<Pair<Player, Team>>, rookies: List<Pair<Player, Team>>) {
        val era = Eras.of(lg)
        val pos = !positionless(era)
        teams(listOf(mvp) + ladder.filter { it !== mvp }, allNbaTeams(era), pos).forEachIndexed { i, five ->
            five.forEach { (p, t) -> give(lg, "All-NBA ${ORD[i]}", p, t) }
        }
        teams(defense, 2, pos).forEachIndexed { i, five -> five.forEach { (p, t) -> give(lg, "All-Defensive ${ORD[i]}", p, t, defLine(p)) } }
        teams(rookies, allRookieTeams(era), false).forEachIndexed { i, five -> five.forEach { (p, t) -> give(lg, "All-Rookie ${ORD[i]}", p, t) } }

        if (clutch(era)) ladder.take(60).filter { it.first.stats.clutch > 0 }
            .maxByOrNull { (p, t) -> p.stats.clutch + t.winPct * 40 + Awards.score(p, t) * 0.5 }
            ?.let { (p, t) -> give(lg, "Clutch Player of the Year", p, t, "${p.stats.clutch} clutch points · ${t.record}") }

        // Coach of the Year: the most wins beyond what the roster promised. Executive: the biggest jump from last season.
        val games = ProLeague.seasonGames(lg).toDouble()
        val ps = lg.proState
        val noProjection = lg.teams.all { it.expWins <= 0.0 }
        fun expected(t: Team) = if (noProjection) games / 2 else t.expWins
        lg.teams.filter { it.gp > 0 }.maxByOrNull { t -> (t.w - expected(t) * t.gp / games) + t.winPct * 10 }?.let { t ->
            staff(lg, "Coach of the Year", lg.coachOf(t).name, t, "${t.record} · picked for ${expected(t).roundToInt()} wins")
        }
        lg.teams.filter { it.gp > 0 }.maxByOrNull { t -> (t.w - (ps?.lastWins?.get(t.id)?.toDouble() ?: expected(t))) + t.winPct * 8 }?.let { t ->
            val before = ps?.lastWins?.get(t.id)
            staff(lg, "Executive of the Year", executive(lg, t), t, "${t.record}" + (before?.let { " · up from $it wins" } ?: ""))
        }

        if (hustle(era)) ladder.filter { it.first.stats.mpg >= 20 }
            .maxByOrNull { (p, _) -> val s = p.stats; per36(s.stl, s) * 1.6 + per36(s.blk, s) * 1.3 + per36(s.orb, s) }
            ?.let { (p, t) -> val s = p.stats; give(lg, "Hustle Award", p, t, "%.1f steals · %.1f blocks · %.1f offensive rebounds".format(s.spg, s.bpg, s.per(s.orb))) }

        // The character awards: votes from players and the league, not the box score.
        if (sportsmanship(era)) ladder.filter { it.first.age >= 24 }.take(80)
            .maxByOrNull { (p, _) -> Rng.d() * 4 - per36(p.stats.pf, p.stats) * 0.8 + (p.age - 24) * 0.12 + character(lg, p) }
            ?.let { (p, t) -> give(lg, "Sportsmanship Award", p, t) }
        if (teammate(era)) ladder.filter { it.first.age >= 29 }
            .maxByOrNull { (p, t) -> Rng.d() * 4 + t.winPct * 4 + (p.age - 29) * 0.3 + p.stats.apg * 0.2 + character(lg, p) }
            ?.let { (p, t) -> give(lg, "Teammate of the Year", p, t) }
        ladder.maxByOrNull { (p, _) -> Rng.d() * 5 + p.age * 0.08 + character(lg, p) + if (p.id == lg.heroId && (lg.road?.followers ?: 0) > 1_000_000) 1.0 else 0.0 }
            ?.let { (p, t) -> give(lg, "Citizenship Award", p, t) }
        if (socialJustice(era)) ladder.filter { it.first.age >= 26 }
            .maxByOrNull { (p, _) -> Rng.d() * 5 + p.ovr * 0.03 + character(lg, p) * 0.5 }
            ?.let { (p, t) -> give(lg, "Social Justice Champion", p, t) }

        // Statistical titles: per game, for players in 70% of the games (58 of 82).
        val need = ceil(games * 0.7).toInt()
        val qualified = lg.teams.flatMap { t -> lg.rosterOf(t).filter { it.stats.gp >= need }.map { it to t } }
        for ((award, f) in listOf<Pair<String, (StatLine) -> Double>>(
            "Scoring title" to { it.ppg }, "Rebounding title" to { it.rpg }, "Assists title" to { it.apg },
            "Steals title" to { it.spg }, "Blocks title" to { it.bpg },
        )) {
            qualified.maxByOrNull { f(it.first.stats) }?.let { (p, t) ->
                give(lg, award, p, t, "%.1f a game".format(f(p.stats)) + " · " + award.substringBefore(" title").lowercase().let { if (it == "scoring") "points" else it })
            }
        }
    }

    private fun defLine(p: Player) = "%.1f steals · %.1f blocks · %.1f rebounds".format(p.stats.spg, p.stats.bpg, p.stats.rpg)

    // ---------- the playoffs and the Cup ----------

    /** A team's players' totals over [games] (box scores), with the games each played. */
    private fun totals(lg: League, games: List<Game>, teamId: Int): Map<Int, StatLine> {
        val out = LinkedHashMap<Int, StatLine>()
        for (g in games) {
            val box = g.box ?: continue
            val lines = when (teamId) { g.home -> box.home; g.away -> box.away; else -> continue }
            for (pl in lines) {
                if (pl.s.sec <= 0) continue
                val s = out.getOrPut(pl.pid) { StatLine() }
                s.add(pl.s.copy().also { it.gp = 1; it.gs = 0 })
            }
        }
        return out
    }

    private fun impact(s: StatLine) = s.ppg + 0.7 * s.rpg + 0.9 * s.apg + 1.2 * (s.spg + s.bpg) - 0.6 * s.per(s.tov)

    /** The series MVP from the winner's players, by what they did in the series. */
    fun seriesMvp(lg: League, s: Series): Pair<Player, StatLine>? {
        val games = s.gameIds.map { lg.game(it) }.filter { it.played }
        return totals(lg, games, s.winner).mapNotNull { (pid, st) -> lg.playerOrNull(pid)?.let { it to st } }
            .filter { it.second.gp * 2 >= games.size }.maxByOrNull { impact(it.second) + it.first.ovr * 0.02 }
    }

    fun seriesLine(s: StatLine) = "%.1f ppg · %.1f rpg · %.1f apg in the series".format(s.ppg, s.rpg, s.apg)

    /** A conference finals series is over: its MVP (since 2022). */
    fun conferenceFinals(lg: League, s: Series) {
        if (!confFinalsMvp(Eras.of(lg)) || s.round != 3 || !s.done) return
        val (p, st) = seriesMvp(lg, s) ?: return
        val award = "${if (s.conf == 0) "Eastern" else "Western"} Conference Finals MVP"
        give(lg, award, p, lg.team(s.winner), seriesLine(st))
        if (p.id == lg.heroId) lg.news += News(lg.season, lg.day, "${p.name} is the $award", true)
    }

    /** The Cup's five best: from the eight knockout teams, by their Cup games, the MVP first. */
    fun cupTeam(lg: League, champ: Team, mvp: Player?) {
        val ps = lg.proState ?: return
        val teams = ps.cup.flatMap { listOf(it.hi, it.lo) }.distinct()
        val cupGames = lg.games.filter { it.played && (it.type == GameType.CUP || it.label.startsWith("NBA Cup")) }
        val depth = { tid: Int -> ps.cup.filter { it.involves(tid) }.maxOfOrNull { it.round } ?: 0 }
        val ranked = teams.flatMap { tid ->
            totals(lg, cupGames, tid).mapNotNull { (pid, st) -> lg.playerOrNull(pid)?.let { Triple(it, tid, st) } }.filter { it.third.gp >= 3 }
        }.sortedByDescending { (p, tid, st) -> impact(st) + depth(tid) * 1.5 + (if (tid == champ.id) 2.0 else 0.0) + p.ovr * 0.02 }
        val five = (listOfNotNull(ranked.firstOrNull { it.first.id == mvp?.id }) + ranked.filter { it.first.id != mvp?.id }).take(5)
        for ((p, tid, st) in five) {
            p.awards += "${lg.season} NBA Cup All-Tournament"
            note(lg, "NBA Cup All-Tournament", p, tid, "%.1f ppg · %.1f rpg · %.1f apg in Cup games".format(st.ppg, st.rpg, st.apg))
        }
    }

    // ---------- reading ----------

    /** How a stored award reads: "All-NBA 1st" is "All-NBA First Team". */
    fun label(award: String): String = when {
        award.endsWith(" 1st") && (award.startsWith("All-NBA") || award.startsWith("All-Defensive") || award.startsWith("All-Rookie") || award.startsWith("All-League")) ->
            award.removeSuffix(" 1st").replace("All-League", "All-NBA") + " First Team"
        award.endsWith(" 2nd") && (award.startsWith("All-NBA") || award.startsWith("All-Defensive") || award.startsWith("All-Rookie") || award.startsWith("All-League")) ->
            award.removeSuffix(" 2nd").replace("All-League", "All-NBA") + " Second Team"
        award.endsWith(" 3rd") && (award.startsWith("All-NBA") || award.startsWith("All-League")) -> "All-NBA Third Team"
        else -> award
    }

    /** Older saves called the All-NBA teams "All-League". */
    fun migrate(lg: League) {
        if (!lg.pro) return
        fun fix(a: String) = if (a.contains(" All-League ")) a.replace(" All-League ", " All-NBA ") else a
        for (p in lg.players) if (p.awards.any { it.contains(" All-League ") }) p.awards.replaceAll(::fix)
        if (lg.seasonAwards.any { it.award.startsWith("All-League") }) lg.seasonAwards = lg.seasonAwards.map {
            if (it.award.startsWith("All-League")) AwardWinner(it.award.replace("All-League", "All-NBA"), it.pid, it.name, it.teamId, it.line) else it
        }.toMutableList()
    }

    /** His honors, grouped: "Player of the Week" to the seasons, most first. */
    fun trophyCase(p: Player): List<Pair<String, List<Int>>> =
        p.awards.mapNotNull { a -> a.substringBefore(' ').toIntOrNull()?.let { it to a.substringAfter(' ') } }
            .filter { !it.second.startsWith("Draft #") && !it.second.startsWith("Record:") }
            .groupBy({ it.second }, { it.first }).entries.map { it.key to it.value }
}
