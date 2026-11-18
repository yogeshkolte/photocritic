package com.yk.photocritic.features.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImagePipelineTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val shared = File(System.getProperty("pc.repoRoot"), "shared/images")

    private fun pipeline(sdk: Int) = ImagePipeline(context.contentResolver, File(tmp.root, "send"), sdk)

    private fun jpeg(
        width: Int,
        height: Int,
        noise: Boolean = false,
        exif: Map<String, String> = emptyMap(),
    ): Uri {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val random = Random(1)
        for (y in 0 until height) {
            for (x in 0 until width) {
                bitmap.setPixel(
                    x,
                    y,
                    if (noise) {
                        random.nextInt() or BLACK
                    } else if (x < width / 2) {
                        Color.RED
                    } else {
                        Color.BLUE
                    },
                )
            }
        }
        val file = tmp.newFile()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        if (exif.isNotEmpty()) {
            ExifInterface(file).apply { exif.forEach { (k, v) -> setAttribute(k, v) } }.saveAttributes()
        }
        return Uri.fromFile(file)
    }

    private fun ready(result: PrepareResult) = result as? PrepareResult.Ready ?: error("expected Ready, got $result")

    @Test
    fun scaledNeverEnlargesAndKeepsTheAspectRatio() {
        assertEquals(2048 to 1365, ImagePipeline.scaled(6000, 4000, 2048))
        assertEquals(1365 to 2048, ImagePipeline.scaled(4000, 6000, 2048))
        assertEquals(800 to 600, ImagePipeline.scaled(800, 600, 2048))
        assertEquals(2048 to 1, ImagePipeline.scaled(100_000, 10, 2048))
    }

    @Test
    fun formatsAreSniffedFromTheFirstBytes() {
        assertEquals(InputFormat.JPEG, InputFormat.sniff(File(shared, "test.jpg").readBytes().copyOf(64)))
        assertEquals(InputFormat.HEIC, InputFormat.sniff(File(shared, "test.heic").readBytes().copyOf(64)))
        assertEquals(InputFormat.AVIF, InputFormat.sniff(File(shared, "test.avif").readBytes().copyOf(64)))
        assertEquals(
            InputFormat.PNG,
            InputFormat.sniff(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())),
        )
        assertEquals(InputFormat.GIF, InputFormat.sniff("GIF89a".toByteArray()))
        assertEquals(InputFormat.WEBP, InputFormat.sniff("RIFF\u0000\u0000\u0000\u0000WEBPVP8 ".toByteArray(Charsets.ISO_8859_1)))
        assertEquals(InputFormat.OTHER, InputFormat.sniff("hello".toByteArray()))
        assertEquals(InputFormat.OTHER, InputFormat.sniff("\u0000\u0000\u0000\u0018ftypmp42".toByteArray(Charsets.ISO_8859_1)))
    }

    @Test
    fun theSharedTestImageIsDownscaledToTheTarget() =
        runBlocking {
            for (sdk in listOf(27, 35)) {
                val result = ready(pipeline(sdk).prepare(Uri.fromFile(File(shared, "test.jpg")), 384, 4_000_000, "high"))
                assertEquals("sdk $sdk", 384 to 256, result.copy.width to result.copy.height)
                assertEquals(InputFormat.JPEG, result.inputFormat)
                assertEquals(result.copy.bytes.toLong(), result.copy.file.length())
                assertEquals(384, result.copy.targetLongEdgePx)
                assertEquals("high", result.copy.load().imageDetail)
            }
        }

    @Test
    fun aSmallPhotoIsNotEnlarged() =
        runBlocking {
            val result = ready(pipeline(27).prepare(jpeg(40, 30), 2048, 4_000_000, "high"))
            assertEquals(40 to 30, result.copy.width to result.copy.height)
        }

    @Test
    fun exifOrientationIsAppliedAndNoMetadataIsKept() =
        runBlocking {
            val input =
                jpeg(
                    80,
                    40,
                    exif =
                        mapOf(
                            ExifInterface.TAG_ORIENTATION to ExifInterface.ORIENTATION_ROTATE_90.toString(),
                            ExifInterface.TAG_MAKE to "TestCam",
                            ExifInterface.TAG_GPS_LATITUDE to "51/1,30/1,0/1",
                        ),
                )
            val result = ready(pipeline(27).prepare(input, 2048, 4_000_000, "high"))
            assertEquals(40 to 80, result.copy.width to result.copy.height)
            val decoded = BitmapFactory.decodeFile(result.copy.file.path)
            // Red was the left half; after a 90° clockwise turn it is the top half.
            assertTrue(Color.red(decoded.getPixel(20, 10)) > 200)
            assertTrue(Color.blue(decoded.getPixel(20, 70)) > 200)
            val exif = ExifInterface(result.copy.file)
            assertNull(exif.getAttribute(ExifInterface.TAG_MAKE))
            assertNull(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
            assertEquals(ExifInterface.ORIENTATION_UNDEFINED, exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 0))
        }

    @Test
    fun mirroredOrientationsAreHandled() {
        val bitmap = Bitmap.createBitmap(4, 2, Bitmap.Config.ARGB_8888)
        val sizes =
            (1..8).associateWith {
                ImagePipeline.oriented(bitmap.copy(Bitmap.Config.ARGB_8888, true), it).let { b -> b.width to b.height }
            }
        assertEquals(listOf(1, 2, 3, 4).map { 4 to 2 }, listOf(1, 2, 3, 4).map(sizes::getValue))
        assertEquals(listOf(5, 6, 7, 8).map { 2 to 4 }, listOf(5, 6, 7, 8).map(sizes::getValue))
    }

    @Test
    fun overTheByteLimitReencodesOnceThenFails() =
        runBlocking {
            val noisy = jpeg(300, 300, noise = true)
            val atQuality80 = ready(pipeline(27).prepare(noisy, 2048, 20_000_000, "high")).copy.bytes
            val retried = ready(pipeline(27).prepare(noisy, 2048, atQuality80 - 1, "high"))
            assertTrue(retried.copy.bytes < atQuality80)
            val failed = pipeline(27).prepare(noisy, 2048, 1_000, "high")
            assertEquals(PrepareResult.Failed("too-large", InputFormat.JPEG), failed)
        }

    @Test
    fun heicAndAvifAreRejectedByApiLevel() =
        runBlocking {
            val heic = Uri.fromFile(File(shared, "test.heic"))
            val avif = Uri.fromFile(File(shared, "test.avif"))
            assertEquals(PrepareResult.Failed("heic-unsupported", InputFormat.HEIC), pipeline(27).prepare(heic, 2048, 4_000_000, "high"))
            assertEquals(PrepareResult.Failed("avif-unsupported", InputFormat.AVIF), pipeline(30).prepare(avif, 2048, 4_000_000, "high"))
        }

    @Test
    fun aTransparentPngBecomesWhite() =
        runBlocking {
            val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.TRANSPARENT) }
            val file = tmp.newFile("clear.png")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val result = ready(pipeline(27).prepare(Uri.fromFile(file), 2048, 4_000_000, "high"))
            assertEquals(InputFormat.PNG, result.inputFormat)
            assertTrue(Color.red(BitmapFactory.decodeFile(result.copy.file.path).getPixel(5, 5)) > 240)
        }

    @Test
    fun unreadableInputsFail() =
        runBlocking {
            val missing = pipeline(27).prepare(Uri.fromFile(File(tmp.root, "nope.jpg")), 2048, 4_000_000, "high")
            assertTrue(missing is PrepareResult.Failed)
            val text = tmp.newFile("text.jpg").apply { writeText("not an image") }
            val notImage = pipeline(27).prepare(Uri.fromFile(text), 2048, 4_000_000, "high")
            assertTrue(notImage is PrepareResult.Failed)
        }

    private companion object {
        const val BLACK = 0xFF000000.toInt()
    }
}
