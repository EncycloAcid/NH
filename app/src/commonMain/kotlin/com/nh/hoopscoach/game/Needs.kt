package com.nh.hoopscoach.game

/**
 * What each team needs and what it can spare — the lens trades and free agency are looked through.
 * A team is contending, rebuilding or neutral (from its record this season, or last season's in the
 * summer); it needs a player when he'd start at his spot or be clearly second there; a player is
 * surplus when the team is deeper at his spot than he is good, or his money outweighs his play on a
 * team that can't afford it. Supports BLM 27-style Trade Block, Untouchables, and positional Needs/Surplus.
 */
object Needs {

    enum class Direction(val label: String) {
        CONTENDING("Contending"),
        NEUTRAL("Neutral"),
        REBUILDING("Rebuilding"),
    }

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

    /** Auto-detected positional needs for [t]: spots where its starter is weak (<86) or depth is thin (<2). */
    fun autoNeeds(lg: League, t: Team): List<Pos> {
        val weak = Pos.entries.filter { pos ->
            val d = depthAt(lg, t, pos)
            (d.firstOrNull() ?: 0) < 86 || d.size < 2
        }.sortedBy { depthAt(lg, t, it).firstOrNull() ?: 0 }
        return weak.ifEmpty { listOfNotNull(Pos.entries.minByOrNull { depthAt(lg, t, it).firstOrNull() ?: 0 }) }.take(3)
    }

    /** Auto-detected surplus positions for [t]: spots where it has 3+ solid players or 2+ starters. */
    fun autoSurplus(lg: League, t: Team): List<Pos> =
        Pos.entries.filter { pos ->
            val d = depthAt(lg, t, pos)
            d.count { it >= 83 } >= 3 || d.count { it >= 87 } >= 2
        }

    /** Active positional needs for [t] (custom if set, otherwise auto-detected). */
    fun needsOf(lg: League, t: Team): List<Pos> {
        val custom = lg.proState?.customNeeds?.get(t.id).orEmpty()
        return if (custom.isNotEmpty()) custom else autoNeeds(lg, t)
    }

    /** Active surplus positions for [t] (custom if set, otherwise auto-detected). */
    fun surplusPositions(lg: League, t: Team): List<Pos> {
        val custom = lg.proState?.customSurplus?.get(t.id).orEmpty()
        return if (custom.isNotEmpty()) custom else autoSurplus(lg, t)
    }

    /** Untouchable player IDs for [t]: players the team refuses to include in standard trade packages. */
    fun untouchableIds(lg: League, t: Team): Set<Int> {
        val ps = lg.proState
        val roster = t.roster.toSet()
        if (NbaCoach.active(lg) && t.id == lg.userTeamId) {
            return ps?.untouchables?.get(t.id).orEmpty().filter { it in roster }.toSet()
        }
        val explicit = ps?.untouchables?.get(t.id).orEmpty().filter { it in roster }.toSet()
        if (explicit.isNotEmpty()) return explicit
        val dir = direction(lg, t)
        val players = lg.rosterOf(t)
        val best = players.maxByOrNull { Trades.value(it) }
        return players.filter { p ->
            p.id == lg.heroId ||
                (p.ovr >= 94 && p.age <= 30) ||
                (p.age <= 24 && (p.pot >= 93 || p.ovr >= 89)) ||
                (dir == Direction.CONTENDING && p.id == best?.id && p.ovr >= 91)
        }.map { it.id }.toSet()
    }

    /** Player IDs currently on the Trade Block for [t]. */
    fun onBlockIds(lg: League, t: Team): Set<Int> {
        val ps = lg.proState
        val roster = t.roster.toSet()
        val untouch = untouchableIds(lg, t)
        if (NbaCoach.active(lg) && t.id == lg.userTeamId) {
            return ps?.onTheBlock?.get(t.id).orEmpty().filter { it in roster && it !in untouch }.toSet()
        }
        val explicit = ps?.onTheBlock?.get(t.id).orEmpty().filter { it in roster && it !in untouch }.toSet()
        if (explicit.isNotEmpty()) return explicit
        val dir = direction(lg, t)
        val players = lg.rosterOf(t).filter { it.id !in untouch && it.id != lg.heroId }
        val block = when (dir) {
            Direction.REBUILDING -> players.filter { (it.age >= 27 && it.ovr >= 82) || (it.contractYears <= 1 && it.age >= 26 && it.ovr >= 81) }
                .sortedByDescending { Trades.value(it) }.take(3)
            Direction.CONTENDING -> players.filter { isSurplusBase(lg, t, it) && it.ovr >= 80 }
                .sortedByDescending { Payroll.salaryOf(lg, it) }.take(2)
            Direction.NEUTRAL -> players.filter { (isSurplusBase(lg, t, it) && it.ovr >= 81) || (it.contractYears <= 1 && it.age >= 29 && it.ovr >= 83) }
                .sortedByDescending { Trades.value(it) }.take(2)
        }
        return block.map { it.id }.toSet()
    }

    fun isUntouchable(lg: League, t: Team, p: Player): Boolean = p.id in untouchableIds(lg, t)

    fun isOnBlock(lg: League, t: Team, p: Player): Boolean = p.id in onBlockIds(lg, t)

