package com.nh.hoopscoach.game

import kotlin.math.roundToInt

/**
 * A player career's long view:
 *  - season goals: before each season he sets a personal target (a scoring average, a rebound or assist
 *    average, a shooting mark, an All-Star or All-Conference nod, a win total), graded at season's end;
 *  - legacy: a score built from his awards, titles and production, with tiers from prospect to the GOAT
 *    conversation, and a headline each time he climbs one.
 */
object PlayerCareer {
    fun active(lg: League) = lg.playerMode && lg.road != null && lg.hero != null

    // ---------- season goals ----------

    class Goal(val kind: String, val target: Double, val label: String, val reward: String)

    private fun f1(d: Double) = String.format(java.util.Locale.US, "%.1f", d)

    /** The goals on offer this season, built from last season's numbers so each is a stretch. */
    fun options(lg: League): List<Goal> {
        val h = lg.hero ?: return emptyList()
        val last = h.career.lastOrNull { it.season == lg.season - 1 && it.s.gp > 0 }?.s
        val ppg = last?.ppg ?: (h.ovr - 50) / 2.5
        val rpg = last?.rpg ?: 4.0
        val apg = last?.apg ?: 2.5
        val out = mutableListOf<Goal>()
        val pts = (kotlin.math.ceil(ppg + 3.0)).coerceIn(8.0, 36.0)
        out += Goal("ppg", pts, "Average ${pts.roundToInt()} points", "followers +10% · leadership +4")
        if (rpg >= apg) {
            val r = (kotlin.math.ceil(rpg + 1.5)).coerceIn(4.0, 15.0)
            out += Goal("rpg", r, "Average ${r.roundToInt()} rebounds", "followers +6% · leadership +5")
        } else {
            val a = (kotlin.math.ceil(apg + 1.5)).coerceIn(3.0, 12.0)
            out += Goal("apg", a, "Average ${a.roundToInt()} assists", "followers +6% · leadership +6")
        }
        out += if (lg.pro) Goal("allstar", 1.0, "Make the All-Star team", "followers +15% · leadership +3")
        else Goal("allconf", 1.0, "Make an all-conference or All-America team", "followers +12% · leadership +3")
        val t = lg.teams.getOrNull(h.teamId)
        val wins = if (t == null) 0 else ((t.history.lastOrNull()?.w ?: if (lg.pro) 41 else 17) + 3).coerceAtMost(if (lg.pro) 65 else 30)
        if (t != null) out += Goal("wins", wins.toDouble(), "Win $wins games with the ${if (lg.pro) t.name.substringAfterLast(' ') else t.mascot}", "teammates happier · leadership +6")
        return out
    }

    fun goalDue(lg: League) = active(lg) && lg.phase == Phase.REGULAR && (lg.hero?.stats?.gp ?: 1) == 0 && lg.careerGoalSeason != lg.season

    fun setGoal(lg: League, g: Goal) {
        lg.careerGoalSeason = lg.season
        lg.careerGoalKind = g.kind
        lg.careerGoalTarget = g.target
        lg.careerGoalLabel = g.label
    }

    /** Where he stands on this season's goal right now, for the card on Today. */
    fun progress(lg: League): String? {
        if (!active(lg) || lg.careerGoalSeason != lg.season || lg.careerGoalKind.isEmpty()) return null
        val h = lg.hero ?: return null
        val s = h.stats
        val t = lg.teams.getOrNull(h.teamId)
        val now = when (lg.careerGoalKind) {
            "ppg" -> "${f1(s.ppg)} so far"
            "rpg" -> "${f1(s.rpg)} so far"
            "apg" -> "${f1(s.apg)} so far"
            "wins" -> "${t?.w ?: 0} wins so far"
            else -> "decided at season's end"
        }
        return "${lg.careerGoalLabel}: $now"
    }

    /** How far along he is, 0..1, for goals with a number to chase (null for award goals). */
    fun fraction(lg: League): Float? {
        if (!active(lg) || lg.careerGoalSeason != lg.season || lg.careerGoalTarget <= 0) return null
        val h = lg.hero ?: return null
        val now = when (lg.careerGoalKind) {
            "ppg" -> h.stats.ppg; "rpg" -> h.stats.rpg; "apg" -> h.stats.apg
            "wins" -> (lg.teams.getOrNull(h.teamId)?.w ?: 0).toDouble()
            else -> return null
        }
        return (now / lg.careerGoalTarget).toFloat().coerceIn(0f, 1f)
    }

