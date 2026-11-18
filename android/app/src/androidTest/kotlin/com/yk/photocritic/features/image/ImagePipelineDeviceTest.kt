package com.yk.photocritic.features.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** The image pipeline with the device's own decoders (file 35, Q2): HEIC, AVIF and orientation by API level. */
@RunWith(AndroidJUnit4::class)
class ImagePipelineDeviceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val sdk = Build.VERSION.SDK_INT
    private val pipeline = ImagePipeline(context.contentResolver, File(context.cacheDir, "send-test"))

    private fun asset(name: String): Uri {
        val file = File(context.cacheDir, "in-$name")
        InstrumentationRegistry
            .getInstrumentation()
            .context.assets
            .open(name)
            .use { input -> file.outputStream().use(input::copyTo) }
        return Uri.fromFile(file)
    }

    private suspend fun prepare(uri: Uri) = pipeline.prepare(uri, 384, 4_000_000, "high")

    @Test
    fun jpegHeicAndAvifByApiLevel() =
        runBlocking {
            for ((name, minSdk) in listOf("test.jpg" to 1, "test.heic" to Build.VERSION_CODES.P, "test.avif" to Build.VERSION_CODES.S)) {
                val result = prepare(asset(name))
                Log.i("PC-PHASE1", "API $sdk $name -> $result")
                if (sdk >= minSdk) {
                    val ready = result as PrepareResult.Ready
                    assertEquals(name, 384 to 256, ready.copy.width to ready.copy.height)
                } else {
                    assertTrue(name, result is PrepareResult.Failed)
                }
            }
        }

    @Test
    fun exifOrientationIsAppliedOnEachDecodePath() =
        runBlocking {
            val bitmap = Bitmap.createBitmap(800, 400, Bitmap.Config.ARGB_8888)
            for (x in 0 until 800) for (y in 0 until 400) bitmap.setPixel(x, y, if (x < 400) Color.RED else Color.BLUE)
            val file = File(context.cacheDir, "rotated.jpg")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            ExifInterface(file)
                .apply {
                    setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
                    setAttribute(ExifInterface.TAG_MAKE, "TestCam")
                }.saveAttributes()
            val ready = prepare(Uri.fromFile(file)) as PrepareResult.Ready
            Log.i("PC-PHASE1", "API $sdk rotated -> ${ready.copy.width}x${ready.copy.height}")
            assertEquals(192 to 384, ready.copy.width to ready.copy.height)
            assertNull(ExifInterface(ready.copy.file).getAttribute(ExifInterface.TAG_MAKE))
        }
}
