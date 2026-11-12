package com.nh.hoopscoach.game

import kotlinx.serialization.Serializable
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

@Serializable
class PreviewAngle(val kind: String, val score: Double, val text: String)

@Serializable
class TeamPreview(
    val overview: String,
    val angles: List<PreviewAngle>,
    val strength: String,
    val concern: String,
    val conference: String,
)

/** One picker per issue, independent of the simulation and global game RNG. */
internal class IssuePhrases(season: Int) {
    private val random = Random(season * 1_000_003L + 919)
    private val used = HashSet<String>()

    fun reserve(sentence: String) = used.add(sentence)

    fun pick(options: List<String>, context: String): String {
        require(options.isNotEmpty()) { "A phrase pool cannot be empty" }
        val fresh = options.shuffled(random).firstOrNull { it !in used }
        val result = fresh ?: "$context: ${options.first().replaceFirstChar { it.lowercase() }}"
        check(used.add(result)) { "Repeated preview sentence: $result" }
        return result
    }
}

object PreviewWriting {
    private fun f1(v: Double) = String.format(Locale.US, "%.1f", v)
    fun percent(v: Double) = "${(v * 100).roundToInt()}%"

    fun label(bid: Double, title: Double, deep: Double): String = when {
        title >= 0.10 -> "Title contender"
        deep >= 0.20 -> "Deep-run contender"
        bid in 0.35..0.65 -> "On the bubble"
        bid > 0.65 -> "Likely postseason team"
        bid >= 0.15 -> "Postseason long shot"
        else -> "Building for the future"
    }

    fun chance(p: Double, event: String): String = when {
        p in 0.35..0.65 -> "${percent(p)} for $event — close enough to go either way"
        p > 0.65 -> "${percent(p)} for $event — favored, not guaranteed"
        else -> "${percent(p)} for $event — an uphill path"
    }

    fun range(lo: Int, median: Int?, hi: Int, games: Int): String = if (median == null) {
        if (lo > 0 || hi > 0) "10th–90th win range: $lo–$hi; median unavailable in this older issue" else "Range unavailable in this older issue"
    } else
        "10th/50th/90th percentile records: $lo-${games - lo} / $median-${games - median} / $hi-${games - hi}"

    fun college(lg: League, proj: Map<Int, Proj>, odds: Map<Int, Odds>): Map<Int, TeamPreview> = build(lg,
        proj.mapValues { it.value.w }, odds.mapValues { it.value.ncaa }, odds.mapValues { it.value.title },
        odds.mapValues { it.value.f4 }, odds.mapValues { it.value.conf },
        lg.preview?.takeIf { it.season == lg.season - 1 }?.let { pv ->
            pv.regularActual.mapNotNull { (id, actual) -> pv.proj[id]?.let { id to (actual to it.w) } }.toMap()
        }.orEmpty())

    fun pro(lg: League, proj: Map<Int, NbaProj>): Map<Int, TeamPreview> = build(lg,
        proj.mapValues { it.value.w }, proj.mapValues { it.value.playoffs }, proj.mapValues { it.value.title },
        proj.mapValues { it.value.confFinals }, proj.mapValues { it.value.finals },
        lg.proState?.preview?.takeIf { it.season == lg.season - 1 }?.let { pv ->
            pv.regularActual.mapNotNull { (id, actual) -> pv.proj[id]?.let { id to (actual to it.w) } }.toMap()
        }.orEmpty())

