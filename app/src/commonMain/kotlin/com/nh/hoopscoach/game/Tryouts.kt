package com.nh.hoopscoach.game

import kotlin.math.roundToInt

/**
 * Walk-on tryouts. Every preseason a handful of students try out for the user's program. Their
 * ratings are hidden: the staff sees drill grades and a gut feeling, and can take a closer look at
 * two of them. Most are practice bodies, but once in a while one is a hidden gem who breaks out over
 * a summer and grows into a star.
 *
 * Walk-ons fill two spots on top of the 13 scholarships. A walk-on who earns it can be put on scholarship.
 */
object Tryouts {
    const val WALK_ON_SPOTS = 2
    const val CANDIDATES = 5
    const val LOOKS = 2
    /** Chance each candidate is a hidden gem: roughly one tryout in five has one. */
    const val GEM_CHANCE = 0.045
    /** Chance each other program finds a hidden gem at its own tryouts (about four a year league-wide). */
    const val AI_GEM_CHANCE = 0.012
    /** Overall at which a former walk-on counts as a star. */
    const val STAR = 80

    val DRILLS = listOf("Shooting", "Athleticism", "Scrimmage", "Coach's gut")
    val GRADES = listOf(
        listOf("Bricked everything", "Streaky", "Smooth stroke", "Lights out"),
        listOf("A step slow", "Average", "Quick twitch", "Explosive"),
        listOf("Overmatched", "Held his own", "Stood out", "Best on the floor"),
        listOf("Not a fit", "Practice body", "Could grow into something", "There's something there"),
    )

    fun scholarships(lg: League, t: Team) = t.roster.count { !lg.player(it).walkOn }
    fun walkOns(lg: League, t: Team) = t.roster.count { lg.player(it).walkOn }
    fun openWalkOnSpots(lg: League, t: Team) = (WALK_ON_SPOTS - walkOns(lg, t)).coerceAtLeast(0)
    fun openScholarships(lg: League, t: Team) = (Recruiting.MAX_ROSTER - scholarships(lg, t)).coerceAtLeast(0)

    /** How many tryout players the user can keep: walk-on spots plus any scholarship left unused. */
    fun capacity(lg: League) = openWalkOnSpots(lg, lg.user) + openScholarships(lg, lg.user)
    fun kept(os: OffseasonState) = os.tryouts.count { it.kept }

    /** Opens tryouts once signing day is done. */
    fun open(lg: League, os: OffseasonState) {
        os.tryouts.clear()
        repeat(CANDIDATES) { os.tryouts += candidate(lg, lg.user) }
        os.looks = LOOKS
        os.step = OffStep.TRYOUTS
    }

    private fun candidate(lg: League, t: Team): Tryout {
        val gem = Rng.chance(GEM_CHANCE)
        val year = if (gem) (if (Rng.chance(0.7)) 1 else 2) else Rng.weighted(listOf(1, 2, 3)) { when (it) { 1 -> 0.55; 2 -> 0.3; else -> 0.15 } }
        val pos = Rng.weighted(Pos.entries) { when (it) { Pos.PG -> .2; Pos.SG -> .24; Pos.SF -> .22; Pos.PF -> .19; Pos.C -> .15 } }
        val target = (42 + t.prestige * 0.05 + Rng.gauss() * 4).roundToInt().coerceIn(34, 56)
        val p = unrated(lg, pos, year, target, gem)
        return Tryout(p, grade(p, looked = false))
    }

    /** A student with no recruiting profile and no NIL deal. */
    fun unrated(lg: League, pos: Pos, year: Int, target: Int, gem: Boolean): Player {
        val p = LeagueGen.makePlayer(lg, pos, year, target, -1)
        p.ovrHistory.clear()
        p.ovrHistory += p.ovr
        p.pot = (p.ovr + Rng.range(2, 10)).coerceAtMost(99)
        p.stars = 1
        p.recruitRank = 0
        p.posRank = 0
        p.nil = 0
        p.gem = gem
        p.traits.remove(Trait.BAG_CHASER)
        return p
    }

    /** Unrecruited roster filler for a scholarship nobody signed. */
    fun filler(lg: League, t: Team): Player {
        val w = LeagueGen.walkOn(lg, t, LeagueGen.nearestPositionNeed(lg, t))
        w.year = 1
        return w
    }

    /** Other programs hold tryouts too. Once in a while one finds a gem, who replaces its weakest player. */
    fun aiTryout(lg: League, t: Team) {
        if (t.id == lg.userTeamId || !Rng.chance(AI_GEM_CHANCE)) return
        val cut = lg.rosterOf(t).minByOrNull { it.ovr } ?: return
        t.roster.remove(cut.id)
        lg.removePlayer(cut)
        val p = unrated(lg, cut.pos, 1, (42 + t.prestige * 0.05).roundToInt(), gem = true)
        p.teamId = t.id
        p.tryout = lg.season + 1
        p.number = Recruiting.freeNumber(lg, t)
        lg.addPlayer(p)
        t.roster += p.id
        // The cut player is gone, so the lineup can't point at him.
        Gameplan.validate(lg, t)
    }

