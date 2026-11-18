package com.yk.photocritic.features.session

import com.yk.photocritic.core.config.ModelConfig
import com.yk.photocritic.core.providers.PromptKind
import com.yk.photocritic.core.providers.ProviderId
import com.yk.photocritic.core.providers.TurnEvent
import com.yk.photocritic.features.image.PrepareResult
import com.yk.photocritic.platform.telemetry.PcEvent
import com.yk.photocritic.platform.telemetry.Telemetry

/** The session's Analytics events (file 33). [Telemetry] drops them while sharing is off. */
class SessionTelemetry(
    private val telemetry: Telemetry,
    private val configVersion: Int,
) {
    fun photoReady(
        provider: ProviderId,
        model: ModelConfig,
        source: String,
        result: PrepareResult.Ready,
    ) {
        telemetry.log(PcEvent.SESSION_START, mapOf("provider" to provider.wire, "model" to model.id, "config_version" to configVersion))
        telemetry.log(
            PcEvent.IMAGE_PREPARED,
            mapOf(
                "source" to source,
                "input_format" to result.inputFormat.wire,
                "prepare_ms" to result.prepareMs,
                "sent_long_edge_px" to result.copy.longEdgePx,
                "sent_bytes" to result.copy.bytes,
            ),
        )
    }

    fun questionSent(
        provider: ProviderId,
        model: ModelConfig,
        kind: PromptKind,
        turnIndex: Int,
    ) = telemetry.log(
        PcEvent.QUESTION_SENT,
        mapOf(
            "provider" to provider.wire,
            "model" to model.id,
            "starter_prompt" to kind.takeIf { it != PromptKind.FREE }?.name?.lowercase(),
            "turn_index" to turnIndex,
        ),
    )

    /** A stopped answer logs nothing. */
    fun answerEnded(
        provider: ProviderId,
        model: ModelConfig,
        end: TurnEvent?,
        firstMs: Long?,
        totalMs: Long,
    ) {
        when (end) {
            is TurnEvent.Done ->
                telemetry.log(
                    PcEvent.ANSWER_RECEIVED,
                    mapOf(
                        "provider" to provider.wire,
                        "model" to model.id,
                        "finish_reason" to end.finishReason.name.lowercase(),
                        "time_to_first_token_ms" to firstMs,
                        "total_ms" to totalMs,
                        "input_tokens" to null,
                        "output_tokens" to null,
                    ),
                )
            is TurnEvent.Error ->
                telemetry.log(
                    PcEvent.REQUEST_FAILED,
                    mapOf(
                        "provider" to provider.wire,
                        "model" to model.id,
                        "phase" to end.error.phase.wire,
                        "http_status" to end.error.httpStatus,
                    ),
                )
            else -> Unit
        }
    }
}
