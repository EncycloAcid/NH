package com.nh.hoopscoach.game

import kotlin.math.roundToInt

/**
 * The head coach's contract, college or NBA: years and a salary, signed when he's hired. Each season
 * pays him and takes a year off. Fired with years left, he's owed the rest (the buyout). When it runs
 * out, the owner or AD either offers an extension (take it, ask for more, or walk and take calls) or
 * doesn't bring him back. A big season can bring an early extension. Salaries are in thousands.
 */
object CoachContracts {
    /** What [t] pays a head coach with [c]'s résumé, a year, in thousands. */
    fun salaryFor(lg: League, t: Team, c: Coach): Int {
        val rep = (c.rating / 60.0).coerceIn(0.7, 1.6) + c.titles * 0.08
        val base = if (lg.pro) 3_200.0 + (t.prestige - 50) * 90.0 else 700.0 + t.prestige * t.prestige * 0.55
        val v = (base.coerceAtLeast(if (lg.pro) 2_500.0 else 450.0) * rep).coerceAtMost(if (lg.pro) 16_000.0 else 11_000.0)
        return (v / 50).roundToInt() * 50
    }

    /** How long a new deal runs: college coaches get five years, NBA coaches four; a retread gets less. */
    fun yearsFor(lg: League, fresh: Boolean = true): Int = if (lg.pro) (if (fresh) 4 else 3) else (if (fresh) 5 else 4)

    /** He signs with [t]: a new contract (and the old one, if any, is done). */
    fun sign(lg: League, t: Team = lg.user, years: Int = yearsFor(lg), salary: Int = salaryFor(lg, t, lg.userCoach)) {
        val c = lg.userCoach
        c.contractYears = years
        c.contractSalary = salary
        c.contractSigned = lg.season
        c.offerYears = 0
        c.offerSalary = 0
    }

    /** An older save: give the user's coach a contract that isn't about to run out. */
    fun ensure(lg: League) {
        if (lg.playerMode || lg.road != null) return
        val c = runCatching { lg.userCoach }.getOrNull() ?: return
        // A brand-new career gets a full first contract; an older save, one that isn't about to run out.
        if (c.contractSalary <= 0) sign(lg, lg.user, years = if (c.history.isEmpty()) yearsFor(lg) else 3)
    }

    fun money(k: Int): String = Road.dollars(k)

    /** "$4.2M a year · 3 years left", or the contract year. */
    fun line(lg: League): String {
        val c = lg.userCoach
        if (c.contractSalary <= 0) return ""
        val left = c.contractYears
        return "${money(c.contractSalary)} a year · " + when { left <= 0 -> "expired"; left == 1 -> "final year"; else -> "$left years left" }
    }

    /**
     * Season over, after the boss's verdict: he's paid, a year comes off, and the deal is settled if
     * it's done. Returns true if he's been let go (fired or not brought back), so the caller can open
     * the job market. [fired] says the boss already decided to fire him.
     */
    fun seasonEnd(lg: League, fired: Boolean): Boolean {
        ensure(lg)
        val c = lg.userCoach
        val t = lg.user
        c.earnings += c.contractSalary
        c.contractYears = (c.contractYears - 1).coerceAtLeast(0)
        val boss = if (lg.pro) "owner" else "AD"
        if (fired) {
            // Fired with years left: they pay the rest.
            val owed = c.contractYears * c.contractSalary
            if (owed > 0) {
                c.earnings += owed
                lg.news += News(lg.season, lg.day, "${c.name} is owed ${money(owed)} on the rest of his deal", true, -1, "move",
                    "${if (lg.pro) "The ${t.name}" else t.school} will pay out the final ${c.contractYears} ${if (c.contractYears == 1) "year" else "years"} of his contract.", "Hoops Wire", t.id, -1)
            }
            c.contractYears = 0; c.contractSalary = 0
            return true
        }
        if (c.contractYears == 0) {
            if (c.security >= 40) {
                offer(lg, early = false)
                lg.news += News(lg.season, lg.day, "${c.name}'s contract is up. The $boss wants him back.", true)
                Alerts.add(lg, "Your contract is up", "The $boss offers ${c.offerYears} more years at ${money(c.offerSalary)} a year. Accept it, ask for more, or walk away and take calls.", "signing")
                return false
            }
            // Not brought back.
            lg.news += News(lg.season, lg.day, "${if (lg.pro) "The ${t.name}" else t.school} won't bring back ${c.name}", true, -1, "move",
                "His contract is up, and the $boss is going another way after a ${t.w}-${t.l} season.", "Hoops Wire", t.id, -1)
            c.contractSalary = 0
            return true
        }
        // A big season with a deal running short: an early extension.
        if (c.contractYears <= 2 && c.security >= 80) {
            offer(lg, early = true)
            Alerts.add(lg, "Extension offer", "After that season, the $boss wants to lock you up: ${c.offerYears} years at ${money(c.offerSalary)} a year, starting now.", "signing")
        }
        return false
    }

