package com.nh.hoopscoach.game

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

class PlayEvent(
    val period: Int,
    val clock: Int,       // seconds left in the period
    val side: Int,        // 0 home, 1 away, -1 neutral
    val text: String,
    val hs: Int,
    val aws: Int,
    val points: Int,
)

/**
 * Simulates one game a possession at a time. Call [step] until it returns false
 * (or [simToEnd]), then [commit] to write results into the league.
 */
class GameEngine(private val lg: League, val game: Game, private val pbp: Boolean) {
    val teams = arrayOf(lg.team(game.home), lg.team(game.away))
    private val coaches = arrayOf(lg.coachOf(teams[0]), lg.coachOf(teams[1]))
    private val rosters: Array<List<Player>> = arrayOf(lg.rosterOf(teams[0]), lg.rosterOf(teams[1]))
    private val byId: Array<Map<Int, Player>> = arrayOf(rosters[0].associateBy { it.id }, rosters[1].associateBy { it.id })
    val lines: Array<LinkedHashMap<Int, StatLine>> = arrayOf(LinkedHashMap(), LinkedHashMap())
    private val starters: Array<Set<Int>>
    val score = IntArray(2)
    var period = 1
        private set
    var clock = 1200.0
        private set
    private var poss = if (Rng.chance(0.5)) 0 else 1
    private val firstPoss = poss
    private val teamFouls = IntArray(2)
    val periodScores = mutableListOf(IntArray(2))
    var finished = false
        private set
    val events = ArrayList<PlayEvent>()

    /** A made basket late in a period, kept to spot buzzer-beaters. [margin] is the shooting team's margin before the shot. */
    class Clutch(val side: Int, val name: String, val shot: String, val period: Int, val clock: Double, val margin: Int, val pts: Int)
    private var lastBasket: Clutch? = null
    private var tyingShot: Clutch? = null
    /** The first shot that tied the game in the last two seconds and sent it to overtime. */
    var forcedOt: Clutch? = null
        private set
    /** The winning basket, if it went in with two seconds or less left in the game. */
    val buzzerBeater: Clutch?
        get() {
            val b = lastBasket ?: return null
            if (!finished || b.period != period || b.clock > 2.0 || b.margin > 0 || b.margin + b.pts <= 0) return null
            val winner = if (score[0] > score[1]) 0 else 1
            return b.takeIf { it.side == winner }
        }
    /** 1.0 is a normal home crowd; rivalry crowds scale it up to match their extra Elo. */
    private val homeEdge = Rankings.homeElo(lg, game) / Rankings.HOME_ELO

    init {
        if (game.att <= 0) game.att = Rivalries.attendance(lg, game)
    }
    private var committed = false
    private val n: DoubleArray = lg.norms ?: computeNorms(lg).also { lg.norms = it }
    private val skillOff = DoubleArray(2) { if (coaches[it].has(CoachSkill.OFF_TACTICIAN)) 0.008 else 0.0 }
    private val skillDef = DoubleArray(2) { if (coaches[it].has(CoachSkill.DEF_TACTICIAN)) 0.008 else 0.0 }

    companion object {
        /**
         * League-wide centers for the shooting and defense formulas, measured from every team's
         * starters. Keeps percentages stable even if ratings drift up or down over many seasons.
         * The offsets keep the original calibration: center = norm + offset.
         */
        private val OFFSET = doubleArrayOf(-1.31, -2.63, 0.48, -0.41, -3.43, -2.8, -1.45, -3.92, -2.93)

        fun computeNorms(lg: League): DoubleArray {
            val sums = DoubleArray(9)
            var teams = 0
            var players = 0
            for (t in lg.teams) {
                val st = t.plan.slots.mapNotNull { lg.playerOrNull(it.starter) }
                if (st.size < 5) continue
                teams++
                for (p in st) {
                    players++
                    sums[0] += p[Attr.INS]; sums[1] += p[Attr.MID]; sums[2] += p[Attr.OUT]; sums[3] += p[Attr.HND]; sums[4] += p[Attr.PAS]
                }
                val idf = st.map { it[Attr.IDF] }
                sums[5] += idf.average() * 0.5 + idf.max() * 0.5
                sums[6] += st.map { it[Attr.PER] }.average()
                sums[7] += st.map { it[Attr.STL] }.average()
                val blk = st.map { it[Attr.BLK] }
                sums[8] += blk.average() * 0.4 + blk.max() * 0.6
            }
            val out = DoubleArray(9)
            for (i in 0..4) out[i] = sums[i] / players.coerceAtLeast(1)
            for (i in 5..8) out[i] = sums[i] / teams.coerceAtLeast(1)
            for (i in 0..8) out[i] += OFFSET[i]
            return out
        }
    }

