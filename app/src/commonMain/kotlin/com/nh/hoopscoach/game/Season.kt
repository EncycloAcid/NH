package com.nh.hoopscoach.game

import kotlin.math.roundToInt

object Season {

    private val SLOT_ORDER = intArrayOf(1, 16, 8, 9, 5, 12, 4, 13, 6, 11, 3, 14, 7, 10, 2, 15)
    val NCAA_ROUNDS = listOf("R64", "R32", "S16", "E8", "F4", "CHAMP")
    private val CT_ROUNDS = listOf("R1", "QF", "SF", "Final")
    val NIT_ROUNDS = listOf("R1", "R2", "QF", "SF", "Final")
    private val NIT_TITLES = listOf("First Round", "Second Round", "Quarterfinals", "Semifinals", "Championship")
    val REGIONS = listOf("East", "West", "South", "Midwest")

    /** Bracket id for the NIT (conference brackets use their conference id, the NCAA uses -1). */
    const val NIT_ID = -2
    /** NIT title rewards: coaching points for the user and XP for every returning player. */
    const val NIT_POINTS = 3
    const val NIT_XP = 350

    fun gamesOn(lg: League, day: Int) = lg.games.filter { it.day == day }

    fun userGameToday(lg: League): Game? = lg.games.firstOrNull { it.day == lg.day && it.involves(lg.userTeamId) && !it.played }

    fun nextUserGame(lg: League): Game? = lg.games.filter { it.involves(lg.userTeamId) && !it.played }.minByOrNull { it.day }

    fun seasonOver(lg: League) = lg.phase == Phase.WRAP_UP || lg.phase == Phase.OFFSEASON

    /** Creates games for today that depend on earlier results (events, brackets). */
    fun prepareDay(lg: League) {
        when (lg.phase) {
            Phase.REGULAR -> if (lg.day in Schedule.EVENT_DAY + 1..Schedule.EVENT_DAY + 2) for (ev in lg.events) eventRound(lg, ev, lg.day - Schedule.EVENT_DAY)
            Phase.CONF_TOURNEY -> for (b in lg.confBrackets) scheduleBracketRound(lg, b, GameType.CONF_TOURNEY)
            Phase.NCAA -> {
                lg.ncaa?.let { if (lg.day < it.startDay) schedulePlayIns(lg, it) else scheduleBracketRound(lg, it, GameType.NCAA) }
                lg.nit?.let { scheduleBracketRound(lg, it, GameType.NIT) }
            }
            else -> {}
        }
    }

    /** The bracket the user's team is (or was) playing in this phase. */
    fun userBracket(lg: League): Bracket? = when (lg.phase) {
        Phase.CONF_TOURNEY -> lg.confBrackets.firstOrNull { it.confId == lg.user.confId }
        Phase.NCAA, Phase.WRAP_UP -> lg.nit?.takeIf { it.hasTeam(lg.userTeamId) } ?: lg.ncaa
        else -> null
    }

    fun firstFourDay(lg: League) = lg.phase == Phase.NCAA && lg.ncaa?.let { it.playIns.isNotEmpty() && lg.day < it.startDay } == true

    /** Plays every remaining game today and moves the calendar forward one day. */
    fun playDay(lg: League) {
        if (seasonOver(lg)) return
        prepareDay(lg)
        for (g in gamesOn(lg, lg.day)) if (!g.played) GameEngine(lg, g, false).commit()
        endOfDay(lg)
    }

    private fun endOfDay(lg: League) {
        if (lg.phase == Phase.REGULAR || lg.phase == Phase.CONF_TOURNEY || lg.phase == Phase.NCAA) NewsDesk.endOfDay(lg)
        when (lg.phase) {
            Phase.REGULAR -> {
                dailyUpkeep(lg)
                if (lg.day == Schedule.EVENT_DAY + 2) for (ev in lg.events) finishEvent(lg, ev)
                // Conference play starts: the AD calls the coach in.
                if (lg.day == Schedule.NC_DAYS && !lg.playerMode) runCatching { FrontOffice.meeting(lg) }
                if (lg.day >= Schedule.REG_DAYS) {
                    endRegularSeason(lg)
                    lg.day = Schedule.CT_START
                    prepareDay(lg)
                    return
                }
            }
            Phase.CONF_TOURNEY -> {
                dailyUpkeep(lg)
                for (b in lg.confBrackets) advanceBracket(lg, b)
                if (lg.confBrackets.all { it.done }) {
                    selection(lg)
                    lg.day = Schedule.NCAA_START
                    prepareDay(lg)
                    return
                }
            }
            Phase.NCAA -> {
                val b = lg.ncaa!!
                if (lg.day < b.startDay) resolvePlayIns(lg, b) else advanceBracket(lg, b)
                val nit = lg.nit
                if (nit != null) advanceBracket(lg, nit)
                if (b.done && (nit == null || nit.done)) { endSeason(lg); return }
            }
            else -> return
        }
        lg.day++
        prepareDay(lg)
    }

