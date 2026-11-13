package com.nh.hoopscoach.ui

import android.graphics.Bitmap
import com.nh.hoopscoach.game.Face
import com.nh.hoopscoach.game.Gear
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Composites modular `character/` sprites onto a shared 512×512 canvas.
 *
 * Architecture (matches the Jetpack Compose customizer guide):
 * every layer is pre-aligned — draw at (0,0) with matching 512 size, no custom offsets.
 * Layer order: body → eyes → mouth → beard → jersey → headband → eyewear → hair.
 * Layers that are not 512×512 have no anchor and are skipped rather than guessed at.
 * Output is a square ARGB bitmap ([N]×[N]), nearest-neighbor scaled for crisp pixels.
 */
object SpriteCompose {
    /** Display / cache size. Source layers are 512 and scaled with nearest-neighbor. */
    const val N = 256
    private const val SRC = 512

    // Chest number on the aligned jersey cutout (collar V at y≈297).
    private const val CHEST_NUM_CX = 256f
    private const val CHEST_NUM_CY = 420f
    private const val SHOULDER_NUM_CX = 318f
    private const val SHOULDER_NUM_CY = 278f

    // Transparent digit cutouts in the jersey pack: enclosed holes this big, below the collar.
    private const val CUTOUT_MIN_AREA = 120
    private const val CUTOUT_MIN_Y = 285
    private const val CUTOUT_RIM = 1
    // How far (sum of RGB deltas) a strap pixel must be from the fabric to count as a printed mark.
    private const val MARK_DISTANCE = 70
    // Edge pixels next to a wiped digit that are this far off the fabric get wiped as well.
    private const val EDGE_DISTANCE = 32
    // Patches grow from fabric within this distance of the jersey's main color first.
    private const val FABRIC_DISTANCE = 140
    private const val NUMBER_AREA_X0 = 186
    private const val NUMBER_AREA_X1 = 354
    private const val NUMBER_AREA_Y0 = 248
    private const val NUMBER_AREA_Y1 = 475

    private val HAIR_COLORS = intArrayOf(
        0xFF2E2A2A.toInt(), 0xFF4A2E1E.toInt(), 0xFF6E4628.toInt(), 0xFFA2562A.toInt(),
        0xFFDAB458.toInt(), 0xFFECE6D4.toInt(), 0xFF8E9096.toInt(), 0xFFC22E2E.toInt(),
    )

    fun render(
        f: Face,
        primary: Int,
        secondary: Int,
        number: Int,
        gear: Gear = Gear(),
        jerseyKey: String? = null,
    ): IntArray {
        val bodySrc = SpriteSheets.body(f.skin) ?: return fallback(f, primary, secondary, number, gear)
        val canvas = Bitmap.createBitmap(SRC, SRC, Bitmap.Config.ARGB_8888)

        // Layer order from engine_coordinates.json (all 512×512 at 0,0).
        blitLayer(canvas, bodySrc)
        blitLayer(canvas, SpriteSheets.eyes(f.eyes))
        blitLayer(canvas, SpriteSheets.mouth(f.mouth))
        val beardSrc = SpriteSheets.beard(f.beard)
        if (beardSrc != null) {
            val tinted = tintHair(beardSrc, HAIR_COLORS[f.hairColor.coerceIn(0, HAIR_COLORS.lastIndex)])
            blitLayer(canvas, tinted)
            if (tinted !== beardSrc) tinted.recycle()
        }

        val jersey = cleanJersey(jerseyKey, primary, secondary)
        if (jersey != null) {
            blitLayer(canvas, jersey)
            applyChestNumber(canvas, number)
        }

        blitLayer(canvas, SpriteSheets.eyewear(f.acc))

        val hairSrc = hairBitmap(f)
        if (hairSrc != null) {
            val tinted = tintHair(hairSrc, HAIR_COLORS[f.hairColor.coerceIn(0, HAIR_COLORS.lastIndex)])
            blitLayer(canvas, tinted)
            if (tinted !== hairSrc) tinted.recycle()
        }

        // Headbands sit over the hair: the hair now covers the whole scalp down to the hairline.
        val headband = when {
            f.hair == 7 -> SpriteSheets.bitmap("character/headbands/03_stealth_black.png")
            else -> SpriteSheets.headband(f.acc)
        }
        blitLayer(canvas, headband)

        val out = Bitmap.createScaledBitmap(canvas, N, N, false)
        canvas.recycle()
        val px = IntArray(N * N)
        out.getPixels(px, 0, N, 0, 0, N, N)
        if (out !== canvas) out.recycle()
        return px
    }

