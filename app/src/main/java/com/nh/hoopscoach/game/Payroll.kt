package com.nh.hoopscoach.game

import kotlin.math.roundToInt

/**
 * Team salary caps in the pros. Every player has a salary and years left (the user's player's
 * comes from his contract); a team's payroll sits under or over the cap (154.6M today, scaled to
 * the era), the luxury tax line (121.5% of the cap) and the first apron (126.8%). Teams with cap
 * room can offer up to it in free agency; teams over the cap only the mid-level exception (9.15% of
 * the cap); a player's own team can always go over the cap to keep him. Teams over the cap have to
 * match salaries in trades (taking back no more than 125% of what they send, plus $100K).
 */
object Payroll {
    const val MAX_RETAINED = 3

    fun cap(lg: League) = ProCareer.cap(lg)
    fun tax(lg: League) = (cap(lg) * 1.215).roundToInt()
    fun apron(lg: League) = (cap(lg) * 1.268).roundToInt()
    fun mle(lg: League) = (cap(lg) * 0.0915).roundToInt() / 10 * 10

    fun salaryOf(lg: League, p: Player): Int = if (p.id == lg.heroId) lg.road?.contract?.salary ?: p.salary else p.salary

    /** Dead cap from retained trade salaries and stretched buyouts. */
    fun retainedList(lg: League, t: Team): List<RetainedSalary> = lg.proState?.retained?.get(t.id).orEmpty()

    fun deadCap(lg: League, t: Team): Int = retainedList(lg, t).sumOf { it.amount }

    fun retainedSlotsUsed(lg: League, t: Team): Int = retainedList(lg, t).count { it.kind == "Retained" }

    fun canRetainMore(lg: League, t: Team): Boolean = retainedSlotsUsed(lg, t) < MAX_RETAINED

    fun addRetained(lg: League, t: Team, p: Player, amount: Int, years: Int = p.contractYears.coerceAtLeast(1), kind: String = "Retained") {
        if (amount <= 0) return
        val ps = lg.proState ?: return
        val list = ps.retained.getOrPut(t.id) { mutableListOf() }
        list += RetainedSalary(p.id, p.name, amount, years.coerceAtLeast(1), kind)
    }

    /** Ages down retained salary and buyout dead-cap entries by one year at the start of the offseason. */
    fun stepOffseasonRetained(lg: League) {
        val ps = lg.proState ?: return
        for ((_, list) in ps.retained) {
            list.forEach { it.yearsLeft-- }
            list.removeAll { it.yearsLeft <= 0 }
        }
    }

    fun total(lg: League, t: Team): Int = lg.rosterOf(t).sumOf { salaryOf(lg, it) } + deadCap(lg, t)

    fun room(lg: League, t: Team): Int = cap(lg) - total(lg, t)

    fun status(lg: League, t: Team): String {
        val total = total(lg, t)
        return when { total > apron(lg) -> "Over the apron"; total > tax(lg) -> "Paying the luxury tax"; total > cap(lg) -> "Over the cap"; else -> "Under the cap" }
    }

    fun clauseLabel(clause: String): String = when (clause) {
        "PO" -> "Player Option"
        "TO" -> "Team Option"
        "NTC" -> "No-Trade Clause"
        "RFA" -> "Restricted FA"
        "QO" -> "Qualifying Offer"
        else -> "None"
    }

    fun clauseShort(clause: String): String = when (clause) {
        "PO" -> "PO"
        "TO" -> "TO"
        "NTC" -> "NTC"
        "RFA" -> "RFA"
        "QO" -> "RFA·QO"
        else -> ""
    }

    fun structureLabel(structure: String): String = when (structure) {
        "FRONT" -> "Front-loaded"
        "BACK" -> "Back-loaded"
        else -> "Flat"
    }

    /** Preview of buying out [p]: returns (annualDeadCapHit, yearsStretched, immediateCapSavings). */
    fun buyoutPreview(lg: League, p: Player): Triple<Int, Int, Int> {
        val sal = salaryOf(lg, p)
        val yrs = p.contractYears.coerceAtLeast(1)
        val stretchYrs = (yrs * 2).coerceAtMost(6)
        // Stretching 60% of the contract over 2 * yearsLeft means 30% annual dead cap hit, freeing 70% now.
        val annualHit = ((sal * 0.30).roundToInt() / 10 * 10).coerceAtLeast(100)
        val saved = (sal - annualHit).coerceAtLeast(0)
        return Triple(annualHit, stretchYrs, saved)
    }

