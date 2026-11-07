package com.nh.hoopscoach.game

/**
 * Coaching and front-office changes, each offseason. Losing teams fire coaches (most after a season
 * under .350, some after missing the playoffs) and the oldest ones retire — then the carousel spins:
 * each opening is first offered to the best up-and-coming coach at a worse job, who takes it and
 * leaves a seat of his own, and only when nobody worth poaching is left does a team hand the keys to
 * a new name. A new coach brings his own system (an offense and a defense) and resets the rotation.
 * Bad teams also change general managers. The user's team gets a pop-up when its coach changes.
 */
object Staffing {
    fun offseason(lg: League) {
        val ps = lg.proState ?: return
        ps.staffMoves.clear()
        val openings = ArrayDeque<Team>()
        for (t in lg.teams) {
            val c = lg.coachOf(t)
            c.age++
            val missed = ProSeason.resultFor(lg, t.id).let { it == "Missed playoffs" || it.isEmpty() }
            val fire = when {
                t.winPct < 0.35 -> 0.6
                t.winPct < 0.45 && missed -> 0.25
                missed -> 0.08
                else -> 0.02
            }
            val retire = c.age >= 70 && Rng.chance(0.3) || c.age >= 74
            // Coaching an NBA team: the user's job is decided by the owner (NbaCoach), not here.
            val userCoached = NbaCoach.active(lg) && t.id == lg.userTeamId
            if (userCoached) continue
            if (retire || Rng.chance(fire)) {
                val line = if (retire) "The ${t.name}'s ${c.name} retires after ${c.w}-${c.l} and ${c.titles} ${if (c.titles == 1) "title" else "titles"}"
                    else "The ${t.name} fire ${c.name} after a ${t.record} season"
                ps.staffMoves += line
                lg.news += News(lg.season, 0, "Coaching change: $line", t.id == lg.userTeamId)
                c.teamId = -1
                openings += t
            }
            if (t.winPct < 0.35 && Rng.chance(0.25)) {
                val old = ps.execs[t.id] ?: NbaAwards.executive(lg, t)
                val name = "${Rng.pick(Names.first)} ${Rng.pick(Names.last)}"
                ps.execs[t.id] = name
                ps.staffMoves += "The ${t.name} replace general manager $old with $name"
                lg.news += News(lg.season, 0, "Front office: the ${t.name} hire $name as general manager", t.id == lg.userTeamId)
            }
        }
        // The carousel: best jobs first, each tries to hire a coach away from a worse team.
        val queue = ArrayDeque(openings.sortedByDescending { it.winPct })
        val moved = mutableSetOf<Int>()
        var poaches = 0
        while (queue.isNotEmpty() && poaches < 5) {
            val t = queue.removeFirst()
            // Who can they get? A sitting coach good enough to want, on a job not clearly better than this one.
            val target = lg.teams.filter { it.id != t.id && it.winPct < t.winPct + 0.1 && !(NbaCoach.active(lg) && it.id == lg.userTeamId) }
                .map { it to lg.coachOf(it) }
                .filter { it.second.teamId == it.first.id && it.second.id !in moved && it.second.rating >= 58 }
                .maxByOrNull { it.second.rating + it.second.winPct * 10 } ?: break
            val (oldTeam, coach) = target
            hire(lg, t, coach, poachedFrom = oldTeam)
            moved += coach.id
            poaches++
            // His old seat opens for the next spin of the carousel.
            queue += oldTeam
        }
        while (queue.isNotEmpty()) newCoach(lg, queue.removeFirst())
    }

    /** Puts [coach] on [t]'s bench and resets the system to his strengths. [poachedFrom] is the team he left, if any. */
    private fun hire(lg: League, t: Team, c: Coach, poachedFrom: Team?) {
        val ps = lg.proState ?: return
        c.teamId = t.id
        t.coachId = c.id
        t.plan.offense = if (c.off >= c.def) Rng.pick(listOf(Offense.UPTEMPO, Offense.PERIMETER, Offense.BALANCED)) else Rng.pick(listOf(Offense.GRIND, Offense.INSIDE, Offense.BALANCED))
        t.plan.defense = if (c.def >= 80) Rng.pick(listOf(Defense.MAN, Defense.PACKLINE)) else Rng.pick(listOf(Defense.MAN, Defense.ZONE, Defense.MAN))
        Gameplan.autoAllocate(lg, t)
        val line = if (poachedFrom != null) "The ${t.name} hire ${c.name} away from the ${poachedFrom.name} (${t.plan.offense.label}, ${t.plan.defense.label})"
            else "The ${t.name} hire ${c.name} as head coach (${t.plan.offense.label}, ${t.plan.defense.label})"
        ps?.staffMoves?.add(line)
        lg.news += News(lg.season, 0, "Coaching change: $line", t.id == lg.userTeamId || poachedFrom?.id == lg.userTeamId)
        Media.post(lg, "NBA Insider", "nbainsider", "insider", "The ${t.name} are hiring ${c.name} as head coach, per sources.", Media.likes(lg, 0.02), verified = true)
        if (t.id == lg.userTeamId) Alerts.add(lg, "New coach", "${c.name} takes over the ${t.name}. He runs a ${t.plan.offense.label} offense and plays ${t.plan.defense.label}. Roles may change: check the Me tab.", "")
    }

    /** No coach worth poaching: a new name takes the job. */
    private fun newCoach(lg: League, t: Team) {
        val old = lg.coachOf(t)
        if (old.teamId == t.id) old.teamId = -1
        val c = Coach(lg.nextCoachId++, Rng.pick(Names.first), Rng.pick(Names.last), Rng.range(38, 62), t.id,
            Rng.range(55, 92), Rng.range(55, 92), Rng.range(40, 80), Rng.range(50, 90), Rng.int(0, 1_000_000))
        lg.coaches += c
        hire(lg, t, c, poachedFrom = null)
    }
}
