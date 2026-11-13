package com.nh.hoopscoach.game

import kotlinx.serialization.Serializable
import kotlin.math.pow

/**
 * One save, one basketball history. Pre-league champions diverge from the real past so
 * dynasties, droughts and blue bloods are never the same twice. Programs carry a coaching
 * philosophy that former players can stamp when they take the bench, and drafted alumni
 * keep living in a shadow pro league that feeds rings and pipeline heat back to campus.
 */
@Serializable
class ShadowPro(
    val name: String,
    val school: String,
    val teamId: Int,
    val pick: Int,
    val draftYear: Int,
    var franchise: String,
    var seasons: Int = 0,
    var rings: Int = 0,
    var allStars: Int = 0,
    var peak: Int = 0,
    var ppg: Double = 0.0,
    var retired: Boolean = false,
    var retiredYear: Int = 0,
)

object Philosophy {
    data class Style(val name: String, val blurb: String, val offense: Offense, val defense: Defense)

    val ALL = listOf(
        Style("Pace & Space", "Push tempo and live behind the arc.", Offense.UPTEMPO, Defense.MAN),
        Style("Grind It Out", "Slow possessions, physical half-court sets.", Offense.GRIND, Defense.PACKLINE),
        Style("Paint First", "Pound the ball inside and crash the glass.", Offense.INSIDE, Defense.ZONE),
        Style("Perimeter Storm", "Spread the floor and hunt threes.", Offense.PERIMETER, Defense.MAN),
        Style("Pressure Defense", "Full-court heat and transition scores.", Offense.UPTEMPO, Defense.PRESS),
        Style("Balanced Pro", "Take what the defense gives; sound man principles.", Offense.BALANCED, Defense.MAN),
    )

    fun of(name: String) = ALL.firstOrNull { it.name == name } ?: ALL.last()

    fun pick(prestige: Int): Style = when {
        prestige >= 88 && Rng.chance(0.35) -> ALL[5]
        prestige >= 75 && Rng.chance(0.25) -> ALL[0]
        prestige <= 45 && Rng.chance(0.3) -> ALL[1]
        else -> Rng.pick(ALL)
    }

    fun apply(t: Team, style: Style) {
        t.philosophy = style.name
        t.plan.offense = style.offense
        t.plan.defense = style.defense
    }

    fun stampCoach(c: Coach, style: Style) {
        when (style.offense) {
            Offense.UPTEMPO, Offense.PERIMETER -> { c.off = (c.off + 4).coerceAtMost(96); c.rec = (c.rec + 2).coerceAtMost(95) }
            Offense.GRIND, Offense.INSIDE -> { c.def = (c.def + 4).coerceAtMost(96); c.dev = (c.dev + 2).coerceAtMost(95) }
            Offense.BALANCED -> { c.off = (c.off + 2).coerceAtMost(94); c.def = (c.def + 2).coerceAtMost(94) }
        }
        when (style.defense) {
            Defense.PRESS -> c.rec = (c.rec + 2).coerceAtMost(95)
            Defense.PACKLINE, Defense.ZONE -> c.def = (c.def + 2).coerceAtMost(96)
            Defense.MAN -> Unit
        }
    }
}

object AlternateHistory {
    private val COLLEGE_YEARS: List<Int> by lazy {
        listOf(
            1939, 1940, 1941, 1942, 1943, 1944, 1945, 1946, 1947, 1948, 1949,
            1951, 1952, 1953, 1954, 1955, 1956, 1957, 1958, 1959, 1960, 1961, 1962,
            1963, 1964, 1965, 1966, 1967, 1968, 1969, 1970, 1971, 1972, 1973, 1974, 1975, 1976,
            1977, 1978, 1979, 1980, 1981, 1982, 1983, 1984, 1985, 1986, 1987, 1988,
            1989, 1990, 1991, 1992, 1993, 1994, 1995, 1996, 1997, 1998, 1999, 2000, 2001,
            2002, 2003, 2004, 2005, 2006, 2007, 2008, 2009, 2010, 2011, 2012, 2013,
            2014, 2015, 2016, 2017, 2018, 2019, 2021, 2022, 2023, 2024, 2025,
        )
    }

    private val NBA_YEARS: List<Int> by lazy { (1947..2025).toList() }

