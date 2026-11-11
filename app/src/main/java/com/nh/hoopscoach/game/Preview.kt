package com.nh.hoopscoach.game

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/** A team's preseason projection: national rank, predicted conference finish and projected records. */
@Serializable
class Proj(val nat: Int, val confRank: Int, val w: Int, val l: Int, val cw: Int, val cl: Int)

/** A team's odds from simulating the season: its conference title, an NCAA bid, the Sweet 16, the Final Four and the title, plus a likely win range. */
@Serializable
class Odds(val conf: Double, val ncaa: Double, val s16: Double, val f4: Double, val title: Double, val lo: Int, val hi: Int)

/** One storyline: a kicker, a headline and a few sentences, about a team or a player. */
@Serializable
class Story(val kicker: String, val head: String, var body: String, val teamId: Int = -1, val pid: Int = -1)

/**
 * A bold prediction, in a form the season can check: what kind of call it is, the team or player it's
 * about and the numbers in it. Graded when the season ends (see [Receipts]).
 */
@Serializable
class Call(val kind: String, val team: Int = -1, val pid: Int = -1, val n: Int = 0, val m: Int = 0)

/** A player's share of the Player of the Year odds. */
@Serializable
class PoyOdds(val pid: Int, val p: Double)

/** The preseason magazine: projections for every team, the players to watch and the experts' picks. */
@Serializable
class Preview(
    val season: Int,
    val proj: Map<Int, Proj>,
    val allAmerica: List<Int>,
    val freshmen: List<Int>,
    val transfers: List<Int>,
    val breakouts: List<Int>,
    /** Preseason Player of the Year for each conference (conference id to player id). */
    val confPoy: Map<Int, Int>,
    val finalFour: List<Int>,
    val sleeper: Int,
    /** 3 = the issue with season odds and storylines. */
    val version: Int = 1,
    val underRadar: List<Int> = emptyList(),
    val dpoy: Int = -1,
    val cinderella: Int = -1,
    /** Team whose coach we pick for Coach of the Year. */
    val cotyTeam: Int = -1,
    /** Regular-season games to circle, by game id. */
    val marquee: List<Int> = emptyList(),
    /** Teams whose coach is on the hot seat. */
    val hotSeat: List<Int> = emptyList(),
    val bold: List<String> = emptyList(),
    /** The issue opens by itself once, at the start of the season. */
    var opened: Boolean = false,
    /** Every team's odds, from simulating the season. */
    val odds: Map<Int, Odds> = emptyMap(),
    /** Each conference's average number of NCAA Tournament bids in the simulations. */
    val bids: Map<Int, Double> = emptyMap(),
    var stories: List<Story> = emptyList(),
    val poy: List<PoyOdds> = emptyList(),
    /** The cover headline. */
    val headline: String = "",
    /** This year's three feature pages, the cover's lead story (null: the favorite) and the cover's design. */
    val features: List<Feature> = emptyList(),
    val cover: Story? = null,
    val coverStyle: Int = 0,
    /** The bold predictions as checkable calls (same order as [bold]), and how they went: 1 right, 0 wrong, -1 can't tell. */
    val calls: List<Call> = emptyList(),
    var graded: List<Int> = emptyList(),
    var outcomes: List<String> = emptyList(),
    val debates: List<TvSegment> = emptyList(),
)

object Previews {
    /** Seasons simulated for the odds. */
    private const val SIMS = 400

    /** Built once per season, when the schedule is set. */
    fun build(lg: League): Preview {
        val n = lg.teams.size
        // The magazine projects what its writers see, not the truth (see Receipts.bias), so it can be wrong.
        val lean = Receipts.bias(lg)
        fun seen(t: Team) = t.elo + lean[t.id]
        val games = IntArray(n)
        val confGames = IntArray(n)
        val confWins = DoubleArray(n)
        for (g in lg.games) {
            if (g.type != GameType.NONCONF && g.type != GameType.CONF && g.type != GameType.EVENT) continue
            games[g.home]++
            games[g.away]++
            if (g.type == GameType.CONF) {
                val p = Rankings.shift(Rankings.winProbFor(lg, g, g.home), lean[g.home] - lean[g.away])
                confWins[g.home] += p
                confWins[g.away] += 1 - p
                confGames[g.home]++
                confGames[g.away]++
            }
        }
        for (ev in lg.events) for (id in ev.teams) games[id] += 2
        val byElo = lg.teams.sortedByDescending { seen(it) }
        val nat = HashMap<Int, Int>()
        byElo.forEachIndexed { i, t -> nat[t.id] = i + 1 }
        val (odds, bids, mean) = simulate(lg, lean)
        // Predicted finish: projected conference wins (the simulations agree), then the better team.
        val confRank = HashMap<Int, Int>()
        for (c in lg.confs) {
            c.teamIds.map { lg.team(it) }.sortedWith(compareByDescending<Team> { confWins[it.id].roundToInt() }.thenByDescending { odds.getValue(it.id).conf }.thenByDescending { seen(it) })
                .forEachIndexed { i, t -> confRank[t.id] = i + 1 }
        }
        // Projected records: the simulations' average, so they agree with the odds and win ranges.
        val proj = lg.teams.associate { t ->
            val w = mean[t.id].roundToInt().coerceIn(0, games[t.id])
            val cw = confWins[t.id].roundToInt().coerceIn(0, confGames[t.id])
            t.id to Proj(nat.getValue(t.id), confRank[t.id] ?: 0, w, games[t.id] - w, cw, confGames[t.id] - cw)
        }

        val active = lg.players.filter { it.teamId >= 0 }
        // All-America with positional balance on the first team, then best available.
        val allAmerica = allAmericaTeams(active, ::value)
        val freshmen = active.filter { it.year == 1 && it.recruitRank > 0 }.sortedByDescending { it.ovr }.take(10).map { it.id }
        val transfers = active.filter { p -> p.career.isNotEmpty() && p.career.last().teamId >= 0 && p.career.last().teamId != p.teamId }
            .sortedByDescending { it.ovr }.take(10).map { it.id }
        val breakouts = active.filter { it.year in 2..3 && it.pot - it.ovr >= 8 && it.ovr in 68..82 && it.id !in allAmerica }
            .sortedByDescending { it.pot * 2 + it.ovr }.take(6).map { it.id }
        val underRadar = active.filter { lg.conf(lg.team(it.teamId).confId).tier >= 1 && it.id !in allAmerica }
            .sortedByDescending { value(it) }.take(6).map { it.id }
        val dpoy = active.maxByOrNull { Ratings.defense(it) * 1.4 + it.ovr * 0.15 }?.id ?: -1
        val confPoy = HashMap<Int, Int>()
        for (c in lg.confs) {
            c.teamIds.flatMap { lg.team(it).roster }.map { lg.player(it) }.maxByOrNull { value(it) }?.let { confPoy[c.id] = it.id }
        }
        // Champion favorite, two other blue bloods, and one darkhorse from outside the top 8.
        val finalFour = finalFourPicks(byElo)
        val sleeper = byElo.drop(20).take(40).firstOrNull { lg.conf(it.confId).tier <= 1 && it.id !in finalFour }?.id
            ?: byElo.drop(25).firstOrNull { it.id !in finalFour }?.id ?: byElo[25].id
        val cinderella = lg.teams.filter { lg.conf(it.confId).tier >= 2 && it.id !in finalFour && it.id != sleeper }
            .maxByOrNull { odds.getValue(it.id).s16 * 3 + odds.getValue(it.id).ncaa }?.id ?: sleeper
        // Coach of the Year pick: the biggest projected jump from last season.
        val coty = lg.teams.filter { it.history.isNotEmpty() && nat.getValue(it.id) <= 60 }
            .maxByOrNull { proj.getValue(it.id).w - it.history.last().w }?.id ?: byElo[0].id
        // Games to circle: two highly ranked teams, or a rivalry.
        val marquee = lg.games.filter { it.day <= Schedule.REG_DAYS && it.type != GameType.EVENT }
            .map { g ->
                val a = nat.getValue(g.home)
                val b = nat.getValue(g.away)
                g to (maxOf(0, 60 - a) + maxOf(0, 60 - b) + (if (lg.rivalry(g) != null) 25 else 0) - if (a > 60 || b > 60) 40 else 0)
            }
            .sortedByDescending { it.second }.take(10).map { it.first }.sortedBy { it.day }.map { it.id }
        // Hot seat: proud programs coming off a season without the NCAA Tournament.
        val hotSeat = lg.teams.filter { t ->
            val last = t.history.lastOrNull()
            t.id != lg.userTeamId && t.prestige >= 58 && last != null && last.result !in NCAA_RESULTS &&
                lg.coachOf(t).history.count { it.teamId == t.id } >= 2
        }.sortedByDescending { it.prestige }.take(5).map { it.id }
        // Player of the Year odds: the best players on the best teams.
        val poyScores = active.sortedByDescending { value(it) }.take(14).map { p ->
            p to value(p) + (seen(lg.team(p.teamId)) - 1500) / 45.0 + (p.career.lastOrNull()?.s?.ppg ?: 0.0) * 0.15
        }
        val top = poyScores.maxOfOrNull { it.second } ?: 0.0
        val weights = poyScores.map { it.first to kotlin.math.exp((it.second - top) / 3.5) }
        val sum = weights.sumOf { it.second }.coerceAtLeast(1e-9)
        val poy = weights.map { PoyOdds(it.first.id, it.second / sum) }.sortedByDescending { it.p }.take(8)

        val base = Preview(
            lg.season, proj, allAmerica, freshmen, transfers, breakouts, confPoy, finalFour, sleeper,
            version = 3, underRadar = underRadar, dpoy = dpoy, cinderella = cinderella, cotyTeam = coty,
            marquee = marquee, hotSeat = hotSeat, odds = odds, bids = bids, poy = poy,
        )
        val stories = storylines(lg, base)
        val (cover, style) = Features.collegeCover(lg, base, stories)
        val calls = boldPredictions(lg, base)
        val debates = PreviewDebates.buildCollege(lg, base)
        return Preview(
            base.season, base.proj, base.allAmerica, base.freshmen, base.transfers, base.breakouts, base.confPoy, base.finalFour, base.sleeper,
            3, base.underRadar, base.dpoy, base.cinderella, base.cotyTeam, base.marquee, base.hotSeat, calls.first,
            calls = calls.second, odds = odds, bids = bids, stories = stories, poy = poy, headline = cover?.head ?: coverHeadline(lg, base),
            features = Features.college(lg, base), cover = cover, coverStyle = style, debates = debates,
        )
    }

