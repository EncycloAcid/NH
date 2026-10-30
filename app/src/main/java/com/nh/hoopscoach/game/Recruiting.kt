package com.nh.hoopscoach.game

import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Recruiting: a class of 1,600 high-school prospects (plus the transfer portal), worked over five
 * offseason rounds.
 *
 * In season you scout prospects and build your board. After the season every program gets one point
 * budget for the whole cycle. Before each round, rival schools set their points. You set yours, preview
 * where you'd stand, and lock in. Points stay on a prospect until you move them or he commits somewhere.
 *
 * Interest gains are exact, so the preview is what will happen. Who commits, which schools drop out and
 * which new ones enter is decided when the round locks.
 */
object Recruiting {
    const val MAX_ROSTER = 13
    const val CLASS_SIZE = 1600
    const val ROUNDS = 5
    const val COST_SCOUT = 5
    const val SCOUT_CAP = 60
    const val STEP = 5

    // ---------- roster room ----------

    /** Players who will be on next year's roster. In the offseason, seniors are already gone. */
    fun stays(lg: League, p: Player) = if (lg.phase == Phase.OFFSEASON) true else p.year in 1..3

    /** Scholarship players on next year's roster (walk-ons don't use a scholarship). */
    fun returning(lg: League, t: Team) = t.roster.count { val p = lg.player(it); stays(lg, p) && !p.walkOn }

    fun committedTo(lg: League, teamId: Int) = lg.recruits.count { it.committed == teamId }

    fun openSpots(lg: League, t: Team) = MAX_ROSTER - returning(lg, t) - committedTo(lg, t.id)

    /** Roster room right now (used in the off-season, after departures). */
    fun openRoom(lg: League, t: Team) = MAX_ROSTER - Tryouts.scholarships(lg, t) - lg.recruits.count { it.committed == t.id }

    fun freeNumber(lg: League, t: Team): Int {
        val used = t.roster.map { lg.player(it).number }.toSet() + Legacy.retiredNumbers(t)
        return (0..55).filter { it !in used }.let { if (it.isEmpty()) Rng.int(0, 99) else Rng.pick(it) }
    }

    fun classRank(lg: League, teamId: Int): Int {
        val score = HashMap<Int, Double>()
        for (r in lg.recruits) if (r.committed >= 0) score[r.committed] = (score[r.committed] ?: 0.0) + r.p.stars * r.p.stars
        val mine = score[teamId] ?: return 0
        return score.values.count { it > mine } + 1
    }

    fun starText(n: Int) = "$n★"

    fun inCycle(lg: League) = lg.phase == Phase.OFFSEASON && lg.offseason?.step == OffStep.RECRUITING

    fun round(lg: League) = lg.offseason?.round ?: 0

    // ---------- scouting ----------

    fun dailyScouting(lg: League) = 10 + lg.userCoach.rec / 10

    /** In season: scouting points refill a little every game day. */
    fun tick(lg: League) { lg.recruitPoints = (lg.recruitPoints + dailyScouting(lg)).coerceAtMost(SCOUT_CAP) }

    fun scout(lg: League, r: Recruit): Boolean {
        if (r.scouted || lg.recruitPoints < COST_SCOUT) return false
        lg.recruitPoints -= COST_SCOUT
        r.scouted = true
        r.estOvr = r.p.ovr
        r.estPot = r.p.pot
        return true
    }

    fun addToBoard(lg: League, r: Recruit) { r.onBoard = true; @Suppress("UNUSED_VARIABLE") val u = lg }

    fun removeFromBoard(lg: League, r: Recruit) {
        r.onBoard = false
        if (r.committed < 0) r.alloc.remove(lg.userTeamId)
    }

    fun setNil(lg: League, r: Recruit, amount: Int) { r.nilOffer = amount.coerceAtLeast(0); @Suppress("UNUSED_VARIABLE") val u = lg }

    // ---------- budget ----------

