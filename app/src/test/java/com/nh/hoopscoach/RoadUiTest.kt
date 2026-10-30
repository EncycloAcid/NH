package com.nh.hoopscoach

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import com.nh.hoopscoach.game.Arch
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.LeagueGen
import com.nh.hoopscoach.game.NilDeal
import com.nh.hoopscoach.game.OffStep
import com.nh.hoopscoach.game.Offseason
import com.nh.hoopscoach.game.Phase
import com.nh.hoopscoach.game.Pos
import com.nh.hoopscoach.game.Road
import com.nh.hoopscoach.game.Rng
import com.nh.hoopscoach.game.Season
import com.nh.hoopscoach.ui.App
import com.nh.hoopscoach.ui.GameViewModel
import com.nh.hoopscoach.ui.Guide
import com.nh.hoopscoach.ui.HistoryKind
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
import kotlin.random.Random

/** Road to the League through the real screens: create, offers, seasons, decisions and the ending. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h880dp-xhdpi")
class RoadUiTest {
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
    private fun swipe() { rule.onAllNodes(hasScrollToIndexAction()).onFirst().performTouchInput { swipeUp() }; idle() }

    /** Every tab and the screens a player can reach, so nothing in player mode crashes. */
    private fun walk(vm: GameViewModel, lg: League) {
        vm.install(lg); idle()
        for (t in Tab.entries) { vm.select(t); idle(); if (t == Tab.TEAM) for (i in 0..1) { vm.teamTab = i; idle() }; if (t == Tab.LEAGUE) for (i in 0..5) { vm.leagueTab = i; idle() } }
        if (lg.phase == Phase.WRAP_UP) { vm.select(Tab.TODAY); for (i in 0..3) { vm.wrapPage = i; idle() } }
        val routes = mutableListOf<Route>(Route.TeamPage(lg.userTeamId), Route.Schedule(lg.userTeamId), Route.Settings, Route.Leaders, Route.Preview)
        HistoryKind.entries.forEach { routes += Route.History(it) }
        lg.playerOrNull(lg.heroId)?.let { routes += Route.Focus(it.id); routes += Route.EditPlayer(it.id) }
        lg.games.lastOrNull { it.played && it.involves(lg.userTeamId) }?.let { routes += Route.Box(it.id) }
        for (r in routes) { vm.push(r); idle(); vm.back(); idle() }
        vm.openGuide(Guide.PROGRAM, 0); idle(); vm.guide = null; idle()
        lg.hero?.let { vm.openPlayer(it.id); idle(); vm.sheet = null; idle() }
        vm.select(Tab.TODAY); idle()
    }

    @Test
    fun career() {
        GameViewModel.autoLoad = false
        Rng.r = Random(9)
        val draft = LeagueGen.newLeague()
        val vm = GameViewModel(ApplicationProvider.getApplicationContext())
        vm.installDraft(draft)
        rule.setContent { App(vm) }
        shot("Y00_new_game")
        rule.onNodeWithText("Play as a player").performClick(); idle()
        shot("Y01_create")
        scrollTo(1); shot("Y02_create_more")

        val hero = Road.createHero(draft, Road.Spec("Marcus", "Hayes", Pos.PG, 74, 185, "Lexington", "KY", Arch.FLOOR_GENERAL, 5, 1))
        vm.roadHero = hero
        vm.roadOffers = Road.offers(draft, hero)
        idle(); shot("Y03_offers"); swipe(); shot("Y04_offers_more")

        val lg = Road.start(draft, hero, vm.roadOffers.minBy { it.ahead }.teamId)
        vm.roadHero = null
        vm.install(lg); idle()
        shot("Y05_today_start")
        vm.select(Tab.RECRUIT); shot("Y06_me_deal")
        vm.act { Road.chooseDeal(it, NilDeal.PERFORMANCE) }; idle()
        shot("Y07_me_signed"); scrollTo(3); shot("Y08_me_depth")
        vm.select(Tab.TEAM); vm.teamTab = 0; shot("Y09_team_roster")
        walk(vm, lg)
        repeat(10) { Season.playDay(lg) }
        vm.install(lg); vm.select(Tab.TODAY); shot("Y10_today_midseason")
        vm.startLive(); idle()
        vm.live?.let { lv -> lv.speed = 0; repeat(80) { lv.engine.step() }; lv.tick++ }
        shot("Y11_live"); vm.back(); idle()

        var seasons = 0
        while (!lg.road!!.ended && seasons < 5) {
            seasons++
            if (Road.needsDeal(lg)) Road.chooseDeal(lg, NilDeal.STEADY)
            var guard = 0
            while (lg.phase != Phase.WRAP_UP && guard++ < 120) Season.playDay(lg)
            if (seasons == 1) { vm.install(lg); vm.select(Tab.TODAY); vm.wrapPage = 2; shot("Y12_wrap_you"); walk(vm, lg) }
            Offseason.begin(lg)
            val me = lg.playerOrNull(lg.heroId)!!
            val rank = Offseason.draftBoard(lg)[me.id]
            lg.road!!.declared = me.year < 4 && rank != null && rank <= 20
            if (seasons == 1) lg.road!!.portal = true
            vm.install(lg); vm.select(Tab.TODAY)
            if (seasons == 1) shot("Y13_decision")
            Road.next(lg); vm.install(lg)
            if (lg.road!!.ended) break
            if (seasons == 1) shot("Y14_draft_back")
            Road.next(lg); vm.install(lg)
            if (seasons == 1) shot("Y15_summer_portal")
            assertTrue(lg.offseason!!.step == OffStep.DEVELOPMENT)
            Road.next(lg); vm.install(lg)
            if (seasons == 1) { shot("Y16_today_year2"); walk(vm, lg) }
        }
        assertTrue(lg.road!!.ended)
        vm.install(lg); vm.select(Tab.TODAY); shot("Y17_ending"); scrollTo(2); shot("Y18_ending_story")
        vm.select(Tab.CAREER); shot("Y19_career")
        vm.select(Tab.RECRUIT); shot("Y20_me_after")
        walk(vm, lg)
        println("ending: ${lg.road!!.ending}")
    }
}
