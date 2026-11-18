package com.yk.photocritic.platform.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yk.photocritic.core.providers.ProviderId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * [SecureKeys] with the real Android Keystore (file 39): run by hand on an
 * API 24 device and a current one, because Robolectric has no Keystore.
 */
@RunWith(AndroidJUnit4::class)
class SecureKeysTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dir = File(context.noBackupFilesDir, "keys-test")
    private val cipher = KeystoreCipher("pc_api_keys_test")
    private val keys = SecureKeys(dir, cipher)

    @After
    fun clean() {
        runBlocking { keys.deleteAll() }
        dir.deleteRecursively()
    }

    @Test
    fun keysAreEncryptedAtRestAndRoundTrip() =
        runBlocking {
            keys.set(ProviderId.GEMINI, "AQ.device-test-key-0123456789")
            assertEquals("AQ.device-test-key-0123456789", keys.get(ProviderId.GEMINI))
            assertFalse(File(dir, "gemini.key").readBytes().decodeToString().contains("device-test-key"))
            // Each write uses a fresh IV.
            val first = File(dir, "gemini.key").readBytes()
            keys.set(ProviderId.GEMINI, "AQ.device-test-key-0123456789")
            assertFalse(first.contentEquals(File(dir, "gemini.key").readBytes()))
        }

    @Test
    fun deletingTheKeystoreKeyMakesOldFilesUnreadable() =
        runBlocking {
            keys.set(ProviderId.OPENAI, "sk-device-test")
            val sealed = File(dir, "openai.key").readBytes()
            keys.deleteAll()
            dir.mkdirs()
            File(dir, "openai.key").writeBytes(sealed)
            assertNull(keys.get(ProviderId.OPENAI))
        }
}
