package com.nh.hoopscoach.game

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.roundToInt

/** A feature page in a preview: a title, a deck, and its items (a tag groups them, like a tier). */
@Serializable
class Feature(val kind: String, val title: String, val deck: String, val items: List<FeatureItem>, val grouped: Boolean = false)

@Serializable
class FeatureItem(val head: String, val body: String, val tag: String = "", val teamId: Int = -1, val pid: Int = -1)

/**
 * What makes each year's issue its own: three feature pages from a pool of eight, a different three
 * every season (the pool rotates, so back-to-back issues never share their features), and a cover built
 * around the year's biggest story in one of three designs.
 */
object Features {
    private fun f1(d: Double) = "%.1f".format(d)
    private fun pct(v: Double) = Previews.pct(v)
    private fun nick(t: Team) = t.name.substringAfterLast(' ')

    /** Three of [pool], a different window each season. */
    private fun <T> rotate(season: Int, pool: List<T>, n: Int = 3): List<T> = (0 until n).map { pool[((season * n) + it).mod(pool.size)] }

    // ---------- the NBA ----------

    private val NBA_POOL = listOf("tiers", "overunder", "superlatives", "questions", "duos", "hotseat", "trades", "numbers")

    fun nba(lg: League, pv: NbaPreview): List<Feature> = rotate(lg.season, NBA_POOL).mapNotNull { k ->
        runCatching {
            when (k) {
                "tiers" -> nbaTiers(lg, pv)
                "overunder" -> nbaOverUnder(lg, pv)
                "superlatives" -> nbaSuperlatives(lg, pv)
                "questions" -> nbaQuestions(lg, pv)
                "duos" -> nbaDuos(lg, pv)
                "hotseat" -> nbaHotSeat(lg, pv)
                "trades" -> nbaTradeCandidates(lg, pv)
                else -> nbaNumbers(lg, pv)
            }
        }.getOrNull()?.takeIf { it.items.isNotEmpty() }
    }

    private fun pr(pv: NbaPreview, t: Team) = pv.proj.getValue(t.id)

    private fun nbaTiers(lg: League, pv: NbaPreview): Feature {
        val order = lg.teams.sortedByDescending { pr(pv, it).title * 100 + pr(pv, it).playoffs * 10 + pr(pv, it).w / 100.0 }
        fun tier(t: Team): String { val o = pr(pv, t); return PreviewWriting.label(o.playoffs, o.title, o.confFinals) }
        val items = order.map { t ->
            val o = pr(pv, t)
            FeatureItem("${t.name} · ${o.w}-${o.l}", pv.reports[t.id]?.strength ?: "Playoffs ${pct(o.playoffs)}, title ${pct(o.title)}.", tier(t), t.id)
        }
        return Feature("tiers", "The tiers", "All ${lg.teams.size} teams, sorted into where they really stand.", items, grouped = true)
    }

    /** A market line: last season's wins pulled a third of the way back to .500, the way a cautious bookmaker would. */
    private fun line(lg: League, pv: NbaPreview, t: Team): Double {
        val half = ProLeague.seasonGames(lg) / 2.0
        val last = pv.lastWins[t.id]?.takeIf { it >= 0 }?.toDouble() ?: half
        return (last * 0.66 + half * 0.34).roundToInt() + 0.5
    }

