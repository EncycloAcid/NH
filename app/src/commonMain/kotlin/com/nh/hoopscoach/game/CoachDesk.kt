package com.nh.hoopscoach.game

import kotlin.math.abs

/**
 * The coach's week off the floor, in five parts, each a card on Today with a choice to make:
 *  - the postgame press conference after every game, on what just happened;
 *  - conference media day before the opener: three questions that set the tone;
 *  - senior night before the last home game;
 *  - the mailbag every other week: letters from kids, critics, alumni and the occasional rival fan;
 *  - the preseason prediction, graded at season's end.
 * Every choice moves something: the fans, job security, the players' mood, a rival coach's temper,
 * or the recruits on the board. College coaching careers only (the radio show's world).
 */
object CoachDesk {
    class Choice(
        val text: String, val fans: Double = 0.0, val security: Int = 0, val mood: Int = 0, val heat: Int = 0,
        val recruits: Int = 0, val reaction: String,
    )

    /** One question: where it's from, who's asking, the question, and the answers. */
    class Prompt(val kind: String, val label: String, val who: String, val question: String, val choices: List<Choice>,
                 val playerId: Int = -1, val rivalCoachId: Int = -1, val step: Int = 0, val steps: Int = 1)

    fun active(lg: League) = !lg.pro && !lg.playerMode && lg.userTeamId >= 0

    /** The press conference also runs in an NBA coaching career. */
    fun pressActive(lg: League) = !lg.playerMode && lg.userTeamId >= 0

    private fun c(text: String, fans: Double = 0.0, security: Int = 0, mood: Int = 0, heat: Int = 0, recruits: Int = 0, reaction: String) =
        Choice(text, fans, security, mood, heat, recruits, reaction)

    private class Pen(seed: Long) {
        val r = kotlin.random.Random(seed)
        fun one(vararg o: String) = o[r.nextInt(o.size)]
        fun <T> any(l: List<T>) = l[r.nextInt(l.size)]
    }

    private val WRITERS = listOf("the beat writer", "the student paper", "the local TV station", "a national columnist", "the conference network",
        "the campus radio station", "the newspaper's columnist", "a wire reporter", "the podcast crowd", "the TV sideline reporter")

    // =====================================================================================
    // The postgame press conference
    // =====================================================================================

    /** The user's last game, if it's recent and the presser hasn't happened yet. */
    private fun lastGame(lg: League): Game? {
        val u = lg.userTeamId
        val g = lg.games.filter { it.played && it.involves(u) }.maxByOrNull { it.day } ?: return null
        if (g.id == lg.deskPressGame || lg.day - g.day > 2) return null
        return g
    }

