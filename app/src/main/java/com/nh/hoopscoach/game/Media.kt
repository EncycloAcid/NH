package com.nh.hoopscoach.game

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The media around the user's player: reporters with names and outlets, short articles behind the
 * big headlines, columns on the award race, and a social feed of the team account, fans, doubters,
 * analysts, highlight accounts, teammates and the rival.
 */
object Media {
    private val OUTLETS = listOf("Hoops Wire", "The Daily Dribble", "Courtside Report", "Baseline Sports", "The Rim", "Fast Break Network", "Above the Break")
    private val ANALYSTS = listOf(
        "Harlan Price" to "HoopsHarlan", "Marcus Webb" to "TheFilmRoomMW", "Sam Ortiz" to "StatSheetSam", "Jenna Holt" to "PaintTouches",
        "Ben Carver" to "BoxScoreBen", "Jay Okafor" to "ThirtyFootJay", "Nia Brooks" to "NiaOnTheBeat",
    )
    private val HIGHLIGHTS = listOf("Hoops Highlights" to "HoopsHighlights", "Ballislife Clips" to "BallClips", "Court Vision" to "CourtVisionHQ")
    private val DAYS = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

    /** A reporter and outlet for a question: a national outlet, or the local beat writer. */
    fun reporter(lg: League): String {
        val h = lg.hero ?: return ""
        val t = if (h.teamId >= 0) lg.team(h.teamId) else null
        val name = "${Rng.pick(Names.first)} ${Rng.pick(Names.last)}"
        val outlet = if (t != null && Rng.chance(0.4)) (if (lg.pro) "${t.school} Herald" else "The ${t.school} Daily") else Rng.pick(OUTLETS)
        return "$name · $outlet"
    }

    fun post(lg: League, name: String, handle: String, kind: String, text: String, likes: Int, verified: Boolean = false, gameId: Int = -1) {
        val r = lg.road ?: return
        // Real accounts where the pack has one: the team's own, highlight and media accounts, analysts and insiders, the league.
        val acct = if (name == "NBA") SocialAccounts.league(lg) else when (kind) {
            "team" -> SocialAccounts.forTeam(lg, name)
            "media", "analyst", "insider" -> SocialAccounts.pick(lg, kind)
            else -> null
        }
        r.feed += Post(lg.season, lg.day, acct?.name ?: name, acct?.handle ?: handle, kind, text, max(likes, 3), acct?.verified ?: verified, gameId, acct?.avatar ?: "")
        if (r.feed.size > 80) r.feed.subList(0, r.feed.size - 80).clear()
    }

    /** Likes as a share of his following, with some luck. */
    fun likes(lg: League, share: Double): Int {
        val f = max(lg.road?.followers ?: 0, 2_000).toDouble()
        return (f * share * (0.5 + Rng.d())).roundToInt()
    }

    private fun fanHandle(t: Team) = Rng.pick(listOf("${t.mascot.replace(" ", "")}Nation", "${t.abbr}Faithful", "${t.school.replace(" ", "")}Hoops", "Die-hard${t.mascot.replace(" ", "")}", "${t.abbr}TilIDie"))
    private fun fanName() = Rng.pick(listOf("Big ${Rng.pick(Names.first)}", "${Rng.pick(Names.first)} ${Rng.pick(Names.last).first()}.", "Courtside ${Rng.pick(Names.first)}", "${Rng.pick(Names.first)} from section 112"))
    private fun hater() = Rng.pick(listOf("Hot Takes Only" to "hottakes_only", "Overrated Watch" to "overratedwatch", "Real Hoops Fan" to "realhoopsfan_", "Bench Mob" to "benchmob99"))

    /** His box line for a game, if the box score was kept. */
    private fun boxLine(g: Game, pid: Int): StatLine? = g.box?.let { b -> (b.home + b.away).firstOrNull { it.pid == pid }?.s }

    // ---------- after a game ----------

