package com.nh.hoopscoach

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.nh.hoopscoach.game.News
import com.nh.hoopscoach.game.Offseason
import com.nh.hoopscoach.game.Rng
import com.nh.hoopscoach.game.RoadState
import com.nh.hoopscoach.game.PressQuestion
import com.nh.hoopscoach.game.Story
import com.nh.hoopscoach.ui.AiBadge
import com.nh.hoopscoach.ui.GameViewModel
import com.nh.hoopscoach.ui.HoopsTheme
import com.nh.hoopscoach.ui.NewsTag
import com.nh.hoopscoach.ui.Panel
import com.nh.hoopscoach.ui.PressCard
import com.nh.hoopscoach.ui.StoryCard
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h880dp-xhdpi")
class AiBadgeTest {
    init { com.nh.hoopscoach.ui.GameViewModel.autoLoad = false }
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun generatedItemShowsGreenCheckAndProviderOnlyOnTap() {
        rule.activity.setContent { HoopsTheme { Panel { AiBadge(true, "Google Gemini") } } }
        rule.onNodeWithContentDescription("AI-generated").assertIsDisplayed()
        rule.onNodeWithText("Generated with Google Gemini").assertDoesNotExist()
        rule.onNodeWithContentDescription("AI-generated").performClick()
        rule.onNodeWithText("Generated with Google Gemini").assertIsDisplayed()
    }

    @Test fun ordinaryItemAndDisabledBadgeHaveNoCheck() {
        val n = News(2026, 1, "Ordinary game story", kind = "game", source = "Staff")
        rule.activity.setContent { HoopsTheme { Column { AiBadge(false, "Google Gemini"); NewsTag(n) } } }
        rule.onNodeWithContentDescription("AI-generated").assertDoesNotExist()
    }

    @Test fun persistedAiFlagControlsNewsBadge() {
        val (lg, n) = ArticleFixture.league()
        n.body = com.nh.hoopscoach.game.ArticleWriting.news(lg, n)
        n.aiLabeled = true
        rule.activity.setContent { HoopsTheme { NewsTag(n) } }
        rule.onNodeWithContentDescription("AI-generated").assertIsDisplayed()
    }

    @Test fun unfinishedPersistedAiFlagDoesNotGiveACheck() {
        val n = News(2026, 1, "Story", body = "A performance cut off to", source = "Courtside Report", aiLabeled = true)
        rule.activity.setContent { HoopsTheme { NewsTag(n) } }
        rule.onNodeWithContentDescription("AI-generated").assertDoesNotExist()
    }

    @Test fun previewStoryUsesItsOwnAiFlagNotEnabledProvider() {
        GameViewModel.autoLoad = false
        val (lg, article) = ArticleFixture.league()
        lg.ollama.enabled = true
        val vm = GameViewModel(ApplicationProvider.getApplicationContext())
        vm.install(lg)
        val story = Story("Game story", article.text, ArticleFixture.aiStory(), lg.userTeamId, aiProvider = "Google Gemini")
        rule.activity.setContent { HoopsTheme { StoryCard(vm, lg, story, 0, false) } }
        rule.onNodeWithContentDescription("AI-generated").assertIsDisplayed()
    }

    @Test fun enabledProviderDoesNotMislabelGameWrittenPressQuestion() {
        GameViewModel.autoLoad = false
        Rng.r = Random(41)
        val lg = Offseason.newGame(5, "Test", "Coach")
        lg.ollama.enabled = true
        lg.road = RoadState().also { it.press = PressQuestion(lg.season, 1, "Postgame", "How do you assess the game?", emptyList(), "Staff", false) }
        val vm = GameViewModel(ApplicationProvider.getApplicationContext())
        vm.install(lg)
        rule.activity.setContent { HoopsTheme { Column { PressCard(vm, lg) } } }
        rule.onNodeWithContentDescription("AI-generated").assertDoesNotExist()
        rule.onNodeWithText("“How do you assess the game?”").assertIsDisplayed()
    }
}
