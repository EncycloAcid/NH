package com.nh.hoopscoach.game

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/** Something that happened in the senior year: a weekend on the circuit, a big game, a visit. */
@Serializable
class HsLine(
    val title: String,
    val text: String,
    val good: Boolean = true,
    val pts: Int = -1,
    val reb: Int = -1,
    val ast: Int = -1,
    val rankFrom: Int = 0,
    val rankTo: Int = 0,
    val starsFrom: Int = 0,
    val starsTo: Int = 0,
    val offers: List<String> = emptyList(),
)

/** The user's player's last year of high school, from the summer circuit to the commitment ceremony. */
@Serializable
class HighSchool(
    val school: String,
    val mascot: String,
    val travelTeam: String,
    val state: String,
    val rankStart: Int,
    val starsStart: Int,
    val ovrStart: Int,
    var step: Int = 0,
    var skipped: Boolean = false,
    var w: Int = 0,
    var l: Int = 0,
    var gp: Int = 0,
    var pts: Int = 0,
    var reb: Int = 0,
    var ast: Int = 0,
    var stateResult: String = "",
    var stateTitle: Boolean = false,
    val honors: MutableList<String> = mutableListOf(),
    val log: MutableList<HsLine> = mutableListOf(),
    var lastResult: HsLine? = null,
    val visits: MutableList<Int> = mutableListOf(),
    val visitNotes: MutableMap<Int, String> = mutableMapOf(),
    /** Teammates' welcome from a visit: happiness when he signs there. */
    val happy: MutableMap<Int, Int> = mutableMapOf(),
    val finalists: MutableList<Int> = mutableListOf(),
    /** 0 live on TV, 1 in his gym, 2 on social media. */
    var setting: Int = 0,
    var committedTo: Int = -1,
    /** His national ranking when he signed. */
    var rankEnd: Int = 0,
    /** Followers gained along the way. */
    var buzz: Int = 0,
    /** Leadership earned along the way. */
    var lead: Int = 0,
) {
    val ppg get() = if (gp == 0) 0.0 else pts.toDouble() / gp
    val rpg get() = if (gp == 0) 0.0 else reb.toDouble() / gp
    val apg get() = if (gp == 0) 0.0 else ast.toDouble() / gp
}

/**
 * Senior year, before college: three stops on the summer travel circuit, a senior season with three big
 * moments, official visits to up to five schools, and a commitment ceremony. Every step can move his national
 * ranking (and his stars), bring new offers, grow his following and sharpen a rating or two. It all happens on
 * the new-career screen, before he signs; [apply] carries it into the career once he does.
 */
object SeniorYear {
    const val INTRO = 0
    val CIRCUIT = 1..3
    val SEASON = 4..6
    const val VISITS = 7
    const val CEREMONY = 8
    const val MAX_VISITS = 5

    class Choice(val text: String, val hint: String)
    class Prompt(val kicker: String, val title: String, val text: String, val choices: List<Choice>)
    class Stop(val name: String, val place: String, val blurb: String)

    val STOPS = listOf(
        Stop("Spring Shootout", "Atlanta", "The first big weekend of the travel season. Every college coach in the Southeast is on the baseline."),
        Stop("Summer Showcase", "Las Vegas", "July in Vegas: four games a day, three hundred coaches in the gym, and the best players in the class on the next court."),
        Stop("Top Prospects Camp", "Charlotte", "Invitation only: the best hundred players in the country, and every scout is taking notes."),
    )

    private val POWERS = listOf("Mount Carmel Prep", "Westbrook Academy", "Saint Vincent", "Riverside Christian", "Lakeview Prep", "Montrose Academy", "Hillcrest Prep", "Bishop Gray")
    private val SUFFIX = listOf("Central", "North", "South", "East", "West", "High", "Prep", "Christian", "Academy")
    private val MASCOTS = listOf("Eagles", "Panthers", "Wildcats", "Tigers", "Knights", "Warriors", "Lions", "Falcons", "Rams", "Bulldogs", "Hornets", "Mustangs")
    private val TRAVEL = listOf("Elite", "Stars", "Select", "Pride", "Rise", "Express", "Supreme")

