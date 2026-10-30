package com.nh.hoopscoach.game

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The numbers around Prestige.
 *
 * - Prestige: the durable 0-100 brand. Moves once a season.
 * - Legacy: the long-run baseline. Very slow; cushions a fallen power and slows a one-year spike.
 * - Buzz: short-term heat from big wins and March runs. Fades fast.
 * - Conference exposure: how visible a league is nationally.
 * - Program Pull: everything blended into one attractiveness number. Recruits see it as Recruiting Pull.
 * - Resume ceiling: above 70, the last 10 NCAA Tournaments decide how high Prestige can stick.
 */
object Brand {
    const val RESUME_YEARS = 10

    /** Programs whose history is bigger than their current standing (titles, Final Fours, eras). */
    private val pastGlory = mapOf(
        "UCLA" to 12, "IND" to 14, "GTWN" to 16, "CIN" to 12, "NCST" to 10, "OKST" to 12, "SF" to 25, "UNLV" to 14,
        "HC" to 20, "LAS" to 20, "LUC" to 12, "UTEP" to 16, "DEP" to 18, "SJU" to 12, "STAN" to 10, "UTAH" to 10,
        "NOVA" to 6, "LOU" to 6, "ARIZ" to 6, "MICH" to 6, "MARQ" to 6, "MD" to 6, "ARK" to 6, "OSU" to 6, "ORE" to 6,
        "KSU" to 6, "OU" to 6, "WICH" to 6, "MEM" to 8, "LSU" to 8, "MINN" to 6, "CONN" to 5, "ILL" to 5, "MSU" to 4,
        "SYR" to 4, "FLA" to 4, "WIS" to 4, "HOU" to 4, "IOWA" to 4, "UK" to 3, "UNC" to 3, "KU" to 2, "DUKE" to 1,
        "PUR" to 2, "TEX" to 2, "MIZ" to 2, "BRAD" to 10, "SBU" to 8, "DAY" to 4, "WKU" to 8, "MUR" to 4, "NMSU" to 6,
        "SLU" to 4, "DUQ" to 10, "TEM" to 8, "UMASS" to 6, "MASS" to 6, "PROV" to 4, "HALL" to 4, "CREI" to 2,
        "VCU" to 3, "GMU" to 6, "BUT" to 4, "XAV" to 3, "GONZ" to 0, "SDSU" to 2, "FAU" to 4, "LIB" to 0,
    )

    fun ensure(lg: League) {
        for (t in lg.teams) {
            if (t.legacy < 0) {
                // Older saves: blend in the program's own history.
                val past = t.history.map { it.prestige }
                val base = if (past.isEmpty()) t.prestige.toDouble() else (past.average() * 0.5 + t.prestige * 0.5)
                t.legacy = (base + (pastGlory[t.abbr] ?: 0)).coerceIn(5.0, 99.0)
            }
            if (t.buzz < 0) t.buzz = startingBuzz(t)
        }
    }

    fun startingBuzz(t: Team) = ((t.prestige - 45) * 0.35).coerceIn(0.0, 20.0)

    // ---------- conference exposure ----------

    fun exposure(lg: League, confId: Int): Int {
        val c = lg.conf(confId)
        val base = when (c.tier) { 0 -> 88.0; 1 -> 62.0; else -> 34.0 }
        val avg = c.teamIds.sumOf { lg.team(it).prestige }.toDouble() / c.teamIds.size.coerceAtLeast(1)
        return (base * 0.65 + avg * 0.35).roundToInt().coerceIn(5, 99)
    }

    fun exposureLabel(e: Int) = when {
        e >= 75 -> "National stage"
        e >= 58 -> "Strong"
        e >= 42 -> "Regional"
        else -> "Low"
    }

    // ---------- tournament history ----------

    /** NCAA round reached in a finished season: 0 missed, 1 First Four/R64 ... 7 champion. */
    fun roundOf(result: String): Int {
        if (result == "First Four") return 1
        for (r in 7 downTo 1) if (Awards.resultLabel(r) == result) return r
        return 0
    }

    /** The most recent finished seasons, newest first, as NCAA rounds. */
    fun recentRounds(t: Team, n: Int): List<Int> = t.history.takeLast(n).reversed().map { roundOf(it.result) }

