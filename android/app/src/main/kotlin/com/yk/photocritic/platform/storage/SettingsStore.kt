package com.yk.photocritic.platform.storage

import com.yk.photocritic.core.providers.ProviderId
import kotlinx.serialization.Serializable

/** `settings.json` (§9). Provider IDs are their wire names. */
@Serializable
data class Settings(
    val schemaVersion: Int = 1,
    val provider: String? = null,
    /** The selected model per provider. */
    val models: Map<String, String> = emptyMap(),
    val shareUsage: Boolean = false,
    val onboardingCompletedAt: String? = null,
    val updatedAt: String? = null,
) {
    val providerId: ProviderId? get() = ProviderId.entries.firstOrNull { it.wire == provider }

    fun modelFor(provider: ProviderId): String? = models[provider.wire]
}

class SettingsStore(
    private val files: JsonFiles,
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun read(): Settings = files.read(FILE, Settings.serializer(), ::Settings)

    /** Throws [java.io.IOException] when the write fails. */
    suspend fun update(change: (Settings) -> Settings): Settings =
        files.update(FILE, Settings.serializer(), ::Settings) { change(it).copy(updatedAt = UtcTime.iso(now())) }

    suspend fun select(
        provider: ProviderId,
        modelId: String,
    ): Settings = update { it.copy(provider = provider.wire, models = it.models + (provider.wire to modelId)) }

    suspend fun setShareUsage(on: Boolean): Settings = update { it.copy(shareUsage = on) }

    suspend fun completeOnboarding(): Settings = update { it.copy(onboardingCompletedAt = it.onboardingCompletedAt ?: UtcTime.iso(now())) }

    companion object {
        const val FILE = "settings.json"
    }
}
