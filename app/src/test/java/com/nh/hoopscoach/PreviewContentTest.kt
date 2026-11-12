package com.nh.hoopscoach

import com.nh.hoopscoach.game.Offseason
import com.nh.hoopscoach.game.Pos
import com.nh.hoopscoach.game.Previews
import com.nh.hoopscoach.game.Rng
import com.nh.hoopscoach.game.Call
import com.nh.hoopscoach.game.IssuePhrases
import com.nh.hoopscoach.game.NbaPreview
import com.nh.hoopscoach.game.Odds
import com.nh.hoopscoach.game.Preview
import com.nh.hoopscoach.game.PreviewAvailability
import com.nh.hoopscoach.game.PreviewEditor
import com.nh.hoopscoach.game.PreviewTracker
import com.nh.hoopscoach.game.PreviewWriting
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlin.random.Random

/** Season preview copy should read like a magazine, not a spreadsheet. */
class PreviewContentTest {

    private fun league() = Offseason.newGame(5, "Test", "Coach")

    @Test
    fun blurbsOmitRawOverallsAndAllAmericaIsBalanced() {
        Rng.r = Random(41)
        val lg = Offseason.newGame(5, "Test", "Coach")
        val pv = Previews.current(lg)
        val top = lg.team(pv.proj.entries.first { it.value.nat == 1 }.key)
        val blurb = Previews.blurb(lg, top)
        assertFalse("blurb leaked an overall: $blurb", Regex("""\b\d{2}\b""").containsMatchIn(blurb) && blurb.contains("ovr", ignoreCase = true))
        assertFalse("blurb should not dump (POS, CLS, OVR): $blurb", Regex("""\([A-Z]{1,2},\s*[A-Z]{2},\s*\d{2}\)""").containsMatchIn(blurb))
        assertTrue(blurb.isNotBlank())
        val (s, c) = Previews.scoutingProse(lg, top)
        assertTrue(s.endsWith("."))
        assertTrue(c.endsWith("."))
        assertFalse("scouting prose jammed a raw label onto a clause: $s", Regex("""closeouts and [a-z]""").containsMatchIn(s.lowercase()))
        assertTrue(c.startsWith("The worry:"))
        assertTrue(Previews.coverDek(lg, top).isNotBlank())

        val letter = Previews.editorsLetter(lg, pv)
        val f4Names = pv.finalFour.map { lg.team(it).school }
        assertTrue("editor letter should mention the actual Final Four pick", f4Names.count { name -> letter.any { it.contains(name) } } >= 2)

        val aa = pv.allAmerica.take(5).map { lg.player(it).pos }
        assertEquals("first team should cover five positions", Pos.entries.toSet(), aa.toSet())
        assertEquals(4, pv.finalFour.size)
        assertEquals(pv.finalFour.distinct().size, 4)
        assertEquals(3, pv.debates.size)
        assertTrue(pv.debates.all { it.topic.isNotBlank() && it.takes.isNotEmpty() })
    }

    @Test
    fun picksAndRangesComeFromSimulationFrequencies() {
        Rng.r = Random(41)
        val lg = league()
        val pv = lg.preview!!
        assertEquals(pv.odds.values.maxOf { it.title }, pv.odds.getValue(pv.finalFour.first()).title, 0.0)
        val rest = pv.odds.filterKeys { it != pv.finalFour.first() }.values.map { it.f4 }.sortedDescending().take(3)
        assertEquals(rest, pv.finalFour.drop(1).map { pv.odds.getValue(it).f4 })
        assertEquals(1.0, pv.odds.values.sumOf { it.title }, 1e-9)
        assertEquals(4.0, pv.odds.values.sumOf { it.f4 }, 1e-9)
        pv.odds.forEach { (id, o) ->
            val p = pv.proj.getValue(id)
            assertTrue(o.median != null && o.lo <= o.median!! && o.median!! <= o.hi)
            assertTrue(o.lo >= 0 && o.hi <= p.w + p.l)
            listOf(o.conf, o.ncaa, o.s16, o.f4, o.title).forEach {
                assertTrue(it in 0.0..1.0)
                assertEquals(kotlin.math.round(it * 400), it * 400, 1e-8)
            }
            assertTrue(o.title <= o.f4 && o.f4 <= o.s16 && o.s16 <= o.ncaa)
        }
        Rng.r = Random(999)
        assertEquals(pv.finalFour, Previews.finalFourPicks(lg.season, pv.odds))
        assertTrue(Previews.finalFourPicks(2026, emptyMap()).isEmpty())
    }

