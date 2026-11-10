package com.nh.hoopscoach.ui

/**
 * Compact bitwise packing for Player.look (32-bit Int) in Model.kt.
 * Fits all customization attributes into Player.look without breaking save games:
 * - Bits 0..3   (0xF): Skin tone index (0..7)
 * - Bits 4..7   (0xF): Hairstyle index (0..15)
 * - Bits 8..10  (0x7): Hair color index (0..3)
 * - Bits 11..13 (0x7): Beard style index (0..3, 0 = none)
 * - Bits 14..16 (0x7): Eyewear style index (0..2, 0 = none)
 * - Bits 17..19 (0x7): Headband index (0..3, 0 = none)
 * - Bits 20..22 (0x7): Mouth expression index (0..3)
 * - Bits 23..25 (0x7): Eye expression index (0..5)
 */
data class PlayerLook(
    val skinToneIndex: Int = 3,     // Medium Brown default
    val hairIndex: Int = 0,         // 0 = Buzz cut, 1 = Afro, 2 = Dreads, 3 = Cornrows, etc.
    val hairColorIndex: Int = 0,    // 0 = Black, 1 = Dark Brown, 2 = Brown, 3 = Auburn
    val beardIndex: Int = 0,        // 0 = None, 1 = Full Beard, 2 = Goatee, 3 = Stubble
    val eyewearIndex: Int = 0,      // 0 = None, 1 = Rec Specs, 2 = Clear Mask
    val headbandIndex: Int = 0,     // 0 = None, 1 = White, 2 = Red, 3 = Black
    val mouthIndex: Int = 0,        // 0 = Neutral, 1 = Grin, 2 = Shout, 3 = Mouthguard
    val eyeIndex: Int = 0           // 0 = Focused, 1 = Clutch, 2 = Swagger, 3 = Alert, 4 = Blue, 5 = Green
) {
    fun toInt(): Int {
        var v = 0
        v = v or ((skinToneIndex and 0xF) shl 0)
        v = v or ((hairIndex and 0xF) shl 4)
        v = v or ((hairColorIndex and 0x7) shl 8)
        v = v or ((beardIndex and 0x7) shl 11)
        v = v or ((eyewearIndex and 0x7) shl 14)
        v = v or ((headbandIndex and 0x7) shl 17)
        v = v or ((mouthIndex and 0x7) shl 20)
        v = v or ((eyeIndex and 0x7) shl 23)
        return v
    }

    companion object {
        fun fromInt(look: Int): PlayerLook {
            if (look == 0) return PlayerLook() // Default
            return PlayerLook(
                skinToneIndex = ((look shr 0) and 0xF).coerceAtMost(skinTones.lastIndex),
                hairIndex = ((look shr 4) and 0xF).coerceAtMost(hairstyles.lastIndex),
                hairColorIndex = (look shr 8) and 0x7,
                beardIndex = ((look shr 11) and 0x7).coerceAtMost(beards.lastIndex),
                eyewearIndex = ((look shr 14) and 0x7).coerceAtMost(eyewear.lastIndex),
                headbandIndex = ((look shr 17) and 0x7).coerceAtMost(headbands.lastIndex),
                mouthIndex = ((look shr 20) and 0x7).coerceAtMost(mouths.lastIndex),
                eyeIndex = ((look shr 23) and 0x7).coerceAtMost(eyes.lastIndex)
            )
        }

        val skinTones = listOf(
            "01_deep_ebony", "02_dark_chocolate", "03_chestnut_brown", "04_medium_brown",
            "05_warm_caramel", "06_olive_bronze", "07_fair_peach", "08_pale_ivory"
        )

        val hairstyles = listOf(
            "buzz_cut", "afro", "dreadlocks", "cornrows", "high_top_fade",
            "curly_top_fade", "mohawk", "headband_with_curls", "braided_top_bun",
            "twists", "bleached_blonde_crop", "messy_waves", "slicked_back_hair",
            "double_afro_puffs", "flat_top"
        )

        val beards = listOf(
            null, // Clean shaven
            "01_full_beard", "02_trimmed_goatee", "03_stubble_shadow"
        )

        val headbands = listOf(
            null, // None
            "01_classic_white", "02_bulls_red", "03_stealth_black"
        )

        val eyewear = listOf(
            null, // None
            "01_sports_rec_specs", "02_protective_clear_mask"
        )

        val mouths = listOf(
            null, // Neutral base mouth
            "01_confident_grin", "02_game_face_shout", "03_athletic_mouthguard"
        )

        val eyes = listOf(
            null, // Base eyes
            "01_intense_focused", "02_clutch_stare", "03_confident_swagger",
            "04_alert_court_vision", "05_light_blue_eyes", "06_emerald_green_eyes"
        )
    }
}
