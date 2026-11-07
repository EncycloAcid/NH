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

    /** Pro teams use their logo files by abbreviation; an era's old club names get a monogram badge instead. */
    const val LOGO = -7
    const val ERA_BADGE = -8
    const val GAMES = 83
    /** The All-Star break and the end of the regular season come from the schedule. */
    fun allStarDay(lg: League) = lg.proState?.allStarDay ?: 30
    fun lastDay(lg: League) = lg.proState?.lastDay ?: 59
    /** Games each team plays this season (58 in saves from before the 83-game schedule). */
    fun seasonGames(lg: League) = lg.proState?.games?.takeIf { it > 0 } ?: if (lastDay(lg) <= 59) 58 else GAMES

    /**
     * A season for a league that isn't 30 teams (23 in 1985-86, 27 in 1989-90...): everyone twice,
     * then more games against conference rivals, to 82 (an odd number of teams can't all play 83)
     * or 83. No Cup or Rivalry Night; one day for the All-Star break.
     */
    private fun scheduleAny(lg: League) {
        lg.games.clear()
        val ps = lg.proState
        val n = lg.teams.size
        val target = if (n % 2 == 1) 82 else GAMES
        ps?.games = target
        val ids = lg.teams.map { it.id }.shuffled(Rng.r).toMutableList<Int>()
        if (ids.size % 2 == 1) ids += -1
        val m = ids.size
        val rounds = mutableListOf<MutableList<Pair<Int, Int>>>()
        for (rd in 0 until m - 1) {
            rounds += (0 until m / 2).mapNotNull { i ->
                val a = ids[i]; val b = ids[m - 1 - i]
                if (a < 0 || b < 0) null else if ((rd + i) % 2 == 0) a to b else b to a
            }.toMutableList()
            ids.add(1, ids.removeAt(m - 1))
        }
        val all = (rounds + rounds.map { rd -> rd.map { (h, a) -> a to h }.toMutableList() }).toMutableList()
        // The rest, mostly against conference rivals, spread over them.
        val need = lg.teams.associate { it.id to target - 2 * (n - 1) }.toMutableMap()
        val met = mutableMapOf<Long, Int>()
        fun key(a: Int, b: Int) = minOf(a, b) * 1000L + maxOf(a, b)
        val extra = mutableListOf<Pair<Int, Int>>()
        var guard = 0
        while (need.values.any { it > 0 } && guard++ < 5000) {
            val a = need.filter { it.value > 0 }.maxBy { it.value + Rng.d() }.key
            val conf = lg.team(a).confId
            val b = need.filter { it.value > 0 && it.key != a }.keys
                .maxByOrNull { b -> (if (lg.team(b).confId == conf) 50.0 else 0.0) - (met[key(a, b)] ?: 0) * 8 + need.getValue(b) + Rng.d() }
            if (b == null) {
                // One team left needing games: split an existing extra game around it.
                val g = extra.firstOrNull { it.first != a && it.second != a } ?: break
                extra.remove(g)
                extra += a to g.first; extra += g.second to a
                need[a] = need.getValue(a) - 2
                continue
            }
            extra += if (Rng.chance(0.5)) a to b else b to a
            need[a] = need.getValue(a) - 1
            need[b] = need.getValue(b) - 1
            met[key(a, b)] = (met[key(a, b)] ?: 0) + 1
        }
        val busy = mutableListOf<MutableSet<Int>>()
        val extraRounds = mutableListOf<MutableList<Pair<Int, Int>>>()
        for (g in extra.shuffled(Rng.r)) {
            val k = busy.indexOfFirst { g.first !in it && g.second !in it }
            if (k >= 0) { extraRounds[k] += g; busy[k] += listOf(g.first, g.second) }
            else { extraRounds += mutableListOf(g); busy += mutableSetOf(g.first, g.second) }
        }
        all += extraRounds
        all.shuffle(Rng.r)
        var day = 1
        for ((i, rd) in all.withIndex()) {
            if (i == all.size / 2) { ps?.allStarDay = day; day++ }
            if (i == (all.size * 0.68).toInt()) ps?.deadline = day
            for ((h, a) in rd) {
                val type = if (lg.team(h).confId == lg.team(a).confId) GameType.CONF else GameType.NONCONF
                lg.addGame(Game(lg.nextGameId++, day, h, a, false, type, ""))
            }
            day++
        }
        ps?.rivalryDay = 0
        ps?.lastDay = day - 1
        NbaAwards.preseason(lg)
    }
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
    /** The rookie cap: 87 on the game's scale, 80 on the 2K scale the screens show (a top pick arrives around 77 to 80). */
    const val ROOKIE_MAX = 87

    /** A draftee's starting overall by pick: about 85 for No. 1 (77 on screen), 80 for No. 30, 76 for No. 60. */
    fun draftOverall(pick: Int) = (85 - (pick - 1) * 0.15 + Rng.gauss() * 1.8).roundToInt().coerceIn(73, ROOKIE_MAX)

    /** Where a college player starts in the pros. */
    fun rookieOverall(collegeOvr: Int) = minOf(collegeOvr - STEP_UP, ROOKIE_MAX)

    /** A first-year pro can't climb past [ROOKIE_MAX]: not at summer camp, not during the season. Growth starts his first summer. */
    fun cappedRookie(lg: League, p: Player) = lg.pro && p.year == PRO_YEAR && p.proYears == 0

    /** Holds a first-year pro at [ROOKIE_MAX], trimming the ratings in [keep] last. */
    fun rookieCap(lg: League, p: Player, keep: Set<Int> = emptySet()): Boolean {
        if (!cappedRookie(lg, p) || p.ovr <= ROOKIE_MAX) return false
        val idx = p.attrs.indices.filter { it !in keep }.ifEmpty { p.attrs.indices.toList() }
        var guard = 0
        while (p.ovr > ROOKIE_MAX && guard++ < 60) for (i in idx) { p.attrs[i] = (p.attrs[i] - 1).coerceAtLeast(25); if (p.ovr <= ROOKIE_MAX) break }
        return true
    }

    /** How strong each team's real roster is: its best eight count most. */
    private fun strength(team: Int): Double {
        val top = NbaData.PLAYERS.filter { it.team == team }.map { proOverall(it.ovr) }.sortedDescending().take(8)
        return top.mapIndexed { i, o -> o * (1.0 - i * 0.07) }.sum() / top.indices.sumOf { 1.0 - it * 0.07 }
    }

    /**
     * An NBA coaching career: the real league of [first] (the season it ends, 1987 to 2026; later means
     * today's league), coaching the team in [slot]. The first season starts from training camp.
     */
    /** The teams of [first]'s league (the season it ends), dressed in that season's names and colors. */
    fun seasonTeams(first: Int): List<Team> {
        val today = first > DATA_SEASON || !RealSeasons.has(first)
        val startList = if (today) null else EraData.teams(first)
        val teams = mutableListOf<Team>()
        for (f in TEAMS) {
            val fr = startList?.firstOrNull { it.slot == f.abbr }
            if (startList != null && fr == null) continue
            val t = Team(teams.size, f.city, f.name, f.abbr, f.conf, f.market, f.primary, f.secondary, LOGO)
            t.arena = 17_500 + (f.market - 55) * 80
            t.fans = 55.0 + (f.market - 55) * 0.6
            t.legacy = f.market.toDouble()
            t.buzz = 45.0
            t.slot = f.abbr
            if (fr != null) Franchises.dress(t, fr)
            teams += t
        }
        return teams
    }

    fun coachLeague(first: Int, slot: String, coachFirst: String, coachLast: String): League {
        val today = first > DATA_SEASON || !RealSeasons.has(first)
        val era = if (today) Era.TODAY else NbaCoach.eraFor(first)
        val startList = if (today) null else EraData.teams(first)
        val confs = listOf(
            Conference(0, "Eastern Conference", "EAST", 0, mutableListOf()),
            Conference(1, "Western Conference", "WEST", 0, mutableListOf()),
        )
        val teams = seasonTeams(first).toMutableList()
        for (t in teams) confs[t.confId].teamIds += t.id
        val lg = League(
            season = (if (today) DATA_SEASON + 1 else first) - 1, teams = teams, players = mutableListOf(), coaches = mutableListOf(), confs = confs,
            userTeamId = 0, userCoachId = 0, nextPlayerId = 1, nextCoachId = 1,
        )
        lg.mode = GameMode.COACH
        lg.level = Level.PRO
        lg.heroId = -1
        lg.proState = ProState(era = era.name, realTeams = startList != null)
        for (t in teams) {
            val c = Coach(lg.nextCoachId++, Rng.pick(Names.first), Rng.pick(Names.last), Rng.range(38, 66), t.id,
                Rng.range(55, 92), Rng.range(55, 92), Rng.range(40, 80), Rng.range(50, 90), Rng.int(0, 1_000_000))
            lg.coaches += c
            t.coachId = c.id
        }
        if (today) {
            for (t in teams) for (np in NbaData.PLAYERS.filter { it.team == t.id }.sortedByDescending { it.ovr }.take(REAL_ROSTER)) makeReal(lg, np, t)
        } else fillReal(lg, first)
        for (t in lg.teams) {
            trim(lg, t)
            while (t.roster.size < ROSTER) makePro(lg, scarcest(lg, t), Rng.range(80, 85), Rng.range(23, 31), t)
            Gameplan.autoAllocate(lg, t)
        }
        lg.reindex()
        rateTeams(lg)
        val u = lg.teams.firstOrNull { it.slot == slot } ?: lg.teams.first()
        lg.userTeamId = u.id
        val c = lg.coachOf(u)
        c.first = coachFirst
        c.last = coachLast
        c.isUser = true
        c.age = 42
        c.security = 70
        lg.userCoachId = c.id
        lg.phase = Phase.OFFSEASON
        lg.day = 0
        lg.proState!!.step = ProStep.SUMMER
        lg.news += News(lg.season, 0, "${c.name} is the new head coach of the ${u.name}", true)
        RecordBook.seed(lg)
        Payroll.sign(lg)
        Picks.ensure(lg)
        Longevity.assignReal(lg)
        return lg
    }

    /**
     * Draft Night, before the pro league exists: the order the pro teams pick in. Weak teams pick
     * first, and the league is later built so that holds. Every pick gets its pro team's name.
     */
    fun planDraft(lg: League) {
        val r = lg.road ?: return
        // An era career drafts into that year's league (27 teams in 1989...), in a shuffled order.
        val eraList = if (lg.era != Era.TODAY.name) EraData.teams(lg.season + 1) else null
        if (r.proOrder.isEmpty()) {
            // Weakest rosters first, give or take; a lottery shuffles the four worst.
            val order = if (eraList != null) eraList.indices.shuffled(Rng.r).toMutableList()
                else TEAMS.indices.sortedBy { strength(it) + Rng.gauss() * 0.6 }.toMutableList()
            val lottery = order.take(4).shuffled(Rng.r)
            for (i in 0 until 4) order[i] = lottery[i]
            r.proOrder.addAll(order)
        }
        for (d in lg.draftHistory) if (d.season == lg.season) {
            val k = r.proOrder[(d.pick - 1) % r.proOrder.size]
            d.proTeam = eraList?.getOrNull(k)?.let { "${it.city} ${it.name}" } ?: teamName(k % TEAMS.size)
        }
    }

    /** Builds the pro league from the college league once the user's player has been drafted (or signed undrafted), in [era]. */
    fun create(college: League, era: Era = Era.TODAY): League {
        val r = college.road!!
        val hero = college.hero!!
        if (r.proOrder.isEmpty()) planDraft(college)
        val order = r.proOrder.toList()
        val confs = listOf(
            Conference(0, "Eastern Conference", "EAST", 0, mutableListOf()),
            Conference(1, "Western Conference", "WEST", 0, mutableListOf()),
        )
        // An era career in college joins the league the year he's drafted, after the years he spent in school.
        val inEra = era != Era.TODAY && college.era == era.name && college.season >= era.year
        // A past era has only the teams that existed in the season he arrives (23 in 1985-86, 25 in 1988-89),
        // under their names then (the Bobcats in 2006-07, the Hornets and Pelicans by 2016-17).
        val startList = if (era != Era.TODAY) EraData.teams(firstSeason(college, era, inEra)) ?: EraData.teams(era.year) else null
        val teams = mutableListOf<Team>()
        for (f in TEAMS) {
            val fr = startList?.firstOrNull { it.slot == f.abbr }
            if (startList != null && fr == null) continue
            // A past era uses the franchise's old name where it had one (the Seattle SuperSonics, the New Jersey Nets...).
            val club = if (startList != null) null else Eras.club(era, f.abbr)
            val t = Team(teams.size, club?.city ?: f.city, club?.name ?: f.name, club?.abbr ?: f.abbr, f.conf, f.market,
                club?.primary ?: f.primary, club?.secondary ?: f.secondary, if (club != null) ERA_BADGE else LOGO)
            t.arena = 17_500 + (f.market - 55) * 80
            t.fans = 55.0 + (f.market - 55) * 0.6
            t.legacy = f.market.toDouble()
            t.buzz = 45.0
            t.slot = f.abbr
            if (fr != null) Franchises.dress(t, fr)
            confs[t.confId].teamIds += t.id
            teams += t
        }
        val lg = League(
            // A past era starts in its own year: the first season is the era's.
            season = if (era == Era.TODAY || inEra) college.season else era.year - 1, teams = teams, players = mutableListOf(), coaches = mutableListOf(), confs = confs,
            userTeamId = 0, userCoachId = 0, nextPlayerId = college.nextPlayerId + 1, nextCoachId = 1,
        )
        lg.mode = GameMode.PLAYER
        lg.level = Level.PRO
        lg.heroId = college.heroId
        lg.road = r
        lg.proState = ProState(era = era.name, realTeams = startList != null)
        for (t in teams) {
            val c = Coach(lg.nextCoachId++, Rng.pick(Names.first), Rng.pick(Names.last), Rng.range(38, 66), t.id,
                Rng.range(55, 92), Rng.range(55, 92), Rng.range(40, 80), Rng.range(50, 90), Rng.int(0, 1_000_000))
            lg.coaches += c
            t.coachId = c.id
        }
        if (era == Era.TODAY) {
            // The real rosters: each team's best players, by the roster data.
            for (t in teams) {
                for (np in NbaData.PLAYERS.filter { it.team == t.id }.sortedByDescending { it.ovr }.take(REAL_ROSTER)) makeReal(lg, np, t)
            }
        } else if (RealSeasons.has(firstSeason(college, era, inEra))) {
            // The real rosters of the season he arrives (an era career), or of the era's first season: every
            // player on the team he really opened that season with. His college classmates come in below.
            val heroClass = college.draftHistory.filter { it.season == college.season }.mapNotNull { college.anyPlayer(it.pid)?.name }.toSet()
            val stillInSchool = college.players.filter { it.teamId >= 0 }.map { it.name }.toSet()
            fillReal(lg, firstSeason(college, era, inEra), heroClass + stillInSchool + hero.name)
        } else {
            // The era's stars on their real teams, and generated players around them. Clubs that didn't exist yet are all generated.
            val stars = Eras.stars(era)
            for (t in teams) {
                for (s in stars[t.slot].orEmpty()) makeStar(lg, s, t)
                // About as deep as today's real rosters, so the stars carry their teams without taking every shot.
                val bench = if (startList == null && Eras.expansion(era, t.slot)) listOf(90, 89, 88, 87, 86, 86, 85, 85, 84, 83, 83, 82, 81, 80)
                    else listOf(88, 87, 86, 86, 85, 85, 84, 84, 83, 82, 82, 81)
                var k = 0
                while (t.roster.size < REAL_ROSTER) {
                    makePro(lg, scarcest(lg, t), bench[k.coerceAtMost(bench.size - 1)] + Rng.int(-1, 1), Rng.range(22, 33), t)
                    k++
                }
            }
        }
        lg.reindex()
        // The real rosters are the 2025-26 league. The years the user spent in college pass for them too:
        // everyone ages, develops or declines, the oldest retire, and young draftees take the open spots.
        if (era == Era.TODAY) repeat((college.season + 1 - DATA_SEASON).coerceIn(0, 8)) { pastYear(lg) }
        // An era career: the years he spent in college pass for the pros too, with that year's teams and real draft class
        // (the players still in college, or drafted with him, come in below as his classmates instead).
        val heroClass = college.draftHistory.filter { it.season == college.season }.mapNotNull { college.anyPlayer(it.pid)?.name }.toSet()
        val stillInSchool = college.players.filter { it.teamId >= 0 }.map { it.name }.toSet()
        if (inEra && !RealSeasons.has(firstSeason(college, era, inEra))) for (y in era.year..college.season) pastYear(lg, y, heroClass + stillInSchool, draft = y < college.season)
        // Picks go by the order planned on Draft Night, to the team in that franchise's place.
        val eraList = if (inEra) EraData.teams(college.season + 1) else null
        fun teamFor(pick: Int): Team {
            val k = order[(pick - 1) % order.size]
            val slot = if (eraList != null) eraList.getOrNull(k)?.slot else TEAMS.getOrNull(k)?.abbr
            return lg.teams.firstOrNull { it.slot == slot } ?: lg.teams[k % lg.teams.size]
        }
        // The real draft class arrives: the user's classmates, the user's player, maybe his rival.
        val picks = college.draftHistory.filter { it.season == college.season }.sortedBy { it.pick }
        for (d in picks) {
            val p = college.anyPlayer(d.pid) ?: continue
            if (lg.playerOrNull(p.id) != null) continue
            joinPro(lg, p, teamFor(d.pick), d.cls)
        }
        if (lg.playerOrNull(hero.id) == null) {
            // Undrafted: a two-way deal with one of the weaker teams.
            val t = teamFor(Rng.int(1, 10))
            joinPro(lg, hero, t, hero.year)
        }
        importRival(college, lg, order)
        for (t in lg.teams) {
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
        r.contract = Contract(t.id, ProCareer.rookieSalary(lg, if (r.undrafted) 0 else pick), ProCareer.rookieYears(if (r.undrafted) 0 else pick),
            if (r.undrafted) "Two-way" else if (pick <= 30) "Rookie scale" else "Second-round", lg.season)
        lg.userTeamId = t.id
        lg.userCoachId = t.coachId
        lg.phase = Phase.OFFSEASON
        lg.day = 0
        lg.proState!!.step = ProStep.FREE_AGENCY
        lg.news += News(lg.season, 0, "${h.name} signs with the ${t.name}: ${r.contract!!.years} years, ${Road.dollars(r.contract!!.salary)} a season", true)
        RecordBook.seed(lg)
        // Every pro gets a deal, so payrolls are real from day one, and every team owns its picks.
        Payroll.sign(lg)
        Picks.ensure(lg)
        Longevity.assignReal(lg)
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
        // Ages are tracked through college; older saves start from his class.
        if (p.age !in 18..30) p.age = 18 + collegeYears.coerceIn(1, 5)
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

    /** An era star: his name, position and age, ratings built to his overall (no photo: the headshots are today's). */
    private fun makeStar(lg: League, s: Eras.Star, t: Team): Player {
        val p = makePro(lg, s.pos, proOverall(s.ovr), s.age, t)
        val parts = s.name.split(" ", limit = 2)
        p.first = parts[0]
        p.last = parts.getOrElse(1) { "" }
        p.country = null
        // His real strengths: from his 2K card, or from his numbers that season. Then the play style that fits them.
        val shapeFrom = s.attrs ?: if (s.ppg > 0) EraData.profile(s.pos, s.ppg, s.rpg, s.apg) else null
        shapeFrom?.let { EraData.shape(p, it, proOverall(s.ovr)) }
        p.style = EraData.styleFor(p)
        p.focus = p.style
        p.pot = maxOf(p.pot, p.ovr)
        if (s.photo.isNotEmpty()) p.photo = s.photo
        return p
    }

    /** The first pro season of a league built from [college]: the season after his last (an era career), or the era's own. */
    private fun firstSeason(college: League, era: Era, inEra: Boolean) = if (inEra) college.season + 1 else era.year

    /** A real player of [season]'s league: his real 2K card when one is near that season, otherwise rated from his numbers. */
    private val cards: Map<String, EraData.Star> by lazy {
        Era.entries.flatMap { EraData.stars(it) }.associateBy { it.id }
    }

    /**
     * The real rosters of [season] (the year it ends): each player on the team he opened that season with,
     * up to [REAL_ROSTER] per team by minutes, rated from his 2K card when one is within a season (or his
     * numbers that season), with his real ceiling when he was drafted in the data, his photo when there's
     * one, and his real seasons before this one. Players named in [skip] arrive another way.
     */
    fun fillReal(lg: League, season: Int, skip: Set<String> = emptySet()) {
        val bySlot = lg.teams.associateBy { it.slot }
        val photos = Era.entries.flatMap { EraData.stars(it) }.filter { it.photo.isNotEmpty() }.associate { it.id to it.photo }
        val lines = RealSeasons.season(season).filter { it.name !in skip }
        for ((slot, group) in lines.groupBy { RealSeasons.slotOf(it.first) }) {
            val t = bySlot[slot] ?: continue
            for (l in group.sortedByDescending { it.mpg * it.g }.take(REAL_ROSTER)) {
                val card = cards[l.id]?.takeIf { c -> kotlin.math.abs((if (c.era == Era.TODAY) 2027 else c.era.year) - season) <= 1 }
                val today = if (season >= 2026) EraData.today(l.name) else null
                val ovr = when {
                    today != null -> Ratings.proInternal(today.ovr)
                    card != null -> card.ovr
                    else -> Ratings.proInternal(RealSeasons.rating2k(l))
                }
                val pos = card?.pos ?: RealSeasons.pos(l)
                val p = makePro(lg, pos, proOverall(ovr), l.age, t)
                val parts = l.name.split(" ", limit = 2)
                p.first = parts[0]
                p.last = parts.getOrElse(1) { "" }
                p.country = null
                EraData.shape(p, today?.attrs ?: card?.attrs ?: RealSeasons.profile(l), proOverall(ovr))
                p.style = EraData.styleFor(p)
                p.focus = p.style
                // Young players grow toward what they became, when the draft data knows it.
                val peak = EraData.pickOf(l.id)?.peak ?: 0
                p.pot = when {
                    today != null -> maxOf(p.ovr, Ratings.proInternal(today.pot))
                    peak > p.ovr && l.age <= 27 -> peak.coerceAtMost(99)
                    else -> (p.ovr + (25 - l.age).coerceAtLeast(0) * 1.2).roundToInt().coerceIn(p.ovr, 99)
                }
                (photos[l.id] ?: NbaData.PLAYERS.firstOrNull { it.name == l.name }?.photo)?.let { p.photo = it }
                val past = RealSeasons.careerBefore(l.id, season)
                p.proYears = past.size
                RealSeasons.addCareer(p, past)
                EraData.pickOf(l.id)?.let { pk -> p.highSchool = pk.college.ifEmpty { p.highSchool }; p.awards += "${pk.year} Draft #${pk.pick}" }
            }
        }
        // Short benches get generated players.
        for (t in lg.teams) while (t.roster.size < ROSTER) makePro(lg, scarcest(lg, t), Rng.range(80, 85), Rng.range(23, 31), t)
        lg.reindex()
    }

    /** The season of the real roster data (2025-26). */
    const val DATA_SEASON = 2026

    /** A year in the league before the user arrives: aging, growth or decline, retirements, and draftees. */
    private fun pastYear(lg: League, year: Int? = null, skip: Set<String> = emptySet(), draft: Boolean = false) {
        for (p in lg.players) p.age++
        for (t in lg.teams) {
            val dev = Coaching.devOf(lg.coachOf(t))
            for (pid in t.roster.toList()) {
                val p = lg.player(pid)
                p.proYears++
                val go = Longevity.retires(p)
                if (go) { AllTime.freeze(lg, p); t.roster.remove(pid); lg.removePlayer(p); continue }
                ProCareer.develop(p, dev)
            }
            if (year == null) while (t.roster.size < REAL_ROSTER) makePro(lg, scarcest(lg, t), Rng.range(76, 84), Rng.range(20, 23), t).proYears = 0
        }
        if (year != null) {
            // Next season's teams (expansion teams draft their rosters), then that summer's real draft class.
            Franchises.apply(lg, year + 1)
            val firstRound = mutableSetOf<Int>()
            if (draft) {
                val order = lg.teams.sortedBy { t -> lg.rosterOf(t).map { it.ovr }.sortedDescending().take(8).average() }
                val names = lg.players.map { it.name }.toSet()
                for (pk in EraData.draft(year).filter { it.pick <= 2 * lg.teams.size && it.name !in skip && it.name !in names }) {
                    val t = order[(pk.pick - 1) % order.size]
                    val target = draftOverall(pk.pick)
                    makePro(lg, pk.pos ?: scarcest(lg, t), target, pk.age, t).also {
                        it.proYears = 0
                        Payroll.rookie(lg, it, pk.pick)
                        it.awards += "$year Draft #${pk.pick}"
                        it.pot = (it.ovr + (24 - it.age) * 2 + Rng.range(0, 4)).coerceIn(it.ovr, 99)
                        EraData.apply(it, pk)
                        if (pk.pick <= lg.teams.size) firstRound += it.id
                    }
                }
            }
            for (t in lg.teams) {
                // Teams keep their first-round picks.
                trim(lg, t, firstRound)
                while (t.roster.size < REAL_ROSTER) makePro(lg, scarcest(lg, t), Rng.range(76, 84), Rng.range(20, 23), t).proYears = 0
            }
        }
        lg.reindex()
    }

    /** [years] seasons pass for a league before the user arrives: aging, growth, retirements and new draftees. */
    fun ageForward(lg: League, years: Int) {
        repeat(years.coerceIn(0, 80)) {
            for (p in lg.players) p.age++
            for (t in lg.teams) {
                val dev = Coaching.devOf(lg.coachOf(t))
                for (pid in t.roster.toList()) {
                    val p = lg.player(pid)
                    p.proYears++
                    val go = Longevity.retires(p)
                    if (go) { AllTime.freeze(lg, p); t.roster.remove(pid); lg.removePlayer(p); continue }
                    ProCareer.develop(p, dev)
                }
            }
            // A full two-round draft, weakest rosters first, with real ceilings: tomorrow's stars.
            val order = lg.teams.sortedBy { t -> lg.rosterOf(t).map { it.ovr }.sortedDescending().take(8).average() }
            val firstRound = mutableSetOf<Int>()
            for (pick in 1..minOf(60, 2 * lg.teams.size)) {
                val t = order[(pick - 1) % order.size]
                val age = Rng.range(19, 22)
                val p = makePro(lg, scarcest(lg, t), draftOverall(pick), age, t)
                p.proYears = 0
                p.pot = (p.ovr + (23 - age) * 2 + Rng.range(0, 3)).coerceIn(p.ovr, 99)
                if (pick <= lg.teams.size) firstRound += p.id
            }
            for (t in lg.teams) {
                trim(lg, t, firstRound)
                while (t.roster.size < REAL_ROSTER) makePro(lg, scarcest(lg, t), Rng.range(76, 82), Rng.range(22, 27), t)
            }
            lg.reindex()
        }
        lg.season += years.coerceIn(0, 80)
        for (t in lg.teams) {
            trim(lg, t)
            while (t.roster.size < ROSTER) makePro(lg, scarcest(lg, t), Rng.range(80, 85), Rng.range(23, 31), t)
            Gameplan.autoAllocate(lg, t)
        }
        lg.reindex()
        rateTeams(lg)
        Payroll.sign(lg)
    }

    /** The position a roster is thinnest at. */
    private fun scarcest(lg: League, t: Team): Pos = Pos.entries.minBy { pos -> lg.rosterOf(t).count { it.pos == pos } * 10 + Rng.int(0, 9) }

    /** A real player from the roster data: his name, number, position, age and photo; ratings built to his overall. */
    private fun makeReal(lg: League, np: NbaData.NbaPlayer, t: Team): Player {
        val p = makePro(lg, np.pos, proOverall(np.ovr), np.age, t)
        // His NBA 2K27 card: his strengths and weaknesses, and his ceiling.
        EraData.today(np.name)?.let { c ->
            c.attrs?.let { EraData.shape(p, it, proOverall(np.ovr)) }
            p.pot = maxOf(p.ovr, Ratings.proInternal(c.pot))
            p.style = EraData.styleFor(p)
            p.focus = p.style
        }
        // Most of today's players came into the league at 19 or 20. Their real seasons through 2025-26 are on their record.
        p.proYears = (np.age - 20).coerceIn(0, 22)
        RealSeasons.addCareer(p, RealSeasons.careerByName(np.name, DATA_SEASON + 1))
        val parts = np.name.split(" ", limit = 2)
        p.first = parts[0]
        p.last = parts.getOrElse(1) { "" }
        p.country = null
        p.photo = np.photo
        if (t.roster.none { it != p.id && lg.player(it).number == np.jersey }) p.number = np.jersey
        return p
    }

    /** A roster over the limit cuts its weakest players (never the user's player or his rival). */
    fun trim(lg: League, t: Team, protect: Set<Int> = emptySet()) {
        val keep = setOf(lg.heroId, lg.road?.rivalId ?: -1) + protect
        while (t.roster.size > MAX_ROSTER) {
            val cut = lg.rosterOf(t).filter { it.id !in keep }.minByOrNull { it.ovr - (30 - it.age).coerceAtLeast(0) * 0.3 } ?: break
            t.roster.remove(cut.id)
            lg.removePlayer(cut)
        }
    }

    /** A roster's strength: the top eight players, the best counting most. */
    fun teamValue(lg: League, t: Team): Double {
        val top = lg.rosterOf(t).map { it.ovr }.sortedDescending().take(8)
        if (top.isEmpty()) return 85.0
        return top.mapIndexed { i, o -> o * (1.0 - i * 0.07) }.sum() / top.indices.sumOf { 1.0 - it * 0.07 }
    }

    /**
     * Strength points per point of roster value. Calibrated against simulated seasons: at 28 the
     * projections bunched every team between 38 and 48 wins while real results spread 2.4 times wider.
     */
    const val ELO_PER_POINT = 72.0

    /** Team strength, for win probability and projections (it moves with every result during the season). */
    fun rateTeams(lg: League) {
        for (t in lg.teams) t.elo = 1500 + (teamValue(lg, t) - 91) * ELO_PER_POINT
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
        if (lg.teams.size != 30) { scheduleAny(lg); return }
        lg.proState?.games = GAMES
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
        // The NBA Cup draw, and its group games out of the first-half meetings. The Cup began in 2023: past eras don't have it.
        val cupOn = Eras.of(lg).cup
        val groups = if (cupOn) NbaCup.draw(lg) else emptyList()
        ps?.cupGroups?.apply { clear(); addAll(groups.map { it.toMutableList() }) }
        ps?.cup?.clear(); ps?.cupSeeds?.clear(); ps?.cupChampion = -1; ps?.cupMvp = ""; ps?.cupNote = ""
        ps?.cupQfDay = 0; ps?.cupSfDay = 0; ps?.cupFinalDay = 0
        val nights = if (cupOn) NbaCup.groupNights(groups).map { night -> night.map { (a, b) -> takeMeeting(rounds, a, b) } } else emptyList()
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
        val cupAt = if (cupOn) listOf(0.05, 0.09, 0.13, 0.17, 0.21).map(::at) else emptyList()
        fun play(day: Int, games: List<Pair<Int, Int>>, label: (Int) -> String = { "" }) {
            for ((h, a) in games) {
                val type = if (lg.team(h).confId == lg.team(a).confId) GameType.CONF else GameType.NONCONF
                lg.addGame(Game(lg.nextGameId++, day, h, a, false, type, label(h)))
            }
        }
        var day = 1
        for ((i, rd) in all.withIndex()) {
            cupAt.forEachIndexed { k, c -> if (c == i) { play(day, nights[k]) { h -> NbaCup.groupLabel(groups.indexOfFirst { h in it }) }; day++ } }
            if (cupOn && i == at(0.26)) { ps?.cupQfDay = day; day++ }
            if (cupOn && i == at(0.28)) { ps?.cupSfDay = day; day++ }
            if (cupOn && i == at(0.30)) { ps?.cupFinalDay = day; day++ }
            if (i == m / 2) { ps?.allStarDay = day; day++ }
            if (i == at(0.62)) { ps?.rivalryDay = day; play(day, rivalry) { "Rivalry Night" }; day++ }
            if (i == at(0.68)) ps?.deadline = day
            play(day, rd)
            day++
        }
        ps?.lastDay = day - 1
        NbaAwards.preseason(lg)
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
            // One game a day per series: a game the user already played live today counts, and no second one is made.
            if (s.done || s.gameIds.any { val g = lg.game(it); !g.played || g.day == lg.day }) continue
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
                NewsDesk.endOfDay(lg)
                // Every week the projection is re-run from the results so far.
                if (lg.day % 7 == 0) lg.proState?.let { ps -> ps.live = NbaPreviews.project(lg); ps.liveDay = lg.day }
                // When he coaches: teams call with offers every week until the deadline.
                if (NbaCoach.active(lg) && lg.day % 7 == 3) TradeFinder.makeOffers(lg)
                // Minor deals trickle through the season: a team thin at a spot swaps bench pieces with one that's deep.
                if (lg.day < Trades.deadlineDay(lg) - 4 && lg.day % 9 == 5 && Rng.chance(0.4)) Trades.minor(lg)
                NbaCup.endOfDay(lg)
                NbaAwards.endOfDay(lg)
                Rumors.daily(lg)
                if (lg.day == ProLeague.allStarDay(lg) - 1) allStar(lg)
                if (lg.day == Trades.deadlineDay(lg) - 1) Trades.deadline(lg)
                if (lg.day >= ProLeague.lastDay(lg)) { startPlayoffs(lg); return }
            }
            Phase.PLAYOFFS -> {
                updateSeries(lg)
                NewsDesk.endOfDay(lg)
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
            if (p.ovr >= p.pot || (ProLeague.cappedRookie(lg, p) && p.ovr >= ProLeague.ROOKIE_MAX)) continue
            Ratings.addXp(p, (3 * Ratings.devRate(p, Coaching.devOf(lg.coachOf(t)))).roundToInt())
            ProLeague.rookieCap(lg, p)
        }
        // Weekly rotation tune-ups, except a rotation the user set himself when he coaches the team.
        if (lg.day % 7 == 0) for (t in lg.teams) if (!(NbaCoach.active(lg) && t.id == lg.userTeamId && !t.plan.auto)) Gameplan.autoAllocate(lg, t)
    }

    fun standings(lg: League, conf: Int): List<Team> =
        lg.teams.filter { it.confId == conf }.sortedWith(compareByDescending<Team> { it.winPct }.thenByDescending { it.cw - it.cl }.thenByDescending { it.elo })

    fun seed(lg: League, teamId: Int): Int = lg.proState?.seeds?.get(teamId) ?: 0

    fun roundName(s: Series) = if (s.round == 0) "Play-In" else ProLeague.ROUND_NAMES[s.round - 1]

    /** Games won by [teamId] and by the other side in [s], counting games already played today. */
    fun seriesScore(lg: League, s: Series, teamId: Int): Pair<Int, Int> {
        val played = s.gameIds.map { lg.game(it) }.filter { it.played }
        val mine = played.count { it.winner() == teamId }
        return mine to played.size - mine
    }

    /** The series in a few words: "Nuggets lead 2-1", "Series tied 2-2", "Celtics win 4-1". */
    fun seriesStatus(lg: League, s: Series): String {
        val (h, l) = seriesScore(lg, s, s.hi)
        val hi = lg.team(s.hi).mascot
        val lo = lg.team(s.lo).mascot
        val need = s.bestOf / 2 + 1
        return when {
            s.bestOf == 1 -> if (h + l == 0) "One game" else "${if (h > l) hi else lo} win"
            h == need -> "$hi win $h-$l"
            l == need -> "$lo win $l-$h"
            h + l == 0 -> "Series starts"
            h == l -> "Series tied $h-$l"
            h > l -> "$hi lead $h-$l"
            else -> "$lo lead $l-$h"
        }
    }

    /** The next game in [s]: "Game 3 at Denver", or "" once it's over. Higher seeds host games 1, 2, 5 and 7. */
    fun nextGame(lg: League, s: Series): String {
        val (h, l) = seriesScore(lg, s, s.hi)
        val need = s.bestOf / 2 + 1
        if (h == need || l == need) return ""
        val n = h + l + 1
        val home = if (s.bestOf == 1 || n in listOf(1, 2, 5, 7)) s.hi else s.lo
        return if (s.bestOf == 1) "At ${lg.team(home).school}" else "Game $n at ${lg.team(home).school}"
    }

    /** A reporter's question after a playoff win, by where the series stands. */
    fun winQuestion(lg: League, g: Game, teamId: Int): String {
        val s = lg.proState?.series?.firstOrNull { g.id in it.gameIds } ?: return "You won. What's next?"
        if (s.bestOf == 1) return "You win the play-in game. Ready for what's next?"
        val (me, them) = seriesScore(lg, s, teamId)
        val need = s.bestOf / 2 + 1
        return when {
            me == need -> if (s.round == 4) "You're champions. What does this one mean?" else "You're through to the ${ProLeague.ROUND_NAMES[s.round]}. Who do you want next?"
            me == need - 1 -> "One more win and you're through. How do you close it out?"
            me > them -> "You're up $me-$them in the series. What's working?"
            me == them -> "Series tied $me-$me. What changed tonight?"
            else -> "You cut it to $me-$them. Can you take this series back?"
        }
    }

    /** A top-six seed's Round 1 opponent while the play-in is on: known for seeds 3 to 6, null for 1 and 2 (a play-in winner). */
    fun firstRoundOpponent(lg: League, teamId: Int): Team? {
        val ps = lg.proState ?: return null
        val seed = ps.seeds[teamId] ?: return null
        if (seed !in 3..6) return null
        val conf = lg.team(teamId).confId
        return ps.seeds.entries.firstOrNull { it.value == 9 - seed && lg.team(it.key).confId == conf }?.let { lg.team(it.key) }
    }

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
        ps.allStarStarters.clear()
        fun guard(p: Player) = p.pos == Pos.PG || p.pos == Pos.SG
        for (c in 0..1) {
            val pool = lg.teams.filter { it.confId == c }.flatMap { t -> lg.rosterOf(t).filter { it.stats.gp * 2 >= t.gp }.map { it to t } }
            // Starters: the fans (half the vote), players and media pick two guards and three frontcourt players.
            // Reserves: the coaches pick two guards, three frontcourt players and two wild cards.
            val fans = { p: Player -> if (p.id == lg.heroId && r != null) fanVote(r.followers) else 0.0 }
            val byFans = pool.sortedByDescending { (p, t) -> Awards.score(p, t) + fans(p) * 1.5 }
            val starters = byFans.filter { guard(it.first) }.take(2) + byFans.filter { !guard(it.first) }.take(3)
            val rest = pool.filter { pt -> starters.none { it === pt } }.sortedByDescending { (p, t) -> Awards.score(p, t) + fans(p) * 0.5 }
            val bench = (rest.filter { guard(it.first) }.take(2) + rest.filter { !guard(it.first) }.take(3)).toMutableList()
            bench += rest.filter { pt -> bench.none { it === pt } }.take(7 - bench.size)
            for (pt in starters) { ps.allStarStarters += pt.first.id; pt.first.awards += "${lg.season} All-Star starter"; NbaAwards.note(lg, "All-Star starter", pt.first, pt.second.id, conf = c) }
            for (pt in bench) NbaAwards.note(lg, "All-Star reserve", pt.first, pt.second.id, conf = c)
            for (pt in starters + bench) { picked += pt; ps.allStars += pt.first.id; pt.first.awards += "${lg.season} All-Star" }
        }
        val h = lg.playerOrNull(lg.heroId)
        val east = 160 + Rng.int(0, 26)
        val west = 160 + Rng.int(0, 26)
        val mvp = picked.sortedByDescending { (p, t) -> Awards.score(p, t) + Rng.gauss() * 6 }.first().first
        mvp.awards += "${lg.season} All-Star MVP"
        NbaAwards.note(lg, "All-Star MVP", mvp, mvp.teamId)
        if (h != null && h.id in ps.allStars && r != null) {
            Alerts.add(lg, "You're an All-Star", if (h.id in ps.allStarStarters) "The fans, players and media voted you a starter for the ${lg.season} All-Star Game." else "The coaches picked you as a reserve for the ${lg.season} All-Star Game.", "star")
            r.allStars++
            val pts = (8 + (h.ovr - 88) * 1.2 + Rng.gauss() * 5).roundToInt().coerceIn(2, 40)
            val gain = (60_000 + r.followers * 0.06 * Road.damp(r.followers)).roundToInt()
            r.followers += gain
            val starter = h.id in ps.allStarStarters
            ps.allStarNote = "You're an All-Star${if (starter) " starter" else ""}. East $east, West $west; you score $pts" +
                (if (mvp.id == h.id) " and take home All-Star MVP" else "") + ". +${Road.count(gain)} followers."
            lg.news += News(lg.season, lg.day, "${h.name} named an All-Star${if (starter) " starter" else ""}" + if (mvp.id == h.id) ", then wins All-Star MVP" else "", true)
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
        // Today: seeds 1 to 6 are in and seeds 7 to 10 play in for the last two spots.
        // Past eras: the top eight go straight in, with best-of-five first rounds before 2003.
        val era = Eras.of(lg)
        for (c in 0..1) {
            if (era.playIn) {
                val top = standings(lg, c).take(10)
                top.forEachIndexed { i, t -> ps.seeds[t.id] = i + 1 }
                ps.series += Series(0, c, 0, top[6].id, top[7].id, bestOf = 1)
                ps.series += Series(0, c, 1, top[8].id, top[9].id, bestOf = 1)
            } else {
                val top = standings(lg, c).take(8)
                top.forEachIndexed { i, t -> ps.seeds[t.id] = i + 1 }
                listOf(0 to 7, 3 to 4, 2 to 5, 1 to 6).forEachIndexed { slot, (a, b) -> ps.series += Series(1, c, slot, top[a].id, top[b].id, bestOf = era.firstRoundBestOf) }
            }
        }
        val u = lg.user
        val seed = ps.seeds[u.id] ?: 0
        lg.news += News(lg.season, lg.day,
            when {
                seed in 1..(if (era.playIn) 6 else 8) -> "The ${u.name} are in the playoffs as the No. $seed seed in the ${lg.conf(u.confId).name}"
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
            if (s.done && s.round == 3) NbaAwards.conferenceFinals(lg, s)
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
        val last = lastSeries(lg, teamId) ?: return if (lg.phase == Phase.REGULAR) "" else if (teamId in ps.seeds) "Playoffs" else "Missed playoffs"
        return if (last.done && last.winner != teamId) "Lost in the ${roundName(last)}" else roundName(last)
    }

    private fun give(lg: League, award: String, p: Player, t: Team, line: String = Awards.line(p)) = NbaAwards.give(lg, award, p, t, line)

    /**
     * Awards night at the end of the regular season: the MVP vote, Rookie of the Year, Defensive
     * Player, Sixth Man, Most Improved, and the All-NBA, All-Defensive and All-Rookie teams.
     */
    private fun regularAwards(lg: League) {
        lg.seasonAwards.clear()
        val all = Awards.ladder(lg)
        if (all.isEmpty()) return
        val r = lg.road
        val ps = lg.proState
        // The 65-game rule: MVP, Defensive Player, Sixth Man, Most Improved and the All-NBA and
        // All-Defensive teams need 65 games (scaled for shorter seasons). Rookie honors don't.
        val minGames = if (Eras.of(lg).gamesRule) (65.0 * ProLeague.seasonGames(lg) / 82).toInt() else 0
        // Rookie honors look at every rookie who played, not just the award-eligible.
        val rookies = lg.teams.flatMap { t -> lg.rosterOf(t).filter { it.proYears == 0 && it.stats.gp > 0 }.map { it to t } }
            .sortedByDescending { (p, t) -> Awards.score(p, t) }
        val ladder = all.filter { it.first.stats.gp >= minGames }.ifEmpty { all }
        val votes = AwardsNight.mvpVote(lg, ladder)
        ps?.mvpVotes?.apply { clear(); addAll(votes) }
        val mvp = votes.firstOrNull()?.let { v -> ladder.first { it.first.id == v.pid } } ?: ladder.first()
        mvp.let { (p, t) -> give(lg, "MVP", p, t); if (p.id == lg.heroId && r != null) r.mvps++ }
        rookies.firstOrNull()?.let { (p, t) -> give(lg, "Rookie of the Year", p, t) }
        val defense = ladder.take(60).sortedByDescending { Awards.defScore(it.first) }
        defense.firstOrNull()?.let { (p, t) -> give(lg, "Defensive Player of the Year", p, t) }
        ladder.firstOrNull { it.first.stats.gs * 2 < it.first.stats.gp }?.let { (p, t) -> give(lg, "Sixth Man of the Year", p, t) }
        AwardsNight.mostImproved(lg, ladder)?.let { (p, t) -> give(lg, "Most Improved Player", p, t) }
        NbaAwards.awardsNight(lg, ladder, mvp, defense, rookies)
        val mine = lg.seasonAwards.filter { it.pid == lg.heroId }.map { NbaAwards.label(it.award) }
        if (mine.isNotEmpty()) {
            lg.news += News(lg.season, lg.day, "${lg.hero?.name}: ${mine.joinToString(", ")}", true)
            Alerts.add(lg, if ("MVP" in mine) "You're the MVP" else "Awards night", "Your ${lg.season} honors: ${mine.joinToString(", ")}.", "award")
        }
    }

    private fun endSeason(lg: League) {
        val ps = lg.proState!!
        val champ = lg.team(ps.champion)
        val finals = ps.series.first { it.round == 4 }
        val runnerUp = if (finals.hi == champ.id) finals.lo else finals.hi
        // Finals MVP goes by the Finals themselves (the whole playoff run if box scores are missing).
        val bySeries = NbaAwards.seriesMvp(lg, finals)
        val fmvp = bySeries?.first ?: lg.rosterOf(champ).filter { it.tourney.gp > 0 }.maxByOrNull { it.tourney.ppg + it.tourney.rpg * 0.7 + it.tourney.apg * 0.9 + it.ovr * 0.05 }
        if (fmvp != null) give(lg, "Finals MVP", fmvp, champ, bySeries?.let { NbaAwards.seriesLine(it.second) } ?: Awards.line(fmvp))
        for (t in lg.teams) ps.lastWins[t.id] = t.w
        // Every team keeps its season (record, how it ended, its seed and coach), for its history and the next preview.
        for (t in lg.teams) if (t.history.none { it.season == lg.season }) t.history += TeamSeason(lg.season, t.w, t.l, t.cw, t.cl, resultFor(lg, t.id), 0, 0, ps.seeds[t.id] ?: 0, lg.coachOf(t).name)
        for (p in lg.rosterOf(champ)) p.awards += "${lg.season} Champion"
        val r = lg.road
        if (r != null && lg.userTeamId == champ.id && lg.playerOrNull(lg.heroId)?.teamId == champ.id) {
            r.rings++
            Alerts.add(lg, "Champions", "The ${champ.name} win the ${lg.season} title" + (if (fmvp?.id == lg.heroId) ", and you're the Finals MVP." else ". That's ring No. ${r.rings}."), "title")
        }
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
        // An era career: this season's teams (new names, moves, expansion teams).
        Franchises.apply(lg, lg.season)
        Payroll.newSeason(lg)
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
        for (p in lg.players) ProLeague.rookieCap(lg, p)
        ProLeague.rateTeams(lg)
        ProLeague.schedule(lg)
        lg.reindex()
        Road.sync(lg)
        ProCareer.paySalary(lg)
        val h = lg.hero
        if (h != null) lg.news += News(lg.season, 0, "${h.name} opens his ${ordinalWord(h.proYears + 1)} pro season with the ${lg.user.name}", true)
        // The preseason magazine, from 400 simulated seasons.
        val pv = NbaPreviews.build(lg)
        ps.preview = pv
        ps.live = emptyMap()
        ps.liveDay = 0
        if (NbaCoach.active(lg)) ps.expect = pv.proj[lg.userTeamId]?.w ?: (ProLeague.seasonGames(lg) / 2)
        NbaPreviews.ranked(lg, pv).firstOrNull()?.let { top ->
            lg.news += News(lg.season, 0, "The ${lg.season} season preview is out: the ${top.name} are the title favorites, and we project the ${lg.user.name} at ${pv.proj[lg.user.id]?.w ?: 0}-${pv.proj[lg.user.id]?.l ?: 0}")
        }
    }

    private fun ordinalWord(n: Int) = when (n) { 1 -> "first"; 2 -> "second"; 3 -> "third"; 4 -> "fourth"; 5 -> "fifth"; 6 -> "sixth"; 7 -> "seventh"; 8 -> "eighth"; 9 -> "ninth"; 10 -> "tenth"; else -> "${n}th" }
}

/** The user's pro career: contracts, salary and endorsements, the offseason, aging and retirement. */
object ProCareer {
    /** The 2025-26 salary cap, in thousands. */
    const val CAP = 154_647

    /** The cap in the league's era (past eras scale every salary by their cap). */
    fun cap(lg: League) = Eras.of(lg).cap

    /** Max salary by years in the league: 25% of the cap through six years, 30% for seven to nine, 35% from ten. */
    fun maxSalary(lg: League, proYears: Int) = (cap(lg) * when { proYears >= 10 -> 0.35; proYears >= 7 -> 0.30; else -> 0.25 }).roundToInt() / 10 * 10

    /** The league minimum by years in the league, 2025-26, scaled to the era. */
    private val MIN_SCALE = intArrayOf(1_273, 2_049, 2_296, 2_378, 2_461, 2_667, 2_872, 3_078, 3_283, 3_304, 3_634)
    fun minSalary(lg: League, proYears: Int) = ((MIN_SCALE[proYears.coerceIn(0, MIN_SCALE.size - 1)] * Eras.of(lg).money).roundToInt() / 10 * 10).coerceAtLeast(10)

    /**
     * The most [teamId] can pay him. A seven-to-nine-year player re-signing with his own team after
     * an MVP, a Defensive Player of the Year or an All-NBA season in the last three can get the
     * 35% supermax.
     */
    fun maxFor(lg: League, teamId: Int): Int {
        val h = lg.hero ?: return maxSalary(lg, 10)
        val recent = h.awards.filter { a -> a.substringBefore(" ").toIntOrNull()?.let { it >= lg.season - 2 } == true }.map { it.substringAfter(" ") }
        val honors = recent.any { it == "MVP" || it == "Defensive Player of the Year" || it.startsWith("All-NBA") }
        val supermax = Eras.of(lg).supermax && h.proYears in 7..9 && teamId == h.teamId && honors
        return maxSalary(lg, if (supermax) 10 else h.proYears)
    }

    /**
     * First-round picks get a four-year rookie-scale deal (No. 1 about $13.8M down to about $2.4M at
     * No. 30), second-rounders two years near the minimum, undrafted players a two-way at half the minimum.
     */
    fun rookieSalary(lg: League, pick: Int): Int = when {
        pick <= 0 -> minSalary(lg, 0) / 2 / 10 * 10
        pick <= 30 -> (13_830 * Eras.of(lg).money * Math.pow(0.94, (pick - 1).toDouble())).roundToInt() / 10 * 10
        else -> minSalary(lg, 0) + ((60 - pick) * 10 * Eras.of(lg).money).roundToInt() / 10 * 10
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
        return ((v * agentCut(lg) * Eras.of(lg).money).roundToInt() / 10 * 10).coerceIn(minSalary(lg, p.proYears), maxSalary(lg, p.proYears))
    }

    private fun agentCut(lg: League) = if (Road.has(lg, Staff.AGENT)) 1.15 else 1.0

    /** Endorsements: $2 a follower a year for the first two million, 50 cents after that. */
    fun endorsements(lg: League): Int {
        val f = (lg.road?.followers ?: 0).toDouble()
        val v = minOf(f, 2_000_000.0) / 1000 * 2.0 + (f - 2_000_000.0).coerceAtLeast(0.0) / 1000 * 0.5
        return (v * agentCut(lg) * Eras.of(lg).money).roundToInt()
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
        if (NbaCoach.active(lg)) {
            for (p in lg.players) if (p.teamId >= 0 || p.stats.gp > 0 || p.tourney.gp > 0) p.proYears++
            NbaCoach.seasonEnd(lg)
            return
        }
        val r = lg.road ?: return
        val h = lg.playerOrNull(lg.heroId) ?: return
        val t = lg.team(h.teamId)
        Leadership.seasonEnd(lg)
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
        // A year in the league counts for everyone on a roster, played or not.
        for (p in lg.players) if (p.teamId >= 0 || p.stats.gp > 0 || p.tourney.gp > 0) p.proYears++
    }

    /** Old enough to call it a career. */
    fun canRetire(lg: League) = age(lg) >= 30 && lg.road?.retired == false

    /** He can play into his early 40s if somebody still wants him. */
    const val MAX_AGE = 43

    // ---------- the offseason ----------

    /** Moves the pro offseason one step: review, draft, free agency, summer, then the new season. */
    fun next(lg: League) {
        val ps = lg.proState ?: return
        if (NbaCoach.active(lg)) { coachNext(lg); return }
        val r = lg.road ?: return
        if (r.ended) return
        when (ps.step) {
            ProStep.REVIEW -> {
                if (age(lg) >= MAX_AGE) { retire(lg, "Nobody plays forever. "); return }
                // Coaches and general managers on the hot seat.
                Staffing.offseason(lg)
                newYear(lg)
                ps.moved.clear()
                Trades.draftDay(lg)
                draft(lg)
                Picks.ensure(lg)
                Trades.summer(lg)
                StarRecruit.openMarket(lg)
                ps.step = ProStep.DRAFT
            }
            ProStep.DRAFT -> {
                // A player option on the last year: he decides before free agency opens.
                r.contract?.let { c -> if (c.option && c.years == 1) r.optionPending = true }
                if ((r.contract?.years ?: 0) <= 0) {
                    // Coming off a first-round rookie deal, he's a restricted free agent.
                    r.restricted = r.contract?.kind == "Rookie scale"
                    r.proOffers.clear()
                    r.proOffers += offers(lg)
                    if (r.proOffers.isEmpty()) { retire(lg, "Nobody called. "); return }
                    Rumors.freeAgency(lg)
                }
                // The rest of the league's free agents sign around him.
                Market.run(lg)
                ps.step = ProStep.FREE_AGENCY
            }
            ProStep.FREE_AGENCY -> {
                if (r.optionPending) optIn(lg)
                ps.freeStars.clear()
                if ((r.contract?.years ?: 0) <= 0) r.proOffers.firstOrNull()?.let { sign(lg, matchOfferSheet(lg, it)) }
                // The rosters start out current, so the league skips a summer of aging before its first season.
                if (ps.history.isNotEmpty()) summer(lg)
                buildAroundStar(lg)
                ps.step = ProStep.SUMMER
            }
            ProStep.SUMMER -> {
                // A trade request still open when camp opens gets settled with the first team that called.
                Trades.settleOpenRequest(lg)
                ProSeason.startSeason(lg)
            }
        }
    }

    /** The offseason when the user coaches the team: the owner's verdict, the draft, free agency, camp. */
    private fun coachNext(lg: League) {
        val ps = lg.proState!!
        if (ps.fired) return
        when (ps.step) {
            ProStep.REVIEW -> {
                Staffing.offseason(lg)
                newYear(lg)
                ps.moved.clear()
                Trades.draftDay(lg)
                draft(lg)
                Picks.ensure(lg)
                Trades.summer(lg)
                Market.prepareOffseasonContracts(lg)
                // His own free agents: the good ones are on the list to bring back.
                ps.userKeep.clear()
                ps.userKeep += NbaCoach.expiring(lg).filter { Trades.value(it) >= 1.5 || it.clause == "QO" }.map { it.id }
                ps.userOffers.clear()
                ps.step = ProStep.DRAFT
            }
            ProStep.DRAFT -> {
                Market.open(lg)
                ps.step = ProStep.FREE_AGENCY
                TradeFinder.makeOffers(lg)
            }
            ProStep.FREE_AGENCY -> {
                Market.finish(lg)
                if (ps.history.isNotEmpty()) summer(lg)
                ps.step = ProStep.SUMMER
            }
            ProStep.SUMMER -> ProSeason.startSeason(lg)
        }
    }

    /** A year older for everyone; some veterans retire. */
    private fun newYear(lg: League) {
        val ps = lg.proState!!
        val r = lg.road
        for (p in lg.players) p.age++
        for (t in lg.teams) for (pid in t.roster.toList()) {
            val p = lg.player(pid)
            if (p.id == lg.heroId) continue
            // Good players last: most stars play into their late 30s. Fading role players go sooner.
            val go = Longevity.retires(p)
            if (!go) continue
            AllTime.freeze(lg, p)
            t.roster.remove(pid)
            lg.removePlayer(p)
            if (r != null && p.id == r.rivalId) {
                r.rivalLeft = "retired in ${lg.season}"
                p.teamId = -1
                p.left = "Retired in ${lg.season}"
                lg.alumni += p
            }
            if (t.id == lg.userTeamId || p.awards.any { it.endsWith(" MVP") || it.endsWith("All-NBA 1st") }) ps.retiredThisSummer += "${p.name} (${p.age})"
        }
    }

    /** Sixty rookies, worst teams first. The rival arrives here if he stayed in college. */
    private fun draft(lg: League) {
        val ps = lg.proState!!
        val r = lg.road
        val byRecord = lg.teams.sortedWith(compareBy<Team> { it.winPct }.thenBy { it.elo }).map { it.id }
        // Lottery night: the 14 teams that missed the playoffs draw for the top four picks, worst odds-on.
        val inPlayoffs = ps.series.filter { it.round >= 1 }.flatMap { listOf(it.hi, it.lo) }.toSet()
        val lotteryTeams = byRecord.filter { it !in inPlayoffs }.take(14)
        val drawn = Lottery.draw(lotteryTeams, Eras.of(lg))
        ps.lotteryPre.apply { clear(); addAll(lotteryTeams) }
        ps.lotteryPicks.apply { clear(); addAll(drawn) }
        ps.lotterySeason = lg.season
        val order = (drawn + byRecord.filter { it !in drawn }).toMutableList()
        lg.news += News(lg.season, lg.day, "Lottery night: the ${lg.team(drawn.first()).name} win the No. 1 pick" +
            (if (lotteryTeams.indexOf(drawn.first()) > 2) ", jumping from No. ${lotteryTeams.indexOf(drawn.first()) + 1}" else ""), drawn.first() == lg.userTeamId)
        // Two rounds: 60 picks with 30 teams, fewer in a smaller league.
        val picks = minOf(60, 2 * lg.teams.size)
        val rookies = mutableListOf<Pair<Pos, Int>>()
        for (i in 0 until 60) rookies += Rng.pick(Pos.entries) to ProLeague.draftOverall(i + 1)
        rookies.sortByDescending { it.second }
        val rival = ps.pendingRival
        var rivalSlot = -1
        if (rival != null) {
            Ratings.offseasonGrowth(rival, 70)
            rivalSlot = rookies.indexOfFirst { it.second <= rival.ovr }.let { if (it < 0 || it >= picks) picks - 1 else it }
        }
        ps.draftedByUser.clear()
        // Past eras draft the real class of that year (1986 to 2026): names, colleges, ceilings and photos.
        val names = lg.players.map { it.name }.toSet()
        val real = if (Eras.of(lg) != Era.TODAY || lg.season <= ProLeague.DATA_SEASON) EraData.draft(lg.season).filter { it.name !in names }.associateBy { it.pick } else emptyMap()
        for (i in 0 until picks) {
            // A traded first-round pick goes to the team that owns it (unless protected and lands inside protection).
            val t = lg.team(if (i < order.size) Picks.resolveDraftOwner(lg, lg.season, order[i], i + 1) else order[i % order.size])
            val p = if (i == rivalSlot && rival != null) {
                ProLeague.joinPro(lg, rival, t, rival.year + 1)
                r?.rivalPick = i + 1
                r?.rivalDraft = lg.season
                r?.rivalLeft = "went No. ${i + 1} in the ${lg.season} draft"
                ps.pendingRival = null
                lg.news += News(lg.season, 0, "Your rival ${rival.name} goes No. ${i + 1} to the ${t.name}. He's coming for you.", true)
                rival
            } else {
                val (pos, target) = rookies[i]
                val pick = real[i + 1]
                ProLeague.makePro(lg, pick?.pos ?: pos, target, pick?.age ?: Rng.range(19, 22), t).also {
                    it.proYears = 0
                    Payroll.rookie(lg, it, i + 1)
                    it.awards += "${lg.season} Draft #${i + 1}"
                    // Rookies come in raw, with real upside.
                    it.pot = (it.ovr + (24 - it.age) * 2 + Rng.range(0, 4)).coerceIn(it.ovr, 99)
                    if (pick != null) EraData.apply(it, pick)
                }
            }
            if (t.id == lg.userTeamId) ps.draftedByUser += p.id
        }
        // Teams keep their first-round picks.
        val firstRound = lg.players.filter { p -> p.awards.any { a -> a.startsWith("${lg.season} Draft #") && (a.substringAfter("#").toIntOrNull() ?: 99) <= lg.teams.size } }.map { it.id }.toSet()
        for (t in lg.teams) {
            ProLeague.trim(lg, t, firstRound)
            while (t.roster.size < ProLeague.ROSTER) ProLeague.makePro(lg, Rng.pick(Pos.entries), Rng.range(84, 88), Rng.range(26, 33), t)
        }
        lg.reindex()
    }

    /** Teams that want him, with what they'd pay and where he'd fit. His own team always asks first. */
    fun offers(lg: League): List<ProOffer> {
        val h = lg.playerOrNull(lg.heroId) ?: return emptyList()
        val value = marketValue(lg, h)
        // Every team is in the market for a real player; below that, nobody calls.
        if (h.ovr < 77) return emptyList()
        val years = when { h.age <= 27 -> Rng.range(4, 5); h.age <= 30 -> 3; h.age <= 33 -> Rng.range(2, 3); h.age <= 36 -> 2; else -> 1 }
        val pool = listOf(lg.user) + lg.teams.filter { it.id != h.teamId }
        return pool.map { t ->
            val ahead = lg.rosterOf(t).count { it.id != h.id && it.pos == h.pos && it.ovr >= h.ovr }
            // Cap room or the mid-level exception; his own team can go over the cap to keep him.
            val (limit, how) = if (t.id == h.teamId) maxFor(lg, t.id) to "Bird rights" else Payroll.canOffer(lg, t)
            val top = minOf(maxFor(lg, t.id), limit).coerceAtLeast(minSalary(lg, h.proYears))
            // Teams that need him at his position pay more; teams set there offer less.
            val need = when (ahead) { 0 -> 1.0; 1 -> 0.9; else -> 0.78 }
            val pay = ((value * (if (t.id == h.teamId) 1.05 else (0.78 + Rng.d() * 0.22) * need)).roundToInt() / 10 * 10).coerceIn(minSalary(lg, h.proYears), top)
            // How far each team will stretch: a little more for his own team, never past the max.
            val max = ((pay * (1.04 + Rng.d() * 0.24 + if (t.id == h.teamId) 0.05 else 0.0)).roundToInt() / 10 * 10).coerceIn(pay, top)
            ProOffer(t.id, pay, years.coerceAtMost(maxYears(lg, t.id)), roleFor(ahead), ahead, t.winPct, max, note = if (pay >= top) how else "")
        }.sortedByDescending { it.salary }
    }

    /** Longest deal at his age. Only his own team can go to five years (Bird rights); a new team stops at four. */
    fun maxYears(lg: League, teamId: Int = lg.hero?.teamId ?: -1): Int {
        val byAge = lg.hero?.age?.let { when { it <= 27 -> 5; it <= 30 -> 4; it <= 33 -> 3; it <= 36 -> 2; else -> 1 } } ?: 3
        return if (teamId == lg.hero?.teamId) byAge else byAge.coerceAtMost(4)
    }

    /**
     * Restricted free agency: if he signs an offer sheet elsewhere, his team can match it and keep him.
     * The better he is, the likelier they match. Returns the offer he ends up on.
     */
    fun matchOfferSheet(lg: League, o: ProOffer): ProOffer {
        val r = lg.road ?: return o
        val h = lg.hero ?: return o
        if (!r.restricted || o.teamId == h.teamId) return o
        val chance = (0.35 + (h.ovr - 85) * 0.06).coerceIn(0.2, 0.9)
        if (!Rng.chance(chance)) {
            lg.news += News(lg.season, 0, "The ${lg.user.name} decline to match ${h.name}'s offer sheet from the ${lg.team(o.teamId).name}", true)
            return o
        }
        lg.news += News(lg.season, 0, "The ${lg.user.name} match the ${lg.team(o.teamId).name}' offer sheet: ${h.name} stays", true)
        return ProOffer(h.teamId, o.salary, o.years.coerceAtMost(maxYears(lg, h.teamId)), o.role, o.ahead, lg.user.winPct, o.max, note = "Matched")
    }

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
        val top = maxFor(lg, o.teamId)
        if (o.salary >= top) { o.note = "It's already a max offer."; return o.note }
        val ask = ((o.salary * (1 + pct)).roundToInt() / 10 * 10).coerceAtMost(top)
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
        val offer = r.proOffers.getOrNull(i) ?: return ""
        val y = years.coerceIn(1, maxYears(lg, offer.teamId))
        val wanted = lg.team(offer.teamId)
        // A restricted free agent's team can match the offer sheet.
        val o = matchOfferSheet(lg, ProOffer(offer.teamId, offer.salary, y, offer.role, offer.ahead, offer.winPct, offer.max))
        val matched = o.teamId != offer.teamId
        var pay = o.salary
        if (option && y >= 2) pay = (pay * 0.95).toInt() / 10 * 10
        val ringDeal = discount && !matched && contender(lg, o.teamId)
        val saved = if (ringDeal) pay - (pay * 0.85).toInt() / 10 * 10 else 0
        pay -= saved
        val t = lg.team(o.teamId)
        sign(lg, ProOffer(o.teamId, pay, o.years, o.role, o.ahead, o.winPct, o.max))
        r.contract?.option = option && o.years >= 2
        if (matched) return "The ${t.name} matched the ${wanted.name}' offer sheet. You stay, on the same terms."
        if (!ringDeal) return "You sign with the ${t.name}."
        // The money he left on the table buys a good veteran.
        val add = ProLeague.makePro(lg, Rng.pick(Pos.entries), (88 + saved / 2500).coerceAtMost(94), Rng.range(27, 31), t)
        ProLeague.trim(lg, t)
        Gameplan.autoAllocate(lg, t)
        lg.reindex()
        lg.news += News(lg.season, 0, "${lg.hero?.name} takes less to chase a ring; the ${t.name} use the room to sign ${add.name}", true)
        return "You took ${Road.dollars(saved)} less a season. The ${t.name} spent it on ${add.name} (${Ratings.shownPro(add.ovr)})."
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
            Leadership.newTeam(lg)
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
        val kind = if (o.salary >= maxSalary(lg, h.proYears) - 10) "Max" else if (o.salary <= minSalary(lg, h.proYears)) "Minimum" else "Veteran"
        r.contract = Contract(to.id, o.salary, o.years, kind, lg.season)
        r.proOffers.clear()
        r.restricted = false
        Road.sync(lg)
        lg.news += News(lg.season, 0,
            if (from.id == to.id) "${h.name} re-signs with the ${to.name}: ${o.years} years, ${Road.dollars(o.salary)} a season"
            else "${h.name} signs with the ${to.name}: ${o.years} years, ${Road.dollars(o.salary)} a season", true)
    }

    /**
     * A team with a star builds around him: if the rest of the roster lags the league, the front office
     * signs a good veteran for the weakest starting spot. Once a summer, from 90 overall.
     */
    fun buildAroundStar(lg: League) {
        val h = lg.playerOrNull(lg.heroId) ?: return
        if (h.teamId < 0 || h.ovr < 90 || lg.road?.retired == true) return
        val t = lg.team(h.teamId)
        fun cast(team: Team) = lg.rosterOf(team).filter { it.id != lg.heroId }.map { it.ovr }.sortedDescending().take(7).average()
        val all = lg.teams.map { cast(it) }.sorted()
        val median = all[all.size / 2]
        if (cast(t) >= median) return
        val weak = t.plan.slots.mapNotNull { lg.playerOrNull(it.starter) }.filter { it.id != h.id }.minByOrNull { it.ovr }?.pos ?: Rng.pick(Pos.entries)
        val ovr = (median + 3 + (h.ovr - 90) * 0.4).roundToInt().coerceIn(87, 93)
        val add = ProLeague.makePro(lg, weak, ovr, Rng.range(27, 31), t)
        ProLeague.trim(lg, t)
        Gameplan.autoAllocate(lg, t)
        lg.reindex()
        lg.news += News(lg.season, 0, "Building around ${h.name}: the ${t.name} sign ${add.name}, a ${Ratings.shownPro(add.ovr)}-rated ${add.pos}", true)
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
                // The user's player earns his growth until 30; after that he ages like everyone.
                if (p.id == lg.heroId && p.age <= 30) Earned.hero(lg, p, dev) else develop(p, dev, trainer)
                if (p.id == lg.heroId && p.age >= 29) lg.news += News(lg.season, 0, Longevity.hint(p), true, -1, "", "", "", t.id, p.id)
            }
        }
        lg.reindex()
    }

    /** One summer of growth or decline for [p]. */
    fun develop(p: Player, coachDev: Int, trainer: Boolean = false) {
        // Pros grow slower than college players, mostly while young: a fifth to a third of the gap to
        // their ceiling a summer at 22 and under, less each year after, so the league doesn't fill up with 90s.
        val share = when { p.age <= 22 -> 0.22 + Rng.d() * 0.12; p.age <= 25 -> 0.14 + Rng.d() * 0.1; else -> 0.06 + Rng.d() * 0.08 }
        // His body's age (hidden longevity): a durable player declines later, a fragile one from 28.
        val body = Longevity.bodyAge(p)
        val declining = p.age >= 28 && body > 30
        when {
            !declining && p.age <= 27 -> Ratings.offseasonGrowth(p, coachDev, share)
            !declining -> if (p.pot > p.ovr) Ratings.offseasonGrowth(p, coachDev, share) else Ratings.offseasonGrowth(p.also { it.pot = it.ovr }, coachDev, share)
            else -> {
                // A slow slide once his body is past 30, faster in its late 30s.
                var drop = (body - 30) * 0.55 + Rng.d() * 0.8
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

    /** Hall of Fame case: titles, MVPs, All-NBA teams and All-Star games, plus longevity. */
    fun hofScore(lg: League): Double {
        val r = lg.road ?: return 0.0
        val h = lg.hero ?: return 0.0
        val allLeague = h.awards.count { it.contains("All-NBA 1st") } * 3.0 + h.awards.count { it.contains("All-NBA 2nd") || it.contains("All-NBA 3rd") } * 1.5
        return r.rings * 3.0 + r.mvps * 6.0 + allLeague + r.allStars * 1.5 + h.awards.count { it.substringAfter(" ") == "Finals MVP" } * 3.0 + h.proYears * 0.4
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
        // NBA teams call about their bench; colleges call too (the ending screen lists both).
        lg.proState?.jobOffers?.let { it.clear(); it += NbaCoach.retiredOffers(lg) }
    }
}

/** Picks the right season loop for the league. */
object Sim {
    fun playDay(lg: League) {
        if (lg.pro) ProSeason.playDay(lg) else Season.playDay(lg)
        Reactions.pass(lg)
    }
}
