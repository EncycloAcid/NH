package com.nh.hoopscoach.game

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/** One pundit's side of the debate. */
@Serializable
class TvTake(val name: String, val handle: String, val avatar: String = "", val verified: Boolean = false, val side: String, val lines: List<String>)

/** This week's segment: the show, the question, the two takes, and how the user answered (-1: not yet). */
@Serializable
class TvSegment(val season: Int, val day: Int, val show: String, val topic: String, val takes: List<TvTake>, val responses: List<String>, var answered: Int = -1)

/**
 * The weekly sports-TV debate. Once a week in season, two pundits argue about you: your player in a player
 * career ("Is he a top-10 player?", "Is he the problem?"), your team and your job as a coach ("Are they
 * for real?", "Hot seat?"). Their takes use the real numbers. You can laugh it off, let your play talk,
 * or fire back, and each lands differently with fans, teammates and the bosses.
 */
object TvShow {
    fun showName(lg: League) = if (lg.pro) "Full Court Press" else "Hardwood Live"

    fun daily(lg: League) {
        if (lg.phase != Phase.REGULAR && lg.phase != Phase.PLAYOFFS) return
        if (lg.day % 7 != 4) return
        val t = lg.user
        if (t.w + t.l < 5) return
        val seg = (if (lg.playerMode) heroSegment(lg) else coachSegment(lg)) ?: return
        lg.tv = seg
        val (a, b) = seg.takes
        lg.news += News(lg.season, lg.day, "${seg.show}: ${seg.topic}", true, -1, "tv",
            "${a.name}: \"${a.lines.first()}\" ${b.name}: \"${b.lines.first()}\"", seg.show, t.id, if (lg.playerMode) lg.heroId else -1)
    }

    private fun voices(lg: League): Pair<Media.Voice, Media.Voice> {
        val a = Media.pundit(lg)
        var b = Media.pundit(lg)
        var guard = 0
        while (b.name == a.name && guard++ < 10) b = Media.pundit(lg)
        return a to b
    }

    private fun f1(d: Double) = "%.1f".format(d)

    private fun heroSegment(lg: League): TvSegment? {
        val h = lg.hero ?: return null
        val t = lg.team(h.teamId)
        val s = h.stats
        if (s.gp < 5) return null
        val scorers = lg.players.filter { it.teamId >= 0 && it.stats.gp >= 5 }.sortedByDescending { it.stats.ppg }
        val rank = scorers.indexOfFirst { it.id == h.id } + 1
        val last5 = h.log.takeLast(5)
        val recent = if (last5.size == 5) last5.sumOf { it.pts } / 5.0 else s.ppg
        val line = "${f1(s.ppg)} points, ${f1(s.rpg)} rebounds and ${f1(s.apg)} assists"
        val team = if (lg.pro) "the ${t.name}" else t.school
        val (topic, pro, con) = when {
            recent < s.ppg - 4 -> Triple("What's wrong with ${h.last}?",
                listOf("Every scorer slumps. ${f1(s.ppg)} a game on the season, he'll be fine.", "Look at the shots he's getting. They're good looks. They'll fall."),
                listOf("${f1(recent)} a night over his last five. That's not a slump, that's a trend.", "Teams figured him out. Now what's the counter?"))
            rank in 1..10 -> Triple("Is ${h.last} a top-10 player right now?",
                listOf("$line. He's No. $rank in the league in scoring. Yes. Next question.", "Ask the coaches who have to game-plan for him."),
                listOf("Top 10 is a club. You don't get in on one good stretch.", "Show me it in the playoffs, then we'll talk."))
            t.winPct < 0.42 && s.ppg >= 15 -> Triple("Is ${h.last} the problem for $team?",
                listOf("Numbers on a losing team. ${t.record}. Somebody has to be accountable.", "Empty calories: $line."),
                listOf("The problem? He's the only reason they're not worse.", "Get him some help and see what happens."))
            h.proYears == 0 && lg.pro || h.year == 1 && !lg.pro -> Triple("Is Rookie of the Year ${h.last}'s to lose?",
                listOf("$line as a rookie. It's his trophy to lose.", "Poise, numbers, and $team is ${t.record}."),
                listOf("Long season. Rookies hit the wall.", "I've got two other kids I'd vote for today."))
            t.winPct >= 0.6 -> Triple("Is ${h.last} the reason $team is ${t.record}?",
                listOf("Watch the fourth quarters. It's him.", "$line on a winner. That's value."),
                listOf("It's the system and the depth, not one guy.", "Put him on a bad team and see what those numbers look like."))
            else -> Triple("Is ${h.last} overrated?",
                listOf("$line. Overrated? Come on.", "People just like to hate the guy with the hype."),
                listOf("The highlights are great. The impact isn't there yet.", "Followers aren't wins."))
        }
        val (a, b) = voices(lg)
        return TvSegment(lg.season, lg.day, showName(lg), topic,
            listOf(TvTake(a.name, a.handle, a.avatar, a.verified, "Yes", pro), TvTake(b.name, b.handle, b.avatar, b.verified, "No", con)),
            listOf("Laugh it off", "Let my play do the talking", "Fire back"))
    }

