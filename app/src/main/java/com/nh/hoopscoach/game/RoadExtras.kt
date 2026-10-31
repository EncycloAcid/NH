package com.nh.hoopscoach.game

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The class rival: a five-star recruit at the same position, signed by a top program in the user's
 * conference, who shadows him all career: in the award races, on the big board, head to head and on Draft Night.
 */
object Rival {
    /** Signs the rival: when the career starts, or at the next season for a career saved before rivals existed. */
    fun create(lg: League) {
        val r = lg.road ?: return
        val h = lg.playerOrNull(lg.heroId) ?: return
        if (r.rivalId >= 0 || r.ended || h.teamId < 0) return
        val home = lg.team(h.teamId)
        val conf = lg.teams.filter { it.confId == home.confId && it.id != home.id }.sortedByDescending { it.prestige }
        val t = if (conf.isNotEmpty()) Rng.pick(conf.take(3)) else lg.teams.filter { it.id != home.id }.maxBy { it.prestige }
        val target = max(h.ovr + 1, Road.targetOvr(4) + 2).coerceAtMost(if (h.year <= 1) 86 else 94)
        val p = LeagueGen.makePlayer(lg, h.pos, h.year.coerceIn(1, 4), target, t.id)
        if (p.country != null) { p.country = null; p.first = Rng.pick(Names.first); p.last = Rng.pick(Names.last); p.hometown = Rng.pick(Names.towns) }
        if (p.last == h.last) p.last = Rng.pick(Names.last.filter { it != h.last })
        p.pot = Rng.range(94, 97).coerceAtLeast(p.ovr + 8).coerceAtMost(99)
        p.stars = 5
        p.recruitRank = if (h.stars == 5) Rng.range(1, (h.recruitRank - 1).coerceIn(1, 3)) else Rng.range(1, 5)
        p.posRank = 1
        p.traits.remove(Trait.DISLOYAL)
        p.number = Recruiting.freeNumber(lg, t)
        lg.addPlayer(p)
        t.roster += p.id
        while (Tryouts.scholarships(lg, t) > Recruiting.MAX_ROSTER) {
            val cut = lg.rosterOf(t).filter { it.id != p.id && !it.walkOn }.minByOrNull { it.ovr + it.year } ?: break
            t.roster.remove(cut.id)
            lg.removePlayer(cut)
        }
        Gameplan.autoAllocate(lg, t)
        r.rivalId = p.id
        r.rivalName = p.name
        lg.news += News(
            lg.season, 0,
            if (h.year <= 1) "${p.name}, the No. ${p.recruitRank} recruit in the class, signs with ${t.school}"
            else "${p.name} of ${t.school}, the No. ${p.recruitRank} recruit in your class, is the rival everyone compares you to",
            true,
        )
    }

    /** The rival, current or former. */
    fun player(lg: League): Player? = lg.road?.rivalId?.takeIf { it >= 0 }?.let { id -> lg.anyPlayer(id) ?: lg.proState?.pendingRival?.takeIf { it.id == id } }

    /** The rival while he's still playing college ball. */
    fun active(lg: League): Player? = lg.road?.rivalId?.takeIf { it >= 0 }?.let { lg.playerOrNull(it) }?.takeIf { it.teamId >= 0 }

    /** Does this game put the user's player against his rival? */
    fun isDuel(lg: League, g: Game): Boolean {
        val h = lg.playerOrNull(lg.heroId) ?: return false
        val rv = active(lg) ?: return false
        return h.teamId >= 0 && rv.teamId != h.teamId && g.involves(h.teamId) && g.involves(rv.teamId)
    }

    /** The next scheduled game against the rival's team, if any. */
    fun nextDuel(lg: League): Game? = lg.games.filter { !it.played && isDuel(lg, it) }.minByOrNull { it.day }

    /** After a game against the rival's team: log the head-to-head if both of them played. */
    fun afterGame(lg: League, g: Game, me: GameLogEntry, won: Boolean): Duel? {
        val r = lg.road ?: return null
        if (!isDuel(lg, g)) return null
        val rv = active(lg) ?: return null
        // Only the user's team keeps game logs, so the rival's night comes from the box score.
        val box = g.box ?: return null
        val his = (box.home + box.away).firstOrNull { it.pid == rv.id }?.s ?: return null
        if (me.min <= 0 || his.sec <= 0) return null
        val d = Duel(lg.season, g.id, rv.teamId, me.pts, his.pts, won)
        r.duels += d
        val h = lg.hero ?: return d
        val t = lg.team(h.teamId)
        val o = lg.team(rv.teamId)
        lg.news += News(
            lg.season, lg.day,
            if (won) "${h.last} ${me.pts}, ${rv.last} ${his.pts}: ${t.school} takes the rival showdown with ${o.school}"
            else "${rv.last} ${his.pts}, ${h.last} ${me.pts}: ${o.school} wins the rival showdown with ${t.school}",
            true, g.id,
        )
        return d
    }

