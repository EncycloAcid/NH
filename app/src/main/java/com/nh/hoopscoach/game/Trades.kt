package com.nh.hoopscoach.game

import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * Pro trades: the user's player can ask out and pick from the teams that want him, he can be dealt
 * at the deadline, and AI teams swap players at the deadline and over the summer.
 */
object Trades {
    /** No trades after day 40 of the 59-day regular season. */
    const val DEADLINE = 40

    // ---------- value ----------

    /** What a player is worth in a trade: overall counts most and climbs steeply; youth and upside add, age takes away. */
    fun value(p: Player): Double {
        val age = when { p.age <= 22 -> 1.25; p.age <= 25 -> 1.12; p.age <= 28 -> 1.0; p.age <= 31 -> 0.8; p.age <= 33 -> 0.6; else -> 0.4 }
        val upside = if (p.age <= 24) (p.pot - p.ovr).coerceAtLeast(0) * 0.15 else 0.0
        return exp((p.ovr - 82) / 3.5 + upside) * age
    }

    /** Up to three players from [t] worth [lo] to [hi] times [target], best first. Null if nothing fits. */
    private fun bundle(lg: League, t: Team, target: Double, lo: Double, hi: Double, keep: Set<Int>): List<Player>? {
        val pool = lg.rosterOf(t).filter { it.id != lg.heroId && it.id !in keep && it.id !in (lg.proState?.moved ?: emptyList<Int>()) }
            .sortedByDescending { value(it) }
        val out = mutableListOf<Player>()
        var sum = 0.0
        for (p in pool) {
            val v = value(p)
            if (sum + v > hi * target) continue
            out += p
            sum += v
            if (sum >= lo * target || out.size == 3) break
        }
        return if (out.isNotEmpty() && sum >= lo * target) out else null
    }

    // ---------- doing a trade ----------

    private fun move(lg: League, p: Player, from: Team, to: Team) {
        from.roster.remove(p.id)
        p.teamId = to.id
        if (to.roster.any { lg.player(it).number == p.number }) p.number = Recruiting.freeNumber(lg, to)
        to.roster += p.id
    }

    /** Swaps [fromA] (to [b]) for [fromB] (to [a]) and fixes up both rosters. */
    fun execute(lg: League, a: Team, b: Team, fromA: List<Player>, fromB: List<Player>, offseason: Boolean = lg.phase == Phase.OFFSEASON): Trade {
        val ps = lg.proState!!
        val hero = fromA.any { it.id == lg.heroId } || fromB.any { it.id == lg.heroId }
        for (p in fromA) move(lg, p, a, b)
        for (p in fromB) move(lg, p, b, a)
        for (t in listOf(a, b)) {
            ProLeague.trim(lg, t)
            while (t.roster.size < ProLeague.ROSTER) ProLeague.makePro(lg, Rng.pick(Pos.entries), Rng.range(84, 88), Rng.range(26, 33), t)
            Gameplan.autoAllocate(lg, t)
        }
        ps.moved += (fromA + fromB).map { it.id }
        val trade = Trade(lg.season, lg.day, offseason, a.id, b.id, fromA.map { it.name }, fromB.map { it.name }, hero)
        ps.trades += trade
        if (ps.trades.size > 200) ps.trades.removeAt(0)
        lg.reindex()
        return trade
    }

    fun describe(lg: League, t: Trade): String =
        "The ${lg.team(t.teamA).name} send ${t.toB.joinToString(" and ")} to the ${lg.team(t.teamB).name} for ${t.toA.joinToString(" and ")}"

    // ---------- the user's player ----------

    /** Which window a trade request belongs to: each season, and each offseason, allows one. */
    private fun window(lg: League) = lg.season * 2 + if (lg.phase == Phase.OFFSEASON) 1 else 0

    /** He can ask out before the deadline, or in the offseason around the draft and free agency, once per window, while under contract. */
    fun canRequest(lg: League): Boolean {
        val r = lg.road ?: return false
        if (!lg.pro || r.ended || (r.contract?.years ?: 0) <= 0 || r.tradeKey == window(lg) || r.suitors.isNotEmpty()) return false
        if (lg.playerOrNull(lg.heroId)?.teamId?.let { it < 0 } != false) return false
        return when (lg.phase) {
            Phase.REGULAR -> lg.day < DEADLINE
            Phase.OFFSEASON -> lg.proState?.step == ProStep.DRAFT || lg.proState?.step == ProStep.FREE_AGENCY
            else -> false
        }
    }

