package com.nh.hoopscoach

import com.nh.hoopscoach.game.Offseason
import com.nh.hoopscoach.game.Rng
import com.nh.hoopscoach.ui.facePath
import com.nh.hoopscoach.ui.resolvePlayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Social feed avatars: players resolve by name/handle, and face paths stay stable. */
class SocialAvatarTest {

    @Test
    fun resolvePlayerByNameAndHandle() {
        Rng.r = Random(9)
        val lg = Offseason.newGame(5, "Test", "Coach")
        val p = lg.rosterOf(lg.user).first()
        assertEquals(p.id, resolvePlayer(lg, p.name)!!.id)
        val handle = p.name.replace(" ", "").replace(".", "").lowercase()
        assertEquals(p.id, resolvePlayer(lg, "Somebody Else", handle)!!.id)
        assertEquals(null, resolvePlayer(lg, "Not A Real Player", "ghostaccount"))
    }

    @Test
    fun facePathIsStablePerHandleAndSave() {
        val a = facePath("hoopsfan99", "Big Marcus", save = 42L)
        val b = facePath("hoopsfan99", "Big Marcus", save = 42L)
        val c = facePath("hoopsfan99", "Big Marcus", save = 99L)
        assertEquals(a, b)
        assertTrue(a.startsWith("faces/"))
        assertTrue("different saves pick different faces", a != c)
        assertTrue(facePath("jess_hoops", "Jess From Section 12", 1L).contains("w"))
    }
}
