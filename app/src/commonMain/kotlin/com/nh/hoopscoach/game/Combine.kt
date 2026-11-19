package com.nh.hoopscoach.game

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** One prospect's combine: measurements, athletic testing and shooting drills, and how it moved his stock. */
@Serializable
class CombineResult(
    val pid: Int,
    val name: String,
    /** Inches, without shoes. */
    val height: Double,
    val wingspan: Double,
    val reach: Double,
    val weight: Int,
    /** Max vertical, inches. */
    val vert: Double,
    /** Lane agility and three-quarter-court sprint, seconds (lower is better). */
    val lane: Double,
    val sprint: Double,
    /** Spot-up makes out of 25, off-the-dribble makes out of 15. */
    val spotUp: Int,
    val offDribble: Int,
    /** Where he stands in the class (a sum of z-scores), and the spots he moved on the board. */
    var score: Double = 0.0,
    var move: Int = 0,
    /** What stood out ("7'2\" wingspan, best in the class"). */
    val notes: MutableList<String> = mutableListOf(),
    /** Percentile in the class for each measure (the user's player's combine). */
    val pct: MutableMap<String, Int> = mutableMapOf(),
) {
    val length get() = wingspan - height
}

/**
 * The draft combine: measurements (height without shoes, wingspan, standing reach, weight), athletic testing
 * (max vertical, lane agility, three-quarter sprint) and shooting drills (spot-up, off the dribble). Everything
 * comes from a player's body and ratings, with a test-day swing, so the numbers tell the truth about him, mostly.
 * A class's results are ranked against each other; standing out moves a prospect's stock.
 */
object Combine {
    /** Inches as 6'5.75". */
    fun feet(inches: Double): String {
        val q = (inches * 4).roundToInt() / 4.0
        val ft = (q / 12).toInt()
        val inch = q - ft * 12
        val s = if (inch == inch.toInt().toDouble()) "${inch.toInt()}" else String.format(java.util.Locale.US, "%.2f", inch).trimEnd('0').trimEnd('.')
        return "$ft'$s\""
    }

    fun f1(v: Double) = String.format(java.util.Locale.US, "%.1f", v)
    fun f2(v: Double) = String.format(java.util.Locale.US, "%.2f", v)

    private fun athleticism(p: Player) = (p[Attr.INS] + p[Attr.STL] + p[Attr.BLK] + p[Attr.PER]) / 4.0

    /**
     * His combine. [effort] (each 0 to 1, 0.5 typical) is how the user's player timed each event: vertical, lane,
     * sprint, then the share of his 25 spot-up shots that went in (or -1 to let his rating decide).
     */
    fun measure(p: Player, effort: DoubleArray? = null, spotMakes: Int = -1, body: CombineResult? = null): CombineResult {
        fun e(i: Int) = (effort?.getOrNull(i) ?: (0.5 + Rng.gauss() * 0.18)).coerceIn(0.0, 1.0) - 0.5
        val big = p.pos == Pos.C || p.pos == Pos.PF
        val guard = p.pos == Pos.PG || p.pos == Pos.SG
        // His body is his body: measured once, kept.
        val h = body?.height ?: (p.height - 1.25 + Rng.gauss() * 0.3)
        val wing = body?.wingspan ?: (h + 2.5 + (p[Attr.BLK] + p[Attr.IDF] + p[Attr.REB] - 180) * 0.02 + Rng.gauss() * 1.6).coerceIn(h - 1.5, h + 9.5)
        val reach = body?.reach ?: (h * 1.33 + (wing - h) * 0.45)
        val ath = athleticism(p) - 62
        val vert = (32.5 + ath * 0.2 + (if (guard) 2.0 else if (big) -2.5 else 0.0) + e(0) * 6 + Rng.gauss() * 1.6).coerceIn(23.0, 46.5)
        val lane = (11.35 - ((p[Attr.HND] + p[Attr.PER]) / 2.0 - 62) * 0.013 + (if (big) 0.45 else 0.0) - e(1) * 0.5 + Rng.gauss() * 0.18).coerceIn(10.0, 13.2)
        val sprint = (3.27 - ath * 0.0045 + (if (big) 0.12 else 0.0) - e(2) * 0.12 + Rng.gauss() * 0.05).coerceIn(2.95, 3.75)
        val spot = if (spotMakes >= 0) spotMakes.coerceIn(0, 25)
            else (0 until 25).count { Rng.chance((0.45 + (p[Attr.OUT] - 60) * 0.011).coerceIn(0.2, 0.92)) }
        val off = (0 until 15).count { Rng.chance((0.35 + (p[Attr.MID] - 60) * 0.009 + (p[Attr.HND] - 60) * 0.003).coerceIn(0.15, 0.88)) }
        return CombineResult(p.id, p.name, h, wing, reach, p.weight, vert, lane, sprint, spot, off)
    }

