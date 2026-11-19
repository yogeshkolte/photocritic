import CoreGraphics
import Foundation
import Observation
import PhotoCriticCore

/// What the consent sheet shows (G13).
struct ConsentUI {
    let providerName: String
    let text: ConsentText
}

/// One conversation (file 37): owns the file 29 controller and connects the
/// photo, consent, key and provider. The conversation ends, and its files
/// are deleted, on New photo and on a provider or model change. Nothing is
/// restored after the process ends (§5).
@MainActor
@Observable
final class SessionModel {
    let controller = SessionController()
    /// The original photo for the screen, or nil while it loads or when there is none.
    private(set) var photo: CGImage?

    @ObservationIgnored private let c: AppContainer
    @ObservationIgnored private let snapshot: Snapshot
    @ObservationIgnored private let events: SessionTelemetry
    @ObservationIgnored private var record: PhotoRecord?
    @ObservationIgnored private var providerID: ProviderID?
    @ObservationIgnored private var model: ModelConfig?
    @ObservationIgnored private var selectionLoaded: Task<Void, Never>?

    var state: SessionState { controller.state }
    var online: Bool { c.connectivity.online }
    var bannerMessage: String { snapshot.killSwitch.bannerMessage }
    var appPolicyURL: String { snapshot.values.privacyPolicy.url }

    init(c: AppContainer, snapshot: Snapshot) {
        self.c = c
        self.snapshot = snapshot
        events = SessionTelemetry(telemetry: c.telemetry, configVersion: snapshot.configVersion)
        selectionLoaded = Task { await refreshSelection() }
    }

    /// Reads the saved provider and model; called at start and after setup saves.
    func refreshSelection() async {
        let settings = await c.settings.read()
        providerID = settings.providerID
        model = providerID.flatMap { p in settings.model(for: p).flatMap { snapshot.model(p, $0) } }
    }

    var consent: ConsentUI? {
        providerID.map { ConsentUI(providerName: snapshot.provider($0).displayName, text: snapshot.consentText($0)) }
    }

    /// Picked or captured photo data. `source` is `picker` or `camera`.
    func photoChosen(_ data: Data, source: String, displayMaxPx: Int) {
        endConversation()
        let url: URL
        do {
            url = try c.cleanup.storeOriginal(data)
        } catch {
            controller.dispatch(.photoChosen(imageVersionID: UUID().uuidString))
            controller.dispatch(.photoFailed(localError("store-failed")))
            return
        }
        let next = PhotoRecord(original: url)
        record = next
        controller.dispatch(.photoChosen(imageVersionID: next.imageVersionID))
        Task {
            photo = await c.imagePipeline.decodeForDisplay(url, maxLongEdgePx: displayMaxPx)
            await prepare(next, source: source)
        }
    }

    private func prepare(_ photo: PhotoRecord, source: String) async {
        await selectionLoaded?.value
        guard let p = providerID, let m = model else {
            return controller.dispatch(.photoFailed(localError("no model selected")))
        }
        let result = await c.imagePipeline.prepare(
            photo.original, targetLongEdgePx: m.maxLongEdgePx, maxBytes: m.maxBytes, detail: m.imageDetail
        )
        switch result {
        case let .ready(copy, format, ms):
            guard record?.imageVersionID == photo.imageVersionID else { return c.cleanup.replaced(copy) }
            record?.sendCopy = copy
            controller.dispatch(.photoReady)
            events.photoReady(provider: p, model: m, source: source, format: format, prepareMs: ms, copy: copy)
        case let .failed(reason, _):
            if record?.imageVersionID == photo.imageVersionID { controller.dispatch(.photoFailed(localError(reason))) }
        }
    }

    /// Send, or a starter button. The consent sheet comes first when the rules need it (G13).
    func ask(_ question: String, kind: PromptKind) {
        Task {
            await selectionLoaded?.value
            guard let p = providerID, let provider = c.providers.get(p) else { return }
            let current = await c.consent.current(p)
            let granted = current?.decision == ConsentStore.granted ? current?.consentTextVersion : nil
            let required = snapshot.consentText(p).version
            let needsConsent = !ConsentRules.canSend(provider.capabilities, grantedVersion: granted, requiredVersion: required)
            controller.dispatch(.ask(question: question, kind: kind, needsConsent: needsConsent))
            if !needsConsent { await startAnswer(provider) }
        }
    }

