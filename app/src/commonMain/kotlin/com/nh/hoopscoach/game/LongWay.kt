package com.nh.hoopscoach.game

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/** A contract on the table for an undrafted player: a G League spot or a club overseas. */
@Serializable
class AbroadOffer(
    /** "gleague" or "overseas". */
    val kind: String,
    val club: String,
    /** "Spain", or the NBA team for a G League side. */
    val where: String,
    val league: String,
    /** How good the league is, on the college overall scale (a G League regular is about 80). */
    val level: Int,
    /** Thousands of dollars for the season. */
    val salary: Int,
    /** "Starter" or "Rotation". */
    val role: String,
    /** The NBA franchise behind a G League side ([ProLeague] slot). */
    val slot: String = "",
)

/** A season away from the NBA, game by game. */
@Serializable
class Abroad(
    val offer: AbroadOffer,
    val total: Int,
    var games: Int = 0,
    var w: Int = 0,
    var l: Int = 0,
    var stats: StatLine = StatLine(),
    val recent: MutableList<String> = mutableListOf(),
    /** Practice and game reps toward his next rating point. */
    var reps: Int = 0,
    var done: Boolean = false,
)

/** An NBA team calling: who, what kind of deal, and why now. */
@Serializable
class NbaCall(val slot: String, val team: String, val deal: String, val note: String)

/**
 * The long way: undrafted, a player isn't finished. His agent has calls from the G League (a small salary, every
 * game a tryout, and a call-up when he plays his way into one) and from clubs overseas (more money, a bigger role,
 * and NBA scouts watching). He plays the season week by week; his stats, growth and badge points all count. At the
 * end of it, NBA teams may call; if not, he goes again or calls it a career. A call-up puts him on that team in his
 * era's league, the season after.
 */
object LongWay {
    private class Club(val city: String, val country: String, val league: String, val level: Int, val pay: Int)

    /** Cities, countries and leagues, from Europe's best to the leagues a step down; [Club.pay] in thousands. */
    private val CLUBS = listOf(
        Club("Madrid", "Spain", "Spanish League", 84, 900), Club("Barcelona", "Spain", "Spanish League", 84, 850),
        Club("Istanbul", "Turkey", "Turkish League", 84, 950), Club("Athens", "Greece", "Greek League", 83, 800),
        Club("Belgrade", "Serbia", "Adriatic League", 82, 600), Club("Tel Aviv", "Israel", "Israeli League", 82, 650),
        Club("Milan", "Italy", "Italian League", 82, 600), Club("Munich", "Germany", "German League", 81, 550),
        Club("Kaunas", "Lithuania", "Lithuanian League", 81, 450), Club("Vitoria", "Spain", "Spanish League", 81, 500),
        Club("Paris", "France", "French League", 80, 450), Club("Monaco", "Monaco", "French League", 81, 600),
        Club("Melbourne", "Australia", "Australian League", 79, 350), Club("Sydney", "Australia", "Australian League", 79, 350),
        Club("Tokyo", "Japan", "Japanese League", 78, 400), Club("Shanghai", "China", "Chinese League", 78, 700),
        Club("Beijing", "China", "Chinese League", 78, 750), Club("Bologna", "Italy", "Italian League", 80, 450),
        Club("Valencia", "Spain", "Spanish League", 82, 550), Club("Adelaide", "Australia", "Australian League", 78, 300),
    )

    const val G_LEAGUE_GAMES = 32
    const val OVERSEAS_GAMES = 30
    const val G_LEAGUE_LEVEL = 80

    /** The NBA's teams the year he'd arrive, by slot and name (his era's league). */
    fun franchises(lg: League): List<Pair<String, String>> =
        (if (lg.era != Era.TODAY.name) EraData.teams(lg.season + 1)?.map { it.slot to "${it.city} ${it.name}" } else null)
            ?: ProLeague.TEAMS.map { it.abbr to "${it.city} ${it.name}" }

    private fun nick(team: String) = team.substringAfterLast(' ')

    /** After Draft Night, undrafted: the calls come in. */
    fun begin(lg: League) {
        val r = lg.road ?: return
        r.longWay = true
        r.ended = false
        r.abroad = null
        r.calls.clear()
        offers(lg)
    }

