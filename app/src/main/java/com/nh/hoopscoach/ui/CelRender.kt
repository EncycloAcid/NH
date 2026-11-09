package com.nh.hoopscoach.ui

import com.nh.hoopscoach.game.Face
import com.nh.hoopscoach.game.Gear
import kotlin.math.PI
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
 * Sports-card portraits: clean athletic illustrations on a soft studio backdrop in his team's
 * colors. Continuous shading (warm key from the upper left, cool fill, a faint team rim), human
 * proportions, readable eyes and hair, a tank top with his number, and a thin silhouette outline.
 * Drawn at twice the output size and shrunk for smooth edges. All original designs; the Face and
 * Gear parts work as before.
 */
object CelRender {
    const val N = 128
    private const val R = 256
    private const val ZOOM = 1.22
    const val INK = 0xFF1C1418.toInt()

    fun render(f: Face, primary: Int, secondary: Int, number: Int, gear: Gear = Gear()): IntArray {
        val sc = CardScene(f, primary, secondary, number, gear)
        val col = IntArray(R * R)
        val mask = BooleanArray(R * R)
        val k = 96.0 / R
        for (y in 0 until R) for (x in 0 until R) {
            // Closer crop: face first, less of the cartoon shoulders.
            sc.sample(48 + ((x + 0.5) * k - 48) / ZOOM, 46 + ((y + 0.5) * k - 46) / ZOOM)
            col[y * R + x] = sc.c
            mask[y * R + x] = sc.figure
        }
        // Soft silhouette edge where the figure meets the backdrop.
        val inked = col.copyOf()
        for (y in 1 until R - 1) for (x in 1 until R - 1) {
            val i = y * R + x
            if (!mask[i]) continue
            var edge = 0
            for (dy in -1..1) for (dx in -1..1) {
                if (dx == 0 && dy == 0) continue
                if (!mask[(y + dy) * R + x + dx]) edge++
            }
            if (edge >= 3) inked[i] = mixInt(col[i], INK, 0.55)
            else if (edge >= 1) inked[i] = mixInt(col[i], INK, 0.28)
        }
        // Down to size, 2 x 2.
        val out = IntArray(N * N)
        for (y in 0 until N) for (x in 0 until N) {
            var r = 0; var g = 0; var b = 0
            for (sy in 0..1) for (sx in 0..1) {
                val c = inked[(y * 2 + sy) * R + x * 2 + sx]
                r += c shr 16 and 0xFF; g += c shr 8 and 0xFF; b += c and 0xFF
            }
            out[y * N + x] = (0xFF shl 24) or ((r / 4) shl 16) or ((g / 4) shl 8) or (b / 4)
        }
        return out
    }

    private fun mixInt(a: Int, b: Int, t: Double): Int {
        fun ch(c: Int, s: Int) = c shr s and 0xFF
        fun one(s: Int) = (ch(a, s) + (ch(b, s) - ch(a, s)) * t).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (one(16) shl 16) or (one(8) shl 8) or one(0)
    }
}

private class CardScene(val f: Face, primary: Int, secondary: Int, val number: Int, val gear: Gear) {
    var c = 0
    var figure = false

    private fun ch(c: Int, s: Int) = (c shr s and 0xFF)
    private fun rgb(r: Double, g: Double, b: Double) =
        (0xFF shl 24) or (r.coerceIn(0.0, 255.0).toInt() shl 16) or (g.coerceIn(0.0, 255.0).toInt() shl 8) or b.coerceIn(0.0, 255.0).toInt()
    private fun mix(a: Int, b: Int, t: Double) = rgb(
        ch(a, 16) + (ch(b, 16) - ch(a, 16)) * t,
        ch(a, 8) + (ch(b, 8) - ch(a, 8)) * t,
        ch(a, 0) + (ch(b, 0) - ch(a, 0)) * t,
    )
    private fun mul(c: Int, r: Double, g: Double, b: Double) = rgb(ch(c, 16) * r, ch(c, 8) * g, ch(c, 0) * b)
    private fun lum(c: Int) = (ch(c, 16) * 0.3 + ch(c, 8) * 0.59 + ch(c, 0) * 0.11) / 255.0
    private fun fr(c: Int) = ch(c, 16) / 255.0
    private fun fg(c: Int) = ch(c, 8) / 255.0
    private fun fb(c: Int) = ch(c, 0) / 255.0

    private val WHITE = 0xFFFFFFFF.toInt()
    private val INK = CelRender.INK

    private val SKIN = intArrayOf(
        0xFFF7DCC8.toInt(), 0xFFEFC4A4.toInt(), 0xFFDFA87A.toInt(), 0xFFC78E5C.toInt(),
        0xFFA97145.toInt(), 0xFF8B5633.toInt(), 0xFF6B3F24.toInt(), 0xFF4C2B19.toInt(),
    )
    private val HAIRS = intArrayOf(
        0xFF1E1C1F.toInt(), 0xFF3C281A.toInt(), 0xFF6A4324.toInt(), 0xFFA45428.toInt(),
        0xFFE0BE5C.toInt(), 0xFFF1ECDC.toInt(), 0xFFA6A8B0.toInt(), 0xFFC53030.toInt(),
    )
    private val skin = SKIN[f.skin.coerceIn(0, 7)]
    private val hair = HAIRS[f.hairColor.coerceIn(0, 7)]
    private val hairSh = if (f.hairColor == 0) 0xFF0E0D10.toInt() else mul(hair, 0.55, 0.52, 0.58)
    private val hairHi = if (f.hairColor == 0) 0xFF5A6070.toInt() else mix(hair, WHITE, 0.38)
    private val team = primary
    private val trim = secondary
    private val seed = f.encode().hashCode()
    private fun gearColor(i: Int) = when (i) { 1 -> 0xFFF2F2F0.toInt(); 2 -> 0xFF24242C.toInt(); 3 -> trim; else -> team }
    private val shirt = when (gear.undershirt) { 1 -> 0xFFF2F2F0.toInt(); 2 -> 0xFF24242C.toInt(); else -> team }
    private val sleeveCol = gearColor(gear.sleeveColor)

