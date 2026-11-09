package com.nh.hoopscoach.ui

import android.graphics.Bitmap
import com.nh.hoopscoach.game.Face
import com.nh.hoopscoach.game.Gear
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Composites the user's modular sprites: skin-tone body + team jersey + hairstyle.
 * Output is a square ARGB bitmap ([N]×[N]) with a transparent background.
 */
object SpriteCompose {
    /** Display / cache size. Source layers are higher-res and scaled with nearest-neighbor. */
    const val N = 256

    private const val SRC = 512
    private const val FACE_CX = 255f
    private const val FACE_CY = 152f
    private const val FACE_HOLE_TARGET = 175f
    // Jersey-less bodies: transparent torso; jersey draws behind. Keep the full jersey
    // (and chest number) inside the 512 canvas — oversized scale pushed numbers off-screen.
    private const val JERSEY_SCALE = 1.08f
    private const val JERSEY_Y = 252
    // Number anchors as fractions of the placed jersey box (recomputed each render).
    private const val CHEST_NUM_YF = 0.50f
    private const val SHOULDER_NUM_XF = 0.72f
    private const val SHOULDER_NUM_YF = 0.12f
    // Face overlay anchors on the 512 body canvas (cropped parts centered here).
    private const val EYE_CX = 255f
    private const val EYE_CY = 145f
    private const val EYE_MAX_W = 170
    private const val MOUTH_CX = 255f
    private const val MOUTH_CY = 225f
    private const val MOUTH_MAX_W = 95
    private const val BEARD_CX = 255f
    private const val BEARD_CY = 232f
    private const val BEARD_MAX_W = 110
    private const val HEADBAND_CX = 255f
    private const val HEADBAND_CY = 100f
    private const val HEADBAND_MAX_W = 175
    private const val EYEWEAR_CX = 255f
    private const val EYEWEAR_CY = 148f
    private const val EYEWEAR_MAX_W = 160

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
        // Jersey-less bodies leave a transparent torso hole: jersey draws behind, body/arms on top.
        val canvasBmp = Bitmap.createBitmap(SRC, SRC, Bitmap.Config.ARGB_8888)
        val jersey = resolveJersey(jerseyKey, primary, secondary)
        var jx = 0
        var jw = 0
        var jh = 0
        if (jersey != null) {
            jw = (jersey.width * JERSEY_SCALE).roundToInt()
            jh = (jersey.height * JERSEY_SCALE).roundToInt()
            jx = (SRC - jw) / 2
            // Wipe baked chest/shoulder #1 on the jersey art before it goes on the body.
            val clean = jersey.copy(Bitmap.Config.ARGB_8888, true)
            eraseGlyphs(clean, jersey.width / 2f, jersey.height * 0.62f, halfW = 70, halfH = 55)
            eraseGlyphs(clean, jersey.width * 0.78f, jersey.height * 0.12f, halfW = 42, halfH = 36)
            eraseGlyphs(clean, jersey.width * 0.72f, jersey.height * 0.14f, halfW = 36, halfH = 30)
            val scaled = Bitmap.createScaledBitmap(clean, jw, jh, false)
            if (clean !== jersey) clean.recycle()
            blitOpaque(canvasBmp, scaled, jx, JERSEY_Y)
            if (scaled !== jersey) scaled.recycle()
            applyJerseyNumber(canvasBmp, number, jx, JERSEY_Y, jw, jh)
        }

        val body = bodySrc.copy(Bitmap.Config.ARGB_8888, true)
        val hairSrc = hairBitmap(f)
        if (f.hair == 0 || hairSrc != null) stripDefaultHair(body)
        blitOpaque(canvasBmp, body, 0, 0)
        body.recycle()

        // Face extras from the separated pack: eyes → mouth → beard → hair → headband/eyewear.
        placeOverlay(canvasBmp, SpriteSheets.eyes(f.eyes), EYE_CX, EYE_CY, EYE_MAX_W)
        placeOverlay(canvasBmp, SpriteSheets.mouth(f.mouth), MOUTH_CX, MOUTH_CY, MOUTH_MAX_W)
        val beardSrc = SpriteSheets.beard(f.beard)
        if (beardSrc != null) {
            val tinted = tintHair(beardSrc, HAIR_COLORS[f.hairColor.coerceIn(0, HAIR_COLORS.lastIndex)])
            placeOverlay(canvasBmp, tinted, BEARD_CX, BEARD_CY, BEARD_MAX_W, recycle = tinted !== beardSrc)
        }

