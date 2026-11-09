package com.nh.hoopscoach.game

/**
 * A sports-card portrait: skin, head shape, hair and its color, facial hair, eyes, brows, mouth and an
 * extra. A player without a custom one gets his own drawn from his look, the same every time.
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
            listOf("Porcelain", "Fair", "Light", "Tan", "Bronze", "Brown", "Deep", "Ebony"),
            listOf("Oval", "Round", "Square", "Long"),
            listOf("Bald", "Buzz", "Short", "Fade", "Flat-top", "Afro", "High-top", "Curls", "Cornrows", "Locs", "Long", "Mohawk", "Waves", "Man bun"),
            listOf("Black", "Dark brown", "Brown", "Auburn", "Blond", "Platinum", "Gray", "Red"),
            listOf("None", "Stubble", "Mustache", "Goatee", "Chin strap", "Full beard"),
            listOf("Normal", "Focused", "Wide"),
            listOf("Normal", "Thick", "Fierce"),
            listOf("Neutral", "Smile", "Smirk"),
            listOf("None", "White headband", "Team headband", "Goggles", "Earring", "Glasses"),
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
            val acc = weighted(r, listOf(0 to 0.84, 1 to 0.05, 2 to 0.04, 3 to 0.01, 4 to 0.05, 5 to 0.01))
            return Face(skin, r.nextInt(4), hair, hairColor, beard, r.nextInt(3), r.nextInt(3), r.nextInt(3), acc)
        }

        fun random(seed: Long): Face {
            val r = kotlin.random.Random(seed)
            return from(IntArray(9) { i -> r.nextInt(OPTIONS[i].size) }.also { if (r.nextDouble() < 0.7) it[8] = 0; if (r.nextDouble() < 0.4) it[4] = 0 })
        }
    }
}
