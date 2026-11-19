package com.nh.hoopscoach.game

import kotlin.math.roundToInt

/**
 * Contract extensions during the season, and auto re-sign.
 *
 * A player in the last year of his deal can be extended any time in the regular season: the new money
 * starts next season (it waits in [Player.nextSalary]) and the years are added on. With **auto re-sign**
 * on, the front office does it for the coach: at the All-Star break it extends the expiring players worth
 * keeping at their asking price, and when the season's over it handles the rest (team options, qualifying
 * offers, extensions) before free agency opens. Other teams lock up their own good players at the break
 * too, so not every star reaches the market.
 */
object Extensions {

    /** Can the coach extend [p] right now, during the season? */
    fun canExtend(lg: League, p: Player): Boolean =
        NbaCoach.active(lg) && lg.phase == Phase.REGULAR && p.teamId == lg.userTeamId && p.id != lg.heroId &&
            p.proYears > 0 && p.contractYears == 1 && p.nextSalary == 0

    /** A player worth keeping: one of the team's top nine, or a young player who can play. */
    fun keeper(lg: League, t: Team, p: Player): Boolean {
        if (p.age >= 35) return false
        val top = lg.rosterOf(t).sortedByDescending { it.ovr }.take(9).map { it.id }
        return p.id in top || (p.age <= 24 && Ratings.shownPro(p.ovr) >= 74)
    }

    /**
     * Extends [p]: in season the new deal waits for next year; before free agency it starts at once
     * (see [Market.negotiateExtension]). Returns whether he signed, and a line for the screen.
     */
    fun extend(lg: League, p: Player, salary: Int, years: Int, clause: String = "", structure: String = "FLAT", auto: Boolean = false): Pair<Boolean, String> {
        if (lg.phase != Phase.REGULAR) return Market.negotiateExtension(lg, p, salary, years, clause, structure)
        if (!canExtend(lg, p)) return false to "${p.name} can't sign an extension right now."
        val yrs = years.coerceIn(1, 5)
        val eval = Market.evaluateOffer(lg, lg.user, p, salary, yrs, clause, structure, fromTeamId = lg.userTeamId, isExtension = true)
        if (!eval.legal) return false to eval.illegalReason
        if (eval.pct < 52) return false to "${p.name} turned it down (${eval.pct}% interested): he wants closer to ${Road.dollars(eval.effectiveAsk)} a season for ${Market.desiredYears(p)} years."
        p.nextSalary = salary
        p.contractYears = 1 + yrs
        p.clause = clause
        p.structure = structure
        val total = Road.dollars(salary * yrs)
        lg.news += News(lg.season, lg.day, "The ${lg.user.name} sign ${p.name} to a $yrs-year, $total extension", true, -1, "signing",
            "${p.name} (${p.posLabel}, ${p.age}) agreed to a $yrs-year extension worth ${Road.dollars(salary)} a season, starting next season." +
                (if (auto) " The front office got it done at the break." else ""), "Hoops Wire", lg.userTeamId, p.id)
        return true to "${p.name} signed a $yrs-year extension: ${Road.dollars(salary)} a season from next year."
    }

    /** What he'll take: his asking price for the years he wants, and a little more if that's not enough. */
    private fun tryAtAsk(lg: League, p: Player, auto: Boolean): Pair<Boolean, String> {
        val ask = NbaCoach.ask(lg, p)
        val yrs = Market.desiredYears(p)
        val clause = Market.desiredClause(p).takeIf { it != "TO" } ?: ""
        val max = ProCareer.maxSalary(lg, p.proYears)
        for (k in doubleArrayOf(1.0, 1.06, 1.12)) {
            val sal = ((ask * k).roundToInt() / 10 * 10).coerceIn(ProCareer.minSalary(lg, p.proYears), max)
            val eval = Market.evaluateOffer(lg, lg.user, p, sal, yrs, clause, "FLAT", fromTeamId = lg.userTeamId, isExtension = true)
            if (eval.legal && eval.pct >= 52) return extend(lg, p, sal, yrs, clause, "FLAT", auto)
        }
        return false to "${p.name} wants to see what's out there."
    }

    /** Would bringing him back at [ask] blow past the apron? Stars always come back. */
    private fun tooRich(lg: League, t: Team, p: Player, ask: Int): Boolean {
        val stars = lg.rosterOf(t).sortedByDescending { Trades.value(it) }.take(3).map { it.id }
        if (p.id in stars) return false
        return Payroll.total(lg, t) - Payroll.salaryOf(lg, p) + ask > Payroll.apron(lg)
    }

