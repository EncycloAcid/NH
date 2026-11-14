package com.nh.hoopscoach.game

import kotlinx.serialization.Serializable

/**
 * Home-court traditions: what makes a building hard to play in, and what it does to visitors. Some come
 * with the building (altitude, an old barn), others are earned and can fade (a student section, banners,
 * a fortress of a home record).
 */
object HomeCourt {
    class Tradition(val id: String, val name: String, val effect: String, val how: String)

    val ALL = listOf(
        Tradition("altitude", "Altitude", "Visitors fade late: they shoot worse in the second half.", "Comes with a mountain campus."),
        Tradition("students", "Deafening student section", "Visitors turn the ball over more.", "Earned with sellouts and fans who are wild about the team; fades if they sour."),
        Tradition("barn", "The old barn", "Tight and loud: visitors miss more free throws.", "Comes with an old building."),
        Tradition("banners", "Banners in the rafters", "Your team is steadier in the final two minutes.", "Earned with national titles or a run of Final Fours."),
        Tradition("fortress", "Fortress", "Visitors shoot a little worse all game.", "Earned with a home season of one loss or fewer; lost when the home record slips under .600."),
    )

    fun def(id: String) = ALL.firstOrNull { it.id == id }

    private val MOUNTAIN = setOf("CO", "UT", "WY", "NM", "MT", "ID")

    /** Gives a building the traditions it starts with, once. */
    fun ensure(lg: League, t: Team) {
        if (t.traditionsSet) return
        t.traditionsSet = true
        val h = (t.abbr.hashCode() and 0x7fffffff)
        val state = runCatching { Geo.stateOf(t) }.getOrNull()
        if (state in MOUNTAIN || (lg.pro && t.slot.uppercase() in setOf("DEN", "UTA", "UTH"))) t.traditions += "altitude"
        if (!lg.pro && h % 9 == 0) t.traditions += "barn"
        if (t.titles >= 3) t.traditions += "banners"
        Fans.ensure(t)
        if (t.prestige >= 75 && (t.fans >= 80 || h % 4 == 0)) t.traditions += "students"
    }

    fun has(t: Team, id: String) = id in t.traditions

    // ---------- in the game (visitors are side 1, and only in a real home game) ----------

    /** Extra turnover odds for the visitors. */
    fun visitorTurnovers(home: Team?): Double = if (home != null && has(home, "students")) 0.012 else 0.0

    /** Free-throw odds lost by the visitors. */
    fun visitorFreeThrows(home: Team?): Double = if (home != null && has(home, "barn")) 0.04 else 0.0

    /** Shooting lost by the visitors ([secondHalf] for altitude). */
    fun visitorShooting(home: Team?, secondHalf: Boolean): Double {
        if (home == null) return 0.0
        var v = 0.0
        if (has(home, "fortress")) v += 0.006
        if (secondHalf && has(home, "altitude")) v += 0.012
        return v
    }

    /** The home team's lift in the last two minutes. */
    fun homeClose(home: Team?): Double = if (home != null && has(home, "banners")) 0.02 else 0.0

    // ---------- earned and lost at season's end ----------

    fun seasonEnd(lg: League) {
        for (t in lg.teams) {
            ensure(lg, t)
            val home = lg.games.filter { it.played && it.home == t.id && !it.neutral && (it.type == GameType.CONF || it.type == GameType.NONCONF) }
            val w = home.count { it.hs > it.aws }
            val l = home.size - w
            Fans.ensure(t)
            fun gain(id: String) {
                if (has(t, id)) return
                t.traditions += id
                val d = def(id) ?: return
                Timeline.record(lg, "tradition", "${t.school}: ${d.name.lowercase()}", "${t.school}'s building has a new tradition. ${d.effect}", t.id, weight = 2)
                if (t.id == lg.userTeamId) {
                    lg.news += News(lg.season, lg.day, "A new home-court tradition at ${t.school}: ${d.name.lowercase()}", true, kind = "column", teamId = t.id,
                        body = "${d.effect} ${d.how}", source = "Hoops Wire")
                    Alerts.add(lg, "Home-court tradition: ${d.name}", d.effect, "")
                }
            }
            fun lose(id: String) {
                if (!has(t, id)) return
                t.traditions -= id
                val d = def(id) ?: return
                if (t.id == lg.userTeamId) lg.news += News(lg.season, lg.day, "${t.school}'s ${d.name.lowercase()} is fading", true, kind = "column", teamId = t.id,
                    body = "The building isn't what it was. ${d.how}", source = "Hoops Wire")
            }
            if (home.size >= 10 && l <= 1) gain("fortress") else if (home.size >= 8 && w < home.size * 0.6) lose("fortress")
            if (t.fans >= 88 && t.homeGames > 0 && t.sellouts >= t.homeGames * 0.8) gain("students") else if (t.fans < 55) lose("students")
            if (t.titles >= 2 || t.finalFours >= 4) gain("banners")
        }
    }
}

