package com.nh.hoopscoach.game

import kotlinx.serialization.Serializable

/** One game on the Pick'em card: who you took, and how it went (0 not yet, 1 right, -1 wrong). */
@Serializable
class PickGame(val gameId: Int, var pick: Int = -1, var result: Int = 0)

/** The weekly Pick'em: this week's three games, your streak and your season. */
@Serializable
class PickemState(
    var season: Int = 0,
    var week: Int = -1,
    val games: MutableList<PickGame> = mutableListOf(),
    var paid: Boolean = false,
    var streak: Int = 0,
    var best: Int = 0,
    var right: Int = 0,
    var total: Int = 0,
    /** What last week's card paid, for the Today card. */
    var lastNote: String = "",
)

/**
 * Weekly Pick'em: each week, the three biggest games that don't involve your team (ranked teams, rivalries, the
 * league's best). Pick a winner in each before they're played. Three right pays two card packs, two right one,
 * and every five right in a row adds a pack. The streak and the season's record stay on the card.
 */
object Pickem {
    fun week(lg: League) = lg.day / 7

    private fun mine(lg: League) = Cards.myTeam(lg)?.id ?: lg.userTeamId

    /** A new week's card, once the regular season or postseason has games left to pick. */
    fun ensure(lg: League) {
        val s = lg.pickem
        if (s.season == lg.season && s.week == week(lg)) return
        if (lg.phase != Phase.REGULAR && lg.phase != Phase.CONF_TOURNEY && lg.phase != Phase.NCAA && lg.phase != Phase.PLAYOFFS) return
        val me = mine(lg)
        val ahead = LeagueLists.games(lg).filter { !it.played && it.day >= lg.day && it.day < lg.day + 7 && !it.involves(me) }
        if (ahead.isEmpty()) return
        // The biggest: ranked and strong teams, rivalries and the postseason first.
        fun weight(g: Game): Double {
            val h = lg.team(g.home); val a = lg.team(g.away)
            val rank = if (lg.pro) 0.0 else listOf(h.ap, a.ap).sumOf { if (it in 1..25) (26 - it) * 3.0 else 0.0 }
            return h.elo + a.elo + rank + (if (lg.rivalry(g) != null) 60.0 else 0.0) +
                (if (g.type == GameType.NCAA || g.type == GameType.PLAYOFF || g.type == GameType.CONF_TOURNEY) 120.0 else 0.0) -
                kotlin.math.abs(h.elo - a.elo) * 0.3
        }
        val picks = ahead.sortedByDescending { weight(it) }.distinctBy { setOf(it.home, it.away) }.take(3)
        s.season = lg.season
        s.week = week(lg)
        s.games.clear()
        s.games += picks.map { PickGame(it.id) }
        s.paid = false
    }

    /** Your pick in [gameId]: [teamId] wins (until tip-off). */
    fun pick(lg: League, gameId: Int, teamId: Int) {
        val pg = lg.pickem.games.firstOrNull { it.gameId == gameId } ?: return
        val g = LeagueLists.games(lg).firstOrNull { it.id == gameId } ?: return
        if (g.played || !g.involves(teamId)) return
        pg.pick = teamId
    }

    /** A game on the card is final: right or wrong, the streak, and the week's payout once all three are in. */
    fun afterGame(lg: League, g: Game) {
        val s = lg.pickem
        val pg = s.games.firstOrNull { it.gameId == g.id } ?: return
        if (pg.result != 0 || pg.pick < 0) return
        val right = g.winner() == pg.pick
        pg.result = if (right) 1 else -1
        s.total++
        if (right) {
            s.right++
            s.streak++
            s.best = maxOf(s.best, s.streak)
            // Five in a row: a bonus pack.
            if (s.streak % 5 == 0) {
                lg.binder.packs = (lg.binder.packs + 1).coerceAtMost(99)
                Alerts.add(lg, "Pick'em: ${s.streak} in a row", "A bonus card pack. Keep it going.", "pickem")
            }
        } else s.streak = 0
        settleWeek(lg)
    }

    private fun settleWeek(lg: League) {
        val s = lg.pickem
        if (s.paid || s.games.isEmpty()) return
        val schedule = LeagueLists.games(lg)
        // Done when every game is final (an unpicked one just doesn't count).
        if (s.games.any { pg -> schedule.firstOrNull { it.id == pg.gameId }?.played != true }) return
        s.paid = true
        val right = s.games.count { it.result == 1 }
        val packs = when (right) { 3 -> 2; 2 -> 1; else -> 0 }
        lg.binder.packs = (lg.binder.packs + packs).coerceAtMost(99)
        s.lastNote = "Last week: $right of ${s.games.size} right" + if (packs > 0) ", ${if (packs == 1) "a card pack" else "two card packs"}." else "."
        if (packs > 0) Alerts.add(lg, "Pick'em: $right of ${s.games.size}", "That's ${if (packs == 1) "a card pack" else "two card packs"}. A new card's up.", "pickem")
    }
}
