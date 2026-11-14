package com.nh.hoopscoach.game

import kotlin.math.exp
import kotlin.math.roundToInt

/** Big playoff nights: Game 7s, elimination games and close-outs, and his playoff highlight reel. */
object BigNights {
    /** What's riding on playoff game [g] for [teamId], or "" for an ordinary night. */
    fun stakes(lg: League, g: Game, teamId: Int = lg.userTeamId): String {
        if (!lg.pro) return ""
        if (g.type == GameType.CUP) return if (g.label.endsWith("Final")) "NBA Cup Final" else "NBA Cup knockout"
        if (g.type != GameType.PLAYOFF) return ""
        val s = lg.proState?.series?.firstOrNull { g.id in it.gameIds } ?: return ""
        if (s.bestOf == 1) return if (s.slot == 0 && s.involves(teamId)) "Win and you're the 7 seed" else if (s.involves(teamId)) "Play-in: win or go home" else ""
        val n = s.gameIds.indexOf(g.id) + 1
        val before = s.gameIds.take(n - 1).map { lg.game(it) }.filter { it.played }
        val mine = before.count { it.winner() == teamId }
        val theirs = before.size - mine
        val finals = s.round == 4
        // Best of seven, or best of five in an older era's first round.
        val need = s.bestOf / 2 + 1
        return when {
            n == s.bestOf -> if (finals) "Game ${s.bestOf} of the Finals" else "Game ${s.bestOf}"
            !s.involves(teamId) -> if (before.count { it.winner() == s.hi } == need - 1 || before.count { it.winner() == s.lo } == need - 1) "Elimination game" else ""
            mine == need - 1 -> if (finals) "Win and you're champions" else "Close-out game"
            theirs == need - 1 -> if (finals) "Win or the title is theirs" else "Win or go home"
            else -> ""
        }
    }

    /** After a playoff game: a big night or a big line goes in his reel, and big nights make the news. */
    fun afterGame(lg: League, g: Game, line: GameLogEntry, won: Boolean, winner: Boolean, stakes: String) {
        val r = lg.road ?: return
        val h = lg.hero ?: return
        if (g.type != GameType.PLAYOFF || line.min <= 0) return
        val big = stakes.isNotEmpty()
        if (!big && line.pts < 30 && !winner) return
        val t = lg.team(h.teamId)
        val opp = lg.team(g.opp(t.id))
        val tags = listOfNotNull(stakes.takeIf { big }, if (winner) "Game-winner" else null, if (line.pts >= 40) "40-point night" else null)
        r.reel += ReelEntry(
            lg.season, g.label.ifEmpty { "Playoffs" },
            "${line.pts} pts, ${line.reb} reb, ${line.ast} ast · ${if (won) "W" else "L"} ${line.score} ${line.site} ${opp.name}" +
                if (tags.isNotEmpty()) " · " + tags.joinToString(" · ") else "",
            big,
        )
        if (r.reel.size > 60) r.reel.removeAt(0)
        if (big && won) lg.news += News(lg.season, lg.day, "${stakes}: ${h.name} scores ${line.pts} as the ${t.name} beat the ${opp.name}", true, g.id)
        else if (big) lg.news += News(lg.season, lg.day, "${stakes}: the ${opp.name} beat the ${t.name} despite ${line.pts} from ${h.name}", true, g.id)
    }
}

/**
 * The records book: real single-game, season and career records, with the real holders. Anyone in
 * this league can break them. Career totals count each 58-game season as a full 83, like the
 * all-time list. Breaking one adds a "Record" honor (3 legacy points).
 */
object RecordBook {
    private val STATS = listOf("pts", "reb", "ast", "3pm", "stl", "blk")
    private val NAMES = listOf("points", "rebounds", "assists", "threes", "steals", "blocks")

