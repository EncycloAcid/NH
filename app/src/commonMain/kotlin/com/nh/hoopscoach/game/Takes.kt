package com.nh.hoopscoach.game

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * The preview writers' takes: a few sentences on any team, written from its situation rather than
 * a template. Each take finds the team's story (the defending champs, a big climb or fall, a new
 * coach, a veteran group, a portal reload, a tournament streak or drought, a summer trade, a top
 * rookie...), then the players who matter with last season's numbers, what the team does best and
 * worst with its national (or league) rank, and what the projection says, worded for the team's
 * tier. The same take every time the issue is opened, different wording from team to team and
 * season to season. Also the in-season lines that say how a projection has moved, and why.
 */
object Takes {
    // ---------- wording helpers ----------

    private fun rnd(lg: League, t: Team, slot: Int) = Random(lg.season * 1_000_003L + t.id * 7_919L + slot * 104_729L)
    private fun <T> pick(lg: League, t: Team, slot: Int, vararg o: T): T = o[rnd(lg, t, slot).nextInt(o.size)]
    private fun f1(v: Double) = "%.1f".format(v)
    private fun pct(v: Double) = Previews.pct(v)
    private fun ord(n: Int) = Previews.ordinal(n)
    private fun num(n: Int) = when (n) {
        0 -> "no"; 1 -> "one"; 2 -> "two"; 3 -> "three"; 4 -> "four"; 5 -> "five"; 6 -> "six"; 7 -> "seven"; 8 -> "eight"; 9 -> "nine"; 10 -> "ten"
        else -> "$n"
    }
    private fun cap(s: String) = s.replaceFirstChar { it.uppercase() }
    private fun starters(n: Int) = if (n == 1) "one starter" else "${num(n)} starters"
    private fun starWord(n: Int) = when (n) { 5 -> "five-star"; 4 -> "four-star"; 3 -> "three-star"; 2 -> "two-star"; else -> "$n-star" }
    private fun classWord(c: String) = when (c.uppercase().take(2)) { "FR" -> "freshman"; "SO" -> "sophomore"; "JR" -> "junior"; "SR" -> "senior"; "GR" -> "grad student"; else -> c.lowercase() }
    private fun posWord(p: Pos) = when (p) { Pos.PG -> "point guard"; Pos.SG -> "shooting guard"; Pos.SF -> "wing"; Pos.PF -> "forward"; Pos.C -> "center" }
    private fun poss(name: String) = NbaPreviews.possessive(name)

    private val DANCE = setOf("National Champion", "Runner-Up", "Final Four", "Elite Eight", "Sweet 16", "Round of 32", "Round of 64", "First Four")

    // ---------- where a team ranks in each part of the game ----------

    private val AREAS = listOf("perimeter shooting", "finishing inside", "ball handling", "rebounding", "perimeter defense", "rim protection", "depth", "experience")
    private var rankKey = Long.MIN_VALUE
    private var rankCache: Map<Int, IntArray> = emptyMap()

    /** Each team's rank in every area, 1 the best: nationally in college, league-wide in the pros. Cached per league, season and week. */
    @Synchronized
    private fun ranks(lg: League): Map<Int, IntArray> {
        val key = System.identityHashCode(lg) * 1_000_003L + lg.season * 101L + lg.day / 7
        if (key == rankKey) return rankCache
        val values = HashMap<Int, DoubleArray>()
        for (t in lg.teams) {
            val roster = lg.rosterOf(t).sortedByDescending { it.ovr }
            val rot = roster.take(8)
            if (rot.isEmpty()) { values[t.id] = DoubleArray(AREAS.size); continue }
            fun avg(f: (Player) -> Double) = rot.sumOf(f) / rot.size
            values[t.id] = doubleArrayOf(
                avg { (it[Attr.OUT] + it[Attr.MID]) / 2.0 },
                avg { it[Attr.INS].toDouble() },
                avg { (it[Attr.HND] + it[Attr.PAS]) / 2.0 },
                avg { it[Attr.REB].toDouble() },
                avg { (it[Attr.PER] + it[Attr.STL]) / 2.0 },
                avg { (it[Attr.IDF] + it[Attr.BLK]) / 2.0 },
                roster.drop(5).take(4).map { it.ovr.toDouble() }.average().takeIf { !it.isNaN() } ?: 0.0,
                if (lg.pro) avg { it.proYears.toDouble() } else avg { it.year.toDouble() },
            )
        }
        val out = HashMap<Int, IntArray>()
        for (t in lg.teams) out[t.id] = IntArray(AREAS.size)
        for (a in AREAS.indices) {
            lg.teams.sortedByDescending { values[it.id]!![a] }.forEachIndexed { i, t -> out[t.id]!![a] = i + 1 }
        }
        rankKey = key
        rankCache = out
        return out
    }

    /** The team's best and worst areas (index, rank), leaving out depth and experience when it can. */
    private fun bestWorst(lg: League, t: Team): Pair<Pair<Int, Int>, Pair<Int, Int>>? {
        val r = ranks(lg)[t.id] ?: return null
        val play = (0 until 6)
        val best = play.minBy { r[it] }
        val worst = play.maxBy { r[it] }
        return (best to r[best]) to (worst to r[worst])
    }

    private fun rankWord(rank: Int, of: Int): String = when {
        rank == 1 -> "the best"
        rank <= of / 30 + 1 -> "elite"
        rank <= of / 6 -> "a real strength"
        else -> "their best trait"
    }

    // ---------- college ----------

