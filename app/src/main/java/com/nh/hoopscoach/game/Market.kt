package com.nh.hoopscoach.game

import kotlin.math.roundToInt

/**
 * League-wide free agency, run as a frenzy instead of a single wave. When the offseason reaches free
 * agency a year comes off every deal, and players whose deals run out hit the market (the user's
 * player has his own, and his rival stays put). Over several rounds teams chase the players they
 * actually need — a spot in their lineup that's short — spend their cap room as they go, and raise
 * their offers when a target is still unsigned. A player signs when one offer clearly leads, or when
 * the frenzy ends and he takes the best one on the table. His old team can always bid with Bird
 * rights (over the cap); everyone else needs cap room, or the once-a-summer mid-level exception.
 * Teams over the tax first buy out a contract to open room. Veterans nobody wants retire; the rest
 * take the minimum where there's a spot.
 */
object Market {
    private class Bid(val team: Team, var pay: Int, val bird: Boolean, val mle: Boolean)

    /** A free agent's standing offers: his old team's id, his asking price, and who has bid what. */
    private class Case(val p: Player, val from: Int, val ask: Int) {
        val bids = mutableListOf<Bid>()
    }

    private const val ROUNDS = 6

    fun run(lg: League) {
        val ps = lg.proState ?: return
        ps.faMoves.clear()
        val keep = setOf(lg.heroId, lg.road?.rivalId ?: -1)
        val pool = mutableListOf<Pair<Player, Int>>()
        for (t in lg.teams) for (p in lg.rosterOf(t).toList()) {
            if (p.id in keep || p.proYears == 0) continue
            if (p.contractYears > 0) p.contractYears--
            if (p.contractYears <= 0) { t.roster.remove(p.id); p.teamId = -1; pool += p to t.id }
        }
        // Over the tax before the frenzy: a team buys out its worst contract to open room (the player joins the market).
        for (t in lg.teams) {
            if (NbaCoach.active(lg) && t.id == lg.userTeamId) continue
            while (Payroll.total(lg, t) > Payroll.tax(lg)) {
                val worst = lg.rosterOf(t).filter { it.id !in keep }
                    .maxByOrNull { Payroll.salaryOf(lg, it).toDouble() / ProCareer.marketValue(lg, it).coerceAtLeast(1) } ?: break
                if (Payroll.salaryOf(lg, worst) <= ProCareer.marketValue(lg, worst) * 1.5) break
                t.roster.remove(worst.id)
                worst.teamId = -1
                pool += worst to t.id
                if (Trades.value(worst) >= 2.5) lg.news += News(lg.season, 0, "The ${t.name} waive ${worst.name} (${Road.dollars(Payroll.salaryOf(lg, worst))}) to clear room", false)
            }
        }
        val usedMle = mutableSetOf<Int>()
        val coach = NbaCoach.active(lg)
        val me = lg.userTeamId
        ps.userMoves.clear()

        // Outstanding commitments per team: cap room shrinks as its live offers pile up.
        val committed = HashMap<Int, Int>()
        fun roomLeft(t: Team) = Payroll.room(lg, t) - (committed[t.id] ?: 0)

        val cases = pool.map { (p, from) -> Case(p, from, ProCareer.marketValue(lg, p).coerceAtLeast(ProCareer.minSalary(lg, p.proYears))) }
            .sortedByDescending { Trades.value(it.p) }
        val signed = HashMap<Int, Team>()

        for (round in 0 until ROUNDS) {
            val last = round == ROUNDS - 1
            // Early rounds only teams with a real need chase a player; late rounds anyone with money does.
            val needGate = if (round < 3) 1.05 else 0.85
            for (c in cases) {
                val p = c.p
                if (signed.containsKey(p.id)) continue
                val suitors = mutableListOf<Team>()
                for (t in lg.teams) {
                    if (t.roster.size >= ProLeague.MAX_ROSTER) continue
                    val bird = t.id == c.from
                    if (coach && t.id == me) {
                        // The user's own lists act as his bids: kept players get their ask, targets his number.
                        val offer = when {
                            bird && p.id in ps.userKeep -> minOf((c.ask / 10) * 10, ProCareer.maxSalary(lg, p.proYears))
                            p.id in ps.userOffers -> ps.userOffers.getValue(p.id)
                            else -> continue
                        }
                        val bid = c.bids.firstOrNull { it.team.id == me } ?: Bid(t, 0, bird, false).also { c.bids += it }
                        if (offer > bid.pay) {
                            committed[t.id] = (committed[t.id] ?: 0) + (offer - bid.pay)
                            bid.pay = offer
                        }
                        suitors += t
                        continue
                    }
                    if (roomLeft(t) < c.ask * 0.7 && !bird && t.id !in usedMle) continue
                    // His old team wants a good young player back more than an aging one; a rebuilding team lets veterans walk.
                    if (bird && !Rng.chance(keepChance(lg, t, p))) continue
                    // Needs drive the market: teams chase the spots they're short at, not every name available.
                    if (Needs.needFactor(lg, t, p) < needGate && !bird) continue
                    suitors += t
                }
                // At most four serious bidders a round; neediest first.
                for (t in suitors.sortedByDescending { Needs.needFactor(lg, it, p) }.take(4)) {
                    if (coach && t.id == me) continue // the user's own bids were already set from his lists
                    val bird = t.id == c.from
                    val (limit, mle) = when {
                        bird -> Int.MAX_VALUE to false
                        roomLeft(t) >= c.ask * 0.7 -> roomLeft(t) to false
                        t.id !in usedMle -> Payroll.mle(lg) to true
                        else -> continue
                    }
                    // The bid rises as the frenzy wears on: a team losing targets pays closer to full ask.
                    val aggression = 0.85 + round * 0.06 + Rng.d() * 0.12
                    val want = (c.ask * aggression).roundToInt() / 10 * 10
                    val pay = minOf(want, limit, ProCareer.maxSalary(lg, p.proYears))
                    if (pay < ProCareer.minSalary(lg, p.proYears)) continue
                    val bid = c.bids.firstOrNull { it.team.id == t.id } ?: Bid(t, 0, bird, mle).also { c.bids += it }
                    if (pay > bid.pay) {
                        committed[t.id] = (committed[t.id] ?: 0) + (pay - bid.pay)
                        bid.pay = pay
                    }
                }
                // He signs when the leader is clear, or on the last round whenever anyone leads.
                val best = c.bids.maxByOrNull { bidScore(lg, c, it) } ?: continue
                val second = c.bids.filter { it !== best }.maxByOrNull { bidScore(lg, c, it) }
                val margin = bidScore(lg, c, best) - (second?.let { bidScore(lg, c, it) } ?: 0.0)
                if (margin >= 0.12 || last) {
                    signed[p.id] = best.team
                    sign(lg, p, best.team, best.pay)
                    if (best.mle) usedMle += best.team.id
                    if (coach && (c.from == me || p.id in ps.userOffers)) {
                        ps.userMoves += when {
                            best.team.id == me && c.from == me -> "${p.name} re-signs: ${Road.dollars(best.pay)} a season"
                            best.team.id == me -> "${p.name} signs with you: ${Road.dollars(best.pay)} a season"
                            c.from == me && p.id !in ps.userKeep -> "${p.name} moves on" + (second?.let { " to the ${it.team.name}" } ?: "")
                            c.from == me -> "${p.name} leaves" + (second?.let { " for the ${it.team.name} (${Road.dollars(it.pay)})" } ?: "")
                            else -> "${p.name} turns you down" + (second?.let { " for the ${it.team.name}" } ?: "")
                        }
                    }
                    if (best.team.id != c.from && Trades.value(p) >= 2.5) {
                        val line = "${p.name} (${Ratings.shownPro(p.ovr)}) signs with the ${best.team.name}: ${p.contractYears} ${if (p.contractYears == 1) "year" else "years"}, ${Road.dollars(best.pay)} a season"
                        ps.faMoves += line
                        lg.news += News(lg.season, 0, "Free agency: $line, leaving the ${lg.team(c.from).name}", best.team.id == lg.userTeamId || c.from == lg.userTeamId)
                        if (Trades.value(p) >= 8) Media.post(lg, "NBA Insider", "nbainsider", "insider", "${p.name} has agreed to a ${p.contractYears}-year, ${Road.dollars(best.pay * p.contractYears)} deal with the ${best.team.name}, per sources.", Media.likes(lg, 0.04), verified = true)
                    } else if (best.team.id == c.from && Trades.value(p) >= 8) ps.faMoves += "${p.name} re-signs with the ${best.team.name}: ${p.contractYears} ${if (p.contractYears == 1) "year" else "years"}, ${Road.dollars(best.pay)} a season"
                    // Every standing bid comes off the books: the winner's becomes real payroll, the losers' money frees.
                    for (b in c.bids) committed[b.team.id] = (committed[b.team.id] ?: 0) - b.pay
                }
            }
        }

        // Nobody signed them: veterans retire, the rest take the minimum where there's a spot.
        for (c in cases) {
            val p = c.p
            if (signed.containsKey(p.id)) continue
            val t = lg.teams.filter { it.roster.size < ProLeague.ROSTER }.minByOrNull { it.roster.size }
            if (p.age >= 32 || t == null) { AllTime.freeze(lg, p); lg.removePlayer(p); continue }
            sign(lg, p, t, ProCareer.minSalary(lg, p.proYears))
        }
        for (t in lg.teams) {
            while (t.roster.size < ProLeague.ROSTER) ProLeague.makePro(lg, Rng.pick(Pos.entries), Rng.range(80, 86), Rng.range(22, 30), t)
            Gameplan.autoAllocate(lg, t)
        }
        Payroll.sign(lg)
        ps.userKeep.clear()
        ps.userOffers.clear()
        lg.reindex()
    }