        if (hairSrc != null) {
            val cleaned = clearEdgeMatte(hairSrc, thresh = 36)
            val (hx, hy, hw, hh) = hairPlacement(cleaned)
            val tinted = tintHair(cleaned, HAIR_COLORS[f.hairColor.coerceIn(0, HAIR_COLORS.lastIndex)])
            val scaled = Bitmap.createScaledBitmap(tinted, hw, hh, false)
            blitOpaque(canvasBmp, scaled, hx, hy)
            if (scaled !== tinted) scaled.recycle()
            if (tinted !== cleaned) tinted.recycle()
            if (cleaned !== hairSrc) cleaned.recycle()
        }

        // Hair style "Headband" always gets a clean black band; otherwise use Extras.
        val headband = when {
            f.hair == 7 -> SpriteSheets.bitmap("sprites/headbands/03_black_headband.png")
            else -> SpriteSheets.headband(f.acc)
        }
        placeOverlay(canvasBmp, headband, HEADBAND_CX, HEADBAND_CY, HEADBAND_MAX_W)
        placeOverlay(canvasBmp, SpriteSheets.eyewear(f.acc), EYEWEAR_CX, EYEWEAR_CY, EYEWEAR_MAX_W)

        val out = Bitmap.createScaledBitmap(canvasBmp, N, N, false)
        canvasBmp.recycle()
        val px = IntArray(N * N)
        out.getPixels(px, 0, N, 0, 0, N, N)
        if (out !== canvasBmp) out.recycle()
        return px
    }

    /** Center a cropped face part on [cx],[cy], optionally capping width. */
    private fun placeOverlay(
        canvas: Bitmap,
        src: Bitmap?,
        cx: Float,
        cy: Float,
        maxW: Int,
        recycle: Boolean = false,
    ) {
        if (src == null) return
        val cleaned = clearEdgeMatte(src)
        val scale = if (cleaned.width > maxW) maxW.toFloat() / cleaned.width else 1f
        val w = max(4, (cleaned.width * scale).roundToInt())
        val h = max(4, (cleaned.height * scale).roundToInt())
        val scaled = if (w == cleaned.width && h == cleaned.height) cleaned else Bitmap.createScaledBitmap(cleaned, w, h, false)
        val x = (cx - w / 2f).roundToInt()
        val y = (cy - h * 0.45f).roundToInt()
        blitOpaque(canvas, scaled, x, y)
        if (scaled !== cleaned) scaled.recycle()
        if (cleaned !== src) cleaned.recycle()
        if (recycle) src.recycle()
    }

    /** Drop near-black matte leftover from pack crops (edge-connected only). */
    private fun clearEdgeMatte(src: Bitmap, thresh: Int = 42): Bitmap {
        val w = src.width
        val h = src.height
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val px = IntArray(w * h)
        out.getPixels(px, 0, w, 0, 0, w, h)
        fun dark(i: Int): Boolean {
            val c = px[i]
            if (c ushr 24 == 0) return false
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            return r + g + b <= thresh
        }
        val seen = BooleanArray(w * h)
        val q = IntArray(w * h)
        var qh = 0
        var qt = 0
        fun push(i: Int) {
            if (seen[i] || !dark(i)) return
            seen[i] = true
            q[qt++] = i
        }
        for (x in 0 until w) { push(x); push((h - 1) * w + x) }
        for (y in 0 until h) { push(y * w); push(y * w + (w - 1)) }
        while (qh < qt) {
            val i = q[qh++]
            px[i] = 0
            val x = i % w
            val y = i / w
            if (x > 0) push(i - 1)
            if (x + 1 < w) push(i + 1)
            if (y > 0) push(i - w)
            if (y + 1 < h) push(i + w)
        }
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    /** Stamp the chest number; wipe the jersey art's baked shoulder #1 (no second number — it ghosted). */
    private fun applyJerseyNumber(canvas: Bitmap, number: Int, jx: Int, jy: Int, jw: Int, jh: Int) {
        val n = number.coerceIn(0, 99)
        val chestCx = jx + jw / 2f
        val chestCy = jy + jh * CHEST_NUM_YF
        val shoulderCx = jx + jw * SHOULDER_NUM_XF
        val shoulderCy = jy + jh * SHOULDER_NUM_YF
        eraseGlyphs(canvas, chestCx, chestCy, halfW = 72, halfH = 52)
        eraseGlyphs(canvas, shoulderCx, shoulderCy, halfW = 40, halfH = 32)
        val chest = SpriteSheets.chestNumber(n) ?: return
        blitOpaque(
            canvas, chest,
            (chestCx - chest.width / 2f).roundToInt(),
            (chestCy - chest.height / 2f).roundToInt(),
        )
    }

    /** Replace bright glyph / dark outline pixels in a box with nearby jersey fabric color. */
    private fun eraseGlyphs(bmp: Bitmap, cx: Float, cy: Float, halfW: Int, halfH: Int) {
        val w = bmp.width
        val h = bmp.height
        val x0 = (cx - halfW).roundToInt().coerceIn(0, w - 1)
        val x1 = (cx + halfW).roundToInt().coerceIn(0, w)
        val y0 = (cy - halfH).roundToInt().coerceIn(0, h - 1)
        val y1 = (cy + halfH).roundToInt().coerceIn(0, h)
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        var sr = 0
        var sg = 0
        var sb = 0
        var sn = 0
        // Sample jersey fabric from below the glyph (more reliable than the side on slim straps).
        val sampleY0 = y1.coerceAtMost(h - 1)
        val sampleY1 = (y1 + 28).coerceAtMost(h)
        for (y in sampleY0 until sampleY1) {
            for (x in x0 until x1) {
                val c = px[y * w + x]
                if (c ushr 24 < 200) continue
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                val sum = r + g + b
                if (sum in 101..649) {
                    sr += r; sg += g; sb += b; sn++
                }
            }
        }
        val fill = if (sn == 0) 0xFF404040.toInt() else {
            0xFF000000.toInt() or ((sr / sn) shl 16) or ((sg / sn) shl 8) or (sb / sn)
        }
        val fr = (fill shr 16) and 0xFF
        val fg = (fill shr 8) and 0xFF
        val fb = fill and 0xFF
        for (y in y0 until y1) {
            for (x in x0 until x1) {
                val i = y * w + x
                val c = px[i]
                if (c ushr 24 < 32) continue
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                val sum = r + g + b
                // Only wipe digit ink (white/silver, black outline, or saturated tint vs fabric).
                // A broad "far from fabric" wipe left flat color boxes over team wordmarks.
                val brightOrDark = sum > 580 || sum < 110
                val tintedDigit = abs(r - fr) + abs(g - fg) + abs(b - fb) > 160 &&
                    (abs(r - g) > 45 || abs(g - b) > 45) && sum in 80..700
                if (brightOrDark || tintedDigit) px[i] = fill
            }
        }
        bmp.setPixels(px, 0, w, 0, 0, w, h)
    }

    /** Copy only opaque-enough source pixels so transparent holes never clear the destination. */
    private fun blitOpaque(dst: Bitmap, src: Bitmap, dx: Int, dy: Int) {
        val sw = src.width
        val sh = src.height
        val dw = dst.width
        val dh = dst.height
        val sp = IntArray(sw * sh)
        src.getPixels(sp, 0, sw, 0, 0, sw, sh)
        val dp = IntArray(dw * dh)
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
                    // Simple src-over for soft edges.
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
        // Platinum/blond buzz → bleached crop when available.
        if (f.hair == 1 && f.hairColor >= 4) {
            SpriteSheets.bitmap("sprites/hairstyles/bleached_blonde_crop.png")?.let { return it }
        }
        if (f.hair == 5 && f.acc == 0) {
            // Occasional double puffs via afro + "wide" eyes as a stand-in seed — keep afro default.
        }
        return SpriteSheets.hair(f.hair)
    }

    private fun resolveJersey(key: String?, primary: Int, secondary: Int): Bitmap? {
        if (key != null) SpriteSheets.jersey(key)?.let { return it }
        // College / custom teams: blank jersey recolored to primary/secondary (no NBA wordmark).
        val template = SpriteSheets.jersey("blank") ?: SpriteSheets.jersey("nets") ?: return null
        return recolorJersey(template, primary or (0xFF shl 24), secondary or (0xFF shl 24))
    }

    /** Clear baked hair / alignment guide ticks so a hair overlay (or bald) sits cleanly. */
    private fun stripDefaultHair(bmp: Bitmap) {
        val w = bmp.width
        val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        // Sample cheek skin as the keep-color.
        var sr = 0; var sg = 0; var sb = 0; var sn = 0
        for (y in 155 until 195) for (x in 220 until 290) {
            val c = px[y * w + x]
            if (c ushr 24 < 200) continue
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            if (r > g + 15 && r > 70) { sr += r; sg += g; sb += b; sn++ }
        }
        val fr = if (sn == 0) 140 else sr / sn
        val fg = if (sn == 0) 90 else sg / sn
        val fb = if (sn == 0) 70 else sb / sn
        for (y in 0 until min(165, h)) {
            for (x in 0 until w) {
                val i = y * w + x
                val c = px[i]
                if (c ushr 24 < 32) continue
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                val sum = r + g + b
                // 1px hair-alignment ticks rising off the scalp.
                if (y < 80 && sum < 120 && (x in 200..230 || x in 280..310)) {
                    px[i] = 0
                    continue
                }
                val skin = abs(r - fr) + abs(g - fg) + abs(b - fb) < 75 && r > g - 5 && r > 60
                // Keep face flesh; drop dark hair, blond buzz tips, and stray outline above the brows.
                if (!skin) px[i] = 0
            }
        }
        bmp.setPixels(px, 0, w, 0, 0, w, h)
    }

    private data class Place(val x: Int, val y: Int, val w: Int, val h: Int)

    private fun hairPlacement(hair: Bitmap): Place {
        val (hcx, hcy, holeW) = faceHole(hair)
        val scale = if (holeW > 40) FACE_HOLE_TARGET / holeW else FACE_HOLE_TARGET / 240f
        val hw = max(8, (hair.width * scale).roundToInt())
        val hh = max(8, (hair.height * scale).roundToInt())
        val x = (FACE_CX - hcx * scale).roundToInt()
        val y = (FACE_CY - hcy * scale).roundToInt()
        return Place(x, y, hw, hh)
    }

    private fun faceHole(hair: Bitmap): Triple<Float, Float, Float> {
        val w = hair.width
        val h = hair.height
        val px = IntArray(w * h)
        hair.getPixels(px, 0, w, 0, 0, w, h)
        var minX = w
        var minY = h
        var maxX = 0
        var maxY = 0
        for (y in 0 until h) for (x in 0 until w) {
            if (px[y * w + x] ushr 24 > 200) {
                if (x < minX) minX = x
                if (y < minY) minY = y
                if (x > maxX) maxX = x
                if (y > maxY) maxY = y
            }
        }
        if (maxX <= minX) return Triple(w / 2f, h / 2f, 240f)
        val cy0 = minY + ((maxY - minY) * 0.35f).toInt()
        val cy1 = minY + ((maxY - minY) * 0.85f).toInt()
        val cx0 = minX + ((maxX - minX) * 0.25f).toInt()
        val cx1 = minX + ((maxX - minX) * 0.75f).toInt()
        var sx = 0.0
        var sy = 0.0
        var n = 0
        for (y in cy0 until cy1) for (x in cx0 until cx1) {
            if (px[y * w + x] ushr 24 < 128) {
                sx += x
                sy += y
                n++
            }
        }
        if (n < 100) return Triple((minX + maxX) / 2f, (minY + maxY) / 2f, 240f)
        val hcx = (sx / n).toFloat()
        val hcy = (sy / n).toFloat()
        val mid = hcy.roundToInt().coerceIn(0, h - 1)
        val center = w / 2
        var l = center
        while (l > 0 && (px[mid * w + l] ushr 24) < 128) l--
        var r = center
        while (r < w - 1 && (px[mid * w + r] ushr 24) < 128) r++
        var holeW = (r - l).toFloat()
        // Locs/twists can report a narrow mid-gap; keep a sane scale.
        if (holeW < 160f) holeW = 230f
        return Triple(hcx, hcy, holeW)
    }

    /** Shift non-outline hair pixels toward the chosen hair color, keeping shading. */
    private fun tintHair(src: Bitmap, color: Int): Bitmap {
        val tr = (color shr 16) and 0xFF
        val tg = (color shr 8) and 0xFF
        val tb = color and 0xFF
        // Near-black / outline stay; bleached crop already light — light targets skip heavy tint.
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
            if (r + g + b < 40) continue // outline
            val lum = (0.35f * r + 0.45f * g + 0.2f * b) / 255f
            val nr = (tr * lum * 1.15f).toInt().coerceIn(0, 255)
            val ng = (tg * lum * 1.15f).toInt().coerceIn(0, 255)
            val nb = (tb * lum * 1.15f).toInt().coerceIn(0, 255)
            px[i] = (a shl 24) or (nr shl 16) or (ng shl 8) or nb
        }
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    /** Recolor a template jersey toward [primary]/[secondary], preserving black/white. */
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
            if (sum < 45) continue // outline
            if (r > 210 && g > 210 && b > 210) continue // white
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

    private fun dist(r: Int, g: Int, b: Int, r2: Int, g2: Int, b2: Int) =
        abs(r - r2) + abs(g - g2) + abs(b - b2)

    private fun fallback(f: Face, primary: Int, secondary: Int, number: Int, gear: Gear): IntArray {
        // Assets missing: keep a flat team-color card so UI still has something.
        val px = IntArray(N * N)
        val fill = primary or (0xFF shl 24)
        for (i in px.indices) px[i] = fill
        return px
    }
}
