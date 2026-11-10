package com.nh.hoopscoach

import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.nh.hoopscoach.game.Face
import com.nh.hoopscoach.ui.PlayerLook
import com.nh.hoopscoach.ui.SpriteCompose
import com.nh.hoopscoach.ui.SpriteSheets
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AvatarRenderTest {
    @Before fun install() {
        SpriteSheets.install(ApplicationProvider.getApplicationContext<android.content.Context>().assets)
    }

    @Test fun savedLooksDecodeToValidChoices() {
        for (encoded in listOf(0, -1, Int.MIN_VALUE, Int.MAX_VALUE, 123456789)) {
            val look = PlayerLook.fromInt(encoded)
            assertTrue(look.skinToneIndex in PlayerLook.skinTones.indices)
            assertTrue(look.hairIndex in PlayerLook.hairstyles.indices)
            assertTrue(look.beardIndex in PlayerLook.beards.indices)
            assertTrue(look.eyeIndex in PlayerLook.eyes.indices)
            assertTrue(look.mouthIndex in PlayerLook.mouths.indices)
            assertTrue(look.headbandIndex in PlayerLook.headbands.indices)
            assertTrue(look.eyewearIndex in PlayerLook.eyewear.indices)
            assertEquals(if (look.toInt() == 0) PlayerLook() else look, PlayerLook.fromInt(look.toInt()))
            for (path in SpriteCompose.lookLayers(look, "lakers", 23)) {
                val layer = SpriteSheets.bitmap(path)
                assertNotNull(path, layer)
                assertEquals(path, 512, layer!!.width)
                assertEquals(path, 512, layer.height)
            }
        }
    }

    @Test fun numberIsFullCanvasAndCompositedWithoutTranslation() {
        val number = SpriteSheets.chestNumber(23)!!
        assertEquals(512, number.width)
        assertEquals(512, number.height)
        val px = IntArray(512 * 512)
        number.getPixels(px, 0, 512, 0, 0, 512, 512)
        val composed = SpriteCompose.render(Face(4, 0, 0, 0, 0, 0, 0, 0, 0), 0, 0, 23, jerseyKey = "lakers")
        var checked = 0
        for (i in px.indices) if (px[i] ushr 24 == 255) {
            assertEquals("number pixel $i", px[i], composed[i])
            checked++
        }
        assertTrue(checked > 100)
    }

    @Test fun orderAndFallback() {
        val look = PlayerLook(hairIndex = 1, beardIndex = 1, eyewearIndex = 1, headbandIndex = 1, mouthIndex = 1, eyeIndex = 1)
        val paths = SpriteCompose.lookLayers(look, "not-a-team", 1000)
        assertEquals(listOf("bodies", "college_jerseys", "numbers", "mouths", "eyes", "beards", "hairstyles", "headbands", "eyewear"),
            paths.map { it.split('/')[1] })
        assertTrue(paths[2].endsWith("/99.png"))
        assertNull(SpriteSheets.bitmap("character/not-present.png"))
        assertTrue(SpriteCompose.renderLook(look, null, -10).any { it != 0 })
    }

    @Test fun transparentLayersAndSourceOver() {
        assertTrue(SpriteCompose.composite(emptyList()).all { it == 0 })
        val red = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        red.eraseColor(0xFFFF0000.toInt())
        val clear = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        assertEquals(0xFFFF0000.toInt(), SpriteCompose.composite(listOf(red, clear))[0])
        clear.eraseColor(0x800000FF.toInt())
        val c = SpriteCompose.composite(listOf(red, clear))[0]
        assertEquals(255, c ushr 24)
        assertEquals(127, (c ushr 16) and 255)
        assertEquals(128, c and 255)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsLegacyCroppedLayers() {
        SpriteCompose.composite(listOf(Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)))
    }

    @Test fun hairColorPreservesAlphaAndOtherLayers() {
        val hair = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        hair.setPixel(12, 20, 0xFF806040.toInt())
        val base = SpriteCompose.composite(listOf(hair))
        val colored = SpriteCompose.composite(listOf(hair), 7, setOf(hair))
        assertNotEquals(base[20 * 512 + 12], colored[20 * 512 + 12])
        assertEquals(255, colored[20 * 512 + 12] ushr 24)
        assertEquals(0, colored[0])
        assertArrayEquals(base, SpriteCompose.composite(listOf(hair), 7))
    }

    @Test fun mouthReplacementUsesEachBodiesOwnSkinWithoutChangingCachedBody() {
        for ((skin, name) in PlayerLook.skinTones.withIndex()) {
            val body = SpriteSheets.bitmap("character/bodies/$name.png")!!
            val original = IntArray(512 * 512)
            body.getPixels(original, 0, 512, 0, 0, 512, 512)
            val base = SpriteCompose.mouthBase(body)
            val after = IntArray(512 * 512)
            body.getPixels(after, 0, 512, 0, 0, 512, 512)
            assertArrayEquals(original, after)
            assertEquals(body.getPixel(220, 200), base.getPixel(220, 200))
            assertNotEquals(body.getPixel(256, 200), base.getPixel(256, 200))
            val left = body.getPixel(226, 200)
            val right = body.getPixel(286, 200)
            for (shift in listOf(16, 8, 0)) {
                assertEquals((((left ushr shift) and 255) + ((right ushr shift) and 255)) / 2,
                    (base.getPixel(256, 200) ushr shift) and 255)
            }
            for (mouth in 1..3) {
                val look = PlayerLook(skinToneIndex = skin, mouthIndex = mouth)
                val paths = SpriteCompose.lookLayers(look, null, 0)
                val layers = paths.map { SpriteSheets.bitmap(it)!! }.toMutableList()
                layers[0] = base
                assertArrayEquals("skin $skin mouth $mouth", SpriteCompose.composite(layers),
                    SpriteCompose.renderLook(look, null, 0))
            }
            val neutral = PlayerLook(skinToneIndex = skin)
            assertArrayEquals(SpriteCompose.composite(SpriteCompose.lookLayers(neutral, null, 0)
                .map { SpriteSheets.bitmap(it)!! }), SpriteCompose.renderLook(neutral, null, 0))
        }
    }

    @Test fun mouthReplacementPreservesTransparentPixels() {
        val clear = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        assertTrue(SpriteCompose.composite(listOf(SpriteCompose.mouthBase(clear))).all { it == 0 })
    }

    @Test fun legacyFaceAlsoReplacesBakedMouth() {
        val face = Face(4, 0, 0, 0, 0, 0, 0, 1, 0)
        val layers = listOfNotNull(
            SpriteCompose.mouthBase(SpriteSheets.body(face.skin)!!),
            SpriteSheets.bitmap(SpriteSheets.jerseyPath(null)),
            SpriteSheets.chestNumber(0),
            SpriteSheets.mouth(face.mouth),
        )
        assertNotNull(SpriteSheets.mouth(face.mouth))
        assertArrayEquals(SpriteCompose.composite(layers), SpriteCompose.render(face, 0, 0, 0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun mouthReplacementRejectsCroppedBody() {
        SpriteCompose.mouthBase(Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888))
    }
}
