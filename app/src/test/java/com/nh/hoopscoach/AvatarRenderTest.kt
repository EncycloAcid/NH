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

    @Test fun everyHairstyleCoversTheSkullAndMeetsEveryHeadBorder() {
        val assets = ApplicationProvider.getApplicationContext<android.content.Context>().assets
        val paths = assets.list("character/hairstyles")!!.filter { it.endsWith(".png") }
        assertEquals(15, paths.size)
        for (skin in PlayerLook.skinTones.indices) {
            val body = SpriteSheets.body(skin)!!
            for (path in paths) {
                val hair = SpriteSheets.bitmap("character/hairstyles/$path")!!
                val composed = SpriteCompose.composite(listOf(body, hair))
                val pixels = IntArray(512 * 512)
                hair.getPixels(pixels, 0, 512, 0, 0, 512, 512)
                fun opaque(x: Int, y: Int) = pixels[y * 512 + x] ushr 24 == 255
                val seam = mutableSetOf<Pair<Int, Int>>()
                for (y in 84..132) {
                    val head = (185..326).filter { body.getPixel(it, y) ushr 24 != 0 }
                    for (x in head.first() - 8..head.first() + 1) seam.add(x to y)
                    for (x in head.last() - 1..head.last() + 8) seam.add(x to y)
                }
                for (y in 133..161) for (x in (185..193) + (318..326)) seam.add(x to y)
                for ((x, y) in seam) if (!opaque(x, y)) {
                    val closed = (-1..1).all { dy -> (-1..1).all { dx ->
                        (-1..1).any { yy -> (-1..1).any { xx -> opaque(x + dx + xx, y + dy + yy) } }
                    } }
                    assertFalse("$path skin $skin 1px hairline/ear sliver $x,$y", closed)
                }
                for (y in 133..136) for (x in listOf(192, 319)) {
                    assertEquals("$path skin $skin sideburn $x,$y", 255, hair.getPixel(x, y) ushr 24)
                    assertEquals(hair.getPixel(x, y), composed[y * 512 + x])
                }
                for (y in 84..132) {
                    val head = (185..326).filter { body.getPixel(it, y) ushr 24 != 0 }
                    for (x in listOf(head.first(), head.last())) {
                        assertEquals("$path skin $skin border $x,$y", 255, hair.getPixel(x, y) ushr 24)
                        assertEquals("$path skin $skin gap $x,$y", hair.getPixel(x, y), composed[y * 512 + x])
                    }
                }
                for (x in 205..306) {
                    for (y in 84..87) {
                        assertEquals("$path skin $skin skull $x,$y", 255, hair.getPixel(x, y) ushr 24)
                    }
                }
                val sideburn = (128..136).flatMap { y ->
                    (184..193).map { x -> x to y } + (318..327).map { x -> x to y }
                }.first { (x, y) ->
                    val pixel = hair.getPixel(x, y)
                    pixel ushr 24 == 255 && ((pixel ushr 16 and 255) + (pixel ushr 8 and 255) + (pixel and 255)) >= 40
                }
                for (color in 1..7) {
                    val tinted = SpriteCompose.composite(listOf(hair), color, setOf(hair))
                    val (x, y) = sideburn
                    assertNotEquals("$path sideburn color $color", hair.getPixel(x, y), tinted[y * 512 + x])
                }
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
        assertEquals(listOf("bodies", "college_jerseys", "numbers", "beards", "mouths", "eyes", "hairstyles", "headbands", "eyewear"),
            paths.map { it.split('/')[1] })
        assertTrue(paths[2].endsWith("/99.png"))
        assertNull(SpriteSheets.bitmap("character/not-present.png"))
        assertTrue(SpriteCompose.renderLook(look, null, -10).any { it != 0 })
    }

    @Test fun largeWordmarksHaveNumberClearanceInBothRenderers() {
        for ((key, offset) in mapOf("grizzlies_retro" to 48, "jazz" to 24, "supersonics" to 40, "wizards" to 16,
                "bulls" to 12, "hawks" to 16, "warriors" to 16, "23_ohio_state" to 12)) {
            for (n in listOf(0, 99)) {
                val number = SpriteSheets.chestNumber(n)!!
                val original = IntArray(512 * 512)
                number.getPixels(original, 0, 512, 0, 0, 512, 512)
                val positioned = SpriteCompose.positionNumber(number, SpriteSheets.jerseyPath(key))
                val legacy = SpriteCompose.render(Face(4, 0, 0, 0, 0, 0, 0, 0, 0), 0, 0, n, jerseyKey = key)
                val packed = SpriteCompose.renderLook(PlayerLook(), key, n)
                for (i in original.indices) if (original[i] ushr 24 == 255) {
                    val shifted = i + offset * 512
                    assertTrue(shifted < original.size)
                    assertEquals(original[i], positioned.getPixel(i % 512, i / 512 + offset))
                    assertEquals(original[i], legacy[shifted])
                    assertEquals(original[i], packed[shifted])
                }
                val after = IntArray(512 * 512)
                number.getPixels(after, 0, 512, 0, 0, 512, 512)
                assertArrayEquals(original, after)
            }
        }
    }

    @Test fun ordinaryAndEmptyNumbersStayUnchanged() {
        val number = SpriteSheets.chestNumber(23)!!
        assertSame(number, SpriteCompose.positionNumber(number, SpriteSheets.jerseyPath("lakers")))
        val clear = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        assertTrue(SpriteCompose.composite(listOf(SpriteCompose.positionNumber(clear,
            SpriteSheets.jerseyPath("grizzlies_retro")))).all { it == 0 })
    }

    @Test fun newPackWordmarksClearEveryCurrentNumber() {
        for (key in listOf("bulls", "hawks", "warriors", "23_ohio_state")) {
            val path = SpriteSheets.jerseyPath(key)
            val jersey = SpriteSheets.bitmap(path)!!
            val wordmark = mutableListOf<Pair<Int, Int>>()
            for (y in 330..394) for (x in 216..297) {
                val pixel = jersey.getPixel(x, y)
                val r = pixel ushr 16 and 255
                val g = pixel ushr 8 and 255
                val b = pixel and 255
                val ink = if (key == "hawks" || key == "warriors")
                    r > 130 && g > 90 && b < 120 && r * 10 > b * 14
                else minOf(r, g, b) > 145 && maxOf(r, g, b) - minOf(r, g, b) < 65
                if (ink) wordmark += x to y
            }
            assertTrue(wordmark.isNotEmpty())
            for (n in 0..99) {
                val number = SpriteCompose.positionNumber(SpriteSheets.chestNumber(n)!!, path)
                for ((x, y) in wordmark) assertEquals("$key #$n at $x,$y", 0, number.getPixel(x, y) ushr 24)
                number.recycle()
            }
        }
    }

    @Test fun allSuppliedJerseysResolveUsingTheirExistingTeamKeys() {
        val assets = ApplicationProvider.getApplicationContext<android.content.Context>().assets
        for ((folder, count) in mapOf("college_jerseys" to 48, "nba_jerseys" to 32)) {
            val files = assets.list("character/$folder")!!.filter { it.endsWith(".png") }
            assertEquals(count, files.size)
            for (file in files) {
                assertEquals("character/$folder/$file", SpriteSheets.jerseyPath(file.removeSuffix(".png")))
            }
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsCroppedNumberPlacement() {
        SpriteCompose.positionNumber(Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888),
            SpriteSheets.jerseyPath("wizards"))
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

    @Test fun brightHairKeepsItsTextureInEveryColor() {
        val hair = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        hair.setPixel(20, 20, 0xFFB4B4B4.toInt())
        hair.setPixel(21, 20, 0xFFE6E6E6.toInt())
        for (color in 0..7) {
            val result = SpriteCompose.composite(listOf(hair), color, setOf(hair))
            assertNotEquals("hair color $color", result[20 * 512 + 20], result[20 * 512 + 21])
            assertEquals(255, result[20 * 512 + 20] ushr 24)
            assertEquals(0, result[0])
        }
        val empty = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        assertTrue(SpriteCompose.composite(listOf(empty), 1, setOf(empty)).all { it == 0 })
    }

    @Test fun lipTintUsesEachBodiesOwnSkinWithoutPatchingTheFace() {
        for ((skin, name) in PlayerLook.skinTones.withIndex()) {
            val body = SpriteSheets.bitmap("character/bodies/$name.png")!!
            val original = IntArray(512 * 512)
            body.getPixels(original, 0, 512, 0, 0, 512, 512)
            val neutral = PlayerLook(skinToneIndex = skin)
            val neutralPixels = SpriteCompose.renderLook(neutral, null, 0)
            for (mouth in 1..3) {
                val look = PlayerLook(skinToneIndex = skin, mouthIndex = mouth)
                val paths = SpriteCompose.lookLayers(look, null, 0)
                val layers = paths.map { SpriteSheets.bitmap(it)!! }.toMutableList()
                layers[0] = SpriteCompose.softenCheekHighlights(body)
                val eyeAt = paths.indexOfFirst { it.startsWith("character/eyes/") }
                if (eyeAt >= 0) layers[eyeAt] = SpriteCompose.tintEyes(layers[0], layers[eyeAt])
                val mask = layers[3]
                layers[3] = SpriteCompose.tintMouth(body, mask)
                val rendered = SpriteCompose.renderLook(look, null, 0)
                assertArrayEquals("skin $skin mouth $mouth", SpriteCompose.composite(layers),
                    rendered)
                for (y in 185..225) for (x in 220..292) {
                    if (mask.getPixel(x, y) ushr 24 == 0) {
                        assertEquals("unpatched skin $skin at $x,$y", neutralPixels[y * 512 + x], rendered[y * 512 + x])
                    }
                }
                val marker = mask.getPixel(240, 194)
                val shade = (marker ushr 16) and 255
                assertEquals(shade, marker and 0xFFFF)
                val left = body.getPixel(226, 194)
                val right = body.getPixel(286, 194)
                for (shift in listOf(16, 8, 0)) {
                    val skinColor = (((left ushr shift) and 255) * 46 + ((right ushr shift) and 255) * 14) / 60
                    assertEquals(skinColor * shade / (if (shift == 16) 192 else 224),
                        (layers[3].getPixel(240, 194) ushr shift) and 255)
                }
            }
            val after = IntArray(512 * 512)
            body.getPixels(after, 0, 512, 0, 0, 512, 512)
            assertArrayEquals(original, after)
            val faceBody = SpriteCompose.softenCheekHighlights(body)
            assertArrayEquals(SpriteCompose.composite(SpriteCompose.lookLayers(neutral, null, 0)
                .map { path ->
                    val bitmap = SpriteSheets.bitmap(path)!!
                    when {
                        path.startsWith("character/bodies/") -> faceBody
                        path.startsWith("character/eyes/") -> SpriteCompose.tintEyes(faceBody, bitmap)
                        else -> bitmap
                    }
                }), SpriteCompose.renderLook(neutral, null, 0))
        }
    }

    @Test fun lipTintPreservesTransparentPixels() {
        val clear = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        assertTrue(SpriteCompose.composite(listOf(SpriteCompose.tintMouth(clear, clear))).all { it == 0 })
    }

    @Test fun bandAliasesMatchAndHairOccludesTheBandWithoutMutatingIt() {
        val assets = ApplicationProvider.getApplicationContext<android.content.Context>().assets
        for ((canonical, alias) in listOf("01_classic_white" to "01_white_headband",
                "02_bulls_red" to "02_red_headband", "03_stealth_black" to "03_black_headband")) {
            assertArrayEquals(assets.open("character/headbands/$canonical.png").readBytes(),
                assets.open("character/headbands/$alias.png").readBytes())
        }
        assertArrayEquals(assets.open("character/beards/02_trimmed_goatee.png").readBytes(),
            assets.open("character/beards/02_goatee.png").readBytes())
        val band = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        band.setPixel(200, 100, 0xFFFFFFFF.toInt())
        val hair = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        assertEquals(band.getPixel(200, 100), SpriteCompose.occludeHeadband(band, hair).getPixel(200, 100))
        hair.setPixel(200, 100, 0xFF201A18.toInt())
        assertEquals(0, SpriteCompose.occludeHeadband(band, hair).getPixel(200, 100))
        assertEquals(0xFFFFFFFF.toInt(), band.getPixel(200, 100))
        hair.setPixel(200, 100, 0xFFAE1C24.toInt())
        assertEquals(band.getPixel(200, 100), SpriteCompose.occludeHeadband(band, hair).getPixel(200, 100))
        assertSame(band, SpriteCompose.occludeHeadband(band, null))
        assertTrue(SpriteCompose.composite(listOf(SpriteCompose.occludeHeadband(
            Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888), hair))).all { it == 0 })
    }

    @Test(expected = IllegalArgumentException::class)
    fun headbandOcclusionRejectsCroppedLayers() {
        SpriteCompose.occludeHeadband(Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888), null)
    }

    @Test fun suppliedBandsNeverFloatOutsideAnyBodyForEveryHairstyle() {
        val assets = ApplicationProvider.getApplicationContext<android.content.Context>().assets
        for (bodyName in PlayerLook.skinTones) {
            val body = SpriteSheets.bitmap("character/bodies/$bodyName.png")!!
            for (hairName in PlayerLook.hairstyles) {
                val hair = SpriteSheets.bitmap("character/hairstyles/$hairName.png")!!
                for (bandName in assets.list("character/headbands")!!.filter { it.endsWith(".png") }) {
                    val band = SpriteSheets.bitmap("character/headbands/$bandName")!!
                    val result = SpriteCompose.occludeHeadband(band, hair, body)
                    for (y in 88..113) for (x in 174..339) {
                        if (body.getPixel(x, y) ushr 24 == 0) assertEquals(0, result.getPixel(x, y))
                    }
                    result.recycle()
                }
            }
        }
        val clear = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        assertTrue(SpriteCompose.composite(listOf(SpriteCompose.occludeHeadband(clear, null, clear))).all { it == 0 })
    }

    @Test fun selectedBandsHideEmbeddedRedSliversWithoutChangingHairArt() {
        val hair = SpriteSheets.bitmap("character/hairstyles/headband_with_curls.png")!!
        val band = SpriteSheets.bitmap("character/headbands/01_classic_white.png")!!
        val prepared = SpriteCompose.occludeEmbeddedBand(hair, band)
        var removed = 0
        val colors = setOf(0xFFAE1C24.toInt(), 0xFFCE3137.toInt(), 0xFF71121A.toInt(), 0xFF301217.toInt())
        for (y in 0..511) for (x in 0..511) {
            val pixel = hair.getPixel(x, y)
            if (pixel in colors) {
                removed++
                assertTrue(prepared.getPixel(x, y) !in colors)
            } else assertEquals(pixel, prepared.getPixel(x, y))
        }
        assertTrue(removed > 0)
        assertSame(hair, SpriteCompose.occludeEmbeddedBand(hair, null))
        val clear = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        assertTrue(SpriteCompose.composite(listOf(SpriteCompose.occludeEmbeddedBand(clear, band))).all { it == 0 })
    }

    @Test(expected = IllegalArgumentException::class)
    fun embeddedBandOcclusionRejectsCroppedHair() {
        SpriteCompose.occludeEmbeddedBand(Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888), null)
    }

    @Test(expected = IllegalArgumentException::class)
    fun bandBodyOcclusionRejectsCroppedBodies() {
        SpriteCompose.occludeHeadband(Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888), null,
            Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888))
    }

    @Test fun suppliedMasksLeaveBothPupilCentersOpenOnEverySkinAndEyeStyle() {
        val assets = ApplicationProvider.getApplicationContext<android.content.Context>().assets
        for (name in listOf("02_protective_clear_mask", "03_carbon_black_mask")) {
            val mask = SpriteSheets.bitmap("character/eyewear/$name.png")!!
            for (x in listOf(224, 287)) assertEquals(0, mask.getPixel(x, 143) ushr 24)
            for (skin in 0..7) for (eye in assets.list("character/eyes")!!.filter { it.endsWith(".png") }) {
                val body = SpriteCompose.softenCheekHighlights(SpriteSheets.body(skin)!!)
                val eyes = SpriteCompose.tintEyes(body, SpriteSheets.bitmap("character/eyes/$eye")!!)
                val bare = SpriteCompose.composite(listOf(body, eyes))
                val guarded = SpriteCompose.composite(listOf(body, eyes, mask))
                for (x in listOf(224, 287)) {
                    assertEquals("$name skin $skin eyes $eye", bare[143 * 512 + x], guarded[143 * 512 + x])
                }
            }
        }
    }

    @Test fun carbonMaskAppendsSlotsWithoutChangingExistingSavedChoices() {
        for (index in 0..3) {
            val look = PlayerLook(eyewearIndex = index)
            assertEquals(look, PlayerLook.fromInt(look.toInt()))
        }
        assertEquals("03_carbon_black_mask", PlayerLook.eyewear[3])
        assertTrue(SpriteCompose.lookLayers(PlayerLook(eyewearIndex = 3), null, 0)
            .contains("character/eyewear/03_carbon_black_mask.png"))
        assertEquals("Carbon mask", Face.OPTIONS[8][6])
        val face = Face(0, 0, 0, 0, 0, 0, 0, 0, 6)
        assertEquals(6, Face.decode(face.encode())!!.acc)
        assertSame(SpriteSheets.bitmap("character/eyewear/03_carbon_black_mask.png"), SpriteSheets.eyewear(6))
    }

    @Test fun everyBeardLeavesAllRedrawnMouthsClear() {
        val assets = ApplicationProvider.getApplicationContext<android.content.Context>().assets
        val beards = assets.list("character/beards")!!.filter { it.endsWith(".png") }
        assertEquals(4, beards.size)
        assertEquals(6, assets.list("character/headbands")!!.count { it.endsWith(".png") })
        for (beardPath in beards) for (mouthIndex in 1..3) {
            val beard = SpriteSheets.bitmap("character/beards/$beardPath")!!
            val mouth = SpriteSheets.mouth(mouthIndex)!!
            for (y in 193..218) for (x in 229..282) if (mouth.getPixel(x, y) ushr 24 != 0) {
                assertEquals("$beardPath mouth $mouthIndex $x,$y", 0, beard.getPixel(x, y) ushr 24)
            }
        }
        for (skin in 0..7) for (beard in 1..3) for (mouthIndex in 1..3) {
            val look = PlayerLook(skinToneIndex = skin, beardIndex = beard, mouthIndex = mouthIndex)
            val result = SpriteCompose.renderLook(look, null, 0)
            val body = SpriteSheets.bitmap("character/bodies/${PlayerLook.skinTones[skin]}.png")!!
            val tinted = SpriteCompose.tintMouth(body, SpriteSheets.mouth(mouthIndex)!!)
            assertEquals(tinted.getPixel(229, 201), result[201 * 512 + 229])
        }
        val full = SpriteSheets.bitmap("character/beards/01_full_beard.png")!!
        for (y in 133..170) assertEquals(255, full.getPixel(194, y) ushr 24)
    }

    @Test fun legacyFaceAlsoReplacesBakedMouth() {
        val face = Face(4, 0, 0, 0, 0, 0, 0, 1, 0)
        val layers = listOfNotNull(
            SpriteCompose.softenCheekHighlights(SpriteSheets.body(face.skin)!!),
            SpriteSheets.bitmap(SpriteSheets.jerseyPath(null)),
            SpriteSheets.chestNumber(0),
            SpriteCompose.tintMouth(SpriteSheets.body(face.skin)!!, SpriteSheets.mouth(face.mouth)!!),
        )
        assertNotNull(SpriteSheets.mouth(face.mouth))
        assertArrayEquals(SpriteCompose.composite(layers), SpriteCompose.render(face, 0, 0, 0))
    }

    @Test fun redrawnEyesStayInsideNativeEyeAndBrowRegions() {
        val assets = ApplicationProvider.getApplicationContext<android.content.Context>().assets
        val names = assets.list("character/eyes")!!.filter { it.endsWith(".png") }
        assertEquals(6, names.size)
        for (name in names) {
            val eyes = SpriteSheets.bitmap("character/eyes/$name")!!
            val pixels = IntArray(512 * 512)
            eyes.getPixels(pixels, 0, 512, 0, 0, 512, 512)
            for (i in pixels.indices) if (pixels[i] ushr 24 != 0) {
                val x = i % 512
                val y = i / 512
                assertTrue("$name stray pixel $x,$y", y in 115..154 && (x in 198..248 || x in 263..313))
            }
            for ((skin, bodyName) in PlayerLook.skinTones.withIndex()) {
                val body = SpriteSheets.bitmap("character/bodies/$bodyName.png")!!
                val prepared = SpriteCompose.softenCheekHighlights(body)
                val tinted = SpriteCompose.tintEyes(prepared, eyes)
                val index = PlayerLook.eyes.indexOf(name.removeSuffix(".png"))
                val result = SpriteCompose.renderLook(PlayerLook(skinToneIndex = skin, eyeIndex = index), null, 0)
                val legacyStyle = names.sorted().indexOf(name) + 1
                val legacy = SpriteCompose.render(Face(skin, 0, 0, 0, 0, legacyStyle, 0, 0, 0), 0, 0, 0)
                val legacyEyes = SpriteCompose.tintEyes(SpriteCompose.softenCheekHighlights(SpriteSheets.body(skin)!!), eyes)
                for (y in 115..154) for (x in (198..248) + (263..313)) {
                    val pixel = tinted.getPixel(x, y)
                    if (pixel ushr 24 == 255) {
                        assertEquals("packed $name skin $skin at $x,$y", pixel, result[y * 512 + x])
                        assertEquals("legacy $name skin $skin at $x,$y", legacyEyes.getPixel(x, y), legacy[y * 512 + x])
                    }
                }
            }
        }
    }

    @Test fun cheekSofteningOnlyChangesThe150PaintedHighlightPixelsWithoutMutatingBody() {
        for (skin in 0..7) {
            val body = SpriteSheets.body(skin)!!
            val before = IntArray(512 * 512)
            body.getPixels(before, 0, 512, 0, 0, 512, 512)
            val softened = SpriteCompose.softenCheekHighlights(body)
            var changed = 0
            for (i in before.indices) if (before[i] != softened.getPixel(i % 512, i / 512)) {
                val x = i % 512
                val y = i / 512
                assertTrue(y in 155..165 && (x in 201..223 || x in 288..310))
                assertEquals(body.getPixel(212, 160), before[i])
                changed++
            }
            assertEquals(150, changed)
            val after = IntArray(512 * 512)
            body.getPixels(after, 0, 512, 0, 0, 512, 512)
            assertArrayEquals(before, after)
        }
        val clear = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        assertTrue(SpriteCompose.composite(listOf(SpriteCompose.tintEyes(clear, clear))).all { it == 0 })
        assertTrue(SpriteCompose.composite(listOf(SpriteCompose.softenCheekHighlights(clear))).all { it == 0 })
    }

    @Test(expected = IllegalArgumentException::class)
    fun eyeSkinSamplingRejectsCroppedLayers() {
        SpriteCompose.tintEyes(Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888),
            Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888))
    }

    @Test(expected = IllegalArgumentException::class)
    fun cheekSofteningRejectsCroppedBodies() {
        SpriteCompose.softenCheekHighlights(Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888))
    }

    @Test(expected = IllegalArgumentException::class)
    fun mouthReplacementRejectsCroppedBody() {
        SpriteCompose.tintMouth(Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888),
            Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888))
    }
}
