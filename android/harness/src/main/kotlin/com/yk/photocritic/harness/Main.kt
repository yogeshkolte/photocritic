package com.yk.photocritic.harness

import com.yk.photocritic.core.providers.ModelSettings
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.system.exitProcess

private const val USAGE = """Usage (paths relative to the repository root):
  run [--set DIR] [--edges 1024,1536,2048,3072] [--details MEDIA_RESOLUTION_LOW,...] [--model ID] [--thinking LEVEL]
      PC_GEMINI_KEY must be set. The set defaults to PC_HARNESS_DATASET, else harness/datasets/synthetic.
  synth DIR                 write the synthetic set (images/ and truth.json)
  prepare SET_DIR EDGE OUT  write the harness's send copies as OUT/<name>.jpg
  compare DIR_A DIR_B       compare send copies with the same names"""

private const val DEFAULT_MAX_OUTPUT_TOKENS = 8192
private const val DEFAULT_CONTEXT_TOKENS = 1_000_000
private const val PREPARE_ARGS = 4
private const val COMPARE_ARGS = 3

private fun option(
    args: List<String>,
    name: String,
): String? = args.indexOf("--$name").takeIf { it >= 0 }?.let { args.getOrNull(it + 1) }

private fun fail(message: String): Nothing {
    System.err.println(message)
    exitProcess(1)
}

fun main(argv: Array<String>) {
    val args = argv.toList()
    when (args.firstOrNull() ?: "run") {
        "run" -> run(args)
        "synth" -> Synth.write(File(args.getOrNull(1) ?: fail(USAGE)))
        "prepare" -> prepare(args)
        "compare" -> compare(args)
        else -> fail(USAGE)
    }
}

private fun run(args: List<String>) {
    val key = System.getenv("PC_GEMINI_KEY")?.takeIf { it.isNotBlank() } ?: fail("PC_GEMINI_KEY is not set.\n$USAGE")
    val set = File(option(args, "set") ?: System.getenv("PC_HARNESS_DATASET") ?: "harness/datasets/synthetic")
    val model =
        ModelSettings(
            option(args, "model") ?: "gemini-4-hydrogen",
            DEFAULT_MAX_OUTPUT_TOKENS,
            DEFAULT_CONTEXT_TOKENS,
            option(args, "thinking") ?: "low",
        )
    val edges = (option(args, "edges") ?: "1024,1536,2048,3072").split(",").map { it.trim().toInt() }
    val details = (option(args, "details") ?: "MEDIA_RESOLUTION_LOW,MEDIA_RESOLUTION_MEDIUM,MEDIA_RESOLUTION_HIGH").split(",")
    val question = "Copy all the text in this photo exactly, line by line."
    val csv = runBlocking { Run(set, File("harness/results"), key, model, edges, details, question).execute() }
    println("Wrote $csv")
}

private fun prepare(args: List<String>) {
    if (args.size != PREPARE_ARGS) fail(USAGE)
    val (setDir, edgeText, outDir) = args.drop(1)
    val edge = edgeText.toInt()
    val out = File(outDir).apply { mkdirs() }
    File(setDir, "images")
        .listFiles()
        .orEmpty()
        .filter {
            it.extension.lowercase() in setOf("jpg", "jpeg", "png")
        }.sortedBy { it.name }
        .forEach {
            val copy = Images.copy(Images.read(it), edge)
            File(out, it.nameWithoutExtension + ".jpg").writeBytes(copy.jpeg)
            println("${it.name} -> ${copy.width}x${copy.height}, ${copy.jpeg.size} bytes")
        }
}

private fun compare(args: List<String>) {
    if (args.size != COMPARE_ARGS) fail(USAGE)
    println("image size_a size_b bytes_a bytes_b mean_abs_diff psnr_db")
    Compare.rows(File(args[1]), File(args[2])).forEach {
        println(
            "${it.name} ${it.sizeA} ${it.sizeB} ${it.bytesA} ${it.bytesB} " +
                "${it.meanAbsDiff?.let { d -> "%.2f".format(d) } ?: "-"} ${it.psnrDb?.let { d -> "%.1f".format(d) } ?: "-"}",
        )
    }
}
