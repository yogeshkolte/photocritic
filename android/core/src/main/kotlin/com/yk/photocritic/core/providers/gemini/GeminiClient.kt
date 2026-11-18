package com.yk.photocritic.core.providers.gemini

import com.yk.photocritic.core.net.Allowlist
import com.yk.photocritic.core.net.HostNotAllowedException
import com.yk.photocritic.core.net.HttpChunk
import com.yk.photocritic.core.net.ProviderHttp
import com.yk.photocritic.core.net.ProviderRequest
import com.yk.photocritic.core.net.SseEvent
import com.yk.photocritic.core.net.SseParser
import com.yk.photocritic.core.providers.Capabilities
import com.yk.photocritic.core.providers.FinishReason
import com.yk.photocritic.core.providers.ImagePlacement
import com.yk.photocritic.core.providers.KeyCheckResult
import com.yk.photocritic.core.providers.ModelSettings
import com.yk.photocritic.core.providers.PcPhase
import com.yk.photocritic.core.providers.Provider
import com.yk.photocritic.core.providers.ProviderId
import com.yk.photocritic.core.providers.Streaming
import com.yk.photocritic.core.providers.TurnEvent
import com.yk.photocritic.core.providers.TurnRequest
import com.yk.photocritic.core.providers.shared.ToPcError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.SerializationException
import java.io.IOException

/**
 * Gemini client (design §3a, file 27). Sends one streaming request through
 * [ProviderHttp]; the key goes only in `x-goog-api-key`. Gemini sends no
 * request-ID header: the request ID is the body's `responseId`.
 */