    fun seed(lg: League) {
        val ps = lg.proState ?: return
        if (ps.records.isNotEmpty()) return
        ps.records += listOf(
            BookRecord("game_pts", "Points in a game", "Game", 100.0, "Wilt Chamberlain", 1962),
            BookRecord("game_reb", "Rebounds in a game", "Game", 55.0, "Wilt Chamberlain", 1960),
            BookRecord("game_ast", "Assists in a game", "Game", 30.0, "Scott Skiles", 1990),
            BookRecord("game_3pm", "Threes in a game", "Game", 14.0, "Klay Thompson", 2018),
            BookRecord("game_stl", "Steals in a game", "Game", 11.0, "Kendall Gill", 1999),
            BookRecord("game_blk", "Blocks in a game", "Game", 17.0, "Elmore Smith", 1973),
            BookRecord("season_pts", "Points per game, season", "Season", 50.4, "Wilt Chamberlain", 1962, decimals = 1),
            BookRecord("season_reb", "Rebounds per game, season", "Season", 27.2, "Wilt Chamberlain", 1961, decimals = 1),
            BookRecord("season_ast", "Assists per game, season", "Season", 14.5, "John Stockton", 1990, decimals = 1),
            BookRecord("season_3pm", "Threes per game, season", "Season", 5.1, "Stephen Curry", 2016, decimals = 1),
            BookRecord("season_stl", "Steals per game, season", "Season", 3.7, "Alvin Robertson", 1986, decimals = 1),
            BookRecord("season_blk", "Blocks per game, season", "Season", 5.6, "Mark Eaton", 1985, decimals = 1),
            BookRecord("career_pts", "Career points", "Career", 43_000.0, "LeBron James", 2026, base = 43_000.0),
            BookRecord("career_reb", "Career rebounds", "Career", 23_924.0, "Wilt Chamberlain", 1973),
            BookRecord("career_ast", "Career assists", "Career", 15_806.0, "John Stockton", 2003),
            BookRecord("career_3pm", "Career threes", "Career", 4_200.0, "Stephen Curry", 2026, base = 4_200.0),
            BookRecord("career_stl", "Career steals", "Career", 3_265.0, "John Stockton", 2003),
            BookRecord("career_blk", "Career blocks", "Career", 3_830.0, "Hakeem Olajuwon", 2002),
        )
    }

    private fun stat(s: StatLine, i: Int) = when (i) { 0 -> s.pts; 1 -> s.reb; 2 -> s.ast; 3 -> s.tpm; 4 -> s.stl; else -> s.blk }

    /** A player's pro totals in this league, scaled to 83-game seasons. */
    fun careerTotals(lg: League, p: Player): DoubleArray {
        val out = DoubleArray(6)
        for (l in p.career.filter { it.cls == PRO_YEAR }) { val k = AllTime.lineScale(l); for (i in 0 until 6) out[i] += stat(l.s, i) * k }
        if (p.teamId >= 0 && p.career.none { it.season == lg.season && it.cls == PRO_YEAR }) for (i in 0 until 6) out[i] += stat(p.stats, i) * AllTime.currentScale(lg)
        return out
    }

    private fun broke(lg: League, rec: BookRecord, p: Player, value: Double) {
        val was = rec.holder
        rec.value = value
        rec.holder = p.name
        rec.season = lg.season
        rec.mine = p.id == lg.heroId
        val shown = if (rec.decimals > 0) "%.1f".format(value) else "${value.roundToInt()}"
        if (rec.mine) {
            p.awards += "${lg.season} Record: ${rec.label}"
            Alerts.add(lg, "Record broken", "You set the mark for ${rec.label.lowercase()}: $shown, passing $was.", "record")
            lg.road?.let { it.allTimeNote = "Record: ${rec.label}, $shown. You passed $was."; it.allTimeSeason = lg.season; it.allTimeDay = lg.day }
        }
        lg.news += News(lg.season, lg.day, "Record: ${p.name} sets the mark for ${rec.label.lowercase()} ($shown), passing $was", rec.mine)
    }

