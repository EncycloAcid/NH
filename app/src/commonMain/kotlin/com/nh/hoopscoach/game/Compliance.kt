package com.nh.hoopscoach.game

import kotlin.math.roundToInt

/**
 * The user's program and the NCAA. Paying players and recruits above their market value (an NIL offer
 * over a recruit's ask, which then becomes his salary for as long as he's there) draws the compliance
 * office's attention. Its checks turn into warnings; ignoring them raises the odds that the program is
 * investigated in the offseason, with the same sanctions any school gets (see [WorldEvents]) and a hot
 * seat for the coach. Self-reporting costs a scholarship but clears the warnings and most of the risk.
 */
object Compliance {
    /** Levels, from paying market value to a program the NCAA will come looking at. */
    enum class Level(val label: String, val note: String) {
        CLEAN("Clean", "You pay players and recruits about what they're worth."),
        WATCHED("Watched", "Some deals run above market. The compliance office has noticed."),
        REVIEW("Under review", "Deals well above market. Every check now adds a warning."),
        RED("Red flags", "Pay far above market. An investigation is likely if nothing changes."),
    }

    fun active(lg: League) = !lg.pro && !lg.playerMode && lg.userTeamId >= 0

    /**
     * What the program pays (roster salaries, commits' promised NIL, open offers) over what those
     * players are worth on the market. 1.0 is market value.
     */
    fun overpay(lg: League): Double {
        if (!active(lg)) return 1.0
        val t = lg.user
        var paid = 0.0
        var worth = 0.0
        for (p in lg.rosterOf(t)) {
            val e = Ratings.expectedNil(p)
            if (e <= 0 || p.walkOn) continue
            paid += p.nil
            worth += e
        }
        for (r in lg.recruits) {
            if (r.nilOffer <= 0 || r.nilAsk <= 0) continue
            if (r.committed == t.id || r.committed < 0) {
                paid += r.nilOffer
                worth += r.nilAsk
            }
        }
        return if (worth <= 0) 1.0 else paid / worth
    }

    fun level(ratio: Double): Level = when {
        ratio < 1.15 -> Level.CLEAN
        ratio < 1.30 -> Level.WATCHED
        ratio < 1.50 -> Level.REVIEW
        else -> Level.RED
    }

    fun level(lg: League): Level = level(overpay(lg))

    /** An offer this far over a recruit's ask is a red flag on its own. */
    fun aboveMarket(r: Recruit): Boolean = r.nilAsk > 0 && r.nilOffer > r.nilAsk * 1.15

    /** Odds the program is investigated this offseason. */
    fun risk(lg: League): Double {
        if (!active(lg)) return 0.0
        val ratio = overpay(lg)
        if (ratio < 1.15 && lg.complianceWarnings == 0) return 0.0
        var p = (ratio - 1.15).coerceAtLeast(0.0) * 0.6 + lg.complianceWarnings * 0.06
        if (lg.userCoach.has(CoachSkill.CULTURE)) p *= 0.6
        if (lg.selfReported == lg.season) p *= 0.3
        return p.coerceIn(0.0, 0.75)
    }

    fun riskLabel(p: Double) = when {
        p <= 0.0 -> "None"
        p < 0.08 -> "Low"
        p < 0.2 -> "Real"
        p < 0.4 -> "High"
        else -> "Severe"
    }

    /**
     * A compliance check: weekly in season, and at each recruiting round. Under review or worse adds a
     * warning (with an alert saying why); a clean check takes one away.
     */
    fun check(lg: League) {
        if (!active(lg)) return
        val stamp = lg.season * 1000 + lg.day + if (lg.phase == Phase.OFFSEASON) 500 + (lg.offseason?.round ?: 0) else 0
        if (lg.complianceStamp == stamp) return
        lg.complianceStamp = stamp
        val ratio = overpay(lg)
        when (level(ratio)) {
            Level.CLEAN -> if (lg.complianceWarnings > 0) lg.complianceWarnings--
            Level.WATCHED -> {}
            Level.REVIEW, Level.RED -> {
                lg.complianceWarnings = (lg.complianceWarnings + 1).coerceAtMost(9)
                val pct = ((ratio - 1) * 100).roundToInt()
                Alerts.add(lg, "Compliance warning ${lg.complianceWarnings}",
                    "Your deals run $pct% above market value. " +
                        when {
                            lg.complianceWarnings >= 4 -> "The NCAA is asking questions. Cut offers back to market, or self-report from the Career page."
                            lg.complianceWarnings >= 2 -> "Warnings that pile up make an investigation likely this offseason."
                            else -> "Offers over a recruit's ask are what's drawing the attention."
                        }, "")
            }
        }
    }

    /** Self-report: a scholarship now, the warnings gone, and most of this year's risk with them. */
    fun selfReport(lg: League): Boolean {
        if (!active(lg) || lg.selfReported == lg.season) return false
        val t = lg.user
        t.scholarshipsLost = (if (t.sanctionUntil >= lg.season) t.scholarshipsLost else 0) + 1
        t.sanctionUntil = maxOf(t.sanctionUntil, lg.season + 1)
        lg.complianceWarnings = 0
        lg.selfReported = lg.season
        lg.userCoach.security = (lg.userCoach.security - 5).coerceAtLeast(0)
        Timeline.record(lg, "selfreport", "${t.school} self-reports NIL violations",
            "${t.school} reported its own NIL violations under ${lg.userCoach.name} and gave up a scholarship.", t.id, coach = lg.userCoach.name, weight = 2)
        lg.news += News(lg.season, lg.day, "${t.school} self-reports NIL violations, gives up a scholarship", true, kind = "move", teamId = t.id,
            body = "${lg.userCoach.name}'s program reported its own violations, a move that usually spares a school harsher penalties.", source = "Hoops Wire")
        return true
    }

    /** The offseason roll for the user's program. True if it was investigated. */
    fun offseason(lg: League): Boolean {
        if (!active(lg)) return false
        val p = risk(lg)
        if (p <= 0 || !Rng.chance(p)) return false
        val major = overpay(lg) >= 1.5 || lg.complianceWarnings >= 4 || Rng.chance(0.25)
        WorldEvents.scandal(lg, lg.user, lg.userCoach, major, what = "NIL payments above market value")
        lg.complianceWarnings = 0
        return true
    }
}
