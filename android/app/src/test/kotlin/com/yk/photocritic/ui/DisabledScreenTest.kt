package com.yk.photocritic.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** CUJ 9 and the kill-switch screens; no Try again button (AR2). */
@RunWith(RobolectricTestRunner::class)
class DisabledScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun configUnavailableHasNoButton() {
        compose.setContent { DisabledScreen(DisabledReason.ConfigUnavailable) {} }
        compose.onNodeWithText("PhotoCritic couldn't load its settings. Check your connection and restart the app.").assertExists()
        compose.onAllNodesWithText("Update").assertCountEquals(0)
        compose.onAllNodesWithText("Try again").assertCountEquals(0)
    }

    @Test
    fun appDisabledShowsTheConfiguredOrDefaultMessage() {
        compose.setContent { DisabledScreen(DisabledReason.AppDisabled("Back on Monday.")) {} }
        compose.onNodeWithText("Back on Monday.").assertExists()
    }

    @Test
    fun appDisabledWithoutAMessage() {
        compose.setContent { DisabledScreen(DisabledReason.AppDisabled("")) {} }
        compose.onNodeWithText("PhotoCritic is not available right now.").assertExists()
    }

    @Test
    fun updateOpensTheUpdateUrl() {
        var opened: String? = null
        compose.setContent { DisabledScreen(DisabledReason.UpdateRequired("https://play.google.com/store")) { opened = it } }
        compose.onNodeWithText("Update").performClick()
        assertEquals("https://play.google.com/store", opened)
    }

    @Test
    fun updateWithoutAUrlHasNoButton() {
        compose.setContent { DisabledScreen(DisabledReason.UpdateRequired("")) {} }
        compose.onAllNodesWithText("Update").assertCountEquals(0)
    }
}