    init {
        for (t in teams) Gameplan.validate(lg, t)
        for (side in 0..1) for (p in rosters[side]) lines[side][p.id] = StatLine()
        starters = Array(2) { side -> teams[side].plan.slots.map { it.starter }.filter { it >= 0 }.toSet() }
        if (pbp) {
            val a = teams[1]; val h = teams[0]
            log(-1, "Tip-off: ${a.school} at ${h.school}${if (game.neutral) " (neutral site)" else ""}", 0)
        }
    }

    // ---------- lineups ----------

    private fun fouledOut(side: Int, id: Int) = (lines[side][id]?.pf ?: 0) >= 5

    private fun lineup(side: Int): List<Player> {
        val t = teams[side]
        val roster = rosters[side]
        val lookup = byId[side]
        val elapsed = if (period <= 2) 1200 - clock else 0.0
        val late = period >= 2 && clock < 240
        val margin = score[side] - score[1 - side]
        val garbage = period == 2 && clock < 270 && abs(margin) >= 18
        val chosen = ArrayList<Player>(5)
        for ((i, s) in t.plan.slots.withIndex()) {
            if (i >= 5) break
            val starterSec = s.starterMin * 30.0
            val reserveSec = 1200 - starterSec
            val a = starterSec * 0.55
            var useStarter = elapsed < a || elapsed >= a + reserveSec
            if (period > 2 || (late && abs(margin) <= 12)) useStarter = true
            val st = lookup[s.starter]
            val rs = lookup[s.reserve]
            // Foul trouble: sit the starter.
            if (st != null && useStarter && rs != null) {
                val pf = lines[side][st.id]?.pf ?: 0
                if ((period == 1 && pf >= 2 && clock > 300) || (period == 2 && pf >= 4 && clock > 240)) useStarter = false
            }
            var pick = if (useStarter) st ?: rs else rs ?: st
            if (pick != null && fouledOut(side, pick.id)) pick = (if (pick === st) rs else st)?.takeIf { !fouledOut(side, it.id) }
            if (pick != null && chosen.none { it.id == pick.id }) chosen += pick
        }
        if (garbage) {
            // Empty the bench: swap the two best on the floor for deep reserves.
            val deep = roster.filter { p -> chosen.none { it.id == p.id } && !fouledOut(side, p.id) }.sortedByDescending { it.ovr }
            val starterIds = starters[side]
            var k = 0
            for (i in chosen.indices) {
                if (k >= deep.size) break
                if (chosen[i].id in starterIds) { chosen[i] = deep[k]; k++ }
            }
        }
        if (chosen.size < 5) {
            val extra = roster.filter { p -> chosen.none { it.id == p.id } && !fouledOut(side, p.id) }.sortedByDescending { it.ovr }
            for (p in extra) { if (chosen.size >= 5) break; chosen += p }
            if (chosen.size < 5) {
                // Everyone has fouled out; play with what's left (rare).
                for (p in roster) { if (chosen.size >= 5) break; if (chosen.none { it.id == p.id }) chosen += p }
            }
        }
        return chosen
    }

    // ---------- tactics ----------