    private fun proofValue(round: Int) = when (round) { 0 -> 25.0; 1 -> 50.0; 2 -> 62.0; 3 -> 74.0; 4 -> 84.0; 5 -> 92.0; 6 -> 96.0; else -> 100.0 }

    /** Recent proof: the last three NCAA Tournaments, newest counting most. Missing years lean on Legacy. */
    fun proof(t: Team): Double {
        val r = recentRounds(t, 3)
        val seed = (t.legacy - 10).coerceIn(15.0, 90.0)
        val w = doubleArrayOf(0.5, 0.3, 0.2)
        var s = 0.0
        for (i in 0 until 3) s += w[i] * (r.getOrNull(i)?.let { proofValue(it) } ?: seed)
        return s
    }

    // ---------- resume ceiling ----------

    /** What a season's NCAA result supports when repeated year after year. */
    fun supportFor(round: Int) = when (round) { 0 -> 55.0; 1 -> 68.0; 2 -> 80.0; 3 -> 87.0; 4 -> 90.0; 5 -> 93.0; 6 -> 95.0; else -> 97.0 }

    /** What one deep run supports on its own, for the next 10 years. */
    fun singleRun(round: Int) = when (round) { 4 -> 87.0; 5 -> 90.0; 6 -> 92.0; 7 -> 94.0; else -> 0.0 }

    /**
     * The soft ceiling from the last 10 NCAA Tournaments. Years before the save began count
     * at the level the program's Legacy implies. Pass [thisSeason] before it's in the history.
     */
    fun ceiling(t: Team, thisSeason: Int? = null): Double {
        val rounds = (listOfNotNull(thisSeason) + recentRounds(t, RESUME_YEARS)).take(RESUME_YEARS)
        val seed = (t.legacy - 2).coerceIn(50.0, 96.0)
        val slots = rounds.map { supportFor(it) } + List(RESUME_YEARS - rounds.size) { seed }
        val best = rounds.maxOfOrNull { singleRun(it) } ?: 0.0
        return max(max(slots.average(), best), 70.0) + 2
    }

    fun ceilingLabel(c: Double) = when {
        c >= 93 -> "Title-level resume"
        c >= 89 -> "Final Four resume"
        c >= 84 -> "Sweet 16 resume"
        c >= 78 -> "Round of 32 resume"
        else -> "Thin resume"
    }

    // ---------- program pull ----------

    fun pull(lg: League, t: Team): Int {
        val c = lg.coachOf(t)
        val v = 0.45 * t.prestige + 0.15 * t.legacy + 0.10 * exposure(lg, t.confId) + 0.10 * c.rec + 0.10 * proof(t) + 0.10 * t.buzz
        return v.roundToInt().coerceIn(1, 99)
    }

    /** Pull for every team at once (exposure computed once per conference). */
    fun pullTable(lg: League): IntArray {
        val exp = HashMap<Int, Int>()
        return IntArray(lg.teams.size) { i ->
            val t = lg.teams[i]
            val e = exp.getOrPut(t.confId) { exposure(lg, t.confId) }
            val v = 0.45 * t.prestige + 0.15 * t.legacy + 0.10 * e + 0.10 * lg.coachOf(t).rec + 0.10 * proof(t) + 0.10 * t.buzz
            v.roundToInt().coerceIn(1, 99)
        }
    }

    fun buzzLabel(b: Double) = when {
        b >= 60 -> "On fire"
        b >= 40 -> "Hot"
        b >= 22 -> "Warm"
        b >= 10 -> "Quiet"
        else -> "Cold"
    }

    // ---------- buzz ----------

