package com.nh.hoopscoach

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import android.graphics.Canvas
import androidx.test.core.app.ApplicationProvider
import com.nh.hoopscoach.game.LeagueGen
import com.nh.hoopscoach.game.Offseason
import com.nh.hoopscoach.game.Phase
import com.nh.hoopscoach.game.Rng
import com.nh.hoopscoach.game.Season
import com.nh.hoopscoach.ui.App
import com.nh.hoopscoach.ui.GameViewModel
import com.nh.hoopscoach.ui.HoopsTheme
import com.nh.hoopscoach.ui.PlayerSheetContent
import com.nh.hoopscoach.ui.Route
import com.nh.hoopscoach.ui.Tab
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h880dp-xhdpi")
class ScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val out = File(System.getProperty("screenshots.dir") ?: "build/screenshots").apply { mkdirs() }

    private fun shot(name: String) {
        rule.waitForIdle()
        val view = rule.activity.window.decorView.rootView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        FileOutputStream(File(out, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun screens() {
        GameViewModel.autoLoad = false
        Rng.r = Random(11)
        val lg = Offseason.newGame(3, "Jordan", "Reed")
        repeat(6) { Season.playDay(lg) }
        val vm = GameViewModel(ApplicationProvider.getApplicationContext())
        vm.install(lg)
        var mode by mutableIntStateOf(0)
        rule.setContent {
            when (mode) {
                0 -> App(vm)
                1 -> HoopsTheme { PlayerSheetContent(vm, lg, lg.rosterOf(lg.user).maxBy { it.ovr }) }
                else -> HoopsTheme { com.nh.hoopscoach.ui.NewGameScreen(vm) }
            }
        }
        shot("01_home")
        vm.select(Tab.PROGRAM)
        for (i in 0..4) { vm.programTab = i; shot("02_program_$i") }
        vm.select(Tab.LEAGUE)
        vm.leagueScope = 1; vm.leagueTab = 0; shot("03_league_standings")
        vm.leagueScope = 0; vm.leagueTab = 0; shot("03_league_rankings")
        vm.leagueTab = 1; shot("03_league_stats")
        vm.leagueTab = 2; shot("03_league_coaches")
        vm.select(Tab.DYNASTY); shot("04_dynasty")
        vm.select(Tab.HOME)
        vm.push(Route.TeamPage(lg.user.id)); shot("05_team")
        vm.back()
        val g = lg.games.first { it.played && it.involves(lg.userTeamId) }
        vm.push(Route.Box(g.id)); shot("06_box")
        vm.back()
        vm.startLive()
        val lv = vm.live!!
        lv.speed = 0
        repeat(80) { lv.engine.step() }
        lv.tick++
        shot("07_live")
        vm.back()
        rule.waitForIdle()
        mode = 1; shot("08_sheet"); mode = 0
        vm.push(Route.Focus(lg.user.roster.first())); shot("09_focus"); vm.back()
        val rec = lg.recruits.first()
        vm.push(Route.RecruitView(rec.p.id)); shot("10_recruit"); vm.back()

        // conference tournament
        while (lg.phase == Phase.REGULAR) Season.playDay(lg)
        vm.install(lg)
        vm.select(Tab.HOME); shot("11_conf_tourney")
        while (lg.phase == Phase.CONF_TOURNEY) Season.playDay(lg)
        Season.playDay(lg)
        vm.install(lg); shot("12_ncaa")
        vm.push(Route.BracketView(-1)); shot("13_bracket"); vm.back()
        while (lg.phase == Phase.NCAA) Season.playDay(lg)
        vm.install(lg)
        for (i in 0..4) { vm.wrapPage = i; shot("14_wrap_$i") }
        Offseason.begin(lg)
        vm.install(lg)
        shot("15_off_roster")
        repeat(5) { i ->
            Offseason.advance(lg)
            vm.install(lg)
            shot("15_off_step${i + 2}")
        }
        Offseason.autoComplete(lg)
        vm.install(lg)
        shot("16_new_season_home")
        vm.push(Route.Settings); shot("17_settings"); vm.back()
        vm.push(Route.EditTeam(lg.userTeamId)); shot("18_edit_team"); vm.back()
        vm.push(Route.History(com.nh.hoopscoach.ui.HistoryKind.SEASONS)); shot("19_history"); vm.back()
        vm.installDraft(LeagueGen.newLeague())
        mode = 2; shot("20_new_game"); mode = 0
        @Suppress("UNUSED_VARIABLE") val unused = LeagueGen
    }
}
