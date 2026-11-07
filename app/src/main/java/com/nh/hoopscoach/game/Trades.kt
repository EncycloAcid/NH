package com.nh.hoopscoach.game

import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * Pro trades: the user's player can ask out and pick from the teams that want him, he can be dealt
 * at the deadline, and AI teams swap players at the deadline and over the summer.
 */
object Trades {
    /** No trades after the deadline, about two-thirds of the way through the regular season. */
    fun deadlineDay(lg: League) = lg.proState?.deadline ?: 40

    // ---------- value ----------

    /** What a player is worth in a trade: overall counts most and climbs steeply; youth and upside add, age takes away. */
    /**
     * What a player is worth in a trade: his overall (steeply: one star is worth several starters),
     * his age (prime years count most, value falls fast after 30), his upside while young, and what
     * he's producing this season against what his rating predicts.
     */
    fun value(p: Player): Double {
        val age = when { p.age <= 21 -> 1.3; p.age <= 23 -> 1.2; p.age <= 25 -> 1.1; p.age <= 28 -> 1.0; p.age <= 30 -> 0.85; p.age <= 32 -> 0.68; p.age <= 34 -> 0.5; else -> 0.35 }
        val upside = if (p.age <= 25) (p.pot - p.ovr).coerceAtLeast(0) * (if (p.age <= 22) 0.15 else 0.11) else 0.0
        val s = p.stats
        val prod = if (s.gp >= 15 && s.mpg >= 10) {
            val per36 = (s.ppg + 1.1 * s.rpg + 1.4 * s.apg + 1.8 * (s.spg + s.bpg) - s.per(s.tov)) * 36.0 / s.mpg
            (per36 - (15 + (p.ovr - 85) * 0.55)).coerceIn(-6.0, 8.0) * 0.04
        } else 0.0
        return exp((p.ovr - 82) / 3.5 + upside + prod) * age
    }

    /** Effective salary of [p] after [retainPct] (0..50%) is retained by the sending team. */
    fun effectiveSalary(lg: League, p: Player, retainPct: Int = 0): Int {
        val sal = Payroll.salaryOf(lg, p)
        val pct = retainPct.coerceIn(0, 50)
        return ((sal * (100 - pct)) / 100.0).roundToInt()
    }

    /**
     * How much [p]'s contract helps or hurts his trade value (in trade-value units).
     * A bargain/rookie deal or retained salary adds value; an overpaid multi-year contract subtracts
     * value and can push a declining veteran's net trade value negative (red bar in BLM 27).
     */
    fun contractDelta(lg: League, p: Player, retainPct: Int = 0): Double {
        val eff = effectiveSalary(lg, p, retainPct)
        val fair = ProCareer.marketValue(lg, p).coerceAtLeast(ProCareer.minSalary(lg, p.proYears))
        val cap = Payroll.cap(lg).toDouble().coerceAtLeast(10_000.0)
        val diffRatio = (fair - eff) / cap // positive if bargain, negative if overpaid
        val yrsFactor = (0.7 + p.contractYears.coerceIn(1, 4) * 0.22)
        return when {
            diffRatio >= 0.02 -> (diffRatio * 14.0 * yrsFactor).coerceAtMost(5.5)
            diffRatio <= -0.02 -> (diffRatio * 18.0 * yrsFactor).coerceAtLeast(-7.5)
            else -> 0.0
        }
    }

    /** Net trade value of [p] including his contract and any [retainPct] salary retention. Can be negative for bad contracts. */
    fun netValue(lg: League, p: Player, retainPct: Int = 0): Double =
        value(p) + contractDelta(lg, p, retainPct)

    /** BLM 27-style explicit numeric trade value (can be negative for albatross contracts). */
    fun points(lg: League, p: Player, retainPct: Int = 0): Int =
        (netValue(lg, p, retainPct) * 42.0).roundToInt()

    /** BLM 27-style numeric trade value from [t]'s perspective (incorporating direction, positional need, and contract). */
    fun pointsFor(lg: League, t: Team, p: Player, retainPct: Int = 0): Int =
        ((valueFor(lg, t, p) * Needs.needFactor(lg, t, p) + contractDelta(lg, p, retainPct)) * 42.0).roundToInt()

    /** A rebuilding seller wants a first-round pick back: the buyer's furthest one, if it has one. */
    fun sweetener(lg: League, seller: Team, buyer: Team): List<PickAsset> =
        if (seller.w + seller.l >= 10 && seller.winPct < 0.45) listOfNotNull(Picks.owned(lg, buyer).lastOrNull()) else emptyList()

