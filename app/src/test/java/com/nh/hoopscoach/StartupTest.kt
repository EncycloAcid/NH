package com.nh.hoopscoach

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.nh.hoopscoach.game.Offseason
import com.nh.hoopscoach.game.ProLeague
import com.nh.hoopscoach.game.ProSeason
import com.nh.hoopscoach.game.Rng
import com.nh.hoopscoach.game.SaveIO
import com.nh.hoopscoach.game.SaveManager
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.ui.GameViewModel
import com.nh.hoopscoach.ui.Route
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.io.File
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34, 35], qualifiers = "w400dp-h880dp-xhdpi")
class StartupTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val save get() = File(ApplicationProvider.getApplicationContext<android.content.Context>().filesDir, "dynasty.sav")

    private fun clearTestSaves() {
        val slots = File(save.parentFile, "saves")
        check(!slots.exists() || slots.deleteRecursively())
        listOf(save, File(save.path + ".bak")).forEach { it.delete() }
    }

    private fun saveFixture(lg: League) {
        val bytes = SaveIO.encode(lg)
        org.junit.Assert.assertEquals(lg.user.id, SaveIO.decode(bytes).user.id)
        SaveManager.saveSlot(save.parentFile!!, "startup-test", bytes, lg)
        SaveManager.setActiveSlotId(save.parentFile!!, "startup-test")
    }

    private fun assertLoaded(vm: GameViewModel, expected: League) {
        rule.runOnIdle {
            org.junit.Assert.assertNotNull("No career loaded: loading=${vm.loading}, draft=${vm.draft != null}, active slot=${vm.activeSlotId}", vm.league)
            org.junit.Assert.assertEquals(expected.user.id, vm.league!!.user.id)
            org.junit.Assert.assertEquals(expected.pro, vm.league!!.pro)
        }
    }

    private fun activityViewModel(activity: MainActivity): GameViewModel =
        ViewModelProvider(activity)[GameViewModel::class.java].also {
            org.junit.Assert.assertSame(activity.application, it.getApplication<android.app.Application>())
        }

    @Before fun prepare() {
        // AndroidX's singleton otherwise retains the previous Robolectric Application.
        ReflectionHelpers.setStaticField(ViewModelProvider.AndroidViewModelFactory::class.java, "_instance", null)
        GameViewModel.autoLoad = true
        clearTestSaves()
    }

    @After fun clean() {
        clearTestSaves()
        GameViewModel.autoLoad = true
        ReflectionHelpers.setStaticField(ViewModelProvider.AndroidViewModelFactory::class.java, "_instance", null)
    }

    @Test fun freshInstallReachesNewCareer() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var vm: GameViewModel
            scenario.onActivity { vm = activityViewModel(it) }
            rule.waitUntil(60_000) {
                rule.runOnIdle { !vm.loading && vm.busy == null && vm.draft != null }
            }
            rule.onNodeWithText("HOOPS DYNASTY").assertIsDisplayed()
        }
    }

    @Test fun savedCareerReachesHomeWithAvatarAssets() {
        Rng.r = Random(41)
        val lg = Offseason.newGame(5, "Test", "Coach")
        lg.preview?.opened = true
        lg.retroAll = true
        for (p in lg.rosterOf(lg.user)) p.face = "0,0,7,0,0,6,0,1,6"
        saveFixture(lg)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var vm: GameViewModel
            scenario.onActivity { vm = activityViewModel(it) }
            rule.waitUntil(60_000) {
                rule.runOnIdle { !vm.loading }
            }
            rule.waitForIdle()
            assertLoaded(vm, lg)
        }
    }

    @Test fun savedNbaSeasonOpensPreviewWithAllAvatarChoices() {
        Rng.r = Random(41)
        val lg = ProLeague.coachLeague(2026, "LAL", "Test", "Coach")
        ProSeason.startSeason(lg)
        lg.retroAll = true
        lg.players.forEachIndexed { i, p ->
            p.face = "${i % 8},0,${i % 14},${i % 8},${i % 6},${i % 7},0,${i % 4},${i % 7}"
        }
        saveFixture(lg)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var vm: GameViewModel
            scenario.onActivity { vm = activityViewModel(it) }
            rule.waitUntil(60_000) {
                rule.runOnIdle { !vm.loading }
            }
            assertLoaded(vm, lg)
            rule.runOnIdle { org.junit.Assert.assertEquals(Route.NbaPreview, vm.stack.lastOrNull()) }
            rule.onNodeWithText("Next ›").assertIsDisplayed()
        }
    }
}
