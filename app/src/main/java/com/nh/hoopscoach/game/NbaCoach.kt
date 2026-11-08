package com.nh.hoopscoach.game

import kotlin.math.roundToInt

/**
 * Coaching an NBA team: no player career, the user runs the bench and the front office. He sets the
 * rotation and the game plan, makes trades (until the deadline, and all summer), decides which of his
 * free agents to bring back and which free agents to chase. The owner judges him against the preseason
 * projection: win more than expected and win in the playoffs, and the job is safe; fall short for long
 * enough and he's fired, and the teams with openings call.
 */
object NbaCoach {
    fun active(lg: League) = lg.pro && lg.road == null && lg.mode == GameMode.COACH

    /** The rules and money of the era a season belongs to. */
    fun eraFor(season: Int): Era = when {
        season <= 1991 -> Era.Y1986
        season <= 1999 -> Era.Y1996
        season <= 2008 -> Era.Y2003
        season <= 2020 -> Era.Y2013
        else -> Era.TODAY
    }

    /** His players whose deals run out this summer. */
    fun expiring(lg: League): List<Player> = lg.rosterOf(lg.user).filter { it.proYears > 0 && it.contractYears <= 1 }.sortedByDescending { Trades.value(it) }

    /** The rest of the league's players whose deals run out this summer, best first. */
    fun market(lg: League): List<Player> = lg.teams.filter { it.id != lg.userTeamId }.flatMap { lg.rosterOf(it) }
        .filter { it.proYears > 0 && it.contractYears <= 1 }.sortedByDescending { Trades.value(it) }

    /** What a free agent asks for a season. */
    fun ask(lg: League, p: Player) = ProCareer.marketValue(lg, p)

    /** Cap room after the players he's bringing back (their asks replace their old deals). */
    fun room(lg: League): Int {
        val ps = lg.proState ?: return 0
        if (ps.step == ProStep.FREE_AGENCY && ps.faCases.isNotEmpty()) {
            return Market.availableRoom(lg, lg.user)
        }
        val u = lg.user
        val staying = lg.rosterOf(u).filter { !(it.proYears > 0 && it.contractYears <= 1) || it.id in ps.userKeep }
        val payroll = staying.sumOf { if (it.contractYears <= 1 && it.proYears > 0) ask(lg, it) else Payroll.salaryOf(lg, it) } + Payroll.deadCap(lg, u)
        return Payroll.cap(lg) - payroll - ps.userOffers.values.sum()
    }

    // ---------- trades ----------

    /** Trades are open all summer and in the season until the deadline. */
    fun canTrade(lg: League): Boolean = active(lg) && !(lg.proState?.fired ?: false) &&
        (lg.phase == Phase.OFFSEASON && lg.proState?.step != ProStep.REVIEW || lg.phase == Phase.REGULAR && lg.day <= Trades.deadlineDay(lg))

    class Verdict(
        val ok: Boolean,
        val text: String,
        val give: Double,
        val get: Double,
        val givePts: Int = (give * 100).roundToInt(),
        val getPts: Int = (get * 100).roundToInt(),
    )

