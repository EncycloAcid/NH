package com.nh.hoopscoach.game

import kotlin.math.roundToInt

object Recruiting {
    const val COST_SCOUT = 5
    const val COST_CALL = 10
    const val COST_VISIT = 30
    const val MAX_ROSTER = 13

    fun dailyPoints(lg: League) = 60 + lg.userCoach.rec / 2

    /** Players who will be on next year's roster. In the offseason, seniors are already gone. */
    fun stays(lg: League, p: Player) = if (lg.phase == Phase.OFFSEASON) true else p.year in 1..3

    /** Scholarship players on next year's roster (walk-ons don't use a scholarship). */
    fun returning(lg: League, t: Team) = t.roster.count { val p = lg.player(it); stays(lg, p) && !p.walkOn }

    fun committedTo(lg: League, teamId: Int) = lg.recruits.count { it.committed == teamId }

    fun openSpots(lg: League, t: Team) = MAX_ROSTER - returning(lg, t) - committedTo(lg, t.id)

    private fun starTarget(stars: Int) = when (stars) { 5 -> 85; 4 -> 65; 3 -> 40; else -> 15 }

    /** Filled during the daily tick so fit() doesn't rescan every recruit. */
    private var needCache: HashMap<Int, Int>? = null

    private fun needFactor(lg: League, t: Team, pos: Pos): Double {
        val cache = needCache
        val n = cache?.get(t.id * 5 + pos.ordinal) ?: (t.roster.count { val p = lg.player(it); p.pos == pos && stays(lg, p) } +
            lg.recruits.count { it.committed == t.id && it.p.pos == pos })
        return when { n <= 1 -> 1.2; n == 2 -> 1.0; n == 3 -> 0.85; else -> 0.7 }
    }

    /** How well a program fits a recruit (around 1.0 is a good fit). */
    fun fit(lg: League, t: Team, r: Recruit): Double {
        val gap = starTarget(r.p.stars) - t.prestige
        val prestigeF = (1.0 - gap.coerceAtLeast(0) / 40.0).coerceIn(0.25, 1.3) + (if (gap < -20) 0.1 else 0.0)
        val coach = lg.coachOf(t)
        var f = prestigeF * needFactor(lg, t, r.p.pos) * (0.7 + coach.rec / 170.0)
        if (t.id == lg.userTeamId) {
            if (coach.has(CoachSkill.RECRUITER)) f *= 1.08
            val ratio = if (r.nilAsk <= 0) 1.0 else r.nilOffer.toDouble() / r.nilAsk
            var nilF = when {
                ratio <= 0.01 -> 0.85
                ratio < 1.0 -> 0.85 + ratio * 0.3
                else -> (1.15 + (ratio - 1) * 0.5).coerceAtMost(1.35)
            }
            if (r.p.has(Trait.BAG_CHASER)) nilF = 1 + (nilF - 1) * 1.8
            f *= nilF
        }
        return f
    }

    fun userInterest(lg: League, r: Recruit) = r.interest[lg.userTeamId] ?: 0

    fun addToBoard(lg: League, r: Recruit) {
        r.onBoard = true
        if (lg.userTeamId !in r.interest) {
            r.interest[lg.userTeamId] = (12 * fit(lg, lg.user, r)).roundToInt().coerceIn(2, 30)
        }
    }

    fun canAfford(lg: League, cost: Int) = lg.recruitPoints >= cost

    fun scout(lg: League, r: Recruit): Boolean {
        if (r.scouted || !canAfford(lg, COST_SCOUT)) return false
        lg.recruitPoints -= COST_SCOUT
        r.scouted = true
        r.estOvr = r.p.ovr
        r.estPot = r.p.pot
        return true
    }

    fun call(lg: League, r: Recruit): Boolean {
        if (r.committed >= 0 || !canAfford(lg, COST_CALL)) return false
        addToBoard(lg, r)
        lg.recruitPoints -= COST_CALL
        bump(lg, r, Rng.range(4, 8) * fit(lg, lg.user, r))
        return true
    }

    fun visitReady(lg: League, r: Recruit) = lg.day - r.lastVisit >= 3

    fun visit(lg: League, r: Recruit): Boolean {
        if (r.committed >= 0 || !canAfford(lg, COST_VISIT) || !visitReady(lg, r)) return false
        addToBoard(lg, r)
        lg.recruitPoints -= COST_VISIT
        r.lastVisit = lg.day
        bump(lg, r, Rng.range(12, 20) * fit(lg, lg.user, r))
        return true
    }

    fun offer(lg: League, r: Recruit): Boolean {
        if (r.committed >= 0 || lg.userTeamId in r.offers) return false
        if (openSpots(lg, lg.user) - pendingOffers(lg) <= 0) return false
        addToBoard(lg, r)
        r.offers += lg.userTeamId
        bump(lg, r, 10 * fit(lg, lg.user, r))
        return true
    }

    /** Offers out to uncommitted recruits; each one could use a scholarship. */
    fun pendingOffers(lg: League) = lg.recruits.count { it.committed < 0 && lg.userTeamId in it.offers }

    fun withdraw(lg: League, r: Recruit) {
        r.offers.remove(lg.userTeamId)
    }

    fun setNil(lg: League, r: Recruit, amount: Int) { r.nilOffer = amount.coerceAtLeast(0) }

    private fun bump(lg: League, r: Recruit, amount: Double) {
        val id = lg.userTeamId
        r.interest[id] = ((r.interest[id] ?: 0) + amount.roundToInt()).coerceAtMost(130)
        checkCommit(lg, r)
    }

