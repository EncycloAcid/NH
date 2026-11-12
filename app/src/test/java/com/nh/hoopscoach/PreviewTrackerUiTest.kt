package com.nh.hoopscoach

import androidx.activity.compose.setContent
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.nh.hoopscoach.game.Offseason
import com.nh.hoopscoach.game.Preview
import com.nh.hoopscoach.game.PreviewTracker
import com.nh.hoopscoach.game.Rng
import com.nh.hoopscoach.ui.HoopsTheme
import com.nh.hoopscoach.ui.PreviewTrackerPanel
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w400dp-h880dp-xhdpi")
class PreviewTrackerUiTest {
    init { com.nh.hoopscoach.ui.GameViewModel.autoLoad = false }
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun trackerAppearsAndUpdatesMidSeasonWithoutEditingPreview() {
        Rng.r = Random(41)
        val lg = Offseason.newGame(5, "Test", "Coach")
        val pv = lg.preview!!
        val before = Json.encodeToString(Preview.serializer(), pv)
        var track by mutableStateOf(PreviewTracker.college(lg, pv))
        rule.activity.setContent { HoopsTheme { LazyColumn { item { PreviewTrackerPanel(lg, track) } } } }
        rule.onNodeWithText("How our picks are doing", ignoreCase = true).assertDoesNotExist()
        rule.runOnIdle {
            lg.user.w = 9; lg.user.l = 1; lg.user.ap = 4
            track = PreviewTracker.college(lg, pv)
        }
        rule.onNodeWithText("How our picks are doing", ignoreCase = true).assertIsDisplayed()
        rule.onNodeWithText("Now: 9-1 · AP No. 4").assertExists()
        assertTrue(rule.onAllNodesWithText("On track").fetchSemanticsNodes().isNotEmpty())
        rule.runOnIdle {
            lg.user.w = 0; lg.user.l = 10; lg.user.ap = 0; lg.user.net = 90
            track = PreviewTracker.college(lg, pv)
        }
        rule.onNodeWithText("Now: 0-10 · NET No. 90").assertExists()
        assertEquals(before, Json.encodeToString(Preview.serializer(), pv))
    }
}
