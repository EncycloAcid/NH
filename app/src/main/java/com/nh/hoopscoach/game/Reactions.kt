package com.nh.hoopscoach.game

import kotlin.math.abs

/**
 * Reactions: after each day (and each offseason step) every new story that matters gets what people said
 * about it. The players involved, coaches, a front office, an analyst or an insider, and fans of both
 * sides, each in their own voice and in the tone the story deserves: a lopsided trade has one happy fan
 * base and one furious one, a 40-point night gets the other coach's shrug, a firing splits the locker
 * room. Signings and coaching changes also become reported stories ("agreed to terms, sources say")
 * instead of one-line notes. Real analysts and insiders speak in today's game (the user's social pack);
 * past eras get made-up ones.
 */
object Reactions {
    private class Voice(val who: String, val role: String, val avatar: String = "", val verified: Boolean = false)

    private val WRITERS = listOf("Nora Quinn", "Cal Benton", "Priya Shah", "Marcus Lyle", "Dana Marsh", "Theo Banks", "Rae Whitfield", "Jules Ortega")
    private val SHOWS = listOf("The Hoops Hour", "Full Court Press", "The Rim", "Hoops Wire", "Courtside Report", "The Daily Dribble")
    private val NEUTRAL_FANS = listOf("Hot Takes Only", "Real Hoops Fan", "Bench Mob", "League Pass Larry", "Box Score Betty")

    private fun nameOf(lg: League, t: Team) = if (lg.pro) t.name else t.school
    private fun nick(lg: League, t: Team) = if (lg.pro) t.name.substringAfterLast(' ') else t.mascot

    /** Names already speaking on the story at hand, so two fans never share one. */
    private val used = HashSet<String>()

    private fun fan(lg: League, t: Team): Voice {
        var name = ""
        repeat(5) {
            val first = Rng.pick(Names.first)
            name = Rng.pick(listOf("Big $first", "$first ${Rng.pick(Names.last).first()}.", "Courtside $first", "$first from section ${Rng.range(101, 330)}", "${nick(lg, t)} Nation"))
            if (used.add(name)) return Voice(name, "${nick(lg, t)} fan")
        }
        return Voice(name, "${nick(lg, t)} fan")
    }

    /** Outlets that don't do NBA takes (college networks, a video game, highlight pages). */
    private val NOT_PUNDITS = setOf("ACC Network", "CBS Sports CBB", "FOX College Hoops", "NBA 2K", "House of Highlights", "Ballislife", "StatMuse", "Barstool Sports", "Overtime")

    private fun neutral(): Voice = Voice(Rng.pick(NEUTRAL_FANS), "Fan")

    private fun analyst(lg: League): Voice {
        repeat(6) {
            val a = SocialAccounts.pick(lg, "analyst") ?: return@repeat
            if (a.name !in NOT_PUNDITS) return Voice(a.name, "Analyst", a.avatar, a.verified)
        }
        return Voice(Rng.pick(WRITERS), "Analyst, ${Rng.pick(SHOWS)}")
    }

    private fun insider(lg: League): Voice = SocialAccounts.pick(lg, "insider")?.let { Voice(it.name, "Insider", it.avatar, it.verified) }
        ?: Voice(Rng.pick(WRITERS), if (lg.pro) "NBA insider" else "College hoops insider")

    private fun player(lg: League, p: Player, t: Team?) = Voice(p.name, listOfNotNull(t?.let { nick(lg, it) }, p.posLabel).joinToString(" "))
    /** A team's head coach, unless the user coaches it: nobody puts words in his mouth. */
    private fun coach(lg: League, t: Team): Voice? =
        if (t.id == lg.userTeamId && lg.road == null) null else Voice(lg.coachOf(t).name, "${nameOf(lg, t)} head coach")

    /** "Jaren Jackson Jr." ends a sentence without a second period. */
    private fun end(s: String) = if (s.endsWith(".")) s else "$s."

    private fun likes(lg: League, t: Team?, scale: Double): Int {
        val market = (t?.prestige?.coerceIn(30, 99) ?: 60) / 60.0
        return ((800 + Rng.d() * 4000) * scale * market * (if (lg.pro) 3.0 else 1.0)).toInt().coerceAtLeast(12)
    }

