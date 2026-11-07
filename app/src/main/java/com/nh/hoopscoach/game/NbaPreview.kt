package com.nh.hoopscoach.game

import kotlinx.serialization.Serializable
import kotlin.math.exp
import kotlin.math.roundToInt

/** A team's NBA projection from simulating the season: record, win range, seed and playoff odds. */
@Serializable
class NbaProj(
    val w: Int, val l: Int, val lo: Int, val hi: Int,
    /** Average conference finish. */
    val seed: Double,
    /** Odds: the No. 1 seed, a top-six seed, the play-in, the playoffs, each round won, the title. */
    val top: Double, val six: Double, val playIn: Double, val playoffs: Double,
    val second: Double, val confFinals: Double, val finals: Double, val title: Double,
)

/** The NBA's preseason magazine. */
@Serializable
class NbaPreview(
    val season: Int,
    val proj: Map<Int, NbaProj>,
    val mvp: List<PoyOdds> = emptyList(),
    val roy: List<PoyOdds> = emptyList(),
    val dpoy: List<PoyOdds> = emptyList(),
    val sixth: List<PoyOdds> = emptyList(),
    val mip: List<PoyOdds> = emptyList(),
    val stories: List<Story> = emptyList(),
    val bold: List<String> = emptyList(),
    val headline: String = "",
    /** Last season by team: "52-30 · Lost in the second round". */
    val last: Map<Int, String> = emptyMap(),
    /** Last season's wins by team, for movement. */
    val lastWins: Map<Int, Int> = emptyMap(),
    /** The issue opens by itself once, at the start of the season. */
    var opened: Boolean = false,
)

object NbaPreviews {
    const val SIMS = 400

    /** This season's issue, built now if there isn't one yet. */
    fun current(lg: League): NbaPreview? {
        val ps = lg.proState ?: return null
        ps.preview?.takeIf { it.season == lg.season }?.let { return it }
        if (lg.phase == Phase.OFFSEASON) return ps.preview
        return build(lg).also { it.opened = true; ps.preview = it }
    }

    /** A player's last pro season, if he had one. */
    fun lastLine(lg: League, p: Player): SeasonLine? = p.career.lastOrNull { it.season == lg.season - 1 && it.cls == PRO_YEAR }

    /** How the magazine rates a player: his overall, plus a little for last season's scoring. */
    fun value(lg: League, p: Player) = p.ovr + (lastLine(lg, p)?.s?.ppg ?: 0.0) * 0.3

    /** The pick a rookie was taken with last summer, or null. */
    fun draftPick(lg: League, p: Player): Int? =
        p.awards.firstOrNull { it.startsWith("${lg.season - 1} Draft #") }?.substringAfter("#")?.toIntOrNull()

    /** Every team's last season, from its players' records. */
    private fun lastSeasons(lg: League): Map<Int, SeasonLine> {
        val out = HashMap<Int, SeasonLine>()
        for (p in lg.players) for (l in p.career) if (l.season == lg.season - 1 && l.cls == PRO_YEAR && l.teamId >= 0 && l.teamId !in out) out[l.teamId] = l
        return out
    }

