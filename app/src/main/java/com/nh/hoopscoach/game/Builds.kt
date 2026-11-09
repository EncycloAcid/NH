package com.nh.hoopscoach.game

import kotlin.math.sqrt

/** A flavor-only NBA-legend template used for GOAT presets, offspring blends and "closest to" tags. */
data class GoatBuild(
    val id: String,
    val name: String,
    val pos: Pos,
    val heightLo: Int,
    val heightHi: Int,
    /** Relative attribute shape in Attr order (INS…BLK). Scaled to a target overall when applied. */
    val attrs: IntArray,
    val arch: Arch,
    val blurb: String,
)

/**
 * 2K-style build naming, GOAT catalog, offspring blends and "closest to" matching.
 * Legend names are flavor text only — no likenesses.
 */
object Builds {
    // Attr order: INS MID OUT HND PAS REB PER IDF STL BLK
    val catalog: List<GoatBuild> = listOf(
        g("jordan", "Michael Jordan", Pos.SG, 76, 78, a(88, 94, 78, 90, 72, 55, 92, 48, 88, 55), Arch.SHOT_CREATOR, "Midrange assassin who locked up and finished at the rim"),
        g("kobe", "Kobe Bryant", Pos.SG, 77, 78, a(86, 95, 80, 88, 70, 52, 88, 50, 78, 52), Arch.VOLUME_SCORER, "Footwork, fadeaways and competitive fire at both ends"),
        g("lebron", "LeBron James", Pos.SF, 79, 81, a(90, 78, 72, 88, 92, 78, 82, 70, 72, 62), Arch.POINT_FORWARD, "Power forward size with point-guard vision"),
        g("magic", "Magic Johnson", Pos.PG, 80, 81, a(78, 72, 55, 92, 98, 75, 70, 58, 68, 48), Arch.FLOOR_GENERAL, "Oversized floor general who pushed and found everyone"),
        g("bird", "Larry Bird", Pos.SF, 79, 80, a(78, 90, 92, 72, 88, 78, 75, 62, 68, 48), Arch.SHARPSHOOTER, "Trash-talking shooter who passed and rebounded like a big"),
        g("curry", "Stephen Curry", Pos.PG, 74, 75, a(70, 88, 98, 92, 85, 48, 68, 40, 72, 35), Arch.SHARPSHOOTER, "Gravity from deep and crafty creation off the bounce"),
        g("stockton", "John Stockton", Pos.PG, 72, 73, a(55, 70, 72, 90, 98, 42, 82, 40, 92, 35), Arch.PURE_POINT, "Ironman pure point who lived in the passing lanes"),
        g("iversen", "Allen Iverson", Pos.PG, 71, 72, a(85, 88, 75, 95, 70, 40, 72, 38, 78, 32), Arch.SLASHER, "Crossover artist who attacked every gap"),
        g("wade", "Dwyane Wade", Pos.SG, 75, 76, a(92, 82, 62, 88, 78, 55, 80, 52, 78, 68), Arch.SLASHER, "Explosive finisher who blocked shots from the two"),
        g("durant", "Kevin Durant", Pos.SF, 81, 83, a(88, 92, 90, 82, 68, 62, 72, 55, 58, 68), Arch.VOLUME_SCORER, "Length and touch that scores from every level"),
        g("kawhi", "Kawhi Leonard", Pos.SF, 78, 79, a(82, 88, 80, 75, 58, 68, 95, 70, 88, 55), Arch.THREE_AND_D, "Quiet two-way wing with board-grabbing hands"),
        g("pippen", "Scottie Pippen", Pos.SF, 79, 80, a(78, 75, 72, 82, 80, 72, 92, 68, 88, 62), Arch.FACILITATING_WING, "Point-forward defender who did a little of everything"),
        g("giannis", "Giannis Antetokounmpo", Pos.PF, 82, 83, a(96, 62, 55, 82, 72, 88, 78, 82, 68, 75), Arch.TRANSITION_FINISHER, "Freak athlete who lives in transition and at the rim"),
        g("dirk", "Dirk Nowitzki", Pos.PF, 83, 84, a(78, 95, 90, 58, 55, 78, 58, 68, 42, 55), Arch.STRETCH, "One-legged fadeaway that stretched every defense"),
        g("duncan", "Tim Duncan", Pos.PF, 82, 83, a(92, 82, 45, 55, 68, 90, 62, 92, 48, 85), Arch.POST_SCORER, "Bank-shot fundamentalist who anchored both ends"),
        g("shaq", "Shaquille O'Neal", Pos.C, 84, 86, a(98, 45, 25, 48, 55, 92, 42, 88, 45, 82), Arch.ENFORCER, "Unstoppable force in the paint"),
        g("hakeem", "Hakeem Olajuwon", Pos.C, 83, 84, a(95, 85, 48, 68, 55, 88, 70, 92, 72, 95), Arch.RIM_PROTECTOR, "Dream Shake footwork and elite rim protection"),
        g("jokic", "Nikola Jokic", Pos.C, 82, 83, a(90, 82, 78, 72, 95, 92, 55, 72, 55, 58), Arch.POINT_CENTER, "Passing big who sees the whole floor from the post"),
        g("embiid", "Joel Embiid", Pos.C, 83, 84, a(94, 85, 75, 58, 55, 88, 58, 90, 48, 88), Arch.POST_SCORER, "Skilled scoring big who can step out and protect"),
        g("garnett", "Kevin Garnett", Pos.PF, 82, 83, a(85, 82, 68, 68, 70, 90, 85, 88, 62, 78), Arch.SWITCH_BIG, "High-motor big who switched, talked and rebounded"),
        g("draymond", "Draymond Green", Pos.PF, 77, 78, a(62, 58, 68, 78, 88, 85, 90, 85, 82, 72), Arch.GLUE_GUY, "Switchable glue guy who runs the offense from the elbow"),
        g("westbrook", "Russell Westbrook", Pos.PG, 74, 75, a(90, 72, 62, 92, 88, 78, 70, 48, 78, 42), Arch.TRANSITION_FINISHER, "Explosive attacker who fills the box score"),
        g("harden", "James Harden", Pos.SG, 76, 77, a(85, 82, 90, 95, 88, 55, 62, 45, 68, 42), Arch.PLAYMAKER, "Step-back threes and downhill creation"),
        g("tmac", "Tracy McGrady", Pos.SG, 79, 80, a(88, 90, 85, 85, 72, 58, 72, 52, 62, 55), Arch.SHOT_CREATOR, "Smooth scorer who created his own look anytime"),
        g("cp3", "Chris Paul", Pos.PG, 72, 73, a(68, 85, 78, 95, 96, 48, 88, 42, 90, 35), Arch.FLOOR_GENERAL, "Point-god tempo control and midrange mastery"),
        g("kgai", "Gary Payton", Pos.PG, 74, 75, a(72, 78, 72, 85, 82, 48, 95, 45, 92, 38), Arch.TWO_WAY_GUARD, "The Glove — trash talk and lockdown perimeter D"),
        g("melo", "Carmelo Anthony", Pos.SF, 79, 80, a(88, 94, 82, 78, 55, 62, 58, 52, 48, 42), Arch.MIDRANGE_ASSASSIN, "Isolation scorer with a deadly midrange game"),
        g("butler", "Jimmy Butler", Pos.SF, 78, 79, a(85, 82, 72, 80, 72, 62, 90, 65, 78, 48), Arch.TWO_WAY_GUARD, "Two-way wing who wills teams through March"),
    )

