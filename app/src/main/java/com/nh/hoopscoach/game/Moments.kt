package com.nh.hoopscoach.game

import kotlin.math.abs

/**
 * Big moments: buzzer-beaters, shots that force overtime, NCAA upsets (five or more seed lines),
 * multi-overtime classics, Cinderella runs and the national title. Each one makes a headline and is
 * kept in the season's highlight reel. Regular-season moments are only kept for the user's games.
 */
object Moments {
    /** Lifetime counters for tests: games seen, buzzer-beaters, shots that forced overtime. */
    val stats = IntArray(3)
    private const val KEEP = 400

    private fun bracketFor(lg: League, g: Game): Bracket? = when (g.type) {
        GameType.NCAA -> lg.ncaa
        GameType.NIT -> lg.nit
        GameType.CONF_TOURNEY -> lg.confBrackets.firstOrNull { it.confId == lg.team(g.home).confId }
        else -> null
    }

    fun stage(g: Game) = g.label.ifEmpty { if (g.day <= Schedule.REG_DAYS) "Game ${g.day}" else "Postseason" }

    private fun seeded(seed: Int, t: Team) = if (seed > 0) "No. $seed ${t.school}" else t.school

    private fun final(w: Team, l: Team, g: Game) =
        "${w.school} ${g.scoreFor(w.id)}, ${l.school} ${g.scoreFor(l.id)}${if (g.ot > 0) " (${if (g.ot > 1) "${g.ot}" else ""}OT)" else ""}"

    private fun clockText(c: GameEngine.Clutch) = if (c.clock <= 0.05) "at the horn" else "with %.1f seconds left".format(c.clock)

    fun ncaaRound(lg: League, g: Game): Int {
        val b = lg.ncaa ?: return 0
        if (g.label.endsWith("First Four")) return 0
        val r = b.gameIds.indexOfFirst { g.id in it }
        return if (r < 0) 0 else r + 1
    }

    fun afterGame(lg: League, g: Game, buzzer: GameEngine.Clutch?, forcedOt: GameEngine.Clutch?) {
        stats[0]++
        if (buzzer != null) stats[1]++
        if (forcedOt != null) stats[2]++
        val march = g.type == GameType.NCAA || g.type == GameType.NIT || g.type == GameType.CONF_TOURNEY
        val mine = g.involves(lg.userTeamId)
        if (!march && !mine) return
        val w = lg.team(g.winner())
        val l = lg.team(g.loser())
        val seeds = bracketFor(lg, g)?.seeds
        val ws = seeds?.get(w.id) ?: 0
        val ls = seeds?.get(l.id) ?: 0
        val ncaa = g.type == GameType.NCAA
        val upset = ncaa && ws > 0 && ls > 0 && ws - ls >= 5
        val round = if (ncaa) ncaaRound(lg, g) else 0
        val base = (if (ncaa) 20 + round * 6 else 0) + (if (mine) 40 else 0)
        fun add(kind: MomentKind, headline: String, detail: String, weight: Int) {
            lg.moments += Moment(lg.season, lg.day, kind, stage(g), headline, detail, w.id, l.id, ws, ls, g.scoreFor(w.id), g.scoreFor(l.id), g.ot, mine, weight + base, g.id)
            if (lg.moments.size > KEEP) lg.moments.removeAt(0)
            val tag = when (kind) { MomentKind.UPSET -> "upset"; MomentKind.BUZZER -> "buzzer"; else -> "moment" }
            lg.news += News(lg.season, lg.day, headline, mine, g.id, tag)
        }
        when {
            buzzer != null -> add(
                MomentKind.BUZZER,
                if (upset) "At the buzzer! ${seeded(ws, w)} stuns ${seeded(ls, l)}" else "At the buzzer! ${buzzer.name} lifts ${w.school} past ${l.school}",
                "${buzzer.name}'s ${buzzer.shot} ${clockText(buzzer)} ${if (buzzer.margin < 0) "erased a ${-buzzer.margin}-point deficit" else "broke the tie"}. ${final(w, l, g)}.",
                60 + if (upset) (ws - ls) * 3 else 0,
            )
            forcedOt != null && (ncaa || mine) -> {
                val shooter = lg.team(if (forcedOt.side == 0) g.home else g.away)
                add(
                    MomentKind.FORCED_OT,
                    "${forcedOt.name} forces overtime at the buzzer",
                    "${shooter.school} tied it with a ${forcedOt.shot} ${clockText(forcedOt)}. ${final(w, l, g)}.",
                    30,
                )
            }
            upset -> add(
                MomentKind.UPSET,
                if (ws - ls >= 13) "Historic upset: ${seeded(ws, w)} takes down ${seeded(ls, l)}" else "${seeded(ws, w)} stuns ${seeded(ls, l)}",
                topScorer(g, w)?.let { (name, pts) -> "$name scored $pts to lead ${w.school}${if (Moments.margin(g) >= 15) " in a ${Moments.margin(g)}-point rout" else ""}." } ?: "${final(w, l, g)}.",
                40 + (ws - ls) * 3,
            )
            ncaa && g.ot >= 2 -> add(MomentKind.CLASSIC, "${g.ot}OT classic: ${w.school} outlasts ${l.school}", "${final(w, l, g)}.", 30)
            mine && g.ot >= 2 -> add(MomentKind.CLASSIC, "${g.ot}OT classic: ${w.school} outlasts ${l.school}", "${final(w, l, g)}.", 10)
        }
    }

