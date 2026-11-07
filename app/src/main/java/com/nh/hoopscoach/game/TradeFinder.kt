package com.nh.hoopscoach.game

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/** A trade another team has offered the user: what it wants, what it gives, and why. */
@Serializable
class TradeOffer(
    val teamId: Int,
    val wants: List<Int>,
    val gives: List<Int>,
    val pickKeys: List<Int>,
    val day: Int,
    val note: String,
    val givePicks: List<Int> = emptyList(),
)

/**
 * The front office's trade tools when the user coaches an NBA team: shop one of his players to the
 * league, find a package for a player he wants, find deals matching his team needs or cap relief,
 * see what a deal does to both teams (their overall and projected wins), and field offers from the other 29 teams.
 */
object TradeFinder {
    /** A deal from the user's side: what he sends, what he gets, plus optional salary retention and pick protections. */
    class Deal(
        val other: Team,
        val give: List<Player>,
        val get: List<Player>,
        val givePicks: List<PickAsset>,
        val getPicks: List<PickAsset>,
        val retainGive: Map<Int, Int> = emptyMap(),
        val retainGet: Map<Int, Int> = emptyMap(),
        val protGive: Map<Int, Int> = emptyMap(),
        val protGet: Map<Int, Int> = emptyMap(),
    )

    /** What a deal does to a team: overall before and after (on the 2K scale shown, to a tenth), and wins over what's left of the season. */
    class Impact(val before: Double, val after: Double, val wins: Double, val payroll: Int, val status: String)

    /** The 2K-scale team overall, unrounded so a small change still shows. */
    private fun shown(v: Double): Double = (if (v >= 85) 77 + (v - 85) * 1.5 else 77 - (85 - v) * 1.2).coerceIn(40.0, 99.0)

    /** A roster's strength with [out] gone and [incoming] added (the same top-eight weighting as team strength). */
    private fun valueWith(lg: League, t: Team, out: Collection<Player>, incoming: Collection<Player>): Double {
        val ids = out.map { it.id }.toSet()
        val top = (lg.rosterOf(t).filter { it.id !in ids } + incoming).map { it.ovr }.sortedDescending().take(8)
        if (top.isEmpty()) return 85.0
        return top.mapIndexed { i, o -> o * (1.0 - i * 0.07) }.sum() / top.indices.sumOf { 1.0 - it * 0.07 }
    }

    /** Games still to play this season (a whole season in the summer). */
    private fun gamesLeft(lg: League, t: Team): Int =
        if (lg.phase == Phase.REGULAR) lg.games.count { !it.played && it.involves(t.id) && (it.type == GameType.CONF || it.type == GameType.NONCONF) }
        else ProLeague.seasonGames(lg)

    /** The team overall as the screens show it. */
    fun overall(lg: League, t: Team): Int = Ratings.shownPro(ProLeague.teamValue(lg, t).roundToInt())

    fun impact(
        lg: League,
        t: Team,
        out: List<Player>,
        incoming: List<Player>,
        retainOut: Map<Int, Int> = emptyMap(),
        retainIn: Map<Int, Int> = emptyMap(),
    ): Impact {
        val before = ProLeague.teamValue(lg, t)
        val after = valueWith(lg, t, out, incoming)
        // Strength points to wins: near .500 each point of strength is worth about 0.14% a game.
        val wins = (after - before) * ProLeague.ELO_PER_POINT * 0.00144 * gamesLeft(lg, t)
        val outSal = out.sumOf { Trades.effectiveSalary(lg, it, retainOut[it.id] ?: 0) }
        val inSal = incoming.sumOf { Trades.effectiveSalary(lg, it, retainIn[it.id] ?: 0) }
        val payroll = Payroll.total(lg, t) - outSal + inSal
        val status = when {
            payroll > Payroll.apron(lg) -> "over the apron"
            payroll > Payroll.tax(lg) -> "in the tax"
            payroll > Payroll.cap(lg) -> "over the cap"
            else -> "under the cap"
        }
        return Impact(Math.round(shown(before) * 10) / 10.0, Math.round(shown(after) * 10) / 10.0, wins, payroll, status)
    }

    fun mine(lg: League, d: Deal) = impact(lg, lg.user, d.give, d.get, d.retainGive, d.retainGet)
    fun theirs(lg: League, d: Deal) = impact(lg, d.other, d.get, d.give, d.retainGet, d.retainGive)

    private fun <T> combos(list: List<T>, max: Int): List<List<T>> {
        val out = mutableListOf<List<T>>()
        for (i in list.indices) {
            out += listOf(list[i])
            if (max >= 2) for (j in i + 1 until list.size) {
                out += listOf(list[i], list[j])
                if (max >= 3) for (k in j + 1 until list.size) out += listOf(list[i], list[j], list[k])
            }
        }
        return out
    }