    private fun g(id: String, name: String, pos: Pos, lo: Int, hi: Int, attrs: IntArray, arch: Arch, blurb: String) =
        GoatBuild(id, name, pos, lo, hi, attrs, arch, blurb)

    private fun a(vararg v: Int): IntArray {
        require(v.size == 10)
        return v
    }

    fun byId(id: String): GoatBuild? = catalog.firstOrNull { it.id == id }

    fun forPos(pos: Pos): List<GoatBuild> = catalog.filter { it.pos == pos }.ifEmpty { catalog }

    /** Nearby positions so a SF player can still pick LeBron/Kawhi/etc. */
    fun nearby(pos: Pos): List<GoatBuild> {
        val near = when (pos) {
            Pos.PG -> setOf(Pos.PG, Pos.SG)
            Pos.SG -> setOf(Pos.PG, Pos.SG, Pos.SF)
            Pos.SF -> setOf(Pos.SG, Pos.SF, Pos.PF)
            Pos.PF -> setOf(Pos.SF, Pos.PF, Pos.C)
            Pos.C -> setOf(Pos.PF, Pos.C)
        }
        return catalog.filter { it.pos in near }
    }

    /** Scale a template so [Ratings.ovr] lands near [target]. */
    fun scaleToOvr(pos: Pos, template: IntArray, target: Int): IntArray {
        val a = template.copyOf()
        repeat(4) {
            val diff = target - Ratings.ovr(pos, a)
            if (diff == 0) return@repeat
            for (i in 0 until 10) a[i] = (a[i] + diff).coerceIn(20, 99)
        }
        for (i in 0 until 10) a[i] = a[i].coerceIn(20, 99)
        // Fine nudge on the highest-weighted attrs if still off.
        var guard = 0
        while (kotlin.math.abs(Ratings.ovr(pos, a) - target) > 1 && guard++ < 40) {
            val needUp = Ratings.ovr(pos, a) < target
            val i = (0 until 10).maxBy { if (needUp) a[it] else -a[it] }
            a[i] = (a[i] + if (needUp) 1 else -1).coerceIn(20, 99)
        }
        return a
    }

