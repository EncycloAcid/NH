package com.nh.hoopscoach

import com.nh.hoopscoach.game.LeagueGen
import com.nh.hoopscoach.game.Offseason
import com.nh.hoopscoach.game.OllamaConfig
import com.nh.hoopscoach.game.Phase
import com.nh.hoopscoach.game.Rng
import com.nh.hoopscoach.game.Sim
import com.nh.hoopscoach.game.TemplateNewsGenerator
import com.nh.hoopscoach.game.WeeklyDispatch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.random.Random

/** The weekly Hoops Dispatch: the fact sheet, the template edition, and reading a model's JSON safely. */
class WeeklyDispatchTest {
    @Before
    fun noModel() { WeeklyDispatch.generator = TemplateNewsGenerator }

    private fun week2(): com.nh.hoopscoach.game.League {
        Rng.r = Random(7)
        var lg = LeagueGen.newLeague()
        lg = Offseason.claimTeam(lg, lg.teams.first { it.abbr == "DUKE" }.id, "Sam", "Reed")
        var guard = 0
        while (lg.phase != Phase.REGULAR && guard++ < 400) Sim.playDay(lg)
        repeat(14) { Sim.playDay(lg) }
        return lg
    }

    @Test
    fun aWeekGetsOneEditionAndAShortFactSheet() {
        val lg = week2()
        val ed = WeeklyDispatch.latest(lg)
        assertNotNull("the game's own edition goes up at the end of the week", ed)
        assertTrue(ed!!.headlines.isNotEmpty())
        val brief = WeeklyDispatch.weeklyBrief(lg, 2)
        // Well under Gemini Nano's ~4,000-token window (about 3 characters a token).
        assertTrue("fact sheet is ${brief.json.length} chars", brief.json.length < 6000)
        val tpl = runBlocking { TemplateNewsGenerator.weekly(brief, OllamaConfig()) }
        assertEquals(brief.lines.take(5), tpl.headlines)
    }

    @Test
    fun readsJsonAndDropsLinesThatInventThings() {
        val lg = week2()
        val brief = WeeklyDispatch.weeklyBrief(lg, 2)
        val real = brief.names.first { it.length > 4 }
        val num = brief.numbers.first { (it.toIntOrNull() ?: 0) > 20 }
        val reply = "Sure! ```json\n{\"headlines\": [\"$real rolls on with $num\", \"Zxqv Plorb stuns everyone\", \"$real wins by 999\"], \"rumors\": [\"Is $real shopping?\"]}\n```"
        val (h, r) = WeeklyDispatch.parse(reply, "headlines", "rumors", brief)!!
        assertEquals(listOf("$real rolls on with $num"), h)
        assertEquals(listOf("Is $real shopping?"), r)
        assertNull(WeeklyDispatch.parse("no json here", "headlines", "rumors", brief))
    }
}
