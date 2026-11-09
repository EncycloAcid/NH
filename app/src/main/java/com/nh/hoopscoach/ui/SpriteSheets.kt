package com.nh.hoopscoach.ui

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Loads modular 512×512 character sprites from `assets/character/`.
 * Every full-canvas layer is pre-aligned — draw at (0,0) with no offsets.
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
        return bitmap("character/bodies/$file.png")
    }

    fun hair(style: Int): Bitmap? {
        val file = HAIR_FILES[style] ?: return null
        return bitmap("character/hairstyles/$file.png")
    }

    /** NBA key (`lakers`) or college key (`01_duke`). */
    fun jersey(key: String): Bitmap? {
        bitmap("character/nba_jerseys/$key.png")?.let { return it }
        bitmap("character/college_jerseys/$key.png")?.let { return it }
        return null
    }

    /** Chest number 0–99 (and `00` via [zeroZero]). */
    fun chestNumber(number: Int, zeroZero: Boolean = false): Bitmap? {
        val name = when {
            zeroZero -> "00"
            number in 0..99 -> number.toString()
            else -> number.coerceIn(0, 99).toString()
        }
        return bitmap("character/numbers/chest/$name.png")
    }

    fun shoulderDigit(digit: Int): Bitmap? =
        bitmap("character/numbers/shoulder/${digit.coerceIn(0, 9)}.png")

    /** Isolated eyes (+ brows). Index 0 = keep the body's baked eyes. */
    fun eyes(style: Int): Bitmap? {
        val file = EYE_FILES.getOrNull(style) ?: return null
        return bitmap("character/eyes/$file.png")
    }

    fun mouth(style: Int): Bitmap? {
        val file = MOUTH_FILES.getOrNull(style) ?: return null
        return bitmap("character/mouths/$file.png")
    }

    fun beard(style: Int): Bitmap? {
        val file = BEARD_FILES.getOrNull(style) ?: return null
        return bitmap("character/beards/$file.png")
    }

    fun headband(style: Int): Bitmap? {
        val file = HEADBAND_FILES.getOrNull(style) ?: return null
        return bitmap("character/headbands/$file.png")
    }

    fun eyewear(style: Int): Bitmap? {
        val file = EYEWEAR_FILES.getOrNull(style) ?: return null
        return bitmap("character/eyewear/$file.png")
    }

    /**
     * Jersey file stem for a team.
     * - NBA: [slot] (franchise id) → `lakers`, `celtics`, …
     * - College: [abbr] → numbered college art (`01_duke`, …); else null (blank recolor).
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
     */
    val HAIR_FILES = arrayOf(
        null,                    // 0 Bald
        "buzz_cut",              // 1 Buzz
        "slicked_back_hair",     // 2 Short / slicked
        "curly_top_fade",        // 3 Fade
        "flat_top",              // 4 Flat-top
        "afro",                  // 5 Afro
        "high_top_fade",         // 6 High-top
        "curly_top_fade",        // 7 Headband (curls + separate headband overlay)
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

    // Face.acc headband slots (guide-named files also present for customizer).
    private val HEADBAND_FILES = arrayOf(
        null,
        "01_classic_white",
        "02_bulls_red",
        "03_stealth_black",
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

    /** College school abbr → numbered college jersey asset (48-school pack). */
    private val COLLEGE_BY_ABBR = mapOf(
        "DUKE" to "01_duke", "UNC" to "02_unc", "UK" to "03_kentucky",
        "KU" to "04_kansas", "CONN" to "05_uconn", "NOVA" to "06_villanova",
        "IND" to "07_indiana", "MICH" to "08_michigan", "MSU" to "09_michigan_state",
        "GONZ" to "10_gonzaga", "PUR" to "11_purdue", "UCLA" to "12_ucla",
        "ARIZ" to "13_arizona", "HOU" to "14_houston", "TEX" to "15_texas",
        "TENN" to "16_tennessee", "SYR" to "17_syracuse", "LOU" to "18_louisville",
        "UVA" to "19_virginia", "MARQ" to "20_marquette", "CREI" to "21_creighton",
        "BAY" to "22_baylor", "OSU" to "23_ohio_state", "ILL" to "24_illinois",
        "BAMA" to "25_alabama", "AUB" to "26_auburn", "FLA" to "27_florida",
        "ARK" to "28_arkansas", "ISU" to "29_iowa_state", "SDSU" to "30_san_diego_state",
        "MEM" to "31_memphis", "SJU" to "32_st_johns", "GTWN" to "33_georgetown",
        "NCST" to "34_nc_state", "WIS" to "35_wisconsin", "MD" to "36_maryland",
        "IOWA" to "37_iowa", "ORE" to "38_oregon", "TTU" to "39_texas_tech",
        "BYU" to "40_byu", "XAV" to "41_xavier", "HALL" to "42_seton_hall",
        "PROV" to "43_providence", "SMC" to "44_saint_marys", "DAY" to "45_dayton",
        "VCU" to "46_vcu", "FSU" to "47_florida_state", "MIA" to "48_miami",
    )
}
