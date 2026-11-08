package com.nh.hoopscoach.ui

import com.nh.hoopscoach.game.Face
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * PS2-era portraits: a little software renderer. The head, ears, neck and shoulders are rounded
 * forms with a low-poly silhouette, lit by a warm key light from the upper left, a cool fill from the
 * right and a rim light behind, with Blinn-Phong highlights for that glossy-skin, shiny-hair,
 * satin-jersey look of 2003 sports games. Features are painted and shaded onto the head: eyes with
 * irises and catchlights, a nose with a lit tip and nostrils, lips with a sheen, brows, facial hair,
 * hair with strand texture and a highlight band. The studio backdrop is in his team's colors.
 * Rendered at 96 px with 2x supersampling, then shown smoothed.
 */
object Ps2Render {
    const val N = 96
    private const val SS = 2

    fun render(f: Face, primary: Int, secondary: Int, number: Int): IntArray {
        val sc = Scene(f, primary, secondary, number)
        val out = IntArray(N * N)
        for (y in 0 until N) for (x in 0 until N) {
            var r = 0.0; var g = 0.0; var b = 0.0
            for (sy in 0 until SS) for (sx in 0 until SS) {
                sc.sample(x + (sx + 0.5) / SS, y + (sy + 0.5) / SS)
                r += sc.r; g += sc.g; b += sc.b
            }
            val k = 1.0 / (SS * SS)
            out[y * N + x] = (0xFF shl 24) or (c8(r * k) shl 16) or (c8(g * k) shl 8) or c8(b * k)
        }
        return out
    }

    private fun c8(v: Double): Int {
        // A touch of filmic roll-off so highlights don't clip flat.
        val t = v / (1 + max(0.0, v - 0.85) * 0.9)
        return (t.coerceIn(0.0, 1.0) * 255 + 0.5).toInt()
    }
}

private class Scene(val f: Face, primary: Int, secondary: Int, val number: Int) {
    var r = 0.0; var g = 0.0; var b = 0.0

    private fun cr(c: Int) = (c shr 16 and 0xFF) / 255.0
    private fun cg(c: Int) = (c shr 8 and 0xFF) / 255.0
    private fun cb(c: Int) = (c and 0xFF) / 255.0

    // ---------- palette ----------
    private val SKIN = intArrayOf(0xFFF3D2BC.toInt(), 0xFFE8BC98.toInt(), 0xFFD9A06F.toInt(), 0xFFC08453.toInt(), 0xFFA0673E.toInt(), 0xFF83502F.toInt(), 0xFF633920.toInt(), 0xFF462615.toInt())
    private val HAIR = intArrayOf(0xFF161414.toInt(), 0xFF3A2416.toInt(), 0xFF6A4325.toInt(), 0xFF9C5527.toInt(), 0xFFD8B15C.toInt(), 0xFFEFE6CC.toInt(), 0xFF9C9C9E.toInt(), 0xFFB8262C.toInt())
    private val skin = SKIN[f.skin.coerceIn(0, 7)]
    private val hair = HAIR[f.hairColor.coerceIn(0, 7)]
    private val team = primary
    private val trim = secondary
    private val seed = f.encode().hashCode()
    private val light = f.skin <= 3

    // ---------- the head's shape ----------
    private val shape = when (f.head.coerceIn(0, 3)) {
        1 -> doubleArrayOf(13.5, 39.0, 20.4, 16.8, 55.5, 64.5, 9.0)
        2 -> doubleArrayOf(13.5, 37.5, 19.5, 18.3, 57.0, 66.0, 11.4)
        3 -> doubleArrayOf(10.5, 37.5, 17.8, 13.2, 58.5, 69.0, 6.6)
        else -> doubleArrayOf(13.5, 37.5, 18.9, 13.8, 55.5, 66.0, 6.6)
    }
    private val crTop = shape[0]; private val cy = shape[1]; private val rx = shape[2]; private val jawW = shape[3]
    private val jawY = shape[4]; private val chinY = shape[5]; private val chinW = shape[6]
    private val eyeY = cy + 1.5
    private val mouthY = cy + 18.5
    private val tipY = eyeY + 11.0

    private fun hwRaw(y: Double, grow: Double): Double = when {
        y < crTop - grow -> -1.0
        y <= cy -> (rx + grow) * (1 - ((cy - y) / (cy - crTop + grow + 0.5)).pow(2.4)).coerceAtLeast(0.0).pow(1 / 2.4)
        y <= jawY -> { val t = (y - cy) / (jawY - cy); rx + (jawW - rx) * t * t * (3 - 2 * t) + grow }
        y <= chinY -> { val t = (y - jawY) / (chinY - jawY); jawW + (chinW - jawW) * t.pow(1.25) + grow }
        y <= chinY + 1.2 + grow -> (chinW - 2.0 + grow).coerceAtLeast(0.0)
        else -> -1.0
    }
    /** The silhouette, in straight segments every 3 px: a low-poly outline. */
    private fun hw(y: Double, grow: Double = 0.0): Double {
        val y0 = floor(y / 3) * 3
        val a = hwRaw(y0, grow); val b2 = hwRaw(y0 + 3, grow)
        if (a < 0 && b2 < 0) return -1.0
        if (a < 0 || b2 < 0) return hwRaw(y, grow)
        return a + (b2 - a) * (y - y0) / 3
    }
    private fun inHead(x: Double, y: Double, grow: Double = 0.0) = abs(x - 48) <= hw(y, grow)

    // ---------- lighting ----------
    private val L1 = norm(-0.5, -0.55, 0.67)
    private val L2 = norm(0.75, 0.05, 0.65)
    private val H1 = norm(L1[0], L1[1], L1[2] + 1)
    private fun norm(x: Double, y: Double, z: Double): DoubleArray { val l = sqrt(x * x + y * y + z * z); return doubleArrayOf(x / l, y / l, z / l) }
    private val rimR = 0.55 + cr(primary) * 0.45; private val rimG = 0.55 + cg(primary) * 0.45; private val rimB = 0.6 + cb(primary) * 0.4

