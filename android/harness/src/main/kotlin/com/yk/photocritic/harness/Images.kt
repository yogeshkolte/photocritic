package com.yk.photocritic.harness

import com.yk.photocritic.core.config.Tuning
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The harness's send copy (file 40): bicubic downsizing with Java2D and a
 * JPEG at [Tuning.JPEG_QUALITY] with no metadata. The sizes round the same
 * way as the app's `ImagePipeline.scaled`. EXIF orientation is not applied,
 * so dataset photos must be stored upright.
 */
object Images {
    class Copy(
        val jpeg: ByteArray,
        val width: Int,
        val height: Int,
    )

    fun scaled(
        width: Int,
        height: Int,
        target: Int,
    ): Pair<Int, Int> {
        val long = max(width, height)
        if (long <= target) return width to height
        val scale = target.toDouble() / long
        return max(1, (width * scale).roundToInt()) to max(1, (height * scale).roundToInt())
    }

    fun read(file: File): BufferedImage = ImageIO.read(file) ?: error("not a readable JPEG or PNG: $file")

    fun copy(
        source: BufferedImage,
        longEdge: Int,
    ): Copy {
        val (w, h) = scaled(source.width, source.height, longEdge)
        // Transparent pixels become white, as in the app.
        val out = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        val g = out.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.color = Color.WHITE
        g.fillRect(0, 0, w, h)
        g.drawImage(source, 0, 0, w, h, null)
        g.dispose()
        return Copy(jpeg(out), w, h)
    }

    private fun jpeg(image: BufferedImage): ByteArray {
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        val params =
            writer.defaultWriteParam.apply {
                compressionMode = ImageWriteParam.MODE_EXPLICIT
                compressionQuality = Tuning.JPEG_QUALITY.toFloat()
            }
        val bytes = ByteArrayOutputStream()
        ImageIO.createImageOutputStream(bytes).use { stream ->
            writer.output = stream
            writer.write(null, IIOImage(image, null, null), params)
        }
        writer.dispose()
        return bytes.toByteArray()
    }
}
