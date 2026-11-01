package com.nh.hoopscoach.game

/**
 * Coaching and front-office changes, each offseason. Losing teams fire coaches (most after a
 * season under .350, some after missing the playoffs); a new coach brings his own system (an
 * offense and a defense) and resets the rotation. Bad teams also change general managers.
 * The user's team gets a pop-up when its coach changes.
 */
object Staffing {
    fun offseason(lg: League) {
        val ps = lg.proState ?: return
        ps.staffMoves.clear()
        for (t in lg.teams) {
            val missed = ProSeason.resultFor(lg, t.id).let { it == "Missed playoffs" || it.isEmpty() }
            val fire = when {
                t.winPct < 0.35 -> 0.6
                t.winPct < 0.45 && missed -> 0.25
                missed -> 0.08
                else -> 0.02
            }
            if (Rng.chance(fire)) newCoach(lg, t)
            if (t.winPct < 0.35 && Rng.chance(0.25)) {
                val old = ps.execs[t.id] ?: NbaAwards.executive(lg, t)
                val name = "${Rng.pick(Names.first)} ${Rng.pick(Names.last)}"
                ps.execs[t.id] = name
                ps.staffMoves += "The ${t.name} replace general manager $old with $name"
                lg.news += News(lg.season, 0, "Front office: the ${t.name} hire $name as general manager", t.id == lg.userTeamId)
            }
        }
    }

    private fun newCoach(lg: League, t: Team) {
        val ps = lg.proState ?: return
        val old = lg.coachOf(t)
        val c = Coach(lg.nextCoachId++, Rng.pick(Names.first), Rng.pick(Names.last), Rng.range(38, 62), t.id,
            Rng.range(55, 92), Rng.range(55, 92), Rng.range(40, 80), Rng.range(50, 90), Rng.int(0, 1_000_000))
        lg.coaches += c
        old.teamId = -1
        t.coachId = c.id
        // His system: an offense that fits his strengths, and a defense.
        t.plan.offense = if (c.off >= c.def) Rng.pick(listOf(Offense.UPTEMPO, Offense.PERIMETER, Offense.BALANCED)) else Rng.pick(listOf(Offense.GRIND, Offense.INSIDE, Offense.BALANCED))
        t.plan.defense = if (c.def >= 80) Rng.pick(listOf(Defense.MAN, Defense.PACKLINE)) else Rng.pick(listOf(Defense.MAN, Defense.ZONE, Defense.MAN))
        Gameplan.autoAllocate(lg, t)
        val line = "The ${t.name} fire ${old.name} after a ${t.record} season; ${c.name} takes over (${t.plan.offense.label}, ${t.plan.defense.label})"
        ps.staffMoves += line
        lg.news += News(lg.season, 0, "Coaching change: $line", t.id == lg.userTeamId)
        Media.post(lg, "NBA Insider", "nbainsider", "insider", "The ${t.name} are hiring ${c.name} as head coach, per sources.", Media.likes(lg, 0.02), verified = true)
        if (t.id == lg.userTeamId) Alerts.add(lg, "New coach", "${c.name} takes over the ${t.name}. He runs a ${t.plan.offense.label} offense and plays ${t.plan.defense.label}. Roles may change: check the Me tab.", "")
    }
}
