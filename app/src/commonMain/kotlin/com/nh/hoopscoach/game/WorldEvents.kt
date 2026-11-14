package com.nh.hoopscoach.game

import kotlin.math.roundToInt

/**
 * The world acting on its own, and the world's history acting back on it.
 *
 * Disruptions (a scandal, a coach leaving, a conference raid, a donor's gift, a freshman out of nowhere,
 * a rival stealing a recruit) happen on conditions and odds, never as a blind roll. They land on the
 * [Timeline] and then change things: rosters (players leave a program in trouble or follow a coach who
 * left), recruiting (scholarships taken away; recruits drawn to a program's recent history or turned off
 * by it), prestige, fans and legacy, the postseason (a banned program stays home) and coaches' jobs.
 */
object WorldEvents {
    /** How each kind of event colours recruits' view of a school, before it fades over three seasons. */
    private val PULL = mapOf(
        "title" to 4, "perfect" to 2, "dynasty" to 3, "f4first" to 3, "rise" to 3, "return" to 2, "upset" to 2,
        "commit" to 1, "donor" to 2, "breakout" to 1, "record" to 1, "tree" to 1, "realign" to 2, "raid" to 2,
        "scandal" to -7, "sanctions" to -4, "fire" to -1, "transfer" to -1, "exodus" to -2,
    )

    /**
     * Recruiting pull from a school's recent history: up to +6 for a title-winning, rising program and
     * down to -8 under a scandal. Recent events count fully, older ones fade.
     */
    fun aura(lg: League, teamId: Int): Int {
        var v = 0.0
        for (e in lg.timeline) {
            if (e.teamId != teamId) continue
            val age = lg.season - e.season
            if (age < 0 || age > 2) continue
            v += (PULL[e.type] ?: 0) * when (age) { 0 -> 1.0; 1 -> 0.6; else -> 0.3 }
        }
        return v.roundToInt().coerceIn(-8, 6)
    }

    // ---------- sanctions ----------

    /** Sitting out this season's postseason. */
    fun banned(lg: League, teamId: Int): Boolean = !lg.pro && lg.teams.getOrNull(teamId)?.banSeason == lg.season

    /** Scholarships it can fill: the usual 13, less any taken away by sanctions. */
    fun cap(lg: League, t: Team): Int =
        Recruiting.MAX_ROSTER - if (!lg.pro && t.sanctionUntil >= lg.season) t.scholarshipsLost else 0

    /** A short description of what's hanging over a program, for its page ("" if nothing). */
    fun sanctionLine(lg: League, t: Team): String = listOfNotNull(
        "Banned from the ${lg.season} postseason".takeIf { t.banSeason == lg.season },
        "Banned from the ${lg.season + 1} postseason".takeIf { t.banSeason == lg.season + 1 },
        "${t.scholarshipsLost} scholarship${if (t.scholarshipsLost == 1) "" else "s"} lost through ${t.sanctionUntil}".takeIf { t.sanctionUntil >= lg.season && t.scholarshipsLost > 0 },
    ).joinToString(" · ")

    // ---------- rosters ----------

    /**
     * How much likelier a player is to leave this offseason because of what happened around him: a new
     * head coach unsettles a roster, NCAA sanctions free players to go, and close friends on the team
     * keep him.
     */
    fun transferFactor(lg: League, p: Player): Double {
        if (p.teamId < 0 || lg.pro) return 1.0
        val t = lg.team(p.teamId)
        var f = friendsFactor(lg, p)
        val c = lg.coachOf(t)
        if (!lg.humanCoach(t) && c.history.none { it.teamId == t.id }) f *= if (p.has(Trait.LOYAL)) 1.2 else 1.8
        if (t.banSeason > lg.season) f *= 2.5
        return f
    }