    private fun r(lg: League, v: Voice, text: String, t: Team?, scale: Double, tone: Int) =
        Reaction(v.who, v.role, text, likes(lg, t, scale), v.avatar, v.verified, tone)

    /** Reactions for every story since the last pass. */
    fun pass(lg: League) {
        // His own feed: posts for anything new in his career (player mode).
        runCatching { Feed.watch(lg) }
        // An older save: start with the latest stories rather than the whole archive.
        if (lg.newsSeen == 0 && lg.news.size > 40) lg.newsSeen = lg.news.size - 12
        var i = lg.newsSeen.coerceIn(0, lg.news.size)
        while (i < lg.news.size) {
            val n = lg.news[i]
            if (n.reactions.isEmpty()) runCatching { react(lg, i, n) }
            i++
        }
        lg.newsSeen = lg.news.size
    }

    private fun gameOf(lg: League, n: News) = if (n.gameId >= 0) lg.games.firstOrNull { it.id == n.gameId && it.played } else null

    private fun react(lg: League, i: Int, n: News) {
        used.clear()
        val text = n.text
        when {
            n.kind == "trade" -> trade(lg, n)
            n.kind == "game" -> bigNight(lg, n)
            n.kind == "team" -> streak(lg, n)
            n.kind == "recap" -> recap(lg, n)
            n.kind == "series" -> series(lg, n)
            n.kind == "upset" || n.kind == "buzzer" || n.kind == "moment" -> moment(lg, n)
            text.startsWith("Coaching change: ") -> proCoaching(lg, i, n)
            text.startsWith("Free agency: ") -> signing(lg, i, n)
            text.contains(" commits to ") -> commit(lg, i, n)
            !lg.pro && text.contains(" hires ") && text.endsWith("as head coach") -> collegeHire(lg, i, n)
            text.contains(" has fired ") || text.contains(" fire head coach ") -> userFired(lg, n)
        }
    }

    // ---------- trades ----------

    private fun gradeScore(g: String) = when (g) { "A" -> 2; "A-" -> 1; "B+" -> 1; "B" -> 0; "B-" -> 0; "C" -> -1; else -> -2 }

    private fun trade(lg: League, n: News) {
        val m = Regex("Our grades: (.+) ([A-D][+-]?), (.+) ([A-D][+-]?)\\.").find(n.body) ?: return
        val (aName, ga, bName, gb) = m.destructured
        val a = lg.teams.firstOrNull { it.name == aName } ?: return
        val b = lg.teams.firstOrNull { it.name == bName } ?: return
        val star = lg.playerOrNull(n.pid)
        val out = mutableListOf<Reaction>()
        if (star != null && star.teamId >= 0) {
            val newTeam = lg.team(star.teamId)
            val oldTeam = if (newTeam.id == a.id) b else a
            out += r(lg, player(lg, star, newTeam), Rng.pick(listOf(
                "Thank you, ${oldTeam.school}. Nothing but love. Time to get to work in ${newTeam.school}.",
                "Grateful for every day with the ${nick(lg, oldTeam)}. New chapter. Let's go, ${nick(lg, newTeam)}.",
                "Business is business. ${newTeam.school}, I'm ready.",
            )), newTeam, 3.0, 1)
        }
        val winner = if (gradeScore(ga) >= gradeScore(gb)) a else b
        out += r(lg, analyst(lg), when {
            abs(gradeScore(ga) - gradeScore(gb)) <= 1 -> Rng.pick(listOf("Fair deal. Both sides got what they needed.", "This one works for everybody. Rare.", "No clear winner yet. Ask me in April."))
            else -> Rng.pick(listOf("The ${winner.name} won this trade, and it isn't close.", "Clear winner here: the ${winner.name}.", "I don't know what the other side was thinking. Big win for the ${winner.name}."))
        }, null, 1.5, 0)
        for ((t, g) in listOf(a to ga, b to gb)) {
            val s = gradeScore(g)
            out += r(lg, fan(lg, t), when {
                s >= 1 -> Rng.pick(listOf("WE GOT ROBBED... in our favor. Front office cooked 🔥", "Best trade we've made in years. Banner szn.", "Somebody check on the other GM because WOW."))
                s == 0 -> Rng.pick(listOf("I'll wait and see. Not mad at it.", "Fine move. Not a franchise-changer.", "Eh. We'll know by the deadline."))
                else -> Rng.pick(listOf("Who approved this?? Fire everyone.", "We gave THAT up for THIS? I need a minute.", "Worst trade I've seen from this front office. And that's saying something."))
            }, t, 1.0, s.coerceIn(-1, 1))
        }
        n.reactions = out
    }

