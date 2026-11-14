package com.nh.hoopscoach.game

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/** An assistant coach: his specialty ("dev", "off", "def", "rec"), how good he is, and his years on the staff. */
@Serializable
class Assistant(val id: Int, val first: String, val last: String, var age: Int, val skill: String, var rating: Int, var seasons: Int = 0) {
    val name get() = "$first $last"
}

/** A former assistant who became a head coach: where, and the coach record that tracks him. */
@Serializable
class TreeBranch(val name: String, val skill: String, val seasonsWithYou: Int, val left: Int, val teamName: String, val coachId: Int)

/**
 * The coaching staff and the coaching tree. The user's head coach hires up to three assistants, each
 * with a specialty: player development, offense, defense or (in college) recruiting, and each worth up
 * to ten points of it. Good ones get better, and the best get hired away as head coaches; the coaching
 * tree follows them.
 */
object Assistants {
    const val SLOTS = 3
    val SKILLS = listOf("dev" to "Player development", "off" to "Offense", "def" to "Defense", "rec" to "Recruiting")
    fun skillName(s: String) = SKILLS.firstOrNull { it.first == s }?.second ?: s

    private fun mine(lg: League, t: Team) = lg.road == null && t.id == lg.userTeamId

    /** Points an assistant adds to his specialty: up to 10 for a 90. */
    private fun points(a: Assistant) = ((a.rating - 40) / 5.0).coerceIn(0.0, 10.0)

    fun bonus(lg: League, t: Team, skill: String): Double = if (!mine(lg, t)) 0.0 else lg.staff.filter { it.skill == skill }.sumOf { points(it) }

    /** Development points for [t]'s player growth. */
    fun dev(lg: League, t: Team): Int = bonus(lg, t, "dev").roundToInt()

    /** Offense and defense as a make-chance edge, like a coach rating. */
    fun off(lg: League, t: Team): Double = bonus(lg, t, "off") * 0.0006 * (if (lg.pro) 0.5 else 1.0)
    fun def(lg: League, t: Team): Double = bonus(lg, t, "def") * 0.0006 * (if (lg.pro) 0.5 else 1.0)

    fun rec(lg: League): Int = if (lg.road != null || lg.pro) 0 else bonus(lg, lg.user, "rec").roundToInt()

    private fun make(lg: League, skill: String, rating: Int): Assistant =
        Assistant(lg.nextAssistantId++, Rng.pick(Names.first), Rng.pick(Names.last), Rng.range(29, 58), skill, rating.coerceIn(40, 92))

    /** This offseason's candidates: six, a mix of specialties, a couple of them very good. */
    fun pool(lg: League): List<Assistant> {
        if (lg.staffPoolSeason != lg.season || lg.staffPool.isEmpty()) {
            lg.staffPool.clear()
            val skills = SKILLS.map { it.first }.filter { lg.pro.not() || it != "rec" }
            repeat(6) { i -> lg.staffPool += make(lg, skills[i % skills.size], (55 + Rng.gauss() * 12).roundToInt() + if (i < 2) 10 else 0) }
            lg.staffPoolSeason = lg.season
        }
        return lg.staffPool
    }

    fun hire(lg: League, id: Int): String {
        if (lg.staff.size >= SLOTS) return "Your staff is full. Let someone go first."
        val a = lg.staffPool.firstOrNull { it.id == id } ?: return ""
        lg.staffPool.remove(a)
        lg.staff += a
        lg.news += News(lg.season, lg.day, "${lg.userCoach.name} adds ${a.name} to his staff (${skillName(a.skill).lowercase()})", true)
        return "${a.name} joins your staff."
    }

    fun release(lg: League, id: Int) {
        lg.staff.removeAll { it.id == id }
    }

    /**
     * Season's end: everyone on the staff gets a year better (the young ones more); a good assistant
     * with a couple of years in may be hired away as a head coach, and joins the coaching tree.
     */
    fun seasonEnd(lg: League) {
        if (lg.road != null) return
        for (a in lg.staff) {
            a.seasons++
            a.age++
            a.rating = (a.rating + if (a.age <= 40) Rng.range(1, 3) else Rng.range(0, 1)).coerceAtMost(95)
        }
        val leaving = lg.staff.filter { it.rating >= 68 && it.seasons >= 2 && Rng.chance((it.rating - 60) / 80.0) }.take(1)
        for (a in leaving) {
            val opening = lg.teams.filter { it.id != lg.userTeamId }.let { ts -> if (lg.pro) ts.sortedBy { it.winPct }.take(8) else ts.filter { it.prestige in 30..70 } }
                .randomOrNull(Rng.r) ?: continue
            val old = runCatching { lg.coachOf(opening) }.getOrNull()
            old?.teamId = -1
            val o = 50 + (if (a.skill == "off") a.rating - 50 else Rng.range(0, 15))
            val d = 50 + (if (a.skill == "def") a.rating - 50 else Rng.range(0, 15))
            val r = 50 + (if (a.skill == "rec") a.rating - 50 else Rng.range(0, 15))
            val v = 50 + (if (a.skill == "dev") a.rating - 50 else Rng.range(0, 15))
            val c = Coach(lg.nextCoachId++, a.first, a.last, a.age, opening.id, o.coerceIn(40, 95), d.coerceIn(40, 95), r.coerceIn(40, 95), v.coerceIn(40, 95), Rng.int(0, 1_000_000))
            lg.coaches += c
            opening.coachId = c.id
            GenerationalLegacy.inheritProgram(lg, opening, c, mentor = lg.userCoach.name)
            Tactics.autoAllocate(lg, opening)
            lg.staff.remove(a)
            val where = if (lg.pro) "the ${opening.name}" else opening.school
            lg.tree += TreeBranch(a.name, a.skill, a.seasons, lg.season, if (lg.pro) opening.name else opening.school, c.id)
            Timeline.record(lg, "poached", "${if (lg.pro) opening.name else opening.school} hires ${a.name} off ${lg.userCoach.name}'s staff",
                "${a.name} ran the ${skillName(a.skill).lowercase()} for ${lg.userCoach.name} for ${a.seasons} season${if (a.seasons == 1) "" else "s"} before taking the job.",
                opening.id, coach = a.name, weight = 2)
            lg.news += News(lg.season, lg.day, "${a.name}, an assistant on ${lg.userCoach.name}'s staff, is the new head coach of $where", true, -1, "",
                "${a.name} spent ${a.seasons} season${if (a.seasons == 1) "" else "s"} on ${lg.userCoach.last}'s bench, running the ${skillName(a.skill).lowercase()}. Another branch on the coaching tree.", "", opening.id)
            Alerts.add(lg, "Your assistant got a head job", "${a.name} leaves to coach $where. Your coaching tree grows.", "")
        }
    }

