package com.yk.photocritic.platform.storage

import kotlinx.serialization.Serializable

/** `reports.json` (§9): sent report IDs and the daily counter. */
@Serializable
data class ReportsFile(
    val schemaVersion: Int = 1,
    /** Newest last, at most [ReportStore.MAX_IDS]. */
    val sentIds: List<String> = emptyList(),
    /** UTC day of [countToday], `yyyy-MM-dd`. */
    val day: String? = null,
    val countToday: Int = 0,
)

class ReportStore(
    private val files: JsonFiles,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** Reports sent today (UTC). */
    suspend fun countToday(): Int {
        val file = files.read(FILE, ReportsFile.serializer(), ::ReportsFile)
        return if (file.day == UtcTime.day(now())) file.countToday else 0
    }

    /** Throws [java.io.IOException] when the write fails. */
    suspend fun recordSent(reportId: String) {
        val today = UtcTime.day(now())
        files.update(FILE, ReportsFile.serializer(), ::ReportsFile) { file ->
            ReportsFile(
                sentIds = (file.sentIds + reportId).takeLast(MAX_IDS),
                day = today,
                countToday = (if (file.day == today) file.countToday else 0) + 1,
            )
        }
    }

    suspend fun sentIds(): List<String> = files.read(FILE, ReportsFile.serializer(), ::ReportsFile).sentIds

    companion object {
        const val FILE = "reports.json"
        const val MAX_IDS = 200
    }
}
