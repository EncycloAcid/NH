package com.nh.hoopscoach.game

/**
 * The coaching carousel as a world of careers rather than a slot machine. When a job opens, a bigger
 * program can hire away a coach who's winning somewhere smaller, and the school he leaves promotes one
 * of his assistants. Every promoted assistant carries his mentor's name ([Coach.mentor]) and some of his
 * coaching, so a great coach leaves a tree of former assistants running programs of their own.
 */
object CoachTrees {
    /** Head coaches working today who came up under [name]. */
    fun branches(lg: League, name: String): List<Coach> =
        if (name.isBlank()) emptyList() else lg.coaches.filter { it.teamId >= 0 && it.mentor == name }

    /** Who he is on the sideline, read off what he's best and worst at. */
    fun identity(c: Coach): String {
        val parts = listOf("offense" to c.off, "defense" to c.def, "recruiting" to c.rec, "player development" to c.dev)
        val best = parts.maxBy { it.second }
        val worst = parts.minBy { it.second }
        val known = when (best.first) {
            "offense" -> "An offensive mind"
            "defense" -> "A defense-first coach"
            "recruiting" -> "A recruiter first"
            else -> "A developer of players"
        }
        return if (best.second - worst.second >= 8) "$known; ${worst.first} is the question" else "$known with few holes"
    }

    /**
     * The odds a school fires its coach this offseason, from what has actually happened there: how far
     * short of expectations this year fell, losing seasons piling up, and his standing, against what he
     * has won lately. A coach who just won his league is close to untouchable; one with three losing
     * years in a row at a proud program is very likely gone.
     */
    fun fireChance(lg: League, t: Team, c: Coach): Double {
        val scandal = WorldEvents.scandalFireChance(lg, t)
        val here = c.history.filter { it.teamId == t.id }
        if (here.size < 2) return scandal                           // nobody is fired after one year, unless there's a scandal
        val recent = here.takeLast(3)
        val losing = recent.count { it.w < it.l }
        val short = t.expWins - t.w                                 // wins short of what this roster should manage
        var p = 0.0
        if (short > 3) p += (short - 3) * 0.05
        p += when (losing) { 3 -> 0.35; 2 -> 0.15; else -> 0.0 }
        if (c.security < 35) p += (35 - c.security) * 0.01
        if (t.prestige >= 75 && losing >= 1) p += 0.08               // proud programs have less patience
        if (lg.confBrackets.any { it.champion == t.id }) p -= 0.4
        val round = Awards.tourneyRound(lg, t.id)
        if (round >= 1) p -= 0.15 + round * 0.05
        if (recent.any { it.result == "Final Four" || it.result == "Runner-Up" || it.result == "National Champion" }) p -= 0.3
        return (p.coerceIn(0.0, 0.85) + scandal).coerceAtMost(0.95)
    }

    /** An assistant stepping up: a young coach shaped by the man he worked for. */
    private fun assistantOf(lg: League, t: Team, mentor: Coach): Coach {
        val n = LeagueGen.makeCoach(lg, t.prestige, Rng.range(34, 48), withCareer = false)
        // A third of what he does, he learned from his mentor.
        n.off = (n.off * 2 + mentor.off) / 3
        n.def = (n.def * 2 + mentor.def) / 3
        n.rec = (n.rec * 2 + mentor.rec) / 3
        n.dev = (n.dev * 2 + mentor.dev) / 3
        n.mentor = mentor.name
        return n
    }

    private fun seat(lg: League, t: Team, c: Coach, mentor: String = "") {
        c.teamId = t.id
        t.coachId = c.id
        GenerationalLegacy.inheritProgram(lg, t, c, mentor)
    }

