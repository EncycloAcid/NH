package com.nh.hoopscoach.game

import kotlin.math.roundToInt

/**
 * The all-time list: the greats of the game, today's stars (their real careers so far plus what they
 * do from here), anyone who builds a career in this league, and the user's player. Every career is
 * scored the same way: MVPs, Finals MVPs, rings, All-NBA teams, All-Star games, Defensive Player
 * of the Year and career points.
 */
object AllTime {
    const val MVP = 8.0
    const val FINALS_MVP = 6.0
    const val RING = 5.0
    const val ALL_LEAGUE_1 = 3.0
    const val ALL_LEAGUE_2 = 1.5
    const val ALL_LEAGUE_3 = 1.0
    const val ALL_STAR = 1.0
    const val DPOY = 2.0
    const val ROY = 1.0
    /** Per thousand career points, on an 83-game scale. */
    const val PER_1000 = 0.6

    /** A finished season counts as a full 83 games (older saves played 58). */
    fun lineScale(l: SeasonLine) = if (l.w + l.l in 40..82) ProLeague.GAMES.toDouble() / (l.w + l.l) else 1.0

    /** The season in progress, the same way. */
    fun currentScale(lg: League) = ProLeague.GAMES.toDouble() / ProLeague.seasonGames(lg)

    /** You make the list at this score. */
    const val FLOOR = 20.0

    /** Retired greats, scored with the same formula from their careers (estimates). */
    val LEGENDS = listOf(
        "Michael Jordan" to 171.4, "Kareem Abdul-Jabbar" to 169.5, "Bill Russell" to 136.7, "Tim Duncan" to 126.4,
        "Kobe Bryant" to 121.2, "Magic Johnson" to 118.1, "Shaquille O'Neal" to 109.2, "Wilt Chamberlain" to 105.4,
        "Larry Bird" to 104.6, "John Havlicek" to 97.0, "Bob Cousy" to 94.2, "Karl Malone" to 89.2,
        "Hakeem Olajuwon" to 87.7, "Moses Malone" to 81.4, "Bob Pettit" to 76.0, "Dirk Nowitzki" to 74.4,
        "Jerry West" to 73.1, "Oscar Robertson" to 71.0, "Julius Erving" to 70.0, "Sam Jones" to 69.2,
        "Kevin Garnett" to 64.1, "Scottie Pippen" to 62.4, "David Robinson" to 61.5, "Dwyane Wade" to 61.4,
        "Charles Barkley" to 56.8, "Elgin Baylor" to 55.0, "George Mikan" to 54.0, "Willis Reed" to 53.3,
        "Isiah Thomas" to 51.3, "Tony Parker" to 49.2, "Dwight Howard" to 48.7, "Steve Nash" to 48.4,
        "Allen Iverson" to 48.1, "Russell Westbrook" to 48.1, "Chris Paul" to 46.7, "Rick Barry" to 45.0,
        "Gary Payton" to 44.6, "Jason Kidd" to 42.0, "Paul Pierce" to 41.3, "Walt Frazier" to 41.3,
        "John Stockton" to 39.8, "Patrick Ewing" to 37.9, "Ray Allen" to 37.2, "Clyde Drexler" to 36.3,
        "Dominique Wilkins" to 36.0, "Kevin McHale" to 35.4, "Carmelo Anthony" to 34.0, "Pau Gasol" to 33.5,
        "Manu Ginobili" to 32.4, "Tracy McGrady" to 30.5, "Vince Carter" to 26.4,
    )

    /** Real players on today's rosters: their careers before this league starts (estimates). */
    val ACTIVE = mapOf(
        "LeBron James" to 172.0, "Stephen Curry" to 90.6, "Kevin Durant" to 89.4, "Giannis Antetokounmpo" to 70.8,
        "Nikola Jokic" to 70.0, "James Harden" to 56.3, "Kawhi Leonard" to 54.5, "Shai Gilgeous-Alexander" to 39.8,
        "Anthony Davis" to 37.2, "Klay Thompson" to 37.0, "Draymond Green" to 34.0, "Damian Lillard" to 32.6,
        "Jayson Tatum" to 30.8, "Kyrie Irving" to 30.7, "Joel Embiid" to 28.8, "Luka Doncic" to 27.8,
        "Jaylen Brown" to 24.3, "DeMar DeRozan" to 23.0, "Jimmy Butler III" to 22.0, "Paul George" to 22.0,
        "Jrue Holiday" to 21.6, "Al Horford" to 20.0, "Pascal Siakam" to 19.0, "Donovan Mitchell" to 18.0,
        "Devin Booker" to 16.0, "Karl-Anthony Towns" to 16.0, "Anthony Edwards" to 12.0, "Trae Young" to 12.0,
        "Bam Adebayo" to 11.0, "Jamal Murray" to 11.0, "Jalen Brunson" to 10.0, "Mike Conley" to 10.0,
        "Kristaps Porzingis" to 10.0, "Ja Morant" to 8.6, "Victor Wembanyama" to 6.0, "Cade Cunningham" to 6.0,
        "Zion Williamson" to 5.0,
    )

