// Tuning constants (design Technical Plan §8a). These are not Remote Config
// defaults: no Remote Config parameter overrides them. Values marked
// PROVISIONAL are placeholders until the Phase 0 measurements set them;
// update the §8a table in docs/designs/photocritic.md at the same time.

export const CONFIG_FETCH_TIMEOUT_MS = 10_000;
export const DEFAULT_LONG_EDGE_PX = 2048;
export const JPEG_QUALITY = 0.8;

export const HISTORY_TURNS_CAP = 10; // PROVISIONAL
export const MAX_QUESTION_CHARS = 2_000; // PROVISIONAL
export const CONNECT_TIMEOUT_MS = 15_000; // PROVISIONAL
export const REQUEST_TIMEOUT_MS = 120_000; // PROVISIONAL

export const MAX_REPORT_CONVERSATION_CHARS = 20_000; // PROVISIONAL
export const REPORT_DAILY_LIMIT_PER_INSTALL = 10; // PROVISIONAL
export const REPORT_RETENTION_DAYS = 90; // PROVISIONAL
export const REPORT_WRITE_TIMEOUT_MS = 10_000; // PROVISIONAL
