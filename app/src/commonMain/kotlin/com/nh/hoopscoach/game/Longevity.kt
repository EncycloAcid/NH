package com.nh.hoopscoach.game

import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Longevity: a hidden rating (1-99, 50 average) for how well a player's body holds up. It's never shown.
 * It moves when he starts to decline and when he retires by up to four years either way: at 90 he plays
 * like a man three years younger, at 15 he slows down at 28. Real players get theirs from their real
 * careers (Kareem and Vince Carter near the top, players who were done at 31 near the bottom); everyone
 * else's is drawn from who they are, the same every time. The user's player hears about it only from his
 * trainers.
 */
object Longevity {
    fun of(p: Player): Int = if (p.longevity in 1..99) p.longevity else seeded(p.id)

    private fun seeded(id: Int): Int {
        val r = Random(id * 2_654_435_761L xor 0x5DEECE66DL)
        return (50 + Rankings.gauss(r) * 17).roundToInt().coerceIn(5, 95)
    }

    /** Years his body plays younger (positive) or older than his age: up to four either way. */
    fun shift(p: Player): Double = (of(p) - 50) / 12.5

    /** The age his body plays at. */
    fun bodyAge(p: Player): Double = p.age - shift(p)

    /** Whether he retires this summer: by his body's age, and earlier when he's no longer good enough. */
    fun retires(p: Player): Boolean {
        val a = bodyAge(p)
        return p.age >= 45 || a >= 42 || (a >= 36 && Rng.chance((a - 35) * 0.14)) || (a >= 33 && p.ovr < 80) || (p.age >= 30 && p.ovr < 76)
    }

    /**
     * A real player's longevity from his real career: how old he was in his last real season (35 is
     * average; 40 is near the top), or, still playing in the data's last season, at least a year older than he is now.
     * Null when the data doesn't know him.
     */
    fun real(name: String): Int? {
        val lines = RealSeasons.careerByName(name, 99_999).filter { it.g >= 15 }
        val last = lines.maxByOrNull { it.season } ?: return null
        val active = last.season >= ProLeague.DATA_SEASON
        return when {
            !active -> (50 + (last.age - 35) * 10).coerceIn(10, 97)
            last.age >= 33 -> (50 + (last.age - 34) * 10).coerceIn(55, 97)
            else -> null
        }
    }

    /** Gives every real player in [lg] his real longevity (generated players keep theirs). */
    fun assignReal(lg: League) {
        for (p in lg.players) if (p.longevity < 1 && p.id != lg.heroId) real(p.name)?.let { p.longevity = it }
    }

    /** What the training staff tells the user's player each summer from 29 on. */
    fun hint(p: Player): String = when (of(p)) {
        in 75..99 -> "The training staff says ${p.first}'s body is holding up like a 25-year-old's."
        in 55..74 -> "The training staff says ${p.first}'s body is holding up well."
        in 35..54 -> "The training staff sees the usual wear and tear on ${p.first}: nothing they can't manage."
        else -> "The training staff says the miles are starting to show on ${p.first}: recovery takes longer every year."
    }
}
