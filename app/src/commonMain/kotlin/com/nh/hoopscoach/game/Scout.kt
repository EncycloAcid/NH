package com.nh.hoopscoach.game

import kotlin.math.abs
import kotlin.math.roundToInt

/** One thing worth knowing about a team: what it is, how interesting (higher reads first), and the sentence. */
class Insight(val kind: String, val score: Double, val text: String, val pid: Int = -1)

/** A team's scouting report: the five, the bench, what stands out, the player to watch, the big question, and the range. */
class ScoutReport(
    val starters: List<Player>,
    val bench: List<Player>,
    val startRank: Int,
    val benchRank: Int,
    val n: Int,
    val insights: List<Insight>,
    val breakout: Player?,
    val breakoutWhy: String,
    val question: String,
    val best: String,
    val worst: String,
)

/**
 * The scout. Instead of restating ratings, it looks for what actually decides a season and says it in
 * plain words, with the numbers: whether the floor is spaced, who protects the rim, who runs the offense,
 * two scorers who both need the ball, how deep the bench is, whether last season's record was luck (wins
 * against the point margin), the breakout candidate, the aging starter, the contract year, the star who
 * missed half of last season, a new coach, and the schedule (its strength and its hardest stretch).
 * Every finding is scored by how much it matters, and a team's write-up leads with its own best ones,
 * so no two teams read alike and the same team reads differently from one year to the next.
 */
object Scout {
    private var cacheKey = ""
    private var cache: Map<Int, ScoutReport> = emptyMap()

    fun report(lg: League, t: Team): ScoutReport {
        val key = "${System.identityHashCode(lg)}:${lg.season}:${lg.day}:${lg.players.size}:${lg.phase}"
        if (key != cacheKey) { cache = build(lg); cacheKey = key }
        return cache[t.id] ?: build(lg)[t.id]!!
    }

    /** The team's write-up from its best [n] findings, skipping any that name a player in [skip]. */
    fun lines(lg: League, t: Team, n: Int, skip: String = ""): List<String> =
        report(lg, t).insights.filter { ins -> skip.isEmpty() || ins.pid < 0 || lg.playerOrNull(ins.pid)?.let { !skip.contains(it.name) } != false }.take(n).map { it.text }

    // ---------- the numbers behind it ----------

    private fun f1(v: Double) = "%.1f".format(v)
    private fun ord(n: Int) = Previews.ordinal(n)
    private fun shown(lg: League, v: Int) = if (lg.pro) Ratings.shownPro(v) else v
    private fun lastLine(lg: League, p: Player): SeasonLine? = p.career.lastOrNull { it.season == lg.season - 1 && (!lg.pro || it.cls == PRO_YEAR) && it.s.gp > 0 }
    private fun young(lg: League, p: Player) = if (lg.pro) p.age <= 23 else p.year <= 2
    private fun ageWord(lg: League, p: Player) = if (lg.pro) "${p.age}" else p.cls.lowercase()

    private class TeamNums(
        val t: Team, val rot: List<Player>, val starters: List<Player>, val bench: List<Player>,
        val startAvg: Double, val benchAvg: Double, val shoot: Double, val rim: Pair<Player, Double>?, val play: Pair<Player, Double>?,
        val sos: Double, val age: Double,
    )

    private fun starters(lg: League, t: Team, rot: List<Player>): List<Player> {
        val fromPlan = t.plan.slots.mapNotNull { s -> rot.firstOrNull { it.id == s.starter } }.distinct()
        return if (fromPlan.size == 5) fromPlan else rot.take(5)
    }

    private fun nums(lg: League, t: Team): TeamNums {
        val rot = lg.rosterOf(t).sortedByDescending { it.ovr }.take(if (lg.pro) 9 else 8)
        val st = starters(lg, t, rot)
        val bench = rot.filter { it !in st }.take(4)
        val games = lg.games.filter { it.involves(t.id) && !it.played && (it.type == GameType.CONF || it.type == GameType.NONCONF || it.type == GameType.EVENT) }
        val sos = if (games.isEmpty()) 1500.0 else games.map { lg.team(it.opp(t.id)).elo }.average()
        return TeamNums(
            t, rot, st, bench,
            st.map { it.ovr }.average().takeIf { !it.isNaN() } ?: 0.0,
            bench.map { it.ovr }.average().takeIf { !it.isNaN() } ?: 0.0,
            rot.take(8).map { it[Attr.OUT] }.average(),
            rot.take(8).maxByOrNull { it[Attr.BLK] + it[Attr.IDF] }?.let { it to (it[Attr.BLK] + it[Attr.IDF]) / 2.0 },
            rot.take(8).maxByOrNull { it[Attr.PAS] + it[Attr.HND] }?.let { it to (it[Attr.PAS] + it[Attr.HND]) / 2.0 },
            sos,
            rot.take(8).map { it.age }.average(),
        )
    }

