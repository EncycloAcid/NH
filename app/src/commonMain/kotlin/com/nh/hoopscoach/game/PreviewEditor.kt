package com.nh.hoopscoach.game

internal class PreviewEditor(season: Int, reports: Map<Int, TeamPreview>) {
    private val picker = IssuePhrases(season)

    init {
        reports.values.forEach { r ->
            (listOf(r.overview, r.strength, r.concern, r.conference) + r.angles.map { it.text }).forEach { text(it, "Team report") }
        }
    }

    /**
     * The issue says each thing once: a sentence already printed elsewhere is dropped. (It used to be
     * reworded with a prefix like "For X, ...", which read like a form letter.) A paragraph keeps its
     * first sentence if dropping would leave it empty.
     */
    fun text(value: String, @Suppress("UNUSED_PARAMETER") context: String): String = value.split(Regex("\\n\\s*\\n")).mapNotNull { paragraph ->
        val all = sentences(paragraph)
        val fresh = all.filter { picker.reserve(it) }
        (fresh.ifEmpty { all.take(1) }).joinToString(" ").takeIf { it.isNotBlank() }
    }.joinToString("\n\n")

    fun story(s: Story, context: String = s.head) = Story(s.kicker, s.head, text(s.body, context), s.teamId, s.pid, s.aiProvider, s.fallbackReason)

    fun feature(f: Feature) = Feature(f.kind, f.title, text(f.deck, f.title), f.items.map {
        FeatureItem(it.head, text(it.body, it.head), it.tag, it.teamId, it.pid)
    }, f.grouped)

    companion object {
        fun sentences(text: String): List<String> = text.split(Regex("""(?<!\b[A-Z]\.)(?<!St\.)(?<!Mt\.)(?<!Dr\.)(?<!No\.)(?<!Jr\.)(?<!Sr\.)(?<=[.!?])\s+(?=[A-Z“"])"""))
            .map { it.trim() }.filter { it.isNotEmpty() }
    }
}
