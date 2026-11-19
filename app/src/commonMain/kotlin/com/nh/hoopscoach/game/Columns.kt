package com.nh.hoopscoach.game

import kotlin.math.roundToInt

/**
 * The columns that make a season worth following: a notebook every couple of weeks (who's hot, who's
 * cold, who's ahead of or behind our preseason projection, the scoring race, a stat leader, the rookie
 * or freshman to watch, the unbeatens and the poll's risers in college), and a season in review when it
 * ends (the champion, the awards, the best record, the biggest surprise and disappointment against our
 * preview, and how our favorite did). Every column checks the preview against what really happened.
 */
object Columns {
    private val BYLINES = listOf("Dana Marsh · Hoops Wire", "Theo Banks · The Daily Dribble", "Rae Whitfield · Courtside Report", "Marcus Lyle · Baseline Sports",
        "Jules Ortega · The Rim", "Priya Shah · Fast Break Network", "Cal Benton · Above the Break")
    private fun byline(lg: League, salt: Int) = BYLINES[kotlin.random.Random(lg.season * 31L + salt).nextInt(BYLINES.size)]
    private fun <T> pick(lg: League, salt: Int, vararg o: T): T = o[kotlin.random.Random(lg.season * 1_000_003L + lg.day * 7919L + salt).nextInt(o.size)]
    private fun f1(d: Double) = "%.1f".format(d)
    private fun nick(lg: League, t: Team) = if (lg.pro) t.name.substringAfterLast(' ') else t.school
    private fun the(lg: League, t: Team) = if (lg.pro) "the ${t.name}" else t.school
    private fun The(lg: League, t: Team) = the(lg, t).replaceFirstChar { it.uppercase() }
    private fun ord(n: Int) = Previews.ordinal(n)

    /** A team's record over its last [n] games (regular season and tournaments; not exhibitions). */
    fun lastN(lg: League, t: Team, n: Int): Pair<Int, Int> {
        val gs = lg.games.filter { it.played && it.involves(t.id) && it.type != GameType.CUP }.sortedBy { it.day }.takeLast(n)
        val w = gs.count { it.winner() == t.id }
        return w to gs.size - w
    }

    private fun qualified(lg: League): List<Player> {
        val most = lg.teams.maxOfOrNull { it.w + it.l } ?: 0
        return lg.players.filter { it.teamId >= 0 && it.stats.gp >= maxOf(3, (most * 0.6).roundToInt()) }
    }

    // ---------- the notebook ----------

    fun notebook(lg: League) {
        if (lg.phase != Phase.REGULAR) return
        if (lg.pro) { if (lg.day < 20 || lg.day % 14 != 10) return } else { if (lg.day < 8 || lg.day % 7 != 4) return }
        if (lg.news.any { it.kind == "column" && it.season == lg.season && it.day == lg.day && it.text.startsWith("Notebook") }) return
        if (lg.pro) proNotebook(lg) else collegeNotebook(lg)
    }