    /** Lights a surface of color (r0, g0, b0) with normal (nx, ny, nz). */
    private fun lit(r0: Double, g0: Double, b0: Double, nx0: Double, ny0: Double, nz0: Double, ks: Double, shin: Double, rim: Double, ao: Double = 0.0, sss: Double = 0.0) {
        val l = sqrt(nx0 * nx0 + ny0 * ny0 + nz0 * nz0).coerceAtLeast(1e-6)
        val nx = nx0 / l; val ny = ny0 / l; val nz = nz0 / l
        val d1 = max(0.0, nx * L1[0] + ny * L1[1] + nz * L1[2])
        val d2 = max(0.0, nx * L2[0] + ny * L2[1] + nz * L2[2])
        val amb = 0.24 * (1 - ao)
        val kr = amb + d1 * 0.98 + d2 * 0.3 * 0.8
        val kg = amb + d1 * 0.94 + d2 * 0.3 * 0.86
        val kb = amb + d1 * 0.88 + d2 * 0.3 * 1.0
        val spec = ks * max(0.0, nx * H1[0] + ny * H1[1] + nz * H1[2]).pow(shin) * (1 - ao)
        val rimK = rim * 0.55 * (1 - nz).coerceIn(0.0, 1.0).pow(2.5) * (if (nx > -0.25) 1.0 else 0.3) * (1 - max(0.0, ny)).pow(2)
        // Light under the skin: the shadow edge glows warm.
        val term = sss * exp(-((d1 - 0.18) / 0.16).pow(2))
        r = r0 * kr * (1 - ao * 0.6) + spec + rimK * rimR + term * 0.16
        g = g0 * kg * (1 - ao * 0.6) + spec + rimK * rimG + term * 0.04
        b = b0 * kb * (1 - ao * 0.6) + spec * 0.97 + rimK * rimB + term * 0.02
    }

    private fun hash(x: Int, y: Int, s: Int): Double { var h = x * 374761393 + y * 668265263 + s * 1274126177; h = (h xor (h ushr 13)) * 1274126177; return ((h xor (h ushr 16)) and 0x7FFFFFFF) / 2147483647.0 }
    private fun noise(x: Double, y: Double, s: Int): Double {
        val xi = floor(x).toInt(); val yi = floor(y).toInt(); val fx = x - xi; val fy = y - yi
        val a = hash(xi, yi, s); val b1 = hash(xi + 1, yi, s); val c = hash(xi, yi + 1, s); val d = hash(xi + 1, yi + 1, s)
        val ux = fx * fx * (3 - 2 * fx); val uy = fy * fy * (3 - 2 * fy)
        return a + (b1 - a) * ux + (c - a) * uy + (a - b1 - c + d) * ux * uy
    }
    private fun smooth(e0: Double, e1: Double, v: Double): Double { val t = ((v - e0) / (e1 - e0)).coerceIn(0.0, 1.0); return t * t * (3 - 2 * t) }

    // ---------- the backdrop ----------
    private fun backdrop(x: Double, y: Double) {
        val d = hypot((x - 48) / 46, (y - 34) / 50)
        val hi = 0.62 * (1 - d).coerceIn(0.0, 1.0)
        val beam = (0.5 + 0.5 * sin((x * 0.55 - y * 0.32))).pow(6) * 0.12 * (1 - y / 96)
        val base = 0.35 + hi + beam
        val tr = cr(team); val tg = cg(team); val tb = cb(team)
        r = tr * base + hi * 0.25 + (1 - base) * 0.02
        g = tg * base + hi * 0.25 + (1 - base) * 0.03
        b = tb * base + hi * 0.28 + (1 - base) * 0.08
        // Corners fall off to night.
        val v = (1 - (d - 0.75).coerceAtLeast(0.0) * 1.4).coerceIn(0.25, 1.0)
        r *= v; g *= v; b *= v
        // Film grain, faint.
        val n = (hash(x.toInt(), y.toInt(), 99) - 0.5) * 0.025
        r += n; g += n; b += n
    }

    // ---------- the body ----------
    /** Athletic block numerals as strokes on a 4 x 6 grid, drawn with round ends. */
    private val DIGITS = arrayOf(
        arrayOf(doubleArrayOf(0.0, 0.0, 4.0, 0.0, 4.0, 6.0, 0.0, 6.0, 0.0, 0.0)),
        arrayOf(doubleArrayOf(0.9, 1.1, 2.2, 0.0, 2.2, 6.0)),
        arrayOf(doubleArrayOf(0.0, 0.0, 4.0, 0.0, 4.0, 2.6, 0.0, 6.0, 4.0, 6.0)),
        arrayOf(doubleArrayOf(0.0, 0.0, 4.0, 0.0, 4.0, 6.0, 0.0, 6.0), doubleArrayOf(1.2, 3.0, 4.0, 3.0)),
        arrayOf(doubleArrayOf(0.0, 0.0, 0.0, 3.6, 4.0, 3.6), doubleArrayOf(3.1, 0.0, 3.1, 6.0)),
        arrayOf(doubleArrayOf(4.0, 0.0, 0.0, 0.0, 0.0, 2.8, 4.0, 2.8, 4.0, 6.0, 0.0, 6.0)),
        arrayOf(doubleArrayOf(4.0, 0.0, 0.0, 0.0, 0.0, 6.0, 4.0, 6.0, 4.0, 3.0, 0.0, 3.0)),
        arrayOf(doubleArrayOf(0.0, 0.0, 4.0, 0.0, 1.6, 6.0)),
        arrayOf(doubleArrayOf(0.0, 0.0, 4.0, 0.0, 4.0, 6.0, 0.0, 6.0, 0.0, 0.0), doubleArrayOf(0.0, 3.0, 4.0, 3.0)),
        arrayOf(doubleArrayOf(4.0, 3.0, 0.0, 3.0, 0.0, 0.0, 4.0, 0.0, 4.0, 6.0, 0.0, 6.0)),
    )
    private val digits = number.coerceIn(0, 99).toString()