    /** How the magazine rates a player: his overall, plus a little for last season's scoring. */
    fun value(p: Player) = p.ovr + (p.career.lastOrNull()?.s?.ppg ?: 0.0) * 0.25

    private val NCAA_RESULTS = setOf("National Champion", "Runner-Up", "Final Four", "Elite Eight", "Sweet 16", "Round of 32", "Round of 64", "First Four")

    /** First team prefers one player per position; second and third fill with the next-best available. */
    private fun allAmericaTeams(active: List<Player>, value: (Player) -> Double): List<Int> {
        val pool = active.sortedByDescending(value).toMutableList()
        val first = mutableListOf<Player>()
        for (pos in Pos.entries) {
            val pick = pool.firstOrNull { it.pos == pos } ?: continue
            first += pick
            pool.remove(pick)
        }
        while (first.size < 5 && pool.isNotEmpty()) first += pool.removeAt(0)
        val rest = pool.take(10)
        return (first + rest).map { it.id }
    }

    /** Favorite, two contenders from the top 8, and one darkhorse from 9–20 so the page isn't just the Top 4. */
    private fun finalFourPicks(byElo: List<Team>): List<Int> {
        val champ = byElo.first().id
        val contenders = byElo.drop(1).take(7).shuffled(Rng.r).take(2).map { it.id }
        val dark = byElo.drop(8).take(12).shuffled(Rng.r).first().id
        return listOf(champ) + contenders + dark
    }

    // ---------- the simulations ----------

    /** Bracket order for a field of [size] (a power of two): 1 v 16, 8 v 9, and so on. */
    private fun seedOrder(size: Int): IntArray {
        var order = intArrayOf(0)
        while (order.size < size) {
            val m = order.size * 2
            order = order.flatMap { listOf(it, m - 1 - it) }.toIntArray()
        }
        return order
    }

    /**
     * Plays the season [SIMS] times from each game's win probability: the regular season, the
     * conference tournaments (top eight), a 68-team field (conference winners plus the best
     * at-large résumés) and the bracket. Returns every team's odds and each conference's bids.
     */
    private fun simulate(lg: League, lean: DoubleArray): Triple<Map<Int, Odds>, Map<Int, Double>, DoubleArray> {
        val n = lg.teams.size
        val rnd = kotlin.random.Random(lg.season * 7919L + n * 31L)
        val elo = DoubleArray(n) { lg.team(it).elo + lean[it] }
        val reg = lg.games.filter { it.type == GameType.NONCONF || it.type == GameType.CONF || it.type == GameType.EVENT }
        val home = IntArray(reg.size) { reg[it].home }
        val away = IntArray(reg.size) { reg[it].away }
        val isConf = BooleanArray(reg.size) { reg[it].type == GameType.CONF }
        val pHome = DoubleArray(reg.size) { i ->
            val g = reg[i]
            if (g.played) (if (g.hs > g.aws) 1.0 else 0.0) else Rankings.shift(Rankings.winProbFor(lg, g, g.home), lean[g.home] - lean[g.away])
        }
        val mean = DoubleArray(n)
        for (i in reg.indices) { mean[home[i]] += pHome[i]; mean[away[i]] += 1 - pHome[i] }
        // Early-season tournament games not on the schedule yet: two more, against the event's field.
        val extra = IntArray(n)
        val pExtra = DoubleArray(n)
        for (ev in lg.events) {
            val avg = ev.teams.map { elo[it] }.average()
            for (id in ev.teams) { extra[id] += 2; pExtra[id] = Rankings.winProb(elo[id], avg, 0) }
        }
        for (t in 0 until n) mean[t] += extra[t] * pExtra[t]
        fun play(a: Int, b: Int) = if (rnd.nextDouble() < Rankings.winProb(elo[a], elo[b], 0)) a else b
        fun knockout(seeded: List<Int>): Int {
            var size = 1
            while (size * 2 <= seeded.size) size *= 2
            var round = seedOrder(size).map { seeded[it] }
            while (round.size > 1) round = round.chunked(2).map { (a, b) -> play(a, b) }
            return round[0]
        }
        val maxGames = 64
        val hist = Array(n) { IntArray(maxGames) }
        val confC = IntArray(n); val ncaaC = IntArray(n); val s16C = IntArray(n); val f4C = IntArray(n); val titleC = IntArray(n)
        val bidC = DoubleArray(lg.confs.size)
        val wins = IntArray(n)
        val cwins = IntArray(n)
        val score = DoubleArray(n)
        val order16 = seedOrder(16)
        // Each simulated season every team gets its form for the year (see Rankings.SEASON_FORM_SD), less as the season goes on.
        val base = elo.copyOf()
        val left = reg.count { !it.played }.toDouble() / reg.size.coerceAtLeast(1)
        val sd = Rankings.SEASON_FORM_SD * kotlin.math.sqrt(left)
        val form = DoubleArray(n)
        repeat(SIMS) {
            for (t in 0 until n) { form[t] = Rankings.gauss(rnd) * sd; elo[t] = base[t] + form[t] }
            wins.fill(0); cwins.fill(0)
            for (i in reg.indices) {
                val w = if (rnd.nextDouble() < Rankings.shift(pHome[i], form[home[i]] - form[away[i]])) home[i] else away[i]
                wins[w]++
                if (isConf[i]) cwins[w]++
            }
            for (t in 0 until n) repeat(extra[t]) { if (rnd.nextDouble() < Rankings.shift(pExtra[t], form[t])) wins[t]++ }
            for (t in 0 until n) {
                hist[t][wins[t].coerceAtMost(maxGames - 1)]++
                // A team that beats its projection moves up the committee's board.
                score[t] = elo[t] + 18.0 * (wins[t] - mean[t])
            }
            // Conference races and tournaments: the winner takes the automatic bid.
            val autos = mutableListOf<Int>()
            for (c in lg.confs) {
                if (c.teamIds.isEmpty()) continue
                val standings = c.teamIds.map { it to cwins[it] + rnd.nextDouble() * 0.9 }.sortedByDescending { it.second }.map { it.first }
                confC[standings[0]]++
                autos += if (standings.size >= 2) knockout(standings.take(8)) else standings[0]
            }
            val autoSet = autos.toHashSet()
            val atLarge = (0 until n).filter { it !in autoSet }.sortedByDescending { score[it] }.take((68 - autos.size).coerceAtLeast(0))
            val field = autos + atLarge
            for (t in field) { ncaaC[t]++; bidC[lg.team(t).confId] += 1.0 }
            // First Four: the last four at-large teams, and the four weakest conference winners.
            val out = HashSet<Int>()
            if (field.size >= 68) {
                val lastIn = atLarge.takeLast(4)
                val weakAutos = autos.sortedBy { score[it] }.take(4)
                for (pair in listOf(lastIn, weakAutos)) for ((a, b) in pair.chunked(2).filter { it.size == 2 }) out += if (play(a, b) == a) b else a
            }
            val sixtyFour = field.filter { it !in out }.sortedByDescending { score[it] }.take(64)
            if (sixtyFour.size < 64) return@repeat
            // Seed lines of four, snaked across the four regions.
            val regions = Array(4) { IntArray(16) }
            for (line in 0 until 16) for (k in 0 until 4) {
                val r = if (line % 2 == 0) k else 3 - k
                regions[r][line] = sixtyFour[line * 4 + k]
            }
            val f4 = IntArray(4)
            for (r in 0 until 4) {
                var round = order16.map { regions[r][it] }
                var size = 16
                while (round.size > 1) {
                    round = round.chunked(2).map { (a, b) -> play(a, b) }
                    size /= 2
                    if (size == 4) for (t in round) s16C[t]++
                }
                f4[r] = round[0]
                f4C[round[0]]++
            }
            titleC[play(play(f4[0], f4[3]), play(f4[1], f4[2]))]++
        }
        fun percentile(t: Int, q: Double): Int {
            val target = SIMS * q
            var acc = 0
            for (w in 0 until maxGames) { acc += hist[t][w]; if (acc >= target) return w }
            return maxGames - 1
        }
        val sims = SIMS.toDouble()
        val odds = (0 until n).associateWith { t ->
            Odds(confC[t] / sims, ncaaC[t] / sims, s16C[t] / sims, f4C[t] / sims, titleC[t] / sims, percentile(t, 0.1), percentile(t, 0.9))
        }
        val bids = lg.confs.associate { it.id to bidC[it.id] / sims }
        val avgWins = DoubleArray(n) { t -> (0 until maxGames).sumOf { it * hist[t][it] } / sims }
        return Triple(odds, bids, avgWins)
    }