    val STATES = mapOf(
        "AL" to "Alabama", "AK" to "Alaska", "AZ" to "Arizona", "AR" to "Arkansas", "CA" to "California", "CO" to "Colorado", "CT" to "Connecticut",
        "DE" to "Delaware", "DC" to "D.C.", "FL" to "Florida", "GA" to "Georgia", "HI" to "Hawaii", "ID" to "Idaho", "IL" to "Illinois", "IN" to "Indiana",
        "IA" to "Iowa", "KS" to "Kansas", "KY" to "Kentucky", "LA" to "Louisiana", "ME" to "Maine", "MD" to "Maryland", "MA" to "Massachusetts",
        "MI" to "Michigan", "MN" to "Minnesota", "MS" to "Mississippi", "MO" to "Missouri", "MT" to "Montana", "NE" to "Nebraska", "NV" to "Nevada",
        "NH" to "New Hampshire", "NJ" to "New Jersey", "NM" to "New Mexico", "NY" to "New York", "NC" to "North Carolina", "ND" to "North Dakota",
        "OH" to "Ohio", "OK" to "Oklahoma", "OR" to "Oregon", "PA" to "Pennsylvania", "RI" to "Rhode Island", "SC" to "South Carolina",
        "SD" to "South Dakota", "TN" to "Tennessee", "TX" to "Texas", "UT" to "Utah", "VT" to "Vermont", "VA" to "Virginia", "WA" to "Washington",
        "WV" to "West Virginia", "WI" to "Wisconsin", "WY" to "Wyoming",
    )

    fun stateName(abbr: String) = STATES[abbr] ?: abbr

    /** His high school, his travel team, and where he starts. */
    fun begin(h: Player): HighSchool {
        val town = h.hometown.substringBefore(",").trim().ifEmpty { "Lincoln" }
        val st = h.hometown.substringAfterLast(",", "").trim().ifEmpty { "GA" }
        val seed = (h.name.hashCode() * 31 + town.hashCode()) and 0x7fffffff
        return HighSchool(
            school = "$town ${SUFFIX[seed % SUFFIX.size]}", mascot = MASCOTS[(seed / 7) % MASCOTS.size],
            travelTeam = "Team ${stateName(st)} ${TRAVEL[(seed / 13) % TRAVEL.size]}", state = st,
            rankStart = h.recruitRank, starsStart = h.stars, ovrStart = h.ovr,
        )
    }

    fun stage(hs: HighSchool) = when (hs.step) { INTRO -> -1; in CIRCUIT -> 0; in SEASON -> 1; VISITS -> 2; else -> 3 }

    // ---------- how he plays ----------

    private fun scoring(h: Player) = listOf(h[Attr.INS], h[Attr.MID], h[Attr.OUT]).sortedDescending().take(2).average()
    private fun playmaking(h: Player) = (h[Attr.HND] + h[Attr.PAS]) / 2.0
    private fun defense(h: Player) = listOf(h[Attr.PER], h[Attr.STL], h[Attr.IDF], h[Attr.BLK]).sortedDescending().take(2).average()
    private fun skill(h: Player, k: Int) = when (k) { 0 -> scoring(h); 1 -> playmaking(h); else -> defense(h) }

    /**
     * How a weekend or a game goes with approach [k]: playing to his strengths helps, and so does being good for
     * his level (a three-star is measured against three-stars).
     */
    private fun perf(h: Player, k: Int, bonus: Double = 0.0, spread: Double = 0.75) =
        ((skill(h, k) - h.ovr) / 8.0 + (h.ovr - Road.targetOvr(h.stars)) / 8.0 + 0.15 + Rng.gauss() * spread + bonus).coerceIn(-2.5, 2.5)

    /** The rating he worked on gets a point (two on a great weekend). */
    private fun train(h: Player, k: Int, great: Boolean) {
        val attrs = when (k) { 0 -> listOf(Attr.INS, Attr.MID, Attr.OUT); 1 -> listOf(Attr.HND, Attr.PAS); else -> listOf(Attr.PER, Attr.STL, Attr.IDF, Attr.BLK) }
        val best = attrs.maxByOrNull { h[it] } ?: return
        Ratings.raise(h, best.ordinal)
        if (great) Ratings.raise(h, attrs.minByOrNull { h[it] }!!.ordinal)
    }

