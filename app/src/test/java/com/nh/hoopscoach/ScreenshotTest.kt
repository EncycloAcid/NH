package com.nh.hoopscoach

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.core.app.ApplicationProvider
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.LeagueGen
import com.nh.hoopscoach.game.Offseason
import com.nh.hoopscoach.game.Phase
import com.nh.hoopscoach.game.Rng
import com.nh.hoopscoach.game.Schedule
import com.nh.hoopscoach.game.Season
import com.nh.hoopscoach.game.Tryouts
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

    /** The preseason magazine: Today card, cover, Top 25, conference predictions, players to watch, picks. */
    @Test
    fun seasonPreview() {
        val lg = league("Kentucky")
        val vm = GameViewModel(ApplicationProvider.getApplicationContext())
        vm.install(lg)
        rule.setContent { App(vm) }
        rule.onAllNodes(hasScrollToIndexAction()).onFirst().performScrollToNode(hasText("Read the issue", substring = true))
        rule.waitForIdle(); shot("V01_today_card")
        vm.push(Route.Preview); rule.waitForIdle(); shot("V01_page01")
        var page = 2
        while (true) {
            val next = rule.onAllNodesWithText("Next ›")
            if (next.fetchSemanticsNodes().isEmpty()) break
            next.onFirst().performClick()
            rule.mainClock.advanceTimeBy(1500)
            rule.waitForIdle()
            shot("V01_page" + "%02d".format(page++))
        }
        vm.back()
        // Player customization and the expanded archetypes.
        val star = lg.rosterOf(lg.user).maxBy { it.ovr }
        vm.push(Route.EditPlayer(star.id)); rule.waitForIdle(); shot("E01_edit_player")
        scrollTo(0)
        rule.onAllNodes(hasScrollToIndexAction()).onFirst().performTouchInput { swipeUp() }
        rule.waitForIdle(); shot("E02_edit_player_more")
        vm.back()
        vm.push(Route.Focus(star.id)); rule.waitForIdle()
        // Training focus lists only his position's archetypes now (plus Manual).
        rule.onAllNodes(hasScrollToIndexAction()).onFirst().performScrollToIndex(6)
        rule.waitForIdle(); shot("E03_archetypes")
    }

    /** Career pages: a veteran's season-by-season table and honors, a former player's page, and former players. */
    @Test
    fun careerPages() {
        val lg = league("Kentucky")
        val vm = GameViewModel(ApplicationProvider.getApplicationContext())
        vm.install(lg)
        var pid by mutableIntStateOf(-1)
        rule.setContent {
            if (pid < 0) App(vm)
            else HoopsTheme(androidx.compose.ui.graphics.Color(lg.user.primary), androidx.compose.ui.graphics.Color(lg.user.secondary)) { PlayerCard(vm, lg, lg.anyPlayer(pid)!!) }
        }
        while (lg.phase != Phase.WRAP_UP) Season.playDay(lg)
        Offseason.autoComplete(lg)
        repeat(20) { Season.playDay(lg) }
        vm.install(lg)
        val vet = lg.rosterOf(lg.user).filter { it.career.isNotEmpty() }.maxBy { it.stats.pts + it.awards.size * 200 }
        pid = vet.id
        rule.waitForIdle()
        rule.onNodeWithText("Career").performClick()
        rule.waitForIdle(); shot("C01_career_table")
        rule.onAllNodes(hasScrollToIndexAction()).onFirst().performScrollToNode(hasText("HONORS BY SEASON"))
        rule.waitForIdle(); shot("C02_career_highs")
        val former = lg.alumni.filter { it.lastTeam == lg.userTeamId }.maxByOrNull { com.nh.hoopscoach.game.Careers.total(it).pts } ?: lg.alumni.first()
        pid = former.id
        rule.waitForIdle(); shot("C03_former_player")
        rule.onAllNodes(hasScrollToIndexAction()).onFirst().performScrollToNode(hasText("HONORS BY SEASON"))
        rule.waitForIdle(); shot("C04_former_honors")
        pid = -1
        vm.select(Tab.TEAM); vm.teamTab = 3
        rule.waitForIdle()
        repeat(6) { rule.onAllNodes(hasScrollToIndexAction()).onFirst().performTouchInput { swipeUp() }; rule.waitForIdle() }
        shot("C05_former_list")
    }

    /** Upset alerts, bracket upset tags, the highlight reel and March moments in the season review. */
    @Test
    fun marchMoments() {
        val lg = league("Kentucky")
        val vm = GameViewModel(ApplicationProvider.getApplicationContext())
        vm.install(lg)
        rule.setContent { App(vm) }
        val me = lg.userTeamId
        val coach = lg.userCoachId
        fun viewAs(id: Int) { lg.userTeamId = id; lg.userCoachId = lg.team(id).coachId; vm.install(lg) }
        while (lg.phase != Phase.NCAA) Season.playDay(lg)
        // First Four, then the Round of 64.
        Season.playDay(lg)
        Season.playDay(lg)
        vm.install(lg); vm.select(Tab.TODAY)
        scrollTo(3); shot("M01_upset_alerts")
        scrollTo(6); shot("M02_headlines")
        val upset = lg.moments.lastOrNull { it.kind == com.nh.hoopscoach.game.MomentKind.UPSET && it.season == lg.season }
        if (upset != null) {
            viewAs(upset.winner)
            vm.push(Route.BracketView(-1)); shot("M03_bracket_upsets"); vm.back()
            lg.userTeamId = me; lg.userCoachId = coach; vm.install(lg)
        }
        while (lg.phase == Phase.NCAA) Season.playDay(lg)
        vm.install(lg); vm.wrapPage = 0
        // The wrap page shows the season's top four moments (your own first), so scroll to the section, not one story.
        rule.onAllNodes(hasScrollToIndexAction()).onFirst().performScrollToNode(hasText("MARCH MOMENTS"))
        rule.waitForIdle(); shot("M04_wrap_moments")
        vm.select(Tab.LEAGUE); vm.leagueTab = 5; scrollTo(0); shot("M05_history_preview")
        vm.push(Route.History(HistoryKind.MOMENTS)); scrollTo(0); shot("M06_highlight_reel")
    }

    /** Draft Night honors and pipeline, fan support in the NIL budget, the Program tab's fans and rafters. */
    @Test
    fun programLegacy() {
        val lg = league("Kentucky")
        val vm = GameViewModel(ApplicationProvider.getApplicationContext())
        vm.install(lg)
        rule.setContent { App(vm) }
        while (lg.phase != Phase.WRAP_UP) Season.playDay(lg)
        Offseason.begin(lg)
        val os = lg.offseason!!
        // Two departing stars with the résumés of program legends.
        val leaving = lg.rosterOf(lg.user).sortedByDescending { it.ovr }.take(2)
        leaving.forEach { it.year = 4 }
        leaving[0].awards += listOf("${lg.season} Nat MVP", "${lg.season} AA 1st")
        leaving[1].awards += listOf("${lg.season} Conf POY", "${lg.season} AA 2nd", "${lg.season} DPOY")
        Offseason.advance(lg)
        vm.install(lg); vm.select(Tab.TODAY)
        scrollTo(0); shot("U01_draft_honors")
        scrollTo(1); shot("U02_draft_pipeline")
        vm.select(Tab.TEAM); vm.teamTab = 3
        scrollTo(0); shot("U03_program_fans")
        rule.onAllNodes(hasScrollToIndexAction()).onFirst().performScrollToNode(hasText("HALL OF FAME", substring = true))
        rule.waitForIdle(); shot("U04_program_legends")
        vm.select(Tab.TODAY)
        while (os.step != com.nh.hoopscoach.game.OffStep.BUDGET) {
            if (os.step == com.nh.hoopscoach.game.OffStep.CAREER && os.fired) com.nh.hoopscoach.game.Coaching.acceptOffer(lg, os.offers.first().teamId)
            Offseason.advance(lg)
        }
        vm.install(lg); scrollTo(0); shot("U05_budget_fans")
        Offseason.advance(lg)
        vm.install(lg)
        vm.push(Route.RecruitView(lg.recruits.first { it.p.stars >= 4 }.p.id)); shot("U06_recruit_pipeline"); vm.back()
    }

    /** Walk-on tryouts, a summer breakout, and a walk-on's player card. */
    @Test
    fun tryouts() {
        val lg = league("Kentucky")
        val vm = GameViewModel(ApplicationProvider.getApplicationContext())
        vm.install(lg)
        var pid by mutableIntStateOf(-1)
        rule.setContent {
            if (pid < 0) App(vm)
            else HoopsTheme(androidx.compose.ui.graphics.Color(lg.user.primary), androidx.compose.ui.graphics.Color(lg.user.secondary)) { PlayerCard(vm, lg, lg.player(pid)) }
        }
        while (lg.phase != Phase.WRAP_UP) Season.playDay(lg)
        Offseason.begin(lg)
        val os = lg.offseason!!
        fun step() {
            if (os.step == com.nh.hoopscoach.game.OffStep.CAREER && os.fired) com.nh.hoopscoach.game.Coaching.acceptOffer(lg, os.offers.first().teamId)
            Offseason.advance(lg)
        }
        while (os.step != com.nh.hoopscoach.game.OffStep.CAREER) step()
        // A walk-on from last year's tryouts who has a breakout in him.
        val gem = lg.rosterOf(lg.user).filter { it.year <= 3 }.minBy { it.ovr }
        gem.gem = true; gem.walkOn = true; gem.tryout = lg.season; gem.year = 3
        step()
        vm.install(lg); vm.select(Tab.TODAY)
        scrollTo(0); shot("T01_breakout")
        while (os.step != com.nh.hoopscoach.game.OffStep.TRYOUTS) step()
        Tryouts.look(lg, 0)
        Tryouts.toggle(lg, 0)
        vm.install(lg)
        scrollTo(0); shot("T02_tryouts_top")
        scrollTo(2); shot("T03_tryouts_cards")
        scrollTo(4); shot("T04_tryouts_more")
        val kept = os.tryouts[0].p.id
        step()
        vm.install(lg)
        pid = kept; shot("T05_walkon_card")
        pid = gem.id; shot("T06_late_bloomer_card")
        pid = -1
        vm.select(Tab.TEAM); vm.teamTab = 0; scrollTo(5); shot("T07_roster_bench")
    }

    /** Duke-North Carolina: preview, live crowd, the newspaper banner and the box score. */
    @Test
    fun rivalry() = rivalryShots("R")

    @Test
    fun rivalryDark() {
        RuntimeEnvironment.setQualifiers("+night")
        rivalryShots("RD", full = false)
    }

    /** The newspaper banner on a narrow phone with slightly larger system text. */
    @Test
    fun rivalryNarrow() {
        RuntimeEnvironment.setQualifiers("w360dp-h780dp-xhdpi")
        RuntimeEnvironment.setFontScale(1.15f)
        rivalryShots("RN", full = false)
    }

    /** The same on a narrow phone in dark mode. */
    @Test
    fun rivalryNarrowDark() {
        RuntimeEnvironment.setQualifiers("w360dp-h780dp-night-xhdpi")
        RuntimeEnvironment.setFontScale(1.15f)
        rivalryShots("RND", full = false)
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

    /** Program numbers, the guides, and a recruiting cycle from scouting to a locked round. */
    @Test
    fun programAndRecruiting() {
        val lg = league("Kentucky")
        val vm = GameViewModel(ApplicationProvider.getApplicationContext())
        vm.install(lg)
        var guide by mutableIntStateOf(-1)
        rule.setContent {
            if (guide < 0) App(vm)
            else HoopsTheme(androidx.compose.ui.graphics.Color(lg.user.primary), androidx.compose.ui.graphics.Color(lg.user.secondary)) {
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize().background(com.nh.hoopscoach.ui.P.bg)) {
                    com.nh.hoopscoach.ui.GuideContent(vm, if (guide == 9) com.nh.hoopscoach.ui.Guide.RECRUITING else com.nh.hoopscoach.ui.Guide.PROGRAM)
                }
            }
        }
        fun swipe() { rule.onAllNodes(hasScrollToIndexAction()).onLast().performTouchInput { swipeUp() }; rule.waitForIdle() }
        vm.select(Tab.TEAM); vm.teamTab = 3; shot("G01_program_numbers")
        for (i in 0..4) { vm.guideTab = i; guide = i; shot("G02_guide_$i") }
        vm.guideTab = 4; guide = 4; swipe(); shot("G03_guide_program_more"); swipe(); swipe(); shot("G04_guide_program_end")
        guide = 9; shot("G05_recruit_guide"); swipe(); shot("G06_recruit_guide_more"); swipe(); swipe(); shot("G07_recruit_guide_end")
        guide = -1
        vm.select(Tab.RECRUIT); shot("G08_recruit_season")
        val five = lg.recruits.first { it.p.stars == 5 }
        vm.push(Route.RecruitView(five.p.id)); shot("G09_prospect_season"); vm.back()

        while (lg.phase != Phase.WRAP_UP) Season.playDay(lg)
        Offseason.begin(lg)
        val os = lg.offseason!!
        while (os.step != com.nh.hoopscoach.game.OffStep.RECRUITING) {
            if (os.step == com.nh.hoopscoach.game.OffStep.CAREER && os.fired) com.nh.hoopscoach.game.Coaching.acceptOffer(lg, os.offers.first().teamId)
            Offseason.advance(lg)
        }
        vm.install(lg); vm.select(Tab.TODAY); shot("G10_round1_flow")
        vm.select(Tab.RECRUIT); vm.recruitView = 1; shot("G11_board_round1")
        com.nh.hoopscoach.game.Recruiting.autoPlan(lg)
        vm.install(lg); vm.select(Tab.RECRUIT); vm.recruitView = 0; scrollTo(2); shot("G12_board_planned")
        val target = lg.recruits.filter { com.nh.hoopscoach.game.Recruiting.mine(lg, it) > 0 }.maxBy { it.p.ovr }
        vm.push(Route.RecruitView(target.p.id)); shot("G13_prospect_points")
        scrollTo(3); shot("G14_prospect_why"); scrollTo(4); shot("G15_prospect_race"); vm.back()
        vm.push(Route.RecruitPreview); shot("G16_preview"); vm.back()
        Offseason.advance(lg)
        vm.install(lg); vm.select(Tab.RECRUIT); vm.recruitView = 0; shot("G17_board_round2")
        vm.select(Tab.TODAY); shot("G18_round2_flow")
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
        vm.push(Route.BuildLab(lg.user.roster.first())); shot("${tag}11b_build_lab"); vm.back()

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