    private fun proNotebook(lg: League) {
        val ps = lg.proState ?: return
        val games = ProLeague.seasonGames(lg).coerceAtLeast(1)
        val played = lg.teams.filter { it.w + it.l >= 8 }
        if (played.size < lg.teams.size / 2) return
        val items = mutableListOf<String>()
        val l10 = played.associateWith { lastN(lg, it, 10) }
        val hot = played.maxByOrNull { l10.getValue(it).first * 10 + it.streak }!!
        val cold = played.minByOrNull { l10.getValue(it).first * 10 + it.streak }!!
        val (hw, hl) = l10.getValue(hot)
        val (cw, cl) = l10.getValue(cold)
        items += "HOT: ${The(lg, hot)} are $hw-$hl in their last ${hw + hl}" + (if (hot.streak >= 4) ", winners of ${hot.streak} straight" else "") + ", and ${hot.record} overall. " +
            (lg.rosterOf(hot).filter { it.stats.gp > 0 }.maxByOrNull { it.stats.ppg }?.let { "${it.name} leads them at ${f1(it.stats.ppg)} a night." } ?: "")
        if (cold.id != hot.id && cw <= 4) items += "COLD: ${The(lg, cold)} are $cw-$cl in their last ${cw + cl}" + (if (cold.streak <= -4) ", losers of ${-cold.streak} in a row" else "") + ". " +
            pick(lg, 1, "Something has to give.", "The phones will be ringing before the deadline.", "It's getting late early.")
        // Against the preseason projection: pace now against what we said in the fall.
        ps.preview?.takeIf { it.season == lg.season }?.let { pv ->
            val pace = played.associateWith { (it.w.toDouble() / (it.w + it.l) * games).roundToInt() }
            val diff = played.mapNotNull { t -> pv.proj[t.id]?.let { t to pace.getValue(t) - it.w } }
            diff.maxByOrNull { it.second }?.takeIf { it.second >= 7 }?.let { (t, d) ->
                items += "AHEAD OF SCHEDULE: We projected ${the(lg, t)} for ${pv.proj.getValue(t.id).w} wins. They're on pace for ${pace.getValue(t)}, $d better. " +
                    pick(lg, 2, "Our model is taking notes.", "Nobody saw this coming, us included.", "Whatever they're doing, it's working.")
            }
            diff.minByOrNull { it.second }?.takeIf { it.second <= -7 }?.let { (t, d) ->
                items += "BEHIND SCHEDULE: We had ${the(lg, t)} at ${pv.proj.getValue(t.id).w} wins. The pace says ${pace.getValue(t)}, ${-d} short. " +
                    pick(lg, 3, "There's time, but not much.", "The preseason optimism is gone.", "Somebody's job is getting warm.")
            }
        }
        // The scoring race.
        val q = qualified(lg)
        q.sortedByDescending { it.stats.ppg }.take(3).takeIf { it.size == 3 }?.let { s ->
            items += "THE SCORING RACE: ${s.joinToString(", ") { "${it.name} ${f1(it.stats.ppg)}" }}. " +
                if (s[0].stats.ppg - s[1].stats.ppg < 1) "Less than a point separates the top two." else "${s[0].last} has some room."
        }
        // A stat leader that changes from column to column.
        statLeader(lg, q, (lg.day / 14) % 4)?.let { items += it }
        // The rookies.
        q.filter { it.proYears == 0 }.maxByOrNull { it.stats.ppg }?.let { r ->
            items += "ROOKIE WATCH: ${r.name} (${nick(lg, lg.team(r.teamId))}) is averaging ${f1(r.stats.ppg)} points, ${f1(r.stats.rpg)} rebounds and ${f1(r.stats.apg)} assists. " +
                (NbaPreviews.draftPick(lg, r)?.let { if (it > 10) "Not bad for the No. $it pick." else "The No. $it pick is living up to it." } ?: "")
        }
        val head = pick(lg, 4,
            "Notebook: the ${nick(lg, hot)} roll, the ${nick(lg, cold)} reel",
            "Notebook: nobody's hotter than the ${nick(lg, hot)}",
            "Notebook: what we've learned, two weeks at a time",
            "Notebook: ${hot.record} and climbing in ${hot.school}",
        )
        lg.news += News(lg.season, lg.day, head, hot.id == lg.userTeamId || cold.id == lg.userTeamId, -1, "column", items.joinToString("\n\n"), byline(lg, lg.day), hot.id)
    }

    private fun statLeader(lg: League, q: List<Player>, which: Int): String? {
        fun per(p: Player, v: Int) = v.toDouble() / p.stats.gp.coerceAtLeast(1)
        return when (which) {
            0 -> q.maxByOrNull { it.stats.apg }?.let { "THE PASSER: ${it.name} leads the ${if (lg.pro) "league" else "country"} with ${f1(it.stats.apg)} assists a game." }
            1 -> q.maxByOrNull { it.stats.rpg }?.let { "ON THE GLASS: ${it.name} is pulling down ${f1(it.stats.rpg)} rebounds a game, more than anyone." }
            2 -> q.maxByOrNull { per(it, it.stats.blk) }?.let { "THE WALL: ${it.name} blocks ${f1(per(it, it.stats.blk))} shots a game." }
            else -> q.filter { it.stats.tpa >= it.stats.gp * 3 }.maxByOrNull { it.stats.tpm.toDouble() / it.stats.tpa.coerceAtLeast(1) }?.let {
                "FROM DEEP: ${it.name} is shooting ${(it.stats.tpm * 100.0 / it.stats.tpa).roundToInt()}% from three on ${f1(per(it, it.stats.tpa))} attempts a game."
            }
        }
    }