    // ---------- lighting ----------
    private val L1 = norm(-0.48, -0.58, 0.66)
    private val L2 = norm(0.72, 0.08, 0.69)
    private val H1 = norm(L1[0], L1[1], L1[2] + 1.0)
    private fun norm(x: Double, y: Double, z: Double): DoubleArray {
        val l = sqrt(x * x + y * y + z * z); return doubleArrayOf(x / l, y / l, z / l)
    }
    private val rimR = 0.35 + fr(team) * 0.45
    private val rimG = 0.35 + fg(team) * 0.45
    private val rimB = 0.4 + fb(team) * 0.4

    /** Soft continuous lighting for a surface of color [base]. */
    private fun lit(base: Int, nx0: Double, ny0: Double, nz0: Double, gloss: Double = 0.18, shin: Double = 28.0, rim: Double = 0.22, ao: Double = 0.0, warm: Double = 0.08): Int {
        val l = sqrt(nx0 * nx0 + ny0 * ny0 + nz0 * nz0).coerceAtLeast(1e-6)
        val nx = nx0 / l; val ny = ny0 / l; val nz = nz0 / l
        val d1 = max(0.0, nx * L1[0] + ny * L1[1] + nz * L1[2])
        val d2 = max(0.0, nx * L2[0] + ny * L2[1] + nz * L2[2])
        val amb = 0.28 * (1 - ao)
        val kr = amb + d1 * 0.95 + d2 * 0.28
        val kg = amb + d1 * 0.9 + d2 * 0.3
        val kb = amb + d1 * 0.84 + d2 * 0.34
        val spec = gloss * max(0.0, nx * H1[0] + ny * H1[1] + nz * H1[2]).pow(shin) * (1 - ao)
        val rimK = rim * (1 - nz).coerceIn(0.0, 1.0).pow(2.2) * (if (nx > -0.2) 1.0 else 0.35)
        val term = warm * exp(-((d1 - 0.2) / 0.18).pow(2))
        return rgb(
            (fr(base) * kr * (1 - ao * 0.5) + spec + rimK * rimR + term * 0.14) * 255,
            (fg(base) * kg * (1 - ao * 0.5) + spec * 0.95 + rimK * rimG + term * 0.08) * 255,
            (fb(base) * kb * (1 - ao * 0.5) + spec * 0.9 + rimK * rimB + term * 0.04) * 255,
        )
    }

    private fun hash(x: Int, y: Int, s: Int): Double {
        var h = x * 374761393 + y * 668265263 + s * 1274126177
        h = (h xor (h ushr 13)) * 1274126177
        return ((h xor (h ushr 16)) and 0x7FFFFFFF) / 2147483647.0
    }
    private fun noise(x: Double, y: Double, s: Int): Double {
        val xi = floor(x).toInt(); val yi = floor(y).toInt(); val fx = x - xi; val fy = y - yi
        val a = hash(xi, yi, s); val b1 = hash(xi + 1, yi, s); val c1 = hash(xi, yi + 1, s); val d1 = hash(xi + 1, yi + 1, s)
        val ux = fx * fx * (3 - 2 * fx); val uy = fy * fy * (3 - 2 * fy)
        return a + (b1 - a) * ux + (c1 - a) * uy + (a - b1 - c1 + d1) * ux * uy
    }
    private fun smooth(e0: Double, e1: Double, v: Double): Double {
        val t = ((v - e0) / (e1 - e0)).coerceIn(0.0, 1.0); return t * t * (3 - 2 * t)
    }
    private fun segDist(px: Double, py: Double, ax: Double, ay: Double, bx: Double, by: Double): Double {
        val vx = bx - ax; val vy = by - ay
        val t = (((px - ax) * vx + (py - ay) * vy) / (vx * vx + vy * vy).coerceAtLeast(1e-9)).coerceIn(0.0, 1.0)
        return hypot(px - ax - vx * t, py - ay - vy * t)
    }

    // ---------- head shape ----------
    // Crown, cheek Y, cheek half-width, jaw half-width, jaw Y, chin Y, chin half-width.
    private val shape = when (f.head.coerceIn(0, 3)) {
        1 -> doubleArrayOf(16.5, 40.0, 19.6, 16.4, 56.0, 65.0, 7.2)   // round
        2 -> doubleArrayOf(16.5, 39.0, 18.8, 17.6, 57.5, 66.0, 9.0)   // square
        3 -> doubleArrayOf(14.0, 39.0, 17.2, 13.4, 57.0, 68.5, 4.4)   // long
        else -> doubleArrayOf(16.5, 39.0, 18.2, 14.2, 55.5, 66.5, 5.4) // oval
    }
    private val crTop = shape[0]; private val cy = shape[1]; private val rx = shape[2]; private val jawW = shape[3]
    private val jawY = shape[4]; private val chinY = shape[5]; private val chinW = shape[6]
    private val eyeY = cy + 2.4
    private val tipY = eyeY + 9.4
    private val mouthY = cy + 16.8

    private fun hw(y: Double, grow: Double = 0.0): Double = when {
        y < crTop - grow -> -1.0
        y <= cy -> (rx + grow) * (1 - ((cy - y) / (cy - crTop + grow + 0.5)).pow(2.35)).coerceAtLeast(0.0).pow(1 / 2.35)
        y <= jawY -> {
            val t = (y - cy) / (jawY - cy)
            rx + (jawW - rx) * t * t * (3 - 2 * t) + grow
        }
        y <= chinY -> {
            val t = (y - jawY) / (chinY - jawY)
            jawW + (chinW - jawW) * t.pow(1.2) + grow
        }
        y <= chinY + grow -> chinW + grow - (y - chinY)
        else -> -1.0
    }
    private fun inHead(x: Double, y: Double, grow: Double = 0.0) = abs(x - 48) <= hw(y, grow)

