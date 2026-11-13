package com.nh.hoopscoach.ui

import android.graphics.Bitmap
import com.nh.hoopscoach.game.Face
import com.nh.hoopscoach.game.Gear

/** Full-canvas source-over composition with jersey-specific number clearance, and a team-colored jersey for teams without art. */
object SpriteCompose {
    const val N = 512
    private val EMBEDDED_BAND_COLORS = setOf(0xFFAE1C24.toInt(), 0xFFCE3137.toInt(), 0xFF71121A.toInt(), 0xFF301217.toInt())

    fun lookLayers(look: PlayerLook, jerseyKey: String?, number: Int): List<String> {
        fun path(folder: String, file: String?) = file?.let { "character/$folder/$it.png" }
        return listOfNotNull(
            path("bodies", PlayerLook.skinTones.getOrElse(look.skinToneIndex) { "04_medium_brown" }),
            SpriteSheets.jerseyPath(jerseyKey),
            "character/numbers/${number.coerceIn(0, 99)}.png",
            path("beards", PlayerLook.beards.getOrNull(look.beardIndex)),
            path("mouths", PlayerLook.mouths.getOrNull(look.mouthIndex)),
            path("eyes", PlayerLook.eyes.getOrNull(look.eyeIndex)),
            path("hairstyles", PlayerLook.hairstyles.getOrNull(look.hairIndex)),
            path("headbands", PlayerLook.headbands.getOrNull(look.headbandIndex)),
            path("eyewear", PlayerLook.eyewear.getOrNull(look.eyewearIndex)),
        )
    }

    /**
     * One portrait. A team with jersey art ([jerseyKey]) wears it; any other team wears a jersey in its own
     * [primary] and [secondary] colors with [label] (its abbreviation) across the chest.
     */
    fun render(
        f: Face,
        primary: Int,
        secondary: Int,
        number: Int,
        gear: Gear = Gear(),
        jerseyKey: String? = null,
        label: String? = null,
    ): IntArray {
        val beard = SpriteSheets.beard(f.beard)
        val rawHair = if (f.hair == 1 && f.hairColor >= 4)
            SpriteSheets.bitmap("character/hairstyles/bleached_blonde_crop.png") else SpriteSheets.hair(f.hair)
        val headband = SpriteSheets.headband(if (f.hair == 7 && f.acc == 0) 3 else f.acc)
        val hair = if (f.hair == 7 && rawHair != null) occludeEmbeddedBand(rawHair, headband) else rawHair
        val body = SpriteSheets.body(f.skin)?.let { softenCheekHighlights(it) }
        val art = SpriteSheets.jerseyArt(jerseyKey)
        // No art for this team: its own colors, not another school's jersey.
        val jersey = art?.let { SpriteSheets.bitmap(it) } ?: teamJersey(primary, secondary, label)
        val jerseyPath = if (art != null) art else if (jersey != null) SpriteSheets.TEAM_JERSEY else SpriteSheets.jerseyPath(null)
        val mouth = SpriteSheets.mouth(f.mouth)?.let { if (body != null) tintMouth(body, it) else it }
        return composite(listOfNotNull(
            body,
            jersey ?: SpriteSheets.bitmap(jerseyPath),
            SpriteSheets.chestNumber(number)?.let { positionNumber(it, jerseyPath) },
            beard,
            mouth,
            SpriteSheets.eyes(f.eyes)?.let { if (body != null) tintEyes(body, it) else it },
            hair,
            headband?.let { occludeHeadband(it, rawHair, body) },
            SpriteSheets.eyewear(f.acc),
        ), f.hairColor, setOfNotNull(beard, hair))
    }

    // ---------- a jersey in the team's own colors ----------