    /**
     * A coach hired away takes some of his players with him: his best young players at the old school
     * go into the portal with his new school first in line.
     */
    fun followCoach(lg: League, from: Team, to: Team, c: Coach) {
        if (lg.pro) return
        val declared = lg.offseason?.entrants.orEmpty().toSet()
        val followers = lg.rosterOf(from).filter { p ->
            p.year <= 2 && p.ovr >= 66 && !p.walkOn && p.id != lg.heroId && p.id != lg.road?.rivalId && p.id !in declared && !p.has(Trait.LOYAL)
        }.sortedByDescending { it.ovr }.take(2).filter { Rng.chance(0.55) }
        for (p in followers) {
            from.roster.remove(p.id)
            p.teamId = -1
            p.transferFrom = from.id
            val r = Recruit(p, transfer = true)
            r.nilAsk = Ratings.expectedNil(p)
            Recruiting.seedFog(r, known = from.id == lg.userTeamId)
            r.pref = Recruiting.pickPref(4)
            r.interest[to.id] = 85
            lg.recruits += r
            Memories.add(lg, p, "coach", "Followed ${c.name} from ${from.school}", to.id, 2)
            Timeline.record(lg, "follow", "${p.name} follows ${c.name} out of ${from.school}",
                "${p.name} entered the transfer portal after ${c.name} left ${from.school} for ${to.school}. ${to.school} is the favorite to land him.",
                to.id, p.id, c.name, weight = if (p.ovr >= 75) 3 else 2, team2 = from.id)
        }
        if (followers.isNotEmpty()) {
            lg.news += News(lg.season, lg.day, "${followers.joinToString(" and ") { it.name }} ${if (followers.size == 1) "follows" else "follow"} ${c.name} into the portal",
                from.id == lg.userTeamId || to.id == lg.userTeamId, kind = "move", teamId = to.id,
                body = "${c.name}'s move from ${from.school} to ${to.school} is costing ${from.school} more than a coach.", source = "Hoops Wire")
            if (from.prestige >= 50) Timeline.record(lg, "exodus", "${from.school} loses its coach and ${followers.size} player${if (followers.size == 1) "" else "s"}",
                "${c.name} left for ${to.school}, and ${followers.joinToString(" and ") { it.name }} went with him.", from.id, coach = c.name, weight = 2)
        }
    }

    // ---------- conferences ----------

    /** A school changing leagues: moving up a level lifts the program; the league it leaves loses some shine. */
    fun realigned(lg: League, t: Team, old: Conference, new: Conference) {
        Brand.ensure(lg)
        if (new.tier < old.tier) {
            t.prestige = (t.prestige + 2 * (old.tier - new.tier)).coerceAtMost(99)
            t.legacy = (t.legacy + 1.0).coerceAtMost(99.0)
            Fans.ensure(t)
            t.fans = (t.fans + 5).coerceAtMost(100.0)
        }
        val wasBest = old.teamIds.none { lg.team(it).prestige > t.prestige }
        if (wasBest) for (id in old.teamIds) {
            val o = lg.team(id)
            o.buzz = (o.buzz - 3).coerceAtLeast(0.0)
        }
        // His league-mates are now opponents twice a year only if they follow him: old conference rivals lose a date.
        val lostRivals = lg.rivalsOf(t.id).filter { lg.team(it.other(t.id)).confId == old.id }
        Timeline.record(lg, "realign", "${t.school} joins the ${new.name}",
            "${t.school} left the ${old.name} for the ${new.name}" +
                (if (lostRivals.isNotEmpty()) ", leaving ${lostRivals.joinToString(" and ") { lg.team(it.other(t.id)).school }} behind." else "."),
            t.id, weight = if (new.tier == 0 && old.tier > 0) 3 else 2)
    }