    private fun headNormal(x: Double, y: Double): Triple<Double, Double, Double> {
        val w = max(hw(y), 0.5)
        val nx = ((x - 48) / (w + 1.2)).coerceIn(-0.97, 0.97)
        val ny = if (y < cy) -((cy - y) / (cy - crTop + 3)).coerceIn(0.0, 0.85)
        else ((y - cy) / (chinY - cy + 4) * 0.85).coerceIn(0.0, 0.85)
        val nz = sqrt((1 - nx * nx - ny * ny).coerceAtLeast(0.08))
        return Triple(nx, ny, nz)
    }

    // ---------- hair ----------
    private val style = f.hair.coerceIn(0, 13)
    private fun hairline(x: Double): Double {
        val dd = abs(x - 48) / rx
        return crTop + 7.2 + dd * dd * 7.5
    }
    private fun pol(x: Double, y: Double, cx: Double, cyy: Double) = atan2(y - cyy, x - cx) to hypot(x - cx, y - cyy)

    private fun hairLit(x: Double, y: Double, cx: Double, cyy: Double, rad: Double, shine: Boolean = true): Int {
        val nx = ((x - cx) / rad).coerceIn(-0.95, 0.95)
        val ny = ((y - cyy) / rad).coerceIn(-0.95, 0.95)
        val nz = sqrt((1 - nx * nx - ny * ny).coerceAtLeast(0.05))
        var col = lit(hair, nx, ny, nz, gloss = 0.18, shin = 26.0, rim = 0.1, warm = 0.0)
        if (shine) {
            val (th, r) = pol(x, y, cx, cyy)
            if (th in -2.7..-1.3 && abs(r - rad * 0.58) < rad * 0.055) col = mix(col, hairHi, 0.38)
        }
        return col
    }

    private fun shortCap(x: Double, y: Double, grow: Double, bang: Double): Boolean {
        if (!inHead(x, y, grow)) return false
        if (inHead(x, y) && y > hairline(x) + bang) return false
        if (y > eyeY - 1) return false
        figure = true
        c = hairLit(x, y, 46.5, crTop + 12, rx + 4)
        // Soft part line for short/fade.
        if (style == 2 && abs(x - 46.5) < 0.35 && y < hairline(48.0) - 1) c = mix(c, hairSh, 0.55)
        return true
    }

    private fun buzz(x: Double, y: Double): Boolean {
        if (!inHead(x, y) || y > hairline(x) + 0.4) return false
        figure = true
        val (nx, ny, nz) = headNormal(x, y)
        val base = mix(skin, hair, 0.55)
        c = lit(base, nx, ny, nz, gloss = 0.08, shin = 16.0, rim = 0.12, warm = 0.05)
        if (hash((x * 3).toInt(), (y * 3).toInt(), seed) > 0.72) c = mix(c, hairSh, 0.18)
        return true
    }

    private fun topBlock(x: Double, y: Double, height: Double, inset: Double, round: Double): Boolean {
        val top = crTop - height
        if (y < top || y > hairline(x) + 0.4) return false
        if (inHead(x, y) && y > hairline(x)) return false
        val join = crTop + 8
        val joinHalf = hw(join) + 0.4
        val half = if (y >= join) hw(y) + 0.4
        else max((rx - inset) + (joinHalf - (rx - inset)) * ((y - top) / (join - top)).coerceIn(0.0, 1.0), hw(y) + 0.4)
        val dx = abs(x - 48)
        if (dx > half) return false
        if (y < top + round && dx > half - round && hypot(dx - (half - round), y - (top + round)) > round) return false
        figure = true
        val nx = ((x - 48) / (half + 2)).coerceIn(-0.95, 0.95)
        c = when {
            y < top + 1.1 -> mix(hair, hairHi, 0.28)
            abs(y - (top + height * 0.32)) < 0.55 && x < 50 -> mix(hairLit(x, y, 46.0, crTop + 8, 22.0), hairHi, 0.2)
            else -> lit(hair, nx, 0.05, sqrt(1 - nx * nx), gloss = 0.14, shin = 22.0, rim = 0.08, warm = 0.0)
        }
        if (y > top + 1.2 && hash((x * 2.1).toInt(), (y * 2.1).toInt(), seed + 3) > 0.88) c = mix(c, hairSh, 0.55)
        return true
    }

    private fun afro(x: Double, y: Double, big: Boolean): Boolean {
        val ac = crTop + (if (big) 13.0 else 14.0)
        val (th, r) = pol(x, y, 48.0, ac)
        val lobes = if (big) 10 else 13
        val base = if (big) 28.5 else rx + 6.2
        val bump = sqrt(abs(sin(th * lobes / 2 + seed % 5))) * (if (big) 2.2 else 1.9)
        val faceOpen = inHead(x, y) && y > hairline(x) + 0.6
        if (r <= base + bump && y < eyeY + (if (big) 18 else 9) && !faceOpen) {
            figure = true
            c = hairLit(x, y, 46.0, ac - 2, base + 2, shine = false)
            if (abs(noise(x * 0.4, y * 0.4, seed + 8) - 0.5) < 0.03) c = mix(c, hairSh, 0.7)
            return true
        }
        return false
    }

    private fun cornrows(x: Double, y: Double): Boolean {
        if (!inHead(x, y, 1.0) || y > hairline(x) + 0.6) return false
        if (inHead(x, y) && y > hairline(x)) return false
        figure = true
        val lane = ((x - 48 + 100) % 4.5)
        val tick = ((y * 1.25 + (if (lane < 2.25) 0.0 else 0.6)) % 1.25) < 0.26
        c = when {
            lane < 0.5 -> mix(hairLit(x, y, 46.0, crTop + 12, 24.0, false), hairSh, 0.75)
            tick && lane in 1.0..3.5 -> mix(hair, hairSh, 0.45)
            else -> hairLit(x, y, 46.0, crTop + 12, 24.0, false)
        }
        return true
    }

