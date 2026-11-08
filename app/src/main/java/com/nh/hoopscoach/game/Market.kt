package com.nh.hoopscoach.game

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * League-wide free agency & contract negotiation engine, modeled after Basketball Legacy Manager 27:
 * - Pre-free agency **Team Options (`TO`)**, **Player Options (`PO`)**, **Qualifying Offers (`QO` / `RFA`)**,
 *   and **Contract Extensions** with live 0–100% **Signing Interest** and factor breakdowns.
 * - Multi-round **Free Agency Frenzy** (6 rounds): in Coach Career, the user can negotiate contracts
 *   round-by-round (choosing Years, Salary, Structure `FLAT`/`FRONT`/`BACK`, and Clauses `PO`/`TO`/`NTC`),
 *   track competing AI offers, and match or decline **RFA Offer Sheets** before advancing to the next round.
 */
object Market {
    private class Bid(val team: Team, var pay: Int, val bird: Boolean, val mle: Boolean)

    /** A free agent's standing offers in one-shot Player Career market runs. */
    private class Case(val p: Player, val from: Int, val ask: Int) {
        val bids = mutableListOf<Bid>()
    }

    const val ROUNDS = 6

    /** Preferred contract length by player age. */
    fun desiredYears(p: Player): Int = when {
        p.age <= 25 -> 4
        p.age <= 29 -> 3
        p.age <= 32 -> 2
        else -> 1
    }

    /** Preferred contract clause by player stature and age. */
    fun desiredClause(p: Player): String = when {
        p.ovr >= 96 && p.proYears >= 8 -> "NTC"
        p.ovr >= 93 && p.age >= 26 -> "PO"
        p.age <= 23 && p.ovr <= 84 -> "TO"
        else -> ""
    }

    /** BLM 27 Signing Interest evaluation for a contract offer or extension. */
    class InterestEval(
        val pct: Int,
        val label: String,
        val factors: List<Pair<String, Boolean>>,
        val effectiveAsk: Int,
        val capHow: String,
        val legal: Boolean,
        val illegalReason: String = "",
    )

    /** Committed cap room from the team's active non-Bird, non-MLE offers on unsigned free agents. */
    fun committedRoom(lg: League, t: Team, excludePid: Int = -1): Int {
        val ps = lg.proState ?: return 0
        var sum = 0
        for (fc in ps.faCases) {
            if (fc.signedTeamId >= 0 || fc.pid == excludePid) continue
            val b = fc.bids.firstOrNull { it.teamId == t.id } ?: continue
            if (!b.bird && !b.mle) sum += b.salary
        }
        for ((pid, b) in ps.pendingRfaMatches) {
            if (pid != excludePid && b.teamId == t.id && !b.bird && !b.mle) sum += b.salary
        }
        return sum
    }

    /** Cap space remaining for [t] after accounting for active free agency offers. */
    fun availableRoom(lg: League, t: Team, excludePid: Int = -1): Int =
        Payroll.room(lg, t) - committedRoom(lg, t, excludePid)