    /** Moves his national ranking: [f] above zero is better. His stars follow the ranking. */
    private fun move(h: Player, f0: Double): Pair<Int, Int> {
        // A bad weekend costs less than a great one gains: scouts remember the best of you.
        val f = if (f0 < 0) f0 * 0.65 else f0
        val from = h.recruitRank
        val to = (from * (1 - f)).roundToInt().coerceIn(1, 400).let { if (f > 0.02 && it == from && from > 1) from - 1 else it }
        h.recruitRank = to
        h.posRank = (to / 5.0).roundToInt().coerceAtLeast(1)
        h.stars = when { to <= 25 -> 5; to <= 100 -> 4; else -> 3 }
        return from to to
    }

    private fun gain(h: Player, hs: HighSchool, share: Double) { hs.buzz += (Road.startFollowers(h) * share).roundToInt() }

    /** The best players in his class: (name, short name, rank). From the recruiting class, or made up for the circuit. */
    private fun prospects(lg: League, h: Player): List<Triple<String, String, Int>> {
        val real = lg.recruits.map { it.p }.filter { it.id != h.id && it.recruitRank > 0 }.sortedBy { it.recruitRank }.take(3)
            .map { Triple(it.name, it.short, it.recruitRank) }
        if (real.size >= 3) return real
        val r = kotlin.random.Random(h.name.hashCode().toLong() * 131 + 7)
        val ranks = (1..6).filter { it != h.recruitRank }
        return real + List(3 - real.size) { i ->
            val f = Names.first[r.nextInt(Names.first.size)]
            val l = Names.last[r.nextInt(Names.last.size)]
            Triple("$f $l", "${f.first()}. $l", ranks[real.size + i])
        }
    }

    private fun winP(h: Player) = (0.6 + (h.ovr - 72) * 0.015).coerceIn(0.5, 0.88)
    private fun ppgBase(h: Player) = (17 + (h.ovr - 72) * 0.6 + (scoring(h) - h.ovr) * 0.25).coerceIn(10.0, 32.0)
    private fun rpgBase(h: Player) = (4 + (h[Attr.REB] - 55) / 6.0).coerceIn(2.0, 14.0)
    private fun apgBase(h: Player) = (2 + (h[Attr.PAS] - 55) / 7.0).coerceIn(1.0, 9.0)

    private fun sim(h: Player, hs: HighSchool, n: Int) {
        repeat(n) {
            if (Rng.chance(winP(h))) hs.w++ else hs.l++
            hs.gp++
            hs.pts += (ppgBase(h) + Rng.gauss() * 6).roundToInt().coerceAtLeast(4)
            hs.reb += (rpgBase(h) + Rng.gauss() * 2.5).roundToInt().coerceAtLeast(0)
            hs.ast += (apgBase(h) + Rng.gauss() * 2).roundToInt().coerceAtLeast(0)
        }
    }

    private fun game(hs: HighSchool, won: Boolean, pts: Int, reb: Int, ast: Int) {
        if (won) hs.w++ else hs.l++
        hs.gp++; hs.pts += pts; hs.reb += reb; hs.ast += ast
    }

    // ---------- the prompts ----------

