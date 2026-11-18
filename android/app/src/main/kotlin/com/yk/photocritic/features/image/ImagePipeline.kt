package com.yk.photocritic.features.image

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import com.yk.photocritic.core.config.Tuning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

/** The picked file's format, from its first bytes; the `input_format` of `pc_image_prepared`. */
enum class InputFormat(
    val wire: String,
) {
    JPEG("jpeg"),
    PNG("png"),
    WEBP("webp"),
    GIF("gif"),
    HEIC("heic"),
    AVIF("avif"),
    OTHER("other"),
    ;

    companion object {
        private const val HEADER_BYTES = 64
        private val heicBrands = setOf("heic", "heix", "hevc", "hevx", "heim", "heis", "mif1", "msf1")
        private val avifBrands = setOf("avif", "avis")

        @Suppress("MagicNumber") // File signatures and their offsets.
        fun sniff(header: ByteArray): InputFormat {
            val text = String(header, Charsets.ISO_8859_1)

            fun u(i: Int) = header.getOrNull(i)?.toInt()?.and(0xFF)
            return when {
                u(0) == 0xFF && u(1) == 0xD8 && u(2) == 0xFF -> JPEG
                text.startsWith("\u0089PNG") -> PNG
                text.startsWith("GIF8") -> GIF
                text.startsWith("RIFF") && text.startsWith("WEBP", 8) -> WEBP
                text.startsWith("ftyp", 4) -> isoBrand(text)
                else -> OTHER
            }
        }

        /** The major brand and the compatible brands of an ISO media file. */
        @Suppress("MagicNumber") // Box layout: brands are 4 bytes each from offset 8.
        private fun isoBrand(text: String): InputFormat {
            val brands = (8 until minOf(text.length, HEADER_BYTES) - 3 step 4).map { text.substring(it, it + 4) }
            return when {
                brands.any { it in avifBrands } -> AVIF
                brands.any { it in heicBrands } -> HEIC
                else -> OTHER
            }
        }

        fun read(
            resolver: ContentResolver,
            uri: Uri,
        ): InputFormat =
            resolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArray(HEADER_BYTES)
                var read = 0
                while (read < HEADER_BYTES) {
                    val n = input.read(buffer, read, HEADER_BYTES - read)
                    if (n < 0) break
                    read += n
                }
                sniff(buffer.copyOf(read))
            } ?: throw IOException("cannot open $uri")
    }
}

sealed interface PrepareResult {
    data class Ready(
        val copy: SendCopyFile,
        val inputFormat: InputFormat,
        val prepareMs: Long,
    ) : PrepareResult

    /** Shown as `image-error` (§7). [reason] goes into "Show details". */
    data class Failed(
        val reason: String,
        val inputFormat: InputFormat?,
    ) : PrepareResult
}

/**
 * Builds the send copy (§6, file 35): decode with downsampling to the
 * model's long edge, apply EXIF orientation, encode JPEG at
 * [Tuning.JPEG_QUALITY] with no metadata; if over `maxBytes`, encode once
 * more at [RETRY_QUALITY]; if still over, fail. A large photo is never
 * fully decoded. HEIC below API 28 and AVIF below API 31 fail (Q2).
 */
