package com.yk.photocritic.platform.config

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigFetchThrottledException
import com.google.firebase.remoteconfig.remoteConfigSettings
import com.yk.photocritic.core.config.AppVersion
import com.yk.photocritic.core.config.ConfigResult
import com.yk.photocritic.core.config.ConfigValidator
import com.yk.photocritic.core.config.Tuning
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/** How the startup fetch ended; the `result` of `pc_config_fetch` (file 33). */
enum class FetchResult(
    val wire: String,
) {
    SUCCESS("success"),
    THROTTLED("throttled"),
    FAILED("failed"),
    TIMEOUT("timeout"),
    INVALID("invalid"),
}

data class ConfigLoad(
    val config: ConfigResult,
    val result: FetchResult,
    val elapsedMs: Long,
)

/** Loads and checks the config at cold start (§8). */
fun interface ConfigSource {
    suspend fun load(appVersion: AppVersion): ConfigLoad
}

/** The Remote Config calls the loader needs, so tests can replace Firebase. */
interface RemoteConfigBackend {
    enum class Fetch { OK, THROTTLED, FAILED }

    suspend fun fetchAndActivate(): Fetch

    /** The activated values, parameter to JSON string; empty if nothing was ever activated. */
    fun activatedValues(): Map<String, String>
}

/**
 * The §8 load order (file 32): fetch and activate with a minimum fetch
 * interval of 0 and [Tuning.CONFIG_FETCH_TIMEOUT_MS]; when throttled, use
 * the values activated by an earlier fetch, if any; then validate. There
 * are no in-app values and no update listener (Q9, G18).
 */
class RemoteConfigLoader(
    private val backend: RemoteConfigBackend,
    private val nowMs: () -> Long = System::currentTimeMillis,
) : ConfigSource {
    override suspend fun load(appVersion: AppVersion): ConfigLoad {
        val start = nowMs()
        val fetch =
            try {
                withTimeout(Tuning.CONFIG_FETCH_TIMEOUT_MS) { backend.fetchAndActivate() }
            } catch (_: TimeoutCancellationException) {
                null
            }
        val elapsed = nowMs() - start
        val failure =
            when (fetch) {
                null -> FetchResult.TIMEOUT
                RemoteConfigBackend.Fetch.FAILED -> FetchResult.FAILED
                else -> null
            }
        if (failure != null) return ConfigLoad(ConfigResult.Unavailable("fetch", failure.wire), failure, elapsed)
        val config = ConfigValidator.validate(backend.activatedValues(), appVersion)
        val result =
            when {
                config is ConfigResult.Unavailable -> FetchResult.INVALID
                fetch == RemoteConfigBackend.Fetch.THROTTLED -> FetchResult.THROTTLED
                else -> FetchResult.SUCCESS
            }
        return ConfigLoad(config, result, elapsed)
    }
}

class FirebaseRemoteConfigBackend(
    private val remote: FirebaseRemoteConfig = FirebaseRemoteConfig.getInstance(),
) : RemoteConfigBackend {
    init {
        remote.setConfigSettingsAsync(
            remoteConfigSettings {
                minimumFetchIntervalInSeconds = 0
                fetchTimeoutInSeconds = Tuning.CONFIG_FETCH_TIMEOUT_MS / MS_PER_SECOND
            },
        )
    }

    @Suppress("TooGenericExceptionCaught") // Firebase reports every fetch failure as an exception.
    override suspend fun fetchAndActivate(): RemoteConfigBackend.Fetch =
        try {
            remote.fetchAndActivate().await()
            RemoteConfigBackend.Fetch.OK
        } catch (_: FirebaseRemoteConfigFetchThrottledException) {
            RemoteConfigBackend.Fetch.THROTTLED
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            RemoteConfigBackend.Fetch.FAILED
        }

    override fun activatedValues(): Map<String, String> = remote.all.mapValues { it.value.asString() }

    private companion object {
        const val MS_PER_SECOND = 1000L
    }
}
