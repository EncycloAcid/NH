package com.nh.hoopscoach.game

import kotlinx.serialization.Serializable
import kotlin.math.pow
import kotlin.math.roundToInt

/** One season of a father's college career, for the side-by-side with his son. */
@Serializable
class AncestorSeason(val cls: Int, val season: Int, val school: String, val ppg: Double, val rpg: Double, val apg: Double)

/** A generation before this one: how he played, and what a son inherits from him. */
@Serializable
class Ancestor(
    val played: PlayedAs,
    val first: String,
    val last: String,
    val number: Int,
    /** His face (an encoded [Face]): his son looks like him. */
    val face: String,
    /** "Atlanta, GA". */
    val hometown: String,
    val pos: String,
    val height: Int,
    val weight: Int,
    /** His archetype's name ([Arch]). */
    val style: String,
    /** His three best ratings ([Attr] ordinals): where his son is born with an edge. */
    val strengths: List<Int>,
    val seasons: List<AncestorSeason>,
    /** The college he's remembered at: his last (or only) school. */
    val school: String,
    val age: Int,
    /** The era his career was played in ([Era] name): his son's goes on in it. */
    val era: String = "TODAY",
    /** His badges when he retired ([Badge] name to tier): his son starts with a head start on them. */
    val badges: Map<String, Int> = emptyMap(),
) {
    val name get() = "$first $last"
}

/**
 * Legacy mode: when a player career ends, the next one can be his son's. Years later, in a new college world,
 * the son is a high-school senior with his father's name, number, look and strengths. Play his career (his
 * father's school always offers), or coach him: take the old school's job and recruit him in this year's class.
 * The father's college career goes up in the school's Hall of Fame (his number in the rafters, if he earned it),
 * his achievements and coaching tree carry over, and the press never lets the son forget whose kid he is.
 */
object LegacyMode {
    /** A finished player career can hand off to the next generation. */
    fun available(lg: League) = lg.playerMode && lg.road?.ended == true && lg.hero != null

    /** The generation before this one. */
    fun father(lg: League): Ancestor? = lg.lineage.lastOrNull()

    /** Years until his son is a high-school senior: born when he was about 26. */
    fun gap(lg: League): Int = (44 - (lg.hero?.age ?: 30)).coerceIn(2, 18)

    /** The father's school as it's named in a college world, if it's there. */
    fun school(world: League, a: Ancestor): Team? = world.teams.firstOrNull { it.school == a.school }

    /** The finished career, as his son will know it. */
    fun ancestorOf(lg: League): Ancestor? {
        val h = lg.hero ?: return null
        val played = Lifetime.fromPlayer(lg) ?: return null
        val col = h.career.filter { it.cls != PRO_YEAR && it.s.gp > 0 }
        val seasons = col.map { AncestorSeason(it.cls, it.season, it.team, it.s.ppg, it.s.rpg, it.s.apg) }.toMutableList()
        if (!lg.pro && h.stats.gp > 0 && col.none { it.season == lg.season })
            seasons += AncestorSeason(h.year, lg.season, lg.teams.getOrNull(h.teamId)?.school ?: "", h.stats.ppg, h.stats.rpg, h.stats.apg)
        val school = played.college.substringAfterLast("→").trim().ifEmpty { seasons.lastOrNull()?.school ?: "" }
        return Ancestor(
            played = played, first = h.first, last = h.last, number = h.number, face = Face.of(h).encode(),
            hometown = h.hometown, pos = h.pos.name, height = h.height, weight = h.weight, style = h.style.name,
            strengths = Attr.entries.sortedByDescending { h[it] }.take(3).map { it.ordinal },
            seasons = seasons, school = school, age = h.age,
            era = lg.proState?.era ?: lg.era,
            badges = runCatching { Badges.of(lg, h).associate { it.first.name to it.second } }.getOrDefault(emptyMap()),
        )
    }

    // ---------- how big the name is ----------

    val TIER_NAMES = listOf("A familiar name", "A known name", "A star's son", "A legend's son", "Basketball royalty")

