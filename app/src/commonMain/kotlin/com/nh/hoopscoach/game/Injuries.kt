package com.nh.hoopscoach.game

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Players get hurt, and how they come back matters.
 *
 * Every game a player logs real minutes there's a small chance (more for heavy minutes, older bodies, a body that
 * wears down early, a tired hero, a player rushed back or playing through it, and one who's been hurt a lot). An
 * injury has a body part and a grade: Minor (a game or three), Moderate (one to four weeks), Severe (one to two
 * months) and Season-ending. A part that's been hurt before is likelier to go again, and getting hurt again while
 * still limited aggravates it.
 *
 * Your players (a coach) and your own player (a player career) get a recovery plan for anything worse than minor:
 * rest fully (longer out, back at full strength), standard (the diagnosed timeline, a couple of limited games), or
 * rush back (back much sooner, then limited longer and twice as likely to get hurt again). Limited games cost a
 * little shooting. A questionable player of yours can play through it, below 100%. Season-ending knee, Achilles
 * and foot injuries cost explosiveness for good. It all goes in his medical history.
 */
object Injuries {
    /** One kind of injury: what it's called, the part, the grade, and the games it costs (standard plan). */
    class Kind(val what: String, val part: String, val grade: Int, val lo: Int, val hi: Int)

    private val KINDS = listOf(
        Kind("a sprained left ankle", "ankle", 1, 1, 3), Kind("a sprained right ankle", "ankle", 1, 1, 3), Kind("a sore knee", "knee", 1, 1, 2),
        Kind("a bruised hip", "hip", 1, 1, 2), Kind("back spasms", "back", 1, 1, 3), Kind("a jammed finger", "hand", 1, 1, 2),
        Kind("hamstring tightness", "hamstring", 1, 1, 3), Kind("a sore Achilles", "Achilles", 1, 1, 3), Kind("an illness", "illness", 1, 1, 2),
        Kind("a sprained wrist", "wrist", 1, 1, 2), Kind("calf tightness", "calf", 1, 1, 2), Kind("groin tightness", "groin", 1, 1, 3),
        Kind("a sprained ankle", "ankle", 2, 4, 9), Kind("knee soreness", "knee", 2, 4, 8), Kind("a hamstring strain", "hamstring", 2, 5, 12),
        Kind("a bone bruise", "knee", 2, 4, 10), Kind("a sprained thumb", "hand", 2, 4, 8), Kind("a calf strain", "calf", 2, 5, 12),
        Kind("a concussion", "head", 2, 4, 9), Kind("a hip flexor strain", "hip", 2, 5, 11), Kind("a rib contusion", "ribs", 2, 4, 8),
        Kind("a sprained MCL", "knee", 2, 6, 12),
        Kind("a high ankle sprain", "ankle", 3, 13, 24), Kind("a Grade 2 MCL sprain", "knee", 3, 14, 26), Kind("a broken hand", "hand", 3, 16, 28),
        Kind("a stress reaction in his foot", "foot", 3, 15, 30), Kind("a torn ligament in his thumb", "hand", 3, 14, 26), Kind("a fractured wrist", "wrist", 3, 18, 30),
        Kind("a partially torn hamstring", "hamstring", 3, 14, 28), Kind("a torn meniscus", "knee", 3, 16, 30),
        Kind("a torn ACL", "knee", 4, 0, 0), Kind("a ruptured Achilles", "Achilles", 4, 0, 0), Kind("a broken foot", "foot", 4, 0, 0),
        Kind("a torn meniscus that needs surgery", "knee", 4, 0, 0), Kind("a fractured kneecap", "knee", 4, 0, 0),
    )

    /** Out for what's left of the season (cleared when it ends). */
    const val SEASON_OUT = 999

    val GRADES = listOf("", "Minor", "Moderate", "Severe", "Season-ending")

    /** How fast each plan gets through the standard recovery: standard games per game missed. */
    private fun speed(plan: String) = when (plan) { "rest" -> 0.8; "rush" -> 1.55; else -> 1.0 }

    /** Games below 100% after a return, by plan and grade. */
    private fun limitedAfter(plan: String, grade: Int) = when (plan) { "rest" -> 0; "rush" -> 6 + grade; else -> if (grade >= 3) 3 else if (grade == 2) 2 else 0 }

    /** The shooting a limited player gives up. */
    const val LIMITED_PENALTY = 0.025

    // ---------- who can play ----------

