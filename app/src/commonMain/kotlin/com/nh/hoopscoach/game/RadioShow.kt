package com.nh.hoopscoach.game

import kotlinx.serialization.Serializable

/** Something the coach said on the show that a caller can bring back: when, what about, the words, and who it was about. */
@Serializable
class RadioQuote(val season: Int, val day: Int, val topic: String, val text: String, val target: Int = -1, var thrownBack: Boolean = false)

/**
 * The coach's weekly radio show. Fans call in about what's going on (a streak, a rivalry, the star's
 * future, NIL rumors, recruits lost to a rival) and the coach answers: humble, confident or fiery.
 * Answers move fan mood, a rival coach's temper, the coach's standing with his bosses and players' moods.
 */
object RadioShow {
    /** One answer and what it does: fans, job security, a rival's heat, and the mood of the player it's about. */
    class Answer(val text: String, val fans: Double = 0.0, val security: Int = 0, val heat: Int = 0, val mood: Int = 0, val reaction: String, val memo: String = "")

    /** One call: who's on the line, the question, and the player or coach it's about (-1 when nobody). */
    class Call(val caller: String, val question: String, val answers: List<Answer>, val playerId: Int = -1, val rivalCoachId: Int = -1, val callback: RadioQuote? = null)

    /** The week of the season (1-based), and the stamp stored once a show is done. */
    private fun week(lg: League) = lg.day / 7
    private fun stamp(lg: League) = lg.season * 100 + week(lg)

    /** A new show every week of the regular season, once a game's been played. */
    fun due(lg: League) = !lg.pro && !lg.playerMode && lg.phase == Phase.REGULAR && lg.user.gp >= 1 && lg.radioWeek != stamp(lg)

