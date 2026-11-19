package com.nh.hoopscoach

import com.nh.hoopscoach.game.LeagueGen
import com.nh.hoopscoach.game.Memories
import com.nh.hoopscoach.game.Memory
import com.nh.hoopscoach.game.Rng
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class MemoriesTest {
    @Test fun whatHeRemembersLeansOnWhetherHeStays() {
        Rng.r = Random(7)
        val lg = LeagueGen.newLeague()
        val t = lg.user
        val p = lg.rosterOf(t).first()
        assertEquals(1.0, Memories.transferFactor(p), 1e-9)
        Memories.add(lg, p, "title", "Won it all", t.id, 3)
        assertTrue(Memories.transferFactor(p) < 0.7)                    // rooted
        Memories.add(lg, p, "title", "Won it all", t.id, 3)              // the same thing isn't remembered twice
        assertEquals(1, p.memories.size)
        p.memories.clear()
        Memories.add(lg, p, "promise", "A role that never came", t.id, -4)
        assertTrue(Memories.transferFactor(p) > 1.8)                    // restless
        // A memory of another school doesn't tie him to this one.
        p.memories.clear()
        Memories.add(lg, p, "title", "Won it all elsewhere", t.id + 1, 3)
        assertEquals(1.0, Memories.transferFactor(p), 1e-9)
    }

    @Test fun keepsTheStrongestTen() {
        Rng.r = Random(7)
        val lg = LeagueGen.newLeague()
        val p = lg.rosterOf(lg.user).first()
        repeat(14) { i -> Memories.add(lg, p, "k$i", "memory $i", lg.userTeamId, if (i == 3) -4 else 1) }
        assertEquals(Memories.KEEP, p.memories.size)
        assertEquals("k3", Memories.defining(p)!!.kind)
        assertTrue(p.memories.any { it.kind == "k3" })
    }

    @Test fun oldSavesLoadWithNoMemories() {
        val m = Memory(2026, "bench", "Sat", 4, -2)
        assertEquals(-2, m.pull)
        assertTrue(LeagueGen.newLeague().players.all { it.memories.isEmpty() })
    }
}
