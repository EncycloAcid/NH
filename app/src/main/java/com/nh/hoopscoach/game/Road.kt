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
        /** An optional second position, next to his first. */
        val pos2: Pos? = null,
        /** Era mode from the start: his college years are that era's, then he turns pro into its league. */
        val era: Era = Era.TODAY,
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
        p.age = 18
        p.pos2 = s.pos2?.takeIf { it in Ratings.secondOptions(s.pos) }
        p.country = null
        p.hometown = "${s.town.trim().ifEmpty { "Hometown" }}, ${s.state}"
        p.number = s.number.coerceIn(0, 99)
        p.traits = mutableListOf()
        p.year = 1
        // The user's player is the story: a real ceiling at every level, so the league is always in reach.
        // A real ceiling at every level, but 99 has to be earned: growth slows above 90.
        p.pot = (when (s.stars) { 5 -> Rng.range(97, 99); 4 -> Rng.range(95, 98); else -> Rng.range(93, 97) }).coerceIn(p.ovr + 8, 99)
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
    fun nilFor(lg: League, t: Team, p: Player) =
        ((Ratings.expectedNil(p) * (0.75 + 0.5 * budgetShare(lg, t))).roundToInt() + heroFollowers(lg, p) / 1000) / 5 * 5

    /** The user's player's following: what he's built, or (before he signs) what his ranking brings. */
    fun heroFollowers(lg: League, p: Player): Int {
        val r = lg.road
        return when {
            r != null && p.id == lg.heroId -> r.followers
            r == null && lg.heroId < 0 && p.teamId < 0 && p.recruitRank > 0 -> startFollowers(p)
            else -> 0
        }
    }

    /** A high schooler's following: the better the ranking, the bigger. */
    fun startFollowers(p: Player) = when (p.stars) {
        5 -> 180_000 - p.recruitRank * 4_000
        4 -> 70_000 - (p.recruitRank - 40) * 500
        else -> 15_000 - (p.recruitRank - 160) * 30
    }.coerceAtLeast(5_000)

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
        lg.road = RoadState(startSeason = lg.season, followers = startFollowers(hero), seasonStartFollowers = startFollowers(hero))
        join(lg, hero, lg.team(teamId))
        Rival.create(lg)
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
        if (lg.pro) return false
        val r = lg.road ?: return false
        return lg.playerMode && !r.ended && lg.phase != Phase.OFFSEASON && r.dealSeason != lg.season && lg.playerOrNull(lg.heroId) != null
    }

    /** What the user's player is worth to his school this season. */
    fun market(lg: League): Int {
        val h = lg.playerOrNull(lg.heroId) ?: return 0
        return (nilFor(lg, lg.team(h.teamId), h) * agentCut(lg)).roundToInt().coerceAtLeast(25) / 5 * 5
    }

    /** An agent gets 15% more on everything he negotiates. */
    private fun agentCut(lg: League) = if (has(lg, Staff.AGENT)) 1.15 else 1.0

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
        // The guaranteed money arrives at signing, so there's something to spend this season.
        r.earned += guaranteed(deal, r.dealBase)
        r.paidSeason = lg.season
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
        return (((r.dealBase * 0.04 * h.stats.ppg) + (if (aa) r.dealBase * 0.25 else 0.0)) * agentCut(lg)).roundToInt() / 5 * 5
    }

    /** A national brand deal eats into practice time. */
    fun xpFactor(lg: League): Double {
        val r = lg.road ?: return 1.0
        return (if (r.deal == NilDeal.BRAND && r.dealSeason == lg.season) 0.85 else 1.0) * (if (has(lg, Staff.TRAINER)) 1.25 else 1.0)
    }

    // ---------- your own staff ----------

    /** NIL money in the bank: everything earned, minus what's been spent on staff. */
    fun bank(lg: League): Int = lg.road?.let { it.earned - it.spent } ?: 0

    fun has(lg: League, s: Staff): Boolean {
        val r = lg.road ?: return false
        return !r.ended && r.hiredSeason == lg.season && s in r.hired
    }

    fun canHire(lg: League, s: Staff) = lg.road?.ended == false && lg.phase != Phase.OFFSEASON && !has(lg, s) && bank(lg) >= s.cost

    /** Hire [s] for the rest of this season, paid out of the bank. */
    fun hire(lg: League, s: Staff): Boolean {
        val r = lg.road ?: return false
        if (!canHire(lg, s)) return false
        if (r.hiredSeason != lg.season) { r.hired.clear(); r.hiredSeason = lg.season }
        r.hired += s
        r.spent += s.cost
        lg.news += News(lg.season, lg.day, "${lg.hero?.name} hires a ${s.label.lowercase()} for the season", true)
        return true
    }

    /** Extra make chance on the user's player's jump shots, and on big-moment shots. */
    fun jumpShotBonus(lg: League) = if (has(lg, Staff.SHOOTING_COACH)) 0.03 else 0.0
    fun clutchBonus(lg: League) = if (has(lg, Staff.SHOOTING_COACH)) 0.05 else 0.0

    /** Fatigue added by a game and its practice. */
    fun fatigueGain(lg: League, plan: Practice, minutes: Int): Int {
        val nutrition = has(lg, Staff.NUTRITIONIST)
        // Pro minutes come out of 48, and pros get more recovery between games (charters, trainers, off days).
        val mins = if (lg.pro) minutes * 40 / 48 else minutes
        val work = (plan.tiring.coerceAtLeast(0) + mins / 8) * (if (nutrition) 0.75 else 1.0)
        val rest = (if (plan.tiring < 0) -plan.tiring else 0) + (if (lg.pro) 6 else 5) + (if (nutrition) 2 else 0)
        return (work - rest).roundToInt()
    }

    // ---------- the season and the offseason ----------

    /** Season over: pay the deal and write the season into the career story. */
    fun seasonEnd(lg: League) {
        val r = lg.road ?: return
        val h = lg.playerOrNull(lg.heroId) ?: return
        val t = lg.team(h.teamId)
        val signed = r.dealSeason == lg.season
        val upfront = if (signed && r.paidSeason == lg.season) guaranteed(r.deal, r.dealBase) else 0
        val paid = if (signed) guaranteed(r.deal, r.dealBase) + bonus(lg, h) else 0
        r.earned += paid - upfront
        r.hired.clear()
        val s = h.stats
        val honors = h.awards.filter { it.startsWith("${lg.season} ") }.map { it.removePrefix("${lg.season} ") }
        r.story += "${lg.season} · ${t.school} · ${classLabel(h.year)} · %.1f ppg, %.1f rpg, %.1f apg · ${Awards.resultFor(lg, t.id)}".format(s.ppg, s.rpg, s.apg) +
            (if (honors.isNotEmpty()) " · " + honors.joinToString(", ") else "") + (if (paid > 0) " · NIL ${dollars(paid)}" else "") +
            " · ${count(r.followers)} followers" +
            r.duels.filter { it.season == lg.season }.let { d -> if (d.isEmpty()) "" else " · ${d.count { it.won }}-${d.count { !it.won }} vs rival ${Rival.player(lg)?.last ?: ""}" }
        r.seasonStartFollowers = r.followers
        r.press = null
        r.fatigue = 0
        r.raceRank = 0
        r.lastRaceRank = 0
        r.raceDay = -99
    }

    /** Before Draft Night: the user's decision puts his player in the draft, or keeps him in school. */
    fun beforeDraft(lg: League, os: OffseasonState) {
        val r = lg.road ?: return
        val h = lg.playerOrNull(lg.heroId) ?: return
        os.entrants.remove(h.id)
        if (r.declared && h.year < 4) os.entrants += h.id
        if (r.declared || h.year >= 4) {
            // Mock draft: his place on the big board among the players actually in tonight's draft.
            val board = Offseason.draftBoard(lg)
            val pool = lg.players.filter { it.teamId >= 0 && (it.year >= 4 || it.id in os.entrants) }.mapNotNull { board[it.id] }
            val mine = board[h.id]
            r.draftSeason = lg.season
            r.draftRank = if (mine == null) 0 else pool.count { it < mine } + 1
        }
    }

    /** He was in tonight's draft and hasn't sat through the green room yet. */
    fun inGreenRoom(lg: League): Boolean {
        val r = lg.road ?: return false
        return r.draftSeason == lg.season && r.draftWatched != lg.season && lg.offseason?.step == OffStep.DRAFT
    }

    fun watchedDraft(lg: League) { lg.road?.draftWatched = lg.season }

    /** After Draft Night: a drafted player (or a senior) is done; an undrafted underclassman goes back to school. */
    fun afterDraft(lg: League) {
        val r = lg.road ?: return
        Rival.afterDraft(lg)
        val h = lg.playerOrNull(lg.heroId)
        val pick = lg.draftHistory.lastOrNull { it.season == lg.season && it.pid == lg.heroId }
        if (r.draftSeason == lg.season) ProLeague.planDraft(lg)
        if (h == null) {
            val me0 = lg.hero
            // Drafted, or undrafted but good enough for a two-way deal: the career goes on in the pros.
            r.goingPro = pick != null || (me0 != null && me0.ovr >= 86)
            r.undrafted = pick == null
            r.ended = !r.goingPro
            r.portal = false
            r.draftPick = pick?.pick ?: 0
            val me = lg.hero
            r.ending = when {
                pick == null && r.goingPro -> "${me?.name ?: "He"} goes undrafted, then signs a two-way deal to chase a pro roster spot."
                pick == null -> "${me?.name ?: "He"} went undrafted after four seasons and signs to play overseas."
                pick.pick <= 14 -> "Lottery pick! ${me?.name} goes No. ${pick.pick} overall in the ${lg.season} draft."
                pick.pick <= 30 -> "${me?.name} goes No. ${pick.pick} overall, a first-round pick in the ${lg.season} draft."
                else -> "${me?.name} goes No. ${pick.pick} overall in the second round of the ${lg.season} draft."
            }
            lg.news += News(lg.season, lg.day, r.ending, true)
            Rival.ending(lg, pick?.pick ?: 0).takeIf { it.isNotEmpty() }?.let { r.ending += " $it" }
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
        if (r.ended || r.goingPro || lg.pro) return
        when (os.step) {
            OffStep.ROSTER -> Offseason.advance(lg)
            OffStep.DRAFT -> { watchedDraft(lg); Offseason.advance(lg); if (os.step == OffStep.CAREER) Offseason.advance(lg) }
            OffStep.CAREER -> Offseason.advance(lg)
            else -> {
                if (r.portal) stay(lg)
                var g = 0
                while (lg.phase == Phase.OFFSEASON && g++ < 20) Offseason.advance(lg)
                // A career saved before rivals existed gets one when the new season starts.
                if (lg.phase == Phase.REGULAR) Rival.create(lg)
            }
        }
        sync(lg)
    }

    // ---------- media and followers ----------

    /** Sponsor milestones: followers needed and the one-time payout (thousands of dollars). */
    val MILESTONES = listOf(100_000 to 25, 250_000 to 60, 500_000 to 125, 1_000_000 to 250, 2_500_000 to 600, 5_000_000 to 1200,
        10_000_000 to 2500, 25_000_000 to 6000, 50_000_000 to 12000)

    fun count(n: Int) = when {
        n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0).replace(".0M", "M")
        n >= 1_000 -> "%.1fK".format(n / 1_000.0).replace(".0K", "K")
        else -> "$n"
    }

    fun personaLabel(p: Int) = when {
        p <= -4 -> "Fan favorite"
        p >= 4 -> "Lightning rod"
        else -> "Quiet pro"
    }

    fun personaBlurb(p: Int) = when {
        p <= -4 -> "Humble and well liked. Wins lift the fan base a little more."
        p >= 4 -> "Brash and box-office. Big games grow your following 20% faster, but teammates notice."
        else -> "You let your game do the talking."
    }

    /** Next sponsor milestone, or null once they're all reached. */
    fun nextMilestone(lg: League): Pair<Int, Int>? = lg.road?.let { MILESTONES.getOrNull(it.milestone) }

    /** After every game: followers move with the performance and the stage, and the press may want a word. */
    fun afterGame(lg: League, g: Game, buzzer: GameEngine.Clutch?) {
        val r = lg.road ?: return
        if (r.ended) return
        val h = lg.playerOrNull(lg.heroId) ?: return
        if (!g.involves(h.teamId)) return
        val line = h.log.lastOrNull()?.takeIf { it.gameId == g.id } ?: return
        practiceDay(lg, h, line.min)
        updateRace(lg)
        val t = lg.team(h.teamId)
        val opp = lg.team(g.opp(t.id))
        val won = g.winner() == t.id
        val rivalry = lg.rivalry(g) != null
        val duel = Rival.afterGame(lg, g, line, won)
        val stakes = BigNights.stakes(lg, g)
        val stage = when (g.type) { GameType.NCAA -> 3.0; GameType.PLAYOFF -> 2.2; GameType.CONF_TOURNEY, GameType.NIT, GameType.CUP -> 1.5; else -> 1.0 } *
            (if (stakes.isNotEmpty()) 1.6 else 1.0) *
            (if (rivalry) 1.5 else 1.0) * (if (opp.ap > 0) 1.3 else 1.0) * (if (won) 1.2 else 0.7) *
            (if (duel != null) (if (duel.won && duel.me > duel.him) 1.8 else 1.3) else 1.0)
        val brand = 0.6 + Brand.pull(lg, t) / 100.0
        val score = line.pts + 0.5 * line.reb + 0.7 * line.ast + line.stl + line.blk - 0.7 * line.tov
        var gain = r.followers * 0.0015 * damp(r.followers)
        if (line.min > 0) gain += (score - 10).coerceAtLeast(0.0).pow(1.5) * 60 * brand * stage
        if (line.pts >= 30) gain += 5_000 * brand
        val winner = buzzer != null && buzzer.name == h.name && won
        if (winner) gain += 25_000 * stage + r.followers * 0.04 * damp(r.followers)
        gain *= 1 + r.persona.coerceIn(-5, 5) * 0.04
        r.followers += gain.roundToInt()
        if (winner) lg.news += News(lg.season, lg.day, "${h.name}'s game-winner goes viral: ${count(r.followers)} followers and counting", true)
        if (won && r.persona <= -4) t.fans = (t.fans + 0.6).coerceAtMost(100.0)
        payMilestones(lg)
        r.press = pressFor(lg, g, h, line, t, opp, won, rivalry, score, winner, duel)
        if (lg.pro) BigNights.afterGame(lg, g, line, won, winner, stakes)
        Media.afterGame(lg, g, line, won, winner, stakes, duel)
        Media.raceColumn(lg)
    }

    fun payMilestonesNow(lg: League) = payMilestones(lg)

    /** Percentage growth slows as the following gets huge: half speed at 3M, a tenth at 27M. */
    fun damp(followers: Int) = 1.0 / (1.0 + followers / 3_000_000.0)

    private fun payMilestones(lg: League) {
        val r = lg.road ?: return
        while (true) {
            val (need, pay) = MILESTONES.getOrNull(r.milestone) ?: return
            if (r.followers < need) return
            r.milestone++
            r.earned += (pay * agentCut(lg)).roundToInt()
            lg.news += News(lg.season, lg.day, "${lg.hero?.name} hits ${count(need)} followers. A sponsor pays ${dollars(pay)} for one post", true)
        }
    }

    private fun pressFor(
        lg: League, g: Game, h: Player, line: GameLogEntry, t: Team, opp: Team,
        won: Boolean, rivalry: Boolean, score: Double, winner: Boolean, duel: Duel? = null,
    ): PressQuestion? {
        val reporter = Media.reporter(lg)
        fun q(topic: String, prompt: String, vararg a: PressAnswer) = PressQuestion(lg.season, g.id, topic, prompt, a.toList(), reporter)
        // Three ways to answer: humble, brash, or even.
        fun humble(text: String, fans: Int = 1, mates: Int = 3) = PressAnswer(text, 0.012, fans = fans, teammates = mates, persona = -1)
        fun brash(text: String, mates: Int = -2, fans: Int = 0) = PressAnswer(text, 0.04, fans = fans, teammates = mates, persona = 2)
        fun even(text: String) = PressAnswer(text, 0.006)
        val tourney = g.type == GameType.NCAA || g.type == GameType.CONF_TOURNEY || g.type == GameType.NIT || g.type == GameType.PLAYOFF
        val rank = if (h.stats.gp >= 15 && !lg.pro) Offseason.draftBoard(lg)[h.id] else null
        val league = if (lg.pro) "league" else "conference"
        val ppg = h.stats.ppg
        val recent = h.log.dropLast(1).takeLast(3)
        val slump = h.stats.gp >= 8 && ppg >= 10 && recent.size == 3 && recent.map { it.pts }.average() < ppg * 0.6 && line.pts < ppg * 0.7
        val careerHigh = line.pts >= 15 && h.log.size >= 5 && h.log.dropLast(1).all { it.pts < line.pts }
        val tripleDouble = listOf(line.pts, line.reb, line.ast).count { it >= 10 } >= 3
        val race = if (lg.day > 25) raceRank(lg) else 0
        val deadline = lg.pro && lg.phase == Phase.REGULAR && lg.day in (Trades.deadlineDay(lg) - 12) until Trades.deadlineDay(lg)
        val contractYear = lg.pro && (lg.road?.contract?.years ?: 0) == 1
        val rookie = lg.pro && h.proYears == 0 && h.stats.gp in 3..15
        val lowMinutes = line.min in 1..14 && h.stats.mpg >= 20
        val tired = (lg.road?.fatigue ?: 0) >= 70
        val blowout = !won && g.scoreAgainst(t.id) - g.scoreFor(t.id) >= 20
        return when {
            winner -> q(
                "Game-winner", Rng.pick(listOf("That shot is everywhere already. Walk us through it.", "Clock winding down, ball in your hands. What were you thinking?", "Did you know it was going in when it left your hand?")),
                PressAnswer("Coach drew it up and my guys trusted me.", 0.015, fans = 2, teammates = 4, persona = -1),
                brash("I want the ball in that moment. Every time."),
                even("Just a good look. It went in."),
            )
            duel != null && Rng.chance(0.85) -> {
                val rv = Rival.player(lg)?.last ?: "your rival"
                if (duel.won) q(
                    "Rival", Rng.pick(listOf("You got the better of $rv tonight, ${duel.me} to ${duel.him}. Is he the best player in your class?", "Another round to you against $rv. Does that rivalry still motivate you?")),
                    humble("He's great. He makes me better every time we play.", mates = 2),
                    brash("There's one best player in this class. It isn't him.", mates = -1),
                    even("It's not about him. We got the win."),
                ) else q(
                    "Rival", Rng.pick(listOf("$rv had the last word tonight. What happened out there?", "$rv outscored you ${duel.him} to ${duel.me}. Does that sting?")),
                    humble("He made plays. Credit to him.", mates = 0),
                    brash("Enjoy it. It won't happen again.", mates = -1),
                    even("We'll see them again. I'll be ready."),
                )
            }
            tourney && won && Rng.chance(0.6) -> q(
                "Moving on", if (g.type == GameType.PLAYOFF) ProSeason.winQuestion(lg, g, t.id)
                    else "You're moving on in the ${if (g.type == GameType.NCAA) "NCAA Tournament" else "tournament"}. Who do you want next?",
                even("Whoever's next. We'll be ready."),
                brash(if (lg.pro) "Give us the best team in the league." else "Give us the best team in the country.", mates = 0),
                humble("All credit to our staff. They had us ready."),
            )
            tripleDouble -> q(
                "Triple-double", Rng.pick(listOf("A triple-double tonight. Is that the version of you we'll see more of?", "Points, rebounds, assists, all in double figures. Where does that come from?")),
                humble("When the ball moves, everybody eats. I just made the simple play.", mates = 4),
                brash("I can do everything on a basketball court. People are starting to notice."),
                even("The numbers come when you play the right way."),
            )
            careerHigh -> q(
                "Career high", Rng.pick(listOf("Career-high ${line.pts} tonight. When did you know you had it going?", "${line.pts} points, a new career high. What clicked?")),
                humble("My teammates kept finding me. I just had to finish."),
                brash("Honestly? I expected it. This is who I am.", mates = -1),
                even("Felt good from warmups. I just kept shooting."),
            )
            race in 1..3 && won && Rng.chance(0.35) -> q(
                "Award race", if (lg.pro) "The crowd was chanting MVP tonight. Is it yours?" else "You're in the Player of the Year conversation. Do you pay attention to that?",
                humble("Individual stuff takes care of itself if we win.", fans = 2),
                brash("Look at the numbers. Who else is it going to be?", mates = -2, fans = 1),
                even("I hear it. I just try to stay locked in."),
            )
            rivalry && won && Rng.chance(0.8) -> q(
                "Rivalry", Rng.pick(listOf("You beat ${opp.school}. What does this one mean?", "That place was loud tonight. How do you rate a rivalry win?")),
                humble("This one's for our fans. They've waited all year.", fans = 3, mates = 0),
                brash("They're not on our level. Not even close.", fans = 1),
                even("It's one game. On to the next."),
            )
            line.min > 0 && score >= 20 && Rng.chance(0.6) -> q(
                "Big night", Rng.pick(listOf("${line.pts} points tonight. What was working?", "You took over in the second half. What changed?", "${line.pts}, ${line.reb} and ${line.ast}. Best you've felt all season?")),
                humble("My teammates found me in great spots.", mates = 4),
                brash("Nobody in this $league can guard me.", mates = -5),
                even("Just taking what the defense gives me."),
            )
            t.streak >= 5 && Rng.chance(0.5) -> q(
                "Win streak", "That's ${t.streak} straight. What's clicking?",
                humble("Everybody's bought in. Nobody cares who gets the credit.", mates = 3),
                brash("We're the best team in the $league right now. It's not close.", mates = 0, fans = 2),
                even("We're just stacking days. Can't get comfortable."),
            )
            t.streak <= -4 && Rng.chance(0.6) -> q(
                "Losing streak", "That's ${-t.streak} losses in a row. How do you stop the slide?",
                humble("It starts with me. I have to be better for this group.", fans = 2),
                brash("Some guys need to look in the mirror. I'm doing my part.", mates = -5),
                even("One game at a time. Win the next one."),
            )
            slump && Rng.chance(0.6) -> q(
                "Slump", Rng.pick(listOf("You've been quiet the last few games. What's off?", "The shot hasn't been falling lately. Is something bothering you?")),
                humble("I've got to be better. I'll be in the gym early tomorrow.", fans = 2, mates = 1),
                brash("Slump? Look at my numbers over a season. I'm fine.", mates = 0),
                even("Shooters shoot. It'll come back."),
            )
            deadline && Rng.chance(0.35) -> q(
                "Trade talk", "Your name keeps coming up before the deadline. Do you want to stay?",
                humble("I love it here. I want to win here.", fans = 4, mates = 2),
                brash("I want to win. If that's not here, everybody knows it.", mates = -3, fans = -3),
                even("That's business. I control what I can control."),
            )
            contractYear && Rng.chance(0.12) -> q(
                "Contract year", "It's a contract year. Does that change anything for you?",
                humble("Not at all. Win games and the rest takes care of itself."),
                brash("I'm betting on myself. Everybody's going to have to pay up.", mates = -1),
                even("I don't think about it. My agent does."),
            )
            rookie && Rng.chance(0.3) -> q(
                "Rookie year", Rng.pick(listOf("What's been the biggest adjustment to the league?", "Welcome to the league. What's surprised you most?")),
                humble("Everybody's good. The vets have taught me a lot.", mates = 4),
                brash("Honestly? It's easier than I thought.", mates = -3),
                even("The speed. Every possession matters."),
            )
            lowMinutes && Rng.chance(0.4) -> q(
                "Minutes", "Only ${line.min} minutes tonight. Are you happy with your role?",
                humble("Whatever coach needs. I'll be ready when my number's called.", mates = 2),
                brash("I should be on the floor. I think everyone knows that.", mates = -3),
                even("Some nights it goes like that."),
            )
            tired && Rng.chance(0.3) -> q(
                "The grind", "You looked worn down out there. Is the schedule catching up with you?",
                humble("No excuses. I have to take better care of my body.", fans = 1, mates = 1),
                brash("I carry a lot for this team. It adds up.", mates = -3),
                even("Long season. Rest up and go again."),
            )
            rank != null && rank <= 30 && Rng.chance(0.15) -> q(
                "Draft", "There were scouts here tonight. Are you thinking about the draft?",
                even("Not until the season's over."),
                brash("I'm ready for the league. Everybody knows it.", mates = -4),
                PressAnswer("I love it here at ${t.school}.", 0.012, fans = 2, persona = -1),
            )
            !won && line.min >= 12 && (rivalry || opp.ap > 0 || tourney || blowout || Rng.chance(0.2)) -> q(
                "Tough loss", if (blowout) "That got away from you early. What happened?" else Rng.pick(listOf("Tough one against ${opp.school}. What went wrong?", "Close game, wrong side of it. What decided it?")),
                humble("That's on me. I'll be better.", mates = 2),
                PressAnswer("Ask the refs. They decided that one.", 0.02, fans = -1, persona = 2),
                even("We'll watch the film and learn from it."),
            )
            else -> null
        }
    }

    /** The user answers the open press question. Returns the follower change. */
    fun answer(lg: League, i: Int): Int {
        val r = lg.road ?: return 0
        val q = r.press ?: return 0
        val a = q.answers.getOrNull(i) ?: return 0
        val h = lg.playerOrNull(lg.heroId)
        val delta = (r.followers * a.followers * damp(r.followers)).roundToInt()
        r.followers = (r.followers + delta).coerceAtLeast(0)
        r.persona = (r.persona + a.persona).coerceIn(-10, 10)
        if (h != null) {
            val t = lg.team(h.teamId)
            t.fans = (t.fans + a.fans).coerceIn(5.0, 100.0)
            if (a.teammates != 0) for (p in lg.rosterOf(t)) if (p.id != h.id) p.happiness = (p.happiness + a.teammates).coerceIn(0, 100)
        }
        r.pressLog += "${q.topic}: \u201c${a.text}\u201d (${if (delta >= 0) "+" else ""}${count(delta)} followers)"
        Media.afterPress(lg, q, a)
        if (r.pressLog.size > 12) r.pressLog.removeAt(0)
        r.press = null
        payMilestones(lg)
        return delta
    }

    // ---------- practice and fatigue ----------

    const val PRACTICE_STEP = 300

    fun fatigueLabel(f: Int) = when {
        f >= 75 -> "Exhausted"
        f >= 55 -> "Tired"
        f >= 30 -> "Fine"
        else -> "Fresh"
    }

    /** What fatigue does right now, in words. */
    fun fatigueEffect(f: Int): String {
        val shooting = ((f - 40).coerceAtLeast(0) * 0.15).roundToInt()
        return when {
            f >= 70 -> "Shooting −$shooting% in games, and practice gains are cut in half."
            shooting > 0 -> "Shooting −$shooting% in games."
            else -> "No effect on your game."
        }
    }

    /** The ratings a plan works on: a specialty, or the training focus for team practice. */
    fun practiceBoosts(lg: League, plan: Practice): List<Attr> {
        val h = lg.playerOrNull(lg.heroId)
        return if (plan == Practice.TEAM) h?.focus?.boosts.orEmpty() else plan.boosts
    }

    fun setPractice(lg: League, plan: Practice) { lg.road?.practice = plan }

    /** A week of practice around each game: work banked toward ratings, and fatigue from the work and the minutes. */
    private fun practiceDay(lg: League, h: Player, minutes: Int) {
        val r = lg.road ?: return
        val plan = r.practice
        if (r.practiceXp.size != 10) r.practiceXp = IntArray(10)
        val coach = Coaching.devOf(lg.coachOf(lg.team(h.teamId)))
        val gain = plan.xp * (0.8 + coach / 250.0) * (if (r.fatigue >= 70) 0.5 else 1.0) * (if (has(lg, Staff.TRAINER)) 1.25 else 1.0)
        for (a in practiceBoosts(lg, plan)) {
            val i = a.ordinal
            r.practiceXp[i] += gain.roundToInt()
            while (r.practiceXp[i] >= PRACTICE_STEP) {
                r.practiceXp[i] -= PRACTICE_STEP
                Ratings.raise(h, i)
            }
        }
        // A pro rookie still sharpens what he practices, but his overall holds at the rookie cap.
        if (lg.pro) ProLeague.rookieCap(lg, h, practiceBoosts(lg, plan).map { it.ordinal }.toSet())
        if (plan == Practice.WEIGHTS && Rng.chance(0.25)) h.weight++
        r.fatigue = (r.fatigue + fatigueGain(lg, plan, minutes)).coerceIn(0, 100)
    }

    // ---------- award races ----------

    /** Where the user's player stands in the Player of the Year race (0 if he hasn't played). */
    fun raceRank(lg: League): Int {
        val h = lg.playerOrNull(lg.heroId) ?: return 0
        if (h.teamId < 0 || h.stats.gp == 0) return 0
        val mine = Awards.score(h, lg.team(h.teamId))
        return Awards.ladder(lg).count { it.first.id != h.id && Awards.score(it.first, it.second) > mine } + 1
    }

    /** Take a new reading once a week, keeping last week's for the arrow. */
    private fun updateRace(lg: League) {
        val r = lg.road ?: return
        if (lg.day - r.raceDay < 7) return
        r.lastRaceRank = r.raceRank
        r.raceRank = raceRank(lg)
        r.raceDay = lg.day
    }

    fun allAmericaPace(rank: Int) = when {
        rank == 0 -> "Not ranked yet"
        rank == 1 -> "Player of the Year pace"
        rank <= 5 -> "First Team All-America pace"
        rank <= 10 -> "Second Team All-America pace"
        rank <= 15 -> "Third Team All-America pace"
        rank <= 30 -> "On the All-America watch list"
        else -> "Outside the national races"
    }

    /** The user's player's rank among one group (conference or freshmen), or 0. */
    fun rankAmong(lg: League, filter: (Player, Team) -> Boolean): Int {
        val h = lg.playerOrNull(lg.heroId) ?: return 0
        if (h.teamId < 0 || h.stats.gp == 0) return 0
        val t = lg.team(h.teamId)
        if (!filter(h, t)) return 0
        val mine = Awards.score(h, t)
        return Awards.ladder(lg).count { (p, pt) -> p.id != h.id && filter(p, pt) && Awards.score(p, pt) > mine } + 1
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