class GeminiClient(
    private val http: ProviderHttp,
    private val baseUrl: String = Allowlist.BASE_URLS.getValue("gemini"),
) : Provider {
    override val id = ProviderId.GEMINI

    override val capabilities =
        Capabilities(
            acceptsImages = true,
            imagePlacement = ImagePlacement.ANY_TURN,
            streaming = Streaming.TOKENS,
            usageReporting = false,
            dataLeavesDevice = true,
        )

    /**
     * A minimal text-only `generateContent` call (§3): a model list does not
     * prove inference is enabled. Only the response status is read; the
     * setup screen says this uses a few tokens.
     */
    override suspend fun checkKey(
        apiKey: String,
        model: ModelSettings,
    ): KeyCheckResult {
        val request =
            ProviderRequest(
                url = "$baseUrl/v1beta/models/${model.modelId}:generateContent",
                headers = mapOf("x-goog-api-key" to apiKey),
                jsonBody = GeminiWire.keyCheckBody(model),
                requestIdHeader = null,
            )

        fun failed(
            status: Int?,
            code: String?,
            message: String,
        ) = KeyCheckResult.Failed(ToPcError.of(PcPhase.BEFORE_RESPONSE, status, code, null, message, apiKey))
        return try {
            val head = http.exchange(request).first() as HttpChunk.Head
            val body = head.errorBody ?: return KeyCheckResult.Ok
            val error = apiError(body)
            failed(head.status, error?.status, error?.message ?: "HTTP ${head.status}")
        } catch (e: CancellationException) {
            throw e
        } catch (e: HostNotAllowedException) {
            failed(null, "host-not-allowed", e.message ?: "Host not allowed.")
        } catch (e: IOException) {
            failed(null, e::class.simpleName, e.message ?: "Network error.")
        }
    }

    override fun send(
        request: TurnRequest,
        apiKey: String,
    ): Flow<TurnEvent> =
        flow {
            val state = StreamState(apiKey)
            val parser = SseParser()
            val providerRequest =
                ProviderRequest(
                    url = "$baseUrl/v1beta/models/${request.model.modelId}:streamGenerateContent?alt=sse",
                    headers = mapOf("x-goog-api-key" to apiKey),
                    jsonBody = GeminiWire.requestBody(request),
                    requestIdHeader = null,
                )
            try {
                http.exchange(providerRequest).collect { chunk ->
                    when (chunk) {
                        is HttpChunk.Head -> state.onHead(chunk, this)
                        is HttpChunk.Line -> parser.feed(chunk.text)?.let { state.onEvent(it, this) }
                    }
                }
                parser.finish()?.let { state.onEvent(it, this) }
                if (!state.finished) {
                    state.fail(this, PcPhase.MID_STREAM, null, null, "The stream ended without a finish reason.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: HostNotAllowedException) {
                state.failNetwork(this, "host-not-allowed", e)
            } catch (e: IOException) {
                state.failNetwork(this, e::class.simpleName, e)
            }
        }

    private class StreamState(
        private val apiKey: String,
    ) {
        var headSeen = false
        var finished = false
        private var requestId: String? = null
        private var servedModel: String? = null

        suspend fun onHead(
            head: HttpChunk.Head,
            out: FlowCollector<TurnEvent>,
        ) {
            headSeen = true
            val body = head.errorBody ?: return
            val error = apiError(body)
            fail(out, PcPhase.BEFORE_RESPONSE, head.status, error?.status, error?.message ?: "HTTP ${head.status}")
        }

        suspend fun onEvent(
            event: SseEvent,
            out: FlowCollector<TurnEvent>,
        ) {
            if (finished || event.data.isBlank() || event.data == "[DONE]") return
            val chunk = decode(event.data)
            if (chunk == null) {
                fail(out, PcPhase.MID_STREAM, null, "unreadable-event", "An answer event could not be read.")
                return
            }
            chunk.responseId?.let { requestId = it }
            chunk.modelVersion?.let { servedModel = it }
            val blockReason = chunk.promptFeedback?.blockReason
            when {
                chunk.error != null -> {
                    val error = chunk.error
                    fail(out, PcPhase.MID_STREAM, error.code, error.status, error.message ?: "Error event.")
                }
                blockReason != null -> fail(out, PcPhase.FINISH, null, blockReason, "The request was blocked: $blockReason.")
                else -> chunk.candidates.firstOrNull()?.let { onCandidate(it, out) }
            }
        }

        private suspend fun onCandidate(
            candidate: GeminiWire.Candidate,
            out: FlowCollector<TurnEvent>,
        ) {
            candidate.content
                ?.parts
                .orEmpty()
                .filter { it.thought != true }
                .mapNotNull { it.text }
                .filter { it.isNotEmpty() }
                .forEach { out.emit(TurnEvent.Text(it)) }
            when (val reason = candidate.finishReason) {
                null, "FINISH_REASON_UNSPECIFIED" -> Unit
                "STOP" -> done(out, FinishReason.STOP)
                "MAX_TOKENS" -> done(out, FinishReason.LENGTH)
                // SAFETY, RECITATION, BLOCKLIST, PROHIBITED_CONTENT, SPII, IMAGE_SAFETY, OTHER and new reasons.
                else -> fail(out, PcPhase.FINISH, null, reason, "The answer was stopped: $reason.")
            }
        }

        private fun decode(data: String): GeminiWire.StreamChunk? =
            try {
                GeminiWire.json.decodeFromString(GeminiWire.StreamChunk.serializer(), data)
            } catch (_: SerializationException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }

        suspend fun failNetwork(
            out: FlowCollector<TurnEvent>,
            code: String?,
            e: IOException,
        ) {
            if (finished) return
            val phase = if (headSeen) PcPhase.MID_STREAM else PcPhase.BEFORE_RESPONSE
            fail(out, phase, null, code, e.message ?: "Network error.")
        }

        private suspend fun done(
            out: FlowCollector<TurnEvent>,
            reason: FinishReason,
        ) {
            finished = true
            out.emit(TurnEvent.Done(reason, servedModel, requestId))
        }

        suspend fun fail(
            out: FlowCollector<TurnEvent>,
            phase: PcPhase,
            httpStatus: Int?,
            providerCode: String?,
            message: String,
        ) {
            finished = true
            out.emit(TurnEvent.Error(ToPcError.of(phase, httpStatus, providerCode, requestId, message, apiKey)))
        }
    }
}

private fun apiError(body: String): GeminiWire.ApiError? =
    try {
        GeminiWire.json.decodeFromString(GeminiWire.ErrorEnvelope.serializer(), body).error
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
