package com.yk.photocritic.core.config

/**
 * Tuning constants (design §8a). Changed only by a new build. Both apps hold
 * the same values; [TuningTest] checks them against `shared/tuning.json`.
 * Values with status "provisional" there are set by the Phase 0 measurements.
 */
object Tuning {
    const val CONFIG_FETCH_TIMEOUT_MS = 10_000L
    const val DEFAULT_LONG_EDGE_PX = 2048
    const val JPEG_QUALITY = 0.8

    const val HISTORY_TURNS_CAP = 10
    const val MAX_QUESTION_CHARS = 2000
    const val CONNECT_TIMEOUT_MS = 15_000L
    const val REQUEST_TIMEOUT_MS = 120_000L

    const val MAX_REPORT_CONVERSATION_CHARS = 20_000
    const val REPORT_DAILY_LIMIT_PER_INSTALL = 10
    const val REPORT_RETENTION_DAYS = 90
    const val REPORT_WRITE_TIMEOUT_MS = 10_000L

    /** Every constant by its name in `shared/tuning.json`. */
    val all: Map<String, Number> =
        mapOf(
            "CONFIG_FETCH_TIMEOUT_MS" to CONFIG_FETCH_TIMEOUT_MS,
            "DEFAULT_LONG_EDGE_PX" to DEFAULT_LONG_EDGE_PX,
            "JPEG_QUALITY" to JPEG_QUALITY,
            "HISTORY_TURNS_CAP" to HISTORY_TURNS_CAP,
            "MAX_QUESTION_CHARS" to MAX_QUESTION_CHARS,
            "CONNECT_TIMEOUT_MS" to CONNECT_TIMEOUT_MS,
            "REQUEST_TIMEOUT_MS" to REQUEST_TIMEOUT_MS,
            "MAX_REPORT_CONVERSATION_CHARS" to MAX_REPORT_CONVERSATION_CHARS,
            "REPORT_DAILY_LIMIT_PER_INSTALL" to REPORT_DAILY_LIMIT_PER_INSTALL,
            "REPORT_RETENTION_DAYS" to REPORT_RETENTION_DAYS,
            "REPORT_WRITE_TIMEOUT_MS" to REPORT_WRITE_TIMEOUT_MS,
        )
}