    private fun collegeNotebook(lg: League) {
        val items = mutableListOf<String>()
        val pv = lg.preview?.takeIf { it.season == lg.season }
        val unbeaten = lg.teams.filter { it.l == 0 && it.w >= 5 }.sortedByDescending { it.w }
        items += when (unbeaten.size) {
            0 -> "NOBODY'S PERFECT: every team in the country has a loss now."
            1 -> "THE LAST UNBEATEN: ${unbeaten[0].school} (${unbeaten[0].record}) is the only team without a loss."
            else -> "STILL PERFECT: ${unbeaten.size} teams are unbeaten: ${unbeaten.take(5).joinToString(", ") { "${it.school} (${it.record})" }}${if (unbeaten.size > 5) " and more" else ""}."
        }
        lg.teams.filter { it.streak >= 5 && it.l > 0 }.maxByOrNull { it.streak * 100 + it.prestige }?.let { t ->
            items += "ROLLING: ${t.school} has won ${t.streak} straight and is ${t.record}" + (if (t.ap > 0) ", No. ${t.ap} in the poll." else ", and the poll hasn't caught up yet.")
        }
        if (pv != null) {
            lg.teams.filter { it.ap in 1..25 }.maxByOrNull { (pv.proj[it.id]?.nat ?: 0) - it.ap }?.let { t ->
                val was = pv.proj[t.id]?.nat ?: 0
                if (was - t.ap >= 15) items += "CRASHING THE PARTY: We picked ${t.school} No. $was in our preview. It's No. ${t.ap} in the poll at ${t.record}."
            }
            lg.teams.filter { (pv.proj[it.id]?.nat ?: 99) <= 10 && it.ap == 0 }.minByOrNull { pv.proj.getValue(it.id).nat }?.let { t ->
                items += "WHAT HAPPENED? Our No. ${pv.proj.getValue(t.id).nat}, ${t.school}, is ${t.record} and out of the poll."
            }
        }
        Awards.ladder(lg).take(3).takeIf { it.size == 3 }?.let { top ->
            items += "PLAYER OF THE YEAR: ${top.joinToString("; ") { (p, t) -> "${p.name}, ${t.school} (${f1(p.stats.ppg)} ppg)" }}. " +
                pick(lg, 5, "${top[0].first.last} is the name to beat.", "It's tighter than it looks.", "Conference play will settle it.")
        }
        qualified(lg).filter { it.year == 1 }.maxByOrNull { it.stats.ppg }?.let { fr ->
            items += "FRESHMAN WATCH: ${fr.name} (${lg.team(fr.teamId).school}) is averaging ${f1(fr.stats.ppg)} points and ${f1(fr.stats.rpg)} rebounds."
        }
        statLeader(lg, qualified(lg), (lg.day / 7) % 4)?.let { items += it }
        val lead = unbeaten.firstOrNull() ?: lg.teams.filter { it.ap == 1 }.firstOrNull()
        val head = when {
            unbeaten.size == 1 -> "Notebook: ${unbeaten[0].school} stands alone"
            unbeaten.size > 1 -> pick(lg, 6, "Notebook: ${unbeaten.size} unbeatens and counting", "Notebook: who stays perfect?")
            else -> pick(lg, 7, "Notebook: nobody's perfect", "Notebook: the week in college hoops", "Notebook: what we learned this week")
        }
        lg.news += News(lg.season, lg.day, head, lead?.id == lg.userTeamId, -1, "column", items.joinToString("\n\n"), byline(lg, lg.day), lead?.id ?: -1)
    }

