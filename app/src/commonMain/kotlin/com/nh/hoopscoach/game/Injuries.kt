package com.nh.hoopscoach.game

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Players get hurt. Every game a player logs real minutes there's a small chance (more for heavy
 * minutes, older bodies, a body that wears down early, and a tired hero), from a sore ankle that costs
 * a game or two to a torn ACL that ends his season. An injured player sits until his team has played
 * out the games he'll miss. Veteran stars on AI teams also sit the odd regular-season night to rest.
 */
object Injuries {
    private val MINOR = listOf("a sprained left ankle", "a sprained right ankle", "a sore knee", "a bruised hip", "back spasms", "a jammed finger",
        "hamstring tightness", "a sore Achilles", "an illness", "a sprained wrist", "calf tightness", "groin tightness")
    private val SHORT = listOf("a sprained ankle", "knee soreness", "a hamstring strain", "a bone bruise", "a sprained thumb", "a calf strain",
        "a concussion", "a hip flexor strain", "a rib contusion", "a sprained MCL")
    private val LONG = listOf("a high ankle sprain", "a sprained MCL", "a broken hand", "a stress reaction in his foot", "a torn ligament in his thumb",
        "a fractured wrist", "a partially torn hamstring", "a torn meniscus")
    private val SEASON = listOf("a torn ACL", "a ruptured Achilles", "a broken foot", "a torn meniscus that needs surgery", "a fractured kneecap")

    /** Out for what's left of the season (cleared when it ends). */
    const val SEASON_OUT = 999

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

    // ---------- after each game ----------

    fun afterGame(lg: League, game: Game, teams: Array<Team>, lines: Array<LinkedHashMap<Int, StatLine>>) {
        for (side in 0..1) {
            val t = teams[side]
            // Those who missed this one are a game closer to coming back.
            for (p in lg.rosterOf(t)) if (p.out && p.id !in lines[side] && p.injGames < SEASON_OUT) {
                p.injGames--
                if (p.injGames == 0) healed(lg, t, p)
            }
            for ((id, st) in lines[side]) {
                if (st.sec < 300) continue
                val p = lg.playerOrNull(id) ?: continue
                if (p.out) continue
                if (Rng.d() < chance(lg, p, st.sec)) hurt(lg, t, p)
            }
        }
    }

    private fun chance(lg: League, p: Player, sec: Int): Double {
        val base = if (lg.pro) 0.008 else 0.006
        val age = 1.0 + max(0, p.age - 27) * 0.04
        val body = 1.5 - Longevity.of(p) / 100.0
        val tired = if (p.id == lg.heroId) when { (lg.road?.fatigue ?: 0) >= 70 -> 2.0; (lg.road?.fatigue ?: 0) >= 50 -> 1.3; else -> 1.0 } else 1.0
        return base * (sec / 1800.0) * age * body * tired * DevPlans.injuryFactor(p)
    }