    private fun times(h: String) = Regex("^(\\d+)×").find(h.trim())?.groupValues?.get(1)?.toIntOrNull() ?: 1

    /** His father's draft slot, or null if he went undrafted. */
    fun pick(a: Ancestor): Int? = a.played.draft.substringAfter("No. ", "").substringBefore(" ").toIntOrNull()

    /** What his father's career adds up to. */
    fun score(a: Ancestor): Int {
        val p = a.played
        fun n(key: String) = p.collegeHonors.filter { it.contains(key) }.sumOf { times(it) }
        val pick = pick(a) ?: 99
        return n("National Player of the Year") * 4 + n("Conference Player of the Year") * 2 + n("All-American") * 3 + n("National champion") * 3 +
            (when { pick <= 3 -> 4; pick <= 14 -> 3; pick <= 30 -> 2; pick <= 60 -> 1; else -> 0 }) +
            p.proSeasons.coerceAtMost(15) / 3 + p.allStars * 2 + p.rings * 3 + p.mvps * 5 + (if (p.verdict.contains("Hall")) 8 else 0)
    }

    /**
     * How big the name is, 0 to 4: a familiar name, a known name, a star's son, a legend's son, basketball
     * royalty. A third generation of stars carries the name a step further.
     */
    fun tier(a: Ancestor, line: List<Ancestor> = emptyList()): Int {
        val s = score(a)
        var t = when { s < 3 -> 0; s < 8 -> 1; s < 16 -> 2; s < 28 -> 3; else -> 4 }
        if (t in 2..3 && line.filter { it !== a }.any { score(it) >= 16 }) t++
        return t
    }

    fun tier(lg: League): Int = father(lg)?.let { tier(it, lg.lineage) } ?: 0

    /** His father's college points a game, over all his seasons. */
    fun collegePpg(a: Ancestor): Double = a.seasons.takeIf { it.isNotEmpty() }?.map { it.ppg }?.average() ?: 0.0

    /** His father's NBA points a game ("12 seasons · 18.2 pts, ..."). */
    fun proPpg(a: Ancestor): Double = Regex("([\\d.]+) pts").find(a.played.proLine)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0

    /** The name sells: a famous father makes his son a five-star. */
    fun sonStars(a: Ancestor) = if (tier(a) >= 2 || a.played.allStars > 0 || a.played.rings > 0 || a.played.mvps > 0 || a.played.verdict.contains("Hall") ||
        a.played.collegeHonors.isNotEmpty()) 5 else 4

    /** "Carter" to "Carter Jr." when he's named for his father, "Carter Jr." to "Carter III", and so on. */
    fun sonLast(a: Ancestor, first: String): String {
        if (!first.trim().equals(a.first, ignoreCase = true)) return a.last.substringBefore(" Jr.").substringBefore(" II").substringBefore(" IV").trim()
        return when {
            a.last.endsWith(" Jr.") -> a.last.removeSuffix(" Jr.") + " III"
            a.last.endsWith(" III") -> a.last.removeSuffix(" III") + " IV"
            a.last.endsWith(" IV") -> a.last.removeSuffix(" IV") + " V"
            else -> "${a.last} Jr."
        }
    }

    private fun heightRange(pos: Pos) = when (pos) { Pos.PG -> 70..78; Pos.SG -> 72..80; Pos.SF -> 75..82; Pos.PF -> 77..84; Pos.C -> 79..87 }

    /** He looks like his father: the same skin, eyes and hair color, often the same cut, and no beard yet at 18. */
    fun sonFace(a: Ancestor): String {
        val f = Face.decode(a.face) ?: return ""
        val r = Face.random(System.nanoTime())
        val color = if (f.hairColor == 6) (if (f.skin >= 4) 0 else 1) else f.hairColor
        return Face(f.skin, f.head, if (Rng.chance(0.5) || f.hair == 0) (if (f.hair == 0) r.hair else f.hair) else r.hair, color, 0, f.eyes, f.brows, 0, 0).encode()
    }

