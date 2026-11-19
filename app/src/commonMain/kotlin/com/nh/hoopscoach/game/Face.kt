package com.nh.hoopscoach.game

/**
 * Portrait parts over the user's modular sprites: skin tone body, hairstyle (+ color), plus legacy
 * face extras (beard/eyes/brows/mouth/acc) kept for saves. A player without a custom one gets his
 * own from his look, the same every time.
 */
class Face(
    val skin: Int, val head: Int, val hair: Int, val hairColor: Int, val beard: Int,
    val eyes: Int, val brows: Int, val mouth: Int, val acc: Int,
) {
    fun parts() = intArrayOf(skin, head, hair, hairColor, beard, eyes, brows, mouth, acc)
    fun encode() = parts().joinToString(",")
    fun with(part: Int, value: Int): Face { val a = parts(); a[part] = value; return from(a) }

    companion object {
        val PARTS = listOf("Skin", "Face", "Hair", "Hair color", "Facial hair", "Eyes", "Brows", "Mouth", "Extras")
        val OPTIONS = listOf(
            listOf("Pale ivory", "Fair peach", "Olive bronze", "Warm caramel", "Medium brown", "Chestnut", "Dark chocolate", "Deep ebony"),
            listOf("Oval", "Round", "Square", "Long"),
            listOf("Bald", "Buzz", "Slicked", "Fade", "Flat-top", "Afro", "High-top", "Headband", "Cornrows", "Locs", "Twists", "Mohawk", "Waves", "Top bun",
                "Afro puffs", "Frohawk", "Short locs", "Short twists", "Twist fade", "Tapered afro", "Hard part", "Wave fade",
                "Buzz fade", "Crew cut", "Undercut", "Faux hawk", "Cornrow fade", "Locs bun", "Twist bun", "Loc-hawk", "Twist-hawk", "Big afro", "Side part", "Low flat-top", "Part fade", "Waves part", "High puff", "Long locs", "Long twists", "Curly top", "Short mohawk", "Locs half-up"),
            listOf("Black", "Dark brown", "Brown", "Auburn", "Blond", "Platinum", "Gray", "Red"),
            listOf("None", "Stubble", "Mustache", "Goatee", "Chin strap", "Full beard"),
            listOf("Default", "Intense", "Clutch", "Swagger", "Alert", "Blue", "Green"),
            listOf("Normal", "Thick", "Fierce"),
            listOf("Neutral", "Grin", "Shout", "Mouthguard"),
            listOf("None", "White headband", "Red headband", "Black headband", "Goggles", "Clear mask", "Carbon mask"),
        )

        private fun from(a: IntArray) = Face(a[0], a[1], a[2], a[3], a[4], a[5], a[6], a[7], a[8])

        fun decode(s: String): Face? = runCatching {
            val a = s.split(",").map { it.trim().toInt() }
            if (a.size < 9) null else from(IntArray(9) { i -> a[i].coerceIn(0, OPTIONS[i].size - 1) })
        }.getOrNull()

        fun of(p: Player): Face = (if (p.face.isNotEmpty()) decode(p.face) else null) ?: generated(p)

        private fun <T> weighted(r: kotlin.random.Random, items: List<Pair<T, Double>>): T {
            var x = r.nextDouble() * items.sumOf { it.second }
            for ((v, w) in items) { x -= w; if (x <= 0) return v }
            return items.last().first
        }

        /** The face his look gives him: a realistic spread of skin and hair, facial hair more likely with age. */
        fun generated(p: Player): Face {
            val r = kotlin.random.Random(p.look.toLong() * 31 + p.id)
            val skin = weighted(r, listOf(0 to 0.07, 1 to 0.1, 2 to 0.12, 3 to 0.13, 4 to 0.15, 5 to 0.17, 6 to 0.15, 7 to 0.11))
            val dark = skin >= 4
            val hair = weighted(r, if (dark) listOf(0 to 0.08, 1 to 0.2, 2 to 0.08, 3 to 0.2, 4 to 0.03, 5 to 0.05, 6 to 0.02, 7 to 0.06, 8 to 0.07, 9 to 0.08, 10 to 0.01, 11 to 0.02, 12 to 0.09, 13 to 0.01)
                else listOf(0 to 0.04, 1 to 0.15, 2 to 0.3, 3 to 0.18, 4 to 0.02, 5 to 0.0, 6 to 0.0, 7 to 0.1, 8 to 0.0, 9 to 0.01, 10 to 0.07, 11 to 0.02, 12 to 0.03, 13 to 0.08))
            val hairColor = if (p.age >= 36 && r.nextDouble() < 0.4) 6 else weighted(r, if (dark) listOf(0 to 0.88, 1 to 0.09, 4 to 0.01, 5 to 0.01, 7 to 0.01)
                else listOf(0 to 0.2, 1 to 0.33, 2 to 0.25, 3 to 0.06, 4 to 0.13, 5 to 0.01, 7 to 0.02))
            val beardOdds = (0.18 + (p.age - 20) * 0.04).coerceIn(0.1, 0.7)
            val beard = if (r.nextDouble() > beardOdds) 0 else weighted(r, listOf(1 to 0.35, 2 to 0.08, 3 to 0.22, 4 to 0.12, 5 to 0.23))
            val eyes = weighted(r, listOf(0 to 0.55, 1 to 0.12, 2 to 0.1, 3 to 0.08, 4 to 0.07, 5 to 0.04, 6 to 0.04))
            val mouth = weighted(r, listOf(0 to 0.62, 1 to 0.2, 2 to 0.12, 3 to 0.06))
            val acc = weighted(r, listOf(0 to 0.82, 1 to 0.05, 2 to 0.04, 3 to 0.05, 4 to 0.02, 5 to 0.02))
            return Face(skin, r.nextInt(4), variant(p, hair), hairColor, beard, eyes, r.nextInt(3), mouth, acc)
        }

        /**
         * The newer cuts for league players: a share of those with a style that has a variant wear the variant
         * instead. Rolled on its own seed so every other part of an existing player's face stays as it was.
         */
        private fun variant(p: Player, hair: Int): Int {
            val r = kotlin.random.Random(p.look.toLong() * 131 + p.id * 7 + 5)
            val roll = r.nextDouble()
            return when (hair) {
                1 -> if (roll < 0.2) 20 else if (roll < 0.4) 22 else if (roll < 0.5) 23 else hair   // buzz → hard part, buzz fade, crew cut
                2 -> if (roll < 0.25) 24 else if (roll < 0.4) 32 else hair           // slicked → undercut, side part
                3 -> if (roll < 0.2) 19 else if (roll < 0.35) 34 else if (roll < 0.5) 39 else hair   // fade → tapered afro, part fade, curly top
                4 -> if (roll < 0.3) 33 else hair                                    // flat-top → low flat-top
                8 -> if (roll < 0.35) 26 else hair                                   // cornrows → cornrow fade
                5 -> if (roll < 0.3) 19 else if (roll < 0.4) 15 else if (roll < 0.5) 31 else hair   // afro → tapered, frohawk, big afro
                9 -> if (roll < 0.25) 16 else if (roll < 0.4) 37 else if (roll < 0.5) 27 else if (roll < 0.6) 41 else hair   // locs → short, long, bun, half-up
                10 -> if (roll < 0.25) 17 else if (roll < 0.5) 18 else if (roll < 0.6) 38 else if (roll < 0.7) 28 else hair   // twists → short, fade, long, bun
                11 -> if (roll < 0.3) 15 else if (roll < 0.5) 40 else if (roll < 0.6) 25 else hair  // mohawk → frohawk, short, faux hawk
                12 -> if (roll < 0.35) 21 else if (roll < 0.5) 35 else hair          // waves → wave fade, waves part
                else -> hair
            }
        }

        /**
         * A random face for the Randomize button: any skin and hairstyle, realistic odds for the rest (dyed
         * hair, light eyes, mouthguards and masks now and then, not on every other roll).
         */
        fun random(seed: Long): Face {
            // Scramble the seed first: neighboring seeds (taps a moment apart) would otherwise roll alike.
            var z = seed + -7046029254386353131L
            z = (z xor (z ushr 30)) * -4658895280553007687L
            z = (z xor (z ushr 27)) * -7723592293110705685L
            val r = kotlin.random.Random(z xor (z ushr 31))
            val skin = r.nextInt(OPTIONS[0].size)
            val dark = skin >= 4
            val hair = r.nextInt(OPTIONS[2].size)
            val hairColor = if (r.nextDouble() < 0.12) weighted(r, listOf(4 to 0.35, 5 to 0.3, 7 to 0.2, 6 to 0.15))
                else weighted(r, if (dark) listOf(0 to 0.85, 1 to 0.15) else listOf(0 to 0.25, 1 to 0.35, 2 to 0.25, 3 to 0.15))
            val beard = if (r.nextDouble() < 0.55) 0 else 1 + r.nextInt(OPTIONS[4].size - 1)
            val eyes = weighted(r, listOf(0 to 0.45, 1 to 0.14, 2 to 0.12, 3 to 0.12, 4 to 0.1) + (if (dark) listOf(5 to 0.03, 6 to 0.02) else listOf(5 to 0.08, 6 to 0.06)))
            val mouth = weighted(r, listOf(0 to 0.6, 1 to 0.24, 2 to 0.11, 3 to 0.05))
            val acc = weighted(r, listOf(0 to 0.78, 1 to 0.06, 2 to 0.04, 3 to 0.06, 4 to 0.03, 5 to 0.02, 6 to 0.01))
            return Face(skin, r.nextInt(OPTIONS[1].size), hair, hairColor, beard, eyes, r.nextInt(OPTIONS[6].size), mouth, acc)
        }
    }
}