    private fun dailyUpkeep(lg: League) {
        Rankings.refresh(lg)
        Fans.drift(lg)
        Morale.tick(lg)
        if (lg.phase == Phase.REGULAR) Recruiting.tick(lg)
        // Practice: everyone improves a little, bench players included — up to their ceiling, not past it.
        // Academic redshirts lean on practice — a small XP bump for the year of development.
        for (t in lg.teams) {
            val dev = Coaching.devOf(lg.coachOf(t))
            for (pid in t.roster) {
                val p = lg.player(pid)
                // Practice takes him to his ceiling, not past it (your own player excepted).
                if (p.ovr >= p.pot && p.id != lg.heroId) continue
                // Light practice XP: games do most of the growing, so the league doesn't creep up every day.
                val base = 4 * Ratings.devRate(p, dev)
                val xp = if (Redshirts.sitting(p)) base * 1.25 else base
                Ratings.addXp(p, xp.roundToInt())
                Tryouts.checkStar(lg, t, p)
            }
        }
        // AI coaches re-tune their rotations as players develop.
        if (lg.day % 7 == 0) for (t in lg.teams) if (!lg.humanCoach(t) || t.plan.auto) Tactics.autoAllocate(lg, t)
    }

    // ---------- in-season events ----------

    private fun eventRound(lg: League, ev: InSeasonEvent, round: Int) {
        if (ev.gameIds.size >= 4 * (round + 1)) return
        val day = Schedule.EVENT_DAY + round
        val label = if (round == 1) "${ev.name} · R2" else "${ev.name} · Final"
        val prev = ev.gameIds.takeLast(4).map { lg.game(it) }
        val pairs: List<Pair<Int, Int>> = if (round == 1) {
            listOf(prev[0].winner() to prev[1].winner(), prev[2].winner() to prev[3].winner(), prev[0].loser() to prev[1].loser(), prev[2].loser() to prev[3].loser())
        } else {
            listOf(prev[0].winner() to prev[1].winner(), prev[0].loser() to prev[1].loser(), prev[2].winner() to prev[3].winner(), prev[2].loser() to prev[3].loser())
        }
        pairs.forEachIndexed { i, (a, b) ->
            val lab = if (round == 2) when (i) { 0 -> "${ev.name} · Final"; 1 -> "${ev.name} · 3rd Place"; 2 -> "${ev.name} · 5th Place"; else -> "${ev.name} · 7th Place" }
            else if (i < 2) label else "${ev.name} · Consolation"
            val g = Game(lg.nextGameId++, day, a, b, true, GameType.EVENT, lab)
            lg.addGame(g)
            ev.gameIds += g.id
        }
    }

    private fun finishEvent(lg: League, ev: InSeasonEvent) {
        if (ev.gameIds.size < 12) return
        val last = ev.gameIds.takeLast(4).map { lg.game(it) }
        ev.placement = mutableListOf(
            last[0].winner(), last[0].loser(), last[1].winner(), last[1].loser(),
            last[2].winner(), last[2].loser(), last[3].winner(), last[3].loser(),
        )
        val champ = lg.team(ev.placement[0])
        lg.news += News(lg.season, lg.day, "${champ.school} wins the ${ev.name}", champ.id == lg.userTeamId)
    }

    // ---------- conference standings & tournaments ----------

    fun standings(lg: League, confId: Int): List<Team> =
        lg.conf(confId).teamIds.map { lg.team(it) }.sortedWith(
            compareByDescending<Team> { if (it.cw + it.cl == 0) 0.0 else it.cw.toDouble() / (it.cw + it.cl) }
                .thenByDescending { it.cw }
                .thenByDescending { it.winPct }
                .thenByDescending { it.elo },
        )

