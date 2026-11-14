package com.nh.hoopscoach.game

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

object LeagueGen {

    fun newLeague(startYear: Int = 2026): League {
        val teams = mutableListOf<Team>()
        val confs = mutableListOf<Conference>()
        Schools.conferences.forEachIndexed { ci, c ->
            val conf = Conference(ci, c.name, c.abbr, c.tier, mutableListOf())
            for (s in Schools.parse(c)) {
                val prestige = (s.prestige + Rng.range(-2, 2)).coerceIn(5, 99)
                val t = Team(teams.size, s.name, s.nickname, s.abbr, ci, prestige, s.primary, s.secondary, 0)
                t.prevPrestige = prestige
                teams += t
                conf.teamIds += t.id
            }
            confs += conf
        }
        val lg = League(
            season = startYear,
            teams = teams,
            players = mutableListOf(),
            coaches = mutableListOf(),
            confs = confs,
            userTeamId = 0,
            userCoachId = -1,
            nextPlayerId = 1,
            nextCoachId = 1,
        )
        lg.reindex()
        for (t in teams) {
            val c = makeCoach(lg, t.prestige, Rng.range(36, 68), withCareer = true)
            c.teamId = t.id
            t.coachId = c.id
            buildRoster(lg, t)
        }
        lg.reindex()
        AlternateHistory.seedCollege(lg)
        return lg
    }

    fun makeCoach(lg: League, prestige: Int, age: Int, withCareer: Boolean): Coach {
        val base = 42 + prestige * 0.42
        fun r() = (base + Rng.gauss() * 8).roundToInt().coerceIn(30, 99)
        val first = Rng.pick(Names.first)
        val last = Rng.pick(Names.last)
        val c = Coach(lg.nextCoachId++, first, last, age, -1, r(), r(), r(), r(), Rng.int(0, 1_000_000))
        if (withCareer) {
            val years = ((age - 32) / 1.4).toInt().coerceAtLeast(0)
            val rate = (0.42 + prestige / 280.0 + Rng.gauss() * 0.06).coerceIn(0.3, 0.82)
            val gp = years * 31
            c.w = (gp * rate).roundToInt()
            c.l = gp - c.w
            c.tourneys = (years * (prestige / 110.0) * (0.7 + Rng.d() * 0.5)).roundToInt().coerceAtMost(years)
            c.finalFours = if (prestige > 80) (c.tourneys * Rng.d() * 0.25).roundToInt() else if (prestige > 60 && Rng.chance(0.2)) 1 else 0
            c.titles = if (c.finalFours > 0 && prestige > 85 && Rng.chance(0.45)) Rng.range(1, (c.finalFours / 2).coerceAtLeast(1)) else 0
            c.confTitles = (years * (prestige / 250.0) * Rng.d()).roundToInt()
        }
        lg.coaches += c
        return c
    }

    private val ROSTER_POS = listOf(Pos.PG, Pos.PG, Pos.SG, Pos.SG, Pos.SG, Pos.SF, Pos.SF, Pos.SF, Pos.PF, Pos.PF, Pos.PF, Pos.C, Pos.C)
    /**
     * Within-roster talent shape. Index 0 is the star, 1 the sidekick, then starters taper into
     * role players and a deep bench. The old flat +8..+2 for the first five packed every blue-blood
     * with four or five near-stars; college teams should feel top-heavy, not five deep of 85+.
     */
    private val QUALITY = intArrayOf(10, 2, -2, -5, -7, -9, -11, -13, -15, -16, -17, -18, -19)

    private fun buildRoster(lg: League, t: Team) {
        // Centered so a mid-major sits in the mid-60s and a blue blood's star still reaches the low 90s.
        val q = 60 + t.prestige * 0.25
        val positions = ROSTER_POS.toMutableList()
        // Star and sidekick land first (random positions), then the rest of the depth chart.
        val firstFive = Pos.entries.shuffled(Rng.r)
        val order = mutableListOf<Pos>()
        order += firstFive
        firstFive.forEach { positions.remove(it) }
        order += positions.shuffled(Rng.r)
        val years = mutableListOf(1, 1, 1, 2, 2, 2, 3, 3, 3, 4, 4, 4, Rng.range(1, 4)).shuffled(Rng.r)
        val numbers = (0..55).shuffled(Rng.r).toMutableList()
        order.forEachIndexed { i, pos ->
            val target = (q + QUALITY[i] + Rng.gauss() * 3).roundToInt().coerceIn(35, 95)
            val p = makePlayer(lg, pos, years[i], target, t.id)
            p.number = numbers.removeAt(0)
            lg.addPlayer(p)
            t.roster += p.id
        }
        Tactics.autoAllocate(lg, t)
        Shoes.assignApparel(t, force = true)
        Morale.setInitialNil(lg, t)
        Shoes.seedRoster(lg, t)
    }