    private fun off(side: Int) = teams[side].plan.offense
    private fun def(side: Int) = teams[side].plan.defense

    private fun possLength(o: Int): Double {
        var base = when (off(o)) {
            Offense.UPTEMPO -> 14.0; Offense.GRIND -> 21.0; Offense.PERIMETER -> 16.6; Offense.INSIDE -> 17.6; else -> 17.2
        }
        when (def(1 - o)) { Defense.PRESS -> base -= 2.0; Defense.ZONE -> base += 0.6; Defense.PACKLINE -> base += 0.8; else -> {} }
        return base * (0.6 + Rng.d() * 0.8)
    }

    private fun usage(side: Int, p: Player): Double {
        for (s in teams[side].plan.slots) {
            if (s.starter == p.id) return s.starterUsg.toDouble()
            if (s.reserve == p.id) return s.reserveUsg.toDouble()
        }
        return Gameplan.usageFor(p).toDouble() * 0.8
    }

    private fun ft(p: Player): Double = (0.69 + ((p[Attr.MID] + p[Attr.OUT]) / 2.0 - 66) * 0.0075).coerceIn(0.4, 0.95)

    private fun line(side: Int, p: Player) = lines[side].getOrPut(p.id) { StatLine() }

    private fun log(side: Int, text: String, pts: Int) {
        if (!pbp) return
        events += PlayEvent(period, clock.roundToInt().coerceAtLeast(0), side, text, score[0], score[1], pts)
    }

    // ---------- the possession ----------

    fun step(): Boolean {
        if (finished) return false
        val o = poss
        val d = 1 - o
        val offense = lineup(o)
        val defense = lineup(d)
        val trail = score[o] - score[d]

        var len = possLength(o)
        // Milk the clock with a late lead; hurry when behind; hold for the last shot when tied.
        if (period >= 2 && clock < 60) {
            if (trail > 0) len = min(clock, 30.0) else if (trail < 0) len = min(len, 8.0 + Rng.d() * 6)
            else if (clock <= 30) len = (clock - Rng.d() * 3).coerceAtLeast(min(clock, 0.5))
        }
        // Intentional foul when the defense trails late.
        if (period >= 2 && clock < 40 && trail in 1..9) {
            val used = min(clock, 2.0 + Rng.d() * 4)
            elapse(used, offense, defense)
            val fouler = Rng.pick(defense)
            line(d, fouler).pf++
            teamFouls[d]++
            val shooter = Rng.weighted(offense) { it[Attr.HND].toDouble().pow(2) }
            log(d, "${fouler.short} fouls ${shooter.short} intentionally", 0)
            freeThrows(o, shooter, 2, offense, defense)
            endCheck()
            return !finished
        }
        if (clock <= len) len = clock
        elapse(len, offense, defense)
        runPossession(o, offense, defense, trail)
        endCheck()
        return !finished
    }

    private fun elapse(sec: Double, a: List<Player>, b: List<Player>) {
        val s = sec.roundToInt()
        val sides = if (poss == 0) arrayOf(a, b) else arrayOf(b, a)
        for (side in 0..1) for (p in sides[side]) line(side, p).sec += s
        clock -= sec
    }

