package com.nh.hoopscoach.game

import kotlin.math.roundToInt

object Gameplan {

    private val NEIGHBORS = mapOf(
        Pos.PG to listOf(Pos.PG, Pos.SG, Pos.SF, Pos.PF, Pos.C),
        Pos.SG to listOf(Pos.SG, Pos.PG, Pos.SF, Pos.PF, Pos.C),
        Pos.SF to listOf(Pos.SF, Pos.SG, Pos.PF, Pos.PG, Pos.C),
        Pos.PF to listOf(Pos.PF, Pos.C, Pos.SF, Pos.SG, Pos.PG),
        Pos.C to listOf(Pos.C, Pos.PF, Pos.SF, Pos.SG, Pos.PG),
    )

    fun usageFor(p: Player, pro: Boolean = false): Int =
        // Pro ratings sit close together, so usage is stretched there or stars would share the ball like role players.
        if (pro) ((Ratings.offense(p) - 84) * 5.0 + 22).roundToInt().coerceIn(10, 99)
        else (Ratings.offense(p) * 1.3 - 30).roundToInt().coerceIn(10, 99)

    /** Picks starters and backups by position and splits the minutes. */
    fun autoAllocate(lg: League, t: Team) {
        // Pro coaches give young, high-ceiling players development minutes: up to 4 points of credit.
        fun rate(p: Player): Double = p.ovr + if (lg.pro && p.age <= 23) ((p.pot - p.ovr) / 3.0).coerceIn(0.0, 4.0) else 0.0
        val pool = lg.rosterOf(t).sortedByDescending { rate(it) }.toMutableList()
        val starters = arrayOfNulls<Player>(5)
        val reserves = arrayOfNulls<Player>(5)
        fun take(pos: Pos): Player? {
            for ((rank, alt) in NEIGHBORS.getValue(pos).withIndex()) {
                // Allow an out-of-position player only if he's clearly better.
                val cand = pool.firstOrNull { it.pos == alt } ?: continue
                if (rank == 0) { pool.remove(cand); return cand }
                val natural = pool.firstOrNull { it.pos == pos }
                if (natural == null || rate(cand) - rate(natural) > 6 * rank) { pool.remove(cand); return cand }
            }
            return pool.removeFirstOrNull()
        }
        // Fill the scarcest positions first.
        val order = Pos.entries.sortedBy { pos -> pool.count { it.pos == pos } }
        for (pos in order) starters[pos.ordinal] = take(pos)
        for (pos in order) reserves[pos.ordinal] = take(pos)
        t.plan.slots = MutableList(5) { i ->
            val s = starters[i]
            val r = reserves[i]
            val sMin = when {
                s == null -> 0
                r == null -> 40
                else -> (28 + (rate(s) - rate(r)) * 0.6).roundToInt().coerceIn(22, 36)
            }
            Slot(s?.id ?: -1, r?.id ?: -1, sMin, s?.let { usageFor(it, lg.pro) } ?: 50, r?.let { usageFor(it, lg.pro) } ?: 40)
        }
    }

    /** Makes sure the plan only references current roster players. */
    fun validate(lg: League, t: Team) {
        val ids = t.roster.toSet()
        val slots = t.plan.slots
        val broken = slots.size != 5 || slots.any { (it.starter != -1 && it.starter !in ids) || (it.reserve != -1 && it.reserve !in ids) } ||
            slots.flatMap { listOf(it.starter, it.reserve) }.filter { it != -1 }.let { it.size != it.toSet().size } ||
            slots.any { it.starter == -1 && t.roster.size >= 5 }
        if (broken || t.plan.auto && !lg.humanCoach(t)) autoAllocate(lg, t)
    }

    fun plannedMinutes(t: Team, pid: Int): Int {
        for (s in t.plan.slots) {
            if (s.starter == pid) return s.starterMin
            if (s.reserve == pid) return 40 - s.starterMin
        }
        return 0
    }

    fun role(t: Team, pid: Int): String {
        for (s in t.plan.slots) {
            if (s.starter == pid) return "Starter"
            if (s.reserve == pid) return "Rotation"
        }
        return "Bench"
    }

    /** Team rating used for headers: minutes-weighted overall of the rotation. */
    fun teamRating(lg: League, t: Team, f: (Player) -> Int = { it.ovr }): Int {
        var s = 0.0
        var w = 0.0
        for (slot in t.plan.slots) {
            val st = if (slot.starter >= 0) lg.playerOrNull(slot.starter) else null
            val rs = if (slot.reserve >= 0) lg.playerOrNull(slot.reserve) else null
            if (st != null) { val m = slot.starterMin.toDouble(); s += f(st) * m; w += m }
            if (rs != null) { val m = (40 - slot.starterMin).toDouble(); s += f(rs) * m; w += m }
        }
        if (w == 0.0) return lg.rosterOf(t).map(f).average().roundToInt()
        return (s / w).roundToInt()
    }

    fun swapInto(lg: League, t: Team, slotIndex: Int, starter: Boolean, pid: Int) {
        val slots = t.plan.slots
        // If the player already holds another spot, trade places.
        val target = slots[slotIndex]
        val old = if (starter) target.starter else target.reserve
        for ((i, s) in slots.withIndex()) {
            if (s.starter == pid) { s.starter = old; if (old >= 0) s.starterUsg = usageFor(lg.player(old), lg.pro) }
            if (s.reserve == pid) { s.reserve = old; if (old >= 0) s.reserveUsg = usageFor(lg.player(old), lg.pro) }
            if (i == slotIndex) Unit
        }
        if (starter) { target.starter = pid; target.starterUsg = usageFor(lg.player(pid), lg.pro) }
        else { target.reserve = pid; target.reserveUsg = usageFor(lg.player(pid), lg.pro) }
        if (target.reserve == -1) target.starterMin = 40
        t.plan.auto = false
    }
}
