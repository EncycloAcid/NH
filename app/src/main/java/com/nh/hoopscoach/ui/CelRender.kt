package com.nh.hoopscoach.ui

import com.nh.hoopscoach.game.Face
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Cel-shaded portraits in the style of 2000s anime fighting games: flat color with one hard-edged
 * shadow tone (and a hard shine on hair), bold ink outlines around every shape, sharp anime eyes, spiky
 * hair, an energy aura in his team's colors and a burst of rays behind him.
 *
 * Drawn in two passes at twice the output size: first every point gets a color and the shape it
 * belongs to, then ink goes along every edge where a shape sits in front of another (thicker around the
 * whole figure), and the result is shrunk down for clean, smooth lines.
 */
object CelRender {
    const val N = 128
    private const val R = 256
    const val INK = 0xFF1A1218.toInt()

    private class Off(val dx: Int, val dy: Int, val d: Double)
    private val OFFS: List<Off> = buildList {
        for (dy in -3..3) for (dx in -3..3) { val d = hypot(dx.toDouble(), dy.toDouble()); if (d in 0.5..2.95) add(Off(dx, dy, d)) }
    }.sortedBy { it.d }

    fun render(f: Face, primary: Int, secondary: Int, number: Int): IntArray {
        val sc = CelScene(f, primary, secondary, number)
        val col = IntArray(R * R)
        val part = IntArray(R * R)
        val k = 96.0 / R
        for (y in 0 until R) for (x in 0 until R) {
            sc.sample((x + 0.5) * k, (y + 0.5) * k)
            col[y * R + x] = sc.c
            part[y * R + x] = sc.part
        }
        // Ink along each shape's edge where it's in front of what's next to it.
        val inked = col.copyOf()
        for (y in 0 until R) for (x in 0 until R) {
            val i = y * R + x
            val p = part[i]
            if (p == Parts.BG) continue
            for (o in OFFS) {
                val xx = x + o.dx; val yy = y + o.dy
                if (xx < 0 || yy < 0 || xx >= R || yy >= R) continue
                val q = part[yy * R + xx]
                if (q >= p) continue
                if (o.d <= Parts.width(p, q)) { inked[i] = INK; break }
            }
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
}

/** Which shape a point belongs to; higher numbers sit in front. Ink goes on the front shape's edge. */
private object Parts {
    const val BG = 0
    const val BODY = 1
    const val JERSEY = 2
    const val TRIM = 3
    const val NUMBER = 4
    const val HAIR_BACK = 5   // 5..14, one per lock
    const val EAR = 15
    const val HEAD = 16
    const val BEARD = 17
    const val HAIR = 20       // 20..59
    const val ACC = 70
    const val EARRING = 71

    fun width(p: Int, q: Int): Double = when {
        q == BG -> 2.9
        p >= HAIR && q >= HAIR -> 1.3
        p in HAIR_BACK..14 && q in HAIR_BACK..14 -> 1.3
        p == BEARD -> 1.5
        p == NUMBER -> 1.9
        else -> 1.8
    }
}

private class CelScene(val f: Face, primary: Int, secondary: Int, val number: Int) {
    /** The point's color and shape. */
    var c = 0
    var part = Parts.BG

    // ---------- color ----------
    private fun ch(c: Int, s: Int) = (c shr s and 0xFF)
    private fun rgb(r: Double, g: Double, b: Double) =
        (0xFF shl 24) or ((r.coerceIn(0.0, 255.0)).toInt() shl 16) or ((g.coerceIn(0.0, 255.0)).toInt() shl 8) or (b.coerceIn(0.0, 255.0)).toInt()
    private fun mul(c: Int, r: Double, g: Double, b: Double) = rgb(ch(c, 16) * r, ch(c, 8) * g, ch(c, 0) * b)
    private fun mix(a: Int, b: Int, t: Double) = rgb(ch(a, 16) + (ch(b, 16) - ch(a, 16)) * t, ch(a, 8) + (ch(b, 8) - ch(a, 8)) * t, ch(a, 0) + (ch(b, 0) - ch(a, 0)) * t)
    private fun lum(c: Int) = (ch(c, 16) * 0.3 + ch(c, 8) * 0.59 + ch(c, 0) * 0.11) / 255.0
    private val WHITE = 0xFFFFFFFF.toInt()
    private val INK = CelRender.INK

    private val SKIN = intArrayOf(0xFFF6D8C3.toInt(), 0xFFEEC3A2.toInt(), 0xFFDDA676.toInt(), 0xFFC68B5B.toInt(), 0xFFA96F44.toInt(), 0xFF8C5634.toInt(), 0xFF6C3F25.toInt(), 0xFF4E2C1A.toInt())
    private val HAIRS = intArrayOf(0xFF2A2A34.toInt(), 0xFF4A2E1C.toInt(), 0xFF7A4A26.toInt(), 0xFFA8532A.toInt(), 0xFFE8C25E.toInt(), 0xFFF2EDDC.toInt(), 0xFFA7A9B2.toInt(), 0xFFC92F2F.toInt())
    private val skin = SKIN[f.skin.coerceIn(0, 7)]
    private val skinSh = mul(skin, 0.8, 0.68, 0.72)
    private val skinDeep = mul(skin, 0.64, 0.52, 0.56)
    private val hair = HAIRS[f.hairColor.coerceIn(0, 7)]
    private val hairSh = if (f.hairColor == 0) 0xFF15151D.toInt() else mul(hair, 0.62, 0.58, 0.66)
    private val hairHi = if (f.hairColor == 0) 0xFF636B80.toInt() else mix(hair, WHITE, 0.45)
    private val team = primary
    private val teamSh = mul(primary, 0.66, 0.66, 0.76)
    private val trim = secondary
    private val trimSh = mul(secondary, 0.7, 0.7, 0.78)
    private val seed = f.encode().hashCode()

    // ---------- light ----------
    private val LX = -0.5; private val LY = -0.6; private val LZ = 0.62
    private val LN = sqrt(LX * LX + LY * LY + LZ * LZ)
    private fun d(nx: Double, ny: Double, nz: Double): Double {
        val l = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-6)
        return (nx * LX + ny * LY + nz * LZ) / (l * LN)
    }
    /** Two tones and a hard edge between them. */
    private fun cel(base: Int, shadow: Int, nx: Double, ny: Double, nz: Double, cut: Double = 0.3): Int = if (d(nx, ny, nz) > cut) base else shadow

    private fun hash(x: Int, y: Int, s: Int): Double { var h = x * 374761393 + y * 668265263 + s * 1274126177; h = (h xor (h ushr 13)) * 1274126177; return ((h xor (h ushr 16)) and 0x7FFFFFFF) / 2147483647.0 }
    private fun noise(x: Double, y: Double, s: Int): Double {
        val xi = floor(x).toInt(); val yi = floor(y).toInt(); val fx = x - xi; val fy = y - yi
        val a = hash(xi, yi, s); val b1 = hash(xi + 1, yi, s); val c1 = hash(xi, yi + 1, s); val d1 = hash(xi + 1, yi + 1, s)
        val ux = fx * fx * (3 - 2 * fx); val uy = fy * fy * (3 - 2 * fy)
        return a + (b1 - a) * ux + (c1 - a) * uy + (a - b1 - c1 + d1) * ux * uy
    }
    private fun smooth(e0: Double, e1: Double, v: Double): Double { val t = ((v - e0) / (e1 - e0)).coerceIn(0.0, 1.0); return t * t * (3 - 2 * t) }
    private fun segDist(px: Double, py: Double, ax: Double, ay: Double, bx: Double, by: Double): Double {
        val vx = bx - ax; val vy = by - ay
        val t = (((px - ax) * vx + (py - ay) * vy) / (vx * vx + vy * vy).coerceAtLeast(1e-9)).coerceIn(0.0, 1.0)
        return hypot(px - ax - vx * t, py - ay - vy * t)
    }

    // ---------- the head: an angular anime jaw ----------
    private val shape = when (f.head.coerceIn(0, 3)) {
        1 -> doubleArrayOf(13.5, 38.5, 19.8, 16.6, 54.5, 64.5, 6.0)
        2 -> doubleArrayOf(13.5, 37.5, 19.0, 18.2, 56.5, 65.5, 9.0)
        3 -> doubleArrayOf(10.5, 37.5, 17.4, 13.4, 55.5, 68.5, 3.6)
        else -> doubleArrayOf(13.5, 37.5, 18.4, 14.6, 54.0, 66.0, 4.0)
    }
    private val crTop = shape[0]; private val cy = shape[1]; private val rx = shape[2]; private val jawW = shape[3]
    private val jawY = shape[4]; private val chinY = shape[5]; private val chinW = shape[6]
    private val eyeY = cy + 1.8
    private val tipY = eyeY + 10.0
    private val mouthY = cy + 18.0

    private fun hw(y: Double, grow: Double = 0.0): Double = when {
        y < crTop - grow -> -1.0
        y <= cy -> (rx + grow) * (1 - ((cy - y) / (cy - crTop + grow + 0.5)).pow(2.4)).coerceAtLeast(0.0).pow(1 / 2.4)
        y <= jawY -> { val t = (y - cy) / (jawY - cy); rx + (jawW - rx) * t * t + grow }
        y <= chinY -> { val t = (y - jawY) / (chinY - jawY); jawW + (chinW - jawW) * t + grow }
        y <= chinY + grow -> chinW + grow - (y - chinY)
        else -> -1.0
    }
    private fun inHead(x: Double, y: Double, grow: Double = 0.0) = abs(x - 48) <= hw(y, grow)

    // ---------- hair ----------
    private val style = f.hair.coerceIn(0, 13)
    private fun hairline(x: Double): Double { val dd = abs(x - 48) / rx; return crTop + 9.5 + dd * dd * 9.0 }
    /** Styles whose hair hangs over the forehead and casts a shadow there. */
    private val casts = style in setOf(2, 3, 4, 6, 7, 8, 9, 10, 12, 13, 5, 11)

    private fun pol(x: Double, y: Double, cx: Double, cyy: Double): Pair<Double, Double> = atan2(y - cyy, x - cx) to hypot(x - cx, y - cyy)

    /** A spiky outline: [n] spikes between angles [a0] and [a1] (radians; -PI/2 is straight up), lengths from [len]. */
    private fun spikes(theta: Double, n: Int, a0: Double, a1: Double, len: (Int) -> Double, sharp: Double = 1.0): Double {
        var best = 0.0
        val step = (a1 - a0) / (n - 1).coerceAtLeast(1)
        for (k in 0 until n) {
            val ak = a0 + step * k + (hash(k, 3, seed) - 0.5) * step * 0.3
            val w = step * 0.62
            val t = 1 - abs(theta - ak) / w
            if (t > 0) best = max(best, len(k) * t.pow(sharp))
        }
        return best
    }

    /** Ink strokes between neighbouring spikes, running from the notch toward the crown. */
    private fun spikeLines(theta: Double, r: Double, base: Double, n: Int, a0: Double, a1: Double, depth: Double): Boolean {
        val step = (a1 - a0) / (n - 1).coerceAtLeast(1)
        for (k in 0 until n - 1) {
            val am = a0 + step * (k + 0.5) + (hash(k, 7, seed) - 0.5) * step * 0.2
            if (abs(theta - am) * r < 0.32 && r in base - depth..base + 1.5) return true
        }
        return false
    }

    /** Bangs: pointed locks hanging over the forehead. Returns the lock index, or -1. */
    private fun bangs(x: Double, y: Double, n: Int, len: Double, sweep: Double): Int {
        val x0 = 48 - rx + 1.5; val x1 = 48 + rx - 1.5
        if (x < x0 || x > x1) return -1
        val w = (x1 - x0) / n
        val i = floor((x - x0) / w).toInt().coerceIn(0, n - 1)
        val t = (x - x0) / w - i
        val peak = (0.5 + sweep).coerceIn(0.15, 0.85)
        val tri = if (t < peak) t / peak else (1 - t) / (1 - peak)
        val l = len * (0.75 + hash(i, 11, seed) * 0.5) * (1 - (abs(x - 48) / rx).pow(2) * 0.6)
        val bottom = hairline(x) - 1.0 + l * tri
        return if (y < bottom && y > crTop) i else -1
    }

    private fun shine(x: Double, y: Double, cx: Double, cyy: Double, rad: Double): Boolean {
        val (th, r) = pol(x, y, cx, cyy)
        val inArc = th in -2.75..-1.35
        return inArc && abs(r - rad * 0.62) < rad * 0.07 && noise(th * 9, 1.0, seed + 5) > 0.35
    }

    private fun hairColor(x: Double, y: Double, cx: Double, cyy: Double, rad: Double, withShine: Boolean = true): Int {
        val nx = ((x - cx) / rad).coerceIn(-0.95, 0.95)
        val ny = ((y - cyy) / rad).coerceIn(-0.95, 0.95)
        val nz = sqrt((1 - nx * nx - ny * ny).coerceAtLeast(0.05))
        if (withShine && shine(x, y, cx, cyy, rad)) return hairHi
        return cel(hair, hairSh, nx, ny, nz, 0.18)
    }

    /** The crown cap with spikes. Returns true when (x, y) is hair. */
    private fun spikyCap(x: Double, y: Double, grow: Double, n: Int, a0: Double, a1: Double, len: (Int) -> Double, bangN: Int, bangLen: Double, sweep: Double, sideTo: Double): Boolean {
        val ccy = crTop + 14
        val (th, r) = pol(x, y, 48.0, ccy)
        // The cap's radius at this angle: the head's outline, grown.
        val ry = ccy - crTop + grow
        val rxx = rx + grow
        val base = 1 / sqrt((cos2(th) / (rxx * rxx)) + (sin2(th) / (ry * ry)))
        val spike = if (th < 0) spikes(th, n, a0, a1, len) else 0.0
        val inShape = r <= base + spike && y < sideTo
        val face = inHead(x, y) && y > hairline(x)
        val bang = if (bangN > 0) bangs(x, y, bangN, bangLen, sweep) else -1
        if (!(inShape && !face) && bang < 0) return false
        part = Parts.HAIR
        c = hairColor(x, y, 46.0, ccy - 2, max(rxx, ry) + 4)
        if (spike > 0 && spikeLines(th, r, base + spike * 0.4, n, a0, a1, 5.0)) c = INK
        // Lines up from the notches between bangs.
        if (bang >= 0 && face) {
            val x0 = 48 - rx + 1.5; val w = (2 * rx - 3) / bangN
            val t = (x - x0) / w - bang
            if ((t < 0.07 || t > 0.93) && y > hairline(x) - 3) c = INK
        }
        return true
    }
    private fun cos2(t: Double) = kotlin.math.cos(t).pow(2)
    private fun sin2(t: Double) = sin(t).pow(2)

    /** A shaved or buzzed look: the scalp tinted with hair, no outline of its own. */
    private fun buzzTint(base: Int, x: Double, y: Double, amt: Double): Int {
        if (!inHead(x, y) || y > hairline(x) + 0.5) return base
        val dots = if (hash((x * 3).toInt(), (y * 3).toInt(), seed) > 0.5) 0.08 else 0.0
        return mix(base, hairSh, (amt + dots).coerceIn(0.0, 1.0))
    }

    private fun hairFront(x: Double, y: Double): Boolean {
        val up = -PI / 2
        when (style) {
            // Spiky: the classic anime look, swept up and back.
            2 -> return spikyCap(x, y, 2.6, 9, -PI + 0.3, -0.3, { k -> 4.0 + 4.0 * sin(PI * k / 8.0) + hash(k, 1, seed) * 1.5 }, 4, 5.0, 0.12, eyeY - 2)
            3 -> {
                if (spikyCap(x, y, 1.6, 7, up - 1.0, up + 1.0, { 2.0 + hash(it, 1, seed) * 1.2 }, 0, 0.0, 0.0, crTop + 9.5)) return true
            }
            4 -> {
                val top = crTop - 10
                if (y >= top && abs(x - 48) <= rx - 1.0 && y < hairline(x) + 0.5 && !(inHead(x, y) && y > hairline(x))) {
                    val corner = y < top + 2 && abs(x - 48) > rx - 3 && hypot(abs(x - 48) - (rx - 3), y - (top + 2)) > 2
                    if (!corner) {
                        part = Parts.HAIR
                        val nx = ((x - 48) / rx).coerceIn(-0.9, 0.9)
                        c = when {
                            y < top + 1.6 -> hairHi
                            abs(y - (top + 5)) < 0.7 && x < 48 -> hairHi
                            else -> cel(hair, hairSh, nx, 0.0, sqrt(1 - nx * nx), 0.2)
                        }
                        return true
                    }
                }
            }
            6 -> {
                val top = crTop - 18
                if (y >= top && y < hairline(x) + 0.5 && !(inHead(x, y) && y > hairline(x))) {
                    val k = ((y - top) / (crTop + 8 - top)).coerceIn(0.0, 1.0)
                    val half = rx - 4.2 + k * 3.2
                    val scallop = if (y < top + 3) sin((x - 48) * 0.9).let { abs(it) } * 1.6 else 0.0
                    if (abs(x - 48 - (1 - k) * 1.4) <= half && y >= top + scallop) {
                        part = Parts.HAIR
                        c = hairColor(x, y, 44.0, top + 6, 22.0)
                        return true
                    }
                }
            }
            5, 7 -> {
                // Only the part over the forehead is in front; the rest of the cloud sits behind.
                if (inHead(x, y) && y < hairline(x) + 1) {
                    part = Parts.HAIR
                    c = hairColor(x, y, 46.0, crTop + 12, 30.0, withShine = false)
                    return true
                }
            }
            8 -> {
                if (inHead(x, y, 1.0) && y < hairline(x) + 1 && !(inHead(x, y) && y > hairline(x))) {
                    part = Parts.HAIR
                    val lane = ((x - 48 + 100) % 4.6)
                    val tick = ((y * 1.3 + (if (lane < 2.3) 0.0 else 0.65)) % 1.3) < 0.28
                    c = when {
                        lane < 0.55 -> INK
                        tick && lane in 1.0..3.6 -> hairSh
                        else -> hairColor(x, y, 46.0, crTop + 12, 26.0, withShine = false)
                    }
                    return true
                }
            }
            9 -> {
                if (inHead(x, y, 1.4) && y < hairline(x) + 0.8 && !(inHead(x, y) && y > hairline(x))) {
                    part = Parts.HAIR
                    c = hairColor(x, y, 46.0, crTop + 12, 26.0)
                    if (((x - 48 + 100) % 4.4) < 0.4 && y > crTop + 3) c = INK
                    return true
                }
                // A few locks fall over the forehead.
                for (k in 0..2) {
                    val lx = 48 - 7 + k * 6.5 + (hash(k, 4, seed) - 0.5) * 2
                    val end = hairline(lx) + 4 + hash(k, 5, seed) * 3
                    if (y in hairline(lx) - 1..end && abs(x - lx - (y - hairline(lx)) * 0.25) < 1.6 - (y - hairline(lx)).coerceAtLeast(0.0) * 0.12) {
                        part = Parts.HAIR + 1 + k
                        c = hairColor(x, y, 46.0, crTop + 12, 26.0, withShine = false)
                        return true
                    }
                }
            }
            10 -> {
                // A center part and two long locks framing the face.
                val ccy = crTop + 14
                if (inHead(x, y, 2.0) && y < hairline(x) + 0.5 && !(inHead(x, y) && y > hairline(x))) {
                    part = Parts.HAIR
                    c = hairColor(x, y, 46.0, ccy, 26.0)
                    if (abs(x - 47.5) < 0.35 && y < hairline(48.0)) c = INK
                    return true
                }
                for (side in intArrayOf(-1, 1)) {
                    val sx = 48 + side * (rx - 3.2)
                    val top = hairline(sx) - 2
                    val bottom = eyeY + 9
                    if (y in top..bottom) {
                        val t = (y - top) / (bottom - top)
                        val cxl = sx + side * t * 1.8
                        val half = 3.4 * (1 - t * 0.8)
                        if (abs(x - cxl) < half) {
                            part = Parts.HAIR + (if (side < 0) 1 else 2)
                            c = hairColor(x, y, 46.0, ccy, 26.0, withShine = false)
                            return true
                        }
                    }
                }
            }
            11 -> {
                if (abs(x - 48) < 6.5 && y < hairline(x) + 1) {
                    val (th, r) = pol(x, y, 48.0, crTop + 12)
                    val len = spikes(th, 5, up - 0.55, up + 0.55, { 10.0 + 6.0 * sin(PI * it / 4.0) })
                    if (r < 12 + len && th < 0 && !(inHead(x, y) && y > hairline(x))) {
                        part = Parts.HAIR
                        c = hairColor(x, y, 47.0, crTop + 2, 16.0)
                        if (spikeLines(th, r, 12 + len * 0.4, 5, up - 0.55, up + 0.55, 6.0)) c = INK
                        return true
                    }
                }
            }
            12 -> {
                if (inHead(x, y, 1.4) && y < hairline(x) + 0.8 && !(inHead(x, y) && y > hairline(x))) {
                    part = Parts.HAIR
                    c = hairColor(x, y, 46.0, crTop + 12, 26.0, withShine = false)
                    val ring = hypot(x - 48, (y - (crTop + 3)) * 1.3)
                    if ((ring % 2.6) < 0.35 && ring > 2) c = hairSh
                    return true
                }
            }
            13 -> {
                val bc = crTop - 5.5
                if (hypot(x - 48, (y - bc) * 1.08) < 6.8) {
                    part = Parts.HAIR + 1
                    c = hairColor(x, y, 46.0, bc, 7.0)
                    return true
                }
                if (inHead(x, y, 1.6) && y < hairline(x) + 0.5 && !(inHead(x, y) && y > hairline(x))) {
                    part = Parts.HAIR
                    c = hairColor(x, y, 46.0, crTop + 12, 26.0)
                    // Combed back: lines toward the bun.
                    val ang = atan2(y - bc, x - 48)
                    if (((ang * 9 + 100) % 1.0) < 0.07 && y > crTop + 2) c = INK
                    return true
                }
            }
        }
        return false
    }

    /** Hair behind the head: the afro and curls, locs and long hair. */
    private fun hairBack(x: Double, y: Double): Boolean {
        when (style) {
            5, 7 -> {
                val big = style == 5
                val ac = crTop + (if (big) 13 else 14)
                val (th, r) = pol(x, y, 48.0, ac)
                val lobes = if (big) 11 else 14
                val base = if (big) 29.0 else rx + 6.5
                val bump = sqrt(abs(sin(th * lobes / 2 + seed % 5))) * (if (big) 2.4 else 2.0)
                if (r <= base + bump && y < eyeY + (if (big) 20 else 10)) {
                    part = Parts.HAIR_BACK
                    c = hairColor(x, y, 46.0, ac - 3, base + 2, withShine = false)
                    // Curl marks.
                    val cell = noise(x * 0.45, y * 0.45, seed + 8)
                    if (abs(cell - 0.5) < 0.025) c = INK
                    return true
                }
            }
            9 -> {
                val col = floor((x - 48 + 100) / 4.4).toInt()
                val inCol = ((x - 48 + 100) % 4.4)
                val end = 72 + hash(col, 1, seed) * 8
                val half = hw(min(y, cy), 5.0) + 2 + (y - cy).coerceAtLeast(0.0) * 0.1
                if (y > crTop + 8 && y < end && abs(x - 48) <= half && inCol in 0.3..4.1) {
                    part = Parts.HAIR_BACK + (col.mod(9))
                    val nx = (inCol - 2.2) / 1.9
                    c = if (((y + col * 0.7) % 3.2) < 0.4) hairSh else cel(hair, hairSh, nx, 0.1, sqrt((1 - nx * nx).coerceAtLeast(0.1)), 0.15)
                    return true
                }
            }
            10 -> {
                val n = 8
                val half = hw(min(y, cy), 3.5) + 0.5 + (y - cy).coerceAtLeast(0.0) * 0.07
                if (y > crTop + 8 && abs(x - 48) <= half) {
                    val x0 = 48 - half; val w = 2 * half / n
                    val i = floor((x - x0) / w).toInt().coerceIn(0, n - 1)
                    val t = (x - x0) / w - i
                    val end = 72 + hash(i, 6, seed) * 5 + 4 * (1 - abs(2 * t - 1))
                    if (y < end) {
                        part = Parts.HAIR_BACK + (i % 2)
                        c = hairColor(x, y, 48.0, cy, 28.0, withShine = false)
                        return true
                    }
                }
            }
        }
        return false
    }

    // ---------- the face ----------
    private val eyeColor = when {
        f.skin <= 3 && abs(seed) % 5 == 0 -> 0xFF3F78B8.toInt()
        f.skin <= 3 && abs(seed) % 7 == 1 -> 0xFF4F8A3C.toInt()
        abs(seed) % 6 == 2 -> 0xFF8A5A2B.toInt()
        else -> 0xFF4A2A1A.toInt()
    }

    private fun head(x: Double, y: Double): Boolean {
        if (!inHead(x, y)) return false
        part = Parts.HEAD
        val dx = x - 48
        val w = max(hw(y), 1.0)
        val nx = (dx / (w + 1.5)).coerceIn(-0.97, 0.97)
        val ny = if (y < cy) -((cy - y) / (cy - crTop + 3)).coerceIn(0.0, 0.9) else ((y - cy) / (chinY - cy + 4) * 0.9).coerceIn(0.0, 0.9)
        val nz = sqrt((1 - nx * nx - ny * ny).coerceAtLeast(0.12))
        var col = cel(skin, skinSh, nx, ny * 0.2, nz, 0.24)
        // The hair's shadow across the forehead.
        if (casts && y > hairline(x) && y < hairline(x) + 2.4 + sin(x * 0.9) * 0.6) col = skinSh
        if (style == 1) col = buzzTint(col, x, y, 0.62)
        if (style == 3 || style == 11 || style == 4) col = buzzTint(col, x, y, 0.45)
        // The nose: a shadow down the right of the bridge, the tip and a nostril.
        if (y in eyeY + 3..tipY + 0.4 && dx in 0.0..2.6 && dx < 0.4 + (y - (eyeY + 3)) * 0.27) col = skinSh
        if (segDist(x, y, 48.6, tipY + 1.0, 50.2, tipY + 0.6) < 0.32) col = mul(skinDeep, 0.85, 0.85, 0.85)
        if (y in tipY + 0.9..tipY + 2.0 && dx in -2.0..2.4 && abs(dx - 0.2) < 2.2 - (y - tipY - 0.9) * 1.2) col = if (dx > -0.6) skinSh else col
        // Shadow under the lower lip and in the neck's direction under the jaw.
        if (y in mouthY + 1.6..mouthY + 2.4 && abs(dx) < 1.8) col = skinSh
        if (y > chinY - 1.2) col = skinSh
        c = col
        // Stubble.
        if (f.beard == 1 && y > eyeY + 9 && !(abs(dx) < 6.4 && y in mouthY - 2.0..mouthY + 2.0) && (y > mouthY - 5 || w - abs(dx) < 4)) c = mix(c, hairSh, 0.28)
        mouth(x, y, dx)
        for (side in intArrayOf(-1, 1)) { eye(x, y, side); brow(x, y, side) }
        glasses(x, y)
        return true
    }

    private fun adxm(dx: Double) = abs(dx)

    private fun mouth(x: Double, y: Double, dx: Double) {
        val mw = if (f.mouth == 1) 6.2 else 5.2
        if (abs(dx) > mw + 0.5) return
        val curve = when (f.mouth.coerceIn(0, 2)) {
            1 -> (dx / mw).pow(2) * 1.2   // corners up
            2 -> (if (dx > 0) -dx * 0.16 else 0.0)
            else -> 0.0
        }
        val ly = mouthY + (if (f.mouth == 1) 0.5 - curve else curve)
        val thick = 0.42 * (1 - (abs(dx) / (mw + 0.5)).pow(4)) + 0.12
        if (abs(y - ly) < thick) c = INK
        // Smile: the corners tuck in with a short tick.
        if (f.mouth == 1 && abs(adxm(dx) - mw) < 0.3 && y in ly - 1.3..ly - 0.2) c = INK
    }

    private fun eye(x: Double, y: Double, side: Int) {
        val ex = 48 + side * 8.0
        val u = (x - ex) * side / 4.7   // -1 inner .. 1 outer
        if (u < -1.05 || u > 1.35) return
        val open = when (f.eyes.coerceIn(0, 2)) { 1 -> 0.72; 2 -> 1.25; else -> 1.0 }
        val baseY = eyeY + 0.35 - (u + 1) / 2 * 1.2
        val uu = u.coerceIn(-1.0, 1.0)
        val top = baseY - 2.3 * open * (1 - uu * uu).pow(0.55) * (1 - 0.18 * uu)
        val bot = baseY + 1.25 * open * (1 - uu * uu).pow(0.85)
        // Upper lash line: thick, thicker at the outer corner, with a sharp flick past it.
        val lash = 0.5 + 0.4 * ((u + 1) / 2).coerceIn(0.0, 1.0)
        if (u <= 1.0 && y in top - lash..top + 0.15) { c = INK; return }
        if (u in 1.0..1.35 && abs(y - (baseY - 0.3 - (u - 1) * 1.6)) < 0.42 * (1.35 - u) / 0.35 + 0.05) { c = INK; return }
        // Lower lid: a thin line on the outer half.
        if (u in 0.1..1.0 && abs(y - bot) < 0.22) { c = INK; return }
        if (u < -1.0 || u > 1.0 || y < top || y > bot) return
        // The white, shaded under the lid.
        var col = if (y < top + 0.9 * open) 0xFFC5C9D6.toInt() else 0xFFF4F2EF.toInt()
        val ix = ex - side * 0.35; val iy = eyeY + 0.15
        val irx = 1.45; val iry = 2.05 * min(open, 1.1)
        val id = ((x - ix) / irx).pow(2) + ((y - iy) / iry).pow(2)
        if (id < 1) {
            col = if (y < iy - 0.3) mul(eyeColor, 0.55, 0.55, 0.6) else eyeColor
            if (((x - ix) / 0.62).pow(2) + ((y - iy) / 1.05).pow(2) < 1) col = 0xFF0E0A0C.toInt()
            if (id > 0.82) col = mul(eyeColor, 0.45, 0.45, 0.5)
            if (hypot(x - (ix - 0.55), y - (iy - 0.75)) < 0.45) col = WHITE
            if (hypot(x - (ix + 0.5), y - (iy + 0.7)) < 0.22) col = WHITE
        }
        c = col
    }

    private fun brow(x: Double, y: Double, side: Int) {
        val ex = 48 + side * 8.0
        val u = (x - ex) * side / 5.4  // -1 inner .. 1 outer
        if (u < -1.0 || u > 1.05) return
        val t = (u + 1) / 2
        val fierce = f.brows == 2
        val inner = eyeY - (if (fierce) 3.4 else 4.6)
        val outer = eyeY - (if (fierce) 6.0 else 5.4)
        val arch = if (fierce) 0.0 else sin(t * PI) * 0.7
        val by = inner + (outer - inner) * t - arch
        val thick = (if (f.brows == 1) 1.6 else 1.15) * (1 - t * 0.55) + 0.15
        if (y in by - thick * 0.35..by + thick * 0.65) {
            c = if (y < by - thick * 0.35 + 0.3 || y > by + thick * 0.65 - 0.3) INK else mul(hairSh, 0.8, 0.8, 0.8)
        }
    }

    private fun glasses(x: Double, y: Double) {
        when (f.acc) {
            3 -> {
                if (abs(x - 48) > 16.5 || y < eyeY - 5.2 || y > eyeY + 3.8) return
                val frame = abs(x - 48) > 15.0 || y < eyeY - 4.2 || y > eyeY + 2.8 || abs(x - 48) < 1.3
                if (frame) { part = Parts.ACC; c = if (y < eyeY - 3.6) 0xFF50505A.toInt() else 0xFF26262E.toInt(); return }
                c = mix(c, 0xFF5AB4E6.toInt(), 0.55)
                if (x - y in 2.0..5.0 || x - y in 7.0..8.0) c = mix(c, WHITE, 0.7)
            }
            5 -> for (side in intArrayOf(-1, 1)) {
                val cx = 48 + side * 8.0
                val ox = abs(x - cx); val oy = abs(y - (eyeY + 0.2))
                if (ox < 5.2 && oy < 3.4 && (ox > 4.5 || oy > 2.7)) { part = Parts.ACC; c = 0xFF22222A.toInt(); return }
                if (ox <= 4.5 && oy <= 2.7 && x - cx < -1.8 && y < eyeY - 0.8) c = mix(c, WHITE, 0.5)
                if (abs(x - 48) < 3.0 && abs(y - (eyeY - 0.8)) < 0.5) { part = Parts.ACC; c = 0xFF22222A.toInt() }
            }
        }
    }

    /** Facial hair shaped and outlined: mustache, goatee, chin strap, full beard. */
    private fun beard(x: Double, y: Double): Boolean {
        if (!inHead(x, y, 0.4)) return false
        val dx = x - 48
        val adx = abs(dx)
        val w = hw(y)
        val inMouth = adx < 5.0 && abs(y - mouthY) < 0.9
        val hit = when (f.beard.coerceIn(0, 5)) {
            2 -> adx < 6.6 && y in mouthY - 3.2 + adx * 0.1..mouthY - 0.9 + (if (adx > 5.0) (adx - 5.0) * 1.6 else 0.0)
            3 -> (adx < 6.4 && y in mouthY - 3.0 + adx * 0.1..mouthY - 0.9) ||
                (adx < 4.6 - (y - mouthY - 2.6) * 0.3 && y in mouthY + 2.6..chinY + 1.2) ||
                (adx in 5.0..6.4 && y in mouthY - 1.5..mouthY + 1.2)
            4 -> (w - adx < 2.4 && y > eyeY + 3) || (y > chinY - 3.2 && y < chinY + 0.8 + (2 - (adx / 3) % 2))
            5 -> {
                val cheek = eyeY + 9.5 + (1 - adx / 18) * 3.0
                (y > cheek || w - adx < 2.6 && y > eyeY + 2.5) && !(adx < 4.8 && y in mouthY - 0.6..mouthY + 1.8) &&
                    // A jagged, anime edge along the bottom.
                    y < chinY + 1.8 + (1 - abs(((x / 2.2) % 1.0) * 2 - 1)) * 2.0
            }
            else -> false
        }
        if (!hit || inMouth) return false
        part = Parts.BEARD
        val nx = (dx / (w + 2)).coerceIn(-0.95, 0.95)
        c = cel(hair, hairSh, nx, 0.35, sqrt((1 - nx * nx).coerceAtLeast(0.1)), 0.2)
        return true
    }

    private fun ears(x: Double, y: Double): Boolean {
        val w = hw(eyeY + 3)
        for (side in intArrayOf(-1, 1)) {
            val ex = 48 + side * (w + 1.3)
            val ey = eyeY + 4.0
            val dx = (x - ex) / 3.1; val dy = (y - ey) / 5.8
            if (dx * dx + dy * dy <= 1) {
                part = Parts.EAR
                c = if (side > 0 || dx * side > 0.35) skinSh else skin
                // The inner curl.
                val inner = hypot((x - ex + side * 0.4) / 1.5, (y - ey + 0.4) / 3.4)
                if (abs(inner - 1) < 0.16 && (x - ex) * side < 0.6) c = INK
                return true
            }
        }
        return false
    }

    private fun earring(x: Double, y: Double): Boolean {
        if (f.acc != 4) return false
        val ex = 48 - (hw(eyeY + 3) + 1.6); val ey = eyeY + 9.6
        if (hypot(x - ex, y - ey) > 1.5) return false
        part = Parts.EARRING
        c = if (hypot(x - ex + 0.5, y - ey + 0.5) < 0.6) 0xFFFFF4C2.toInt() else 0xFFE8B23A.toInt()
        return true
    }

    private fun headband(x: Double, y: Double): Boolean {
        if (f.acc != 1 && f.acc != 2) return false
        val by = crTop + 7.0
        if (y < by || y > by + 5.5 || !inHead(x, y, 2.0)) return false
        part = Parts.ACC
        val base = if (f.acc == 1) 0xFFF4F4F2.toInt() else trim
        val nx = ((x - 48) / (hw(y) + 2)).coerceIn(-0.95, 0.95)
        c = cel(base, mul(base, 0.72, 0.72, 0.82), nx, -0.1, sqrt(1 - nx * nx), 0.2)
        if (f.acc == 1 && y in by + 2.2..by + 3.3) c = team
        return true
    }

    // ---------- the body ----------
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

    private fun numberDist(x: Double, y: Double): Double {
        val u = 1.8
        val gap = 2.4
        val w = digits.length * 4 + (digits.length - 1) * gap
        val gx = (x - 48) / u + w / 2; val gy = (y - 79.5) / u
        var best = 99.0
        for ((i, chr) in digits.withIndex()) {
            val ox = i * (4 + gap)
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

    private fun trapY(adx: Double): Double = 70.5 + ((adx - 7.5).coerceAtLeast(0.0) / 21.0).coerceAtMost(1.3).pow(1.7) * 9.0

    private fun body(x: Double, y: Double): Boolean {
        val dx = x - 48; val adx = abs(dx); val side = if (dx < 0) -1.0 else 1.0
        // The neck.
        val half = 7.6 + smooth(69.0, 78.0, y) * 4.6
        val inNeck = y >= jawY - 2 && y <= 80 && adx <= half
        val dcx = 29.5; val dcy = 87.5; val dr = 12.0
        val inDelt = hypot(adx - dcx, y - dcy) <= dr
        val inBody = adx <= 31.0 && y >= trapY(adx)
        if (!inNeck && !inDelt && !inBody) return false
        val arm = 16.5 + smooth(74.0, 90.0, y) * 9.5
        val neckHole = (dx / 10.8).pow(2) + ((y - 69.5) / 9.0).pow(2) < 1
        val onJersey = (inBody || inDelt) && adx <= arm && !neckHole && y >= trapY(adx)
        if (onJersey) {
            val edgeArm = arm - adx
            val edgeNeck = sqrt((dx / 10.8).pow(2) + ((y - 69.5) / 9.0).pow(2)) - 1
            val nx = side * (0.1 + adx / 31.0 * 0.4); val ny = -0.3 + (y - 80) / 40
            val nz = sqrt((1 - nx * nx - ny * ny).coerceAtLeast(0.1))
            if (edgeArm < 1.7 || edgeNeck < 0.16) { part = Parts.TRIM; c = cel(trim, trimSh, nx, ny, nz, 0.25); return true }
            if (numberDist(x, y) < 0.7) { part = Parts.NUMBER; c = cel(trim, trimSh, nx, ny, nz, 0.15); return true }
            part = Parts.JERSEY
            c = cel(team, teamSh, nx, ny, nz, 0.28)
            // A hard fold shadow by each armhole.
            if (adx in arm - 6.0..arm - 4.4 && y > 82) c = teamSh
            return true
        }
        part = Parts.BODY
        val nx: Double; val ny: Double
        if (inDelt && (adx > 25 || !inBody)) { nx = (adx - dcx) / dr * side; ny = (y - dcy) / dr }
        else if (inNeck && !inBody) { nx = dx / (half + 2); ny = 0.15 }
        else { nx = side * (0.1 + adx / 31.0 * 0.35); ny = -0.5 * smooth(trapY(adx) + 6, trapY(adx), y) + (y - 88) / 60 }
        val nz = sqrt((1 - nx * nx - ny * ny).coerceAtLeast(0.1))
        var col = cel(skin, skinSh, nx, ny, nz, 0.25)
        // The head's shadow on the neck, hard-edged.
        if (inNeck && y < chinY + 4.5 - adx * 0.15) col = skinSh
        // Neck muscles and the collarbones, in ink.
        if (inNeck && y > chinY + 3) {
            val mx = 4.4 + (y - chinY) * 0.18
            if (abs(adx - mx) < 0.26 && y < 76) col = mul(skinDeep, 0.9, 0.9, 0.9)
        }
        if (segDist(adx, y, 4.5, 75.5, 12.5, 74.0) < 0.3) col = mul(skinDeep, 0.9, 0.9, 0.9)
        // Where the deltoid meets the arm.
        if (inDelt && abs(hypot(adx - dcx, y - dcy) - dr * 0.72) < 0.3 && y > dcy - 2 && adx > dcx - 3) col = mul(skinDeep, 0.9, 0.9, 0.9)
        c = col
        return true
    }

    // ---------- behind him ----------
    private val auraCol = if (lum(trim) > 0.85 || lum(trim) < 0.12) mix(team, WHITE, 0.55) else mix(trim, WHITE, 0.25)
    private val auraCore = mix(auraCol, WHITE, 0.55)

    private fun backdrop(x: Double, y: Double) {
        part = Parts.BG
        val ang = atan2(y - 44, x - 48)
        val rays = 18
        val ray = floor((ang + PI) / (2 * PI) * rays + noise(ang * 3, 0.0, seed) * 0.4).toInt() % 2 == 0
        val dist = hypot(x - 48, (y - 46) * 0.9)
        var col = if (ray) mul(team, 0.5, 0.5, 0.58) else mul(team, 0.72, 0.72, 0.8)
        // The aura: flame tongues around the figure, licking upward.
        val flick = noise(x * 0.32, y * 0.11 + 7, seed + 21) * 5.0
        val up = ((60 - y) / 60).coerceIn(0.0, 1.0) * 3.0
        val g = 2.6 + flick * 0.85 + up
        val inAura = inHead(x, y + up * 0.8, g + 3.5) || (y > 66 && abs(x - 48) < 31 + g * 0.6 && y > trapY(abs(x - 48)) - g) ||
            (style in setOf(5, 6, 4) && hypot(x - 48, y - (crTop + 4)) < 24 + g)
        if (inAura) {
            val core = inHead(x, y + up * 0.5, g * 0.45 + 2.0) || (y > 68 && abs(x - 48) < 31 + g * 0.3 && y > trapY(abs(x - 48)) - g * 0.4)
            col = if (core) auraCore else auraCol
        }
        // Speed lines.
        if (!inAura && (hash((ang * 40).toInt(), 2, seed) > 0.93) && dist > 30) col = mix(col, WHITE, 0.35)
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