    private fun segDist(px: Double, py: Double, ax: Double, ay: Double, bx: Double, by: Double): Double {
        val vx = bx - ax; val vy = by - ay
        val t = (((px - ax) * vx + (py - ay) * vy) / (vx * vx + vy * vy).coerceAtLeast(1e-9)).coerceIn(0.0, 1.0)
        return hypot(px - ax - vx * t, py - ay - vy * t)
    }

    /** Distance in grid units from (x, y) to the number's strokes. */
    private fun numberDist(x: Double, y: Double): Double {
        val u = 1.75 // px per grid unit
        val gap = 2.3
        val w = digits.length * 4 + (digits.length - 1) * gap
        val gx = (x - 48) / u + w / 2; val gy = (y - 79.5) / u
        var best = 99.0
        for ((i, ch) in digits.withIndex()) {
            val ox = i * (4 + gap)
            for (line in DIGITS[ch - '0']) {
                var k = 0
                while (k + 3 < line.size) {
                    best = min(best, segDist(gx, gy, ox + line[k], line[k + 1], ox + line[k + 2], line[k + 3]))
                    k += 2
                }
            }
        }
        return best
    }

    /** Where the trapezius meets the neck and slopes down to the shoulder. */
    private fun trapY(adx: Double): Double = 70.5 + ((adx - 7.5).coerceAtLeast(0.0) / 21.0).coerceAtMost(1.3).pow(1.7) * 9.0

    private fun torso(x: Double, y: Double): Boolean {
        val dx = x - 48; val adx = abs(dx); val side = if (dx < 0) -1.0 else 1.0
        // The deltoids: a ball on each shoulder.
        val dcx = 29.5; val dcy = 88.0; val dr = 11.5
        val dd = hypot(adx - dcx, y - dcy)
        val inDelt = dd <= dr
        val inBody = adx <= 31.0 && y >= trapY(adx)
        if (!inDelt && !inBody) return false
        // The surface's normal: the shoulder ball, the slope of the traps, or the chest.
        val nx: Double; val ny: Double
        if (inDelt && (adx > 25 || !inBody)) {
            nx = (adx - dcx) / dr * side * 0.9 + side * 0.15; ny = (y - dcy) / dr * 0.9
        } else {
            val top = smooth(trapY(adx) + 6, trapY(adx), y)
            nx = side * (0.12 + (adx / 31.0) * 0.35); ny = -0.55 * top + (y - 88) / 60
        }
        val nz = sqrt((1 - nx * nx - ny * ny).coerceAtLeast(0.1))
        // The tank top: straps over the traps, armholes curving out, a round neckline.
        val arm = 16.5 + smooth(74.0, 90.0, y) * 9.5
        val neckHole = (dx / 10.8).pow(2) + ((y - 69.5) / 9.0).pow(2) < 1
        if (adx <= arm && !neckHole) {
            val edgeArm = arm - adx
            val edgeNeck = sqrt((dx / 10.8).pow(2) + ((y - 69.5) / 9.0).pow(2)) - 1
            if (edgeArm < 1.5 || edgeNeck < 0.14) { lit(cr(trim), cg(trim), cb(trim), nx, ny, nz, 0.35, 14.0, 0.5); return true }
            val d = numberDist(x, y)
            if (d < 0.68) { lit(cr(trim), cg(trim), cb(trim), nx, ny, nz, 0.3, 12.0, 0.4); return true }
            if (d < 1.15) { val o = numberOutline; lit(cr(o), cg(o), cb(o), nx, ny, nz, 0.15, 10.0, 0.3); return true }
            // Mesh: tiny holes catch a little less light.
            val mesh = if ((floor(x * 1.4).toInt() + floor(y * 1.4).toInt()) % 2 == 0) 0.96 else 1.0
            // The strap shades the skin next to it; folds where the jersey hangs.
            val fold = exp(-((adx - (arm - 4.5)) / 1.6).pow(2)) * 0.12 * smooth(80.0, 86.0, y)
            lit(cr(team) * mesh, cg(team) * mesh, cb(team) * mesh, nx, ny, nz, 0.3, 10.0, 0.55, ao = fold)
            return true
        }
        // Skin: a little shadow where the jersey and the neck meet it.
        val near = if (adx > arm) exp(-((adx - arm) / 1.4).pow(2)) * 0.25 else 0.0
        val under = if (neckHole) (1 - smooth(70.0, 75.0, y)) * 0.35 else 0.0
        val pit = if (inDelt && inBody && adx in 22.0..27.0) 0.12 else 0.0
        lit(cr(skin), cg(skin), cb(skin), nx, ny, nz, 0.2, 16.0, 0.6, ao = near + under + pit, sss = 1.0)
        return true
    }

    /** The outline around the number: dark on a light trim, light on a dark one. */
    private val numberOutline = run {
        val lum = cr(trim) * 0.3 + cg(trim) * 0.59 + cb(trim) * 0.11
        val tl = cr(team) * 0.3 + cg(team) * 0.59 + cb(team) * 0.11
        when {
            lum > 0.6 && tl < 0.5 -> 0xFF101014.toInt()
            lum > 0.6 -> darken(team, 0.45)
            else -> 0xFFF4F4F2.toInt()
        }
    }
    private fun darken(c: Int, k: Double): Int = (0xFF shl 24) or ((cr(c) * k * 255).toInt() shl 16) or ((cg(c) * k * 255).toInt() shl 8) or (cb(c) * k * 255).toInt()