    private class Col(val lg: League, val pv: Preview, val t: Team) {
        val pr = pv.proj.getValue(t.id)
        val o = pv.odds[t.id] ?: Odds(0.0, 0.0, 0.0, 0.0, 0.0, 0, 0)
        val last = t.history.lastOrNull()?.takeIf { it.season == lg.season - 1 }
        val ret = Previews.returning(lg, t)
        val st = ret.first
        val ppg = ret.second
        val star = Previews.star(lg, t)
        val starLast = star?.career?.lastOrNull()?.takeIf { it.season == lg.season - 1 && it.cls != PRO_YEAR && it.s.gp > 0 }
        val nc = Previews.newcomer(lg, t)
        val conf = lg.conf(t.confId)
        val newCoach = Previews.newCoach(lg, t)
        val coach = lg.coachOf(t)
        val streak = t.history.reversed().takeWhile { it.result in DANCE }.count()
        val drought = t.history.reversed().takeWhile { it.result !in DANCE }.count()
        val lastAp = last?.ap ?: -1
        val champ = CollegeHistory.lastChampion(lg)?.id == t.id
        /** A new league's first season has no last season to talk about. */
        val known = Previews.hasLastSeason(lg)
        val titles = CollegeHistory.titles(lg, t)
        val n = lg.teams.size
    }

    private fun Col.statLine(p: Player, l: SeasonLine): String {
        val second = when (p.pos) {
            Pos.PG -> "${f1(l.s.apg)} assists"
            Pos.SG, Pos.SF -> if (l.s.apg >= l.s.rpg * 0.8) "${f1(l.s.apg)} assists" else "${f1(l.s.rpg)} rebounds"
            else -> "${f1(l.s.rpg)} rebounds"
        }
        return "${f1(l.s.ppg)} points and $second"
    }

    /** The team's story, in a sentence or two. */
    private fun Col.lead(): String {
        val lastLine = last?.let { "${it.w}-${it.l}" } ?: ""
        return when {
            champ && !known -> pick(lg, t, 0,
                "The defending national champions, and the target on their backs.",
                "Last season ended with a net in ${poss(t.school)} hands. Everyone is coming for them now.",
            )
            champ -> pick(lg, t, 1,
                "The defending champions bring back ${starters(st)}, and the target on their backs.",
                "Last season ended with a net in ${poss(t.school)} hands. ${cap(starters(st))} ${if (st == 1) "returns" else "return"} to try it again.",
                "Nobody had a better March than ${t.school}. Now comes the hard part: doing it again with ${starters(st)} back.",
            )
            last != null && last.result in setOf("Runner-Up", "Final Four") -> pick(lg, t, 2,
                "A Final Four team last spring, ${t.school} wants the last two wins this time.",
                "Last season ended in the ${if (last.result == "Runner-Up") "title game" else "Final Four"}. ${cap(starters(st))} ${if (st == 1) "is" else "are"} back for another run.",
                "${t.school} was one of the last four standing a year ago, and ${if (st >= 3) "most of that team is back" else "it has to rebuild to get there again"}.",
            )
            newCoach -> pick(lg, t, 3,
                "Year one of the ${coach.last} era. ",
                "${coach.name} takes over. ",
                "New coach, new system: ${coach.last} runs the show now. ",
            ) + (if (last == null) "" else if (last.result in DANCE) "He inherits a $lastLine team that ${Previews.lastSeason(last.result)}."
                else "He inherits a $lastLine team that ${Previews.lastSeason(last.result)}, so expectations are his to set.")
            last != null && lastAp <= 0 && pr.nat <= 15 -> pick(lg, t, 4,
                "Unranked at the end of last season, ${t.school} opens at No. ${pr.nat}.",
                "Nobody climbed more over the summer: from outside the poll to No. ${pr.nat}.",
                "From unranked to No. ${pr.nat}. The team changed, and so did the expectations.",
            )
            lastAp > 0 && lastAp - pr.nat >= 10 -> pick(lg, t, 5,
                "Up from No. $lastAp in the final poll to No. ${pr.nat}.",
                "A ${lastAp - pr.nat}-spot jump from last season's final poll.",
            )
            lastAp in 1..12 && pr.nat - lastAp >= 10 -> pick(lg, t, 6,
                "A tumble from No. $lastAp to No. ${pr.nat}" + (if (st <= 2) ": only ${starters(st)} ${if (st == 1) "returns" else "return"}." else "."),
                "Last season's No. $lastAp team starts at ${pr.nat}" + (if (st <= 2) " after losing ${starters(5 - st)}." else "."),
            )
            st >= 4 && last != null -> pick(lg, t, 7,
                "${cap(num(st))} starters and ${f1(ppg)} points a game return from a $lastLine team.",
                "Continuity is the word: ${num(st)} starters back from last season's $lastLine group.",
                "Few teams are as settled. ${cap(num(st))} starters return, and ${f1(ppg)} points a game with them.",
            )
            known && st <= 1 && nc != null -> pick(lg, t, 8,
                "Almost everyone is new: ${if (st == 0) "no starters" else "one starter"} back, and a rebuilt rotation.",
                "A reload, not a rebuild: the portal and the freshman class replace a starting five that left.",
            )
            streak >= 5 -> pick(lg, t, 9,
                "$streak straight NCAA Tournaments, and no reason to think the streak ends.",
                "March regulars: $streak tournaments in a row.",
            )
            drought >= 4 && o.ncaa >= 0.45 -> pick(lg, t, 10,
                "$drought years without an NCAA bid. This could be the year that ends.",
                "The drought is $drought seasons long; this group says it's time.",
            )
            star != null && starLast != null && starLast.s.ppg >= 17 -> pick(lg, t, 11,
                "It's ${poss(star.name)} team.",
                "Everything runs through ${star.name}.",
                "${t.school} goes as ${star.name} goes.",
            )
            pr.nat <= 8 && titles.isEmpty() -> pick(lg, t, 17,
                "Still chasing a first national title, and this team is good enough to win one.",
                "No banner yet. This could be the team that hangs the first one.",
            )
            pr.nat <= 25 && titles.size >= 4 && lg.season - titles.last() >= 15 -> pick(lg, t, 18,
                "${CollegeHistory.count(titles.size).replaceFirstChar { it.uppercase() }}, but none since ${titles.last()}. That's a long time here.",
                "A blue blood waiting: the last of its ${titles.size} titles came in ${titles.last()}.",
            )
            pr.nat <= 25 && titles.size >= 5 -> pick(lg, t, 19,
                "Blue blood, ${num(titles.size)} banners deep (the last in ${titles.last()}), and a contender again.",
                "${CollegeHistory.count(titles.size).replaceFirstChar { it.uppercase() }}, the last in ${titles.last()}. The standard is the standard.",
            )
            o.title >= 0.10 -> pick(lg, t, 12, "A title contender from day one.", "A chance to play in April.", "The ceiling is a championship.")
            o.ncaa > 0.65 -> pick(lg, t, 13, "A likely tournament team.", "Favored to make the field, not guaranteed.", "A strong postseason case.")
            o.ncaa in 0.35..0.65 -> pick(lg, t, 14, "A bubble team that could play its way off it.", "In the mix for a bid, with little certainty.", "The bid could go either way.")
            o.conf >= 0.35 -> pick(lg, t, 15, "A contender in the ${conf.abbr}.", "A plausible winner of the ${conf.abbr}.")
            else -> pick(lg, t, 16, "A work in progress.", "Building, one season at a time.", "Looking for a breakthrough.")
        }
    }

