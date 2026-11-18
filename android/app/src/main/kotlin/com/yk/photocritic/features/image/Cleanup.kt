package com.yk.photocritic.features.image

import android.net.Uri
import java.io.File

/**
 * Removes send copies and camera captures (§6, file 35): everything at
 * launch, and one photo's files when its conversation ends. Photos are
 * never kept in any other cache.
 */
class Cleanup(
    cacheDir: File,
) {
    val sendDir = File(cacheDir, SEND_DIR)
    val cameraDir = File(cacheDir, CAMERA_DIR)

    fun atLaunch() {
        sendDir.deleteRecursively()
        cameraDir.deleteRecursively()
    }

    /** [cameraFile] is the capture behind the record's original, if it came from the camera. */
    fun conversationEnded(
        record: PhotoRecord,
        cameraFile: File? = null,
    ) {
        record.sendCopy?.file?.delete()
        cameraFile?.takeIf { it.parentFile == cameraDir }?.delete()
    }

    /** Send copies left from earlier copies of the same photo (the model's size changed). */
    fun replaced(old: SendCopyFile?) {
        old?.file?.delete()
    }

    companion object {
        const val SEND_DIR = "send"

        /** Matches `res/xml/file_paths.xml`. */
        const val CAMERA_DIR = "camera"

        fun isCameraUri(
            uri: Uri,
            authority: String,
        ): Boolean = uri.authority == authority && uri.pathSegments.firstOrNull() == CAMERA_DIR
    }
}