    /**
     * Evaluates how interested [p] is in an offer from [t] (`0..100%`), what salary he effectively asks
     * given the [years], [clause], and [structure], and whether the offer is cap-legal.
     */
    fun evaluateOffer(
        lg: League,
        t: Team,
        p: Player,
        salary: Int,
        years: Int,
        clause: String = "",
        structure: String = "FLAT",
        fromTeamId: Int = p.teamId,
        isExtension: Boolean = false,
    ): InterestEval {
        val ps = lg.proState
        val fc = ps?.faCases?.firstOrNull { it.pid == p.id }
        val baseAsk = fc?.askSalary ?: ProCareer.marketValue(lg, p).coerceAtLeast(ProCareer.minSalary(lg, p.proYears))
        val wantYrs = fc?.askYears ?: desiredYears(p)
        var askMult = 1.0
        if (years < wantYrs && p.age <= 30) askMult += (wantYrs - years) * 0.04
        if (years > wantYrs && p.age >= 31) askMult -= (years - wantYrs) * 0.03
        when (clause) {
            "PO" -> askMult -= 0.06
            "NTC" -> askMult -= 0.05
            "TO" -> askMult += 0.07
        }
        when (structure) {
            "FRONT" -> askMult -= 0.02
            "BACK" -> askMult += 0.02
        }
        val minSal = ProCareer.minSalary(lg, p.proYears)
        val maxSal = ProCareer.maxSalary(lg, p.proYears)
        val effectiveAsk = ((baseAsk * askMult).roundToInt() / 10 * 10).coerceIn(minSal, maxSal)

        val factors = mutableListOf<Pair<String, Boolean>>()
        val ratio = salary.toDouble() / effectiveAsk.coerceAtLeast(1)
        var raw = if (ratio >= 1.0) 58.0 + (ratio - 1.0) * 140.0 else 58.0 - (1.0 - ratio) * 210.0

        when {
            ratio >= 1.08 -> factors += "+Generous offer" to true
            ratio >= 0.96 -> factors += "+Fair market pay" to true
            else -> factors += "-Below asking price" to false
        }

        val ahead = lg.rosterOf(t).count { it.id != p.id && it.pos == p.pos && it.ovr >= p.ovr }
        when (ahead) {
            0 -> { raw += 12; factors += "+Starter at ${p.pos.label}" to true }
            1 -> { raw += 4; factors += "+Rotation role (${p.pos.label})" to true }
            else -> { raw -= 12; factors += "-Depth chart logjam (${p.pos.label})" to false }
        }

        val gp = t.w + t.l
        when {
            gp >= 10 && t.winPct >= 0.56 -> { raw += 10; factors += "+Contender (${t.w}-${t.l})" to true }
            gp >= 10 && t.winPct < 0.40 && p.age >= 28 -> { raw -= 10; factors += "-Rebuilding team" to false }
            gp >= 10 && t.winPct < 0.42 && p.age <= 24 -> { raw += 5; factors += "+Young core minutes" to true }
        }

        val bird = fromTeamId == t.id
        if (bird) {
            raw += 10
            factors += "+Bird rights / Loyalty" to true
        }

        when (clause) {
            "PO" -> { raw += 8; factors += "+Player Option" to true }
            "NTC" -> { raw += 7; factors += "+No-Trade Clause" to true }
            "TO" -> { raw -= 8; factors += "-Team Option" to false }
        }

        when (structure) {
            "FRONT" -> { raw += 3; factors += "+Front-loaded pay" to true }
            "BACK" -> { raw -= 2; factors += "-Back-loaded pay" to false }
        }

        if (years == wantYrs) {
            raw += 4
            factors += "+Preferred $years-yr term" to true
        } else if (abs(years - wantYrs) >= 2) {
            raw -= 6
            factors += "-Prefers $wantYrs yrs" to false
        }

        if (t.prestige >= 82) {
            raw += 4
            factors += "+Big market (${t.school})" to true
        }

        val pct = (if (ratio < 0.65) minOf(raw.roundToInt(), 15) else raw.roundToInt()).coerceIn(0, 100)
        val label = when {
            pct >= 80 -> "Very High"
            pct >= 62 -> "High"
            pct >= 50 -> "Moderate"
            pct >= 30 -> "Low"
            else -> "Very Low"
        }

        // Cap legality check
        if (salary < minSal) {
            return InterestEval(pct, label, factors, effectiveAsk, "Below min", false, "Minimum salary for ${p.name} is ${Road.dollars(minSal)}.")
        }
        if (salary > maxSal) {
            return InterestEval(pct, label, factors, effectiveAsk, "Over max", false, "Maximum salary for ${p.name} is ${Road.dollars(maxSal)}.")
        }
        if (isExtension) {
            return InterestEval(pct, label, factors, effectiveAsk, "Bird extension", true)
        }
        if (t.roster.size >= ProLeague.MAX_ROSTER && p.id !in t.roster) {
            return InterestEval(pct, label, factors, effectiveAsk, "Roster full", false, "Your roster is full (${ProLeague.MAX_ROSTER} players).")
        }
        val avail = availableRoom(lg, t, excludePid = p.id)
        val mleUsed = ps?.faUsedMle?.contains(t.id) == true
        val mleLimit = Payroll.mle(lg)
        return when {
            bird -> InterestEval(pct, label, factors, effectiveAsk, "Bird rights", true)
            salary <= minSal -> InterestEval(pct, label, factors, effectiveAsk, "Veteran minimum", true)
            avail >= salary -> InterestEval(pct, label, factors, effectiveAsk, "Cap space", true)
            !mleUsed && avail < mleLimit && salary <= mleLimit -> InterestEval(pct, label, factors, effectiveAsk, "Mid-Level Exception", true)
            else -> {
                val maxCanOffer = maxOf(minSal, avail, if (!mleUsed) mleLimit else 0)
                InterestEval(
                    pct, label, factors, effectiveAsk, "Over cap", false,
                    "Over the cap: the most you can offer without Bird rights is ${Road.dollars(maxCanOffer)} (${if (!mleUsed && maxCanOffer == mleLimit) "the mid-level exception" else "cap room"}).",
                )
            }
        }
    }

    // ---------- Pre-FA Contract Extensions, Options & Qualifying Offers (ProStep.DRAFT) ----------

    /**
     * Called when entering `ProStep.DRAFT` in Coach mode: assigns realistic expiring clauses (`RFA`, `TO`, `PO`)
     * to expiring contracts and resolves AI teams' pre-FA team options and qualifying offers.
     */
    fun prepareOffseasonContracts(lg: League) {
        val ps = lg.proState ?: return
        ps.userMoves.clear()
        ps.faCases.clear()
        ps.pendingRfaMatches.clear()
        ps.faRound = 0
        for (t in lg.teams) for (p in lg.rosterOf(t)) {
            if (p.proYears <= 0 || p.contractYears != 1) continue
            if (p.proYears in 3..4 && p.clause.isEmpty()) p.clause = "RFA"
            else if (p.clause.isEmpty() && Rng.chance(0.18)) {
                p.clause = when {
                    p.ovr >= 91 && p.age >= 26 -> "PO"
                    p.age <= 25 -> "TO"
                    else -> ""
                }
            }
            // AI teams decide their Team Options and Qualifying Offers before the market opens.
            if (!NbaCoach.active(lg) || t.id != lg.userTeamId) {
                if (p.clause == "TO") {
                    val worthIt = Payroll.salaryOf(lg, p) <= ProCareer.marketValue(lg, p) * 1.15 && p.ovr >= 82
                    if (worthIt) { p.contractYears = 2; p.clause = "" } else p.clause = ""
                } else if (p.clause == "RFA") {
                    p.clause = if (p.ovr >= 82) "QO" else ""
                }
            }
        }
    }

