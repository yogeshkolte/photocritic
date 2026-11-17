package com.yk.photocritic

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.yk.photocritic.ui.Placeholder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaceholderTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun showsPlaceholderText() {
        compose.setContent { Placeholder() }
        compose.onNodeWithText("Phase 0 build").assertExists()
    }
}