    /**
     * Now and then a power conference raids a smaller league for its best program, whether or not that
     * school was looking to move.
     */
    fun raid(lg: League) {
        if (lg.pro || !Rng.chance(0.12)) return
        val raider = lg.confs.filter { it.tier == 0 && it.teamIds.size < Realignment.MAX_SIZE }
            .maxByOrNull { c -> c.teamIds.map { lg.team(it).prestige }.average() + Rng.d() * 8 } ?: return
        val avg = raider.teamIds.map { lg.team(it).prestige }.average()
        val target = lg.teams.filter { t ->
            t.id != lg.userTeamId && lg.conf(t.confId).tier == 1 && t.prestige >= avg - 14
        }.maxByOrNull { it.prestige + Awards.tourneyRound(lg, it.id) * 3 } ?: return
        val old = lg.conf(target.confId)
        Realignment.join(lg, target.id, raider.id)
        Timeline.record(lg, "raid", "The ${raider.name} raids the ${old.name} for ${target.school}",
            "The ${raider.name} went after ${target.school}, the ${old.name}'s best program, and got it.", target.id, weight = 3)
        lg.news += News(lg.season, lg.day, "Conference raid: ${target.school} jumps from the ${old.name} to the ${raider.name}",
            old.id == lg.user.confId || raider.id == lg.user.confId, kind = "move", teamId = target.id,
            body = "${target.school} (${target.w}-${target.l}) takes a seat in the ${raider.name}. The ${old.name} loses its flagship program and the exposure that came with it.",
            source = "Hoops Wire")
    }

    // ---------- the season's end ----------

    /**
     * After [Timeline.seasonEnd] and before prestige is recalculated: records broken, dynasties, unbeaten
     * regular seasons and rivals meeting in March, then what this season's history does to each program's
     * fans and legacy (legacy feeds next year's prestige).
     */
    fun seasonEnd(lg: League) {
        if (lg.pro) return
        Brand.ensure(lg)
        runCatching { RecruitRivals.seasonEnd(lg) }
        runCatching { HomeCourt.seasonEnd(lg) }
        runCatching { RivalryTrophies.seasonEnd(lg) }
        runCatching { Challenges.seasonEnd(lg) }
        runCatching { Shoes.marchBonuses(lg) }
        records(lg)
        dynasties(lg)
        unbeatenRegularSeasons(lg)
        rivalsInMarch(lg)
        for (e in lg.timeline.filter { it.season == lg.season }) {
            val t = lg.teams.getOrNull(e.teamId) ?: continue
            Fans.ensure(t)
            when (e.type) {
                "title" -> { t.fans += 6.0; t.legacy += if (t.titles == 1) 2.0 else 0.8 }
                "dynasty" -> t.legacy += 2.0
                "perfect" -> { t.fans += 4.0; t.legacy += 1.0 }
                "f4first" -> { t.fans += 6.0; t.legacy += 1.5 }
                "rise" -> t.fans += 5.0
                "return", "upset" -> t.fans += 3.0
                "record" -> t.fans += 1.0
            }
            t.fans = t.fans.coerceIn(5.0, 100.0)
            t.legacy = t.legacy.coerceIn(5.0, 99.0)
        }
    }

    /** A school's single-season scoring mark, and the national one, from every season on record. */
    private fun records(lg: League) {
        val everyone = (lg.players + lg.alumni).distinctBy { it.id }
        val past = everyone.flatMap { p -> p.career.filter { it.season < lg.season && it.s.gp >= 15 }.map { p to it } }
        if (past.isEmpty()) return
        val national = past.maxByOrNull { it.second.s.ppg }
        for (t in lg.teams) {
            val best = lg.rosterOf(t).filter { it.stats.gp >= 15 }.maxByOrNull { it.stats.ppg } ?: continue
            val old = past.filter { it.second.teamId == t.id }.maxByOrNull { it.second.s.ppg } ?: continue
            if (t.history.size < 3 || best.stats.ppg <= old.second.s.ppg + 0.05) continue
            val years = lg.season - old.second.season
            val f = { v: Double -> "%.1f".format(v) }
            Timeline.record(lg, "record", "${best.name} sets ${t.school}'s scoring record",
                "${best.name} averaged ${f(best.stats.ppg)} points, past ${old.first.name}'s ${f(old.second.s.ppg)} from ${old.second.season}" +
                    (if (years >= 10) ", a mark that had stood for $years years." else "."), t.id, best.id, weight = if (years >= 20) 4 else 2)
        }
        val top = lg.players.filter { it.teamId >= 0 && it.stats.gp >= 15 }.maxByOrNull { it.stats.ppg } ?: return
        if (national != null && top.stats.ppg > national.second.s.ppg + 0.05 && lg.history.size >= 5)
            Timeline.record(lg, "record", "${top.name} sets the national scoring record",
                "${top.name} averaged ${"%.1f".format(top.stats.ppg)} points a game, the most on record, past ${national.first.name}'s ${"%.1f".format(national.second.s.ppg)} in ${national.second.season}.",
                top.teamId, top.id, weight = 5)
    }

