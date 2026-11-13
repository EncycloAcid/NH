package com.nh.hoopscoach.game

/**
 * Academic and medical redshirts.
 *
 * Academic: coach sits a player for a full season before he plays a game. He keeps developing in
 * practice, stays off the depth chart, and does not burn a year of eligibility.
 *
 * Medical: after a season of four games or fewer, the coach can claim a hardship year so the class
 * clock does not advance. One academic and one medical per career.
 */
object Redshirts {
    const val MEDICAL_MAX_GAMES = 4

    fun sitting(p: Player) = p.redshirt == Redshirt.ACADEMIC

    fun eligible(lg: League, p: Player): List<Redshirt> {
        if (p.teamId != lg.userTeamId && lg.phase != Phase.OFFSEASON) {
            // AI uses its own planner; this list is for the user's controls.
        }
        val out = mutableListOf<Redshirt>()
        if (canAcademic(lg, p)) out += Redshirt.ACADEMIC
        if (canMedical(lg, p)) out += Redshirt.MEDICAL
        return out
    }

    fun canAcademic(lg: League, p: Player): Boolean {
        if (p.usedAcademicRs || p.redshirt == Redshirt.ACADEMIC) return false
        if (p.year !in 1..3) return false
        if (p.stats.gp > 0 || p.tourney.gp > 0) return false
        return lg.phase == Phase.REGULAR || lg.phase == Phase.CONF_TOURNEY || lg.phase == Phase.NCAA
    }

    fun canMedical(lg: League, p: Player): Boolean {
        if (p.usedMedicalRs || p.redshirt != Redshirt.NONE) return false
        if (p.stats.gp > MEDICAL_MAX_GAMES) return false
        return lg.phase == Phase.WRAP_UP || (lg.phase == Phase.OFFSEASON && lg.offseason?.step == OffStep.ROSTER)
    }

    fun blurb(p: Player): String = when (p.redshirt) {
        Redshirt.ACADEMIC -> "Sitting out this season. He develops in practice and keeps this year of eligibility."
        Redshirt.MEDICAL -> "Medical hardship claimed. His class year will not advance."
        Redshirt.NONE -> when {
            p.rsClass -> "Has used a redshirt year earlier in his career."
            else -> "Four seasons of eligibility. A redshirt stretches the clock."
        }
    }

    /** Designate or clear an academic redshirt. Pulls him out of the rotation. */
    fun setAcademic(lg: League, pid: Int, on: Boolean): Boolean {
        val p = lg.playerOrNull(pid) ?: return false
        if (p.teamId != lg.userTeamId) return false
        if (on) {
            if (!canAcademic(lg, p)) return false
            p.redshirt = Redshirt.ACADEMIC
            p.rsClass = true
            pullFromRotation(lg, lg.team(p.teamId), pid)
        } else {
            if (p.redshirt != Redshirt.ACADEMIC) return false
            if (p.stats.gp > 0 || p.tourney.gp > 0) return false
            p.redshirt = Redshirt.NONE
            // rsClass stays: he was planned as a redshirt prospect; clear only if never used.
            if (!p.usedAcademicRs && !p.usedMedicalRs) p.rsClass = false
            val t = lg.team(p.teamId)
            if (t.plan.auto) Gameplan.autoAllocate(lg, t) else Gameplan.validate(lg, t)
        }
        return true
    }

    /** Claim a medical hardship after a short season. Must happen before Draft Night. */
    fun setMedical(lg: League, pid: Int): Boolean {
        val p = lg.playerOrNull(pid) ?: return false
        if (p.teamId != lg.userTeamId) return false
        if (!canMedical(lg, p)) return false
        p.redshirt = Redshirt.MEDICAL
        p.usedMedicalRs = true
        p.rsClass = true
        lg.offseason?.graduates?.removeAll { it.pid == pid }
        lg.news += News(lg.season, lg.day, "${p.name} is granted a medical redshirt", p.teamId == lg.userTeamId)
        return true
    }

