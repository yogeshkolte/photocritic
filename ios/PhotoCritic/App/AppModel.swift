import Foundation
import Observation
import PhotoCriticCore

/// Where the app is after startup (file 38).
enum AppRoute {
    case loading
    case disabled(DisabledReason)
    /// `edit` is setup opened from home; `visit` makes each opening a fresh screen.
    case setup(Snapshot, killSwitchMessage: String?, edit: Bool, visit: Int)
    case home(Snapshot)
}

enum DisabledReason: Equatable {
    /// Fetch failed, timed out, or a value is invalid (CUJ 9).
    case configUnavailable
    /// From `kill_switch.message`; empty shows the default text.
    case appDisabled(message: String)
    /// The URL may be empty; then there is no Update button.
    case updateRequired(updateURL: String)
}

/// The cold-start sequence, in the file 38 order: clean up photo files;
/// install marker and key wipe; settings; config fetch and validation;
/// kill switch once; telemetry if opted in. Runs once per process.
@MainActor
@Observable
final class AppModel {
    private(set) var route = AppRoute.loading
    @ObservationIgnored let container: AppContainer
    @ObservationIgnored private var setupVisits = 0
    /// One conversation for the process, kept across the setup screen (§5).
    @ObservationIgnored private(set) var session: SessionModel?

    init(container: AppContainer) {
        self.container = container
    }

    func start() async {
        let c = container
        c.cleanup.atLaunch()
        // The marker is written again at the next start; the app can still run.
        _ = try? await c.installMarker.ensure(appVersion: c.appVersionName)
        let settings = await c.settings.read()
        let load = await c.config.load(appVersion: c.appVersion)
        var snapshot: Snapshot?
        if case let .valid(s) = load.config { snapshot = s }
        if let snapshot {
            let provider = settings.providerID
            var hasKey = false
            if let provider { hasKey = await c.keys.get(provider) != nil }
            let saved = SavedChoice(provider: provider, modelID: provider.flatMap(settings.model(for:)), hasKey: hasKey)
            switch KillSwitch.evaluate(snapshot, saved) {
            case let .appDisabled(message): route = .disabled(.appDisabled(message: message))
            case let .updateRequired(url): route = .disabled(.updateRequired(updateURL: url))
            case let .setup(message): route = .setup(snapshot, killSwitchMessage: message, edit: false, visit: 0)
            case .home: route = .home(snapshot)
            }
        } else {
            route = .disabled(.configUnavailable)
        }
        if settings.shareUsage { c.telemetry.setSharing(true) }
        c.telemetry.log(.configFetch, ["result": load.result.rawValue, "config_version": snapshot?.configVersion, "ms": load.elapsedMs])
    }

    func open(_ next: AppRoute) { route = next }

    /// Settings opens setup in edit mode until the Settings screen arrives in Phase 1.4.
    func openSettings(_ snapshot: Snapshot) {
        setupVisits += 1
        route = .setup(snapshot, killSwitchMessage: nil, edit: true, visit: setupVisits)
    }

    func session(for snapshot: Snapshot) -> SessionModel {
        if let session { return session }
        let created = SessionModel(c: container, snapshot: snapshot)
        session = created
        return created
    }
}
