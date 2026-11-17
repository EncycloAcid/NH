package com.nh.hoopscoach.game

import java.util.WeakHashMap
import kotlin.math.roundToInt

/**
 * A badge: something a player is known for, with a real effect in games. [xp] is what the user's own player
 * has to do in games to reach each tier ([unit]: made threes, rebounds...), [step] the effect per tier.
 */
enum class Badge(
    val label: String, val group: String, val does: String, val attr: Attr, val second: Attr?,
    val xp: IntArray, val unit: String, val step: Double,
) {
    SNIPER("Sniper", "Shooting", "Knocks down threes", Attr.OUT, null, intArrayOf(30, 100, 220, 450), "threes made", 0.006),
    PULL_UP("Pull-Up Artist", "Shooting", "Lives in the mid-range", Attr.MID, null, intArrayOf(30, 100, 220, 450), "mid-range makes", 0.006),
    ICE_WATER("Ice Water", "Shooting", "Makes the big shot late", Attr.MID, Attr.OUT, intArrayOf(12, 40, 100, 220), "clutch points", 0.012),
    RIM_WRECKER("Rim Wrecker", "Finishing", "Finishes at the rim", Attr.INS, null, intArrayOf(40, 140, 320, 650), "baskets inside", 0.005),
    AND_ONE("And-One", "Finishing", "Scores through contact", Attr.INS, Attr.HND, intArrayOf(40, 140, 320, 650), "free throws made", 0.05),
    FLOOR_GENERAL("Floor General", "Playmaking", "Makes everyone better", Attr.PAS, null, intArrayOf(50, 170, 380, 750), "assists", 0.0015),
    TIGHT_HANDLES("Tight Handles", "Playmaking", "Doesn't give it away", Attr.HND, null, intArrayOf(40, 140, 320, 650), "clean possessions", 0.007),
    LOCKDOWN("Lockdown", "Defense", "Smothers his man's jumper", Attr.PER, null, intArrayOf(30, 100, 230, 480), "stops", 0.008),
    RIM_PROTECTOR("Rim Protector", "Defense", "Owns the paint", Attr.IDF, Attr.BLK, intArrayOf(35, 120, 270, 550), "rim stops", 0.006),
    BALL_HAWK("Ball Hawk", "Defense", "Jumps passing lanes", Attr.STL, null, intArrayOf(25, 90, 200, 420), "steals", 0.003),
    GLASS_CLEANER("Glass Cleaner", "Rebounding", "Wins the boards", Attr.REB, null, intArrayOf(70, 240, 520, 1000), "rebounds", 0.3);

    /** "+1.4% on threes" at [tier]. */
    fun effectAt(tier: Int): String {
        val v = fmt(step * tier * 100)
        return when (this) {
            SNIPER -> "+$v% on threes"
            PULL_UP -> "+$v% on mid-range jumpers"
            ICE_WATER -> "+$v% on shots late in close games"
            RIM_WRECKER -> "+$v% at the rim"
            AND_ONE -> "+$v% on shots while fouled"
            FLOOR_GENERAL -> "+$v% on teammates' shots"
            TIGHT_HANDLES -> "-$v% turnover chance"
            LOCKDOWN -> "-$v% on his man's jump shots"
            RIM_PROTECTOR -> "-$v% on shots at the rim"
            BALL_HAWK -> "+$v% turnovers forced"
            GLASS_CLEANER -> "+$v% of the rebounds"
        }
    }

    private fun fmt(v: Double) = if (v == v.roundToInt().toDouble()) "${v.roundToInt()}" else String.format(java.util.Locale.US, "%.1f", v)
}

/**
 * Player badges, 2K style: Bronze, Silver, Gold and Legend.
 *
 * Every player's badges come from where his ratings rank in his league (a top-25% three-point shooter is a
 * Bronze Sniper, the top 1% a Legend). The user's own player in a player career earns his: each tier needs the
 * reps (badge points from his box scores: made threes for Sniper, rebounds for Glass Cleaner) and the rating to
 * back it up (one tier above where his rating ranks). Badges carry from college to the pros; the bar gets
 * higher there. A son starts with a head start on his father's best badges ([LegacyMode]).
 */
object Badges {
    val TIERS = listOf("", "Bronze", "Silver", "Gold", "Legend")

    /** Rating percentiles for each tier among the league's rotation players. */
    private val CUTS = doubleArrayOf(0.75, 0.88, 0.96, 0.99)

    private class Cuts(val key: Int, val by: Array<IntArray>)
    private val cache = WeakHashMap<League, Cuts>()

    private fun rating(p: Player, b: Badge): Int = if (b.second == null) p[b.attr] else (p[b.attr] * 0.65 + p[b.second] * 0.35).roundToInt()

    /** The ratings each tier takes this season, per badge (from each team's top nine). */
    private fun cuts(lg: League): Array<IntArray> {
        val key = lg.season * 4 + (if (lg.pro) 1 else 0) + lg.teams.size * 8
        synchronized(cache) { cache[lg]?.takeIf { it.key == key }?.let { return it.by } }
        val pool = LeagueLists.teams(lg).flatMap { t -> t.roster.mapNotNull { lg.playerOrNull(it) }.sortedByDescending { it.ovr }.take(9) }
        val by = Badge.entries.map { b ->
            val v = pool.map { rating(it, b) }.sorted()
            if (v.isEmpty()) intArrayOf(99, 99, 99, 99) else IntArray(4) { i -> v[((v.size - 1) * CUTS[i]).roundToInt()] }
        }.toTypedArray()
        synchronized(cache) { cache[lg] = Cuts(key, by) }
        return by
    }

