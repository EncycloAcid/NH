package com.nh.hoopscoach.ui

import com.nh.hoopscoach.game.Face
import com.nh.hoopscoach.game.Gear
import kotlin.math.abs

/**
 * Retro bit player sprites: the 64×64 dribble pixel art from the reference, customizable by [Face],
 * [Gear], team colors and jersey number. Shown with hard pixel edges on a transparent background.
 */
object CelRender {
    const val N = 64
    private const val OUTLINE = 0xFF0C0A0A.toInt()
    private const val WHITE = 0xFFF8F8F8.toInt()
    private const val BLACK = 0xFF1E1E22.toInt()
    private const val BALL_HI = 0xFFFAD248.toInt()
    private const val BALL = 0xFFF28C24.toInt()
    private const val BALL_MID = 0xFFDA6618.toInt()
    private const val BALL_DARK = 0xFFAC4414.toInt()

    private val SKIN = intArrayOf(
        0xFFF6D7C3.toInt(), 0xFFEBC2A1.toInt(), 0xFFDDA679.toInt(), 0xFFD08852.toInt(),
        0xFFD0744A.toInt(), 0xFF9A5A36.toInt(), 0xFF734126.toInt(), 0xFF4E2B18.toInt(),
    )
    private val HAIR = intArrayOf(
        0xFF2E2A2A.toInt(), 0xFF4A2E1E.toInt(), 0xFF6E4628.toInt(), 0xFFA2562A.toInt(),
        0xFFDAB458.toInt(), 0xFFECE6D4.toInt(), 0xFF8E9096.toInt(), 0xFFC22E2E.toInt(),
    )

    private val DIGITS = listOf(
        "111101101101111", "010110010010111", "111001111100111", "111001111001111", "101101111001001",
        "111100111001111", "111100111101111", "111001010010010", "111101111101111", "111101111001111",
    )

