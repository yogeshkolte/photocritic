package com.yk.photocritic.platform.config

import com.yk.photocritic.TestConfig
import com.yk.photocritic.core.config.ConfigResult
import com.yk.photocritic.core.config.Tuning
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteConfigLoaderTest {
    private class FakeBackend(
        val fetch: RemoteConfigBackend.Fetch? = RemoteConfigBackend.Fetch.OK,
        val values: Map<String, String> = TestConfig.templateValues(),
    ) : RemoteConfigBackend {
        var fetches = 0

        override suspend fun fetchAndActivate(): RemoteConfigBackend.Fetch {
            fetches++
            return fetch ?: awaitCancellation()
        }

        override fun activatedValues() = values
    }

    private suspend fun load(backend: FakeBackend) = RemoteConfigLoader(backend).load(TestConfig.APP_VERSION)

    @Test
    fun aSuccessfulFetchIsValidated() =
        runTest {
            val backend = FakeBackend()
            val load = load(backend)
            assertEquals(FetchResult.SUCCESS, load.result)
            assertTrue(load.config is ConfigResult.Valid)
            assertEquals(1, backend.fetches)
        }

    @Test
    fun throttledUsesTheLastActivatedValues() =
        runTest {
            val load = load(FakeBackend(RemoteConfigBackend.Fetch.THROTTLED))
            assertEquals(FetchResult.THROTTLED, load.result)
            assertTrue(load.config is ConfigResult.Valid)
        }

    @Test
    fun throttledWithNothingActivatedIsUnavailable() =
        runTest {
            val load = load(FakeBackend(RemoteConfigBackend.Fetch.THROTTLED, emptyMap()))
            assertEquals(FetchResult.INVALID, load.result)
            assertTrue(load.config is ConfigResult.Unavailable)
        }

    @Test
    fun aFailedFetchIsUnavailableWithoutReadingValues() =
        runTest {
            val load = load(FakeBackend(RemoteConfigBackend.Fetch.FAILED))
            assertEquals(FetchResult.FAILED, load.result)
            assertEquals(ConfigResult.Unavailable("fetch", "failed"), load.config)
        }

    @Test
    fun aFetchThatNeverEndsTimesOut() =
        runTest {
            val load = load(FakeBackend(fetch = null))
            assertEquals(FetchResult.TIMEOUT, load.result)
            assertTrue(load.config is ConfigResult.Unavailable)
            assertEquals(Tuning.CONFIG_FETCH_TIMEOUT_MS, currentTime)
        }

    @Test
    fun anInvalidValueIsUnavailable() =
        runTest {
            val values = TestConfig.templateValues() + ("kill_switch" to "{}")
            val load = load(FakeBackend(values = values))
            assertEquals(FetchResult.INVALID, load.result)
            assertTrue(load.config is ConfigResult.Unavailable)
        }
}
