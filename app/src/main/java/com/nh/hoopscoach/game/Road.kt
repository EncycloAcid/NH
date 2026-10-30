package com.nh.hoopscoach.game

import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Road to the League: the user plays one player's college career instead of coaching.
 *
 * The user's school ("your team") is run by its AI coach like every other program: the coach sets the
 * rotation, recruits and pays the roster. The user picks a school out of high school, chooses an NIL deal
 * each season and a training focus, decides each spring whether to declare for the draft or enter the
 * transfer portal, and the career ends on draft night (or after four seasons).
 */
object Road {
    /** What the user picks on the create-a-player screen. */
    class Spec(
        val first: String, val last: String, val pos: Pos, val height: Int, val weight: Int,
        val town: String, val state: String, val style: Arch, val stars: Int, val number: Int,
    )

    fun targetOvr(stars: Int) = when (stars) { 5 -> 82; 4 -> 77; else -> 71 }

    fun starLabel(stars: Int) = when (stars) { 5 -> "Five-star blue-chipper"; 4 -> "Four-star prospect"; else -> "Three-star sleeper" }

    /** A high school senior built from the user's choices. Not on any team yet. */
    fun createHero(lg: League, s: Spec): Player {
        val target = targetOvr(s.stars)
        val p = LeagueGen.makePlayer(lg, s.pos, 1, target, -1)
        p.first = s.first.trim().ifEmpty { "Jordan" }
        p.last = s.last.trim().ifEmpty { "Hayes" }
        p.height = s.height
        p.weight = s.weight
        p.style = s.style
        p.focus = s.style
        p.attrs = Ratings.build(s.pos, s.style, target)
        p.country = null
        p.hometown = "${s.town.trim().ifEmpty { "Hometown" }}, ${s.state}"
        p.number = s.number.coerceIn(0, 99)
        p.traits = mutableListOf()
        p.year = 1
        // The user's player is the story: a real ceiling at every level, so the league is always in reach.
        p.pot = (when (s.stars) { 5 -> 99; 4 -> 98; else -> 96 } - Rng.range(0, 1)).coerceIn(p.ovr + 8, 99)
        p.stars = s.stars
        p.recruitRank = when (s.stars) { 5 -> Rng.range(4, 18); 4 -> Rng.range(40, 90); else -> Rng.range(160, 320) }
        p.posRank = (p.recruitRank / 5.0).roundToInt().coerceAtLeast(1)
        p.ovrHistory.clear()
        p.ovrHistory += p.ovr
        p.happiness = 80
        return p
    }

    // ---------- offers ----------

    /** Players at [p]'s position on [t] who are already at least as good as him. */
    fun ahead(lg: League, t: Team, p: Player) = lg.rosterOf(t).count { it.id != p.id && it.pos == p.pos && it.ovr >= p.ovr }

    fun roleFor(ahead: Int) = when (ahead) {
        0 -> "Starts from day one"
        1 -> "First off the bench"
        2 -> "Fights for minutes"
        else -> "Has to earn a spot"
    }

    /** Share of the market a program pays, from its NIL budget rank: 0 for the smallest, 1 for the richest. */
    private fun budgetShare(lg: League, t: Team): Double {
        val mine = Finance.budget(lg, t)
        return lg.teams.count { Finance.budget(lg, it) < mine }.toDouble() / (lg.teams.size - 1).coerceAtLeast(1)
    }

    /** What a program offers the user's player a year in NIL, in thousands. */
    fun nilFor(lg: League, t: Team, p: Player) = (Ratings.expectedNil(p) * (0.75 + 0.5 * budgetShare(lg, t))).roundToInt() / 5 * 5

    /**
     * Schools that offer the user's player a scholarship: programs whose Recruiting Pull matches his level,
     * with room at his position, and a lean toward home. [transfer] offers are judged on his current overall.
     */
    fun offers(lg: League, p: Player, count: Int = 8, transfer: Boolean = false, exclude: Int = -1): List<SchoolOffer> {
        Brand.ensure(lg)
        val pulls = Brand.pullTable(lg)
        val want = if (transfer) (p.ovr * 1.35 - 28).coerceIn(20.0, 92.0) else when (p.stars) { 5 -> 80.0; 4 -> 62.0; else -> 44.0 }
        val scored = lg.teams.filter { it.id != exclude }.map { t ->
            val band = exp(-((pulls[t.id] - want) / 13.0).pow(2))
            val need = when (ahead(lg, t, p)) { 0 -> 1.3; 1 -> 1.1; 2 -> 0.85; else -> 0.6 }
            val loc = 1.0 + Geo.closeness(t, p) * 0.15
            t to band * need * loc * (0.75 + Rng.d() * 0.5)
        }.sortedByDescending { it.second }.take(count)
        return scored.map { (t, _) ->
            val a = ahead(lg, t, p)
            SchoolOffer(t.id, nilFor(lg, t, p), roleFor(a), a, Geo.closeness(t, p))
        }.sortedByDescending { pulls[it.teamId] }
    }