    private fun locs(x: Double, y: Double, front: Boolean): Boolean {
        if (front) {
            if (!inHead(x, y, 1.3) || y > hairline(x) + 0.5) return false
            if (inHead(x, y) && y > hairline(x)) return false
            figure = true
            c = hairLit(x, y, 46.0, crTop + 12, 24.0)
            if (((x - 48 + 100) % 4.3) < 0.38 && y > crTop + 3) c = mix(c, hairSh, 0.6)
            // A few locks over the forehead.
            for (k in 0..2) {
                val lx = 48 - 7 + k * 6.5 + (hash(k, 4, seed) - 0.5) * 2
                val end = hairline(lx) + 3.5 + hash(k, 5, seed) * 2.5
                if (y in hairline(lx) - 1..end && abs(x - lx - (y - hairline(lx)) * 0.22) < 1.5 - (y - hairline(lx)).coerceAtLeast(0.0) * 0.1) {
                    c = hairLit(x, y, 46.0, crTop + 12, 24.0, false)
                    return true
                }
            }
            return true
        }
        val col = floor((x - 48 + 100) / 4.3).toInt()
        val inCol = ((x - 48 + 100) % 4.3)
        val end = 71 + hash(col, 1, seed) * 7
        val half = hw(min(y, cy), 4.5) + 1.5 + (y - cy).coerceAtLeast(0.0) * 0.08
        if (y > crTop + 7 && y < end && abs(x - 48) <= half && inCol in 0.25..4.05) {
            figure = true
            val nx = (inCol - 2.15) / 1.9
            c = if (((y + col * 0.7) % 3.1) < 0.38) mix(hair, hairSh, 0.55)
            else lit(hair, nx, 0.1, sqrt((1 - nx * nx).coerceAtLeast(0.1)), gloss = 0.2, shin = 24.0, rim = 0.08, warm = 0.0)
            return true
        }
        return false
    }

    private fun longHair(x: Double, y: Double, front: Boolean): Boolean {
        if (front) {
            val ccy = crTop + 13.5
            if (inHead(x, y, 1.8) && y < hairline(x) + 0.4 && !(inHead(x, y) && y > hairline(x))) {
                figure = true
                c = hairLit(x, y, 46.0, ccy, 25.0)
                if (abs(x - 47.5) < 0.32 && y < hairline(48.0)) c = mix(c, hairSh, 0.55)
                return true
            }
            for (side in intArrayOf(-1, 1)) {
                val sx = 48 + side * (rx - 3.0)
                val top = hairline(sx) - 1.5
                val bottom = eyeY + 8
                if (y in top..bottom) {
                    val t = (y - top) / (bottom - top)
                    val cxl = sx + side * t * 1.6
                    val half = 3.2 * (1 - t * 0.75)
                    if (abs(x - cxl) < half) {
                        figure = true
                        c = hairLit(x, y, 46.0, ccy, 25.0, false)
                        return true
                    }
                }
            }
            return false
        }
        val n = 7
        val wave = sin(y * 0.2 + seed % 3) * 0.75 * smooth(cy, cy + 10, y)
        val xs = x - wave
        val half = hw(min(y, cy), 3.2) + 0.4 + (y - cy).coerceAtLeast(0.0) * 0.06
        if (y > crTop + 7 && abs(xs - 48) <= half) {
            val x0 = 48 - half; val w = 2 * half / n
            val i = floor((xs - x0) / w).toInt().coerceIn(0, n - 1)
            val t = (xs - x0) / w - i
            val end = 72 + hash(i, 6, seed) * 4.5 + 4.0 * (1 - abs(2 * t - 1)).pow(0.7)
            if (y < end) {
                figure = true
                c = hairLit(x, y, 48.0, cy, 26.0, false)
                if ((t < 0.06 || t > 0.94) && y > end - 8) c = mix(c, hairSh, 0.65)
                return true
            }
        }
        return false
    }

    private fun mohawk(x: Double, y: Double): Boolean {
        if (abs(x - 48) < 6.2 && y < hairline(x) + 0.8) {
            val (th, r) = pol(x, y, 48.0, crTop + 11)
            val up = -PI / 2
            var best = 0.0
            for (k in 0 until 5) {
                val ak = up - 0.5 + k * 0.25
                val w = 0.18
                val t = 1 - abs(th - ak) / w
                if (t > 0) best = max(best, (9.5 + 5.5 * sin(PI * k / 4.0)) * t.pow(1.1))
            }
            if (r < 11 + best && th < 0 && !(inHead(x, y) && y > hairline(x))) {
                figure = true
                c = hairLit(x, y, 47.0, crTop + 2, 15.0)
                return true
            }
        }
        // Faded sides.
        if (inHead(x, y) && y < hairline(x) + 0.2 && abs(x - 48) > 6.5) {
            figure = true
            val (nx, ny, nz) = headNormal(x, y)
            c = lit(mix(skin, hair, 0.4), nx, ny, nz, gloss = 0.06, shin = 14.0, rim = 0.1, warm = 0.04)
            return true
        }
        return false
    }

    private fun waves(x: Double, y: Double): Boolean {
        if (!inHead(x, y, 1.2) || y > hairline(x) + 0.5) return false
        if (inHead(x, y) && y > hairline(x)) return false
        figure = true
        c = hairLit(x, y, 46.0, crTop + 12, 24.0, false)
        val ring = hypot(x - 48, (y - (crTop + 3)) * 1.25)
        if ((ring % 2.5) < 0.32 && ring > 2) c = mix(c, hairSh, 0.5)
        return true
    }

