package com.yk.photocritic.core.providers.gemini

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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Gemini client against a local server that speaks the §3a stream format.
 * Hand-written streams cover what cannot be recorded on demand (safety stop,
 * blocked prompt, mid-stream error); [GeminiContractTest] replays the recordings.
 */
class GeminiClientTest {
    private lateinit var server: MockWebServer
    private val key = "AQ.test-key-0123456789abcdefghij"

    @BeforeEach
    fun start() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun stop() {
        server.close()
    }

    private fun client() =
        GeminiClient(
            ProviderHttp(HostPolicy(setOf(server.hostName), requireHttps = false), 2_000, 5_000),
            baseUrl = server.url("").toString().trimEnd('/'),
        )

    private fun request(history: List<NeutralTurn> = emptyList()) =
        TurnRequest(
            model = ModelSettings("gemini-4-hydrogen", maxOutputTokens = 512, contextTokens = 1_000_000),
            history = history,
            question = "What is this?",
            image = SendCopy(byteArrayOf(1, 2, 3), 1, 1, "MEDIA_RESOLUTION_HIGH"),
            promptKind = PromptKind.FREE,
        )

    private fun stream(vararg events: String) =
        MockResponse
            .Builder()
            .code(200)
            .addHeader("Content-Type", "text/event-stream")
            .body(events.joinToString("") { "data: $it\r\n\r\n" })
            .build()

