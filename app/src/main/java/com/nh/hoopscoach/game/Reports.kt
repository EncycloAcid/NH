package com.nh.hoopscoach.game

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Magazine-style copy for awards, championships and season reviews. Built from the actual season
 * so two 28-win titles don't read the same, and an MVP who scored 24 a night doesn't get the same
 * blurb as one who ran the offense at 18 and 8.
 */
object Reports {

    /** A short citation stored on the award itself (survives into history). */
    fun awardLine(award: String, p: Player, t: Team): String {
        val s = p.stats
        val stats = "%.1f / %.1f / %.1f".format(s.ppg, s.rpg, s.apg)
        val lead = when (award) {
            "Nat MVP", "Conf POY" -> when {
                s.ppg >= 22 && s.apg >= 5 -> "Averaged a near triple-double threat at %.0f-%.0f-%.0f".format(s.ppg, s.rpg, s.apg)
                s.ppg >= 22 -> "Poured in %.1f a night for a %d-win %s".format(s.ppg, t.w, t.school)
                s.apg >= 7 -> "Ran the show at %.1f assists for %s".format(s.apg, t.school)
                t.w >= 28 -> "The engine of a %d-win machine".format(t.w)
                t.ap in 1..5 -> "Star of a top-five team"
                else -> "Clear choice after a %d-%d season".format(t.w, t.l)
            }
            "DPOY", "Conf DPOY" -> when {
                s.bpg >= 2.5 -> "Erased %.1f shots a game at the rim".format(s.bpg)
                s.spg >= 2.0 -> "Lived in the passing lanes (%.1f steals)".format(s.spg)
                else -> "The stopper on a %d-win defense".format(t.w)
            }
            "FOY", "Conf FOY" -> when {
                s.ppg >= 16 -> "Freshman averaged %.1f points from day one".format(s.ppg)
                p.stars >= 5 -> "Lived up to the five-star billing"
                else -> "The best first-year player in the field"
            }
            "MOP" -> {
                val m = p.tourney
                "Averaged %.1f points in March for the champions".format(m.ppg)
            }
            "AA 1st", "AA 2nd", "AA 3rd", "All-Conf 1st" -> when {
                s.ppg >= 18 -> "%.1f ppg on a %d-%d team".format(s.ppg, t.w, t.l)
                s.apg >= 6 -> "%.1f apg floor general".format(s.apg)
                s.rpg >= 9 -> "%.1f rebounds a night".format(s.rpg)
                else -> "%s · %s".format(p.pos, stats)
            }
            "All-Tourn" -> "%.1f ppg in the NCAA Tournament".format(p.tourney.ppg)
            "Pos OY" -> "Best %s in the country · %s".format(p.pos, stats)
            else -> stats
        }
        return if (award in setOf("AA 1st", "AA 2nd", "AA 3rd", "All-Conf 1st", "All-Tourn", "Pos OY")) {
            "$lead"
        } else {
            "$lead · $stats"
        }
    }

    /** Coach award line from the team's season and how far it went in March. */
    fun coachAwardLine(t: Team, round: Int): String {
        val over = (t.w - t.expWins).roundToInt()
        return when {
            round >= 5 -> "Took %s to the %s (%s)".format(t.school, Awards.resultLabel(round).lowercase(), t.record)
            over >= 5 -> "%d wins over expectation at %s (%s)".format(over, t.school, t.record)
            t.w >= 28 -> "Built a %d-win machine at %s".format(t.w, t.school)
            else -> "%s · %s".format(t.school, t.record)
        }
    }

    /** Lead paragraph for the national championship. */
    fun champReport(lg: League, champ: Team, runner: Team, titleGame: Game?, mop: AwardWinner?): String {
        val ws = lg.ncaa?.seeds?.get(champ.id) ?: 0
        val ls = lg.ncaa?.seeds?.get(runner.id) ?: 0
        val score = titleGame?.let { "${it.scoreFor(champ.id)}-${it.scoreFor(runner.id)}" }
        val margin = titleGame?.let { abs(it.hs - it.aws) } ?: 0
        val ot = titleGame?.ot ?: 0
        val open = when {
            ws >= 8 -> "In one of the rarer title runs in recent memory, No. $ws ${champ.school}"
            ws in 1..3 && ls in 1..3 -> "No. $ws ${champ.school}"
            ws > 0 -> "No. $ws ${champ.school}"
            else -> champ.school
        }
        val verb = when {
            ot > 0 -> "outlasted"
            margin >= 18 -> "routed"
            margin <= 3 -> "edged"
            ws - ls >= 5 -> "stunned"
            else -> "beat"
        }
        val foe = if (ls > 0) "No. $ls ${runner.school}" else runner.school
        val scoreBit = score?.let { " $it" } ?: ""
        val otBit = if (ot > 0) " in ${if (ot > 1) "${ot}OT" else "overtime"}" else ""
        val mopBit = mop?.let { " ${it.name} took Most Outstanding Player after ${it.line.substringBefore(" · ").replaceFirstChar { c -> c.lowercase() }}." } ?: ""
        val recordBit = " The ${champ.mascot} finished ${champ.w}-${champ.l}${if (champ.ap > 0) " and were ranked No. ${champ.ap} in the final poll" else ""}."
        return "$open $verb $foe$scoreBit$otBit to cut down the nets.$mopBit$recordBit"
    }

