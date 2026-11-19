package com.nh.hoopscoach.game

import kotlinx.serialization.Serializable

/**
 * An NCAA case at one school, through the season: rumors (opened in the offseason), the investigation
 * (the school cooperates or stonewalls), the ruling, and an appeal.
 */
@Serializable
class NcaaCase(
    val teamId: Int,
    val what: String,
    var major: Boolean,
    val opened: Int,
    /** "rumor", "investigation", "ruling", "appeal", "closed". */
    var stage: String = "rumor",
    /** "", "cooperate", "stonewall". */
    var stance: String = "",
    var appealed: Boolean = false,
    /** How it ended, in a sentence. */
    var outcome: String = "",
)

/**
 * Scandals as a story. Rumors first; the NCAA opens an investigation a week into the season, and the
 * school cooperates (lighter penalties, more likely) or stonewalls (the case may fall apart for lack of
 * evidence, or come down harder). The ruling lands late in February, a major case can cost this March,
 * and an appeal is decided before Selection Sunday: a win lifts the ban or eases the scholarships, a loss
 * adds one.
 */
object NcaaCases {
    const val INVESTIGATION_DAY = 6
    val RULING_DAY = Schedule.REG_DAYS - 6
    val APPEAL_DAY = Schedule.REG_DAYS - 2

    fun open(lg: League, teamId: Int): NcaaCase? = lg.cases.firstOrNull { it.teamId == teamId && it.stage != "closed" }

    fun mine(lg: League): NcaaCase? = open(lg, lg.userTeamId)

    /** A school comes under suspicion: the rumors start. */
    fun open(lg: League, t: Team, major: Boolean, what: String = Rng.pick(listOf("recruiting violations", "improper benefits to players", "academic misconduct"))) {
        if (open(lg, t.id) != null) return
        lg.cases += NcaaCase(t.id, what, major, lg.season + if (lg.phase == Phase.OFFSEASON) 1 else 0)
        val mine = t.id == lg.userTeamId
        Timeline.record(lg, "rumor", "Rumors of $what at ${t.school}", "Reports surfaced of $what at ${t.school}. The NCAA hasn't said a word yet.", t.id, weight = 2)
        lg.news += News(lg.season, lg.day, "Report: questions about $what at ${t.school}", mine, kind = "move", teamId = t.id,
            body = "Nothing official yet, but people around the sport are talking. If the NCAA gets involved, it won't be quick.", source = "Hoops Wire")
        if (mine) Alerts.add(lg, "Rumors about your program", "Reports of $what at ${t.school} are out there. If the NCAA opens a case, you'll decide whether to cooperate or fight it.", "")
        Fans.ensure(t)
        t.fans = (t.fans - 3).coerceAtLeast(5.0)
    }

    /** The case moves on its calendar; computer-run schools pick their stance on their own. */
    fun daily(lg: League) {
        for (c in lg.cases.filter { it.stage != "closed" && it.opened <= lg.season }) {
            val t = lg.teams.getOrNull(c.teamId) ?: continue
            val mine = t.id == lg.userTeamId
            when {
                c.stage == "rumor" && lg.day >= INVESTIGATION_DAY -> {
                    c.stage = "investigation"
                    Timeline.record(lg, "investigation", "NCAA opens an investigation at ${t.school}", "The NCAA is investigating ${c.what} at ${t.school}.", t.id, weight = 2)
                    lg.news += News(lg.season, lg.day, "NCAA opens an investigation into ${t.school}", mine, kind = "move", teamId = t.id,
                        body = "The inquiry centers on ${c.what}. A ruling is expected before March.", source = "Hoops Wire")
                    if (mine) Alerts.add(lg, "The NCAA is investigating", "Cooperate or stonewall: decide on the Today screen before the ruling in late February.", "")
                    else c.stance = if (Rng.chance(0.6)) "cooperate" else "stonewall"
                }
                c.stage == "investigation" && lg.day >= RULING_DAY -> rule(lg, c, t)
                c.stage == "appeal" && lg.day >= APPEAL_DAY -> decideAppeal(lg, c, t)
            }
        }
    }

    /** The user's stance in the investigation. */
    fun choose(lg: League, stance: String) {
        val c = mine(lg) ?: return
        if (c.stage != "investigation" || c.stance.isNotEmpty()) return
        c.stance = stance
        val coach = lg.userCoach
        val t = lg.user
        if (stance == "cooperate") {
            coach.security = (coach.security - 4).coerceAtLeast(0)
            lg.news += News(lg.season, lg.day, "${t.school} says it is cooperating fully with the NCAA", true, kind = "move", teamId = t.id,
                body = "Cooperation tends to soften penalties. The athletic director called it the right thing to do.", source = "Hoops Wire")
        } else {
            lg.news += News(lg.season, lg.day, "${t.school} declines to cooperate with the NCAA inquiry", true, kind = "move", teamId = t.id,
                body = "${t.school}'s lawyers say the NCAA has no case. If they're wrong, the penalties will be steeper.", source = "Hoops Wire")
        }
    }