    private fun level(v: Double) = ((v - 36) / 6.0).roundToInt().coerceIn(0, 3)

    /** Drill grades 0-3. A closer look cuts the noise and makes the gut read reliable. */
    fun grade(p: Player, looked: Boolean): IntArray {
        val noise = if (looked) 1.0 else 3.0
        val shoot = level((p[Attr.MID] + p[Attr.OUT]) / 2.0 + Rng.gauss() * noise)
        val ath = level((p[Attr.PER] + p[Attr.STL] + p[Attr.REB] + p[Attr.BLK]) / 4.0 + Rng.gauss() * noise)
        val scrim = level(p.ovr + Rng.gauss() * noise * 1.3)
        return intArrayOf(shoot, ath, scrim, gut(p, looked))
    }

    private fun gut(p: Player, looked: Boolean): Int {
        if (p.gem) return when {
            looked -> if (Rng.chance(0.9)) 3 else 2
            Rng.chance(0.55) -> 3
            Rng.chance(0.75) -> 2
            else -> 1
        }
        return when {
            !looked && Rng.chance(0.06) -> 3
            p.pot - p.ovr >= 8 -> if (Rng.chance(0.6)) 2 else 1
            p.ovr < 40 -> if (Rng.chance(0.6)) 0 else 1
            else -> 1
        }
    }

    /** Spend a closer look: shows his overall and sharpens every grade. */
    fun look(lg: League, i: Int): Boolean {
        val os = lg.offseason ?: return false
        val tr = os.tryouts.getOrNull(i) ?: return false
        if (tr.looked || os.looks <= 0) return false
        os.looks--
        tr.looked = true
        tr.notes = grade(tr.p, looked = true)
        return true
    }

    fun toggle(lg: League, i: Int): Boolean {
        val os = lg.offseason ?: return false
        val tr = os.tryouts.getOrNull(i) ?: return false
        if (tr.kept) { tr.kept = false; return true }
        if (kept(os) >= capacity(lg)) return false
        tr.kept = true
        return true
    }

    /** Cut a walk-on to open his spot. The portal is closed by now, so he simply leaves the program. */
    fun release(lg: League, pid: Int): Boolean {
        val t = lg.user
        val p = lg.playerOrNull(pid) ?: return false
        if (!p.walkOn || pid !in t.roster) return false
        t.roster.remove(pid)
        lg.removePlayer(p)
        lg.offseason?.released?.add(p.name)
        Gameplan.validate(lg, t)
        return true
    }

    /** Puts the kept candidates on the roster: walk-on spots first, then any unused scholarship. */
    fun finish(lg: League, os: OffseasonState) {
        val t = lg.user
        for (tr in os.tryouts.filter { it.kept }) {
            val p = tr.p
            p.walkOn = openWalkOnSpots(lg, t) > 0
            if (!p.walkOn && openScholarships(lg, t) <= 0) continue
            p.teamId = t.id
            p.tryout = lg.season + 1
            p.number = Recruiting.freeNumber(lg, t)
            p.happiness = 80
            lg.addPlayer(p)
            t.roster += p.id
            lg.news += News(lg.season + 1, 0, "${p.name} makes ${t.school} as a walk-on", true)
        }
        os.tryouts.clear()
    }

    /** Every summer, before growth: a hidden gem may break out and his ceiling jumps. */
    fun summer(p: Player): Boolean {
        if (!p.gem || p.story > 0 || p.year >= 4) return false
        if (p.year < 3 && !Rng.chance(0.7)) return false
        p.story = 1
        p.pot = maxOf(p.pot, Rng.range(84, 93))
        if (Trait.LATE_BLOOMER !in p.traits) p.traits += Trait.LATE_BLOOMER
        return true
    }

    fun breakoutNews(lg: League, t: Team, p: Player, before: Int) {
        lg.news += News(lg.season, lg.day, "Walk-on ${p.name} breaks out at ${t.school}: up ${p.ovr - before} to ${p.ovr} overall this summer", t.id == lg.userTeamId)
    }

    /** From walk-on to star, the first time a tryout player reaches [STAR]. */
    fun checkStar(lg: League, t: Team, p: Player) {
        if (p.tryout <= 0 || p.story >= 2 || p.ovr < STAR) return
        p.story = 2
        lg.news += News(lg.season, lg.day, "From walk-on to star: ${t.school}'s ${p.name} is now a ${p.ovr} overall", t.id == lg.userTeamId)
    }

    /** Put a walk-on on scholarship. He's thrilled, and often loyal for good. */
    fun awardScholarship(lg: League, pid: Int): Boolean {
        val t = lg.user
        val p = lg.playerOrNull(pid) ?: return false
        if (!p.walkOn || p.teamId != t.id || openScholarships(lg, t) <= 0) return false
        p.walkOn = false
        p.happiness = (p.happiness + 20).coerceAtMost(100)
        if (Trait.LOYAL !in p.traits && Trait.DISLOYAL !in p.traits && Rng.chance(0.5)) p.traits += Trait.LOYAL
        lg.news += News(lg.season, lg.day, "Former walk-on ${p.name} earns a scholarship at ${t.school}", true)
        return true
    }
}
