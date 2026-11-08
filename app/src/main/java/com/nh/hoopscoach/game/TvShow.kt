package com.nh.hoopscoach.game

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.roundToInt

/** One pundit's side of the debate: who, which side, his style (numbers or hot takes), and what he said (the last line answers the other guy). */
@Serializable
class TvTake(val name: String, val handle: String, val avatar: String = "", val verified: Boolean = false, val side: String, val lines: List<String>, val persona: String = "")

/**
 * This week's segment: the show, the question, the two takes, what the fans think (percent yes), a look back
 * at last week's take, the answers the user can give (and what each says), and which he gave (-1: not yet).
 */
@Serializable
class TvSegment(
    val season: Int, val day: Int, val show: String, val topic: String, val takes: List<TvTake>, val responses: List<String>,
    var answered: Int = -1,
    val kind: String = "", val pid: Int = -1, val poll: Int = -1, val recap: String = "", val quotes: List<String> = emptyList(),
)

/**
 * The weekly sports-TV debate. Once a week in season, two pundits argue about you, one with the numbers and
 * one with the hot takes. The topic comes from what's actually going on: an MVP case, a hot streak or a
 * slump, you against your rival, clutch play, whether you're worth the money, an All-Star nod, a rookie
 * race, a team that's carrying you or one you're carrying, the farewell tour, the comeback; as a coach,
 * whether the team is the surprise of the season or a disappointment, the hot seat, an unhappy star, and
 * buying or selling at the deadline. Each side makes its case with the real numbers and answers the other.
 * The fans vote, the show checks last week's take against what happened since, and you answer in your own
 * words: each answer lands differently with fans, teammates, your following and the bosses.
 */
object TvShow {
    fun showName(lg: League) = if (lg.pro) "Full Court Press" else "Hardwood Live"

    private fun f1(d: Double) = "%.1f".format(d)
    private fun ord(n: Int) = Previews.ordinal(n)

    private class Topic(val kind: String, val score: Double, val topic: String, val yes: List<String>, val no: List<String>,
                        val yesBack: String, val noBack: String, val strength: Double, val responses: List<Pair<String, String>>, val pid: Int = -1)

    fun daily(lg: League) {
        if (lg.phase != Phase.REGULAR && lg.phase != Phase.PLAYOFFS) return
        if (lg.day % 7 != 4) return
        val t = lg.user
        if (t.w + t.l < 5) return
        val recap = recap(lg)
        val cands = (if (lg.playerMode) heroTopics(lg) else coachTopics(lg)).toMutableList()
        val recent = lg.tvKinds.takeLast(3).toSet()
        val pick = cands.filter { it.kind !in recent }.maxByOrNull { it.score } ?: cands.maxByOrNull { it.score } ?: return
        val (a, b) = voices(lg)
        val poll = (50 + (pick.strength - 0.5) * 60 + Rng.gauss() * 6).roundToInt().coerceIn(12, 88)
        val seg = TvSegment(lg.season, lg.day, showName(lg), pick.topic,
            listOf(TvTake(a.name, a.handle, a.avatar, a.verified, "Yes", pick.yes + pick.yesBack, "The numbers"),
                TvTake(b.name, b.handle, b.avatar, b.verified, "No", pick.no + pick.noBack, "Hot take")),
            pick.responses.map { it.first }, kind = pick.kind, pid = pick.pid, poll = poll, recap = recap, quotes = pick.responses.map { it.second })
        lg.tv = seg
        lg.tvKinds += pick.kind
        if (lg.tvKinds.size > 12) lg.tvKinds.removeAt(0)
        lg.news += News(lg.season, lg.day, "${seg.show}: ${seg.topic}", true, -1, "tv",
            (if (recap.isNotEmpty()) "$recap " else "") + "${a.name}: \"${pick.yes.first()}\" ${b.name}: \"${pick.no.first()}\" The fans: $poll% say yes.",
            seg.show, t.id, if (lg.playerMode) lg.heroId else -1)
    }

    private fun voices(lg: League): Pair<Media.Voice, Media.Voice> {
        val a = Media.pundit(lg)
        var b = Media.pundit(lg)
        var guard = 0
        while (b.name == a.name && guard++ < 10) b = Media.pundit(lg)
        return a to b
    }

    // ---------- the numbers ----------

    /** His scoring since day [day], or null if he hasn't played since. */
    private fun since(lg: League, p: Player, day: Int): Pair<Double, Int>? {
        val games = p.log.filter { (runCatching { lg.game(it.gameId).day }.getOrNull() ?: 0) > day }
        if (games.isEmpty()) return null
        return games.sumOf { it.pts }.toDouble() / games.size to games.size
    }

