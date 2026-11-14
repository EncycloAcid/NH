package com.nh.hoopscoach.game

/**
 * Selection committee: resumes, Bubble Watch, the 68-team field with the First Four, and the NIT field.
 *
 * Teams are ordered by one committee score, so the projection during the season and the real
 * selection on Selection Sunday always agree. NET (predictive) and Wins Above Bubble (results)
 * count equally; Quadrant 1 wins help and Quadrant 4 losses hurt.
 */
object Bracketology {
    const val FIELD = 68
    const val NIT_FIELD = 32
    private val SLOT_ORDER = intArrayOf(1, 16, 8, 9, 5, 12, 4, 13, 6, 11, 3, 14, 7, 10, 2, 15)
    private val NIT_ORDER = intArrayOf(1, 8, 4, 5, 3, 6, 2, 7)

    /** Positions on the 64-team S-curve held by First Four winners: two 11 seeds, two 16 seeds. */
    private val PLAY_IN_POSITIONS = intArrayOf(42, 43, 62, 63)

    fun score(net: Int, wabRank: Int, q1w: Int, q4l: Int) = 0.5 * net + 0.5 * wabRank - 0.4 * q1w + 1.5 * q4l

    /** Resume for every team from the regular season and conference tournaments, in one pass. */
    fun sheets(lg: League): Map<Int, Sheet> {
        val n = lg.teams.size
        val q = Array(n) { IntArray(8) }
        val w = IntArray(n)
        val l = IntArray(n)
        val opp = DoubleArray(n)
        val oppN = IntArray(n)
        val nc = DoubleArray(n)
        val ncN = IntArray(n)
        val wab = DoubleArray(n)
        val bubbleElo = lg.teams.sortedBy { it.net }.getOrNull(44)?.elo ?: 1500.0
        for (g in lg.games) {
            if (!g.played || g.type == GameType.NCAA || g.type == GameType.NIT) continue
            for (me in intArrayOf(g.home, g.away)) {
                val o = lg.team(g.opp(me))
                val won = g.winner() == me
                if (won) w[me]++ else l[me]++
                val quad = Rankings.quadrant(lg, o.net, g.site(me))
                q[me][(quad - 1) * 2 + if (won) 0 else 1]++
                opp[me] += o.elo; oppN[me]++
                if (g.type == GameType.NONCONF || g.type == GameType.EVENT) { nc[me] += o.elo; ncN[me]++ }
                val site = when { g.neutral -> 0; g.home == me -> 1; else -> -1 }
                wab[me] += (if (won) 1.0 else 0.0) - Rankings.winProb(bubbleElo, o.elo, site)
            }
        }
        fun ranks(v: DoubleArray, has: (Int) -> Boolean): IntArray {
            val r = IntArray(n)
            (0 until n).sortedByDescending { if (has(it)) v[it] else Double.NEGATIVE_INFINITY }.forEachIndexed { i, t -> r[t] = i + 1 }
            return r
        }
        val sosR = ranks(DoubleArray(n) { if (oppN[it] > 0) opp[it] / oppN[it] else 0.0 }) { oppN[it] > 0 }
        val ncR = ranks(DoubleArray(n) { if (ncN[it] > 0) nc[it] / ncN[it] else 0.0 }) { ncN[it] > 0 }
        val wabR = ranks(wab) { true }
        return lg.teams.associate { t ->
            val i = t.id
            i to Sheet(i, t.net, w[i], l[i], q[i], sosR[i], ncR[i], wab[i], wabR[i], score(t.net, wabR[i], q[i][0], q[i][7]))
        }
    }