    private fun endRegularSeason(lg: League) {
        Rankings.refresh(lg)
        Awards.conference(lg)
        lg.confBrackets = mutableListOf()
        for (c in lg.confs) {
            val st = standings(lg, c.id)
            val seeds = HashMap<Int, Int>()
            st.forEachIndexed { i, t -> seeds[t.id] = i + 1 }
            val slots = SLOT_ORDER.map { s -> st.getOrNull(s - 1)?.id ?: -1 }.toMutableList()
            lg.confBrackets += Bracket("${c.abbr} Tournament", c.abbr, c.id, Schedule.CT_START, CT_ROUNDS, seeds, mutableListOf(slots))
            st.first().let { champ ->
                lg.news += News(lg.season, lg.day, "${champ.school} wins the ${c.name} regular season title", champ.id == lg.userTeamId)
            }
        }
        lg.phase = Phase.CONF_TOURNEY
        val u = lg.user
        val seed = lg.confBrackets.first { it.confId == u.confId }.seeds[u.id]
        lg.news += News(lg.season, lg.day, "${u.school} enters the ${lg.conf(u.confId).abbr} Tournament as the No. $seed seed", true)
    }

    private fun scheduleBracketRound(lg: League, b: Bracket, type: GameType) {
        if (b.done) return
        val r = b.current
        if (b.gameIds.size > r) return
        val slots = b.rounds[r]
        val ids = mutableListOf<Int>()
        val day = b.startDay + r
        for (i in 0 until slots.size / 2) {
            val a = slots[2 * i]
            val c = slots[2 * i + 1]
            if (a < 0 || c < 0) { ids += -1; continue }
            val sa = b.seeds[a] ?: 99
            val sc = b.seeds[c] ?: 99
            val neutral = when (type) { GameType.NCAA -> true; GameType.NIT -> r >= 3; else -> r > 0 }
            val (h, aw) = if (sa <= sc) a to c else c to a
            val label = when (type) {
                GameType.NCAA -> ncaaLabel(r, i, slots.size)
                GameType.NIT -> "NIT · ${NIT_TITLES.getOrElse(r) { "Round ${r + 1}" }}"
                else -> "${b.name} · ${b.roundNames.getOrElse(r) { "R${r + 1}" }}"
            }
            val g = Game(lg.nextGameId++, day, h, aw, neutral, type, label)
            lg.addGame(g)
            ids += g.id
        }
        b.gameIds += ids
    }

    private fun ncaaLabel(round: Int, index: Int, slots: Int): String {
        val name = when (round) { 0 -> "First Round"; 1 -> "Second Round"; 2 -> "Sweet 16"; 3 -> "Elite Eight"; 4 -> "Final Four"; else -> "National Championship" }
        if (round >= 4) return "NCAA · $name"
        val perRegion = slots / 2 / 4
        val region = REGIONS[(index / perRegion).coerceAtMost(3)]
        return "NCAA $region · $name"
    }

    private fun advanceBracket(lg: League, b: Bracket) {
        if (b.done) return
        val r = b.current
        if (b.gameIds.size <= r) return
        val slots = b.rounds[r]
        val next = mutableListOf<Int>()
        for (i in 0 until slots.size / 2) {
            val a = slots[2 * i]
            val c = slots[2 * i + 1]
            val gid = b.gameIds[r][i]
            next += when {
                gid >= 0 -> lg.game(gid).let { g -> if (g.played) g.winner() else -2 }
                a >= 0 -> a
                else -> c
            }
        }
        if (next.size == 1) {
            b.champion = next[0]
            b.rounds += next.toMutableList()
            b.current++
            val t = lg.team(b.champion)
            if (b.confId >= 0) {
                lg.news += News(lg.season, lg.day, "${t.school} wins the ${b.name} and an automatic bid", t.id == lg.userTeamId)
            } else if (b.confId == NIT_ID) {
                lg.news += News(lg.season, lg.day, "${t.school} wins the NIT", t.id == lg.userTeamId)
            }
            Moments.afterRound(lg, b)
            return
        }
        b.rounds += next
        b.current++
        Moments.afterRound(lg, b)
    }

    // ---------- NCAA tournament ----------

