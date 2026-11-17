package com.yk.photocritic.core.net

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SseTest {
    private fun parse(vararg lines: String): List<SseEvent> {
        val parser = SseParser()
        return lines.mapNotNull { parser.feed(it) } + listOfNotNull(parser.finish())
    }

    @Test
    fun `data-only events end at blank lines`() {
        assertEquals(
            listOf(SseEvent(null, """{"a":1}"""), SseEvent(null, """{"b":2}""")),
            parse("""data: {"a":1}""", "", """data: {"b":2}""", ""),
        )
    }

    @Test
    fun `named events and multi-line data`() {
        assertEquals(
            listOf(SseEvent("content_block_delta", "line1\nline2")),
            parse("event: content_block_delta", "data: line1", "data: line2", ""),
        )
    }

    @Test
    fun `comments and unknown fields are ignored`() {
        assertEquals(listOf(SseEvent(null, "x")), parse(": keep-alive", "id: 7", "retry: 10", "data: x", ""))
    }

    @Test
    fun `an event without a closing blank line is returned by finish`() {
        assertEquals(listOf(SseEvent(null, "[DONE]")), parse("data: [DONE]"))
    }

    @Test
    fun `value without a space after the colon`() {
        assertEquals(listOf(SseEvent(null, "x")), parse("data:x", ""))
    }

    @Test
    fun `blank lines alone produce nothing`() {
        val parser = SseParser()
        assertNull(parser.feed(""))
        assertNull(parser.finish())
    }
}
