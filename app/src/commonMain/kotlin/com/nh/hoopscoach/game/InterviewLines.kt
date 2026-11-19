package com.nh.hoopscoach.game

import com.nh.hoopscoach.game.Interviews.Answer
import com.nh.hoopscoach.game.Interviews.Question

/**
 * More of what athletic directors ask: questions that fit each kind of program, and a pool any of them
 * might ask. Answers carry what they show the AD: "win", "steady", "build" or "recruit".
 */
internal object InterviewLines {
    private fun q(text: String, win: String, steady: String, build: String, recruit: String, vararg drop: String): Question {
        val all = listOf(Answer(win, "win"), Answer(steady, "steady"), Answer(build, "build"), Answer(recruit, "recruit"))
        // Three answers a question, a different three each time it's asked would be unstable, so drop the one named.
        return Question(text, all.filter { it.wants !in drop }.take(3))
    }

    val MORE: Map<String, List<Question>> = mapOf(
        "blueblood" to listOf(
            q("The last coach went to three Final Fours and we let him go. What does that tell you?", "That the standard here is titles, and I'm fine with that.", "That consistency isn't enough. I'll bring both.", "That something underneath needed fixing.", "That the roster has to be elite every single year.", "build"),
            q("Our former players are on TV every night. How do you handle a room full of legends?", "I'll welcome them, and then I'll beat their records.", "With respect. Their door is always open.", "By making the current team their team too.", "By using them. Nothing recruits like a legend.", "steady"),
            q("We play on national TV forty times a year. Are you ready for that spotlight?", "I've been waiting for that spotlight my whole career.", "The cameras don't change how I coach.", "The spotlight is for the players. I'll stay out of it.", "Forty national games is the best recruiting pitch in America.", "build"),
            q("What would you say to a fan base that thinks a Sweet 16 is a bad year?", "They're right. I think so too.", "I'd ask them to judge the whole decade, not one March.", "I'd tell them the foundation matters, and then I'd win.", "I'd tell them the next class is coming.", "steady"),
            q("Every recruit in America takes our calls. Who do you want?", "The best player in the country, every year.", "Players who'll stay three or four years.", "Players who will be better as seniors than freshmen.", "Everyone. We're in on all of them until they say no.", "build"),
        ),
        "rebuild" to listOf(
            q("Our gym is half empty. How do you get people back in the seats?", "Win. Nothing sells tickets like winning.", "Be in the community every week until they come.", "Give them a team worth watching grow.", "Bring in players people want to see.", "steady"),
            q("We haven't made the tournament in a long time. What's realistic for year one?", "The tournament. Why not?", "A winning record and a team that competes every night.", "Better than last year. Then better again.", "A recruiting class that changes everything.", "win"),
            q("The roster you'd inherit is thin. Keep them or start over?", "Keep the ones who can play now and replace the rest.", "Keep almost all of them. Loyalty matters.", "Coach up who's here before judging anybody.", "Start over. The portal is open.", "steady"),
            q("Why would a good coach take a job like ours?", "Because I can win here faster than anyone thinks.", "Because I want to be somewhere for a long time.", "Because building something is the best job in coaching.", "Because the players are out there, and nobody's recruiting them.", "steady"),
            q("What do you say to boosters who've given up on this program?", "Give me one season. Then decide.", "Come to a practice. You'll see the difference.", "Watch the habits change before the scoreboard does.", "Help me land the first big recruit. The rest follows.", "build"),
        ),
        "midmajor" to listOf(
            q("Our league gets one bid. How do you live with that pressure every March?", "By winning the league so it doesn't matter.", "By building teams that peak in March every year.", "By playing a schedule that gets us ready.", "By having more talent than the league.", "build"),
            q("Big schools will try to hire you if you win. What then?", "Then I'll have won here, which is the point.", "I'd tell them I'm happy where I am.", "I'd want to finish what I started.", "I'd use it to sell recruits on our success.", "steady"),
            q("How do you schedule against power conferences without getting embarrassed?", "Play them anywhere. Upsets build programs.", "Pick your spots and grow into it.", "Play them when we're ready, not before.", "Play them, because recruits watch those games.", "steady"),
            q("Our best players transfer up as soon as they're good. How do you stop that?", "Win enough that they don't want to leave.", "Build relationships that last four years.", "Develop the next one before the last one goes.", "Recruit enough talent that one loss doesn't hurt.", "build"),
            q("What does a Cinderella run do for a school like ours?", "Changes everything. I want to make one.", "Pays the bills for a decade. We have to be ready.", "Means nothing if it isn't built to last.", "Puts us in living rooms we can't reach now.", "steady"),
        ),
        "money" to listOf(
            q("Our donors want a say. How do you handle that?", "They can say whatever they want. I'll win.", "I'll listen, and I'll keep the basketball decisions.", "I'll show them the plan and keep them in it.", "I'll put them to work on the recruiting trail.", "build"),
            q("We could buy the best roster in America next year. Should we?", "Absolutely. Why wait?", "Some of it. Continuity still matters.", "Not if it means no culture.", "Yes, and keep buying every year.", "steady"),
            q("If you had an unlimited staff budget, what would you hire?", "The best assistant coaches in the country.", "A full player-development staff.", "A sports scientist and a skills coach for every position.", "Recruiters in every major city.", "steady"),
            q("We spent big last year and missed the tournament. What went wrong?", "Wrong players. I'll pick better ones.", "No continuity. Money can't buy chemistry.", "No foundation under the stars.", "Not enough talent at the top.", "steady"),
            q("How do you make sure our money doesn't get us in trouble with the NCAA?", "Win clean. That's the only way I know.", "Compliance in every meeting, from day one.", "Build a program that doesn't need shortcuts.", "Pay market value and document everything.", "win"),
        ),
        "steady" to listOf(
            q("We're good every year but never great. How do you take the next step?", "Recruit one great player and the rest follows.", "Keep doing what works and get a little better each year.", "Find the one thing that keeps us from great and fix it.", "Raise our recruiting ceiling.", "steady"),
            q("Our fans are loyal but quiet. How do you get them loud?", "Win big games. They'll get loud.", "Be part of the community.", "Play an exciting style they can believe in.", "Bring in players they've heard of.", "win"),
            q("What would make you leave a good job?", "Nothing short of a chance to win titles.", "Very little. I'm loyal.", "Unfinished business elsewhere. I don't have any.", "Not being able to get players.", "build"),
            q("How do you handle a season that goes sideways in January?", "I win a game to stop the bleeding, then another.", "I stay the same. The players watch how I react.", "I go back to basics and teach.", "I look at who wants to play.", "recruit"),
            q("What's the first thing you'd change about our program?", "The expectations. They're too low.", "Very little. It's a good program.", "The practice habits.", "The recruiting footprint.", "steady"),
        ),
    )