    /** Daily AI activity and commitments. */
    fun tick(lg: League) {
        val open = HashMap<Int, Int>()
        val need = HashMap<Int, Int>()
        for (t in lg.teams) {
            open[t.id] = openSpots(lg, t)
            for (pid in t.roster) { val p = lg.player(pid); if (stays(lg, p)) need.merge(t.id * 5 + p.pos.ordinal, 1, Int::plus) }
        }
        for (r in lg.recruits) if (r.committed >= 0) need.merge(r.committed * 5 + r.p.pos.ordinal, 1, Int::plus)
        needCache = need
        try { aiTick(lg, open) } finally { needCache = null }
        lg.recruitPoints = dailyPoints(lg)
    }

    private fun aiTick(lg: League, open: HashMap<Int, Int>) {
        for (r in lg.recruits) {
            if (r.committed >= 0) continue
            for ((tid, v) in r.interest.entries.toList()) {
                if (tid == lg.userTeamId) continue
                if ((open[tid] ?: 0) <= 0) continue
                val t = lg.team(tid)
                val gain = Rng.range(1, 5) * fit(lg, t, r)
                r.interest[tid] = (v + gain.roundToInt()).coerceAtMost(130)
                if (r.interest[tid]!! >= 55 && tid !in r.offers) r.offers += tid
            }
            // Recruits slowly cool on the user if ignored.
            val u = r.interest[lg.userTeamId]
            if (u != null && r.onBoard && Rng.chance(0.15)) r.interest[lg.userTeamId] = (u - 1).coerceAtLeast(0)
            checkCommit(lg, r)
            if (r.committed >= 0 && r.committed != lg.userTeamId) open[r.committed] = (open[r.committed] ?: 1) - 1
        }
    }

    private fun checkCommit(lg: League, r: Recruit) {
        if (r.committed >= 0) return
        // Only programs that offered and still have a scholarship are in the running.
        val ranked = r.interest.entries.filter { it.key in r.offers && openSpots(lg, lg.team(it.key)) > 0 }.sortedByDescending { it.value }
        val top = ranked.firstOrNull() ?: return
        val second = ranked.getOrNull(1)?.value ?: 0
        if (top.value >= 100 && (top.value - second >= 8 || top.value >= 118)) {
            r.committed = top.key
            val t = lg.team(top.key)
            val mine = top.key == lg.userTeamId
            if (mine || r.p.stars >= 5) {
                val who = if (r.transfer) "Transfer ${r.p.pos} ${r.p.name}" else "${starText(r.p.stars)} ${r.p.pos} ${r.p.name}"
                lg.news += News(lg.season, lg.day, "$who commits to ${t.school}", mine)
            }
        }
    }

    fun starText(n: Int) = "$n★"

    /** End of season: remaining recruits sign. Returns ids of players added to the user's team. */
    fun signingDay(lg: League): List<Int> {
        val signedUser = mutableListOf<Int>()
        // Undecided recruits pick their favorite school that offered and still has room.
        for (r in lg.recruits.sortedBy { it.p.recruitRank }) {
            if (r.committed >= 0) continue
            val ranked = r.interest.entries.filter { it.key in r.offers || it.key != lg.userTeamId }.sortedByDescending { it.value }
            for ((tid, v) in ranked) {
                if (v < 40) break
                if (tid == lg.userTeamId && tid !in r.offers) continue
                if (openRoom(lg, lg.team(tid)) > 0) { r.committed = tid; break }
            }
        }
        // Programs that still have room take the best remaining prospects.
        val leftovers = lg.recruits.filter { it.committed < 0 }.sortedByDescending { it.p.ovr }.toMutableList()
        val teamsByPrestige = Rng.noisyDesc(lg.teams) { it.prestige + Rng.gauss() * 10 }
        var progress = true
        while (progress && leftovers.isNotEmpty()) {
            progress = false
            for (t in teamsByPrestige) {
                if (openRoom(lg, t) <= 0 || leftovers.isEmpty()) continue
                val need = LeagueGen.nearestPositionNeed(lg, t)
                val pick = leftovers.firstOrNull { it.p.pos == need } ?: leftovers.first()
                leftovers.remove(pick)
                pick.committed = t.id
                progress = true
            }
        }
        // Move every committed recruit onto his roster as an incoming freshman.
        for (r in lg.recruits) {
            if (r.committed < 0) continue
            val t = lg.team(r.committed)
            val p = r.p
            p.teamId = t.id
            p.walkOn = false
            if (!r.transfer) {
                p.year = 1
                p.ovrHistory.clear()
                p.ovrHistory += p.ovr
            }
            p.number = freeNumber(lg, t)
            p.nil = if (t.id == lg.userTeamId && r.nilOffer > 0) r.nilOffer else Ratings.expectedNil(p)
            p.happiness = 75
            if (lg.playerOrNull(p.id) == null) lg.addPlayer(p)
            t.roster += p.id
            if (t.id == lg.userTeamId) signedUser += p.id
        }
        lg.recruits.clear()
        return signedUser
    }

    /** Roster room right now (used in the off-season, after departures). */
    fun openRoom(lg: League, t: Team) = MAX_ROSTER - Tryouts.scholarships(lg, t) - lg.recruits.count { it.committed == t.id }

    fun freeNumber(lg: League, t: Team): Int {
        val used = t.roster.map { lg.player(it).number }.toSet()
        return (0..55).filter { it !in used }.let { if (it.isEmpty()) Rng.int(0, 99) else Rng.pick(it) }
    }

    fun classRank(lg: League, teamId: Int): Int {
        val score = HashMap<Int, Double>()
        for (r in lg.recruits) if (r.committed >= 0) score[r.committed] = (score[r.committed] ?: 0.0) + r.p.stars * r.p.stars
        val mine = score[teamId] ?: return 0
        return score.values.count { it > mine } + 1
    }
}
