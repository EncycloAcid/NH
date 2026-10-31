package com.nh.hoopscoach.game

import kotlin.math.log10
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The pro league the user's player enters after Draft Night: 30 franchises in two conferences,
 * an 83-game season with the NBA Cup, All-Star Weekend, a play-in and best-of-seven playoffs. It is its own League,
 * so the game engine, live games, big moments and box scores all work as they do in college.
 */
object ProLeague {
    /** The 30 teams, East then West, in team-id order. */
    val TEAMS get() = NbaData.TEAMS

    /** Logo file for a pro team, by its abbreviation. */
    fun logoKey(abbr: String) = NbaData.TEAMS.firstOrNull { it.abbr == abbr }?.logo

    /** Pro teams have no logo files: their badge is drawn from team colors. */
    const val LOGO = -7
    const val GAMES = 83
    /** The All-Star break and the end of the regular season come from the schedule. */
    fun allStarDay(lg: League) = lg.proState?.allStarDay ?: 30
    fun lastDay(lg: League) = lg.proState?.lastDay ?: 59
    /** Games each team plays this season (58 in saves from before the 83-game schedule). */
    fun seasonGames(lg: League) = if (lastDay(lg) <= 59) 58 else GAMES
    const val ROSTER = 13
    const val MAX_ROSTER = 15
    val ROUND_NAMES = listOf("First Round", "Conference Semifinals", "Conference Finals", "Finals")

    /** Real players kept per team when the league is built; the draft class fills the rest. */
    private const val REAL_ROSTER = 14

    fun teamName(i: Int) = TEAMS[i].let { "${it.city} ${it.name}" }

    /** Pro overalls from the roster data, as estimated. College players drop [STEP_UP] when they arrive instead. */
    fun proOverall(ovr: Int) = ovr.coerceIn(70, 99)

    /** The pros are a step up: a college player arrives at least this much lower, and no higher than [ROOKIE_MAX] (his ceiling stays). */
    const val STEP_UP = 5
    const val ROOKIE_MAX = 83

    /** Where a college player starts in the pros. */
    fun rookieOverall(collegeOvr: Int) = minOf(collegeOvr - STEP_UP, ROOKIE_MAX)

    /** How strong each team's real roster is: its best eight count most. */
    private fun strength(team: Int): Double {
        val top = NbaData.PLAYERS.filter { it.team == team }.map { proOverall(it.ovr) }.sortedDescending().take(8)
        return top.mapIndexed { i, o -> o * (1.0 - i * 0.07) }.sum() / top.indices.sumOf { 1.0 - it * 0.07 }
    }

    /**
     * Draft Night, before the pro league exists: the order the pro teams pick in. Weak teams pick
     * first, and the league is later built so that holds. Every pick gets its pro team's name.
     */
    fun planDraft(lg: League) {
        val r = lg.road ?: return
        if (r.proOrder.isEmpty()) {
            // Weakest rosters first, give or take; a lottery shuffles the four worst.
            val order = TEAMS.indices.sortedBy { strength(it) + Rng.gauss() * 0.6 }.toMutableList()
            val lottery = order.take(4).shuffled(Rng.r)
            for (i in 0 until 4) order[i] = lottery[i]
            r.proOrder.addAll(order)
        }
        for (d in lg.draftHistory) if (d.season == lg.season) d.proTeam = teamName(r.proOrder[(d.pick - 1) % TEAMS.size])
    }

    /** Builds the pro league from the college league once the user's player has been drafted (or signed undrafted). */
    fun create(college: League): League {
        val r = college.road!!
        val hero = college.hero!!
        if (r.proOrder.isEmpty()) planDraft(college)
        val order = r.proOrder.toList()
        val confs = listOf(
            Conference(0, "Eastern Conference", "EAST", 0, mutableListOf()),
            Conference(1, "Western Conference", "WEST", 0, mutableListOf()),
        )
        val teams = mutableListOf<Team>()
        for (f in TEAMS) {
            val t = Team(teams.size, f.city, f.name, f.abbr, f.conf, f.market, f.primary, f.secondary, LOGO)
            t.arena = 17_500 + (f.market - 55) * 80
            t.fans = 55.0 + (f.market - 55) * 0.6
            t.legacy = f.market.toDouble()
            t.buzz = 45.0
            confs[f.conf].teamIds += t.id
            teams += t
        }
        val lg = League(
            season = college.season, teams = teams, players = mutableListOf(), coaches = mutableListOf(), confs = confs,
            userTeamId = 0, userCoachId = 0, nextPlayerId = college.nextPlayerId + 1, nextCoachId = 1,
        )
        lg.mode = GameMode.PLAYER
        lg.level = Level.PRO
        lg.heroId = college.heroId
        lg.road = r
        lg.proState = ProState()
        for (t in teams) {
            val c = Coach(lg.nextCoachId++, Rng.pick(Names.first), Rng.pick(Names.last), Rng.range(38, 66), t.id,
                Rng.range(55, 92), Rng.range(55, 92), Rng.range(40, 80), Rng.range(50, 90), Rng.int(0, 1_000_000))
            lg.coaches += c
            t.coachId = c.id
        }
        // The real rosters: each team's best players, by the roster data.
        for (t in teams) {
            for (np in NbaData.PLAYERS.filter { it.team == t.id }.sortedByDescending { it.ovr }.take(REAL_ROSTER)) makeReal(lg, np, t)
        }
        lg.reindex()
        // The real draft class arrives: the user's classmates, the user's player, maybe his rival.
        val picks = college.draftHistory.filter { it.season == college.season }.sortedBy { it.pick }
        for (d in picks) {
            val p = college.anyPlayer(d.pid) ?: continue
            if (lg.playerOrNull(p.id) != null) continue
            joinPro(lg, p, lg.team(order[(d.pick - 1) % order.size]), d.cls)
        }
        if (lg.playerOrNull(hero.id) == null) {
            // Undrafted: a two-way deal with one of the weaker teams.
            val t = lg.team(order[Rng.int(0, 9)])
            joinPro(lg, hero, t, hero.year)
        }
        importRival(college, lg, order)
        for (t in teams) {
            trim(lg, t)
            while (t.roster.size < ROSTER) makePro(lg, Rng.pick(Pos.entries), Rng.range(84, 88), Rng.range(26, 33), t)
            Gameplan.autoAllocate(lg, t)
        }
        lg.reindex()
        rateTeams(lg)
        // The user's player, his first contract and his college record.
        val h = lg.player(hero.id)
        r.collegeTotals = Careers.total(h)
        r.collegeSeasons = h.career.size
        r.collegeSchools = r.schools.joinToString(" → ") { college.team(it).school }
        r.proStart = lg.season + 1
        r.goingPro = false
        r.ended = false
        r.offers.clear()
        r.press = null
        r.fatigue = 0
        r.hired.clear()
        r.raceRank = 0; r.lastRaceRank = 0; r.raceDay = -99
        r.proTeams += h.teamId
        val t = lg.team(h.teamId)
        val pick = r.draftPick
        r.contract = Contract(t.id, ProCareer.rookieSalary(if (r.undrafted) 0 else pick), ProCareer.rookieYears(if (r.undrafted) 0 else pick),
            if (r.undrafted) "Two-way" else if (pick <= 30) "Rookie scale" else "Second-round", lg.season)
        lg.userTeamId = t.id
        lg.userCoachId = t.coachId
        lg.phase = Phase.OFFSEASON
        lg.day = 0
        lg.proState!!.step = ProStep.FREE_AGENCY
        lg.news += News(lg.season, 0, "${h.name} signs with the ${t.name}: ${r.contract!!.years} years, ${Road.dollars(r.contract!!.salary)} a season", true)
        RecordBook.seed(lg)
        return lg
    }

