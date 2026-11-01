package com.nh.hoopscoach.game

import kotlin.math.floor

object AgeCurve {
    private val stats = listOf(
        "mpg", "pts36", "fga36", "3pa_share", "fta_rate", "fg2pct", "fg3pct", "ftpct",
        "orb36", "drb36", "ast36", "stl36", "blk36", "tov36", "pf36",
    )

    private val levels: Map<Int, Map<String, Double>> by lazy {
        val rows = EraData::class.java.getResourceAsStream("/eras/age_curves.tsv")
            ?.bufferedReader(Charsets.UTF_8)?.use { it.readLines() }
            .orEmpty()
            .filter { it.isNotBlank() && !it.startsWith("#") }
        rows.mapNotNull { row ->
            val fields = row.split("|")
            val age = fields.firstOrNull()?.toIntOrNull() ?: return@mapNotNull null
            age to stats.mapIndexedNotNull { index, stat ->
                fields.getOrNull(index + 1)?.toDoubleOrNull()?.let { stat to it }
            }.toMap()
        }.toMap()
    }

    fun level(stat: String, age: Double): Double {
        val clamped = age.coerceIn(19.0, 42.0)
        if (stat == "trb") return 0.26 * level("orb36", clamped) + 0.74 * level("drb36", clamped)
        val lower = floor(clamped).toInt()
        val upper = (lower + 1).coerceAtMost(42)
        val low = levels[lower]?.get(stat) ?: 1.0
        val high = levels[upper]?.get(stat) ?: low
        return low + (high - low) * (clamped - lower)
    }
}
