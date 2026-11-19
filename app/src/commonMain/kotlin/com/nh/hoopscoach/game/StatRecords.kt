package com.nh.hoopscoach.game

/**
 * Season and career records, worked out from every season line on file (current players and former
 * ones), so the book grows as the world does. Nationally, or for one program. Single-game records stay
 * in [League.records].
 */
object StatRecords {
    /** One mark: the number as shown, who holds it, and where and when. */
    class Mark(val value: Double, val shown: String, val holder: String, val pid: Int, val teamId: Int, val detail: String)

    class Category(val label: String, val marks: List<Mark>)

    private class Line(val p: Player, val season: Int, val teamId: Int, val team: String, val s: StatLine)

    private fun lines(lg: League, teamId: Int?): List<Line> {
        val out = ArrayList<Line>()
        val byAbbr = lg.teams.associateBy { it.abbr }
        val seen = HashSet<Int>()
        for (p in lg.players.asSequence() + lg.alumni.asSequence()) {
            if (!seen.add(p.id)) continue
            for (l in p.career) {
                val pro = l.cls == PRO_YEAR
                if (pro != lg.pro) continue
                val tid = l.teamId.takeIf { it >= 0 } ?: byAbbr[l.team]?.id ?: -1
                if (teamId != null && tid != teamId) continue
                out += Line(p, l.season, tid, l.team, l.s)
            }
            // This season, until it's added to his career at the end of it.
            if (p.teamId >= 0 && p.stats.gp > 0 && p.career.none { it.season == lg.season } && (teamId == null || p.teamId == teamId))
                out += Line(p, lg.season, p.teamId, lg.team(p.teamId).abbr, p.stats)
        }
        return out
    }

    private fun f1(v: Double) = "%.1f".format(v)

    private fun teamLabel(lg: League, id: Int, fallback: String) = lg.teams.getOrNull(id)?.let { if (lg.pro) it.name else it.school } ?: fallback

    /** The best single seasons in each category ([n] each). Averages need 15 games. */
    fun seasons(lg: League, teamId: Int? = null, n: Int = 5): List<Category> {
        val all = lines(lg, teamId)
        val qualified = all.filter { it.s.gp >= 15 }
        fun cat(label: String, pool: List<Line>, value: (StatLine) -> Double, shown: (Double) -> String) = Category(label,
            pool.sortedByDescending { value(it.s) }.take(n).filter { value(it.s) > 0 }.map { l ->
                Mark(value(l.s), shown(value(l.s)), l.p.name, l.p.id, l.teamId,
                    "${teamLabel(lg, l.teamId, l.team)} · ${l.season}" + if (l.season == lg.season && lg.phase != Phase.OFFSEASON) " (so far)" else "")
            })
        return listOf(
            cat("Points per game", qualified, { it.ppg }, ::f1),
            cat("Rebounds per game", qualified, { it.rpg }, ::f1),
            cat("Assists per game", qualified, { it.apg }, ::f1),
            cat("Points in a season", all, { it.pts.toDouble() }, { "${it.toInt()}" }),
            cat("Threes in a season", all, { it.tpm.toDouble() }, { "${it.toInt()}" }),
            cat("Steals in a season", all, { it.stl.toDouble() }, { "${it.toInt()}" }),
            cat("Blocks in a season", all, { it.blk.toDouble() }, { "${it.toInt()}" }),
        )
    }

    /** Career totals (at one program when [teamId] is set). Career scoring average needs 60 games. */
    fun careers(lg: League, teamId: Int? = null, n: Int = 5): List<Category> {
        class Tot(val p: Player, val s: StatLine, val first: Int, val last: Int, val teams: Set<Int>)
        val tots = lines(lg, teamId).groupBy { it.p.id }.map { (_, ls) ->
            val s = StatLine()
            ls.forEach { s.add(it.s) }
            Tot(ls.first().p, s, ls.minOf { it.season }, ls.maxOf { it.season }, ls.map { it.teamId }.toSet())
        }
        fun cat(label: String, pool: List<Tot>, value: (StatLine) -> Double, shown: (Double) -> String) = Category(label,
            pool.sortedByDescending { value(it.s) }.take(n).filter { value(it.s) > 0 }.map { t ->
                val where = t.teams.mapNotNull { lg.teams.getOrNull(it)?.let { tm -> if (lg.pro) tm.name else tm.school } }.joinToString(", ")
                Mark(value(t.s), shown(value(t.s)), t.p.name, t.p.id, t.teams.firstOrNull() ?: -1,
                    (if (where.isNotEmpty()) "$where · " else "") + (if (t.first == t.last) "${t.first}" else "${t.first}-${t.last % 100}") + " · ${t.s.gp} games")
            })
        return listOf(
            cat("Career points", tots, { it.pts.toDouble() }, { "${it.toInt()}" }),
            cat("Career rebounds", tots, { it.reb.toDouble() }, { "${it.toInt()}" }),
            cat("Career assists", tots, { it.ast.toDouble() }, { "${it.toInt()}" }),
            cat("Career threes", tots, { it.tpm.toDouble() }, { "${it.toInt()}" }),
            cat("Career steals", tots, { it.stl.toDouble() }, { "${it.toInt()}" }),
            cat("Career blocks", tots, { it.blk.toDouble() }, { "${it.toInt()}" }),
            cat("Career scoring average", tots.filter { it.s.gp >= 60 }, { it.ppg }, ::f1),
            cat("Games played", tots, { it.gp.toDouble() }, { "${it.toInt()}" }),
        )
    }
}