    /** Questions any athletic director might ask. */
    val GENERAL: List<Question> = listOf(
        q("Tell me about your coaching philosophy in one sentence.", "Win the next game.", "Be the same coach every day.", "Teach the game, and the wins follow.", "Get the best players and let them play.", "build"),
        q("How do you handle a star player who breaks team rules?", "He sits. Even in a big game.", "Same rules for everyone. Always.", "Teach him why the rule matters, then sit him.", "Handle it privately. Stars need managing.", "recruit"),
        q("What's your approach to the transfer portal?", "Use it every year to stay ahead.", "Sparingly. I want players to stay.", "As a supplement to developing our own.", "It's the best recruiting tool we have.", "steady"),
        q("Where do you want this program in five years?", "Hanging a banner.", "In the tournament every year.", "With a foundation that doesn't depend on me.", "Recruiting at a national level.", "steady"),
        q("Who would be on your staff?", "Proven winners, even if they cost us.", "People I trust who've been with me for years.", "Teachers. Guys who love player development.", "The best recruiters I can find.", "build"),
        q("What's your biggest weakness as a coach?", "I hate losing more than I should.", "I'm too patient sometimes.", "I take on too much myself.", "I chase recruits too hard.", "steady"),
        q("How do you build relationships with boosters?", "By winning. Winning is the best relationship.", "By being honest and showing up.", "By showing them the plan and where the money goes.", "By making them part of the recruiting effort.", "steady"),
        q("How would you handle a scandal in your first year?", "Get the facts, fix it, keep winning.", "Cooperate fully and communicate.", "Use it to set the culture once and for all.", "Make sure it doesn't hurt recruiting.", "recruit"),
        q("How do you feel about playing freshmen?", "If they're the best players, they play.", "They earn minutes like everyone else.", "Carefully. I want them ready, not rushed.", "I sell freshmen on minutes. I keep my word.", "steady"),
        q("Our academics are demanding. Can you win here anyway?", "Yes. Smart players win close games.", "Yes. I've done it before.", "Yes. Better students make better teams over time.", "Yes. It's a selling point to the right families.", "steady"),
        q("How involved should the head coach be in the community?", "As much as it helps us win.", "Very. This is my home now.", "Enough to show the players what it means.", "Every camp, every clinic. That's recruiting.", "win"),
        q("What would you do in your first week?", "Watch every game from last season and set a rotation.", "Meet every player one on one.", "Set the practice standards on day one.", "Get on the phone with every recruit we've offered.", "win"),
        q("How do you deal with a bad loss in public?", "Own it and promise it won't happen again.", "Same as a win. Calm, even.", "Use it as a teaching moment.", "Move on quickly. Recruits are watching.", "recruit"),
        q("What style of basketball would we play?", "Whatever wins. I'll adapt to the roster.", "Disciplined. Defense and execution.", "A system the players grow into over four years.", "Fast and fun. Players want to play that way.", "steady"),
        q("Why should we hire you over the other candidates?", "Because I'll win sooner.", "Because I'll be here for a long time.", "Because I'll build something that outlasts me.", "Because I'll bring better players.", "steady"),
        q("How do you measure success beyond wins?", "I don't, really. Wins are the measure.", "Graduation rates and players who come back to visit.", "How much better each player gets.", "How many top players want to come here.", "steady"),
        q("How do you keep players happy in the NIL era?", "Winning keeps players happy.", "Honest relationships and fair treatment.", "Development. Players stay where they get better.", "Make sure our collective competes.", "win"),
        q("What would your former players say about you?", "That I made them winners.", "That I was the same guy every day.", "That they left better than they came.", "That I kept every promise I made in their living room.", "steady"),
        q("Our rival has dominated us lately. What's your plan?", "Beat them. First chance we get.", "Close the gap year by year.", "Build a program that's better than theirs at the roots.", "Beat them on the recruiting trail first.", "steady"),
        q("What do you do in the summer?", "Watch film of every loss we had.", "Spend time with the players and their families.", "Run skill work every day.", "Live on the AAU circuit.", "steady"),
    )
}
