package com.yk.photocritic.harness

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

class ImagesTest {
    @Test
    fun `sizes round like the app and never grow`() {
        assertEquals(2048 to 1536, Images.scaled(4032, 3024, 2048))
        assertEquals(1024 to 683, Images.scaled(1536, 1024, 1024))
        assertEquals(800 to 600, Images.scaled(800, 600, 2048))
    }

    @Test
    fun `the copy is a JPEG with only the JFIF header and transparent pixels become white`() {
        val source = BufferedImage(400, 200, BufferedImage.TYPE_INT_ARGB)
        val copy = Images.copy(source, 100)
        assertEquals(100 to 50, copy.width to copy.height)
        val decoded = ImageIO.read(ByteArrayInputStream(copy.jpeg))
        assertTrue((decoded.getRGB(50, 25) and 0xFF) > 250, "white")
        // FF D8 then the JFIF APP0 segment; no APP1 (EXIF) segment.
        assertEquals(listOf(0xFF, 0xD8, 0xFF, 0xE0), copy.jpeg.take(4).map { it.toInt() and 0xFF })
        assertTrue((0 until copy.jpeg.size - 1).none { copy.jpeg[it] == 0xFF.toByte() && copy.jpeg[it + 1] == 0xE1.toByte() })
    }
}
