package com.nh.hoopscoach.game

/**
 * Today's league doesn't stand still. Once the real data runs out, the NBA can grow (two new teams,
 * announced two years ahead, stocked in an expansion draft) and a struggling small-market franchise
 * can move (announced a year ahead): it keeps its players and its history and takes a new city, name
 * and colors. Era careers follow the real teams instead (see Franchises).
 */
object Expansion {
    class City(val city: String, val name: String, val abbr: String, val primary: Long, val secondary: Long, val market: Int, val west: Boolean)

    val CITIES = listOf(
        City("Seattle", "SuperSonics", "SEA", 0xFF00653A, 0xFFFFC200, 70, true),
        City("Las Vegas", "Silver", "LVS", 0xFF101820, 0xFFC4CED4, 62, true),
        City("Kansas City", "Kings", "KCK", 0xFF004C97, 0xFFC8102E, 52, true),
        City("Vancouver", "Ravens", "VAN", 0xFF00205B, 0xFF6CACE4, 58, true),
        City("Mexico City", "Aztecas", "MEX", 0xFF006847, 0xFFCE1126, 75, true),
        City("San Diego", "Waves", "SDW", 0xFF002D62, 0xFF00A3E0, 58, true),
        City("Louisville", "Colonels", "LOU", 0xFFAD0000, 0xFF000000, 45, false),
        City("St. Louis", "Spirits", "STL", 0xFF041E42, 0xFFC41E3A, 52, false),
        City("Nashville", "Sound", "NSH", 0xFF041E42, 0xFFFFB81C, 55, false),
        City("Pittsburgh", "Pipers", "PIT", 0xFF101820, 0xFFFFB612, 54, false),
        City("Montreal", "Royaux", "MTL", 0xFF001E62, 0xFFAF1E2D, 60, false),
        City("Tampa Bay", "Storm", "TBS", 0xFF002244, 0xFF00B2A9, 56, false),
    )

    private fun city(abbr: String) = CITIES.firstOrNull { it.abbr == abbr }
    private fun taken(lg: League, c: City) = lg.teams.any { it.school == c.city || it.abbr == c.abbr }

    /** A new season starts: grow or move what was announced, and maybe announce what's next. */
    fun startSeason(lg: League) {
        val ps = lg.proState ?: return
        if (!lg.pro) return
        // Era careers with real teams for this season follow history instead.
        if (ps.realTeams && EraData.teams(lg.season) != null) return
        if (ps.expandAt == lg.season && ps.expandWith.isNotEmpty()) expand(lg)
        if (ps.moveAt == lg.season && ps.moveTeam >= 0) relocate(lg)
        val first = lg.proState?.history?.minOfOrNull { it.season } ?: lg.season
        if (lg.season < first + 1) return
        // Expansion: two new teams, two years out, up to 32.
        if (ps.expandAt < lg.season && lg.teams.size <= 30 && Rng.chance(0.22)) {
            val pick = CITIES.filter { !taken(lg, it) }.shuffled(Rng.r).take(2)
            if (pick.size == 2) {
                ps.expandAt = lg.season + 2
                ps.expandWith.clear(); ps.expandWith += pick.map { it.abbr }
                lg.news += News(lg.season, 0, "The NBA will expand to ${pick[0].city} and ${pick[1].city} for the ${lg.season + 2} season", false, -1, "",
                    "Two new teams, the ${pick[0].city} ${pick[0].name} and the ${pick[1].city} ${pick[1].name}, start play in ${lg.season + 2}. They'll stock their rosters in an expansion draft: every team protects its best eight.", "Hoops Wire")
            }
        }
        // Relocation: a small market that keeps losing, with nobody in the seats.
        if (ps.moveAt < lg.season && Rng.chance(0.12)) {
            val t = lg.teams.filter { it.id != lg.userTeamId && it.prestige < 52 }.filter { t ->
                val last = t.history.takeLast(3)
                last.size == 3 && last.all { it.w < it.l }
            }.minByOrNull { it.fans }
            val dest = CITIES.filter { !taken(lg, it) && it.abbr !in ps.expandWith }.shuffled(Rng.r).firstOrNull()
            if (t != null && dest != null) {
                ps.moveAt = lg.season + 1
                ps.moveTeam = t.id
                ps.moveTo = dest.abbr
                lg.news += News(lg.season, 0, "The ${t.name} will move to ${dest.city} after this season", false, -1, "",
                    "Three losing seasons and empty seats: the franchise is leaving ${t.school}. Next season they play in ${dest.city} as the ${dest.city} ${dest.name}, with the same players and their history.", "Hoops Wire", t.id)
            }
        }
    }