    fun build(lg: League): NbaPreview {
        val ps = lg.proState!!
        val last = lastSeasons(lg)
        val proj = simulate(lg)
        val active = lg.teams.flatMap { lg.rosterOf(it) }
        fun projW(p: Player) = proj[p.teamId]?.w ?: 41
        fun odds(cands: List<Pair<Player, Double>>, temp: Double, n: Int): List<PoyOdds> {
            val top = cands.sortedByDescending { it.second }.take(n)
            val max = top.maxOfOrNull { it.second } ?: 0.0
            val w = top.map { it.first to exp((it.second - max) / temp) }
            val sum = w.sumOf { it.second }.coerceAtLeast(1e-9)
            return w.map { PoyOdds(it.first.id, it.second / sum) }.sortedByDescending { it.p }
        }
        val games = ProLeague.seasonGames(lg).coerceAtLeast(1)
        val mvp = odds(active.map { it to value(lg, it) + (projW(it) - games / 2.0) * 0.18 }, 2.6, 12)
        val roy = odds(active.filter { it.proYears == 0 }.map { it to it.ovr + (60 - (draftPick(lg, it) ?: 60)) * 0.04 }, 1.8, 10)
        val dpoy = odds(active.map { it to Ratings.defense(it) + it.ovr * 0.25 }, 2.0, 10)
        val sixth = odds(active.filter { p -> lg.rosterOf(lg.team(p.teamId)).sortedByDescending { it.ovr }.indexOf(p) in 5..8 }
            .map { it to it.ovr + (lastLine(lg, it)?.s?.ppg ?: 0.0) * 0.3 }, 1.8, 10)
        val mip = odds(active.filter { it.age <= 25 && it.proYears >= 1 && it.pot > it.ovr }.map { it to it.ovr * 0.6 + (it.pot - it.ovr) * 0.8 }, 2.0, 8)
        val lastText = lg.teams.mapNotNull { t -> last[t.id]?.let { t.id to "${it.w}-${it.l} · ${it.result.ifEmpty { "Missed playoffs" }}" } }.toMap()
        val lastWins = lg.teams.associate { t -> t.id to (last[t.id]?.w ?: ps.lastWins[t.id] ?: -1) }
        val base = NbaPreview(lg.season, proj, mvp, roy, dpoy, sixth, mip, last = lastText, lastWins = lastWins)
        return NbaPreview(
            lg.season, proj, mvp, roy, dpoy, sixth, mip,
            stories = storylines(lg, base), bold = bold(lg, base), headline = headline(lg, base), last = lastText, lastWins = lastWins,
        )
    }

    // ---------- the simulations ----------

