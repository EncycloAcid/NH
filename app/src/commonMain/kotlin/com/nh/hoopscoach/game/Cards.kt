package com.nh.hoopscoach.game

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/** A collected card: the player as he was when it was pulled (players move on; the card doesn't). */
@Serializable
class Card(
    val pid: Int, val name: String, val team: String, val pos: String, val rating: Int, val season: Int,
    /** 0 Bronze, 1 Silver, 2 Gold, 3 Diamond. */
    val rarity: Int,
    val face: String, val primary: Long, val secondary: Long, val number: Int, val abbr: String = "",
)

/** A page of the binder: the players it takes, and whether it's complete. */
@Serializable
class CardSet(val id: String, val title: String, val season: Int, val pids: List<Int>, val blurb: String, var done: Boolean = false)

/** The collection: cards, sets, packs waiting to be opened, and what the last pack held. */
@Serializable
class Binder(
    val cards: MutableList<Card> = mutableListOf(),
    val sets: MutableList<CardSet> = mutableListOf(),
    var packs: Int = 0,
    /** Wins toward the next pack. */
    var points: Int = 0,
    /** Duplicates toward a trade-in pack. */
    var dupes: Int = 0,
    var opened: Int = 0,
    val lastPack: MutableList<Card> = mutableListOf(),
    var setsSeason: Int = 0,
    var started: Boolean = false,
)

/**
 * Card collection. Packs come from playing: one for every three wins, one more for an upset, a rivalry win or a
 * postseason win, one a season (three for a title), and three to start. A pack is five cards of the league's
 * players, Bronze to Diamond by rating (the last at least Silver), about half of them toward the sets you haven't
 * finished. Each season has three sets: your team, the best player on every team in your conference, and the
 * league's twelve best. A finished set pays two packs and a bigger following (a player career) or more fan
 * interest (a coach). Five duplicates trade in for a pack.
 */
object Cards {
    val RARITY = listOf("Bronze", "Silver", "Gold", "Diamond")
    const val PACK = 5

    /** The team that's yours: your player's in a player career, your program's otherwise. */
    fun myTeam(lg: League): Team? = lg.teams.getOrNull(if (lg.playerMode) lg.hero?.teamId ?: -1 else lg.userTeamId)

    /** The rating a card shows: the pros on the scale the screens use. */
    fun shown(lg: League, p: Player) = if (lg.pro) Ratings.shownPro(p.ovr) else p.ovr

    fun rarity(v: Int) = when { v >= 90 -> 3; v >= 80 -> 2; v >= 70 -> 1; else -> 0 }

    fun cardOf(lg: League, p: Player): Card {
        val t = lg.teams.getOrNull(p.teamId)
        val v = shown(lg, p)
        return Card(p.id, p.name, t?.let { if (lg.pro) it.name else it.school } ?: "Free agent", p.posLabel, v, lg.season, rarity(v),
            runCatching { Face.of(p).encode() }.getOrDefault(""), t?.primary ?: 0xFF3A3F47L, t?.secondary ?: 0xFFFFFFFFL, p.number, t?.abbr ?: "")
    }

    private fun pool(lg: League): List<Player> = LeagueLists.teams(lg).flatMap { t -> t.roster.mapNotNull { lg.playerOrNull(it) } }

    fun owns(lg: League, pid: Int) = lg.binder.cards.any { it.pid == pid }

    /** The season's three sets (made the first time they're needed), and three packs for a brand-new binder. */
    fun ensure(lg: League) {
        val b = lg.binder
        if (!b.started) { b.started = true; b.packs += 3 }
        if (b.setsSeason == lg.season) return
        val u = myTeam(lg) ?: return
        b.setsSeason = lg.season
        val name = if (lg.pro) u.name else u.school
        val roster = lg.rosterOf(u).sortedByDescending { it.ovr }.take(if (lg.pro) 15 else 13)
        if (roster.isNotEmpty()) b.sets += CardSet("team-${lg.season}-${u.id}", "$name ${lg.season}", lg.season, roster.map { it.id }, "Your team, every player on the roster")
        val conf = lg.conf(u.confId)
        val stars = LeagueLists.teams(lg).filter { it.confId == u.confId }.mapNotNull { t -> lg.rosterOf(t).maxByOrNull { it.ovr } }
        if (stars.size >= 4) b.sets += CardSet("conf-${lg.season}-${u.confId}", "${conf.name} stars", lg.season, stars.map { it.id }, "The best player on every team in the ${conf.name}")
        val elite = pool(lg).sortedByDescending { it.ovr }.take(12)
        if (elite.isNotEmpty()) b.sets += CardSet("elite-${lg.season}-${if (lg.pro) "nba" else "ncaa"}", "The ${lg.season} elite", lg.season, elite.map { it.id }, "The 12 best players in ${if (lg.pro) "the league" else "college basketball"}")
        // Old unfinished sets go; finished ones stay as trophies.
        b.sets.removeAll { !it.done && it.season < lg.season - 1 }
        checkSets(lg)
    }

