package com.nh.hoopscoach.game

/**
 * What happens inside a team over a season, with the coach's call each time (college coaching careers):
 *  - locker room incidents: a player late, a post that went viral, a scuffle, a class problem; handle it,
 *    and a suspension sits him for games;
 *  - pregame speeches before the big ones (rivals, ranked teams, March), which set the room's mood;
 *  - the bracket challenge on Selection Sunday: pick a champion, graded when the tournament ends.
 */
object Clubhouse {
    class Option(val text: String, val suspend: Int = 0, val mood: Int = 0, val team: Int = 0, val security: Int = 0, val fans: Double = 0.0, val reaction: String)
    class Incident(val title: String, val text: String, val playerId: Int, val options: List<Option>)

    fun active(lg: League) = !lg.pro && !lg.playerMode && lg.userTeamId >= 0

    /** Incidents and speeches also run in an NBA coaching career (the bracket doesn't). */
    fun roomActive(lg: League) = !lg.playerMode && lg.userTeamId >= 0 && (!lg.pro || NbaCoach.active(lg))

    /** College-only situations, left out of an NBA locker room. */
    private val COLLEGE_ONLY = setOf("Homesick", "Grades", "An agent calling", "NBA scouts", "Walk-on spark", "Last chance", "Big ego")

    private fun o(text: String, suspend: Int = 0, mood: Int = 0, team: Int = 0, security: Int = 0, fans: Double = 0.0, reaction: String) =
        Option(text, suspend, mood, team, security, fans, reaction)

    // ---------- incidents ----------

    /** Who an incident is about: the star, a freshman, a reserve or anyone. */
    private enum class Who { STAR, FRESH, BENCH, ANY, SENIOR }

    private class Kind(val who: Who, val title: String, val texts: List<String>, val options: (Player) -> List<Option>)

