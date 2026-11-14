package com.nh.hoopscoach.game

import kotlin.math.roundToInt

/**
 * The building and the money: ticket prices, crowds, the gate and the upgrades it pays for. A cheap
 * ticket fills the seats, a luxury one empties them; a full house is louder (a few Elo of home court),
 * and the owner or AD notices the sellouts at season's end. A quarter of the gate goes to the upgrade
 * budget.
 */
object Business {
    class Upgrade(val id: String, val name: String, val what: String, val proCost: Long, val collegeCost: Long)

    val UPGRADES = listOf(
        Upgrade("board", "Video board", "A new center-hung board: fans +4, a little fuller every night.", 8_000, 2_000),
        Upgrade("seats", "Expanded seating", "6% more seats in the building.", 25_000, 6_000),
        Upgrade("arena", "Arena renovation", "Full remodel: 8% more seats, fans +3, louder nights.", 40_000, 10_000),
        Upgrade("premium", "Premium seating", "Courtside clubs and suites: the gate is 10% bigger.", 15_000, 4_000),
        Upgrade("practice", "Practice facility", "Better development (coach development +3)" + "; in college, recruits notice too (recruiting +2).", 20_000, 5_000),
        Upgrade("training", "Training center", "Expanded performance lab: coach development +2 more; players happier (+3).", 28_000, 7_000),
        Upgrade("recruiting", "Recruiting infrastructure", "Film room, host suites, campus tour hub: coach recruiting +3.", 18_000, 4_500),
        Upgrade("brand", "National brand campaign", "Marketing push: buzz +8, fans +2, the program looks bigger nationally.", 22_000, 5_500),
        Upgrade("locker", "Locker room", "Players love it: happiness +6.", 6_000, 1_500),
    )
    val PRICES = listOf("Cheap", "Fair", "Standard", "Premium", "Luxury")
    private val PRICE_MULT = listOf(0.6, 0.8, 1.0, 1.3, 1.7)
    private val PRICE_FILL = listOf(0.08, 0.04, 0.0, -0.07, -0.15)

    fun cost(lg: League, u: Upgrade) = if (lg.pro) u.proCost else u.collegeCost
    fun has(t: Team, id: String) = id in t.upgrades

    /** What a seat costs, in dollars. */
    fun ticket(lg: League, t: Team): Int = ((if (lg.pro) 95.0 else 35.0) * PRICE_MULT[t.price.coerceIn(0, 4)] * Eras.of(lg).money.coerceAtLeast(0.2).let { if (lg.pro) it else 1.0 }).roundToInt()

    /** How much fuller (or emptier) the building is for the price and the upgrades. NBA buildings run fuller than college ones. */
    fun fillBonus(lg: League, t: Team): Double = PRICE_FILL[t.price.coerceIn(0, 4)] + (if (has(t, "board")) 0.02 else 0.0) + (if (lg.pro) 0.24 else 0.0)

    /** The starting upgrade budget, the first time anyone looks. */
    fun ensure(lg: League, t: Team) { if (t.cash < 0) t.cash = if (lg.pro) 10_000 else 2_000 }

    /** After a home game: the crowd (NBA counts it here; college in Fans), the gate and the budget. */
    fun afterGame(lg: League, g: Game) {
        if (g.neutral || g.att <= 0) return
        val h = lg.team(g.home)
        if (lg.pro) {
            h.homeGames++
            h.homeAtt += g.att
            if (g.att >= h.arena) h.sellouts++
        }
        val gate = (g.att.toLong() * ticket(lg, h) * (if (has(h, "premium")) 1.1 else 1.0) / 1000).toLong()
        h.gate += gate
        ensure(lg, h)
        h.cash += gate / 4
    }

    /** A full house is louder: up to about 10 Elo of extra home court, an empty one costs a little. */
    fun crowdElo(t: Team): Double {
        if (t.homeGames < 3) return 0.0
        return ((Fans.avgFill(t) - 0.75) * 40).coerceIn(-8.0, 10.0)
    }

    fun setPrice(lg: League, t: Team, level: Int) { t.price = level.coerceIn(0, 4) }

    fun buy(lg: League, t: Team, id: String): String {
        val u = UPGRADES.firstOrNull { it.id == id } ?: return ""
        ensure(lg, t)
        if (has(t, id)) return "Already built."
        val c = cost(lg, u)
        if (t.cash < c) return "Not enough in the budget yet."
        t.cash -= c
        t.upgrades += id
        when (id) {
            "board" -> t.fans = (t.fans + 4).coerceAtMost(100.0)
            "seats" -> t.arena = (t.arena * 1.06).roundToInt() / 50 * 50
            "arena" -> {
                t.arena = (t.arena * 1.08).roundToInt() / 50 * 50
                t.fans = (t.fans + 3).coerceAtMost(100.0)
            }
            "practice" -> runCatching { lg.coachOf(t) }.getOrNull()?.let { c -> c.dev = (c.dev + 3).coerceAtMost(99); if (!lg.pro) c.rec = (c.rec + 2).coerceAtMost(99) }
            "training" -> {
                runCatching { lg.coachOf(t) }.getOrNull()?.let { c -> c.dev = (c.dev + 2).coerceAtMost(99) }
                lg.rosterOf(t).forEach { it.happiness = (it.happiness + 3).coerceAtMost(99) }
            }
            "recruiting" -> runCatching { lg.coachOf(t) }.getOrNull()?.let { c -> c.rec = (c.rec + 3).coerceAtMost(99) }
            "brand" -> {
                Brand.ensure(lg)
                t.buzz = (t.buzz.coerceAtLeast(0.0) + 8).coerceAtMost(100.0)
                t.fans = (t.fans + 2).coerceAtMost(100.0)
            }
            "locker" -> lg.rosterOf(t).forEach { it.happiness = (it.happiness + 6).coerceAtMost(99) }
        }
        lg.news += News(lg.season, lg.day, "${if (lg.pro) "The ${t.name}" else t.school} ${if (lg.pro) "unveil" else "unveils"} a new ${u.name.lowercase()}", true, -1, "facility", u.what, "Hoops Wire Campus", t.id)
        if (lg.ollama.enabled && lg.ollama.universityDevelopment && t.id == lg.userTeamId) {
            Ollama.maybeAutoGenerateUniversityDevelopment(lg.ollama, lg, force = true)
        }
        return "${u.name} built."
    }

    /** The season's crowd, for the bosses: +2 for a building that sold out most nights, −2 for a half-empty one. */
    fun bossVerdict(t: Team): Int {
        if (t.homeGames < 5) return 0
        val sold = t.sellouts.toDouble() / t.homeGames
        return when { sold >= 0.7 -> 2; Fans.avgFill(t) < 0.5 -> -2; else -> 0 }
    }

    /** A new season: the gate starts over. */
    fun newSeason(t: Team) { t.gate = 0 }

    fun money(k: Long): String = when {
        k >= 1_000_000 -> "$" + "%.2f".format(k / 1_000_000.0) + "B"
        k >= 1_000 -> "$" + "%.1f".format(k / 1_000.0) + "M"
        else -> "$${k}K"
    }
}
