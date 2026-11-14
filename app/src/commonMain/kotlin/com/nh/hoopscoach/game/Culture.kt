package com.nh.hoopscoach.game

import kotlin.math.roundToInt

/**
 * The locker room: captains the players vote for, the team's chemistry, and what both do. Good captains
 * pull unhappy teammates back up; a fractured room drags everyone down and costs a little on the floor.
 * Also mentor pairings (a junior or senior bringing along a freshman at his position) and the season's
 * opening night, Midnight Madness.
 */
object Culture {
    /** How a player looks to his teammates as a leader (0-100). */
    fun leadership(lg: League, t: Team, p: Player): Int {
        var v = 18.0 + p.year * 9 + (p.ovr - 60) * 0.5 + (p.happiness - 50) * 0.15
        v += Tactics.plannedMinutes(t, p.id) * 0.4
        if (p.has(Trait.TEAM_PLAYER)) v += 14
        if (p.has(Trait.LOYAL)) v += 8
        if (p.has(Trait.GYM_RAT)) v += 7
        if (p.has(Trait.PASS_FIRST)) v += 5
        if (p.has(Trait.DISLOYAL)) v -= 16
        if (p.has(Trait.AMBITIOUS)) v -= 5
        if (p.has(Trait.GUNNER)) v -= 6
        if (p.walkOn) v -= 6
        return v.roundToInt().coerceIn(0, 100)
    }

    /** A player sulking in a way that spreads: unhappy, and the kind who lets everyone know. */
    fun badApple(p: Player) = p.happiness < 40 && (p.has(Trait.DISLOYAL) || p.has(Trait.AMBITIOUS) || p.has(Trait.BAG_CHASER) || p.happiness < 25)

    /** The captains' vote, once a season: the top two (or one, if nobody's close). */
    fun ensureCaptains(lg: League, t: Team) {
        if (lg.pro || t.captainSeason == lg.season || t.roster.size < 5) return
        if (t.id == lg.userTeamId) tidyMentors(lg)
        val roster = lg.rosterOf(t).filter { it.year > 0 }
        if (roster.isEmpty()) return
        val score = roster.associateWith { leadership(lg, t, it) + Rng.gauss() * 6 }
        // Each player votes for the two he respects most (never himself).
        val votes = HashMap<Int, Int>()
        for (voter in roster) {
            score.entries.filter { it.key.id != voter.id }.sortedByDescending { it.value + Rng.gauss() * 8 }.take(2)
                .forEach { votes[it.key.id] = (votes[it.key.id] ?: 0) + 1 }
        }
        val ranked = votes.entries.sortedByDescending { it.value }
        val picks = ranked.take(2).filter { it.value >= roster.size / 3 || it == ranked.first() }
        t.captains = picks.map { it.key }.toMutableList()
        t.captainVotes = picks.map { it.value }.toMutableList()
        t.captainSeason = lg.season
        if (t.id == lg.userTeamId && !lg.playerMode) {
            val names = t.captains.mapNotNull { lg.playerOrNull(it)?.name }
            lg.news += News(lg.season, lg.day, "${t.school} players vote ${names.joinToString(" and ")} as captain${if (names.size == 1) "" else "s"}", true,
                kind = "column", teamId = t.id, body = "The vote: " + t.captains.indices.joinToString(", ") { i -> "${names.getOrNull(i)} ${t.captainVotes[i]} of ${roster.size}" } + ".",
                source = "Hoops Wire")
        }
    }

    /** The captains' leadership, averaged (0 with none). */
    fun captainLeadership(lg: League, t: Team): Int {
        val cs = t.captains.mapNotNull { id -> lg.playerOrNull(id)?.takeIf { it.teamId == t.id } }
        return if (cs.isEmpty()) 0 else cs.map { leadership(lg, t, it) }.average().roundToInt()
    }

    /** Team chemistry, 0-100: the rotation's mood, the captains, bad apples, and players who've been through it together. */
    fun chemistry(lg: League, t: Team): Int {
        val roster = lg.rosterOf(t).filter { it.year > 0 }
        if (roster.isEmpty()) return 50
        var wSum = 0.0
        var hSum = 0.0
        for (p in roster) {
            val w = 6.0 + Tactics.plannedMinutes(t, p.id)
            wSum += w
            hSum += p.happiness * w
        }
        var v = hSum / wSum
        v += (captainLeadership(lg, t) - 55) * 0.25
        v -= roster.count { badApple(it) } * 7
        v += roster.count { it.has(Trait.TEAM_PLAYER) } * 1.5
        v += roster.count { it.year >= 3 } * 0.8
        return v.roundToInt().coerceIn(0, 100)
    }

    fun label(chem: Int) = when {
        chem >= 78 -> "Tight-knit"
        chem >= 60 -> "Together"
        chem >= 42 -> "Uneasy"
        chem >= 25 -> "Fractured"
        else -> "Toxic"
    }