    /**
     * How [other] sees a deal: what it gets from the user ([give], [givePicks]) against what it sends ([get], [getPicks]),
     * accounting for contract surplus/burden, retained salary ([retainMine], [retainTheirs]), pick protections ([protMine], [protTheirs]),
     * Untouchable status, and No-Trade Clauses (`NTC`).
     */
    fun judge(
        lg: League,
        other: Team,
        give: List<Player>,
        get: List<Player>,
        givePicks: List<PickAsset>,
        getPicks: List<PickAsset>,
        retainMine: Map<Int, Int> = emptyMap(),
        retainTheirs: Map<Int, Int> = emptyMap(),
        protMine: Map<Int, Int> = emptyMap(),
        protTheirs: Map<Int, Int> = emptyMap(),
    ): Verdict {
        val u = lg.user
        if (give.isEmpty() && givePicks.isEmpty() || get.isEmpty() && getPicks.isEmpty()) {
            return Verdict(false, "Pick something from each side.", 0.0, 0.0, 0, 0)
        }
        val theyGet = give.sumOf { p ->
            val r = retainMine[p.id] ?: 0
            (Trades.valueFor(lg, other, p) + Trades.contractDelta(lg, p, r) * 0.45).coerceAtLeast(-2.0)
        } + givePicks.sumOf { pk -> Picks.value(lg, pk, protMine[Picks.key(pk)]) }
        val theySend = get.sumOf { p ->
            val r = retainTheirs[p.id] ?: 0
            (Trades.valueFor(lg, other, p) + Trades.contractDelta(lg, p, r) * 0.45).coerceAtLeast(-2.0)
        } + getPicks.sumOf { pk -> Picks.value(lg, pk, protTheirs[Picks.key(pk)]) }

        val theirRetainCount = retainTheirs.count { (pid, pct) -> pct > 0 && get.any { it.id == pid } }
        val reqRatio = if (theirRetainCount > 0) 1.12 else 1.05
        val givePts = (theyGet * 100).roundToInt().coerceAtLeast(0)
        val getPts = (theySend * reqRatio * 100).roundToInt().coerceAtLeast(0)

        val ntcMine = give.firstOrNull { it.clause == "NTC" }
        if (ntcMine != null) return Verdict(false, "${ntcMine.name} has a No-Trade Clause (NTC) and blocks the trade.", theyGet, theySend, givePts, getPts)
        val ntcTheirs = get.firstOrNull { it.clause == "NTC" }
        if (ntcTheirs != null) return Verdict(false, "${ntcTheirs.name} has a No-Trade Clause (NTC) and won't waive it.", theyGet, theySend, givePts, getPts)

        val untouch = get.firstOrNull { Needs.isUntouchable(lg, other, it) }
        if (untouch != null) return Verdict(false, "The ${other.name} have marked ${untouch.name} as Untouchable.", theyGet, theySend, givePts, getPts)

        val myRetainCount = retainMine.count { (pid, pct) -> pct > 0 && give.any { it.id == pid } }
        if (Payroll.retainedSlotsUsed(lg, u) + myRetainCount > Payroll.MAX_RETAINED) {
            return Verdict(false, "You can retain salary on at most ${Payroll.MAX_RETAINED} contracts at once.", theyGet, theySend, givePts, getPts)
        }
        if (Payroll.retainedSlotsUsed(lg, other) + theirRetainCount > Payroll.MAX_RETAINED) {
            return Verdict(false, "The ${other.name} have no retained salary slots left.", theyGet, theySend, givePts, getPts)
        }

        val inMine = get.sumOf { Trades.effectiveSalary(lg, it, retainTheirs[it.id] ?: 0) }
        val outMine = give.sumOf { Trades.effectiveSalary(lg, it, retainMine[it.id] ?: 0) }
        if (!Payroll.tradeWorks(lg, u, inMine, outMine)) {
            return Verdict(false, "Your salaries don't match: over the cap you can take back at most 125% of what you send, plus $100K.", theyGet, theySend, givePts, getPts)
        }
        if (!Payroll.tradeWorks(lg, other, outMine, inMine)) {
            return Verdict(false, "Their salaries don't match: the ${other.name} are over the cap and can't take on that much.", theyGet, theySend, givePts, getPts)
        }
        val myAfter = lg.rosterOf(u).size - give.size + get.size
        val theirAfter = lg.rosterOf(other).size - get.size + give.size
        if (myAfter < 10 || theirAfter < 10) return Verdict(false, "That leaves a roster too thin to play (minimum 10).", theyGet, theySend, givePts, getPts)
        if (myAfter > ProLeague.MAX_ROSTER || theirAfter > ProLeague.MAX_ROSTER) return Verdict(false, "That puts a roster over the ${ProLeague.MAX_ROSTER}-player limit.", theyGet, theySend, givePts, getPts)

        // A team wants a little more than it gives, and won't move its franchise player for a pile of role players.
        val star = get.maxByOrNull { Trades.value(it) }
        if (star != null && Trades.tier(star) == "Franchise" && give.none { Trades.value(it) >= Trades.value(star) * 0.7 }) {
            return Verdict(false, "The ${other.name} won't move ${star.name} without a star coming back.", theyGet, theySend, givePts, getPts)
        }
        val ok = theyGet >= theySend * reqRatio
        val text = when {
            ok && theyGet >= theySend * 1.4 -> "They'd jump at it. You might be giving up too much."
            ok -> "The ${other.name} accept (${givePts} vs ${getPts} pts)."
            theyGet >= theySend * 0.85 -> "Close (${givePts} vs ${getPts} pts). The ${other.name} want a little more."
            else -> "Not close (${givePts} vs ${getPts} pts). The ${other.name} hang up."
        }
        return Verdict(ok, text, theyGet, theySend, givePts, getPts)
    }

