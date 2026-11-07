package com.nh.hoopscoach.game

import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

/**
 * The postgame podium, about his career. Every question that fits tonight is a candidate, ranked by how
 * much it matters: a game-winner, the first game against the team that traded him, his rival, a career
 * high, the end of a season, the fit after a trade, the award race, the All-Star vote, a hot week or a
 * slump (with the numbers), the teams that passed on him in the draft, a contract year, the draft board
 * in college, the standings, his role. The top one gets asked (topics from his last few pressers wait
 * their turn), with his numbers in the question and answers that speak to it. His team's beat writer
 * asks most of them; national writers turn up for the big ones.
 */
object Press {
    private class Ask(val priority: Int, val topic: String, val prompt: String, val answers: List<PressAnswer>, val national: Boolean = false)

    private fun humble(text: String, fans: Int = 1, mates: Int = 3) = PressAnswer(text, 0.012, fans = fans, teammates = mates, persona = -1)
    private fun brash(text: String, mates: Int = -2, fans: Int = 0) = PressAnswer(text, 0.04, fans = fans, teammates = mates, persona = 2)
    private fun even(text: String) = PressAnswer(text, 0.006)

    private val NATIONAL = listOf("Hoops Wire", "The Daily Dribble", "Courtside Report", "Baseline Sports", "The Rim", "Fast Break Network", "Above the Break")

    /** His team's beat writer: the same person for years (a new one every four seasons). */
    fun beat(lg: League, t: Team): String {
        val rnd = Random(t.id * 7919 + (if (lg.pro) 104_729 else 0) + lg.season / 4)
        val name = "${Names.first[rnd.nextInt(Names.first.size)]} ${Names.last[rnd.nextInt(Names.last.size)]}"
        return "$name · ${if (lg.pro) "The ${t.school} Herald" else "The ${t.school} Daily"}"
    }

    /** Topics that only come up once in a while (the season's storylines), and the rest (a week or so apart). */
    private val RARE = setOf("Draft night", "Rookie race", "Contract year", "Contender", "NIL", "Draft stock", "Freshman year", "Rookie year", "Pressure", "All-Star", "Playoff race", "Award race")

    private fun coolingDown(lg: League, r: RoadState, topic: String): Boolean {
        val last = r.pressAsked[topic] ?: return false
        if (last / 1000 != lg.season) return false
        // Pro seasons run about three times as many days as college ones.
        val days = (if (topic in RARE) 15 else 6) * (if (lg.pro) 3 else 1)
        return lg.day - last % 1000 < days
    }

    private fun national() = "${Rng.pick(Names.first)} ${Rng.pick(Names.last)} · ${Rng.pick(NATIONAL)}"
    private fun f1(d: Double) = "%.1f".format(d)
    private fun ord(n: Int) = "$n" + when { n % 100 in 11..13 -> "th"; n % 10 == 1 -> "st"; n % 10 == 2 -> "nd"; n % 10 == 3 -> "rd"; else -> "th" }

