package com.nh.hoopscoach.game

import kotlin.math.roundToInt

/**
 * The offseason runs as a series of steps the user walks through:
 * Roster & NIL → Draft Night → Career Decision → Player Development → NIL Budget → Recruiting rounds
 * → Walk-on Tryouts.
 */
object Offseason {
    const val ROUNDS = Recruiting.ROUNDS

    // ---------- entering the offseason ----------

    fun begin(lg: League) {
        if (lg.phase != Phase.WRAP_UP) return
        val os = OffseasonState()
        val board = draftBoard(lg)
        for (p in lg.rosterOf(lg.user).filter { it.year >= 4 }) {
            os.graduates += GradEntry(p.id, p.name, p.pos, p.ovr, p.nil, Awards.line(p), gradNote(board[p.id]))
        }
        // In player mode the user decides for his own player.
        os.entrants += declarations(lg, board).filter { it != lg.heroId }
        lg.offseason = os
        Coaching.carousel(lg, os)
        Realignment.offseason(lg, os)
        lg.phase = Phase.OFFSEASON
    }

    fun gradNote(rank: Int?): String = when {
        rank == null || rank > 90 -> "Moving on after a four-year career."
        rank <= 30 -> "Projected first-round pick heading to draft night."
        rank <= 60 -> "Projected second-round pick, hoping to hear his name on draft night."
        else -> "Draft hopeful, hoping to hear his name on draft night."
    }

    private fun draftValue(p: Player) = p.ovr + (p.pot - p.ovr) * 0.5 - (p.year - 1) * 1.2

    /** Ranks every player by draft stock, 1 = best. Used for projections. */
    fun draftBoard(lg: League): Map<Int, Int> {
        val ranked = lg.players.filter { it.teamId >= 0 && it.year >= 1 }.sortedByDescending { draftValue(it) }
        return ranked.withIndex().associate { (i, p) -> p.id to i + 1 }
    }

    fun projection(rank: Int?, year: Int): String = when {
        rank == null -> "—"
        rank <= 14 -> "Lottery"
        rank <= 30 -> "1st Round"
        rank <= 60 -> "2nd Round"
        year <= 1 -> "TBD"
        else -> "Undrafted"
    }

    private fun declarations(lg: League, board: Map<Int, Int>): List<Int> {
        val out = mutableListOf<Int>()
        for (p in lg.players) {
            if (p.teamId < 0 || p.year !in 1..3) continue
            val rank = board[p.id] ?: continue
            if (rank > 45 || p.ovr < 76) continue
            var c = when { rank <= 14 -> 0.9; rank <= 30 -> 0.7; else -> 0.4 }
            c *= when (p.year) { 1 -> 0.85; 2 -> 0.95; else -> 1.0 }
            if (p.has(Trait.LOYAL)) c *= 0.6
            if (Rng.chance(c)) out += p.id
        }
        return out
    }

    // ---------- step 1: roster & NIL ----------

    fun userEntrants(lg: League): List<Player> {
        val os = lg.offseason ?: return emptyList()
        return os.entrants.mapNotNull { lg.playerOrNull(it) }.filter { it.teamId == lg.userTeamId }
    }

    /** Try to talk a draft entrant into another year. */
    fun pitchReturn(lg: League, pid: Int): Boolean {
        val os = lg.offseason ?: return false
        if (pid !in os.entrants || pid in os.pitched) return false
        os.pitched += pid
        val p = lg.player(pid)
        val rank = draftBoard(lg)[pid] ?: 99
        var c = when { rank <= 14 -> 0.08; rank <= 30 -> 0.3; else -> 0.6 } + (p.happiness - 60) / 200.0
        if (p.has(Trait.LOYAL)) c += 0.15
        if (lg.userCoach.has(CoachSkill.CULTURE)) c += 0.1
        if (!Rng.chance(c.coerceIn(0.03, 0.9))) return false
        os.entrants.remove(pid)
        p.happiness = (p.happiness + 5).coerceAtMost(100)
        lg.news += News(lg.season, lg.day, "${p.name} withdraws from the draft and returns to ${lg.user.school}", true)
        return true
    }