    private fun neck(x: Double, y: Double): Boolean {
        val dx = x - 48
        if (y < jawY - 2 || y > 80) return false
        val half = 7.8 + smooth(69.0, 78.0, y) * 4.5
        if (abs(dx) > half) return false
        // The jersey sits in front of the neck.
        if (y >= trapY(abs(dx)) && (dx / 10.8).pow(2) + ((y - 69.5) / 9.0).pow(2) >= 1) return false
        val nx = dx / (half + 3); val nz = sqrt((1 - nx * nx).coerceAtLeast(0.1))
        // The head shades the top of the neck.
        val shadow = 1 - smooth(chinY - 1, chinY + 7, y)
        val muscle = exp(-((abs(dx) - 4.2 - (y - chinY) * 0.12) / 1.2).pow(2)) * 0.1 * smooth(chinY + 1, chinY + 6, y)
        val adam = exp(-(dx / 1.4).pow(2) - ((y - (chinY + 5.5)) / 1.6).pow(2)) * 0.12
        lit(cr(skin), cg(skin), cb(skin), nx, 0.15 - adam * 2, nz, 0.12, 14.0, 0.5, ao = shadow * 0.6 + muscle, sss = 1.0)
        return true
    }

    private fun ears(x: Double, y: Double): Boolean {
        val w = hw(eyeY + 3)
        for (side in intArrayOf(-1, 1)) {
            val ex = 48 + side * (w + 1.4)
            val ey = eyeY + 4.5
            val dx = (x - ex) / 3.3; val dy = (y - ey) / 6.4
            val d = dx * dx + dy * dy
            if (d <= 1) {
                val inner = ((x - ex + side * 0.6) / 1.6).pow(2) + ((y - ey) / 3.8).pow(2) < 1
                val nx = side * 0.75 + dx * 0.3; val ny = dy * 0.6
                lit(cr(skin), cg(skin), cb(skin), nx, ny, 0.55, 0.12, 12.0, 0.7, ao = if (inner) 0.45 else 0.1, sss = 1.6)
                return true
            }
        }
        return false
    }

    // ---------- the face ----------
    private val eyeColor = when {
        light && abs(seed) % 5 == 0 -> 0xFF4C7EB0.toInt()
        f.skin <= 3 && abs(seed) % 7 == 1 -> 0xFF62803E.toInt()
        abs(seed) % 6 == 2 -> 0xFF7E5530.toInt()
        else -> 0xFF3A2214.toInt()
    }