/** A season the user held a rivalry trophy. */
@Serializable
class TrophyYear(val name: String, val season: Int, val oppId: Int)

/**
 * Rivalry trophies: every named rivalry has a traveling trophy that goes to the winner of each meeting.
 * Every season the user ends holding one goes on his trophy-room shelf.
 */
object RivalryTrophies {
    fun named(r: Rivalry) = r.name.isNotBlank() && !r.name.equals("Emerging rivalry", ignoreCase = true)

    fun name(r: Rivalry): String = r.name.let { n ->
        if (listOf("trophy", "cup", "bell", "bucket", "jug", "game", "rights").any { it in n.lowercase() }) n else "$n Trophy"
    }

    /** After a rivalry game: the winner holds the trophy. */
    fun afterGame(lg: League, r: Rivalry, winner: Team, loser: Team) {
        if (!named(r)) return
        val before = r.holder
        r.holder = winner.id
        if (before == winner.id) return
        val mine = winner.id == lg.userTeamId || loser.id == lg.userTeamId
        val head = if (before < 0) "${winner.school} takes the first ${name(r)}" else "${winner.school} takes back the ${name(r)}"
        if (mine || winner.ap > 0 || loser.ap > 0) lg.news += News(lg.season, lg.day, head, mine, kind = "rivalry", teamId = winner.id,
            body = "${winner.school} beat ${loser.school} and the ${name(r)} changes hands. The series stands at ${r.winsFor(winner.id)}-${r.winsFor(loser.id)}.", source = "Hoops Wire")
        if (winner.id == lg.userTeamId && before >= 0) Alerts.add(lg, "The ${name(r)} is yours", "You took it back from ${loser.school}. It goes in the trophy room at season's end if you keep it.", "")
    }

    /** At season's end: each trophy the user holds goes on his shelf. */
    fun seasonEnd(lg: League) {
        val u = lg.userTeamId
        for (r in lg.rivalsOf(u)) {
            if (!named(r) || r.holder != u) continue
            if (lg.trophyShelf.none { it.name == name(r) && it.season == lg.season }) lg.trophyShelf += TrophyYear(name(r), lg.season, r.other(u))
        }
    }
}

/**
 * Homecoming games: a player back in his home state with a school from somewhere else. The family's in
 * the stands and the local paper is there; he plays a little better, keeps the memory, and recruits
 * from that state notice.
 */
object Homecoming {
    /** The players in [game] who are home, by id. */
    fun players(lg: League, game: Game): List<Player> {
        if (game.neutral || lg.pro) return emptyList()
        val arena = Geo.stateOf(lg.team(game.home)) ?: return emptyList()
        return listOf(game.home, game.away).flatMap { tid ->
            val t = lg.team(tid)
            if (Geo.stateOf(t) == arena) emptyList() else lg.rosterOf(t).filter { Geo.stateOf(it) == arena }
        }
    }

    /** After the game: the memory, the story, and recruits from his state warming to his school. */
    fun afterGame(lg: League, game: Game, pts: (Int) -> Int) {
        for (p in players(lg, game)) {
            val t = lg.teams.getOrNull(p.teamId) ?: continue
            val town = p.hometown.substringBefore(",")
            val won = game.winner() == t.id
            val scored = pts(p.id)
            Memories.add(lg, p, "homecoming", "Played in front of family near $town" + if (won && scored >= 15) ", and won" else "", t.id, if (won) 1 else 0)
            if (t.id != lg.userTeamId) continue
            val state = Geo.stateOf(p) ?: continue
            for (r in lg.recruits) if (r.committed < 0 && Geo.stateOf(r.p) == state) r.interest[t.id] = ((r.interest[t.id] ?: 4) + if (scored >= 15) 3 else 1).coerceAtMost(100)
            if (scored >= 10 || won) lg.news += News(lg.season, lg.day,
                "${p.name} comes home: $scored points ${if (won) "in a win" else "in front of family"} near $town", true, game.id, "column",
                body = "A section of family and friends made the trip. ${p.name} grew up in ${p.hometown}, and recruits back home were watching.",
                source = "Hoops Wire", teamId = t.id, pid = p.id)
        }
    }
}
