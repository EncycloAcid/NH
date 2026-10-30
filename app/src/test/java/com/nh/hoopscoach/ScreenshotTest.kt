package com.nh.hoopscoach

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.core.app.ApplicationProvider
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.LeagueGen
import com.nh.hoopscoach.game.Offseason
import com.nh.hoopscoach.game.Phase
import com.nh.hoopscoach.game.Rng
import com.nh.hoopscoach.game.Schedule
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

    private fun scrollTo(index: Int) {
        rule.onAllNodes(hasScrollToIndexAction()).onFirst().performScrollToIndex(index)
        rule.waitForIdle()
    }

    /** First Four, NIT and Bubble Watch, seen from the teams that play in them. */
    @Test
    fun postseason() {
        val lg = league("Kentucky")
        val vm = GameViewModel(ApplicationProvider.getApplicationContext())
        vm.install(lg)
        rule.setContent { App(vm) }
        fun viewAs(id: Int) { lg.userTeamId = id; lg.userCoachId = lg.team(id).coachId; vm.install(lg) }

        while (lg.phase == Phase.REGULAR && lg.day < Schedule.REG_DAYS - 2) Season.playDay(lg)
        vm.install(lg)
        scrollTo(3); shot("P01_today_outlook")
        while (lg.phase == Phase.REGULAR) Season.playDay(lg)
        Season.playDay(lg)
        vm.install(lg); shot("P02_ct_today")
        scrollTo(3); shot("P03_ct_outlook")
        vm.select(Tab.LEAGUE); vm.leagueTab = 3
        scrollTo(0); shot("P04_bubble_top")
        scrollTo(2); shot("P05_bubble_locks")
        scrollTo(4); shot("P06_bubble_cut")
        scrollTo(6); shot("P07_bubble_out")
        vm.leagueTab = 4; scrollTo(0); shot("P08_bracketology")

        while (lg.phase == Phase.CONF_TOURNEY) Season.playDay(lg)
        vm.install(lg); vm.leagueTab = 3; scrollTo(0); shot("P09_selection_sunday")
        scrollTo(4); shot("P10_selection_cut")
        vm.leagueTab = 4; scrollTo(0); shot("P11_postseason")
        val pi = lg.ncaa!!.playIns.first()
        viewAs(pi.a); vm.select(Tab.TODAY); shot("P12_first_four_today")
        vm.push(Route.BracketView(-1)); shot("P13_region_first_four"); vm.back()
        val nitTeam = lg.nit!!.rounds[0][0]
        viewAs(nitTeam); vm.select(Tab.TODAY); shot("P14_nit_today")
        vm.push(Route.BracketView(Season.NIT_ID)); shot("P15_nit_bracket"); vm.back()
        while (lg.nit?.done == false) Season.playDay(lg)
        viewAs(lg.nit!!.champion); vm.select(Tab.TODAY); shot("P16_nit_champions")
        while (lg.phase == Phase.NCAA) Season.playDay(lg)
        vm.install(lg); vm.wrapPage = 0; shot("P17_wrap_nit")
        scrollTo(1); shot("P18_wrap_nit_rewards")
        vm.wrapPage = 3; scrollTo(0); shot("P19_wrap_program")
        scrollTo(1); shot("P20_wrap_signals")
        vm.wrapPage = 4; scrollTo(0); shot("P21_wrap_coach")
    }

    /** Duke-North Carolina: preview, live crowd, the newspaper banner and the box score. */
    @Test
    fun rivalry() = rivalryShots("R")

    @Test
    fun rivalryDark() {
        RuntimeEnvironment.setQualifiers("+night")
        rivalryShots("RD", full = false)
    }

    private fun rivalryShots(tag: String, full: Boolean = true) {
        val lg = league("Duke")
        val vm = GameViewModel(ApplicationProvider.getApplicationContext())
        val duke = lg.userTeamId
        val g = lg.games.filter { it.involves(duke) && lg.rivalry(it) != null }.minBy { it.day }
        while (lg.day < g.day) Season.playDay(lg)
        vm.install(lg)
        rule.setContent { App(vm) }
        if (full) {
            shot("${tag}01_next_rivalry")
            vm.startLive()
            val lv = vm.live!!
            lv.speed = 0
            repeat(120) { lv.engine.step() }
            lv.tick++
            shot("${tag}02_live")
            vm.back()
        } else {
            Season.playDay(lg)
        }
        rule.waitForIdle()
        vm.install(lg)
        scrollTo(0); shot("${tag}03_banner")
        vm.push(Route.Box(g.id)); shot("${tag}04_box"); vm.back()
        if (!full) return
        scrollTo(6); shot("${tag}05_headlines")
        vm.select(Tab.TEAM); vm.teamTab = 3; scrollTo(1); shot("${tag}06_program_rivals")
        vm.push(Route.Schedule(duke)); scrollTo(2); shot("${tag}07_schedule"); vm.back()
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
        for (i in 0..5) { vm.leagueTab = i; shot("${tag}04_league_$i") }
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
