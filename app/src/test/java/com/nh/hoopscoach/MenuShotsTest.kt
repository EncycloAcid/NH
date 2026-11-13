package com.nh.hoopscoach

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.LeagueGen
import com.nh.hoopscoach.game.Offseason
import com.nh.hoopscoach.game.Phase
import com.nh.hoopscoach.game.ProCareer
import com.nh.hoopscoach.game.ProLeague
import com.nh.hoopscoach.game.Rng
import com.nh.hoopscoach.game.Season
import com.nh.hoopscoach.game.Sim
import com.nh.hoopscoach.ui.App
import com.nh.hoopscoach.ui.GameViewModel
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

/** The drop-down menus, open: the sim menus on Today and the pickers. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h880dp-xhdpi")
class MenuShotsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val out = File(System.getProperty("screenshots.dir") ?: "build/screenshots").apply { mkdirs() }
    private fun idle() = rule.waitForIdle()

    /** The screen with any open menu drawn on top where it sits. */
    private fun shot(name: String) {
        idle()
        val view = rule.activity.window.decorView.rootView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        view.draw(canvas)
        val loc = IntArray(2).also { view.getLocationOnScreen(it) }
        // Popups are their own windows: draw each one where the window manager put it.
        val wmg = Class.forName("android.view.WindowManagerGlobal").getMethod("getInstance").invoke(null)
        @Suppress("UNCHECKED_CAST")
        val views = wmg.javaClass.getDeclaredField("mViews").apply { isAccessible = true }.get(wmg) as List<android.view.View>
        for (v in views) {
            if (v === view || v.width == 0) continue
            val lp = v.layoutParams as? android.view.WindowManager.LayoutParams ?: continue
            canvas.save()
            canvas.translate((lp.x - loc[0]).toFloat(), (lp.y - loc[1]).toFloat())
            v.draw(canvas)
            canvas.restore()
        }
        FileOutputStream(File(out, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun openSimMenu() { rule.onAllNodes(hasContentDescription("More sim options")).onFirst().performClick(); idle() }
    /** Back closes an open menu: send it to each popup window. */
    private fun close(@Suppress("UNUSED_PARAMETER") vm: GameViewModel) {
        val root = rule.activity.window.decorView.rootView
        val wmg = Class.forName("android.view.WindowManagerGlobal").getMethod("getInstance").invoke(null)
        @Suppress("UNCHECKED_CAST")
        val views = (wmg.javaClass.getDeclaredField("mViews").apply { isAccessible = true }.get(wmg) as List<android.view.View>).toList()
        rule.runOnUiThread {
            for (v in views) if (v !== root) {
                v.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_BACK))
                v.dispatchKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_BACK))
            }
        }
        idle()
    }

    /** The social feed and a game story. */
    @Test
    fun media() {
        GameViewModel.autoLoad = false
        Rng.r = Random(6)
        val lg = ProLeague.create(ProTest().toDraft(21))
        while (lg.phase == Phase.OFFSEASON) ProCareer.next(lg)
        var days = 0
        while ((days < 30 || lg.news.none { it.kind == "article" }) && days++ < 80) { Sim.playDay(lg); lg.road!!.press?.let { com.nh.hoopscoach.game.Road.answer(lg, 1) } }
        Sim.playDay(lg)
        val vm = testViewModel()
        vm.install(lg)
        rule.setContent { App(vm) }
        vm.select(Tab.TODAY); idle()
        rule.onAllNodes(androidx.compose.ui.test.hasScrollToIndexAction()).onFirst().performScrollToNode(hasText("See all")); idle()
        shot("Z10_today_social")
        vm.push(com.nh.hoopscoach.ui.Route.Social); idle(); shot("Z11_social_feed"); vm.back(); idle()
        val story = lg.news.indexOfLast { it.kind == "article" }
        assertTrue("a game story", story >= 0)
        vm.push(com.nh.hoopscoach.ui.Route.Article(story)); idle(); shot("Z12_article"); vm.back(); idle()
        val column = lg.news.indexOfLast { it.kind == "column" }
        if (column >= 0) { vm.push(com.nh.hoopscoach.ui.Route.Article(column)); idle(); shot("Z13_column") }
    }

    /** A 1996 league: the era's stars, old club names and monogram badges. */
    @Test
    fun eraLeague() {
        GameViewModel.autoLoad = false
        Rng.r = Random(5)
        val lg = ProLeague.create(ProTest().toDraft(21), com.nh.hoopscoach.game.Era.Y1996)
        while (lg.phase == Phase.OFFSEASON) ProCareer.next(lg)
        repeat(12) { Sim.playDay(lg) }
        val vm = testViewModel()
        vm.install(lg)
        rule.setContent { App(vm) }
        vm.select(Tab.LEAGUE); vm.leagueTab = 3; idle(); shot("Z05_era_leaders")
        vm.leagueTab = 0; idle(); shot("Z06_era_standings")
        vm.leagueTab = 5; idle(); shot("Z07_era_history")
        vm.select(Tab.TODAY); idle(); shot("Z08_era_today")
    }

    @Test
    fun statePicker() {
        GameViewModel.autoLoad = false
        Rng.r = Random(9)
        val vm = testViewModel()
        vm.installDraft(LeagueGen.newLeague())
        rule.setContent { App(vm) }
        rule.onAllNodes(hasText("Play as a player")).onFirst().performClick(); idle()
        // The home state field: a long list that opens scrolled to the current state.
        rule.onAllNodes(androidx.compose.ui.test.hasScrollToIndexAction()).onFirst().performScrollToNode(hasText("STATE")); idle()
        rule.onAllNodes(hasText("STATE")).onFirst().performClick(); idle()
        assertTrue("state picker open", rule.onAllNodes(androidx.compose.ui.test.isPopup()).fetchSemanticsNodes().isNotEmpty())
        shot("Z04_state_picker")
    }

    /** The play style picker in the creator: the position's own styles, now 10 or 11 of them. */
    @Test
    fun stylePicker() {
        GameViewModel.autoLoad = false
        Rng.r = Random(9)
        val vm = testViewModel()
        vm.installDraft(LeagueGen.newLeague())
        rule.setContent { App(vm) }
        rule.onAllNodes(hasText("Play as a player")).onFirst().performClick(); idle()
        rule.onAllNodes(androidx.compose.ui.test.hasScrollToIndexAction()).onFirst().performScrollToNode(hasText("Shot Creator")); idle()
        rule.onAllNodes(hasText("Shot Creator")).onFirst().performClick(); idle()
        assertTrue("style picker open", rule.onAllNodes(hasText("Spark Plug")).fetchSemanticsNodes().isNotEmpty())
        shot("Z09_style_picker")
    }

    @Test
    fun menus() {
        GameViewModel.autoLoad = false
        Rng.r = Random(11)
        val base = LeagueGen.newLeague()
        val college = Offseason.claimTeam(base, base.teams.first { it.school == "Kentucky" }.id, "Jordan", "Reed")
        repeat(4) { Season.playDay(college) }
        val vm = testViewModel()
        vm.install(college)
        rule.setContent { App(vm) }
        vm.select(Tab.TODAY); idle()
        openSimMenu()
        assertTrue("college sim menu", rule.onAllNodes(hasText("Sim to Selection Sunday")).fetchSemanticsNodes().isNotEmpty())
        shot("Z01_college_sim_menu")
        close(vm)
        vm.select(Tab.TEAM); vm.teamTab = 1; idle()
        // The ratings row also says "OFFENSE"; the picker is the field showing the current plan.
        rule.onAllNodes(hasText(college.user.plan.offense.label)).onFirst().performClick(); idle()
        assertTrue("picker open", rule.onAllNodes(androidx.compose.ui.test.isPopup()).fetchSemanticsNodes().isNotEmpty())
        shot("Z02_offense_picker")
        close(vm)

        val pro = ProLeague.create(ProTest().toDraft(21))
        while (pro.phase == Phase.OFFSEASON) ProCareer.next(pro)
        repeat(3) { Sim.playDay(pro) }
        vm.install(pro); vm.select(Tab.TODAY); idle()
        openSimMenu()
        assertTrue("pro sim menu has the deadline", rule.onAllNodes(hasText("Sim to the trade deadline")).fetchSemanticsNodes().isNotEmpty())
        shot("Z03_pro_sim_menu")
        close(vm)
    }
}