    /** The creator's spec for the son: his father's build, number, hometown and style, at [pos]. */
    fun sonSpec(a: Ancestor, first: String, pos: Pos): Road.Spec {
        val town = a.hometown.substringBefore(",").trim().ifEmpty { "Hometown" }
        val state = a.hometown.substringAfter(",", "").trim().takeIf { it in Geo.states } ?: "GA"
        val range = heightRange(pos)
        val height = (a.height + Rng.int(-2, 2)).coerceIn(range.first, range.last)
        val weight = (a.weight + Rng.int(-18, 6)).coerceIn(165, 280)
        val styles = Ratings.styles(pos)
        val style = Arch.entries.firstOrNull { it.name == a.style }?.takeIf { it in styles } ?: styles.first()
        return Road.Spec(first.trim().ifEmpty { a.first }, sonLast(a, first), pos, height, weight, town, state, style, sonStars(a), a.number, null, eraOf(a), sonFace(a))
    }

    /**
     * What he gets from his father, more the bigger the name: an edge in the three things his father did best
     * (+2 to +6), more ceiling (+1 to +5), and a head start on each of his father's badges.
     */
    fun inherit(p: Player, a: Ancestor, line: List<Ancestor> = emptyList()) {
        val t = tier(a, line)
        for (i in a.strengths) if (i in p.attrs.indices) p.attrs[i] = (p.attrs[i] + 2 + t).coerceAtMost(99)
        p.pot = (p.pot + 1 + t).coerceIn(p.ovr + 6, 99)
        p.happiness = 85
        for ((name, tier) in a.badges) Badge.entries.firstOrNull { it.name == name }?.let { Badges.headStart(p, it, tier) }
    }

    /** The era his career was played in. */
    fun eraOf(a: Ancestor): Era = Era.entries.firstOrNull { it.name == a.era } ?: Era.TODAY

    /**
     * A college world [gap] years on, with the father's career in it: his school's Hall of Fame (his number
     * retired, if he earned it), his achievements and coaching tree, and the family line.
     */
    fun newWorld(old: League, a: Ancestor): League {
        val years = gap(old)
        val w = LeagueGen.newLeague(old.season + years)
        // The family's era goes on: that year's real players in school (an era career), then the same NBA timeline.
        w.era = eraOf(a).name
        if (eraOf(a) != Era.TODAY) runCatching { EraCollege.populate(w) }
        w.lineage += old.lineage
        w.lineage += a
        runCatching { Trophies.carry(old, w) }
        w.tree += old.tree
        enshrine(w, a)
        return w
    }

    /** The father in his old school's Hall of Fame: a star there gets his number retired. */
    fun enshrine(w: League, a: Ancestor) {
        val t = school(w, a) ?: return
        if (t.legends.any { it.name == a.name }) return
        val p = a.played
        val star = p.collegeHonors.any { it.contains("All-American") || it.contains("Player of the Year") || it.contains("champion") }
        val pick = p.draft.substringAfter("No. ").substringBefore(" ").toIntOrNull() ?: 99
        val pro = p.allStars > 0 || p.rings > 0 || p.mvps > 0
        if (!star && !pro && pick > 14) return
        val retired = star || p.verdict.contains("Hall") || p.mvps > 0 || p.allStars >= 3 || pick <= 3
        val here = a.seasons.filter { it.school == a.school }.ifEmpty { a.seasons }
        val from = here.minOfOrNull { it.season } ?: (w.season - 20)
        val to = here.maxOfOrNull { it.season } ?: from
        val honors = (p.collegeHonors + listOfNotNull(
            if (p.allStars > 0) "${p.allStars}× NBA All-Star" else null,
            if (p.rings > 0) "${p.rings}× NBA champion" else null,
            if (p.mvps > 0) "${p.mvps}× NBA MVP" else null,
        )).take(4)
        t.legends += Legend(a.name, Pos.entries.firstOrNull { it.name == a.pos }, a.number, from, to, p.collegeLine, honors, w.season, retired, score = if (retired) 12 else 6)
    }

    /** The numbers his family made famous at [t]: he can wear his father's retired number there. */
    fun familyNumbers(lg: League, t: Team): Set<Int> = lg.lineage.filter { it.school == t.school }.map { it.number }.toSet()

