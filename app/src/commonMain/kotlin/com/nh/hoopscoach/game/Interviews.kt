package com.nh.hoopscoach.game

import kotlin.math.roundToInt

/**
 * Interviewing for a job. The athletic director asks three questions; what he wants to hear depends on
 * the program (a blue blood wants titles now, a rebuild wants patience, a rich school wants recruiting).
 * Answers and the coach's record make the score: a great interview brings more money and an extra year,
 * a bad one gets the offer pulled. Then he can push for more, which works or annoys them.
 */
object Interviews {
    class Answer(val text: String, val wants: String)
    class Question(val text: String, val answers: List<Answer>)

    /** The kind of program doing the hiring: what its questions are about. */
    fun kind(lg: League, t: Team): String = when {
        t.prestige >= 80 -> "blueblood"
        t.prestige < 45 || t.history.takeLast(3).count { it.w < it.l } >= 2 -> "rebuild"
        lg.conf(t.confId).tier >= 1 -> "midmajor"
        Finance.budget(lg, t) > Finance.donor(60) * 1.4 -> "money"
        else -> "steady"
    }

    fun kindLabel(k: String) = when (k) {
        "blueblood" -> "A blue blood"; "rebuild" -> "A rebuild"; "midmajor" -> "A mid-major"; "money" -> "A program with money"; else -> "A steady program"
    }

    private val POOLS: Map<String, List<Question>> = mapOf(
        "blueblood" to listOf(
            Question("There are banners in our rafters. How many are you going to add?", listOf(
                Answer("At least one. This program should play in April every year.", "win"),
                Answer("I'll keep us in the hunt every season. Banners follow.", "steady"),
                Answer("I'll recruit the players who hang them.", "recruit"))),
            Question("Our fans expect a Final Four. How do you handle that pressure?", listOf(
                Answer("I want that pressure. It's why I'm here.", "win"),
                Answer("By building a program that's good every year, not just some years.", "steady"),
                Answer("By building it the right way, even if it takes a year.", "build"))),
            Question("Five-stars want to be here. What do you sell them?", listOf(
                Answer("A path to the NBA, and a national title on the way.", "win"),
                Answer("The best development in the country.", "build"),
                Answer("Everything: the brand, the NIL, the stage.", "recruit"))),
        ),
        "rebuild" to listOf(
            Question("We've been losing. How long until we're winning?", listOf(
                Answer("Year one. I don't believe in waiting.", "win"),
                Answer("Give me three years and you won't recognize this place.", "build"),
                Answer("We'll be competitive right away and better every year.", "steady"))),
            Question("Our facilities and budget aren't the best. How do you recruit here?", listOf(
                Answer("I'll outwork everybody for the kids nobody else sees.", "build"),
                Answer("Transfers who want minutes and a fresh start.", "win"),
                Answer("Sell the opportunity: play right away and build something.", "recruit"))),
            Question("What does patience look like to you?", listOf(
                Answer("Measuring progress by more than wins in year one.", "build"),
                Answer("I'm not a patient man. Neither should you be.", "win"),
                Answer("Small steps every season.", "steady"))),
        ),
        "midmajor" to listOf(
            Question("Is this job a stepping stone for you?", listOf(
                Answer("If I win here, I'll have options. I want to win here first.", "win"),
                Answer("I want to build something that lasts, right here.", "steady"),
                Answer("This can be a destination, not a stop.", "build"))),
            Question("How do you beat the power-conference schools in March?", listOf(
                Answer("Older, tougher teams that know each other.", "build"),
                Answer("Shooting and pace. Make them uncomfortable.", "win"),
                Answer("Find the players they missed.", "recruit"))),
            Question("We win our league but can't get past the first weekend. Why?", listOf(
                Answer("Not enough talent at the top. I'll fix that.", "recruit"),
                Answer("Not enough experience. I'll build it.", "build"),
                Answer("We play scared. My teams won't.", "win"))),
        ),
        "money" to listOf(
            Question("Our boosters are ready to spend. How would you use it?", listOf(
                Answer("The best NIL roster money can buy, right now.", "win"),
                Answer("Recruiting staff and development, then players.", "recruit"),
                Answer("Carefully. Money doesn't build culture.", "steady"))),
            Question("How do you keep a roster together with the portal open?", listOf(
                Answer("Pay them what they're worth and win.", "recruit"),
                Answer("Build relationships so they don't want to leave.", "steady"),
                Answer("Replace them with better ones.", "win"))),
            Question("What would you do in your first hundred days?", listOf(
                Answer("Meet every donor and booster in the state.", "recruit"),
                Answer("Sit down with every player on the roster.", "steady"),
                Answer("Hire the best staff money can buy and get to work.", "win"))),
        ),
        "steady" to listOf(
            Question("Where is this program in three years?", listOf(
                Answer("Cutting down the nets. We're built to win now.", "win"),
                Answer("In the tournament every March, year after year.", "steady"),
                Answer("Rebuilt from the ground up, with a foundation that lasts.", "build"))),
            Question("How do you build a roster?", listOf(
                Answer("The portal and NIL. Proven players, right away.", "win"),
                Answer("High school recruits we develop for four years.", "build"),
                Answer("Whatever gets the best players here. We outwork everyone on the trail.", "recruit"))),
            Question("Our last coach left after three years. Will you?", listOf(
                Answer("I'm here to stay. This is the job I want.", "steady"),
                Answer("If we're winning titles, why would I leave?", "win"),
                Answer("Let's build something that makes leaving unthinkable.", "build"))),
        ),
    )