    private fun runPossession(o: Int, offense: List<Player>, defense: List<Player>, trail: Int) {
        val d = 1 - o
        val stealD = defense.map { it[Attr.STL] }.average()
        val perD = defense.map { it[Attr.PER] }.average()
        val idfs = defense.map { it[Attr.IDF] }
        val intD = idfs.average() * 0.5 + idfs.max() * 0.5
        val blks = defense.map { it[Attr.BLK] }
        val blkD = blks.average() * 0.4 + blks.max() * 0.6
        val handler = Rng.weighted(offense) { it[Attr.HND].toDouble().pow(2) }
        val pasAvg = offense.map { it[Attr.PAS] }.average()

        // Turnover
        var pTo = 0.168 + (stealD - n[7]) * 0.0018 - (handler[Attr.HND] - n[3]) * 0.0018 - (pasAvg - n[4]) * 0.0008
        when (def(d)) { Defense.PRESS -> pTo += 0.035; Defense.PACKLINE -> pTo -= 0.012; Defense.ZONE -> pTo -= 0.006; else -> {} }
        when (off(o)) { Offense.UPTEMPO -> pTo += 0.01; Offense.GRIND -> pTo -= 0.012; else -> {} }
        pTo -= (coaches[o].off - 70) * 0.0004
        pTo = pTo.coerceIn(0.07, 0.3)
        if (Rng.chance(pTo)) {
            val loser = if (Rng.chance(0.6)) handler else Rng.weighted(offense) { usage(o, it) }
            line(o, loser).tov++
            if (Rng.chance(0.55)) {
                val stealer = Rng.weighted(defense) { it[Attr.STL].toDouble().pow(3) }
                line(d, stealer).stl++
                log(d, "${stealer.short} steals it from ${loser.short}", 0)
            } else log(o, "Turnover by ${loser.short}", 0)
            poss = d
            return
        }

        // Non-shooting foul
        if (Rng.chance(0.1)) {
            val fouler = Rng.weighted(defense) { posFoul(it.pos) }
            line(d, fouler).pf++
            teamFouls[d]++
            if (teamFouls[d] >= 7) {
                val shooter = Rng.weighted(offense) { usage(o, it) }
                log(d, "Foul on ${fouler.short}, ${shooter.short} to the line", 0)
                freeThrows(o, shooter, 2, offense, defense)
                return
            }
        }

        var putback: Player? = null
        var attempts = 0
        while (attempts < 6) {
            attempts++
            val shooter = putback ?: Rng.weighted(offense) { usage(o, it).pow(1.1) * (Ratings.offense(it) / 70.0).pow(1.2) }
            val type = if (putback != null) 0 else shotType(o, d, shooter, trail)
            var pMake = when (type) {
                0 -> 0.522 + (shooter[Attr.INS] - n[0]) * 0.0055 - (intD - n[5]) * 0.0038
                1 -> 0.365 + (shooter[Attr.MID] - n[1]) * 0.0047 - (perD - n[6]) * 0.003
                else -> 0.312 + (shooter[Attr.OUT] - n[2]) * 0.0043 - (perD - n[6]) * 0.0026
            }
            if (putback != null) pMake += 0.06
            pMake += (coaches[o].off - 70) * 0.0006 - (coaches[d].def - 70) * 0.0006 + skillOff[o] - skillDef[d]
            pMake += if (o == 0) 0.012 * homeEdge else -0.004 * homeEdge
            when (def(d)) {
                Defense.ZONE -> if (type == 0) pMake -= 0.03 else if (type == 2) pMake += 0.012
                Defense.PRESS -> if (type == 0) pMake += 0.03
                Defense.PACKLINE -> if (type == 0) pMake -= 0.04 else if (type == 2) pMake += 0.018
                else -> {}
            }
            if (off(o) == Offense.GRIND) pMake += 0.015
            if (off(o) == Offense.UPTEMPO && type == 0) pMake += 0.01
            if (shooter.has(Trait.CLUTCH) && period >= 2 && clock < 120 && abs(trail) <= 5) pMake += 0.03
            // Shots at the horn are rushed and contested.
            if (clock <= 1.0) pMake *= if (shooter.has(Trait.CLUTCH)) 0.8 else 0.68
            pMake = pMake.coerceIn(0.12, 0.82)

            val foulP = when (type) { 0 -> 0.2; 1 -> 0.06; else -> 0.022 }
            val fouled = Rng.chance(foulP)
            val made = Rng.chance(if (fouled) pMake * 0.62 else pMake)
            val pts = if (type == 2) 3 else 2
            val sl = line(o, shooter)
            sl.fga++
            if (type == 2) sl.tpa++
            val what = when (type) { 0 -> if (putback != null) "putback" else Rng.pick(listOf("layup", "dunk", "hook shot", "floater")); 1 -> "jumper"; else -> "three" }

            if (made) {
                sl.fgm++; sl.pts += pts
                if (type == 2) sl.tpm++
                val before = score[o] - score[d]
                val c = Clutch(o, shooter.name, what, period, clock, before, pts)
                lastBasket = c
                if (period >= 2 && clock <= 2.0 && before < 0 && before + pts == 0) tyingShot = c
                score[o] += pts
                var astText = ""
                val astP = when (type) { 0 -> if (putback != null) 0.0 else 0.42; 1 -> 0.38; else -> 0.74 }
                if (Rng.chance(astP)) {
                    val mates = offense.filter { it.id != shooter.id }
                    if (mates.isNotEmpty()) {
                        val passer = Rng.weighted(mates) { it[Attr.PAS].toDouble().pow(2.5) }
                        line(o, passer).ast++
                        astText = " (${passer.short} assists)"
                    }
                }
                val verb = when (type) { 2 -> "drains a three"; 1 -> "hits a $what"; else -> if (what == "dunk") "throws down a dunk" else "scores on a $what" }
                log(o, "${shooter.short} $verb$astText${if (clock <= 1.0) " at the buzzer!" else ""}", pts)
                if (fouled) {
                    val fouler = Rng.weighted(defense) { posFoul(it.pos) }
                    line(d, fouler).pf++; teamFouls[d]++
                    log(d, "And-one! Foul on ${fouler.short}", 0)
                    freeThrows(o, shooter, 1, offense, defense)
                    return
                }
                poss = d
                return
            }

            if (fouled) {
                sl.fga--; if (type == 2) sl.tpa--
                val fouler = Rng.weighted(defense) { posFoul(it.pos) }
                line(d, fouler).pf++; teamFouls[d]++
                log(d, "${fouler.short} fouls ${shooter.short} on the $what", 0)
                freeThrows(o, shooter, pts, offense, defense)
                return
            }

            // Missed shot
            var blocked = false
            val pBlk = when (type) { 0 -> 0.2 * (blkD / n[8]).pow(2); 1 -> 0.05; else -> 0.015 }
            if (Rng.chance(pBlk)) {
                val b = Rng.weighted(defense) { it[Attr.BLK].toDouble().pow(3) }
                line(d, b).blk++
                blocked = true
                log(d, "${b.short} blocks ${shooter.short}!", 0)
            } else log(o, "${shooter.short} misses the $what", 0)
            val reb = rebound(o, offense, defense, blocked)
            if (reb.first == o) {
                if (clock <= 1.0) { poss = d; return }
                val extra = 2.0 + Rng.d() * 5
                val used = min(extra, clock)
                elapse(used, offense, defense)
                putback = if (Rng.chance(0.5)) reb.second else null
                continue
            }
            poss = d
            return
        }
        poss = d
    }

