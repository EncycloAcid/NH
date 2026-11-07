package com.nh.hoopscoach.game

import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Draft night in an NBA coaching career: the lottery, a big board of prospects with the ranges your
 * scouts can narrow, the league picking around you, trades up and down the board, and grades when
 * it's over. Player mode keeps the automatic draft in [ProCareer].
 */
object DraftNight {

    /** What the scouts can tell you about a prospect, on the shown pro scale. */
    class Est(val oLo: Int, val oHi: Int, val pLo: Int, val pHi: Int, val o: Double, val p: Double) {
        /** Value on your board: what he is now, plus half of what he could become. */
        val value get() = o + (p - o) / 2
    }

    /** A trade offer on draft night: picks are slots in this draft (0-based); futures are later first-rounders. */
    class Offer(val up: Boolean, val partner: Int, val give: List<Int>, val giveFuture: List<PickAsset>, val get: List<Int>, val getFuture: List<PickAsset>)

    class MockPick(val slot: Int, val teamId: Int, val pr: Prospect, val why: String)

    private val CLUBS = listOf("Mega Basket", "Partizan", "Real Madrid", "ASVEL", "Ratiopharm Ulm", "Joventut", "Zalgiris", "Bayern Munich", "Olympiacos", "Anadolu Efes")

    fun active(lg: League): Boolean = lg.proState?.draftNight?.let { !it.done } == true

    // ---------- opening the night ----------

    fun open(lg: League) {
        val ps = lg.proState ?: return
        val first = ProCareer.lotteryOrder(lg)
        val n = lg.teams.size
        val picks = minOf(60, 2 * n)
        // A traded first-round pick goes to the team that owns it (unless protected and it lands inside the protection).
        val order = MutableList(picks) { i -> if (i < n) Picks.resolveDraftOwner(lg, lg.season, first[i], i + 1) else first[i % n] }
        ps.draftedByUser.clear()
        // Past eras draft the real class of that year (1986 to 2026): names, colleges, ceilings and photos.
        val names = lg.players.map { it.name }.toSet()
        val real = if (Eras.of(lg) != Era.TODAY || lg.season <= ProLeague.DATA_SEASON) EraData.draft(lg.season).filter { it.name !in names }.associateBy { it.pick } else emptyMap()
        val schools = Schools.conferences.flatMap { c -> Schools.parse(c) }
        val made = mutableListOf<Pair<Prospect, Double>>()
        for (i in 0 until picks + 15) {
            val slot = i + 1
            val rp = real[slot]
            val pos = rp?.pos ?: Rng.pick(Pos.entries)
            val age = rp?.age?.takeIf { it > 0 } ?: Rng.range(19, 22)
            val p = LeagueGen.makePlayer(lg, pos, 4, ProLeague.draftOverall(slot).coerceIn(70, 99), -1)
            p.year = PRO_YEAR
            p.age = age
            p.proYears = 0
            p.height = (Ratings.heightFor(pos) + 1).coerceAtMost(88)
            p.weight = Ratings.weightFor(p.height)
            // Rookies come in raw, with real upside.
            p.pot = (p.ovr + (24 - age) * 2 + Rng.range(0, 4)).coerceIn(p.ovr, 99)
            p.recruitRank = 0
            p.stars = 0
            p.ovrHistory.clear()
            p.ovrHistory += p.ovr
            if (rp != null) EraData.apply(p, rp)
            val school = when {
                rp != null && rp.college.isNotEmpty() -> rp.college
                p.country != null -> "${Rng.pick(CLUBS)} (${p.country})"
                else -> Rng.weighted(schools) { (it.prestige * it.prestige).toDouble() }.name
            }
            if (rp == null) p.highSchool = school
            val pr = Prospect(p, 0, school, line(p, rp), (Rng.gauss() * 2.5).roundToInt(), (Rng.gauss() * 4.0).roundToInt())
            // The consensus: what the unscouted board shows, give or take what the experts hear. Real
            // draftees go about where they really went (so the real steals are still there to find).
            val score = if (rp != null) 85 - (slot - 1) * 0.15 + 4 + Rng.gauss() * 1.0
            else trueValue(p) + (pr.noiseO + pr.noiseP) / 2.0 + Rng.gauss() * 1.0
            made += pr to score
        }
        val ranked = made.sortedByDescending { it.second }.mapIndexed { i, (pr, _) -> Prospect(pr.p, i + 1, pr.school, pr.line, pr.noiseO, pr.noiseP) }
        val coach = runCatching { lg.coachOf(lg.user) }.getOrNull()
        ps.draftNight = DraftState(lg.season, order, ranked.toMutableList(), scoutPoints = 8 + (coach?.rec ?: 60) / 15)
        val mine = order.withIndex().filter { it.value == lg.userTeamId }.map { it.index + 1 }
        lg.news += News(lg.season, lg.day, "Draft night: " + if (mine.isEmpty()) "you have no picks tonight, but the phones are on" else "you pick at ${mine.joinToString(" and ") { "No. $it" }}", true)
    }

