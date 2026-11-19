package com.nh.hoopscoach.game

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/** A summer with Team USA: the event, where it stands (0 offered, 1 accepted, 2 declined, 3 played), the pool, his twelve and the results. */
@Serializable
class UsaTeam(
    val season: Int, val event: String, val pro: Boolean, var status: Int = 0,
    val pool: MutableList<Int> = mutableListOf(), val picked: MutableList<Int> = mutableListOf(),
    val games: MutableList<String> = mutableListOf(), var medal: String = "",
)

/**
 * Team USA calls a decorated coach: an NBA coach for the Olympics or the World Cup (the best American
 * players in the league), a college coach for the U19 World Cup in odd summers (the best American
 * teenagers in college). He picks twelve, plays the group and the knockout rounds, and the medal gets
 * its own ceremony.
 */
object TeamUSA {
    private val PRO_OPP = listOf("Serbia" to 89, "France" to 89, "Canada" to 89, "Germany" to 88, "Spain" to 87, "Australia" to 87, "Greece" to 86, "Slovenia" to 86,
        "Lithuania" to 85, "Italy" to 85, "Argentina" to 84, "Brazil" to 83, "South Sudan" to 82, "Puerto Rico" to 82, "Japan" to 81, "Nigeria" to 81)
    private val U19_OPP = listOf("Canada" to 75, "France" to 74, "Spain" to 74, "Serbia" to 73, "Australia" to 73, "Lithuania" to 72, "Germany" to 71, "Turkey" to 71,
        "Argentina" to 70, "Italy" to 70, "Mali" to 68, "Brazil" to 68, "China" to 67, "Japan" to 66)

    private fun f1(d: Double) = "%.1f".format(d)

    /** A coach Team USA would call: a title, a Coach of the Year, or a long winning record. */
    fun eligible(lg: League): Boolean {
        if (lg.road != null) return false
        val c = runCatching { lg.userCoach }.getOrNull() ?: return false
        val titles = c.history.count { it.result == "National Champion" || it.result == "Champion" }
        val w = c.history.sumOf { it.w }
        val l = c.history.sumOf { it.l }
        val coty = c.natCoty > 0 || lg.proState?.honors?.any { it.award == "Coach of the Year" && it.name == c.name } == true
        return titles >= 1 || coty || (c.history.size >= 5 && w.toDouble() / (w + l).coerceAtLeast(1) >= 0.62)
    }

    /** The best Americans for the event, best first. */
    private fun pool(lg: League): List<Player> = if (lg.pro) lg.players.filter { it.teamId >= 0 && it.country == null && it.year == PRO_YEAR }.sortedByDescending { it.ovr }.take(30)
    else lg.players.filter { it.teamId >= 0 && it.country == null && it.age <= 19 }.sortedByDescending { it.ovr }.take(30)

    /** This summer's call, if there's an event and he's the man for it. */
    fun offer(lg: League) {
        if (!eligible(lg)) return
        val event = if (lg.pro) Summer.proTournament(lg.season) else if (lg.season % 2 == 1) "U19 World Cup" else null
        if (event == null || lg.usa?.season == lg.season) return
        if (!Rng.chance(if (lg.pro) 0.6 else 0.5)) return
        val p = pool(lg)
        if (p.size < 12) return
        lg.usa = UsaTeam(lg.season, event, lg.pro, 0, p.map { it.id }.toMutableList(), p.take(12).map { it.id }.toMutableList())
        lg.news += News(lg.season, lg.day, "USA Basketball wants ${lg.userCoach.name} to coach at the ${lg.season} $event", true)
        Alerts.add(lg, "Team USA is calling", "USA Basketball wants you to coach its team at the ${lg.season} $event. The offer is in your offseason.", "")
    }

    fun active(lg: League) = lg.usa?.takeIf { it.season == lg.season }

    fun respond(lg: League, yes: Boolean) { active(lg)?.let { if (it.status == 0) it.status = if (yes) 1 else 2 } }

    fun toggle(lg: League, pid: Int) {
        val u = active(lg) ?: return
        if (u.status != 1) return
        if (pid in u.picked) u.picked.remove(pid) else if (u.picked.size < 12) u.picked += pid
    }

