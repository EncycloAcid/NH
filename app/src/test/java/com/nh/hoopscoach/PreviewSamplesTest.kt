package com.nh.hoopscoach

import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.AiTemplates
import com.nh.hoopscoach.game.NbaPreview
import com.nh.hoopscoach.game.Offseason
import com.nh.hoopscoach.game.PreviewEditor
import com.nh.hoopscoach.game.PreviewWriting
import com.nh.hoopscoach.game.ProLeague
import com.nh.hoopscoach.game.ProSeason
import com.nh.hoopscoach.game.Rng
import com.nh.hoopscoach.game.TeamPreview
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

class PreviewSamplesTest {
    @Test fun dumpThreeCompleteTeamPreviews() {
        Rng.r = Random(41)
        val college = Offseason.newGame(5, "Test", "Coach")
        val pv = college.preview!!
        val ids = pv.proj.entries.sortedBy { it.value.nat }
        val pro = ProLeague.coachLeague(2026, "LAL", "Test", "Coach")
        ProSeason.startSeason(pro)
        val nba = pro.proState!!.preview!!
        val output = buildString {
            listOf(ids.first().key, ids.last().key).forEachIndexed { i, id ->
                val t = college.team(id)
                val p = pv.proj.getValue(id)
                val o = pv.odds.getValue(id)
                appendLine("=== SAMPLE ${i + 1}: ${t.school} · ${college.season} COLLEGE PREVIEW ===")
                appendLine("National No. ${p.nat}; conference No. ${p.confRank}; projected ${p.w}-${p.l}.")
                appendLine(PreviewWriting.range(o.lo, o.median, o.hi, p.w + p.l))
                appendLine("NCAA ${PreviewWriting.percent(o.ncaa)}; conference ${PreviewWriting.percent(o.conf)}; Final Four ${PreviewWriting.percent(o.f4)}; title ${PreviewWriting.percent(o.title)}.")
                appendLine("GAME-WRITTEN ARTICLE")
                appendLine(AiTemplates.previewCopy(college, t))
                report(pv.reports.getValue(id))
                appendLine("EDITOR'S LETTER")
                pv.letter.forEach { appendLine(it) }
                appendLine("STORYLINES")
                pv.stories.forEach { appendLine("${it.head}\n${it.body}\n") }
                appendLine("FEATURES")
                pv.features.forEach { f ->
                    appendLine("${f.title}\n${f.deck}")
                    f.items.forEach { appendLine("${it.head}: ${it.body}") }
                }
                appendLine("FINAL FOUR: " + pv.finalFour.joinToString { college.team(it).school })
                appendLine("BOLD PICKS")
                pv.bold.forEach { appendLine(it) }
                appendLine("DEBATES")
                pv.debates.forEach { appendLine(Json.encodeToString(com.nh.hoopscoach.game.TvSegment.serializer(), it)) }
                appendLine()
            }
            val t = pro.user
            val o = nba.proj.getValue(t.id)
            appendLine("=== SAMPLE 3: ${t.name} · ${pro.season} PRO PREVIEW ===")
            appendLine("Projected ${o.w}-${o.l}; conference seed ${"%.1f".format(java.util.Locale.US, o.seed)}.")
            appendLine(PreviewWriting.range(o.lo, o.median, o.hi, o.w + o.l))
            appendLine("Playoffs ${PreviewWriting.percent(o.playoffs)}; conference champion ${PreviewWriting.percent(o.finals)}; conference finals ${PreviewWriting.percent(o.confFinals)}; title ${PreviewWriting.percent(o.title)}.")
            appendLine("GAME-WRITTEN ARTICLE")
            appendLine(AiTemplates.previewCopy(pro, t))
            report(nba.reports.getValue(t.id))
            nba.stories.forEach { appendLine("${it.head}\n${it.body}\n") }
            nba.features.forEach { f ->
                appendLine("${f.title}\n${f.deck}")
                f.items.forEach { appendLine("${it.head}: ${it.body}") }
            }
            appendLine("BOLD PICKS")
            nba.bold.forEach { appendLine(it) }
            appendLine("DEBATES")
            nba.debates.forEach { appendLine(Json.encodeToString(com.nh.hoopscoach.game.TvSegment.serializer(), it)) }
        }
        val out = File(System.getenv("PREVIEW_SAMPLES_PATH") ?: "build/preview-samples.txt")
        out.parentFile?.mkdirs()
        out.writeText(output)
        assertTrue(out.length() > 1000)
        assertTrue(!Regex("simulat|assum|\\b(sims|rosters?)\\b", RegexOption.IGNORE_CASE).containsMatchIn(output))
        assertProRanges(pro, nba)
        println("Wrote 3 complete team previews: ${out.absolutePath}")
    }

    private fun StringBuilder.report(r: TeamPreview) {
        appendLine(r.overview)
        r.angles.forEach { appendLine("${it.kind.uppercase()}: ${it.text}") }
        appendLine(r.strength)
        appendLine(r.concern)
        appendLine(r.conference)
        appendLine()
    }

    private fun assertProRanges(lg: League, pv: NbaPreview) {
        pv.proj.forEach { (_, p) ->
            assertTrue(p.median != null && p.lo <= p.median!! && p.median!! <= p.hi)
            assertTrue(p.lo >= 0 && p.hi <= p.w + p.l)
        }
        assertTrue(pv.reports.keys == lg.teams.map { it.id }.toSet())
        val text = pv.reports.values.flatMap { r -> listOf(r.overview, r.conference, r.strength, r.concern) + r.angles.map { it.text } } +
            pv.stories.map { it.body } + pv.features.flatMap { listOf(it.deck) + it.items.map { item -> item.body } } +
            pv.bold + listOfNotNull(pv.cover?.body)
        val sentences = text.flatMap { PreviewEditor.sentences(it) }
        assertTrue("No duplicate pro issue sentences", sentences.size == sentences.distinct().size)
        val saved = Json.encodeToString(NbaPreview.serializer(), pv)
        assertTrue(Json.decodeFromString(NbaPreview.serializer(), saved).reports.isNotEmpty())
    }
}