    fun prompt(lg: League, h: Player, hs: HighSchool): Prompt? {
        val top = prospects(lg, h)
        return when (hs.step) {
            in CIRCUIT -> {
                val i = hs.step - 1
                val s = STOPS[i]
                val star = top.getOrNull(i)
                val starLine = star?.let { "${it.first}, the No. ${it.third} player in the class" } ?: "the best player in the class"
                Prompt("SUMMER CIRCUIT · STOP ${i + 1} OF 3 · ${hs.travelTeam.uppercase()}", "${s.name} · ${s.place}", s.blurb,
                    listOf(
                        Choice("Score in bunches", "Your scoring: ${scoring(h).roundToInt()} · big upside"),
                        Choice("Run the show and win", "Your playmaking: ${playmaking(h).roundToInt()} · coaches love winners"),
                        Choice("Guard $starLine", "Your defense: ${defense(h).roundToInt()} · everyone will be watching"),
                    ))
            }
            4 -> {
                val opp = POWERS[(hs.school.hashCode() and 0x7fffffff) % POWERS.size]
                Prompt("SENIOR SEASON · THE SHOWCASE", "On national TV vs. $opp",
                    "${hs.school} is ${hs.w}-${hs.l}. Tonight is the national TV game against $opp, the No. ${2 + (hs.school.length % 6)} team in the country, and their star is a top-20 recruit.",
                    listOf(
                        Choice("Go right at their star", "Scoring ${scoring(h).roundToInt()} · a big night or a long one"),
                        Choice("Take what the defense gives you", "the safe way to play it"),
                        Choice("Get your teammates going", "Playmaking ${playmaking(h).roundToInt()} · best odds to win"),
                    ))
            }
            5 -> Prompt("SENIOR SEASON · THE RIVALRY GAME", "The last shot",
                "${hs.school} vs. ${rivalOf(hs)}, a packed gym, and you're down 2 with six seconds left. The ball's in your hands.",
                listOf(
                    Choice("Pull up from three to win it", "3-point ${h[Attr.OUT]} · the hero shot"),
                    Choice("Drive for the tie", "Inside ${h[Attr.INS]} · then overtime"),
                    Choice("Kick it to the open man", "Passing ${h[Attr.PAS]} · your teammates will remember"),
                ))
            6 -> {
                val isFinal = hs.w.toDouble() / (hs.w + hs.l).coerceAtLeast(1) >= 0.65
                Prompt("SENIOR SEASON · THE STATE PLAYOFFS", if (isFinal) "The state championship game" else "The regional final",
                    "${hs.school} is ${hs.w}-${hs.l}" + (if (isFinal)", one win from a state title. Your last high school game, either way." else ". Win and you're in the state tournament. Lose and it's your last high school game."),
                    listOf(
                        Choice("Take over", "Scoring ${scoring(h).roundToInt()} · all or nothing"),
                        Choice("Trust your teammates", "Playmaking ${playmaking(h).roundToInt()}"),
                        Choice("Defense wins titles", "Defense ${defense(h).roundToInt()}"),
                    ))
            }
            else -> null
        }
    }

    private fun rivalOf(hs: HighSchool): String {
        val town = hs.school.substringBeforeLast(' ')
        val mine = hs.school.substringAfterLast(' ')
        return "$town ${SUFFIX.filter { it != mine }[(hs.mascot.hashCode() and 0x7fffffff) % (SUFFIX.size - 1)]}"
    }

    // ---------- answering ----------