    /** A typical age for a class: high school seniors 17 or 18, freshmen 18 or 19, and a year more each class after. */
    fun collegeAge(year: Int) = when (year.coerceIn(0, 5)) { 0 -> Rng.range(17, 18); 1 -> Rng.range(18, 19); 2 -> Rng.range(19, 20); 3 -> Rng.range(20, 21); 4 -> Rng.range(21, 22); else -> Rng.range(22, 23) }

    fun makePlayer(lg: League, pos: Pos, year: Int, target: Int, teamId: Int): Player {
        val style = Rng.pick(Ratings.styles(pos))
        val intl = Rng.chance(0.07)
        val country = if (intl) Rng.pick(Names.intlFirst.keys.toList()) else null
        val first = if (country != null) Rng.pick(Names.intlFirst.getValue(country)) else Rng.pick(Names.first)
        val last = if (country != null) Rng.pick(Names.intlLast.getValue(country)) else Rng.pick(Names.last)
        val height = Ratings.heightFor(pos)
        // Headroom is real but rare: most players climb a few points, not a full tier.
        val growthLeft = when (year) { 1 -> Rng.range(4, 12); 2 -> Rng.range(3, 9); 3 -> Rng.range(1, 6); else -> Rng.range(0, 3) }
        val p = Player(
            id = lg.nextPlayerId++,
            first = first, last = last, pos = pos, year = year,
            height = height, weight = Ratings.weightFor(height),
            attrs = Ratings.build(pos, style, target),
            pot = (target + growthLeft).coerceAtMost(99),
            style = style, focus = style,
            teamId = teamId, number = Rng.int(0, 56),
            hometown = country ?: Rng.pick(Names.towns),
            highSchool = "${Rng.pick(Names.hsPrefix)} ${Rng.pick(Names.hsSuffix)}",
            country = country,
            look = Rng.int(0, 1_000_000),
            traits = randomTraits(),
        )
        // Back-fill how they've grown since high school.
        val ovr = p.ovr
        val perYear = Rng.range(2, 6)
        val hs = (ovr - (year - 1) * perYear - Rng.range(0, 3)).coerceAtLeast(35)
        p.ovrHistory += hs
        for (y in 1 until year) p.ovrHistory += (hs + (ovr - hs) * y / year.toDouble()).roundToInt()
        p.stars = when { hs >= 79 -> 5; hs >= 69 -> 4; hs >= 56 -> 3; else -> 2 }
        p.recruitRank = when (p.stars) { 5 -> Rng.range(1, 25); 4 -> Rng.range(26, 120); 3 -> Rng.range(121, 400); else -> Rng.range(401, 800) }
        p.posRank = (p.recruitRank / 5.0 + Rng.range(-3, 3)).roundToInt().coerceAtLeast(1)
        p.happiness = Rng.range(55, 85)
        p.age = collegeAge(year)
        // HS recruits get a second star pass in recruitClass before the GOAT roll.
        if (year != 0) Builds.maybeGoatShape(p) else Builds.stampFlavor(p)
        p.pos2 = Ratings.secondFor(pos, p.attrs)
        return p
    }