    /** Exercises or declines a Team Option (`TO`) on one of the user's expiring players during `ProStep.DRAFT`. */
    fun decideTeamOption(lg: League, p: Player, exercise: Boolean): String {
        val ps = lg.proState ?: return ""
        if (p.teamId != lg.userTeamId || p.clause != "TO") return "No team option on ${p.name}."
        val sal = Payroll.salaryOf(lg, p)
        return if (exercise) {
            p.contractYears = 2
            p.clause = ""
            ps.userKeep.remove(p.id)
            ps.userMoves += "Picked up ${p.name}'s team option (${Road.dollars(sal)})"
            "Picked up ${p.name}'s ${Road.dollars(sal)} team option for next season."
        } else {
            p.contractYears = 1
            p.clause = ""
            ps.userKeep.remove(p.id)
            ps.userMoves += "Declined ${p.name}'s team option: he becomes a free agent"
            "Declined ${p.name}'s team option. He will become an unrestricted free agent."
        }
    }

    /** Tenders or withdraws a Qualifying Offer (`QO`) for a Restricted Free Agent (`RFA`) on the user's roster. */
    fun tenderQualifyingOffer(lg: League, p: Player, tender: Boolean): String {
        val ps = lg.proState ?: return ""
        if (p.teamId != lg.userTeamId || p.clause !in listOf("RFA", "QO")) return "${p.name} can't get a qualifying offer."
        val qo = ((Payroll.salaryOf(lg, p) * 1.25).roundToInt() / 10 * 10)
            .coerceIn(ProCareer.minSalary(lg, p.proYears), ProCareer.maxSalary(lg, p.proYears))
        return if (tender) {
            p.clause = "QO"
            ps.userKeep += p.id
            ps.userMoves.removeAll { it.contains(p.name) && it.contains("ualifying") }
            ps.userMoves += "Qualifying offer to ${p.name} (${Road.dollars(qo)}): you can match any offer he signs"
            "Made ${p.name} a ${Road.dollars(qo)} qualifying offer. You can match any offer sheet he signs."
        } else {
            p.clause = "RFA"
            ps.userKeep.remove(p.id)
            ps.userMoves.removeAll { it.contains(p.name) && it.contains("ualifying") }
            "Withdrew the qualifying offer for ${p.name}. He'll be an unrestricted free agent."
        }
    }

    /**
     * Negotiates an immediate contract extension with one of the user's expiring players during `ProStep.DRAFT`.
     * Returns `(accepted, message)`.
     */
    fun negotiateExtension(
        lg: League,
        p: Player,
        salary: Int,
        years: Int,
        clause: String = "",
        structure: String = "FLAT",
    ): Pair<Boolean, String> {
        val ps = lg.proState ?: return false to "No pro state."
        if (p.teamId != lg.userTeamId) return false to "${p.name} is not on your roster."
        val yrs = years.coerceIn(1, 5)
        val eval = evaluateOffer(lg, lg.user, p, salary, yrs, clause, structure, fromTeamId = lg.userTeamId, isExtension = true)
        if (!eval.legal) return false to eval.illegalReason
        if (eval.pct < 52) {
            return false to "${p.name} declined your offer (${eval.pct}% interest — wants closer to ${Road.dollars(eval.effectiveAsk)}/yr for ${desiredYears(p)} yrs)."
        }
        p.salary = salary
        // +1 because Market.open decrements contractYears by 1 when transitioning from DRAFT to FREE_AGENCY.
        p.contractYears = yrs + 1
        p.clause = clause
        p.structure = structure
        ps.userKeep.remove(p.id)
        val clTag = if (clause.isNotEmpty()) " · ${Payroll.clauseShort(clause)}" else ""
        val stTag = if (structure != "FLAT" && structure.isNotEmpty()) " · ${Payroll.structureLabel(structure)}" else ""
        val line = "Extended ${p.name}: ${yrs}y, ${Road.dollars(salary)}/yr$clTag$stTag"
        ps.userMoves += line
        lg.news += News(lg.season, 0, "The ${lg.user.name} sign ${p.name} to a $yrs-year, ${Road.dollars(salary * yrs)} contract extension", true)
        return true to "${p.name} agreed to a $yrs-year, ${Road.dollars(salary)}/yr extension ($clTag${if (clTag.isEmpty()) "standard" else ""})!"
    }

    // ---------- Interactive Multi-Round Free Agency (Coach Career) ----------