    /** Ranks a class against itself: each result's standing, and what stood out in it. */
    fun standings(cls: List<CombineResult>) {
        if (cls.size < 2) return
        fun z(f: (CombineResult) -> Double): Map<Int, Double> {
            val v = cls.map(f)
            val m = v.average()
            val sd = sqrt(v.sumOf { (it - m) * (it - m) } / v.size).coerceAtLeast(1e-6)
            return cls.associate { it.pid to (f(it) - m) / sd }
        }
        val length = z { it.length }
        val vert = z { it.vert }
        val lane = z { -it.lane }
        val sprint = z { -it.sprint }
        val spot = z { it.spotUp.toDouble() }
        val off = z { it.offDribble.toDouble() }
        for (r in cls) {
            r.score = (length.getValue(r.pid) + vert.getValue(r.pid) * 0.8 + lane.getValue(r.pid) * 0.6 + sprint.getValue(r.pid) * 0.6 +
                spot.getValue(r.pid) + off.getValue(r.pid) * 0.7) / 4.7
            r.notes.clear()
        }
        fun best(label: String, by: (CombineResult) -> Double, show: (CombineResult) -> String) {
            cls.maxByOrNull(by)?.notes?.add("$label: ${show(cls.maxBy(by))}, best in the class")
        }
        best("Wingspan", { it.wingspan }) { feet(it.wingspan) }
        best("Max vertical", { it.vert }) { "${f1(it.vert)}\"" }
        best("Lane agility", { -it.lane }) { "${f2(it.lane)} s" }
        best("Sprint", { -it.sprint }) { "${f2(it.sprint)} s" }
        best("Spot-up shooting", { it.spotUp + it.offDribble * 0.5 }) { "${it.spotUp} of 25" }
        for (r in cls) {
            if (r.length >= 6.5 && r.notes.none { it.startsWith("Wingspan") }) r.notes += "Long: a ${feet(r.wingspan)} wingspan, +${f1(r.length)}\""
            if (r.vert >= 39 && r.notes.none { it.startsWith("Max vertical") }) r.notes += "Explosive: ${f1(r.vert)}\" max vertical"
            if (r.spotUp >= 20 && r.notes.none { it.startsWith("Spot-up") }) r.notes += "Shot it: ${r.spotUp} of 25 spot-up"
            if (r.spotUp <= 8) r.notes += "Shaky shooting: ${r.spotUp} of 25 spot-up"
            if (r.length <= 0.0) r.notes += "Short arms: ${feet(r.wingspan)} wingspan"
        }
    }

    /** "6'6\" · 7'0\" wingspan · 37.5\" vert · 10.95 lane · 3.19 sprint · 19/25 spot-up". */
    fun line(r: CombineResult) = "${feet(r.height)} · ${feet(r.wingspan)} wingspan · ${f1(r.vert)}\" vert · ${f2(r.lane)} lane · ${f2(r.sprint)} sprint · ${r.spotUp}/25 spot-up"

    /** How far up (or down) the class he is in one stat, as a percentile. */
    fun percentile(cls: List<CombineResult>, r: CombineResult, f: (CombineResult) -> Double): Int =
        (cls.count { f(it) < f(r) } * 100.0 / cls.size.coerceAtLeast(1)).roundToInt()
}

/**
 * The user's player at the combine (a player career, before Draft Night): measured, then the vertical, the lane
 * agility and the sprint on a timing meter (how well he times each one is part of the result), ten spot-up shots
 * on the shot meter, and interviews with three teams. His results are ranked against the rest of the class, and
 * the combine moves him on the big board: that's where he goes on Draft Night.
 */
object PlayerCombine {
    /** In this year's draft (declared, or out of eligibility), before Draft Night, and not yet been. */
    fun open(lg: League): Boolean {
        val r = lg.road ?: return false
        val h = lg.playerOrNull(lg.heroId) ?: return false
        return lg.playerMode && !lg.pro && !r.ended && !r.longWay && !r.goingPro && lg.offseason?.step == OffStep.ROSTER &&
            (r.declared || h.year >= 4) && r.combineSeason != lg.season
    }

    fun done(lg: League) = lg.road?.let { it.combineSeason == lg.season && it.combine != null } == true