    fun press(lg: League): Prompt? {
        if (!pressActive(lg)) return null
        val g = lastGame(lg) ?: return null
        val u = lg.user
        val pen = Pen(g.id * 7919L + lg.season)
        val opp = lg.team(g.opp(u.id))
        val won = g.winner() == u.id
        val us = g.scoreFor(u.id); val them = g.scoreAgainst(u.id)
        val margin = abs(us - them)
        val score = "$us-$them"
        val o = if (lg.pro) "the ${opp.name}" else opp.school
        val oc = runCatching { lg.coachOf(opp) }.getOrNull()
        val rivalry = lg.rivalry(g) != null
        val mine = g.box?.let { if (g.home == u.id) it.home else it.away }.orEmpty()
        val top = mine.maxByOrNull { it.s.pts }
        val topP = top?.let { lg.playerOrNull(it.pid) }
        val fresh = mine.filter { lg.playerOrNull(it.pid)?.year == 1 }.maxByOrNull { it.s.pts }?.takeIf { it.s.pts >= 18 }
        val upsetWin = won && opp.ap in 1..25 && (u.ap !in 1..25 || u.ap > opp.ap)
        val upsetLoss = !won && u.ap in 1..25 && (opp.ap !in 1..25 || opp.ap > u.ap)
        val march = g.type == GameType.NCAA || g.type == GameType.CONF_TOURNEY || g.type == GameType.PLAYOFF
        val who = pen.any(WRITERS)
        val label = "Postgame · ${if (won) "W" else "L"} $score ${if (g.home == u.id) "vs" else "at"} ${if (lg.pro) opp.name else opp.school}"
        fun p(q: String, vararg ch: Choice, playerId: Int = -1, rival: Int = -1) = Prompt("press", label, who, q, ch.toList(), playerId, rival)

        return when {
            march && !won -> p(pen.one("That's the end of the road. What do you say to this group?", "The season's over. How will you remember this team?",
                "Coach, it's over. What does this team leave behind?", "Where does this one rank among the hard nights?"),
                c(pen.one("I love this team. Nobody can take away what they built.", "They gave me everything. I'm proud of every one of them.", "This group will always matter to me."),
                    fans = 2.0, mood = 4, reaction = "The players heard it. So did the recruits."),
                c(pen.one("We're not satisfied. We'll be back here and we'll be better.", "Remember this feeling. We'll use it.", "This hurts, and it should. It means we were close."),
                    fans = 3.0, security = 1, reaction = "Fans are already talking about next year."),
                c(pen.one("They were better tonight. That's the truth.", "Credit to them. We didn't have enough.", "We ran into a good team on a good night."),
                    security = 1, reaction = "Gracious, and the AD noticed."))
            march && won -> p(pen.one("You're moving on. How does it feel?", "Survive and advance. What did you see tonight?", "Coach, you're still dancing. What's working?"),
                c(pen.one("These guys refuse to go home. I'm just along for the ride.", "Credit the players. They wanted it.", "This group plays for each other. That's March."),
                    fans = 2.0, mood = 4, reaction = "The locker room loved it."),
                c(pen.one("We came here to win the whole thing.", "Nobody wants to see us right now.", "We're not done. Not even close."),
                    fans = 4.0, heat = 1, recruits = 2, reaction = "Bold. Recruits are watching this run."),
                c(pen.one("One game at a time. That's all March is.", "We'll enjoy it for an hour, then get to work.", "Next game is the only one that matters."),
                    security = 1, reaction = "Steady."))
            rivalry && won -> p(pen.one("You beat $o. What does that mean to this program?", "Rivalry win, coach. Talk about the atmosphere.", "How sweet was that one?",
                "${oc?.last ?: o} said this week his team would win. Your response?"),
                c(pen.one("It means everything to our fans, and that matters to me.", "That one's for the people who've been here forever.", "Our fans will be happy all week. That's what this game is."),
                    fans = 4.0, heat = -1, reaction = "The fan base is on cloud nine."),
                c(pen.one("This is our house. They know it now.", "Tell them to enjoy the bus ride home.", "We heard all the talk. We answered it."),
                    fans = 5.0, security = -1, heat = 3, reaction = "That'll be on their locker room wall by Monday."),
                c(pen.one("Great program. Great coach. We were just a little better tonight.", "Respect to them. It's always a war.", "It's a big win. It counts the same in the standings."),
                    heat = -2, security = 1, reaction = "Classy. ${oc?.last ?: "Their coach"} noticed."), rival = oc?.id ?: -1)
            rivalry && !won -> p(pen.one("Tough loss to a rival. What went wrong?", "Coach, what do you say to fans after losing to $o?", "How long does this one sting?"),
                c(pen.one("That's on me. Our fans deserved better.", "I didn't have them ready. I'll own it.", "We let the fans down tonight. That's on me."),
                    fans = 1.0, security = 1, reaction = "Fans appreciate the accountability."),
                c(pen.one("We'll see them again. Circle it.", "This isn't over. Not by a long shot.", "Enjoy it. We'll remember it."),
                    fans = 3.0, heat = 2, reaction = "The rivalry just got hotter."),
                c(pen.one("They played better. Give them credit.", "They earned it tonight.", "They made more plays. Simple as that."),
                    fans = -1.0, heat = -1, reaction = "A few fans wanted more fire."), rival = oc?.id ?: -1)
            upsetWin -> p(pen.one("You just beat No. ${opp.ap} $o. Did you believe?", "Biggest win of the year. What changed tonight?", "Are you a top-25 team now, coach?",
                "Upset of the week. Where does this rank for you?"),
                c(pen.one("I believed. The guys believed. That's what matters.", "This group doesn't care about rankings.", "We knew we could play with anybody."),
                    fans = 4.0, mood = 3, recruits = 2, reaction = "A signature win, and recruits noticed."),
                c(pen.one("It's one game. We have to do it again Saturday.", "Big win. Doesn't mean anything if we don't follow it up.", "Proud of them. Now the hard part: doing it again."),
                    security = 2, reaction = "The AD likes the perspective."),
                c(pen.one("Put us in the poll. We belong.", "Rank us. We'll prove it again.", "Nobody's going to want to play us now."),
                    fans = 5.0, heat = 1, recruits = 1, reaction = "Confident, and the fans loved it."))
            upsetLoss -> p(pen.one("That's a bad loss for a ranked team. What happened?", "Coach, $o? Really?", "Is this team as good as its ranking?"),
                c(pen.one("We weren't ready. That's my job.", "I'll take this one. We'll be better.", "I have to have them ready every night. I didn't."),
                    security = 1, fans = 1.0, reaction = "Accountability plays well."),
                c(pen.one("Some guys think the ranking plays for us. It doesn't.", "We got comfortable. We'll get uncomfortable this week.", "We believed our own press clippings."),
                    mood = -4, security = 1, reaction = "A message to the room. It landed."),
                c(pen.one("Credit to them. They were the better team tonight.", "They played great. It happens.", "Every team in this league can beat you."),
                    fans = -2.0, reaction = "The fans wanted more than that."))
            won && margin <= 3 -> p(pen.one("That one came down to the wire. What did you see late?", "Close one, coach. Your heart okay?", "How did you pull that out?",
                "What was the call on the last possession?"),
                c(pen.one("Our guys made winning plays. I just tried to stay out of the way.", "That's toughness. You can't teach it.", "They wanted it more at the end."),
                    mood = 4, fans = 1.0, reaction = "The players loved hearing it."),
                c(pen.one("We shouldn't have needed the last possession.", "We made it harder than it needed to be.", "Too close. We'll clean it up."),
                    security = 1, mood = -1, reaction = "Never satisfied."),
                c(pen.one("I'll take a win any way I can get one.", "A win is a win. We'll sleep fine.", "Ugly, close, whatever. It counts."),
                    fans = 2.0, reaction = "No argument there."))
            !won && margin <= 3 -> p(pen.one("So close. What would you do differently?", "A one-possession loss. How does that sit?", "Tough way to lose. What happened at the end?"),
                c(pen.one("I'd take that last timeout back. That one's on me.", "I put us in a tough spot late. I'll be better.", "My decision at the end didn't work. My fault."),
                    security = 1, fans = 1.0, reaction = "Honest. The players appreciated it."),
                c(pen.one("We had the shot we wanted. I'd run it again.", "Good look, good shooter. Basketball.", "I trust my guys. Same shot, every time."),
                    mood = 4, reaction = "The shooter heard that."),
                c(pen.one("We didn't lose it at the end. We lost it in the first half.", "You don't lose games on the last play.", "Forty minutes. We played about thirty-five."),
                    security = 1, mood = -2, reaction = "Tough love."))
            won && margin >= 20 -> p(pen.one("Dominant performance. Anything you didn't like?", "A $margin-point win. Is this who you are?", "Did you call off the dogs?",
                "That looked easy. Was it?"),
                c(pen.one("The second half got sloppy. We'll fix it.", "We have a lot to clean up, believe it or not.", "I didn't like our defense after halftime."),
                    security = 1, mood = -1, reaction = "Never satisfied, and the AD likes that."),
                c(pen.one("Everybody played, everybody contributed. That's the best part.", "The bench was great. That's what I'll remember.", "Good night for the whole roster."),
                    mood = 4, fans = 1.0, reaction = "The reserves appreciated it."),
                c(pen.one("When we play like that, nobody beats us.", "That's the standard. Get used to it.", "That's who we are."),
                    fans = 4.0, heat = 1, reaction = "The fans want more."))
            !won && margin >= 20 -> p(pen.one("A $margin-point loss. Where do you start?", "That was hard to watch. What happened?", "Did your team quit tonight?"),
                c(pen.one("I start with me. I didn't have them ready.", "That's on the head coach. Period.", "I'll own all of it."),
                    security = 1, fans = 1.0, reaction = "Accountability plays well."),
                c(pen.one("We got punched and didn't punch back. That's unacceptable.", "Soft. There's no other word.", "Some guys need to look in the mirror."),
                    mood = -5, fans = 1.0, reaction = "The players heard every word."),
                c(pen.one("Flush it. One loss. We move on.", "Burn the tape, learn from it, move on.", "It happens. It won't happen again."),
                    fans = -2.0, reaction = "The fans wanted more fire."))
            fresh != null -> {
                val fp = lg.playerOrNull(fresh.pid)
                p(pen.one("${fresh.name} had ${fresh.s.pts}. Is he ready for this stage?", "A freshman with ${fresh.s.pts} points. What did you tell him?", "Did ${fresh.name} just announce himself?"),
                    c(pen.one("He's going to be special. And he's going to work.", "He's as talented as anybody I've coached.", "Get used to that name."),
                        mood = 6, fans = 2.0, recruits = 2, reaction = "${fp?.short ?: "He"} heard it. So did every recruit."),
                    c(pen.one("He's a freshman. He'll have bad nights too.", "Let's not crown him yet.", "Ask me in February."),
                        security = 1, mood = -1, reaction = "Managing expectations."),
                    c(pen.one("His teammates found him. Credit them too.", "The veterans set him up. It's a team.", "He'd tell you it's about the guys around him."),
                        mood = 2, reaction = "The veterans appreciated that."), playerId = fresh.pid)
            }
            top != null && top.s.pts >= 28 -> p(pen.one("${top.name} had ${top.s.pts}. Where does he rank in this league?", "What makes ${topP?.last ?: top.name} so hard to guard?",
                "${top.s.pts} for ${top.name}. Is he the best player in the conference?"),
                c(pen.one("He's the best player in this league, and it's not close.", "I'd put him up against anybody in the country.", "Special. That's the word."),
                    mood = 6, fans = 2.0, recruits = 1, reaction = "${topP?.short ?: "He"} walked taller at practice."),
                c(pen.one("His teammates made it possible. He knows it.", "Great night, great teammates.", "Ask him. He'll credit the guys who set the screens."),
                    mood = 2, reaction = "The supporting cast liked that."),
                c(pen.one("He can be better. He knows it.", "I want more from him on defense.", "Good offensive night. Defense was average."),
                    mood = -3, security = 1, reaction = "A challenge, publicly."), playerId = top.pid)
            !won && u.streak <= -3 -> p(pen.one("That's ${-u.streak} straight. Is the season slipping away?", "How do you stop this skid?", "What's the mood in the locker room?"),
                c(pen.one("We stick together. That's the only way out.", "Nobody in that room has quit.", "We're going to fight our way out of it."),
                    mood = 3, fans = 1.0, reaction = "The room stays together."),
                c(pen.one("Changes are coming. Minutes will be earned.", "Some guys will lose their spots this week.", "Starting tomorrow, nothing is guaranteed."),
                    mood = -4, security = 2, reaction = "The roster got the message."),
                c(pen.one("We're close. We just need one.", "It's a long season.", "One win flips it."),
                    fans = -1.0, reaction = "Callers weren't convinced."))
            won && u.streak >= 4 -> p(pen.one("That's ${u.streak} straight. What's clicking?", "${u.streak} in a row. Are you surprised?", "How do you keep a streak like this going?"),
                c(pen.one("Everybody's bought in. That's the whole secret.", "These guys like each other. It shows.", "We're playing for each other."),
                    mood = 3, fans = 1.0, reaction = "Good for the room."),
                c(pen.one("Streaks don't mean anything in March.", "We haven't won anything yet.", "Nice run. Long way to go."),
                    security = 1, reaction = "Grounded."),
                c(pen.one("Who's going to stop us? I'm asking honestly.", "We're not even at our best yet.", "Keep doubting us."),
                    fans = 4.0, heat = 1, reaction = "The fans are fired up."))
            won -> p(pen.one("Good win tonight. What stood out?", "What did you like about that one?", "Talk about the performance, coach.", "Where did you win this game?"),
                c(pen.one("Our defense. That's who we have to be.", "We guarded. Everything else follows.", "The stops. Every one of them."),
                    security = 1, reaction = "On message."),
                c(pen.one("We shared the ball. Nobody cared who scored.", "The assists. That's a team.", "Unselfish basketball."),
                    mood = 3, reaction = "The players liked that."),
                c(pen.one("Our crowd. They were great tonight.", "The fans carried us early.", "That building was loud. Thank you."),
                    fans = 3.0, reaction = "The fans will be back louder."))
            else -> p(pen.one("What went wrong tonight?", "Tough night. What did you see?", "Where did it get away from you?"),
                c(pen.one("We have to defend better. Simple as that.", "Defense. We didn't guard anybody.", "We gave up too many easy ones."),
                    security = 1, reaction = "Fair."),
                c(pen.one("We'll learn from it and get better.", "Film tomorrow, fix it, move on.", "One game. We'll grow from it."),
                    reaction = "Measured."),
                c(pen.one("The officials had a tough night too.", "I'd like to see some of those calls again.", "I'll get fined if I say what I think about the whistle."),
                    fans = 3.0, security = -2, reaction = "The fans loved it. The league office didn't."))
        }
    }