    /** His answer to the current prompt: what happens, and the offers after it. */
    fun answer(lg: League, h: Player, hs: HighSchool, k: Int, offers: List<SchoolOffer>): List<SchoolOffer> {
        val starsFrom = h.stars
        var reach = 0
        val line: HsLine = when (hs.step) {
            in CIRCUIT -> {
                val i = hs.step - 1
                val s = STOPS[i]
                val star = prospects(lg, h).getOrNull(i)
                val p = perf(h, k)
                val pts = (11 + 4 * p + (if (k == 0) 7 else if (k == 1) 2 else 0) + (h.ovr - 74) * 0.4).roundToInt().coerceAtLeast(2)
                val ast = (2 + (if (k == 1) 5 else 1) + p * 1.2 + (h[Attr.PAS] - 60) / 12.0).roundToInt().coerceAtLeast(0)
                val reb = (3 + (h[Attr.REB] - 55) / 7.0 + p * 0.8 + (if (k == 2) 1 else 0)).roundToInt().coerceAtLeast(1)
                train(h, k, p >= 1.0)
                gain(h, hs, 0.02 + p.coerceAtLeast(0.0) * 0.04)
                if (p >= 0.9) reach = if (p >= 1.6) 3 else 2
                val (from, to) = move(h, p * 0.14)
                val oppShort = star?.second ?: "the No. 1 player"
                val text = when (k) {
                    0 -> when { p >= 1.0 -> "You dropped ${pts + 9} in the title game and the camera phones came out. $pts a game for the weekend."; p >= 0.0 -> "You got yours: $pts a game, and efficient."; else -> "The shots wouldn't fall. $pts a game on bad percentages." }
                    1 -> when { p >= 1.0 -> "You ran the show: $ast assists a game, and ${hs.travelTeam} didn't lose. Coaches love a winner."; p >= 0.0 -> "Steady: $pts points, $ast assists a game, and the right reads."; else -> "Too many turnovers, and coaches noticed." }
                    else -> {
                        val held = (14 + (-p) * 5).roundToInt().coerceIn(6, 34)
                        when { p >= 1.0 -> "You held $oppShort to $held points, and the whole gym saw it."; p >= 0.0 -> "You made $oppShort work for his $held."; else -> "$oppShort got you for $held. It'll be on every highlight account." }
                    }
                }
                HsLine("${s.name}: " + when { p >= 1.0 -> "a breakout weekend"; p >= 0.0 -> "a solid weekend"; else -> "a rough weekend" }, text, p >= 0.0,
                    pts, reb, ast, from, to, starsFrom, h.stars)
            }
            4 -> {
                val p = when (k) { 0 -> perf(h, 0, spread = 1.0); 1 -> perf(h, listOf(0, 1, 2).maxByOrNull { skill(h, it) }!!) * 0.6 + 0.3; else -> perf(h, 1) }
                val won = Rng.chance((0.42 + p * 0.12 + (if (k == 2) 0.08 else 0.0)).coerceIn(0.1, 0.9))
                val pts = (20 + p * 7 + (if (k == 0) 6 else 0) + (h.ovr - 74) * 0.5).roundToInt().coerceAtLeast(4)
                val reb = (rpgBase(h) + p).roundToInt().coerceAtLeast(1)
                val ast = (apgBase(h) + (if (k == 2) 3 else 0) + p).roundToInt().coerceAtLeast(0)
                game(hs, won, pts, reb, ast)
                gain(h, hs, 0.03 + p.coerceAtLeast(0.0) * 0.06)
                if (p >= 1.2) reach = 2
                train(h, if (k == 1) 0 else if (k == 0) 0 else 1, false)
                val (from, to) = move(h, p * 0.08)
                sim(h, hs, 8)
                HsLine(if (won) "The showcase: a statement win" else "The showcase: a loss on national TV",
                    (if (won) "${hs.school} won it. " else "${hs.school} lost it. ") + when {
                        p >= 1.0 -> "You had $pts and the announcers couldn't stop talking about you."
                        p >= 0.0 -> "You finished with $pts, $reb and $ast. A good look in front of the country."
                        else -> "$pts points on a tough night. The scouts saw it too."
                    } + " The season rolled on: ${hs.w}-${hs.l}.", won || p >= 0.5, pts, reb, ast, from, to, starsFrom, h.stars)
            }
            5 -> {
                val (won, text) = when (k) {
                    0 -> {
                        val hit = Rng.chance((0.22 + (h[Attr.OUT] - 60) / 120.0).coerceIn(0.12, 0.5))
                        if (hit) { gain(h, hs, 0.12); hs.lead += 1; true to "Pull-up three. Net. The student section stormed the floor, and the clip had a million views by morning." }
                        else false to "Off the back rim. The rival student section won't let you forget it, but you took the shot."
                    }
                    1 -> {
                        val made = Rng.chance((0.5 + (h[Attr.INS] - 60) / 150.0).coerceIn(0.3, 0.75))
                        if (made) {
                            val ot = Rng.chance(winP(h))
                            gain(h, hs, 0.05); hs.lead += 1
                            ot to (if (ot) "You got to the rim for the tie and took over in overtime." else "You tied it at the rim, but they pulled away in overtime.")
                        } else false to "You got to the rim, and it rolled off. They held on."
                    }
                    else -> {
                        val hit = Rng.chance((0.33 + (h[Attr.PAS] - 60) / 200.0).coerceIn(0.25, 0.5))
                        hs.lead += 3
                        if (hit) { gain(h, hs, 0.04); true to "Two defenders came at you; you found the open man in the corner. Bang. Your teammates carried you off the floor." }
                        else false to "You found the open man; it rimmed out. Your teammates won't forget that you trusted them."
                    }
                }
                val pts = (ppgBase(h) + Rng.gauss() * 4 + (if (won) 2 else 0)).roundToInt().coerceAtLeast(6)
                val reb = rpgBase(h).roundToInt()
                val ast = (apgBase(h) + if (k == 2) 1 else 0).roundToInt()
                game(hs, won, pts, reb, ast)
                val (from, to) = move(h, if (won) 0.04 else 0.0)
                sim(h, hs, 8)
                HsLine(if (won) "The rivalry: you won it" else "The rivalry: so close", "$text Season: ${hs.w}-${hs.l}.", won, pts, reb, ast, from, to, starsFrom, h.stars)
            }
            6 -> {
                val isFinal = hs.w.toDouble() / (hs.w + hs.l).coerceAtLeast(1) >= 0.65
                val p = when (k) { 0 -> perf(h, 0, 0.2, spread = 1.0); 1 -> perf(h, 1); else -> perf(h, 2) }
                val won = Rng.chance((0.45 + p * 0.13 + (if (k == 1) 0.06 else 0.0) - (if (isFinal) 0.05 else 0.0)).coerceIn(0.1, 0.9))
                val pts = (21 + p * 6 + (if (k == 0) 6 else 0) + (h.ovr - 74) * 0.5).roundToInt().coerceAtLeast(5)
                val reb = (rpgBase(h) + p + (if (k == 2) 2 else 0)).roundToInt().coerceAtLeast(1)
                val ast = (apgBase(h) + (if (k == 1) 3 else 0)).roundToInt()
                game(hs, won, pts, reb, ast)
                hs.stateTitle = isFinal && won
                hs.stateResult = when { isFinal && won -> "${stateName(hs.state)} state champions"; isFinal ->"Lost in the state final"; won -> "Won the regional, reached the state tournament"; else -> "Lost in the regional final" }
                if (hs.stateTitle) { hs.lead += 3; gain(h, hs, 0.06) }
                train(h, k, false)
                h.style.boosts.take(2).forEach { Ratings.raise(h, it.ordinal) }
                // The season, judged: honors and the final ranking.
                val ppg = hs.ppg
                val f = p * 0.06 + (ppg - ppgBase(h)) * 0.01 + (if (hs.stateTitle) 0.05 else 0.0)
                val (from, to) = move(h, f)
                honors(h, hs, pts)
                if (hs.honors.isNotEmpty()) reach = maxOf(reach, 1)
                HsLine(if (hs.stateTitle) "State champions" else hs.stateResult,
                    (if (won) "You had $pts in the win. " else "You had $pts, and it wasn't enough. ") +
                        "Your senior season: ${hs.w}-${hs.l}, %.1f points, %.1f rebounds, %.1f assists.".format(hs.ppg, hs.rpg, hs.apg) +
                        if (hs.honors.isNotEmpty()) " Honors: ${hs.honors.joinToString(", ")}." else "",
                    won, pts, reb, ast, from, to, starsFrom, h.stars)
            }
            else -> return offers
        }
        val (next, addedIds) = refresh(lg, h, offers, hs, reach)
        val added = addedIds.map { lg.team(it).school }
        val full = HsLine(line.title, line.text, line.good, line.pts, line.reb, line.ast, line.rankFrom, line.rankTo, line.starsFrom, line.starsTo, added)
        hs.log += full
        hs.lastResult = full
        hs.step++
        // The summer's over: the first few games of the season go by before the showcase.
        if (hs.step == SEASON.first) sim(h, hs, 6)
        return next
    }