    private val BASE = arrayOf(
        "................................................................",
        "............................######..............................",
        ".........................###HHHHHH##............................",
        "........................#HHHHHHHHHHH##..........................",
        ".......................#HHHHHHHHHHhhhh#.........................",
        "......................#HHHHHhHHhhhHhHhh#........................",
        ".....................#HHHHHHHHHHHHHHHHH#........................",
        ".....................#HHHHHHHHHHHHHHHHH#........................",
        ".....................#HHHH############H#........................",
        ".....................#HHH#ssSSSSSSSSS#H#........................",
        ".....................#HH#ssSSSSSSSSSSS##........................",
        ".....................##H#s#####SSSS##S#.........................",
        "....................#ss##ssssssSSSSW#S#.........................",
        "....................#s#S#ssSW#sSSSSW#S#.........................",
        "....................#ssssssSsssSSSS##S#.........................",
        ".....................#s#sssSsssSSSS##S#.........................",
        "......................##sssSSSSSSSSSSS#.........................",
        "........................#sssSSSSSSSSSS#.........................",
        "........................##ssSS#####SS#..........................",
        "........................##sssSSSSSSS#...........................",
        ".....................####S#ssssssss#............................",
        "...................####TJ#T#sssss###............................",
        "..................#ssss#STSCsssS#.#S#..........######...........",
        ".................#sSSSss#TJ#SSSSC#JTs#........#dbbBBB##.........",
        "................#sSSSSss#TJCsSS#CJJT#s#......#ddmbbBbdB#........",
        "................#sSSssss#TJJCs#CCJJT#s#....###dmmbbBbbBB#.......",
        "..............##sSSSssss#TJJJJJJJJJT#s#....#dbbbbbdbdbBBb#......",
        ".............##SSSss##ss#TJJJJJJJJJT#s#....#dddbbbbdbbBbb#......",
        "............#sSSSss#SS##SJJJJJJJJJJ##sS#..#mmmbbmddbmbdBBB#.....",
        "...........#ssSSss###ssSJJJJJJJJJJJ#ssSS###mmmbbbbddbdBBbb#.....",
        "...........#sSSss#..#jjJJJJJJJJJJJJ#ssSSmm#mmmbbmdbbdbdbbb#.....",
        "...........#SSss#...#jjJJJJJJJJJJJJ##ssSmm#dmmmdbbbmbbbdbm#.....",
        "..........#sSsss#...#jjJJJJJJJJJJJ#.##ssdd#dddmbbbdbbdbbmm#.....",
        "..........#sSss#....#jjJJJJJJJJJJJ#...#sdd##ddmbbbdbbdbbm#......",
        ".........#ssSs#....#jjjJJJJJJJJJJJ#....###.#dddmbdbbbdbmm#......",
        ".........#ssSs#....#jjjJJJJJJJJJJJ#.........#ddmddmmmdmm#.......",
        ".........#ssSs#...#jjjjjjJJJJJJJJJ#..........#dddmmmmdd#........",
        "........#sSS#ss#..#jjjjjjJJJJJJJJ#............#dddmmm##.........",
        "........#sS#.##s#.#j########jjjjj#.............######...........",
        "........#sS#...##.#jjTJJJJJJ#####...............................",
        ".........#s#......#jjTTJJJJJJJJj#...............................",
        "..........##......#jjjTTJJJJJJJJj#..............................",
        "..................#jjjjTTJJJJJJJJJ#.............................",
        "...................#jjjjTTJJJJJJTTT#............................",
        "...................###jjjTTJJJTl#####...........................",
        "...................#j#jjjjTTJT#llllll#..........................",
        "...................##j#jjjjTT#llLLLLL#..........................",
        "...................#jjj#jjjTT#lllLLLLl#.........................",
        "..................#Tjjjj##jjT#llllLLLL#.........................",
        ".................#l#Tjjjjj#jT##lllLLLL#.........................",
        "................#lll#Tjjjjj##..#llllLL##........................",
        "...............#lllLL#Tjjjj#....#llllLL#........................",
        "..............#llLLLLL#TTL#......#lllLL##.......................",
        ".........###.#llLLLLll####.......#llllLL#.......................",
        "........#UU####LLLlll#............##lllL##......................",
        ".......##UU#UU#LLLl##...............#llL###.....................",
        "......#T#UUUuUUUU##.................#lluuUU#####................",
        "......#T#UUUUu###....................##UU#uUUUUU#...............",
        "......#W#UUUUuuu#....................#U##UUUUUU#W#..............",
        ".......#W#UUUuuW#...................#uuUUUUUUU#W#...............",
        ".......#W#UUUUUW#...................#uuuUU####W#................",
        ".......#WW#UUUUu#...................#uuu##.#WW#.................",
        "........#W#WWWWW#....................#uu#.####..................",
        ".........#######......................####......................",
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

    private fun gearCol(i: Int, team: Int, trim: Int) = when (i) {
        1 -> WHITE
        2 -> BLACK
        3 -> trim
        else -> team
    }

    fun render(f: Face, primary: Int, secondary: Int, number: Int, gear: Gear = Gear()): IntArray {
        val g = Array(N) { BASE[it].toCharArray() }
        fun setc(x: Int, y: Int, ch: Char) { if (x in 0 until N && y in 0 until N) g[y][x] = ch }

        // ---------- customize hair silhouette ----------
        val hStyle = f.hair.coerceIn(0, 13)
        if (hStyle == 0) {
            // Bald: strip hair rows to scalp skin, re-outline top.
            for (y in 1..11) for (x in 20..40) if (g[y][x] == 'H' || g[y][x] == 'h') {
                g[y][x] = if (y <= 3) '.' else if (x < 26) 's' else 'S'
            }
        } else if (hStyle == 1) {
            // Buzz: lower crown by 2 px.
            for (y in 1..3) for (x in 20..40) if (g[y][x] != '.') g[y][x] = '.'
        } else if (hStyle == 4 || hStyle == 6) {
            // Flat-top / High-top: squared block top.
            val topY = if (hStyle == 6) 0 else 1
            for (y in topY..7) for (x in 22..38) {
                g[y][x] = if (y == topY && x in 27..35) 'h' else 'H'
            }
        } else if (hStyle == 5 || hStyle == 7) {
            // Afro / Curls: rounded puff around crown.
            val rad = if (hStyle == 5) 10.2 else 8.8
            for (y in 0..14) for (x in 18..42) {
                val d = kotlin.math.hypot((x - 30.0), (y - 6.5) * 1.15)
                if (d <= rad && g[y][x] == '.') g[y][x] = if ((x + y) % 5 == 0) 'h' else 'H'
            }
        } else if (hStyle == 8) {
            // Cornrows: striped rows across scalp.
            for (y in 2..8) for (x in 22..38) if (g[y][x] == 'H' || g[y][x] == 'h') {
                g[y][x] = if (x % 2 == 0) 'H' else 's'
            }
        } else if (hStyle == 9 || hStyle == 10) {
            // Locs / Long: locks hanging behind neck/ear on left.
            for (y in 9..20) for (x in 18..22) {
                if (g[y][x] == '.') g[y][x] = if (hStyle == 9 && (x + y) % 2 == 0) 'h' else 'H'
            }
        } else if (hStyle == 11) {
            // Mohawk: strip sides of crown, tall center crest.
            for (y in 1..6) for (x in 21..39) if (g[y][x] in "Hh#" && abs(x - 30) > 3) g[y][x] = '.'
            for (y in 0..7) for (x in 28..32) g[y][x] = if (y == 0) 'h' else 'H'
        } else if (hStyle == 12) {
            // Waves: alternating horizontal ripple.
            for (y in 3..7) for (x in 22..38) if (g[y][x] == 'H' || g[y][x] == 'h') {
                g[y][x] = if (y % 2 == 0) 'H' else 'h'
            }
        } else if (hStyle == 13) {
            // Man bun: knot at top-back of head.
            for (y in 0..4) for (x in 20..25) g[y][x] = if (y == 1 && x == 23) 'h' else 'H'
        }

        // ---------- eyes / brows / mouth ----------
        if (f.eyes == 1) { // focused: 1px shorter eyes
            setc(29, 12, 's'); setc(35, 12, 'S')
        } else if (f.eyes == 2) { // wide: extra white highlight
            setc(28, 12, 'W'); setc(36, 12, 'W')
        }
        if (f.brows == 1) { // thick brows
            for (x in 26..30) setc(x, 10, '#')
        } else if (f.brows == 2) { // angled fierce brow
            setc(26, 10, '#'); setc(30, 12, '#')
        }
        if (f.mouth == 1) { // smile
            setc(29, 17, '#'); setc(34, 17, '#'); setc(30, 18, 'W'); setc(31, 18, 'W'); setc(32, 18, 'W')
        } else if (f.mouth == 2) { // smirk
            setc(29, 17, '#'); setc(33, 18, 'S')
        }

        // ---------- facial hair ('F' = beard) ----------
        val beard = f.beard.coerceIn(0, 5)
        if (beard == 2 || beard == 3) { // mustache / goatee
            for (x in 29..34) setc(x, 17, 'F')
            if (beard == 3) for (y in 19..20) for (x in 30..33) if (g[y][x] in "Ss#") setc(x, y, 'F')
        } else if (beard == 4) { // chin strap
            for (y in 15..19) for (x in 24..36) if (g[y][x] in "Ss" && (x <= 25 || y >= 19)) setc(x, y, 'F')
        } else if (beard == 5) { // full beard
            for (y in 16..20) for (x in 24..37) if (g[y][x] in "Ss" && !(y == 18 && x in 30..33)) setc(x, y, 'F')
        }

        // ---------- extras (headband / glasses / goggles / earring) ----------
        when (f.acc.coerceIn(0, 5)) {
            1, 2 -> {
                val ch = if (f.acc == 1) 'W' else 'T'
                for (y in 8..9) for (x in 22..38) if (g[y][x] != '.') g[y][x] = ch
            }
            3 -> { // goggles
                for (x in 26..38) { setc(x, 11, '#'); setc(x, 14, '#') }
                for (y in 12..13) { setc(27, y, 'W'); setc(34, y, 'W') }
            }
            4 -> setc(21, 15, 'T') // gold earring on ear
            5 -> { // glasses
                for (x in 26..38) setc(x, 11, '#')
                setc(26, 12, '#'); setc(31, 12, '#'); setc(38, 12, '#')
            }
        }

        // ---------- chest number (up to 2 digits in 26..35, 25..34) ----------
        val digits = number.coerceIn(0, 99).toString()
        if (digits.length == 1) {
            val pat = DIGITS[digits[0] - '0']
            // Scale 3x5 -> 4x8 to match reference's bold chest number.
            for (dy in 0 until 8) for (dx in 0 until 4) {
                val sy = dy * 5 / 8; val sx = dx * 3 / 4
                if (pat[sy * 3 + sx] == '1') setc(28 + dx, 27 + dy, 'N')
            }
        } else {
            var ox = 26
            for (d in digits) {
                val pat = DIGITS[d - '0']
                for (dy in 0 until 6) for (dx in 0 until 3) {
                    val sy = dy * 5 / 6
                    if (pat[sy * 3 + dx] == '1') setc(ox + dx, 28 + dy, 'N')
                }
                ox += 4
            }
        }

        // Re-ensure 4-connected outline '#' around any modified silhouette pixels.
        val snap = Array(N) { g[it].clone() }
        for (y in 0 until N) for (x in 0 until N) {
            val ch = snap[y][x]
            if (ch == '.' || ch == '#') continue
            for ((dx, dy) in listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) {
                val nx = x + dx; val ny = y + dy
                if (nx !in 0 until N || ny !in 0 until N || snap[ny][nx] == '.') {
                    g[y][x] = '#'
                    break
                }
            }
        }

        // ---------- resolve palette ----------
        val skin = SKIN[f.skin.coerceIn(0, 7)]
        val skinD = shade(skin, -0.22)
        val hair = HAIR[f.hairColor.coerceIn(0, 7)]
        val hairHi = if (f.hairColor == 0) 0xFF766052.toInt() else shade(hair, 0.28)
        val beardCol = shade(hair, -0.08)
        val jersey = primary or (0xFF shl 24)
        val jerseyD = shade(jersey, -0.32)
        val trim = secondary or (0xFF shl 24)
        val numCol = when {
            abs(lum(trim) - lum(jersey)) >= 0.2 -> WHITE
            lum(jersey) > 0.55 -> OUTLINE
            else -> WHITE
        }
        val collarCol = when (gear.undershirt) {
            1 -> WHITE
            2 -> BLACK
            3 -> jersey
            else -> WHITE
        }
        val sleeveCol = gearCol(gear.sleeveColor, jersey, trim)
        val sleeveD = shade(sleeveCol, -0.25)
        val wristCol = when (gear.wristbands) {
            1 -> WHITE; 2 -> BLACK; 3 -> trim; else -> 0
        }
        val legBase = when (gear.legs) {
            0 -> skin
            1 -> BLACK
            2 -> WHITE
            else -> if (abs(lum(trim) - lum(jersey)) > 0.15) trim else shade(jersey, -0.35)
        }
        val legDark = if (gear.legs == 0) skinD else shade(legBase, -0.25)
        val shoeCol = if (gear.shoeColor == 0) jersey else gearCol(gear.shoeColor, jersey, trim)
        val shoeDark = shade(shoeCol, -0.32)

        val out = IntArray(N * N)
        for (y in 0 until N) for (x in 0 until N) {
            val ch = g[y][x]
            if (ch == '.') { out[y * N + x] = 0; continue }
            var c = when (ch) {
                '#' -> OUTLINE
                'H' -> hair
                'h' -> hairHi
                'F' -> beardCol
                'S' -> skin
                's' -> skinD
                'J' -> jersey
                'j' -> jerseyD
                'T' -> trim
                'C' -> collarCol
                'N' -> numCol
                'W' -> WHITE
                'L' -> legBase
                'l' -> legDark
                'U' -> shoeCol
                'u' -> shoeDark
                'B' -> BALL_HI
                'b' -> BALL
                'm' -> BALL_MID
                'd' -> BALL_DARK
                else -> OUTLINE
            }
            // Stubble dither on lower face
            if (beard == 1 && y in 15..19 && x in 24..36 && (ch == 'S' || ch == 's') && (x + y) % 2 == 0) {
                c = mix(c, beardCol, 0.45)
            }
            // Arm sleeves: back arm (x<21, y in 23..34) = right arm; front dribble arm (x in 34..41, y in 22..32) = left arm
            if ((ch == 'S' || ch == 's') && y in 22..35) {
                val onBackArm = x < 21 && (gear.sleeve == 2 || gear.sleeve == 3)
                val onFrontArm = x in 34..41 && (gear.sleeve == 1 || gear.sleeve == 3)
                if (onBackArm || onFrontArm) c = if (ch == 's') sleeveD else sleeveCol
            }
            // Wristbands near hands
            if (wristCol != 0 && (ch == 'S' || ch == 's')) {
                if ((x < 16 && y in 35..36) || (x in 36..41 && y in 30..31)) c = wristCol
            }
            out[y * N + x] = c
        }
        return out
    }
}