    /** A college line for a prospect: his real last season when we have it, otherwise one that fits his game. */
    private fun line(p: Player, rp: EraData.Pick?): String {
        rp?.let { EraData.college(it.id).lastOrNull() }?.let { c -> return "${f1(c.ppg)} pts · ${f1(c.rpg)} reb · ${f1(c.apg)} ast" }
        val a = p.attrs
        val scoring = (a[Attr.INS.ordinal] + a[Attr.MID.ordinal] + a[Attr.OUT.ordinal]) / 3.0
        val ppg = (8 + (scoring - 55) * 0.35 + Rng.gauss() * 2.0).coerceIn(6.0, 27.0)
        val rpg = (1.5 + (a[Attr.REB.ordinal] - 35) * 0.13 + Rng.gauss() * 0.8).coerceIn(1.5, 13.5)
        val apg = (0.6 + (a[Attr.PAS.ordinal] - 35) * 0.09 + Rng.gauss() * 0.5).coerceIn(0.4, 8.5)
        val where = if (p.country != null) " (pro league)" else ""
        return "${f1(ppg)} pts · ${f1(rpg)} reb · ${f1(apg)} ast$where"
    }

    private fun f1(d: Double) = "%.1f".format(d)

    /** A prospect's real worth (hidden): what he is now, plus half of what he could become, on the raw scale. */
    fun trueValue(p: Player): Double = p.ovr + (p.pot - p.ovr) / 2.0

    // ---------- scouting ----------

    fun estimate(pr: Prospect): Est {
        val k = 1.0 - pr.scout / 3.0
        val o = pr.p.ovr + pr.noiseO * k
        val pt = maxOf(o, pr.p.pot + pr.noiseP * k)
        val wO = 1 + 3 * k
        val wP = 2 + 5 * k
        fun s(v: Double) = Ratings.shownPro(v.roundToInt())
        return Est(s(o - wO), s(o + wO), s(pt - wP), s(pt + wP), Ratings.shownPro(o.roundToInt()).toDouble(), Ratings.shownPro(pt.roundToInt()).toDouble())
    }

    fun scout(lg: League, pid: Int) {
        val dn = lg.proState?.draftNight ?: return
        val pr = dn.prospects.firstOrNull { it.p.id == pid } ?: return
        if (dn.scoutPoints <= 0 || pr.scout >= 3) return
        dn.scoutPoints--
        pr.scout++
    }

    /** His two best ratings and his weakest, for the scouting report. */
    fun report(pr: Prospect): Pair<List<Attr>, Attr> {
        val a = pr.p.attrs
        val sorted = Attr.entries.sortedByDescending { a[it.ordinal] }
        return sorted.take(2) to sorted.last()
    }

    // ---------- the picks ----------

    /** The slot (0-based) the user picks at next, or -1. */
    fun nextUserSlot(lg: League): Int {
        val dn = lg.proState?.draftNight ?: return -1
        return (dn.at until dn.order.size).firstOrNull { dn.order[it] == lg.userTeamId } ?: -1
    }

    fun onTheClock(lg: League): Boolean {
        val dn = lg.proState?.draftNight ?: return false
        return !dn.done && dn.at < dn.order.size && dn.order[dn.at] == lg.userTeamId
    }

    /** How much [t] likes [pr]: real worth (teams scout too, imperfectly), the consensus, its needs and where it's headed. */
    private fun want(lg: League, t: Team, pr: Prospect, needs: List<Pos>, dir: Needs.Direction, noise: Boolean): Double {
        val p = pr.p
        var s = trueValue(p) * 0.7 - pr.rank * 0.15
        if (p.pos in needs || (p.pos2 != null && p.pos2 in needs)) s += 0.8
        when (dir) {
            Needs.Direction.REBUILDING -> s += (p.pot - p.ovr) * 0.08 - (p.age - 20) * 0.2
            Needs.Direction.CONTENDING -> s += (p.ovr - 80) * 0.05
            else -> {}
        }
        if (noise) s += Rng.gauss() * 0.7
        return s
    }

