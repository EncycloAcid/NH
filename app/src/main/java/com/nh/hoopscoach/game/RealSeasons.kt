package com.nh.hoopscoach.game

import kotlin.math.roundToInt

/**
 * Every real NBA season from 1986-87 to 2026-27 (resources/eras/seasons.tsv, from basketball-reference's
 * per-game pages; personal use): who played where and how. Used to start a pro league from the real
 * rosters of any season, to rate players without a 2K card from their numbers, and to give real players
 * their real career lines.
 */
object RealSeasons {
    /** One player's season: his first team (opening day) and his numbers with every team combined. */
    class Line(
        val season: Int, val id: String, val name: String, val age: Int, val pos: String,
        /** Each team he played for that season, in order (basketball-reference abbreviations). */
        val teams: List<String>,
        val g: Int, val gs: Int, val mpg: Double, val ppg: Double, val rpg: Double, val apg: Double,
        val spg: Double, val bpg: Double, val tpa: Double, val tpp: Double, val fgp: Double, val ftp: Double,
    ) {
        val first get() = teams.first()
        val last get() = teams.last()
    }

    private val seasons: Map<Int, List<Line>> by lazy { load() }

    private fun load(): Map<Int, List<Line>> {
        val text = RealSeasons::class.java.getResourceAsStream("/eras/seasons.tsv")?.bufferedReader(Charsets.UTF_8)?.use { it.readLines() } ?: return emptyMap()
        fun d(s: String) = s.toDoubleOrNull() ?: 0.0
        // Stints in the order the site lists them; combine each player's season, games-weighted.
        class Acc(val season: Int, val id: String, val name: String, val age: Int, val pos: String) {
            val teams = mutableListOf<String>()
            var g = 0; var gs = 0; var min = 0.0; var pts = 0.0; var reb = 0.0; var ast = 0.0; var stl = 0.0; var blk = 0.0
            var tpa = 0.0; var tpm = 0.0; var fgw = 0.0; var ftw = 0.0
        }
        val acc = LinkedHashMap<String, Acc>()
        for (l in text) {
            if (l.isBlank() || l.startsWith("#")) continue
            val f = l.split("|")
            if (f.size < 18) continue
            val season = f[0].toIntOrNull() ?: continue
            val a = acc.getOrPut("$season|${f[1]}") { Acc(season, f[1], f[2], f[3].toIntOrNull() ?: 27, f[5]) }
            val g = f[6].toIntOrNull() ?: 0
            a.teams += f[4]
            a.g += g; a.gs += f[7].toIntOrNull() ?: 0
            a.min += g * d(f[8]); a.pts += g * d(f[9]); a.reb += g * d(f[10]); a.ast += g * d(f[11])
            a.stl += g * d(f[12]); a.blk += g * d(f[13]); a.tpa += g * d(f[14]); a.tpm += g * d(f[14]) * d(f[15])
            a.fgw += g * d(f[16]); a.ftw += g * d(f[17])
        }
        return acc.values.map { a ->
            val g = a.g.coerceAtLeast(1).toDouble()
            Line(a.season, a.id, a.name, a.age, a.pos, a.teams.distinct(), a.g, a.gs, a.min / g, a.pts / g, a.reb / g, a.ast / g,
                a.stl / g, a.blk / g, a.tpa / g, if (a.tpa > 0) a.tpm / a.tpa else 0.0, a.fgw / g, a.ftw / g)
        }.groupBy { it.season }
    }

    fun has(season: Int) = seasons[season]?.isNotEmpty() == true

    fun season(season: Int): List<Line> = seasons[season] ?: emptyList()

    /** A player's seasons before [season], oldest first. */
    fun careerBefore(id: String, season: Int): List<Line> = seasons.filterKeys { it < season }.values.mapNotNull { list -> list.firstOrNull { it.id == id } }.sortedBy { it.season }

    private val byName: Map<String, List<Line>> by lazy { seasons.values.flatten().groupBy { it.name } }

    /** Today's real players have no site id in the roster data: their seasons by name, before [season]. */
    fun careerByName(name: String, season: Int): List<Line> = byName[name].orEmpty().filter { it.season < season }.sortedBy { it.season }

    /** basketball-reference's abbreviation, then and now, to the franchise's place in today's league. */
    fun slotOf(abbr: String): String = when (abbr) {
        "NJN", "BRK" -> "BKN"
        // The first Hornets (1988-2002) play in Charlotte's place in the era team lists, and move to New Orleans's in 2003.
        "CHH" -> "CHA"
        "CHO" -> "CHA"
        "NOH", "NOK" -> "NOP"
        "SEA" -> "OKC"
        "VAN" -> "MEM"
        "WSB" -> "WAS"
        "PHO" -> "PHX"
        "KCK" -> "SAC"
        "SDC" -> "LAC"
        else -> abbr
    }

    /** Scoring and rebounding scale with the pace of the time; this puts a season's numbers on 2025-26's. */
    private val paceFactor: Map<Int, Double> by lazy {
        fun teamPpg(s: Int): Double {
            val l = season(s)
            val games = l.maxOfOrNull { it.g }?.coerceAtLeast(1) ?: 82
            val teams = l.flatMap { it.teams }.map { slotOf(it) }.distinct().size.coerceAtLeast(1)
            return l.sumOf { it.ppg * it.g } / (teams * games)
        }
        val ref = teamPpg(2026).takeIf { it > 0 } ?: 115.0
        seasons.keys.associateWith { s -> (ref / teamPpg(s).coerceAtLeast(1.0)).coerceIn(0.8, 1.35) }
    }

