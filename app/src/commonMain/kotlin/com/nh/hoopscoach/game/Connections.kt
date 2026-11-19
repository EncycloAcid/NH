package com.nh.hoopscoach.game

/** Who sits at the middle of the web, or at the other end of a link: a coach, a team, a player or an assistant. */
data class WebNode(val kind: Char, val id: Int) {
    companion object {
        const val COACH = 'C'
        const val TEAM = 'T'
        const val PLAYER = 'P'
        const val STAFF = 'S'
    }
}

/** The kinds of link, in the order the web lists them. */
enum class LinkKind(val label: String, val group: String) {
    COACHES("Head coach", "Program"),
    MENTOR("Mentor", "Coaching tree"),
    TREE("Coaching tree", "Coaching tree"),
    STAFF("On staff", "Coaching tree"),
    FORMER_COACH("Former head coach", "Program"),
    COACH_RIVAL("Coaching rival", "Rivals"),
    TEAM_RIVAL("Rival", "Rivals"),
    RECRUIT_RIVAL("Recruiting rival", "Rivals"),
    PLAYED_AT("Played there", "Program"),
    PLAYER("Former player", "Players"),
    LEGEND("Program legend", "Players"),
}

/**
 * One link in the web. [node] is where tapping it goes (null: nothing to open, like a mentor who has
 * left the game). [strength] (1-10) sets how heavy the line is: a bitter rivalry, a long tenure, a star.
 */
class WebLink(val kind: LinkKind, val node: WebNode?, val title: String, val detail: String, val strength: Int)

/**
 * The relationship web: rivals, mentors, coaching trees and former players around a coach or a team,
 * built from what the league already records (coach histories and mentors, the user's coaching tree and
 * staff, coaching rivalries, school rivalries, and former players' career lines).
 */
object Connections {
    private fun teamName(lg: League, t: Team) = if (lg.pro) t.name else t.school

    /** Seasons and teams a coach has been head coach of, this season included. */
    private fun stints(lg: League, c: Coach): Set<Pair<Int, Int>> {
        val out = c.history.map { it.season to it.teamId }.toMutableSet()
        if (c.teamId >= 0) out += lg.season to c.teamId
        return out
    }

    fun title(lg: League, n: WebNode): String = when (n.kind) {
        WebNode.COACH -> lg.coaches.firstOrNull { it.id == n.id }?.name ?: "Coach"
        WebNode.TEAM -> lg.teams.getOrNull(n.id)?.let { teamName(lg, it) } ?: "Team"
        WebNode.PLAYER -> lg.anyPlayer(n.id)?.name ?: "Player"
        else -> "Connections"
    }

    fun linksFor(lg: League, n: WebNode): List<WebLink> = when (n.kind) {
        WebNode.COACH -> lg.coaches.firstOrNull { it.id == n.id }?.let { forCoach(lg, it) } ?: emptyList()
        WebNode.TEAM -> lg.teams.getOrNull(n.id)?.let { forTeam(lg, it) } ?: emptyList()
        else -> emptyList()
    }

    private fun record(c: Coach, teamId: Int): Pair<Int, Int> {
        val h = c.history.filter { it.teamId == teamId }
        return h.sumOf { it.w } to h.sumOf { it.l }
    }

