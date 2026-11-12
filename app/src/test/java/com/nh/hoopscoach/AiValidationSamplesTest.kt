package com.nh.hoopscoach

import com.nh.hoopscoach.game.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class AiValidationSamplesTest {
    @Test fun representativeProviderCopy() {
        Rng.r = Random(41)
        val lg = LeagueGen.newLeague()
        val t = lg.user
        t.mascot = "Harborlights"
        lg.userTeamId = t.id
        t.w = 7; t.l = 3; lg.day = 10
        val p = lg.rosterOf(t).first()
        p.first = "Alex"; p.last = "Sentinel"
        p.stats.gp = 10; p.stats.pts = 173; p.stats.ast = 65
        val facts = AiFacts.build(lg, p.name, players = listOf(p))
        val copy = "${p.name} averages 17.3 PPG for ${t.school}, which is 7-3."
        val good = listOf(copy, copy.lowercase(), "${p.name.substringAfterLast(' ')} averages 17.3 PPG for ${t.mascot}, which is 7-3.",
            "${p.name} averages 17 PPG for ${t.school}.", "${t.mascot} is 7-3.", "HEADLINE: Season Preview\nBODY: $copy", "The ${t.school} roster includes ${p.name}.", "${p.name} averages seventeen points per game for ${t.school}.")
        val bad = listOf("${t.school} is 99-0.", "${p.name} averages 29.1 PPG for ${t.school}.", "Michael Invented averages 17.3 PPG for ${t.school}.", "HEADLINE: Alex Phantom leads ${t.school}\nBODY: $copy")
        val accepted = good.count { text -> AiGrounding.validate(text, facts).also { println("COPY: $text => $it") }.isEmpty() }
        println("3.4-pro.147 comparison: ${good.count { Legacy147Validation.validate(it, facts).isEmpty() }}/${good.size} factual accepted; ${bad.count { Legacy147Validation.validate(it, facts).isNotEmpty() }}/${bad.size} factual errors rejected.")
        val rejected = bad.count { AiGrounding.validate(it, facts).isNotEmpty() }
        println("REPRESENTATIVE COPY: $accepted/${good.size} factual accepted; $rejected/${bad.size} factual errors rejected. Curated provider-style fixtures, not live model responses.")
        assertEquals(bad.size, rejected)
        assertEquals(good.size, accepted)
    }
}

private object Legacy147Validation {
    private val number = Regex("(?<![\\p{L}\\d])\\d+(?:\\.\\d+)?%?")
    private val pair = Regex("\\b(\\d{1,3})\\s*[-–—]\\s*(\\d{1,3})\\b")
    private fun normalized(n: String) = java.math.BigDecimal(n.removeSuffix("%")).stripTrailingZeros().toPlainString() + if (n.endsWith("%")) "%" else ""
    private fun canonicalPairs(text: String) = pair.findAll(text).map { "${it.groupValues[1].toInt()}-${it.groupValues[2].toInt()}" }.toSet()