    private val KINDS: List<Kind> = listOf(
        Kind(Who.ANY, "Late to practice", listOf("{p} showed up twenty minutes late to practice. Second time this month.", "{p} overslept and missed the start of film. The team noticed."), { p -> listOf(
            o("Sit him for the next game.", suspend = 1, mood = -8, team = 2, security = 1, reaction = "The message landed. Nobody's been late since."),
            o("Extra conditioning, before sunrise.", mood = -3, team = 1, reaction = "${p.last} ran until the sun came up. Point made."),
            o("Talk to him privately. Let it go this time.", mood = 3, team = -2, reaction = "${p.last} appreciated it. A few teammates didn't.")) }),
        Kind(Who.STAR, "A viral post", listOf("{p} posted something about playing time that went viral. Everyone's asking about it.", "A clip of {p} complaining about his role is all over social media."), { p -> listOf(
            o("Bench him to start the next game.", mood = -6, team = 2, security = 1, reaction = "He came off the bench. He scored 20. He got it."),
            o("Make him apologize to the team.", mood = -2, team = 3, reaction = "He stood up in front of everyone. The room respected it."),
            o("Defend him. Players have a right to speak.", mood = 6, team = -2, fans = -1.0, reaction = "${p.last} is grateful. Some veterans rolled their eyes.")) }),
        Kind(Who.ANY, "A practice scuffle", listOf("{p} and a teammate got into a shoving match at practice. It took three guys to separate them.", "Tempers flared in a scrimmage. {p} threw the first punch."), { p -> listOf(
            o("Suspend him two games.", suspend = 2, mood = -10, team = 3, security = 2, reaction = "Zero tolerance. The program's standard is clear."),
            o("Both of them run until they apologize.", mood = -3, team = 2, reaction = "They hugged it out somewhere around the fortieth sprint."),
            o("Competitive fire. I like it, as long as it ends there.", mood = 2, team = -3, security = -1, reaction = "Some players liked it. The AD didn't.")) }),
        Kind(Who.FRESH, "Homesick", listOf("{p} has been struggling. He's homesick and talking about leaving.", "{p} called his mom four times this week. He wants to go home."), { p -> listOf(
            o("Invite his family to campus for a weekend.", mood = 10, team = 1, fans = 1.0, reaction = "His mom cooked for the whole team. He's smiling again."),
            o("Pair him with a senior mentor.", mood = 6, team = 2, reaction = "The senior took it seriously. They're inseparable now."),
            o("Tell him to push through. It's part of growing up.", mood = -5, reaction = "He's still here, but he's quiet.")) }),
        Kind(Who.ANY, "Grades", listOf("The academic office says {p} is falling behind in two classes.", "{p} missed three tutoring sessions. His eligibility could be in question."), { p -> listOf(
            o("He sits until the grades are fixed.", suspend = 2, mood = -6, team = 1, security = 3, reaction = "He's in the library every night now. The president's office noticed."),
            o("Mandatory study hall, every night.", mood = -2, security = 2, reaction = "His grades turned around in two weeks."),
            o("Handle it quietly with the tutors.", mood = 2, security = -1, reaction = "It worked, this time.")) }),
        Kind(Who.STAR, "An agent calling", listOf("Word is an agent has been in contact with {p}'s family.", "Someone close to {p} has been talking to an agent. The compliance office called."), { p -> listOf(
            o("Bring in compliance and the family. Do it by the book.", mood = -2, security = 3, reaction = "Clean, transparent and documented."),
            o("Talk to {p} yourself. Make sure he knows the rules.", mood = 2, security = 1, reaction = "He understood. Nothing more came of it."),
            o("Ignore it. Not my business.", security = -3, reaction = "It might come back around.")) }),
        Kind(Who.BENCH, "Wants more minutes", listOf("{p} came to your office asking for more minutes.", "{p} says he's thinking about transferring if his role doesn't grow."), { p -> listOf(
            o("Earn it in practice. Then we'll talk.", mood = -2, team = 2, reaction = "He went to work. Practice got more competitive."),
            o("Promise him a bigger role.", mood = 8, team = -2, reaction = "Happy for now. Others are watching to see if you keep that promise."),
            o("If he wants to leave, I won't stop him.", mood = -8, team = -1, reaction = "He's looking at the portal.")) }),
        Kind(Who.SENIOR, "A leader speaks up", listOf("{p} asked for a players-only meeting after the last loss.", "{p} wants the seniors to run the next practice."), { p -> listOf(
            o("Let them have the room. Trust the seniors.", mood = 6, team = 4, reaction = "Whatever they said, it worked."),
            o("I'll be in the room too.", mood = -2, team = 1, reaction = "The meeting was shorter than planned."),
            o("We don't do players-only meetings here.", mood = -5, team = -2, security = 1, reaction = "Old school. The seniors weren't happy.")) }),
        Kind(Who.ANY, "A night out", listOf("{p} was spotted at a party until 3 a.m. the night before a game.", "Photos of {p} out late before a road game are going around campus."), { p -> listOf(
            o("He doesn't start the next game.", mood = -6, team = 2, security = 1, reaction = "Rules are rules."),
            o("Team curfew from now on. Everybody.", mood = -2, team = -2, security = 2, reaction = "The whole roster grumbled. The AD loved it."),
            o("He's an adult. Just play well.", mood = 3, team = -1, fans = -1.0, reaction = "Fans on the message boards weren't impressed.")) }),
        Kind(Who.ANY, "Off-court good deed", listOf("{p} has been reading to kids at the local hospital every week. The local news wants a story.", "{p} organized a food drive with his teammates. Nobody told you."), { p -> listOf(
            o("Let the cameras in. People should see this.", mood = 5, fans = 4.0, team = 1, reaction = "The story ran everywhere. Recruits' parents noticed."),
            o("Join him next week.", mood = 6, fans = 3.0, reaction = "The whole team showed up."),
            o("Keep it quiet. That's what makes it good.", mood = 3, team = 2, reaction = "Humble. The room respects him even more.")) }),
        Kind(Who.STAR, "Playing hurt", listOf("{p} has a sore ankle. He wants to play through it.", "The trainers say {p} should rest. He's begging to play."), { p -> listOf(
            o("Rest him a game. The season is long.", suspend = 1, mood = -3, security = 1, reaction = "He fumed for a day. The ankle thanked you."),
            o("Let him play limited minutes.", mood = 3, reaction = "He played. He was careful. Fine for now."),
            o("If he wants to play, he plays.", mood = 6, team = 1, security = -1, reaction = "Tough kid. Hopefully it holds up.")) }),
        Kind(Who.FRESH, "Big ego", listOf("{p} has been telling teammates he's the best player on the team.", "{p} refused to do the freshman chores. The seniors are livid."), { p -> listOf(
            o("Freshman chores, double, for a month.", mood = -4, team = 4, reaction = "Humbled. The seniors are satisfied."),
            o("Let the seniors handle it.", mood = -2, team = 2, reaction = "They handled it. Nobody will say how."),
            o("Confidence isn't a crime.", mood = 4, team = -3, reaction = "He's happy. The locker room isn't.")) }),
        Kind(Who.ANY, "Family emergency", listOf("{p} got a call from home. A family member is in the hospital.", "{p}'s grandmother is sick. He wants to go home."), { p -> listOf(
            o("Go home. Take as long as you need.", suspend = 2, mood = 10, team = 3, fans = 1.0, reaction = "The team wore his number on their wristbands."),
            o("Go, and be back for the weekend.", suspend = 1, mood = 6, team = 2, reaction = "He made it back. He played with a heavy heart, and played well."),
            o("We need you here this week.", mood = -10, team = -3, security = -1, reaction = "That one will be talked about for a long time.")) }),
        Kind(Who.BENCH, "Walk-on spark", listOf("{p} has been the best player in practice all week. The team is buzzing about him.", "The reserves say {p} deserves a shot."), { p -> listOf(
            o("Give him real minutes next game.", mood = 10, team = 3, fans = 2.0, reaction = "The bench exploded every time he touched the ball."),
            o("Start him on senior night style: one possession.", mood = 6, team = 2, fans = 1.0, reaction = "The crowd loved it."),
            o("Practice is practice. Games are games.", mood = -3, reaction = "He kept working.")) }),
        Kind(Who.ANY, "Social media beef", listOf("{p} is going back and forth online with a player from a rival school.", "{p} and a rival player are trading insults online."), { p -> listOf(
            o("Off social media until the season ends.", mood = -5, team = 1, security = 2, reaction = "Quiet. Focused."),
            o("Let him talk. Then he'd better back it up.", mood = 4, fans = 2.0, team = -1, reaction = "The rivalry game just got a lot more interesting."),
            o("Make him apologize publicly.", mood = -3, security = 1, reaction = "Classy. The rival school noticed.")) }),
        Kind(Who.STAR, "NBA scouts", listOf("Scouts were at practice to watch {p}. He seemed distracted afterward.", "{p} is reading mock drafts every night."), { p -> listOf(
            o("Tell him winning is what gets you drafted.", mood = 2, team = 2, reaction = "Back to work. Good."),
            o("Close practices to scouts until March.", mood = -3, team = 1, security = 1, reaction = "No distractions."),
            o("Help him plan for the draft. It's his future.", mood = 6, team = -1, reaction = "He trusts you completely now.")) }),
        Kind(Who.ANY, "Missed curfew on the road", listOf("{p} broke curfew at the team hotel.", "Hotel security found {p} in the lobby at 2 a.m."), { p -> listOf(
            o("Sent home on the next flight.", suspend = 1, mood = -8, team = 3, security = 2, reaction = "The rest of the trip was very, very quiet."),
            o("He sits the first half.", mood = -4, team = 2, reaction = "Message received."),
            o("Warning. Next time it's serious.", mood = 1, team = -2, reaction = "A few guys tested the line the next night.")) }),
        Kind(Who.SENIOR, "Last chance", listOf("{p} asks to start the next game. It's his last season and he's never started.", "{p} has given four years to the program. He wants one start."), { p -> listOf(
            o("He starts.", mood = 12, team = 4, fans = 2.0, reaction = "He hit his first shot. The bench lost it."),
            o("If he earns it this week, he starts.", mood = 3, team = 1, reaction = "He's been the best player at practice since."),
            o("Starts are earned, not given.", mood = -6, team = -2, reaction = "He understood. It hurt.")) }),
        Kind(Who.FRESH, "Viral highlight", listOf("{p}'s dunk in practice went viral. He has 200,000 new followers.", "A video of {p} is everywhere. Brands are calling."), { p -> listOf(
            o("Enjoy it. Then get back to work.", mood = 4, team = 1, fans = 2.0, reaction = "Grounded and happy."),
            o("Help him handle the NIL offers right.", mood = 6, security = 1, reaction = "He signed a deal. Clean and smart."),
            o("No phones in the building.", mood = -4, team = 2, reaction = "The rule is now a team rule.")) }),
        Kind(Who.ANY, "Effort questioned", listOf("Assistants say {p} has been coasting in practice.", "{p} has been jogging back on defense. The film is ugly."), { p -> listOf(
            o("Show him the film in front of the team.", mood = -6, team = 3, reaction = "He's been sprinting ever since."),
            o("Pull his minutes until it changes.", mood = -5, team = 2, security = 1, reaction = "It changed."),
            o("Ask him what's wrong.", mood = 4, reaction = "Something was going on at home. You'll keep an eye on it.")) }),
    )

