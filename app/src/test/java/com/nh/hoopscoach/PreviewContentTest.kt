package com.nh.hoopscoach

import com.nh.hoopscoach.game.Offseason
import com.nh.hoopscoach.game.Pos
import com.nh.hoopscoach.game.Previews
import com.nh.hoopscoach.game.Rng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Season preview copy should read like a magazine, not a spreadsheet. */
class PreviewContentTest {

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
}