    /** The head with everything on it. Returns false if (x, y) isn't on the head. */
    private fun head(x: Double, y: Double): Boolean {
        if (!inHead(x, y)) return false
        val dx = x - 48
        val w = max(hw(y), 1.0)
        var nx = (dx / (w + 1.5)).coerceIn(-0.97, 0.97)
        var ny = if (y < cy) -((cy - y) / (cy - crTop + 3)).coerceIn(0.0, 0.92) else ((y - cy) / (chinY - cy + 6) * 0.75).coerceIn(0.0, 0.85)
        var ao = 0.0
        var sr = cr(skin); var sg = cg(skin); var sb = cb(skin)
        var ks = 0.2; var shin = 16.0

        // Brow ridge and eye sockets.
        for (side in intArrayOf(-1, 1)) {
            val ex = 48 + side * 8.2
            val g = exp(-((x - ex) / 5.0).pow(2) - ((y - eyeY) / 3.6).pow(2))
            ao += g * 0.32
            if (y < eyeY - 3.5 && y > eyeY - 7 && abs(x - ex) < 6) ny -= 0.25
        }
        // Cheekbones catch the light; the hollow below them doesn't.
        ny -= exp(-((abs(dx) - 9) / 3.5).pow(2) - ((y - (eyeY + 6)) / 2.5).pow(2)) * 0.25
        ao += exp(-((abs(dx) - 10.5) / 2.5).pow(2) - ((y - (mouthY - 3)) / 4.5).pow(2)) * 0.12
        // A warm cheek.
        if (light) { val bl = exp(-((abs(dx) - 9.5) / 4.0).pow(2) - ((y - (eyeY + 8)) / 3.5).pow(2)) * 0.07; sr += bl; sg -= bl * 0.4; sb -= bl * 0.4 }

        // The nose: a ridge that turns the surface, a round tip, nostrils, and its shadow below.
        val nw = 1.6 + (y - eyeY).coerceAtLeast(0.0) / 11 * 2.8
        if (y in eyeY + 1..tipY + 1 && abs(dx) < nw + 1.5) {
            val t = (dx / (nw + 1.5)).coerceIn(-1.0, 1.0)
            nx += t * 0.55 * smooth(eyeY + 1, eyeY + 5, y)
            ao += if (dx > 0) 0.08 * smooth(eyeY + 3, tipY, y) else 0.0
        }
        val tdx = (x - 48) / 3.3; val tdy = (y - tipY) / 2.6
        if (tdx * tdx + tdy * tdy < 1) { nx = nx * 0.3 + tdx * 0.7; ny = ny * 0.3 + tdy * 0.6; ks = 0.32; shin = 22.0 }
        for (side in intArrayOf(-1, 1)) {
            val nd = ((x - (48 + side * 2.3)) / 1.2).pow(2) + ((y - (tipY + 1.8)) / 0.75).pow(2)
            if (nd < 1) ao = max(ao, 0.8 - nd * 0.3)
        }
        ao += exp(-((x - 48) / 3.8).pow(2) - ((y - (tipY + 3.3)) / 1.4).pow(2)) * 0.25
        // Smile lines from the nose to the corners of the mouth, and soft shadow under the eyes.
        if (y in tipY - 1..mouthY + 2) {
            val lineX = 5.0 + (y - (tipY - 1)) * 0.32
            ao += exp(-((abs(dx) - lineX) / 0.9).pow(2)) * 0.14 * smooth(tipY - 1, tipY + 1.5, y) * (1 - smooth(mouthY, mouthY + 2, y))
        }
        ao += exp(-((abs(dx) - 8.0) / 3.2).pow(2) - ((y - (eyeY + 3.2)) / 1.1).pow(2)) * 0.1
        // Under the jaw and the bottom lip.
        ao += smooth(chinY - 4, chinY + 1, y) * 0.22
        ao += exp(-((x - 48) / 4.0).pow(2) - ((y - (mouthY + 4.5)) / 1.3).pow(2)) * 0.18

        // The mouth.
        val lipR = sr * 0.82 + 0.1; val lipG = sg * 0.62; val lipB = sb * 0.62
        val curve = when (f.mouth.coerceIn(0, 2)) { 1 -> 0.055; 2 -> 0.0; else -> 0.015 }
        val smirk = if (f.mouth == 2) (dx / 6.5).coerceIn(-1.0, 1.0) * -1.2 + (if (dx > 0) -dx * 0.12 else 0.0) else 0.0
        val lineY = mouthY - curve * dx * dx + smirk
        val mw = if (f.mouth == 1) 7.5 else 6.4
        if (abs(dx) < mw) {
            val edge = 1 - (abs(dx) / mw).pow(3)
            val up = lineY - 2.0 * edge; val low = lineY + 2.6 * edge
            when {
                f.mouth == 1 && y in lineY - 0.6..lineY + 0.9 && abs(dx) < mw - 2 -> { sr = 0.93; sg = 0.9; sb = 0.86; ks = 0.3; shin = 30.0; ao += 0.15 }
                abs(y - lineY) < 0.55 -> { sr *= 0.35; sg *= 0.25; sb *= 0.25 }
                y in up..lineY -> { sr = lipR * 0.92; sg = lipG * 0.92; sb = lipB * 0.92; ny += 0.2 }
                y in lineY..low -> { sr = lipR; sg = lipG; sb = lipB; ny -= 0.25; ks = 0.35; shin = 26.0 }
            }
        }

        // Eyes.
        for (side in intArrayOf(-1, 1)) {
            val ex = 48 + side * 8.2
            val ry = when (f.eyes.coerceIn(0, 2)) { 1 -> 1.45; 2 -> 2.7; else -> 2.15 }
            val ex2 = (x - ex) / 4.0; val ey2 = (y - eyeY) / ry
            val almond = if (abs(ex2) > 1.1 || abs(ey2) > 1.6) 9.0 else ex2 * ex2 + ey2 * ey2 * (1 + 0.25 * (ex2 * side))
            // Upper lid and lashes.
            val lidY = eyeY - ry * sqrt((1 - ex2 * ex2).coerceAtLeast(0.0))
            if (abs(ex2) < 1.08 && y in lidY - 0.95..lidY + 0.1) { sr *= 0.22; sg *= 0.18; sb *= 0.16; ao = 0.0; ks = 0.0; continue }
            if (almond < 1) {
                ao = 0.0; ks = 0.0
                val ix = ex + side * -0.4
                val id = hypot(x - ix, y - (eyeY + 0.2))
                if (id < 1.85) {
                    val t = id / 1.85
                    sr = cr(eyeColor) * (1.15 - t * 0.5); sg = cg(eyeColor) * (1.15 - t * 0.5); sb = cb(eyeColor) * (1.15 - t * 0.5)
                    if (id < 0.8) { sr = 0.04; sg = 0.04; sb = 0.05 }
                    if (hypot(x - (ix - 0.7), y - (eyeY - 0.7)) < 0.55) { r = 1.0; g = 1.0; b = 1.0; return true }
                } else { val corner = 1 - abs(ex2) * 0.22; sr = 0.86 * corner; sg = 0.83 * corner; sb = 0.8 * corner }
                // The lid shades the top of the eye.
                val shade = smooth(lidY + 1.8, lidY, y) * 0.45
                lit(sr * (1 - shade), sg * (1 - shade), sb * (1 - shade), 0.0, 0.0, 1.0, 0.25, 40.0, 0.0)
                return true
            }
            // Brows.
            val bw = if (f.brows == 1) 1.35 else 0.85
            val bt = ((x - ex) * side + 5.5) / 11.0 // 0 inner .. 1 outer
            if (bt in 0.0..1.0) {
                val arch = sin(bt * Math.PI) * 1.4
                val tilt = if (f.brows == 2) (1 - bt) * 1.6 else 0.0
                val by = eyeY - 4.6 - arch + tilt
                if (abs(y - by) < bw * (1 - 0.45 * abs(bt - 0.45))) {
                    val bc = if (f.hairColor in 4..6 && f.hair == 0) 0.6 else 0.85
                    val grain = 0.85 + noise(x * 2.2, y * 1.2, seed) * 0.3
                    sr = cr(hair) * bc * grain; sg = cg(hair) * bc * grain; sb = cb(hair) * bc * grain; ks = 0.1
                }
            }
        }

        // Facial hair.
        val beard = beardAt(x, y, dx)
        if (beard > 0) {
            val grain = 0.75 + noise(x * 2.7, y * 2.7, seed + 5) * 0.5
            val t = beard
            sr = sr * (1 - t) + cr(hair) * grain * t; sg = sg * (1 - t) + cg(hair) * grain * t; sb = sb * (1 - t) + cb(hair) * grain * t
            ks *= (1 - t * 0.8)
        }

        val nz = sqrt((1 - nx * nx - ny * ny).coerceAtLeast(0.15))
        lit(sr, sg, sb, nx, ny, nz, ks, shin, 0.75, ao.coerceIn(0.0, 0.9), sss = if (beard > 0.5) 0.0 else 1.0)
        // Glasses and goggles sit on the face.
        lenses(x, y)
        return true
    }