    // ---------- games ----------

    private fun bigNight(lg: League, n: News) {
        val p = lg.playerOrNull(n.pid) ?: return
        val t = lg.teams.getOrNull(n.teamId) ?: return
        val g = gameOf(lg, n)
        val opp = g?.let { lg.team(it.opp(t.id)) }
        val mate = lg.rosterOf(t).filter { it.id != p.id }.maxByOrNull { it.ovr }
        val out = mutableListOf<Reaction>()
        if (mate != null) out += r(lg, player(lg, mate, t), Rng.pick(listOf(
            "That's my guy. ${p.last} was locked in from the jump.",
            "When he gets going like that you just get out of the way.",
            "Seen him do this in practice for years. Now everybody knows.",
        )), t, 1.5, 1)
        if (opp != null) coach(lg, opp)?.let { v -> out += r(lg, v, Rng.pick(listOf(
            "We threw everything at him. Tip your cap.",
            "We made it too easy for him early, and that's on me.",
            "Great players make tough shots. He made all of them tonight.",
        )), opp, 0.6, 0) }
        out += r(lg, analyst(lg), Rng.pick(listOf(
            "${p.name} tonight: that's a star performance, period.",
            "Put that one in the time capsule. ${p.last} was unguardable.",
            "Best individual game I've seen this season. ${p.last} is a problem.",
        )), null, 1.4, 1)
        val won = g == null || g.winner() == t.id
        val award = if (lg.pro) "MVP" else "Player of the Year"
        out += r(lg, fan(lg, t), if (won) Rng.pick(listOf("${p.last.uppercase()}!!! I'm out of breath just watching.", if (lg.pro) "Somebody give this man a raise." else "Enjoy him while he's here, people.", "$award chants were loud tonight and they should be."))
            else Rng.pick(listOf("${p.last} did everything. Nobody else showed up.", "Wasting a night like that should be illegal.", "Give that man some help!")), t, 1.0, if (won) 1 else 0)
        if (opp != null) out += r(lg, fan(lg, opp), if (won) Rng.pick(listOf("Did anybody on our team try to guard him?", "Embarrassing. Zero adjustments.", "Not watching the replay. Not today."))
            else Rng.pick(listOf("He got his, we got the W.", "Let him score. Scoreboard.", "Let him have his numbers. We'll take the win.")), opp, 0.6, if (won) -1 else 1)
        n.reactions = out
    }

    private fun streak(lg: League, n: News) {
        val t = lg.teams.getOrNull(n.teamId) ?: return
        val win = Regex("\\bwins? \\d+ straight").containsMatchIn(n.text)
        val out = mutableListOf<Reaction>()
        if (win) {
            out += r(lg, fan(lg, t), Rng.pick(listOf("Don't wake me up. ${t.w}-${t.l} and rolling.", "Nobody wants to see us right now.", "This is the most fun I've had watching this team in years.")), t, 1.0, 1)
            out += r(lg, analyst(lg), Rng.pick(listOf("The ${nick(lg, t)} are for real. I was wrong about them.", "This streak isn't luck: they're defending like a playoff team.", "Hot streak or the real thing? I'm leaning real.")), null, 1.2, 1)
        } else {
            out += r(lg, fan(lg, t), Rng.pick(listOf("I can't keep doing this. Something has to change.", "Fire somebody. Anybody. Please.", "At least the draft lottery will be fun.", "Same mistakes every night.")), t, 1.0, -1)
            out += r(lg, analyst(lg), Rng.pick(listOf("At some point this becomes a coaching conversation.", "Time for the ${nick(lg, t)} to think about the future.", "The effort is there. The talent isn't.")), null, 1.0, -1)
        }
        out += r(lg, neutral(), if (win) Rng.pick(listOf("Bandwagon is open, hop on.", "Is it too early to call them contenders? Yes. Am I doing it anyway? Also yes."))
            else Rng.pick(listOf("Thoughts and prayers to ${nameOf(lg, t)} fans.", "Tough watch.")), null, 0.5, 0)
        n.reactions = out
    }

