package com.nh.hoopscoach.game

import kotlin.math.roundToInt

/**
 * Calling it a career. A pro who's 30 or older can announce that a season will be his last. Then it's a
 * farewell tour: every road arena honors him the first time he visits (a gift, a video, a standing
 * ovation, a word from their star), his last home game is a night of its own, and when the season ends he
 * retires, with the full farewell. Until he does, the shows and the fans talk about whether he's leaving
 * too early.
 */
object FarewellTour {
    private val GIFTS = listOf(
        "a custom rocking chair in team colors", "a framed jersey signed by the whole roster", "a donation to his foundation",
        "a painting of his best night in their building", "a custom bike", "a pair of cowboy boots with his number stitched in",
        "a surfboard", "a golf cart", "a guitar signed by a local band", "the game ball from his first visit", "a piece of the old court",
        "a lifetime pass to the arena", "a scrapbook of every game he played there", "a vintage record player",
    )

    fun canAnnounce(lg: League): Boolean {
        val r = lg.road ?: return false
        return ProCareer.canRetire(lg) && r.farewellSeason == 0 && !r.ended
    }

    /** He tells the world: this season ([lg.season], in season) or next season (in the offseason) is the last. */
    fun announce(lg: League) {
        val r = lg.road ?: return
        val h = lg.hero ?: return
        if (!canAnnounce(lg)) return
        val inSeason = lg.phase == Phase.REGULAR
        r.farewellSeason = if (inSeason) lg.season else lg.season + 1
        r.tourStops.clear()
        r.followers = (r.followers * 1.06).roundToInt()
        val t = lg.teams.getOrNull(h.teamId)
        val when_ = if (inSeason) "this season" else "the ${r.farewellSeason - 1}-${(r.farewellSeason % 100).toString().padStart(2, '0')} season"
        lg.news += News(lg.season, lg.day, "${h.name} announces $when_ will be his last", true, -1, "farewell",
            "After ${h.proYears} pro ${if (h.proYears == 1) "season" else "seasons"}, ${h.name} (${h.age}) says he'll retire when $when_ ends. Every arena on the schedule gets one more look.",
            "Hoops Wire", t?.id ?: -1, h.id)
        Media.post(lg, h.name, h.name.replace(" ", "").replace(".", "").lowercase(), "self",
            "It's time. One more run, one more time in every building. Thank you for all of it.", Media.likes(lg, 0.08))
        Alerts.add(lg, "The farewell tour", "You've announced $when_ is your last. Every road arena will honor you on your first visit.", "farewell")
    }

    /** After a day is played: the tour's stops, the last home game, the last ride. */
    fun afterDay(lg: League, day: Int) {
        val r = lg.road ?: return
        val h = lg.hero ?: return
        if (r.farewellSeason != lg.season || h.teamId < 0) return
        val t = lg.team(h.teamId)
        for (g in Season.gamesOn(lg, day).filter { it.played && it.involves(t.id) }) {
            val played = h.log.any { it.gameId == g.id }
            val opp = lg.team(g.opp(t.id))
            if (g.home != t.id && opp.id !in r.tourStops && (g.type == GameType.CONF || g.type == GameType.NONCONF)) {
                r.tourStops += opp.id
                val gift = GIFTS[(opp.id * 7 + lg.season) % GIFTS.size]
                val star = lg.rosterOf(opp).maxByOrNull { it.ovr }
                val line = h.log.lastOrNull { it.gameId == g.id }?.let { " He had ${it.pts} that night." } ?: ""
                r.followers = (r.followers * 1.005).roundToInt() + 2_000
                lg.news += News(lg.season, day, "Farewell tour: the ${opp.name} honor ${h.name}", true, -1, "farewell",
                    "A video tribute, a standing ovation in ${opp.school}, and $gift." + (if (played) line else " He sat that one, but stood for the ovation.") +
                        (star?.let { " ${it.name}: \"One of the toughest guys I ever had to guard. Respect.\"" } ?: ""),
                    "Hoops Wire", opp.id, h.id)
            }
            // The last regular-season game at home.
            val lastHome = lg.games.filter { it.involves(t.id) && it.home == t.id && (it.type == GameType.CONF || it.type == GameType.NONCONF) }.maxByOrNull { it.day }
            if (lastHome?.id == g.id) {
                val pts = h.log.lastOrNull { it.gameId == g.id }?.pts
                lg.news += News(lg.season, day, "${h.name}'s final home game", true, -1, "farewell",
                    "A sellout, a tribute video from every team he played for, and a crowd that wouldn't sit down." + (pts?.let { " He scored $it." } ?: "") +
                        " \"I'll never forget this building,\" he said.", "Hoops Wire", t.id, h.id)
                Alerts.add(lg, "Your last home game", "The building stood for you all night.", "farewell")
            }
        }
    }
}