    /**
     * Opens the 6-round interactive Free Agency market for Coach Career (`ProStep.FREE_AGENCY`):
     * - Steps retained dead cap by 1 year.
     * - Resolves expiring Player Options (`PO`) and takes 1 year off all contracts.
     * - Moves expiring players (`contractYears <= 0`) into `ps.faCases`.
     * - Executes pre-market AI luxury-tax buyouts.
     * - Converts any `ps.userKeep` / `ps.userOffers` into initial Round 1 user bids and places Round 1 AI bids.
     */
    fun open(lg: League) {
        val ps = lg.proState ?: return
        ps.faMoves.clear()
        ps.faCases.clear()
        ps.faUsedMle.clear()
        ps.pendingRfaMatches.clear()
        Payroll.stepOffseasonRetained(lg)

        val keep = setOf(lg.heroId, lg.road?.rivalId ?: -1)
        val pool = mutableListOf<Triple<Player, Int, Boolean>>()
        for (t in lg.teams) for (p in lg.rosterOf(t).toList()) {
            if (p.id in keep || p.proYears == 0) continue
            // Player option on final year: player opts in if current salary beats market value, else opts out.
            if (p.contractYears == 1 && p.clause == "PO") {
                val curSal = Payroll.salaryOf(lg, p)
                val mkt = ProCareer.marketValue(lg, p)
                if (curSal >= mkt * 1.05) {
                    p.contractYears = 2
                    p.clause = ""
                    if (t.id == lg.userTeamId) ps.userMoves += "${p.name} picks up his ${Road.dollars(curSal)} Player Option"
                } else {
                    p.clause = ""
                    if (t.id == lg.userTeamId) ps.userMoves += "${p.name} declines his Player Option and enters free agency"
                }
            }
            Extensions.applyNext(p)
            if (p.contractYears > 0) p.contractYears--
            if (p.contractYears <= 0) {
                val isRfa = p.clause == "QO" || (t.id != lg.userTeamId && p.clause == "RFA" && p.ovr >= 82)
                t.roster.remove(p.id)
                p.teamId = -1
                p.clause = if (isRfa) "RFA" else ""
                pool += Triple(p, t.id, isRfa)
            }
        }

        // Over the tax before the frenzy: AI teams buy out their worst overpaid contract to open room.
        for (t in lg.teams) {
            if (NbaCoach.active(lg) && t.id == lg.userTeamId) continue
            while (Payroll.total(lg, t) > Payroll.tax(lg)) {
                val worst = lg.rosterOf(t).filter { it.id !in keep }
                    .maxByOrNull { Payroll.salaryOf(lg, it).toDouble() / ProCareer.marketValue(lg, it).coerceAtLeast(1) } ?: break
                if (Payroll.salaryOf(lg, worst) <= ProCareer.marketValue(lg, worst) * 1.5) break
                t.roster.remove(worst.id)
                worst.teamId = -1
                worst.clause = ""
                pool += Triple(worst, t.id, false)
                if (Trades.value(worst) >= 2.5) lg.news += News(lg.season, 0, "The ${t.name} waive ${worst.name} (${Road.dollars(Payroll.salaryOf(lg, worst))}) to clear room", false)
            }
        }

        val inPool = pool.map { it.first.id }.toMutableSet()
        for (p in lg.players) {
            if (p.teamId < 0 && p.id !in keep && p.id !in inPool) {
                pool += Triple(p, -1, false)
                inPool += p.id
            }
        }

        val me = lg.userTeamId
        val sortedPool = pool.sortedByDescending { Trades.value(it.first) }
        for ((p, from, isRfa) in sortedPool) {
            val askSal = ProCareer.marketValue(lg, p).coerceAtLeast(ProCareer.minSalary(lg, p.proYears))
            val askYrs = desiredYears(p)
            val askCl = desiredClause(p)
            val fc = FaCase(p.id, from, askSal, askYrs, askCl, isRfa)
            // Seed initial user bids from userKeep / userOffers if set during DRAFT step.
            if (from == me && p.id in ps.userKeep) {
                val pay = minOf((askSal / 10) * 10, ProCareer.maxSalary(lg, p.proYears))
                fc.bids += ContractBid(me, pay, askYrs, askCl, "FLAT", bird = true, mle = false, rfaSheet = false, roundPlaced = 1)
            } else if (p.id in ps.userOffers) {
                val pay = ps.userOffers.getValue(p.id)
                val bird = from == me
                val mle = !bird && availableRoom(lg, lg.user, p.id) < pay && pay <= Payroll.mle(lg)
                fc.bids += ContractBid(me, pay, askYrs, askCl, "FLAT", bird = bird, mle = mle, rfaSheet = isRfa && !bird, roundPlaced = 1)
            }
            ps.faCases += fc
        }
        ps.faRound = 1
        placeAiBids(lg, 1)
    }