    fun forCoach(lg: League, c: Coach): List<WebLink> {
        val out = mutableListOf<WebLink>()
        val isUser = c.id == lg.userCoachId

        // The program he runs.
        lg.teams.getOrNull(c.teamId)?.let { t ->
            val seasons = c.history.count { it.teamId == t.id } + 1
            out += WebLink(LinkKind.COACHES, WebNode(WebNode.TEAM, t.id), teamName(lg, t),
                "Head coach · ${if (seasons == 1) "first season" else "season $seasons"} · ${t.record} this year", 6)
        }
        // Schools he coached before.
        c.history.map { it.teamId }.distinct().filter { it != c.teamId }.forEach { id ->
            val t = lg.teams.getOrNull(id) ?: return@forEach
            val (w, l) = record(c, id)
            val years = c.history.filter { it.teamId == id }.map { it.season }
            out += WebLink(LinkKind.COACHES, WebNode(WebNode.TEAM, id), teamName(lg, t),
                "Coached here ${years.min()}${if (years.max() != years.min()) "-${years.max() % 100}" else ""} · $w-$l", 4)
        }

        // Where he came from: his mentor.
        if (c.mentor.isNotBlank()) {
            val m = lg.coaches.firstOrNull { it.name == c.mentor && it.id != c.id }
            out += WebLink(LinkKind.MENTOR, m?.let { WebNode(WebNode.COACH, it.id) }, c.mentor,
                m?.let { mc -> lg.teams.getOrNull(mc.teamId)?.let { "Coached him as an assistant · now at ${teamName(lg, it)}" } }
                    ?: "Coached him as an assistant", 7)
        }

        // Who came from him: assistants who became head coaches.
        val tree = mutableMapOf<Int, WebLink>()
        if (isUser) for (b in lg.tree) {
            val bc = lg.coaches.firstOrNull { it.id == b.coachId }
            val now = bc?.let { lg.teams.getOrNull(it.teamId) }?.let { "now at ${teamName(lg, it)}" } ?: "out of coaching"
            tree[b.coachId] = WebLink(LinkKind.TREE, bc?.let { WebNode(WebNode.COACH, it.id) }, b.name,
                "${b.seasonsWithYou} season${if (b.seasonsWithYou == 1) "" else "s"} on your staff · $now · ${bc?.let { "${it.w}-${it.l}" } ?: ""}".trimEnd(' ', '·'),
                (4 + b.seasonsWithYou).coerceAtMost(9))
        }
        lg.coaches.filter { it.mentor == c.name && it.id != c.id && it.id !in tree }.forEach { p ->
            val now = lg.teams.getOrNull(p.teamId)?.let { "now at ${teamName(lg, it)}" } ?: "out of coaching"
            tree[p.id] = WebLink(LinkKind.TREE, WebNode(WebNode.COACH, p.id), p.name, "Former assistant · $now · ${p.w}-${p.l}", 5)
        }
        out += tree.values

        // The staff beside him now.
        if (isUser) lg.staff.forEach { a ->
            out += WebLink(LinkKind.STAFF, WebNode(WebNode.STAFF, a.id), a.name,
                "${Assistants.skillName(a.skill)} · rated ${a.rating} · ${a.seasons} season${if (a.seasons == 1) "" else "s"} with you", 3)
        }

        // Coaching rivals.
        if (isUser) {
            CoachRivals.rivals(lg).forEach { r ->
                val rc = lg.coaches.firstOrNull { it.id == r.coachId } ?: return@forEach
                val at = lg.teams.getOrNull(rc.teamId)?.let { teamName(lg, it) } ?: "out of coaching"
                out += WebLink(LinkKind.COACH_RIVAL, WebNode(WebNode.COACH, rc.id), rc.name,
                    "${CoachRivals.heatLabel(r.heat)} · you're ${r.w}-${r.l} against him · $at", r.heat.coerceIn(1, 10))
            }
        } else CoachRivals.of(lg, c.id)?.takeIf { it.heat >= 3 }?.let { r ->
            val you = lg.userCoach
            out += WebLink(LinkKind.COACH_RIVAL, WebNode(WebNode.COACH, you.id), you.name,
                "${CoachRivals.heatLabel(r.heat)} · he's ${r.l}-${r.w} against you", r.heat.coerceIn(1, 10))
        }

        // Schools that keep beating him for recruits.
        if (isUser) RecruitRivals.rivals(lg).forEach { e ->
            val t = lg.teams.getOrNull(e.teamId) ?: return@forEach
            out += WebLink(LinkKind.RECRUIT_RIVAL, WebNode(WebNode.TEAM, t.id), teamName(lg, t),
                "Beat you for ${e.names.take(3).joinToString(", ")}" + if (e.won > 0) " · you've won ${e.won} back" else "", (4 + e.lost).coerceAtMost(9))
        }

        // Where he played.
        c.playedAt.forEach { school ->
            val t = lg.teams.firstOrNull { it.school == school || it.name == school }
            out += WebLink(LinkKind.PLAYED_AT, t?.let { WebNode(WebNode.TEAM, it.id) }, school, "Played there", 3)
        }

        // Players he coached who have moved on.
        val mine = stints(lg, c)
        // Older saves kept only the team's abbreviation on a season line.
        val byAbbr = lg.teams.associate { it.abbr to it.id }
        lg.alumni.filter { p -> p.career.any { (it.season to (it.teamId.takeIf { id -> id >= 0 } ?: byAbbr[it.team] ?: -1)) in mine } }
            .sortedByDescending { score(it) }.take(12).forEach { p ->
                out += WebLink(LinkKind.PLAYER, WebNode(WebNode.PLAYER, p.id), p.name,
                    p.left.ifBlank { "Played for him" } + peak(p), (score(p) / 12).coerceIn(1, 10))
            }
        return out
    }