    /** Makes the deal if [other] accepts. */
    fun trade(
        lg: League,
        other: Team,
        give: List<Player>,
        get: List<Player>,
        givePicks: List<PickAsset>,
        getPicks: List<PickAsset>,
        retainMine: Map<Int, Int> = emptyMap(),
        retainTheirs: Map<Int, Int> = emptyMap(),
        protMine: Map<Int, Int> = emptyMap(),
        protTheirs: Map<Int, Int> = emptyMap(),
    ): Verdict {
        val v = judge(lg, other, give, get, givePicks, getPicks, retainMine, retainTheirs, protMine, protTheirs)
        if (!v.ok || !canTrade(lg)) return v
        Trades.execute(lg, lg.user, other, give, get, picksA = givePicks, picksB = getPicks, retainA = retainMine, retainB = retainTheirs, protA = protMine, protB = protTheirs)
        // A new roster, a new target: the owner re-sets his expectation to the new projection.
        val ps = lg.proState
        if (ps != null && lg.phase == Phase.REGULAR) {
            ps.live = NbaPreviews.project(lg)
            ps.liveDay = lg.day
            val before = ps.expect
            ps.expect = ps.live[lg.userTeamId]?.w ?: ps.expect
            if (ps.expect != before) lg.news += News(lg.season, lg.day, "After the trade, the ${lg.user.name}' owner now expects about ${ps.expect} wins (was $before)", true)
        }
        return v
    }