    private fun build(lg: League, wins: Map<Int, Int>, bid: Map<Int, Double>, title: Map<Int, Double>,
                      deep: Map<Int, Double>, conf: Map<Int, Double>, previous: Map<Int, Pair<Int, Int>>): Map<Int, TeamPreview> {
        val picker = IssuePhrases(lg.season)
        val profiles = Previews.profiles(lg)
        val production = (lg.players + lg.alumni).distinctBy { it.id }.flatMap { p -> p.career.filter {
            it.season == lg.season - 1 && (!lg.pro || it.cls == PRO_YEAR)
        }.map { Triple(it.teamId, p.id, it.s) } }.groupBy { it.first }
        val schedule = lg.teams.associate { t ->
            val opponents = lg.games.filter { it.involves(t.id) && PreviewAvailability.regular(it) }
            t.id to (opponents.map { lg.team(it.opp(t.id)).elo }.average().takeIf { it.isFinite() } ?: 1500.0)
        }
        val confSchedule = lg.confs.associate { c -> c.id to c.teamIds.mapNotNull { schedule[it] }.average() }
        return lg.teams.sortedBy { it.id }.associate { t ->
            val name = if (lg.pro) t.name else t.school
            val p = bid[t.id] ?: 0.0
            val event = if (lg.pro) "the playoffs" else "an NCAA bid"
            val forecast = "${wins[t.id] ?: 0} wins; ${chance(p, event)}"
            val status = label(p, title[t.id] ?: 0.0, deep[t.id] ?: 0.0).lowercase()
            val overview = picker.pick(listOf(
                "$name enters as a $status: $forecast.",
                "The outlook at $name is $status territory, with $forecast.",
                "The preseason outlook puts $name in the $status conversation: $forecast.",
                "$name has a $status profile, not a promise: $forecast.",
                "For $name, the preseason baseline is $forecast, a $status outlook.",
                "$name starts with $forecast; that makes this a $status forecast.",
                "The numbers behind $name suggest $forecast and a $status season.",
                "$name's route begins with $forecast, squarely in $status territory."
            ), name)
            val prior = production[t.id].orEmpty().map { it.second to it.third }
            val candidates = angles(lg, t, schedule.getValue(t.id), confSchedule[t.confId] ?: 1500.0, previous[t.id], prior, picker)
            val chosen = candidates.sortedWith(compareByDescending<PreviewAngle> { it.score }.thenBy { it.kind }).take(2)
            val (strength, concern) = scouting(lg, t, picker, profiles.getValue(t.id))
            val cp = conf[t.id] ?: 0.0
            val target = if (lg.pro) "winning the conference playoffs" else "winning the regular-season conference"
            val conference = picker.pick(listOf(
                "$name: ${chance(cp, target)}.",
                "The conference case for $name rests on ${percent(cp)} odds of $target.",
                "$name has ${percent(cp)} odds of $target; the race remains open.",
                "For $name, $target comes in at ${percent(cp)}, not a certainty.",
                "$name's conference ceiling is real, but $target rates at ${percent(cp)}.",
                "$name has a ${percent(cp)} chance of $target.",
                "The conference forecast for $name: ${chance(cp, target)}.",
                "$name must earn it: the model assigns ${percent(cp)} to $target."
            ), name)
            t.id to TeamPreview(overview, chosen, strength, concern, conference)
        }
    }

