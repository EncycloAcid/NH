package com.nh.hoopscoach.game

import kotlin.math.max

/**
 * His feed, built from his career. After a game, who he is right now decides what people post: a rookie,
 * a sixth man, a late pick, a max contract, a hot week or a slump, a game against the team that let him
 * go, a duel with his rival. Fans judge a night against his own normal (12 points is a big night for a
 * bench guy and a bad one for a star), haters only bother with players worth the effort, and analysts
 * post the numbers behind a trend. The moments of his career get their own posts when they happen: the
 * draft, an award, an All-Star nod, a title, a trade, a transfer, a new contract, a career-points milestone.
 */
object Feed {
    /** Who he is right now. [line] is the game just played (already in his log), or null. */
    class Form(val lg: League, val h: Player, line: GameLogEntry?) {
        val r: RoadState = lg.road!!
        val pro = lg.pro
        val t: Team? = if (h.teamId >= 0) lg.team(h.teamId) else null
        /** This season's games before this one. */
        val before: List<GameLogEntry> = if (line != null && h.log.lastOrNull()?.gameId == line.gameId) h.log.dropLast(1) else h.log
        private val lastSeason = h.career.lastOrNull()?.s?.takeIf { it.gp >= 5 }?.ppg
        /** What he usually scores: this season so far, or last season early on. */
        val norm: Double = when {
            before.size >= 4 -> before.map { it.pts }.average()
            lastSeason != null -> (lastSeason * 0.7 + (before.map { it.pts }.average().takeIf { before.isNotEmpty() } ?: lastSeason) * 0.3)
            before.isNotEmpty() -> before.map { it.pts }.average()
            else -> 8.0
        }
        val last5: Double? = before.takeLast(5).takeIf { it.size == 5 }?.map { it.pts }?.average()
        val earlier: Double? = before.dropLast(5).takeIf { it.size >= 4 }?.map { it.pts }?.average()
        val last3: Double? = before.takeLast(3).takeIf { it.size == 3 }?.map { it.pts }?.average()
        val starter = h.stats.gp == 0 || h.stats.gs * 2 >= h.stats.gp
        val rookie = if (pro) h.proYears == 0 else h.year == 1
        val pick = if (pro) r.draftPick else 0
        val undrafted = pro && r.undrafted
        val salary = if (pro) r.contract?.salary ?: 0 else 0
        val star = h.stats.ppg >= 19 || r.followers >= 2_000_000 || salary >= 25_000
        /** Teams he used to play for (in this league). */
        val former: Set<Int> = (if (pro) r.proTeams else r.schools).filter { it != h.teamId }.toSet()
        val award = if (pro) "MVP" else "Player of the Year"
        val rookieWord = if (pro) "rookie" else "freshman"
        val nick = t?.mascot ?: ""
        val place = t?.school ?: ""
        val league = if (pro) "the league" else "the country"
        /** Where his team stands: "5th in the East", or "3rd in the SEC". */
        val standing: Int = t?.let { tm -> if (pro) ProSeason.standings(lg, tm.confId).indexOfFirst { it.id == tm.id } + 1 else Season.standings(lg, tm.confId).indexOfFirst { it.id == tm.id } + 1 } ?: 0
        val confName: String = t?.let { lg.conf(it.confId).abbr.let { a -> if (pro) a.lowercase().replaceFirstChar { c -> c.uppercase() } else a } } ?: ""
        /** "the No. 49 pick", "an undrafted rookie", or "". */
        val pickWords = when { undrafted -> "undrafted"; pick in 1..60 -> "the No. $pick pick"; else -> "" }
    }

    /** His career high in points before the game just played (-1 when nothing's known yet). */
    fun careerBest(lg: League, f: Form): Int = if (f.r.bestPts >= 0) f.r.bestPts
        else (f.before.maxOfOrNull { it.pts } ?: -1).let { if (lg.pro) max(it, f.r.proBest.getOrElse(0) { 0 }) else it }

    /** Whether a league story touches his team (his feed is about him, not the whole league). */
    fun mine(lg: League, vararg teamIds: Int): Boolean { val id = lg.hero?.teamId ?: return false; return id >= 0 && id in teamIds }

    fun line(pts: Int, reb: Int, ast: Int): String = listOfNotNull("$pts PTS", if (reb > 0) "$reb REB" else null, if (ast > 0) "$ast AST" else null).joinToString(", ")
    private fun f1(d: Double) = "%.1f".format(d)
    private fun lastName(full: String) = full.substringAfter(' ').ifEmpty { full }