    private fun stamp(lg: League) = lg.season * 100 + lg.day / (if (lg.pro) 20 else 9)

    /** This stretch's incident, if one happens (about half the time), the same however often it's drawn. */
    fun incident(lg: League): Incident? {
        if (!roomActive(lg) || lg.phase != Phase.REGULAR || lg.user.gp < 3 || lg.deskIncidentStamp == stamp(lg)) return null
        val rnd = kotlin.random.Random(stamp(lg) * 7907L + lg.userTeamId)
        if (rnd.nextDouble() > 0.5) return null
        val roster = lg.rosterOf(lg.user).filter { !it.out }
        if (roster.size < 6) return null
        val kinds = KINDS.filter { !lg.pro || it.title !in COLLEGE_ONLY }.shuffled(rnd)
        for (k in kinds) {
            val byOvr = roster.sortedByDescending { it.ovr }
            val p = when (k.who) {
                Who.STAR -> byOvr.first()
                Who.FRESH -> roster.filter { it.year == 1 }.randomOrNull(rnd)
                Who.BENCH -> byOvr.drop(7).randomOrNull(rnd)
                Who.SENIOR -> roster.filter { it.year >= 4 }.randomOrNull(rnd)
                Who.ANY -> roster.random(rnd)
            } ?: continue
            val text = k.texts[rnd.nextInt(k.texts.size)].replace("{p}", p.name)
            return Incident(k.title, text, p.id, k.options(p).map { Option(it.text.replace("{p}", p.name), it.suspend, it.mood, it.team, it.security, it.fans, it.reaction) })
        }
        return null
    }