    /** The rival follows him: drafted earlier (he's already in the league), or still in college (he enters next year). */
    private fun importRival(college: League, lg: League, order: List<Int>) {
        val r = lg.road ?: return
        val rv = Rival.player(college) ?: return
        if (lg.playerOrNull(rv.id) != null) return
        val inCollege = college.playerOrNull(rv.id)?.teamId?.let { it >= 0 } == true
        when {
            r.rivalPick > 0 -> {
                val years = (college.season - r.rivalDraft).coerceAtLeast(0)
                joinPro(lg, rv, lg.team(order[(r.rivalPick - 1) % order.size]), rv.year)
                repeat(years) { rv.age++; ProCareer.develop(rv, 60) }
                rv.proYears = years
            }
            inCollege -> lg.proState!!.pendingRival = rv
        }
    }

    /** A college player arrives in the pros. */
    fun joinPro(lg: League, p: Player, t: Team, collegeYears: Int) {
        // The league is a step up: a college star starts over as a pro rookie, with room to grow.
        if (p.year != PRO_YEAR) {
            if (p.id == lg.heroId) lg.road?.collegeOvr = p.ovr
            val target = rookieOverall(p.ovr)
            var guard = 0
            while (p.ovr > target && guard++ < 60) for (i in p.attrs.indices) p.attrs[i] = (p.attrs[i] - 1).coerceAtLeast(25)
            p.ovrHistory += p.ovr
        }
        p.teamId = t.id
        p.age = 18 + collegeYears.coerceIn(1, 5)
        p.year = PRO_YEAR
        p.proYears = 0
        p.walkOn = false
        p.transferFrom = -1
        p.lastTeam = -1
        p.left = ""
        p.nil = 0
        p.stats = StatLine()
        p.tourney = StatLine()
        p.log = mutableListOf()
        p.seasonGains = IntArray(10)
        p.seasonLevels = 0
        // College seasons keep their school's abbreviation but no longer point at a team in this league.
        p.career = p.career.map { SeasonLine(it.season, it.team, it.conf, it.cls, it.ovr, it.s, it.ncaa, it.w, it.l, it.result, -1) }.toMutableList()
        if (t.roster.any { lg.player(it).number == p.number }) p.number = Recruiting.freeNumber(lg, t)
        if (lg.playerOrNull(p.id) == null) lg.addPlayer(p)
        if (p.id !in t.roster) t.roster += p.id
    }

    /** A generated pro: [target] overall at [age], with the growth left that his age allows. */
    fun makePro(lg: League, pos: Pos, target: Int, age: Int, t: Team): Player {
        val p = LeagueGen.makePlayer(lg, pos, 4, target.coerceIn(70, 99), t.id)
        p.year = PRO_YEAR
        p.age = age
        p.proYears = (age - 22 + Rng.range(-1, 1)).coerceAtLeast(0)
        p.height = (Ratings.heightFor(pos) + 1).coerceAtMost(88)
        p.weight = Ratings.weightFor(p.height)
        p.pot = (p.ovr + (25 - age).coerceAtLeast(0) * 0.9 + Rng.range(0, 1)).roundToInt().coerceIn(p.ovr, 99)
        p.recruitRank = 0
        p.stars = 0
        p.ovrHistory.clear()
        p.ovrHistory += p.ovr
        if (t.roster.any { lg.player(it).number == p.number }) p.number = Recruiting.freeNumber(lg, t)
        lg.addPlayer(p)
        t.roster += p.id
        return p
    }

    /** A real player from the roster data: his name, number, position, age and photo; ratings built to his overall. */
    private fun makeReal(lg: League, np: NbaData.NbaPlayer, t: Team): Player {
        val p = makePro(lg, np.pos, proOverall(np.ovr), np.age, t)
        val parts = np.name.split(" ", limit = 2)
        p.first = parts[0]
        p.last = parts.getOrElse(1) { "" }
        p.country = null
        p.photo = np.photo
        if (t.roster.none { it != p.id && lg.player(it).number == np.jersey }) p.number = np.jersey
        return p
    }

    /** A roster over the limit cuts its weakest players (never the user's player or his rival). */
    fun trim(lg: League, t: Team) {
        val keep = setOf(lg.heroId, lg.road?.rivalId ?: -1)
        while (t.roster.size > MAX_ROSTER) {
            val cut = lg.rosterOf(t).filter { it.id !in keep }.minByOrNull { it.ovr - (30 - it.age).coerceAtLeast(0) * 0.3 } ?: break
            t.roster.remove(cut.id)
            lg.removePlayer(cut)
        }
    }

    /** Team strength, for win probability and home edge: the top eight players matter most. */
    fun rateTeams(lg: League) {
        for (t in lg.teams) {
            val top = lg.rosterOf(t).map { it.ovr }.sortedDescending().take(8)
            val v = top.mapIndexed { i, o -> o * (1.0 - i * 0.07) }.sum() / top.indices.sumOf { 1.0 - it * 0.07 }
            t.elo = 1500 + (v - 91) * 28
        }
    }

    /** Game 83: one more against a natural rival. */
    private val RIVALS = listOf(
        "BOS" to "LAL", "NYK" to "BKN", "CHI" to "DET", "MIA" to "ORL", "CLE" to "GSW", "PHI" to "TOR", "MIL" to "IND", "ATL" to "CHA",
        "HOU" to "DAL", "MEM" to "NOP", "DEN" to "UTA", "MIN" to "OKC", "POR" to "SAC", "LAC" to "PHX", "SAS" to "WAS",
    )

    /** Rivalry Night pairs: the classic rivalries, and anyone left over is paired at random. */
    private fun rivalryNight(lg: League): List<Pair<Int, Int>> {
        val byAbbr = lg.teams.associateBy { it.abbr }
        val left = lg.teams.map { it.id }.toMutableSet()
        val out = mutableListOf<Pair<Int, Int>>()
        for ((a, b) in RIVALS) {
            val x = byAbbr[a]?.id ?: continue
            val y = byAbbr[b]?.id ?: continue
            if (x !in left || y !in left) continue
            out += if (Rng.chance(0.5)) x to y else y to x
            left -= setOf(x, y)
        }
        left.shuffled(Rng.r).chunked(2).filter { it.size == 2 }.forEach { out += it[0] to it[1] }
        return out
    }

    /** Takes the meeting of [a] and [b] out of the round it was in, keeping who's at home. */
    private fun takeMeeting(rounds: List<MutableList<Pair<Int, Int>>>, a: Int, b: Int): Pair<Int, Int> {
        for (rd in rounds) {
            val k = rd.indexOfFirst { it == a to b || it == b to a }
            if (k >= 0) return rd.removeAt(k)
        }
        return a to b
    }