    // ---------- starting and keeping in step ----------

    /** Starts a player-mode career: [hero] signs with [teamId] and the season begins. */
    fun start(lg: League, hero: Player, teamId: Int): League {
        lg.mode = GameMode.PLAYER
        lg.addPlayer(hero)
        lg.heroId = hero.id
        lg.road = RoadState(startSeason = lg.season)
        join(lg, hero, lg.team(teamId))
        sync(lg)
        lg.reindex()
        Offseason.setupSeason(lg, carryElo = false)
        lg.news += News(lg.season, 0, "${hero.name} signs with ${lg.team(teamId).school}", true)
        return lg
    }

    /** Puts the user's player on [t]'s roster. A full roster cuts its weakest scholarship player to make room. */
    fun join(lg: League, p: Player, t: Team) {
        p.teamId = t.id
        p.walkOn = false
        val used = t.roster.map { lg.player(it).number }.toSet() + Legacy.retiredNumbers(t)
        if (p.number in used) p.number = Recruiting.freeNumber(lg, t)
        t.roster += p.id
        while (Tryouts.scholarships(lg, t) > Recruiting.MAX_ROSTER) {
            val cut = lg.rosterOf(t).filter { it.id != p.id && !it.walkOn }.minByOrNull { it.ovr + it.year } ?: break
            t.roster.remove(cut.id)
            lg.removePlayer(cut)
        }
        lg.road?.let { if (it.schools.lastOrNull() != t.id) it.schools += t.id }
        Gameplan.autoAllocate(lg, t)
    }

    /** "Your team" follows the user's player, and its head coach is whoever coaches there now. */
    fun sync(lg: League) {
        val h = lg.playerOrNull(lg.heroId)
        if (h != null && h.teamId >= 0) lg.userTeamId = h.teamId
        lg.userCoachId = lg.coachOf(lg.user).id
    }

    // ---------- NIL deals ----------

    fun needsDeal(lg: League): Boolean {
        val r = lg.road ?: return false
        return lg.playerMode && !r.ended && lg.phase != Phase.OFFSEASON && r.dealSeason != lg.season && lg.playerOrNull(lg.heroId) != null
    }

    /** What the user's player is worth to his school this season. */
    fun market(lg: League): Int {
        val h = lg.playerOrNull(lg.heroId) ?: return 0
        return nilFor(lg, lg.team(h.teamId), h).coerceAtLeast(25)
    }

    fun guaranteed(deal: NilDeal, base: Int) = when (deal) {
        NilDeal.STEADY -> base
        NilDeal.PERFORMANCE -> (base * 0.6).roundToInt() / 5 * 5
        NilDeal.BRAND -> (base * 1.5).roundToInt() / 5 * 5
        NilDeal.NONE -> 0
    }

    fun chooseDeal(lg: League, deal: NilDeal) {
        val r = lg.road ?: return
        r.deal = deal
        r.dealBase = market(lg)
        r.dealSeason = lg.season
        applyDeal(lg)
        lg.news += News(lg.season, lg.day, "${lg.hero?.name} signs a ${deal.label.lowercase()} NIL deal worth ${dollars(guaranteed(deal, r.dealBase))}", true)
    }

    /** The user's player is paid what his deal guarantees (nothing until he signs one this season). */
    fun applyDeal(lg: League) {
        val r = lg.road ?: return
        val h = lg.playerOrNull(lg.heroId) ?: return
        h.nil = if (r.dealSeason == lg.season) guaranteed(r.deal, r.dealBase) else 0
    }

    /** Performance bonus: 4% of the base for every point he averages, plus a quarter for All-America honors. */
    fun bonus(lg: League, h: Player): Int {
        val r = lg.road ?: return 0
        if (r.deal != NilDeal.PERFORMANCE || r.dealSeason != lg.season) return 0
        val aa = h.awards.any { it.startsWith("${lg.season} ") && it.contains("AA") }
        return ((r.dealBase * 0.04 * h.stats.ppg) + (if (aa) r.dealBase * 0.25 else 0.0)).roundToInt() / 5 * 5
    }

    /** A national brand deal eats into practice time. */
    fun xpFactor(lg: League): Double {
        val r = lg.road ?: return 1.0
        return if (r.deal == NilDeal.BRAND && r.dealSeason == lg.season) 0.85 else 1.0
    }

    // ---------- the season and the offseason ----------