    private fun simulate(lg: League): Map<Int, NbaProj> {
        val n = lg.teams.size
        val rnd = kotlin.random.Random(lg.season * 6271L + n * 17L)
        val era = Eras.of(lg)
        val elo = DoubleArray(n) { lg.team(it).elo }
        val reg = lg.games.filter { it.type == GameType.CONF || it.type == GameType.NONCONF }
        val home = IntArray(reg.size) { reg[it].home }
        val away = IntArray(reg.size) { reg[it].away }
        val pHome = DoubleArray(reg.size) { i -> val g = reg[i]; if (g.played) (if (g.hs > g.aws) 1.0 else 0.0) else Rankings.winProbFor(lg, g, g.home) }
        val played = IntArray(n)
        for (g in reg) { played[g.home]++; played[g.away]++ }
        val maxW = 100
        val hist = Array(n) { IntArray(maxW) }
        val seedSum = DoubleArray(n)
        val top = IntArray(n); val six = IntArray(n); val pin = IntArray(n); val po = IntArray(n)
        val r2 = IntArray(n); val cf = IntArray(n); val fin = IntArray(n); val title = IntArray(n)
        val wins = IntArray(n)
        val confs = lg.teams.map { it.confId }.distinct().sorted()
        fun game(a: Int, b: Int, aHome: Boolean) = rnd.nextDouble() < Rankings.winProb(elo[a], elo[b], if (aHome) 1 else -1)
        // A series: the better seed [a] has home court (games 1, 2, 5 and 7).
        fun series(a: Int, b: Int, bestOf: Int): Int {
            val need = bestOf / 2 + 1
            var wa = 0; var wb = 0; var g = 0
            while (wa < need && wb < need) {
                if (game(a, b, g == 0 || g == 1 || g == 4 || g == 6)) wa++ else wb++
                g++
            }
            return if (wa == need) a else b
        }
        repeat(SIMS) {
            wins.fill(0)
            for (i in reg.indices) wins[if (rnd.nextDouble() < pHome[i]) home[i] else away[i]]++
            for (t in 0 until n) hist[t][wins[t].coerceAtMost(maxW - 1)]++
            val champs = mutableListOf<Int>()
            for (c in confs) {
                val order = (0 until n).filter { lg.team(it).confId == c }.sortedByDescending { wins[it] + rnd.nextDouble() * 0.9 }
                order.forEachIndexed { i, t -> seedSum[t] += i + 1.0 }
                if (order.isEmpty()) continue
                top[order[0]]++
                order.take(6).forEach { six[it]++ }
                val field: List<Int> = if (era.playIn && order.size >= 10) {
                    order.subList(6, 10).forEach { pin[it]++ }
                    val a = order[6]; val b = order[7]
                    val seven = series(a, b, 1)
                    val loser = if (seven == a) b else a
                    val nine = series(order[8], order[9], 1)
                    val eight = series(loser, nine, 1)
                    order.take(6) + seven + eight
                } else {
                    var size = 1
                    while (size * 2 <= minOf(8, order.size)) size *= 2
                    order.take(size)
                }
                field.forEach { po[it]++ }
                // Bracket: 1 v 8 and 4 v 5 on one side, 2 v 7 and 3 v 6 on the other.
                val pairs = when (field.size) { 8 -> listOf(0 to 7, 3 to 4, 1 to 6, 2 to 5); 4 -> listOf(0 to 3, 1 to 2); 2 -> listOf(0 to 1); else -> emptyList() }
                if (pairs.isEmpty()) { champs += field.firstOrNull() ?: continue; continue }
                var round = pairs.map { (x, y) -> series(field[x], field[y], if (field.size == 8) era.firstRoundBestOf else 7) }
                round.forEach { if (field.size == 8) r2[it]++ }
                if (field.size == 4) round.forEach { cf[it]++ }
                val seedOf = field.withIndex().associate { (i, t) -> t to i }
                while (round.size > 1) {
                    round = round.chunked(2).map { (x, y) -> if (seedOf.getValue(x) <= seedOf.getValue(y)) series(x, y, 7) else series(y, x, 7) }
                    if (round.size == 2) round.forEach { cf[it]++ }
                }
                fin[round[0]]++
                champs += round[0]
            }
            if (champs.size == 2) {
                val (x, y) = champs
                title[if (wins[x] >= wins[y]) series(x, y, 7) else series(y, x, 7)]++
            } else champs.firstOrNull()?.let { title[it]++ }
        }
        val sims = SIMS.toDouble()
        fun pct(t: Int, q: Double): Int {
            var acc = 0
            for (w in 0 until maxW) { acc += hist[t][w]; if (acc >= SIMS * q) return w }
            return maxW - 1
        }
        return (0 until n).associateWith { t ->
            val mean = (0 until maxW).sumOf { it * hist[t][it] } / sims
            val w = mean.roundToInt().coerceIn(0, played[t])
            NbaProj(w, played[t] - w, pct(t, 0.1), pct(t, 0.9), seedSum[t] / sims,
                top[t] / sims, six[t] / sims, pin[t] / sims, po[t] / sims, r2[t] / sims, cf[t] / sims, fin[t] / sims, title[t] / sims)
        }
    }

    // ---------- the writing ----------

    private fun <T> pick(lg: League, salt: Int, vararg options: T): T =
        options[kotlin.random.Random(lg.season * 1_000_033L + salt).nextInt(options.size)]

    private fun pct(v: Double) = Previews.pct(v)

    /** "the Spurs'", "the Heat's". */
    fun possessive(name: String) = if (name.endsWith("s")) "$name'" else "$name's"

    /** "a 12% chance", "an 8% chance", "almost no chance". */
    fun chance(v: Double): String {
        if (v < 0.005) return "almost no chance"
        val n = (v * 100).roundToInt().coerceIn(1, 99)
        return (if (n == 8 || n == 11 || n == 18 || n in 80..89) "an " else "a ") + "$n% chance"
    }
    private fun f1(v: Double) = "%.1f".format(v)

