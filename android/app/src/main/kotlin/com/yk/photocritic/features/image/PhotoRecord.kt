package com.yk.photocritic.features.image

import android.net.Uri
import com.yk.photocritic.core.providers.SendCopy
import java.io.File
import java.util.UUID

/** The JPEG written for one model's size and detail settings (§6). */
data class SendCopyFile(
    val file: File,
    val bytes: Int,
    val width: Int,
    val height: Int,
    /** The model's long-edge limit this copy was made for. */
    val targetLongEdgePx: Int,
    val detail: String,
) {
    val longEdgePx: Int get() = maxOf(width, height)

    fun load(): SendCopy = SendCopy(file.readBytes(), width, height, detail)
}

/**
 * One photo in a conversation (file 35). The original is never changed and
 * is shown at full quality; the send copy is rebuilt when the model's
 * target size or detail differs. `edits` is empty in v1.
 */
data class PhotoRecord(
    val originalUri: Uri,
    val edits: List<String> = emptyList(),
    val imageVersionId: String = UUID.randomUUID().toString(),
    val sendCopy: SendCopyFile? = null,
) {
    fun needsSendCopy(
        longEdgePx: Int,
        detail: String,
    ): Boolean = sendCopy == null || sendCopy.targetLongEdgePx != longEdgePx || sendCopy.detail != detail
}
