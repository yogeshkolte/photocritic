import Foundation
import Observation
import PhotoCriticCore

enum SetupStatus: Equatable {
    case idle
    case checking
    /// The generic message with "Show details" (G12).
    case failed(PcError)
    case saved
}

struct ProviderOption: Equatable, Identifiable {
    let id: ProviderID
    let displayName: String
}

/// The setup screen (file 38, CUJ 1, G9, G10): provider, model by the file 26
/// rules, key check, and on first run the sharing switch. It never picks a
/// model for the user (G18). Typed keys are not kept here: the screen passes
/// one to `save`, which checks it before storing it.
@MainActor
@Observable
final class SetupModel {
    private(set) var providers: [ProviderOption] = []
    private(set) var provider: ProviderID?
    private(set) var models: [ModelChoice] = []
    var modelID: String?
    /// A key for `provider` is already stored, so the field may stay empty.
    private(set) var hasSavedKey = false
    private(set) var keyHelpURL = ""
    var shareUsage = false
    var status = SetupStatus.idle
    private(set) var loaded = false
    let firstRun: Bool

    @ObservationIgnored private let c: AppContainer
    @ObservationIgnored private let snapshot: Snapshot
    @ObservationIgnored private var savedProvider: ProviderID?
    @ObservationIgnored private var savedModels: [String: String] = [:]

    init(c: AppContainer, snapshot: Snapshot, firstRun: Bool) {
        self.c = c
        self.snapshot = snapshot
        self.firstRun = firstRun
    }

    func load() async {
        guard !loaded else { return }
        let settings = await c.settings.read()
        savedProvider = settings.providerID
        savedModels = settings.models
        providers = snapshot.enabledProviders
            .filter { c.providers.ids.contains($0) }
            .map { ProviderOption(id: $0, displayName: snapshot.provider($0).displayName) }
        shareUsage = settings.shareUsage
        loaded = true
        let saved = savedProvider.flatMap { p in providers.contains { $0.id == p } ? p : nil }
        if let pick = saved ?? (providers.count == 1 ? providers[0].id : nil) { await select(pick) }
    }

    func select(_ id: ProviderID) async {
        let choices = snapshot.modelChoices(id)
        let saved = savedModels[id.rawValue].flatMap { m in choices.contains { $0.model.id == m } ? m : nil }
        hasSavedKey = await c.keys.get(id) != nil
        provider = id
        models = choices
        modelID = saved
        keyHelpURL = snapshot.provider(id).keyHelpURL
        status = .idle
    }

    var providerName: String { provider.map { snapshot.provider($0).displayName } ?? "" }

    /// True when saving would change the provider or model in use, which ends the conversation (CUJ 5).
    var changesSelection: Bool {
        provider != savedProvider || (provider.map { modelID != savedModels[$0.rawValue] } ?? false)
    }

    func canSave(_ typedKey: String) -> Bool {
        provider != nil && modelID != nil && status != .checking &&
            (!typedKey.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || hasSavedKey)
    }

    /// "Check key and continue": checks a typed key, then stores the key and the choice. True when saved.
    func save(_ typedKey: String) async -> Bool {
        guard canSave(typedKey), let provider, let modelID, let model = snapshot.model(provider, modelID) else { return false }
        let key = typedKey.trimmingCharacters(in: .whitespacesAndNewlines)
        status = .checking
        if !key.isEmpty {
            let result = await c.providers.get(provider)?.checkKey(key, model: model.settings) ?? .failed(storageError("no client"))
            if case let .failed(error) = result {
                status = .failed(error)
                return false
            }
        }
        do {
            if !key.isEmpty { try await c.keys.set(provider, key) }
            try await c.settings.select(provider, model: model.id)
            if firstRun {
                try await c.settings.setShareUsage(shareUsage)
                try await c.settings.completeOnboarding()
                c.telemetry.setSharing(shareUsage)
            }
            hasSavedKey = true
            status = .saved
            return true
        } catch {
            status = .failed(storageError(String(describing: type(of: error))))
            return false
        }
    }

    private func storageError(_ detail: String) -> PcError {
        PcError(phase: .beforeResponse, httpStatus: nil, providerCode: "storage", requestID: nil, detail: detail)
    }
}
