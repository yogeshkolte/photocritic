package com.yk.photocritic.core.session

import com.yk.photocritic.core.providers.NeutralTurn
import com.yk.photocritic.core.providers.PcError
import com.yk.photocritic.core.providers.PromptKind
import com.yk.photocritic.core.providers.shared.ExtractedText
import com.yk.photocritic.core.providers.shared.Segment

/** The states of the §5 diagram. `stopped` passes straight to [READY]. */
enum class Phase { NO_PHOTO, PREPARING, IMAGE_ERROR, READY, AWAITING_CONSENT, SENDING, STREAMING, ERROR }

enum class MessageStatus { STREAMING, DONE, STOPPED, FAILED }

/** One question and its answer as shown; stable [id] so only the streaming message redraws. */
data class VisibleMessage(
    val id: Long,
    val question: String,
    val promptKind: PromptKind,
    val answer: String = "",
    val status: MessageStatus = MessageStatus.STREAMING,
    val error: PcError? = null,
) {
    val segments: List<Segment> get() = ExtractedText.parse(answer)
}

data class PendingQuestion(
    val question: String,
    val kind: PromptKind,
)

data class SessionState(
    val phase: Phase = Phase.NO_PHOTO,
    val messages: List<VisibleMessage> = emptyList(),
    /** Finished turns only (§5): stopped and failed answers are never added. */
    val history: List<NeutralTurn> = emptyList(),
    /** The question waiting for the consent sheet. */
    val pending: PendingQuestion? = null,
    /** The error shown for [Phase.ERROR] and [Phase.IMAGE_ERROR]. */
    val error: PcError? = null,
    val imageVersionId: String? = null,
    val nextId: Long = 1,
) {
    val canSend: Boolean get() = phase == Phase.READY
    val answering: Boolean get() = phase == Phase.SENDING || phase == Phase.STREAMING
    val hasPhoto: Boolean get() = phase != Phase.NO_PHOTO && phase != Phase.PREPARING && phase != Phase.IMAGE_ERROR
}

sealed interface SessionEvent {
    data class PhotoChosen(
        val imageVersionId: String,
    ) : SessionEvent

    data object PhotoReady : SessionEvent

    data class PhotoFailed(
        val error: PcError,
    ) : SessionEvent

    /** Send was tapped; [needsConsent] comes from `ConsentRules.canSend`. */
    data class Ask(
        val question: String,
        val kind: PromptKind,
        val needsConsent: Boolean,
    ) : SessionEvent

    data object ConsentAgreed : SessionEvent

    data object ConsentDeclined : SessionEvent

    /** The consent record could not be saved: the question is not sent (§5). */
    data class ConsentWriteFailed(
        val error: PcError,
    ) : SessionEvent

    data class Delta(
        val messageId: Long,
        val text: String,
    ) : SessionEvent

    data class Finished(
        val messageId: Long,
    ) : SessionEvent

    data class Failed(
        val messageId: Long,
        val error: PcError,
    ) : SessionEvent

    data class Stopped(
        val messageId: Long,
    ) : SessionEvent

    /** Dismiss on the generic error. */
    data object Dismiss : SessionEvent

    /** New photo, or provider or model change: the conversation ends. */
    data object Cleared : SessionEvent
}
