import Foundation

/// The provider, model and key state saved on the device, read at cold start.
public struct SavedChoice: Sendable {
    public let provider: ProviderID?
    public let modelID: String?
    public let hasKey: Bool

    public init(provider: ProviderID?, modelID: String?, hasKey: Bool) {
        self.provider = provider
        self.modelID = modelID
        self.hasKey = hasKey
    }
}

/// Where the app goes after the config is loaded (file 38).
public enum StartRoute: Equatable, Sendable {
    /// `appDisabled`; the message may be empty, and the screen then shows its own text.
    case appDisabled(message: String)
    /// The app version is below `minSupportedAppVersion`; the URL may be empty.
    case updateRequired(updateURL: String)
    /// The message is set when the saved provider or model is hidden (G18).
    case setup(killSwitchMessage: String?)
    case home
}

/// Evaluated once at cold start; there is no live update (G18, design §8 step 5).
public enum KillSwitch {
    public static let defaultMessage = "The model you used is no longer available. Pick another one."

    public static func evaluate(_ snapshot: Snapshot, _ saved: SavedChoice) -> StartRoute {
        let ks = snapshot.killSwitch
        if ks.appDisabled { return .appDisabled(message: ks.message) }
        if snapshot.appVersion < ks.minSupportedAppVersion { return .updateRequired(updateURL: ks.updateURL) }
        let model = saved.provider.flatMap { p in saved.modelID.flatMap { snapshot.model(p, $0) } }
        var hidden = false
        if let provider = saved.provider {
            hidden = !snapshot.isProviderEnabled(provider) ||
                (saved.modelID != nil && (model.map { !snapshot.isModelVisible($0) } ?? true))
        }
        if hidden { return .setup(killSwitchMessage: ks.message.isEmpty ? defaultMessage : ks.message) }
        if model == nil || !saved.hasKey { return .setup(killSwitchMessage: nil) }
        return .home
    }
}