    // ---------- the writing ----------

    /** Odds as the magazine writes them. */
    fun pct(v: Double): String = when {
        v >= 0.995 -> "99%"
        v < 0.005 -> "under 1%"
        else -> "${(v * 100).roundToInt()}%"
    }

    /** The same pick every time this issue is opened, a different one each season. */
    private fun <T> pick(lg: League, salt: Int, vararg options: T): T =
        options[kotlin.random.Random(lg.season * 1_000_003L + salt).nextInt(options.size)]

    /** Whether the league has a last season to talk about (a new league's first season doesn't). */
    fun hasLastSeason(lg: League): Boolean = lg.players.any { p -> p.career.any { it.season == lg.season - 1 && it.cls != PRO_YEAR && it.s.gp > 0 } }

    /** Returning starters (out of five) and points a game back from last season. */
    fun returning(lg: League, t: Team): Pair<Int, Double> {
        val back = lg.rosterOf(t).mapNotNull { p -> p.career.lastOrNull()?.takeIf { it.teamId == t.id && it.season == lg.season - 1 } }
        return back.count { it.s.gp > 0 && it.s.gs * 2 >= it.s.gp }.coerceAtMost(5) to back.sumOf { it.s.ppg }
    }

    /** Teams that did best in the transfer portal, with their new players. */
    fun portalWinners(lg: League): List<Pair<Team, List<Player>>> =
        lg.teams.map { t -> t to lg.rosterOf(t).filter { p -> p.career.isNotEmpty() && p.career.last().teamId >= 0 && p.career.last().teamId != t.id } }
            .filter { it.second.isNotEmpty() }.sortedByDescending { (_, ps) -> ps.sumOf { it.ovr } + ps.maxOf { it.ovr } * 2 }.take(5)
            .map { (t, ps) -> t to ps.sortedByDescending { it.ovr } }

    fun cameFrom(lg: League, p: Player): String = p.career.lastOrNull()?.let { l -> l.teamId.takeIf { it >= 0 }?.let { lg.team(it).school } ?: l.team } ?: ""

    /** A team whose coach is in his first season there. */
    fun newCoach(lg: League, t: Team): Boolean = t.history.isNotEmpty() && lg.coachOf(t).history.none { it.teamId == t.id }

    private fun lastFor(lg: League, t: Team) = t.history.lastOrNull()?.takeIf { it.season == lg.season - 1 }

    private fun posName(p: Pos) = when (p) { Pos.PG -> "point guard"; Pos.SG -> "shooting guard"; Pos.SF -> "small forward"; Pos.PF -> "power forward"; Pos.C -> "center" }

    private fun ranked(lg: League, pv: Preview) = pv.proj.entries.sortedBy { it.value.nat }.map { lg.team(it.key) }

    private fun odds(pv: Preview, t: Team) = pv.odds[t.id] ?: Odds(0.0, 0.0, 0.0, 0.0, 0.0, 0, 0)

    /** The cover's big line. */
    private fun coverHeadline(lg: League, pv: Preview): String {
        val top = ranked(lg, pv)[0]
        val o = odds(pv, top)
        val champ = CollegeHistory.lastChampion(lg)?.id
        return when {
            champ == top.id && lg.teams.getOrNull(top.id)?.let { TitleDefense.streak(lg, it, lg.season - 1) >= 2 } == true -> pick(lg, 1, "Three-peat?", "Can anyone stop them?", "The dynasty question")
            champ == top.id -> pick(lg, 1, "Repeat?", "Run it back", "Two in a row?")
            o.title < 0.12 -> pick(lg, 2, "Wide open", "Anyone's year", "Up for grabs")
            else -> pick(lg, 3, "The team to beat", "Chasing ${top.school}", "${top.school} on top", "Built for March")
        }
    }