    /** Players on next year's roster being paid under their market value. */
    fun belowMarket(lg: League): List<Player> {
        val os = lg.offseason
        return lg.rosterOf(lg.user).filter { it.year < 4 && (os == null || it.id !in os.entrants) && Ratings.expectedNil(it) - it.nil >= 25 }
            .sortedByDescending { Ratings.expectedNil(it) - it.nil }
    }

    fun raiseNil(lg: League, pid: Int): Boolean {
        val p = lg.player(pid)
        val ask = Ratings.expectedNil(p)
        if (ask <= p.nil) return true
        if (Finance.available(lg, lg.user, includeSeniors = false) < ask - p.nil) return false
        p.nil = ask
        p.happiness = (p.happiness + 15).coerceAtMost(100)
        return true
    }

    /** Cut a player loose: he enters the transfer portal. */
    fun release(lg: League, pid: Int) {
        val t = lg.user
        if (pid !in t.roster) return
        val p = lg.player(pid)
        t.roster.remove(pid)
        lg.offseason?.let { it.released += p.name; it.freedOther += p.nil }
        toPortal(lg, p, t.id)
        Gameplan.validate(lg, t)
    }

    private fun toPortal(lg: League, p: Player, fromId: Int) {
        p.teamId = -1
        p.transferFrom = fromId
        val r = Recruit(p, transfer = true)
        r.nilAsk = Ratings.expectedNil(p)
        r.scouted = true
        r.estOvr = p.ovr
        r.estPot = p.pot
        r.pref = Recruiting.pickPref(if (p.ovr >= 75) 4 else 3)
        val target = (p.ovr * 1.35 - 28).coerceIn(10.0, 99.0)
        val k = Rng.range(3, 5)
        repeat(k * 3) {
            if (r.interest.size >= k) return@repeat
            val t = Rng.weighted(lg.teams) { tm ->
                if (tm.id == fromId || tm.id == lg.recruiterId) 0.0 else kotlin.math.exp(-((tm.prestige - target) / 15.0).let { it * it })
            }
            r.interest[t.id] = Rng.range(15, 45)
        }
        lg.recruits += r
    }

    private fun transferChance(lg: League, p: Player): Double {
        if (p.year >= 4) return 0.0
        var c = when (Morale.mood(p)) { Mood.TRANSFER -> 0.55; Mood.UNHAPPY -> 0.14; Mood.CONTENT -> 0.03; Mood.HAPPY -> 0.01 }
        if (p.has(Trait.DISLOYAL)) c *= 1.5
        if (p.has(Trait.LOYAL)) c *= 0.35
        val t = if (p.teamId >= 0) lg.team(p.teamId) else null
        if (t != null && lg.coachOf(t).has(CoachSkill.CULTURE)) c *= 0.7
        return c.coerceAtMost(0.85)
    }

    // ---------- moving between steps ----------

    fun advance(lg: League) {
        val os = lg.offseason ?: return
        when (os.step) {
            OffStep.ROSTER -> {
                if (lg.playerMode) Road.beforeDraft(lg, os)
                runTransfers(lg, os); runDraft(lg, os); os.step = OffStep.DRAFT
                if (lg.playerMode) Road.afterDraft(lg)
            }
            OffStep.DRAFT -> os.step = OffStep.CAREER
            OffStep.CAREER -> { if (os.fired) return; runDevelopment(lg, os); os.step = OffStep.DEVELOPMENT }
            OffStep.DEVELOPMENT -> os.step = OffStep.BUDGET
            OffStep.BUDGET -> { os.step = OffStep.RECRUITING; Recruiting.startCycle(lg) }
            OffStep.RECRUITING -> { Recruiting.lockRound(lg); if (os.round > ROUNDS) finish(lg) }
            OffStep.TRYOUTS -> {
                if (!lg.playerMode) Tryouts.finish(lg, os)
                fillHoles(lg, lg.user)
                startNewSeason(lg)
            }
        }
    }

    private fun runTransfers(lg: League, os: OffseasonState) {
        val entrants = os.entrants.toSet()
        for (t in lg.teams) {
            for (pid in t.roster.toList()) {
                val p = lg.player(pid)
                if (pid in entrants || pid == lg.heroId || pid == lg.road?.rivalId || !Rng.chance(transferChance(lg, p))) continue
                t.roster.remove(pid)
                if (t.id == lg.userTeamId) { os.transferredOut += p.name; os.freedOther += p.nil }
                toPortal(lg, p, t.id)
            }
            Gameplan.validate(lg, t)
        }
    }

