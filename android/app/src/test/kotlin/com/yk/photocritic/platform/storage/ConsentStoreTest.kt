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
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ConsentStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private var now = 0L
    private val store by lazy { ConsentStore(JsonFiles(tmp.root)) { now } }

    @Test
    fun recordsPerProviderAndKeepsTenEarlier() =
        runBlocking {
            assertNull(store.current(ProviderId.GEMINI))
            repeat(12) { i ->
                now = i * 1000L
                assertTrue(store.record(ProviderId.GEMINI, i + 1, granted = i % 2 == 0))
            }
            val current = store.current(ProviderId.GEMINI)!!
            assertEquals(12, current.consentTextVersion)
            assertEquals(ConsentStore.DECLINED, current.decision)
            val file = JsonFiles(tmp.root).read(ConsentStore.FILE, ConsentFile.serializer(), ::ConsentFile)
            val earlier = file.providers.getValue("gemini").earlier
            assertEquals(ConsentStore.MAX_EARLIER, earlier.size)
            assertEquals(11, earlier.first().consentTextVersion)
            assertNull(store.current(ProviderId.OPENAI))
        }

    @Test
    fun aFailedWriteReportsFalse() =
        runBlocking {
            File(tmp.root, "${ConsentStore.FILE}.new").mkdirs()
            assertFalse(store.record(ProviderId.GEMINI, 1, granted = true))
            assertNull(store.current(ProviderId.GEMINI))
        }
}