    private fun build(lg: League): Map<Int, ScoutReport> {
        val all = lg.teams.associate { it.id to nums(lg, it) }
        val n = lg.teams.size
        fun rankOf(id: Int, key: (TeamNums) -> Double, high: Boolean = true): Int {
            val sorted = all.values.sortedBy { if (high) -key(it) else key(it) }
            return sorted.indexOfFirst { it.t.id == id } + 1
        }
        val startR = all.keys.associateWith { rankOf(it, { x -> x.startAvg }) }
        val benchR = all.keys.associateWith { rankOf(it, { x -> x.benchAvg }) }
        val shootR = all.keys.associateWith { rankOf(it, { x -> x.shoot }) }
        val rimR = all.keys.associateWith { rankOf(it, { x -> x.rim?.second ?: 0.0 }) }
        val playR = all.keys.associateWith { rankOf(it, { x -> x.play?.second ?: 0.0 }) }
        val sosR = all.keys.associateWith { rankOf(it, { x -> x.sos }) }
        val ageR = all.keys.associateWith { rankOf(it, { x -> x.age }) }
        val starGap = all.mapValues { (_, x) -> x.rot.getOrNull(0)?.let { a -> x.rot.getOrNull(1)?.let { b -> (a.ovr - b.ovr).toDouble() } } ?: 0.0 }
        val gapR = all.keys.associateWith { id -> starGap.values.sortedDescending().indexOf(starGap.getValue(id)) + 1 }
        val playoffLine = lg.teams.sortedByDescending { it.elo }.getOrNull(if (lg.pro) n / 2 else n / 6)?.elo ?: 1500.0
        return all.mapValues { (id, x) -> one(lg, x, n, startR.getValue(id), benchR.getValue(id), shootR.getValue(id), rimR.getValue(id), playR.getValue(id),
            sosR.getValue(id), ageR.getValue(id), gapR.getValue(id), playoffLine) }
    }