    /** The father's school always offers his son, at the top of its list. */
    fun withFatherOffer(w: League, son: Player, a: Ancestor, offers: List<SchoolOffer>): List<SchoolOffer> {
        val t = school(w, a) ?: return offers
        if (offers.any { it.teamId == t.id }) return offers
        val ahead = Road.ahead(w, t, son)
        return listOf(SchoolOffer(t.id, Road.nilFor(w, t, son), Road.roleFor(ahead), ahead, Geo.closeness(t, son))) + offers
    }

    /** One line on what the father did: "an All-American at Georgia Tech and a 3-time NBA All-Star". */
    fun resume(a: Ancestor): String {
        val p = a.played
        val col = p.collegeHonors.take(2).joinToString(" and ").takeIf { it.isNotEmpty() }?.let { h ->
            (if (h.first().lowercaseChar() in "aeiou") "an " else "a ") + h + " at ${a.school}"
        } ?: "a ${a.school} ${if (a.seasons.size >= 4) "four-year player" else "player"}"
        val pro = listOfNotNull(
            if (p.mvps > 0) "${p.mvps}-time MVP" else null,
            if (p.rings > 0) "${p.rings}-time champion" else null,
            if (p.allStars > 0) "${p.allStars}-time All-Star" else null,
        ).joinToString(", ")
        return when {
            pro.isNotEmpty() -> "$col, then a $pro in the NBA"
            p.proSeasons > 0 -> "$col, then ${p.proSeasons} seasons in the NBA"
            else -> col
        }
    }

    /** The son signs: the headlines, and a following that starts with the name. */
    fun onStart(lg: League, h: Player) {
        val dad = father(lg) ?: return
        val t = lg.teams.getOrNull(h.teamId) ?: return
        lg.road?.let { r -> r.followers = (r.followers * (1.2 + 0.25 * tier(lg))).roundToInt(); r.seasonStartFollowers = r.followers }
        val home = t.school == dad.school
        lg.news += News(lg.season, 0,
            if (home) "${h.name} follows his father to ${t.school}" else "${h.name}, son of ${dad.name}, picks ${t.school} over his father's school",
            true, kind = "legacy", teamId = t.id, pid = h.id, source = "Hoops Wire",
            body = "His father, ${dad.name}, was ${resume(dad)}. The son wears No. ${h.number}" + (if (h.number == dad.number) ", his father's number" else "") + ". " +
                (if (home) "The old gym is already sold out for his first game." else "Nobody in ${dad.school} is happy about it."))
    }

    // ---------- coaching your son ----------

    /** The coach path: the father coaches his old school, and his son is in this year's recruiting class. */
    fun coachSon(old: League, a: Ancestor): League? {
        val w = LeagueGen.newLeague(old.season + gap(old))
        if (school(w, a) == null) return null
        w.era = eraOf(a).name
        if (eraOf(a) != Era.TODAY) runCatching { EraCollege.populate(w) }
        val college = Afterward.becomeCoach(old, w, a.school, years = gap(old))
        college.lineage += old.lineage
        college.lineage += a
        enshrine(college, a)
        addSonRecruit(college, a)
        return college
    }