    fun ranked(lg: League, pv: NbaPreview): List<Team> =
        lg.teams.sortedWith(compareByDescending<Team> { pv.proj[it.id]?.title ?: 0.0 }.thenByDescending { pv.proj[it.id]?.w ?: 0 })

    /** Power rankings: projected wins, then title odds. */
    fun power(lg: League, pv: NbaPreview): List<Team> =
        lg.teams.sortedWith(compareByDescending<Team> { pv.proj[it.id]?.w ?: 0 }.thenByDescending { pv.proj[it.id]?.title ?: 0.0 }.thenByDescending { it.elo })

    fun star(lg: League, t: Team): Player? = lg.rosterOf(t).maxByOrNull { value(lg, it) }

    /** The best players who changed teams since last season, and how they got there. */
    fun newHomes(lg: League): List<Triple<Player, Team, String>> {
        val ps = lg.proState ?: return emptyList()
        val trades = ps.trades.filter { it.season == lg.season - 1 && it.offseason }
        return lg.teams.flatMap { lg.rosterOf(it) }.mapNotNull { p ->
            val l = lastLine(lg, p) ?: return@mapNotNull null
            if (l.teamId < 0 || l.teamId == p.teamId) return@mapNotNull null
            val how = if (trades.any { p.name in it.toA || p.name in it.toB }) "traded" else "signed"
            Triple(p, lg.team(l.teamId), how)
        }.sortedByDescending { value(lg, it.first) }
    }

    private fun headline(lg: League, pv: NbaPreview): String {
        val top = ranked(lg, pv).first()
        val o = pv.proj.getValue(top.id)
        val champ = lg.proState?.history?.lastOrNull()?.champion
        return when {
            champ == top.id -> pick(lg, 1, "Run it back", "Repeat?", "The champs are still the team to beat")
            o.title < 0.16 -> pick(lg, 2, "Wide open", "Anyone's title", "No favorite")
            else -> pick(lg, 3, "The team to beat", "Chasing ${top.name}", "Built for June")
        }
    }