    /**
     * Fills the job [old] just left at [t] ([retired] or fired). [touched] collects every school whose
     * coach changed this cycle so nobody is hired and judged in the same offseason.
     */
    fun fill(lg: League, t: Team, old: Coach, retired: Boolean, touched: MutableSet<Int>) {
        touched += t.id
        val mine = t.id == lg.userTeamId
        // A coach winning more than he should at a smaller school is the first call a real program makes.
        val hot = lg.teams.filter { o ->
            o.id != t.id && o.id !in touched && !lg.humanCoach(o) && o.prestige in (t.prestige - 45)..(t.prestige - 10)
        }.map { it to lg.coachOf(it) }.filter { (o, c) ->
            c.age <= 62 && c.history.isNotEmpty() && (o.w - o.expWins >= 3 || Awards.tourneyRound(lg, o.id) >= 3)
        }.maxByOrNull { (o, _) -> (o.w - o.expWins) + Awards.tourneyRound(lg, o.id) * 2 + Rng.gauss() * 2 }
        val roll = Rng.d()
        when {
            hot != null && t.prestige >= 55 && roll < 0.5 -> {
                val (from, c) = hot
                touched += from.id
                seat(lg, t, c)
                c.security = 70
                lg.news += News(lg.season, lg.day, "${t.school} hires ${c.name} away from ${from.school}", mine, kind = "hire",
                    body = "${c.name} went ${from.w}-${from.l} at ${from.school} this season and ${c.w}-${c.l} for his career. ${identity(c)}." +
                        (if (c.mentor.isNotBlank()) " He came up as an assistant under ${c.mentor}." else ""),
                    source = "Hoops Wire", teamId = t.id)
                Timeline.record(lg, "hire", "${t.school} hires ${c.name} from ${from.school}", "${c.name} left ${from.school} after a ${from.w}-${from.l} season to take over at ${t.school}.", t.id, coach = c.name, weight = if (t.prestige >= 80) 3 else 2, team2 = from.id)
                runCatching { WorldEvents.followCoach(lg, from, t, c) }
                // The school he left keeps what he built: one of his assistants takes over.
                val next = assistantOf(lg, from, c)
                seat(lg, from, next, c.name)
                lg.news += News(lg.season, lg.day, "${from.school} promotes ${next.name} to replace ${c.last}", from.id == lg.userTeamId, kind = "hire",
                    body = "${next.name}, ${next.age}, was on ${c.name}'s staff at ${from.school}. It's his first head coaching job.",
                    source = "Hoops Wire", teamId = from.id)
            }
            roll < 0.82 -> {
                // An assistant: the old coach's own when he retired on his terms or won there, otherwise one off a champion's staff.
                val champions = lg.coaches.filter { it.teamId >= 0 && it.titles >= 1 && it.id != old.id }
                val mentor = if (retired || old.titles > 0 || champions.isEmpty() || Rng.chance(0.4)) old else Rng.pick(champions)
                val n = assistantOf(lg, t, mentor)
                seat(lg, t, n, mentor.name)
                val from = if (mentor === old) "from his own staff" else "off ${mentor.name}'s staff at ${lg.teams.getOrNull(mentor.teamId)?.school ?: "his last stop"}"
                if (t.prestige >= 70 || mine || mentor.titles > 0) lg.news += News(lg.season, lg.day,
                    "${t.school} hires ${n.name}, ${if (mentor === old) "${old.last}'s assistant" else "a ${mentor.last} assistant"}, as head coach", mine, kind = "hire",
                    body = "${n.name}, ${n.age}, comes $from. ${mentor.name} is ${mentor.w}-${mentor.l} with ${mentor.titles} national ${if (mentor.titles == 1) "title" else "titles"}. ${identity(n)}.",
                    source = "Hoops Wire", teamId = t.id)
                Timeline.record(lg, "hire", "${t.school} hires ${n.name}", "${n.name}, a former assistant to ${mentor.name}, becomes head coach at ${t.school}.", t.id, coach = n.name, weight = if (t.prestige >= 80 || mentor.titles > 0) 2 else 1)
                treeStory(lg, mentor)
            }
            else -> {
                val n = LeagueGen.makeCoach(lg, t.prestige, Rng.range(34, 55), withCareer = true)
                seat(lg, t, n)
                if (t.prestige >= 80) lg.news += News(lg.season, lg.day, "${t.school} hires ${n.name} as head coach", false)
            }
        }
    }

    /** Once three of a coach's former assistants run programs, that's a story. */
    private fun treeStory(lg: League, mentor: Coach) {
        val tree = branches(lg, mentor.name)
        if (tree.size != 3) return
        Timeline.record(lg, "tree", "The ${mentor.last} coaching tree reaches three programs", tree.joinToString(", ") { "${it.name} (${lg.teams.getOrNull(it.teamId)?.school})" } + " all worked for ${mentor.name}.", mentor.teamId, coach = mentor.name, weight = 3)
        val schools = tree.mapNotNull { c -> lg.teams.getOrNull(c.teamId)?.school?.let { "${c.name} at $it" } }
        lg.news += News(lg.season, lg.day, "The ${mentor.last} tree: three of his former assistants now run programs", false, kind = "column",
            body = "${schools.joinToString(", ")}. All three learned the job under ${mentor.name}, who is ${mentor.w}-${mentor.l}" +
                (if (mentor.titles > 0) " with ${mentor.titles} national ${if (mentor.titles == 1) "title" else "titles"}" else "") + ".",
            source = "Hoops Wire", teamId = mentor.teamId)
    }
}

