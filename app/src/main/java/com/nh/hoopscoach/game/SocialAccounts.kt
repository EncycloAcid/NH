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

    /** A real account for a kind of post: highlights, analysts, insiders (the college game has its own). */
    fun pick(lg: League, kind: String): Account? {
        if (!today(lg)) return null
        val types = when (kind) {
            "media" -> listOf("media")
            "analyst" -> if (lg.pro) listOf("analyst", "media") else listOf("cbb analyst", "cbb analytics", "bracketology")
            "insider" -> if (lg.pro) listOf("insider") else listOf("cbb insider", "recruiting")
            else -> return null
        }
        // Woj retired in 2024; the rest are fair game.
        val pool = all.filter { it.type in types && it.handle != "wojespn" }
        return if (pool.isEmpty()) null else Rng.pick(pool)
    }

    /** The league's own account: the NBA, or the NCAA. */
    fun league(lg: League): Account? = if (!today(lg)) null else all.firstOrNull { it.type == "league" && it.handle == (if (lg.pro) "NBA" else "NCAA") }
}