    /** Three titles in five seasons, or four Final Fours in five. */
    private fun dynasties(lg: League) {
        val window = lg.history.takeLast(4)          // this season's summary isn't in history yet
        val ncaa = lg.ncaa ?: return
        val champs = (window.map { it.champion } + ncaa.champion).groupingBy { it }.eachCount()
        val lastFour = ncaa.rounds.getOrNull(4)?.filter { it >= 0 }.orEmpty()
        val f4 = (window.flatMap { it.finalFour } + lastFour).groupingBy { it }.eachCount()
        for (t in lg.teams) {
            val titles = champs[t.id] ?: 0
            val fours = f4[t.id] ?: 0
            if (titles >= 3 && ncaa.champion == t.id) Timeline.record(lg, "dynasty", "The ${t.school} dynasty: $titles titles in five years",
                "${t.school} has won $titles of the last five national championships under ${lg.coachOf(t).name}.", t.id, coach = lg.coachOf(t).name, weight = 5)
            else if (fours >= 4 && t.id in lastFour) Timeline.record(lg, "dynasty", "${t.school}: four Final Fours in five years",
                "${t.school} has been to $fours of the last five Final Fours.", t.id, coach = lg.coachOf(t).name, weight = 4)
        }
    }

    private fun unbeatenRegularSeasons(lg: League) {
        val regular = setOf(GameType.CONF, GameType.NONCONF)
        for (t in lg.teams) {
            val games = lg.games.filter { it.played && it.type in regular && (it.home == t.id || it.away == t.id) }
            if (games.size < 20 || games.any { winnerOf(it) != t.id } || t.l == 0) continue      // a perfect season is recorded on its own
            Timeline.record(lg, "unbeaten", "${t.school} goes unbeaten in the regular season",
                "${t.school} won all ${games.size} regular-season games before finishing ${t.w}-${t.l}.", t.id, coach = lg.coachOf(t).name, weight = 4)
        }
    }

    private fun winnerOf(g: Game) = if (g.hs > g.aws) g.home else g.away

    /** Rivals who knock each other out of the NCAA tournament. */
    private fun rivalsInMarch(lg: League) {
        for (g in lg.games.filter { it.played && it.type == GameType.NCAA }) {
            val r = lg.rivalry(g) ?: continue
            val wId = winnerOf(g)
            val w = lg.team(wId)
            val l = lg.team(if (wId == g.home) g.away else g.home)
            val (ws, ls) = if (wId == g.home) g.hs to g.aws else g.aws to g.hs
            Timeline.record(lg, "rivalry", "${w.school} ends ${l.school}'s season",
                "${w.school} beat its rival ${l.school} $ws-$ls in the NCAA tournament" + (if (r.name.isNotBlank()) ", the biggest chapter yet in the ${r.name}." else "."),
                w.id, team2 = l.id, weight = 4)
        }
    }

    // ---------- during the season ----------

    /** Daily: a freshman nobody saw coming, recognised once he has a real body of work. */
    fun daily(lg: League) {
        if (lg.pro || lg.phase != Phase.REGULAR) return
        if (lg.day % 7 == 0) Compliance.check(lg)
        if (lg.day != Schedule.REG_DAYS / 2) return
        for (t in lg.teams) {
            val p = lg.rosterOf(t).filter { it.year == 1 && it.stats.gp >= 8 && it.stats.ppg >= 14.0 && it.stars <= 3 }.maxByOrNull { it.stats.ppg } ?: continue
            Timeline.record(lg, "breakout", "${p.stars}-star freshman ${p.name} breaks out at ${t.school}",
                "${p.name} came in as a ${p.stars}-star recruit and is averaging ${"%.1f".format(p.stats.ppg)} points as a freshman.", t.id, p.id, weight = if (p.stars <= 2) 3 else 2)
            Memories.add(lg, p, "breakout", "Broke out as a freshman at ${t.school}", t.id, 1)
        }
    }