    private fun coachSegment(lg: League): TvSegment? {
        val t = lg.user
        val c = lg.userCoach
        val team = if (lg.pro) "the ${t.name}" else t.school
        val star = lg.rosterOf(t).maxByOrNull { it.stats.ppg }
        val starLine = star?.let { "${it.name} at ${f1(it.stats.ppg)} a night" } ?: "their best player"
        val (topic, pro, con) = when {
            t.winPct >= 0.65 -> Triple("Are $team for real?",
                listOf("${t.record}. That's not luck, that's a team.", "Coach ${c.last} has them defending every night."),
                listOf("Wait till the schedule gets hard.", "Ride $starLine only so far."))
            t.winPct <= 0.4 -> Triple("Is Coach ${c.last}'s seat getting hot?",
                listOf("${t.record}. In this business, that gets people fired.", "Something has to change, and it usually starts at the top."),
                listOf("Give the man a roster first.", "He's got $starLine and not much else. That's not on him."))
            else -> Triple("Can $team make a run?",
                listOf("${t.record} and trending up. Don't sleep on them.", "Coach ${c.last} always has a plan for March."),
                listOf("They're fine. Fine doesn't win anything.", "No closer. Who takes the last shot?"))
        }
        val (a, b) = voices(lg)
        return TvSegment(lg.season, lg.day, showName(lg), topic,
            listOf(TvTake(a.name, a.handle, a.avatar, a.verified, "Yes", pro), TvTake(b.name, b.handle, b.avatar, b.verified, "No", con)),
            listOf("Laugh it off", "Let the team do the talking", "Fire back"))
    }

    /** The user's answer. */
    fun respond(lg: League, choice: Int) {
        val seg = lg.tv ?: return
        if (seg.answered >= 0 || choice !in seg.responses.indices) return
        seg.answered = choice
        val r = lg.road
        if (lg.playerMode && r != null) {
            val h = lg.hero ?: return
            when (choice) {
                0 -> { r.persona -= 1; r.followers = (r.followers * 1.01).roundToInt() }
                1 -> { r.leadership = (r.leadership + 1).coerceAtMost(100) }
                else -> { r.persona += 2; r.followers = (r.followers * 1.04).roundToInt() }
            }
            val quote = when (choice) {
                0 -> "I saw it. I laughed. We've got a game tomorrow."
                1 -> "They get paid to talk. I get paid to hoop."
                else -> "Tell ${seg.takes[1].name} to come guard me."
            }
            lg.news += News(lg.season, lg.day, "${h.name} answers ${seg.show}", true, -1, "tv", "\"$quote\"", seg.show, h.teamId, h.id)
        } else {
            val c = lg.userCoach
            val t = lg.user
            when (choice) {
                0 -> c.security = (c.security + 1).coerceIn(0, 100)
                1 -> lg.rosterOf(t).forEach { it.happiness = (it.happiness + 2).coerceAtMost(99) }
                else -> { lg.rosterOf(t).forEach { it.happiness = (it.happiness + 3).coerceAtMost(99) }; c.security = (c.security - 1).coerceIn(0, 100) }
            }
            val quote = when (choice) {
                0 -> "I don't watch that stuff. Somebody told me. It's fine."
                1 -> "Our guys will answer it on the floor."
                else -> "${seg.takes[1].name} has never coached a game in his life."
            }
            lg.news += News(lg.season, lg.day, "Coach ${c.last} answers ${seg.show}", true, -1, "tv", "\"$quote\"", seg.show, t.id, -1)
        }
    }
}