    private fun nbaOverUnder(lg: League, pv: NbaPreview): Feature {
        val picks = lg.teams.map { t -> Triple(t, line(lg, pv, t), pr(pv, t).w) }.sortedByDescending { abs(it.third - it.second) }.take(12)
        val items = picks.map { (t, l, w) ->
            val over = w > l
            val edge = abs(w - l)
            val conf = when { edge >= 7 -> "LOCK"; edge >= 3.5 -> "LEAN"; else -> "COIN FLIP" }
            val tv = Analysis.turnover(lg, t)
            val why = when {
                tv != null && over && tv.arrivals.any { NbaPreviews.lastLine(lg, it) != null } -> "The newcomers (${tv.arrivals.filter { NbaPreviews.lastLine(lg, it) != null }.sortedByDescending { it.ovr }.take(2).joinToString(" and ") { it.last }}) are worth more than the line thinks."
                tv != null && !over && tv.departures.isNotEmpty() -> "The line still counts ${tv.departures.first().first.last}, who's gone."
                over -> "This group is better than last season's record."
                else -> "Last season's record flattered this group."
            }
            FeatureItem("${t.name}: ${if (over) "OVER" else "UNDER"} ${f1(l)}", "We project $w. $why", "${if (over) "OVER" else "UNDER"} · $conf", t.id)
        }
        return Feature("overunder", "Over/under", "Win totals the way a bookmaker would set them, and where our model disagrees most.", items)
    }

    private fun bestTwo(lg: League, t: Team, f: (Player) -> Boolean) = lg.rosterOf(t).filter(f).sortedByDescending { it.ovr }.take(2)

    private fun nbaSuperlatives(lg: League, pv: NbaPreview): Feature {
        val items = mutableListOf<FeatureItem>()
        fun best(label: String, f: (Player) -> Boolean) {
            val (t, two) = lg.teams.map { it to bestTwo(lg, it, f) }.filter { it.second.size == 2 }.maxByOrNull { it.second.sumOf { p -> p.ovr } } ?: return
            items += FeatureItem("${t.name}: ${two.joinToString(" and ") { it.name }}", two.joinToString("; ") { "${it.last} ${Analysis.project(lg, it).short}" } + " (projected points, rebounds, assists).", label, t.id, two[0].id)
        }
        best("Best backcourt") { it.pos == Pos.PG || it.pos == Pos.SG }
        best("Best frontcourt") { it.pos == Pos.PF || it.pos == Pos.C }
        lg.teams.maxByOrNull { t -> lg.rosterOf(t).sortedByDescending { it.ovr }.drop(5).take(5).sumOf { it.ovr } }?.let { t ->
            val bench = lg.rosterOf(t).sortedByDescending { it.ovr }.drop(5).take(3)
            items += FeatureItem("${t.name}", "Their sixth through eighth men: ${bench.joinToString(", ") { it.name }}. Injuries won't sink this team.", "Deepest bench", t.id)
        }
        lg.teams.flatMap { lg.rosterOf(it) }.maxByOrNull { Ratings.defense(it) }?.let { p -> items += FeatureItem(p.name, "The best defender on our board. ${nick(lg.team(p.teamId))} opponents will feel it.", "Best defender", p.teamId, p.id) }
        lg.teams.flatMap { lg.rosterOf(it) }.filter { NbaPreviews.lastLine(lg, it)?.let { l -> l.s.tpa >= l.s.gp * 4 } == true }
            .maxByOrNull { NbaPreviews.lastLine(lg, it)!!.s.let { s -> s.tpm.toDouble() / s.tpa } }?.let { p ->
                val s = NbaPreviews.lastLine(lg, p)!!.s
                items += FeatureItem(p.name, "${(s.tpm * 100.0 / s.tpa).roundToInt()}% from three last season on ${f1(s.tpa.toDouble() / s.gp)} attempts a game.", "Best shooter", p.teamId, p.id)
            }
        lg.teams.minByOrNull { Analysis.rotationAge(lg, it) }?.let { t -> items += FeatureItem(t.name, "A top eight that averages ${f1(Analysis.rotationAge(lg, t))} years old. Patience required.", "Youngest core", t.id) }
        lg.teams.flatMap { lg.rosterOf(it) }.filter { it.proYears >= 1 && it.age <= 24 }.maxByOrNull { it.pot - it.ovr + it.ovr / 10 }?.let { p ->
            items += FeatureItem(p.name, "Ceiling of ${Ratings.shownPro(p.pot)} on our board, ${Ratings.shownPro(p.ovr)} today. This is the year it starts to show.", "Highest ceiling", p.teamId, p.id)
        }
        return Feature("superlatives", "Superlatives", "The league's best at everything, and a few things nobody asked about.", items)
    }

