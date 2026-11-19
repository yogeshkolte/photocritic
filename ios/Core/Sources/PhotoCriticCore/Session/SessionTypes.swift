import Foundation

/// The states of the §5 diagram. `stopped` passes straight to `ready`.
public enum Phase: Sendable { case noPhoto, preparing, imageError, ready, awaitingConsent, sending, streaming, error }

public enum MessageStatus: Sendable { case streaming, done, stopped, failed }

/// One question and its answer as shown; stable `id` so only the streaming message redraws.
public struct VisibleMessage: Equatable, Sendable, Identifiable {
    public let id: Int
    public let question: String
    public let promptKind: PromptKind
    public var answer: String = ""
    public var status: MessageStatus = .streaming
    public var error: PcError?

    public var segments: [Segment] { ExtractedText.parse(answer) }
}

public struct PendingQuestion: Equatable, Sendable {
    public let question: String
    public let kind: PromptKind
}

public struct SessionState: Equatable, Sendable {
    public var phase: Phase = .noPhoto
    public var messages: [VisibleMessage] = []
    /// Finished turns only (§5): stopped and failed answers are never added.
    public var history: [NeutralTurn] = []
    /// The question waiting for the consent sheet.
    public var pending: PendingQuestion?
    /// The error shown for `.error` and `.imageError`.
    public var error: PcError?
    public var imageVersionID: String?
    public var nextID = 1

    public init() {}

    public var canSend: Bool { phase == .ready }
    public var answering: Bool { phase == .sending || phase == .streaming }
    public var hasPhoto: Bool { ![.noPhoto, .preparing, .imageError].contains(phase) }
}

public enum SessionEvent: Sendable {
    case photoChosen(imageVersionID: String)
    case photoReady
    case photoFailed(PcError)
    /// Send was tapped; `needsConsent` comes from `ConsentRules.canSend`.
    case ask(question: String, kind: PromptKind, needsConsent: Bool)
    case consentAgreed
    case consentDeclined
    /// The consent record could not be saved: the question is not sent (§5).
    case consentWriteFailed(PcError)
    case delta(messageID: Int, text: String)
    case finished(messageID: Int)
    case failed(messageID: Int, error: PcError)
    case stopped(messageID: Int)
    /// Dismiss on the generic error.
    case dismiss
    /// New photo, or provider or model change: the conversation ends.
    case cleared
}
