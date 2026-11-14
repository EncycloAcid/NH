package com.nh.hoopscoach.game

import kotlinx.serialization.Serializable
import java.io.File

/** A challenge being played: which one, the school, the first season and the last one it can be done in. */
@Serializable
class ChallengeRun(val id: String, val teamId: Int, val start: Int, val deadline: Int, var result: String = "", var medal: String = "")

/** A medal won, kept across careers. */
class Medal(val id: String, val medal: String, val season: Int, val school: String)

/**
 * Challenge mode: a career started with a goal and a clock. Do it early for gold, in time for silver or
 * bronze; miss the deadline and the career just goes on. Medals are kept across careers.
 */
object Challenges {
    class Def(val id: String, val name: String, val goal: String, val seasons: Int, val pick: (League) -> Team?, val done: (League, Team) -> Boolean)

    private fun round(lg: League, t: Team) = Awards.tourneyRound(lg, t.id)

    val ALL = listOf(
        Def("turnaround", "The turnaround", "Take over one of the worst power-conference programs and make the NCAA tournament within 3 seasons.", 3,
            { lg -> lg.teams.filter { lg.conf(it.confId).tier == 0 }.minByOrNull { it.prestige } }, { lg, t -> round(lg, t) >= 1 }),
        Def("midmajor", "Mid-major magic", "Take a mid-major to the Sweet 16 within 4 seasons.", 4,
            { lg -> lg.teams.filter { lg.conf(it.confId).tier == 1 }.sortedBy { it.prestige }.let { it.getOrNull(it.size / 2) } }, { lg, t -> round(lg, t) >= 3 }),
        Def("rebuild", "Final Four rebuild", "Start at a bottom-tier power-conference school and reach the Final Four within 5 seasons.", 5,
            { lg -> lg.teams.filter { lg.conf(it.confId).tier == 0 }.sortedBy { it.prestige }.let { it.getOrNull(it.size / 8) } }, { lg, t -> round(lg, t) >= 5 }),
        Def("lowmajor", "The impossible dream", "Win a national championship at a low-major school within 10 seasons.", 10,
            { lg -> lg.teams.filter { lg.conf(it.confId).tier >= 2 }.maxByOrNull { it.prestige } }, { lg, _ -> lg.history.lastOrNull()?.champion == lg.userTeamId || lg.ncaa?.champion == lg.userTeamId }),
        Def("blueblood", "Blue-blood pressure", "Take over the top program in the country and win the title within 2 seasons.", 2,
            { lg -> lg.teams.maxByOrNull { it.prestige } }, { lg, _ -> lg.ncaa?.champion == lg.userTeamId }),
    )

    fun def(id: String) = ALL.firstOrNull { it.id == id }

    /** The school a challenge starts at, in a league that hasn't been claimed yet. */
    fun teamFor(lg: League, id: String): Int = def(id)?.pick?.invoke(lg)?.id ?: -1

    fun begin(lg: League, id: String) {
        val d = def(id) ?: return
        lg.challenge = ChallengeRun(id, lg.userTeamId, lg.season, lg.season + d.seasons - 1)
        lg.news += News(lg.season, lg.day, "Challenge: ${d.name}", true, kind = "column", teamId = lg.userTeamId, body = d.goal, source = "Hoops Dynasty")
    }

    /** Called at each season's end. */
    fun seasonEnd(lg: League) {
        val run = lg.challenge ?: return
        if (run.result.isNotEmpty()) return
        val d = def(run.id) ?: return
        if (lg.userTeamId != run.teamId) {
            run.result = "forfeited"
            Alerts.add(lg, "Challenge over", "You left ${lg.team(run.teamId).school}, so \"${d.name}\" is forfeited.", "")
            return
        }
        val t = lg.user
        if (d.done(lg, t)) {
            val used = lg.season - run.start + 1
            val medal = when {
                used <= maxOf(1, (d.seasons + 2) / 3) -> "gold"
                used <= maxOf(1, d.seasons * 2 / 3) -> "silver"
                else -> "bronze"
            }
            run.result = "won"
            run.medal = medal
            save(Medal(run.id, medal, lg.season, t.school))
            Alerts.add(lg, "Challenge complete: ${medal.uppercase()}", "\"${d.name}\" done in $used season${if (used == 1) "" else "s"} at ${t.school}.", "")
            lg.news += News(lg.season, lg.day, "${t.school} completes \"${d.name}\" for a $medal medal", true, kind = "column", teamId = t.id, body = d.goal, source = "Hoops Dynasty")
        } else if (lg.season >= run.deadline) {
            run.result = "missed"
            Alerts.add(lg, "Challenge missed", "Time ran out on \"${d.name}\". Your career at ${t.school} goes on.", "")
        }
    }

    private fun file(): File? = runCatching { File(com.nh.hoopscoach.PlatformContext.filesDir, "medals.txt") }.getOrNull()

    fun medals(): List<Medal> = runCatching {
        file()?.takeIf { it.exists() }?.readLines()?.mapNotNull { l ->
            val p = l.split('|')
            if (p.size >= 4) Medal(p[0], p[1], p[2].toIntOrNull() ?: 0, p[3]) else null
        }
    }.getOrNull().orEmpty()

    private fun save(m: Medal) {
        runCatching { file()?.appendText("${m.id}|${m.medal}|${m.season}|${m.school.replace('|', ' ')}\n") }
    }
}