    /** How much facial hair covers (x, y): 0 none to 1 full. */
    private fun beardAt(x: Double, y: Double, dx: Double): Double {
        val w = hw(y)
        val edgeDist = w - abs(dx)
        val lips = abs(dx) < 6.4 && y in mouthY - 2.2..mouthY + 2.8
        return when (f.beard.coerceIn(0, 5)) {
            1 -> if (y > eyeY + 9 && !lips && (y > mouthY - 5 || edgeDist < 4)) 0.38 * noise(x * 3.1, y * 3.1, seed + 9).let { if (it > 0.45) 1.0 else 0.4 } else 0.0
            2 -> if (abs(dx) < 7.2 && y in mouthY - 4.6..mouthY - 1.8 + (if (abs(dx) > 5.5) 2.5 else 0.0)) 0.95 else 0.0
            3 -> when {
                abs(dx) < 7.2 && y in mouthY - 4.6..mouthY - 1.8 -> 0.95
                abs(dx) < 5.6 - (y - mouthY - 2.8) * 0.22 && y > mouthY + 2.8 && y < chinY + 1.5 -> 0.95
                abs(dx) in 5.6..7.2 && y in mouthY - 2.0..mouthY + 2.0 -> 0.9
                else -> 0.0
            }
            4 -> if ((edgeDist < 3.0 && y > eyeY + 1) || (y > chinY - 3.5 && abs(dx) < w)) 0.95 * smooth(eyeY, eyeY + 4, y) else 0.0
            5 -> {
                val cheek = eyeY + 7.5 + (1 - abs(dx) / 18) * 4 + (noise(x * 0.9, y * 0.2, seed + 3) - 0.5) * 2.2
                if ((y > cheek || edgeDist < 3) && y > eyeY + 2 && !(lips && abs(dx) < 5.6 && y in mouthY - 0.9..mouthY + 2.6)) 0.97 * smooth(cheek - 2.5, cheek + 1.0, y).coerceAtLeast(if (edgeDist < 3 && y > eyeY + 3) 1.0 else 0.0) else 0.0
            }
            else -> 0.0
        }
    }

    private fun lenses(x: Double, y: Double) {
        when (f.acc) {
            3 -> {
                val inBox = abs(x - 48) < 17.5 && y in eyeY - 6.0..eyeY + 4.5
                if (!inBox) return
                val frame = abs(x - 48) > 15.8 || y < eyeY - 4.8 || y > eyeY + 3.3 || abs(x - 48) < 1.4
                if (frame) { lit(0.1, 0.1, 0.12, 0.0, 0.0, 1.0, 0.5, 30.0, 0.4); return }
                val streak = (0.5 + 0.5 * sin((x + y) * 0.6)).pow(8) * 0.35
                r = r * 0.55 + 0.25 * 0.45 + streak; g = g * 0.55 + 0.55 * 0.45 + streak; b = b * 0.55 + 0.75 * 0.45 + streak
            }
            5 -> for (side in intArrayOf(-1, 1)) {
                val cx = 48 + side * 8.2
                val ox = abs(x - cx); val oy = abs(y - (eyeY + 0.3))
                val inRim = ox < 5.6 && oy < 3.8
                val inLens = ox < 4.8 && oy < 3.0
                if (inRim && !inLens) { r = 0.08; g = 0.08; b = 0.09; return }
                if (inLens) { val gl = if (x - cx < -1.5 && y < eyeY - 0.8) 0.22 else 0.0; r += gl; g += gl; b += gl + 0.03; return }
                if (abs(x - 48) < 3.3 && abs(y - (eyeY - 1.0)) < 0.6) { r = 0.08; g = 0.08; b = 0.09 }
            }
        }
    }

    // ---------- hair ----------
    private val style = f.hair.coerceIn(0, 13)
    private fun hairline(x: Double): Double {
        val d = abs(x - 48) / rx
        return crTop + 9.3 + d * d * 9.75 + sin(x * 1.1) * 0.55
    }

    /** Shades hair at (x, y): its shape's normal, strand texture and a glossy band. */
    private fun shadeHair(x: Double, y: Double, cx: Double, cyy: Double, rad: Double, curly: Boolean = false, alpha: Double = 1.0) {
        val nx = ((x - cx) / rad).coerceIn(-0.95, 0.95)
        val ny = ((y - cyy) / rad).coerceIn(-0.95, 0.95)
        val nz = sqrt((1 - nx * nx - ny * ny).coerceAtLeast(0.05))
        val strand = if (curly) noise(x * 1.3, y * 1.3, seed + 2) else noise(x * 2.6, y * 0.35, seed + 2)
        val t = 0.72 + strand * 0.5
        val hr = cr(hair) * t; val hg = cg(hair) * t; val hb = cb(hair) * t
        val dark = f.hairColor <= 1
        lit(hr + (if (dark) 0.025 else 0.0), hg + (if (dark) 0.025 else 0.0), hb + (if (dark) 0.03 else 0.0), nx, ny, nz, if (curly) 0.12 else 0.42, if (curly) 10.0 else 28.0, 0.8)
        // The highlight band across the crown.
        val band = exp(-((ny + 0.45) / 0.14).pow(2)) * (if (curly) 0.06 else 0.16) * (0.6 + strand * 0.8)
        r += band; g += band; b += band * 1.05
        if (alpha < 1.0) { /* blended by the caller */ }
    }