    /**
     * BLM 27's "Auto-Balance Deal" magic button in the Trade Builder: adjusts salary retention, pick protections,
     * draft picks, or matching players so that salaries match and [other] accepts the trade.
     */
    fun autoBalance(
        lg: League,
        other: Team,
        giveIds: MutableSet<Int>,
        getIds: MutableSet<Int>,
        givePickKeys: MutableSet<Int>,
        getPickKeys: MutableSet<Int>,
        retainMine: MutableMap<Int, Int>,
        retainTheirs: MutableMap<Int, Int>,
        protMine: MutableMap<Int, Int>,
        protTheirs: MutableMap<Int, Int>,
    ): String {
        val u = lg.user
        val myRoster = lg.rosterOf(u)
        val theirRoster = lg.rosterOf(other)
        val myPicks = Picks.owned(lg, u)
        val theirPicks = Picks.owned(lg, other)
        val myUntouch = Needs.untouchableIds(lg, u)
        val theirUntouch = Needs.untouchableIds(lg, other)

        fun curGive() = myRoster.filter { it.id in giveIds }
        fun curGet() = theirRoster.filter { it.id in getIds }
        fun curGivePicks() = myPicks.filter { Picks.key(it) in givePickKeys }
        fun curGetPicks() = theirPicks.filter { Picks.key(it) in getPickKeys }
        fun curVerdict() = judge(lg, other, curGive(), curGet(), curGivePicks(), curGetPicks(), retainMine, retainTheirs, protMine, protTheirs)

        if (giveIds.isEmpty() && givePickKeys.isEmpty() && getIds.isEmpty() && getPickKeys.isEmpty()) {
            return "Select at least one player or pick first, then tap Auto-Balance."
        }

        // Case 1: User only selected what they want from [other]; find the best accepted package.
        if (giveIds.isEmpty() && givePickKeys.isEmpty() && curGet().size == 1 && getPickKeys.isEmpty()) {
            val targetPlayer = curGet().first()
            val found = TradeFinder.target(lg, targetPlayer, 1).firstOrNull()
            if (found != null) {
                giveIds.addAll(found.give.map { it.id })
                givePickKeys.addAll(found.givePicks.map { Picks.key(it) })
                return "Found package for ${targetPlayer.name}."
            }
        }
        // Case 2: User only selected what they're sending; find the best return from [other].
        if (getIds.isEmpty() && getPickKeys.isEmpty()) {
            val candidates = theirRoster.filter { it.id !in theirUntouch && it.clause != "NTC" }
                .sortedByDescending { Trades.valueFor(lg, u, it) + Trades.contractDelta(lg, it) * 0.35 }
            for (q in candidates) {
                getIds += q.id
                if (curVerdict().ok) return "Added ${q.name} from the ${other.name}."
                for (pk in theirPicks) {
                    getPickKeys += Picks.key(pk)
                    if (curVerdict().ok) return "Added ${q.name} + 1st-round pick from the ${other.name}."
                    getPickKeys -= Picks.key(pk)
                }
                getIds -= q.id
            }
        }

        if (curVerdict().ok) return "Deal already works — the ${other.name} accept!"

        // Step A: Try fixing salary matching first (via retention or adding a salary-matching player).
        val inSal = curGet().sumOf { Trades.effectiveSalary(lg, it, retainTheirs[it.id] ?: 0) }
        val outSal = curGive().sumOf { Trades.effectiveSalary(lg, it, retainMine[it.id] ?: 0) }
        if (!Payroll.tradeWorks(lg, other, outSal, inSal) && Payroll.canRetainMore(lg, u)) {
            for (p in curGive().sortedByDescending { Payroll.salaryOf(lg, it) }) {
                for (pct in listOf(25, 50)) {
                    retainMine[p.id] = pct
                    val newOut = curGive().sumOf { Trades.effectiveSalary(lg, it, retainMine[it.id] ?: 0) }
                    if (Payroll.tradeWorks(lg, other, newOut, inSal)) break
                }
            }
        }
        if (!Payroll.tradeWorks(lg, u, inSal, curGive().sumOf { Trades.effectiveSalary(lg, it, retainMine[it.id] ?: 0) })) {
            val myPool = myRoster.filter { it.id !in giveIds && it.id !in myUntouch && it.clause != "NTC" }
                .sortedWith(compareByDescending<Player> { Needs.isOnBlock(lg, u, it) }.thenBy { Trades.netValue(lg, it) })
            for (filler in myPool) {
                giveIds += filler.id
                val newOut = curGive().sumOf { Trades.effectiveSalary(lg, it, retainMine[it.id] ?: 0) }
                if (Payroll.tradeWorks(lg, u, inSal, newOut)) break
                if (giveIds.size >= 4) break
            }
        }

        if (curVerdict().ok) return "Balanced salaries — the ${other.name} accept!"

        // Step B: Remove pick protections on user picks if value is slightly short.
        for (k in givePickKeys) {
            if ((protMine[k] ?: 0) > 0) {
                protMine[k] = 0
                if (curVerdict().ok) return "Removed pick protection to close the deal."
            }
        }

        // Step C: Try retaining salary on an outgoing player to boost trade value.
        if (Payroll.canRetainMore(lg, u)) {
            for (p in curGive().sortedByDescending { Payroll.salaryOf(lg, it) }) {
                for (pct in listOf(25, 50)) {
                    retainMine[p.id] = pct
                    if (curVerdict().ok) return "Added $pct% salary retention on ${p.last} to seal the deal."
                }
                retainMine.remove(p.id)
            }
        }

        // Step D: Try adding 1 or 2 user draft picks (trying Top-10 protected first, then unprotected).
        val availPicks = myPicks.filter { Picks.key(it) !in givePickKeys }
        for (pk in availPicks) {
            val k = Picks.key(pk)
            givePickKeys += k
            for (prot in listOf(10, 5, 0)) {
                protMine[k] = prot
                if (curVerdict().ok) {
                    val pLabel = Picks.protectionLabel(prot).let { if (it.isNotEmpty()) " ($it)" else "" }
                    return "Added ${pk.season} 1st$pLabel to balance the trade."
                }
            }
            protMine.remove(k)
        }
        if (curVerdict().ok) return "Added draft picks to balance the trade."
        // Reset added picks that didn't solve it alone before trying player combinations.
        availPicks.forEach { givePickKeys -= Picks.key(it) }

        // Step E: Try adding a non-untouchable user player (+ optional pick).
        val pool = myRoster.filter { it.id !in giveIds && it.id !in myUntouch && it.clause != "NTC" }
            .sortedWith(compareByDescending<Player> { Needs.isOnBlock(lg, u, it) }.thenBy { Trades.value(it) })
        for (p in pool) {
            giveIds += p.id
            if (curVerdict().ok) return "Added ${p.name} to complete the trade."
            for (pk in availPicks.take(2)) {
                val k = Picks.key(pk)
                givePickKeys += k
                if (curVerdict().ok) return "Added ${p.name} + ${pk.season} 1st to complete the trade."
                givePickKeys -= k
            }
            giveIds -= p.id
        }

        return curVerdict().text
    }

