package com.nh.hoopscoach.game

import kotlin.math.roundToInt

/**
 * Legacy mode, the father as a person: he calls after the big nights and the bad ones, and before the big
 * games, and how his son answers moves their bond. The bond decides how much it lifts the son to see him in
 * the stands (and, when it's strained, whether he makes the trip). Once a season, before the first games, he
 * works out with his son on one of his own best skills.
 */
object Family {
    /** One way to answer: what it does to the bond, and to him. [prep] is a film session for the next game. */
    class Answer(
        val text: String, val bond: Int, val mood: Int = 0, val prep: Boolean = false, val fatigue: Int = 0,
        val followers: Double = 0.0, val reaction: String,
    )

    /** A call from his father: why he's calling, what he says, and the answers. [stamp] keeps it to one per occasion. */
    class Call(val kind: String, val title: String, val text: String, val answers: List<Answer>, val stamp: Int)

    fun father(lg: League) = LegacyMode.father(lg)

    fun active(lg: League): Boolean {
        val h = lg.hero ?: return false
        return lg.playerMode && father(lg) != null && lg.road?.ended != true && h.teamId >= 0 && !(lg.road?.longWay ?: false)
    }

    private fun inSeason(lg: League) = lg.phase == Phase.REGULAR || lg.phase == Phase.CONF_TOURNEY || lg.phase == Phase.NCAA || lg.phase == Phase.PLAYOFFS

    /** "Close", "Good", "Distant" or "Strained". */
    fun bondLabel(b: Int) = when { b >= 80 -> "Close"; b >= 55 -> "Good"; b >= 35 -> "Distant"; else -> "Strained" }

    /** The next game on his team's schedule. */
    fun nextGame(lg: League): Game? {
        val h = lg.hero ?: return null
        return lg.games.filter { !it.played && it.involves(h.teamId) }.minByOrNull { it.day }
    }

    /** A big one: a rivalry, the postseason, or a game at (or against) his father's old school. */
    fun bigGame(lg: League, g: Game): Boolean {
        val dad = father(lg) ?: return false
        if (lg.rivalry(g) != null || Rival.isDuel(lg, g)) return true
        if (g.type == GameType.NCAA || g.type == GameType.PLAYOFF || g.type == GameType.CONF_TOURNEY) return true
        return !lg.pro && againstDadsSchool(lg, g, dad)
    }

    /** A game against his father's old school (when he went somewhere else). */
    fun againstDadsSchool(lg: League, g: Game, dad: Ancestor): Boolean {
        val h = lg.hero ?: return false
        val mine = lg.teams.getOrNull(h.teamId)?.school
        if (mine == dad.school) return false
        return lg.teams.getOrNull(g.home)?.school == dad.school || lg.teams.getOrNull(g.away)?.school == dad.school
    }

