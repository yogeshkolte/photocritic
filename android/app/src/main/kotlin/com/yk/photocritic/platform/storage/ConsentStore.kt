package com.yk.photocritic.platform.storage

import com.yk.photocritic.core.providers.ProviderId
import kotlinx.serialization.Serializable
import java.io.IOException

@Serializable
data class ConsentRecord(
    val consentTextVersion: Int,
    /** `granted` or `declined`. */
    val decision: String,
    val recordedAt: String,
)

@Serializable
data class ConsentHistory(
    val current: ConsentRecord? = null,
    /** Newest first, at most [ConsentStore.MAX_EARLIER]. */
    val earlier: List<ConsentRecord> = emptyList(),
)

/** `consent.json` (§9): per provider, the current record and up to 10 earlier ones. */
@Serializable
data class ConsentFile(
    val schemaVersion: Int = 1,
    val providers: Map<String, ConsentHistory> = emptyMap(),
)

class ConsentStore(
    private val files: JsonFiles,
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun current(provider: ProviderId): ConsentRecord? =
        files.read(FILE, ConsentFile.serializer(), ::ConsentFile).providers[provider.wire]?.current

    /** True only when the decision was saved; a failed write must block the send (file 31). */
    suspend fun record(
        provider: ProviderId,
        consentTextVersion: Int,
        granted: Boolean,
    ): Boolean =
        try {
            files.update(FILE, ConsentFile.serializer(), ::ConsentFile) { file ->
                val history = file.providers[provider.wire] ?: ConsentHistory()
                val record = ConsentRecord(consentTextVersion, if (granted) GRANTED else DECLINED, UtcTime.iso(now()))
                val updated = ConsentHistory(record, (listOfNotNull(history.current) + history.earlier).take(MAX_EARLIER))
                file.copy(providers = file.providers + (provider.wire to updated))
            }
            true
        } catch (_: IOException) {
            false
        }

    companion object {
        const val FILE = "consent.json"
        const val MAX_EARLIER = 10
        const val GRANTED = "granted"
        const val DECLINED = "declined"
    }
}