    // =====================================================================================
    // Conference media day
    // =====================================================================================

    fun mediaDay(lg: League): Prompt? {
        if (!active(lg) || lg.phase != Phase.REGULAR || lg.user.gp > 0 || lg.deskMediaSeason == lg.season) return null
        val u = lg.user
        val step = lg.deskMediaStep
        val pen = Pen(lg.season * 31L + u.id * 7 + step)
        val conf = lg.conf(u.confId)
        val proj = lg.preview?.proj?.get(u.id)
        val confRank = proj?.confRank ?: 0
        val star = lg.rosterOf(u).maxByOrNull { it.ovr }
        val freshmen = lg.rosterOf(u).filter { it.year == 1 }.sortedByDescending { it.ovr }
        val last = u.history.lastOrNull()
        val rival = lg.rivalsOf(u.id).firstOrNull()?.let { lg.teams.getOrNull(it.other(u.id)) }
        val who = pen.any(listOf("a ${conf.abbr} network host", "the league's longtime columnist", "a national reporter", "a student journalist", "the panel moderator", "a TV analyst"))
        val label = "${conf.name} media day · question ${step + 1} of 3"
        fun p(q: String, vararg ch: Choice, playerId: Int = -1) = Prompt("media", label, who, q, ch.toList(), playerId, step = step, steps = 3)
        return when (step) {
            0 -> if (confRank in 1..2) p(pen.one("You were picked ${if (confRank == 1) "first" else "second"} in the league. Can you handle being the hunted?",
                    "Everybody's picking you near the top. How do you feel about that?", "The coaches' poll has you at No. $confRank. Fair?"),
                c("We'll embrace it. We want everybody's best shot.", fans = 3.0, heat = 1, reaction = "The league heard it."),
                c("Preseason polls don't win games. We'll find out in March.", security = 1, reaction = "Coach-speak, but the AD liked it."),
                c("I don't read the polls. My players shouldn't either.", mood = 2, reaction = "A shield for the locker room."))
            else if (confRank >= 8) p(pen.one("You were picked $confRank${Previews.ordinal(confRank).drop("$confRank".length)} in the league. Is that motivation?",
                    "Nobody's picking you this year. Does that bother you?", "Low expectations for your team. Fair?"),
                c("Print it. We'll put it on the wall.", fans = 3.0, mood = 3, reaction = "The players loved it."),
                c("They're probably right right now. Ask me in February.", security = 1, reaction = "Honest. The AD respects it."),
                c("Rankings are for people who don't watch practice.", fans = 2.0, heat = 1, reaction = "A little edge never hurts."))
            else p(pen.one("Where do you see your team finishing?", "What's a successful season for you?", "Middle of the pack in the preseason poll. Where do you belong?"),
                c("At the top. Why else would we play?", fans = 3.0, security = -1, reaction = "Big words. The fans will remember."),
                c("Playing our best basketball in March.", security = 1, reaction = "The right answer, well delivered."),
                c("We'll be better than people think.", fans = 2.0, mood = 2, reaction = "Quiet confidence."))
            1 -> if (star != null && star.ovr >= 72) p(pen.one("Tell us about ${star.name}. What are you expecting from him?", "Is ${star.name} the best player in this league?",
                    "What's the next step for ${star.name}?"),
                c("He's the best player in this conference. I'll say it.", fans = 2.0, mood = 6, recruits = 1, reaction = "${star.short} heard it."),
                c("He's one of five. This is about our team.", mood = 2, security = 1, reaction = "Balanced."),
                c("He's got a lot to prove. He knows it.", mood = -3, security = 1, reaction = "A challenge in public.", ), playerId = star.id)
            else if (freshmen.isNotEmpty()) p(pen.one("You've got new faces. Who's going to surprise people?", "Talk about this freshman class.", "Will any freshmen start for you?"),
                c("${freshmen.first().name}. Remember the name.", fans = 2.0, mood = 4, recruits = 2, reaction = "Recruits watching took note.", ),
                c("They'll earn it in practice. Nothing's given.", security = 1, reaction = "Fair."),
                c("All of them. This is a great class.", fans = 3.0, recruits = 3, mood = 1, reaction = "Every recruit on the board heard it."), playerId = freshmen.first().id)
            else p(pen.one("What's your team's identity?", "How will you play this year?", "What should fans expect to see?"),
                c("We'll guard. Every night.", security = 1, reaction = "Classic."),
                c("Fast. We'll run every chance we get.", fans = 3.0, recruits = 1, reaction = "Fans want to watch that."),
                c("We'll share it. Nobody cares who scores.", mood = 3, reaction = "The roster liked that."))
            else -> if (rival != null) p(pen.one("What about ${rival.school} this year?", "The ${rival.school} game: circled on your calendar?", "Your thoughts on ${rival.school}?"),
                c("Great program. Great rivalry. It's good for the league.", heat = -2, reaction = "Classy."),
                c("We're coming for them. They know it.", fans = 4.0, heat = 3, reaction = "Bulletin-board material."),
                c("Same as any other game on the schedule.", heat = 1, fans = -1.0, reaction = "Nobody believed that."))
            else p(pen.one(if (last != null) "Last season you went ${last.w}-${last.l}. What's different?" else "What's different about this team?",
                    "What did you learn from last season?", "What's the message to your fans this year?"),
                c("Our fans are going to love this team.", fans = 4.0, reaction = "Season-ticket sales ticked up."),
                c("We're tougher. We spent the summer on it.", security = 1, mood = 2, reaction = "Good answer."),
                c("We're better. You'll see.", fans = 2.0, heat = 1, reaction = "Short and confident."))
        }
    }