    @Test
    fun issueProseIsConcreteUniqueAndDeterministic() {
        Rng.r = Random(41)
        val lg = league()
        val pv = lg.preview!!
        val text = pv.reports.values.flatMap { r ->
            assertTrue(r.angles.size in 1..2)
            assertTrue(r.angles.zipWithNext().all { (a, b) -> a.score >= b.score })
            assertTrue(r.strength.any { it.isDigit() })
            assertTrue(r.concern.any { it.isDigit() })
            listOf(r.overview, r.strength, r.concern, r.conference) + r.angles.map { it.text }
        } + pv.stories.map { it.body } + pv.features.flatMap { listOf(it.deck) + it.items.map { item -> item.body } } +
            pv.bold + listOfNotNull(pv.cover?.body) + pv.letter
        val sentences = text.flatMap { PreviewEditor.sentences(it) }
        assertEquals("Duplicate issue sentences: ${sentences.groupingBy { it }.eachCount().filterValues { it > 1 }}",
            sentences.size, sentences.distinct().size)
        val rebuilt = Previews.build(lg)
        assertEquals(Json.encodeToString(Preview.serializer(), pv), Json.encodeToString(Preview.serializer(), rebuilt))
        val next = IssuePhrases(lg.season + 1)
        val now = IssuePhrases(lg.season)
        val pools = (1..12).map { i -> (1..12).map { "Team $i option $it." } }
        assertTrue(pools.map { now.pick(it, "team") } != pools.map { next.pick(it, "team") })
        val picker = IssuePhrases(2026)
        val first = picker.pick(listOf("One sentence."), "First")
        assertTrue(first != picker.pick(listOf("One sentence."), "Second"))
        assertThrows(IllegalArgumentException::class.java) { picker.pick(emptyList(), "Empty") }
    }

    @Test
    fun oldPreviewLoadsWithoutNewFields() {
        val old = """{"season":2026,"proj":{},"allAmerica":[],"freshmen":[],"transfers":[],"breakouts":[],"confPoy":{},"finalFour":[],"sleeper":-1}"""
        val pv = Json.decodeFromString(Preview.serializer(), old)
        assertTrue(pv.reports.isEmpty() && pv.regularActual.isEmpty() && pv.letter.isEmpty())
        val odds = Json.decodeFromString(Odds.serializer(), """{"conf":0.4,"ncaa":0.5,"s16":0.2,"f4":0.1,"title":0.05,"lo":15,"hi":23}""")
        assertEquals(null, odds.median)
        assertTrue(PreviewWriting.range(odds.lo, odds.median, odds.hi, 30).contains("older issue"))
        assertTrue(Json.decodeFromString(NbaPreview.serializer(), """{"season":2026,"proj":{}}""").reports.isEmpty())
        assertEquals("On the bubble", PreviewWriting.label(0.5, 0.01, 0.05))
        assertTrue(PreviewWriting.chance(0.35, "a bid").contains("either way"))
        assertTrue(PreviewWriting.chance(0.65, "a bid").contains("either way"))
    }

    @Test
    fun knownAvailabilityAndPreviousMissDriveAngles() {
        Rng.r = Random(41)
        val lg = league()
        val pv = lg.preview!!
        val t = lg.user
        assertEquals(0.0, PreviewAvailability.loss(lg, t), 0.0)
        val star = lg.rosterOf(t).maxBy { it.ovr }
        star.injGames = 999
        star.injury = "a torn ACL"
        val loss = PreviewAvailability.loss(lg, t)
        assertTrue(loss > 0)
        val injured = Previews.build(lg)
        assertTrue(injured.proj.getValue(t.id).w <= pv.proj.getValue(t.id).w)
        assertTrue(injured.reports.getValue(t.id).angles.any { it.kind == "availability" && it.text.contains(star.name) })
        star.injGames = 1
        assertTrue(PreviewAvailability.loss(lg, t) < loss)
        star.injGames = 999
        pv.regularActual = mapOf(t.id to pv.proj.getValue(t.id).w + 18)
        lg.season++
        val report = PreviewWriting.college(lg, pv.proj, pv.odds).getValue(t.id)
        assertEquals(setOf("availability", "receipts"), report.angles.map { it.kind }.toSet())
        assertTrue(report.angles.first { it.kind == "receipts" }.text.contains("regular-season"))
    }

    @Test
    fun liveTrackerChangesWithoutMutatingIssue() {
        Rng.r = Random(41)
        val lg = league()
        val old = lg.preview!!
        val pv = Preview(lg.season, old.proj, emptyList(), emptyList(), emptyList(), emptyList(), emptyMap(), old.finalFour,
            old.sleeper, bold = listOf("A top-25 finish"), calls = listOf(Call("C_NET", lg.userTeamId, n = 25)))
        val before = Json.encodeToString(Preview.serializer(), pv)
        assertTrue(PreviewTracker.college(lg, pv).teams.all { it.onTrack == null })
        lg.user.w = 9; lg.user.l = 1; lg.user.net = 5
        val on = PreviewTracker.college(lg, pv)
        assertEquals("On track", on.calls.single().status)
        assertTrue(on.teams.first { it.teamId == lg.userTeamId }.current.contains("9-1"))
        lg.user.w = 0; lg.user.l = 10; lg.user.net = 90
        val off = PreviewTracker.college(lg, pv)
        assertEquals("Off track", off.calls.single().status)
        assertEquals(false, off.teams.first { it.teamId == lg.userTeamId }.onTrack)
        assertEquals(before, Json.encodeToString(Preview.serializer(), pv))
        val legacy = Preview(lg.season, old.proj, emptyList(), emptyList(), emptyList(), emptyList(), emptyMap(), old.finalFour,
            old.sleeper, bold = listOf("An old call"))
        assertEquals("Awaiting evidence", PreviewTracker.college(lg, legacy).calls.single().status)
    }
}
