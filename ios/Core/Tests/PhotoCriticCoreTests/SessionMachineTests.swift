import Foundation
import Testing
@testable import PhotoCriticCore

/// The §5 state machine (file 29), the same cases as the Android `SessionMachineTest`.
struct SessionMachineTests {
    let error = PcError(phase: .midStream, httpStatus: nil, providerCode: "x", requestID: nil, detail: "boom")

    func reduce(_ events: SessionEvent...) -> SessionState {
        events.reduce(SessionState(), SessionMachine.reduce)
    }

    var ready: [SessionEvent] { [.photoChosen(imageVersionID: "img"), .photoReady] }

    @Test func aFinishedAnswerEntersHistory() {
        let s = (ready + [.ask(question: "What?", kind: .free, needsConsent: false), .delta(messageID: 1, text: "A "),
                          .delta(messageID: 1, text: "cat"), .finished(messageID: 1)]).reduce(SessionState(), SessionMachine.reduce)
        #expect(s.phase == .ready)
        #expect(s.messages.map(\.answer) == ["A cat"])
        #expect(s.messages[0].status == .done)
        #expect(s.history == [NeutralTurn(question: "What?", answerText: "A cat", imageVersionID: "img")])
    }

    @Test func stoppedAndFailedAnswersStayOutOfHistory() {
        var s = (ready + [.ask(question: "Q1", kind: .free, needsConsent: false), .delta(messageID: 1, text: "part"),
                          .stopped(messageID: 1)]).reduce(SessionState(), SessionMachine.reduce)
        #expect(s.phase == .ready)
        #expect(s.messages[0].status == .stopped)
        #expect(s.messages[0].answer == "part")
        s = [.ask(question: "Q2", kind: .free, needsConsent: false), .failed(messageID: 2, error: error)].reduce(s, SessionMachine.reduce)
        #expect(s.phase == .error)
        #expect(s.error == error)
        #expect(s.messages[1].status == .failed)
        #expect(s.history.isEmpty)
    }

    @Test func lateEventsAreIgnored() {
        let s = (ready + [.ask(question: "Q", kind: .free, needsConsent: false), .stopped(messageID: 1)])
            .reduce(SessionState(), SessionMachine.reduce)
        #expect(SessionMachine.reduce(s, .delta(messageID: 1, text: "late")) == s)
        #expect(SessionMachine.reduce(s, .finished(messageID: 1)) == s)
    }

    @Test func consentGate() {
        var s = (ready + [.ask(question: "Q", kind: .identify, needsConsent: true)]).reduce(SessionState(), SessionMachine.reduce)
        #expect(s.phase == .awaitingConsent)
        #expect(s.messages.isEmpty)
        let declined = SessionMachine.reduce(s, .consentDeclined)
        #expect(declined.phase == .ready)
        #expect(declined.pending == nil)
        s = SessionMachine.reduce(s, .consentAgreed)
        #expect(s.phase == .sending)
        #expect(s.messages.map(\.question) == ["Q"])
        let failed = (ready + [.ask(question: "Q", kind: .free, needsConsent: true), .consentWriteFailed(error)])
            .reduce(SessionState(), SessionMachine.reduce)
        #expect(failed.phase == .error)
        #expect(failed.messages.isEmpty)
    }

    @Test func historyIsCapped() {
        var s = ready.reduce(SessionState(), SessionMachine.reduce)
        for i in 1...(Tuning.historyTurnsCap + 3) {
            s = [.ask(question: "Q\(i)", kind: .free, needsConsent: false), .finished(messageID: i)].reduce(s, SessionMachine.reduce)
        }
        #expect(s.history.count == Tuning.historyTurnsCap)
        #expect(s.history.last?.question == "Q\(Tuning.historyTurnsCap + 3)")
    }

    @Test func photoErrorsAndClear() {
        let failed = [.photoChosen(imageVersionID: "a"), .photoFailed(error)].reduce(SessionState(), SessionMachine.reduce)
        #expect(failed.phase == .imageError)
        #expect(SessionMachine.reduce(failed, .dismiss).phase == .noPhoto)
        let cleared = (ready + [.ask(question: "Q", kind: .free, needsConsent: false), .cleared])
            .reduce(SessionState(), SessionMachine.reduce)
        #expect(cleared.phase == .noPhoto)
        #expect(cleared.messages.isEmpty)
        #expect(cleared.nextID == 2)
        #expect(SessionMachine.reduce(SessionState(), .ask(question: "Q", kind: .free, needsConsent: false)) == SessionState())
    }

    @Test func consentRules() {
        let cloud = FakeProvider().capabilities
        #expect(!ConsentRules.canSend(cloud, grantedVersion: nil, requiredVersion: 1))
        #expect(!ConsentRules.canSend(cloud, grantedVersion: 1, requiredVersion: 2))
        #expect(ConsentRules.canSend(cloud, grantedVersion: 2, requiredVersion: 2))
    }

    @MainActor @Test func controllerRunsAndStops() async throws {
        let fake = FakeProvider()
        let controller = SessionController()
        for e in ready + [.ask(question: "Hello", kind: .free, needsConsent: false)] { controller.dispatch(e) }
        let model = ModelSettings(modelID: "m", maxOutputTokens: 1, contextTokens: 1000)
        let image = SendCopy(jpeg: Data([1]), width: 1, height: 1, imageDetail: "")
        var ended: TurnEvent?
        controller.run(fake.send(TurnRequest(model: model, history: [], question: "Hello", image: image, promptKind: .free), apiKey: "k"),
                       onEnd: { ended = $0 }, incomplete: { error })
        try await waitUntil { controller.state.phase == .ready }
        #expect(controller.state.messages[0].answer == "You asked: Hello")
        #expect(ended != nil)

        fake.enqueue(.hang(partialText: "partial"))
        controller.dispatch(.ask(question: "Again", kind: .free, needsConsent: false))
        controller.run(fake.send(TurnRequest(model: model, history: [], question: "Again", image: image, promptKind: .free), apiKey: "k"),
                       incomplete: { error })
        try await waitUntil { controller.state.phase == .streaming }
        controller.stop()
        #expect(controller.state.phase == .ready)
        #expect(controller.state.messages[1].status == .stopped)
        #expect(controller.state.messages[1].answer == "partial")
    }

    @MainActor private func waitUntil(_ condition: () -> Bool) async throws {
        for _ in 0..<200 where !condition() { try await Task.sleep(for: .milliseconds(10)) }
        #expect(condition())
    }
}