    private fun aiChoice(lg: League, t: Team, pool: List<Prospect>, noise: Boolean): Prospect {
        val needs = Needs.needsOf(lg, t)
        val dir = Needs.direction(lg, t)
        return pool.sortedBy { it.rank }.take(8).maxBy { want(lg, t, it, needs, dir, noise) }
    }

    /** The user's best available, by what his scouts see (the consensus breaks ties). */
    private fun userChoice(pool: List<Prospect>): Prospect = pool.maxBy { estimate(it).value * 10 - it.rank * 0.01 }

    fun grade(slot: Int, rank: Int): String {
        val d = (slot - rank) / sqrt(slot.toDouble())
        return when { d >= 1.5 -> "A+"; d >= 0.0 -> "A"; d >= -1.0 -> "B"; d >= -2.0 -> "C"; else -> "D" }
    }

    private fun points(g: String) = when (g) { "A+" -> 4.3; "A" -> 4.0; "B" -> 3.0; "C" -> 2.0; else -> 1.0 }

    /** Makes the current pick: [pid] goes to the team on the clock. */
    fun pick(lg: League, pid: Int) {
        val ps = lg.proState ?: return
        val dn = ps.draftNight ?: return
        if (dn.done || dn.at >= dn.order.size) return
        val pr = dn.prospects.firstOrNull { it.p.id == pid } ?: return
        val slot = dn.at + 1
        val t = lg.team(dn.order[dn.at])
        val p = pr.p
        p.teamId = t.id
        if (t.roster.any { lg.player(it).number == p.number }) p.number = Recruiting.freeNumber(lg, t)
        lg.addPlayer(p)
        t.roster += p.id
        Payroll.rookie(lg, p, slot)
        p.awards += "${lg.season} Draft #$slot"
        if (t.id == lg.userTeamId) ps.draftedByUser += p.id
        dn.prospects.remove(pr)
        val g = grade(slot, pr.rank)
        dn.log += DraftPickMade(slot, t.id, p.id, p.name, p.posLabel, pr.rank, g)
        val mine = t.id == lg.userTeamId
        if (slot <= 3 || mine) {
            val head = if (mine) "With the No. $slot pick, you take ${p.name} (${p.posLabel}, ${pr.school})"
            else "No. $slot: the ${t.name} take ${p.name} (${p.posLabel}, ${pr.school})"
            val body = "${p.name}, ${p.age}, was No. ${pr.rank} on the consensus board. ${pr.line}." +
                when { pr.rank - slot >= 5 -> " A reach, by most boards."; slot - pr.rank >= 5 -> " He slid further than anyone expected."; else -> "" }
            lg.news += News(lg.season, lg.day, head, mine, -1, "draft", body, "Draft Desk", t.id, p.id)
        }
        dn.at++
        if (dn.at >= dn.order.size) finish(lg)
    }

    /** The league picks until the user is on the clock (or the draft ends). */
    fun simToUser(lg: League) {
        val dn = lg.proState?.draftNight ?: return
        while (!dn.done && dn.at < dn.order.size && dn.order[dn.at] != lg.userTeamId) aiPick(lg)
    }

    /** The next pick, made by the team on the clock. */
    fun aiPick(lg: League) {
        val dn = lg.proState?.draftNight ?: return
        if (dn.done || dn.at >= dn.order.size || dn.prospects.isEmpty()) return
        val t = lg.team(dn.order[dn.at])
        pick(lg, aiChoice(lg, t, dn.prospects, noise = true).p.id)
    }

    /** Picks for the user: the best player on his board. */
    fun autoPick(lg: League) {
        val dn = lg.proState?.draftNight ?: return
        if (!onTheClock(lg) || dn.prospects.isEmpty()) return
        pick(lg, userChoice(dn.prospects).p.id)
    }

    fun simRest(lg: League) {
        val dn = lg.proState?.draftNight ?: return
        var guard = 0
        while (!dn.done && guard++ < 200) {
            if (dn.prospects.isEmpty()) { finish(lg); break }
            if (onTheClock(lg)) autoPick(lg) else aiPick(lg)
        }
    }

    fun finish(lg: League) {
        val dn = lg.proState?.draftNight ?: return
        if (dn.done) return
        dn.done = true
        ProCareer.afterCoachDraft(lg)
        column(lg, dn)
    }

    // ---------- the mock draft ----------

