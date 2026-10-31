package com.nh.hoopscoach.game

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/** Shared random source. Tests can reseed it for repeatable seasons. */
object Rng {
    var r: Random = Random(System.nanoTime())
    fun int(from: Int, until: Int) = r.nextInt(from, until)
    fun range(lo: Int, hi: Int) = r.nextInt(lo, hi + 1)
    fun d() = r.nextDouble()
    fun chance(p: Double) = r.nextDouble() < p
    fun gauss(): Double {
        // Box-Muller
        val u = max(1e-9, r.nextDouble())
        val v = r.nextDouble()
        return kotlin.math.sqrt(-2.0 * kotlin.math.ln(u)) * kotlin.math.cos(2 * Math.PI * v)
    }
    fun <T> pick(list: List<T>): T = list[r.nextInt(list.size)]
    /** Sorts by a noisy key, computing each key once so the sort stays consistent. */
    fun <T> noisyDesc(items: List<T>, key: (T) -> Double): List<T> = items.map { it to key(it) }.sortedByDescending { it.second }.map { it.first }
    fun <T> weighted(items: List<T>, weight: (T) -> Double): T {
        var total = 0.0
        for (it in items) total += max(0.0, weight(it))
        if (total <= 0) return pick(items)
        var x = r.nextDouble() * total
        for (it in items) {
            x -= max(0.0, weight(it))
            if (x <= 0) return it
        }
        return items.last()
    }
}

object Ratings {
    // Index order matches Attr: INS MID OUT HND PAS REB PER INT STL BLK
    private val W = arrayOf(
        doubleArrayOf(.10, .12, .16, .20, .20, .03, .11, .01, .06, .01), // PG
        doubleArrayOf(.13, .15, .22, .12, .08, .04, .15, .02, .07, .02), // SG
        doubleArrayOf(.15, .14, .15, .08, .08, .10, .14, .06, .06, .04), // SF
        doubleArrayOf(.20, .10, .08, .04, .05, .18, .07, .16, .03, .09), // PF
        doubleArrayOf(.24, .05, .03, .02, .05, .22, .04, .20, .02, .13), // C
    )
    private val POS_OFFSET = arrayOf(
        intArrayOf(-6, 2, 3, 10, 10, -14, 4, -16, 4, -18),
        intArrayOf(-3, 4, 6, 4, 0, -10, 4, -12, 3, -14),
        intArrayOf(1, 2, 1, -2, -2, -2, 2, -4, 0, -6),
        intArrayOf(5, -2, -6, -10, -6, 6, -6, 5, -5, 2),
        intArrayOf(7, -8, -16, -16, -8, 9, -12, 8, -8, 7),
    )

    fun ovr(pos: Pos, a: IntArray): Int {
        val w = W[pos.ordinal]
        var s = 0.0
        for (i in 0 until 10) s += w[i] * a[i]
        // Stretch a little so the stars separate from the pack.
        return (50 + (s - 50) * 1.08).roundToInt().coerceIn(25, 99)
    }

    private fun group(pos: Pos, a: IntArray, idx: IntArray): Int {
        val w = W[pos.ordinal]
        var s = 0.0
        var tw = 0.0
        for (i in idx) { s += w[i] * a[i]; tw += w[i] }
        return if (tw == 0.0) 0 else (s / tw).roundToInt()
    }

    private val OFF = intArrayOf(0, 1, 2, 3, 4)
    private val DEF = intArrayOf(5, 6, 7, 8, 9)
    private val SHOOT = intArrayOf(0, 1, 2)
    fun offense(p: Player) = group(p.pos, p.attrs, OFF)
    fun defense(p: Player) = group(p.pos, p.attrs, DEF)
    fun shooting(p: Player) = group(p.pos, p.attrs, SHOOT)

    fun styles(pos: Pos): List<Arch> = when (pos) {
        Pos.PG -> listOf(Arch.FLOOR_GENERAL, Arch.PLAYMAKER, Arch.SLASHER, Arch.SHARPSHOOTER, Arch.COMBO_GUARD, Arch.LOCKDOWN, Arch.TABLE_SETTER, Arch.BALL_HAWK, Arch.SHOT_CREATOR,
            Arch.TWO_WAY_GUARD, Arch.PEST, Arch.MICROWAVE)
        Pos.SG -> listOf(Arch.SHARPSHOOTER, Arch.SLASHER, Arch.COMBO_GUARD, Arch.LOCKDOWN, Arch.PLAYMAKER, Arch.THREE_AND_D, Arch.SHOT_CREATOR, Arch.BALL_HAWK,
            Arch.TWO_WAY_GUARD, Arch.MICROWAVE, Arch.MOVEMENT_SHOOTER, Arch.BULLY_GUARD, Arch.HIGH_FLYER)
        Pos.SF -> listOf(Arch.SLASHER, Arch.POINT_FORWARD, Arch.SHARPSHOOTER, Arch.LOCKDOWN, Arch.STRETCH, Arch.THREE_AND_D, Arch.GLUE_GUY, Arch.SHOT_CREATOR,
            Arch.MOVEMENT_SHOOTER, Arch.BULLY_GUARD, Arch.HIGH_FLYER, Arch.FACE_UP_FOUR)
        Pos.PF -> listOf(Arch.STRETCH, Arch.REBOUNDER, Arch.POST_SCORER, Arch.POINT_FORWARD, Arch.RIM_PROTECTOR, Arch.GLUE_GUY, Arch.RIM_RUNNER, Arch.ENFORCER,
            Arch.FACE_UP_FOUR, Arch.SWITCHABLE_BIG, Arch.PICK_AND_POP, Arch.HUSTLE_BIG)
        Pos.C -> listOf(Arch.REBOUNDER, Arch.POST_SCORER, Arch.RIM_PROTECTOR, Arch.STRETCH, Arch.RIM_RUNNER, Arch.POINT_CENTER, Arch.STRETCH_FIVE, Arch.ENFORCER,
            Arch.SWITCHABLE_BIG, Arch.PICK_AND_POP, Arch.HUSTLE_BIG)
    }