    /** Submits or updates the user's contract offer to free agent [pid] in the current round. */
    fun submitUserBid(
        lg: League,
        pid: Int,
        salary: Int,
        years: Int,
        clause: String = "",
        structure: String = "FLAT",
    ): Pair<Boolean, String> {
        val ps = lg.proState ?: return false to "No pro state."
        val fc = ps.faCases.firstOrNull { it.pid == pid } ?: return false to "Player is not in free agency."
        if (fc.signedTeamId >= 0) return false to "Player has already signed."
        val p = lg.playerOrNull(pid) ?: return false to "Player not found."
        val yrs = years.coerceIn(1, if (fc.fromTeamId == lg.userTeamId) 5 else 4)
        val eval = evaluateOffer(lg, lg.user, p, salary, yrs, clause, structure, fromTeamId = fc.fromTeamId)
        if (!eval.legal) return false to eval.illegalReason
        if (eval.pct < 25) return false to "${p.name} won't consider an offer with ${eval.pct}% interest (wants closer to ${Road.dollars(eval.effectiveAsk)}/yr)."

        val bird = fc.fromTeamId == lg.userTeamId
        val mle = eval.capHow == "Mid-Level Exception"
        // Only one active MLE offer at a time unless already using MLE on this player.
        if (mle) {
            for (other in ps.faCases) {
                if (other.pid != pid && other.signedTeamId < 0) {
                    other.bids.removeAll { it.teamId == lg.userTeamId && it.mle }
                }
            }
        }
        fc.bids.removeAll { it.teamId == lg.userTeamId }
        fc.bids += ContractBid(
            teamId = lg.userTeamId,
            salary = salary,
            years = yrs,
            clause = clause,
            structure = structure,
            bird = bird,
            mle = mle,
            rfaSheet = fc.restricted && !bird,
            roundPlaced = ps.faRound.coerceAtLeast(1),
        )
        ps.userOffers[pid] = salary
        if (bird) ps.userKeep += pid
        val clTag = if (clause.isNotEmpty()) " (${Payroll.clauseShort(clause)})" else ""
        return true to "Offered ${p.name} ${yrs}y / ${Road.dollars(salary)}/yr$clTag (${eval.capHow} · ${eval.pct}% interest)."
    }

    /** Withdraws the user's standing offer to free agent [pid]. */
    fun withdrawUserBid(lg: League, pid: Int) {
        val ps = lg.proState ?: return
        ps.faCases.firstOrNull { it.pid == pid }?.bids?.removeAll { it.teamId == lg.userTeamId }
        ps.userOffers.remove(pid)
        ps.userKeep.remove(pid)
    }

    /** Matches or declines an AI team's RFA offer sheet for one of the user's Restricted Free Agents. */
    fun resolveRfaMatch(lg: League, pid: Int, match: Boolean): String {
        val ps = lg.proState ?: return ""
        val sheet = ps.pendingRfaMatches.remove(pid) ?: return "No pending offer sheet."
        val fc = ps.faCases.firstOrNull { it.pid == pid } ?: return ""
        val p = lg.playerOrNull(pid) ?: return ""
        val other = lg.team(sheet.teamId)
        return if (match) {
            fc.signedTeamId = lg.userTeamId
            fc.statusNote = "Matched ${other.abbr} offer sheet (${sheet.years}y/${Road.dollars(sheet.salary)})"
            signPlayer(lg, p, lg.user, sheet.salary, sheet.years, sheet.clause, sheet.structure)
            val line = "Matched ${other.name}'s ${sheet.years}y, ${Road.dollars(sheet.salary)}/yr offer sheet for ${p.name}"
            ps.userMoves += line
            lg.news += News(lg.season, 0, "The ${lg.user.name} match the ${other.name}'s offer sheet to keep ${p.name}", true)
            line
        } else {
            fc.signedTeamId = other.id
            fc.statusNote = "Signed with ${other.abbr} (${sheet.years}y/${Road.dollars(sheet.salary)})"
            signPlayer(lg, p, other, sheet.salary, sheet.years, sheet.clause, sheet.structure)
            if (sheet.mle) ps.faUsedMle += other.id
            val line = "Declined to match — ${p.name} signs with the ${other.name} (${Road.dollars(sheet.salary)}/yr)"
            ps.userMoves += line
            if (Trades.value(p) >= 2.5) {
                val faLine = "${p.name} (${Ratings.shownPro(p.ovr)}) signs with the ${other.name}: ${p.contractYears} yrs, ${Road.dollars(sheet.salary)}/yr"
                ps.faMoves += faLine
                lg.news += News(lg.season, 0, "Free agency: $faLine (unmatched RFA)", true)
            }
            line
        }
    }

    private fun autoResolveUserRfa(lg: League) {
        val ps = lg.proState ?: return
        for (pid in ps.pendingRfaMatches.keys.toList()) {
            val match = pid in ps.userKeep && lg.user.roster.size < ProLeague.MAX_ROSTER
            resolveRfaMatch(lg, pid, match)
        }
    }

    /** Places or escalates AI team bids for [round] (`1..ROUNDS`) across all unsigned free agents. */
    private fun placeAiBids(lg: League, round: Int) {
        val ps = lg.proState ?: return
        val coach = NbaCoach.active(lg)
        val me = lg.userTeamId
        val needGate = if (round <= 3) 1.04 else 0.85

        for (fc in ps.faCases) {
            if (fc.signedTeamId >= 0 || fc.pid in ps.pendingRfaMatches) continue
            val p = lg.playerOrNull(fc.pid) ?: continue
            val suitors = mutableListOf<Team>()
            for (t in lg.teams) {
                if (coach && t.id == me) continue
                if (t.roster.size >= ProLeague.MAX_ROSTER) continue
                val bird = t.id == fc.fromTeamId
                val room = availableRoom(lg, t, fc.pid)
                val mleAvail = t.id !in ps.faUsedMle
                if (room < fc.askSalary * 0.7 && !bird && !mleAvail) continue
                if (bird && !Rng.chance(keepChance(lg, t, p))) continue
                if (Needs.needFactor(lg, t, p) < needGate && !bird) continue
                suitors += t
            }
            for (t in suitors.sortedByDescending { Needs.needFactor(lg, it, p) }.take(4)) {
                val bird = t.id == fc.fromTeamId
                val room = availableRoom(lg, t, fc.pid)
                val (limit, mle) = when {
                    bird -> Int.MAX_VALUE to false
                    room >= fc.askSalary * 0.7 -> room to false
                    t.id !in ps.faUsedMle -> Payroll.mle(lg) to true
                    else -> continue
                }
                val aggression = 0.86 + (round - 1) * 0.055 + Rng.d() * 0.11
                val want = (fc.askSalary * aggression).roundToInt() / 10 * 10
                val pay = minOf(want, limit, ProCareer.maxSalary(lg, p.proYears))
                if (pay < ProCareer.minSalary(lg, p.proYears)) continue
                val yrs = desiredYears(p).coerceAtMost(if (bird) 5 else 4)
                val cl = if (round >= 3 && p.ovr >= 91 && Rng.chance(0.35)) "PO" else ""
                val existing = fc.bids.firstOrNull { it.teamId == t.id }
                if (existing == null) {
                    fc.bids += ContractBid(t.id, pay, yrs, cl, "FLAT", bird, mle, fc.restricted && !bird, round)
                } else if (pay > existing.salary) {
                    existing.salary = pay
                    existing.years = yrs
                    if (cl.isNotEmpty()) existing.clause = cl
                }
            }
        }
    }