    // ---------- after a game ----------

    fun game(lg: League, g: Game, line: GameLogEntry, won: Boolean, winner: Boolean, stakes: String, duel: Duel?) {
        val h = lg.hero ?: return
        val r = lg.road ?: return
        val f = Form(lg, h, line)
        val t = lg.team(h.teamId)
        val opp = lg.team(g.opp(t.id))
        val us = g.scoreFor(t.id)
        val them = g.scoreAgainst(t.id)
        val box = g.box?.let { if (g.home == t.id) it.home else it.away }.orEmpty()
        val top = box.maxByOrNull { it.s.pts }
        val stat = line(line.pts, line.reb, line.ast)
        val triple = listOf(line.pts, line.reb, line.ast).count { it >= 10 } >= 3
        val big = winner || triple || (line.pts >= 14 && line.pts >= max(f.norm * 1.6, f.norm + 8)) || (line.pts >= 30)
        val bestBefore = careerBest(lg, f)
        val careerHigh = bestBefore >= 0 && line.pts > bestBefore && line.pts >= 12 && (f.before.size >= 4 || h.career.isNotEmpty())
        r.bestPts = max(bestBefore, line.pts)
        val bad = line.min >= 18 && f.norm >= 10 && line.pts <= f.norm * 0.45
        val former = opp.id in f.former
        val teamName = "${t.school} ${t.mascot}"
        val teamHandle = "${t.abbr}${if (lg.pro) "" else "Hoops"}"

        // The team's final: the score, the top scorer if it wasn't him, and his line.
        val lead = if (top != null && top.pid != h.id && top.s.pts > line.pts) "${lastName(top.name)} ${top.s.pts} PTS. " else ""
        Media.post(lg, teamName, teamHandle, "team",
            "FINAL: ${t.mascot} $us, ${opp.mascot} $them. $lead${h.last}: $stat${if (!f.starter) " off the bench" else ""}." + if (stakes.isNotEmpty()) " $stakes." else "",
            Media.likes(lg, 0.004), verified = true, gameId = g.id)

        // The clip pages only post the plays worth a clip.
        val clip = when {
            winner -> "${h.name.uppercase()} WITH THE GAME-WINNER 🚨 ${t.mascot} $us, ${opp.mascot} $them"
            line.pts >= 40 -> "${h.name} went off for ${line.pts} against the ${opp.mascot} 🔥"
            triple -> "${h.name}: ${line.pts}-${line.reb}-${line.ast}. Triple-double vs ${opp.school}."
            careerHigh && line.pts >= 25 -> "Career night for ${h.name}: ${line.pts} points vs ${opp.school}."
            else -> null
        }
        clip?.let { Media.post(lg, "Hoops Highlights", "HoopsHighlights", "media", it, Media.likes(lg, if (winner) 0.06 else 0.03), verified = true, gameId = g.id) }

        // One of his own fans, about this game and who he is.
        val pickLine = when { f.undrafted -> "Undrafted, by the way."; f.pick in 21..60 -> "No. ${f.pick} pick, by the way. ${f.pick - 1} picks went before him."; else -> null }
        val fanText = when {
            winner -> Rng.pick(listOf("${h.last.uppercase()} FOR THE WIN. I'm screaming.", "Ice in his veins. ${h.first} ${h.last}, game-winner.", "Give ${h.last} the ball with the game on the line. Every time."))
            careerHigh -> "Career high for ${h.first}: ${line.pts}." + when {
                f.rookie -> " The ${f.rookieWord} is here."
                !f.starter -> " Off the bench. Put him in the starting five."
                else -> " And he's still getting better."
            }
            big && former -> "${h.last} against his old team: ${line.pts} points. Somebody over there is regretting that decision."
            big && pickLine != null && f.pro && h.proYears <= 2 -> "${line.pts} from ${h.last} tonight. $pickLine"
            big && !f.starter -> "${line.pts} off the bench from ${h.last}. That's a starter. Make it official."
            big && f.rookie && r.feed.none { it.season == lg.season && it.kind == "fan" && (it.text.contains("Rookie of the Year talk") || it.text.contains("A freshman with")) } -> if (f.pro) "${line.pts} from the rookie. Rookie of the Year talk starts now." else "A freshman with ${line.pts}. We're in trouble (everybody else is)."
            big -> Rng.pick(listOf("${h.last} is HIM. $stat.", "Every time somebody doubts ${h.last}, he does this. ${line.pts} tonight.", "Put some respect on ${h.first}'s name. $stat."))
            !won && line.pts >= f.norm + 6 -> Rng.pick(listOf("${h.last} gave us ${line.pts} and we still lost. Get the man some help.", "${line.pts} from ${h.last} wasted. Somebody else has to show up."))
            bad -> Rng.pick(listOf("Not ${h.last}'s night: ${line.pts} points. He'll bounce back.", "${h.last} looked off tonight. Happens.", "${line.pts} points for ${h.last}. Shake it off, kid."))
            // An ordinary night: a fan says something only now and then.
            !Rng.chance(0.35) -> null
            !won -> if (them - us >= 15) Rng.pick(listOf("That was ugly against the ${opp.mascot}. Burn the tape.", "Lost by ${them - us}. I'm going to bed.")) else Rng.pick(listOf("Close one against the ${opp.mascot}. Get it back next time.", "Should've had that one. ${h.last} needs the ball late."))
            line.pts >= f.norm -> Rng.pick(listOf("W. ${h.last} with $stat. Doing his job.", "Quietly solid from ${h.last} again: ${line.pts} points.", "${h.last} giving us ${line.pts} on a normal night. Love to see it.", "Another W. ${h.first} keeps stacking good games."))
            else -> Rng.pick(listOf("Team win. ${top?.takeIf { it.pid != h.id }?.let { "${lastName(it.name)} carried us with ${it.s.pts}." } ?: "Everybody chipped in."}", "W is a W. ${h.last} will have bigger nights.", "Win the game, that's all that matters."))
        }
        fanText?.let { Media.post(lg, Media.fanName(), Media.fanHandle(t), "fan", it, Media.likes(lg, if (big) 0.012 else 0.003)) }

        // Doubters only bother with a player worth doubting.
        // Doubters pile on now and then, not every bad night.
        val lastHate = r.feed.lastOrNull { it.kind == "hater" }
        val hateCooling = lastHate != null && lastHate.season == lg.season && lg.day - lastHate.day < (if (lg.pro) 15 else 6)
        val hater = if (hateCooling) null else when {
            bad && f.pro && f.salary >= 15_000 -> "${Road.dollars(f.salary)} a year for ${line.pts} points. Nice work if you can get it."
            bad && !f.pro && h.nil >= 500 -> "${Road.dollars(h.nil)} in NIL money for ${line.pts} points? Refund."
            bad && f.star -> "${h.last} disappeared tonight. ${line.pts} points in ${line.min} minutes."
            !won && r.persona >= 4 && line.pts < f.norm && Rng.chance(0.3) -> "All that talk from ${h.last} and they lost to the ${opp.mascot}. Let the game do the talking."
            else -> null
        }
        hater?.let { val (n, hd) = Media.hater(); Media.post(lg, n, hd, "hater", it, Media.likes(lg, 0.004)) }

        // An analyst, when there's something to say: the numbers behind a trend, the context of a big night.
        val l5 = f.last5
        val e = f.earlier
        val l3 = f.last3
        // Analysts take a few days between takes, unless something big happened.
        val lastTake = r.feed.lastOrNull { it.kind == "analyst" }
        val takeCooling = lastTake != null && lastTake.season == lg.season && lg.day - lastTake.day < (if (lg.pro) 12 else 5) && !winner && !careerHigh
        val take = if (takeCooling) null else when {
            careerHigh && bestBefore >= 10 && Rng.chance(0.6) -> "Career high for ${h.last}: ${line.pts}, up from ${bestBefore}. ${if (f.rookie) "It's his first year." else "He keeps adding to his game."}"
            big && (opp.ap in 1..10 || (opp.w + opp.l >= 10 && opp.winPct >= 0.62)) -> "${line.pts} against ${if (lg.pro) "the ${opp.name}" else opp.school} (${opp.record}). That's the version of ${h.last} that matters."
            l5 != null && e != null && l5 + line.pts / 5.0 >= e + 6 && l5 >= 13 && Rng.chance(0.5) -> "${h.last} over his last five: ${f1((l5 * 4 + line.pts) / 5)} a game, counting tonight. Before that: ${f1(e)}. That's not noise."
            l3 != null && f.norm >= 12 && l3 <= f.norm * 0.6 && line.pts <= f.norm * 0.6 -> "${h.last} is at ${f1((l3 * 3 + line.pts) / 4)} a game over his last four, down from ${f1(f.norm)}. Teams are sending a second defender at him."
            f.pro && f.rookie && h.stats.gp >= 10 && h.stats.ppg >= 12 && (f.pick >= 10 || f.undrafted) && big -> "${h.last}: ${f1(h.stats.ppg)} a game as a rookie, and he was ${f.pickWords}. Redo the draft and he goes much higher."
            big && r.raceRank in 1..5 && Rng.chance(0.4) -> "${h.last} is No. ${r.raceRank} on my ${f.award} ladder, and nights like this are why."
            else -> null
        }
        take?.let { Media.say(lg, Media.pundit(lg), "analyst", it, Media.likes(lg, 0.006)) }

        // His old team's fans, when he plays them.
        if (former && Rng.chance(0.7)) {
            Media.post(lg, Media.fanName(), Media.fanHandle(opp), "fan", when {
                won && big -> "${h.last} came back and torched us. We let him go for THAT?"
                won -> "${h.last} gets the win in his return. That one stings."
                else -> "${h.last} came back and left with an L. Thanks for the memories."
            }, Media.likes(lg, 0.004))
        }

        // A teammate, after a win.
        if (won && Rng.chance(if (big) 0.6 else 0.15)) {
            lg.rosterOf(t).filter { it.id != h.id }.maxByOrNull { p -> box.firstOrNull { it.pid == p.id }?.s?.pts ?: 0 }?.let { mate ->
                val mateLine = box.firstOrNull { it.pid == mate.id }?.s
                Media.post(lg, mate.name, mate.name.replace(" ", "").replace(".", "").lowercase(), "teammate", when {
                    big -> Rng.pick(listOf("${h.first} cooking. Just get him the ball.", "My brother ${h.last} different. Proud of this group."))
                    mateLine != null && mateLine.pts >= 20 -> "Team win. Locked in. On to the next."
                    else -> "W. Everybody ate tonight."
                }, Media.likes(lg, 0.005), verified = lg.pro)
            }
        }

        // His rival, after they meet (not every game of a playoff series): his side of it, with the head-to-head.
        if (duel != null && (g.type != GameType.PLAYOFF || Rng.chance(0.4))) Rival.player(lg)?.let { rv ->
            val w = r.duels.count { it.won }
            val l = r.duels.size - w
            val tally = when { l > w -> "I lead it $l-$w."; l == w -> "All square at $w-$w."; else -> "He's up $w-$l. Not for long." }
            Media.post(lg, rv.name, rv.name.replace(" ", "").lowercase(), "rival", when {
                !duel.won && duel.him > duel.me -> Rng.pick(listOf("Told y'all. ${duel.him} to ${duel.me}. $tally", "Same result. ${duel.him}-${duel.me}, and the W. $tally"))
                !duel.won -> Rng.pick(listOf("${h.last} got his numbers. I got the W. $tally", "Scoreboard. $tally"))
                duel.him > duel.me -> Rng.pick(listOf("Outscored him and still lost. Need my guys locked in. $tally", "${duel.him} to ${duel.me} and an L. Next time. $tally"))
                else -> Rng.pick(listOf("Good battle. Respect to ${h.last}. $tally", "He got this one. Next one's mine. $tally"))
            }, Media.likes(lg, 0.01), verified = true)
        }
    }