    /** Lead for the user's season review. */
    fun wrapLead(lg: League, w: WrapUp): String {
        val t = lg.team(w.teamId)
        val diff = w.w - w.expWins
        val march = when {
            w.result == "National Champion" -> "and capped it by winning the national title"
            w.result == "Runner-Up" -> "and played for the national title"
            w.result == "Final Four" -> "and reached the Final Four"
            w.result == "Elite Eight" -> "and made the Elite Eight"
            w.result == "Sweet 16" -> "and danced into the Sweet 16"
            w.event == "NCAA" && w.seed > 0 -> "as a No. ${w.seed} seed that ${w.result.lowercase()}"
            w.result == "NIT Champion" -> "then won the NIT after missing the field"
            w.event == "NIT" -> "and settled for the NIT (${w.result.removePrefix("NIT ").lowercase()})"
            else -> "but missed the NCAA Tournament"
        }
        val pace = when {
            diff >= 5 -> "blew past a %.0f-win projection".format(w.expWins)
            diff >= 2 -> "beat the preseason number"
            diff > -2 -> "played about to expectation"
            diff > -5 -> "came in under the preseason number"
            else -> "had a year that got away from them"
        }
        val honors = when {
            w.awards.any { it.award == "Nat MVP" } -> " Their star took national player of the year."
            w.awards.any { it.award == "AA 1st" } -> " They put a player on the All-America first team."
            w.coachAwards.isNotEmpty() -> " ${w.coachAwards.first()} headed to the sideline."
            w.awards.isNotEmpty() -> " The program collected ${w.awards.size} individual honor${if (w.awards.size == 1) "" else "s"}."
            else -> ""
        }
        return "${t.school} went ${w.w}-${w.l}, $pace, $march.$honors"
    }

    /** History-card summary for a finished season. */
    fun seasonCard(lg: League, s: SeasonSummary): String {
        val champ = lg.team(s.champion)
        val runner = lg.team(s.runnerUp)
        val mvp = s.awards.firstOrNull { it.award == "Nat MVP" }
        val mop = s.awards.firstOrNull { it.award == "MOP" }
        val bits = mutableListOf("${champ.school} over ${runner.school}")
        mvp?.let { bits += "${it.name} was player of the year" }
        mop?.let { bits += "${it.name} was Most Outstanding Player" }
        if (s.cotyName.isNotEmpty()) bits += "${s.cotyName} took coach of the year"
        return bits.joinToString(" · ") + "."
    }

    /** Dynamic national-title news / moment headline. */
    fun titleHeadline(champ: Team, runner: Team, ws: Int, ls: Int, margin: Int, ot: Int): String = when {
        ot > 0 -> "${champ.school} wins the title in ${if (ot > 1) "${ot}OT" else "overtime"}"
        ws >= 8 -> "Cinderella crowned: No. $ws ${champ.school} wins it all"
        margin >= 20 -> "${champ.school} romps to the national title"
        ws in 1..2 && ls in 1..2 -> "No. $ws ${champ.school} edges No. $ls ${runner.school} for the title"
        else -> "${champ.school} cuts down the nets"
    }

    fun titleDetail(champ: Team, runner: Team, g: Game, ws: Int, ls: Int, mopName: String?): String {
        val score = "${g.scoreFor(champ.id)}-${g.scoreFor(runner.id)}"
        val foe = if (ls > 0) "No. $ls ${runner.school}" else runner.school
        val seed = if (ws > 0) "No. $ws " else ""
        val ot = if (g.ot > 0) " in ${if (g.ot > 1) "${g.ot}OT" else "overtime"}" else ""
        val mop = mopName?.let { " $it was named Most Outstanding Player." } ?: ""
        return "$seed${champ.school} beat $foe $score$ot for the national championship.$mop"
    }
}
