package com.nh.hoopscoach.game

import kotlin.math.roundToInt

/**
 * League-wide free agency. When the offseason reaches free agency a year comes off every deal, and
 * players whose deals run out hit the market (the user's player has his own, and his rival stays
 * put). Best players first: his old team may bid with Bird rights (over the cap); other teams bid
 * with their cap room, or once a summer with the mid-level exception, if they have a roster spot.
 * He picks by money first, then winning, then a role. Veterans nobody wants retire; the rest take
 * the minimum where there's room.
 */
object Market {
    private class Bid(val team: Team, val pay: Int, val bird: Boolean, val mle: Boolean)

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
        val usedMle = mutableSetOf<Int>()
        val unsigned = mutableListOf<Player>()
        // Coaching a team: the user decides his own team's bids (who he brings back, who he offers).
        val coach = NbaCoach.active(lg)
        val me = lg.userTeamId
        ps.userMoves.clear()
        for ((p, from) in pool.sortedByDescending { Trades.value(it.first) }) {
            val ask = ProCareer.marketValue(lg, p)
            val bids = lg.teams.filter { it.roster.size < ProLeague.MAX_ROSTER }.mapNotNull { t ->
                val bird = t.id == from
                if (coach && t.id == me) {
                    val room = Payroll.room(lg, t)
                    return@mapNotNull when {
                        bird && p.id in ps.userKeep -> Bid(t, minOf((ask / 10) * 10, ProCareer.maxSalary(lg, p.proYears)), true, false)
                        !bird && p.id in ps.userOffers -> {
                            val pay = ps.userOffers.getValue(p.id)
                            when {
                                room >= pay -> Bid(t, pay, false, false)
                                pay <= Payroll.mle(lg) && t.id !in usedMle -> Bid(t, pay, false, true)
                                else -> null
                            }
                        }
                        else -> null
                    }
                }
                // His old team wants a good young player back more than an aging one; a rebuilding team lets veterans walk.
                if (bird && !Rng.chance(keepChance(lg, t, p))) return@mapNotNull null
                val room = Payroll.room(lg, t)
                val (limit, mle) = when {
                    bird -> Int.MAX_VALUE to false
                    room >= ask * 0.8 -> room to false
                    t.id !in usedMle -> Payroll.mle(lg) to true
                    else -> return@mapNotNull null
                }
                val pay = minOf((ask * (0.85 + Rng.d() * 0.25)).roundToInt() / 10 * 10, limit, ProCareer.maxSalary(lg, p.proYears))
                if (pay < ProCareer.minSalary(lg, p.proYears)) null else Bid(t, pay, bird, mle)
            }
            val best = bids.maxByOrNull { b ->
                val ahead = lg.rosterOf(b.team).count { it.pos == p.pos && it.ovr > p.ovr }
                b.pay / ask.toDouble() + b.team.winPct * 0.6 + (if (ahead == 0) 0.3 else if (ahead == 1) 0.15 else 0.0) + (if (b.bird) 0.1 else 0.0) + Rng.d() * 0.15
            }
            if (coach && (from == me || p.id in ps.userOffers)) {
                ps.userMoves += when {
                    best?.team?.id == me && from == me -> "${p.name} re-signs: ${Road.dollars(best.pay)} a season"
                    best?.team?.id == me -> "${p.name} signs with you: ${Road.dollars(best.pay)} a season"
                    from == me && p.id !in ps.userKeep -> "${p.name} moves on" + (best?.let { " to the ${it.team.name}" } ?: "")
                    from == me -> "${p.name} leaves" + (best?.let { " for the ${it.team.name} (${Road.dollars(it.pay)})" } ?: "")
                    else -> "${p.name} turns you down" + (best?.let { " for the ${it.team.name}" } ?: "")
                }
            }
            if (best == null) { unsigned += p; continue }
            sign(lg, p, best.team, best.pay)
            if (best.mle) usedMle += best.team.id
            if (best.team.id != from && Trades.value(p) >= 2.5) {
                val line = "${p.name} (${Ratings.shownPro(p.ovr)}) signs with the ${best.team.name}: ${p.contractYears} ${if (p.contractYears == 1) "year" else "years"}, ${Road.dollars(best.pay)} a season"
                ps.faMoves += line
                lg.news += News(lg.season, 0, "Free agency: $line, leaving the ${lg.team(from).name}", best.team.id == lg.userTeamId || from == lg.userTeamId)
                if (Trades.value(p) >= 8) Media.post(lg, "NBA Insider", "nbainsider", "insider", "${p.name} has agreed to a ${p.contractYears}-year, ${Road.dollars(best.pay * p.contractYears)} deal with the ${best.team.name}, per sources.", Media.likes(lg, 0.04), verified = true)
            } else if (best.team.id == from && Trades.value(p) >= 8) ps.faMoves += "${p.name} re-signs with the ${best.team.name}: ${p.contractYears} ${if (p.contractYears == 1) "year" else "years"}, ${Road.dollars(best.pay)} a season"
        }
        // Nobody bid: veterans retire, the rest take the minimum where there's a spot.
        for (p in unsigned) {
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
        p.salary = pay
        p.contractYears = when { p.age <= 25 -> Rng.range(3, 4); p.age <= 29 -> Rng.range(2, 4); p.age <= 32 -> Rng.range(1, 3); else -> 1 }
    }
}
