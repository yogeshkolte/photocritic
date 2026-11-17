package com.yk.photocritic.core.net

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** Design §4 rules, against a local mock server (Verification Plan: client acceptance tests). */
class ProviderHttpTest {
    private lateinit var server: MockWebServer

    @BeforeEach
    fun start() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun stop() {
        server.close()
    }

    private fun http(hosts: Set<String> = setOf(server.hostName)) =
        ProviderHttp(HostPolicy(hosts, requireHttps = false), connectTimeoutMs = 2_000, requestTimeoutMs = 5_000)

    private fun request(requestIdHeader: String? = "x-request-id") =
        ProviderRequest(server.url("/v1/test").toString(), mapOf("x-test-key" to "secret"), "{}", requestIdHeader)

    @Test
    fun `a host outside the policy is refused before anything is sent`() {
        assertThrows<HostNotAllowedException> {
            runBlocking { http(hosts = setOf("api.example.invalid")).exchange(request()).toList() }
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `the production policy refuses plain http and unknown hosts`() {
        val policy = HostPolicy.PRODUCTION
        assertTrue(
            policy.allows(
                okhttp3.HttpUrl
                    .Builder()
                    .scheme("https")
                    .host("api.openai.com")
                    .build(),
            ),
        )
        assertTrue(
            !policy.allows(
                okhttp3.HttpUrl
                    .Builder()
                    .scheme("http")
                    .host("api.openai.com")
                    .build(),
            ),
        )
        assertTrue(
            !policy.allows(
                okhttp3.HttpUrl
                    .Builder()
                    .scheme("https")
                    .host("api.openai.com.evil.example")
                    .build(),
            ),
        )
    }

    @Test
    fun `a redirect is not followed`() =
        runBlocking {
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(302)
                    .addHeader("Location", server.url("/elsewhere"))
                    .build(),
            )
            val chunks = http().exchange(request()).toList()
            assertEquals(302, (chunks.single() as HttpChunk.Head).status)
            assertEquals(1, server.requestCount)
        }

    @Test
    fun `an error is not retried and its body and request ID are returned`() =
        runBlocking {
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(503)
                    .addHeader("x-request-id", "req_1")
                    .body("""{"error":"overloaded"}""")
                    .build(),
            )
            val head = http().exchange(request()).toList().single() as HttpChunk.Head
            assertEquals(HttpChunk.Head(503, "req_1", """{"error":"overloaded"}"""), head)
            assertEquals(1, server.requestCount)
        }

    @Test
    fun `a 2xx body is returned line by line with the headers sent as given`() =
        runBlocking {
            server.enqueue(
                MockResponse
                    .Builder()
                    .code(200)
                    .body("data: a\n\ndata: b\n")
                    .build(),
            )
            val chunks = http().exchange(request(requestIdHeader = null)).toList()
            assertEquals(
                listOf(HttpChunk.Head(200, null, null), HttpChunk.Line("data: a"), HttpChunk.Line(""), HttpChunk.Line("data: b")),
                chunks,
            )
            val recorded = server.takeRequest()
            assertEquals("secret", recorded.headers["x-test-key"])
            assertEquals(null, recorded.headers["Cookie"])
        }
}
