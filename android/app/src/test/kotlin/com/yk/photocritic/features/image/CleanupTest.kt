package com.yk.photocritic.features.image

import android.net.Uri
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class CleanupTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val cleanup by lazy { Cleanup(tmp.root) }

    private fun file(
        dir: File,
        name: String,
    ) = File(dir, name).apply {
        parentFile!!.mkdirs()
        writeText("x")
    }

    @Test
    fun launchRemovesSendCopiesAndCaptures() {
        val send = file(cleanup.sendDir, "send-1.jpg")
        val capture = file(cleanup.cameraDir, "capture-1.jpg")
        val other = file(tmp.root, "keep.txt")
        cleanup.atLaunch()
        assertFalse(send.exists())
        assertFalse(capture.exists())
        assertTrue(other.exists())
    }

    @Test
    fun conversationEndRemovesOnlyThatPhotosFiles() {
        val mine = file(cleanup.sendDir, "send-1.jpg")
        val next = file(cleanup.sendDir, "send-2.jpg")
        val capture = file(cleanup.cameraDir, "capture-1.jpg")
        val outside = file(tmp.root, "capture-elsewhere.jpg")
        val record = PhotoRecord(Uri.parse("content://x/1"), sendCopy = SendCopyFile(mine, 1, 1, 1, 2048, "high"))
        cleanup.conversationEnded(record, capture)
        cleanup.conversationEnded(record, outside)
        assertFalse(mine.exists())
        assertFalse(capture.exists())
        assertTrue(next.exists())
        assertTrue(outside.exists())
    }

    @Test
    fun aReplacedSendCopyIsRemoved() {
        val old = file(cleanup.sendDir, "send-old.jpg")
        cleanup.replaced(SendCopyFile(old, 1, 1, 1, 1024, "low"))
        cleanup.replaced(null)
        assertFalse(old.exists())
    }

    @Test
    fun cameraUrisAreRecognisedByAuthorityAndFolder() {
        assertTrue(Cleanup.isCameraUri(Uri.parse("content://a.fileprovider/camera/capture-1.jpg"), "a.fileprovider"))
        assertFalse(Cleanup.isCameraUri(Uri.parse("content://media/camera/1"), "a.fileprovider"))
        assertFalse(Cleanup.isCameraUri(Uri.parse("content://a.fileprovider/other/1.jpg"), "a.fileprovider"))
    }

    @Test
    fun aRecordNeedsANewCopyWhenTheModelSettingsChange() {
        val record = PhotoRecord(Uri.parse("content://x/1"))
        assertTrue(record.needsSendCopy(2048, "high"))
        val withCopy = record.copy(sendCopy = SendCopyFile(File("a"), 1, 20, 10, 2048, "high"))
        assertFalse(withCopy.needsSendCopy(2048, "high"))
        assertTrue(withCopy.needsSendCopy(1024, "high"))
        assertTrue(withCopy.needsSendCopy(2048, "low"))
        assertTrue(withCopy.sendCopy!!.longEdgePx == 20)
    }
}