    fun seedCollege(lg: League) {
        if (lg.altCollegeTitles.isNotEmpty()) return
        val schools = lg.teams.map { it.school }
        val weights = lg.teams.associate { it.school to (it.prestige / 100.0).pow(1.6).coerceAtLeast(0.04) }
        lg.altCollegeTitles.putAll(weave(COLLEGE_YEARS.filter { it < lg.season }, schools, weights))
        for (t in lg.teams) {
            t.titles = lg.altCollegeTitles.count { it.value == t.school }
            val style = Philosophy.pick(t.prestige)
            Philosophy.apply(t, style)
            Philosophy.stampCoach(lg.coachOf(t), style)
        }
        lg.universeSeed = Rng.int(1, Int.MAX_VALUE)
        lg.news += News(
            lg.season, 0,
            "This universe opens with a different college past: ${previewLine(lg.altCollegeTitles)}",
            true, -1, "universe",
            "National titles before ${lg.season} were redrawn for this save. Dynasties, droughts and blue bloods will not match the real record books.",
        )
    }

    fun seedNba(lg: League) {
        if (lg.altNbaTitles.isNotEmpty()) return
        val slots = lg.teams.map { it.slot }.filter { it.isNotEmpty() }.distinct()
        if (slots.isEmpty()) return
        val weights = lg.teams.filter { it.slot.isNotEmpty() }
            .associate { it.slot to (it.prestige / 100.0).pow(1.4).coerceAtLeast(0.05) }
        lg.altNbaTitles.putAll(weave(NBA_YEARS.filter { it < lg.season }, slots, weights))
        lg.universeSeed = if (lg.universeSeed == 0) Rng.int(1, Int.MAX_VALUE) else lg.universeSeed
        for (t in lg.teams) {
            val style = Philosophy.pick(t.prestige)
            Philosophy.apply(t, style)
            Philosophy.stampCoach(lg.coachOf(t), style)
        }
        lg.news += News(
            lg.season, 0,
            "This universe opens with a different NBA past: ${previewLine(lg.altNbaTitles)}",
            true, -1, "universe",
            "Championship banners before ${lg.season} were redrawn for this save. Franchise pedigrees diverge from the real record.",
        )
    }

    private fun previewLine(titles: Map<Int, String>): String {
        val counts = titles.values.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(3)
        return counts.joinToString(", ") { (name, n) -> "$name ($n)" }.ifEmpty { "a blank slate" }
    }

    private fun weave(years: List<Int>, pool: List<String>, weights: Map<String, Double>): Map<Int, String> {
        if (years.isEmpty() || pool.isEmpty()) return emptyMap()
        val out = LinkedHashMap<Int, String>()
        var i = 0
        while (i < years.size) {
            val cinderella = Rng.chance(0.08)
            val champ = if (cinderella) {
                pool.filter { (weights[it] ?: 0.05) < 0.2 }.ifEmpty { pool }.let { Rng.pick(it) }
            } else {
                weighted(pool, weights)
            }
            val maxRun = when {
                cinderella -> 1
                (weights[champ] ?: 0.1) >= 0.55 -> Rng.weighted(listOf(1, 2, 3, 4, 5, 6, 7)) { n ->
                    when (n) { 1 -> 3.0; 2 -> 4.0; 3 -> 2.5; 4 -> 1.5; 5 -> 0.7; 6 -> 0.35; else -> 0.15 }
                }
                (weights[champ] ?: 0.1) >= 0.3 -> Rng.weighted(listOf(1, 2, 3, 4)) { n ->
                    when (n) { 1 -> 4.0; 2 -> 3.0; 3 -> 1.5; else -> 0.6 }
                }
                else -> Rng.weighted(listOf(1, 2)) { if (it == 1) 5.0 else 1.0 }
            }
            val run = maxRun.coerceAtMost(years.size - i)
            repeat(run) { k -> out[years[i + k]] = champ }
            i += run
        }
        return out
    }

    private fun weighted(pool: List<String>, weights: Map<String, Double>): String {
        val total = pool.sumOf { weights[it] ?: 0.05 }
        var roll = Rng.d() * total
        for (s in pool) {
            roll -= weights[s] ?: 0.05
            if (roll <= 0) return s
        }
        return pool.last()
    }
}

object GenerationalLegacy {
    fun stampFromPlayer(college: League, school: String, past: PlayedAs?) {
        val t = college.teams.firstOrNull { it.school == school } ?: return
        val c = college.userCoach
        val style = styleFor(past)
        Philosophy.apply(t, style)
        Philosophy.stampCoach(c, style)
        t.legacyCoach = past?.name ?: c.name
        c.playedAt.clear()
        past?.college?.split("→")?.map { it.trim() }?.filter { it.isNotEmpty() }?.let { c.playedAt += it }
        past?.proTeams?.let { c.playedAt += it }
        c.mentor = ""
        college.news += News(
            college.season, college.day,
            "${c.name} installs ${style.name} at ${t.school}",
            true, -1, "legacy",
            "${c.name}'s playing days (${past?.verdict ?: "coach"}) shape the program. Recruits will hear the pedigree; the system is ${style.blurb.lowercase()}",
            "", t.id,
        )
    }

