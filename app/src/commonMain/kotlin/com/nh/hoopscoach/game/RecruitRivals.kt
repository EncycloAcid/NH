package com.nh.hoopscoach.game

import kotlinx.serialization.Serializable

/**
 * A school the user keeps fighting for recruits: how many it has beaten him for, how many he's taken
 * from it, the season it became a rival (0: not one), and the last few recruits it won.
 */
@Serializable
class RecruitRival(val teamId: Int, var lost: Int = 0, var won: Int = 0, var since: Int = 0, var names: MutableList<String> = mutableListOf())

/**
 * Recruiting rivalries. When the same school keeps beating the user for recruits he was really in on,
 * it becomes his recruiting rival: there's news, and its pitch gets tougher (+20% a round) on every
 * recruit they're both after. Win enough of them back, or let a few seasons pass, and it cools.
 */
object RecruitRivals {
    private const val BOOST = 1.2

    fun of(lg: League, teamId: Int): RecruitRival? = lg.recruitRivals.firstOrNull { it.teamId == teamId }

    fun isRival(lg: League, teamId: Int) = (of(lg, teamId)?.since ?: 0) > 0

    fun rivals(lg: League) = lg.recruitRivals.filter { it.since > 0 }

    private fun entry(lg: League, teamId: Int) = of(lg, teamId) ?: RecruitRival(teamId).also { lg.recruitRivals += it }

    private fun name(lg: League, t: Team) = if (lg.pro) t.name else t.school

    /** [t] beat the user for [r]. */
    fun lost(lg: League, r: Recruit, t: Team) {
        if (lg.pro || lg.playerMode) return
        val e = entry(lg, t.id)
        e.lost++
        e.names.add(0, r.p.name)
        while (e.names.size > 5) e.names.removeAt(e.names.lastIndex)
        if (e.since == 0 && e.lost >= 3 && e.lost > e.won) {
            e.since = lg.season
            val u = lg.user
            val list = e.names.take(3).joinToString(", ")
            lg.news += News(lg.season, lg.day, "${name(lg, t)} is ${u.school}'s new recruiting rival", true, kind = "column", teamId = t.id,
                body = "${name(lg, t)} has beaten ${u.school} for ${e.lost} recruits lately, $list among them. Expect ${lg.coachOf(t).name}'s staff to go after every prospect you want.",
                source = "Hoops Wire")
            Alerts.add(lg, "A recruiting rival", "${name(lg, t)} keeps beating you for recruits ($list). From now on their pitch is tougher on every recruit you're both after.", "")
            Timeline.record(lg, "recruitrival", "${name(lg, t)} and ${u.school} become recruiting rivals",
                "${name(lg, t)} won ${e.lost} recruiting battles with ${u.school}, including $list.", t.id, coach = lg.coachOf(t).name, weight = 2, team2 = u.id)
        }
    }

    /** The user beat [t] for [r]. */
    fun won(lg: League, r: Recruit, t: Team) {
        if (lg.pro || lg.playerMode) return
        val e = entry(lg, t.id)
        e.won++
        if (e.since > 0 && e.won >= e.lost + 3) {
            e.since = 0
            e.lost = 0
            e.won = 0
            lg.news += News(lg.season, lg.day, "${lg.user.school} turns the tables on ${name(lg, t)} on the recruiting trail", true, kind = "column", teamId = t.id,
                body = "After losing recruit after recruit to ${name(lg, t)}, ${lg.user.school} has won the last few battles, ${r.p.name} the latest.", source = "Hoops Wire")
        }
    }

    /** Each offseason the tally fades; a rivalry with little left to it cools. */
    fun seasonEnd(lg: League) {
        for (e in lg.recruitRivals) {
            e.lost = e.lost * 2 / 3
            e.won = e.won * 2 / 3
            if (e.since > 0 && e.lost < 2 && lg.season - e.since >= 2) e.since = 0
        }
        lg.recruitRivals.removeAll { it.since == 0 && it.lost == 0 && it.won == 0 }
    }

    /** How much harder a rival's pitch is on a recruit the user is after. */
    fun pitch(lg: League, r: Recruit, teamId: Int): Double {
        val u = lg.recruiterId
        if (u < 0 || teamId == u || !isRival(lg, teamId)) return 1.0
        return if (r.onBoard || u in r.alloc || (r.interest[u] ?: 0) >= 30) BOOST else 1.0
    }

    /** Recruiting rivals in on [r], for his page. */
    fun inOn(lg: League, r: Recruit): List<Team> = r.interest.keys.filter { isRival(lg, it) }.mapNotNull { lg.teams.getOrNull(it) }
}