    // ---------- the season in review ----------

    fun reviewPro(lg: League) {
        val ps = lg.proState ?: return
        runCatching { Receipts.gradePro(lg) }
        val champ = lg.teams.getOrNull(ps.champion) ?: return
        val finals = ps.series.firstOrNull { it.round == 4 }
        val runner = finals?.let { lg.team(if (it.hi == champ.id) it.lo else it.hi) }
        val award = { name: String -> lg.seasonAwards.firstOrNull { it.award == name } }
        val parts = mutableListOf<String>()
        parts += "${The(lg, champ)} are champions" + (runner?.let { ", beating ${the(lg, it)}" } ?: "") +
            (finals?.let { " (${ProSeason.seriesStatus(lg, it)})" } ?: "") + "." + (award("Finals MVP")?.let { " ${it.name} was Finals MVP." } ?: "")
        val mvp = award("MVP")
        val roy = award("Rookie of the Year")
        if (mvp != null) parts += "${mvp.name} won MVP" + (lg.playerOrNull(mvp.pid)?.let { " (${Awards.line(it)})" } ?: "") + "." + (roy?.let { " ${it.name} was Rookie of the Year." } ?: "")
        val best = lg.teams.maxByOrNull { it.w }!!
        val scorer = qualified(lg).maxByOrNull { it.stats.ppg }
        parts += "Best record: ${the(lg, best)} at ${best.record}." + (scorer?.let { " Scoring title: ${it.name}, ${f1(it.stats.ppg)} a game." } ?: "")
        ps.preview?.takeIf { it.season == lg.season }?.let { pv ->
            val diff = lg.teams.mapNotNull { t -> pv.proj[t.id]?.let { t to t.w - it.w } }
            diff.maxByOrNull { it.second }?.takeIf { it.second >= 6 }?.let { (t, d) -> parts += "Biggest surprise: ${the(lg, t)}. We projected ${pv.proj.getValue(t.id).w} wins; they won ${t.w}, $d more." }
            diff.minByOrNull { it.second }?.takeIf { it.second <= -6 }?.let { (t, d) -> parts += "Biggest disappointment: ${the(lg, t)}, projected for ${pv.proj.getValue(t.id).w} and finished with ${t.w}." }
            pv.proj.maxByOrNull { it.value.title }?.let { lg.teams.getOrNull(it.key) }?.let { fav ->
                parts += if (fav.id == champ.id) "We picked them in the fall. For once, the favorite held." else
                    "Our preseason favorite, ${the(lg, fav)}, went ${fav.record}: ${resultWords(fav.history.lastOrNull { it.season == lg.season }?.result ?: "")}."
            }
        }
        defenseLine(lg)?.let { parts += it }
        ps.preview?.takeIf { it.season == lg.season }?.let { pv -> Receipts.paragraph(pv.bold, pv.graded, pv.outcomes, nextYear = false)?.let { parts += it } }
        val head = pick(lg, 10, "${lg.season} in review: the ${nick(lg, champ)}' year", "The season in review: ${champ.name}, champions", "Looking back at ${lg.season}: how the ${nick(lg, champ)} won it all")
        lg.news += News(lg.season, lg.day, head, champ.id == lg.userTeamId, -1, "column", parts.joinToString("\n\n"), byline(lg, 99), champ.id, mvp?.pid ?: -1)
    }