    /** Score of a contract bid for free agent [fc], combining salary vs ask and BLM 27 Signing Interest. */
    fun scoreOfBid(lg: League, fc: FaCase, b: ContractBid): Double {
        val p = lg.playerOrNull(fc.pid) ?: return 0.0
        val t = lg.teams.getOrNull(b.teamId) ?: return 0.0
        val eval = evaluateOffer(lg, t, p, b.salary, b.years, b.clause, b.structure, fc.fromTeamId)
        return b.salary.toDouble() / fc.askSalary.coerceAtLeast(1) + (eval.pct / 100.0) * 0.85
    }

    /** Leading bid on [fc], if any. */
    fun leadingBid(lg: League, fc: FaCase): ContractBid? =
        fc.bids.filter { lg.team(it.teamId).roster.size < ProLeague.MAX_ROSTER }.maxByOrNull { scoreOfBid(lg, fc, it) }

    /**
     * Advances the Free Agency Frenzy by one round (`ps.faRound`):
     * - Resolves any pending user RFA offer sheets from the previous round.
     * - Evaluates all bids in the current round; players with a clear leader or strong interest sign.
     * - Advances `ps.faRound++` and either generates the next round's AI bids or finalizes Free Agency after Round 6.
     */
    fun runRound(lg: League): List<String> {
        val ps = lg.proState ?: return emptyList()
        if (ps.faRound <= 0) open(lg)
        if (ps.faRound > ROUNDS) return emptyList()

        // Any RFA offer sheets from the previous round that the user didn't manually resolve get auto-resolved now.
        autoResolveUserRfa(lg)

        val round = ps.faRound
        val last = round >= ROUNDS
        val me = lg.userTeamId
        val roundLog = mutableListOf<String>()

        for (fc in ps.faCases) {
            if (fc.signedTeamId >= 0 || fc.pid in ps.pendingRfaMatches) continue
            val p = lg.playerOrNull(fc.pid) ?: continue
            // Remove bids from teams whose rosters filled up.
            fc.bids.removeAll { lg.team(it.teamId).roster.size >= ProLeague.MAX_ROSTER }
            val best = fc.bids.maxByOrNull { scoreOfBid(lg, fc, it) } ?: continue
            val second = fc.bids.filter { it !== best }.maxByOrNull { scoreOfBid(lg, fc, it) }
            val bestScore = scoreOfBid(lg, fc, best)
            val margin = bestScore - (second?.let { scoreOfBid(lg, fc, it) } ?: 0.0)
            val bestEval = evaluateOffer(lg, lg.team(best.teamId), p, best.salary, best.years, best.clause, best.structure, fc.fromTeamId)

            val readyToSign = last || (bestEval.pct >= 55 && (margin >= 0.11 || (round >= 2 && bestEval.pct >= 68)))
            if (!readyToSign) continue

            // Check Restricted Free Agency (RFA) matching!
            if (fc.restricted && best.teamId != fc.fromTeamId) {
                val origTeam = lg.team(fc.fromTeamId)
                if (fc.fromTeamId == me && origTeam.roster.size < ProLeague.MAX_ROSTER && !last) {
                    // User's RFA signed an AI offer sheet — let the user decide whether to match!
                    ps.pendingRfaMatches[p.id] = best
                    fc.statusNote = "Offer sheet from ${lg.team(best.teamId).abbr}: ${best.years}y/${Road.dollars(best.salary)}"
                    val msg = "Offer sheet: ${p.name} signed for ${best.years} ${if (best.years == 1) "year" else "years"}, ${Road.dollars(best.salary)} a year with the ${lg.team(best.teamId).name}. Match it or let him walk."
                    roundLog += msg
                    continue
                } else if (fc.fromTeamId != me && origTeam.roster.size < ProLeague.MAX_ROSTER) {
                    // AI team decides whether to match an offer sheet on its RFA.
                    val willMatch = best.salary <= fc.askSalary * 1.25 && Rng.chance(keepChance(lg, origTeam, p))
                    if (willMatch) {
                        fc.signedTeamId = origTeam.id
                        fc.statusNote = "${origTeam.abbr} matched (${best.years}y/${Road.dollars(best.salary)})"
                        signPlayer(lg, p, origTeam, best.salary, best.years, best.clause, best.structure)
                        if (best.teamId == me) {
                            val line = "The ${origTeam.name} matched your ${Road.dollars(best.salary)}-a-year offer sheet for ${p.name}"
                            ps.userMoves += line
                            roundLog += line
                        }
                        continue
                    }
                }
            }

            val winTeam = lg.team(best.teamId)
            fc.signedTeamId = winTeam.id
            val clTag = if (best.clause.isNotEmpty()) " (${Payroll.clauseShort(best.clause)})" else ""
            fc.statusNote = "Signed with ${winTeam.abbr}: ${best.years}y/${Road.dollars(best.salary)}$clTag"
            signPlayer(lg, p, winTeam, best.salary, best.years, best.clause, best.structure)
            if (best.mle) ps.faUsedMle += winTeam.id

            val userHadBid = fc.bids.any { it.teamId == me }
            if (fc.fromTeamId == me || userHadBid) {
                val moveLine = when {
                    winTeam.id == me && fc.fromTeamId == me -> "${p.name} re-signs: ${best.years}y, ${Road.dollars(best.salary)}/yr$clTag"
                    winTeam.id == me -> "${p.name} signs with you: ${best.years}y, ${Road.dollars(best.salary)}/yr$clTag"
                    fc.fromTeamId == me && !userHadBid -> "${p.name} leaves for the ${winTeam.name} (${Road.dollars(best.salary)}/yr)"
                    fc.fromTeamId == me -> "${p.name} leaves for the ${winTeam.name} (${Road.dollars(best.salary)}/yr)"
                    else -> "${p.name} signs with the ${winTeam.name} (${Road.dollars(best.salary)}/yr) over your offer"
                }
                ps.userMoves += moveLine
                roundLog += moveLine
            }

            if (winTeam.id != fc.fromTeamId && Trades.value(p) >= 2.5) {
                val line = "${p.name} (${Ratings.shownPro(p.ovr)}) signs with the ${winTeam.name}: ${p.contractYears} ${if (p.contractYears == 1) "year" else "years"}, ${Road.dollars(best.salary)} a season"
                ps.faMoves += line
                if (roundLog.none { it.contains(p.name) }) roundLog += line
                val leaving = lg.teams.getOrNull(fc.fromTeamId)?.let { ", leaving the ${it.name}" } ?: ""
                lg.news += News(lg.season, 0, "Free agency: $line$leaving", winTeam.id == me || fc.fromTeamId == me)
                if (Trades.value(p) >= 8) Media.post(lg, "NBA Insider", "nbainsider", "insider", "${p.name} has agreed to a ${p.contractYears}-year, ${Road.dollars(best.salary * p.contractYears)} deal with the ${winTeam.name}, per sources.", Media.likes(lg, 0.04), verified = true, league = !Feed.mine(lg, winTeam.id, fc.fromTeamId))
            } else if (winTeam.id == fc.fromTeamId && Trades.value(p) >= 8) {
                val line = "${p.name} re-signs with the ${winTeam.name}: ${p.contractYears} ${if (p.contractYears == 1) "year" else "years"}, ${Road.dollars(best.salary)} a season"
                ps.faMoves += line
                if (roundLog.none { it.contains(p.name) }) roundLog += line
            }
        }

        ps.faRound = round + 1
        if (ps.faRound <= ROUNDS) {
            placeAiBids(lg, ps.faRound)
        } else {
            finalize(lg)
        }
        return roundLog
    }