    /** After every pro game: single-game records, and his career totals. */
    fun afterGame(lg: League, lines: Array<LinkedHashMap<Int, StatLine>>) {
        val ps = lg.proState ?: return
        if (ps.records.isEmpty()) seed(lg)
        val r = lg.road
        for (side in 0..1) for ((pid, st) in lines[side]) {
            if (st.sec <= 0) continue
            for (i in 0 until 6) {
                val v = stat(st, i)
                if (pid == lg.heroId && r != null) {
                    if (r.proBest.size < 6) r.proBest = r.proBest.copyOf(6)
                    if (v > r.proBest[i]) r.proBest[i] = v
                }
                val rec = ps.records.first { it.key == "game_" + STATS[i] }
                if (v > rec.value) lg.playerOrNull(pid)?.let { broke(lg, rec, it, v.toDouble()) }
            }
        }
        val h = lg.playerOrNull(lg.heroId) ?: return
        val mine = careerTotals(lg, h)
        for (i in 0 until 6) {
            val rec = ps.records.first { it.key == "career_" + STATS[i] }
            if (!rec.mine && mine[i] > rec.value) broke(lg, rec, h, mine[i])
            else if (rec.mine) rec.value = mine[i]
        }
    }

    /** Games needed to qualify for a per-game record: 70% of the season. */
    fun qualify(lg: League) = (ProLeague.seasonGames(lg) * 0.7).toInt()

    /** End of the regular season: per-game records (70% of games to qualify), and active holders' career totals. */
    fun seasonEnd(lg: League) {
        val ps = lg.proState ?: return
        if (ps.records.isEmpty()) seed(lg)
        for (i in 0 until 6) {
            val rec = ps.records.first { it.key == "season_" + STATS[i] }
            val best = lg.players.filter { it.stats.gp >= qualify(lg) }.maxByOrNull { stat(it.stats, i).toDouble() / it.stats.gp } ?: continue
            val v = ((stat(best.stats, i).toDouble() / best.stats.gp) * 10).roundToInt() / 10.0
            if (v > rec.value) broke(lg, rec, best, v)
        }
        // A real holder still playing keeps adding to his record.
        for (rec in ps.records.filter { it.scope == "Career" && it.base > 0 && !it.mine }) {
            val p = lg.players.firstOrNull { it.name == rec.holder && it.photo.isNotEmpty() } ?: continue
            val i = STATS.indexOf(rec.key.removePrefix("career_"))
            rec.value = rec.base + careerTotals(lg, p)[i]
        }
    }

    /** His standing on a record: his best (single game), best season average, or career total. */
    fun mine(lg: League, rec: BookRecord): Double {
        val h = lg.hero ?: return 0.0
        val i = STATS.indexOf(rec.key.substringAfter("_"))
        return when (rec.scope) {
            "Game" -> lg.road?.proBest?.getOrNull(i)?.toDouble() ?: 0.0
            "Season" -> (h.career.filter { it.cls == PRO_YEAR && it.s.gp >= (it.w + it.l) * 0.7 }.map { it.s } + listOfNotNull(h.stats.takeIf { it.gp >= qualify(lg) }))
                .maxOfOrNull { stat(it, i).toDouble() / it.gp } ?: 0.0
            else -> careerTotals(lg, h)[i]
        }
    }

    fun format(rec: BookRecord, v: Double) = if (rec.decimals > 0) "%.1f".format(v) else "%,d".format(v.roundToInt())

    @Suppress("unused") private val keep = NAMES
}

/** Recruiting a star: pitch a free agent each summer, or push the front office to trade for one in season. */
object StarRecruit {
    /** Title odds from each team's best eight, weighted toward the top. */
    fun odds(lg: League, rosters: Map<Int, List<Player>> = emptyMap()): Map<Int, Double> {
        val ps = lg.proState
        // In the playoffs, only teams still alive can win it. Losing the 7-8 play-in game isn't the end.
        val alive = if (lg.phase == Phase.PLAYOFFS && ps != null) {
            ps.seeds.keys.filter { id -> ps.series.none { it.done && it.loser == id && !(it.round == 0 && it.slot == 0) } }.toSet()
        } else lg.teams.map { it.id }.toSet()
        val strength = lg.teams.associate { t ->
            val top = (rosters[t.id] ?: lg.rosterOf(t)).map { it.ovr }.sortedDescending().take(8)
            t.id to top.mapIndexed { i, o -> o * (1.0 - i * 0.07) }.sum() / top.indices.sumOf { 1.0 - it * 0.07 }.coerceAtLeast(1.0)
        }
        val mean = strength.values.average()
        val w = strength.mapValues { (id, s) -> if (id in alive) exp((s - mean) * 0.75) else 0.0 }
        val sum = w.values.sum().coerceAtLeast(1e-9)
        return w.mapValues { it.value / sum }
    }

