package com.nh.hoopscoach.game

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Building a college program: six facilities from level 1 to 5, a booster club that pays for them, and the
 * ticket price. Every program starts at levels that fit its prestige; effects are measured against an average
 * (level 3) program, so a blue blood's arena is louder than a low-major's and building yours up pays off:
 *  - Arena: home-court edge (and seats)
 *  - Practice facility: player development
 *  - Recruiting office: recruiting pull
 *  - Training & medical: fewer injuries
 *  - Locker room: morale, every week
 *  - Fan experience: fuller crowds and fans' mood
 * Money comes from a quarter of the gate (see [Business]), boosters every week (more when they're happy),
 * a once-a-season gala, and postseason payouts. A big donor will cover 40% of a level-4 or 5 build if his name
 * goes on it.
 */
object Facilities {
    class Kind(val id: String, val name: String, val icon: String, val base: Long, val blurb: String)

    val ALL = listOf(
        Kind("arena", "Arena", "🏟️", 1_200, "A louder building: home-court edge, and more seats."),
        Kind("practice", "Practice facility", "🏀", 900, "Players develop faster, in practice and over the summer."),
        Kind("recruiting", "Recruiting office", "📋", 700, "Recruiting pull: the program looks bigger to recruits."),
        Kind("training", "Training & medical", "🩺", 600, "Fewer injuries, all season."),
        Kind("locker", "Locker room", "🛋️", 400, "Players are happier every week, and stay."),
        Kind("fans", "Fan experience", "📣", 500, "Video board and student section: fuller, louder crowds."),
    )

    private fun kind(id: String) = ALL.first { it.id == id }

    /** Where a program starts: about level 1 for a low-major up to 5 for a blue blood, a little different school to school. */
    private fun startLevel(t: Team, id: String): Int {
        val wobble = ((t.school.hashCode() * 31 + id.hashCode()) and 0x7fffffff) % 3 - 1
        return (1 + t.prestige / 32.0 + wobble * 0.6).roundToInt().coerceIn(1, 5)
    }

    /** Fills in a program's levels the first time, counting the old one-time upgrades it already bought. */
    fun ensure(t: Team) {
        if (t.facilities.isNotEmpty()) return
        for (k in ALL) t.facilities[k.id] = startLevel(t, k.id)
        fun bump(id: String) { t.facilities[id] = ((t.facilities[id] ?: 1) + 1).coerceAtMost(5) }
        for (u in t.upgrades.distinct()) when (u) {
            "arena", "seats" -> bump("arena")
            "practice" -> bump("practice")
            "training" -> bump("training")
            "recruiting" -> bump("recruiting")
            "locker" -> bump("locker")
            "board", "brand" -> bump("fans")
        }
    }

    fun level(t: Team, id: String): Int { ensure(t); return t.facilities[id] ?: 3 }

    /** What the next level costs, in thousands, or null at the top. */
    fun cost(t: Team, id: String): Long? {
        val l = level(t, id)
        return if (l >= 5) null else kind(id).base * l
    }

    // ---------- effects, measured against an average (level 3) program ----------

    /** Elo of home court from the building itself. */
    fun homeElo(lg: League, t: Team): Double = if (lg.pro) 0.0 else (level(t, "arena") - 3) * 7.0
    /** Development points, like a coach's development rating. */
    fun dev(lg: League, t: Team): Int = if (lg.pro) 0 else (level(t, "practice") - 3) * 3
    /** Recruiting pull, plus a packed student section's buzz at cheap prices. */
    fun pull(lg: League, t: Team): Double = if (lg.pro) 0.0 else (level(t, "recruiting") - 3) * 2.0 + when (t.price) { 0 -> 1.0; 4 -> -1.0; else -> 0.0 }
    /** Injury odds multiplier. */
    fun injury(lg: League, t: Team?): Double = if (lg.pro || t == null) 1.0 else 1.0 - (level(t, "training") - 3) * 0.08
    /** Extra share of the arena filled. */
    fun fill(lg: League, t: Team): Double = if (lg.pro) 0.0 else (level(t, "fans") - 3) * 0.02
    /** Happiness each week. */
    fun morale(lg: League, t: Team): Int = if (lg.pro) 0 else level(t, "locker") - 3

    /** The effect of a level, in words, for the screen. */
    fun effectAt(id: String, l: Int): String {
        val d = l - 3
        fun s(v: Number) = (if (v.toDouble() > 0) "+" else if (v.toDouble() < 0) "−" else "±") + when (v) { is Double -> "%.1f".format(kotlin.math.abs(v)).removeSuffix(".0"); else -> "${kotlin.math.abs(v.toInt())}" }
        return when (id) {
            "arena" -> "Home-court edge ${s(winPct(d * 7.0))}% win chance"
            "practice" -> "Player development ${s(d * 3)}"
            "recruiting" -> "Recruiting pull ${s(d * 2)}"
            "training" -> "Injuries ${s(-d * 8)}%"
            "locker" -> "Morale ${s(d)} a week"
            else -> "Crowds ${s(d * 2)}% fuller"
        }
    }

    /** Elo as a home win-chance edge, in percentage points. */
    fun winPct(elo: Double): Double = ((1.0 / (1.0 + 10.0.pow(-elo / 400.0)) - 0.5) * 100)

    /** The whole home-court edge at [t] (the usual edge, the crowd, the building), as win chance. */
    fun homeEdgePct(lg: League, t: Team): Double = winPct(Rankings.HOME_ELO + Business.crowdElo(t) + homeElo(lg, t))

    // ---------- money ----------

    /** Builds the next level. [named] puts a donor's name on it and he pays 40%. */
    fun build(lg: League, t: Team, id: String, named: Boolean = false): String {
        Business.ensure(lg, t)
        val c = cost(t, id) ?: return "Already at the top level."
        val share = if (named && level(t, id) >= 3) (c * 0.4).toLong() else 0L
        if (t.cash < c - share) return "Not enough in the budget yet."
        t.cash -= c - share
        t.facilities[id] = level(t, id) + 1
        val k = kind(id)
        if (share > 0) t.facilityNames[id] = donors(t).first().name
        when (id) {
            "arena" -> t.arena = (t.arena * 1.05).roundToInt() / 50 * 50
            "fans" -> t.fans = (t.fans + 2).coerceAtMost(100.0)
            "locker" -> lg.rosterOf(t).forEach { it.happiness = (it.happiness + 3).coerceAtMost(99) }
        }
        t.boosterMood = (t.boosterMood + 3).coerceAtMost(100)
        val title = displayName(t, id)
        lg.news += News(lg.season, lg.day, "${t.school} opens its new ${title.lowercase().let { if (share > 0) displayName(t, id) else it }}", t.id == lg.userTeamId,
            kind = "facility", teamId = t.id, source = "Hoops Wire Campus",
            body = "${k.blurb} Now level ${level(t, id)}: ${effectAt(id, level(t, id)).lowercase()}." + if (share > 0) " ${t.facilityNames[id]} covered ${Business.money(share)} of the bill." else "")
        return "$title: level ${level(t, id)}."
    }

    fun displayName(t: Team, id: String): String {
        val n = t.facilityNames[id]
        return if (n != null) "${n.substringAfterLast(' ')} ${kind(id).name}" else kind(id).name
    }

    class Donor(val name: String, val business: String)

    private val BUSINESSES = listOf("Auto Group", "Construction", "Energy", "Homes", "Insurance", "Logistics", "Steakhouse", "Bank & Trust", "Furniture", "Dealerships")

    /** The program's biggest boosters, the same names every time. */
    fun donors(t: Team): List<Donor> {
        val r = kotlin.random.Random(t.school.hashCode().toLong() * 7919 + 11)
        return List(3) {
            val last = Names.last[r.nextInt(Names.last.size)]
            Donor("${Names.first[r.nextInt(Names.first.size)]} $last", "$last ${BUSINESSES[r.nextInt(BUSINESSES.size)]}")
        }
    }

    fun moodLabel(m: Int) = when { m >= 80 -> "Thrilled"; m >= 60 -> "Happy"; m >= 40 -> "Patient"; m >= 25 -> "Restless"; else -> "Furious" }

    /** What the boosters give in a week, in thousands: the fan base and the program's name, more when they're happy. */
    fun weekly(t: Team): Long = ((20 + t.fans * 0.8 + t.prestige * 0.4) * (0.5 + t.boosterMood / 100.0)).roundToInt().toLong()

    /** The gala: once a season, money now and a better mood. */
    fun galaAmount(t: Team): Long = ((150 + t.fans * 6 + t.prestige * 4) * (0.6 + t.boosterMood / 120.0)).roundToInt().toLong() / 10 * 10

    fun canGala(lg: League, t: Team) = !lg.pro && t.galaSeason != lg.season && lg.phase == Phase.REGULAR

    fun gala(lg: League, t: Team): String {
        if (!canGala(lg, t)) return "One gala a season."
        Business.ensure(lg, t)
        val amt = galaAmount(t)
        t.cash += amt
        t.boosterSeason += amt
        t.galaSeason = lg.season
        t.boosterMood = (t.boosterMood + 5).coerceAtMost(100)
        lg.news += News(lg.season, lg.day, "${t.school}'s booster gala raises ${Business.money(amt)}", t.id == lg.userTeamId, kind = "facility", teamId = t.id, source = "Hoops Wire Campus",
            body = "${donors(t).first().name} of ${donors(t).first().business} gave the most and gave the speech. The money goes to the facilities fund.")
        return "The gala raised ${Business.money(amt)}."
    }

    /** Every day: boosters react to last night's game; every week, they give. And every program's locker room. */
    fun daily(lg: League) {
        if (lg.pro) return
        val u = lg.user
        ensure(u)
        Business.ensure(lg, u)
        lg.games.filter { it.day == lg.day && it.played && it.involves(u.id) }.forEach { g ->
            val won = g.winner() == u.id
            val opp = lg.team(g.opp(u.id))
            var d = if (won) 0.6 else -0.6
            if (won && opp.ap in 1..25) d += 2.0
            if (lg.rivalry(g) != null) d += if (won) 3.0 else -3.0
            u.boosterMood = (u.boosterMood + d).roundToInt().coerceIn(5, 100)
        }
        if (lg.phase == Phase.REGULAR && lg.day % 7 == 0) {
            val w = weekly(u)
            u.cash += w
            u.boosterSeason += w
            for (t in lg.teams) { ensure(t); val m = morale(lg, t); if (m != 0) for (p in lg.rosterOf(t)) p.happiness = (p.happiness + m).coerceIn(0, 99) }
        }
    }

    /** The end of a season: March pays, the boosters judge it, and the giving count starts over. */
    fun seasonEnd(lg: League) {
        if (lg.pro) return
        val u = lg.user
        ensure(u)
        Business.ensure(lg, u)
        val round = Awards.tourneyRound(lg, u.id)
        val confChamp = lg.confBrackets.any { it.champion == u.id }
        var pay = 0L
        if (confChamp) pay += 400
        if (round >= 1) pay += 500 + (round - 1).coerceAtLeast(0) * 250L
        if (round >= 7) pay += 1_500
        var d = when { round >= 7 -> 20; round >= 5 -> 12; round >= 3 -> 8; round >= 1 -> 4; else -> -6 }
        if (confChamp) d += 5
        if (round == 0 && u.prestige >= 70) d -= 6
        u.boosterMood = (u.boosterMood + d).coerceIn(5, 100)
        if (pay > 0) {
            u.cash += pay
            lg.news += News(lg.season, lg.day, "${u.school}'s postseason brings in ${Business.money(pay)} for the program", true, kind = "facility", teamId = u.id, source = "Hoops Wire Campus",
                body = "Tournament units, conference money and a happy booster club. The facilities fund grows.")
        }
        u.boosterSeason = 0
    }
}