    /** Points for the whole cycle: mostly program and coach. */
    fun budgetFor(lg: League, t: Team, pull: Int = Brand.pull(lg, t)): Int {
        val c = lg.coachOf(t)
        var b = 300 + pull * 6 + c.rec * 2
        if (c.has(CoachSkill.RECRUITER)) b = b * 11 / 10
        return b / 10 * 10
    }

    /** User points sitting on prospects who haven't committed. */
    fun tied(lg: League) = lg.recruits.sumOf { if (it.committed < 0) it.alloc[lg.userTeamId] ?: 0 else 0 }

    fun available(lg: League) = lg.recruitBudget - tied(lg)

    fun mine(lg: League, r: Recruit) = r.alloc[lg.userTeamId] ?: 0

    /** Put [points] on a prospect (0 takes them all off). Returns false if it can't be done. */
    fun setPoints(lg: League, r: Recruit, points: Int): Boolean {
        if (!inCycle(lg) || r.committed >= 0) return false
        val p = (points / STEP * STEP).coerceAtLeast(0)
        val cur = mine(lg, r)
        if (p > cur && (p - cur) > available(lg)) return false
        if (p > 0 && cur == 0 && openSpots(lg, lg.user) <= 0) return false
        if (p == 0) r.alloc.remove(lg.userTeamId) else { r.alloc[lg.userTeamId] = p; r.onBoard = true }
        return true
    }

    // ---------- fit ----------

    /** One reason a prospect likes (or doesn't like) a school. 1.0 is neutral. */
    class Factor(val label: String, val value: Double, val note: String)

    /** The Recruiting Pull a prospect is looking for. */
    fun wants(r: Recruit): Double =
        if (r.transfer) (r.p.ovr * 1.35 - 28).coerceIn(10.0, 95.0)
        else when (r.p.stars) { 5 -> 80.0; 4 -> 62.0; 3 -> 42.0; else -> 22.0 }

    /** Per-round lookups shared by every fit calculation. */
    class Ctx(lg: League) {
        val pulls = Brand.pullTable(lg)
        val pipes = Pipeline.table(lg)
        /** Overalls of next year's players at each team and position (key teamId * 5 + pos). */
        val depth = HashMap<Int, MutableList<Int>>()
        init {
            for (t in lg.teams) for (pid in t.roster) {
                val p = lg.player(pid)
                if (stays(lg, p)) depth.getOrPut(t.id * 5 + p.pos.ordinal) { mutableListOf() } += p.ovr
            }
            for (r in lg.recruits) if (r.committed >= 0) depth.getOrPut(r.committed * 5 + r.p.pos.ordinal) { mutableListOf() } += r.p.ovr
        }
    }