    /** His team's title odds if [star] joined (and his old team lost him). */
    fun oddsWith(lg: League, star: Player): Double {
        val me = lg.userTeamId
        val roster = (lg.rosterOf(lg.user) + star)
        val from = lg.team(star.teamId)
        return odds(lg, mapOf(me to roster, from.id to lg.rosterOf(from).filter { it.id != star.id }))[me] ?: 0.0
    }

    /** This summer's star free agents: a few of the league's best who'd listen. */
    fun openMarket(lg: League) {
        val ps = lg.proState ?: return
        val h = lg.playerOrNull(lg.heroId) ?: return
        ps.freeStars.clear()
        ps.freeStars += lg.players.filter { it.teamId >= 0 && it.teamId != h.teamId && it.id != h.id && it.ovr >= 92 && it.age >= 24 }
            .shuffled(Rng.r).take(4).sortedByDescending { it.ovr }.map { it.id }
    }

    fun canPitch(lg: League) = lg.phase == Phase.OFFSEASON && lg.proState?.step.let { it == ProStep.DRAFT || it == ProStep.FREE_AGENCY } &&
        lg.road?.pitchSeason != lg.season && lg.road?.ended == false && lg.proState?.freeStars?.isNotEmpty() == true

    /** How likely a star says yes: winning, his star power, the market, and older stars chasing rings. */
    fun chance(lg: League, star: Player): Double {
        val h = lg.hero ?: return 0.0
        val mine = odds(lg)[lg.userTeamId] ?: 0.0
        var c = 0.2 + mine * 1.5
        if (h.ovr >= 95) c += 0.15 else if (h.ovr >= 92) c += 0.07
        c += (lg.user.prestige - 70) / 200.0
        if (star.age >= 30) c += mine
        if ((lg.road?.followers ?: 0) >= 10_000_000) c += 0.05
        return c.coerceIn(0.05, 0.85)
    }

    /** He pitches [pid]. One pitch a summer. Returns what happened. */
    fun pitch(lg: League, pid: Int): String {
        val r = lg.road ?: return ""
        val ps = lg.proState ?: return ""
        if (!canPitch(lg) || pid !in ps.freeStars) return ""
        val star = lg.playerOrNull(pid) ?: return ""
        val from = lg.team(star.teamId)
        val to = lg.user
        r.pitchSeason = lg.season
        ps.freeStars.clear()
        if (!Rng.chance(chance(lg, star))) {
            r.recruitNote = "${star.name} hears you out, then re-signs with the ${from.name}."
            lg.news += News(lg.season, 0, "${star.name} stays with the ${from.name} after a pitch from ${lg.hero?.name}", true)
            return r.recruitNote
        }
        from.roster.remove(star.id)
        star.teamId = to.id
        if (to.roster.any { lg.player(it).number == star.number }) star.number = Recruiting.freeNumber(lg, to)
        to.roster += star.id
        ProLeague.trim(lg, to)
        while (from.roster.size < ProLeague.ROSTER) ProLeague.makePro(lg, Rng.pick(Pos.entries), Rng.range(85, 89), Rng.range(26, 32), from)
        Tactics.autoAllocate(lg, from)
        Tactics.autoAllocate(lg, to)
        lg.reindex()
        r.recruitNote = "${star.name} is in. He signs with the ${to.name} to play with you."
        lg.news += News(lg.season, 0, "${star.name} leaves the ${from.name} to team up with ${lg.hero?.name} on the ${to.name}", true)
        return r.recruitNote
    }

    /** In season: stars on losing teams the front office could chase. */
    fun tradeTargets(lg: League): List<Player> {
        val h = lg.playerOrNull(lg.heroId) ?: return emptyList()
        return lg.teams.filter { it.id != h.teamId && it.gp >= 8 && it.winPct < 0.45 }
            .flatMap { lg.rosterOf(it) }.filter { it.ovr >= 91 && it.id != lg.road?.rivalId }.sortedByDescending { it.ovr }.take(3)
    }