    /** The players who can go tonight: not hurt, and not resting. A team never dresses fewer than five. */
    fun available(lg: League, t: Team, game: Game): List<Player> {
        val all = lg.rosterOf(t)
        val healthy = all.filter { !it.out }
        val ok = healthy.filter { !resting(lg, t, it, game) }
        return when {
            ok.size >= 5 -> ok
            healthy.size >= 5 -> healthy
            else -> all
        }
    }

    /** A veteran star resting on an ordinary night: AI pro teams only, never late in the season or in the playoffs. */
    fun resting(lg: League, t: Team, p: Player, game: Game): Boolean {
        if (!lg.pro || (game.type != GameType.CONF && game.type != GameType.NONCONF)) return false
        if (lg.humanCoach(t) || p.id == lg.heroId || p.age < 31 || lg.day < 20) return false
        if (lg.day > ProLeague.allStarDay(lg) * 2 - 10) return false
        if (lg.rosterOf(t).sortedByDescending { it.ovr }.take(2).none { it.id == p.id }) return false
        return kotlin.random.Random(game.id * 31L + p.id).nextDouble() < 0.04
    }

    /** The user decides for this player: his own team's players (a coach) or himself (a player career). */
    fun yours(lg: League, p: Player): Boolean = if (lg.playerMode) p.id == lg.heroId else p.teamId == lg.userTeamId && p.teamId >= 0

    // ---------- status ----------

    /** "Out", "Doubtful", "Questionable", "Playing through it", "Limited" or "" when he's 100%. */
    fun status(p: Player): String = when {
        p.injGames >= SEASON_OUT -> "Out for the season"
        p.out && p.injGrade in 1..2 && p.injGames == 1 -> "Questionable"
        p.out && p.injGrade in 1..2 && p.injGames == 2 -> "Doubtful"
        p.out -> "Out"
        p.hurtPlaying -> "Playing through it"
        p.limited > 0 -> "Limited"
        else -> ""
    }

    /** A questionable (or doubtful) player of yours can play through it. */
    fun canPlayThrough(lg: League, p: Player) = yours(lg, p) && p.out && p.injGrade in 1..2 && p.injGames <= 2

    /** How far along his recovery is (0 to 1). */
    fun progress(p: Player): Float = if (!p.out || p.injTotal <= 0 || p.injGames >= SEASON_OUT) 0f else (1f - p.injGames.toFloat() / p.injTotal).coerceIn(0f, 1f)

    /** The three recovery plans, as (key, label). */
    val PLANS = listOf("rest" to "Rest fully", "standard" to "Standard", "rush" to "Rush back")

    /** You choose his recovery plan: yours, out, moderate or severe. */
    fun canPlan(lg: League, p: Player) = yours(lg, p) && p.out && p.injGrade in 2..3 && p.injGames < SEASON_OUT

    /** Games he'd still miss on [plan]. */
    fun gamesOn(p: Player, plan: String): Int = ceil(p.injLeft / speed(plan)).toInt().coerceAtLeast(1)

    /** Games he'd play below 100% after coming back on [plan]. */
    fun limitedOn(p: Player, plan: String): Int = limitedAfter(plan, p.injGrade)

    /** What [plan] means, in one line. */
    fun planNote(plan: String, you: Boolean = false): String = when (plan) {
        "rest" -> if (you) "Slower, but you come back at 100% with no added risk." else "Slower, but he comes back at 100% with no added risk."
        "rush" -> "Back sooner, but below 100% for longer, and far likelier to get hurt again."
        else -> if (you) "The training staff's timeline, then a couple of games to get your legs back." else "The training staff's timeline, then a couple of games to get his legs back."
    }

    /** The plan in force ("standard" when none was chosen). */
    fun planOf(p: Player) = p.injPlan.ifEmpty { "standard" }

    // ---------- choices ----------

    /** Your call on his recovery: rest fully, standard, or rush back (anything worse than minor, not season-ending). */
    fun setPlan(lg: League, p: Player, plan: String) {
        if (!yours(lg, p) || !p.out || p.injGrade !in 2..3 || plan !in listOf("rest", "standard", "rush")) return
        p.injPlan = plan
        p.injGames = ceil(p.injLeft / speed(plan)).toInt().coerceAtLeast(1)
        lg.teams.getOrNull(p.teamId)?.let { t -> if (!lg.humanCoach(t) || t.plan.auto) Tactics.autoAllocate(lg, t) }
    }

