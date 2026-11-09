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

    /** Someone posting: a real account (with its avatar) or a made-up one. */
    class Voice(val name: String, val handle: String, val avatar: String = "", val verified: Boolean = true)

    /**
     * A post on his feed. Team, league and clip posts use the real accounts where the pack has them (today's
     * game only); the generic "NBA Insider" becomes a real insider. Posts from a [Voice] keep their account.
     */
    fun post(lg: League, name: String, handle: String, kind: String, text: String, likes: Int, verified: Boolean = false, gameId: Int = -1, avatar: String = "",
             league: Boolean = false, re: String = "") {
        val r = lg.road ?: return
        val feed = if (league) r.leagueFeed else r.feed
        // Nobody posts the exact same thing twice in a row of posts.
        if (feed.takeLast(40).any { it.text == text }) return
        // Team / league / highlight posts always take the pack account. Voices that already carry a
        // real avatar (pundit, insider) keep it; generic placeholders get upgraded.
        val acct = when {
            kind == "league" || name.equals("NBA", true) || name.equals("NCAA", true) || name.contains("March Madness", true) ->
                SocialAccounts.league(lg)
            kind == "team" -> {
                val t = lg.teams.firstOrNull { it.name.equals(name, true) || "${it.school} ${it.mascot}".equals(name, true) }
                if (t != null) SocialAccounts.forTeam(lg, t) else SocialAccounts.forTeam(lg, name)
            }
            kind == "media" -> SocialAccounts.pick(lg, "highlights")
            kind == "insider" && (avatar.isEmpty() || handle in setOf("nbainsider", "hoopsinsider")) ->
                SocialAccounts.pick(lg, "insider")
            kind == "analyst" && avatar.isEmpty() ->
                SocialAccounts.pick(lg, "person") ?: SocialAccounts.pick(lg, "analyst")
            else -> null
        }
        // Made-up names for the league account in eras before social media, at the right level.
        val shown = acct?.name ?: if (kind == "league" && !lg.pro) "NCAA March Madness" else name
        feed += Post(lg.season, lg.day, shown, acct?.handle ?: handle, kind, text, max(likes, 3), acct?.verified ?: verified, gameId, acct?.avatar ?: avatar, re)
        val keep = if (league) 150 else 80
        if (feed.size > keep) feed.subList(0, feed.size - keep).clear()
    }

    fun say(lg: League, v: Voice, kind: String, text: String, likes: Int) =
        post(lg, v.name, v.handle, kind, text, likes, v.verified, avatar = v.avatar)

    /** Likes as a share of his following, with some luck. */
    fun likes(lg: League, share: Double): Int {
        val f = max(lg.road?.followers ?: 0, 2_000).toDouble()
        return (f * share * (0.5 + Rng.d())).roundToInt()
    }

    fun fanHandle(t: Team) = Rng.pick(listOf("${t.mascot.replace(" ", "")}Nation", "${t.abbr}Faithful", "${t.school.replace(" ", "")}Hoops", "Die-hard${t.mascot.replace(" ", "")}", "${t.abbr}TilIDie"))
    fun fanName() = Rng.pick(listOf("Big ${Rng.pick(Names.first)}", "${Rng.pick(Names.first)} ${Rng.pick(Names.last).first()}.", "Courtside ${Rng.pick(Names.first)}", "${Rng.pick(Names.first)} from section ${Rng.range(101, 330)}"))
    fun hater() = Rng.pick(listOf("Hot Takes Only" to "hottakes_only", "Overrated Watch" to "overratedwatch", "Real Hoops Fan" to "realhoopsfan_", "Bench Mob" to "benchmob99"))

    /** An analyst with a first-person take: a real pack account in today's game, else a made-up regular. */
    fun pundit(lg: League): Voice {
        SocialAccounts.pick(lg, "person")?.let { return Voice(it.name, it.handle, it.avatar, it.verified) }
        SocialAccounts.pick(lg, "analyst")?.let { return Voice(it.name, it.handle, it.avatar, it.verified) }
        val (n, h) = Rng.pick(ANALYSTS)
        return Voice(n, h)
    }

    /** An insider: a real one at his level, or a made-up one only when the pack has none. */
    fun insider(lg: League): Voice = SocialAccounts.pick(lg, "insider")?.let { Voice(it.name, it.handle, it.avatar, it.verified) }
        ?: if (lg.pro) Voice("NBA Insider", "nbainsider") else Voice("Hoops Insider", "hoopsinsider")

    /** His box line for a game, if the box score was kept. */
    private fun boxLine(g: Game, pid: Int): StatLine? = g.box?.let { b -> (b.home + b.away).firstOrNull { it.pid == pid }?.s }

    // ---------- after a game ----------

    fun afterGame(lg: League, g: Game, line: GameLogEntry, won: Boolean, winner: Boolean, stakes: String, duel: Duel?) {
        if (lg.hero == null || lg.road == null) return
        Feed.game(lg, g, line, won, winner, stakes, duel)
        val big = line.pts >= 28 || listOf(line.pts, line.reb, line.ast).count { it >= 10 } >= 2 || winner
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
        val headlineTopic = q.topic in listOf("Game-winner", "Rival", "Old team", "Award race", "Rookie race", "All-Star", "Contract year", "Draft stock", "Season over", "Senior night", "New team", "Playoffs")
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
            // A bold answer gets quoted back at him, by a fan or a doubter (not every time).
            a.persona > 0 && Rng.chance(0.6) -> if (Rng.chance(0.55)) post(lg, fanName(), fanHandle(t), "fan", Rng.pick(listOf(
                "“$short” ${h.last} doesn't hold back. I like it.",
                "${h.last} after the game: “$short” Confidence.",
                "“$short” Somebody print that on a shirt.",
            )), likes(lg, 0.008)) else {
                val (hn, hh) = hater()
                post(lg, hn, hh, "hater", Rng.pick(listOf("“$short” Okay, ${h.last}. Let's see it.", "Bold words from ${h.last}: “$short” Back it up.", "“$short” Screenshot this one for later.")), likes(lg, 0.006))
            }
            a.persona < 0 && Rng.chance(0.3) -> say(lg, pundit(lg), "analyst", Rng.pick(listOf(
                "Listen to how ${h.last} talks about his teammates. That's how winning players sound.",
                "${h.last} deflects credit every time. Coaches love that.",
                "Mature answer from ${h.last}. The talent is obvious; the head is what makes it last.",
            )), likes(lg, 0.004))
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