    /** True when [p] fills one of [t]'s needed positions at a starter or strong rotation level. */
    fun fitsNeed(lg: League, t: Team, p: Player): Boolean {
        val needed = needsOf(lg, t)
        if (p.pos !in needed && (p.pos2 == null || p.pos2 !in needed)) return false
        val best = depthAt(lg, t, p.pos).firstOrNull() ?: 0
        val second = depthAt(lg, t, p.pos).getOrNull(1) ?: 0
        return p.ovr >= best - 1 || (p.ovr >= 82 && p.ovr >= second)
    }

    /** Toggle [pid] on/off the user's Trade Block (clears Untouchable if set). */
    fun toggleBlock(lg: League, t: Team, pid: Int) {
        val ps = lg.proState ?: return
        val b = ps.onTheBlock.getOrPut(t.id) { mutableListOf() }
        val u = ps.untouchables.getOrPut(t.id) { mutableListOf() }
        if (pid in b) b.remove(pid) else { u.remove(pid); b.add(pid) }
    }

    /** Toggle [pid] on/off the user's Untouchables list (clears Trade Block if set). */
    fun toggleUntouchable(lg: League, t: Team, pid: Int) {
        val ps = lg.proState ?: return
        val b = ps.onTheBlock.getOrPut(t.id) { mutableListOf() }
        val u = ps.untouchables.getOrPut(t.id) { mutableListOf() }
        if (pid in u) u.remove(pid) else { b.remove(pid); u.add(pid) }
    }

    fun toggleNeedPos(lg: League, t: Team, pos: Pos) {
        val ps = lg.proState ?: return
        val list = ps.customNeeds.getOrPut(t.id) { autoNeeds(lg, t).toMutableList() }
        if (pos in list) list.remove(pos) else list.add(pos)
    }

    fun toggleSurplusPos(lg: League, t: Team, pos: Pos) {
        val ps = lg.proState ?: return
        val list = ps.customSurplus.getOrPut(t.id) { autoSurplus(lg, t).toMutableList() }
        if (pos in list) list.remove(pos) else list.add(pos)
    }

    /** Resets [t]'s Needs, Surplus, Trade Block, and Untouchables to smart auto-detected defaults. */
    fun autoDetectBlock(lg: League, t: Team) {
        val ps = lg.proState ?: return
        ps.customNeeds.remove(t.id)
        ps.customSurplus.remove(t.id)
        val players = lg.rosterOf(t)
        val untouch = players.filter { (it.ovr >= 92 && it.age <= 29) || (it.age <= 24 && it.pot >= 91) }.sortedByDescending { it.ovr }.take(2).map { it.id }
        ps.untouchables[t.id] = untouch.toMutableList()
        val block = players.filter { it.id !in untouch && isSurplusBase(lg, t, it) && it.ovr >= 80 }.sortedByDescending { Payroll.salaryOf(lg, it) }.take(3).map { it.id }
        ps.onTheBlock[t.id] = block.toMutableList()
    }

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
        val customN = lg.proState?.customNeeds?.get(t.id).orEmpty()
        if (customN.isNotEmpty() && (p.pos in customN || p.pos2 in customN)) f = maxOf(f, 1.15)
        // A contender pays a little more for a piece that starts now; a rebuilder for youth.
        when (direction(lg, t)) {
            Direction.CONTENDING -> if (p.ovr >= best) f += 0.05
            Direction.REBUILDING -> f *= if (p.age <= 24) 1.08 else if (p.age >= 30) 0.82 else 1.0
            Direction.NEUTRAL -> {}
        }
        return f
    }

    private fun isSurplusBase(lg: League, t: Team, p: Player): Boolean {
        val d = depthAt(lg, t, p.pos, setOf(p.id))
        if (d.count { it >= p.ovr - 2 } >= 2 || (d.size >= 3 && p.ovr < d[1])) return true
        if (Payroll.total(lg, t) > Payroll.tax(lg)) {
            val fair = ProCareer.marketValue(lg, p)
            if (Payroll.salaryOf(lg, p) > fair * 1.35 && Trades.tier(p) != "Franchise") return true
        }
        return false
    }

    /**
     * Would moving [p] hurt? He's surplus when he's on the Trade Block, when at least two teammates
     * at his spot are as good as he is, when the whole roster is stacked enough that he's fourth at
     * his spot, or when his contract is a drag on a team over the tax line.
     */
    fun isSurplus(lg: League, t: Team, p: Player): Boolean {
        if (p.id in untouchableIds(lg, t)) return false
        if (lg.proState?.onTheBlock?.get(t.id)?.contains(p.id) == true) return true
        val customS = lg.proState?.customSurplus?.get(t.id).orEmpty()
        if (p.pos in customS && (depthAt(lg, t, p.pos, setOf(p.id)).firstOrNull() ?: 0) >= p.ovr) return true
        return isSurplusBase(lg, t, p)
    }

    /** The best position where [t] could use a starter: its weakest best player, null if every spot is strong. */
    fun weakestSpot(lg: League, t: Team): Pos? {
        val custom = lg.proState?.customNeeds?.get(t.id).orEmpty()
        if (custom.isNotEmpty()) return custom.minByOrNull { depthAt(lg, t, it).firstOrNull() ?: 0 }
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