    private fun one(lg: League, x: TeamNums, n: Int, startRank: Int, benchRank: Int, shootRank: Int, rimRank: Int, playRank: Int,
                    sosRank: Int, ageRank: Int, gapRank: Int, playoffLine: Double): ScoutReport {
        val t = x.t
        val pro = lg.pro
        val out = mutableListOf<Insight>()
        val star = x.rot.firstOrNull()
        val third = n / 3
        val rnd = kotlin.random.Random(lg.season * 7_919L + t.id * 104_729L)
        fun <T> pick(vararg o: T): T = o[rnd.nextInt(o.size)]

        // Was last season's record real? Wins against the point margin.
        t.history.lastOrNull { it.season == lg.season - 1 }?.let { last ->
            val gp = last.w + last.l
            if (gp >= 10) {
                val exp = (gp * (0.5 + last.margin * (if (pro) 0.033 else 0.03))).coerceIn(0.0, gp.toDouble())
                val luck = last.w - exp
                val e = exp.roundToInt()
                val m = (if (last.margin >= 0) "+" else "−") + f1(abs(last.margin))
                if (luck >= (if (pro) 4.5 else 3.0)) out += Insight("lucky", 4.0 + luck * 0.5, pick(
                    "Last season's ${last.w}-${last.l} came with a $m point margin, the profile of a $e-win team. Close games broke their way, and that rarely happens twice.",
                    "Be careful with last year's ${last.w} wins: a $m margin says they were closer to $e. Expect some of those close wins to flip.",
                ))
                if (luck <= -(if (pro) 4.5 else 3.0)) out += Insight("unlucky", 3.6 - luck * 0.45, pick(
                    "They went ${last.w}-${last.l}, but a $m point margin is how a $e-win team plays. The close losses should start going the other way.",
                    "Better than the record: ${last.w}-${last.l} on a $m margin. Bad luck in close games doesn't usually last.",
                ))
            }
        }

        // Spacing.
        val shootLine = if (pro) 74 else 70
        val shooters = x.rot.take(8).filter { it[Attr.OUT] >= shootLine }
        if (shooters.size <= 1 && star != null) out += Insight("spacing", 5.0, pick(
            "Spacing is the problem: ${if (shooters.isEmpty()) "nobody in the rotation is a real threat from three" else "${shooters[0].name} is the only real threat from three"}, so defenses can crowd ${star.last}.",
            "${if (shooters.isEmpty()) "No one" else "Only ${shooters[0].last}"} in the top eight shoots it well from deep. Expect packed paints and a lot of tough twos.",
        ), star.id)
        else if (shooters.size >= 5) out += Insight("shooting", 3.4, pick(
            "${shooters.size} of the top eight can really shoot it, led by ${shooters.maxBy { it[Attr.OUT] }.name}. Good luck guarding all of them.",
            "Five-out spacing: ${shooters.size} shooters in the rotation means driving lanes everywhere.",
        ), shooters.maxBy { it[Attr.OUT] }.id)
        else if (shootRank <= 3) out += Insight("shooting", 2.8, "The ${ord(shootRank)}-best shooting rotation in the league.")

        // Rim protection.
        x.rim?.let { (p, _) ->
            if (rimRank > n - third) out += Insight("rim", 4.6, pick(
                "Who protects the rim? ${p.name} is the best shot blocker they have, and he'd be a backup on most teams.",
                "There's no rim protector here (the best is ${p.name}), so opponents will live in the paint.",
            ), p.id)
            else if (rimRank <= 3) out += Insight("anchor", 3.0, pick(
                "${p.name} anchors the defense: one of the league's best rim protectors.",
                "Drive on them at your own risk: ${p.name} is waiting at the rim.",
            ), p.id)
        }

        // Who runs it.
        x.play?.let { (p, _) ->
            if (playRank > n - third) out += Insight("playmaking", 4.2, pick(
                "No true lead guard: ${p.name} is the best playmaker, which means a lot of late-clock isolation.",
                "Somebody has to run the offense, and the best candidate is ${p.name}. That's a stretch.",
            ), p.id)
        }

        // Two scorers who both need it.
        val scoreLine = if (pro) 20.0 else 15.0
        val scorers = x.starters.mapNotNull { p -> lastLine(lg, p)?.takeIf { it.s.gp >= 10 && it.s.ppg >= scoreLine }?.let { p to it.s.ppg } }.sortedByDescending { it.second }
        if (scorers.size >= 2) {
            val (a, b) = scorers[0] to scorers[1]
            out += Insight("twostars", 3.9, pick(
                "${a.first.name} (${f1(a.second)} a game last year) and ${b.first.name} (${f1(b.second)}) both need the ball. How they share it decides the season.",
                "Two scorers, one ball: ${a.first.last} and ${b.first.last} combined for ${f1(a.second + b.second)} a night last season.",
            ), a.first.id)
        }

        // Depth.
        val sixth = x.bench.firstOrNull()
        if (sixth != null && benchRank <= 3) out += Insight("depth", 3.5, pick(
            "The ${if (benchRank == 1) "deepest" else "${ord(benchRank)}-deepest"} bench in the league: ${sixth.name} would start for most teams.",
            "Depth is a weapon: ${sixth.name} comes off this bench.",
        ), sixth.id)
        if (sixth != null && benchRank > n - 3) out += Insight("thin", 4.4, pick(
            "Thin: after the starting five there's a steep drop to ${sixth.name}. One injury and the rotation is in trouble.",
            "The bench ranks ${ord(benchRank)} of $n. The starters will play heavy minutes.",
        ), sixth.id)

        // It's his team.
        if (star != null && gapRank <= 3 && x.rot.size >= 2) out += Insight("onestar", 3.2, pick(
            "Everything runs through ${star.name}: no team leans on one player more.",
            "It's ${NbaPreviews.possessive(star.name)} team. When he sits, they're a different (worse) team.",
        ), star.id)

        // The breakout candidate.
        val breakout = x.rot.filter { young(lg, it) && it.pot - it.ovr >= 3 }.maxByOrNull { (it.pot - it.ovr) * 2 + it.ovr * 0.1 }
        var breakoutWhy = ""
        breakout?.let { b ->
            val last = lastLine(lg, b)
            val role = if (b in x.starters && (last == null || last.s.mpg < 24)) "and he moves into the starting five" else if (last != null && last.s.gp >= 10) "after ${f1(last.s.ppg)} a game last season" else "and the minutes are there"
            breakoutWhy = if (pro) "${ageWord(lg, b).replaceFirstChar { it.uppercase() }}, rated ${shown(lg, b.ovr)} with room to grow to ${shown(lg, b.pot)}, $role."
            else if (b.year == 1 && b.stars >= 4) "${b.stars}★ signee with an All-America ceiling, $role."
            else if (b.year == 1) "Freshman with big upside, $role."
            else "${ageWord(lg, b).replaceFirstChar { it.uppercase() }} poised for a breakout year, $role."
            out += Insight("breakout", 2.3 + (b.pot - b.ovr).coerceIn(0, 14) * 0.08, pick(
                "Breakout watch: ${b.name}. $breakoutWhy",
                "Keep an eye on ${b.name} (${ageWord(lg, b)}): $breakoutWhy",
            ), b.id)
        }

        // The aging starter (pros).
        if (pro) x.starters.filter { it.age >= 32 }.maxByOrNull { it.age }?.let { o ->
            val before = lg.proState?.before?.get(o.id)
            val slip = if (before != null && before > o.ovr) " His rating slipped from ${shown(lg, before)} to ${shown(lg, o.ovr)} over the summer." else ""
            out += Insight("aging", 2.8 + (o.age - 31) * 0.6 + (if (slip.isNotEmpty()) 1.0 else 0.0), pick(
                "${o.name} is ${o.age}, the oldest starter here.$slip How much he has left matters.",
                "Father Time is undefeated, and ${o.name} is ${o.age}.$slip",
            ), o.id)
        }

        // The contract year (pros).
        if (pro) x.rot.take(3).firstOrNull { it.contractYears == 1 && it.proYears > 0 && it.nextSalary == 0 && it.id != lg.heroId }?.let { c ->
            out += Insight("contract", 3.3, pick(
                "${c.name} is playing for his next contract: free agency is next summer. If the season goes sideways, his name will be in trade rumors by February.",
                "Contract year for ${c.name}. That's motivation, and a deadline subplot if it goes wrong.",
            ), c.id)
        }

        // Durability.
        x.rot.take(3).firstOrNull { p -> lastLine(lg, p)?.let { it.s.gp in 5 until (if (pro) 55 else 20) } == true }?.let { p ->
            val gp = lastLine(lg, p)!!.s.gp
            out += Insight("health", 3.7, pick(
                "${p.name} played only $gp games last season. Health is the swing factor.",
                "The question nobody can answer in October: can ${p.name} stay on the floor after a $gp-game season?",
            ), p.id)
        }

        // The coach.
        val c = lg.coachOf(t)
        val newCoach = t.history.isNotEmpty() && c.history.none { it.teamId == t.id }
        if (newCoach) out += Insight("coach", 3.4, pick(
            "First season under ${c.name}" + (if (c.w + c.l > 0) " (${c.w}-${c.l} as a head coach${if (c.titles > 0) ", ${c.titles} ${if (c.titles == 1) "title" else "titles"}" else ""})" else ", a first-time head coach") + ". New systems take time.",
            "${c.name} takes over, and everyone's starting from zero in his system.",
        ))
        else if (c.def >= 85 && c.def >= c.off) out += Insight("coachdef", 2.5, "${c.name}'s teams defend; expect that to be the identity again.")
        else if (c.off >= 85) out += Insight("coachoff", 2.5, "${c.name} runs one of the most creative offenses around.")

        // Age.
        if (ageRank <= 2) out += Insight("old", 2.6, "The oldest rotation in the league (${f1(x.age)} on average): built to win now.")
        if (ageRank >= n - 1) out += Insight("young", 2.6, "The youngest rotation in the league (${f1(x.age)}): expect growing pains and big swings.")

        // The schedule.
        val games = lg.games.filter { it.involves(t.id) && !it.played && (it.type == GameType.CONF || it.type == GameType.NONCONF || it.type == GameType.EVENT) }.sortedBy { it.day }
        val win = if (pro) 10 else 4
        if (games.size >= win + 2) {
            var bestI = 0; var bestV = -1.0
            for (i in 0..games.size - win) {
                val v = games.subList(i, i + win).sumOf { lg.team(it.opp(t.id)).elo }
                if (v > bestV) { bestV = v; bestI = i }
            }
            val stretch = games.subList(bestI, bestI + win)
            val good = stretch.count { lg.team(it.opp(t.id)).elo >= playoffLine }
            if (sosRank <= 3) out += Insight("schedule", 3.2, "One of the toughest schedules in the league (${ord(sosRank)} hardest). Game ${stretch.first().day} to game ${stretch.last().day} is the gauntlet: $good of $win against ${if (pro) "playoff" else "tournament"} teams.")
            else if (sosRank >= n - 2) out += Insight("schedule", 2.6, "A friendly schedule (${ord(n - sosRank + 1)} easiest), which helps a team on the edge.")
            else if (good >= (if (pro) 7 else 3)) out += Insight("stretch", 2.7, "Circle game ${stretch.first().day} through ${stretch.last().day}: $good of $win against ${if (pro) "playoff" else "tournament"} teams.")
        }

        val sorted = out.sortedByDescending { it.score }
        // The question that decides the season.
        val problem = sorted.firstOrNull { it.kind in setOf("spacing", "rim", "playmaking", "thin", "lucky", "aging", "health", "twostars", "contract", "coach") }
        fun name(pid: Int) = lg.playerOrNull(pid)?.last ?: ""
        val question = when (problem?.kind) {
            "spacing" -> "Can they space the floor around ${star?.last ?: "their stars"}?"
            "rim" -> "Who protects the rim?"
            "playmaking" -> "Who runs the offense when it matters?"
            "thin" -> "Can the bench hold up for a whole season?"
            "lucky" -> "Was last season's record real?"
            "aging" -> "How much does ${name(problem.pid)} have left?"
            "health" -> "Can ${name(problem.pid)} stay healthy?"
            "twostars" -> "Can ${scorers.getOrNull(0)?.first?.last} and ${scorers.getOrNull(1)?.first?.last} share the ball?"
            "contract" -> "Does ${name(problem.pid)}'s contract year help or hurt?"
            "coach" -> "How fast does the new system click?"
            else -> breakout?.let { "Is this ${it.last}'s year?" } ?: "Can they beat the teams above them?"
        }
        val good = when {
            breakout != null -> "${breakout.last} breaks out"
            problem?.kind == "health" -> "${name(problem.pid)} plays a full season"
            problem?.kind == "coach" -> "the new system clicks early"
            star != null -> "${star.last} plays at his best"
            else -> "everything breaks right"
        }
        val bad = when (problem?.kind) {
            "spacing" -> "defenses keep packing the paint"
            "rim" -> "opponents keep living at the rim"
            "playmaking" -> "the offense stalls late in games"
            "thin" -> "one injury exposes the bench"
            "lucky" -> "the close games go the other way"
            "aging" -> "${name(problem.pid)} finally slows down"
            "health" -> "${name(problem.pid)} misses time again"
            "twostars" -> "the two scorers never figure it out"
            "contract" -> "the contract year becomes a distraction"
            "coach" -> "the new system takes all year to learn"
            else -> "the stars cool off"
        }
        return ScoutReport(x.starters, x.bench, startRank, benchRank, n, sorted, breakout, breakoutWhy, question, good, bad)
    }

