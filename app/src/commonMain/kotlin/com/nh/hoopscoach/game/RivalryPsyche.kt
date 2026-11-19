package com.nh.hoopscoach.game

/**
 * How each side walks into a rivalry game, from the rivalry's history and from how you've handled it.
 *
 * A team that lost the last meeting comes in hungry; lost it badly, or lost several in a row, and it's out for
 * blood. A team that keeps winning can get complacent. What you say matters too: call them out and you hand them
 * bulletin-board material; downplay it and they have less to play for. A heated rivalry-coach storyline (and a
 * guaranteed win) fires them up as well.
 *
 * Each side gets a mindset from -2 (flat) to +3 (out for blood). Every point is a little better shooting and a
 * little better ball security in that game, so the psychology is felt on the floor, not just in the preview.
 */
object RivalryPsyche {
    /** Your stance going into a rivalry game. */
    enum class Stance(val label: String, val hint: String) {
        DOWNPLAY("Downplay it", "\"It's one game on the schedule.\" Takes the edge off them, and off you."),
        EMBRACE("Embrace it", "\"This is why you come here.\" Your team is fired up. So are they."),
        CALL_OUT("Call them out", "Bulletin-board material: your fans love it, and they'll be out for blood."),
    }

    class Mind(val score: Int, val reasons: List<String>) {
        val label: String get() = when {
            score >= 3 -> "Out for blood"; score == 2 -> "Hungry"; score == 1 -> "Fired up"
            score == 0 -> "Locked in"; score == -1 -> "Complacent"; else -> "Flat"
        }
    }

    /** Made-shot chance per point of mindset. */
    const val MAKE = 0.004
    /** Turnover chance saved per point of mindset. */
    const val TOV = 0.003

    fun stance(lg: League, g: Game): Stance? = lg.rivalryStance[g.id]?.let { Stance.entries.getOrNull(it) }

    fun setStance(lg: League, g: Game, s: Stance) {
        val before = stance(lg, g)
        lg.rivalryStance[g.id] = s.ordinal
        if (s == Stance.CALL_OUT && before != Stance.CALL_OUT) {
            lg.user.fans = (lg.user.fans + 2).coerceAtMost(100.0)
            // It feeds the rival-coach storyline, if there is one.
            lg.storylines.firstOrNull { it.season == lg.season && it.kind == "coachrival" && it.b == g.opp(lg.userTeamId) && !it.done }?.let { it.heat = (it.heat + 1).coerceAtMost(9) }
        }
        // Old games' stances aren't needed.
        if (lg.rivalryStance.size > 40) lg.rivalryStance.keys.filter { id -> lg.games.none { it.id == id && !it.played } }.forEach { lg.rivalryStance.remove(it) }
    }

    /** [teamId]'s mindset for rivalry game [g] (zero, no reasons, if it isn't one). */
    fun mind(lg: League, g: Game, teamId: Int): Mind {
        val r = lg.rivalry(g) ?: return Mind(0, emptyList())
        val opp = g.opp(teamId)
        var s = 0
        val why = mutableListOf<String>()
        val oppName = name(lg, lg.team(opp))
        // The last meeting.
        if (r.lastWinner >= 0) {
            if (r.lastWinner == opp) {
                if (r.lastMargin >= 15) { s += 2; why += "embarrassed by ${r.lastMargin} last time" } else { s += 1; why += "lost the last meeting" }
            }
        }
        // A streak in the series.
        if (r.streak >= 3) {
            if (r.streakTeam == opp) { s += 1; why += "$oppName has won ${r.streak} straight" }
            else if (r.streakTeam == teamId) { s -= 1; why += "won ${r.streak} straight, and it shows" }
        }
        // The other side owns the series.
        val mine = r.winsFor(teamId)
        val theirs = r.winsFor(opp)
        if (theirs - mine >= 8) { s += 1; why += "trails the series $mine-$theirs" }
        // What the user said, and the storylines around it.
        val user = lg.userTeamId
        if (g.involves(user)) {
            val st = stance(lg, g)
            if (teamId == user) when (st) {
                Stance.EMBRACE, Stance.CALL_OUT -> { s += 1; why += if (st == Stance.CALL_OUT) "backing up the coach's words" else "the coach embraced it" }
                Stance.DOWNPLAY -> { s -= 1; why += "the coach played it down" }
                null -> {}
            } else when (st) {
                Stance.CALL_OUT -> { s += 2; why += "bulletin-board material from ${lg.userCoach.last}" }
                Stance.EMBRACE -> { s += 1; why += "the rivalry's in the air" }
                Stance.DOWNPLAY -> { s -= 1; why += "nothing to get worked up about" }
                null -> {}
            }
            lg.storylines.firstOrNull { it.season == lg.season && it.kind == "coachrival" && it.b == (if (teamId == user) opp else teamId) }?.let { sl ->
                if (teamId != user && sl.heat >= 4) { s += 1; why += "their coach's war of words with yours" }
                if (teamId != user && sl.flag == "guarantee") { s += 1; why += "you guaranteed a win" }
            }
        }
        return Mind(s.coerceIn(-2, 3), why)
    }

    private fun name(lg: League, t: Team) = if (lg.pro) t.name else t.school

    /** After a rivalry game: the streak and the last margin, for next time. */
    fun afterGame(r: Rivalry, g: Game) {
        val w = g.winner()
        if (r.streakTeam == w) r.streak++ else { r.streakTeam = w; r.streak = 1 }
        r.lastWinner = w
        r.lastMargin = kotlin.math.abs(g.hs - g.aws)
    }
}