    /** The boss's offer: longer and richer the more secure he is. */
    private fun offer(lg: League, early: Boolean) {
        val c = lg.userCoach
        val raise = when { c.security >= 85 -> 1.3; c.security >= 70 -> 1.18; c.security >= 55 -> 1.08; else -> 1.0 }
        val market = salaryFor(lg, lg.user, c)
        c.offerSalary = ((maxOf(c.contractSalary, market) * raise) / 50).roundToInt() * 50
        c.offerYears = when { c.security >= 80 -> if (lg.pro) 4 else 5; c.security >= 60 -> 3; else -> 2 }
        c.offerEarly = early
    }

    fun hasOffer(lg: League) = !lg.playerMode && runCatching { lg.userCoach.offerSalary > 0 }.getOrDefault(false)

    /** He signs the extension on the table. */
    fun accept(lg: League) {
        val c = lg.userCoach
        if (c.offerSalary <= 0) return
        val t = lg.user
        lg.news += News(lg.season, lg.day, "${c.name} signs a ${c.offerYears}-year extension with ${if (lg.pro) "the ${t.name}" else t.school}", true, -1, "move",
            "${money(c.offerSalary)} a year through ${lg.season + c.offerYears}.", "Hoops Wire", t.id, -1)
        sign(lg, t, c.offerYears, c.offerSalary)
        c.security = (c.security + 4).coerceAtMost(100)
    }

    /** He asks for more: a secure coach usually gets something, a shaky one can lose the offer. Returns what happened. */
    fun counter(lg: League): String {
        val c = lg.userCoach
        if (c.offerSalary <= 0 || c.countered) return "You've already countered."
        c.countered = true
        val boss = if (lg.pro) "owner" else "AD"
        val p = ((c.security - 35) / 60.0).coerceIn(0.1, 0.9)
        return if (Rng.chance(p)) {
            c.offerSalary = ((c.offerSalary * 1.12) / 50).roundToInt() * 50
            "The $boss comes up to ${money(c.offerSalary)} a year."
        } else if (!c.offerEarly && c.security < 55 && Rng.chance(0.4)) {
            c.offerSalary = 0; c.offerYears = 0
            lg.news += News(lg.season, lg.day, "Talks break down between ${c.name} and the ${if (lg.pro) lg.user.name else lg.user.school}", true)
            "The $boss pulls the offer. You'll have to find a new job."
        } else "The $boss won't move. The offer stands."
    }

    /** He declines an early extension (his deal runs on) or walks away from an expired one (he takes calls). Returns true if he's now looking for work. */
    fun decline(lg: League): Boolean {
        val c = lg.userCoach
        val early = c.offerEarly
        c.offerSalary = 0; c.offerYears = 0; c.countered = false
        if (early) return false
        lg.news += News(lg.season, lg.day, "${c.name} walks away from ${if (lg.pro) "the ${lg.user.name}" else lg.user.school}", true, -1, "move",
            "His contract was up and he wanted more. He'll take calls from teams with openings.", "Hoops Wire", lg.user.id, -1)
        c.contractSalary = 0
        return true
    }

    /** An expired deal he never answered: he signs it when the offseason moves on. */
    fun settle(lg: League) {
        val c = runCatching { lg.userCoach }.getOrNull() ?: return
        if (c.offerSalary > 0 && !c.offerEarly) accept(lg)
        else if (c.offerEarly) { c.offerSalary = 0; c.offerYears = 0 }
        c.countered = false
    }

    /** What a team with an opening would pay him, for the job-offer lists. */
    fun pitch(lg: League, t: Team): String {
        val c = lg.userCoach
        return "${money(salaryFor(lg, t, c))} a year · ${yearsFor(lg)} years"
    }
}
