@preconcurrency import FirebaseRemoteConfig
import Foundation
import PhotoCriticCore

/// How the startup fetch ended; the `result` of `pc_config_fetch` (file 33).
enum FetchResult: String {
    case success, throttled, failed, timeout, invalid
}

struct ConfigLoad {
    let config: ConfigResult
    let result: FetchResult
    let elapsedMs: Int
}

/// The Remote Config calls the loader needs, so tests can replace Firebase.
protocol RemoteConfigBackend: Sendable {
    func fetchAndActivate() async -> RemoteConfigFetch
    /// The activated values, parameter to JSON string; empty if nothing was ever activated.
    func activatedValues() -> [String: String]
}

enum RemoteConfigFetch: Sendable { case ok, throttled, failed }

/// Loads and checks the config at cold start (§8).
protocol ConfigSource: Sendable {
    func load(appVersion: AppVersion) async -> ConfigLoad
}

/// The §8 load order (file 32): fetch and activate with a minimum fetch
/// interval of 0 and `Tuning.configFetchTimeoutMs`; when throttled, use the
/// values activated by an earlier fetch, if any; then validate. There are
/// no in-app values and no update listener (Q9, G18).
struct RemoteConfigLoader: ConfigSource {
    let backend: any RemoteConfigBackend

    func load(appVersion: AppVersion) async -> ConfigLoad {
        let start = ContinuousClock.now
        let fetch = await withTaskGroup(of: RemoteConfigFetch?.self) { group in
            group.addTask { await backend.fetchAndActivate() }
            group.addTask {
                try? await Task.sleep(for: .milliseconds(Tuning.configFetchTimeoutMs))
                return nil
            }
            let first = await group.next() ?? nil
            group.cancelAll()
            return first
        }
        let elapsed = Int((ContinuousClock.now - start) / .milliseconds(1))
        switch fetch {
        case nil: return ConfigLoad(config: .unavailable(path: "fetch", reason: "timeout"), result: .timeout, elapsedMs: elapsed)
        case .failed: return ConfigLoad(config: .unavailable(path: "fetch", reason: "failed"), result: .failed, elapsedMs: elapsed)
        default: break
        }
        let config = ConfigValidator.validate(backend.activatedValues(), appVersion: appVersion)
        let result: FetchResult
        if case .unavailable = config {
            result = .invalid
        } else {
            result = fetch == .throttled ? .throttled : .success
        }
        return ConfigLoad(config: config, result: result, elapsedMs: elapsed)
    }
}

struct FirebaseRemoteConfigBackend: RemoteConfigBackend {
    private var remote: RemoteConfig { RemoteConfig.remoteConfig() }

    init() {
        let settings = RemoteConfigSettings()
        settings.minimumFetchInterval = 0
        settings.fetchTimeout = Double(Tuning.configFetchTimeoutMs) / 1000
        RemoteConfig.remoteConfig().configSettings = settings
    }

    func fetchAndActivate() async -> RemoteConfigFetch {
        await withCheckedContinuation { continuation in
            remote.fetchAndActivate { status, error in
                switch status {
                case .successFetchedFromRemote, .successUsingPreFetchedData:
                    continuation.resume(returning: .ok)
                case .error:
                    let throttled = (error as NSError?)?.code == RemoteConfigError.throttled.rawValue
                    continuation.resume(returning: throttled ? .throttled : .failed)
                @unknown default:
                    continuation.resume(returning: .failed)
                }
            }
        }
    }

    func activatedValues() -> [String: String] {
        var values: [String: String] = [:]
        for key in remote.allKeys(from: .remote) {
            values[key] = remote.configValue(forKey: key, source: .remote).stringValue
        }
        return values
    }
}