    private fun runDraft(lg: League, os: OffseasonState) {
        val entrants = os.entrants.toSet()
        val pool = lg.players.filter { it.teamId >= 0 && (it.year >= 4 || it.id in entrants) }
            .map { it to draftValue(it) + Rng.gauss() * 2 }
            .sortedByDescending { it.second }
            .map { it.first }
        val picks = pool.take(60)
        picks.forEachIndexed { i, p ->
            val t = lg.team(p.teamId)
            lg.draftHistory += DraftPick(lg.season, i + 1, p.name, p.pos, t.id, t.school, p.year, p.ovr, p.look, p.number, p.id)
            lg.coachOf(t).drafted++
            p.awards += "${lg.season} Draft #${i + 1}"
            if (t.id == lg.userTeamId) {
                os.draftedCount++
                os.freedDraft += p.nil
                lg.news += News(lg.season, lg.day, "${p.name} goes No. ${i + 1} overall in the draft", true)
            }
        }
        val drafted = picks.map { it.id }.toSet()
        // Seniors graduate, draftees leave, undrafted underclassmen return.
        for (t in lg.teams) {
            for (pid in t.roster.toList()) {
                val p = lg.player(pid)
                if (p.year >= 4 || pid in drafted) {
                    if (t.id == lg.userTeamId && pid !in drafted) os.freedGrad += p.nil
                    hallOfFameCheck(lg, p, t)
                    Legacy.honorPlayer(lg, t, p)
                    t.roster.remove(pid)
                    lg.removePlayer(p)
                    Careers.keep(lg, p, t, if (pid in drafted) picks.indexOfFirst { it.id == pid } + 1 else 0)
                }
            }
        }
        os.entrants.retainAll(drafted)
        // Lineups can't point at players who just left.
        for (t in lg.teams) Gameplan.validate(lg, t)
    }

    private fun hallOfFameCheck(lg: League, p: Player, t: Team) {
        val aa = p.awards.count { it.endsWith("AA 1st") }
        val mvp = p.awards.count { it.endsWith("Nat MVP") }
        val mop = p.awards.count { it.endsWith("MOP") }
        if (mvp >= 1 || aa >= 2 || (aa >= 1 && mop >= 1)) {
            val tot = StatLine()
            p.career.forEach { tot.add(it.s) }
            lg.hallOfFame += HofEntry(lg.season, p.name, p.pos, t.school, "%.1f ppg career · %d× All-American%s".format(tot.ppg, aa, if (mvp > 0) " · MVP" else ""))
            if (t.id == lg.userTeamId) lg.userCoach.hof++
        }
    }

    private fun runDevelopment(lg: League, os: OffseasonState) {
        for (t in lg.teams) {
            val dev = Coaching.devOf(lg.coachOf(t))
            for (pid in t.roster) {
                val p = lg.player(pid)
                val before = p.ovr
                val attrs = p.attrs.copyOf()
                val brokeOut = Tryouts.summer(p)
                if (p.id == lg.heroId && lg.road != null) Earned.hero(lg, p, dev) else Ratings.offseasonGrowth(p, dev)
                val gains = IntArray(10) { p.attrs[it] - attrs[it] }
                val lbs = if (p.year <= 2) Rng.range(0, 8) else Rng.range(-2, 4)
                p.weight += lbs
                p.year++
                if (brokeOut) {
                    Tryouts.breakoutNews(lg, t, p, before)
                    if (t.id == lg.userTeamId) os.breakouts += pid
                }
                Tryouts.checkStar(lg, t, p)
                if (t.id == lg.userTeamId) os.growth += Growth(pid, before, p.ovr, gains, lbs)
            }
        }
        os.growth.sortByDescending { it.after - it.before }
    }

    // ---------- recruiting rounds ----------


    private fun finish(lg: League) {
        val os = lg.offseason ?: return
        os.signed += Recruiting.endCycle(lg)
        // Other programs fill their open scholarships; the user holds walk-on tryouts first.
        for (t in lg.teams) if (!lg.humanCoach(t)) { fillHoles(lg, t); Tryouts.aiTryout(lg, t) }
        // Unsigned transfers leave the game; the user's former players stay on record.
        lg.players.filter { it.teamId < 0 }.forEach { p ->
            lg.removePlayer(p)
            if (p.transferFrom == lg.userTeamId) Careers.keep(lg, p, lg.user, 0, "Left ${lg.user.school} through the transfer portal in ${lg.season}")
        }
        if (lg.playerMode) os.step = OffStep.TRYOUTS else Tryouts.open(lg, os)
    }

