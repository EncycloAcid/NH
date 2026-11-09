package com.nh.hoopscoach.ui

import com.nh.hoopscoach.game.Face
import com.nh.hoopscoach.game.Gear
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Retro bit full-body portraits in the style of a handheld sports sprite: a 64×64 player mid-dribble
 * with a thick black outline and flat cell shading. Skin, hair, face, jersey, number, undershirt,
 * sleeves, wristbands, shoes and legs come from [Face] and [Gear] in his team's colors. Shown with
 * hard pixel edges.
 */
object CelRender {
    const val N = 64
    private const val OUTLINE = 0xFF141414.toInt()
    private const val WHITE = 0xFFF4F4F4.toInt()
    private const val BLACK = 0xFF1E1E22.toInt()
    private const val BALL = 0xFFE07030.toInt()
    private const val BALL_D = 0xFFB04818.toInt()
    private const val BALL_L = 0xFFF0A050.toInt()

    private val SKIN = intArrayOf(
        0xFFF6D7C3.toInt(), 0xFFEBC2A1.toInt(), 0xFFDDA679.toInt(), 0xFFC68A5C.toInt(),
        0xFFA86E45.toInt(), 0xFF8A5534.toInt(), 0xFF6B3F26.toInt(), 0xFF4E2C1A.toInt(),
    )
    private val HAIR = intArrayOf(
        0xFF1A1A1A.toInt(), 0xFF3B2416.toInt(), 0xFF6A4325.toInt(), 0xFF9C5A2E.toInt(),
        0xFFD8B366.toInt(), 0xFFE8E2D0.toInt(), 0xFF8D8D8D.toInt(), 0xFFB3262E.toInt(),
    )

    // 3x5 digits.
    private val DIGITS = listOf(
        "111101101101111", "010110010010111", "111001111100111", "111001111001111", "101101111001001",
        "111100111001111", "111100111101111", "111001010010010", "111101111101111", "111101111001111",
    )

    private fun shade(c: Int, f: Double): Int {
        fun ch(v: Int) = (if (f < 0) v * (1 + f) else v + (255 - v) * f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(c shr 16 and 0xFF) shl 16) or (ch(c shr 8 and 0xFF) shl 8) or ch(c and 0xFF)
    }