    // =====================================================================================
    // Senior night
    // =====================================================================================

    /** The last home game of the regular season, if it's next and there are seniors to honor. */
    private fun seniorGame(lg: League): Game? {
        val u = lg.userTeamId
        val home = lg.games.filter { it.home == u && !it.neutral && it.day <= Schedule.REG_DAYS }
        val last = home.maxByOrNull { it.day } ?: return null
        if (last.played) return null
        val next = Season.nextUserGame(lg) ?: return null
        return if (next.id == last.id) last else null
    }

    fun seniorNight(lg: League): Prompt? {
        if (!active(lg) || lg.deskSeniorSeason == lg.season) return null
        val g = seniorGame(lg) ?: return null
        val seniors = lg.rosterOf(lg.user).filter { it.year >= 4 }
        if (seniors.isEmpty()) return null
        val pen = Pen(lg.season * 53L + lg.userTeamId)
        val names = seniors.joinToString(", ") { it.name }
        return Prompt("senior", "Senior night · ${lg.team(g.opp(lg.userTeamId)).school} at home", "The athletic department", pen.one(
            "It's senior night: $names. How do you want to honor them?",
            "The last home game for $names. What's the plan?",
            "Senior night is next. $names will be introduced with their families. Anything special?",
        ), listOf(
            c(pen.one("Start every senior. They earned it.", "Put the seniors on the floor for the opening tip.", "All the seniors start. No debate."),
                mood = 8, fans = 2.0, reaction = "The seniors will never forget it."),
            c(pen.one("Give them the microphone after the game.", "Let the seniors speak to the crowd.", "A speech from each of them at the end."),
                fans = 5.0, mood = 4, recruits = 1, reaction = "Not a dry eye in the building."),
            c(pen.one("Honor them before the game, then it's business.", "Flowers before tip-off. Then we need a win.", "We'll celebrate them, and then we'll compete."),
                security = 2, mood = 1, reaction = "Professional. The AD approved."),
        ), playerId = -2)
    }

