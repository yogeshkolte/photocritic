package com.yk.photocritic.core.session

import com.yk.photocritic.core.config.Tuning
import com.yk.photocritic.core.providers.NeutralTurn
import com.yk.photocritic.core.providers.PcError
import com.yk.photocritic.core.providers.TurnEvent
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The §5 state machine as a pure reducer (file 29). An event that does not
 * apply to the current state, or names a message that is no longer the one
 * being answered, leaves the state unchanged, so a late event from a
 * stopped request cannot change anything.
 */
object SessionMachine {
    @Suppress("CyclomaticComplexMethod") // One branch per arrow in the §5 diagram.
    fun reduce(
        s: SessionState,
        e: SessionEvent,
    ): SessionState =
        when (e) {
            is PhotoChosen -> SessionState(phase = Phase.PREPARING, imageVersionId = e.imageVersionId, nextId = s.nextId)
            PhotoReady -> if (s.phase == Phase.PREPARING) s.copy(phase = Phase.READY) else s
            is PhotoFailed -> if (s.phase == Phase.PREPARING) s.copy(phase = Phase.IMAGE_ERROR, error = e.error) else s
            is Ask ->
                when {
                    s.phase != Phase.READY || e.question.isBlank() -> s
                    e.needsConsent -> s.copy(phase = Phase.AWAITING_CONSENT, pending = PendingQuestion(e.question, e.kind))
                    else -> start(s, PendingQuestion(e.question, e.kind))
                }
            ConsentAgreed -> if (s.phase == Phase.AWAITING_CONSENT && s.pending != null) start(s, s.pending) else s
            ConsentDeclined -> if (s.phase == Phase.AWAITING_CONSENT) s.copy(phase = Phase.READY, pending = null) else s
            is ConsentWriteFailed ->
                if (s.phase == Phase.AWAITING_CONSENT) s.copy(phase = Phase.ERROR, pending = null, error = e.error) else s
            is Delta ->
                current(s, e.messageId) {
                    s.copy(
                        phase = Phase.STREAMING,
                        messages =
                            s.messages.dropLast(1) + it.copy(answer = it.answer + e.text),
                    )
                }
            is Finished -> current(s, e.messageId) { finish(s, it) }
            is Failed ->
                current(s, e.messageId) {
                    s.copy(
                        phase = Phase.ERROR,
                        error = e.error,
                        messages =
                            s.messages.dropLast(1) + it.copy(status = MessageStatus.FAILED, error = e.error),
                    )
                }
            is Stopped ->
                current(s, e.messageId) {
                    s.copy(
                        phase = Phase.READY,
                        messages =
                            s.messages.dropLast(1) + it.copy(status = MessageStatus.STOPPED),
                    )
                }
            Dismiss ->
                when (s.phase) {
                    Phase.ERROR -> s.copy(phase = Phase.READY, error = null)
                    Phase.IMAGE_ERROR -> SessionState(nextId = s.nextId)
                    else -> s
                }
            Cleared -> SessionState(nextId = s.nextId)
        }

    private fun start(
        s: SessionState,
        q: PendingQuestion,
    ) = s.copy(
        phase = Phase.SENDING,
        pending = null,
        error = null,
        messages = s.messages + VisibleMessage(s.nextId, q.question, q.kind),
        nextId = s.nextId + 1,
    )

    private fun finish(
        s: SessionState,
        m: VisibleMessage,
    ): SessionState {
        val turn = NeutralTurn(m.question, m.answer, s.imageVersionId.orEmpty())
        return s.copy(
            phase = Phase.READY,
            messages = s.messages.dropLast(1) + m.copy(status = MessageStatus.DONE),
            history = (s.history + turn).takeLast(Tuning.HISTORY_TURNS_CAP),
        )
    }

    /** Applies [change] only while [id] is the message being answered. */
    private inline fun current(
        s: SessionState,
        id: Long,
        change: (VisibleMessage) -> SessionState,
    ): SessionState {
        val last = s.messages.lastOrNull()?.takeIf { it.id == id && it.status == MessageStatus.STREAMING }
        return if (s.answering && last != null) change(last) else s
    }
}

/**
 * Runs the provider stream for the streaming message (file 29). Text
 * extends it, `done` adds the turn to history, an error marks it failed and
 * keeps its text; Stop cancels the request. There is no retry (G12).
 */
class SessionController(
    private val scope: CoroutineScope,
) {
    private val mutable = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = mutable.asStateFlow()
    private var job: Job? = null

    fun dispatch(event: SessionEvent) = mutable.update { SessionMachine.reduce(it, event) }

    /**
     * Collects [answer] for the message just started (the state must be
     * `SENDING`). [onEnd] gets the last event, or null when the stream
     * ended without one, which counts as a mid-stream failure.
     */
    fun run(
        answer: Flow<TurnEvent>,
        onEnd: (TurnEvent?) -> Unit = {},
        incomplete: () -> PcError,
    ) {
        val id =
            state.value.messages
                .lastOrNull()
                ?.takeIf { state.value.phase == Phase.SENDING }
                ?.id ?: return
        job =
            scope.launch {
                var end: TurnEvent? = null
                answer.collect { event ->
                    when (event) {
                        is TurnEvent.Text -> dispatch(Delta(id, event.delta))
                        is TurnEvent.Done -> dispatch(Finished(id)).also { end = event }
                        is TurnEvent.Error -> dispatch(Failed(id, event.error)).also { end = event }
                    }
                }
                if (end == null) dispatch(Failed(id, incomplete()))
                onEnd(end)
            }
    }

    /** Stop (G4): cancels the request; the partial answer stays, labelled Stopped. */
    fun stop() {
        val id =
            state.value.messages
                .lastOrNull()
                ?.id ?: return
        if (!state.value.answering) return
        job?.cancel()
        dispatch(Stopped(id))
    }

    fun clear() {
        job?.cancel()
        dispatch(Cleared)
    }
}
