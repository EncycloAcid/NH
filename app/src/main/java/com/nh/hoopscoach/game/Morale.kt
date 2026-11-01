package com.nh.hoopscoach.game

import kotlin.math.roundToInt

enum class Mood(val label: String) { HAPPY("Happy"), CONTENT("Content"), UNHAPPY("Unhappy"), TRANSFER("Considering Transfer") }

object Morale {


    fun nilSpent(lg: League, t: Team) = t.roster.sumOf { lg.player(it).nil }

    fun mood(p: Player): Mood = when {
        p.happiness >= 70 -> Mood.HAPPY
        p.happiness >= 50 -> Mood.CONTENT
        p.happiness >= 35 -> Mood.UNHAPPY
        else -> Mood.TRANSFER
    }

    fun setInitialNil(lg: League, t: Team) {
        val players = lg.rosterOf(t)
        for (p in players) p.nil = (Ratings.expectedNil(p) * (0.96 + Rng.d() * 0.14)).roundToInt() / 5 * 5
        val cap = Finance.budget(lg, t) * 0.92
        val total = players.sumOf { it.nil }
        if (total > cap) for (p in players) p.nil = (p.nil * cap / total).roundToInt() / 5 * 5
    }

    fun expectedMinutes(lg: League, t: Team, p: Player): Int {
        val rank = lg.rosterOf(t).sortedByDescending { it.ovr }.indexOf(p)
        return when {
            rank < 5 -> 28
            rank < 8 -> 16
            rank < 10 -> 7
            else -> 2
        }
    }

    /** Why a player is unhappy, for the Attention list. */
    fun reason(lg: League, t: Team, p: Player): String {
        val mins = Gameplan.plannedMinutes(t, p.id)
        val exp = expectedMinutes(lg, t, p)
        val nilGap = Ratings.expectedNil(p) - p.nil
        return when {
            p.year == 4 && p.happiness < 50 && mins < exp -> "Final-season morale slipping."
            mins + 6 < exp && p.has(Trait.AMBITIOUS) -> "Ambitious player looking for a bigger role."
            mins + 6 < exp -> "Wants more minutes."
            nilGap > 30 && p.has(Trait.BAG_CHASER) -> "Feels underpaid for his production."
            nilGap > 30 -> "NIL deal is below his market value."
            t.winPct < 0.4 && t.gp > 5 -> "Frustrated with losing."
            else -> "Mood is slipping."
        }
    }

    fun target(lg: League, t: Team, p: Player): Double {
        val mins = Gameplan.plannedMinutes(t, p.id)
        val exp = expectedMinutes(lg, t, p)
        var role = ((mins - exp) * 1.3).coerceIn(-32.0, 10.0)
        if (role < 0 && p.has(Trait.AMBITIOUS)) role *= 1.5
        if (role < 0 && p.has(Trait.TEAM_PLAYER)) role *= 0.5
        val expNil = Ratings.expectedNil(p).coerceAtLeast(5)
        var nil = ((p.nil - expNil).toDouble() / expNil * 35).coerceIn(-25.0, 10.0)
        if (nil < 0 && p.has(Trait.BAG_CHASER)) nil *= 1.8
        val win = if (t.gp >= 4) (t.winPct - 0.5) * 24 else 0.0
        var v = 68 + role + nil + win
        if (p.has(Trait.LOYAL)) v += 10
        if (p.has(Trait.DISLOYAL)) v -= 8
        if (lg.coachOf(t).has(CoachSkill.CULTURE)) v += 5
        return v.coerceIn(0.0, 100.0)
    }

    /** Daily drift toward the target mood. */
    fun tick(lg: League) {
        for (t in lg.teams) for (pid in t.roster) {
            val p = lg.player(pid)
            if (p.year == 0) continue
            val tg = target(lg, t, p)
            p.happiness = (p.happiness + (tg - p.happiness) * 0.12).roundToInt().coerceIn(0, 100)
        }
    }

    /** AI programs keep their players paid close to market. */
    fun aiNilRefresh(lg: League, t: Team) {
        if (lg.humanCoach(t)) return
        setInitialNil(lg, t)
        // In player mode the user's own NIL comes from the deal he signed.
        if (lg.playerMode) Road.applyDeal(lg)
    }
}