    fun validate(text: String, facts: AiFactSheet): List<String> {
        if (text.isBlank()) return listOf("empty response")
        val errors = mutableListOf<String>()
        if (Regex("\\b(simulat\\w*|sims|assum\\w*|rosters?)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) errors += "meta-commentary instead of sports copy"
        val content = text.lines().filterNot { it.startsWith("OUTLET:", true) || it.startsWith("REPORTER:", true) }
            .joinToString("\n") { it.replace(Regex("^(SCOOP|RUMOR) \\d+:"), "ITEM:") }
        val numbers = number.findAll(facts.text).map { normalized(it.value) }.toSet()
        if (number.findAll(content).any { normalized(it.value) !in numbers }) errors += "unsupported number"
        Regex("(\\d+(?:\\.\\d+)?)\\s*(PPG|RPG|APG|PTS|REB|AST|STL|BLK|TOV|MIN|points|rebounds|assists)\\b", RegexOption.IGNORE_CASE).findAll(content).forEach { m ->
            val prefix = content.substring((m.range.first - 100).coerceAtLeast(0), m.range.first)
            val player = facts.names.mapNotNull { name ->
                Regex(Regex.escape(name), RegexOption.IGNORE_CASE).findAll(prefix).lastOrNull()?.let { name to it.range.first }
            }.filter { (name, _) -> facts.text.lines().any { it.startsWith("PLAYER $name;") } }.maxByOrNull { it.second }?.first
            if (player != null) {
                val perGame = Regex("averag", RegexOption.IGNORE_CASE).containsMatchIn(prefix) || content.substring(m.range.last + 1).trimStart().startsWith("per game", true)
                val metric = when (m.groupValues[2].uppercase(java.util.Locale.US)) {
                    "POINTS" -> if (perGame) "PPG" else "PTS"
                    "REBOUNDS" -> if (perGame) "RPG" else "REB"
                    "ASSISTS" -> if (perGame) "APG" else "AST"
                    else -> m.groupValues[2].uppercase(java.util.Locale.US)
                }
                val expected = facts.text.lines().filter { if (metric in setOf("PPG", "RPG", "APG"))
                    it.startsWith("CURRENT $player:") || it.startsWith("LAST SEASON $player,")
                    else it.startsWith("BOX $player:") || it.startsWith("GAME LINE $player:") }
                    .flatMap { Regex("(\\d+(?:\\.\\d+)?) $metric\\b", RegexOption.IGNORE_CASE).findAll(it).map { match -> normalized(match.groupValues[1]) }.toList() }.toSet()
                if (normalized(m.groupValues[1]) !in expected) errors += "statistic belongs to another player"
            }
        }
        if (Regex("\\b(one|two|three|four|five|six|seven|eight|nine|ten|twenty|thirty|forty|fifty|sixty|seventy|eighty|ninety)[ -]+(wins|losses|points|rebounds|assists)\\b", RegexOption.IGNORE_CASE).containsMatchIn(content)) errors += "numeric claims must use fact-sheet digits"
        val scores = facts.text.lines().filter { it.startsWith("RESULT") || it.startsWith("EVENT FACTS") }.flatMap { canonicalPairs(it) }
        val allowedPairs = canonicalPairs(facts.text) + scores.map { p -> p.split('-').let { v -> "${v[1]}-${v[0]}" } }
        pair.findAll(content).forEach { m ->
            val value = "${m.groupValues[1].toInt()}-${m.groupValues[2].toInt()}"
            if (value !in allowedPairs) errors += "unsupported record or score"
            val prefix = content.substring((m.range.first - 90).coerceAtLeast(0), m.range.first)
            if (Regex("(?:record|sits? at|stands? at|is|are|\\()\\s*$", RegexOption.IGNORE_CASE).containsMatchIn(prefix) &&
                !Regex("last season|last year|previous|project|forecast|conference", RegexOption.IGNORE_CASE).containsMatchIn(prefix)) {
                val named = facts.records.keys.mapNotNull { name -> Regex(Regex.escape(name), RegexOption.IGNORE_CASE).findAll(prefix).lastOrNull()?.let { name to it.range.first } }.maxByOrNull { it.second }?.first
                if (named != null && value !in facts.records.getValue(named)) errors += "record belongs to another team"
            }
        }
        val known = facts.names.sortedByDescending { it.length }
        var residue = content
        known.forEach { name -> residue = residue.replace(Regex("(?i)(?<![\\p{L}\\d])${Regex.escape(name)}(?![\\p{L}\\d])"), "entity") }
        facts.allNames.filter { it !in facts.names && it.length > 3 && residue.contains(it, ignoreCase = true) }.forEach { name ->
            if (Regex("(?i)(?<![\\p{L}\\d])${Regex.escape(name)}(?![\\p{L}\\d])").containsMatchIn(residue)) errors += "entity outside fact sheet"
        }
        val common = setOf("Final Four", "Sweet Sixteen", "Sweet Sixteen", "Elite Eight", "Head Coach", "Coach", "Game Winner", "The Favorite", "New Era", "Courtside Report", "National Championship", "March Madness", "All America", "Most Valuable Player", "First Team", "Second Team", "Third Team", "Staff", "Hot Seat")
        common.sortedByDescending { it.length }.forEach { residue = residue.replace(it, "topic", ignoreCase = true) }
        if (Regex("\\b[A-Z][a-z]+(?:[ '-][A-Z][a-z]+)+\\b").containsMatchIn(residue)) errors += "unknown proper name"
        val subjects = setOf("He", "She", "It", "This", "That", "There", "They", "We", "What", "Who", "Basketball", "March", "April", "Defense", "Offense", "Depth", "Availability", "Continuity", "Nothing", "Everyone", "Nobody", "Something")
        if (Regex("\\b([A-Z][a-z]+)\\s+(?:is|are|has|went|won|lost)\\b").findAll(residue).any { it.groupValues[1] !in subjects }) errors += "unknown named subject"
        val lower = content.lowercase(java.util.Locale.US)
        if ((facts.preseason || facts.preview || facts.upcoming) && Regex("\\b(tonight's win|tonight's loss|won tonight|lost tonight|just defeated|clinched|eliminated|advanced to|celebrated the win)\\b").containsMatchIn(lower)) errors += "result claimed before event"
        if (facts.phase == Phase.OFFSEASON && Regex("\\b(tonight's game|tonight's win|tonight's loss|tomorrow's game)\\b").containsMatchIn(lower)) errors += "wrong season phase"
        if (!facts.preseason && !facts.preview && Regex("\\b(before the season begins|season has yet to begin|no games have been played)\\b").containsMatchIn(lower) && facts.phase != Phase.OFFSEASON) errors += "wrong season phase"
        if (facts.phase == Phase.REGULAR && !facts.preview && Regex("\\b(won the championship|won the title|clinched the title|playoff victory|postseason victory)\\b").containsMatchIn(lower)) errors += "wrong season phase"
        if (Regex("\\b(buzzer.beater|game.winning shot|late run|closing minutes|fourth.quarter run|second.chance points|locker.room champagne|parade|contract demand|demanded a trade|physicians confirmed|doctors confirmed|non.contact conditioning|medically cleared)\\b").containsMatchIn(lower)) errors += "unsupported event detail"
        return errors.distinct()
    }

}
