package com.nh.hoopscoach.game

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Future first-round picks as trade assets. Every team owns its next four first-rounders unless it
 * traded them; a traded pick goes to its owner on draft night (unless top-X protection keeps it
 * with the original team). A pick is worth more the worse the team that gave it up (a bad team's
 * pick is a likely lottery pick) and less the further out it is or the heavier its protection.
 */
object Picks {
    val PROTECTIONS = listOf(0, 3, 5, 10, 14)

    fun key(p: PickAsset) = p.season * 100 + p.original

    fun protectionLabel(prot: Int): String = when (prot) {
        3 -> "Top-3 prot"
        5 -> "Top-5 prot"
        10 -> "Top-10 prot"
        14 -> "Lottery prot"
        else -> "Unprotected"
    }

    fun label(lg: League, p: PickAsset, protOverride: Int? = null): String {
        val effProt = protOverride ?: p.protection
        val via = if (p.original != p.owner) " (via ${lg.team(p.original).abbr})" else ""
        val prot = if (effProt > 0) " [${protectionLabel(effProt)}]" else ""
        return "a ${p.season} first$via$prot"
    }

    /** The next draft: this season's until it's been held, then next season's. */
    fun nextDraft(lg: League): Int {
        val ps = lg.proState ?: return lg.season
        return if (lg.phase == Phase.OFFSEASON && ps.step != ProStep.REVIEW) lg.season + 1 else lg.season
    }

    /** Past drafts drop off; every team gets its picks four years out. */
    fun ensure(lg: League) {
        val ps = lg.proState ?: return
        val first = nextDraft(lg)
        ps.picks.removeAll { it.season < first || lg.teams.none { t -> t.id == it.original } }
        for (t in lg.teams) for (y in first..first + 3) if (ps.picks.none { it.season == y && it.original == t.id }) ps.picks += PickAsset(y, t.id, t.id)
    }

    /** Who picks for [original] in [season]'s first round. */
    fun owner(lg: League, season: Int, original: Int): Int =
        lg.proState?.picks?.firstOrNull { it.season == season && it.original == original }?.owner ?: original

    /**
     * Resolves who actually uses [original]'s first-round pick at draft slot [slot] (1-indexed).
     * If the pick was traded with top-[protection] protection and [slot] lands inside that range,
     * the original team keeps the pick and the protection is cleared!
     */
    fun resolveDraftOwner(lg: League, season: Int, original: Int, slot: Int): Int {
        val pick = lg.proState?.picks?.firstOrNull { it.season == season && it.original == original } ?: return original
        if (pick.owner != original && pick.protection > 0 && slot <= pick.protection) {
            val holder = pick.owner
            val prot = pick.protection
            pick.owner = original
            pick.protection = 0
            // Roll over to next season's pick if original still owns it, otherwise it extinguishes into second-round value.
            val next = lg.proState?.picks?.firstOrNull { it.season == season + 1 && it.original == original && it.owner == original }
            if (next != null) {
                next.owner = holder
                next.protection = 0
                lg.news += News(lg.season, lg.day, "Pick protection: the ${lg.team(original).name} keep the No. $slot pick (top-$prot protected); ${lg.team(holder).name} receive ${season + 1} 1st instead", original == lg.userTeamId || holder == lg.userTeamId)
            } else {
                lg.news += News(lg.season, lg.day, "Pick protection: the ${lg.team(original).name} keep the No. $slot pick (top-$prot protected from the ${lg.team(holder).name})", original == lg.userTeamId || holder == lg.userTeamId)
            }
            return original
        }
        return pick.owner
    }

    fun owned(lg: League, t: Team): List<PickAsset> = lg.proState?.picks?.filter { it.owner == t.id }?.sortedBy { it.season } ?: emptyList()

    /**
     * On the same scale as a player's trade value: a likely top pick is worth a good starter, a late
     * one a bench player. Protections reduce the pick's value based on the chance the pick lands in
     * the protected range.
     */
    fun value(lg: League, p: PickAsset, protOverride: Int? = null): Double {
        val effProt = protOverride ?: p.protection
        val t = lg.team(p.original)
        val worst = lg.teams.count { it.winPct > t.winPct }.toDouble() / (lg.teams.size - 1).coerceAtLeast(1)
        val base = (1.5 + 6.0 * worst.pow(1.5)) * 0.85.pow((p.season - nextDraft(lg)).coerceAtLeast(0))
        val protFactor = when (effProt) {
            3 -> 1.0 - (0.28 * worst).coerceIn(0.05, 0.28)
            5 -> 1.0 - (0.38 * worst).coerceIn(0.08, 0.38)
            10 -> 1.0 - (0.50 * worst).coerceIn(0.12, 0.50)
            14 -> 1.0 - (0.62 * worst).coerceIn(0.18, 0.62)
            else -> 1.0
        }
        return base * protFactor
    }

    /** BLM 27-style explicit numeric trade value for UI bars and cards. */
    fun points(lg: League, p: PickAsset, protOverride: Int? = null): Int =
        (value(lg, p, protOverride) * 42.0).roundToInt().coerceAtLeast(15)

    fun move(lg: League, picks: List<PickAsset>, to: Team, protections: Map<Int, Int> = emptyMap()) {
        for (p in picks) {
            p.owner = to.id
            protections[key(p)]?.let { p.protection = it }
        }
    }
}
