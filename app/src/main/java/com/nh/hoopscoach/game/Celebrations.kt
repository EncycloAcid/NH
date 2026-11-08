package com.nh.hoopscoach.game

import kotlin.math.roundToInt

/**
 * The nights a career is remembered for, each on a screen of its own (see [Ceremony]): the title
 * parade (with the speech he gives), ring night at the next opener, and awards night when he, or one
 * of his players, takes home a big one.
 */
object Celebrations {

    private fun f1(d: Double) = "%.1f".format(d)
    private fun <T> pick(vararg o: T): T = o[Rng.int(0, o.size)]
    private fun plural(n: Int, one: String, many: String = one + "s") = "$n ${if (n == 1) one else many}"
    private fun reactions(ts: List<Tribute>, lo: Int, hi: Int) = ts.map { Reaction(it.who, it.role, it.text, Rng.range(lo, hi), it.avatar, it.verified) }

    /** Where the parade goes: the NBA city, or the college town. */
    private fun city(lg: League, t: Team) = if (lg.pro) t.school else t.school

    /** Who the user is tonight: the player (player mode) or the coach. */
    private fun me(lg: League): String = lg.hero?.takeIf { lg.road != null }?.name ?: lg.userCoach.name
    private fun playerMode(lg: League) = lg.road != null && lg.hero != null

    // ---------- the parade ----------

