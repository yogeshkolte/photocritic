package com.yk.photocritic.platform.storage

import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap

/** What a read found; a file that exists but cannot be read is not "missing" (§9). */
sealed interface FileRead<out T> {
    data object Missing : FileRead<Nothing>

    data object Unreadable : FileRead<Nothing>

    data class Ok<T>(
        val value: T,
    ) : FileRead<T>
}

/**
 * The small JSON files in §9. Writes go to a temporary file that is then
 * renamed ([AtomicFile]), one writer per file at a time (file 31).
 */
class JsonFiles(
    private val dir: File,
) {
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    private fun lock(name: String) = locks.getOrPut(name) { Mutex() }

    private fun file(name: String) = AtomicFile(File(dir, name))

    suspend fun <T> readState(
        name: String,
        serializer: KSerializer<T>,
    ): FileRead<T> = lock(name).withLock { withContext(Dispatchers.IO) { readNow(name, serializer) } }

    /** The file's value, or [default] when it is missing or unreadable. */
    suspend fun <T> read(
        name: String,
        serializer: KSerializer<T>,
        default: () -> T,
    ): T = (readState(name, serializer) as? FileRead.Ok)?.value ?: default()

    /** Throws [IOException] when the write fails; the previous file is kept. */
    suspend fun <T> write(
        name: String,
        serializer: KSerializer<T>,
        value: T,
    ) = lock(name).withLock { withContext(Dispatchers.IO) { writeNow(name, serializer, value) } }

    /** Reads, changes and writes under one lock. A missing or unreadable file starts from [default]. */
    suspend fun <T> update(
        name: String,
        serializer: KSerializer<T>,
        default: () -> T,
        change: (T) -> T,
    ): T =
        lock(name).withLock {
            withContext(Dispatchers.IO) {
                val current = (readNow(name, serializer) as? FileRead.Ok)?.value ?: default()
                change(current).also { writeNow(name, serializer, it) }
            }
        }

    private fun <T> readNow(
        name: String,
        serializer: KSerializer<T>,
    ): FileRead<T> {
        val file = file(name)
        if (!file.baseFile.exists()) return FileRead.Missing
        return try {
            FileRead.Ok(json.decodeFromString(serializer, file.readFully().decodeToString()))
        } catch (_: IOException) {
            FileRead.Unreadable
        } catch (_: SerializationException) {
            FileRead.Unreadable
        } catch (_: IllegalArgumentException) {
            FileRead.Unreadable
        }
    }

    private fun <T> writeNow(
        name: String,
        serializer: KSerializer<T>,
        value: T,
    ) {
        dir.mkdirs()
        val file = file(name)
        val out = file.startWrite()
        try {
            out.write(json.encodeToString(serializer, value).encodeToByteArray())
            file.finishWrite(out)
        } catch (e: IOException) {
            file.failWrite(out)
            throw e
        }
    }
}

/** UTC times in the §9 files, as ISO 8601 text. */
object UtcTime {
    fun iso(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(millis))

    /** The UTC calendar day, used by the report daily limit. */
    fun day(millis: Long): String = iso(millis).substring(0, DAY_LENGTH)

    private const val DAY_LENGTH = 10
}
