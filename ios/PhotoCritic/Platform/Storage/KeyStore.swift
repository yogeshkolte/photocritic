import Foundation
import PhotoCriticCore
import Security

/// Where API keys live (§9, G7): on this device only, never in Firebase.
protocol KeyStore: Sendable {
    func get(_ provider: ProviderID) async -> String?
    func set(_ provider: ProviderID, _ key: String) async throws
    func delete(_ provider: ProviderID) async throws
    func deleteAll() async throws
}

struct KeychainError: Error {
    let status: OSStatus
}

/// Keys in the Keychain as generic passwords, readable after the first unlock
/// and never synced or moved to another device (`ThisDeviceOnly`). The
/// Keychain can outlive an uninstall, so `InstallMarker` wipes it on a fresh install.
struct KeychainKeys: KeyStore {
    let service: String

    init(service: String = "com.yk.photocritic.apikeys") {
        self.service = service
    }

    private func query(_ provider: ProviderID?) -> [String: Any] {
        var q: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service]
        if let provider { q[kSecAttrAccount as String] = provider.rawValue }
        return q
    }

    func get(_ provider: ProviderID) async -> String? {
        var q = query(provider)
        q[kSecReturnData as String] = true
        q[kSecMatchLimit as String] = kSecMatchLimitOne
        var item: CFTypeRef?
        guard SecItemCopyMatching(q as CFDictionary, &item) == errSecSuccess, let data = item as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    func set(_ provider: ProviderID, _ key: String) async throws {
        let data = Data(key.utf8)
        let update: [String: Any] = [kSecValueData as String: data]
        var status = SecItemUpdate(query(provider) as CFDictionary, update as CFDictionary)
        if status == errSecItemNotFound {
            var add = query(provider)
            add[kSecValueData as String] = data
            add[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
            status = SecItemAdd(add as CFDictionary, nil)
        }
        guard status == errSecSuccess else { throw KeychainError(status: status) }
    }

    func delete(_ provider: ProviderID) async throws {
        let status = SecItemDelete(query(provider) as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else { throw KeychainError(status: status) }
    }

    func deleteAll() async throws {
        let status = SecItemDelete(query(nil) as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else { throw KeychainError(status: status) }
    }
}

/// Reinstall handling (§9, file 31). The Keychain outlives an uninstall on
/// iOS, so a missing marker means a fresh install: every key is deleted
/// before the marker is written. An unreadable marker wipes nothing.
struct InstallMarker {
    static let file = "install.json"

    struct Info: Codable {
        let installedAt: String
        let appVersion: String
    }

    enum Outcome { case freshInstall, existing, unreadable }

    let files: JsonFiles
    let keys: any KeyStore

    func ensure(appVersion: String) async throws -> Outcome {
        switch await files.readState(Self.file, as: Info.self) {
        case .ok: return .existing
        case .unreadable: return .unreadable
        case .missing:
            try await keys.deleteAll()
            try await files.write(Self.file, Info(installedAt: UtcTime.iso(), appVersion: appVersion))
            return .freshInstall
        }
    }
}
