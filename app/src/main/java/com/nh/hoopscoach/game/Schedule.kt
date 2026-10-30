package com.nh.hoopscoach.game

object Schedule {
    const val NC_DAYS = 13
    const val CONF_GAMES = 18
    const val REG_DAYS = NC_DAYS + CONF_GAMES // 31
    const val EVENT_DAY = 4                   // in-season events run days 4-6
    const val CT_START = REG_DAYS + 1         // 32..35
    const val NCAA_START = CT_START + 4       // 36 First Four and NIT first round, 37..42 Round of 64 to title game
    const val LAST_DAY = NCAA_START + 6

    fun build(lg: League) {
        lg.games = mutableListOf()
        lg.events = mutableListOf()
        lg.confBrackets = mutableListOf()
        lg.ncaa = null
        lg.nit = null
        lg.bubble = null
        lg.nextGameId = 1
        lg.reindex()
        val played = HashSet<Long>()
        fun key(a: Int, b: Int) = if (a < b) a.toLong() * 10000 + b else b.toLong() * 10000 + a

        // In-season events: 4 fields of 8, mostly power programs.
        val ranked = Rng.noisyDesc(lg.teams) { it.prestige + Rng.gauss() * 8 }
        val invited = ranked.take(24).toMutableList()
        invited += lg.teams.filter { it !in invited && lg.conf(it.confId).tier == 1 }.shuffled(Rng.r).take(8)
        val user = lg.user
        if (user !in invited && ranked.indexOf(user) < 48) { invited.removeAt(invited.lastIndex); invited += user }
        val bySeed = invited.sortedByDescending { it.prestige }
        val fields = List(4) { mutableListOf<Team>() }
        bySeed.forEachIndexed { i, t ->
            val round = i / 4
            val slot = if (round % 2 == 0) i % 4 else 3 - i % 4
            fields[slot] += t
        }
        val eventTeams = HashSet<Int>()
        fields.forEachIndexed { i, f ->
            val ev = InSeasonEvent(Names.events[i], f.map { it.id }, EVENT_DAY)
            lg.events += ev
            eventTeams += ev.teams
            val pairs = listOf(0 to 7, 3 to 4, 2 to 5, 1 to 6)
            for ((a, b) in pairs) {
                val g = Game(lg.nextGameId++, EVENT_DAY, ev.teams[a], ev.teams[b], true, GameType.EVENT, "${ev.name} · R1")
                lg.addGame(g)
                ev.gameIds += g.id
                played += key(g.home, g.away)
            }
        }

        // Non-conference days.
        for (day in 1..NC_DAYS) {
            val avail = lg.teams.filter { day !in EVENT_DAY..EVENT_DAY + 2 || it.id !in eventTeams }
            val pairs = pairUp(avail, played)
            for ((a, b) in pairs) {
                played += key(a.id, b.id)
                val neutral = Rng.chance(0.06)
                val aHome = if (a.prestige >= b.prestige) Rng.chance(0.72) else Rng.chance(0.28)
                val (h, aw) = if (aHome) a to b else b to a
                lg.addGame(Game(lg.nextGameId++, day, h.id, aw.id, neutral, GameType.NONCONF))
            }
        }

        // Conference play: circle method, then a partial second pass to reach 18 games.
        for (c in lg.confs) {
            // Odd-sized conferences get a bye slot (-1); games against it are skipped.
            val ids = c.teamIds.shuffled(Rng.r).toMutableList<Int>().also { if (it.size % 2 == 1) it += -1 }
            val n = ids.size
            val rounds = mutableListOf<List<Pair<Int, Int>>>()
            val rot = ids.toMutableList()
            for (r in 0 until n - 1) {
                val pairs = mutableListOf<Pair<Int, Int>>()
                for (i in 0 until n / 2) {
                    val a = rot[i]
                    val b = rot[n - 1 - i]
                    pairs += if ((r + i) % 2 == 0) a to b else b to a
                }
                rounds += pairs
                // rotate all but the first
                val last = rot.removeAt(n - 1)
                rot.add(1, last)
            }
            val all = rounds.toMutableList()
            var k = 0
            while (all.size < CONF_GAMES) {
                all += rounds[k % rounds.size].map { (h, a) -> a to h }
                k++
            }
            val order = all.shuffled(Rng.r)
            order.forEachIndexed { idx, pairs ->
                val day = NC_DAYS + 1 + idx
                for ((h, a) in pairs) if (h >= 0 && a >= 0) lg.addGame(Game(lg.nextGameId++, day, h, a, false, GameType.CONF))
            }
        }
        lg.games.sortBy { it.day }
        lg.reindex()
    }

    private fun pairUp(all: List<Team>, played: Set<Long>): List<Pair<Team, Team>> {
        fun key(a: Int, b: Int) = if (a < b) a.toLong() * 10000 + b else b.toLong() * 10000 + a
        // With an odd number of teams, one random team sits out the day.
        val teams = if (all.size % 2 == 1) all.shuffled(Rng.r).drop(1) else all
        repeat(300) { attempt ->
            val list = teams.shuffled(Rng.r)
            val used = BooleanArray(list.size)
            val out = mutableListOf<Pair<Team, Team>>()
            var ok = true
            for (i in list.indices) {
                if (used[i]) continue
                used[i] = true
                var found = -1
                for (j in i + 1 until list.size) {
                    if (used[j]) continue
                    val strict = attempt < 250
                    if (strict && list[j].confId == list[i].confId) continue
                    if (key(list[i].id, list[j].id) in played) continue
                    found = j
                    break
                }
                if (found < 0) { ok = false; break }
                used[found] = true
                out += list[i] to list[found]
            }
            if (ok) return out
        }
        // Fallback: pair in order.
        val list = teams.shuffled(Rng.r)
        return list.chunked(2).filter { it.size == 2 }.map { it[0] to it[1] }
    }

    fun teamGames(lg: League, teamId: Int): List<Game> = lg.games.filter { it.involves(teamId) }.sortedBy { it.day }

    fun dayLabel(day: Int): String = when {
        day <= REG_DAYS -> "Game $day"
        day < NCAA_START -> "Conf. Tournament"
        else -> "Postseason"
    }
}