    /** How bright a color looks (0 black to 1 white). */
    private fun brightness(c: Int): Double {
        fun channel(v: Int): Double {
            val x = v / 255.0
            return if (x <= 0.03928) x / 12.92 else Math.pow((x + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel((c ushr 16) and 255) + 0.7152 * channel((c ushr 8) and 255) + 0.0722 * channel(c and 255)
    }

    private fun contrast(a: Int, b: Int): Double {
        val x = brightness(a) + 0.05
        val y = brightness(b) + 0.05
        return maxOf(x, y) / minOf(x, y)
    }

    /** The cloth and trim colors of a team's jersey, as opaque RGB. */
    internal fun teamColors(primary: Int, secondary: Int): Pair<Int, Int> {
        var cloth = primary or 0xFF000000.toInt()
        var trim = secondary or 0xFF000000.toInt()
        // The chest number is white, so a near-white school color goes on the trim and the other one makes the cloth.
        if (brightness(cloth) > 0.8 && contrast(cloth, trim) >= 2.0) cloth = trim.also { trim = cloth }
        // Two colors too alike to tell apart: white piping on a dark jersey, dark piping on a light one.
        if (contrast(cloth, trim) < 1.6) trim = if (brightness(cloth) < 0.4) 0xFFFFFFFF.toInt() else 0xFF14161C.toInt()
        return cloth to trim
    }

    /** One channel of a base color at a template shade: 128 is the color itself, below it folds, above it highlights. */
    private fun shaded(base: Int, shade: Int): Int =
        if (shade <= 128) base * shade / 128 else base + (255 - base) * ((shade - 128) * 55 / 100) / 128

    /**
     * The jersey of a team with no art of its own: the neutral template in the team's colors, with its
     * abbreviation across the chest in the trim color. Cached per colors and wording.
     */
    internal fun teamJersey(primary: Int, secondary: Int, label: String?): Bitmap? {
        val template = SpriteSheets.bitmap(SpriteSheets.TEAM_JERSEY) ?: return null
        if (template.width != N || template.height != N) return null
        val word = label.orEmpty().uppercase().filter { it in LETTERS }.take(5)
        return SpriteSheets.derived("team:${primary and 0xFFFFFF}:${secondary and 0xFFFFFF}:$word") {
            val (cloth, trim) = teamColors(primary, secondary)
            // Black cloth is lifted to charcoal so its folds still show.
            val clothRgb = intArrayOf(maxOf((cloth ushr 16) and 255, 30), maxOf((cloth ushr 8) and 255, 30), maxOf(cloth and 255, 30))
            val trimRgb = intArrayOf((trim ushr 16) and 255, (trim ushr 8) and 255, trim and 255)
            val px = IntArray(N * N)
            template.getPixels(px, 0, N, 0, 0, N, N)
            for (i in px.indices) {
                val t = px[i]
                if (t ushr 24 == 0) { px[i] = 0; continue }
                val shade = (t ushr 16) and 255
                val base = if (((t ushr 8) and 255) > 127) trimRgb else clothRgb
                px[i] = 0xFF000000.toInt() or (shaded(base[0], shade) shl 16) or (shaded(base[1], shade) shl 8) or shaded(base[2], shade)
            }
            if (word.isNotEmpty()) {
                val scale = if (word.length <= 3) 5 else 4
                val gap = if (word.length <= 4) scale else 3
                val width = word.length * 5 * scale + (word.length - 1) * gap
                val left = N / 2 - width / 2 + 1
                val top = 332
                val shadow = 0xFF000000.toInt() or ((clothRgb[0] * 45 / 100) shl 16) or ((clothRgb[1] * 45 / 100) shl 8) or (clothRgb[2] * 45 / 100)
                val ink = 0xFF000000.toInt() or (trimRgb[0] shl 16) or (trimRgb[1] shl 8) or trimRgb[2]
                // A drop shadow first, then the letters on top of it.
                for ((offset, color) in listOf(scale / 2 + 1 to shadow, 0 to ink)) {
                    var x = left + offset
                    for (ch in word) {
                        val rows = LETTERS.getValue(ch)
                        for (ry in 0 until 7) for (rx in 0 until 5) {
                            if (rows[ry][rx] != '1') continue
                            for (dy in 0 until scale) for (dx in 0 until scale) {
                                val yy = top + offset + ry * scale + dy
                                val xx = x + rx * scale + dx
                                if (xx !in 0 until N || yy !in 0 until N) continue
                                val i = yy * N + xx
                                if (px[i] ushr 24 != 0) px[i] = color      // never outside the jersey
                            }
                        }
                        x += 5 * scale + gap
                    }
                }
            }
            Bitmap.createBitmap(px, N, N, Bitmap.Config.ARGB_8888)
        }
    }

    /** Block capitals and digits on a 5x7 grid, for the chest of a team-colored jersey. */
    private val LETTERS: Map<Char, List<String>> = mapOf(
        'A' to listOf("01110", "10001", "10001", "11111", "10001", "10001", "10001"),
        'B' to listOf("11110", "10001", "10001", "11110", "10001", "10001", "11110"),
        'C' to listOf("01111", "10000", "10000", "10000", "10000", "10000", "01111"),
        'D' to listOf("11110", "10001", "10001", "10001", "10001", "10001", "11110"),
        'E' to listOf("11111", "10000", "10000", "11110", "10000", "10000", "11111"),
        'F' to listOf("11111", "10000", "10000", "11110", "10000", "10000", "10000"),
        'G' to listOf("01111", "10000", "10000", "10011", "10001", "10001", "01111"),
        'H' to listOf("10001", "10001", "10001", "11111", "10001", "10001", "10001"),
        'I' to listOf("11111", "00100", "00100", "00100", "00100", "00100", "11111"),
        'J' to listOf("00111", "00010", "00010", "00010", "00010", "10010", "01100"),
        'K' to listOf("10001", "10010", "10100", "11000", "10100", "10010", "10001"),
        'L' to listOf("10000", "10000", "10000", "10000", "10000", "10000", "11111"),
        'M' to listOf("10001", "11011", "10101", "10101", "10001", "10001", "10001"),
        'N' to listOf("10001", "11001", "10101", "10011", "10001", "10001", "10001"),
        'O' to listOf("01110", "10001", "10001", "10001", "10001", "10001", "01110"),
        'P' to listOf("11110", "10001", "10001", "11110", "10000", "10000", "10000"),
        'Q' to listOf("01110", "10001", "10001", "10001", "10101", "10010", "01101"),
        'R' to listOf("11110", "10001", "10001", "11110", "10100", "10010", "10001"),
        'S' to listOf("01111", "10000", "10000", "01110", "00001", "00001", "11110"),
        'T' to listOf("11111", "00100", "00100", "00100", "00100", "00100", "00100"),
        'U' to listOf("10001", "10001", "10001", "10001", "10001", "10001", "01110"),
        'V' to listOf("10001", "10001", "10001", "10001", "10001", "01010", "00100"),
        'W' to listOf("10001", "10001", "10001", "10101", "10101", "11011", "10001"),
        'X' to listOf("10001", "10001", "01010", "00100", "01010", "10001", "10001"),
        'Y' to listOf("10001", "10001", "01010", "00100", "00100", "00100", "00100"),
        'Z' to listOf("11111", "00001", "00010", "00100", "01000", "10000", "11111"),
        '0' to listOf("01110", "10001", "10011", "10101", "11001", "10001", "01110"),
        '1' to listOf("00100", "01100", "00100", "00100", "00100", "00100", "01110"),
        '2' to listOf("01110", "10001", "00001", "00010", "00100", "01000", "11111"),
        '3' to listOf("11110", "00001", "00001", "01110", "00001", "00001", "11110"),
        '4' to listOf("00010", "00110", "01010", "10010", "11111", "00010", "00010"),
        '5' to listOf("11111", "10000", "11110", "00001", "00001", "10001", "01110"),
        '6' to listOf("00110", "01000", "10000", "11110", "10001", "10001", "01110"),
        '7' to listOf("11111", "00001", "00010", "00100", "01000", "01000", "01000"),
        '8' to listOf("01110", "10001", "10001", "01110", "10001", "10001", "01110"),
        '9' to listOf("01110", "10001", "10001", "01111", "00001", "00010", "01100"),
    )

    /**
     * A portrait at half size for rows and cards: each pixel is the average of a 2x2 block, weighted by
     * alpha so the silhouette's edge doesn't darken. A quarter of the memory of the full picture.
     */
    fun half(px: IntArray): IntArray {
        require(px.size == N * N) { "A portrait is ${N}x$N" }
        val h = N / 2
        val out = IntArray(h * h)
        for (y in 0 until h) for (x in 0 until h) {
            val i = y * 2 * N + x * 2
            var a = 0
            var r = 0
            var g = 0
            var b = 0
            for (k in 0..3) {
                val c = px[i + (k and 1) + (k shr 1) * N]
                val alpha = c ushr 24
                a += alpha
                r += ((c ushr 16) and 255) * alpha
                g += ((c ushr 8) and 255) * alpha
                b += (c and 255) * alpha
            }
            out[y * h + x] = if (a == 0) 0 else (((a + 2) / 4) shl 24) or ((r / a) shl 16) or ((g / a) shl 8) or (b / a)
        }
        return out
    }

    fun renderLook(look: PlayerLook, jerseyKey: String?, number: Int): IntArray {
        val layers = lookLayers(look, jerseyKey, number).mapNotNull { path ->
            SpriteSheets.bitmap(path)?.let { path to it }
        }
        val body = layers.firstOrNull { it.first.startsWith("character/bodies/") }?.second?.let { softenCheekHighlights(it) }
        val rawHair = layers.firstOrNull { it.first.startsWith("character/hairstyles/") }?.second
        val headband = layers.firstOrNull { it.first.startsWith("character/headbands/") }?.second
        val hair = if (look.hairIndex == PlayerLook.hairstyles.indexOf("headband_with_curls") && rawHair != null)
            occludeEmbeddedBand(rawHair, headband) else rawHair
        val colored = layers.filter { it.first.startsWith("character/hairstyles/") || it.first.startsWith("character/beards/") }
            .map { it.second }.toSet() + listOfNotNull(hair)
        val jerseyPath = SpriteSheets.jerseyPath(jerseyKey)
        return composite(layers.map { (path, bitmap) ->
            when {
                body != null && path.startsWith("character/bodies/") -> body
                body != null && path.startsWith("character/mouths/") -> tintMouth(body, bitmap)
                body != null && path.startsWith("character/eyes/") -> tintEyes(body, bitmap)
                path.startsWith("character/numbers/") -> positionNumber(bitmap, jerseyPath)
                path.startsWith("character/hairstyles/") -> hair ?: bitmap
                path.startsWith("character/headbands/") -> occludeHeadband(bitmap, rawHair, body)
                else -> bitmap
            }
        }, look.hairColorIndex, colored)
    }

    internal fun softenCheekHighlights(body: Bitmap): Bitmap {
        require(body.width == N && body.height == N) { "Avatar layers must be ${N}x$N" }
        val highlight = body.getPixel(212, 160)
        if (highlight ushr 24 == 0) return body
        val pixels = IntArray(N * N)
        body.getPixels(pixels, 0, N, 0, 0, N, N)
        val mask = IntArray(N * N)
        for (y in 155..165) for (x in (201..223) + (288..310)) {
            if (pixels[y * N + x] == highlight) mask[y * N + x] = -1
        }
        for (y in 155..165) for (x in (201..223) + (288..310)) {
            if (mask[y * N + x] == 0) continue
            val skin = surroundingSkin(body, mask, x, y)
            fun channel(shift: Int) = (((highlight ushr shift) and 255) + ((skin ushr shift) and 255) * 7) / 8
            pixels[y * N + x] = (highlight and 0xFF000000.toInt()) or
                (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
        }
        return Bitmap.createBitmap(pixels, N, N, Bitmap.Config.ARGB_8888)
    }

    internal fun tintEyes(body: Bitmap, eyes: Bitmap): Bitmap {
        require(body.width == N && body.height == N && eyes.width == N && eyes.height == N) {
            "Avatar layers must be ${N}x$N"
        }
        val pixels = IntArray(N * N)
        eyes.getPixels(pixels, 0, N, 0, 0, N, N)
        val result = pixels.copyOf()
        val skinPixels = eyeSkin(body)
        for (i in pixels.indices) {
            val value = pixels[i]
            val shade = (value ushr 16) and 255
            if (value ushr 24 == 0 || (value and 0xFFFF) != shade) continue
            val skin = skinPixels[i]
            fun channel(shift: Int) = (((skin ushr shift) and 255) * shade / 224).coerceAtMost(255)
            result[i] = (value and 0xFF000000.toInt()) or
                (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
        }
        return Bitmap.createBitmap(result, N, N, Bitmap.Config.ARGB_8888)
    }

    private fun eyeSkin(body: Bitmap): IntArray {
        val result = IntArray(N * N)
        for (left in listOf(197, 262)) {
            val channels = Array(3) { IntArray(53 * 42) }
            for (y in 0..41) for (x in 0..52) {
                val value = body.getPixel(left + x, 114 + y)
                for (c in 0..2) channels[c][y * 53 + x] = ((value ushr (16 - c * 8)) and 255) * 256
            }
            for (channel in channels) {
                for (y in 1..40) for (x in 1..51) {
                    channel[y * 53 + x] = (channel[x] * (41 - y) + channel[41 * 53 + x] * y) / 41
                }
                repeat(96) {
                    for (y in 1..40) for (x in 1..51) {
                        val i = y * 53 + x
                        channel[i] = (channel[i - 1] + channel[i + 1] + channel[i - 53] + channel[i + 53]) / 4
                    }
                }
            }
            for (y in 1..40) for (x in 1..51) {
                val i = y * 53 + x
                result[(114 + y) * N + left + x] = 0xFF000000.toInt() or
                    ((channels[0][i] / 256) shl 16) or ((channels[1][i] / 256) shl 8) or (channels[2][i] / 256)
            }
        }
        return result
    }

    private fun surroundingSkin(body: Bitmap, mask: IntArray, x: Int, y: Int): Int {
        val left = if (x < 256) 198 else 261
        val right = if (x < 256) 250 else 313
        var red = 0
        var green = 0
        var blue = 0
        var total = 0
        for ((dx, dy) in listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) {
            for (distance in 1..56) {
                val xx = x + dx * distance
                val yy = y + dy * distance
                if (xx !in left..right || yy !in 112..167) break
                if (mask[yy * N + xx] ushr 24 != 0) continue
                val sample = body.getPixel(xx, yy)
                if (sample ushr 24 == 0) continue
                val weight = maxOf(1, 1024 / (distance * distance))
                red += ((sample ushr 16) and 255) * weight
                green += ((sample ushr 8) and 255) * weight
                blue += (sample and 255) * weight
                total += weight
                break
            }
        }
        return if (total == 0) body.getPixel(x, y) else
            0xFF000000.toInt() or ((red / total) shl 16) or ((green / total) shl 8) or (blue / total)
    }

    internal fun occludeEmbeddedBand(hair: Bitmap, band: Bitmap?): Bitmap {
        require(hair.width == N && hair.height == N && (band == null || band.width == N && band.height == N)) {
            "Avatar layers must be ${N}x$N"
        }
        if (band == null) return hair
        val pixels = IntArray(N * N)
        hair.getPixels(pixels, 0, N, 0, 0, N, N)
        for (i in pixels.indices) if (pixels[i] in EMBEDDED_BAND_COLORS) pixels[i] = 0
        return Bitmap.createBitmap(pixels, N, N, Bitmap.Config.ARGB_8888)
    }

    internal fun occludeHeadband(band: Bitmap, hair: Bitmap?, body: Bitmap? = null): Bitmap {
        require(band.width == N && band.height == N && (hair == null || hair.width == N && hair.height == N) &&
            (body == null || body.width == N && body.height == N)) {
            "Avatar layers must be ${N}x$N"
        }
        if (hair == null && body == null) return band
        val pixels = IntArray(N * N)
        val hairPixels = IntArray(N * N)
        band.getPixels(pixels, 0, N, 0, 0, N, N)
        hair?.getPixels(hairPixels, 0, N, 0, 0, N, N)
        val bodyPixels = body?.let { IntArray(N * N).also { pixels -> it.getPixels(pixels, 0, N, 0, 0, N, N) } }
        for (i in pixels.indices) {
            if (bodyPixels != null && bodyPixels[i] ushr 24 == 0 ||
                hairPixels[i] ushr 24 == 255 && hairPixels[i] !in EMBEDDED_BAND_COLORS) pixels[i] = 0
        }
        return Bitmap.createBitmap(pixels, N, N, Bitmap.Config.ARGB_8888)
    }

    internal fun positionNumber(number: Bitmap, jerseyPath: String): Bitmap {
        require(number.width == N && number.height == N) { "Avatar layers must be ${N}x$N" }
        val offset = when (jerseyPath.removePrefix("character/")) {
            "nba_jerseys/grizzlies_retro.png" -> 48
            "nba_jerseys/jazz.png" -> 24
            "nba_jerseys/supersonics.png" -> 40
            "nba_jerseys/wizards.png" -> 16
            "nba_jerseys/bulls.png" -> 12
            "nba_jerseys/hawks.png" -> 16
            "nba_jerseys/warriors.png" -> 16
            "college_jerseys/23_ohio_state.png" -> 12
            else -> return number
        }
        val pixels = IntArray(N * (N - offset))
        number.getPixels(pixels, 0, N, 0, 0, N, N - offset)
        return Bitmap.createBitmap(N, N, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, N, 0, offset, N, N - offset)
        }
    }

    internal fun tintMouth(body: Bitmap, mouth: Bitmap): Bitmap {
        require(body.width == N && body.height == N && mouth.width == N && mouth.height == N) {
            "Avatar layers must be ${N}x$N"
        }
        val pixels = IntArray(N * N)
        mouth.getPixels(pixels, 0, N, 0, 0, N, N)
        for (y in 193..218) {
            val left = body.getPixel(226, y)
            val right = body.getPixel(286, y)
            for (x in 229..282) {
                val value = pixels[y * N + x]
                val shade = (value ushr 16) and 255
                if (value ushr 24 == 0 || (value and 0xFFFF) != shade) continue
                fun channel(shift: Int): Int {
                    val skin = (((left ushr shift) and 255) * (286 - x) + ((right ushr shift) and 255) * (x - 226)) / 60
                    return skin * shade / (if (shift == 16) 192 else 224)
                }
                pixels[y * N + x] = (value and 0xFF000000.toInt()) or
                    (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
            }
        }
        return Bitmap.createBitmap(pixels, N, N, Bitmap.Config.ARGB_8888)
    }

    private val hairColors = intArrayOf(
        0x2E2A2A, 0x4A2E1E, 0x6E4628, 0xA2562A, 0xDAB458, 0xECE6D4, 0x8E9096, 0xC22E2E,
    )

    internal fun composite(layers: List<Bitmap>, hairColor: Int = 0, coloredLayers: Set<Bitmap> = emptySet()): IntArray {
        val out = IntArray(N * N)
        val src = IntArray(N * N)
        for (layer in layers) {
            require(layer.width == N && layer.height == N) { "Avatar layers must be ${N}x$N" }
            layer.getPixels(src, 0, N, 0, 0, N, N)
            val colorHair = hairColor in 1..hairColors.lastIndex && layer in coloredLayers
            var hairReference = 80f
            if (colorHair) {
                val histogram = IntArray(256)
                var count = 0
                for (pixel in src) {
                    if (pixel ushr 24 == 0) continue
                    val r = (pixel ushr 16) and 255
                    val g = (pixel ushr 8) and 255
                    val b = pixel and 255
                    if (r + g + b < 40) continue
                    histogram[(r * 35 + g * 45 + b * 20) / 100]++
                    count++
                }
                var accumulated = 0
                for (value in histogram.indices) {
                    accumulated += histogram[value]
                    if (count > 0 && accumulated * 10 >= count * 9) {
                        hairReference = maxOf(80f, value.toFloat())
                        break
                    }
                }
            }
            for (i in src.indices) {
                var s = src[i]
                if (s ushr 24 == 0) continue
                if (colorHair) {
                    val r = (s ushr 16) and 255
                    val g = (s ushr 8) and 255
                    val b = s and 255
                    if (r + g + b >= 40) {
                        val shade = ((r * 0.35f + g * 0.45f + b * 0.2f) / hairReference).coerceAtMost(1.5f)
                        val color = hairColors[hairColor]
                        fun tinted(shift: Int) = (((color ushr shift) and 255) * shade).toInt().coerceIn(0, 255)
                        s = (s and 0xFF000000.toInt()) or (tinted(16) shl 16) or (tinted(8) shl 8) or tinted(0)
                    }
                }
                val sa = s ushr 24
                if (sa == 0) continue
                if (sa == 255) { out[i] = s; continue }
                val d = out[i]
                val da = d ushr 24
                val inv = 255 - sa
                val a = sa + da * inv / 255
                fun channel(shift: Int) =
                    ((((s ushr shift) and 255) * sa + ((d ushr shift) and 255) * da * inv / 255) / a)
                out[i] = (a shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
            }
        }
        return out
    }
}