    // =====================================================================================
    // The mailbag
    // =====================================================================================

    private class Letter(val from: String, val text: String, val replies: List<Choice>)

    private val LETTERS: List<Letter> = listOf(
        Letter("A 10-year-old in the upper deck", "Coach, I want to play for you one day. What should I practice?", listOf(
            c("Your left hand, every day. And listen to your teachers.", fans = 2.0, reaction = "The letter got framed. So did your reply."),
            c("Come to our summer camp. I'll show you myself.", fans = 3.0, recruits = 1, reaction = "Camp signups jumped."),
            c("Defense. Nobody practices defense. Be the one who does.", fans = 1.0, reaction = "A future point guard nodded."))),
        Letter("A season-ticket holder since 1979", "I've watched every home game for forty years. This team reminds me of the good ones. Thank you.", listOf(
            c("Thank you for forty years. Come say hi at practice.", fans = 3.0, reaction = "He showed up. The players signed his program."),
            c("You've seen more than I have. What do we need?", fans = 2.0, reaction = "He wrote back with four pages of notes."),
            c("The good ones are coming. Stick with us.", fans = 2.0, reaction = "A loyal fan, made more loyal."))),
        Letter("An angry fan", "Your rotations make no sense. My dog could coach this team.", listOf(
            c("Bring the dog to practice. We need a mascot.", fans = 3.0, reaction = "It went viral. The dog has fans now."),
            c("I hear you. I'm working on it.", fans = 1.0, security = 1, reaction = "Disarming."),
            c("No reply.", reaction = "Some letters don't need one."))),
        Letter("A former player", "Coach, I played here twenty years ago. I'd love to help however I can.", listOf(
            c("Come talk to the team. They need to hear it from you.", mood = 4, reaction = "He spoke for twenty minutes. The room was silent."),
            c("Help us recruit. Your story sells this place.", recruits = 2, reaction = "He's been calling recruits' families all week."),
            c("Come to a game. You're always welcome.", fans = 2.0, reaction = "He brought half his old team."))),
        Letter("A booster", "I'd like to give more. What would make the biggest difference?", listOf(
            c("Player development. Strength coaches, film, nutrition.", security = 2, reaction = "A check arrived with a note: 'For the players.'"),
            c("Recruiting. Let's get in more living rooms.", recruits = 3, security = 1, reaction = "The recruiting budget just grew."),
            c("Honestly? Come to more games and bring friends.", fans = 3.0, reaction = "He bought a block of tickets."))),
        Letter("A student", "The student section wants to do something special for the next big game. Any ideas?", listOf(
            c("A white-out. Every seat.", fans = 4.0, reaction = "The white-out sold out in an hour."),
            c("Learn the opposing starters' names. Use them.", fans = 3.0, heat = 1, reaction = "The visitors noticed. So did the league."),
            c("Just be loud. That's all we need.", fans = 2.0, reaction = "They were."))),
        Letter("A rival fan", "Enjoy your season while it lasts. We're coming for you.", listOf(
            c("Thanks for writing. See you in the building.", heat = 1, fans = 1.0, reaction = "The rival fan posted your reply. It backfired on him."),
            c("Tell your coach I said hi.", heat = 2, fans = 2.0, reaction = "Pinned to their message board."),
            c("Ignore it.", reaction = "No oxygen. Smart."))),
        Letter("A parent of a player", "Thank you for how you treat my son. He calls home happy.", listOf(
            c("He's a great kid. You raised him right.", mood = 5, reaction = "Word got around the locker room."),
            c("He works harder than anybody. He earned it.", mood = 4, reaction = "He read it twice."),
            c("Thank you. That means a lot to us.", mood = 3, reaction = "A quiet, good moment."))),
        Letter("A high school coach", "One of my players wants to walk on. Would you give him a look?", listOf(
            c("Send him to open gym. Everyone gets a fair look.", fans = 2.0, recruits = 1, reaction = "High school coaches across the state took note."),
            c("We're full this year. Have him try junior college first.", security = 1, reaction = "Honest, and appreciated."),
            c("I'll call him myself.", fans = 2.0, reaction = "A kid's dream, for at least one afternoon."))),
        Letter("A local business owner", "We'd love to sponsor a team dinner after the next road win.", listOf(
            c("Deal. The guys will be starving.", mood = 3, fans = 1.0, reaction = "Best barbecue on the road trip."),
            c("Sponsor our community clinic instead.", fans = 3.0, reaction = "Two hundred kids got free sneakers."),
            c("Check with compliance first, but thank you.", security = 1, reaction = "By the book."))),
        Letter("A sports radio host", "Come on the show this week. Our callers have questions.", listOf(
            c("Book me. I'll answer anything.", fans = 2.0, reaction = "Ratings were the best of the year."),
            c("I've got my own show. They can call in there.", fans = 1.0, reaction = "Touché."),
            c("After the season.", reaction = "Fair."))),
        Letter("A grandmother", "I've knitted a scarf in school colors for every player. Where should I send them?", listOf(
            c("To the arena. I'll hand them out myself.", mood = 4, fans = 3.0, reaction = "The whole team wore them on the bench."),
            c("Bring them to practice. They'll want to thank you.", mood = 5, fans = 2.0, reaction = "Fifteen hugs. One very happy grandmother."),
            c("Thank you. That's the nicest thing we've gotten all year.", mood = 2, reaction = "The scarves came anyway."))),
        Letter("A critic in the newspaper", "This program has plateaued. Prove me wrong.", listOf(
            c("Watch the next game. That's my answer.", fans = 2.0, security = 1, reaction = "Confident."),
            c("He might be right. We're working on it.", security = 2, reaction = "Disarming, and the AD liked it."),
            c("Pin it in the locker room.", mood = 3, fans = 1.0, reaction = "The players read it every day."))),
        Letter("A recruit's little brother", "My brother says your school is cool. Is it?", listOf(
            c("Tell him the coolest. Come visit with him.", recruits = 3, reaction = "The whole family visited."),
            c("The coolest. We've got the best fans in the country.", recruits = 2, fans = 1.0, reaction = "Recruits talk to their brothers."),
            c("You'll have to see for yourself.", recruits = 1, reaction = "Curious."))),
        Letter("A teacher", "My class wrote you letters for a project. Will you write back?", listOf(
            c("I'll write back to every one.", fans = 3.0, reaction = "Thirty letters. Thirty replies. The class lost its mind."),
            c("Even better: I'll visit the class.", fans = 4.0, reaction = "The players came too."),
            c("Send a team poster, signed.", fans = 2.0, reaction = "It's on the classroom wall."))),
        Letter("An alum overseas", "Watching every game at 3 a.m. from Tokyo. Worth it.", listOf(
            c("Our fans are everywhere. That's the best part.", fans = 2.0, reaction = "Alumni chapters around the world shared it."),
            c("We'll try to win earlier in the game for you.", fans = 3.0, reaction = "A laugh across the time zones."),
            c("Send us a picture of the watch party.", fans = 2.0, reaction = "It's on the arena jumbotron now."))),
        Letter("A cranky alum", "We used to win championships here. What happened?", listOf(
            c("We'll get back there. Give us time.", security = 1, reaction = "Fair."),
            c("Come back and see what we're building.", fans = 2.0, reaction = "He came. He came back again."),
            c("Those teams were great. This one can be too.", fans = 1.0, mood = 2, reaction = "Respect for the past, faith in the present."))),
        Letter("A sports science student", "Can I help with your team's analytics?", listOf(
            c("Yes. Come to the office Monday.", security = 1, mood = 1, reaction = "She found three things the staff missed."),
            c("We're good, but thank you.", reaction = "Polite."),
            c("Start with our free throws. Please.", fans = 2.0, reaction = "Her report went straight to the shooting coach."))),
        Letter("A youth league coach", "Our team of 8-year-olds lost every game. Any advice?", listOf(
            c("Tell them winning starts with having fun.", fans = 2.0, reaction = "The kids got a signed ball."),
            c("Bring them to a practice. They'll learn from the best.", fans = 3.0, mood = 2, reaction = "Twenty very excited 8-year-olds."),
            c("Teach them to play defense. Wins will follow.", fans = 1.0, reaction = "They won their next game."))),
        Letter("A fan who drove 500 miles", "Drove eight hours to see the last road game. We lost. Still worth it.", listOf(
            c("Next time, you're my guest at a home game.", fans = 3.0, reaction = "Front row, two seats."),
            c("We owe you a better game. We'll give you one.", fans = 2.0, mood = 1, reaction = "The players heard about it."),
            c("That's the best fan base in the country.", fans = 2.0, reaction = "Shared everywhere."))),
        Letter("A disappointed fan", "Ticket prices went up and the team got worse.", listOf(
            c("Fair. We have to earn it on the floor.", fans = 1.0, security = 1, reaction = "Honest."),
            c("Talk to the ticket office. I'll talk to the team.", fans = -1.0, reaction = "A shrug. Not great."),
            c("I'll get you two tickets on me.", fans = 2.0, reaction = "One happy fan, at least."))),
        Letter("A former coach of yours", "Proud of you. Remember what I told you: the players come first.", listOf(
            c("Every day. Thank you for teaching me.", mood = 2, reaction = "The players heard about the letter."),
            c("Come visit. I want them to meet you.", mood = 3, reaction = "He told them stories for an hour."),
            c("I still use your drills.", reaction = "He wrote back: 'I know. I watch.'"))),
        Letter("A local reporter", "Can I follow the team for a week for a long feature?", listOf(
            c("Full access. Show people who these guys are.", fans = 4.0, recruits = 2, reaction = "The feature ran on the front page."),
            c("Practice only. Games are ours.", fans = 2.0, reaction = "A good story anyway."),
            c("Not during the season.", security = 1, reaction = "Fair, and the players appreciated the privacy."))),
        Letter("A nervous freshman's mom", "My son is homesick. Is he going to be okay?", listOf(
            c("He's going to be fine. We'll take care of him.", mood = 4, reaction = "He had dinner at your house that weekend."),
            c("I'll pair him with a senior. They'll look out for him.", mood = 5, reaction = "The senior took it seriously."),
            c("Homesick is normal. He's tougher than he thinks.", mood = 2, reaction = "Reassured."))),
        Letter("A former rival player", "I hated playing against your teams. Respect.", listOf(
            c("Respect back. Those were great games.", heat = -1, fans = 1.0, reaction = "Shared by both fan bases. Rare."),
            c("Good. That was the idea.", fans = 2.0, reaction = "A laugh."),
            c("Come to a game. I'll get you a seat behind our bench.", heat = -1, reaction = "He came, and the crowd booed him lovingly."))),
    )

