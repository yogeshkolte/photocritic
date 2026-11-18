package com.yk.photocritic.harness

import com.yk.photocritic.core.config.Tuning
import com.yk.photocritic.core.net.ProviderHttp
import com.yk.photocritic.core.providers.ModelSettings
import com.yk.photocritic.core.providers.PromptKind
import com.yk.photocritic.core.providers.SendCopy
import com.yk.photocritic.core.providers.TurnEvent
import com.yk.photocritic.core.providers.TurnRequest
import com.yk.photocritic.core.providers.gemini.GeminiClient
import com.yk.photocritic.core.providers.shared.SystemPrompt
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * `run` (file 40): every photo in a set, at each long edge and detail
 * setting, through the Kotlin Gemini client; one CSV per run in
 * `harness/results/`. OpenAI and Anthropic join when their clients exist
 * (Phase 2). Token columns stay empty until the clients report usage.
 */
@Suppress("LongParameterList") // One value per command-line option.
class Run(
    private val set: File,
    private val results: File,
    private val apiKey: String,
    private val model: ModelSettings,
    private val edges: List<Int>,
    private val details: List<String>,
    private val question: String,
) {
    private val client =
        GeminiClient(ProviderHttp(connectTimeoutMs = Tuning.CONNECT_TIMEOUT_MS, requestTimeoutMs = Tuning.REQUEST_TIMEOUT_MS))

    private class Outcome(
        val ttftMs: Long?,
        val totalMs: Long,
        val answer: String,
        val end: TurnEvent?,
    )

    suspend fun execute(): File {
        val runId = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC).format(Instant.now())
        val truth = Json.parseToJsonElement(File(set, "truth.json").readText()).jsonObject.mapValues { it.value.jsonPrimitive.content }
        val photos =
            File(set, "images")
                .listFiles { f -> f.extension.lowercase() in setOf("jpg", "jpeg", "png") }
                .orEmpty()
                .sortedBy { it.name }
        check(photos.isNotEmpty()) { "no photos in ${File(set, "images")}" }
        results.mkdirs()
        val csv = File(results, "$runId.csv")
        csv.writeText(COLUMNS.joinToString(",") + "\n")
        for (photo in photos) measure(runId, photo, truth[photo.name], csv)
        return csv
    }

    private suspend fun measure(
        runId: String,
        photo: File,
        truth: String?,
        csv: File,
    ) {
        val source = Images.read(photo)
        for (edge in edges) {
            val copy = Images.copy(source, edge)
            for (detail in details) {
                val outcome = send(SendCopy(copy.jpeg, copy.width, copy.height, detail))
                val cer = truth?.let { Score.cer(outcome.answer, it) }
                val row = row(runId, Sample(photo.name, edge, detail, copy.jpeg.size), outcome, cer)
                csv.appendText(row.joinToString(",") { csvField(it) } + "\n")
                println(row.joinToString(" "))
            }
        }
    }

    private suspend fun send(copy: SendCopy): Outcome {
        val start = System.nanoTime()
        var first: Long? = null
        val answer = StringBuilder()
        var end: TurnEvent? = null
        client.send(TurnRequest(model, emptyList(), question, copy, PromptKind.EXTRACT), apiKey).collect {
            if (it is TurnEvent.Text) {
                if (first == null) first = elapsedMs(start)
                answer.append(it.delta)
            } else {
                end = it
            }
        }
        return Outcome(first, elapsedMs(start), answer.toString(), end)
    }

    private class Sample(
        val image: String,
        val edge: Int,
        val detail: String,
        val bytes: Int,
    )

    private fun row(
        runId: String,
        s: Sample,
        o: Outcome,
        cer: Double?,
    ): List<Any?> {
        val done = o.end as? TurnEvent.Done
        val error = (o.end as? TurnEvent.Error)?.error
        return listOf(
            runId,
            "gemini",
            model.modelId,
            done?.servedModel,
            SystemPrompt.version,
            s.image,
            s.edge,
            s.detail,
            s.bytes,
            o.ttftMs,
            o.totalMs,
            null,
            null,
            done?.finishReason?.name?.lowercase(),
            error?.phase?.wire,
            error?.httpStatus,
            cer?.let { "%.4f".format(it) },
        )
    }

    companion object {
        val COLUMNS =
            listOf(
                "run_id",
                "provider",
                "requested_model",
                "served_model",
                "prompt_version",
                "image",
                "long_edge_px",
                "detail",
                "bytes",
                "ttft_ms",
                "total_ms",
                "input_tokens",
                "output_tokens",
                "finish_reason",
                "error_phase",
                "http_status",
                "cer",
            )
        private const val NANOS_PER_MS = 1_000_000L

        private fun elapsedMs(start: Long) = (System.nanoTime() - start) / NANOS_PER_MS

        fun csvField(value: Any?): String {
            val text = value?.toString() ?: ""
            return if (text.any { it == ',' || it == '"' || it == '\n' }) "\"" + text.replace("\"", "\"\"") + "\"" else text
        }
    }
}
