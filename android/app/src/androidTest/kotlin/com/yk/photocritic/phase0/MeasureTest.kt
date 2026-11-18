package com.yk.photocritic.phase0

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.yk.photocritic.core.config.Tuning
import com.yk.photocritic.core.net.ProviderHttp
import com.yk.photocritic.core.net.ProviderRequest
import com.yk.photocritic.core.providers.ModelSettings
import com.yk.photocritic.core.providers.NeutralTurn
import com.yk.photocritic.core.providers.PromptKind
import com.yk.photocritic.core.providers.SendCopy
import com.yk.photocritic.core.providers.TurnEvent
import com.yk.photocritic.core.providers.TurnRequest
import com.yk.photocritic.core.providers.gemini.GeminiClient
import com.yk.photocritic.features.image.ImagePipeline
import com.yk.photocritic.features.image.PrepareResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Phase 0 measurements moved to Phase 1.1, run with the real app code on a
 * phone (file 50): image-size accuracy (Q5), history length, timeouts and
 * the report write time. Each result is one JSON line under the PC-MEASURE
 * tag; the host scores it. The run script puts the synthetic photos, their
 * text and the key in the app's `files/measure/`; without them every test is
 * skipped. Not part of CI.
 */
@RunWith(AndroidJUnit4::class)
class MeasureTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dir = File(context.filesDir, "measure")
    private val key: String? =
        File(context.filesDir, "pc-gemini-test.key")
            .takeIf { it.exists() }
            ?.readText()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    private val client =
        GeminiClient(ProviderHttp(connectTimeoutMs = Tuning.CONNECT_TIMEOUT_MS, requestTimeoutMs = Tuning.REQUEST_TIMEOUT_MS))
    private val pipeline = ImagePipeline(context.contentResolver, File(context.cacheDir, "send-measure"))

    private fun photos(): List<File> =
        dir
            .listFiles { f -> f.extension in setOf("jpg", "png") }
            .orEmpty()
            .sortedBy { it.name }

    private fun emit(fields: Map<String, Any?>) {
        val line =
            buildJsonObject {
                fields.forEach { (k, v) ->
                    when (v) {
                        null -> put(k, null as String?)
                        is Number -> put(k, JsonPrimitive(v))
                        else -> put(k, v.toString())
                    }
                }
            }
        Log.i(TAG, Json.encodeToString(line))
    }

    private fun request(
        copy: SendCopy,
        question: String,
        kind: PromptKind,
        history: List<NeutralTurn> = emptyList(),
    ) = TurnRequest(ModelSettings(MODEL, MAX_OUTPUT_TOKENS, 1_000_000, THINKING_LEVEL), history, question, copy, kind)

    /** Sends one turn and reports time to first text, total time, the end and the answer. */
    private suspend fun timed(request: TurnRequest): Map<String, Any?> {
        val start = SystemClock.elapsedRealtime()
        var firstMs: Long? = null
        val answer = StringBuilder()
        var end: TurnEvent? = null
        client.send(request, key!!).collect {
            when (it) {
                is TurnEvent.Text -> {
                    if (firstMs == null) firstMs = SystemClock.elapsedRealtime() - start
                    answer.append(it.delta)
                }
                else -> end = it
            }
        }
        val endText =
            when (val e = end) {
                is TurnEvent.Done -> e.finishReason.name
                is TurnEvent.Error -> "error ${e.error.httpStatus} ${e.error.providerCode}"
                else -> "none"
            }
        return mapOf(
            "ttftMs" to firstMs,
            "totalMs" to SystemClock.elapsedRealtime() - start,
            "end" to endText,
            "answer" to answer.toString(),
        )
    }

    private suspend fun prepared(
        photo: File,
        longEdge: Int,
        resolution: String,
    ): Pair<SendCopy, Map<String, Any?>> {
        val ready = pipeline.prepare(Uri.fromFile(photo), longEdge, MAX_BYTES, resolution) as PrepareResult.Ready
        val copy = ready.copy.load()
        return SendCopy(copy.jpeg, copy.width, copy.height, resolution) to
            mapOf("w" to copy.width, "h" to copy.height, "bytes" to copy.jpeg.size, "prepareMs" to ready.prepareMs)
    }

    @Test
    fun imageSizeAccuracy() =
        runBlocking(Dispatchers.Default) {
            assumeTrue(key != null && photos().isNotEmpty())
            for (photo in photos()) {
                for (edge in LONG_EDGES) {
                    val (copy, info) = prepared(photo, edge, "MEDIA_RESOLUTION_HIGH")
                    val result = timed(request(copy, EXTRACT_QUESTION, PromptKind.EXTRACT))
                    emit(mapOf("kind" to "size", "photo" to photo.name, "edge" to edge, "res" to "HIGH") + info + result)
                }
                for (res in listOf("MEDIA_RESOLUTION_LOW", "MEDIA_RESOLUTION_MEDIUM")) {
                    val (copy, info) = prepared(photo, Tuning.DEFAULT_LONG_EDGE_PX, res)
                    val result = timed(request(copy, EXTRACT_QUESTION, PromptKind.EXTRACT))
                    emit(
                        mapOf(
                            "kind" to "size",
                            "photo" to photo.name,
                            "edge" to Tuning.DEFAULT_LONG_EDGE_PX,
                            "res" to res.removePrefix("MEDIA_RESOLUTION_"),
                        ) +
                            info +
                            result,
                    )
                }
            }
        }

    @Test
    fun historyLength() =
        runBlocking(Dispatchers.Default) {
            assumeTrue(key != null && photos().isNotEmpty())
            val (copy, _) = prepared(photos().last(), Tuning.DEFAULT_LONG_EDGE_PX, "MEDIA_RESOLUTION_HIGH")
            for (turns in HISTORY_SIZES) {
                val history =
                    List(turns) { i -> NeutralTurn("Question $i: what does line ${i % 6 + 1} say, and what might it mean?", ANSWER, "img") }
                val result = timed(request(copy, "In one sentence, what have we covered so far?", PromptKind.FREE, history))
                val historyChars = history.sumOf { it.question.length + it.answerText.length }
                emit(mapOf("kind" to "history", "turns" to turns, "historyChars" to historyChars) + result.minus("answer"))
            }
        }

    @Test
    fun repeatedDefaultRequests() =
        runBlocking(Dispatchers.Default) {
            assumeTrue(key != null && photos().isNotEmpty())
            val (copy, _) = prepared(photos().last(), Tuning.DEFAULT_LONG_EDGE_PX, "MEDIA_RESOLUTION_HIGH")
            repeat(REPEATS) { i ->
                val extract = timed(request(copy, EXTRACT_QUESTION, PromptKind.EXTRACT))
                emit(mapOf("kind" to "repeat", "prompt" to "extract", "i" to i) + extract.minus("answer"))
                val describe = timed(request(copy, "Describe this photo in about 150 words.", PromptKind.FREE))
                emit(mapOf("kind" to "repeat", "prompt" to "describe", "i" to i) + describe.minus("answer"))
            }
        }

    /** Time to the response head on a new connection each time: DNS, TCP, TLS and the server's first byte. */
    @Test
    fun connectTimes() =
        runBlocking(Dispatchers.Default) {
            assumeTrue(key != null)
            repeat(REPEATS) { i ->
                val http = ProviderHttp(connectTimeoutMs = Tuning.CONNECT_TIMEOUT_MS, requestTimeoutMs = Tuning.REQUEST_TIMEOUT_MS)
                val start = SystemClock.elapsedRealtime()
                val head =
                    http
                        .exchange(
                            ProviderRequest(
                                "https://generativelanguage.googleapis.com/v1beta/models?pageSize=1",
                                mapOf("x-goog-api-key" to key!!),
                                jsonBody = null,
                                requestIdHeader = null,
                            ),
                        ).first()
                emit(
                    mapOf(
                        "kind" to "connect",
                        "i" to i,
                        "headMs" to SystemClock.elapsedRealtime() - start,
                        "head" to head.toString().take(40),
                    ),
                )
            }
        }

    /**
     * A Firestore write and the server's answer. The dev project's rules deny
     * every write, so nothing is stored; the denial still needs the full
     * round trip, which is what `REPORT_WRITE_TIMEOUT_MS` waits for.
     */
    @Test
    fun reportWriteRoundTrip() =
        runBlocking(Dispatchers.Default) {
            assumeTrue(key != null)
            val db = FirebaseFirestore.getInstance()
            db.firestoreSettings =
                FirebaseFirestoreSettings.Builder().setLocalCacheSettings(MemoryCacheSettings.newBuilder().build()).build()
            repeat(REPEATS) { i ->
                val start = SystemClock.elapsedRealtime()
                val outcome =
                    try {
                        db
                            .collection("phase0_checks")
                            .document()
                            .set(mapOf("text" to "x".repeat(Tuning.MAX_REPORT_CONVERSATION_CHARS)))
                            .await()
                        "stored"
                    } catch (e: Exception) {
                        e::class.simpleName + ": " + e.message?.take(60)
                    }
                emit(mapOf("kind" to "firestore", "i" to i, "ms" to SystemClock.elapsedRealtime() - start, "outcome" to outcome))
            }
        }

    /** The app pipeline's send copies, saved for comparison with the harness's `prepare` output (file 40). */
    @Test
    fun pipelineCopies() =
        runBlocking(Dispatchers.Default) {
            assumeTrue(photos().isNotEmpty())
            for (edge in COMPARE_EDGES) {
                val out = File(dir, "out/$edge").apply { mkdirs() }
                for (photo in photos()) {
                    val (copy, info) = prepared(photo, edge, "MEDIA_RESOLUTION_HIGH")
                    File(out, photo.nameWithoutExtension + ".jpg").writeBytes(copy.jpeg)
                    emit(mapOf("kind" to "copy", "photo" to photo.name, "edge" to edge) + info)
                }
            }
        }

    private companion object {
        const val TAG = "PC-MEASURE"
        const val MODEL = "gemini-4-hydrogen"
        const val MAX_OUTPUT_TOKENS = 8192
        const val THINKING_LEVEL = "low"
        const val MAX_BYTES = 4_000_000
        const val REPEATS = 6
        const val EXTRACT_QUESTION = "Copy all the text in this photo exactly, line by line."
        val LONG_EDGES = listOf(768, 1024, 1536, 2048)
        val HISTORY_SIZES = listOf(0, 5, 10, 20)
        val COMPARE_EDGES = listOf(1024, 2048)
        val ANSWER = "The line reads as a list of codes and numbers. ".repeat(12)
    }
}
