package com.nh.hoopscoach.game

import kotlin.math.abs

/**
 * The pregame shootaround (a player career): ten shots on a timing meter on a game day. The green window is wider
 * the better he shoots from that spot (his ratings and badges). Every three makes are a badge point (up to three a
 * day, so it can't be farmed), and eight or better means he's feeling it: a little lift on his shots in today's game.
 */
object Shootaround {
    /** A spot on the floor: what it's called, and the shot (1 mid-range, 2 three). */
    class Spot(val name: String, val type: Int)

    val SPOTS = listOf(
        Spot("Left corner three", 2), Spot("Left elbow", 1), Spot("Left wing three", 2), Spot("Free-throw line", 1), Spot("Top of the key three", 2),
        Spot("Right elbow", 1), Spot("Right wing three", 2), Spot("Baseline jumper", 1), Spot("Right corner three", 2), Spot("Deep three", 2),
    )

    const val HOT_EDGE = 0.012

    /** The day's game, if he has one: shootaround is a game-day thing. */
    fun todaysGame(lg: League): Game? {
        val h = lg.hero ?: return null
        if (h.teamId < 0) return null
        return LeagueLists.games(lg).firstOrNull { !it.played && it.day == lg.day && it.involves(h.teamId) }
    }

    fun available(lg: League): Boolean {
        val r = lg.road ?: return false
        if (!lg.playerMode || r.ended || r.longWay || todaysGame(lg) == null || lg.hero?.out == true) return false
        return !(r.shootSeason == lg.season && r.shootDay == lg.day)
    }

    /** Half the green window's width (on a meter from 0 to 1), from his rating and badge at that spot. */
    fun window(lg: League, h: Player, type: Int): Double {
        val rating = if (type == 2) h[Attr.OUT] else h[Attr.MID]
        val badge = Badges.tier(lg, h, if (type == 2) Badge.SNIPER else Badge.PULL_UP)
        return (0.05 + (rating - 55) * 0.0018 + badge * 0.012).coerceIn(0.04, 0.17)
    }

    /** How fast the marker moves: a full sweep in this many milliseconds (a deep three is quicker). */
    fun sweepMs(type: Int, spot: Int) = if (spot == SPOTS.lastIndex) 650 else if (type == 2) 800 else 950

    /** Did it go in? Inside the window always; just outside, sometimes. */
    fun made(pos: Double, half: Double): Boolean {
        val d = abs(pos - 0.5)
        return d <= half || (d <= half * 1.6 && Rng.chance(0.35))
    }

    /** The session's done: badge points, today's hot hand, and his best. */
    fun finish(lg: League, threes: Int, mids: Int): String {
        val r = lg.road ?: return ""
        val h = lg.hero ?: return ""
        r.shootSeason = lg.season
        r.shootDay = lg.day
        val makes = threes + mids
        r.shootLast = makes
        r.shootBest = maxOf(r.shootBest, makes)
        // A badge point for every three makes, at most three a day.
        val pts = (makes / 3).coerceAtMost(3)
        val sniper = (pts * threes.toDouble() / makes.coerceAtLeast(1)).let { kotlin.math.round(it).toInt() }
        if (sniper > 0) h.badgeXp[Badge.SNIPER.name] = (h.badgeXp[Badge.SNIPER.name] ?: 0) + sniper
        if (pts - sniper > 0) h.badgeXp[Badge.PULL_UP.name] = (h.badgeXp[Badge.PULL_UP.name] ?: 0) + (pts - sniper)
        val hot = makes >= 8
        if (hot) { r.hotSeason = lg.season; r.hotDay = lg.day }
        return "$makes of 10" + (if (pts > 0) " · +$pts badge point${if (pts == 1) "" else "s"}" else "") + if (hot) " · feeling it tonight" else ""
    }

    /** The lift on his shots tonight if he shot 8 or better this morning. */
    fun edge(lg: League): Double {
        val r = lg.road ?: return 0.0
        return if (r.hotSeason == lg.season && r.hotDay == lg.day) HOT_EDGE else 0.0
    }
}
