import Foundation

/// How a model is labelled in the model list (file 26).
public enum ModelTag: Equatable, Sendable {
    case none
    case preview
    case endingSoon(replacementName: String)
}

public struct ModelChoice: Equatable, Sendable {
    public let model: ModelConfig
    public let tag: ModelTag
}

/// The validated config for this session (design §8 step 4). Built once at
/// cold start; screens read only this. Applies the file 26 display rules.
public struct Snapshot: Sendable {
    public let values: RemoteConfigValues
    public let appVersion: AppVersion

    public var configVersion: Int { values.meta.configVersion }
    public var killSwitch: KillSwitchConfig { values.killSwitch }

    public func provider(_ id: ProviderID) -> ProviderConfig { values.providers[id]! }

    public func consentText(_ id: ProviderID) -> ConsentText { values.consentTexts[id]! }

    /// Disabled when `enabled` is false or the kill switch lists it.
    public func isProviderEnabled(_ id: ProviderID) -> Bool {
        provider(id).enabled && !killSwitch.disabledProviders.contains(id)
    }

    public var enabledProviders: [ProviderID] { ProviderID.allCases.filter(isProviderEnabled) }

    public func model(_ provider: ProviderID, _ modelID: String) -> ModelConfig? {
        values.models.first { $0.provider == provider && $0.id == modelID }
    }

    public func isModelVisible(_ model: ModelConfig) -> Bool {
        model.status != .retired &&
            !killSwitch.disabledModels.contains(model.id) &&
            model.minAppVersion <= appVersion &&
            isProviderEnabled(model.provider)
    }

    /// The models the setup screen lists for `provider`, in config order.
    public func modelChoices(_ provider: ProviderID) -> [ModelChoice] {
        values.models
            .filter { $0.provider == provider && isModelVisible($0) }
            .map { ModelChoice(model: $0, tag: tag($0)) }
    }

    private func tag(_ model: ModelConfig) -> ModelTag {
        switch model.status {
        case .preview: .preview
        case .deprecated: .endingSoon(replacementName: values.models.first { $0.id == model.replacementID }?.displayName ?? "")
        default: .none
        }
    }
}
