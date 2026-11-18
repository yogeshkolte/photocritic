package com.yk.photocritic.phase0

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import androidx.core.util.AtomicFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer

/** HEIC and AVIF decoding by API level (Q2) and atomic file writes (design file 50). */
@RunWith(AndroidJUnit4::class)
class PlatformChecksTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val sdk = Build.VERSION.SDK_INT

    private fun asset(name: String) =
        InstrumentationRegistry
            .getInstrumentation()
            .context.assets
            .open(name)
            .use { it.readBytes() }

    private fun bitmapFactory(bytes: ByteArray): Bitmap? = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

    private fun imageDecoder(bytes: ByteArray): Bitmap? =
        if (sdk >= Build.VERSION_CODES.P) {
            runCatching { ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) }.getOrNull()
        } else {
            null
        }

    /** The §6 pipeline decodes with ImageDecoder on API 28+ and BitmapFactory below. */
    private fun pipelineDecodes(bytes: ByteArray) =
        (if (sdk >= Build.VERSION_CODES.P) imageDecoder(bytes) else bitmapFactory(bytes)) != null

    @Test
    fun decodeSupportByFormat() {
        for (name in listOf("test.jpg", "test.heic", "test.avif")) {
            val bytes = asset(name)
            observe(
                "API $sdk $name: BitmapFactory=${bitmapFactory(bytes) != null} " +
                    "ImageDecoder=${if (sdk >= Build.VERSION_CODES.P) (imageDecoder(bytes) != null).toString() else "n/a"}",
            )
        }
        assertNotNull(bitmapFactory(asset("test.jpg")))
        if (sdk < Build.VERSION_CODES.P) assertNull("HEIC below API 28 gives image-error", bitmapFactory(asset("test.heic")))
        if (sdk < Build.VERSION_CODES.S) assertEquals("AVIF below API 31 gives image-error", false, pipelineDecodes(asset("test.avif")))
    }

    @Test
    fun atomicWriteKeepsTheOldFileWhenAWriteFails() {
        val file = File(context.cacheDir, "phase0-atomic.txt").also { it.delete() }
        val atomic = AtomicFile(file)

        fun write(text: String) =
            atomic.startWrite().let { out ->
                out.write(text.toByteArray())
                atomic.finishWrite(out)
            }
        write("v1")
        val failing = atomic.startWrite()
        failing.write("v2-partial".toByteArray())
        atomic.failWrite(failing)
        assertEquals("v1", String(AtomicFile(file).readFully()))
        val interrupted = atomic.startWrite()
        interrupted.write("v3-partial".toByteArray())
        interrupted.close() // as if the process died before finishWrite
        assertEquals("v1", String(AtomicFile(file).readFully()))
        write("v4")
        assertEquals("v4", String(AtomicFile(file).readFully()))
        observe("API $sdk AtomicFile: failed and interrupted writes keep the previous file")
        atomic.delete()
    }
}