    /** Unrecruited players fill any scholarship left open. */
    private fun fillHoles(lg: League, t: Team) {
        while (Tryouts.scholarships(lg, t) < Recruiting.MAX_ROSTER) {
            val w = Tryouts.filler(lg, t)
            w.number = Recruiting.freeNumber(lg, t)
            lg.addPlayer(w)
            t.roster += w.id
        }
    }

    // ---------- new season ----------

    private fun startNewSeason(lg: League) {
        // Everyone in college, and every high school recruit, is a year older.
        for (p in lg.players) p.age = if (p.age <= 0) LeagueGen.collegeAge(p.year) else p.age + 1
        for (r in lg.recruits) if (lg.playerOrNull(r.p.id) == null) r.p.age = if (r.p.age <= 0) LeagueGen.collegeAge(0) else r.p.age + 1
        for (t in lg.teams) {
            for (pid in t.roster) {
                val p = lg.player(pid)
                p.stats = StatLine(); p.tourney = StatLine(); p.log = mutableListOf()
                p.seasonGains = IntArray(10); p.seasonLevels = 0
                p.happiness = ((p.happiness + 72) / 2.0).roundToInt()
                p.transferFrom = -1
            }
        }
        for (c in lg.coaches) {
            c.age++
            if (!c.isUser) Coaching.aiGrowth(c)
        }
        lg.season++
        // An era career's college years: this season's real players join their schools.
        EraCollege.populate(lg)
        setupSeason(lg, carryElo = true)
        lg.news += News(lg.season, 0, "Welcome to the ${lg.season} season", true)
        // Ring night: last season's national champions raise the banner at the opener.
        if (lg.history.lastOrNull()?.let { it.season == lg.season - 1 && it.champion == lg.userTeamId } == true) runCatching { Celebrations.ringNight(lg) }
        if (lg.news.size > 250) {
            val cut = lg.news.size - 250
            lg.news.subList(0, cut).clear()
            lg.newsSeen = (lg.newsSeen - cut).coerceAtLeast(0)
        }
    }

    private fun ordinalWord(n: Int) = "$n" + when { n % 100 in 11..13 -> "th"; n % 10 == 1 -> "st"; n % 10 == 2 -> "nd"; n % 10 == 3 -> "rd"; else -> "th" }

    /** Shared by a brand-new league and every new season. */
    fun setupSeason(lg: League, carryElo: Boolean) {
        Brand.ensure(lg)
        for (t in lg.teams) {
            if (carryElo) Brand.newSeason(t)
            t.w = 0; t.l = 0; t.cw = 0; t.cl = 0; t.streak = 0; t.rivalryDelta = 0
            Fans.newSeason(t)
            t.totals = StatLine(); t.opp = StatLine(); t.ap = 0; t.net = 0
            if (!lg.humanCoach(t)) Morale.aiNilRefresh(lg, t)
            if (!lg.humanCoach(t) || t.plan.auto) Gameplan.autoAllocate(lg, t) else Gameplan.validate(lg, t)
        }
        lg.phase = Phase.REGULAR
        lg.day = 1
        Injuries.clearAll(lg)
        lg.wrapUp = null
        lg.offseason = null
        lg.seasonAwards = mutableListOf()
        lg.confAwards = mutableListOf()
        lg.cotyCoachId = -1
        Records.ensure(lg)
        Schedule.build(lg)
        Rankings.seedElo(lg, carryElo)
        Rankings.computeExpectedWins(lg)
        Rankings.refresh(lg)
        lg.norms = GameEngine.computeNorms(lg)
        lg.recruits = LeagueGen.recruitClass(lg)
        lg.recruitPoints = Recruiting.SCOUT_CAP / 2
        val u = lg.user
        lg.news += News(lg.season, 0, if (u.ap > 0) "${u.school} opens the season ranked No. ${u.ap}" else "${u.school} opens the season unranked", true)
        val preview = Previews.build(lg)
        lg.preview = preview
        val top = lg.team(preview.proj.entries.first { it.value.nat == 1 }.key)
        lg.news += News(lg.season, 0, "The ${lg.season} preview is out: ${top.school} is the preseason No. 1, and ${u.school} is picked ${ordinalWord(preview.proj.getValue(u.id).confRank)} in the ${lg.conf(u.confId).abbr}")
        Season.prepareDay(lg)
    }