    fun storylines(lg: League, pv: NbaPreview): List<Story> {
        val ps = lg.proState ?: return emptyList()
        val out = mutableListOf<Story>()
        val ranked = ranked(lg, pv)
        fun pr(t: Team) = pv.proj.getValue(t.id)
        val top = ranked[0]
        val to = pr(top)
        val champId = ps.history.lastOrNull()?.champion ?: -1
        val contenders = ranked.count { pr(it).title >= 0.05 }
        val topStar = star(lg, top)
        out += if (to.title >= 0.16) Story(
            "The favorite",
            pick(lg, 10, "The ${top.name} are the team to beat", "Everyone is chasing the ${top.name}", "The title is the ${possessive(top.name)} to lose"),
            (if (champId == top.id) "The defending champions start where they finished. " else "") +
                "The ${top.name} win the title in ${pct(to.title)} of our $SIMS simulations and reach the Finals in ${pct(to.finals)}." +
                (topStar?.let { " ${it.name} leads a team we project to go ${to.w}-${to.l}." } ?: ""),
            top.id, topStar?.id ?: -1,
        ) else Story(
            "Wide open",
            pick(lg, 11, "No team stands above the rest", "It's anyone's title", "A season without a favorite"),
            "Our favorite, the ${top.name}, win it all in only ${pct(to.title)} of our simulations, and $contenders teams have at least a 5% shot. The ${ranked[1].name} and ${ranked[2].name} are right there.",
            top.id,
        )
        // The user's player and his team.
        val hero = lg.playerOrNull(lg.heroId)?.takeIf { it.teamId >= 0 }
        if (hero != null) {
            val ht = lg.team(hero.teamId)
            val l = lastLine(lg, hero)
            val rank = lg.rosterOf(ht).sortedByDescending { it.ovr }.indexOf(hero) + 1
            val mvpRank = pv.mvp.indexOfFirst { it.pid == hero.id }
            val royRank = pv.roy.indexOfFirst { it.pid == hero.id }
            out += Story(
                "Spotlight",
                when {
                    hero.proYears == 0 -> pick(lg, 20, "${hero.name}'s rookie year", "Welcome to the league, ${hero.name}")
                    mvpRank in 0..2 -> pick(lg, 21, "${hero.name} chases the MVP", "Is this ${hero.name}'s league now?")
                    else -> pick(lg, 22, "Year ${hero.proYears + 1} for ${hero.name}", "All eyes on ${hero.name}")
                },
                buildString {
                    append(if (l != null) "${hero.name} averaged ${f1(l.s.ppg)} points, ${f1(l.s.rpg)} rebounds and ${f1(l.s.apg)} assists last season. " else "${hero.name} starts his pro career with the ${ht.name}. ")
                    append(when (rank) { 1 -> "He's the ${possessive(ht.name)} best player"; in 2..3 -> "He's one of the ${possessive(ht.name)} top three players"; in 4..5 -> "He projects as a starter"; else -> "He'll have to earn his minutes" })
                    append(when {
                        mvpRank >= 0 -> ", and he's No. ${mvpRank + 1} in our MVP odds at ${pct(pv.mvp[mvpRank].p)}."
                        royRank >= 0 -> ", and he's No. ${royRank + 1} in our Rookie of the Year odds at ${pct(pv.roy[royRank].p)}."
                        else -> "."
                    })
                },
                ht.id, hero.id,
            )
        }
        val u = lg.user
        val uo = pr(u)
        out += Story(
            "Your team",
            when {
                uo.title >= 0.12 -> pick(lg, 30, "The ${u.name} are built to win now", "Title or bust for the ${u.name}")
                uo.playoffs >= 0.7 -> pick(lg, 31, "The ${u.name} should be a playoff team", "A playoff push for the ${u.name}")
                uo.playoffs >= 0.35 -> pick(lg, 32, "The ${u.name} are on the bubble", "Play-in or bust?")
                else -> pick(lg, 33, "A rebuilding year for the ${u.name}", "The ${u.name} are playing for the future")
            },
            "We project the ${u.name} at ${uo.w}-${uo.l} (somewhere between ${uo.lo} and ${uo.hi} wins), about ${Previews.ordinal(uo.seed.roundToInt())} in the ${lg.conf(u.confId).name}. Playoffs: ${pct(uo.playoffs)}. A top-six seed: ${pct(uo.six)}. Conference finals: ${pct(uo.confFinals)}. Title: ${pct(uo.title)}.",
            u.id,
        )
        // The defending champion.
        if (champId >= 0 && champId != top.id) {
            val c = lg.team(champId)
            val co = pr(c)
            out += Story(
                "The champs",
                if (co.title >= 0.1) pick(lg, 40, "Can the ${c.name} repeat?", "The ${c.name} go for two") else pick(lg, 41, "The ${c.name} defend their crown", "Life after the title for the ${c.name}"),
                "The defending champions project at ${co.w}-${co.l}, with ${chance(co.title)} to win it all again and ${pct(co.finals)} odds to get back to the Finals.",
                c.id,
            )
        }
        // The summer's biggest move.
        newHomes(lg).firstOrNull()?.let { (p, from, how) ->
            val t = lg.team(p.teamId)
            val l = lastLine(lg, p)
            out += Story(
                if (how == "traded") "Blockbuster" else "Free agency",
                pick(lg, 50, "${p.name} has a new home", "${p.name} joins the ${t.name}", "The summer belonged to the ${t.name}"),
                "${p.name} ${if (how == "traded") "was traded" else "signed"} from the ${from.name} to the ${t.name}" +
                    (l?.let { " after averaging ${f1(it.s.ppg)} points a game" } ?: "") + ". With him, we project the ${t.name} at ${pr(t).w}-${pr(t).l} and give them ${chance(pr(t).playoffs)} to make the playoffs.",
                t.id, p.id,
            )
        }
        // The top pick.
        lg.teams.flatMap { lg.rosterOf(it) }.firstOrNull { draftPick(lg, it) == 1 }?.let { p ->
            val t = lg.team(p.teamId)
            val roy = pv.roy.firstOrNull { it.pid == p.id }?.p ?: 0.0
            out += Story(
                "The No. 1 pick",
                pick(lg, 60, "${p.name} arrives", "The ${t.name} get their man", "Meet ${p.name}"),
                "The No. 1 pick in the draft, ${p.name} (${p.posLabel}${p.highSchool.takeIf { it.isNotEmpty() }?.let { ", $it" } ?: ""}) starts his career with the ${t.name}. We give him ${chance(roy)} at Rookie of the Year.",
                t.id, p.id,
            )
        }
        // The MVP race.
        pv.mvp.firstOrNull()?.let { o ->
            val p = lg.player(o.pid)
            val rest = pv.mvp.drop(1).take(2).mapNotNull { x -> lg.playerOrNull(x.pid)?.let { "${it.name} (${pct(x.p)})" } }
            out += Story(
                "MVP race",
                pick(lg, 70, "${p.name} leads the MVP race", "The MVP is ${p.last}'s to lose", "The race starts with ${p.name}"),
                "${p.name} of the ${lg.team(p.teamId).name} heads our MVP odds at ${pct(o.p)}" + (if (rest.isNotEmpty()) ", ahead of ${rest.joinToString(" and ")}." else "."),
                p.teamId, p.id,
            )
        }
        // A new coach.
        ps.staffMoves.firstNotNullOfOrNull { line -> lg.teams.firstOrNull { line.startsWith("The ${it.name} fire") }?.let { it to line } }?.let { (t, line) ->
            out += Story(
                "New coach",
                pick(lg, 80, "A new voice for the ${t.name}", "The ${t.name} start over on the bench"),
                "$line. We project ${pr(t).w}-${pr(t).l} in year one.",
                t.id,
            )
        }
        // Risers and fallers against last season's wins.
        val moves = lg.teams.mapNotNull { t -> pv.lastWins[t.id]?.takeIf { it >= 0 }?.let { t to pr(t).w - it } }
        moves.maxByOrNull { it.second }?.takeIf { it.second >= 8 }?.let { (t, d) ->
            out += Story(
                "On the rise",
                pick(lg, 90, "The ${t.name} take a leap", "From ${pv.lastWins[t.id]} wins to ${pr(t).w}?"),
                "The ${t.name} won ${pv.lastWins[t.id]} games last season. We project ${pr(t).w}, $d more, and give them ${chance(pr(t).playoffs)} to make the playoffs." +
                    (star(lg, t)?.let { " ${it.name} leads the way." } ?: ""),
                t.id,
            )
        }
        moves.minByOrNull { it.second }?.takeIf { it.second <= -8 }?.let { (t, d) ->
            out += Story(
                "Falling back",
                pick(lg, 100, "Trouble for the ${t.name}", "The ${t.name} take a step back"),
                "${pv.lastWins[t.id]} wins last season, ${pr(t).w} in our projection (${-d} fewer). Playoff odds: ${pct(pr(t).playoffs)}.",
                t.id,
            )
        }
        // A veteran's last runs.
        lg.teams.flatMap { lg.rosterOf(it) }.filter { it.age >= 35 && it.id != lg.heroId }.maxByOrNull { value(lg, it) }?.let { p ->
            out += Story(
                "Still going",
                pick(lg, 110, "One more run for ${p.name}", "${p.name} isn't done yet"),
                "${p.name} is ${p.age} and still rated ${Ratings.shownPro(p.ovr)} on our board. With him, the ${lg.team(p.teamId).name} have ${chance(pr(lg.team(p.teamId)).title)} at a title this year.",
                p.teamId, p.id,
            )
        }
        // The bottom of the league.
        lg.teams.filter { it.id != u.id }.minByOrNull { pr(it).w }?.let { t ->
            out += Story(
                "Lottery watch",
                pick(lg, 120, "The ${t.name} are playing for the lottery", "A long year ahead for the ${t.name}"),
                "We have the ${t.name} at ${pr(t).w}-${pr(t).l}, near the bottom of the league. Playoff odds: ${pct(pr(t).playoffs)}.",
                t.id,
            )
        }
        return out.take(12)
    }