class ImagePipeline(
    private val resolver: ContentResolver,
    private val outDir: File,
    private val sdkInt: Int = Build.VERSION.SDK_INT,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    suspend fun prepare(
        uri: Uri,
        targetLongEdgePx: Int,
        maxBytes: Int,
        detail: String,
    ): PrepareResult =
        withContext(Dispatchers.IO) {
            val start = nowMs()
            var format: InputFormat? = null
            try {
                format = InputFormat.read(resolver, uri)
                unsupported(format)?.let { return@withContext PrepareResult.Failed(it, format) }
                val bitmap = flattened(decode(uri, targetLongEdgePx))
                val jpeg =
                    encode(bitmap, (Tuning.JPEG_QUALITY * PERCENT).roundToInt()).takeIf { it.size <= maxBytes }
                        ?: encode(bitmap, RETRY_QUALITY).takeIf { it.size <= maxBytes }
                        ?: return@withContext PrepareResult.Failed("too-large", format)
                outDir.mkdirs()
                val file = File(outDir, "send-${UUID.randomUUID()}.jpg").apply { writeBytes(jpeg) }
                val copy = SendCopyFile(file, jpeg.size, bitmap.width, bitmap.height, targetLongEdgePx, detail)
                bitmap.recycle()
                PrepareResult.Ready(copy, format, nowMs() - start)
            } catch (e: IOException) {
                PrepareResult.Failed("unreadable: ${e.javaClass.simpleName}", format)
            } catch (e: SecurityException) {
                PrepareResult.Failed("no-access: ${e.javaClass.simpleName}", format)
            } catch (_: OutOfMemoryError) {
                PrepareResult.Failed("out-of-memory", format)
            }
        }

    /**
     * The original, decoded for the screen (§6): downsampled to at most
     * [maxLongEdgePx] with EXIF orientation applied, kept in memory only.
     * Null when the photo cannot be read.
     */
    suspend fun decodeForDisplay(
        uri: Uri,
        maxLongEdgePx: Int,
    ): Bitmap? =
        withContext(Dispatchers.IO) {
            try {
                val format = InputFormat.read(resolver, uri)
                if (unsupported(format) != null) null else decode(uri, maxLongEdgePx)
            } catch (_: IOException) {
                null
            } catch (_: SecurityException) {
                null
            } catch (_: OutOfMemoryError) {
                null
            }
        }

    private fun unsupported(format: InputFormat): String? =
        when {
            format == InputFormat.HEIC && sdkInt < Build.VERSION_CODES.P -> "heic-unsupported"
            format == InputFormat.AVIF && sdkInt < Build.VERSION_CODES.S -> "avif-unsupported"
            else -> null
        }

    private fun decode(
        uri: Uri,
        target: Int,
    ): Bitmap = if (sdkInt >= Build.VERSION_CODES.P) decodeWithImageDecoder(uri, target) else decodeWithBitmapFactory(uri, target)

    /** ImageDecoder applies EXIF orientation and decodes the first frame of an animation. */
    @RequiresApi(Build.VERSION_CODES.P)
    private fun decodeWithImageDecoder(
        uri: Uri,
        target: Int,
    ): Bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val (w, h) = scaled(info.size.width, info.size.height, target)
            if (w != info.size.width) decoder.setTargetSize(w, h)
        }

    private fun decodeWithBitmapFactory(
        uri: Uri,
        target: Int,
    ): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("not an image")
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= target) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val sampled = open(uri).use { BitmapFactory.decodeStream(it, null, options) } ?: throw IOException("decode failed")
        val (w, h) = scaled(sampled.width, sampled.height, target)
        val sized = if (w == sampled.width) sampled else Bitmap.createScaledBitmap(sampled, w, h, true).also { sampled.recycle() }
        val orientation =
            open(uri).use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        return oriented(sized, orientation)
    }

    private fun open(uri: Uri) = resolver.openInputStream(uri) ?: throw IOException("cannot open $uri")

    /** Transparent pixels become white, not JPEG's black. */
    private fun flattened(bitmap: Bitmap): Bitmap {
        if (!bitmap.hasAlpha()) return bitmap
        val out = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        Canvas(out).apply {
            drawColor(Color.WHITE)
            drawBitmap(bitmap, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        }
        bitmap.recycle()
        return out
    }

    /** `Bitmap.compress` writes no EXIF or other metadata. */
    private fun encode(
        bitmap: Bitmap,
        quality: Int,
    ): ByteArray = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()

    companion object {
        const val RETRY_QUALITY = 60
        private const val PERCENT = 100

        /** The size whose long edge is at most [target]; never larger than the original. */
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

        /** Applies an EXIF orientation value, including the mirrored ones. */
        @Suppress("MagicNumber", "CyclomaticComplexMethod") // The EXIF orientation table.
        fun oriented(
            bitmap: Bitmap,
            orientation: Int,
        ): Bitmap {
            val m = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.setScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> m.setRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.setScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> m.apply { setRotate(90f) }.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_90 -> m.setRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> m.apply { setRotate(-90f) }.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_270 -> m.setRotate(-90f)
                else -> return bitmap
            }
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true).also {
                if (it !== bitmap) bitmap.recycle()
            }
        }
    }
}