    /** A season's numbers as one score: fit to NBA 2K27's ratings of the 2025-26 players (about two points off on average). */
    private fun raw(l: Line): Double {
        val g = l.g.coerceAtLeast(1).toDouble()
        val games = (season(l.season).maxOfOrNull { it.g } ?: 82).coerceAtLeast(1)
        var v = 66.71 - 0.223 * l.mpg + 0.743 * l.ppg + 0.386 * l.rpg + 0.536 * l.apg + 0.973 * l.spg + 1.273 * l.bpg +
            1.704 * (l.gs / g) + 3.738 * (l.g.toDouble() / games).coerceAtMost(1.0)
        // A handful of games says little: pull short seasons toward a bench player.
        if (l.g < 10) v = 68 + (v - 68) * (l.g / 10.0)
        return v
    }

    private val ratings = HashMap<Int, Map<String, Int>>()

    /**
     * A 2K-style overall from a season's numbers. Players are ranked within their own season, then given
     * the rating at the same place in today's league (NBA 2K27), scaled for the number of teams: every
     * season has about as many stars as today's, whatever its pace.
     */
    fun rating2k(l: Line): Int = synchronized(ratings) {
        ratings.getOrPut(l.season) {
            val lines = season(l.season)
            val teams = lines.flatMap { it.teams }.map { slotOf(it) }.distinct().size.coerceAtLeast(1)
            val ref = EraData.todayOveralls()
            lines.sortedByDescending { raw(it) }.withIndex().associate { (i, x) ->
                x.id to if (ref.isEmpty()) raw(x).roundToInt() else ref[(i * 30.0 / teams).toInt().coerceAtMost(ref.size - 1)]
            }
        }[l.id] ?: 68
    }.coerceIn(60, 99)

    fun pos(l: Line): Pos {
        val p = l.pos.substringBefore('-').trim()
        return when (p) {
            "PG" -> Pos.PG; "SG" -> Pos.SG; "SF" -> Pos.SF; "PF" -> Pos.PF; "C" -> Pos.C
            "G" -> if (l.apg >= 4) Pos.PG else Pos.SG
            "F" -> if (l.rpg >= 7) Pos.PF else Pos.SF
            else -> Pos.SF
        }
    }

    /** Strengths from a season's numbers: scorers, shooters, passers, rebounders and defenders. */
    fun profile(l: Line): IntArray {
        val f = paceFactor[l.season] ?: 1.0
        val m = 36.0 / l.mpg.coerceAtLeast(8.0)
        val pts = l.ppg * f * m; val reb = l.rpg * f * m; val ast = l.apg * f * m; val stl = l.spg * f * m; val blk = l.bpg * f * m
        val threes = l.tpa * m
        val pos = pos(l)
        val big = pos == Pos.PF || pos == Pos.C
        val shooter = 40 + threes * 5.5 + (l.tpp - 0.3).coerceAtLeast(0.0) * 120
        val a = doubleArrayOf(
            45 + pts * 1.3 + (if (big) 8 else 0) + (l.fgp - 0.45) * 60,
            45 + pts * 1.2 + (l.ftp - 0.7) * 40,
            shooter.coerceAtMost(99.0),
            if (big) 40 + ast * 3.0 else 45 + ast * 4.0,
            40 + ast * 5.0,
            30 + reb * 4.0,
            (if (big) 45 else 55) + stl * 12.0,
            (if (big) 55 else 40) + blk * 9.0 + reb * 1.0,
            40 + stl * 20.0,
            35 + blk * 22.0,
        )
        return IntArray(10) { a[it].roundToInt().coerceIn(25, 99) }
    }

    /** A real season as a career line (season numbers rebuilt so the points add up). */
    fun statLine(l: Line): StatLine {
        val g = l.g
        val pts = (l.ppg * g).roundToInt()
        val tpa = (l.tpa * g).roundToInt()
        var tpm = (tpa * l.tpp).roundToInt().coerceAtMost(pts / 3)
        var ftm = (pts * 0.18).roundToInt()
        var fgm = ((pts - tpm - ftm) / 2).coerceAtLeast(tpm)
        ftm = (pts - tpm - 2 * fgm).coerceAtLeast(0)
        if (2 * fgm + tpm + ftm != pts) { fgm = 0; tpm = 0; ftm = pts }
        val fga = if (l.fgp > 0) (fgm / l.fgp).roundToInt().coerceAtLeast(fgm) else fgm
        val fta = if (l.ftp > 0) (ftm / l.ftp).roundToInt().coerceAtLeast(ftm) else ftm
        val reb = (l.rpg * g).roundToInt()
        val s = StatLine()
        s.gp = g; s.gs = l.gs; s.sec = (l.mpg * 60 * g).roundToInt()
        s.pts = pts; s.fgm = fgm; s.fga = fga; s.tpm = tpm; s.tpa = tpa.coerceAtLeast(tpm); s.ftm = ftm; s.fta = fta
        s.orb = (reb * 0.25).roundToInt(); s.drb = reb - s.orb
        s.ast = (l.apg * g).roundToInt(); s.stl = (l.spg * g).roundToInt(); s.blk = (l.bpg * g).roundToInt()
        return s
    }

    /** Gives [p] his real seasons before [season] as career lines. */
    fun addCareer(p: Player, lines: List<Line>) {
        if (lines.isEmpty() || p.career.any { it.cls == PRO_YEAR }) return
        for (l in lines) {
            if (l.g <= 0) continue
            p.career += SeasonLine(l.season, l.last, "", PRO_YEAR, p.ovr, statLine(l))
        }
    }
}
