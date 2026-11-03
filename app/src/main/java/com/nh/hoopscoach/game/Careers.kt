package com.nh.hoopscoach.game

import kotlin.math.exp
import kotlin.math.ln

/**
 * Player careers: single-game career highs, honors by season, and former players kept on record
 * so their career pages outlive their time in school.
 */
object Careers {
    val HIGHS = listOf("Points", "Rebounds", "Assists", "Threes", "Steals", "Blocks")
    /** Former players kept on record, oldest dropped first (the user's own are kept longest). */
    private const val KEEP = 1500

    private data class CareerFit(val player: Player, val totals: EraData.CareerTotals)
    private val todayFingerprint: Map<String, List<Double>> by lazy {
        Careers::class.java.getResourceAsStream("/eras/today_fingerprint.tsv")?.bufferedReader()?.use { reader ->
            reader.readLines().filter { it.isNotBlank() && !it.startsWith("#") }.associate { line ->
                val fields = line.split("|")
                fields[0] to fields.drop(1).map { it.toDouble() }
            }
        } ?: emptyMap()
    }

    /** Attach a ratings-relative profile to today's pros using their NBA career totals. */
    fun fingerprint(lg: League) {
        val era = Eras.of(lg)
        if (!lg.pro || era != Era.TODAY) return
        val candidates = lg.players.mapNotNull { p ->
            EraData.career("${p.first} ${p.last}")?.takeIf { it.mp > 0 && it.fga > 0 }?.let { CareerFit(p, it) }
        }
        if (candidates.isEmpty()) return
        val leagueFt = candidates.sumOf { it.totals.ft.toDouble() } / candidates.sumOf { it.totals.fta.toDouble() }

        fun normalize(real: (CareerFit) -> Double, engine: (CareerFit) -> Double): Map<Player, Double> {
            val numerator = candidates.sumOf { it.totals.mp * real(it) }
            val denominator = candidates.sumOf { it.totals.mp * engine(it) }
            if (numerator <= 0.0 || denominator <= 0.0) return candidates.associate { it.player to 1.0 }
            val scale = numerator / denominator
            return candidates.associate { fit ->
                val raw = real(fit) / (scale * engine(fit))
                val safeRaw = raw.takeIf { it.isFinite() && it > 0.0 } ?: 1e-9
                val shrunk = exp(ln(safeRaw) * fit.totals.mp / (fit.totals.mp + 1500.0))
                fit.player to shrunk.coerceIn(0.25, 4.0)
            }
        }

        val shots = normalize(
            { (it.totals.fga + 0.44 * it.totals.fta) / it.totals.mp },
            { PlayerWeights.shotW(it.player) },
        )
        val orb = normalize({ it.totals.orb.toDouble() / it.totals.mp }, { PlayerWeights.rebW(it.player, true) })
        val drb = normalize({ it.totals.drb.toDouble() / it.totals.mp }, { PlayerWeights.rebW(it.player, true) })
        val ast = normalize({ it.totals.ast.toDouble() / it.totals.mp }, { PlayerWeights.astW(it.player) })
        val stl = normalize({ it.totals.stl.toDouble() / it.totals.mp }, { PlayerWeights.stlW(it.player) })
        val blk = normalize({ it.totals.blk.toDouble() / it.totals.mp }, { PlayerWeights.blkW(it.player) })
        val tov = normalize({ it.totals.tov.toDouble() / it.totals.mp }, { PlayerWeights.tovW(it.player) })
        val foul = normalize({ it.totals.pf.toDouble() / it.totals.mp }, { PlayerWeights.foulW(it.player) })
        val three = normalize(
            { fit ->
                val r = (fit.totals.fg3a.toDouble() / fit.totals.fga).coerceIn(0.01, 0.95)
                r / (1.0 - r)
            },
            { fit ->
                val (wi, wm, w3) = PlayerWeights.shotMix(fit.player, true, era)
                w3 / (wi + wm)
            },
        )
        val draw = normalize(
            { it.totals.fta.toDouble() / it.totals.fga },
            { fit ->
                val (wi, wm, w3) = PlayerWeights.shotMix(fit.player, true, era)
                val adjustedW3 = w3 * (three[fit.player] ?: 1.0)
                (wi * 0.2 + wm * 0.06 + adjustedW3 * 0.022) / (wi + wm + adjustedW3)
            },
        )

        for (fit in candidates) {
            val p = fit.player
            val baseFt = PlayerWeights.ftBase(p, true)
            // Shrink low-volume marks toward the matched players' career FT%.
            val ftReal = (fit.totals.ft + leagueFt * 150.0) / (fit.totals.fta + 150.0)
            val calibrated = todayFingerprint["${p.first} ${p.last}"]
            p.fingerprint = Fingerprint(
                shots = calibrated?.getOrNull(0) ?: shots.getValue(p),
                three = calibrated?.getOrNull(1) ?: three.getValue(p),
                draw = calibrated?.getOrNull(2) ?: draw.getValue(p),
                ftAdj = calibrated?.getOrNull(3) ?: ftReal - baseFt,
                orb = calibrated?.getOrNull(4) ?: orb.getValue(p),
                drb = calibrated?.getOrNull(5) ?: drb.getValue(p),
                ast = calibrated?.getOrNull(6) ?: ast.getValue(p),
                stl = calibrated?.getOrNull(7) ?: stl.getValue(p),
                blk = calibrated?.getOrNull(8) ?: blk.getValue(p),
                tov = calibrated?.getOrNull(9) ?: tov.getValue(p),
                foul = calibrated?.getOrNull(10) ?: foul.getValue(p),
                age = fit.totals.age,
                mpg = if (fit.totals.rG > 0.0) fit.totals.rMp / fit.totals.rG else 0.0,
                season = lg.season,
                v = 2,
            )
        }
    }

