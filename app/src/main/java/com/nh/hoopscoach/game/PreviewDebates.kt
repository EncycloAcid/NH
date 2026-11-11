package com.nh.hoopscoach.game

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The preseason magazine's TV page: the season's biggest questions, argued before a game is played.
 * Same rules as the weekly show (see [TvShow]): one pundit argues what the projections say, the other
 * argues the other side, the YES/NO tags follow the arguments, and the fans vote. The questions come
 * from the issue's own numbers and from the user's story: the favorite, the champs, his MVP case, the
 * hometown kid, a final season, a comeback, and his team's playoff hopes.
 */
object PreviewDebates {
    private class Q(val kind: String, val question: String, val lean: Double, val interest: Double, val yes: List<String>, val no: List<String>, val yesTalk: List<String>, val noTalk: List<String>, val pid: Int = -1)

    private fun pct(v: Double) = "${(v * 100).roundToInt().coerceIn(0, 100)}%"

    fun build(lg: League, pv: NbaPreview): List<TvSegment> {
        val ranked = NbaPreviews.ranked(lg, pv)
        if (ranked.size < 3) return emptyList()
        fun pr(t: Team) = pv.proj.getValue(t.id)
        val qs = mutableListOf<Q>()
        val top = ranked[0]
        val second = ranked[1]
        // The favorite.
        qs += Q("favorite", "Are the ${top.name} the team to beat?", ((pr(top).title - 0.2) * 4).coerceIn(-0.9, 0.8), 6.0,
            listOf("They win the title in ${pct(pr(top).title)} of our sims and reach the Finals in ${pct(pr(top).finals)}.", "Projected ${pr(top).w}-${pr(top).l}, the best record in the league."),
            listOf("That still leaves a ${pct(1 - pr(top).title)} chance somebody else wins it.", "The ${second.name} win it in ${pct(pr(second).title)} of our sims."),
            listOf("Best roster, best odds. Everyone else is chasing.", "Look at who they put on the floor."),
            listOf("Nobody wins a title in October.", "Favorites lose every June."))
        // The champs.
        val (champ, _) = NbaHistory.finalists(lg, lg.season - 1)
        if (champ != null) qs += Q("repeat", "Can the ${champ.name} repeat?", ((pr(champ).title - 0.18) * 4).coerceIn(-0.85, 0.75), 6.5,
            listOf("We give them ${pct(pr(champ).title)} to win it again.", "Projected ${pr(champ).w}-${pr(champ).l}."),
            listOf("Only a ${pct(pr(champ).title)} chance in our sims.", "Repeats are rare: everybody's best night is against you."),
            listOf("They know how to win in June. That doesn't go away.", "Champions don't give it back easily."),
            listOf("The hunted get tired.", "Running it back is harder than winning it once."))
        val hero = lg.hero?.takeIf { lg.playerMode && it.teamId >= 0 }
        val r = lg.road
        if (hero != null && r != null) {
            val ht = lg.team(hero.teamId)
            val ho = pr(ht)
            val mvpAt = pv.mvp.indexOfFirst { it.pid == hero.id }
            val royAt = pv.roy.indexOfFirst { it.pid == hero.id }
            if (mvpAt in 0..7) {
                val p = pv.mvp[mvpAt].p
                val lead = pv.mvp.firstOrNull()
                qs += Q("mvp", "Can ${hero.name} win MVP?", ((p - 0.14) * 4).coerceIn(-0.85, 0.75), 8.0,
                    listOfNotNull("He's No. ${mvpAt + 1} on our MVP board at ${pct(p)}.", "The ${ht.name} project at ${ho.w}-${ho.l}, and MVPs come from winners."),
                    listOfNotNull(lead?.takeIf { it.pid != hero.id }?.let { "${lg.playerOrNull(it.pid)?.name ?: "The favorite"} leads the board at ${pct(it.p)}." }, "${pct(1 - p)} of our sims, somebody else wins it."),
                    listOf("Watch him for a week and tell me who's better.", "It's his league now."),
                    listOf("Name recognition wins this award, and he's not there yet.", "Ask me again at the All-Star break."), hero.id)
            } else if (royAt in 0..4 && hero.proYears == 0) {
                val p = pv.roy[royAt].p
                qs += Q("roy", "Is ${hero.name} the Rookie of the Year?", ((p - 0.25) * 3).coerceIn(-0.85, 0.75), 7.0,
                    listOf("He's No. ${royAt + 1} in our rookie odds at ${pct(p)}.", "He walks into a real role with the ${ht.name}."),
                    listOf("${pct(1 - p)} of our sims, another rookie wins it.", "Rookies hit a wall in February."),
                    listOf("He's ready. You can see it.", "The minutes are there."),
                    listOf("Every year a rookie nobody talked about wins this.", "Let him play a game first."), hero.id)
            }
            if (Hometown.active(lg)) {
                val town = Hometown.city(lg)
                qs += Q("hometown", "Can ${hero.name} bring a title home to $town?", ((ho.title - 0.12) * 4 + (ho.playoffs - 0.5) * 0.6).coerceIn(-0.85, 0.7), 9.0,
                    listOf("The ${ht.name} win the title in ${pct(ho.title)} of our sims.", "They make the playoffs ${pct(ho.playoffs)} of the time."),
                    listOf("A title in only ${pct(ho.title)} of our sims.", "Projected ${ho.w}-${ho.l}. That's not a champion's record."),
                    listOf("The kid from $town, in front of $town. That's how titles happen.", "Nobody plays harder for a city than the one who grew up in it."),
                    listOf("A great story doesn't win a playoff series.", "Every bad night, the whole city hears about it."), hero.id)
            }
            if (r.farewellSeason == lg.season) {
                qs += Q("lastride", "Does ${hero.name} go out with a ring?", ((ho.title - 0.12) * 4).coerceIn(-0.85, 0.75), 9.5,
                    listOf("The ${ht.name} win it all in ${pct(ho.title)} of our sims.", "Reach the conference finals ${pct(ho.confFinals)}."),
                    listOf("They win it all in only ${pct(ho.title)} of our sims.", "Projected ${ho.w}-${ho.l}."),
                    listOf("Last rides bring out something extra.", "His teammates will play for him."),
                    listOf("Sentiment doesn't guard anybody.", "Most farewell tours end in the second round."), hero.id)
            }
            if (r.comebackSeason == lg.season || r.comebackSeason == lg.season - 1 && hero.proYears > 0) {
                val rank = lg.rosterOf(ht).sortedByDescending { it.ovr }.indexOf(hero) + 1
                qs += Q("comeback", "Was coming back the right call for ${hero.name}?", ((ho.playoffs - 0.45) * 1.6 + (if (rank <= 3) 0.25 else -0.2)).coerceIn(-0.8, 0.75), 9.0,
                    listOf("He's ${if (rank <= 3) "still one of the ${NbaPreviews.possessive(ht.name)} top three" else "back in the rotation"}.", "The ${ht.name} make the playoffs ${pct(ho.playoffs)} of the time."),
                    listOf("At ${hero.age}, the legs don't come back.", "Projected ${ho.w}-${ho.l}."),
                    listOf("You don't tell a competitor when he's done.", "He had more in the tank. You'll see."),
                    listOf("Some goodbyes should stay goodbyes.", "Nobody wants to watch a legend fade."), hero.id)
            }
        }
        // His team (or a coach's): the playoffs.
        val u = lg.user
        val uo = pr(u)
        if (uo.playoffs in 0.2..0.85) qs += Q("playoffs", "Are the ${u.name} a playoff team?", ((uo.playoffs - 0.5) * 2.2).coerceIn(-0.85, 0.85), 6.5,
            listOf("They make the playoffs in ${pct(uo.playoffs)} of our sims.", "Projected ${uo.w}-${uo.l}, around the ${Previews.ordinal(uo.seed.roundToInt())} seed."),
            listOf("They miss in ${pct(1 - uo.playoffs)} of our sims.", "Projected range: ${uo.lo} to ${uo.hi} wins."),
            listOf("They've got the top-end talent. That matters in April.", "Healthy, they're in."),
            listOf("Too many teams in the middle, and they're one of them.", "One injury and it's the lottery."))
        // Pick the three biggest, favoring the user's own stories; nothing the numbers have settled.
        val ranked3 = qs.filter { abs(it.lean) <= 0.85 }.sortedByDescending { it.interest * (1 + abs(it.lean)) }
        // One title question about him is enough: a last ride beats the hometown ring question.
        val picked = ranked3.filterNot { it.kind == "hometown" && ranked3.any { o -> o.kind == "lastride" } }.take(3)
        val show = TvShow.showName(lg)
        return picked.map { q -> segment(lg, q, show) }
    }

