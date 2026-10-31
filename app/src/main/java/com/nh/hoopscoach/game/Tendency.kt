package com.nh.hoopscoach.game

/**
 * A play style on the floor. Each style boosts three ratings; the same three steer what the
 * player does in games: a Sharpshooter (3-point) hunts threes, a Slasher (inside) attacks the rim,
 * a Floor General (passing) piles up assists, a Rebounder crashes the glass, a Rim Protector
 * blocks shots, a Ball Hawk jumps passing lanes. Scorers look for their shot more; defensive and
 * glue styles less.
 */
object Tendency {
    private fun has(p: Player, a: Attr) = a in p.style.boosts

    fun three(p: Player) = if (has(p, Attr.OUT)) 1.45 else 1.0
    fun inside(p: Player) = if (has(p, Attr.INS)) 1.35 else 1.0
    fun mid(p: Player) = if (has(p, Attr.MID)) 1.55 else 1.0
    fun pass(p: Player) = if (has(p, Attr.PAS)) 1.6 else 1.0
    fun reb(p: Player) = if (has(p, Attr.REB)) 1.45 else 1.0
    fun stl(p: Player) = if (has(p, Attr.STL)) 1.5 else 1.0
    fun blk(p: Player) = if (has(p, Attr.BLK)) 1.6 else 1.0

    /** Scoring styles (two or more of inside, mid-range, three and handling) shoot more; mostly-defensive styles less. */
    fun usage(p: Player): Double {
        val b = p.style.boosts
        val scoring = b.count { it == Attr.INS || it == Attr.MID || it == Attr.OUT || it == Attr.HND }
        return when { scoring >= 2 -> 1.15; scoring == 0 -> 0.85; else -> 1.0 }
    }
}