    fun parade(lg: League) {
        val t = lg.user
        val pro = lg.pro
        val season = lg.season
        val star = lg.rosterOf(t).maxByOrNull { it.ovr + it.stats.ppg }
        val hero = lg.hero?.takeIf { playerMode(lg) }
        val labels = mutableListOf("RECORD")
        val values = mutableListOf(t.record)
        var finalsLine = ""
        var mvpName = ""
        var runnerUp: Team? = null
        if (pro) {
            val ps = lg.proState
            val mine = ps?.series?.filter { it.round >= 1 && it.involves(t.id) }.orEmpty()
            val w = mine.sumOf { ProSeason.seriesScore(lg, it, t.id).first }
            val l = mine.sumOf { ProSeason.seriesScore(lg, it, t.id).second }
            labels += "PLAYOFFS"; values += "$w-$l"
            mine.firstOrNull { it.round == 4 }?.let { f ->
                val (a, b) = ProSeason.seriesScore(lg, f, t.id)
                runnerUp = lg.team(if (f.hi == t.id) f.lo else f.hi)
                finalsLine = "$a-$b over the ${runnerUp!!.name}"
                labels += "FINALS"; values += "$a-$b"
            }
            lg.seasonAwards.firstOrNull { it.award == "Finals MVP" }?.let { mvpName = it.name; labels += "FINALS MVP"; values += it.name.substringAfterLast(' ') }
        } else {
            val b = lg.ncaa
            b?.seeds?.get(t.id)?.let { labels += "SEED"; values += "No. $it" }
            labels += "IN MARCH"; values += "${(Awards.tourneyRound(lg, t.id) - 1).coerceAtLeast(0)}-0"
            runnerUp = b?.rounds?.getOrNull(5)?.firstOrNull { it >= 0 && it != t.id }?.let { lg.team(it) }
            finalsLine = runnerUp?.let { "over ${it.school} in the final" } ?: ""
            lg.players.firstOrNull { it.awards.contains("$season MOP") }?.let { mvpName = it.name; labels += "MOP"; values += it.last }
        }
        val titles = if (pro) lg.proState?.history?.count { it.champion == t.id }?.plus(1) ?: 1 else t.titles
        if (titles > 1) { labels += "TITLES"; values += "$titles" }

        // The day.
        val crowd = ((if (pro) 400_000 else 60_000) * (0.6 + (t.fans.takeIf { it > 0 } ?: 50.0) / 100.0) / 1000).roundToInt() * 1000
        val town = city(lg, t)
        val story = mutableListOf<String>()
        story += if (pro) pick(
            "The buses rolled out at ten. By noon, downtown $town was a wall of ${t.mascot} colors, an estimated ${"%,d".format(crowd)} people deep, confetti in the trees and on the rooftops.",
            "They started lining the route the night before. When the trophy finally came around the corner on the lead bus, $town lost its mind: an estimated ${"%,d".format(crowd)} people, the biggest crowd the city has seen in years.",
        ) else pick(
            "The team buses crawled across campus and into town behind a marching band, students on every balcony and an estimated ${"%,d".format(crowd)} fans along the way.",
            "Back home, ${t.school} turned out: an estimated ${"%,d".format(crowd)} fans, the band, the cheerleaders and the trophy on the hood of the lead bus.",
        )
        story += buildString {
            append(if (pro) "The ${t.name} won the $season title" else "${t.school} won the $season national title")
            if (finalsLine.isNotEmpty()) append(" $finalsLine")
            append(", ${t.record} on the season.")
            if (mvpName.isNotEmpty()) append(" $mvpName${if (pro) ", the Finals MVP," else ", the Most Outstanding Player,"} rode up front holding the trophy over his head the whole way.")
        }
        story += when {
            titles >= 3 -> "It's title No. $titles. The word nobody in the building will say out loud is the one everybody is thinking: dynasty."
            titles == 2 -> "Their second. The first one was a surprise. This one was a statement."
            else -> "The first one is the one you never forget."
        }
        runCatching { GreatTeams.rankOf(lg, t) }.getOrNull()?.takeIf { it in 1..25 }?.let { story += "Where this team ranks: No. $it on our list of the greatest champions ever." }
        story += "At the stage, the microphone came to ${me(lg)}."

        // The speech.
        val choices = listOf("We're coming back for another one.", "This one is for you, $town.", "I want to thank these guys.")
        val quotes = listOf(
            "Enjoy it tonight. Tomorrow we start chasing the next one. Nobody's taking this from us. We're coming back for another one!",
            "You waited for this. You filled that building every night, you traveled, you never stopped believing. This trophy is yours, $town. It belongs to you!",
            "Look at these guys behind me. Every one of them gave up something for this. I love every one of them. This is their day.",
        )
        val effects = if (hero != null) listOf("Your following grows (+3%); now everyone expects a repeat.", "The city loves you for it (followers +8%).", "Your teammates won't forget it (teammate happiness +5).")
        else listOf("The bosses like the hunger (job security +3).", "The fans eat it up; no change in the building.", "The locker room won't forget it (player happiness +5).")
        val an = Media.pundit(lg)
        val fan = Media.fanName()
        val speaker = star?.takeIf { it.id != hero?.id }
        val choiceReactions = listOf(
            listOf(
                Tribute(an.name, "Analyst", if (titles >= 2) "They're not just saying it. They're built to do it again." else "Love the confidence. Now try doing it with a target on your back all year.", an.avatar, an.verified),
                Tribute(fan, "${t.abbr} fan", "BACK TO BACK. SAY IT WITH ME."),
            ) + listOfNotNull(speaker?.let { Tribute(it.name, "${t.abbr}", "You heard him. Rest up, we're running it back.") }),
            listOf(
                Tribute(fan, "${t.abbr} fan", "I'm not crying, you're crying. Thank you!!!"),
                Tribute(Media.fanName(), "${t.abbr} fan", "Been a fan for 30 years. Worth every single loss."),
                Tribute(an.name, "Analyst", "That's how you win a city for life.", an.avatar, an.verified),
            ),
            listOf(
                Tribute(an.name, "Analyst", "Class. That's why guys want to play there.", an.avatar, an.verified),
                Tribute(fan, "${t.abbr} fan", "The way the whole team mobbed him after that. This group is special."),
            ) + listOfNotNull(speaker?.let { Tribute(it.name, "${t.abbr}", "Love you too. Brothers for life.") }),
        )

        // What people said.
        val trib = mutableListOf<Tribute>()
        trib += Tribute("${Rng.pick(Names.first)} ${Rng.pick(Names.last)}", if (pro) "Owner, ${t.name}" else "Athletic director, ${t.school}",
            if (pro) "We promised this city a champion. These men delivered." else "This is what we built this program for. Thank you to everyone who made it happen.")
        trib += Tribute("Mayor ${Rng.pick(Names.last)}", "Mayor of $town", "Today, $town is the capital of basketball. The key to the city is waiting.")
        if (hero == null) star?.let { trib += Tribute(it.name, "${t.abbr} star", "Coach ${lg.userCoach.last} believed in us before anybody. This ring is for him too.") }
        else trib += Tribute(lg.coachOf(t).name, "Head coach, ${t.abbr}", "${hero.first} carried us when we needed it. Proud of him.")
        runnerUp?.let { r -> trib += Tribute(lg.coachOf(r).name, "Head coach, ${if (pro) r.name else r.school}", "Congratulations to them. They were the better team. We'll be back.") }
        trib += Tribute(if (pro) "NBA" else "NCAA March Madness", "League", "Congratulations to ${if (pro) "the ${t.name}" else t.school}, the $season champions!", verified = true)
        trib += Tribute(Media.fanName(), "${t.abbr} fan", pick("I'll be telling my grandkids about this day.", "Called in sick. Worth it. CHAMPS!", "Nobody believed. We did."))

        val head = if (pro) "The ${t.name} celebrate with a parade through $town" else "${t.school} celebrates its title with a parade"
        lg.news += News(season, lg.day, head, true, -1, "parade", story.joinToString("\n\n"), "Hoops Wire", t.id, -1, reactions(trib, 2_000, 80_000))
        Farewell.show(lg, Ceremony(
            "parade", "$season CHAMPIONS", "CHAMPIONS", if (pro) t.name else "${t.school} ${t.mascot}", t.id, hero?.number ?: 0,
            labels, values, "The parade", story, trib, listOf(head), choices, quotes, choiceReactions,
            choiceTitle = "Your speech", choicePrompt = "The crowd is waiting. What do you say?", effects = effects,
            badge = if (pro) NbaAwards.trophy("Champion", Eras.of(lg)).takeIf { it != "Champion" } ?: "Larry O'Brien Trophy" else "National champions",
        ))
    }