    private fun recap(lg: League, n: News) {
        val g = gameOf(lg, n) ?: return
        val u = lg.user
        if (!g.involves(u.id)) return
        val opp = lg.team(g.opp(u.id))
        val won = g.winner() == u.id
        val margin = abs(g.hs - g.aws)
        val out = mutableListOf<Reaction>()
        out += r(lg, fan(lg, u), when {
            won && margin >= 20 -> Rng.pick(listOf("That's a statement. Everyone take notes.", "Blowout city. Bench got run, love to see it.", "We look like a different team this year."))
            won && margin <= 4 -> Rng.pick(listOf("My heart can't take these close ones. W is a W.", "Ugly win but I'll take it.", "Clutch when it mattered. That's growth."))
            won -> Rng.pick(listOf("Good win. On to the next.", "Handled business.", "Solid. Need more of that defense."))
            margin >= 20 -> Rng.pick(listOf("Turned it off at halftime. Unwatchable.", "No effort. None.", "Coach has to answer for that one."))
            else -> Rng.pick(listOf("Should've had that one. Bad shots late.", "Learn from it and move on.", "Officials were awful, but we still have to finish."))
        }, u, 1.0, if (won) 1 else -1)
        out += r(lg, fan(lg, opp), if (won) Rng.pick(listOf("Not our night.", "We'll see them again.", "Credit to them, they wanted it more.")) else Rng.pick(listOf("Too easy.", "Big win for us. Moving on.", "Defense travels.")), opp, 0.6, if (won) -1 else 1)
        if (margin <= 3 || margin >= 25) out += r(lg, analyst(lg), if (margin <= 3) "That's the kind of finish that tells you who a team is." else "Lopsided. One of these teams has some soul-searching to do.", null, 0.8, 0)
        n.reactions = out
    }

    private fun series(lg: League, n: News) {
        val w = lg.teams.getOrNull(n.teamId) ?: return
        val g = gameOf(lg, n) ?: return
        val l = lg.team(g.opp(w.id))
        val title = n.text.endsWith("champions")
        val out = mutableListOf<Reaction>()
        lg.playerOrNull(n.pid)?.let { star -> out += r(lg, player(lg, star, w), if (title) "Champions. Every sprint, every early morning, for this. Thank you, ${w.school}." else Rng.pick(listOf("One down. We're not satisfied.", "Respect to ${l.school}. That was a war.", "Job's not finished.")), w, if (title) 4.0 else 1.8, 1) }
        out += r(lg, fan(lg, w), if (title) Rng.pick(listOf("CHAMPIONS!!! I'm crying in the parking lot.", "PARADE. TIME. 🏆", "Bring it home. We earned this one.")) else Rng.pick(listOf("Next round, let's go!", "Never in doubt (it was very in doubt).", "Bring on whoever's next.")), w, if (title) 2.5 else 1.2, 1)
        out += r(lg, fan(lg, l), Rng.pick(listOf("Long summer ahead.", "Proud of the fight. Not proud of the ending.", "Changes have to come.")), l, 0.8, -1)
        coach(lg, l)?.let { out += r(lg, it, Rng.pick(listOf("They were better. That's the truth of it.", "Our guys gave everything. We'll be back.", "It stings. It should sting.")), l, 0.5, 0) }
        out += r(lg, analyst(lg), if (title) "The ${w.name} are champions, and they earned every bit of it." else Rng.pick(listOf("The ${nick(lg, w)} look like they're just getting started.", "${l.school} has questions to answer this summer.")), null, 1.4, 0)
        n.reactions = out
    }