    /**
     * Draw a pre-aligned 512 layer at (0,0). Anything else is a raw crop with no anchor; centering
     * one drops it in the middle of the canvas (a beard crop lands as a black bar across the mouth),
     * so it is skipped instead.
     */
    private fun blitLayer(dst: Bitmap, src: Bitmap?) {
        if (src == null) return
        if (!isAligned(src)) return
        blitOpaque(dst, src, 0, 0)
    }

    /** True for a layer that can be drawn at (0,0) on the shared canvas. */
    fun isAligned(src: Bitmap): Boolean = src.width == SRC && src.height == SRC

    /**
     * Removes the jersey art's baked number so only the player's own number shows:
     * fills the transparent digit cutouts the jersey pack ships with (otherwise the body layer
     * shows through them as a "1"), wipes the printed digits around them, and repaints both from
     * the surrounding fabric.
     */
    private fun cleanNumberAreas(bmp: Bitmap) {
        val w = bmp.width
        val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        val kill = BooleanArray(w * h)
        val cutout = markEnclosedCutouts(px, w, h, kill)
        markGlyphs(px, w, h, kill, CHEST_NUM_CX, CHEST_NUM_CY, halfW = 70, halfH = 55)
        markGlyphs(px, w, h, kill, SHOULDER_NUM_CX, SHOULDER_NUM_CY + 5f, halfW = 42, halfH = 40)
        markIsolatedMarks(px, w, h, kill, SHOULDER_NUM_CX, SHOULDER_NUM_CY + 9f, halfW = 42, halfH = 44)
        // The cutouts' anti-aliased rims (white edge, dark outline) sit just outside the boxes.
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            if (kill[i] || px[i] ushr 24 < 32 || !isGlyphTone(px[i])) continue
            if (nearCutout(cutout, w, h, x, y, CUTOUT_RIM)) kill[i] = true
        }
        val fabric = dominantColor(px, w, NUMBER_AREA_X0, NUMBER_AREA_X1, NUMBER_AREA_Y0, NUMBER_AREA_Y1)
        inpaint(px, w, h, kill, fabric)
        // Second pass: faint shoulder-strap ghosts often survive the first fill with a near-fabric
        // shade. Resample each column from fabric above the strap and wipe leftover marks.
        scrubShoulderGhost(px, w, h)
        bmp.setPixels(px, 0, w, 0, 0, w, h)
    }

    /**
     * Clears residual shoulder-strap digit ghosts after the main inpaint. Samples fabric from the
     * same column a few rows above the strap hole so the fill matches local shade instead of a
     * flat average that still reads as a pale "1" at zoom.
     */
    private fun scrubShoulderGhost(px: IntArray, w: Int, h: Int) {
        val x0 = (SHOULDER_NUM_CX - 44f).roundToInt().coerceIn(0, w - 1)
        val x1 = (SHOULDER_NUM_CX + 44f).roundToInt().coerceIn(0, w)
        val y0 = (SHOULDER_NUM_CY - 36f).roundToInt().coerceIn(0, h - 1)
        val y1 = (SHOULDER_NUM_CY + 48f).roundToInt().coerceIn(0, h)
        val fabric = dominantColor(px, w, x0, x1, (y0 - 28).coerceAtLeast(0), y0) ?: return
        val kill = BooleanArray(w * h)
        for (y in y0 until y1) for (x in x0 until x1) {
            val i = y * w + x
            val c = px[i]
            if (c ushr 24 < 32) continue
            val dist = colorDistance(c, fabric)
            // Catch both hard glyph tones and soft near-fabric ghosts left by the first fill.
            if (dist > 22 && (isGlyphTone(c) || dist > 40)) kill[i] = true
        }
        // Grow two rings so anti-aliased ghost edges don't remain.
        repeat(2) {
            val grow = ArrayList<Int>()
            for (y in y0 until y1) for (x in x0 until x1) {
                val i = y * w + x
                if (kill[i] || px[i] ushr 24 < 32) continue
                if (colorDistance(px[i], fabric) <= 14) continue
                if ((x > x0 && kill[i - 1]) || (x < x1 - 1 && kill[i + 1]) ||
                    (y > y0 && kill[i - w]) || (y < y1 - 1 && kill[i + w])
                ) grow.add(i)
            }
            for (i in grow) kill[i] = true
        }
        for (y in y0 until y1) for (x in x0 until x1) {
            val i = y * w + x
            if (!kill[i]) continue
            px[i] = sampleFabricAbove(px, w, h, x, y, fabric)
        }
    }

    /** Prefer the nearest opaque fabric pixel above (x,y); fall back to [fabric]. */
    private fun sampleFabricAbove(px: IntArray, w: Int, h: Int, x: Int, y: Int, fabric: Int): Int {
        for (yy in (y - 1) downTo (y - 48).coerceAtLeast(0)) {
            val c = px[yy * w + x]
            if (c ushr 24 < 200) continue
            if (isGlyphTone(c)) continue
            if (colorDistance(c, fabric) > FABRIC_DISTANCE) continue
            return c
        }
        // Blend a little with left/right neighbors at the same row if they look like fabric.
        var r = (fabric shr 16) and 0xFF
        var g = (fabric shr 8) and 0xFF
        var b = fabric and 0xFF
        var n = 1
        for (dx in intArrayOf(-1, 1)) {
            val xx = x + dx
            if (xx !in 0 until w) continue
            val c = px[y * w + xx]
            if (c ushr 24 < 200 || isGlyphTone(c) || colorDistance(c, fabric) > FABRIC_DISTANCE) continue
            r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF; n++
        }
        return 0xFF000000.toInt() or ((r / n) shl 16) or ((g / n) shl 8) or (b / n)
    }

    /**
     * Marks transparent regions fully enclosed by jersey fabric below the collar: the digit
     * cutouts. The neck, armholes and the small collar gaps are reachable from the border or sit
     * above [CUTOUT_MIN_Y], so they stay open.
     */
    private fun markEnclosedCutouts(px: IntArray, w: Int, h: Int, kill: BooleanArray): BooleanArray {
        val outside = BooleanArray(w * h)
        val queue = IntArray(w * h)
        var head = 0
        var tail = 0
        fun seed(i: Int) {
            if (!outside[i] && px[i] ushr 24 < 16) { outside[i] = true; queue[tail++] = i }
        }
        for (x in 0 until w) { seed(x); seed((h - 1) * w + x) }
        for (y in 0 until h) { seed(y * w); seed(y * w + w - 1) }
        while (head < tail) {
            val i = queue[head++]
            val x = i % w
            val y = i / w
            if (x > 0) seed(i - 1)
            if (x < w - 1) seed(i + 1)
            if (y > 0) seed(i - w)
            if (y < h - 1) seed(i + w)
        }
        val cutout = BooleanArray(w * h)
        val seen = BooleanArray(w * h)
        for (start in 0 until w * h) {
            if (outside[start] || seen[start] || px[start] ushr 24 >= 16) continue
            head = 0
            tail = 0
            seen[start] = true
            queue[tail++] = start
            var minY = h
            while (head < tail) {
                val i = queue[head++]
                val x = i % w
                val y = i / w
                if (y < minY) minY = y
                for (n in intArrayOf(if (x > 0) i - 1 else -1, if (x < w - 1) i + 1 else -1, if (y > 0) i - w else -1, if (y < h - 1) i + w else -1)) {
                    if (n < 0 || seen[n] || outside[n] || px[n] ushr 24 >= 16) continue
                    seen[n] = true
                    queue[tail++] = n
                }
            }
            if (tail >= CUTOUT_MIN_AREA && minY >= CUTOUT_MIN_Y) {
                for (k in 0 until tail) { cutout[queue[k]] = true; kill[queue[k]] = true }
            }
        }
        return cutout
    }

    private fun nearCutout(cutout: BooleanArray, w: Int, h: Int, x: Int, y: Int, r: Int): Boolean {
        for (dy in -r..r) {
            val yy = y + dy
            if (yy < 0 || yy >= h) continue
            for (dx in -r..r) {
                val xx = x + dx
                if (xx in 0 until w && cutout[yy * w + xx]) return true
            }
        }
        return false
    }

    /** Bright digit fill or dark digit outline. */
    private fun isGlyphTone(c: Int): Boolean {
        val sum = ((c shr 16) and 0xFF) + ((c shr 8) and 0xFF) + (c and 0xFF)
        return sum > 580 || sum < 110
    }

    /** Marks printed digit pixels (bright, dark, or strongly tinted vs. the fabric) in a box. */
    private fun markGlyphs(px: IntArray, w: Int, h: Int, kill: BooleanArray, cx: Float, cy: Float, halfW: Int, halfH: Int) {
        val x0 = (cx - halfW).roundToInt().coerceIn(0, w - 1)
        val x1 = (cx + halfW).roundToInt().coerceIn(0, w)
        val y0 = (cy - halfH).roundToInt().coerceIn(0, h - 1)
        val y1 = (cy + halfH).roundToInt().coerceIn(0, h)
        val fabric = fabricBelow(px, w, h, x0, x1, y1) ?: return
        val fr = (fabric shr 16) and 0xFF
        val fg = (fabric shr 8) and 0xFF
        val fb = fabric and 0xFF
        for (y in y0 until y1) {
            for (x in x0 until x1) {
                val i = y * w + x
                val c = px[i]
                if (c ushr 24 < 32) continue
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                val sum = r + g + b
                val tintedDigit = abs(r - fr) + abs(g - fg) + abs(b - fb) > 160 &&
                    (abs(r - g) > 45 || abs(g - b) > 45) && sum in 80..700
                if (isGlyphTone(c) || tintedDigit) kill[i] = true
            }
        }
        // Take the digits' anti-aliased edge too, or repainting smears it inward as a pale ghost.
        val base = dominantColor(px, w, x0, x1, y0, y1) ?: return
        repeat(3) {
            val grow = ArrayList<Int>()
            for (y in y0 until y1) for (x in x0 until x1) {
                val i = y * w + x
                val c = px[i]
                if (kill[i] || c ushr 24 < 32 || colorDistance(c, base) <= EDGE_DISTANCE) continue
                if ((x > 0 && kill[i - 1]) || (x < w - 1 && kill[i + 1]) ||
                    (y > 0 && kill[i - w]) || (y < h - 1 && kill[i + w])
                ) grow.add(i)
            }
            for (i in grow) kill[i] = true
        }
    }

    private fun colorDistance(a: Int, b: Int): Int =
        abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)) +
            abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)) +
            abs((a and 0xFF) - (b and 0xFF))

    /**
     * Marks any off-fabric shape that sits wholly inside a box: the strap digit, whatever its
     * color. Stripes, trim and collar lines run into the box edge, so they are left alone.
     */
    private fun markIsolatedMarks(px: IntArray, w: Int, h: Int, kill: BooleanArray, cx: Float, cy: Float, halfW: Int, halfH: Int) {
        val x0 = (cx - halfW).roundToInt().coerceIn(0, w - 1)
        val x1 = (cx + halfW).roundToInt().coerceIn(0, w)
        val y0 = (cy - halfH).roundToInt().coerceIn(0, h - 1)
        val y1 = (cy + halfH).roundToInt().coerceIn(0, h)
        val fabric = dominantColor(px, w, x0, x1, y0, y1) ?: return
        val bw = x1 - x0
        val bh = y1 - y0
        val off = BooleanArray(bw * bh)
        for (y in y0 until y1) for (x in x0 until x1) {
            val c = px[y * w + x]
            if (c ushr 24 < 32) continue
            if (colorDistance(c, fabric) > MARK_DISTANCE) off[(y - y0) * bw + (x - x0)] = true
        }
        val seen = BooleanArray(bw * bh)
        val queue = IntArray(bw * bh)
        for (start in off.indices) {
            if (!off[start] || seen[start]) continue
            var head = 0
            var tail = 0
            seen[start] = true
            queue[tail++] = start
            var touchesEdge = false
            while (head < tail) {
                val i = queue[head++]
                val x = i % bw
                val y = i / bw
                if (x == 0 || y == 0 || x == bw - 1 || y == bh - 1) touchesEdge = true
                for (dy in -1..1) for (dx in -1..1) {
                    val xx = x + dx
                    val yy = y + dy
                    if (xx !in 0 until bw || yy !in 0 until bh) continue
                    val j = yy * bw + xx
                    if (off[j] && !seen[j]) { seen[j] = true; queue[tail++] = j }
                }
            }
            if (touchesEdge) continue
            for (k in 0 until tail) {
                val i = queue[k]
                kill[(y0 + i / bw) * w + x0 + i % bw] = true
            }
        }
    }

    /** Most common opaque color in a box (4-bit buckets, averaged), i.e. the fabric. */
    private fun dominantColor(px: IntArray, w: Int, x0: Int, x1: Int, y0: Int, y1: Int): Int? {
        val count = IntArray(4096)
        for (y in y0 until y1) for (x in x0 until x1) {
            val c = px[y * w + x]
            if (c ushr 24 < 200) continue
            count[bucket(c)]++
        }
        var best = -1
        for (k in count.indices) if (count[k] > 0 && (best < 0 || count[k] > count[best])) best = k
        if (best < 0) return null
        var r = 0
        var g = 0
        var b = 0
        var n = 0
        for (y in y0 until y1) for (x in x0 until x1) {
            val c = px[y * w + x]
            if (c ushr 24 < 200 || bucket(c) != best) continue
            r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF; n++
        }
        return 0xFF000000.toInt() or ((r / n) shl 16) or ((g / n) shl 8) or (b / n)
    }

    private fun bucket(c: Int): Int =
        (((c shr 20) and 0xF) shl 8) or (((c shr 12) and 0xF) shl 4) or ((c shr 4) and 0xF)

    /** Average mid-tone fabric color in the rows just under a box (reference for tinted digits). */
    private fun fabricBelow(px: IntArray, w: Int, h: Int, x0: Int, x1: Int, y1: Int): Int? {
        var sr = 0
        var sg = 0
        var sb = 0
        var sn = 0
        for (y in y1.coerceAtMost(h - 1) until (y1 + 28).coerceAtMost(h)) {
            for (x in x0 until x1) {
                val c = px[y * w + x]
                if (c ushr 24 < 200) continue
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                if (r + g + b in 101..649) { sr += r; sg += g; sb += b; sn++ }
            }
        }
        if (sn == 0) return null
        return 0xFF000000.toInt() or ((sr / sn) shl 16) or ((sg / sn) shl 8) or (sb / sn)
    }

    /**
     * Repaints [kill] pixels from the surrounding fabric, one ring at a time from the edge
     * inward, so the patch takes the local shade instead of one flat color.
     */
    private fun inpaint(px: IntArray, w: Int, h: Int, kill: BooleanArray, fabric: Int?) {
        // First grow only from plain fabric so lettering and trim don't smear into the patch, then
        // let anything that is left fill from whatever opaque neighbor it has.
        if (fabric != null) {
            val plain = BooleanArray(w * h) {
                !kill[it] && px[it] ushr 24 >= 200 && colorDistance(px[it], fabric) <= FABRIC_DISTANCE
            }
            fillRings(px, w, h, kill, plain)
        }
        val known = BooleanArray(w * h) { !kill[it] && px[it] ushr 24 >= 200 && !isGlyphTone(px[it]) }
        fillRings(px, w, h, kill, known)
    }

    private fun fillRings(px: IntArray, w: Int, h: Int, kill: BooleanArray, known: BooleanArray) {
        var pending = 0
        var bx0 = w
        var by0 = h
        var bx1 = -1
        var by1 = -1
        for (i in 0 until w * h) {
            if (!kill[i]) continue
            pending++
            val x = i % w
            val y = i / w
            if (x < bx0) bx0 = x
            if (x > bx1) bx1 = x
            if (y < by0) by0 = y
            if (y > by1) by1 = y
        }
        var pass = 0
        val ring = ArrayList<Int>()
        val colors = ArrayList<Int>()
        while (pending > 0 && pass < 200) {
            pass++
            ring.clear()
            colors.clear()
            for (y in by0..by1) for (x in bx0..bx1) {
                val i = y * w + x
                if (!kill[i] || known[i]) continue
                var r = 0
                var g = 0
                var b = 0
                var n = 0
                for (dy in -1..1) for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val xx = x + dx
                    val yy = y + dy
                    if (xx !in 0 until w || yy !in 0 until h) continue
                    val j = yy * w + xx
                    if (!known[j]) continue
                    val c = px[j]
                    r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF; n++
                }
                if (n == 0) continue
                ring.add(i)
                colors.add(0xFF000000.toInt() or ((r / n) shl 16) or ((g / n) shl 8) or (b / n))
            }
            if (ring.isEmpty()) break
            for (k in ring.indices) { px[ring[k]] = colors[k]; known[ring[k]] = true; kill[ring[k]] = false }
            pending -= ring.size
        }
    }

    private fun applyChestNumber(canvas: Bitmap, number: Int) {
        val n = number.coerceIn(0, 99)
        val chest = SpriteSheets.chestNumber(n) ?: return
        blitOpaque(
            canvas, chest,
            (CHEST_NUM_CX - chest.width / 2f).roundToInt(),
            (CHEST_NUM_CY - chest.height / 2f).roundToInt(),
        )
    }

    /**
     * Alpha-aware blit. Robolectric's Canvas.drawBitmap replaces transparent src pixels with
     * transparent dest, wiping earlier layers — so we skip near-zero alpha ourselves.
     */
    private fun blitOpaque(dst: Bitmap, src: Bitmap, dx: Int, dy: Int) {
        val sw = src.width
        val sh = src.height
        val dw = dst.width
        val dh = dst.height
        val sp = IntArray(sw * sh)
        val dp = IntArray(dw * dh)
        src.getPixels(sp, 0, sw, 0, 0, sw, sh)
        dst.getPixels(dp, 0, dw, 0, 0, dw, dh)
        for (sy in 0 until sh) {
            val dyRow = dy + sy
            if (dyRow < 0 || dyRow >= dh) continue
            for (sx in 0 until sw) {
                val sc = sp[sy * sw + sx]
                val sa = sc ushr 24
                if (sa < 16) continue
                val dxCol = dx + sx
                if (dxCol < 0 || dxCol >= dw) continue
                val di = dyRow * dw + dxCol
                if (sa >= 240) {
                    dp[di] = sc
                } else {
                    val dc = dp[di]
                    val da = dc ushr 24
                    val inv = 255 - sa
                    val outA = sa + da * inv / 255
                    if (outA == 0) {
                        dp[di] = 0
                        continue
                    }
                    val sr = (sc shr 16) and 0xFF
                    val sg = (sc shr 8) and 0xFF
                    val sb = sc and 0xFF
                    val dr = (dc shr 16) and 0xFF
                    val dg = (dc shr 8) and 0xFF
                    val db = dc and 0xFF
                    val or = (sr * sa + dr * da * inv / 255) / outA
                    val og = (sg * sa + dg * da * inv / 255) / outA
                    val ob = (sb * sa + db * da * inv / 255) / outA
                    dp[di] = (outA shl 24) or (or shl 16) or (og shl 8) or ob
                }
            }
        }
        dst.setPixels(dp, 0, dw, 0, 0, dw, dh)
    }

    private fun hairBitmap(f: Face): Bitmap? {
        if (f.hair == 0) return null
        if (f.hair == 1 && f.hairColor >= 4) {
            SpriteSheets.bitmap("character/hairstyles/bleached_blonde_crop.png")?.let { return it }
        }
        return SpriteSheets.hair(f.hair)
    }

    /** Jerseys with their baked numbers removed, cached since cleaning walks the whole sprite. */
    private val cleanJerseys = ConcurrentHashMap<String, Bitmap>()

    private fun cleanJersey(key: String?, primary: Int, secondary: Int): Bitmap? {
        val cacheKey = if (key != null && SpriteSheets.jersey(key) != null) key else "blank|$primary|$secondary"
        cleanJerseys[cacheKey]?.let { return it }
        val jersey = resolveJersey(key, primary, secondary) ?: return null
        val clean = jersey.copy(Bitmap.Config.ARGB_8888, true)
        cleanNumberAreas(clean)
        if (cleanJerseys.size >= 48) cleanJerseys.clear()
        cleanJerseys[cacheKey] = clean
        return clean
    }

    private fun resolveJersey(key: String?, primary: Int, secondary: Int): Bitmap? {
        if (key != null) SpriteSheets.jersey(key)?.let { return it }
        val template = SpriteSheets.jersey("blank") ?: return null
        return recolorJersey(template, primary or (0xFF shl 24), secondary or (0xFF shl 24))
    }

    /** Shift non-outline hair pixels toward the chosen hair color, keeping shading. */
    private fun tintHair(src: Bitmap, color: Int): Bitmap {
        val tr = (color shr 16) and 0xFF
        val tg = (color shr 8) and 0xFF
        val tb = color and 0xFF
        if (tr + tg + tb > 500) return src
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val w = out.width
        val h = out.height
        val px = IntArray(w * h)
        out.getPixels(px, 0, w, 0, 0, w, h)
        for (i in px.indices) {
            val c = px[i]
            val a = c ushr 24
            if (a < 32) continue
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            if (r + g + b < 40) continue
            val lum = (0.35f * r + 0.45f * g + 0.2f * b) / 255f
            val nr = (tr * lum * 1.15f).toInt().coerceIn(0, 255)
            val ng = (tg * lum * 1.15f).toInt().coerceIn(0, 255)
            val nb = (tb * lum * 1.15f).toInt().coerceIn(0, 255)
            px[i] = (a shl 24) or (nr shl 16) or (ng shl 8) or nb
        }
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    private fun recolorJersey(src: Bitmap, primary: Int, secondary: Int): Bitmap {
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val w = out.width
        val h = out.height
        val px = IntArray(w * h)
        out.getPixels(px, 0, w, 0, 0, w, h)
        val pr = (primary shr 16) and 0xFF
        val pg = (primary shr 8) and 0xFF
        val pb = primary and 0xFF
        val sr = (secondary shr 16) and 0xFF
        val sg = (secondary shr 8) and 0xFF
        val sb = secondary and 0xFF
        for (i in px.indices) {
            val c = px[i]
            val a = c ushr 24
            if (a < 32) continue
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val sum = r + g + b
            if (sum < 45) continue
            if (r > 210 && g > 210 && b > 210) continue
            val lum = (0.3f * r + 0.5f * g + 0.2f * b) / 255f
            val useSecondary = abs(r - g) < 25 && abs(g - b) < 25 && sum in 120..420
            val nr: Int
            val ng: Int
            val nb: Int
            if (useSecondary) {
                nr = (sr * lum * 1.2f).toInt().coerceIn(0, 255)
                ng = (sg * lum * 1.2f).toInt().coerceIn(0, 255)
                nb = (sb * lum * 1.2f).toInt().coerceIn(0, 255)
            } else {
                nr = (pr * lum * 1.25f).toInt().coerceIn(0, 255)
                ng = (pg * lum * 1.25f).toInt().coerceIn(0, 255)
                nb = (pb * lum * 1.25f).toInt().coerceIn(0, 255)
            }
            px[i] = (a shl 24) or (nr shl 16) or (ng shl 8) or nb
        }
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    private fun fallback(f: Face, primary: Int, secondary: Int, number: Int, gear: Gear): IntArray {
        val px = IntArray(N * N)
        val fill = primary or (0xFF shl 24)
        for (i in px.indices) px[i] = fill
        return px
    }
}