    // ---------- ring night ----------

    fun ringNight(lg: League) {
        val t = lg.user
        val pro = lg.pro
        val last = lg.season - 1
        val opener = lg.games.filter { it.involves(t.id) }.minByOrNull { it.day }?.let { lg.team(it.opp(t.id)) }
        val summary = if (pro) lg.proState?.history?.lastOrNull { it.season == last } else null
        val college = if (!pro) lg.history.lastOrNull { it.season == last } else null
        val lastRec = t.history.lastOrNull { it.season == last }
        val mvp = summary?.finalsMvp?.takeIf { it.isNotEmpty() } ?: lg.players.firstOrNull { it.awards.contains("$last MOP") }?.name ?: ""
        val back = lg.rosterOf(t).filter { p -> p.awards.contains("$last Champion") || (!pro && p.career.any { it.season == last && it.teamId == t.id }) }
        val loudest = lg.rosterOf(t).firstOrNull { it.name == mvp } ?: back.maxByOrNull { it.ovr }
        val lines = mutableListOf<String>()
        lines += "Before the opener" + (opener?.let { " against ${if (pro) "the ${it.name}" else it.school}" } ?: "") +
            ", the lights went down and the $last banner rose to the rafters" + (if ((if (pro) lg.proState?.history?.count { it.champion == t.id } else t.titles) ?: 0 > 1) ", next to the others." else ".")
        lines += "One by one, the rings: ${plural(back.size.coerceAtLeast(1), "player")} still here from the title team" +
            (loudest?.let { ", and the loudest ovation of the night for ${it.name}." } ?: ".")
        lines += pick("Then the hard part: playing a game after that.", "The visitors watched the whole thing from their bench. They'll be getting everybody's best shot all year, too.", "Now everyone's gunning for them.")
        val trib = mutableListOf<Tribute>()
        loudest?.let { trib += Tribute(it.name, "${t.abbr}", "Seeing that banner go up... I got chills. Now let's go get another one.") }
        back.filter { it.id != loudest?.id }.maxByOrNull { it.ovr }?.let { trib += Tribute(it.name, "${t.abbr}", "Ring fits perfect. Time to go to work.") }
        trib += Tribute(Media.fanName(), "${t.abbr} fan", "Got the ring night replica. Framing it. What a night.")
        val labels = listOf("SEASON", "RECORD", "RESULT")
        val values = listOf("$last", lastRec?.let { "${it.w}-${it.l}" } ?: "", if (pro) "Champions" else "National champions")
        val head = if (pro) "Ring night: the ${t.name} raise the $last banner" else "${t.school} raises its $last national championship banner"
        lg.news += News(lg.season, lg.day, head, true, -1, "parade", lines.joinToString("\n\n"), "Hoops Wire", t.id, -1, reactions(trib, 800, 20_000))
        Farewell.show(lg, Ceremony(
            "ring", "OPENING NIGHT · ${lg.season}", "RING NIGHT", if (pro) t.name else "${t.school} ${t.mascot}", t.id, 0,
            labels, values, "The banner", lines, trib, listOf(head),
            badge = college?.let { "$last national champions" } ?: "$last champions",
        ))
    }