    /** The league's most telling findings, for a feature: luck both ways, breakouts, the gauntlets. */
    fun leagueNotes(lg: League, include: (Team) -> Boolean = { true }): List<Pair<String, String>> {
        val reps = lg.teams.filter(include).map { it to report(lg, it) }
        fun teamName(t: Team) = if (lg.pro) "the ${t.name}" else t.school
        val out = mutableListOf<Pair<String, String>>()
        reps.mapNotNull { (t, r) -> r.insights.firstOrNull { it.kind == "lucky" }?.let { t to it } }.sortedByDescending { it.second.score }.take(2).forEach { (t, i) ->
            out += "Due for a step back: ${teamName(t).replaceFirstChar { it.uppercase() }}" to i.text
        }
        reps.mapNotNull { (t, r) -> r.insights.firstOrNull { it.kind == "unlucky" }?.let { t to it } }.sortedByDescending { it.second.score }.take(2).forEach { (t, i) ->
            out += "Due for a jump: ${teamName(t).replaceFirstChar { it.uppercase() }}" to i.text
        }
        reps.mapNotNull { (t, r) -> r.breakout?.let { Triple(t, it, r.breakoutWhy) } }.sortedByDescending { it.second.pot - it.second.ovr }.take(3).forEach { (t, p, why) ->
            out += "Breakout: ${p.name}, ${teamName(t)}" to why
        }
        reps.mapNotNull { (t, r) -> r.insights.firstOrNull { it.kind == "schedule" && it.text.startsWith("One of the toughest") }?.let { t to it } }.take(1).forEach { (t, i) ->
            out += "The hardest road: ${teamName(t)}" to i.text
        }
        reps.mapNotNull { (t, r) -> r.insights.firstOrNull { it.kind == "spacing" || it.kind == "rim" }?.let { t to it } }
            .sortedByDescending { lg.teams.size - (lg.teams.sortedByDescending { x -> x.elo }.indexOf(it.first)) }.take(1).forEach { (t, i) ->
                out += "A contender's hole: ${teamName(t)}" to i.text
            }
        return out
    }
}
