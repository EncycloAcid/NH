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
    fun cap(lg: League) = ProCareer.cap(lg)
    fun tax(lg: League) = (cap(lg) * 1.215).roundToInt()
    fun apron(lg: League) = (cap(lg) * 1.268).roundToInt()
    fun mle(lg: League) = (cap(lg) * 0.0915).roundToInt() / 10 * 10

    fun salaryOf(lg: League, p: Player): Int = if (p.id == lg.heroId) lg.road?.contract?.salary ?: p.salary else p.salary

    fun total(lg: League, t: Team): Int = lg.rosterOf(t).sumOf { salaryOf(lg, it) }

    fun room(lg: League, t: Team): Int = cap(lg) - total(lg, t)

    fun status(lg: League, t: Team): String {
        val total = total(lg, t)
        return when { total > apron(lg) -> "Over the apron"; total > tax(lg) -> "Paying the luxury tax"; total > cap(lg) -> "Over the cap"; else -> "Under the cap" }
    }

    /** Every pro without a deal gets one: his market value give or take, for one to four years. */
    fun sign(lg: League) {
        if (!lg.pro) return
        for (t in lg.teams) for (p in lg.rosterOf(t)) {
            if (p.id == lg.heroId || (p.salary > 0 && p.contractYears > 0)) continue
            p.salary = ((ProCareer.marketValue(lg, p) * (0.85 + Rng.d() * 0.3)).roundToInt() / 10 * 10).coerceAtLeast(ProCareer.minSalary(lg, p.proYears))
            p.contractYears = Rng.range(1, 4)
        }
    }

    /** A rookie's deal by draft slot: four years in the first round, two in the second. */
    fun rookie(lg: League, p: Player, pick: Int) {
        p.salary = ProCareer.rookieSalary(lg, pick)
        p.contractYears = if (pick <= lg.teams.size) 4 else 2
    }

    /** A new season: a year comes off every deal, and expiring players re-sign at today's value. */
    fun newSeason(lg: League) {
        if (!lg.pro) return
        for (p in lg.players) if (p.contractYears > 0) p.contractYears--
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