    /** He plays through a questionable tag: available now, below 100%, and more likely to get hurt worse. */
    fun playThrough(lg: League, p: Player) {
        if (!canPlayThrough(lg, p)) return
        p.injGames = 0
        p.hurtPlaying = true
        p.limited = max(p.limited, 3)
        lg.teams.getOrNull(p.teamId)?.let { t -> if (!lg.humanCoach(t) || t.plan.auto) Tactics.autoAllocate(lg, t) }
        lg.news += News(lg.season, lg.day, "${p.name} will play through ${p.injury}", yours(lg, p), -1, "injury",
            "He's listed as available, but he won't be 100%.", "Hoops Wire", p.teamId, p.id)
    }

    // ---------- after each game ----------

    fun afterGame(lg: League, game: Game, teams: Array<Team>, lines: Array<LinkedHashMap<Int, StatLine>>) {
        for (side in 0..1) {
            val t = teams[side]
            // Those who missed this one are a game further along.
            for (p in lg.rosterOf(t)) if (p.out && p.id !in lines[side] && p.injGames < SEASON_OUT) {
                if (p.injGrade == 0) {
                    // Not a medical matter (a suspension, a family matter): a plain count of games.
                    p.injGames--
                    if (p.injGames == 0) healed(lg, t, p)
                } else {
                    p.injLeft -= speed(p.injPlan)
                    p.injGames = ceil(p.injLeft / speed(p.injPlan)).toInt().coerceAtLeast(0)
                    if (p.injLeft <= 0.0 || p.injGames == 0) healed(lg, t, p)
                }
            }
            for ((id, st) in lines[side]) {
                if (st.sec < 60) continue
                val p = lg.playerOrNull(id) ?: continue
                if (p.out) continue
                // Playing on it: he heals at half speed, and he's limited until it's done.
                if (p.hurtPlaying) {
                    p.injLeft -= 0.5
                    if (p.injLeft <= 0.0) { p.hurtPlaying = false; p.injury = ""; p.injLeft = 0.0; p.injGrade = 0; p.injPart = ""; p.injTotal = 0 }
                    else p.limited = max(p.limited, 1)
                }
                if (p.limited > 0) p.limited--
                if (st.sec >= 300 && Rng.d() < chance(lg, p, st.sec)) hurt(lg, t, p)
            }
        }
    }

    /** How injury-prone he is: recent injuries add up. */
    fun prone(p: Player) = p.injCount >= 4

    private fun chance(lg: League, p: Player, sec: Int): Double {
        val base = if (lg.pro) 0.008 else 0.006
        val age = 1.0 + max(0, p.age - 27) * 0.04
        val body = 1.5 - Longevity.of(p) / 100.0
        val tired = if (p.id == lg.heroId) when { (lg.road?.fatigue ?: 0) >= 70 -> 2.0; (lg.road?.fatigue ?: 0) >= 50 -> 1.3; else -> 1.0 } else 1.0
        // Back too soon, or playing hurt: much likelier to go again.
        val risk = when {
            p.hurtPlaying -> 1.8
            p.limited > 0 && p.injPlan == "rush" -> 2.5
            p.limited > 0 -> 1.5
            else -> 1.0
        } * (1.0 + (p.injCount - 2).coerceAtLeast(0) * 0.12)
        return base * (sec / 1800.0) * age * body * tired * risk * DevPlans.injuryFactor(p) * Facilities.injury(lg, lg.teams.getOrNull(p.teamId))
    }

    /** What he hurt: by grade, often a part he's hurt before, and worse if he was still limited (aggravated). */
    private fun diagnose(p: Player): Pair<Kind, Boolean> {
        val aggravated = (p.limited > 0 || p.hurtPlaying) && p.lastPart.isNotEmpty() && Rng.chance(0.55)
        val r = Rng.d()
        var grade = when { r < 0.55 -> 1; r < 0.85 -> 2; r < 0.97 -> 3; else -> 4 }
        if (aggravated) grade = (grade + 1).coerceAtMost(4)
        val pool = KINDS.filter { it.grade == grade }
        val part = when {
            aggravated -> p.lastPart
            p.injHistory.isNotEmpty() && Rng.chance(0.35) -> p.lastPart
            else -> ""
        }
        val kind = pool.filter { part.isNotEmpty() && it.part == part }.takeIf { it.isNotEmpty() }?.let { Rng.pick(it) } ?: Rng.pick(pool)
        return kind to aggravated
    }

