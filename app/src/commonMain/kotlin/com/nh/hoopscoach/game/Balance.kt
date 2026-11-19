package com.nh.hoopscoach.game

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Keeps the league's talent where the real NBA's is. Players grow and decline on their own, but each
 * summer anyone rated well above where his rank in the league would put him in today's real league
 * (NBA 2K27's spread: about 20 players at 90 or better, a typical starter in the high 70s) comes back
 * down to it. So stars stay rare: a team might have one or two 90s, not five. Your own player in a
 * player career is never touched; his growth is earned.
 */
object Balance {
    /** The real league's overalls, best first, on the game's internal scale. */
    private val reference: DoubleArray by lazy {
        EraData.todayOveralls().map { Ratings.proInternal(it).toDouble() }.toDoubleArray()
    }

    /** Where the real league puts the player at [frac] of the way down the list (0 the best). */
    private fun refAt(frac: Double): Double {
        val r = reference
        if (r.isEmpty()) return 99.0
        val x = (frac * (r.size - 1)).coerceIn(0.0, (r.size - 1).toDouble())
        val i = x.toInt()
        val j = min(i + 1, r.size - 1)
        return r[i] + (r[j] - r[i]) * (x - i)
    }

    /** Brings anyone more than [slack] points above his rank's real rating back down to it. Returns how many moved. */
    fun pro(lg: League, slack: Int = 1): Int {
        if (!lg.pro || reference.isEmpty()) return 0
        val all = lg.teams.flatMap { lg.rosterOf(it) }.sortedByDescending { it.ovr }
        if (all.size < 60) return 0
        var moved = 0
        for ((i, p) in all.withIndex()) {
            if (p.id == lg.heroId) continue
            val cap = (refAt(i.toDouble() / all.size) + slack).roundToInt()
            if (p.ovr > cap) { lower(p, cap); moved++ }
        }
        return moved
    }

    /** Lowers [p]'s ratings evenly until his overall is [target], and his ceiling with them. */
    fun lower(p: Player, target: Int) {
        val before = p.ovr
        repeat(4) {
            val diff = target - p.ovr
            if (diff >= 0) return@repeat
            for (i in p.attrs.indices) p.attrs[i] = (p.attrs[i] + diff).coerceIn(20, 99)
        }
        val drop = before - p.ovr
        p.pot = max(p.ovr, p.pot - drop)
        if (p.ovrHistory.isNotEmpty()) p.ovrHistory[p.ovrHistory.size - 1] = p.ovr
    }

    /** A draftee's ceiling: most become rotation players; a few, mostly the youngest at the top of a draft, become stars. */
    fun rookieCeiling(ovr: Int, age: Int): Int {
        val upside = (22 - age).coerceAtLeast(0) * 1.0 + 3.5 + Rng.gauss() * 3.0
        return (ovr + upside).roundToInt().coerceIn(ovr, 99)
    }

    /**
     * A real player's ceiling from his 2K potential grade. The grades are generous (more than a hundred of
     * today's players grade out at 90 or better), so only part of the gap counts, less the older he is.
     */
    fun realCeiling(ovr2k: Int, pot2k: Int, age: Int): Int {
        val share = when { age <= 21 -> 0.6; age <= 23 -> 0.45; age <= 25 -> 0.3; else -> 0.1 }
        return Ratings.proInternal((ovr2k + (pot2k - ovr2k).coerceAtLeast(0) * share).roundToInt())
    }
}