    private fun rule(lg: League, c: NcaaCase, t: Team) {
        if (c.stance.isEmpty()) c.stance = "cooperate"
        val coach = runCatching { lg.coachOf(t) }.getOrNull() ?: return
        val mine = t.id == lg.userTeamId
        if (c.stance == "stonewall" && Rng.chance(0.4)) {
            c.stage = "closed"
            c.outcome = "Dropped: the NCAA couldn't prove it."
            Timeline.record(lg, "cleared", "NCAA drops its case against ${t.school}", "Without enough evidence, the NCAA closed its investigation into ${c.what} at ${t.school}.", t.id, weight = 2)
            lg.news += News(lg.season, lg.day, "NCAA closes its case against ${t.school} without penalties", mine, kind = "move", teamId = t.id,
                body = "Stonewalling worked. The investigation into ${c.what} ran out of evidence.", source = "Hoops Wire")
            if (mine) Alerts.add(lg, "Case closed", "The NCAA couldn't prove it. No penalties.", "")
            return
        }
        // Cooperating can soften a major case; stonewalling and losing makes any case major.
        c.major = when (c.stance) {
            "cooperate" -> c.major && !Rng.chance(0.5)
            else -> true
        }
        if (c.stance == "stonewall") coach.security = (coach.security - 10).coerceAtLeast(0)
        WorldEvents.scandal(lg, t, coach, c.major, c.what)
        c.stage = if (mine) "ruling" else if (Rng.chance(0.3)) "appeal".also { c.appealed = true } else "closed"
        c.outcome = if (c.major) "Major penalties, including a postseason ban." else "Scholarships taken away."
        if (!mine && c.stage == "closed") return
    }

    /** Season's end: nothing stays open past it (an unanswered ruling is accepted). */
    fun seasonEnd(lg: League) {
        for (c in lg.cases.filter { it.stage != "closed" && it.opened <= lg.season }) {
            val t = lg.teams.getOrNull(c.teamId) ?: continue
            when (c.stage) {
                "rumor" -> { c.stage = "closed"; c.outcome = "The rumors faded." }
                "investigation" -> rule(lg, c, t)
                "appeal" -> decideAppeal(lg, c, t)
            }
            if (c.stage == "ruling") { c.stage = "closed"; c.outcome += " Accepted." }
            if (c.stage == "appeal") decideAppeal(lg, c, t)
        }
        while (lg.cases.size > 60) lg.cases.removeAt(0)
    }

    /** The user appeals the ruling (once). */
    fun appeal(lg: League) {
        val c = mine(lg) ?: return
        if (c.stage != "ruling" || c.appealed) return
        c.appealed = true
        c.stage = "appeal"
        lg.news += News(lg.season, lg.day, "${lg.user.school} appeals its NCAA penalties", true, kind = "move", teamId = lg.userTeamId,
            body = "A decision is expected before Selection Sunday.", source = "Hoops Wire")
    }

    /** The user accepts the ruling and moves on. */
    fun accept(lg: League) {
        val c = mine(lg) ?: return
        if (c.stage == "ruling") { c.stage = "closed"; c.outcome += " Accepted." }
    }

    private fun decideAppeal(lg: League, c: NcaaCase, t: Team) {
        val mine = t.id == lg.userTeamId
        val odds = if (c.stance == "cooperate") 0.4 else 0.25
        if (Rng.chance(odds)) {
            if (t.banSeason >= lg.season) t.banSeason = 0 else t.scholarshipsLost = (t.scholarshipsLost - 1).coerceAtLeast(0)
            c.outcome = "Appeal won: " + if (c.major) "the postseason ban is lifted." else "a scholarship back."
            Timeline.record(lg, "appeal", "${t.school} wins its NCAA appeal", c.outcome, t.id, weight = 2)
            lg.news += News(lg.season, lg.day, "${t.school} wins its appeal", mine, kind = "move", teamId = t.id, body = c.outcome, source = "Hoops Wire")
            if (mine) Alerts.add(lg, "Appeal won", c.outcome, "")
        } else {
            t.scholarshipsLost = (t.scholarshipsLost + 1).coerceAtMost(4)
            Fans.ensure(t)
            t.fans = (t.fans - 3).coerceAtLeast(5.0)
            c.outcome = "Appeal denied: one more scholarship lost."
            lg.news += News(lg.season, lg.day, "NCAA denies ${t.school}'s appeal", mine, kind = "move", teamId = t.id, body = c.outcome, source = "Hoops Wire")
            if (mine) Alerts.add(lg, "Appeal denied", "The penalties stand, and the committee added a scholarship.", "")
        }
        c.stage = "closed"
    }
}
