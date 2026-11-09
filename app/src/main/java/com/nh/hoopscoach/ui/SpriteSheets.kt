package com.nh.hoopscoach.ui

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Loads the user's modular basketball sprites from `assets/sprites/` (bodies, jerseys, hairstyles).
 * Call [install] once with the app's [AssetManager]; unit tests can skip it and read from disk.
 */
object SpriteSheets {
    @Volatile private var assets: AssetManager? = null
    private val cache = ConcurrentHashMap<String, Bitmap>()

    fun install(am: AssetManager) {
        assets = am
    }

    fun bitmap(path: String): Bitmap? {
        cache[path]?.let { return it }
        val bmp = open(path)?.use { BitmapFactory.decodeStream(it) } ?: return null
        val immutable = if (bmp.isMutable) bmp.copy(Bitmap.Config.ARGB_8888, false) else bmp
        cache[path] = immutable
        return immutable
    }

    fun body(skin: Int): Bitmap? {
        val file = BODY_FILES[skin.coerceIn(0, BODY_FILES.lastIndex)]
        return bitmap("sprites/bodies/$file.png")
    }

    fun hair(style: Int): Bitmap? {
        val file = HAIR_FILES[style] ?: return null
        return bitmap("sprites/hairstyles/$file.png")
    }

    fun jersey(key: String): Bitmap? = bitmap("sprites/jerseys/$key.png")

    /** Chest number 0–99 (and `00` via [zeroZero]). */
    fun chestNumber(number: Int, zeroZero: Boolean = false): Bitmap? {
        val name = when {
            zeroZero -> "00"
            number in 0..99 -> number.toString()
            else -> number.coerceIn(0, 99).toString()
        }
        return bitmap("sprites/numbers/chest/$name.png")
    }

    fun shoulderDigit(digit: Int): Bitmap? =
        bitmap("sprites/numbers/shoulder/${digit.coerceIn(0, 9)}.png")

    /** Isolated eyes (+ brows). Index 0 = keep the body's baked eyes. */
    fun eyes(style: Int): Bitmap? {
        val file = EYE_FILES.getOrNull(style) ?: return null
        return bitmap("sprites/eyes/$file.png")
    }

    fun mouth(style: Int): Bitmap? {
        val file = MOUTH_FILES.getOrNull(style) ?: return null
        return bitmap("sprites/mouths/$file.png")
    }

    fun beard(style: Int): Bitmap? {
        val file = BEARD_FILES.getOrNull(style) ?: return null
        return bitmap("sprites/beards/$file.png")
    }

    fun headband(style: Int): Bitmap? {
        val file = HEADBAND_FILES.getOrNull(style) ?: return null
        return bitmap("sprites/headbands/$file.png")
    }

    fun eyewear(style: Int): Bitmap? {
        val file = EYEWEAR_FILES.getOrNull(style) ?: return null
        return bitmap("sprites/eyewear/$file.png")
    }

    /**
     * NBA franchise → jersey file stem.
     * Only [slot] (today's franchise id) is trusted — college abbrs like MIA/IND/HOU collide with NBA
     * codes, and mascot substrings ("bull" in Bulldogs, "sun" in Sun Devils) false-match.
     * College teams leave [slot] empty → null → blank recolor in [SpriteCompose].
     */
    fun jerseyKey(abbr: String?, slot: String? = null, name: String? = null): String? {
        val s = slot?.uppercase()?.takeIf { it.isNotBlank() } ?: return null
        return JERSEY_BY_ABBR[s]
    }

    private fun open(path: String): InputStream? {
        try {
            assets?.open(path)?.let { return it }
        } catch (_: Exception) { /* fall through to disk */ }
        val candidates = listOf(
            File("src/main/assets/$path"),
            File("app/src/main/assets/$path"),
            File("../app/src/main/assets/$path"),
            File(path),
        )
        return candidates.firstOrNull { it.isFile }?.inputStream()
    }

    /** Face.skin 0..7 (porcelain→ebony) → body file (pale→deep). */
    private val BODY_FILES = arrayOf(
        "08_pale_ivory",
        "07_fair_peach",
        "06_olive_bronze",
        "05_warm_caramel",
        "04_medium_brown",
        "03_chestnut_brown",
        "02_dark_chocolate",
        "01_deep_ebony",
    )