    fun newGame(teamId: Int, coachFirst: String, coachLast: String, startYear: Int = 2026): League =
        claimTeam(LeagueGen.newLeague(startYear), teamId, coachFirst, coachLast)

    /** Puts the user in charge of [teamId] in a freshly generated league and starts the season. */
    fun claimTeam(lg: League, teamId: Int, coachFirst: String, coachLast: String): League {
        val t = lg.team(teamId)
        val old = lg.coachOf(t)
        lg.coaches.remove(old)
        val c = Coach(lg.nextCoachId++, coachFirst, coachLast, 38, teamId, 68, 66, 70, 68, Rng.int(0, 1_000_000), isUser = true)
        lg.coaches += c
        t.coachId = c.id
        lg.userTeamId = teamId
        lg.userCoachId = c.id
        lg.reindex()
        setupSeason(lg, carryElo = false)
        return lg
    }

    /**
     * Runs every offseason step with default choices. Used by tests and "sim offseason". With
     * [stopForNba], it stops at the career decision when NBA teams have called: that one is the user's.
     * Returns true if it stopped there.
     */
    fun autoComplete(lg: League, stopForNba: Boolean = false): Boolean {
        if (lg.phase == Phase.WRAP_UP) begin(lg)
        var guard = 0
        while (lg.phase == Phase.OFFSEASON && guard++ < 20) {
            val os = lg.offseason ?: break
            if (stopForNba && os.step == OffStep.CAREER && (os.nbaOffers.isNotEmpty() || os.confInvites.isNotEmpty())) return true
            if (os.step == OffStep.CAREER && os.fired) Coaching.acceptOffer(lg, os.offers.first().teamId)
            if (os.step == OffStep.RECRUITING) Recruiting.autoPlan(lg)
            advance(lg)
        }
        return false
    }
}

object Coaching {

    private fun expectedRound(prestige: Int) = when {
        prestige >= 85 -> 3
        prestige >= 72 -> 2
        prestige >= 58 -> 1
        else -> 0
    }

    fun devOf(c: Coach) = c.dev + if (c.has(CoachSkill.DEVELOPER)) 8 else 0

    fun evaluateUser(lg: League) {
        val t = lg.user
        val c = lg.userCoach
        val round = Awards.tourneyRound(lg, t.id)
        var delta = (t.w - t.expWins) * 3 + (round - expectedRound(t.prevPrestige)) * 5
        if (lg.nit?.champion == t.id) delta += 4
        c.security = (c.security + delta).roundToInt().coerceIn(0, 100)
    }

    /** Coaching points earned this season. */
    fun pointsEarned(lg: League): Int {
        val t = lg.user
        val c = lg.userCoach
        val round = Awards.tourneyRound(lg, t.id)
        var pts = 3 + ((t.w - t.expWins) / 2).roundToInt().coerceIn(0, 6) + (round - 1).coerceAtLeast(0)
        if (lg.cotyCoachId == c.id) pts += 2
        if (lg.confAwards.any { it.pid == -c.id }) pts += 1
        if (lg.confBrackets.any { it.confId == t.confId && it.champion == t.id }) pts += 1
        if (lg.nit?.champion == t.id) pts += Season.NIT_POINTS
        return pts
    }

    fun cost(which: Int) = if (which == 0) 3 else 2

    /** Spend points on a rating: 0 offense, 1 defense, 2 recruiting, 3 development. */
    fun buyRating(c: Coach, which: Int): Boolean {
        val cost = cost(which)
        if (c.points < cost) return false
        val cur = when (which) { 0 -> c.off; 1 -> c.def; 2 -> c.rec; else -> c.dev }
        if (cur >= 99) return false
        c.points -= cost
        when (which) { 0 -> c.off++; 1 -> c.def++; 2 -> c.rec++; else -> c.dev++ }
        return true
    }

    const val SKILL_COST = 10

    fun buySkill(c: Coach, s: CoachSkill): Boolean {
        if (c.has(s) || c.points < SKILL_COST) return false
        c.points -= SKILL_COST
        c.skills += s
        return true
    }

