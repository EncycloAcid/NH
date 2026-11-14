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

    private fun clip(v: Double) = v.coerceIn(-1.0, 1.0).times(30000).toInt().toShort()

    /** The net: a quick rising then falling hiss, brighter at the top. */
    val swish: ShortArray by lazy {
        val n = (RATE * 0.32).toInt()
        val rnd = java.util.Random(11)
        var lp = 0.0
        ShortArray(n) { i ->
            val t = i.toDouble() / n
            val white = rnd.nextGaussian()
            lp += 0.35 * (white - lp)
            val hp = white - lp                                  // keep the airy top
            val env = if (t < 0.18) t / 0.18 else Math.pow(1 - (t - 0.18) / 0.82, 2.2)
            clip(hp * env * 0.32)
        }
    }

    /** A referee's whistle: two close tones that beat, with a little trill. */
    val whistle: ShortArray by lazy {
        val n = (RATE * 0.42).toInt()
        ShortArray(n) { i ->
            val t = i.toDouble() / RATE
            val trill = 1 + 0.012 * sin(2 * PI * 28 * t)
            val env = minOf(1.0, t / 0.01) * minOf(1.0, (0.42 - t) / 0.06).coerceAtLeast(0.0)
            clip((sin(2 * PI * 2950 * trill * t) * 0.5 + sin(2 * PI * 3110 * trill * t) * 0.35) * env * 0.55)
        }
    }

    /** One crowd syllable: many voices on a pitch, with breath, shaped by [env]. */
    private fun voices(out: DoubleArray, from: Int, len: Int, pitch: Double, breath: Double, rnd: java.util.Random) {
        val detune = DoubleArray(9) { pitch * (0.96 + rnd.nextDouble() * 0.08) }
        val phase = DoubleArray(9) { rnd.nextDouble() * 2 * PI }
        for (k in 0 until len) {
            val i = from + k
            if (i >= out.size) break
            val t = k.toDouble() / len
            val env = minOf(1.0, t / 0.08) * minOf(1.0, (1 - t) / 0.25)
            var v = 0.0
            for (j in detune.indices) {
                val ph = 2 * PI * detune[j] * k / RATE + phase[j]
                v += sin(ph) * 0.6 + sin(2 * ph) * 0.25 + sin(3 * ph) * 0.12   // a buzzy, voice-like tone
            }
            v /= detune.size
            out[i] += (v + rnd.nextGaussian() * breath) * env
        }
    }

    /** "DE-FENSE!" twice: a short high "DE", a longer lower "FENSE" with its hiss, and two claps. */
    val chant: ShortArray by lazy {
        val beat = (RATE * 0.5).toInt()
        val out = DoubleArray(beat * 8)
        val rnd = java.util.Random(3)
        for (rep in 0..1) {
            val at = rep * beat * 4
            voices(out, at, (beat * 0.55).toInt(), 196.0, 0.15, rnd)                 // DE
            voices(out, at + beat, (beat * 0.9).toInt(), 165.0, 0.25, rnd)           // FENSE
            // The "-se" hiss on the end.
            for (k in 0 until (beat * 0.3).toInt()) out.getOrNull(at + beat + (beat * 0.65).toInt() + k)?.let {
                out[at + beat + (beat * 0.65).toInt() + k] = it + rnd.nextGaussian() * 0.2 * (1 - k / (beat * 0.3))
            }
            // Two claps.
            for (c in 2..3) for (k in 0 until (RATE * 0.05).toInt()) {
                val i = at + beat * c + k
                if (i < out.size) out[i] += rnd.nextGaussian() * 0.5 * Math.pow(1 - k / (RATE * 0.05), 3.0)
            }
        }
        ShortArray(out.size) { clip(out[it] * 0.55) }
    }

    /** The building erupting: a fast, huge swell of noise and voices that rings out. */
    val roar: ShortArray by lazy {
        val n = (RATE * 3.2).toInt()
        val rnd = java.util.Random(5)
        var a = 0.0
        var b = 0.0
        val out = DoubleArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / n
            val w = rnd.nextGaussian()
            a += 0.08 * (w - a)
            b += 0.4 * (w - b)
            val env = minOf(1.0, t / 0.04) * Math.pow(1 - t, 0.8)
            out[i] = (a * 1.6 + b * 0.5) * env
        }
        voices(out, 0, n, 240.0, 0.0, rnd)
        ShortArray(n) { clip(out[it] * 0.6) }
    }

    /** A sound by name: "swish", "whistle", "chant", "roar", "buzzer". */
    fun sound(name: String): ShortArray? = when (name) {
        "swish" -> swish; "whistle" -> whistle; "chant" -> chant; "roar" -> roar; "buzzer" -> buzzer; else -> null
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
