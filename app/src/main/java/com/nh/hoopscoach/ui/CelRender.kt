package com.nh.hoopscoach.ui

import com.nh.hoopscoach.game.Face
import com.nh.hoopscoach.game.Gear
import kotlin.math.hypot

/**
 * Retro bit-pixel portraits: a 32-by-32 sprite drawn in layers (team-colored background bands, a
 * tank top with his number, neck, head, ears, eyes, brows, nose, mouth, facial hair, hair, extras
 * and optional undershirt/sleeves) with flat shading and a one-pixel dark outline. Shown with hard
 * pixel edges. All original designs; the Face and Gear parts work as before.
 */
object CelRender {
    const val N = 32
    private val SKIN = longArrayOf(0xFFF6D7C3, 0xFFEBC2A1, 0xFFDDA679, 0xFFC68A5C, 0xFFA86E45, 0xFF8A5534, 0xFF6B3F26, 0xFF4E2C1A)
    private val HAIR = longArrayOf(0xFF151515, 0xFF3B2416, 0xFF6A4325, 0xFF9C5A2E, 0xFFD8B366, 0xFFE8E2D0, 0xFF8D8D8D, 0xFFB3262E)
    private const val OUTLINE = 0xFF141625.toInt()
    private const val WHITE = 0xFFF4F4F4.toInt()
    private const val PUPIL = 0xFF1A1A1A.toInt()
    private const val BLACK = 0xFF24242C.toInt()

    private fun shade(c: Int, f: Double): Int {
        val a = c ushr 24 and 0xFF
        fun ch(v: Int) = (if (f < 0) v * (1 + f) else v + (255 - v) * f).toInt().coerceIn(0, 255)
        return (a shl 24) or (ch(c shr 16 and 0xFF) shl 16) or (ch(c shr 8 and 0xFF) shl 8) or ch(c and 0xFF)
    }

