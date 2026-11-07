package com.nh.hoopscoach.game

import kotlin.math.roundToInt

/**
 * Real data for Era mode, from the user's NBA eras pack (resources/eras; personal use only):
 * each past era's stars (the top 120 by Win Shares that season, plus a few who missed most of it)
 * with ratings built from their NBA 2K cards, their 2K attributes and headshots; the first 60 picks
 * of every draft from 1986 to 2026; and drafted players' college seasons.
 * Built by tools/build_eras.py; ratings are on the game's pro scale.
 */
object EraData {
    class Star(val era: Era, val id: String, val name: String, val team: String, val pos: Pos, val age: Int, val ovr: Int, val attrs: IntArray?, val photo: String,
        val ppg: Double = 0.0, val rpg: Double = 0.0, val apg: Double = 0.0)
    class Pick(val year: Int, val pick: Int, val id: String, val name: String, val college: String, val pos: Pos?, val age: Int, val peak: Int, val photo: String)
    class College(val season: Int, val school: String, val conf: String, val cls: Int, val g: Int, val mpg: Double, val ppg: Double, val rpg: Double, val apg: Double,
        val id: String = "", val ours: String = "")
    /** A franchise in a season: its place in today's league ([slot]) and what it was called then. */
    class Franchise(val slot: String, val abbr: String, val city: String, val name: String)