    /** The season's storylines, the biggest first; the user's team (and player) always make it. */
    fun storylines(lg: League, pv: Preview): List<Story> {
        val ranked = ranked(lg, pv)
        val out = mutableListOf<Story>()
        val u = lg.user
        fun proj(t: Team) = pv.proj.getValue(t.id)
        val known = hasLastSeason(lg)
        fun backLine(t: Team): String {
            if (!known) return ""
            val (st, ppg) = returning(lg, t)
            return "It brings back $st of 5 starters and ${"%.1f".format(ppg)} points a game from last season."
        }

        // 1. The favorite, or a wide-open year.
        val top = ranked[0]
        val to = odds(pv, top)
        val contenders = ranked.count { odds(pv, it).title >= 0.05 }
        val topStar = star(lg, top)
        val lastChamp = CollegeHistory.lastChampion(lg)
        val repeat = if (lastChamp?.id == top.id) "The defending champions start where they finished. " else ""
        out += if (to.title >= 0.12) Story(
            "The favorite",
            pick(lg, 10, "${top.school} is the team to beat", "Everyone is chasing ${top.school}", "${top.school}'s title to lose"),
            repeat + "${top.school} wins the national title in ${pct(to.title)} of our simulations and reaches the Final Four in ${pct(to.f4)}; " +
                "${ranked[1].school} is next at ${pct(odds(pv, ranked[1]).title)}. We project ${proj(top).w}-${proj(top).l}. " + Takes.college(lg, pv, top, 2, from = 1),
            top.id,
        ) else Story(
            "Wide open",
            pick(lg, 11, "No team stands above the rest", "It's anyone's year", "A season without a favorite"),
            repeat + "Our No. 1, ${top.school}, wins it all in only ${pct(to.title)} of our simulations, and $contenders teams have at least a 5% shot. ${ranked[1].school} and ${ranked[2].school} are right there.",
            top.id,
        )

        collegeReport(lg, pv)?.let { out += it }

        // 2. The user's player and team.
        val hero = lg.playerOrNull(lg.heroId)?.takeIf { lg.playerMode && it.teamId >= 0 }
        if (hero != null) {
            val ht = lg.team(hero.teamId)
            val last = hero.career.lastOrNull()?.takeIf { it.season == lg.season - 1 }
            val aaTeam = pv.allAmerica.indexOf(hero.id).takeIf { it >= 0 }?.let { listOf("first", "second", "third")[it / 5] }
            val posRank = lg.players.filter { it.teamId >= 0 && it.pos == hero.pos }.sortedByDescending { value(it) }.indexOfFirst { it.id == hero.id } + 1
            out += Story(
                "Spotlight",
                pick(lg, 20, "All eyes on ${hero.name}", "Year ${hero.year} for ${hero.name}", "${hero.name}'s moment"),
                buildString {
                    append(if (last != null) "${hero.name} averaged ${"%.1f".format(last.s.ppg)} points, ${"%.1f".format(last.s.rpg)} rebounds and ${"%.1f".format(last.s.apg)} assists last season. "
                    else "${hero.name} makes his college debut at ${ht.school}. ")
                    append("We rank him No. $posRank among the nation's ${posName(hero.pos)}s")
                    append(if (aaTeam != null) ", and he's on our preseason All-America $aaTeam team." else ".")
                },
                ht.id, hero.id,
            )
        }
        val uo = odds(pv, u)
        val up = proj(u)
        val head = when {
            up.nat <= 10 -> pick(lg, 30, "${u.school} has title hopes", "Big expectations at ${u.school}", "${u.school} is built to win now")
            up.nat <= 25 -> pick(lg, 31, "${u.school} aims for the second weekend", "${u.school} is ranked, now what?")
            uo.ncaa >= 0.5 -> pick(lg, 32, "${u.school} should go dancing", "March is the goal at ${u.school}")
            uo.ncaa >= 0.15 -> pick(lg, 33, "${u.school} lives on the bubble", "Every game counts at ${u.school}")
            else -> pick(lg, 34, "A building year at ${u.school}", "${u.school} is playing for the future")
        }
        out += Story(
            if (lg.playerMode) "Your team" else "Your program", head,
            "We have ${u.school} at No. ${up.nat}, ${ordinal(up.confRank)} in the ${lg.conf(u.confId).abbr} and ${up.w}-${up.l} (${uo.lo} to ${uo.hi} wins). " +
                "They make the NCAA Tournament ${NbaPreviews.often(uo.ncaa)}" +
                (if (uo.s16 >= 0.05) ", reach the Sweet 16 ${NbaPreviews.often(uo.s16)}" else "") +
                (if (uo.conf >= 0.02) " and win the ${lg.conf(u.confId).abbr} ${NbaPreviews.often(uo.conf)}." else ", and a league title would be a surprise.") +
                " " + (if (u.id == top.id) backLine(u) else Takes.college(lg, pv, u, 2, from = 1)) +
                (if (!lg.playerMode) " " + when {
                    lg.userCoach.security >= 75 -> "Coach ${lg.userCoach.last} has all the job security he needs."
                    lg.userCoach.security >= 45 -> "Coach ${lg.userCoach.last} is safe, but the boosters expect progress."
                    else -> "Coach ${lg.userCoach.last} needs a good year: his job security is ${lg.userCoach.security}/100."
                } else ""),
            u.id,
        )

        // 3. The defending champion.
        lastChamp?.takeIf { it.id != top.id }?.let { c ->
            val r = proj(c).nat
            val co = odds(pv, c)
            out += Story(
                "The champs",
                when {
                    r <= 4 -> pick(lg, 40, "Can ${c.school} repeat?", "${c.school} goes for two", "Back-to-back for ${c.school}?")
                    r <= 25 -> pick(lg, 41, "${c.school} defends its crown", "The champs reload at ${c.school}")
                    else -> pick(lg, 42, "${c.school} starts over", "Life after the title at ${c.school}")
                },
                "${backLine(c)} We have the defending champions at No. $r, with ${NbaPreviews.chance(co.title)} to win it all again.".trim(),
                c.id,
            )
        }

        // History: a dynasty in this league, a contender chasing its first title, a blue blood's long wait.
        run {
            val recent = lg.history.filter { it.season >= lg.season - 4 }.mapNotNull { h -> h.champion.takeIf { it >= 0 }?.let { h.season to it } }
            val dynasty = recent.groupBy { it.second }.maxByOrNull { it.value.size }?.takeIf { it.value.size >= 2 }
            val firsts = ranked.take(5).filter { CollegeHistory.titles(lg, it).isEmpty() && odds(pv, it).title >= 0.04 }
            val waits = ranked.take(12).map { it to CollegeHistory.titles(lg, it) }.filter { (_, ts) -> ts.size >= 2 && lg.season - ts.last() >= 20 }
            val bluest = ranked.take(15).map { it to CollegeHistory.titles(lg, it) }.filter { it.second.size >= 4 }.maxByOrNull { it.second.size }
            when {
                dynasty != null -> {
                    val t = lg.team(dynasty.key)
                    val years = dynasty.value.map { it.first }.sorted()
                    out += Story("History", pick(lg, 160, "A ${t.school} dynasty?", "${t.school} is building something", "Can anyone stop ${t.school}?"),
                        "${t.school} won it all in ${years.joinToString(" and ")}: ${CollegeHistory.count(CollegeHistory.titles(lg, t).size)} in all now. " +
                            "We have them No. ${proj(t).nat}, with ${NbaPreviews.chance(odds(pv, t).title)} at another.", t.id)
                }
                firsts.isNotEmpty() -> {
                    val t = firsts.first()
                    out += Story("History", pick(lg, 161, "${t.school} chases its first title", "No banner yet at ${t.school}", "Is this ${t.school}'s year?"),
                        "${t.school} has never won a national title. This team is good enough to: No. ${proj(t).nat} in our poll, the Final Four ${NbaPreviews.often(odds(pv, t).f4)} " +
                            "and a title ${NbaPreviews.often(odds(pv, t).title)} in our simulations." +
                            (bluest?.takeIf { it.first.id != t.id }?.let { (b, ts) -> " The blue bloods are waiting: ${b.school} has ${CollegeHistory.count(ts.size)} and is No. ${proj(b).nat}." } ?: ""),
                        t.id)
                }
                waits.isNotEmpty() -> {
                    val (t, ts) = waits.maxBy { (_, x) -> lg.season - x.last() }
                    out += Story("History", pick(lg, 162, "${lg.season - ts.last()} years and counting at ${t.school}", "${t.school} wants the old days back"),
                        "${CollegeHistory.line(lg, t)} We pick them No. ${proj(t).nat}, with the Final Four ${NbaPreviews.often(odds(pv, t).f4)}.", t.id)
                }
                bluest != null -> {
                    val (t, ts) = bluest
                    out += Story("History", pick(lg, 163, "Blue blood: ${t.school}", "${t.school} and its banners"),
                        "${CollegeHistory.line(lg, t)} Banner No. ${ts.size + 1}? We give them ${NbaPreviews.chance(odds(pv, t).title)} from No. ${proj(t).nat}.", t.id)
                }
            }
        }

        // 4. The closest race in a power conference.
        lg.confs.filter { it.tier == 0 }.mapNotNull { c ->
            val two = c.teamIds.map { lg.team(it) }.sortedByDescending { odds(pv, it).conf }.take(2)
            if (two.size < 2) null else Triple(c, two, odds(pv, two[1]).conf / odds(pv, two[0]).conf.coerceAtLeast(0.01))
        }.filter { odds(pv, it.second[0]).conf >= 0.2 }.maxByOrNull { it.third }?.takeIf { it.third >= 0.6 }?.let { (c, two, _) ->
            val (a, b) = two
            val days = lg.games.filter { it.type == GameType.CONF && it.involves(a.id) && it.involves(b.id) }.sortedBy { it.day }
                .map { g -> "${if (g.home == a.id) "at ${a.school}" else "at ${b.school}"} (game ${g.day})" }
            out += Story(
                "Conference race",
                pick(lg, 50, "The ${c.abbr} is a coin flip", "A two-team race in the ${c.abbr}", "${a.school} or ${b.school}?"),
                "${a.school} wins the ${c.abbr} in ${pct(odds(pv, a).conf)} of our simulations, ${b.school} in ${pct(odds(pv, b).conf)}." +
                    (if (days.isNotEmpty()) " They meet ${days.joinToString(" and ")}." else "") +
                    " ${Takes.college(lg, pv, if (a.id == top.id) b else a, 1, from = 1)}",
                a.id,
            )
        }

        // 4b. Experience against talent: the ranked team that brings everyone back, and the one reloading.
        if (known) {
            val top25 = ranked.take(25).mapNotNull { t -> Analysis.turnover(lg, t)?.let { t to it } }
            top25.maxByOrNull { it.second.returning }?.takeIf { it.second.returning >= 0.7 }?.let { (t, tv) ->
                out += Story("Experience", pick(lg, 310, "${t.school} brings everybody back", "The most experienced contender", "Old heads at ${t.school}"),
                    "${t.school} returns ${pct(tv.returning)} of last season's minutes, the most of any team in our Top 25" +
                        " (${"%.1f".format(tv.backPpg)} points a game back). Experience wins in March: we give them the Final Four ${NbaPreviews.often(odds(pv, t).f4)}.",
                    t.id)
            }
            top25.filter { proj(it.first).nat <= 12 }.minByOrNull { it.second.returning }?.takeIf { it.second.returning <= 0.45 }?.let { (t, tv) ->
                val newbies = tv.arrivals.sortedByDescending { it.ovr }.take(3)
                out += Story("Reloading", pick(lg, 311, "${t.school} doesn't rebuild; it reloads", "A new team at ${t.school}, same expectations", "Talent over experience"),
                    "Only ${pct(tv.returning)} of ${t.school}'s minutes are back, yet we still have them No. ${proj(t).nat}. " +
                        (if (newbies.isNotEmpty()) "The newcomers carry it: ${newbies.joinToString(", ") { "${it.name} (${if (isTransfer(it)) "transfer from ${cameFrom(lg, it)}" else it.cls})" }}. " else "") +
                        "Talent like that usually figures it out by February. The question is November.",
                    t.id, newbies.firstOrNull()?.id ?: -1)
            }
        }
        // 4c. On paper: the best offense and the best defense in the Top 25.
        run {
            val top = ranked.take(25)
            val bestO = top.maxByOrNull { Analysis.offense(lg, it) }
            val bestD = top.maxByOrNull { Analysis.defense(lg, it) }
            if (bestO != null && bestD != null && bestO.id != bestD.id) out += Story("On paper", pick(lg, 312, "Offense vs. defense", "Points or stops?", "Two ways to win"),
                "${bestO.school} has the best offense in our Top 25 on paper; its defense ranks ${ordinal(Analysis.rank(top, bestO) { Analysis.defense(lg, it) })}. " +
                    "${bestD.school} is the opposite: the best defense, the ${ordinal(Analysis.rank(top, bestD) { Analysis.offense(lg, it) })}-ranked offense. " +
                    "History says defense travels in March: we give ${bestD.school} the Final Four ${NbaPreviews.often(odds(pv, bestD).f4)}, ${bestO.school} ${NbaPreviews.often(odds(pv, bestO).f4)}.",
                bestD.id)
        }
        // 5. Risers and fallers.
        ranked.take(20).filter { t -> lastFor(lg, t)?.let { it.ap == 0 } == true }
            .maxByOrNull { proj(it).w - lastFor(lg, it)!!.w }?.let { t ->
                val last = lastFor(lg, t)!!
                val nc = newcomer(lg, t)
                out += Story(
                    "On the rise",
                    pick(lg, 60, "${t.school} crashes the party", "From unranked to No. ${proj(t).nat}", "${t.school} is for real"),
                    "Unranked at the end of last season after going ${last.w}-${last.l}, ${t.school} opens at No. ${proj(t).nat}." +
                        (nc?.let { " ${it.name} (${if (isTransfer(it)) "from ${cameFrom(lg, it)}" else "${it.stars}-star freshman"}) is the big addition." } ?: "") +
                        " " + backLine(t),
                    t.id,
                )
            }
        lg.teams.filter { t -> lastFor(lg, t)?.ap?.let { it in 1..12 } == true && proj(t).nat > 25 }
            .minByOrNull { lastFor(lg, it)!!.ap }?.let { t ->
                val last = lastFor(lg, t)!!
                out += Story(
                    "Starting over",
                    pick(lg, 70, "What happened to ${t.school}?", "${t.school} has holes to fill", "A reset at ${t.school}"),
                    "No. ${last.ap} at the end of last season (${last.w}-${last.l}), ${t.school} opens at No. ${proj(t).nat}. ${backLine(t)}",
                    t.id,
                )
            }

        // 6. The portal and the freshmen.
        portalWinners(lg).firstOrNull()?.let { (t, ps) ->
            out += Story(
                "Transfer portal",
                pick(lg, 80, "${t.school} wins the portal", "${t.school} shopped well", "New faces at ${t.school}"),
                ps.take(3).joinToString("; ") { "${it.name} (${it.posLabel}) from ${cameFrom(lg, it)}" } + ". " +
                    if (proj(t).nat <= 25) "They open at No. ${proj(t).nat}." else "We pick them ${ordinal(proj(t).confRank)} in the ${lg.conf(t.confId).abbr}.",
                t.id, ps.first().id,
            )
        }
        lg.playerOrNull(pv.freshmen.firstOrNull() ?: -1)?.takeIf { it.teamId >= 0 }?.let { f ->
            val t = lg.team(f.teamId)
            val board = Offseason.draftBoard(lg)[f.id]
            out += Story(
                "Freshman phenom",
                pick(lg, 90, "Meet ${f.name}", "${f.last} arrives at ${t.school}", "The ${f.name} show"),
                "The No. ${f.recruitRank} recruit in the country, a ${f.stars}-star ${posName(f.pos)} from ${f.hometown}. " +
                    (if (f.ovr >= 75) "${f.last} should start from day one at ${t.school}" else "${f.last} has a big ceiling at ${t.school}") +
                    (if (board != null && board <= 14) ", and the early mock drafts have him in the lottery." else "."),
                t.id, f.id,
            )
        }

        // 7. The sidelines.
        lg.teams.filter { it.id != u.id && newCoach(lg, it) }.maxByOrNull { it.prestige }?.takeIf { it.prestige >= 55 }?.let { t ->
            val c = lg.coachOf(t)
            val last = lastFor(lg, t)
            out += Story(
                "New era",
                pick(lg, 100, "A new era at ${t.school}", "${c.last} takes over at ${t.school}", "${t.school} turns the page"),
                "${c.name} inherits a program that went ${last?.let { "${it.w}-${it.l} and ${lastSeason(it.result)}" } ?: "through a rough year"}. " +
                    "We pick his first team ${ordinal(proj(t).confRank)} in the ${lg.conf(t.confId).abbr}.",
                t.id,
            )
        }
        pv.hotSeat.firstOrNull()?.let { lg.team(it) }?.let { t ->
            val c = lg.coachOf(t)
            val dry = t.history.reversed().takeWhile { it.result !in NCAA_RESULTS }.size
            out += Story(
                "Hot seat",
                pick(lg, 110, "${c.name} needs a big year", "Pressure builds at ${t.school}", "March or bust for ${c.last}"),
                "${t.school} has missed the NCAA Tournament ${if (dry >= 2) "$dry straight seasons" else "last season"}. " +
                    "We project ${proj(t).w}-${proj(t).l} and give them ${NbaPreviews.chance(odds(pv, t).ncaa)} to get back.",
                t.id,
            )
        }

        // 8. Players.
        lg.playerOrNull(pv.poy.firstOrNull()?.pid ?: -1)?.takeIf { it.teamId >= 0 }?.let { p ->
            val rest = pv.poy.drop(1).take(2).mapNotNull { o -> lg.playerOrNull(o.pid)?.let { "${it.name} (${pct(o.p)})" } }
            out += Story(
                "Player of the Year",
                pick(lg, 120, "${p.name} leads the Player of the Year race", "${p.last} is the man to catch", "The race starts with ${p.name}"),
                "${p.name} of ${lg.team(p.teamId).school} heads our Player of the Year odds at ${pct(pv.poy[0].p)}" + (if (rest.isNotEmpty()) ", ahead of ${rest.joinToString(" and ")}." else "."),
                p.teamId, p.id,
            )
        }
        lg.players.filter { it.teamId >= 0 && it.year >= 4 && !it.walkOn && it.id != lg.heroId && it.id != pv.poy.firstOrNull()?.pid }.maxByOrNull { value(it) }?.let { p ->
            val last = p.career.lastOrNull()
            out += Story(
                "Last dance",
                pick(lg, 130, "One last ride for ${p.name}", "${p.name} came back for this", "${p.last}'s final chapter"),
                (if (last != null && last.season == lg.season - 1 && last.s.gp > 0) "${p.name} (${p.posLabel}) averaged ${"%.1f".format(last.s.ppg)} points last season at ${lg.team(p.teamId).school}. "
                else "${p.name} (${p.posLabel}) is a senior with one more shot at ${lg.team(p.teamId).school}. ") +
                    "He came back for a senior year, and ${lg.team(p.teamId).school} has ${NbaPreviews.chance(odds(pv, lg.team(p.teamId)).f4)} at the Final Four.",
                p.teamId, p.id,
            )
        }

        // 9. March.
        lg.teams.getOrNull(pv.cinderella)?.let { t ->
            val s = star(lg, t)
            out += Story(
                "Bracket buster",
                pick(lg, 140, "Don't sleep on ${t.school}", "${t.school} could bust brackets", "Remember the name: ${t.school}"),
                "${t.school} of the ${lg.conf(t.confId).abbr} makes the NCAA Tournament in ${pct(odds(pv, t).ncaa)} of our simulations" +
                    (if (odds(pv, t).s16 >= 0.05) " and the Sweet 16 in ${pct(odds(pv, t).s16)}." else ". Nobody will want to see them in the first round.") +
                    (s?.let { " ${it.name} (${it.posLabel}, ${it.cls}) is the reason." } ?: ""),
                t.id,
            )
        }
        // What the numbers say, among the teams that matter (the top 40 and the user's).
        runCatching { val top = pv.proj.entries.sortedBy { it.value.nat }.take(40).map { it.key }.toSet() + lg.userTeamId; Scout.leagueNotes(lg) { it.id in top } }.getOrNull()?.takeIf { it.size >= 3 }?.let { notes ->
            out += Story("By the numbers", pick(lg, 170, "What the numbers say", "Things the polls won't tell you", "Under the hood"),
                notes.joinToString(" ") { (h, b) -> "$h. $b" })
        }
        pv.marquee.mapNotNull { id -> lg.games.firstOrNull { it.id == id } }.firstOrNull { lg.rivalry(it) != null }?.let { g ->
            val a = lg.team(g.away)
            val h = lg.team(g.home)
            out += Story(
                "Rivalry",
                pick(lg, 150, "${a.school} at ${h.school}: circle it", "Bad blood: ${a.school} vs ${h.school}"),
                "${lg.rivalry(g)!!.title.let { if (it.equals("Rivalry", true)) "The rivalry game" else it }} is game ${g.day} of the season: No. ${proj(a).nat} ${a.school} visits No. ${proj(h).nat} ${h.school}" +
                    Rankings.winProbFor(lg, g, h.id).let { p -> if (p in 0.4..0.6) ", and our numbers call it a coin flip." else ", and we like ${if (p > 0.5) h.school else a.school} at ${(maxOf(p, 1 - p) * 100).toInt()}%." },
                h.id,
            )
        }
        return out.take(15)
    }