    /**
     * Where a player's mood is pulled each day by the room: strong captains lift the unhappy, a broken
     * locker room pulls everyone down, and a mentor enjoys the role.
     */
    fun moodPull(lg: League, t: Team, p: Player, chem: Int, lead: Int): Double {
        var v = 0.0
        if (p.happiness < 55 && lead >= 55 && p.id !in t.captains) v += (lead - 50) * 0.18
        if (chem < 40) v -= (40 - chem) * 0.2
        if (t.roster.any { lg.playerOrNull(it)?.mentorId == p.id }) v += 3
        return v
    }

    /** The edge chemistry gives on the floor: added make chance and fewer turnovers (both small). */
    fun gameEdge(chem: Int): Pair<Double, Double> = ((chem - 50) * 0.0002) to ((chem - 50) * 0.00012)

    // ---------- mentors ----------

    /** Who can mentor [fresh]: a junior or senior at his position, not already mentoring someone. */
    fun mentorOptions(lg: League, t: Team, fresh: Player): List<Player> = lg.rosterOf(t).filter { m ->
        m.year >= 3 && m.pos == fresh.pos && m.id != fresh.id && t.roster.none { lg.playerOrNull(it)?.mentorId == m.id && it != fresh.id }
    }.sortedByDescending { leadership(lg, t, it) }

    /** A freshman's practice growth with a mentor on the roster. */
    fun mentorBoost(lg: League, p: Player): Double {
        if (p.mentorId < 0) return 1.0
        val m = lg.playerOrNull(p.mentorId)?.takeIf { it.teamId == p.teamId } ?: return 1.0
        p.mentoredSeason = lg.season
        return 1.2 + (if (m.has(Trait.GYM_RAT)) 0.1 else 0.0) + (if (m.has(Trait.TEAM_PLAYER)) 0.05 else 0.0)
    }

    /** The offseason jump for a player who had a mentor this season. */
    fun summerBoost(lg: League, p: Player): Double = if (p.mentoredSeason == lg.season) 1.15 else 1.0

    /** Pairings end when either one has moved on, and at each new season for anyone no longer a freshman. */
    fun tidyMentors(lg: League) {
        for (p in lg.players) if (p.mentorId >= 0) {
            val m = lg.playerOrNull(p.mentorId)
            if (m == null || m.teamId != p.teamId || p.teamId < 0 || p.year > 1) p.mentorId = -1
        }
    }

    // ---------- Midnight Madness ----------

    class Show(val id: Int, val name: String, val cost: Long, val fans: Double, val buzz: Double, val recruits: Int, val blurb: String)

    val SHOWS = listOf(
        Show(0, "Low-key", 0, 2.0, 1.0, 1, "Open practice and a scrimmage. Free."),
        Show(1, "Full show", 150, 6.0, 5.0, 4, "Lights, intros, a dunk contest and the recruits on your board in the front row."),
        Show(2, "Spectacle", 400, 10.0, 9.0, 8, "A celebrity DJ, pyro and a sold-out arena. The talk of the recruiting trail."),
    )

    /** Shows only before the season's first game, once a season, for the user's program. */
    fun madnessOpen(lg: League) = !lg.pro && !lg.playerMode && lg.phase == Phase.REGULAR && lg.user.gp == 0 && lg.madnessSeason != lg.season

    /** The program's hype going in: buzz and fans. Scales what the night does. */
    fun hype(t: Team): Double {
        Fans.ensure(t)
        return 0.6 + ((if (t.buzz < 0) 50.0 else t.buzz) + t.fans) / 250.0
    }

    /** Puts on the show. Returns what happened, or null if it can't be afforded. */
    fun midnightMadness(lg: League, id: Int): String? {
        val s = SHOWS.getOrNull(id) ?: return null
        val t = lg.user
        Business.ensure(lg, t)
        if (t.cash < s.cost) return null
        Brand.ensure(lg)
        t.cash -= s.cost
        val k = hype(t)
        Fans.ensure(t)
        t.fans = (t.fans + s.fans * k).coerceAtMost(100.0)
        t.buzz = (t.buzz + s.buzz * k).coerceAtMost(100.0)
        val u = t.id
        val bump = (s.recruits * k).roundToInt()
        val impressed = lg.recruits.filter { it.committed < 0 && (it.onBoard || it.interest.containsKey(u)) }
        for (r in impressed) r.interest[u] = ((r.interest[u] ?: 4) + bump).coerceAtMost(100)
        val crowd = (t.arena * (0.55 + 0.15 * id + k * 0.2)).roundToInt().coerceAtMost(t.arena)
        val note = "${s.name}: ${"%,d".format(crowd)} fans packed in" +
            (if (impressed.isNotEmpty()) ", and ${impressed.size} recruit${if (impressed.size == 1) "" else "s"} on your board came away impressed (+$bump interest)." else ".")
        lg.madnessSeason = lg.season
        lg.madnessNote = note
        lg.news += News(lg.season, lg.day, "Midnight Madness at ${t.school}: ${if (id == 2) "a spectacle" else if (id == 1) "the season tips off in style" else "a quiet start"}", true,
            kind = "column", teamId = t.id, body = note, source = "Hoops Wire")
        return note
    }
}
