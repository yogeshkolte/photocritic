package com.yk.photocritic.core.providers.shared

import com.yk.photocritic.core.RepoRoot
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ExtractedTextTest {
    @Serializable
    data class Expected(
        val type: String,
        val text: String,
    )

    @Serializable
    data class Case(
        val name: String,
        val answer: String,
        val segments: List<Expected>,
    )

    @Serializable
    data class Cases(
        val description: String,
        val cases: List<Case>,
    )

    private fun asExpected(segment: Segment) =
        when (segment) {
            is Segment.Text -> Expected("text", segment.text)
            is Segment.Card -> Expected("card", segment.text)
        }

    @Test
    fun `every case in shared extracted-text json`() {
        val cases = Json.decodeFromString(Cases.serializer(), RepoRoot.shared("vectors/extracted-text.json")).cases
        for (case in cases) assertEquals(case.segments, ExtractedText.parse(case.answer).map(::asExpected), case.name)
    }

    @Test
    fun `windows line endings close a card`() {
        assertEquals(listOf(Segment.Card("A")), ExtractedText.parse("```text\r\nA\r\n```\r"))
    }
}