    /**
     * Last year's issue, graded: our preseason No. 1 against the champion, how many conference champions we
     * called, the biggest conference surprise, our Final Four, and where the Player of the Year started.
     */
    private fun collegeReport(lg: League, pv: Preview): Story? {
        val old = lg.preview?.takeIf { it.season == lg.season - 1 } ?: return null
        val prev = lg.teams.filter { t -> t.history.any { it.season == lg.season - 1 } }
        if (prev.size < 50) return null
        fun lastOf(t: Team) = t.history.lastOrNull { it.season == lg.season - 1 }
        val champ = CollegeHistory.lastChampion(lg)
        val no1 = old.proj.entries.firstOrNull { it.value.nat == 1 }?.let { lg.teams.getOrNull(it.key) }
        // Conference races: our pick for first against who actually finished first in league play.
        var called = 0
        var races = 0
        var surprise: Triple<Team, Int, Int>? = null
        for (c in lg.confs) {
            val teams = c.teamIds.map { lg.team(it) }.filter { lastOf(it) != null && old.proj[it.id] != null }
            if (teams.size < 6) continue
            races++
            val finish = teams.sortedWith(compareByDescending<Team> { lastOf(it)!!.cw - lastOf(it)!!.cl }.thenByDescending { lastOf(it)!!.w })
            val picked = teams.minByOrNull { old.proj.getValue(it.id).confRank }!!
            if (finish.first().id == picked.id) called++
            if (c.tier == 0) finish.forEachIndexed { i, t ->
                val jump = old.proj.getValue(t.id).confRank - (i + 1)
                if (jump > (surprise?.let { it.second - it.third } ?: 3)) surprise = Triple(t, old.proj.getValue(t.id).confRank, i + 1)
            }
        }
        val deep = setOf("Final Four", "Runner-Up", "National Champion")
        val f4hits = old.finalFour.count { id -> lg.teams.getOrNull(id)?.let { lastOf(it)?.result in deep } == true }
        val parts = mutableListOf<String>()
        if (no1 != null) parts += "Last fall our No. 1 was ${no1.school}." + when {
            champ == null -> ""
            champ.id == no1.id -> " They cut down the nets."
            else -> " ${champ.school} won it all instead" + (old.proj[champ.id]?.nat?.let { ", from No. $it in our poll." } ?: ".") + " ${no1.school} " +
                (lastOf(no1)?.let { "went ${it.w}-${it.l} and ${lastSeason(it.result)}." } ?: "fell short.")
        }
        if (races > 0) parts += "We called $called of $races conference champions."
        surprise?.let { (t, was, now) -> parts += "Biggest surprise: ${t.school}, picked ${ordinal(was)} in the ${lg.conf(t.confId).abbr}, finished ${ordinal(now)}." }
        parts += "Our Final Four: $f4hits of 4 got there."
        lg.players.firstOrNull { p -> p.awards.contains("${lg.season - 1} Nat MVP") }?.let { m ->
            val at = old.poy.indexOfFirst { it.pid == m.id }
            parts += if (at == 0) "And we had the Player of the Year, ${m.name}, at the top of our board." else if (at > 0) "The Player of the Year, ${m.name}, started No. ${at + 1} in our odds." else "The Player of the Year, ${m.name}, wasn't in our top eight."
        }
        Receipts.paragraph(old.bold, old.graded, old.outcomes, nextYear = true)?.let { parts += it }
        return Story("Report card", pick(lg, 299, "How we did last year", "Grading last year's picks", "What we got right, and wrong"), parts.joinToString(" "), (champ ?: no1)?.id ?: -1)
    }