    /** Selection Sunday: 68 teams for the NCAA Tournament (First Four first), the next 32 for the NIT. */
    private fun selection(lg: League) {
        Rankings.refresh(lg)
        val pr = Bracketology.project(lg)
        lg.bubble = pr.snapshot(lg, final = true)
        val s = pr.seeding
        // With a First Four, the Round of 64 starts the day after the play-in games.
        val start = if (s.playIns.isEmpty()) Schedule.NCAA_START else Schedule.NCAA_START + 1
        lg.ncaa = Bracket("NCAA Tournament", "NCAA", -1, start, NCAA_ROUNDS, s.seeds, mutableListOf(s.slots), playIns = s.playIns)
        val field = pr.field.toSet()
        lg.nit = Bracketology.nitBracket(pr.order.filter { it !in field })
        lg.phase = Phase.NCAA
        for (id in field) { val t = lg.team(id); t.tourneys++; lg.coachOf(t).tourneys++; t.fans = (t.fans + 4).coerceAtMost(100.0) }

        val groups = lg.bubble!!.groups.associateBy { it.key }
        val lastIn = pr.atLarge.takeLast(4)
        if (s.playIns.isNotEmpty()) lg.news += News(lg.season, lg.day, "Last four in: " + lastIn.joinToString(", ") { lg.team(it).school })
        groups["ffo"]?.ids?.takeIf { it.isNotEmpty() }?.let { ids -> lg.news += News(lg.season, lg.day, "First four out: " + ids.joinToString(", ") { lg.team(it).school }) }

        val u = lg.user
        val seed = s.seeds[u.id]
        val pi = lg.ncaa!!.playInOf(u.id)
        val nitSeed = lg.nit?.seeds?.get(u.id)
        lg.news += News(
            lg.season, lg.day,
            when {
                pi != null -> "${u.school} heads to the First Four, playing for a No. ${pi.seed} seed in the ${REGIONS[pi.slot / 16]}"
                seed != null -> "${u.school} earns a No. $seed seed in the ${regionOf(lg, u.id)} Region"
                nitSeed != null -> "${u.school} misses the NCAA field and takes a No. $nitSeed seed in the NIT"
                else -> "${u.school} is left out of the postseason"
            },
            true,
        )
    }

    private fun schedulePlayIns(lg: League, b: Bracket) {
        for (pi in b.playIns) {
            if (pi.gameId >= 0) continue
            val (h, a) = if ((b.seeds[pi.a] ?: 99) <= (b.seeds[pi.b] ?: 99)) pi.a to pi.b else pi.b to pi.a
            val g = Game(lg.nextGameId++, lg.day, h, a, true, GameType.NCAA, "NCAA ${REGIONS[pi.slot / 16]} · First Four")
            lg.addGame(g)
            pi.gameId = g.id
        }
    }

    /** First Four winners move into their Round of 64 slot. */
    private fun resolvePlayIns(lg: League, b: Bracket) {
        for (pi in b.playIns) {
            if (pi.gameId < 0) continue
            val g = lg.game(pi.gameId)
            if (!g.played || b.rounds[0][pi.slot] >= 0) continue
            val w = g.winner()
            b.rounds[0][pi.slot] = w
            if (g.involves(lg.userTeamId)) {
                lg.news += News(lg.season, lg.day, if (w == lg.userTeamId) "${lg.user.school} wins in the First Four and moves on as a No. ${pi.seed} seed" else "${lg.user.school} falls in the First Four", true)
            }
        }
    }

    fun regionOf(lg: League, teamId: Int): String? {
        val b = lg.ncaa ?: return null
        val idx = b.rounds[0].indexOf(teamId).takeIf { it >= 0 } ?: b.playInOf(teamId)?.slot ?: return null
        return REGIONS[idx / 16]
    }

    // ---------- end of season ----------