    private fun manBun(x: Double, y: Double): Boolean {
        val bc = crTop - 5.0
        if (hypot(x - 48, (y - bc) * 1.08) < 6.5) {
            figure = true
            c = hairLit(x, y, 46.0, bc, 6.5)
            return true
        }
        if (inHead(x, y, 1.4) && y < hairline(x) + 0.4 && !(inHead(x, y) && y > hairline(x))) {
            figure = true
            c = hairLit(x, y, 46.0, crTop + 12, 24.0)
            val ang = atan2(y - bc, x - 48)
            if (((ang * 8.5 + 100) % 1.0) < 0.06 && y > crTop + 2) c = mix(c, hairSh, 0.55)
            return true
        }
        return false
    }

    private fun hairFront(x: Double, y: Double): Boolean = when (style) {
        0 -> false
        1 -> buzz(x, y)
        2 -> shortCap(x, y, 2.2, 1.2)
        3 -> shortCap(x, y, 1.4, 0.2) // fade: tight
        4 -> topBlock(x, y, 6.2, 2.0, 2.3)
        5 -> afro(x, y, true)
        6 -> topBlock(x, y, 10.8, 3.4, 3.6)
        7 -> afro(x, y, false)
        8 -> cornrows(x, y)
        9 -> locs(x, y, front = true)
        10 -> longHair(x, y, front = true)
        11 -> mohawk(x, y)
        12 -> waves(x, y)
        13 -> manBun(x, y)
        else -> false
    }

    private fun hairBack(x: Double, y: Double): Boolean = when (style) {
        5 -> afro(x, y, true)
        7 -> afro(x, y, false)
        9 -> locs(x, y, front = false)
        10 -> longHair(x, y, front = false)
        else -> false
    }

    // ---------- face ----------
    private val eyeColor = when {
        f.skin <= 3 && abs(seed) % 5 == 0 -> 0xFF3E74B0.toInt()
        f.skin <= 3 && abs(seed) % 7 == 1 -> 0xFF4C863A.toInt()
        abs(seed) % 6 == 2 -> 0xFF845628.toInt()
        else -> 0xFF3F2418.toInt()
    }

    private fun head(x: Double, y: Double): Boolean {
        if (!inHead(x, y)) return false
        figure = true
        val (nx, ny, nz) = headNormal(x, y)
        var ao = 0.0
        // Soft eye-socket shade (elliptical, not a box).
        for (side in intArrayOf(-1, 1)) {
            val sx = ((x - (48 + side * 7.2)) / 4.8).pow(2) + ((y - (eyeY - 0.2)) / 3.4).pow(2)
            if (sx < 1.0) ao = max(ao, 0.1 * (1 - sx))
        }
        // Hair shadow on the forehead.
        if (style !in setOf(0, 1) && y > hairline(x) && y < hairline(x) + 2.0) ao = max(ao, 0.14)
        var col = lit(skin, nx, ny, nz, gloss = 0.14, shin = 22.0, rim = 0.2, ao = ao, warm = 0.1)
        // Nose: bridge, tip highlight, nostrils.
        val dx = x - 48
        if (y in eyeY + 2.0..tipY && abs(dx) < 2.8) {
            if (dx in 0.15..2.2 && dx < 0.4 + (y - (eyeY + 2.0)) * 0.28) {
                col = mix(col, mul(skin, 0.72, 0.64, 0.62), 0.7)
            }
            if (dx in -1.6..-0.15 && y in eyeY + 3.0..tipY - 0.5) {
                col = mix(col, mix(skin, WHITE, 0.12), 0.25)
            }
        }
        if (hypot(x - 48.3, y - (tipY + 0.45)) < 1.45) {
            col = lit(skin, 0.12, 0.5, 0.85, gloss = 0.22, shin = 28.0, rim = 0.06, warm = 0.1)
        }
        if (segDist(x, y, 47.2, tipY + 1.15, 46.0, tipY + 0.7) < 0.3) col = mul(skin, 0.58, 0.5, 0.48)
        if (segDist(x, y, 49.4, tipY + 1.15, 50.6, tipY + 0.7) < 0.3) col = mul(skin, 0.58, 0.5, 0.48)
        // Cheekbones catch a little light.
        if (y in eyeY + 3.5..eyeY + 8.0 && abs(abs(dx) - 9.5) < 2.2) {
            col = mix(col, mix(skin, WHITE, 0.1), 0.18)
        }
        // Under-lip and chin.
        if (y in mouthY + 1.3..mouthY + 2.4 && abs(dx) < 1.8) col = mix(col, mul(skin, 0.72, 0.64, 0.62), 0.5)
        if (y > chinY - 1.0) col = mix(col, mul(skin, 0.7, 0.62, 0.6), 0.4)
        c = col
        if (f.beard == 1 && y > eyeY + 8.5 && !(abs(dx) < 6.0 && y in mouthY - 1.8..mouthY + 1.8) &&
            (y > mouthY - 4.5 || hw(y) - abs(dx) < 3.8)
        ) {
            if (hash((x * 2.4).toInt(), (y * 2.4).toInt(), seed + 9) > 0.4) c = mix(c, hairSh, 0.28)
        }
        mouth(x, y, dx)
        for (side in intArrayOf(-1, 1)) { eye(x, y, side); brow(x, y, side) }
        glasses(x, y)
        return true
    }