    private fun table(name: String): List<List<String>> =
        (EraData::class.java.getResourceAsStream("/eras/$name")?.bufferedReader(Charsets.UTF_8)?.use { it.readLines() } ?: emptyList())
            .filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split("|") }

    private fun pos(s: String) = Pos.entries.firstOrNull { it.name == s }

    private val allStars: List<Star> by lazy {
        table("stars.tsv").mapNotNull { f ->
            val era = Era.entries.firstOrNull { it.name == f[0] } ?: return@mapNotNull null
            val attrs = f[7].takeIf { it.isNotEmpty() }?.split(",")?.map { it.toInt() }?.toIntArray()?.takeIf { it.size == 10 }
            Star(era, f[1], f[2], f[3], pos(f[4]) ?: Pos.SF, f[5].toIntOrNull() ?: 27, f[6].toInt(), attrs, f[8],
                f.getOrNull(10)?.toDoubleOrNull() ?: 0.0, f.getOrNull(11)?.toDoubleOrNull() ?: 0.0, f.getOrNull(12)?.toDoubleOrNull() ?: 0.0)
        }
    }

    private val drafts: Map<Int, List<Pick>> by lazy {
        table("drafts.tsv").map { f -> Pick(f[0].toInt(), f[1].toInt(), f[2], f[3], f[4], pos(f[5]), f[6].toInt(), f[7].toInt(), f[8]) }.groupBy { it.year }
    }

    private val colleges: Map<String, List<College>> by lazy {
        table("college.tsv").map { f ->
            f[0] to College(f[1].toInt() + 1, f[2], f[3], listOf("FR", "SO", "JR", "SR").indexOf(f[4]) + 1, f[5].toIntOrNull() ?: 0,
                f[6].toDoubleOrNull() ?: 0.0, f[7].toDoubleOrNull() ?: 0.0, f[8].toDoubleOrNull() ?: 0.0, f[9].toDoubleOrNull() ?: 0.0, f[0], f.getOrElse(10) { "" })
        }.groupBy({ it.first }, { it.second })
    }

    private val franchises: Map<Int, List<Franchise>> by lazy {
        table("teams.tsv").map { f -> f[0].toInt() to Franchise(f[1], f[2], f[3], f[4]) }.groupBy({ it.first }, { it.second })
    }

    private val picksById: Map<String, Pick> by lazy { drafts.values.flatten().associateBy { it.id } }

    /** Today's players from NBA 2K27: overall, potential and the game's ten ratings, all on the 2K scale. */
    class Card(val ovr: Int, val pot: Int, val attrs: IntArray?)
    private val today: Map<String, Card> by lazy {
        table("today2k.tsv").associate { f ->
            f[0] to Card(f[1].toInt(), f[2].toInt(), f[3].takeIf { it.isNotEmpty() }?.split(",")?.map { it.toInt() }?.toIntArray())
        }
    }

    /** Every NBA 2K27 overall in today's league, best first. */
    fun todayOveralls(): List<Int> = today.values.map { it.ovr }.sortedDescending()

    /** [name]'s NBA 2K27 card, if the ratings file has him. */
    fun today(name: String): Card? = today[name]

    /** The teams of [season] (the year it ends), 1986 to 2027, or null outside the data. */
    fun teams(season: Int): List<Franchise>? = franchises[season]

    /** Real players at the game's schools in [season]: who played where, in what class. */
    fun collegeSeason(season: Int): List<College> = colleges.values.flatten().filter { it.season == season && it.ours.isNotEmpty() }

    /** Where a drafted player went: his year and pick. */
    fun pickOf(id: String): Pick? = picksById[id]

    fun stars(era: Era): List<Star> = allStars.filter { it.era == era }

    /** The real draft of [year] (first 60 picks), or empty when there's no data for it. */
    fun draft(year: Int): List<Pick> = drafts[year] ?: emptyList()

    fun college(id: String): List<College> = colleges[id] ?: emptyList()

    /**
     * Gives [p] a real player's shape: his 2K attributes scaled so his overall lands on [target]
     * (a great shooter stays a great shooter), then his second position from them.
     */
    fun shape(p: Player, attrs: IntArray, target: Int) {
        // Each rating keeps its distance below 99, scaled: a great player's weak spots shrink but stay his weakest.
        fun scaled(f: Double) = IntArray(10) { (99 - (99 - attrs[it]) * f).roundToInt().coerceIn(25, 99) }
        var best = 1.0
        var gap = Int.MAX_VALUE
        var f = 0.05
        while (f <= 3.0) {
            val d = kotlin.math.abs(Ratings.ovr(p.pos, scaled(f)) - target)
            if (d < gap) { gap = d; best = f }
            f += 0.01
        }
        p.attrs = scaled(best)
        p.pos2 = Ratings.secondFor(p.pos, p.attrs)
    }

    /**
     * Strengths from a real season's numbers, for players with no 2K attributes in the data: scorers
     * get scoring ratings (inside for bigs, outside for guards), passers handling and passing,
     * rebounders boards and rim protection. [shape] then scales them to his overall.
     */
    fun profile(pos: Pos, ppg: Double, rpg: Double, apg: Double): IntArray {
        val big = pos == Pos.PF || pos == Pos.C
        val guard = pos == Pos.PG || pos == Pos.SG
        val score = 45 + ppg * 1.7
        val pass = 45 + apg * 5.0
        val reb = 35 + rpg * 5.0
        val a = doubleArrayOf(
            if (big) score + 6 else if (guard) score - 6 else score,
            score,
            if (big) score - 22 else if (guard) score + 2 else score - 4,
            if (guard) maxOf(pass, score - 5) else (pass + score) / 2 - 8,
            pass,
            reb,
            if (big) 55.0 else 68.0,
            if (big) 50 + rpg * 3.5 else 50.0,
            if (guard) 65.0 else 55.0,
            if (big) 45 + rpg * 3.5 else 40.0,
        )
        return IntArray(10) { a[it].roundToInt().coerceIn(25, 99) }
    }

    /** The play style that fits a player's strengths best (the one whose three ratings are highest). */
    fun styleFor(p: Player): Arch = Ratings.styles(p.pos).maxBy { s -> s.boosts.sumOf { p.attrs[it.ordinal] } }

    /** A real draftee: his name, college, ceiling, photo and college seasons. */
    fun apply(p: Player, pick: Pick) {
        val parts = pick.name.split(" ", limit = 2)
        p.first = parts[0]
        p.last = parts.getOrElse(1) { "" }
        p.country = null
        if (pick.photo.isNotEmpty()) p.photo = pick.photo
        if (pick.college.isNotEmpty()) p.highSchool = pick.college
        // Players who became stars get the ceiling they reached; the rest keep a raw rookie's.
        if (pick.peak > 0) p.pot = maxOf(p.ovr, pick.peak + Rng.int(-1, 1)).coerceAtMost(99)
        for (c in college(pick.id)) {
            val s = StatLine(gp = c.g, sec = (c.mpg * 60 * c.g).roundToInt(), pts = (c.ppg * c.g).roundToInt(),
                drb = (c.rpg * c.g).roundToInt(), ast = (c.apg * c.g).roundToInt())
            p.career += SeasonLine(c.season, c.school, c.conf, c.cls.coerceIn(1, 4), p.ovr, s)
        }
    }
}