    /** After each game: new career highs and the game they came in. */
    fun track(lg: League, p: Player, st: StatLine, g: Game, teamId: Int) {
        if (p.highs.size < HIGHS.size) p.highs = p.highs.copyOf(HIGHS.size)
        while (p.highGames.size < HIGHS.size) p.highGames += ""
        val vals = intArrayOf(st.pts, st.reb, st.ast, st.tpm, st.stl, st.blk)
        for (i in vals.indices) {
            if (vals[i] <= p.highs[i]) continue
            p.highs[i] = vals[i]
            val site = g.site(teamId).let { if (it == "N") "vs" else it }
            p.highGames[i] = "$site ${lg.team(g.opp(teamId)).abbr} · ${lg.season}"
        }
    }

    /**
     * A player leaving the league. His record stays if he played for the user, was drafted,
     * made an All-America team or entered a program Hall of Fame.
     */
    fun keep(lg: League, p: Player, t: Team, pick: Int, how: String? = null) {
        val star = p.awards.any { a -> listOf("AA 1st", "AA 2nd", "AA 3rd", "Nat MVP", "MOP").any { a.endsWith(it) } }
        val legend = t.legends.any { it.pid == p.id }
        if (t.id != lg.userTeamId && p.id != lg.heroId && pick <= 0 && !star && !legend && p.transferFrom != lg.userTeamId) return
        p.lastTeam = t.id
        p.teamId = -1
        p.left = how ?: if (pick > 0) "Drafted No. $pick overall in ${lg.season}" else "Graduated from ${t.school} in ${lg.season}"
        p.log = mutableListOf()
        p.stats = StatLine()
        p.tourney = StatLine()
        if (lg.alumni.none { it.id == p.id }) lg.alumni += p
        if (lg.alumni.size > KEEP) {
            val drop = lg.alumni.firstOrNull { it.lastTeam != lg.userTeamId } ?: lg.alumni.first()
            lg.alumni.remove(drop)
        }
    }

    fun formerPlayers(lg: League, teamId: Int): List<Player> =
        lg.alumni.filter { it.lastTeam == teamId || it.career.any { l -> l.teamId == teamId } }.sortedByDescending { it.career.lastOrNull()?.season ?: 0 }

    /** Readable honor for an award code, or null if it isn't one we show. */
    fun honor(code: String, pos: Pos): String? = when {
        code == "Nat MVP" -> "National Player of the Year"
        code == "AA 1st" -> "First Team All-American"
        code == "AA 2nd" -> "Second Team All-American"
        code == "AA 3rd" -> "Third Team All-American"
        code == "DPOY" -> "National Defensive Player of the Year"
        code == "FOY" -> "National Freshman of the Year"
        code == "MOP" -> "Final Four Most Outstanding Player"
        code == "All-Tourn" -> "NCAA All-Tournament Team"
        code == "Pos OY" -> "Best $pos in the country"
        code == "Conf POY" -> "Conference Player of the Year"
        code == "All-Conf 1st" -> "All-Conference First Team"
        code == "Conf DPOY" -> "Conference Defensive Player of the Year"
        code == "Conf FOY" -> "Conference Freshman of the Year"
        code.startsWith("Draft #") -> "Drafted No. ${code.removePrefix("Draft #")} overall"
        else -> null
    }

    /** How much an honor matters, for ordering: national 3, NCAA/draft 2, conference 1. */
    fun rank(code: String) = when (code) {
        "Nat MVP", "AA 1st", "MOP" -> 5; "AA 2nd", "AA 3rd", "DPOY", "FOY", "Pos OY" -> 4
        "All-Tourn" -> 3; "Conf POY" -> 2; else -> if (code.startsWith("Draft #")) 3 else 1
    }

    class SeasonHonors(val season: Int, val honors: List<Pair<String, Int>>)

    /** Every season of a career with its honors, newest first. Rank is 1 (conference) to 5 (national). */
    fun honorsBySeason(p: Player, currentSeason: Int): List<SeasonHonors> {
        val bySeason = HashMap<Int, MutableList<Pair<String, Int>>>()
        for (a in p.awards) {
            val season = a.substringBefore(' ').toIntOrNull() ?: continue
            val code = a.substringAfter(' ')
            val text = honor(code, p.pos) ?: continue
            bySeason.getOrPut(season) { mutableListOf() } += text to rank(code)
        }
        val seasons = (p.career.map { it.season } + bySeason.keys + if (p.teamId >= 0 && p.stats.gp > 0) listOf(currentSeason) else emptyList()).toSortedSet().toList().asReversed()
        return seasons.map { s -> SeasonHonors(s, bySeason[s].orEmpty().distinct().sortedByDescending { it.second }) }
    }

    /** Career line including the season in progress. */
    fun total(p: Player, ncaa: Boolean = false): StatLine {
        val t = StatLine()
        for (l in p.career) (if (ncaa) l.ncaa else l.s)?.let { t.add(it) }
        val current = if (ncaa) p.tourney else p.stats
        if (current.gp > 0 && p.teamId >= 0) t.add(current)
        return t
    }
}