    /**
     * Shop [p]: what each team would give for him. Every team's best accepted package (one or two of
     * its players, plus a future first if it's short), best for the user first.
     */
    fun shop(lg: League, p: Player, limit: Int = 8): List<Deal> {
        if (p.clause == "NTC") return emptyList()
        val u = lg.user
        val deals = mutableListOf<Deal>()
        for (t in lg.teams) {
            if (t.id == u.id) continue
            val untouch = Needs.untouchableIds(lg, t)
            val pool = lg.rosterOf(t).filter { it.id !in untouch && it.clause != "NTC" }
                .sortedByDescending { Trades.netValue(lg, it) }.take(10)
            val pick = Picks.owned(lg, t).firstOrNull()
            var best: Deal? = null
            var bestScore = -999.0
            for (c in combos(pool, 2)) for (picks in listOf(emptyList(), listOfNotNull(pick))) {
                if (picks.isEmpty() && c.isEmpty()) continue
                if (!NbaCoach.judge(lg, t, listOf(p), c, emptyList(), picks).ok) continue
                val score = c.sumOf { Trades.valueFor(lg, u, it) + Trades.contractDelta(lg, it) * 0.35 } + picks.sumOf { Picks.value(lg, it) }
                if (score > bestScore) { bestScore = score; best = Deal(t, listOf(p), c, emptyList(), picks) }
            }
            best?.let { deals += it }
        }
        return deals.sortedByDescending { d -> d.get.sumOf { Trades.valueFor(lg, u, it) + Trades.contractDelta(lg, it) * 0.35 } + d.getPicks.sumOf { Picks.value(lg, it) } }.take(limit)
    }

    /**
     * Find a way to get [q]: packages of one to three of the user's players (and his picks if needed)
     * that [q]'s team accepts. The ones that keep the user's team strongest come first.
     */
    fun target(lg: League, q: Player, limit: Int = 5): List<Deal> {
        val u = lg.user
        val t = lg.team(q.teamId)
        if (Needs.isUntouchable(lg, t, q) || q.clause == "NTC") return emptyList()
        val myUntouch = Needs.untouchableIds(lg, u)
        val mine = lg.rosterOf(u).filter { it.id !in myUntouch && it.clause != "NTC" }
            .sortedByDescending { (if (Needs.isOnBlock(lg, u, it)) 3.0 else 0.0) + Trades.value(it) }.take(13)
        val myPicks = Picks.owned(lg, u)
        val deals = mutableListOf<Deal>()
        for (c in combos(mine, 3)) for (picks in listOf(emptyList(), myPicks.take(1), myPicks.take(2))) {
            if (NbaCoach.judge(lg, t, c, listOf(q), picks, emptyList()).ok) deals += Deal(t, c, listOf(q), picks, emptyList())
        }
        // The best few that leave the user's team strongest, without near-duplicates.
        return deals.sortedWith(compareByDescending<Deal> { mine(lg, it).after }.thenBy { d -> d.give.sumOf { Trades.value(it) } + d.givePicks.size * 2.0 })
            .distinctBy { d -> d.give.firstOrNull()?.id }.take(limit)
    }

    /**
     * BLM 27 "Fits My Needs" Trade Finder: searches all 29 teams for players at the user's needed positions
     * (prioritizing players on other teams' Trade Blocks or Surplus lists) that can be acquired with the user's
     * Trade Block / Surplus / non-untouchable pieces.
     */
    fun matchingNeeds(lg: League, limit: Int = 8): List<Deal> {
        val u = lg.user
        val neededPos = Needs.needsOf(lg, u)
        val targets = lg.teams.filter { it.id != u.id }.flatMap { t ->
            val block = Needs.onBlockIds(lg, t)
            val untouch = Needs.untouchableIds(lg, t)
            lg.rosterOf(t).filter { p ->
                p.pos in neededPos && p.id !in untouch && p.clause != "NTC" && p.ovr >= 82
            }.map { p -> p to ((if (p.id in block) 8.0 else 0.0) + (if (Needs.isSurplus(lg, t, p)) 3.0 else 0.0) + Trades.netValue(lg, p)) }
        }.sortedByDescending { it.second }.take(18).map { it.first }
        val out = mutableListOf<Deal>()
        for (q in targets) {
            val d = target(lg, q, 2).firstOrNull() ?: continue
            if (out.none { it.get.firstOrNull()?.id == q.id }) out += d
            if (out.size >= limit) break
        }
        return out.sortedByDescending { mine(lg, it).after - mine(lg, it).before }
    }