    /** This year's offers: one G League side and two or three clubs overseas, by how good he is. */
    fun offers(lg: League) {
        val r = lg.road ?: return
        val h = lg.hero ?: return
        r.wayOffers.clear()
        val (slot, team) = Rng.pick(franchises(lg))
        r.wayOffers += AbroadOffer("gleague", "${nick(team)} G League", team, "G League", G_LEAGUE_LEVEL, 40,
            if (h.ovr >= G_LEAGUE_LEVEL) "Starter" else "Rotation", slot)
        // The better he is, the better the clubs that call.
        val fits = CLUBS.filter { it.level <= h.ovr + 3 }.ifEmpty { CLUBS.sortedBy { it.level }.take(4) }
        fits.shuffled(Rng.r).sortedByDescending { it.level }.take(if (h.ovr >= 80) 3 else 2).forEach { c ->
            val pay = (c.pay * (0.55 + (h.ovr - c.level + 6) * 0.06)).roundToInt().coerceIn(120, 2_500) / 5 * 5
            r.wayOffers += AbroadOffer("overseas", c.city, c.country, c.league, c.level, pay, if (h.ovr >= c.level - 1) "Starter" else "Rotation")
        }
    }

    /** He signs: the season starts. */
    fun sign(lg: League, i: Int) {
        val r = lg.road ?: return
        val h = lg.hero ?: return
        val o = r.wayOffers.getOrNull(i) ?: return
        r.abroad = Abroad(o, if (o.kind == "gleague") G_LEAGUE_GAMES else OVERSEAS_GAMES)
        r.wayOffers.clear()
        r.calls.clear()
        r.earned += o.salary
        lg.news += News(lg.season, lg.day, if (o.kind == "gleague") "${h.name} signs with the ${o.club} to chase an NBA call-up"
            else "${h.name} signs with ${o.city()} in ${o.where}: ${Road.dollars(o.salary)} for the season", true, kind = "longway", pid = h.id)
    }

    private fun AbroadOffer.city() = if (kind == "gleague") club else "$club ($league)"

    /** Plays [n] games (a week is three). */
    fun play(lg: League, n: Int) {
        val a = lg.road?.abroad ?: return
        repeat(n) { if (!a.done) game(lg) }
    }

