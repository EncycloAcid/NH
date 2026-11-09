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
     * Jersey file stem for a team.
     * - NBA: [slot] (franchise id) → `lakers`, `celtics`, …
     * - College: [abbr] → `college_duke`, `college_unc`, … when we have art; else null (blank recolor).
     * Never fuzzy-match mascot names — "bull" in Bulldogs / college MIA≠Heat.
     */
    fun jerseyKey(abbr: String?, slot: String? = null, name: String? = null): String? {
        val s = slot?.uppercase()?.takeIf { it.isNotBlank() }
        if (s != null) JERSEY_BY_ABBR[s]?.let { return it }
        val a = abbr?.uppercase()?.takeIf { it.isNotBlank() } ?: return null
        COLLEGE_BY_ABBR[a]?.let { return it }
        return null
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

    /** College school abbr → `college_*` jersey asset (48-school pack). */
    private val COLLEGE_BY_ABBR = mapOf(
        "DUKE" to "college_duke", "UNC" to "college_unc", "UK" to "college_kentucky",
        "KU" to "college_kansas", "CONN" to "college_uconn", "NOVA" to "college_villanova",
        "IND" to "college_indiana", "MICH" to "college_michigan", "MSU" to "college_michigan_state",
        "GONZ" to "college_gonzaga", "PUR" to "college_purdue", "UCLA" to "college_ucla",
        "ARIZ" to "college_arizona", "HOU" to "college_houston", "TEX" to "college_texas",
        "TENN" to "college_tennessee", "SYR" to "college_syracuse", "LOU" to "college_louisville",
        "UVA" to "college_virginia", "MARQ" to "college_marquette", "CREI" to "college_creighton",
        "BAY" to "college_baylor", "OSU" to "college_ohio_state", "ILL" to "college_illinois",
        "BAMA" to "college_alabama", "AUB" to "college_auburn", "FLA" to "college_florida",
        "ARK" to "college_arkansas", "ISU" to "college_iowa_state", "SDSU" to "college_san_diego_state",
        "MEM" to "college_memphis", "SJU" to "college_st_johns", "GTWN" to "college_georgetown",
        "NCST" to "college_nc_state", "WIS" to "college_wisconsin", "MD" to "college_maryland",
        "IOWA" to "college_iowa", "ORE" to "college_oregon", "TTU" to "college_texas_tech",
        "BYU" to "college_byu", "XAV" to "college_xavier", "HALL" to "college_seton_hall",
        "PROV" to "college_providence", "SMC" to "college_saint_marys", "DAY" to "college_dayton",
        "VCU" to "college_vcu", "FSU" to "college_florida_state", "MIA" to "college_miami",
    )
}
