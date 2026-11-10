package com.nh.hoopscoach

import androidx.test.core.app.ApplicationProvider
import com.nh.hoopscoach.game.Face
import com.nh.hoopscoach.ui.SpriteCompose
import com.nh.hoopscoach.ui.SpriteSheets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** Guards the customizer avatar against art drops that break the layer contract. */
@RunWith(RobolectricTestRunner::class)
class AvatarArtTest {
    private val layerDirs = listOf(
        "bodies", "eyes", "mouths", "beards", "nba_jerseys", "college_jerseys", "headbands", "eyewear", "hairstyles",
    )

    @Before
    fun install() {
        SpriteSheets.install(ApplicationProvider.getApplicationContext<android.content.Context>().assets)
    }

    private fun assetDir(): File =
        listOf(File("src/main/assets/character"), File("app/src/main/assets/character")).first { it.isDirectory }

    @Test
    fun everyLayerIsAPreAligned512Canvas() {
        val bad = mutableListOf<String>()
        for (dir in layerDirs) {
            val files = File(assetDir(), dir).listFiles { f -> f.name.endsWith(".png") }.orEmpty()
            assertTrue("no art in $dir", files.isNotEmpty())
            for (f in files) {
                val bmp = SpriteSheets.bitmap("character/$dir/${f.name}")
                if (bmp == null) bad += "$dir/${f.name} (unreadable)"
                else if (!SpriteCompose.isAligned(bmp)) bad += "$dir/${f.name} (${bmp.width}x${bmp.height})"
            }
        }
        assertEquals("layers must be 512x512 and drawn at (0,0): $bad", emptyList<String>(), bad)
    }

    @Test
    fun bodiesHaveAWholeHeadAndTorso() {
        for (skin in 0..7) {
            val body = SpriteSheets.body(skin)!!
            fun solid(x: Int, y: Int) = body.getPixel(x, y) ushr 24 >= 128
            // Cranium under the hair: no flat-cut scalp, no stray strands beside it.
            for ((x, y) in listOf(256 to 56, 210 to 70, 300 to 70, 200 to 100, 311 to 100)) {
                assertTrue("body $skin has no scalp at ($x,$y)", solid(x, y))
            }
            for (y in 0 until 116) for (x in 0 until 512) {
                if (x in 186..325) continue
                assertTrue("body $skin has a stray pixel at ($x,$y)", !solid(x, y))
            }
            // Torso between the arms, so the jersey's armholes never show the background.
            for (y in 290 until 500 step 5) {
                var x = 0
                while (x < 256 && !solid(x, y)) x++
                while (x < 256 && solid(x, y)) x++
                if (x >= 205) continue
                for (xx in x until 306) assertTrue("body $skin has a gap beside the arm at ($xx,$y)", solid(xx, y))
            }
        }
    }

    @Test
    fun nothingShowsThroughTheJerseyNumberArea() {
        // Same look and jersey on the lightest and darkest bodies: inside the number area only the
        // jersey may be visible, so the two renders must match there.
        for (jersey in listOf("timberwolves", "lakers", "celtics", "heat", "knicks", "01_duke")) {
            val a = SpriteCompose.render(Face(0, 0, 1, 0, 0, 0, 0, 0, 0), 0, 0, 3, jerseyKey = jersey)
            val b = SpriteCompose.render(Face(7, 0, 1, 0, 0, 0, 0, 0, 0), 0, 0, 3, jerseyKey = jersey)
            val n = SpriteCompose.N
            var diff = 0
            // Chest and strap digit cutouts in the jersey pack (256 scale), with a margin.
            for ((xr, yr) in listOf((116 until 142) to (173 until 218), (148 until 164) to (140 until 166))) {
                for (y in yr) for (x in xr) if (a[y * n + x] != b[y * n + x]) diff++
            }
            assertEquals("$jersey: body shows through the jersey at $diff pixels", 0, diff)
        }
    }

    @Test
    fun beardStaysOnTheChin() {
        // A beard layer must never put dark pixels beyond the face's width at mouth height.
        val n = SpriteCompose.N
        for (beard in 1..5) {
            val bare = SpriteCompose.render(Face(6, 0, 0, 0, 0, 0, 0, 0, 0), 0, 0, 3, jerseyKey = "timberwolves")
            val with = SpriteCompose.render(Face(6, 0, 0, 0, beard, 0, 0, 0, 0), 0, 0, 3, jerseyKey = "timberwolves")
            var outside = 0
            for (y in 100 until 160) for (x in 0 until n) {
                if (x in 100 until 156) continue
                if (bare[y * n + x] != with[y * n + x]) outside++
            }
            assertEquals("beard $beard draws outside the face", 0, outside)
        }
    }
}