    /** How an offer stacks up for him: money first, then the team, a role for him, staying home. */
    private fun bidScore(lg: League, c: Case, b: Bid): Double {
        val ahead = Needs.depthAt(lg, b.team, c.p.pos).count { it >= c.p.ovr }
        return b.pay / c.ask.toDouble() + b.team.winPct * 0.6 + (if (ahead == 0) 0.3 else if (ahead == 1) 0.15 else 0.0) + (if (b.bird) 0.1 else 0.0)
    }

    private fun keepChance(lg: League, t: Team, p: Player): Double {
        var c = when { p.age <= 26 -> 0.85; p.age <= 29 -> 0.7; p.age <= 32 -> 0.5; else -> 0.3 }
        if (t.winPct < 0.42 && p.age >= 29) c -= 0.25
        if (t.winPct >= 0.58) c += 0.1
        return c.coerceIn(0.1, 0.95)
    }

    private fun sign(lg: League, p: Player, t: Team, pay: Int) {
        if (t.roster.any { lg.player(it).number == p.number }) p.number = Recruiting.freeNumber(lg, t)
        t.roster += p.id
        p.teamId = t.id
        ProLeague.trim(lg, t)
        p.salary = pay
        p.contractYears = when { p.age <= 25 -> Rng.range(3, 4); p.age <= 29 -> Rng.range(2, 4); p.age <= 32 -> Rng.range(1, 3); else -> 1 }
    }
}