    private fun nbaQuestions(lg: League, pv: NbaPreview): Feature {
        val ranked = NbaPreviews.ranked(lg, pv)
        val items = mutableListOf<FeatureItem>()
        fun ask(q: String, a: String, t: Int = -1, p: Int = -1) { items += FeatureItem(q, a, "Q${items.size + 1}", t, p) }
        val fav = ranked[0]
        ask("Can the ${fav.name} win it all?", "${if (pr(pv, fav).title >= 0.3) "Probably." else "Maybe."} They're our favorite at ${pct(pr(pv, fav).title)}, which still means they lose ${pct(1 - pr(pv, fav).title)} of the time.", fav.id)
        NbaHistory.finalists(lg, lg.season - 1).first?.let { c -> ask("Do the ${c.name} repeat?", if (pr(pv, c).title >= 0.2) "They're good enough: ${pct(pr(pv, c).title)}." else "Unlikely: ${pct(pr(pv, c).title)}. Repeating is hard, and the league caught up.", c.id) }
        pv.mvp.firstOrNull()?.let { o -> lg.playerOrNull(o.pid)?.let { ask("Who wins MVP?", "${it.name} (${pct(o.p)}). Projected line: ${Analysis.project(lg, it)}.", it.teamId, it.id) } }
        lg.teams.filter { (pv.lastWins[it.id] ?: 99) >= 0 }.maxByOrNull { pr(pv, it).w - (pv.lastWins[it.id] ?: 0) }?.let { t ->
            ask("Who's the team nobody's talking about?", "The ${t.name}: ${pv.lastWins[t.id]} wins last season, ${pr(pv, t).w} in our projection.", t.id)
        }
        lg.teams.filter { (pv.lastWins[it.id] ?: -1) >= 0 }.minByOrNull { pr(pv, it).w - (pv.lastWins[it.id] ?: 0) }?.let { t ->
            ask("Which team is in trouble?", "The ${t.name}. ${pv.lastWins[t.id]} wins last season, ${pr(pv, t).w} projected.", t.id)
        }
        pv.roy.firstOrNull()?.let { o -> lg.playerOrNull(o.pid)?.let { ask("Who's Rookie of the Year?", "${it.name} (${pct(o.p)}), with ${Analysis.project(lg, it)} projected.", it.teamId, it.id) } }
        val u = lg.user
        ask("Do the ${u.name} make the playoffs?", "${if (pr(pv, u).playoffs >= 0.5) "Yes" else "Probably not"}: ${pct(pr(pv, u).playoffs)}, at ${pr(pv, u).w}-${pr(pv, u).l}.", u.id)
        val confPower = (0..1).map { c -> c to lg.teams.filter { it.confId == c }.map { pr(pv, it).w }.sortedDescending().take(8).average() }
        val strong = confPower.maxByOrNull { it.second }!!
        ask("Which conference is better?", "The ${lg.conf(strong.first).name}: its top eight average ${f1(strong.second)} projected wins, against ${f1(confPower.first { it.first != strong.first }.second)}.")
        pv.mip.firstOrNull()?.let { o -> lg.playerOrNull(o.pid)?.let { ask("Who's the breakout star?", "${it.name}, ${it.age}, ceiling ${Ratings.shownPro(it.pot)}. Most Improved odds: ${pct(o.p)}.", it.teamId, it.id) } }
        ranked.last().let { t -> ask("Who finishes last?", "The ${t.name}: ${pr(pv, t).w}-${pr(pv, t).l}. At least the lottery odds are good.", t.id) }
        return Feature("questions", "Ten questions", "The questions everyone is asking, and our answers, with the numbers behind them.", items.take(10))
    }