    fun afterGame(lg: League, g: Game, line: GameLogEntry, won: Boolean, winner: Boolean, stakes: String, duel: Duel?) {
        val h = lg.hero ?: return
        val r = lg.road ?: return
        val t = lg.team(h.teamId)
        val opp = lg.team(g.opp(t.id))
        val us = g.scoreFor(t.id)
        val them = g.scoreAgainst(t.id)
        val box = boxLine(g, h.id)
        val shots = box?.let { " on ${it.fgm}-of-${it.fga} shooting" } ?: ""
        val doubleDouble = listOf(line.pts, line.reb, line.ast).count { it >= 10 }
        val big = line.pts >= 28 || doubleDouble >= 2 || winner
        val bad = line.min >= 20 && line.pts <= 6
        val careerHigh = line.pts >= 15 && h.log.dropLast(1).all { it.pts < line.pts } && h.log.size >= 5

        // The team account always posts the final.
        post(lg, "${t.school} ${t.mascot}", "${t.abbr}${if (lg.pro) "" else "Hoops"}", "team",
            "FINAL: ${t.mascot} $us, ${opp.mascot} $them. ${h.last}: ${line.pts} PTS, ${line.reb} REB, ${line.ast} AST" + if (stakes.isNotEmpty()) ". $stakes." else ".",
            likes(lg, 0.004), verified = true, gameId = g.id)

        if (winner) {
            val (n, hd) = Rng.pick(HIGHLIGHTS)
            post(lg, n, hd, "media", "${h.name.uppercase()} WITH THE GAME-WINNER. ${t.mascot} ${us}, ${opp.mascot} ${them}.", likes(lg, 0.06), verified = true, gameId = g.id)
        }
        if (big) {
            post(lg, fanName(), fanHandle(t), "fan", Rng.pick(listOf(
                "${h.last} is HIM. ${line.pts} tonight and it never felt close.",
                "Put some respect on ${h.first}'s name. ${line.pts}-${line.reb}-${line.ast}.",
                "Every time I doubt ${h.last} he does THIS.",
                "We have a star. ${h.last} with ${line.pts}. Say it with me.",
            )), likes(lg, 0.012))
            val (an, ah) = Rng.pick(ANALYSTS)
            val ppg = h.stats.ppg
            post(lg, an, ah, "analyst", Rng.pick(listOf(
                "${h.last} tonight: ${line.pts} points$shots. He's at ${"%.1f".format(ppg)} a game this year. The jump is real.",
                "Watched the tape on ${h.last}. The footwork on his drives is ahead of his age. ${line.pts} points was not a fluke.",
                "${h.last} ${line.pts}/${line.reb}/${line.ast} against ${opp.school}. Teams are going to have to game-plan for him now.",
            )), likes(lg, 0.006), verified = true)
        }
        if (careerHigh) {
            post(lg, "${t.school} ${t.mascot}", "${t.abbr}${if (lg.pro) "" else "Hoops"}", "team", "Career high for ${h.first} ${h.last}: ${line.pts} points.", likes(lg, 0.008), verified = true)
        }
        if (bad) {
            val (hn, hh) = hater()
            post(lg, hn, hh, "hater", Rng.pick(listOf(
                "${h.last} disappeared tonight. ${line.pts} points in ${line.min} minutes. Overrated.",
                "Where was ${h.last}? Asking for the whole ${t.school} fan base.",
                "${h.last} hype train just derailed. ${line.pts} points.",
            )), likes(lg, 0.004))
            post(lg, fanName(), fanHandle(t), "fan", "Bad night for ${h.last}. Everybody has them. He bounces back. Always does.", likes(lg, 0.002))
        } else if (!won && !big && Rng.chance(0.5)) {
            post(lg, fanName(), fanHandle(t), "fan", Rng.pick(listOf(
                "Tough one. ${opp.mascot} wanted it more tonight.",
                if (them - us > 15) "That was ugly. Moving on." else "Lost a close one. Get it back next time.",
                "Need more of ${h.last} in the fourth quarter. Just saying.",
            )), likes(lg, 0.002))
        }
        // A brash persona draws doubters; a humble one draws love.
        if (r.persona >= 4 && Rng.chance(0.25)) {
            val (hn, hh) = hater()
            post(lg, hn, hh, "hater", "All that talk from ${h.last} and ${if (won) "they barely won" else "they lost"}. Let your game do the talking.", likes(lg, 0.003))
        } else if (r.persona <= -4 && won && Rng.chance(0.4)) {
            post(lg, fanName(), fanHandle(t), "fan", "Love how ${h.last} carries himself. Never about him. Always the team.", likes(lg, 0.003))
        }
        if (won && Rng.chance(if (big) 0.6 else 0.2)) {
            lg.rosterOf(t).filter { it.id != h.id }.maxByOrNull { boxLine(g, it.id)?.pts ?: 0 }?.let { mate ->
                post(lg, mate.name, mate.name.replace(" ", "").replace(".", "").lowercase(), "teammate", Rng.pick(listOf(
                    "My brother ${h.last} different. Proud of this group.",
                    "Locked in. W. On to the next.",
                    "${h.first} cooking. Just get him the ball.",
                )), likes(lg, 0.005), verified = lg.pro)
            }
        }
        if (duel != null) Rival.player(lg)?.let { rv ->
            post(lg, rv.name, rv.name.replace(" ", "").lowercase(), "rival", if (duel.won) Rng.pick(listOf(
                "Good battle tonight. See you next time.",
                "Respect to ${h.last}. Next one's mine.",
            )) else Rng.pick(listOf(
                "Told y'all. ${duel.him} to ${duel.me}.",
                "Same result every time. ${duel.him}-${duel.me}.",
            )), likes(lg, 0.01), verified = true)
        }
        if (big || winner || stakes.isNotEmpty() || lg.rivalry(g) != null) article(lg, g, line, won, winner, stakes, duel)
    }