    private fun mix(a: Int, b: Int, t: Double): Int {
        fun ch(s: Int) = ((a shr s and 0xFF) * (1 - t) + (b shr s and 0xFF) * t).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private fun lum(c: Int) = ((c shr 16 and 0xFF) * 0.3 + (c shr 8 and 0xFF) * 0.59 + (c and 0xFF) * 0.11) / 255.0

    private fun gearColor(i: Int, team: Int, trim: Int) = when (i) {
        1 -> WHITE
        2 -> BLACK
        3 -> trim
        else -> team
    }

    fun render(f: Face, primary: Int, secondary: Int, number: Int, gear: Gear = Gear()): IntArray {
        val px = IntArray(N * N) // transparent bg
        val fig = BooleanArray(N * N)
        fun inb(x: Int, y: Int) = x in 0 until N && y in 0 until N
        fun set(x: Int, y: Int, c: Int, figure: Boolean = true) {
            if (!inb(x, y)) return
            px[y * N + x] = c
            if (figure) fig[y * N + x] = true
        }
        fun fill(x0: Int, y0: Int, x1: Int, y1: Int, c: Int) {
            for (y in y0..y1) for (x in x0..x1) set(x, y, c)
        }
        fun oval(cx: Double, cy: Double, rx: Double, ry: Double, c: Int, shadow: Int? = null, lightFromLeft: Boolean = true) {
            val x0 = (cx - rx).toInt() - 1; val x1 = (cx + rx).toInt() + 1
            val y0 = (cy - ry).toInt() - 1; val y1 = (cy + ry).toInt() + 1
            for (y in y0..y1) for (x in x0..x1) {
                val nx = (x + 0.5 - cx) / rx; val ny = (y + 0.5 - cy) / ry
                if (nx * nx + ny * ny <= 1.0) {
                    val col = if (shadow != null && ((lightFromLeft && nx > 0.35) || (!lightFromLeft && nx < -0.35) || ny > 0.55)) shadow else c
                    set(x, y, col)
                }
            }
        }
        fun disc(cx: Int, cy: Int, r: Int, c: Int, shadow: Int? = null) = oval(cx + 0.5, cy + 0.5, r + 0.5, r + 0.5, c, shadow)

        val skin = SKIN[f.skin.coerceIn(0, 7)]
        val skinD = shade(skin, -0.18)
        val skinL = shade(skin, 0.1)
        val hair = HAIR[f.hairColor.coerceIn(0, 7)]
        val hairD = shade(hair, -0.28)
        val hairL = if (f.hairColor in 4..6) shade(hair, 0.16) else shade(hair, 0.22)
        val jersey = primary or (0xFF shl 24)
        val trim = secondary or (0xFF shl 24)
        val jerseyD = shade(jersey, -0.22)
        val trimD = shade(trim, -0.2)
        val numCol = when {
            abs(lum(trim) - lum(jersey)) >= 0.2 -> trim
            lum(jersey) > 0.45 -> OUTLINE
            else -> WHITE
        }
        val shirt = when (gear.undershirt) { 1 -> WHITE; 2 -> BLACK; 3 -> jersey; else -> 0 }
        val sleeveCol = gearColor(gear.sleeveColor, jersey, trim)
        val sleeveD = shade(sleeveCol, -0.2)
        val shoe = gearColor(gear.shoeColor, jersey, trim)
        val shoeD = shade(shoe, -0.25)
        val shoeAcc = if (lum(shoe) > 0.5) BLACK else WHITE
        val legsCol = when (gear.legs) {
            1 -> BLACK; 2 -> WHITE; 3, 4 -> gearColor(0, jersey, trim); else -> 0
        }
        val legsD = if (legsCol != 0) shade(legsCol, -0.2) else 0
        val wrist = when (gear.wristbands) { 1 -> WHITE; 2 -> BLACK; 3 -> jersey; else -> 0 }

        // ---------- pose anchors: mid-stride dribble, like the reference ----------
        val hx = 30; val hy = 11
        val neckY = 17
        val torsoTop = 18; val torsoBot = 33
        val hipY = 34
        val ballX = 50; val ballY = 50

        // ---------- back leg (viewer's left, planted) ----------
        for (y in hipY..47) {
            val t = (y - hipY) / 13.0
            val cx = 20 - (t * 5).roundToInt()
            val half = 5
            for (x in cx - half..cx + half) {
                val covered = legsCol != 0 && (gear.legs in setOf(1, 2, 4) || y >= 40)
                val col = if (covered) (if (x > cx + 1) legsD else legsCol) else if (x > cx + 1) skinD else skin
                set(x, y, col)
            }
        }
        for (y in 48..56) {
            val t = (y - 48) / 8.0
            val cx = 15 - (t * 2).roundToInt()
            val half = if (y < 54) 3 else 2
            for (x in cx - half..cx + half) {
                val covered = legsCol != 0 && gear.legs in setOf(1, 2, 4)
                val col = if (covered) (if (x > cx + 1) legsD else legsCol) else if (x > cx + 1) skinD else skin
                set(x, y, col)
            }
        }
        drawShoe(gear.shoes, 10, 57, shoe, shoeD, shoeAcc, facingRight = false, ::set, ::fill)

        // ---------- front leg (viewer's right, striding) ----------
        for (y in hipY..46) {
            val t = (y - hipY) / 12.0
            val cx = 36 + (t * 8).roundToInt()
            val half = 5
            for (x in cx - half..cx + half) {
                val covered = legsCol != 0 && (gear.legs in setOf(1, 2, 4) || y >= 40)
                val col = if (covered) (if (x > cx + 1) legsD else legsCol) else if (x > cx + 1) skinD else skin
                set(x, y, col)
            }
        }
        for (y in 47..55) {
            val t = (y - 47) / 8.0
            val cx = 44 + (t * 3).roundToInt()
            val half = if (y < 53) 3 else 2
            for (x in cx - half..cx + half) {
                val covered = legsCol != 0 && gear.legs in setOf(1, 2, 4)
                val col = if (covered) (if (x > cx + 1) legsD else legsCol) else if (x > cx + 1) skinD else skin
                set(x, y, col)
            }
        }
        if (gear.legs == 3) {
            for (y in 43..47) for (x in 38..48) if (y * N + x in fig.indices && fig[y * N + x]) {
                set(x, y, if (x > 44) legsD else legsCol)
            }
        }
        drawShoe(gear.shoes, 44, 56, shoe, shoeD, shoeAcc, facingRight = true, ::set, ::fill)

        // ---------- shorts (over the tights) ----------
        for (y in hipY - 1..hipY + 7) {
            val half = when (y - hipY) {
                -1 -> 9; 0 -> 10; 1 -> 11; 2 -> 11; 3 -> 10; 4 -> 9; 5 -> 8; else -> 7
            }
            for (x in 30 - half..30 + half) {
                val stripe = x - 30 == (y - hipY) + 3 || x - 30 == (y - hipY) + 4
                set(x, y, when {
                    stripe && x > 30 -> trim
                    x > 30 + half - 2 -> jerseyD
                    else -> jersey
                })
            }
        }
        for (x in 22..38) set(x, hipY + 7, trimD)

        // ---------- torso / jersey ----------
        for (y in torsoTop..torsoBot) {
            val t = (y - torsoTop).toDouble() / (torsoBot - torsoTop)
            val half = (8 + t * 3).roundToInt()
            // Slight lean toward the dribble side.
            val lean = ((y - torsoTop) / 5)
            for (x in hx - half + lean..hx + half + lean) {
                set(x, y, if (x > hx + half + lean - 2) jerseyD else jersey)
            }
        }
        // Armholes trim
        for (y in torsoTop + 1..torsoTop + 8) {
            set(hx - 8, y, trim); set(hx + 8, y, trim)
        }
        // V-neck
        if (gear.undershirt > 0) {
            for (y in torsoTop..torsoTop + 4) for (x in hx - 2..hx + 2) {
                if (abs(x - hx) + (y - torsoTop) < 4) set(x, y, shirt)
            }
            set(hx - 2, torsoTop, trim); set(hx + 2, torsoTop, trim)
            set(hx - 1, torsoTop + 1, trim); set(hx + 1, torsoTop + 1, trim)
            set(hx, torsoTop + 2, trim)
        } else {
            for (y in torsoTop..torsoTop + 3) {
                set(hx - 2 + (y - torsoTop), y, trim)
                set(hx + 2 - (y - torsoTop), y, trim)
            }
            set(hx, torsoTop + 3, skin)
            set(hx - 1, torsoTop + 2, skin); set(hx + 1, torsoTop + 2, skin)
        }
        // Number on chest
        val digits = number.coerceIn(0, 99).toString()
        val nw = digits.length * 3 + (digits.length - 1)
        var nx = hx - nw / 2
        val ny = torsoTop + 8
        for (ch in digits) {
            val pat = DIGITS[ch - '0']
            for (i in 0 until 15) if (pat[i] == '1') set(nx + i % 3, ny + i / 3, numCol)
            nx += 4
        }

        // ---------- arms ----------
        // Back arm (viewer's left): bent near waist
        for (y in 20..28) {
            val cx = hx - 9 - ((y - 20) / 3)
            for (x in cx - 2..cx + 2) set(x, y, if (x > cx) skinD else skin)
        }
        for (y in 28..34) {
            val cx = hx - 11 + ((y - 28) / 2)
            for (x in cx - 2..cx + 2) set(x, y, if (x > cx) skinD else skin)
        }
        // Fist near hip
        disc(hx - 9, 35, 2, skin, skinD)
        if (wrist != 0) fill(hx - 12, 32, hx - 8, 33, wrist)

        // Dribble arm (viewer's right): reaching down to the ball
        for (y in 20..28) {
            val cx = hx + 10 + ((y - 20) / 3)
            val onSleeve = gear.sleeve == 1 || gear.sleeve == 3
            for (x in cx - 3..cx + 3) {
                val col = if (onSleeve && y <= 27) (if (x > cx) sleeveD else sleeveCol) else if (x > cx) skinD else skin
                set(x, y, col)
            }
        }
        for (y in 29..48) {
            val t = (y - 29) / 19.0
            val cx = hx + 14 + (t * 10).roundToInt()
            val onSleeve = (gear.sleeve == 1 || gear.sleeve == 3) && y <= 34
            for (x in cx - 3..cx + 3) {
                val col = if (onSleeve) (if (x > cx) sleeveD else sleeveCol) else if (x > cx) skinD else skin
                set(x, y, col)
            }
        }
        // Hand on the ball
        disc(ballX - 1, ballY - 5, 3, skin, skinD)
        if (wrist != 0) fill(ballX - 5, 43, ballX, 44, wrist)

        // Opposite sleeve if both / right arm (viewer's left)
        if (gear.sleeve == 2 || gear.sleeve == 3) {
            for (y in 20..26) {
                val cx = hx - 9 - ((y - 20) / 3)
                for (x in cx - 2..cx + 2) set(x, y, if (x > cx) sleeveD else sleeveCol)
            }
        }

        // ---------- ball ----------
        disc(ballX, ballY, 5, BALL, BALL_D)
        // Seam + highlight
        for (a in -4..4) {
            set(ballX + a, ballY, OUTLINE)
            set(ballX, ballY + a, OUTLINE)
        }
        set(ballX - 2, ballY - 3, BALL_L); set(ballX - 3, ballY - 2, BALL_L)

        // ---------- neck ----------
        fill(hx - 2, neckY - 1, hx + 2, torsoTop, skin)
        set(hx + 2, neckY, skinD); set(hx + 2, neckY + 1, skinD)

        // ---------- head ----------
        val (rx, ryTop, ryBot) = when (f.head.coerceIn(0, 3)) {
            1 -> Triple(7, 6, 7)   // round
            2 -> Triple(7, 5, 8)   // square
            3 -> Triple(6, 7, 9)   // long
            else -> Triple(7, 6, 8)
        }
        for (y in hy - ryTop..hy + ryBot) {
            val t = if (y <= hy) (hy - y).toDouble() / ryTop else (y - hy).toDouble() / ryBot
            val hw = (rx * (1 - t * t * 0.85)).roundToInt().coerceAtLeast(2)
            for (x in hx - hw..hx + hw) {
                val col = when {
                    x >= hx + hw - 1 -> skinD
                    x == hx - hw + 1 && y in hy - 2..hy + 3 -> skinL
                    y >= hy + ryBot - 1 -> skinD
                    else -> skin
                }
                set(x, y, col)
            }
        }
        // Ears
        set(hx - rx - 1, hy, skin); set(hx - rx - 1, hy + 1, skinD)
        set(hx + rx + 1, hy, skinD); set(hx + rx + 1, hy + 1, skinD)

        // Eyes
        val eyeY = hy
        when (f.eyes.coerceIn(0, 2)) {
            1 -> { // focused: smaller dark
                set(hx - 3, eyeY, OUTLINE); set(hx - 2, eyeY, OUTLINE)
                set(hx + 2, eyeY, OUTLINE); set(hx + 3, eyeY, OUTLINE)
            }
            2 -> { // wide
                fill(hx - 4, eyeY - 1, hx - 2, eyeY, WHITE)
                fill(hx + 2, eyeY - 1, hx + 4, eyeY, WHITE)
                set(hx - 3, eyeY, OUTLINE); set(hx + 3, eyeY, OUTLINE)
                set(hx - 4, eyeY - 1, WHITE)
            }
            else -> {
                set(hx - 3, eyeY, WHITE); set(hx - 2, eyeY, OUTLINE)
                set(hx + 2, eyeY, OUTLINE); set(hx + 3, eyeY, WHITE)
            }
        }
        // Brows
        val brow = if (f.hair == 0 && f.hairColor >= 4) hairD else shade(hair, -0.1)
        when (f.brows.coerceIn(0, 2)) {
            1 -> {
                fill(hx - 4, eyeY - 3, hx - 1, eyeY - 2, brow)
                fill(hx + 1, eyeY - 3, hx + 4, eyeY - 2, brow)
            }
            2 -> {
                set(hx - 4, eyeY - 3, brow); set(hx - 3, eyeY - 2, brow); set(hx - 2, eyeY - 2, brow)
                set(hx + 2, eyeY - 2, brow); set(hx + 3, eyeY - 2, brow); set(hx + 4, eyeY - 3, brow)
            }
            else -> {
                fill(hx - 4, eyeY - 2, hx - 1, eyeY - 2, brow)
                fill(hx + 1, eyeY - 2, hx + 4, eyeY - 2, brow)
            }
        }
        // Nose
        set(hx, eyeY + 2, skinD); set(hx, eyeY + 3, skinD); set(hx - 1, eyeY + 4, shade(skin, -0.28))
        // Mouth
        val lip = mix(shade(skin, -0.35), 0xFF8A2F2F.toInt(), 0.28)
        val my = eyeY + 6
        when (f.mouth.coerceIn(0, 2)) {
            1 -> { fill(hx - 2, my, hx + 2, my, lip); set(hx - 3, my - 1, lip); set(hx + 3, my - 1, lip) }
            2 -> { fill(hx - 1, my, hx + 2, my, lip); set(hx + 3, my - 1, lip) }
            else -> fill(hx - 2, my, hx + 2, my, lip)
        }

        // Facial hair
        val fh = shade(hair, -0.05)
        val chinY = hy + ryBot
        when (f.beard.coerceIn(0, 5)) {
            1 -> for (y in my..chinY) for (x in hx - 5..hx + 5) {
                val i = y * N + x
                if (i in fig.indices && fig[i] && (x + y) % 2 == 0 && px[i] != lip) {
                    set(x, y, mix(px[i], fh, 0.55))
                }
            }
            2 -> fill(hx - 2, my - 1, hx + 2, my - 1, fh)
            3 -> {
                fill(hx - 2, my - 1, hx + 2, my - 1, fh)
                set(hx - 2, my, fh); set(hx + 2, my, fh)
                fill(hx - 1, my + 1, hx + 1, chinY, fh)
            }
            4 -> for (y in eyeY + 2..chinY) {
                set(hx - 5, y, fh); set(hx + 5, y, fh)
                if (y >= chinY - 1) fill(hx - 5, y, hx + 5, y, fh)
            }
            5 -> for (y in my - 1..chinY) for (x in hx - 5..hx + 5) {
                val i = y * N + x
                if (i in fig.indices && fig[i] && !(y == my && x in hx - 2..hx + 2)) {
                    set(x, y, if (x >= hx + 4) shade(fh, -0.2) else fh)
                }
            }
        }

        // Hair
        drawHair(f.hair.coerceIn(0, 13), hx, hy, rx, ryTop, hair, hairD, hairL, skin, ::set, ::fill, ::disc)

        // Accessories
        when (f.acc.coerceIn(0, 5)) {
            1, 2 -> {
                val band = if (f.acc == 1) WHITE else trim
                fill(hx - rx, hy - 3, hx + rx, hy - 2, band)
                if (f.acc == 1) fill(hx - rx, hy - 2, hx + rx, hy - 2, shade(WHITE, -0.12))
                else fill(hx - rx, hy - 2, hx + rx, hy - 2, jersey)
            }
            3 -> { // goggles
                val frame = 0xFF2B2B2B.toInt()
                fill(hx - 5, eyeY - 1, hx + 5, eyeY + 1, frame)
                fill(hx - 4, eyeY, hx - 2, eyeY, mix(WHITE, 0xFF88CCFF.toInt(), 0.5))
                fill(hx + 2, eyeY, hx + 4, eyeY, mix(WHITE, 0xFF88CCFF.toInt(), 0.5))
            }
            4 -> set(hx - rx - 1, hy + 2, 0xFFF2C14E.toInt())
            5 -> { // glasses
                val frame = 0xFF222222.toInt()
                fill(hx - 5, eyeY - 1, hx - 1, eyeY - 1, frame)
                fill(hx + 1, eyeY - 1, hx + 5, eyeY - 1, frame)
                set(hx - 5, eyeY, frame); set(hx - 1, eyeY, frame)
                set(hx + 1, eyeY, frame); set(hx + 5, eyeY, frame)
                set(hx, eyeY - 1, frame)
            }
        }

        // Thick silhouette outline
        val out = px.copyOf()
        for (y in 0 until N) for (x in 0 until N) {
            if (!fig[y * N + x]) continue
            var edge = false
            for (dy in -1..1) for (dx in -1..1) {
                if (dx == 0 && dy == 0) continue
                val xx = x + dx; val yy = y + dy
                if (xx !in 0 until N || yy !in 0 until N || !fig[yy * N + xx]) { edge = true; break }
            }
            // Draw outline onto neighboring empty pixels for a thick comic outline.
            if (edge) {
                for (dy in -1..1) for (dx in -1..1) {
                    val xx = x + dx; val yy = y + dy
                    if (xx in 0 until N && yy in 0 until N && !fig[yy * N + xx]) out[yy * N + xx] = OUTLINE
                }
            }
        }
        return out
    }

    private fun drawShoe(
        style: Int, x: Int, y: Int, shoe: Int, shoeD: Int, accent: Int, facingRight: Boolean,
        set: (Int, Int, Int) -> Unit, fill: (Int, Int, Int, Int, Int) -> Unit,
    ) {
        val dir = if (facingRight) 1 else -1
        // Sole
        run {
            val x0 = min(x, x + 8 * dir); val x1 = max(x, x + 8 * dir)
            fill(x0, y + 2, x1, y + 3, BLACK)
        }
        // Body
        when (style.coerceIn(0, 5)) {
            0, 4 -> { // low / speed
                val x0 = min(x, x + 7 * dir); val x1 = max(x, x + 7 * dir)
                fill(x0, y, x1, y + 2, shoe)
                set(x + 6 * dir, y, shoeD)
            }
            1 -> { // mid
                val x0 = min(x, x + 7 * dir); val x1 = max(x, x + 7 * dir)
                fill(x0, y - 1, x1, y + 2, shoe)
                val a0 = min(x, x + 2 * dir); val a1 = max(x, x + 2 * dir)
                fill(a0, y - 1, a1, y, accent)
            }
            2, 3, 5 -> { // high / retro / signature
                val x0 = min(x, x + 7 * dir); val x1 = max(x, x + 7 * dir)
                fill(x0, y - 3, x1, y + 2, shoe)
                val a0 = min(x, x + 3 * dir); val a1 = max(x, x + 3 * dir)
                fill(a0, y - 3, a1, y - 1, accent)
                set(x + 5 * dir, y - 1, accent)
            }
        }
        // Toe highlight
        set(x + (if (facingRight) 6 else -6), y + 1, shoeD)
    }

    private fun drawHair(
        style: Int, hx: Int, hy: Int, rx: Int, ryTop: Int,
        hair: Int, hairD: Int, hairL: Int, skin: Int,
        set: (Int, Int, Int) -> Unit,
        fill: (Int, Int, Int, Int, Int) -> Unit,
        disc: (Int, Int, Int, Int, Int?) -> Unit,
    ) {
        val top = hy - ryTop
        fun scalp(extraTop: Int = 0, down: Int = 3, side: Int = 1, pat: (Int, Int) -> Int = { _, _ -> hair }) {
            for (y in top - extraTop..top + down) for (x in hx - rx - side..hx + rx + side) {
                val onHead = y >= top && abs(x - hx) <= rx + (if (y <= top + 2) side else 0)
                val above = y < top && abs(x - hx) <= rx - (top - y) + 1
                if (onHead || above) set(x, y, pat(x, y))
            }
        }
        when (style) {
            0 -> set(hx - 2, top + 1, shade(skin, 0.1)) // bald highlight
            1 -> scalp(0, 2, 1) { x, y -> if ((x + y) % 2 == 0) hair else mix(hair, skin, 0.4) }
            2 -> {
                scalp(1, 3, 1)
                set(hx - 3, top - 1, hairL); set(hx - 2, top - 1, hairL)
            }
            3 -> {
                scalp(1, 2, 0)
                for (y in top + 2..top + 5) {
                    set(hx - rx, y, mix(hair, skin, 0.45))
                    set(hx + rx, y, mix(hair, skin, 0.45))
                }
                set(hx - 2, top - 1, hairL)
            }
            4 -> { // flat-top
                fill(hx - rx - 1, top - 4, hx + rx + 1, top + 1, hair)
                fill(hx - rx, top - 4, hx + rx, top - 4, hairL)
            }
            5 -> { // afro
                disc(hx, hy - 2, 10, hair, hairD)
                for (i in 0..8) set(hx - 6 + i * 2, hy - 8 + (i % 3), hairL)
            }
            6 -> { // high-top
                fill(hx - rx, top - 7, hx + rx, top + 1, hair)
                fill(hx - rx + 1, top - 7, hx + rx - 1, top - 7, hairL)
                set(hx + rx, top - 3, hairD)
            }
            7 -> { // curls
                disc(hx, hy - 1, 8, hair, hairD)
                scalp(2, 2, 2) { x, y -> if ((x + y) % 3 == 0) hairD else if ((x * 2 + y) % 5 == 0) hairL else hair }
            }
            8 -> scalp(1, 4, 1) { x, _ -> if (x % 2 == 0) hair else mix(hair, skin, 0.55) } // cornrows
            9 -> { // locs
                scalp(1, 3, 2) { x, _ -> if (x % 2 == 0) hairD else hair }
                for (y in hy..hy + 10) for (x in hx - rx - 2..hx + rx + 2 step 2) {
                    if (abs(x - hx) >= rx - 1) set(x, y, if (x % 4 == 0) hairD else hair)
                }
            }
            10 -> { // long
                scalp(1, 3, 2)
                for (y in hy..hy + 12) {
                    set(hx - rx - 1, y, hair); set(hx + rx + 1, y, hairD)
                    if (y % 3 == 0) { set(hx - rx - 2, y, hair); set(hx + rx + 2, y, hair) }
                }
                fill(hx - 1, top, hx + 1, top + 3, hairD) // part
            }
            11 -> { // mohawk
                fill(hx - 2, top - 6, hx + 2, top + 2, hair)
                fill(hx - 1, top - 6, hx + 1, top - 6, hairL)
                for (y in top..top + 3) {
                    set(hx - rx, y, mix(hair, skin, 0.5))
                    set(hx + rx, y, mix(hair, skin, 0.5))
                }
            }
            12 -> scalp(1, 3, 1) { _, y -> if ((y - top) % 2 == 0) hairD else hair } // waves
            13 -> { // man bun
                scalp(1, 2, 1)
                disc(hx, top - 3, 3, hair, hairD)
                set(hx - 1, top - 5, hairL)
            }
        }
    }
}