    // ---------- awards night ----------

    private class Win(val award: String, val label: String, val pid: Int, val name: String, val teamId: Int, val line: String, val coach: Boolean, val rank: Int)

    private fun proWins(lg: League): List<Win> {
        val order = listOf("MVP", "Coach of the Year", "Defensive Player of the Year", "Rookie of the Year", "Sixth Man of the Year", "Most Improved Player", "Clutch Player of the Year")
        val out = mutableListOf<Win>()
        val hero = lg.heroId.takeIf { playerMode(lg) }
        for (a in lg.seasonAwards) {
            val i = order.indexOf(a.award)
            if (i < 0) continue
            if (if (hero != null) a.pid == hero else a.teamId == lg.userTeamId) out += Win(a.award, a.award, a.pid, a.name, a.teamId, a.line, false, i)
        }
        if (hero == null) NbaAwards.of(lg).firstOrNull { it.award == "Coach of the Year" && it.teamId == lg.userTeamId }?.let {
            out += Win("Coach of the Year", "Coach of the Year", -1, it.name, it.teamId, it.line, true, 1)
        }
        return out.sortedBy { it.rank }
    }

    private fun collegeWins(lg: League): List<Win> {
        val names = mapOf("Nat MVP" to "National Player of the Year", "COTY" to "National Coach of the Year", "DPOY" to "National Defensive Player of the Year", "FOY" to "National Freshman of the Year")
        val order = listOf("Nat MVP", "COTY", "DPOY", "FOY")
        val out = mutableListOf<Win>()
        val hero = lg.heroId.takeIf { playerMode(lg) }
        for (a in lg.seasonAwards) {
            val i = order.indexOf(a.award)
            if (i < 0) continue
            val coach = a.award == "COTY"
            val ours = if (hero != null) !coach && a.pid == hero else a.teamId == lg.userTeamId && (!coach || -a.pid == lg.userCoach.id)
            if (ours) out += Win(a.award, names.getValue(a.award), if (coach) -1 else a.pid, a.name, a.teamId, a.line, coach, i)
        }
        return out.sortedBy { it.rank }
    }