    private fun mailStamp(lg: League) = lg.season * 100 + lg.day / 14

    fun mail(lg: League): Prompt? {
        if (!active(lg) || lg.phase != Phase.REGULAR || lg.user.gp < 2 || lg.deskMailStamp == mailStamp(lg)) return null
        val pen = Pen(mailStamp(lg) * 977L + lg.userTeamId)
        val picks = LETTERS.shuffled(pen.r).take(2)
        val i = lg.deskMailAnswered.coerceIn(0, 1)
        val letter = picks[i]
        return Prompt("mail", "The mailbag · letter ${i + 1} of 2", letter.from, "\"${letter.text}\"", letter.replies, step = i, steps = 2)
    }

    // =====================================================================================
    // The preseason prediction
    // =====================================================================================

    fun predictionDue(lg: League) = active(lg) && lg.phase == Phase.REGULAR && lg.user.gp == 0 && lg.deskPredSeason != lg.season

    class Bet(val id: String, val label: String, val detail: String, val win: Int, val lose: Int)

    fun bets(lg: League): List<Bet> {
        val last = lg.user.history.lastOrNull()
        return listOf(
            Bet("conf", "We win the ${lg.conf(lg.user.confId).abbr} regular season", "Finish first in the league. Hit: job security +8, fans +6. Miss: security -4.", 8, -4),
            Bet("ncaa", "We make the NCAA Tournament", "Get into the field of 68. Hit: security +5, fans +4. Miss: security -3.", 5, -3),
            Bet("better", if (last != null) "We beat last year's ${last.w} wins" else "We finish above .500", "Hit: security +3, fans +2. Miss: security -1.", 3, -1),
        )
    }