    private fun hurt(lg: League, t: Team, p: Player) {
        val (k, aggravated) = diagnose(p)
        val games = if (k.grade >= 4) SEASON_OUT else Rng.range(k.lo, k.hi)
        p.injury = k.what
        p.injPart = k.part
        p.lastPart = k.part
        p.injGrade = k.grade
        p.injPlan = "standard"
        p.injLeft = games.toDouble()
        p.injGames = games
        p.injTotal = games
        p.limited = 0
        p.hurtPlaying = false
        p.injCount++
        p.injHistory += "${lg.season} · ${k.what}" + (if (aggravated) " (aggravated)" else "") + " · " + (if (games >= SEASON_OUT) "out for the season" else "${GRADES[k.grade].lowercase()}, $games game${if (games == 1) "" else "s"}")
        while (p.injHistory.size > 12) p.injHistory.removeAt(0)
        // Coaches re-do the rotation around him.
        if (!lg.humanCoach(t) || t.plan.auto) Tactics.autoAllocate(lg, t)
        val mine = t.id == lg.userTeamId
        val star = lg.rosterOf(t).sortedByDescending { it.ovr }.take(2).any { it.id == p.id }
        val hero = p.id == lg.heroId
        if (!(mine || hero || (star && games >= 4))) return
        val team = if (lg.pro) "the ${t.name}" else t.school
        val Team = team.replaceFirstChar { it.uppercase() }
        val how = howLong(lg, games)
        val head = when {
            aggravated -> "${p.name} aggravates his ${k.part}: ${if (games >= SEASON_OUT) "out for the season" else "out $how"}"
            games >= SEASON_OUT -> "${p.name} out for the season with ${k.what}"
            games >= 4 -> "${p.name} out $how with ${k.what}"
            else -> "${p.name} day-to-day with ${k.what}"
        }
        val line = if (p.stats.gp > 0) "He's averaging ${"%.1f".format(p.stats.ppg)} points, ${"%.1f".format(p.stats.rpg)} rebounds and ${"%.1f".format(p.stats.apg)} assists this season." else ""
        val body = listOf(
            "${p.name} left with ${k.what}" + (if (games >= SEASON_OUT) " and won't be back this season." else ", a ${GRADES[k.grade].lowercase()} injury, and the team expects him to miss ${if (games == 1) "a game" else "about $games games"}."),
            if (aggravated) "He was back from a ${k.part} injury, and still not 100%." else "",
            line,
            "$Team are ${t.record}." + if (star && games >= 10) " Without him, the rotation tightens and everyone else moves up a spot." else "",
        ).filter { it.isNotBlank() }.joinToString(" ")
        lg.news += News(lg.season, lg.day, head, mine || hero, -1, "injury", body, "Hoops Wire", t.id, p.id)
        if (games >= SEASON_OUT && (star || mine || hero)) Timeline.record(lg, "injury", "${p.name} lost for the season",
            "${p.name} (${t.school}) was ruled out for the year with ${k.what}" + (if (p.stats.gp > 0) " after averaging ${"%.1f".format(p.stats.ppg)} points." else "."), t.id, p.id, weight = if (star) 3 else 2)
        val plan = if (k.grade in 2..3) " Pick a recovery plan on the injury report: rest fully, standard, or rush back." else ""
        if (hero) Alerts.add(lg, if (games >= SEASON_OUT) "Season over" else if (aggravated) "You aggravated it" else "You're hurt",
            if (games >= SEASON_OUT) "You tore it up: ${k.what}. You're done for the season." else "${k.what.replaceFirstChar { it.uppercase() }}: you'll miss ${if (games == 1) "a game" else "about $games games"}.$plan", "injury")
        else if (mine && k.grade >= 2) Alerts.add(lg, "${p.name} is hurt", "${k.what.replaceFirstChar { it.uppercase() }} (${GRADES[k.grade].lowercase()}): about ${if (games >= SEASON_OUT) "the season" else "$games games"}.$plan", "injury")
    }

