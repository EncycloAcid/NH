package com.nh.hoopscoach.game

import kotlin.math.roundToInt
import kotlin.math.pow

object Gameplan {

    private const val PRO_STARTER_MIN_BASE = 24
    private const val PRO_STARTER_MIN_SLOPE = 1.0
    private val PRO_MINUTE_BASE = doubleArrayOf(31.7, 29.7, 28.3, 27.5, 26.5, 26.5, 22.0, 19.5, 15.3, 13.0)

    private fun rate(lg: League, p: Player): Double =
        p.ovr + (if (lg.pro && p.age <= 23) ((p.pot - p.ovr) / 3.0).coerceIn(0.0, 4.0) else 0.0) +
            (if (lg.pro && p.id == lg.heroId && p.age <= 23) 2.0 else 0.0) +
            (if (lg.pro && p.proYears == 0 && p.year == PRO_YEAR && p.pot >= 88) 2.5 else 0.0)

    private val NEIGHBORS = mapOf(
        Pos.PG to listOf(Pos.PG, Pos.SG, Pos.SF, Pos.PF, Pos.C),
        Pos.SG to listOf(Pos.SG, Pos.PG, Pos.SF, Pos.PF, Pos.C),
        Pos.SF to listOf(Pos.SF, Pos.SG, Pos.PF, Pos.PG, Pos.C),
        Pos.PF to listOf(Pos.PF, Pos.C, Pos.SF, Pos.SG, Pos.PG),
        Pos.C to listOf(Pos.C, Pos.PF, Pos.SF, Pos.SG, Pos.PG),
    )

    fun usageFor(p: Player, pro: Boolean = false): Int =
        // Pro ratings sit close together, so usage is stretched there or stars would share the ball like role players.
        if (pro) ((Ratings.offense(p) - 84) * 2.0 + 35).roundToInt().coerceIn(10, 99)
        else (Ratings.offense(p) * 1.3 - 30).roundToInt().coerceIn(10, 99)

    /** Picks starters and backups by position, then assigns pro minutes by roster rank. */
    fun autoAllocate(lg: League, t: Team) {
        val pool = lg.rosterOf(t).sortedByDescending { rate(lg, it) }.toMutableList()
        val starters = arrayOfNulls<Player>(5)
        val reserves = arrayOfNulls<Player>(5)
        fun take(pos: Pos): Player? {
            for ((rank, alt) in NEIGHBORS.getValue(pos).withIndex()) {
                // A player whose first or second position is this one is a natural fit.
                // Allow an out-of-position player only if he's clearly better.
                val cand = (if (rank == 0) pool.firstOrNull { it.plays(pos) } else pool.firstOrNull { it.pos == alt }) ?: continue
                if (rank == 0) { pool.remove(cand); return cand }
                val natural = pool.firstOrNull { it.plays(pos) }
                if (natural == null || rate(lg, cand) - rate(lg, natural) > 6 * rank) { pool.remove(cand); return cand }
            }
            return pool.removeFirstOrNull()
        }
        // Fill the scarcest positions first.
        val order = Pos.entries.sortedBy { pos -> pool.count { it.plays(pos) } }
        for (pos in order) starters[pos.ordinal] = take(pos)
        for (pos in order) reserves[pos.ordinal] = take(pos)
        // A backup clearly better than the starter at his own or the next position starts there instead.
        repeat(5) {
            var best: Triple<Int, Int, Double>? = null
            for (ri in 0 until 5) {
                val r = reserves[ri] ?: continue
                val near = NEIGHBORS.getValue(r.pos).take(if (lg.pro) 3 else 2) + listOfNotNull(r.pos2)
                for (alt in near.distinct()) {
                    val s = starters[alt.ordinal] ?: continue
                    val gain = rate(lg, r) - rate(lg, s)
                    if (gain >= (if (lg.pro) 2.0 else 3.0) && (best == null || gain > best!!.third)) best = Triple(ri, alt.ordinal, gain)
                }
            }
            val b = best ?: return@repeat
            val promoted = reserves[b.first]!!
            reserves[b.first] = starters[b.second]
            starters[b.second] = promoted
        }
        // Coaches play their best: a player left out who is better than a backup at his own or a nearby
        // position takes that backup's minutes, so nobody good sits all season for being third at his spot.
        var guard = 0
        while (guard++ < 5) {
            val out = pool.maxByOrNull { rate(lg, it) } ?: break
            val near = NEIGHBORS.getValue(out.pos).take(3) + listOfNotNull(out.pos2)
            val i = (0 until 5).filter { Pos.entries[it] in near && reserves[it] != null }.minByOrNull { rate(lg, reserves[it]!!) } ?: break
            val bench = reserves[i]!!
            if (rate(lg, out) < rate(lg, bench) + 1.0) break
            reserves[i] = out
            pool.remove(out)
            pool += bench
        }
        t.plan.slots = MutableList(5) { i ->
            val s = starters[i]
            val r = reserves[i]
            val sMin = when {
                s == null -> 0
                r == null -> 40
                lg.pro -> (PRO_STARTER_MIN_BASE + (rate(lg, s) - rate(lg, r)) * PRO_STARTER_MIN_SLOPE).roundToInt().coerceIn(20, 31)
                else -> (28 + (rate(lg, s) - rate(lg, r)) * 0.6).roundToInt().coerceIn(22, 36)
            }
            Slot(s?.id ?: -1, r?.id ?: -1, sMin, s?.let { usageFor(it, lg.pro) } ?: 50, r?.let { usageFor(it, lg.pro) } ?: 40)
        }
        if (!lg.pro) {
            t.plan.minutes.clear()
            return
        }
        t.plan.minutes = proTargets(lg, t, lg.rosterOf(t))
        for (slot in t.plan.slots) slot.starterMin = (t.plan.minutes[slot.starter] ?: 0).coerceAtMost(40)
    }

