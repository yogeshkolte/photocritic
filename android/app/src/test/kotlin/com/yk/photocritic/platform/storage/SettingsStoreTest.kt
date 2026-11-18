package com.yk.photocritic.platform.storage

import com.yk.photocritic.core.providers.ProviderId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private var now = 0L
    private val store by lazy { SettingsStore(JsonFiles(tmp.root)) { now } }

    @Test
    fun defaultsThenSelections() =
        runBlocking {
            val empty = store.read()
            assertNull(empty.providerId)
            assertFalse(empty.shareUsage)
            store.select(ProviderId.GEMINI, "gemini-4-hydrogen")
            store.select(ProviderId.OPENAI, "gpt-5.4-mini")
            store.select(ProviderId.GEMINI, "gemini-4-argon")
            val s = store.read()
            assertEquals(ProviderId.GEMINI, s.providerId)
            assertEquals("gemini-4-argon", s.modelFor(ProviderId.GEMINI))
            assertEquals("gpt-5.4-mini", s.modelFor(ProviderId.OPENAI))
            assertEquals("1970-01-01T00:00:00.000Z", s.updatedAt)
        }

    @Test
    fun sharingAndOnboarding() =
        runBlocking {
            assertTrue(store.setShareUsage(true).shareUsage)
            now = 1000
            val first = store.completeOnboarding().onboardingCompletedAt
            now = 2000
            assertEquals(first, store.completeOnboarding().onboardingCompletedAt)
            assertEquals("1970-01-01T00:00:02.000Z", store.read().updatedAt)
        }
}