    private fun mouth(x: Double, y: Double, dx: Double) {
        val mw = if (f.mouth == 1) 5.6 else 4.7
        if (abs(dx) > mw + 0.5) return
        val curve = when (f.mouth.coerceIn(0, 2)) {
            1 -> (dx / mw).pow(2) * 1.1
            2 -> if (dx > 0) -dx * 0.15 else 0.0
            else -> (dx / mw).pow(2) * 0.2
        }
        val ly = mouthY + (if (f.mouth == 1) 0.35 - curve else curve)
        // Upper lip.
        if (abs(dx) < mw && y in ly - 0.85..ly - 0.05) {
            val lip = mix(skin, 0xFFB56870.toInt(), 0.28)
            c = lit(lip, dx / 7, -0.15, 0.9, gloss = 0.16, shin = 24.0, rim = 0.04, warm = 0.04)
        }
        // Mouth line.
        val thick = 0.32 * (1 - (abs(dx) / (mw + 0.4)).pow(4)) + 0.08
        if (abs(y - ly) < thick) c = mix(INK, mul(skin, 0.5, 0.4, 0.4), 0.45)
        // Lower lip.
        if (abs(dx) < mw * 0.9 && y in ly + 0.1..ly + 1.35) {
            val lip = mix(skin, 0xFFC07880.toInt(), 0.34)
            c = lit(lip, dx / 6, 0.45, 0.85, gloss = 0.24, shin = 32.0, rim = 0.04, warm = 0.05)
        }
        if (f.mouth == 1 && abs(abs(dx) - mw) < 0.35 && y in ly - 1.1..ly - 0.15) {
            c = mix(c, mul(skin, 0.7, 0.6, 0.58), 0.4)
        }
    }

    private fun eye(x: Double, y: Double, side: Int) {
        val ex = 48 + side * 7.2
        val u = (x - ex) * side / 4.1
        if (u < -1.05 || u > 1.12) return
        val open = when (f.eyes.coerceIn(0, 2)) { 1 -> 0.78; 2 -> 1.12; else -> 0.95 }
        val baseY = eyeY + 0.15 - (u + 1) / 2 * 0.4
        val uu = u.coerceIn(-1.0, 1.0)
        val top = baseY - 1.65 * open * (1 - uu * uu).pow(0.55)
        val bot = baseY + 1.2 * open * (1 - uu * uu).pow(0.8)
        // Upper lash.
        if (u in -1.0..1.02 && y in top - 0.32..top + 0.06) { c = INK; return }
        // Lower lid.
        if (u in -0.7..1.0 && abs(y - bot) < 0.14) { c = mix(INK, skin, 0.4); return }
        if (u < -1.0 || u > 1.0 || y < top || y > bot) return
        var col = if (y < top + 0.45 * open) 0xFFD5D9E4.toInt() else 0xFFF7F5F2.toInt()
        val ix = ex - side * 0.15; val iy = eyeY + 0.2
        val irx = 1.35; val iry = 1.55 * min(open, 1.0)
        val id = ((x - ix) / irx).pow(2) + ((y - iy) / iry).pow(2)
        if (id < 1) {
            col = when {
                y < iy - 0.3 -> mul(eyeColor, 0.58, 0.58, 0.64)
                y > iy + 0.45 -> mix(eyeColor, WHITE, 0.2)
                else -> eyeColor
            }
            if (((x - ix) / 0.55).pow(2) + ((y - iy) / 0.85).pow(2) < 1) col = 0xFF100C0E.toInt()
            if (hypot(x - (ix - 0.4), y - (iy - 0.45)) < 0.32) col = WHITE
        }
        c = col
    }

    private fun brow(x: Double, y: Double, side: Int) {
        val ex = 48 + side * 7.2
        val u = (x - ex) * side / 4.8
        if (u < -1.0 || u > 1.0) return
        val t = (u + 1) / 2
        val fierce = f.brows == 2
        val inner = eyeY - (if (fierce) 3.2 else 4.1)
        val outer = eyeY - (if (fierce) 5.2 else 4.8)
        val arch = if (fierce) 0.0 else sin(t * PI) * 0.45
        val by = inner + (outer - inner) * t - arch
        val thick = (if (f.brows == 1) 1.25 else 0.88) * (1 - t * 0.48) + 0.1
        if (y in by - thick * 0.35..by + thick * 0.55) {
            c = lit(hairSh, 0.0, -0.2, 0.95, gloss = 0.05, shin = 10.0, rim = 0.05, warm = 0.0)
        }
    }

    private fun glasses(x: Double, y: Double) {
        when (f.acc) {
            3 -> {
                if (abs(x - 48) > 15.8 || y < eyeY - 4.6 || y > eyeY + 3.4) return
                val frame = abs(x - 48) > 14.4 || y < eyeY - 3.8 || y > eyeY + 2.5 || abs(x - 48) < 1.2
                if (frame) { figure = true; c = 0xFF2A2A32.toInt(); return }
                c = mix(c, 0xFF5AAEDC.toInt(), 0.42)
                if (x - y in 1.5..4.5) c = mix(c, WHITE, 0.45)
            }
            5 -> for (side in intArrayOf(-1, 1)) {
                val cx = 48 + side * 7.5
                val ox = abs(x - cx); val oy = abs(y - (eyeY + 0.15))
                if (ox < 4.8 && oy < 3.1 && (ox > 4.15 || oy > 2.45)) { figure = true; c = 0xFF22222A.toInt(); return }
                if (ox <= 4.15 && oy <= 2.45 && x - cx < -1.5 && y < eyeY - 0.6) c = mix(c, WHITE, 0.4)
                if (abs(x - 48) < 2.8 && abs(y - (eyeY - 0.7)) < 0.4) { figure = true; c = 0xFF22222A.toInt() }
            }
        }
    }