    private fun endSeason(lg: League) {
        Rankings.refresh(lg)
        Awards.national(lg)
        val ncaa = lg.ncaa!!
        val champ = lg.team(ncaa.champion)
        champ.titles++
        lg.coachOf(champ).titles++
        val f4 = ncaa.rounds[4].filter { it >= 0 }
        for (id in f4) { lg.team(id).finalFours++; lg.coachOf(lg.team(id)).finalFours++ }
        val runnerUp = ncaa.rounds[5].first { it != ncaa.champion }
        val titleGame = ncaa.gameIds.lastOrNull()?.firstOrNull()?.takeIf { it >= 0 }?.let { lg.game(it) }
        val ws = ncaa.seeds[champ.id] ?: 0
        val ls = ncaa.seeds[runnerUp] ?: 0
        val titleNews = News(
            lg.season, lg.day,
            Reports.titleHeadline(champ, lg.team(runnerUp), ws, ls, titleGame?.let { kotlin.math.abs(it.hs - it.aws) } ?: 0, titleGame?.ot ?: 0),
            champ.id == lg.userTeamId,
            titleGame?.id ?: -1,
            "moment",
        )
        lg.news += titleNews
        if (lg.ollama.enabled && lg.ollama.championshipCelebrations) {
            Ollama.maybeAutoGenerate(lg.ollama, lg, titleNews)
        }
        // The parade for his team's title, then awards night for his (or his players') big honors.
        if (champ.id == lg.userTeamId) runCatching { Celebrations.parade(lg) }
        runCatching { Celebrations.awardsNight(lg) }

        val confChamps = HashMap<Int, Int>()
        for (b in lg.confBrackets) {
            confChamps[b.confId] = b.champion
            lg.team(b.champion).confTitles++
            lg.coachOf(lg.team(b.champion)).confTitles++
        }
        val coty = lg.coaches.firstOrNull { it.id == lg.cotyCoachId }
        lg.history += SeasonSummary(
            lg.season, champ.id, runnerUp, f4, lg.seasonAwards.toList(),
            coty?.name ?: "", coty?.teamId ?: -1, confChamps,
        )

        // NIT champion: a prestige boost and extra development for everyone coming back.
        val nitChamp = lg.nit?.champion ?: -1
        val nitBoost = if (nitChamp >= 0) Rng.range(2, 3) else 0
        var nitPlayers = 0
        if (nitChamp >= 0) {
            val t = lg.team(nitChamp)
            for (p in lg.rosterOf(t)) if (p.year < 4) { Ratings.addXp(p, NIT_XP); nitPlayers++ }
        }

        // Prestige, team & coach history.
        // Season-start prestige: rivalry swings during the year count toward the season's change.
        val beforeUser = lg.user.prestige - lg.user.rivalryDelta
        Brand.ensure(lg)
        val places = HashMap<Int, Int>()
        for (c in lg.confs) standings(lg, c.id).forEachIndexed { i, tm -> places[tm.id] = i + 1 }
        val targets = lg.teams.associate { t ->
            val round = Awards.tourneyRound(lg, t.id)
            t.id to Brand.target(lg, t, round, places[t.id] ?: 99, confChamps[t.confId] == t.id)
        }
        val earned = targets.mapValues { it.value.total }
        val userRate = Brand.climbRate(lg.user, earned.getValue(lg.userTeamId))
        for (t in lg.teams) {
            val round = Awards.tourneyRound(lg, t.id)
            t.prevPrestige = t.prestige - t.rivalryDelta
            t.prestige = Brand.newPrestige(t, earned.getValue(t.id), round).roundToInt().coerceIn(5, 99)
            if (t.id == nitChamp) t.prestige = (t.prestige + nitBoost).coerceAtMost(99)
            Brand.updateLegacy(t, round)
            val c = lg.coachOf(t)
            val result = Awards.resultFor(lg, t.id)
            t.history += TeamSeason(lg.season, t.w, t.l, t.cw, t.cl, result, t.prestige, t.net, t.ap, c.name, GreatTeams.margin(t))
            c.history += CoachSeason(lg.season, t.id, t.school, t.w, t.l, result, t.prestige)
        }
        val results = HashMap<Int, String>()
        for (p in lg.players) {
            val t = if (p.teamId >= 0) lg.team(p.teamId) else null
            if (t != null && p.stats.gp > 0) {
                val result = results.getOrPut(t.id) { Awards.resultFor(lg, t.id) }
                p.career += SeasonLine(
                    lg.season, t.abbr, lg.conf(t.confId).abbr, p.year, p.ovr, p.stats.copy(),
                    p.tourney.takeIf { it.gp > 0 }?.copy(), t.w, t.l, result, t.id,
                )
            }
            p.ovrHistory += p.ovr
        }
        Finance.decayBonuses(lg)
        Finance.awardBonuses(lg)
        Coaching.evaluateUser(lg)
        val points = Coaching.pointsEarned(lg)
        lg.userCoach.points += points
        val reward = if (nitChamp == lg.userTeamId) NitReward(nitBoost, NIT_POINTS, NIT_XP, nitPlayers) else null
        lg.wrapUp = buildWrapUp(lg, beforeUser, points, reward, targets[lg.userTeamId], userRate)
        if (lg.playerMode) Road.seasonEnd(lg)
        runCatching { Columns.reviewCollege(lg) }
        // An odd summer: USA Basketball may want him for the U19 World Cup.
        if (!lg.playerMode) runCatching { TeamUSA.offer(lg) }
        lg.phase = Phase.WRAP_UP
    }