    private fun bold(lg: League, pv: NbaPreview): List<String> {
        val ps = lg.proState ?: return emptyList()
        val ranked = ranked(lg, pv)
        fun pr(t: Team) = pv.proj.getValue(t.id)
        val pool = mutableListOf<String>()
        ranked.first().let { if (pr(it).finals < 0.55) pool += "The ${it.name} don't make the Finals." }
        ranked.getOrNull(7)?.let { pool += "The ${it.name} reach the conference finals." }
        lg.teams.minByOrNull { pr(it).w }?.let { if (pr(it).w <= 26) pool += "The ${it.name} win fewer than 20 games." }
        pv.roy.firstOrNull()?.let { o -> lg.playerOrNull(o.pid)?.let { pool += "${it.name} averages 20 points as a rookie." } }
        pv.mip.firstOrNull()?.let { o -> lg.playerOrNull(o.pid)?.let { pool += "${it.name} wins Most Improved Player." } }
        if (Eras.of(lg).playIn) pool += "A team from the play-in wins a first-round series."
        ps.history.lastOrNull()?.champion?.takeIf { it >= 0 }?.let { pool += "The ${lg.team(it).name} lose in the first round." }
        pv.mvp.getOrNull(3)?.let { o -> lg.playerOrNull(o.pid)?.let { pool += "${it.name} wins MVP." } }
        val picked = pool.shuffled(kotlin.random.Random(lg.season * 37L + 3)).take(5)
        val hero = lg.playerOrNull(lg.heroId)?.takeIf { it.teamId >= 0 }
        val own = when {
            hero == null -> null
            hero.proYears == 0 -> "${hero.name} makes the All-Rookie First Team."
            pv.mvp.take(3).any { it.pid == hero.id } -> "${hero.name} wins MVP."
            pv.mvp.any { it.pid == hero.id } -> "${hero.name} makes an All-NBA team."
            else -> "${hero.name} sets a career high in points per game."
        }
        return picked + listOfNotNull(own)
    }

