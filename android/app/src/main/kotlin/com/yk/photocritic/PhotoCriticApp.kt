package com.yk.photocritic

import android.app.Application
import com.yk.photocritic.platform.telemetry.RedactingExceptionHandler

/**
 * Builds the [AppContainer] once per process. Firebase starts from its own
 * content provider before this runs, so the redacting handler is installed
 * in front of Crashlytics' handler (file 33).
 */
class PhotoCriticApp : Application() {
    val container: AppContainer by lazy { AppContainer.create(this) }

    override fun onCreate() {
        super.onCreate()
        RedactingExceptionHandler.install()
    }
}
