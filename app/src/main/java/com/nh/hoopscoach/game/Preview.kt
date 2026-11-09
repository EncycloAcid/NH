package com.nh.hoopscoach.game

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/** A team's preseason projection: national rank, predicted conference finish and projected records. */
@Serializable
class Proj(val nat: Int, val confRank: Int, val w: Int, val l: Int, val cw: Int, val cl: Int)

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
    /** 2 = the full 15-page issue. */
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
)

object Previews {

    /** Built once per season, when the schedule is set. */
    fun build(lg: League): Preview {
        val n = lg.teams.size
        val games = IntArray(n)
        val confGames = IntArray(n)
        val confWins = DoubleArray(n)
        for (g in lg.games) {
            if (g.type != GameType.NONCONF && g.type != GameType.CONF && g.type != GameType.EVENT) continue
            games[g.home]++
            games[g.away]++
            if (g.type == GameType.CONF) {
                val p = Rankings.winProbFor(lg, g, g.home)
                confWins[g.home] += p
                confWins[g.away] += 1 - p
                confGames[g.home]++
                confGames[g.away]++
            }
        }
        for (ev in lg.events) for (id in ev.teams) games[id] += 2
        val byElo = lg.teams.sortedByDescending { it.elo }
        val nat = HashMap<Int, Int>()
        byElo.forEachIndexed { i, t -> nat[t.id] = i + 1 }
        val confRank = HashMap<Int, Int>()
        for (c in lg.confs) {
            c.teamIds.map { lg.team(it) }.sortedWith(compareByDescending<Team> { confWins[it.id] }.thenByDescending { it.elo })
                .forEachIndexed { i, t -> confRank[t.id] = i + 1 }
        }
        val proj = lg.teams.associate { t ->
            val w = t.expWins.roundToInt().coerceIn(0, games[t.id])
            val cw = confWins[t.id].roundToInt().coerceIn(0, confGames[t.id])
            t.id to Proj(nat.getValue(t.id), confRank[t.id] ?: 0, w, games[t.id] - w, cw, confGames[t.id] - cw)
        }

        val active = lg.players.filter { it.teamId >= 0 }
        fun value(p: Player) = p.ovr + (p.career.lastOrNull()?.s?.ppg ?: 0.0) * 0.25
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
        val cinderella = byElo.firstOrNull { lg.conf(it.confId).tier >= 2 && it.id !in finalFour && it.id != sleeper }?.id ?: sleeper
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
        val pv = Preview(
            lg.season, proj, allAmerica, freshmen, transfers, breakouts, confPoy, finalFour, sleeper,
            version = 2, underRadar = underRadar, dpoy = dpoy, cinderella = cinderella, cotyTeam = coty,
            marquee = marquee, hotSeat = hotSeat,
        )
        return Preview(
            pv.season, pv.proj, pv.allAmerica, pv.freshmen, pv.transfers, pv.breakouts, pv.confPoy, pv.finalFour, pv.sleeper,
            2, pv.underRadar, pv.dpoy, pv.cinderella, pv.cotyTeam, pv.marquee, pv.hotSeat, boldPredictions(lg, pv),
        )
    }

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

    /** Five predictions to argue about. */
    private fun boldPredictions(lg: League, pv: Preview): List<String> {
        val out = mutableListOf<String>()
        val ranked = pv.proj.entries.sortedBy { it.value.nat }.map { lg.team(it.key) }
        lg.playerOrNull(pv.freshmen.firstOrNull() ?: -1)?.let { f ->
            out += "${f.name} wins National Freshman of the Year and is a top-five pick in the draft."
        }
        out += "${lg.team(pv.cinderella).school} wins two NCAA Tournament games."
        out += "${lg.team(pv.sleeper).school} beats a top-10 team before conference play."
        ranked.getOrNull(4)?.let { out += "${it.school} loses in the first weekend of March." }
        val byConf = ranked.take(25).groupingBy { it.confId }.eachCount().maxByOrNull { it.value }
        if (byConf != null) out += "The ${lg.conf(byConf.key).abbr} sends ${byConf.value + 2} teams to the NCAA Tournament."
        val u = lg.user
        val mine = pv.proj.getValue(u.id)
        out += when {
            mine.nat <= 10 -> "${u.school} wins the ${lg.conf(u.confId).abbr} by two games."
            mine.nat <= 45 -> "${u.school} finishes ahead of where we picked it in the ${lg.conf(u.confId).abbr}."
            else -> "${u.school} pulls off at least one upset over a ranked team."
        }
        return out
    }