    fun randomTraits(): MutableList<Trait> {
        val t = mutableListOf<Trait>()
        val pool = listOf(
            Trait.AMBITIOUS to 0.15, Trait.BAG_CHASER to 0.12, Trait.DISLOYAL to 0.07, Trait.LOYAL to 0.12,
            Trait.TEAM_PLAYER to 0.12, Trait.GYM_RAT to 0.12, Trait.CLUTCH to 0.08,
            Trait.GUNNER to 0.14, Trait.PASS_FIRST to 0.14,
        ).shuffled(Rng.r)
        for ((tr, p) in pool) {
            if (t.size >= 2) break
            if (tr == Trait.LOYAL && Trait.DISLOYAL in t) continue
            if (tr == Trait.DISLOYAL && Trait.LOYAL in t) continue
            if (tr == Trait.GUNNER && Trait.PASS_FIRST in t) continue
            if (tr == Trait.PASS_FIRST && Trait.GUNNER in t) continue
            if (Rng.chance(p)) t += tr
        }
        return t
    }

    /** A new high-school class. Ranked best to worst. */
    fun recruitClass(lg: League): MutableList<Recruit> {
        val n = Recruiting.CLASS_SIZE
        val out = mutableListOf<Recruit>()
        val posCount = HashMap<Pos, Int>()
        for (rank in 1..n) {
            val x = 1.0 - (rank - 1).toDouble() / (n - 1)
            val ovr = (48 + 36 * x.pow(2.2) + Rng.gauss() * 1.5).roundToInt().coerceIn(40, 87)
            val pos = Rng.weighted(Pos.entries) { when (it) { Pos.PG -> .2; Pos.SG -> .22; Pos.SF -> .22; Pos.PF -> .2; Pos.C -> .16 } }
            val p = makePlayer(lg, pos, 0, ovr, -1)
            p.ovrHistory.clear()
            // Most of a recruit's growth is already in the rating; the top of a class isn't a pile of future 95s.
            val growth = (Rng.range(2, 8) + x * 3 + Rng.gauss() * 2.5).roundToInt().coerceAtLeast(1)
            p.pot = (ovr + growth).coerceAtMost(99)
            p.recruitRank = rank
            p.stars = Ratings.starsFor(rank)
            posCount[pos] = (posCount[pos] ?: 0) + 1
            p.posRank = posCount[pos]!!
            p.happiness = 75
            // Stars were overwritten after makePlayer's GOAT roll — re-roll flavor for the true star tier.
            Builds.maybeGoatShape(p)
            val r = Recruit(p)
            r.nilAsk = (Ratings.expectedNil(p) * (1.0 + (p.stars - 2) * 0.35)).roundToInt() / 5 * 5
            Recruiting.seedFog(r)
            r.pref = Recruiting.pickPref(p.stars)
            out += r
        }
        // Each recruit starts with a handful of interested programs, near the Recruiting Pull he's after.
        Brand.ensure(lg)
        val pulls = Brand.pullTable(lg)
        for (r in out) {
            val target = Recruiting.wants(r)
            val k = Rng.range(3, 5)
            val chosen = HashSet<Int>()
            var guard = 0
            while (chosen.size < k && guard++ < 50) {
                val t = Rng.weighted(lg.teams) { tm ->
                    if (tm.id == lg.recruiterId) 0.0 else kotlin.math.exp(-((pulls[tm.id] - target) / 14.0).pow(2)) + 1e-9
                }
                chosen += t.id
            }
            for (id in chosen) r.interest[id] = Rng.range(8, 35)
        }
        return out
    }

    fun walkOn(lg: League, t: Team, pos: Pos): Player {
        val target = (45 + t.prestige * 0.18 + Rng.gauss() * 3).roundToInt().coerceIn(35, 70)
        val p = makePlayer(lg, pos, 0, target, t.id)
        p.ovrHistory.clear()
        p.ovrHistory += p.ovr
        p.pot = (p.ovr + Rng.range(2, 10)).coerceAtMost(99)
        p.stars = if (p.ovr > 56) 2 else 1
        p.recruitRank = 0
        p.posRank = 0
        p.nil = 0
        return p
    }

    fun nearestPositionNeed(lg: League, t: Team): Pos {
        val counts = Pos.entries.associateWith { pos -> t.roster.count { lg.player(it).pos == pos } }
        val ideal = mapOf(Pos.PG to 2, Pos.SG to 3, Pos.SF to 3, Pos.PF to 3, Pos.C to 2)
        return Pos.entries.maxBy { (ideal.getValue(it) - counts.getValue(it)) * 10 + Rng.int(0, 5) }
    }

    @Suppress("unused")
    private fun closeness(a: Int, b: Int) = 1.0 / (1 + abs(a - b))
}
