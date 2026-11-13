package com.nh.hoopscoach.game

/**
 * A play style on the floor. Each style boosts three ratings, and the same three steer what the
 * player does in games: 3-point styles hunt threes, inside styles attack the rim, mid-range styles
 * take the pull-up, passing (and, a little, handling) styles pile up assists, rebounding and
 * interior-defense styles crash the glass, steal and perimeter-defense styles jump passing lanes
 * (a Two-Way Guard or a 3-and-D wing gets his steals), block and interior-defense styles block
 * shots. Scoring styles look for their shot more; defensive styles less.
 */
object Tendency {
    private fun has(p: Player, a: Attr) = a in p.style.boosts

    fun three(p: Player) = if (has(p, Attr.OUT)) 1.6 else 1.0
    fun inside(p: Player) = if (has(p, Attr.INS)) 1.4 else 1.0
    fun mid(p: Player) = if (has(p, Attr.MID)) 1.6 else 1.0
    fun pass(p: Player) = (if (has(p, Attr.PAS)) 1.8 else 1.0) * (if (has(p, Attr.HND)) 1.15 else 1.0) * Personality.passMod(p)
    /** Rebounders crash the glass; defenders (interior and perimeter: two-way and lockdown players) box out and finish the stop. */
    fun reb(p: Player) = (if (has(p, Attr.REB)) 1.7 else 1.0) * (if (has(p, Attr.IDF)) 1.15 else 1.0) * (if (has(p, Attr.PER)) 1.2 else 1.0) *
        (if (p.style == Arch.TWO_WAY_GUARD || p.style == Arch.TWO_WAY_WING || p.style == Arch.TWO_WAY_POINT) 1.25 else 1.0)
    fun stl(p: Player) = (if (has(p, Attr.STL)) 1.8 else 1.0) * (if (has(p, Attr.PER)) 1.6 else 1.0)
    fun blk(p: Player) = (if (has(p, Attr.BLK)) 1.9 else 1.0) * (if (has(p, Attr.IDF)) 1.2 else 1.0)

    /** Scoring styles (inside, mid-range, three; handling half) shoot more, each defensive rating in the style a little less. */
    fun usage(p: Player): Double {
        val b = p.style.boosts
        val scoring = b.count { it == Attr.INS || it == Attr.MID || it == Attr.OUT } + 0.5 * b.count { it == Attr.HND }
        val defense = b.count { it == Attr.PER || it == Attr.STL || it == Attr.BLK || it == Attr.IDF || it == Attr.REB }
        return ((1.0 + 0.08 * scoring - 0.07 * defense) * Personality.usageMod(p)).coerceIn(0.72, 1.38)
    }
}