    /** A team's interview: who they are, what they ask, three ways to answer, the one they like and the one they don't. */
    class Talk(val team: String, val kind: String, val question: String, val answers: List<String>, val likes: Int, val dislikes: Int)

    private val KINDS = listOf(
        Triple("A contender looking for a piece", 2, 1),
        Triple("A rebuilding team hunting for a star", 1, 2),
        Triple("A front office that trusts the numbers", 0, 1),
    )

    private val QUESTIONS = listOf(
        "What's the biggest hole in your game?" to listOf("My jumper off the dribble. I'm in the gym on it every day.", "Honestly? I don't have one.", "Whatever you need me to be, I'll be."),
        "Why should we take you over the guy ranked ahead of you?" to listOf("Watch the tape: the numbers back me up.", "Because I'm better. Simple.", "I'll outwork him, and I make the guys around me better."),
        "Where do you see yourself in three years?" to listOf("Getting a little better at something every season.", "An All-Star.", "Starting on a team that's winning."),
    )

    /** The three interviews, the same every time he looks this season. */
    fun talks(lg: League): List<Talk> {
        val rnd = kotlin.random.Random(lg.season * 31L + lg.heroId)
        val teams = LongWay.franchises(lg).shuffled(rnd).take(3)
        val kinds = KINDS.shuffled(rnd)
        return teams.mapIndexed { i, (_, team) ->
            val (q, a) = QUESTIONS[i]
            val (kind, likes, dislikes) = kinds[i]
            Talk(team, kind, q, a, likes, dislikes)
        }
    }

    /** His body, measured first (kept for the rest of the combine). */
    fun body(lg: League): CombineResult? = lg.playerOrNull(lg.heroId)?.let { Combine.measure(it) }

    /**
     * The combine's done: [effort] for the vertical, lane and sprint (0 to 1 each), [spotOf10] spot-up makes,
     * [answers] his answer to each interview. Ranks him against the class and moves him on the big board.
     */
    fun finish(lg: League, body: CombineResult?, effort: DoubleArray, spotOf10: Int, answers: List<Int>): CombineResult? {
        val r = lg.road ?: return null
        val h = lg.playerOrNull(lg.heroId) ?: return null
        val before = Offseason.draftBoard(lg)[h.id] ?: 0
        val mine = Combine.measure(h, effort, (spotOf10 * 2.5).roundToInt(), body)
        // The class: everyone else likely to go in the first sixty.
        val board = Offseason.draftBoard(lg)
        val cls = lg.players.filter { it.teamId >= 0 && it.id != h.id && (board[it.id] ?: 999) <= 70 }.map { Combine.measure(it) } + mine
        Combine.standings(cls)
        mine.pct["Wingspan"] = Combine.percentile(cls, mine) { it.wingspan }
        mine.pct["Max vertical"] = Combine.percentile(cls, mine) { it.vert }
        mine.pct["Lane agility"] = Combine.percentile(cls, mine) { -it.lane }
        mine.pct["Sprint"] = Combine.percentile(cls, mine) { -it.sprint }
        mine.pct["Spot-up"] = Combine.percentile(cls, mine) { it.spotUp.toDouble() }
        val talks = talks(lg)
        r.combineTalks.clear()
        var interview = 0
        talks.forEachIndexed { i, t ->
            val a = answers.getOrElse(i) { -1 }
            val v = when (a) { t.likes -> 1; t.dislikes -> -1; else -> 0 }
            interview += v
            r.combineTalks += "${t.team}: " + when (v) { 1 -> "they loved it"; -1 -> "it didn't land"; else -> "fine, nothing more" }
        }
        r.combine = mine
        r.combineSeason = lg.season
        // A great combine is worth a few spots near the top and more in the middle; one bad day can't sink a career.
        r.combineBoost = (mine.score * 2.0 + interview * 0.5).coerceIn(-3.5, 3.5)
        val after = Offseason.draftBoard(lg)[h.id] ?: before
        r.combineBefore = before
        r.combineAfter = after
        mine.move = before - after
        lg.news += News(lg.season, lg.day,
            "${h.name} at the combine: " + (mine.notes.firstOrNull()?.replaceFirstChar { it.lowercase() } ?: "${Combine.f1(mine.vert)}-inch vertical, ${mine.spotUp} of 25 spot-up"),
            true, kind = "draft", pid = h.id,
            body = Combine.line(mine) + ". " + when {
                mine.move > 0 -> "He moves up ${mine.move} spots on the big board, to No. $after."
                mine.move < 0 -> "He slips ${-mine.move} spots on the big board, to No. $after."
                else -> "He holds at No. $after on the big board."
            })
        return mine
    }
}