    fun canPush(lg: League) = lg.phase == Phase.REGULAR && lg.day < Trades.deadlineDay(lg) && lg.road?.pushSeason != lg.season && lg.road?.ended == false

    /** He pushes for a trade for [pid]: the front office offers what it can spare (never him). Returns what happened. */
    fun push(lg: League, pid: Int): String {
        val r = lg.road ?: return ""
        if (!canPush(lg)) return ""
        val star = lg.playerOrNull(pid) ?: return ""
        r.pushSeason = lg.season
        val from = lg.team(star.teamId)
        // A losing team selling its star takes less than full value.
        val pack = Trades.bundleFor(lg, lg.user, Trades.value(star), 0.6, 1.5)
        if (pack == null) {
            r.recruitNote = "Your front office called the ${from.name}, but couldn't put together enough for ${star.name}."
            return r.recruitNote
        }
        Trades.execute(lg, lg.user, from, pack, listOf(star))
        r.recruitNote = "Done: the ${lg.user.name} land ${star.name} for ${pack.joinToString(" and ") { it.name }}."
        lg.news += News(lg.season, lg.day, "Trade: the ${lg.user.name} get ${star.name} from the ${from.name} after a push from ${lg.hero?.name}", true)
        return r.recruitNote
    }
}

/** When the playing ends: numbers in the rafters, the Hall of Fame, and a coaching job. */
object Afterward {
    /** Teams where he starred: three seasons with an All-Star or a ring there, or six seasons. */
    fun retireNumbers(lg: League) {
        val r = lg.road ?: return
        val h = lg.hero ?: return
        r.jerseys.clear()
        for (tid in r.proTeams.distinct()) {
            val lines = h.career.filter { it.cls == PRO_YEAR && it.teamId == tid }
            val seasons = lines.map { it.season }.toSet()
            val stars = h.awards.count { a -> a.endsWith(" All-Star") && a.substringBefore(" ").toIntOrNull() in seasons }
            val rings = h.awards.count { a -> a.endsWith(" Champion") && a.substringBefore(" ").toIntOrNull() in seasons }
            if ((seasons.size >= 3 && (stars > 0 || rings > 0)) || seasons.size >= 6) r.jerseys += "The ${lg.team(tid).name} retire No. ${h.number}"
        }
        val college = r.collegeSchools.substringBefore(" →").trim()
        if (college.isNotEmpty() && (h.awards.any { it.contains("AA 1st") || it.contains("Nat MVP") } || r.draftPick in 1..5)) r.jerseys += "$college retires No. ${h.number}"
        if (ProCareer.hofScore(lg) >= 18) r.hofClass = lg.season + 4
    }

    /** Highlights for the Hall of Fame night. */
    fun highlights(lg: League): List<String> {
        val r = lg.road ?: return emptyList()
        val h = lg.hero ?: return emptyList()
        val out = mutableListOf<String>()
        if (r.rings > 0) out += "${r.rings} ${if (r.rings == 1) "championship" else "championships"}"
        val fmvp = h.awards.count { it.substringAfter(" ") == "Finals MVP" }
        if (fmvp > 0) out += "$fmvp Finals MVP${if (fmvp == 1) "" else "s"}"
        if (r.mvps > 0) out += "${r.mvps} MVP${if (r.mvps == 1) "" else "s"}"
        if (r.allStars > 0) out += "${r.allStars} All-Star selections"
        val firsts = h.awards.count { it.endsWith(" All-NBA 1st") }
        if (firsts > 0) out += "$firsts All-NBA First Team${if (firsts == 1) "" else "s"}"
        val recs = lg.proState?.records?.filter { it.mine } ?: emptyList()
        if (recs.isNotEmpty()) out += "Records: " + recs.joinToString(", ") { it.label.lowercase() }
        AllTime.rank(lg).takeIf { it > 0 }?.let { out += "No. $it on the all-time list" }
        r.reel.filter { it.big }.maxByOrNull { it.line.substringBefore(" pts").toIntOrNull() ?: 0 }?.let { out += "Best big night: ${it.title}, ${it.line.substringBefore(" ·")}" }
        return out
    }

