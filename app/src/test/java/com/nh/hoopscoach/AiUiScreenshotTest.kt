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
    @Test fun diagnosticsFetchWithNoApiKeyShowsAnActionableFailure() {
        val config = com.nh.hoopscoach.game.OllamaConfig(provider = "google")
        rule.activity.setContent { HoopsTheme { com.nh.hoopscoach.ui.AiDiagnosticsPanel(config) {} } }
        rule.onNodeWithText("Fetch available models").performClick()
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
        val lg = Offseason.newGame(5, "Test", "Coach")
        lg.ollama.enabled = true
        val star = lg.rosterOf(lg.user).maxBy { it.ovr }
        star.career.removeAll { it.season == lg.season - 1 }
        star.career += SeasonLine(lg.season - 1, lg.user.school, lg.conf(lg.user.confId).name, 3, star.ovr,
            StatLine(gp = 30, pts = 510, orb = 18, drb = 114, ast = 138), teamId = lg.userTeamId)
        lg.news += News(lg.season, lg.day, "${lg.user.school} faces a changing rotation as ${star.name} anchors the preseason outlook",
            source = if (isAi) "Courtside Report · Google Gemini" else "Game-written · Courtside Report", teamId = lg.userTeamId, pid = star.id, kind = "column", aiLabeled = isAi,
            body = AiTemplates.previewCopy(lg, lg.user), aiFallbackReason = if (isAi) "" else "Gemini requires an API key. Configure it in AI settings.")
        val vm = GameViewModel(ApplicationProvider.getApplicationContext())
        vm.install(lg)
        rule.activity.setContent { HoopsTheme { ArticleScreen(vm, lg, lg.news.lastIndex) } }
        rule.onNodeWithText(lg.news.last().text).assertIsDisplayed()
        rule.onAllNodesWithContentDescription("AI-generated").assertCountEquals(if (isAi) 1 else 0)
        shot("article-$mode")
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
