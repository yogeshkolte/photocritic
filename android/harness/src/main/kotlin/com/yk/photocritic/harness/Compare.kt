package com.yk.photocritic.harness

import java.io.File
import kotlin.math.abs
import kotlin.math.log10

/**
 * Compares two folders of send copies with the same file names, such as the
 * harness's `prepare` output and the Android pipeline's (Phase 0, file 40):
 * size, bytes, mean absolute difference per channel (0–255) and PSNR.
 */
object Compare {
    private const val MAX_CHANNEL = 255.0
    private const val CHANNEL_MASK = 0xFF
    private const val CHANNELS = 3
    private const val DECIBELS = 10.0
    private val CHANNEL_SHIFTS = intArrayOf(0, 8, 16)

    data class Row(
        val name: String,
        val sizeA: String,
        val sizeB: String,
        val bytesA: Long,
        val bytesB: Long,
        val meanAbsDiff: Double?,
        val psnrDb: Double?,
    )

    fun rows(
        a: File,
        b: File,
    ): List<Row> =
        a.listFiles().orEmpty().filter { it.extension == "jpg" }.sortedBy { it.name }.mapNotNull { fa ->
            val fb = File(b, fa.name).takeIf { it.exists() } ?: return@mapNotNull null
            val ia = Images.read(fa)
            val ib = Images.read(fb)
            val same = ia.width == ib.width && ia.height == ib.height
            var sum = 0.0
            var sq = 0.0
            if (same) {
                for (y in 0 until ia.height) {
                    for (x in 0 until ia.width) {
                        val pa = ia.getRGB(x, y)
                        val pb = ib.getRGB(x, y)
                        for (shift in CHANNEL_SHIFTS) {
                            val d = abs((pa shr shift and CHANNEL_MASK) - (pb shr shift and CHANNEL_MASK)).toDouble()
                            sum += d
                            sq += d * d
                        }
                    }
                }
            }
            val n = CHANNELS.toDouble() * ia.width * ia.height
            Row(
                fa.name,
                "${ia.width}x${ia.height}",
                "${ib.width}x${ib.height}",
                fa.length(),
                fb.length(),
                if (same) sum / n else null,
                if (same) DECIBELS * log10(MAX_CHANNEL * MAX_CHANNEL / (sq / n)) else null,
            )
        }
}