    /** True when the regular season is within 5 days of the Trade Deadline. */
    fun isDeadlineWindow(lg: League): Boolean =
        active(lg) && lg.phase == Phase.REGULAR && lg.day in (Trades.deadlineDay(lg) - 5)..Trades.deadlineDay(lg)

    /**
     * BLM 27 Trade Deadline Day Frenzy: advances the deadline countdown by 1 hour (`1..6`), triggering
     * league-wide deadline deals and fresh incoming trade offers for the user's Trade Block.
     */
    fun deadlineStep(lg: League): List<String> {
        val ps = lg.proState ?: return emptyList()
        if (!canTrade(lg)) return emptyList()
        val beforeSize = ps.trades.size
        if (Rng.chance(0.75)) Trades.minor(lg)
        if (ps.deadlineHour >= 4 && Rng.chance(0.5)) Trades.deadline(lg)
        TradeFinder.makeOffers(lg)
        ps.deadlineHour = (ps.deadlineHour + 1).coerceIn(1, 6)
        val newDeals = ps.trades.drop(beforeSize).map { Trades.describe(lg, it) }
        val offerMsg = if (ps.tradeOffers.isNotEmpty()) "${ps.tradeOffers.size} active trade offers on your desk" else "Phones are quiet this hour"
        return listOf("Deadline Hour ${ps.deadlineHour}/6 · $offerMsg") + newDeals
    }

    // ---------- from college ----------

    /** The NBA season a college coach would start in, after college season [season]. */
    fun firstSeasonAfter(season: Int) = season + 1

    /** The teams of that NBA season, for the offer list. */
    fun teamsFor(season: Int): List<Team> = ProLeague.seasonTeams(firstSeasonAfter(season).coerceAtMost(ProLeague.DATA_SEASON + 1))

    /**
     * A college coach who wins big gets NBA calls in the offseason: a national title or a Final Four run
     * makes it likely, a great program and a long résumé possible. One to three teams call.
     */
    fun collegeOffers(lg: League, os: OffseasonState, round: Int) {
        if (lg.playerMode || lg.pro) return
        val c = lg.userCoach
        val chance = when {
            round == 7 -> 0.75
            round >= 5 -> 0.45
            round >= 4 && c.finalFours >= 1 -> 0.25
            c.titles >= 2 || (c.finalFours >= 3 && c.security >= 70) -> 0.2
            c.w >= 250 && c.tourneys >= 6 && round >= 2 -> 0.1
            else -> 0.0
        } + Lifetime.nbaBoost(lg, round) // a former NBA name gets calls sooner
        if (!Rng.chance(chance.coerceAtMost(0.9))) return
        val n = if (round >= 6) Rng.range(2, 3) else Rng.range(1, 2)
        os.nbaOffers.clear()
        val teams = teamsFor(lg.season)
        // His old NBA team (the last one he played for that's in the league that year) often calls first.
        val old = lg.playedAs?.proSlots.orEmpty().reversed().firstOrNull { s -> teams.any { it.slot == s } }
        if (old != null && Rng.chance(0.6)) os.nbaOffers += old
        os.nbaOffers += teams.filter { it.slot !in os.nbaOffers }.shuffled(Rng.r).take(n - os.nbaOffers.size).map { it.slot }
        lg.news += News(lg.season, lg.day, "NBA teams call ${c.name}", true)
        Alerts.add(lg, "NBA teams are calling", "${os.nbaOffers.size} NBA ${if (os.nbaOffers.size == 1) "team wants" else "teams want"} you as head coach. You'll find the offers on the Career decision step of the offseason.", "")
    }