    private fun capRegion(x: Double, y: Double, grow: Double, sideburn: Double = 6.0, sides: Boolean = true): Boolean {
        if (!inHead(x, y, grow)) return false
        val inside = inHead(x, y)
        if (!inside) return y < cy
        if (y < hairline(x)) return true
        if (!sides) return false
        val edge = abs(x - 48) > hw(y) - 3.3
        val limit = eyeY - 6 + (if (abs(x - 48) > hw(y) - 1.8) sideburn else 0.0)
        return edge && y < limit
    }

    /** Hair in front of (or on) the head. Returns true if hit. */
    private fun hairFront(x: Double, y: Double): Boolean {
        val crown = crTop + 12
        when (style) {
            0 -> return false
            1 -> if (capRegion(x, y, 0.4)) { if (inHead(x, y)) { head(x, y); val t = 0.62 * (0.7 + noise(x * 4, y * 4, seed) * 0.6); r = r * (1 - t) + cr(hair) * 0.5 * t; g = g * (1 - t) + cg(hair) * 0.5 * t; b = b * (1 - t) + cb(hair) * 0.5 * t; return true } }
            2 -> if (capRegion(x, y, 3.6) || (y < hairline(x) + 1.6 && inHead(x, y) && noise(x * 0.9, 3.0, seed) > 0.55)) { shadeHair(x, y, 46.0, crown, 24.0); return true }
            3 -> {
                if (capRegion(x, y, 4.2, sides = false) && (abs(x - 48) < hw(y) - 3 || y < crTop + 4)) { shadeHair(x, y, 46.0, crown, 24.0); return true }
                if (inHead(x, y) && abs(x - 48) > hw(y) - 4.5 && y < eyeY - 5 && y > crTop + 3) {
                    head(x, y)
                    val t = (0.75 - (y - crTop - 3) / (eyeY - crTop) * 0.7).coerceIn(0.05, 0.75) * (0.75 + noise(x * 4, y * 4, seed) * 0.5)
                    r = r * (1 - t) + cr(hair) * 0.55 * t; g = g * (1 - t) + cg(hair) * 0.55 * t; b = b * (1 - t) + cb(hair) * 0.55 * t
                    return true
                }
            }
            4 -> {
                val top = crTop - 13
                if (y >= top && y <= crTop + 9 && abs(x - 48) <= rx - 0.8 && !(y > hairline(x) && inHead(x, y))) {
                    if (y < top + 1.2) { lit(cr(hair) * 0.9, cg(hair) * 0.9, cb(hair) * 0.9, 0.0, -0.95, 0.3, 0.35, 20.0, 0.8); return true }
                    shadeHair(x, y, 46.0, crTop + 2, 26.0); return true
                }
                if (capRegion(x, y, 0.3) && inHead(x, y)) { head(x, y); val t = 0.5; r = r * (1 - t) + cr(hair) * 0.5 * t; g = g * (1 - t) + cg(hair) * 0.5 * t; b = b * (1 - t) + cb(hair) * 0.5 * t; return true }
            }
            5 -> if (capRegion(x, y, 2.0)) { shadeHair(x, y, 46.0, crTop + 13, 30.0, curly = true); return true }
            6 -> {
                val top = crTop - 19
                if (y >= top && y <= crTop + 9) {
                    val k = (y - top) / (crTop + 9 - top)
                    val half = rx - 4.5 + k * 3
                    val lean = (1 - k) * 1.8
                    if (abs(x - 48 - lean) <= half && !(y > hairline(x) && inHead(x, y))) {
                        if (y < top + 1.2) { lit(cr(hair) * 0.9, cg(hair) * 0.9, cb(hair) * 0.9, 0.0, -0.95, 0.3, 0.3, 20.0, 0.8); return true }
                        shadeHair(x, y, 46.0, crTop - 2, 24.0, curly = true); return true
                    }
                }
                if (capRegion(x, y, 0.3) && inHead(x, y)) { head(x, y); val t = 0.5; r = r * (1 - t) + cr(hair) * 0.5 * t; g = g * (1 - t) + cg(hair) * 0.5 * t; b = b * (1 - t) + cb(hair) * 0.5 * t; return true }
            }
            7 -> {
                val ang = atan2(y - cy, x - 48)
                val bump = sin(ang * 13 + seed % 7) * 1.5 + sin(ang * 7) * 0.8
                if (inHead(x, y, 6.0 + bump) && y < eyeY - 4 && (y < hairline(x) + 1.5 || !inHead(x, y))) { shadeHair(x, y, 46.0, crown, 26.0, curly = true); return true }
            }
            8 -> if (capRegion(x, y, 1.6, sideburn = 0.0) && y < hairline(x) + 2.2) {
                val lane = ((x - 48 + 100) % 6.0)
                if (lane < 1.3) { if (inHead(x, y)) { head(x, y); r *= 0.7; g *= 0.68; b *= 0.66; return true } }
                val bump = 0.5 + 0.5 * sin(y * 1.6 + (x / 6).toInt())
                val dxl = (lane - 3.65) / 2.4
                lit(cr(hair) * (0.8 + bump * 0.3), cg(hair) * (0.8 + bump * 0.3), cb(hair) * (0.8 + bump * 0.3), dxl, -0.3, sqrt((1 - dxl * dxl).coerceAtLeast(0.1)), 0.3, 18.0, 0.7)
                return true
            }
            9 -> {
                if (capRegion(x, y, 3.2)) { shadeHair(x, y, 46.0, crown, 24.0); return true }
                val col = ((x - 48 + 100) % 4.5)
                if (y > hairline(x) - 1 && y < hairline(x) + 5 + abs((x * 7).toInt() % 5) && abs(x - 48) < 12 && col < 3.2 && inHead(x, y)) {
                    val d = (col - 1.6) / 1.6
                    lit(cr(hair), cg(hair), cb(hair), d, 0.2, sqrt((1 - d * d).coerceAtLeast(0.1)), 0.25, 14.0, 0.6); return true
                }
            }
            10 -> if (capRegion(x, y, 3.0)) {
                shadeHair(x, y, 46.0, crown, 24.0)
                if (abs(x - 47) < 0.6 && y < hairline(48.0)) { r *= 0.55; g *= 0.55; b *= 0.55 }
                return true
            }
            11 -> {
                if (abs(x - 48) < 6 && y >= crTop - 14 && y < hairline(x) + 1) {
                    val spike = crTop - 14 + abs(((x - 42) % 4.0) - 2) * 3
                    if (y >= spike) { shadeHair(x, y, 48.0, crTop + 2, 14.0); return true }
                }
                if (capRegion(x, y, 0.3) && inHead(x, y)) { head(x, y); val t = 0.32; r = r * (1 - t) + cr(hair) * 0.5 * t; g = g * (1 - t) + cg(hair) * 0.5 * t; b = b * (1 - t) + cb(hair) * 0.5 * t; return true }
            }
            12 -> if (capRegion(x, y, 1.6, sideburn = 3.0)) {
                shadeHair(x, y, 46.0, crown, 24.0)
                val ring = hypot(x - 48, (y - (crTop + 4)) * 1.3)
                val wave = 0.5 + 0.5 * sin(ring * 1.25)
                r *= 0.85 + wave * 0.3; g *= 0.85 + wave * 0.3; b *= 0.85 + wave * 0.3
                return true
            }
            13 -> {
                val bc = crTop - 6
                if (hypot(x - 48, (y - bc) * 1.1) < 7.0) { shadeHair(x, y, 47.0, bc, 7.5); return true }
                if (capRegion(x, y, 1.8)) { shadeHair(x, y, 46.0, crown, 24.0); return true }
            }
        }
        return false
    }

