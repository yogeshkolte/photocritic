import Foundation

/// Gemini `streamGenerateContent` request and stream types (§3a). They stay in this folder.
enum GeminiWire {
    struct GenerateRequest: Encodable {
        let systemInstruction: Content?
        let contents: [Content]
        let generationConfig: GenerationConfig
    }

    struct Content: Codable {
        var role: String?
        var parts: [Part] = []
    }

    struct Part: Codable {
        var text: String?
        var inlineData: InlineData?
        // Thinking models stream their reasoning as parts marked thought; not shown.
        var thought: Bool?
    }

    struct InlineData: Codable {
        let mimeType: String
        let data: String
    }

    struct GenerationConfig: Encodable {
        // Gemini counts thinking against this limit (Phase 0, 2026-11-18).
        let maxOutputTokens: Int
        let mediaResolution: String?
        let thinkingConfig: ThinkingConfig?
    }

    struct ThinkingConfig: Encodable {
        let thinkingLevel: String
    }

    struct StreamChunk: Decodable {
        var candidates: [Candidate]?
        var promptFeedback: PromptFeedback?
        var responseId: String?
        var modelVersion: String?
        var error: APIError?
    }

    struct Candidate: Decodable {
        var content: Content?
        var finishReason: String?
    }

    struct PromptFeedback: Decodable {
        var blockReason: String?
    }

    struct ErrorEnvelope: Decodable {
        var error: APIError?
    }

    struct APIError: Decodable {
        var code: Int?
        var message: String?
        var status: String?
    }

    /// The image goes in the first user message, then the earlier turns, then
    /// the new question (§3 step 2). History trimming arrives in Phase 2.
    static func requestBody(_ request: TurnRequest) throws -> Data {
        let image = Part(inlineData: InlineData(mimeType: "image/jpeg", data: request.image.jpeg.base64EncodedString()))
        let questions = request.history.map(\.question) + [request.question]
        var contents: [Content] = []
        for (index, question) in questions.enumerated() {
            let text = Part(text: question.trimmingCharacters(in: .whitespaces).isEmpty ? request.promptKind.rawValue : question)
            contents.append(Content(role: "user", parts: index == 0 ? [image, text] : [text]))
            if index < request.history.count {
                contents.append(Content(role: "model", parts: [Part(text: request.history[index].answerText)]))
            }
        }
        let resolution = request.image.imageDetail.hasPrefix("MEDIA_RESOLUTION_") ? request.image.imageDetail : nil
        let body = GenerateRequest(
            systemInstruction: Content(parts: [Part(text: SystemPrompt.forKind(request.promptKind))]),
            contents: contents,
            generationConfig: GenerationConfig(
                maxOutputTokens: request.model.maxOutputTokens,
                mediaResolution: resolution,
                thinkingConfig: request.model.thinkingLevel.isEmpty ? nil : ThinkingConfig(thinkingLevel: request.model.thinkingLevel)
            )
        )
        return try JSONEncoder().encode(body)
    }

    /// The key check (§3): a text-only request with a tiny answer budget.
    static func keyCheckBody(_ model: ModelSettings) throws -> Data {
        let body = GenerateRequest(
            systemInstruction: nil,
            contents: [Content(role: "user", parts: [Part(text: "Reply with OK.")])],
            generationConfig: GenerationConfig(
                maxOutputTokens: keyCheckMaxTokens,
                mediaResolution: nil,
                thinkingConfig: model.thinkingLevel.isEmpty ? nil : ThinkingConfig(thinkingLevel: model.thinkingLevel)
            )
        )
        return try JSONEncoder().encode(body)
    }

    static let keyCheckMaxTokens = 16
}
