package com.nh.hoopscoach.game

/**
 * NBA history: the real seasons (resources/eras/history.tsv, from basketball-reference's season pages;
 * personal use) and this league's own. Every team's record and how its season ended from 1979-80 on,
 * every champion since 1947, MVPs and Rookies of the Year. A league that starts from a real season (an
 * era career, Coach NBA, today's league) reads the real past up to its first season and its own
 * results after that, so the previews know the defending champions, last season's records, title
 * droughts, dynasties and rematches from day one.
 */
object NbaHistory {
    class TeamYear(val season: Int, val w: Int, val l: Int, val result: String) {
        val record get() = "$w-$l"
        val playoffs get() = result != "Missed playoffs" && result.isNotEmpty()
    }

    class Year(val season: Int, val champion: String, val runnerUp: String, val mvp: String, val roy: String)

    private class Data(val teams: Map<Int, Map<String, TeamYear>>, val years: Map<Int, Year>, val early: Map<Int, String>)

    private val data: Data by lazy {
        val teams = HashMap<Int, HashMap<String, TeamYear>>()
        val years = HashMap<Int, Year>()
        val early = HashMap<Int, String>()
        NbaHistory::class.java.getResourceAsStream("/eras/history.tsv")?.bufferedReader(Charsets.UTF_8)?.useLines { lines ->
            for (l in lines) {
                if (l.isBlank() || l.startsWith("#")) continue
                val f = l.split("|")
                val season = f.getOrNull(1)?.toIntOrNull() ?: continue
                when (f[0]) {
                    "T" -> if (f.size >= 6) teams.getOrPut(season) { HashMap() }[RealSeasons.slotOf(f[2])] =
                        TeamYear(season, f[3].toIntOrNull() ?: 0, f[4].toIntOrNull() ?: 0, f[5])
                    "S" -> if (f.size >= 6) years[season] = Year(season, f[2].takeIf { it.isNotEmpty() }?.let { RealSeasons.slotOf(it) } ?: "",
                        f[3].takeIf { it.isNotEmpty() }?.let { RealSeasons.slotOf(it) } ?: "", f[4], f[5])
                    "C" -> if (f.size >= 3) early[season] = f[2]
                }
            }
        }
        Data(teams, years, early)
    }

    /** The real season ending in [season], for the franchise in [slot]'s place. */
    fun real(season: Int, slot: String): TeamYear? = data.teams[season]?.get(slot)

    fun realYear(season: Int): Year? = data.years[season]

    /** The real champion of [season], by franchise slot. */
    fun realChampion(season: Int): String? = data.years[season]?.champion?.takeIf { it.isNotEmpty() } ?: data.early[season]

    /** This league's first season of its own; everything before it is the real past. */
    fun firstSeason(lg: League): Int = lg.proState?.history?.minOfOrNull { it.season } ?: lg.season

    private fun played(lg: League, season: Int) = lg.proState?.history?.firstOrNull { it.season == season }

    /** How [t]'s season ending in [season] went: this league's if it was played here, otherwise the real one. */
    fun season(lg: League, t: Team, season: Int): TeamYear? {
        if (played(lg, season) != null) return t.history.firstOrNull { it.season == season }?.let { TeamYear(season, it.w, it.l, it.result) }
        if (season >= firstSeason(lg)) return null
        return real(season, t.slot)
    }

    fun last(lg: League, t: Team): TeamYear? = season(lg, t, lg.season - 1)

    /** The champion and runner-up of [season]: this league's, or the real ones (as teams of this league, when they exist here). */
    fun finalists(lg: League, season: Int): Pair<Team?, Team?> {
        played(lg, season)?.let { return lg.teams.getOrNull(it.champion) to lg.teams.getOrNull(it.runnerUp) }
        if (season >= firstSeason(lg)) return null to null
        val y = data.years[season]
        fun bySlot(s: String?) = s?.takeIf { it.isNotEmpty() }?.let { slot -> lg.teams.firstOrNull { it.slot == slot } }
        return bySlot(realChampion(season)) to bySlot(y?.runnerUp)
    }

    /** Last season's MVP: this league's, or the real one. */
    fun lastMvp(lg: League): String? = played(lg, lg.season - 1)?.mvp?.takeIf { it.isNotEmpty() }
        ?: if (lg.season - 1 < firstSeason(lg)) data.years[lg.season - 1]?.mvp?.takeIf { it.isNotEmpty() } else null

    /** Every title the franchise in [t]'s place has won, oldest first: the real ones before this league, then this league's. */
    fun titles(lg: League, t: Team): List<Int> {
        val first = firstSeason(lg)
        val real = (1947 until first).filter { realChampion(it) == t.slot }
        val ours = lg.proState?.history?.filter { it.champion == t.id }?.map { it.season }.orEmpty()
        return real + ours
    }

    /** Seasons in a row, up to last season, that [t] made the playoffs. */
    fun playoffStreak(lg: League, t: Team): Int {
        var n = 0
        var s = lg.season - 1
        while (true) {
            val y = season(lg, t, s) ?: break
            if (!y.playoffs) break
            n++; s--
        }
        return n
    }

    /** Seasons in a row, up to last season, that [t] missed the playoffs. */
    fun missStreak(lg: League, t: Team): Int {
        var n = 0
        var s = lg.season - 1
        while (true) {
            val y = season(lg, t, s) ?: break
            if (y.playoffs) break
            n++; s--
        }
        return n
    }

    /** How a season ended, as a phrase: "won the title", "lost in the second round", "missed the playoffs". */
    fun phrase(result: String): String {
        val r = result.lowercase()
        return when {
            r == "champion" -> "won the title"
            r.contains("play-in") -> "lost in the play-in"
            r.contains("first round") -> "lost in the first round"
            r.contains("semifinal") || r.contains("second round") -> "lost in the second round"
            r.contains("conference final") -> "lost in the conference finals"
            r.contains("finals") -> "lost in the Finals"
            r.contains("missed") || r.isEmpty() -> "missed the playoffs"
            else -> r
        }
    }

    fun ordinalWord(n: Int) = when (n) { 1 -> "first"; 2 -> "second"; 3 -> "third"; 4 -> "fourth"; 5 -> "fifth"; else -> Previews.ordinal(n) }

    /** A title count in words: "17 titles", "one title". */
    fun titleWords(n: Int) = when (n) { 0 -> "no titles"; 1 -> "one title"; 2 -> "two titles"; 3 -> "three titles"; 4 -> "four titles"; 5 -> "five titles"; else -> "$n titles" }
}