    /**
     * BLM 27 "Cap Relief / Salary Dumps" Finder: finds deals that shed salary for the user (moving expensive
     * or negative-value contracts to teams with room or shorter deals, using picks or 25% salary retention if needed).
     */
    fun salaryDumps(lg: League, limit: Int = 6): List<Deal> {
        val u = lg.user
        val myUntouch = Needs.untouchableIds(lg, u)
        val candidates = lg.rosterOf(u).filter { it.id !in myUntouch && it.clause != "NTC" && Payroll.salaryOf(lg, it) >= 8_000 }
            .sortedBy { Trades.contractDelta(lg, it) - Payroll.salaryOf(lg, it) / 25_000.0 }
            .take(5)
        val myPicks = Picks.owned(lg, u)
        val deals = mutableListOf<Deal>()
        for (p in candidates) {
            val pSal = Payroll.salaryOf(lg, p)
            for (t in lg.teams) {
                if (t.id == u.id) continue
                val untouch = Needs.untouchableIds(lg, t)
                val cheaper = lg.rosterOf(t).filter {
                    it.id !in untouch && it.clause != "NTC" && Payroll.salaryOf(lg, it) <= pSal * 0.8
                }.sortedBy { Payroll.salaryOf(lg, it) }.take(8)
                for (q in cheaper) {
                    for (picks in listOf(emptyList(), myPicks.take(1))) {
                        for (retPct in listOf(0, 25)) {
                            if (retPct > 0 && !Payroll.canRetainMore(lg, u)) continue
                            val retMap = if (retPct > 0) mapOf(p.id to retPct) else emptyMap()
                            val saved = Trades.effectiveSalary(lg, p, retPct) - Payroll.salaryOf(lg, q)
                            if (saved < 2_500) continue
                            if (NbaCoach.judge(lg, t, listOf(p), listOf(q), picks, emptyList(), retainMine = retMap).ok) {
                                deals += Deal(t, listOf(p), listOf(q), picks, emptyList(), retainGive = retMap)
                                break
                            }
                        }
                        if (deals.any { it.other.id == t.id && it.give.first().id == p.id }) break
                    }
                }
            }
        }
        return deals.sortedBy { mine(lg, it).payroll }.distinctBy { it.give.first().id to it.other.id }.take(limit)
    }

    /**
     * Every week until the deadline (and in the summer): up to four teams call with offers for the
     * user's players. Teams prioritize players the user placed on the Trade Block, and never ask for Untouchables.
     */
    fun makeOffers(lg: League) {
        val ps = lg.proState ?: return
        if (!NbaCoach.canTrade(lg)) { ps.tradeOffers.clear(); return }
        val u = lg.user
        val untouch = Needs.untouchableIds(lg, u)
        val block = Needs.onBlockIds(lg, u)
        val onBlockPlayers = lg.rosterOf(u).filter { it.id in block && it.id !in untouch && it.clause != "NTC" }.shuffled(Rng.r)
        val otherPlayers = lg.rosterOf(u).filter {
            it.id !in block && it.id !in untouch && it.clause != "NTC" && Trades.value(it) >= 1.5 && Trades.tier(it) != "Franchise"
        }.shuffled(Rng.r)
        val wanted = (onBlockPlayers + otherPlayers).take(4)
        val offers = mutableListOf<TradeOffer>()
        for (p in wanted) {
            val d = shop(lg, p, 4).shuffled(Rng.r).firstOrNull() ?: continue
            if (offers.any { it.teamId == d.other.id }) continue
            val note = when {
                p.id in block -> "Saw ${p.last} on the trade block — the ${d.other.name} make an offer"
                d.other.w + d.other.l >= 10 && d.other.winPct >= 0.58 && p.age >= 27 -> "The ${d.other.name} want ${p.last} for a playoff run"
                d.other.w + d.other.l >= 10 && d.other.winPct < 0.42 && p.age <= 24 -> "The ${d.other.name} see ${p.last} as part of their future"
                else -> "The ${d.other.name} like ${p.last}'s fit"
            }
            offers += TradeOffer(
                d.other.id,
                d.give.map { it.id },
                d.get.map { it.id },
                d.getPicks.map { Picks.key(it) },
                lg.day,
                note,
                d.givePicks.map { Picks.key(it) },
            )
        }
        ps.tradeOffers.clear()
        ps.tradeOffers += offers
        if (offers.isNotEmpty()) lg.news += News(lg.season, lg.day, "${offers.size} ${if (offers.size == 1) "team calls" else "teams call"} the ${u.name} with trade offers", true)
    }

    /** An offer as a deal, if everyone involved is still where they were. */
    fun dealOf(lg: League, o: TradeOffer): Deal? {
        val t = lg.teams.getOrNull(o.teamId) ?: return null
        val give = o.wants.mapNotNull { lg.playerOrNull(it) }.filter { it.teamId == lg.userTeamId }
        val get = o.gives.mapNotNull { lg.playerOrNull(it) }.filter { it.teamId == t.id }
        if (give.size != o.wants.size || get.size != o.gives.size) return null
        val picks = lg.proState?.picks.orEmpty().filter { Picks.key(it) in o.pickKeys && it.owner == t.id }
        val myPicks = lg.proState?.picks.orEmpty().filter { Picks.key(it) in o.givePicks && it.owner == lg.userTeamId }
        return Deal(t, give, get, myPicks, picks)
    }
}
