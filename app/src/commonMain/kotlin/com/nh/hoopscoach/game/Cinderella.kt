package com.nh.hoopscoach.game

import kotlin.math.roundToInt

/**
 * Summer foreign tours (every four years: a trip abroad that brings a team together and adds practice
 * time) and the Cinderella tracker (the lowest seed still dancing in March, followed with its own story).
 */
object Tours {
    class Tour(val id: Int, val place: String, val cost: Long, val mood: Int, val xp: Int, val blurb: String)

    val ALL = listOf(
        Tour(0, "Italy", 250, 12, 180, "Rome and Milan, three games against pro clubs. The players won't stop talking about it."),
        Tour(1, "Spain", 280, 9, 240, "Barcelona and Madrid against the top Spanish clubs: tough games, more growth."),
        Tour(2, "Australia", 320, 8, 300, "Two weeks Down Under against the national team program: the most practice time."),
        Tour(3, "Japan", 300, 11, 220, "Tokyo and Osaka. Long flights, tight bonds, solid work."),
    )

    fun available(lg: League) = !lg.pro && !lg.playerMode && lg.phase == Phase.REGULAR && lg.user.gp == 0 &&
        lg.season - lg.tourSeason >= 4 && lg.tourDeclined != lg.season

    /** Goes on tour: everyone's mood rises and the extra practice adds growth. Null if it can't be afforded. */
    fun go(lg: League, id: Int): String? {
        val tour = ALL.getOrNull(id) ?: return null
        val t = lg.user
        Business.ensure(lg, t)
        if (t.cash < tour.cost) return null
        t.cash -= tour.cost
        val dev = Coaching.devOf(lg.coachOf(t))
        for (p in lg.rosterOf(t)) {
            p.happiness = (p.happiness + tour.mood).coerceAtMost(100)
            Ratings.addXp(p, (tour.xp * Ratings.devRate(p, dev)).roundToInt())
        }
        lg.tourSeason = lg.season
        val note = "${t.school} went to ${tour.place}. Every player came home happier (+${tour.mood}) and sharper from the extra practice."
        lg.news += News(lg.season, lg.day, "${t.school}'s summer tour of ${tour.place}", true, kind = "column", teamId = t.id, body = note, source = "Hoops Wire")
        Timeline.record(lg, "tour", "${t.school} tours ${tour.place}", note, t.id, coach = lg.userCoach.name, weight = 1)
        return note
    }
}

object Cinderella {
    class Run(val team: Team, val seed: Int, val wins: List<Game>, val out: Game?, val next: Game?)

    private fun ncaaGames(lg: League, id: Int) = lg.games.filter { it.type == GameType.NCAA && it.involves(id) }.sortedBy { it.day }

    /** The Cinderella already being followed this March, without choosing one (for the screens). */
    fun peek(lg: League): Run? = if (lg.cinderellaSeason == lg.season && lg.ncaa != null) current(lg) else null

    /** The Cinderella being followed this March, choosing one once a low seed has won a game. */
    fun current(lg: League): Run? {
        val b = lg.ncaa ?: return null
        if (lg.pro) return null
        if (lg.cinderellaSeason != lg.season) {
            val alive = b.seeds.keys.filter { id ->
                val gs = ncaaGames(lg, id).filter { it.played }
                gs.none { it.loser() == id } && gs.any { it.winner() == id } && (b.seeds[id] ?: 0) >= 10
            }
            val pick = alive.maxWithOrNull(compareBy<Int> { b.seeds[it] ?: 0 }.thenBy { ncaaGames(lg, it).count { g -> g.played } }) ?: return null
            lg.cinderellaId = pick
            lg.cinderellaSeason = lg.season
            val t = lg.team(pick)
            lg.news += News(lg.season, lg.day, "Cinderella watch: No. ${b.seeds[pick]} ${t.school} is still dancing", false, kind = "column", teamId = pick,
                body = "${t.school} came into the tournament a ${b.seeds[pick]} seed and has already won. Follow the run on the Today screen.", source = "Hoops Wire")
        }
        val t = lg.teams.getOrNull(lg.cinderellaId) ?: return null
        val gs = ncaaGames(lg, t.id)
        return Run(t, b.seeds[t.id] ?: 0, gs.filter { it.played && it.winner() == t.id }, gs.firstOrNull { it.played && it.loser() == t.id }, gs.firstOrNull { !it.played })
    }

    /** The story so far, as paragraphs. */
    fun story(lg: League, r: Run): List<String> {
        val t = r.team
        val b = lg.ncaa
        val c = lg.coachOf(t)
        val prior = t.history.filter { it.season < lg.season }
        val wonBefore = prior.count { Brand.roundOf(it.result) >= 2 }
        val heroes = lg.rosterOf(t).filter { it.tourney.gp > 0 }.sortedByDescending { it.tourney.pts }.take(3)
        fun vs(g: Game): String {
            val o = lg.team(g.opp(t.id))
            val s = b?.seeds?.get(o.id)?.let { "No. $it " } ?: ""
            return "$s${o.school} ${g.scoreFor(t.id)}-${g.scoreFor(o.id)}"
        }
        return buildList {
            add("${t.school} came into March ${t.w}-${t.l}, a ${r.seed} seed out of the ${lg.conf(t.confId).name}. " +
                (if (wonBefore == 0) "The program had never won an NCAA tournament game in the ${prior.size} seasons on record." else "It had won in the tournament $wonBefore time${if (wonBefore == 1) "" else "s"} before."))
            if (r.wins.isNotEmpty()) add("The run: " + r.wins.joinToString("; ") { "beat " + vs(it) } + ".")
            if (heroes.isNotEmpty()) add("Leading the way: " + heroes.joinToString(", ") { "${it.name} (${"%.1f".format(it.tourney.ppg)} points a game)" } +
                ". Coach ${c.name}" + (if (c.mentor.isNotBlank()) ", who learned under ${c.mentor}," else "") + " has them believing.")
            when {
                r.out != null -> add("The clock struck midnight against ${lg.team(r.out.opp(t.id)).school}, ${r.out.scoreFor(r.out.opp(t.id))}-${r.out.scoreFor(t.id)}. " +
                    "${r.wins.size} win${if (r.wins.size == 1) "" else "s"} as a ${r.seed} seed: a March they'll never forget.")
                b?.champion == t.id -> add("And they won it all. A ${r.seed} seed, national champions.")
                r.next != null -> add("Next: ${lg.team(r.next.opp(t.id)).school}${b?.seeds?.get(r.next.opp(t.id))?.let { ", the No. $it seed" } ?: ""}.")
            }
        }
    }
}
