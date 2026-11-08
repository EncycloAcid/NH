package com.nh.hoopscoach.game

import kotlin.math.roundToInt

/**
 * Jersey sales: who the fans are buying. At the All-Star break and at season's end the league's top 15
 * is announced. Scoring and stardom sell, winning and a big market help, and so do young players with
 * buzz (and, for your own player, his following). A spot on the list pays: merch royalties for your
 * player, merch money for your team's budget as a coach.
 */
object Jerseys {
    fun score(lg: League, p: Player): Double {
        val s = p.stats
        if (s.gp < 8) return 0.0
        val t = lg.teams.getOrNull(p.teamId) ?: return 0.0
        val stars = p.awards.count { it.endsWith("All-Star") }
        val mvps = p.awards.count { it.endsWith(" MVP") && !it.contains("Finals") }
        var v = s.ppg * 1.1 + (s.rpg + s.apg) * 0.3 + stars * 2.2 + mvps * 5.0 + t.winPct * 9 + t.prestige / 20.0
        if (Ratings.shownPro(p.ovr) >= 90) v += 3.5
        if (p.age <= 24 && p.pot - p.ovr >= 4) v += 3.0
        if (p.id == lg.heroId) v += ((lg.road?.followers ?: 0) / 1_000_000.0 * 3.0).coerceAtMost(9.0)
        return v
    }

    /** The current top 15, best-selling first. */
    fun top(lg: League): List<Player> = lg.jerseyTop.mapNotNull { lg.playerOrNull(it) }

    /** Where [p] ranks (1-15), or 0 off the list. */
    fun rank(lg: League, p: Player): Int = lg.jerseyTop.indexOf(p.id) + 1

    /** Tallies the sales, announces them and pays out. [when] is "at the break" or "for the season". */
    fun update(lg: League, whenLabel: String) {
        if (!lg.pro) return
        lg.jerseyLast.clear(); lg.jerseyLast += lg.jerseyTop
        val ranked = lg.teams.flatMap { lg.rosterOf(it) }.map { it to score(lg, it) }.filter { it.second > 0 }.sortedByDescending { it.second }.take(15).map { it.first }
        if (ranked.isEmpty()) return
        lg.jerseyTop.clear(); lg.jerseyTop += ranked.map { it.id }
        lg.jerseySeason = lg.season
        val list = ranked.mapIndexed { i, p -> "${i + 1}. ${p.name} (${lg.team(p.teamId).abbr})" }
        lg.news += News(lg.season, lg.day, "Top-selling jerseys $whenLabel: ${ranked.take(3).joinToString(", ") { it.last }} lead the way", false, -1, "jerseys",
            list.joinToString(". ") + ".", "League Store", ranked.first().teamId, ranked.first().id)
        val money = Eras.of(lg).money
        // Your player's royalties.
        lg.hero?.takeIf { lg.playerMode }?.let { h ->
            val r = rank(lg, h)
            if (r > 0) {
                val pay = ((16 - r) * 60 * money).roundToInt()
                lg.road?.let { it.earned += pay }
                lg.news += News(lg.season, lg.day, "${h.name}'s jersey is No. $r in sales $whenLabel", true, -1, "jerseys",
                    "Merch royalties: ${Road.dollars(pay)}.", "League Store", h.teamId, h.id)
            }
        }
        // Your team's merch money, as a coach.
        if (!lg.playerMode) {
            val t = lg.user
            val mine = ranked.withIndex().filter { it.value.teamId == t.id }
            if (mine.isNotEmpty()) {
                Business.ensure(lg, t)
                val pay = mine.sumOf { (16 - (it.index + 1)) * 250 }.toLong()
                t.cash += pay
                lg.news += News(lg.season, lg.day, "${mine.size} ${t.name} ${if (mine.size == 1) "jersey" else "jerseys"} in the top 15 $whenLabel", true, -1, "jerseys",
                    mine.joinToString(", ") { "${it.value.name} (No. ${it.index + 1})" } + ". Merch adds ${Road.dollars(pay.toInt())} to the budget.", "League Store", t.id, -1)
            }
        }
    }
}