    fun awardsNight(lg: League) {
        val wins = if (lg.pro) proWins(lg) else collegeWins(lg)
        val top = wins.firstOrNull() ?: return
        val t = lg.teams.getOrNull(top.teamId) ?: lg.user
        val pro = lg.pro
        val p = lg.playerOrNull(top.pid)
        val hero = lg.hero?.takeIf { playerMode(lg) }
        // The user gives the speech when the award is his: his own as a player, or Coach of the Year as a coach.
        val mine = (hero != null && top.pid == hero.id) || (hero == null && top.coach)
        val trophy = if (pro) NbaAwards.trophy(top.award, Eras.of(lg)).takeIf { it != top.award } ?: "" else ""
        val labels = mutableListOf<String>()
        val values = mutableListOf<String>()
        if (p != null) {
            labels += listOf("PPG", "RPG", "APG"); values += listOf(f1(p.stats.ppg), f1(p.stats.rpg), f1(p.stats.apg))
        }
        labels += "TEAM"; values += t.record
        val votes = lg.proState?.mvpVotes.orEmpty()
        if (top.award == "MVP" && votes.isNotEmpty()) votes.firstOrNull { it.pid == top.pid }?.let { v ->
            labels += "1ST-PLACE"; values += "${v.firsts}"
            labels += "POINTS"; values += "${v.points}"
        }
        if (wins.size > 1) { labels += "AWARDS"; values += "${wins.size}" }

        // The envelope.
        val env = mutableListOf<String>()
        val finalists: List<String> = when {
            top.award == "MVP" && votes.size >= 3 -> votes.take(3).map { it.name }
            top.coach -> emptyList()
            else -> {
                val ladder = Awards.ladder(lg)
                when (top.award) {
                    "Defensive Player of the Year", "DPOY" -> ladder.take(80).sortedByDescending { Awards.defScore(it.first) }.take(3)
                    "Rookie of the Year" -> ladder.filter { it.first.proYears == 0 }.take(3)
                    "FOY" -> ladder.filter { it.first.year == 1 }.take(3)
                    else -> ladder.take(3)
                }.map { it.first.name }.let { if (top.name !in it) (listOf(top.name) + it).take(3) else it }
            }
        }
        env += "${if (pro) "The league" else "The voters"} announced the ${top.label}" + (trophy.takeIf { it.isNotEmpty() }?.let { ", the $it," } ?: "") + " tonight." +
            (if (finalists.size >= 3) " The finalists: ${finalists.joinToString(", ")}." else "")
        env += "The envelope, please. " + pick("And the winner is", "The ${top.label} goes to", "This year's ${top.label}:") + " ${top.name}" +
            (if (top.coach) " of ${if (pro) "the ${t.name}" else t.school}" else "") + "."
        env += if (top.coach) "${t.record}: ${top.line.substringAfter(" · ", "").ifEmpty { "a season nobody saw coming" }}."
        else "${top.line}" + (if (top.award == "MVP") votes.firstOrNull { it.pid == top.pid }?.let { ", with ${it.firsts} first-place votes" } ?: "" else "") + "."
        if (wins.size > 1) env += "And not the only one for ${if (pro) "the ${t.name}" else t.school} tonight: " + wins.drop(1).joinToString(", ") { "${it.name}, ${it.label}" } + "."
        if (!mine) env += "${top.name} took the stage: \"" + when {
            hero == null && !top.coach -> "I want to thank my teammates and Coach ${lg.userCoach.last}. He believed in me before anybody did. This is ours."
            else -> "I can't do this without the guys in that locker room. Thank you."
        } + "\""

        // His speech, when it's his.
        val choices = if (mine) listOf("Thank the team.", "To everyone who doubted me.", "For my family.") else emptyList()
        val quotes = if (mine) listOf(
            "This award has my name on it, but it belongs to everybody in that locker room. I don't win anything without them.",
            "There were a lot of people who said I couldn't. I heard every one of them. I kept every one of those. This is for you.",
            "Mom, Dad: every early morning, every long drive, every game you sat through. This is yours.",
        ) else emptyList()
        val effects = if (mine) listOf(if (top.coach) "The players hear it (player happiness +4)." else "Your teammates hear it (teammate happiness +4).", "The doubters get a reply (followers +6%).", "No effect. Just the right thing to say.") else emptyList()
        val an = Media.pundit(lg)
        val mate = lg.rosterOf(t).filter { it.id != top.pid }.maxByOrNull { it.ovr }
        val choiceReactions = if (mine) listOf(
            listOfNotNull(mate?.let { Tribute(it.name, "${t.abbr}", "That's why we run through walls for him.") }, Tribute(an.name, "Analyst", "Humble. Deserved.", an.avatar, an.verified)),
            listOf(Tribute(an.name, "Analyst", "Petty. I love it.", an.avatar, an.verified), Tribute(Media.fanName(), "Fan", "He remembered EVERY ONE of them 😂")),
            listOf(Tribute(Media.fanName(), "Fan", "Not a dry eye in the building."), Tribute(an.name, "Analyst", "The best part of awards night.", an.avatar, an.verified)),
        ) else emptyList()

        val trib = mutableListOf<Tribute>()
        mate?.let { trib += Tribute(it.name, "Teammate, ${t.abbr}", if (top.coach) "Coach deserves it. Nobody picked us. He did." else pick("Told y'all all season. Well deserved, ${top.name.substringBefore(' ')}.", "Nobody worked harder. Congrats bro.")) }
        if (!top.coach && hero != null) trib += Tribute(lg.coachOf(t).name, "Head coach, ${t.abbr}", "He earned every vote.")
        if (top.award == "MVP" && votes.size >= 2) votes.firstOrNull { it.pid != top.pid }?.let { trib += Tribute(it.name, "Runner-up", "Congrats to him. Great season. I'll see him in the playoffs.") }
        trib += Tribute(an.name, "Analyst", when (top.award) {
            "MVP", "Nat MVP" -> "No debate. The best player on ${if (pro) "a" else "a"} ${t.record} team. ${top.name} earned it."
            "Coach of the Year", "COTY" -> "${t.record}. Nobody saw it coming except the guy on the sideline."
            else -> "Deserved. ${top.name} was the clear choice."
        }, an.avatar, an.verified)
        trib += Tribute(if (pro) "NBA" else "NCAA", "League", "Congratulations to ${top.name}, the ${lg.season} ${top.label}.", verified = true)
        trib += Tribute(Media.fanName(), "${t.abbr} fan", pick("MY GUY!!! Deserved!", "Robbery if it was anyone else. Glad they got it right.", "Frame this one."))

        val head = "${top.name} wins ${top.label}" + (trophy.takeIf { it.isNotEmpty() }?.let { " ($it)" } ?: "")
        lg.news += News(lg.season, lg.day, head, true, -1, "award", env.joinToString("\n\n"), "Hoops Wire", t.id, top.pid, reactions(trib, 1_000, 60_000))
        Farewell.show(lg, Ceremony(
            "awards", "AWARDS NIGHT · ${lg.season}", "THE ${top.label.uppercase()}", "${top.name} · ${if (pro) t.name else t.school}", t.id,
            p?.number ?: 0, labels, values, "The envelope", env, trib, listOf(head) + wins.drop(1).map { "${it.name}: ${it.label}" },
            choices, quotes, choiceReactions, choiceTitle = "Your speech", choicePrompt = "The trophy is in your hands. What do you say?", effects = effects,
            badge = trophy,
        ))
    }

