package com.nh.hoopscoach.game

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.roundToInt

/** One pundit's side of the debate: who, which side, his style (the numbers or the hot take), and what he said (the last line answers the other guy). */
@Serializable
class TvTake(val name: String, val handle: String, val avatar: String = "", val verified: Boolean = false, val side: String, val lines: List<String>, val persona: String = "")

/**
 * This week's segment: the show, the question, both takes (YES first), the fans' vote (percent yes), a check of
 * last week's call, the user's possible answers (and what each says), and which he gave (-1: not yet). What it
 * hinges on is kept so next week's show can say who was right: [metric] ("ppg" or "win"), the line it's
 * measured against ([base]), which way YES said it would go ([expect]), and which side the numbers took
 * ([numbersYes]).
 */
@Serializable
class TvSegment(
    val season: Int, val day: Int, val show: String, val topic: String, val takes: List<TvTake>, val responses: List<String>,
    var answered: Int = -1,
    val kind: String = "", val pid: Int = -1, val poll: Int = -1, val recap: String = "", val quotes: List<String> = emptyList(),
    val metric: String = "", val base: Double = 0.0, val expect: Int = 0, val numbersYes: Boolean = true,
)

/**
 * The weekly sports-TV debate.
 *
 * Every week the show looks at what's actually going on and finds the questions worth arguing (more than fifty
 * kinds: award cases, streaks and slumps, rivals, contracts, roles, efficiency, the farewell tour, the hot
 * seat, the deadline, tanking, injuries, the bubble...). A question only comes up when it applies, with enough
 * games behind it. The data decides which side is right: the numbers guy takes that side and makes the case
 * with the real numbers; the hot-take guy argues the other side with the eye test and narrative, and uses a
 * number when one honestly cuts his way. Each answers the other. The fans vote, next week's show checks who
 * was right, and you answer in your own words.
 */
object TvShow {
    fun showName(lg: League) = if (lg.pro) "Full Court Press" else "Hardwood Live"

    private fun f1(d: Double) = "%.1f".format(d)
    private fun pct(d: Double) = "${(d * 100).roundToInt()}%"
    private fun ord(n: Int) = Previews.ordinal(n)

    /** A number that backs one side. */
    private class Fact(val yes: Boolean, val text: String, val weight: Double = 1.0)

    private class Topic(
        val kind: String, val question: String,
        /** What the data says, -1 (clearly no) to 1 (clearly yes). */
        val lean: Double,
        /** How big a story it is this week. */
        val interest: Double,
        val facts: List<Fact>,
        /** The eye-test arguments for each side. */
        val yesTalk: List<String>, val noTalk: List<String>,
        val answers: List<Pair<String, String>>,
        val pid: Int = -1, val metric: String = "", val base: Double = 0.0, val expect: Int = 0,
        /** A nudge to the fans' vote from the hype (positive toward yes). */
        val hype: Double = 0.0,
        /** Last words for each side, answering the other. */
        val yesBack: List<String> = emptyList(), val noBack: List<String> = emptyList(),
    )

    // ---------- the week's show ----------

    fun daily(lg: League) {
        if (lg.phase != Phase.REGULAR && lg.phase != Phase.PLAYOFFS) return
        if (lg.day % 7 != 4) return
        val t = lg.user
        if (t.w + t.l < 6) return
        val topics = candidates(lg)
        if (topics.isEmpty()) return
        val recent = lg.tvKinds.takeLast(6).toSet()
        val older = lg.tvKinds.dropLast(6).toSet()
        val fresh = topics.filter { it.kind !in recent }
        // Nothing new to argue about this week: no segment beats a rerun.
        val pick = fresh.maxByOrNull { tp ->
            val base = tp.interest * (1 + abs(tp.lean)) + Rng.d() * 1.5
            if (tp.kind in older) base * 0.6 else base
        } ?: return
        val seg = stage(lg, pick)
        lg.tv = seg
        lg.tvKinds += pick.kind
        while (lg.tvKinds.size > 14) lg.tvKinds.removeAt(0)
        val yes = seg.takes[0]; val no = seg.takes[1]
        lg.news += News(lg.season, lg.day, "${seg.show}: ${seg.topic}", true, -1, "tv",
            (if (seg.recap.isNotEmpty()) "${seg.recap} " else "") + "${yes.name} (yes): \"${yes.lines.first()}\" ${no.name} (no): \"${no.lines.first()}\" Fans: ${seg.poll}% yes.",
            seg.show, t.id, if (lg.playerMode) lg.heroId else -1)
    }

    /** Every question that applies this week, for the show (and for checking the show's judgment). */
    fun questions(lg: League): List<Triple<String, String, Double>> = candidates(lg).map { Triple(it.kind, it.question, it.lean) }

    private fun candidates(lg: League): List<Topic> = runCatching {
        val c = Ctx(lg)
        val out = mutableListOf<Topic>()
        if (lg.playerMode) { c.hero?.let { heroTopics(c, it, out) } } else coachTopics(c, out)
        // A debate needs two sides: nothing the numbers have already settled, and the numbers' side needs a number.
        out.filter { tp -> abs(tp.lean) <= 0.85 && tp.facts.any { it.yes == (tp.lean >= 0) } }
    }.getOrDefault(emptyList())

    /** Turns a topic into the segment: who argues which side, with what. */
    private fun stage(lg: League, tp: Topic): TvSegment {
        val numbersYes = tp.lean >= 0
        val (a, b) = voices(lg)
        val forData = tp.facts.filter { it.yes == numbersYes }.sortedByDescending { it.weight }
        val against = tp.facts.filter { it.yes != numbersYes }.sortedByDescending { it.weight }
        val dataTalk = if (numbersYes) tp.yesTalk else tp.noTalk
        val otherTalk = if (numbersYes) tp.noTalk else tp.yesTalk
        val numbersPool = (forData.map { it.text } + dataTalk).distinct()
        val numbersLines = numbersPool.take(2)
        val dataBack = if (numbersYes) tp.yesBack else tp.noBack
        val otherBack = if (numbersYes) tp.noBack else tp.yesBack
        val numbersBack = dataBack.takeIf { it.isNotEmpty() }?.let { Rng.pick(it) }
            ?: forData.getOrNull(2)?.let { "And another thing: ${it.text}" } ?: numbersPool.getOrNull(2)
            ?: Rng.pick(listOf("Argue with the numbers, not with me.", "The evidence is right there on the screen.", "That's not an opinion. That's the box score."))
        val hotLines = (listOfNotNull(against.firstOrNull { it.weight >= 1.0 }?.text) + otherTalk).distinct().take(2)
        val hotBack = otherBack.takeIf { it.isNotEmpty() }?.let { Rng.pick(it) }
            ?: Rng.pick(listOf("Numbers don't play the games.", "I watch the games. You watch spreadsheets.", "Talk to me when it matters, in April.", "The box score doesn't tell you what I see.", "Small sample. Big conclusions. Classic."))
        val numbers = TvTake(a.name, a.handle, a.avatar, a.verified, if (numbersYes) "Yes" else "No", numbersLines + numbersBack, "The numbers")
        val hot = TvTake(b.name, b.handle, b.avatar, b.verified, if (numbersYes) "No" else "Yes", hotLines + hotBack, "Hot take")
        val poll = (50 + tp.lean * 30 + tp.hype * 12 + Rng.gauss() * 5).roundToInt().coerceIn(15, 85)
        return TvSegment(lg.season, lg.day, showName(lg), tp.question, if (numbersYes) listOf(numbers, hot) else listOf(hot, numbers),
            tp.answers.map { it.first }, kind = tp.kind, pid = tp.pid, poll = poll, recap = recap(lg), quotes = tp.answers.map { it.second },
            metric = tp.metric, base = tp.base, expect = tp.expect, numbersYes = numbersYes)
    }

    private fun voices(lg: League): Pair<Media.Voice, Media.Voice> {
        val a = Media.pundit(lg)
        var b = Media.pundit(lg)
        var guard = 0
        while (b.name == a.name && guard++ < 10) b = Media.pundit(lg)
        return a to b
    }

    /** Last week's call, checked against what happened since. */
    private fun recap(lg: League): String {
        val prev = lg.tv ?: return ""
        if (prev.season != lg.season || prev.metric.isEmpty() || prev.expect == 0) return ""
        val yesName = prev.takes.getOrNull(0)?.name ?: return ""
        val noName = prev.takes.getOrNull(1)?.name ?: return ""
        val (now, n, what) = when (prev.metric) {
            "ppg" -> {
                val p = lg.playerOrNull(prev.pid) ?: return ""
                val games = p.log.filter { (runCatching { lg.game(it.gameId).day }.getOrNull() ?: 0) > prev.day }
                if (games.size < 2) return ""
                val v = games.sumOf { it.pts }.toDouble() / games.size
                Triple(v, games.size, "${p.last} has scored ${f1(v)} a night in ${games.size} games since")
            }
            "win" -> {
                val t = lg.user
                val games = lg.games.filter { it.played && it.involves(t.id) && it.day > prev.day }
                if (games.size < 2) return ""
                val w = games.count { it.winner() == t.id }
                Triple(w.toDouble() / games.size, games.size, "${if (lg.pro) "the ${t.name} are" else "${t.school} is"} $w-${games.size - w} since")
            }
            else -> return ""
        }
        if (n < 2) return ""
        val yesRight = if (prev.expect > 0) now >= prev.base else now <= prev.base
        val who = if (yesRight) yesName else noName
        return "Last week: \"${prev.topic}\" ${what.replaceFirstChar { it.uppercase() }}. Point, $who."
    }

    // ---------- what the show knows ----------

