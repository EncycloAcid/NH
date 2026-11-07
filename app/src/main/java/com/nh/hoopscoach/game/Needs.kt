package com.nh.hoopscoach.game

/**
 * What each team needs and what it can spare — the lens trades and free agency are looked through.
 * A team is contending, rebuilding or neutral (from its record this season, or last season's in the
 * summer); it needs a player when he'd start at his spot or be clearly second there; a player is
 * surplus when the team is deeper at his spot than he is good, or his money outweighs his play on a
 * team that can't afford it.
 */
object Needs {

    enum class Direction { CONTENDING, NEUTRAL, REBUILDING }

    /** Contenders buy help now, rebuilders sell it for the future. From the record mid-season, the last full season in the summer (records are reset then). */
    fun direction(lg: League, t: Team): Direction {
        val pct = if (t.gp >= 10) t.winPct else t.history.lastOrNull()?.let { h -> if (h.w + h.l > 0) h.w.toDouble() / (h.w + h.l) else 0.5 } ?: 0.5
        return when {
            pct >= 0.56 -> Direction.CONTENDING
            pct < 0.42 -> Direction.REBUILDING
            else -> Direction.NEUTRAL
        }
    }

    /** Overalls at [pos] on [t]'s roster, best first, optionally minus players in [out]. */
    fun depthAt(lg: League, t: Team, pos: Pos, out: Set<Int> = emptySet()): List<Int> =
        lg.rosterOf(t).filter { it.pos == pos && it.id !in out }.map { it.ovr }.sortedDescending()

    /**
     * How much [t] wants [p] at his position, as a multiplier on his value: he fills a hole at 1.3,
     * upgrades a weak starter at ~1.15, adds depth around 1.0, crowds a set spot below 0.9.
     */
    fun needFactor(lg: League, t: Team, p: Player): Double {
        val d = depthAt(lg, t, p.pos)
        val best = d.firstOrNull() ?: 0
        val second = d.getOrNull(1) ?: 0
        var f = when {
            d.isEmpty() -> 1.3
            p.ovr >= best + 3 -> 1.28
            p.ovr >= best -> 1.16
            p.ovr >= second + 4 -> 1.1
            p.ovr >= second -> 1.0
            d.size <= 2 -> 0.95
            else -> 0.85
        }
        // A contender pays a little more for a piece that starts now; a rebuilder for youth.
        when (direction(lg, t)) {
            Direction.CONTENDING -> if (p.ovr >= best) f += 0.05
            Direction.REBUILDING -> f *= if (p.age <= 24) 1.08 else if (p.age >= 30) 0.82 else 1.0
            Direction.NEUTRAL -> {}
        }
        return f
    }

    /**
     * Would moving [p] hurt? He's surplus when at least two teammates at his spot are as good as he
     * is, when the whole roster is stacked enough that he's fourth at his spot, or when his contract
     * is a drag on a team over the tax line.
     */
    fun isSurplus(lg: League, t: Team, p: Player): Boolean {
        val d = depthAt(lg, t, p.pos, setOf(p.id))
        if (d.count { it >= p.ovr - 2 } >= 2 || (d.size >= 3 && p.ovr < d[1])) return true
        if (Payroll.total(lg, t) > Payroll.tax(lg)) {
            val fair = ProCareer.marketValue(lg, p)
            if (Payroll.salaryOf(lg, p) > fair * 1.35 && Trades.tier(p) != "Franchise") return true
        }
        return false
    }

    /** The best position where [t] could use a starter: its weakest best player, null if every spot is strong. */
    fun weakestSpot(lg: League, t: Team): Pos? {
        var worst: Pos? = null
        var worstOvr = 101
        for (pos in Pos.entries) {
            val best = depthAt(lg, t, pos).firstOrNull() ?: 0
            if (best < worstOvr) { worstOvr = best; worst = pos }
        }
        return if (worstOvr < 86) worst else null
    }

    /** Role a player would walk into on [t]: Starter, Sixth man, Rotation or Depth. */
    fun roleFor(lg: League, t: Team, p: Player): String {
        val ahead = depthAt(lg, t, p.pos, setOf(p.id)).count { it >= p.ovr }
        return when (ahead) { 0 -> "starter"; 1 -> "sixth man"; 2 -> "rotation"; else -> "depth" }
    }
}
