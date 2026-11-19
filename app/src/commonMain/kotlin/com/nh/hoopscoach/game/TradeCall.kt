package com.nh.hoopscoach.game

import kotlin.math.roundToInt

/**
 * Being traded, as it should feel. A trade he asked for is a wish granted; one he didn't is a phone
 * call that changes his life, and it takes the whole screen: who called, what he was traded for, the
 * reactions, and how he answers. The bigger the star, the bigger the story: a blockbuster headline, a
 * column on what it means, both fan bases and his old teammates online. And the first time he plays
 * his old team in their building, the city tells him how it feels: a tribute for a player they lost,
 * boos for one who asked out.
 */
object TradeCall {
    private fun <T> pick(vararg o: T): T = o[Rng.int(0, o.size)]

    /** How big a name he is: 2 a franchise player, 1 an All-Star, 0 everyone else. */
    fun stature(p: Player): Int = when (Trades.tier(p)) { "Franchise" -> 2; "All-Star" -> 1; else -> 0 }

    /** After [Trades.dealHero] has moved him: the news, the reactions and, when he didn't ask, the call. */
    fun after(lg: League, from: Team, to: Team, back: List<Player>, asked: Boolean) {
        val r = lg.road ?: return
        val h = lg.hero ?: return
        val star = stature(h)
        r.tradeAsked = asked
        r.returnTeam = from.id
        r.returnSeason = lg.season
        val city = from.school
        val newCity = to.school
        // The bigger the name, the bigger the following that moves with him.
        r.followers = (r.followers * (1 + when (star) { 2 -> 0.07; 1 -> 0.045; else -> 0.02 })).roundToInt()
        val got = back.joinToString(" and ") { it.name }
        // The national story, sized to the star.
        if (star >= 1) {
            val head = if (asked) pick("${h.name} gets his wish: traded to the ${to.name}", "${h.name} forces his way to the ${to.name}", "The ${from.name} grant ${NbaPreviews.possessive(h.name)} request")
                else pick("Stunner: the ${from.name} trade ${h.name}", "${h.name} traded to the ${to.name} in a shocker", "The ${from.name} blow it up: ${h.name} is gone")
            val body = listOf(
                if (asked) "${h.name} wanted out, and now he's out: the ${from.name} send him to the ${to.name} for $got." else "Nobody saw this coming. Not even ${h.last}, by most accounts. The ${from.name} send ${h.name} to the ${to.name} for $got.",
                "${h.name} (${h.age}) is ${if (star == 2) "a franchise player, the kind of name that changes a conference" else "an All-Star in his prime"}, averaging ${"%.1f".format(h.stats.ppg)} points this season." .takeIf { h.stats.gp > 0 } ?: "${h.name} (${h.age}) is ${if (star == 2) "a franchise player" else "an All-Star"}.",
                "For the ${to.name} (${to.w}-${to.l}): ${if (to.winPct >= 0.55) "a contender just got its closer." else "a franchise just got its face."}",
                "For the ${from.name} (${from.w}-${from.l}): ${if (asked) "they got what they could for a star who wanted to leave." else "a bet that $got and the future are worth more than ${h.last} is today."}",
                if (asked) "In $city, the jerseys are already coming down." else "In $city, the jerseys are still up. People aren't ready.",
            ).joinToString("\n\n")
            lg.news += News(lg.season, lg.day, head, true, -1, "column", body, pick("The Rim", "Baseline Sports", "Hoops Wire"), to.id, h.id)
        }
        // Social: the insider broke it (see [Rumors.breaking]); both fan bases, his old teammates, his new team.
        val likes = Media.likes(lg, when (star) { 2 -> 0.1; 1 -> 0.06; else -> 0.02 })
        Media.post(lg, to.name, to.abbr.lowercase(), "team", "Welcome to $newCity, ${h.last}. 🔥", likes, verified = true)
        val oldFan = "${from.mascot.lowercase().replace(" ", "")}forever"
        Media.post(lg, oldFan, oldFan, "fan",
            if (asked) pick("He wanted out. Fine. Don't let the door hit you.", "Took our money, asked out. Remember that.", "We'll see you in ${city}. Bring earplugs.")
            else pick("Thank you for everything, ${h.last}. This one hurts.", "Who made this trade? ${h.last} was ONE OF US.", "Gonna miss you, ${h.last}. Not ready for this."),
            Media.likes(lg, 0.02))
        val newFan = "${to.mascot.lowercase().replace(" ", "")}nation"
        Media.post(lg, newFan, newFan, "fan", pick("WE GOT ${h.last.uppercase()}!!!", "Front office cooking. ${h.last} in $newCity is real.", "Not crying, you're crying. ${h.last} is here."), Media.likes(lg, 0.03))
        lg.rosterOf(from).filter { it.id != h.id }.maxByOrNull { it.ovr }?.let { mate ->
            Media.post(lg, mate.name, mate.name.replace(" ", "").replace(".", "").lowercase(), "player",
                if (asked) pick("Wish you the best, ${h.first}.", "Business is business.") else pick("Gonna miss my brother. Love you ${h.first}.", "Just found out. This league, man."),
                Media.likes(lg, 0.04), verified = true)
        }
        if (asked) {
            Alerts.add(lg, "You got your wish", "The ${from.name} send you to the ${to.name} for $got. New team, new role: check the Me tab.", "trade")
            return
        }
        // He didn't ask: the call.
        val coach = runCatching { lg.coachOf(from) }.getOrNull()
        val caller = coach?.let { "Coach ${it.last}" } ?: "Your GM"
        val ft = lg.team(to.id)
        val rank = lg.rosterOf(ft).sortedByDescending { it.ovr }.indexOfFirst { it.id == h.id } + 1
        val role = when (rank) { 1 -> "The No. 1 option"; 2, 3 -> "A top-three player"; in 4..5 -> "A starter"; else -> "Off the bench" }
        val letter = listOf(
            "Your phone buzzes at ${pick("6:12 in the morning", "7:40 in the morning", "1:03 in the afternoon", "11:58 at night")}. It's $caller. He doesn't waste time.",
            "\"We traded you to the ${to.name}. ${pick("I wanted you to hear it from me.", "It wasn't an easy call.", "It's done. I'm sorry.")}\"",
            "Your agent calls thirty seconds later. Then your mom. Then everyone.",
            if (star >= 1) "By breakfast it's the lead on every show in the country. Your name is in every headline." else "By lunch it's on the crawl at the bottom of every sports channel.",
            "You've got a flight to $newCity tonight.",
        )
        val reactions = listOfNotNull(
            coach?.let { Tribute(it.name, "${from.abbr} coach", pick("He gave this team everything. That's what makes it hard.", "One of the best people I've coached. This part of the job is the worst part.")) },
            runCatching { lg.coachOf(to) }.getOrNull()?.let { Tribute(it.name, "${to.abbr} coach", pick("We've wanted him for a long time. Can't wait to get him in the gym.", "He fits everything we do. Welcome.")) },
            lg.rosterOf(to).filter { it.id != h.id }.maxByOrNull { it.ovr }?.let { Tribute(it.name, "${to.abbr} star", pick("Let's work. 🤝", "Been waiting on this one.")) },
            Tribute("${from.mascot} fans", city, if (star >= 1) "Shock. Jerseys in the trash, jerseys in frames. The whole city is talking." else "Sad to see him go. Thanks for the effort."),
        )
        Farewell.show(lg, Ceremony(
            kind = "traded", kicker = if (star >= 1) "BREAKING · TRADED" else "TRADED", title = "YOU'VE BEEN TRADED",
            sub = "The ${from.name} send you to the ${to.name}", teamId = to.id, number = h.number,
            statLabels = listOf("NEW TEAM", "FOR", "YOUR ROLE"), statValues = listOf(to.name, got, role),
            letterTitle = "The call", letter = letter, tributes = reactions,
            coverage = lg.news.takeLast(4).filter { it.mine || it.kind == "trade" || it.kind == "column" }.map { it.text }.distinct().take(3),
            choices = listOf("Thank you, $city. I'll always be grateful.", "New chapter. Let's go to work.", "Nobody called me first. I found out like everybody else."),
            choiceQuotes = listOf(
                "\"I gave $city everything I had, and $city gave it right back. That doesn't go away. Now I've got a job to do in $newCity.\"",
                "\"It's a business. I get it. $newCity, I'm ready. Let's go to work.\"",
                "\"I'm not going to pretend. I found out like everybody else. That's not how you treat people.\"",
            ),
            choiceReactions = listOf(
                listOf(Tribute("${from.mascot} fans", city, "Class act. Always one of us."), Tribute("${to.mascot} fans", newCity, "Love the respect. Now let's win.")),
                listOf(Tribute("${to.mascot} fans", newCity, "That's what we want to hear."), Tribute("Hoops Wire", "Media", "Says all the right things.")),
                listOf(Tribute("${from.mascot} fans", city, "He's not wrong. The front office botched this."), Tribute("The Rim", "Media", "${h.last} isn't hiding how he feels. The clip has a million views.")),
            ),
            choiceTitle = "Your first words", choicePrompt = "The cameras are waiting at the airport.",
            effects = listOf(
                "Humble: ${from.mascot} fans love you forever; a warm welcome when you go back.",
                "Even: your new teammates like it (happiness +4); the sting fades faster.",
                "Brash: it goes viral (followers +6%), but the ${from.name} front office won't forget.",
            ),
            badge = "${from.name} → ${to.name}",
        ))
    }

