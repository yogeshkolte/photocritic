import Foundation

/// Tuning constants (design §8a). Changed only by a new build. Both apps hold
/// the same values; `TuningTests` checks them against `shared/tuning.json`.
/// Values with status "provisional" there are set by the Phase 0 measurements.
public enum Tuning {
    public static let configFetchTimeoutMs = 10_000
    public static let defaultLongEdgePx = 2048
    public static let jpegQuality = 0.8

    public static let historyTurnsCap = 10
    public static let maxQuestionChars = 2000
    public static let connectTimeoutMs = 15_000
    public static let requestTimeoutMs = 120_000

    public static let maxReportConversationChars = 20_000
    public static let reportDailyLimitPerInstall = 10
    public static let reportRetentionDays = 90
    public static let reportWriteTimeoutMs = 10_000

    /// Every constant by its name in `shared/tuning.json`.
    public static let all: [String: Double] = [
        "CONFIG_FETCH_TIMEOUT_MS": Double(configFetchTimeoutMs),
        "DEFAULT_LONG_EDGE_PX": Double(defaultLongEdgePx),
        "JPEG_QUALITY": jpegQuality,
        "HISTORY_TURNS_CAP": Double(historyTurnsCap),
        "MAX_QUESTION_CHARS": Double(maxQuestionChars),
        "CONNECT_TIMEOUT_MS": Double(connectTimeoutMs),
        "REQUEST_TIMEOUT_MS": Double(requestTimeoutMs),
        "MAX_REPORT_CONVERSATION_CHARS": Double(maxReportConversationChars),
        "REPORT_DAILY_LIMIT_PER_INSTALL": Double(reportDailyLimitPerInstall),
        "REPORT_RETENTION_DAYS": Double(reportRetentionDays),
        "REPORT_WRITE_TIMEOUT_MS": Double(reportWriteTimeoutMs),
    ]
}