    /**
     * 83 games: twice against everyone (58), twice more against 12 of the 14 conference rivals (24),
     * and Rivalry Night (1). The NBA Cup group games are first-half meetings between group mates,
     * moved to five Cup nights early in the season. Three days are kept for the Cup knockouts,
     * and one for the All-Star break.
     */
    fun schedule(lg: League) {
        lg.games.clear()
        val ps = lg.proState
        val ids = lg.teams.map { it.id }.shuffled(Rng.r).toMutableList()
        val n = ids.size
        val rounds = mutableListOf<MutableList<Pair<Int, Int>>>()
        for (rd in 0 until n - 1) {
            rounds += (0 until n / 2).map { i -> val a = ids[i]; val b = ids[n - 1 - i]; if ((rd + i) % 2 == 0) a to b else b to a }.toMutableList()
            ids.add(1, ids.removeAt(n - 1))
        }
        val second = rounds.map { rd -> rd.map { (h, a) -> a to h } }
        // The NBA Cup draw, and its group games out of the first-half meetings.
        val groups = NbaCup.draw(lg)
        ps?.cupGroups?.apply { clear(); addAll(groups.map { it.toMutableList() }) }
        ps?.cup?.clear(); ps?.cupSeeds?.clear(); ps?.cupChampion = -1; ps?.cupMvp = ""; ps?.cupNote = ""
        val nights = NbaCup.groupNights(groups).map { night -> night.map { (a, b) -> takeMeeting(rounds, a, b) } }
        val all = (rounds.filter { it.isNotEmpty() } + second).toMutableList<List<Pair<Int, Int>>>()
        // Twice more against 12 of the 14 conference rivals.
        val extra = mutableListOf<Pair<Int, Int>>()
        for (c in lg.confs) {
            val ring = c.teamIds.shuffled(Rng.r)
            val m = ring.size
            for (i in 0 until m) for (j in i + 1 until m) {
                if (j - i == 1 || (i == 0 && j == m - 1)) continue
                extra += ring[i] to ring[j]
                extra += ring[j] to ring[i]
            }
        }
        val extraRounds = mutableListOf<MutableList<Pair<Int, Int>>>()
        val busy = mutableListOf<MutableSet<Int>>()
        for (g in extra.shuffled(Rng.r)) {
            val k = busy.indexOfFirst { g.first !in it && g.second !in it }
            if (k >= 0) { extraRounds[k] += g; busy[k] += listOf(g.first, g.second) }
            else { extraRounds += mutableListOf(g); busy += mutableSetOf(g.first, g.second) }
        }
        all += extraRounds
        all.shuffle(Rng.r)
        val rivalry = rivalryNight(lg)
        val m = all.size
        fun at(f: Double) = (m * f).toInt()
        val cupAt = listOf(0.05, 0.09, 0.13, 0.17, 0.21).map(::at)
        fun play(day: Int, games: List<Pair<Int, Int>>, label: (Int) -> String = { "" }) {
            for ((h, a) in games) {
                val type = if (lg.team(h).confId == lg.team(a).confId) GameType.CONF else GameType.NONCONF
                lg.addGame(Game(lg.nextGameId++, day, h, a, false, type, label(h)))
            }
        }
        var day = 1
        for ((i, rd) in all.withIndex()) {
            cupAt.forEachIndexed { k, c -> if (c == i) { play(day, nights[k]) { h -> NbaCup.groupLabel(groups.indexOfFirst { h in it }) }; day++ } }
            if (i == at(0.26)) { ps?.cupQfDay = day; day++ }
            if (i == at(0.28)) { ps?.cupSfDay = day; day++ }
            if (i == at(0.30)) { ps?.cupFinalDay = day; day++ }
            if (i == m / 2) { ps?.allStarDay = day; day++ }
            if (i == at(0.62)) { ps?.rivalryDay = day; play(day, rivalry) { "Rivalry Night" }; day++ }
            if (i == at(0.68)) ps?.deadline = day
            play(day, rd)
            day++
        }
        ps?.lastDay = day - 1
    }
}

/** The pro season: games, the All-Star break, the playoffs and the awards. */
object ProSeason {
    fun playDay(lg: League) {
        if (Season.seasonOver(lg)) return
        prepareDay(lg)
        for (g in Season.gamesOn(lg, lg.day)) if (!g.played) GameEngine(lg, g, false).commit()
        endOfDay(lg)
        AllTime.check(lg)
    }

    /** Playoff games are made a day at a time, since each depends on the series so far. */
    fun prepareDay(lg: League) {
        if (lg.phase != Phase.PLAYOFFS) return
        val ps = lg.proState ?: return
        for (s in ps.series) {
            if (s.done || s.gameIds.any { !lg.game(it).played }) continue
            val n = s.gameIds.size + 1
            val hiHome = n in listOf(1, 2, 5, 7)
            val label = if (s.bestOf == 1) "Play-In · ${playInName(s)}" else "${roundName(s)} · Game $n"
            val g = Game(lg.nextGameId++, lg.day, if (hiHome) s.hi else s.lo, if (hiHome) s.lo else s.hi, false, GameType.PLAYOFF, label)
            lg.addGame(g)
            s.gameIds += g.id
        }
    }

    private fun endOfDay(lg: League) {
        when (lg.phase) {
            Phase.REGULAR -> {
                upkeep(lg)
                NbaCup.endOfDay(lg)
                if (lg.day == ProLeague.allStarDay(lg) - 1) allStar(lg)
                if (lg.day == Trades.deadlineDay(lg) - 1) Trades.deadline(lg)
                if (lg.day >= ProLeague.lastDay(lg)) { startPlayoffs(lg); return }
            }
            Phase.PLAYOFFS -> {
                updateSeries(lg)
                if (lg.proState!!.champion >= 0) { endSeason(lg); return }
            }
            else -> return
        }
        lg.day++
        prepareDay(lg)
    }

    private fun upkeep(lg: League) {
        for (t in lg.teams) for (pid in t.roster) {
            val p = lg.player(pid)
            if (p.ovr < p.pot) Ratings.addXp(p, (3 * Ratings.devRate(p, Coaching.devOf(lg.coachOf(t)))).roundToInt())
        }
        if (lg.day % 7 == 0) for (t in lg.teams) Gameplan.autoAllocate(lg, t)
    }

    fun standings(lg: League, conf: Int): List<Team> =
        lg.teams.filter { it.confId == conf }.sortedWith(compareByDescending<Team> { it.winPct }.thenByDescending { it.cw - it.cl }.thenByDescending { it.elo })

    fun seed(lg: League, teamId: Int): Int = lg.proState?.seeds?.get(teamId) ?: 0

    fun roundName(s: Series) = if (s.round == 0) "Play-In" else ProLeague.ROUND_NAMES[s.round - 1]

    /** The three play-in games in each conference. */
    fun playInName(s: Series) = when (s.slot) { 0 -> "7 vs 8"; 1 -> "9 vs 10"; else -> "for the 8 seed" }

    /** The last series [teamId] played or is playing (in the play-in, the later game). */
    fun lastSeries(lg: League, teamId: Int): Series? =
        lg.proState?.series?.filter { it.involves(teamId) }?.maxWithOrNull(compareBy<Series>({ it.round }, { it.slot }))