    private fun honors(h: Player, hs: HighSchool, titlePts: Int) {
        val ppg = hs.ppg
        if (hs.stateTitle && titlePts >= 20) hs.honors += "State Tournament MVP"
        if (ppg >= 20 || h.recruitRank <= 120 || hs.stateTitle) hs.honors += "All-State"
        if ((h.recruitRank <= 60 && ppg >= 23) || h.recruitRank <= 15 || (hs.stateTitle && ppg >= 20)) hs.honors += "${stateName(hs.state)} Mr. Basketball"
        if (h.recruitRank <= 24) hs.honors += "National All-American Game"
        if (h.recruitRank <= 3 && ppg >= 24) hs.honors += "National Player of the Year"
    }

    /** The offers after a step: better NIL where it's gone up, schools newly in reach, and a few reach schools after a big showing. */
    fun refresh(lg: League, h: Player, old: List<SchoolOffer>, hs: HighSchool, reach: Int): Pair<List<SchoolOffer>, List<Int>> {
        val fresh = Road.offers(lg, h)
        val byId = fresh.associateBy { it.teamId }
        val have = old.map { it.teamId }.toSet()
        val merged = old.map { o -> byId[o.teamId]?.takeIf { it.nil > o.nil }?.let { SchoolOffer(o.teamId, it.nil, o.role, o.ahead, o.closeness) } ?: o }
        val added = fresh.filter { it.teamId !in have }.toMutableList()
        if (reach > 0) {
            val pulls = Brand.pullTable(lg)
            val taken = have + added.map { it.teamId }
            lg.teams.filter { it.id !in taken }.sortedBy { pulls[it.id] }.take(reach).forEach { t ->
                val a = Road.ahead(lg, t, h)
                added += SchoolOffer(t.id, Road.nilFor(lg, t, h), Road.roleFor(a), a, Geo.closeness(t, h))
            }
        }
        return (merged + added) to added.sortedByDescending { lg.team(it.teamId).prestige }.map { it.teamId }
    }