    private fun beard(x: Double, y: Double): Boolean {
        if (!inHead(x, y, 0.35)) return false
        val dx = x - 48
        val adx = abs(dx)
        val w = hw(y)
        val inMouth = adx < 4.6 && abs(y - mouthY) < 0.85
        val hit = when (f.beard.coerceIn(0, 5)) {
            2 -> adx < 6.2 && y in mouthY - 3.0 + adx * 0.08..mouthY - 0.85 + (if (adx > 4.8) (adx - 4.8) * 1.4 else 0.0)
            3 -> (adx < 6.0 && y in mouthY - 2.8 + adx * 0.08..mouthY - 0.85) ||
                (adx < 4.3 - (y - mouthY - 2.4) * 0.28 && y in mouthY + 2.4..chinY + 1.0) ||
                (adx in 4.6..6.0 && y in mouthY - 1.3..mouthY + 1.0)
            4 -> (w - adx < 2.2 && y > eyeY + 2.8) || (y > chinY - 3.0 && y < chinY + 0.6)
            5 -> {
                val cheek = eyeY + 9.0 + (1 - adx / 17) * 2.8
                (y > cheek || w - adx < 2.4 && y > eyeY + 2.2) && !(adx < 4.5 && y in mouthY - 0.5..mouthY + 1.6) &&
                    y < chinY + 1.4
            }
            else -> false
        }
        if (!hit || inMouth) return false
        figure = true
        val (nx, _, nz) = headNormal(x, y)
        c = lit(hair, nx, 0.25, nz, gloss = 0.06, shin = 12.0, rim = 0.06, ao = 0.1, warm = 0.0)
        val grain = hash((x * 3.1).toInt(), (y * 3.1).toInt(), seed + 11)
        if (grain > 0.62) c = mix(c, hairSh, 0.4)
        else if (grain < 0.18) c = mix(c, hairHi, 0.12)
        return true
    }

    private fun ears(x: Double, y: Double): Boolean {
        val w = hw(eyeY + 2.5)
        for (side in intArrayOf(-1, 1)) {
            val ex = 48 + side * (w + 1.15)
            val ey = eyeY + 3.6
            val dx = (x - ex) / 2.9; val dy = (y - ey) / 5.4
            if (dx * dx + dy * dy <= 1) {
                figure = true
                c = lit(skin, dx * side, dy * 0.4, sqrt((1 - dx * dx - dy * dy).coerceAtLeast(0.1)), gloss = 0.1, shin = 18.0, rim = 0.12, warm = 0.08)
                val inner = hypot((x - ex + side * 0.35) / 1.4, (y - ey + 0.35) / 3.1)
                if (abs(inner - 1) < 0.14 && (x - ex) * side < 0.55) c = mix(c, mul(skin, 0.7, 0.62, 0.6), 0.55)
                return true
            }
        }
        return false
    }

    private fun earring(x: Double, y: Double): Boolean {
        if (f.acc != 4) return false
        val ex = 48 - (hw(eyeY + 2.5) + 1.5); val ey = eyeY + 9.0
        if (hypot(x - ex, y - ey) > 1.4) return false
        figure = true
        c = if (hypot(x - ex + 0.4, y - ey + 0.4) < 0.55) 0xFFFFF0B8.toInt() else 0xFFE0A830.toInt()
        return true
    }

    private fun headband(x: Double, y: Double): Boolean {
        if (f.acc != 1 && f.acc != 2) return false
        val by = crTop + 6.8
        if (y < by || y > by + 5.0 || !inHead(x, y, 1.8)) return false
        figure = true
        val base = if (f.acc == 1) 0xFFF2F2F0.toInt() else trim
        val nx = ((x - 48) / (hw(y) + 2)).coerceIn(-0.95, 0.95)
        c = lit(base, nx, -0.1, sqrt(1 - nx * nx), gloss = 0.12, shin = 18.0, rim = 0.08, warm = 0.0)
        if (f.acc == 1 && y in by + 2.0..by + 3.0) c = lit(team, nx, -0.1, sqrt(1 - nx * nx), gloss = 0.1, shin = 16.0, rim = 0.05, warm = 0.0)
        return true
    }

    // ---------- body ----------
    // Athletic block digits as polylines in a 4x6 cell.
    private val DIGITS = arrayOf(
        arrayOf(doubleArrayOf(0.0, 0.0, 4.0, 0.0, 4.0, 6.0, 0.0, 6.0, 0.0, 0.0)),
        arrayOf(doubleArrayOf(1.2, 1.0, 2.2, 0.0, 2.2, 6.0)),
        arrayOf(doubleArrayOf(0.0, 0.0, 4.0, 0.0, 4.0, 2.5, 0.0, 6.0, 4.0, 6.0)),
        arrayOf(doubleArrayOf(0.0, 0.0, 4.0, 0.0, 4.0, 6.0, 0.0, 6.0), doubleArrayOf(1.4, 3.0, 4.0, 3.0)),
        arrayOf(doubleArrayOf(0.0, 0.0, 0.0, 3.4, 4.0, 3.4), doubleArrayOf(3.0, 0.0, 3.0, 6.0)),
        arrayOf(doubleArrayOf(4.0, 0.0, 0.0, 0.0, 0.0, 2.8, 4.0, 2.8, 4.0, 6.0, 0.0, 6.0)),
        arrayOf(doubleArrayOf(4.0, 0.0, 0.0, 0.0, 0.0, 6.0, 4.0, 6.0, 4.0, 3.0, 0.0, 3.0)),
        arrayOf(doubleArrayOf(0.0, 0.0, 4.0, 0.0, 1.5, 6.0)),
        arrayOf(doubleArrayOf(0.0, 0.0, 4.0, 0.0, 4.0, 6.0, 0.0, 6.0, 0.0, 0.0), doubleArrayOf(0.0, 3.0, 4.0, 3.0)),
        arrayOf(doubleArrayOf(4.0, 3.0, 0.0, 3.0, 0.0, 0.0, 4.0, 0.0, 4.0, 6.0, 0.0, 6.0)),
    )
    private val digits = number.coerceIn(0, 99).toString()

    private fun numberDist(x: Double, y: Double): Double {
        val u = 1.55
        val gap = 2.0
        val w = digits.length * 4.0 + (digits.length - 1) * gap
        val gx = (x - 48) / u + w / 2; val gy = (y - 77.6) / u
        var best = 99.0
        for ((i, chr) in digits.withIndex()) {
            val ox = i * (4.0 + gap)
            for (line in DIGITS[chr - '0']) {
                var k = 0
                while (k + 3 < line.size) {
                    best = min(best, segDist(gx, gy, ox + line[k], line[k + 1], ox + line[k + 2], line[k + 3]))
                    k += 2
                }
            }
        }
        return best
    }