    fun resolve(lg: League, inc: Incident, i: Int): String {
        val op = inc.options.getOrNull(i) ?: return ""
        val u = lg.user
        lg.playerOrNull(inc.playerId)?.let { p ->
            p.happiness = (p.happiness + op.mood).coerceIn(0, 100)
            if (op.suspend > 0 && !p.out) {
                p.injury = when (inc.title) { "Playing hurt" -> "a sore ankle"; "Family emergency" -> "a family matter"; else -> "a suspension" }
                p.injGames = op.suspend
                if (u.plan.auto) Tactics.autoAllocate(lg, u)
            }
        }
        if (op.team != 0) for (q in lg.rosterOf(u)) if (q.id != inc.playerId) q.happiness = (q.happiness + op.team).coerceIn(0, 100)
        lg.userCoach.security = (lg.userCoach.security + op.security).coerceIn(0, 100)
        Fans.ensure(u)
        u.fans = (u.fans + op.fans).coerceIn(5.0, 100.0)
        if (op.suspend > 0 && inc.title !in setOf("Playing hurt", "Family emergency")) lg.playerOrNull(inc.playerId)?.let { p ->
            lg.news += News(lg.season, lg.day, "${if (lg.pro) "The ${u.name}" else u.school} suspend${if (lg.pro) "" else "s"} ${p.name} for ${op.suspend} game${if (op.suspend == 1) "" else "s"}", true, kind = "move", teamId = u.id, pid = p.id,
                body = "${lg.userCoach.name} sat ${p.last} for a violation of team rules. \"${op.text}\"", source = "Hoops Wire")
        }
        lg.deskIncidentStamp = stamp(lg)
        return op.reaction
    }

