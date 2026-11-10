package com.nh.hoopscoach.ui

import android.graphics.Bitmap
import com.nh.hoopscoach.game.Face
import com.nh.hoopscoach.game.Gear

/** Full-canvas source-over composition with jersey-specific number clearance. */
object SpriteCompose {
    const val N = 512

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

    fun render(
        f: Face,
        primary: Int,
        secondary: Int,
        number: Int,
        gear: Gear = Gear(),
        jerseyKey: String? = null,
    ): IntArray {
        val beard = SpriteSheets.beard(f.beard)
        val hair = if (f.hair == 1 && f.hairColor >= 4)
            SpriteSheets.bitmap("character/hairstyles/bleached_blonde_crop.png") else SpriteSheets.hair(f.hair)
        val body = SpriteSheets.body(f.skin)
        val jerseyPath = SpriteSheets.jerseyPath(jerseyKey)
        val mouth = SpriteSheets.mouth(f.mouth)?.let { if (body != null) tintMouth(body, it) else it }
        return composite(listOfNotNull(
            body,
            SpriteSheets.bitmap(jerseyPath),
            SpriteSheets.chestNumber(number)?.let { positionNumber(it, jerseyPath) },
            beard,
            mouth,
            SpriteSheets.eyes(f.eyes),
            hair,
            SpriteSheets.headband(if (f.hair == 7 && f.acc == 0) 3 else f.acc)?.let { occludeHeadband(it, hair) },
            SpriteSheets.eyewear(f.acc),
        ), f.hairColor, setOfNotNull(beard, hair))
    }

    fun renderLook(look: PlayerLook, jerseyKey: String?, number: Int): IntArray {
        val layers = lookLayers(look, jerseyKey, number).mapNotNull { path ->
            SpriteSheets.bitmap(path)?.let { path to it }
        }
        val colored = layers.filter { it.first.startsWith("character/hairstyles/") || it.first.startsWith("character/beards/") }
            .map { it.second }.toSet()
        val body = layers.firstOrNull { it.first.startsWith("character/bodies/") }?.second
        val hair = layers.firstOrNull { it.first.startsWith("character/hairstyles/") }?.second
        val jerseyPath = SpriteSheets.jerseyPath(jerseyKey)
        return composite(layers.map { (path, bitmap) ->
            when {
                body != null && path.startsWith("character/mouths/") -> tintMouth(body, bitmap)
                path.startsWith("character/numbers/") -> positionNumber(bitmap, jerseyPath)
                path.startsWith("character/headbands/") -> occludeHeadband(bitmap, hair)
                else -> bitmap
            }
        }, look.hairColorIndex, colored)
    }

    internal fun occludeHeadband(band: Bitmap, hair: Bitmap?): Bitmap {
        require(band.width == N && band.height == N && (hair == null || hair.width == N && hair.height == N)) {
            "Avatar layers must be ${N}x$N"
        }
        if (hair == null) return band
        val pixels = IntArray(N * N)
        val hairPixels = IntArray(N * N)
        band.getPixels(pixels, 0, N, 0, 0, N, N)
        hair.getPixels(hairPixels, 0, N, 0, 0, N, N)
        val embeddedBand = setOf(0xFFAE1C24.toInt(), 0xFFCE3137.toInt(), 0xFF71121A.toInt(), 0xFF301217.toInt())
        for (i in pixels.indices) {
            if (hairPixels[i] ushr 24 == 255 && hairPixels[i] !in embeddedBand) pixels[i] = 0
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