    private fun nbaDuos(lg: League, pv: NbaPreview): Feature {
        val duos = lg.teams.map { t -> t to lg.rosterOf(t).sortedByDescending { NbaPreviews.value(lg, it) }.take(2) }.filter { it.second.size == 2 }
            .sortedByDescending { (_, d) -> d.sumOf { NbaPreviews.value(lg, it) } }.take(8)
        val items = duos.mapIndexed { i, (t, d) ->
            val pts = d.sumOf { Analysis.project(lg, it).ppg }
            FeatureItem("${i + 1}. ${d[0].name} & ${d[1].name}", "${f1(pts)} projected points a night between them for the ${t.name} (${pr(pv, t).w} wins, title ${pct(pr(pv, t).title)}).", "", t.id, d[0].id)
        }
        return Feature("duos", "The best duos", "Stars win in pairs. The eight best one-two punches in the league.", items)
    }

    private fun nbaHotSeat(lg: League, pv: NbaPreview): Feature {
        val items = lg.teams.filter { it.id != lg.userTeamId }.mapNotNull { t ->
            val c = lg.coachOf(t)
            val tenure = c.history.count { it.teamId == t.id }
            val last = pv.lastWins[t.id] ?: return@mapNotNull null
            val heat = (if (pr(pv, t).w < ProLeague.seasonGames(lg) / 2) 2 else 0) + (if (last in 0 until ProLeague.seasonGames(lg) / 2) 2 else 0) + (if (tenure >= 3) 1 else 0) + (if (pr(pv, t).title >= 0.05 && c.titles == 0) 2 else 0)
            if (heat < 3) null else t to heat
        }.sortedByDescending { it.second }.take(6).map { (t, h) ->
            val c = lg.coachOf(t)
            FeatureItem("${c.name}, ${t.name}", "${c.history.count { it.teamId == t.id }} seasons in charge, ${c.w}-${c.l} overall. We project ${pr(pv, t).w} wins" +
                (if (pr(pv, t).title >= 0.05) "; with a team this good, anything short of a deep run is trouble." else "; another losing year could be his last."),
                if (h >= 5) "HOT" else "WARM", t.id)
        }
        return Feature("hotseat", "The hot seat", "The coaches who need a good year, and why.", items)
    }

    private fun nbaTradeCandidates(lg: League, pv: NbaPreview): Feature {
        val items = lg.teams.filter { pr(pv, it).playoffs < 0.3 }.flatMap { t -> lg.rosterOf(t).filter { it.age >= 26 && it.id != lg.heroId }.sortedByDescending { it.ovr }.take(2).map { it to t } }
            .sortedByDescending { NbaPreviews.value(lg, it.first) }.take(8).map { (p, t) ->
                FeatureItem(p.name, "${p.age} years old, ${p.contractYears} ${if (p.contractYears == 1) "year" else "years"} left at ${Road.dollars(Payroll.salaryOf(lg, p))}. " +
                    "The ${t.name} project at ${pr(pv, t).w} wins; a contender will call before the deadline.", if (p.contractYears <= 1) "EXPIRING" else "", t.id, p.id)
            }
        return Feature("trades", "On the block", "Good players on teams going nowhere: the names you'll hear before the deadline.", items)
    }