    /** The players: the star with last season's numbers, and the newcomer to watch. [named] the lead already named the star. */
    private fun Col.people(named: Boolean = false): String? {
        val s = star ?: return null
        val who = "the ${classWord(s.cls)} ${posWord(s.pos)}"
        val starPart = when {
            starLast != null && starLast.s.ppg < 9 -> pick(lg, t, 23,
                "${s.name}, $who, is the best player on the team after a quiet ${f1(starLast.s.ppg)}-point season; his breakout is the key.",
                "The most talented player is ${s.name}, $who, who scored only ${f1(starLast.s.ppg)} a game last season. That changes now.",
            )
            starLast != null && named -> pick(lg, t, 24,
                "He averaged ${statLine(s, starLast)} last season.",
                "Last season: ${statLine(s, starLast)} a game.",
            )
            starLast != null -> pick(lg, t, 20,
                "${s.name}, $who, averaged ${statLine(s, starLast)} last season.",
                "${s.name} is back after ${statLine(s, starLast)} a game.",
                "The headliner is ${s.name}, $who who put up ${statLine(s, starLast)}.",
            )
            Previews.isTransfer(s) -> "${s.name} arrives from ${Previews.cameFrom(lg, s)} as the best player on the team."
            s.year == 1 && s.recruitRank > 0 -> "Freshman ${s.name}, the No. ${s.recruitRank} recruit, is the best player on the team from day one."
            else -> "${s.name}, $who, leads the way."
        }
        val c = nc ?: return starPart
        val ncPart = if (Previews.isTransfer(c)) pick(lg, t, 21,
            " ${c.name} comes over from ${Previews.cameFrom(lg, c)}.",
            " Add ${c.name}, a transfer from ${Previews.cameFrom(lg, c)}.",
            " ${Previews.cameFrom(lg, c)} transfer ${c.name} is the new piece.",
        ) else pick(lg, t, 22,
            " ${cap(starWord(c.stars))} freshman ${c.name} could start right away.",
            " Watch ${c.name}, the No. ${c.recruitRank} recruit in the class.",
            " Freshman ${c.name} brings ${starWord(c.stars)} talent.",
        )
        return starPart + ncPart
    }

    /** What they do best and worst, with the national ranks. */
    private fun Col.identity(): String? {
        val (b, w) = bestWorst(lg, t) ?: return null
        val (bi, br) = b
        val (wi, wr) = w
        if (br > n / 4 && wr < n * 3 / 4) return pick(lg, t, 30, "No glaring weakness, no standout strength: they're average almost everywhere.", "Balanced to a fault: nothing ranks far from the middle.")
        return when {
            wr <= n / 9 -> pick(lg, t, 32,
                "${cap(AREAS[bi])} ranks ${ord(br)} in the country, and there's no hole to attack: even their weakest area, ${AREAS[wi]}, is ${ord(wr)}.",
                "Good at everything: ${AREAS[bi]} is ${ord(br)} nationally and nothing ranks worse than ${ord(wr)}.",
            )
            wr <= n * 2 / 5 -> pick(lg, t, 33,
                "They're ${ord(br)} in the country in ${AREAS[bi]}; ${AREAS[wi]} (${ord(wr)}) is merely average.",
                "${cap(AREAS[bi])} (${ord(br)}) is the calling card, and ${AREAS[wi]} (${ord(wr)}) is the area to watch.",
            )
            else -> pick(lg, t, 31,
                "They're ${ord(br)} in the country in ${AREAS[bi]} and ${ord(wr)} in ${AREAS[wi]}.",
                "Their ${AREAS[bi]} is ${rankWord(br, n)} (${ord(br)} nationally); their ${AREAS[wi]} (${ord(wr)}) is where opponents will attack.",
                "${cap(AREAS[bi])} (${ord(br)}) is the calling card. ${cap(AREAS[wi])} (${ord(wr)}) is the worry.",
            )
        }
    }