    // ---------- official visits ----------

    /** How well a school fits him, 0-100: playing time, home, the program, the buildings. */
    fun fit(lg: League, o: SchoolOffer): Int {
        val t = lg.team(o.teamId)
        val fac = Facilities.ALL.map { Facilities.level(t, it.id) }.average()
        return (30 + (3 - o.ahead.coerceAtMost(3)) * 12 + o.closeness * 6 + t.prestige * 0.2 + fac * 3).roundToInt().coerceIn(0, 100)
    }

    fun grade(f: Int) = when { f >= 78 -> "A"; f >= 62 -> "B"; else -> "C" }

    val ACTIVITIES = listOf(
        Choice("Tour the facilities", "see where you'd get better · NIL +5%"),
        Choice("Spend the night with the team", "teammates welcome you if you come"),
        Choice("Meet the NIL collective", "NIL +15%"),
    )

    fun visit(lg: League, h: Player, hs: HighSchool, teamId: Int, k: Int, offers: List<SchoolOffer>): List<SchoolOffer> {
        if (teamId in hs.visits || hs.visits.size >= MAX_VISITS) return offers
        val o = offers.firstOrNull { it.teamId == teamId } ?: return offers
        val t = lg.team(teamId)
        runCatching { Business.ensure(lg, t) }
        hs.visits += teamId
        gain(h, hs, 0.015)
        val bump = when (k) { 0 -> 1.05; 2 -> 1.15; else -> 1.0 }
        val nil = ((o.nil * bump).roundToInt() / 5) * 5
        val coach = runCatching { lg.coachOf(t).last }.getOrDefault("the coach")
        val text = when (k) {
            0 -> {
                val prac = Facilities.level(t, "practice")
                val arena = Facilities.level(t, "arena")
                "The ${Facilities.displayName(t, "practice").lowercase()} is level $prac of 5, and " + (if (t.arena > 0) "the arena seats ${"%,d".format(t.arena)}. " else "the arena is level $arena. ") +
                    (if (prac >= 4) "You'd get better here." else if (prac <= 2) "It's dated, and you noticed." else "Solid, not flashy.") +
                    if (arena >= 4) " The building will be loud." else ""
            }
            1 -> {
                hs.happy[teamId] = 8
                val best = lg.rosterOf(t).maxByOrNull { it.ovr }
                "You spent the night with ${best?.name ?: "the guys"} and the team. " +
                    if (o.ahead == 0) "Nobody there plays your spot like you, and they told you so." else (if (o.ahead == 1) "One of them plays your spot" else "${o.ahead} of them play your spot") + ". They didn't hide it, but they want you."
            }
            else -> "The collective walked you through it: ${nilText(nil)} a year, and Coach $coach sat in."
        }
        hs.visitNotes[teamId] = "${ACTIVITIES[k].text}: ${grade(fit(lg, o))} fit"
        val line = HsLine("Official visit: ${t.school}", text, true)
        hs.log += line
        hs.lastResult = line
        return offers.map { if (it.teamId == teamId) SchoolOffer(it.teamId, nil, it.role, it.ahead, it.closeness) else it }
    }