    private fun nbaNumbers(lg: League, pv: NbaPreview): Feature {
        val items = mutableListOf<FeatureItem>()
        val ranked = NbaPreviews.ranked(lg, pv)
        items += FeatureItem(pct(pr(pv, ranked[0]).title), "The ${ranked[0].name}' title odds, the best in the league.", "", ranked[0].id)
        items += FeatureItem("${lg.teams.count { pr(pv, it).title >= 0.05 }}", "Teams with at least a 5% chance to win it all.")
        val tvs = lg.teams.mapNotNull { t -> Analysis.turnover(lg, t)?.let { t to it } }
        tvs.maxByOrNull { it.second.returning }?.let { (t, tv) -> items += FeatureItem(pct(tv.returning), "Of last season's minutes back in ${t.school}, the most in the league.", "", t.id) }
        tvs.minByOrNull { it.second.returning }?.let { (t, tv) -> items += FeatureItem(pct(tv.returning), "Back in ${t.school}, the least: almost a new team.", "", t.id) }
        lg.teams.maxByOrNull { Analysis.rotationAge(lg, it) }?.let { t -> items += FeatureItem(f1(Analysis.rotationAge(lg, t)), "The average age of the ${possessive(t.name)} top eight, the oldest.", "", t.id) }
        lg.teams.flatMap { lg.rosterOf(it) }.sortedByDescending { NbaPreviews.value(lg, it) }.take(30).maxByOrNull { Analysis.project(lg, it).ppg }?.let { p ->
            items += FeatureItem(f1(Analysis.project(lg, p).ppg), "Projected points a game for ${p.name}, the league's top scorer in our model.", "", p.teamId, p.id)
        }
        lg.teams.maxByOrNull { pr(pv, it).hi - pr(pv, it).lo }?.let { t -> items += FeatureItem("${pr(pv, t).lo}-${pr(pv, t).hi}", "The ${possessive(t.name)} win range, the widest: nobody knows what this team is.", "", t.id) }
        lg.teams.flatMap { lg.rosterOf(it) }.maxByOrNull { it.age }?.let { p -> items += FeatureItem("${p.age}", "${p.name}, the oldest player in the league.", "", p.teamId, p.id) }
        return Feature("numbers", "By the numbers", "The season in figures.", items)
    }

    private fun possessive(name: String) = if (name.endsWith("s")) "$name'" else "$name's"

    /**
     * The cover: the story it leads with and its design (0 the classic favorite cover, 1 a feature cover
     * on one team or player, 2 a two-team "versus" cover for a two-horse race).
     */
    fun nbaCover(lg: League, pv: NbaPreview, stories: List<Story>): Pair<Story?, Int> {
        val ranked = NbaPreviews.ranked(lg, pv)
        val hero = lg.playerOrNull(lg.heroId)
        stories.firstOrNull { it.kicker == "Spotlight" && hero != null && pv.mvp.take(3).any { o -> o.pid == hero.id } }?.let { return it to 1 }
        val a = pr(pv, ranked[0]).title
        val b = ranked.getOrNull(1)?.let { pr(pv, it).title } ?: 0.0
        if (a - b < 0.04 && a >= 0.12) return null to 2
        val featured = setOf("Superteam", "New look", "Summer winner", "The window", "Youth movement", "Continuity", "History", "Breakout watch")
        if (lg.season % 3 != 0) stories.firstOrNull { it.kicker in featured }?.let { return it to 1 }
        return null to 0
    }

    // ---------- college ----------

    private val COLLEGE_POOL = listOf("tiers", "superlatives", "questions", "conferences", "numbers", "hotseat", "cinderella", "freshmen")

    fun college(lg: League, pv: Preview): List<Feature> = rotate(lg.season, COLLEGE_POOL).mapNotNull { k ->
        runCatching {
            when (k) {
                "tiers" -> colTiers(lg, pv)
                "superlatives" -> colSuperlatives(lg, pv)
                "questions" -> colQuestions(lg, pv)
                "conferences" -> colConferences(lg, pv)
                "hotseat" -> colHotSeat(lg, pv)
                "cinderella" -> colCinderella(lg, pv)
                "freshmen" -> colFreshmen(lg, pv)
                else -> colNumbers(lg, pv)
            }
        }.getOrNull()?.takeIf { it.items.isNotEmpty() }
    }

    private fun odds(pv: Preview, t: Team) = pv.odds[t.id] ?: Odds(0.0, 0.0, 0.0, 0.0, 0.0, 0, 0)
    private fun colRanked(lg: League, pv: Preview) = pv.proj.entries.sortedBy { it.value.nat }.map { lg.team(it.key) }