    private fun buildWrapUp(lg: League, prestigeBefore: Int, points: Int, nitReward: NitReward?, target: Brand.Target? = null, rate: Double = 0.0): WrapUp {
        val t = lg.user
        val round = Awards.tourneyRound(lg, t.id)
        val b = lg.ncaa!!
        val nit = lg.nit
        val event = when { b.hasTeam(t.id) -> "NCAA"; nit != null && nit.hasTeam(t.id) -> "NIT"; else -> "" }
        val result = Awards.resultFor(lg, t.id)
        val path = mutableListOf<Int>()
        val won = mutableListOf<Boolean>()
        val type = if (event == "NIT") GameType.NIT else GameType.NCAA
        for (g in lg.games.filter { it.type == type && it.involves(t.id) && it.played }.sortedBy { it.day }) {
            path += g.opp(t.id); won += g.winner() == t.id
        }
        val rounds = when (event) {
            "NIT" -> NIT_ROUNDS.map { it.uppercase() }
            else -> (if (b.playInOf(t.id) != null) listOf("FF") else emptyList()) + NCAA_ROUNDS
        }
        val seed = when (event) { "NCAA" -> b.seeds[t.id] ?: 0; "NIT" -> nit?.seeds?.get(t.id) ?: 0; else -> 0 }
        val tourneyText = when {
            result == "First Four" -> "Made the field and lost in the First Four."
            round == 7 -> "Won the national title."
            round >= 1 -> "Reached the ${Awards.resultLabel(round).replace("Round of", "round of")}."
            result == "NIT Champion" -> "Missed the field, then won the NIT."
            event == "NIT" -> "Missed the field and played in the NIT (${result.removePrefix("NIT ").lowercase()})."
            else -> "Missed the field."
        }
        val regular = lg.games.filter { it.involves(t.id) && it.played && (it.type == GameType.CONF || it.type == GameType.NONCONF || it.type == GameType.EVENT) }
        val regW = regular.count { it.winner() == t.id }
        val st = standings(lg, t.confId)
        val mine = (lg.seasonAwards + lg.confAwards).filter { it.teamId == t.id && it.pid > 0 }
        val coach = lg.userCoach
        val coachAwards = mutableListOf<String>()
        if (lg.cotyCoachId == coach.id) coachAwards += "Coach of the Year"
        if (lg.confAwards.any { it.pid == -coach.id }) coachAwards += "${lg.conf(t.confId).abbr} Coach of the Year"
        val ct = lg.confBrackets.firstOrNull { it.confId == t.confId }
        val ctResult = when {
            ct == null -> ""
            ct.champion == t.id -> "Champions"
            else -> {
                val reached = ct.rounds.indexOfLast { t.id in it }
                "Lost in ${ct.roundNames.getOrElse(reached) { "R1" }}"
            }
        }
        val confFinish = st.indexOf(t) + 1
        val diff = t.w - t.expWins
        val q1w = Rankings.quadRecord(lg, t, 1).substringBefore('-').toInt()
        val natAwards = lg.seasonAwards.count { it.teamId == t.id }
        val signals = listOf(
            Driver("Season performance", "${t.w} wins finished %+.0f against a %.0f-win target.".format(diff, t.expWins),
                when { diff >= 5 -> "Big lift"; diff >= 1.5 -> "Small lift"; diff > -1.5 -> "Steady"; diff > -5 -> "Small drop"; else -> "Big drop" }, diff > -1.5),
            Driver("Conference result", "Finished $confFinish of ${st.size} in conference play" + if (ctResult == "Champions") " and won the conference tournament." else ".",
                when { confFinish == 1 || ctResult == "Champions" -> "Champions"; confFinish <= 4 -> "Contender"; confFinish <= st.size / 2 -> "Middle"; else -> "Struggled" }, confFinish <= st.size / 2),
            Driver("Postseason", tourneyText,
                when { round >= 5 -> "Deep run"; round >= 3 -> "Solid run"; round >= 1 -> "Made it"; result == "NIT Champion" -> "NIT title"; event == "NIT" -> "NIT"; else -> "Missed" },
                round >= 1 || result == "NIT Champion"),
            Driver("Quality wins", "$q1w Quadrant 1 win${if (q1w == 1) "" else "s"} on the resume.", when { q1w >= 8 -> "Strong"; q1w >= 3 -> "Some quality"; else -> "Thin" }, q1w >= 3),
            Driver("National awards", if (natAwards > 0) "$natAwards national honor${if (natAwards == 1) "" else "s"} for the program." else "No national honors this year.",
                if (natAwards > 0) "Award boost" else "None", natAwards > 0),
            draftSignal(lg, t),
            ceilingSignal(t),
            legacySignal(t),
        ) + listOfNotNull(
            rivalrySignal(lg, t),
            fanSignal(lg, t),
            nitReward?.let { Driver("NIT title", "+${it.prestige} prestige on top of the season's change, and ${it.xp} XP for ${it.players} returning players.", "+${it.prestige}", true) },
        )
        val drivers = mutableListOf(
            Driver("Season vs Expectation", "${t.w} wins against a %.0f-win target.".format(t.expWins), "%+d".format(diff.roundToInt().coerceIn(-6, 6)), diff >= 0),
            Driver("National Tournament", if (round == 0) "Missed the field." else "$result.", "%+d".format((round - 1).coerceIn(-1, 6)), round >= 1),
        )
        if (nitReward != null) drivers += Driver("NIT title", "Earned ${nitReward.points} extra coaching points.", "+${nitReward.points}", true)
        if (coachAwards.isNotEmpty()) drivers += Driver("Awards", coachAwards.joinToString(" + "), "+${coachAwards.size * 2}", true)
        // What the season earned, piece by piece: Prestige moves toward this number.
        val confAbbr = lg.conf(t.confId).abbr
        val parts = target?.let { x ->
            listOf(
                Driver("Record", "${t.w}-${t.l}" + if (x.resistance < 0.97) ", counted at ${(x.resistance * 100).roundToInt()}% in the $confAbbr" else "", "+%.0f".format(x.record * x.resistance), true),
                Driver("Conference", "Finished $confFinish" + if (ctResult == "Champions") ", won the tournament" else "", "+%.0f".format(x.conference * x.resistance), x.conference > 0),
                Driver("National stage", "${Brand.exposureLabel(Brand.exposure(lg, t.confId))} exposure in the $confAbbr", "%+.0f".format(x.stage), x.stage >= 0),
                Driver("March", tourneyText, "+%.0f".format(x.march), x.march > 0),
                Driver("Quality wins", "$q1w Quadrant 1 win${if (q1w == 1) "" else "s"}", "+%.0f".format(x.quality), x.quality > 0),
                Driver("Draft picks", "Players taken in last summer's draft", "+%.0f".format(x.draft), x.draft > 0),
                Driver("National awards", "$natAwards honor${if (natAwards == 1) "" else "s"}", "+%.0f".format(x.awards), x.awards > 0),
            )
        } ?: emptyList()
        return WrapUp(
            lg.season, t.id, result, seed, path, won,
            t.w, t.l, regW, regular.size - regW, t.cw, t.cl, t.expWins, t.net, t.ap, st.indexOf(t) + 1,
            t.prestige, t.prestige - prestigeBefore,
            Rankings.quadRecord(lg, t, 1), Rankings.quadRecord(lg, t, 2), Rankings.wab(lg, t), Rankings.sosRank(lg, t),
            mine, coachAwards, prestigeBefore, points, drivers, signals, ctResult,
            event, rounds.take(path.size), nitReward,
            earned = target?.total?.roundToInt() ?: 0, earnedParts = parts, climbRate = rate, streak = Brand.streak(t),
        )
    }