    private fun nilText(k: Int) = if (k >= 1000) "$%.1fM".format(k / 1000.0) else "$${k}K"

    /** The finalists to start decision day with: his best visits, else the best programs offering. */
    fun suggestFinalists(lg: League, hs: HighSchool, offers: List<SchoolOffer>): List<Int> {
        val visited = offers.filter { it.teamId in hs.visits }.sortedByDescending { fit(lg, it) }.map { it.teamId }
        val rest = offers.filter { it.teamId !in hs.visits }.sortedByDescending { lg.team(it.teamId).prestige }.map { it.teamId }
        return (visited + rest).take(3)
    }

    val SETTINGS = listOf("Live on TV", "In your gym", "On social media")
    fun settingText(i: Int) = when (i) {
        0 -> "Three hats on a table and a national TV audience. The biggest boost to your following."
        1 -> "Your family, your coach and your teammates in the bleachers. Leadership carries into college."
        else -> "One graphic, posted at 7 p.m. sharp. The internet does the rest."
    }

    // ---------- into the career ----------

    /** He's signed: the senior year goes into the career, with its headlines. */
    fun apply(lg: League, h: Player, hs: HighSchool) {
        val r = lg.road ?: return
        val t = lg.teams.getOrNull(h.teamId) ?: return
        hs.committedTo = t.id
        hs.rankEnd = h.recruitRank
        hs.lastResult = null
        r.hs = hs
        val boost = when (hs.setting) { 0 -> 0.12; 1 -> 0.05; else -> 0.08 }
        r.followers += hs.buzz + (r.followers * boost).roundToInt()
        r.seasonStartFollowers = r.followers
        r.leadership = (r.leadership + hs.lead + if (hs.setting == 1) 3 else 0).coerceIn(0, 100)
        if (hs.setting == 0) r.persona += 1
        hs.happy[t.id]?.let { d ->
            h.happiness = (h.happiness + d).coerceAtMost(99)
            for (p in lg.rosterOf(t)) if (p.id != h.id) p.happiness = (p.happiness + d / 2).coerceAtMost(99)
        }
        if (t.id in hs.visits) h.happiness = (h.happiness + 4).coerceAtMost(99)
        h.ovrHistory.clear()
        h.ovrHistory += h.ovr
        lg.news.removeAll { it.text == "${h.name} signs with ${t.school}" }
        val others = hs.finalists.filter { it != t.id }.map { lg.team(it).school }
        val how = when (hs.setting) { 0 -> "live on national TV"; 1 -> "in front of his teammates in the ${hs.school} gym"; else -> "with a post on social media" }
        lg.news += News(lg.season, 0, "${h.name} commits to ${t.school}", true, kind = "commit", teamId = t.id, source = "Hoops Wire Recruiting",
            body = "The No. ${h.recruitRank} player in the class picked ${t.school}" + (if (others.isNotEmpty()) " over ${others.joinToString(" and ")}" else "") +
                ", $how. At ${hs.school} he averaged %.1f points, %.1f rebounds and %.1f assists".format(hs.ppg, hs.rpg, hs.apg) +
                (when { hs.stateTitle -> ", and won the ${stateName(hs.state)} state title."; hs.stateResult.isNotEmpty() -> "; ${hs.stateResult.replaceFirstChar { it.lowercase() }}."; else -> "." }) +
                (if (h.recruitRank < hs.rankStart) " He was No. ${hs.rankStart} a year ago." else ""))
        if (hs.honors.isNotEmpty()) lg.news += News(lg.season, 0, "${h.name}'s senior year: ${hs.honors.joinToString(", ")}", true, kind = "commit", teamId = t.id,
            source = "Hoops Wire Recruiting", body = "${hs.school} went ${hs.w}-${hs.l}. ${hs.stateResult}.")
    }
}
