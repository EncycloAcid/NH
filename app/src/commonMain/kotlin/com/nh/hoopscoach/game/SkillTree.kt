package com.nh.hoopscoach.game

/**
 * Skill trees inside each archetype. A player's archetype has four skills to unlock with skill points:
 *  - two "Improving" skills (tier 1, one point each), one for each of the archetype's first two strengths;
 *  - an "Enhancing" skill for its third strength (tier 2, two points, after a tier-1 skill);
 *  - the archetype's Mastery (three points, after the other three): a lift to all three strengths.
 * A Sharpshooter's tree is Improving Finishing at the Rim, Improving Mid-Range Touch, Enhancing Deep Range,
 * Sharpshooter Mastery; a Floor General's has Improving Mid-Range Touch, Improving Handle, Enhancing Playmaking
 * Efficiency and Floor General Mastery.
 *
 * Players earn a skill point every season they play, and another for a big one; a new career starts each
 * returning player with a point for every year he's been around. Unlocking a skill raises those ratings for good.
 * Your players are yours to spend; the computer spends for everyone else at the end of the season.
 */
object SkillTree {
    class Skill(val id: String, val name: String, val tier: Int, val cost: Int, val raises: List<Pair<Attr, Int>>, val requires: List<String>) {
        val blurb: String get() = raises.joinToString(", ") { (a, n) -> "${a.label} +$n" }
    }

    /** What each rating's skill is called. */
    private fun skillName(a: Attr) = when (a) {
        Attr.INS -> "Finishing at the Rim"; Attr.MID -> "Mid-Range Touch"; Attr.OUT -> "Deep Range"
        Attr.HND -> "Handle"; Attr.PAS -> "Playmaking Efficiency"; Attr.REB -> "Glass Cleaning"
        Attr.PER -> "On-Ball Pressure"; Attr.IDF -> "Post Defense"; Attr.STL -> "Passing-Lane Instincts"; Attr.BLK -> "Rim Protection"
    }

    /** The four skills of [arch]'s tree (none for Manual Allocation). */
    fun tree(arch: Arch): List<Skill> {
        val b = arch.boosts
        if (b.size < 3) return emptyList()
        val k = arch.name.lowercase()
        val t1a = Skill("$k.1a", "Improving ${skillName(b[0])}", 1, 1, listOf(b[0] to 2), emptyList())
        val t1b = Skill("$k.1b", "Improving ${skillName(b[1])}", 1, 1, listOf(b[1] to 2), emptyList())
        val t2 = Skill("$k.2", "Enhancing ${skillName(b[2])}", 2, 2, listOf(b[2] to 2, b[0] to 1), listOf(t1a.id, t1b.id))
        val m = Skill("$k.m", "${arch.label} Mastery", 3, 3, b.map { it to 1 }, listOf(t1a.id, t1b.id, t2.id))
        return listOf(t1a, t1b, t2, m)
    }

    /** Tier 2 needs any tier-1 skill; Mastery needs all three. */
    fun unlockable(p: Player, s: Skill): Boolean = s.id !in p.skills && p.skillPts >= s.cost && when (s.tier) {
        1 -> true
        2 -> s.requires.any { it in p.skills }
        else -> s.requires.all { it in p.skills }
    }

    /** Why [s] is locked, in a few words ("" if it isn't). */
    fun lockedReason(p: Player, s: Skill): String = when {
        s.id in p.skills -> ""
        s.tier == 2 && s.requires.none { it in p.skills } -> "Needs an Improving skill"
        s.tier == 3 && !s.requires.all { it in p.skills } -> "Needs the other three"
        p.skillPts < s.cost -> "${s.cost} point${if (s.cost == 1) "" else "s"}"
        else -> ""
    }

    /** Unlocks [s] for [p]: spends the points and raises the ratings for good. */
    fun unlock(p: Player, s: Skill): String {
        if (!unlockable(p, s)) return lockedReason(p, s).ifEmpty { "Already unlocked." }
        p.skillPts -= s.cost
        p.skills += s.id
        for ((a, n) in s.raises) p.attrs[a.ordinal] = (p.attrs[a.ordinal] + n).coerceAtMost(99)
        if (p.pot < p.ovr) p.pot = p.ovr
        return "${s.name}: ${s.blurb}."
    }

    /** A returning player's starting points the first time anyone looks: a point for each year he's been around. */
    fun ensure(p: Player) {
        if (p.skillInit) return
        p.skillInit = true
        p.skillPts += if (p.year == PRO_YEAR) 2 else (p.year - 1).coerceIn(0, 3)
    }

    /** The end of a season: a point for everyone who played, two for a big season; the computer spends for its players. */
    fun seasonEnd(lg: League) {
        for (p in lg.players) {
            ensure(p)
            if (p.stats.gp <= 0) continue
            p.skillPts += 1 + if (p.stats.ppg >= 15) 1 else 0
            val mine = p.teamId == lg.userTeamId || p.id == lg.heroId
            if (!mine) autoSpend(p)
        }
    }

    /** The computer's choices: the cheapest open skill, in order. */
    fun autoSpend(p: Player) {
        val t = tree(p.style)
        while (true) {
            val next = t.firstOrNull { unlockable(p, it) } ?: return
            unlock(p, next)
        }
    }

    /** Skills he's unlocked from any tree (a player can change archetypes and keep what he learned). */
    fun unlocked(p: Player): List<Skill> = Arch.entries.flatMap { tree(it) }.filter { it.id in p.skills }
}
