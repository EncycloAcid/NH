package com.nh.hoopscoach.game

import kotlin.math.roundToInt

class PreviewTrackRow(val teamId: Int, val preseason: String, val current: String, val onTrack: Boolean?)
class PreviewCallTrack(val text: String, val status: String, val evidence: String)
class PreviewTrack(val teams: List<PreviewTrackRow>, val calls: List<PreviewCallTrack>)

object PreviewTracker {
    fun college(lg: League, pv: Preview): PreviewTrack {
        val ids = (pv.proj.entries.sortedBy { it.value.nat }.take(25).map { it.key } + pv.finalFour +
            pv.calls.map { it.team }.filter { pv.proj.containsKey(it) } + lg.userTeamId).distinct()
        return PreviewTrack(ids.mapNotNull { id -> pv.proj[id]?.let { p ->
            row(lg, id, p.w, p.l, "No. ${p.nat}")
        } }, pv.bold.mapIndexed { i, text -> call(lg, text, pv.calls.getOrNull(i)) })
    }

    fun pro(lg: League, pv: NbaPreview): PreviewTrack {
        val ranked = pv.proj.entries.sortedByDescending { it.value.w }.map { it.key }
        val ids = (ranked.take(25) + pv.calls.map { it.team }.filter { pv.proj.containsKey(it) } + lg.userTeamId).distinct()
        return PreviewTrack(ids.mapNotNull { id -> pv.proj[id]?.let { p ->
            row(lg, id, p.w, p.l, "No. ${ranked.indexOf(id) + 1}")
        } }, pv.bold.mapIndexed { i, text -> call(lg, text, pv.calls.getOrNull(i)) })
    }

    private fun row(lg: League, id: Int, w: Int, l: Int, rank: String): PreviewTrackRow {
        val t = lg.team(id)
        val now = if (!lg.pro) {
            if (t.ap > 0) "AP No. ${t.ap}" else if (t.net > 0) "NET No. ${t.net}" else "Unranked"
        } else "No. ${liveOrder(lg).indexOf(t) + 1}"
        val expected = w.toDouble() * t.gp / (w + l).coerceAtLeast(1)
        return PreviewTrackRow(id, "$w-$l · $rank", "${t.record} · $now",
            if (t.gp < 5) null else t.w >= expected - 1.0)
    }

    private fun liveOrder(lg: League) = lg.teams.sortedWith(compareByDescending<Team> {
        if (it.gp > 0) it.w.toDouble() / it.gp else 0.0
    }.thenByDescending { it.elo })

    private fun call(lg: League, text: String, c: Call?): PreviewCallTrack {
        fun result(ok: Boolean?, evidence: String) = PreviewCallTrack(text,
            if (ok == null) "Awaiting evidence" else if (ok) "On track" else "Off track", evidence)
        if (c == null) return result(null, "This older issue has no checkable target.")
        if (lg.phase == Phase.WRAP_UP || lg.phase == Phase.OFFSEASON) {
            val (grade, outcome) = Receipts.check(lg, c)
            return result(if (grade < 0) null else grade == 1, outcome)
        }
        val t = lg.teams.firstOrNull { it.id == c.team }
        val p = lg.playerOrNull(c.pid)
        val order = if (lg.pro) liveOrder(lg) else lg.teams.sortedWith(compareBy<Team> {
            it.net.takeIf { n -> n > 0 } ?: Int.MAX_VALUE
        }.thenByDescending { it.elo })
        val rank = t?.let { order.indexOf(it) + 1 } ?: Int.MAX_VALUE
        val rankText = t?.let { "${it.record}, ${if (lg.pro) "league" else "NET"} position $rank; provisional, not a final grade." }.orEmpty()
        if (c.kind in setOf("C_SCORING", "SCORING_TITLE", "PPG", "CAREER_PPG", "AWARD", "C_AWARD", "HONOR")) {
            if (p == null || p.stats.gp < 3) return result(null, "Waiting for three player appearances.")
            val scoring = lg.players.filter { it.teamId >= 0 && it.stats.gp >= 3 }.sortedByDescending { it.stats.ppg }
            val place = scoring.indexOf(p) + 1
            val ok = when (c.kind) {
                "PPG" -> p.stats.ppg >= c.n
                "CAREER_PPG" -> p.stats.ppg * 10 > c.n
                "C_SCORING" -> place == 1 && p.stats.ppg > c.n
                "SCORING_TITLE" -> place == 1
                else -> null
            }
            return result(ok, "${p.name}: ${"%.1f".format(java.util.Locale.US, p.stats.ppg)} PPG in ${p.stats.gp} games; awards await voting.")
        }
        if (c.kind == "C_BIDS") {
            val conf = lg.confs.firstOrNull { it.id == c.team } ?: return result(null, "Conference unavailable.")
            val count = conf.teamIds.count { id -> order.take(68).any { it.id == id } }
            return result(count >= c.n, "$count teams in the current NET top 68; automatic bids and selection remain unsettled.")
        }
        if (t == null || t.gp < 5) return result(null, "Waiting for five team games.")
        val scheduled = lg.games.count { it.involves(t.id) && PreviewAvailability.regular(it) }
        val pace = t.w.toDouble() / t.gp * scheduled
        val confOrder = lg.teams.filter { it.confId == t.confId }.sortedWith(compareByDescending<Team> {
            if (lg.pro) { if (it.gp > 0) it.w.toDouble() / it.gp else 0.0 }
            else if (it.cw + it.cl > 0) it.cw.toDouble() / (it.cw + it.cl) else 0.0
        }.thenByDescending { it.elo })
        val confRank = confOrder.indexOf(t) + 1
        val ok = when (c.kind) {
            "OVER_W" -> pace >= c.n
            "UNDER_W" -> pace < c.n
            "C_NET" -> rank <= c.n
            "C_CONF_BEAT" -> confRank < c.n
            "C_CONF_500" -> if (t.cw + t.cl == 0) return result(null, "Conference games have not started.") else t.cw > t.cl
            "C_NO_CONF" -> confRank > 1
            "C_CONF_BY" -> {
                val next = confOrder.filter { it.id != t.id }.maxOfOrNull { it.cw - it.cl } ?: 0
                t.cw - t.cl - next >= c.n * 2
            }
            "C_NCAA" -> rank <= 68 || confRank == 1
            "C_F4" -> rank <= 4
            "C_NO_S16" -> rank > 16
            "C_FIRST_WEEKEND" -> rank in 17..68
            "C_NCAA_WINS" -> rank <= (64 / (1 shl c.n.coerceIn(0, 6))).coerceAtLeast(1)
            "PLAYOFFS" -> confRank <= 8
            "MISS_PLAYOFFS" -> confRank > 8
            "CONF_FINALS" -> confRank <= 2
            "NO_FINALS" -> confRank > 1
            "FIRST_ROUND_EXIT" -> confRank in 5..8
            else -> return result(null, "This call needs postseason results or award voting.")
        }
        val evidence = if (c.kind == "OVER_W" || c.kind == "UNDER_W") "${t.record}: ${pace.roundToInt()}-win pace against a ${c.n}-win target." else rankText
        return result(ok, evidence)
    }
}
