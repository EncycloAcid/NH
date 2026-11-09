package com.nh.hoopscoach

import androidx.test.core.app.ApplicationProvider
import com.nh.hoopscoach.game.Face
import com.nh.hoopscoach.game.Gear
import com.nh.hoopscoach.ui.SpriteCompose
import com.nh.hoopscoach.ui.SpriteSheets
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Renders a grid of layered sprite portraits to PPM so we can eyeball the redesign without a device.
 * Run: ./gradlew :app:testDebugUnitTest --tests com.nh.hoopscoach.PortraitDumpTest
 * Output: build/portraits/
 */
@RunWith(RobolectricTestRunner::class)
class PortraitDumpTest {
    @Test
    fun dumpSheet() {
        SpriteSheets.install(ApplicationProvider.getApplicationContext<android.content.Context>().assets)
        val out = File(System.getProperty("portraits.dir") ?: "build/portraits").apply { mkdirs() }
        val teams = listOf(
            Triple("lakers", 0xFF552583.toInt(), 0xFFFDB927.toInt()),
            Triple("celtics", 0xFF007A33.toInt(), 0xFFBA9653.toInt()),
            Triple("heat", 0xFF98002E.toInt(), 0xFFF9A01B.toInt()),
            Triple("01_duke", 0xFF003087.toInt(), 0xFFFFFFFF.toInt()), // college pack
        )
        val faces = listOf(
            Face(5, 0, 3, 0, 5, 1, 1, 1, 0),   // chestnut, fade, full beard, intense, grin
            Face(2, 1, 2, 1, 0, 2, 0, 0, 4),   // olive, slicked, clutch, goggles
            Face(6, 2, 5, 0, 1, 3, 2, 2, 3),   // dark chocolate, afro, swagger, shout, black HB
            Face(1, 0, 10, 2, 3, 5, 0, 0, 0),  // fair peach, twists, blue eyes, goatee
            Face(4, 3, 4, 0, 0, 4, 1, 3, 2),   // medium brown, flat-top, alert, mouthguard, red HB
            Face(7, 1, 9, 0, 5, 1, 1, 1, 0),   // deep ebony, locs, full beard
            Face(3, 0, 8, 1, 2, 6, 0, 1, 0),   // caramel, cornrows, green eyes
            Face(0, 2, 11, 0, 0, 2, 2, 0, 5),  // pale ivory, mohawk, clear mask
            Face(5, 0, 6, 0, 4, 1, 0, 0, 1),   // high-top, white HB
            Face(2, 1, 13, 4, 0, 3, 0, 1, 0),  // blond top bun, swagger grin
            Face(6, 0, 12, 0, 5, 4, 1, 2, 0),  // waves, full beard, alert shout
            Face(4, 2, 7, 0, 1, 0, 0, 0, 0),   // headband style (curls + clean black band)
        )
        val gear = Gear(undershirt = 1, sleeve = 1, sleeveColor = 2)
        val n = SpriteCompose.N
        val cols = 4
        val rows = (faces.size + cols - 1) / cols
        val pad = 8
        val cell = n + pad
        val W = cols * cell + pad
        val H = rows * cell + pad
        val sheet = IntArray(W * H) { 0xFF2A2E36.toInt() }
        faces.forEachIndexed { i, face ->
            val (jersey, pri, sec) = teams[i % teams.size]
            val px = SpriteCompose.render(
                face, pri, sec,
                listOf(23, 0, 11, 34, 7, 15, 3, 8, 21, 30, 1, 44)[i],
                if (i % 3 == 0) gear else Gear(),
                jersey,
            )
            val cx = pad + (i % cols) * cell
            val cy = pad + (i / cols) * cell
            for (y in 0 until n) for (x in 0 until n) sheet[(cy + y) * W + cx + x] = px[y * n + x]
            writePpm(File(out, "portrait-${"%02d".format(i)}.ppm"), px, n, n)
        }
        writePpm(File(out, "sheet.ppm"), sheet, W, H)
        println("Wrote ${faces.size} portraits + sheet to ${out.absolutePath}")
    }

    private fun writePpm(file: File, px: IntArray, w: Int, h: Int) {
        val header = "P6\n$w $h\n255\n".toByteArray()
        val rgb = ByteArray(w * h * 3)
        var o = 0
        for (c in px) {
            rgb[o++] = ((c shr 16) and 0xFF).toByte()
            rgb[o++] = ((c shr 8) and 0xFF).toByte()
            rgb[o++] = (c and 0xFF).toByte()
        }
        file.outputStream().use {
            it.write(header)
            it.write(rgb)
        }
    }
}
