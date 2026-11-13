package com.nh.hoopscoach.game

import kotlinx.serialization.Serializable

/** Someone's word on the moment: a teammate, a coach, the rival, an analyst, a fan. */
@Serializable
class Tribute(val who: String, val role: String, val text: String, val avatar: String = "", val verified: Boolean = false)

/**
 * A moment big enough to stop the game: his retirement (a farewell letter, the career in numbers, the
 * tributes and the coverage) or his introduction as a head coach (the announcement, the résumé and the
 * opening statement he chooses, which the room reacts to).
 */
@Serializable
class Ceremony(
    /** "retire" or "hire". */
    val kind: String,
    val kicker: String,
    val title: String,
    val sub: String,
    /** The team whose colors the moment wears (-1: none). */
    val teamId: Int,
    val number: Int = 0,
    val statLabels: List<String> = emptyList(),
    val statValues: List<String> = emptyList(),
    val letterTitle: String = "",
    val letter: List<String> = emptyList(),
    val tributes: List<Tribute> = emptyList(),
    /** Headlines that ran. */
    val coverage: List<String> = emptyList(),
    /** For a hire: the opening statements to choose from, and how the room takes each one. */
    val choices: List<String> = emptyList(),
    val choiceQuotes: List<String> = emptyList(),
    val choiceReactions: List<List<Tribute>> = emptyList(),
    var chosen: Int = -1,
    /** The choice section's title and prompt, and what each choice does (empty: the press conference's). */
    val choiceTitle: String = "",
    val choicePrompt: String = "",
    val effects: List<String> = emptyList(),
    /** A line under the title: a trophy's name, a banner. */
    val badge: String = "",
)

object Farewell {

    private fun f1(d: Double) = "%.1f".format(d)
    private fun <T> pick(vararg o: T): T = o[Rng.int(0, o.size)]
    private fun plural(n: Int, one: String, many: String = one + "s") = "$n ${if (n == 1) one else many}"

    // ---------- retirement ----------