    /** The next [n] picks as the board sees them now. Changes nothing. */
    fun mock(lg: League, n: Int): List<MockPick> {
        val dn = lg.proState?.draftNight ?: return emptyList()
        val pool = dn.prospects.toMutableList()
        val out = mutableListOf<MockPick>()
        for (i in dn.at until minOf(dn.order.size, dn.at + n)) {
            if (pool.isEmpty()) break
            val t = lg.team(dn.order[i])
            val pr = if (t.id == lg.userTeamId) userChoice(pool) else aiChoice(lg, t, pool, noise = false)
            pool.remove(pr)
            val needs = Needs.needsOf(lg, t)
            val why = when {
                t.id == lg.userTeamId -> "Top of your board"
                pr.p.pos in needs -> "Fills a need at ${pr.p.pos}"
                Needs.direction(lg, t) == Needs.Direction.REBUILDING && pr.p.age <= 20 -> "Rebuilding team, youngest upside on the board"
                pr.rank <= i + 1 -> "Best player available"
                else -> "Their scouts are higher on him than the consensus"
            }
            out += MockPick(i + 1, t.id, pr, why)
        }
        return out
    }

    // ---------- trades ----------

    /** Value of a pick at [slot] (1-based) on the draft-day chart. */
    fun chart(slot: Int): Double = 1000 * exp(-0.065 * (slot - 1))

    /** A future first on the same chart: where that team is likely to pick, discounted for the wait. */
    fun chartFuture(lg: League, a: PickAsset): Double {
        val t = lg.team(a.original)
        val worse = lg.teams.count { it.winPct < t.winPct }
        val prot = if (a.protection > 0) 0.8 else 1.0
        return chart(worse + 1) * 0.8 * Math.pow(0.88, (a.season - lg.season - 1).coerceAtLeast(0).toDouble()) * prot
    }

    private fun futures(lg: League, teamId: Int): List<PickAsset> =
        lg.proState?.picks?.filter { it.owner == teamId && it.season > lg.season }?.sortedBy { it.season } ?: emptyList()

    /** Offers on the table: teams behind the user wanting to move up, and teams ahead willing to move down. */
    fun offers(lg: League): List<Offer> {
        val dn = lg.proState?.draftNight ?: return emptyList()
        if (dn.done) return emptyList()
        val u = nextUserSlot(lg)
        if (u < 0) return emptyList()
        val me = lg.userTeamId
        val rnd = kotlin.random.Random(dn.season * 1000L + dn.at * 7 + u)
        val out = mutableListOf<Offer>()
        // Trade down: a team picking later gives its pick and a sweetener for yours.
        val later = (u + 3 until minOf(dn.order.size, u + 14)).filter { dn.order[it] != me }.distinctBy { dn.order[it] }.shuffled(rnd)
        for (j in later) {
            if (out.size >= 3) break
            val partner = dn.order[j]
            val need = chart(u + 1) * 0.95
            val j2 = (j + 1 until dn.order.size).firstOrNull { dn.order[it] == partner }
            when {
                j2 != null && chart(j + 1) + chart(j2 + 1) >= need -> out += Offer(false, partner, listOf(u), emptyList(), listOf(j, j2), emptyList())
                else -> futures(lg, partner).firstOrNull { chart(j + 1) + chartFuture(lg, it) >= need }?.let {
                    out += Offer(false, partner, listOf(u), emptyList(), listOf(j), listOf(it))
                }
            }
        }
        // Trade up: a team picking ahead (before the user's pick) moves back for the user's pick and more.
        val ahead = (dn.at until u).filter { dn.order[it] != me }.distinctBy { dn.order[it] }.takeLast(6).shuffled(rnd)
        var ups = 0
        for (k in ahead) {
            if (ups >= 3) break
            val partner = dn.order[k]
            val need = chart(k + 1) * 1.05
            val u2 = (u + 1 until dn.order.size).firstOrNull { dn.order[it] == me }
            val add = when {
                u2 != null && chart(u + 1) + chart(u2 + 1) >= need -> Offer(true, partner, listOf(u, u2), emptyList(), listOf(k), emptyList())
                else -> futures(lg, me).firstOrNull { chart(u + 1) + chartFuture(lg, it) >= need }?.let { Offer(true, partner, listOf(u), listOf(it), listOf(k), emptyList()) }
            }
            if (add != null) { out += add; ups++ }
        }
        return out
    }