    /** Each conference's bid: the tournament champion, else the favorite still alive, else the standings leader. */
    fun autoBids(lg: League): Map<Int, Int> {
        val out = LinkedHashMap<Int, Int>()
        for (c in lg.confs) {
            if (c.teamIds.isEmpty()) continue
            val b = lg.confBrackets.firstOrNull { it.confId == c.id }
            val alive = b?.rounds?.getOrNull(b.current)?.filter { it >= 0 }
            val pick = when {
                b == null -> Season.standings(lg, c.id).first().id
                b.done -> b.champion
                !alive.isNullOrEmpty() -> alive.minBy { b.seeds[it] ?: 99 }
                else -> Season.standings(lg, c.id).first().id
            }
            // A school serving a postseason ban can't take the bid: it goes to the best eligible school in the standings.
            out[c.id] = if (!WorldEvents.banned(lg, pick)) pick
                else Season.standings(lg, c.id).firstOrNull { !WorldEvents.banned(lg, it.id) }?.id ?: continue
        }
        return out
    }

    class Seeding(val slots: MutableList<Int>, val seeds: HashMap<Int, Int>, val playIns: MutableList<PlayIn>)

    class Projection(
        val sheets: Map<Int, Sheet>,
        /** Every team, best resume first. */
        val order: List<Int>,
        val auto: Set<Int>,
        val atLarge: List<Int>,
        /** The whole field in committee order. */
        val field: List<Int>,
        val seeding: Seeding,
        val atLargeRank: Map<Int, Int>,
        val spots: Int,
    ) {
        fun groups(final: Boolean): List<BubbleGroup> {
            val lockMax = (spots - 16).coerceAtLeast(0)
            val safeMax = (spots - 8).coerceAtLeast(0)
            fun r(id: Int) = atLargeRank[id] ?: Int.MAX_VALUE
            val isAuto = { id: Int -> id in auto }
            return listOf(
                BubbleGroup("lock", "Locks", if (final) "In the field with room to spare." else "In the field no matter what happens from here.",
                    order.filter { r(it) <= lockMax }),
                BubbleGroup("safe", "Should be in", if (final) "Comfortably above the cut line." else "One more good result seals it.",
                    order.filter { r(it) in lockMax + 1..safeMax }),
                BubbleGroup("bubble", "On the bubble", "The last four byes, then the last four in. The last four in play in the First Four.",
                    order.filter { !isAuto(it) && r(it) in safeMax + 1..spots }),
                BubbleGroup("ffo", "First four out", if (final) "Just missed. Top seeds in the NIT." else "The first teams left out right now.",
                    order.filter { !isAuto(it) && r(it) in spots + 1..spots + 4 }),
                BubbleGroup("nfo", "Next four out", if (final) "Headed to the NIT." else "Need a big week to climb back in.",
                    order.filter { !isAuto(it) && r(it) in spots + 5..spots + 8 }),
                BubbleGroup("auto", if (final) "Automatic bids" else "Projected automatic bids",
                    if (final) "Conference tournament champions from one-bid leagues." else "Favorites from one-bid leagues: the conference tournament favorite still alive, or the standings leader before it starts.",
                    order.filter { isAuto(it) && r(it) > safeMax }),
            )
        }

        fun snapshot(lg: League, final: Boolean): Bubble {
            val groups = groups(final)
            val keep = if (final) (groups.flatMap { it.ids } + lg.userTeamId).toSet() else null
            val list = sheets.values.filter { keep == null || it.teamId in keep }.sortedBy { it.rank }
            val ranks = if (keep == null) atLargeRank else atLargeRank.filterKeys { it in keep }
            return Bubble(lg.season, final, groups, list, HashMap(seeding.seeds), ranks, spots)
        }
    }

    fun project(lg: League): Projection {
        val sheets = sheets(lg)
        // Schools serving a postseason ban aren't considered at all.
        val order = sheets.values.sortedWith(compareBy<Sheet>({ it.score }, { it.net })).map { it.teamId }.filter { !WorldEvents.banned(lg, it) }
        order.forEachIndexed { i, id -> sheets.getValue(id).rank = i + 1 }
        for (sh in sheets.values) if (WorldEvents.banned(lg, sh.teamId)) sh.rank = sheets.size + 1     // listed last, never in
        val auto = autoBids(lg).values.toSet()
        val spots = (FIELD - auto.size).coerceAtLeast(4)
        val atLargeRank = HashMap<Int, Int>()
        var k = 0
        for (id in order) if (id in auto) atLargeRank[id] = k + 1 else { k++; atLargeRank[id] = k }
        val atLarge = order.filter { it !in auto }.take(spots)
        val inField = auto + atLarge
        val field = order.filter { it in inField }
        return Projection(sheets, order, auto, atLarge, field, seed(field, auto, atLarge), atLargeRank, spots)
    }

