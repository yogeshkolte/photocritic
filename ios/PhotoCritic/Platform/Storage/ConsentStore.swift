import Foundation
import PhotoCriticCore

struct ConsentRecord: Codable, Equatable {
    let consentTextVersion: Int
    /// `granted` or `declined`.
    let decision: String
    let recordedAt: String
}

struct ConsentHistory: Codable, Equatable {
    var current: ConsentRecord?
    /// Newest first, at most `ConsentStore.maxEarlier`.
    var earlier: [ConsentRecord] = []
}

/// `consent.json` (§9): per provider, the current record and up to 10 earlier ones.
struct ConsentFile: Codable, Equatable {
    var schemaVersion = 1
    var providers: [String: ConsentHistory] = [:]
}

struct ConsentStore {
    static let file = "consent.json"
    static let maxEarlier = 10
    static let granted = "granted"
    static let declined = "declined"
    let files: JsonFiles

    func current(_ provider: ProviderID) async -> ConsentRecord? {
        await files.read(Self.file, default: ConsentFile()).providers[provider.rawValue]?.current
    }

    /// True only when the decision was saved; a failed write must block the send (file 31).
    func record(_ provider: ProviderID, version: Int, granted: Bool) async -> Bool {
        do {
            _ = try await files.update(Self.file, default: ConsentFile()) { file in
                var file = file
                let history = file.providers[provider.rawValue] ?? ConsentHistory()
                let record = ConsentRecord(
                    consentTextVersion: version, decision: granted ? Self.granted : Self.declined, recordedAt: UtcTime.iso()
                )
                let earlier = Array(([history.current].compactMap { $0 } + history.earlier).prefix(Self.maxEarlier))
                file.providers[provider.rawValue] = ConsentHistory(current: record, earlier: earlier)
                return file
            }
            return true
        } catch {
            return false
        }
    }
}