    private fun styleFor(past: PlayedAs?): Philosophy.Style {
        if (past == null) return Philosophy.pick(70)
        val pos = past.pos.uppercase()
        return when {
            past.rings >= 2 -> Philosophy.ALL[5]
            pos in setOf("PG", "SG") && past.proSeasons >= 6 -> Philosophy.ALL[0]
            pos in setOf("C", "PF") -> Philosophy.ALL[2]
            past.proSeasons >= 8 -> Philosophy.ALL[3]
            else -> Philosophy.pick(72)
        }
    }

    fun inheritProgram(lg: League, t: Team, c: Coach, mentor: String = "") {
        val style = if (t.philosophy.isNotBlank()) Philosophy.of(t.philosophy) else Philosophy.pick(t.prestige)
        Philosophy.apply(t, style)
        Philosophy.stampCoach(c, style)
        if (mentor.isNotBlank()) c.mentor = mentor
    }

    fun recruitFactor(lg: League, t: Team, r: Recruit): Pair<String, Double>? {
        val c = lg.coachOf(t)
        var mult = 1.0
        val bits = mutableListOf<String>()
        if (t.legacyCoach.isNotBlank() && (c.name == t.legacyCoach || c.isUser && lg.playedAs != null)) {
            mult += 0.08 + (lg.playedAs?.rings ?: 0) * 0.02
            bits += "playing pedigree"
        }
        if (t.philosophy.isNotBlank()) {
            val style = Philosophy.of(t.philosophy)
            val fit = when {
                r.pref == RecruitPref.WINNING && style.offense == Offense.GRIND -> 0.04
                r.pref == RecruitPref.PROS && style.name.contains("Pro") -> 0.06
                r.pref == RecruitPref.MINUTES && style.offense == Offense.UPTEMPO -> 0.04
                r.p.stars >= 4 && style.offense in setOf(Offense.PERIMETER, Offense.UPTEMPO) -> 0.05
                else -> 0.02
            }
            mult += fit
            bits += t.philosophy
        }
        val rings = lg.shadowPros.filter { it.teamId == t.id && !it.retired }.sumOf { it.rings } +
            lg.shadowPros.filter { it.teamId == t.id && it.retired && lg.season - it.retiredYear <= 4 }.sumOf { it.rings }
        if (rings > 0) {
            mult += (rings * 0.03).coerceAtMost(0.15)
            bits += "$rings pro rings from alumni"
        }
        if (bits.isEmpty()) return null
        return "Program identity" to mult.coerceIn(0.9, 1.35)
    }

    fun sidelineStory(lg: League, g: Game) {
        if (!g.played || (!lg.humanCoach(g.home) && !lg.humanCoach(g.away))) return
        val mine = if (lg.humanCoach(g.home)) lg.team(g.home) else lg.team(g.away)
        val theirs = lg.team(g.opp(mine.id))
        val me = lg.userCoach
        val them = lg.coachOf(theirs)
        val shared = me.playedAt.intersect(them.playedAt.toSet())
        val tree = lg.tree.any { it.coachId == them.id }
        val mentorClash = them.mentor.isNotBlank() && them.mentor == me.name
        if (shared.isEmpty() && !tree && !mentorClash) return
        if (lg.news.any { it.day == lg.day && it.kind == "legacy" && it.teamId == mine.id }) return
        val angle = when {
            mentorClash -> "${them.name}, once on ${me.last}'s staff, brings ${theirs.school} in against his old boss"
            tree -> "Coaching-tree clash: ${me.name}'s former assistant ${them.name} brings ${theirs.school} across"
            else -> "${me.name} faces ${them.name} — shared stops at ${shared.take(2).joinToString(" / ")}"
        }
        lg.news += News(lg.season, lg.day, angle, true, g.id, "legacy", "", "", mine.id)
    }
}

object ShadowProWorld {
    private val FRANCHISES = listOf(
        "Atlanta Hawks", "Boston Celtics", "Brooklyn Nets", "Charlotte Hornets", "Chicago Bulls",
        "Cleveland Cavaliers", "Dallas Mavericks", "Denver Nuggets", "Detroit Pistons", "Golden State Warriors",
        "Houston Rockets", "Indiana Pacers", "LA Clippers", "Los Angeles Lakers", "Memphis Grizzlies",
        "Miami Heat", "Milwaukee Bucks", "Minnesota Timberwolves", "New Orleans Pelicans", "New York Knicks",
        "Oklahoma City Thunder", "Orlando Magic", "Philadelphia 76ers", "Phoenix Suns", "Portland Trail Blazers",
        "Sacramento Kings", "San Antonio Spurs", "Toronto Raptors", "Utah Jazz", "Washington Wizards",
    )

