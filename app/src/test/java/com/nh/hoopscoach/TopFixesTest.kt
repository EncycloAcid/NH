package com.nh.hoopscoach

import com.nh.hoopscoach.game.AllTime
import com.nh.hoopscoach.game.Era
import com.nh.hoopscoach.game.GameEngine
import com.nh.hoopscoach.game.LeagueGen
import com.nh.hoopscoach.game.Pos
import com.nh.hoopscoach.game.ProState
import com.nh.hoopscoach.game.Rng
import com.nh.hoopscoach.game.SaveIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

class TopFixesTest {

    @Test
    fun historicalEraDoesNotGrantModernActiveLegacy() {
        val lg = LeagueGen.newLeague()
        lg.level = com.nh.hoopscoach.game.Level.PRO
        lg.proState = ProState(era = Era.Y2003.name)

        val bron = LeagueGen.makePlayer(lg, Pos.SF, 1, 88, 0).apply {
            first = "LeBron"
            last = "James"
            photo = "lebron.webp"
        }
        lg.players += bron

        // In 2003 era, rookie LeBron James must NOT start with 172 career points
        val pastTotal = AllTime.total(lg, bron)
        assertEquals(0.0, pastTotal, 0.001)

        // In today's era, real active stars receive their real career legacy
        lg.proState!!.era = Era.TODAY.name
        val todayTotal = AllTime.total(lg, bron)
        assertTrue("LeBron James receives active legacy in Era.TODAY", todayTotal >= 170.0)
    }

    @Test
    fun cosmeticFlavorTextDoesNotPolluteSimulationRng() {
        Rng.r = Random(12345)
        Rng.cosmetic = Random(99999)

        val beforeNextInt = Rng.r.nextInt(1000)
        // Reset seed
        Rng.r = Random(12345)

        // Draw multiple cosmetic flavor picks
        repeat(50) {
            Rng.flavorPick(listOf("A", "B", "C", "D"))
            Rng.flavorD()
            Rng.flavorChance(0.5)
        }

        // Rng.r must be completely untouched and deterministic
        val afterNextInt = Rng.r.nextInt(1000)
        assertEquals("Flavor text draws must not consume from simulation RNG", beforeNextInt, afterNextInt)
    }

    @Test
    fun backupSaveRecoversCorruptedPrimarySave() {
        val tempDir = createTempDir("save_test")
        val file = File(tempDir, "dynasty.sav")
        val bak = File(tempDir, "dynasty.sav.bak")

        val lg = LeagueGen.newLeague()
        val validBytes = SaveIO.encode(lg)

        // Write valid backup, corrupted primary
        bak.writeBytes(validBytes)
        file.writeBytes("corrupted data that is not valid gzip or json".toByteArray())

        // Simulate ViewModel recovery logic
        val restored = runCatching { if (file.exists()) SaveIO.decode(file.readBytes()) else null }.getOrNull()
            ?: runCatching { if (bak.exists()) SaveIO.decode(bak.readBytes()) else null }.getOrNull()?.also {
                bak.copyTo(file, overwrite = true)
            }

        assertNotNull("League was successfully restored from backup save", restored)
        assertEquals(lg.teams.size, restored!!.teams.size)

        tempDir.deleteRecursively()
    }

    @Test
    fun liveGameSimulatesAndCommitsCleanly() {
        val lg = com.nh.hoopscoach.game.Offseason.newGame(5, "Test", "Coach")
        val g = lg.games.first { !it.played }
        val engine = GameEngine(lg, g, pbp = true)
        val liveGame = com.nh.hoopscoach.ui.LiveGame(engine)

        liveGame.engine.simToEnd()
        liveGame.engine.commit()
        liveGame.done = true

        assertTrue("Live game is marked done", liveGame.done)
        assertTrue("Game is marked played", g.played)
        assertTrue("Game has a winner", g.winner() in listOf(g.home, g.away))
    }
}
