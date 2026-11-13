package com.nh.hoopscoach.game

import kotlinx.serialization.Serializable

/**
 * Something that happened in this world, stored when it happened. The timeline, a school's history and
 * "this week in history" are all read back from these; nothing on them is written after the fact.
 * [weight] is how much it matters: 1 a note, 3 a big story, 5 a moment the save will always be known for.
 */
@Serializable
class WorldEvent(
    val season: Int, val day: Int, val type: String, val title: String, val text: String,
    val teamId: Int = -1, val pid: Int = -1, val coach: String = "", val weight: Int = 1,
)

object Timeline {
    /** Old, minor notes are dropped past this many; big moments are never dropped. */
    private const val KEEP = 800

    private val MADE_IT = setOf("First Four", "Round of 64", "Round of 32", "Sweet 16", "Elite Eight", "Final Four", "Runner-Up", "National Champion")

    fun record(lg: League, type: String, title: String, text: String, teamId: Int = -1, pid: Int = -1, coach: String = "", weight: Int = 1) {
        if (lg.timeline.any { it.season == lg.season && it.type == type && it.teamId == teamId && it.pid == pid && it.coach == coach }) return
        lg.timeline += WorldEvent(lg.season, lg.day, type, title, text, teamId, pid, coach, weight)
        if (lg.timeline.size > KEEP) lg.timeline.filter { it.weight <= 1 }.minByOrNull { it.season }?.let { lg.timeline.remove(it) }
    }

    fun of(lg: League, teamId: Int) = lg.timeline.filter { it.teamId == teamId }
    fun ofPlayer(lg: League, pid: Int) = lg.timeline.filter { it.pid == pid }

    /** Big moments from earlier seasons that happened within a week of today's date. */
    fun thisWeek(lg: League): List<WorldEvent> =
        lg.timeline.filter { it.season < lg.season && it.weight >= 3 && kotlin.math.abs(it.day - lg.day) <= 7 }.sortedByDescending { it.weight * 100 + it.season }.take(3)

    private fun ordinalTitle(n: Int) = when (n) { 2 -> "Back-to-back"; 3 -> "Three in a row"; else -> "$n straight titles" }

    /**
     * The end of a season, before it is written into each team's history: the title and what made it
     * unusual, first Final Fours, droughts ended and the bracket's biggest upset.
     */
    fun seasonEnd(lg: League, championId: Int, runnerUpId: Int, finalFour: List<Int>) {
        val champ = lg.team(championId)
        val c = lg.coachOf(champ)
        var streak = 1
        for (s in lg.history.reversed()) if (s.champion == championId) streak++ else break
        val elsewhere = c.history.filter { it.result == "National Champion" && it.teamId != championId }.map { it.team }.distinct()
        val record = "${champ.w}-${champ.l}"
        when {
            streak >= 2 -> record(lg, "title", "${ordinalTitle(streak)} for ${champ.school}",
                "${champ.school} beat ${lg.team(runnerUpId).school} for its ${Previews.ordinal(streak)} straight national championship, finishing $record under ${c.name}.", championId, coach = c.name, weight = 5)
            champ.titles == 1 -> record(lg, "title", "${champ.school} wins its first national championship",
                "${champ.school} had never won it all. ${c.name}'s team went $record and beat ${lg.team(runnerUpId).school} in the title game.", championId, coach = c.name, weight = 5)
            else -> record(lg, "title", "${champ.school} wins the national championship",
                "Title No. ${champ.titles} for ${champ.school}, which finished $record and beat ${lg.team(runnerUpId).school} in the final.", championId, coach = c.name, weight = 4)
        }
        if (elsewhere.isNotEmpty()) record(lg, "coach2", "${c.name} wins a title at a second school",
            "${c.name} won a national championship at ${elsewhere.first()} and has now won one at ${champ.school}.", championId, coach = c.name, weight = 5)
        if (champ.l == 0) record(lg, "perfect", "${champ.school} finishes unbeaten", "${champ.school} went $record: a perfect season.", championId, coach = c.name, weight = 5)
        for (id in finalFour) {
            val t = lg.team(id)
            if (t.finalFours == 1 && id != championId) record(lg, "f4first", "${t.school} reaches its first Final Four",
                "${t.school} (${t.w}-${t.l}) had never been to a Final Four before ${lg.season}.", id, coach = lg.coachOf(t).name, weight = 4)
        }
        val ncaa = lg.ncaa
        if (ncaa != null) for (t in lg.teams) {
            if (!ncaa.hasTeam(t.id) || t.history.size < 8) continue
            val away = t.history.reversed().takeWhile { it.result !in MADE_IT }.size
            if (away >= 8) record(lg, "return", "${t.school} is back in the tournament after $away years",
                "${t.school} had not made the NCAA tournament since ${lg.season - away - 1}. It went ${t.w}-${t.l} under ${lg.coachOf(t).name}.", t.id, coach = lg.coachOf(t).name, weight = 3)
        }
        lg.moments.filter { it.season == lg.season && it.wSeed >= 15 && it.lSeed in 1..2 }.maxByOrNull { it.wSeed - it.lSeed }?.let { m ->
            val w = lg.teams.getOrNull(m.winner) ?: return@let
            val l = lg.teams.getOrNull(m.loser) ?: return@let
            record(lg, "upset", "No. ${m.wSeed} ${w.school} beats No. ${m.lSeed} ${l.school}",
                "${w.school} ${m.wScore}, ${l.school} ${m.lScore} in the NCAA tournament" + (if (m.wSeed == 16) ": a 16 seed over a 1." else "."), w.id, weight = if (m.wSeed == 16) 5 else 4)
        }
    }
}
