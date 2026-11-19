import Foundation

/// One scripted answer for `FakeProvider`.
public enum FakeAnswer: Sendable {
    case reply(String)
    /// The partial text, if any, arrives before the error.
    case fail(PcError, partialText: String = "")
    /// Sends the partial text and then waits until cancelled (Stop).
    case hang(partialText: String = "")
}

/// A scripted provider for tests and the Debug fake mode (file 28). It
/// answers final-turn-only and whole (one text event), reports no usage, and
/// says data leaves the device, so the consent sheet still runs. Without a
/// script it echoes the question.
public final class FakeProvider: Provider, @unchecked Sendable {
    public static let rejectedKey = "bad-key"

    public let id: ProviderID
    public let capabilities = Capabilities(
        acceptsImages: true, imagePlacement: .finalTurnOnly, streaming: .whole, usageReporting: false, dataLeavesDevice: true
    )
    private let delay: Duration
    private let lock = NSLock()
    private var answers: [FakeAnswer] = []
    private var sent: [TurnRequest] = []

    public init(id: ProviderID = .gemini, delay: Duration = .zero) {
        self.id = id
        self.delay = delay
    }

    /// Every request sent, in order.
    public var requests: [TurnRequest] { lock.withLock { sent } }

    public func enqueue(_ next: FakeAnswer...) {
        lock.withLock { answers.append(contentsOf: next) }
    }

    public func checkKey(_ apiKey: String, model: ModelSettings) async -> KeyCheckResult {
        try? await Task.sleep(for: delay)
        guard apiKey != Self.rejectedKey else {
            return .failed(PcError(
                phase: .beforeResponse, httpStatus: 401, providerCode: "UNAUTHENTICATED", requestID: nil, detail: "Key rejected."
            ))
        }
        return .ok
    }

    public func send(_ request: TurnRequest, apiKey: String) -> AsyncStream<TurnEvent> {
        let (answer, count) = lock.withLock { () -> (FakeAnswer, Int) in
            sent.append(request)
            let next = answers.isEmpty ? FakeAnswer.reply("You asked: \(request.question)") : answers.removeFirst()
            return (next, sent.count)
        }
        let delay = delay
        return AsyncStream { continuation in
            let task = Task {
                try? await Task.sleep(for: delay)
                switch answer {
                case let .reply(text):
                    continuation.yield(.text(text))
                    continuation.yield(.done(.stop, servedModel: request.model.modelID, requestID: "fake-\(count)"))
                case let .fail(error, partial):
                    if !partial.isEmpty { continuation.yield(.text(partial)) }
                    continuation.yield(.error(error))
                case let .hang(partial):
                    if !partial.isEmpty { continuation.yield(.text(partial)) }
                    while !Task.isCancelled { try? await Task.sleep(for: .seconds(3600)) }
                }
                continuation.finish()
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }
}