    // ---------- the offseason ----------

    /**
     * Before the coaching carousel: scandals and donors. A scandal is most likely where a program spends
     * far more on players than schools like it, under a coach fighting for his job. A donor gives after a
     * big season.
     */
    fun offseason(lg: League) {
        if (lg.pro) return
        Brand.ensure(lg)
        runCatching { Compliance.offseason(lg) }
        val spend = lg.teams.associate { it.id to Morale.nilSpent(lg, it).toDouble() }
        val bands = lg.teams.groupBy { it.prestige / 10 }.mapValues { (_, ts) -> ts.map { spend.getValue(it.id) }.average().coerceAtLeast(1.0) }
        for (t in lg.teams) {
            if (lg.humanCoach(t)) continue
            val c = lg.coachOf(t)
            val ratio = spend.getValue(t.id) / bands.getValue(t.prestige / 10)
            var p = 0.0015
            if (ratio > 1.4) p += (ratio - 1.4) * 0.03
            if (c.security < 30) p += 0.01
            if (c.has(CoachSkill.CULTURE)) p *= 0.4
            if (lg.timeline.any { it.teamId == t.id && it.type == "scandal" && lg.season - it.season < 4 }) p = 0.0
            if (Rng.chance(p.coerceAtMost(0.08))) scandal(lg, t, c, major = ratio > 1.8 || Rng.chance(0.3))
        }
        for (t in lg.teams) {
            val round = Awards.tourneyRound(lg, t.id)
            Fans.ensure(t)
            val chance = when { round >= 5 -> 0.6; round >= 3 -> 0.3; t.w - t.expWins >= 5 -> 0.15; else -> 0.0 } * (0.5 + t.fans / 100.0)
            if (!Rng.chance(chance)) continue
            val gift = (500 + Rng.range(0, 8) * 250 + round * 250).toLong()     // thousands
            Business.ensure(lg, t)
            t.cash += gift
            t.fans = (t.fans + 3).coerceAtMost(100.0)
            Timeline.record(lg, "donor", "A ${Business.money(gift)} gift for ${t.school} basketball",
                "After ${t.school}'s ${t.w}-${t.l} season, a donor gave ${Business.money(gift)} to the program for facilities and recruiting.", t.id, weight = if (gift >= 2000) 2 else 1)
            if (t.id == lg.userTeamId) lg.news += News(lg.season, lg.day, "A donor gives ${Business.money(gift)} to ${t.school} basketball", true, kind = "move", teamId = t.id,
                body = "The gift follows a ${t.w}-${t.l} season. It goes into the program's budget for upgrades.", source = "Hoops Wire")
        }
    }

