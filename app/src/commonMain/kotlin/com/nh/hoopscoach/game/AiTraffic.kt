package com.nh.hoopscoach.game

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * Traffic control for every AI request the game sends: one at a time, in priority order, paced per provider,
 * and held back after a provider says "slow down" (an HTTP 429, Gemini Nano's BUSY, a used-up daily allowance).
 *
 * - What the player is waiting on (they tapped Write, opened the postgame) goes first ([Priority.USER]).
 * - Dialogue for the game just played (interviews, press, posts) comes next ([Priority.LIVE]).
 * - Background desks (articles, story packages, the weekly Dispatch) go last ([Priority.BACKGROUND]). They never
 *   pile up: a full line or a resting provider skips them at once, nothing is sent, and the game's own writing
 *   stays up.
 *
 * A provider that refused gets a rest that grows with each refusal and resets after a success, and the gap
 * between its requests widens and then eases back.
 */
object AiTraffic {
    enum class Priority { USER, LIVE, BACKGROUND }

    /** Marks a coroutine's AI requests with a priority. Requests with none are the player's own. */
    class Lane(val priority: Priority) : AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<Lane>
    }

    suspend fun priority(): Priority = coroutineContext[Lane]?.priority ?: Priority.USER

    /** The game didn't send this request (the provider is resting, or the line is full). Nothing was spent. */
    class Skipped(message: String) : Exception(message)

    enum class Trouble { RATE_LIMIT, BUSY, QUOTA, OFF_SCREEN }

    private class Hold(val until: Long, val trouble: Trouble)
    private class Ticket(val priority: Priority, val since: Long, val seq: Long) { val go = CompletableDeferred<Unit>() }

    private val lock = Any()
    private var busy = false
    private var seq = 0L
    private val waiting = mutableListOf<Ticket>()
    private val holds = HashMap<String, Hold>()
    private val strikes = HashMap<String, Int>()
    private val gaps = HashMap<String, Long>()
    private val lastStart = HashMap<String, Long>()

    /** At most this many background requests wait in line; more are skipped (the game's writing stays). */
    const val MAX_BACKGROUND_WAITING = 2
    const val MAX_LIVE_WAITING = 4
    /** A background request that waited this long is out of date by the time its turn comes. */
    const val STALE_MS = 120_000L
    /** The player waits out a rest this short rather than get the game's version. */
    const val USER_WAIT_MS = 20_000L

    const val LINE_FULL = "The AI was busy with other stories, so the game wrote this one"

    /** For tests. */
    @Volatile var clock: () -> Long = { System.currentTimeMillis() }

    fun name(provider: String): String = when (provider.lowercase()) {
        "nano" -> "Gemini Nano"
        "google" -> "Gemini"
        "openai" -> "ChatGPT"
        "anthropic" -> "Claude"
        "groq" -> "Groq"
        "deepseek" -> "DeepSeek"
        "openrouter" -> "OpenRouter"
        "cerebras" -> "Cerebras"
        "mistral" -> "Mistral"
        else -> "Ollama"
    }

    /** The shortest gap between two requests to [provider]: free tiers count requests a minute. */
    fun baseGap(provider: String): Long = when (provider.lowercase()) {
        "nano" -> 700
        "google" -> 1_200
        "groq", "cerebras" -> 1_000
        "mistral" -> 1_100
        "openrouter" -> 1_500
        "ollama" -> 0
        else -> 300
    }

    /** How long [provider] still rests, in ms (0: it's ready). */
    fun remaining(provider: String): Long = synchronized(lock) {
        val h = holds[provider.lowercase()] ?: return 0
        (h.until - clock()).coerceAtLeast(0)
    }

    fun resting(provider: String) = remaining(provider) > 0

    /** Why [provider] is resting and for how long, as a reason for the "written by the game" note; null if it isn't. */
    fun restingNote(provider: String): String? {
        val p = provider.lowercase()
        val left = remaining(p)
        if (left <= 0) return null
        val trouble = synchronized(lock) { holds[p]?.trouble } ?: return null
        val name = name(p)
        val back = "AI writing picks back up in about ${about(left)}"
        return when (trouble) {
            Trouble.QUOTA -> if (p == "nano") "Gemini Nano has used this app's on-device allowance for now, so the game wrote this one. It tries again in about ${about(left)}"
                else "$name's free allowance is used up for now, so the game wrote this one. It tries again in about ${about(left)}"
            Trouble.BUSY -> "$name was busy, so the game wrote this one. $back"
            Trouble.RATE_LIMIT -> "$name asked the game to slow down, so the game wrote this one. $back"
            Trouble.OFF_SCREEN -> "Gemini Nano only writes while the game is on screen, so the game wrote this one"
        }
    }

    /** What's resting right now, for the AI settings screen. */
    fun status(): List<String> = synchronized(lock) { holds.keys.toList() }.mapNotNull { p ->
        val left = remaining(p)
        if (left <= 0) null else {
            val why = when (synchronized(lock) { holds[p]?.trouble }) {
                Trouble.QUOTA -> "allowance used up"
                Trouble.BUSY -> "busy"
                Trouble.RATE_LIMIT -> "rate limited"
                else -> "paused"
            }
            "${name(p)}: resting (${why}), back in about ${about(left)}"
        }
    }

    private fun about(ms: Long): String {
        val s = (ms + 999) / 1000
        return when {
            s < 60 -> "$s seconds"
            s < 90 * 60 -> "${(s + 59) / 60} minute${if ((s + 59) / 60 == 1L) "" else "s"}"
            else -> "${(s + 1799) / 3600} hours"
        }
    }

    /** What kind of refusal [e] is, if it's one: a rate limit, a busy model, a used-up allowance. */
    fun classify(e: Throwable?): Trouble? {
        if (e == null || e is Skipped) return null
        (e as? OnDeviceAi.Refused)?.let {
            return when (it.kind) {
                OnDeviceAi.Refused.Kind.BUSY -> Trouble.BUSY
                OnDeviceAi.Refused.Kind.QUOTA -> Trouble.QUOTA
                OnDeviceAi.Refused.Kind.OFF_SCREEN -> Trouble.OFF_SCREEN
                else -> null
            }
        }
        val m = e.message?.lowercase() ?: return null
        if (!Ollama.isRateLimit(m)) return null
        return if (Regex("per.?day|daily|\\btpd\\b|\\brpd\\b|insufficient_quota|billing").containsMatchIn(m)) Trouble.QUOTA else Trouble.RATE_LIMIT
    }

    /** "Try again in 7.5s" / "retryDelay": "13s" in a provider's error, in ms. */
    fun retryHint(e: Throwable?): Long? {
        val m = e?.message ?: return null
        Regex("(?i)retry.?delay\\W+(\\d+(?:\\.\\d+)?)s").find(m)?.let { return (it.groupValues[1].toDouble() * 1000).toLong() }
        Regex("(?i)(?:try again|retry) in (?:about )?(\\d+(?:\\.\\d+)?)\\s*(ms|s|sec|seconds|m|min|minutes)\\b").find(m)?.let {
            val v = it.groupValues[1].toDouble()
            return when (it.groupValues[2].lowercase()) {
                "ms" -> v.toLong()
                "m", "min", "minutes" -> (v * 60_000).toLong()
                else -> (v * 1000).toLong()
            }
        }
        return null
    }

    /** Records how a request to [provider] went: a success eases the pace back; a refusal starts a rest. */
    fun note(provider: String, error: Throwable?) {
        val p = provider.lowercase()
        val trouble = classify(error)
        synchronized(lock) {
            val base = baseGap(p)
            if (error == null) {
                strikes[p] = 0
                gaps[p] = maxOf(base, ((gaps[p] ?: base) * 0.8).toLong())
                return
            }
            when (trouble) {
                Trouble.RATE_LIMIT, Trouble.BUSY -> {
                    val n = (strikes[p] ?: 0) + 1
                    strikes[p] = n
                    val grow = (if (trouble == Trouble.BUSY) 10_000L else 15_000L) shl (n - 1).coerceAtMost(4)
                    val rest = maxOf(retryHint(error) ?: 0L, grow).coerceAtMost(5 * 60_000L)
                    holds[p] = Hold(clock() + rest, trouble)
                    gaps[p] = ((gaps[p] ?: base).coerceAtLeast(1_000) * 2).coerceAtMost(20_000)
                }
                // A daily allowance: rest a good while, then one request finds out whether it reset.
                Trouble.QUOTA -> holds[p] = Hold(clock() + maxOf(retryHint(error) ?: 0L, 30 * 60_000L), Trouble.QUOTA)
                Trouble.OFF_SCREEN, null -> {}
            }
        }
    }

    /** Clears every rest (the player pressed Test AI, or changed providers). */
    fun reset() = synchronized(lock) {
        holds.clear()
        strikes.clear()
        gaps.clear()
    }

    /**
     * Sends [call] to [provider] when its turn comes, with this coroutine's priority. Fails with [Skipped] (nothing
     * sent) when the provider is resting or the line is full. The player's own request waits out a short rest, and
     * gets one more try after a "slow down" that asks for a short wait.
     */
    suspend fun <T> run(provider: String, call: suspend () -> Result<T>): Result<T> {
        val priority = priority()
        val p = provider.lowercase()
        if (!waitable(p, priority)) return Result.failure(Skipped(restingNote(p) ?: LINE_FULL))
        if (!acquire(priority)) return Result.failure(Skipped(LINE_FULL))
        try {
            var retried = false
            while (true) {
                // The provider may have started resting while this request waited its turn.
                val rest = remaining(p)
                if (rest > 0) {
                    if (priority != Priority.USER || rest > USER_WAIT_MS) return Result.failure(Skipped(restingNote(p) ?: LINE_FULL))
                    delay(rest)
                }
                val wait = synchronized(lock) { (lastStart[p] ?: 0L) + (gaps[p] ?: baseGap(p)) - clock() }
                if (wait > 0) delay(wait)
                synchronized(lock) { lastStart[p] = clock() }
                val result = call()
                val error = result.exceptionOrNull()
                if (error is CancellationException) throw error
                note(p, error)
                val trouble = classify(error)
                if (!retried && priority == Priority.USER && (trouble == Trouble.RATE_LIMIT || trouble == Trouble.BUSY) && remaining(p) <= USER_WAIT_MS) {
                    retried = true
                    continue
                }
                if (trouble != null && trouble != Trouble.OFF_SCREEN) restingNote(p)?.let { return Result.failure(Skipped(it)) }
                return result
            }
        } finally {
            release()
        }
    }

    private fun waitable(p: String, priority: Priority): Boolean {
        val rest = remaining(p)
        return rest <= 0 || (priority == Priority.USER && rest <= USER_WAIT_MS)
    }

    /** Waits for the lane; false if the line for this priority is full or the request went stale waiting. */
    private suspend fun acquire(priority: Priority): Boolean {
        val t: Ticket
        synchronized(lock) {
            if (!busy) {
                busy = true
                return true
            }
            val same = waiting.count { it.priority == priority }
            if (priority == Priority.BACKGROUND && same >= MAX_BACKGROUND_WAITING) return false
            if (priority == Priority.LIVE && same >= MAX_LIVE_WAITING) return false
            t = Ticket(priority, clock(), seq++)
            waiting += t
        }
        try {
            t.go.await()
        } catch (e: CancellationException) {
            val handedOver = synchronized(lock) { !waiting.remove(t) }
            if (handedOver) release()
            throw e
        }
        if (priority == Priority.BACKGROUND && clock() - t.since > STALE_MS) {
            release()
            return false
        }
        return true
    }

    private fun release() {
        val next = synchronized(lock) {
            val n = waiting.minWithOrNull(compareBy<Ticket>({ it.priority.ordinal }, { it.seq }))
            if (n == null) busy = false else waiting.remove(n)
            n
        }
        next?.go?.complete(Unit)
    }

    /** How many requests are waiting (for tests and the settings screen). */
    fun waitingCount(): Int = synchronized(lock) { waiting.size }
}