    fun pullFromRotation(lg: League, t: Team, pid: Int) {
        var touched = false
        for (s in t.plan.slots) {
            if (s.starter == pid) { s.starter = s.reserve; s.reserve = -1; s.starterMin = 40; touched = true }
            if (s.reserve == pid) { s.reserve = -1; s.starterMin = 40; touched = true }
        }
        if (touched) {
            t.plan.auto = false
            Gameplan.validate(lg, t)
            if (t.plan.slots.any { it.starter == -1 } || t.plan.slots.count { it.reserve == -1 } >= 3) {
                Gameplan.autoAllocate(lg, t)
            }
        }
    }

    /** Active players available for the depth chart and live games. */
    fun available(lg: League, t: Team) = lg.rosterOf(t).filter { !sitting(it) }

    /**
     * End-of-season clock: redshirted players keep their class year; everyone else advances.
     * Clears the active redshirt flag after the year is preserved.
     */
    fun advanceYear(p: Player): Boolean {
        val keep = p.redshirt != Redshirt.NONE
        if (p.redshirt == Redshirt.ACADEMIC) p.usedAcademicRs = true
        if (p.redshirt == Redshirt.MEDICAL) p.usedMedicalRs = true
        if (keep) p.rsClass = true
        p.redshirt = Redshirt.NONE
        if (!keep) p.year++
        return keep
    }

    /** AI: sit crowded, high-upside underclassmen before the season tip. */
    fun aiPlan(lg: League) {
        for (t in lg.teams) {
            if (t.id == lg.userTeamId) continue
            val roster = lg.rosterOf(t)
            for (pos in Pos.entries) {
                val group = roster.filter { it.pos == pos }.sortedByDescending { it.ovr }
                if (group.size < 3) continue
                val aheadFloor = group[1].ovr
                for (p in group.drop(2)) {
                    if (p.usedAcademicRs || p.redshirt != Redshirt.NONE) continue
                    if (p.year !in 1..2) continue
                    if (p.stats.gp > 0) continue
                    val upside = p.pot - p.ovr
                    if (upside < 10) continue
                    if (p.ovr + 4 >= aheadFloor) continue
                    p.redshirt = Redshirt.ACADEMIC
                    p.rsClass = true
                }
            }
            Gameplan.autoAllocate(lg, t)
        }
    }

    /** AI: claim medical hardships for buried or injured-short seasons before graduation. */
    fun aiFinishSeason(lg: League) {
        for (t in lg.teams) {
            if (t.id == lg.userTeamId) continue
            for (p in lg.rosterOf(t)) {
                if (p.usedMedicalRs || p.redshirt != Redshirt.NONE) continue
                if (p.stats.gp > MEDICAL_MAX_GAMES) continue
                val upside = p.pot - p.ovr
                val worth = upside >= 8 || p.ovr >= 72 || (p.year >= 4 && p.ovr >= 68)
                if (!worth) continue
                p.redshirt = Redshirt.MEDICAL
                p.usedMedicalRs = true
                p.rsClass = true
            }
        }
    }

    /** Recruiting pitch: a redshirt year behind a crowded chart for high-upside kids. */
    @Suppress("UNUSED_PARAMETER")
    fun developFactor(lg: League, t: Team, r: Recruit, ahead: Int): Recruiting.Factor? {
        val gap = r.p.pot - r.p.ovr
        if (gap < 8 || ahead < 1) return null
        var v = when (ahead) {
            1 -> 1.06
            2 -> 1.12
            else -> 1.16
        }
        // Minutes-first kids hate sitting; development-minded kids (big pot gap) lean in.
        if (r.pref == RecruitPref.MINUTES) v = 0.84
        else if (gap >= 14) v += 0.04
        val note = when {
            r.pref == RecruitPref.MINUTES -> "He wants minutes now, not a redshirt year"
            ahead == 1 -> "Redshirt available: one ${r.p.pos} ahead of him"
            else -> "Redshirt available: time to develop behind $ahead ${r.p.pos}s"
        }
        return Recruiting.Factor("Time to develop", v, note)
    }
}