    class Entry(val name: String, val score: Double, val status: String, val pid: Int = -1)

    private fun count(p: Player, award: String) = p.awards.count { it.substringAfter(" ") == award }

    /** What [p] has done in this league, by the same formula. */
    fun inGame(lg: League, p: Player): Double {
        var pts = p.career.filter { it.cls == PRO_YEAR }.sumOf { it.s.pts * lineScale(it) }
        if (p.teamId >= 0 && p.career.none { it.season == lg.season && it.cls == PRO_YEAR }) pts += p.stats.pts * currentScale(lg)
        return count(p, "MVP") * MVP + count(p, "Finals MVP") * FINALS_MVP + count(p, "Champion") * RING +
            count(p, "All-NBA 1st") * ALL_LEAGUE_1 + count(p, "All-NBA 2nd") * ALL_LEAGUE_2 + count(p, "All-NBA 3rd") * ALL_LEAGUE_3 +
            count(p, "All-Star") * ALL_STAR + count(p, "Defensive Player of the Year") * DPOY + count(p, "Rookie of the Year") * ROY +
            count(p, "Olympics Gold") * 2.0 + count(p, "World Cup Gold") * 1.0 +
            p.awards.count { it.substringAfter(" ").startsWith("Record: ") } * 3.0 +
            pts / 1000.0 * PER_1000
    }

    /** A real player's career before this league, plus everything since. */
    fun total(lg: League, p: Player): Double = (if (p.photo.isNotEmpty()) ACTIVE[p.name] ?: 0.0 else 0.0) + inGame(lg, p)

    /** Everyone on the list, best first (the user's player included once he's earned a spot). */
    fun list(lg: League): List<Entry> {
        val out = mutableListOf<Entry>()
        val names = HashSet<String>()
        // In a past era some legends are still playing here: they're on the list by what they do in this league.
        val here = (lg.players.map { it.name } + lg.alumni.map { it.name }).toSet()
        for ((n, s) in LEGENDS) { if (n in here) continue; out += Entry(n, s, "Retired"); names += n }
        for (p in lg.players) {
            if (p.id == lg.heroId) continue
            val s = total(lg, p)
            if (s >= FLOOR && names.add(p.name)) out += Entry(p.name, s, "Active", p.id)
        }
        for ((n, s) in lg.proState?.frozen ?: emptyMap()) if (s >= FLOOR && names.add(n)) out += Entry(n, s, "Retired")
        val h = lg.hero
        if (h != null) out += Entry(h.name, inGame(lg, h), "You", h.id)
        return out.sortedByDescending { it.score }
    }

    fun heroScore(lg: League): Double = lg.hero?.let { inGame(lg, it) } ?: 0.0

    /** His all-time rank, or 0 while he's below the floor. */
    fun rank(lg: League): Int {
        val s = heroScore(lg)
        if (s < FLOOR) return 0
        return list(lg).indexOfFirst { it.status == "You" } + 1
    }

    /** The next name above him, and how far away. */
    fun next(lg: League): Entry? {
        val l = list(lg)
        val i = l.indexOfFirst { it.status == "You" }
        return if (i > 0) l[i - 1] else null
    }

    /** A retiring player keeps his place on the list. */
    fun freeze(lg: League, p: Player) {
        val s = total(lg, p)
        if (s >= FLOOR) lg.proState?.frozen?.put(p.name, s)
    }

    /** After games and awards: did he pass anyone? Announces the biggest name. */
    fun check(lg: League) {
        val r = lg.road ?: return
        if (!lg.pro || r.ended) return
        val h = lg.hero ?: return
        val mine = heroScore(lg)
        if (mine < FLOOR) return
        val l = list(lg)
        val below = l.filter { it.status != "You" && it.score < mine }.map { it.name }
        val fresh = below.filter { it !in r.allTimePassed }
        r.allTimePassed.clear()
        r.allTimePassed += below
        val rank = l.indexOfFirst { it.status == "You" } + 1
        val first = r.allTimeRank == 0
        r.allTimeRank = rank
        val news = fresh.filter { it !in r.allTimeNamed }
        if (news.isEmpty() && !first) return
        r.allTimeNamed += news
        val top = l.firstOrNull { it.name in news }
        r.allTimeSeason = lg.season
        r.allTimeDay = lg.day
        r.allTimeNote = when {
            first -> "You've made the all-time list at No. $rank."
            top != null -> "You passed ${top.name} for No. $rank on the all-time list" + (if (news.size > 1) ", and ${news.size - 1} more." else ".")
            else -> return
        }
        lg.news += News(lg.season, lg.day,
            if (first) "${h.name} cracks the all-time list at No. $rank"
            else "${h.name} passes ${top!!.name} for No. $rank on the all-time list", true)
    }

    fun points(s: Double) = (s * 10).roundToInt() / 10.0
}
