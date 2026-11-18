package com.yk.photocritic.harness

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.random.Random

/**
 * The synthetic set (Phase 0 Q5): 4032×3024 "phone photos", each with six
 * lines of random codes at one text height on uneven, textured paper,
 * turned 1.5°. Random codes cannot be guessed from context, so the error
 * rate measures what the model can read. The same seed gives the same set.
 */
object Synth {
    private const val WIDTH = 4032
    private const val HEIGHT = 3024
    private const val LINES = 6
    private const val WORDS = 4
    private const val SPECKS = 4000
    private const val TEXT_X = 1500
    private const val TEXT_Y = 1100
    private const val LINE_SPACING = 1.8
    private const val TILT_DEGREES = 1.5
    private const val SEED = 20_261_118
    val TEXT_HEIGHTS = listOf(15, 22, 32, 48)
    private const val LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    private val LABELS = listOf("LOT", "ZONE", "RATE", "BAY", "UNIT", "REF", "GATE", "ROW")

    @Suppress("MagicNumber") // Number ranges for the random codes.
    private fun word(rng: Random): String =
        when (rng.nextInt(3)) {
            0 -> (1..3).map { LETTERS[rng.nextInt(LETTERS.length)] }.joinToString("") + "-" + rng.nextInt(10, 1000)
            1 -> "${rng.nextInt(1, 1000)}.${rng.nextInt(0, 100).toString().padStart(2, '0')}"
            else -> LABELS[rng.nextInt(LABELS.size)] + rng.nextInt(1, 100)
        }

    @Suppress("MagicNumber") // Paper and ink colours.
    fun write(dir: File) {
        val images = File(dir, "images").apply { mkdirs() }
        val rng = Random(SEED)
        val truth = mutableMapOf<String, JsonPrimitive>()
        for (px in TEXT_HEIGHTS) {
            val lines = List(LINES) { List(WORDS) { word(rng) }.joinToString(" ") }
            val image = BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB)
            val g = image.createGraphics()
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            g.color = Color(120, 110, 100)
            g.fillRect(0, 0, WIDTH, HEIGHT)
            g.rotate(Math.toRadians(-TILT_DEGREES), WIDTH / 2.0, HEIGHT / 2.0)
            for (y in 0 until HEIGHT step 6) {
                g.color = Color(236 - y / 200, 232 - y / 220, 222 - y / 240)
                g.fillRect(-100, y, WIDTH + 200, 6)
            }
            g.color = Color(200, 196, 188)
            repeat(SPECKS) { g.fillRect(rng.nextInt(WIDTH), rng.nextInt(HEIGHT), 1, 1) }
            g.color = Color(30, 30, 34)
            g.font = Font("Arial", Font.PLAIN, px)
            lines.forEachIndexed { i, line -> g.drawString(line, TEXT_X, TEXT_Y + px + (i * px * LINE_SPACING).toInt()) }
            g.dispose()
            val name = "text-${px}px.png"
            ImageIO.write(image, "png", File(images, name))
            truth[name] = JsonPrimitive(lines.joinToString("\n"))
        }
        File(dir, "truth.json").writeText(Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), JsonObject(truth)) + "\n")
    }
}