    /** Build an attribute set whose overall lands near [target]. */
    fun build(pos: Pos, style: Arch, target: Int): IntArray {
        val a = IntArray(10)
        val off = POS_OFFSET[pos.ordinal]
        for (i in 0 until 10) a[i] = target + off[i] + (Rng.gauss() * 4).roundToInt()
        for (b in style.boosts) a[b.ordinal] += 7
        for (i in 0 until 10) if (style.boosts.none { it.ordinal == i }) a[i] -= 2
        // Nudge everything so the overall hits the target.
        repeat(3) {
            val diff = target - ovr(pos, a)
            for (i in 0 until 10) a[i] += diff
        }
        for (i in 0 until 10) a[i] = a[i].coerceIn(20, 99)
        return a
    }

    fun heightFor(pos: Pos): Int = when (pos) {
        Pos.PG -> Rng.range(71, 76)
        Pos.SG -> Rng.range(74, 78)
        Pos.SF -> Rng.range(77, 80)
        Pos.PF -> Rng.range(79, 82)
        Pos.C -> Rng.range(81, 86)
    }

    fun weightFor(height: Int): Int = (height * 2.75 + Rng.range(-12, 28) - 3).roundToInt()

    fun heightLabel(inches: Int) = "${inches / 12}'${inches % 12}\""

    fun starsFor(rank: Int): Int = when {
        rank <= 25 -> 5
        rank <= 120 -> 4
        rank <= 400 -> 3
        else -> 2
    }

    /** NIL value (in thousands of dollars per year) a player expects. */
    fun expectedNil(p: Player): Int {
        val o = p.ovr
        var v = 8.0 + max(0, o - 58).let { it * it } * 1.7
        v *= 1 + (p.stars - 3) * 0.08
        if (p.has(Trait.BAG_CHASER)) v *= 1.25
        return (v / 5).roundToInt() * 5
    }

    /** Applies one training level-up to a player. Returns the attributes raised. */
    fun levelUp(p: Player, arch: Arch): List<Attr> {
        val raised = mutableListOf<Attr>()
        for (a in arch.boosts) {
            val i = a.ordinal
            if (p.attrs[i] < 99) {
                p.attrs[i] += 1
                p.seasonGains[i] += 1
                raised += a
            }
        }
        p.seasonLevels++
        p.careerLevels++
        return raised
    }

    fun xpNeeded() = 500

    /** Development speed multiplier from potential headroom and coaching. */
    fun devRate(p: Player, coachDev: Int): Double {
        val gap = (p.pot - p.ovr).toDouble()
        val potF = (gap / 10.0).coerceIn(0.12, 1.6)
        var r = (0.55 + coachDev / 100.0) * potF
        if (p.has(Trait.GYM_RAT)) r *= 1.25
        if (p.has(Trait.LATE_BLOOMER)) r *= 1.2
        return r
    }

    fun addXp(p: Player, amount: Int) {
        p.xp += amount
        while (p.xp >= xpNeeded()) {
            p.xp -= xpNeeded()
            if (p.focus == Arch.MANUAL) {
                p.banked++
                p.seasonLevels++
                p.careerLevels++
            } else levelUp(p, p.focus)
        }
    }

    /** Spend banked level-ups on an archetype. */
    fun spendBanked(p: Player, arch: Arch) {
        while (p.banked > 0) {
            p.banked--
            for (a in arch.boosts) {
                val i = a.ordinal
                if (p.attrs[i] < 99) { p.attrs[i] += 1; p.seasonGains[i] += 1 }
            }
        }
    }

    /** Off-season growth toward potential. Returns OVR change. */
    fun offseasonGrowth(p: Player, coachDev: Int): Int {
        val before = p.ovr
        val gap = p.pot - before
        var gain = if (gap > 0) gap * (0.28 + Rng.d() * 0.22) + Rng.gauss() * 1.2 else Rng.gauss() * 1.0
        gain *= 0.85 + coachDev / 330.0
        if (p.has(Trait.GYM_RAT)) gain *= 1.15
        if (p.has(Trait.LATE_BLOOMER)) gain *= 1.2
        // One attribute point moves the overall by roughly 0.14, so scale up.
        val steps = (gain * 7.3).roundToInt()
        val w = when (p.pos) {
            Pos.PG -> doubleArrayOf(1.0, 1.1, 1.2, 1.3, 1.3, .5, 1.0, .4, .8, .3)
            Pos.SG -> doubleArrayOf(1.1, 1.2, 1.4, 1.0, .8, .5, 1.1, .4, .9, .3)
            Pos.SF -> doubleArrayOf(1.2, 1.1, 1.1, .8, .8, 1.0, 1.1, .7, .7, .6)
            Pos.PF -> doubleArrayOf(1.4, .8, .7, .5, .6, 1.3, .7, 1.2, .5, 1.0)
            Pos.C -> doubleArrayOf(1.5, .6, .4, .4, .6, 1.5, .5, 1.4, .4, 1.3)
        }
        val idx = (0 until 10).toList()
        if (steps > 0) {
            repeat(steps) {
                val i = Rng.weighted(idx) { w[it] * (if (p.attrs[it] >= 99) 0.0 else 1.0) }
                p.attrs[i] = min(99, p.attrs[i] + 1)
            }
        } else if (steps < 0) {
            repeat(-steps) {
                val i = Rng.pick(idx)
                p.attrs[i] = max(20, p.attrs[i] - 1)
            }
        }
        return p.ovr - before
    }
}
