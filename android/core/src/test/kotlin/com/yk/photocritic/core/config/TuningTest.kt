package com.yk.photocritic.core.config

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

class TuningTest {
    private val shared =
        Json
            .parseToJsonElement(File(System.getProperty("pc.repoRoot"), "shared/tuning.json").readText())
            .jsonObject

    @Test
    fun `constants have the same names as shared tuning json`() {
        assertEquals(shared.keys, Tuning.all.keys)
    }

    @Test
    fun `constants have the same values as shared tuning json`() {
        for ((name, value) in Tuning.all) {
            val expected =
                shared
                    .getValue(name)
                    .jsonObject
                    .getValue("value")
                    .jsonPrimitive.double
            assertEquals(expected, value.toDouble(), name)
        }
    }
}