    class Job(val school: String, val why: String)

    /** College jobs for a retired star: his alma mater first, and programs that match his name. */
    fun coachingJobs(lg: League): List<Job> {
        val r = lg.road ?: return emptyList()
        val schools = Schools.conferences.flatMap { Schools.parse(it) }
        val alma = (if (lg.pro) r.collegeSchools else r.schools.lastOrNull()?.let { lg.team(it).school } ?: "").substringBefore(" →").trim()
        // A career that ended after college: smaller programs, and his school only if he was a star there.
        if (!lg.pro) {
            val h = lg.hero
            val star = h != null && h.awards.any { it.contains("AA ") || it.contains("Nat MVP") || it.contains("Conf POY") }
            val out = mutableListOf<Job>()
            if (star) schools.firstOrNull { it.name == alma }?.let { out += Job(it.name, "Your old school wants its star back") }
            for (s in schools.filter { it.prestige in 25..55 && it.name != alma }.shuffled(Rng.r).take(4 - out.size)) out += Job(s.name, "Prestige ${s.prestige}")
            return out
        }
        val legacy = AllTime.heroScore(lg)
        val band = when { legacy >= 60 -> 80..99; legacy >= 30 -> 65..85; else -> 45..70 }
        val out = mutableListOf<Job>()
        schools.firstOrNull { it.name == alma }?.let { out += Job(it.name, "Your alma mater wants you home") }
        for (s in schools.filter { it.prestige in band && it.name != alma }.shuffled(Rng.r).take(3)) out += Job(s.name, "Prestige ${s.prestige}")
        return out
    }

    /** Years between the end of his playing days and his first season as a coach (a career overseas first). */
    fun yearsAway(lg: League): Int {
        if (lg.pro) return 1
        val age = lg.hero?.age ?: 22
        return (31 - age).coerceIn(4, 10)
    }

    /** The coach he becomes: a recruiter who sells his name, and a teacher of the game he played. */
    fun becomeCoach(pro: League, college: League, school: String): League {
        val r = pro.road!!
        val h = pro.hero!!
        val t = college.teams.first { it.school == school }
        val away = yearsAway(pro)
        val past = Lifetime.fromPlayer(pro)
        college.season = pro.season + away
        Offseason.claimTeam(college, t.id, h.first, h.last)
        val c = college.userCoach
        val legacy = AllTime.heroScore(pro)
        val peak = h.ovrHistory.maxOrNull() ?: h.ovr
        c.age = h.age + away
        if (pro.pro) {
            c.rec = (60 + legacy / 3).roundToInt().coerceIn(60, 95)
            c.off = (55 + (peak - 85) * 2.5).roundToInt().coerceIn(55, 92)
            c.def = (52 + (peak - 85) * 2.0).roundToInt().coerceIn(50, 88)
            c.dev = 64
        } else {
            // Years overseas taught him the game; his college name still sells a little.
            c.rec = (55 + (peak - 75) * 0.8).roundToInt().coerceIn(52, 72)
            c.off = (58 + (peak - 75) * 0.8).roundToInt().coerceIn(55, 75)
            c.def = (55 + (peak - 75) * 0.6).roundToInt().coerceIn(52, 72)
            c.dev = 66
        }
        college.playedAs = past
        Trophies.carry(pro, college)
        college.tree += pro.tree
        past?.let { GenerationalLegacy.stampFromPlayer(college, school, it) }
        val head = if (pro.pro) "${h.name} takes over at ${t.school}: ${r.rings} ${if (r.rings == 1) "ring" else "rings"}, ${r.allStars} All-Star games and a pro career to sell"
            else "${h.name} comes home from $away years overseas to coach ${t.school}"
        // His introduction: a press conference of its own.
        runCatching { Farewell.hire(college, head) }.onFailure { college.news += News(college.season, 0, head, true) }
        return college
    }
}
