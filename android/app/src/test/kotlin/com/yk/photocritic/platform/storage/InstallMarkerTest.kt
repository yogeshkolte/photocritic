package com.yk.photocritic.platform.storage

import com.yk.photocritic.FakeKeyStore
import com.yk.photocritic.core.providers.ProviderId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class InstallMarkerTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val keys = FakeKeyStore()

    private fun marker() = InstallMarker(JsonFiles(tmp.root), keys) { 0 }

    @Test
    fun aMissingMarkerWipesKeysThenWritesTheMarker() =
        runBlocking {
            keys.keys[ProviderId.GEMINI] = "left over"
            assertEquals(InstallMarker.Outcome.FRESH_INSTALL, marker().ensure("0.1.0"))
            assertEquals(1, keys.deleteAllCalls)
            assertEquals(emptyMap<ProviderId, String>(), keys.keys)
            assertEquals(
                FileRead.Ok(InstallInfo("1970-01-01T00:00:00.000Z", "0.1.0")),
                JsonFiles(tmp.root).readState(InstallMarker.FILE, InstallInfo.serializer()),
            )
        }

    @Test
    fun anUpgradeKeepsKeys() =
        runBlocking {
            marker().ensure("0.1.0")
            keys.keys[ProviderId.GEMINI] = "kept"
            assertEquals(InstallMarker.Outcome.EXISTING, marker().ensure("0.2.0"))
            assertEquals("kept", keys.keys[ProviderId.GEMINI])
            assertEquals(1, keys.deleteAllCalls)
        }

    @Test
    fun anUnreadableMarkerIsNotMissing() =
        runBlocking {
            File(tmp.root, InstallMarker.FILE).writeText("garbage")
            keys.keys[ProviderId.GEMINI] = "kept"
            assertEquals(InstallMarker.Outcome.UNREADABLE, marker().ensure("0.1.0"))
            assertEquals(0, keys.deleteAllCalls)
            assertEquals("kept", keys.keys[ProviderId.GEMINI])
        }
}
