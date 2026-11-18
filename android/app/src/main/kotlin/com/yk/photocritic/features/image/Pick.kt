package com.yk.photocritic.features.image

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/** Camera capture files in the app cache, shared through the `FileProvider` (§6). */
object Pick {
    fun authority(context: Context) = "${context.packageName}.fileprovider"

    fun newCapture(context: Context): Pair<File, Uri> {
        val dir = File(context.cacheDir, Cleanup.CAMERA_DIR).apply { mkdirs() }
        val file = File(dir, "capture-${UUID.randomUUID()}.jpg")
        return file to FileProvider.getUriForFile(context, authority(context), file)
    }

    /** The file behind a capture URI made by [newCapture]. */
    fun fileFor(
        context: Context,
        uri: Uri,
    ): File? =
        uri
            .takeIf { Cleanup.isCameraUri(it, authority(context)) }
            ?.lastPathSegment
            ?.let { File(File(context.cacheDir, Cleanup.CAMERA_DIR), it) }
}

/**
 * The system Photo Picker, image only (§6): no storage or media permission;
 * below API 30 the Google Play services backport declared in the manifest.
 * Returns the action that opens it.
 */
@Composable
fun rememberPhotoPicker(onPicked: (Uri) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(onPicked) }
    return { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}

/**
 * The camera app through `TakePicture`, writing to a cache file; no
 * `CAMERA` permission is declared. The pending URI is saved with the
 * instance state, so the result survives the activity being destroyed
 * during capture (§6). [onUnavailable] runs when no camera app exists.
 */
@Composable
fun rememberCamera(
    onCaptured: (Uri) -> Unit,
    onUnavailable: () -> Unit,
): () -> Unit {
    val context = LocalContext.current
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
            val uri = pending?.let(Uri::parse)
            pending = null
            if (saved && uri != null) {
                onCaptured(uri)
            } else {
                uri?.let { Pick.fileFor(context, it)?.delete() }
            }
        }
    return {
        val (file, uri) = Pick.newCapture(context)
        pending = uri.toString()
        try {
            launcher.launch(uri)
        } catch (_: ActivityNotFoundException) {
            pending = null
            file.delete()
            onUnavailable()
        }
    }
}
