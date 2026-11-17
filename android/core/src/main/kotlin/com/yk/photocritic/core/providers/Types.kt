package com.yk.photocritic.core.providers

import kotlinx.coroutines.flow.Flow

/** Provider-neutral types (design §3 and §7). Nothing vendor-specific leaves a client. */
enum class ProviderId(
    val wire: String,
) {
    OPENAI("openai"),
    ANTHROPIC("anthropic"),
    GEMINI("gemini"),
}

enum class PromptKind(
    val fileName: String,
) {
    IDENTIFY("identify.txt"),
    EXPLAIN("explain.txt"),
    EXTRACT("extract.txt"),
    FREE("free.txt"),
}

/** A finished turn; stopped and failed answers never become one (§5). */
data class NeutralTurn(
    val question: String,
    val answerText: String,
    val imageVersionId: String,
)

/** The JPEG sent to the model (§6) and the model's detail or resolution setting. */
class SendCopy(
    val jpeg: ByteArray,
    val width: Int,
    val height: Int,
    val imageDetail: String,
)

/** Per-model values from the Remote Config `models` entry (§8). */
data class ModelSettings(
    val modelId: String,
    val maxOutputTokens: Int,
    val contextTokens: Int,
)

class TurnRequest(
    val model: ModelSettings,
    val history: List<NeutralTurn>,
    val question: String,
    val image: SendCopy,
    val promptKind: PromptKind,
)

enum class FinishReason { STOP, LENGTH }

enum class PcPhase(
    val wire: String,
) {
    BEFORE_RESPONSE("before-response"),
    MID_STREAM("mid-stream"),
    FINISH("finish"),
}

/** Every failure, shown as the one generic message with these details (G12, §7). */
data class PcError(
    val phase: PcPhase,
    val httpStatus: Int?,
    val providerCode: String?,
    val requestId: String?,
    /** Redacted, at most 200 characters. */
    val detail: String,
)

sealed interface TurnEvent {
    data class Text(
        val delta: String,
    ) : TurnEvent

    data class Done(
        val finishReason: FinishReason,
        val servedModel: String?,
        val requestId: String?,
    ) : TurnEvent

    data class Error(
        val error: PcError,
    ) : TurnEvent
}

/**
 * A provider client (§3). `send` emits text deltas, then exactly one [TurnEvent.Done]
 * or [TurnEvent.Error]. Cancelling the collector cancels the request (Stop).
 * Availability, key checks and capabilities arrive with the Phase 2 interface (file 24).
 */
interface Provider {
    val id: ProviderId

    fun send(
        request: TurnRequest,
        apiKey: String,
    ): Flow<TurnEvent>
}