    /** The All-Star break: twelve per conference, the fans' votes give a little lift, and a quick game. */
    fun allStar(lg: League) {
        val ps = lg.proState ?: return
        val r = lg.road
        ps.allStars.clear()
        ps.allStarSeason = lg.season
        AllStarWeekend.run(lg)
        val picked = mutableListOf<Pair<Player, Team>>()
        for (c in 0..1) {
            val pool = lg.teams.filter { it.confId == c }.flatMap { t -> lg.rosterOf(t).filter { it.stats.gp * 2 >= t.gp }.map { it to t } }
            val ranked = pool.sortedByDescending { (p, t) -> Awards.score(p, t) + (if (p.id == lg.heroId && r != null) fanVote(r.followers) else 0.0) }
            for (pt in ranked.take(12)) { picked += pt; ps.allStars += pt.first.id; pt.first.awards += "${lg.season} All-Star" }
        }
        val h = lg.playerOrNull(lg.heroId)
        val east = 160 + Rng.int(0, 26)
        val west = 160 + Rng.int(0, 26)
        val mvp = picked.sortedByDescending { (p, t) -> Awards.score(p, t) + Rng.gauss() * 6 }.first().first
        mvp.awards += "${lg.season} All-Star MVP"
        if (h != null && h.id in ps.allStars && r != null) {
            r.allStars++
            val pts = (8 + (h.ovr - 88) * 1.2 + Rng.gauss() * 5).roundToInt().coerceIn(2, 40)
            val gain = (60_000 + r.followers * 0.06 * Road.damp(r.followers)).roundToInt()
            r.followers += gain
            ps.allStarNote = "You're an All-Star. East $east, West $west; you score $pts" +
                (if (mvp.id == h.id) " and take home All-Star MVP" else "") + ". +${Road.count(gain)} followers."
            lg.news += News(lg.season, lg.day, "${h.name} named an All-Star" + if (mvp.id == h.id) ", then wins All-Star MVP" else "", true)
            Road.payMilestonesNow(lg)
        } else {
            ps.allStarNote = "East $east, West $west. ${mvp.name} is the All-Star MVP." + if (h != null) " You weren't picked this year." else ""
            lg.news += News(lg.season, lg.day, "All-Star Game: ${mvp.name} wins MVP", false)
        }
    }

    /** Followers turn into All-Star votes: a million adds about what a strong extra game a week would. */
    fun fanVote(followers: Int) = log10(max(followers, 1000) / 1000.0) * 0.9

    private fun startPlayoffs(lg: League) {
        val ps = lg.proState ?: return
        RecordBook.seasonEnd(lg)
        regularAwards(lg)
        ps.series.clear()
        ps.seeds.clear()
        ps.champion = -1
        // Seeds 1 to 6 are in. Seeds 7 to 10 play in for the last two spots.
        for (c in 0..1) {
            val top = standings(lg, c).take(10)
            top.forEachIndexed { i, t -> ps.seeds[t.id] = i + 1 }
            ps.series += Series(0, c, 0, top[6].id, top[7].id, bestOf = 1)
            ps.series += Series(0, c, 1, top[8].id, top[9].id, bestOf = 1)
        }
        val u = lg.user
        val seed = ps.seeds[u.id] ?: 0
        lg.news += News(lg.season, lg.day,
            when {
                seed in 1..6 -> "The ${u.name} are in the playoffs as the No. $seed seed in the ${lg.conf(u.confId).name}"
                seed > 0 -> "The ${u.name} are in the play-in as the No. $seed seed in the ${lg.conf(u.confId).name}"
                else -> "The ${u.name} miss the playoffs at ${u.w}-${u.l}"
            }, true)
        lg.phase = Phase.PLAYOFFS
        lg.day = ProLeague.lastDay(lg) + 2
        prepareDay(lg)
    }

    private fun updateSeries(lg: League) {
        val ps = lg.proState ?: return
        for (s in ps.series) {
            if (s.done) continue
            val last = s.gameIds.lastOrNull()?.let { lg.game(it) } ?: continue
            if (!last.played || last.day != lg.day) continue
            if (last.winner() == s.hi) s.hiWins++ else s.loWins++
            if (s.done && s.involves(lg.userTeamId)) {
                val won = s.winner == lg.userTeamId
                val other = lg.team(if (s.hi == lg.userTeamId) s.lo else s.hi)
                lg.news += News(lg.season, lg.day,
                    if (s.bestOf == 1) "Play-in: the ${lg.user.name} ${if (won) "beat" else "fall to"} the ${other.name}" + when {
                        won && s.slot == 0 -> " and take the 7 seed"
                        won && s.slot == 2 -> " and take the 8 seed"
                        won -> " and stay alive"
                        s.slot == 0 -> ", but get one more chance"
                        else -> ". Season over"
                    }
                    else "${if (won) "Series won" else "Eliminated"}: ${lg.user.name} ${if (won) "beat" else "fall to"} the ${other.name}, " +
                        "${max(s.hiWins, s.loWins)}-${minOf(s.hiWins, s.loWins)}, in the ${roundName(s)}", true)
            }
        }
        val round = ps.series.maxOf { it.round }
        val cur = ps.series.filter { it.round == round }
        if (!cur.all { it.done }) return
        when (round) {
            0 -> for (c in 0..1) {
                val a = cur.firstOrNull { it.conf == c && it.slot == 0 } ?: continue
                val b = cur.firstOrNull { it.conf == c && it.slot == 1 } ?: continue
                val last = cur.firstOrNull { it.conf == c && it.slot == 2 }
                if (last == null) { ps.series += Series(0, c, 2, a.loser, b.winner, bestOf = 1); continue }
                // The play-in is over: 7 and 8 are set, and the first round starts.
                ps.seeds[a.winner] = 7
                ps.seeds[last.winner] = 8
                ps.seeds.remove(b.loser)
                ps.seeds.remove(last.loser)
                val bySeed = ps.seeds.filter { lg.team(it.key).confId == c }.entries.associate { it.value to it.key }
                listOf(1 to 8, 4 to 5, 3 to 6, 2 to 7).forEachIndexed { slot, (x, y) -> ps.series += Series(1, c, slot, bySeed.getValue(x), bySeed.getValue(y)) }
            }
            4 -> ps.champion = cur.first().winner
            3 -> {
                val e = cur.first { it.conf == 0 }.winner
                val w = cur.first { it.conf == 1 }.winner
                val (hi, lo) = if (better(lg, e, w)) e to w else w to e
                ps.series += Series(4, -1, 0, hi, lo)
            }
            else -> for (c in 0..1) {
                val cs = cur.filter { it.conf == c }.sortedBy { it.slot }
                for (k in 0 until cs.size / 2) {
                    val a = cs[2 * k].winner
                    val b = cs[2 * k + 1].winner
                    val (hi, lo) = if (seed(lg, a) <= seed(lg, b)) a to b else b to a
                    ps.series += Series(round + 1, c, k, hi, lo)
                }
            }
        }
    }

    private fun better(lg: League, a: Int, b: Int): Boolean {
        val x = lg.team(a)
        val y = lg.team(b)
        return if (x.winPct != y.winPct) x.winPct > y.winPct else x.elo >= y.elo
    }