    /** The tournament: three group games, then the knockout rounds; a loss in the semis plays for bronze. */
    fun play(lg: League) {
        val u = active(lg) ?: return
        if (u.status != 1 || u.picked.size != 12) return
        val roster = u.picked.mapNotNull { lg.playerOrNull(it) }
        val strength = roster.sortedByDescending { it.ovr }.take(8).map { it.ovr }.average()
        val opp = if (u.pro) PRO_OPP else U19_OPP
        val scale = if (u.pro) 45.0 else 35.0
        val baseScore = if (u.pro) 96 else 80
        val used = mutableSetOf<String>()
        fun rival(from: List<Pair<String, Int>>) = from.filter { it.first !in used }.ifEmpty { from }.let { Rng.pick(it) }.also { used += it.first }
        var w = 0; var l = 0; var margin = 0
        val scorers = HashMap<Int, Int>()
        fun game(round: String, o: Pair<String, Int>): Boolean {
            val diff = (strength - o.second) * scale
            val p = 1.0 / (1.0 + Math.pow(10.0, -diff / 400.0))
            val won = Rng.d() < p
            val m = ((p - 0.5) * 30 + Rng.gauss() * 9).roundToInt().let { if (won) it.coerceAtLeast(1) else (-it).coerceAtLeast(1) }
            val us = baseScore + Rng.range(-8, 10)
            val them = if (won) us - m else us + m
            val top = Rng.weighted(roster) { it.ovr.toDouble() * it.ovr }
            val pts = Rng.range(14, 29)
            scorers[top.id] = (scorers[top.id] ?: 0) + pts
            if (won) w++ else l++
            margin += us - them
            u.games += "$round: USA $us, ${o.first} $them${if (won) "" else " (L)"} · ${top.name} $pts pts"
            return won
        }
        val sorted = opp.sortedBy { it.second }
        repeat(3) { game("Group", rival(sorted.take(10))) }
        u.medal = when {
            !game("Quarterfinal", rival(sorted.drop(4))) -> "Fifth place"
            !game("Semifinal", rival(sorted.drop(8))) -> if (game("Bronze game", rival(sorted.drop(6)))) "Bronze" else "Fourth place"
            game("Final", rival(sorted.takeLast(4))) -> "Gold"
            else -> "Silver"
        }
        u.status = 3
        val medal = u.medal in setOf("Gold", "Silver", "Bronze")
        if (u.medal == "Gold") lg.usaGolds++
        // The players: a medal sticks with them (and a gold a little more).
        val lifts = when (u.medal) { "Gold" -> 3; "Silver", "Bronze" -> 2; else -> 1 }
        for (p in roster) {
            repeat(lifts) { Ratings.raise(p, Rng.int(0, p.attrs.size)) }
            if (u.pro) ProLeague.rookieCap(lg, p)
            p.happiness = (p.happiness + 5).coerceAtMost(99)
            if (medal) p.awards += "${u.season} ${if (u.pro) u.event else "U19"} ${u.medal}"
        }
        val c = lg.userCoach
        val star = scorers.maxByOrNull { it.value }?.let { lg.playerOrNull(it.key) }
        val head = when (u.medal) {
            "Gold" -> "Gold: ${c.name}'s Team USA wins the ${u.season} ${u.event}"
            "Silver", "Bronze" -> "${u.medal} for ${c.name}'s Team USA at the ${u.event}"
            else -> "Team USA finishes ${u.medal.lowercase()} at the ${u.event} under ${c.name}"
        }
        val body = u.games.joinToString("\n")
        val an = Media.pundit(lg)
        val trib = listOfNotNull(
            star?.let { Tribute(it.name, "Team USA", if (u.medal == "Gold") "Gold. For the country. Coach had us ready every night." else "We wanted gold. We'll be back.") },
            roster.filter { it.id != star?.id }.maxByOrNull { it.ovr }?.let { Tribute(it.name, "Team USA", "Honor of my life to wear this jersey.") },
            Tribute(an.name, "Analyst", when (u.medal) { "Gold" -> "${c.last} pushed every right button."; "Silver" -> "One game short. The world caught up a little."; else -> "Not the result USA Basketball expects." }, an.avatar, an.verified),
            Tribute(Media.fanName(), "Fan", if (u.medal == "Gold") "USA! USA! USA! 🇺🇸" else "Next time."),
        )
        lg.news += News(lg.season, lg.day, head, true, -1, "award", body, "Hoops Wire", -1, star?.id ?: -1,
            trib.map { Reaction(it.who, it.role, it.text, Rng.range(2_000, 90_000), it.avatar, it.verified) })
        Farewell.show(lg, Ceremony(
            "olympics", "${u.event.uppercase()} · ${u.season}", u.medal.uppercase(), "Team USA · Coach ${c.name}", -1, 0,
            listOf("RECORD", "AVG MARGIN", "TOP SCORER"), listOf("$w-$l", "${if (margin >= 0) "+" else ""}${f1(margin.toDouble() / (w + l).coerceAtLeast(1))}", star?.last ?: "—"),
            "The tournament", u.games.toList(), trib, listOf(head),
            badge = when (u.medal) { "Gold" -> "🥇 Gold medal"; "Silver" -> "🥈 Silver medal"; "Bronze" -> "🥉 Bronze medal"; else -> "" },
        ))
    }
}