    /** What the numbers say, worded for the team's tier. */
    private fun Col.outlook(): String {
        val wide = o.hi - o.lo >= 9
        return when {
            o.title >= 0.10 -> pick(lg, t, 40,
                "We have them ${pr.w}-${pr.l}: Final Four ${pct(o.f4)}, title ${pct(o.title)}.",
                "Their Final Four chance is ${pct(o.f4)}, and their national-title chance is ${pct(o.title)}.",
            )
            o.ncaa > 0.65 -> pick(lg, t, 41,
                "Projection: ${pr.w}-${pr.l} (${o.lo} to ${o.hi} wins), ${ord(pr.confRank)} in the ${conf.abbr}, Sweet 16 ${pct(o.s16)}.",
                "We see ${pr.w}-${pr.l} and a ${pct(o.s16)} shot at the second weekend.",
            )
            o.ncaa in 0.3..0.85 -> pick(lg, t, 42,
                "A ${pr.w}-${pr.l} projection and ${NbaPreviews.chance(o.ncaa)} at a bid: every road win matters.",
                "Bubble math: ${pr.w}-${pr.l}, ${ord(pr.confRank)} in the ${conf.abbr}, ${pct(o.ncaa)} to go dancing.",
            )
            conf.tier >= 1 && o.conf >= 0.2 -> pick(lg, t, 43,
                "${pct(o.conf)} to win the ${conf.abbr}, and that's the path to March.",
                "The ${conf.abbr} favorite at ${pct(o.conf)}; the league tournament decides the rest.",
            )
            else -> pick(lg, t, 44,
                "We have them ${pr.w}-${pr.l}, ${ord(pr.confRank)} in the ${conf.abbr}.",
                "${pr.w}-${pr.l} projected" + if (wide) ", anywhere from ${o.lo} to ${o.hi} wins." else ", a ${ord(pr.confRank)}-place finish in the ${conf.abbr}.",
            )
        } + if (wide && pr.nat <= 25) pick(lg, t, 45, "", " The range is wide: ${o.lo} to ${o.hi} wins.") else ""
    }

    /**
     * A college take of [n] sentences: the story, then (in an order that varies by team) the players,
     * the identity and the outlook. [from] 1 skips the story and starts with the rest, so a page that
     * lists the team again reads differently.
     */
    fun college(lg: League, pv: Preview, t: Team, n: Int = 3, from: Int = 0): String {
        val c = Col(lg, pv, t)
        val lead = c.lead()
        // The scout's best findings first.
        val scout = Scout.lines(lg, t, 2, skip = if (from == 0) lead else "")
        val star = c.star
        val people = c.people(from == 0 && c.star != null && lead.contains(c.star.name))?.takeIf { star == null || scout.none { it.contains(star.name) || it.contains(star.last) } }
        val rest = listOfNotNull(scout.getOrNull(0), people, scout.getOrNull(1) ?: c.identity(), c.outlook())
        val order = rest
        val parts = if (from == 0) listOf(lead) + order.take((n - 1).coerceAtLeast(0)) else order.take(n)
        return parts.joinToString(" ").replace("  ", " ").trim()
    }

    /** Why a top team can win it all, and why it might not. */
    fun collegeCase(lg: League, pv: Preview, t: Team): Pair<String, String> {
        val c = Col(lg, pv, t)
        val bw = bestWorst(lg, t)
        val r = ranks(lg)[t.id]
        val forIt = buildString {
            if (bw != null) append("${cap(AREAS[bw.first.first])} ranks ${ord(bw.first.second)} in the country")
            val second = r?.let { arr -> (0 until 6).filter { it != bw?.first?.first }.minByOrNull { arr[it] }?.let { it to arr[it] } }
            if (second != null && second.second <= c.n / 5) append(" and ${AREAS[second.first]} ${ord(second.second)}")
            append(". ")
            if (c.star != null && c.starLast != null) append(pick(lg, t, 60,
                "${c.star.name} (${c.statLine(c.star, c.starLast)}) gives them a closer. ",
                "${c.star.name} (${f1(c.starLast.s.ppg)} a game last season) can take over any night. ",
                "In a tight game the ball goes to ${c.star.name}, and he delivered ${f1(c.starLast.s.ppg)} a night last season. ",
            ))
            else if (c.star != null) append("${c.star.name} is a matchup problem every night. ")
            when {
                c.known && c.st >= 4 -> append(pick(lg, t, 61, "${cap(num(c.st))} starters back: they know who they are.", "With ${num(c.st)} starters returning, there's no learning curve.", "${cap(num(c.st))} starters have played big games together."))
                c.champ -> append("They've already done it once.")
                (r?.get(6) ?: 999) <= c.n / 10 -> append("The bench (${ord(r!![6])} in depth) means fresh legs in March.")
                c.nc != null -> append("${c.nc.name} adds a dimension last year's team didn't have.")
            }
        }.trim()
        val against = buildString {
            if (bw != null) append(if (bw.second.second <= c.n / 9) pick(lg, t, 62,
                    "There's no obvious hole: even their weakest area, ${AREAS[bw.second.first]}, ranks ${ord(bw.second.second)}. ",
                    "Nitpicking, but the ${AREAS[bw.second.first]} (${ord(bw.second.second)}) is their least dominant area. ",
                    "If there's a crack, it's the ${AREAS[bw.second.first]}, and that still ranks ${ord(bw.second.second)}. ",
                ) else pick(lg, t, 63,
                    "${cap(AREAS[bw.second.first])} ranks ${ord(bw.second.second)}, and March finds weaknesses. ",
                    "The ${AREAS[bw.second.first]} (${ord(bw.second.second)}) is the soft spot a good opponent will find. ",
                ))
            when {
                c.known && c.st <= 2 -> append("Only ${num(c.st)} starters return; chemistry takes time.")
                c.newCoach -> append("A new coach means a new system to learn.")
                (r?.get(7) ?: 0) >= c.n * 2 / 3 -> append("It's a young rotation (${ord(r!![7])} in experience).")
                c.o.title < 0.1 -> append("Even the favorites have only a ${pct(c.o.title)} title chance.")
                else -> append(pick(lg, t, 64,
                    "The single-elimination bracket doesn't care about the regular season.",
                    "One cold shooting night in March and it's over; that's the tournament.",
                    "History says the favorite usually doesn't win it all.",
                    "Six straight wins against the best teams in the country is a lot to ask of anyone.",
                ))
            }
        }.trim()
        return forIt to against
    }

