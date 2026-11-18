package com.yk.photocritic.core.providers.gemini

import com.yk.photocritic.core.RepoRoot
import com.yk.photocritic.core.net.HostPolicy
import com.yk.photocritic.core.net.ProviderHttp
import com.yk.photocritic.core.providers.ModelSettings
import com.yk.photocritic.core.providers.NeutralTurn
import com.yk.photocritic.core.providers.PromptKind
import com.yk.photocritic.core.providers.SendCopy
import com.yk.photocritic.core.providers.TurnEvent
import com.yk.photocritic.core.providers.TurnRequest
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

/**
 * Contract tests against the recordings in `shared/` (design files 13 and 39):
 * every recorded Gemini response gives its expected events, and every
 * request input gives exactly the recorded body.
 */
class GeminiContractTest {
    private val key = "AQ.contract-test-key-0123456789abcdef"

    private fun withServer(block: suspend (MockWebServer, GeminiClient) -> Unit) =
        runBlocking {
            MockWebServer().use { server ->
                server.start()
                val http = ProviderHttp(HostPolicy(setOf(server.hostName), requireHttps = false), 2_000, 5_000)
                block(server, GeminiClient(http, server.url("").toString().trimEnd('/')))
            }
        }

    @TestFactory
    fun `recorded responses give the expected events`(): List<DynamicTest> =
        listOf("provider-streams/gemini", "provider-errors/gemini")
            .flatMap { folder ->
                RepoRoot.sharedJsonFiles(folder).map { name ->
                    DynamicTest.dynamicTest(
                        "$folder/$name",
                    ) { replay(Json.parseToJsonElement(RepoRoot.shared("$folder/$name")).jsonObject) }
                }
            }.also { assertTrue(it.size >= 5, "Gemini recordings missing from shared/") }

    private fun replay(fixture: JsonObject) =
        withServer { server, client ->
            val response = MockResponse.Builder().code(fixture.getValue("status").jsonPrimitive.int)
            fixture.getValue("headers").jsonObject.forEach { (name, value) -> response.addHeader(name, value.jsonPrimitive.content) }
            val body =
                fixture["sseLines"]?.jsonArray?.joinToString("") { it.jsonPrimitive.content + "\n" }
                    ?: fixture.getValue("body").jsonPrimitive.content
            server.enqueue(response.body(body).build())
            val events = client.send(request(input("normal")), key).toList()
            assertEquals(fixture.getValue("expected").jsonArray.toList(), events.map(::asJson))
            events.filterIsInstance<TurnEvent.Error>().forEach { assertFalse(it.error.detail.contains(key)) }
            assertEquals(1, server.requestCount)
        }

    @TestFactory
    fun `request inputs give the recorded bodies`(): List<DynamicTest> =
        RepoRoot
            .sharedJsonFiles("provider-requests/gemini")
            .filter { it.endsWith(".input.json") }
            .map { it.removeSuffix(".input.json") }
            .map { name ->
                DynamicTest.dynamicTest(name) {
                    withServer { server, client ->
                        server.enqueue(
                            MockResponse
                                .Builder()
                                .code(200)
                                .body("")
                                .build(),
                        )
                        client.send(request(input(name)), key).toList()
                        val sent = Json.parseToJsonElement(server.takeRequest().body!!.utf8())
                        assertEquals(Json.parseToJsonElement(RepoRoot.shared("provider-requests/gemini/$name.json")), sent)
                    }
                }
            }.also { assertTrue(it.isNotEmpty(), "Gemini request fixtures missing from shared/") }

    private fun input(name: String) = Json.parseToJsonElement(RepoRoot.shared("provider-requests/gemini/$name.input.json")).jsonObject

    private fun request(input: JsonObject): TurnRequest {
        fun string(name: String) = input.getValue(name).jsonPrimitive.content
        return TurnRequest(
            model = ModelSettings(string("modelId"), input.getValue("maxOutputTokens").jsonPrimitive.int, contextTokens = 1_000_000),
            history =
                input.getValue("history").jsonArray.mapIndexed { i, turn ->
                    val t = turn.jsonObject
                    NeutralTurn(t.getValue("question").jsonPrimitive.content, t.getValue("answerText").jsonPrimitive.content, "img$i")
                },
            question = string("question"),
            image = SendCopy(RepoRoot.sharedBytes(string("image").removePrefix("shared/")), 0, 0, string("imageDetail")),
            promptKind = PromptKind.valueOf(string("promptKind").uppercase()),
        )
    }

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
}