/**
 * Feature stories about programs that climbed, written only from what happened: the losing season on
 * record, this year's run, how long the coach has been there and who carried the team.
 */
object Turnarounds {
    private fun reached(round: Int) = when (round) {
        3 -> "the Sweet 16"; 4 -> "the Elite Eight"; 5 -> "the Final Four"; 6 -> "the national title game"; else -> "the national championship"
    }

    /** At season's end, after this season is in each team's history. */
    fun publish(lg: League) {
        if (lg.pro) return
        val found = lg.teams.mapNotNull { t ->
            val round = Awards.tourneyRound(lg, t.id)
            if (round < 3) return@mapNotNull null
            val before = t.history.filter { it.season < lg.season }
            val low = before.takeLast(4).filter { it.w < it.l }.minByOrNull { it.w.toDouble() / (it.w + it.l).coerceAtLeast(1) } ?: return@mapNotNull null
            Triple(t, round, low)
        }.sortedByDescending { (t, round, low) -> round * 3 + (low.l - low.w) + if (t.id == lg.userTeamId) 100 else 0 }.take(2)
        for ((t, round, low) in found) {
            val c = lg.coachOf(t)
            val years = c.history.count { it.teamId == t.id }
            val ago = lg.season - low.season
            val before = t.history.filter { it.season < lg.season }
            val deeper = listOf("Sweet 16", "Elite Eight", "Final Four", "Runner-Up", "National Champion").drop(round - 3)
            val first = before.size >= 3 && before.none { s -> deeper.any { s.result == it } }
            val roster = lg.rosterOf(t).filter { it.stats.gp >= 10 }
            val star = roster.maxByOrNull { it.stats.ppg }
            // The player who grew the most since last season, if anyone grew a lot.
            // (This season's rating is already the last entry, so last year's is the one before it.)
            fun grew(p: Player) = p.ovrHistory.dropLast(1).lastOrNull()?.let { p.ovr - it } ?: 0
            fun year(p: Player) = when (p.year) { 1 -> "freshman"; 2 -> "sophomore"; 3 -> "junior"; else -> "senior" }
            val riser = roster.filter { it.id != star?.id }.maxByOrNull { grew(it) }?.takeIf { grew(it) >= 4 }
            val body = buildList {
                add("After finishing ${low.w}-${low.l} ${if (ago == 1) "just last season" else "just $ago seasons ago"}, ${t.school} has reached ${reached(round)}" +
                    (if (first) ", further than any ${t.school} team in the ${before.size} seasons on record." else ".") +
                    " The ${t.mascot} went ${t.w}-${t.l} this year.")
                add("Head coach ${c.name} has been there ${if (years <= 1) "one season" else "$years seasons"}" +
                    (if (years <= ago) ", and took over after that losing year." else ", through the losing and the climb back.") +
                    " ${CoachTrees.identity(c)}." + (if (c.mentor.isNotBlank()) " He learned the job as an assistant under ${c.mentor}." else ""))
                if (star != null) add("${star.name} led the way at ${"%.1f".format(star.stats.ppg)} points a game" +
                    (riser?.let { ", and ${year(it)} ${it.name} came on over the year: up ${grew(it)} points in the ratings since last season." } ?: "."))
            }.joinToString("\n\n")
            val head = when {
                low.w * 2 <= low.l -> "The little program that wouldn't quit: ${t.school}'s climb to ${reached(round)}"
                else -> "From ${low.w}-${low.l} to ${reached(round)}: how ${t.school} turned it around"
            }
            Timeline.record(lg, "rise", head.substringBefore(':'), "${t.school} reached ${reached(round)} at ${t.w}-${t.l}, ${if (ago == 1) "a year" else "$ago years"} after going ${low.w}-${low.l}.", t.id, star?.id ?: -1, c.name, 3)
            lg.news += News(lg.season, lg.day, head, t.id == lg.userTeamId, kind = "column", body = body,
                source = "Dana Marsh · Hoops Wire", teamId = t.id, pid = star?.id ?: -1)
        }
    }
}