    // ---------- the moments of his career ----------

    /** Called after every day and action: posts for anything new in his career since the last look. */
    fun watch(lg: League) {
        val r = lg.road ?: return
        val h = lg.playerOrNull(lg.heroId) ?: return
        if (r.ended) return
        // First look (or an older save): remember where things stand, post nothing.
        if (r.seenAwards < 0 || r.seenAwards > h.awards.size) r.seenAwards = h.awards.size
        if (r.seenTeam == -2) { r.seenTeam = h.teamId; r.seenPro = lg.pro }
        val deal = r.contract?.let { "${it.teamId}:${it.salary}:${it.signed}" } ?: ""
        if (r.seenDeal.isEmpty() && deal.isNotEmpty() && r.seenPoints < 0) r.seenDeal = deal
        if (r.seenPoints < 0) r.seenPoints = milestone(proPoints(lg, h))

        val f = Form(lg, h, null)
        // A new level or a new team: the draft, a trade, a signing, a transfer.
        if (lg.pro && !r.seenPro && h.teamId >= 0) {
            drafted(lg, h, f)
            r.seenPro = true; r.seenTeam = h.teamId; r.seenDeal = deal
        } else if (h.teamId >= 0 && r.seenTeam != h.teamId) {
            val old = lg.teams.getOrNull(r.seenTeam)
            if (old != null) moved(lg, h, f, old)
            r.seenTeam = h.teamId; r.seenDeal = deal
        } else if (deal.isNotEmpty() && deal != r.seenDeal) {
            r.contract?.let { c -> if (r.seenDeal.isNotEmpty()) extended(lg, h, f, c) }
            r.seenDeal = deal
        }
        // New honors on his record.
        while (r.seenAwards < h.awards.size) {
            val a = h.awards[r.seenAwards]
            r.seenAwards++
            honor(lg, h, f, a.substringAfter(' '), a.substringBefore(' ').toIntOrNull() ?: lg.season)
        }
        // Career points (the pros).
        val m = milestone(proPoints(lg, h))
        if (m > r.seenPoints) {
            r.seenPoints = m
            val n = MILESTONES[m - 1]
            Media.post(lg, "NBA", "NBA", "league", "${h.name} has scored ${"%,d".format(n)} career points.", Media.likes(lg, 0.03), verified = true)
            f.t?.let { t -> Media.post(lg, "${t.school} ${t.mascot}", t.abbr, "team", "${"%,d".format(n)} career points for ${h.first} ${h.last}. 👏", Media.likes(lg, 0.01), verified = true) }
            Media.post(lg, Media.fanName(), f.t?.let { Media.fanHandle(it) } ?: "hoopsfan", "fan", if (n >= 20_000) "${"%,d".format(n)}. ${h.last} is going to the Hall of Fame. Book it." else "${"%,d".format(n)} points for ${h.last}. Watched every one of them.", Media.likes(lg, 0.006))
        }
    }