    /**
     * A college coach takes an NBA job: the NBA league of the next season (real rosters through 2026-27,
     * then today's league aged forward to the year he arrives), with him on the bench of the team in [slot].
     * His college record stays with him.
     */
    fun fromCollege(college: League, slot: String): League {
        val c = college.userCoach
        val first = firstSeasonAfter(college.season)
        // A former player goes back to the league he played in, carried forward to the year he arrives:
        // the same teams, his old teammates and rivals (older now, or retired), the draft classes since.
        val world = college.proWorld.takeIf { it.isNotEmpty() }?.let { runCatching { SaveIO.unpack(it) }.getOrNull() }
        val pro = if (world != null) backTo(world, first, slot, c) else ProLeague.coachLeague(first.coerceAtMost(ProLeague.DATA_SEASON + 1), slot, c.first, c.last)
        // Past today's data, the league ages forward to the year he arrives.
        val gap = first - (pro.season + 1)
        if (gap > 0) ProLeague.ageForward(pro, gap)
        val pc = pro.userCoach
        pc.age = c.age
        pc.off = c.off; pc.def = c.def; pc.rec = c.rec; pc.dev = c.dev
        // His college seasons stay on his record (not tied to an NBA team).
        pc.history.clear()
        pc.history += c.history.map { CoachSeason(it.season, -1, it.team, it.w, it.l, it.result, it.prestige) }
        pc.security = 70
        pro.playedAs = college.playedAs
        Trophies.carry(college, pro)
        pro.proState!!.collegeResume = "${c.w}-${c.l} in college · ${c.tourneys} NCAA trip${if (c.tourneys == 1) "" else "s"} · ${c.finalFours} Final Four${if (c.finalFours == 1) "" else "s"} · ${c.titles} title${if (c.titles == 1) "" else "s"}"
        val head = "${pc.name} leaves ${college.user.school} for the ${pro.user.name}"
        runCatching { Farewell.hire(pro, head) }.onFailure { pro.news += News(pro.season, 0, head, true) }
        return pro
    }

    /** The league he played in, made ready for him to coach the team in [slot] in season [first]. */
    private fun backTo(lg: League, first: Int, slot: String, c: Coach): League {
        val ps = lg.proState!!
        lg.proWorld = ""
        val t = lg.teams.firstOrNull { it.slot == slot } ?: lg.teams.first()
        lg.coaches.forEach { it.isUser = false }
        runCatching { lg.coachOf(t) }.getOrNull()?.let { it.teamId = -1 }
        val me = Coach(lg.nextCoachId++, c.first, c.last, c.age, t.id, c.off, c.def, c.rec, c.dev, c.look, isUser = true, security = 70)
        lg.coaches += me
        t.coachId = me.id
        lg.userCoachId = me.id
        lg.userTeamId = t.id
        lg.mode = GameMode.COACH
        lg.road = null
        lg.heroId = -1
        // Where a new coaching league starts: the summer before his first season.
        lg.phase = Phase.OFFSEASON
        lg.day = 0
        ps.step = ProStep.SUMMER
        ps.fired = false
        ps.jobOffers.clear()
        ps.tradeOffers.clear()
        ps.userOffers.clear()
        ps.userKeep.clear()
        ps.expect = -1
        // The years he spent in college pass here too: everyone ages, the old guard retires, new classes arrive.
        val years = first - 1 - lg.season
        if (years > 0) ProLeague.ageForward(lg, years)
        for (tm in lg.teams) Gameplan.autoAllocate(lg, tm)
        Picks.ensure(lg)
        return lg
    }