    private fun moment(lg: League, n: News) {
        val g = gameOf(lg, n) ?: return
        val w = lg.team(g.winner())
        val l = lg.team(g.loser())
        val march = g.type == GameType.NCAA
        val upset = n.kind == "upset"
        val buzzer = n.kind == "buzzer"
        val out = mutableListOf<Reaction>()
        out += r(lg, fan(lg, w), when {
            buzzer -> Rng.pick(listOf("AT THE BUZZER!!! I'm hoarse. I'm so hoarse.", "I've watched it 40 times. Going for 41.", "WHAT. A. SHOT."))
            upset -> Rng.pick(listOf("WE STORMED THE COURT AND I'D DO IT AGAIN", "Bracket busted, hearts full.", "Nobody believed in us. NOBODY."))
            march -> Rng.pick(listOf("This team, man. This team.", "Instant classic.", "Survive and advance!"))
            else -> Rng.pick(listOf("This team, man. This team.", "Instant classic.", "Took years off my life. Worth it."))
        }, w, if (upset || buzzer) 2.0 else 1.0, 1)
        coach(lg, l)?.let { v -> out += r(lg, v, if (march) Rng.pick(listOf("They made one more play than us. That's March.", "Hurts. Proud of these guys anyway.", "Credit to ${w.school}. They earned it."))
            else Rng.pick(listOf("They made one more play than us.", "We'll learn from it. Credit to ${w.school}.", "Tough one to swallow.")), l, 0.6, 0) }
        out += r(lg, analyst(lg), when {
            upset -> Rng.pick(listOf("${w.school} just busted millions of brackets.", "Cinderella has a name this year: ${w.school}.", "This is why we love March."))
            buzzer && march -> "Moment of the tournament so far. Play it on a loop."
            buzzer -> "Play of the year contender. Play it on a loop."
            else -> "That's the game everyone will be talking about tomorrow."
        }, null, 1.6, 0)
        out += r(lg, fan(lg, l), Rng.pick(listOf("I'll be in bed for the next three days.", "We had it. We HAD it.", if (march) "Next year. Always next year." else "Can't let that one go.")), l, 0.7, -1)
        n.reactions = out
    }

    // ---------- the front office ----------

    /** A story rewritten as a reported article (body, outlet), keeping everything else. */
    private fun report(lg: League, i: Int, n: News, head: String, body: String, source: String, teamId: Int, pid: Int, reactions: List<Reaction>) {
        lg.news[i] = News(n.season, n.day, head, n.mine, n.gameId, n.kind.ifEmpty { "move" }, body, source, teamId, pid, reactions)
    }

    private fun proCoaching(lg: League, i: Int, n: News) {
        val line = n.text.removePrefix("Coaching change: ")
        val t = lg.teams.firstOrNull { line.startsWith("The ${it.name} fire ") } ?: return
        val old = line.substringAfter(" fire ").substringBefore(" after ")
        val record = line.substringAfter(" after a ").substringBefore(" season")
        // The replacement, once the team has hired one (the hire can be its own story a step later).
        val newCoach = lg.coachOf(t).takeIf { it.teamId == t.id && it.name != old }
        val ins = insider(lg)
        val body = "The ${t.name} have fired head coach $old after a $record season, sources told ${ins.who}. " +
            (newCoach?.let { "${it.name} takes over, and he brings a ${t.plan.offense.label.lowercase()} offense and a ${t.plan.defense.label.lowercase()} defense." } ?: "The search for a replacement starts now.") + "\n\n" +
            end("The new staff inherits a roster led by ${lg.rosterOf(t).maxByOrNull { it.ovr }?.name ?: "a young core"}")
        val star = lg.rosterOf(t).maxByOrNull { it.ovr }
        val out = listOfNotNull(
            star?.let { r(lg, player(lg, it, t), Rng.pick(listOf("Coach ${old.substringAfterLast(' ')} gave me my shot. Love that man, forever.", "Tough business. Thank you, Coach.", "We let him down. That's on us as players.")), t, 1.5, 0) },
            r(lg, analyst(lg), if (t.winPct < 0.35) "It was time. That roster needed a new voice." else Rng.pick(listOf("Harsh. I'm not sure the coach was the problem.", "Coaches get fired. Rosters get them fired.")), null, 1.0, 0),
            r(lg, fan(lg, t), if (t.winPct < 0.4) Rng.pick(listOf("FINALLY.", "About time. Now fix the roster.", "Fresh start. I'm in.")) else Rng.pick(listOf("Didn't deserve that.", "We just fired the wrong guy.")), t, 0.8, if (t.winPct < 0.4) 1 else -1),
            newCoach?.let { r(lg, fan(lg, t), "${it.last}? Never heard of him. Prove me wrong.", t, 0.5, 0) },
        )
        report(lg, i, n, "The ${t.name} fire $old" + (newCoach?.let { "; ${it.name} takes over" } ?: ""), body, "${ins.who} · ${if (lg.pro) "NBA Insider" else "Hoops Wire"}", t.id, -1, out)
    }