    /** One game: his line from how he stacks up against the league, the result, his reps, badge points, and maybe a call. */
    private fun game(lg: League) {
        val r = lg.road ?: return
        val h = lg.hero ?: return
        val a = r.abroad ?: return
        val o = a.offer
        val edge = h.ovr - o.level
        val starter = o.role == "Starter" || edge >= -3
        val min = ((if (starter) 31.0 else 22.0) + edge * 0.6 + Rng.gauss() * 3).roundToInt().coerceIn(12, 40)
        val f = min / 30.0
        fun attr(x: Attr) = (h[x] - 70) / 10.0
        val scorer = 1.0 + (if (h.style.boosts.any { it == Attr.OUT || it == Attr.MID || it == Attr.INS }) 0.08 else 0.0)
        val pts = ((15.0 + edge * 1.3 + Rng.gauss() * 5.5) * f * scorer).roundToInt().coerceAtLeast(0)
        val posReb = when (h.pos) { Pos.C -> 8.0; Pos.PF -> 6.5; Pos.SF -> 4.5; Pos.SG -> 3.5; Pos.PG -> 3.0 }
        val posAst = when (h.pos) { Pos.PG -> 5.5; Pos.SG -> 3.0; Pos.SF -> 2.5; Pos.PF -> 2.0; Pos.C -> 1.5 }
        val reb = ((posReb + attr(Attr.REB) * 1.2 + Rng.gauss() * 2) * f).roundToInt().coerceAtLeast(0)
        val ast = ((posAst + attr(Attr.PAS) * 1.0 + Rng.gauss() * 1.6) * f).roundToInt().coerceAtLeast(0)
        val stl = ((0.8 + attr(Attr.STL) * 0.3 + Rng.gauss() * 0.7) * f).roundToInt().coerceAtLeast(0)
        val blk = ((if (h.pos == Pos.C || h.pos == Pos.PF) 0.9 else 0.3) * f + attr(Attr.BLK) * 0.2 + Rng.gauss() * 0.6).roundToInt().coerceAtLeast(0)
        val tov = ((1.6 + Rng.gauss() * 1.0) * f).roundToInt().coerceAtLeast(0)
        // Shots that add up to his points: threes by his range, free throws, the rest twos.
        val threeShare = ((h[Attr.OUT] - 55) / 100.0).coerceIn(0.05, 0.45)
        val tpm = (pts * threeShare / 3 + Rng.gauss() * 0.6).roundToInt().coerceIn(0, pts / 3)
        val ftm = ((pts - tpm * 3) * 0.18 + Rng.gauss() * 0.8).roundToInt().coerceIn(0, (pts - tpm * 3).coerceAtLeast(0))
        val twos = ((pts - tpm * 3 - ftm) / 2).coerceAtLeast(0)
        val pct = (0.45 + edge * 0.005).coerceIn(0.36, 0.56)
        val fgm = twos + tpm
        val fga = (fgm / pct).roundToInt().coerceAtLeast(fgm)
        val tpa = (tpm / 0.35).roundToInt().coerceAtLeast(tpm)
        val s = StatLine(gp = 1, gs = if (starter) 1 else 0, sec = min * 60, pts = tpm * 3 + twos * 2 + ftm, fgm = fgm, fga = fga, tpm = tpm, tpa = tpa,
            ftm = ftm, fta = (ftm / 0.76).roundToInt().coerceAtLeast(ftm), drb = reb - reb / 4, orb = reb / 4, ast = ast, stl = stl, blk = blk, tov = tov,
            clutch = if (Rng.chance(0.3)) Rng.range(0, 4) else 0)
        a.stats.add(s)
        a.games++
        val won = Rng.chance((0.5 + edge * 0.02 + (s.pts - 12) * 0.006).coerceIn(0.25, 0.78))
        if (won) a.w++ else a.l++
        a.recent.add(0, "Game ${a.games}: ${if (won) "W" else "L"} · ${s.pts} pts, ${reb} reb, ${ast} ast in $min min")
        while (a.recent.size > 8) a.recent.removeAt(a.recent.size - 1)
        // Badge points count here too: inside, mid-range, threes.
        runCatching { Badges.afterGame(lg, h, s, intArrayOf((twos * 0.6).roundToInt(), (twos * 0.4).roundToInt(), tpm)) }
        // Reps: every game and practice, more for more minutes; each step a rating point in his archetype's strengths.
        a.reps += 8 + min / 3
        while (a.reps >= 30) {
            a.reps -= 30
            if (h.ovr < h.pot) Ratings.raise(h, growPick(h))
        }
        // The G League's whole point: play your way into a call-up.
        if (o.kind == "gleague" && a.games >= 8 && r.calls.isEmpty()) {
            val form = a.recent.take(6).mapNotNull { Regex("(\\d+) pts").find(it)?.groupValues?.get(1)?.toIntOrNull() }.average()
            if ((h.ovr >= 84 || (h.ovr >= 82 && form >= 20)) && Rng.chance(0.25)) {
                val team = franchises(lg).firstOrNull { it.first == o.slot }?.second ?: o.where
                r.calls += NbaCall(o.slot, team, "Two-way deal", "The ${nick(team)} call you up from their G League side.")
                lg.news += News(lg.season, lg.day, "The ${nick(team)} call up ${h.name} on a two-way deal", true, kind = "longway", pid = h.id)
                Alerts.add(lg, "Call-up!", "The ${team} want you on a two-way deal. Take it now, or finish the season first.", "longway")
            }
        }
        if (a.games >= a.total) endSeason(lg)
    }

    /** Mostly his archetype's strengths, sometimes anything. */
    private fun growPick(h: Player): Int = if (h.style.boosts.isNotEmpty() && Rng.chance(0.6)) Rng.pick(h.style.boosts).ordinal else Rng.int(0, Attr.entries.size)

    /** A summer of work: a young player grows toward his ceiling (1 to 4 points of overall). */
    private fun summer(h: Player) {
        if (h.age > 27 || h.ovr >= h.pot) return
        val target = h.ovr + ((h.pot - h.ovr) * 0.3).roundToInt().coerceIn(1, 4)
        var k = 0
        while (h.ovr < target && h.ovr < h.pot && k++ < 80) Ratings.raise(h, growPick(h))
    }