    /** Predictions to argue about: the user's team always, plus five more. */
    private fun boldPredictions(lg: League, pv: Preview): Pair<List<String>, List<Call>> {
        val ranked = ranked(lg, pv)
        val pool = mutableListOf<Pair<String, Call>>()
        fun add(text: String, c: Call) { pool += text to c }
        lg.playerOrNull(pv.freshmen.firstOrNull() ?: -1)?.let { f ->
            add("${f.name} wins National Freshman of the Year.", Call("C_AWARD", pid = f.id, n = 2))
        }
        add("${lg.team(pv.cinderella).school} wins two NCAA Tournament games.", Call("C_NCAA_WINS", pv.cinderella, n = 2))
        add("${lg.team(pv.sleeper).school} finishes in the top 25 of the NET.", Call("C_NET", pv.sleeper, n = 25))
        ranked.getOrNull(4)?.let { add("${it.school} loses in the first weekend of March.", Call("C_FIRST_WEEKEND", it.id)) }
        pv.bids.maxByOrNull { it.value }?.let { (c, b) -> add("The ${lg.conf(c).abbr} sends ${b.roundToInt() + 2} teams to the NCAA Tournament.", Call("C_BIDS", c, n = b.roundToInt() + 2)) }
        CollegeHistory.lastChampion(lg)?.let { c -> add("${c.school} doesn't make the Sweet 16.", Call("C_NO_S16", c.id)) }
        lg.teams.filter { lg.conf(it.confId).tier >= 1 }.maxByOrNull { odds(pv, it).f4 }?.let { t -> add("${t.school} crashes the Final Four out of the ${lg.conf(t.confId).abbr}.", Call("C_F4", t.id)) }
        ranked[0].let { t -> if (odds(pv, t).conf < 0.6) add("${t.school} doesn't win the ${lg.conf(t.confId).abbr}.", Call("C_NO_CONF", t.id)) }
        lg.players.filter { it.teamId >= 0 && (it.career.lastOrNull()?.s?.ppg ?: 0.0) >= 17 }.maxByOrNull { it.career.last().s.ppg }?.let { p ->
            add("${p.name} leads the country in scoring at better than 24 points a game.", Call("C_SCORING", pid = p.id, n = 24))
        }
        lg.teams.filter { it.id != lg.userTeamId && newCoach(lg, it) }.maxByOrNull { it.prestige }?.let { t ->
            add("${t.school} makes the NCAA Tournament in ${lg.coachOf(t).last}'s first season.", Call("C_NCAA", t.id))
        }
        val picked = pool.shuffled(kotlin.random.Random(lg.season * 31L + 7)).take(5)
        val u = lg.user
        val mine = pv.proj.getValue(u.id)
        val own = when {
            mine.nat <= 10 -> "${u.school} wins the ${lg.conf(u.confId).abbr} by two games." to Call("C_CONF_BY", u.id, n = 2)
            mine.nat <= 45 -> "${u.school} finishes ahead of where we picked it in the ${lg.conf(u.confId).abbr}." to Call("C_CONF_BEAT", u.id, n = mine.confRank)
            else -> "${u.school} finishes above .500 in the ${lg.conf(u.confId).abbr}." to Call("C_CONF_500", u.id)
        }
        val all = picked + own
        return all.map { it.first } to all.map { it.second }
    }

