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
    private const val FACE_CY = 168f
    private const val FACE_HOLE_TARGET = 160f
    private const val JERSEY_SCALE = 1.186f
    private const val JERSEY_X = 102
    private const val JERSEY_Y = 195

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
        val canvasBmp = bodySrc.copy(Bitmap.Config.ARGB_8888, true)
        stripDefaultJersey(canvasBmp)
        val hairSrc = hairBitmap(f)
        // Body sheet ships with a baked afro; clear it for bald or before a hair overlay.
        if (f.hair == 0 || hairSrc != null) stripDefaultHair(canvasBmp)

        val jersey = resolveJersey(jerseyKey, primary, secondary)
        if (jersey != null) {
            val jw = (jersey.width * JERSEY_SCALE).roundToInt()
            val jh = (jersey.height * JERSEY_SCALE).roundToInt()
            val scaled = Bitmap.createScaledBitmap(jersey, jw, jh, false)
            blitOpaque(canvasBmp, scaled, JERSEY_X, JERSEY_Y)
            if (scaled !== jersey) scaled.recycle()
        }

        if (hairSrc != null) {
            val (hx, hy, hw, hh) = hairPlacement(hairSrc)
            val tinted = tintHair(hairSrc, HAIR_COLORS[f.hairColor.coerceIn(0, HAIR_COLORS.lastIndex)])
            val scaled = Bitmap.createScaledBitmap(tinted, hw, hh, false)
            blitOpaque(canvasBmp, scaled, hx, hy)
            if (scaled !== tinted) scaled.recycle()
            if (tinted !== hairSrc) tinted.recycle()
        }

        // Jersey number is baked into team sprites; college recolors keep the template mark.

        val out = Bitmap.createScaledBitmap(canvasBmp, N, N, false)
        canvasBmp.recycle()
        val px = IntArray(N * N)
        out.getPixels(px, 0, N, 0, 0, N, N)
        if (out !== canvasBmp) out.recycle()
        return px
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
        val template = SpriteSheets.jersey("spurs") ?: SpriteSheets.jersey("nets") ?: return null
        return recolorJersey(template, primary or (0xFF shl 24), secondary or (0xFF shl 24))
    }

    /** Transparent-out the baked green jersey / yellow trim / white #1 on the body sheet. */
    private fun stripDefaultJersey(bmp: Bitmap) {
        val w = bmp.width
        val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        for (y in 0 until h) {
            if (y < 230) continue
            for (x in 0 until w) {
                val i = y * w + x
                val c = px[i]
                val a = c ushr 24
                if (a < 32) continue
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                val skinish = r > b + 20 && r > 80 && g < r
                val green = dist(r, g, b, 57, 149, 88) < 90
                val yellow = dist(r, g, b, 220, 200, 60) < 120 && !skinish
                val white = dist(r, g, b, 240, 240, 240) < 40 && y > 260
                if (green || yellow || white) px[i] = 0
            }
        }
        bmp.setPixels(px, 0, w, 0, 0, w, h)
    }

    /** Rough bald: clear dark hair pixels on the crown (keeps face/skin). */
    private fun stripDefaultHair(bmp: Bitmap) {
        val w = bmp.width
        val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        for (y in 0 until min(210, h)) {
            for (x in 0 until w) {
                val i = y * w + x
                val c = px[i]
                if (c ushr 24 < 32) continue
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                val dark = r < 90 && g < 70 && b < 60
                val hairish = dark && r + g + b < 180 && abs(r - g) < 35
                if (hairish) px[i] = 0
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