    fun forTeam(lg: League, t: Team): List<WebLink> {
        val out = mutableListOf<WebLink>()
        runCatching { lg.coachOf(t) }.getOrNull()?.let { c ->
            val seasons = c.history.count { it.teamId == t.id } + 1
            out += WebLink(LinkKind.COACHES, WebNode(WebNode.COACH, c.id), c.name,
                "Head coach · ${if (seasons == 1) "first season" else "season $seasons"} · ${c.w}-${c.l} career", 7)
        }
        // Coaches who used to run it.
        lg.coaches.filter { it.id != t.coachId && it.history.any { h -> h.teamId == t.id } }
            .sortedByDescending { c -> c.history.filter { it.teamId == t.id }.maxOf { it.season } }.take(5).forEach { c ->
                val (w, l) = record(c, t.id)
                val years = c.history.filter { it.teamId == t.id }.map { it.season }
                val now = lg.teams.getOrNull(c.teamId)?.let { "now at ${teamName(lg, it)}" } ?: "out of coaching"
                out += WebLink(LinkKind.FORMER_COACH, WebNode(WebNode.COACH, c.id), c.name,
                    "${years.min()}-${years.max() % 100} · $w-$l · $now", 4)
            }
        // School rivalries.
        lg.rivalsOf(t.id).forEach { r ->
            val o = lg.teams.getOrNull(r.other(t.id)) ?: return@forEach
            val w = r.winsFor(t.id)
            val l = r.winsFor(o.id)
            out += WebLink(LinkKind.TEAM_RIVAL, WebNode(WebNode.TEAM, o.id), teamName(lg, o),
                "${r.title} · ${if (w + l == 0) "no meetings yet" else "series $w-$l"}", 8)
        }
        // Legends and former players.
        t.legends.filter { it.pid >= 0 && !it.coach }.sortedByDescending { it.score }.take(4).forEach { leg ->
            out += WebLink(LinkKind.LEGEND, WebNode(WebNode.PLAYER, leg.pid), leg.name, "${leg.years} · ${leg.line}", 8)
        }
        val legendIds = t.legends.map { it.pid }.toSet()
        lg.alumni.filter { it.lastTeam == t.id && it.id !in legendIds }.sortedByDescending { score(it) }.take(8).forEach { p ->
            out += WebLink(LinkKind.PLAYER, WebNode(WebNode.PLAYER, p.id), p.name, p.left.ifBlank { "Former player" } + peak(p), (score(p) / 12).coerceIn(1, 10))
        }
        return out
    }

    /** How much a former player stands out: his peak rating, honors and whether he was drafted. */
    private fun score(p: Player): Int =
        (p.career.maxOfOrNull { it.ovr } ?: p.ovr) + p.awards.size * 4 + if (p.left.startsWith("Drafted")) 15 else 0

    private fun peak(p: Player): String = p.career.maxOfOrNull { it.ovr }?.let { " · peaked at $it" } ?: ""

    /** The links that go on the web itself: a few of each kind first, then the strongest of the rest. */
    fun onWeb(links: List<WebLink>, max: Int = 10): List<WebLink> {
        val first = links.groupBy { it.kind }.values.flatMap { g -> g.sortedByDescending { it.strength }.take(2) }
            .sortedByDescending { it.strength }.take(max)
        val rest = links.filter { it !in first }.sortedByDescending { it.strength }.take(max - first.size)
        return (first + rest).sortedBy { it.kind.ordinal }
    }
}
