package com.nh.hoopscoach.game

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/** One of the boss's goals for the season: what, the number, and (at season's end) whether he met it. */
@Serializable
class Goal(val kind: String, val target: Int, val label: String, val season: Int, var met: Int = -1, val pid: Int = -1)

/**
 * The midseason meeting. At the All-Star break (NBA) or the start of conference play (college) the
 * owner or athletic director calls the coach in, says what he sees, and sets three goals for the rest
 * of the year. How the coach answers sets the stakes; at season's end each goal met adds to his job
 * security and each one missed takes away.
 */
object FrontOffice {

    private fun boss(lg: League) = if (lg.pro) "owner" else "athletic director"

    /** Wins the team is expected to finish with. */
    private fun expected(lg: League, t: Team): Int = if (lg.pro) (lg.proState?.expect?.takeIf { it >= 0 } ?: ProLeague.seasonGames(lg) / 2)
    else t.expWins.roundToInt().coerceAtLeast(1)

    private fun totalGames(lg: League, t: Team) = if (lg.pro) ProLeague.seasonGames(lg) else lg.games.count { it.involves(t.id) && it.type in setOf(GameType.NONCONF, GameType.CONF, GameType.EVENT) }

    fun meeting(lg: League) {
        if (lg.playerMode || lg.road != null) return
        val t = lg.user
        if (lg.goals.any { it.season == lg.season }) return
        val games = totalGames(lg, t).coerceAtLeast(1)
        val played = (t.w + t.l).coerceAtLeast(1)
        val pace = (t.w.toDouble() / played * games).roundToInt()
        val exp = expected(lg, t)
        val goals = mutableListOf<Goal>()
        // Wins: what he expected, nudged by how it's going.
        val winTarget = ((exp + pace) / 2.0).roundToInt().coerceIn(t.w, games)
        goals += Goal("wins", winTarget, "Win $winTarget games", lg.season)
        // The postseason.
        goals += if (lg.pro) {
            if (exp >= games * 0.45 || pace >= games * 0.5) Goal("playoffs", 0, "Make the playoffs", lg.season)
            else Goal("playin", 0, "Get into the play-in", lg.season)
        } else {
            if (t.prestige >= 58 || pace >= games * 0.62) Goal("ncaa", 0, "Make the NCAA Tournament", lg.season)
            else Goal("conf500", 0, "Finish .500 or better in conference", lg.season)
        }
        // One more, about the program.
        val young = lg.rosterOf(t).filter { if (lg.pro) it.age <= 22 else it.year <= 2 }.filter { it.pot - it.ovr >= 6 }.maxByOrNull { it.pot }
        val rival = lg.rivalsOf(t.id).firstOrNull()?.let { r -> lg.team(r.other(t.id)) }
            ?.takeIf { r -> lg.games.any { g -> !g.played && g.involves(t.id) && g.involves(r.id) } }
        goals += when {
            young != null && Rng.chance(0.45) -> Goal("youth", 20, "Play ${young.name} 20 minutes a night", lg.season, pid = young.id)
            rival != null && Rng.chance(0.6) -> Goal("rival", rival.id, "Beat ${if (lg.pro) "the ${rival.name}" else rival.school}", lg.season)
            else -> Goal("crowd", 85, "Fill the building (85% average)", lg.season)
        }
        lg.goals.removeAll { it.season != lg.season }
        lg.goals += goals
        lg.goalStake = 4

        val bossName = "${Rng.pick(Names.first)} ${Rng.pick(Names.last)}"
        val read = mutableListOf<String>()
        read += "${t.record} at the break. " + when {
            pace >= exp + 4 -> "\"I'll be honest, this is better than I expected. Now don't let it slip.\""
            pace <= exp - 4 -> "\"This isn't what we talked about in the summer. I need to see a different team.\""
            else -> "\"About what we expected. I want more than 'expected.'\""
        }
        read += "On pace for $pace wins; the ${boss(lg)} expected about $exp."
        read += "What I want from the rest of the season:\n" + goals.joinToString("\n") { "• ${it.label}" }
        val choices = listOf("We'll get there.", "Be patient with us.", "I need help.")
        val quotes = listOf(
            "You'll get all three. Hold me to it.",
            "We're building something. Some of this takes longer than half a season.",
            "I can get there, but not with what I have right now. I need more resources.",
        )
        val effects = listOf(
            "High stakes: each goal is worth ±6 job security at season's end.",
            "Lower stakes: each goal is worth ±3.",
            if (lg.pro) "Lower stakes (±3), and $5M more for the building budget." else "Lower stakes (±3), and $100K more in NIL money from the AD.",
        )
        val replies = listOf(
            listOf(Tribute(bossName, "The ${boss(lg)}", "That's what I wanted to hear. I'm holding you to it.")),
            listOf(Tribute(bossName, "The ${boss(lg)}", "Patience I can give you. Not forever.")),
            listOf(Tribute(bossName, "The ${boss(lg)}", "Fine. You'll have it. Now there are no excuses.")),
        )
        val labels = listOf("RECORD", "PACE", "EXPECTED")
        val values = listOf(t.record, "$pace", "$exp")
        Farewell.show(lg, Ceremony(
            "meeting", "MIDSEASON MEETING · ${lg.season}", if (lg.pro) "THE OWNER'S OFFICE" else "THE AD'S OFFICE", "$bossName · ${boss(lg).replaceFirstChar { it.uppercase() }}",
            t.id, 0, labels, values, "What he sees", read, emptyList(), emptyList(), choices, quotes, replies,
            choiceTitle = "Your answer", choicePrompt = "He's waiting. What do you tell him?", effects = effects,
        ))
        lg.news += News(lg.season, lg.day, "${lg.userCoach.name} meets with the ${boss(lg)} at the break", true, -1, "", read.joinToString("\n\n"), "", t.id)
    }