    /** At the All-Star break: the user's auto re-signs, and other teams extend their own. */
    fun allStarBreak(lg: League) {
        val ps = lg.proState ?: return
        if (NbaCoach.active(lg) && ps.autoResign) {
            val signed = mutableListOf<String>()
            for (p in lg.rosterOf(lg.user).filter { canExtend(lg, it) && keeper(lg, lg.user, it) }.sortedByDescending { Trades.value(it) }) {
                if (tooRich(lg, lg.user, p, NbaCoach.ask(lg, p))) continue
                val (ok, _) = tryAtAsk(lg, p, auto = true)
                if (ok) signed += "${p.name} (${p.contractYears - 1} yrs, ${Road.dollars(p.nextSalary)})"
            }
            if (signed.isNotEmpty()) Alerts.add(lg, "Extensions done", "Auto re-sign at the break: " + signed.joinToString(", ") + ".", "signing")
        }
        aiExtensions(lg)
    }

    /** Other teams lock up their good players in the last year of their deals. */
    private fun aiExtensions(lg: League) {
        val keep = setOf(lg.heroId, lg.road?.rivalId ?: -1)
        for (t in lg.teams) {
            if (NbaCoach.active(lg) && t.id == lg.userTeamId) continue
            for (p in lg.rosterOf(t).filter { it.contractYears == 1 && it.proYears > 0 && it.nextSalary == 0 && it.id !in keep }) {
                val v = Trades.value(p)
                if (v < 2.5 || p.age >= 33) continue
                val chance = when { p.age <= 25 -> 0.6; p.age <= 29 -> 0.5; else -> 0.35 }
                if (!Rng.chance(chance)) continue
                val pay = (ProCareer.marketValue(lg, p) / 10 * 10).coerceIn(ProCareer.minSalary(lg, p.proYears), ProCareer.maxSalary(lg, p.proYears))
                if (v < 6 && Payroll.total(lg, t) - Payroll.salaryOf(lg, p) + pay > (Payroll.apron(lg) * 1.05).roundToInt()) continue
                val yrs = Market.desiredYears(p)
                p.nextSalary = pay
                p.contractYears = 1 + yrs
                if (p.clause == "TO" || p.clause == "RFA" || p.clause == "QO") p.clause = ""
                if (v >= 5) lg.news += News(lg.season, lg.day, "The ${t.name} sign ${p.name} to a $yrs-year, ${Road.dollars(pay * yrs)} extension", false, -1, "signing",
                    "${p.name} won't reach free agency: ${t.name} locked him up for $yrs more years at ${Road.dollars(pay)} a season.", "Hoops Wire", t.id, p.id)
            }
        }
    }

    /**
     * When the season's over (the draft step), with auto re-sign on: options, qualifying offers and
     * extensions for the expiring players worth keeping. Those who won't sign now go to free agency
     * with the team bidding to bring them back.
     */
    fun beforeFreeAgency(lg: League) {
        val ps = lg.proState ?: return
        if (!NbaCoach.active(lg) || !ps.autoResign) return
        val u = lg.user
        for (p in NbaCoach.expiring(lg)) {
            val want = keeper(lg, u, p)
            val ask = NbaCoach.ask(lg, p)
            when (p.clause) {
                "TO" -> Market.decideTeamOption(lg, p, want && Payroll.salaryOf(lg, p) <= ask * 1.15)
                "PO" -> {}
                "RFA", "QO" -> if (want && !tooRich(lg, u, p, ask)) {
                    if (p.clause == "RFA") Market.tenderQualifyingOffer(lg, p, true)
                    val (ok, _) = tryAtAsk(lg, p, auto = true)
                    if (!ok) ps.userKeep.let { if (p.id !in it) it += p.id }
                }
                else -> if (want && !tooRich(lg, u, p, ask)) {
                    val (ok, _) = tryAtAsk(lg, p, auto = true)
                    if (!ok && p.id !in ps.userKeep) ps.userKeep += p.id
                } else ps.userKeep.remove(p.id)
            }
        }
        ps.userMoves += "Auto re-sign: done (turn it off on the Payroll tab to handle contracts yourself)"
    }

    /** A new season's money: deals signed as extensions start now. */
    fun applyNext(p: Player) {
        if (p.nextSalary > 0) { p.salary = p.nextSalary; p.nextSalary = 0 }
    }

    fun applyAll(lg: League) { for (p in lg.players) applyNext(p) }
}