    private fun lastN(p: Player, n: Int): Double? = p.log.takeLast(n).takeIf { it.size == n }?.let { l -> l.sumOf { it.pts }.toDouble() / n }

    /** Last week's take, checked against what happened since. */
    private fun recap(lg: League): String {
        val prev = lg.tv ?: return ""
        if (prev.season != lg.season) return ""
        val no = prev.takes.getOrNull(1)?.name ?: return ""
        val yes = prev.takes.getOrNull(0)?.name ?: return ""
        if (prev.pid >= 0) {
            val p = lg.playerOrNull(prev.pid) ?: return ""
            val (ppg, gp) = since(lg, p, prev.day) ?: return ""
            if (gp < 2) return ""
            val season = p.stats.ppg
            return when {
                ppg >= season + 3 -> "Last week $no doubted ${p.last}. He's scored ${f1(ppg)} a night since. Point, $yes."
                ppg <= season - 3 -> "Last week $yes went to bat for ${p.last}. He's scored ${f1(ppg)} a night since. Point, $no."
                else -> "Last week's question about ${p.last} is still open: ${f1(ppg)} a night since."
            }
        }
        val t = lg.user
        val games = lg.games.filter { it.played && it.involves(t.id) && it.day > prev.day }
        if (games.size < 2) return ""
        val w = games.count { it.winner() == t.id }
        val name = if (lg.pro) "the ${t.name}" else t.school
        return when {
            w * 3 >= games.size * 2 -> "Since last week's show, $name are $w-${games.size - w}. Point, $yes."
            w * 3 <= games.size -> "Since last week's show, $name are $w-${games.size - w}. Point, $no."
            else -> "Since last week's show, $name are $w-${games.size - w}. The jury's still out."
        }
    }

    private val HUMBLE = "Laugh it off"
    private val QUIET = "Let my play do the talking"
    private val BRASH = "Fire back"