    private fun signing(lg: League, i: Int, n: News) {
        val line = n.text.removePrefix("Free agency: ")
        val name = line.substringBefore(" (")
        val t = lg.teams.firstOrNull { line.contains("signs with the ${it.name}") } ?: return
        val from = lg.teams.firstOrNull { line.endsWith("leaving the ${it.name}") }
        val p = lg.players.firstOrNull { it.name == name && it.teamId == t.id } ?: return
        val years = p.contractYears
        val pay = Payroll.salaryOf(lg, p)
        val ask = ProCareer.marketValue(lg, p)
        val ins = insider(lg)
        val last = NbaPreviews.lastLine(lg, p) ?: p.career.lastOrNull { it.cls == PRO_YEAR }
        val body = "${p.name} has agreed to a $years-year, ${Road.dollars(pay * years)} deal with the ${t.name}, sources told ${ins.who}." +
            (last?.let { " He averaged ${"%.1f".format(it.s.ppg)} points, ${"%.1f".format(it.s.rpg)} rebounds and ${"%.1f".format(it.s.apg)} assists last season" + (from?.let { f -> " for the ${f.name}" } ?: "") + "." } ?: "") +
            "\n\nThe ${t.name} " + (if (t.w + t.l > 0) (if (lg.phase == Phase.REGULAR) "(${t.w}-${t.l}) " else "went ${t.w}-${t.l} last season and ") else "") + end("add him to a rotation led by ${lg.rosterOf(t).filter { it.id != p.id }.maxByOrNull { it.ovr }?.name ?: "a young core"}")
        val star = Trades.value(p) >= 8
        val longBet = p.age >= 32 && years >= 3
        val value = when { longBet || pay >= ask * 1.05 -> -1; pay <= ask * 0.92 -> 1; else -> 0 }
        val out = listOfNotNull(
            r(lg, player(lg, p, t), Rng.pick(listOf("${t.school}, let's work. Grateful for the opportunity.", "New city, same hunger. Can't wait to get started.", "This felt like home from the first call.")), t, 2.0, 1),
            r(lg, analyst(lg), when {
                longBet -> Rng.pick(listOf("$years years for a ${p.age}-year-old. The last two could get ugly.", "Great for the next year or two. I'm less sure about the back end of that deal."))
                value == 1 -> Rng.pick(listOf("Great value for the ${t.name}. That contract will look like a steal.", "${p.last} at that price? The ${nick(lg, t)} got a bargain.", "Under market for ${if (star) "a star" else "a guy who can play"}. Smart business."))
                value == -1 -> Rng.pick(listOf("That's an overpay, and the ${nick(lg, t)} will feel it in two years.", "The ${nick(lg, t)} paid top dollar. They'd better be right.", "Somebody bid against themselves on this one."))
                star -> Rng.pick(listOf("Fair price for a star. Sensible signing.", "The ${nick(lg, t)} just changed their ceiling.", "That's a contender move."))
                else -> Rng.pick(listOf("Fair price for a useful player.", "Solid depth signing. Nothing flashy.", "He fills a need. Good, quiet move."))
            }, null, if (star) 1.5 else 0.8, value),
            r(lg, fan(lg, t), Rng.pick(listOf("WELCOME TO ${t.school.uppercase()}!", "Front office finally did something. Love it.", "Get his jersey printed.")), t, 1.2, 1),
            from?.let { f -> r(lg, fan(lg, f), if (star) Rng.pick(listOf("Can't believe we let him walk.", "We let a star walk for NOTHING?", "Front office had one job."))
                else Rng.pick(listOf("Good luck I guess. Thanks for the memories.", "Honestly? Wasn't worth that money.", "We'll be fine. Next man up.")), f, 0.8, if (star) -1 else 0) },
        )
        report(lg, i, n, "${p.name} signs with the ${t.name}", body, "${ins.who} · NBA Insider", t.id, p.id, out)
    }

