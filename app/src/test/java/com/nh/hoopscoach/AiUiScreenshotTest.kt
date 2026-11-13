package com.nh.hoopscoach

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.nh.hoopscoach.game.News
import com.nh.hoopscoach.game.AiTemplates
import com.nh.hoopscoach.game.ArticleWriting
import com.nh.hoopscoach.game.SeasonLine
import com.nh.hoopscoach.game.StatLine
import com.nh.hoopscoach.game.Offseason
import com.nh.hoopscoach.game.Rng
import com.nh.hoopscoach.ui.ArticleScreen
import com.nh.hoopscoach.ui.GameViewModel
import com.nh.hoopscoach.ui.HoopsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h880dp-xhdpi")
class AiUiScreenshotTest {
    init { com.nh.hoopscoach.ui.GameViewModel.autoLoad = false }
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun articleAndEditorsDeskLight() = render("light")
    @Test fun articleAndEditorsDeskDark() {
        RuntimeEnvironment.setQualifiers("+night")
        render("dark")
    }

    @Test fun gameWrittenFallbackHasNoCheck() = render("fallback-light", false)
    @Test fun gameWrittenFallbackDark() {
        RuntimeEnvironment.setQualifiers("+night")
        render("fallback-dark", false)
    }
    @Test fun unfinishedSavedDraftCannotBePublishedWithACheck() {
        val (lg, news) = ArticleFixture.league()
        lg.ollama.enabled = true
        news.body = "${lg.playerOrNull(news.pid)!!.name} delivered a standout performance, scoring 32 points to"
        news.aiLabeled = true
        news.source = "Baseline Sports · Google Gemini"
        lg.news += news
        val vm = GameViewModel(ApplicationProvider.getApplicationContext())
        vm.install(lg)
        rule.activity.setContent { HoopsTheme { ArticleScreen(vm, lg, 0) } }
        rule.onAllNodesWithContentDescription("AI-generated").assertCountEquals(0)
        rule.onNodeWithText("Publish").performScrollTo().performClick()
        rule.waitUntil(30_000) { news.approved }
        org.junit.Assert.assertFalse(news.isAiGenerated)
        org.junit.Assert.assertEquals(ArticleWriting.news(lg, news), news.body)
    }
    @Test fun diagnosticsFetchWithNoApiKeyShowsAnActionableFailure() {
        val config = com.nh.hoopscoach.game.OllamaConfig(provider = "google")
        rule.activity.setContent { HoopsTheme { com.nh.hoopscoach.ui.AiDiagnosticsPanel(config) {} } }
        rule.onNodeWithText("Fetch models").performClick()
        rule.waitUntil(30_000) { com.nh.hoopscoach.game.AiDiagnostics.last?.let { it.request == "Model list" && it.result == "network error" } == true }
        org.junit.Assert.assertTrue(com.nh.hoopscoach.game.AiDiagnostics.last!!.detail.contains("API key"))
        rule.onNodeWithText("Test AI").performClick()
        rule.waitUntil(30_000) { com.nh.hoopscoach.game.AiDiagnostics.last?.let { it.request == "Test AI" && it.result == "network error" } == true }
        org.junit.Assert.assertTrue(com.nh.hoopscoach.game.AiDiagnostics.last!!.detail.contains("API key"))
        shot("diagnostics-key-required")
    }

    private fun render(mode: String, isAi: Boolean = true) {
        GameViewModel.autoLoad = false
        Rng.r = Random(41)
        val (lg, article) = ArticleFixture.league()
        lg.ollama.enabled = true
        val star = lg.playerOrNull(article.pid)!!
        star.career.removeAll { it.season == lg.season - 1 }
        star.career += SeasonLine(lg.season - 1, lg.user.school, lg.conf(lg.user.confId).name, 3, star.ovr,
            StatLine(gp = 30, pts = 510, orb = 18, drb = 114, ast = 138), teamId = lg.userTeamId)
        val fullStory = if (isAi) ArticleFixture.aiStory() else ArticleWriting.news(lg, article)
        lg.news += News(lg.season, lg.day, article.text, gameId = article.gameId,
            source = if (isAi) "Baseline Sports · Google Gemini" else "Game-written · Baseline Sports", teamId = lg.userTeamId, pid = star.id, kind = "article", aiLabeled = isAi,
            body = fullStory, aiFallbackReason = if (isAi) "" else "Gemini requires an API key. Configure it in AI settings.")
        val vm = GameViewModel(ApplicationProvider.getApplicationContext())
        vm.install(lg)
        rule.activity.setContent { HoopsTheme { ArticleScreen(vm, lg, lg.news.lastIndex) } }
        rule.onNodeWithText(lg.news.last().text).assertIsDisplayed()
        rule.onAllNodesWithContentDescription("AI-generated").assertCountEquals(if (isAi) 1 else 0)
        rule.onNodeWithText("AWAY · ${lg.team(lg.games.first().away).name} (6-4)  86").assertIsDisplayed()
        rule.onNodeWithText("HOME · ${lg.user.name} (7-3)  90").assertIsDisplayed()
        shot("article-$mode")
        rule.onNodeWithText(fullStory.split("\n\n")[2]).performScrollTo().assertIsDisplayed()
        shot("story-middle-$mode")
        rule.onNodeWithText(fullStory.split("\n\n").last()).performScrollTo().assertIsDisplayed()
        shot("story-end-$mode")
        rule.onNodeWithText("Review, edit quotes, or re-roll the reporter's angle before publishing.").performScrollTo().assertIsDisplayed()
        shot("editors-desk-$mode")
    }

    private fun shot(name: String) {
        rule.waitForIdle()
        val view = rule.activity.window.decorView.rootView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        val dir = File(System.getenv("AI_UI_SCREENSHOTS") ?: "build/ai-ui").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("AI UI screenshot: ${File(dir, "$name.png").absolutePath}")
    }
}
