package com.nh.hoopscoach

import kotlin.math.PI
import kotlin.math.sin

/**
 * The game's sounds, made in code (16-bit mono PCM at [RATE]): a crowd that loops, and the horn.
 * No audio files to ship; each platform plays these through [GameSounds].
 */
object SoundSynth {
    const val RATE = 22050

    /** Four seconds of arena crowd that loops cleanly: murmuring, rising and falling noise. */
    val crowd: ShortArray by lazy {
        val n = RATE * 4
        val out = ShortArray(n)
        val rnd = java.util.Random(7)
        var a = 0.0
        var b = 0.0
        for (i in 0 until n) {
            val white = rnd.nextGaussian()
            a += 0.06 * (white - a)               // a soft low-pass: the roar
            b += 0.25 * (white - b)               // a brighter layer: voices
            val t = i.toDouble() / n
            // Swells that come back to where they started, so the loop has no seam.
            val swell = 0.75 + 0.15 * sin(2 * PI * 3 * t) + 0.1 * sin(2 * PI * 7 * t + 1.3)
            val v = (a * 0.9 + b * 0.25) * swell
            out[i] = (v * 6500).toInt().coerceIn(-32000, 32000).toShort()
        }
        // Fade the ends into each other.
        val f = RATE / 20
        for (i in 0 until f) {
            val k = i.toDouble() / f
            out[i] = (out[i] * k + out[n - f + i] * (1 - k)).toInt().toShort()
        }
        out
    }

    /** The final horn: a second of two detuned, buzzy tones. */
    val buzzer: ShortArray by lazy {
        val n = RATE
        ShortArray(n) { i ->
            val t = i.toDouble() / RATE
            val env = minOf(1.0, t / 0.01) * minOf(1.0, (1.0 - t) / 0.08)
            fun sq(f: Double) = if (sin(2 * PI * f * t) >= 0) 1.0 else -1.0
            val v = (sq(208.0) * 0.5 + sq(312.0) * 0.35 + sin(2 * PI * 416.0 * t) * 0.15) * env
            (v * 9000).toInt().toShort()
        }
    }

    /** Little-endian bytes for players that want them. */
    fun bytes(s: ShortArray): ByteArray {
        val b = ByteArray(s.size * 2)
        for (i in s.indices) {
            b[i * 2] = (s[i].toInt() and 0xFF).toByte()
            b[i * 2 + 1] = ((s[i].toInt() shr 8) and 0xFF).toByte()
        }
        return b
    }
}
