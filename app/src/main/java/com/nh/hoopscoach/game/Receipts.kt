package com.nh.hoopscoach.game

import kotlin.math.roundToInt

/**
 * The magazine can be wrong. Its writers don't see true strength: they lean on last season's record,
 * on star names and (in college) on big programs, and they simply misjudge some teams. [bias] is that
 * lean, in Elo, used for the college poll; simulation odds use strength and known availability. When a season ends its bold predictions are graded
 * (right, wrong, and what really happened), for the season review, the old issue and next year's
 * report card.
 */
object Receipts {

    // ---------- how the writers see the league ----------

    /** Each team's perceived strength minus its real strength, in Elo. Seeded by season, so an issue is stable. */
    fun bias(lg: League): DoubleArray {
        val n = lg.teams.size
        val out = DoubleArray(n)
        if (n < 4) return out
        val rnd = kotlin.random.Random(lg.season * 4099L + 77)
        val elos = lg.teams.map { it.elo }.sorted()
        // Last season's win rate, where there was one.
        val last = lg.teams.associate { t ->
            t.id to if (lg.pro) NbaHistory.last(lg, t)?.let { it.w.toDouble() / (it.w + it.l).coerceAtLeast(1) }
            else t.history.lastOrNull { it.season == lg.season - 1 }?.let { it.w.toDouble() / (it.w + it.l).coerceAtLeast(1) }
        }
        // What last season says each team is: the strength at its place in last season's order.
        val haveLast = lg.teams.filter { last[it.id] != null }.sortedBy { last[it.id]!! }
        val fromLast = HashMap<Int, Double>()
        haveLast.forEachIndexed { i, t -> fromLast[t.id] = elos[(i.toDouble() / (haveLast.size - 1).coerceAtLeast(1) * (n - 1)).roundToInt()] }
        val stars = if (lg.pro) lg.teams.associate { t -> t.id to (lg.rosterOf(t).maxOfOrNull { NbaPreviews.value(lg, it) } ?: 0.0) } else emptyMap()
        val starAvg = stars.values.average().takeIf { !it.isNaN() } ?: 0.0
        for (t in lg.teams) {
            var b = 0.0
            // Recency: a team that changed a lot over the summer is still judged by last season.
            fromLast[t.id]?.let { b += (it - t.elo) * if (lg.pro) 0.35 else 0.3 }
            // Names: the best player in the league sells a team; a team without one gets overlooked.
            if (lg.pro) b += ((stars[t.id] ?: starAvg) - starAvg) * 2.5
            // Brands: the blue bloods get the benefit of the doubt.
            else b += (t.prestige - 60) * 0.7
            // And plain error.
            b += gauss(rnd) * if (lg.pro) 30.0 else 34.0
            out[t.id] = b.coerceIn(-110.0, 110.0)
        }
        return out
    }

    private fun gauss(r: kotlin.random.Random): Double {
        val u = r.nextDouble().coerceAtLeast(1e-9)
        val v = r.nextDouble()
        return kotlin.math.sqrt(-2 * kotlin.math.ln(u)) * kotlin.math.cos(2 * Math.PI * v)
    }

    // ---------- grading the bold predictions ----------

    private fun f1(d: Double) = "%.1f".format(d)

    /** Grades this season's NBA calls. Run when the season ends, before the review column. */
    fun gradePro(lg: League) {
        val ps = lg.proState ?: return
        val pv = ps.preview?.takeIf { it.season == lg.season } ?: return
        pv.regularActual = regularWins(lg)
        if (pv.calls.isEmpty()) return
        val res = pv.calls.map { runCatching { pro(lg, ps, it) }.getOrNull() ?: (-1 to "") }
        pv.graded = res.map { it.first }
        pv.outcomes = res.map { it.second }
    }

    /** Grades this season's college calls. Run when the season ends, before the review column. */
    fun gradeCollege(lg: League) {
        val pv = lg.preview?.takeIf { it.season == lg.season } ?: return
        pv.regularActual = regularWins(lg)
        if (pv.calls.isEmpty()) return
        val res = pv.calls.map { runCatching { college(lg, it) }.getOrNull() ?: (-1 to "") }
        pv.graded = res.map { it.first }
        pv.outcomes = res.map { it.second }
    }

    private fun hit(b: Boolean, text: String) = (if (b) 1 else 0) to text

    private fun regularWins(lg: League): Map<Int, Int> {
        val games = lg.games.filter { it.played && PreviewAvailability.regular(it) }
        return lg.teams.associate { t -> t.id to games.count { it.involves(t.id) && (if (it.hs > it.aws) it.home else it.away) == t.id } }
    }

    fun check(lg: League, call: Call): Pair<Int, String> = runCatching {
        if (lg.pro) pro(lg, lg.proState!!, call) else college(lg, call)
    }.getOrDefault(-1 to "Result unavailable.")