    // ---------- the Hall of Fame ----------

    /** Classmates for an induction: this year's inductees in the league, else retired greats, else made-up names. */
    private fun classmates(lg: League, me: String): List<String> {
        val here = lg.hallOfFame.filter { it.season == lg.season && it.name != me }.map { it.name }
        val frozen = lg.proState?.frozen?.keys?.filter { it != me }?.shuffled(kotlin.random.Random(lg.season.toLong()))?.take(3).orEmpty()
        return (here + frozen).distinct().take(3).ifEmpty { List(3) { "${Rng.pick(Names.first)} ${Rng.pick(Names.last)}" } }
    }

    private val HOF_CHOICES = listOf("Thank everyone who got me here.", "Talk about where it all started.", "Settle a few old scores.")
    private fun hofEffects(lg: League) = if (lg.road == null) listOf("Recruits are watching (recruiting +2).", "Your players love it (player happiness +4).", "Your bosses like the fire (job security +2).")
    else listOf("", "", "")

    /** His Hall of Fame night as a player: the presenter, the career, his speech. */
    fun hallOfFame(lg: League, pa: PlayedAs) {
        val year = pa.hofClass.takeIf { it > 0 } ?: lg.season
        val teams = pa.proTeams
        // His presenter: an old teammate still around, or a legend.
        val mate = lg.players.firstOrNull { p -> p.name != pa.name && p.career.any { l -> l.cls == PRO_YEAR && lg.teams.getOrNull(l.teamId)?.name in teams } }?.name
        val presenter = mate ?: "${Rng.pick(Names.first)} ${Rng.pick(Names.last)}"
        val labels = mutableListOf("SEASONS")
        val values = mutableListOf("${pa.proSeasons}")
        if (pa.rings > 0) { labels += "RINGS"; values += "${pa.rings}" }
        if (pa.mvps > 0) { labels += "MVP"; values += "${pa.mvps}" }
        if (pa.allStars > 0) { labels += "ALL-STAR"; values += "${pa.allStars}" }
        if (pa.allTime > 0) { labels += "ALL-TIME"; values += "No. ${pa.allTime}" }
        val story = mutableListOf<String>()
        story += "Springfield, Massachusetts. The class of $year walks in to a standing ovation: ${pa.name}, with ${classmates(lg, pa.name).joinToString(", ")}."
        story += "${pa.name}: ${pa.college.ifEmpty { "college" }}, then ${pa.draft.lowercase().replaceFirstChar { it.uppercase() }}. " +
            (if (teams.isNotEmpty()) "${plural(pa.proSeasons, "season")} with ${teams.joinToString(", ")}. " else "") + pa.proLine.ifEmpty { pa.collegeLine } + "."
        if (pa.honors.isNotEmpty()) story += "The résumé: ${pa.honors.joinToString(", ")}. ${pa.verdict}."
        story += "$presenter walks him to the podium" + (if (mate != null) ", the way he walked him onto the floor so many nights." else ".") + " The jacket goes on. The room goes quiet."
        val quotes = listOf(
            "Nobody gets here alone. My coaches, my teammates, every trainer who taped an ankle at six in the morning, and my family, who sat through every one of them. This is yours.",
            "There was a gym and a kid who wouldn't go home. If you're that kid tonight: stay a little longer. It's worth it.",
            "A lot of people said I'd never be here. I wrote every one of their names down. I don't need the list anymore. But I kept it.",
        )
        val an = Media.pundit(lg)
        val reactions = listOf(
            listOf(Tribute(an.name, "Analyst", "Grace. That's how you give a Hall of Fame speech.", an.avatar, an.verified), Tribute(Media.fanName(), "Fan", "Crying at a speech again. Thank you.")),
            listOf(Tribute(an.name, "Analyst", "Every kid in a gym somewhere just got a little taller.", an.avatar, an.verified), Tribute(Media.fanName(), "Fan", "\"Stay a little longer.\" Chills.")),
            listOf(Tribute(an.name, "Analyst", "Petty. Legendary. Both can be true.", an.avatar, an.verified), Tribute(Media.fanName(), "Fan", "HE KEPT THE LIST 😂😂")),
        )
        val trib = mutableListOf(
            Tribute(presenter, "His presenter", "Proudest night of my life, and it isn't even mine."),
            Tribute(if (lg.pro) "NBA" else "NCAA", "League", "Congratulations to Hall of Famer ${pa.name}.", verified = true),
            Tribute(Media.fanName(), "Fan", "Hall of Famer. Say it again."),
        )
        val head = "${pa.name} enters the Hall of Fame, class of $year"
        lg.news += News(lg.season, lg.day, head, true, -1, "award", story.joinToString("\n\n"), "Hoops Wire", -1, -1, reactions(trib, 2_000, 70_000))
        Farewell.show(lg, Ceremony(
            "hof", "CLASS OF $year", "HALL OF FAMER", pa.name, -1, 0, labels, values, "Enshrinement night", story, trib, listOf(head),
            HOF_CHOICES, quotes, reactions, choiceTitle = "Your induction speech", choicePrompt = "The jacket is on. The room is waiting.", effects = hofEffects(lg),
            badge = "Basketball Hall of Fame",
        ))
    }