    private val MILESTONES = listOf(1_000, 5_000, 10_000, 15_000, 20_000, 25_000, 30_000, 35_000, 40_000)
    private fun milestone(points: Int) = MILESTONES.count { points >= it }
    private fun proPoints(lg: League, h: Player): Int {
        if (!lg.pro) return 0
        val done = h.career.filter { it.cls == PRO_YEAR }
        return done.sumOf { it.s.pts } + if (done.none { it.season == lg.season }) h.stats.pts else 0
    }

    /** Draft night (or his first pro team). */
    private fun drafted(lg: League, h: Player, f: Form) {
        val t = f.t ?: return
        val r = f.r
        val college = r.collegeSchools.ifEmpty { h.highSchool }
        val how = when { f.undrafted -> "signs as an undrafted free agent"; f.pick in 1..60 -> "goes No. ${f.pick}"; else -> "joins" }
        Media.post(lg, "${t.school} ${t.mascot}", t.abbr, "team",
            if (f.pick in 1..60) "With the No. ${f.pick} pick, the ${t.name} select ${h.name}${if (college.isNotEmpty()) " from $college" else ""}. Welcome to ${t.school}! 🏀" else "Welcome to ${t.school}, ${h.name}! 🏀",
            Media.likes(lg, 0.02), verified = true)
        Media.say(lg, Media.pundit(lg), "analyst", when {
            f.pick in 1..5 -> "${h.last} ${how} to the ${t.mascot}. Franchise expectations from day one."
            f.pick in 6..20 -> "${h.last} at No. ${f.pick} is good value. The ${t.mascot} needed a ${h.pos.name}."
            f.pick in 21..60 -> "${h.last} ${how}. Teams ahead of the ${t.mascot} passed on him; I wouldn't have."
            else -> "${h.last} ${how} with the ${t.mascot}. Nobody's handing him minutes. He'll have to take them."
        }, Media.likes(lg, 0.01))
        Media.post(lg, Media.fanName(), Media.fanHandle(t), "fan", if (f.pick in 1..14) "We got ${h.last}!! Lottery pick, let's go." else "Welcome ${h.first}. Prove the other teams wrong.", Media.likes(lg, 0.006))
        self(lg, h, if (r.persona >= 4) "Remember the teams that passed. I will. ${t.school}, let's work." else "Dream come true. Thank you, ${college.ifEmpty { "everybody who got me here" }}. ${t.school}, let's work.", 0.04)
    }