    /** A tree branch's record as a head coach, from his coach entry. */
    fun record(lg: League, b: TreeBranch): String {
        val c = lg.coaches.firstOrNull { it.id == b.coachId } ?: return ""
        val w = c.history.sumOf { it.w } + (lg.teams.getOrNull(c.teamId)?.takeIf { c.history.none { h -> h.season == lg.season } }?.w ?: 0)
        val l = c.history.sumOf { it.l } + (lg.teams.getOrNull(c.teamId)?.takeIf { c.history.none { h -> h.season == lg.season } }?.l ?: 0)
        val titles = c.history.count { it.result == "Champion" || it.result == "National Champion" }
        val now = lg.teams.getOrNull(c.teamId)?.let { if (lg.pro) it.name else it.school } ?: "out of coaching"
        return "$w-$l" + (if (titles > 0) " · $titles title${if (titles == 1) "" else "s"}" else "") + " · now: $now"
    }
}

/**
 * Summer development plans: the coach points a player's summer at one part of his game. The plan's
 * ratings grow (more for the young), at the cost of a point somewhere else; conditioning keeps him
 * healthier instead (fewer injuries, a slower decline).
 */
object DevPlans {
    val PLANS = listOf("" to "No plan", "shooting" to "Shooting", "playmaking" to "Playmaking", "inside" to "Inside", "defense" to "Defense", "conditioning" to "Conditioning")
    fun label(p: String) = PLANS.firstOrNull { it.first == p }?.second ?: "No plan"
    fun blurb(p: String) = when (p) {
        "shooting" -> "Mid-range and threes"
        "playmaking" -> "Handling and passing"
        "inside" -> "Finishing and rebounding"
        "defense" -> "Perimeter, interior, steals and blocks"
        "conditioning" -> "Fewer injuries, a slower decline"
        else -> "Normal summer"
    }
    private fun attrs(p: String): List<Attr> = when (p) {
        "shooting" -> listOf(Attr.MID, Attr.OUT)
        "playmaking" -> listOf(Attr.HND, Attr.PAS)
        "inside" -> listOf(Attr.INS, Attr.REB)
        "defense" -> listOf(Attr.PER, Attr.IDF, Attr.STL, Attr.BLK)
        else -> emptyList()
    }

    fun set(lg: League, pid: Int, plan: String) { lg.playerOrNull(pid)?.devPlan = plan }

    /** After his summer growth: the plan's extra work. Returns the points it moved. */
    fun apply(lg: League, t: Team, p: Player): Int {
        if (lg.road != null || t.id != lg.userTeamId) return 0
        val a = attrs(p.devPlan)
        if (a.isEmpty()) return 0
        val n = (if (p.age <= 23) 3 else if (p.age <= 27) 2 else 1) + if (Assistants.dev(lg, t) >= 6) 1 else 0
        var moved = 0
        repeat(n) { if (Ratings.raise(p, Rng.pick(a).ordinal)) moved++ }
        // The trade-off: a point somewhere he didn't work on.
        if (n >= 2) {
            val others = Attr.entries.filter { it !in a && p.attrs[it.ordinal] > 30 }
            if (others.isNotEmpty()) p.attrs[Rng.pick(others).ordinal]--
        }
        return moved
    }

    /** Conditioning: an injury multiplier and a decline multiplier. */
    fun injuryFactor(p: Player) = if (p.devPlan == "conditioning") 0.75 else 1.0
    fun declineFactor(p: Player) = if (p.devPlan == "conditioning") 0.85 else 1.0

    /** A plan that paid off: news for a summer jump of 4 or more. */
    fun report(lg: League, t: Team, p: Player, before: Int) {
        if (p.devPlan.isEmpty() || t.id != lg.userTeamId || p.devPlan == "conditioning") return
        val d = p.ovr - before
        if (d >= 4) lg.news += News(lg.season, lg.day, "${p.name}'s summer of ${label(p.devPlan).lowercase()} work pays off (+$d)", true, -1, "",
            "${p.name} spent the summer on ${blurb(p.devPlan).lowercase()} and comes into camp ${d} points better.", "", t.id, p.id)
    }
}