    /** This week's calls (the same ones however often the screen draws). */
    fun calls(lg: League): List<Call> {
        val rnd = java.util.Random(stamp(lg).toLong() * 31 + lg.userTeamId)
        fun name() = RadioLines.CALLERS[rnd.nextInt(RadioLines.CALLERS.size)]
        /** One of several ways to say it, so no two shows sound alike. */
        fun v(vararg s: String) = s[rnd.nextInt(s.size)]
        val u = lg.user
        val pressing = mutableListOf<Call>()
        val back = callback(lg, name())
        // Streaks.
        if (u.streak >= 3) pressing += Call(name(), v(
            "You've won ${u.streak} straight. Coach, is this a title team?",
            "${u.streak} in a row now. How good can this team be?",
            "Coach, ${u.streak} straight wins. Should we be booking hotel rooms for the Final Four?",
            "We haven't lost in ${u.streak} games. Is this the real thing?",
            "${u.streak} straight. Be honest: did you see this coming?",
            "Nobody's beaten us in ${u.streak} tries. What's the ceiling?",
        ), listOf(
            Answer(v("One game at a time. We haven't done anything yet.", "We haven't won anything yet. Nobody hangs a banner in ${if (lg.day < 60) "the winter" else "February"}.",
                "I like where we are. I don't love it yet.", "Streaks end. Habits don't. We're working on the habits."), fans = 1.0, security = 1, reaction = "Fans like the humility."),
            Answer(v("We're the best team in this conference, and it isn't close.", "Put us against anybody. I like my guys.", "Yes. And we're still getting better.",
                "If we play like this, nobody in this league beats us."), fans = 4.0, heat = 1, reaction = "The fan base is fired up, and every rival heard it.", memo = "boast"),
            Answer(v("Ask me in March.", "Call back in six weeks.", "Let's talk when the nets come down.", "That's a March question."), fans = 2.0, reaction = "A crowd-pleaser."),
        ))
        if (u.streak <= -2) pressing += Call(name(), v(
            "${-u.streak} losses in a row. What's wrong with this team?",
            "Coach, that's ${-u.streak} straight losses. How do you stop the bleeding?",
            "We've dropped ${-u.streak} in a row. Is the season slipping away?",
            "${-u.streak} straight. What are you seeing that we aren't?",
            "I'll say it: ${-u.streak} losses in a row is not acceptable here. Your response?",
        ), listOf(
            Answer(v("That's on me. I have to coach them better.", "Start with me. I haven't had them ready.", "Put it on the head coach. That's the job.",
                "I've got to find answers, and I will."), fans = 1.0, security = 2, reaction = "Taking the blame plays well with the athletic director."),
            Answer(v("Guys aren't executing. Some of them need to grow up.", "We've got players who like the jersey more than the work.", "The effort isn't there. That's not coaching.",
                "Some guys are finding out this level is hard."), fans = -1.0, security = -1, mood = -6, reaction = "The players heard it. The locker room is tense.", memo = "blame"),
            Answer(v("We'll be fine. Nobody panic.", "It's a long season. Deep breaths.", "The sky isn't falling.", "Every team hits this. We'll come out of it."), fans = -2.0, reaction = "Callers aren't convinced."),
        ))
        // The next rival.
        Season.nextUserGame(lg)?.let { g ->
            lg.rivalry(g) ?: return@let
            val opp = lg.team(g.opp(u.id))
            val oc = runCatching { lg.coachOf(opp) }.getOrNull() ?: return@let
            pressing += Call(name(), v(
                "${opp.school} is next. What do you think of ${oc.name}?",
                "Rivalry week. How do you feel about ${oc.name} and ${opp.school}?",
                "It's ${opp.school} week, coach. Give us something.",
                "My brother-in-law went to ${opp.school}. Please tell me we're winning this one.",
                "${oc.name} has been talking. Are you going to answer?",
                "What's the message to ${opp.school} before this one?",
            ), listOf(
                Answer(v("Great coach, great program. It'll be a battle.", "Nothing but respect. They'll be ready and so will we.", "${oc.last} does it the right way. It's a good rivalry.",
                    "Two good teams. That's what a rivalry should be."), heat = -2, reaction = "Classy. ${oc.last} returns the compliment."),
                Answer(v("We're going to run them out of our building.", "I don't like them, our players don't like them, and it's going to show.", "Tell your brother-in-law to stay home.",
                    "They can talk. We'll play."), fans = 4.0, security = -1, heat = 3, reaction = "Bulletin-board material: ${oc.last}'s team will be fired up.", memo = "trash"),
                Answer(v("I don't think about them. We worry about us.", "It's the next game on the schedule.", "Faceless opponent. Same preparation.", "We don't circle games."), fans = 1.0, heat = 1, reaction = "A shrug that says plenty."),
            ), rivalCoachId = oc.id)
        }
        // The star.
        lg.rosterOf(u).filter { it.year >= 2 }.maxByOrNull { it.ovr }?.takeIf { it.ovr >= 75 }?.let { star ->
            val unhappy = star.happiness < 45
            pressing += Call(name(), if (unhappy) v(
                "Word is ${star.name} isn't happy. Is he transferring?",
                "I keep hearing ${star.name} has one foot out the door. True?",
                "Is something going on with ${star.name}? He doesn't look like himself.",
                "The message boards say ${star.name} is gone after this year. Say it isn't so.",
            ) else v(
                "Is ${star.name} going pro after this year?",
                "How much longer do we get to watch ${star.name} in our uniform?",
                "The scouts are at every game for ${star.name}. Is this his last year?",
                "Tell ${star.name} to stay one more year. Can you do that for us?",
                "Is ${star.name} the best player in this league?",
            ), listOf(
                Answer(v("${star.last} is family. Whatever he decides, we've got his back.", "I love that kid. He's earned the right to choose.", "${star.last} has given this program everything. We'll support him.",
                    "He's one of ours for life, whatever happens."), fans = 1.0, mood = 6, reaction = "${star.short} heard it and appreciated it.", memo = "family"),
                Answer(v("Nobody is bigger than this program.", "The name on the front outlasts the name on the back.", "Players come and go. The program stays.",
                    "We'll be fine with him or without him."), fans = 2.0, security = 1, mood = -6, reaction = "The boosters loved it. ${star.short} didn't.", memo = "nobody"),
                Answer(v("We're focused on this season.", "That's a spring conversation.", "He's got games to play. So do we.", "One thing at a time."), reaction = "A safe answer."),
            ), playerId = star.id)
        }
        // NIL rumors with warnings on file.
        if (lg.complianceWarnings > 0) pressing += Call(name(), v(
            "There are rumors about NIL payments. Anything to them?",
            "People are whispering about how we're paying for this roster. Should we worry?",
            "I read something about our NIL deals. Are we in trouble?",
            "Coach, straight question: is everything above board with the money?",
        ), listOf(
            Answer(v("We do everything by the book here.", "Everything we do is by the rules. Period.", "We run a clean program. Always have."), fans = 1.0, security = if (lg.complianceWarnings >= 2) -2 else 0, reaction = "If the warnings keep coming, that answer will age badly.", memo = "bythebook"),
            Answer(v("No comment.", "Next question.", "I'm not going there."), security = -1, fans = -1.0, reaction = "Callers read plenty into that."),
            Answer(v("We're working closely with our compliance office.", "Our compliance people review every deal.", "We've asked compliance to look at everything, and we'll act on what they find."), fans = -1.0, security = 2, reaction = "The athletic director appreciated the transparency."),
        ))
        // The recruiting rival.
        RecruitRivals.rivals(lg).maxByOrNull { it.lost }?.let { e ->
            val t = lg.teams.getOrNull(e.teamId) ?: return@let
            val oc = runCatching { lg.coachOf(t) }.getOrNull()
            pressing += Call(name(), v(
                "Why does ${t.school} keep beating us for recruits?",
                "Another one picked ${t.school} over us. What are they selling?",
                "How do we stop losing kids to ${t.school}?",
                "${t.school} is living in our backyard on the trail. What's the plan?",
            ), listOf(
                Answer(v("They do a good job. We have to be better.", "Credit to them. We've got to close better.", "That's on us. We'll win the next one."), security = 1, heat = -1, reaction = "Honest. Recruits respect it."),
                Answer(v("Ask them how they're paying for it.", "I'd love to see their books.", "Funny how it always comes down to the last week and a bigger number."), fans = 3.0, security = -2, heat = 3, reaction = "That one will get back to ${t.school}, and to the NCAA."),
                Answer(v("We get the players who fit us.", "I'll take the guys we've got.", "Rankings don't win games. Players who fit do."), fans = 1.0, reaction = "Fair enough."),
            ), rivalCoachId = oc?.id ?: -1)
        }
        // The poll.
        if (u.ap in 1..25 && u.gp >= 4) pressing += Call(name(), v(
            "We're No. ${u.ap} in the country. Too high, too low, or just right?",
            "No. ${u.ap}! Does the team talk about it?",
            "Coach, No. ${u.ap}. When was the last time you looked at the poll?",
        ), listOf(
            Answer(v("It's a number next to our name. It doesn't guard anybody.", "Nice for the fans. We've got work to do.", "Rankings are for you. We've got practice."), fans = 1.0, security = 1, reaction = "The right thing to say."),
            Answer(v("Too low. Everybody we've played knows it.", "We should be higher, and we will be.", "Give it two weeks."), fans = 3.0, heat = 1, reaction = "The fans agree. The teams ahead of you heard it."),
            Answer(v("Ask me where we are in April.", "The only poll that matters comes out after the last game.", "Call me when it's over."), fans = 2.0, reaction = "A crowd-pleaser."),
        ))
        // The last game.
        lg.games.filter { it.played && it.involves(u.id) }.maxByOrNull { it.day }?.let { g ->
            val opp = lg.team(g.opp(u.id))
            val margin = g.scoreFor(u.id) - g.scoreAgainst(u.id)
            if (margin >= 20) pressing += Call(name(), v(
                "We beat ${opp.school} by $margin. Was that as fun as it looked?",
                "A $margin-point win over ${opp.school}. Is that who we are?",
                "Coach, $margin points! Did you call off the dogs?",
            ), listOf(
                Answer(v("It was one night. Shots fell. We move on.", "Good night. Doesn't count double.", "I liked the first half. We got sloppy late."), security = 1, reaction = "Never satisfied, and the AD likes that."),
                Answer(v("That's who we are when we're locked in.", "That's the standard. Get used to it.", "When this team plays like that, good luck."), fans = 3.0, heat = 1, reaction = "The fans want more of it."),
                Answer(v("The bench got minutes. That was the best part.", "Everybody played, everybody scored. Good night for the locker room.", "I got to empty the bench. Those guys work hard too."), mood = 4, fans = 1.0, reaction = "The reserves appreciated it."),
            ))
            else if (margin <= -15) pressing += Call(name(), v(
                "${opp.school} beat us by ${-margin}. What happened out there?",
                "That ${opp.school} game was hard to watch. Explain it to me.",
                "Coach, we lost by ${-margin}. Did the team quit?",
            ), listOf(
                Answer(v("I didn't have them ready. That's my job.", "Burn the tape? No. We'll watch every second of it.", "I'll take that one. It won't happen again."), fans = 1.0, security = 1, reaction = "Accountability plays well."),
                Answer(v("We got punched and didn't punch back.", "They wanted it more. That's a bad sentence to say out loud.", "Soft. There's no other word for it."), fans = 1.0, mood = -5, reaction = "The players heard every word."),
                Answer(v("It happens. Give them credit.", "One of those nights. Flush it.", "They made shots. Tip your cap."), fans = -2.0, reaction = "Callers wanted more fire than that."),
            ))
            else if (margin in -4..-1) pressing += Call(name(), v(
                "That ${opp.school} loss is still bothering me. What would you do differently?",
                "We lost to ${opp.school} by ${-margin}. Last possession: what was the call?",
                "A ${-margin}-point loss. Does that one keep you up?",
            ), listOf(
                Answer(v("I'd take the last timeout back.", "That's on me. I've replayed the last minute a hundred times.", "I'd like one more possession. So would they."), security = 1, fans = 1.0, reaction = "An honest answer."),
                Answer(v("We had the shot we wanted. It didn't go.", "I'd run the same play again tomorrow.", "Good look, good shooter. That's basketball."), mood = 3, reaction = "He stood by his players."),
                Answer(v("We shouldn't have been in that position.", "It wasn't the last play. It was the first thirty minutes.", "You don't lose it at the end. You lose it at the start."), security = 1, mood = -2, reaction = "A message to the room."),
            ))
        }
        // A freshman who's playing.
        lg.rosterOf(u).filter { it.year == 1 && it.stats.gp >= 3 }.maxByOrNull { it.stats.ppg }?.takeIf { it.stats.ppg >= 9 }?.let { fr ->
            pressing += Call(name(), v(
                "${fr.name} looks like the real deal. How good can he be?",
                "Tell us about ${fr.name}. He doesn't play like a freshman.",
                "Is ${fr.name} the best freshman in this league?",
            ), listOf(
                Answer(v("He's got a long way to go. But he listens.", "Ask me when he's a junior.", "He's a freshman. Let him be a freshman."), security = 1, reaction = "Managing expectations."),
                Answer(v("He can be as good as anyone who's played here.", "Special. I don't use that word much.", "He's going to make a lot of money playing this game."), fans = 3.0, mood = 5, reaction = "${fr.short} heard it. So did every scout."),
                Answer(v("He's one of five out there. The team comes first.", "He'd tell you it's about his teammates.", "Good player on a good team."), mood = 2, reaction = "The veterans appreciated that."),
            ), playerId = fr.id)
        }
        // The league race.
        if (u.cw + u.cl >= 4) {
            val rank = runCatching { Season.standings(lg, u.confId).indexOfFirst { it.id == u.id } + 1 }.getOrDefault(0)
            if (rank == 1) pressing += Call(name(), v(
                "We're in first place. Do you feel the target on your back?",
                "Top of the league at ${u.cw}-${u.cl}. Can we hold on?",
            ), listOf(
                Answer(v("First place in the middle of the season pays nothing.", "Standings don't matter until the last week.", "We haven't won it yet."), security = 1, reaction = "Steady."),
                Answer(v("We like the target. Come get us.", "Everybody's best shot is exactly what we want.", "It's our league to lose, and we're not losing it."), fans = 3.0, heat = 2, reaction = "The league heard that."),
                Answer(v("One at a time, same as always.", "We look at the next game, not the standings.", "I honestly couldn't tell you who's second."), reaction = "Tunnel vision."),
            ))
            else if (rank >= 8) pressing += Call(name(), v(
                "We're ${u.cw}-${u.cl} in the league. Is the season over?",
                "Coach, we're near the bottom of the standings. What do you tell the fans?",
            ), listOf(
                Answer(v("Stick with us. This group will fight to the end.", "We need you now more than ever.", "Nobody in that locker room has quit."), fans = 2.0, mood = 2, reaction = "A plea, and it was heard."),
                Answer(v("The record is what we've earned.", "We are what the standings say we are.", "No excuses. We haven't been good enough."), security = 1, reaction = "Honest."),
                Answer(v("There's a league tournament for a reason.", "Get hot for four days and nobody remembers January.", "Everybody's 0-0 in March."), fans = 1.0, reaction = "Hope is a strategy this time of year."),
            ))
        }
        // The show: the caller with a long memory first, then one pressing matter (a different one each
        // week), then an any-week topic, so no two shows run the same.
        val out = mutableListOf<Call>()
        back?.let { out += it }
        if (pressing.isNotEmpty()) out += pressing[rnd.nextInt(pressing.size)]
        val pool = RadioLines.GENERAL.toMutableList()
        while (out.size < 2 && pool.isNotEmpty()) {
            val topic = pool.removeAt(rnd.nextInt(pool.size))
            val q = topic.questions[rnd.nextInt(topic.questions.size)].replace("{school}", u.school).replace("{mascot}", u.mascot)
            out += Call(name(), q, topic.answers)
        }
        return out.take(2)
    }