    /** The tier his rating alone reaches (0 to 4). */
    fun ratingTier(lg: League, p: Player, b: Badge): Int {
        val c = cuts(lg)[b.ordinal]
        val r = rating(p, b)
        return c.count { r >= it }
    }

    /** The rating [p] needs for [tier] of [b]. */
    fun ratingFor(lg: League, b: Badge, tier: Int): Int = cuts(lg)[b.ordinal][(tier - 1).coerceIn(0, 3)]

    fun xpTier(p: Player, b: Badge): Int { val x = p.badgeXp[b.name] ?: 0; return b.xp.count { x >= it } }

    /** The user's player earns his badges; everyone else has the ones his ratings say. */
    fun earns(lg: League, p: Player) = lg.playerMode && p.id == lg.heroId

    /** Off for balance checks only. */
    @Volatile var off = false

    fun tier(lg: League, p: Player, b: Badge): Int = if (off) 0 else
        if (earns(lg, p)) minOf(xpTier(p, b), ratingTier(lg, p, b) + 1) else ratingTier(lg, p, b)

    /** Every badge's tier for [p], by [Badge] ordinal. */
    fun tiers(lg: League, p: Player): IntArray = IntArray(Badge.entries.size) { tier(lg, p, Badge.entries[it]) }

    /** His badges, best first. */
    fun of(lg: League, p: Player): List<Pair<Badge, Int>> = Badge.entries.map { it to tier(lg, p, it) }.filter { it.second > 0 }.sortedByDescending { it.second }

    /** What holds the user's player back from the next tier: the reps, the rating, or nothing (he's a Legend). */
    fun next(lg: League, p: Player, b: Badge): String {
        val t = tier(lg, p, b)
        if (t >= 4) return "Maxed out"
        val x = p.badgeXp[b.name] ?: 0
        val needXp = b.xp[t]
        val needRating = if (t >= 1) ratingFor(lg, b, t) else 0
        val ratingShort = t >= 1 && rating(p, b) < needRating
        return when {
            x < needXp && ratingShort -> "$x/$needXp ${b.unit}, and ${b.attr.label} $needRating, for ${TIERS[t + 1]}"
            x < needXp -> "$x/$needXp ${b.unit} for ${TIERS[t + 1]}"
            ratingShort -> "Needs ${b.attr.label} ${needRating}${if (b.second != null) " (with ${b.second.label})" else ""} for ${TIERS[t + 1]}"
            else -> ""
        }
    }

    /**
     * After a game: the user's player's badge points from his box score ([makes]: baskets inside, mid-range and
     * threes), and an alert for each tier he reaches.
     */
    fun afterGame(lg: League, p: Player, s: StatLine, makes: IntArray) {
        if (!earns(lg, p) || s.sec <= 0) return
        val before = tiers(lg, p)
        val min = s.sec / 60
        fun add(b: Badge, n: Int) { if (n > 0) p.badgeXp[b.name] = (p.badgeXp[b.name] ?: 0) + n }
        add(Badge.SNIPER, makes.getOrElse(2) { s.tpm })
        add(Badge.PULL_UP, makes.getOrElse(1) { 0 })
        add(Badge.ICE_WATER, s.clutch)
        add(Badge.RIM_WRECKER, makes.getOrElse(0) { 0 })
        add(Badge.AND_ONE, s.ftm)
        add(Badge.FLOOR_GENERAL, s.ast)
        add(Badge.TIGHT_HANDLES, if (min >= 12) (3 - s.tov).coerceAtLeast(0) + s.ast / 3 else 0)
        add(Badge.LOCKDOWN, s.stl + if (min >= 20) 1 else 0)
        add(Badge.RIM_PROTECTOR, s.blk * 2 + s.drb / 4)
        add(Badge.BALL_HAWK, s.stl * 2)
        add(Badge.GLASS_CLEANER, s.reb)
        val after = tiers(lg, p)
        for (b in Badge.entries) {
            val t = after[b.ordinal]
            if (t <= before[b.ordinal]) continue
            Alerts.add(lg, "Badge earned: ${b.label}", "${TIERS[t]} · ${b.effectAt(t)}. ${b.does}.", "badge")
            if (t >= 3) lg.news += News(lg.season, lg.day, "${p.name} earns a ${TIERS[t]} ${b.label} badge", true, kind = "badge", pid = p.id, teamId = p.teamId)
        }
    }

    /**
     * A head start on [b]: 60% of the badge points [tier] takes (a son on his father's badges). A Gold father's son
     * starts at Silver, a Legend's at Gold, a Bronze's most of the way there; his ratings still cap it.
     */
    fun headStart(p: Player, b: Badge, tier: Int) {
        if (tier <= 0) return
        val x = (b.xp[(tier - 1).coerceIn(0, 3)] * 0.6).roundToInt()
        p.badgeXp[b.name] = maxOf(p.badgeXp[b.name] ?: 0, x)
    }
}
