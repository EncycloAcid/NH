package com.nh.hoopscoach.game

/**
 * The real list of teams in an era career, season by season: 23 teams in 1985-86, the Heat and
 * Hornets in 1988-89, the Timberwolves and Magic in 1989-90, the Raptors and Grizzlies in 1995-96,
 * the Hornets' move to New Orleans in 2002-03, the Bobcats in 2004-05, and every new name in
 * between (Bullets to Wizards, Sonics to Thunder, New Jersey to Brooklyn...). New teams stock their
 * rosters in an expansion draft.
 */
object Franchises {
    private fun today(slot: String) = NbaData.TEAMS.first { it.abbr == slot }

    /** Franchises that moved into another's place in today's league: the Charlotte Hornets became the New Orleans Hornets in 2002. */
    private val MOVES = mapOf("CHA" to "NOP")

    /** How a franchise looks this season: today's name, logo and colors, or its old name on a badge in its old colors. */
    fun dress(t: Team, f: EraData.Franchise) {
        val now = today(f.slot)
        val same = f.city == now.city && f.name == now.name
        t.slot = f.slot
        t.school = f.city
        t.mascot = f.name
        t.abbr = if (same) now.abbr else f.abbr
        t.confId = now.conf
        val club = Eras.clubByAbbr(f.abbr)
        t.logo = if (same) ProLeague.LOGO else ProLeague.ERA_BADGE
        t.primary = if (same) now.primary else club?.primary ?: now.primary
        t.secondary = if (same) now.secondary else club?.secondary ?: now.secondary
    }

    /** A new team for [f], with a coach. */
    private fun newTeam(lg: League, f: EraData.Franchise): Team {
        val now = today(f.slot)
        val t = Team(lg.teams.size, f.city, f.name, f.abbr, now.conf, now.market, now.primary, now.secondary, ProLeague.LOGO)
        dress(t, f)
        t.arena = 17_500 + (now.market - 55) * 80
        t.fans = 55.0 + (now.market - 55) * 0.6
        t.legacy = now.market.toDouble()
        t.buzz = 45.0
        val c = Coach(lg.nextCoachId++, Rng.pick(Names.first), Rng.pick(Names.last), Rng.range(38, 66), t.id,
            Rng.range(55, 92), Rng.range(55, 92), Rng.range(40, 80), Rng.range(50, 90), Rng.int(0, 1_000_000))
        lg.coaches += c
        t.coachId = c.id
        lg.teams += t
        lg.confs[t.confId].teamIds += t.id
        return t
    }

    /** Puts the league in line with [season]'s real teams: new names, moves, and expansion teams. */
    fun apply(lg: League, season: Int) {
        val ps = lg.proState ?: return
        if (!ps.realTeams) return
        val list = EraData.teams(season) ?: return
        val want = list.associateBy { it.slot }
        val joining = list.filter { f -> lg.teams.none { it.slot == f.slot } }.toMutableList()
        // A move: the team keeps its players and its history and takes its new city (the Hornets to New Orleans).
        for (t in lg.teams.filter { it.slot !in want }) {
            val f = joining.firstOrNull { it.slot == MOVES[t.slot] } ?: continue
            joining.remove(f)
            val old = t.name
            lg.confs[t.confId].teamIds.remove(t.id)
            dress(t, f)
            lg.confs[t.confId].teamIds += t.id
            lg.news += News(season, 0, "The $old move to ${t.school}: they're the ${t.name} now", t.id == lg.userTeamId)
        }
        // New names (the Bullets become the Wizards, the Sonics the Thunder).
        for (t in lg.teams) {
            val f = want[t.slot] ?: continue
            if (t.school == f.city && t.mascot == f.name) continue
            val old = t.name
            dress(t, f)
            lg.news += News(season, 0, "The $old are now the ${t.name}", t.id == lg.userTeamId)
        }
        val fresh = joining.map { newTeam(lg, it) }
        if (fresh.isNotEmpty()) {
            expansionDraft(lg, fresh)
            lg.news += News(season, 0, "Expansion: the league grows to ${lg.teams.size} teams with the ${fresh.joinToString(" and the ") { it.name }}", false)
        }
        lg.reindex()
    }

    /**
     * Each new team picks in turn from the players other teams leave unprotected (everyone past a
     * team's best eight; never the user's player or his rival), and each team loses at most one
     * player. Free agents and generated players fill the rest.
     */
    fun expansionDraft(lg: League, fresh: List<Team>) {
        val keep = setOf(lg.heroId, lg.road?.rivalId ?: -1)
        val old = lg.teams.filter { it !in fresh }
        val lost = mutableSetOf<Int>()
        var turn = 0
        while (fresh.any { it.roster.size < 11 }) {
            val t = fresh[turn++ % fresh.size]
            if (t.roster.size >= 11) continue
            val (p, from) = old.filter { it.id !in lost }.flatMap { o ->
                lg.rosterOf(o).sortedByDescending { it.ovr }.drop(8).filter { it.id !in keep }.map { it to o }
            }.maxByOrNull { (p, _) -> p.ovr + (30 - p.age).coerceAtLeast(0) * 0.3 } ?: break
            from.roster.remove(p.id)
            t.roster += p.id
            p.teamId = t.id
            lost += from.id
            lg.news += News(lg.season, 0, "Expansion draft: the ${t.name} take ${p.name} from the ${from.name}", from.id == lg.userTeamId)
        }
        for (t in lg.teams) {
            while (t.roster.size < ProLeague.ROSTER) ProLeague.makePro(lg, Rng.pick(Pos.entries), if (t in fresh) Rng.range(80, 86) else Rng.range(83, 87), Rng.range(22, 31), t)
            Tactics.autoAllocate(lg, t)
        }
    }
}