    /** What his father has to say right now, if anything. */
    fun call(lg: League): Call? {
        if (!active(lg) || !inSeason(lg)) return null
        val h = lg.hero ?: return null
        val r = lg.road ?: return null
        val dad = father(lg) ?: return null
        val pop = if (dad.first.length % 2 == 0) "Dad" else "Pops"
        // Before a big game: his scouting report (once for each big game).
        val next = nextGame(lg)
        if (next != null && next.day <= lg.day + 1 && bigGame(lg, next) && r.dadCall != -next.id && r.dadPrep != next.id) {
            val opp = lg.team(next.opp(h.teamId))
            val oppName = if (lg.pro) opp.name else opp.school
            val rival = Rival.isDuel(lg, next)
            return Call("prep", "$pop has a scouting report",
                "\"I've watched $oppName on tape all week." + (if (rival) " Your guy ${Rival.active(lg)?.last ?: "over there"} goes left every time he's pressured." else " They give up the corner three when they help.") +
                    " Come over tonight and I'll show you.\"",
                listOf(
                    Answer("Go over the film together", bond = 5, prep = true, fatigue = 2, reaction = "Two hours of tape at the kitchen table. You know where your shots are coming from."),
                    Answer("Thank him, but do your own prep", bond = -1, mood = 2, reaction = "\"Your call. I'll be watching.\""),
                ), stamp = -next.id)
        }
        // After a game: not every one, and only the ones that matter.
        val last = h.log.lastOrNull() ?: return null
        if (r.dadCall == last.gameId) return null
        val g = lg.games.firstOrNull { it.id == last.gameId }
        val rnd = kotlin.random.Random(last.gameId * 7919L + h.id)
        val avg = if (h.stats.gp >= 4) h.stats.ppg else 0.0
        val recent = h.log.takeLast(3)
        val slump = avg >= 10 && recent.size == 3 && recent.map { it.pts }.average() < avg * 0.6
        val margin = last.score.split("-").mapNotNull { it.trim().takeWhile { c -> c.isDigit() }.toIntOrNull() }.let { if (it.size == 2) it[0] - it[1] else 0 }
        val big = g != null && bigGame(lg, g)
        val bigNight = last.pts >= 25 || (avg > 0 && last.pts >= maxOf(16.0, avg * 1.7)) || (big && last.won && last.pts >= 15)
        val debut = h.log.size == 1
        return when {
            debut -> Call("debut", "$pop calls after your first game",
                "\"${last.pts} points in your first ${if (lg.pro) "NBA" else "college"} game. Mine was ${if (dad.played.proSeasons > 0 && lg.pro) "a lot quieter" else "four points and a turnover"}. How'd it feel?\"",
                listOf(
                    Answer("\"Like I belonged.\"", bond = 4, mood = 3, reaction = "\"Because you do.\""),
                    Answer("\"Nervous. Tell me about yours.\"", bond = 6, reaction = "He tells you the whole story. You've heard it before. You let him tell it anyway."),
                    Answer("\"Fine. Gotta go, Dad.\"", bond = -2, reaction = "\"Okay. Proud of you.\""),
                ), last.gameId)
            bigNight -> if (rnd.nextDouble() < 0.7) Call("proud", "$pop calls after the game",
                "\"${last.pts}. I counted every one from the ${if (LegacyMode.dadInStands(lg, g ?: return null)) "stands" else "couch"}." +
                    " I never had a night like that ${if (lg.pro) "my rookie year" else "at your age"}.\"" + if (last.pts >= 30) " He's trying not to cry. He's not doing great." else "",
                listOf(
                    Answer("\"Learned it from you.\"", bond = 6, mood = 3, reaction = "He goes quiet for a second. \"Get some sleep, kid.\""),
                    Answer("Ask what he'd have done different", bond = 4, prep = true, reaction = "He has a list. A long one. It's actually good."),
                    Answer("Keep it short: you're with the team", bond = -2, mood = 1, reaction = "\"Go, go. Enjoy it.\""),
                ), last.gameId) else null
            slump -> Call("slump", "$pop wants to come to practice",
                "\"You're pressing. I can see it from here: you're shooting before you're set. Let me come watch you work tomorrow.\"",
                listOf(
                    Answer("Let him come", bond = 5, prep = true, reaction = "He rebounds for you for an hour and says maybe nine words. Your feet are under you again."),
                    Answer("\"I've got it, Dad.\"", bond = -3, mood = 2, reaction = "\"Okay. Okay.\" He doesn't sound okay."),
                    Answer("Let it go to voicemail", bond = -6, reaction = "The voicemail is four minutes long. You don't play it."),
                ), last.gameId)
            (last.min >= 20 && last.pts <= 6) || (!last.won && big && margin <= -12) -> if (rnd.nextDouble() < 0.6) Call("rough", "$pop has notes",
                if (!last.won && big) "\"That one stings. I've been there: we lost by twenty to them once and I didn't sleep for a week. Want to talk through it?\""
                else "\"${last.pts} points in ${last.min} minutes. You were waiting for the ball instead of going to get it. Call me back.\"",
                listOf(
                    Answer("Call him back and listen", bond = 5, prep = true, reaction = "He's right, which is annoying. You'll go get it next game."),
                    Answer("\"I know, Dad. I've got it.\"", bond = -2, mood = 2, reaction = "\"Alright. I believe you.\""),
                    Answer("Don't call back", bond = -6, mood = -1, reaction = "He texts a thumbs-up an hour later. That's somehow worse."),
                ), last.gameId) else null
            else -> null
        }
    }

