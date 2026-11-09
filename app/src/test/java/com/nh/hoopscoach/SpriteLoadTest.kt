package com.nh.hoopscoach

import android.graphics.Bitmap
import android.graphics.Canvas
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
        assertEquals("college_miami", SpriteSheets.jerseyKey("MIA", "", "Miami Hurricanes"))
        assertEquals("college_duke", SpriteSheets.jerseyKey("DUKE", "", "Duke Blue Devils"))
        assertEquals("college_gonzaga", SpriteSheets.jerseyKey("GONZ", "", "Gonzaga Bulldogs"))
        assertEquals("college_indiana", SpriteSheets.jerseyKey("IND", "", "Indiana Hoosiers"))
        assertEquals("college_uconn", SpriteSheets.jerseyKey("CONN", "", "UConn Huskies"))
        // School without art still returns null → blank recolor.
        assertNull(SpriteSheets.jerseyKey("WAKE", "", "Wake Forest Demon Deacons"))
    }

    @Test
    fun loadsAndCompositesFace() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        SpriteSheets.install(ctx.assets)
        val body = SpriteSheets.body(4)!!
        val px = IntArray(body.width * body.height)
        body.getPixels(px, 0, body.width, 0, 0, body.width, body.height)
        fun sample(label: String, arr: IntArray, w: Int, x: Int, y: Int) {
            val c = arr[y * w + x]
            println("$label ($x,$y)=${c.toUInt().toString(16)} a=${c ushr 24} r=${(c shr 16) and 0xFF}")
        }
        sample("bodySrc", px, body.width, 255, 160)

        val mutable = body.copy(Bitmap.Config.ARGB_8888, true)
        val canvasBmp = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        println("canvas config=${canvasBmp.config} mutable=${canvasBmp.isMutable}")
        val g = Canvas(canvasBmp)
        g.drawBitmap(mutable, 0f, 0f, null)
        val after = IntArray(512 * 512)
        canvasBmp.getPixels(after, 0, 512, 0, 0, 512, 512)
        sample("afterDraw", after, 512, 255, 160)
        var nonzero = after.count { it != 0 }
        println("nonzero=$nonzero")

        val jersey = SpriteSheets.jersey("lakers")!!
        println("jersey ${jersey.width}x${jersey.height}")
        val jw = (jersey.width * 1.186f).toInt()
        val jh = (jersey.height * 1.186f).toInt()
        val jScaled = Bitmap.createScaledBitmap(jersey, jw, jh, false)
        g.drawBitmap(jScaled, 102f, 195f, null)
        canvasBmp.getPixels(after, 0, 512, 0, 0, 512, 512)
        sample("afterJerseyFace", after, 512, 255, 160)
        sample("afterJerseyChest", after, 512, 255, 320)

        val hair = SpriteSheets.hair(5)!!
        println("hair ${hair.width}x${hair.height}")
        // use compose placement roughly
        val hScaled = Bitmap.createScaledBitmap(hair, 389, 320, false)
        g.drawBitmap(hScaled, 61f, -35f, null)
        canvasBmp.getPixels(after, 0, 512, 0, 0, 512, 512)
        sample("afterHairFace", after, 512, 255, 160)
        sample("afterHairTop", after, 512, 255, 40)

        val out = Bitmap.createScaledBitmap(canvasBmp, 256, 256, false)
        val outPx = IntArray(256 * 256)
        out.getPixels(outPx, 0, 256, 0, 0, 256, 256)
        sample("scaledFace", outPx, 256, 128, 84)
        println("scaled nonzero=" + outPx.count { it != 0 })

        val composed = SpriteCompose.render(Face(4, 0, 5, 0, 0, 0, 0, 0, 0), 0xFF552583.toInt(), 0xFFFDB927.toInt(), 1, jerseyKey = "lakers")
        sample("composeFace", composed, 256, 128, 84)
        println("compose nonzero=" + composed.count { it != 0 })
        assertTrue(composed.count { it != 0 } > 1000)
    }
}