    /** A trade, a signing elsewhere, or a transfer. */
    private fun moved(lg: League, h: Player, f: Form, old: Team) {
        val t = f.t ?: return
        val r = f.r
        val traded = lg.pro && r.tradedSeason == lg.season && lg.day - r.tradedDay in 0..3
        val c = r.contract
        val terms = if (lg.pro && c != null && !traded) " (${c.years} ${if (c.years == 1) "year" else "years"}, ${Road.dollars(c.salary)} a season)" else ""
        Media.post(lg, "${t.school} ${t.mascot}", "${t.abbr}${if (lg.pro) "" else "Hoops"}", "team",
            when { traded -> "Welcome to ${t.school}, ${h.name}! 🤝"; lg.pro -> "${h.name} is officially a ${t.mascot}$terms. Welcome to ${t.school}!"; else -> "Welcome to ${t.school}, ${h.name}! The transfer is official." },
            Media.likes(lg, 0.015), verified = true)
        if (!traded) {
            Media.say(lg, Media.insider(lg), "insider", if (lg.pro) "${h.name} has agreed to terms with the ${t.name}$terms, per sources. He leaves the ${old.name}." else "${h.name} is transferring from ${old.school} to ${t.school}, per sources.", Media.likes(lg, 0.02))
        }
        Media.post(lg, Media.fanName(), Media.fanHandle(old), "fan", Rng.pick(listOf(
            "Thank you, ${h.last}. Wish it ended differently.",
            "Can't believe ${h.last} is gone. ${if (h.stats.gp > 0) "${f1(h.stats.ppg)} a game for us and we let him walk." else "Rough day."}",
            "Good luck in ${t.school}, ${h.first}. Except against us.",
        )), Media.likes(lg, 0.005))
        Media.post(lg, Media.fanName(), Media.fanHandle(t), "fan", "Welcome to ${t.school}, ${h.first}! ${if (lg.pro) "Big pickup." else "Huge get."}", Media.likes(lg, 0.005))
        self(lg, h, if (traded) "Thank you ${old.school} for everything. New chapter. ${t.school}, I'm ready." else "New home. Grateful for ${old.school}. Let's get to work, ${t.school}.", 0.03)
    }