    /** A short game story: the lede, his line, the context, a quote from the coach and who else stood out. */
    private fun article(lg: League, g: Game, line: GameLogEntry, won: Boolean, winner: Boolean, stakes: String, duel: Duel?) {
        val h = lg.hero ?: return
        val t = lg.team(h.teamId)
        val opp = lg.team(g.opp(t.id))
        val us = g.scoreFor(t.id)
        val them = g.scoreAgainst(t.id)
        val box = boxLine(g, h.id)
        val coach = lg.coachOf(t)
        val home = g.home == t.id
        val dateline = (if (home) t.school else opp.school).uppercase()
        val day = DAYS[(g.day + lg.season) % 7]
        val headline = when {
            winner -> "${h.last}'s buzzer-beater lifts ${t.mascot} past ${opp.mascot}"
            stakes.isNotEmpty() && won -> "${stakes}: ${h.last} and the ${t.mascot} come through"
            stakes.isNotEmpty() -> "${stakes}: ${opp.mascot} end it despite ${line.pts} from ${h.last}"
            line.pts >= 40 -> "${h.last} erupts for ${line.pts} as ${t.mascot} ${if (won) "beat" else "fall to"} ${opp.mascot}"
            won -> "${h.last} scores ${line.pts}, ${t.mascot} top ${opp.mascot} $us-$them"
            else -> "${opp.mascot} hold off ${t.mascot} despite ${line.pts} from ${h.last}"
        }
        val lede = "$dateline — ${h.name} scored ${line.pts} points" + (box?.let { " on ${it.fgm}-of-${it.fga} shooting" } ?: "") +
            (if (line.reb >= 8 || line.ast >= 6) ", with ${line.reb} rebounds and ${line.ast} assists," else "") +
            " and the ${t.name} ${if (won) "beat" else "lost to"} the ${opp.name} $us-$them on $day night" +
            (if (winner) ", winning it at the horn." else ".")
        val context = buildList {
            if (g.type == GameType.PLAYOFF) lg.proState?.series?.firstOrNull { g.id in it.gameIds }?.let { add("${ProSeason.seriesStatus(lg, it)}.") }
            else add("The ${t.mascot} are ${t.w}-${t.l}" + when {
                t.streak >= 3 -> ", winners of ${t.streak} straight."
                t.streak <= -3 -> " and have lost ${-t.streak} in a row."
                else -> "."
            })
            if (lg.rivalry(g) != null) add("It was the latest chapter of the rivalry, and the crowd treated it that way.")
            if (duel != null) Rival.player(lg)?.let { add("${it.last}, his rival since high school, had ${duel.him}.") }
        }.joinToString(" ")
        val quote = if (won) Rng.pick(listOf(
            "“He was special tonight,” coach ${coach.last} said. “When he plays like that, we're hard to beat.”",
            "“We rode him,” coach ${coach.last} said. “He made the right play almost every time down.”",
        )) else Rng.pick(listOf(
            "“We have to be better,” coach ${coach.last} said. “He gave us a chance. The rest of us didn't do enough.”",
            "“Credit to them,” coach ${coach.last} said. “We'll look at the film and get back to work.”",
        ))
        val others = g.box?.let { b ->
            val mine = if (home) b.home else b.away
            val theirs = if (home) b.away else b.home
            val mate = mine.filter { it.pid != h.id }.maxByOrNull { it.s.pts }
            val top = theirs.maxByOrNull { it.s.pts }
            listOfNotNull(mate?.let { "${it.name} added ${it.s.pts} for the ${t.mascot}." }, top?.let { "${it.name} led the ${opp.mascot} with ${it.s.pts}." }).joinToString(" ")
        } ?: ""
        val body = listOf(lede, context, quote, others).filter { it.isNotBlank() }.joinToString("\n\n")
        lg.news += News(lg.season, lg.day, headline, true, g.id, "article", body, Rng.pick(OUTLETS))
    }

