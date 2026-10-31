package com.nh.hoopscoach.game

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
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
    /** Player mode: the user's player is in this play. */
    val hero: Boolean = false,
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
    /**
     * The rules of the level. NBA: four 12-minute quarters, a 24-second shot clock (14 after an
     * offensive rebound), six fouls to foul out, and the penalty from a team's fifth foul in a quarter
     * (fourth in overtime) or its second in the last two minutes. College: two 20-minute halves, a
     * 30-second shot clock (20 after an offensive rebound), five fouls, a one-and-one from the seventh
     * team foul in a half and two shots from the tenth. Overtime is five minutes in both.
     */
    val regPeriods = if (lg.pro) 4 else 2
    val periodLen = if (lg.pro) 720.0 else 1200.0
    /** Half of regulation (24 minutes in the pros, 20 in college): the rotation and the win chart work in halves. */
    val half = if (lg.pro) 1440.0 else 1200.0
    private val foulLimit = if (lg.pro) 6 else 5
    private val shotClock = if (lg.pro) 24.0 else 30.0
    var period = 1
        private set
    var clock = periodLen
        private set
    /** The last period of regulation, or overtime: when games are decided. */
    val finalPeriod get() = period >= regPeriods
    val overtime get() = period > regPeriods
    private var poss = if (Rng.chance(0.5)) 0 else 1
    private val firstPoss = poss
    private val teamFouls = IntArray(2)
    /** Pro team fouls in the last two minutes of a period (the second one is a penalty foul). */
    private val lateFouls = IntArray(2)
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
    // ---------- player mode: the user's player ----------

    private val heroId = if (lg.playerMode) lg.heroId else -1
    private val heroSide = when {
        heroId < 0 -> -1
        rosters[0].any { it.id == heroId } -> 0
        rosters[1].any { it.id == heroId } -> 1
        else -> -1
    }
    private val heroShort = if (heroSide >= 0) lg.playerOrNull(heroId)?.short else null
    /** Fatigue costs the user's player up to 9 points of shooting percentage. */
    private val heroTired = ((lg.road?.fatigue ?: 0) - 40).coerceAtLeast(0) * 0.0015
    /** A shooting coach helps the user's player's jump shots, and his big shots most. */
    private val heroJumper = if (heroId >= 0) Road.jumpShotBonus(lg) else 0.0
    private val heroClutch = if (heroId >= 0) Road.clutchBonus(lg) else 0.0
    /** A Game 7, an elimination game or a close-out: more big moments, and they start earlier. */
    val bigNight: String = if (heroSide >= 0) BigNights.stakes(lg, game) else ""

    /** The three ways to play a big moment. */
    enum class MomentChoice { PULL_UP, DRIVE, KICK }

    /** A late, close possession for the user's player, with each option's chance of scoring. */
    class BigMoment(val period: Int, val clock: Double, val margin: Int, val options: List<Triple<MomentChoice, String, Int>>)

    /** Live games in player mode pause for big moments; simmed games never do. */
    var interactive = false
    private var momentsAsked = 0
    private var forced: MomentChoice? = null
    /** What happened on the last big moment, for the live screen. */
    var lastMoment: String? = null
        private set

    private class DefStats(val stealD: Double, val perD: Double, val intD: Double, val blkD: Double)

    private fun defStats(defense: List<Player>): DefStats {
        val idfs = defense.map { it[Attr.IDF] }
        val blks = defense.map { it[Attr.BLK] }
        return DefStats(
            defense.map { it[Attr.STL] }.average(), defense.map { it[Attr.PER] }.average(),
            idfs.average() * 0.5 + idfs.max() * 0.5, blks.average() * 0.4 + blks.max() * 0.6,
        )
    }

    /** Chance a shot of [type] goes in (0 inside, 1 mid-range, 2 three), as the regular possession figures it. */
    private fun makeChance(o: Int, d: Int, shooter: Player, type: Int, ds: DefStats, trail: Int): Double {
        var p = when (type) {
            0 -> 0.522 + (shooter[Attr.INS] - n[0]) * 0.0055 - (ds.intD - n[5]) * 0.0038
            1 -> 0.365 + (shooter[Attr.MID] - n[1]) * 0.0047 - (ds.perD - n[6]) * 0.003
            else -> 0.312 + (shooter[Attr.OUT] - n[2]) * 0.0043 - (ds.perD - n[6]) * 0.0026
        }
        p += (coaches[o].off - 70) * 0.0006 - (coaches[d].def - 70) * 0.0006 + skillOff[o] - skillDef[d]
        p += if (o == 0) 0.012 * homeEdge else -0.004 * homeEdge
        if (shooter.has(Trait.CLUTCH) && abs(trail) <= 5) p += 0.03
        if (shooter.id == heroId) p += (if (type > 0) heroJumper + heroClutch else 0.0) - heroTired
        return p.coerceIn(0.12, 0.82)
    }

    private fun pullType(hero: Player, trail: Int) = if (trail == -3 || hero[Attr.OUT] >= hero[Attr.MID] + 3) 2 else 1

    private fun kickTarget(offense: List<Player>, hero: Player) =
        offense.filter { it.id != hero.id }.maxByOrNull { it[Attr.OUT] * 1.0 + it[Attr.MID] * 0.3 }

    /** A big moment is due: the user's team has the ball, late and close, with him on the floor. */
    fun pendingMoment(): BigMoment? {
        val big = bigNight.isNotEmpty()
        if (!interactive || finished || heroSide < 0 || poss != heroSide || momentsAsked >= (if (big) 5 else 3)) return null
        if (!finalPeriod || clock > (if (big) 180 else 120) || clock < 1.0) return null
        val margin = score[heroSide] - score[1 - heroSide]
        val close = if (big) 7 else 5
        if (margin < -close || margin > close || (margin > 0 && clock < 40)) return null
        val offense = lineup(heroSide)
        val hero = offense.firstOrNull { it.id == heroId } ?: return null
        val o = heroSide
        val d = 1 - o
        val ds = defStats(lineup(d))
        val type = pullType(hero, margin)
        val pull = makeChance(o, d, hero, type, ds, margin) - 0.02
        val driveTo = (0.07 + (ds.stealD - hero[Attr.HND]) * 0.002).coerceIn(0.03, 0.18)
        val drive = (1 - driveTo) * makeChance(o, d, hero, 0, ds, margin)
        val mate = kickTarget(offense, hero)
        val kickTo = (0.04 + (ds.stealD - hero[Attr.PAS]) * 0.0015).coerceIn(0.02, 0.12)
        val kick = if (mate == null) 0.0 else (1 - kickTo) * (makeChance(o, d, mate, 2, ds, margin) + 0.04 + (hero[Attr.PAS] - 70) * 0.001).coerceAtMost(0.8)
        fun pct(x: Double) = (x * 100).roundToInt()
        return BigMoment(period, clock, margin, listOfNotNull(
            Triple(MomentChoice.PULL_UP, if (type == 2) "Pull-up three" else "Pull-up jumper", pct(pull)),
            Triple(MomentChoice.DRIVE, "Drive to the rim", pct(drive)),
            mate?.let { Triple(MomentChoice.KICK, "Kick to ${it.short} for three", pct(kick)) },
        ))
    }

    /** Play the big moment the user chose, then carry on. */
    fun playMoment(choice: MomentChoice) {
        momentsAsked++
        forced = choice
        lastMoment = null
        step()
    }

    private fun heroPossession(o: Int, offense: List<Player>, defense: List<Player>, trail: Int, hero: Player, choice: MomentChoice) {
        val d = 1 - o
        val ds = defStats(defense)
        val road = lg.road
        road?.let { it.moments++ }
        fun stolen(loser: Player, how: String) {
            line(o, loser).tov++
            val stealer = Rng.weighted(defense) { it[Attr.STL].toDouble().pow(3) }
            line(d, stealer).stl++
            log(d, "${stealer.short} $how ${loser.short}!", 0)
            lastMoment = "Turned over. ${stealer.short} $how ${loser.short}."
            poss = d
        }
        when (choice) {
            MomentChoice.PULL_UP -> heroShot(o, d, hero, pullType(hero, trail), offense, defense, ds, trail, -0.02, 0.04, null)
            MomentChoice.DRIVE -> {
                if (Rng.chance((0.07 + (ds.stealD - hero[Attr.HND]) * 0.002).coerceIn(0.03, 0.18))) { stolen(hero, "strips"); return }
                heroShot(o, d, hero, 0, offense, defense, ds, trail, 0.0, 0.3, null)
            }
            MomentChoice.KICK -> {
                val mate = kickTarget(offense, hero) ?: return heroShot(o, d, hero, 1, offense, defense, ds, trail, 0.0, 0.04, null)
                if (Rng.chance((0.04 + (ds.stealD - hero[Attr.PAS]) * 0.0015).coerceIn(0.02, 0.12))) { stolen(hero, "picks off the pass from"); return }
                heroShot(o, d, mate, 2, offense, defense, ds, trail, 0.04 + (hero[Attr.PAS] - 70) * 0.001, 0.02, hero)
            }
        }
    }

    /** One shot in a big moment: the same math as any shot, with the play's bonus or contest. */
    private fun heroShot(
        o: Int, d: Int, shooter: Player, type: Int, offense: List<Player>, defense: List<Player>,
        ds: DefStats, trail: Int, bonus: Double, foulP: Double, passer: Player?,
    ) {
        val pMake = (makeChance(o, d, shooter, type, ds, trail) + bonus).coerceIn(0.12, 0.85)
        val fouled = Rng.chance(foulP)
        val made = Rng.chance(if (fouled) pMake * 0.62 else pMake)
        val pts = if (type == 2) 3 else 2
        val sl = line(o, shooter)
        sl.fga++
        if (type == 2) sl.tpa++
        val what = when (type) { 0 -> "layup"; 1 -> "pull-up jumper"; else -> "three" }
        if (made) {
            sl.fgm++; sl.pts += pts
            if (type == 2) sl.tpm++
            val before = score[o] - score[d]
            val c = Clutch(o, shooter.name, what, period, clock, before, pts)
            lastBasket = c
            if (finalPeriod && clock <= 2.0 && before < 0 && before + pts == 0) tyingShot = c
            score[o] += pts
            if (passer != null) line(o, passer).ast++
            log(o, "★ ${shooter.short} ${if (type == 2) "drains the three" else "scores on the $what"}${passer?.let { " (${it.short} assists)" } ?: ""}", pts)
            lg.road?.let { it.momentsWon++ }
            lastMoment = if (passer != null) "Assist! ${shooter.short} buries the three." else "Bucket! ${shooter.short} hits the $what."
            if (fouled) {
                val fouler = pickFouler(d, defense)
                line(d, fouler).pf++; teamFoul(d)
                lastMoment += " And one!"
                freeThrows(o, shooter, 1, offense, defense)
                return
            }
            poss = d
            return
        }
        if (fouled) {
            sl.fga--; if (type == 2) sl.tpa--
            val fouler = pickFouler(d, defense)
            line(d, fouler).pf++; teamFoul(d)
            log(d, "★ ${fouler.short} fouls ${shooter.short} on the $what", 0)
            val before = score[o]
            freeThrows(o, shooter, pts, offense, defense)
            val made = score[o] - before
            if (made > 0 && shooter.id == heroId) lg.road?.let { it.momentsWon++ }
            lastMoment = "Fouled on the $what: $made of $pts at the line."
            return
        }
        log(o, "★ ${shooter.short} misses the $what", 0)
        lastMoment = "No good. ${shooter.short} misses the $what."
        val reb = rebound(o, offense, defense, false)
        poss = if (reb.first == o) o else d
    }

    private val skillOff = DoubleArray(2) { if (coaches[it].has(CoachSkill.OFF_TACTICIAN)) 0.008 else 0.0 }
    private val skillDef = DoubleArray(2) { if (coaches[it].has(CoachSkill.DEF_TACTICIAN)) 0.008 else 0.0 }

    companion object {
        /**
         * League-wide centers for the shooting and defense formulas, measured from every team's
         * starters. Keeps percentages stable even if ratings drift up or down over many seasons.
         * The offsets keep the original calibration: center = norm + offset.
         */
        private val OFFSET = doubleArrayOf(-1.31, -2.63, 0.48, -0.41, -3.43, -2.8, -1.45, -3.92, -2.93)

        /** Chance of a non-shooting foul on a possession, and a scale on shooting fouls, set so fouls and free throws match each level. */
        const val NON_SHOOTING_PRO = 0.08
        const val NON_SHOOTING_COLLEGE = 0.13
        const val SHOOTING_FOUL_PRO = 1.0
        const val SHOOTING_FOUL_COLLEGE = 1.15

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

    private fun fouledOut(side: Int, id: Int) = (lines[side][id]?.pf ?: 0) >= foulLimit

    /**
     * Who commits a defensive foul: never a player who has already fouled out, and players in
     * foul trouble play more carefully. Big men foul most.
     */
    private fun pickFouler(d: Int, defense: List<Player>): Player {
        val ok = defense.filter { !fouledOut(d, it.id) }.ifEmpty { defense }
        return Rng.weighted(ok) { posFoul(it.pos) * (1.0 - 0.6 * (lines[d][it.id]?.pf ?: 0) / foulLimit.toDouble()) }
    }

    /** Seconds played in the current half of regulation, for the rotation (0 in overtime). */
    private fun halfElapsed() = when {
        overtime -> 0.0
        lg.pro -> ((period - 1) % 2) * periodLen + (periodLen - clock)
        else -> periodLen - clock
    }

    /** Coaches sit a starter in foul trouble: college, two in the first half or four in the second; pros, one more than the quarter. */
    private fun foulTrouble(pf: Int) = if (lg.pro) {
        period <= 4 && pf >= period + 1 && !(period == 4 && clock <= 300)
    } else (period == 1 && pf >= 2 && clock > 300) || (period == 2 && pf >= 4 && clock > 240)

    private fun lineup(side: Int): List<Player> {
        val t = teams[side]
        val roster = rosters[side]
        val lookup = byId[side]
        val elapsed = halfElapsed()
        val late = finalPeriod && clock < 240
        val margin = score[side] - score[1 - side]
        val garbage = period == regPeriods && clock < 270 && abs(margin) >= 18
        val chosen = ArrayList<Player>(5)
        for ((i, s) in t.plan.slots.withIndex()) {
            if (i >= 5) break
            val starterSec = s.starterMin * 30.0 * half / 1200.0
            val reserveSec = half - starterSec
            val a = starterSec * 0.55
            var useStarter = elapsed < a || elapsed >= a + reserveSec
            if (overtime || (late && abs(margin) <= 12)) useStarter = true
            val st = lookup[s.starter]
            val rs = lookup[s.reserve]
            // Foul trouble: sit the starter.
            if (st != null && useStarter && rs != null) {
                if (foulTrouble(lines[side][st.id]?.pf ?: 0)) useStarter = false
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
        // The pro game is faster: a shorter shot clock and about 100 possessions a game.
        if (lg.pro) base *= 0.81
        // Nobody holds it past the shot clock.
        return (base * (0.6 + Rng.d() * 0.8)).coerceAtMost(shotClock - 0.5)
    }

    /** A team foul, and the pro count of fouls in the last two minutes of the period. */
    private fun teamFoul(d: Int) {
        teamFouls[d]++
        if (lg.pro && clock <= 120) lateFouls[d]++
    }

    /**
     * Free throws a non-shooting foul by [d] gives: 0 (side out), 1 for a college one-and-one, or 2.
     * Called after the foul is counted.
     */
    private fun penaltyShots(d: Int): Int = if (lg.pro) {
        if (teamFouls[d] >= (if (overtime) 4 else 5) || lateFouls[d] >= 2) 2 else 0
    } else when {
        teamFouls[d] >= 10 -> 2
        teamFouls[d] >= 7 -> 1
        else -> 0
    }

    /** A non-shooting foul in the penalty: two shots, or a college one-and-one. */
    private fun bonusFreeThrows(o: Int, shooter: Player, offense: List<Player>, defense: List<Player>, shots: Int) =
        if (shots == 1) freeThrows(o, shooter, 2, offense, defense, oneAndOne = true) else freeThrows(o, shooter, 2, offense, defense)

    private fun usage(side: Int, p: Player): Double {
        for (s in teams[side].plan.slots) {
            if (s.starter == p.id) return s.starterUsg.toDouble()
            if (s.reserve == p.id) return s.reserveUsg.toDouble()
        }
        return Gameplan.usageFor(p, lg.pro).toDouble() * 0.8
    }

    /** Free-throw percentage: about 78% in the NBA and 71% in college for the average shooter. */
    private fun ft(p: Player): Double = ((if (lg.pro) 0.675 else 0.715) + ((p[Attr.MID] + p[Attr.OUT]) / 2.0 - 66) * 0.0075).coerceIn(0.4, 0.95)

    private fun line(side: Int, p: Player) = lines[side].getOrPut(p.id) { StatLine() }

    private fun log(side: Int, text: String, pts: Int) {
        if (!pbp) return
        events += PlayEvent(period, clock.roundToInt().coerceAtLeast(0), side, text, score[0], score[1], pts, heroShort != null && text.contains(heroShort))
    }

    /**
     * Spotlight: play on until the user's player is in a play, a big moment comes up, or the game ends.
     * Returns how many possessions went by.
     */
    fun stepToHero(limit: Int = 400): Int {
        if (heroSide < 0) { step(); return 1 }
        val before = events.size
        var n = 0
        while (!finished && n < limit) {
            if (pendingMoment() != null) break
            step(); n++
            var i = before
            while (i < events.size) { if (events[i].hero) return n; i++ }
        }
        return n
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
        // Milk the clock with a late lead (but not past the shot clock); hurry when behind; hold for the last shot when tied.
        if (finalPeriod && clock < 60) {
            if (trail > 0) len = min(clock, shotClock - 1.0) else if (trail < 0) len = min(len, 8.0 + Rng.d() * 6)
            else if (clock <= shotClock) len = (clock - Rng.d() * 3).coerceAtLeast(min(clock, 0.5))
        }
        // Fouling on purpose when the defense trails late. Before the penalty it's only a side out, so they keep fouling.
        if (finalPeriod && clock < 40 && trail in 1..9) {
            val used = min(clock, 2.0 + Rng.d() * 4)
            elapse(used, offense, defense)
            val fouler = pickFouler(d, defense)
            line(d, fouler).pf++
            teamFoul(d)
            val shooter = Rng.weighted(offense) { it[Attr.HND].toDouble().pow(2) }
            val shots = penaltyShots(d)
            if (shots == 0) log(d, "${fouler.short} fouls ${shooter.short} to stop the clock. Not in the penalty yet: side out", 0)
            else {
                log(d, "${fouler.short} fouls ${shooter.short} to stop the clock", 0)
                bonusFreeThrows(o, shooter, offense, defense, shots)
            }
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
        // Whole seconds off the clock, so a game's minutes add up exactly (48 or 40, plus overtime).
        val before = clock
        clock -= sec
        val s = (floor(before) - floor(clock.coerceAtLeast(0.0))).toInt()
        val sides = if (poss == 0) arrayOf(a, b) else arrayOf(b, a)
        for (side in 0..1) for (p in sides[side]) line(side, p).sec += s
    }

    private fun runPossession(o: Int, offense: List<Player>, defense: List<Player>, trail: Int) {
        val f = forced
        if (f != null) {
            forced = null
            val hero = offense.firstOrNull { it.id == heroId }
            if (hero != null && o == heroSide) { heroPossession(o, offense, defense, trail, hero, f); return }
        }
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
        if (lg.pro) pTo *= 0.9
        pTo = pTo.coerceIn(0.07, 0.3)
        if (Rng.chance(pTo)) {
            val loser = if (Rng.chance(0.6)) handler else Rng.weighted(offense) { usage(o, it) }
            line(o, loser).tov++
            val kind = Rng.d()
            when {
                kind < 0.55 -> {
                    val stealer = Rng.weighted(defense) { it[Attr.STL].toDouble().pow(3) }
                    line(d, stealer).stl++
                    log(d, "${stealer.short} steals it from ${loser.short}", 0)
                }
                // An offensive foul is a personal foul. College counts it as a team foul; the NBA doesn't.
                kind < 0.67 && !fouledOut(o, loser.id) -> {
                    line(o, loser).pf++
                    if (!lg.pro) teamFouls[o]++
                    log(o, "Offensive foul on ${loser.short}", 0)
                }
                kind < 0.69 -> log(o, "Shot clock violation", 0)
                kind < 0.75 -> log(o, "${loser.short} is called for traveling", 0)
                else -> log(o, "Turnover by ${loser.short}", 0)
            }
            poss = d
            return
        }

        // Non-shooting foul: free throws only in the penalty; otherwise the offense keeps the ball.
        if (Rng.chance(if (lg.pro) NON_SHOOTING_PRO else NON_SHOOTING_COLLEGE)) {
            val fouler = pickFouler(d, defense)
            line(d, fouler).pf++
            teamFoul(d)
            val shots = penaltyShots(d)
            if (shots > 0) {
                val shooter = Rng.weighted(offense) { usage(o, it) }
                log(d, "Foul on ${fouler.short}${if (shots == 1) ", one-and-one for" else ", two shots for"} ${shooter.short}", 0)
                bonusFreeThrows(o, shooter, offense, defense, shots)
                return
            }
        }

        var putback: Player? = null
        var attempts = 0
        while (attempts < 6) {
            attempts++
            // Pro rosters are all good, so shots spread out more than in college.
            val shooter = putback ?: Rng.weighted(offense) {
                if (lg.pro) usage(o, it) * (Ratings.offense(it) / 70.0).pow(0.6) * proShotPos(it.pos)
                else usage(o, it).pow(1.1) * (Ratings.offense(it) / 70.0).pow(1.2)
            }
            val type = if (putback != null) 0 else shotType(o, d, shooter, trail)
            var pMake = when (type) {
                0 -> 0.522 + (shooter[Attr.INS] - n[0]) * 0.0055 - (intD - n[5]) * 0.0038
                1 -> 0.365 + (shooter[Attr.MID] - n[1]) * 0.0047 - (perD - n[6]) * 0.003
                else -> 0.312 + (shooter[Attr.OUT] - n[2]) * 0.0043 - (perD - n[6]) * 0.0026
            }
            if (putback != null) pMake += 0.06
            // Pros finish and shoot a little better than college players at the same ratings.
            if (lg.pro) pMake += if (type == 2) 0.014 else 0.028
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
            if (shooter.has(Trait.CLUTCH) && finalPeriod && clock < 120 && abs(trail) <= 5) pMake += 0.03
            if (shooter.id == heroId) pMake += (if (type > 0) heroJumper else 0.0) - heroTired
            // Shots at the horn are rushed and contested.
            if (clock <= 1.0) pMake *= if (shooter.has(Trait.CLUTCH)) 0.8 else 0.68
            pMake = pMake.coerceIn(0.12, 0.82)

            val foulP = when (type) { 0 -> 0.2; 1 -> 0.06; else -> 0.022 } * (if (lg.pro) SHOOTING_FOUL_PRO else SHOOTING_FOUL_COLLEGE)
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
                if (finalPeriod && clock <= 2.0 && before < 0 && before + pts == 0) tyingShot = c
                score[o] += pts
                var astText = ""
                // About half of made shots are assisted in college, closer to two-thirds in the pros.
                val astP = (when (type) { 0 -> if (putback != null) 0.0 else 0.47; 1 -> 0.43; else -> 0.82 } * (if (lg.pro) 1.22 else 1.0)).coerceAtMost(0.95)
                if (Rng.chance(astP)) {
                    val mates = offense.filter { it.id != shooter.id }
                    if (mates.isNotEmpty()) {
                        // The best passers, point guards above all, get most of the assists.
                        val passer = Rng.weighted(mates) { exp((it[Attr.PAS] - 70) / 14.0) * posAst(it.pos) }
                        line(o, passer).ast++
                        astText = " (${passer.short} assists)"
                    }
                }
                val verb = when (type) { 2 -> "drains a three"; 1 -> "hits a $what"; else -> if (what == "dunk") "throws down a dunk" else "scores on a $what" }
                log(o, "${shooter.short} $verb$astText${if (clock <= 1.0) " at the buzzer!" else ""}", pts)
                if (fouled) {
                    val fouler = pickFouler(d, defense)
                    line(d, fouler).pf++; teamFoul(d)
                    log(d, "And-one! Foul on ${fouler.short}", 0)
                    freeThrows(o, shooter, 1, offense, defense)
                    return
                }
                poss = d
                return
            }

            if (fouled) {
                sl.fga--; if (type == 2) sl.tpa--
                val fouler = pickFouler(d, defense)
                line(d, fouler).pf++; teamFoul(d)
                log(d, "${fouler.short} fouls ${shooter.short} on the $what", 0)
                freeThrows(o, shooter, pts, offense, defense)
                return
            }

            // Missed shot
            var blocked = false
            val pBlk = when (type) { 0 -> 0.23 * (blkD / n[8]).pow(2); 1 -> 0.055; else -> 0.017 } * (if (lg.pro) 1.25 else 1.0)
            if (Rng.chance(pBlk)) {
                // Rim protectors, big men above all, get most of the blocks.
                val b = Rng.weighted(defense) { (it[Attr.BLK] / 70.0).pow(4) * posBlk(it.pos) }
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

    private fun proShotPos(p: Pos) = when (p) { Pos.C -> 1.2; Pos.PF -> 1.1; else -> 1.0 }
    private fun posAst(p: Pos) = when (p) { Pos.PG -> 1.6; Pos.SG -> 1.15; Pos.SF -> 1.0; Pos.PF -> 0.7; Pos.C -> 0.6 }
    private fun posBlk(p: Pos) = when (p) { Pos.C -> 2.2; Pos.PF -> 1.6; Pos.SF -> 0.9; Pos.SG -> 0.5; Pos.PG -> 0.35 }
    private fun posFoul(p: Pos) = when (p) { Pos.C -> 1.5; Pos.PF -> 1.3; Pos.SF -> 1.0; Pos.SG -> 0.85; Pos.PG -> 0.75 }
    private fun posReb(p: Pos) = when (p) { Pos.C -> 1.25; Pos.PF -> 1.2; Pos.SF -> 1.0; Pos.SG -> 0.8; Pos.PG -> 0.7 }

    private fun shotType(o: Int, d: Int, p: Player, trail: Int): Int {
        if (finalPeriod && clock < 25 && trail == -3) return 2
        val posIns = when (p.pos) { Pos.PG -> .8; Pos.SG -> .8; Pos.SF -> 1.0; Pos.PF -> 1.3; Pos.C -> 1.65 }
        val pos3 = when (p.pos) { Pos.PG -> 1.0; Pos.SG -> 1.15; Pos.SF -> .95; Pos.PF -> .55; Pos.C -> .16 }
        var wi = (p[Attr.INS] / 70.0).pow(3) * posIns
        var wm = (p[Attr.MID] / 70.0).pow(3) * 0.5
        var w3 = (p[Attr.OUT] / 70.0).pow(if (lg.pro) 3 else 4) * pos3 * (if (lg.pro) 1.3 else 1.2)
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
        var pOff = (if (lg.pro) 0.25 else 0.285) + (oR - dR) * 0.004
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

    /** [n] free throws; a one-and-one stops after a miss on the first. */
    private fun freeThrows(o: Int, shooter: Player, n: Int, offense: List<Player>, defense: List<Player>, oneAndOne: Boolean = false) {
        val d = 1 - o
        val pct = ft(shooter)
        var made = 0
        var taken = 0
        var lastMade = true
        for (i in 0 until n) {
            val sl = line(o, shooter)
            sl.fta++
            taken++
            lastMade = Rng.chance(pct)
            if (lastMade) { sl.ftm++; sl.pts++; score[o]++; made++ }
            if (oneAndOne && !lastMade) break
        }
        log(o, if (oneAndOne && taken == 1) "${shooter.short} misses the front end of the one-and-one" else "${shooter.short} makes $made of $taken free throw${if (taken > 1) "s" else ""}", made)
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
        if (period < regPeriods) {
            val halftime = period == regPeriods / 2
            log(-1, "${if (halftime) "Halftime" else "End of the ${ordinalWord(period)} quarter"}: ${teams[1].abbr} ${score[1]}, ${teams[0].abbr} ${score[0]}", 0)
            // Pros: the team that lost the opening tip gets the ball to start the 2nd and 3rd quarters, the winner the 4th.
            poss = if (lg.pro) (if (period == 1 || period == 2) 1 - firstPoss else firstPoss) else 1 - firstPoss
            period++
            clock = periodLen
            // Team fouls reset each quarter in the pros and at halftime in college.
            if (lg.pro || halftime) { teamFouls[0] = 0; teamFouls[1] = 0 }
            lateFouls[0] = 0; lateFouls[1] = 0
            periodScores += IntArray(2)
            return
        }
        if (score[0] == score[1]) {
            if (forcedOt == null && tyingShot?.period == period) forcedOt = tyingShot
            period++; clock = 300.0
            log(-1, "Tied at ${score[0]}. Overtime!", 0)
            periodScores += IntArray(2)
            // Each pro overtime starts its own foul count; college fouls carry over from the second half.
            if (lg.pro) { teamFouls[0] = 0; teamFouls[1] = 0 }
            lateFouls[0] = 0; lateFouls[1] = 0
            poss = if (Rng.chance(0.5)) 0 else 1
            return
        }
        finished = true
        val ots = period - regPeriods
        log(-1, "Final: ${teams[1].abbr} ${score[1]}, ${teams[0].abbr} ${score[0]}${if (ots > 0) " (${if (ots == 1) "" else "$ots"}OT)" else ""}", 0)
    }

    private fun ordinalWord(n: Int) = when (n) { 1 -> "1st"; 2 -> "2nd"; 3 -> "3rd"; else -> "${n}th" }

    fun simToEnd() {
        var guard = 0
        while (!finished && guard++ < 2000) step()
    }

    /** Short label for the scoreboard: "Q3" or "2nd" (half), "OT", "2OT". */
    val periodLabel get() = when {
        overtime -> if (period - regPeriods == 1) "OT" else "${period - regPeriods}OT"
        lg.pro -> "Q$period"
        else -> ordinalWord(period)
    }

    /** Longer label for the live game's header: "3rd quarter", "2nd half", "OT". */
    val periodName get() = when {
        overtime -> periodLabel
        lg.pro -> "${ordinalWord(period)} quarter"
        else -> "${ordinalWord(period)} half"
    }

    fun boxScore(): BoxScore {
        fun side(s: Int) = lines[s].entries.map { (id, st) ->
            val p = lg.player(id)
            PlayerLine(id, p.short, p.pos, id in starters[s], st)
        }.sortedWith(compareByDescending<PlayerLine> { it.starter }.thenByDescending { it.s.sec })
        return BoxScore(side(0), side(1), periodScores.map { it.copyOf() }, quarters = lg.pro)
    }

    /** Writes the result into the league: records, stats, rankings inputs, logs. */
    fun commit() {
        if (committed) return
        committed = true
        if (!finished) simToEnd()
        game.hs = score[0]; game.aws = score[1]; game.ot = max(0, period - regPeriods)
        val userInvolved = game.involves(lg.userTeamId)
        val userConfTourney = game.type == GameType.CONF_TOURNEY && teams[0].confId == lg.user.confId
        if (userInvolved || game.type == GameType.NCAA || userConfTourney || lg.rivalry(game) != null) {
            game.box = boxScore()
        }
        val hw = score[0] > score[1]
        // Pro playoff games count in their own stat line, not the standings or the season's averages.
        // NBA Cup knockout games don't count anywhere, so every team still plays exactly 83.
        val playoff = game.type == GameType.PLAYOFF
        val counts = !playoff && game.type != GameType.CUP
        for (side in 0..1) {
            val t = teams[side]
            val won = if (side == 0) hw else !hw
            if (counts) {
                if (won) { t.w++; t.streak = if (t.streak > 0) t.streak + 1 else 1 } else { t.l++; t.streak = if (t.streak < 0) t.streak - 1 else -1 }
                if (game.type == GameType.CONF) { if (won) t.cw++ else t.cl++ }
            }
            val c = coaches[side]
            if (won) c.w++ else c.l++
            val tot = StatLine(gp = 1)
            for ((id, st) in lines[side]) {
                val p = lg.playerOrNull(id) ?: continue
                if (st.sec <= 0 && st.fga == 0 && st.fta == 0) continue
                st.gp = 1
                if (id in starters[side]) st.gs = 1
                if (counts) p.stats.add(st)
                if (game.type == GameType.NCAA || playoff) p.tourney.add(st)
                tot.add(st.copy().also { it.gp = 0; it.gs = 0 })
                if (t.id == lg.userTeamId) {
                    val oppSide = 1 - side
                    p.log += GameLogEntry(
                        game.id, teams[oppSide].id, game.site(t.id), won, "${score[side]}-${score[oppSide]}",
                        st.pts, st.reb, st.ast, st.stl, st.blk, st.tov, (st.sec / 60.0).roundToInt(),
                    )
                }
                // Development: playing time and production earn XP.
                // Pros grow slower in season, and not past their ceiling, so the league's ratings hold steady.
                // First-year pros stop at the rookie cap of 83 until their first summer.
                val proRate = if (!lg.pro) 1.0 else if (p.ovr >= p.pot || (ProLeague.cappedRookie(lg, p) && p.ovr >= ProLeague.ROOKIE_MAX)) 0.0 else 0.35
                val xp = (st.sec / 60.0 * 0.9 + (st.pts + st.reb + st.ast) * 0.6) * Ratings.devRate(p, Coaching.devOf(coaches[side])) *
                    (if (p.id == lg.heroId) Road.xpFactor(lg) else 1.0) * proRate
                Ratings.addXp(p, xp.roundToInt())
                if (lg.pro) ProLeague.rookieCap(lg, p)
                Records.checkGame(lg, p, t, st)
                Careers.track(lg, p, st, game, t.id)
            }
            tot.gp = 1
            if (counts) t.totals.add(tot)
        }
        if (counts) {
            teams[0].opp.add(lastTotals(1))
            teams[1].opp.add(lastTotals(0))
        }
        Rankings.updateElo(lg, game)
        if (!lg.pro) {
            Rivalries.afterGame(lg, game)
            Fans.afterGame(lg, game)
            Brand.afterGame(lg, game)
            Moments.afterGame(lg, game, buzzerBeater, forcedOt)
        }
        if (lg.pro) RecordBook.afterGame(lg, lines)
        if (lg.playerMode) Road.afterGame(lg, game, buzzerBeater)
    }

    private fun lastTotals(side: Int): StatLine {
        val tot = StatLine(gp = 1)
        for ((_, st) in lines[side]) { val c = st.copy(); c.gp = 0; c.gs = 0; tot.add(c) }
        return tot
    }
}