    /** His first words after the call. */
    fun react(lg: League, choice: Int) {
        val r = lg.road ?: return
        val h = lg.hero ?: return
        val cer = lg.ceremony ?: return
        val from = lg.teams.getOrNull(r.returnTeam)
        when (choice) {
            0 -> { r.persona = (r.persona - 1).coerceAtLeast(-10); from?.let { it.fans = (it.fans + 3).coerceAtMost(100.0) }; r.tradeReaction = 0 }
            1 -> { lg.rosterOf(lg.team(h.teamId)).forEach { if (it.id != h.id) it.happiness = (it.happiness + 4).coerceAtMost(99) }; r.tradeReaction = 1 }
            else -> { r.persona = (r.persona + 2).coerceAtMost(10); r.followers = (r.followers * 1.06).roundToInt(); r.tradeReaction = 2 }
        }
        lg.news += News(lg.season, lg.day, "${h.name} on the trade: \"${cer.choices[choice].trimEnd('.')}\"", true, -1, "quote",
            cer.choiceQuotes.getOrNull(choice).orEmpty(), "Hoops Wire", h.teamId, h.id,
            cer.choiceReactions.getOrNull(choice).orEmpty().map { Reaction(it.who, it.role, it.text, Rng.range(800, 60_000), it.avatar, it.verified) })
    }