    /** Teams that would trade for him, contenders first. [lo] and [hi] bound what they'd give up. */
    fun suitors(lg: League, count: Int, lo: Double, hi: Double): List<Suitor> {
        val h = lg.playerOrNull(lg.heroId) ?: return emptyList()
        val target = value(h)
        val out = mutableListOf<Suitor>()
        for (t in lg.teams.filter { it.id != h.teamId }.shuffled(Rng.r)) {
            // A team keeps its best player unless it's sending him for someone better.
            val best = lg.rosterOf(t).maxByOrNull { value(it) }
            val keep = if (best != null && value(best) < target * 0.9) emptySet() else setOfNotNull(best?.id)
            val pack = bundle(lg, t, target, lo, hi, keep) ?: continue
            val ahead = lg.rosterOf(t).count { it.pos == h.pos && it.ovr >= h.ovr && pack.none { p -> p.id == it.id } }
            out += Suitor(t.id, pack.map { it.id }, ProCareer.roleFor(ahead), ahead)
            if (out.size >= count * 2) break
        }
        return out.sortedByDescending { lg.team(it.teamId).winPct + lg.team(it.teamId).elo / 10_000.0 }.take(count)
    }

    /** He asks out: his team's fans sour, and his agent brings back the teams that want him. Returns how many. */
    fun request(lg: League): Int {
        val r = lg.road ?: return 0
        val h = lg.playerOrNull(lg.heroId) ?: return 0
        if (!canRequest(lg)) return 0
        val t = lg.team(h.teamId)
        r.tradeKey = window(lg)
        r.suitors.clear()
        r.suitors += suitors(lg, 3, 0.5, 1.25)
        t.fans = (t.fans - 6).coerceAtLeast(5.0)
        r.persona = (r.persona + 1).coerceAtMost(10)
        lg.news += News(lg.season, lg.day,
            if (r.suitors.isEmpty()) "${h.name} asks the ${t.name} for a trade. No team bites."
            else "${h.name} asks the ${t.name} for a trade. ${r.suitors.size} teams call.", true)
        return r.suitors.size
    }

    /** He picks where he goes. */
    fun accept(lg: League, i: Int): Boolean {
        val r = lg.road ?: return false
        val s = r.suitors.getOrNull(i) ?: return false
        return dealHero(lg, s, asked = true)
    }

    /** He takes the request back; the fans come around a little. */
    fun withdraw(lg: League) {
        val r = lg.road ?: return
        val h = lg.playerOrNull(lg.heroId) ?: return
        if (r.suitors.isEmpty()) return
        r.suitors.clear()
        val t = lg.team(h.teamId)
        t.fans = (t.fans + 3).coerceAtMost(100.0)
        lg.news += News(lg.season, lg.day, "${h.name} pulls his trade request and commits to the ${t.name}", true)
    }

    /** Sends him to [s]'s team for the players it offered. */
    fun dealHero(lg: League, s: Suitor, asked: Boolean): Boolean {
        val r = lg.road ?: return false
        val h = lg.playerOrNull(lg.heroId) ?: return false
        val from = lg.team(h.teamId)
        val to = lg.team(s.teamId)
        val back = s.sends.mapNotNull { lg.playerOrNull(it) }.filter { it.teamId == to.id }
        if (back.isEmpty() || to.id == from.id) { r.suitors.clear(); return false }
        execute(lg, from, to, listOf(h), back)
        r.suitors.clear()
        r.contract = r.contract?.let { Contract(to.id, it.salary, it.years, it.kind, it.signed, it.option) }
        if (r.proTeams.lastOrNull() != to.id) r.proTeams += to.id
        r.tradedSeason = lg.season
        r.tradedDay = lg.day
        r.tradeNote = (if (asked) "You got your wish: " else "You've been traded: ") +
            "the ${from.name} send you to the ${to.name} for ${back.joinToString(" and ") { it.name }}."
        Road.sync(lg)
        lg.news += News(lg.season, lg.day, "Trade: ${h.name} to the ${to.name}; ${back.joinToString(" and ") { it.name }} to the ${from.name}", true)
        return true
    }

    /** Deadline day: will his team move him? Rarely as a rookie or a young star; more often on a losing team, older, or behind a better player. */
    fun dealChance(lg: League): Double {
        val h = lg.playerOrNull(lg.heroId) ?: return 0.0
        if (h.proYears == 0) return 0.0
        val t = lg.team(h.teamId)
        val rank = lg.rosterOf(t).sortedByDescending { it.ovr }.indexOfFirst { it.id == h.id } + 1
        var c = 0.03
        if (t.winPct < 0.4 && h.age >= 27) c += 0.15
        if (rank >= 3) c += 0.06
        if (h.ovr >= 96 && h.age < 30) c *= 0.4
        return c.coerceAtMost(0.3)
    }

