package com.nh.hoopscoach.game

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A pro player and his coach: trust earned game by game, talks with the coach (a bigger role, what he needs,
 * or buying in), and the coach's asks (five games to show him). Trust is minutes: the coach's rotation leans
 * toward the players he trusts, and a role he promised lasts the season.
 */
object CoachTrust {
    fun active(lg: League): Boolean {
        val h = lg.hero ?: return false
        return lg.playerMode && lg.pro && lg.road?.ended != true && h.teamId >= 0
    }

    /** A new team, a new coach: trust starts over. */
    private fun fresh(lg: League) {
        val r = lg.road ?: return
        val h = lg.hero ?: return
        if (r.trustTeam != h.teamId) {
            r.trustTeam = h.teamId
            r.coachTrust = 50
            r.rolePromise = 0
            r.coachAsk = ""; r.coachAskLeft = 0; r.coachAskHits = 0
        }
    }

    fun trust(lg: League): Int { fresh(lg); return lg.road?.coachTrust ?: 50 }

    fun label(t: Int) = when { t >= 80 -> "Trusted"; t >= 60 -> "Solid"; t >= 40 -> "Neutral"; t >= 20 -> "Shaky"; else -> "In the doghouse" }

    fun meaning(t: Int, promised: Boolean) = when {
        promised -> "He promised you a bigger role this season. Keep earning it."
        t >= 80 -> "He trusts you: more minutes, and the ball late in close games."
        t >= 60 -> "He likes what he sees. The minutes are starting to lean your way."
        t >= 40 -> "He's still deciding about you."
        t >= 20 -> "He's not sure about you. Your minutes are on the line."
        else -> "You're in his doghouse: the shortest leash on the team."
    }

    /** What trust adds to how the coach rates him for minutes: about -4 to +4, and 3 more for a promised role. */
    fun credit(lg: League, p: Player): Double {
        if (!active(lg) || p.id != lg.heroId) return 0.0
        val r = lg.road ?: return 0.0
        if (r.trustTeam != p.teamId) return 0.0
        return (r.coachTrust - 50) / 12.0 + if (r.rolePromise == lg.season) 3.0 else 0.0
    }

    private fun stamp(lg: League) = lg.season * 1000 + lg.day
    private fun inSeason(lg: League) = lg.phase == Phase.REGULAR || lg.phase == Phase.PLAYOFFS

    /** One talk every ten days. */
    fun canTalk(lg: League): Boolean = active(lg) && inSeason(lg) && stamp(lg) - (lg.road?.coachTalk ?: -99_999) >= 10
    fun daysToTalk(lg: League): Int = (10 - (stamp(lg) - (lg.road?.coachTalk ?: -99_999))).coerceAtLeast(0)

    private fun coach(lg: League) = lg.coachOf(lg.team(lg.hero!!.teamId)).last

    private fun reshuffle(lg: League) {
        val t = lg.teams.getOrNull(lg.hero?.teamId ?: -1) ?: return
        if (!lg.humanCoach(t) || t.plan.auto) Tactics.autoAllocate(lg, t)
    }

    /** The odds he says yes to a bigger role: his trust, how you've played lately, and how you stack up with the man ahead of you. */
    fun roleOdds(lg: League): Double {
        val h = lg.hero ?: return 0.0
        val r = lg.road ?: return 0.0
        val recent = h.log.takeLast(5)
        val form = if (recent.size >= 3 && h.stats.gp >= 5) (recent.map { it.pts }.average() - h.stats.ppg) / 6.0 else 0.0
        val ahead = Road.depth(lg).takeWhile { it.id != h.id }.lastOrNull()
        val gap = if (ahead != null) (h.ovr - ahead.ovr) / 12.0 else 0.2
        return (0.18 + (r.coachTrust - 50) / 90.0 + form.coerceIn(-0.2, 0.25) + gap.coerceIn(-0.3, 0.3)).coerceIn(0.05, 0.9)
    }

    /** Ask for a bigger role: yes is minutes for the season; no costs trust. */
    fun askForRole(lg: League): String {
        fresh(lg)
        val r = lg.road ?: return ""
        val h = lg.hero ?: return ""
        if (!canTalk(lg)) return ""
        r.coachTalk = stamp(lg)
        val name = coach(lg)
        if (r.rolePromise == lg.season) return "Coach $name: \"I already told you. You're getting your minutes.\""
        return if (Rng.chance(roleOdds(lg))) {
            r.rolePromise = lg.season
            r.coachTrust = (r.coachTrust - 2).coerceIn(0, 100)
            h.happiness = (h.happiness + 5).coerceAtMost(100)
            reshuffle(lg)
            lg.news += News(lg.season, lg.day, "${h.name} gets a bigger role with the ${lg.team(h.teamId).name}", true, kind = "role", pid = h.id, teamId = h.teamId,
                source = "Hoops Wire", body = "Coach $name says the young ${h.posLabel.lowercase()} has earned more minutes.")
            "Coach $name: \"You've earned it. You're getting more run, starting now.\""
        } else {
            r.coachTrust = (r.coachTrust - 7).coerceIn(0, 100)
            h.happiness = (h.happiness - 4).coerceAtLeast(0)
            "Coach $name: \"Earn it. Show me in the games, not in my office.\""
        }
    }