    /** The editor's letter. */
    fun letter(lg: League, pv: NbaPreview): List<String> {
        val ps = lg.proState
        val ranked = ranked(lg, pv)
        val top = ranked[0]
        val to = pv.proj.getValue(top.id)
        val champ = ps?.history?.lastOrNull()?.champion?.takeIf { it >= 0 }?.let { lg.team(it) }
        val u = lg.user
        val uo = pv.proj.getValue(u.id)
        val out = mutableListOf<String>()
        out += pick(lg, 200, "Welcome to the ${lg.season} season. ", "The ${lg.season} season tips off. ", "Basketball is back. ") +
            "We simulated it $SIMS times, through the playoffs. The ${top.name} won the title in ${pct(to.title)} of them, the most of any team. " +
            (champ?.let { if (it.id == top.id) "The defending champions are the team to catch." else "The defending champion ${it.name} won it in ${pct(pv.proj.getValue(it.id).title)}." } ?: "")
        val east = lg.teams.filter { it.confId == 0 }.maxByOrNull { pv.proj.getValue(it.id).finals }
        val west = lg.teams.filter { it.confId == 1 }.maxByOrNull { pv.proj.getValue(it.id).finals }
        if (east != null && west != null) out += "Our Finals: the ${east.name} against the ${west.name}. " +
            (pv.mvp.firstOrNull()?.let { lg.playerOrNull(it.pid) }?.let { "${it.name} opens as the MVP favorite." } ?: "")
        out += "The ${u.name} come in at ${uo.w}-${uo.l} in our projection, with ${chance(uo.playoffs)} at the playoffs. Turn to the team report for the full breakdown."
        return out
    }
}