    private fun pro(lg: League, ps: ProState, c: Call): Pair<Int, String> {
        val t = lg.teams.getOrNull(c.team)
        val p = lg.playerOrNull(c.pid)
        val playoffs = ps.series.filter { it.round >= 1 }
        fun reached(id: Int) = playoffs.filter { it.involves(id) }.maxOfOrNull { it.round } ?: 0
        fun how(id: Int): String = ProSeason.resultFor(lg, id).ifEmpty { "Missed playoffs" }.let { r ->
            when {
                r == "Champion" -> "They won the title."
                r == "Missed playoffs" -> "They missed the playoffs."
                r.startsWith("Lost") -> "They ${r.replaceFirstChar { it.lowercase() }}."
                else -> "$r."
            }
        }
        fun award(code: Int) = when (code) { 1 -> "MVP"; 2 -> "Rookie of the Year"; 3 -> "Defensive Player of the Year"; else -> "Most Improved Player" }
        return when (c.kind) {
            "NO_FINALS" -> t!!.let { hit(reached(it.id) < 4, how(it.id)) }
            "CONF_FINALS" -> t!!.let { hit(reached(it.id) >= 3, how(it.id)) }
            "UNDER_W" -> t!!.let { hit(it.w < c.n, "They won ${it.w}.") }
            "OVER_W" -> t!!.let { hit(it.w >= c.n, "They won ${it.w}.") }
            "PPG" -> p!!.let { hit(it.stats.gp > 0 && it.stats.ppg >= c.n, "He averaged ${f1(it.stats.ppg)}.") }
            "AWARD" -> {
                val a = award(c.n)
                val winner = lg.seasonAwards.firstOrNull { it.award == a }
                hit(winner?.pid == c.pid, winner?.let { if (it.pid == c.pid) "He won it." else "${it.name} won it." } ?: "Nobody won it.")
            }
            "HONOR" -> p!!.let { pl ->
                val got = if (c.n == 1) pl.awards.contains("${lg.season} All-Rookie 1st") else pl.awards.any { it.startsWith("${lg.season} All-NBA") }
                hit(got, if (got) "He made it." else "He didn't make it.")
            }
            "CAREER_PPG" -> p!!.let { hit(it.stats.gp > 0 && it.stats.ppg * 10 > c.n, "He averaged ${f1(it.stats.ppg)}, against a best of ${f1(c.n / 10.0)}.") }
            "FIRST_ROUND_EXIT" -> t!!.let { hit(reached(it.id) == 1 && ProSeason.resultFor(lg, it.id).startsWith("Lost"), how(it.id)) }
            "PLAYOFFS" -> t!!.let { hit(reached(it.id) >= 1, how(it.id)) }
            "MISS_PLAYOFFS" -> t!!.let { hit(reached(it.id) == 0, how(it.id)) }
            "PLAYIN_SERIES" -> {
                val playIn = ps.series.filter { it.round == 0 }.flatMap { listOf(it.hi, it.lo) }.toSet()
                val won = playoffs.firstOrNull { it.round == 1 && it.done && it.winner in playIn }
                hit(won != null, won?.let { "The ${lg.team(it.winner).name} did it." } ?: "None did.")
            }
            "SCORING_TITLE" -> {
                val most = lg.teams.maxOfOrNull { it.w + it.l } ?: 0
                val top = lg.players.filter { it.teamId >= 0 && it.stats.gp >= maxOf(3, (most * 0.6).roundToInt()) }.maxByOrNull { it.stats.ppg }
                hit(top?.id == c.pid, top?.let { if (it.id == c.pid) "He did, at ${f1(it.stats.ppg)}." else "${it.name} won it at ${f1(it.stats.ppg)}." } ?: "")
            }
            "SEED_UPSET" -> {
                val up = playoffs.firstOrNull { s -> s.round == 1 && s.done && ps.seeds[s.hi] == c.n && ps.seeds[s.lo] == c.m && s.winner == s.lo }
                hit(up != null, up?.let { "The ${lg.team(it.lo).name} beat the ${lg.team(it.hi).name}." } ?: "Every No. ${c.n} seed survived.")
            }
            else -> -1 to ""
        }
    }