    /** How far a team got: "Champion", "Finals", ... "Missed playoffs". */
    fun resultFor(lg: League, teamId: Int): String {
        val ps = lg.proState ?: return ""
        if (ps.champion == teamId) return "Champion"
        val last = lastSeries(lg, teamId) ?: return if (lg.phase == Phase.REGULAR) "" else "Missed playoffs"
        return if (last.done && last.winner != teamId) "Lost in the ${roundName(last)}" else roundName(last)
    }

    private fun give(lg: League, award: String, p: Player, t: Team) {
        lg.seasonAwards += AwardWinner(award, p.id, p.name, t.id, Awards.line(p))
        p.awards += "${lg.season} $award"
    }

    /**
     * Awards night at the end of the regular season: the MVP vote, Rookie of the Year, Defensive
     * Player, Sixth Man, Most Improved, and the All-League, All-Defensive and All-Rookie teams.
     */
    private fun regularAwards(lg: League) {
        lg.seasonAwards.clear()
        val ladder = Awards.ladder(lg)
        if (ladder.isEmpty()) return
        val r = lg.road
        val ps = lg.proState
        val votes = AwardsNight.mvpVote(lg, ladder)
        ps?.mvpVotes?.apply { clear(); addAll(votes) }
        val mvp = votes.firstOrNull()?.let { v -> ladder.first { it.first.id == v.pid } } ?: ladder.first()
        mvp.let { (p, t) -> give(lg, "MVP", p, t); if (p.id == lg.heroId && r != null) r.mvps++ }
        ladder.firstOrNull { it.first.proYears == 0 }?.let { (p, t) -> give(lg, "Rookie of the Year", p, t) }
        val defense = ladder.take(60).sortedByDescending { Awards.defScore(it.first) }
        defense.firstOrNull()?.let { (p, t) -> give(lg, "Defensive Player of the Year", p, t) }
        ladder.firstOrNull { it.first.stats.gs * 2 < it.first.stats.gp }?.let { (p, t) -> give(lg, "Sixth Man of the Year", p, t) }
        AwardsNight.mostImproved(lg, ladder)?.let { (p, t) -> give(lg, "Most Improved Player", p, t) }
        val allLeague = (listOf(mvp) + ladder.filter { it !== mvp }).take(15)
        allLeague.forEachIndexed { i, (p, t) -> give(lg, "All-League ${listOf("1st", "2nd", "3rd")[i / 5]}", p, t) }
        defense.take(5).forEach { (p, t) -> give(lg, "All-Defensive 1st", p, t) }
        ladder.filter { it.first.proYears == 0 }.take(5).forEach { (p, t) -> give(lg, "All-Rookie 1st", p, t) }
        val mine = lg.seasonAwards.filter { it.pid == lg.heroId }.map { it.award }
        if (mine.isNotEmpty()) lg.news += News(lg.season, lg.day, "${lg.hero?.name}: ${mine.joinToString(", ")}", true)
    }

    private fun endSeason(lg: League) {
        val ps = lg.proState!!
        val champ = lg.team(ps.champion)
        val finals = ps.series.first { it.round == 4 }
        val runnerUp = if (finals.hi == champ.id) finals.lo else finals.hi
        val fmvp = lg.rosterOf(champ).filter { it.tourney.gp > 0 }.maxByOrNull { it.tourney.ppg + it.tourney.rpg * 0.7 + it.tourney.apg * 0.9 + it.ovr * 0.05 }
        if (fmvp != null) give(lg, "Finals MVP", fmvp, champ)
        for (p in lg.rosterOf(champ)) p.awards += "${lg.season} Champion"
        val r = lg.road
        if (r != null && lg.userTeamId == champ.id && lg.playerOrNull(lg.heroId)?.teamId == champ.id) r.rings++
        lg.news += News(lg.season, lg.day, "The ${champ.name} win the title over the ${lg.team(runnerUp).name}" + (fmvp?.let { ". Finals MVP: ${it.name}" } ?: ""), true)
        for (p in lg.players) {
            val t = if (p.teamId >= 0) lg.team(p.teamId) else null
            if (t != null && (p.stats.gp > 0 || p.tourney.gp > 0)) {
                p.career += SeasonLine(lg.season, t.abbr, lg.conf(t.confId).abbr, PRO_YEAR, p.ovr, p.stats.copy(), p.tourney.takeIf { it.gp > 0 }?.copy(), t.w, t.l, resultFor(lg, t.id), t.id)
            }
            p.ovrHistory += p.ovr
        }
        val heroResult = resultFor(lg, lg.userTeamId)
        ps.history += ProSeasonSummary(
            lg.season, champ.id, runnerUp,
            lg.seasonAwards.firstOrNull { it.award == "MVP" }?.name ?: "", fmvp?.name ?: "",
            lg.seasonAwards.firstOrNull { it.award == "Rookie of the Year" }?.name ?: "", heroResult,
        )
        ProCareer.seasonEnd(lg)
        lg.phase = Phase.OFFSEASON
        ps.step = ProStep.REVIEW
    }

    /** A new season: records reset, a new schedule, salaries paid. */
    fun startSeason(lg: League) {
        val ps = lg.proState!!
        lg.season++
        lg.day = 1
        lg.phase = Phase.REGULAR
        lg.games.clear()
        lg.seasonAwards.clear()
        ps.series.clear(); ps.seeds.clear(); ps.champion = -1
        ps.allStars.clear(); ps.allStarNote = ""; ps.weekend.clear(); ps.mvpVotes.clear()
        ps.draftedByUser.clear(); ps.retiredThisSummer.clear(); ps.before.clear()
        ps.moved.clear()
        for (t in lg.teams) {
            t.w = 0; t.l = 0; t.cw = 0; t.cl = 0; t.streak = 0
            t.totals = StatLine(); t.opp = StatLine()
            t.homeGames = 0; t.homeAtt = 0; t.sellouts = 0
            Gameplan.autoAllocate(lg, t)
        }
        for (p in lg.players) {
            p.stats = StatLine(); p.tourney = StatLine(); p.log = mutableListOf()
            p.seasonGains = IntArray(10); p.seasonLevels = 0
        }
        lg.norms = null
        ProLeague.rateTeams(lg)
        ProLeague.schedule(lg)
        lg.reindex()
        Road.sync(lg)
        ProCareer.paySalary(lg)
        val h = lg.hero
        if (h != null) lg.news += News(lg.season, 0, "${h.name} opens his ${ordinalWord(h.proYears + 1)} pro season with the ${lg.user.name}", true)
    }

    private fun ordinalWord(n: Int) = when (n) { 1 -> "first"; 2 -> "second"; 3 -> "third"; 4 -> "fourth"; 5 -> "fifth"; 6 -> "sixth"; 7 -> "seventh"; 8 -> "eighth"; 9 -> "ninth"; 10 -> "tenth"; else -> "${n}th" }
}

/** The user's pro career: contracts, salary and endorsements, the offseason, aging and retirement. */
object ProCareer {
    /** First-round picks get a four-year scale deal, second-rounders two years, undrafted players a two-way. */
    fun rookieSalary(pick: Int): Int = when {
        pick <= 0 -> 750
        pick <= 30 -> 10_500 - (pick - 1) * 290
        else -> 1_600 - (pick - 31) * 15
    }