    /** Season over: pay the deal and write the season into the career story. */
    fun seasonEnd(lg: League) {
        val r = lg.road ?: return
        val h = lg.playerOrNull(lg.heroId) ?: return
        val t = lg.team(h.teamId)
        val paid = if (r.dealSeason == lg.season) guaranteed(r.deal, r.dealBase) + bonus(lg, h) else 0
        r.earned += paid
        val s = h.stats
        val honors = h.awards.filter { it.startsWith("${lg.season} ") }.map { it.removePrefix("${lg.season} ") }
        r.story += "${lg.season} · ${t.school} · ${classLabel(h.year)} · %.1f ppg, %.1f rpg, %.1f apg · ${Awards.resultFor(lg, t.id)}".format(s.ppg, s.rpg, s.apg) +
            (if (honors.isNotEmpty()) " · " + honors.joinToString(", ") else "") + (if (paid > 0) " · NIL ${dollars(paid)}" else "")
    }

    /** Before Draft Night: the user's decision puts his player in the draft, or keeps him in school. */
    fun beforeDraft(lg: League, os: OffseasonState) {
        val r = lg.road ?: return
        val h = lg.playerOrNull(lg.heroId) ?: return
        os.entrants.remove(h.id)
        if (r.declared && h.year < 4) os.entrants += h.id
    }

    /** After Draft Night: a drafted player (or a senior) is done; an undrafted underclassman goes back to school. */
    fun afterDraft(lg: League) {
        val r = lg.road ?: return
        val h = lg.playerOrNull(lg.heroId)
        val pick = lg.draftHistory.lastOrNull { it.season == lg.season && it.pid == lg.heroId }
        if (h == null) {
            r.ended = true
            r.portal = false
            r.draftPick = pick?.pick ?: 0
            val me = lg.hero
            r.ending = when {
                pick == null -> "${me?.name ?: "He"} went undrafted after four seasons and signs to play overseas."
                pick.pick <= 14 -> "Lottery pick! ${me?.name} goes No. ${pick.pick} overall in the ${lg.season} draft."
                pick.pick <= 30 -> "${me?.name} goes No. ${pick.pick} overall, a first-round pick in the ${lg.season} draft."
                else -> "${me?.name} goes No. ${pick.pick} overall in the second round of the ${lg.season} draft."
            }
            lg.news += News(lg.season, lg.day, r.ending, true)
            return
        }
        if (r.declared) lg.news += News(lg.season, lg.day, "${h.name} went undrafted and returns to ${lg.team(h.teamId).school}", true)
        r.declared = false
        if (r.portal) {
            r.offers.clear()
            r.offers += offers(lg, h, 6, transfer = true, exclude = h.teamId)
        }
    }

    /** The user picks a school out of the transfer portal. */
    fun transfer(lg: League, teamId: Int) {
        val r = lg.road ?: return
        val h = lg.playerOrNull(lg.heroId) ?: return
        if (r.offers.none { it.teamId == teamId }) return
        val old = lg.team(h.teamId)
        old.roster.remove(h.id)
        Gameplan.validate(lg, old)
        h.transferFrom = old.id
        join(lg, h, lg.team(teamId))
        r.portal = false
        r.offers.clear()
        sync(lg)
        lg.news += News(lg.season, lg.day, "${h.name} transfers from ${old.school} to ${lg.team(teamId).school}", true)
    }

    /** Stay put after all: leave the portal without picking a school. */
    fun stay(lg: League) {
        val r = lg.road ?: return
        r.portal = false
        r.offers.clear()
    }

    /**
     * The player-mode offseason in a few taps: decision, Draft Night, the summer, then everything else
     * (the coaching carousel, recruiting, tryouts) runs on its own into the next season.
     */
    fun next(lg: League) {
        val os = lg.offseason ?: return
        val r = lg.road ?: return
        if (r.ended) return
        when (os.step) {
            OffStep.ROSTER -> Offseason.advance(lg)
            OffStep.DRAFT -> { Offseason.advance(lg); if (os.step == OffStep.CAREER) Offseason.advance(lg) }
            OffStep.CAREER -> Offseason.advance(lg)
            else -> {
                if (r.portal) stay(lg)
                var g = 0
                while (lg.phase == Phase.OFFSEASON && g++ < 20) Offseason.advance(lg)
            }
        }
        sync(lg)
    }

    /** Thousands of dollars as "$850K" or "$1.37M". */
    fun dollars(k: Int) = if (k >= 1000) "$%.2fM".format(k / 1000.0).replace(".00M", "M") else "$${k}K"

    // ---------- where he stands ----------

    /** Teammates at his position, best first, with him among them. */
    fun depth(lg: League): List<Player> {
        val h = lg.playerOrNull(lg.heroId) ?: return emptyList()
        return lg.rosterOf(lg.team(h.teamId)).filter { it.pos == h.pos }.sortedByDescending { it.ovr }
    }

    fun role(lg: League): String {
        val h = lg.playerOrNull(lg.heroId) ?: return ""
        val t = lg.team(h.teamId)
        return Gameplan.role(t, h.id)
    }
}
