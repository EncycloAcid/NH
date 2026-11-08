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