    private fun colTiers(lg: League, pv: Preview): Feature {
        val top = colRanked(lg, pv).take(40)
        fun tier(t: Team): String {
            val o = odds(pv, t)
            return PreviewWriting.label(o.ncaa, o.title, o.f4)
        }
        fun summary(t: Team): String {
            val o = odds(pv, t)
            val star = lg.rosterOf(t).maxByOrNull { it.ovr }
            val starText = if (star != null) "Led by ${star.name} (${star.posLabel}, ${Analysis.project(lg, star).short})." else ""
            val scoutNote = Takes.college(lg, pv, t, 1, from = 1)
            val oddsText = when {
                o.title >= 0.06 -> "Title ${pct(o.title)} · Final Four ${pct(o.f4)} · Dance ${pct(o.ncaa)}."
                o.f4 >= 0.08 -> "Final Four ${pct(o.f4)} · Dance ${pct(o.ncaa)} · ${o.lo}-${o.hi} wins."
                o.s16 >= 0.2 -> "Sweet 16 ${pct(o.s16)} · Dance ${pct(o.ncaa)}."
                else -> "Dance ${pct(o.ncaa)} · Win range ${o.lo}-${o.hi}."
            }
            return if (scoutNote.isNotEmpty()) "$oddsText $scoutNote" else "$oddsText $starText"
        }
        return Feature("tiers", "The tiers", "Our top 40, sorted by how far they can go in March.",
            top.map { t ->
                val pr = pv.proj.getValue(t.id)
                FeatureItem("${pr.nat}. ${t.school} · ${pr.w}-${pr.l}", summary(t), tier(t), t.id)
            }, grouped = true)
    }

    private fun colSuperlatives(lg: League, pv: Preview): Feature {
        val items = mutableListOf<FeatureItem>()
        fun best(label: String, f: (Player) -> Boolean) {
            val (t, two) = lg.teams.map { it to bestTwo(lg, it, f) }.filter { it.second.size == 2 }.maxByOrNull { it.second.sumOf { p -> p.ovr } } ?: return
            items += FeatureItem("${t.school}: ${two.joinToString(" and ") { it.name }}", two.joinToString("; ") { "${it.last} ${Analysis.project(lg, it).short}" } + " projected.", label, t.id, two[0].id)
        }
        best("Best backcourt") { it.pos == Pos.PG || it.pos == Pos.SG }
        best("Best frontcourt") { it.pos == Pos.PF || it.pos == Pos.C }
        lg.teams.maxByOrNull { t -> lg.rosterOf(t).filter { it.year == 1 }.sortedByDescending { it.ovr }.take(3).sumOf { it.ovr } }?.let { t ->
            items += FeatureItem(t.school, "Freshmen: ${lg.rosterOf(t).filter { it.year == 1 }.sortedByDescending { it.ovr }.take(3).joinToString(", ") { it.name }}.", "Best freshman class", t.id)
        }
        Previews.portalWinners(lg).firstOrNull()?.let { (t, ps) -> items += FeatureItem(t.school, "Transfers: ${ps.take(3).joinToString(", ") { "${it.name} (from ${Previews.cameFrom(lg, it)})" }}.", "Portal champion", t.id, ps.first().id) }
        colRanked(lg, pv).take(25).mapNotNull { t -> Analysis.turnover(lg, t)?.let { t to it } }.maxByOrNull { it.second.returning }?.let { (t, tv) ->
            items += FeatureItem(t.school, "${pct(tv.returning)} of last season's minutes are back.", "Most experienced", t.id)
        }
        lg.players.filter { it.teamId >= 0 }.maxByOrNull { Ratings.defense(it) }?.let { p -> items += FeatureItem(p.name, "${lg.team(p.teamId).school}. The best defender in the country on our board.", "Best defender", p.teamId, p.id) }
        return Feature("superlatives", "Superlatives", "The country's best at everything.", items)
    }

