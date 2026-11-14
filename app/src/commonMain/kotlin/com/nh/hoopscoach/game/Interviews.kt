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

    val QUESTIONS = listOf(
        Question("Where is this program in three years?", listOf(
            Answer("Cutting down the nets. We're built to win now.", "win"),
            Answer("In the tournament every March, year after year.", "steady"),
            Answer("Rebuilt from the ground up, with a foundation that lasts.", "build"),
        )),
        Question("How do you build a roster?", listOf(
            Answer("The portal and NIL. Proven players, right away.", "win"),
            Answer("High school recruits we develop for four years.", "build"),
            Answer("Whatever gets the best players here. We outwork everyone on the trail.", "recruit"),
        )),
        Question("What do your first hundred days look like?", listOf(
            Answer("Sitting down with every player on the roster.", "steady"),
            Answer("Meeting every donor and booster in the state.", "recruit"),
            Answer("Hiring the best staff money can buy and getting to work.", "win"),
        )),
    )

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
        QUESTIONS.forEachIndexed { i, q -> q.answers.getOrNull(answers.getOrElse(i) { -1 })?.let { s += (w[it.wants] ?: 0.5) * 22 } }
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