    /// "Agree and send": the answer starts only after the record is saved (§5).
    func agreeConsent() {
        guard let p = providerID, let provider = c.providers.get(p) else { return }
        Task {
            if await c.consent.record(p, version: snapshot.consentText(p).version, granted: true) {
                controller.dispatch(.consentAgreed)
                await startAnswer(provider)
            } else {
                controller.dispatch(.consentWriteFailed(localError("consent-write")))
            }
        }
    }

    /// "Not now": nothing is recorded; the question stays in the composer.
    func declineConsent() { controller.dispatch(.consentDeclined) }

    func stop() { controller.stop() }

    func dismiss() { controller.dispatch(.dismiss) }

    /// New photo, after the confirmation when a question was asked (G1, CUJ 5).
    func newPhoto() { endConversation() }

    private func startAnswer(_ provider: any Provider) async {
        let state = controller.state
        guard let message = state.messages.last, let m = model else { return }
        let key = await c.keys.get(provider.id)
        let image = try? record?.sendCopy?.load()
        guard let key, let image else {
            let missing = localError(key == nil ? "no key" : "send copy missing")
            return controller.run(AsyncStream { $0.yield(.error(missing)); $0.finish() }) { missing }
        }
        events.questionSent(provider: provider.id, model: m, kind: message.promptKind, turnIndex: state.history.count)
        let start = ContinuousClock.now
        let stream = provider.send(
            TurnRequest(
                model: m.settings, history: state.history, question: message.question, image: image, promptKind: message.promptKind
            ),
            apiKey: key
        )
        let first = FirstText()
        // Times the first text before the controller sees it.
        let timed = AsyncStream<TurnEvent> { continuation in
            let task = Task { @MainActor in
                for await event in stream {
                    if first.ms == nil, case .text = event { first.ms = Int((ContinuousClock.now - start) / .milliseconds(1)) }
                    continuation.yield(event)
                }
                continuation.finish()
            }
            continuation.onTermination = { _ in task.cancel() }
        }
        let events = events
        let providerID = provider.id
        controller.run(timed, onEnd: { end in
            events.answerEnded(provider: providerID, model: m, end: end, firstMs: first.ms,
                               totalMs: Int((ContinuousClock.now - start) / .milliseconds(1)))
        }, incomplete: {
            PcError(
                phase: .midStream, httpStatus: nil, providerCode: nil, requestID: nil, detail: "The answer ended without a finish reason."
            )
        })
    }

    private func localError(_ detail: String) -> PcError {
        PcError(phase: .beforeResponse, httpStatus: nil, providerCode: "local", requestID: nil, detail: detail)
    }

    private func endConversation() {
        controller.clear()
        if let record { c.cleanup.conversationEnded(record) }
        record = nil
        photo = nil
    }
}

/// When the first text of an answer arrived, for `time_to_first_token_ms`.
@MainActor
private final class FirstText {
    var ms: Int?
}

/// The session's Analytics events (file 33). `Telemetry` drops them while sharing is off.
@MainActor
struct SessionTelemetry {
    let telemetry: Telemetry
    let configVersion: Int

    func photoReady(provider: ProviderID, model: ModelConfig, source: String, format: InputFormat, prepareMs: Int, copy: SendCopyFile) {
        telemetry.log(.sessionStart, ["provider": provider.rawValue, "model": model.id, "config_version": configVersion])
        telemetry.log(.imagePrepared, [
            "source": source, "input_format": format.rawValue, "prepare_ms": prepareMs,
            "sent_long_edge_px": copy.longEdgePx, "sent_bytes": copy.bytes,
        ])
    }

    func questionSent(provider: ProviderID, model: ModelConfig, kind: PromptKind, turnIndex: Int) {
        telemetry.log(.questionSent, [
            "provider": provider.rawValue, "model": model.id,
            "starter_prompt": kind == .free ? nil : kind.rawValue, "turn_index": turnIndex,
        ])
    }

    /// A stopped answer logs nothing.
    func answerEnded(provider: ProviderID, model: ModelConfig, end: TurnEvent?, firstMs: Int?, totalMs: Int) {
        switch end {
        case let .done(reason, _, _)?:
            telemetry.log(.answerReceived, [
                "provider": provider.rawValue, "model": model.id, "finish_reason": reason == .stop ? "stop" : "length",
                "time_to_first_token_ms": firstMs, "total_ms": totalMs, "input_tokens": nil, "output_tokens": nil,
            ])
        case let .error(error)?:
            telemetry.log(.requestFailed, [
                "provider": provider.rawValue, "model": model.id, "phase": error.phase.rawValue, "http_status": error.httpStatus,
            ])
        default:
            break
        }
    }
}