    fun factors(lg: League, t: Team, r: Recruit, ctx: Ctx = Ctx(lg)): List<Factor> {
        val pref = r.pref
        val out = ArrayList<Factor>(8)
        // Program strength: Recruiting Pull against what he's looking for.
        val pull = ctx.pulls[t.id]
        val gap = wants(r) - pull
        var strength = (1.0 - gap.coerceAtLeast(0.0) / 40.0).coerceIn(0.3, 1.3) + if (gap < -20) 0.1 else 0.0
        if (pref == RecruitPref.BRAND) strength = strength.pow(1.6)
        out += Factor("Program strength", strength, "Recruiting Pull $pull; he's looking for about ${wants(r).roundToInt()}")
        // Coach.
        val c = lg.coachOf(t)
        val coach = (0.8 + c.rec / 250.0) * if (c.has(CoachSkill.RECRUITER)) 1.08 else 1.0
        out += Factor("Coach recruiting", coach, "Coach ${c.last}: ${c.rec} recruiting" + if (c.has(CoachSkill.RECRUITER)) ", Ace Recruiter" else "")
        // Location.
        val close = Geo.closeness(t, r.p)
        val loc = if (pref == RecruitPref.HOME) when (close) { 2 -> 1.35; 1 -> 1.15; else -> 0.85 } else when (close) { 2 -> 1.15; 1 -> 1.07; else -> 1.0 }
        val home = Geo.stateOf(r.p)
        out += Factor("Location", loc, when {
            home == null -> "From ${r.p.hometown}"
            close == 2 -> "In-state: home is $home"
            close == 1 -> "Same region (${Geo.region(home)})"
            else -> "Far from home ($home)"
        })
        // Pro pipeline.
        val pipe = ctx.pipes.getOrElse(t.id) { 0 }
        val bonus = Pipeline.bonus(pipe, Pipeline.isTop(r)) * if (pref == RecruitPref.PROS) 3.0 else 1.0
        out += Factor("Pro pipeline", 1 + bonus, if (pipe == 0) "No recent draft picks" else "$pipe pipeline points from recent drafts")
        // Playing time: how many players at his spot are already as good as him.
        val list = ctx.depth[t.id * 5 + r.p.pos.ordinal].orEmpty()
        val ahead = list.count { it >= r.p.ovr }
        var minutes = when (ahead) { 0 -> 1.15; 1 -> 1.05; 2 -> 0.95; else -> 0.85 }
        if (pref == RecruitPref.MINUTES) minutes = minutes.pow(2)
        out += Factor("Playing time", minutes, when (ahead) { 0 -> "He'd start right away"; 1 -> "One player ahead of him"; else -> "$ahead players ahead of him" })
        // Roster crowding at his position.
        val n = list.size
        val crowd = when { n <= 1 -> 1.15; n == 2 -> 1.0; n == 3 -> 0.88; else -> 0.75 }
        out += Factor("Roster crowding", crowd, "$n ${r.p.pos}${if (n == 1) "" else "s"} on next year's roster")
        // Winning now.
        if (pref == RecruitPref.WINNING) {
            val last = t.history.lastOrNull()
            val pct = if (t.gp > 0) t.winPct else if (last != null && last.w + last.l > 0) last.w.toDouble() / (last.w + last.l) else 0.5
            out += Factor("Winning", 0.85 + pct * 0.4, "Won ${(pct * 100).roundToInt()}% of games")
        }
        // NIL: AI programs meet the market; your offer is up to you.
        if (t.id == lg.userTeamId) {
            val ratio = if (r.nilAsk <= 0) 1.0 else r.nilOffer.toDouble() / r.nilAsk
            var nil = when {
                ratio <= 0.01 -> 0.88
                ratio < 1.0 -> 0.88 + ratio * 0.12
                else -> (1.0 + (ratio - 1) * 0.5).coerceAtMost(1.2)
            }
            if (pref == RecruitPref.NIL || r.p.has(Trait.BAG_CHASER)) nil = 1 + (nil - 1) * 1.8
            out += Factor("NIL offer", nil, if (r.nilOffer <= 0) "No NIL offer yet" else "Offering ${r.nilOffer}K of his ${r.nilAsk}K ask")
        }
        return out
    }

    fun fit(lg: League, t: Team, r: Recruit, ctx: Ctx): Double = factors(lg, t, r, ctx).fold(1.0) { a, f -> a * f.value }

    /** Interest a school gains in one round from [points] (diminishing returns). */
    fun gain(points: Int, fit: Double) = if (points <= 0) 0.0 else 2.6 * sqrt(points.toDouble()) * fit

    /** Where every school in the race would stand after this round, with the points as they are now. */
    fun project(lg: League, r: Recruit, ctx: Ctx): Map<Int, Int> {
        val out = HashMap<Int, Int>()
        val ids = r.interest.keys + r.alloc.keys
        for (tid in ids) {
            val now = r.interest[tid] ?: 4
            val pts = r.alloc[tid] ?: 0
            // Gains taper as interest climbs, so the last stretch to 100 is the hardest.
            out[tid] = if (pts > 0) (now + gain(pts, fit(lg, lg.team(tid), r, ctx)) * (1 - now / 115.0).coerceAtLeast(0.05)).roundToInt().coerceAtMost(100) else (now * 0.9).roundToInt()
        }
        return out
    }

