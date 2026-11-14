package com.nh.hoopscoach.game

import kotlinx.serialization.Serializable

/**
 * Something a player carries with him: who believed in him, a title won or lost, a season on the bench.
 * [pull] is how it ties him to [teamId]: positive keeps him there, negative pushes him toward the door.
 */
@Serializable
class Memory(val season: Int, val kind: String, val text: String, val teamId: Int = -1, val pull: Int = 0)

/**
 * Player memory. Players remember the moments that shape a career, and those memories weigh on what
 * they do next: a player whose promised role never came looks to leave even in a decent mood, while
 * one who was recruited when nobody else called, or who won something there, stays through a bad year.
 */
object Memories {
    /** A player keeps his ten strongest memories. */
    const val KEEP = 10

    fun add(lg: League, p: Player, kind: String, text: String, teamId: Int, pull: Int) {
        if (p.memories.any { it.season == lg.season && it.kind == kind && it.teamId == teamId }) return
        p.memories += Memory(lg.season, kind, text, teamId, pull)
        while (p.memories.size > KEEP) {
            // The faintest goes first, the oldest among equals.
            val drop = p.memories.minWith(compareBy<Memory> { kotlin.math.abs(it.pull) }.thenBy { it.season })
            p.memories.remove(drop)
        }
    }

    /** How strongly his memories tie him to (or turn him against) a school. */
    fun pull(p: Player, teamId: Int): Int = p.memories.filter { it.teamId == teamId }.sumOf { it.pull }

    /** What his memories do to the odds he transfers: under 1 he's rooted, over 1 he's restless. */
    fun transferFactor(p: Player): Double =
        if (p.teamId < 0) 1.0 else Math.pow(0.85, pull(p, p.teamId).toDouble()).coerceIn(0.3, 2.5)

    /** In one line, for a profile or a story: the memory that weighs most. */
    fun defining(p: Player): Memory? = p.memories.maxWithOrNull(compareBy<Memory> { kotlin.math.abs(it.pull) }.thenBy { it.season })

    /** He picked this school: remembered for good if it was one of the few that wanted him. */
    fun committed(lg: League, r: Recruit, t: Team) {
        if (r.transfer) return
        val suitors = r.interest.count { it.value >= 40 }
        if (suitors <= 2 || r.p.stars <= 2) add(lg, r.p, "believed", "${t.school} recruited him when few others did", t.id, 3)
    }

    /** A rivalry game that got out of hand stays with the side that lost it. */
    fun rivalry(lg: League, g: Game, w: Team, l: Team) {
        if (kotlin.math.abs(g.hs - g.aws) < 18) return
        for (pid in l.roster) add(lg, lg.player(pid), "humbled", "Run off the floor by ${w.school} in ${lg.season}", l.id, 1)
    }

    /** The end of a season: titles won and lost, and how each player's year went. */
    fun seasonEnd(lg: League, championId: Int, runnerUpId: Int) {
        val champ = lg.team(championId)
        val second = lg.team(runnerUpId)
        for (pid in champ.roster) add(lg, lg.player(pid), "title", "Won the ${lg.season} national championship with ${champ.school}", champ.id, 3)
        for (pid in second.roster) add(lg, lg.player(pid), "runnerup", "Lost the ${lg.season} national title game to ${champ.school}", second.id, 1)
        for (b in lg.confBrackets) {
            if (b.champion < 0) continue
            val winner = lg.team(b.champion)
            for (pid in winner.roster) add(lg, lg.player(pid), "conftitle", "Won the ${lg.season} ${b.name}", winner.id, 2)
            val finalists = b.rounds.lastOrNull { round -> round.count { it >= 0 } == 2 } ?: continue
            val loser = finalists.firstOrNull { it >= 0 && it != b.champion }?.let { lg.team(it) } ?: continue
            for (pid in loser.roster) add(lg, lg.player(pid), "conffinal", "Lost the ${lg.season} ${b.name} final to ${winner.school}", loser.id, 1)
        }
        for (t in lg.teams) {
            val roster = lg.rosterOf(t)
            val scorer = roster.filter { it.stats.gp >= 10 }.maxByOrNull { it.stats.ppg }
            for (p in roster) {
                if (p.year >= 4) continue                      // seniors are done; nothing here changes what they do next
                val expected = Morale.expectedMinutes(lg, t, p)
                // A healthy year spent well short of the minutes his talent called for.
                if (p.stats.gp >= 8 && expected >= 14 && p.stats.mpg + 8 < expected) {
                    val before = p.memories.any { it.kind == "bench" && it.teamId == t.id && it.season == lg.season - 1 }
                    if (before) add(lg, p, "promise", "Waited two seasons at ${t.school} for a role that never came", t.id, -4)
                    else add(lg, p, "bench", "Spent ${lg.season} behind others at ${t.school}", t.id, -2)
                }
                if (p === scorer) add(lg, p, "goto", "${t.school} ran its offense through him in ${lg.season}", t.id, 1)
                if (p.year == 1) roster.filter { it.year >= 4 && it.pos == p.pos && it.ovr >= p.ovr }.maxByOrNull { it.ovr }?.let { senior ->
                    add(lg, p, "mentor", "Learned the college game from ${senior.name}", t.id, 1)
                }
            }
        }
    }
}