    /** The coach's own induction, once: two titles, or 500 wins over 15 seasons. Checked at season's end. */
    fun coachHallOfFame(lg: League) {
        if (lg.road != null) return
        val c = runCatching { lg.userCoach }.getOrNull() ?: return
        if (c.hofSeason > 0) return
        val titles = c.history.count { it.result == "National Champion" || it.result == "Champion" }
        val wins = c.history.sumOf { it.w }
        if (titles < 2 && !(wins >= 500 && c.history.size >= 15)) return
        c.hofSeason = lg.season
        val losses = c.history.sumOf { it.l }
        val stops = c.history.map { it.team }.distinct()
        val labels = listOf("SEASONS", "RECORD", "TITLES", "FINAL FOURS")
        val values = listOf("${c.history.size}", "$wins-$losses", "$titles", "${c.history.count { it.result in setOf("Final Four", "Runner-Up", "National Champion") }}")
        val story = mutableListOf<String>()
        story += "The call came in the summer: ${c.name} is going into the Hall of Fame as a coach, class of ${lg.season}, with ${classmates(lg, c.name).joinToString(", ")}."
        story += "${plural(c.history.size, "season")} on the sideline at ${stops.joinToString(", ")}: $wins wins, ${plural(titles, "title")}."
        c.history.filter { it.result == "National Champion" || it.result == "Champion" }.takeIf { it.isNotEmpty() }?.let { t ->
            story += "The titles: " + t.joinToString(", ") { "${it.season} with ${it.team} (${it.w}-${it.l})" } + "."
        }
        lg.playedAs?.takeIf { it.inducted }?.let { story += "He's already in as a player. Now he's in twice." }
        story += "His former players line the front rows."
        val quotes = listOf(
            "Coaches don't win games. Players do. Every one of you in those front rows: this is your night.",
            "I was a kid with a whistle and a lot of ideas, and somebody gave me a chance. To every young coach out there: keep showing up.",
            "Some people told me I'd never last in this business. They're not here tonight. I am.",
        )
        val an = Media.pundit(lg)
        val reactions = listOf(
            listOf(Tribute(an.name, "Analyst", "That's why players wanted to play for him.", an.avatar, an.verified)),
            listOf(Tribute(an.name, "Analyst", "A love letter to coaching.", an.avatar, an.verified)),
            listOf(Tribute(an.name, "Analyst", "Coach still has that edge. Good.", an.avatar, an.verified)),
        )
        val trib = listOfNotNull(
            lg.rosterOf(lg.user).maxByOrNull { it.ovr }?.let { Tribute(it.name, "His player", "Hall of Fame coach. We knew.") },
            Tribute(Media.fanName(), "Fan", "Put him in twice if you have to."),
        )
        val head = "${c.name} elected to the Hall of Fame as a coach"
        lg.news += News(lg.season, lg.day, head, true, -1, "award", story.joinToString("\n\n"), "Hoops Wire", lg.userTeamId, -1, reactions(trib, 1_000, 40_000))
        Farewell.show(lg, Ceremony(
            "hofcoach", "CLASS OF ${lg.season}", "HALL OF FAME COACH", c.name, lg.userTeamId, 0, labels, values, "Enshrinement night", story, trib, listOf(head),
            HOF_CHOICES, quotes, reactions, choiceTitle = "Your induction speech", choicePrompt = "The jacket is on. Your players are in the front row.", effects = hofEffects(lg),
            badge = "Basketball Hall of Fame",
        ))
    }