    /** The user's results since a day this season: (wins, losses), optionally only against [teamId]. */
    private fun since(lg: League, day: Int, season: Int, teamId: Int = -1): Pair<Int, Int> {
        if (season != lg.season) return 0 to 0
        val u = lg.userTeamId
        val gs = lg.games.filter { it.played && it.day > day && it.involves(u) && (teamId < 0 || it.involves(teamId)) }
        return gs.count { it.winner() == u } to gs.count { it.winner() != u }
    }

    /** A caller who remembers: something the coach said that the season has since made awkward. */
    private fun callback(lg: League, caller: String): Call? {
        for (q in lg.radioLog.filter { !it.thrownBack }.reversed()) {
            val (w, l) = since(lg, q.day, q.season)
            when (q.topic) {
                "boast" -> if (q.season == lg.season && l >= 3 && l > w) return Call(caller,
                    "Coach, you told us you were the best team in this conference. Since then you're $w-$l. What happened?", listOf(
                        Answer("I got ahead of myself. That's on me.", fans = 1.0, security = 1, reaction = "Owning it bought some goodwill."),
                        Answer("I meant it. Talk to me in March.", fans = 2.0, security = -1, reaction = "Bold. March had better go well.", memo = "boast"),
                        Answer("We've hit a rough patch. Every team does.", fans = -1.0, reaction = "Callers want more than that."),
                    ), callback = q)
                "trash" -> {
                    val coach = lg.coaches.firstOrNull { it.id == q.target } ?: continue
                    val t = lg.teams.getOrNull(coach.teamId) ?: continue
                    val (_, lost) = since(lg, q.day, q.season, t.id)
                    if (lost >= 1) return Call(caller, "You said you'd run ${t.school} out of the building. They beat you. Want those words back?", listOf(
                        Answer("Yes. They earned it, and I should have kept my mouth shut.", heat = -2, fans = 1.0, reaction = "${coach.last} accepted the olive branch."),
                        Answer("Next time, it's a different story.", heat = 2, fans = 2.0, reaction = "The rivalry just got hotter.", memo = "trash"),
                        Answer("One game. It's a long season.", reaction = "A shrug."),
                    ), rivalCoachId = coach.id, callback = q)
                }
                "bythebook" -> if (NcaaCases.open(lg, lg.userTeamId) != null) return Call(caller,
                    "You told us you do everything by the book. Now the NCAA is here. Which is it?", listOf(
                        Answer("We'll cooperate fully and let the facts come out.", security = 1, fans = -1.0, reaction = "The athletic director nodded along."),
                        Answer("This is a witch hunt.", fans = 3.0, security = -3, reaction = "The base loved it. The NCAA was listening too."),
                        Answer("I can't comment on an open case.", fans = -1.0, reaction = "The standard answer, and everyone knows it."),
                    ), callback = q)
                "family", "nobody" -> {
                    val p = lg.anyPlayer(q.target) ?: continue
                    if (p.teamId == lg.userTeamId) continue
                    return Call(caller, if (q.topic == "family") "You called ${p.name} family. He's gone now. What happened?"
                        else "You said nobody's bigger than the program, and ${p.name} walked. Was it worth it?", listOf(
                        Answer("I wish him the best. Sometimes it's time to move on.", fans = 1.0, reaction = "Graceful."),
                        Answer("We're better without guys who don't want to be here.", fans = 2.0, mood = -3, reaction = "The roster took note. Some of them didn't like it."),
                        Answer("I could have handled it better.", security = 1, reaction = "Honest, and the AD appreciated it."),
                    ), callback = q)
                }
            }
        }
        return null
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
        call.callback?.thrownBack = true
        if (a.memo.isNotEmpty()) {
            lg.radioLog += RadioQuote(lg.season, lg.day, a.memo, a.text, if (a.memo == "trash") call.rivalCoachId else call.playerId)
            while (lg.radioLog.size > 30) lg.radioLog.removeAt(0)
        }
        lg.radioAnswered++
        if (lg.radioAnswered >= calls(lg).size) { lg.radioWeek = stamp(lg); lg.radioAnswered = 0 }
        return a.reaction
    }

    /** Key for an AI-written version of this week's call [i]. */
    fun aiKey(lg: League, i: Int) = "${stamp(lg)}:$i"

    /** The prompt that has the AI rewrite a caller's question in his own voice. */
    fun aiPrompt(lg: League, call: Call): String =
        "A caller to ${lg.userCoach.name}'s weekly college basketball radio show (the ${lg.user.school} ${lg.user.mascot}, ${lg.user.record}) is ${call.caller}. " +
            "Rewrite the caller's question in his own voice, one or two sentences, conversational, same meaning, no quotation marks, no greeting to the host by name: " + call.question

    /** Skips this week's show. */
    fun skip(lg: League) { lg.radioWeek = stamp(lg); lg.radioAnswered = 0 }
}
