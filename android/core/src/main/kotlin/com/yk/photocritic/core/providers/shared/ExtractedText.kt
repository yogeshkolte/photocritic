package com.yk.photocritic.core.providers.shared

/** A piece of an answer: plain text, or a "Text in photo" card (G3, D36). */
sealed interface Segment {
    data class Text(
        val text: String,
    ) : Segment

    data class Card(
        val text: String,
    ) : Segment
}

/**
 * Splits an answer into plain text and cards (§3, "Extracted text"). The
 * base prompt asks the model to wrap text copied from the photo in a fence
 * tagged `text`: a line that is only ```` ```text ```` or `~~~text`, closed by
 * a line that is only the same fence. Other fenced blocks stay plain text. A
 * fence that is not yet closed (an answer still streaming) is plain text
 * until it closes. Runs on every streamed update, so it is linear in the
 * answer length. Tested against `shared/vectors/extracted-text.json`.
 */
object ExtractedText {
    private val OPEN = Regex("""^(```|~~~)text\s*$""")

    fun parse(answer: String): List<Segment> {
        val lines = answer.split("\n")
        val segments = mutableListOf<Segment>()
        val plain = mutableListOf<String>()
        var i = 0
        while (i < lines.size) {
            val fence = OPEN.find(lines[i].trimEnd('\r'))?.groupValues?.get(1)
            val close = fence?.let { f -> (i + 1 until lines.size).firstOrNull { lines[it].trimEnd('\r').trim() == f } }
            if (fence == null || close == null) {
                plain += lines[i]
                i++
                continue
            }
            flush(plain, segments)
            segments += Segment.Card(lines.subList(i + 1, close).joinToString("\n") { it.trimEnd('\r') })
            i = close + 1
        }
        flush(plain, segments)
        return segments
    }

    /** Copy and Share give the card text exactly; plain text is trimmed of blank edges. */
    private fun flush(
        plain: MutableList<String>,
        segments: MutableList<Segment>,
    ) {
        val text = plain.joinToString("\n").trim('\n', '\r', ' ')
        if (text.isNotEmpty()) segments += Segment.Text(text)
        plain.clear()
    }
}
