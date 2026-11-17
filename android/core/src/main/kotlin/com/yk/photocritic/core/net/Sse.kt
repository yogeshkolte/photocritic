package com.yk.photocritic.core.net

/** One server-sent event: the `event:` name, if any, and the joined `data:` lines. */
data class SseEvent(
    val event: String?,
    val data: String,
)

/**
 * Server-sent-events line parser (design §4). Feed it each line; it returns an
 * event at each blank line. Comment lines (`:`) and unknown fields are ignored.
 */
class SseParser {
    private var event: String? = null
    private val data = StringBuilder()

    fun feed(line: String): SseEvent? {
        if (line.isEmpty()) return dispatch()
        if (line.startsWith(":")) return null
        val colon = line.indexOf(':')
        val field = if (colon < 0) line else line.substring(0, colon)
        var value = if (colon < 0) "" else line.substring(colon + 1)
        if (value.startsWith(" ")) value = value.substring(1)
        when (field) {
            "event" -> event = value
            "data" -> {
                if (data.isNotEmpty()) data.append('\n')
                data.append(value)
            }
        }
        return null
    }

    /** Call at the end of the stream: returns an event that had no closing blank line. */
    fun finish(): SseEvent? = dispatch()

    private fun dispatch(): SseEvent? {
        val result = if (data.isEmpty() && event == null) null else SseEvent(event, data.toString())
        event = null
        data.setLength(0)
        return result
    }
}