    fun proTargets(lg: League, t: Team, players: List<Player>): MutableMap<Int, Int> {
        val ranked = players.sortedByDescending { rate(lg, it) }
        val top = ranked.take(PRO_MINUTE_BASE.size)
        if (ranked.isEmpty()) return mutableMapOf()
        val meanRate = top.map { rate(lg, it) }.average()
        val claims = ranked.mapIndexed { i, p ->
            val base = if (i < PRO_MINUTE_BASE.size) {
                (PRO_MINUTE_BASE[i] + 0.8 * (rate(lg, p) - meanRate)).coerceAtLeast(0.0)
            } else 0.0
            val fp = p.fingerprint
            val claim = if (fp != null && fp.mpg > 0.0) {
                val w = 0.75 * 0.5.pow((lg.season - fp.season).toDouble())
                w * fp.mpg + (1.0 - w) * base
            } else base
            p to claim
        }.sortedByDescending { it.second }.take(PRO_MINUTE_BASE.size)
        val targets = claims.map { it.second.coerceIn(0.0, 38.0) }.toMutableList()
        repeat(3) {
            val total = targets.sum()
            if (total > 0.0) for (i in targets.indices) targets[i] = (targets[i] * 240.0 / total).coerceIn(0.0, 38.0)
        }
        val rounded = targets.map { it.roundToInt() }.toMutableList()
        rounded[0] += 240 - rounded.sum()
        return claims.indices.associate { claims[it].first.id to rounded[it] }.toMutableMap()
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
        if (t.plan.minutes.isNotEmpty()) return t.plan.minutes[pid] ?: 0
        for (s in t.plan.slots) {
            if (s.starter == pid) return s.starterMin
            if (s.reserve == pid) return 40 - s.starterMin
        }
        return 0
    }

    fun role(t: Team, pid: Int): String {
        if (t.plan.minutes.isNotEmpty()) {
            if (t.plan.slots.any { it.starter == pid }) return "Starter"
            return if ((t.plan.minutes[pid] ?: 0) > 0) "Rotation" else "Bench"
        }
        for (s in t.plan.slots) {
            if (s.starter == pid) return "Starter"
            if (s.reserve == pid) return "Rotation"
        }
        return "Bench"
    }

    /** Team rating used for headers: minutes-weighted overall of the rotation. */
    fun teamRating(lg: League, t: Team, f: (Player) -> Int = { it.ovr }): Int {
        if (t.plan.minutes.isNotEmpty()) {
            var total = 0.0
            var weight = 0
            for (pid in t.roster) {
                val minutes = t.plan.minutes[pid] ?: 0
                val player = lg.playerOrNull(pid) ?: continue
                if (minutes <= 0) continue
                total += f(player) * minutes
                weight += minutes
            }
            if (weight > 0) return (total / weight).roundToInt()
            return lg.rosterOf(t).map(f).average().roundToInt()
        }
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