    private fun commit(lg: League, i: Int, n: News) {
        val school = n.text.substringAfter(" commits to ").trim()
        val t = lg.teams.firstOrNull { it.school == school } ?: return
        val words = n.text.substringBefore(" commits to ").split(" ")
        val posAt = words.indexOfFirst { it in setOf("PG", "SG", "SF", "PF", "C") }
        val name = if (posAt >= 0) words.drop(posAt + 1).joinToString(" ") else words.takeLast(2).joinToString(" ")
        val ins = insider(lg)
        val out = listOf(
            r(lg, Voice(name, if (n.text.startsWith("Transfer ")) "Transfer" else "Recruit"), Rng.pick(listOf("100% committed to ${t.school}! Thank you to every coach who believed in me. #Committed", "Home. ${t.school}, let's get it.", "Blessed to announce my commitment to ${t.school}.")), t, 1.5, 1),
            r(lg, ins, Rng.pick(listOf("Big get for ${t.school}. Instant impact guy.", "${t.school} keeps winning on the recruiting trail.", "Flip alert averted: ${name.substringAfterLast(' ')} is a ${nick(lg, t)}.")), null, 1.0, 1),
            r(lg, fan(lg, t), Rng.pick(listOf("WE GOT HIM!!!", "Coach is cooking on the trail.", "Welcome to the family!")), t, 0.8, 1),
        )
        report(lg, i, n, n.text, "${n.text}, sources told ${ins.who}. " + "He picked ${t.school} after official visits, and ${lg.coachOf(t).name}'s staff made him a priority from the start.", "${ins.who} · Recruiting", t.id, -1, out)
    }

    private fun collegeHire(lg: League, i: Int, n: News) {
        val t = lg.teams.firstOrNull { n.text.startsWith("${it.school} hires ") } ?: return
        val c = lg.coachOf(t)
        val out = listOf(
            r(lg, analyst(lg), if (c.titles > 0) "A national champion takes over. ${t.school} aimed high." else Rng.pick(listOf("Solid hire. The question is recruiting.", "Interesting choice. He has to win early at a place like ${t.school}.")), null, 1.0, 0),
            r(lg, fan(lg, t), Rng.pick(listOf("I like it. Give him time.", "Underwhelming, but let's see.", "This is the guy. Book it.")), t, 0.8, 0),
        )
        report(lg, i, n, n.text, "${t.school} has hired ${c.name} as its head coach. He brings a ${c.w}-${c.l} career record" + (if (c.titles > 0) " and ${c.titles} national ${if (c.titles == 1) "title" else "titles"}" else "") + ".", "${Rng.pick(WRITERS)} · Hoops Wire", t.id, -1, out)
    }

    private fun userFired(lg: League, n: News) {
        val u = lg.user
        val name = n.text.substringAfter(if (n.text.contains(" has fired ")) " has fired " else " fire head coach ").trim()
        val last = name.substringAfterLast(' ')
        val star = lg.rosterOf(u).maxByOrNull { it.ovr }
        n.reactions = listOfNotNull(
            star?.let { r(lg, player(lg, it, u), "Coach $last believed in me when nobody else did. Wherever he goes, they're lucky.", u, 1.5, 0) },
            r(lg, analyst(lg), Rng.pick(listOf("Results business. ${u.school} decided it couldn't wait.", "Some will call it harsh. The record says otherwise.")), null, 1.0, 0),
            r(lg, fan(lg, u), Rng.pick(listOf("Needed to happen.", "Thanks for everything, Coach. Truly.", "We'll regret this one.")), u, 0.8, 0),
        )
    }
}
