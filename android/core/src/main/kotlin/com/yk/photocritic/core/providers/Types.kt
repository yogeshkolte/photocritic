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
    /** How much the model may think before answering; empty sends nothing (provider default). */
    val thinkingLevel: String = "",
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

/** Where a model needs the photo in the request (§3). */
enum class ImagePlacement { ANY_TURN, FINAL_TURN_ONLY }

/** Whether answers arrive in pieces or all at once. */
enum class Streaming { TOKENS, WHOLE }

/**
 * What a provider can do (§3), so the screens need no provider checks. An
 * on-device provider (Phase 2) reports `dataLeavesDevice = false` and skips
 * the consent sheet.
 */
data class Capabilities(
    val acceptsImages: Boolean,
    val imagePlacement: ImagePlacement,
    val streaming: Streaming,
    val usageReporting: Boolean,
    val dataLeavesDevice: Boolean,
)

/** The result of checking a key on the setup screen (§3, G10). */
sealed interface KeyCheckResult {
    data object Ok : KeyCheckResult

    data class Failed(
        val error: PcError,
    ) : KeyCheckResult
}

/**
 * A provider client (§3). `send` emits text deltas, then exactly one [TurnEvent.Done]
 * or [TurnEvent.Error]. Cancelling the collector cancels the request (Stop).
 * Availability, `prepare` and `dispose` arrive with on-device providers in Phase 2.
 */
interface Provider {
    val id: ProviderId

    val capabilities: Capabilities

    /** One small call that proves the key works for [model]; cancellable. Never retried. */
    suspend fun checkKey(
        apiKey: String,
        model: ModelSettings,
    ): KeyCheckResult

    fun send(
        request: TurnRequest,
        apiKey: String,
    ): Flow<TurnEvent>
}

/** The providers this build has a client for. OpenAI and Anthropic join in Phase 1.3. */
class ProviderRegistry(
    providers: List<Provider>,
) {
    private val byId = providers.associateBy { it.id }

    fun get(id: ProviderId): Provider? = byId[id]

    val ids: Set<ProviderId> get() = byId.keys
}