    fun blend(a: IntArray, b: IntArray, biasTowardA: Float = 0.5f): IntArray {
        val w = biasTowardA.coerceIn(0f, 1f)
        return IntArray(10) { i -> (a[i] * w + b[i] * (1f - w)).toInt().coerceIn(20, 99) }
    }

    fun offspring(parentA: GoatBuild, parentB: GoatBuild, biasTowardA: Float = 0.5f): IntArray =
        blend(parentA.attrs, parentB.attrs, biasTowardA)

    /** Cosine distance on attr vectors (lower = closer). */
    fun distance(a: IntArray, b: IntArray): Double {
        var dot = 0.0
        var na = 0.0
        var nb = 0.0
        for (i in 0 until 10) {
            val x = a[i].toDouble()
            val y = b[i].toDouble()
            dot += x * y; na += x * x; nb += y * y
        }
        if (na < 1e-9 || nb < 1e-9) return 1.0
        return 1.0 - dot / (sqrt(na) * sqrt(nb))
    }

    fun closestGoats(pos: Pos, attrs: IntArray, n: Int = 3): List<GoatBuild> {
        val pool = nearby(pos).ifEmpty { catalog }
        return pool.sortedBy { distance(attrs, it.attrs) }.take(n)
    }

    /** Pick the non-MANUAL archetype whose boosts best match the player's highs. */
    fun bestArch(pos: Pos, attrs: IntArray): Arch {
        val pool = Ratings.styles(pos)
        return pool.maxBy { arch ->
            arch.boosts.sumOf { attrs[it.ordinal].toDouble() } / arch.boosts.size.coerceAtLeast(1)
        }
    }

    /** 2K-style build title from position + attribute shape. */
    fun buildName(pos: Pos, attrs: IntArray): String {
        val mean = attrs.average()
        fun high(vararg idx: Int) = idx.map { attrs[it] - mean }.average()
        val shoot = high(Attr.INS.ordinal, Attr.MID.ordinal, Attr.OUT.ordinal)
        val create = high(Attr.HND.ordinal, Attr.PAS.ordinal)
        val defend = high(Attr.PER.ordinal, Attr.IDF.ordinal, Attr.STL.ordinal, Attr.BLK.ordinal)
        val glass = high(Attr.REB.ordinal)
        val three = attrs[Attr.OUT.ordinal] - mean
        val mid = attrs[Attr.MID.ordinal] - mean
        val inside = attrs[Attr.INS.ordinal] - mean
        val pass = attrs[Attr.PAS.ordinal] - mean
        val handle = attrs[Attr.HND.ordinal] - mean

        val tags = mutableListOf<String>()
        if (defend > 4 && shoot > 2) tags += "Two-Way"
        if (three > 6 && mid > 3 && inside > 2) tags += "3-Level"
        else if (three > 7) tags += "Sharpshooting"
        else if (mid > 7 && three < 4) tags += "Midrange"
        if (pass > 7 || (create > 5 && pass > 4)) tags += "Playmaking"
        if (inside > 7 && three < 3) tags += "Slashing"
        if (glass > 6) tags += "Rebounding"
        if (defend > 7 && shoot < 2) tags += "Defensive"

        val role = when (pos) {
            Pos.PG -> when {
                pass > 6 && handle > 4 -> "Point Guard"
                shoot > 5 -> "Scoring Guard"
                defend > 5 -> "Two-Way Guard"
                else -> "Combo Guard"
            }
            Pos.SG -> when {
                three > 6 && defend > 4 -> "3-and-D Guard"
                shoot > 6 -> "Shooting Guard"
                create > 5 -> "Shot Creator"
                else -> "Wing Guard"
            }
            Pos.SF -> when {
                pass > 5 -> "Point Forward"
                three > 5 && defend > 4 -> "3-and-D Wing"
                shoot > 5 -> "Scoring Wing"
                else -> "Forward"
            }
            Pos.PF -> when {
                three > 5 -> "Stretch Four"
                defend > 5 && glass > 4 -> "Switch Big"
                inside > 5 -> "Power Forward"
                else -> "Big"
            }
            Pos.C -> when {
                pass > 5 -> "Point Center"
                three > 5 -> "Stretch Five"
                defend > 5 -> "Rim Protector"
                else -> "Center"
            }
        }

        val prefix = tags.distinct().take(2).joinToString(" ")
        return if (prefix.isEmpty()) role else "$prefix $role".trim()
    }