    fun reviewCollege(lg: League) {
        val ncaa = lg.ncaa ?: return
        runCatching { Receipts.gradeCollege(lg) }
        val champ = lg.teams.getOrNull(ncaa.champion) ?: return
        val runner = ncaa.rounds.getOrNull(5)?.firstOrNull { it >= 0 && it != ncaa.champion }?.let { lg.team(it) }
        val withAward = { code: String -> lg.players.firstOrNull { p -> p.awards.contains("${lg.season} $code") } }
        val parts = mutableListOf<String>()
        parts += "${champ.school} won the national title" + (runner?.let { ", beating ${it.school} in the final" } ?: "") + ", and finished ${champ.record}." +
            (withAward("MOP")?.let { " ${it.name} was the Most Outstanding Player." } ?: "")
        withAward("Nat MVP")?.let { p -> parts += "${p.name} (${lg.team(p.teamId.coerceAtLeast(0)).school}) was the national Player of the Year: ${Awards.line(p)}." }
        val best = lg.teams.maxByOrNull { it.w * 100 - it.l * 100 + it.prestige }!!
        parts += "Best record: ${best.school} at ${best.record}."
        lg.preview?.takeIf { it.season == lg.season }?.let { pv ->
            val ranked = lg.teams.filter { it.net in 1..25 }
            ranked.maxByOrNull { (pv.proj[it.id]?.nat ?: 0) - it.net }?.let { t ->
                val was = pv.proj[t.id]?.nat ?: 0
                if (was - t.net >= 20) parts += "Biggest surprise: ${t.school}. We picked it No. $was; it finished No. ${t.net} in the NET at ${t.record}."
            }
            lg.teams.filter { (pv.proj[it.id]?.nat ?: 99) <= 10 }.maxByOrNull { it.net - (pv.proj[it.id]?.nat ?: 0) }?.let { t ->
                if (t.net >= 30) parts += "Biggest disappointment: ${t.school}, our preseason No. ${pv.proj.getValue(t.id).nat}, finished ${t.record} and No. ${t.net} in the NET."
            }
            pv.proj.entries.firstOrNull { it.value.nat == 1 }?.let { lg.teams.getOrNull(it.key) }?.let { no1 ->
                parts += if (no1.id == champ.id) "We had them No. 1 in October. Wire to wire." else
                    "Our preseason No. 1, ${no1.school}, ${Previews.lastSeason(no1.history.lastOrNull { it.season == lg.season }?.result ?: "")}."
            }
        }
        defenseLine(lg)?.let { parts += it }
        lg.preview?.takeIf { it.season == lg.season }?.let { pv -> Receipts.paragraph(pv.bold, pv.graded, pv.outcomes, nextYear = false)?.let { parts += it } }
        val head = pick(lg, 11, "${lg.season} in review: ${champ.school}'s year", "The season in review: ${champ.school}, champions", "Looking back at ${lg.season}: how ${champ.school} won it all")
        lg.news += News(lg.season, lg.day, head, champ.id == lg.userTeamId, -1, "column", parts.joinToString("\n\n"), byline(lg, 98), champ.id, withAward("Nat MVP")?.id ?: -1)
    }

    /** How last season's champion did this season, for the review. */
    private fun defenseLine(lg: League): String? {
        val d = lg.defense?.takeIf { it.season == lg.season } ?: return null
        val t = lg.teams.getOrNull(d.champ) ?: return null
        val champNow = if (lg.pro) lg.proState?.champion else lg.ncaa?.champion
        val who = if (lg.pro) "the ${t.name}" else t.school
        val result = if (lg.pro) ProSeason.resultFor(lg, t.id) else Awards.resultFor(lg, t.id)
        return if (champNow == t.id) {
            val run = TitleDefense.streak(lg, t, lg.season - 1) + 1
            "${who.replaceFirstChar { it.uppercase() }} did it again: ${TitleDefense.runWords(run)}."
        } else "Last year's champions, $who, " + when {
            result == "Missed playoffs" || result == "Missed Tournament" || result.startsWith("NIT") -> "didn't make it back to the ${if (lg.pro) "playoffs" else "tournament"} (${t.record})."
            lg.pro -> "${resultWords(result)} (${t.record})."
            else -> "went out in the $result (${t.record})."
        } + if (d.streak >= 2) " Their run of ${TitleDefense.runWords(d.streak)} is over." else ""
    }

    private fun resultWords(result: String) = when {
        result.isEmpty() -> "their season ended early"
        result == "Missed playoffs" -> "they missed the playoffs"
        result.startsWith("Lost") -> "they ${result.replaceFirstChar { it.lowercase() }}"
        else -> result.replaceFirstChar { it.lowercase() }
    }
}