    /** What the League tab shows: a live projection in season, the committee's final call afterwards. */
    fun board(lg: League): Bubble? = when (lg.phase) {
        Phase.REGULAR, Phase.CONF_TOURNEY -> project(lg).snapshot(lg, final = false)
        else -> lg.bubble
    }

    fun status(b: Bubble, id: Int): String {
        val g = b.groupOf(id) ?: return "Outside the bubble"
        return when (g.key) {
            "lock" -> "Lock"
            "safe" -> "Should be in"
            "bubble" -> if ((b.atLargeRank[id] ?: 0) > b.atLargeSpots - 4) "Last four in" else "Last four byes"
            "ffo" -> "First four out"
            "nfo" -> "Next four out"
            else -> if (b.final) "Automatic bid" else "Projected auto bid"
        }
    }

    /**
     * Seeds the field on an S-curve into four regions of 16. With a full 68-team field, the last four
     * at-large teams play for two 11 seeds and the four lowest automatic qualifiers play for two 16 seeds.
     */
    fun seed(field: List<Int>, auto: Set<Int>, atLarge: List<Int>): Seeding {
        val seeds = HashMap<Int, Int>()
        val playIns = mutableListOf<PlayIn>()
        val usePlayIns = field.size == FIELD && atLarge.size >= 4 && auto.size >= 4
        val lastIn = if (usePlayIns) atLarge.takeLast(4) else emptyList()
        val lowAuto = if (usePlayIns) field.filter { it in auto }.takeLast(4) else emptyList()
        val pairs = if (usePlayIns) listOf(lastIn[0] to lastIn[3], lastIn[1] to lastIn[2], lowAuto[0] to lowAuto[3], lowAuto[1] to lowAuto[2]) else emptyList()
        val direct = field.filter { it !in lastIn && it !in lowAuto }
        val grid = Array(4) { IntArray(16) { -1 } }
        val pairAt = HashMap<Int, Int>()
        var d = 0
        for (pos in 0 until 64) {
            val line = pos / 4
            val k = pos % 4
            val region = if (line % 2 == 0) k else 3 - k
            val pi = if (usePlayIns) PLAY_IN_POSITIONS.indexOf(pos) else -1
            if (pi >= 0) {
                grid[region][line] = -2
                pairAt[region * 16 + line] = pi
            } else {
                val id = direct.getOrNull(d++) ?: -1
                grid[region][line] = id
                if (id >= 0) seeds[id] = line + 1
            }
        }
        val slots = mutableListOf<Int>()
        for (rg in 0 until 4) for (s in SLOT_ORDER) {
            val v = grid[rg][s - 1]
            if (v == -2) {
                val (a, b) = pairs[pairAt.getValue(rg * 16 + s - 1)]
                playIns += PlayIn(slots.size, a, b, s)
                seeds[a] = s
                seeds[b] = s
            }
            slots += v
        }
        return Seeding(slots, seeds, playIns)
    }

    /** NIT: four brackets of eight, seeded on an S-curve. Higher seeds host until the semifinals. */
    fun nitBracket(teams: List<Int>): Bracket? {
        if (teams.size < NIT_FIELD) return null
        val grid = Array(4) { IntArray(8) }
        val seeds = HashMap<Int, Int>()
        teams.take(NIT_FIELD).forEachIndexed { i, id ->
            val line = i / 4
            val k = i % 4
            grid[if (line % 2 == 0) k else 3 - k][line] = id
            seeds[id] = line + 1
        }
        val slots = mutableListOf<Int>()
        for (rg in 0 until 4) for (s in NIT_ORDER) slots += grid[rg][s - 1]
        return Bracket("NIT", "NIT", Season.NIT_ID, Schedule.NCAA_START, Season.NIT_ROUNDS, seeds, mutableListOf(slots))
    }
}