    /** Opens a pack: five cards, and the rewards for any set they finish. */
    fun open(lg: League): List<Card> {
        val b = lg.binder
        ensure(lg)
        if (b.packs <= 0) return emptyList()
        b.packs--
        b.opened++
        val all = pool(lg)
        val missing = b.sets.filter { !it.done && it.season >= lg.season - 1 }.flatMap { it.pids }.filter { !owns(lg, it) }.distinct()
        val taken = mutableSetOf<Int>()
        val pulls = (0 until PACK).mapNotNull { slot ->
            val want = roll(slot == PACK - 1)
            // Toward a set, at the rarity this slot rolled or below: Diamonds stay rare, and the elite set is the hard one.
            val chase = missing.filter { it !in taken }.mapNotNull { lg.playerOrNull(it) }.filter { rarity(shown(lg, it)) <= want }
            val p = if (chase.isNotEmpty() && Rng.chance(if (want >= 2) 0.7 else 0.45)) Rng.pick(chase)
                else byRarity(lg, all.filter { it.id !in taken }, want)
            p?.also { taken += it.id }?.let { cardOf(lg, it) }
        }
        for (c in pulls) {
            if (owns(lg, c.pid)) b.dupes++ else b.cards += c
        }
        // Five duplicates trade in for a pack.
        while (b.dupes >= 5) { b.dupes -= 5; b.packs++ }
        b.lastPack.clear()
        b.lastPack += pulls
        checkSets(lg)
        return pulls
    }

    /** A card's rarity: mostly Bronze and Silver; the last card in a pack is at least Silver. */
    private fun roll(last: Boolean): Int {
        val d = Rng.d()
        return if (last) when { d < 0.08 -> 3; d < 0.38 -> 2; else -> 1 }
            else when { d < 0.04 -> 3; d < 0.19 -> 2; d < 0.52 -> 1; else -> 0 }
    }

    private fun byRarity(lg: League, from: List<Player>, want: Int): Player? {
        for (r in want downTo 0) {
            val c = from.filter { rarity(shown(lg, it)) == r }
            if (c.isNotEmpty()) return Rng.pick(c)
        }
        return if (from.isNotEmpty()) Rng.pick(from) else null
    }

    /** Finished sets: two packs, and a bigger following (your player) or more fan interest (your program). */
    private fun checkSets(lg: League) {
        val b = lg.binder
        for (s in b.sets) {
            if (s.done || s.pids.isEmpty() || !s.pids.all { owns(lg, it) }) continue
            s.done = true
            b.packs += 2
            val bonus = if (lg.playerMode) {
                lg.road?.let { r -> r.followers = (r.followers * 1.05).roundToInt() + 2_000 }
                "two packs and a bigger following"
            } else {
                myTeam(lg)?.let { it.fans = (it.fans + 2.0).coerceAtMost(100.0) }
                "two packs and more fan interest"
            }
            Alerts.add(lg, "Set complete: ${s.title}", "All ${s.pids.size} cards. Reward: $bonus.", "cards")
        }
    }

    /** A win: a point toward the next pack, and a pack of its own for an upset, a rivalry or a postseason win. */
    fun afterGame(lg: League, g: Game) {
        val u = myTeam(lg) ?: return
        if (!g.involves(u.id) || g.winner() != u.id) return
        val b = lg.binder
        val opp = lg.team(g.opp(u.id))
        val upset = opp.elo > u.elo + 75 || (!lg.pro && opp.ap in 1..25 && (u.ap == 0 || u.ap > opp.ap + 5))
        val big = upset || lg.rivalry(g) != null || g.type == GameType.NCAA || g.type == GameType.PLAYOFF || g.type == GameType.CONF_TOURNEY
        if (big) b.packs++
        b.points++
        while (b.points >= 3) { b.points -= 3; b.packs++ }
        b.packs = b.packs.coerceAtMost(99)
    }

    /** A season done: a pack, three for a title. */
    fun seasonEnd(lg: League) {
        val u = myTeam(lg) ?: return
        val result = u.history.lastOrNull()?.result.orEmpty()
        lg.binder.packs = (lg.binder.packs + if (result.contains("Champion")) 3 else 1).coerceAtMost(99)
    }

    /** "12 of 13" for a set. */
    fun progress(lg: League, s: CardSet) = s.pids.count { owns(lg, it) }
}