    private fun expand(lg: League) {
        val ps = lg.proState ?: return
        val fresh = ps.expandWith.mapNotNull { city(it) }.filter { !taken(lg, it) }.map { c ->
            val conf = if (c.west) lg.confs.maxBy { cf -> cf.name.contains("West").let { if (it) 1 else 0 } }.id else lg.confs.maxBy { cf -> cf.name.contains("East").let { if (it) 1 else 0 } }.id
            val t = Team(lg.teams.size, c.city, c.name, c.abbr, conf, c.market, c.primary, c.secondary, ProLeague.ERA_BADGE)
            t.slot = c.abbr
            t.arena = 17_500 + (c.market - 55) * 80
            t.fans = 70.0
            t.legacy = c.market.toDouble()
            t.buzz = 70.0
            val coach = Coach(lg.nextCoachId++, Rng.pick(Names.first), Rng.pick(Names.last), Rng.range(38, 62), t.id,
                Rng.range(55, 85), Rng.range(55, 85), Rng.range(40, 80), Rng.range(55, 88), Rng.int(0, 1_000_000))
            lg.coaches += coach
            t.coachId = coach.id
            lg.teams += t
            lg.confs[conf].teamIds += t.id
            t
        }
        ps.expandWith.clear()
        if (fresh.isEmpty()) return
        Franchises.expansionDraft(lg, fresh)
        Picks.ensure(lg)
        lg.reindex()
        lg.news += News(lg.season, 0, "Expansion: the league grows to ${lg.teams.size} teams with the ${fresh.joinToString(" and the ") { it.name }}", true, -1, "",
            fresh.joinToString(" ") { "The ${it.name} open in ${it.school} with a roster from the expansion draft." }, "Hoops Wire")
    }

    private fun relocate(lg: League) {
        val ps = lg.proState ?: return
        val t = lg.teams.getOrNull(ps.moveTeam) ?: return
        val c = city(ps.moveTo) ?: return
        ps.moveTeam = -1
        if (taken(lg, c)) return
        val old = t.name
        t.school = c.city
        t.mascot = c.name
        t.abbr = c.abbr
        t.primary = c.primary
        t.secondary = c.secondary
        t.logo = ProLeague.ERA_BADGE
        t.arena = 17_500 + (c.market - 55) * 80
        t.fans = 68.0
        t.buzz = 65.0
        lg.news += News(lg.season, 0, "The $old are now the ${t.name}", true, -1, "",
            "The franchise opens its first season in ${c.city}: new name, new colors, the same roster and the same history.", "Hoops Wire", t.id)
    }

    /** What's coming, for the league's history page. */
    fun outlook(lg: League): List<String> {
        val ps = lg.proState ?: return emptyList()
        val out = mutableListOf<String>()
        if (ps.expandAt > lg.season && ps.expandWith.isNotEmpty()) out += "Expansion in ${ps.expandAt}: " + ps.expandWith.mapNotNull { city(it) }.joinToString(" and ") { "the ${it.city} ${it.name}" }
        if (ps.moveAt > lg.season && ps.moveTeam >= 0) lg.teams.getOrNull(ps.moveTeam)?.let { t -> city(ps.moveTo)?.let { out += "In ${ps.moveAt}: the ${t.name} move to ${it.city}" } }
        return out
    }
}