    fun record(lg: League): Pair<Int, Int> {
        val d = lg.road?.duels ?: return 0 to 0
        return d.count { it.won } to d.count { !it.won }
    }

    /** Player of the Year race rank for anyone, the way the award ladder figures it (0 before he's played). */
    fun raceRank(lg: League, p: Player): Int {
        if (p.teamId < 0 || p.stats.gp == 0) return 0
        val mine = Awards.score(p, lg.team(p.teamId))
        return Awards.ladder(lg).count { it.first.id != p.id && Awards.score(it.first, it.second) > mine } + 1
    }

    /** After Draft Night: if the rival's college career ended, write down how. */
    fun afterDraft(lg: League) {
        val r = lg.road ?: return
        if (r.rivalId < 0 || r.rivalLeft.isNotEmpty()) return
        val rv = player(lg) ?: return
        val pick = lg.draftHistory.lastOrNull { it.season == lg.season && it.pid == r.rivalId }
        when {
            pick != null -> {
                r.rivalPick = pick.pick
                r.rivalDraft = lg.season
                r.rivalLeft = "went No. ${pick.pick} in the ${lg.season} draft"
                if (lg.playerOrNull(lg.heroId) != null) lg.news += News(lg.season, lg.day, "Your rival ${rv.name} goes No. ${pick.pick} overall. You're still in school.", true)
            }
            lg.playerOrNull(rv.id) == null -> {
                r.rivalDraft = lg.season
                r.rivalLeft = "went undrafted in ${lg.season}"
            }
        }
    }

    /** One line for the career ending: how the two of them came out. */
    fun ending(lg: League, myPick: Int): String {
        val r = lg.road ?: return ""
        val rv = player(lg) ?: return ""
        val (w, l) = record(lg)
        val h2h = if (w + l > 0) " You went $w-$l head to head." else ""
        val draft = when {
            r.rivalPick > 0 && r.rivalDraft == lg.season && myPick > 0 ->
                if (myPick < r.rivalPick) " You went ${r.rivalPick - myPick} ${if (r.rivalPick - myPick == 1) "pick" else "picks"} ahead of your rival ${rv.name}."
                else " Your rival ${rv.name} went ${myPick - r.rivalPick} ${if (myPick - r.rivalPick == 1) "pick" else "picks"} ahead of you, at No. ${r.rivalPick}."
            r.rivalLeft.isNotEmpty() -> " Your rival ${rv.name} ${r.rivalLeft}."
            else -> " Your rival ${rv.name} is still in college."
        }
        return (draft + h2h).trim()
    }
}

/** Summer invitations between seasons: elite camps and the U19 national team. */
object Summer {
    /** He's young enough for U19 after his first two seasons, and good enough with a top-40 board rank or an 84 overall. */
    fun u19Eligible(lg: League, h: Player): Boolean {
        val r = lg.road ?: return false
        if (lg.season - r.startSeason > 1) return false
        val rank = Offseason.draftBoard(lg)[h.id]
        return (rank != null && rank <= 40) || h.ovr >= 84
    }

    /** Pro summers: the Olympics every fourth year, the World Cup the year before. */
    fun proTournament(season: Int): String? = when (season % 4) { 0 -> "Olympics"; 3 -> "World Cup"; else -> null }

    /** Team USA wants the league's best: an MVP-race top 20 or a 95 overall, in an Olympic or World Cup summer. */
    fun nationalEligible(lg: League, h: Player): Boolean {
        if (proTournament(lg.season) == null) return false
        val rank = Rival.raceRank(lg, h)
        return h.ovr >= 95 || rank in 1..20
    }

    /** This summer's invitations; he can take one. */
    fun invites(lg: League): List<SummerEvent> {
        val r = lg.road ?: return emptyList()
        val h = lg.playerOrNull(lg.heroId) ?: return emptyList()
        if (r.ended || h.teamId < 0 || lg.phase != Phase.OFFSEASON) return emptyList()
        val camp = when (h.pos) { Pos.PG, Pos.SG -> SummerEvent.GUARD_CAMP; Pos.SF -> SummerEvent.WING_CAMP; else -> SummerEvent.BIGS_CAMP }
        val out = mutableListOf<SummerEvent>()
        if (lg.pro) { if (nationalEligible(lg, h)) out += SummerEvent.NATIONAL_TEAM }
        else if (u19Eligible(lg, h)) out += SummerEvent.U19
        out += camp
        out += SummerEvent.SHOOTING_CAMP
        out += SummerEvent.LOCKDOWN_CAMP
        return out
    }

    /** Summer events are open after the draft, before the new season, once a summer. */
    fun open(lg: League): Boolean {
        val r = lg.road ?: return false
        if (lg.pro) return r.summer != lg.season && lg.proState?.step == ProStep.SUMMER && invites(lg).isNotEmpty()
        val os = lg.offseason ?: return false
        return r.summer != lg.season && os.step != OffStep.ROSTER && os.step != OffStep.DRAFT && invites(lg).isNotEmpty()
    }