    /** 1-based place of [teamId] in an interest map (0 if not in the race). */
    fun place(m: Map<Int, Int>, teamId: Int): Int {
        val v = m[teamId] ?: return 0
        return m.values.count { it > v } + 1
    }

    fun effortLabel(points: Int) = when {
        points >= 120 -> "All in"
        points >= 60 -> "Heavy push"
        points >= 25 -> "Steady"
        points > 0 -> "Light"
        else -> "Cooling"
    }

    // ---------- the cycle ----------

    fun startCycle(lg: League) {
        val os = lg.offseason ?: return
        os.round = 1
        os.report = null
        lg.recruitBudget = budgetFor(lg, lg.user)
        lg.recruitPoints = SCOUT_CAP
        for (r in lg.recruits) r.alloc.clear()
        aiMoves(lg)
    }

    /** Rivals set their points for the round: their budget spread over the races they can win. */
    fun aiMoves(lg: League) {
        val ctx = Ctx(lg)
        val room = IntArray(lg.teams.size) { openSpots(lg, lg.teams[it]) }
        val races = HashMap<Int, MutableList<Recruit>>()
        for (r in lg.recruits) {
            if (r.committed >= 0) continue
            r.alloc.keys.retainAll(setOf(lg.userTeamId))
            for (tid in r.interest.keys) if (tid != lg.userTeamId && room[tid] > 0) races.getOrPut(tid) { mutableListOf() } += r
        }
        for ((tid, list) in races) {
            val t = lg.team(tid)
            val budget = budgetFor(lg, t, ctx.pulls[tid])
            val scored = list.map { r -> r to value(lg, t, r, ctx) }.sortedByDescending { it.second }.take(room[tid] * 3 + 1)
            val total = scored.sumOf { it.second.pow(1.5) }
            if (total <= 0) continue
            for ((r, v) in scored) {
                val pts = (budget * v.pow(1.5) / total).coerceAtMost(budget * 0.22)
                val rounded = (pts / STEP).roundToInt() * STEP
                if (rounded >= 10) r.alloc[tid] = rounded
            }
        }
    }

    /** How worthwhile a prospect is for a school: talent, fit, and a race it can win. */
    private fun value(lg: League, t: Team, r: Recruit, ctx: Ctx): Double {
        val mine = r.interest[t.id] ?: 0
        val best = r.interest.entries.filter { it.key != t.id }.maxOfOrNull { it.value } ?: 0
        val winnable = (1 - (best - mine) / 80.0).coerceIn(0.3, 1.0)
        val quality = (((r.p.ovr * 0.6 + r.p.pot * 0.4) - 35).coerceAtLeast(5.0) / 40.0).pow(2)
        // Programs aim at prospects their Pull can land, and the best ones don't spend on fallbacks early.
        val gap = wants(r) - ctx.pulls[t.id]
        val band = when { gap > 25 -> 0.3; gap > 12 -> 0.7; gap < -25 -> 0.45; gap < -15 -> 0.75; else -> 1.0 }
        return fit(lg, t, r, ctx) * quality * winnable * band
    }

