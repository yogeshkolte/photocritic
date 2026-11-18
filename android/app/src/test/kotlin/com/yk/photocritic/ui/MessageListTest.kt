package com.yk.photocritic.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.yk.photocritic.core.providers.PcError
import com.yk.photocritic.core.providers.PcPhase
import com.yk.photocritic.core.providers.PromptKind
import com.yk.photocritic.core.session.MessageStatus
import com.yk.photocritic.core.session.VisibleMessage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Messages, text cards, tags and the generic error (file 38, G3, G4, G12). */
@RunWith(RobolectricTestRunner::class)
class MessageListTest {
    @get:Rule
    val compose = createComposeRule()

    private val copied = mutableListOf<String>()
    private var dismissed = 0
    private var details: PcError? = null
    private val actions = MessageActions({ copied += it }, {}, { details = it }, { dismissed++ })

    @Test
    fun aTextCardHasItsOwnCopyAndTheAnswerHasCopyAnswer() {
        val m =
            VisibleMessage(1, "Copy text", PromptKind.EXTRACT, "The sign says:\n```text\nHILLTOP\nSummit 2.4 km\n```", MessageStatus.DONE)
        compose.setContent { MessageList(listOf(m), actions) }
        compose.onNodeWithText("Text in photo").assertExists()
        compose.onNodeWithText("HILLTOP\nSummit 2.4 km").assertExists()
        compose.onNodeWithText("Copy").performClick()
        compose.onNodeWithText("Copy answer").performClick()
        assertEquals(listOf("HILLTOP\nSummit 2.4 km", m.answer), copied)
    }

    @Test
    fun stoppedAndFailedAnswersAreLabelledAndTheErrorHasDetailsAndDismiss() {
        val error = PcError(PcPhase.MID_STREAM, 500, "INTERNAL", "req-1", "boom")
        val messages =
            listOf(
                VisibleMessage(1, "One", PromptKind.FREE, "partial", MessageStatus.STOPPED),
                VisibleMessage(2, "Two", PromptKind.FREE, "half", MessageStatus.FAILED, error),
            )
        compose.setContent { MessageList(messages, actions) }
        compose.onNodeWithText("Stopped").assertExists()
        compose.onNodeWithText("Failed").assertExists()
        compose.onNodeWithText("Something went wrong. Please try again.").assertExists()
        compose.onNodeWithText("Show details").performClick()
        assertEquals(error, details)
        compose.onNodeWithText("Dismiss").performClick()
        assertEquals(1, dismissed)
        compose.onAllNodesWithText("Copy answer").assertCountEquals(2)
    }
}