    private class AskKind(val id: String, val text: String, val hit: (GameLogEntry, Player) -> Boolean)

    private fun asks(h: Player): List<AskKind> {
        val big = h.pos == Pos.PF || h.pos == Pos.C
        val boards = if (big) 8 else 5
        val dimes = if (h.pos == Pos.PG) 6 else 3
        val pts = max(10, (h.stats.ppg + 3).roundToInt())
        return listOfNotNull(
            AskKind("defend", "Defend. Get your hands on the ball: two steals or blocks a night.") { l, _ -> l.stl + l.blk >= 2 },
            AskKind("board", "Rebound. $boards boards a night, every night.") { l, _ -> l.reb >= boards },
            AskKind("care", "Take care of the ball. One turnover or fewer.") { l, _ -> l.tov <= 1 && l.min >= 10 },
            AskKind("move", "Move it. $dimes assists a night: find the open man.") { l, _ -> l.ast >= dimes },
            if (!big) AskKind("attack", "Be aggressive. $pts points: stop passing up shots.") { l, _ -> l.pts >= pts } else null,
        )
    }

    /** "Defend. Two steals or blocks a night." */
    fun askText(lg: League): String {
        val h = lg.hero ?: return ""
        return asks(h).firstOrNull { it.id == lg.road?.coachAsk }?.text ?: ""
    }

    /** Ask what he needs: he gives you something to show him over the next five games. */
    fun askWhatHeNeeds(lg: League): String {
        fresh(lg)
        val r = lg.road ?: return ""
        val h = lg.hero ?: return ""
        if (!canTalk(lg)) return ""
        r.coachTalk = stamp(lg)
        // What the team is short of, more or less: the one he does least.
        val pool = asks(h)
        val k = pool.sortedBy { a ->
            when (a.id) {
                "defend" -> h.stats.per(h.stats.stl + h.stats.blk) * 2
                "board" -> h.stats.rpg / 2
                "care" -> 3 - h.stats.per(h.stats.tov)
                "move" -> h.stats.apg
                else -> h.stats.ppg / 5
            } + Rng.d()
        }.first()
        r.coachAsk = k.id
        r.coachAskLeft = 5
        r.coachAskHits = 0
        r.coachTrust = (r.coachTrust + 2).coerceIn(0, 100)
        return "Coach ${coach(lg)}: \"${k.text}\" Five games to show him."
    }

    /** Tell him you'll do whatever the team needs: trust, at a little cost to your pride. */
    fun buyIn(lg: League): String {
        fresh(lg)
        val r = lg.road ?: return ""
        val h = lg.hero ?: return ""
        if (!canTalk(lg)) return ""
        r.coachTalk = stamp(lg)
        // It means more from a player he doesn't trust yet.
        r.coachTrust = (r.coachTrust + if (r.coachTrust >= 70) 2 else 5).coerceIn(0, 100)
        h.happiness = (h.happiness - 2).coerceAtLeast(0)
        lg.teams.getOrNull(h.teamId)?.let { t -> for (p in lg.rosterOf(t)) if (p.id != h.id) p.happiness = (p.happiness + 2).coerceAtMost(100) }
        return "Coach ${coach(lg)}: \"That's what I like to hear.\" The locker room heard about it too."
    }

    /** After each of his games: trust from how he played, and the coach's ask. */
    fun afterGame(lg: League, g: Game, l: GameLogEntry) {
        if (!active(lg)) return
        fresh(lg)
        val r = lg.road ?: return
        val h = lg.hero ?: return
        if (l.min <= 0) return
        fun eff(pts: Double, reb: Double, ast: Double, stocks: Double, tov: Double) = pts + reb * 0.6 + ast * 0.8 + stocks - tov * 0.8
        val game = eff(l.pts.toDouble(), l.reb.toDouble(), l.ast.toDouble(), (l.stl + l.blk).toDouble(), l.tov.toDouble()) / max(l.min, 8) * 36
        val s = h.stats
        val base = if (s.gp >= 3 && s.mpg > 0) eff(s.ppg, s.rpg, s.apg, s.per(s.stl + s.blk), s.per(s.tov)) / max(s.mpg, 8.0) * 36 else game
        var d = when {
            game >= base * 1.3 && l.min >= 10 -> 2
            game <= base * 0.65 -> -2
            else -> 0
        }
        if (l.tov >= 5) d -= 1
        // What have you done for me lately: high trust fades a little unless he keeps earning it.
        if (r.coachTrust >= 70 && Rng.chance(0.3)) d -= 1
        // His ask: five games.
        if (r.coachAskLeft > 0) {
            val k = asks(h).firstOrNull { it.id == r.coachAsk }
            if (k != null && k.hit(l, h)) { r.coachAskHits++; d += 2 }
            r.coachAskLeft--
            if (r.coachAskLeft == 0) {
                if (r.coachAskHits >= 3) {
                    d += 5
                    h.happiness = (h.happiness + 3).coerceAtMost(100)
                    Alerts.add(lg, "You gave Coach ${coach(lg)} what he asked", "${r.coachAskHits} of 5 games. He noticed: more trust, and the minutes follow.", "role")
                } else {
                    d -= 3
                    Alerts.add(lg, "Coach ${coach(lg)} noticed", "${r.coachAskHits} of 5 games on what he asked for. Ask him again when you're ready.", "role")
                }
                r.coachAsk = ""
            }
        }
        r.coachTrust = (r.coachTrust + d).coerceIn(0, 100)
        @Suppress("UNUSED_VARIABLE") val unused = g.id + abs(d)
    }
}