    /** Followers a camp or the U19 team should bring, for the invitation card. */
    fun followerRange(lg: League, ev: SummerEvent): String {
        val f = lg.road?.followers ?: 0
        return if (ev == SummerEvent.NATIONAL_TEAM) "+${Road.count((150_000 + f * 0.04).roundToInt())} to +${Road.count((600_000 + f * 0.04).roundToInt())} followers"
        else if (ev == SummerEvent.U19) "+${Road.count((35_000 + f * 0.05).roundToInt())} to +${Road.count((120_000 + f * 0.05).roundToInt())} followers"
        else "+${Road.count((8_000 + f * 0.03).roundToInt())} to +${Road.count((35_000 + f * 0.03).roundToInt())} followers"
    }

    /** Goes to [ev]: ratings and followers move now. Returns what happened. */
    fun attend(lg: League, ev: SummerEvent): String {
        val r = lg.road ?: return ""
        val h = lg.playerOrNull(lg.heroId) ?: return ""
        if (!open(lg) || ev !in invites(lg)) return ""
        val before = h.ovr
        val note: String
        if (ev == SummerEvent.NATIONAL_TEAM) {
            for (i in h.attrs.indices) h.attrs[i] = (h.attrs[i] + 1).coerceAtMost(99)
            val event = proTournament(lg.season) ?: "World Cup"
            val roll = Rng.d()
            val (medal, fans) = when { roll < 0.6 -> "gold" to 600_000; roll < 0.82 -> "silver" to 300_000; roll < 0.94 -> "bronze" to 220_000; else -> "fourth place" to 150_000 }
            val ppg = ((h.ovr - 88) * 0.9 + 6 + Rng.gauss() * 2.5).coerceIn(2.0, 26.0)
            val gain = (fans + r.followers * 0.04 * Road.damp(r.followers)).roundToInt()
            r.followers += gain
            if (medal != "fourth place") h.awards += "${lg.season} $event ${medal.replaceFirstChar { it.uppercase() }}"
            note = "$event: Team USA wins $medal. You average %.1f points. Every rating +1, +${Road.count(gain)} followers.".format(ppg)
            lg.news += News(lg.season, lg.day, "${h.name} and Team USA take $medal at the $event", true)
        } else if (ev == SummerEvent.U19) {
            for (i in h.attrs.indices) h.attrs[i] = (h.attrs[i] + 1).coerceAtMost(99)
            val roll = Rng.d()
            val (medal, fans) = when {
                roll < 0.45 -> "gold" to 120_000
                roll < 0.70 -> "silver" to 80_000
                roll < 0.85 -> "bronze" to 60_000
                else -> "fifth place" to 35_000
            }
            val ppg = ((h.ovr - 72) * 0.55 + 7 + Rng.gauss() * 2).coerceIn(3.0, 24.0)
            val gain = (fans + r.followers * 0.05 * Road.damp(r.followers)).roundToInt()
            r.followers += gain
            if (medal != "fifth place") h.awards += "${lg.season} U19 World Cup ${medal.replaceFirstChar { it.uppercase() }}"
            val rv = Rival.active(lg)
            val withRival = rv != null && (Offseason.draftBoard(lg)[rv.id] ?: 99) <= 40
            note = "U19 World Cup: Team USA wins $medal. You average %.1f points".format(ppg) +
                (if (withRival) ", sharing the floor with your rival ${rv!!.last}" else "") +
                ". Every rating +1, +${Road.count(gain)} followers."
            lg.news += News(lg.season, lg.day, "${h.name} and Team USA take $medal at the U19 World Cup", true)
        } else {
            for (a in ev.boosts) h.attrs[a.ordinal] = (h.attrs[a.ordinal] + 2).coerceAtMost(99)
            val mvp = Rng.chance(((h.ovr - 70) / 40.0).coerceIn(0.1, 0.6))
            val gain = (Rng.range(8_000, 20_000) + r.followers * 0.03 * Road.damp(r.followers) + if (mvp) 15_000 else 0).roundToInt()
            r.followers += gain
            note = "${ev.label}: ${ev.boosts.joinToString(", ") { "${it.label} +2" }}" +
                (if (mvp) ". Named camp MVP" else "") + ". +${Road.count(gain)} followers."
        }
        if (h.ovr > h.pot) h.pot = h.ovr
        r.summer = lg.season
        r.summerNote = note + if (h.ovr > before) " Overall $before → ${h.ovr}." else ""
        val tag = when (ev) { SummerEvent.U19 -> "U19 Team USA"; SummerEvent.NATIONAL_TEAM -> "Team USA, ${proTournament(lg.season)}"; else -> ev.label }
        val story = if (lg.pro) r.proStory else r.story
        if (story.isNotEmpty()) story[story.size - 1] = story.last() + " · Summer: " + tag
        Road.payMilestonesNow(lg)
        if (lg.pro) AllTime.check(lg)
        return r.summerNote
    }
}