    /** His son, a top recruit in this class who leans toward his father's school (but still has to be won). */
    fun addSonRecruit(lg: League, a: Ancestor, first: String = a.first): Recruit {
        val stars = sonStars(a)
        val pos = Pos.entries.firstOrNull { it.name == a.pos } ?: Pos.SG
        val rank = if (stars == 5) Rng.range(3, 14) else Rng.range(26, 60)
        val n = Recruiting.CLASS_SIZE
        val x = 1.0 - (rank - 1).toDouble() / (n - 1)
        val ovr = (48 + 36 * x.pow(2.2)).roundToInt().coerceIn(40, 87)
        val p = LeagueGen.makePlayer(lg, pos, 0, ovr, -1)
        val spec = sonSpec(a, first, pos)
        p.first = spec.first; p.last = spec.last
        p.hometown = a.hometown.ifEmpty { p.hometown }
        p.number = a.number.coerceIn(0, 99)
        p.height = spec.height; p.weight = spec.weight
        Face.decode(spec.face)?.let { p.face = it.encode() }
        p.country = null
        inherit(p, a, lg.lineage)
        p.ovrHistory.clear()
        p.pot = (p.ovr + Rng.range(6, 12)).coerceAtMost(99)
        for (r in lg.recruits) if (r.p.recruitRank >= rank) r.p.recruitRank++
        p.recruitRank = rank
        p.stars = stars
        p.posRank = lg.recruits.count { it.p.pos == pos && it.p.recruitRank < rank } + 1
        val r = Recruit(p)
        r.nilAsk = (Ratings.expectedNil(p) * (1.0 + (stars - 2) * 0.35)).roundToInt() / 5 * 5
        Recruiting.seedFog(r)
        r.pref = RecruitPref.NONE
        r.onBoard = true
        val home = school(lg, a)
        if (home != null) r.interest[home.id] = 60
        Brand.ensure(lg)
        val pulls = Brand.pullTable(lg)
        lg.teams.filter { it.id != home?.id }.sortedByDescending { pulls[it.id] }.take(12).shuffled(Rng.r).take(3).forEach { r.interest[it.id] = Rng.range(28, 44) }
        val at = lg.recruits.indexOfFirst { it.p.recruitRank > rank }.let { if (it < 0) lg.recruits.size else it }
        lg.recruits.add(at, r)
        lg.legacySonId = p.id
        lg.news += News(lg.season, 0, "${p.name}, ${a.name}'s son, is a $stars-star recruit, and his father is recruiting him", true, kind = "legacy",
            teamId = home?.id ?: -1, source = "Hoops Wire",
            body = "His father was ${resume(a)}. Now he's the coach, and the No. $rank prospect in the class wears his No. ${p.number}. ${home?.school ?: "His father's school"} has the inside track, but ${r.interest.size - 1} other programs are in it too.")
        return r
    }

    // ---------- chasing Dad ----------

    /** One of his father's marks: what to do, what his father did, and whether he's passed it. */
    class Mark(val id: String, val goal: String, val dad: String, val done: Boolean)

    /** His father's marks, from his college scoring to his rings. */
    fun marks(lg: League): List<Mark> {
        val a = father(lg) ?: return emptyList()
        val r = lg.road ?: return emptyList()
        val p = a.played
        val done = r.legacyMarks
        val out = mutableListOf<Mark>()
        val ppg = collegePpg(a)
        if (ppg > 0) out += Mark("ppg", "Out-score his college career", "Dad: %.1f ppg".format(ppg), "ppg" in done)
        fun honor(id: String, key: String, goal: String) {
            p.collegeHonors.firstOrNull { it.contains(key) }?.let { out += Mark(id, goal, "Dad: $it", id in done) }
        }
        honor("aa", "All-American", "Make All-American")
        honor("cpoy", "Conference Player of the Year", "Win Conference Player of the Year")
        honor("poy", "National Player of the Year", "Win National Player of the Year")
        honor("champ", "National champion", "Win a national title")
        val pk = pick(a)
        out += Mark("draft", if (pk != null) "Go No. $pk or higher in the draft" else "Get drafted", if (pk != null) "Dad: No. $pk pick" else "Dad went undrafted", "draft" in done)
        if (p.proSeasons > 0) out += Mark("pro", "Play ${p.proSeasons} NBA season${if (p.proSeasons == 1) "" else "s"}", "Dad: ${p.proSeasons}", "pro" in done)
        proPpg(a).takeIf { it > 0 }?.let { out += Mark("proppg", "Out-score his NBA career", "Dad: %.1f ppg".format(it), "proppg" in done) }
        if (p.allStars > 0) out += Mark("allstar", "Make ${p.allStars} All-Star team${if (p.allStars == 1) "" else "s"}", "Dad: ${p.allStars}", "allstar" in done)
        if (p.rings > 0) out += Mark("rings", "Win ${p.rings} ring${if (p.rings == 1) "" else "s"}", "Dad: ${p.rings}", "rings" in done)
        if (p.mvps > 0) out += Mark("mvp", "Win ${p.mvps} MVP${if (p.mvps == 1) "" else "s"}", "Dad: ${p.mvps}", "mvp" in done)
        return out
    }

    /** His college (or pro) points a game so far, with this season. */
    private fun careerPpg(lg: League, h: Player, pro: Boolean): Pair<Double, Int> {
        val lines = h.career.filter { (it.cls == PRO_YEAR) == pro && it.s.gp > 0 }.map { it.s }.toMutableList()
        if (lg.pro == pro && h.stats.gp > 0 && h.career.none { it.season == lg.season && (it.cls == PRO_YEAR) == pro }) lines += h.stats
        val gp = lines.sumOf { it.gp }
        return (if (gp > 0) lines.sumOf { it.pts }.toDouble() / gp else 0.0) to gp
    }

    /** Checks his father's marks (each season's end, and after the draft): the news, and a reward for each one he passes. */
    fun checkMarks(lg: League) {
        val a = father(lg) ?: return
        val h = lg.hero ?: return
        val r = lg.road ?: return
        val p = a.played
        fun has(key: String) = h.awards.count { it.substringAfter(" ").startsWith(key) }
        val (colPpg, colGp) = careerPpg(lg, h, pro = false)
        val (proPpgNow, proGp) = careerPpg(lg, h, pro = true)
        val proSeasons = h.career.count { it.cls == PRO_YEAR && it.s.gp > 0 } + if (lg.pro && h.stats.gp > 0 && h.career.none { it.season == lg.season && it.cls == PRO_YEAR }) 1 else 0
        val titles = h.career.count { it.result == "National Champion" } + if (!lg.pro && runCatching { Awards.resultFor(lg, h.teamId) }.getOrNull() == "National Champion" && h.career.none { it.season == lg.season }) 1 else 0
        val pk = pick(a)
        val checks = listOf(
            "ppg" to (colGp >= 25 && colPpg >= collegePpg(a)),
            "aa" to (has("AA 1st") + has("AA 2nd") + has("AA 3rd") >= 1),
            "cpoy" to (has("Conf POY") >= 1),
            "poy" to (has("Nat MVP") >= 1),
            "champ" to (titles >= 1),
            "draft" to (r.draftPick > 0 && (pk == null || r.draftPick <= pk)),
            "pro" to (p.proSeasons > 0 && proSeasons >= p.proSeasons),
            "proppg" to (proGp >= 40 && proPpgNow >= proPpg(a) && proPpg(a) > 0),
            "allstar" to (p.allStars > 0 && r.allStars >= p.allStars),
            "rings" to (p.rings > 0 && r.rings >= p.rings),
            "mvp" to (p.mvps > 0 && r.mvps >= p.mvps),
        )
        val marks = marks(lg).associateBy { it.id }
        for ((id, ok) in checks) {
            val m = marks[id] ?: continue
            if (!ok || id in r.legacyMarks) continue
            r.legacyMarks += id
            // Each one: a skill point, a bigger following, and the story.
            h.skillPts += 1
            h.happiness = (h.happiness + 3).coerceAtMost(100)
            r.followers = (r.followers * 1.08).roundToInt()
            lg.news += News(lg.season, lg.day, "${h.name} passes his father: ${m.goal.replaceFirstChar { it.lowercase() }}", true, kind = "legacy", pid = h.id, teamId = h.teamId,
                source = "Hoops Wire", body = "${m.dad.removePrefix("Dad: ").let { "His father, ${a.name}, set the mark: $it." }} The son is past it now, ${r.legacyMarks.size} of ${marks.size} of his father's marks in all.")
            Alerts.add(lg, "You passed your father", "${m.goal}. ${m.dad}. A skill point and a bigger following.", "legacy")
        }
    }

    /**
     * The end of a season, measured against his father: in college, his father's line in the same class; in the
     * pros, his father's NBA average. Out of his shadow lifts his mood and following; far behind it weighs on him.
     */
    fun seasonEnd(lg: League) {
        val a = father(lg) ?: return
        val h = lg.hero ?: return
        val r = lg.road ?: return
        if (h.stats.gp >= 5) {
            val mine = h.stats.ppg
            val cls = when (h.year) { 1 -> "freshman"; 2 -> "sophomore"; 3 -> "junior"; 4 -> "senior"; else -> "graduate student" }
            val (dad, when_) = if (!lg.pro) (a.seasons.firstOrNull { it.cls == h.year }?.ppg ?: -1.0) to "as a $cls"
                else proPpg(a) to "in his NBA career"
            if (dad >= 0) {
                val diff = mine - dad
                val (head, mood) = when {
                    diff >= 1.0 -> "${h.name} steps out of his father's shadow" to 6
                    diff >= -2.0 -> "${h.name} keeps pace with his father" to 1
                    else -> "${h.name} is still chasing his father" to -4
                }
                h.happiness = (h.happiness + mood).coerceIn(0, 100)
                if (diff >= 1.0) r.followers = (r.followers * 1.05).roundToInt()
                r.legacyLog += "${lg.season}: %.1f ppg · Dad %.1f %s".format(mine, dad, when_)
                lg.news += News(lg.season, lg.day, head, true, kind = "legacy", pid = h.id, teamId = h.teamId, source = "Hoops Wire",
                    body = "%.1f points a game this season. His father, ${a.name}, averaged %.1f $when_.".format(mine, dad))
            }
        }
        checkMarks(lg)
    }

    // ---------- in the stands ----------

    /** His father comes to the big ones: rivalry games, the postseason, his old school, and his son's debut. */
    fun dadInStands(lg: League, g: Game): Boolean {
        val a = father(lg) ?: return false
        val h = lg.hero ?: return false
        if (!g.involves(h.teamId)) return false
        if (lg.rivalry(g) != null || g.type == GameType.NCAA || g.type == GameType.PLAYOFF || g.type == GameType.CONF_TOURNEY) return true
        if (!lg.pro && (lg.teams.getOrNull(g.home)?.school == a.school || lg.teams.getOrNull(g.away)?.school == a.school)) return true
        return h.stats.gp == 0 && (if (lg.pro) h.career.none { it.cls == PRO_YEAR } else h.career.isEmpty())
    }

    /** A lift on his shots: his father watching (more, the bigger the name), and the gym his father made famous. */
    fun heroEdge(lg: League, g: Game): Double {
        val a = father(lg) ?: return 0.0
        val h = lg.hero ?: return 0.0
        var e = if (dadInStands(lg, g)) 0.012 + 0.003 * tier(lg) else 0.0
        if (!lg.pro && !g.neutral && h.teamId == g.home && lg.teams.getOrNull(g.home)?.school == a.school) e += 0.008
        return e
    }

    fun standsLine(lg: League, g: Game): String? = father(lg)?.takeIf { dadInStands(lg, g) }?.let { "${it.name} is in the stands to watch his son." }

    /** A big night with his father watching makes the news. */
    fun afterGame(lg: League, g: Game, line: GameLogEntry) {
        val a = father(lg) ?: return
        val h = lg.hero ?: return
        if (line.pts < 25 || !dadInStands(lg, g)) return
        lg.news += News(lg.season, lg.day, "With ${a.name} watching, ${h.name} scores ${line.pts}", true, kind = "legacy", gameId = g.id, pid = h.id, teamId = h.teamId, source = "Hoops Wire")
    }

    /** The son picks a school: the coach's son committing to his father, or the one that got away. */
    fun onCommit(lg: League, r: Recruit, t: Team) {
        if (r.p.id != lg.legacySonId) return
        val dad = father(lg) ?: return
        val mine = t.id == lg.userTeamId
        lg.news += News(lg.season, lg.day,
            if (mine) "${r.p.name} commits to play for his father at ${t.school}" else "${r.p.name} turns down his father and commits to ${t.school}",
            true, kind = "legacy", teamId = t.id, pid = r.p.id, source = "Hoops Wire",
            body = if (mine) "\"I grew up in that gym,\" he said. \"There was never really anywhere else.\" ${dad.name} will coach his own son next season."
                else "\"I love my dad,\" he said. \"I need to make my own name.\" ${dad.name}'s program will see him on the schedule.")
    }
}
