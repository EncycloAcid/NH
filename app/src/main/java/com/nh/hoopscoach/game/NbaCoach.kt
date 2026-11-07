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
        val u = lg.user
        val staying = lg.rosterOf(u).filter { !(it.proYears > 0 && it.contractYears <= 1) || it.id in ps.userKeep }
        val payroll = staying.sumOf { if (it.contractYears <= 1 && it.proYears > 0) ask(lg, it) else Payroll.salaryOf(lg, it) }
        return Payroll.cap(lg) - payroll - ps.userOffers.values.sum()
    }

    // ---------- trades ----------

    /** Trades are open all summer and in the season until the deadline. */
    fun canTrade(lg: League): Boolean = active(lg) && !(lg.proState?.fired ?: false) &&
        (lg.phase == Phase.OFFSEASON && lg.proState?.step != ProStep.REVIEW || lg.phase == Phase.REGULAR && lg.day <= Trades.deadlineDay(lg))

    class Verdict(val ok: Boolean, val text: String, val give: Double, val get: Double)

    /** How [other] sees a deal: what it gets from the user ([give], [givePicks]) against what it sends ([get], [getPicks]). */
    fun judge(lg: League, other: Team, give: List<Player>, get: List<Player>, givePicks: List<PickAsset>, getPicks: List<PickAsset>): Verdict {
        val u = lg.user
        if (give.isEmpty() && givePicks.isEmpty() || get.isEmpty() && getPicks.isEmpty()) return Verdict(false, "Pick something from each side.", 0.0, 0.0)
        val theyGet = give.sumOf { Trades.valueFor(lg, other, it) } + givePicks.sumOf { Picks.value(lg, it) }
        val theySend = get.sumOf { Trades.valueFor(lg, other, it) } + getPicks.sumOf { Picks.value(lg, it) }
        val inMine = get.sumOf { Payroll.salaryOf(lg, it) }
        val outMine = give.sumOf { Payroll.salaryOf(lg, it) }
        if (!Payroll.tradeWorks(lg, u, inMine, outMine)) return Verdict(false, "Your salaries don't match: over the cap you can take back at most 125% of what you send, plus $100K.", theyGet, theySend)
        if (!Payroll.tradeWorks(lg, other, outMine, inMine)) return Verdict(false, "Their salaries don't match: the ${other.name} are over the cap and can't take on that much.", theyGet, theySend)
        if (lg.rosterOf(u).size - give.size + get.size < 10 || lg.rosterOf(other).size - get.size + give.size < 10) return Verdict(false, "That leaves a roster too thin to play.", theyGet, theySend)
        // A team wants a little more than it gives, and won't move its franchise player for a pile of role players.
        val star = get.maxByOrNull { Trades.value(it) }
        if (star != null && Trades.tier(star) == "Franchise" && give.none { Trades.value(it) >= Trades.value(star) * 0.7 })
            return Verdict(false, "The ${other.name} won't move ${star.name} without a star coming back.", theyGet, theySend)
        val ok = theyGet >= theySend * 1.05
        val text = when {
            ok && theyGet >= theySend * 1.4 -> "They'd jump at it. You might be giving up too much."
            ok -> "The ${other.name} accept."
            theyGet >= theySend * 0.85 -> "Close. The ${other.name} want a little more."
            else -> "Not close. The ${other.name} hang up."
        }
        return Verdict(ok, text, theyGet, theySend)
    }

    /** Makes the deal if [other] accepts. */
    fun trade(lg: League, other: Team, give: List<Player>, get: List<Player>, givePicks: List<PickAsset>, getPicks: List<PickAsset>): Verdict {
        val v = judge(lg, other, give, get, givePicks, getPicks)
        if (!v.ok || !canTrade(lg)) return v
        val t = Trades.execute(lg, lg.user, other, give, get, picksA = givePicks, picksB = getPicks)
        lg.news += News(lg.season, lg.day, "Trade: " + Trades.describe(lg, t), true)
        return v
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
        c.security = (c.security + d.roundToInt()).coerceIn(0, 100)
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
        lg.news += News(lg.season, lg.day, "${c.name} is the new head coach of the ${t.name}", true)
    }
}