    private class Ctx(val lg: League) {
        val hero = lg.hero?.takeIf { lg.playerMode && it.teamId >= 0 }
        val t = lg.user
        val teamGames = lg.games.filter { it.played && it.involves(t.id) && (it.type == GameType.CONF || it.type == GameType.NONCONF || it.type == GameType.EVENT) }.sortedBy { it.day }
        val minGp = maxOf(5, (t.w + t.l) / 2)
        val qualified = lg.players.filter { it.teamId >= 0 && it.stats.gp >= minGp }
        fun rankBy(p: Player, pool: List<Player> = qualified, key: (Player) -> Double): Int = pool.sortedByDescending(key).indexOfFirst { it.id == p.id } + 1
        val margin = if (teamGames.isEmpty()) 0.0 else teamGames.sumOf { it.scoreFor(t.id) - it.scoreAgainst(t.id) }.toDouble() / teamGames.size
        val home = teamGames.filter { it.home == t.id && !it.neutral }
        val road = teamGames.filter { it.away == t.id && !it.neutral }
        fun winPct(gs: List<Game>) = if (gs.isEmpty()) 0.0 else gs.count { it.winner() == t.id }.toDouble() / gs.size
        val streak: Int = run {
            var s = 0
            for (g in teamGames.reversed()) {
                val w = g.winner() == t.id
                if (s == 0) s = if (w) 1 else -1 else if ((s > 0) == w) s += if (w) 1 else -1 else break
            }
            s
        }
        val totalGames = lg.games.count { it.involves(t.id) && (it.type == GameType.CONF || it.type == GameType.NONCONF || it.type == GameType.EVENT) }
        val pre: Int? = if (lg.pro) lg.proState?.preview?.takeIf { it.season == lg.season }?.proj?.get(t.id)?.w else lg.preview?.takeIf { it.season == lg.season }?.proj?.get(t.id)?.w
        val pace: Int = if (lg.pro) runCatching { NbaPreviews.latest(lg)[t.id]?.w }.getOrNull() ?: (t.winPct * totalGames).roundToInt() else (t.winPct * totalGames).roundToInt()
        /** Points for and against per game, every team, for league ranks. */
        val offense: Map<Int, Double>; val defense: Map<Int, Double>
        init {
            val pf = HashMap<Int, Int>(); val pa = HashMap<Int, Int>(); val gp = HashMap<Int, Int>()
            for (g in lg.games) if (g.played && (g.type == GameType.CONF || g.type == GameType.NONCONF || g.type == GameType.EVENT)) {
                pf.merge(g.home, g.hs, Int::plus); pa.merge(g.home, g.aws, Int::plus); gp.merge(g.home, 1, Int::plus)
                pf.merge(g.away, g.aws, Int::plus); pa.merge(g.away, g.hs, Int::plus); gp.merge(g.away, 1, Int::plus)
            }
            offense = gp.mapValues { (id, n) -> (pf[id] ?: 0).toDouble() / n }
            defense = gp.mapValues { (id, n) -> (pa[id] ?: 0).toDouble() / n }
        }
        val offRank = offense.entries.sortedByDescending { it.value }.indexOfFirst { it.key == t.id } + 1
        val defRank = defense.entries.sortedBy { it.value }.indexOfFirst { it.key == t.id } + 1
        val name = if (lg.pro) "the ${t.name}" else t.school
        val Cap = name.replaceFirstChar { it.uppercase() }
        /** A pro team is plural ("the Hawks are"), a school singular ("Michigan is"). */
        val are = if (lg.pro) "are" else "is"
        val AreQ = if (lg.pro) "Are" else "Is"
        val Do = if (lg.pro) "Do" else "Does"
        val they = if (lg.pro) "they" else "it"
        fun confLabel(id: Int) = lg.conf(id).abbr.let { if (lg.pro) (if (it.startsWith("E")) "East" else "West") else it }
        val rec get() = t.record
        fun since(days: Int) = lg.games.filter { !it.played && it.involves(t.id) && it.day in lg.day..lg.day + days }.sortedBy { it.day }
        fun lastSeason(p: Player): SeasonLine? = p.career.lastOrNull { it.season == lg.season - 1 && (!lg.pro || it.cls == PRO_YEAR) && it.s.gp >= 10 }
        fun last5(p: Player): Double? = p.log.takeLast(5).takeIf { it.size == 5 }?.let { l -> l.sumOf { it.pts } / 5.0 }
        fun salaryRank(p: Player) = lg.players.filter { it.teamId >= 0 }.sortedByDescending { Payroll.salaryOf(lg, it) }.indexOfFirst { it.id == p.id } + 1
        fun prod(p: Player) = p.stats.ppg + p.stats.rpg * 0.7 + p.stats.apg * 0.9 + (p.stats.spg + p.stats.bpg) * 1.2
    }

    private val HUMBLE = "Laugh it off"
    private val QUIET = "Let my play do the talking"
    private val BRASH = "Fire back"

    private fun answers(humble: String, quiet: String, brash: String) = listOf(HUMBLE to humble, QUIET to quiet, BRASH to brash)

    // ---------- your player ----------