    fun predict(lg: League, id: String) {
        lg.deskPredSeason = lg.season
        lg.deskPredBet = id
        val b = bets(lg).firstOrNull { it.id == id } ?: return
        lg.news += News(lg.season, lg.day, "${lg.userCoach.name} makes a bold preseason call", true, kind = "column", teamId = lg.userTeamId,
            body = "\"${b.label}.\" ${lg.userCoach.last} said it at the podium, on the record. We'll see in March.", source = "Hoops Wire")
    }

    /** At season's end: was the coach right? */
    fun gradePrediction(lg: League) {
        if (!active(lg) || lg.deskPredSeason != lg.season || lg.deskPredBet.isEmpty() || lg.deskPredGraded == lg.season) return
        lg.deskPredGraded = lg.season
        val u = lg.user
        val b = bets(lg).firstOrNull { it.id == lg.deskPredBet } ?: return
        val hit = when (b.id) {
            "conf" -> runCatching { Season.standings(lg, u.confId).firstOrNull()?.id == u.id }.getOrDefault(false)
            "ncaa" -> lg.ncaa?.seeds?.containsKey(u.id) == true
            else -> u.history.lastOrNull()?.takeIf { it.season < lg.season }?.let { u.w > it.w } ?: (u.w > u.l)
        }
        val c = lg.userCoach
        c.security = (c.security + if (hit) b.win else b.lose).coerceIn(0, 100)
        Fans.ensure(u)
        if (hit) u.fans = (u.fans + b.win * 0.75).coerceAtMost(100.0)
        Alerts.add(lg, if (hit) "Called it" else "The prediction missed", if (hit) "\"${b.label}.\" You said it in October, and you were right." else "\"${b.label}.\" It didn't happen, and people remember.", "")
        lg.news += News(lg.season, lg.day, if (hit) "${c.name} called his shot, and delivered" else "${c.name}'s bold prediction falls short", true, kind = "column", teamId = u.id,
            body = "Back in the preseason: \"${b.label}.\" " + if (hit) "It happened. The bosses noticed." else "It didn't. The bosses noticed that too.", source = "Hoops Wire")
    }

