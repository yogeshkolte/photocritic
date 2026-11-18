package com.yk.photocritic.core.config

import com.yk.photocritic.core.RepoRoot
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AppVersionTest {
    private val vectors = Json.parseToJsonElement(RepoRoot.shared("vectors/app-version.json")).jsonObject

    @Test
    fun `comparisons from shared vectors`() {
        for (case in vectors.getValue("comparisons").jsonArray) {
            val c = case.jsonObject
            val a = AppVersion.parse(c.getValue("a").jsonPrimitive.content)!!
            val b = AppVersion.parse(c.getValue("b").jsonPrimitive.content)!!
            assertEquals(c.getValue("expected").jsonPrimitive.int, a.compareTo(b).coerceIn(-1, 1), "$a vs $b")
        }
    }

    @Test
    fun `invalid strings from shared vectors`() {
        for (text in vectors.getValue("invalid").jsonArray) {
            assertNull(AppVersion.parse(text.jsonPrimitive.content), text.toString())
        }
    }

    @Test
    fun `prints as dotted numbers`() {
        assertEquals("1.10.0", AppVersion.parse("1.10.0").toString())
    }
}