    /** He's retiring: the letter, the numbers, the tributes and the coverage. Call after [ProCareer.retire] has done the rest. */
    fun retire(lg: League) {
        val r = lg.road ?: return
        val h = lg.hero ?: return
        val pa = Lifetime.fromPlayer(lg)
        val last = lg.teams.getOrNull(h.lastTeam)
        val pro = h.career.filter { it.cls == PRO_YEAR }
        val tot = StatLine().also { t -> pro.forEach { t.add(it.s) } }
        val seasons = pro.size
        val gp = tot.gp.coerceAtLeast(1)
        val ppg = tot.pts.toDouble() / gp
        val rpg = tot.reb.toDouble() / gp
        val apg = tot.ast.toDouble() / gp
        val tier = Lifetime.name(pa)
        val rank = AllTime.rank(lg)
        val rival = Rival.player(lg)
        val teams = r.proTeams.distinct().mapNotNull { lg.teams.getOrNull(it) }
        val first = teams.firstOrNull()
        val ringYears = h.awards.filter { it.endsWith(" Champion") }.mapNotNull { it.substringBefore(" ").toIntOrNull() }
        val best = pro.maxByOrNull { it.s.ppg }
        val bigNight = r.reel.filter { it.big }.maxByOrNull { it.line.substringBefore(" pts").toIntOrNull() ?: 0 }
        val draftYear = r.draftSeason.takeIf { it > 0 }
        val college = r.collegeSchools.substringBefore(" →").trim()
        val verdict = ProCareer.hofVerdict(lg)

        // The numbers.
        val labels = mutableListOf("SEASONS", "POINTS", "PPG", "RPG", "APG")
        val values = mutableListOf("$seasons", "%,d".format(tot.pts), f1(ppg), f1(rpg), f1(apg))
        if (r.rings > 0) { labels += "RINGS"; values += "${r.rings}" }
        if (r.allStars > 0) { labels += "ALL-STAR"; values += "${r.allStars}" }
        if (r.mvps > 0) { labels += "MVP"; values += "${r.mvps}" }
        if (rank > 0) { labels += "ALL-TIME"; values += "No. $rank" }

        // His letter.
        val letter = mutableListOf<String>()
        letter += pick(
            "It started in ${h.hometown}. A hoop with no net, a ball that wouldn't hold air, and a kid who wouldn't go inside when it got dark.",
            "Before any of this, there was a gym in ${h.hometown} that stayed open late because somebody left the side door unlocked. I was in there every night.",
            "I don't remember the first basket I ever made. I remember the thousands I missed in ${h.hometown}, and how badly I wanted the next one.",
        ) + if (college.isNotEmpty()) " Then $college took a chance on me, and I've been trying to pay them back ever since." else ""
        letter += when {
            draftYear != null && r.draftPick in 1..60 && first != null ->
                "Draft night, $draftYear: the ${first.name} called my name at No. ${r.draftPick}. " + when {
                    r.draftPick <= 3 -> "Everybody expected it. I still couldn't breathe."
                    r.draftPick <= 14 -> "I hugged everybody at the table twice."
                    r.draftPick <= 30 -> "I told myself the 29 teams that passed would hear about it."
                    else -> "Second round. I kept that draft card in my locker for $seasons years."
                }
            first != null -> "Nobody called my name on draft night. The ${first.name} gave me a camp invite and a locker by the door. I never gave it back."
            else -> "Nobody handed me anything, and I wouldn't have wanted it any other way."
        }
        letter += buildString {
            when {
                ringYears.isNotEmpty() -> {
                    val y = ringYears.first()
                    val with = pro.firstOrNull { it.season == y }?.teamId?.let { lg.teams.getOrNull(it)?.name }
                    append("Nothing will ever top $y" + (with?.let { " with the $it" } ?: "") + ". The confetti, the locker room, my family in the tunnel. ")
                    if (ringYears.size > 1) append("We did it ${plural(ringYears.size, "time")}, and every one felt like the first. ")
                }
                best != null && best.s.ppg >= 18 -> append("The ${best.season} season with the ${best.team.let { abbr -> lg.teams.firstOrNull { it.abbr == abbr }?.name ?: abbr }}, ${f1(best.s.ppg)} a night, is the one I'll tell my grandkids about. ")
                else -> append("I wasn't always the star. I was the guy who showed up, guarded the best player and made the extra pass. I'm proud of that. ")
            }
            bigNight?.let { append("And ${it.title.replaceFirstChar { c -> c.lowercase() }}: I still watch that one sometimes. ") }
        }.trim()
        if (teams.isNotEmpty()) letter += "To the fans in " + teams.joinToString(", ") { it.name.substringBeforeLast(' ') } +
            ": you made every road trip feel like it was worth coming home for. Thank you for letting me be part of your city."
        rival?.let { letter += "To ${it.name}: you made me better. I hated every minute of it. Thank you." }
        letter += when (tier) {
            2 -> "I gave this game everything I had for ${plural(seasons, "season")}, and it gave me more than I ever deserved. It's somebody else's turn now. I'll be watching."
            1 -> "${plural(seasons, "season")}. More than the kid in ${h.hometown} ever dreamed. It's time."
            else -> "${plural(seasons, "season")} in the league. Every one of them was a gift. I'm walking away grateful."
        } + "\n— ${h.first}"
        val letterTitle = pick("Dear basketball", "To everyone who was there", "One last thing", "Thank you")

        // The tributes.
        val trib = mutableListOf<Tribute>()
        val ins = Media.insider(lg)
        trib += Tribute(ins.name, "Insider", "${h.name} is retiring after ${plural(seasons, "season")}, he told the ${last?.name ?: "league"} today." +
            (if (r.rings > 0) " ${plural(r.rings, "ring")}" + (if (r.allStars > 0) ", ${plural(r.allStars, "All-Star selection")}." else ".") else ""), ins.avatar, ins.verified)
        last?.let { t ->
            lg.rosterOf(t).maxByOrNull { it.ovr }?.let { mate ->
                trib += Tribute(mate.name, "Teammate, ${t.name}", pick(
                    "Every day in practice he made me better. Locker next to mine is going to feel empty. Love you, ${h.first}.",
                    "Taught me how to be a pro. How to eat, how to prepare, how to carry yourself. Forever grateful.",
                    "First one in, last one out. Every single day. That's my vet. Enjoy it, ${h.first}.",
                ))
            }
            trib += Tribute(lg.coachOf(t).name, "Head coach, ${t.name}", when (tier) {
                2 -> "You coach a lot of players. Once or twice, if you're lucky, you get one like ${h.first}. I was lucky."
                1 -> "${h.first} was the heartbeat of our locker room. We'll miss the player. We'll miss the person more."
                else -> "Pros like ${h.first} are why teams win. Did everything we asked and never once complained about it."
            })
        }
        teams.filter { it.id != last?.id }.take(2).forEach { t ->
            val acct = SocialAccounts.forTeam(lg, t.name)
            trib += Tribute(acct?.name ?: t.name, "Former team", "Thank you, ${h.first}. Always a part of this franchise. No. ${h.number} forever.", acct?.avatar ?: "", acct?.verified ?: true)
        }
        rival?.let { rv -> trib += Tribute(rv.name, "His rival", pick(
            "We went at it since we were kids. Nobody pushed me harder. Respect, ${h.first}. Always.",
            "I'm not going to lie and say I liked guarding him. But the game's going to miss him. So will I.",
        )) }
        val an = Media.pundit(lg)
        trib += Tribute(an.name, "Analyst", when {
            verdict.contains("first ballot", ignoreCase = true) || tier == 2 -> "${h.name} retires as one of the greats. ${if (rank > 0) "No. $rank on the all-time list. " else ""}First ballot. Not a debate."
            r.rings > 0 -> "${plural(r.rings, "ring")} and a career anybody would sign up for. $verdict."
            else -> "${h.name}: ${f1(ppg)} points over ${plural(seasons, "season")}. Never the headline, always the reason. $verdict."
        }, an.avatar, an.verified)
        trib += Tribute(if (lg.pro) "The Commissioner" else "NCAA", "League", "On behalf of the entire league, congratulations to ${h.name} on a remarkable career. The game is better because he played it.")
        last?.let { t -> trib += Tribute(Media.fanName(), "${t.name} fan", pick(
            "Crying in the parking lot. Thank you for everything No. ${h.number}.",
            "Bought my first jersey with his name on it. Not taking it off.",
            "Some guys play for a city. He played like he was from here. Thank you.",
        )) }

        // The coverage.
        val coverage = mutableListOf<String>()
        val head = "${h.name} retires after ${plural(seasons, "season")}" + when {
            r.rings > 0 -> ", with ${plural(r.rings, "ring")}"
            r.allStars > 0 -> ", a ${plural(r.allStars, "time")} All-Star".replace("a 1 time All-Star", "an All-Star")
            else -> ""
        }
        val body = mutableListOf<String>()
        body += "${h.name} announced his retirement on ${letterTitle.lowercase().let { "a letter titled \"$letterTitle\"" }}, ending a ${plural(seasons, "season")} career " +
            (if (teams.size > 1) "with ${teams.size} teams (${teams.joinToString(", ") { it.name }})" else last?.let { "spent entirely with the ${it.name}" } ?: "") + "."
        body += "He finishes with ${"%,d".format(tot.pts)} points in ${plural(tot.gp, "game")}: ${f1(ppg)} points, ${f1(rpg)} rebounds and ${f1(apg)} assists a game." +
            (pa?.honors?.takeIf { it.isNotEmpty() }?.let { " Honors: ${it.joinToString(", ")}." } ?: "")
        if (draftYear != null && r.draftPick > 0) body += "The ${first?.name ?: "league"} took him No. ${r.draftPick} in $draftYear" + (if (college.isNotEmpty()) " out of $college" else "") + "."
        body += "$verdict." + (if (rank > 0) " He retires No. $rank on the all-time list." else "")
        if (r.jerseys.isNotEmpty()) body += r.jerseys.joinToString(". ") + "."
        val main = News(lg.season, lg.day, head, true, -1, "retire", body.joinToString("\n\n"), "Hoops Wire", last?.id ?: -1, h.id,
            trib.map { Reaction(it.who, it.role, it.text, Rng.range(2_000, 90_000), it.avatar, it.verified) })
        lg.news += main
        coverage += head
        if (r.jerseys.isNotEmpty()) for (j in r.jerseys) {
            lg.news += News(lg.season, lg.day, "$j for ${h.name}", true, -1, "retire", "", "", lg.teams.firstOrNull { j.startsWith("The ${it.name} ") }?.id ?: -1, h.id)
            coverage += "$j for ${h.name}"
        }
        if (rank in 1..50 || tier == 2) {
            val col = "Where ${h.name} ranks among the all-time greats"
            lg.news += News(lg.season, lg.day, col, true, -1, "column",
                (if (rank > 0) "By our all-time list, ${h.name} retires No. $rank. " else "") + "$verdict. " +
                    (Afterward.highlights(lg).takeIf { it.isNotEmpty() }?.let { "The case: ${it.joinToString("; ")}." } ?: ""), "Hoops Wire", last?.id ?: -1, h.id)
            coverage += col
        }
        // The feed.
        Media.post(lg, ins.name, ins.handle, "insider", trib.first().text, Media.likes(lg, 0.6), ins.verified, avatar = ins.avatar, league = true)
        last?.let { t -> Media.post(lg, t.name, t.abbr.lowercase(), "team", "Thank you, ${h.first}. 🧡 No. ${h.number}", Media.likes(lg, 0.5), true, league = true) }
        Media.post(lg, if (lg.pro) "NBA" else "NCAA", "nba", "league", "Congratulations to ${h.name} on a ${plural(seasons, "season")} career.", Media.likes(lg, 0.7), true, league = true)

        show(lg, Ceremony(
            "retire", "AFTER ${plural(seasons, "SEASON").uppercase()}", "THANK YOU, ${h.last.uppercase().ifEmpty { h.name.uppercase() }}",
            "${h.name} · ${h.posLabel} · ${teams.joinToString(" · ") { it.abbr }}", last?.id ?: -1, h.number,
            labels, values, letterTitle, letter, trib, coverage,
        ))
    }

    // ---------- the introduction ----------

    /**
     * He's the new head coach of [lg]'s user team: the announcement ([headline]), his résumé, and the
     * press conference, where he picks his opening statement.
     */
    fun hire(lg: League, headline: String) {
        // A new job, a new contract.
        runCatching { CoachContracts.sign(lg) }
        val t = lg.user
        val c = lg.userCoach
        val pa = lg.playedAs
        val pro = lg.pro
        val team = if (pro) "the ${t.name}" else t.school
        val Team = team.replaceFirstChar { it.uppercase() }
        val boss = "${Rng.pick(Names.first)} ${Rng.pick(Names.last)}"
        val bossRole = if (pro) "general manager" else "athletic director"
        val lastYear = t.history.lastOrNull()

        // The résumé.
        val labels = mutableListOf<String>()
        val values = mutableListOf<String>()
        if (c.history.isNotEmpty()) {
            labels += "SEASONS"; values += "${c.history.size}"
            labels += "RECORD"; values += "${c.history.sumOf { it.w }}-${c.history.sumOf { it.l }}"
            val f4 = c.history.count { it.result in setOf("Final Four", "Runner-Up", "National Champion") }
            if (f4 > 0) { labels += "FINAL FOURS"; values += "$f4" }
            val titles = c.history.count { it.result == "National Champion" || it.result == "Champion" }
            if (titles > 0) { labels += "TITLES"; values += "$titles" }
        }
        if (pa != null && pa.proSeasons > 0) {
            labels += "PRO SEASONS"; values += "${pa.proSeasons}"
            if (pa.rings > 0) { labels += "RINGS"; values += "${pa.rings}" }
            if (pa.allStars > 0) { labels += "ALL-STAR"; values += "${pa.allStars}" }
        }
        if (labels.isEmpty()) { labels += "AGE"; values += "${c.age}" }

        // The introduction: what the boss says, and what the job is.
        val intro = mutableListOf<String>()
        intro += "$boss, the $bossRole, opens it: \"" + when {
            pa != null && pa.rings > 0 && c.history.isEmpty() -> "He's won at the highest level. He knows what it takes, and he knows how to teach it. We think he's going to be a great coach."
            pa != null && c.history.isEmpty() -> "We've watched ${c.first} compete for years. Our players are going to want to run through a wall for him."
            c.history.any { it.result == "National Champion" || it.result == "Champion" } -> "He's won a championship. That's not a small thing. He's the right man to lead $team."
            c.history.isNotEmpty() -> "He built a winner everywhere he's been. We did our homework, and every road led back to ${c.first}."
            else -> "We talked to a lot of people. ${c.first} was the one we couldn't stop thinking about."
        } + "\""
        intro += buildString {
            append("The job: ")
            append(lastYear?.let { "$team went ${it.w}-${it.l} last season" + (if (it.result.isNotEmpty()) " (${it.result.replaceFirstChar { c -> c.lowercase() }})" else "") + ". " } ?: "")
            append(if (pro) "The owner wants a team that matters in the spring." else "The boosters want March.")
        }
        pa?.takeIf { it.proSeasons > 0 }?.let { p ->
            intro += "His playing career: ${plural(p.proSeasons, "season")} with ${p.proTeams.joinToString(", ")}" + (if (p.honors.isNotEmpty()) "; ${p.honors.joinToString(", ")}" else "") + "."
        }
        lg.proState?.collegeResume?.takeIf { it.isNotEmpty() && pro }?.let { intro += "In college: $it." }

        // The statements he can make, and how each lands.
        val choices = listOf("We're here to win. Now.", "We'll build it the right way.", "This is about the players.")
        val quotes = listOf(
            "Nobody came here to be patient. I didn't take this job to rebuild for five years. We're going to compete from the first day, and I expect to be holding a trophy in this building.",
            "There are no shortcuts. We're going to develop our guys, build habits, and build something that lasts. When we win, we're going to keep winning.",
            "This is about the young men in that locker room. If they trust each other and they trust us, the wins take care of themselves. My door is always open.",
        )
        val star = lg.rosterOf(t).maxByOrNull { it.ovr }
        val ins = Media.insider(lg)
        val an = Media.pundit(lg)
        val fan = Media.fanName()
        val mate = pa?.let { lg.players.firstOrNull { p -> p.name != c.name && p.career.any { l -> l.cls == PRO_YEAR && lg.teams.getOrNull(l.teamId)?.name in it.proTeams } } }
        fun base() = mutableListOf<Tribute>().also { out ->
            mate?.let { out += Tribute(it.name, "Played with him", pick("Coach ${c.last}?? I'll never get used to that. Go get 'em, ${c.first}.", "Always knew he'd end up on a sideline. He was coaching us in the huddle for years.")) }
        }
        val reactions = listOf(
            base().apply {
                add(Tribute(an.name, "Analyst", "Bold. I like it. Now he has to back it up, and $team ${if ((lastYear?.w ?: 0) < (lastYear?.l ?: 0)) "won ${lastYear?.w} games last year" else "has the pieces to try"}.", an.avatar, an.verified))
                star?.let { add(Tribute(it.name, "${t.abbr} star", "That's what I want to hear. Let's go.")) }
                add(Tribute(fan, "${t.abbr} fan", "FINALLY a coach who says it out loud. Banner or bust."))
                add(Tribute(ins.name, "Insider", "Expectations just went up in ${if (pro) t.name.substringBeforeLast(' ') else t.school}. The front office liked the confidence, sources say. Now the clock starts.", ins.avatar, ins.verified))
            },
            base().apply {
                add(Tribute(an.name, "Analyst", "The right message for $team. Patience is a hard sell, but it's the only thing that's ever worked there.", an.avatar, an.verified))
                star?.let { add(Tribute(it.name, "${t.abbr} star", "Excited to get to work. Building something real.")) }
                add(Tribute(fan, "${t.abbr} fan", "\"The right way\" means losing for two years, right? I'll give him one."))
                add(Tribute(ins.name, "Insider", "${c.name} has the $bossRole's backing for a longer runway, per sources. Development is the word around the building.", ins.avatar, ins.verified))
            },
            base().apply {
                add(Tribute(an.name, "Analyst", "Players are going to love playing for him. The question is whether the locker room's love shows up in the standings.", an.avatar, an.verified))
                star?.let { add(Tribute(it.name, "${t.abbr} star", "Already talked to Coach. He gets it. We're riding with him.")) }
                add(Tribute(fan, "${t.abbr} fan", "A players' coach. We'll see how that goes in February."))
                add(Tribute(ins.name, "Insider", "Several ${t.abbr} players reached out to ${c.last} within an hour of the announcement, sources say. The room is happy.", ins.avatar, ins.verified))
            },
        )
        val trib = mutableListOf<Tribute>()
        trib += Tribute(ins.name, "Insider", "$Team ${if (pro) "are" else "is"} hiring ${c.name} as head coach, sources say." +
            (pa?.takeIf { it.proSeasons > 0 }?.let { " The former ${it.proTeams.lastOrNull() ?: "NBA"} player takes his first head-coaching job." } ?: ""), ins.avatar, ins.verified)
        trib += Tribute(boss, "${t.abbr} $bossRole", "We're thrilled to welcome ${c.first} and his family. The future starts today.")

        val body = (intro + "He takes the podium next.").joinToString("\n\n")
        lg.news += News(lg.season, lg.day, headline, true, -1, "hire", body, "Hoops Wire", t.id, -1,
            trib.map { Reaction(it.who, it.role, it.text, Rng.range(1_000, 40_000), it.avatar, it.verified) })
        show(lg, Ceremony(
            "hire", "INTRODUCING", "COACH ${c.last.uppercase()}", "Head coach · ${if (pro) t.name else "${t.school} ${t.mascot}"}", t.id, 0,
            labels, values, "The introduction", intro, trib, listOf(headline), choices, quotes, reactions,
            effects = choices.indices.map { effect(it) },
        ))
    }

    /** He makes his opening statement: the room reacts, the owner or AD takes note, the news runs it. */
    fun speak(lg: League, choice: Int) {
        val cer = lg.ceremony ?: return
        if (cer.chosen >= 0 || choice !in cer.choices.indices) return
        if (cer.kind != "hire") { Celebrations.speak(lg, cer, choice); return }
        cer.chosen = choice
        val c = lg.userCoach
        val t = lg.user
        when (choice) {
            // Win now: the bosses like it, but they'll hold him to it.
            0 -> { c.security = (c.security - 4).coerceIn(0, 100); lg.rosterOf(t).forEach { it.happiness = (it.happiness + 2).coerceAtMost(99) } }
            // The right way: a longer leash.
            1 -> c.security = (c.security + 6).coerceIn(0, 100)
            // Players first: the locker room is with him.
            else -> lg.rosterOf(t).forEach { it.happiness = (it.happiness + 8).coerceAtMost(99) }
        }
        val quote = cer.choiceQuotes[choice]
        lg.news += News(lg.season, lg.day, "${c.name} at his introduction: \"${cer.choices[choice].trimEnd('.')}\"", true, -1, "hire",
            "\"$quote\"", "Hoops Wire", t.id, -1,
            cer.choiceReactions[choice].map { Reaction(it.who, it.role, it.text, Rng.range(500, 30_000), it.avatar, it.verified) })
    }

    /** Shows [c] now, or after the moment already on screen (a title and an award can land in the same sim). */
    fun show(lg: League, c: Ceremony) {
        if (lg.ceremony == null) lg.ceremony = c else lg.ceremonyQueue += c
    }

    fun dismiss(lg: League) { lg.ceremony = if (lg.ceremonyQueue.isEmpty()) null else lg.ceremonyQueue.removeAt(0) }

    /** What each statement does, in a line, for the press conference screen. */
    fun effect(choice: Int) = when (choice) {
        0 -> "Fans and players buy in; your bosses will hold you to it (job security −4)."
        1 -> "A longer leash from the front office (job security +6)."
        else -> "The locker room is with you (player happiness +8)."
    }
}
