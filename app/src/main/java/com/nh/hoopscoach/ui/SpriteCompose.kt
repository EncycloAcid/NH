package com.nh.hoopscoach.ui

import android.graphics.Bitmap
import com.nh.hoopscoach.game.Face
import com.nh.hoopscoach.game.Gear
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
 * Output is a square ARGB bitmap ([N]×[N]), nearest-neighbor scaled for crisp pixels.
 */
object SpriteCompose {
    /** Display / cache size. Source layers are 512 and scaled with nearest-neighbor. */
    const val N = 256
    private const val SRC = 512

    // Chest number on the aligned jersey cutout (collar V at y≈297).
    private const val CHEST_NUM_CX = 256f
    private const val CHEST_NUM_CY = 430f
    private const val SHOULDER_NUM_CX = 320f
    private const val SHOULDER_NUM_CY = 270f

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

        val jersey = resolveJersey(jerseyKey, primary, secondary)
        if (jersey != null) {
            val clean = jersey.copy(Bitmap.Config.ARGB_8888, true)
            eraseGlyphs(clean, CHEST_NUM_CX, CHEST_NUM_CY, halfW = 70, halfH = 55)
            eraseGlyphs(clean, SHOULDER_NUM_CX, SHOULDER_NUM_CY, halfW = 36, halfH = 30)
            blitLayer(canvas, clean)
            if (clean !== jersey) clean.recycle()
            applyChestNumber(canvas, number)
        }

        val headband = when {
            f.hair == 7 -> SpriteSheets.bitmap("character/headbands/03_stealth_black.png")
            else -> SpriteSheets.headband(f.acc)
        }
        blitLayer(canvas, headband)
        blitLayer(canvas, SpriteSheets.eyewear(f.acc))

        val hairSrc = hairBitmap(f)
        if (hairSrc != null) {
            val tinted = tintHair(hairSrc, HAIR_COLORS[f.hairColor.coerceIn(0, HAIR_COLORS.lastIndex)])
            blitLayer(canvas, tinted)
            if (tinted !== hairSrc) tinted.recycle()
        }

        val out = Bitmap.createScaledBitmap(canvas, N, N, false)
        canvas.recycle()
        val px = IntArray(N * N)
        out.getPixels(px, 0, N, 0, 0, N, N)
        if (out !== canvas) out.recycle()
        return px
    }

    /** Draw a 512 layer at (0,0). Smaller legacy crops are centered as a fallback. */
    private fun blitLayer(dst: Bitmap, src: Bitmap?) {
        if (src == null) return
        if (src.width == SRC && src.height == SRC) {
            blitOpaque(dst, src, 0, 0)
            return
        }
        val x = (SRC - src.width) / 2
        val y = (SRC - src.height) / 2
        blitOpaque(dst, src, x, y)
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
                val brightOrDark = sum > 580 || sum < 110
                val tintedDigit = abs(r - fr) + abs(g - fg) + abs(b - fb) > 160 &&
                    (abs(r - g) > 45 || abs(g - b) > 45) && sum in 80..700
                if (brightOrDark || tintedDigit) px[i] = fill
            }
        }
        bmp.setPixels(px, 0, w, 0, 0, w, h)
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
