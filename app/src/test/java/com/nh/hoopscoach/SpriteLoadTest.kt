package com.nh.hoopscoach

import androidx.test.core.app.ApplicationProvider
import com.nh.hoopscoach.game.Face
import com.nh.hoopscoach.ui.SpriteCompose
import com.nh.hoopscoach.ui.SpriteSheets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SpriteLoadTest {
    @Test
    fun jerseyKeyUsesSlotForNbaAndAbbrForCollege() {
        assertEquals("lakers", SpriteSheets.jerseyKey("LAL", "LAL", "Los Angeles Lakers"))
        assertEquals("heat", SpriteSheets.jerseyKey("MIA", "MIA", "Miami Heat"))
        // College: empty slot → college pack by school abbr (not NBA Heat/Pacers/etc.).
        assertEquals("48_miami", SpriteSheets.jerseyKey("MIA", "", "Miami Hurricanes"))
        assertEquals("01_duke", SpriteSheets.jerseyKey("DUKE", "", "Duke Blue Devils"))
        assertEquals("10_gonzaga", SpriteSheets.jerseyKey("GONZ", "", "Gonzaga Bulldogs"))
        assertEquals("07_indiana", SpriteSheets.jerseyKey("IND", "", "Indiana Hoosiers"))
        assertEquals("05_uconn", SpriteSheets.jerseyKey("CONN", "", "UConn Huskies"))
        // School without art still returns null → blank recolor.
        assertNull(SpriteSheets.jerseyKey("WAKE", "", "Wake Forest Demon Deacons"))
    }

    @Test
    fun loadsAndCompositesFace() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        AndroidAssets.appContext = ctx
        SpriteSheets.install()
        val body = SpriteSheets.body(4)!!
        assertEquals(512, body.width)
        assertEquals(512, body.height)
        assertTrue(body.pixels.any { it != 0 })

        val composed = SpriteCompose.render(
            Face(4, 0, 5, 0, 0, 0, 0, 0, 0),
            0xFF552583.toInt(),
            0xFFFDB927.toInt(),
            1,
            jerseyKey = "lakers",
        )
        assertTrue(composed.count { it != 0 } > 1000)
    }
}
