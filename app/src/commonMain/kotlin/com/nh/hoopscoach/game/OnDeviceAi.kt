package com.nh.hoopscoach.game

/**
 * On-device AI: Gemini Nano, through Android's AICore system service (Google's ML Kit GenAI Prompt API).
 * It runs on the phone's own chip (Tensor, on a Pixel): no account, no key, nothing leaves the phone.
 *
 * The Android app installs the [engine] at startup. The PC, and phones without AICore, have none, so
 * everything here reports "not supported" and the game uses its other writers.
 *
 * AICore only answers while the app is on screen, keeps a per-app quota, and takes prompts of about 4,000
 * tokens, so dialogue (press conferences, interviews, speeches, posts) suits it best. When it can't answer,
 * the request fails like any provider's and the game's own writing fills in.
 */
object OnDeviceAi {
    enum class State { READY, DOWNLOADABLE, DOWNLOADING, UNAVAILABLE, NOT_SUPPORTED, UNKNOWN }

    class Status(val state: State, val detail: String = "", val model: String = "")

    /** What the platform provides (the Android app's Gemini Nano engine). */
    interface Engine {
        /** Whether Gemini Nano is on the phone, needs a download, or isn't offered. */
        suspend fun status(): Status

        /** The one-time model download; [onProgress] gets a short line as it goes. */
        suspend fun download(onProgress: (String) -> Unit): Result<Unit>

        /**
         * One reply. [system] is the writer's instructions; [maxTokens] caps the reply. [fast] asks for the
         * smaller, quicker model where the phone has both. [onToken] gets each new piece as it streams.
         */
        suspend fun generate(prompt: String, system: String, temperature: Double, maxTokens: Int, fast: Boolean, onToken: ((String) -> Unit)?): String
    }

    /**
     * What Gemini Nano writes, and which of the phone's two models suits it: the fast one (E2B) for short,
     * quick lines, the full one (E4B) where the writing needs more.
     */
    enum class Desk(val label: String, val blurb: String, val fullByDefault: Boolean) {
        REACTIONS("Quick player reactions", "Postgame player quotes and social posts", false),
        NOTIFICATIONS("Short notifications", "The weekly Dispatch and recruiting crystal ball", false),
        PERSONALITIES("Player personalities", "On-court personalities and scout notes", true),
        COACH("Coach interviews", "Press conferences, halftime and award speeches", true),
        RECRUITING("Recruiting conversations", "Recruiting pitch letters", true),
    }

    /** Whether [desk] uses the fast model (anything else: the "everything else" setting). */
    fun fast(config: OllamaConfig, desk: Desk?): Boolean =
        if (desk == null) config.nanoFast else config.nanoSizes[desk.name]?.let { it == "fast" } ?: !desk.fullByDefault

    const val NAME = "Gemini Nano"
    const val NOT_HERE = "Gemini Nano runs on Android phones with AICore (Pixel 9 and newer), not on this device."

    @Volatile var engine: Engine? = null

    /** The last status seen, so routing and the settings screen don't have to ask AICore every time. */
    @Volatile var last: Status = Status(State.UNKNOWN)

    /** This device has an engine at all (an Android phone; not the PC). */
    val supported: Boolean get() = engine != null

    /** Gemini Nano is downloaded and answering. */
    val ready: Boolean get() = engine != null && last.state == State.READY

    suspend fun refresh(): Status {
        val e = engine ?: return Status(State.NOT_SUPPORTED, NOT_HERE).also { last = it }
        return runCatching { e.status() }.getOrElse { Status(State.UNAVAILABLE, it.message ?: "AICore didn't answer.") }.also { last = it }
    }

    /** "Gemini Nano", or with the model AICore reports ("Gemini Nano (nano-v3)"). */
    fun label(): String = if (last.model.isNotBlank()) "$NAME (${last.model})" else NAME

    fun stateLabel(s: Status = last): String = when (s.state) {
        State.READY -> "Ready on this phone"
        State.DOWNLOADABLE -> "Needs a one-time download"
        State.DOWNLOADING -> "Downloading"
        State.UNAVAILABLE -> "Not available right now"
        State.NOT_SUPPORTED -> "Not on this device"
        State.UNKNOWN -> "Checking…"
    }

    /**
     * Fits a prompt into Gemini Nano's small window: the start (the fact sheet's header) and the end (the task
     * and the format) matter most, so a long middle is cut instead.
     */
    fun fit(prompt: String, maxChars: Int): String {
        if (prompt.length <= maxChars) return prompt
        val keepHead = (maxChars * 0.45).toInt()
        val keepTail = maxChars - keepHead - 12
        val head = prompt.take(keepHead).substringBeforeLast('\n', prompt.take(keepHead))
        val tail = prompt.takeLast(keepTail).substringAfter('\n', prompt.takeLast(keepTail))
        return "$head\n…\n$tail"
    }
}