    private fun healed(lg: League, t: Team, p: Player) {
        val was = p.injury
        val long = p.injTotal >= 10
        val medical = p.injGrade > 0
        p.limited = if (medical) limitedAfter(p.injPlan, p.injGrade) else 0
        val plan = p.injPlan
        p.injury = ""
        p.injTotal = 0
        p.injGames = 0
        p.injLeft = 0.0
        p.injGrade = 0
        p.injPart = ""
        if (!lg.humanCoach(t) || t.plan.auto) Tactics.autoAllocate(lg, t)
        val mine = t.id == lg.userTeamId
        val star = lg.rosterOf(t).sortedByDescending { it.ovr }.take(2).any { it.id == p.id }
        val how = when { !medical -> ""; plan == "rush" -> " ahead of schedule, though he'll be limited for a while"; plan == "rest" -> ", fully healed"; p.limited > 0 -> ", though he'll be eased back in"; else -> "" }
        if (long && (mine || star || p.id == lg.heroId)) {
            lg.news += News(lg.season, lg.day, "${p.name} is back from $was", mine || p.id == lg.heroId, -1, "injury",
                "${p.name} is cleared to play after missing time with $was$how.", "Hoops Wire", t.id, p.id)
        } else if (mine) lg.news += News(lg.season, lg.day, "${p.name} returns", true)
    }

    /** A season-ending knee, Achilles or foot injury takes some explosiveness for good. */
    private fun lastingCost(p: Player) {
        if (p.injGrade < 4 || p.injPart !in listOf("knee", "Achilles", "foot")) return
        val cost = if (p.injPart == "Achilles") 3 else 2
        for (a in listOf(Attr.PER, Attr.STL, Attr.BLK)) p.attrs[a.ordinal] = (p.attrs[a.ordinal] - cost).coerceAtLeast(25)
        p.attrs[Attr.INS.ordinal] = (p.attrs[Attr.INS.ordinal] - 1).coerceAtLeast(25)
        p.pot = (p.pot - 1).coerceAtLeast(p.ovr)
        p.lostBurst += cost
        p.injHistory += "Came back from ${p.injury} a step slower (−$cost to his quickness and defense)"
    }

    /** "two weeks", "about a month", in games for college. */
    fun howLong(lg: League, games: Int): String = when {
        games >= SEASON_OUT -> "for the season"
        !lg.pro -> "$games game${if (games == 1) "" else "s"}"
        games <= 4 -> "about a week"
        games <= 7 -> "about two weeks"
        games <= 11 -> "about three weeks"
        games <= 16 -> "about a month"
        else -> "${(games / 3.5 / 4.3).roundToInt().coerceAtLeast(1)}-plus months".replace("1-plus months", "over a month")
    }

    /** "Out 6 games · a sprained ankle" for a roster row, or the status when he's back but not 100%. */
    fun tag(p: Player): String = when {
        p.out -> (if (p.injGames >= SEASON_OUT) "Out for the season" else if (p.injGrade in 1..2 && p.injGames <= 2) status(p) else if (p.injGames <= 3 && p.injTotal <= 3) "Day-to-day" else "Out ${p.injGames} more game${if (p.injGames == 1) "" else "s"}") +
            " · " + p.injury.removePrefix("a ").removePrefix("an ")
        p.hurtPlaying -> "Playing through ${p.injury.removePrefix("a ").removePrefix("an ")}"
        p.limited > 0 -> "Limited · ${p.limited} more game${if (p.limited == 1) "" else "s"} below 100%"
        else -> ""
    }

    /** Everyone hurt or not yet 100% on [t], longest out first. */
    fun report(lg: League, t: Team): List<Player> = lg.rosterOf(t).filter { it.out || it.limited > 0 || it.hurtPlaying }
        .sortedWith(compareByDescending<Player> { it.out }.thenByDescending { it.injGames }.thenByDescending { it.limited })

    /** Every team's notable injuries: starters and better, longest out first. */
    fun league(lg: League): List<Pair<Player, Team>> = lg.teams.flatMap { t ->
        val top = lg.rosterOf(t).sortedByDescending { it.ovr }.take(6).map { it.id }.toSet()
        lg.rosterOf(t).filter { it.out && it.id in top }.map { it to t }
    }.sortedWith(compareByDescending<Pair<Player, Team>> { it.first.ovr }.thenByDescending { it.first.injGames })

    /** A new season (or its end): everyone heals; a season-ending injury leaves its mark; old injuries fade. */
    fun clearAll(lg: League) {
        for (p in lg.players) {
            if (p.injGrade >= 4) lastingCost(p)
            p.injury = ""; p.injGames = 0; p.injTotal = 0
            p.injPart = ""; p.injGrade = 0; p.injLeft = 0.0; p.limited = 0; p.hurtPlaying = false
            p.injCount /= 2
        }
    }
}
