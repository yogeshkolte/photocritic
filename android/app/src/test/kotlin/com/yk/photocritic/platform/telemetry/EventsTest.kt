package com.yk.photocritic.platform.telemetry

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** [PcEvent] against `shared/telemetry/events.json` (file 33) and the Analytics naming rules. */
class EventsTest {
    private val shared =
        Json
            .parseToJsonElement(File(System.getProperty("pc.repoRoot"), "shared/telemetry/events.json").readText())
            .jsonObject
            .getValue("events")
            .jsonObject

    @Test
    fun sameEventsAndParametersAsTheSharedList() {
        assertEquals(shared.keys, PcEvent.entries.map { it.wire }.toSet())
        for (event in PcEvent.entries) {
            val params =
                shared
                    .getValue(event.wire)
                    .jsonObject
                    .getValue("params")
                    .jsonObject
                    .mapValues { it.value.jsonPrimitive.content }
            assertEquals(event.wire, params, event.params.mapValues { it.value.wire })
        }
    }

    @Test
    fun namesFollowAnalyticsRules() {
        val reserved = listOf("firebase_", "google_", "ga_")
        val name = Regex("[a-z][a-z0-9_]{0,39}")
        for (event in PcEvent.entries) {
            assertTrue(event.wire, event.wire.startsWith("pc_") && name.matches(event.wire))
            assertTrue(event.params.size <= MAX_PARAMS)
            for (param in event.params.keys) {
                assertTrue(param, name.matches(param) && reserved.none(param::startsWith))
            }
        }
    }

    private companion object {
        const val MAX_PARAMS = 25
    }
}
