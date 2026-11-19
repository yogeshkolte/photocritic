import Foundation

/// The Remote Config parameters (design §8), after `ConfigValidator` has checked them.
public struct RemoteConfigValues: Sendable {
    public let meta: ConfigMeta
    public let killSwitch: KillSwitchConfig
    public let providers: [ProviderID: ProviderConfig]
    public let models: [ModelConfig]
    public let consentTexts: [ProviderID: ConsentText]
    public let privacyPolicy: PrivacyPolicyConfig
}

public struct ConfigMeta: Sendable {
    public let configVersion: Int
    public let publishedAt: String
}

public struct KillSwitchConfig: Sendable {
    public let appDisabled: Bool
    public let message: String
    public let minSupportedAppVersion: AppVersion
    /// Empty when there is no update link.
    public let updateURL: String
    public let disabledProviders: Set<ProviderID>
    public let disabledModels: Set<String>
    public let bannerMessage: String
    public let reportsDisabled: Bool
}

public struct ProviderConfig: Sendable {
    public let id: ProviderID
    public let enabled: Bool
    public let displayName: String
    public let baseURL: String
    public let keyHelpURL: String
    public let consentTextVersion: Int
}

public enum ModelStatus: String, Sendable, CaseIterable {
    case active, preview, deprecated, retired
}

public struct ModelConfig: Sendable, Equatable {
    public let provider: ProviderID
    public let id: String
    public let displayName: String
    /// `Fast`, `Best` or empty; display only.
    public let label: String
    public let maxLongEdgePx: Int
    public let maxBytes: Int
    public let imageDetail: String
    public let maxOutputTokens: Int
    /// `low`, `medium`, `high`, or empty for the provider's default.
    public let thinkingLevel: String
    public let contextTokens: Int
    public let status: ModelStatus
    /// Empty when there is no replacement.
    public let replacementID: String
    public let minAppVersion: AppVersion

    public var settings: ModelSettings {
        ModelSettings(modelID: id, maxOutputTokens: maxOutputTokens, contextTokens: contextTokens, thinkingLevel: thinkingLevel)
    }
}

public struct ConsentText: Sendable, Equatable {
    public let version: Int
    public let effectiveDate: String
    public let summary: [String]
    public let policyURL: String
}

public struct PrivacyPolicyConfig: Sendable {
    public let version: Int
    /// Empty until a hosted copy exists.
    public let url: String
}