    /** The editor's letter at the front of the issue. */
    fun editorsLetter(lg: League, pv: Preview): List<String> {
        val ranked = ranked(lg, pv)
        val top = ranked[0]
        val champ = CollegeHistory.lastChampion(lg)
        val u = lg.user
        val mine = pv.proj.getValue(u.id)
        val to = odds(pv, top)
        val out = mutableListOf<String>()
        out += buildString {
            append(pick(lg, 200, "Welcome to the ${pv.season} season. ", "The ${pv.season} season is here. ", "Basketball is back. "))
            append("${top.school} opens at No. 1")
            star(lg, top)?.let { append(pick(lg, 201, ", and ${it.name} is the reason", " behind ${it.name}", ", with ${it.name} leading the way")) }
            append(". We simulated the season $SIMS times: ${top.school} won it all in ${pct(to.title)} of them. ")
            if (champ != null) append(if (champ.id == top.id) "The defending champions are the team to catch." else "Defending champion ${champ.school} starts at No. ${pv.proj.getValue(champ.id).nat}.")
        }
        val ff = pv.finalFour.map { lg.team(it).school }
        out += "Our Final Four: ${ff.dropLast(1).joinToString(", ")} and ${ff.last()}. ${lg.team(pv.sleeper).school} is the team nobody wants to see in March" +
            (pv.stories.firstOrNull { it.kicker == "Freshman phenom" }?.let { s -> lg.playerOrNull(s.pid)?.let { ", and ${it.name} is the freshman everyone will be talking about." } } ?: ".")
        out += "${u.school} comes in at No. ${mine.nat}, picked ${ordinal(mine.confRank)} in the ${lg.conf(u.confId).abbr}, with ${NbaPreviews.chance(odds(pv, u).ncaa)} at the NCAA Tournament. Turn to the team report for the full breakdown."
        return out
    }

    fun ordinal(n: Int): String = "$n" + when { n % 100 in 11..13 -> "th"; n % 10 == 1 -> "st"; n % 10 == 2 -> "nd"; n % 10 == 3 -> "rd"; else -> "th" }

    /** Every area of a team's game and where it ranks nationally (0 = last, 1 = first). */
    fun profile(lg: League, t: Team, peers: List<Team> = lg.teams): List<Pair<String, Double>> {
        fun rotation(team: Team) = lg.rosterOf(team).sortedByDescending { it.ovr }.take(8)
        val areas = listOf<Pair<String, (Team) -> Double>>(
            "Perimeter shooting" to { tm -> rotation(tm).map { (it[Attr.OUT] + it[Attr.MID]) / 2.0 }.average() },
            "Finishing inside" to { tm -> rotation(tm).map { it[Attr.INS].toDouble() }.average() },
            "Ball handling" to { tm -> rotation(tm).map { (it[Attr.HND] + it[Attr.PAS]) / 2.0 }.average() },
            "Rebounding" to { tm -> rotation(tm).map { it[Attr.REB].toDouble() }.average() },
            "Perimeter defense" to { tm -> rotation(tm).map { (it[Attr.PER] + it[Attr.STL]) / 2.0 }.average() },
            "Rim protection" to { tm -> rotation(tm).map { (it[Attr.IDF] + it[Attr.BLK]) / 2.0 }.average() },
            "Depth" to { tm -> lg.rosterOf(tm).sortedByDescending { it.ovr }.drop(5).take(4).map { it.ovr.toDouble() }.average() },
            "Experience" to { tm -> rotation(tm).map { it.year.toDouble() }.average() },
        )
        val group = peers.filter { it.id != t.id }
        return areas.map { (name, f) ->
            val all = group.map { f(it) }
            val mine = f(t)
            name to all.count { it < mine }.toDouble() / all.size.coerceAtLeast(1)
        }
    }

    /** Team strengths and concerns, by where the team ranks nationally in each area. */
    fun scouting(lg: League, t: Team): Pair<List<String>, List<String>> {
        val pct = profile(lg, t).sortedByDescending { it.second }
        return pct.take(2).map { it.first } to pct.takeLast(2).reversed().map { it.first }
    }

    /** Magazine-style scouting sentences built from [scouting]. */
    fun scoutingProse(lg: League, t: Team): Pair<String, String> {
        val (good, bad) = scouting(lg, t)
        fun strengthOf(area: String) = when (area) {
            "Perimeter shooting" -> "they can heat up from deep and punish soft closeouts"
            "Finishing inside" -> "they live at the rim and get to the line"
            "Ball handling" -> "they take care of the ball and create for each other"
            "Rebounding" -> "they own the glass on both ends"
            "Perimeter defense" -> "they can switch and pressure on the perimeter"
            "Rim protection" -> "they erase mistakes at the rim"
            "Depth" -> "they can go nine deep without falling off a cliff"
            else -> "they have a veteran core that knows how to win"
        }
        fun concernOf(area: String) = when (area) {
            "Perimeter shooting" -> "the jump shot has to travel"
            "Finishing inside" -> "they need more punch inside"
            "Ball handling" -> "turnovers could bury them on the road"
            "Rebounding" -> "they can get pushed around on the glass"
            "Perimeter defense" -> "guards will have to prove they can stay in front"
            "Rim protection" -> "the paint is a soft spot"
            "Depth" -> "a short bench leaves little margin for foul trouble"
            else -> "youth will cost them some nights"
        }
        val strength = buildString {
            append(strengthOf(good.first()).replaceFirstChar { it.uppercase() })
            good.getOrNull(1)?.let { append(", and ${strengthOf(it)}") }
            append(".")
        }
        val concern = buildString {
            append("The worry: ")
            append(concernOf(bad.first()))
            bad.getOrNull(1)?.let { append(", and ${concernOf(it)}") }
            append(".")
        }
        return strength to concern
    }