    fun rookieYears(pick: Int) = if (pick in 1..30) 4 else 2

    /** What the league would pay him a season, in thousands. */
    fun marketValue(lg: League, p: Player): Int {
        val o = p.ovr
        var v = when {
            o >= 98 -> 52_000.0; o >= 96 -> 42_000.0; o >= 94 -> 32_000.0; o >= 92 -> 22_000.0
            o >= 90 -> 14_000.0; o >= 88 -> 8_000.0; o >= 86 -> 4_000.0; o >= 83 -> 2_000.0; else -> 1_100.0
        }
        v *= when { p.age >= 37 -> 0.5; p.age >= 35 -> 0.65; p.age >= 33 -> 0.8; p.age >= 31 -> 0.92; else -> 1.0 }
        val recent = p.awards.filter { it.startsWith("${lg.season} ") || it.startsWith("${lg.season - 1} ") }
        if (recent.any { it.substringAfter(" ") == "MVP" }) v *= 1.15
        if (recent.any { it.endsWith("All-Star") }) v *= 1.08
        return (v * agentCut(lg)).roundToInt().coerceIn(750, 55_000) / 10 * 10
    }

    private fun agentCut(lg: League) = if (Road.has(lg, Staff.AGENT)) 1.15 else 1.0

    /** Endorsements: $2 a follower a year for the first two million, 50 cents after that. */
    fun endorsements(lg: League): Int {
        val f = (lg.road?.followers ?: 0).toDouble()
        val v = minOf(f, 2_000_000.0) / 1000 * 2.0 + (f - 2_000_000.0).coerceAtLeast(0.0) / 1000 * 0.5
        return (v * agentCut(lg)).roundToInt()
    }

    /** Salary lands when the season starts, so he can spend it during the season. */
    fun paySalary(lg: League) {
        val r = lg.road ?: return
        val c = r.contract ?: return
        if (r.salarySeason == lg.season || r.retired) return
        r.earned += c.salary
        r.salarySeason = lg.season
    }

    fun age(lg: League) = lg.hero?.age ?: 0

    fun seasonEnd(lg: League) {
        val r = lg.road ?: return
        val h = lg.playerOrNull(lg.heroId) ?: return
        val t = lg.team(h.teamId)
        val endorse = endorsements(lg)
        r.earned += endorse
        r.contract?.let { it.years-- }
        val s = h.stats
        val honors = h.awards.filter { it.startsWith("${lg.season} ") }.map { it.removePrefix("${lg.season} ") }
        r.proStory += "${lg.season} · ${t.name} · age ${h.age} · %.1f ppg, %.1f rpg, %.1f apg · ${t.w}-${t.l} · ${ProSeason.resultFor(lg, t.id)}".format(s.ppg, s.rpg, s.apg) +
            (if (honors.isNotEmpty()) " · " + honors.joinToString(", ") else "") +
            " · salary ${Road.dollars(r.contract?.salary ?: 0)}, endorsements ${Road.dollars(endorse)} · ${Road.count(r.followers)} followers"
        r.seasonStartFollowers = r.followers
        r.press = null
        r.fatigue = 0
        r.raceRank = 0
        r.lastRaceRank = 0
        r.raceDay = -99
        r.hired.clear()
        for (p in lg.players) if (p.stats.gp > 0 || p.tourney.gp > 0) p.proYears++
    }

    /** Old enough to call it a career. */
    fun canRetire(lg: League) = age(lg) >= 30 && lg.road?.retired == false

    /** He can play into his early 40s if somebody still wants him. */
    const val MAX_AGE = 43

    // ---------- the offseason ----------

    /** Moves the pro offseason one step: review, draft, free agency, summer, then the new season. */
    fun next(lg: League) {
        val ps = lg.proState ?: return
        val r = lg.road ?: return
        if (r.ended) return
        when (ps.step) {
            ProStep.REVIEW -> {
                if (age(lg) >= MAX_AGE) { retire(lg, "Nobody plays forever. "); return }
                newYear(lg)
                ps.moved.clear()
                draft(lg)
                Trades.summer(lg)
                StarRecruit.openMarket(lg)
                ps.step = ProStep.DRAFT
            }
            ProStep.DRAFT -> {
                // A player option on the last year: he decides before free agency opens.
                r.contract?.let { c -> if (c.option && c.years == 1) r.optionPending = true }
                if ((r.contract?.years ?: 0) <= 0) {
                    r.proOffers.clear()
                    r.proOffers += offers(lg)
                    if (r.proOffers.isEmpty()) { retire(lg, "Nobody called. "); return }
                }
                ps.step = ProStep.FREE_AGENCY
            }
            ProStep.FREE_AGENCY -> {
                if (r.optionPending) optIn(lg)
                ps.freeStars.clear()
                if ((r.contract?.years ?: 0) <= 0) r.proOffers.firstOrNull()?.let { sign(lg, it) }
                // The rosters start out current, so the league skips a summer of aging before its first season.
                if (ps.history.isNotEmpty()) summer(lg)
                ps.step = ProStep.SUMMER
            }
            ProStep.SUMMER -> {
                // A trade request still open when camp opens gets settled with the first team that called.
                Trades.settleOpenRequest(lg)
                ProSeason.startSeason(lg)
            }
        }
    }

    /** A year older for everyone; some veterans retire. */
    private fun newYear(lg: League) {
        val ps = lg.proState!!
        val r = lg.road!!
        for (p in lg.players) p.age++
        for (t in lg.teams) for (pid in t.roster.toList()) {
            val p = lg.player(pid)
            if (p.id == lg.heroId) continue
            // Good players last: most stars play into their late 30s. Fading role players go sooner.
            val go = p.age >= 42 || (p.age >= 36 && Rng.chance((p.age - 35) * 0.14)) || (p.age >= 33 && p.ovr < 80) || (p.age >= 30 && p.ovr < 76)
            if (!go) continue
            AllTime.freeze(lg, p)
            t.roster.remove(pid)
            lg.removePlayer(p)
            if (p.id == r.rivalId) {
                r.rivalLeft = "retired in ${lg.season}"
                p.teamId = -1
                p.left = "Retired in ${lg.season}"
                lg.alumni += p
            }
            if (t.id == lg.userTeamId || p.awards.any { it.endsWith(" MVP") || it.endsWith("All-League 1st") }) ps.retiredThisSummer += "${p.name} (${p.age})"
        }
    }

