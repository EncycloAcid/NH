package com.nh.hoopscoach

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.Phase
import com.nh.hoopscoach.game.ProCareer
import com.nh.hoopscoach.game.ProLeague
import com.nh.hoopscoach.game.ProStep
import com.nh.hoopscoach.game.Road
import com.nh.hoopscoach.game.Season
import com.nh.hoopscoach.game.Sim
import com.nh.hoopscoach.game.Summer
import com.nh.hoopscoach.ui.App
import com.nh.hoopscoach.ui.GameViewModel
import com.nh.hoopscoach.ui.Route
import com.nh.hoopscoach.ui.Tab
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/** The pro career through the real screens, from Draft Night to retirement. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h880dp-xhdpi")
class ProUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val out = File(System.getProperty("screenshots.dir") ?: "build/screenshots").apply { mkdirs() }

    private fun idle() = rule.waitForIdle()
    private fun shot(name: String) {
        idle()
        val view = rule.activity.window.decorView.rootView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        FileOutputStream(File(out, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun scrollTo(i: Int) { rule.onAllNodes(hasScrollToIndexAction()).onFirst().performScrollToIndex(i); idle() }
    private fun scrollToText(t: String) { rule.onAllNodes(hasScrollToIndexAction()).onFirst().performScrollToNode(hasText(t)); idle() }

    /** Every tab, sub-tab, route and the player sheet, so nothing crashes on pro data. */
    private fun walk(vm: GameViewModel, lg: League) {
        vm.install(lg); idle()
        for (t in Tab.entries) {
            vm.select(t); idle()
            if (t == Tab.TEAM) for (i in 0..1) { vm.teamTab = i; idle() }
            if (t == Tab.LEAGUE) for (i in 0..3) { vm.leagueTab = i; idle() }
        }
        val routes = mutableListOf<Route>(Route.Settings)
        lg.playerOrNull(lg.heroId)?.let { routes += Route.Focus(it.id) }
        lg.games.lastOrNull { it.played && it.involves(lg.userTeamId) }?.let { routes += Route.Box(it.id) }
        for (r in routes) { vm.push(r); idle(); vm.back(); idle() }
        lg.hero?.let { vm.openPlayer(it.id); idle(); vm.sheet = null; idle() }
        lg.user.roster.firstOrNull { it != lg.heroId }?.let { vm.openPlayer(it); idle(); vm.sheet = null; idle() }
        vm.leagueTab = 0; vm.teamTab = 0
        vm.select(Tab.TODAY); idle()
    }

    @Test
    fun proCareer() {
        GameViewModel.autoLoad = false
        val college = ProTest().toDraft(21)
        val vm = GameViewModel(ApplicationProvider.getApplicationContext())
        rule.setContent { App(vm) }
        // Draft Night with pro teams on the board, then the hand-off.
        vm.install(college); vm.select(Tab.TODAY); idle()
        vm.revealSeason = college.season; vm.reveal = 60; idle()
        shot("P00_green_room_pro")
        Road.watchedDraft(college); vm.install(college); idle()
        shot("P01_going_pro")

        var lg = ProLeague.create(college)
        vm.install(lg); vm.select(Tab.TODAY); idle()
        shot("P02_rookie_contract"); scrollTo(1); shot("P02b_new_team")
        walk(vm, lg)
        ProCareer.next(lg); vm.install(lg)
        shot("P03_rookie_summer")
        Summer.invites(lg).firstOrNull()?.let { Summer.attend(lg, it) }
        ProCareer.next(lg); vm.install(lg)
        assertTrue(lg.phase == Phase.REGULAR)
        shot("P04_opening_night")
        repeat(8) { Sim.playDay(lg); lg.road!!.press?.let { Road.answer(lg, 0) } }
        vm.install(lg); vm.select(Tab.TODAY)
        shot("P05_today")
        scrollTo(3); shot("P05b_today_more")
        vm.select(Tab.RECRUIT); shot("P06_me"); scrollTo(2); shot("P06b_me_races"); scrollToText("YOUR RIVAL"); shot("P06c_me_rival")
        vm.select(Tab.TEAM); vm.teamTab = 0; shot("P07_roster"); vm.teamTab = 1; shot("P07b_schedule")
        vm.select(Tab.LEAGUE); vm.leagueTab = 0; shot("P08_standings"); vm.leagueTab = 2; shot("P08b_leaders"); vm.leagueTab = 0
        // Trades: how he feels, asking out, the teams that call, and the move.
        vm.select(Tab.RECRUIT); idle(); scrollToText("YOUR SITUATION"); shot("P08c_situation")
        assertTrue(com.nh.hoopscoach.game.Trades.request(lg) > 0)
        vm.install(lg); vm.select(Tab.RECRUIT); idle(); scrollToText("TEAMS THAT WANT YOU"); shot("P08d_suitors")
        vm.select(Tab.TODAY); idle(); shot("P08e_today_request")
        com.nh.hoopscoach.game.Trades.accept(lg, 0); vm.install(lg); vm.select(Tab.TODAY); shot("P08f_traded")
        walk(vm, lg)
        vm.select(Tab.TODAY); idle()
        if (Season.userGameToday(lg) == null) Sim.playDay(lg)
        vm.install(lg); idle()
        vm.startLive(); idle()
        vm.live?.let { lv -> lv.speed = 0; repeat(6) { lv.engine.stepToHero() }; lv.tick++ }
        shot("P09_live_spotlight"); vm.live?.done = true; vm.back(); idle()
        vm.live = null
        while (lg.day < ProLeague.allStarDay(lg) && lg.phase == Phase.REGULAR) { Sim.playDay(lg); lg.road!!.press?.let { Road.answer(lg, 0) } }
        vm.install(lg); vm.select(Tab.TODAY); shot("P10_all_star")
        var guard = 0
        while (lg.phase == Phase.REGULAR && guard++ < 80) { Sim.playDay(lg); lg.road!!.press?.let { Road.answer(lg, 0) } }
        repeat(3) { Sim.playDay(lg) }
        vm.install(lg); vm.select(Tab.TODAY); shot("P11_playoffs")
        vm.select(Tab.LEAGUE); vm.leagueTab = 1; shot("P11b_bracket"); vm.leagueTab = 3; shot("P11c_awards"); vm.leagueTab = 0
        walk(vm, lg)
        guard = 0
        while (lg.phase != Phase.OFFSEASON && guard++ < 80) { Sim.playDay(lg); lg.road!!.press?.let { Road.answer(lg, 0) } }
        vm.install(lg); vm.select(Tab.TODAY); shot("P12_review")
        walk(vm, lg)
        ProCareer.next(lg); vm.install(lg); shot("P13_draft")
        if (lg.proState!!.freeStars.isNotEmpty()) { scrollToText("BUILD A CONTENDER"); shot("P13b_contender") }
        lg.road!!.contract!!.years = 0
        ProCareer.next(lg); vm.install(lg); shot("P14_free_agency")
        assertTrue("offers come in", lg.road!!.proOffers.isNotEmpty())
        // Negotiate: open the first offer, counter it, then sign.
        rule.onAllNodes(androidx.compose.ui.test.hasText(lg.team(lg.road!!.proOffers.first().teamId).name, substring = true)).onFirst().performClick(); idle()
        shot("P14b_negotiate")
        ProCareer.counter(lg, 0, 0.1); vm.install(lg); idle()
        shot("P14c_countered")
        ProCareer.signWith(lg, 0, 2, option = true, discount = false)
        ProCareer.next(lg); vm.install(lg); shot("P15_summer")
        vm.select(Tab.CAREER); shot("P16_career"); scrollTo(3); shot("P16b_career_story")
        // The all-time list, with a big season added so he's on it.
        lg.hero!!.awards += listOf("${lg.season} MVP", "${lg.season} Champion", "${lg.season} Finals MVP", "${lg.season} All-League 1st", "${lg.season} All-Star")
        lg.road!!.allTimeRank = 0
        com.nh.hoopscoach.game.AllTime.check(lg)
        vm.install(lg); vm.select(Tab.RECRUIT); idle(); scrollToText("ALL-TIME LIST"); shot("P16c_me_all_time")
        vm.select(Tab.CAREER); idle(); scrollToText("ALL-TIME LIST"); shot("P16d_all_time_list")
        lg.road!!.reel += com.nh.hoopscoach.game.ReelEntry(lg.season, "Conference Finals · Game 7", "41 pts, 9 reb, 6 ast · W 108-104 vs Boston Celtics · Game 7 · Game-winner", true)
        val lines = arrayOf(LinkedHashMap<Int, com.nh.hoopscoach.game.StatLine>(), LinkedHashMap())
        lines[0][lg.heroId] = com.nh.hoopscoach.game.StatLine(gp = 1, sec = 2800, pts = 58, tpm = 15)
        com.nh.hoopscoach.game.RecordBook.afterGame(lg, lines)
        vm.install(lg); vm.select(Tab.CAREER); idle(); scrollToText("PLAYOFF HIGHLIGHTS"); shot("P16e_reel")
        scrollToText("RECORDS BOOK · SINGLE GAME"); shot("P16f_records")
        // Skip ahead to the end of a career.
        ProCareer.next(lg)
        lg.hero!!.age = 34
        guard = 0
        while (lg.phase != Phase.OFFSEASON && guard++ < 120) Sim.playDay(lg)
        vm.install(lg); vm.select(Tab.TODAY); idle()
        assertTrue(lg.proState!!.step == ProStep.REVIEW && ProCareer.canRetire(lg))
        scrollToText("Retire"); shot("P17_retire_option")
        lg.road!!.mvps = 2; lg.road!!.rings = 2; lg.road!!.allStars = 7
        ProCareer.retire(lg); vm.install(lg); vm.select(Tab.TODAY)
        shot("P18_ending"); scrollToText("HALL OF FAME NIGHT"); shot("P18b_hall_of_fame")
        scrollToText("WHAT'S NEXT: COACHING"); shot("P18c_coaching")
        walk(vm, lg)
        println("ending: ${lg.road!!.ending}")
    }
}