    fun aiGrowth(c: Coach) {
        repeat(2) {
            val d = if (c.age > 66) -1 else if (c.age < 58 && Rng.chance(0.5)) 1 else 0
            when (Rng.int(0, 4)) { 0 -> c.off += d; 1 -> c.def += d; 2 -> c.rec += d; else -> c.dev += d }
        }
        c.off = c.off.coerceIn(30, 99); c.def = c.def.coerceIn(30, 99); c.rec = c.rec.coerceIn(30, 99); c.dev = c.dev.coerceIn(30, 99)
    }

    fun carousel(lg: League, os: OffseasonState) {
        for (t in lg.teams) {
            if (lg.humanCoach(t)) continue
            val c = lg.coachOf(t)
            val retire = c.age >= 72 || (c.age >= 66 && Rng.chance(0.3))
            val fired = !retire && t.w - t.expWins < -5 && Rng.chance(0.35)
            if (retire || fired) {
                Legacy.honorCoach(lg, t, c)
                if (retire && c.titles >= 2) lg.hallOfFame += HofEntry(lg.season, c.name, Pos.PG, t.school, "${c.w}-${c.l} · ${c.titles} titles", isCoach = true)
                lg.coaches.remove(c)
                val n = LeagueGen.makeCoach(lg, t.prestige, Rng.range(34, 55), withCareer = true)
                n.teamId = t.id
                t.coachId = n.id
                if (t.prestige >= 80) lg.news += News(lg.season, lg.day, "${t.school} hires ${n.name} as head coach", false)
            }
        }
        // In player mode the user doesn't coach: just keep "your team" and its coach in step.
        if (lg.playerMode) { Road.sync(lg); return }
        val u = lg.user
        val c = lg.userCoach
        val round = Awards.tourneyRound(lg, u.id)
        Lifetime.hallOfFame(lg)
        runCatching { Celebrations.coachHallOfFame(lg) }
        if (c.security < 15) {
            os.fired = true
            val pool = lg.teams.filter { it.id != u.id && it.prestige in (u.prestige - 40)..(u.prestige - 8) }.shuffled(Rng.r)
            val picks = (if (pool.size >= 2) pool else lg.teams.filter { it.id != u.id }.sortedBy { it.prestige }.take(20).shuffled(Rng.r)).take(3)
            picks.forEach { os.offers += JobOffer(it.id, "Offers you a fresh start") }
            lg.news += News(lg.season, lg.day, "${u.school} has fired ${c.name}", true)
        } else {
            NbaCoach.collegeOffers(lg, os, round)
            val strong = c.security >= 75 || round >= 4 || u.w - u.expWins >= 5
            val n = if (strong) Rng.range(2, 5) else if (c.security >= 55) Rng.range(0, 2) else 0
            lg.teams.filter { it.id != u.id && it.prestige in (u.prestige - 12)..(u.prestige + 35) }
                .sortedByDescending { it.prestige }.take(12).shuffled(Rng.r).take(n)
                .sortedByDescending { it.prestige }
                .forEach { os.offers += JobOffer(it.id, if (it.prestige > u.prestige) "Wants you to lead a bigger program" else "Interested in your rebuild skills") }
        }
    }

    /** User accepts a job: swaps benches with that school's coach. */
    fun acceptOffer(lg: League, teamId: Int) {
        val os = lg.offseason ?: return
        if (os.offers.none { it.teamId == teamId }) return
        Legacy.honorCoach(lg, lg.user, lg.userCoach)
        val oldTeam = lg.user
        val newTeam = lg.team(teamId)
        val me = lg.userCoach
        val other = lg.coachOf(newTeam)
        other.teamId = oldTeam.id
        oldTeam.coachId = other.id
        me.teamId = newTeam.id
        newTeam.coachId = me.id
        lg.userTeamId = newTeam.id
        me.security = 70
        newTeam.plan.auto = true
        os.offers.clear()
        os.fired = false
        os.confInvites.clear()
        os.graduates.clear()
        os.signed.clear()
        // Commitments made to the old school stay there.
        for (r in lg.recruits) { r.onBoard = false; r.alloc.clear() }
        lg.news += News(lg.season, lg.day, "${me.name} is the new head coach at ${newTeam.school}", true)
    }
}