    /**
     * Spend the user's free points the way a sensible staff would: about two prospects per open
     * scholarship, best value first, never more than one per position beyond what the roster needs.
     * Points already placed stay where they are. Returns how many prospects got points.
     */
    fun autoPlan(lg: League): Int {
        if (!inCycle(lg)) return 0
        val u = lg.user
        val room = openSpots(lg, u)
        val free = available(lg)
        if (room <= 0 || free < 10) return 0
        val ctx = Ctx(lg)
        val already = lg.recruits.filter { it.committed < 0 && mine(lg, it) > 0 }
        val want = (room * 2 + 1 - already.size).coerceAtLeast(0)
        val perPos = HashMap<Pos, Int>()
        for (r in already) perPos.merge(r.p.pos, 1, Int::plus)
        val picks = lg.recruits.asSequence()
            .filter { it.committed < 0 && mine(lg, it) == 0 }
            .map { it to value(lg, u, it, ctx) }
            .sortedByDescending { it.second }
            .filter { (r, _) -> (perPos[r.p.pos] ?: 0) < 3 && perPos.merge(r.p.pos, 1, Int::plus) != null }
            .take(want).toList()
        val all = already.map { it to value(lg, u, it, ctx) } + picks
        val total = all.sumOf { it.second.pow(1.5) }
        if (total <= 0) return 0
        var nilRoom = Finance.available(lg, u, includeSeniors = false)
        for ((r, _) in all.sortedByDescending { it.second }) {
            if (r.nilOffer < r.nilAsk && r.nilAsk - r.nilOffer <= nilRoom) { nilRoom -= r.nilAsk - r.nilOffer; r.nilOffer = r.nilAsk }
        }
        var left = free
        for ((r, v) in all.sortedByDescending { it.second }) {
            val add = ((free * v.pow(1.5) / total) / STEP).roundToInt() * STEP
            val pts = add.coerceAtMost(left)
            if (pts <= 0) continue
            if (setPoints(lg, r, mine(lg, r) + pts)) left -= pts
        }
        return all.size
    }

    private val commitBase = doubleArrayOf(0.25, 0.40, 0.55, 0.70, 0.90)

    /** Lock in the round: interest moves, some prospects commit, schools drop out and new ones enter. */
    fun lockRound(lg: League) {
        val os = lg.offseason ?: return
        val round = os.round.coerceIn(1, ROUNDS)
        val ctx = Ctx(lg)
        val room = IntArray(lg.teams.size) { openSpots(lg, lg.teams[it]) }
        val u = lg.userTeamId
        val rep = RoundReport(round)
        val order = lg.recruits.filter { it.committed < 0 }.sortedByDescending { it.p.ovr }
        for (r in order) {
            val proj = project(lg, r, ctx)
            r.interest.clear()
            r.interest.putAll(proj)
            val ranked = proj.entries.filter { room[it.key] > 0 }.sortedByDescending { it.value }
            val top = ranked.firstOrNull() ?: continue
            val second = ranked.getOrNull(1)?.value ?: 0
            val ready = ((top.value - 40) / 40.0).coerceIn(0.0, 1.0)
            val margin = (0.35 + (top.value - second) / 25.0).coerceIn(0.15, 1.0)
            if (Rng.chance(commitBase[round - 1] * ready * margin)) commit(lg, r, top.key, round, room, rep)
        }
        val targets = HashSet<Int>()
        for (r in lg.recruits) if (r.onBoard || u in r.alloc || u in r.interest) targets += r.p.id
        val pulls = ctx.pulls
        for (r in lg.recruits) {
            if (r.committed >= 0) continue
            val lead = r.interest.values.maxOrNull() ?: 0
            // Schools that are out of room, or far behind and not trying, drop out.
            val gone = r.interest.keys.filter { tid ->
                tid != u && (room[tid] <= 0 || ((r.alloc[tid] ?: 0) == 0 && (r.interest[tid] ?: 0) < maxOf(12.0, lead * 0.4)))
            }
            for (tid in gone) r.interest.remove(tid)
            // New schools jump in.
            val enters = Rng.chance(0.15 + if (r.interest.size < 3) 0.25 else 0.0)
            var entered = 0
            if (enters) {
                val want = wants(r)
                val pick = Rng.weighted(lg.teams) { tm ->
                    if (tm.id == u || room[tm.id] <= 0 || tm.id in r.interest) 0.0 else exp(-((pulls[tm.id] - want) / 14.0).pow(2)) + 1e-6
                }
                if (pick.id != u && pick.id !in r.interest && room[pick.id] > 0) { r.interest[pick.id] = Rng.range(8, 20); entered = 1 }
            }
            if (r.p.id in targets) { rep.dropped += gone.size; rep.entered += entered }
        }
        for (r in lg.recruits) if (r.committed < 0) r.alloc.keys.retainAll(setOf(u))
        rep.open = lg.recruits.count { it.committed < 0 && it.p.id in targets }
        os.report = rep
        os.round++
        lg.recruitPoints = (lg.recruitPoints + 20).coerceAtMost(SCOUT_CAP)
        if (os.round <= ROUNDS) aiMoves(lg)
    }

