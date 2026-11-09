package com.nh.hoopscoach

import com.nh.hoopscoach.game.Face
import com.nh.hoopscoach.game.Gear
import com.nh.hoopscoach.ui.CelRender
import org.junit.Test
import java.io.File

/**
 * Renders a grid of retro bit portraits to PPM so we can eyeball the redesign without a device.
 * Run: ./gradlew :app:testDebugUnitTest --tests com.nh.hoopscoach.PortraitDumpTest
 * Output: build/portraits/
 */
class PortraitDumpTest {
    @Test
    fun dumpSheet() {
        val out = File(System.getProperty("portraits.dir") ?: "build/portraits").apply { mkdirs() }
        val teams = listOf(
            0xFF13294B.toInt() to 0xFFE84A27.toInt(), // Illinois
            0xFF0033A0.toInt() to 0xFFFFFFFF.toInt(), // Kentucky-ish
            0xFFCE1141.toInt() to 0xFF000000.toInt(), // Rockets-ish
            0xFF1D428A.toInt() to 0xFFFFC72C.toInt(), // Warriors-ish
        )
        val faces = listOf(
            Face(5, 0, 3, 0, 5, 0, 1, 0, 0),   // brown, fade, full beard
            Face(2, 1, 2, 1, 0, 1, 0, 1, 0),   // light, short, smile
            Face(6, 2, 5, 0, 1, 0, 2, 0, 4),   // deep, afro, stubble, earring
            Face(1, 0, 10, 2, 3, 2, 0, 0, 0),  // fair, long hair, goatee
            Face(4, 3, 4, 0, 0, 0, 1, 2, 2),   // bronze, flat-top, smirk, team headband
            Face(7, 1, 9, 0, 5, 1, 1, 0, 0),   // ebony, locs, beard
            Face(3, 0, 8, 1, 2, 0, 0, 1, 0),   // tan, cornrows, mustache
            Face(0, 2, 11, 0, 0, 2, 2, 0, 5),  // porcelain, mohawk, glasses
            Face(5, 0, 6, 0, 4, 0, 0, 0, 0),   // high-top, chin strap
            Face(2, 1, 13, 4, 0, 1, 0, 1, 1),  // blond man bun, white headband
            Face(6, 0, 12, 0, 5, 0, 1, 0, 0),  // waves, full beard
            Face(4, 2, 1, 0, 0, 0, 0, 0, 3),   // buzz, goggles
        )
        val gear = Gear(undershirt = 1, sleeve = 1, sleeveColor = 2)
        val n = CelRender.N
        val cols = 4
        val rows = (faces.size + cols - 1) / cols
        val pad = 8
        val cell = n + pad
        val W = cols * cell + pad
        val H = rows * cell + pad
        val sheet = IntArray(W * H) { 0xFF2A2E36.toInt() }
        faces.forEachIndexed { i, face ->
            val (pri, sec) = teams[i % teams.size]
            val px = CelRender.render(face, pri, sec, listOf(23, 0, 11, 34, 7, 15, 3, 8, 21, 30, 1, 44)[i], if (i % 3 == 0) gear else Gear())
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
