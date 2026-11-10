package com.nh.hoopscoach.game

/**
 * College basketball eras: the shot clock, the three-point line and how the game was played.
 * A dynasty that starts in a past season uses that era's rules, and as the years roll forward
 * the rules advance with real NCAA history (capped at today's game).
 */
enum class CollegeEra(
    /** First season this era applies (the year the season ends). */
    val from: Int,
    val label: String,
    val title: String,
    val blurb: String,
    /** Shot clock in seconds; 0 means no clock (stall-ball era). */
    val shotClock: Int,
    /**
     * Three-point volume vs modern college (1.0 = today). 0 means there is no three-point line:
     * long jumpers still happen, they just count as twos.
     */
    val threes: Double,
    /** Make-rate nudge for threes: shorter lines shoot better. */
    val threeMake: Double,
    /** Possession length scale (above 1 is slower). */
    val pace: Double,
    /** Non-shooting and shooting foul rate scale. */
    val fouls: Double,
    /** Team fouls in a half that start the one-and-one. */
    val oneAndOneFrom: Int,
    /** Team fouls in a half that become two shots. */
    val doubleBonusFrom: Int,
) {
    PRE_CLOCK(
        1900, "Pre-clock", "Stall ball",
        "No shot clock and no three-point line. Games crawl, big men dominate the paint, and late leads get held.",
        0, 0.0, 0.0, 1.28, 0.92, 7, 10,
    ),
    CLOCK_45(
        1986, "1986", "45-second clock",
        "The 45-second clock arrives. Still no national three: post-ups, midrange and fast breaks.",
        45, 0.0, 0.0, 1.14, 0.95, 7, 10,
    ),
    SHORT_THREE(
        1987, "1987–93", "Short three",
        "National three-point line at 19'9\" with the 45-second clock. Threes are rare but count.",
        45, 0.22, 0.04, 1.10, 0.97, 7, 10,
    ),
    CLASSIC(
        1994, "1994–07", "35-second classic",
        "35-second clock and the short three. Physical half-court basketball; midrange still matters.",
        35, 0.36, 0.035, 1.06, 1.0, 7, 10,
    ),
    MID(
        2008, "2008–14", "Longer three",
        "Three-point line moves to 20'9\". More spacing, still a 35-second clock.",
        35, 0.58, 0.01, 1.03, 1.0, 7, 10,
    ),
    THIRTY(
        2015, "2015–18", "30-second clock",
        "Shot clock drops to 30. The one-and-one is gone: two free throws from the seventh team foul. Pace up, threes keep climbing.",
        30, 0.80, 0.005, 1.01, 1.02, 99, 7,
    ),
    MODERN(
        2019, "Today", "Modern game",
        "International three-point line, 30-second clock, threes everywhere. Two free throws from the seventh team foul — no one-and-one.",
        30, 1.0, 0.0, 1.0, 1.0, 99, 7,
    );

    val hasThrees get() = threes > 0.0
    val hasShotClock get() = shotClock > 0
    /** Soft max possession length when there is no shot clock. */
    val maxPossLen get() = if (hasShotClock) shotClock.toDouble() else 55.0
    /** True when the one-and-one bonus is still in the rulebook (pre-2015). */
    val hasOneAndOne get() = oneAndOneFrom < doubleBonusFrom

    fun rulesSummary(): String = buildList {
        add(if (hasShotClock) "$shotClock-second shot clock" else "No shot clock")
        add(when {
            !hasThrees -> "No three-point line"
            this@CollegeEra == SHORT_THREE || this@CollegeEra == CLASSIC -> "19'9\" three-point line"
            this@CollegeEra == MID || this@CollegeEra == THIRTY -> "20'9\" three-point line"
            else -> "International three-point line"
        })
        add(
            if (hasOneAndOne) "One-and-one at $oneAndOneFrom · two shots at $doubleBonusFrom"
            else "Two free throws from the ${doubleBonusFrom}th team foul (no one-and-one)",
        )
        add("Five fouls to foul out")
    }.joinToString(" · ")
}

object EraRules {
    /** Every selectable starting era for a new career (newest last). */
    val pickable: List<CollegeEra> = CollegeEra.entries

    fun of(lg: League): CollegeEra = ofSeason(lg.season)

    fun ofSeason(season: Int): CollegeEra {
        var best = CollegeEra.PRE_CLOCK
        for (e in CollegeEra.entries) if (season >= e.from) best = e
        return best
    }

    /** Season year to start a new career in this era (first year of the era, or today for modern). */
    fun startYear(era: CollegeEra): Int = when (era) {
        CollegeEra.MODERN -> 2026
        CollegeEra.PRE_CLOCK -> 1980
        else -> era.from
    }
}