    private fun heroTopics(c: Ctx, h: Player, out: MutableList<Topic>) {
        val lg = c.lg
        val r = lg.road ?: return
        val s = h.stats
        if (s.gp < c.minGp) return
        val L = h.last
        val t = c.t
        val line = "${f1(s.ppg)} points, ${f1(s.rpg)} rebounds and ${f1(s.apg)} assists"
        val ppgRank = c.rankBy(h) { it.stats.ppg }
        val prodRank = c.rankBy(h) { c.prod(it) }
        val ovrRank = c.rankBy(h) { it.ovr.toDouble() }
        val fgShooters = c.qualified.filter { it.stats.fga >= it.stats.gp * 8 }
        val fgRank = if (h in fgShooters) c.rankBy(h, fgShooters) { it.stats.fgPct } else 0
        val l5 = c.last5(h)
        val last = c.lastSeason(h)
        val best = c.qualified.filter { it.id != h.id }.maxByOrNull { c.prod(it) }
        val starting = s.gs * 2 >= s.gp
        val conf = t.confId
        val confScorers = c.qualified.filter { lg.teams.getOrNull(it.teamId)?.confId == conf }
        val confRank = c.rankBy(h, confScorers) { it.stats.ppg }
        val share = if (c.teamGames.isEmpty()) 0.0 else (s.pts.toDouble() / c.teamGames.sumOf { it.scoreFor(t.id) }.coerceAtLeast(1))
        val ppgMetric = { exp: Int, base: Double -> Triple("ppg", base, exp) }

        // Award cases.
        if (lg.pro && r.raceRank in 1..3) out += Topic("mvp", "Is $L the MVP favorite?",
            ((4 - r.raceRank) / 3.0 * 0.7 + (t.winPct - 0.55) * 1.5).coerceIn(-1.0, 1.0), 9.0,
            listOfNotNull(Fact(true, "He's No. ${r.raceRank} on the MVP ladder with $line.", 2.0),
                Fact(t.winPct >= 0.55, "${c.Cap} ${c.are} ${c.rec}.", 1.5),
                best?.let { Fact(false, "${it.name} is putting up ${f1(it.stats.ppg)}, ${f1(it.stats.rpg)} and ${f1(it.stats.apg)} on a ${lg.team(it.teamId).record} team.", 1.4) },
                Fact(true, "${ord(prodRank)} in the league in total production.", 1.2)),
            listOf("Take him off that team and see what happens.", "He's the best player on a winner. That's the definition."),
            listOf("It's a long season. Ladders in ${month(lg)} don't hand out trophies.", "MVPs carry teams in March. Show me March."),
            answers("It's nice. Winning is nicer.", "Ask me in April.", "Who else is doing what I'm doing?"), h.id, "ppg", s.ppg - 2, 1, hype = 0.3)
        if (lg.pro && r.raceRank in 4..12 && t.winPct >= 0.55) out += Topic("darkhorse", "Is $L an MVP dark horse?",
            (0.35 - (r.raceRank - 4) * 0.08).coerceIn(-1.0, 1.0), 6.5,
            listOf(Fact(true, "No. ${r.raceRank} on the ladder, and ${c.name} ${c.are} ${c.rec}.", 1.6), Fact(true, line + ".", 1.0),
                Fact(false, "There are ${r.raceRank - 1} players ahead of him, and the top three aren't slowing down.", 1.4)),
            listOf("Winning teams produce MVPs. Look at the standings.", "One big month and he's in it."),
            listOf("Dark horses stay in the dark.", "Nice season. Not an MVP season."),
            answers("I'm just trying to win games.", "Let's see where we are in March.", "Keep sleeping on me."), h.id, "ppg", s.ppg, 1)

        // Where he ranks.
        if (ppgRank in 1..12 || ovrRank in 1..12) out += Topic("top10", "Is $L a top-10 player right now?",
            (((12 - ppgRank.coerceAtMost(25)) + (12 - ovrRank.coerceAtMost(25))) / 22.0 - 0.1).coerceIn(-1.0, 1.0), 6.0,
            listOfNotNull(Fact(ppgRank <= 10, "He's ${ord(ppgRank)} in the league in scoring at ${f1(s.ppg)}.", 1.5),
                Fact(ovrRank <= 10, "He's rated ${ord(ovrRank)} in the league.", 1.3),
                if (s.fgPct < 0.43) Fact(false, "He's shooting ${pct(s.fgPct)} from the field.", 1.2) else Fact(true, "He's shooting ${pct(s.fgPct)} from the field.", 0.8)),
            listOf("Ask the coaches who have to game-plan for him.", "Watch him for a week and tell me who's better."),
            listOf("Top 10 is a club. One good stretch doesn't get you in.", "Show me in the playoffs."),
            answers("There are a lot of great players in this league.", "I'll let the season answer that.", "Name ten better. I'll wait."), h.id, "ppg", s.ppg - 1.5, 1)
        if (r.followers >= 1_000_000 && prodRank > 40) out += Topic("hype", "Is $L more hype than hoops?",
            ((prodRank - 40) / 60.0 + (r.followers / 10_000_000.0).coerceAtMost(0.4)).coerceIn(-1.0, 1.0), 6.5,
            listOf(Fact(true, "${Road.count(r.followers)} followers, and ${ord(prodRank)} in the league in production.", 1.8),
                Fact(false, line + ".", 1.0), Fact(s.fgPct < 0.44, "He's shooting ${pct(s.fgPct)}.", 1.0)),
            listOf("The highlights are great. The impact isn't there yet.", "Followers aren't wins."),
            listOf("Hate the attention all you want. He produces.", "Fame isn't his fault."),
            answers("I just play. The rest is the internet.", "The work speaks for itself.", "Keep watching."), h.id, "ppg", s.ppg, -1, hype = -0.2)

        // Streaks.
        if (l5 != null && s.gp >= 10 && l5 >= s.ppg + 5) {
            val hotMinutes = s.mpg >= 30
            out += Topic("hot", "Is ${NbaPreviews.possessive(L)} hot streak for real?", ((if (hotMinutes) 0.25 else -0.1) + (if (l5 > s.ppg * 1.6) -0.3 else 0.1)).coerceIn(-1.0, 1.0), 7.5,
                listOf(Fact(true, "${f1(l5)} a night over his last five games, up from ${f1(s.ppg)}.", 1.6),
                    Fact(hotMinutes, "He's playing ${f1(s.mpg)} minutes a night${if (hotMinutes) "; the role is there" else ", not a featured role"}.", 1.2),
                    Fact(false, "That's ${f1(l5 - s.ppg)} over his average. Streaks come back to the mean.", 1.3)),
                listOf("He's getting to his spots earlier. Watch the film.", "Confidence is real, and he's got it."),
                listOf("Five games. Everybody gets five games.", "Call me when it's twenty."),
                answers("I'm just taking what the defense gives me.", "Let's see it in twenty games.", "This is who I am. Get used to it."), h.id, "ppg", s.ppg + 2, 1)
        }
        if (l5 != null && s.gp >= 10 && l5 <= s.ppg - 4) out += Topic("slump", "Is something wrong with $L?",
            ((if (h.age >= 31) 0.25 else -0.2) + (if (l5 < s.ppg * 0.6) 0.2 else -0.1)).coerceIn(-1.0, 1.0), 7.5,
            listOf(Fact(true, "${f1(l5)} a night over his last five games.", 1.5),
                Fact(h.age >= 31, "He's ${h.age}${if (h.age >= 31) ", and legs go before shots do" else "; this isn't age"}.", 1.1),
                Fact(false, "He's still at ${f1(s.ppg)} on the season.", 1.5)),
            listOf("Teams have a book on him now.", "He's forcing it. You can see it."),
            listOf("Shooters shoot. He'll be fine.", "One bad week isn't a trend."),
            answers("Fair. I've got to be better.", "I'm putting in the work. It'll turn.", "One bad week and everybody's an expert."), h.id, "ppg", s.ppg - 2, -1)
        if (h.age >= 33 && last != null) {
            val drop = last.s.ppg - s.ppg
            out += Topic("declining", "Is $L slowing down?", (drop / 5 + (h.age - 33) * 0.1).coerceIn(-1.0, 1.0), 6.0,
                listOf(Fact(drop > 0, "${f1(s.ppg)} a night, ${if (drop > 0) "down" else "up"} from ${f1(last.s.ppg)} last season.", 1.6),
                    Fact(true, "He's ${h.age}.", 1.0), Fact(false, "He's still playing ${f1(s.mpg)} minutes a night for a ${c.rec} team.", 1.1)),
                listOf("Father Time is undefeated.", "You can see it on the defensive end first."),
                listOf("Smart players age well. He's smart.", "He's not the problem on that team."),
                answers("I'll keep playing as long as I can help.", "My legs feel great. We'll see.", "I've heard that for years."), h.id, "ppg", s.ppg, -1)
        }

        // The rival.
        Rival.player(lg)?.takeIf { it.stats.gp >= c.minGp && it.teamId >= 0 }?.let { rv ->
            val won = r.duels.count { it.won }; val lost = r.duels.size - won
            val lean = ((s.ppg - rv.stats.ppg) / 10 + (h.ovr - rv.ovr) / 15.0 + (won - lost) * 0.08).coerceIn(-1.0, 1.0)
            out += Topic("rival", "Is $L better than ${rv.name}?", lean, 6.5,
                listOfNotNull(Fact(s.ppg >= rv.stats.ppg, "$L is at ${f1(s.ppg)} a night; ${rv.last} is at ${f1(rv.stats.ppg)}.", 1.5),
                    Fact(h.ovr >= rv.ovr, "${if (h.ovr >= rv.ovr) L else rv.last} is the higher-rated player.", 1.1),
                    if (r.duels.isNotEmpty()) Fact(won >= lost, "${if (won >= lost) L else rv.last} leads the head-to-head ${maxOf(won, lost)}-${minOf(won, lost)}.", 1.4) else null),
                listOf("Head-to-head, stat line: you pick it.", "He wants it more."),
                listOf("${rv.last} does more for his team.", "Rivalries are decided in May, not November."),
                answers("${rv.last} is a great player. We push each other.", "We'll settle it on the floor.", "Check the head-to-head."), h.id, "ppg", rv.stats.ppg, 1)
            c.since(7).firstOrNull { it.involves(rv.teamId) }?.let { g ->
                val p = Rankings.winProbFor(lg, g, t.id)
                out += Topic("duel", "Does $L win this week's showdown with ${rv.last}?", ((p - 0.5) * 2 + (s.ppg - rv.stats.ppg) / 15).coerceIn(-1.0, 1.0), 8.5,
                    listOf(Fact(p >= 0.5, "Our numbers give ${c.name} a ${pct(p)} chance in that game.", 1.6),
                        Fact(s.ppg >= rv.stats.ppg, "$L ${f1(s.ppg)} a night, ${rv.last} ${f1(rv.stats.ppg)}.", 1.2),
                        Fact(lg.team(rv.teamId).winPct > t.winPct, "${rv.last}'s team is ${lg.team(rv.teamId).record}; ${c.name} ${c.are} ${c.rec}.", 1.0)),
                    listOf("Big games bring out his best.", "He's had this one circled for weeks."),
                    listOf("${rv.last} has been waiting for this one too.", "Rivalry games are coin flips."),
                    answers("It's one game. We'll be ready.", "See you there.", "He knows what's coming."), h.id, "win", 0.5, 1)
            }
        }

        // Clutch.
        val clutchRank = c.rankBy(h) { it.stats.clutch.toDouble() }
        if (clutchRank in 1..10 && s.clutch >= 12) {
            val top = c.qualified.maxBy { it.stats.clutch }
            out += Topic("clutch", "Is $L the most clutch player in the league?", (if (clutchRank == 1) 0.55 else 0.35 - clutchRank * 0.09).coerceIn(-1.0, 1.0), 6.5,
                listOfNotNull(Fact(true, "${s.clutch} clutch-time points, ${ord(clutchRank)} in the league.", 1.6),
                    if (top.id != h.id) Fact(false, "${top.name} has ${top.stats.clutch}.", 1.5) else null,
                    Fact(false, "Clutch stats are a small sample by definition.", 0.8)),
                listOf("He wants the last shot. Not everybody does.", "When it's close, the ball finds him."),
                listOf("Hit one in the playoffs and we'll talk.", "Regular-season clutch is a different sport."),
                answers("My teammates trust me. That's all it is.", "Next close game, watch.", "Give me the ball and get out of the way."), h.id, "win", t.winPct, 1)
        }

        // Money.
        if (lg.pro) {
            val sal = Payroll.salaryOf(lg, h)
            val salRank = c.salaryRank(h)
            if (salRank in 1..60 && prodRank >= salRank + 15) out += Topic("money", "Is $L worth his contract?",
                ((salRank - prodRank) / 40.0).coerceIn(-1.0, 1.0), 6.5,
                listOf(Fact(false, "${Road.dollars(sal)} a year: ${ord(salRank)} in pay, ${ord(prodRank)} in production.", 1.8),
                    Fact(t.winPct >= 0.55, "${c.Cap} ${c.are} ${c.rec} with him.", 1.1), Fact(true, "Contracts pay for what you'll be, not just what you are.", 0.6)),
                listOf("You can't measure winning in a box score.", "Stars get paid. That's the business."),
                listOf("That's star money for a role-player season.", "Do the math."),
                answers("I'll earn it. That's on me.", "I don't sign the checks.", "Check the wins, not my bank account."), h.id, "ppg", s.ppg + 1, 1)
            if (prodRank in 1..40 && salRank > prodRank + 60) out += Topic("underpaid", "Is $L the most underpaid player in the league?",
                (0.3 + (salRank - prodRank) / 200.0).coerceIn(-1.0, 1.0), 6.0,
                listOf(Fact(true, "${ord(prodRank)} in production, ${ord(salRank)} in pay at ${Road.dollars(sal)}.", 1.8), Fact(true, line + ".", 1.0)),
                listOf("Best bargain in the league, and it isn't close.", "His agent is going to have a good summer."),
                listOf("Rookie deals and bargains are everywhere. Most underpaid? Come on.", "Get paid first, then we'll talk."),
                answers("The money will come.", "I just play.", "My agent's watching this."), h.id)
            val asd = ProLeague.allStarDay(lg)
            if (lg.day in asd - 21 until asd && confRank in 1..30) {
                out += Topic("allstar", "Is $L an All-Star this year?", ((13 - confRank) / 12.0).coerceIn(-1.0, 1.0), 8.0,
                    listOf(Fact(confRank <= 12, "He's ${ord(confRank)} in the ${c.confLabel(conf)} in scoring at ${f1(s.ppg)}.", 1.7),
                        Fact(t.winPct >= 0.5, "${c.Cap} ${c.are} ${c.rec}.", 1.2), Fact(true, line + ".", 0.9)),
                    listOf("Coaches vote on impact. He has it.", "Leave him off and it's a snub."),
                    listOf("Twelve spots, and twelve better candidates.", "Popularity isn't a stat."),
                    answers("If it happens, it happens.", "I can't control the vote.", "If I'm not in, the vote's broken."), h.id)
                if (confRank <= 3) out += Topic("asstarter", "Should $L start the All-Star Game?", ((4 - confRank) / 3.0 * 0.7).coerceIn(-1.0, 1.0), 7.0,
                    listOf(Fact(true, "${ord(confRank)} in the ${c.confLabel(conf)} in scoring.", 1.6), Fact(t.winPct >= 0.55, "On a ${c.rec} team.", 1.0)),
                    listOf("Starters are the best of the best. That's him.", "The fans and the numbers agree."),
                    listOf("Starting is for legends. He's not there yet.", "Bench is still an honor."),
                    answers("Just happy to be there.", "Wherever they put me.", "Start me. Watch."), h.id)
            }
            if ((r.contract?.years ?: 2) <= 1 && h.age <= 31) out += Topic("extend", "Should ${c.name} pay $L this summer?",
                (((50 - prodRank) / 50.0) + (if (h.age <= 27) 0.2 else -0.1)).coerceIn(-1.0, 1.0), 6.0,
                listOf(Fact(prodRank <= 50, "${ord(prodRank)} in the league in production.", 1.5), Fact(h.age <= 28, "He's ${h.age}.", 1.0),
                    Fact(false, "Big money for a ${if (prodRank > 50) "role player" else "star"} limits everything else.", 0.8)),
                listOf("You don't let a player like this walk.", "Pay the man."),
                listOf("Let him test the market. Somebody will overpay.", "Cap space builds contenders."),
                answers("I love it here. We'll see.", "That's for the summer.", "They know what I'm worth."), h.id)
            if (t.winPct < 0.4 && h.age in 25..31 && s.ppg >= 17 && s.gp >= 15) out += Topic("askout", "Should $L ask for a trade?",
                ((0.42 - t.winPct) * 3 + (s.ppg - 17) / 20).coerceIn(-1.0, 1.0), 7.0,
                listOf(Fact(true, "${c.Cap} ${c.are} ${c.rec}.", 1.5), Fact(true, "He's ${h.age} with $line. His prime is now.", 1.2),
                    Fact(false, "He's under contract and the team picks high next summer.", 0.8)),
                listOf("Life's too short to waste a prime on a lottery team.", "Every great player has asked out at some point."),
                listOf("Loyalty still means something.", "Build it where you are."),
                answers("I'm committed to this team.", "No comment on that.", "Maybe they should ask themselves that."), h.id, "win", t.winPct, -1)
            val seed = ProSeason.standings(lg, conf).indexOfFirst { it.id == t.id } + 1
            if (seed == 1 && s.gp >= 25) out += Topic("titleorbust", "Is it title or bust for ${c.name}?", ((c.margin) / 10 + 0.3).coerceIn(-1.0, 1.0), 6.5,
                listOf(Fact(true, "First in the ${c.confLabel(conf)} at ${c.rec}.", 1.5), Fact(c.margin >= 5, "${sign(c.margin)} a game, ${if (c.margin >= 5) "a champion's margin" else "not a champion's margin"}.", 1.4)),
                listOf("Top seed, healthy star: anything less is a failure.", "Windows close fast."),
                listOf("A conference final is a great season.", "Nobody's title or bust in January."),
                answers("Every season's about winning it all.", "One game at a time.", "We know what we're here for."), h.id, "win", t.winPct, 1)
        }

        // Rookies, sophomores, improvement.
        val rookie = (lg.pro && h.proYears == 0) || (!lg.pro && h.year == 1)
        if (rookie) {
            val rookies = c.qualified.filter { if (lg.pro) it.proYears == 0 else it.year == 1 }
            val rr = c.rankBy(h, rookies) { it.stats.ppg }
            if (rr in 1..4) {
            val other = rookies.filter { it.id != h.id }.maxByOrNull { it.stats.ppg }
            out += Topic(if (lg.pro) "roy" else "freshman", if (lg.pro) "Is Rookie of the Year ${NbaPreviews.possessive(L)} to lose?" else "Is $L the best freshman in the country?",
                (if (rr == 1) 0.5 else 0.15 - rr * 0.1).coerceIn(-1.0, 1.0), 7.0,
                listOfNotNull(Fact(rr == 1, "He's ${ord(rr)} among ${if (lg.pro) "rookies" else "freshmen"} in scoring at ${f1(s.ppg)}.", 1.7),
                    other?.let { Fact(false, "${it.name} is at ${f1(it.stats.ppg)}.", if (it.stats.ppg > s.ppg) 1.6 else 0.7) }, Fact(true, line + ".", 1.0)),
                listOf("Poise, numbers, and a winning team.", "Find me a first-year player doing more."),
                listOf("Long season. First-year players hit a wall.", "I've got another kid I'd vote for today."),
                answers("I'm just trying to help us win.", "Long season. I'm locked in.", "It's mine."), h.id, "ppg", s.ppg, 1,
                yesBack = listOf("Name the rookie doing more for a winner."), noBack = listOf("${other?.last ?: "The other kid"} has been better. That's the race."))
            }
        }
        if (lg.pro && h.proYears == 1 && last != null && s.ppg < last.s.ppg - 2) out += Topic("sophomore", "Is $L in a sophomore slump?",
            ((last.s.ppg - s.ppg) / 6).coerceIn(-1.0, 1.0), 6.5,
            listOf(Fact(true, "${f1(s.ppg)} a night, down from ${f1(last.s.ppg)} as a rookie.", 1.7), Fact(false, "His minutes are ${f1(s.mpg)}; the role changed.", 0.9)),
            listOf("Teams scouted him over the summer. Now he has to adjust.", "The league caught up."),
            listOf("Second-year dips are normal. It's not a slump.", "He's doing more of the dirty work."),
            answers("I'm still learning.", "I'll figure it out.", "Check back in March."), h.id, "ppg", last.s.ppg, -1)
        if (last != null && s.ppg >= last.s.ppg + 5) out += Topic("mip", if (lg.pro) "Is $L the Most Improved Player?" else "Is $L the most improved player in the country?",
            ((s.ppg - last.s.ppg - 4) / 6).coerceIn(-1.0, 1.0), 6.5,
            listOf(Fact(true, "${f1(s.ppg)} a night, up from ${f1(last.s.ppg)} last season.", 1.8), Fact(s.mpg > last.s.mpg + 6, "His minutes went from ${f1(last.s.mpg)} to ${f1(s.mpg)}.", 1.0)),
            listOf("He put in the summer. You can see it.", "A different player."),
            listOf("More minutes, more points. That's not improvement, that's opportunity.", "Same player, bigger role."),
            answers("Thanks. The work's paying off.", "Still a lot to improve.", "I told you so."), h.id, "ppg", last.s.ppg + 3, 1)

        // Role.
        if (!starting && s.ppg >= 11 && s.gp >= 10) {
            val bench = c.qualified.filter { it.stats.gs * 2 < it.stats.gp }
            val br = c.rankBy(h, bench) { it.stats.ppg }
            if (lg.pro) out += Topic("sixth", "Is $L the Sixth Man of the Year?", (if (br == 1) 0.55 else 0.25 - br * 0.08).coerceIn(-1.0, 1.0), 6.0,
                listOf(Fact(br <= 2, "${ord(br)} among bench players in scoring at ${f1(s.ppg)}.", 1.7), Fact(true, "${f1(s.mpg)} minutes off the bench.", 0.9)),
                listOf("Best scorer off any bench.", "He changes games when he checks in."),
                listOf("The award goes to the guy who closes games. That's not him yet.", "Bench numbers come against bench players."),
                answers("Whatever helps the team.", "I just play my role.", "Give me the trophy."), h.id)
            val fifth = c.lg.rosterOf(t).filter { it.id != h.id }.sortedByDescending { it.stats.gs }.take(5).minByOrNull { it.ovr }
            if (fifth != null) out += Topic("start", "Should $L be starting?", ((h.ovr - fifth.ovr) / 6.0 + (s.ppg - fifth.stats.ppg) / 15).coerceIn(-1.0, 1.0), 7.0,
                listOf(Fact(h.ovr >= fifth.ovr, "He's rated ${if (h.ovr >= fifth.ovr) "higher" else "lower"} than ${fifth.name}, who starts.", 1.5),
                    Fact(s.ppg >= fifth.stats.ppg, "$L ${f1(s.ppg)} a night, ${fifth.last} ${f1(fifth.stats.ppg)}.", 1.3)),
                listOf("Put your best five on the floor. Simple.", "He's earned it."),
                listOf("Coaches see practice. We don't.", "Some guys are better off the bench."),
                answers("Coach makes those calls.", "I'll be ready either way.", "Put me in and watch."), h.id, "ppg", s.ppg, 1)
        }

        // On a bad team.
        if (t.winPct < 0.42 && s.ppg >= 15 && s.gp >= 10) {
            if (s.fgPct < 0.43) out += Topic("problem", "Is $L part of ${NbaPreviews.possessive(c.name)} problem?", ((0.45 - s.fgPct) * 10 + (share - 0.2) * 2).coerceIn(-1.0, 1.0), 6.5,
                listOf(Fact(true, "${pct(s.fgPct)} from the field on ${f1(s.fga.toDouble() / s.gp)} shots a night.", 1.7), Fact(true, "${c.Cap} ${c.are} ${c.rec}.", 1.0),
                    Fact(false, "He's their leading scorer at ${f1(s.ppg)}.", 1.3)),
                listOf("Volume without efficiency loses games.", "Somebody has to be accountable."),
                listOf("Take him away and they don't score at all.", "Blame the roster around him."),
                answers("We're all in this together. I've got to be better.", "Winning fixes everything.", "Look at my numbers."), h.id, "win", t.winPct, -1)
            else out += Topic("carrying", "Is $L carrying ${c.name}?", ((share - 0.2) * 4 + (s.fgPct - 0.45) * 5).coerceIn(-1.0, 1.0), 6.5,
                listOf(Fact(share >= 0.22, "He scores ${pct(share)} of their points.", 1.7), Fact(true, "${f1(s.ppg)} a night on ${pct(s.fgPct)} shooting.", 1.3), Fact(false, "They're ${c.rec} anyway.", 0.9)),
                listOf("Give him any help and they're a playoff team.", "One man show."),
                listOf("Carrying a team to ${c.rec} isn't carrying.", "Great players make teammates better."),
                answers("My teammates are working hard.", "We'll turn it around.", "Get me some help."), h.id, "win", t.winPct, 1)
        }
        if (s.ppg >= 18 && s.fgPct < 0.42 && s.gp >= 10) out += Topic("empty", "Are ${NbaPreviews.possessive(L)} numbers empty calories?", ((0.44 - s.fgPct) * 12 + (0.5 - t.winPct)).coerceIn(-1.0, 1.0), 6.0,
            listOf(Fact(true, "${f1(s.ppg)} a night on ${pct(s.fgPct)} shooting.", 1.7), Fact(t.winPct < 0.5, "${c.Cap} ${c.are} ${c.rec}.", 1.2), Fact(false, "Somebody has to take the tough shots.", 0.7)),
            listOf("Points aren't the same as winning.", "Look at the shot chart."),
            listOf("Somebody has to score.", "He draws the defense; everyone else gets open looks."),
            answers("I'll take better shots.", "We'll see at the end of the season.", "Count the wins."), h.id, "ppg", s.ppg, -1)
        if (fgRank in 1..8) out += Topic("efficient", "Is $L the most efficient scorer in the league?", (if (fgRank == 1) 0.5 else 0.3 - fgRank * 0.07).coerceIn(-1.0, 1.0), 5.5,
            listOf(Fact(true, "${pct(s.fgPct)} from the field, ${ord(fgRank)} among regular shooters.", 1.7), Fact(s.ppg >= 15, "On ${f1(s.ppg)} points a night.", 1.0)),
            listOf("He doesn't take bad shots. Ever.", "Efficiency wins."),
            listOf("Dunks and layups pad percentages.", "Efficiency without volume is a stat, not a skill."),
            answers("I take what's there.", "My teammates find me.", "Try to stop it."), h.id)

        // Specialties.
        val defRank = c.rankBy(h) { it.stats.spg + it.stats.bpg }
        if (defRank in 1..8) out += Topic("dpoy", if (lg.pro) "Is $L a Defensive Player of the Year candidate?" else "Is $L the best defender in the country?",
            (0.45 - defRank * 0.07).coerceIn(-1.0, 1.0), 5.5,
            listOf(Fact(true, "${f1(s.spg)} steals and ${f1(s.bpg)} blocks a night, ${ord(defRank)} in the league.", 1.7), Fact(c.defRank in 1..10, "${c.Cap} allow the ${ord(c.defRank)}-fewest points.", 1.2)),
            listOf("He changes shots he doesn't even block.", "Nobody wants to go at him."),
            listOf("Steals and blocks aren't defense. Positioning is.", "Gambling for steals isn't defending."),
            answers("Defense wins.", "I take pride in it.", "Come at me."), h.id)
        val astRank = c.rankBy(h) { it.stats.apg }
        if (astRank in 1..8) out += Topic("passer", "Is $L the best passer in the league?", (0.45 - astRank * 0.07).coerceIn(-1.0, 1.0), 5.0,
            listOf(Fact(true, "${f1(s.apg)} assists a night, ${ord(astRank)} in the league.", 1.7), Fact(false, "${f1(s.tov.toDouble() / s.gp)} turnovers a night too.", 1.0)),
            listOf("He sees plays before they happen.", "Everybody wants to play with him."),
            listOf("Assists come with the ball in your hands all game.", "High turnovers, high assists: that's usage."),
            answers("My teammates make the shots.", "I just make the right play.", "Watch the tape."), h.id)
        val posPeers = c.qualified.filter { it.pos == h.pos }
        val rebRank = c.rankBy(h, posPeers) { it.stats.rpg }
        if (rebRank in 1..3 && s.rpg >= 6) out += Topic("boards", "Is $L the best rebounder at his position?", (0.5 - rebRank * 0.15).coerceIn(-1.0, 1.0), 4.5,
            listOf(Fact(true, "${f1(s.rpg)} rebounds a night, ${ord(rebRank)} among ${h.pos.label}s.", 1.7)),
            listOf("He just wants the ball more than everybody else.", "Possessions win games."),
            listOf("Uncontested rebounds pad numbers.", "Boxing out is the job, not the stat."),
            answers("Effort is free.", "Just doing my job.", "Every rebound's mine."), h.id)
        if (s.ppg >= 15 && s.rpg >= 7 && s.apg >= 6) out += Topic("tripledouble", "Is $L a triple-double threat every night?", ((s.rpg + s.apg - 13) / 6).coerceIn(-1.0, 1.0), 6.0,
            listOf(Fact(true, line + ".", 1.8)),
            listOf("He does everything.", "Fill the stat sheet, win the game."),
            listOf("Stat-chasing isn't winning.", "Close isn't a triple-double."),
            answers("I just try to help however I can.", "Whatever the game needs.", "Count them."), h.id)

        // Playoffs.
        if (lg.pro) {
            val seed = ProSeason.standings(lg, conf).indexOfFirst { it.id == t.id } + 1
            val top2 = lg.rosterOf(t).filter { it.stats.gp >= 5 }.sortedByDescending { c.prod(it) }.take(2).map { it.id }
            if (seed in 1..8 && s.gp >= 20 && h.id in top2) out += Topic("playoffs", "Can $L lead a deep playoff run?", ((9 - seed) / 8.0 * 0.6 + (s.ppg - 18) / 30 + (c.margin / 15)).coerceIn(-1.0, 1.0), 5.5,
                listOf(Fact(seed <= 4, "${c.Cap} ${c.are} ${ord(seed)} in the ${c.confLabel(conf)}.", 1.4), Fact(s.clutch >= 15, "${s.clutch} clutch-time points.", 1.2),
                    Fact(c.margin >= 3, "A ${sign(c.margin)} margin.", 1.2)),
                listOf("Stars win in May. He's a star.", "His game travels."),
                listOf("Playoff defenses take away the first option.", "He hasn't done it yet."),
                answers("We'll find out.", "Got to get there first.", "Yes."), h.id, "win", t.winPct, 1)
        }

        // College: the draft and the awards.
        if (!lg.pro) {
            val board = runCatching { Offseason.draftBoard(lg)[h.id] }.getOrNull()
            if (board != null && board <= 30) {
                out += Topic("lottery", "Is $L a lottery pick?", ((15 - board) / 15.0).coerceIn(-1.0, 1.0), 7.0,
                    listOf(Fact(board <= 14, "No. $board on the big boards.", 1.8), Fact(true, line + ".", 1.0)),
                    listOf("NBA scouts were in the building again last night.", "Tools you can't teach."),
                    listOf("Draft boards change by the week.", "Scouts want more."),
                    answers("I'm focused on this season.", "That's for later.", "Watch me."), h.id)
                if (h.year <= 3) out += Topic("gopro", "Should $L go pro after this season?", ((20 - board) / 20.0).coerceIn(-1.0, 1.0), 6.5,
                    listOf(Fact(board <= 20, "No. $board on the big boards.", 1.8), Fact(h.year >= 2, "He's a ${h.cls.lowercase()}.", 0.9)),
                    listOf("Strike while he's projected this high.", "Get paid."),
                    listOf("Another year makes him a top-10 pick.", "College is the last fun he'll have."),
                    answers("I'm focused on my team.", "That's a decision for April.", "We'll see."), h.id)
            }
            if (r.raceRank in 1..5) out += Topic("poy", "Is $L the Player of the Year?", ((6 - r.raceRank) / 5.0 * 0.7 + (t.winPct - 0.6)).coerceIn(-1.0, 1.0), 8.5,
                listOf(Fact(true, "No. ${r.raceRank} on the Player of the Year ladder with $line.", 1.8), Fact(t.winPct >= 0.65, "${c.Cap} ${c.are} ${c.rec}.", 1.2)),
                listOf("The best player on one of the best teams.", "Nobody's doing more."),
                listOf("Ladders in ${month(lg)} don't matter.", "Wait for conference play."),
                answers("Team first.", "Still a long way to go.", "Who else?"), h.id, "ppg", s.ppg - 2, 1, hype = 0.2)
        }

        // Role players and young players.
        val shownOvr = if (lg.pro) Ratings.shownPro(h.ovr) else h.ovr
        val shownPot = if (lg.pro) Ratings.shownPro(h.pot) else h.pot
        if (s.gp >= 10 && s.mpg in 8.0..26.0) {
            val per36 = s.ppg / s.mpg * 36
            if (per36 >= 17) out += Topic("minutes", "Should $L be playing more?", ((per36 - 17) / 10 + (0.5 - t.winPct) * 0.5).coerceIn(-1.0, 1.0), 6.0,
                listOf(Fact(true, "${f1(per36)} points per 36 minutes, in ${f1(s.mpg)} minutes a night.", 1.8), Fact(s.fgPct >= 0.46, "Shooting ${pct(s.fgPct)}.", 1.1),
                    Fact(false, "Per-36 numbers against second units flatter everybody.", 1.0)),
                listOf("Production like that earns minutes.", "The coach is leaving points on the bench."),
                listOf("Bench minutes come against bench players.", "Minutes are won in practice, and we don't see practice."),
                answers("Coach knows best.", "I'll be ready when my number's called.", "Give me the minutes and watch."), h.id, "ppg", s.ppg + 1, 1,
                yesBack = listOf("Scale that up and you've got a starter."), noBack = listOf("Wait till he sees starters every night."))
        }
        if ((if (lg.pro) h.age <= 23 else h.year <= 2) && shownPot - shownOvr >= 6 && s.gp >= 8) out += Topic("future", if (lg.pro) "Is $L a future All-Star?" else "Is $L a future NBA player?",
            ((shownPot - (if (lg.pro) 84 else 80)) / 10.0 + (s.ppg - 10) / 30).coerceIn(-1.0, 1.0), 5.5,
            listOf(Fact(shownPot >= (if (lg.pro) 85 else 80), "Rated $shownOvr now, with a $shownPot ceiling.", 1.7), Fact(true, "${if (lg.pro) "${h.age} years old" else h.cls.lowercase()}, $line.", 1.0),
                Fact(false, "Ceilings are guesses. Most young players never reach theirs.", 1.0)),
            listOf("The tools are all there.", "Give him two years."),
            listOf("Potential is a word for players who haven't done it.", "Talk to me when he's done it."),
            answers("I've got a lot of work to do.", "One day at a time.", "Bet on me."), h.id, "ppg", s.ppg, 1)
        if (lg.pro && r.draftPick >= 12 && h.proYears <= 1) {
            val cls = c.qualified.filter { it.proYears == h.proYears }
            val cr = c.rankBy(h, cls) { c.prod(it) }
            if (cr in 1..6 && cr < r.draftPick / 2) out += Topic("steal", "Is $L the steal of his draft?", ((r.draftPick - cr * 3) / 25.0).coerceIn(-1.0, 1.0), 6.0,
                listOf(Fact(true, "Picked No. ${r.draftPick}; ${ord(cr)} in his class in production.", 1.8), Fact(false, "It's early. Draft classes take years to sort out.", 1.0)),
                listOf("${r.draftPick - 1} teams passed on him. Ouch.", "Scouts missed this one."),
                listOf("Steal of the draft is a three-year question.", "The guys picked ahead of him have higher ceilings."),
                answers("I'm just happy to be here.", "I remember every team that passed.", "Ask them how it feels."), h.id)
        }
        if (s.tpa >= s.gp * 2 && s.gp >= 10 && (s.tpPct >= 0.38 || s.tpPct <= 0.31)) out += Topic("three", "Is ${NbaPreviews.possessive(L)} three-point shot for real?",
            ((s.tpPct - 0.35) * 8).coerceIn(-1.0, 1.0), 5.0,
            listOfNotNull(Fact(s.tpPct >= 0.36, "${pct(s.tpPct)} from three on ${f1(s.tpa.toDouble() / s.gp)} attempts a night.", 1.8),
                last?.takeIf { it.s.tpa >= 20 }?.let { l -> Fact(s.tpPct > l.s.tpPct, "${if (s.tpPct > l.s.tpPct) "Up" else "Down"} from ${pct(l.s.tpPct)} last season.", 1.1) }),
            listOf("The form looks clean. It's sustainable.", "Defenses have to close out now."),
            listOf("Hot shooting comes back to earth.", "Make them do it for a full season."),
            answers("I put in a lot of shots this summer.", "Just shooting with confidence.", "Leave me open and find out."), h.id, "ppg", s.ppg, 1)
        if (s.fgPct >= 0.5 && s.gp >= 10 && s.fga < s.gp * 9) out += Topic("shootmore", "Should $L shoot more?", ((s.fgPct - 0.48) * 8 + (9 - s.fga.toDouble() / s.gp) / 10).coerceIn(-1.0, 1.0), 4.5,
            listOf(Fact(true, "${pct(s.fgPct)} from the field on only ${f1(s.fga.toDouble() / s.gp)} shots a night.", 1.8), Fact(false, "Good shots are good because he doesn't force them.", 1.0)),
            listOf("Efficient scorers should get more shots. Simple math.", "He's too unselfish."),
            listOf("More shots means worse shots.", "His role is to finish what others create."),
            answers("I take what the game gives me.", "My teammates find me.", "Get me the ball."), h.id, "ppg", s.ppg + 1.5, 1)
        val teamRank = lg.rosterOf(t).filter { it.stats.gp >= 5 }.sortedByDescending { c.prod(it) }.indexOfFirst { it.id == h.id } + 1
        val ahead = lg.rosterOf(t).filter { it.stats.gp >= 5 }.sortedByDescending { c.prod(it) }.firstOrNull()
        if (teamRank in 2..3 && ahead != null && ahead.id != h.id) out += Topic("teamrank", "Is $L the best player on ${c.name}?", ((c.prod(h) - c.prod(ahead)) / 8 + (h.ovr - ahead.ovr) / 10.0 + 0.2).coerceIn(-1.0, 1.0), 6.0,
            listOf(Fact(c.prod(h) >= c.prod(ahead), "$L: $line. ${ahead.last}: ${f1(ahead.stats.ppg)}, ${f1(ahead.stats.rpg)} and ${f1(ahead.stats.apg)}.", 1.7),
                Fact(h.ovr >= ahead.ovr, "${if (h.ovr >= ahead.ovr) L else ahead.last} is the higher-rated player.", 1.2)),
            listOf("Watch who closes games.", "He's the one they can't stop."),
            listOf("${ahead.last} is still the guy.", "The best player is the one the defense fears. That's ${ahead.last}."),
            answers("${ahead.last} is our leader. I'm just doing my part.", "We're better together.", "Watch the fourth quarters."), h.id, "ppg", ahead.stats.ppg, 1)
        if (r.leadership >= 55 && s.gp >= 10) out += Topic("leader", "Is $L the leader of ${c.name}?", ((r.leadership - 55) / 30.0 + (t.winPct - 0.5)).coerceIn(-1.0, 1.0), 4.5,
            listOf(Fact(true, "His teammates follow him: the locker room is his.", 1.2), Fact(t.winPct >= 0.5, "${c.Cap} ${c.are} ${c.rec}.", 1.2), Fact(h.age <= 23, "He's ${h.age}.", 1.0)),
            listOf("Watch the huddles. They look at him.", "Leaders make everyone better."),
            listOf("Leadership is a word for winning. Win first.", "It's still the veterans' team."),
            answers("Everybody leads here.", "I just try to set the example.", "Follow me."), h.id, "win", t.winPct, 1)
        if (!lg.pro && s.gp >= 8 && confRank in 1..3) out += Topic("confbest", "Is $L the best player in the ${lg.conf(conf).abbr}?", ((4 - confRank) / 3.0 * 0.6 + (t.winPct - 0.5) * 0.6).coerceIn(-1.0, 1.0), 6.5,
            listOf(Fact(true, "${ord(confRank)} in the ${c.confLabel(conf)} in scoring at ${f1(s.ppg)}.", 1.7), Fact(t.winPct >= 0.6, "${c.Cap} ${c.are} ${c.rec}.", 1.1)),
            listOf("Nobody in the league does more.", "Conference play will prove it."),
            listOf("Wait till league play really starts.", "Somebody else's team is winning more."),
            answers("Lot of good players in this league.", "We'll see in March.", "Name somebody better."), h.id, "ppg", s.ppg - 1.5, 1)

        // Calling it a career, and coming back.
        if (r.farewellSeason == lg.season) out += Topic("farewell", "Is $L retiring too early?", ((s.ppg - 12) / 12 + (prodRank.let { (60 - it) / 120.0 })).coerceIn(-1.0, 1.0), 9.0,
            listOf(Fact(s.ppg >= 14, "$line in what he says is his last season.", 1.7), Fact(h.age <= 34, "He's only ${h.age}.", 1.2), Fact(false, "He's ${h.age}; the legs don't lie.", if (h.age >= 36) 1.4 else 0.6)),
            listOf("Nobody retires putting up these numbers.", "Leaving this on the table is a waste."),
            listOf("Go out on top. That's how legends do it.", "Better a year early than a year late."),
            answers("I appreciate it. But it's time.", "I'm enjoying every night of it.", "Never say never."), h.id, "ppg", s.ppg, 1)
        if (r.comebackSeason == lg.season) out += Topic("comeback", "Was coming back the right call for $L?", ((s.ppg - 10) / 12 + (t.winPct - 0.5)).coerceIn(-1.0, 1.0), 9.0,
            listOf(Fact(s.ppg >= 12, line + ".", 1.6), Fact(t.winPct >= 0.5, "${c.Cap} ${c.are} ${c.rec} with him.", 1.2), Fact(false, "He's ${h.age}.", 0.8)),
            listOf("He's not hanging on, he's contributing.", "Look at the production, not the age."),
            listOf("He'd already said goodbye.", "Ask me in March, when the legs go."),
            answers("I just missed the game.", "I came back to win.", "I'm not done, and they know it."), h.id, "ppg", s.ppg, 1)
    }

    // ---------- the coach ----------

    private fun coachTopics(c: Ctx, out: MutableList<Topic>) {
        val lg = c.lg
        val t = c.t
        val co = lg.userCoach
        val gp = t.w + t.l
        val name = c.name; val Name = c.Cap
        val roster = lg.rosterOf(t)
        val star = roster.filter { it.stats.gp >= 3 }.maxByOrNull { it.stats.ppg }
        val teamPts = c.teamGames.sumOf { it.scoreFor(t.id) }.coerceAtLeast(1)
        val coachAns = { h: String, q: String, b: String -> answers(h, q, b) }

        // Against the preseason projection.
        if (c.pre != null) {
            val d = c.pace - c.pre
            if (d >= 5) out += Topic("surprise", "${c.AreQ} $name the surprise of the season?", ((d - 3) / 8.0 + c.margin / 15).coerceIn(-1.0, 1.0), 8.0,
                listOf(Fact(true, "Projected for ${c.pre} wins in the fall; on pace for ${c.pace} now.", 1.8), Fact(c.margin > 0, "A ${sign(c.margin)} point margin.", 1.3),
                    Fact(false, "It's ${gp} games. A lot of season left.", 0.8)),
                listOf("This isn't luck. They're better than anybody thought.", "Coach ${co.last} has them believing."),
                listOf("Hot starts cool off.", "Wait till teams have tape on them."),
                coachAns("Credit to the players. They bought in.", "We haven't done anything yet.", "Nobody believed in us. That's fine."), metric = "win", base = t.winPct - 0.05, expect = 1)
            if (d <= -5) {
                val injured = Injuries.report(lg, t).firstOrNull { it.ovr >= (star?.ovr ?: 99) - 5 }
                out += Topic("blame", "Is Coach ${co.last} to blame for ${NbaPreviews.possessive(name)} slide?", ((-d - 3) / 10.0 - (if (injured != null) 0.4 else 0.0) - (if (c.margin > 0) 0.3 else 0.0)).coerceIn(-1.0, 1.0), 8.0,
                    listOfNotNull(Fact(true, "Projected for ${c.pre} wins; on pace for ${c.pace}.", 1.8),
                        injured?.let { Fact(false, "${it.name} has been out: ${Injuries.tag(it)}.", 1.6) },
                        Fact(c.margin > 0, "${c.Cap} ${if (lg.pro) "have" else "has"} ${if (c.margin > 0) "outscored" else "been outscored by"} opponents by ${f1(abs(c.margin))} a night.", 1.2)),
                    listOf("Same roster everyone liked in October.", "That's coaching."),
                    listOf("Coaches don't miss shots.", "Give the man a healthy roster."),
                    coachAns("It's on me. I'll fix it.", "We're working. It'll turn.", "Talk to me when you've drawn up a play."), metric = "win", base = t.winPct, expect = -1)
            }
            if (d >= 6 && t.winPct >= 0.55) out += Topic("coy", "Is Coach ${co.last} the Coach of the Year?", ((d - 4) / 8.0).coerceIn(-1.0, 1.0), 7.0,
                listOf(Fact(true, "${d} wins over the preseason projection.", 1.8), Fact(true, "${c.rec}, a ${sign(c.margin)} margin.", 1.1)),
                listOf("Beating the projection by that much is coaching.", "Look at what he had to work with."),
                listOf("The players are playing. That's not coaching.", "Wait for the second half."),
                coachAns("It's a team award.", "We're focused on the next game.", "Ask the guys we beat."))
        }

        // Record, margin, contender or pretender.
        if (gp >= 10 && (t.winPct <= 0.42 || co.security < 35)) out += Topic("hotseat", "Is Coach ${co.last}'s seat getting hot?",
            ((0.5 - t.winPct) * 2 + (50 - co.security) / 60.0).coerceIn(-1.0, 1.0), 7.0,
            listOf(Fact(true, "${c.rec}${if (c.pre != null) ", against a preseason projection of ${c.pre} wins" else ""}.", 1.7), Fact(co.security < 45, "Job security at ${co.security} out of 100.", 1.4),
                Fact(false, "${star?.name ?: "Their best player"} ${star?.let { "is at ${f1(it.stats.ppg)} a night" } ?: ""}; the roster is thin.", 0.8)),
            listOf("In this business, that record gets people fired.", "Something has to change, and it starts at the top."),
            listOf("Fire him and the next guy has the same roster.", "Patience builds programs."),
            coachAns("The seat's always hot. I don't worry about it.", "I'm focused on the next game.", "Check back in a month."), metric = "win", base = t.winPct, expect = -1)
        if (gp >= 12 && t.winPct >= 0.6 && c.margin < 3) out += Topic("pretender", "${c.AreQ} $name a pretender?", ((4 - c.margin) / 6).coerceIn(-1.0, 1.0), 6.5,
            listOf(Fact(true, "${c.rec} on a ${sign(c.margin)} point margin: they're winning the close ones.", 1.8), Fact(false, "Wins are wins.", 0.8)),
            listOf("Close-game luck runs out.", "Good teams blow people out."),
            listOf("Winning close is a skill.", "The record is real."),
            coachAns("We'll take every win.", "We know we have to get better.", "Pretend we're not good, then."), metric = "win", base = t.winPct, expect = -1)
        if (gp >= 12 && t.winPct >= 0.62 && c.margin >= 5) out += Topic("contender", "${c.AreQ} $name a real title contender?", (c.margin / 10 + (t.winPct - 0.6)).coerceIn(-1.0, 1.0), 6.5,
            listOf(Fact(true, "${c.rec} with a ${sign(c.margin)} margin.", 1.8), Fact(c.offRank <= 5 || c.defRank <= 5, "${ord(c.offRank)} in scoring, ${ord(c.defRank)} in points allowed.", 1.3)),
            listOf("Elite on both ends.", "They blow people out."),
            listOf("Regular seasons don't win titles.", "Show me a playoff series."),
            coachAns("We're just trying to get better every day.", "We'll see where we are in the spring.", "Ask the teams we've beaten."), metric = "win", base = t.winPct - 0.05, expect = 1)
        if (gp >= 8 && abs(c.streak) >= 5) {
            val win = c.streak > 0
            out += Topic(if (win) "streak" else "panic", if (win) "Is ${NbaPreviews.possessive(name)} ${c.streak}-game win streak for real?" else "Is it time to panic about $name?",
                ((if (win) c.margin / 10 else -c.margin / 10) + 0.1).coerceIn(-1.0, 1.0), 7.5,
                listOf(Fact(true, "${abs(c.streak)} straight ${if (win) "wins" else "losses"}.", 1.6), Fact(if (win) c.margin > 2 else c.margin < -2, "A ${sign(c.margin)} margin on the season.", 1.2)),
                if (win) listOf("Streaks like that don't happen by accident.", "They've figured something out.") else listOf("This is how seasons fall apart.", "The locker room is quiet. Too quiet."),
                if (win) listOf("Look who they've played.", "Streaks end.") else listOf("Every team hits a skid.", "Too early to panic."),
                coachAns("One game at a time.", if (win) "We stay humble." else "We'll get out of it.", if (win) "Keep doubting us." else "Panic? We're fine."), metric = "win", base = 0.5, expect = if (win) 1 else -1)
        }
        val homeP = c.winPct(c.home); val roadP = c.winPct(c.road)
        if (c.home.size >= 5 && c.road.size >= 5 && homeP - roadP >= 0.3) out += Topic("road", "${c.AreQ} $name a different team on the road?", ((homeP - roadP - 0.2) * 2).coerceIn(-1.0, 1.0), 5.5,
            listOf(Fact(true, "${c.home.count { it.winner() == t.id }}-${c.home.count { it.winner() != t.id }} at home, ${c.road.count { it.winner() == t.id }}-${c.road.count { it.winner() != t.id }} on the road.", 1.8)),
            listOf("Young teams wilt on the road.", "They need the crowd."),
            listOf("Everybody's better at home.", "Schedules even out."),
            coachAns("We've got to travel better.", "Road wins are hard everywhere.", "Our fans are just that good."))
        if (gp >= 10 && (c.defRank in 1..3 || c.offRank in 1..3)) {
            val d = c.defRank <= c.offRank
            out += Topic("identity", if (d) "Can $name win it all with defense?" else "Is $name's offense good enough to carry them?",
                ((if (d) (4 - c.defRank) else (4 - c.offRank)) / 3.0 * 0.5 + t.winPct - 0.5).coerceIn(-1.0, 1.0), 5.0,
                listOf(Fact(true, if (d) "They allow ${f1(c.defense[t.id] ?: 0.0)} a game, ${ord(c.defRank)}-fewest in the league." else "They score ${f1(c.offense[t.id] ?: 0.0)} a game, ${ord(c.offRank)} in the league.", 1.7),
                    Fact(false, if (d) "But they score only ${f1(c.offense[t.id] ?: 0.0)}, ${ord(c.offRank)}." else "But they allow ${f1(c.defense[t.id] ?: 0.0)}, ${ord(c.defRank)}.", 1.3)),
                listOf(if (d) "Defense travels." else "You can't stop that offense.", "That's championship DNA."),
                listOf(if (d) "You have to score in the playoffs." else "Offense gets you to the playoffs; defense wins them.", "One-dimensional teams lose in the spring."),
                coachAns("We're working on the other end too.", "It's who we are.", "Try to beat it."))
        }
        if (star != null && teamPts > 0 && star.stats.pts.toDouble() / teamPts >= 0.28 && gp >= 8) {
            val sh = star.stats.pts.toDouble() / teamPts
            out += Topic("onestar", "${c.AreQ} $name too dependent on ${star.name}?", ((sh - 0.25) * 5).coerceIn(-1.0, 1.0), 6.0,
                listOf(Fact(true, "${star.last} scores ${pct(sh)} of their points.", 1.8), Fact(false, "They're ${c.rec}; it's working.", if (t.winPct >= 0.55) 1.3 else 0.5)),
                listOf("Take him away and what's left?", "One injury from a lost season."),
                listOf("Ride your best player. That's how you win.", "Stars are supposed to carry."),
                coachAns("He's our guy. Everybody contributes.", "Others will step up.", "Wouldn't you?"), star.id, "win", t.winPct, -1)
        }
        // People.
        roster.filter { it.stats.gp >= 5 }.maxByOrNull { it.ovr }?.takeIf { it.happiness < 40 }?.let { p ->
            out += Topic("unhappy", "Is ${p.name} unhappy in ${if (lg.pro) t.school else t.school}?", ((40 - p.happiness) / 25.0).coerceIn(-1.0, 1.0), 7.0,
                listOf(Fact(true, "His mood is the worst on the roster.", 1.5), Fact(p.stats.mpg < 28, "He's playing ${f1(p.stats.mpg)} minutes a night.", 1.2),
                    Fact(false, "${f1(p.stats.ppg)} a night doesn't sound unhappy.", 1.0)),
                listOf("Look at the body language.", "He wants a bigger role, and everybody knows it."),
                listOf("Every star sulks after a loss.", "There's no fire here."),
                coachAns("${p.last} and I talk every day. We're good.", "That's between us and the locker room.", "Stop making stuff up."), p.id)
        }
        roster.filter { (if (lg.pro) it.age <= 22 else it.year <= 2) && it.pot - it.ovr >= 9 && it.stats.gp >= 5 && it.stats.mpg < 14 }.maxByOrNull { it.pot }?.let { kid ->
            out += Topic("kids", "Should Coach ${co.last} play ${kid.name} more?", ((kid.pot - kid.ovr) / 20.0 + (0.5 - t.winPct) - 0.1).coerceIn(-1.0, 1.0), 3.5,
                listOf(Fact(true, "${kid.last} has one of the highest ceilings on the roster and plays ${f1(kid.stats.mpg)} minutes.", 1.6), Fact(t.winPct < 0.5, "The team is ${c.rec}.", 1.2),
                    Fact(false, "He's averaging ${f1(kid.stats.ppg)} points in the minutes he gets.", if (kid.stats.ppg < 4) 1.2 else 0.5)),
                listOf("Develop the future. What's the downside?", "Kids learn by playing."),
                listOf("The veterans ahead of him are winning their minutes.", "Throwing kids into a playoff race is how you ruin them."),
                coachAns("He'll get his chance.", "He's learning every day.", "I'll play who I want."), kid.id)
        }
        // The calendar.
        if (lg.pro && lg.day in Trades.deadlineDay(lg) - 10 until Trades.deadlineDay(lg)) out += Topic("deadline", "Should $name buy at the deadline?", ((t.winPct - 0.5) * 3).coerceIn(-1.0, 1.0), 7.5,
            listOf(Fact(t.winPct >= 0.5, "${c.rec}${if (t.winPct >= 0.55) ", in the hunt" else ", on the fringe"}.", 1.6), Fact(c.margin > 2, "A ${sign(c.margin)} margin.", 1.1)),
            listOf("Windows close. Go get a piece.", "Banners hang forever."),
            listOf("Don't mortgage the future for a first-round exit.", "Sell high and build it right."),
            coachAns("We're always looking to get better.", "No comment on rumors.", "We like our guys. Period."))
        if (gp >= 20 && t.winPct <= 0.3) out += Topic("tank", "Should $name tank?", ((0.35 - t.winPct) * 3 + (if (roster.count { it.age <= 23 } >= 5) 0.2 else -0.1)).coerceIn(-1.0, 1.0), 6.0,
            listOf(Fact(true, "${c.Cap} ${c.are} ${c.rec}.", 1.5), Fact(roster.count { it.age <= 23 } >= 5, "${roster.count { it.age <= 23 }} players 23 or younger.", 1.0), Fact(false, "Losing becomes a habit.", 0.9)),
            listOf("Get the best pick you can.", "Commit to the rebuild."),
            listOf("You never tell players to lose.", "Culture first."),
            coachAns("We play every game to win.", "We're building something.", "Nobody here is tanking."))
        c.since(7).firstOrNull { lg.rivalry(it) != null || it.label.isNotEmpty() }?.let { g ->
            val opp = lg.team(g.opp(t.id))
            val p = Rankings.winProbFor(lg, g, t.id)
            out += Topic("bigone", "${c.Do} $name win this week against ${if (lg.pro) "the ${opp.name}" else opp.school}?", ((p - 0.5) * 2).coerceIn(-1.0, 1.0), 8.0,
                listOf(Fact(p >= 0.5, "Our numbers make it ${pct(maxOf(p, 1 - p))} for ${if (p >= 0.5) name else (if (lg.pro) "the ${opp.name}" else opp.school)}.", 1.7),
                    Fact(t.winPct >= opp.winPct, "${c.rec} against ${opp.record}.", 1.2)),
                listOf("Home crowd, big night: they're ready.", "This is the game they've circled."),
                listOf("Big games are coin flips.", "The other side wants it just as bad."),
                coachAns("It's one game.", "We'll be ready.", "See you there."), metric = "win", base = 0.5, expect = 1)
        }
        CoachRivals.rivals(lg).firstOrNull { it.w + it.l >= 3 && it.l > it.w }?.let { cr ->
            val other = lg.coaches.firstOrNull { it.id == cr.coachId } ?: return@let
            out += Topic("coachrival", "Does Coach ${other.last} have Coach ${co.last}'s number?", ((cr.l - cr.w) / 4.0).coerceIn(-1.0, 1.0), 6.0,
                listOf(Fact(true, "${co.last} is ${cr.w}-${cr.l} against him.", 1.8), Fact(false, "Rivalry games are close; a bounce here or there.", 0.7)),
                listOf("Some coaches just have your number.", "He's in his head."),
                listOf("Different rosters every year.", "That record evens out."),
                coachAns("He's a great coach.", "We'll see him again.", "Next time."))
        }
        // Injuries.
        Injuries.report(lg, t).firstOrNull { it.injGames >= 5 && it.ovr >= (star?.ovr ?: 99) - 4 }?.let { p ->
            val missed = c.teamGames.filter { g -> p.log.none { it.gameId == g.id } }.takeLast(10)
            val mw = missed.count { it.winner() == t.id }
            val withoutP = if (missed.isEmpty()) t.winPct else mw.toDouble() / missed.size
            out += Topic("injury", "Can $name survive without ${p.name}?", ((withoutP - 0.45) * 2 + (if (roster.size >= 12) 0.1 else -0.1)).coerceIn(-1.0, 1.0), 8.0,
                listOfNotNull(if (missed.size >= 2) Fact(withoutP >= 0.5, "${c.Cap} ${c.are} $mw-${missed.size - mw} in the games he's missed.", 1.8) else null,
                    Fact(t.winPct >= 0.5, "${c.rec} on the season.", 1.1), Fact(false, "${p.last}: ${Injuries.tag(p)}.", 1.5)),
                listOf("Next man up.", "The depth is there."),
                listOf("You don't replace that.", "Their margin for error is gone."),
                coachAns("Somebody has to step up.", "We'll be fine.", "Write us off. Go ahead."), p.id, "win", t.winPct, 1,
                yesBack = listOf("Depth wins in January."), noBack = listOf("You can't hide a hole that size for a month."))
        }
        // Close games, minutes, the All-Star, the rookie, threes.
        val close = c.teamGames.filter { abs(it.scoreFor(t.id) - it.scoreAgainst(t.id)) <= 5 }
        if (close.size >= 6) {
            val cw = close.count { it.winner() == t.id }
            val cp = cw.toDouble() / close.size
            if (cp >= 0.7 || cp <= 0.3) out += Topic("closers", if (cp >= 0.5) "${c.AreQ} $name the best closing team in the league?" else "${c.Do} $name have a closing problem?",
                (if (cp >= 0.5) (cp - 0.55) * 3 else (0.45 - cp) * 3 - 0.2).coerceIn(-1.0, 1.0), 6.0,
                listOf(Fact(true, "$cw-${close.size - cw} in games decided by five or fewer.", 1.8), Fact(false, "Close games are close to coin flips over a season.", 1.1)),
                if (cp >= 0.5) listOf("They know who gets the ball late.", "Composure wins.") else listOf("No go-to guy in the fourth.", "The execution falls apart late."),
                listOf("That record in close games won't hold.", "Luck evens out."),
                coachAns("We work on late-game situations every day.", "We'll keep competing.", if (cp >= 0.5) "We know how to win." else "We'll close the next one."), metric = "win", base = t.winPct, expect = if (cp >= 0.5) 1 else -1)
        }
        if (star != null && star.stats.mpg >= 37 && star.stats.gp >= 10) out += Topic("starmin", "Is Coach ${co.last} running ${star.name} into the ground?", ((star.stats.mpg - 36) / 4 + (star.age - 28) / 10.0).coerceIn(-1.0, 1.0), 6.0,
            listOf(Fact(true, "${f1(star.stats.mpg)} minutes a night.", 1.8), Fact(star.age >= 30, "He's ${star.age}.", 1.2), Fact(false, "Stars play big minutes. It's the job.", 0.9)),
            listOf("Long season. He'll have nothing left in April.", "Trust the bench."),
            listOf("He wants those minutes.", "You ride your best player."),
            coachAns("We monitor it closely.", "He tells me when he needs a blow.", "He's fine. Ask him."), star.id)
        if (lg.pro && star != null) {
            val asd = ProLeague.allStarDay(lg)
            val confScorers = c.qualified.filter { lg.teams.getOrNull(it.teamId)?.confId == t.confId }
            val sr = c.rankBy(star, confScorers) { it.stats.ppg }
            if (lg.day in asd - 21 until asd && sr in 1..20) out += Topic("allstarcoach", "Is ${star.name} an All-Star?", ((13 - sr) / 12.0).coerceIn(-1.0, 1.0), 7.0,
                listOf(Fact(sr <= 12, "${ord(sr)} in the ${c.confLabel(t.confId)} in scoring at ${f1(star.stats.ppg)}.", 1.8), Fact(t.winPct >= 0.5, "On a ${c.rec} team.", 1.1)),
                listOf("He's been their best player all year.", "Leave him off and it's a snub."),
                listOf("Twelve spots, and better candidates.", "Winning teams get the spots."),
                coachAns("He deserves it, but it's not up to me.", "He's focused on winning.", "If he's not in, the vote's broken."), star.id)
            roster.filter { it.proYears == 0 && it.stats.gp >= 8 }.maxByOrNull { it.stats.ppg }?.let { rk ->
                val rookies = c.qualified.filter { it.proYears == 0 }
                val rr = c.rankBy(rk, rookies) { it.stats.ppg }
                if (rr in 1..3) out += Topic("coachroy", "Is ${rk.name} the Rookie of the Year?", (if (rr == 1) 0.5 else 0.1 - rr * 0.1).coerceIn(-1.0, 1.0), 6.0,
                    listOf(Fact(rr == 1, "${ord(rr)} among rookies in scoring at ${f1(rk.stats.ppg)}.", 1.8)),
                    listOf("Poise and production.", "He's playing like a veteran."),
                    listOf("Long season. Rookies hit a wall.", "There's another kid I'd vote for."),
                    coachAns("He's a great kid. Just keep working.", "He's learning every day.", "Give him the trophy."), rk.id)
            }
        }
        val tpa = roster.sumOf { it.stats.tpa }; val tpm = roster.sumOf { it.stats.tpm }; val fga = roster.sumOf { it.stats.fga }
        if (gp >= 10 && fga > 0 && tpa.toDouble() / fga >= 0.45) {
            val p3 = if (tpa > 0) tpm.toDouble() / tpa else 0.0
            out += Topic("threes", "${c.Do} $name live and die by the three?", ((tpa.toDouble() / fga - 0.4) * 5 + (0.35 - p3) * 4).coerceIn(-1.0, 1.0), 5.0,
                listOf(Fact(true, "${pct(tpa.toDouble() / fga)} of their shots are threes.", 1.7), Fact(p3 < 0.35, "They're making ${pct(p3)} of them.", 1.3), Fact(false, "Threes are the most efficient shot in the game.", 1.0)),
                listOf("Cold shooting night, easy loss.", "No plan B."),
                listOf("That's the modern game.", "Math says shoot threes."),
                coachAns("We take good shots.", "We'll keep shooting.", "Three is more than two."), metric = "win", base = t.winPct, expect = -1)
        }

        // College: the bubble.
        if (!lg.pro && gp >= 12) runCatching { Bracketology.board(lg) }.getOrNull()?.let { b ->
            val st = Bracketology.status(b, t.id)
            val inn = st == "Lock" || st == "Should be in" || st.startsWith("Last four") || st.contains("auto")
            if (st != "Lock") out += Topic("bubble", "Will $name make the NCAA Tournament?", (when (st) { "Should be in" -> 0.6; "Last four byes" -> 0.3; "Last four in" -> 0.1; "First four out" -> -0.1; "Next four out" -> -0.35; else -> if (inn) 0.4 else -0.7 }), 7.5,
                listOf(Fact(inn, "Bubble Watch: ${st.lowercase()}.", 1.8), Fact(t.winPct >= 0.6, "${c.Cap} ${c.are} ${c.rec}.", 1.0)),
                listOf("The résumé is there.", "They've got the quality wins."),
                listOf("The committee wants road wins.", "The bubble always pops on somebody."),
                coachAns("We control what we can.", "Win and it takes care of itself.", "We belong."), metric = "win", base = t.winPct, expect = 1)
        }
    }

    private fun sign(m: Double) = (if (m >= 0) "+" else "−") + f1(abs(m))
    private fun month(lg: League) = when { lg.day < 30 -> "November"; lg.day < 60 -> "December"; lg.day < 90 -> "January"; lg.day < 120 -> "February"; else -> "March" }

    /** The user's answer. */
    fun respond(lg: League, choice: Int) {
        val seg = lg.tv ?: return
        if (seg.answered >= 0 || choice !in seg.responses.indices) return
        seg.answered = choice
        val r = lg.road
        val quote = seg.quotes.getOrNull(choice) ?: seg.responses[choice]
        if (lg.playerMode && r != null) {
            val h = lg.hero ?: return
            when (choice) {
                0 -> { r.persona -= 1; r.followers = (r.followers * 1.01).roundToInt() }
                1 -> { r.leadership = (r.leadership + 1).coerceAtMost(100) }
                else -> { r.persona += 2; r.followers = (r.followers * 1.04).roundToInt() }
            }
            lg.news += News(lg.season, lg.day, "${h.name} answers ${seg.show}", true, -1, "tv", "\"$quote\"", seg.show, h.teamId, h.id)
        } else {
            val c = lg.userCoach
            val t = lg.user
            when (choice) {
                0 -> c.security = (c.security + 1).coerceIn(0, 100)
                1 -> lg.rosterOf(t).forEach { it.happiness = (it.happiness + 2).coerceAtMost(99) }
                else -> { lg.rosterOf(t).forEach { it.happiness = (it.happiness + 3).coerceAtMost(99) }; c.security = (c.security - 1).coerceIn(0, 100) }
            }
            lg.news += News(lg.season, lg.day, "Coach ${c.last} answers ${seg.show}", true, -1, "tv", "\"$quote\"", seg.show, t.id, -1)
        }
    }
}