    fun buildCollege(lg: League, pv: Preview): List<TvSegment> {
        val ranked = pv.proj.entries.sortedBy { it.value.nat }.map { lg.team(it.key) }
        if (ranked.size < 4) return emptyList()
        fun pr(t: Team) = pv.proj.getValue(t.id)
        fun od(t: Team) = pv.odds[t.id] ?: Odds(0.0, 0.0, 0.0, 0.0, 0.0, 0, 0)
        val qs = mutableListOf<Q>()
        val top = ranked[0]
        val second = ranked[1]
        val topOdds = od(top)
        val secOdds = od(second)
        // 1. The favorite.
        qs += Q(
            "favorite",
            "Can ${top.school} cut down the nets in April?",
            ((topOdds.title - 0.12) * 4).coerceIn(-0.85, 0.85),
            7.0,
            listOf(
                "They win the national title in ${pct(topOdds.title)} of our season sims and reach the Final Four in ${pct(topOdds.f4)}.",
                "Projected ${pr(top).w}-${pr(top).l} with the No. 1 overall ranking.",
            ),
            listOf(
                "Single-elimination March means ${pct(1 - topOdds.title)} of the field is picking someone else.",
                "No. 2 ${second.school} has a ${pct(secOdds.title)} chance in our simulations.",
            ),
            listOf(
                "Best roster in America, top to bottom. They have all the pieces.",
                "Championship pedigree wins in March.",
            ),
            listOf(
                "Nobody breezes through six tournament rounds unscathed.",
                "One cold shooting night in March and the season is over.",
            ),
        )

        // 2. Defending champion (if any in history).
        val champ = CollegeHistory.lastChampion(lg)
        if (champ != null) {
            val co = od(champ)
            val cp = pr(champ)
            qs += Q(
                "repeat",
                "Can ${champ.school} repeat as national champions?",
                ((co.title - 0.10) * 4).coerceIn(-0.85, 0.75),
                6.5,
                listOf(
                    "Our model gives them a ${pct(co.title)} chance to run it back.",
                    "Projected ${cp.w}-${cp.l} with a Final Four probability of ${pct(co.f4)}.",
                ),
                listOf(
                    "Sims give them only ${pct(co.title)} to repeat.",
                    "Repeating in modern college basketball with roster turnover is nearly impossible.",
                ),
                listOf(
                    "They have the championship DNA and culture. Don't count them out.",
                    "Winning when everyone has you circled is what true programs do.",
                ),
                listOf(
                    "Target on their backs every night in hostile gymnasiums.",
                    "The portal reshuffles the deck every spring; nobody repeats easily.",
                ),
            )
        }

        // 3. Hero or User's team.
        val hero = lg.hero?.takeIf { lg.playerMode && it.teamId >= 0 }
        if (hero != null) {
            val ht = lg.team(hero.teamId)
            val ho = od(ht)
            val poyAt = pv.poy.indexOfFirst { it.pid == hero.id }
            if (poyAt in 0..5) {
                val p = pv.poy[poyAt].p
                qs += Q(
                    "poy",
                    "Is ${hero.name} the National Player of the Year?",
                    ((p - 0.15) * 4).coerceIn(-0.85, 0.8),
                    8.5,
                    listOf(
                        "He sits at No. ${poyAt + 1} on our National Player of the Year board (${pct(p)}).",
                        "${ht.school} projects as a tournament lock at ${pr(ht).w}-${pr(ht).l}.",
                    ),
                    listOf(
                        "${pct(1 - p)} of our simulations see another star take the trophy.",
                        "POY voters demand tournament No. 1 seeds.",
                    ),
                    listOf(
                        "Must-watch basketball every time he touches the rock.",
                        "He's the most electric player in the country.",
                    ),
                    listOf(
                        "Too early to crown him before conference play starts.",
                        "Wait until he faces road defenses loaded up to stop him.",
                    ),
                    hero.id,
                )
            } else {
                qs += Q(
                    "hero_tourney",
                    "Can ${hero.name} lead ${ht.school} on a deep March run?",
                    ((ho.s16 - 0.3) * 2.5).coerceIn(-0.85, 0.8),
                    7.5,
                    listOf(
                        "${ht.school} has a ${pct(ho.ncaa)} chance at the Big Dance and ${pct(ho.s16)} to reach the Sweet 16.",
                        "With ${hero.name} leading the way, their ceiling is unmatched.",
                    ),
                    listOf(
                        "Sims project only ${pct(ho.f4)} to make the Final Four.",
                        "One star alone cannot carry a squad through March.",
                    ),
                    listOf(
                        "He's the kind of alpha who can take over a 40-minute tournament game.",
                        "Tournament legends are born in March, and he has that look.",
                    ),
                    listOf(
                        "Tournament defenses will double him and force teammates to beat them.",
                        "March requires a full eight-man rotation, not just one guy.",
                    ),
                    hero.id,
                )
            }
        } else {
            // User coach team
            val ut = lg.user
            val uo = od(ut)
            val up = pr(ut)
            if (uo.ncaa in 0.2..0.85) {
                qs += Q(
                    "dance",
                    "Will ${ut.school} hear their name called on Selection Sunday?",
                    ((uo.ncaa - 0.5) * 2.5).coerceIn(-0.85, 0.85),
                    6.5,
                    listOf(
                        "Simulations punch their ticket in ${pct(uo.ncaa)} of runs.",
                        "Projected ${up.w}-${up.l} (${up.cw}-${up.cl} in conference).",
                    ),
                    listOf(
                        "Miss the tournament in ${pct(1 - uo.ncaa)} of simulations.",
                        "A brutal bubble battle awaits them in February.",
                    ),
                    listOf(
                        "The resume will be strong enough; quality wins will get them in.",
                        "They have the veteran poise needed to win key games down the stretch.",
                    ),
                    listOf(
                        "One bad road loss in conference play drops them straight to the NIT.",
                        "The bubble is unforgiving when committee members debate Quadrant 1 records.",
                    ),
                )
            } else if (uo.f4 >= 0.12) {
                qs += Q(
                    "finalfour",
                    "Are the ${ut.school} a legitimate Final Four contender?",
                    ((uo.f4 - 0.22) * 3.0).coerceIn(-0.85, 0.85),
                    6.5,
                    listOf(
                        "Reach the Final Four in ${pct(uo.f4)} of our season simulations.",
                        "Ranked No. ${up.nat} nationally with a projected ${up.w}-${up.l} record.",
                    ),
                    listOf(
                        "${pct(1 - uo.f4)} of our tournament simulations fall short of Glendale.",
                        "The bracket draw determines everything.",
                    ),
                    listOf(
                        "They have the guard play and rim protection needed for April.",
                        "Built specifically for tournament basketball.",
                    ),
                    listOf(
                        "Too many landmines in the regional rounds.",
                        "Tough matchups against athletic mid-majors could trip them up early.",
                    ),
                )
            }
        }

        // 4. Cinderella
        if (pv.cinderella >= 0 && pv.cinderella != top.id) {
            val cin = lg.team(pv.cinderella)
            val co = od(cin)
            val cp = pr(cin)
            qs += Q(
                "cinderella",
                "Is ${cin.school} this year's Cinderella story?",
                ((co.s16 - 0.18) * 3.5).coerceIn(-0.85, 0.8),
                6.0,
                listOf(
                    "Projected at ${cp.w}-${cp.l} with a ${pct(co.s16)} chance to reach the Sweet 16.",
                    "Dominant mid-major profile with top-tier efficiency metrics.",
                ),
                listOf(
                    "Underdogs only reach the second weekend ${pct(co.s16)} of the time.",
                    "Facing a high-major 2 or 3 seed in the Round of 32 is a monumental hill.",
                ),
                listOf(
                    "They have the fearless veteran guards who shock power conference teams in March.",
                    "Every bracket needs a darling, and this is the one.",
                ),
                listOf(
                    "Size and athletic disparity always show up by the second round.",
                    "Fun early story, but power conference depth wears them down.",
                ),
            )
        }

        val ranked3 = qs.filter { abs(it.lean) <= 0.85 }.sortedByDescending { it.interest * (1 + abs(it.lean)) }
        val picked = ranked3.take(3)
        val show = TvShow.showName(lg)
        return picked.map { q -> segment(lg, q, show) }
    }

    private fun segment(lg: League, q: Q, show: String): TvSegment {
        val numbersYes = q.lean >= 0
        val a = Media.pundit(lg)
        var b = Media.pundit(lg)
        var guard = 0
        while (b.handle == a.handle && guard++ < 6) b = Media.pundit(lg)
        // The numbers side cites the projections; the hot take argues the other side with talk only (the
        // numbers don't help it, so it never quotes one against itself).
        val dataLines = if (numbersYes) q.yes else q.no
        val talk = if (numbersYes) q.noTalk else q.yesTalk
        val numbers = TvTake(a.name, a.handle, a.avatar, a.verified, if (numbersYes) "Yes" else "No", dataLines.take(2), "The numbers")
        val hot = TvTake(b.name, b.handle, b.avatar, b.verified, if (numbersYes) "No" else "Yes", talk.take(2), "Hot take")
        val takes = if (numbersYes) listOf(numbers, hot) else listOf(hot, numbers)
        val poll = (50 + q.lean * 30).roundToInt().coerceIn(15, 85)
        return TvSegment(lg.season, 0, show, q.question, takes, emptyList(), kind = q.kind, pid = q.pid, poll = poll, numbersYes = numbersYes)
    }
}