    private fun trapY(adx: Double): Double = 71.5 + ((adx - 6.5).coerceAtLeast(0.0) / 16.0).coerceAtMost(1.2).pow(1.6) * 6.5

    private fun body(x: Double, y: Double): Boolean {
        val dx = x - 48; val adx = abs(dx); val side = if (dx < 0) -1.0 else 1.0
        // Slimmer neck and athletic — not cartoon — shoulders.
        val half = 6.4 + smooth(70.0, 78.0, y) * 3.0
        val inNeck = y >= jawY - 1.2 && y <= 79 && adx <= half
        val dcx = 23.5; val dcy = 88.0; val dr = 9.2
        val inDelt = hypot(adx - dcx, y - dcy) <= dr
        val inBody = adx <= 25.0 && y >= trapY(adx)
        if (!inNeck && !inDelt && !inBody) return false
        figure = true
        val arm = 14.2 + smooth(74.0, 90.0, y) * 7.0
        val neckHole = (dx / 9.0).pow(2) + ((y - 70.0) / 7.5).pow(2) < 1
        val onJersey = (inBody || inDelt) && adx <= arm && !neckHole && y >= trapY(adx)
        if (onJersey) {
            val edgeArm = arm - adx
            val edgeNeck = sqrt((dx / 8.8).pow(2) + ((y - 70.0) / 7.4).pow(2)) - 1
            val nx = side * (0.1 + adx / 24.0 * 0.35); val ny = -0.25 + (y - 80) / 42
            val nz = sqrt((1 - nx * nx - ny * ny).coerceAtLeast(0.1))
            if (edgeArm < 1.35 || edgeNeck < 0.13) {
                c = lit(trim, nx, ny, nz, gloss = 0.18, shin = 24.0, rim = 0.08, warm = 0.0)
                return true
            }
            if (numberDist(x, y) < 0.58) {
                // Prefer a readable contrast color when trim is too close to the jersey.
                val numCol = if (kotlin.math.abs(lum(trim) - lum(team)) < 0.18) {
                    if (lum(team) > 0.45) INK else WHITE
                } else trim
                c = lit(numCol, nx, ny, nz, gloss = 0.14, shin = 18.0, rim = 0.05, warm = 0.0)
                return true
            }
            c = lit(team, nx, ny, nz, gloss = 0.2, shin = 26.0, rim = 0.1, warm = 0.0)
            if (adx in arm - 4.8..arm - 3.4 && y > 82) c = mix(c, mul(team, 0.72, 0.72, 0.8), 0.5)
            return true
        }
        val crew = 72.8 + (dx / 7.2).pow(2) * 1.5
        // Undershirt only shows in the neck opening — not as shoulder pads.
        val underShirt = gear.undershirt > 0 && neckHole && y >= crew
        val sleeveSide = gear.sleeve == 3 || (gear.sleeve == 1 && dx > 0) || (gear.sleeve == 2 && dx < 0)
        val onSleeve = sleeveSide && inDelt && adx > 19.5 && y > 86.5 - (adx - 19.5) * 0.45
        val nx: Double; val ny: Double
        if (inDelt && (adx > 19.5 || !inBody)) { nx = (adx - dcx) / dr * side; ny = (y - dcy) / dr }
        else if (inNeck && !inBody) { nx = dx / (half + 1.5); ny = 0.1 }
        else { nx = side * (0.08 + adx / 24.0 * 0.28); ny = -0.4 * smooth(trapY(adx) + 4, trapY(adx), y) + (y - 87) / 55 }
        val nz = sqrt((1 - nx * nx - ny * ny).coerceAtLeast(0.1))
        if (onSleeve) {
            c = lit(sleeveCol, nx, ny, nz, gloss = 0.14, shin = 20.0, rim = 0.08, warm = 0.0)
            return true
        }
        if (underShirt) {
            c = lit(shirt, nx, ny, nz, gloss = 0.12, shin = 18.0, rim = 0.06, warm = 0.0)
            if (abs(y - crew - 0.5) < 0.2) c = mul(shirt, 0.72, 0.72, 0.8)
            return true
        }
        val ao = if (inNeck && y < chinY + 3.6 - adx * 0.12) 0.2 else 0.0
        c = lit(skin, nx, ny, nz, gloss = 0.12, shin = 20.0, rim = 0.16, ao = ao, warm = 0.1)
        if (inNeck && y > chinY + 2.2) {
            val mx = 3.6 + (y - chinY) * 0.14
            if (abs(adx - mx) < 0.2 && y < 75.0) c = mix(c, mul(skin, 0.72, 0.65, 0.62), 0.35)
        }
        return true
    }

    // ---------- backdrop ----------
    private fun backdrop(x: Double, y: Double) {
        figure = false
        val cx = 48.0; val cyb = 44.0
        val dist = hypot(x - cx, (y - cyb) * 0.95) / 52.0
        val vignette = smooth(0.15, 1.15, dist)
        val lightTeam = if (lum(team) < 0.25) mix(team, WHITE, 0.35) else mix(team, WHITE, 0.18)
        val darkTeam = mul(team, 0.45, 0.45, 0.52)
        var col = mix(lightTeam, darkTeam, vignette)
        // Soft highlight behind the head.
        val glow = exp(-((hypot(x - 48, y - 40) / 22).pow(2)))
        col = mix(col, mix(team, WHITE, 0.55), glow * 0.28)
        // Subtle vertical wash so it doesn't read as a flat circle.
        col = mix(col, mul(team, 0.55, 0.55, 0.62), ((y - 20) / 90.0).coerceIn(0.0, 0.35) * 0.35)
        c = col
    }

    fun sample(x: Double, y: Double) {
        if (earring(x, y)) return
        if (headband(x, y)) return
        if (hairFront(x, y)) return
        if (beard(x, y)) return
        if (head(x, y)) return
        if (ears(x, y)) return
        if (hairBack(x, y)) return
        if (body(x, y)) return
        backdrop(x, y)
    }
}