    /** Completes all remaining Free Agency rounds and finalizes rosters for Training Camp. */
    fun finish(lg: League) {
        val ps = lg.proState ?: return
        if (ps.faRound == 0) open(lg)
        while (ps.faRound in 1..ROUNDS) {
            autoResolveUserRfa(lg)
            runRound(lg)
        }
        if (ps.faCases.any { it.signedTeamId < 0 && lg.playerOrNull(it.pid)?.teamId == -1 }) {
            finalize(lg)
        }
    }

    /** Signs remaining unsigned free agents to minimum deals (or retires 32+ vets) and fills all rosters to [ProLeague.ROSTER]. */
    private fun finalize(lg: League) {
        val ps = lg.proState ?: return
        autoResolveUserRfa(lg)
        for (fc in ps.faCases) {
            if (fc.signedTeamId >= 0) continue
            val p = lg.playerOrNull(fc.pid) ?: continue
            if (p.teamId >= 0) { fc.signedTeamId = p.teamId; continue }
            val t = lg.teams.filter { it.roster.size < ProLeague.ROSTER }.minByOrNull { it.roster.size }
            if (p.age >= 32 || t == null) {
                fc.signedTeamId = -2
                fc.statusNote = "Retired"
                AllTime.freeze(lg, p)
                lg.removePlayer(p)
                continue
            }
            val minSal = ProCareer.minSalary(lg, p.proYears)
            fc.signedTeamId = t.id
            fc.statusNote = "Min deal with ${t.abbr} (${Road.dollars(minSal)})"
            signPlayer(lg, p, t, minSal, 1, "", "FLAT")
        }
        for (t in lg.teams) {
            while (t.roster.size < ProLeague.ROSTER) ProLeague.makePro(lg, Rng.pick(Pos.entries), Rng.range(80, 86), Rng.range(22, 30), t)
            Gameplan.autoAllocate(lg, t)
        }
        Payroll.sign(lg)
        ps.userKeep.clear()
        ps.userOffers.clear()
        ps.pendingRfaMatches.clear()
        lg.reindex()
    }