    /** His answer: the stakes, and the help he asked for. */
    fun answer(lg: League, choice: Int) {
        val t = lg.user
        lg.goalStake = if (choice == 0) 6 else 3
        if (choice == 2) {
            if (lg.pro) { Business.ensure(lg, t); t.cash += 5_000 }
            else t.bonuses += NilBonus("${lg.season} AD's emergency fund for ${t.school}", 100, 1)
        }
    }

    /** Where a goal stands right now: done, on track, or behind. */
    fun progress(lg: League, g: Goal): Pair<String, Boolean> {
        val t = lg.user
        val ps = lg.proState
        return when (g.kind) {
            "wins" -> {
                val games = totalGames(lg, t).coerceAtLeast(1)
                val pace = (t.w.toDouble() / (t.w + t.l).coerceAtLeast(1) * games).roundToInt()
                "${t.w} wins, on pace for $pace" to (pace >= g.target)
            }
            "playoffs" -> { val seed = seedNow(lg, t); "${ordinal(seed)} in the conference" to (seed in 1..6) }
            "playin" -> { val seed = seedNow(lg, t); "${ordinal(seed)} in the conference" to (seed in 1..10) }
            "ncaa" -> "${t.record}, No. ${t.net.takeIf { it > 0 } ?: "—"} in the NET" to (t.net in 1..45)
            "conf500" -> "${t.cw}-${t.cl} in conference" to (t.cw >= t.cl)
            "youth" -> lg.playerOrNull(g.pid)?.let { p -> "${"%.1f".format(p.stats.mpg)} minutes a night" to (p.stats.mpg >= g.target) } ?: ("He's gone" to false)
            "rival" -> {
                val beat = lg.games.any { it.played && it.involves(t.id) && it.involves(g.target) && it.winner() == t.id && it.day >= 0 }
                (if (beat) "Done" else "Still to play") to beat
            }
            "crowd" -> "${(Fans.avgFill(t) * 100).roundToInt()}% full" to (Fans.avgFill(t) * 100 >= g.target)
            else -> "" to false
        }.let { (text, ok) -> if (ps == null && g.kind in setOf("playoffs", "playin")) text to ok else text to ok }
    }

    private fun seedNow(lg: League, t: Team): Int = runCatching { ProSeason.standings(lg, t.confId).indexOf(t) + 1 }.getOrDefault(0)
    private fun ordinal(n: Int) = Previews.ordinal(n.coerceAtLeast(1))

    /** Season over: each goal met or missed, the job security that comes with it, and the verdict in the news. */
    fun grade(lg: League): Int {
        val t = lg.user
        val mine = lg.goals.filter { it.season == lg.season && it.met < 0 }
        if (mine.isEmpty()) return 0
        val ps = lg.proState
        for (g in mine) {
            g.met = if (when (g.kind) {
                    "wins" -> t.w >= g.target
                    "playoffs" -> ps?.series?.any { it.round >= 1 && it.involves(t.id) } == true
                    "playin" -> ps?.series?.any { it.involves(t.id) } == true || (ps?.seeds?.get(t.id) ?: 99) <= 10
                    "ncaa" -> Awards.tourneyRound(lg, t.id) >= 1
                    "conf500" -> t.cw >= t.cl
                    "youth" -> (lg.playerOrNull(g.pid)?.stats?.mpg ?: 0.0) >= g.target
                    "rival" -> lg.games.any { it.played && it.involves(t.id) && it.involves(g.target) && it.winner() == t.id }
                    "crowd" -> Fans.avgFill(t) * 100 >= g.target
                    else -> false
                }) 1 else 0
        }
        val met = mine.count { it.met == 1 }
        val delta = (met - (mine.size - met)) * lg.goalStake
        val c = lg.userCoach
        c.security = (c.security + delta).coerceIn(0, 100)
        lg.news += News(lg.season, lg.day, "The ${boss(lg)}'s verdict: $met of ${mine.size} goals met (job security ${if (delta >= 0) "+" else "−"}${kotlin.math.abs(delta)})", true, -1, "",
            mine.joinToString("\n") { "${if (it.met == 1) "✓" else "✗"} ${it.label}" }, "", t.id)
        return delta
    }
}