    private fun commit(lg: League, r: Recruit, tid: Int, round: Int, room: IntArray?, rep: RoundReport?) {
        val u = lg.userTeamId
        val wasTarget = r.onBoard || u in r.alloc || u in r.interest
        r.committed = tid
        r.round = round
        r.alloc.clear()
        if (room != null) room[tid]--
        rep?.let {
            it.commits++
            if (tid == u) it.mine += r.p.id else if (wasTarget) it.lost += r.p.id
        }
        val t = lg.team(tid)
        if (tid == u || r.p.stars >= 5 || (r.transfer && r.p.ovr >= 80)) {
            val who = if (r.transfer) "Transfer ${r.p.pos} ${r.p.name}" else "${starText(r.p.stars)} ${r.p.pos} ${r.p.name}"
            lg.news += News(lg.season, lg.day, "$who commits to ${t.school}", tid == u)
        }
    }

    /**
     * After round five: prospects with a clear favorite sign late, programs with room pick up the best
     * of the rest, and everyone else goes unsigned. Returns ids of players added to the user's team.
     */
    fun endCycle(lg: League): List<Int> {
        val u = lg.userTeamId
        val room = IntArray(lg.teams.size) { openRoom(lg, lg.teams[it]) }
        for (r in lg.recruits.filter { it.committed < 0 }.sortedByDescending { it.p.ovr }) {
            val top = r.interest.entries.filter { room[it.key] > 0 && it.value >= 40 }.maxByOrNull { it.value } ?: continue
            commit(lg, r, top.key, ROUNDS + 1, room, null)
        }
        val leftovers = lg.recruits.filter { it.committed < 0 }.sortedByDescending { it.p.ovr }.toMutableList()
        val teams = Rng.noisyDesc(lg.teams.filter { it.id != u }) { it.prestige + Rng.gauss() * 10 }
        var progress = true
        while (progress && leftovers.isNotEmpty()) {
            progress = false
            for (t in teams) {
                if (room[t.id] <= 0 || leftovers.isEmpty()) continue
                val floor = 45 + t.prestige * 0.18
                val need = LeagueGen.nearestPositionNeed(lg, t)
                val pick = leftovers.firstOrNull { it.p.pos == need && it.p.ovr >= floor } ?: leftovers.firstOrNull { it.p.ovr >= floor } ?: continue
                leftovers.remove(pick)
                pick.committed = t.id
                pick.round = ROUNDS + 1
                room[t.id]--
                progress = true
            }
        }
        lg.offseason?.unsigned = leftovers.count { !it.transfer }
        val signedUser = mutableListOf<Int>()
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
            p.nil = if (t.id == u && r.nilOffer > 0) r.nilOffer else Ratings.expectedNil(p)
            p.happiness = 75
            if (lg.playerOrNull(p.id) == null) lg.addPlayer(p)
            t.roster += p.id
            if (t.id == u) signedUser += p.id
        }
        lg.recruits.clear()
        lg.recruitBudget = 0
        return signedUser
    }

    /** Older saves that were mid-cycle: give them a budget and rival moves. */
    fun ensure(lg: League) {
        if (inCycle(lg) && lg.recruitBudget <= 0) {
            val round = lg.offseason!!.round
            startCycle(lg)
            lg.offseason!!.round = round
        }
    }

    fun pickPref(stars: Int): RecruitPref = Rng.weighted(RecruitPref.entries) {
        when (it) {
            RecruitPref.NONE -> 0.28
            RecruitPref.HOME -> 0.2
            RecruitPref.MINUTES -> 0.15
            RecruitPref.WINNING -> 0.12
            RecruitPref.BRAND -> if (stars >= 4) 0.16 else 0.08
            RecruitPref.NIL -> 0.09
            RecruitPref.PROS -> if (stars >= 4) 0.14 else 0.03
        }
    }
}