    /** One line on what makes a contender dangerous, for the title race page. */
    fun collegeEdge(lg: League, pv: Preview, t: Team): String {
        val c = Col(lg, pv, t)
        val bw = bestWorst(lg, t)
        return listOfNotNull(
            c.star?.let { s -> c.starLast?.let { "${s.name}: ${f1(it.s.ppg)} ppg" } ?: s.name },
            bw?.let { "${AREAS[it.first.first]} ${ord(it.first.second)}" },
            if (c.known && c.st >= 4) "${c.st} starters back" else if (c.champ) "defending champs" else null,
        ).joinToString(" · ").replaceFirstChar { it.uppercase() }
    }

    /** The team report's bottom line: the take, the swing games and what the season comes down to. */
    fun collegeBottom(lg: League, pv: Preview, t: Team): String {
        val c = Col(lg, pv, t)
        val games = lg.games.filter { it.involves(t.id) && (it.type == GameType.CONF || it.type == GameType.NONCONF || it.type == GameType.EVENT) }
        val probs = games.map { it to Rankings.winProbFor(lg, it, t.id) }
        val flips = probs.filter { it.second in 0.38..0.62 }
        val longShots = probs.count { it.second < 0.38 }
        val biggest = flips.minByOrNull { abs(it.second - 0.5) }?.first?.let { lg.team(it.opp(t.id)) }
        val bw = bestWorst(lg, t)
        return buildString {
            append(c.lead()).append(' ')
            append("We see ${c.pr.w}-${c.pr.l} (${c.o.lo} to ${c.o.hi} wins), ${ord(c.pr.confRank)} in the ${c.conf.abbr}, with ${NbaPreviews.chance(c.o.ncaa)} at the NCAA Tournament. ")
            if (flips.isNotEmpty()) append("The season swings on ${flips.size} coin-flip ${if (flips.size == 1) "game" else "games"}" + (biggest?.let { ", none closer than ${it.school}" } ?: "") + "; ")
            append(if (longShots > 0) "$longShots more ${if (longShots == 1) "is" else "are"} long shots that would make the résumé. " else "there's no game on the schedule they can't win. ")
            if (bw != null) append(if (bw.second.second <= c.n / 9) "There's no weak spot to fix; it's about winning the close ones. " else "Fix the ${AREAS[bw.second.first]} (${ord(bw.second.second)}) and the ceiling rises. ")
            append(Previews.expectation(c.o) + ". ")
            append(CollegeHistory.line(lg, t))
            val r = Scout.report(lg, t)
            append(" The question: ${r.question} Best case, ${c.o.hi} wins if ${r.best}; worst case, ${c.o.lo} if ${r.worst}.")
        }.trim()
    }

    /** In season: the preseason pick against where the record is heading now, and why. */
    fun collegePulse(lg: League, t: Team, projW: Int, projL: Int): String? {
        val pre = lg.preview?.takeIf { it.season == lg.season }?.proj?.get(t.id) ?: return null
        if (t.gp < 4) return null
        val d = projW - pre.w
        val recent = lg.games.filter { it.played && it.involves(t.id) }.sortedBy { it.day }.takeLast(5)
        val r5 = recent.count { it.winner() == t.id }
        val why = when {
            d >= 2 && r5 >= 4 -> "$r5 wins in the last 5."
            d >= 2 -> "a ${t.w}-${t.l} start and a NET of ${t.net}."
            d <= -2 && r5 <= 1 -> "${recent.size - r5} losses in the last ${recent.size}."
            d <= -2 -> "a ${t.w}-${t.l} start and a NET of ${t.net}."
            else -> "right where we had them."
        }
        return when {
            d >= 2 -> "Picked ${pre.w}-${pre.l} in the preseason; now heading for $projW-$projL (+$d) after $why"
            d <= -2 -> "Picked ${pre.w}-${pre.l} in the preseason; now heading for $projW-$projL (${d}) after $why"
            else -> "Picked ${pre.w}-${pre.l} in the preseason, heading for $projW-$projL: $why"
        }
    }

    // ---------- NBA ----------