    /** The editor's letter at the front of the issue. */
    fun editorsLetter(lg: League, pv: Preview): List<String> {
        val ranked = pv.proj.entries.sortedBy { it.value.nat }.map { lg.team(it.key) }
        val top = ranked[0]
        val champ = lg.history.lastOrNull()?.let { lg.team(it.champion) }
        val u = lg.user
        val mine = pv.proj.getValue(u.id)
        val out = mutableListOf<String>()
        out += buildString {
            append("Welcome to the ${pv.season} season. ")
            append("${top.school} opens at No. 1")
            star(lg, top)?.let { append(", and ${it.name} is the reason") }
            append(". ")
            if (champ != null) append(if (champ.id == top.id) "The defending champions have the look of a repeat. " else "Defending champion ${champ.school} starts at No. ${pv.proj.getValue(champ.id).nat}. ")
        }
        val f4 = pv.finalFour.map { lg.team(it) }
        val others = f4.drop(1).joinToString(", ") { it.school }.let { names ->
            val lastComma = names.lastIndexOf(", ")
            if (lastComma < 0) names else names.replaceRange(lastComma, lastComma + 1, " and")
        }
        out += if (f4.size >= 4) {
            "We have ${others} joining ${f4[0].school} in our Final Four, and ${lg.team(pv.sleeper).school} is the sleeper nobody wants to see in March."
        } else {
            "Behind them, ${ranked[1].school}, ${ranked[2].school} and ${ranked[3].school} round out our Final Four, and ${lg.team(pv.sleeper).school} is the team nobody wants to see in March."
        }
        out += "${u.school} comes in at No. ${mine.nat} and picked ${ordinal(mine.confRank)} in the ${lg.conf(u.confId).abbr}. Turn to the team report for the full breakdown."
        return out
    }

    fun ordinal(n: Int): String = "$n" + when { n % 100 in 11..13 -> "th"; n % 10 == 1 -> "st"; n % 10 == 2 -> "nd"; n % 10 == 3 -> "rd"; else -> "th" }

    /** Team strengths and concerns, by where the team ranks nationally in each area. */
    fun scouting(lg: League, t: Team): Pair<List<String>, List<String>> {
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
        val pct = areas.map { (name, f) ->
            val mine = f(t)
            name to lg.teams.count { f(it) < mine }.toDouble() / lg.teams.size
        }.sortedByDescending { it.second }
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

    /** This season's issue, built now if the save predates the full magazine. */
    fun current(lg: League): Preview {
        val p = lg.preview
        if (p != null && p.season == lg.season && p.version >= 2) return p
        return build(lg).also { it.opened = true; lg.preview = it }
    }

    fun star(lg: League, t: Team): Player? = lg.rosterOf(t).maxByOrNull { it.ovr }

    /** Best freshman or transfer on the roster, other than the headliner. */
    fun newcomer(lg: League, t: Team): Player? {
        val star = star(lg, t)
        return lg.rosterOf(t).filter { isNewcomer(it) && it.id != star?.id }.maxByOrNull { it.ovr }
    }

    /** A freshman, or a transfer whose last season was somewhere else. */
    fun isNewcomer(p: Player) = p.year == 1 || (p.career.isNotEmpty() && p.career.last().teamId >= 0 && p.career.last().teamId != p.teamId)

    private fun lastSeason(result: String) = when (result) {
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