    /** Sixty rookies, worst teams first. The rival arrives here if he stayed in college. */
    private fun draft(lg: League) {
        val ps = lg.proState!!
        val r = lg.road!!
        val byRecord = lg.teams.sortedWith(compareBy<Team> { it.winPct }.thenBy { it.elo }).map { it.id }
        // Lottery night: the 14 teams that missed the playoffs draw for the top four picks, worst odds-on.
        val inPlayoffs = ps.series.filter { it.round >= 1 }.flatMap { listOf(it.hi, it.lo) }.toSet()
        val lotteryTeams = byRecord.filter { it !in inPlayoffs }.take(14)
        val drawn = Lottery.draw(lotteryTeams)
        ps.lotteryPre.apply { clear(); addAll(lotteryTeams) }
        ps.lotteryPicks.apply { clear(); addAll(drawn) }
        ps.lotterySeason = lg.season
        val order = (drawn + byRecord.filter { it !in drawn }).toMutableList()
        lg.news += News(lg.season, lg.day, "Lottery night: the ${lg.team(drawn.first()).name} win the No. 1 pick" +
            (if (lotteryTeams.indexOf(drawn.first()) > 2) ", jumping from No. ${lotteryTeams.indexOf(drawn.first()) + 1}" else ""), drawn.first() == lg.userTeamId)
        val rookies = mutableListOf<Pair<Pos, Int>>()
        for (i in 0 until 60) rookies += Rng.pick(Pos.entries) to (82 - i * 0.12 + Rng.gauss() * 2).roundToInt().coerceIn(72, ProLeague.ROOKIE_MAX)
        rookies.sortByDescending { it.second }
        val rival = ps.pendingRival
        var rivalSlot = -1
        if (rival != null) {
            Ratings.offseasonGrowth(rival, 70)
            rivalSlot = rookies.indexOfFirst { it.second <= rival.ovr }.let { if (it < 0) 59 else it }
        }
        ps.draftedByUser.clear()
        for (i in 0 until 60) {
            val t = lg.team(order[i % order.size])
            val p = if (i == rivalSlot && rival != null) {
                ProLeague.joinPro(lg, rival, t, rival.year + 1)
                r.rivalPick = i + 1
                r.rivalDraft = lg.season
                r.rivalLeft = "went No. ${i + 1} in the ${lg.season} draft"
                ps.pendingRival = null
                lg.news += News(lg.season, 0, "Your rival ${rival.name} goes No. ${i + 1} to the ${t.name}. He's coming for you.", true)
                rival
            } else {
                val (pos, target) = rookies[i]
                ProLeague.makePro(lg, pos, target, Rng.range(19, 22), t).also {
                    it.proYears = 0
                    it.awards += "${lg.season} Draft #${i + 1}"
                    // Rookies come in raw, with real upside.
                    it.pot = (it.ovr + (24 - it.age) * 2 + Rng.range(0, 4)).coerceIn(it.ovr, 99)
                }
            }
            if (t.id == lg.userTeamId) ps.draftedByUser += p.id
        }
        for (t in lg.teams) {
            ProLeague.trim(lg, t)
            while (t.roster.size < ProLeague.ROSTER) ProLeague.makePro(lg, Rng.pick(Pos.entries), Rng.range(84, 88), Rng.range(26, 33), t)
        }
        lg.reindex()
    }

    /** Teams that want him, with what they'd pay and where he'd fit. His own team always asks first. */
    fun offers(lg: League): List<ProOffer> {
        val h = lg.playerOrNull(lg.heroId) ?: return emptyList()
        val value = marketValue(lg, h)
        val count = when { h.ovr >= 90 -> 6; h.ovr >= 86 -> 4; h.ovr >= 80 -> 3; h.ovr >= 77 -> 1; else -> 0 }
        if (count == 0) return emptyList()
        val years = when { h.age <= 27 -> Rng.range(4, 5); h.age <= 30 -> 3; h.age <= 33 -> Rng.range(2, 3); h.age <= 36 -> 2; else -> 1 }
        val pool = (listOf(lg.user) + lg.teams.filter { it.id != h.teamId }.shuffled(Rng.r)).take(count)
        return pool.map { t ->
            val ahead = lg.rosterOf(t).count { it.id != h.id && it.pos == h.pos && it.ovr >= h.ovr }
            val pay = ((value * (if (t.id == h.teamId) 1.05 else 0.85 + Rng.d() * 0.3)).roundToInt() / 10 * 10).coerceIn(750, MAX_SALARY)
            // How far each team will stretch: a little more for his own team, never past the max.
            val max = ((pay * (1.04 + Rng.d() * 0.24 + if (t.id == h.teamId) 0.05 else 0.0)).roundToInt() / 10 * 10).coerceIn(pay, MAX_SALARY)
            ProOffer(t.id, pay, years, roleFor(ahead), ahead, t.winPct, max)
        }.sortedByDescending { it.salary }
    }

    /** The biggest contract the league allows, in thousands a season. */
    const val MAX_SALARY = 55_000

    /** Longest deal a team will give him at his age. */
    fun maxYears(lg: League) = lg.hero?.age?.let { when { it <= 27 -> 5; it <= 30 -> 4; it <= 33 -> 3; it <= 36 -> 2; else -> 1 } } ?: 3

    /** A contender: won 55% last season or reached the conference finals. */
    fun contender(lg: League, teamId: Int): Boolean {
        val t = lg.team(teamId)
        val deep = lg.proState?.series?.any { it.round >= 3 && it.involves(teamId) } == true
        return t.winPct >= 0.55 || deep
    }

    /**
     * He asks for [pct] more. The team agrees if it fits, comes up to its limit, or walks away
     * (more likely the bigger the ask). One counter per offer. Returns the team's answer.
     */
    fun counter(lg: League, i: Int, pct: Double): String {
        val r = lg.road ?: return ""
        val o = r.proOffers.getOrNull(i) ?: return ""
        val t = lg.team(o.teamId)
        if (o.countered) return o.note
        if (o.salary >= MAX_SALARY) { o.note = "It's already a max offer."; return o.note }
        val ask = ((o.salary * (1 + pct)).roundToInt() / 10 * 10).coerceAtMost(MAX_SALARY)
        o.countered = true
        val walk = (if (pct >= 0.2) 0.3 else 0.1) + if (o.max < o.salary * 1.08) 0.15 else 0.0
        o.note = when {
            ask <= o.max -> { o.salary = ask; "Deal. The ${t.name} come up to ${Road.dollars(ask)}." }
            Rng.chance(walk) -> {
                r.proOffers.removeAt(i)
                lg.news += News(lg.season, 0, "The ${t.name} pull their offer to ${lg.hero?.name}", true)
                return "The ${t.name} walked away."
            }
            else -> { o.salary = o.max; "Final offer: the ${t.name} go to ${Road.dollars(o.max)} and no higher." }
        }
        return o.note
    }

    /**
     * Signs offer [i] for [years], with a player option on the last year (5% less a season) if [option],
     * or at a 15% ring discount with a contender, which spends the savings on a veteran.
     */
    fun signWith(lg: League, i: Int, years: Int, option: Boolean, discount: Boolean): String {
        val r = lg.road ?: return ""
        val o = r.proOffers.getOrNull(i) ?: return ""
        val y = years.coerceIn(1, maxYears(lg))
        var pay = o.salary
        if (option && y >= 2) pay = (pay * 0.95).toInt() / 10 * 10
        val ringDeal = discount && contender(lg, o.teamId)
        val saved = if (ringDeal) pay - (pay * 0.85).toInt() / 10 * 10 else 0
        pay -= saved
        val t = lg.team(o.teamId)
        sign(lg, ProOffer(o.teamId, pay, y, o.role, o.ahead, o.winPct, o.max))
        r.contract?.option = option && y >= 2
        if (!ringDeal) return "You sign with the ${t.name}."
        // The money he left on the table buys a good veteran.
        val add = ProLeague.makePro(lg, Rng.pick(Pos.entries), (88 + saved / 2500).coerceAtMost(94), Rng.range(27, 31), t)
        ProLeague.trim(lg, t)
        Gameplan.autoAllocate(lg, t)
        lg.reindex()
        lg.news += News(lg.season, 0, "${lg.hero?.name} takes less to chase a ring; the ${t.name} use the room to sign ${add.name}", true)
        return "You took ${Road.dollars(saved)} less a season. The ${t.name} spent it on ${add.name} (${add.ovr})."
    }

