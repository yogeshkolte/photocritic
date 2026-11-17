package com.yk.photocritic.core.providers.shared

/**
 * Removes keys, URL query strings and file paths from text shown in "Show
 * details" or sent to telemetry (design file 14). Cases: `shared/redaction/cases.json`.
 */
object Redact {
    const val MAX_DETAIL_CHARS = 200

    private val keyPatterns =
        listOf(
            Regex("""sk-ant-[A-Za-z0-9_-]{16,}"""),
            Regex("""sk-[A-Za-z0-9_-]{16,}"""),
            Regex("""AIza[0-9A-Za-z_-]{35}"""),
            Regex("""AQ\.[0-9A-Za-z_-]{20,}"""),
            Regex("""Bearer\s+\S+"""),
        )
    private val urlQuery = Regex("""(https?://[^\s?#]+)\?[^\s#]*""")
    private val uri = Regex("""\b(?:file|content)://\S+""")
    private val absolutePath = Regex("""(?<![\w.:/])/(?:[\w.-]+/)+[\w.-]+""")

    fun text(
        input: String,
        userKey: String? = null,
    ): String {
        var out = input
        if (!userKey.isNullOrEmpty()) out = out.replace(userKey, "[key]")
        for (pattern in keyPatterns) out = pattern.replace(out, "[key]")
        out = urlQuery.replace(out) { "${it.groupValues[1]}?[query]" }
        out = uri.replace(out, "[path]")
        out = absolutePath.replace(out, "[path]")
        return if (out.length <= MAX_DETAIL_CHARS) out else out.take(MAX_DETAIL_CHARS)
    }
}
