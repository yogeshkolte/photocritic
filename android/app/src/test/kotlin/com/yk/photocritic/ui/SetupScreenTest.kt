package com.yk.photocritic.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.yk.photocritic.FakeKeyStore
import com.yk.photocritic.FakeTelemetryBackend
import com.yk.photocritic.TestConfig
import com.yk.photocritic.core.providers.ProviderId
import com.yk.photocritic.core.providers.fake.FakeProvider
import com.yk.photocritic.features.setup.SetupViewModel
import com.yk.photocritic.testContainer
import com.yk.photocritic.testSnapshot
import com.yk.photocritic.waitFor
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** CUJ 1 on the setup screen (file 38). */
@RunWith(RobolectricTestRunner::class)
class SetupScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private val keys = FakeKeyStore()

    @Test
    fun pickAModelEnterAKeyAndContinue() {
        val c = testContainer(tmp.root, TestConfig.source(), keys, FakeTelemetryBackend(), FakeProvider(ProviderId.GEMINI))
        val vm = SetupViewModel(c, testSnapshot(), firstRun = true)
        var saved: Boolean? = null
        compose.setContent {
            SetupScreen(vm, killSwitchMessage = null, conversationActive = false, onSaved = { saved = it }, onBack = null, openUrl = {})
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Gemini 4 Hydrogen").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Set up PhotoCritic").assertExists()
        compose.onNodeWithText("Fast").assertExists()
        compose.onNodeWithText("Best").assertExists()
        compose.onNodeWithText("Checking a Gemini key uses a few tokens.").assertExists()
        compose.onNodeWithText("Share usage and crash data").assertExists()
        compose.onNodeWithText("Check key and continue").assertIsNotEnabled()
        compose.onNodeWithText("Gemini 4 Hydrogen").performScrollTo().performClick()
        compose.onNodeWithText("API key").assertExists()
        compose.onAllNodesWithText("Show key")[0].assertExists()
        compose.onNode(hasSetTextAction()).performScrollTo().performTextInput("AQ.typed-key")
        compose
            .onNodeWithText("Check key and continue")
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        // The save runs on real IO threads, so wait in real time, not on the test clock.
        waitFor("saved") {
            compose.waitForIdle()
            saved != null
        }
        assertEquals(true, saved)
        assertEquals("AQ.typed-key", keys.keys[ProviderId.GEMINI])
    }

    @Test
    fun theKillSwitchMessageIsShown() {
        val c = testContainer(tmp.root, TestConfig.source(), keys, FakeTelemetryBackend(), FakeProvider(ProviderId.GEMINI))
        compose.setContent {
            SetupScreen(SetupViewModel(c, testSnapshot(), firstRun = false), "Pick another model.", false, {}, onBack = {}, openUrl = {})
        }
        compose.onNodeWithText("Pick another model.").assertExists()
        compose.onNodeWithText("Provider and model").assertExists()
        compose.onAllNodesWithText("Share usage and crash data").assertCountEquals(0)
    }
}
