package com.yk.photocritic.core.providers.fake

import com.yk.photocritic.core.providers.Capabilities
import com.yk.photocritic.core.providers.FinishReason
import com.yk.photocritic.core.providers.ImagePlacement
import com.yk.photocritic.core.providers.KeyCheckResult
import com.yk.photocritic.core.providers.ModelSettings
import com.yk.photocritic.core.providers.PcError
import com.yk.photocritic.core.providers.PcPhase
import com.yk.photocritic.core.providers.Provider
import com.yk.photocritic.core.providers.ProviderId
import com.yk.photocritic.core.providers.Streaming
import com.yk.photocritic.core.providers.TurnEvent
import com.yk.photocritic.core.providers.TurnRequest
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** One scripted answer for [FakeProvider]. */
sealed interface FakeAnswer {
    data class Reply(
        val text: String,
    ) : FakeAnswer

    /** [partialText], if any, arrives before the error. */
    data class Fail(
        val error: PcError,
        val partialText: String = "",
    ) : FakeAnswer

    /** Sends [partialText] and then waits until cancelled (Stop). */
    data class Hang(
        val partialText: String = "",
    ) : FakeAnswer
}

/**
 * A scripted provider for tests and the devDebug fake container (file 28).
 * It answers final-turn-only and whole (one text event), reports no usage,
 * and says data leaves the device, so the consent sheet still runs. Without
 * a script it echoes the question.
 */
class FakeProvider(
    override val id: ProviderId = ProviderId.GEMINI,
    private val delayMs: Long = 0,
    private val rejectedKey: String = REJECTED_KEY,
) : Provider {
    private val answers = ArrayDeque<FakeAnswer>()

    /** Every request sent, in order. */
    val requests = mutableListOf<TurnRequest>()

    override val capabilities =
        Capabilities(
            acceptsImages = true,
            imagePlacement = ImagePlacement.FINAL_TURN_ONLY,
            streaming = Streaming.WHOLE,
            usageReporting = false,
            dataLeavesDevice = true,
        )

    fun enqueue(vararg next: FakeAnswer) {
        answers.addAll(next)
    }

    override suspend fun checkKey(
        apiKey: String,
        model: ModelSettings,
    ): KeyCheckResult {
        delay(delayMs)
        return if (apiKey == rejectedKey) {
            KeyCheckResult.Failed(PcError(PcPhase.BEFORE_RESPONSE, HTTP_UNAUTHORIZED, "UNAUTHENTICATED", null, "Key rejected."))
        } else {
            KeyCheckResult.Ok
        }
    }

    override fun send(
        request: TurnRequest,
        apiKey: String,
    ): Flow<TurnEvent> =
        flow {
            requests += request
            val answer = answers.removeFirstOrNull() ?: FakeAnswer.Reply("You asked: ${request.question}")
            delay(delayMs)
            when (answer) {
                is FakeAnswer.Reply -> {
                    emit(TurnEvent.Text(answer.text))
                    emit(TurnEvent.Done(FinishReason.STOP, request.model.modelId, "fake-${requests.size}"))
                }
                is FakeAnswer.Fail -> {
                    if (answer.partialText.isNotEmpty()) emit(TurnEvent.Text(answer.partialText))
                    emit(TurnEvent.Error(answer.error))
                }
                is FakeAnswer.Hang -> {
                    if (answer.partialText.isNotEmpty()) emit(TurnEvent.Text(answer.partialText))
                    awaitCancellation()
                }
            }
        }

    companion object {
        const val REJECTED_KEY = "bad-key"
        private const val HTTP_UNAUTHORIZED = 401
    }
}