    private class Nba(val lg: League, val pv: NbaPreview, val t: Team) {
        val pr = pv.proj.getValue(t.id)
        val lastW = pv.lastWins[t.id]
        val lastLine = pv.last[t.id]
        val delta = lastW?.let { pr.w - it } ?: 0
        val star = NbaPreviews.star(lg, t)
        val starLast = star?.let { NbaPreviews.lastLine(lg, it) }
        val roster = lg.rosterOf(t)
        val arrivals = NbaPreviews.newHomes(lg).filter { it.first.teamId == t.id }
        val departures = lg.players.filter { p -> NbaPreviews.lastLine(lg, p)?.teamId == t.id && p.teamId != t.id && p.teamId >= 0 }
            .sortedByDescending { NbaPreviews.value(lg, it) }
        val top3 = roster.sortedByDescending { NbaPreviews.value(lg, it) }.take(3).map { it.id }.toSet()
        /** A summer arrival big enough to lead with; one worth a mention; a departure that hurts. */
        val bigArrival = arrivals.firstOrNull { NbaPreviews.value(lg, it.first) >= 98 || (it.first.id in top3 && NbaPreviews.value(lg, it.first) >= 93) }
        val newPiece = arrivals.firstOrNull { NbaPreviews.value(lg, it.first) >= 92 }
        val bigLoss = departures.firstOrNull { NbaPreviews.value(lg, it) >= 93 }
        val rookie = roster.mapNotNull { p -> NbaPreviews.draftPick(lg, p)?.let { p to it } }.minByOrNull { it.second }
        val age = roster.sortedByDescending { it.ovr }.take(8).map { it.age }.average()
        // Last season's finalists, from this league or the real past.
        private val finals = NbaHistory.finalists(lg, lg.season - 1)
        val champ = finals.first?.id == t.id
        val runnerUp = finals.second?.id == t.id
        val titles = NbaHistory.titles(lg, t)
        val lastYear = NbaHistory.last(lg, t)
        val streak = NbaHistory.playoffStreak(lg, t)
        val misses = NbaHistory.missStreak(lg, t)
        val titleRank = NbaPreviews.ranked(lg, pv).indexOf(t) + 1
        val n = lg.teams.size
    }

    private fun Nba.statLine(p: Player, l: SeasonLine) = "${f1(l.s.ppg)} points, ${f1(l.s.rpg)} rebounds and ${f1(l.s.apg)} assists"

    private fun Nba.lead(): String {
        val nick = t.name.substringAfterLast(' ')
        val a = bigArrival
        val d = bigLoss
        return when {
            champ -> pick(lg, t, 101,
                "The champions run it back${if (d == null) " with the core intact" else ", minus ${d.name}"}.",
                "Last June ended with the ${nick} holding the trophy. Repeating is harder.",
                "The ${t.name} are the team everyone measures against: they won it all last season.",
            )
            runnerUp -> pick(lg, t, 102,
                "Four wins short in the Finals, the ${nick} want the last step.",
                "A Finals loss stings all summer. The ${t.name} are back to finish the job.",
            )
            titleRank in 1..5 && pr.title >= 0.07 && (titles.isEmpty() || lg.season - titles.last() >= 25) -> pick(lg, t, 116,
                if (titles.isEmpty()) "Still chasing the franchise's first title, and this could be the group." else "${lg.season - titles.last()} years since the last title (${titles.last()}); this team could end the wait.",
                if (titles.isEmpty()) "No banner yet. This team has a real shot at the first one." else "The last parade was in ${titles.last()}. A contender again.",
            )
            titleRank == 1 -> pick(lg, t, 103,
                "The favorite, and it isn't especially close.",
                "If you're picking one team today, it's the ${t.name}.",
                "Our title favorite.",
            )
            delta >= 9 && a != null -> pick(lg, t, 104,
                "${a.first.name} changes everything: ${a.third} from the ${a.second.name}, and the projection jumps $delta wins.",
                "Up $delta wins on last season, and the reason is ${a.first.name}.",
            )
            delta >= 9 -> pick(lg, t, 105,
                "Up $delta wins on last season: the young core grew up.",
                "The ${nick} should win $delta more games than last year, and internal growth is why.",
            )
            delta <= -9 && d != null -> pick(lg, t, 106,
                "Losing ${d.name} costs them: the projection drops ${-delta} wins.",
                "Down ${-delta} wins on last season, and ${poss(d.name)} exit is most of it.",
            )
            delta <= -9 -> pick(lg, t, 107,
                "Down ${-delta} wins on last season: the team got older and thinner.",
                "The projection slides ${-delta} wins. Something has to break right.",
            )
            a != null -> pick(lg, t, 108,
                "${a.first.name} is the summer's headline: ${a.third} from the ${a.second.name}.",
                "The ${nick} landed ${a.first.name} from the ${a.second.name}.",
            )
            rookie != null && rookie.second <= 3 -> pick(lg, t, 109,
                "Everything starts with No. ${rookie.second} pick ${rookie.first.name}.",
                "The future arrived on draft night: ${rookie.first.name}, the No. ${rookie.second} pick.",
            )
            age >= 29.5 && pr.playoffs >= 0.55 -> pick(lg, t, 110,
                "The window is now: the rotation averages ${f1(age)} years old.",
                "A veteran team built for this season, not the next five.",
            )
            age <= 25.0 && pr.w <= 36 -> pick(lg, t, 111,
                "Young and patient: a ${f1(age)}-year-old rotation learning on the job.",
                "A rebuild with a plan. The kids get the minutes.",
            )
            misses >= 4 && pr.playoffs >= 0.5 -> pick(lg, t, 117,
                "$misses straight years without the playoffs, and this is the team to end it.",
                "The playoff drought is $misses seasons long; the projection says it ends now.",
            )
            streak >= 6 && pr.playoffs >= 0.6 -> pick(lg, t, 118,
                "$streak straight playoff trips and counting.",
                "A playoff team for $streak years running, and no sign of stopping.",
            )
            pr.playIn >= 0.35 -> pick(lg, t, 112, "Play-in territory.", "Somewhere between the play-in and the playoffs.", "The middle of the pack, where every win counts.")
            pr.w <= 28 -> pick(lg, t, 113, "The lottery beckons.", "A long season ahead.", "Development first, wins second.")
            pr.playoffs >= 0.8 -> pick(lg, t, 114, "A playoff lock.", "Safely in, and dangerous once there.", "A sure thing for April.")
            else -> pick(lg, t, 115, "Fighting for a spot.", "On the edge of the playoff race.", "Good enough to make noise.")
        }
    }