    private fun angles(lg: League, t: Team, sos: Double, average: Double, previous: Pair<Int, Int>?, prior: List<Pair<Int, StatLine>>, picker: IssuePhrases): List<PreviewAngle> {
        val name = if (lg.pro) t.name else t.school
        val roster = lg.rosterOf(t)
        val returning = prior.filter { it.first in t.roster }
        val minutes = prior.sumOf { it.second.sec }.toDouble()
        val points = prior.sumOf { it.second.pts }.toDouble()
        val out = mutableListOf<PreviewAngle>()
        fun add(kind: String, score: Double, vararg options: String) {
            out += PreviewAngle(kind, score, picker.pick(options.toList(), "$name $kind"))
        }
        if (minutes > 0 || points > 0) {
            val m = if (minutes > 0) returning.sumOf { it.second.sec } / minutes else null
            val p = if (points > 0) returning.sumOf { it.second.pts } / points else null
            val detail = listOfNotNull(m?.let { "${percent(it)} of last season's minutes" }, p?.let { "${percent(it)} of its points" }).joinToString(" and ")
            add("continuity", abs(listOfNotNull(m, p).average() - 0.5) * 2,
                "$name brings back $detail, defining how much must be rebuilt.",
                "Continuity at $name means returning $detail.",
                "$name's returning core accounts for $detail; familiarity is measurable.",
                "Turnover leaves $name with $detail still in the building.")
        }
        roster.mapNotNull { p -> p.career.lastOrNull { it.season == lg.season - 1 && it.s.gp > 0 }?.let { p to it.s } }
            .maxByOrNull { it.second.ppg }?.let { (p, s) ->
                val detail = "${f1(s.ppg)} points, ${f1(s.rpg)} rebounds and ${f1(s.apg)} assists per game"
                add("star", (s.ppg / if (lg.pro) 30.0 else 22.0).coerceAtMost(1.5),
                    "For $name, ${p.name} sets the statistical standard after $detail last season.",
                    "$name can point to ${p.name}'s $detail last season as a starting point.",
                    "The proven production at $name belongs to ${p.name}: $detail last year.",
                    "Before projecting a leap at $name, remember ${p.name}'s $detail a year ago.")
            }
        roster.filter { p ->
            val last = p.career.lastOrNull { it.season == lg.season - 1 }
            if (lg.pro) p.proYears == 0 || last?.let { it.cls == PRO_YEAR && it.teamId >= 0 && it.teamId != t.id } == true
            else p.recruitRank > 0 && p.year == 1 || last?.let { it.teamId >= 0 && it.teamId != t.id } == true
        }.maxByOrNull { p ->
            if (!lg.pro && p.year == 1 && p.recruitRank > 0) 100.0 / kotlin.math.sqrt(p.recruitRank.toDouble())
            else if (lg.pro && p.proYears == 0) 100.0 / kotlin.math.sqrt((NbaPreviews.draftPick(lg, p) ?: 60).toDouble())
            else (p.career.lastOrNull { it.season == lg.season - 1 }?.s?.ppg ?: 0.0) * 3
        }?.let { p ->
                val stats = p.career.lastOrNull { it.season == lg.season - 1 }?.s
                val detail = when {
                    !lg.pro && p.year == 1 && p.recruitRank > 0 -> "the No. ${p.recruitRank} recruit"
                    lg.pro && p.proYears == 0 -> NbaPreviews.draftPick(lg, p)?.let { "the No. $it draft pick" } ?: "a rookie beginning his first pro season"
                    else -> "a newcomer who scored ${f1(stats?.ppg ?: 0.0)} points per game last season"
                }
                val score = when {
                    !lg.pro && p.year == 1 && p.recruitRank > 0 -> 1.2 / kotlin.math.sqrt(p.recruitRank / 10.0)
                    lg.pro && p.proYears == 0 -> 1.2 / kotlin.math.sqrt((NbaPreviews.draftPick(lg, p) ?: 60) / 5.0)
                    else -> (stats?.ppg ?: 0.0) / 18
                }
                add("newcomer", score.coerceAtMost(1.5),
                    "$name adds ${p.name}, $detail, to the rotation debate.",
                    "The new variable at $name is ${p.name}, $detail.",
                    "Incoming talent gives $name a different look: ${p.name} is $detail.",
                    "$name's biggest arrival is ${p.name}, $detail; the fit still has to work.")
            }
        val coach = lg.coachOf(t)
        val tenure = coach.history.count { it.teamId == t.id } + 1
        val heat = ((70 - coach.security) / 70.0).coerceAtLeast(0.0)
        add("coach", maxOf(heat * 1.4, if (tenure == 1) 0.7 else (tenure / 15.0).coerceAtMost(1.0)),
            "$name enters year $tenure under ${coach.name}, whose job security is ${coach.security}/100.",
            "The bench at $name belongs to ${coach.name}: year $tenure and ${coach.security}/100 job security.",
            "For $name, coaching continuity reads year $tenure with ${coach.name}, at ${coach.security}/100 security.",
            "$name's sideline story centers on ${coach.name}, in year $tenure with ${coach.security}/100 job security.")
        val delta = sos - average
        add("schedule", (abs(delta) / 100).coerceAtMost(1.5),
            "$name faces average opponent Elo ${sos.roundToInt()}, against its conference's ${average.roundToInt()} schedule average.",
            "The road at $name carries ${sos.roundToInt()} average opponent Elo; conference peers face ${average.roundToInt()}.",
            "$name's schedule checks in at ${sos.roundToInt()} opponent Elo versus ${average.roundToInt()} for conference peers.",
            "Schedule context for $name: ${sos.roundToInt()} average opponent Elo, compared with a ${average.roundToInt()} conference baseline.")
        if (previous != null) {
            val (actual, predicted) = previous
            val d = actual - predicted
            add("receipts", abs(d) / 6.0,
                "$name won $actual regular-season games against our $predicted-win forecast last season, a ${if (d >= 0) "+" else ""}$d-win miss.",
                "Our last $name forecast was $predicted regular-season wins; the team delivered $actual, a ${abs(d)}-win difference.",
                "$name supplied a reality check last season: $actual regular-season wins versus our projected $predicted.",
                "Last year's $name prediction missed by ${abs(d)} regular-season wins: we had $predicted, and they won $actual.")
        }
        val hurt = roster.filter { it.out }.sortedByDescending { it.ovr }
        if (hurt.isNotEmpty()) {
            val p = hurt.first()
            val duration = if (p.injGames >= Injuries.SEASON_OUT) "the season" else "${p.injGames} team games"
            add("availability", 2.0 + PreviewAvailability.loss(lg, t) / 100,
                "$name is missing ${p.name} for $duration with ${p.injury.ifBlank { "an injury" }}; the forecast discounts those minutes.",
                "Availability changes $name's outlook: ${p.name} is out for $duration, already reflected in the projection.",
                "$name must cover ${p.name}'s absence for $duration; the model uses replacement minutes until his return.",
                "For $name, ${p.name} will miss $duration.")
        }
        return out
    }

    internal fun scouting(lg: League, t: Team, picker: IssuePhrases = IssuePhrases(lg.season), profile: List<Pair<String, Double>> = Previews.profile(lg, t)): Pair<String, String> {
        val areas = profile.sortedByDescending { it.second }
        val name = if (lg.pro) t.name else t.school
        fun detail(area: Pair<String, Double>): String = "${area.first.lowercase()} at the ${Previews.ordinal((area.second * 100).roundToInt())} national percentile"
        val good = detail(areas.first())
        val bad = detail(areas.last())
        val strengths = listOf("The clearest edge", "The reliable weapon", "The foundation", "The strongest card", "The pressure point", "The bankable skill", "The best route", "The distinguishing trait", "The positive signal", "The matchup lever", "The starting advantage", "The dependable piece")
        val worries = listOf("opponents will test", "the thin margin comes from", "the toughest adjustment is", "the weak link remains", "the first repair must address", "the risk centers on", "the evidence is weakest in", "the least reliable tool is", "the vulnerability lies in", "close games may expose", "the difficult trade-off is", "the staff must protect")
        return picker.pick(strengths.map { "$it for $name is $good." }, name) to
            picker.pick(worries.map { "The worry: at $name, $it $bad." }, name)
    }
}