    private fun colQuestions(lg: League, pv: Preview): Feature {
        val ranked = colRanked(lg, pv)
        val items = mutableListOf<FeatureItem>()
        fun ask(q: String, a: String, t: Int = -1, p: Int = -1) { items += FeatureItem(q, a, "Q${items.size + 1}", t, p) }
        val fav = ranked[0]
        ask("Is ${fav.school} the team to beat?", "Yes, at ${pct(odds(pv, fav).title)} to win it all. It's still more likely somebody else cuts down the nets.", fav.id)
        CollegeHistory.lastChampion(lg)?.let { c -> ask("Can ${c.school} repeat?", "A ${pct(odds(pv, c).title)} chance. ${if (odds(pv, c).title >= 0.1) "It has a credible case." else "A repeat is a long shot."}", c.id) }
        pv.poy.firstOrNull()?.let { o -> lg.playerOrNull(o.pid)?.let { ask("Who's the Player of the Year?", "${it.name}, ${lg.team(it.teamId).school} (${pct(o.p)}).", it.teamId, it.id) } }
        lg.teams.getOrNull(pv.sleeper)?.let { t -> ask("Who's the sleeper?", "${t.school}: No. ${pv.proj.getValue(t.id).nat} in our poll, and a second-weekend team ${NbaPreviews.often(odds(pv, t).s16)}.", t.id) }
        lg.teams.getOrNull(pv.cinderella)?.let { t -> ask("Who's this year's Cinderella?", "${t.school} out of the ${lg.conf(t.confId).abbr}: in the tournament ${NbaPreviews.often(odds(pv, t).ncaa)}, the Sweet 16 ${NbaPreviews.often(odds(pv, t).s16)}.", t.id) }
        pv.bids.maxByOrNull { it.value }?.let { (c, n) -> ask("Which is the best conference?", "The ${lg.conf(c).name}, with ${f1(n)} projected NCAA bids.") }
        val u = lg.user
        ask("Does ${u.school} go dancing?", "${if (odds(pv, u).ncaa >= 0.5) "Yes" else "Probably not"}: ${pct(odds(pv, u).ncaa)}.", u.id)
        lg.teams.getOrNull(pv.freshmen.firstOrNull()?.let { lg.playerOrNull(it)?.teamId } ?: -1)?.let { t ->
            lg.playerOrNull(pv.freshmen.first())?.let { p -> ask("Who's the best freshman?", "${p.name} at ${t.school}. ${Analysis.project(lg, p)} projected.", t.id, p.id) }
        }
        ranked.firstOrNull { (lg.coachOf(it).history.lastOrNull()?.result ?: "") in setOf("National Champion", "Runner-Up", "Final Four") && pv.proj.getValue(it.id).nat > 15 }?.let { t ->
            ask("Which Final Four team falls off?", "${t.school}: No. ${pv.proj.getValue(t.id).nat} in our poll this year.", t.id)
        }
        ask("Who wins it all?", "${lg.teams.getOrNull(pv.finalFour.firstOrNull() ?: fav.id)?.school ?: fav.school}. Our Final Four: ${pv.finalFour.mapNotNull { lg.teams.getOrNull(it)?.school }.joinToString(", ")}.")
        return Feature("questions", "Ten questions", "What everyone is asking, and our answers.", items.take(10))
    }

    private fun colConferences(lg: League, pv: Preview): Feature {
        val rows = lg.confs.map { c -> c to (pv.bids[c.id] ?: 0.0) }.sortedByDescending { it.second }.take(12)
        return Feature("conferences", "Conference rankings", "Every league's strength, by its projected NCAA bids.",
            rows.mapIndexed { i, (c, b) ->
                val fav = c.teamIds.map { lg.team(it) }.minByOrNull { pv.proj[it.id]?.confRank ?: 99 }
                FeatureItem("${i + 1}. ${c.name}", "${f1(b)} bids on average. The favorite: ${fav?.school ?: "?"}" + (fav?.let { " (No. ${pv.proj.getValue(it.id).nat})" } ?: "") + ".", "", fav?.id ?: -1)
            })
    }

    private fun colHotSeat(lg: League, pv: Preview): Feature {
        val items = pv.hotSeat.mapNotNull { lg.teams.getOrNull(it) }.map { t ->
            val c = lg.coachOf(t)
            FeatureItem("${c.name}, ${t.school}", "No tournament last season at a program that expects one. We project ${pv.proj.getValue(t.id).w}-${pv.proj.getValue(t.id).l} and the tournament ${NbaPreviews.often(odds(pv, t).ncaa)}.",
                if (odds(pv, t).ncaa < 0.4) "HOT" else "WARM", t.id)
        }
        return Feature("hotseat", "The hot seat", "Coaches at proud programs who need March this year.", items)
    }