    /** The player option on his last year: keep the year, or walk into free agency now. */
    fun optIn(lg: League) {
        val r = lg.road ?: return
        r.optionPending = false
        r.contract?.option = false
        lg.news += News(lg.season, 0, "${lg.hero?.name} picks up his player option with the ${lg.user.name}", true)
    }

    fun optOut(lg: League) {
        val r = lg.road ?: return
        r.optionPending = false
        r.contract?.let { it.years = 0; it.option = false }
        r.proOffers.clear()
        r.proOffers += offers(lg)
        lg.news += News(lg.season, 0, "${lg.hero?.name} opts out and hits free agency", true)
    }

    fun roleFor(ahead: Int) = when (ahead) { 0 -> "Starter"; 1 -> "Sixth man"; 2 -> "Rotation"; else -> "Bench" }

    /** Signs with [o]: moves him if it's a new team. */
    fun sign(lg: League, o: ProOffer) {
        val r = lg.road ?: return
        val h = lg.playerOrNull(lg.heroId) ?: return
        val from = lg.team(h.teamId)
        val to = lg.team(o.teamId)
        if (from.id != to.id) {
            from.roster.remove(h.id)
            h.teamId = to.id
            if (to.roster.any { lg.player(it).number == h.number }) h.number = Recruiting.freeNumber(lg, to)
            to.roster += h.id
            ProLeague.trim(lg, to)
            while (from.roster.size < ProLeague.ROSTER) ProLeague.makePro(lg, Rng.pick(Pos.entries), Rng.range(84, 88), Rng.range(26, 33), from)
            Gameplan.autoAllocate(lg, from)
            if (r.proTeams.lastOrNull() != to.id) r.proTeams += to.id
        }
        Gameplan.autoAllocate(lg, to)
        val kind = if (o.salary >= 45_000) "Max" else "Veteran"
        r.contract = Contract(to.id, o.salary, o.years, kind, lg.season)
        r.proOffers.clear()
        Road.sync(lg)
        lg.news += News(lg.season, 0,
            if (from.id == to.id) "${h.name} re-signs with the ${to.name}: ${o.years} years, ${Road.dollars(o.salary)} a season"
            else "${h.name} signs with the ${to.name}: ${o.years} years, ${Road.dollars(o.salary)} a season", true)
    }

    /** Everyone grows or ages over the summer. Young players climb toward their ceiling; from 31 on, they slowly slip. */
    private fun summer(lg: League) {
        val ps = lg.proState!!
        ps.before.clear()
        for (t in lg.teams) {
            val dev = Coaching.devOf(lg.coachOf(t))
            for (pid in t.roster) {
                val p = lg.player(pid)
                ps.before[p.id] = p.ovr
                val trainer = p.id == lg.heroId && lg.road?.hired?.contains(Staff.TRAINER) == true
                develop(p, dev, trainer)
            }
        }
        lg.reindex()
    }

    /** One summer of growth or decline for [p]. */
    fun develop(p: Player, coachDev: Int, trainer: Boolean = false) {
        when {
            p.age <= 27 -> Ratings.offseasonGrowth(p, coachDev)
            p.age <= 30 -> if (p.pot > p.ovr) Ratings.offseasonGrowth(p, coachDev) else Ratings.offseasonGrowth(p.also { it.pot = it.ovr }, coachDev)
            else -> {
                // A slow slide from 31, faster in the late 30s.
                var drop = (p.age - 30) * 0.55 + Rng.d() * 0.8
                if (trainer) drop *= 0.6
                val steps = (drop * 7.3).roundToInt()
                val w = doubleArrayOf(1.3, 0.6, 0.5, 1.0, 0.4, 1.1, 1.4, 1.0, 1.3, 1.2)
                val idx = (0 until 10).toList()
                repeat(steps) {
                    val i = Rng.weighted(idx) { w[it] }
                    p.attrs[i] = max(25, p.attrs[i] - 1)
                }
                p.pot = p.ovr
            }
        }
    }

    // ---------- the end ----------

    /** Hall of Fame case: titles, MVPs, All-League teams and All-Star games, plus longevity. */
    fun hofScore(lg: League): Double {
        val r = lg.road ?: return 0.0
        val h = lg.hero ?: return 0.0
        val allLeague = h.awards.count { it.contains("All-League 1st") } * 3.0 + h.awards.count { it.contains("All-League 2nd") || it.contains("All-League 3rd") } * 1.5
        return r.rings * 3.0 + r.mvps * 6.0 + allLeague + r.allStars * 1.5 + h.awards.count { it.endsWith("Finals MVP") } * 3.0 + h.proYears * 0.4
    }

    fun hofVerdict(lg: League): String {
        val v = hofScore(lg)
        return when {
            v >= 30 -> "First-ballot Hall of Famer"
            v >= 18 -> "Hall of Famer"
            v >= 10 -> "On the Hall of Fame bubble"
            else -> "A pro career to be proud of"
        }
    }

    /** He hangs it up. */
    fun retire(lg: League, why: String = "") {
        val r = lg.road ?: return
        val h = lg.playerOrNull(lg.heroId) ?: return
        val years = h.proYears
        r.retired = true
        r.ended = true
        lg.team(h.teamId).roster.remove(h.id)
        Gameplan.autoAllocate(lg, lg.team(h.teamId))
        h.lastTeam = h.teamId
        h.teamId = -1
        h.left = "Retired in ${lg.season}"
        lg.removePlayer(h)
        if (lg.alumni.none { it.id == h.id }) lg.alumni += h
        r.ending = "$why${h.name} retires after $years pro ${if (years == 1) "season" else "seasons"}" +
            (if (r.rings > 0) ", ${r.rings} ${if (r.rings == 1) "ring" else "rings"}" else "") +
            (if (r.mvps > 0) ", ${r.mvps} MVP${if (r.mvps == 1) "" else "s"}" else "") +
            (if (r.allStars > 0) ", ${r.allStars} All-Star ${if (r.allStars == 1) "selection" else "selections"}" else "") +
            ". ${hofVerdict(lg)}." +
            AllTime.rank(lg).let { if (it > 0) " He retires No. $it on the all-time list." else "" }
        Rival.player(lg)?.let { rv ->
            val rings = rv.awards.count { it.endsWith(" Champion") }
            val stars = rv.awards.count { it.endsWith(" All-Star") }
            r.ending += " Your rival ${rv.name}: $rings ${if (rings == 1) "ring" else "rings"}, $stars All-Star ${if (stars == 1) "selection" else "selections"}."
        }
        Afterward.retireNumbers(lg)
        lg.news += News(lg.season, lg.day, "${h.name} announces his retirement", true)
    }
}

/** Picks the right season loop for the league. */
object Sim {
    fun playDay(lg: League) = if (lg.pro) ProSeason.playDay(lg) else Season.playDay(lg)
}
