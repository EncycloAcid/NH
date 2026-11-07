package com.nh.hoopscoach.game

/**
 * Real accounts for the social feed, from the user's social accounts pack (resources/social,
 * personal use): NBA teams, college programs, media, insiders and analysts, with their avatars.
 * Today's game only: past-era careers keep made-up accounts (no 1986 Twitter).
 */
object SocialAccounts {
    class Account(val type: String, val name: String, val handle: String, val followers: Long, val verified: Boolean, val avatar: String)

    private val all: List<Account> by lazy {
        (SocialAccounts::class.java.getResourceAsStream("/social/accounts.tsv")?.bufferedReader(Charsets.UTF_8)?.use { it.readLines() } ?: emptyList())
            .filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split("|") }.filter { it.size >= 6 }
            .map { Account(it[0], it[1], it[2], it[3].toLongOrNull() ?: 0, it[4] == "1", it[5]) }
    }

    private fun today(lg: League) = if (lg.pro) Eras.of(lg) == Era.TODAY else lg.era == Era.TODAY.name

    /** A team's own account: "Denver Nuggets" in the pros, "North Carolina Tar Heels" in college. */
    fun forTeam(lg: League, name: String): Account? {
        if (!today(lg)) return null
        val type = if (lg.pro) "nba team" else "college mbb"
        return all.firstOrNull { it.type == type && it.name.equals(name, ignoreCase = true) }
    }

    /** Outlets that only cover the pros, or only the college game: neither turns up at the other level. */
    private val NBA_ONLY = setOf("The Athletic NBA", "NBA on ESPN", "NBA TV", "TNT Sports", "NBA on NBC", "NBA on Prime", "NBA 2K")
    private val COLLEGE_ONLY = setOf("ACC Network", "CBS Sports CBB", "FOX College Hoops")
    /** Clip and highlight pages: they post the play, not an opinion. */
    private val CLIPS = setOf("House of Highlights", "Ballislife", "Overtime", "SLAM", "Bleacher Report", "StatMuse", "NBA 2K", "Barstool Sports")

    /**
     * A real account for a kind of post, at the right level: "media" (outlets), "highlights" (clip pages and
     * the networks that air the games), "analyst" (outlets and people with takes), "person" (analysts who
     * are people, for first-person takes) and "insider" (the college game has its own).
     */
    fun pick(lg: League, kind: String): Account? {
        if (!today(lg)) return null
        val wrongLevel = if (lg.pro) COLLEGE_ONLY else NBA_ONLY
        val pool = when (kind) {
            "media" -> all.filter { it.type == "media" }
            "highlights" -> all.filter { it.type == "media" && (it.name in CLIPS || it.name in (if (lg.pro) setOf("NBA on ESPN", "NBA TV", "TNT Sports", "NBA on Prime") else setOf("CBS Sports CBB", "FOX College Hoops", "ESPN"))) }
            "analyst" -> if (lg.pro) all.filter { it.type == "analyst" || (it.type == "media" && it.name !in CLIPS) } else all.filter { it.type in listOf("cbb analyst", "cbb analytics", "bracketology") }
            "person" -> if (lg.pro) all.filter { it.type == "analyst" } else all.filter { it.type in listOf("cbb analyst", "cbb analytics", "bracketology") && it.name != "Ken Pomeroy" }
            "insider" -> all.filter { it.type in (if (lg.pro) listOf("insider") else listOf("cbb insider", "recruiting")) }
            else -> return null
        }.filter { it.handle != "wojespn" && it.name !in wrongLevel } // Woj retired in 2024; the rest are fair game.
        return if (pool.isEmpty()) null else Rng.pick(pool)
    }

    /** The league's own account: the NBA, or the NCAA. */
    fun league(lg: League): Account? = if (!today(lg)) null else all.firstOrNull { it.type == "league" && it.handle == (if (lg.pro) "NBA" else "NCAA") }
}