    private fun heroTopics(lg: League): List<Topic> {
        val h = lg.hero ?: return emptyList()
        val r = lg.road ?: return emptyList()
        val t = lg.team(h.teamId)
        val s = h.stats
        if (s.gp < 5) return emptyList()
        val out = mutableListOf<Topic>()
        val line = "${f1(s.ppg)} points, ${f1(s.rpg)} rebounds and ${f1(s.apg)} assists"
        val team = if (lg.pro) "the ${t.name}" else t.school
        val rec = t.record
        val scorers = lg.players.filter { it.teamId >= 0 && it.stats.gp >= 8 }.sortedByDescending { it.stats.ppg }
        val rank = scorers.indexOfFirst { it.id == h.id } + 1
        val l5 = lastN(h, 5) ?: s.ppg
        val best = scorers.firstOrNull { it.id != h.id }
        val resp = listOf(HUMBLE to "I saw it. I laughed. We've got a game tomorrow.", QUIET to "They get paid to talk. I get paid to hoop.", BRASH to "Tell them to come guard me.")

        if (lg.pro && r.raceRank in 1..5) out += Topic("mvp", 9.0 + (6 - r.raceRank), "Is ${h.last} the MVP right now?",
            listOf("No. ${r.raceRank} on the MVP ladder: $line on a $rec team. The case makes itself.", "Take him off $team and tell me what they look like."),
            listOf(best?.let { "${it.name} is at ${f1(it.stats.ppg)} a night. Let's not crown anybody in ${monthWord(lg)}." } ?: "Let's not crown anybody yet.", "MVPs win. $rec is good, not great."),
            "You want narrative; I've got ${f1(s.ppg)} a game and a winning record.", "Spreadsheets don't win trophies. Talk to me in April.",
            (6 - r.raceRank) / 5.0, listOf(HUMBLE to "MVP talk is nice. Wins are nicer.", QUIET to "Ask me in April.", BRASH to "Who else is doing what I'm doing?"), h.id)

        if (l5 >= s.ppg + 5) out += Topic("hot", 8.0, "Is ${h.last}'s hot streak for real?",
            listOf("${f1(l5)} a night over his last five, up from ${f1(s.ppg)} on the season. That's not luck, that's a leap.", "He's getting to his spots earlier. Watch the film."),
            listOf("Five games. Everybody gets five games.", "Call me when it's twenty."),
            "Ignore it if you want; defenses can't.", "Regression is undefeated.",
            0.65, listOf(HUMBLE to "I'm just taking what the defense gives me.", QUIET to "Let's see it in twenty games, then.", BRASH to "This is who I am. Get used to it."), h.id)

        if (l5 <= s.ppg - 4) out += Topic("slump", 8.0, "What's wrong with ${h.last}?",
            listOf("Nothing a few makes won't fix. ${f1(s.ppg)} on the season; ${f1(l5)} is a dip, not a decline.", "The looks are still there."),
            listOf("${f1(l5)} over his last five. Teams have a book on him now.", "He's forcing it. You can see it."),
            "Shooters shoot. He'll be fine.", "This is what happens when the league catches up.",
            0.45, listOf(HUMBLE to "Fair. I've got to be better.", QUIET to "I'm putting in the work. It'll turn.", BRASH to "One bad week and everybody's an expert."), h.id)

        Rival.player(lg)?.takeIf { it.stats.gp >= 5 && it.teamId >= 0 }?.let { rv ->
            val won = r.duels.count { it.won }
            val lost = r.duels.size - won
            out += Topic("rival", 6.5, "Who's better right now: ${h.last} or ${rv.last}?",
                listOf("${h.last}: ${f1(s.ppg)} a night. ${rv.last}: ${f1(rv.stats.ppg)}. " + (if (r.duels.isNotEmpty()) "And ${h.last} leads the head-to-head $won-$lost." else ""), "It's ${h.last}, and it's not close."),
                listOf("${rv.last} does more on a better ${if (lg.team(rv.teamId).winPct > t.winPct) "team" else "night"}.", "Rivalries are decided in May, not November."),
                "Head-to-head, stat line, you pick it.", "Ask me which one I'd want with the season on the line.",
                (s.ppg / (s.ppg + rv.stats.ppg).coerceAtLeast(1.0)),
                listOf(HUMBLE to "${rv.last} is a great player. We push each other.", QUIET to "We'll settle it on the floor.", BRASH to "Check the head-to-head."), h.id)
        }

        val clutchRank = lg.players.filter { it.teamId >= 0 && it.stats.gp >= 8 }.sortedByDescending { it.stats.clutch }.indexOfFirst { it.id == h.id } + 1
        if (clutchRank in 1..8 && s.clutch >= 15) out += Topic("clutch", 6.5, "Is ${h.last} the most clutch player in the league?",
            listOf("${s.clutch} points in clutch time, ${ord(clutchRank)} in the league. When it's close, the ball finds him.", "He wants the last shot. Not everybody does."),
            listOf("Clutch is a small sample dressed up as a skill.", "Hit one in the playoffs and we'll talk."),
            "Small sample? It's ${s.clutch} points.", "Regular-season clutch is a different sport.",
            (9 - clutchRank) / 8.0, listOf(HUMBLE to "My teammates trust me. That's all it is.", QUIET to "Next close game, watch.", BRASH to "Give me the ball and get out of the way."), h.id)

        if (lg.pro) {
            val sal = Payroll.salaryOf(lg, h)
            val salRank = lg.players.filter { it.teamId >= 0 }.sortedByDescending { Payroll.salaryOf(lg, it) }.indexOfFirst { it.id == h.id } + 1
            if (salRank in 1..60 && rank > salRank + 25) out += Topic("money", 6.5, "Is ${h.last} worth the money?",
                listOf("${Road.dollars(sal)} a year buys more than points: his team is $rec.", "Contracts pay for what you'll be, not just what you are."),
                listOf("${Road.dollars(sal)}, ${ord(salRank)} in the league in pay and ${ord(rank)} in scoring. Do the math.", "That's a max-money price for a role-player season."),
                "You can't measure winning in a box score.", "The box score is where I'd start.",
                0.35, listOf(HUMBLE to "I'll earn it. That's on me.", QUIET to "I don't sign the checks.", BRASH to "Check the wins, not my bank account."), h.id)
            val asd = ProLeague.allStarDay(lg)
            if (lg.day in asd - 21 until asd) {
                val confRank = scorers.filter { lg.team(it.teamId).confId == t.confId }.indexOfFirst { it.id == h.id } + 1
                out += Topic("allstar", 7.5, "All-Star or snub: ${h.last}?",
                    listOf("${ord(confRank)} in the ${lg.conf(t.confId).abbr} in scoring at ${f1(s.ppg)}. He's in.", "Coaches vote on impact. He has it."),
                    listOf("There are twelve spots and at least twelve better candidates.", "Popularity isn't a stat."),
                    "Name the twelve. I'll wait.", "Easy: the guys on winning teams.",
                    ((25 - confRank) / 24.0).coerceIn(0.05, 0.95), listOf(HUMBLE to "If it happens, it happens.", QUIET to "I can't control the vote.", BRASH to "If I'm not in, the vote's broken."), h.id)
            }
        }

        if (rank in 1..10) out += Topic("top10", 5.0, "Is ${h.last} a top-10 player right now?",
            listOf("$line. ${ord(rank)} in the league in scoring.", "Ask the coaches who game-plan for him."),
            listOf("Top 10 is a club. One good stretch doesn't get you in.", "Show me in the playoffs."), "Numbers don't lie.", "Neither do rings.",
            (11 - rank) / 10.0, resp, h.id)
        if (t.winPct < 0.42 && s.ppg >= 15) out += Topic("problem", 6.0, "Is ${h.last} the problem for $team?",
            listOf("The only reason they're not worse. $line.", "Get him some help."),
            listOf("Numbers on a losing team. $rec. Somebody has to be accountable.", "Empty calories."),
            "Blame the roster, not the one guy producing.", "Stars make teammates better. Where's that?",
            0.5, listOf(HUMBLE to "We're all in this together. I've got to be better too.", QUIET to "Winning fixes everything.", BRASH to "Look at my numbers and tell me I'm the problem."), h.id)
        if ((lg.pro && h.proYears == 0) || (!lg.pro && h.year == 1)) out += Topic("roy", 7.0, "Is Rookie of the Year ${h.last}'s to lose?",
            listOf("$line as a rookie. It's his trophy to lose.", "Poise, numbers, and $team is $rec."),
            listOf("Long season. Rookies hit the wall.", "I've got two other kids I'd vote for today."),
            "Find me a rookie doing more.", "Talk to me after the All-Star break.",
            0.6, listOf(HUMBLE to "I'm just trying to help us win.", QUIET to "Long season. I'm locked in.", BRASH to "It's mine."), h.id)
        if (r.farewellSeason == lg.season) out += Topic("farewell", 9.5, "Is ${h.last} retiring too early?",
            listOf("$line in what he says is his last season. He's still got it.", "Nobody retires putting up these numbers."),
            listOf("Go out on top. That's how legends do it.", "Better a year early than a year late."),
            "Leaving this on the table is a waste.", "Respect the decision.",
            ((s.ppg - 8) / 20.0).coerceIn(0.1, 0.9), listOf(HUMBLE to "I appreciate it. But it's time.", QUIET to "I'm enjoying every night of it.", BRASH to "Never say never."), h.id)
        if (r.comebackSeason == lg.season) out += Topic("comeback", 9.5, "Was coming back the right call for ${h.last}?",
            listOf("$line. He's not hanging on, he's contributing.", "$team is $rec with him."),
            listOf("Comebacks rarely end the way people want.", "He'd already said goodbye. Should've meant it."),
            "Look at the production, not the age.", "Ask me in March, when the legs go.",
            ((s.ppg - 6) / 18.0).coerceIn(0.1, 0.9), listOf(HUMBLE to "I just missed the game.", QUIET to "I came back to win. That's it.", BRASH to "I'm not done, and they know it."), h.id)
        out += Topic("overrated", 3.0, "Is ${h.last} overrated?",
            listOf("$line. Overrated? Come on.", "People just like to hate the guy with the hype."),
            listOf("The highlights are great. The impact isn't there yet.", "Followers aren't wins."),
            "Watch a full game, not the clips.", "I watch every game. That's the problem.",
            0.6, resp, h.id)
        return out
    }

