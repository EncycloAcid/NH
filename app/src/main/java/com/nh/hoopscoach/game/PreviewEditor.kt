package com.nh.hoopscoach.game

internal class PreviewEditor(season: Int, reports: Map<Int, TeamPreview>) {
    private val picker = IssuePhrases(season)

    init {
        reports.values.forEach { r ->
            (listOf(r.overview, r.strength, r.concern, r.conference) + r.angles.map { it.text }).forEach { text(it, "Team report") }
        }
    }

    fun text(value: String, context: String): String = sentences(value).joinToString(" ") { sentence ->
        if (picker.reserve(sentence)) return@joinToString sentence
        val body = sentence.replaceFirstChar { it.lowercase() }
        val label = context.replace(Regex("(?<=\\d)\\.(?=\\s)"), "")
        picker.pick(listOf("For $label, $body", "Our $label assessment: $body",
            "The $label angle is this: $body"), label)
    }

    fun story(s: Story, context: String = s.head) = Story(s.kicker, s.head, text(s.body, context), s.teamId, s.pid, s.aiProvider)

    fun feature(f: Feature) = Feature(f.kind, f.title, text(f.deck, f.title), f.items.map {
        FeatureItem(it.head, text(it.body, it.head), it.tag, it.teamId, it.pid)
    }, f.grouped)

    companion object {
        fun sentences(text: String): List<String> = text.split(Regex("""(?<!\b[A-Z]\.)(?<!St\.)(?<!Mt\.)(?<!Dr\.)(?<!No\.)(?<!Jr\.)(?<!Sr\.)(?<=[.!?])\s+(?=[A-Z“"])"""))
            .map { it.trim() }.filter { it.isNotEmpty() }
    }
}