    private fun hurt(lg: League, t: Team, p: Player) {
        val r = Rng.d()
        val (what, games) = when {
            r < 0.55 -> Rng.pick(MINOR) to Rng.range(1, 3)
            r < 0.85 -> Rng.pick(SHORT) to Rng.range(4, 12)
            r < 0.97 -> Rng.pick(LONG) to Rng.range(13, 30)
            else -> Rng.pick(SEASON) to SEASON_OUT
        }
        p.injury = what
        p.injGames = games
        p.injTotal = games
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
            games >= SEASON_OUT -> "${p.name} out for the season with $what"
            games >= 4 -> "${p.name} out $how with $what"
            else -> "${p.name} day-to-day with $what"
        }
        val line = if (p.stats.gp > 0) "He's averaging ${"%.1f".format(p.stats.ppg)} points, ${"%.1f".format(p.stats.rpg)} rebounds and ${"%.1f".format(p.stats.apg)} assists this season." else ""
        val body = listOf(
            "${p.name} left with $what" + (if (games >= SEASON_OUT) " and won't be back this season." else ", and the team expects him to miss ${if (games == 1) "a game" else "about $games games"}."),
            line,
            "$Team ${if (lg.pro) "are" else "are"} ${t.record}." + if (star && games >= 10) " Without him, the rotation tightens and everyone else moves up a spot." else "",
        ).filter { it.isNotBlank() }.joinToString(" ")
        lg.news += News(lg.season, lg.day, head, mine || hero, -1, "injury", body, "Hoops Wire", t.id, p.id)
        if (games >= SEASON_OUT && (star || mine || hero)) Timeline.record(lg, "injury", "${p.name} lost for the season",
            "${p.name} (${t.school}) was ruled out for the year with $what" + (if (p.stats.gp > 0) " after averaging ${"%.1f".format(p.stats.ppg)} points." else "."), t.id, p.id, weight = if (star) 3 else 2)
        if (hero) Alerts.add(lg, if (games >= SEASON_OUT) "Season over" else "You're hurt",
            if (games >= SEASON_OUT) "You tore it up: $what. You're done for the season." else "${what.replaceFirstChar { it.uppercase() }}: you'll miss ${if (games == 1) "a game" else "about $games games"}.", "injury")
    }

    private fun healed(lg: League, t: Team, p: Player) {
        val was = p.injury
        val long = p.injTotal >= 10
        p.injury = ""
        p.injTotal = 0
        if (!lg.humanCoach(t) || t.plan.auto) Tactics.autoAllocate(lg, t)
        val mine = t.id == lg.userTeamId
        val star = lg.rosterOf(t).sortedByDescending { it.ovr }.take(2).any { it.id == p.id }
        if (long && (mine || star || p.id == lg.heroId)) {
            lg.news += News(lg.season, lg.day, "${p.name} is back from $was", mine || p.id == lg.heroId, -1, "injury",
                "${p.name} is cleared to play after missing time with $was.", "Hoops Wire", t.id, p.id)
        } else if (mine) lg.news += News(lg.season, lg.day, "${p.name} returns", true)
    }

    /** "two weeks", "about a month", in games for college. */
    fun howLong(lg: League, games: Int): String = when {
        games >= SEASON_OUT -> "for the season"
        !lg.pro -> "$games games"
        games <= 4 -> "about a week"
        games <= 7 -> "about two weeks"
        games <= 11 -> "about three weeks"
        games <= 16 -> "about a month"
        else -> "${(games / 3.5 / 4.3).roundToInt().coerceAtLeast(1)}-plus months".replace("1-plus months", "over a month")
    }

    /** "Out 6 games · a sprained ankle" for a roster row, or empty when he's healthy. */
    fun tag(p: Player): String = if (!p.out) "" else
        (if (p.injGames >= SEASON_OUT) "Out for the season" else if (p.injGames <= 3 && p.injTotal <= 3) "Day-to-day" else "Out ${p.injGames} more game${if (p.injGames == 1) "" else "s"}") +
            " · " + p.injury.removePrefix("a ").removePrefix("an ")

    /** Everyone hurt on [t], longest out first. */
    fun report(lg: League, t: Team): List<Player> = lg.rosterOf(t).filter { it.out }.sortedByDescending { it.injGames }

    /** Every team's notable injuries: starters and better, longest out first. */
    fun league(lg: League): List<Pair<Player, Team>> = lg.teams.flatMap { t ->
        val top = lg.rosterOf(t).sortedByDescending { it.ovr }.take(6).map { it.id }.toSet()
        lg.rosterOf(t).filter { it.out && it.id in top }.map { it to t }
    }.sortedWith(compareByDescending<Pair<Player, Team>> { it.first.ovr }.thenByDescending { it.first.injGames })

    /** A new season (or its end): everyone heals. */
    fun clearAll(lg: League) {
        for (p in lg.players) { p.injury = ""; p.injGames = 0; p.injTotal = 0 }
    }
}