    /** Why a team can win it all, and why it might not. */
    fun case(lg: League, pv: Preview, t: Team): Pair<String, String> = Takes.collegeCase(lg, pv, t)

    /** Three questions the user's team has to answer. */
    fun questions(lg: League, pv: Preview, t: Team): List<String> {
        val (st, ppg) = returning(lg, t)
        val bad = scouting(lg, t).second
        val nc = newcomer(lg, t)
        val out = mutableListOf<String>()
        out += if (!hasLastSeason(lg)) "Who leads this group? Everyone starts from zero, and the pecking order is up for grabs."
        else if (st <= 2) "Who replaces the production? Only $st starters and ${"%.1f".format(ppg)} points a game are back."
        else "Can a veteran group take the next step? $st starters and ${"%.1f".format(ppg)} points a game return."
        out += "Can they fix their ${bad[0].lowercase()}? It's the weakest part of their game."
        if (nc != null) out += "Is ${nc.name} ready? He's the newcomer with the most to give."
        else star(lg, t)?.let { out += "Can ${it.name} carry the load? He's the best player on the roster." }
        return out
    }

    /** This season's issue, built now if the save predates this version of the magazine. */
    fun current(lg: League): Preview {
        val p = lg.preview
        if (p != null && p.season == lg.season && p.version >= 3) return p
        return build(lg).also { it.opened = true; lg.preview = it }
    }

    fun star(lg: League, t: Team): Player? = lg.rosterOf(t).maxByOrNull { it.ovr }

    /** Best freshman or transfer on the roster, other than the headliner. */
    fun newcomer(lg: League, t: Team): Player? {
        val star = star(lg, t)
        return lg.rosterOf(t).filter { isNewcomer(it) && !it.walkOn && (isTransfer(it) || it.recruitRank > 0) && it.id != star?.id }.maxByOrNull { it.ovr }
    }

    /** His last season was at another school. */
    fun isTransfer(p: Player) = p.career.isNotEmpty() && p.career.last().teamId >= 0 && p.career.last().teamId != p.teamId

    /** A freshman, or a transfer whose last season was somewhere else. */
    fun isNewcomer(p: Player) = p.year == 1 || (p.career.isNotEmpty() && p.career.last().teamId >= 0 && p.career.last().teamId != p.teamId)

    fun lastSeason(result: String) = when (result) {
        "National Champion" -> "won the national title"
        "Runner-Up" -> "reached the title game"
        "Final Four" -> "made the Final Four"
        "Elite Eight" -> "reached the Elite Eight"
        "Sweet 16" -> "made the Sweet 16"
        "Round of 32" -> "won a game in the NCAA Tournament"
        "Round of 64", "First Four" -> "lost in its first NCAA Tournament game"
        "NIT Champion" -> "won the NIT"
        else -> if (result.startsWith("NIT")) "went to the NIT" else "missed the NCAA Tournament"
    }

    /** A short write-up: how last season went and who to watch — no raw overalls. */
    fun blurb(lg: League, t: Team): String {
        val last = t.history.lastOrNull()
        val s = star(lg, t)
        val nc = newcomer(lg, t)
        val (good, _) = scouting(lg, t)
        val open = when {
            last == null -> "${t.school} opens a new chapter under ${lg.coachOf(t).name}."
            last.result == "National Champion" -> "The defending champions (${last.w}-${last.l}) are back with unfinished business."
            last.result in setOf("Runner-Up", "Final Four", "Elite Eight") -> "After they ${lastSeason(last.result)} (${last.w}-${last.l}), the bar is set high."
            last.result in NCAA_RESULTS -> "Went ${last.w}-${last.l} and ${lastSeason(last.result)} last March."
            last.w >= 20 -> "Won ${last.w} games a year ago but ${lastSeason(last.result)} — this group wants a different ending."
            else -> "Coming off a ${last.w}-${last.l} year that ${lastSeason(last.result)}."
        }
        val head = s?.let {
            val style = it.style.label.lowercase()
            when {
                it.year >= 4 -> "${it.name}, a senior $style, is the headliner."
                isNewcomer(it) -> "${it.name} arrives as the new face of the program."
                else -> "${it.name} (${it.pos}, ${it.cls}) sets the tone as a $style."
            }
        }
        val add = nc?.let {
            if (it.year == 1 && it.recruitRank > 0) "${it.name}, the ${it.stars}★ signee, is the freshman to watch."
            else "${it.name} is the newcomer who could change the rotation."
        }
        val edge = "The staff hangs its hat on ${good.first().lowercase()}."
        return listOfNotNull(open, head, add, edge).joinToString(" ")
    }

    /** One line for a conference predicted-finish row. */
    fun confLine(lg: League, t: Team, confRank: Int): String {
        val s = star(lg, t) ?: return "A workmanlike group looking for its identity."
        val nc = newcomer(lg, t)
        return when {
            confRank == 1 -> "Our pick to win it, built around ${s.name}."
            confRank == 2 -> "The top challenger if ${s.name} stays healthy."
            confRank <= 4 -> listOfNotNull(
                "${s.name} gives them a chance on any night.",
                nc?.let { "${it.last} is the X-factor." },
            ).joinToString(" ")
            confRank <= lg.conf(t.confId).teamIds.size / 2 -> "Dangerous when ${s.name} gets going; consistency is the question."
            else -> "Rebuilding pieces, with ${s.name} as the bright spot."
        }
    }

    /** Why this player is on the breakouts page. */
    fun breakoutLine(p: Player): String {
        val gap = p.pot - p.ovr
        return when {
            gap >= 12 && p.year == 2 -> "Sophomore with a star's ceiling — the leap could be loud."
            gap >= 12 -> "Huge upside as a ${p.cls.lowercase()} ${p.pos}; the tools are ahead of the production."
            p.year == 2 -> "Ready for a bigger role after a quiet freshman year."
            p.career.lastOrNull()?.let { it.s.ppg in 8.0..14.0 } == true -> "Averaged %.1f points last season and looks primed for more.".format(p.career.last().s.ppg)
            else -> "The staff sees a bigger role — and a bigger number — this winter."
        }
    }

    /** Why a mid-major star is under the radar. */
    fun sleeperLine(lg: League, p: Player): String {
        val t = lg.team(p.teamId)
        val conf = lg.conf(t.confId)
        return "${p.name} would be a household name in a power league; instead he carries ${t.school} in the ${conf.abbr}."
    }

    /** Cover dek that changes with the story. */
    fun coverDek(lg: League, top: Team): String {
        val last = top.history.lastOrNull()
        val s = star(lg, top)
        return when {
            last?.result == "National Champion" -> "Can ${possessive(top.school)} repeat?"
            last?.result in setOf("Runner-Up", "Final Four") -> "From last March to No. 1 — is this the year?"
            s != null && s.year >= 4 -> "${s.name}'s last dance at the top of the poll."
            else -> "Is this ${possessive(top.school)} year?"
        }
    }

    private fun possessive(s: String) = if (s.endsWith("s", ignoreCase = true)) "$s'" else "$s's"

    /** What the program should expect from its preseason ranking. */
    fun expectation(nat: Int) = when {
        nat <= 4 -> "National title contender"
        nat <= 12 -> "Final Four darkhorse"
        nat <= 25 -> "Second-weekend team"
        nat <= 45 -> "NCAA Tournament team"
        nat <= 70 -> "On the bubble"
        else -> "Building for the future"
    }
}