    // =====================================================================================
    // Answering
    // =====================================================================================

    /** What's on the desk right now, most time-sensitive first. */
    fun current(lg: League): List<Prompt> = listOfNotNull(press(lg), mediaDay(lg), seniorNight(lg), mail(lg))

    fun answer(lg: League, pr: Prompt, i: Int): String {
        val ch = pr.choices.getOrNull(i) ?: return ""
        val u = lg.user
        Fans.ensure(u)
        u.fans = (u.fans + ch.fans).coerceIn(5.0, 100.0)
        val coach = lg.userCoach
        coach.security = (coach.security + ch.security).coerceIn(0, 100)
        if (ch.mood != 0) {
            when {
                pr.playerId >= 0 -> lg.playerOrNull(pr.playerId)?.let { it.happiness = (it.happiness + ch.mood).coerceIn(0, 100) }
                pr.playerId == -2 -> for (p in lg.rosterOf(u).filter { it.year >= 4 }) p.happiness = (p.happiness + ch.mood).coerceIn(0, 100)
                else -> for (p in lg.rosterOf(u)) p.happiness = (p.happiness + ch.mood / 2).coerceIn(0, 100)
            }
        }
        if (ch.heat != 0) {
            val targets = if (pr.rivalCoachId >= 0) listOf(pr.rivalCoachId) else CoachRivals.rivals(lg).map { it.coachId }
            for (id in targets) {
                val r = CoachRivals.of(lg, id) ?: CoachRival(id).also { lg.coachRivals += it }
                r.heat = (r.heat + ch.heat).coerceIn(0, 12)
            }
        }
        if (ch.recruits > 0) for (r in lg.recruits) if (r.committed < 0 && (r.onBoard || u.id in r.interest)) r.interest[u.id] = ((r.interest[u.id] ?: 4) + ch.recruits).coerceAtMost(100)
        when (pr.kind) {
            "press" -> lg.deskPressGame = lastGame(lg)?.id ?: lg.deskPressGame
            "media" -> { lg.deskMediaStep++; if (lg.deskMediaStep >= 3) { lg.deskMediaSeason = lg.season; lg.deskMediaStep = 0 } }
            "senior" -> {
                lg.deskSeniorSeason = lg.season
                val seniors = lg.rosterOf(u).filter { it.year >= 4 }
                lg.news += News(lg.season, lg.day, "Senior night at ${u.school}: ${seniors.joinToString(", ") { it.last }}", true, kind = "column", teamId = u.id,
                    body = "${u.school} honors ${seniors.joinToString(", ") { it.name }} before the last home game. ${ch.reaction}", source = "Hoops Wire")
            }
            "mail" -> { lg.deskMailAnswered++; if (lg.deskMailAnswered >= 2) { lg.deskMailStamp = mailStamp(lg); lg.deskMailAnswered = 0 } }
        }
        return ch.reaction
    }

    fun skip(lg: League, pr: Prompt) {
        when (pr.kind) {
            "press" -> lg.deskPressGame = lastGame(lg)?.id ?: lg.deskPressGame
            "media" -> { lg.deskMediaSeason = lg.season; lg.deskMediaStep = 0 }
            "senior" -> lg.deskSeniorSeason = lg.season
            "mail" -> { lg.deskMailStamp = mailStamp(lg); lg.deskMailAnswered = 0 }
        }
    }
}
