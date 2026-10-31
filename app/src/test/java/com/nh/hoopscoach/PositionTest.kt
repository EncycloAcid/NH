package com.nh.hoopscoach

import com.nh.hoopscoach.game.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Two-position players: who gets a second spot, and that coaches use it. */
class PositionTest {
    @Test
    fun secondPositions() {
        Rng.r = Random(4)
        val lg = LeagueGen.newLeague()
        val all = lg.players
        val two = all.filter { it.pos2 != null }
        println("college: ${two.size} of ${all.size} play two positions; " + two.groupingBy { it.posLabel }.eachCount().entries.sortedByDescending { it.value }.joinToString { "${it.key} ${it.value}" })
        assertTrue("about a third play two positions", two.size in (all.size * 0.2).toInt()..(all.size * 0.5).toInt())
        for (pos in Pos.entries) {
            val at = all.filter { it.pos == pos }
            val share = at.count { it.pos2 != null }.toDouble() / at.size
            println("  $pos: %.0f%% play two".format(share * 100))
            assertTrue("every position has some ($pos %.2f)".format(share), share in 0.08..0.6)
        }
        assertTrue("only next-door positions", two.all { it.pos2 in Ratings.secondOptions(it.pos) })
        assertTrue("labels", two.all { it.posLabel == "${it.pos}/${it.pos2}" })

        val pro = ProLeague.create(ProTest().toDraft(21))
        val proTwo = pro.players.filter { it.pos2 != null }
        println("pros: ${proTwo.size} of ${pro.players.size}; e.g. " + proTwo.filter { it.photo.isNotEmpty() }.take(8).joinToString { "${it.name} ${it.posLabel}" })
        assertTrue("pros too", proTwo.size >= pro.players.size / 6)

        // A team's rotation can use a player at his second position.
        val used = pro.teams.sumOf { t -> t.plan.slots.withIndex().count { (i, s) -> listOf(s.starter, s.reserve).any { id -> pro.playerOrNull(id)?.let { it.pos != Pos.entries[i] && it.pos2 == Pos.entries[i] } == true } } }
        println("rotation spots filled by a second position: $used")
        assertTrue("coaches use second positions", used > 0)

        // The user's player can choose one.
        val h = Road.createHero(lg, Road.Spec("Dre", "Collins", Pos.SG, 77, 200, "Memphis", "TN", Arch.SHARPSHOOTER, 4, 1, pos2 = Pos.PG))
        assertEquals("SG/PG", h.posLabel)
        assertTrue("styles from both", Ratings.stylesFor(h).containsAll(Ratings.styles(Pos.PG)))
        val bad = Road.createHero(lg, Road.Spec("Dre", "Collins", Pos.SG, 77, 200, "Memphis", "TN", Arch.SHARPSHOOTER, 4, 1, pos2 = Pos.C))
        assertEquals("not a center", null, bad.pos2)
        assertEquals("it saves", "SG/PG", SaveIO.decode(SaveIO.encode(lg.also { it.players += h })).players.first { it.id == h.id }.posLabel)
    }
}
