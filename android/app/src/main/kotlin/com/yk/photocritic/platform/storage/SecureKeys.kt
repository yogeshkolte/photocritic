package com.yk.photocritic.platform.storage

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import com.yk.photocritic.core.providers.ProviderId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** The user's API keys, one per provider; they never leave the device (§9). */
interface KeyStore {
    suspend fun get(provider: ProviderId): String?

    /** Throws [IOException] or [GeneralSecurityException] when the key cannot be saved. */
    suspend fun set(
        provider: ProviderId,
        key: String,
    )

    suspend fun delete(provider: ProviderId)

    /** Deletes every key and the encryption key (reinstall, §9). */
    suspend fun deleteAll()
}

/** Encrypts and decrypts key files. The real one uses the Android Keystore; Robolectric tests use a fake. */
interface KeyCipher {
    fun encrypt(plain: ByteArray): ByteArray

    fun decrypt(sealed: ByteArray): ByteArray

    /** Removes the encryption key itself. */
    fun destroy()
}

/**
 * One encrypted file per provider in `noBackupFilesDir/keys/` (§9, file 31).
 * A file that cannot be decrypted (for example after the Keystore key was
 * lost) reads as no key.
 */
class SecureKeys(
    private val dir: File,
    private val cipher: KeyCipher,
) : KeyStore {
    private val mutex = Mutex()

    private fun file(provider: ProviderId) = AtomicFile(File(dir, "${provider.wire}.key"))

    override suspend fun get(provider: ProviderId): String? =
        locked {
            val file = file(provider)
            if (!file.baseFile.exists()) return@locked null
            try {
                cipher.decrypt(file.readFully()).decodeToString()
            } catch (_: IOException) {
                null
            } catch (_: GeneralSecurityException) {
                null
            }
        }

    override suspend fun set(
        provider: ProviderId,
        key: String,
    ) = locked {
        dir.mkdirs()
        val sealed = cipher.encrypt(key.encodeToByteArray())
        val file = file(provider)
        val out = file.startWrite()
        try {
            out.write(sealed)
            file.finishWrite(out)
        } catch (e: IOException) {
            file.failWrite(out)
            throw e
        }
    }

    override suspend fun delete(provider: ProviderId) = locked { file(provider).delete() }

    override suspend fun deleteAll() =
        locked {
            dir.listFiles()?.forEach { it.delete() }
            cipher.destroy()
        }

    private suspend fun <T> locked(block: () -> T): T = mutex.withLock { withContext(Dispatchers.IO) { block() } }
}

/** AES-GCM with a non-exportable Android Keystore key (§9). */
class KeystoreCipher(
    private val alias: String = "pc_api_keys",
) : KeyCipher {
    private fun keyStore() =
        java.security.KeyStore
            .getInstance(PROVIDER)
            .apply { load(null) }

    private fun key(): SecretKey =
        (keyStore().getKey(alias, null) as? SecretKey) ?: KeyGenerator
            .getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
            .apply {
                init(
                    KeyGenParameterSpec
                        .Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(KEY_BITS)
                        .build(),
                )
            }.generateKey()

    /** The IV, then the cipher text. */
    override fun encrypt(plain: ByteArray): ByteArray {
        val c = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        return c.iv + c.doFinal(plain)
    }

    override fun decrypt(sealed: ByteArray): ByteArray {
        if (sealed.size <= IV_BYTES) throw GeneralSecurityException("key file too short")
        val c =
            Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES))
            }
        return c.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES)
    }

    override fun destroy() {
        keyStore().deleteEntry(alias)
    }

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BITS = 256
        const val TAG_BITS = 128
        const val IV_BYTES = 12
    }
}
