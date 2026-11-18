package com.yk.photocritic.providers

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yk.photocritic.core.config.Tuning
import com.yk.photocritic.core.net.HostPolicy
import com.yk.photocritic.core.net.ProviderHttp
import com.yk.photocritic.core.providers.FinishReason
import com.yk.photocritic.core.providers.ModelSettings
import com.yk.photocritic.core.providers.NeutralTurn
import com.yk.photocritic.core.providers.PcPhase
import com.yk.photocritic.core.providers.PromptKind
import com.yk.photocritic.core.providers.SendCopy
import com.yk.photocritic.core.providers.TurnEvent
import com.yk.photocritic.core.providers.TurnRequest
import com.yk.photocritic.core.providers.gemini.GeminiClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The Gemini client acceptance tests on a phone (Phase 0, "Client acceptance
 * tests on a phone"). The transport checks run against a mock server on the
 * phone; the live checks call Gemini with the key the run script writes to
 * the app's `files/pc-gemini-test.key` through stdin (an instrumentation
 * argument would be logged by adbd), and are skipped without it. Nothing
 * logs the key.
 */
@RunWith(AndroidJUnit4::class)
class GeminiPhoneTest {
    private val testAssets = InstrumentationRegistry.getInstrumentation().context.assets
    private val liveKey: String? =
        File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "pc-gemini-test.key")
            .takeIf { it.exists() }
            ?.readText()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    private val fakeKey = "AQ.phone-test-key-0123456789abcdefghij"
    private val jpeg = testAssets.open("test.jpg").use { it.readBytes() }

    private fun request(
        question: String = "What does the sign say?",
        kind: PromptKind = PromptKind.EXTRACT,
        maxOutputTokens: Int = TEMPLATE_MAX_OUTPUT_TOKENS,
        history: List<NeutralTurn> = emptyList(),
    ) = TurnRequest(
        model = ModelSettings(MODEL, maxOutputTokens, contextTokens = 1_000_000, thinkingLevel = TEMPLATE_THINKING_LEVEL),
        history = history,
        question = question,
        image = SendCopy(jpeg, 0, 0, "MEDIA_RESOLUTION_HIGH"),
        promptKind = kind,
    )

    private fun mockClient(
        server: MockWebServer,
        hosts: Set<String> = setOf(server.hostName),
    ) = GeminiClient(
        ProviderHttp(HostPolicy(hosts, requireHttps = false), Tuning.CONNECT_TIMEOUT_MS, Tuning.REQUEST_TIMEOUT_MS),
        baseUrl = server.url("").toString().trimEnd('/'),
    )

    private fun liveClient() =
        GeminiClient(ProviderHttp(connectTimeoutMs = Tuning.CONNECT_TIMEOUT_MS, requestTimeoutMs = Tuning.REQUEST_TIMEOUT_MS))

    private fun withServer(block: suspend (MockWebServer) -> Unit) =
        runBlocking {
            MockWebServer().use { server ->
                server.start()
                block(server)
            }
        }

    private fun log(message: String) = Log.i(TAG, message)

    private fun assertNoKey(
        events: List<TurnEvent>,
        key: String,
    ) = events.forEach { assertFalse("key in event", it.toString().contains(key)) }

    // --- On the phone's network stack, against a local server ---

    @Test
    fun keyOnlyInHeaderAndImageCarriesResolution() =
        withServer { server ->
            server.enqueue(sse("""{"candidates":[{"content":{"parts":[{"text":"Hi"}]},"finishReason":"STOP"}],"responseId":"r1"}"""))
            val events = mockClient(server).send(request(), fakeKey).toList()
            val sent = server.takeRequest()
            assertEquals(fakeKey, sent.headers["x-goog-api-key"])
            assertFalse(sent.url.toString().contains(fakeKey))
            val body = sent.body!!.utf8()
            assertFalse(body.contains(fakeKey))
            val json = Json.parseToJsonElement(body).jsonObject
            assertEquals("MEDIA_RESOLUTION_HIGH", json["generationConfig"]!!.jsonObject["mediaResolution"]!!.jsonPrimitive.content)
            assertTrue(body.contains("\"inlineData\""))
            assertNoKey(events, fakeKey)
            assertEquals(1, server.requestCount)
            log("mock key-in-header: ok, body ${body.length} chars")
        }

    @Test
    fun disallowedHostIsRefusedBeforeSending() =
        withServer { server ->
            val events = mockClient(server, hosts = setOf("generativelanguage.googleapis.com")).send(request(), fakeKey).toList()
            val error = (events.single() as TurnEvent.Error).error
            assertEquals("host-not-allowed", error.providerCode)
            assertEquals(PcPhase.BEFORE_RESPONSE, error.phase)
            assertEquals(0, server.requestCount)
            log("mock disallowed host: ok, 0 requests")
        }

    @Test
    fun redirectIsNotFollowed() =
        withServer { server ->
            MockWebServer().use { target ->
                target.start()
                server.enqueue(
                    MockResponse
                        .Builder()
                        .code(302)
                        .addHeader("Location", target.url("/x").toString())
                        .build(),
                )
                val events = mockClient(server, hosts = setOf(server.hostName, target.hostName)).send(request(), fakeKey).toList()
                val error = (events.single() as TurnEvent.Error).error
                assertEquals(302, error.httpStatus)
                assertEquals(1, server.requestCount)
                assertEquals(0, target.requestCount)
                log("mock redirect: ok, status 302, target got 0 requests")
            }
        }

    @Test
    fun exactlyOneRequestPerQuestionIncludingErrors() =
        withServer { server ->
            for (status in listOf(500, 503, 429, 400)) {
                val before = server.requestCount
                server.enqueue(
                    MockResponse
                        .Builder()
                        .code(status)
                        .body("""{"error":{"code":$status,"status":"X$status","message":"m"}}""")
                        .build(),
                )
                val events = mockClient(server).send(request(), fakeKey).toList()
                val error = (events.single() as TurnEvent.Error).error
                assertEquals(status, error.httpStatus)
                assertEquals("X$status", error.providerCode)
                assertEquals(1, server.requestCount - before)
            }
            // A stream cut off mid-answer is not retried either.
            server.enqueue(sse("""{"candidates":[{"content":{"parts":[{"text":"partial"}]}}]}"""))
            val events = mockClient(server).send(request(), fakeKey).toList()
            assertEquals(PcPhase.MID_STREAM, (events.last() as TurnEvent.Error).error.phase)
            assertEquals(5, server.requestCount)
            log("mock one request per question: ok over 5 failures")
        }

    @Test
    fun recordedStreamsGiveTheExpectedEvents() {
        val names =
            listOf("provider-streams/gemini", "provider-errors/gemini").flatMap { folder ->
                testAssets
                    .list(folder)
                    .orEmpty()
                    .filter { it.endsWith(".json") }
                    .map { "$folder/$it" }
            }
        assertTrue("recordings missing from the test assets", names.size >= 5)
        for (name in names) {
            val fixture = Json.parseToJsonElement(testAssets.open(name).use { it.readBytes().decodeToString() }).jsonObject
            withServer { server ->
                val response = MockResponse.Builder().code(fixture.getValue("status").jsonPrimitive.int)
                fixture.getValue("headers").jsonObject.forEach { (k, v) -> response.addHeader(k, v.jsonPrimitive.content) }
                val body =
                    fixture["sseLines"]?.jsonArray?.joinToString("") { it.jsonPrimitive.content + "\n" }
                        ?: fixture.getValue("body").jsonPrimitive.content
                server.enqueue(response.body(body).build())
                val events = mockClient(server).send(request(), fakeKey).toList()
                assertEquals(name, fixture.getValue("expected").jsonArray.toList(), events.map(::asJson))
                assertNoKey(events, fakeKey)
                assertEquals(1, server.requestCount)
            }
        }
        log("recorded streams: ok, ${names.size} recordings")
    }

    // --- Live, against Gemini ---

    @Test
    fun liveTextStreamsInSeveralDeltas() =
        runBlocking(Dispatchers.Default) {
            val key = liveKey
            assumeTrue("no live key", key != null)
            val start = SystemClock.elapsedRealtime()
            var firstMs = -1L
            val events = mutableListOf<TurnEvent>()
            liveClient()
                .send(
                    request(
                        question = "Describe this image in about 150 words.",
                        kind = PromptKind.FREE,
                    ),
                    key!!,
                ).collect {
                    if (firstMs < 0 && it is TurnEvent.Text) firstMs = SystemClock.elapsedRealtime() - start
                    events += it
                }
            val totalMs = SystemClock.elapsedRealtime() - start
            val deltas = events.filterIsInstance<TurnEvent.Text>()
            val done = events.last()
            log(
                "live stream: ${deltas.size} deltas, ${deltas.sumOf {
                    it.delta.length
                }} chars, first text ${firstMs}ms, total ${totalMs}ms, last=$done",
            )
            assertTrue("expected Done, got $done", done is TurnEvent.Done)
            done as TurnEvent.Done
            assertEquals(FinishReason.STOP, done.finishReason)
            assertNotNull("requestId", done.requestId)
            assertTrue("only ${deltas.size} delta(s)", deltas.size > 1)
            assertNoKey(events, key)
        }

    @Test
    fun liveInvalidKeyGivesStatusAndCodeWithoutTheKey() =
        runBlocking(Dispatchers.Default) {
            assumeTrue("no live key", liveKey != null)
            val events = liveClient().send(request(), fakeKey).toList()
            val error = (events.single() as TurnEvent.Error).error
            log(
                "live invalid key: phase=${error.phase.wire} status=${error.httpStatus} code=${error.providerCode} requestId=${error.requestId}",
            )
            assertEquals(PcPhase.BEFORE_RESPONSE, error.phase)
            assertEquals(401, error.httpStatus)
            assertNotNull(error.providerCode)
            assertNoKey(events, fakeKey)
        }

    @Test
    fun liveUnknownModelGivesStatusAndCode() =
        runBlocking(Dispatchers.Default) {
            val key = liveKey
            assumeTrue("no live key", key != null)
            val unknown =
                TurnRequest(
                    ModelSettings("gemini-no-such-model", 256, 1_000_000),
                    emptyList(),
                    "Hi",
                    SendCopy(jpeg, 0, 0, "MEDIA_RESOLUTION_LOW"),
                    PromptKind.FREE,
                )
            val events = liveClient().send(unknown, key!!).toList()
            val error = (events.single() as TurnEvent.Error).error
            log("live unknown model: status=${error.httpStatus} code=${error.providerCode}")
            assertEquals(404, error.httpStatus)
            assertNotNull(error.providerCode)
            assertNoKey(events, key)
        }

    @Test
    fun liveStopEndsTheStreamAndTheRequest() =
        runBlocking(Dispatchers.Default) {
            val key = liveKey
            assumeTrue("no live key", key != null)
            // Completes on the first text, or on an end that came before any text.
            val first = CompletableDeferred<TurnEvent>()
            val after = mutableListOf<TurnEvent>()
            var stopped = false
            val job =
                launch {
                    liveClient()
                        .send(
                            request(
                                question = "Describe this image in at least 600 words.",
                                kind = PromptKind.FREE,
                            ),
                            key!!,
                        ).collect {
                            if (stopped) after += it
                            first.complete(it)
                        }
                }
            val firstEvent = withTimeout(Tuning.REQUEST_TIMEOUT_MS) { first.await() }
            assertTrue("stream ended before any text: $firstEvent", firstEvent is TurnEvent.Text)
            val stopAt = SystemClock.elapsedRealtime()
            stopped = true
            job.cancel()
            job.join()
            val stopMs = SystemClock.elapsedRealtime() - stopAt
            log("live stop: collector ended ${stopMs}ms after Stop, ${after.size} events after Stop")
            assertTrue("Stop took ${stopMs}ms", stopMs < STOP_LIMIT_MS)
            assertTrue("events after Stop: $after", after.none { it is TurnEvent.Done || it is TurnEvent.Error })
        }

    private fun sse(vararg events: String) =
        MockResponse
            .Builder()
            .code(200)
            .addHeader("Content-Type", "text/event-stream")
            .body(events.joinToString("") { "data: $it\r\n\r\n" })
            .build()

    private fun asJson(event: TurnEvent): JsonElement =
        when (event) {
            is TurnEvent.Text ->
                buildJsonObject {
                    put("type", "text")
                    put("delta", event.delta)
                }
            is TurnEvent.Done ->
                buildJsonObject {
                    put("type", "done")
                    put("finishReason", event.finishReason.name.lowercase())
                    put("servedModel", event.servedModel)
                    put("requestId", event.requestId)
                }
            is TurnEvent.Error ->
                buildJsonObject {
                    put("type", "error")
                    put(
                        "error",
                        buildJsonObject {
                            put("phase", event.error.phase.wire)
                            put("httpStatus", event.error.httpStatus?.let(::JsonPrimitive) ?: JsonNull)
                            put("providerCode", event.error.providerCode)
                            put("requestId", event.error.requestId)
                        },
                    )
                }
        }

    private companion object {
        const val TAG = "PC-PHASE1"
        const val MODEL = "gemini-4-hydrogen"
        const val STOP_LIMIT_MS = 2_000L

        /** `maxOutputTokens` and `thinkingLevel` for the model in `shared/remote-config/template.json`. */
        const val TEMPLATE_MAX_OUTPUT_TOKENS = 8192
        const val TEMPLATE_THINKING_LEVEL = "low"
    }
}