    fun question(
        lg: League, g: Game, h: Player, line: GameLogEntry, t: Team, opp: Team,
        won: Boolean, rivalry: Boolean, winner: Boolean, duel: Duel?,
    ): PressQuestion? {
        val r = lg.road ?: return null
        val f = Feed.Form(lg, h, line)
        val pro = lg.pro
        val asks = mutableListOf<Ask>()
        fun ask(priority: Int, topic: String, prompt: String, vararg a: PressAnswer, national: Boolean = false) { asks += Ask(priority, topic, prompt, a.toList(), national) }

        val norm = f.norm
        val triple = listOf(line.pts, line.reb, line.ast).count { it >= 10 } >= 3
        val big = triple || (line.pts >= 14 && line.pts >= max(norm * 1.5, norm + 7)) || line.pts >= 30
        val quiet = line.min >= 15 && norm >= 10 && line.pts <= norm * 0.5
        val best = Feed.careerBest(lg, f)
        val careerHigh = best >= 0 && line.pts > best && line.pts >= 12 && (f.before.size >= 4 || h.career.isNotEmpty())
        val margin = abs(g.scoreFor(t.id) - g.scoreAgainst(t.id))
        val mpg = f.before.takeIf { it.isNotEmpty() }?.map { it.min }?.average() ?: h.stats.mpg
        val tourney = g.type in listOf(GameType.NCAA, GameType.CONF_TOURNEY, GameType.NIT, GameType.PLAYOFF)
        val stat = "${line.pts} points" + (if (line.reb >= 8) ", ${line.reb} rebounds" else "") + (if (line.ast >= 6) ", ${line.ast} assists" else "")

        // 1. The game-winner.
        if (winner) ask(100, "Game-winner", Rng.pick(listOf("That shot is everywhere already. Walk us through the last possession.", "Clock winding down, ball in your hands. What were you thinking?")),
            humble("Coach drew it up and my guys trusted me. I just had to make it.", fans = 2, mates = 4),
            brash("I want the ball in that moment. Every single time."),
            even("Good look, good shot. It went in."), national = true)

        // 2. His old team, the first time he sees them this season.
        if (opp.id in f.former && f.before.none { it.oppId == opp.id }) {
            val traded = pro && r.proTeams.lastOrNull { it != t.id } == opp.id && r.tradedSeason > 0
            val how = when { !pro -> "since you transferred"; traded -> "since they traded you"; else -> "since you left" }
            ask(95, "Old team", "First time against ${if (pro) "the ${opp.name}" else opp.school} $how${if (big) ", and you had $stat" else ""}. Did this one mean more?",
                humble("Nothing but love for that place. It was good to see familiar faces.", fans = 1, mates = 1),
                if (won) brash("They made their choice. Tonight I made them think about it.", mates = 0) else brash("They got this one. Circle the next one.", mates = 0),
                even("It's one game. We needed the win${if (won) ", and we got it" else ""}."), national = true)
        }

        // 3. His rival, with the head-to-head.
        if (duel != null) Rival.player(lg)?.let { rv ->
            val w = r.duels.count { it.won }
            val l = r.duels.size - w
            val score = when {
                duel.me > duel.him -> "You outscored ${rv.last} ${duel.me} to ${duel.him}"
                duel.me == duel.him -> "You and ${rv.last} both had ${duel.me}"
                else -> "${rv.last} outscored you ${duel.him} to ${duel.me}, but your team won"
            }
            if (duel.won) ask(90, "Rival", "$score. That's $w-$l against him since high school. Is he the best player in your class?",
                humble("He's great. He makes me better every time.", mates = 2),
                brash("There's one best player in this class. It isn't him.", mates = -1),
                even("It's not about him. We got the win."), national = true)
            else ask(90, "Rival", (when { duel.me > duel.him -> "You outscored ${rv.last} ${duel.me} to ${duel.him}, but his team won"; duel.me == duel.him -> "You and ${rv.last} both had ${duel.me}, but his team won"; else -> "${rv.last} had ${duel.him} to your ${duel.me}, and his team won" }) + ". You're $w-$l against him now. Does that one sting?",
                humble("He made plays. Credit to him.", mates = 0),
                brash("Enjoy it. It won't happen again.", mates = -1),
                even("We'll see them again. I'll be ready."))
        }

        // 4. A career high, against what it used to be.
        if (careerHigh && (line.pts >= 30 || line.pts >= best + 3)) ask(85, "Career high", "Career-high ${line.pts} tonight${if (best >= 10) ", topping the $best you had before" else ""}${if (!f.starter) ", off the bench" else ""}. What clicked?",
            humble("My teammates kept finding me. I just had to finish."),
            brash("Honestly? I expected it. There's more where that came from.", mates = -1),
            even("Felt good from warmups. I just kept shooting."))

        // 5. The postseason: where the series stands, or the end of a season (and maybe of college).
        if (g.type == GameType.PLAYOFF) {
            val s = lg.proState?.series?.firstOrNull { g.id in it.gameIds }
            val through = s != null && ProSeason.seriesStatus(lg, s).contains(" win ")
            if (won && through) ask(80, "Playoffs", ProSeason.winQuestion(lg, g, t.id) + if (big) " You had $stat." else "",
                even("Whoever's next. We'll be ready."), brash("Give us the best team in the league.", mates = 0), humble("All credit to our staff. They had us ready."))
            else if (won) ask(80, "Playoffs", ProSeason.winQuestion(lg, g, t.id) + if (big) " You had $stat." else "",
                humble("Everybody's locked in. Nobody cares who scores.", mates = 3), brash("We're the better team. We'll keep showing it.", mates = 0), even("Series isn't over. Next game."))
            else if (s != null) {
                val status = ProSeason.seriesStatus(lg, s)
                val over = status.contains(" win ")
                ask(80, if (over) "Season over" else "Playoffs", if (over) "That's your season. ${status}. What do you take from this one?" else "$status. What has to change next game?",
                    humble(if (over) "I have to come back better. This team deserved more from me." else "It starts with me. I have to be better.", fans = 2),
                    brash(if (over) "We'll be back. Mark it down." else "We're fine. We'll take care of business at home.", mates = 0),
                    even(if (over) "It hurts. We'll learn from it." else "Watch the film, fix it, go again."))
            }
        } else if (!pro && g.type == GameType.NCAA) {
            if (won) ask(80, "March", "You're moving on in the NCAA Tournament, and you had $stat. What's different about this team in March?",
                humble("Everybody's bought in. Nobody cares who scores.", mates = 4), brash("We're the best team left. Simple as that.", mates = 0), even("Survive and advance. That's all it is."))
            else {
                val board = Offseason.draftBoard(lg)[h.id]
                ask(80, "Season over", when {
                    h.year >= 4 -> "That's the last game of your college career. What will you remember about ${t.school}?"
                    board != null && board <= 40 -> "The season's over, and you're ${ord(board)} on the draft boards. Are you coming back to ${t.school}, or is it time?"
                    else -> "The season's over. What do you have to add before next year?"
                },
                    humble(if (h.year >= 4) "The people. My teammates, the coaches, the fans. This place made me." else "I have to get better this summer. This team deserved more from me.", fans = 3),
                    brash(if (board != null && board <= 40 && h.year < 4) "I'm ready for the league. Everybody knows it." else "We'll be back here, and we'll win it.", mates = -1),
                    even(if (board != null && board <= 40 && h.year < 4) "I'll sit down with my family and decide." else "It hurts. We'll learn from it."))
            }
        }

        // 6. The fit after a trade.
        val old = if (pro) r.proTeams.dropLast(1).lastOrNull()?.let { lg.teams.getOrNull(it) } else null
        if (pro && old != null && r.tradedSeason == lg.season && lg.day - r.tradedDay in 0..20) {
            val since = h.log.filter { e -> (lg.games.firstOrNull { it.id == e.gameId }?.day ?: 0) >= r.tradedDay }
            if (since.size >= 2) {
                val w = since.count { it.won }
                ask(75, "New team", "You're $w-${since.size - w} with the ${t.mascot} since the trade from ${old.school}, at ${f1(since.map { it.pts }.average())} points a game. How's the fit?",
                    humble("Guys made it easy. I'm still learning the playbook, but this locker room is special.", mates = 4),
                    brash("I'm the piece they were missing. You can see it.", mates = -2),
                    even("It takes time. We're getting there."))
            }
        }

        // 7. The award races.
        val race = if (lg.day > 20 && h.stats.gp >= 10) Road.raceRank(lg) else 0
        if (race in 1..3 && (big || won)) {
            val leader = Awards.ladder(lg).firstOrNull()?.first
            ask(70, "Award race", if (race == 1) "You're No. 1 on the ${f.award} ladder at ${Awards.line(h)}. Is it yours to lose?"
                else "You're No. $race in the ${f.award} race${leader?.let { ", behind ${it.name}" } ?: ""}. Can you catch him?",
                humble("Individual stuff takes care of itself if we win.", fans = 2),
                brash("Look at the numbers. Who else is it going to be?", mates = -2, fans = 1),
                even("I hear it. I just try to stay locked in."), national = true)
        }
        if (pro && f.rookie && h.stats.gp >= 10) {
            val rookies = Awards.ladder(lg).filter { it.first.proYears == 0 }
            val roy = rookies.indexOfFirst { it.first.id == h.id } + 1
            if (roy in 1..3) ask(65, "Rookie race", if (roy == 1) "You're the Rookie of the Year favorite at ${Awards.line(h)}. Do you look at that?"
                else "You're No. $roy in the Rookie of the Year race, behind ${rookies.first().first.name}. Do you look at that?",
                humble("I just want to help us win. The vets have taught me a lot.", mates = 3),
                brash("It's mine. I'm not even thinking about anybody else.", mates = -2),
                even("Long season. I'll worry about it in April."))
        }

        // 8. The All-Star vote.
        if (pro && lg.phase == Phase.REGULAR && ProLeague.allStarDay(lg) - lg.day in 1..12 && (h.stats.ppg >= 17 || race in 1..15) &&
            h.awards.none { it == "${lg.season} All-Star" }) {
            val n = h.awards.count { it.endsWith(" All-Star") }
            ask(68, "All-Star", "All-Star rosters come out in ${ProLeague.allStarDay(lg) - lg.day} days. You're at ${Awards.line(h)}. ${if (n == 0) "Is this the year you make your first one?" else "Do you deserve a spot?"}",
                humble("If it happens, it's because of my teammates. If not, we keep winning.", mates = 3),
                brash("Look at the numbers. I should be there.", mates = -1, fans = 1),
                even("Not up to me. I just have to keep playing."))
        }

        // 9. A hot week or a slump, with the numbers.
        val recent = (f.before.takeLast(4).map { it.pts } + line.pts)
        val earlier = f.before.dropLast(4).takeIf { it.size >= 4 }?.map { it.pts }?.average()
        if (recent.size == 5 && earlier != null && recent.average() >= earlier + 6 && recent.average() >= 14)
            ask(60, "Hot streak", "You're averaging ${f1(recent.average())} over your last five, up from ${f1(earlier)} before that. What changed?",
                humble("Coaches showed me where my shots come from, and my teammates are finding me.", mates = 3),
                brash("Nothing changed. People are just paying attention now.", mates = -1),
                even("More reps, more confidence. Same work."))
        if (recent.size == 5 && norm >= 12 && recent.takeLast(4).average() <= norm * 0.62 && line.pts <= norm * 0.7)
            ask(60, "Slump", "${line.pts} tonight, ${f1(recent.takeLast(4).average())} a game over your last four, down from ${f1(norm)}. What's going on?",
                humble("I have to be better. I'll be in the gym early tomorrow.", fans = 2, mates = 1),
                brash("One bad week. Check back in a month.", mates = 0),
                even("Teams are loading up on me. I have to find other ways to help."))

        // 10. The teams that passed on him.
        if (pro && h.proYears <= 2 && (f.pick >= 20 || f.undrafted) && big)
            ask(55, "Draft night", "You went ${f.pickWords} in the draft, and tonight you had $stat. Do you think about the teams that passed?",
                humble("I'm just grateful somebody gave me a chance.", mates = 2),
                brash("I remember every one of them.", mates = 0, fans = 1),
                even("The draft's over. I worry about being better tomorrow."))

        // 11. His contract.
        val c = r.contract
        if (pro && c != null && c.years == 1 && h.stats.gp >= 10 && lg.phase == Phase.REGULAR)
            ask(50, "Contract year", "It's the last year of your deal, ${Road.dollars(c.salary)} a season, and you're at ${Awards.line(h)}. Are you thinking about free agency?",
                humble("I'd love to stay here. My agent handles the rest.", fans = 3),
                brash("I'm betting on myself. Somebody's going to have to pay up.", mates = -1),
                even("Not during the season. I'll deal with it in July."))
        if (pro && c != null && quiet && c.salary >= 15_000 && lg.rosterOf(t).all { it.id == h.id || Payroll.salaryOf(lg, it) <= c.salary })
            ask(52, "Pressure", "You're the highest-paid player on this team, and you had ${line.pts} tonight. Do you feel that pressure?",
                humble("Pressure comes with it. I owe this team more than that.", fans = 2),
                brash("I don't play for the contract. One night doesn't define me.", mates = 0),
                even("Some nights the shots don't fall."))

        // 12. College: the draft board, NIL money, senior night, freshman year.
        if (!pro && h.stats.gp >= 10 && (h.year >= 2 || norm >= 15) && Rng.chance(0.35)) {
            val board = Offseason.draftBoard(lg)[h.id]
            if (board != null && board <= 40) ask(50, "Draft stock", "The draft boards have you ${ord(board)}. Is this your last year at ${t.school}?",
                PressAnswer("I'm focused on ${t.school}. That decision comes after the season.", 0.012, fans = 3, persona = -1),
                brash("I'm ready for the league. Everybody knows it.", mates = -3),
                even("My family and I will sit down after the season."))
        }
        if (!pro && h.nil >= 300 && big && Rng.chance(0.3))
            ask(40, "NIL", "Your NIL deal is worth ${Road.dollars(h.nil)}. Nights like this, $stat, is that what they're paying for?",
                humble("I just play. The money's a blessing for my family.", fans = 2),
                brash("They're paying for a star. They got one.", mates = -2),
                even("I don't think about it on the court."))
        if (!pro && h.year == 4 && g.home == t.id && g.type == GameType.CONF &&
            lg.games.none { !it.played && it.home == t.id && (it.type == GameType.CONF || it.type == GameType.NONCONF) })
            ask(70, "Senior night", "That was your last home game at ${t.school}. What has this place meant to you?",
                humble("Everything. These fans, my teammates, the coaches. This place made me.", fans = 4, mates = 3),
                brash("I gave them four years of buckets. They'll remember me.", mates = -1, fans = 1),
                even("Hard to put into words. I'll miss it."))
        if (!pro && f.rookie && h.stats.gp in 3..12 && Rng.chance(0.4))
            ask(35, "Freshman year", "What's been the biggest adjustment from high school to ${lg.conf(t.confId).name} basketball?",
                humble("Everybody's strong and smart. The older guys have helped me a lot.", mates = 4),
                brash("Honestly? It's easier than I thought.", mates = -3),
                even("The speed. Every possession matters."))
        if (pro && f.rookie && h.stats.gp in 3..15 && Rng.chance(0.4))
            ask(35, "Rookie year", "${if (r.collegeSchools.isNotEmpty()) "From ${r.collegeSchools.substringAfterLast("→ ").trim()} to the league." else "Welcome to the league."} What's been the biggest adjustment?",
                humble("Everybody's good. The vets have taught me a lot.", mates = 4),
                brash("Honestly? It's easier than I thought.", mates = -3),
                even("The speed. Every possession matters."))

        // 13. The standings.
        val gp = t.w + t.l
        if (pro && lg.phase == Phase.REGULAR && gp >= 55 && f.standing in 5..11)
            ask(45, "Playoff race", "You're ${t.record}, ${ord(f.standing)} in the ${f.confName}. ${if (f.standing <= 6) "Can you stay out of the play-in?" else if (f.standing <= 10) "Is this a play-in team or a playoff team?" else "Is there time to get into the play-in?"}",
                humble("We just have to take care of our own games.", mates = 2),
                brash("Nobody wants to see us in April.", mates = 0, fans = 2),
                even("One game at a time. The standings take care of themselves."))
        if (pro && lg.phase == Phase.REGULAR && gp >= 40 && f.standing == 1 && won)
            ask(45, "Contender", "You're ${t.record}, the best record in the ${f.confName}. Is this a title team?",
                humble("We haven't done anything yet. Ask me in June.", fans = 1),
                brash("Yes. And it's not close.", mates = 0, fans = 2),
                even("We like where we are. We have to keep getting better."))
        if (!pro && t.ap in 1..10 && won && h.stats.gp >= 10 && Rng.chance(0.4))
            ask(45, "Contender", "${t.school} is No. ${t.ap} in the country. Can this team win it all?",
                humble("We haven't done anything yet. Ask me in April.", fans = 1),
                brash("Yes. Write it down.", mates = 0, fans = 2),
                even("We have the pieces. We have to keep getting better."))

        // 14. His role.
        if (line.min >= mpg + 10 && line.min >= 25 && mpg <= 26 && f.before.size >= 5)
            ask(40, "Bigger role", "You played ${line.min} minutes tonight, way above your usual ${mpg.toInt()}. Did you earn a bigger role?",
                humble("Whatever coach needs. I'm ready when my number's called.", mates = 2),
                brash("I should have been out there all along.", mates = -2),
                even("Coach makes those calls. I just play."))
        if (!f.starter && big && h.stats.gp >= 8)
            ask(42, "Sixth man", "$stat off the bench. Should you be starting?",
                humble("I don't care if I start. I care if I finish.", mates = 3),
                brash("I think everybody knows the answer.", mates = -3),
                even("Coach knows best. I just bring energy."))
        if (line.min in 1..14 && mpg >= 20)
            ask(40, "Minutes", "Only ${line.min} minutes tonight, after ${mpg.toInt()} a game. Are you happy with your role?",
                humble("Whatever coach needs. I'll be ready.", mates = 2),
                brash("I should be on the floor. I think everyone knows that.", mates = -3),
                even("Some nights it goes like that."))

        // 15. Ordinary nights.
        if (big) ask(30, "Big night", Rng.pick(listOf("$stat tonight. What was working?", "You took over against the ${opp.mascot}: $stat. What did you see?")),
            humble("My teammates found me in great spots.", mates = 4),
            brash("Nobody ${if (pro) "in this league" else "in this conference"} can guard me.", mates = -5),
            even("Just taking what the defense gives me."))
        if (t.streak >= 5) ask(25, "Win streak", "That's ${t.streak} straight, and you're ${t.record}. What's clicking?",
            humble("Everybody's bought in. Nobody cares who gets the credit.", mates = 3),
            brash("We're the best team ${if (pro) "in the league" else "in the conference"} right now.", mates = 0, fans = 2),
            even("We're stacking days. Can't get comfortable."))
        if (t.streak <= -4) ask(30, "Losing streak", "That's ${-t.streak} losses in a row, and you're ${t.record}. How do you stop the slide?",
            humble("It starts with me. I have to be better for this group.", fans = 2),
            brash("Some guys need to look in the mirror. I'm doing my part.", mates = -5),
            even("One game at a time. Win the next one."))
        if (!won && line.min >= 12 && (rivalry || tourney || margin <= 5 || margin >= 20))
            ask(22, "Tough loss", if (margin >= 20) "The ${opp.mascot} won by $margin. What happened?" else "You had ${line.pts}, and it came down to the end against the ${opp.mascot}. What decided it?",
                humble("That's on me. I'll be better.", mates = 2),
                PressAnswer("Ask the refs. They decided that one.", 0.02, fans = -1, persona = 2),
                even("We'll watch the film and learn from it."))
        if (rivalry && won) ask(28, "Rivalry", "You beat ${opp.school}. What does this one mean?",
            humble("This one's for our fans. They've waited all year.", fans = 3, mates = 0),
            brash("They're not on our level. Not even close.", fans = 1),
            even("It's one game. On to the next."))

        // The biggest story wins, with a little luck; his last few topics wait their turn.
        val pick = asks.filter { it.priority >= 80 || !coolingDown(lg, r, it.topic) }.maxByOrNull { it.priority + Rng.int(0, 12) } ?: return null
        if (pick.priority < 30 && Rng.chance(0.5)) return null
        r.pressAsked[pick.topic] = lg.season * 1000 + lg.day
        val reporter = if (pick.national || Rng.chance(0.2)) national() else beat(lg, t)
        return PressQuestion(lg.season, g.id, pick.topic, pick.prompt, pick.answers, reporter)
    }
}