    private fun draftSignal(lg: League, t: Team): Driver {
        val picks = lg.draftHistory.filter { it.season == lg.season - 1 && it.teamId == t.id }
        return Driver(
            "Draft picks",
            if (picks.isEmpty()) "Nobody from the program was drafted last summer." else "${picks.size} drafted last summer: " + picks.joinToString { "${it.playerName} (No. ${it.pick})" } + ".",
            if (picks.isEmpty()) "None" else "Pro boost", picks.isNotEmpty(),
        )
    }

    private fun ceilingSignal(t: Team): Driver {
        val c = Brand.ceiling(t).roundToInt()
        return when {
            t.prestige <= 70 -> Driver("Resume ceiling", "Below 70, records and conference titles drive the climb. The ceiling ($c) starts to matter above 70.", "Not yet", true)
            t.prestige > c + 1 -> Driver(
                "Resume ceiling",
                "Prestige is above what your last 10 tournaments support ($c, ${Brand.ceilingLabel(c.toDouble()).lowercase()}). Climbing stays slow" +
                    (if (t.prestige > 80) ", and you'll drift down," else "") + " until you win in March.",
                "Above ceiling", false,
            )
            t.prestige >= c - 1 -> Driver("Resume ceiling", "Prestige is at your ceiling of $c (${Brand.ceilingLabel(c.toDouble()).lowercase()}). Deeper March runs raise it.", "At ceiling", false)
            else -> Driver("Resume ceiling", "Your last 10 tournaments support up to $c (${Brand.ceilingLabel(c.toDouble()).lowercase()}).", "Room to grow", true)
        }
    }