    // ---------- the speeches ----------

    fun speak(lg: League, cer: Ceremony, choice: Int) {
        cer.chosen = choice
        val r = lg.road
        val hero = lg.hero?.takeIf { playerMode(lg) }
        val t = lg.user
        fun follow(pct: Double) { r?.let { it.followers = (it.followers * (1 + pct)).roundToInt() } }
        fun happy(n: Int, skip: Int = -1) = lg.rosterOf(t).forEach { if (it.id != skip) it.happiness = (it.happiness + n).coerceAtMost(99) }
        when (cer.kind) {
            "parade" -> when (choice) {
                0 -> if (hero != null) follow(0.03) else lg.userCoach.security = (lg.userCoach.security + 3).coerceIn(0, 100)
                1 -> if (hero != null) follow(0.08)
                else -> happy(5, hero?.id ?: -1)
            }
            "awards" -> when (choice) {
                0 -> happy(4, hero?.id ?: -1)
                1 -> follow(0.06)
                else -> {}
            }
            "hof", "hofcoach" -> if (lg.road == null) runCatching { lg.userCoach }.getOrNull()?.let { c ->
                when (choice) {
                    0 -> c.rec = (c.rec + 2).coerceAtMost(99)
                    1 -> happy(4)
                    else -> c.security = (c.security + 2).coerceIn(0, 100)
                }
            }
        }
        val who = me(lg)
        lg.news += News(lg.season, lg.day, "$who: \"${cer.choices[choice].trimEnd('.')}\"", true, -1, if (cer.kind == "parade") "parade" else "award",
            "\"${cer.choiceQuotes.getOrNull(choice).orEmpty()}\"", "Hoops Wire", cer.teamId, hero?.id ?: -1,
            reactions(cer.choiceReactions.getOrNull(choice).orEmpty(), 500, 30_000))
    }
}