    /** The season's over: a year passes, the line goes in his story, and NBA teams call if he's earned it. */
    private fun endSeason(lg: League) {
        val r = lg.road ?: return
        val h = lg.hero ?: return
        val a = r.abroad ?: return
        a.done = true
        val s = a.stats
        val o = a.offer
        r.abroadPast += "${lg.season + 1} · ${o.city()} · ${a.w}-${a.l} · %.1f ppg, %.1f rpg, %.1f apg".format(s.ppg, s.rpg, s.apg)
        r.story += "${lg.season + 1} · ${o.city()} · ${if (o.kind == "gleague") "G League" else "Overseas"} · %.1f ppg, %.1f rpg, %.1f apg · ${a.w}-${a.l}".format(s.ppg, s.rpg, s.apg)
        // A year has gone by, and a summer of work.
        lg.season++
        h.age++
        summer(h)
        val good = h.ovr >= 84 || (h.ovr >= 82 && s.ppg >= 16) || (h.ovr >= 80 && s.ppg >= 21)
        val n = when { h.ovr >= 87 -> 3; good -> if (Rng.chance(0.5)) 2 else 1; else -> 0 }
        val taken = r.calls.map { it.slot }.toMutableSet()
        franchises(lg).shuffled(Rng.r).filter { it.first !in taken }.take(n - r.calls.size.coerceAtMost(n)).forEach { (slot, team) ->
            val deal = if (h.ovr >= 86) "Standard contract" else if (h.ovr >= 84) "Training camp deal" else "Two-way deal"
            r.calls += NbaCall(slot, team, deal, if (o.kind == "gleague") "They watched you in the G League." else "Their scouts saw you in ${o.where}.")
        }
        lg.news += News(lg.season, lg.day,
            if (r.calls.isNotEmpty()) "${h.name}'s season in ${if (o.kind == "gleague") "the G League" else o.where} pays off: ${r.calls.size} NBA ${if (r.calls.size == 1) "team calls" else "teams call"}"
            else "${h.name} finishes the season with ${o.city()}: %.1f points a game".format(s.ppg), true, kind = "longway", pid = h.id)
    }

    /** He takes an NBA team's call: the going-pro screen, then that team, the season after. */
    fun accept(lg: League, i: Int) {
        val r = lg.road ?: return
        val h = lg.hero ?: return
        val c = r.calls.getOrNull(i) ?: return
        val a = r.abroad
        // Leaving mid-season (a G League call-up) still takes the year.
        if (a != null && !a.done) {
            a.done = true
            r.abroadPast += "${lg.season + 1} · ${a.offer.city()} · ${a.w}-${a.l} · %.1f ppg (called up)".format(a.stats.ppg)
            r.story += "${lg.season + 1} · ${a.offer.city()} · G League · %.1f ppg, called up by the ${nick(c.team)}".format(a.stats.ppg)
            lg.season++
            h.age++
        }
        r.signSlot = c.slot
        r.signTeam = c.team
        r.goingPro = true
        r.undrafted = true
        r.longWay = false
        r.abroad = null
        r.calls.clear()
        r.wayOffers.clear()
        r.ending = "${h.name} took the long way and made it: ${c.deal.lowercase()} with the ${c.team}."
        lg.news += News(lg.season, lg.day, "${h.name} signs with the ${c.team}: the long way to the NBA", true, kind = "longway", pid = h.id)
    }

    /** No call (or none he wants): another season, with this year's offers. */
    fun again(lg: League) {
        val r = lg.road ?: return
        r.abroad = null
        r.calls.clear()
        offers(lg)
    }

    /** He calls it a career. */
    fun retire(lg: League) {
        val r = lg.road ?: return
        val h = lg.hero
        val years = r.abroadPast.size
        r.longWay = false
        r.ended = true
        r.abroad = null
        r.calls.clear()
        r.wayOffers.clear()
        r.ending = if (years == 0) "${h?.name ?: "He"} went undrafted and called it a career."
            else "${h?.name ?: "He"} played $years season${if (years == 1) "" else "s"} away from the NBA, then called it a career."
        lg.news += News(lg.season, lg.day, r.ending, true)
    }
}