    private fun Nba.people(lead: String): String? {
        val s = star ?: return null
        val named = lead.contains(s.name)
        val starPart = when {
            starLast != null && starLast.s.gp >= 20 && named -> pick(lg, t, 123,
                "He put up ${statLine(s, starLast)} last season.",
                "Last season: ${f1(starLast.s.ppg)} points a night.",
            )
            starLast != null && starLast.s.gp >= 20 -> pick(lg, t, 120,
                "${s.name} put up ${statLine(s, starLast)} last season.",
                "${s.name} (${s.posLabel}, ${s.age}) is the engine: ${f1(starLast.s.ppg)} a night last season.",
                "It's ${poss(s.name)} team after ${f1(starLast.s.ppg)} points a game.",
            )
            s.proYears == 0 -> "Rookie ${s.name} is already the best player on the team."
            else -> "${s.name} (${s.posLabel}) leads the way."
        }
        val a = newPiece?.takeIf { it.first.id != s.id && !lead.contains(it.first.name) }
        val d = bigLoss?.takeIf { !lead.contains(it.name) }
        val extra = when {
            a != null -> pick(lg, t, 121, " New: ${a.first.name}, ${a.third} from the ${a.second.name}.", " ${a.first.name} joins from the ${a.second.name}.")
            d != null -> pick(lg, t, 122, " Gone: ${d.name}, now with the ${lg.team(d.teamId).name}.", " ${d.name} left for the ${lg.team(d.teamId).name}.")
            else -> ""
        }
        return starPart + extra
    }

    private fun Nba.identity(): String? {
        val (b, w) = bestWorst(lg, t) ?: return null
        return when {
            w.second <= n / 3 -> pick(lg, t, 131,
                "${cap(AREAS[b.first])} ranks ${ord(b.second)} in the league, and there's no real hole: nothing is worse than ${ord(w.second)}.",
                "Complete: ${AREAS[b.first]} is ${ord(b.second)}, and even the ${AREAS[w.first]} is ${ord(w.second)}.",
            )
            w.second <= n * 2 / 3 -> pick(lg, t, 132,
                "${cap(AREAS[b.first])} ranks ${ord(b.second)}; the ${AREAS[w.first]} (${ord(w.second)}) is middle of the pack.",
                "Strong at ${AREAS[b.first]} (${ord(b.second)}), average at ${AREAS[w.first]} (${ord(w.second)}).",
            )
            else -> pick(lg, t, 130,
                "${cap(AREAS[b.first])} ranks ${ord(b.second)} in the league, ${AREAS[w.first]} ${ord(w.second)}.",
                "Their ${AREAS[b.first]} is ${rankWord(b.second, n)} (${ord(b.second)}); the ${AREAS[w.first]} (${ord(w.second)}) is the hole.",
                "Strong at ${AREAS[b.first]} (${ord(b.second)}), exposed at ${AREAS[w.first]} (${ord(w.second)}).",
            )
        }
    }

    private fun Nba.outlook(): String = when {
        pr.title >= 0.12 -> pick(lg, t, 140,
            "${pr.w}-${pr.l} projected, Finals ${pct(pr.finals)}, title ${pct(pr.title)}.",
            "Projected ${pr.w} wins, with a ${pct(pr.finals)} Finals chance and a ${pct(pr.title)} title chance.",
        )
        pr.playoffs >= 0.75 -> pick(lg, t, 141,
            "${pr.w}-${pr.l} (${pr.lo} to ${pr.hi} wins), about the ${ord(pr.seed.roundToInt())} seed, playoffs ${pct(pr.playoffs)}.",
            "We have them ${pr.w}-${pr.l} and in the playoffs ${pct(pr.playoffs)} of the time.",
        )
        pr.playoffs >= 0.3 -> pick(lg, t, 142,
            "${pr.w}-${pr.l}, around the ${ord(pr.seed.roundToInt())} seed: playoffs ${pct(pr.playoffs)}, play-in ${pct(pr.playIn)}.",
            "A ${pr.w}-win team on paper, in the playoffs ${pct(pr.playoffs)} of our seasons.",
        )
        else -> pick(lg, t, 143,
            "${pr.w}-${pr.l} projected; ${pct(pr.playoffs)} to sneak into the playoffs.",
            "We see ${pr.w} wins (${pr.lo} to ${pr.hi}) and a lottery ticket.",
        )
    }