    /**
     * An NCAA case. Every one costs prestige, fans and the coach's standing, takes scholarships away and
     * scares off recruits; a major one also bans the program from next season's postseason, which frees
     * its players to transfer, and all but ends the coach's tenure.
     */
    internal fun scandal(lg: League, t: Team, c: Coach, major: Boolean, what: String = Rng.pick(listOf("recruiting violations", "improper benefits to players", "academic misconduct"))) {
        val mine = t.id == lg.userTeamId
        t.prestige = (t.prestige - if (major) Rng.range(5, 8) else Rng.range(2, 4)).coerceAtLeast(5)
        t.legacy = (t.legacy - if (major) 3.0 else 1.5).coerceAtLeast(5.0)
        Fans.ensure(t)
        t.fans = (t.fans - if (major) 14 else 8).coerceAtLeast(5.0)
        c.security = (c.security - if (major) 45 else 25).coerceAtLeast(0)
        val already = if (t.sanctionUntil >= lg.season) t.scholarshipsLost else 0
        t.scholarshipsLost = (already + if (major) 3 else Rng.range(1, 2)).coerceAtMost(4)
        t.sanctionUntil = maxOf(t.sanctionUntil, lg.season + if (major) 2 else 1)
        if (major) t.banSeason = lg.season + 1
        val penalties = listOfNotNull(
            "a postseason ban for ${lg.season + 1}".takeIf { major },
            "${t.scholarshipsLost} scholarship${if (t.scholarshipsLost == 1) "" else "s"} taken away through ${t.sanctionUntil}",
            "players free to transfer without sitting out".takeIf { major },
        )
        Timeline.record(lg, "scandal", "${t.school} hit by an investigation into $what",
            "The NCAA found $what at ${t.school} under ${c.name}. Penalties: ${penalties.joinToString(", ")}.", t.id, coach = c.name, weight = if (major) 4 else 3)
        lg.news += News(lg.season, lg.day, "NCAA ${if (major) "hammers" else "sanctions"} ${t.school} over $what", mine, kind = "move", teamId = t.id,
            body = "${t.school} (${t.w}-${t.l}) gets ${penalties.joinToString(", ")}. Recruits are already looking elsewhere, and ${c.name}'s job is in question.",
            source = "Hoops Wire")
        if (mine) {
            Alerts.add(lg, if (major) "The NCAA hammers your program" else "NCAA sanctions",
                "The investigation into $what is done: ${penalties.joinToString(", ")}. " +
                    "Your job security took a hit" + (if (c.security < 30) " and you're on the hot seat." else ".") +
                    " Keep NIL deals at market value from here on.", "")
            return
        }
        val u = lg.user
        val chasing = lg.recruits.count { it.interest.containsKey(t.id) && it.interest.containsKey(u.id) }
        if (t.confId == u.confId || lg.rivalry(t.id, u.id) != null || chasing > 0) Alerts.add(lg, "Scandal at ${t.school}",
            "${t.school} ${if (major) "is banned from the postseason and " else ""}lost ${t.scholarshipsLost} scholarship${if (t.scholarshipsLost == 1) "" else "s"}." +
                (if (chasing > 0) " $chasing recruit${if (chasing == 1) "" else "s"} you're both chasing may look your way." else "") +
                (if (major) " Its players can transfer freely: watch the portal." else ""), "")
    }

    /** Extra odds a coach is fired this offseason because of a scandal on his watch. */
    fun scandalFireChance(lg: League, t: Team): Double {
        val e = lg.timeline.lastOrNull { it.teamId == t.id && it.type == "scandal" && it.season == lg.season } ?: return 0.0
        return if (t.banSeason == lg.season + 1) 0.7 else 0.3
    }

    /** A recruit the user was chasing signs with a rival instead. */
    fun stolen(lg: League, r: Recruit, from: Team, to: Team) {
        Timeline.record(lg, "steal", "${to.school} beats ${from.school} for ${r.p.name}",
            "${r.p.stars}-star ${r.p.pos} ${r.p.name} chose ${to.school} after ${from.school} had been in on him.", to.id, r.p.id, team2 = from.id, weight = if (r.p.stars >= 5) 3 else 2)
    }

    // ---------- relationships ----------

    /** Teammates he has shared at least [seasons] seasons with, from both careers. */
    fun teammates(lg: League, p: Player, seasons: Int = 1): List<Player> {
        val mine = p.career.map { it.season to it.teamId }.toSet() + if (p.teamId >= 0) setOf(lg.season to p.teamId) else emptySet()
        return (lg.players + lg.alumni).distinctBy { it.id }.filter { o ->
            o.id != p.id && (o.career.count { (it.season to it.teamId) in mine } + if (o.teamId >= 0 && (lg.season to o.teamId) in mine) 1 else 0) >= seasons
        }
    }

    /**
     * Close friends still on his team make him likelier to stay: each teammate he's been through two
     * or more seasons with takes a little off his odds of leaving.
     */
    fun friendsFactor(lg: League, p: Player): Double {
        if (p.teamId < 0) return 1.0
        val mine = p.career.map { it.season to it.teamId }.toSet()
        val close = lg.team(p.teamId).roster.count { id ->
            id != p.id && lg.playerOrNull(id)?.career?.count { (it.season to it.teamId) in mine }?.let { it >= 2 } == true
        }
        return Math.pow(0.9, close.coerceAtMost(4).toDouble())
    }
}
