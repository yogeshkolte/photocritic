import Foundation
import PhotoCriticCore

/// Everything the screens use, built once per process (design §2). Tests
/// build one with fakes; nothing here holds a key except `keys`.
@MainActor
final class AppContainer {
    let appVersion: AppVersion
    let appVersionName: String
    let keys: any KeyStore
    let installMarker: InstallMarker
    let settings: SettingsStore
    let consent: ConsentStore
    let config: any ConfigSource
    let telemetry: Telemetry
    let cleanup: Cleanup
    let imagePipeline: ImagePipeline
    let providers: ProviderRegistry
    let connectivity: Connectivity

    init(
        appVersionName: String,
        files: JsonFiles,
        keys: any KeyStore,
        config: any ConfigSource,
        telemetry: Telemetry,
        cacheDir: URL,
        providers: ProviderRegistry,
        connectivity: Connectivity
    ) {
        self.appVersionName = appVersionName
        appVersion = AppVersion.parse(appVersionName) ?? AppVersion(major: 0, minor: 0, patch: 0)
        self.keys = keys
        installMarker = InstallMarker(files: files, keys: keys)
        settings = SettingsStore(files: files)
        consent = ConsentStore(files: files)
        self.config = config
        self.telemetry = telemetry
        cleanup = Cleanup(cacheDir: cacheDir)
        imagePipeline = ImagePipeline(outDir: cleanup.sendDir)
        self.providers = providers
        self.connectivity = connectivity
    }

    static func live() -> AppContainer {
        let fm = FileManager.default
        let support = fm.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("PhotoCritic")
        let caches = fm.urls(for: .cachesDirectory, in: .userDomainMask)[0].appendingPathComponent("PhotoCritic")
        let version = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "0.0.0"
        let http = ProviderHTTP(requestTimeoutMs: Tuning.requestTimeoutMs)
        return AppContainer(
            appVersionName: version,
            files: JsonFiles(dir: support),
            keys: KeychainKeys(),
            config: RemoteConfigLoader(backend: FirebaseRemoteConfigBackend()),
            telemetry: Telemetry(backend: FirebaseTelemetryBackend()),
            cacheDir: caches,
            providers: ProviderRegistry([GeminiClient(http: http)]),
            connectivity: Connectivity()
        )
    }
}
