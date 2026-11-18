package com.yk.photocritic.platform.telemetry

import com.yk.photocritic.FakeTelemetryBackend
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TelemetryTest {
    private val backend = FakeTelemetryBackend()
    private val telemetry = Telemetry(backend)

    private val fetch = mapOf("result" to "success", "config_version" to 3, "ms" to 120L)

    @Test
    fun nothingIsLoggedWhileSharingIsOff() {
        telemetry.log(PcEvent.CONFIG_FETCH, fetch)
        assertTrue(backend.events.isEmpty())
    }

    @Test
    fun sharingOnSetsTheInstanceIdAndLogs() =
        runBlocking {
            telemetry.setSharing(true)
            assertEquals(true, backend.collection)
            assertEquals("instance-1", backend.currentUserId)
            telemetry.log(PcEvent.CONFIG_FETCH, fetch)
            val (name, params) = backend.events.single()
            assertEquals("pc_config_fetch", name)
            assertEquals("success", params.getString("result"))
            assertEquals(3L, params.getLong("config_version"))
            assertEquals(120L, params.getLong("ms"))
        }

    @Test
    fun sharingOffClearsTheUserIdAndStopsLogging() =
        runBlocking {
            telemetry.setSharing(true)
            telemetry.setSharing(false)
            assertEquals(false, backend.collection)
            assertNull(backend.currentUserId)
            telemetry.log(PcEvent.CONFIG_FETCH, fetch)
            assertTrue(backend.events.isEmpty())
        }

    @Test
    fun stringsAreRedactedAndCut() =
        runBlocking {
            telemetry.setSharing(true)
            telemetry.log(
                PcEvent.REPORT_SUBMITTED,
                mapOf(
                    "provider" to "gemini",
                    "model" to "x".repeat(150),
                    "category" to "key AQ.abcdefghijklmnopqrstuvwxyz",
                    "result" to "sent",
                ),
            )
            val params = backend.events.single().second
            assertEquals("key [key]", params.getString("category"))
            assertEquals(Telemetry.MAX_STRING_PARAM, params.getString("model")!!.length)
        }

    @Test
    fun aNullValueIsLeftOut() =
        runBlocking {
            telemetry.setSharing(true)
            telemetry.log(PcEvent.CONFIG_FETCH, fetch + ("config_version" to null))
            assertTrue(
                !backend.events
                    .single()
                    .second
                    .containsKey("config_version"),
            )
        }

    @Test(expected = IllegalArgumentException::class)
    fun unlistedParametersAreRejected() {
        telemetry.log(PcEvent.CONFIG_FETCH, fetch + ("question" to "what is this?"))
    }

    @Test
    fun instanceIdIsReadEvenWithSharingOff() =
        runBlocking {
            assertEquals("instance-1", telemetry.instanceIdOrNull())
        }

    @Test
    fun uncaughtExceptionsReachCrashlyticsRedacted() {
        var received: Throwable? = null
        val handler = RedactingExceptionHandler { _, e -> received = e }
        val original = IllegalStateException("failed for sk-abcdefghijklmnopqrstuv", RuntimeException("at /data/user/0/app/files/x.json"))
        handler.uncaughtException(Thread.currentThread(), original)
        val r = received!!
        assertEquals("java.lang.IllegalStateException: failed for [key]", r.message)
        assertEquals("java.lang.RuntimeException: at [path]", r.cause!!.message)
        assertEquals(original.stackTrace.toList(), r.stackTrace.toList())
    }
}
