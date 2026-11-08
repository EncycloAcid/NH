package com.nh.hoopscoach.game

/**
 * Your player's gear: what he wears on the floor. The undershirt and arm sleeves show on his portrait;
 * the rest is on his player card. Stored on the player as a short string, like his [Face].
 */
class Gear(
    val undershirt: Int = 0, val sleeve: Int = 0, val sleeveColor: Int = 0, val wristbands: Int = 0,
    val shoes: Int = 0, val shoeColor: Int = 0, val legs: Int = 0,
) {
    fun parts() = intArrayOf(undershirt, sleeve, sleeveColor, wristbands, shoes, shoeColor, legs)
    fun encode() = parts().joinToString(",")
    fun with(part: Int, value: Int): Gear { val a = parts(); a[part] = value; return Gear(a[0], a[1], a[2], a[3], a[4], a[5], a[6]) }
    val isPlain get() = parts().all { it == 0 }

    /** "Black undershirt, team sleeve on the right arm, white wristbands" for the player card. */
    fun describe(): String = listOfNotNull(
        if (undershirt > 0) "${OPTIONS[0][undershirt].lowercase()} undershirt" else null,
        if (sleeve > 0) "${COLORS[sleeveColor].lowercase()} ${if (sleeve == 3) "sleeves on both arms" else "sleeve on the ${if (sleeve == 1) "left" else "right"} arm"}" else null,
        if (wristbands > 0) "${OPTIONS[3][wristbands].lowercase()} wristbands" else null,
        if (legs > 0) OPTIONS[6][legs].lowercase() else null,
        "${COLORS[shoeColor].lowercase()} ${OPTIONS[4][shoes].lowercase()}",
    ).joinToString(", ").replaceFirstChar { it.uppercase() }

    companion object {
        val PARTS = listOf("Undershirt", "Arm sleeve", "Sleeve color", "Wristbands", "Shoes", "Shoe color", "Legs")
        val COLORS = listOf("Team", "White", "Black", "Trim")
        val OPTIONS = listOf(
            listOf("None", "White", "Black", "Team"),
            listOf("None", "Left arm", "Right arm", "Both arms"),
            COLORS,
            listOf("None", "White", "Black", "Team"),
            listOf("Low-tops", "Mid-tops", "High-tops", "Retro high-tops", "Speed lows"),
            COLORS,
            listOf("Nothing", "Black tights", "White tights", "Knee sleeve", "Leg sleeve"),
        )

        fun decode(s: String): Gear {
            val a = s.split(",").mapNotNull { it.trim().toIntOrNull() }
            if (a.size < 7) return Gear()
            fun at(i: Int) = a[i].coerceIn(0, OPTIONS[i].size - 1)
            return Gear(at(0), at(1), at(2), at(3), at(4), at(5), at(6))
        }
    }
}

/** Signature style: how he dunks, his go-to move, his jumper and how he celebrates. They show up in the play-by-play and on social media. */
object Signature {
    val DUNKS = listOf("Two-hand hammer", "Tomahawk", "Windmill", "Reverse jam", "360", "Cock-back", "Off the glass", "Between the legs")
    val MOVES = listOf("Step-back", "Crossover", "Euro step", "Spin move", "Hesitation", "Jab step", "Behind the back", "Post fadeaway")
    val SHOTS = listOf("Smooth jumper", "Quick release", "High release", "Fadeaway", "Set shot", "Sling shot")
    val CELEBRATIONS = listOf("Flex", "Shimmy", "Chest pound", "Point to the sky", "Too small", "Finger wag", "Calm down", "Salute", "Stare-down")

    val dunkVerb = listOf("throws down a two-handed hammer", "throws down a tomahawk", "throws down a windmill", "finishes with a reverse jam",
        "spins in for a 360 jam", "cocks it back and slams it", "throws it off the glass to himself and slams it", "goes between the legs for the slam")
    val moveText = listOf("step-back", "crossover", "euro step", "spin move", "hesitation", "jab step", "behind-the-back dribble", "post fadeaway")
    val shotWord = listOf("smooth", "quick-release", "high-release", "fadeaway", "set-shot", "sling-shot")
    val celebrateText = listOf("flexes for the crowd", "hits the shimmy", "pounds his chest", "points to the sky", "does the too-small",
        "wags a finger", "tells the crowd to calm down", "salutes the bench", "stares down the defender")

    /** A short "\"Nickname\" " prefix now and then for the user's player, empty otherwise. */
    fun nick(lg: League, p: Player): String = if (p.id == lg.heroId && p.nickname.isNotBlank() && Rng.chance(0.3)) "\"${p.nickname}\" " else ""

    /** How the play-by-play describes a made basket by [p]; null when he has nothing special for it. */
    fun made(lg: League, p: Player, type: Int, what: String): String? {
        if (p.id != lg.heroId) return null
        val r = lg.road ?: return null
        return when {
            what == "dunk" -> dunkVerb[r.sigDunk.coerceIn(0, dunkVerb.size - 1)]
            type == 0 && what == "layup" && r.sigMove == 2 && Rng.chance(0.6) -> "euro steps in for the layup"
            type == 0 && what == "layup" && r.sigMove == 3 && Rng.chance(0.5) -> "spins past his man for the layup"
            type == 1 && Rng.chance(0.45) -> "hits a ${moveText[r.sigMove.coerceIn(0, moveText.size - 1)]} jumper"
            type == 1 && Rng.chance(0.4) -> "knocks down the ${shotWord[r.sigShot.coerceIn(0, shotWord.size - 1)]} jumper"
            type == 2 && r.sigMove in setOf(0, 4, 5) && Rng.chance(0.35) -> "drains a ${moveText[r.sigMove]} three"
            type == 2 && Rng.chance(0.25) -> "buries the ${shotWord[r.sigShot.coerceIn(0, shotWord.size - 1)]} three"
            else -> null
        }
    }

    /** His celebration after a big basket, or null. */
    fun celebrate(lg: League, p: Player, big: Boolean): String? {
        if (p.id != lg.heroId || !big) return null
        val r = lg.road ?: return null
        return celebrateText[r.celebration.coerceIn(0, celebrateText.size - 1)]
    }

    /** Nicknames to suggest for [p], by position and style. */
    fun nicknames(p: Player): List<String> {
        val byPos = when (p.pos) {
            Pos.PG -> listOf("The Professor", "Floor General", "Handles", "The Maestro", "Quick")
            Pos.SG -> listOf("Bucket", "The Microwave", "Splash", "Iceman", "Cash")
            Pos.SF -> listOf("Smooth", "The Swiss Army Knife", "Glide", "The Wing", "Silk")
            Pos.PF -> listOf("The Hammer", "Bulldozer", "Big Easy", "The Engine", "Tank")
            Pos.C -> listOf("The Wall", "Skyscraper", "Big Smooth", "The Tower", "Glass")
        }
        val last = p.last.trim()
        val fromName = if (last.length >= 3) listOf("${last.take(1).uppercase()}-${last.take(3).replaceFirstChar { it.uppercase() }}", "Lil ${last.replaceFirstChar { it.uppercase() }}", "${p.first.take(1).uppercase()}${last.take(1).uppercase()}") else emptyList()
        return byPos + fromName
    }
}
