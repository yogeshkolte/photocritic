import Foundation
import PhotoCriticCore

/// `settings.json` (§9). Provider IDs are their wire names.
struct Settings: Codable, Equatable {
    var schemaVersion = 1
    var provider: String?
    /// The selected model per provider.
    var models: [String: String] = [:]
    var shareUsage = false
    var onboardingCompletedAt: String?
    var updatedAt: String?

    var providerID: ProviderID? { provider.flatMap(ProviderID.init(rawValue:)) }

    func model(for provider: ProviderID) -> String? { models[provider.rawValue] }
}

struct SettingsStore {
    static let file = "settings.json"
    let files: JsonFiles

    func read() async -> Settings { await files.read(Self.file, default: Settings()) }

    /// Throws when the write fails.
    @discardableResult
    func update(_ change: @Sendable @escaping (Settings) -> Settings) async throws -> Settings {
        try await files.update(Self.file, default: Settings()) { current in
            var next = change(current)
            next.updatedAt = UtcTime.iso()
            return next
        }
    }

    func select(_ provider: ProviderID, model: String) async throws {
        try await update { s in
            var s = s
            s.provider = provider.rawValue
            s.models[provider.rawValue] = model
            return s
        }
    }

    func setShareUsage(_ on: Bool) async throws {
        try await update { s in
            var s = s
            s.shareUsage = on
            return s
        }
    }

    func completeOnboarding() async throws {
        try await update { s in
            var s = s
            s.onboardingCompletedAt = s.onboardingCompletedAt ?? UtcTime.iso()
            return s
        }
    }
}