    /** Hair behind the head: an afro, locs and long hair. */
    private fun hairBack(x: Double, y: Double): Boolean {
        when (style) {
            5 -> {
                val ac = crTop + 13
                val ang = atan2(y - ac, x - 48)
                val rr = 31 + sin(ang * 11) * 1.4 + sin(ang * 5 + 1) * 0.9
                if (hypot(x - 48, (y - ac) * 1.08) <= rr && y < eyeY + 22) { shadeHair(x, y, 48.0, ac, 33.0, curly = true); return true }
            }
            9 -> {
                val col = floor((x - 48 + 100) / 4.5)
                val inCol = ((x - 48 + 100) % 4.5)
                val end = 76 + hash(col.toInt(), 1, seed) * 10
                val wave = sin(y * 0.5 + col) * 0.5
                val c = inCol + wave
                if (y > crTop + 8 && y < end && abs(x - 48) <= hw(min(y, cy), 5.0) + 2 + (y - cy).coerceAtLeast(0.0) * 0.12 && c in 0.0..3.4) {
                    val d = (c - 1.7) / 1.7
                    val seg = 0.85 + 0.15 * sin(y * 1.4 + col)
                    val tip = smooth(end, end - 2.5, y)
                    lit(cr(hair) * seg, cg(hair) * seg, cb(hair) * seg, d * 0.8 + (x - 48) / 60, 0.1 + (1 - tip) * 0.5, sqrt((1 - d * d * 0.64).coerceAtLeast(0.1)), 0.22, 14.0, 0.7)
                    return true
                }
            }
            10 -> {
                val end = 80 + noise(x * 0.7, 2.0, seed + 4) * 7
                val half = hw(min(y, cy), 5.0) + 1.5 + (y - cy).coerceAtLeast(0.0) * 0.18
                if (y > crTop + 8 && y < end && abs(x - 48) <= half) {
                    shadeHair(x, y, 48.0, cy, 26.0)
                    // Strands thin out toward the ends.
                    val fade = smooth(end - 6, end, y) * 0.35
                    r *= 1 - fade; g *= 1 - fade; b *= 1 - fade
                    return true
                }
            }
        }
        return false
    }

    // ---------- extras that sit over the hair ----------
    private fun headband(x: Double, y: Double): Boolean {
        if (f.acc != 1 && f.acc != 2) return false
        val by = crTop + 7.5
        if (y < by || y > by + 6 || !inHead(x, y, 1.8)) return false
        val base = if (f.acc == 1) 0xFFF2F2F0.toInt() else trim
        val stripe = f.acc == 1 && y > by + 2.6 && y < by + 3.8
        val c = if (stripe) team else base
        val nx = ((x - 48) / (hw(y) + 2)).coerceIn(-0.95, 0.95)
        val rib = 0.94 + 0.06 * sin(x * 2.4)
        lit(cr(c) * rib, cg(c) * rib, cb(c) * rib, nx, -0.15, sqrt((1 - nx * nx).coerceAtLeast(0.05)), 0.12, 8.0, 0.6)
        return true
    }

    private fun earring(x: Double, y: Double): Boolean {
        if (f.acc != 4) return false
        val ex = 48 - (hw(eyeY + 3) + 1.4); val ey = eyeY + 10.6
        val d = hypot(x - ex, y - ey)
        if (d > 1.4) return false
        val nx = (x - ex) / 1.4; val ny = (y - ey) / 1.4
        lit(0.95, 0.76, 0.28, nx, ny, sqrt((1 - nx * nx - ny * ny).coerceAtLeast(0.05)), 0.9, 40.0, 0.4)
        return true
    }

    // ---------- the frame ----------
    fun sample(x: Double, y: Double) {
        if (earring(x, y)) return
        if (headband(x, y)) return
        if (hairFront(x, y)) return
        if (head(x, y)) return
        if (ears(x, y)) return
        if (neck(x, y)) return
        if (hairBack(x, y)) return
        if (torso(x, y)) return
        backdrop(x, y)
    }
}
