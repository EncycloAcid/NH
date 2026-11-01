package com.nh.hoopscoach.game

import kotlin.math.pow

/**
 * Future first-round picks as trade assets. Every team owns its next four first-rounders unless it
 * traded them; a traded pick goes to its owner on draft night. A pick is worth more the worse the
 * team that gave it up (a bad team's pick is a likely lottery pick) and less the further out it is.
 */
object Picks {
    fun key(p: PickAsset) = p.season * 100 + p.original

    fun label(lg: League, p: PickAsset) = "a ${p.season} first" + if (p.original != p.owner) " (via ${lg.team(p.original).abbr})" else ""

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

    fun owned(lg: League, t: Team): List<PickAsset> = lg.proState?.picks?.filter { it.owner == t.id }?.sortedBy { it.season } ?: emptyList()

    /** On the same scale as a player's trade value: a likely top pick is worth a good starter, a late one a bench player. */
    fun value(lg: League, p: PickAsset): Double {
        val t = lg.team(p.original)
        val worst = lg.teams.count { it.winPct > t.winPct }.toDouble() / (lg.teams.size - 1).coerceAtLeast(1)
        return (1.5 + 6.0 * worst.pow(1.5)) * 0.85.pow((p.season - nextDraft(lg)).coerceAtLeast(0))
    }

    fun move(lg: League, picks: List<PickAsset>, to: Team) { for (p in picks) p.owner = to.id }
}
