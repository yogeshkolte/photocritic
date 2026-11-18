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
import javax.crypto.AEADBadTagException

/**
 * The file handling of [SecureKeys] with a stand-in cipher: Robolectric has
 * no Android Keystore. [KeystoreCipher] runs on devices in `androidTest`.
 */
@RunWith(RobolectricTestRunner::class)
class SecureKeysTest {
    @get:Rule
    val tmp = TemporaryFolder()

    /** Reverses the bytes and adds a marker byte, so stored files never hold the key as written. */
    private class ReversingCipher : KeyCipher {
        var destroyed = false

        override fun encrypt(plain: ByteArray) = byteArrayOf(MARK) + plain.reversedArray()

        override fun decrypt(sealed: ByteArray): ByteArray {
            if (destroyed || sealed.firstOrNull() != MARK) throw AEADBadTagException()
            return sealed.drop(1).toByteArray().reversedArray()
        }

        override fun destroy() {
            destroyed = true
        }

        companion object {
            const val MARK: Byte = 42
        }
    }

    private val cipher = ReversingCipher()
    private val dir by lazy { File(tmp.root, "keys") }
    private val keys by lazy { SecureKeys(dir, cipher) }

    @Test
    fun setGetDeletePerProvider() =
        runBlocking {
            assertNull(keys.get(ProviderId.GEMINI))
            keys.set(ProviderId.GEMINI, "AQ.secret-value")
            keys.set(ProviderId.OPENAI, "sk-other")
            assertEquals("AQ.secret-value", keys.get(ProviderId.GEMINI))
            assertFalse(File(dir, "gemini.key").readText().contains("secret"))
            keys.delete(ProviderId.GEMINI)
            assertNull(keys.get(ProviderId.GEMINI))
            assertEquals("sk-other", keys.get(ProviderId.OPENAI))
        }

    @Test
    fun anUndecryptableFileReadsAsNoKey() =
        runBlocking {
            keys.set(ProviderId.GEMINI, "AQ.secret-value")
            File(dir, "gemini.key").writeBytes(byteArrayOf(1, 2, 3))
            assertNull(keys.get(ProviderId.GEMINI))
        }

    @Test
    fun deleteAllRemovesFilesAndTheEncryptionKey() =
        runBlocking {
            keys.set(ProviderId.GEMINI, "a")
            keys.set(ProviderId.ANTHROPIC, "b")
            keys.deleteAll()
            assertTrue(cipher.destroyed)
            assertEquals(0, dir.listFiles()?.size ?: 0)
            assertNull(keys.get(ProviderId.ANTHROPIC))
        }
}