    /** His trade value in a word: Franchise, All-Star, Starter, Rotation, Bench, or Negative (bad contract). */
    fun tier(p: Player): String {
        val v = value(p)
        return when { v >= 40 -> "Franchise"; v >= 12 -> "All-Star"; v >= 4 -> "Starter"; v >= 1.5 -> "Rotation"; else -> "Bench" }
    }

    /** How [t] sees him: a rebuilding team pays more for youth and less for veterans; a contender the other way. */
    fun valueFor(lg: League, t: Team, p: Player): Double {
        val v = value(p)
        val rebuilding = t.w + t.l >= 10 && t.winPct < 0.42
        val contending = t.w + t.l >= 10 && t.winPct >= 0.58
        return v * when {
            rebuilding && p.age <= 24 -> 1.2
            rebuilding && p.age >= 30 -> 0.75
            contending && p.age >= 27 && p.age <= 33 -> 1.15
            contending && p.age <= 22 -> 0.9
            else -> 1.0
        }
    }

    /** A deal in a word, by what each side gives up: "Fair", "They're overpaying", "Lowball". */
    fun verdict(lg: League, sends: List<Player>, gets: Player, picks: List<PickAsset> = emptyList()): String {
        val give = sends.sumOf { value(it) } + picks.sumOf { Picks.value(lg, it) }
        val r = give / value(gets).coerceAtLeast(0.01)
        return when { r >= 1.15 -> "They're overpaying"; r >= 0.9 -> "Fair deal"; r >= 0.75 -> "A little light"; else -> "Lowball" }
    }

    /** Up to three players from [t] worth [lo] to [hi] times [target], best first. Null if nothing fits. */
    /** What [t] could send for a player worth [target]: up to three of its players (never the user's). */
    fun bundleFor(lg: League, t: Team, target: Double, lo: Double, hi: Double) = bundle(lg, t, target, lo, hi, emptySet())

    private fun bundle(lg: League, t: Team, target: Double, lo: Double, hi: Double, keep: Set<Int>): List<Player>? {
        val untouch = Needs.untouchableIds(lg, t)
        val pool = lg.rosterOf(t).filter { it.id != lg.heroId && it.id !in keep && it.id !in untouch && it.clause != "NTC" && it.id !in (lg.proState?.moved ?: emptyList<Int>()) }
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
        if (p.id == lg.heroId && from.id != to.id) Leadership.newTeam(lg)
        from.roster.remove(p.id)
        p.teamId = to.id
        if (to.roster.any { lg.player(it).number == p.number }) p.number = Recruiting.freeNumber(lg, to)
        to.roster += p.id
        lg.proState?.onTheBlock?.get(from.id)?.remove(p.id)
        lg.proState?.untouchables?.get(from.id)?.remove(p.id)
    }