    private fun posFoul(p: Pos) = when (p) { Pos.C -> 1.5; Pos.PF -> 1.3; Pos.SF -> 1.0; Pos.SG -> 0.85; Pos.PG -> 0.75 }
    private fun posReb(p: Pos) = when (p) { Pos.C -> 1.25; Pos.PF -> 1.2; Pos.SF -> 1.0; Pos.SG -> 0.8; Pos.PG -> 0.7 }

    private fun shotType(o: Int, d: Int, p: Player, trail: Int): Int {
        if (period >= 2 && clock < 25 && trail == -3) return 2
        val posIns = when (p.pos) { Pos.PG -> .8; Pos.SG -> .8; Pos.SF -> 1.0; Pos.PF -> 1.3; Pos.C -> 1.65 }
        val pos3 = when (p.pos) { Pos.PG -> 1.0; Pos.SG -> 1.15; Pos.SF -> .95; Pos.PF -> .55; Pos.C -> .16 }
        var wi = (p[Attr.INS] / 70.0).pow(3) * posIns
        var wm = (p[Attr.MID] / 70.0).pow(3) * 0.5
        var w3 = (p[Attr.OUT] / 70.0).pow(4) * pos3 * 1.2
        when (off(o)) {
            Offense.INSIDE -> { wi *= 1.4; w3 *= 0.72 }
            Offense.PERIMETER -> { w3 *= 1.45; wi *= 0.8 }
            Offense.UPTEMPO -> { w3 *= 1.12; wi *= 1.05; wm *= 0.85 }
            Offense.GRIND -> { wm *= 1.25 }
            else -> {}
        }
        when (def(d)) {
            Defense.ZONE -> { w3 *= 1.25; wi *= 0.85 }
            Defense.PACKLINE -> { w3 *= 1.15; wi *= 0.85 }
            Defense.PRESS -> { wi *= 1.1 }
            else -> {}
        }
        val x = Rng.d() * (wi + wm + w3)
        return when { x < wi -> 0; x < wi + wm -> 1; else -> 2 }
    }

