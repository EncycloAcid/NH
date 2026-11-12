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
            .map { raw ->
                val handle = raw[2].ifBlank { raw[5] }.ifBlank { slug(raw[1]) }
                Account(raw[0], raw[1], handle, raw[3].toLongOrNull() ?: 0, raw[4] == "1", raw[5])
            }
    }

    fun isToday(lg: League) = if (lg.pro) Eras.of(lg) == Era.TODAY else lg.era == Era.TODAY.name

    private fun today(lg: League) = isToday(lg)

    /** Digits-and-letters only, with OKC normalized so pack names match in-game names. */
    private fun dig(s: String): String = s.lowercase()
        .replace("oklahoma city", "okc")
        .replace("okc thunder", "okcthunder")
        .replace(Regex("[^a-z0-9]"), "")

    private fun slug(name: String): String = name.replace(Regex("[^A-Za-z0-9]+"), "").ifBlank { "hoops" }

    /** A team's own account: "Denver Nuggets" in the pros, "North Carolina Tar Heels" in college. */
    fun forTeam(lg: League, name: String): Account? {
        if (!today(lg)) return null
        val type = if (lg.pro) "nba team" else "college mbb"
        val pool = all.filter { it.type == type }
        val want = dig(name)
        pool.firstOrNull { dig(it.name) == want }?.let { return it }
        // Last word when unique (Nuggets, Lakers, Tar Heels → Heels is weak; require longer tokens).
        val last = name.trim().split(Regex("\\s+")).lastOrNull()?.let(::dig).orEmpty()
        if (last.length >= 5) {
            val hits = pool.filter { dig(it.name).endsWith(last) || dig(it.name.substringAfterLast(' ')) == last }
            if (hits.size == 1) return hits[0]
        }
        // Soft contains both ways for odd pack casing ("Miami HEAT", "NEW YORK KNICKS").
        pool.firstOrNull { val k = dig(it.name); k.contains(want) || want.contains(k) }?.let { return it }
        return null
    }

    /** Resolve from the live [Team] so blank pack handles still get a real-looking @abbrMBB / @abbr. */
    fun forTeam(lg: League, t: Team): Account? {
        val found = forTeam(lg, t.name) ?: forTeam(lg, "${t.school} ${t.mascot}")
        if (found != null) {
            if (found.handle.isNotBlank()) return found
            val fallback = found.avatar.ifBlank { if (lg.pro) t.abbr else "${t.abbr}MBB" }
            return Account(found.type, found.name.ifBlank { t.name }, fallback, found.followers, found.verified, found.avatar)
        }
        if (!today(lg)) return null
        // Pack miss: still post under a stable team handle derived from the abbr.
        return Account(if (lg.pro) "nba team" else "college mbb", t.name, if (lg.pro) t.abbr else "${t.abbr}MBB", 0, false, "")
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
    fun pick(lg: League, kind: String, random: kotlin.random.Random = Rng.r): Account? {
        if (!today(lg)) return null
        val wrongLevel = if (lg.pro) COLLEGE_ONLY else NBA_ONLY
        val pool = when (kind) {
            "media" -> all.filter { it.type == "media" }
            "highlights" -> all.filter { it.type == "media" && (it.name in CLIPS || it.name in (if (lg.pro) setOf("NBA on ESPN", "NBA TV", "TNT Sports", "NBA on Prime") else setOf("CBS Sports CBB", "FOX College Hoops", "ESPN"))) }
            "analyst" -> if (lg.pro) all.filter { it.type == "analyst" || (it.type == "media" && it.name !in CLIPS) } else all.filter { it.type in listOf("cbb analyst", "cbb analytics", "bracketology") }
            "person" -> if (lg.pro) all.filter { it.type == "analyst" } else all.filter { it.type in listOf("cbb analyst", "cbb analytics", "bracketology") && it.name != "Ken Pomeroy" }
            "insider" -> all.filter { it.type in (if (lg.pro) listOf("insider") else listOf("cbb insider", "recruiting")) }
            else -> return null
        }.filter { it.handle.isNotBlank() && it.handle != "wojespn" && it.name !in wrongLevel } // Woj retired in 2024; the rest are fair game.
        return if (pool.isEmpty()) null else pool.random(random)
    }

    /** The league's own account: the NBA, or the NCAA. */
    fun league(lg: League): Account? = if (!today(lg)) null else all.firstOrNull { it.type == "league" && it.handle.equals(if (lg.pro) "NBA" else "NCAA", true) }
}