    /** His answer: the bond, his mood, a film session for the next game, and the call is done. */
    fun answer(lg: League, c: Call, i: Int): String {
        val a = c.answers.getOrNull(i) ?: return ""
        val r = lg.road ?: return ""
        val h = lg.hero ?: return ""
        r.dadBond = (r.dadBond + a.bond).coerceIn(0, 100)
        h.happiness = (h.happiness + a.mood).coerceIn(0, 100)
        r.fatigue = (r.fatigue + a.fatigue).coerceIn(0, 100)
        if (a.followers != 0.0) r.followers = (r.followers * (1 + a.followers)).roundToInt()
        if (a.prep) nextGame(lg)?.let { r.dadPrep = it.id }
        r.dadCall = c.stamp
        return a.reaction
    }

    /** Doesn't pick up: the call is done, and he notices. */
    fun skip(lg: League, c: Call) {
        val r = lg.road ?: return
        r.dadCall = c.stamp
        r.dadBond = (r.dadBond - 2).coerceIn(0, 100)
    }

    /** A strained bond: he skips the regular-season trips (never the postseason). */
    fun skipsTrip(lg: League, g: Game): Boolean {
        val r = lg.road ?: return false
        if (r.dadBond >= 25) return false
        return g.type != GameType.NCAA && g.type != GameType.PLAYOFF && g.type != GameType.CONF_TOURNEY
    }

    /** How much seeing him in the stands lifts his son: half as much when they're strained, half again when they're close. */
    fun bondFactor(lg: League): Double = 0.5 + (lg.road?.dadBond ?: 60) / 100.0

    /** The film session they did for this game. */
    fun prepEdge(lg: League, g: Game): Double = if (lg.road?.dadPrep == g.id) 0.01 else 0.0

    /** After the game: the film session is used up. */
    fun afterGame(lg: League, g: Game) {
        val r = lg.road ?: return
        if (r.dadPrep == g.id) r.dadPrep = -1
    }

    // ---------- the summer workout ----------

    /** Before the season's first games: a workout with his father, once a season. */
    fun workoutOpen(lg: League): Boolean {
        val r = lg.road ?: return false
        val h = lg.hero ?: return false
        return active(lg) && r.dadSummer != lg.season && h.stats.gp < 3 && (father(lg)?.strengths?.isNotEmpty() == true)
    }

    /** How much the workout adds: more the closer they are. */
    fun workoutGain(lg: League): Int = when { (lg.road?.dadBond ?: 60) >= 80 -> 3; (lg.road?.dadBond ?: 60) >= 45 -> 2; else -> 1 }

    /** A workout on one of his father's best skills ([Attr] ordinal from his strengths). */
    fun workout(lg: League, attr: Int): String {
        val r = lg.road ?: return ""
        val h = lg.hero ?: return ""
        val dad = father(lg) ?: return ""
        if (!workoutOpen(lg) || attr !in dad.strengths) return ""
        val gain = workoutGain(lg)
        h.attrs[attr] = (h.attrs[attr] + gain).coerceAtMost(99)
        r.dadSummer = lg.season
        r.dadBond = (r.dadBond + 4).coerceIn(0, 100)
        val name = Attr.entries[attr].label
        r.legacyLog += "${lg.season}: worked on ${name.lowercase()} with Dad (+$gain)"
        return "Every morning before it got hot: ${name.lowercase()}, the way he used to do it. +$gain ${name}."
    }
}