/** All-Star Saturday, played: the 3-Point Contest and the Dunk Contest, when the hero is invited. */
object AllStarSaturday {
    /** The contests he's invited to this season: "three" and/or "dunk" (the same picks the weekend makes). */
    fun invites(lg: League): List<String> {
        val h = lg.hero ?: return emptyList()
        if (!lg.pro || !lg.playerMode || lg.road?.ended == true) return emptyList()
        val ps = lg.proState ?: return emptyList()
        if (ps.allStarSeason == lg.season || lg.phase != Phase.REGULAR) return emptyList()
        val day = ProLeague.allStarDay(lg)
        if (lg.day < day - 10 || lg.day > day) return emptyList()
        val pool = lg.teams.flatMap { t -> lg.rosterOf(t).filter { it.stats.gp > 0 && it.stats.gp * 2 >= t.gp } }
        if (pool.size < 8 || h !in pool) return emptyList()
        val out = mutableListOf<String>()
        val shooters = pool.filter { it.stats.tpa >= it.stats.gp * 3 }.sortedByDescending { it[Attr.OUT] * 2.0 + it.stats.per(it.stats.tpm) * 4 }.take(8)
        if (h in shooters) out += "three"
        val dunkers = pool.filter { it.age <= 26 && it.pos != Pos.C }.sortedByDescending { it[Attr.INS] }.take(4)
        if (h in dunkers) out += "dunk"
        return out
    }

    /** His played round, if he's played it this season (-1 if not). */
    fun threeScore(lg: League) = lg.road?.takeIf { it.contestSeason == lg.season }?.threeScore ?: -1
    fun dunkScore(lg: League) = lg.road?.takeIf { it.contestSeason == lg.season }?.dunkScore ?: -1

    private fun mark(lg: League) {
        val r = lg.road ?: return
        if (r.contestSeason != lg.season) { r.contestSeason = lg.season; r.threeScore = -1; r.dunkScore = -1 }
    }

    fun recordThree(lg: League, score: Int) { mark(lg); lg.road?.threeScore = score.coerceIn(0, 34) }
    fun recordDunk(lg: League, total: Int) { mark(lg); lg.road?.dunkScore = total.coerceIn(0, 100) }

    // ---------- the 3-Point Contest ----------

    /** Five racks of five: the last ball of each rack is a money ball (2), and the fifth rack is all money balls. Out of 34. */
    fun ballValue(i: Int) = if (i >= 20 || i % 5 == 4) 2 else 1

    /** The contest window: a little tighter than a shootaround, and it speeds up rack by rack. */
    fun window(lg: League, h: Player) = Shootaround.window(lg, h, 2) * 0.92
    fun sweepMs(i: Int) = 760 - (i / 5) * 30

    // ---------- the Dunk Contest ----------

    class Dunk(val name: String, val diff: Int, val lo: Int, val hi: Int, val blurb: String)

    val DUNKS = listOf(
        Dunk("Tomahawk", 1, 35, 41, "Cock it back and throw it down."),
        Dunk("Windmill", 2, 38, 44, "The full arm swing, all the way around."),
        Dunk("Reverse 360", 3, 41, 47, "Spin all the way around and finish it backwards."),
        Dunk("Between the legs", 4, 43, 49, "Pass it through your legs in mid-air."),
        Dunk("Off the glass", 4, 43, 49, "Toss it off the backboard and catch it at the top."),
        Dunk("From the free-throw line", 5, 45, 50, "Take off from fifteen feet. The building stands up."),
    )

    /** The green: smaller for harder dunks, bigger the more athletic he is. */
    fun dunkWindow(h: Player, d: Dunk) = (0.20 - 0.028 * d.diff + (h[Attr.INS] - 75) * 0.0018).coerceIn(0.035, 0.22)

    /** A make: closer to the middle, the higher the score; each miss before it costs the judges' patience. Null on a miss. */
    fun dunkScore(d: Dunk, pos: Double, half: Double, attempt: Int): Int? {
        val off = abs(pos - 0.5)
        if (off > half) return null
        val precision = 1.0 - off / half
        return (d.lo + (d.hi - d.lo) * precision - 3 * attempt).roundToInt().coerceIn(30, 50)
    }

    /** Three misses: the judges give something for trying. */
    fun missedScore() = Rng.range(25, 30)

    /** Five judges' cards adding up to [score]. */
    fun cards(score: Int): List<Int> {
        val base = score / 5
        val extra = score % 5
        return List(5) { i -> (base + if (i < extra) 1 else 0).coerceIn(5, 10) }
    }
}
