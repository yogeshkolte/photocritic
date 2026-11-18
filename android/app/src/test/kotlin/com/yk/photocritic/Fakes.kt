package com.yk.photocritic

import android.content.Context
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import com.yk.photocritic.core.config.AppVersion
import com.yk.photocritic.core.config.ConfigResult
import com.yk.photocritic.core.config.ConfigValidator
import com.yk.photocritic.core.config.Snapshot
import com.yk.photocritic.core.providers.ProviderId
import com.yk.photocritic.core.providers.ProviderRegistry
import com.yk.photocritic.core.providers.fake.FakeProvider
import com.yk.photocritic.features.image.Cleanup
import com.yk.photocritic.features.image.ImagePipeline
import com.yk.photocritic.platform.config.ConfigLoad
import com.yk.photocritic.platform.config.ConfigSource
import com.yk.photocritic.platform.config.FetchResult
import com.yk.photocritic.platform.storage.ConsentStore
import com.yk.photocritic.platform.storage.InstallMarker
import com.yk.photocritic.platform.storage.JsonFiles
import com.yk.photocritic.platform.storage.KeyStore
import com.yk.photocritic.platform.storage.ReportStore
import com.yk.photocritic.platform.storage.SettingsStore
import com.yk.photocritic.platform.telemetry.Telemetry
import com.yk.photocritic.platform.telemetry.TelemetryBackend
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.io.File

/** Test doubles for the [AppContainer] interfaces (file 39). */
class FakeKeyStore : KeyStore {
    val keys = mutableMapOf<ProviderId, String>()
    var deleteAllCalls = 0

    override suspend fun get(provider: ProviderId) = keys[provider]

    override suspend fun set(
        provider: ProviderId,
        key: String,
    ) {
        keys[provider] = key
    }

    override suspend fun delete(provider: ProviderId) {
        keys.remove(provider)
    }

    override suspend fun deleteAll() {
        deleteAllCalls++
        keys.clear()
    }
}

class FakeTelemetryBackend : TelemetryBackend {
    var collection: Boolean? = null
    var currentUserId: String? = "unset"
    val events = mutableListOf<Pair<String, Bundle>>()
    var instanceId: String? = "instance-1"

    override fun setCollection(on: Boolean) {
        collection = on
    }

    override suspend fun appInstanceId() = instanceId

    override fun setUserId(id: String?) {
        currentUserId = id
    }

    override fun logEvent(
        name: String,
        params: Bundle,
    ) {
        events += name to params
    }
}

object TestConfig {
    val APP_VERSION = AppVersion(0, 1, 0)

    /** `shared/remote-config/template.json` as Remote Config hands it over. */
    fun templateValues(): Map<String, String> =
        Json
            .parseToJsonElement(File(System.getProperty("pc.repoRoot"), "shared/remote-config/template.json").readText())
            .jsonObject
            .mapValues { it.value.toString() }

    fun source(
        values: Map<String, String>? = templateValues(),
        result: FetchResult = FetchResult.SUCCESS,
    ) = ConfigSource { version ->
        val config = values?.let { ConfigValidator.validate(it, version) } ?: ConfigResult.Unavailable("fetch", result.wire)
        ConfigLoad(config, result, 12)
    }
}

fun testContainer(
    root: File,
    config: ConfigSource = TestConfig.source(),
    keys: FakeKeyStore = FakeKeyStore(),
    telemetry: FakeTelemetryBackend = FakeTelemetryBackend(),
    provider: FakeProvider = FakeProvider(),
    /** Robolectric's ImageDecoder hangs on bytes that are not an image; tests of that case use 27. */
    sdkInt: Int = android.os.Build.VERSION.SDK_INT,
): AppContainer {
    val files = JsonFiles(File(root, "files"))
    val cleanup = Cleanup(File(root, "cache"))
    return AppContainer(
        appVersion = TestConfig.APP_VERSION,
        appVersionName = "0.1.0",
        keys = keys,
        installMarker = InstallMarker(files, keys),
        settings = SettingsStore(files),
        consent = ConsentStore(files),
        reports = ReportStore(files),
        config = config,
        providers = ProviderRegistry(listOf(provider)),
        telemetry = Telemetry(telemetry),
        cleanup = cleanup,
        imagePipeline = ImagePipeline(ApplicationProvider.getApplicationContext<Context>().contentResolver, cleanup.sendDir, sdkInt),
        connectivity = null,
    )
}

/** The template's validated snapshot. */
fun testSnapshot(): Snapshot =
    (ConfigValidator.validate(TestConfig.templateValues(), TestConfig.APP_VERSION) as ConfigResult.Valid).snapshot

/** Polls [condition] until it holds; ViewModels here mix the main thread and real IO threads. */
fun waitFor(
    what: String,
    timeoutMs: Long = 5_000,
    condition: () -> Boolean,
) {
    val end = System.currentTimeMillis() + timeoutMs
    while (!condition()) {
        org.robolectric.shadows.ShadowLooper
            .idleMainLooper()
        check(System.currentTimeMillis() < end) { "timed out waiting for $what" }
        Thread.sleep(5)
    }
}