    private fun colCinderella(lg: League, pv: Preview): Feature {
        val items = lg.teams.filter { lg.conf(it.confId).tier >= 2 }.sortedByDescending { odds(pv, it).s16 * 3 + odds(pv, it).ncaa }.take(8).map { t ->
            FeatureItem("${t.school} (${lg.conf(t.confId).abbr})", "In the tournament ${NbaPreviews.often(odds(pv, t).ncaa)}; a Sweet 16 ${NbaPreviews.often(odds(pv, t).s16)}. " +
                (Previews.star(lg, t)?.let { "${it.name} is the reason: ${Analysis.project(lg, it).short} projected." } ?: ""), "", t.id)
        }
        return Feature("cinderella", "Cinderella watch", "Small schools with a real chance to bust brackets.", items)
    }

    private fun colFreshmen(lg: League, pv: Preview): Feature {
        val items = pv.freshmen.mapNotNull { lg.playerOrNull(it) }.take(10).mapIndexed { i, p ->
            FeatureItem("${i + 1}. ${p.name}", "${lg.team(p.teamId).school}, ${p.posLabel}. Projected ${Analysis.project(lg, p)}.", if (p.recruitRank in 1..10) "TOP 10 RECRUIT" else "", p.teamId, p.id)
        }
        return Feature("freshmen", "The freshmen", "The ten first-year players who'll matter most.", items)
    }

    private fun colNumbers(lg: League, pv: Preview): Feature {
        val items = mutableListOf<FeatureItem>()
        val ranked = colRanked(lg, pv)
        items += FeatureItem(pct(odds(pv, ranked[0]).title), "${ranked[0].school}'s title odds, the best in the country.", "", ranked[0].id)
        items += FeatureItem("${lg.teams.count { odds(pv, it).title >= 0.03 }}", "Teams with at least a 3% chance to win it all.")
        val tvs = ranked.take(25).mapNotNull { t -> Analysis.turnover(lg, t)?.let { t to it } }
        tvs.minByOrNull { it.second.returning }?.let { (t, tv) -> items += FeatureItem(pct(tv.returning), "Of ${t.school}'s minutes are back, the fewest in our Top 25.", "", t.id) }
        tvs.maxByOrNull { it.second.returning }?.let { (t, tv) -> items += FeatureItem(pct(tv.returning), "Back at ${t.school}, the most in our Top 25.", "", t.id) }
        pv.bids.maxByOrNull { it.value }?.let { (c, n) -> items += FeatureItem(f1(n), "Projected NCAA bids for the ${lg.conf(c).name}, the most.") }
        lg.players.filter { it.teamId >= 0 }.maxByOrNull { Analysis.project(lg, it).ppg }?.let { p -> items += FeatureItem(f1(Analysis.project(lg, p).ppg), "Projected points a game for ${p.name}, the country's top scorer in our model.", "", p.teamId, p.id) }
        return Feature("numbers", "By the numbers", "The season in figures.", items)
    }

    fun collegeCover(lg: League, pv: Preview, stories: List<Story>): Pair<Story?, Int> {
        val ranked = colRanked(lg, pv)
        val hero = lg.playerOrNull(lg.heroId)
        stories.firstOrNull { it.kicker == "Spotlight" && hero != null && pv.poy.take(3).any { o -> o.pid == hero.id } }?.let { return it to 1 }
        val a = odds(pv, ranked[0]).title
        val b = ranked.getOrNull(1)?.let { odds(pv, it).title } ?: 0.0
        if (a - b < 0.03 && a >= 0.1) return null to 2
        val featured = setOf("Experience", "Reloading", "History", "On paper", "Report card")
        if (lg.season % 3 != 0) stories.firstOrNull { it.kicker in featured && it.kicker != "Report card" }?.let { return it to 1 }
        return null to 0
    }
}