    /** The question about his record: what his own résumé makes the AD ask. */
    private fun recordQuestion(lg: League, c: Coach): Question {
        val wl = "${c.w}-${c.l}"
        return when {
            c.titles > 0 -> Question("You've won ${c.titles} national title${if (c.titles == 1) "" else "s"}. Why leave, and what's left to prove?", listOf(
                Answer("Winning one somewhere new. That's the next challenge.", "win"),
                Answer("Building a program that lasts beyond me.", "build"),
                Answer("This is a place I can win for a long time.", "steady")))
            c.w < c.l -> Question("You're $wl as a head coach. Why should we trust you with this program?", listOf(
                Answer("Look at the rosters I took over. I made them better.", "build"),
                Answer("I've learned from every loss. I'm ready to win.", "win"),
                Answer("Give me players and I'll prove it.", "recruit")))
            c.finalFours > 0 -> Question("You've been to ${c.finalFours} Final Four${if (c.finalFours == 1) "" else "s"}. How do you get over the hump?", listOf(
                Answer("More talent at the top of the roster.", "recruit"),
                Answer("Experience. My best teams were old.", "build"),
                Answer("Keep knocking on the door. It opens.", "steady")))
            else -> Question("You're $wl. What's the one thing you'd do differently here?", listOf(
                Answer("Recruit harder. Talent wins.", "recruit"),
                Answer("Stay patient with young players.", "build"),
                Answer("Push to win now, every night.", "win")))
        }
    }

    /** The interview at [t]: two questions from its kind of program, one about the coach's record. */
    fun questionsFor(lg: League, t: Team): List<Question> {
        val pool = POOLS[kind(lg, t)] ?: POOLS.getValue("steady")
        val rnd = java.util.Random(t.id * 31L + lg.season)
        return pool.shuffled(rnd).take(2) + recordQuestion(lg, lg.userCoach)
    }

    /** The prompt for the athletic director's closing remark (AI on). */
    fun aiPrompt(lg: League, t: Team, o: JobOffer): String {
        val c = lg.userCoach
        return "You are the athletic director at ${t.school} (prestige ${t.prestige}/100, ${kindLabel(kind(lg, t)).lowercase()}), finishing a job interview with coach ${c.name} " +
            "(${c.w}-${c.l} career, ${c.titles} titles, ${c.finalFours} Final Fours, now at ${lg.user.school}). The interview scored ${o.score}/100" +
            (if (o.withdrawn) " and you've decided not to hire him" else " and you're offering the job") +
            ". Say one or two sentences to him, in your own voice, that mention his record. Plain text, no quotation marks."
    }

