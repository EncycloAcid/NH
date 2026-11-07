package com.nh.hoopscoach.game

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/** A trade another team has offered the user: what it wants, what it gives, and why. */
@Serializable
class TradeOffer(val teamId: Int, val wants: List<Int>, val gives: List<Int>, val pickKeys: List<Int>, val day: Int, val note: String)

/**
 * The front office's trade tools when the user coaches an NBA team: shop one of his players to the
 * league, find a package for a player he wants, see what a deal does to both teams (their overall and
 * their projected wins), and field offers the other 29 teams make every week.
 */
object TradeFinder {
    /** A deal from the user's side: what he sends, what he gets. */
    class Deal(val other: Team, val give: List<Player>, val get: List<Player>, val givePicks: List<PickAsset>, val getPicks: List<PickAsset>)

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

    fun impact(lg: League, t: Team, out: List<Player>, incoming: List<Player>): Impact {
        val before = ProLeague.teamValue(lg, t)
        val after = valueWith(lg, t, out, incoming)
        // Strength points to wins: near .500 each point of strength is worth about 0.14% a game.
        val wins = (after - before) * ProLeague.ELO_PER_POINT * 0.00144 * gamesLeft(lg, t)
        val payroll = Payroll.total(lg, t) - out.sumOf { Payroll.salaryOf(lg, it) } + incoming.sumOf { Payroll.salaryOf(lg, it) }
        val status = when {
            payroll > Payroll.apron(lg) -> "over the apron"
            payroll > Payroll.tax(lg) -> "in the tax"
            payroll > Payroll.cap(lg) -> "over the cap"
            else -> "under the cap"
        }
        return Impact(Math.round(shown(before) * 10) / 10.0, Math.round(shown(after) * 10) / 10.0, wins, payroll, status)
    }

    fun mine(lg: League, d: Deal) = impact(lg, lg.user, d.give, d.get)
    fun theirs(lg: League, d: Deal) = impact(lg, d.other, d.get, d.give)

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
        val u = lg.user
        val deals = mutableListOf<Deal>()
        for (t in lg.teams) {
            if (t.id == u.id) continue
            val pool = lg.rosterOf(t).sortedByDescending { Trades.value(it) }.take(10)
            val pick = Picks.owned(lg, t).firstOrNull()
            var best: Deal? = null
            var bestScore = 0.0
            for (c in combos(pool, 2)) for (picks in listOf(emptyList(), listOfNotNull(pick))) {
                if (picks.isEmpty() && c.isEmpty()) continue
                if (!NbaCoach.judge(lg, t, listOf(p), c, emptyList(), picks).ok) continue
                val score = c.sumOf { Trades.valueFor(lg, u, it) } + picks.sumOf { Picks.value(lg, it) }
                if (score > bestScore) { bestScore = score; best = Deal(t, listOf(p), c, emptyList(), picks) }
            }
            best?.let { deals += it }
        }
        return deals.sortedByDescending { d -> d.get.sumOf { Trades.valueFor(lg, u, it) } + d.getPicks.sumOf { Picks.value(lg, it) } }.take(limit)
    }

    /**
     * Find a way to get [q]: packages of one to three of the user's players (and his picks if needed)
     * that [q]'s team accepts. The ones that keep the user's team strongest come first.
     */
    fun target(lg: League, q: Player, limit: Int = 5): List<Deal> {
        val u = lg.user
        val t = lg.team(q.teamId)
        val mine = lg.rosterOf(u).sortedByDescending { Trades.value(it) }.take(13)
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
     * Every week until the deadline (and in the summer): up to three teams call with offers for the
     * user's players. Contenders chase veterans who help now, rebuilders chase youth.
     */
    fun makeOffers(lg: League) {
        val ps = lg.proState ?: return
        if (!NbaCoach.canTrade(lg)) { ps.tradeOffers.clear(); return }
        val u = lg.user
        val wanted = lg.rosterOf(u).filter { Trades.value(it) >= 1.5 && Trades.tier(it) != "Franchise" }.shuffled(Rng.r).take(3)
        val offers = mutableListOf<TradeOffer>()
        for (p in wanted) {
            val d = shop(lg, p, 4).shuffled(Rng.r).firstOrNull() ?: continue
            if (offers.any { it.teamId == d.other.id }) continue
            val note = when {
                d.other.w + d.other.l >= 10 && d.other.winPct >= 0.58 && p.age >= 27 -> "The ${d.other.name} want ${p.last} for a playoff run"
                d.other.w + d.other.l >= 10 && d.other.winPct < 0.42 && p.age <= 24 -> "The ${d.other.name} see ${p.last} as part of their future"
                else -> "The ${d.other.name} like ${p.last}'s fit"
            }
            offers += TradeOffer(d.other.id, d.give.map { it.id }, d.get.map { it.id }, d.getPicks.map { Picks.key(it) }, lg.day, note)
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
        return Deal(t, give, get, emptyList(), picks)
    }
}