    // ---------- the owner ----------

    /** Playoff series the team won this season (the title counts as one more). */
    private fun roundsWon(lg: League, t: Team): Int {
        val ps = lg.proState ?: return 0
        return ps.series.count { it.round >= 1 && it.done && it.winner == t.id } + if (ps.champion == t.id) 1 else 0
    }

    fun seasonsHere(lg: League): Int = lg.userCoach.history.count { it.teamId == lg.userTeamId }

    /** The owner's mood in a word. */
    fun securityLabel(v: Int) = when { v >= 75 -> "Untouchable"; v >= 55 -> "Secure"; v >= 35 -> "Under pressure"; v >= 20 -> "Hot seat"; else -> "On thin ice" }

    /**
     * Season over: the coach's record, then the owner's verdict. Each win above the projection adds,
     * each one below takes away; playoff series won add more, a title most of all. Below 15 after two
     * seasons, he's fired, and three teams with openings call.
     */
    fun seasonEnd(lg: League) {
        Lifetime.hallOfFame(lg)
        runCatching { Celebrations.coachHallOfFame(lg) }
        val ps = lg.proState ?: return
        val u = lg.user
        val c = lg.userCoach
        val result = ProSeason.resultFor(lg, u.id)
        val won = roundsWon(lg, u)
        // (His wins and losses count game by game.)
        if (u.id in ps.seeds) c.tourneys++
        if (ps.champion == u.id) c.titles++
        c.history += CoachSeason(lg.season, u.id, u.name, u.w, u.l, result, 0)
        val games = (u.w + u.l).coerceAtLeast(1)
        val expect = ps.expect.takeIf { it >= 0 } ?: (games / 2)
        val inPlayoffs = ps.seeds[u.id]?.let { it in 1..(if (Eras.of(lg).playIn) 6 else 8) } == true || ps.series.any { it.round >= 1 && (it.hi == u.id || it.lo == u.id) }
        var d = (u.w - expect) * 1.3 * 82.0 / games + won * 5.0 + (if (ps.champion == u.id) 15.0 else 0.0)
        if (!inPlayoffs && expect >= games * 0.55) d -= 6
        if (inPlayoffs && expect < games * 0.45) d += 5
        val before = c.security
        // The building: sellouts please the owner, empty seats don't.
        d += Business.bossVerdict(u)
        c.security = (c.security + d.roundToInt()).coerceIn(0, 100)
        // The owner's midseason goals.
        runCatching { FrontOffice.grade(lg) }
        lg.news += News(lg.season, lg.day, "The ${u.name} finish ${u.w}-${u.l} (projected $expect wins). " +
            "Job security: ${securityLabel(c.security).lowercase()} (${if (c.security >= before) "+" else "−"}${kotlin.math.abs(c.security - before)})", true)
        val recentTitle = c.history.takeLast(2).any { it.result == "Champion" }
        if (c.security < 15 && seasonsHere(lg) >= 2 && !recentTitle) fire(lg)
    }

    private fun fire(lg: League) {
        val ps = lg.proState ?: return
        val u = lg.user
        ps.fired = true
        ps.jobOffers.clear()
        ps.jobOffers += lg.teams.filter { it.id != u.id }.sortedBy { it.winPct }.take(6).shuffled(Rng.r).take(3).map { it.id }
        lg.news += News(lg.season, lg.day, "The ${u.name} fire head coach ${lg.userCoach.name}", true)
        Alerts.add(lg, "You've been fired", "The ${u.name} have let you go after a ${u.w}-${u.l} season. ${ps.jobOffers.size} teams with openings have called.", "")
    }

    // ---------- from the court ----------

    /**
     * A retired player's NBA head-coaching offers, made the day he retires: losing teams looking for a
     * new voice, one to three by how big a name he was (a legend can land a good team too), and the
     * team he finished with often wants him on its bench.
     */
    fun retiredOffers(lg: League): List<Int> {
        if (lg.road == null || !lg.pro) return emptyList()
        val tier = Lifetime.name(Lifetime.fromPlayer(lg))
        val n = when (tier) { 2 -> 3; 1 -> 2; else -> 1 }
        val out = mutableListOf<Int>()
        val last = lg.hero?.lastTeam ?: -1
        if (last in lg.teams.indices && Rng.chance(when (tier) { 2 -> 0.8; 1 -> 0.5; else -> 0.25 })) out += last
        val pool = lg.teams.sortedBy { it.winPct }.take(if (tier == 2) 20 else 10).map { it.id }.filter { it !in out }.shuffled(Rng.r)
        out += pool.take((n - out.size).coerceAtLeast(0))
        return out
    }

