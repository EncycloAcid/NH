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
        }
    }

    @Test
    fun armsJoinEveryJerseyAtTheArmholes() {
        // From the shoulder down to the armpit, everything between the arm and the jersey's
        // armhole must be body (shoulder/chest skin), never background.
        val jerseys = listOf("nba_jerseys", "college_jerseys").flatMap { dir ->
            File(assetDir(), dir).listFiles { f -> f.name.endsWith(".png") }.orEmpty().map { "character/$dir/${it.name}" }
        }
        for (skin in 0..7) {
            val body = SpriteSheets.body(skin)!!
            for (path in jerseys) {
                val jersey = SpriteSheets.bitmap(path)!!
                fun body(x: Int, y: Int) = body.getPixel(x, y) ushr 24 >= 128
                fun shirt(x: Int, y: Int) = jersey.getPixel(x, y) ushr 24 >= 128
                for (y in 266 until 334 step 2) {
                    // The arm's inner edge is at x=163 / 348 on these rows; from there to the
                    // jersey's armhole must be solid.
                    for (x in 162 until 256) {
                        if (shirt(x, y)) break
                        assertTrue("body $skin + $path: background at ($x,$y)", body(x, y))
                    }
                    for (x in 349 downTo 256) {
                        if (shirt(x, y)) break
                        assertTrue("body $skin + $path: background at ($x,$y)", body(x, y))
                    }
                }
            }
        }
    }

    @Test
    fun everyHairstyleCoversTheScalp() {
        // Probe the crown, the temples and the sideburns: hair must sit on the head there, with no
        // scalp gap or float. Mohawk sides are shaved stubble, which is translucent but present.
        val probes = listOf(256 to 46, 230 to 50, 282 to 50, 206 to 76, 305 to 76, 196 to 104, 315 to 104)
        for (style in 1 until SpriteSheets.HAIR_FILES.size) {
            val hair = SpriteSheets.hair(style) ?: continue
            for ((x, y) in probes) {
                val a = hair.getPixel(x, y) ushr 24
                assertTrue("hair style $style leaves the scalp bare at ($x,$y)", a >= 60)
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