    private fun rebound(o: Int, offense: List<Player>, defense: List<Player>, blocked: Boolean): Pair<Int, Player> {
        val d = 1 - o
        val oR = offense.map { it[Attr.REB] }.average()
        val dR = defense.map { it[Attr.REB] }.average()
        var pOff = 0.29 + (oR - dR) * 0.004
        if (off(o) == Offense.INSIDE) pOff += 0.02
        if (off(o) == Offense.UPTEMPO) pOff -= 0.02
        if (def(d) == Defense.ZONE) pOff += 0.02
        if (blocked) pOff += 0.04
        pOff = pOff.coerceIn(0.15, 0.45)
        return if (Rng.chance(pOff)) {
            val r = Rng.weighted(offense) { it[Attr.REB].toDouble().pow(2.5) * posReb(it.pos) }
            line(o, r).orb++
            log(o, "${r.short} grabs the offensive board", 0)
            o to r
        } else {
            val r = Rng.weighted(defense) { it[Attr.REB].toDouble().pow(2.5) * posReb(it.pos) }
            line(d, r).drb++
            d to r
        }
    }

    private fun freeThrows(o: Int, shooter: Player, n: Int, offense: List<Player>, defense: List<Player>) {
        val d = 1 - o
        val pct = ft(shooter)
        var made = 0
        var lastMade = true
        for (i in 0 until n) {
            val sl = line(o, shooter)
            sl.fta++
            lastMade = Rng.chance(pct)
            if (lastMade) { sl.ftm++; sl.pts++; score[o]++; made++ }
        }
        log(o, "${shooter.short} makes $made of $n free throw${if (n > 1) "s" else ""}", made)
        if (!lastMade) {
            val oReb = Rng.chance(0.14)
            if (oReb) {
                val r = Rng.weighted(offense) { it[Attr.REB].toDouble().pow(2.5) * posReb(it.pos) }
                line(o, r).orb++
                poss = o
                return
            }
            val r = Rng.weighted(defense) { it[Attr.REB].toDouble().pow(2.5) * posReb(it.pos) }
            line(d, r).drb++
        }
        poss = d
    }

    private fun endCheck() {
        if (clock > 0.5) return
        periodScores.last().let { it[0] = score[0] - periodScores.dropLast(1).sumOf { s -> s[0] }; it[1] = score[1] - periodScores.dropLast(1).sumOf { s -> s[1] } }
        if (period == 1) {
            log(-1, "Halftime: ${teams[1].abbr} ${score[1]}, ${teams[0].abbr} ${score[0]}", 0)
            period = 2; clock = 1200.0; teamFouls[0] = 0; teamFouls[1] = 0
            poss = 1 - firstPoss
            periodScores += IntArray(2)
            return
        }
        if (score[0] == score[1]) {
            if (forcedOt == null && tyingShot?.period == period) forcedOt = tyingShot
            period++; clock = 300.0
            log(-1, "Tied at ${score[0]}. Overtime!", 0)
            periodScores += IntArray(2)
            poss = if (Rng.chance(0.5)) 0 else 1
            return
        }
        finished = true
        log(-1, "Final: ${teams[1].abbr} ${score[1]}, ${teams[0].abbr} ${score[0]}${if (period > 2) " (${if (period == 3) "" else "${period - 2}"}OT)" else ""}", 0)
    }