    /** An NBA take of [n] sentences; [from] 1 skips the story (for a second listing of the same team). */
    fun nba(lg: League, pv: NbaPreview, t: Team, n: Int = 3, from: Int = 0): String {
        val c = Nba(lg, pv, t)
        val lead = c.lead()
        // The scout's best findings come first: what actually decides this team's season.
        val scout = Scout.lines(lg, t, 2, skip = if (from == 0) lead else "")
        val star = c.star
        val people = c.people(if (from == 0) lead else "")?.takeIf { star == null || scout.none { it.contains(star.name) || it.contains(star.last) } }
        val rest = listOfNotNull(scout.getOrNull(0), people, scout.getOrNull(1) ?: c.identity(), c.outlook())
        val order = rest
        val parts = if (from == 0) listOf(lead) + order.take((n - 1).coerceAtLeast(0)) else order.take(n)
        return parts.joinToString(" ").replace("  ", " ").trim()
    }

    /** One line on a contender, for the title race page. */
    fun nbaEdge(lg: League, pv: NbaPreview, t: Team): String {
        val c = Nba(lg, pv, t)
        val bw = bestWorst(lg, t)
        return listOfNotNull(
            c.star?.let { s -> c.starLast?.takeIf { it.s.gp >= 20 }?.let { "${s.name}: ${f1(it.s.ppg)} ppg" } ?: s.name },
            bw?.let { "${AREAS[it.first.first]} ${ord(it.first.second)}" },
            c.newPiece?.takeIf { it.first.id != c.star?.id }?.let { "added ${it.first.name}" } ?: if (c.champ) "defending champs" else null,
        ).joinToString(" · ").replaceFirstChar { it.uppercase() }
    }

    /** The team report's bottom line for an NBA team. */
    fun nbaBottom(lg: League, pv: NbaPreview, t: Team): String {
        val c = Nba(lg, pv, t)
        val games = lg.games.filter { it.involves(t.id) && (it.type == GameType.CONF || it.type == GameType.NONCONF) }
        val probs = games.map { Rankings.winProbFor(lg, it, t.id) }
        val flips = probs.count { it in 0.42..0.58 }
        val favored = probs.count { it >= 0.58 }
        val bw = bestWorst(lg, t)
        return buildString {
            append(c.lead()).append(' ')
            append("We see ${c.pr.w}-${c.pr.l} (${c.pr.lo} to ${c.pr.hi} wins), about the ${ord(c.pr.seed.roundToInt())} seed in the ${lg.conf(t.confId).name}, with ${NbaPreviews.chance(c.pr.playoffs)} at the playoffs and ${NbaPreviews.chance(c.pr.title)} at the title. ")
            append("They're favored in $favored of ${games.size} games; $flips are coin flips. ")
            if (bw != null) append(if (bw.second.second <= c.n / 3) "There's no weak spot to shore up, so it comes down to health and the matchups. "
                else "Shore up the ${AREAS[bw.second.first]} (${ord(bw.second.second)}) and those flips start breaking their way. ")
            c.star?.let { append(pick(lg, t, 160, "It goes as far as ${it.name} takes it.", "${it.name} sets the ceiling.", "Keep ${it.name} healthy and anything's possible.")) }
            val r = Scout.report(lg, t)
            append(" The question: ${r.question} Best case, ${c.pr.hi} wins if ${r.best}; worst case, ${c.pr.lo} if ${r.worst}.")
        }.trim()
    }

    /** In season: how the projection has moved since the preseason, and why. */
    fun nbaPulse(lg: League, t: Team): String? {
        val ps = lg.proState ?: return null
        val pre = ps.preview?.takeIf { it.season == lg.season }?.proj?.get(t.id) ?: return null
        val now = NbaPreviews.latest(lg)[t.id] ?: return null
        val games = lg.games.filter { it.played && it.involves(t.id) && (it.type == GameType.CONF || it.type == GameType.NONCONF) }.sortedBy { it.day }
        if (games.size < 5) return null
        val d = now.w - pre.w
        val diff = games.sumOf { it.scoreFor(t.id) - it.scoreAgainst(t.id) }.toDouble() / games.size
        val last10 = games.takeLast(10)
        val l10 = last10.count { it.winner() == t.id }
        val hot = lg.rosterOf(t).filter { it.stats.gp >= 4 }.maxByOrNull { it.stats.ppg }
        val hotLast = hot?.let { NbaPreviews.lastLine(lg, it) }
        val hotNote = if (hot != null && (hotLast == null || hot.stats.ppg >= hotLast.s.ppg + 3)) " ${hot.name} is scoring ${f1(hot.stats.ppg)} a night." else ""
        val sign = if (diff >= 0) "+" else "−"
        return when {
            d >= 3 -> "Up $d wins on the preseason projection (${pre.w} to ${now.w}). " + when {
                diff >= 4 -> "A $sign${f1(abs(diff))} point differential says it's no fluke."
                l10 >= 7 -> "They've won $l10 of their last ${last10.size}."
                else -> "Close games have gone their way: ${t.w}-${t.l} on a $sign${f1(abs(diff))} differential."
            } + hotNote
            d <= -3 -> "Down ${-d} wins from the preseason projection (${pre.w} to ${now.w}). " + when {
                l10 <= 3 -> "They've lost ${last10.size - l10} of their last ${last10.size}."
                diff < 0 -> "They're being outscored by ${f1(-diff)} a game."
                else -> "The record trails the point differential ($sign${f1(abs(diff))}), so better luck may be coming."
            }
            else -> "Right on the preseason projection (${pre.w} wins). $l10-${last10.size - l10} in the last ${last10.size}, $sign${f1(abs(diff))} a game." + hotNote
        }
    }
}