    fun afterDraft(lg: League, picks: List<DraftPick>) {
        if (lg.pro) return
        for (dp in picks) {
            if (lg.shadowPros.any { it.name == dp.playerName && it.draftYear == dp.season }) continue
            val franchise = dp.nba.ifBlank { Rng.pick(FRANCHISES) }
            lg.shadowPros += ShadowPro(
                name = dp.playerName,
                school = dp.school,
                teamId = dp.teamId,
                pick = dp.pick,
                draftYear = dp.season,
                franchise = franchise,
                peak = dp.ovr,
                ppg = (8.0 + (80 - dp.pick) * 0.25 + (dp.ovr - 70) * 0.35).coerceIn(4.0, 28.0),
            )
        }
    }

    fun advance(lg: League) {
        if (lg.pro || lg.shadowPros.isEmpty()) return
        val active = lg.shadowPros.filter { !it.retired }
        if (active.isEmpty()) return
        val champ = Rng.weighted(active) { p ->
            val youth = (14 - p.seasons).coerceAtLeast(1)
            (p.peak / 10.0).pow(1.4) * youth * if (p.pick <= 14) 1.4 else 1.0
        }
        for (p in active) {
            p.seasons++
            val ageProxy = 22 + p.seasons
            if (p === champ) {
                p.rings++
                if (p.teamId == lg.userTeamId || (p.rings <= 2 && p.pick <= 10)) {
                    lg.news += News(
                        lg.season, lg.day,
                        "${p.name} (${p.school}) wins an NBA title with the ${p.franchise}",
                        p.teamId == lg.userTeamId, -1, "shadow",
                        "A ${p.draftYear} draft pick out of ${p.school} adds ring No. ${p.rings}. Campus recruiting feels it.",
                        "", p.teamId,
                    )
                }
            }
            if (Rng.chance(0.12 + (p.peak - 70).coerceAtLeast(0) * 0.004) && p.seasons in 2..12) p.allStars++
            p.ppg = (p.ppg + Rng.gauss() * 1.4).coerceIn(3.0, 32.0)
            val retire = ageProxy >= 36 || (ageProxy >= 32 && Rng.chance(0.22)) || (p.seasons >= 14 && Rng.chance(0.4))
            if (retire) {
                p.retired = true
                p.retiredYear = lg.season
                if (p.teamId == lg.userTeamId || p.rings >= 2 || p.allStars >= 5) {
                    lg.news += News(
                        lg.season, lg.day,
                        "${p.name} retires from the ${p.franchise} after ${p.seasons} seasons",
                        p.teamId == lg.userTeamId, -1, "shadow",
                        buildString {
                            append("${p.name} leaves the league")
                            if (p.rings > 0) append(" with ${p.rings} ${if (p.rings == 1) "ring" else "rings"}")
                            if (p.allStars > 0) append("${if (p.rings > 0) " and" else " with"} ${p.allStars} All-Star ${if (p.allStars == 1) "nod" else "nods"}")
                            append(". ${p.school}'s pipeline story grows.")
                        },
                        "", p.teamId,
                    )
                }
            }
        }
        if (lg.shadowPros.size > 400) {
            val keep = lg.shadowPros.sortedWith(
                compareByDescending<ShadowPro> { !it.retired }
                    .thenByDescending { it.rings * 10 + it.allStars }
                    .thenByDescending { it.draftYear },
            ).take(320)
            lg.shadowPros.clear()
            lg.shadowPros += keep
        }
    }

    fun heat(lg: League, teamId: Int): Int {
        val recent = lg.shadowPros.filter { it.teamId == teamId && (!it.retired || lg.season - it.retiredYear <= 3) }
        return recent.sumOf { p ->
            var v = when { p.pick <= 5 -> 3; p.pick <= 14 -> 2; p.pick <= 30 -> 1; else -> 0 }
            v += p.rings.coerceAtMost(3)
            if (p.allStars >= 3) v += 1
            v
        }.coerceAtMost(18)
    }

    fun brief(lg: League): String {
        val stars = lg.shadowPros.filter { !it.retired }.sortedByDescending { it.rings * 5 + it.allStars }.take(4)
        if (stars.isEmpty()) return ""
        return "SHADOW PRO LEAGUE: " + stars.joinToString("; ") {
            "${it.name} (${it.school}) · ${it.franchise} · ${it.rings} rings · ${"%.1f".format(it.ppg)} ppg"
        }
    }
}
