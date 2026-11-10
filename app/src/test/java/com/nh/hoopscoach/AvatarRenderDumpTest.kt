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
 * Renders customizer avatars for a fixed set of skin / hair / beard / face / gear / team combos
 * to PPM so art or compositor changes can be compared before/after.
 * Only writes when the `AVATAR_DUMP_DIR` env var is set:
 *   AVATAR_DUMP_DIR=/tmp/avatars ./gradlew :app:testDebugUnitTest --tests com.nh.hoopscoach.AvatarRenderDumpTest
 */
@RunWith(RobolectricTestRunner::class)
class AvatarRenderDumpTest {
    @Test
    fun dump() {
        val dir = System.getenv("AVATAR_DUMP_DIR") ?: return
        SpriteSheets.install(ApplicationProvider.getApplicationContext<android.content.Context>().assets)
        val out = File(dir).apply { mkdirs() }
        // Face(skin, head, hair, hairColor, beard, eyes, brows, mouth, acc)
        val cases = listOf(
            Case("01_wolves3_darkchoc_buzz", Face(6, 0, 1, 0, 0, 0, 0, 0, 0), "timberwolves", 3),
            Case("02_wolves3_darkchoc_buzz_goatee", Face(6, 0, 1, 0, 3, 0, 0, 0, 0), "timberwolves", 3),
            Case("03_wolves3_darkchoc_fade_mustache_intense", Face(6, 0, 3, 0, 2, 1, 0, 1, 0), "timberwolves", 3),
            Case("04_lakers23_ebony_afro_fullbeard", Face(7, 0, 5, 0, 5, 2, 0, 0, 0), "lakers", 23),
            Case("05_celtics11_pale_cornrows_chinstrap_mask", Face(0, 0, 8, 2, 4, 3, 0, 0, 5), "celtics", 11),
            Case("06_heat7_caramel_locs_stubble_whiteHB", Face(3, 0, 9, 1, 1, 4, 0, 2, 1), "heat", 7),
            Case("07_warriors30_peach_mohawk_goggles", Face(1, 0, 11, 4, 0, 5, 0, 3, 4), "warriors", 30),
            Case("08_duke1_chestnut_twists_goatee_redHB", Face(5, 0, 10, 0, 3, 6, 0, 1, 2), "01_duke", 1, Gear(undershirt = 1, sleeve = 1, sleeveColor = 2)),
            Case("09_bulls0_olive_topbun", Face(2, 0, 13, 4, 0, 1, 0, 0, 0), "bulls", 0),
            Case("10_blank44_medium_headbandstyle", Face(4, 0, 7, 0, 0, 0, 0, 0, 0), null, 44),
            Case("11_knicks8_ebony_bald_fullbeard", Face(7, 0, 0, 0, 5, 0, 0, 0, 3), "knicks", 8),
            Case("12_wolves3_darkchoc_afro", Face(6, 0, 5, 0, 0, 0, 0, 0, 0), "timberwolves", 3),
        )
        for (c in cases) {
            val px = SpriteCompose.render(c.face, 0xFF0C2340.toInt(), 0xFF78BE20.toInt(), c.number, c.gear, c.jersey)
            writePpm(File(out, "${c.name}.ppm"), px, SpriteCompose.N, SpriteCompose.N)
        }
    }

    private class Case(val name: String, val face: Face, val jersey: String?, val number: Int, val gear: Gear = Gear())

    private fun writePpm(file: File, px: IntArray, w: Int, h: Int) {
        // Flatten over the customizer's background (0xFF23201D) so holes read like on device.
        val bg = 0xFF23201D.toInt()
        val rgb = ByteArray(w * h * 3)
        var o = 0
        for (c in px) {
            val a = c ushr 24
            for (s in intArrayOf(16, 8, 0)) {
                rgb[o++] = ((((c shr s) and 0xFF) * a + ((bg shr s) and 0xFF) * (255 - a)) / 255).toByte()
            }
        }
        file.outputStream().use { it.write("P6\n$w $h\n255\n".toByteArray()); it.write(rgb) }
    }
}
