package com.yk.photocritic.platform.storage

import kotlinx.serialization.Serializable

@Serializable
data class InstallInfo(
    val installedAt: String,
    val appVersionAtInstall: String,
)

/**
 * Reinstall handling (§9, file 31). Android removes app files on uninstall,
 * but a Keystore key or a restored file could outlive them, so a missing
 * marker means a fresh install: every key is deleted before the marker is
 * written. An unreadable marker is not "missing" and wipes nothing.
 */
class InstallMarker(
    private val files: JsonFiles,
    private val keys: KeyStore,
    private val now: () -> Long = System::currentTimeMillis,
) {
    enum class Outcome { FRESH_INSTALL, EXISTING, UNREADABLE }

    suspend fun ensure(appVersion: String): Outcome =
        when (files.readState(FILE, InstallInfo.serializer())) {
            is FileRead.Ok -> Outcome.EXISTING
            FileRead.Unreadable -> Outcome.UNREADABLE
            FileRead.Missing -> {
                keys.deleteAll()
                files.write(FILE, InstallInfo.serializer(), InstallInfo(UtcTime.iso(now()), appVersion))
                Outcome.FRESH_INSTALL
            }
        }

    companion object {
        const val FILE = "install.json"
    }
}