    private fun monthWord(lg: League) = when { lg.day < 30 -> "November"; lg.day < 60 -> "December"; lg.day < 90 -> "January"; lg.day < 120 -> "February"; else -> "March" }

    private fun coachTopics(lg: League): List<Topic> {
        val t = lg.user
        val c = lg.userCoach
        val team = if (lg.pro) "the ${t.name}" else t.school
        val Team = team.replaceFirstChar { it.uppercase() }
        val rec = t.record
        val star = lg.rosterOf(t).maxByOrNull { it.stats.ppg }
        val starLine = star?.let { "${it.name} at ${f1(it.stats.ppg)} a night" } ?: "their best player"
        val out = mutableListOf<Topic>()
        val resp = listOf(HUMBLE to "I don't watch that stuff. Somebody told me. It's fine.", QUIET to "Our guys will answer it on the floor.", BRASH to "They've never coached a game in their lives.")
        // Against the preseason projection.
        val pre: Int?; val now: Int?
        if (lg.pro) { pre = lg.proState?.preview?.takeIf { it.season == lg.season }?.proj?.get(t.id)?.w; now = runCatching { NbaPreviews.latest(lg)[t.id]?.w }.getOrNull() }
        else { pre = lg.preview?.takeIf { it.season == lg.season }?.proj?.get(t.id)?.w; now = null }
        val gp = t.w + t.l
        val pace = if (now != null) now else if (gp > 0 && pre != null) (t.winPct * (lg.games.count { it.involves(t.id) && (it.type == GameType.CONF || it.type == GameType.NONCONF) })).roundToInt() else null
        if (pre != null && pace != null && abs(pace - pre) >= 5) {
            val up = pace > pre
            out += if (up) Topic("surprise", 8.5, "Are $team the surprise of the season?",
                listOf("Projected for $pre wins in October. On pace for $pace now. That's coaching.", "Coach ${c.last} has them playing harder than anybody."),
                listOf("Hot starts cool off. $rec today, .500 by March.", "Wait till teams have tape on them."),
                "${pace - pre} wins over the projection doesn't happen by accident.", "Projections were wrong in October; they'll be wrong again.",
                0.65, listOf(HUMBLE to "Credit to the players. They bought in.", QUIET to "We haven't done anything yet.", BRASH to "Nobody believed in us. That's fine."))
            else Topic("blame", 8.5, "Who's to blame for $team?",
                listOf("Projected for $pre wins, on pace for $pace. That's on the coach.", "The roster's the same one everyone liked in October."),
                listOf("Injuries and bad luck. Give the man a healthy roster.", "$starLine. The effort's there."),
                "Same roster, worse results: that's coaching.", "Coaches don't miss shots.",
                0.6, listOf(HUMBLE to "It's on me. I'll fix it.", QUIET to "We're working. It'll turn.", BRASH to "Talk to me when you've drawn up a play."))
        }
        if (t.winPct <= 0.4) out += Topic("hotseat", 7.0, "Is Coach ${c.last}'s seat getting hot?",
            listOf("$rec. In this business, that gets people fired.", "Something has to change, and it starts at the top."),
            listOf("Give the man a roster first.", "He's got $starLine and not much else."),
            "The record is the record.", "Fire him and the next guy has the same roster.",
            0.6, listOf(HUMBLE to "The seat's always hot. I don't worry about it.", QUIET to "I'm focused on Thursday.", BRASH to "Check back in a month."))
        if (t.winPct >= 0.65) out += Topic("real", 6.0, "Are $team for real?",
            listOf("$rec. That's not luck, that's a team.", "Coach ${c.last} has them defending every night."),
            listOf("Wait till the schedule gets hard.", "Ride $starLine only so far."),
            "Good teams don't need a soft schedule.", "Good teams beat good teams. Show me.",
            0.7, listOf(HUMBLE to "We're just trying to get better every day.", QUIET to "We'll see where we are in March.", BRASH to "Ask the teams we've beaten."))
        lg.rosterOf(t).filter { it.stats.gp >= 5 }.maxByOrNull { it.ovr }?.takeIf { it.happiness < 40 }?.let { p ->
            out += Topic("unhappy", 7.0, "Is ${p.name} unhappy in $team?",
                listOf("Look at the body language. Something's off.", "${p.last} wants a bigger role, and everybody knows it."),
                listOf("Every star sulks after a loss. It's November.", "${f1(p.stats.ppg)} a night doesn't sound unhappy."),
                "Where there's smoke...", "There's no fire here.",
                0.55, listOf(HUMBLE to "${p.last} and I talk every day. We're good.", QUIET to "That's between us and the locker room.", BRASH to "Stop making stuff up."), p.id)
        }
        if (lg.pro && lg.day in Trades.deadlineDay(lg) - 10 until Trades.deadlineDay(lg)) out += Topic("deadline", 7.5, "Buyers or sellers: $team?",
            listOf("$rec. Go get a piece. Windows close.", "One more shooter changes their ceiling."),
            listOf("Don't mortgage the future for a first-round exit.", "Sell high and build it right."),
            "Banners hang forever.", "So do bad contracts.",
            t.winPct.coerceIn(0.2, 0.8), listOf(HUMBLE to "We're always looking to get better.", QUIET to "No comment on rumors.", BRASH to "We like our guys. Period."))
        out += Topic("run", 3.0, "Can $team make a run?",
            listOf("$rec and trending up. Don't sleep on them.", "Coach ${c.last} always has a plan for the big games."),
            listOf("They're fine. Fine doesn't win anything.", "No closer. Who takes the last shot?"),
            "Ask the teams they've beaten.", "Ask the teams they've lost to.",
            t.winPct.coerceIn(0.2, 0.8), resp)
        return out
    }

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