    fun skipIncident(lg: League) { lg.deskIncidentStamp = stamp(lg) }

    // ---------- pregame speeches ----------

    class Speech(val label: String, val text: String, val team: Int, val security: Int = 0, val fans: Double = 0.0, val reaction: String)

    /** Today's game, if it's a big one and the speech hasn't been given. */
    fun bigGame(lg: League): Game? {
        if (!roomActive(lg)) return null
        val g = (if (lg.pro) lg.games.filter { !it.played && it.involves(lg.userTeamId) && it.day == lg.day }.minByOrNull { it.id } else Season.userGameToday(lg)) ?: return null
        if (g.id == lg.deskSpeechGame) return null
        val opp = lg.team(g.opp(lg.userTeamId))
        val big = lg.rivalry(g) != null || g.type == GameType.PLAYOFF || (lg.pro && opp.w + opp.l >= 10 && opp.winPct >= 0.65) ||
            (!lg.pro && (opp.ap in 1..25 || g.type == GameType.NCAA || g.type == GameType.CONF_TOURNEY))
        return if (big) g else null
    }

    fun why(lg: League, g: Game): String {
        val opp = lg.team(g.opp(lg.userTeamId))
        if (lg.pro) return when {
            g.type == GameType.PLAYOFF -> "Playoffs: the ${opp.name}"
            lg.rivalry(g) != null -> "Rivalry game: the ${opp.name}"
            else -> "The ${opp.record} ${opp.name}"
        }
        return when {
            g.type == GameType.NCAA -> "NCAA Tournament: ${opp.school}"
            g.type == GameType.CONF_TOURNEY -> "Conference tournament: ${opp.school}"
            lg.rivalry(g) != null -> "Rivalry game: ${opp.school}"
            else -> "No. ${opp.ap} ${opp.school}"
        }
    }

    fun speeches(lg: League, g: Game): List<Speech> {
        val rnd = kotlin.random.Random(g.id * 31L + lg.season)
        fun one(vararg o: String) = o[rnd.nextInt(o.size)]
        val opp = lg.team(g.opp(lg.userTeamId))
        val march = g.type == GameType.NCAA || g.type == GameType.CONF_TOURNEY || g.type == GameType.PLAYOFF
        return listOf(
            Speech("Fire them up", one(
                "Nobody in that building thinks we can win. Go prove every one of them wrong.",
                "They've been talking all week. Tonight we answer. Every possession.",
                if (march) "Lose and go home. I'm not ready to go home. Are you?" else "This is the game they'll talk about for years. Make it ours.",
                "Forty minutes. Leave everything on that floor. Everything.",
            ), team = 6, fans = 1.0, reaction = "They came out of the tunnel like they were shot out of a cannon."),
            Speech("Keep them calm", one(
                "It's just basketball. We've done this a hundred times. Trust the work.",
                "Breathe. Play our game. The scoreboard takes care of itself.",
                "Don't play the name on their jersey. Play the guy in front of you.",
                "Same as practice. Same as every day. Let's go.",
            ), team = 3, security = 1, reaction = "Loose, focused and ready."),
            Speech("The game plan", one(
                "Take away their best player. Make anybody else beat us.",
                "Win the glass, take care of the ball, and we win. That's it.",
                "${if (lg.pro) "The ${opp.name.substringAfterLast(' ')}" else opp.school} want${if (lg.pro) "" else "s"} to run. Make them walk it up all night.",
                "They can't guard us inside. Feed the post until they prove they can.",
            ), team = 2, security = 2, reaction = "Everybody knew the job."),
        )
    }

