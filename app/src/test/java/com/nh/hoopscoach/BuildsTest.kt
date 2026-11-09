package com.nh.hoopscoach

import com.nh.hoopscoach.game.Arch
import com.nh.hoopscoach.game.Builds
import com.nh.hoopscoach.game.LeagueGen
import com.nh.hoopscoach.game.Pos
import com.nh.hoopscoach.game.Ratings
import com.nh.hoopscoach.game.Rng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BuildsTest {

    @Test
    fun catalogCoversEveryPosition() {
        assertTrue(Builds.catalog.size >= 24)
        for (pos in Pos.entries) {
            assertTrue("$pos has GOAT presets", Builds.forPos(pos).isNotEmpty() || Builds.nearby(pos).isNotEmpty())
        }
    }

    @Test
    fun jordanShapedVectorMatchesJordan() {
        val jordan = Builds.byId("jordan")!!
        val closest = Builds.closestGoats(Pos.SG, jordan.attrs, 3)
        assertEquals("Michael Jordan", closest.first().name)
        assertTrue(closest.size >= 2)
        val name = Builds.buildName(Pos.SG, jordan.attrs)
        assertTrue("build name not blank: '$name'", name.isNotBlank())
    }

    @Test
    fun offspringBlendsMidpoint() {
        val a = Builds.byId("jordan")!!
        val b = Builds.byId("curry")!!
        val mid = Builds.offspring(a, b, 0.5f)
        for (i in 0 until 10) {
            val lo = minOf(a.attrs[i], b.attrs[i])
            val hi = maxOf(a.attrs[i], b.attrs[i])
            assertTrue("attr $i in range", mid[i] in lo..hi)
        }
    }

    @Test
    fun applyTemplateKeepsOverallNearCurrent() {
        Rng.r = Random(42)
        val lg = LeagueGen.newLeague()
        val p = lg.rosterOf(lg.user).maxBy { it.ovr }
        val before = p.ovr
        val goat = Builds.nearby(p.pos).first()
        Builds.applyGoat(p, goat)
        assertTrue("ovr stayed near $before, got ${p.ovr}", kotlin.math.abs(p.ovr - before) <= 2)
        assertTrue(p.buildName.isNotEmpty())
        assertTrue(p.likeA.isNotEmpty())
        assertEquals(goat.id, p.goatId)
        assertNotEquals(Arch.MANUAL, p.style)
    }

    @Test
    fun customRedistributesWithoutHugeOvrSwing() {
        Rng.r = Random(7)
        val lg = LeagueGen.newLeague()
        val p = lg.rosterOf(lg.user).first()
        val before = p.ovr
        val custom = IntArray(10) { 50 }
        custom[2] = 99
        custom[3] = 90
        Builds.applyCustom(p, custom)
        assertTrue(kotlin.math.abs(p.ovr - before) <= 2)
        assertTrue(p.buildName.isNotEmpty())
    }

    @Test
    fun scaleToOvrHitsTarget() {
        val goat = Builds.byId("duncan")!!
        for (target in listOf(55, 70, 85)) {
            val a = Builds.scaleToOvr(Pos.PF, goat.attrs, target)
            assertTrue("target $target got ${Ratings.ovr(Pos.PF, a)}", kotlin.math.abs(Ratings.ovr(Pos.PF, a) - target) <= 2)
        }
    }
}