    // ---------- the AI's trades ----------

    /** Sellers move a good veteran to a contender for younger pieces. */
    fun aiTrades(lg: League, count: Int) {
        val ranked = lg.teams.sortedBy { it.winPct + it.elo / 10_000.0 }
        val sellers = ranked.take(12)
        val buyers = ranked.takeLast(12)
        var made = 0
        var tries = 0
        while (made < count && tries++ < count * 6) {
            val seller = Rng.pick(sellers)
            val buyer = Rng.pick(buyers)
            if (seller.id == buyer.id) continue
            val vet = lg.rosterOf(seller).filter {
                it.id != lg.heroId && it.age >= 28 && it.ovr >= 87 && it.id !in lg.proState!!.moved
            }.maxByOrNull { it.ovr } ?: continue
            val target = value(vet)
            val keep = lg.rosterOf(buyer).sortedByDescending { value(it) }.take(2).map { it.id }.toSet()
            val pack = bundle(lg, buyer, target, 0.75, 1.3, keep) ?: continue
            val trade = execute(lg, seller, buyer, listOf(vet), pack)
            if (seller.id == lg.userTeamId || buyer.id == lg.userTeamId) lg.news += News(lg.season, lg.day, "Trade: " + describe(lg, trade), true)
            made++
        }
    }

    /** The deadline: AI teams deal, then his own team decides on him (an open request gets settled too). */
    fun deadline(lg: League) {
        val r = lg.road ?: return
        aiTrades(lg, Rng.range(4, 8))
        if (r.suitors.isNotEmpty()) {
            dealHero(lg, r.suitors.first(), asked = true)
            return
        }
        if (Rng.chance(dealChance(lg))) {
            suitors(lg, 3, 0.7, 1.3).maxByOrNull { s -> s.sends.sumOf { lg.playerOrNull(it)?.let { p -> value(p) } ?: 0.0 } }
                ?.let { dealHero(lg, it, asked = false) }
        }
        lg.news += News(lg.season, lg.day, "The trade deadline passes", false)
    }

    /** The summer: more AI deals, and a request still open when the season starts gets settled. */
    fun summer(lg: League) = aiTrades(lg, Rng.range(6, 10))

    fun settleOpenRequest(lg: League) {
        val r = lg.road ?: return
        r.suitors.firstOrNull()?.let { dealHero(lg, it, asked = true) }
    }

    // ---------- how he feels ----------

    class Mood(val score: Int, val label: String, val reasons: List<String>)

    /** How happy he is where he is: winning, his minutes for his place on the team, and, from 30, a shot at a ring. */
    fun mood(lg: League): Mood {
        val h = lg.playerOrNull(lg.heroId) ?: return Mood(70, "Content", emptyList())
        val t = lg.team(h.teamId)
        var s = 72.0
        val why = mutableListOf<String>()
        if (t.gp >= 8) {
            if (t.winPct < 0.4) { s -= (0.4 - t.winPct) * 120 + 8; why += "Losing: ${t.w}-${t.l}" }
            else if (t.winPct >= 0.6) { s += 10; why += "Winning: ${t.w}-${t.l}" }
        }
        val rank = lg.rosterOf(t).sortedByDescending { it.ovr }.indexOfFirst { it.id == h.id } + 1
        val mins = (Gameplan.plannedMinutes(t, h.id) * 1.2).roundToInt()
        val want = when (rank) { 1 -> 34; 2 -> 32; 3 -> 30; else -> 24 }
        if (mins < want - 4) { s -= (want - mins) * 2.0; why += "Minutes: $mins a game; you want $want" }
        else if (rank <= 2 && mins >= want) why += "You're a focal point"
        if (h.age >= 30 && t.gp >= 8 && t.winPct < 0.55) { s -= 12; why += "At ${h.age}, you want a ring" }
        if ((lg.road?.persona ?: 0) >= 5) { s -= 5; why += "Lightning rods get restless" }
        val v = s.roundToInt().coerceIn(0, 100)
        val label = when { v >= 70 -> "Happy"; v >= 50 -> "Content"; v >= 30 -> "Frustrated"; else -> "Wants out" }
        return Mood(v, label, why)
    }
}
