package com.nh.hoopscoach.game

import kotlin.math.min
import kotlin.math.roundToInt

/** NIL money, all in thousands of dollars per season. */
object Finance {
    fun donor(prestige: Int): Int = (250 + prestige * prestige * 1.05).roundToInt() / 5 * 5

    fun revShare(lg: League, t: Team): Int = when (lg.conf(t.confId).tier) { 0 -> 4000; 1 -> 1200; else -> 350 }

    fun bonusTotal(t: Team) = t.bonuses.sumOf { it.amount }

    fun bonusCap(lg: League, t: Team) = ((donor(t.prestige) + revShare(lg, t)) * 0.12).roundToInt()

    fun appliedBonus(lg: League, t: Team) = min(bonusTotal(t), bonusCap(lg, t))

    /** Fans add to the donor pool when they're excited and pull money out when they're not. */
    fun fanMoney(t: Team) = Fans.nilBoost(t, donor(t.prestige))

    fun budget(lg: League, t: Team): Int = donor(t.prestige) + fanMoney(t) + revShare(lg, t) + appliedBonus(lg, t)

    /** Budget the program would have at a different prestige (for year-over-year comparisons). */
    fun budgetAt(lg: League, t: Team, prestige: Int): Int {
        val base = donor(prestige) + revShare(lg, t)
        return base + Fans.nilBoost(t, donor(prestige)) + min(bonusTotal(t), (base * 0.12).roundToInt())
    }

    /** Salaries owed to players who will be on next season's roster. */
    fun committed(lg: League, t: Team, includeSeniors: Boolean = true): Int =
        t.roster.map { lg.player(it) }.filter { includeSeniors || it.year < 4 }.sumOf { it.nil }

    /** NIL promised to recruits who have committed but not yet enrolled. */
    fun promised(lg: League, t: Team): Int = lg.recruits.filter { it.committed == t.id }.sumOf { r ->
        if (lg.humanCoach(t)) r.nilOffer else Ratings.expectedNil(r.p)
    }

    fun available(lg: League, t: Team, includeSeniors: Boolean = true): Int =
        budget(lg, t) - committed(lg, t, includeSeniors) - promised(lg, t)

    /** Called once the title game is over: performance money for next year's pool. */
    fun awardBonuses(lg: League) {
        val yr = "'${(lg.season - 1) % 100}-${lg.season % 100}"
        val f4Confs = HashSet<Int>()
        for (t in lg.teams) {
            val round = Awards.tourneyRound(lg, t.id)
            val (label, amt, yrs) = when (round) {
                7 -> Triple("National Title", 3000, 3)
                6 -> Triple("Runner-Up", 2500, 3)
                5 -> Triple("Final Four", 2000, 3)
                4 -> Triple("Elite Eight", 750, 2)
                3 -> Triple("Sweet 16", 400, 2)
                2 -> Triple("Round of 32", 150, 1)
                else -> Triple("", 0, 0)
            }
            if (amt > 0) t.bonuses += NilBonus("$yr $label bonus for ${t.school}", amt, yrs)
            if (round >= 5) f4Confs += t.confId
        }
        for (ev in lg.events) ev.placement.firstOrNull()?.let { lg.team(it).bonuses += NilBonus("$yr MTE Champion bonus for ${lg.team(it).school}", 325, 1) }
        for (b in lg.confBrackets) if (b.champion >= 0) lg.team(b.champion).bonuses += NilBonus("$yr Conference Tournament bonus for ${lg.team(b.champion).school}", 200, 1)
        lg.nit?.champion?.takeIf { it >= 0 }?.let { lg.team(it).bonuses += NilBonus("$yr NIT Champion bonus for ${lg.team(it).school}", 150, 1) }
        for (cid in f4Confs) {
            val c = lg.conf(cid)
            for (tid in c.teamIds) lg.team(tid).bonuses += NilBonus("$yr Final Four bonus for ${c.abbr}", 275, 3, conference = true)
        }
    }

    /** At the start of a new season every bonus has paid out one more year. */
    fun decayBonuses(lg: League) {
        for (t in lg.teams) {
            t.bonuses.forEach { it.seasonsLeft-- }
            t.bonuses.removeAll { it.seasonsLeft <= 0 }
        }
    }

    fun bonusNote(b: NilBonus) = (if (b.conference) "Conference rev-share" else "NIL pool") + " · " +
        if (b.seasonsLeft <= 1) "Final season" else "Pays for ${b.seasonsLeft - 1} more season${if (b.seasonsLeft - 1 == 1) "" else "s"}"
}
