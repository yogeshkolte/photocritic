package com.yk.photocritic.core.providers.shared

import com.yk.photocritic.core.RepoRoot
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RedactTest {
    @Serializable
    data class Case(
        val name: String,
        val input: String,
        val expected: String,
        val userKey: String? = null,
    )

    @Serializable
    data class Cases(
        val description: String,
        val cases: List<Case>,
    )

    @Test
    fun `every case in shared redaction cases json`() {
        val cases = Json.decodeFromString(Cases.serializer(), RepoRoot.shared("redaction/cases.json")).cases
        for (case in cases) assertEquals(case.expected, Redact.text(case.input, case.userKey), case.name)
    }
}
