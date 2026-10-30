package com.nh.hoopscoach

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.LeagueGen
import com.nh.hoopscoach.game.Offseason
import com.nh.hoopscoach.game.Phase
import com.nh.hoopscoach.game.Rng
import com.nh.hoopscoach.game.Season
import com.nh.hoopscoach.ui.App
import com.nh.hoopscoach.ui.GameViewModel
import com.nh.hoopscoach.ui.HistoryKind
import com.nh.hoopscoach.ui.HoopsTheme
import com.nh.hoopscoach.ui.NewGameScreen
import com.nh.hoopscoach.ui.PlayerCard
import com.nh.hoopscoach.ui.Route
import com.nh.hoopscoach.ui.Tab
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
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

    private fun league(team: String): League {
        GameViewModel.autoLoad = false
        Rng.r = Random(11)
        val base = LeagueGen.newLeague()
        val lg = Offseason.claimTeam(base, base.teams.first { it.school == team }.id, "Jordan", "Reed")
        repeat(6) { Season.playDay(lg) }
        return lg
    }

    @Test
    fun light() = run("Kentucky", "L")

    @Test
    fun dark() {
        RuntimeEnvironment.setQualifiers("+night")
        run("Duke", "D", full = false)
    }

    private fun run(team: String, tag: String, full: Boolean = true) {
        val lg = league(team)
        val vm = GameViewModel(ApplicationProvider.getApplicationContext())
        vm.install(lg)
        var mode by mutableIntStateOf(0)
        rule.setContent {
            when (mode) {
                0 -> App(vm)
                1 -> HoopsTheme(androidx.compose.ui.graphics.Color(lg.user.primary), androidx.compose.ui.graphics.Color(lg.user.secondary)) {
                    PlayerCard(vm, lg, lg.rosterOf(lg.user).maxBy { it.ovr })
                }
                else -> HoopsTheme { NewGameScreen(vm) }
            }
        }
        shot("${tag}01_today")
        vm.select(Tab.TEAM)
        for (i in 0..3) { vm.teamTab = i; shot("${tag}02_team_$i") }
        vm.select(Tab.RECRUIT); shot("${tag}03_recruit")
        vm.select(Tab.LEAGUE)
        for (i in 0..4) { vm.leagueTab = i; shot("${tag}04_league_$i") }
        vm.select(Tab.CAREER); shot("${tag}05_career")
        vm.select(Tab.TODAY)
        vm.startLive()
        val lv = vm.live!!
        lv.speed = 0
        repeat(90) { lv.engine.step() }
        lv.tick++
        shot("${tag}06_live")
        vm.back()
        rule.waitForIdle()
        mode = 1; shot("${tag}07_player"); mode = 0
        if (!full) return
        val g = lg.games.first { it.played && it.involves(lg.userTeamId) }
        vm.push(Route.Box(g.id)); shot("${tag}08_box"); vm.back()
        vm.push(Route.TeamPage(lg.teams.first { it.school == "Duke" }.id)); shot("${tag}09_teampage"); vm.back()
        vm.push(Route.RecruitView(lg.recruits.first().p.id)); shot("${tag}10_recruit_detail"); vm.back()
        vm.push(Route.Focus(lg.user.roster.first())); shot("${tag}11_focus"); vm.back()

        while (lg.phase == Phase.REGULAR) Season.playDay(lg)
        vm.install(lg); shot("${tag}12_conf_tourney")
        while (lg.phase == Phase.CONF_TOURNEY) Season.playDay(lg)
        Season.playDay(lg)
        vm.install(lg); shot("${tag}13_ncaa")
        vm.push(Route.BracketView(-1)); shot("${tag}14_bracket"); vm.back()
        while (lg.phase == Phase.NCAA) Season.playDay(lg)
        vm.install(lg)
        for (i in 0..4) { vm.wrapPage = i; shot("${tag}15_wrap_$i") }
        Offseason.begin(lg)
        vm.install(lg); shot("${tag}16_off_1")
        repeat(5) { i -> Offseason.advance(lg); vm.install(lg); shot("${tag}16_off_${i + 2}") }
        Offseason.autoComplete(lg)
        vm.install(lg); shot("${tag}17_next_season")
        vm.push(Route.Settings); shot("${tag}18_settings"); vm.back()
        vm.push(Route.History(HistoryKind.SEASONS)); shot("${tag}19_history"); vm.back()
        vm.installDraft(LeagueGen.newLeague())
        mode = 2; shot("${tag}20_new_game"); mode = 0
    }
}