    /** After each NCAA round: Cinderella runs and the national champion. */
    fun afterRound(lg: League, b: Bracket) {
        if (b.confId != -1) return
        val r = b.rounds.lastIndex
        if (b.done) {
            val g = b.gameIds.lastOrNull()?.firstOrNull()?.takeIf { it >= 0 }?.let { lg.game(it) } ?: return
            title(lg, b, g)
            return
        }
        val (minSeed, label) = when (r) { 2 -> 10 to "dances into the Sweet 16"; 4 -> 7 to "crashes the Final Four"; else -> return }
        for (id in b.rounds[r]) {
            val seed = b.seeds[id] ?: continue
            if (seed < minSeed) continue
            val g = b.gameIds[r - 1].filter { it >= 0 }.map { lg.game(it) }.firstOrNull { it.involves(id) } ?: continue
            val w = lg.team(id)
            val l = lg.team(g.opp(id))
            val mine = id == lg.userTeamId
            val headline = "No. $seed ${w.school} $label"
            lg.moments += Moment(
                lg.season, lg.day, MomentKind.CINDERELLA, Moments.stage(g), headline,
                "${final(w, l, g)}, over No. ${b.seeds[l.id] ?: 0} ${l.school}.", w.id, l.id, seed, b.seeds[l.id] ?: 0,
                g.scoreFor(w.id), g.scoreFor(l.id), g.ot, mine, 45 + r * 6 + (if (mine) 40 else 0), g.id,
            )
            lg.news += News(lg.season, lg.day, headline, mine, g.id, "moment")
        }
    }

    private fun title(lg: League, b: Bracket, g: Game) {
        val w = lg.team(b.champion)
        val l = lg.team(g.opp(w.id))
        val mine = w.id == lg.userTeamId || l.id == lg.userTeamId
        val ws = b.seeds[w.id] ?: 0
        val ls = b.seeds[l.id] ?: 0
        val mop = lg.seasonAwards.firstOrNull { it.award == "MOP" }?.name
            ?: lg.rosterOf(w).filter { it.tourney.gp > 0 }.maxByOrNull { it.tourney.pts }?.name
        lg.moments += Moment(
            lg.season, lg.day, MomentKind.TITLE, stage(g),
            Reports.titleHeadline(w, l, ws, ls, margin(g), g.ot),
            Reports.titleDetail(w, l, g, ws, ls, mop),
            w.id, l.id, ws, ls, g.scoreFor(w.id), g.scoreFor(l.id), g.ot, mine, 130 + if (mine) 40 else 0, g.id,
        )
    }

    fun season(lg: League, season: Int) = lg.moments.filter { it.season == season }

    /** The best of a season, biggest first. */
    fun reel(lg: League, season: Int, limit: Int = 50) = season(lg, season).sortedByDescending { it.weight }.take(limit)

    fun margin(m: Moment) = abs(m.wScore - m.lScore)
    fun margin(g: Game) = abs(g.hs - g.aws)

    /** The winner's leading scorer, from the box score. */
    private fun topScorer(g: Game, w: Team): Pair<String, Int>? {
        val box = g.box ?: return null
        val line = (if (g.home == w.id) box.home else box.away).maxByOrNull { it.s.pts } ?: return null
        return line.name to line.s.pts
    }
}
