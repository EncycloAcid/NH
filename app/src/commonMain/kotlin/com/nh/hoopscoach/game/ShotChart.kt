package com.nh.hoopscoach.game

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Where shots come from. Every shot gets a spot on the half court that fits it (layups and dunks at the
 * rim, floaters and hooks in the paint, jumpers in the midrange, threes in the corners or above the
 * break). The user's games keep every spot for the box score's shot chart, and every player keeps his
 * season's makes and tries by zone for his hot zones.
 *
 * The court is in feet: x from -25 (left sideline) to 25, y from the baseline (0) out to half court
 * (47); the rim is at (0, 5.25).
 */
object ShotChart {
    const val RIM_Y = 5.25
    val ZONES = listOf("At the rim", "Paint", "Midrange", "Corner 3", "Above the break 3")

    /** A spot for a shot of [type] (0 inside, 1 mid, 2 three), as (x, y) in feet. */
    fun spot(type: Int, what: String): Pair<Double, Double> = when {
        type == 2 && Rng.chance(0.27) -> {
            val side = if (Rng.chance(0.5)) -1 else 1
            side * (22.4 + Rng.d() * 1.6) to 1.0 + Rng.d() * 11.0
        }
        type == 2 -> polar(23.9 + Rng.d() * 3.5, 0.38 + Rng.d() * (PI - 0.76))
        type == 1 -> polar(9.0 + Rng.d() * 12.0, 0.1 + Rng.d() * (PI - 0.2)).let { (x, y) -> x.coerceIn(-21.5, 21.5) to y }
        what == "floater" || what == "hook shot" -> polar(4.5 + Rng.d() * 6.5, 0.4 + Rng.d() * (PI - 0.8))
        else -> polar(Rng.d() * 3.4, 0.2 + Rng.d() * (PI - 0.4))
    }

    private fun polar(r: Double, a: Double) = r * cos(a) to (RIM_Y + r * sin(a)).coerceAtLeast(0.6)

    /** Which of the five zones a spot is in. */
    fun zone(type: Int, x: Double, y: Double): Int = when {
        type == 2 -> if (y < 14 && kotlin.math.abs(x) > 21) 3 else 4
        hypot(x, y - RIM_Y) < 4.0 -> 0
        kotlin.math.abs(x) <= 8 && y <= 19 -> 1
        else -> 2
    }

    /** One shot packed into a Long: player id, side, spot (in half-feet) and made. */
    fun pack(pid: Int, side: Int, x: Double, y: Double, made: Boolean, type: Int): Long {
        val xi = ((x + 25) * 2).roundToInt().coerceIn(0, 127)
        val yi = (y * 2).roundToInt().coerceIn(0, 127)
        return (pid.toLong() shl 20) or (side.toLong() shl 19) or (xi.toLong() shl 12) or (yi.toLong() shl 5) or ((if (made) 1L else 0L) shl 4) or type.toLong()
    }

    class Shot(val pid: Int, val side: Int, val x: Double, val y: Double, val made: Boolean, val type: Int)

    fun unpack(v: Long) = Shot(
        (v ushr 20).toInt(), ((v ushr 19) and 1).toInt(),
        ((v ushr 12) and 127) / 2.0 - 25, ((v ushr 5) and 127) / 2.0, ((v ushr 4) and 1L) == 1L, (v and 15).toInt(),
    )

    /** Counts a shot in [p]'s season zones. */
    fun tally(lg: League, p: Player, zone: Int, made: Boolean) {
        if (p.zoneSeason != lg.season) { p.zoneA = IntArray(5); p.zoneM = IntArray(5); p.zoneSeason = lg.season }
        p.zoneA[zone]++
        if (made) p.zoneM[zone]++
    }

    /** League-wide make rate by zone, for coloring hot and cold zones. */
    fun leagueRates(lg: League): DoubleArray {
        val a = IntArray(5); val m = IntArray(5)
        for (p in lg.players) if (p.zoneSeason == lg.season) for (i in 0..4) { a[i] += p.zoneA[i]; m[i] += p.zoneM[i] }
        val base = doubleArrayOf(0.64, 0.42, 0.40, 0.38, 0.35)
        return DoubleArray(5) { if (a[it] >= 200) m[it].toDouble() / a[it] else base[it] }
    }
}
