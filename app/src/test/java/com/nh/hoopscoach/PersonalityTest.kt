package com.nh.hoopscoach

import com.nh.hoopscoach.game.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class PersonalityTest {
    private fun player(pas: Int, mid: Int, out: Int, ins: Int = 70, traits: List<Trait> = emptyList()): Player {
        val attrs = IntArray(Attr.entries.size) { 70 }
        attrs[Attr.PAS.ordinal] = pas
        attrs[Attr.MID.ordinal] = mid
        attrs[Attr.OUT.ordinal] = out
        attrs[Attr.INS.ordinal] = ins
        return Player(
            id = 1, first = "Test", last = "Player", pos = Pos.SG, year = 2, height = 78, weight = 200,
            attrs = attrs, pot = 80, style = Arch.THREE_LEVEL, focus = Arch.THREE_LEVEL, teamId = 0, number = 1,
            hometown = "Town", highSchool = "HS", traits = traits.toMutableList(),
        )
    }

    @Test fun gunnerUsesMoreAndPassesLessThanUnselfish() {
        val gunner = player(60, 85, 85, traits = listOf(Trait.GUNNER))
        val passer = player(90, 70, 70, traits = listOf(Trait.PASS_FIRST))
        assertTrue(Tendency.usage(gunner) > Tendency.usage(passer))
        assertTrue(Tendency.pass(passer) > Tendency.pass(gunner))
        assertTrue(Personality.toughShotAppetite(gunner) > Personality.toughShotAppetite(passer))
        assertTrue(Personality.kickOutChance(passer, 0.28) > Personality.kickOutChance(gunner, 0.28))
    }

    @Test fun applyAssignmentSetsTraitAndNote() {
        val p = player(70, 70, 70)
        Personality.applyAssignment(p, "GUNNER", "Loves step-back jumpers.")
        assertTrue(p.has(Trait.GUNNER))
        assertFalse(p.has(Trait.PASS_FIRST))
        assertEquals("Loves step-back jumpers.", p.personalityNote)
        Personality.applyAssignment(p, "PASS_FIRST", "Finds the open man.")
        assertTrue(p.has(Trait.PASS_FIRST))
        assertFalse(p.has(Trait.GUNNER))
    }

    @Test fun seedIsIdempotentForOnCourtTrait() {
        Rng.r = Random(7)
        val p = player(92, 60, 60)
        Personality.seedOnCourtTrait(p)
        val first = p.traits.toList()
        Personality.seedOnCourtTrait(p)
        assertEquals(first, p.traits.toList())
    }
}