    fun stampFlavor(p: Player) {
        p.buildName = buildName(p.pos, p.attrs)
        val likes = closestGoats(p.pos, p.attrs, 2)
        p.likeA = likes.getOrNull(0)?.name ?: ""
        p.likeB = likes.getOrNull(1)?.name ?: ""
    }

    fun applyAttrs(
        p: Player,
        attrs: IntArray,
        arch: Arch? = null,
        goatId: String = "",
        parentA: String = "",
        parentB: String = "",
    ) {
        val shaped = scaleToOvr(p.pos, attrs, p.ovr)
        for (i in 0 until 10) p.attrs[i] = shaped[i]
        val style = arch ?: bestArch(p.pos, p.attrs)
        p.style = style
        if (p.focus != Arch.MANUAL) p.focus = style
        p.goatId = goatId
        p.parentA = parentA
        p.parentB = parentB
        stampFlavor(p)
    }

    fun applyGoat(p: Player, goat: GoatBuild) {
        applyAttrs(p, goat.attrs, goat.arch, goatId = goat.id)
    }

    fun applyOffspring(p: Player, a: GoatBuild, b: GoatBuild, biasTowardA: Float = 0.5f) {
        val blended = offspring(a, b, biasTowardA)
        val arch = if (biasTowardA >= 0.5f) a.arch else b.arch
        applyAttrs(p, blended, arch, parentA = a.id, parentB = b.id)
    }

    fun applyCustom(p: Player, attrs: IntArray) {
        // Keep the same total attribute points; redistribute only.
        val budget = p.attrs.sum()
        val raw = attrs.copyOf()
        val sum = raw.sum().coerceAtLeast(1)
        val scaled = IntArray(10) { i -> (raw[i].toDouble() * budget / sum).toInt().coerceIn(20, 99) }
        var drift = budget - scaled.sum()
        var i = 0
        while (drift != 0 && i < 200) {
            val idx = i % 10
            if (drift > 0 && scaled[idx] < 99) { scaled[idx]++; drift-- }
            else if (drift < 0 && scaled[idx] > 20) { scaled[idx]--; drift++ }
            i++
        }
        applyAttrs(p, scaleToOvr(p.pos, scaled, p.ovr))
    }

    /** Chance a high-end recruit is shaped like a GOAT (or rare offspring). */
    fun maybeGoatShape(p: Player) {
        if (p.stars < 4) {
            stampFlavor(p)
            return
        }
        if (!Rng.chance(0.20)) {
            stampFlavor(p)
            return
        }
        val pool = nearby(p.pos)
        if (pool.isEmpty()) {
            stampFlavor(p)
            return
        }
        if (Rng.chance(0.25) && pool.size >= 2) {
            val a = Rng.pick(pool)
            val b = Rng.pick(pool.filter { it.id != a.id }.ifEmpty { pool })
            applyOffspring(p, a, b, Rng.range(35, 65) / 100f)
        } else {
            applyGoat(p, Rng.pick(pool))
        }
        p.pot = p.pot.coerceAtLeast(p.ovr + 2)
    }
}