    fun accept(lg: League, o: Offer) {
        val dn = lg.proState?.draftNight ?: return
        val me = lg.userTeamId
        // Still on the table: every pick unmade and still with its owner.
        if (o.give.any { it < dn.at || dn.order[it] != me } || o.get.any { it < dn.at || dn.order[it] != o.partner }) return
        if (o.giveFuture.any { it.owner != me } || o.getFuture.any { it.owner != o.partner }) return
        for (s in o.give) dn.order[s] = o.partner
        for (s in o.get) dn.order[s] = me
        Picks.move(lg, o.giveFuture, lg.team(o.partner))
        Picks.move(lg, o.getFuture, lg.user)
        val t = lg.team(o.partner)
        val gave = o.give.map { "No. ${it + 1}" } + o.giveFuture.map { Picks.label(lg, it) }
        val got = o.get.map { "No. ${it + 1}" } + o.getFuture.map { Picks.label(lg, it) }
        val head = if (o.up) "Trade: you move up to No. ${o.get.first() + 1} with the ${t.name}" else "Trade: you move down from No. ${o.give.first() + 1} with the ${t.name}"
        lg.news += News(lg.season, lg.day, head, true, -1, "trade", "You send ${gave.joinToString(" and ")} to the ${t.name} for ${got.joinToString(" and ")}.", "Draft Desk", t.id)
    }

    fun describe(lg: League, o: Offer, give: Boolean): String {
        val slots = if (give) o.give else o.get
        val fut = if (give) o.giveFuture else o.getFuture
        return (slots.map { "No. ${it + 1}" } + fut.map { Picks.label(lg, it).removePrefix("a ") }).joinToString(" + ")
    }

    // ---------- grades ----------

    /** Each team's night: its grade-point average (weighted by where it picked) and the letter. */
    fun teamGrades(dn: DraftState): Map<Int, Pair<Double, String>> =
        dn.log.groupBy { it.teamId }.mapValues { (_, ps) ->
            val w = ps.sumOf { chart(it.pick) }
            val gpa = ps.sumOf { points(it.grade) * chart(it.pick) } / w
            gpa to letter(gpa)
        }

    fun letter(gpa: Double) = when {
        gpa >= 4.1 -> "A+"; gpa >= 3.6 -> "A"; gpa >= 3.25 -> "A-"; gpa >= 2.9 -> "B+"; gpa >= 2.55 -> "B"
        gpa >= 2.2 -> "B-"; gpa >= 1.85 -> "C+"; gpa >= 1.5 -> "C"; else -> "D"
    }

    private fun column(lg: League, dn: DraftState) {
        if (dn.log.isEmpty()) return
        val grades = teamGrades(dn)
        // Teams with a first-rounder are graded; a couple of second-rounders isn't a class.
        val graded = grades.filterKeys { id -> dn.log.any { it.teamId == id && it.pick <= lg.teams.size } }
        val parts = mutableListOf<String>()
        val first = dn.log.first()
        parts += "The ${lg.team(first.teamId).name} took ${first.name} first" +
            (if (first.rank == 1) ", the consensus No. 1." else ", over the board's No. 1. Bold, or wrong; we'll know in three years.")
        graded.maxByOrNull { it.value.first }?.let { (id, g) ->
            val names = dn.log.filter { it.teamId == id }.joinToString(", ") { "${it.name} (No. ${it.pick})" }
            parts += "Best night: the ${lg.team(id).name}, ${g.second}. $names."
        }
        graded.minByOrNull { it.value.first }?.takeIf { it.value.first < 3.0 }?.let { (id, g) ->
            parts += "Toughest night: the ${lg.team(id).name}, ${g.second}. They took players the boards had well below where they went."
        }
        dn.log.maxByOrNull { it.pick - it.rank }?.takeIf { it.pick - it.rank >= 6 }?.let {
            parts += "Steal of the draft: ${it.name} to the ${lg.team(it.teamId).name} at No. ${it.pick}. He was No. ${it.rank} on the consensus board."
        }
        dn.log.maxByOrNull { it.rank - it.pick }?.takeIf { it.rank - it.pick >= 6 }?.let {
            parts += "Biggest reach: ${it.name} to the ${lg.team(it.teamId).name} at No. ${it.pick}, ranked No. ${it.rank}."
        }
        val mine = dn.log.filter { it.teamId == lg.userTeamId }
        if (mine.isNotEmpty()) {
            val g = grades[lg.userTeamId]?.second ?: "B"
            parts += "Your class: " + mine.joinToString(", ") { "${it.name} (No. ${it.pick}, ${it.grade})" } + ". Our grade: $g."
        }
        val head = listOf("Draft grades: who won the night", "${lg.season} draft grades, team by team", "Grading the ${lg.season} draft")[lg.season % 3]
        lg.news += News(lg.season, lg.day, head, mine.isNotEmpty(), -1, "column", parts.joinToString("\n\n"), "Draft Desk", graded.maxByOrNull { it.value.first }?.key ?: -1, first.pid)
    }
}
