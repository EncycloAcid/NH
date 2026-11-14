package com.nh.hoopscoach.game

/**
 * The night's Top 10: every game keeps its best plays (dunks and posters, clutch threes, blocks at the
 * rim, buzzer-beaters, see [GameEngine]), and once the day is done the ten biggest are ranked, star
 * power and the moment included. The user's own plays (his player's, or his team's as a coach) also
 * go on his season reel, which becomes the mixtape.
 */
object Highlights {
    /** After a game: its best three go to tonight's pool; the user's best go on his reel. */
    fun afterGame(lg: League, plays: List<Highlight>) {
        if (plays.isEmpty()) return
        lg.dayPlays += plays.sortedByDescending { it.score }.take(3)
        val mine = plays.filter { mine(lg, it) }
        if (mine.isNotEmpty()) {
            lg.myPlays.removeAll { it.season != lg.season }
            lg.myPlays += mine
            if (lg.myPlays.size > 40) {
                val keep = lg.myPlays.sortedByDescending { it.score }.take(40).toSet()
                lg.myPlays.retainAll(keep)
            }
        }
    }

    /** His play: his player's in player mode, his team's as a coach. */
    fun mine(lg: League, h: Highlight): Boolean = if (lg.playerMode) h.pid == lg.heroId else h.teamId == lg.userTeamId

    /** The day is over: rank tonight's plays (a little nudge for his own), and start fresh. */
    fun rank(lg: League, day: Int) {
        if (lg.dayPlays.isEmpty()) return
        val luck = kotlin.random.Random(lg.season * 1000L + day)
        // A mix of plays, not ten of a kind: at most three of any kind (game-winners always make it).
        val ranked = lg.dayPlays.map { it to it.score + luck.nextDouble() * 1.5 + (if (mine(lg, it)) 1.0 else 0.0) }
            .sortedByDescending { it.second }.map { it.first }.distinctBy { it.pid to it.kind }
        val count = HashMap<String, Int>()
        val pick = mutableListOf<Highlight>()
        for (h in ranked) {
            val fam = when (h.kind) { "poster", "dunk" -> h.kind; "three", "dagger", "four" -> "three"; "winner", "tie" -> "winner"; else -> h.kind }
            if (fam != "winner" && (count[fam] ?: 0) >= 3) continue
            count[fam] = (count[fam] ?: 0) + 1
            pick += h
            if (pick.size == 10) break
        }
        lg.topPlays = pick
        lg.topDay = day
        lg.topSeason = lg.season
        lg.dayPlays.clear()
        // His play at No. 1 is news.
        lg.topPlays.firstOrNull()?.takeIf { mine(lg, it) }?.let {
            lg.news += News(lg.season, day, "No. 1 on tonight's Top 10: ${it.text}", true, it.gameId, "game", "", "Hoops Wire", it.teamId, it.pid)
        }
    }

    /** His plays in last night's Top 10, as ranks. */
    fun myRanks(lg: League): List<Int> = lg.topPlays.mapIndexedNotNull { i, h -> if (mine(lg, h)) i + 1 else null }

    /** The season's best plays of his, for the mixtape. */
    fun mixtape(lg: League): List<Highlight> = lg.myPlays.filter { it.season == lg.season }.sortedByDescending { it.score }.distinctBy { it.text }.take(15)

    /** "Dunks 6 · Threes 3 · Blocks 2 · Game-winners 1" for the mixtape's header. */
    fun tally(plays: List<Highlight>): List<Pair<String, Int>> = listOf(
        "Dunks" to plays.count { it.kind == "dunk" || it.kind == "poster" },
        "Threes" to plays.count { it.kind in setOf("three", "dagger", "four") },
        "Blocks" to plays.count { it.kind == "block" },
        "Clutch" to plays.count { it.kind in setOf("winner", "tie", "goahead", "moment") },
    )
}