    fun speak(lg: League, g: Game, s: Speech): String {
        val u = lg.user
        for (p in lg.rosterOf(u)) p.happiness = (p.happiness + s.team).coerceIn(0, 100)
        lg.userCoach.security = (lg.userCoach.security + s.security).coerceIn(0, 100)
        Fans.ensure(u)
        u.fans = (u.fans + s.fans).coerceIn(5.0, 100.0)
        lg.deskSpeechGame = g.id
        return s.reaction
    }

    // ---------- the bracket challenge ----------

    /** Selection Sunday: the field is set and nothing's been played yet. */
    fun bracketDue(lg: League): Boolean {
        if (!active(lg) || lg.phase != Phase.NCAA || lg.deskBracketSeason == lg.season) return false
        val b = lg.ncaa ?: return false
        return b.current == 0 && b.gameIds.flatten().none { it >= 0 && lg.game(it).played }
    }

    /** The teams worth picking: every top-4 seed, and the user's team if it's in. */
    fun bracketChoices(lg: League): List<Team> {
        val b = lg.ncaa ?: return emptyList()
        val picks = b.seeds.filter { it.value <= 4 }.keys.toMutableList()
        if (lg.userTeamId in b.seeds && lg.userTeamId !in picks) picks += lg.userTeamId
        return picks.map { lg.team(it) }.sortedWith(compareBy<Team> { b.seeds[it.id] ?: 99 }.thenByDescending { it.elo })
    }

    fun pickChampion(lg: League, teamId: Int) {
        lg.deskBracketSeason = lg.season
        lg.deskBracketPick = teamId
        val t = lg.team(teamId)
        lg.news += News(lg.season, lg.day, "${lg.userCoach.name} picks ${t.school} to win it all", true, kind = "column", teamId = lg.userTeamId,
            body = if (teamId == lg.userTeamId) "His own team. \"Who else would I pick?\"" else "The coach filled out his bracket like everybody else. His champion: ${t.school}.", source = "Hoops Wire")
    }

    /** At season's end: did the coach's champion win? */
    fun gradeBracket(lg: League) {
        if (!active(lg) || lg.deskBracketSeason != lg.season || lg.deskBracketPick < 0 || lg.deskBracketGraded == lg.season) return
        lg.deskBracketGraded = lg.season
        val champ = lg.ncaa?.champion ?: return
        if (champ < 0) return
        val u = lg.user
        val pick = lg.team(lg.deskBracketPick)
        Fans.ensure(u)
        if (champ == lg.deskBracketPick) {
            u.fans = (u.fans + 3.0).coerceAtMost(100.0)
            lg.userCoach.security = (lg.userCoach.security + 2).coerceAtMost(100)
            Alerts.add(lg, "Your bracket hit", "You picked ${pick.school} to win it all, and they did.", "")
            lg.news += News(lg.season, lg.day, "${lg.userCoach.name}'s bracket: perfect at the top", true, kind = "column", teamId = u.id,
                body = "He picked ${pick.school} on Selection Sunday. ${if (pick.id == u.id) "His own team. Of course it was." else "Somebody give the man a TV show."}", source = "Hoops Wire")
        } else {
            lg.news += News(lg.season, lg.day, "${lg.userCoach.name}'s bracket is busted", true, kind = "column", teamId = u.id,
                body = "He had ${pick.school}. ${lg.team(champ).school} won it. Join the club, coach.", source = "Hoops Wire")
        }
    }
}