    private fun gradeGoal(lg: League) {
        if (lg.careerGoalSeason != lg.season || lg.careerGoalKind.isEmpty() || lg.careerGoalGraded == lg.season) return
        lg.careerGoalGraded = lg.season
        val h = lg.hero ?: return
        val r = lg.road ?: return
        val s = h.stats
        val mine = h.awards.filter { it.startsWith("${lg.season} ") }.map { it.removePrefix("${lg.season} ") }
        val hit = when (lg.careerGoalKind) {
            "ppg" -> s.ppg >= lg.careerGoalTarget
            "rpg" -> s.rpg >= lg.careerGoalTarget
            "apg" -> s.apg >= lg.careerGoalTarget
            "allstar" -> mine.any { it.startsWith("All-Star") }
            "allconf" -> mine.any { it.startsWith("All-") && it != "All-Tourn" }
            "wins" -> (lg.teams.getOrNull(h.teamId)?.w ?: 0) >= lg.careerGoalTarget
            else -> false
        }
        if (hit) {
            val pct = when (lg.careerGoalKind) { "allstar" -> 0.15; "allconf" -> 0.12; "ppg" -> 0.10; else -> 0.06 }
            r.followers = (r.followers * (1 + pct)).roundToInt()
            r.leadership = (r.leadership + if (lg.careerGoalKind == "wins") 6 else 4).coerceAtMost(100)
            if (lg.careerGoalKind == "wins") lg.teams.getOrNull(h.teamId)?.let { t -> for (p in lg.rosterOf(t)) if (p.id != h.id) p.happiness = (p.happiness + 4).coerceAtMost(100) }
            Alerts.add(lg, "Goal reached", "${lg.careerGoalLabel}: done.", "award")
            lg.news += News(lg.season, lg.day, "${h.name} hits his goal: ${lg.careerGoalLabel.lowercase()}", true, kind = "column", teamId = h.teamId, pid = h.id,
                body = "He set it before the season. He got there.", source = "Hoops Wire")
        } else {
            r.leadership = (r.leadership - 1).coerceAtLeast(0)
            lg.news += News(lg.season, lg.day, "${h.name} falls short of his goal", true, kind = "column", teamId = h.teamId, pid = h.id,
                body = "\"${lg.careerGoalLabel}.\" Not this year. He says next year.", source = "Hoops Wire")
        }
    }

    // ---------- legacy ----------

    class Tier(val min: Int, val name: String, val blurb: String)

    val TIERS = listOf(
        Tier(0, "Prospect", "Everything is still ahead of him."),
        Tier(15, "Rotation player", "A real player at this level."),
        Tier(40, "Fan favorite", "The jersey people buy."),
        Tier(80, "All-Star caliber", "One of the best in the league."),
        Tier(140, "Franchise legend", "His number goes in the rafters someday."),
        Tier(220, "Hall of Famer", "Springfield is waiting."),
        Tier(320, "All-time great", "Top-25 all time, and climbing."),
        Tier(450, "The GOAT conversation", "Say his name with the very best who ever played."),
    )

    /** Points for one award (the part after the season). */
    private fun awardPoints(a: String): Int = when {
        a == "MVP" -> 30
        a == "Finals MVP" -> 20
        a == "Champion" -> 18
        a == "Defensive Player of the Year" -> 12
        a == "All-NBA 1st" -> 10
        a == "All-NBA 2nd" -> 7
        a == "All-NBA 3rd" -> 5
        a == "Rookie of the Year" -> 6
        a == "Sixth Man of the Year" -> 5
        a == "Most Improved Player" -> 4
        a == "Clutch Player of the Year" -> 3
        a == "All-Star starter" -> 1
        a == "All-Star" -> 5
        a == "All-Star MVP" -> 2
        a.startsWith("All-Defensive") -> 3
        a == "NBA Cup MVP" -> 3
        a == "Nat MVP" -> 12
        a == "MOP" -> 8
        a == "DPOY" -> 4
        a == "FOY" -> 3
        a.startsWith("All-American") || a.startsWith("All-America") -> 4
        a == "All-Tourn" -> 2
        a.startsWith("Draft #") -> a.removePrefix("Draft #").toIntOrNull()?.let { if (it <= 3) 4 else if (it <= 14) 2 else 1 } ?: 0
        a.contains("Gold") -> 4
        else -> 0
    }

    /** His legacy score: awards plus production (pro points and college points, lightly). */
    fun score(lg: League, h: Player): Int {
        val fromAwards = h.awards.sumOf { awardPoints(it.substringAfter(' ')) }
        val proPts = h.career.filter { it.cls == PRO_YEAR }.sumOf { it.s.pts } + (if (lg.pro && h.career.none { it.season == lg.season && it.cls == PRO_YEAR }) h.stats.pts else 0)
        val colPts = h.career.filter { it.cls != PRO_YEAR }.sumOf { it.s.pts } + (if (!lg.pro && h.career.none { it.season == lg.season }) h.stats.pts else 0)
        return fromAwards + proPts / 170 + colPts / 400
    }

    fun tierOf(score: Int): Int = TIERS.indexOfLast { score >= it.min }.coerceAtLeast(0)

    private fun climb(lg: League) {
        val h = lg.hero ?: return
        val t = tierOf(score(lg, h))
        if (t <= lg.legacyTier) return
        lg.legacyTier = t
        val tier = TIERS[t]
        Alerts.add(lg, "Legacy: ${tier.name}", tier.blurb, "award")
        lg.news += News(lg.season, lg.day, "${h.name}'s legacy grows: ${tier.name.lowercase()}", true, kind = "column", teamId = h.teamId, pid = h.id,
            body = "${tier.blurb} The résumé so far: ${h.awards.size} honors and counting.", source = "Hoops Wire")
    }

    /** At season's end: grade the goal, then see if the legacy climbed. */
    fun seasonEnd(lg: League) {
        if (!active(lg)) return
        runCatching { gradeGoal(lg) }
        runCatching { climb(lg) }
    }
}
