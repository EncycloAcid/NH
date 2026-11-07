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

    /** Second positions a player at [pos] can have: the spots next to his. */
    fun secondOptions(pos: Pos): List<Pos> = when (pos) {
        Pos.PG -> listOf(Pos.SG); Pos.SG -> listOf(Pos.PG, Pos.SF); Pos.SF -> listOf(Pos.SG, Pos.PF); Pos.PF -> listOf(Pos.SF, Pos.C); Pos.C -> listOf(Pos.PF)
    }

    /**
     * How much better than usual his rating at the next spot has to be for him to play it. Each position's
     * overall weighs ratings differently (a power forward almost always rates higher as a center), so the
     * bar is set per pair, about a third of each position qualifying.
     */
    private val SECOND_BAR = mapOf(
        (Pos.PG to Pos.SG) to -2, (Pos.SG to Pos.PG) to 1, (Pos.SG to Pos.SF) to -1, (Pos.SF to Pos.SG) to 2,
        (Pos.SF to Pos.PF) to 0, (Pos.PF to Pos.SF) to -2, (Pos.PF to Pos.C) to 3, (Pos.C to Pos.PF) to -3,
    )

    /** A second position when his ratings fit it better than most at his position, else null. */
    fun secondFor(pos: Pos, a: IntArray): Pos? {
        val mine = ovr(pos, a)
        return secondOptions(pos).map { it to ovr(it, a) - mine - (SECOND_BAR[pos to it] ?: 0) }.filter { it.second >= 0 }.maxByOrNull { it.second }?.first
    }

    /** Play styles for a player: his position's, and his second position's too. */
    fun stylesFor(p: Player): List<Arch> = styles(p.pos) + (p.pos2?.let { styles(it) } ?: emptyList())

    fun styles(pos: Pos): List<Arch> = when (pos) {
        // Every archetype belongs to one position.
        Pos.PG -> listOf(Arch.FLOOR_GENERAL, Arch.PLAYMAKER, Arch.TABLE_SETTER, Arch.BALL_HAWK, Arch.PEST, Arch.LOGO_SHOOTER,
            Arch.SCORING_POINT, Arch.SPEEDSTER, Arch.PNR_MAESTRO, Arch.POINT_OF_ATTACK,
            Arch.JUMBO_PLAYMAKER, Arch.PULL_UP_ARTIST, Arch.TWO_WAY_POINT, Arch.CRAFTY_FINISHER)
        Pos.SG -> listOf(Arch.SHARPSHOOTER, Arch.COMBO_GUARD, Arch.SHOT_CREATOR, Arch.TWO_WAY_GUARD, Arch.MICROWAVE, Arch.MOVEMENT_SHOOTER, Arch.BULLY_GUARD,
            Arch.SPOT_UP, Arch.MIDRANGE_MAESTRO, Arch.DOWNHILL_GUARD, Arch.SPARK_PLUG,
            Arch.SECONDARY_CREATOR, Arch.PERIMETER_STOPPER, Arch.CRASH_GUARD, Arch.OFF_BALL_CUTTER)
        Pos.SF -> listOf(Arch.SLASHER, Arch.THREE_AND_D, Arch.LOCKDOWN, Arch.POINT_FORWARD, Arch.GLUE_GUY, Arch.HIGH_FLYER, Arch.ISO_WING,
            Arch.THREE_LEVEL, Arch.POST_WING, Arch.CORNER_SPECIALIST, Arch.TWO_WAY_WING,
            Arch.SHOT_MAKER, Arch.CONNECTOR, Arch.RANGY_DEFENDER, Arch.TRANSITION_WING)
        Pos.PF -> listOf(Arch.STRETCH, Arch.REBOUNDER, Arch.FACE_UP_FOUR, Arch.SWITCHABLE_BIG, Arch.PICK_AND_POP, Arch.HUSTLE_BIG, Arch.ATHLETIC_FOUR,
            Arch.LOW_POST_FOUR, Arch.PLAYMAKING_FOUR, Arch.DUNKER_SPOT, Arch.DEFENSIVE_FOUR,
            Arch.STRETCH_REBOUNDER, Arch.TWO_WAY_FOUR, Arch.SHORT_ROLL, Arch.BRUISER)
        Pos.C -> listOf(Arch.POST_SCORER, Arch.RIM_PROTECTOR, Arch.RIM_RUNNER, Arch.POINT_CENTER, Arch.STRETCH_FIVE, Arch.ENFORCER, Arch.UNICORN,
            Arch.GLASS_CLEANER, Arch.ELBOW_HUB, Arch.MOBILE_BIG, Arch.PAINT_BEAST,
            Arch.LOB_THREAT, Arch.SPACING_FIVE, Arch.POST_PLAYMAKER, Arch.SHOT_BLOCKER)
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

    /**
     * One point of growth for rating [i], if it takes. Up to 90 it always does; above that each point
     * is harder (60% at 93, 30% at 96, 10% at 98), so 99s are rare and earned.
     */
    fun raise(p: Player, i: Int): Boolean {
        val a = p.attrs[i]
        if (a >= 99) return false
        if (a >= 90 && !Rng.chance((99 - a) / 10.0)) return false
        p.attrs[i] = a + 1
        p.seasonGains[i] += 1
        // He grew past his ceiling: the ceiling was wrong, not the growth.
        if (p.pot < p.ovr) p.pot = p.ovr
        return true
    }

    /** Applies one training level-up to a player. Returns the attributes raised. */
    fun levelUp(p: Player, arch: Arch): List<Attr> {
        val raised = mutableListOf<Attr>()
        for (a in arch.boosts) {
            if (raise(p, a.ordinal)) raised += a
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
            for (a in arch.boosts) raise(p, a.ordinal)
        }
    }

    /** Off-season growth toward potential. Returns OVR change. */
    /**
     * A pro rating as the screens show it, on a 2K-style scale: 90 and up is about the league's top
     * 20, a typical starter about 77, a rookie about 70. The game works on its own internal scale.
     */
    fun shownPro(v: Int): Int = (if (v >= 85) 77 + (v - 85) * 1.5 else 77 - (85 - v) * 1.2).roundToInt().coerceIn(40, 99)

    /** The other way: a 2K-style rating on the game's internal pro scale. */
    fun proInternal(k: Int): Int = (if (k >= 77) 85 + (k - 77) / 1.5 else 85 - (77 - k) / 1.2).roundToInt().coerceIn(60, 99)

    fun offseasonGrowth(p: Player, coachDev: Int, share: Double? = null): Int {
        val before = p.ovr
        val gap = p.pot - before
        // [share]: how much of the gap to his ceiling he closes (the user's player earns it; see Earned).
        var gain = if (gap > 0) gap * (share ?: (0.28 + Rng.d() * 0.22)) + Rng.gauss() * (if (share != null) 0.7 else 1.2) else Rng.gauss() * 1.0
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
                if (raise(p, i)) p.seasonGains[i] -= 1
            }
        } else if (steps < 0) {
            repeat(-steps) {
                val i = Rng.pick(idx)
                p.attrs[i] = max(20, p.attrs[i] - 1)
            }
        }
        if (p.pot < p.ovr) p.pot = p.ovr
        return p.ovr - before
    }
}