    /** Swaps [fromA] (to [b]) for [fromB] (to [a]), applies any salary retention & pick protections, and fixes up both rosters. */
    fun execute(
        lg: League, a: Team, b: Team, fromA: List<Player>, fromB: List<Player>, offseason: Boolean = lg.phase == Phase.OFFSEASON,
        picksA: List<PickAsset> = emptyList(), picksB: List<PickAsset> = emptyList(),
        retainA: Map<Int, Int> = emptyMap(), retainB: Map<Int, Int> = emptyMap(),
        protA: Map<Int, Int> = emptyMap(), protB: Map<Int, Int> = emptyMap(),
    ): Trade {
        val ps = lg.proState!!
        val hero = fromA.any { it.id == lg.heroId } || fromB.any { it.id == lg.heroId }
        if (hero) {
            val to = if (fromA.any { it.id == lg.heroId }) b else a
            Alerts.add(lg, "You've been traded", "You're headed to the ${to.name}. New team, new role: check the Me tab.", "trade")
        }
        val beforeA = ProLeague.teamValue(lg, a)
        val beforeB = ProLeague.teamValue(lg, b)
        for (p in fromA) {
            val pct = retainA[p.id]?.coerceIn(0, 50) ?: 0
            if (pct > 0 && Payroll.canRetainMore(lg, a)) {
                val kept = ((p.salary * pct) / 100.0).roundToInt() / 10 * 10
                if (kept > 0) {
                    Payroll.addRetained(lg, a, p, kept, p.contractYears.coerceAtLeast(1), "Retained")
                    p.salary = (p.salary - kept).coerceAtLeast(ProCareer.minSalary(lg, p.proYears))
                }
            }
            move(lg, p, a, b)
        }
        for (p in fromB) {
            val pct = retainB[p.id]?.coerceIn(0, 50) ?: 0
            if (pct > 0 && Payroll.canRetainMore(lg, b)) {
                val kept = ((p.salary * pct) / 100.0).roundToInt() / 10 * 10
                if (kept > 0) {
                    Payroll.addRetained(lg, b, p, kept, p.contractYears.coerceAtLeast(1), "Retained")
                    p.salary = (p.salary - kept).coerceAtLeast(ProCareer.minSalary(lg, p.proYears))
                }
            }
            move(lg, p, b, a)
        }
        for (t in listOf(a, b)) {
            ProLeague.trim(lg, t)
            while (t.roster.size < ProLeague.ROSTER) ProLeague.makePro(lg, Rng.pick(Pos.entries), Rng.range(84, 88), Rng.range(26, 33), t)
            Gameplan.autoAllocate(lg, t)
        }
        ps.moved += (fromA + fromB).map { it.id }
        // The trade changes how good each team is (on top of how it has played).
        a.elo += (ProLeague.teamValue(lg, a) - beforeA) * ProLeague.ELO_PER_POINT
        b.elo += (ProLeague.teamValue(lg, b) - beforeB) * ProLeague.ELO_PER_POINT
        // Picks change hands too, with any agreed protections.
        Picks.move(lg, picksA, b, protA)
        Picks.move(lg, picksB, a, protB)
        val pickNamesA = picksA.map { Picks.label(lg, it) }
        val pickNamesB = picksB.map { Picks.label(lg, it) }
        val namesA = fromA.map { p -> val r = retainA[p.id] ?: 0; if (r > 0) "${p.name} ($r% ret.)" else p.name } + pickNamesA
        val namesB = fromB.map { p -> val r = retainB[p.id] ?: 0; if (r > 0) "${p.name} ($r% ret.)" else p.name } + pickNamesB
        val trade = Trade(lg.season, lg.day, offseason, a.id, b.id, namesA, namesB, hero)
        ps.trades += trade
        NewsDesk.trade(lg, trade, a, b, fromA, fromB, pickNamesA, pickNamesB)
        Rumors.breaking(lg, trade)
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
            Phase.REGULAR -> lg.day < deadlineDay(lg)
            Phase.OFFSEASON -> lg.proState?.step == ProStep.DRAFT || lg.proState?.step == ProStep.FREE_AGENCY
            else -> false
        }
    }

    /** Teams that would trade for him, contenders first. [lo] and [hi] bound what they'd give up. */
    fun suitors(lg: League, count: Int, lo: Double, hi: Double): List<Suitor> {
        val h = lg.playerOrNull(lg.heroId) ?: return emptyList()
        val out = mutableListOf<Suitor>()
        for (t in lg.teams.filter { it.id != h.teamId }.shuffled(Rng.r)) {
            // Each team values him its own way (rebuilders want youth, contenders want help now).
            val target = valueFor(lg, t, h)
            // A team keeps its best player unless it's sending him for someone better.
            val best = lg.rosterOf(t).maxByOrNull { value(it) }
            val keep = if (best != null && value(best) < target * 0.9) emptySet() else setOfNotNull(best?.id)
            val pack = bundle(lg, t, target, lo, hi, keep) ?: continue
            // Over the cap, the salaries have to match.
            if (!Payroll.tradeWorks(lg, t, Payroll.salaryOf(lg, h), pack.sumOf { Payroll.salaryOf(lg, it) })) continue
            val ahead = lg.rosterOf(t).count { it.pos == h.pos && it.ovr >= h.ovr && pack.none { p -> p.id == it.id } }
            // Short on players? They add a first-round pick.
            val sweet = if (pack.sumOf { value(it) } < target * 0.95) listOfNotNull(Picks.owned(lg, t).firstOrNull()) else emptyList()
            out += Suitor(t.id, pack.map { it.id }, ProCareer.roleFor(ahead), ahead, sweet.map { Picks.key(it) })
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
        val picks = lg.proState?.picks?.filter { Picks.key(it) in s.pickKeys && it.owner == to.id } ?: emptyList()
        execute(lg, from, to, listOf(h), back, picksB = picks)
        r.suitors.clear()
        r.contract = r.contract?.let { Contract(to.id, it.salary, it.years, it.kind, it.signed, it.option) }
        if (r.proTeams.lastOrNull() != to.id) r.proTeams += to.id
        r.tradedSeason = lg.season
        r.tradedDay = lg.day
        r.tradeNote = (if (asked) "You got your wish: " else "You've been traded: ") +
            "the ${from.name} send you to the ${to.name} for ${back.joinToString(" and ") { it.name }}."
        Road.sync(lg)
        // The trade's write-up comes from execute().
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

    /** A player's worth to a specific team: base value, then up or down for whether he fills its hole at his spot. */
    fun neededValue(lg: League, t: Team, p: Player): Double = valueFor(lg, t, p) * Needs.needFactor(lg, t, p)

    /**
     * Would both sides take the deal, each valuing the players its own way? A motivated seller takes a
     * little less (dumping a contract or cashing out a veteran); everyone else wants to win the swap
     * or break about even, the way Basketball Legacy Manager scores it: a small loss is fine, a big
     * one is not.
     */
    private fun fair(lg: League, a: Team, b: Team, fromA: List<Player>, fromB: List<Player>, picksA: List<PickAsset>, picksB: List<PickAsset>, aMotivated: Boolean, bMotivated: Boolean): Boolean {
        if (fromA.isEmpty() && picksA.isEmpty() || fromB.isEmpty() && picksB.isEmpty()) return false
        val aGets = fromB.sumOf { neededValue(lg, a, it) } + picksB.sumOf { Picks.value(lg, it) }
        val aGives = fromA.sumOf { neededValue(lg, a, it) } + picksA.sumOf { Picks.value(lg, it) }
        val bGets = fromA.sumOf { neededValue(lg, b, it) } + picksA.sumOf { Picks.value(lg, it) }
        val bGives = fromB.sumOf { neededValue(lg, b, it) } + picksB.sumOf { Picks.value(lg, it) }
        val aNeed = if (aMotivated) 0.82 else 0.93
        val bNeed = if (bMotivated) 0.82 else 0.93
        return aGets >= aGives * aNeed && bGets >= bGives * bNeed
    }

    /**
     * Balance a deal the way a GM would: the light side adds its cheapest spare pick, then a
     * low-value spare part, until it's close enough or nothing sensible is left. True when it lands.
     */
    private fun equalize(lg: League, a: Team, b: Team, fromA: MutableList<Player>, fromB: MutableList<Player>, picksA: MutableList<PickAsset>, picksB: MutableList<PickAsset>, aMotivated: Boolean, bMotivated: Boolean): Boolean {
        val moved = lg.proState?.moved.orEmpty().toSet()
        var tries = 0
        while (tries++ < 4 && !fair(lg, a, b, fromA, fromB, picksA, picksB, aMotivated, bMotivated)) {
            val aGets = fromB.sumOf { neededValue(lg, a, it) } + picksB.sumOf { Picks.value(lg, it) }
            val aGives = fromA.sumOf { neededValue(lg, a, it) } + picksA.sumOf { Picks.value(lg, it) }
            val bGets = fromA.sumOf { neededValue(lg, b, it) } + picksA.sumOf { Picks.value(lg, it) }
            val bGives = fromB.sumOf { neededValue(lg, b, it) } + picksB.sumOf { Picks.value(lg, it) }
            val aShort = aGets < aGives * (if (aMotivated) 0.82 else 0.93)
            val bShort = bGets < bGives * (if (bMotivated) 0.82 else 0.93)
            if (aShort == bShort) return fair(lg, a, b, fromA, fromB, picksA, picksB, aMotivated, bMotivated)
            // The side getting the worse end owes value; it pays from its bench or its picks.
            val payer = if (aShort) a else b
            val sends = if (aShort) fromA else fromB
            val sendPicks = if (aShort) picksA else picksB
            val pick = Picks.owned(lg, payer).filter { it !in sendPicks }.minByOrNull { Picks.value(lg, it) }
            val spare = lg.rosterOf(payer).filter { it.id != lg.heroId && it.id !in moved && it !in sends && Needs.isSurplus(lg, payer, it) }
                .sortedBy { value(it) }.firstOrNull { value(it) <= 6.0 }
            val deficit = if (aShort) aGives - aGets else bGives - bGets
            when {
                spare != null && value(spare) <= deficit * 1.4 -> sends += spare
                pick != null && (spare == null || Picks.value(lg, pick) <= deficit * 1.5) -> sendPicks += pick
                spare != null -> sends += spare
                else -> return false
            }
        }
        return fair(lg, a, b, fromA, fromB, picksA, picksB, aMotivated, bMotivated)
    }

    /** Everyone the engine may move: nobody on the user's bench when he coaches, never his player, nobody already dealt. */
    private fun tradeable(lg: League, t: Team, ps: com.nh.hoopscoach.game.ProState): List<Player> {
        val userCoached = NbaCoach.active(lg) && t.id == lg.userTeamId
        return lg.rosterOf(t).filter { it.id != lg.heroId && it.id != (lg.road?.rivalId ?: -2) && it.id !in ps.moved && !userCoached }
    }

    /**
     * Contenders shop for the piece they're missing: the best player another team will give up at
     * their weakest spot, bought with surplus players and picks. The league's live trades, made one
     * phone call at a time until [count] land or the lines go quiet.
     */
    fun aiTrades(lg: League, count: Int) {
        val ps = lg.proState ?: return
        var made = 0
        var tries = 0
        val maxTries = count * 25 + 30
        while (made < count && tries++ < maxTries) {
            val trade = when (val roll = Rng.d()) {
                in 0.0..0.45 -> upgradeTrade(lg, ps)
                in 0.45..0.75 -> sellOffTrade(lg, ps)
                in 0.75..0.9 -> salaryDumpTrade(lg, ps)
                else -> minorSwap(lg, ps)
            }
            if (trade != null) made++
        }
    }

    /** A contender (or the hero's stacked team) buys a starter-level player at its weakest spot. */
    private fun upgradeTrade(lg: League, ps: com.nh.hoopscoach.game.ProState): Trade? {
        // A team with a star (the user's player at 90 or better) builds around him: it buys, it never sells.
        val starTeam = lg.playerOrNull(lg.heroId)?.takeIf { it.teamId >= 0 && it.ovr >= 90 }?.let { lg.team(it.teamId) }
        val buyers = lg.teams.filter {
            !(NbaCoach.active(lg) && it.id == lg.userTeamId) &&
                (Needs.direction(lg, it) == Needs.Direction.CONTENDING || it.id == starTeam?.id)
        }.shuffled(Rng.r)
        for (buyer in buyers) {
            val spot = Needs.weakestSpot(lg, buyer) ?: continue
            val keep = (listOfNotNull(lg.heroId.takeIf { it >= 0 }, lg.road?.rivalId) + lg.rosterOf(buyer).filter { it.id != lg.heroId }
                .sortedByDescending { value(it) }.take(2).map { it.id }).toSet()
            // Sellers: anyone with a surplus player at that spot, rebuilders first.
            val sellers = lg.teams.filter { it.id != buyer.id && !(NbaCoach.active(lg) && it.id == lg.userTeamId) }
                .sortedBy { if (Needs.direction(lg, it) == Needs.Direction.REBUILDING) 0 else 1 }
            for (seller in sellers) {
                val target = tradeable(lg, seller, ps).filter { it.pos == spot && Needs.isSurplus(lg, seller, it) }
                    .maxByOrNull { neededValue(lg, buyer, it) } ?: continue
                if (Trades.value(target) < 3.0) continue
                val pack = bundle(lg, buyer, valueFor(lg, buyer, target), 0.7, 1.3, keep) ?: continue
                val fromB = pack.toMutableList()
                val picksB = mutableListOf<PickAsset>()
                val fromA = mutableListOf(target)
                val picksA = mutableListOf<PickAsset>()
                val sellerMotivated = Needs.direction(lg, seller) == Needs.Direction.REBUILDING || target.age >= 30
                if (!Payroll.tradeWorks(lg, buyer, Payroll.salaryOf(lg, target), pack.sumOf { Payroll.salaryOf(lg, it) })) continue
                if (!equalize(lg, seller, buyer, fromA, fromB, picksA, picksB, sellerMotivated, bMotivated = false)) continue
                return execute(lg, seller, buyer, fromA, fromB, picksA = picksA, picksB = picksB)
            }
        }
        return null
    }

    /** A rebuilder cashes in a productive veteran for youth and a draft pick. */
    private fun sellOffTrade(lg: League, ps: com.nh.hoopscoach.game.ProState): Trade? {
        val sellers = lg.teams.filter {
            Needs.direction(lg, it) == Needs.Direction.REBUILDING && !(NbaCoach.active(lg) && it.id == lg.userTeamId)
        }.shuffled(Rng.r)
        for (seller in sellers) {
            val vet = tradeable(lg, seller, ps).filter { it.age >= 27 && Trades.value(it) >= 5.0 }
                .maxByOrNull { value(it) } ?: continue
            // Who wants him? A team short at his spot; contenders pay best.
            val buyers = lg.teams.filter { it.id != seller.id && !(NbaCoach.active(lg) && it.id == lg.userTeamId) }
                .sortedByDescending { neededValue(lg, it, vet) * (if (Needs.direction(lg, it) == Needs.Direction.CONTENDING) 1.2 else 1.0) }
            for (buyer in buyers.take(6)) {
                if (Needs.needFactor(lg, buyer, vet) < 1.0) continue
                val target = neededValue(lg, buyer, vet)
                // The seller wants a pick plus young pieces; the buyer keeps its top two.
                val keep = (listOfNotNull(lg.heroId.takeIf { it >= 0 }) + lg.rosterOf(buyer).filter { it.id != lg.heroId }
                    .sortedByDescending { value(it) }.take(2).map { it.id }).toSet()
                val young = lg.rosterOf(buyer).filter { it.id !in keep && it.id != (lg.road?.rivalId ?: -2) && it.age <= 25 && it.id !in ps.moved }
                    .sortedByDescending { value(it) }
                val pack = mutableListOf<Player>()
                var sum = 0.0
                for (y in young) { if (sum + value(y) > target * 1.15) continue; pack += y; sum += value(y); if (sum >= target * 0.6 || pack.size == 2) break }
                val picksB = mutableListOf<PickAsset>()
                val fromA = mutableListOf(vet)
                val picksA = mutableListOf<PickAsset>()
                if (!Payroll.tradeWorks(lg, buyer, Payroll.salaryOf(lg, vet), pack.sumOf { Payroll.salaryOf(lg, it) })) continue
                if (!equalize(lg, seller, buyer, fromA, pack, picksA, picksB, aMotivated = true, bMotivated = false)) continue
                return execute(lg, seller, buyer, fromA, pack, picksA = picksA, picksB = picksB)
            }
        }
        return null
    }

    /** A team over the tax sheds a bad contract: money relief for whoever has the room or the need. */
    private fun salaryDumpTrade(lg: League, ps: com.nh.hoopscoach.game.ProState): Trade? {
        val dumper = lg.teams.filter {
            !(NbaCoach.active(lg) && it.id == lg.userTeamId) && Payroll.total(lg, it) > Payroll.tax(lg)
        }.shuffled(Rng.r).firstOrNull() ?: return null
        val dump = tradeable(lg, dumper, ps).filter { Needs.isSurplus(lg, dumper, it) && Payroll.salaryOf(lg, it) > ProCareer.marketValue(lg, it) * 1.35 }
            .maxByOrNull { Payroll.salaryOf(lg, it) - ProCareer.marketValue(lg, it) } ?: return null
        // A bad contract costs an asset to move: the dumper attaches a pick, and takes back the cheapest matching salary.
        for (taker in lg.teams.filter { it.id != dumper.id && !(NbaCoach.active(lg) && it.id == lg.userTeamId) }.shuffled(Rng.r)) {
            val room = Payroll.total(lg, taker) - Payroll.salaryOf(lg, dump)
            if (room > Payroll.cap(lg) * 0.9 && Needs.needFactor(lg, taker, dump) < 1.1) continue
            val back = tradeable(lg, taker, ps).filter { Payroll.salaryOf(lg, it) <= Payroll.salaryOf(lg, dump) }
                .minByOrNull { value(it) } ?: continue
            if (!Payroll.tradeWorks(lg, taker, Payroll.salaryOf(lg, dump), Payroll.salaryOf(lg, back))) continue
            val picksA = mutableListOf<PickAsset>()
            val overpay = Payroll.salaryOf(lg, dump) - ProCareer.marketValue(lg, dump)
            val cost = (overpay / (Payroll.cap(lg) * 0.05)).toInt().coerceAtMost(2)
            repeat(cost) {
                Picks.owned(lg, dumper).filter { it !in picksA }.maxByOrNull { it.season }?.let { picksA += it }
            }
            val fromA = mutableListOf(dump)
            val fromB = mutableListOf(back)
            val picksB = mutableListOf<PickAsset>()
            if (!equalize(lg, dumper, taker, fromA, fromB, picksA, picksB, aMotivated = true, bMotivated = false)) continue
            return execute(lg, dumper, taker, fromA, fromB, picksA = picksA, picksB = picksB)
        }
        return null
    }

    /** One small in-season deal (returns it, or null when nobody matches up). */
    fun minor(lg: League): Trade? = lg.proState?.let { minorSwap(lg, it) }

    /** A small deal: a team thin at a spot swaps bench pieces with one that's deep there. */
    private fun minorSwap(lg: League, ps: com.nh.hoopscoach.game.ProState): Trade? {
        val a = lg.teams.filter { !(NbaCoach.active(lg) && it.id == lg.userTeamId) }.shuffled(Rng.r).firstOrNull() ?: return null
        val spot = Needs.weakestSpot(lg, a) ?: return null
        val b = lg.teams.filter { it.id != a.id && !(NbaCoach.active(lg) && it.id == lg.userTeamId) }
            .shuffled(Rng.r).firstOrNull { Needs.depthAt(lg, it, spot).size >= 3 } ?: return null
        val get = tradeable(lg, b, ps).filter { it.pos == spot && Trades.value(it) in 0.5..6.0 }.maxByOrNull { neededValue(lg, a, it) } ?: return null
        val give = tradeable(lg, a, ps).filter { it.pos != spot && Needs.isSurplus(lg, a, it) && Trades.value(it) <= Trades.value(get) * 1.3 }
            .maxByOrNull { neededValue(lg, b, it) } ?: return null
        if (!Payroll.tradeWorks(lg, a, Payroll.salaryOf(lg, get), Payroll.salaryOf(lg, give))) return null
        val fromA = mutableListOf(give)
        val fromB = mutableListOf(get)
        val picksA = mutableListOf<PickAsset>()
        val picksB = mutableListOf<PickAsset>()
        if (!equalize(lg, a, b, fromA, fromB, picksA, picksB, aMotivated = false, bMotivated = false)) return null
        return execute(lg, a, b, fromA, fromB, picksA = picksA, picksB = picksB)
    }

    /**
     * Draft night: teams move up and down the board. A team hunting its man pays its later first plus
     * a future one for an earlier slot; the team moving down only deals when it isn't a rebuilder
     * sitting on a top-five pick.
     */
    fun draftDay(lg: League) {
        val ps = lg.proState ?: return
        val year = lg.season
        // Firsts on the clock, earliest (worst original record) first.
        val board = ps.picks.filter { it.season == year }.sortedBy { lg.team(it.original).winPct }
        var swaps = 0
        var tries = 0
        while (swaps < 2 && tries++ < 15) {
            val up = board.drop(5).shuffled(Rng.r).firstOrNull { candidate ->
                !(NbaCoach.active(lg) && candidate.owner == lg.userTeamId)
            } ?: return
            val upTeam = lg.team(up.owner)
            val down = board.take(board.indexOf(up)).shuffled(Rng.r).firstOrNull { earlier ->
                val t = lg.team(earlier.owner)
                t.id != upTeam.id && !(NbaCoach.active(lg) && t.id == lg.userTeamId) &&
                    !(board.indexOf(earlier) < 5 && Needs.direction(lg, t) == Needs.Direction.REBUILDING)
            } ?: return
            val downTeam = lg.team(down.owner)
            // The mover-up pays his later first plus his cheapest future first.
            val extra = Picks.owned(lg, upTeam).filter { it.season != year }.minByOrNull { Picks.value(lg, it) } ?: return
            Picks.move(lg, listOf(down), upTeam)
            Picks.move(lg, listOf(up, extra), downTeam)
            swaps++
            lg.news += News(lg.season, 0, "Draft-night trade: the ${upTeam.name} move up, sending ${Picks.label(lg, up)} and a future first to the ${downTeam.name} for ${Picks.label(lg, down)}", true)
        }
    }

    /** The deadline: AI teams deal, then his own team decides on him (an open request gets settled too). */
    fun deadline(lg: League) {
        val r = lg.road ?: run {
            aiTrades(lg, Rng.range(4, 8))
            lg.news += News(lg.season, lg.day, "The trade deadline passes", false)
            return
        }
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
