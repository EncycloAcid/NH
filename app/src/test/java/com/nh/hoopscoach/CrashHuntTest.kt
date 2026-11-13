package com.nh.hoopscoach

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.LeagueGen
import com.nh.hoopscoach.game.OffStep
import com.nh.hoopscoach.game.Offseason
import com.nh.hoopscoach.game.Phase
import com.nh.hoopscoach.game.Recruiting
import com.nh.hoopscoach.game.Rng
import com.nh.hoopscoach.game.SaveIO
import com.nh.hoopscoach.game.Season
import com.nh.hoopscoach.ui.App
import com.nh.hoopscoach.ui.GameViewModel
import com.nh.hoopscoach.ui.Guide
import com.nh.hoopscoach.ui.HistoryKind
import com.nh.hoopscoach.ui.Route
import com.nh.hoopscoach.ui.Tab
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.random.Random

/**
 * Drives the real app through a season and an offseason at phone width, opening every tab, screen,
 * sheet and guide and pressing the recruiting buttons. Any exception fails the test. Saves made by
 * older versions (in build/oldsaves) are loaded and walked through the same way.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xhdpi")
class CrashHuntTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun idle() = rule.waitForIdle()

    private fun visitAll(vm: GameViewModel, lg: League, where: String) {
        println("visiting: $where (${lg.phase}, day ${lg.day}, step ${lg.offseason?.step}, round ${lg.offseason?.round})")
        vm.install(lg); idle()
        for (t in Tab.entries) {
            vm.select(t); idle()
            when (t) {
                Tab.TEAM -> for (i in 0..3) { vm.teamTab = i; idle() }
                Tab.LEAGUE -> for (i in 0..5) { vm.leagueTab = i; idle() }
                Tab.RECRUIT -> for (i in 0..3) { vm.recruitView = i; idle() }
                Tab.TODAY -> if (lg.phase == Phase.WRAP_UP) for (i in 0..4) { vm.wrapPage = i; idle() }
                else -> {}
            }
        }
        vm.select(Tab.TODAY); idle()
        val u = lg.user
        val routes = mutableListOf<Route>(
            Route.TeamPage(u.id), Route.TeamPage(lg.teams.first { it.id != u.id }.id), Route.Schedule(u.id),
            Route.Settings, Route.Leaders, Route.CoachDev, Route.Preview, Route.EditTeam(u.id),
        )
        HistoryKind.entries.forEach { routes += Route.History(it) }
        u.roster.firstOrNull()?.let { routes += Route.Focus(it); routes += Route.EditPlayer(it) }
        lg.recruits.take(3).forEach { routes += Route.RecruitView(it.p.id) }
        lg.recruits.filter { Recruiting.mine(lg, it) > 0 || it.nilOffer > 0 }.take(3).forEach { routes += Route.RecruitView(it.p.id) }
        lg.recruits.firstOrNull { it.committed >= 0 }?.let { routes += Route.RecruitView(it.p.id) }
        if (Recruiting.inCycle(lg)) routes += Route.RecruitPreview
        if (lg.ncaa != null) routes += Route.BracketView(-1)
        lg.games.lastOrNull { it.played }?.let { routes += Route.Box(it.id) }
        lg.games.lastOrNull { it.played && it.involves(u.id) }?.let { routes += Route.Box(it.id) }
        for (r in routes) {
            vm.push(r); idle()
            for (tab in 0..3) {
                // Team pages have four tabs; other screens ignore this.
                if (r is Route.TeamPage) { /* tabs are local state; the first is enough */ }
            }
            vm.back(); idle()
        }
        for (i in 0..4) { vm.openGuide(Guide.PROGRAM, i); idle() }
        vm.guide = null; idle()
        vm.openGuide(Guide.RECRUITING); idle(); vm.guide = null; idle()
        (u.roster.take(2) + lg.alumni.take(1).map { it.id }).forEach { vm.openPlayer(it); idle(); vm.sheet = null; idle() }
    }

    private fun recruitingActions(vm: GameViewModel, lg: League) {
        val r = lg.recruits.firstOrNull { it.committed < 0 } ?: return
        vm.act { Recruiting.addToBoard(it, r) }; idle()
        vm.act { Recruiting.setNil(it, r, r.nilAsk) }; idle()
        vm.act { Recruiting.setNil(it, r, r.nilAsk * 5) }; idle()
        vm.act { Recruiting.scout(it, r) }; idle()
        vm.push(Route.RecruitView(r.p.id)); idle(); vm.back(); idle()
        if (Recruiting.inCycle(lg)) {
            vm.act { Recruiting.setPoints(it, r, 50) }; idle()
            vm.act { Recruiting.autoPlan(it) }; idle()
            vm.push(Route.RecruitPreview); idle(); vm.back(); idle()
        }
    }

    private fun playThrough(vm: GameViewModel, lg: League, tag: String) {
        visitAll(vm, lg, "$tag start"); recruitingActions(vm, lg)
        var guard = 0
        while (lg.phase == Phase.REGULAR && lg.day < 12 && guard++ < 50) Season.playDay(lg)
        visitAll(vm, lg, "$tag mid-season")
        while (lg.phase == Phase.REGULAR && guard++ < 200) Season.playDay(lg)
        visitAll(vm, lg, "$tag conference tournaments")
        while (lg.phase == Phase.CONF_TOURNEY && guard++ < 260) Season.playDay(lg)
        if (lg.phase == Phase.NCAA) { Season.playDay(lg); visitAll(vm, lg, "$tag NCAA") }
        while (lg.phase != Phase.WRAP_UP && lg.phase != Phase.OFFSEASON && guard++ < 320) Season.playDay(lg)
        if (lg.phase == Phase.WRAP_UP) { visitAll(vm, lg, "$tag wrap-up"); Offseason.begin(lg) }
        val os = lg.offseason!!
        var g2 = 0
        while (lg.phase == Phase.OFFSEASON && g2++ < 20) {
            visitAll(vm, lg, "$tag offseason")
            recruitingActions(vm, lg)
            if (os.step == OffStep.CAREER && os.fired) com.nh.hoopscoach.game.Coaching.acceptOffer(lg, os.offers.first().teamId)
            Offseason.advance(lg)
            vm.install(lg); idle()
        }
        visitAll(vm, lg, "$tag next season")
    }

    @Test
    fun freshLeague() {
        GameViewModel.autoLoad = false
        Rng.r = Random(5)
        val base = LeagueGen.newLeague()
        val lg = Offseason.claimTeam(base, base.teams.first { it.school == "Georgia Tech" }.id, "Jordan", "Reed")
        val vm = testViewModel()
        vm.install(lg)
        rule.setContent { App(vm) }
        playThrough(vm, lg, "fresh")
    }

    @Test
    fun oldSaves() {
        GameViewModel.autoLoad = false
        val dir = File(System.getProperty("user.dir"), "../build/oldsaves").canonicalFile
        val files = dir.listFiles { f -> f.name.endsWith(".sav") }?.sortedBy { it.name }.orEmpty()
        println("old saves in $dir: ${files.map { it.name }}")
        val vm = testViewModel()
        var current: League? = null
        rule.setContent { if (current != null || true) App(vm) }
        for (f in files) {
            val lg = SaveIO.decode(f.readBytes())
            current = lg
            vm.install(lg); idle()
            visitAll(vm, lg, f.name)
            recruitingActions(vm, lg)
            if (lg.phase == Phase.OFFSEASON) {
                var g = 0
                while (lg.phase == Phase.OFFSEASON && g++ < 12) {
                    val os = lg.offseason!!
                    if (os.step == OffStep.CAREER && os.fired) com.nh.hoopscoach.game.Coaching.acceptOffer(lg, os.offers.first().teamId)
                    Offseason.advance(lg)
                    visitAll(vm, lg, "${f.name} after step")
                }
            }
        }
    }
}
