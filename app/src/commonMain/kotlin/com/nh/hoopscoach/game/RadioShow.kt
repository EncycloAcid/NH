package com.nh.hoopscoach.game

/**
 * The coach's weekly radio show. Fans call in about what's going on (a streak, a rivalry, the star's
 * future, NIL rumors, recruits lost to a rival) and the coach answers: humble, confident or fiery.
 * Answers move fan mood, a rival coach's temper, the coach's standing with his bosses and players' moods.
 */
object RadioShow {
    /** One answer and what it does: fans, job security, a rival's heat, and the mood of the player it's about. */
    class Answer(val text: String, val fans: Double = 0.0, val security: Int = 0, val heat: Int = 0, val mood: Int = 0, val reaction: String)

    /** One call: who's on the line, the question, and the player or coach it's about (-1 when nobody). */
    class Call(val caller: String, val question: String, val answers: List<Answer>, val playerId: Int = -1, val rivalCoachId: Int = -1)

    /** The week of the season (1-based), and the stamp stored once a show is done. */
    private fun week(lg: League) = lg.day / 7
    private fun stamp(lg: League) = lg.season * 100 + week(lg)

    /** A new show every week of the regular season, once a game's been played. */
    fun due(lg: League) = !lg.pro && !lg.playerMode && lg.phase == Phase.REGULAR && lg.user.gp >= 1 && lg.radioWeek != stamp(lg)

    private val NAMES = listOf("Marcus from the east side", "Big Tony in the truck", "Linda, season-ticket holder since '88", "Dev, a student in section 112",
        "Coach Ray, retired", "Keisha on her lunch break", "Earl, longtime caller", "Jamal from the barbershop")