    /**
     * Straight from the court to the bench: the league he played in becomes his coaching career. He
     * replaces [teamId]'s coach, his playing career goes on his résumé, and the offseason carries on
     * from where he retired.
     */
    fun fromRetirement(lg: League, teamId: Int) {
        val h = lg.hero ?: return
        val ps = lg.proState ?: return
        if (lg.road == null) return
        lg.playedAs = Lifetime.fromPlayer(lg)
        val legacy = AllTime.heroScore(lg)
        val peak = h.ovrHistory.maxOrNull() ?: h.ovr
        AllTime.freeze(lg, h) // he stays on the all-time list as a retired player
        val t = lg.team(teamId)
        runCatching { lg.coachOf(t) }.getOrNull()?.let { old -> old.teamId = -1; old.isUser = false }
        lg.coaches.forEach { it.isUser = false }
        val c = Coach(lg.nextCoachId++, h.first, h.last, h.age + 1, t.id,
            (55 + (peak - 85) * 2.5).roundToInt().coerceIn(55, 92), (52 + (peak - 85) * 2.0).roundToInt().coerceIn(50, 88),
            (60 + legacy / 3).roundToInt().coerceIn(60, 95), 62, Rng.int(0, 1_000_000), isUser = true, security = 65)
        lg.coaches += c
        t.coachId = c.id
        lg.userCoachId = c.id
        lg.userTeamId = t.id
        lg.mode = GameMode.COACH
        lg.road = null
        lg.heroId = -1
        ps.fired = false
        ps.jobOffers.clear()
        ps.tradeOffers.clear()
        ps.userOffers.clear()
        ps.userKeep.clear()
        ps.expect = -1
        // Past the draft already (he retired when nobody called): his free agents are up now.
        if (ps.step != ProStep.REVIEW) ps.userKeep += expiring(lg).filter { Trades.value(it) >= 1.5 }.map { it.id }
        Gameplan.autoAllocate(lg, t)
        val was = lg.playedAs?.proTeams?.contains(t.name) == true
        val head = "From the court to the bench: the ${t.name} name ${h.name} head coach" + if (was) ", back with a team he played for" else ""
        // The farewell screen is behind him; now the press conference.
        lg.ceremony = null
        lg.ceremonyQueue.clear()
        runCatching { Farewell.hire(lg, head) }.onFailure { lg.news += News(lg.season, lg.day, head, true) }
    }

    /** A new job: he takes over [teamId], whose coach is let go. */
    fun takeJob(lg: League, teamId: Int) {
        val ps = lg.proState ?: return
        val t = lg.team(teamId)
        val c = lg.userCoach
        val old = lg.coachOf(t)
        if (old.id != c.id) old.teamId = -1
        // His old team hires a new coach.
        val prev = lg.user
        if (prev.id != t.id && prev.coachId == c.id) {
            val n = Coach(lg.nextCoachId++, Rng.pick(Names.first), Rng.pick(Names.last), Rng.range(38, 62), prev.id,
                Rng.range(55, 92), Rng.range(55, 92), Rng.range(40, 80), Rng.range(50, 90), Rng.int(0, 1_000_000))
            lg.coaches += n
            prev.coachId = n.id
        }
        c.teamId = t.id
        t.coachId = c.id
        lg.userTeamId = t.id
        c.security = 60
        ps.fired = false
        ps.jobOffers.clear()
        Gameplan.autoAllocate(lg, t)
        val head = "${c.name} is the new head coach of the ${t.name}"
        runCatching { Farewell.hire(lg, head) }.onFailure { lg.news += News(lg.season, lg.day, head, true) }
    }
}
