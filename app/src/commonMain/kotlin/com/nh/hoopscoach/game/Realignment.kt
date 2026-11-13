package com.nh.hoopscoach.game

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/** A conference that wants the user's school. */
@Serializable
class ConfInvite(val confId: Int, val note: String)

/**
 * Conference realignment. A small school that outgrows its league gets invited up a level each
 * offseason (a mid-major to a power conference, a low-major to a mid-major; a true powerhouse in a
 * small league can jump straight to a power conference). The user decides on the Career step; the
 * computer's schools move up on their own now and then, and a league that shrinks too far brings in
 * the best school from a level below. Leagues top out at 18 (the schedule has room for 17 opponents).
 */
object Realignment {
    const val MAX_SIZE = 18
    private const val MIN_SIZE = 8

    private fun avg(lg: League, c: Conference) = c.teamIds.map { lg.team(it).prestige }.average()

    /** Where a league plays: the region most of its schools are in. */
    private fun regionOf(lg: League, c: Conference): String? =
        c.teamIds.mapNotNull { Geo.region(Geo.stateOf(lg.team(it))) }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key

    /** Recent success: an NCAA trip or a conference title in the last two seasons. */
    private fun winning(t: Team): Boolean = t.history.takeLast(2).any { it.result != "Missed" && Brand.roundOf(it.result) >= 1 } ||
        t.history.takeLast(2).any { it.cw >= it.cl + 6 }

    /** The leagues a level (or two) up that have room for [t], best fit first. */
    private fun targets(lg: League, t: Team): List<Conference> {
        val own = lg.conf(t.confId)
        if (own.tier == 0) return emptyList()
        val region = Geo.region(Geo.stateOf(t))
        return lg.confs.filter { c ->
            c.id != own.id && c.teamIds.size < MAX_SIZE &&
                (c.tier == own.tier - 1 || (own.tier == 2 && c.tier == 0 && t.prestige >= 72)) &&
                t.prestige >= avg(lg, c) - 6
        }.sortedByDescending { c -> (if (regionOf(lg, c) == region && region != null) 10.0 else 0.0) + avg(lg, c) / 10 + Rng.d() * 3 }
    }

    /** The offseason: invitations for the user's school, then the computer's moves. */
    fun offseason(lg: League, os: OffseasonState) {
        if (lg.pro) return
        os.confInvites.clear()
        if (!lg.playerMode) userInvites(lg, os)
        aiMoves(lg)
    }

    private fun userInvites(lg: League, os: OffseasonState) {
        val u = lg.user
        val own = lg.conf(u.confId)
        if (own.tier == 0) return
        // He has to have outgrown his league, and have won lately.
        if (u.prestige < avg(lg, own) + 10 || !winning(u)) return
        val round = Awards.tourneyRound(lg, u.id)
        val chance = (0.35 + (if (round >= 3) 0.25 else 0.0) + (if (round >= 5) 0.15 else 0.0)).coerceAtMost(0.85)
        if (!Rng.chance(chance)) return
        val offers = targets(lg, u).take(if (round >= 4) 2 else 1)
        for (c in offers) {
            os.confInvites += ConfInvite(c.id, when {
                c.tier == 0 -> "Wants a program that wins in March"
                regionOf(lg, c) == Geo.region(Geo.stateOf(u)) -> "A regional fit on a bigger stage"
                else -> "Wants a school on the rise"
            })
        }
        if (os.confInvites.isNotEmpty()) {
            lg.news += News(lg.season, lg.day, "${offers.first().name} invites ${u.school} to join", true, -1, "", "", "", u.id)
            Alerts.add(lg, "A conference invitation", "${os.confInvites.joinToString(" and ") { lg.conf(it.confId).name }} ${if (os.confInvites.size == 1) "wants" else "want"} ${u.school}. " +
                "Decide on the Career decision step of the offseason.", "")
        }
    }

    /** Now and then a strong school moves up on its own. */
    private fun aiMoves(lg: League) {
        val movers = lg.teams.filter { t ->
            t.id != lg.userTeamId && lg.conf(t.confId).tier >= 1 && t.prestige >= avg(lg, lg.conf(t.confId)) + 15 && winning(t)
        }.sortedByDescending { it.prestige }
        var moves = 0
        for (t in movers) {
            if (moves >= 2 || !Rng.chance(0.2)) continue
            val c = targets(lg, t).firstOrNull() ?: continue
            join(lg, t.id, c.id)
            moves++
        }
    }

    /** [teamId] moves to [confId]; a league left too small invites the best school from a level below. */
    fun join(lg: League, teamId: Int, confId: Int) {
        val t = lg.team(teamId)
        val old = lg.conf(t.confId)
        val new = lg.conf(confId)
        if (old.id == new.id || new.teamIds.size >= MAX_SIZE) return
        old.teamIds.remove(t.id)
        new.teamIds += t.id
        t.confId = new.id
        t.buzz = (t.buzz + 12).coerceAtMost(100.0)
        lg.news += News(lg.season, lg.day, "${t.school} leaves the ${old.name} for the ${new.name}", t.id == lg.userTeamId, -1, "", "", "", t.id)
        if (old.teamIds.size < MIN_SIZE) {
            val region = regionOf(lg, old)
            lg.teams.filter { lg.conf(it.confId).tier == old.tier + 1 && it.id != lg.userTeamId && lg.conf(it.confId).teamIds.size > MIN_SIZE }
                .maxByOrNull { it.prestige + (if (Geo.region(Geo.stateOf(it)) == region && region != null) 15 else 0) }
                ?.let { b ->
                    val from = lg.conf(b.confId)
                    from.teamIds.remove(b.id)
                    old.teamIds += b.id
                    b.confId = old.id
                    b.buzz = (b.buzz + 8).coerceAtMost(100.0)
                    lg.news += News(lg.season, lg.day, "${b.school} moves up to the ${old.name}, filling ${t.school}'s spot", false, -1, "", "", "", b.id)
                }
        }
        lg.reindex()
    }

    /** What a move would change for the user's school: exposure, revenue share, NIL budget. */
    fun effects(lg: League, t: Team, c: Conference): List<Pair<String, String>> {
        val ownExp = Brand.exposure(lg, t.confId)
        val newExp = Brand.exposure(lg, c.id)
        val ownRev = Finance.revShare(lg, t)
        val newRev = when (c.tier) { 0 -> 4000; 1 -> 1200; else -> 350 }
        return listOf(
            "Exposure" to "${Brand.exposureLabel(ownExp)} ($ownExp) → ${Brand.exposureLabel(newExp)} ($newExp)",
            "Revenue share" to "${money(ownRev)} → ${money(newRev)} a year toward NIL",
            "League strength" to "Average prestige ${avg(lg, lg.conf(t.confId)).roundToInt()} → ${avg(lg, c).roundToInt()}: tougher nights, more quality wins",
            "Members" to "${c.teamIds.size} schools now, ${c.teamIds.size + 1} with you",
        )
    }

    private fun money(k: Int) = if (k >= 1000) "$" + "%.1fM".format(k / 1000.0) else "$${k}K"
}