    private fun mix(a: Int, b: Int, t: Double): Int {
        fun ch(s: Int) = ((a shr s and 0xFF) * (1 - t) + (b shr s and 0xFF) * t).toInt()
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private fun lum(c: Int) = ((c shr 16 and 0xFF) * 0.3 + (c shr 8 and 0xFF) * 0.59 + (c and 0xFF) * 0.11) / 255.0

    // 3x5 digits for the jersey number.
    private val DIGITS = listOf(
        "111101101101111", "010110010010111", "111001111100111", "111001111001111", "101101111001001",
        "111100111001111", "111100111101111", "111001010010010", "111101111101111", "111101111001111",
    )

    fun render(f: Face, primary: Int, secondary: Int, number: Int, gear: Gear = Gear()): IntArray {
        val px = IntArray(N * N)
        val fig = BooleanArray(N * N)
        fun inb(x: Int, y: Int) = x in 0 until N && y in 0 until N
        fun set(x: Int, y: Int, c: Int, figure: Boolean = true) {
            if (inb(x, y)) {
                px[y * N + x] = c
                if (figure) fig[y * N + x] = true
            }
        }
        fun get(x: Int, y: Int) = if (inb(x, y)) px[y * N + x] else 0
        fun isFig(x: Int, y: Int) = inb(x, y) && fig[y * N + x]

        // Background: four bands from a light to a dark take on the team color.
        val bgTop = shade(primary, 0.35)
        val bgBottom = shade(primary, -0.45)
        for (y in 0 until N) {
            val band = (y / 8).coerceAtMost(3)
            val c = mix(bgTop, bgBottom, band / 3.0)
            for (x in 0 until N) px[y * N + x] = c
        }
        // A thin lighter frame line at the top, like an old card.
        for (x in 0 until N) px[x] = shade(bgTop, 0.3)

        val skin = SKIN[f.skin.coerceIn(0, 7)].toInt()
        val skinD = shade(skin, -0.16)
        val skinL = shade(skin, 0.12)
        val hair = HAIR[f.hairColor.coerceIn(0, 7)].toInt()
        val hairD = shade(hair, -0.3)
        val hairL = if (f.hairColor in 4..6) shade(hair, 0.18) else shade(hair, 0.28)
        val jersey = primary or (0xFF shl 24)
        val trim = secondary or (0xFF shl 24)
        val jerseyD = shade(jersey, -0.25)
        fun gearCol(i: Int) = when (i) {
            1 -> WHITE
            2 -> BLACK
            3 -> trim
            else -> jersey
        }
        val shirt = when (gear.undershirt) {
            1 -> WHITE
            2 -> BLACK
            3 -> jersey
            else -> 0
        }
        val sleeveCol = gearCol(gear.sleeveColor)

        // Number color: trim when it contrasts; otherwise white or ink.
        val numCol = when {
            kotlin.math.abs(lum(trim) - lum(jersey)) >= 0.22 -> trim
            lum(jersey) > 0.45 -> OUTLINE
            else -> WHITE
        }

        // Head shape: half-widths per row from the top of the head.
        val (top, widths) = when (f.head.coerceIn(0, 3)) {
            1 -> 6 to intArrayOf(4, 5, 6, 7, 7, 7, 7, 7, 7, 7, 7, 7, 6, 6, 5, 4, 3)
            2 -> 6 to intArrayOf(5, 6, 6, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 6, 6, 5, 4)
            3 -> 5 to intArrayOf(3, 4, 5, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 5, 5, 4, 4, 3)
            else -> 6 to intArrayOf(3, 5, 6, 6, 7, 7, 7, 7, 7, 7, 7, 6, 6, 5, 5, 4, 3)
        }
        val chin = top + widths.size - 1
        fun left(y: Int) = 16 - widths[(y - top).coerceIn(0, widths.size - 1)]
        fun right(y: Int) = 15 + widths[(y - top).coerceIn(0, widths.size - 1)]
        val eyeY = top + 7
        val longBack = f.hair == 9 || f.hair == 10

        // Back hair: an afro, locs and long hair sit behind the head and shoulders.
        if (f.hair == 5) {
            val cx = 15.5; val cy = top + 4.0
            for (y in 0 until N) for (x in 0 until N) {
                val d = hypot(x - cx, (y - cy) * 1.05)
                if (d <= 10.2 && y <= top + 11) set(x, y, if ((x + y) % 5 == 0) hairL else if (d > 9.2) hairD else hair)
            }
        }
        if (longBack) {
            for (y in top + 2..24) for (x in left(top + 4) - 2..right(top + 4) + 2) {
                val edge = x <= left(top + 4) - 1 || x >= right(top + 4) + 1 || y <= top + 6
                if (edge) set(x, y, if (f.hair == 9 && x % 2 == 0) hairD else hair)
            }
        }

        // Shoulders and the tank top.
        for (y in 25 until N) {
            val half = when (y) { 25 -> 6; 26 -> 8; else -> 11 }
            for (x in 16 - half..15 + half) set(x, y, if (x == 15 + half || x == 14 + half) skinD else skin)
            // Undershirt peeks at the neck hole only (drawn with the neckline below).
            val jh = when (y) { 25 -> 3; 26 -> 5; 27 -> 6; else -> 7 }
            for (x in 16 - jh..15 + jh) set(x, y, if (x >= 14 + jh) jerseyD else jersey)
            // Trim along the armholes.
            set(16 - jh, y, trim); set(15 + jh, y, trim)
            // Arm sleeves over the outer shoulder (player's left is the right of the picture).
            if (gear.sleeve > 0 && y >= 27) {
                val sleeveL = gear.sleeve == 1 || gear.sleeve == 3
                val sleeveR = gear.sleeve == 2 || gear.sleeve == 3
                if (sleeveL) for (x in 15 + jh + 1..15 + half) set(x, y, if (x == 15 + half) shade(sleeveCol, -0.2) else sleeveCol)
                if (sleeveR) for (x in 16 - half until 16 - jh) set(x, y, if (x == 16 - half) shade(sleeveCol, -0.2) else sleeveCol)
            }
        }
        // The neckline: a U of skin (or undershirt) with trim around it.
        val neckFill = if (gear.undershirt > 0) shirt else skin
        for (x in 14..17) {
            set(x, 25, neckFill)
            set(x, 26, if (x == 14 || x == 17) trim else neckFill)
        }
        set(13, 25, trim); set(18, 25, trim)
        // His number on the chest.
        val digits = number.coerceIn(0, 99).toString()
        val w = digits.length * 3 + (digits.length - 1)
        var nx = 16 - (w + 1) / 2
        for (ch in digits) {
            val pat = DIGITS[ch - '0']
            for (i in 0 until 15) if (pat[i] == '1') {
                val dx = i % 3; val dy = i / 3
                if (27 + dy < N) set(nx + dx, 27 + dy, numCol)
            }
            nx += 4
        }

        // Neck, shadowed under the jaw.
        for (y in chin - 1..25) for (x in 13..18) set(x, y, if (y <= chin || x == 18) skinD else skin)

        // Head, shaded on the right, lit on the left.
        for ((i, hw) in widths.withIndex()) {
            val y = top + i
            for (x in 16 - hw..15 + hw) {
                val c = when {
                    x >= 15 + hw - 1 -> skinD
                    x == 16 - hw + 1 && i in 3..9 -> skinL
                    i == widths.size - 1 -> skinD
                    else -> skin
                }
                set(x, y, c)
            }
        }
        // Ears.
        for (y in eyeY - 1..eyeY + 2) {
            set(left(eyeY) - 1, y, skin)
            set(right(eyeY) + 1, y, skinD)
        }
        set(left(eyeY) - 1, eyeY, skinD)

        // Eyes.
        when (f.eyes.coerceIn(0, 2)) {
            1 -> {
                set(12, eyeY, PUPIL); set(13, eyeY, PUPIL)
                set(18, eyeY, PUPIL); set(19, eyeY, PUPIL)
            }
            2 -> {
                for (x in listOf(12, 13, 18, 19)) set(x, eyeY - 1, WHITE)
                set(12, eyeY, WHITE); set(13, eyeY, PUPIL)
                set(18, eyeY, PUPIL); set(19, eyeY, WHITE)
            }
            else -> {
                set(12, eyeY, WHITE); set(13, eyeY, PUPIL)
                set(18, eyeY, PUPIL); set(19, eyeY, WHITE)
            }
        }
        // Brows.
        val brow = if (f.hair == 0 && f.hairColor >= 4) hairD else shade(hair, -0.1)
        when (f.brows.coerceIn(0, 2)) {
            1 -> for (x in listOf(11, 12, 13, 18, 19, 20)) {
                set(x, eyeY - 2, brow)
                if (x in 12..13 || x in 18..19) set(x, eyeY - 3, brow)
            }
            2 -> {
                set(11, eyeY - 3, brow); set(12, eyeY - 2, brow); set(13, eyeY - 2, brow)
                set(18, eyeY - 2, brow); set(19, eyeY - 2, brow); set(20, eyeY - 3, brow)
            }
            else -> for (x in listOf(11, 12, 13, 18, 19, 20)) set(x, eyeY - 2, brow)
        }
        // Nose.
        set(16, eyeY + 1, skinD); set(16, eyeY + 2, skinD)
        set(15, eyeY + 3, skinD); set(16, eyeY + 3, shade(skin, -0.28))
        // Mouth.
        val lip = mix(shade(skin, -0.38), 0xFF8A2F2F.toInt(), 0.25)
        val my = eyeY + 5
        when (f.mouth.coerceIn(0, 2)) {
            1 -> {
                for (x in 14..17) set(x, my, lip)
                set(13, my - 1, lip); set(18, my - 1, lip)
                set(15, my + 1, skinD); set(16, my + 1, skinD)
            }
            2 -> {
                for (x in 15..17) set(x, my, lip)
                set(18, my - 1, lip)
            }
            else -> for (x in 14..17) set(x, my, lip)
        }

        // Facial hair.
        val fh = shade(hair, -0.05)
        when (f.beard.coerceIn(0, 5)) {
            1 -> for (y in my - 1..chin) for (x in left(y)..right(y)) {
                if ((x + y) % 2 == 0 && get(x, y) != lip) set(x, y, mix(get(x, y), fh, 0.55))
            }
            2 -> for (x in 14..17) set(x, my - 1, fh)
            3 -> {
                for (x in 14..17) set(x, my - 1, fh)
                set(14, my, fh); set(17, my, fh)
                for (y in my + 1..chin) for (x in 15..16) set(x, y, fh)
            }
            4 -> for (y in eyeY + 1..chin) {
                set(left(y), y, fh); set(right(y), y, fh)
                if (y >= chin - 1) for (x in left(y)..right(y)) set(x, y, fh)
            }
            5 -> for (y in my - 1..chin) for (x in left(y)..right(y)) {
                if (!(y == my && x in 14..17)) set(x, y, if (x >= right(y) - 1) shade(fh, -0.2) else fh)
            }
        }

        // Hair on top.
        val l0 = left(top + 3); val r0 = right(top + 3)
        fun cap(rows: Int, extraTop: Int = 0, sides: Int = 0, pattern: (Int, Int) -> Int = { _, _ -> hair }) {
            for (y in top - extraTop..top + rows) for (x in l0 - 1..r0 + 1) {
                val inHead = y >= top && x >= left(y) && x <= right(y)
                val above = y < top && x >= left(top) - 1 + (top - y) && x <= right(top) + 1 - (top - y)
                if (inHead || above || (y >= top && (x == left(y) - 1 || x == right(y) + 1) && y <= top + sides)) {
                    set(x, y, pattern(x, y))
                }
            }
        }
        when (f.hair.coerceIn(0, 13)) {
            0 -> set(13, top + 1, skinL)
            1 -> cap(2, sides = 4) { x, y -> if ((x + y) % 2 == 0) hair else mix(hair, skin, 0.45) }
            2 -> {
                cap(3, extraTop = 1, sides = 5)
                for (x in l0..r0 step 3) set(x, top + 4, hair)
                set(12, top - 1, hairL); set(13, top - 1, hairL)
            }
            3 -> {
                cap(2, extraTop = 1)
                for (y in top + 3..top + 6) {
                    set(left(y), y, mix(hair, skin, 0.5))
                    set(right(y), y, mix(hair, skin, 0.5))
                }
                set(13, top - 1, hairL)
            }
            4 -> {
                for (y in top - 4..top + 2) for (x in left(top + 2) - 1..right(top + 2) + 1) {
                    set(x, y, if (y == top - 4) hairL else hair)
                }
                for (y in top + 3..top + 5) {
                    set(left(y) - 1, y, hair); set(right(y) + 1, y, hair)
                }
            }
            5 -> cap(2, extraTop = 1, sides = 6) { x, y -> if ((x * 3 + y) % 7 == 0) hairL else hair }
            6 -> {
                for (y in top - 6..top + 2) {
                    val inset = if (y < top - 3) 1 else 0
                    for (x in l0 + inset..r0 - inset) {
                        set(x, y, if (y == top - 6) hairL else if (x == r0 - inset) hairD else hair)
                    }
                }
                for (y in top + 3..top + 5) {
                    set(left(y) - 1, y, hair); set(right(y) + 1, y, hair)
                }
            }
            7 -> {
                cap(3, extraTop = 2, sides = 6) { x, y ->
                    if ((x + y) % 3 == 0) hairD else if ((x + 2 * y) % 5 == 0) hairL else hair
                }
                for (x in l0 - 1..r0 + 1 step 2) set(x, top - 3, hair)
            }
            8 -> cap(4, sides = 3) { x, _ -> if (x % 2 == 0) hair else mix(hair, skin, 0.6) }
            9 -> cap(3, extraTop = 1, sides = 9) { x, _ -> if (x % 2 == 0) hairD else hair }
            10 -> {
                cap(3, extraTop = 1, sides = 9)
                for (y in top - 1..top + 3) set(15, y, hairD)
            }
            11 -> {
                for (y in top - 5..top + 3) for (x in 14..17) {
                    set(x, y, if (y == top - 5) hairL else if (x == 17) hairD else hair)
                }
                for (y in top..top + 3) {
                    set(left(y), y, mix(hair, skin, 0.6))
                    set(right(y), y, mix(hair, skin, 0.6))
                }
            }
            12 -> cap(3, extraTop = 1, sides = 4) { _, y -> if ((y - top) % 2 == 0) hairD else hair }
            13 -> {
                cap(2, extraTop = 1, sides = 5)
                for (y in top - 4..top - 1) for (x in 14..17) {
                    set(x, y, if (y == top - 4) hairL else hair)
                }
            }
        }

        // Extras.
        when (f.acc.coerceIn(0, 5)) {
            1, 2 -> {
                val band = if (f.acc == 1) WHITE else trim
                for (x in left(top + 3) - 1..right(top + 3) + 1) {
                    set(x, top + 3, band)
                    set(x, top + 4, if (f.acc == 2) jersey else shade(WHITE, -0.12))
                }
            }
            3 -> {
                val frame = 0xFF2B2B2B.toInt()
                val lens = 0xFFA8D8FF.toInt()
                for (x in 11..20) {
                    set(x, eyeY - 1, frame); set(x, eyeY + 1, frame)
                }
                for (x in listOf(11, 15, 16, 20)) set(x, eyeY, frame)
                for (x in listOf(12, 13, 14, 17, 18, 19)) set(x, eyeY, mix(get(x, eyeY), lens, 0.5))
                set(left(eyeY) - 1, eyeY, frame); set(right(eyeY) + 1, eyeY, frame)
            }
            4 -> set(left(eyeY) - 1, eyeY + 2, 0xFFF2C14E.toInt())
            5 -> {
                val frame = 0xFF222222.toInt()
                for (x in listOf(11, 14, 17, 20)) set(x, eyeY, frame)
                for (x in 11..14) set(x, eyeY - 1, frame)
                for (x in 17..20) set(x, eyeY - 1, frame)
                set(15, eyeY - 1, frame); set(16, eyeY - 1, frame)
            }
        }

        // The outline: every background pixel touching the figure.
        val out = px.copyOf()
        for (y in 0 until N) for (x in 0 until N) {
            if (isFig(x, y)) continue
            if (isFig(x - 1, y) || isFig(x + 1, y) || isFig(x, y - 1) || isFig(x, y + 1)) {
                out[y * N + x] = OUTLINE
            }
        }
        return out
    }
}