    /** Waives and stretches [p] from [t]'s roster to clear immediate cap space, leaving a smaller dead-cap hit. */
    fun buyout(lg: League, t: Team, p: Player): Boolean {
        if (p.id !in t.roster || p.id == lg.heroId) return false
        if (t.roster.size <= 10 && lg.phase != Phase.OFFSEASON) return false
        val (hit, yrs, saved) = buyoutPreview(lg, p)
        t.roster.remove(p.id)
        p.teamId = -1
        p.clause = ""
        addRetained(lg, t, p, hit, yrs, "Buyout")
        val ps = lg.proState
        if (ps != null && lg.phase == Phase.OFFSEASON && ps.step == ProStep.FREE_AGENCY && ps.faRound in 1..Market.ROUNDS) {
            val ask = ProCareer.marketValue(lg, p).coerceAtLeast(ProCareer.minSalary(lg, p.proYears))
            if (ps.faCases.none { it.pid == p.id }) {
                ps.faCases += FaCase(p.id, t.id, ask, Market.desiredYears(p), Market.desiredClause(p), restricted = false)
            }
        } else if (lg.phase != Phase.OFFSEASON) {
            // During the regular season, another team with a thin bench can claim him on a minimum deal, or he waits for summer.
            val claimer = lg.teams.filter { it.id != t.id && it.roster.size < ProLeague.MAX_ROSTER }.minByOrNull { it.roster.size }
            if (claimer != null && p.ovr >= 80) {
                claimer.roster += p.id
                p.teamId = claimer.id
                p.salary = ProCareer.minSalary(lg, p.proYears)
                p.contractYears = 1
                Gameplan.autoAllocate(lg, claimer)
            }
        }
        Gameplan.autoAllocate(lg, t)
        lg.news += News(lg.season, lg.day, "The ${t.name} buy out ${p.name}, freeing ${Road.dollars(saved)} in cap space (${Road.dollars(hit)}/yr dead cap for $yrs yrs)", t.id == lg.userTeamId)
        return true
    }

    /** Every pro without a deal gets one: his market value give or take, for one to four years. */
    fun sign(lg: League) {
        if (!lg.pro) return
        for (t in lg.teams) for (p in lg.rosterOf(t)) {
            if (p.id == lg.heroId || (p.salary > 0 && p.contractYears > 0)) continue
            p.salary = ((ProCareer.marketValue(lg, p) * (0.85 + Rng.d() * 0.3)).roundToInt() / 10 * 10).coerceAtLeast(ProCareer.minSalary(lg, p.proYears))
            p.contractYears = Rng.range(1, 4)
            if (p.structure.isEmpty()) p.structure = "FLAT"
            if (p.clause.isEmpty()) {
                p.clause = when {
                    p.proYears <= 3 && p.contractYears <= 2 -> if (p.contractYears == 1) "RFA" else "TO"
                    p.ovr >= 94 && p.age >= 28 && p.proYears >= 7 && Rng.chance(0.35) -> "NTC"
                    p.ovr >= 90 && p.contractYears >= 2 && Rng.chance(0.30) -> "PO"
                    p.contractYears >= 2 && Rng.chance(0.18) -> "TO"
                    else -> ""
                }
            }
        }
    }

    /** A rookie's deal by draft slot: four years in the first round (with a team option / RFA rights), two in the second. */
    fun rookie(lg: League, p: Player, pick: Int) {
        p.salary = ProCareer.rookieSalary(lg, pick)
        p.contractYears = if (pick <= lg.teams.size) 4 else 2
        p.clause = if (pick <= lg.teams.size) "TO" else ""
        p.structure = "BACK"
    }

    /** A new season: anyone without a deal gets one (the years come off in free agency; see Market). */
    fun newSeason(lg: League) {
        if (!lg.pro) return
        sign(lg)
    }

    /** Whether [t] can take [incoming] salary for [outgoing]: under the cap after, or matched within 125% plus $100K. */
    fun tradeWorks(lg: League, t: Team, incoming: Int, outgoing: Int): Boolean =
        total(lg, t) - outgoing + incoming <= cap(lg) || incoming <= outgoing * 1.25 + 100

    /** The most [t] can offer a free agent who isn't his: its cap room, or the mid-level exception if it's over. */
    fun canOffer(lg: League, t: Team): Pair<Int, String> {
        val room = room(lg, t)
        return if (room >= mle(lg)) room to "Cap space" else mle(lg) to "Mid-level exception"
    }
}