    /** What the athletic director at [t] values, by weight. */
    fun wants(lg: League, t: Team): Map<String, Double> {
        val rich = Finance.budget(lg, t) > Finance.donor(60) * 1.4
        return mapOf(
            "win" to if (t.prestige >= 75) 1.0 else if (t.prestige >= 55) 0.55 else 0.15,
            "steady" to if (t.prestige in 50..80) 0.9 else 0.5,
            "build" to if (t.prestige < 50 || t.w < t.l) 1.0 else 0.25,
            "recruit" to if (rich) 0.95 else 0.55,
        )
    }

    /** What the AD is looking for, in a sentence, so the user can read the room. */
    fun brief(lg: League, t: Team): String {
        val w = wants(lg, t)
        val top = w.maxByOrNull { it.value }?.key
        return when (top) {
            "win" -> "${t.school} expects titles. The AD wants someone who can win right away."
            "build" -> "${t.school} needs a rebuild. The AD wants patience and a plan."
            "recruit" -> "${t.school} has money to spend. The AD wants a recruiter."
            else -> "${t.school} wants stability: a coach who wins every year and stays."
        }
    }

    /** Scores the interview from [answers] (an answer index per question) and sets the terms. */
    fun interview(lg: League, o: JobOffer, answers: List<Int>): String {
        val t = lg.team(o.teamId)
        val w = wants(lg, t)
        val c = lg.userCoach
        var s = 0.0
        questionsFor(lg, t).forEachIndexed { i, q -> q.answers.getOrNull(answers.getOrElse(i) { -1 })?.let { s += (w[it.wants] ?: 0.5) * 22 } }
        s += (c.rating - 55) * 0.6 + c.titles * 4 + c.finalFours * 1.5 + Rng.gauss() * 5
        o.score = s.roundToInt().coerceIn(0, 100)
        val base = CoachContracts.salaryFor(lg, t, c)
        val years = CoachContracts.yearsFor(lg)
        return when {
            o.score >= 70 -> {
                o.salary = (base * 1.15 / 50).roundToInt() * 50; o.years = years + 1
                "They loved you. ${t.school} offers ${CoachContracts.money(o.salary)} a year for ${o.years} years."
            }
            o.score >= 42 -> {
                o.salary = base; o.years = years
                "A solid interview. ${t.school} offers ${CoachContracts.money(o.salary)} a year for ${o.years} years."
            }
            else -> {
                o.withdrawn = true
                "It didn't go well. ${t.school} has decided to go another direction."
            }
        }
    }

    /** Asks for more money: better terms, or an AD who won't move (and a bad interview can lose the offer). */
    fun push(lg: League, o: JobOffer): String {
        if (o.pushed || o.withdrawn || o.score < 0) return ""
        o.pushed = true
        val t = lg.team(o.teamId)
        val chance = ((o.score - 35) / 70.0).coerceIn(0.1, 0.85)
        return when {
            Rng.chance(chance) -> {
                o.salary = (o.salary * 1.1 / 50).roundToInt() * 50
                "${t.school} comes up: ${CoachContracts.money(o.salary)} a year for ${o.years} years."
            }
            o.score < 55 && Rng.chance(0.35) -> {
                o.withdrawn = true
                "${t.school}'s AD didn't like the ask and pulled the offer."
            }
            else -> "${t.school} won't go higher. The offer stands at ${CoachContracts.money(o.salary)} a year."
        }
    }

    /** Takes the job on the interviewed terms. */
    fun accept(lg: League, o: JobOffer) {
        if (o.withdrawn) return
        val salary = o.salary
        val years = o.years
        Coaching.acceptOffer(lg, o.teamId)
        if (salary > 0 && years > 0) CoachContracts.sign(lg, lg.user, years, salary)
    }
}