    fun simToEnd() {
        var guard = 0
        while (!finished && guard++ < 2000) step()
    }

    val periodLabel get() = when { period == 1 -> "1st"; period == 2 -> "2nd"; period == 3 -> "OT"; else -> "${period - 2}OT" }

    fun boxScore(): BoxScore {
        fun side(s: Int) = lines[s].entries.map { (id, st) ->
            val p = lg.player(id)
            PlayerLine(id, p.short, p.pos, id in starters[s], st)
        }.sortedWith(compareByDescending<PlayerLine> { it.starter }.thenByDescending { it.s.sec })
        return BoxScore(side(0), side(1), periodScores.map { it.copyOf() })
    }

    /** Writes the result into the league: records, stats, rankings inputs, logs. */
    fun commit() {
        if (committed) return
        committed = true
        if (!finished) simToEnd()
        game.hs = score[0]; game.aws = score[1]; game.ot = max(0, period - 2)
        val userInvolved = game.involves(lg.userTeamId)
        val userConfTourney = game.type == GameType.CONF_TOURNEY && teams[0].confId == lg.user.confId
        if (userInvolved || game.type == GameType.NCAA || userConfTourney || lg.rivalry(game) != null) {
            game.box = boxScore()
        }
        val hw = score[0] > score[1]
        for (side in 0..1) {
            val t = teams[side]
            val won = if (side == 0) hw else !hw
            if (won) { t.w++; t.streak = if (t.streak > 0) t.streak + 1 else 1 } else { t.l++; t.streak = if (t.streak < 0) t.streak - 1 else -1 }
            if (game.type == GameType.CONF) { if (won) t.cw++ else t.cl++ }
            val c = coaches[side]
            if (won) c.w++ else c.l++
            val tot = StatLine(gp = 1)
            for ((id, st) in lines[side]) {
                val p = lg.playerOrNull(id) ?: continue
                if (st.sec <= 0 && st.fga == 0 && st.fta == 0) continue
                st.gp = 1
                if (id in starters[side]) st.gs = 1
                p.stats.add(st)
                if (game.type == GameType.NCAA) p.tourney.add(st)
                tot.add(st.copy().also { it.gp = 0; it.gs = 0 })
                if (t.id == lg.userTeamId) {
                    val oppSide = 1 - side
                    p.log += GameLogEntry(
                        game.id, teams[oppSide].id, game.site(t.id), won, "${score[side]}-${score[oppSide]}",
                        st.pts, st.reb, st.ast, st.stl, st.blk, st.tov, (st.sec / 60.0).roundToInt(),
                    )
                }
                // Development: playing time and production earn XP.
                val xp = (st.sec / 60.0 * 0.9 + (st.pts + st.reb + st.ast) * 0.6) * Ratings.devRate(p, Coaching.devOf(coaches[side]))
                Ratings.addXp(p, xp.roundToInt())
                Records.checkGame(lg, p, t, st)
                Careers.track(lg, p, st, game, t.id)
            }
            tot.gp = 1
            t.totals.add(tot)
        }
        teams[0].opp.add(lastTotals(1))
        teams[1].opp.add(lastTotals(0))
        Rankings.updateElo(lg, game)
        Rivalries.afterGame(lg, game)
        Fans.afterGame(lg, game)
        Moments.afterGame(lg, game, buzzerBeater, forcedOt)
    }

    private fun lastTotals(side: Int): StatLine {
        val tot = StatLine(gp = 1)
        for ((_, st) in lines[side]) { val c = st.copy(); c.gp = 0; c.gs = 0; tot.add(c) }
        return tot
    }
}