    /** A new deal with the same team. */
    private fun extended(lg: League, h: Player, f: Form, c: Contract) {
        val t = f.t ?: return
        Media.post(lg, "${t.school} ${t.mascot}", t.abbr, "team", "${h.name} is staying in ${t.school}. ✍️ ${c.years} ${if (c.years == 1) "year" else "years"}, ${Road.dollars(c.salary)} a season.", Media.likes(lg, 0.015), verified = true)
        Media.say(lg, Media.insider(lg), "insider", "${h.name} and the ${t.name} have agreed to a ${c.years}-year, ${Road.dollars(c.salary * c.years)} deal, per sources.", Media.likes(lg, 0.02))
        Media.post(lg, Media.fanName(), Media.fanHandle(t), "fan", if (c.salary >= 25_000) "Paid the man. Now go get us a ring, ${h.last}." else "Glad ${h.last} is staying. Good deal for everybody.", Media.likes(lg, 0.005))
    }

    /** An honor on his record. */
    private fun honor(lg: League, h: Player, f: Form, code: String, season: Int) {
        // Weekly and monthly honors already have their own post from the league.
        if (code.contains("of the Week") || code.contains("of the Month")) return
        val r = f.r
        val t = f.t
        val label = (if (!lg.pro) Careers.honor(code, h.pos) else null) ?: when {
            code.startsWith("Record: ") -> "the league record for ${code.removePrefix("Record: ").lowercase()}"
            code.startsWith("Draft #") -> return
            code.endsWith(" title") -> "the league lead in ${code.removeSuffix(" title").lowercase()}"
            code.endsWith(" 1st") -> code.removeSuffix(" 1st") + " First Team"
            code.endsWith(" 2nd") -> code.removeSuffix(" 2nd") + " Second Team"
            code.endsWith(" 3rd") -> code.removeSuffix(" 3rd") + " Third Team"
            else -> code
        }
        val allStars = h.awards.count { it.endsWith(" All-Star") }
        val big = code in setOf("MVP", "Finals MVP", "Champion", "Rookie of the Year", "Defensive Player of the Year", "Nat MVP", "MOP", "AA 1st", "All-Star") || code.startsWith("All-NBA 1st") || code.startsWith("Record")
        val text = when {
            code == "All-Star" -> if (allStars == 1) "${h.name} is an All-Star for the first time. 🌟" else "${h.name}: All-Star No. $allStars. 🌟"
            code == "Champion" -> "CHAMPIONS. 🏆 ${h.name} and the ${t?.mascot ?: "team"} win the $season title."
            code == "MVP" -> "${h.name} is the $season MVP. 🏆"
            code.startsWith("Record") -> "${h.name} sets $label."
            code.endsWith(" title") -> "${h.name} finishes the $season season with $label."
            else -> "${h.name}: $season ${label.replaceFirstChar { it.uppercase() }}."
        }
        t?.let { tm -> Media.post(lg, "${tm.school} ${tm.mascot}", "${tm.abbr}${if (lg.pro) "" else "Hoops"}", "team", if (code == "Champion") text else "Congratulations! $text", Media.likes(lg, if (big) 0.02 else 0.008), verified = true) }
        if (big) Media.post(lg, if (lg.pro) "NBA" else "NCAA", if (lg.pro) "NBA" else "NCAA", "league",
            if (code == "Champion") "The ${t?.name ?: "champions"} are the $season ${if (lg.pro) "NBA" else "national"} champions. 🏆" else text, Media.likes(lg, 0.03), verified = true)
        // The take: what it says about his career.
        val take = when {
            code == "All-Star" && allStars == 1 && (f.pick >= 15 || f.undrafted) -> "${h.last} is an All-Star, and he was ${f.pickWords}. Development story of the year."
            code == "All-Star" && allStars == 1 -> "First All-Star nod for ${h.last}, in year ${h.proYears + 1}. ${Awards.line(h)}."
            code == "All-Star" -> "${h.last}'s ${ordinal(allStars)} All-Star selection. He's a fixture now."
            code == "MVP" -> "MVP: ${h.last}. ${Awards.line(h)}. ${if (r.mvps > 1) "That's ${r.mvps} of them." else "First of how many?"}"
            code == "Champion" -> if (r.rings > 1) "${h.last} has ${r.rings} rings now. The legacy conversation is real." else "${h.last} is a champion. Nobody can take that away."
            code == "Rookie of the Year" -> "${h.last} wins Rookie of the Year${if (f.pick >= 10 || f.undrafted) ", and he was ${f.pickWords}" else ""}. ${Awards.line(h)}."
            big -> "Deserved. ${h.last}: ${Awards.line(h)}."
            else -> null
        }
        take?.let { Media.say(lg, Media.pundit(lg), "analyst", it, Media.likes(lg, 0.008)) }
        t?.let { tm -> Media.post(lg, Media.fanName(), Media.fanHandle(tm), "fan", when (code) {
            "Champion" -> "WE ARE CHAMPIONS. ${h.last}, thank you. 🏆"
            "All-Star" -> "All-Star ${h.last}. Told you all."
            else -> "${label.replaceFirstChar { it.uppercase() }}. ${h.last} earned every bit of it."
        }, Media.likes(lg, 0.006)) }
        if (big) self(lg, h, when {
            code == "Champion" -> "Champion. Every early morning was for this. Thank you ${t?.school ?: ""}."
            r.persona >= 4 -> "Told y'all. ${label.replaceFirstChar { it.uppercase() }}. Not done."
            else -> "Grateful. ${label.replaceFirstChar { it.uppercase() }}. Couldn't do it without my teammates and coaches."
        }, 0.03)
        // His rival, for the biggest ones.
        if (big && code != "Champion") Rival.player(lg)?.let { rv -> if (Rng.chance(0.5)) Media.post(lg, rv.name, rv.name.replace(" ", "").lowercase(), "rival", Rng.pick(listOf("Congrats ${h.last}. Enjoy it.", "Respect. Now I'm coming for it.")), Media.likes(lg, 0.01), verified = true) }
    }

    /** A post of his own. */
    private fun self(lg: League, h: Player, text: String, share: Double) {
        Media.post(lg, h.name, h.name.replace(" ", "").replace(".", "").lowercase(), "self", text, Media.likes(lg, share), verified = (lg.road?.followers ?: 0) >= 100_000)
    }

    private fun ordinal(n: Int) = "$n" + when { n % 100 in 11..13 -> "th"; n % 10 == 1 -> "st"; n % 10 == 2 -> "nd"; n % 10 == 3 -> "rd"; else -> "th" }
}