    /** This week's calls (the same ones however often the screen draws). */
    fun calls(lg: League): List<Call> {
        val rnd = java.util.Random(stamp(lg).toLong() * 31 + lg.userTeamId)
        fun name() = NAMES[rnd.nextInt(NAMES.size)]
        val u = lg.user
        val out = mutableListOf<Call>()
        // Streaks.
        if (u.streak >= 3) out += Call(name(), "You've won ${u.streak} straight. Coach, is this a title team?", listOf(
            Answer("One game at a time. We haven't done anything yet.", fans = 1.0, security = 1, reaction = "Fans like the humility."),
            Answer("We're the best team in this conference, and it isn't close.", fans = 4.0, heat = 1, reaction = "The fan base is fired up, and every rival heard it."),
            Answer("Ask me in March.", fans = 2.0, reaction = "A crowd-pleaser."),
        ))
        if (u.streak <= -2) out += Call(name(), "${-u.streak} losses in a row. What's wrong with this team?", listOf(
            Answer("That's on me. I have to coach them better.", fans = 1.0, security = 2, reaction = "Taking the blame plays well with the athletic director."),
            Answer("Guys aren't executing. Some of them need to grow up.", fans = -1.0, security = -1, mood = -6, reaction = "The players heard it. The locker room is tense."),
            Answer("We'll be fine. Nobody panic.", fans = -2.0, reaction = "Callers aren't convinced."),
        ))
        // The next rival.
        Season.nextUserGame(lg)?.let { g ->
            val r = lg.rivalry(g) ?: return@let
            val opp = lg.team(g.opp(u.id))
            val oc = runCatching { lg.coachOf(opp) }.getOrNull() ?: return@let
            out += Call(name(), "${opp.school} is next. What do you think of ${oc.name}?", listOf(
                Answer("Great coach, great program. It'll be a battle.", heat = -2, reaction = "Classy. ${oc.last} returns the compliment."),
                Answer("We're going to run them out of our building.", fans = 4.0, security = -1, heat = 3, reaction = "Bulletin-board material: ${oc.last}'s team will be fired up."),
                Answer("I don't think about them. We worry about us.", fans = 1.0, heat = 1, reaction = "A shrug that says plenty."),
            ), rivalCoachId = oc.id)
        }
        // The star.
        lg.rosterOf(u).filter { it.year >= 2 }.maxByOrNull { it.ovr }?.takeIf { it.ovr >= 75 }?.let { star ->
            val unhappy = star.happiness < 45
            out += Call(name(), if (unhappy) "Word is ${star.name} isn't happy. Is he transferring?" else "Is ${star.name} going pro after this year?", listOf(
                Answer("${star.last} is family. Whatever he decides, we've got his back.", fans = 1.0, mood = 6, reaction = "${star.short} heard it and appreciated it."),
                Answer("Nobody is bigger than this program.", fans = 2.0, security = 1, mood = -6, reaction = "The boosters loved it. ${star.short} didn't."),
                Answer("We're focused on this season.", reaction = "A safe answer."),
            ), playerId = star.id)
        }
        // NIL rumors with warnings on file.
        if (lg.complianceWarnings > 0) out += Call(name(), "There are rumors about NIL payments. Anything to them?", listOf(
            Answer("We do everything by the book here.", fans = 1.0, security = if (lg.complianceWarnings >= 2) -2 else 0, reaction = "If the warnings keep coming, that answer will age badly."),
            Answer("No comment.", security = -1, fans = -1.0, reaction = "Callers read plenty into that."),
            Answer("We're working closely with our compliance office.", fans = -1.0, security = 2, reaction = "The athletic director appreciated the transparency."),
        ))
        // The recruiting rival.
        RecruitRivals.rivals(lg).maxByOrNull { it.lost }?.let { e ->
            val t = lg.teams.getOrNull(e.teamId) ?: return@let
            val oc = runCatching { lg.coachOf(t) }.getOrNull()
            out += Call(name(), "Why does ${t.school} keep beating us for recruits?", listOf(
                Answer("They do a good job. We have to be better.", security = 1, heat = -1, reaction = "Honest. Recruits respect it."),
                Answer("Ask them how they're paying for it.", fans = 3.0, security = -2, heat = 3, reaction = "That one will get back to ${t.school}, and to the NCAA."),
                Answer("We get the players who fit us.", fans = 1.0, reaction = "Fair enough."),
            ), rivalCoachId = oc?.id ?: -1)
        }
        // Something to talk about any week.
        out += Call(name(), "What's the one thing this team has to get better at?", listOf(
            Answer("Defense. It wins in March.", fans = 1.0, reaction = "The old-school callers approve."),
            Answer("Taking care of the ball.", fans = 1.0, reaction = "Fair."),
            Answer("Honestly? Everything. We're not close to our ceiling.", fans = 2.0, security = 1, reaction = "Ambitious, and the AD likes ambitious."),
        ))
        // Two calls a show: the most pressing ones first.
        return out.take(2)
    }

    /** Answers call [ci] with answer [ai]; returns how it landed. */
    fun answer(lg: League, ci: Int, ai: Int): String {
        val call = calls(lg).getOrNull(ci) ?: return ""
        val a = call.answers.getOrNull(ai) ?: return ""
        val u = lg.user
        Fans.ensure(u)
        u.fans = (u.fans + a.fans).coerceIn(5.0, 100.0)
        val c = lg.userCoach
        c.security = (c.security + a.security).coerceIn(0, 100)
        if (call.playerId >= 0 && a.mood != 0) lg.playerOrNull(call.playerId)?.let { it.happiness = (it.happiness + a.mood).coerceIn(0, 100) }
        if (a.mood < 0 && call.playerId < 0) for (p in lg.rosterOf(u)) p.happiness = (p.happiness + a.mood / 2).coerceIn(0, 100)
        if (a.heat != 0) {
            val targets = if (call.rivalCoachId >= 0) listOf(call.rivalCoachId) else CoachRivals.rivals(lg).map { it.coachId }
            for (id in targets) {
                val r = CoachRivals.of(lg, id) ?: CoachRival(id).also { lg.coachRivals += it }
                r.heat = (r.heat + a.heat).coerceIn(0, 12)
            }
        }
        lg.radioAnswered++
        if (lg.radioAnswered >= calls(lg).size) { lg.radioWeek = stamp(lg); lg.radioAnswered = 0 }
        return a.reaction
    }

    /** Skips this week's show. */
    fun skip(lg: League) { lg.radioWeek = stamp(lg); lg.radioAnswered = 0 }
}