    // ---------- the press ----------

    /** His answer makes a story and gets reactions. */
    fun afterPress(lg: League, q: PressQuestion, a: PressAnswer) {
        val h = lg.hero ?: return
        val t = if (h.teamId >= 0) lg.team(h.teamId) else return
        val short = a.text.trimEnd('.').let { if (it.length > 60) it.take(57) + "..." else it }
        val outlet = q.reporter.substringAfter(" · ", Rng.pick(OUTLETS))
        // Bold answers and the big topics make the news, but not every day.
        val headlineTopic = q.topic in listOf("Game-winner", "Rival", "Trade talk", "Award race", "Contract year")
        val recent = lg.news.any { it.kind == "quote" && it.season == lg.season && lg.day - it.day < 5 }
        if (!recent && (headlineTopic || (a.persona > 0 && Rng.chance(0.6)))) {
            val body = "Asked ${q.prompt.replaceFirstChar { it.lowercase() }.trimEnd('?', '.').let { "“$it”" }}, ${h.name} didn't hesitate.\n\n" +
                "“${a.text}”\n\n" + when {
                    a.persona > 0 -> "It's the kind of answer that plays well with the fan base and gets pinned on bulletin boards across the league."
                    a.persona < 0 -> "Teammates and coaches have noticed the same thing: the attention hasn't changed how he carries himself."
                    else -> "He kept it short, and moved on to the next question."
                }
            lg.news += News(lg.season, lg.day, "${h.last} on ${q.topic.lowercase()}: “$short”", true, q.gameId, "quote", body, outlet)
        }
        when {
            a.persona > 0 -> {
                post(lg, fanName(), fanHandle(t), "fan", "“${short}” — that's my guy. Confidence is everything.", likes(lg, 0.008))
                if (Rng.chance(0.5)) {
                    val (hn, hh) = hater()
                    post(lg, hn, hh, "hater", "“${short}” Okay, ${h.last}. Let's see it.", likes(lg, 0.006))
                }
            }
            a.persona < 0 -> {
                val (an, ah) = Rng.pick(ANALYSTS)
                post(lg, an, ah, "analyst", "Listen to how ${h.last} talks about his teammates. That's how winning players sound.", likes(lg, 0.004), verified = true)
            }
            else -> {}
        }
    }

    // ---------- columns ----------

    /** Every two weeks of a season: a column on the MVP (or Player of the Year) race. */
    fun raceColumn(lg: League) {
        val h = lg.hero ?: return
        val r = lg.road ?: return
        val key = lg.season * 1000 + lg.day
        if (lg.day < 14 || (r.lastColumn / 1000 == lg.season && key - r.lastColumn < 14)) return
        r.lastColumn = key
        val ladder = Awards.ladder(lg).take(5)
        if (ladder.size < 5) return
        val award = if (lg.pro) "MVP" else "Player of the Year"
        val mine = Road.raceRank(lg)
        val lines = ladder.mapIndexed { i, (p, t) -> "${i + 1}. ${p.name}, ${t.school} ${t.mascot} (${Awards.line(p)})" }
        val headline = "$award ladder: ${ladder.first().first.last} holds No. 1" + if (mine in 1..10) "; ${h.last} at No. $mine" else ""
        val body = "Where the $award race stands after ${lg.user.gp} games.\n\n" + lines.joinToString("\n") +
            "\n\n" + when {
                mine == 1 -> "${h.last} has the top spot. The question now is whether he can hold it."
                mine in 2..5 -> "${h.last} is in the conversation at No. $mine. A big stretch moves him up."
                mine in 6..10 -> "${h.last} is on the edge of the race at No. $mine."
                else -> "${h.last} isn't in the race yet, but there's plenty of season left."
            }
        lg.news += News(lg.season, lg.day, headline, mine in 1..10, -1, "column", body, Rng.pick(OUTLETS))
    }
}