    // ---------- One-shot Free Agency (Player Career & legacy calls) ----------

    fun run(lg: League) {
        if (NbaCoach.active(lg)) {
            open(lg)
            finish(lg)
            return
        }
        val ps = lg.proState ?: return
        ps.faMoves.clear()
        Payroll.stepOffseasonRetained(lg)
        val keep = setOf(lg.heroId, lg.road?.rivalId ?: -1)
        val pool = mutableListOf<Pair<Player, Int>>()
        for (t in lg.teams) for (p in lg.rosterOf(t).toList()) {
            if (p.id in keep || p.proYears == 0) continue
            Extensions.applyNext(p)
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

        val committed = HashMap<Int, Int>()
        fun roomLeft(t: Team) = Payroll.room(lg, t) - (committed[t.id] ?: 0)

        val cases = pool.map { (p, from) -> Case(p, from, ProCareer.marketValue(lg, p).coerceAtLeast(ProCareer.minSalary(lg, p.proYears))) }
            .sortedByDescending { Trades.value(it.p) }
        val signed = HashMap<Int, Team>()

        for (round in 0 until ROUNDS) {
            val last = round == ROUNDS - 1
            val needGate = if (round < 3) 1.05 else 0.85
            for (c in cases) {
                val p = c.p
                if (signed.containsKey(p.id)) continue
                val suitors = mutableListOf<Team>()
                for (t in lg.teams) {
                    if (t.roster.size >= ProLeague.MAX_ROSTER) continue
                    val bird = t.id == c.from
                    if (roomLeft(t) < c.ask * 0.7 && !bird && t.id !in usedMle) continue
                    if (bird && !Rng.chance(keepChance(lg, t, p))) continue
                    if (Needs.needFactor(lg, t, p) < needGate && !bird) continue
                    suitors += t
                }
                for (t in suitors.sortedByDescending { Needs.needFactor(lg, it, p) }.take(4)) {
                    val bird = t.id == c.from
                    val (limit, mle) = when {
                        bird -> Int.MAX_VALUE to false
                        roomLeft(t) >= c.ask * 0.7 -> roomLeft(t) to false
                        t.id !in usedMle -> Payroll.mle(lg) to true
                        else -> continue
                    }
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
                val best = c.bids.maxByOrNull { bidScore(lg, c, it) } ?: continue
                val second = c.bids.filter { it !== best }.maxByOrNull { bidScore(lg, c, it) }
                val margin = bidScore(lg, c, best) - (second?.let { bidScore(lg, c, it) } ?: 0.0)
                if (margin >= 0.12 || last) {
                    signed[p.id] = best.team
                    sign(lg, p, best.team, best.pay)
                    if (best.mle) usedMle += best.team.id
                    if (best.team.id != c.from && Trades.value(p) >= 2.5) {
                        val line = "${p.name} (${Ratings.shownPro(p.ovr)}) signs with the ${best.team.name}: ${p.contractYears} ${if (p.contractYears == 1) "year" else "years"}, ${Road.dollars(best.pay)} a season"
                        ps.faMoves += line
                        lg.news += News(lg.season, 0, "Free agency: $line, leaving the ${lg.team(c.from).name}", best.team.id == me || c.from == me)
                        if (Trades.value(p) >= 8) Media.post(lg, "NBA Insider", "nbainsider", "insider", "${p.name} has agreed to a ${p.contractYears}-year, ${Road.dollars(best.pay * p.contractYears)} deal with the ${best.team.name}, per sources.", Media.likes(lg, 0.04), verified = true, league = !Feed.mine(lg, best.team.id, c.from))
                    } else if (best.team.id == c.from && Trades.value(p) >= 8) ps.faMoves += "${p.name} re-signs with the ${best.team.name}: ${p.contractYears} ${if (p.contractYears == 1) "year" else "years"}, ${Road.dollars(best.pay)} a season"
                    for (b in c.bids) committed[b.team.id] = (committed[b.team.id] ?: 0) - b.pay
                }
            }
        }

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

    private fun signPlayer(lg: League, p: Player, t: Team, pay: Int, years: Int, clause: String, structure: String) {
        if (t.roster.any { lg.playerOrNull(it)?.number == p.number }) p.number = Recruiting.freeNumber(lg, t)
        if (p.id !in t.roster) t.roster += p.id
        p.teamId = t.id
        ProLeague.trim(lg, t, setOf(p.id))
        p.salary = pay
        p.contractYears = years.coerceIn(1, 5)
        p.clause = clause
        p.structure = structure.ifEmpty { "FLAT" }
    }

    private fun sign(lg: League, p: Player, t: Team, pay: Int) {
        val yrs = when { p.age <= 25 -> Rng.range(3, 4); p.age <= 29 -> Rng.range(2, 4); p.age <= 32 -> Rng.range(1, 3); else -> 1 }
        signPlayer(lg, p, t, pay, yrs, desiredClause(p), "FLAT")
    }
}
