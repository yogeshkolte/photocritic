import Foundation
import Observation

/// The §5 state machine as a pure reducer (file 29). An event that does not
/// apply to the current state, or names a message that is no longer the one
/// being answered, leaves the state unchanged, so a late event from a
/// stopped request cannot change anything.
public enum SessionMachine {
    // One branch per arrow in the §5 diagram.
    // swiftlint:disable:next cyclomatic_complexity function_body_length
    public static func reduce(_ s: SessionState, _ e: SessionEvent) -> SessionState {
        var n = s
        switch e {
        case let .photoChosen(id):
            n = SessionState()
            n.phase = .preparing
            n.imageVersionID = id
            n.nextID = s.nextID
        case .photoReady:
            if s.phase == .preparing { n.phase = .ready }
        case let .photoFailed(error):
            if s.phase == .preparing {
                n.phase = .imageError
                n.error = error
            }
        case let .ask(question, kind, needsConsent):
            guard s.phase == .ready, !question.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return s }
            if needsConsent {
                n.phase = .awaitingConsent
                n.pending = PendingQuestion(question: question, kind: kind)
            } else {
                n = start(s, PendingQuestion(question: question, kind: kind))
            }
        case .consentAgreed:
            if s.phase == .awaitingConsent, let pending = s.pending { n = start(s, pending) }
        case .consentDeclined:
            if s.phase == .awaitingConsent {
                n.phase = .ready
                n.pending = nil
            }
        case let .consentWriteFailed(error):
            if s.phase == .awaitingConsent {
                n.phase = .error
                n.pending = nil
                n.error = error
            }
        case let .delta(id, text):
            return current(s, id) { m in
                m.answer += text
                return .streaming
            }
        case let .finished(id):
            guard let m = streamingMessage(s, id) else { return s }
            var done = m
            done.status = .done
            n.phase = .ready
            n.messages[n.messages.count - 1] = done
            let turn = NeutralTurn(question: m.question, answerText: m.answer, imageVersionID: s.imageVersionID ?? "")
            n.history = Array((s.history + [turn]).suffix(Tuning.historyTurnsCap))
        case let .failed(id, error):
            n = current(s, id) { m in
                m.status = .failed
                m.error = error
                return .error
            }
            if n != s { n.error = error }
        case let .stopped(id):
            return current(s, id) { m in
                m.status = .stopped
                return .ready
            }
        case .dismiss:
            switch s.phase {
            case .error:
                n.phase = .ready
                n.error = nil
            case .imageError:
                n = SessionState()
                n.nextID = s.nextID
            default:
                break
            }
        case .cleared:
            n = SessionState()
            n.nextID = s.nextID
        }
        return n
    }

    private static func start(_ s: SessionState, _ q: PendingQuestion) -> SessionState {
        var n = s
        n.phase = .sending
        n.pending = nil
        n.error = nil
        n.messages.append(VisibleMessage(id: s.nextID, question: q.question, promptKind: q.kind))
        n.nextID += 1
        return n
    }

    private static func streamingMessage(_ s: SessionState, _ id: Int) -> VisibleMessage? {
        guard s.answering, let last = s.messages.last, last.id == id, last.status == .streaming else { return nil }
        return last
    }

    /// Applies `change` to the message only while `id` is the one being answered; `change` returns the new phase.
    private static func current(_ s: SessionState, _ id: Int, _ change: (inout VisibleMessage) -> Phase) -> SessionState {
        guard var m = streamingMessage(s, id) else { return s }
        var n = s
        n.phase = change(&m)
        n.messages[n.messages.count - 1] = m
        return n
    }
}

/// Runs the provider stream for the streaming message (file 29). Text
/// extends it, `done` adds the turn to history, an error marks it failed and
/// keeps its text; Stop cancels the request. There is no retry (G12).
@MainActor
@Observable
public final class SessionController {
    public private(set) var state = SessionState()
    @ObservationIgnored private var task: Task<Void, Never>?

    public init() {}

    public func dispatch(_ event: SessionEvent) {
        let next = SessionMachine.reduce(state, event)
        if next != state { state = next }
    }

    /// Collects `answer` for the message just started (the state must be
    /// `sending`). `onEnd` gets the last event, or nil when the stream ended
    /// without one, which counts as a mid-stream failure.
    public func run(
        _ answer: AsyncStream<TurnEvent>,
        onEnd: @escaping @MainActor (TurnEvent?) -> Void = { _ in },
        incomplete: @escaping @MainActor () -> PcError
    ) {
        guard state.phase == .sending, let id = state.messages.last?.id else { return }
        task = Task { [weak self] in
            var end: TurnEvent?
            for await event in answer {
                guard let self, !Task.isCancelled else { return }
                switch event {
                case let .text(delta):
                    self.dispatch(.delta(messageID: id, text: delta))
                case .done:
                    self.dispatch(.finished(messageID: id))
                    end = event
                case let .error(error):
                    self.dispatch(.failed(messageID: id, error: error))
                    end = event
                }
            }
            guard let self, !Task.isCancelled else { return }
            if end == nil { self.dispatch(.failed(messageID: id, error: incomplete())) }
            onEnd(end)
        }
    }

    /// Stop (G4): cancels the request; the partial answer stays, labelled Stopped.
    public func stop() {
        guard let id = state.messages.last?.id, state.answering else { return }
        task?.cancel()
        dispatch(.stopped(messageID: id))
    }

    public func clear() {
        task?.cancel()
        dispatch(.cleared)
    }
}
