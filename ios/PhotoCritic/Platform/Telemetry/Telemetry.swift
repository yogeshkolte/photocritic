@preconcurrency import FirebaseAnalytics
@preconcurrency import FirebaseCrashlytics
import Foundation
import PhotoCriticCore

enum ParamType { case string, integer }

/// The only events the app sends (file 33), the same list as `shared/telemetry/events.json`.
enum PcEvent: String, CaseIterable {
    case sessionStart = "pc_session_start"
    case imagePrepared = "pc_image_prepared"
    case questionSent = "pc_question_sent"
    case answerReceived = "pc_answer_received"
    case requestFailed = "pc_request_failed"
    case reportSubmitted = "pc_report_submitted"
    case configFetch = "pc_config_fetch"

    var params: [String: ParamType] {
        switch self {
        case .sessionStart: strings("provider", "model").merging(ints("config_version")) { a, _ in a }
        case .imagePrepared:
            strings("source", "input_format").merging(ints("prepare_ms", "sent_long_edge_px", "sent_bytes")) { a, _ in a }
        case .questionSent: strings("provider", "model", "starter_prompt").merging(ints("turn_index")) { a, _ in a }
        case .answerReceived:
            strings("provider", "model", "finish_reason")
                .merging(ints("time_to_first_token_ms", "total_ms", "input_tokens", "output_tokens")) { a, _ in a }
        case .requestFailed: strings("provider", "model", "phase").merging(ints("http_status")) { a, _ in a }
        case .reportSubmitted: strings("provider", "model", "category", "result")
        case .configFetch: strings("result").merging(ints("config_version", "ms")) { a, _ in a }
        }
    }

    private func strings(_ names: String...) -> [String: ParamType] { Dictionary(uniqueKeysWithValues: names.map { ($0, .string) }) }
    private func ints(_ names: String...) -> [String: ParamType] { Dictionary(uniqueKeysWithValues: names.map { ($0, .integer) }) }
}

/// The Analytics and Crashlytics calls `Telemetry` makes, so tests can replace Firebase.
protocol TelemetryBackend: Sendable {
    func setCollection(_ on: Bool)
    func appInstanceID() -> String?
    /// Nil clears the user ID.
    func setUserID(_ id: String?)
    func logEvent(_ name: String, _ params: [String: Any])
}

/// Analytics and Crashlytics behind the "Share usage and crash data" switch
/// (§11, file 33). Collection is off in Info.plist; `setSharing` turns it on
/// or off and sets or clears the instance-ID user ID. `log` sends only the
/// `PcEvent` list, drops everything while sharing is off, and redacts every
/// string value.
@MainActor
final class Telemetry {
    static let maxStringParam = 100
    private let backend: any TelemetryBackend
    private(set) var sharing = false

    init(backend: any TelemetryBackend) {
        self.backend = backend
    }

    func setSharing(_ on: Bool) {
        sharing = on
        backend.setCollection(on)
        backend.setUserID(on ? backend.appInstanceID() : nil)
    }

    /// Drops the event while sharing is off. A missing or extra parameter is a bug and stops a debug build.
    func log(_ event: PcEvent, _ params: [String: Any?]) {
        assert(Set(params.keys) == Set(event.params.keys), "\(event.rawValue): parameters \(params.keys.sorted())")
        guard sharing else { return }
        var bundle: [String: Any] = [:]
        for (name, type) in event.params {
            guard let value = params[name] ?? nil else { continue }
            switch type {
            case .string: bundle[name] = String(Redact.text(String(describing: value)).prefix(Self.maxStringParam))
            case .integer: if let number = value as? Int { bundle[name] = number }
            }
        }
        backend.logEvent(event.rawValue, bundle)
    }
}

struct FirebaseTelemetryBackend: TelemetryBackend {
    func setCollection(_ on: Bool) {
        Analytics.setAnalyticsCollectionEnabled(on)
        Crashlytics.crashlytics().setCrashlyticsCollectionEnabled(on)
    }

    func appInstanceID() -> String? { Analytics.appInstanceID() }

    func setUserID(_ id: String?) {
        Analytics.setUserID(id)
        Crashlytics.crashlytics().setUserID(id ?? "")
    }

    func logEvent(_ name: String, _ params: [String: Any]) {
        Analytics.logEvent(name, parameters: params)
    }
}
