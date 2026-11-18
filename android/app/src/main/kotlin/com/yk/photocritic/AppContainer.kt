package com.yk.photocritic

import android.content.Context
import com.yk.photocritic.core.config.AppVersion
import com.yk.photocritic.core.config.Tuning
import com.yk.photocritic.core.net.ProviderHttp
import com.yk.photocritic.core.providers.ProviderRegistry
import com.yk.photocritic.core.providers.gemini.GeminiClient
import com.yk.photocritic.features.image.Cleanup
import com.yk.photocritic.features.image.ImagePipeline
import com.yk.photocritic.platform.config.ConfigSource
import com.yk.photocritic.platform.config.FirebaseRemoteConfigBackend
import com.yk.photocritic.platform.config.RemoteConfigLoader
import com.yk.photocritic.platform.net.Connectivity
import com.yk.photocritic.platform.storage.ConsentStore
import com.yk.photocritic.platform.storage.InstallMarker
import com.yk.photocritic.platform.storage.JsonFiles
import com.yk.photocritic.platform.storage.KeyStore
import com.yk.photocritic.platform.storage.KeystoreCipher
import com.yk.photocritic.platform.storage.ReportStore
import com.yk.photocritic.platform.storage.SecureKeys
import com.yk.photocritic.platform.storage.SettingsStore
import com.yk.photocritic.platform.telemetry.FirebaseTelemetryBackend
import com.yk.photocritic.platform.telemetry.Telemetry
import java.io.File

/**
 * Every dependency, passed by hand (design Assumptions, file 37). Reports
 * arrive in Phase 1.4.
 * Tests build one from fakes with the primary constructor.
 */
@Suppress("LongParameterList") // One holder for every dependency, by design.
class AppContainer(
    val appVersion: AppVersion,
    val appVersionName: String,
    val keys: KeyStore,
    val installMarker: InstallMarker,
    val settings: SettingsStore,
    val consent: ConsentStore,
    val reports: ReportStore,
    val config: ConfigSource,
    val providers: ProviderRegistry,
    val telemetry: Telemetry,
    val cleanup: Cleanup,
    val imagePipeline: ImagePipeline,
    /** Null in tests that do not need it. */
    val connectivity: Connectivity?,
) {
    companion object {
        fun create(context: Context): AppContainer {
            val files = JsonFiles(context.filesDir)
            val keys = SecureKeys(File(context.noBackupFilesDir, "keys"), KeystoreCipher())
            val cleanup = Cleanup(context.cacheDir)
            return AppContainer(
                appVersion = checkNotNull(AppVersion.parse(BuildConfig.VERSION_NAME)) { "versionName must be x.y.z" },
                appVersionName = BuildConfig.VERSION_NAME,
                keys = keys,
                installMarker = InstallMarker(files, keys),
                settings = SettingsStore(files),
                consent = ConsentStore(files),
                reports = ReportStore(files),
                config = RemoteConfigLoader(FirebaseRemoteConfigBackend()),
                providers =
                    ProviderRegistry(
                        listOf(
                            GeminiClient(
                                ProviderHttp(connectTimeoutMs = Tuning.CONNECT_TIMEOUT_MS, requestTimeoutMs = Tuning.REQUEST_TIMEOUT_MS),
                            ),
                        ),
                    ),
                telemetry = Telemetry(FirebaseTelemetryBackend(context)),
                cleanup = cleanup,
                imagePipeline = ImagePipeline(context.contentResolver, cleanup.sendDir),
                connectivity = Connectivity(context),
            )
        }
    }
}
