package com.nh.hoopscoach

import com.nh.hoopscoach.game.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class PersonalityTest {
    private fun player(
        pas: Int,
        mid: Int,
        out: Int,
        ins: Int = 70,
        traits: List<Trait> = emptyList(),
        id: Int = 1,
    ): Player {
        val attrs = IntArray(Attr.entries.size) { 70 }
        attrs[Attr.PAS.ordinal] = pas
        attrs[Attr.MID.ordinal] = mid
        attrs[Attr.OUT.ordinal] = out
        attrs[Attr.INS.ordinal] = ins
        return Player(
            id = id, first = "Test", last = "Player", pos = Pos.SG, year = 2, height = 78, weight = 200,
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
        assertTrue(Personality.isoMod(gunner) > Personality.isoMod(passer))
    }

    @Test fun showboatIsMoreSelfishThanGunnerOnIsoAndKickouts() {
        val showboat = player(55, 88, 88, traits = listOf(Trait.SHOWBOAT))
        val gunner = player(55, 88, 88, traits = listOf(Trait.GUNNER))
        assertTrue(Personality.isoMod(showboat) > Personality.isoMod(gunner))
        assertTrue(Personality.kickOutChance(showboat, 0.30) < Personality.kickOutChance(gunner, 0.30))
        assertTrue(Personality.toughShotAppetite(showboat) >= Personality.toughShotAppetite(gunner))
    }

    @Test fun hotheadForcesMoreWhenTrailing() {
        val hot = player(65, 80, 80, traits = listOf(Trait.HOTHEAD))
        val even = Personality.kickOutChance(hot, 0.30, trail = 0)
        val down = Personality.kickOutChance(hot, 0.30, trail = -8)
        assertTrue(down < even)
    }

    @Test fun steadyAndCoachableDishMoreThanShowboat() {
        val showboat = player(70, 80, 80, traits = listOf(Trait.SHOWBOAT))
        val steady = player(70, 80, 80, traits = listOf(Trait.STEADY))
        val coachable = player(70, 80, 80, traits = listOf(Trait.COACHABLE))
        assertTrue(Tendency.pass(steady) > Tendency.pass(showboat))
        assertTrue(Tendency.pass(coachable) > Tendency.pass(showboat))
        assertTrue(Personality.kickOutChance(steady, 0.28) > Personality.kickOutChance(showboat, 0.28))
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
        Personality.applyAssignment(p, "SHOWBOAT", "Wants the poster.")
        assertTrue(p.has(Trait.SHOWBOAT))
        assertFalse(p.has(Trait.PASS_FIRST))
        Personality.applyAssignment(p, "HOTHEAD", "")
        assertTrue(p.has(Trait.HOTHEAD))
        assertTrue(p.personalityNote.isNotBlank())
    }

    @Test fun applyAssignmentKeepsSingleOnCourtTrait() {
        val p = player(70, 70, 70, traits = listOf(Trait.LOYAL, Trait.GUNNER))
        Personality.applyAssignment(p, "STEADY", "Calm under fire.")
        assertEquals(Trait.STEADY, Personality.onCourtTrait(p))
        assertTrue(p.has(Trait.LOYAL))
        assertFalse(p.has(Trait.GUNNER))
    }

    @Test fun seedIsIdempotentForOnCourtTrait() {
        Rng.r = Random(7)
        val p = player(92, 60, 60, id = 42)
        Personality.seedOnCourtTrait(p)
        val first = p.traits.toList()
        val note = p.personalityNote
        Personality.seedOnCourtTrait(p)
        assertEquals(first, p.traits.toList())
        assertEquals(note, p.personalityNote)
    }

    @Test fun seedAssignsSomeOnCourtPersonalityFromRatings() {
        Rng.r = Random(3)
        val scorers = (1..40).map { player(55, 90, 90, id = it) }
        Personality.seedRoster(scorers)
        assertTrue(Personality.countOnCourt(scorers) >= 8)
        assertTrue(scorers.any { it.has(Trait.GUNNER) || it.has(Trait.SHOWBOAT) })
    }

    @Test fun interviewParseKeepsPersonalityLines() {
        val gunner = player(60, 85, 85, traits = listOf(Trait.GUNNER))
        val byId = mapOf(1 to gunner)
        val raw = "1|confident|How did you get going tonight?|I hunted my shot. Coach trusted me late."
        val parsed = Ollama.parseInterviewLines(raw, byId, "Beat · Daily", true)
        assertEquals(1, parsed.size)
        assertEquals("confident", parsed[0].tone)
        assertTrue(parsed[0].answer.contains("hunted", ignoreCase = true))
    }
}