    /** How the trade sits with him, for his mood: a surprise stings for a few weeks. */
    fun moodHit(lg: League): Pair<Int, String>? {
        val r = lg.road ?: return null
        if (r.tradeAsked || r.tradedSeason != lg.season || r.tradedDay < 0) return null
        val days = lg.day - r.tradedDay
        if (days !in 0..24 || lg.phase == Phase.OFFSEASON) return null
        val hit = when (r.tradeReaction) { 1 -> 5; 2 -> 12; else -> 8 } * (24 - days) / 24
        return if (hit <= 0) null else -hit to "Still stung by the trade"
    }

    /** After each day: his first game back in his old team's building. */
    fun afterDay(lg: League, day: Int) {
        val r = lg.road ?: return
        val h = lg.hero ?: return
        if (r.returnTeam < 0 || r.returnTeam == h.teamId) return
        // The return is a story for a season or so; after that he's just another visitor.
        if (lg.season - r.returnSeason > 1) { r.returnTeam = -1; return }
        val old = lg.teams.getOrNull(r.returnTeam) ?: return
        val g = Season.gamesOn(lg, day).firstOrNull { it.played && it.home == old.id && it.involves(h.teamId) && h.log.any { l -> l.gameId == it.id } } ?: return
        val line = h.log.last { it.gameId == g.id }
        val star = stature(h)
        val warm = !r.tradeAsked && r.tradeReaction != 2
        val head = if (warm) "${old.school} welcomes ${h.name} back" else "${old.school} lets ${h.name} hear it"
        val body = if (warm)
            "A tribute video in the first timeout, and ${if (star >= 1) "a standing ovation that stopped the game for a minute" else "a long ovation"}. ${h.last} put his hand on his heart. Then he scored ${line.pts}."
        else
            "Boos on every touch${if (r.tradeAsked) ", from a building that remembers who asked out" else ""}. ${h.last} answered with ${line.pts} points${if (g.winner() == h.teamId) " and a win" else ""}."
        lg.news += News(lg.season, day, head, true, g.id, "game", body, "Hoops Wire", old.id, h.id)
        Media.post(lg, "${old.mascot.lowercase().replace(" ", "")}forever", "${old.mascot.lowercase().replace(" ", "")}forever", "fan",
            if (warm) "That ovation for ${h.last} gave me chills. Always one of ours." else "Booed him all night. ${line.pts} points though. Ugh.", Media.likes(lg, 0.02), gameId = g.id)
        // Once is the story.
        r.returnTeam = -1
    }
}