    private fun chunk(
        text: String,
        finish: String? = null,
    ) = """{"candidates":[{"content":{"parts":[{"text":"$text"}],"role":"model"}""" +
        (finish?.let { ""","finishReason":"$it"""" } ?: "") +
        """}],"modelVersion":"gemini-4-hydrogen-001","responseId":"resp_1"}"""

    @Test
    fun `streams text then done with the body responseId as request ID`() =
        runBlocking {
            server.enqueue(stream(chunk("A white "), chunk("square."), chunk("", "STOP")))
            val events = client().send(request(), key).toList()
            assertEquals(
                listOf(
                    TurnEvent.Text("A white "),
                    TurnEvent.Text("square."),
                    TurnEvent.Done(FinishReason.STOP, "gemini-4-hydrogen-001", "resp_1"),
                ),
                events,
            )
        }

    @Test
    fun `key only in the header, image and resolution in the body, one request`() =
        runBlocking {
            server.enqueue(stream(chunk("ok", "STOP")))
            client().send(request(listOf(NeutralTurn("First?", "First answer.", "img1"))), key).toList()
            val recorded = server.takeRequest()
            assertEquals(1, server.requestCount)
            assertEquals(key, recorded.headers["x-goog-api-key"])
            assertFalse(recorded.url.toString().contains(key))
            assertEquals("/v1beta/models/gemini-4-hydrogen:streamGenerateContent?alt=sse", recorded.target)
            val body = Json.parseToJsonElement(recorded.body!!.utf8()).jsonObject
            val contents = body.getValue("contents").jsonArray
            assertEquals(
                listOf("user", "model", "user"),
                contents.map {
                    it.jsonObject
                        .getValue("role")
                        .jsonPrimitive.content
                },
            )
            val firstParts = contents[0].jsonObject.getValue("parts").jsonArray
            assertEquals(
                "AQID",
                firstParts[0]
                    .jsonObject
                    .getValue("inlineData")
                    .jsonObject
                    .getValue("data")
                    .jsonPrimitive.content,
            )
            assertEquals(
                "First?",
                firstParts[1]
                    .jsonObject
                    .getValue("text")
                    .jsonPrimitive.content,
            )
            val config = body.getValue("generationConfig").jsonObject
            assertEquals("MEDIA_RESOLUTION_HIGH", config.getValue("mediaResolution").jsonPrimitive.content)
            assertEquals("512", config.getValue("maxOutputTokens").jsonPrimitive.content)
            assertTrue(body.toString().contains("fenced block"))
        }

    @Test
    fun `an invalid key gives a before-response error without the key in detail`() =
        runBlocking {
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(400)
                    .body("""{"error":{"code":400,"message":"API key not valid: $key","status":"INVALID_ARGUMENT"}}""")
                    .build(),
            )
            val error = (client().send(request(), key).toList().single() as TurnEvent.Error).error
            assertEquals(PcPhase.BEFORE_RESPONSE, error.phase)
            assertEquals(400, error.httpStatus)
            assertEquals("INVALID_ARGUMENT", error.providerCode)
            assertEquals(null, error.requestId)
            assertFalse(error.detail.contains(key))
            assertEquals(1, server.requestCount)
        }

    @Test
    fun `a safety stop is a finish failure that keeps the earlier text`() =
        runBlocking {
            server.enqueue(stream(chunk("Partial"), chunk("", "SAFETY")))
            val events = client().send(request(), key).toList()
            assertEquals(TurnEvent.Text("Partial"), events[0])
            val error = (events[1] as TurnEvent.Error).error
            assertEquals(PcPhase.FINISH, error.phase)
            assertEquals("SAFETY", error.providerCode)
            assertEquals("resp_1", error.requestId)
        }

    @Test
    fun `max tokens ends the answer normally`() =
        runBlocking {
            server.enqueue(stream(chunk("Long", "MAX_TOKENS")))
            assertEquals(FinishReason.LENGTH, (client().send(request(), key).toList().last() as TurnEvent.Done).finishReason)
        }

    @Test
    fun `a blocked prompt is a finish failure`() =
        runBlocking {
            server.enqueue(stream("""{"promptFeedback":{"blockReason":"PROHIBITED_CONTENT"},"responseId":"resp_2"}"""))
            val error = (client().send(request(), key).toList().single() as TurnEvent.Error).error
            assertEquals(PcPhase.FINISH, error.phase)
            assertEquals("PROHIBITED_CONTENT", error.providerCode)
        }

    @Test
    fun `a stream that ends without a finish reason is a mid-stream failure`() =
        runBlocking {
            server.enqueue(stream(chunk("Cut")))
            val events = client().send(request(), key).toList()
            assertEquals(PcPhase.MID_STREAM, (events.last() as TurnEvent.Error).error.phase)
        }

    @Test
    fun `an error event after a 200 is a mid-stream failure`() =
        runBlocking {
            server.enqueue(stream(chunk("Start"), """{"error":{"code":503,"message":"overloaded","status":"UNAVAILABLE"}}"""))
            val error = (client().send(request(), key).toList().last() as TurnEvent.Error).error
            assertEquals(PcPhase.MID_STREAM, error.phase)
            assertEquals("UNAVAILABLE", error.providerCode)
        }

    @Test
    fun `thought parts are not shown`() =
        runBlocking {
            server.enqueue(
                stream(
                    """{"candidates":[{"content":{"parts":[""" +
                        """{"text":"thinking","thought":true},{"text":"Answer"}]},"finishReason":"STOP"}]}""",
                ),
            )
            assertEquals(TurnEvent.Text("Answer"), client().send(request(), key).toList().first())
        }

    @Test
    fun `stopping after the first text ends the stream`() =
        runBlocking {
            server.enqueue(stream(chunk("One"), chunk("Two"), chunk("", "STOP")))
            assertEquals(TurnEvent.Text("One"), client().send(request(), key).first())
            assertEquals(1, server.requestCount)
        }

    @Test
    fun `a disallowed host is a before-response failure with no request sent`() =
        runBlocking {
            val blocked =
                GeminiClient(ProviderHttp(HostPolicy(setOf("api.example.invalid"), false), 2_000, 5_000), server.url("").toString())
            val error = (blocked.send(request(), key).toList().single() as TurnEvent.Error).error
            assertEquals(PcPhase.BEFORE_RESPONSE, error.phase)
            assertEquals("host-not-allowed", error.providerCode)
            assertEquals(0, server.requestCount)
        }
}