    /**
     * Face.hair index → hairstyle file. Null = bald (no overlay; body hair is stripped).
     * Extra styles (puffs, bleached crop) are reached via nearby options / hair color.
     */
    val HAIR_FILES = arrayOf(
        null,                    // 0 Bald
        "buzz_cut",              // 1 Buzz
        "slicked_back_hair",     // 2 Short / slicked
        "curly_top_fade",        // 3 Fade
        "flat_top",              // 4 Flat-top
        "afro",                  // 5 Afro
        "high_top_fade",         // 6 High-top
        "curly_top_fade",        // 7 Headband (curls + separate headband overlay; pack file is broken)
        "cornrows",              // 8 Cornrows
        "dreadlocks",            // 9 Locs
        "twists",                // 10 Long / twists
        "mohawk",                // 11 Mohawk
        "messy_waves",           // 12 Waves
        "braided_top_bun",       // 13 Man bun / top bun
    )

    // Face.eyes 0 = default (no overlay); 1..6 map to files.
    private val EYE_FILES = arrayOf(
        null,
        "01_intense_focused",
        "02_clutch_stare",
        "03_confident_swagger",
        "04_alert_court_vision",
        "05_light_blue_eyes",
        "06_emerald_green_eyes",
    )

    // Face.mouth 0 = neutral (no overlay).
    private val MOUTH_FILES = arrayOf(
        null,
        "01_confident_grin",
        "02_game_face_shout",
        "03_athletic_mouthguard",
    )

    // Face.beard 0 = none; pack has stubble / goatee / full.
    private val BEARD_FILES = arrayOf(
        null,
        "03_stubble_shadow", // Stubble
        "02_goatee",         // Mustache → closest
        "02_goatee",         // Goatee
        "02_goatee",         // Chin strap → closest
        "01_full_beard",     // Full beard
    )

    // Face.acc headband slots.
    private val HEADBAND_FILES = arrayOf(
        null,
        "01_white_headband",
        "02_red_headband",
        "03_black_headband",
        null, // goggles
        null, // mask
    )

    private val EYEWEAR_FILES = arrayOf(
        null, null, null, null,
        "01_sports_rec_specs",
        "02_protective_clear_mask",
    )

    private val JERSEY_BY_ABBR = mapOf(
        "ATL" to "hawks", "BOS" to "celtics", "BKN" to "nets", "NJN" to "nets",
        "CHA" to "hornets", "CHH" to "hornets", "CHO" to "hornets",
        "CHI" to "bulls", "CLE" to "cavs", "DET" to "pistons", "IND" to "pacers",
        "MIA" to "heat", "MIL" to "bucks", "NYK" to "knicks", "ORL" to "magic",
        "PHI" to "76ers", "TOR" to "raptors", "WAS" to "wizards", "WSB" to "wizards",
        "DAL" to "mavs", "DEN" to "nuggets", "GSW" to "warriors", "GS" to "warriors",
        "HOU" to "rockets", "LAC" to "clippers", "LAL" to "lakers",
        "MEM" to "grizzlies_memphis", "VAN" to "grizzlies_retro",
        "MIN" to "timberwolves", "NOP" to "pelicans", "NOH" to "pelicans", "NOK" to "pelicans",
        "OKC" to "thunder", "SEA" to "supersonics",
        "PHX" to "suns", "PHO" to "suns", "POR" to "trail_blazers",
        "SAC" to "kings", "SAS" to "spurs", "SA" to "spurs", "UTA" to "jazz", "UTH" to "jazz",
    )

    private val JERSEY_BY_NAME = mapOf(
        "hawk" to "hawks", "celtic" to "celtics", "net" to "nets", "hornet" to "hornets",
        "bull" to "bulls", "cavalier" to "cavs", "piston" to "pistons", "pacer" to "pacers",
        "heat" to "heat", "buck" to "bucks", "knick" to "knicks", "magic" to "magic",
        "76er" to "76ers", "sixer" to "76ers", "raptor" to "raptors", "wizard" to "wizards",
        "bullet" to "wizards", "maverick" to "mavs", "nugget" to "nuggets", "warrior" to "warriors",
        "rocket" to "rockets", "clipper" to "clippers", "laker" to "lakers",
        "grizzlie" to "grizzlies_memphis", "timberwolf" to "timberwolves", "pelican" to "pelicans",
        "thunder" to "thunder", "sonic" to "supersonics", "sun" to "suns",
        "blazer" to "trail_blazers", "king" to "kings", "spur" to "spurs", "jazz" to "jazz",
    )
}