    private fun college(lg: League, c: Call): Pair<Int, String> {
        val t = lg.teams.getOrNull(c.team)
        val p = lg.playerOrNull(c.pid)
        fun round(id: Int) = Awards.tourneyRound(lg, id)
        fun how(id: Int): String = Awards.resultFor(lg, id).let { r ->
            when (r) {
                "National Champion" -> "They won it all."
                "Missed Tournament" -> "They missed the tournament."
                "Runner-Up" -> "They lost in the title game."
                "First Four" -> "They lost in the First Four."
                else -> if (r.startsWith("NIT")) "They missed the tournament (${r})." else "Their season ended in the ${r}."
            }
        }
        fun confTable(confId: Int) = lg.conf(confId).teamIds.map { lg.team(it) }.sortedWith(compareByDescending<Team> { it.cw - it.cl }.thenByDescending { it.w })
        return when (c.kind) {
            "C_AWARD" -> {
                val code = if (c.n == 2) "FOY" else "Nat MVP"
                val winner = lg.players.firstOrNull { it.awards.contains("${lg.season} $code") }
                hit(winner?.id == c.pid, winner?.let { if (it.id == c.pid) "He won it." else "${it.name} won it." } ?: "")
            }
            "C_NCAA_WINS" -> t!!.let { val w = (round(it.id) - 1).coerceAtLeast(0); hit(w >= c.n, how(it.id)) }
            "C_NET" -> t!!.let { hit(it.net in 1..c.n, "They finished No. ${it.net} in the NET.") }
            "C_FIRST_WEEKEND" -> t!!.let { hit(round(it.id) in 1..2, how(it.id)) }
            "C_BIDS" -> {
                val bids = lg.conf(c.team).teamIds.count { round(it) >= 1 }
                hit(bids >= c.n, "They got $bids.")
            }
            "C_NO_S16" -> t!!.let { hit(round(it.id) < 3, how(it.id)) }
            "C_F4" -> t!!.let { hit(round(it.id) >= 5, how(it.id)) }
            "C_NCAA" -> t!!.let { hit(round(it.id) >= 1, how(it.id)) }
            "C_NO_CONF" -> t!!.let { tm ->
                val table = confTable(tm.confId)
                val top = table.first()
                val won = (top.cw - top.cl) == (tm.cw - tm.cl)
                hit(!won, if (won) "They won it at ${tm.cw}-${tm.cl}." else "${top.school} won it; they went ${tm.cw}-${tm.cl}.")
            }
            "C_SCORING" -> {
                val most = lg.teams.maxOfOrNull { it.w + it.l } ?: 0
                val top = lg.players.filter { it.teamId >= 0 && it.stats.gp >= maxOf(3, (most * 0.6).roundToInt()) }.maxByOrNull { it.stats.ppg }
                hit(top?.id == c.pid && (top?.stats?.ppg ?: 0.0) > c.n, p?.let { "He averaged ${f1(it.stats.ppg)}" + (top?.takeIf { x -> x.id != it.id }?.let { x -> "; ${x.name} led at ${f1(x.stats.ppg)}." } ?: ".") } ?: "")
            }
            "C_CONF_BY" -> t!!.let { tm ->
                val next = confTable(tm.confId).filter { it.id != tm.id }.maxOfOrNull { it.cw - it.cl } ?: 0
                hit((tm.cw - tm.cl) - next >= c.n * 2, "They went ${tm.cw}-${tm.cl} in conference.")
            }
            "C_CONF_BEAT" -> t!!.let { tm ->
                val at = confTable(tm.confId).indexOfFirst { it.id == tm.id } + 1
                hit(at < c.n, "We picked them ${Previews.ordinal(c.n)}; they finished ${Previews.ordinal(at)}.")
            }
            "C_CONF_500" -> t!!.let { hit(it.cw > it.cl, "They went ${it.cw}-${it.cl} in conference.") }
            else -> -1 to ""
        }
    }

    // ---------- the writing ----------

    /** "Our bold predictions went 2 for 7" and the worst misses, or null when nothing was graded. */
    fun paragraph(bold: List<String>, graded: List<Int>, outcomes: List<String>, nextYear: Boolean): String? {
        val done = graded.indices.filter { graded[it] >= 0 && it < bold.size }
        if (done.isEmpty()) return null
        val right = done.count { graded[it] == 1 }
        val misses = done.filter { graded[it] == 0 }
        val head = when {
            right == 0 -> "Our bold predictions went 0 for ${done.size}. Every one of them was wrong."
            right == done.size -> "Our bold predictions went ${done.size} for ${done.size}. Frame it."
            right * 2 >= done.size -> "Our bold predictions went $right for ${done.size}, better than bold predictions usually do."
            else -> "Our bold predictions went $right for ${done.size}."
        }
        val wrong = misses.take(2).joinToString(" ") { i -> "We said \"${bold[i].trimEnd('.')}.\" ${outcomes.getOrNull(i).orEmpty()}".trim() }
        val gotIt = done.firstOrNull { graded[it] == 1 }?.let { i -> " We did call one: \"${bold[i].trimEnd('.')}.\"" } ?: ""
        val tail = if (nextYear) "" else if (misses.isNotEmpty()) " We'll own it." else ""
        return listOf(head, wrong).filter { it.isNotBlank() }.joinToString(" ") + gotIt + tail
    }
}
