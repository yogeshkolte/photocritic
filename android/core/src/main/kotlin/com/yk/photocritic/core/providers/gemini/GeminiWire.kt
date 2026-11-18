package com.yk.photocritic.core.providers.gemini

import com.yk.photocritic.core.providers.ModelSettings
import com.yk.photocritic.core.providers.TurnRequest
import com.yk.photocritic.core.providers.shared.SystemPrompt
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Base64

/** Gemini `streamGenerateContent` request and stream types (§3a). They stay in this package. */
internal object GeminiWire {
    val json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    @Serializable
    data class GenerateRequest(
        val systemInstruction: Content?,
        val contents: List<Content>,
        val generationConfig: GenerationConfig,
    )

    @Serializable
    data class Content(
        val role: String? = null,
        val parts: List<Part> = emptyList(),
    )

    @Serializable
    data class Part(
        val text: String? = null,
        val inlineData: InlineData? = null,
        // Thinking models stream their reasoning as parts marked thought; not shown.
        val thought: Boolean? = null,
    )

    @Serializable
    data class InlineData(
        val mimeType: String,
        val data: String,
    )

    @Serializable
    data class GenerationConfig(
        // Gemini counts thinking against this limit (Phase 0, 2026-11-18).
        val maxOutputTokens: Int,
        val mediaResolution: String? = null,
        val thinkingConfig: ThinkingConfig? = null,
    )

    @Serializable
    data class ThinkingConfig(
        val thinkingLevel: String,
    )

    @Serializable
    data class StreamChunk(
        val candidates: List<Candidate> = emptyList(),
        val promptFeedback: PromptFeedback? = null,
        val responseId: String? = null,
        val modelVersion: String? = null,
        val error: ApiError? = null,
    )

    @Serializable
    data class Candidate(
        val content: Content? = null,
        val finishReason: String? = null,
    )

    @Serializable
    data class PromptFeedback(
        val blockReason: String? = null,
    )

    @Serializable
    data class ErrorEnvelope(
        val error: ApiError? = null,
    )

    @Serializable
    data class ApiError(
        val code: Int? = null,
        val message: String? = null,
        val status: String? = null,
    )

    /** The key check's request: one short text question, no image (§3). */
    fun keyCheckBody(model: ModelSettings): String =
        json.encodeToString(
            GenerateRequest.serializer(),
            GenerateRequest(
                systemInstruction = null,
                contents = listOf(Content("user", listOf(Part(text = "Reply with OK.")))),
                generationConfig =
                    GenerationConfig(
                        KEY_CHECK_MAX_TOKENS,
                        thinkingConfig = model.thinkingLevel.takeIf { it.isNotEmpty() }?.let(::ThinkingConfig),
                    ),
            ),
        )

    private const val KEY_CHECK_MAX_TOKENS = 16

    /**
     * The image goes in the first user message, then the earlier turns, then
     * the new question (§3 step 2). History trimming arrives with file 25 in Phase 2.
     */
    fun requestBody(request: TurnRequest): String {
        val image =
            Part(inlineData = InlineData("image/jpeg", Base64.getEncoder().encodeToString(request.image.jpeg)))
        val questions = request.history.map { it.question } + request.question
        val contents = mutableListOf<Content>()
        questions.forEachIndexed { index, question ->
            val text = Part(text = question.ifBlank { request.promptKind.name.lowercase() })
            contents += Content("user", if (index == 0) listOf(image, text) else listOf(text))
            request.history.getOrNull(index)?.let { contents += Content("model", listOf(Part(text = it.answerText))) }
        }
        val resolution = request.image.imageDetail.takeIf { it.startsWith("MEDIA_RESOLUTION_") }
        val body =
            GenerateRequest(
                systemInstruction = Content(parts = listOf(Part(text = SystemPrompt.forKind(request.promptKind)))),
                contents = contents,
                generationConfig =
                    GenerationConfig(
                        request.model.maxOutputTokens,
                        resolution,
                        request.model.thinkingLevel
                            .takeIf { it.isNotEmpty() }
                            ?.let(::ThinkingConfig),
                    ),
            )
        return json.encodeToString(GenerateRequest.serializer(), body)
    }
}
