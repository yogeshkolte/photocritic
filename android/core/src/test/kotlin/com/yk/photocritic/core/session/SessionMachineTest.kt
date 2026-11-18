package com.yk.photocritic.core.session

import com.yk.photocritic.core.config.Tuning
import com.yk.photocritic.core.providers.ModelSettings
import com.yk.photocritic.core.providers.PcError
import com.yk.photocritic.core.providers.PcPhase
import com.yk.photocritic.core.providers.PromptKind
import com.yk.photocritic.core.providers.SendCopy
import com.yk.photocritic.core.providers.TurnEvent
import com.yk.photocritic.core.providers.TurnRequest
import com.yk.photocritic.core.providers.fake.FakeAnswer
import com.yk.photocritic.core.providers.fake.FakeProvider
import com.yk.photocritic.core.session.SessionEvent.Ask
import com.yk.photocritic.core.session.SessionEvent.Cleared
import com.yk.photocritic.core.session.SessionEvent.ConsentAgreed
import com.yk.photocritic.core.session.SessionEvent.ConsentDeclined
import com.yk.photocritic.core.session.SessionEvent.ConsentWriteFailed
import com.yk.photocritic.core.session.SessionEvent.Delta
import com.yk.photocritic.core.session.SessionEvent.Dismiss
import com.yk.photocritic.core.session.SessionEvent.Failed
import com.yk.photocritic.core.session.SessionEvent.Finished
import com.yk.photocritic.core.session.SessionEvent.PhotoChosen
import com.yk.photocritic.core.session.SessionEvent.PhotoFailed
import com.yk.photocritic.core.session.SessionEvent.PhotoReady
import com.yk.photocritic.core.session.SessionEvent.Stopped
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SessionMachineTest {
    private val error = PcError(PcPhase.BEFORE_RESPONSE, 500, "INTERNAL", null, "boom")

    private fun reduce(
        vararg events: SessionEvent,
        from: SessionState = SessionState(),
    ) = events.fold(from, SessionMachine::reduce)

    private val ready = reduce(PhotoChosen("img1"), PhotoReady)

    @Test
    fun `photo chosen then ready or failed, and dismissing an image error goes back to no photo`() {
        assertEquals(Phase.READY, ready.phase)
        assertTrue(ready.hasPhoto)
        val failed = reduce(PhotoChosen("img1"), PhotoFailed(error))
        assertEquals(Phase.IMAGE_ERROR, failed.phase)
        assertEquals(error, failed.error)
        assertEquals(Phase.NO_PHOTO, reduce(Dismiss, from = failed).phase)
    }

    @Test
    fun `a full answer streams, finishes and joins history`() {
        val s =
            reduce(Ask("What is it?", PromptKind.FREE, needsConsent = false), Delta(1, "A "), Delta(1, "sign."), Finished(1), from = ready)
        assertEquals(Phase.READY, s.phase)
        val m = s.messages.single()
        assertEquals("A sign.", m.answer)
        assertEquals(MessageStatus.DONE, m.status)
        assertEquals("What is it?", s.history.single().question)
        assertEquals("A sign.", s.history.single().answerText)
        assertEquals("img1", s.history.single().imageVersionId)
    }

    @Test
    fun `consent gate - agree sends, not now returns to ready, a failed write sends nothing`() {
        val waiting = reduce(Ask("Q", PromptKind.IDENTIFY, needsConsent = true), from = ready)
        assertEquals(Phase.AWAITING_CONSENT, waiting.phase)
        assertTrue(waiting.messages.isEmpty())
        val sent = reduce(ConsentAgreed, from = waiting)
        assertEquals(Phase.SENDING, sent.phase)
        assertEquals(PromptKind.IDENTIFY, sent.messages.single().promptKind)
        val declined = reduce(ConsentDeclined, from = waiting)
        assertEquals(Phase.READY, declined.phase)
        assertNull(declined.pending)
        assertTrue(declined.messages.isEmpty())
        val writeFailed = reduce(ConsentWriteFailed(error), from = waiting)
        assertEquals(Phase.ERROR, writeFailed.phase)
        assertTrue(writeFailed.messages.isEmpty())
        assertEquals(Phase.READY, reduce(Dismiss, from = writeFailed).phase)
    }

    @Test
    fun `stopped and failed answers keep their text and never join history`() {
        val stopped = reduce(Ask("Q", PromptKind.FREE, false), Delta(1, "part"), Stopped(1), from = ready)
        assertEquals(Phase.READY, stopped.phase)
        assertEquals(MessageStatus.STOPPED, stopped.messages.single().status)
        assertEquals("part", stopped.messages.single().answer)
        assertTrue(stopped.history.isEmpty())
        val failed = reduce(Ask("Q", PromptKind.FREE, false), Delta(1, "part"), Failed(1, error), from = ready)
        assertEquals(Phase.ERROR, failed.phase)
        assertEquals(MessageStatus.FAILED, failed.messages.single().status)
        assertEquals("part", failed.messages.single().answer)
        assertEquals(error, failed.messages.single().error)
        assertTrue(failed.history.isEmpty())
        assertEquals(Phase.READY, reduce(Dismiss, from = failed).phase)
    }

    @Test
    fun `late events for a stopped message and asks outside ready change nothing`() {
        val stopped = reduce(Ask("Q", PromptKind.FREE, false), Stopped(1), from = ready)
        assertSame(stopped, SessionMachine.reduce(stopped, Delta(1, "late")))
        assertSame(stopped, SessionMachine.reduce(stopped, Finished(1)))
        val sending = reduce(Ask("Q", PromptKind.FREE, false), from = ready)
        assertSame(sending, SessionMachine.reduce(sending, Ask("Again", PromptKind.FREE, false)))
        assertSame(ready, SessionMachine.reduce(ready, Ask("  ", PromptKind.FREE, false)))
        val empty = SessionState()
        assertSame(empty, SessionMachine.reduce(empty, Ask("Q", PromptKind.FREE, false)))
    }

    @Test
    fun `history keeps at most HISTORY_TURNS_CAP turns and a new photo clears everything`() {
        var s = ready
        repeat(Tuning.HISTORY_TURNS_CAP + 2) { i ->
            s = reduce(Ask("Q$i", PromptKind.FREE, false), Delta(s.nextId, "A$i"), Finished(s.nextId), from = s)
        }
        assertEquals(Tuning.HISTORY_TURNS_CAP, s.history.size)
        assertEquals("Q2", s.history.first().question)
        val cleared = reduce(Cleared, from = s)
        assertEquals(Phase.NO_PHOTO, cleared.phase)
        assertTrue(cleared.messages.isEmpty() && cleared.history.isEmpty())
        val next = reduce(PhotoChosen("img2"), from = s)
        assertTrue(next.messages.isEmpty() && next.history.isEmpty())
        assertTrue(next.nextId > s.messages.last().id, "message IDs are never reused")
    }

    private fun request() =
        TurnRequest(ModelSettings("m", 100, 1000), emptyList(), "Q", SendCopy(byteArrayOf(1), 1, 1, "high"), PromptKind.FREE)

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `controller runs a fake answer, stops a hanging one, and fails a stream with no end`() =
        runTest {
            val provider = FakeProvider()
            val controller = SessionController(this)
            controller.dispatch(PhotoChosen("img"))
            controller.dispatch(PhotoReady)
            controller.dispatch(Ask("Hello", PromptKind.FREE, false))
            var end: TurnEvent? = null
            controller.run(provider.send(request(), "k"), { end = it }) { error }
            advanceUntilIdle()
            assertEquals(
                "You asked: Q",
                controller.state.value.messages
                    .single()
                    .answer,
            )
            assertTrue(end is TurnEvent.Done)
            assertEquals(1, controller.state.value.history.size)

            provider.enqueue(FakeAnswer.Hang("partial"))
            controller.dispatch(Ask("Long", PromptKind.FREE, false))
            controller.run(provider.send(request(), "k"), {}) { error }
            runCurrent()
            assertEquals(Phase.STREAMING, controller.state.value.phase)
            controller.stop()
            advanceUntilIdle()
            assertEquals(
                MessageStatus.STOPPED,
                controller.state.value.messages
                    .last()
                    .status,
            )
            assertEquals(
                "partial",
                controller.state.value.messages
                    .last()
                    .answer,
            )
            assertEquals(1, controller.state.value.history.size)

            controller.dispatch(Ask("Cut", PromptKind.FREE, false))
            controller.run(emptyFlow(), {}) { error }
            advanceUntilIdle()
            assertEquals(Phase.ERROR, controller.state.value.phase)
            assertEquals(
                error,
                controller.state.value.messages
                    .last()
                    .error,
            )

            controller.clear()
            assertEquals(Phase.NO_PHOTO, controller.state.value.phase)
        }
}