    private fun legacySignal(t: Team): Driver {
        val l = t.legacy.roundToInt()
        return when {
            l > t.prestige + 3 -> Driver("Legacy", "Legacy $l is above your Prestige, so it pulls you back up faster after good seasons.", "Pulls up", true)
            l < t.prestige - 3 -> Driver("Legacy", "Legacy $l hasn't caught up with your Prestige yet. It slows a quick rise until the results last.", "Holds back", false)
            else -> Driver("Legacy", "Legacy $l is in line with your Prestige.", "Steady", true)
        }
    }

    private fun fanSignal(lg: League, t: Team): Driver? {
        if (t.homeGames == 0) return null
        val boost = Fans.nilBoost(t, Finance.donor(t.prestige))
        val money = if (kotlin.math.abs(boost) >= 1000) "%.1fM".format(kotlin.math.abs(boost) / 1000.0) else "${kotlin.math.abs(boost)}K"
        return Driver(
            "Fan support",
            "Average crowd of ${"%,d".format(Fans.avgCrowd(t))} (${(Fans.avgFill(t) * 100).roundToInt()}% full), ${t.sellouts} sellout${if (t.sellouts == 1) "" else "s"}. Fans are ${Fans.mood(t.fans).lowercase()}.",
            "${if (boost >= 0) "+" else "−"}$$money NIL", boost >= 0,
        )
    }

    private fun rivalrySignal(lg: League, t: Team): Driver? {
        val games = lg.games.filter { it.played && it.involves(t.id) && lg.rivalry(it) != null }
        if (games.isEmpty()) return null
        val w = games.count { it.winner() == t.id }
        val opps = games.map { lg.team(it.opp(t.id)).school }.distinct().joinToString(" and ")
        val d = t.rivalryDelta
        return Driver("Rivalry games", "Went $w-${games.size - w} against $opps.", "%+d prestige".format(d), d >= 0)
    }

    /** Short label for where a team's season stands. */
    fun statusLine(lg: League): String = when (lg.phase) {
        Phase.REGULAR -> "Game ${(lg.user.gp + 1).coerceAtMost(Schedule.REG_DAYS)} of ${Schedule.REG_DAYS}"
        Phase.CONF_TOURNEY -> "Conference Tournament"
        Phase.NCAA -> when {
            firstFourDay(lg) -> "First Four"
            lg.nit?.hasTeam(lg.userTeamId) == true -> "NIT"
            else -> "NCAA Tournament"
        }
        Phase.WRAP_UP -> "Season Complete"
        Phase.OFFSEASON -> "Offseason"
        Phase.PLAYOFFS -> "Playoffs"
    }
}
