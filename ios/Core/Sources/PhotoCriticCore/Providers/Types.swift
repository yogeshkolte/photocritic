import Foundation

// Provider-neutral types (design §3 and §7). Nothing vendor-specific leaves a client.

public enum ProviderID: String, Sendable, CaseIterable {
    case openai, anthropic, gemini
}

public enum PromptKind: String, Sendable, CaseIterable {
    case identify, explain, extract, free

    var fileName: String { rawValue }
}

/// A finished turn; stopped and failed answers never become one (§5).
public struct NeutralTurn: Equatable, Sendable {
    public let question: String
    public let answerText: String
    public let imageVersionID: String

    public init(question: String, answerText: String, imageVersionID: String) {
        self.question = question
        self.answerText = answerText
        self.imageVersionID = imageVersionID
    }
}

/// The JPEG sent to the model (§6) and the model's detail or resolution setting.
public struct SendCopy: Sendable {
    public let jpeg: Data
    public let width: Int
    public let height: Int
    public let imageDetail: String

    public init(jpeg: Data, width: Int, height: Int, imageDetail: String) {
        self.jpeg = jpeg
        self.width = width
        self.height = height
        self.imageDetail = imageDetail
    }
}

/// Per-model values from the Remote Config `models` entry (§8).
public struct ModelSettings: Equatable, Sendable {
    public let modelID: String
    public let maxOutputTokens: Int
    public let contextTokens: Int

    public init(modelID: String, maxOutputTokens: Int, contextTokens: Int) {
        self.modelID = modelID
        self.maxOutputTokens = maxOutputTokens
        self.contextTokens = contextTokens
    }
}

public struct TurnRequest: Sendable {
    public let model: ModelSettings
    public let history: [NeutralTurn]
    public let question: String
    public let image: SendCopy
    public let promptKind: PromptKind

    public init(model: ModelSettings, history: [NeutralTurn], question: String, image: SendCopy, promptKind: PromptKind) {
        self.model = model
        self.history = history
        self.question = question
        self.image = image
        self.promptKind = promptKind
    }
}

public enum FinishReason: Sendable { case stop, length }

public enum PcPhase: String, Sendable {
    case beforeResponse = "before-response"
    case midStream = "mid-stream"
    case finish
}

/// Every failure, shown as the one generic message with these details (G12, §7).
public struct PcError: Equatable, Sendable {
    public let phase: PcPhase
    public let httpStatus: Int?
    public let providerCode: String?
    public let requestID: String?
    /// Redacted, at most 200 characters.
    public let detail: String
}

public enum TurnEvent: Equatable, Sendable {
    case text(String)
    case done(FinishReason, servedModel: String?, requestID: String?)
    case error(PcError)
}

/// A provider client (§3). `send` yields text deltas, then exactly one `.done` or
/// `.error`. Cancelling the consuming task cancels the request (Stop).
/// Availability, key checks and capabilities arrive with the Phase 2 interface.
public protocol Provider: Sendable {
    var id: ProviderID { get }
    func send(_ request: TurnRequest, apiKey: String) -> AsyncStream<TurnEvent>
}
