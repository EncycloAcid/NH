package com.nh.hoopscoach.game

/**
 * How archetypes fit players and rosters: which styles suit a player's ratings best, and which of the
 * seven categories a roster covers (and which it's missing, for recruiting).
 */
object ArchFit {
    /** How well [a] suits [attrs]: the average of the three ratings it trains. */
    fun score(a: Arch, attrs: IntArray): Double = if (a.boosts.isEmpty()) 0.0 else a.boosts.sumOf { attrs[it.ordinal] }.toDouble() / a.boosts.size

    /** The archetypes at his positions that build on what he already does best. */
    fun best(p: Player, n: Int = 3): List<Arch> = Ratings.stylesFor(p).sortedByDescending { score(it, p.attrs) }.take(n)

    /** The categories that count for roster balance (Manual isn't a style). */
    val GROUPS = ArchGroup.entries.filter { it != ArchGroup.MANUAL }

    /** Players by the category of their play style. */
    fun coverage(players: List<Player>): Map<ArchGroup, List<Player>> = players.groupBy { it.style.group }

    /**
     * Categories the roster is missing: nobody whose style is in it. All-around players are welcome
     * anywhere, so that category is never called a gap.
     */
    fun gaps(players: List<Player>): List<ArchGroup> {
        val c = coverage(players)
        return GROUPS.filter { it != ArchGroup.TWO_WAY && c[it].isNullOrEmpty() }
    }

    /** Next season's roster: players who stay, plus commits. */
    fun nextRoster(lg: League, t: Team): List<Player> =
        lg.rosterOf(t).filter { Recruiting.stays(lg, it) } + lg.recruits.filter { it.committed == t.id }.map { it.p }
}