    fun afterGame(lg: League, g: Game) {
        val w = lg.team(g.winner())
        val l = lg.team(g.loser())
        for (t in arrayOf(w, l)) t.buzz *= 0.965
        var gain = 0.6
        if (l.ap in 1..10) gain += 3.5 else if (l.ap > 0) gain += 2.0
        if (l.ap > 0 && !g.neutral && g.away == w.id) gain += 1.0
        if (l.prestige - w.prestige >= 15) gain += 1.5
        if (lg.rivalry(g) != null) gain += 1.0
        when (g.type) {
            GameType.NCAA -> {
                val round = Moments.ncaaRound(lg, g)
                gain += when (round) { 0 -> 2.0; 1 -> 5.0; 2 -> 6.0; 3 -> 8.0; 4 -> 10.0; 5 -> 12.0; else -> 15.0 }
                val seed = lg.ncaa?.seeds?.get(w.id) ?: 0
                if (seed >= 10) gain *= 1.5
            }
            GameType.NIT -> gain += 1.0
            GameType.CONF_TOURNEY -> {
                val b = lg.confBrackets.firstOrNull { it.confId == w.confId }
                if (b != null && b.champion == w.id) gain += 4.0
            }
            else -> {}
        }
        w.buzz = (w.buzz + gain).coerceAtMost(100.0)
        var loss = 0.4
        if (w.prestige + 20 <= l.prestige && g.type != GameType.NCAA) loss += 2.0
        l.buzz = (l.buzz - loss).coerceAtLeast(0.0)
    }

    /** Buzz cools over the summer. */
    fun newSeason(t: Team) { t.buzz *= 0.5 }

    // ---------- prestige ----------

    class Target(
        val record: Double, val conference: Double, val march: Double, val quality: Double, val draft: Double, val awards: Double,
        /** Below 1: the conference's resistance. Records in a low-exposure league count for less. */
        val resistance: Double,
        /** The national stage: a high-exposure league adds, a low one takes away. */
        val stage: Double,
    ) {
        val total get() = (record + conference) * resistance + stage + march + quality + draft + awards
    }

    fun target(lg: League, t: Team, round: Int, confPlace: Int, confChamp: Boolean): Target {
        val record = 24 + t.winPct * 46
        val conf = (if (confPlace == 1) 5.0 else if (confPlace <= 3) 2.0 else 0.0) + if (confChamp) 3.0 else 0.0
        val e = exposure(lg, t.confId)
        val resist = 0.72 + 0.28 * e / 100.0
        val march = when (round) { 0 -> 0.0; 1 -> 5.0; 2 -> 10.0; 3 -> 16.0; 4 -> 21.0; 5 -> 27.0; 6 -> 30.0; else -> 34.0 }
        val q1 = Rankings.quadRecord(lg, t, 1).substringBefore('-').toIntOrNull() ?: 0
        val quality = q1.coerceAtMost(9) * 1.2
        val lastDraft = lg.draftHistory.filter { it.season == lg.season - 1 && it.teamId == t.id }.sumOf { Pipeline.value(it.pick) }
        val awards = lg.seasonAwards.count { it.teamId == t.id } * 1.5
        return Target(record, conf, march, quality, lastDraft.coerceAtMost(5).toDouble(), awards.coerceAtMost(5.0), resist, (e - 50) * 0.25)
    }

    /**
     * End-of-season prestige. Moves 15% toward what the season earned (faster for a fallen power
     * with Legacy to fall back on), leans slightly on Legacy, then meets the resume ceiling.
     */
    fun newPrestige(t: Team, earned: Double, round: Int): Double {
        val now = t.prestige.toDouble()
        val climbBoost = if (earned > now && t.legacy > now) 0.12 * ((t.legacy - now) / 25).coerceIn(0.0, 1.0) else 0.0
        val rate = if (earned >= now) 0.15 + climbBoost else 0.12
        var p = now + (earned - now) * rate
        p += (t.legacy - p) * 0.04
        val ceil = ceiling(t, round)
        if (p > 70 && p > ceil) {
            if (p > now) {
                val start = max(now, ceil)
                if (p > start) p = start + (p - start) * 0.25
            }
            if (p > 80) p -= (p - max(ceil, 80.0)) * 0.15
        }
        return p
    }

    /** Legacy creeps toward Prestige, with a bump for the biggest moments. */
    fun updateLegacy(t: Team, round: Int) {
        t.legacy += (t.prestige - t.legacy) * 0.05
        if (round >= 5) t.legacy += 0.8
        if (round == 7) t.legacy += 1.2
        t.legacy = t.legacy.coerceIn(5.0, 99.0)
    }
}
